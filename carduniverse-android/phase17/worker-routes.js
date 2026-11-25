// Phase 17 additive routes. Shared existing session and admin authorization.
const PHASE17_GOALS=[
 ["legendary50","Legendary Collector","Collect 50 Legendary copies"],
 ["adventure_all","Adventure Conqueror","Complete every active Adventure chapter"],
 ["ranked100","Ranked Champion","Win 100 human Ranked matches"],
 ["raid50","Raid Slayer","Claim rewards from 50 distinct defeated Raid bosses"],
 ["sets_all","Master Collector","Complete every active card set"],
 ["mastery10","Master of Cards","Reach Mastery 5 on 10 different cards"]
];
async function phase17Progress(db,uid){
 const statements=[
 db.prepare("SELECT COUNT(*) n FROM card_instances i JOIN cards c ON c.card_id=i.card_id WHERE i.owner_user_id=? AND c.rarity='LEGENDARY' AND c.mythic_flag=0 AND NOT EXISTS(SELECT 1 FROM card_consumptions x WHERE x.instance_id=i.instance_id)").bind(uid),
 db.prepare("SELECT COUNT(*) total,COALESCE(SUM(NOT EXISTS(SELECT 1 FROM adventure_stages s WHERE s.chapter_id=h.chapter_id AND s.is_active=1 AND NOT EXISTS(SELECT 1 FROM user_adventure_progress p WHERE p.user_id=? AND p.stage_id=s.stage_id AND p.clears>0))),0) n FROM adventure_chapters h WHERE h.is_active=1 AND EXISTS(SELECT 1 FROM adventure_stages s WHERE s.chapter_id=h.chapter_id AND s.is_active=1)").bind(uid),
 db.prepare("SELECT COUNT(*) n FROM pvp_matches m JOIN users a ON a.user_id=m.player_one_id JOIN users b ON b.user_id=m.player_two_id WHERE m.winner_id=? AND m.mode='RANKED' AND m.status='COMPLETED' AND a.role='collector' AND b.role='collector' AND a.is_bot=0 AND b.is_bot=0 AND a.is_beta_tester=0 AND b.is_beta_tester=0").bind(uid),
 db.prepare("SELECT COUNT(DISTINCT boss_id) n FROM boss_raid_attempts WHERE user_id=? AND reward_claimed_at IS NOT NULL").bind(uid),
 db.prepare("SELECT COUNT(*) total,COALESCE(SUM(NOT EXISTS(SELECT 1 FROM cards c WHERE c.set_id=s.set_id AND c.is_active=1 AND NOT EXISTS(SELECT 1 FROM card_instances i WHERE i.card_id=c.card_id AND i.owner_user_id=? AND NOT EXISTS(SELECT 1 FROM card_consumptions x WHERE x.instance_id=i.instance_id)))),0) n FROM card_sets s WHERE s.is_active=1 AND EXISTS(SELECT 1 FROM cards c WHERE c.set_id=s.set_id AND c.is_active=1)").bind(uid),
 db.prepare("SELECT COUNT(*) n FROM phase16_mastery WHERE user_id=? AND uses>=120").bind(uid)
 ];
 const rows=(await db.batch(statements)).map(r=>r.results[0]||{});
 const targets=[50,Number(rows[1].total||0),100,50,Number(rows[4].total||0),10];
 const goals=PHASE17_GOALS.map(([key,title,description],i)=>({key,title,description,progress:Number(rows[i].n||0),target:targets[i]}));
 const earned=goals.filter(g=>g.target>0&&g.progress>=g.target);
 if(earned.length) await db.batch(earned.map(g=>db.prepare("INSERT OR IGNORE INTO phase17_achievement_unlocks(user_id,achievement_key) VALUES(?,?)").bind(uid,g.key)));
 const saved=(await db.prepare("SELECT achievement_key,slot,unlocked_at FROM phase17_achievement_unlocks WHERE user_id=?").bind(uid).all()).results;
 return goals.map(g=>{const row=saved.find(r=>r.achievement_key===g.key);return {...g,unlocked:!!row,slot:row?.slot??null,unlocked_at:row?.unlocked_at??null}});
}
async function phase17Route(request,env,path,method,deps){
 const {need,inp,out,bad}=deps,db=env.DB;
 if(path==="/api/showcase/achievements"){
  const u=await need(request,env);
  if(method==="GET"){
   const handle=new URL(request.url).searchParams.get("username")?.trim().replace(/^@/,"");
   if(!handle)return out({ok:true,is_self:true,achievements:await phase17Progress(db,u.user_id)});
   if(!/^[a-zA-Z][a-zA-Z0-9_]{2,19}$/.test(handle))return bad("Invalid collector username.");
   const player=await db.prepare("SELECT user_id FROM users WHERE username=? COLLATE NOCASE AND role='collector' AND is_bot=0").bind(handle).first();
   if(!player)return bad("Collector not found.",404);
   if(player.user_id===u.user_id)return out({ok:true,is_self:true,achievements:await phase17Progress(db,u.user_id)});
   const rows=(await db.prepare("SELECT achievement_key,slot,unlocked_at FROM phase17_achievement_unlocks WHERE user_id=? AND slot IS NOT NULL ORDER BY slot").bind(player.user_id).all()).results;
   return out({ok:true,is_self:false,achievements:rows.map(r=>{const g=PHASE17_GOALS.find(g=>g[0]===r.achievement_key);return {key:g[0],title:g[1],description:g[2],slot:r.slot,unlocked:true,unlocked_at:r.unlocked_at}})});
  }
  if(method==="POST"){
   const body=await inp(request),keys=body?.keys;
   if(!Array.isArray(keys)||keys.length>3||new Set(keys).size!==keys.length||keys.some(k=>typeof k!=="string"||!PHASE17_GOALS.some(g=>g[0]===k)))return bad("Choose up to three distinct achievements.");
   const goals=await phase17Progress(db,u.user_id);
   if(keys.some(k=>!goals.some(g=>g.key===k&&g.unlocked)))return bad("Only unlocked achievements can be displayed.",409);
   await db.batch([db.prepare("UPDATE phase17_achievement_unlocks SET slot=NULL WHERE user_id=? AND slot IS NOT NULL").bind(u.user_id),...keys.map((k,i)=>db.prepare("UPDATE phase17_achievement_unlocks SET slot=? WHERE user_id=? AND achievement_key=?").bind(i+1,u.user_id,k))]);
   return out({ok:true,message:"Achievement showcase saved."});
  }
  return bad("Method not allowed.",405);
 }
 if(path==="/api/admin/balance-monitor"&&method==="GET"){
  await need(request,env,true);
  const period=new URL(request.url).searchParams.get("period")||"7d", periods={"24h":"-1 day","7d":"-7 days","30d":"-30 days"};
  if(!Object.hasOwn(periods,period))return bad("Invalid period.");
  const since=periods[period], normal="u.role='collector' AND u.is_bot=0 AND u.is_beta_tester=0";
  const q=[
   db.prepare("SELECT COUNT(*) players,COALESCE(SUM(w.coins),0) gold,COALESCE(SUM(w.gems),0) gems,COALESCE(SUM(w.shards),0) shards,AVG(w.coins) avg_gold,AVG(w.gems) avg_gems,AVG(w.shards) avg_shards,MAX(w.coins) max_gold,MAX(w.gems) max_gems,MAX(w.shards) max_shards FROM wallets w JOIN users u ON u.user_id=w.user_id WHERE "+normal),
   db.prepare("SELECT currency,reason,COUNT(*) transactions,SUM(CASE WHEN amount>0 THEN amount ELSE 0 END) earned,SUM(CASE WHEN amount<0 THEN -amount ELSE 0 END) spent FROM wallet_transactions t JOIN users u ON u.user_id=t.user_id WHERE "+normal+" AND datetime(t.created_at)>=datetime('now',?) GROUP BY currency,reason ORDER BY earned+spent DESC").bind(since),
   db.prepare("SELECT o.pack_id,c.slot_index,c.rarity_snapshot rarity,COUNT(*) drops FROM pack_opening_cards c JOIN pack_openings o ON o.opening_id=c.opening_id JOIN users u ON u.user_id=o.user_id WHERE "+normal+" AND datetime(o.created_at)>=datetime('now',?) GROUP BY o.pack_id,c.slot_index,c.rarity_snapshot").bind(since),
   db.prepare("SELECT pack_id,slot_index,rarity,weight,100.0*weight/SUM(weight) OVER(PARTITION BY pack_id,slot_index) configured_pct FROM pack_odds ORDER BY pack_id,slot_index,rarity"),
   db.prepare("SELECT c.card_id,c.name,SUM(m.uses) lifetime_uses FROM phase16_mastery m JOIN cards c ON c.card_id=m.card_id JOIN users u ON u.user_id=m.user_id WHERE "+normal+" GROUP BY c.card_id,c.name ORDER BY lifetime_uses DESC LIMIT 50"),
   db.prepare("SELECT c.card_id,c.name,COUNT(*) matches,SUM(CASE WHEN m.winner_id=r.user_id THEN 1 ELSE 0 END) wins,ROUND(100.0*SUM(CASE WHEN m.winner_id=r.user_id THEN 1 ELSE 0 END)/COUNT(*),2) win_pct FROM phase17_ranked_cards r JOIN pvp_matches m ON m.match_id=r.match_id JOIN cards c ON c.card_id=r.card_id WHERE m.status='COMPLETED' AND datetime(m.completed_at)>=datetime('now',?) GROUP BY c.card_id,c.name ORDER BY matches DESC LIMIT 100").bind(since),
   db.prepare("SELECT u.username,t.currency,t.reason,strftime('%Y-%m-%dT%H:00:00',t.created_at) hour,COUNT(*) transactions,SUM(t.amount) net FROM wallet_transactions t JOIN users u ON u.user_id=t.user_id WHERE "+normal+" AND datetime(t.created_at)>=datetime('now',?) GROUP BY t.user_id,t.currency,t.reason,hour HAVING COUNT(*)>=30 ORDER BY transactions DESC LIMIT 50").bind(since)
  ];
  const [balances,ledger,drops,odds,usage,ranked,alerts]=(await db.batch(q)).map(r=>r.results);
  const totals={};for(const d of drops){const k=d.pack_id+':'+d.slot_index;totals[k]=(totals[k]||0)+d.drops;}
  return out({ok:true,period,generated_at:new Date().toISOString(),balances:balances[0],ledger,drop_rates:odds.map(o=>{const n=totals[o.pack_id+':'+o.slot_index]||0;const count=drops.find(d=>d.pack_id===o.pack_id&&d.slot_index===o.slot_index&&d.rarity===o.rarity)?.drops||0;return {...o,samples:n,actual_pct:n?100*count/n:null}}),card_usage_lifetime:usage,ranked_formation_win_rate:ranked.map(r=>({...r,low_sample:r.matches<20})),review_flags:alerts,notes:["Normal collectors only; excludes admins, bots and beta testers.","Configured odds are current base odds, not historical/pity-adjusted odds.","Ranked card statistics start at snapshot deployment; formation association is not causal card strength.","Review flags are heuristics, not proof of abuse."]});
 }
 return null;
}
