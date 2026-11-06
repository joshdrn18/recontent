/* ReContent workspace upgrade: structured briefs, editable outputs and review states. */
(() => {
  const $ = (s, root=document) => root.querySelector(s);
  const briefMarkup = document.createElement("section");
  briefMarkup.className = "form-section brief-section";
  briefMarkup.innerHTML = `
    <div class="form-title"><span class="step-number">04</span><div><h3>Give the remix a direction</h3><p>Shape the message before reviewing each format.</p></div></div>
    <div class="brief-grid">
      <label>WHO IS THIS FOR<input id="brief-audience" placeholder="e.g. solo creators, small business owners"></label>
      <label>WHAT SHOULD IT ACHIEVE<select id="brief-goal"><option value="Educate">Educate</option><option value="Build trust">Build trust</option><option value="Start conversations">Start conversations</option><option value="Drive sign-ups">Drive sign-ups</option><option value="Promote an offer">Promote an offer</option></select></label>
      <label>CALL TO ACTION<input id="brief-cta" placeholder="e.g. Save this for your next planning session"></label>
      <label>KEY POINTS TO KEEP<input id="brief-keypoints" placeholder="Separate key points with commas"></label>
      <label class="brief-wide">AVOID / EXTRA INSTRUCTIONS<textarea id="brief-notes" rows="2" placeholder="Claims to avoid, details to preserve, words that should not appear..."></textarea></label>
    </div>
    <div class="brief-hint">Your brief is included in each AI generation request and saved with your cloud workspace.</div>`;
  const generateBtn = $("#generate-btn");
  if (generateBtn) generateBtn.before(briefMarkup);

  const style = document.createElement("style");
  style.textContent = `
    .brief-grid{display:grid;grid-template-columns:1fr 1fr;gap:11px}
    .brief-grid label{display:grid;gap:6px;font:8px var(--mono);letter-spacing:.7px;color:#858279}
    .brief-grid input,.brief-grid select,.brief-grid textarea{width:100%;min-width:0;border:1px solid var(--line);border-radius:5px;background:#f8f6f0;padding:10px;color:var(--ink);font:10px var(--sans);letter-spacing:0;outline:none}
    .brief-grid input:focus,.brief-grid select:focus,.brief-grid textarea:focus{border-color:var(--orange)}
    .brief-wide{grid-column:1/-1}
    .brief-hint{font-size:9px;line-height:1.6;color:#969187;margin-top:10px}
    .result-content[contenteditable=true]{outline:none;min-height:90px}
    .result-content[contenteditable=true]:focus{background:#fffdf8;box-shadow:inset 0 0 0 1px #ef603b}
    .review-tools{display:flex;align-items:center;gap:8px;padding:0 14px 12px;flex-wrap:wrap}
    .review-tools button{border:1px solid var(--line);background:#fffdf8;border-radius:4px;padding:6px 9px;font-size:9px;color:#666}
    .review-tools .reviewed{background:#e7efcf;border-color:#c8d99a;color:#43551d}
    .review-state{font:8px var(--mono);color:#8b887e;margin-left:auto}
    @media(max-width:560px){.brief-grid{grid-template-columns:1fr}.brief-wide{grid-column:auto}}
  `;
  document.head.append(style);

  const brief = () => ({
    audience: $("#brief-audience")?.value.trim() || "",
    goal: $("#brief-goal")?.value || "Educate",
    cta: $("#brief-cta")?.value.trim() || "",
    keypoints: $("#brief-keypoints")?.value.trim() || "",
    notes: $("#brief-notes")?.value.trim() || ""
  });
  const draftBriefKey = "recontent-current-brief-v1";
  try {
    const old = JSON.parse(localStorage.getItem(draftBriefKey) || "{}");
    if ($("#brief-audience")) $("#brief-audience").value = old.audience || "";
    if ($("#brief-goal")) $("#brief-goal").value = old.goal || "Educate";
    if ($("#brief-cta")) $("#brief-cta").value = old.cta || "";
    if ($("#brief-keypoints")) $("#brief-keypoints").value = old.keypoints || "";
    if ($("#brief-notes")) $("#brief-notes").value = old.notes || "";
  } catch {}
  briefMarkup.addEventListener("input", () => {
    try { localStorage.setItem(draftBriefKey, JSON.stringify(brief())); } catch {}
  });

  const resultRoot = $("#results-list");
  if (!resultRoot) return;
  const decorate = () => {
    const cards = [...resultRoot.querySelectorAll(".result-card")];
    cards.forEach((card, index) => {
      const content = card.querySelector(".result-content");
      if (content && !content.dataset.editorReady) {
        content.contentEditable = "true";
        content.setAttribute("role", "textbox");
        content.setAttribute("aria-label", "Edit generated content");
        content.dataset.editorReady = "true";
        content.dataset.originalText = content.innerText;
        content.title = "Click here to edit this draft";
        content.addEventListener("input", () => {
          if (!content.dataset.editedLogged) { content.dataset.editedLogged = "true"; trackActivity("content.edited", { action: "draft" }); }
          if (typeof generated !== "undefined" && generated[index]) generated[index].text = content.innerText.replace(/\u00a0/g, " ");
          const state = card.querySelector(".review-state");
          if (state) state.textContent = "EDITED · UNSAVED";
        });
      }
      if (!card.querySelector(".review-tools")) {
        const tools = document.createElement("div");
        tools.className = "review-tools";
        const review = document.createElement("button");
        review.type = "button";
        review.textContent = "✓ Mark reviewed";
        const state = document.createElement("span");
        state.className = "review-state";
        state.textContent = "DRAFT · CLICK TEXT TO EDIT";
        review.addEventListener("click", () => {
          const active = review.classList.toggle("reviewed");
          review.textContent = active ? "✓ Reviewed" : "✓ Mark reviewed";
          state.textContent = active ? "READY TO SAVE" : "DRAFT";
        });
        const reset = document.createElement("button");
        reset.type = "button";
        reset.textContent = "Reset edits";
        reset.addEventListener("click", () => {
          if (typeof generated === "undefined" || !generated[index]) return;
          content.textContent = content.dataset.originalText || generated[index].text;
          generated[index].text = content.textContent;
          state.textContent = "RESET · DRAFT";
          review.classList.remove("reviewed");
          review.textContent = "✓ Mark reviewed";
        });
        tools.append(review, reset, state);
        card.append(tools);
      }
    });
  };
  new MutationObserver(decorate).observe(resultRoot, {childList:true,subtree:true});
  decorate();

  $("#save-all")?.addEventListener("click", () => {
    try {
      const data = JSON.parse(localStorage.getItem("recontent-library-v1") || "[]");
      if (data[0] && data[0].saved) {
        data[0].brief = brief();
        data[0].status = "Draft";
        data[0].updatedAt = new Date().toISOString();
        localStorage.setItem("recontent-library-v1", JSON.stringify(data));
      }
    } catch {}
  }, true);

  const source = $("#source-content");
  const updateQuality = () => {
    if (!source) return;
    const words = (source.value.trim().match(/\S+/g) || []).length;
    let note = $("#source-quality-note");
    if (!note) {
      note = document.createElement("div");
      note.id = "source-quality-note";
      note.className = "brief-hint";
      $(".input-footer")?.after(note);
    }
    note.textContent = words < 20 ? "Quick note: add more source detail for richer, more faithful remixes." :
      words > 1200 ? "Long source detected. Review each output carefully for coverage and context." :
      "Source length looks good for a first pass.";
  };
  source?.addEventListener("input", updateQuality);
  updateQuality();
})();

/* Workspace management: metadata, duplication and per-workspace summaries. */
(() => {
  const root=document.querySelector("#page-settings");
  const manager=()=>window.recontentWorkspaces;
  if(!root)return;
  const section=document.createElement("section");
  section.className="workspace-management";
  section.innerHTML=`
    <div class="workspace-management-head"><div><span class="eyebrow">MULTI-BRAND CONTROL</span><h2>Manage your spaces<span class="orange-dot">.</span></h2><p>Give every brand its own identity, keep projects separate, or duplicate a workspace to start a new direction.</p></div><span class="workspace-count" id="workspace-count">— WORKSPACES</span></div>
    <div class="workspace-management-grid">
      <div class="workspace-manage-card"><div class="workspace-card-title"><b>Workspace identity</b><small>Applies to the selected workspace</small></div>
        <label>WORKSPACE NAME<input id="wm-name" maxlength="60" placeholder="My workspace"></label>
        <label>DESCRIPTION<input id="wm-description" maxlength="160" placeholder="What is this workspace for?"></label>
        <div class="wm-two"><label>ICON<input id="wm-icon" maxlength="8" placeholder="✳"></label><label>ACCENT COLOR<input id="wm-color" type="color" value="#ef7048"></label></div>
        <button class="primary-btn compact" id="wm-save">Save workspace identity ↗</button>
      </div>
      <div class="workspace-manage-card"><div class="workspace-card-title"><b>Workspace snapshot</b><small>Counts from the active workspace</small></div>
        <div class="wm-stats"><div><b id="wm-projects">0</b><span>PROJECTS</span></div><div><b id="wm-outputs">0</b><span>OUTPUTS</span></div><div><b id="wm-scheduled">0</b><span>SCHEDULED</span></div><div><b id="wm-completed">0</b><span>COMPLETED</span></div></div>
        <p class="muted-note">Counts update from the current workspace data stored in this browser and cloud sync.</p>
      </div>
    </div>
    <div class="workspace-manage-card workspace-list-card"><div class="workspace-card-title"><div><b>All workspaces</b><small>Duplicate a workspace to copy its library, calendar, Brand Kit and content brief.</small></div><button class="outline-btn" id="wm-create">＋ New workspace</button></div><div class="wm-list" id="wm-list"><div class="admin-empty">Loading workspaces…</div></div></div>`;
  const settingsHeading=root.querySelector("h1");
  const settingsPanel=root.querySelector('.settings-panel');
  if(settingsPanel)root.insertBefore(section,settingsPanel);else root.append(section);
  const $=s=>section.querySelector(s);
  const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
  function render(){
    const m=manager();if(!m)return;
    const list=m.getList(),active=m.getActive(),stats=m.getStats?.()||{};
    $("#workspace-count").textContent=list.length+" / "+(window.recontentAuth?.getUser?.()?.role==="admin"?50:10)+" WORKSPACES";
    $("#wm-name").value=active?.name||"";$("#wm-description").value=active?.description||"";
    $("#wm-icon").value=active?.icon||"✳";$("#wm-color").value=active?.color||"#ef7048";
    $("#wm-projects").textContent=stats.projects||0;$("#wm-outputs").textContent=stats.outputs||0;
    $("#wm-scheduled").textContent=stats.scheduled||0;$("#wm-completed").textContent=stats.completed||0;
    $("#wm-list").innerHTML=list.map(w=>{const current=w.id===active?.id;return `<article class="wm-row"><span class="wm-row-icon" style="--wm-color:${esc(w.color||"#ef7048")}">${esc(w.icon||w.name?.slice(0,1)||"✳")}</span><div class="wm-row-main"><b>${esc(w.name)}</b><small>${esc(w.description||"No description")}${current?" · CURRENT":""}</small></div><div class="wm-row-actions">${current?'<span class="wm-current">ACTIVE</span>':`<button type="button" data-wm-switch="${esc(w.id)}">Open</button>`}<button type="button" data-wm-duplicate="${esc(w.id)}">Duplicate</button></div></article>`}).join("")||'<div class="admin-empty">No workspaces yet.</div>';
  }
  $("#wm-save").addEventListener("click",async()=>{try{const m=manager(),active=m?.getActive();if(!active)throw new Error("Workspace is still loading.");await m.updateMeta(active.id,{name:$("#wm-name").value,description:$("#wm-description").value,icon:$("#wm-icon").value,color:$("#wm-color").value});render()}catch(e){window.notify?.(e.message)}});
  $("#wm-create").addEventListener("click",async()=>{const name=prompt("Name your new workspace:","New workspace");if(name===null)return;try{await manager().create(name)}catch(e){window.notify?.(e.message)}});
  $("#wm-list").addEventListener("click",async e=>{const sw=e.target.closest("[data-wm-switch]"),du=e.target.closest("[data-wm-duplicate]");try{if(sw)await manager().switchTo(sw.dataset.wmSwitch);if(du){const source=manager().getList().find(x=>x.id===du.dataset.wmDuplicate);const name=prompt("Name the duplicated workspace:",(source?.name||"Workspace")+" copy");if(name!==null)await manager().duplicate(du.dataset.wmDuplicate,name)}}catch(err){window.notify?.(err.message)}});
  window.addEventListener("recontent:workspaces",render);
  window.addEventListener("recontent:auth",render);
  render();
})();

