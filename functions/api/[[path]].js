const json = (data, status = 200, headers = {}) => new Response(JSON.stringify(data), {
  status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers }
});
const enc = new TextEncoder();
const b64url = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"");
const hex = (bytes) => [...new Uint8Array(bytes)].map(x=>x.toString(16).padStart(2,"0")).join("");
const digest = async (value) => hex(await crypto.subtle.digest("SHA-256", enc.encode(value)));
const random = (n=32) => { const b = new Uint8Array(n); crypto.getRandomValues(b); return b64url(b); };
const read = async (kv,key,fallback=null) => { const v=await kv.get(key); if(v===null)return fallback; try{return JSON.parse(v)}catch{return fallback} };
const write = (kv,key,value,ttl) => kv.put(key,JSON.stringify(value),ttl?{expirationTtl:ttl}:undefined);
const cookie = (request,name) => (request.headers.get("Cookie")||"").split(";").map(x=>x.trim()).find(x=>x.startsWith(name+"="))?.slice(name.length+1) || "";
const emailNorm = (v) => String(v||"").trim().toLowerCase();
const safeUser = ({passwordHash,...u}) => u;
const corsHeaders = (origin) => ({ "access-control-allow-origin": origin, "access-control-allow-credentials":"true", "vary":"Origin" });
async function logEvent(env,user,event,details={}) {
  const id = Date.now()+"-"+random(6);
  const item = {id,userId:user.id,email:user.email,role:user.role,event,details,createdAt:new Date().toISOString()};
  await env.DATA.put("activity:"+id,JSON.stringify(item),{expirationTtl:60*60*24*180});
}
async function current(request,env) {
  const raw=cookie(request,"rc_session"); if(!raw)return null;
  const sid=await digest(raw), session=await read(env.DATA,"session:"+sid);
  if(!session || session.expiresAt<Date.now())return null;
  const user=await read(env.DATA,"user:"+session.userId);
  return user && user.status!=="suspended" ? user : null;
}
async function passwordHash(password,salt) {
  const material=await crypto.subtle.importKey("raw",enc.encode(password),"PBKDF2",false,["deriveBits"]);
  const bits=await crypto.subtle.deriveBits({name:"PBKDF2",salt:enc.encode(salt),iterations:210000,hash:"SHA-256"},material,256);
  return b64url(bits);
}
async function handle(request,env) {
  if(!env.DATA)return json({error:"Storage binding DATA is not configured."},503);
  const url=new URL(request.url), route=url.pathname.replace(/^\/api\/?/,"").replace(/\/$/,"");
  const origin=request.headers.get("Origin")||"";
  const allowed=origin==="https://recontent.pages.dev" || /^https:\/\/[a-z0-9-]+\.recontent\.pages\.dev$/.test(origin) || /^http:\/\/localhost(:\d+)?$/.test(origin);
  if(origin && !allowed)return json({error:"Origin not allowed."},403);
  const cors=origin?corsHeaders(origin):{};
  if(request.method==="OPTIONS")return new Response(null,{status:204,headers:{...cors,"access-control-allow-methods":"GET,POST,PATCH,OPTIONS","access-control-allow-headers":"Content-Type","access-control-max-age":"86400"}});
  if(route==="health" && request.method==="GET")return json({ok:true,service:"ReContent API",storage:"connected"},200,cors);
  if(request.method!=="POST" && request.method!=="GET" && request.method!=="PATCH")return json({error:"Method not allowed."},405,cors);
  let body={}; if(request.method!=="GET"){try{body=await request.json()}catch{return json({error:"Invalid JSON body."},400,cors)}}
  const ip=request.headers.get("CF-Connecting-IP")||"unknown";
  if(route==="auth/register" && request.method==="POST"){
    const email=emailNorm(body.email),name=String(body.name||"").trim().slice(0,80),password=String(body.password||"");
    if(!/^\S+@\S+\.\S+$/.test(email)||password.length<10||password.length>128||name.length<2)return json({error:"Enter a valid email, name, and password of at least 10 characters."},400,cors);
    const throttleKey="throttle:register:"+ip, count=Number(await env.DATA.get(throttleKey)||0);
    if(count>=8)return json({error:"Too many registration attempts. Try again later."},429,cors);
    await env.DATA.put(throttleKey,String(count+1),{expirationTtl:3600});
    if(await env.DATA.get("email:"+email))return json({error:"An account with this email already exists."},409,cors);
    let role="member";
    if(email===emailNorm(env.ADMIN_EMAIL)){
      const expected=await env.DATA.get("config:admin_setup_hash");
      if(!expected || !body.adminSetupKey || await digest(String(body.adminSetupKey))!==expected)return json({error:"A valid one-time Super Admin activation key is required for this email."},403,cors);
      role="admin";
      await env.DATA.delete("config:admin_setup_hash");
    } else if(body.adminSetupKey) return json({error:"That activation key is not valid for this email."},403,cors);
    const id="usr_"+random(12),salt=random(16),hash=await passwordHash(password,salt),now=new Date().toISOString();
    const user={id,email,name,role,status:"active",createdAt:now,updatedAt:now,passwordHash:hash,passwordSalt:salt};
    await write(env.DATA,"user:"+id,user);
    await env.DATA.put("email:"+email,id);
    const index=await read(env.DATA,"users:index",[]);index.push(id);await write(env.DATA,"users:index",index);
    const token=random(32),sid=await digest(token),expiresAt=Date.now()+30*86400000;
    await write(env.DATA,"session:"+sid,{userId:id,expiresAt},30*86400);
    await logEvent(env,user,"account.registered",{method:"password"});
    return json({user:safeUser(user)},201,{...cors,"set-cookie":"rc_session="+token+"; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000"});
  }
  if(route==="auth/login" && request.method==="POST"){
    const email=emailNorm(body.email),throttleKey="throttle:login:"+ip+":"+email.replace(/[^a-z0-9]/g,"_"),count=Number(await env.DATA.get(throttleKey)||0);
    if(count>=10)return json({error:"Too many login attempts. Try again later."},429,cors);
    await env.DATA.put(throttleKey,String(count+1),{expirationTtl:900});
    const id=await env.DATA.get("email:"+email),user=id?await read(env.DATA,"user:"+id):null;
    if(!user||user.status!=="active"||await passwordHash(String(body.password||""),user.passwordSalt)!==user.passwordHash)return json({error:"Email or password is incorrect."},401,cors);
    const token=random(32),sid=await digest(token),expiresAt=Date.now()+30*86400000;
    await write(env.DATA,"session:"+sid,{userId:user.id,expiresAt},30*86400);
    await logEvent(env,user,"auth.login");
    return json({user:safeUser(user)},200,{...cors,"set-cookie":"rc_session="+token+"; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000"});
  }
  if(route==="auth/logout" && request.method==="POST"){
    const raw=cookie(request,"rc_session");if(raw)await env.DATA.delete("session:"+await digest(raw));
    return json({ok:true},200,{...cors,"set-cookie":"rc_session=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0"});
  }
  const user=await current(request,env);
  if(route==="auth/me" && request.method==="GET")return json({user:user?safeUser(user):null},200,cors);
  if(!user)return json({error:"Please sign in to continue."},401,cors);
  if(route==="profile" && request.method==="PATCH"){
    const next={...user,name:String(body.name||user.name).trim().slice(0,80),updatedAt:new Date().toISOString()};
    await write(env.DATA,"user:"+user.id,next);await logEvent(env,next,"profile.updated");return json({user:safeUser(next)},200,cors);
  }
  if(route==="activity" && request.method==="GET"){
    const listed=await env.DATA.list({prefix:"activity:",limit:1000});
    const events=await Promise.all(listed.keys.slice(-100).reverse().map(k=>read(env.DATA,k.name)));
    return json({events:events.filter(x=>x&&x.userId===user.id)},200,cors);
  }
  if(route==="admin/overview" && request.method==="GET"){
    if(user.role!=="admin")return json({error:"Admin access required."},403,cors);
    const ids=await read(env.DATA,"users:index",[]),users=(await Promise.all(ids.slice(-500).map(id=>read(env.DATA,"user:"+id)))).filter(Boolean);
    const listed=await env.DATA.list({prefix:"activity:",limit:1000});
    const events=(await Promise.all(listed.keys.slice(-150).reverse().map(k=>read(env.DATA,k.name)))).filter(Boolean);
    return json({stats:{users:users.length,members:users.filter(x=>x.role==="member").length,admins:users.filter(x=>x.role==="admin").length,suspended:users.filter(x=>x.status==="suspended").length,events:events.length},users:users.map(safeUser).sort((a,b)=>b.createdAt.localeCompare(a.createdAt)),activity:events.sort((a,b)=>b.createdAt.localeCompare(a.createdAt))},200,cors);
  }
  if(route==="admin/users" && request.method==="PATCH"){
    if(user.role!=="admin")return json({error:"Admin access required."},403,cors);
    const target=await read(env.DATA,"user:"+String(body.userId||""));
    if(!target||target.role==="admin")return json({error:"Member not found or cannot be changed."},404,cors);
    target.status=body.status==="suspended"?"suspended":"active";target.updatedAt=new Date().toISOString();await write(env.DATA,"user:"+target.id,target);await logEvent(env,user,"admin.member_status_changed",{targetUserId:target.id,status:target.status});return json({user:safeUser(target)},200,cors);
  }
  return json({error:"Endpoint not found."},404,cors);
}
export async function onRequest(context) {
  try { return await handle(context.request,context.env); }
  catch (error) { return json({error:"Internal server error.",detail:context.env.ENVIRONMENT==="development"?String(error):undefined},500); }
}