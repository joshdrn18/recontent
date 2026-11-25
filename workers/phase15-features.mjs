const PHASE15_BASE = {COMMON:120,UNCOMMON:180,RARE:260,EPIC:360,LEGENDARY:480,MYTHIC:560};
const PHASE15_MODIFIERS = [
    {key:"FIRE_SURGE",name:"Fire Surge",description:"Fire cards deal +25% base attack damage."},
    {key:"HEALING_FOG",name:"Healing Fog",description:"Recover half the normal HP between rounds."},
    {key:"TRIO_LIMIT",name:"Trio Limit",description:"Use at most 3 cards in your Formation."},
    {key:"BOSS_WARD",name:"Boss Ward",description:"Boss and Legendary Boss floors start with an extra shield."}
];
const PHASE15_TOWER_EPOCH = Date.UTC(2026,10,23);
const PHASE15_WEEK_MS = 604800000;

function phase15Modifier(week) {
    const index=Math.round((Date.parse(week+"T00:00:00Z")-PHASE15_TOWER_EPOCH)/PHASE15_WEEK_MS);
    return PHASE15_MODIFIERS[((index%4)+4)%4];
}

function phase15Combos(cards) {
    const fire=cards.filter(card=>card.element==="FIRE").length;
    const water=cards.some(card=>card.element==="WATER");
    const universes=new Map();
    for(const card of cards) if(card.universe_id) {
        universes.set(card.universe_id,(universes.get(card.universe_id)||0)+1);
    }
    const codes=new Set(cards.map(card=>card.card_code));
    return [
        {key:"BURN",name:"Burning Damage",requirement:"3 FIRE cards",effect:"Extra Burn damage each round.",active:fire>=3},
        {key:"SHIELD",name:"Steam Shield",requirement:"FIRE + WATER",effect:"Absorb 30 damage from the first enemy attack.",active:fire>0&&water},
        {key:"UNIVERSE",name:"Universe Bond",requirement:"2 cards from the same universe",effect:"+8% base damage.",active:[...universes.values()].some(count=>count>=2)},
        {key:"SKILL",name:"Team Skill · Firstlight Steam",requirement:"Cinder Fox + Tidecaller",effect:"Deal 24 extra damage on round 2.",active:codes.has("FL-001")&&codes.has("FL-002")}
    ];
}

function phase15Floor(floor) {
    const tier=floor<=5?0:floor<=10?1:floor<=15?2:3;
    const name=["Normal","Elite","Boss","Legendary Boss"][tier];
    return {floor,tier,name,hp:36+floor*10+tier*28,attack:9+floor*3+tier*4};
}

function phase15TowerBattle(cards,floor,modifier) {
    const target=phase15Floor(floor);
    const combos=phase15Combos(cards);
    const active=new Set(combos.filter(item=>item.active).map(item=>item.key));
    const power=cards.reduce((sum,card)=>sum+card.card_power,0);
    const maxHP=85+Math.floor(power/15);
    let attack=cards.reduce((sum,card)=>sum+Math.max(8,Math.floor(card.card_power/15))
        *(modifier.key==="FIRE_SURGE"&&card.element==="FIRE"?1.25:1),0);
    if(active.has("UNIVERSE")) attack*=1.08;
    attack=Math.floor(attack);
    let enemyHP=target.hp;
    let shield=modifier.key==="BOSS_WARD"&&target.tier>=2?45+floor*2:0;
    let playerHP=maxHP;
    let steam=active.has("SHIELD")?30:0;
    const rounds=[];
    for(let turn=1;turn<=6&&enemyHP>0&&playerHP>0;turn++){
        const burn=active.has("BURN")?Math.max(5,Math.floor(attack*0.12)):0;
        const skill=turn===2&&active.has("SKILL")?24:0;
        const hit=attack+burn+skill;
        const absorbed=Math.min(shield,hit);
        shield-=absorbed;
        enemyHP=Math.max(0,enemyHP-hit+absorbed);
        const blocked=enemyHP>0?Math.min(steam,target.attack):0;
        if(enemyHP>0) steam=0;
        const damage=enemyHP>0?target.attack-blocked:0;
        playerHP=Math.max(0,playerHP-damage);
        const healing=playerHP>0&&enemyHP>0&&turn%2===0
            ? Math.min(maxHP-playerHP,modifier.key==="HEALING_FOG"?5:10):0;
        playerHP+=healing;
        rounds.push({turn,hit,burn,skill,absorbed,blocked,damage,healing,player_hp:playerHP,enemy_hp:enemyHP});
    }
    return {won:enemyHP===0,rounds,combos};
}