/* Workspace-scoped custom template library. Stored with the active cloud workspace. */
(() => {
  const key="recontent-custom-templates-v1";
  const page=document.querySelector("#page-templates");
  if(!page)return;
  const $=s=>page.querySelector(s);
  const read=()=>{try{const v=JSON.parse(localStorage.getItem(key)||"[]");return Array.isArray(v)?v:[]}catch{return[]}};
  const write=list=>{localStorage.setItem(key,JSON.stringify(list.slice(0,100)));window.dispatchEvent(new CustomEvent("recontent:templates"))};
  const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
  const panel=document.createElement("section");
  panel.className="custom-template-studio";
  panel.innerHTML=`
    <div class="custom-template-head"><div><span class="eyebrow">YOUR WORKSPACE LIBRARY</span><h2>Custom templates<span class="orange-dot">.</span></h2><p>Save reusable content structures for this brand. They stay separate from your other workspaces.</p></div><span class="custom-template-count" id="ct-count">0 / 100 SAVED</span></div>
    <div class="custom-template-builder">
      <div class="ct-field"><label for="ct-title">TEMPLATE NAME</label><input id="ct-title" maxlength="80" placeholder="e.g. Product launch sequence"></div>
      <div class="ct-field"><label for="ct-category">CATEGORY</label><input id="ct-category" maxlength="32" placeholder="e.g. PRODUCT"></div>
      <div class="ct-field ct-wide"><label for="ct-content">CONTENT STRUCTURE</label><textarea id="ct-content" rows="7" maxlength="12000" placeholder="Write your reusable structure here. Use [brackets] for details to fill in later."></textarea><small>Up to 12,000 characters. Keep placeholders clear so your team knows what to replace.</small></div>
      <div class="ct-builder-foot"><span id="ct-editing">NEW TEMPLATE</span><div><button type="button" class="outline-btn" id="ct-cancel" hidden>Cancel edit</button><button type="button" class="primary-btn compact" id="ct-save">Save template ↗</button></div></div>
    </div>
    <div class="custom-template-toolbar"><b>Saved in this workspace</b><input id="ct-search" type="search" placeholder="Search your templates…"></div>
    <div class="custom-template-grid" id="ct-grid"></div>`;
  const toolbar=page.querySelector(".template-toolbar"),grid=page.querySelector("#template-grid");
  (grid||toolbar||page).after(panel);
  let editing="";
  function render(){
    const list=read(),q=($("#ct-search").value||"").trim().toLowerCase();
    $("#ct-count").textContent=list.length+" / 100 SAVED";
    const filtered=list.filter(t=>(t.title+" "+t.category+" "+t.content).toLowerCase().includes(q));
    $("#ct-grid").innerHTML=filtered.map(t=>`<article class="custom-template-card"><div class="ct-card-top"><span>${esc(t.category||"CUSTOM")}</span><small>YOUR TEMPLATE</small></div><h3>${esc(t.title)}</h3><p>${esc(t.content.slice(0,190))}${t.content.length>190?"…":""}</p><div class="ct-card-actions"><button type="button" data-ct-use="${esc(t.id)}">Use template ↗</button><button type="button" data-ct-edit="${esc(t.id)}">Edit</button><button type="button" data-ct-delete="${esc(t.id)}">Delete</button></div></article>`).join("")||'<div class="ct-empty">No custom templates yet. Save your first reusable structure above.</div>';
  }
  function reset(){editing="";$("#ct-title").value="";$("#ct-category").value="";$("#ct-content").value="";$("#ct-editing").textContent="NEW TEMPLATE";$("#ct-save").textContent="Save template ↗";$("#ct-cancel").hidden=true}
  $("#ct-save").addEventListener("click",()=>{
    const title=$("#ct-title").value.trim(),content=$("#ct-content").value.trim(),category=$("#ct-category").value.trim().toUpperCase()||"CUSTOM";
    if(title.length<2){window.notify?.("Template name must be at least 2 characters.");return}
    if(content.length<5){window.notify?.("Add a little more content to your template.");return}
    const list=read(),id=editing||("ct_"+Date.now().toString(36)+Math.random().toString(36).slice(2,7));
    if(!editing&&list.length>=100){window.notify?.("This workspace has reached the 100-template limit.");return}
    const item={id,title,category,content,createdAt:list.find(x=>x.id===id)?.createdAt||new Date().toISOString()};
    write(editing?list.map(x=>x.id===id?item:x):[item,...list]);reset();render();window.notify?.(editing?"Template updated.":"Template saved to this workspace.");
  });
  $("#ct-cancel").addEventListener("click",reset);$("#ct-search").addEventListener("input",render);
  $("#ct-grid").addEventListener("click",e=>{
    const use=e.target.closest("[data-ct-use]"),edit=e.target.closest("[data-ct-edit]"),del=e.target.closest("[data-ct-delete]");
    const id=use?.dataset.ctUse||edit?.dataset.ctEdit||del?.dataset.ctDelete,item=read().find(x=>x.id===id);if(!item)return;
    if(use){const source=document.querySelector("#source-content");if(!source){window.notify?.("Content Studio is unavailable.");return}source.value=item.content;source.dispatchEvent(new Event("input",{bubbles:true}));document.querySelectorAll(".source-tab").forEach(x=>x.classList.toggle("active",x.dataset.source==="text"));document.querySelector('[data-page="studio"]')?.click();window.notify?.("Custom template loaded into Content Studio.");}
    if(edit){editing=item.id;$("#ct-title").value=item.title;$("#ct-category").value=item.category||"";$("#ct-content").value=item.content;$("#ct-editing").textContent="EDITING TEMPLATE";$("#ct-save").textContent="Update template ↗";$("#ct-cancel").hidden=false;$("#ct-title").focus();}
    if(del&&confirm('Delete "'+item.title+'"? This cannot be undone.')){write(read().filter(x=>x.id!==item.id));if(editing===item.id)reset();render();window.notify?.("Template deleted.");}
  });
  window.addEventListener("recontent:templates",render);window.addEventListener("recontent:workspaces",()=>{reset();render()});window.addEventListener("recontent:auth",render);render();
})();

/* Portable template packs: JSON export/import scoped to the active workspace. */
(() => {
  const page=document.querySelector("#page-templates");
  if(!page)return;
  const key="recontent-custom-templates-v1";
  const read=()=>{try{const x=JSON.parse(localStorage.getItem(key)||"[]");return Array.isArray(x)?x:[]}catch{return[]}};
  const write=list=>{localStorage.setItem(key,JSON.stringify(list.slice(0,100)));window.dispatchEvent(new CustomEvent("recontent:templates"))};
  const makeId=()=>"ct_"+Date.now().toString(36)+Math.random().toString(36).slice(2,9);
  const toolbar=page.querySelector(".custom-template-toolbar");
  if(!toolbar||page.querySelector(".ct-pack-tools"))return;
  const tools=document.createElement("div");
  tools.className="ct-pack-tools";
  tools.innerHTML=`
    <div class="ct-pack-copy"><b>Move templates between workspaces</b><small>Export a portable pack or import one here. Only custom templates are included.</small></div>
    <div class="ct-pack-actions">
      <button type="button" class="outline-btn" id="ct-export">↓ Export pack</button>
      <button type="button" class="primary-btn compact" id="ct-import">↑ Import pack</button>
      <input id="ct-pack-file" type="file" accept=".json,application/json" hidden>
    </div>
    <div class="ct-pack-mode"><label for="ct-import-mode">IMPORT MODE</label><select id="ct-import-mode"><option value="merge">Merge · keep existing templates</option><option value="replace">Replace · overwrite this workspace library</option></select><span id="ct-pack-status">JSON template packs · up to 100 templates</span></div>`;
  toolbar.after(tools);
  const $=s=>tools.querySelector(s);
  const status=m=>{$("#ct-pack-status").textContent=m};
  $("#ct-export").addEventListener("click",()=>{
    const templates=read();
    if(!templates.length){status("Nothing to export yet.");return}
    const pack={format:"recontent-template-pack",version:1,exportedAt:new Date().toISOString(),templates:templates.map(t=>({title:String(t.title||""),category:String(t.category||"CUSTOM"),content:String(t.content||""),createdAt:String(t.createdAt||"")}))};
    const blob=new Blob([JSON.stringify(pack,null,2)],{type:"application/json"});
    const url=URL.createObjectURL(blob),a=document.createElement("a");
    a.href=url;a.download="recontent-template-pack-"+new Date().toISOString().slice(0,10)+".json";
    document.body.append(a);a.click();a.remove();URL.revokeObjectURL(url);
    status(templates.length+" template(s) exported.");
    window.notify?.("Template pack exported.");
  });
  $("#ct-import").addEventListener("click",()=>$("#ct-pack-file").click());
  $("#ct-pack-file").addEventListener("change",async e=>{
    const file=e.target.files?.[0];if(!file)return;
    try{
      if(file.size>1024*1024)throw new Error("Pack is too large. Maximum file size is 1 MB.");
      const parsed=JSON.parse(await file.text());
      const source=Array.isArray(parsed)?parsed:(parsed?.format==="recontent-template-pack"&&parsed?.version===1?parsed.templates:null);
      if(!Array.isArray(source))throw new Error("This is not a supported ReContent template pack.");
      if(source.length>500)throw new Error("Pack contains too many entries (maximum 500).");
      const clean=source.map((t,i)=>{
        if(!t||typeof t!=="object"||Array.isArray(t))throw new Error("Invalid template at item "+(i+1)+".");
        const title=typeof t.title==="string"?t.title.trim():"";
        const content=typeof t.content==="string"?t.content.trim():"";
        const category=typeof t.category==="string"?t.category.trim().toUpperCase().slice(0,32):"CUSTOM";
        if(title.length<2||title.length>80||content.length<5||content.length>12000)throw new Error("Template "+(i+1)+" has an invalid name or content length.");
        return {id:makeId(),title,category:category||"CUSTOM",content,createdAt:new Date().toISOString()};
      });
      const existing=read(),replace=$("#ct-import-mode").value==="replace";
      if(replace&&!confirm("Replace all "+existing.length+" existing templates in this workspace with the imported pack?"))return;
      const base=replace?[]:existing;
      const seen=new Set(base.map(t=>(t.title+"|"+t.category+"|"+t.content).toLowerCase()));
      const unique=clean.filter(t=>{const sig=(t.title+"|"+t.category+"|"+t.content).toLowerCase();if(seen.has(sig))return false;seen.add(sig);return true});
      const capacity=Math.max(0,100-base.length),accepted=unique.slice(0,capacity);
      if(!accepted.length&&!(replace&&clean.length===0))throw new Error(replace?"The pack contains no valid templates.":"No new templates to import, or this workspace is already at its 100-template limit.");
      write([...accepted,...base].slice(0,100));
      const skipped=clean.length-accepted.length;
      status(accepted.length+" imported"+(skipped?" · "+skipped+" skipped":"")+".");
      window.notify?.(accepted.length+" template(s) imported"+(skipped?"; "+skipped+" skipped.":"."));
    }catch(err){status(err.message||"Import failed.");window.notify?.(err.message||"Template import failed.");}
    finally{e.target.value=""}
  });
})();

