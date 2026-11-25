import { readFile, writeFile } from "node:fs/promises";
import { gzipSync } from "node:zlib";

const [input, output] = process.argv.slice(2);
if (!input || !output) {
    throw new Error("Pass the pinned deployment's public HTML file and the output Worker path.");
}
const html = await readFile(input, "utf8");
if (!html.includes("CardUniverse · Admin Command Center") || !html.includes("/unified-sync.js")) {
    throw new Error("The public HTML must contain the current CardUniverse Admin Console.");
}
const styled = html.replace("</head>", '<link rel="stylesheet" href="/firstlight-console.css"></head>');
if (styled === html) throw new Error("The public console is missing its head element.");
const template = await readFile(new URL("current-console-overlay.mjs", import.meta.url), "utf8");
const source = template.replace("/* PUBLIC_CONSOLE_HTML */ null", JSON.stringify(gzipSync(styled).toString("base64")));
await writeFile(output, source);
