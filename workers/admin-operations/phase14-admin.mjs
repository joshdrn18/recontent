export function addAdminOperations(source, assets, server) {
    function replaceOnce(before, after) {
        if (source.split(before).length !== 2) throw new Error("Admin console patch baseline does not match");
        source = source.replace(before, after);
    }
    const route = 'async function cardUniverseFetch(request,env){const url=new URL(request.url);';
    const shell = "CU_PACK_REVEAL_CSS+'</style></head>');";
    replaceOnce(route, route + `
        const operations=await cuOperationsRoute(request,env,url);
        if(operations)return operations;
    `);
    replaceOnce(shell, `CU_PACK_REVEAL_CSS+'</style><link rel="stylesheet" href="/admin/operations.css"><script defer src="/admin/operations.js"></script></head>');`);
    return source + "\nconst CU_OPERATIONS_ASSETS = " + JSON.stringify(assets) + ";\n" + server + "\n";
}
