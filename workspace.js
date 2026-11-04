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