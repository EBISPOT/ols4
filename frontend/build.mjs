
import { build } from "esbuild";
import fs from 'fs'
import { exec } from 'child_process'

let define = {}
for (const k in process.env) { define[`process.env.${k}`] = JSON.stringify(process.env[k]) }

///
/// Build index.html (simple find and replace)
///
console.log('### Building index.html')
fs.writeFileSync('dist/index.html',
	fs.readFileSync('index.html.in')
		.toString()
		.split('%PUBLIC_URL%').join(process.env.PUBLIC_URL || ''));

///
/// Build bundle.js (esbuild)
///
console.log('### Building bundle.js')
build({
	entryPoints: ["src/index.tsx"],
	bundle: true,
	platform: 'browser',
	outfile: "dist/bundle.js",
	define,
	plugins: [
	],
	logLevel: 'info',

	...(process.env.REACT_APP_ENV === 'prod' ? {
		sourcemap: false,
		minify: true
	} : {
		sourcemap: 'inline'
	})
});


///
/// Build styles.css (tailwind)
///
console.log('### Building styles.css')
exec('tailwind -i ./src/index.css -o ./dist/styles.css')



