import { readFile, writeFile } from "node:fs/promises";
import { gzipSync } from "node:zlib";

const [input, output, extraModule] = process.argv.slice(2);
if (!input || !output || !extraModule) {
    throw new Error("Pass the pinned console HTML, output Worker path, and pinned unified-extra.js.");
}

function replaceOnce(source, before, after) {
    const start = source.indexOf(before);
    if (start < 0 || source.indexOf(before, start + before.length) >= 0) {
        throw new Error("Could not identify the pinned console template.");
    }
    return source.slice(0, start) + after + source.slice(start + before.length);
}

const local = name => readFile(new URL(name, import.meta.url), "utf8");
const [html, extra, overview, styles, template] = await Promise.all([
    readFile(input, "utf8"),
    readFile(extraModule, "utf8"),
    local("firstlight-overview.js"),
    local("firstlight-overview.css"),
    local("current-console-overlay.mjs")
]);
if (!html.includes("CardUniverse · Admin Command Center") || !html.includes("/unified-sync.js") ||
        !extra.includes('import("/unified-editor.js")') || !extra.includes('export async function mount')) {
    throw new Error("The inputs must contain the pinned CardUniverse console and management module.");
}

let page = replaceOnce(html, "</head>", '<link rel="stylesheet" href="/firstlight-console.css"><link rel="stylesheet" href="/firstlight-overview.css"></head>');
page = replaceOnce(page, 'return labels.has(s)?s:"overview"}',
    'const section=s.split("/",1)[0];return labels.has(section)?section:"overview"}');
page = replaceOnce(page, 'adminSection=id;history.replaceState(null,"","/#"+id);draw();try{if(id==="sync-diagnostics")',
    'adminSection=id;const category=id==="live"?location.hash.match(/^#live\\/([a-zA-Z]+)$/)?.[1]:null;' +
    'history.replaceState(null,"","/#"+id+(category?"/"+category:""));draw();try{if(id==="sync-diagnostics")');
page = replaceOnce(page, 'else if(legacy.has(id)){await base();}',
    'else if(id==="overview"){const mount=document.createElement("div");root.replaceChildren(mount);' +
    'const mod=await import("/firstlight-overview.js");if(v===sequence&&mount.isConnected)mod.mount(mount);}' +
    'else if(legacy.has(id)){await base();}');
const tools = replaceOnce(extra, 'await m.mount("live",host);',
    'await m.mount("live",host,location.hash.match(/^#live\\/([a-zA-Z]+)$/)?.[1]);')
    .replace('Pause or resume incoming actions on Android and web.', 'Edit settings and records used by Android and web.');

const pack = source => gzipSync(source).toString("base64");
const assets = {
    "/": pack(page),
    "/unified-extra.js": pack(tools),
    "/firstlight-overview.js": pack(overview),
    "/firstlight-overview.css": pack(styles)
};
await writeFile(output, replaceOnce(template, "/* PUBLIC_CONSOLE_ASSETS */ {}", JSON.stringify(assets)));
