export function updateAdminOperations(source, assets, server) {
    const assetMarker = "\nconst CU_OPERATIONS_ASSETS = ";
    const serverMarker = "\nasync function cuOperationsRoute";
    const assetsStart = source.indexOf(assetMarker);
    const serverStart = source.indexOf(serverMarker, assetsStart);
    if (assetsStart < 0 || serverStart < 0 || source.indexOf(assetMarker, assetsStart + assetMarker.length) >= 0
            || !source.endsWith("\n}\n\n")) throw new Error("Unexpected Admin Console baseline");
    const home = '<script defer src="/admin/operations.js"></script>';
    const original = source.slice(0, assetsStart);
    if (original.split(home).length !== 2) throw new Error("Unexpected Admin home script");
    return original.replace(home, "") + assetMarker + JSON.stringify(assets) + ";\n" + server + "\n";
}
