async function cuOperationsRoute(request, env, url) {
    const path = url.pathname;
    const asset = path === "/admin/operations.css" ? "css" : path === "/admin/operations.js" ? "js" : null;
    const page = path === "/admin/operations";
    const summary = path === "/api/admin/operations/phase14";
    if (!asset && !page && !summary) return null;

    const method = request.method;
    const head = method === "HEAD";
    if (method !== "GET" && !(head && !summary)) return bad("Method not allowed.", 405);

    const responseHeaders = {
        "cache-control": "no-store",
        "x-content-type-options": "nosniff"
    };
    if (asset) {
        return new Response(head ? null : CU_OPERATIONS_ASSETS[asset], {
            headers: {...responseHeaders,"content-type":asset === "js"
                ? "text/javascript; charset=utf-8" : "text/css; charset=utf-8"}
        });
    }

    // The page and its data use the existing Master Admin Level 100 check.
    try {
        await need(request, env, true);
    } catch (error) {
        if (summary) return bad(error.status === 401 ? "Please sign in first."
            : error.status === 403 ? "Master Admin access is required." : "Could not verify the session.", error.status || 500);
        if (error.status === 401) return new Response(null, {status:302,headers:{...responseHeaders,location:"/"}});
        return new Response(error.status === 403 ? "Master Admin access is required." : "Could not verify the session.",
            {status:error.status || 500,headers:responseHeaders});
    }

    if (page) {
        return new Response(head ? null : CU_OPERATIONS_ASSETS.html, {headers:{
            ...responseHeaders,"content-type":"text/html; charset=utf-8","x-robots-tag":"noindex, nofollow",
            "content-security-policy":"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'"
        }});
    }

    try {
        return out(await cuOperationsSummary(env.DB, phase14Calendar(klDay())));
    } catch {
        return bad("Phase 14 data could not be loaded.",500);
    }
}

