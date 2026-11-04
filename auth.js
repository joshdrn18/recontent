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
    .admin-modal{padding:0;background:#090b0deF;backdrop-filter:blur(14px);align-items:stretch;justify-content:stretch}
    .admin-box{width:100vw;height:100dvh;max-height:none;max-width:none;padding:0;border:0;border-radius:0;background:#0c0e11;color:#eceeea;overflow:hidden;display:grid;grid-template-columns:248px minmax(0,1fr);box-shadow:none}
    .admin-rail{background:#101216;border-right:1px solid #24272c;padding:27px 17px 18px;display:flex;flex-direction:column;gap:22px;min-height:0}
    .admin-logo{font:700 23px var(--display);letter-spacing:-1.3px;color:#f4f4f0;padding:0 10px}.admin-logo span{font-weight:400;color:#b8bcb6}.admin-logo i{color:#f28b5d;font-style:normal}
    .admin-rail-caption{font:8px var(--mono);letter-spacing:1.4px;color:#6f747d;padding:0 11px;margin-bottom:-14px}
    .admin-workspace{display:flex;align-items:center;gap:10px;padding:11px;background:#191c21;border:1px solid #292d33;border-radius:9px}.admin-workspace-icon{width:32px;height:32px;display:grid;place-items:center;background:#f18a5d;color:#171310;border-radius:8px;font:700 15px var(--display)}.admin-workspace b,.admin-workspace small{display:block}.admin-workspace b{font-size:10px}.admin-workspace small{font-size:8px;color:#858b94;margin-top:3px}
    .admin-nav{display:grid;gap:5px}.admin-nav a{display:flex;align-items:center;gap:11px;padding:11px 12px;border-radius:7px;color:#9298a1;text-decoration:none;font:500 11px var(--sans);transition:.18s}.admin-nav a span{width:17px;font-size:15px;text-align:center}.admin-nav a:hover,.admin-nav a.active{background:#22262c;color:#f6f6f2}.admin-nav a.active{box-shadow:inset 2px 0 #f18a5d}.admin-nav em{font:7px var(--mono);font-style:normal;color:#9ac59e;background:#1d3024;border-radius:4px;padding:3px 5px;margin-left:auto}
    .admin-rail-bottom{margin-top:auto;border-top:1px solid #292c31;padding:17px 8px 3px;display:flex;align-items:center;gap:9px}.admin-online-dot,.admin-live i{width:7px;height:7px;border-radius:50%;background:#76c68b;box-shadow:0 0 12px #76c68b66}.admin-rail-bottom b,.admin-rail-bottom small{display:block}.admin-rail-bottom b{font-size:9px;color:#d8ddd7}.admin-rail-bottom small{font-size:8px;color:#747b84;margin-top:3px}
    .admin-main{min-width:0;display:flex;flex-direction:column;min-height:0;background:#0c0e11}.admin-topbar{height:64px;flex:none;border-bottom:1px solid #24272c;display:flex;align-items:center;justify-content:space-between;padding:0 34px;background:#101216}.admin-breadcrumb{font-size:10px;color:#7e858f}.admin-breadcrumb span{padding:0 8px;color:#454b54}.admin-breadcrumb b{font-weight:600;color:#e2e5df}.admin-top-actions{display:flex;align-items:center;gap:14px}.admin-live{display:flex;align-items:center;gap:7px;font:8px var(--mono);letter-spacing:.8px;color:#9ba39a}.admin-live i{display:inline-block}.admin-refresh,.admin-close{border:1px solid #30353d;background:#191d22;color:#e5e8e2;border-radius:7px;padding:8px 11px;font-size:10px}.admin-close{font-size:18px;line-height:14px;padding:7px 10px}
    .admin-scroll{overflow:auto;padding:34px clamp(20px,4vw,54px) 22px;min-height:0;scroll-behavior:smooth}.admin-welcome{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;margin:4px 0 27px}.admin-eyebrow,.admin-panel-kicker{font:8px var(--mono);letter-spacing:1.6px;color:#e58a60}.admin-welcome h1{font:600 clamp(28px,3vw,39px) var(--display);letter-spacing:-1.7px;margin:9px 0 7px;color:#f1f1ed}.admin-welcome h1 span{color:#f18a5d}.admin-welcome p{font-size:11px;color:#858c95;margin:0}.admin-date-chip{border:1px solid #2a2e34;border-radius:7px;padding:9px 12px;color:#a0a6ad;font-size:9px;white-space:nowrap}
    .admin-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-bottom:17px}.admin-stat{background:#14171b;border:1px solid #292d33;border-radius:10px;padding:17px 18px;min-width:0;position:relative;overflow:hidden}.admin-stat:after{content:"";position:absolute;right:-25px;top:-35px;width:90px;height:90px;border-radius:50%;background:#f18a5d0b}.admin-stat span{display:block;font:8px var(--mono);letter-spacing:1px;color:#858c95}.admin-stat b{display:block;font:600 29px var(--display);letter-spacing:-1px;color:#f0f1ec;margin-top:10px}.admin-stat small{display:block;font-size:8px;color:#707781;margin-top:5px}
    .admin-content-grid{display:grid;grid-template-columns:1fr 1fr;gap:13px;margin-bottom:13px}.admin-panel{background:#14171b;border:1px solid #292d33;border-radius:10px;padding:18px;min-width:0}.admin-panel-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10px;margin-bottom:14px}.admin-panel-head h2{font:600 16px var(--display);letter-spacing:-.3px;color:#eff0eb;margin:5px 0 0}.admin-panel-head p{font-size:9px;color:#7f8790;margin:6px 0 0}.admin-panel-count{font:7px var(--mono);letter-spacing:.8px;color:#747c86;border:1px solid #30353c;border-radius:5px;padding:5px 7px;white-space:nowrap}
    .admin-list{display:grid;gap:7px;max-height:340px;overflow:auto}.admin-row{display:flex;align-items:center;gap:10px;padding:10px;background:#191d22;border:1px solid #282d34;border-radius:7px;min-width:0;color:#e8eae5}.admin-row-main{min-width:0;flex:1}.admin-row-main b,.admin-row-main small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.admin-row-main b{font-size:10px;font-weight:600;color:#e3e6df}.admin-row-main small{font-size:8px;color:#838b95;margin-top:4px}.admin-row button{border:1px solid #45403b;background:#28231f;color:#f0a17d;border-radius:5px;padding:7px 9px;font-size:8px}.admin-empty{font-size:10px;color:#7e8791;padding:18px 5px}.admin-ai-panel{margin-bottom:18px}.admin-ai-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px}.admin-ai-metrics .admin-stat{background:#191d22;padding:12px}.admin-ai-metrics .admin-stat b{font-size:21px}.admin-ai-events{margin-top:12px;max-height:170px}.admin-footer{display:flex;justify-content:space-between;gap:10px;padding:13px 2px 4px;border-top:1px solid #24272c;color:#69717b;font:7px var(--mono);letter-spacing:.8px}
    @media(max-width:900px){.admin-box{grid-template-columns:190px minmax(0,1fr)}.admin-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}.admin-content-grid{grid-template-columns:1fr}.admin-scroll{padding:25px 22px}.admin-topbar{padding:0 22px}}
    @media(max-width:600px){.admin-box{grid-template-columns:58px minmax(0,1fr)}.admin-rail{padding:18px 6px;gap:20px}.admin-logo{font-size:0;text-align:center;padding:0}.admin-logo:first-letter{font-size:22px}.admin-rail-caption,.admin-workspace,.admin-rail-bottom{display:none}.admin-nav a{justify-content:center;padding:12px 3px;font-size:0}.admin-nav a span{font-size:17px}.admin-nav em{display:none}.admin-topbar{height:56px;padding:0 12px}.admin-breadcrumb{font-size:8px}.admin-top-actions{gap:6px}.admin-live{display:none}.admin-refresh{padding:7px 8px}.admin-refresh span{display:none}.admin-scroll{padding:21px 12px}.admin-welcome{align-items:flex-start;flex-direction:column;margin-bottom:18px}.admin-date-chip{font-size:8px}.admin-metrics{gap:7px}.admin-stat{padding:12px}.admin-stat b{font-size:23px}.admin-ai-metrics{grid-template-columns:1fr 1fr}.admin-panel{padding:13px}.admin-footer{font-size:6px;flex-direction:column}.admin-list{max-height:280px}}
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
    .admin-modal{padding:18px;background:#11130fe8;backdrop-filter:blur(12px)}
    .admin-box{width:min(1440px,calc(100vw - 36px));height:min(900px,calc(100vh - 36px));max-height:none;padding:0;border-radius:15px;background:#f5f3ed;overflow:hidden;display:flex;flex-direction:column}
    .admin-head{padding:22px 26px 14px;margin:0;background:#fffefa;border-bottom:1px solid #e5e1d7}
    .admin-head h2{font-size:30px}.admin-head p{margin-top:4px}
    .admin-stats{grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:18px 24px}
    .admin-stat{background:#fffefa;border:1px solid #e5e1d7;padding:16px}.admin-stat b{font-size:28px}
    .admin-columns{grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:14px;padding:0 24px 22px;overflow:auto}
    .admin-section{background:#fffefa;border:1px solid #e5e1d7;padding:16px}.admin-section h3{font-size:15px}
    .admin-list{max-height:calc(100vh - 330px);gap:8px}
    .admin-row{padding:11px;border:1px solid #efede6;background:#f7f5ef}
    .admin-row-main b{font-size:11px}.admin-row-main small{font-size:9px}
    .admin-row button{font-size:9px;padding:7px 9px}
    .admin-foot{padding:0 24px 20px;margin-top:auto}
    @media(max-width:800px){.admin-box{width:calc(100vw - 20px);height:calc(100vh - 20px)}.admin-columns{grid-template-columns:1fr}.admin-stats{grid-template-columns:repeat(2,1fr)}}
    @media(max-width:600px){.account-box{padding:18px}.admin-modal{padding:0}.admin-box{width:100vw;height:100dvh;border-radius:0}.admin-head{padding:18px}.admin-columns{padding:0 14px 14px}.admin-stats{margin:14px;gap:7px}.admin-stat{padding:11px}.admin-stat b{font-size:23px}.admin-foot{padding:0 14px 14px}.top-actions{gap:6px}.account-action{padding:8px}}
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
    <div class="admin-box" role="dialog" aria-modal="true" aria-label="ReContent Super Admin">
      <aside class="admin-rail">
        <div class="admin-logo">re<span>content</span><i>.</i></div>
        <div class="admin-rail-caption">WORKSPACE</div>
        <div class="admin-workspace"><div class="admin-workspace-icon">R</div><div><b>ReContent</b><small>Platform administration</small></div></div>
        <div class="admin-rail-caption">MANAGE</div>
        <nav class="admin-nav">
          <a class="active" href="#admin-overview"><span>◫</span> Overview</a>
          <a href="#admin-members"><span>♙</span> Members <em>LIVE</em></a>
          <a href="#admin-activity"><span>◷</span> Activity log</a>
          <a href="#admin-ai"><span>✳</span> AI & usage</a>
        </nav>
        <div class="admin-rail-bottom"><div class="admin-online-dot"></div><div><b>System operational</b><small>Admin environment</small></div></div>
      </aside>
      <main class="admin-main">
        <header class="admin-topbar"><div class="admin-breadcrumb">ReContent <span>/</span> <b>Admin console</b></div><div class="admin-top-actions"><span class="admin-live"><i></i> LIVE DATA</span><button id="admin-refresh" class="admin-refresh">↻ <span>Refresh</span></button><button class="account-close admin-close" aria-label="Close">×</button></div></header>
        <div class="admin-scroll">
          <section class="admin-welcome" id="admin-overview"><div><div class="admin-eyebrow">SUPER ADMIN / OVERVIEW</div><h1>Platform overview<span>.</span></h1><p>Your command center for members, product activity and AI consumption.</p></div><div class="admin-date-chip">◷ &nbsp; Live platform snapshot</div></section>
          <div class="admin-metrics" id="admin-stats"></div>
          <div class="admin-content-grid">
            <section class="admin-panel admin-members-panel" id="admin-members"><div class="admin-panel-head"><div><div class="admin-panel-kicker">ACCOUNT DIRECTORY</div><h2>Members</h2></div><span class="admin-panel-count">USER ACCOUNTS</span></div><div class="admin-list" id="admin-users"></div></section>
            <section class="admin-panel admin-activity-panel" id="admin-activity"><div class="admin-panel-head"><div><div class="admin-panel-kicker">AUDIT TRAIL</div><h2>Recent activity</h2></div><span class="admin-panel-count">LATEST EVENTS</span></div><div class="admin-list" id="admin-activity"></div></section>
          </div>
          <section class="admin-panel admin-ai-panel" id="admin-ai"><div class="admin-panel-head"><div><div class="admin-panel-kicker">WORKERS AI TELEMETRY</div><h2>AI usage & generation</h2><p>Consumption metrics recorded from successful generation requests.</p></div><span class="admin-panel-count">USAGE</span></div><div class="admin-ai-metrics" id="admin-ai-stats"></div><div class="admin-list admin-ai-events" id="admin-ai-events"></div></section>
          <footer class="admin-footer"><span>RECONTENT ADMIN CONSOLE</span><span>Private platform access · Keep your credentials secure</span></footer>
        </div>
      </main>
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
  function paintAccount(){let adminLink=$("#admin-sidebar-entry");if(me?.role==="admin"){if(!adminLink){adminLink=document.createElement("button");adminLink.id="admin-sidebar-entry";adminLink.className="nav-item admin-sidebar-entry";adminLink.innerHTML='<span class="nav-icon">⌘</span><span>Admin Control Room</span><span class="nav-new">ADMIN</span>';adminLink.addEventListener("click",openAdmin);const nav=$(".main-nav");if(nav)nav.append(adminLink)}adminLink.hidden=false}else if(adminLink){adminLink.remove()}let button=$("#account-trigger");if(!button){button=document.createElement("button");button.id="account-trigger";button.className="account-action";$(".top-actions").prepend(button)}button.textContent=me?(me.role==="admin"?"Admin console ↗":me.name.split(" ")[0]+" · Account"):"Sign in ↗";button.classList.toggle("admin",me?.role==="admin");button.onclick=()=>me?.role==="admin"?openAdmin():me?openProfile():openAuth("login");
    const bottom=$(".sidebar-bottom");if(bottom){bottom.innerHTML="";const av=document.createElement("div");av.className="avatar";av.textContent=me?me.name.slice(0,2).toUpperCase():"RC";const meta=document.createElement("div");meta.className="user-meta";const b=document.createElement("b");b.textContent=me?me.name:"Guest workspace";const small=document.createElement("small");small.textContent=me?(me.role==="admin"?"Super Admin":"Member account"):"Sign in to sync your work";meta.append(b,small);meta.style.cursor=me?"pointer":"default";if(me)meta.addEventListener("click",openProfile);bottom.append(av,meta);const action=document.createElement("button");action.className="more-btn";action.textContent=me?"↪":"···";action.title=me?"Sign out":"Sign in";action.onclick=async()=>{if(!me){openAuth();return}if(!confirm("Sign out of ReContent?"))return;await api("auth/logout",{method:"POST",body:"{}"});me=null;paintAccount();window.dispatchEvent(new CustomEvent("recontent:auth",{detail:null}));notify("Signed out.")};bottom.append(action)}
  }
  async function openAdmin(){admin.classList.add("open");await refreshAdmin()}
  async function refreshAdmin(){const stats=$("#admin-stats"),users=$("#admin-users"),activity=$("#admin-activity");stats.innerHTML="";users.innerHTML="<div class='admin-empty'>Loading members…</div>";activity.innerHTML="<div class='admin-empty'>Loading activity…</div>";try{const data=await api("admin/overview");Object.entries({Users:data.stats.users,Members:data.stats.members,Admins:data.stats.admins,Suspended:data.stats.suspended,Activities:data.stats.events}).forEach(([label,value])=>{const card=document.createElement("div");card.className="admin-stat";const l=document.createElement("span");l.textContent=label.toUpperCase();const v=document.createElement("b");v.textContent=value;card.append(l,v);stats.append(card)});users.innerHTML="";if(!data.users.length)users.innerHTML="<div class='admin-empty'>No members yet.</div>";data.users.forEach(u=>{const row=document.createElement("div");row.className="admin-row";const main=document.createElement("div");main.className="admin-row-main";const name=document.createElement("b");name.textContent=u.name;const sub=document.createElement("small");sub.textContent=u.email+" · "+u.status;main.append(name,sub);row.append(main);if(u.role!=="admin"){const action=document.createElement("button");action.textContent=u.status==="suspended"?"Restore":"Suspend";action.onclick=async()=>{await api("admin/users",{method:"PATCH",body:JSON.stringify({userId:u.id,status:u.status==="suspended"?"active":"suspended"})});await refreshAdmin()};row.append(action)}users.append(row)});activity.innerHTML="";if(!data.activity.length)activity.innerHTML="<div class='admin-empty'>No activity recorded yet.</div>";data.activity.slice(0,60).forEach(ev=>{const row=document.createElement("div");row.className="admin-row";const main=document.createElement("div");main.className="admin-row-main";const name=document.createElement("b");name.textContent=ev.event.replaceAll("."," · ");const sub=document.createElement("small");const detail=ev.details||{},summary=Array.isArray(detail.formats)?detail.formats.join(", "):typeof detail.title==="string"?detail.title:"",usage=typeof detail.neurons==="number"?" · "+detail.neurons.toFixed(1)+" neurons":"";sub.textContent=ev.email+" · "+new Date(ev.createdAt).toLocaleString()+(summary?" · "+summary:"")+usage;main.append(name,sub);row.append(main);activity.append(row)});
      const generations=data.activity.filter(ev=>ev.event==="content.generated");
      const neurons=generations.reduce((n,ev)=>n+Number(ev.details?.neurons||0),0);
      const inputTokens=generations.reduce((n,ev)=>n+Number(ev.details?.inputTokens||0),0);
      const outputTokens=generations.reduce((n,ev)=>n+Number(ev.details?.outputTokens||0),0);
      $("#admin-ai-stats").innerHTML=[["GENERATIONS",generations.length],["AI NEURONS",neurons.toFixed(1)],["INPUT TOKENS",inputTokens.toLocaleString()],["OUTPUT TOKENS",outputTokens.toLocaleString()]].map(([label,value])=>`<div class="admin-stat"><span>${label}</span><b>${value}</b></div>`).join("");
      $("#admin-ai-events").innerHTML=generations.slice(0,8).map(ev=>{const row=document.createElement("div");row.className="admin-row";const main=document.createElement("div");main.className="admin-row-main";const name=document.createElement("b");name.textContent=ev.email+" · "+(ev.details?.formats||[]).join(", ");const sub=document.createElement("small");sub.textContent=new Date(ev.createdAt).toLocaleString()+" · "+Number(ev.details?.neurons||0).toFixed(1)+" neurons";main.append(name,sub);row.append(main);return row.outerHTML}).join("")||"<div class='admin-empty'>No successful AI generations recorded yet.</div>";
    }catch(err){users.textContent="";const message=document.createElement("div");message.className="admin-empty";message.textContent=err.message||"Could not load admin data.";users.append(message);activity.textContent=""}}
  $("#admin-refresh").addEventListener("click",refreshAdmin);
  paintAccount();
  api("auth/me").then(({user})=>{me=user;paintAccount();window.dispatchEvent(new CustomEvent("recontent:auth",{detail:me}))}).catch(()=>{me=null;paintAccount();window.dispatchEvent(new CustomEvent("recontent:auth",{detail:null}))});
  window.recontentAuth={open:openAuth,getUser:()=>me};
})();