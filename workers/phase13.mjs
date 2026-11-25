// Apply to the deployed CardUniverse worker module. Keep the existing bindings and account settings.
export function applyPhase13(source) {
    function replaceOnce(before, after) {
        const index = source.indexOf(before);
        if (index < 0 || source.indexOf(before, index + before.length) >= 0) {
            throw new Error("Unexpected Worker source near " + before.slice(0, 90));
        }
        source = source.slice(0, index) + after + source.slice(index + before.length);
    }

    // Ranked admits human opponents only. Existing bot matches can finish as practice.
    const botStart = source.indexOf("async function createRankedBotMatch(");
    const botEnd = source.indexOf("async function arenaState(", botStart);
    if (botStart < 0 || botEnd < 0) throw new Error("Ranked bot factory was not found");
    source = source.slice(0, botStart) + source.slice(botEnd);
    const queueStart = source.indexOf("const rankedHistory=mode");
    const queueEnd = source.indexOf("if(candidate){", queueStart);
    if (queueStart < 0 || queueEnd < 0 || !source.slice(queueStart, queueEnd).includes("createRankedBotMatch")) {
        throw new Error("Ranked bot matchmaking branch was not found");
    }
    const queueLine = source.lastIndexOf("\n", queueStart);
    const candidateLine = source.lastIndexOf("\n", queueEnd);
    source = source.slice(0, queueLine) + source.slice(candidateLine);

    // Beta-test accounts previously entered the bot branch. Keep them out of the public ladder.
    const modeLine = 'const x=await inp(request),mode=String(x.mode||"CASUAL").toUpperCase()==="RANKED"?"RANKED":"CASUAL";';
    replaceOnce(modeLine, modeLine + '\n  if(mode==="RANKED"&&Number(u.is_beta_tester||0)===1)'
        + 'return bad("Ranked is unavailable on beta accounts. Use Training Grounds to practise.",403);');
    replaceOnce('const betaTester=Number(u.is_beta_tester||0)===1;', '');

    // The opponent is looked up from the stored match. The client supplies no eligibility flags.
    replaceOnce('if(completed&&match.mode==="RANKED"&&match.season_id){',
        'if(completed&&!opponentIsBot&&match.mode==="RANKED"&&match.season_id){');
    const completion = 'if(completed){await trackMission(env,u.user_id,"pvp_match",1).catch(()=>{});'
        + 'await syncUserTitles(env,u.user_id).catch(()=>{});if(!opponentIsBot){await trackMission(env,opponentId,"pvp_match",1).catch(()=>{});'
        + 'await syncUserTitles(env,opponentId).catch(()=>{});}}';
    replaceOnce(completion,
        'if(completed&&!opponentIsBot){await Promise.all([u.user_id,opponentId].map(async uid=>{'
        + 'await trackMission(env,uid,"pvp_match",1).catch(()=>{});await syncUserTitles(env,uid).catch(()=>{});}));}');

    const arenaRoute = ' if(p==="/api/ranked"&&m==="GET"){';
    replaceOnce(arenaRoute, `
 if(p==="/api/encyclopedia"&&m==="GET"){
   const user=await need(request,env);
   const cards=await env.DB.prepare(\`
     SELECT c.card_id,c.card_code,c.name,c.description,c.element,c.art_path,s.name AS set_name,
       CASE WHEN c.mythic_flag=1 THEN 'MYTHIC' ELSE c.rarity END AS rarity,
       sk.skill_name,sk.description AS skill_description,sk.bonus_power AS skill_power,
       sk.unlock_star_rank,
       (SELECT COUNT(*) FROM card_instances ci WHERE ci.owner_user_id=? AND ci.card_id=c.card_id
         AND ci.status='AVAILABLE' AND NOT EXISTS(
           SELECT 1 FROM card_consumptions cc WHERE cc.instance_id=ci.instance_id)) AS owned_count
     FROM cards c JOIN card_sets s ON s.set_id=c.set_id
     LEFT JOIN card_skills sk ON sk.card_id=c.card_id
     WHERE c.is_active=1 ORDER BY c.card_code
   \`).bind(user.user_id).all();
   const base={COMMON:120,UNCOMMON:180,RARE:260,EPIC:360,LEGENDARY:480,MYTHIC:560};
   return out({ok:true,cards:cards.results.map(card=>({
     ...card,base_power:base[String(card.rarity).toUpperCase()]||120
   }))});
 }
 if(p==="/api/battle-history"&&m==="GET"){
   const user=await need(request,env);
   const uid=user.user_id;
   const pageQuery=new URL(request.url).searchParams;
   const mode=String(pageQuery.get("mode")||"ARENA").toUpperCase();
   const offset=Number(pageQuery.get("offset")||0);
   if(!Number.isInteger(offset)||offset<0||offset>10000)return bad("Invalid history page.");
   const queries={
     ARENA: \`
       SELECT m.match_id,m.mode,m.winner_id,m.player_one_id,m.player_two_id,
         m.player_one_score,m.player_two_score,m.completed_at AS played_at,
         p1.display_name AS player_one_name,p2.display_name AS player_two_name,
         p1.is_bot AS player_one_is_bot,p2.is_bot AS player_two_is_bot,
         rs.player_one_delta,rs.player_two_delta
       FROM pvp_matches m JOIN users p1 ON p1.user_id=m.player_one_id
       JOIN users p2 ON p2.user_id=m.player_two_id
       LEFT JOIN ranked_match_settlements rs ON rs.match_id=m.match_id AND rs.status='APPLIED'
       WHERE (m.player_one_id=? OR m.player_two_id=?) AND m.status='COMPLETED'
       ORDER BY m.completed_at DESC,m.match_id DESC LIMIT 21 OFFSET ?
     \`,
     ADVENTURE: \`
       SELECT r.run_id,s.title,s.enemy_name,r.player_power,r.enemy_power,r.result,
         r.reward_xp,r.reward_coins,r.created_at AS played_at
       FROM adventure_runs r JOIN adventure_stages s ON s.stage_id=r.stage_id
       WHERE r.user_id=? ORDER BY r.created_at DESC,r.run_id DESC LIMIT 21 OFFSET ?
     \`,
     DUNGEON: \`
       SELECT r.run_id,d.name AS title,d.difficulty,r.floor_reached,r.result,
         r.created_at AS played_at
       FROM dungeon_runs r JOIN dungeon_definitions d ON d.dungeon_id=r.dungeon_id
       WHERE r.user_id=? AND r.result<>'IN_PROGRESS'
       ORDER BY r.created_at DESC,r.run_id DESC LIMIT 21 OFFSET ?
     \`,
     RAID: \`
       SELECT a.attempt_id,b.name AS title,a.damage,a.reward_claimed_at,
         a.created_at AS played_at
       FROM boss_raid_attempts a JOIN boss_raid_bosses b ON b.boss_id=a.boss_id
       WHERE a.user_id=? ORDER BY a.created_at DESC,a.attempt_id DESC LIMIT 21 OFFSET ?
     \`
   };
   if(!Object.hasOwn(queries,mode))return bad("Unknown battle history mode.");
   const statement=env.DB.prepare(queries[mode]);
   const {results}=await (mode==="ARENA"?statement.bind(uid,uid,offset):statement.bind(uid,offset)).all();
   return out({ok:true,entries:results.slice(0,20),hasMore:results.length>20&&offset<10000});
 }
 const historyRecap=p.match(/^\\/api\\/battle-history\\/arena\\/([a-zA-Z0-9_-]{1,100})$/);
 if(historyRecap&&m==="GET"){
   const user=await need(request,env);
   const matchId=historyRecap[1];
   const match=await env.DB.prepare(\`
     SELECT 1 AS visible FROM pvp_matches
     WHERE match_id=? AND status='COMPLETED' AND (player_one_id=? OR player_two_id=?)
   \`).bind(matchId,user.user_id,user.user_id).first();
   if(!match)return bad("Match recap not found.",404);
   const rounds=await env.DB.prepare(\`
     SELECT turn_no,player_one_move,player_two_move,player_one_damage,player_two_damage,
       player_one_hp,player_two_hp
     FROM pvp_match_rounds WHERE match_id=? ORDER BY turn_no LIMIT 5
   \`).bind(matchId).all();
   return out({ok:true,rounds:rounds.results});
 }
` + arenaRoute);
    return source;
}
