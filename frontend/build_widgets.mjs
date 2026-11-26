
import { build } from "esbuild";
import { mkdir, readFile, writeFile } from "node:fs/promises";

let define = {}
for (const k in process.env) { define[`process.env.${k}`] = JSON.stringify(process.env[k]) }

console.log('### Building ols4_widgets.js')
build({
	entryPoints: ["src/widgets/index.ts"],
	bundle: true,
	platform: 'browser',
	outfile: "dist_widgets/ols4_widgets.js",
	define,
	plugins: [
	],
	logLevel: 'info',
	sourcemap: 'linked',
	format: "esm"
});

console.log('### Building ols4_widgets.min.js')
build({
	entryPoints: ["src/widgets/index.ts"],
	bundle: true,
	platform: 'browser',
	outfile: "dist_widgets/ols4_widgets.min.js",
	define,
	plugins: [
	],
	logLevel: 'info',
	minify: true,
	sourcemap: 'linked',
	format: "esm"
});

console.log("### Building treestyles.css");
await mkdir("dist_widgets", { recursive: true });
const stylesheets = await Promise.all(
  ["treestyles.css", "entity-tree.css"].map(file => readFile(`src/widgets/styles/${file}`, "utf8"))
);
await writeFile("dist_widgets/treestyles.css", stylesheets.join("\n"));
