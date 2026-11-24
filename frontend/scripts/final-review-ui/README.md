# Final tree review browser regression

Build with `node scripts/final-review-ui/build.mjs`, serve the built frontend against the changed local backend and open `/final-review-ui/` with CUA. Click **Run final review regressions**. This uses real HTTP and production public widgets without a Router, not mocked fetch.

Requires isolated manual `finalsubsets` and `finalpaging` fixtures: real subset definition properties; imported-only, obsolete-only and overlapping members; a property root with 1001 children; an individual root with 1001 hierarchical-only children. The setup script and SQL with exact provenance are retained in the final-fix evidence directory. Do not reset other fixtures. Tests assert selection/zero/empty/restoration, final page, search/empty/clear/focus, sibling independence and actual callback entity type.

Separately verify the app Properties and Individuals tabs and the RDF-loaded mixed I/K/L paths with Preferred/All roots, subset and visibility controls. Harness success does not replace actual-loaded provenance.
