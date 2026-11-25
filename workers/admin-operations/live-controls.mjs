const CU_GAME_CONTROLS = [
    {key:"summon",group:"Economy",name:"Summon Hall",description:"Allow buying and opening summon packs.",
        paths:[/^\/api\/packs\/[a-z0-9-]+\/(buy|open)$/]},
    {key:"shop",group:"Economy",name:"Shop and Event Shop",description:"Allow store and event item purchases.",
        paths:[/^\/api\/(shop|events)\/purchase$/]},
    {key:"daily",group:"Economy",name:"Daily and event claims",description:"Allow daily login, challenge, and event login rewards.",
        paths:[/^\/api\/daily\/claim$/, /^\/api\/events\/login\/claim$/, /^\/api\/daily-challenges\/[a-z0-9_-]+\/claim$/]},
    {key:"binder",group:"Economy",name:"Binder rewards",description:"Allow new Binder Completion reward claims.",
        paths:[/^\/api\/binders\/completion\/claim$/]},
    {key:"progression",group:"Economy",name:"Progression rewards",description:"Allow level, mission and achievement reward claims.",
        paths:[/^\/api\/progression\/claim$/, /^\/api\/(missions|achievements)\/[^/]+\/claim$/]},
    {key:"duplicates",group:"Economy",name:"Duplicate conversion",description:"Allow converting duplicate cards into materials.",
        paths:[/^\/api\/duplicates\/convert$/]},
    {key:"evolution",group:"Cards and combat",name:"Card Evolution",description:"Allow Level Up and Ascend.",
        paths:[/^\/api\/evolution\/action$/]},
    {key:"adventure",group:"Cards and combat",name:"Adventure",description:"Allow new Adventure battles.",
        paths:[/^\/api\/adventure\/[a-z0-9-]+\/battle$/]},
    {key:"dungeons",group:"Cards and combat",name:"Dungeons",description:"Allow new expeditions. Current runs can continue.",
        paths:[/^\/api\/dungeons\/[a-z0-9-]+\/start$/]},
    {key:"raids",group:"Cards and combat",name:"World Raid",description:"Allow new World Raid strikes.",
        paths:[/^\/api\/raids\/[a-z0-9-]+\/attack$/]},
    {key:"arena",group:"Cards and combat",name:"Arena matchmaking",description:"Allow new Ranked and Casual queues. Current matches can finish.",
        paths:[/^\/api\/arena\/queue$/]},
    {key:"guilds",group:"Community and progression",name:"Guild recruitment",description:"Allow creating and joining guilds.",
        paths:[/^\/api\/guilds$/, /^\/api\/guilds\/[a-z0-9]{2,5}\/join$/i]},
    {key:"guild_coop",group:"Community and progression",name:"Guild cooperative actions",description:"Allow Guild Boss strikes and cooperative reward claims.",
        paths:[/^\/api\/guilds\/cooperative\/(strike|claim)$/]},
    {key:"guild_treasury",group:"Community and progression",name:"Guild treasury",description:"Allow new Gold contributions.",
        paths:[/^\/api\/guilds\/contribute$/]},
    {key:"chat",group:"Community and progression",name:"Game chat",description:"Allow posting new chat messages.",
        paths:[/^\/api\/chat$/, /^\/api\/guilds\/chat$/]},
    {key:"trades",group:"Community and progression",name:"Trades",description:"Allow new offers and acceptance. Players can cancel or decline existing offers.",
        paths:[/^\/api\/trades$/, /^\/api\/trades\/[^/]+\/accept$/, /^\/api\/trade-board$/, /^\/api\/trade-board\/[^/]+\/request$/]},
    {key:"journey",group:"Community and progression",name:"New Player Journey",description:"Allow finishing the tutorial. Other milestones keep tracking.",
        paths:[/^\/api\/journey\/tutorial$/]}
];

async function cuLiveControls(request, env, url) {
    const endpoint = "/api/admin/operations/settings";
    const setting = request.method === "POST"
        ? CU_GAME_CONTROLS.find(control => control.paths.some(path => path.test(url.pathname))) : null;
    if (!setting && url.pathname !== endpoint) return null;

    // Reuse the server's normal player and Master Admin authorization.
    try {
        if (setting) {
            const state = await cuReadControls(env.DB);
            if (state.config[setting.key] !== false) return null;
            await need(request, env);
            return bad(setting.name + " is temporarily paused by the game administrator.", 503);
        }
        if (!["GET","PATCH"].includes(request.method)) return bad("Method not allowed.",405);
        const admin = await need(request, env, true);
        const current = await cuReadControls(env.DB);
        if (request.method === "GET") {
            return out({ok:true,revision:current.revision,updated_at:current.updated_at,
                controls:CU_GAME_CONTROLS.map(({key,group,name,description}) => ({
                    key,group,name,description,enabled:current.config[key] !== false
                }))});
        }
        const body = await inp(request);
        if (!body || !Number.isSafeInteger(body.revision) || !body.controls
                || typeof body.controls !== "object" || Array.isArray(body.controls)) {
            return bad("Submit a valid settings revision and controls.");
        }
        if (body.revision !== current.revision) return bad("Settings were changed in another session. Reload and retry.",409);
        const requested = Object.entries(body.controls);
        if (!requested.length || requested.some(([key,value]) => !CU_GAME_CONTROLS.some(c=>c.key===key) || typeof value !== "boolean")) {
            return bad("Unknown setting or invalid switch state.");
        }
        const changes = requested.filter(([key,value]) => (current.config[key] !== false) !== value);
        if (!changes.length) return out({ok:true,revision:current.revision,message:"Settings are up to date."});
        const next = {...current.config,...Object.fromEntries(changes)};
        const saved = await env.DB.batch([
            env.DB.prepare(`UPDATE game_live_operations SET config_json=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP
                WHERE config_id=1 AND revision=?`).bind(JSON.stringify(next),current.revision),
            env.DB.prepare(`INSERT INTO admin_audit_log(audit_id,actor_user_id,action,details_json)
                SELECT ?,?,'GAME_LIVE_CONTROLS_UPDATED',? WHERE changes()=1`)
                .bind(id(),admin.user_id,JSON.stringify({revision:current.revision+1,changes:Object.fromEntries(changes)}))
        ]);
        if (!saved[0]?.meta?.changes) return bad("Settings were changed in another session. Reload and retry.",409);
        return out({ok:true,revision:current.revision+1,message:"Game settings saved."});
    } catch (error) {
        const status=error.status||500;
        return bad(status === 401 ? "Please sign in first."
            : status === 403 ? "Access is restricted."
            : status === 400 ? "Invalid settings request."
            : "Game settings are temporarily unavailable.",status);
    }
}

async function cuReadControls(db) {
    const state = await db.prepare("SELECT revision,config_json,updated_at FROM game_live_operations WHERE config_id=1").first();
    if (!state) throw new Error("Live Operations configuration is missing");
    return {revision:state.revision,updated_at:state.updated_at,config:JSON.parse(state.config_json)};
}