function phase15Reward(floor){
    const cosmetic=floor===15?{id:"tower-adept-crest",name:"Tower Adept Crest"}
        :floor===20?{id:"tower-legend-frame",name:"Tower Legend Frame"}:null;
    return {coins:20+floor*8,shards:1+Math.floor(floor/5),cosmetic};
}

const PHASE15_CARD_COLUMNS = `
    c.card_id,c.card_code,c.name,c.element,c.art_path,
    CASE WHEN c.mythic_flag=1 THEN 'MYTHIC' ELSE c.rarity END rarity,
    s.universe_id,un.name AS universe,
    COALESCE(p.level,1) AS level,COALESCE(p.star_rank,0) AS star_rank,
    COALESCE(sk.bonus_power,0) AS skill_power,COALESCE(sk.unlock_star_rank,1) AS skill_unlock
`;
const PHASE15_CARD_JOINS = `
    JOIN card_sets s ON s.set_id=c.set_id JOIN universes un ON un.universe_id=s.universe_id
    LEFT JOIN user_card_progression p ON p.user_id=? AND p.card_id=c.card_id
    LEFT JOIN card_skills sk ON sk.card_id=c.card_id
`;
const PHASE15_OWNED = `
    EXISTS(SELECT 1 FROM card_instances ci WHERE ci.card_id=c.card_id AND ci.owner_user_id=? AND ci.status='AVAILABLE'
        AND NOT EXISTS(SELECT 1 FROM card_consumptions cc WHERE cc.instance_id=ci.instance_id))
`;

function phase15WithPower(rows) {
    return rows.map(row=>({...row,card_power:(PHASE15_BASE[row.rarity]||120)
        +Math.max(0,row.level-1)*16+row.star_rank*70
        +(row.star_rank>=row.skill_unlock?row.skill_power*row.star_rank*10:0)}));
}

async function phase15Formation(db,uid,ids=null) {
    if(ids?.length===0) return [];
    if(ids) {
        const rows=await db.prepare(`
            SELECT ${PHASE15_CARD_COLUMNS} FROM cards c ${PHASE15_CARD_JOINS}
            WHERE c.is_active=1 AND c.card_id IN (${ids.map(()=>"?").join(",")}) AND ${PHASE15_OWNED}
        `).bind(uid,...ids,uid).all();
        const byId=new Map(phase15WithPower(rows.results).map(card=>[card.card_id,card]));
        return ids.map(id=>byId.get(id)).filter(Boolean);
    }
    const rows=await db.prepare(`
        SELECT f.slot_no,${PHASE15_CARD_COLUMNS} FROM user_formation_slots f
        JOIN cards c ON c.card_id=f.card_id ${PHASE15_CARD_JOINS}
        WHERE f.user_id=? AND c.is_active=1 AND ${PHASE15_OWNED} ORDER BY f.slot_no
    `).bind(uid,uid,uid).all();
    return phase15WithPower(rows.results);
}

