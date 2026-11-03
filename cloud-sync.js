/* Per-account cloud sync for ReContent workspace data. */
(() => {
  const KEYS={
    library:"recontent-library-v1",
    schedule:"recontent-calendar-v1",
    brand:"recontent-brand-v1",
    brief:"recontent-current-brief-v1"
  };
  let signedIn=false,hydrating=false,timer=null,syncing=false,pushPending=false;
  const parse=(key,fallback)=>{try{return JSON.parse(localStorage.getItem(key)??JSON.stringify(fallback))}catch{return fallback}};
  const record=value=>value&&typeof value==="object"&&!Array.isArray(value)?value:{};
  const snapshot=()=>{
    const library=parse(KEYS.library,[]),schedule=parse(KEYS.schedule,[]);
    const formats=["thread","carousel","linkedin","reel","newsletter","caption"];
    return {
      library:(Array.isArray(library)?library:[]).filter(x=>x&&typeof x==="object"&&!Array.isArray(x)&&typeof x.id==="string"&&typeof x.title==="string"&&Array.isArray(x.formats)).map(x=>({...x,formats:x.formats.filter(f=>formats.includes(f)),outputs:Array.isArray(x.outputs)?x.outputs.filter(o=>o&&formats.includes(o.format)&&typeof o.text==="string"):[]})),
      schedule:(Array.isArray(schedule)?schedule:[]).filter(x=>x&&typeof x==="object"&&!Array.isArray(x)&&typeof x.id==="string"&&typeof x.title==="string"&&/^\\d{4}-\\d{2}-\\d{2}$/.test(x.date)&&formats.includes(x.format)).map(x=>({...x,done:!!x.done})),
      brand:record(parse(KEYS.brand,{})),
      brief:record(parse(KEYS.brief,{}))
    };
  };
  const mergeList=(local,remote)=>{
    const map=new Map();
    [...(Array.isArray(local)?local:[]),...(Array.isArray(remote)?remote:[])].forEach(item=>{
      if(item&&typeof item==="object"&&item.id!==undefined)map.set(String(item.id),item);
    });
    return [...map.values()];
  };
  const mergeWorkspace=(local,remote)=>({
    library:mergeList(local.library,remote.library),
    schedule:mergeList(local.schedule,remote.schedule),
    brand:Object.keys(record(remote.brand)).length?record(remote.brand):record(local.brand),
    brief:Object.keys(record(remote.brief)).length?record(remote.brief):record(local.brief)
  });
  const toast=(message)=>{
    const el=document.querySelector("#toast");
    if(el){el.textContent=message;el.classList.add("show");setTimeout(()=>el.classList.remove("show"),2400)}
  };
  async function api(method,workspace){
    const response=await fetch("/api/workspace",{method,credentials:"same-origin",headers:{"content-type":"application/json"},...(workspace?{body:JSON.stringify({workspace})}:{})});
    const data=await response.json().catch(()=>({}));
    if(!response.ok)throw new Error(data.error||"Cloud sync failed.");
    return data;
  }
  function applyRemote(workspace){
    hydrating=true;
    try{
      for(const key of Object.keys(KEYS)){
        if(workspace[key]!==undefined)localStorage.setItem(KEYS[key],JSON.stringify(workspace[key]));
      }
    }finally{hydrating=false}
  }
  async function syncNow(showToast=false){
    if(!signedIn||hydrating)return;
    if(syncing){pushPending=true;return}
    syncing=true;
    try{
      const result=await api("GET");
      if(result.workspace){
        const merged=mergeWorkspace(snapshot(),result.workspace);
        const current=snapshot();
        const changed=Object.keys(KEYS).some(k=>JSON.stringify(merged[k])!==JSON.stringify(current[k]));
        const remoteChanged=Object.keys(KEYS).some(k=>JSON.stringify(merged[k])!==JSON.stringify(result.workspace[k]));
        if(changed){applyRemote(merged);if(remoteChanged)await api("PUT",merged);location.reload();return}
        if(remoteChanged)await api("PUT",merged);
      }else{
        await api("PUT",snapshot());
      }
      const badge=document.querySelector("#cloud-sync-status");
      if(badge){badge.textContent="CLOUD SYNCED";badge.dataset.state="ok"}
      if(showToast)toast("Workspace synced to your account.");
    }catch(error){
      const badge=document.querySelector("#cloud-sync-status");
      if(badge){badge.textContent="SYNC PAUSED";badge.dataset.state="error"}
      if(showToast)toast(error.message);
    }finally{
      syncing=false;
      if(pushPending){pushPending=false;push()}
    }
  }
  async function push(){
    if(!signedIn||hydrating)return;
    if(syncing){pushPending=true;return}
    syncing=true;
    try{
      await api("PUT",snapshot());
      const badge=document.querySelector("#cloud-sync-status");
      if(badge){badge.textContent="CLOUD SYNCED";badge.dataset.state="ok"}
    }catch(error){
      const badge=document.querySelector("#cloud-sync-status");
      if(badge){badge.textContent="SYNC ERROR";badge.dataset.state="error"}
      console.error("ReContent cloud sync:",error);
    }finally{
      syncing=false;
      if(pushPending){pushPending=false;push()}
    }
  }
  const original=Storage.prototype.setItem;
  Storage.prototype.setItem=function(key,value){
    original.call(this,key,value);
    if(this===localStorage&&Object.values(KEYS).includes(String(key))&&signedIn&&!hydrating){
      clearTimeout(timer);const badge=document.querySelector("#cloud-sync-status");
      if(badge){badge.textContent="SAVING…";badge.dataset.state="pending"}
      timer=setTimeout(push,900);
    }
  };
  function addStatus(){
    if(document.querySelector("#cloud-sync-status"))return;
    const target=document.querySelector(".top-actions");
    if(!target)return;
    const status=document.createElement("span");status.id="cloud-sync-status";status.textContent=signedIn?"CONNECTING…":"LOCAL WORKSPACE";status.dataset.state="idle";
    target.append(status);
    const style=document.createElement("style");
    style.textContent="#cloud-sync-status{align-self:center;font:8px var(--mono);letter-spacing:.6px;color:#858279;padding:7px 9px;border:1px solid var(--line);border-radius:5px;white-space:nowrap}#cloud-sync-status[data-state=ok]{color:#56702b;border-color:#cad8a7;background:#f0f4e5}#cloud-sync-status[data-state=pending]{color:#9a6a16}#cloud-sync-status[data-state=error]{color:#a33e2d;border-color:#e8b8ad;background:#fbefeb}@media(max-width:600px){#cloud-sync-status{display:none}}";
    document.head.append(style);
  }
  function accountChanged(user){
    signedIn=!!user;
    addStatus();
    const badge=document.querySelector("#cloud-sync-status");
    if(!signedIn){if(badge){badge.textContent="LOCAL WORKSPACE";badge.dataset.state="idle"}return}
    if(badge){badge.textContent="CONNECTING…";badge.dataset.state="pending"}
    syncNow(false);
  }
  window.addEventListener("recontent:auth",event=>accountChanged(event.detail));
  addStatus();
  const existing=window.recontentAuth?.getUser?.();
  if(existing)accountChanged(existing);
})();