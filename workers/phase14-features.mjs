const PHASE14_MYTHIC_PITY = 40;
const PHASE14_RARITIES = ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"];
const PHASE14_BINDER_REWARDS = [
    {threshold:25, label:"100 Gold", coins:100},
    {threshold:50, label:"12 Evolution Shards", shards:12},
    {threshold:75, label:"Archive Crest cosmetic", asset:"COSMETIC"},
    {threshold:100,label:"Exclusive Archivist title", asset:"TITLE"}
];
const PHASE14_BOSS_HP = 2000;
const PHASE14_GUILD_REWARDS = {
    muster:{label:"Guild Muster", target:5, metric:"strikes", coins:120},
    assault:{label:"Coordinated Assault", target:1200, metric:"damage", shards:8},
    boss:{label:"Defeat the Guild Boss", target:PHASE14_BOSS_HP, metric:"damage", coins:160, shards:12}
};
const PHASE14_SEASON = {bosses:2, strikes:4, coins:400, shards:30};
const PHASE14_SEASON_EPOCH = Date.UTC(2026, 10, 23);
const PHASE14_DAY = 86400000;

function phase14Calendar(day) {
    const today = Date.parse(day + "T00:00:00Z");
    const date = ms => new Date(ms).toISOString().slice(0, 10);
    const monday = today - ((new Date(today).getUTCDay() + 6) % 7) * PHASE14_DAY;
    const seasonMs = 28 * PHASE14_DAY;
    const start = PHASE14_SEASON_EPOCH + Math.floor((monday - PHASE14_SEASON_EPOCH) / seasonMs) * seasonMs;
    return {
        day, week:date(monday), nextWeek:date(monday + 7 * PHASE14_DAY),
        season:date(start), nextSeason:date(start + seasonMs),
        previousSeason:start >= PHASE14_SEASON_EPOCH + seasonMs ? date(start - seasonMs) : null
    };
}

