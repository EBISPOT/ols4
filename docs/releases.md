# OLS software releases

Every push to `stable` starts Build & Test. Protect `stable` so changes enter by
reviewed pull requests. After backend, dataload, API, frontend and release-contract
checks pass, the same run creates one numbered software release. Merging `stable`
back into `dev`, feature PRs, and dev pushes do not create stable releases.
Deployment and ontology data updates remain separate actions.

## Releasing

1. Open the release PR into `stable`. The optional template is available with
   `?template=stable-release.md` on the GitHub compare/create-PR URL.
2. Review the `## Release notes` section in the PR body. Include Added, Changed,
   Fixed, breaking changes and migration instructions when relevant. If the section
   is absent or empty, GitHub generates notes from changes since the previous
   numbered release.
3. Choose one label: `release:patch` (default), `release:minor`, or `release:major`.
   Multiple bump labels fail preparation instead of silently choosing one.
4. Merge. Version selection, tagging, GitHub publication, Zenodo archiving and
   versioned image builds happen automatically after the checks pass.
5. Wait for the complete workflow to succeed, then deploy the versioned images or
   the digests from the release's `release-manifest.json` through the existing
   deployment process. A published GitHub source release may still be waiting for
   archiving or image builds; its body shows that images are being prepared.

Versions use MAJOR.MINOR.PATCH. Patch means compatible fixes; minor means compatible
features; major means breaking changes. The initial baseline is `4.0.0`, matching
the existing frontend version. The first default patch release is `4.0.1`; a first
`release:minor` release is `4.1.0`. Configure the baseline in
`.github/release/config.json` before the first release if a different start is needed.
Maven module and npm package versions retain their existing build identifiers;
GitHub tags and the generated release manifest identify the software release.

## GitHub archiving setup

The software starts a **fresh Zenodo DOI series** through Zenodo's native GitHub
integration. The paper's DOI is not used as a software DOI, concept DOI or base
record. No existing DOI is configured or written into the repository's citation
metadata. The first archived software release establishes the software concept
DOI; each later release receives a distinct version DOI in that series.

- Connect your GitHub account in Zenodo and enable `EBISPOT/ols4`. This requires
  repository admin rights and EBISPOT approval of the Zenodo application.
  The enabled repository and active release webhook were verified during setup.
- `CITATION.cff` describes OLS software, its authors, repository and license. It
  deliberately omits DOI, version and release date: Zenodo determines the version
  and date from the GitHub release, and assigns a new software DOI.
- The `release:patch`, `release:minor`, and `release:major` labels are configured.
  Without a bump label, the release increments the patch version.
- There is **no Zenodo API token** and no deposition/upload/publish API call in
  the workflow. Zenodo handles archiving through its GitHub webhook. The workflow
  only queries public records to discover the completed software archive.
- GitHub's `GITHUB_TOKEN` needs contents/packages write permissions. OLS uses `dev`
  as its default branch; merge workflow changes into dev before releasing stable.
  GitHub restricts release creation/update when target workflow files differ from
  the default branch: that case needs a GitHub App or token with Workflows write
  permission, rather than the built-in token. It is independent of Zenodo access.
- Grant another OLS maintainer management access to the software record after its
  first publication, so maintenance can continue across account changes.
- Stable pushes are queued with `queue: max` (up to 100 pending runs). Queue order
  follows arrival, not commit order; ancestry checks stop out-of-order publication.
  Avoid merging another stable release until the previous one finishes.

Setup and archiving are described in Zenodo's
[repository guide](https://help.zenodo.org/docs/github/enable-repository/) and
[GitHub release guide](https://help.zenodo.org/docs/github/archive-software/github-upload/).
The optional GitHub workflow permission requirement is documented in the
[release API](https://docs.github.com/en/rest/releases/releases#create-a-release).

## Publication and recovery

Preparation verifies that the checked-out SHA is on stable, chooses the version
and release notes, and creates a GitHub draft with durable state. An unfinished
release blocks the next release, including one whose source is already public but
whose DOI or images are still pending.

The workflow creates an immutable lightweight tag at the tested SHA and publishes
that GitHub source release. Zenodo's native webhook archives the **tagged repository
source ZIP** and assigns its software DOI. It does not archive the ontology database
or the later enriched GitHub assets. The workflow never uploads or changes Zenodo
files, never creates a second series through the API, and never republishes a
GitHub release to retry archiving.

Public DOI discovery matches the exact GitHub tag/release URL, the version and
software resource type. Paper records, other repositories and other versions are
excluded. Ambiguous matches fail instead of choosing a DOI. After the first release,
the software concept DOI must match the previous release's series. Public indexing
can lag: polling lasts up to 30 minutes, configured by `archiveWaitSeconds` and
`archivePollSeconds`. Transient HTTP 429/5xx errors are retried.

After the DOI is available, every component is built for amd64 and arm64 from that
exact SHA with the same version/date/DOI manifest. Successful image digests are
saved as GitHub release assets. The frontend/backend contain the manifest;
dataload/apitester contain it at `/opt/ols/release.json`. OCI labels identify version,
revision and source. Build caches use each component's `release-buildcache` tag.

Finishing adds `release-manifest.json` and a deterministic, enriched source archive
to the GitHub release. That archive contains the tagged source plus generated
release metadata, notes, image digests and a version-specific `CITATION.cff`.
It is distinct from the source ZIP automatically preserved by Zenodo. The release
body is then updated to show its software DOI and completed image availability.

The recorded image digests are promoted to `stable` only if that SHA is still the
stable branch tip, so an older rerun cannot move aliases backwards. Alias updates
are sequential across components; deploy explicit version tags/digests. The older
Docker publishing workflow no longer publishes stable images independently.

If anything fails, use **Re-run failed jobs** or **Re-run all jobs** on the original
Build & Test run. Durable GitHub state/assets recover the same version, DOI and
completed images, including after a lost publication response. Archiving happens
only on the initial source release publication. If Zenodo has not produced the DOI,
open `EBISPOT/ols4` in Zenodo's GitHub settings and inspect that release's errors.
Fix any integration issue there and rerun the original workflow once its record is
available. Do not create another release or delete/edit managed tags, hidden state
or release assets to bypass a pending release.

## Website and API

The homepage displays the frontend image's bundled version with release-notes and
software version-DOI links. `/releases` shows its bundled software release history,
newest first, and links the software concept DOI once the first release exists.
The backend exposes its own bundled manifest at `GET /api/v2/releases` without a
database. Neither website nor backend makes runtime GitHub/Zenodo requests.

Development/local builds display “Development build”, have no numbered release
history and show no software DOI until a real release exists. Older deployments
retain their own version/history. **Data updated** comes from `/api/v2/stats` and
is independent of the software **Released** date. Cite the version DOI for an exact
software archive; the software concept DOI identifies all archived versions.

## Validation

- `python3 -m unittest discover -s .github/release -p 'test_*.py' -v` exercises native
  webhook publication, public DOI discovery, successive software releases, delayed
  indexing, timeouts, partial builds and lost external responses with fake providers
  and a real temporary Git repository. It makes no real releases or deposits.
- `V2ReleaseControllerWIT` tests the endpoint with bundled metadata and a development
  build without PostgreSQL; development has no invented software concept DOI.
- CI builds the frontend and runs the existing assembled backend/dataload/API checks
  before invoking the publisher. End-to-end webhook archiving can be verified only
  after a real release; local tests do not claim a minted software DOI.
