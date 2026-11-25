import { readFile, mkdir, writeFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";

const read=name=>readFile(new URL(name,import.meta.url),"utf8");
const files={
    "/console-assets/app.css":["text/css; charset=utf-8",await read("app.css")],
    "/console-assets/app.js":["text/javascript; charset=utf-8",await read("app.js")],
    "/console-assets/legacy.js":["text/javascript; charset=utf-8",await read("legacy.js")]
};
const assets={html:await read("index.html"),files};
const output=resolve(process.argv[2] || "dist/_worker.js");
await mkdir(dirname(output),{recursive:true});
await writeFile(output,"const ASSETS = "+JSON.stringify(assets)+";\n"+await read("worker.mjs"));
console.log(output);
