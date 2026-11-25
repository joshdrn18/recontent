export function installLiveControls(source, assets, controls) {
    const assetStart = source.indexOf("\nconst CU_OPERATIONS_ASSETS = ");
    const serverStart = source.indexOf("\nasync function cuOperationsRoute",assetStart);
    const route = "const operations=await cuOperationsRoute(request,env,url);";
    if (assetStart < 0 || serverStart < 0 || source.split(route).length !== 2 || source.includes("const CU_GAME_CONTROLS")) {
        throw new Error("Unexpected Operations baseline");
    }
    source = source.slice(0,assetStart) + "\nconst CU_OPERATIONS_ASSETS = " + JSON.stringify(assets)
        + ";\n" + source.slice(serverStart);
    return source.replace(route, "const liveControl=await cuLiveControls(request,env,url);if(liveControl)return liveControl;\n"+route)
        + "\n" + controls + "\n";
}
