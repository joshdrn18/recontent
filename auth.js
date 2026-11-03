/* ReContent account layer: secure cookie sessions, member activity and admin console. */
(() => {
  const $ = (s,root=document)=>root.querySelector(s);
  const api = async (path,options={}) => {
    const res=await fetch("/api/"+path,{credentials:"same-origin",headers:{"content-type":"application/json",...(options.headers||{})},...options});
    const data=await res.json().catch(()=>({error:"Unexpected server response."}));
    if(!res.ok)throw new Error(data.error||"Request failed.");
    return data;
  };
  let me=null,authMode="login";
  const style=document.createElement("style");
  style.textContent=`
    .account-action{border:1px solid var(--line);background:#f8f6f0;border-radius:6px;padding:9px 12px;font-size:10px;font-weight:700;color:var(--ink)}
    .account-action.admin{background:var(--ink);color:white;border-color:var(--ink)}
    .account-modal,.admin-modal{position:fixed;inset:0;z-index:100;background:#1718149c;display:none;align-items:center;justify-content:center;padding:18px}
    .account-modal.open,.admin-modal.open{display:flex}
    .account-box,.admin-box{width:min(100%,440px);max-height:90vh;overflow:auto;background:var(--paper);border:1px solid var(--line);border-radius:12px;padding:24px;box-shadow:0 25px 80px #0003}
    .account-head,.admin-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:18px}
    .account-head h2,.admin-head h2{font:600 27px var(--display);letter-spacing:-1px;margin:5px 0}
    .account-head p{font-size:11px;color:#777;margin:0;line-height:1.6}
    .account-close{border:0;background:none;font-size:22px;color:#777}
    .account-tabs{display:flex;gap:6px;background:#e8e4da;padding:4px;border-radius:7px;margin:18px 0}
    .account-tabs button{flex:1;border:0;border-radius:5px;background:transparent;padding:9px;font-size:10px;color:#777}
    .account-tabs button.active{background:#fffdf8;color:var(--ink);font-weight:700}
    .account-form{display:grid;gap:12px}
    .account-form label{display:grid;gap:6px;font:9px var(--mono);letter-spacing:.6px;color:#777}
    .account-form input{width:100%;border:1px solid var(--line);background:#fffdf8;border-radius:6px;padding:12px;font:12px var(--sans);letter-spacing:0;color:var(--ink)}
    .account-submit{background:var(--orange);color:white;border:0;border-radius:6px;padding:13px;font-weight:700;font-size:11px;margin-top:5px}
    .account-hint{font-size:9px;color:#8b887e;line-height:1.6}
    .account-error{display:none;background:#f8e3dc;color:#9c3c28;border-radius:5px;padding:9px;font-size:10px}
    .account-error.show{display:block}
    .account-user{display:flex;align-items:center;gap:8px;min-width:0}
    .account-user .avatar{flex-shrink:0}
    .account-user .user-meta{min-width:0}
    .account-user .user-meta b{max-width:130px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
    .admin-box{width:min(100%,900px);padding:22px}
    .admin-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:9px;margin:18px 0}
    .admin-stat{background:#e8e4da;border-radius:7px;padding:13px}
    .admin-stat span{display:block;font:8px var(--mono);color:#858279;letter-spacing:.6px}
    .admin-stat b{display:block;font:600 25px var(--display);margin-top:6px}
    .admin-columns{display:grid;grid-template-columns:1fr 1fr;gap:14px}
    .admin-section{border:1px solid var(--line);border-radius:8px;padding:12px;min-width:0}
    .admin-section h3{font:600 14px var(--display);margin:0 0 10px}
    .admin-list{display:grid;gap:7px;max-height:330px;overflow:auto}
    .admin-row{display:flex;align-items:center;gap:8px;padding:8px;background:#f8f6f0;border-radius:5px;min-width:0}
    .admin-row-main{min-width:0;flex:1}
    .admin-row-main b,.admin-row-main small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
    .admin-row-main b{font-size:10px}.admin-row-main small{font-size:8px;color:#858279;margin-top:3px}
    .admin-row button{border:1px solid var(--line);background:white;border-radius:4px;padding:5px 7px;font-size:8px}
    .admin-empty{font-size:10px;color:#858279;padding:14px 4px}
    .admin-foot{display:flex;justify-content:flex-end;margin-top:13px}
    @media(max-width:600px){.account-box{padding:18px}.admin-stats{grid-template-columns:1fr 1fr}.admin-columns{grid-template-columns:1fr}.admin-box{padding:15px}.top-actions{gap:6px}.account-action{padding:8px}}
  `;
  document.head.append(style);
  const modal=document.createElement("div");modal.className="account-modal";modal.innerHTML=`
    <div class="account-box" role="dialog" aria-modal="true" aria-labelledby="account-title">
      <div class="account-head"><div><div class="eyebrow">YOUR RECONTENT ACCOUNT</div><h2 id="account-title">Welcome back.</h2><p id="account-subtitle">Sign in to keep your work connected.</p></div><button class="account-close" aria-label="Close">×</button></div>
      <div class="account-tabs"><button data-mode="login" class="active">Sign in</button><button data-mode="register">Create account</button></div>
      <div class="account-error" id="account-error"></div>
      <form class="account-form" id="account-form">
        <label id="account-name-wrap" hidden>YOUR NAME<input id="account-name" autocomplete="name" minlength="2" maxlength="80"></label>
        <label>EMAIL ADDRESS<input id="account-email" type="email" autocomplete="email" required></label>
        <label>PASSWORD<input id="account-password" type="password" autocomplete="current-password" minlength="10" required></label>
        <label id="account-key-wrap" hidden>ONE-TIME SUPER ADMIN ACTIVATION KEY<input id="account-key" type="password" autocomplete="off"></label>
        <p class="account-hint" id="account-hint">Your session is protected with an HttpOnly cookie. Passwords are never stored in your browser.</p>
        <button class="account-submit" id="account-submit">Sign in ↗</button>
      </form>
    </div>`;
  document.body.append(modal);
  const admin=document.createElement("div");admin.className="admin-modal";admin.innerHTML=`
    <div class="admin-box" role="dialog" aria-modal="true">
      <div class="admin-head"><div><div class="eyebrow">RECONTENT CONTROL ROOM</div><h2>Admin overview<span class="orange-dot">.</span></h2><p class="account-hint">Member accounts and activity across the workspace.</p></div><button class="account-close" aria-label="Close">×</button></div>
      <div class="admin-stats" id="admin-stats"></div>
      <div class="admin-columns"><section class="admin-section"><h3>Members</h3><div class="admin-list" id="admin-users"></div></section><section class="admin-section"><h3>Recent activity</h3><div class="admin-list" id="admin-activity"></div></section></div>
      <div class="admin-foot"><button class="outline-btn" id="admin-refresh">Refresh data ↻</button></div>
    </div>`;
  document.body.append(admin);
  const profile=document.createElement("div");profile.className="account-modal";profile.innerHTML=`
    <div class="account-box" role="dialog" aria-modal="true" aria-labelledby="profile-title">
      <div class="account-head"><div><div class="eyebrow">YOUR ACCOUNT</div><h2 id="profile-title">Profile settings.</h2><p id="profile-meta"></p></div><button class="account-close" aria-label="Close">×</button></div>
      <div class="account-error" id="profile-error"></div>
      <form class="account-form" id="profile-form"><label>DISPLAY NAME<input id="profile-name" minlength="2" maxlength="80" required></label><button class="account-submit">Save profile</button></form>
      <div style="height:1px;background:var(--line);margin:20px 0"></div>
      <form class="account-form" id="password-form"><label>CURRENT PASSWORD<input id="profile-current-password" type="password" autocomplete="current-password" required></label><label>NEW PASSWORD<input id="profile-new-password" type="password" minlength="10" maxlength="128" autocomplete="new-password" required></label><label>CONFIRM NEW PASSWORD<input id="profile-confirm-password" type="password" minlength="10" maxlength="128" autocomplete="new-password" required></label><button class="account-submit">Update password</button></form>
      <p class="account-hint">Use at least 10 characters. Your password is stored as a salted hash, never as plain text.</p>
    </div>`;
  document.body.append(profile);
  const openAuth=(mode="login")=>{setMode(mode);modal.classList.add("open");$("#account-email").focus()};
  const close=()=>{modal.classList.remove("open");admin.classList.remove("open");profile.classList.remove("open")};
  function openProfile(){if(!me)return openAuth("login");$("#profile-name").value=me.name||"";$("#profile-meta").textContent=me.email+" · "+(me.role==="admin"?"Super Admin":"Member");$("#profile-error").classList.remove("show");$("#profile-current-password").value="";$("#profile-new-password").value="";$("#profile-confirm-password").value="";profile.classList.add("open")}
  profile.querySelector(".account-close").addEventListener("click",close);
  profile.addEventListener("click",e=>{if(e.target===profile)close()});
  $("#profile-form").addEventListener("submit",async e=>{e.preventDefault();const button=e.submitter,error=$("#profile-error");button.disabled=true;error.classList.remove("show");try{const result=await api("profile",{method:"PATCH",body:JSON.stringify({name:$("#profile-name").value})});me=result.user;paintAccount();$("#profile-meta").textContent=me.email+" · "+(me.role==="admin"?"Super Admin":"Member");notify("Profile updated.")}catch(err){error.textContent=err.message;error.classList.add("show")}finally{button.disabled=false}});
  $("#password-form").addEventListener("submit",async e=>{e.preventDefault();const button=e.submitter,error=$("#profile-error"),next=$("#profile-new-password").value;if(next!==$("#profile-confirm-password").value){error.textContent="New passwords do not match.";error.classList.add("show");return}button.disabled=true;error.classList.remove("show");try{await api("password",{method:"PATCH",body:JSON.stringify({currentPassword:$("#profile-current-password").value,newPassword:next})});$("#profile-current-password").value="";$("#profile-new-password").value="";$("#profile-confirm-password").value="";notify("Password updated.")}catch(err){error.textContent=err.message;error.classList.add("show")}finally{button.disabled=false}});
  function setMode(mode){authMode=mode;$("#account-title").textContent=mode==="login"?"Welcome back.":"Create your account.";$("#account-subtitle").textContent=mode==="login"?"Sign in to keep your work connected.":"Your ideas, drafts, and projects in one place.";$("#account-name-wrap").hidden=mode!=="register";$("#account-key-wrap").hidden=mode!=="register";$("#account-name").required=mode==="register";$("#account-key").required=false;$("#account-password").autocomplete=mode==="login"?"current-password":"new-password";$("#account-submit").textContent=mode==="login"?"Sign in ↗":"Create account ↗";$("#account-hint").textContent=mode==="register"?"Use the one-time activation key only if this is the designated Super Admin email. All other users can register normally. Password must be at least 10 characters.":"Your session uses a secure HttpOnly cookie. Passwords are never stored in your browser.";modal.querySelectorAll("[data-mode]").forEach(b=>b.classList.toggle("active",b.dataset.mode===mode));$("#account-error").classList.remove("show")}
  modal.querySelectorAll("[data-mode]").forEach(b=>b.addEventListener("click",()=>setMode(b.dataset.mode)));
  modal.querySelector(".account-close").addEventListener("click",close);admin.querySelector(".account-close").addEventListener("click",close);
  modal.addEventListener("click",e=>{if(e.target===modal)close()});admin.addEventListener("click",e=>{if(e.target===admin)close()});
  document.addEventListener("keydown",e=>{if(e.key==="Escape")close()});
  $("#account-form").addEventListener("submit",async e=>{e.preventDefault();const btn=$("#account-submit"),error=$("#account-error");btn.disabled=true;btn.textContent="Please wait…";error.classList.remove("show");try{const payload={email:$("#account-email").value,password:$("#account-password").value};if(authMode==="register"){payload.name=$("#account-name").value;payload.adminSetupKey=$("#account-key").value}const result=await api(authMode==="login"?"auth/login":"auth/register",{method:"POST",body:JSON.stringify(payload)});me=result.user;close();paintAccount();window.dispatchEvent(new CustomEvent("recontent:auth",{detail:me}));notify(me.role==="admin"?"Super Admin signed in.":"You're signed in.");}catch(err){error.textContent=err.message;error.classList.add("show")}finally{btn.disabled=false;btn.textContent=authMode==="login"?"Sign in ↗":"Create account ↗"}});
  function paintAccount(){let button=$("#account-trigger");if(!button){button=document.createElement("button");button.id="account-trigger";button.className="account-action";$(".top-actions").prepend(button)}button.textContent=me?(me.role==="admin"?"Admin console ↗":me.name.split(" ")[0]+" · Account"):"Sign in ↗";button.classList.toggle("admin",me?.role==="admin");button.onclick=()=>me?.role==="admin"?openAdmin():me?openProfile():openAuth("login");
    const bottom=$(".sidebar-bottom");if(bottom){bottom.innerHTML="";const av=document.createElement("div");av.className="avatar";av.textContent=me?me.name.slice(0,2).toUpperCase():"RC";const meta=document.createElement("div");meta.className="user-meta";const b=document.createElement("b");b.textContent=me?me.name:"Guest workspace";const small=document.createElement("small");small.textContent=me?(me.role==="admin"?"Super Admin":"Member account"):"Sign in to sync your work";meta.append(b,small);meta.style.cursor=me?"pointer":"default";if(me)meta.addEventListener("click",openProfile);bottom.append(av,meta);const action=document.createElement("button");action.className="more-btn";action.textContent=me?"↪":"···";action.title=me?"Sign out":"Sign in";action.onclick=async()=>{if(!me){openAuth();return}if(!confirm("Sign out of ReContent?"))return;await api("auth/logout",{method:"POST",body:"{}"});me=null;paintAccount();window.dispatchEvent(new CustomEvent("recontent:auth",{detail:null}));notify("Signed out.")};bottom.append(action)}
  }
  async function openAdmin(){admin.classList.add("open");await refreshAdmin()}
  async function refreshAdmin(){const stats=$("#admin-stats"),users=$("#admin-users"),activity=$("#admin-activity");stats.innerHTML="";users.innerHTML="<div class='admin-empty'>Loading members…</div>";activity.innerHTML="<div class='admin-empty'>Loading activity…</div>";try{const data=await api("admin/overview");Object.entries({Members:data.stats.members,Admins:data.stats.admins,Suspended:data.stats.suspended,Activities:data.stats.events}).forEach(([label,value])=>{const card=document.createElement("div");card.className="admin-stat";const l=document.createElement("span");l.textContent=label.toUpperCase();const v=document.createElement("b");v.textContent=value;card.append(l,v);stats.append(card)});users.innerHTML="";if(!data.users.length)users.innerHTML="<div class='admin-empty'>No members yet.</div>";data.users.forEach(u=>{const row=document.createElement("div");row.className="admin-row";const main=document.createElement("div");main.className="admin-row-main";const name=document.createElement("b");name.textContent=u.name;const sub=document.createElement("small");sub.textContent=u.email+" · "+u.status;main.append(name,sub);row.append(main);if(u.role!=="admin"){const action=document.createElement("button");action.textContent=u.status==="suspended"?"Restore":"Suspend";action.onclick=async()=>{await api("admin/users",{method:"PATCH",body:JSON.stringify({userId:u.id,status:u.status==="suspended"?"active":"suspended"})});await refreshAdmin()};row.append(action)}users.append(row)});activity.innerHTML="";if(!data.activity.length)activity.innerHTML="<div class='admin-empty'>No activity recorded yet.</div>";data.activity.slice(0,60).forEach(ev=>{const row=document.createElement("div");row.className="admin-row";const main=document.createElement("div");main.className="admin-row-main";const name=document.createElement("b");name.textContent=ev.event.replaceAll("."," · ");const sub=document.createElement("small");const detail=ev.details||{},summary=Array.isArray(detail.formats)?detail.formats.join(", "):typeof detail.title==="string"?detail.title:"",usage=typeof detail.neurons==="number"?" · "+detail.neurons.toFixed(1)+" neurons":"";sub.textContent=ev.email+" · "+new Date(ev.createdAt).toLocaleString()+(summary?" · "+summary:"")+usage;main.append(name,sub);row.append(main);activity.append(row)})}catch(err){users.textContent="";const message=document.createElement("div");message.className="admin-empty";message.textContent=err.message||"Could not load admin data.";users.append(message);activity.textContent=""}}
  $("#admin-refresh").addEventListener("click",refreshAdmin);
  $("#account-trigger")?.addEventListener("click",()=>openAuth());
  paintAccount();
  api("auth/me").then(({user})=>{me=user;paintAccount();window.dispatchEvent(new CustomEvent("recontent:auth",{detail:me}))}).catch(()=>{me=null;paintAccount();window.dispatchEvent(new CustomEvent("recontent:auth",{detail:null}))});
  window.recontentAuth={open:openAuth,getUser:()=>me};
})();