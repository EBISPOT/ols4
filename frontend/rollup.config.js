import typescript from "@rollup/plugin-typescript";
import replace from "@rollup/plugin-replace";
import dts from "rollup-plugin-dts";
export default [
  {
    input: "src/index.ts",
    external: ["@mui/material", "react", "react-router-dom"],
    output: {
      dir: "dist/entity-tree-esm",
      format: "esm",
      sourcemap: true,
    },
    plugins: [
      replace({"process.env.NODE_ENV": JSON.stringify("development")}),
      typescript({ tsconfig: "./tsconfig.json" })
    ],
  },{
    input: "dist/entity-tree-esm/index.d.ts",
    output: {
      file: 'entity-tree.d.ts',
      format: 'es'
    },
    plugins: [dts()]
  }
];
