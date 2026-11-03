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