async function phase14Route(request, env, path, method, deps) {
    const {need,out,bad,inp,id,klDay,collectorPower} = deps;
    const get = method === "GET", post = method === "POST";
    const match = path.match(/^\/api\/(summon\/(?:transparency|history)|binders\/completion(?:\/claim)?|guilds\/cooperative(?:\/strike|\/claim)?|journey(?:\/tutorial)?)$/);
    if (!match) return null;
    const u = await need(request, env);
    const uid = u.user_id;
    const db = env.DB;
    if (path === "/api/summon/transparency" && get) {
        const slug = new URL(request.url).searchParams.get("pack") || "";
        const pack = await db.prepare(`
            SELECT p.pack_id,p.name,p.description,p.cards_per_pack,p.currency,p.price,p.ownership_type,
              COALESCE(up.packs_since_mythic,0) AS pity
            FROM pack_types p LEFT JOIN user_pack_pity up ON up.pack_id=p.pack_id AND up.user_id=?
            WHERE p.slug=? AND p.is_active=1
        `).bind(uid,slug).first();
        if (!pack) return bad("Summon banner not found.",404);
        const [odds,offers] = await Promise.all([
            db.prepare(`SELECT slot_index,rarity,weight FROM pack_odds WHERE pack_id=?`).bind(pack.pack_id).all(),
            db.prepare(`
                SELECT DISTINCT e.title,e.description,datetime(e.starts_at) AS starts_at,datetime(e.ends_at) AS ends_at
                FROM live_events e JOIN event_shop_items i ON e.event_id=i.event_id
                WHERE i.item_type='PACK' AND i.item_ref=? AND i.is_active=1 AND e.is_active=1
                  AND datetime(e.starts_at)<=CURRENT_TIMESTAMP AND datetime(e.ends_at)>CURRENT_TIMESTAMP
                ORDER BY datetime(e.ends_at) LIMIT 10
            `).bind(pack.pack_id).all()
        ]);
        const threshold = pack.pack_id === "pack_mythic" ? PHASE14_MYTHIC_PITY : null;
        const force = threshold !== null && Number(pack.pity) >= threshold - 1;
        const slots = [];
        const guarantees = [];
        for (let i=1; i<=pack.cards_per_pack; i++) {
            const config=odds.results.filter(row=>Number(row.slot_index)===i);
            const total=config.reduce((sum,row)=>sum+Number(row.weight),0);
            const rates=total>0 || (force&&i===1) ? PHASE14_RARITIES.map(rarity=>{
                const weight=Number(config.find(row=>row.rarity===rarity)?.weight||0);
                return {rarity,percent:force&&i===1 ? (rarity==="MYTHIC"?100:0) : weight*100/total};
            }) : null;
            for (const rate of rates || []) if (rate.percent===100) guarantees.push({slot:i,rarity:rate.rarity});
            slots.push({slot:i,rates});
        }
        return out({ok:true,banner:{...pack,ends_at:null},slots,guarantees,offers:offers.results,
            pity:threshold===null?null:{count:Number(pack.pity),threshold,guaranteed_next:force}});
    }
    if (path === "/api/summon/history" && get) {
        const offset = Number(new URL(request.url).searchParams.get("offset") || 0);
        if (!Number.isInteger(offset) || offset<0 || offset>10000) return bad("Invalid history page.");
        const rows = await db.prepare(`
            WITH page AS (
                SELECT o.opening_id,o.created_at,o.cards_count,p.name AS pack_name
                FROM pack_openings o JOIN pack_types p ON p.pack_id=o.pack_id
                WHERE o.user_id=? ORDER BY o.created_at DESC,o.opening_id DESC LIMIT 21 OFFSET ?
            )
            SELECT page.*,c.card_code,c.name,oc.rarity_snapshot AS rarity,oc.slot_index
            FROM page LEFT JOIN pack_opening_cards oc ON oc.opening_id=page.opening_id
            LEFT JOIN cards c ON c.card_id=oc.card_id
            ORDER BY page.created_at DESC,page.opening_id DESC,oc.slot_index
        `).bind(uid,offset).all();
        const byOpening=new Map();
        for (const row of rows.results) {
            if (!byOpening.has(row.opening_id)) byOpening.set(row.opening_id,
                {pack_name:row.pack_name,created_at:row.created_at,cards_count:row.cards_count,cards:[]});
            if (row.slot_index!==null) byOpening.get(row.opening_id).cards.push(
                {name:row.name,card_code:row.card_code,rarity:row.rarity,slot:row.slot_index});
        }
        return out({ok:true,entries:[...byOpening.values()].slice(0,20),has_more:byOpening.size>20&&offset<10000});
    }

    if (path === "/api/binders/completion" && get) {
        const [sets,claims]=await Promise.all([
            db.prepare(`
                SELECT s.slug,s.name,COUNT(*) AS total,SUM(
                    EXISTS(SELECT 1 FROM card_instances i WHERE i.card_id=c.card_id AND i.owner_user_id=?
                       AND NOT EXISTS(SELECT 1 FROM card_consumptions cc WHERE cc.instance_id=i.instance_id))
                ) AS collected
                FROM card_sets s JOIN cards c ON c.set_id=s.set_id AND c.is_active=1
                WHERE s.is_active=1 GROUP BY s.set_id ORDER BY s.season_number,s.name
            `).bind(uid).all(),
            db.prepare(`SELECT reward_key FROM phase14_reward_claims WHERE user_id=? AND reward_kind='BINDER'`).bind(uid).all()
        ]);
        const owned = new Set(claims.results.map(row=>row.reward_key));
        return out({ok:true,sets:sets.results.map(set=>({...set,milestones:PHASE14_BINDER_REWARDS.map(reward=>({
            threshold:reward.threshold,label:reward.label,claimed:owned.has(set.slug+":"+reward.threshold),
            ready:set.collected*100>=set.total*reward.threshold
        }))}))});
    }
    if (path === "/api/binders/completion/claim" && post) {
        const body=await inp(request);
        const slug=String(body.set||"");
        const reward=PHASE14_BINDER_REWARDS.find(entry=>entry.threshold===Number(body.threshold));
        if (!reward || !/^[a-z0-9_-]{1,100}$/.test(slug)) return bad("Unknown set reward.");
        const set=await db.prepare(`SELECT set_id,name FROM card_sets WHERE slug=? AND is_active=1`).bind(slug).first();
        if (!set) return bad("Set not found.",404);
        const eligibility = db.prepare(`
            INSERT OR IGNORE INTO phase14_reward_claims(claim_id,user_id,reward_kind,reward_key)
            SELECT ?,?,'BINDER',? WHERE EXISTS(SELECT 1 FROM wallets WHERE user_id=?)
              AND EXISTS(SELECT 1 FROM card_sets WHERE set_id=? AND is_active=1)
              AND EXISTS(SELECT 1 FROM cards WHERE set_id=? AND is_active=1)
              AND (? * (SELECT COUNT(*) FROM cards WHERE set_id=? AND is_active=1)) <=
                100 * (SELECT COUNT(*) FROM cards c WHERE c.set_id=? AND c.is_active=1 AND EXISTS(
                    SELECT 1 FROM card_instances i WHERE i.card_id=c.card_id AND i.owner_user_id=?
                    AND NOT EXISTS(SELECT 1 FROM card_consumptions cc WHERE cc.instance_id=i.instance_id)))
        `);
        const claim=id();
        const key=slug+":"+reward.threshold;
        const statements=[eligibility.bind(claim,uid,key,uid,set.set_id,set.set_id,reward.threshold,set.set_id,set.set_id,uid)];
        phase14Wallet(db,statements,claim,uid,reward,"BINDER_COMPLETION",id);
        const assetId="phase14-binder-"+slug;
        if (reward.asset==="COSMETIC") {
            statements.push(
                db.prepare(`INSERT OR IGNORE INTO cosmetics(cosmetic_id,slug,name,cosmetic_type,rarity,is_limited)
                    SELECT ?,?,?,'BINDER_CREST','EPIC',1 WHERE EXISTS(SELECT 1 FROM phase14_reward_claims WHERE claim_id=?)`)
                    .bind(assetId,assetId,set.name+" Archive Crest",claim),
                db.prepare(`INSERT OR IGNORE INTO user_cosmetics(user_id,cosmetic_id)
                    SELECT ?,? WHERE EXISTS(SELECT 1 FROM phase14_reward_claims WHERE claim_id=?)`).bind(uid,assetId,claim)
            );
        }
        if (reward.asset==="TITLE") {
            statements.push(
                db.prepare(`INSERT OR IGNORE INTO game_titles(title_id,slug,name,description,category,rarity,unlock_hint)
                    SELECT ?,?,?,?,'COLLECTION','LEGENDARY',? WHERE EXISTS(SELECT 1 FROM phase14_reward_claims WHERE claim_id=?)`)
                    .bind(assetId,assetId,set.name+" Archivist","Collected every card in "+set.name+".",
                        "Complete the set in Binder Completion Rewards.",claim),
                db.prepare(`INSERT OR IGNORE INTO user_titles(user_id,title_id,source_type,source_ref)
                    SELECT ?,?,'BINDER_COMPLETION',? WHERE EXISTS(SELECT 1 FROM phase14_reward_claims WHERE claim_id=?)`)
                    .bind(uid,assetId,slug,claim)
            );
        }
        const result=await db.batch(statements);
        if (!result[0]?.meta?.changes) return bad("This reward is already claimed or the set milestone is incomplete.",409);
        return out({ok:true,message:reward.label+" claimed."});
    }

    if (path === "/api/journey" && get) {
        const row=await db.prepare(`
          SELECT
            EXISTS(SELECT 1 FROM phase14_journey_tutorial WHERE user_id=?1) AS tutorial,
            (EXISTS(SELECT 1 FROM card_instances WHERE owner_user_id=?1) OR
              EXISTS(SELECT 1 FROM pack_openings WHERE user_id=?1 AND cards_count>0)) AS card,
            (EXISTS(SELECT 1 FROM user_formation_slots WHERE user_id=?1) OR
              EXISTS(SELECT 1 FROM user_card_decks WHERE user_id=?1 AND json_array_length(cards_json)>0)) AS formation,
            EXISTS(SELECT 1 FROM card_evolution_actions WHERE user_id=?1) AS upgrade,
            EXISTS(SELECT 1 FROM adventure_runs WHERE user_id=?1 AND result='VICTORY') AS adventure,
            (EXISTS(SELECT 1 FROM guild_members WHERE user_id=?1) OR
              EXISTS(SELECT 1 FROM guild_activity WHERE user_id=?1 AND event_type IN ('MEMBER_JOINED','GUILD_FOUNDED'))) AS guild,
            EXISTS(SELECT 1 FROM pvp_match_moves WHERE user_id=?1) AS arena
        `).bind(uid).first();
        return out({ok:true,steps:row});
    }
    if (path === "/api/journey/tutorial" && post) {
        await db.prepare(`INSERT OR IGNORE INTO phase14_journey_tutorial(user_id) VALUES(?)`).bind(uid).run();
        return out({ok:true});
    }

    if (path.startsWith("/api/guilds/cooperative")) {
        const guild=await db.prepare(`
            SELECT g.guild_id,g.name,g.guild_level FROM guild_members gm JOIN guilds g ON g.guild_id=gm.guild_id
            WHERE gm.user_id=?
        `).bind(uid).first();
        if (!guild) return bad("Join a guild to access cooperative activities.",403);
        const calendar = phase14Calendar(klDay());
        const gid = guild.guild_id;
        if (path === "/api/guilds/cooperative" && get) {
            return out(await phase14Guild(db,guild,uid,calendar));
        }
        if (path === "/api/guilds/cooperative/strike" && post) {
            if (u.role==="admin") return bad("Admin accounts can view Guild activities.",403);
            const power = await collectorPower(env,uid,u.collector_level);
            const damage = Math.min(400,Math.max(25,Math.floor(40+4*Math.sqrt(Math.max(0,power)))));
            const strike=id();
            const row=await db.prepare(`
                WITH hp AS (
                    SELECT ? - COALESCE(SUM(damage),0) remaining FROM phase14_guild_strikes
                    WHERE guild_id=? AND week_start=?
                )
                INSERT OR IGNORE INTO phase14_guild_strikes(strike_id,guild_id,user_id,strike_day,week_start,damage)
                SELECT ?,?,?,?,?,MIN(hp.remaining,?) FROM hp
                WHERE hp.remaining>0 AND EXISTS(SELECT 1 FROM guild_members WHERE guild_id=? AND user_id=?)
                RETURNING damage
            `).bind(PHASE14_BOSS_HP,gid,calendar.week,strike,gid,uid,calendar.day,calendar.week,damage,gid,uid).first();
            if (!row) return bad("The boss is defeated, your daily strike is used, or your membership changed.",409);
            return out({ok:true,message:"You dealt "+row.damage+" damage.",damage:row.damage});
        }
        if (path === "/api/guilds/cooperative/claim" && post) {
            if (u.role==="admin") return bad("Admin accounts can view Guild activities.",403);
            const body=await inp(request);
            const key=String(body.reward||"");
            const guildReward=Object.hasOwn(PHASE14_GUILD_REWARDS,key) ? PHASE14_GUILD_REWARDS[key] : null;
            if (!guildReward && key!=="season") return bad("Unknown Guild reward.");
            const claim=id();
            let first;
            const kind=key==="season"?"GUILD_SEASON":"GUILD_WEEK";
            if (guildReward) {
                const progress=guildReward.metric==="strikes"?"COUNT(*)":"COALESCE(SUM(damage),0)";
                first=db.prepare(`
                    INSERT OR IGNORE INTO phase14_reward_claims(claim_id,user_id,reward_kind,reward_key)
                    SELECT ?,?,'GUILD_WEEK',? WHERE EXISTS(SELECT 1 FROM guild_members WHERE guild_id=? AND user_id=?)
                      AND EXISTS(SELECT 1 FROM wallets WHERE user_id=?)
                      AND EXISTS(SELECT 1 FROM phase14_guild_strikes WHERE guild_id=? AND week_start=? AND user_id=?)
                      AND (SELECT ${progress} FROM phase14_guild_strikes WHERE guild_id=? AND week_start=?)>=?
                `).bind(claim,uid,calendar.week+":"+key,gid,uid,uid,gid,calendar.week,uid,gid,calendar.week,guildReward.target);
            } else {
                if (!calendar.previousSeason) return bad("The first season is still in progress.",409);
                first=db.prepare(`
                    INSERT OR IGNORE INTO phase14_reward_claims(claim_id,user_id,reward_kind,reward_key)
                    SELECT ?,?,'GUILD_SEASON',? WHERE EXISTS(SELECT 1 FROM guild_members WHERE guild_id=? AND user_id=?)
                      AND EXISTS(SELECT 1 FROM wallets WHERE user_id=?)
                      AND (SELECT COUNT(*) FROM phase14_guild_strikes WHERE guild_id=? AND user_id=? AND week_start>=? AND week_start<?)>=?
                      AND (SELECT COUNT(*) FROM (
                          SELECT week_start FROM phase14_guild_strikes WHERE guild_id=? AND week_start>=? AND week_start<?
                          GROUP BY week_start HAVING SUM(damage)>=?
                      ))>=?
                `).bind(claim,uid,calendar.previousSeason,gid,uid,uid,gid,uid,calendar.previousSeason,calendar.season,
                    PHASE14_SEASON.strikes,gid,calendar.previousSeason,calendar.season,PHASE14_BOSS_HP,PHASE14_SEASON.bosses);
            }
            const statements=[first];
            phase14Wallet(db,statements,claim,uid,guildReward || PHASE14_SEASON,kind,id);
            const result=await db.batch(statements);
            if (!result[0]?.meta?.changes) return bad("This reward is not available or has already been claimed.",409);
            return out({ok:true,message:"Guild reward claimed."});
        }
    }
    return bad("Method not allowed.",405);
}

