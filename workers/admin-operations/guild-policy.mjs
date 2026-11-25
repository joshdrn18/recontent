export function applyGuildPolicyConsole(source, assets) {
    function replaceOnce(before, after) {
        if (source.split(before).length !== 2) throw new Error("Unexpected Guild policy baseline");
        source = source.replace(before, () => after);
    }
    const assetStart = source.indexOf("\nconst CU_OPERATIONS_ASSETS = ");
    const serverStart = source.indexOf("\nasync function cuOperationsRoute", assetStart);
    if (assetStart < 0 || serverStart < 0) throw new Error("Admin assets not found");
    source = source.slice(0, assetStart) + "\nconst CU_OPERATIONS_ASSETS = " + JSON.stringify(assets)
        + ";\n" + source.slice(serverStart);

    const join = `  try{
   await env.DB.batch([
    env.DB.prepare("INSERT INTO guild_members(guild_id,user_id,role) VALUES(?,?,'MEMBER')").bind(g.guild_id,u.user_id),
    env.DB.prepare("INSERT INTO guild_activity(activity_id,guild_id,user_id,event_type,details_json) VALUES(?,?,?,'MEMBER_JOINED',?)").bind(id(),g.guild_id,u.user_id,JSON.stringify({display_name:u.display_name}))
   ]);
  }catch(e){if(String(e.message).includes("UNIQUE"))return bad("You are already in a guild.",409);throw e}`;
    replaceOnce(join, `  const joined=await env.DB.batch([
   env.DB.prepare("INSERT OR IGNORE INTO guild_members(guild_id,user_id,role) SELECT g.guild_id,u.user_id,'MEMBER' FROM guilds g CROSS JOIN users u WHERE g.guild_id=? AND u.user_id=? AND g.is_recruiting=1 AND u.collector_level>=g.minimum_level AND (SELECT COUNT(*) FROM guild_members WHERE guild_id=g.guild_id)<g.member_limit").bind(g.guild_id,u.user_id),
   env.DB.prepare("INSERT INTO guild_activity(activity_id,guild_id,user_id,event_type,details_json) SELECT ?,?,?,'MEMBER_JOINED',? WHERE changes()=1").bind(id(),g.guild_id,u.user_id,JSON.stringify({display_name:u.display_name}))
  ]);
  if(!joined[0]?.meta?.changes)return bad("Guild eligibility changed. Refresh the directory before trying again.",409);`);

    const capacity = `  await env.DB.batch([
   env.DB.prepare("UPDATE guilds SET member_limit=?,minimum_level=?,is_recruiting=?,motto=?,announcement=?,updated_at=CURRENT_TIMESTAMP WHERE guild_id=?").bind(memberLimit,minimumLevel,recruiting,motto,announcement,guildId),
   env.DB.prepare("INSERT INTO admin_audit_log(audit_id,actor_user_id,target_user_id,action,details_json) VALUES(?,?,?,?,?)").bind(id(),a.user_id,old.owner_user_id,"GUILD_POLICY_UPDATED",JSON.stringify({guild_id:guildId,member_limit:memberLimit,minimum_level:minimumLevel,is_recruiting:recruiting,motto,announcement})),
   env.DB.prepare("INSERT INTO guild_activity(activity_id,guild_id,user_id,event_type,details_json) VALUES(?,?,?,'ADMIN_POLICY_UPDATED',?)").bind(id(),guildId,a.user_id,JSON.stringify({member_limit:memberLimit,minimum_level:minimumLevel,is_recruiting:recruiting}))
  ]);
  return out({ok:true,message:"Guild policy updated."});`;
    replaceOnce(capacity, capacity
        .replace("await env.DB.batch", "const applied=await env.DB.batch")
        .replace('WHERE guild_id=?").bind(memberLimit,minimumLevel,recruiting,motto,announcement,guildId)',
            'WHERE guild_id=? AND (SELECT COUNT(*) FROM guild_members WHERE guild_id=?)<=?").bind(memberLimit,minimumLevel,recruiting,motto,announcement,guildId,guildId,memberLimit)')
        .replace('VALUES(?,?,?,?,?)").bind(id(),a.user_id', 'SELECT ?,?,?,?,? WHERE changes()=1").bind(id(),a.user_id')
        .replace("VALUES(?,?,?,'ADMIN_POLICY_UPDATED',?)", "SELECT ?,?,?,'ADMIN_POLICY_UPDATED',? WHERE changes()=1")
        .replace('  return out(', '  if(!applied[0]?.meta?.changes)return bad("Guild roster changed. Refresh before updating capacity.",409);\n  return out('));
    return source;
}
