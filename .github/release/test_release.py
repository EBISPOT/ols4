import copy
import io
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch
from urllib.parse import parse_qs, urlparse
from urllib.request import Request

import release as subject

CONFIG = {"baselineVersion": "4.0.0", "archiveWaitSeconds": 0, "archivePollSeconds": 1}
CONCEPT_DOI = "10.5281/zenodo.20000000"
DIGEST = "sha256:" + "b" * 64


class FakeGitHub:
    repo, root = "EBISPOT/ols4", "/repos/EBISPOT/ols4"

    def __init__(self):
        self.releases, self.assets, self.refs, self.calls, self.prs = [], {}, [], [], []
        self.fail_state_once = self.fail_publish_once = self.fail_finish_once = False
        self.on_publish = lambda state: None

    def pages(self, path):
        return copy.deepcopy(self.releases)

    def request(self, path, method="GET", data=None):
        self.calls.append((path, method))
        if path.endswith("/pulls"):
            return self.prs
        if path.endswith("/generate-notes"):
            return {"body": "### Changes\n\n* A useful software change"}
        if path.endswith("/releases") and method == "POST":
            item = {**data, "id": len(self.releases) + 1}
            self.releases.append(item)
            return copy.deepcopy(item)
        if "/releases/" in path:
            item = self.releases[int(path.rsplit("/", 1)[1]) - 1]
            if method == "GET":
                return copy.deepcopy(item)
            if method == "PATCH":
                if self.fail_state_once and "draft" not in data:
                    self.fail_state_once = False
                    raise RuntimeError("Lost state update")
                publishing = item["draft"] and data.get("draft") is False
                item.update(data)
                if publishing:
                    item["published_at"] = "2026-10-05T12:00:00Z"
                    self.on_publish(subject.parse_state(item))
                if self.fail_publish_once and publishing:
                    self.fail_publish_once = False
                    raise RuntimeError("Lost GitHub publish response")
                if self.fail_finish_once and subject.parse_state(item).get("imagesComplete"):
                    self.fail_finish_once = False
                    raise RuntimeError("Lost finish response")
                return copy.deepcopy(item)
        if "matching-refs" in path:
            return self.refs
        if path.endswith("/git/refs") and method == "POST":
            self.refs.append({"ref": data["ref"], "object": {"sha": data["sha"], "type": "commit"}})
            return self.refs[-1]
        raise AssertionError((path, method))

    def save_state(self, release, state):
        return self.request(f"{self.root}/releases/{release['id']}", "PATCH", {"body": subject.state_body(state)})

    def asset(self, release_id, name):
        return self.assets.get((release_id, name))

    def upload(self, release_id, name, data, content_type="application/json"):
        if (release_id, name) in self.assets and self.assets[release_id, name] != data:
            raise ValueError("Existing release asset differs")
        self.assets[release_id, name] = data


class FakeZenodo(subject.PublicZenodo):
    """Simulate the independent native webhook; this client only reads records."""
    def __init__(self, gh):
        super().__init__()
        self.records, self.calls = [], []
        self.available = True
        self.empty_polls = 0
        self.http_error_once = None
        gh.on_publish = self.archived_by_webhook

    def archived_by_webhook(self, state):
        record_id = 20000001 + len(self.records)
        self.records.append({
            "id": record_id, "doi": f"10.5281/zenodo.{record_id}", "conceptdoi": CONCEPT_DOI,
            "metadata": {"resource_type": {"type": "software"}, "version": "v" + state["version"],
                         "related_identifiers": [{"identifier": state["releaseUrl"].replace("/releases/tag/", "/tree/"),
                                                  "relation": "isSupplementTo"}]},
            "files": [{"key": f"EBISPOT/ols4-v{state['version']}.zip"}]})

    def request(self, path, method="GET", **kwargs):
        assert method == "GET" and path.startswith("/records/?"), (path, method)
        self.calls.append((path, method))
        if self.http_error_once:
            status, self.http_error_once = self.http_error_once, None
            raise subject.ApiError(method, "/records/", status)
        if not self.available or self.empty_polls:
            self.empty_polls = max(0, self.empty_polls - 1)
            return {"hits": {"hits": []}}
        query = parse_qs(urlparse(path).query)["q"][0]
        rows = [row for row in self.records if any(json.dumps(item["identifier"]) in query
                for item in row["metadata"]["related_identifiers"])]
        return {"hits": {"hits": copy.deepcopy(rows)}}


