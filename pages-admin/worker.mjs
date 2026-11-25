const GAME_API = "https://carduniverse.inipastiuy-41f.workers.dev";
const LEGACY_PAGES = "https://0b771c72.carduniverse-admin.pages.dev";

function response(body, {status=200,headers={}} = {}) {
    return new Response(body,{status,headers:{
        "cache-control":"no-store",
        "x-content-type-options":"nosniff",
        "x-robots-tag":"noindex, nofollow",
        ...headers
    }});
}

function sessionCookie(request) {
    return (request.headers.get("Cookie") || "").split(";").map(value=>value.trim())
        .find(value=>value.startsWith("cu_session="));
}

async function upstream(request, origin, pathname, search, useSession) {
    const target = new URL(origin);
    target.pathname = pathname;
    target.search = search;
    const headers = new Headers();
    for (const name of ["Accept","Content-Type","X-Requested-With"]) {
        const value=request.headers.get(name);
        if(value) headers.set(name,value);
    }
    if (useSession) {
        const cookie=sessionCookie(request);
        if(cookie) headers.set("Cookie",cookie);
    }
    return fetch(target,{
        method:request.method,headers,body:["GET","HEAD"].includes(request.method)?undefined:request.body,
        redirect:"manual"
    });
}

function proxied(result, currentOrigin) {
    const headers = new Headers(result.headers);
    headers.set("cache-control","no-store");
    headers.set("x-content-type-options","nosniff");
    headers.set("x-robots-tag","noindex, nofollow");
    headers.delete("access-control-allow-origin");
    headers.delete("access-control-allow-credentials");
    const redirect=headers.get("Location");
    if (redirect) {
        const target=new URL(redirect,currentOrigin);
        if (target.origin===GAME_API || target.origin===LEGACY_PAGES) {
            headers.set("Location",target.pathname+target.search+target.hash);
        } else if (target.origin!==currentOrigin) {
            return response("Unexpected upstream redirect.",{status:502});
        }
    }
    return new Response(result.body,{status:result.status,headers});
}

async function verifyAdmin(request) {
    const verified=await upstream(new Request(request,{method:"GET"}),GAME_API,"/api/admin/profile","",true);
    if (verified.status===401) return response(null,{status:302,headers:{Location:"/login"}});
    if (verified.status===403) return response("Master Admin access required.",{status:403});
    if (!verified.ok) return response("Could not verify Master Admin access.",{status:502});
    return null;
}

async function handle(request) {
    const url=new URL(request.url);
    const path=url.pathname;
    const head=request.method==="HEAD";
    const read=request.method==="GET"||head;
    if(path.startsWith("/api/")){
        const origin=request.headers.get("Origin");
        if(!read&&origin&&origin!==url.origin){
            return response(JSON.stringify({ok:false,error:"Request origin did not match this console."}),{
                status:403,headers:{"content-type":"application/json; charset=utf-8"}
            });
        }
        return proxied(await upstream(request,GAME_API,path,url.search,true),url.origin);
    }
    if(!read) return response("Method not allowed.",{status:405,headers:{Allow:"GET, HEAD"}});
    if(path==="/" || path==="/login"){
        return response(head?null:ASSETS.html,{headers:{
            "content-type":"text/html; charset=utf-8",
            "content-security-policy":"default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'"
        }});
    }
    if(Object.hasOwn(ASSETS.files,path)){
        const [type,content]=ASSETS.files[path];
        return response(head?null:content,{headers:{"content-type":type}});
    }
    if(path==="/legacy"){
        const denied=await verifyAdmin(request);
        if (denied) return denied;
        const result=await upstream(request,LEGACY_PAGES,"/","",false);
        if(!result.ok) return response("The original administration tools are temporarily unavailable.",{status:502});
        if(head) return response(null,{headers:{"content-type":"text/html; charset=utf-8"}});
        const html=await result.text();
        const enhanced=html.replace(/<\/body\s*>/i,'<script src="/console-assets/legacy.js" defer></script></body>');
        return response(enhanced,{headers:{"content-type":"text/html; charset=utf-8","x-frame-options":"DENY"}});
    }
    if (["/admin/operations","/admin/phase6","/admin/support"].includes(path)) {
        const denied=await verifyAdmin(request);
        if (denied) return denied;
    }
    if(path.startsWith("/admin/") || path.startsWith("/media/") || path.startsWith("/art/") || path==="/favicon.svg"){
        return proxied(await upstream(request,GAME_API,path,url.search,path.startsWith("/admin/")),url.origin);
    }
    if(path==="/admin") return response(null,{status:302,headers:{Location:"/"}});
    return proxied(await upstream(request,LEGACY_PAGES,path,url.search,false),url.origin);
}

export default {
    async fetch(request) {
        try { return await handle(request); }
        catch {
            return response("CardUniverse Admin Console is temporarily unavailable.",{status:502});
        }
    }
};
