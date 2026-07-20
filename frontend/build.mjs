import { exec } from "child_process";
import { build } from "esbuild";
import fs from "fs";

// These values are supplied when the web container starts, not when the
// JavaScript bundle is built. This lets one immutable image serve each OLS
// environment while keeping Node and npm out of the runtime image.
const runtimeEnvKeys = [
  "PUBLIC_URL",
  "REACT_APP_APIURL",
  "REACT_APP_EBI_HOME",
  "REACT_APP_EBI_LICENSING",
  "REACT_APP_OBO_FOUNDRY_REPO_RAW",
  "REACT_APP_SPOT_HOME",
  "REACT_APP_SPOT_OLS4_REPO",
  "REACT_APP_SPOT_OLS_ANNOUNCE",
  "REACT_APP_SPOT_ONTOTOOLS",
  "REACT_APP_SPOT_OXO",
  "REACT_APP_SPOT_ZOOMA",
  "REACT_APP_UMAP_URL",
];

const define = Object.fromEntries(
  runtimeEnvKeys.map((key) => [
    `process.env.${key}`,
    `window.__OLS_RUNTIME_CONFIG__.${key}`,
  ]),
);

const renderIndex = (publicUrl) =>
  fs
    .readFileSync("index.html.in")
    .toString()
    .split("%PUBLIC_URL%/")
    .join(publicUrl)
    .split("%PUBLIC_URL%")
    .join(publicUrl);

const runtimeConfig = Object.fromEntries(
  runtimeEnvKeys.map((key) => [key, process.env[key] || ""]),
);

///
/// Build index.html (simple find and replace)
///
console.log("### Building index.html");
fs.writeFileSync(
  "dist/index.html",
  renderIndex(process.env.PUBLIC_URL || "/"),
);
fs.writeFileSync("dist/index.html.template", renderIndex("__OLS_PUBLIC_URL__"));
fs.writeFileSync(
  "dist/runtime-config.js",
  `window.__OLS_RUNTIME_CONFIG__ = Object.freeze(${JSON.stringify(runtimeConfig)});\n`,
);

///
/// Build bundle.js (esbuild)
///
console.log("### Building bundle.js");
build({
  entryPoints: ["src/index.tsx"],
  bundle: true,
  platform: "browser",
  outfile: "dist/bundle.js",
  define,
  plugins: [],
  logLevel: "info",
  sourcemap: "linked",

  ...(process.env.OLS_MINIFY === "true"
    ? {
        minify: true,
      }
    : {}),
});

///
/// Build styles.css (tailwind)
///
console.log("### Building styles.css");
exec("tailwind -i ./src/index.css -o ./dist/styles.css");

///
/// Copy files
///
console.log("### Copying misc files");
exec("cp ./src/banner.txt ./dist"); // home page banner text

console.log("### Copying static assets");
fs.cpSync("public", "dist", { recursive: true });