async function cuOperationsSummary(db, calendar) {
    const [odds,offers,binders,guilds,claims,journey,weekly,activity]=await Promise.all([
        db.prepare(`
            WITH openings AS (
                SELECT pack_id,COUNT(*) AS opened FROM pack_openings
                WHERE created_at>=datetime('now','-7 days') GROUP BY pack_id
            )
            SELECT p.slug,p.name,p.cards_per_pack,p.is_active,COALESCE(x.opened,0) AS opened,
                o.slot_index,o.rarity,o.weight
            FROM pack_types p LEFT JOIN pack_odds o ON o.pack_id=p.pack_id
            LEFT JOIN openings x ON x.pack_id=p.pack_id
            ORDER BY p.sort_order,p.slug,o.slot_index,o.rarity
        `).all(),
        db.prepare(`
            SELECT DISTINCT e.title,p.name,datetime(e.starts_at) AS starts_at,datetime(e.ends_at) AS ends_at
            FROM live_events e JOIN event_shop_items i ON i.event_id=e.event_id AND i.item_type='PACK'
            JOIN pack_types p ON p.pack_id=i.item_ref
            WHERE e.is_active=1 AND i.is_active=1 AND datetime(e.ends_at)>CURRENT_TIMESTAMP
            ORDER BY ends_at,e.title,p.name LIMIT 40
        `).all(),
        db.prepare(`
            WITH claims AS (
                SELECT substr(reward_key,1,instr(reward_key,':')-1) AS slug,
                    CAST(substr(reward_key,instr(reward_key,':')+1) AS INTEGER) AS threshold,COUNT(*) AS total
                FROM phase14_reward_claims WHERE reward_kind='BINDER' GROUP BY 1,2
            )
            SELECT s.slug,s.name,c.threshold,COALESCE(c.total,0) AS claimed
            FROM card_sets s LEFT JOIN claims c ON c.slug=s.slug
            WHERE s.is_active=1 ORDER BY s.season_number,s.name,c.threshold
        `).all(),
        db.prepare(`
            SELECT g.name,g.tag,COUNT(*) AS strikes,SUM(s.damage) AS damage,COUNT(DISTINCT s.user_id) AS players
            FROM phase14_guild_strikes s JOIN guilds g ON g.guild_id=s.guild_id
            WHERE s.week_start=? GROUP BY g.guild_id
            ORDER BY damage DESC,strikes DESC,g.tag LIMIT 30
        `).bind(calendar.week).all(),
        db.prepare(`
            SELECT reward_kind,COUNT(*) AS claimed FROM phase14_reward_claims
            WHERE reward_kind IN ('GUILD_WEEK','GUILD_SEASON') GROUP BY reward_kind
        `).all(),
        db.prepare(`
            WITH cohort AS (
                SELECT user_id FROM users WHERE role='collector' AND COALESCE(is_bot,0)=0
                  AND created_at>=datetime('now','-30 days')
            )
            SELECT COUNT(*) AS players,
                SUM(EXISTS(SELECT 1 FROM phase14_journey_tutorial t WHERE t.user_id=u.user_id)) AS tutorial,
                SUM(EXISTS(SELECT 1 FROM card_instances i WHERE i.owner_user_id=u.user_id) OR
                    EXISTS(SELECT 1 FROM pack_openings o WHERE o.user_id=u.user_id AND o.cards_count>0)) AS card,
                SUM(EXISTS(SELECT 1 FROM user_formation_slots s WHERE s.user_id=u.user_id) OR
                    EXISTS(SELECT 1 FROM user_card_decks d WHERE d.user_id=u.user_id AND json_array_length(d.cards_json)>0)) AS formation,
                SUM(EXISTS(SELECT 1 FROM card_evolution_actions a WHERE a.user_id=u.user_id)) AS upgrade,
                SUM(EXISTS(SELECT 1 FROM adventure_runs a WHERE a.user_id=u.user_id AND a.result='VICTORY')) AS adventure,
                SUM(EXISTS(SELECT 1 FROM guild_members g WHERE g.user_id=u.user_id) OR
                    EXISTS(SELECT 1 FROM guild_activity g WHERE g.user_id=u.user_id AND g.event_type IN ('MEMBER_JOINED','GUILD_FOUNDED'))) AS guild,
                SUM(EXISTS(SELECT 1 FROM pvp_match_moves p WHERE p.user_id=u.user_id)) AS arena
            FROM cohort u
        `).first(),
        db.prepare(`
            WITH weekly_guilds AS (
                SELECT week_start,guild_id,COUNT(*) strikes,SUM(damage) damage
                FROM phase14_guild_strikes GROUP BY week_start,guild_id
            )
            SELECT week_start,COUNT(*) AS guilds,SUM(strikes) AS strikes,
                SUM(CASE WHEN damage>=? THEN 1 ELSE 0 END) AS cleared
            FROM weekly_guilds GROUP BY week_start ORDER BY week_start DESC LIMIT 8
        `).bind(PHASE14_BOSS_HP).all(),
        db.prepare(`
            SELECT date(created_at) AS day,reward_kind,COUNT(*) AS claims
            FROM phase14_reward_claims WHERE created_at>=datetime('now','-13 days','start of day')
            GROUP BY 1,2 ORDER BY day DESC,reward_kind
        `).all()
    ]);

    const packs=new Map();
    for (const row of odds.results) {
        if (!packs.has(row.slug)) packs.set(row.slug,{slug:row.slug,name:row.name,active:row.is_active===1,
            cards_per_pack:row.cards_per_pack,opened:row.opened,slots:[]});
        if (row.slot_index===null) continue;
        const slots=packs.get(row.slug).slots;
        let slot=slots.find(value=>value.number===row.slot_index);
        if (!slot) {
            slot={number:row.slot_index,rates:[]};
            slots.push(slot);
        }
        slot.rates.push({rarity:row.rarity,weight:row.weight});
    }
    for (const pack of packs.values()) for (const slot of pack.slots) {
        const total=slot.rates.reduce((sum,item)=>sum+item.weight,0);
        for (const rate of slot.rates) rate.percent=total>0 ? rate.weight*100/total : 0;
    }
    const sets=new Map();
    for (const row of binders.results) {
        if (!sets.has(row.slug)) sets.set(row.slug,{name:row.name,claims:{"25":0,"50":0,"75":0,"100":0}});
        if (row.threshold!==null) sets.get(row.slug).claims[row.threshold]=row.claimed;
    }
    return {ok:true,updated_at:new Date().toISOString(),calendar,mythic_pity:PHASE14_MYTHIC_PITY,
        boss_hp:PHASE14_BOSS_HP,packs:[...packs.values()],offers:offers.results,
        sets:[...sets.values()],guilds:guilds.results,claims:claims.results,journey,
        weekly:weekly.results,activity:activity.results};
}
