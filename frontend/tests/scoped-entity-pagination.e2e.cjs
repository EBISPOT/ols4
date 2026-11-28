// Run against two locally served OLS builds with GO and COHO loaded in the same database.
// PLAYWRIGHT_MODULE can point to an external Playwright installation.
// node tests/scoped-entity-pagination.e2e.cjs BASELINE_URL CANDIDATE_URL OUTPUT_DIR
const assert = require("node:assert/strict");
const { createHash } = require("node:crypto");
const { mkdir, writeFile } = require("node:fs/promises");
const { join } = require("node:path");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");

const [baselineUrl, candidateUrl, outputDir] = process.argv.slice(2);
const runs = Number(process.env.OLS_E2E_RUNS || 4);
const [listOntology, searchOntology] = (process.env.OLS_E2E_ONTOLOGIES || "go,coho").split(",");
const plural = { class: "classes", individual: "individuals", property: "properties" };
const entityType = (entity) => entity.type.find((type) => Object.hasOwn(plural, type));

async function verifyPage(page, origin, listing, pageNumber, action) {
  const isList = listing === "list";
  const ontology = isList ? listOntology : searchOntology;
  const pathname = isList ? `/api/v2/ontologies/${ontology}/classes` : "/api/v2/entities";
  const start = performance.now();
  const [response] = await Promise.all([
    page.waitForResponse((response) => {
      const requestUrl = new URL(response.url());
      const params = requestUrl.searchParams;
      return requestUrl.origin === origin && requestUrl.pathname === pathname
        && params.get("page") === String(pageNumber)
        && (isList || params.get("ontologyId") === ontology);
    }),
    action(),
  ]);
  assert.equal(response.status(), 200, response.url());
  const url = new URL(response.url());
  // The scoped Search uses the API's default mixed types and default order.
  for (const param of ["search", "type", "sort"]) assert(!url.searchParams.has(param), response.url());
  const body = await response.body();
  const responseMs = performance.now() - start;
  const data = JSON.parse(body);
  assert.equal(data.page, pageNumber);
  assert(data.elements.length > 0, response.url());
  assert(data.elements.every((entity) => entity.ontologyId === ontology));
  assert(data.elements.every((entity) => !entity.isObsolete));
  const types = [...new Set(data.elements.map(entityType))];

  if (isList) {
    const shortForms = data.elements.map((entity) => entity.curie || entity.shortForm || "(no data)");
    await page.waitForFunction((expected) => {
      const rows = [...document.querySelectorAll("main table tbody tr")];
      return JSON.stringify(rows.map((row) => row.cells[1].textContent.trim())) === JSON.stringify(expected);
    }, shortForms);
  } else {
    const selector = `main a.link-default.text-xl[href^="/ontologies/${ontology}/"]`;
    const hrefs = data.elements.map((entity) =>
      `/ontologies/${ontology}/${plural[entityType(entity)]}/${encodeURIComponent(encodeURIComponent(entity.iri))}`);
    await page.waitForFunction(({ selector, hrefs }) => {
      const actual = [...document.querySelectorAll(selector)].map((link) => link.getAttribute("href"));
      return JSON.stringify(actual) === JSON.stringify(hrefs);
    }, { selector, hrefs });
  }

  return {
    body, report: { request: url.pathname + url.search, page: pageNumber, types,
      sha256: createHash("sha256").update(body).digest("hex"), count: data.elements.length,
      totalPages: data.totalPages, totalElements: data.totalElements,
      responseMs, visibleMs: performance.now() - start },
  };
}

