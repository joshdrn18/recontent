export function applyPhase15(source, features) {
    const anchor = ' if(p==="/api/cards"&&m==="GET"){';
    if (source.split(anchor).length!==2 || source.includes("const phase15Response=")) throw Error("Phase 15 Worker baseline mismatch");
    const daily = 'return out({ok:true,message:"Daily rewards claimed: 1 Starter Pack + 20 Gold."})';
    if (source.split(daily).length!==2) throw new Error("Daily reward response baseline mismatch");
    source = source.replace(daily, 'return out({ok:true,message:"Daily rewards claimed: 1 Starter Pack + 20 Gold.",reward:{coins:20,pack_quantity:1,pack_id:"pack_starter",pack_name:"Starter Pack"}})');
    return source.replace(anchor, `
 const phase15Response=await phase15Route(request,env,p,m,{need,inp,out,bad,id,klDay,rankTier});
 if(phase15Response)return phase15Response;
` + anchor) + "\n" + features + "\n";
}