function phase14Wallet(db,statements,claim,uid,reward,reason,id) {
    const coins=reward.coins||0, shards=reward.shards||0;
    if (!coins && !shards) return;
    statements.push(db.prepare(`
        UPDATE wallets SET coins=coins+?,shards=shards+?,updated_at=CURRENT_TIMESTAMP
        WHERE user_id=? AND EXISTS(SELECT 1 FROM phase14_reward_claims WHERE claim_id=?)
    `).bind(coins,shards,uid,claim));
    for (const [currency,amount] of [["COINS",coins],["SHARDS",shards]]) if (amount) statements.push(db.prepare(`
        INSERT INTO wallet_transactions(transaction_id,user_id,currency,amount,reason,reference_id,idempotency_key)
        SELECT ?,?,?,?,?,?,? WHERE EXISTS(SELECT 1 FROM phase14_reward_claims WHERE claim_id=?)
    `).bind(id(),uid,currency,amount,reason,claim,"phase14:"+claim+":"+currency,claim));
}

async function phase14Guild(db,guild,uid,calendar) {
    const gid=guild.guild_id;
    const [week,used,ranking,claims,seasons,lifetime]=await Promise.all([
        db.prepare(`SELECT COUNT(*) AS strikes,COALESCE(SUM(damage),0) AS damage,
            COALESCE(SUM(CASE WHEN user_id=? THEN 1 ELSE 0 END),0) AS personal
            FROM phase14_guild_strikes WHERE guild_id=? AND week_start=?`).bind(uid,gid,calendar.week).first(),
        db.prepare(`SELECT 1 AS used FROM phase14_guild_strikes WHERE user_id=? AND strike_day=?`).bind(uid,calendar.day).first(),
        db.prepare(`
            WITH strikes AS (SELECT user_id,SUM(damage) AS damage FROM phase14_guild_strikes
                WHERE guild_id=? AND week_start=? GROUP BY user_id)
            SELECT u.display_name,gm.contribution,COALESCE(s.damage,0) AS damage
            FROM guild_members gm JOIN users u ON u.user_id=gm.user_id LEFT JOIN strikes s ON s.user_id=gm.user_id
            WHERE gm.guild_id=? ORDER BY damage DESC,gm.contribution DESC,u.display_name,gm.user_id LIMIT 30
        `).bind(gid,calendar.week,gid).all(),
        db.prepare(`SELECT reward_kind,reward_key FROM phase14_reward_claims WHERE user_id=?
            AND reward_kind IN ('GUILD_WEEK','GUILD_SEASON')`).bind(uid).all(),
        db.prepare(`
            SELECT week_start,SUM(damage) AS damage,SUM(CASE WHEN user_id=? THEN 1 ELSE 0 END) personal
            FROM phase14_guild_strikes WHERE guild_id=? AND week_start>=? AND week_start<?
            GROUP BY week_start
        `).bind(uid,gid,calendar.previousSeason||calendar.season,calendar.nextSeason).all(),
        db.prepare(`
            SELECT COUNT(*) AS weeks,COALESCE(SUM(damage),0) AS damage FROM (
                SELECT SUM(damage) AS damage FROM phase14_guild_strikes WHERE guild_id=? GROUP BY week_start
            ) WHERE damage>=?
        `).bind(gid,PHASE14_BOSS_HP).first()
    ]);
    const has=(kind,key)=>claims.results.some(row=>row.reward_kind===kind&&row.reward_key===key);
    const season=(start,end)=>{
        const rows=seasons.results.filter(row=>row.week_start>=start&&row.week_start<end);
        return {start,end,bosses:rows.filter(row=>row.damage>=PHASE14_BOSS_HP).length,
            personal:rows.reduce((sum,row)=>sum+Number(row.personal),0)};
    };
    const previous=calendar.previousSeason?season(calendar.previousSeason,calendar.season):null;
    if (previous) {
        previous.claimed=has("GUILD_SEASON",calendar.previousSeason);
        previous.ready=previous.bosses>=PHASE14_SEASON.bosses&&previous.personal>=PHASE14_SEASON.strikes;
    }
    return {ok:true,guild,calendar,ranking:ranking.results,boss:{name:"Astral Warden",hp:PHASE14_BOSS_HP,
        damage:week.damage,remaining:Math.max(0,PHASE14_BOSS_HP-week.damage),struck:!!used},
        missions:Object.entries(PHASE14_GUILD_REWARDS).map(([key,reward])=>({key,...reward,
            progress:week[reward.metric],ready:week[reward.metric]>=reward.target&&week.personal>0,
            claimed:has("GUILD_WEEK",calendar.week+":"+key)})),
        season:season(calendar.season,calendar.nextSeason),previous,season_reward:PHASE14_SEASON,
        achievements:[
            {name:"Guild Vanguard",description:"Defeat the weekly boss for the first time.",complete:lifetime.weeks>=1},
            {name:"Warden Hunters",description:"Defeat 5 weekly Guild Bosses.",complete:lifetime.weeks>=5},
            {name:"Guild Stronghold",description:"Reach Guild Level 5.",complete:guild.guild_level>=5}
        ]};
}