/* Curated template marketplace: collections, full previews, favourites and personal copies. */
(() => {
  const page=document.querySelector("#page-templates"),grid=page?.querySelector("#template-grid");
  if(!page||!grid||page.querySelector(".marketplace-toolbar"))return;
  const activeWorkspace=()=>{try{return JSON.parse(localStorage.getItem("recontent-active-workspace-v1")||'{"id":"default"}').id||"default"}catch{return"default"}};
  const favKey=()=>"recontent-template-favourites-v1:"+activeWorkspace();
  const usageKey=()=>"recontent-template-usage-v1:"+activeWorkspace();
  const getUsage=()=>{try{const x=JSON.parse(localStorage.getItem(usageKey())||"{}");return x&&typeof x==="object"&&!Array.isArray(x)?x:{}}catch{return{}}};
  const recordUse=id=>{const u=getUsage();u[id]={count:Math.min(9999,(Number(u[id]?.count)||0)+1),lastUsed:new Date().toISOString()};localStorage.setItem(usageKey(),JSON.stringify(u))};
  const readContext=()=>{const read=k=>{try{return JSON.parse(localStorage.getItem(k)||"{}")}catch{return{}}};const b=read("recontent-brand-v1"),brief=read("recontent-current-brief-v1");return [b.name,b.audience,b.voice,b.words,b.avoid,brief.audience,brief.goal,brief.topic,brief.purpose,brief.notes,brief.description].filter(x=>typeof x==="string").join(" ").toLowerCase()};
  const renderRecommendations=()=>{
    const root=bar.querySelector("#mp-recommend-list");if(!root)return;root.innerHTML="";
    const context=readContext(),tokens=context.match(/[a-z0-9]{3,}/g)||[],stop=new Set(["the","and","for","with","from","that","this","your","you","are","our","will","can","how","what","who","into","about","use","more","best","brand","content"]);
    const words=tokens.filter(x=>!stop.has(x)),ranked=cards().map(card=>{const id=card.querySelector("[data-template]")?.dataset.template;const txt=(card.textContent+" "+templateContent(id)).toLowerCase();let score=0;words.forEach(word=>{if(txt.includes(word))score+=word.length>6?3:1});if(/launch|product|business|sales|customer/.test(context)&&["product","launch","case","faq","comparison"].includes(id))score+=2;if(/creator|video|social|audience|community/.test(context)&&["story","tips","behind","community","challenge"].includes(id))score+=2;if(/personal|founder|thought leadership|expert/.test(context)&&["opinion","story","newsletter","quote"].includes(id))score+=2;return{card,id,score}}).sort((a,b)=>b.score-a.score);
    ranked.slice(0,3).forEach(({card,id,score},i)=>{const item=document.createElement("article");item.className="mp-recommend-card";const tag=document.createElement("span");tag.textContent=i===0?"BEST MATCH":"GOOD FIT";const title=document.createElement("b");title.textContent=card.querySelector("h3")?.textContent||id;const desc=document.createElement("small");desc.textContent=score?"Matches keywords in your saved brand and content brief":"A useful starting point for your next post";const btn=document.createElement("button");btn.type="button";btn.textContent="Preview ↗";btn.addEventListener("click",()=>openPreview(id));item.append(tag,title,desc,btn);root.append(item)});
  };
  const getFavs=()=>{try{const a=JSON.parse(localStorage.getItem(favKey())||"[]");return Array.isArray(a)?a:[]}catch{return[]}};
  const setFavs=a=>localStorage.setItem(favKey(),JSON.stringify(a));
  const collections={
    "All templates":null,
    "Creator":new Set(["story","teach","opinion","tips","behind","quote","challenge"]),
    "Business":new Set(["product","launch","case","faq","comparison","job","event"]),
    "Marketing":new Set(["product","tips","launch","case","myth","checklist","comparison","mistakes","prediction"]),
    "Personal brand":new Set(["story","opinion","community","behind","quote","newsletter"])
  };
  const bar=document.createElement("div");
  bar.className="marketplace-toolbar";
  bar.innerHTML='<div class="marketplace-heading"><div><span class="eyebrow">CURATED STARTING POINTS</span><h2>Template marketplace<span class="orange-dot">.</span></h2><p>Explore by goal, inspect the complete framework, and save your own copy.</p></div><span class="marketplace-total" id="mp-total">21 TEMPLATES</span></div><section class="mp-recommendations"><div class="mp-recommend-head"><div><span class="eyebrow">BASED ON YOUR BRAND + BRIEF</span><h3>Smart picks<span class="orange-dot">.</span></h3></div><span class="mp-recommend-note">Personalised on this device</span></div><div class="mp-recommend-list" id="mp-recommend-list"></div></section><div class="mp-collections" role="group" aria-label="Template collections"></div><div class="marketplace-tabs"><button type="button" class="active" data-mp-tab="all">Explore all</button><button type="button" data-mp-tab="trending">↗ Trending <span id="mp-trending-count">0</span></button><button type="button" data-mp-tab="favourites">♥ Favourites <span id="mp-fav-count">0</span></button></div>';
  const toolbar=page.querySelector(".template-toolbar");toolbar?.before(bar);
  const collectionRoot=bar.querySelector(".mp-collections");
  Object.keys(collections).forEach((name,i)=>{const b=document.createElement("button");b.type="button";b.className="mp-collection"+(i===0?" active":"");b.dataset.mpCollection=name;b.textContent=name;collectionRoot.append(b)});
  const modal=document.createElement("div");modal.className="mp-modal";modal.hidden=true;
  modal.innerHTML='<div class="mp-backdrop" data-mp-close></div><section class="mp-dialog mp-dialog-wide" role="dialog" aria-modal="true" aria-labelledby="mp-title"><button type="button" class="mp-close" data-mp-close aria-label="Close preview">×</button><span class="eyebrow" id="mp-category">TEMPLATE PREVIEW</span><h2 id="mp-title">Template</h2><p id="mp-description"></p><div class="mp-full-preview"><div class="mp-preview-label">FULL TEMPLATE STRUCTURE</div><pre id="mp-content"></pre></div><div class="mp-dialog-actions"><button type="button" class="outline-btn" id="mp-favourite">♡ Save to favourites</button><button type="button" class="outline-btn" id="mp-duplicate">＋ Add to My Templates</button><button type="button" class="primary-btn compact" id="mp-use">Use this template ↗</button></div></section></div>';
  page.append(modal);
  let activeTab="all",activeCollection="All templates",selected=null;
  const cards=()=>[...grid.querySelectorAll(".template-card")];
  const templateContent=id=>String(window.recontentTemplateText?.[id]||"");
  const update=()=>{
    const favs=getFavs();
    cards().forEach(card=>{
      const id=card.querySelector("[data-template]")?.dataset.template;if(!id)return;
      const favourite=favs.includes(id);let actions=card.querySelector(".mp-card-actions");
      if(!actions){actions=document.createElement("div");actions.className="mp-card-actions";card.append(actions)}
      actions.innerHTML='<button type="button" class="mp-preview-btn" data-mp-preview="'+id+'">Full preview ↗</button><button type="button" class="mp-star '+(favourite?"is-favourite":"")+'" data-mp-fav="'+id+'" aria-label="'+(favourite?"Remove from favourites":"Add to favourites")+'" title="'+(favourite?"Remove from favourites":"Add to favourites")+'">'+(favourite?"♥":"♡")+'</button>';
      card.querySelector("[data-template]")?.classList.add("mp-use-original");
    });
    const count=bar.querySelector("#mp-fav-count");if(count)count.textContent=favs.length;const trending=bar.querySelector("#mp-trending-count");if(trending)trending.textContent=Object.values(getUsage()).filter(x=>(Number(x?.count)||0)>0).length;
    const total=bar.querySelector("#mp-total");if(total)total.textContent=cards().length+" TEMPLATES";
    renderRecommendations();
    applyFilters();
  };
  const applyFilters=()=>{
    const q=(page.querySelector("#template-search")?.value||"").trim().toLowerCase(),cat=page.querySelector("#template-category")?.value||"all";
    const allowed=collections[activeCollection];let visible=0;
    cards().forEach(card=>{
      const id=card.querySelector("[data-template]")?.dataset.template;
      const usage=getUsage();const matchTab=activeTab==="all"||(activeTab==="favourites"&&getFavs().includes(id))||(activeTab==="trending"&&(Number(usage[id]?.count)||0)>0);
      const matchSearch=!q||card.dataset.title?.includes(q)||card.dataset.category?.toLowerCase().includes(q)||card.textContent.toLowerCase().includes(q);
      const matchCat=cat==="all"||card.dataset.category===cat;
      const matchCollection=!allowed||allowed.has(id);
      card.hidden=!(matchTab&&matchSearch&&matchCat&&matchCollection);if(!card.hidden)visible++;
    });
    bar.querySelectorAll("[data-mp-tab]").forEach(b=>b.classList.toggle("active",b.dataset.mpTab===activeTab));
    collectionRoot.querySelectorAll("[data-mp-collection]").forEach(b=>b.classList.toggle("active",b.dataset.mpCollection===activeCollection));
    const result=page.querySelector("#template-result-count");if(result)result.textContent=visible+" templates";
  };
  const openPreview=id=>{
    const card=cards().find(c=>c.querySelector("[data-template]")?.dataset.template===id);if(!card)return;
    selected=id;modal.querySelector("#mp-title").textContent=card.querySelector("h3")?.textContent||"Template";
    modal.querySelector("#mp-category").textContent=card.dataset.category||"TEMPLATE";
    modal.querySelector("#mp-description").textContent=card.querySelector("p")?.textContent||"A ready-made content framework.";
    modal.querySelector("#mp-content").textContent=templateContent(id)||"Full structure is unavailable for this template.";
    const fav=getFavs().includes(id),btn=modal.querySelector("#mp-favourite");
    btn.textContent=fav?"♥ Saved to favourites":"♡ Save to favourites";btn.classList.toggle("is-favourite",fav);
    modal.querySelector("#mp-duplicate").disabled=!templateContent(id);
    modal.hidden=false;document.body.classList.add("mp-modal-open");
  };
  const close=()=>{modal.hidden=true;document.body.classList.remove("mp-modal-open");selected=null};
  bar.addEventListener("click",e=>{
    const tab=e.target.closest("[data-mp-tab]");if(tab){activeTab=tab.dataset.mpTab;applyFilters();return}
    const collection=e.target.closest("[data-mp-collection]");if(collection){activeCollection=collection.dataset.mpCollection;applyFilters()}
  });
  grid.addEventListener("click",e=>{
    const preview=e.target.closest("[data-mp-preview]"),star=e.target.closest("[data-mp-fav]");
    if(preview){openPreview(preview.dataset.mpPreview);return}
    if(star){const id=star.dataset.mpFav,favs=getFavs();setFavs(favs.includes(id)?favs.filter(x=>x!==id):[...favs,id]);update()}
  });
  modal.addEventListener("click",e=>{if(e.target.closest("[data-mp-close]"))close()});
  modal.querySelector("#mp-favourite").addEventListener("click",()=>{
    if(!selected)return;const favs=getFavs(),next=favs.includes(selected)?favs.filter(x=>x!==selected):[...favs,selected];setFavs(next);update();
    const saved=next.includes(selected),btn=modal.querySelector("#mp-favourite");btn.textContent=saved?"♥ Saved to favourites":"♡ Save to favourites";btn.classList.toggle("is-favourite",saved);
  });
  modal.querySelector("#mp-duplicate").addEventListener("click",()=>{
    if(!selected)return;const content=templateContent(selected);if(!content)return;
    const key="recontent-custom-templates-v1";let list=[];try{const x=JSON.parse(localStorage.getItem(key)||"[]");if(Array.isArray(x))list=x}catch{}
    if(list.length>=100){window.notify?.("Your custom template library is full (100 templates).");return}
    const card=cards().find(c=>c.querySelector("[data-template]")?.dataset.template===selected);
    const title=(card?.querySelector("h3")?.textContent||selected).trim();
    list.unshift({id:"ct_"+Date.now().toString(36)+Math.random().toString(36).slice(2,9),title,category:(card?.dataset.category||"CUSTOM").toUpperCase().slice(0,32),content,createdAt:new Date().toISOString()});
    localStorage.setItem(key,JSON.stringify(list));window.dispatchEvent(new CustomEvent("recontent:templates"));
    modal.querySelector("#mp-duplicate").textContent="✓ Added to My Templates";window.notify?.("Template copied to My Templates.");
  });
  modal.querySelector("#mp-use").addEventListener("click",()=>{if(!selected)return;const id=selected;recordUse(id);const btn=grid.querySelector('[data-template="'+id+'"]');close();update();btn?.click()});
  document.addEventListener("keydown",e=>{if(e.key==="Escape"&&!modal.hidden)close()});
  page.querySelector("#template-search")?.addEventListener("input",applyFilters);
  page.querySelector("#template-category")?.addEventListener("change",applyFilters);
  window.addEventListener("recontent:workspaces",()=>{activeTab="all";activeCollection="All templates";update()});
  window.addEventListener("storage",e=>{if(["recontent-brand-v1","recontent-current-brief-v1"].includes(e.key))renderRecommendations()});
  update();
})();
/* Content intelligence: library analysis, strategic gaps and AI-assisted planning. */
(() => {
  const page=document.querySelector("#page-analytics");
  if(!page||page.querySelector(".content-intelligence"))return;
  const read=(key,fallback)=>{try{const x=JSON.parse(localStorage.getItem(key)||"null");return x??fallback}catch{return fallback}};
  const formats={thread:"X / Twitter thread",carousel:"Instagram carousel",linkedin:"LinkedIn post",reel:"Reels / TikTok",newsletter:"Newsletter",caption:"Social captions"};
  const section=document.createElement("section");
  section.className="content-intelligence";
  section.innerHTML=`
    <div class="ci-heading"><div><span class="eyebrow">BEYOND THE NUMBERS</span><h2>Content intelligence<span class="orange-dot">.</span></h2><p>Find patterns in your saved work, spot missing formats, and turn the next gap into a plan.</p></div><span class="ci-live">● BASED ON YOUR WORKSPACE</span></div>
    <div class="ci-overview" id="ci-overview"></div>
    <div class="ci-columns">
      <article class="ci-panel"><div class="ci-panel-head"><div><span class="eyebrow">WHAT YOU TALK ABOUT</span><h3>Recurring themes</h3></div><span class="ci-panel-index">01</span></div><div id="ci-themes" class="ci-theme-list"></div></article>
      <article class="ci-panel"><div class="ci-panel-head"><div><span class="eyebrow">WHAT'S UNDERUSED</span><h3>Format opportunities</h3></div><span class="ci-panel-index">02</span></div><div id="ci-gaps" class="ci-gap-list"></div></article>
    </div>
    <article class="ci-panel ci-strategy"><div class="ci-panel-head"><div><span class="eyebrow">YOUR NEXT MOVE</span><h3>Strategy recommendations</h3></div><span class="ci-panel-index">03</span></div><div id="ci-recommendations" class="ci-recommendations"></div></article>
    <article class="ci-ai-panel"><div class="ci-ai-copy"><span class="eyebrow">POWERED BY RECONTENT AI</span><h3>Build your next content strategy<span class="orange-dot">.</span></h3><p>Use your Brand Kit, current brief, saved topics, and format gaps to generate a tailored content plan.</p><small>Uses one AI generation from your available daily allowance.</small></div><div class="ci-ai-controls"><select id="ci-ai-format" aria-label="Strategy output format"><option value="thread">X / Twitter thread</option><option value="linkedin">LinkedIn post</option><option value="carousel">Instagram carousel</option><option value="newsletter">Newsletter</option></select><button type="button" class="primary-btn compact" id="ci-generate">Generate strategy ↗</button></div><div class="ci-ai-result" id="ci-ai-result" hidden></div></article>
    <p class="ci-disclaimer">Insights use your saved projects and publishing calendar. ReContent does not have access to social-platform reach, clicks, or engagement unless you connect those data sources in a future release.</p>
  `;
  const existing=page.querySelector(".insight-panel");
  if(existing)existing.after(section);else page.append(section);
  const $=s=>section.querySelector(s);
  const esc=s=>String(s??"").replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
  const tokenize=text=>(String(text||"").toLowerCase().match(/[a-z][a-z0-9'-]{3,}/g)||[]);
  const stop=new Set("about after again also been being between content create could every from have into just like more most other over should some than that their there these they this through time what when where which while with your yours ideas post posts brand audience".split(" "));
  function analyze(){
    const library=read("recontent-library-v1",[]).filter(x=>x&&typeof x==="object");
    const schedule=read("recontent-calendar-v1",[]).filter(x=>x&&typeof x==="object");
    const brand=read("recontent-brand-v1",{}),brief=read("recontent-current-brief-v1",{});
    const counts={};const words={};let outputCount=0;
    library.forEach(project=>{
      (Array.isArray(project.formats)?project.formats:[]).forEach(f=>counts[f]=(counts[f]||0)+1);
      const outputs=Array.isArray(project.outputs)?project.outputs:[];
      outputCount+=outputs.length;
      const corpus=[project.title,project.source,...outputs.map(o=>o?.text)].filter(Boolean).join(" ");
      tokenize(corpus).forEach(word=>{if(!stop.has(word))words[word]=(words[word]||0)+1});
    });
    const topWords=Object.entries(words).filter(([,n])=>n>1).sort((a,b)=>b[1]-a[1]).slice(0,6);
    const used=Object.entries(counts).sort((a,b)=>b[1]-a[1]);
    const missing=Object.keys(formats).map(f=>({format:f,count:counts[f]||0})).sort((a,b)=>a.count-b.count);
    const done=schedule.filter(x=>x.done).length,upcoming=schedule.filter(x=>!x.done&&String(x.date)>=new Date().toISOString().slice(0,10)).length;
    const planned=schedule.filter(x=>!x.done);
    const dateCounts={};planned.forEach(x=>dateCounts[x.date]=(dateCounts[x.date]||0)+1);
    const busyDays=Object.values(dateCounts).filter(n=>n>1).length;
    return {library,schedule,brand,brief,counts,outputCount,topWords,used,missing,done,upcoming,planned,busyDays};
  }
  function render(){
    const d=analyze(),totalFormats=Object.values(d.counts).reduce((a,b)=>a+b,0);
    $("#ci-overview").innerHTML=[
      ["SAVED PROJECTS",d.library.length,"Across your current library"],
      ["GENERATED OUTPUTS",d.outputCount,"Saved content variations"],
      ["PUBLISHED / DONE",d.done,"Marked complete in calendar"],
      ["UPCOMING POSTS",d.upcoming,"Still on your schedule"]
    ].map(x=>'<div class="ci-stat"><span>'+x[0]+'</span><b>'+x[1]+'</b><small>'+x[2]+'</small></div>').join("");
    $("#ci-themes").innerHTML=d.topWords.length?d.topWords.map(([word,n],i)=>'<div class="ci-theme"><span class="ci-theme-rank">0'+(i+1)+'</span><b>'+esc(word)+'</b><span>'+n+' mentions</span></div>').join(""):'<div class="ci-empty">Save a few projects with source text or generated outputs. ReContent will surface recurring themes here.</div>';
    $("#ci-gaps").innerHTML=d.missing.map(x=>'<div class="ci-gap"><div><b>'+esc(formats[x.format])+'</b><span>'+(x.count?"Used "+x.count+" time(s)":"Not used yet")+'</span></div><span class="ci-gap-state '+(x.count?"":"opportunity")+'">'+(x.count?"IN ROTATION":"OPPORTUNITY")+'</span></div>').join("");
    const rec=[];
    if(!d.library.length)rec.push(["Start with one source","Save your first remix to establish a baseline for topic and format analysis."]);
    else if(d.library.length<5)rec.push(["Build a stronger baseline","Save a few more projects. A larger library makes recurring themes more useful."]);
    if(d.missing[0]?.count===0)rec.push(["Experiment with "+formats[d.missing[0].format],"This format has no saved projects yet. Repurpose one of your strongest sources into it."]);
    if(d.upcoming===0)rec.push(["Give your ideas a publishing date","Your calendar has no upcoming unfinished posts. Schedule one idea to create a consistent rhythm."]);
    if(d.busyDays>0)rec.push(["Spread out your publishing","You have multiple unfinished posts on the same date. Distribute them to avoid a crowded schedule."]);
    if(d.topWords.length)rec.push(["Build a mini-series around "+d.topWords[0][0],"This is your most repeated theme in saved content. Explore a deeper angle, case study, and practical checklist."]);
    if(!rec.length)rec.push(["Keep testing formats","Your library has a useful spread. Compare how different formats support the same topic."]);
    $("#ci-recommendations").innerHTML=rec.slice(0,4).map((x,i)=>'<div class="ci-rec"><span>0'+(i+1)+'</span><div><b>'+esc(x[0])+'</b><p>'+esc(x[1])+'</p></div></div>').join("");
  }
  $("#ci-generate").addEventListener("click",async()=>{
    const d=analyze(),format=$("#ci-ai-format").value,button=$("#ci-generate"),result=$("#ci-ai-result");
    if(!d.library.length&&!d.brand.name&&!d.brief.audience){window.notify?.("Save a project or complete your Brand Kit / Content Brief first.");return}
    const top=d.topWords.slice(0,6).map(x=>x[0]).join(", ")||"not enough recurring themes yet";
    const underused=d.missing.slice(0,3).map(x=>formats[x.format]).join(", ");
    const source="Create a practical next-step content strategy for this creator or brand.\n\nBrand: "+(d.brand.name||"Not specified")+"\nAudience: "+(d.brand.audience||d.brief.audience||"Not specified")+"\nBrand voice: "+(d.brand.voice||"Not specified")+"\nBusiness goal: "+(d.brief.goal||"Grow a useful audience")+"\nRecurring themes from saved content: "+top+"\nUnderused formats: "+underused+"\nSaved projects: "+d.library.length+"; saved outputs: "+d.outputCount+"; upcoming posts: "+d.upcoming+".\n\nProvide a concise but actionable 7-day content strategy with distinct ideas, recommended angles, hooks, and calls to action. Do not invent performance metrics or claim that a post will go viral. Avoid repeating the same idea.";
    button.disabled=true;button.textContent="Building your strategy…";
    result.hidden=false;result.textContent="ReContent AI is analysing your workspace context…";
    try{
      const response=await fetch("/api/content/generate",{method:"POST",credentials:"same-origin",headers:{"content-type":"application/json"},body:JSON.stringify({source,formats:[format],tone:d.brand.voice||"Conversational",language:"English",brief:d.brief,brand:d.brand})});
      const data=await response.json().catch(()=>({}));
      if(!response.ok)throw new Error(data.error||"Strategy generation failed.");
      const output=Array.isArray(data.outputs)?data.outputs[0]:null;
      if(!output||typeof output.text!=="string")throw new Error("The AI response did not include a strategy.");
      result.innerHTML="";
      const heading=document.createElement("div");heading.className="ci-result-head";heading.innerHTML="<b>Your 7-day strategy</b><span>"+esc(formats[format])+"</span>";
      const body=document.createElement("pre");body.textContent=output.text;
      const actions=document.createElement("div");actions.className="ci-result-actions";
      const copy=document.createElement("button");copy.type="button";copy.className="outline-btn";copy.textContent="Copy strategy";
      copy.addEventListener("click",async()=>{try{await navigator.clipboard.writeText(output.text);window.notify?.("Strategy copied.")}catch{window.notify?.("Clipboard unavailable in this browser.")}});
      const studio=document.createElement("button");studio.type="button";studio.className="primary-btn compact";studio.textContent="Open in Content Studio ↗";
      studio.addEventListener("click",()=>{const sourceBox=document.querySelector("#source-content");if(sourceBox){sourceBox.value=output.text;sourceBox.dispatchEvent(new Event("input",{bubbles:true}));document.querySelector('[data-page="studio"]')?.click()}});
      actions.append(copy,studio);result.append(heading,body,actions);
      window.notify?.("Your content strategy is ready.");
    }catch(error){result.textContent=error.message||"Could not generate a strategy."}
    finally{button.disabled=false;button.textContent="Generate strategy ↗"}
  });
  window.addEventListener("recontent:workspaces",render);
  window.addEventListener("recontent:auth",render);
  window.addEventListener("recontent:templates",render);
  window.addEventListener("recontent:library-updated",render);
  window.addEventListener("recontent:calendar-updated",render);
  document.addEventListener("input",e=>{if(e.target.matches("#brief-audience,#brief-goal,#brief-cta,#brief-keypoints,#brief-notes"))render()});
  document.querySelector("#save-brand")?.addEventListener("click",()=>setTimeout(render,0));
  document.addEventListener("click",e=>{if(e.target.closest('[data-page="analytics"]'))setTimeout(render,0)});
  render();
})();

/* Campaign Manager */
(()=>{const page=document.querySelector("#page-calendar");if(!page||page.querySelector(".campaign-manager"))return;
const key="recontent-campaigns-v1",read=()=>{try{const x=JSON.parse(localStorage.getItem(key)||"[]");return Array.isArray(x)?x:[]}catch{return []}},calendarLinked=id=>{try{return JSON.parse(localStorage.getItem("recontent-calendar-v1")||"[]").some(x=>x.id==="campcal_"+id)}catch{return false}},save=x=>{localStorage.setItem(key,JSON.stringify(x));window.dispatchEvent(new CustomEvent("recontent:campaigns-updated"))},esc=s=>String(s??"").replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
const formats=[["thread","X thread"],["carousel","Instagram carousel"],["linkedin","LinkedIn"],["reel","Reels / TikTok"],["newsletter","Newsletter"],["caption","Social captions"]],channels=["Instagram","TikTok","YouTube","X","LinkedIn","Newsletter","Other"];
const root=document.createElement("section");root.className="campaign-manager";root.innerHTML='<div class="cm-head"><div><span class="eyebrow">FROM IDEA TO DELIVERY</span><h2>Campaign manager<span>.</span></h2><p>Group content by campaign, channels, dates, and production status.</p></div><span id="cm-count"></span></div><form class="cm-create"><div class="cm-fields"><label>CAMPAIGN NAME<input name="title" maxlength="100" placeholder="November product launch" required></label><label>CAMPAIGN GOAL<input name="goal" maxlength="500" placeholder="What should this campaign achieve?"></label><label>START DATE<input name="start" type="date"></label><label>END DATE<input name="end" type="date"></label><label class="cm-platforms">CHANNELS<select name="channels" multiple size="3">'+channels.map(x=>'<option>'+x+'</option>').join("")+'</select></label></div><button class="primary-btn compact" type="submit">＋ Create campaign</button></form><div class="cm-toolbar"><b>Your campaigns</b><span id="cm-summary"></span></div><div class="cm-list"></div>';
const summary=page.querySelector(".calendar-summary");summary?summary.before(root):page.prepend(root);const $=s=>root.querySelector(s);
function render(){const list=read(),items=list.flatMap(c=>c.items||[]);$("#cm-count").textContent=list.length+" CAMPAIGNS";$("#cm-summary").textContent=items.length+" content items · "+items.filter(x=>x.status==="published").length+" published";
$(".cm-list").innerHTML=list.length?list.map(c=>{const a=c.items||[],done=a.filter(x=>x.status==="published").length,p=a.length?Math.round(done/a.length*100):0;return '<article class="cm-card"><div class="cm-card-top"><div><span class="eyebrow">'+esc((c.platforms||[]).join(" · ")||"MULTI-CHANNEL")+'</span><h3>'+esc(c.title)+'</h3><p>'+esc(c.goal||"No campaign goal added.")+'</p><small>'+esc(c.startDate||"—")+' → '+esc(c.endDate||"—")+'</small></div><button type="button" data-cm-delete="'+esc(c.id)+'" class="cm-delete">×</button></div><div class="cm-progress"><div><span>'+done+' / '+a.length+' PUBLISHED</span><b>'+p+'%</b></div><i><span style="width:'+p+'%"></span></i></div><form class="cm-add" data-cm-add="'+esc(c.id)+'"><input name="title" placeholder="Add content item…" maxlength="120" required><select name="platform">'+channels.map(x=>'<option>'+x+'</option>').join("")+'</select><select name="format">'+formats.map(x=>'<option value="'+x[0]+'">'+x[1]+'</option>').join("")+'</select><input name="date" type="date"><button class="outline-btn">＋ Add</button></form><div class="cm-items">'+(a.length?a.map(x=>'<div class="cm-item"><div><b>'+esc(x.title)+'</b><small>'+esc(x.platform)+' · '+esc(formats.find(f=>f[0]===x.format)?.[1]||x.format)+' · '+esc(x.date||"Unscheduled")+'</small></div><div class="cm-item-actions"><button type="button" data-cm-generate="'+esc(c.id)+'" data-item="'+esc(x.id)+'">✳ '+(x.libraryId?"Regenerate draft":"Generate draft")+'</button>'+(x.libraryId?'<button type="button" data-cm-open="'+esc(x.libraryId)+'">Open draft</button>':'')+'</div>'+(x.date?'<button type="button" data-cm-calendar="'+esc(c.id)+'" data-item="'+esc(x.id)+'" '+(calendarLinked(x.id)?"disabled":"")+'>'+(calendarLinked(x.id)?"✓ In calendar":"＋ Calendar")+'</button>':'')+'<select data-cm-status="'+esc(c.id)+'" data-item="'+esc(x.id)+'"><option value="idea" '+(x.status==="idea"?"selected":"")+'>Idea</option><option value="drafting" '+(x.status==="drafting"?"selected":"")+'>Drafting</option><option value="ready" '+(x.status==="ready"?"selected":"")+'>Ready</option><option value="published" '+(x.status==="published"?"selected":"")+'>Published</option></select><button type="button" data-cm-remove="'+esc(c.id)+'" data-item="'+esc(x.id)+'">×</button></div>').join(""):'<p class="cm-empty">Add the first content item to this campaign.</p>')+'</div></article>'}).join(""):'<div class="cm-empty">No campaigns yet. Create one to organise a launch, series, or content initiative.</div>'}
$(".cm-create").addEventListener("submit",e=>{e.preventDefault();const f=new FormData(e.currentTarget),list=read(),title=String(f.get("title")||"").trim();if(list.length>=100){window.notify?.("Maximum 100 campaigns per workspace.");return}list.unshift({id:"camp_"+Date.now().toString(36),title,goal:String(f.get("goal")||""),platforms:Array.from(e.currentTarget.elements.channels.selectedOptions).map(o=>o.value),startDate:String(f.get("start")||""),endDate:String(f.get("end")||""),items:[]});save(list);e.currentTarget.reset();render()});
$(".cm-list").addEventListener("submit",e=>{const form=e.target.closest("[data-cm-add]");if(!form)return;e.preventDefault();const f=new FormData(form),list=read(),c=list.find(x=>x.id===form.dataset.cmAdd);if(!c)return;c.items=c.items||[];if(c.items.length>=100)return;c.items.push({id:"asset_"+Date.now().toString(36),title:String(f.get("title")||"").trim(),platform:String(f.get("platform")),format:String(f.get("format")),date:String(f.get("date")||""),status:"idea"});save(list);render()});
$(".cm-list").addEventListener("change",e=>{const s=e.target.closest("[data-cm-status]");if(!s)return;const list=read(),c=list.find(x=>x.id===s.dataset.cmStatus),item=c?.items?.find(x=>x.id===s.dataset.item);if(item){item.status=s.value;const scheduleKey="recontent-calendar-v1";let schedule=[];try{const raw=JSON.parse(localStorage.getItem(scheduleKey)||"[]");if(Array.isArray(raw))schedule=raw}catch{}const calendarId="campcal_"+item.id;const linked=schedule.find(x=>x.id===calendarId);if(linked)linked.done=s.value==="published";localStorage.setItem(scheduleKey,JSON.stringify(schedule));save(list);render()}});

async function generateCampaignDraft(campaignId,itemId,button){
  const list=read(),campaign=list.find(c=>c.id===campaignId),item=campaign?.items?.find(x=>x.id===itemId);
  if(!campaign||!item)return;
  if(!window.recontentAuth?.getUser?.()){window.notify?.("Sign in to generate campaign drafts.");window.recontentAuth?.open?.("login");return}
  if(button?.disabled)return;
  if(button){button.disabled=true;button.textContent="Creating draft…"}
  const readLocal=(key,fallback)=>{try{return JSON.parse(localStorage.getItem(key)||"null")??fallback}catch{return fallback}};
  const formatLabel=formats.find(x=>x[0]===item.format)?.[1]||item.format;
  const source=[
    "Create one publish-ready content draft for this campaign item.",
    "CAMPAIGN: "+campaign.title,
    "CAMPAIGN GOAL: "+(campaign.goal||"Not specified"),
    "CONTENT ITEM: "+item.title,
    "CHANNEL: "+(item.platform||"Not specified"),
    "OUTPUT FORMAT: "+formatLabel,
    "PLANNED DATE: "+(item.date||"Not scheduled"),
    "Write specifically for the named channel and format. Keep it focused on this item, use the campaign goal as direction, and do not invent product facts, statistics, testimonials, or offers. Use clear placeholders such as [insert detail] when essential information is missing."
  ].join("\n\n");
  try{
    const brief=readLocal("recontent-current-brief-v1",{});
    const brand=readLocal("recontent-brand-v1",{});
    const response=await fetch("/api/content/generate",{method:"POST",credentials:"same-origin",headers:{"content-type":"application/json"},body:JSON.stringify({source,formats:[item.format],tone:document.querySelector("#tone")?.value||"Conversational",language:document.querySelector("#language")?.value||"English",brief:{...brief,goal:campaign.goal||brief.goal},brand})});
    const data=await response.json().catch(()=>({}));
    if(!response.ok)throw new Error(data.error||"Campaign draft generation failed.");
    const project={id:item.libraryId||("campdraft-"+Date.now().toString(36)),title:(item.title+" · "+campaign.title).slice(0,58),type:"CAMPAIGN",formats:[item.format],date:"Just now",source,outputs:data.outputs,saved:true,campaignId:campaign.id,campaignItemId:item.id};
    if(typeof window.recontentSaveCampaignDraft==="function")window.recontentSaveCampaignDraft(project);
    else{
      const key="recontent-library-v1";let library=[];try{const v=JSON.parse(localStorage.getItem(key)||"[]");if(Array.isArray(v))library=v}catch{}
      const index=library.findIndex(x=>x.id===project.id);if(index>=0)library[index]=project;else library.unshift(project);
      localStorage.setItem(key,JSON.stringify(library));window.dispatchEvent(new CustomEvent("recontent:library-updated"));
    }
    item.libraryId=project.id;item.status="drafting";item.generatedAt=new Date().toISOString();save(list);render();
    window.trackActivity?.("campaign.draft_generated",{campaignId:campaign.id,itemId:item.id,format:item.format});
    window.notify?.("Draft created and saved to My Library · "+(data.usage?.remaining??"—")+" AI generations left today.");
  }catch(error){window.notify?.(error.message||"Could not create campaign draft.");if(button){button.disabled=false;button.textContent=item.libraryId?"✳ Regenerate draft":"✳ Generate draft"}}
}

$(".cm-list").addEventListener("click",e=>{const cal=e.target.closest("[data-cm-calendar]");if(cal){const list=read(),campaign=list.find(x=>x.id===cal.dataset.cmCalendar),item=campaign?.items?.find(x=>x.id===cal.dataset.item);if(!item?.date){window.notify?.("Add a planned date first.");return}const key="recontent-calendar-v1";let schedule=[];try{const raw=JSON.parse(localStorage.getItem(key)||"[]");if(Array.isArray(raw))schedule=raw}catch{}const id="campcal_"+item.id;const entry={id,title:item.title,date:item.date,format:item.format,done:item.status==="published",campaign:campaign.title};const existing=schedule.findIndex(x=>x.id===id);if(existing>=0)schedule[existing]=entry;else schedule.push(entry);localStorage.setItem(key,JSON.stringify(schedule));window.notify?.("Added to Content Calendar.");cal.textContent="✓ In calendar";cal.disabled=true;return}const g=e.target.closest("[data-cm-generate]"),o=e.target.closest("[data-cm-open]");if(g){generateCampaignDraft(g.dataset.cmGenerate,g.dataset.item,g);return}if(o){window.recontentOpenLibraryProject?.(o.dataset.cmOpen);return}const d=e.target.closest("[data-cm-delete]"),r=e.target.closest("[data-cm-remove]");if(d){const c=read().find(x=>x.id===d.dataset.cmDelete);if(c&&confirm('Delete "'+c.title+'" and its content items?'))save(read().filter(x=>x.id!==c.id))}else if(r){const list=read(),c=list.find(x=>x.id===r.dataset.cmRemove);if(c)c.items=c.items.filter(x=>x.id!==r.dataset.item);save(list)}render()});
window.addEventListener("recontent:workspaces",render);window.addEventListener("recontent:auth",render);render()})();


/* Publishing Command Center — workspace-local publishing handoff, not direct social posting. */
(()=>{const page=document.querySelector("#page-calendar");if(!page||page.querySelector(".publishing-center"))return;
const section=document.createElement("section");section.className="publishing-center";section.innerHTML='<div class="pc-heading"><div><span class="eyebrow">YOUR NEXT PUBLISHING MOVES</span><h2>Publishing command<br><em>center.</em></h2><p>One clear view of what is ready, what is coming up, and what still needs a draft.</p></div><span class="pc-manual-note">MANUAL PUBLISHING WORKFLOW</span></div><div class="pc-stats" id="pc-stats"></div><div class="pc-columns"><article class="pc-panel"><div class="pc-panel-head"><div><span class="eyebrow">READY TO GO</span><h3>Publishing queue</h3></div><span id="pc-ready-count"></span></div><div id="pc-ready"></div></article><article class="pc-panel"><div class="pc-panel-head"><div><span class="eyebrow">UP NEXT</span><h3>Upcoming schedule</h3></div><span id="pc-upcoming-count"></span></div><div id="pc-upcoming"></div></article></div><article class="pc-panel pc-drafts"><div class="pc-panel-head"><div><span class="eyebrow">STILL IN PRODUCTION</span><h3>Drafts in progress</h3></div><span id="pc-draft-count"></span></div><div id="pc-drafts-list"></div></article><p class="pc-footnote">ReContent organises your publishing workflow. It does not post to social platforms automatically; mark an item Published after you publish it on the destination platform.</p>';
const campaignRoot=page.querySelector(".campaign-manager");campaignRoot?campaignRoot.before(section):page.prepend(section);
const $=s=>section.querySelector(s),read=(key,fallback)=>{try{const v=JSON.parse(localStorage.getItem(key)||"null");return v??fallback}catch{return fallback}},esc=s=>String(s??"").replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
const formatNames={thread:"X / Twitter thread",carousel:"Instagram carousel",linkedin:"LinkedIn post",reel:"Reels / TikTok",newsletter:"Newsletter",caption:"Social captions"};
const dateLabel=date=>{if(!date)return"Unscheduled";const d=new Date(date+"T12:00:00");return Number.isNaN(d.getTime())?date:d.toLocaleDateString(undefined,{month:"short",day:"numeric",year:"numeric"})};
function collect(){const campaigns=read("recontent-campaigns-v1",[]),library=read("recontent-library-v1",[]),schedule=read("recontent-calendar-v1",[]),items=campaigns.flatMap(c=>(c.items||[]).map(i=>({...i,campaignId:c.id,campaignTitle:c.title}))),today=new Date().toISOString().slice(0,10);const byId=new Map(library.map(p=>[p.id,p]));return{campaigns,library,schedule,items,byId,today,ready:items.filter(i=>i.status==="ready"),drafts:items.filter(i=>i.status==="idea"||i.status==="drafting"),upcoming:schedule.filter(i=>!i.done&&i.date>=today).sort((a,b)=>a.date.localeCompare(b.date))}}
function render(){const d=collect(),published=d.items.filter(i=>i.status==="published").length,scheduled=d.schedule.filter(i=>!i.done).length;
$("#pc-stats").innerHTML=[["READY TO PUBLISH",d.ready.length,"Approved campaign items"],["IN PROGRESS",d.drafts.length,"Ideas and active drafts"],["UPCOMING",d.upcoming.length,"Future calendar entries"],["PUBLISHED",published,"Campaign items marked live"]].map(x=>'<div class="pc-stat"><span>'+x[0]+'</span><b>'+x[1]+'</b><small>'+x[2]+'</small></div>').join("");
$("#pc-ready-count").textContent=d.ready.length+" ITEMS";
$("#pc-ready").innerHTML=d.ready.length?d.ready.map(i=>{const p=d.byId.get(i.libraryId),output=p?.outputs?.find(o=>o.format===i.format)||p?.outputs?.[0];return '<div class="pc-row"><div class="pc-row-main"><b>'+esc(i.title)+'</b><small>'+esc(i.campaignTitle)+' · '+esc(i.platform||"Channel not set")+' · '+esc(dateLabel(i.date))+'</small></div><div class="pc-actions">'+(i.libraryId?'<button type="button" data-pc-open="'+esc(i.libraryId)+'">Open draft</button>':'<button type="button" data-pc-missing="'+esc(i.id)+'">Draft missing</button>')+(output?.text?'<button type="button" data-pc-copy="'+esc(i.libraryId)+'" data-format="'+esc(output.format)+'">Copy</button>':'')+'<button type="button" class="pc-done" data-pc-publish="'+esc(i.campaignId)+'" data-item="'+esc(i.id)+'">Mark published</button></div></div>'}).join(""):'<div class="pc-empty">Nothing is marked Ready yet. Move a campaign item to Ready when its draft and assets are approved.</div>';
$("#pc-upcoming-count").textContent=d.upcoming.length+" POSTS";
$("#pc-upcoming").innerHTML=d.upcoming.length?d.upcoming.slice(0,7).map(i=>'<div class="pc-row"><div class="pc-date">'+esc(dateLabel(i.date))+'</div><div class="pc-row-main"><b>'+esc(i.title)+'</b><small>'+esc(formatNames[i.format]||i.format)+' · '+esc(i.campaign||"Content calendar")+'</small></div><span class="pc-status">SCHEDULED</span></div>').join(""):'<div class="pc-empty">Your calendar has no upcoming unfinished posts.</div>';
$("#pc-draft-count").textContent=d.drafts.length+" ITEMS";
$("#pc-drafts-list").innerHTML=d.drafts.length?d.drafts.slice(0,8).map(i=>'<div class="pc-row"><div class="pc-row-main"><b>'+esc(i.title)+'</b><small>'+esc(i.campaignTitle)+' · '+esc(i.status==="idea"?"Idea":"Drafting")+' · '+esc(i.platform||"Channel not set")+'</small></div><div class="pc-actions">'+(i.libraryId?'<button type="button" data-pc-open="'+esc(i.libraryId)+'">Open draft</button>':'<button type="button" data-pc-generate="'+esc(i.campaignId)+'" data-item="'+esc(i.id)+'">Generate draft</button>')+'</div></div>').join(""):'<div class="pc-empty">No campaign drafts are waiting. Nice work.</div>'}
section.addEventListener("click",async e=>{const open=e.target.closest("[data-pc-open]");if(open){window.recontentOpenLibraryProject?.(open.dataset.pcOpen);return}
const gen=e.target.closest("[data-pc-generate]");if(gen){const trigger=document.querySelector('[data-cm-generate="'+CSS.escape(gen.dataset.pcGenerate)+'"][data-item="'+CSS.escape(gen.dataset.item)+'"]');trigger?.click();return}
const copy=e.target.closest("[data-pc-copy]");if(copy){const p=collect().byId.get(copy.dataset.pcCopy),o=p?.outputs?.find(x=>x.format===copy.dataset.format);if(!o?.text)return;try{await navigator.clipboard.writeText(o.text);window.notify?.("Draft copied. Ready to paste into your platform.")}catch{window.notify?.("Clipboard access is unavailable in this browser.")}return}
const done=e.target.closest("[data-pc-publish]");if(done){const list=read("recontent-campaigns-v1",[]),c=list.find(x=>x.id===done.dataset.pcPublish),item=c?.items?.find(x=>x.id===done.dataset.item);if(!item)return;if(!confirm("Mark this campaign item as Published? Only do this after it is live on the destination platform."))return;item.status="published";localStorage.setItem("recontent-campaigns-v1",JSON.stringify(list));const schedule=read("recontent-calendar-v1",[]),linked=schedule.find(x=>x.id==="campcal_"+item.id);if(linked){linked.done=true;localStorage.setItem("recontent-calendar-v1",JSON.stringify(schedule));window.dispatchEvent(new CustomEvent("recontent:calendar-updated"))}window.dispatchEvent(new CustomEvent("recontent:campaigns-updated"));window.notify?.("Campaign status updated to Published.");render();return}
});
["recontent:workspaces","recontent:auth","recontent:library-updated","recontent:calendar-updated","recontent:campaigns-updated"].forEach(name=>window.addEventListener(name,render));
window.addEventListener("storage",e=>{if(["recontent-campaigns-v1","recontent-calendar-v1","recontent-library-v1"].includes(e.key))render()});
document.addEventListener("change",e=>{if(e.target.matches("[data-cm-status]"))setTimeout(render,0)});
render();
})();


/* Social Account Hub — stores profile references server-side; no OAuth/token claims. */
(()=>{const page=document.querySelector("#page-settings");if(!page||page.querySelector(".social-hub"))return;
const root=document.createElement("section");root.className="social-hub";root.innerHTML='<div class="sh-head"><div><span class="eyebrow">YOUR DISTRIBUTION NETWORK</span><h2>Social account<br><em>hub.</em></h2><p>Keep the profiles you publish to organised alongside your campaigns.</p></div><span class="sh-count" id="sh-count">0 PROFILES</span></div><div class="sh-notice"><b>Profile directory</b><span>Saved handles are planning references. Connect YouTube below to authorize a real channel connection. Other platforms are not authenticated yet.</span></div><section class="sh-youtube" id="sh-youtube"><div class="sh-youtube-copy"><span class="eyebrow">LIVE INTEGRATION · PHASE 01</span><h3>YouTube channel</h3><p id="sh-youtube-state">Checking OAuth configuration…</p></div><div class="sh-youtube-action" id="sh-youtube-action"><button type="button" id="sh-youtube-connect" disabled>Connect YouTube</button></div></section><div class="sh-layout"><form class="sh-form" id="sh-form"><h3>Add a social profile</h3><label>PLATFORM<select name="platform"><option value="instagram">Instagram</option><option value="tiktok">TikTok</option><option value="youtube">YouTube</option><option value="x">X / Twitter</option><option value="linkedin">LinkedIn</option><option value="facebook">Facebook</option><option value="pinterest">Pinterest</option></select></label><label>ACCOUNT HANDLE OR PROFILE NAME<input name="handle" maxlength="100" placeholder="@yourhandle or profile name" required></label><label>INTERNAL LABEL <span>(OPTIONAL)</span><input name="label" maxlength="80" placeholder="e.g. Main creator account"></label><button class="primary-btn compact" type="submit">＋ Save profile</button><p>Profiles are private to this workspace and stored in your ReContent account.</p></form><div class="sh-list-wrap"><div class="sh-list-head"><h3>Saved profiles</h3><span id="sh-state">CHECKING ACCOUNT…</span></div><div class="sh-list" id="sh-list"><div class="sh-empty">Loading saved profiles…</div></div></div></div><div class="sh-next"><div><span class="eyebrow">NEXT INTEGRATION PHASE</span><b>Authorize platforms with OAuth 2.0</b><p>Real connections require developer apps and redirect URLs for each platform. Access tokens will be stored server-side only after authorization is implemented.</p></div><span class="sh-lock">⌑</span></div>';
const privacy=page.querySelector(".settings-panel");privacy?privacy.before(root):page.append(root);
const $=s=>root.querySelector(s),names={instagram:"Instagram",tiktok:"TikTok",youtube:"YouTube",x:"X / Twitter",linkedin:"LinkedIn",facebook:"Facebook",pinterest:"Pinterest"},icons={instagram:"◎",tiktok:"♪",youtube:"▶",x:"𝕏",linkedin:"in",facebook:"f",pinterest:"P"};
let accounts=[],busy=false,youtubeConnection=null,youtubeOAuthEnabled=false;
const esc=s=>String(s??"").replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
function workspaceId(){return window.recontentWorkspaces?.getActive?.()?.id||"ws_default"}
async function request(method="GET",data={}){const id=workspaceId(),url="/api/social/accounts"+(method==="GET"?"?workspaceId="+encodeURIComponent(id):"");const res=await fetch(url,{method,credentials:"same-origin",headers:{"content-type":"application/json"},...(method==="GET"?{}:{body:JSON.stringify({...data,workspaceId:id})})});const result=await res.json().catch(()=>({error:"Unexpected server response."}));if(!res.ok)throw new Error(result.error||"Request failed.");return result}
function renderYoutube(){const state=$("#sh-youtube-state"),action=$("#sh-youtube-action");if(!state||!action)return;if(youtubeConnection){state.textContent="Connected to "+youtubeConnection.title+(youtubeConnection.needsReauth?" · Reauthorization required":" · OAuth active");action.innerHTML='<button type="button" class="sh-disconnect" id="sh-youtube-disconnect">Disconnect</button>'}else if(youtubeOAuthEnabled){state.textContent="Authorize your YouTube channel to prepare secure publishing access.";action.innerHTML='<button type="button" id="sh-youtube-connect">Connect YouTube ↗</button>'}else{state.textContent="OAuth setup is waiting for three Cloudflare secrets: client ID, client secret, and token encryption key.";action.innerHTML='<button type="button" id="sh-youtube-connect" disabled>Connect YouTube</button>'}}
function render(){ $("#sh-count").textContent=accounts.length+" PROFILES";$("#sh-list").innerHTML=accounts.length?accounts.map(a=>'<article class="sh-card"><div class="sh-platform">'+(icons[a.platform]||"◎")+'</div><div class="sh-card-main"><b>'+esc(a.label||names[a.platform]||a.platform)+'</b><span>'+esc(names[a.platform]||a.platform)+' · '+esc(a.handle)+'</span><small><i></i> PROFILE SAVED · NOT AUTHENTICATED</small></div><button type="button" data-sh-remove="'+esc(a.id)+'" aria-label="Remove '+esc(a.handle)+'">Remove</button></article>').join(""):'<div class="sh-empty">No profiles saved yet. Add the social accounts you plan to publish to.</div>'}
async function load(){if(!window.recontentAuth?.getUser?.()){accounts=[];render();$("#sh-state").textContent="SIGN IN REQUIRED";return}$("#sh-state").textContent="SYNCING…";try{await window.recontentWorkspaces?.refresh?.();const data=await request();accounts=Array.isArray(data.accounts)?data.accounts:[];youtubeConnection=data.youtubeConnection||null;youtubeOAuthEnabled=!!data.oauth?.youtubeEnabled;render();renderYoutube();$("#sh-state").textContent="SYNCED TO WORKSPACE"}catch(e){$("#sh-state").textContent=e.message.includes("sign in")?"SIGN IN REQUIRED":"UNAVAILABLE";$("#sh-list").innerHTML='<div class="sh-empty">'+esc(e.message)+'</div>'}}
$("#sh-form").addEventListener("submit",async e=>{e.preventDefault();if(busy)return;if(!window.recontentAuth?.getUser?.()){window.recontentAuth?.open?.();return}const f=new FormData(e.currentTarget),button=e.currentTarget.querySelector("button[type=submit]");busy=true;button.disabled=true;button.textContent="Saving…";try{const data=await request("POST",{platform:f.get("platform"),handle:f.get("handle"),label:f.get("label")});accounts=data.accounts||[data.account,...accounts];render();e.currentTarget.reset();window.notify?.("Social profile saved to this workspace.")}catch(err){window.notify?.(err.message||"Could not save profile.")}finally{busy=false;button.disabled=false;button.textContent="＋ Save profile"}});
$("#sh-list").addEventListener("click",async e=>{const b=e.target.closest("[data-sh-remove]");if(!b||busy)return;if(!confirm("Remove this saved profile from the workspace?"))return;busy=true;try{const data=await request("DELETE",{accountId:b.dataset.shRemove});accounts=data.accounts||[];render();window.notify?.("Profile removed.")}catch(err){window.notify?.(err.message||"Could not remove profile.")}finally{busy=false}});
root.addEventListener("click",async e=>{if(e.target.closest("#sh-youtube-connect")){if(!window.recontentAuth?.getUser?.()){window.recontentAuth?.open?.();return}window.location.href="/api/social/youtube/connect?workspaceId="+encodeURIComponent(workspaceId());return}if(e.target.closest("#sh-youtube-disconnect")){if(!confirm("Disconnect this YouTube channel from the active workspace?"))return;try{await request("DELETE",{});youtubeConnection=null;renderYoutube();window.notify?.("YouTube disconnected.")}catch(err){window.notify?.(err.message||"Could not disconnect YouTube.")}}});
const oauthResult=new URLSearchParams(location.search).get("youtube");if(oauthResult){const messages={connected:"YouTube channel connected successfully.",cancelled:"YouTube authorization was cancelled.",state_error:"The YouTube authorization session expired. Please try again.",setup_error:"YouTube OAuth setup is incomplete.",token_error:"Google could not exchange the authorization code.",channel_error:"No YouTube channel was returned by Google."};if(messages[oauthResult])window.setTimeout?.(()=>window.notify?.(messages[oauthResult]),300);if(history.replaceState)history.replaceState(null,"",location.pathname+location.hash)}
window.addEventListener("recontent:auth",load);window.addEventListener("recontent:workspaces",load);load();
})();


// Content Intelligence — workspace-specific AI strategy planner
(()=>{
 const page=document.querySelector("#page-analytics");if(!page||page.querySelector("#ci-root"))return;
 const root=document.createElement("section");root.id="ci-root";root.className="ci-root";
 root.innerHTML='<div class="ci-head"><div><span class="eyebrow">AI CONTENT INTELLIGENCE</span><h2>Your next 7 days, with a strategy.</h2><p>Turn your Brand Kit, Content Brief, recent library and campaigns into a practical content direction.</p></div><button type="button" id="ci-generate" class="primary-btn compact">✦ Generate strategy</button></div><div id="ci-status" class="ci-status">Sign in and generate a strategy to get started.</div><div id="ci-output"></div><p class="ci-footnote">Recommendations are AI-generated from your workspace context. No reach, engagement, or audience analytics are fabricated.</p>';
 const insight=page.querySelector(".insight-grid");if(insight)insight.before(root);else page.append(root);
 const $=s=>root.querySelector(s),esc=s=>String(s??"").replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
 const workspaceId=()=>window.recontentWorkspaces?.getActive?.()?.id||"ws_default";
 let busy=false,strategy=null;
 async function request(method="GET"){const id=workspaceId(),res=await fetch("/api/strategy?workspaceId="+encodeURIComponent(id),{method,credentials:"same-origin",headers:{"content-type":"application/json"},...(method==="POST"?{body:JSON.stringify({workspaceId:id})}:{})});const data=await res.json().catch(()=>({error:"Unexpected server response."}));if(!res.ok)throw new Error(data.error||"Strategy request failed.");return data}
 function render(){const out=$("#ci-output");if(!strategy){out.innerHTML='<div class="ci-empty"><span>✳</span><b>Your strategy starts here</b><p>Generate a fresh plan to discover content pillars, post ideas, and a weekly publishing rhythm.</p></div>';return}
 const ideas=strategy.ideas||[],pillars=strategy.pillars||[],days=strategy.weeklyPlan||[];
 out.innerHTML='<div class="ci-summary"><span class="eyebrow">STRATEGIC DIRECTION</span><h3>'+esc(strategy.summary)+'</h3><p>'+esc(strategy.positioning)+'</p><small>Generated '+new Date(strategy.generatedAt).toLocaleString()+'</small></div><div class="ci-section-head"><div><span class="eyebrow">CONTENT PILLARS</span><h3>What your brand can own</h3></div></div><div class="ci-pillar-grid">'+pillars.map((p,i)=>'<article class="ci-pillar"><span>0'+(i+1)+'</span><h4>'+esc(p.name)+'</h4><p>'+esc(p.description)+'</p><div>'+((p.exampleTopics||[]).map(t=>'<small>'+esc(t)+'</small>').join(""))+'</div></article>').join("")+'</div><div class="ci-section-head"><div><span class="eyebrow">IDEA BANK</span><h3>Five ideas worth making</h3></div></div><div class="ci-ideas">'+ideas.map((x,i)=>'<article class="ci-idea"><div class="ci-idea-top"><span>IDEA 0'+(i+1)+'</span><small>'+esc(x.platform)+' · '+esc(x.format)+'</small></div><h4>'+esc(x.title)+'</h4><p>'+esc(x.angle)+'</p><blockquote>'+esc(x.hook)+'</blockquote><div class="ci-idea-bottom"><span>CTA: '+esc(x.cta)+'</span><div class="ci-idea-actions"><button type="button" data-ci-copy="'+i+'">Copy idea</button><button type="button" data-ci-add="'+i+'">＋ Add to campaign</button></div></div></article>').join("")+'</div><div class="ci-section-head"><div><span class="eyebrow">WEEKLY RHYTHM</span><h3>A balanced 7-day plan</h3></div></div><div class="ci-week">'+days.map(d=>'<article><b>'+esc(d.day)+'</b><span>'+esc(d.focus)+'</span><small>'+esc(d.platform)+' · '+esc(d.format)+'</small></article>').join("")+'</div><div class="ci-recommend"><span class="eyebrow">NEXT BEST MOVES</span><ul>'+((strategy.recommendations||[]).map(x=>'<li>'+esc(x)+'</li>').join(""))+'</ul></div>';
 }
 async function load(){if(!window.recontentAuth?.getUser?.()){$("#ci-status").textContent="Sign in to save a strategy to your workspace.";return}try{const data=await request();strategy=data.strategy;render();$("#ci-status").textContent=strategy?"Saved to this workspace":"No strategy saved yet."}catch(e){$("#ci-status").textContent=e.message}}
 $("#ci-generate").addEventListener("click",async()=>{if(busy)return;if(!window.recontentAuth?.getUser?.()){window.recontentAuth?.open?.();return}busy=true;const btn=$("#ci-generate");btn.disabled=true;btn.textContent="Building your strategy…";$("#ci-status").textContent="Analyzing your Brand Kit, brief, library and campaigns…";try{const data=await request("POST");strategy=data.strategy;render();$("#ci-status").textContent="Strategy generated and saved to this workspace.";window.notify?.("Your content strategy is ready.")}catch(e){$("#ci-status").textContent=e.message;window.notify?.(e.message)}finally{busy=false;btn.disabled=false;btn.textContent="↻ Refresh strategy"}});
 $("#ci-output").addEventListener("click",async e=>{
    const add=e.target.closest("[data-ci-add]");
    if(add&&strategy){
      const idea=strategy.ideas[Number(add.dataset.ciAdd)];if(!idea)return;
      const key="recontent-campaigns-v1";let list=[];try{const parsed=JSON.parse(localStorage.getItem(key)||"[]");if(Array.isArray(parsed))list=parsed}catch{}
      const labels=list.map((c,i)=>(i+1)+". "+c.title).join("\n");
      let campaign;
      if(list.length){const choice=prompt("Add this idea to which campaign? Enter a number, or type NEW to create one.\n\n"+labels,"1");if(choice===null)return;if(choice.trim().toLowerCase()==="new"){const title=prompt("Name the new campaign:",idea.title);if(!title?.trim())return;campaign={id:"camp_"+Date.now().toString(36),title:title.trim().slice(0,100),goal:idea.angle||"Content strategy idea",platforms:[],startDate:"",endDate:"",items:[]};list.unshift(campaign)}else{const n=Number(choice);if(!Number.isInteger(n)||n<1||n>list.length){window.notify?.("Choose a valid campaign number.");return}campaign=list[n-1]}}
      else{const title=prompt("Create a campaign for this idea:",idea.title);if(!title?.trim())return;campaign={id:"camp_"+Date.now().toString(36),title:title.trim().slice(0,100),goal:idea.angle||"Content strategy idea",platforms:[],startDate:"",endDate:"",items:[]};list.unshift(campaign)}
      campaign.items=Array.isArray(campaign.items)?campaign.items:[];
      const platform=String(idea.platform||"Other"),formatRaw=String(idea.format||"").toLowerCase();
      const format=formatRaw.includes("carousel")?"carousel":formatRaw.includes("reel")||formatRaw.includes("video")||formatRaw.includes("tiktok")?"reel":formatRaw.includes("linkedin")?"linkedin":formatRaw.includes("newsletter")||formatRaw.includes("email")?"newsletter":formatRaw.includes("caption")?"caption":"thread";
      campaign.items.push({id:"idea_"+Date.now().toString(36),title:String(idea.title||"New content idea").slice(0,120),platform,format,date:"",status:"idea",strategyAngle:String(idea.angle||"").slice(0,500),hook:String(idea.hook||"").slice(0,500),cta:String(idea.cta||"").slice(0,240)});
      localStorage.setItem(key,JSON.stringify(list));window.dispatchEvent(new CustomEvent("recontent:campaigns-updated"));window.notify?.("Idea added to "+campaign.title+".");return;
    }
    const b=e.target.closest("[data-ci-copy]");if(!b||!strategy)return;const x=strategy.ideas[Number(b.dataset.ciCopy)];if(!x)return;const text=[x.title,x.angle,"Hook: "+x.hook,"Platform: "+x.platform,"Format: "+x.format,"CTA: "+x.cta].join("\\n");try{await navigator.clipboard.writeText(text);window.notify?.("Idea copied.")}catch{window.notify?.("Could not access clipboard.")}
  });
 window.addEventListener("recontent:auth",load);window.addEventListener("recontent:workspaces",()=>{strategy=null;render();load()});load();
})();
