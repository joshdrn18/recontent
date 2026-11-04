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