async function phase15Route(request,env,path,method,deps){
    const {need,inp,out,bad,id,klDay,rankTier}=deps;
    if(!/^\/api\/(wallet\/history|showcase(?:\/favorite)?|team\/combos|tower(?:\/battle)?)$/.test(path))return null;
    const u=await need(request,env);
    const uid=u.user_id,db=env.DB,query=new URL(request.url).searchParams;
    if(path==="/api/wallet/history"&&method==="GET"){
        const currency=query.get("currency")||"COINS";
        const offset=Number(query.get("offset")||0);
        if(!["COINS","GEMS","SHARDS"].includes(currency)||!Number.isInteger(offset)||offset<0||offset>10000)
            return bad("Invalid wallet history filters.");
        const rows=await db.prepare(`
            SELECT currency,amount,reason,created_at FROM wallet_transactions
            WHERE user_id=? AND currency=? ORDER BY created_at DESC,transaction_id DESC LIMIT 31 OFFSET ?
        `).bind(uid,currency,offset).all();
        return out({ok:true,transactions:rows.results.slice(0,30),has_more:rows.results.length>30&&offset<10000});
    }
    if(path==="/api/showcase"&&method==="GET"){
        const handle=query.get("username")?.trim().replace(/^@/,"");
        let player={user_id:uid,display_name:u.display_name,username:u.username};
        if(handle){
            if(!/^[a-zA-Z][a-zA-Z0-9_]{2,19}$/.test(handle))return bad("Invalid collector username.");
            player=await db.prepare(`SELECT user_id,display_name,username FROM users
                WHERE username=? COLLATE NOCASE AND role='collector' AND COALESCE(is_bot,0)=0`).bind(handle).first();
            if(!player)return bad("Collector not found.",404);
        }
        const owner=player.user_id;
        const [favorite,formation,sets,titles,achievements,rank,cosmetics,candidates]=await Promise.all([
            db.prepare(`
                SELECT c.card_id,c.card_code,c.name,c.element,c.art_path,c.rarity FROM phase15_showcase f
                JOIN cards c ON c.card_id=f.favorite_card_id WHERE f.user_id=? AND c.is_active=1 AND ${PHASE15_OWNED}
            `).bind(owner,owner).first(),
            phase15Formation(db,owner),
            db.prepare(`
                SELECT s.name,COUNT(*) AS total,SUM(EXISTS(SELECT 1 FROM card_instances i WHERE i.card_id=c.card_id
                    AND i.owner_user_id=? AND NOT EXISTS(SELECT 1 FROM card_consumptions cc WHERE cc.instance_id=i.instance_id))) collected
                FROM card_sets s JOIN cards c ON c.set_id=s.set_id AND c.is_active=1 WHERE s.is_active=1
                GROUP BY s.set_id ORDER BY s.season_number,s.name
            `).bind(owner).all(),
            db.prepare(`
                SELECT t.name,t.rarity,CASE WHEN p.active_title_id=t.title_id THEN 1 ELSE 0 END equipped
                FROM user_titles ut JOIN game_titles t ON t.title_id=ut.title_id LEFT JOIN user_profiles p ON p.user_id=ut.user_id
                WHERE ut.user_id=? AND t.is_active=1 ORDER BY equipped DESC,t.name LIMIT 50
            `).bind(owner).all(),
            db.prepare(`
                SELECT a.title FROM user_achievements ua JOIN collection_achievements a ON a.achievement_id=ua.achievement_id
                WHERE ua.user_id=? AND ua.unlocked_at IS NOT NULL ORDER BY ua.unlocked_at DESC,a.title LIMIT 50
            `).bind(owner).all(),
            db.prepare(`
                SELECT MAX(best_rating) AS best FROM ranked_ratings
                WHERE user_id=? AND (wins+losses+draws)>0
            `).bind(owner).first(),
            db.prepare(`
                SELECT co.name,co.rarity,co.cosmetic_type FROM user_cosmetics uc JOIN cosmetics co ON co.cosmetic_id=uc.cosmetic_id
                WHERE uc.user_id=? ORDER BY co.name LIMIT 50
            `).bind(owner).all(),
            owner===uid?db.prepare(`
                SELECT c.card_id,c.card_code,c.name,c.element,c.art_path,c.rarity FROM cards c
                WHERE c.is_active=1 AND c.mythic_flag=0 AND c.rarity='LEGENDARY' AND ${PHASE15_OWNED} ORDER BY c.name
            `).bind(owner).all():{results:[]}
        ]);
        return out({ok:true,is_self:owner===uid,display_name:player.display_name,username:player.username,
            favorite,formation,sets:sets.results,titles:titles.results,achievements:achievements.results,
            best_rank:rank?.best===null||rank?.best===undefined?null:{rating:rank.best,tier:rankTier(rank.best)},
            cosmetics:cosmetics.results,candidates:candidates.results});
    }
    if(path==="/api/showcase/favorite"&&method==="POST"){
        const body=await inp(request);
        const favorite=body?.card_id;
        if(favorite===null){
            await db.prepare(`DELETE FROM phase15_showcase WHERE user_id=?`).bind(uid).run();
            return out({ok:true,message:"Favorite Legendary cleared."});
        }
        if(typeof favorite!=="string"||favorite.length===0||favorite.length>100)return bad("Choose a Legendary card.");
        const result=await db.prepare(`
            INSERT INTO phase15_showcase(user_id,favorite_card_id)
            SELECT ?,c.card_id FROM cards c WHERE c.card_id=? AND c.is_active=1 AND c.mythic_flag=0 AND c.rarity='LEGENDARY' AND ${PHASE15_OWNED}
            ON CONFLICT(user_id) DO UPDATE SET favorite_card_id=excluded.favorite_card_id,updated_at=CURRENT_TIMESTAMP
        `).bind(uid,favorite,uid).run();
        if(!result.meta?.changes)return bad("Choose a Legendary card you currently own.",409);
        return out({ok:true,message:"Favorite Legendary saved."});
    }
    if(path==="/api/team/combos"&&(method==="GET"||method==="POST")){
        let ids=null;
        if(method==="POST"){
            const body=await inp(request);
            ids=body?.cards;
            if(!Array.isArray(ids)||ids.length>5||new Set(ids).size!==ids.length
                ||ids.some(value=>typeof value!=="string"||value.length<1||value.length>100))return bad("Choose up to five different cards.");
        }
        const formation=await phase15Formation(db,uid,ids);
        if(ids&&formation.length!==ids.length)return bad("One of the selected cards is no longer available.",409);
        return out({ok:true,formation,combos:phase15Combos(formation)});
    }
    if(path==="/api/tower"&&method==="GET"){
        const calendar=phase14Calendar(klDay());
        const modifier=phase15Modifier(calendar.week);
        const [progress,formation,leaderboard]=await Promise.all([
            db.prepare("SELECT COALESCE(MAX(floor),0) floor FROM phase15_tower_clears WHERE week_start=? AND user_id=?").bind(calendar.week,uid).first(),
            phase15Formation(db,uid),
            db.prepare(`
                WITH best AS (SELECT user_id,MAX(floor) floor FROM phase15_tower_clears WHERE week_start=? GROUP BY user_id)
                SELECT u.display_name,b.floor,c.created_at FROM best b JOIN users u ON u.user_id=b.user_id
                JOIN phase15_tower_clears c ON c.week_start=? AND c.user_id=b.user_id AND c.floor=b.floor
                WHERE u.role='collector' AND COALESCE(u.is_bot,0)=0 ORDER BY b.floor DESC,c.created_at,b.user_id LIMIT 30
            `).bind(calendar.week,calendar.week).all()
        ]);
        const best=progress?.floor||0;
        return out({ok:true,calendar:{week:calendar.week,next_week:calendar.nextWeek},modifier,best,view_only:u.role==="admin",
            formation,combos:phase15Combos(formation),leaderboard:leaderboard.results,
            floors:Array.from({length:20},(_,i)=>({...phase15Floor(i+1),reward:phase15Reward(i+1)}))});
    }
    if(path==="/api/tower/battle"&&method==="POST"){
        if(u.role==="admin"||Number(u.is_bot||0)===1)return bad("This account can view the Tower leaderboard.",403);
        const input=await inp(request);
        const calendar=phase14Calendar(klDay());
        const floor=input?.floor;
        if(!Number.isInteger(floor)||floor<1||floor>20||input.week!==calendar.week)
            return bad("The floor or week changed. Refresh the Tower.",409);
        const [progress,cards]=await Promise.all([
            db.prepare("SELECT COALESCE(MAX(floor),0) floor FROM phase15_tower_clears WHERE week_start=? AND user_id=?").bind(calendar.week,uid).first(),
            phase15Formation(db,uid)
        ]);
        if(floor!==(progress?.floor||0)+1)return bad("Clear the previous floor or refresh your Tower progress.",409);
        if(!cards.length)return bad("Save a Formation before starting the Tower.",409);
        const modifier=phase15Modifier(calendar.week);
        if(modifier.key==="TRIO_LIMIT"&&cards.length>3)return bad("Trio Limit allows at most three cards this week.",409);
        const battle=phase15TowerBattle(cards,floor,modifier);
        if(!battle.won)return out({ok:true,battle,floor});
        const reward=phase15Reward(floor);
        const claim=id();
        const statements=[
            db.prepare(`
              INSERT OR IGNORE INTO phase15_tower_clears(clear_id,week_start,user_id,floor)
              SELECT ?,?,?,? WHERE EXISTS(SELECT 1 FROM wallets WHERE user_id=?) AND ?=1+(
                  SELECT COALESCE(MAX(floor),0) FROM phase15_tower_clears WHERE week_start=? AND user_id=?)
            `).bind(claim,calendar.week,uid,floor,uid,floor,calendar.week,uid),
            db.prepare(`
              UPDATE wallets SET coins=coins+?,shards=shards+?,updated_at=CURRENT_TIMESTAMP
              WHERE user_id=? AND EXISTS(SELECT 1 FROM phase15_tower_clears WHERE clear_id=?)
            `).bind(reward.coins,reward.shards,uid,claim)
        ];
        for(const [currency,amount] of [["COINS",reward.coins],["SHARDS",reward.shards]])statements.push(db.prepare(`
            INSERT INTO wallet_transactions(transaction_id,user_id,currency,amount,reason,reference_id,idempotency_key)
            SELECT ?,?,?,?,'WEEKLY_TOWER',?,? WHERE EXISTS(SELECT 1 FROM phase15_tower_clears WHERE clear_id=?)
        `).bind(id(),uid,currency,amount,claim,"tower:"+claim+":"+currency,claim));
        if(reward.cosmetic){
            const item=reward.cosmetic;
            statements.push(
                db.prepare(`
                    INSERT OR IGNORE INTO cosmetics(cosmetic_id,slug,name,cosmetic_type,rarity)
                    SELECT ?,?,?,'TOWER_FRAME','LEGENDARY' WHERE EXISTS(SELECT 1 FROM phase15_tower_clears WHERE clear_id=?)
                `).bind(item.id,item.id,item.name,claim),
                db.prepare(`
                    INSERT OR IGNORE INTO user_cosmetics(user_id,cosmetic_id)
                    SELECT ?,? WHERE EXISTS(SELECT 1 FROM phase15_tower_clears WHERE clear_id=?)
                `).bind(uid,item.id,claim)
            );
        }
        const results=await db.batch(statements);
        if(!results[0]?.meta?.changes)return bad("Tower progress changed. Refresh before challenging again.",409);
        return out({ok:true,battle,floor,reward:{coins:reward.coins,shards:reward.shards,
            cosmetic_name:reward.cosmetic&&results.at(-1)?.meta?.changes?reward.cosmetic.name:null}});
    }
    return bad("Method not allowed.",405);
}
