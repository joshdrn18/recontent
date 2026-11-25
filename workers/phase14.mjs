export function applyPhase14(source, features) {
    function replaceOnce(before, after) {
        if (source.split(before).length !== 2) throw new Error("Worker base does not match the Phase 14 patch");
        source = source.replace(before, after);
    }

    const anchor = ' if(p==="/api/cards"&&m==="GET"){';
    if (source.includes('const phase14Response=')) throw new Error("Phase 14 is already applied");
    replaceOnce(anchor, `
 const phase14Response=await phase14Route(request,env,p,m,{need,out,bad,inp,id,klDay,collectorPower});
 if(phase14Response)return phase14Response;
` + anchor);

    // Use the same threshold in the opening rule and in the banner details.
    replaceOnce('forceMythic=v.pack_id==="pack_mythic"&&pityCount>=39',
        'forceMythic=v.pack_id==="pack_mythic"&&pityCount>=PHASE14_MYTHIC_PITY-1');

    // Do not consume a seal using a Mythic pity snapshot that another opening has changed.
    const inventory = 'WHERE user_id=? AND pack_id=? AND quantity>0").bind(u.user_id,u.user_id,v.pack_id),env.DB.prepare("INSERT INTO pack_openings';
    replaceOnce(inventory,
        'WHERE user_id=? AND pack_id=? AND quantity>0 AND (pack_id<>\'pack_mythic\' OR ('
        + 'COALESCE((SELECT pp.packs_since_mythic FROM user_pack_pity pp WHERE pp.user_id=user_pack_inventory.user_id AND pp.pack_id=user_pack_inventory.pack_id),0)=? AND '
        + 'COALESCE((SELECT pp.total_mythics FROM user_pack_pity pp WHERE pp.user_id=user_pack_inventory.user_id AND pp.pack_id=user_pack_inventory.pack_id),0)=?'
        + '))").bind(u.user_id,u.user_id,v.pack_id,pityCount,Number(pityRow?.total_mythics||0)),env.DB.prepare("INSERT INTO pack_openings');
    replaceOnce('if(!saved)return bad("You do not have this pack.",400);await trackMission(env,u.user_id,"open_pack",1);',
        'if(!saved){if(v.pack_id==="pack_mythic")return bad("Your seal inventory or Mythic pity changed. Refresh before opening again.",409);'
        + 'return bad("You do not have this pack.",400);}'
        + 'if(saved.opening_id!==opening)return out({ok:true,openingId:saved.opening_id,cards:await opened(env,rid,u.user_id),replayed:true});'
        + 'await trackMission(env,u.user_id,"open_pack",1);');

    return source + "\n" + features + "\n";
}
