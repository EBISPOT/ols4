#!/usr/bin/env python3
"""Stable releases with Zenodo's GitHub archiver and public, token-free DOI lookup."""
import argparse
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile
import time
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener


class SafeRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        if urlparse(new_url).scheme != "https":
            raise ValueError("Refusing non-HTTPS redirect")
        redirected = super().redirect_request(request, response, code, message, headers, new_url)
        if redirected and urlparse(request.full_url).netloc != urlparse(new_url).netloc:
            redirected.remove_header("Authorization")
        return redirected


HTTP = build_opener(SafeRedirect())

SEMVER = re.compile(r"^v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
STATE_MARKER = "<!-- ols-release-state:"
COMPONENTS = ("backend", "frontend", "dataload", "apitester4")


def version_tuple(version):
    match = SEMVER.fullmatch(version)
    if not match:
        raise ValueError(f"Invalid release version: {version}")
    return tuple(map(int, match.groups()))


def next_version(previous, labels):
    bumps = {label.removeprefix("release:") for label in labels
             if label in ("release:patch", "release:minor", "release:major")}
    if len(bumps) > 1:
        raise ValueError("Select only one release:patch/minor/major label")
    bump = next(iter(bumps), "patch")
    major, minor, patch = version_tuple(previous)
    return {"major": f"{major + 1}.0.0", "minor": f"{major}.{minor + 1}.0",
            "patch": f"{major}.{minor}.{patch + 1}"}[bump]


def reviewed_notes(body):
    body = re.sub(r"<!--.*?-->", "", body or "", flags=re.S)
    match = re.search(r"^## Release notes\s*\n(.*?)(?=^## |\Z)", body, re.M | re.S)
    if not match:
        return ""
    notes = match.group(1).strip()
    # An untouched template is not a release note.
    content = re.sub(r"^#+ .*?$", "", notes, flags=re.M).strip()
    return notes if content else ""


def state_body(state):
    encoded = json.dumps(state, sort_keys=True).replace("<", "\\u003c").replace(">", "\\u003e")
    body = state["notes"]
    if state.get("doi"):
        body += f"\n\nCite this software release: https://doi.org/{state['doi']}"
    if "imagesComplete" in state and not state["imagesComplete"]:
        body += "\n\nContainer images are being prepared. Use them after the release workflow completes."
    return body + "\n\n" + STATE_MARKER + encoded + " -->"


def parse_state(release):
    body = release.get("body") or ""
    if STATE_MARKER not in body:
        return None
    return json.loads(body.rsplit(STATE_MARKER, 1)[1].split(" -->", 1)[0])


class ApiError(RuntimeError):
    def __init__(self, method, path, status):
        self.status = status
        super().__init__(f"API {method} {path} failed with HTTP {status}")


class Api:
    def __init__(self, base, token=None):
        self.base, self.token = base.rstrip("/"), token

    def request(self, path, method="GET", data=None, raw=None, content_type="application/json"):
        url = path if path.startswith("https://") else self.base + path
        # Never forward the token to a host other than the configured API host.
        if urlparse(url).netloc != urlparse(self.base).netloc:
            raise ValueError("Refusing cross-host authenticated request")
        headers = {"Accept": "application/json",
                   "User-Agent": "OLS-release-workflow"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        if data is not None:
            raw = json.dumps(data).encode()
        if raw is not None:
            headers["Content-Type"] = content_type
        try:
            with HTTP.open(Request(url, data=raw, headers=headers, method=method), timeout=120) as res:
                body = res.read()
                if not body:
                    return None
                if "json" in res.headers.get("Content-Type", ""):
                    return json.loads(body)
                return body
        except HTTPError as exc:
            # Response bodies/headers can contain sensitive provider information.
            raise ApiError(method, urlparse(url).path, exc.code) from None


class PublicZenodo(Api):
    """Only public record reads; the GitHub webhook owns archival/publication."""
    def __init__(self):
        super().__init__("https://zenodo.org/api")

    def request(self, path, method="GET", **kwargs):
        if method != "GET" or not path.startswith("/records/"):
            raise ValueError("Zenodo integration only permits public record GET requests")
        return super().request(path, method, **kwargs)

    def find(self, state):
        tag_url = state["releaseUrl"].replace("/releases/tag/", "/tree/")
        query = f"related.identifier:{json.dumps(tag_url)} OR related.identifier:{json.dumps(state['releaseUrl'])}"
        records, page = [], 1
        while True:
            result = self.request("/records/?" + urlencode({"q": query, "all_versions": "true", "size": 25, "page": page}))
            rows = result["hits"]["hits"]
            records.extend(rows)
            if len(rows) < 25:
                break
            page += 1
            if page > 20:
                raise ValueError("Too many Zenodo records match the exact release URL")
        matches = []
        for record in records:
            metadata = record["metadata"]
            urls = {item["identifier"] for item in (metadata.get("related_identifiers") or [])}
            if not urls.intersection((tag_url, state["releaseUrl"])):
                continue
            if metadata.get("resource_type", {}).get("type") != "software":
                continue
            try:
                same_version = version_tuple(metadata.get("version") or "") == version_tuple(state["version"])
            except ValueError:
                same_version = False
            if same_version:
                matches.append(record)
        if len(matches) > 1:
            raise ValueError("Multiple software DOIs match this GitHub release; inspect Zenodo before continuing")
        if not matches:
            return None
        record = matches[0]
        for key in ("doi", "conceptdoi"):
            if not re.fullmatch(r"10\.5281/zenodo\.[0-9]+", record.get(key, "")):
                raise ValueError("Zenodo did not return a valid software DOI")
        if state.get("conceptDoi") and record["conceptdoi"] != state["conceptDoi"]:
            raise ValueError("Zenodo software DOI series changed between releases")
        if not record.get("files"):
            raise ValueError("Zenodo software record has no source archive")
        return record


class GitHub(Api):
    def __init__(self, repo, token):
        super().__init__("https://api.github.com", token)
        self.repo, self.root = repo, f"/repos/{repo}"

    def pages(self, path):
        result, page = [], 1
        while True:
            rows = self.request(f"{self.root}{path}?per_page=100&page={page}")
            result.extend(rows)
            if len(rows) < 100:
                return result
            page += 1

    def asset(self, release_id, name):
        assets = self.pages(f"/releases/{release_id}/assets")
        found = next((asset for asset in assets if asset["name"] == name), None)
        if found is None:
            return None
        # GitHub's asset endpoint needs an octet-stream Accept header. Use the
        # JSON API's base64-independent download with a scoped request here.
        if urlparse(found["url"]).netloc != "api.github.com":
            raise ValueError("Refusing cross-host authenticated asset request")
        req = Request(found["url"], headers={"Authorization": f"Bearer {self.token}",
                      "Accept": "application/octet-stream", "User-Agent": "OLS-release-workflow"})
        with HTTP.open(req, timeout=120) as res:
            return res.read()

    def upload(self, release_id, name, data, content_type="application/json"):
        existing = self.asset(release_id, name)
        if existing is not None:
            if existing != data:
                raise ValueError(f"Existing release asset differs: {name}")
            return
        uploader = Api("https://uploads.github.com", self.token)
        uploader.request(f"/repos/{self.repo}/releases/{release_id}/assets?name={quote(name)}",
                         "POST", raw=data, content_type=content_type)

    def save_state(self, release, state):
        return self.request(f"{self.root}/releases/{release['id']}", "PATCH",
                            {"body": state_body(state)})


def json_bytes(data):
    return (json.dumps(data, indent=2, sort_keys=True) + "\n").encode()


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def release_history(gh, releases, current_state):
    history = []
    for release in releases:
        state = parse_state(release)
        if release["draft"] or release.get("prerelease") or not state:
            continue
        if state["commit"] == current_state["commit"]:
            continue
        # A newer commit must not be released before an older stable commit.
        if subprocess.run(["git", "merge-base", "--is-ancestor", state["commit"],
                           current_state["commit"]], capture_output=True).returncode:
            raise ValueError("Release order differs from stable history; retry earlier releases first")
        history.append(public_entry(state))
    return sorted(history, key=lambda entry: version_tuple(entry["version"]), reverse=True)


def public_entry(state):
    return {key: state[key] for key in ("version", "commit", "releasedAt", "notes",
                                       "doi", "releaseUrl")}


def save_context(output, context):
    output.mkdir(parents=True, exist_ok=True)
    (output / "context.json").write_bytes(json_bytes(context))


def restore_assets(gh, output, context):
    for component in COMPONENTS:
        saved = gh.asset(context["releaseId"], f"image-{component}.json")
        if saved:
            (output / f"image-{component}.json").write_bytes(saved)
    if context["complete"]:
        (output / "release-manifest.json").write_bytes(gh.asset(context["releaseId"], "release-manifest.json"))


def write_bundle(bundle):
    for target in (Path("backend/src/main/resources/ols-release.json"),
                   Path("frontend/public/ols-release.json"), Path("apitester4/ols-release.json")):
        target.write_bytes(json_bytes(bundle))


def prepare(gh, config, commit, output):
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("A full merged commit SHA is required")
    if git("rev-parse", "HEAD") != commit:
        raise ValueError("Checkout must match the release commit")
    if subprocess.run(["git", "merge-base", "--is-ancestor", commit, "origin/stable"],
                      capture_output=True).returncode:
        raise ValueError("Release commit must be on stable")
    releases = gh.pages("/releases")
    managed = [(release, parse_state(release)) for release in releases]
    managed = [(release, state) for release, state in managed if state]
    if any(state.get("archiving") != "github" for _, state in managed):
        raise ValueError("A release uses a different archiver; reconcile it before starting the software DOI series")
    match = next(((release, state) for release, state in managed if state["commit"] == commit), None)
    if match:
        release, state = match
    else:
        if any(release["draft"] or not state.get("imagesComplete") for release, state in managed):
            raise ValueError("An earlier release is unfinished; retry it before releasing another commit")
        release_history(gh, releases, {"commit": commit})
        versions = [release["tag_name"] for release in releases if SEMVER.fullmatch(release["tag_name"])]
        previous = max([config["baselineVersion"], *versions], key=version_tuple)
        prs = gh.request(f"{gh.root}/commits/{commit}/pulls")
        prs = [pr for pr in prs if pr.get("merged_at") and pr["base"]["ref"] == "stable"
               and pr.get("merge_commit_sha") == commit]
        labels = [label["name"] for pr in prs for label in pr["labels"]]
        version = next_version(previous, labels)
        tag = "v" + version
        if tag in git("tag", "--list").splitlines():
            raise ValueError(f"Tag {tag} already exists without a managed release")
        notes = "\n\n".join(filter(None, (reviewed_notes(pr.get("body")) for pr in prs)))
        if not notes:
            args = {"tag_name": tag, "target_commitish": commit}
            if versions:
                args["previous_tag_name"] = max(versions, key=version_tuple)
            notes = gh.request(f"{gh.root}/releases/generate-notes", "POST", args)["body"]
        previous_state = max(managed, key=lambda pair: version_tuple(pair[1]["version"]))[1] if managed else {}
        state = {"archiving": "github", "commit": commit, "version": version, "notes": notes,
                 "releasedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                 "releaseUrl": f"https://github.com/{gh.repo}/releases/tag/{tag}",
                 "conceptDoi": previous_state.get("conceptDoi"), "imagesComplete": False}
        release = gh.request(f"{gh.root}/releases", "POST", {
            "tag_name": tag, "target_commitish": commit, "name": f"OLS software {version}",
            "body": state_body(state), "draft": True, "prerelease": False})
    context = {"releaseId": release["id"], "state": state, "complete": state["imagesComplete"]}
    saved = gh.asset(release["id"], "release-history.json")
    if saved:
        context["bundle"] = json.loads(saved)
        write_bundle(context["bundle"])
    save_context(output, context)
    restore_assets(gh, output, context)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as handle:
            handle.write(f"version={state['version']}\ntag=v{state['version']}\n")
            handle.write(f"complete={str(context['complete']).lower()}\n")
    return context


def ensure_tag(gh, state):
    tag = "v" + state["version"]
    refs = gh.request(f"{gh.root}/git/matching-refs/tags/{tag}")
    exact = next((ref for ref in refs if ref["ref"] == "refs/tags/" + tag), None)
    if exact:
        if exact["object"]["type"] != "commit" or exact["object"]["sha"] != state["commit"]:
            raise ValueError("Release tag no longer matches the tested commit")
    else:
        gh.request(f"{gh.root}/git/refs", "POST", {"ref": "refs/tags/" + tag, "sha": state["commit"]})


def archive(gh, zenodo, config, output):
    context = json.loads((output / "context.json").read_text())
    state, release_id = context["state"], context["releaseId"]
    if context["complete"]:
        return
    ensure_tag(gh, state)
    release = gh.request(f"{gh.root}/releases/{release_id}")
    if release["draft"]:
        # Publishing this release is the only archival trigger. Never call the
        # Zenodo deposition API or republish it to retry DOI discovery.
        release = gh.request(f"{gh.root}/releases/{release_id}", "PATCH", {
            "draft": False, "make_latest": "true", "body": state_body(state)})
    state["releasedAt"] = release["published_at"]
    if "doi" not in state:
        deadline = time.monotonic() + config["archiveWaitSeconds"]
        while True:
            try:
                record = zenodo.find(state)
            except ApiError as exc:
                if exc.status not in (429, 500, 502, 503, 504):
                    raise
                record = None
            except (URLError, TimeoutError):
                record = None
            if record:
                break
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError("Zenodo has not archived this release yet. Check the repository's Zenodo release errors, then rerun the original workflow; do not create another release.")
            time.sleep(min(config["archivePollSeconds"], remaining))
        state.update({"doi": record["doi"], "conceptDoi": record["conceptdoi"],
                      "zenodoRecordId": record["id"]})
        gh.save_state(release, state)
    ensure_tag(gh, state)
    saved = gh.asset(release_id, "release-history.json")
    if saved:
        bundle = json.loads(saved)
    else:
        history = release_history(gh, gh.pages("/releases"), state)
        bundle = {"current": public_entry(state), "conceptDoi": state["conceptDoi"],
                  "releases": [public_entry(state), *history]}
        gh.upload(release_id, "release-history.json", json_bytes(bundle))
    context["bundle"] = bundle
    save_context(output, context)
    write_bundle(bundle)
    restore_assets(gh, output, context)


def remember_image(gh, output, component, metadata):
    context = json.loads((output / "context.json").read_text())
    digest = json.loads(metadata.read_text())["containerimage.digest"]
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
        raise ValueError("Invalid image digest")
    data = {"component": component, "digest": digest,
            "image": f"ghcr.io/{gh.repo.lower()}-{component}@{digest}"}
    body = json_bytes(data)
    gh.upload(context["releaseId"], f"image-{component}.json", body)
    (output / f"image-{component}.json").write_bytes(body)


def source_archive(state, manifest):
    prefix = f"ols4-v{state['version']}/"
    source = subprocess.check_output(["git", "archive", "--format=tar", f"--prefix={prefix}", state["commit"]])
    metadata = json.loads(git("show", state["commit"] + ":.zenodo.json"))
    metadata.update({"doi": state["doi"], "version": state["version"],
                     "publication_date": state["releasedAt"][:10]})
    bundle = json_bytes({key: manifest[key] for key in ("current", "conceptDoi", "releases")})
    additions = {".zenodo.json": json_bytes(metadata), "RELEASE_NOTES.md": state["notes"].encode(),
                 "release-manifest.json": json_bytes(manifest),
                 "backend/src/main/resources/ols-release.json": bundle,
                 "frontend/public/ols-release.json": bundle,
                 "apitester4/ols-release.json": bundle}
    result = io.BytesIO()
    # Fixed gzip timestamp makes re-created archives identical after a retry.
    import gzip
    with gzip.GzipFile(fileobj=result, mode="wb", mtime=0) as compressed:
        with tarfile.open(fileobj=compressed, mode="w") as target:
            with tarfile.open(fileobj=io.BytesIO(source), mode="r:") as original:
                for item in original:
                    if item.name.removeprefix(prefix) in additions:
                        continue
                    target.addfile(item, original.extractfile(item) if item.isfile() else None)
            for name, data in additions.items():
                item = tarfile.TarInfo(prefix + name)
                item.size, item.mtime = len(data), int(datetime.fromisoformat(state["releasedAt"].replace("Z", "+00:00")).timestamp())
                target.addfile(item, io.BytesIO(data))
    return result.getvalue()


def finish(gh, output):
    context = json.loads((output / "context.json").read_text())
    state, release_id = context["state"], context["releaseId"]
    if context["complete"]:
        return
    ensure_tag(gh, state)
    images = {component: json.loads((output / f"image-{component}.json").read_text())["image"]
              for component in COMPONENTS}
    manifest = {**context["bundle"], "images": images, "zenodoRecordId": state["zenodoRecordId"],
                "archiving": "github"}
    # These enriched GitHub assets are distinct from Zenodo's exact tagged-source
    # ZIP, which is produced exclusively by the native GitHub integration.
    gh.upload(release_id, "release-manifest.json", json_bytes(manifest))
    gh.upload(release_id, f"ols4-v{state['version']}-source.tar.gz", source_archive(state, manifest), "application/gzip")
    state["imagesComplete"] = True
    gh.save_state({"id": release_id}, state)
    context["complete"] = True
    save_context(output, context)
    (output / "release-manifest.json").write_bytes(json_bytes(manifest))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["prepare", "archive", "remember-image", "finish"])
    parser.add_argument("--commit", default=os.environ.get("GITHUB_SHA"))
    parser.add_argument("--output", type=Path, default=Path(".release-output"))
    parser.add_argument("--component", choices=COMPONENTS)
    parser.add_argument("--metadata", type=Path)
    args = parser.parse_args()
    gh = GitHub(os.environ["GITHUB_REPOSITORY"], os.environ["GH_TOKEN"])
    if args.command == "remember-image":
        remember_image(gh, args.output, args.component, args.metadata)
        return
    config = json.loads(Path(".github/release/config.json").read_text())
    if args.command == "prepare":
        prepare(gh, config, args.commit, args.output)
    elif args.command == "archive":
        archive(gh, PublicZenodo(), config, args.output)
    else:
        finish(gh, args.output)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        # Avoid traceback/environment/request dumps in CI logs.
        print(f"Release failed: {type(exc).__name__}: {exc}")
        raise SystemExit(1)
