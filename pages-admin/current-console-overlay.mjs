const previous = "https://3781239c.carduniverse-admin.pages.dev";
const assets = /* PUBLIC_CONSOLE_ASSETS */ {};
const pageHeaders = {
    "content-type": "text/html; charset=utf-8",
    "content-security-policy": "default-src 'self' https://fonts.googleapis.com https://fonts.gstatic.com; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data: blob: https:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "x-robots-tag": "noindex, nofollow"
};

const consoleStyles = `
#cu-nav { background: linear-gradient(165deg, #1e2635, #101720 68%); }
#cu-top { background: linear-gradient(103deg, #292d38, #161e2a 75%); }
#cu-nav a[aria-current="page"] {
    background: linear-gradient(105deg,#d3aa5e2c,#d3aa5e0c);
    color: #ffe3af;
    box-shadow: inset 2px 0 #dfba76;
}
#cu-nav a:focus-visible, #cu-nav summary:focus-visible, #cu-top button:focus-visible {
    outline: 2px solid #e3bf7e;
    outline-offset: 2px;
}
`;

function replaceOnce(source, original, replacement) {
    const offset = source.indexOf(original);
    if (offset < 0 || source.indexOf(original, offset + original.length) >= 0) {
        throw new Error("The pinned admin module has changed.");
    }
    return source.slice(0, offset) + replacement + source.slice(offset + original.length);
}

function updateOperations(source) {
    const capacityInput = '      <label>Member limit <input id="ops-guild-capacity" type="number" required min="5" max="1000" step="1"></label>\n';
    source = replaceOnce(source, JSON.stringify(capacityInput).slice(1, -1), "");
    source = replaceOnce(source, '    const guildCapacity = byId("ops-guild-capacity");\n', "");
    source = replaceOnce(source, "        guildCapacity.disabled = !guild;\n", "");
    source = replaceOnce(source, "Server limit 5–1,000", "Fixed capacity 50");
    source = replaceOnce(source, "        guildCapacity.min = Math.max(5, guild?.member_count ?? 0);\n", "");
    source = replaceOnce(source, '        guildCapacity.value = guild?.member_limit ?? "";\n', "");
    source = replaceOnce(source, "            member_limit:guildCapacity.valueAsNumber,\n", "");
    return replaceOnce(source, "[guildCapacity,guildLevel", "[guildLevel");
}

function updateRecordEditor(source) {
    source = replaceOnce(source, 'N("member_limit","Member capacity",5,1000),', "");
    const marker = "if(meta.children.length)right.append(meta);";
    source = replaceOnce(source, marker,
        'if(type==="guild"){const cap=e("div",undefined,"cue-metric");' +
        'cap.append(e("small","Fixed capacity"),e("b",50));meta.append(cap);}' + marker);
    source = replaceOnce(source, "export async function mount(id,host){", "export async function mount(id,host,preferred){");
    source = replaceOnce(source, "const v=++generation,c=cfg[type];",
        'if(id==="live")history.replaceState(null,"","/#live/"+type);const v=++generation,c=cfg[type];');
    return replaceOnce(source, "await choose(types[0]);}", "await choose(types.includes(preferred)?preferred:types[0]);}");
}

export default {
    async fetch(request) {
        const url = new URL(request.url);
        const read = request.method === "GET" || request.method === "HEAD";
        const key = url.pathname === "/login" ? "/" : url.pathname;
        if (read && Object.hasOwn(assets, key)) {
            const contentType = key === "/" ? "text/html" : key.endsWith(".css") ? "text/css" : "text/javascript";
            const responseHeaders = {
                ...pageHeaders,
                "content-type": contentType + "; charset=utf-8"
            };
            if (request.method === "HEAD") return new Response(null, {headers: responseHeaders});
            const bytes = Uint8Array.from(atob(assets[key]), value => value.charCodeAt(0));
            const body = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"));
            return new Response(body, {headers: responseHeaders});
        }
        if (read && url.pathname === "/firstlight-console.css") {
            return new Response(request.method === "HEAD" ? null : consoleStyles, {
                headers: {"content-type":"text/css; charset=utf-8", "cache-control":"no-store"}
            });
        }

        const target = new URL(previous);
        target.pathname = url.pathname;
        target.search = url.search;
        const headers = new Headers(request.headers);
        headers.delete("host");
        const origin = headers.get("origin");
        if (!read && origin && origin !== url.origin) {
            return Response.json({ok:false,error:"Request origin did not match this console."}, {status:403});
        }
        if (origin === url.origin) headers.set("origin", previous);
        const result = await fetch(target, {
            method: request.method, headers, body: read ? null : request.body, redirect: "manual"
        });
        const outgoing = new Headers(result.headers);
        const location = outgoing.get("location");
        if (location) {
            const redirect = new URL(location, previous);
            if (redirect.origin === previous) outgoing.set("location", redirect.pathname + redirect.search + redirect.hash);
        }

        const editor = url.pathname === "/unified-ops.js" ? updateOperations
            : url.pathname === "/unified-editor.js" ? updateRecordEditor : null;
        if (request.method === "GET" && editor && result.ok) {
            // This route serves one bounded JavaScript module from the pinned deployment.
            const source = await result.text();
            if (source.length > 150000) return new Response("Admin tools unavailable", {status: 502});
            outgoing.delete("content-length");
            outgoing.delete("content-encoding");
            outgoing.delete("etag");
            try {
                return new Response(editor(source), {status: result.status, headers: outgoing});
            } catch (error) {
                console.error("Cannot prepare admin editor", error.message);
                return new Response(source, {status: result.status, headers: outgoing});
            }
        }

        return new Response(result.body, {status: result.status, headers: outgoing});
    }
};