async function verifyTreeSubsets(page, origin) {
  const subset = "http://purl.obolibrary.org/obo/go#goslim_generic";
  const start = performance.now();
  const [response] = await Promise.all([
    page.waitForResponse((response) => {
      const requestUrl = new URL(response.url());
      const params = requestUrl.searchParams;
      return requestUrl.origin === origin && requestUrl.pathname === "/api/v2/ontologies/go/entities"
        && params.get("type") === "class,individual"
        && params.get("includeObsoleteEntities") === "false"
        && params.get("facetFields") === "subset" && params.get("size") === "1";
    }),
    page.getByRole("button", { name: "Tree", exact: true }).click(),
  ]);
  assert.equal(response.status(), 200, response.url());
  const body = await response.body();
  const responseMs = performance.now() - start;
  const data = JSON.parse(body);
  const count = data.facetFieldsToCounts.subset[subset];
  assert(count > 0);
  assert.equal(data.elements.length, 1);
  await page.getByText(/^Filter by subset \(\d+\)$/).waitFor();
  const checkbox = page.getByRole("checkbox", { name: `goslim_generic (${count.toLocaleString("en-US")})` });
  await checkbox.waitFor();
  assert.equal(await checkbox.isChecked(), false);
  const url = new URL(response.url());
  return {
    body, report: { request: url.pathname + url.search, totalElements: data.totalElements, subsetCount: count,
      sha256: createHash("sha256").update(body).digest("hex"), responseMs, visibleMs: performance.now() - start },
  };
}

async function run(browser, baseUrl, label, saveScreenshots) {
  const origin = new URL(baseUrl).origin;
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  await context.route("**/*", (route) => {
    const url = new URL(route.request().url());
    return url.origin === origin ? route.continue() : route.abort();
  });
  const page = await context.newPage();
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  const results = {};
  try {
    results.listFirst = await verifyPage(page, origin, "list", 0, () =>
      page.goto(`${origin}/ontologies/${listOntology}?viewMode=list`, { waitUntil: "domcontentloaded" }));
    assert.deepEqual(results.listFirst.report.types, ["class"]);
    await page.getByRole("button", { name: "List", exact: true }).waitFor();
    results.listNext = await verifyPage(page, origin, "list", 1, () =>
      page.getByRole("button", { name: "Next", exact: true }).click());
    if (saveScreenshots) await page.screenshot({ path: join(outputDir, `${label}-list.png`) });

    if (listOntology !== "go") {
      await page.goto(`${origin}/ontologies/go?viewMode=list`, { waitUntil: "domcontentloaded" });
    }
    results.treeSubsets = await verifyTreeSubsets(page, origin);
    if (saveScreenshots) await page.screenshot({ path: join(outputDir, `${label}-tree.png`) });

    results.searchFirst = await verifyPage(page, origin, "search", 0, () =>
      page.goto(`${origin}/search?ontologyId=${searchOntology}`, { waitUntil: "domcontentloaded" }));
    results.searchNext = await verifyPage(page, origin, "search", 1, () =>
      page.getByRole("button", { name: "Next", exact: true }).first().click());
    const { totalPages } = results.searchFirst.report;
    results.searchLast = await verifyPage(page, origin, "search", totalPages - 1, () =>
      page.getByRole("button", { name: String(totalPages), exact: true }).first().click());
    assert(new Set([
      ...results.searchFirst.report.types, ...results.searchLast.report.types,
    ]).size > 1, "The scoped search should display more than one entity type");
    assert.equal(results.searchFirst.report.totalElements, results.searchLast.report.totalElements);
    assert.deepEqual(pageErrors, []);
    if (saveScreenshots) await page.screenshot({ path: join(outputDir, `${label}-search.png`) });
    return results;
  } finally {
    await context.close();
  }
}

(async () => {
  assert(baselineUrl && candidateUrl && outputDir);
  await mkdir(outputDir, { recursive: true });
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH });
  const report = [];
  try {
    for (let index = 0; index <= runs; index++) {
      const pair = {};
      for (const label of index % 2 ? ["candidate", "baseline"] : ["baseline", "candidate"]) {
        pair[label] = await run(browser, label === "baseline" ? baselineUrl : candidateUrl, label, index === 1);
      }
      for (const name of Object.keys(pair.baseline)) {
        assert(pair.baseline[name].body.equals(pair.candidate[name].body), `Response differs for ${name}`);
      }
      if (index === 0) continue;
      report.push(Object.fromEntries(Object.entries(pair).map(([label, results]) =>
        [label, Object.fromEntries(Object.entries(results).map(([name, result]) => [name, result.report]))])));
      await writeFile(join(outputDir, "browser-report.json"), JSON.stringify(report, null, 2));
      console.log(`Pair ${index}: list rows, tree subset control, mixed search pages, and response bodies matched`);
    }
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
