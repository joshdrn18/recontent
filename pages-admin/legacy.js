(() => {
    const allowed = new Set(["overview","players","welcome","mail","economy","cards","packs","shop",
        "rewards","quests","world","systems","community","ranked","moderation","events","audit","support"]);
    const pick = new URL(location.href).searchParams.get("section");
    const section = allowed.has(pick) ? pick : "overview";
    try {
        tab = "admin";
        adminSection = section;
        if (me?.user) render();
    } catch {
        // A deployment with a different console controller still opens through its own navigation.
    }
    const link = document.createElement("a");
    link.href = "/";
    link.className = "btn secondary";
    link.textContent = "Back to new Admin Console";
    const attach = () => {
        const header = document.querySelector(".admin-workbench .admin-header");
        if (header && !header.contains(link)) header.append(link);
    };
    new MutationObserver(attach).observe(document.getElementById("content") || document.body,
        {childList:true,subtree:true});
    attach();
})();
