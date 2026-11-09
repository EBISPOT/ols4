import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, test } from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";
import { build } from "esbuild";

const testDir = await mkdtemp(join(tmpdir(), "ols-ontology-test-"));
after(() => rm(testDir, { recursive: true, force: true }));
const bundlePath = join(testDir, "Ontology.mjs");
await build({
  entryPoints: [fileURLToPath(new URL("../src/model/Ontology.ts", import.meta.url))],
  bundle: true,
  platform: "node",
  format: "esm",
  outfile: bundlePath,
});
const { default: Ontology } = await import(pathToFileURL(bundlePath).href);

test("identifies OBO Foundry membership from is_foundry: true", () => {
  assert.equal(new Ontology({ is_foundry: true }).isFoundry(), true);
});

for (const value of [false, undefined, null, "true", "false", "", 0, 1, [], {}, [true]]) {
  test(`does not infer Foundry membership from ${JSON.stringify(value)}`, () => {
    assert.equal(new Ontology({ is_foundry: value }).isFoundry(), false);
  });
}

test("does not infer Foundry membership when is_foundry is absent", () => {
  assert.equal(new Ontology({}).isFoundry(), false);
});

test("ignores the obsolete in_foundry_order field", () => {
  assert.equal(new Ontology({ in_foundry_order: 1 }).isFoundry(), false);
  assert.equal(new Ontology({ is_foundry: false, in_foundry_order: 1 }).isFoundry(), false);
  assert.equal(new Ontology({ is_foundry: true, in_foundry_order: 0 }).isFoundry(), true);
});

test("does not treat the misspelled is_foundary field as membership", () => {
  assert.equal(new Ontology({ is_foundary: true }).isFoundry(), false);
});