class VersionTests(unittest.TestCase):
    def test_version_policy(self):
        self.assertEqual(subject.next_version("v4.9.9", []), "4.9.10")
        self.assertEqual(subject.next_version("4.9.9", ["release:minor", "bug"]), "4.10.0")
        self.assertEqual(subject.next_version("4.9.9", ["release:major"]), "5.0.0")
        with self.assertRaises(ValueError):
            subject.next_version("4.0.0", ["release:major", "release:patch"])
        with self.assertRaises(ValueError):
            subject.next_version("04.0.0", [])

    def test_reviewed_notes_and_empty_template(self):
        self.assertEqual(subject.reviewed_notes("## Release notes\n\n### Added\n\n### Fixed\n"), "")
        self.assertEqual(subject.reviewed_notes("intro\n## Release notes\n### Fixed\n* Better search\n## Validation\npasses"),
                         "### Fixed\n* Better search")

    def test_state_round_trip_with_comment_markers_in_notes(self):
        state = {"notes": "Literal <!-- ols-release-state: and --> in a PR title"}
        self.assertEqual(subject.parse_state({"body": subject.state_body(state)}), state)

    def test_redirect_strips_authentication_on_another_host(self):
        original = Request("https://api.github.com/asset", headers={"Authorization": "Bearer example"})
        handler = subject.SafeRedirect()
        redirected = handler.redirect_request(original, None, 302, "", {}, "https://release-assets.githubusercontent.com/file")
        self.assertIsNone(redirected.get_header("Authorization"))
        same = handler.redirect_request(original, None, 302, "", {}, "https://api.github.com/other")
        self.assertEqual(same.get_header("Authorization"), "Bearer example")
        with self.assertRaises(ValueError):
            handler.redirect_request(original, None, 302, "", {}, "http://example.com/file")

    def test_zenodo_client_has_no_authorization_header_and_cannot_write(self):
        class Response:
            headers = {"Content-Type": "application/json"}
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self): return b'{"hits":{"hits":[]}}'
        client = subject.PublicZenodo()
        with patch.object(subject.HTTP, "open", return_value=Response()) as opened:
            client.request("/records/?q=software")
            self.assertIsNone(opened.call_args.args[0].get_header("Authorization"))
            for path, method in (("/deposit/depositions", "GET"), ("/records/", "POST")):
                with self.assertRaisesRegex(ValueError, "public record GET"):
                    client.request(path, method)
            self.assertEqual(opened.call_count, 1)


class LifecycleTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.previous_dir = Path.cwd()
        os.chdir(self.temp.name)
        for path in ("backend/src/main/resources", "frontend/public", "apitester4"):
            Path(path).mkdir(parents=True)
        Path("CITATION.cff").write_text('cff-version: 1.2.0\ntitle: OLS software\n')
        Path("code.txt").write_text("source code\n")
        for args in (["init", "-q"], ["add", "."], ["-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-qm", "test source"]):
            subprocess.run(["git", *args], check=True, capture_output=True)
        self.commit = subject.git("rev-parse", "HEAD")
        subprocess.run(["git", "update-ref", "refs/remotes/origin/stable", self.commit], check=True)
        self.output, self.gh = Path("out"), FakeGitHub()
        self.zenodo = FakeZenodo(self.gh)
        self.env = patch.dict(os.environ, {"GITHUB_OUTPUT": ""})
        self.env.start()

    def tearDown(self):
        self.env.stop()
        os.chdir(self.previous_dir)
        self.temp.cleanup()

    def prepare(self):
        return subject.prepare(self.gh, CONFIG, self.commit, self.output)

    def archive(self, config=CONFIG):
        subject.archive(self.gh, self.zenodo, config, self.output)
        return json.loads((self.output / "context.json").read_text())

    def images(self):
        for component in subject.COMPONENTS:
            metadata = self.output / "build.json"
            metadata.write_text(json.dumps({"containerimage.digest": DIGEST}))
            subject.remember_image(self.gh, self.output, component, metadata)

    def advance(self):
        Path("code.txt").write_text("next stable source")
        subprocess.run(["git", "add", "code.txt"], check=True)
        subprocess.run(["git", "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-qm", "next stable"], check=True)
        self.commit = subject.git("rev-parse", "HEAD")
        subprocess.run(["git", "update-ref", "refs/remotes/origin/stable", self.commit], check=True)
        self.output = Path("second-output")

    def test_end_to_end_native_archiving_and_component_metadata(self):
        context = self.prepare()
        self.assertEqual(context["state"]["version"], "4.0.1")
        self.assertIsNone(context["state"]["conceptDoi"])
        self.assertNotIn("doi", context["state"])
        self.assertTrue(self.gh.releases[0]["draft"])
        self.assertEqual(self.zenodo.calls, [])
        context = self.archive()
        self.assertFalse(self.gh.releases[0]["draft"])
        self.assertIn("Container images are being prepared", self.gh.releases[0]["body"])
        self.assertEqual(context["state"]["doi"], "10.5281/zenodo.20000001")
        self.assertEqual(context["state"]["conceptDoi"], CONCEPT_DOI)
        self.assertEqual(context["state"]["releasedAt"], "2026-10-05T12:00:00Z")
        frontend = Path("frontend/public/ols-release.json").read_bytes()
        self.assertEqual(frontend, Path("backend/src/main/resources/ols-release.json").read_bytes())
        self.assertEqual(frontend, Path("apitester4/ols-release.json").read_bytes())
        self.images()
        subject.finish(self.gh, self.output)
        self.assertTrue(subject.parse_state(self.gh.releases[0])["imagesComplete"])
        self.assertNotIn("Container images are being prepared", self.gh.releases[0]["body"])
        self.assertEqual(self.gh.refs[0]["object"]["sha"], self.commit)
        data = self.gh.assets[1, "ols4-v4.0.1-source.tar.gz"]
        with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
            prefix = "ols4-v4.0.1/"
            self.assertEqual(archive.extractfile(prefix + "code.txt").read(), b"source code\n")
            citation = archive.extractfile(prefix + "CITATION.cff").read().decode()
            self.assertIn('doi: "10.5281/zenodo.20000001"', citation)
            self.assertIn('version: "4.0.1"', citation)
            manifest = json.load(archive.extractfile(prefix + "release-manifest.json"))
            self.assertEqual(set(manifest["images"]), set(subject.COMPONENTS))
            self.assertEqual(manifest["archiving"], "github")
            self.assertEqual(manifest["current"]["commit"], self.commit)
            bundled = json.load(archive.extractfile(prefix + "backend/src/main/resources/ols-release.json"))
            self.assertEqual(bundled["current"], manifest["current"])
        self.assertEqual(data, subject.source_archive(context["state"], manifest))
        # An original-run retry reuses the published release, DOI and all images.
        self.assertTrue(self.prepare()["complete"])
        self.archive()
        subject.finish(self.gh, self.output)
        self.assertEqual(len(self.zenodo.records), 1)
        self.assertEqual(len(self.gh.releases), 1)
        self.assertTrue(all(method == "GET" for _, method in self.zenodo.calls))

    def test_two_stable_releases_increment_version_and_preserve_software_series(self):
        self.prepare()
        first = self.archive()
        self.images()
        subject.finish(self.gh, self.output)
        self.advance()
        self.prepare()
        second = self.archive()
        self.assertEqual(second["state"]["version"], "4.0.2")
        self.assertEqual(second["state"]["conceptDoi"], first["state"]["conceptDoi"])
        self.assertNotEqual(second["state"]["doi"], first["state"]["doi"])
        self.assertEqual([entry["version"] for entry in second["bundle"]["releases"]], ["4.0.2", "4.0.1"])
        self.images()
        subject.finish(self.gh, self.output)
        self.assertEqual(len(self.zenodo.records), 2)

    def test_timeout_retry_does_not_republish_or_create_another_doi(self):
        self.prepare()
        self.zenodo.available = False
        with self.assertRaisesRegex(TimeoutError, "rerun the original workflow"):
            self.archive()
        self.assertFalse(self.gh.releases[0]["draft"])
        self.assertEqual(len(self.zenodo.records), 1)
        self.zenodo.available = True
        self.prepare()
        self.archive()
        self.assertEqual(len(self.zenodo.records), 1)
        self.assertEqual(len(self.gh.releases), 1)

    def test_delayed_public_index_is_polled(self):
        self.prepare()
        self.zenodo.empty_polls = 2
        with patch.object(subject.time, "sleep") as sleep:
            self.archive({**CONFIG, "archiveWaitSeconds": 60})
        self.assertEqual(sleep.call_count, 2)
        self.assertEqual(len(self.zenodo.records), 1)

    def test_transient_zenodo_error_is_retried(self):
        self.prepare()
        self.zenodo.http_error_once = 429
        with patch.object(subject.time, "sleep") as sleep:
            self.archive({**CONFIG, "archiveWaitSeconds": 60})
        self.assertEqual(sleep.call_count, 1)

    def test_permanent_public_api_error_stops_without_republishing(self):
        self.prepare()
        self.zenodo.http_error_once = 400
        with self.assertRaises(subject.ApiError):
            self.archive()
        self.prepare()
        self.archive()
        self.assertEqual(len(self.zenodo.records), 1)

    def test_lost_github_publish_response_does_not_retrigger_archiving(self):
        self.prepare()
        self.gh.fail_publish_once = True
        with self.assertRaises(RuntimeError):
            self.archive()
        self.prepare()
        self.archive()
        self.assertEqual(len(self.zenodo.records), 1)

    def test_lost_doi_state_update_rediscovers_same_record(self):
        self.prepare()
        self.gh.fail_state_once = True
        with self.assertRaises(RuntimeError):
            self.archive()
        self.prepare()
        self.assertEqual(self.archive()["state"]["doi"], "10.5281/zenodo.20000001")
        self.assertEqual(len(self.zenodo.records), 1)

    def test_lost_finish_response_restores_complete_manifest(self):
        self.prepare()
        self.archive()
        self.images()
        self.gh.fail_finish_once = True
        with self.assertRaises(RuntimeError):
            subject.finish(self.gh, self.output)
        (self.output / "release-manifest.json").unlink(missing_ok=True)
        self.assertTrue(self.prepare()["complete"])
        self.assertTrue((self.output / "release-manifest.json").exists())
        subject.finish(self.gh, self.output)
        self.assertEqual(len(self.zenodo.records), 1)

    def test_partial_image_build_survives_a_rerun(self):
        self.prepare()
        self.archive()
        metadata = self.output / "build.json"
        metadata.write_text(json.dumps({"containerimage.digest": DIGEST}))
        subject.remember_image(self.gh, self.output, "backend", metadata)
        (self.output / "image-backend.json").unlink()
        self.prepare()
        self.archive()
        self.assertTrue((self.output / "image-backend.json").exists())
        self.assertFalse((self.output / "image-frontend.json").exists())

    def test_paper_record_is_never_used_as_software_doi(self):
        self.prepare()
        self.gh.on_publish = lambda state: None
        self.zenodo.archived_by_webhook(subject.parse_state(self.gh.releases[0]))
        self.zenodo.records[0]["metadata"]["resource_type"] = {"type": "publication", "subtype": "article"}
        with self.assertRaises(TimeoutError):
            self.archive()

    def test_wrong_version_is_never_used(self):
        self.prepare()
        self.gh.on_publish = lambda state: None
        self.zenodo.archived_by_webhook(subject.parse_state(self.gh.releases[0]))
        self.zenodo.records[0]["metadata"]["version"] = "4.0.9"
        with self.assertRaises(TimeoutError):
            self.archive()

    def test_wrong_repository_is_never_used(self):
        self.prepare()
        self.gh.on_publish = lambda state: None
        self.zenodo.archived_by_webhook(subject.parse_state(self.gh.releases[0]))
        self.zenodo.records[0]["metadata"]["related_identifiers"][0]["identifier"] = "https://github.com/other/repo/tree/v4.0.1"
        with self.assertRaises(TimeoutError):
            self.archive()

    def test_ambiguous_software_records_stop_instead_of_choosing_one(self):
        self.prepare()
        self.gh.on_publish = lambda state: [self.zenodo.archived_by_webhook(state) for _ in range(2)]
        with self.assertRaisesRegex(ValueError, "Multiple software DOIs"):
            self.archive()

    def test_changed_concept_doi_blocks_building_next_release(self):
        self.prepare()
        self.archive()
        self.images()
        subject.finish(self.gh, self.output)
        self.advance()
        self.prepare()
        self.gh.on_publish = lambda state: None
        self.zenodo.archived_by_webhook(subject.parse_state(self.gh.releases[-1]))
        self.zenodo.records[-1]["conceptdoi"] = "10.5281/zenodo.99999999"
        with self.assertRaisesRegex(ValueError, "series changed"):
            self.archive()

    def test_conflicting_tag_cannot_publish_source(self):
        self.prepare()
        self.gh.refs = [{"ref": "refs/tags/v4.0.1", "object": {"sha": "a" * 40, "type": "commit"}}]
        with self.assertRaisesRegex(ValueError, "tested commit"):
            self.archive()
        self.assertTrue(self.gh.releases[0]["draft"])
        self.assertEqual(self.zenodo.records, [])

    def test_minor_label_and_reviewed_release_notes(self):
        self.gh.prs = [{"merged_at": "2026-10-05T00:00:00Z", "base": {"ref": "stable"},
                       "merge_commit_sha": self.commit, "labels": [{"name": "release:minor"}],
                       "body": "## Release notes\n### Added\n* Release history"}]
        context = self.prepare()
        self.assertEqual(context["state"]["version"], "4.1.0")
        self.assertEqual(context["state"]["notes"], "### Added\n* Release history")

    def test_published_release_with_unfinished_images_blocks_next_commit(self):
        self.prepare()
        self.archive()
        self.advance()
        with self.assertRaisesRegex(ValueError, "unfinished"):
            self.prepare()
        self.assertEqual(len(self.gh.releases), 1)

    def test_draft_blocks_next_commit(self):
        self.prepare()
        self.advance()
        with self.assertRaisesRegex(ValueError, "unfinished"):
            self.prepare()

    def test_out_of_order_stable_commit_fails_before_mutation(self):
        older = self.commit
        self.advance()
        self.prepare()
        self.archive()
        self.images()
        subject.finish(self.gh, self.output)
        subprocess.run(["git", "checkout", "--detach", older], check=True, capture_output=True)
        calls = len(self.gh.calls)
        with self.assertRaisesRegex(ValueError, "Release order"):
            subject.prepare(self.gh, CONFIG, older, Path("older-output"))
        self.assertFalse(any(method == "POST" for _, method in self.gh.calls[calls:]))

    def test_nonstable_checkout_cannot_release(self):
        Path("code.txt").write_text("unmerged code")
        subprocess.run(["git", "add", "code.txt"], check=True)
        subprocess.run(["git", "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-qm", "unmerged"], check=True)
        with self.assertRaisesRegex(ValueError, "must be on stable"):
            subject.prepare(self.gh, CONFIG, subject.git("rev-parse", "HEAD"), self.output)
        self.assertEqual(self.gh.releases, [])


if __name__ == "__main__":
    unittest.main()
