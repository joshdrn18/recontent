const workspaces = [
    {
        title: "Players & community", mark: "people",
        tools: [
            ["Players", "Account controls, wallet adjustments and rewards", "players"],
            ["Guild Settings", "Recruitment, minimum level and guild notices", "ops-guild-settings"],
            ["Ranked Security", "Season integrity and account restrictions", "ranked"],
            ["Support Center", "Official contact links shown in the APK", "support"]
        ]
    },
    {
        title: "Cards & economy", mark: "cards",
        tools: [
            ["Card Catalog", "Card artwork, rarity, elements and availability", "cards"],
            ["Packs & Odds", "Summon pricing, availability and rarity weights", "packs"],
            ["Universal Shop", "Store listings, prices and purchase limits", "shop"],
            ["Economy Center", "Currency balances, earnings and spending", "economy"]
        ]
    },
    {
        title: "Game modes", mark: "game",
        tools: [
            ["Adventure", "Chapters, encounter balance and XP rewards", "world"],
            ["Dungeons", "Difficulty, floors and clear rewards", "live/dungeons"],
            ["World Raid", "Boss HP, availability and raid rewards", "live/bosses"],
            ["Ranked Seasons", "Season dates and availability", "live/seasons"]
        ]
    },
    {
        title: "Progression & events", mark: "progress",
        tools: [
            ["Achievements", "Completion targets and rewards", "live/achievements"],
            ["Daily Challenges", "Templates, objectives and reward amounts", "live/daily"],
            ["Missions & Quests", "Daily and weekly goals and rewards", "quests"],
            ["Level Rewards", "Collector-level reward schedule", "rewards"],
            ["Event Studio", "Event dates, login rewards and event shop", "events"]
        ]
    }
];

const operations = [
    ["Summon Analytics", "Recent openings, banner rates and pity information", "ops-summon"],
    ["Binder Rewards", "Completion claims by set and milestone", "ops-binder"],
    ["Guild Activities", "Guild Boss strikes and weekly participation", "ops-guild"],
    ["Player Journey", "Tutorial and onboarding completion", "ops-journey"],
    ["Ranked Match Monitor", "Recent completed matches and rating changes", "intel-ranked"],
    ["Audit Log", "Recorded changes from admin accounts", "audit"]
];

const coverage = [
    {
        title: "Configurable", type: "edit",
        description: "Adventure, Dungeons, World Raid, missions, achievements, login events, shop, packs, guild recruitment, ranked seasons and support contacts.",
        action: ["Open Live Settings", "live"]
    },
    {
        title: "Monitoring", type: "monitor",
        description: "Binder Completion, Guild Boss, Player Journey, summon activity and Ranked history have reports or account-level tools.",
        action: ["Open Sync & Diagnostics", "sync-diagnostics"]
    },
    {
        title: "Editor unavailable", type: "unavailable",
        description: "Weekly Tower rules, rotating World Events, Card Mastery, Trade Board rules and Player Showcase have no admin editor on the current backend."
    },
    {
        title: "Device and player settings", type: "local",
        description: "Graphics, sound, reduced motion, Practice Mode and the PvP/PvE deck selection are managed in the APK."
    }
];

function el(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined) element.textContent = text;
    return element;
}

function decoration(className, glyph) {
    const mark = el("span", className, glyph);
    mark.setAttribute("aria-hidden", "true");
    return mark;
}

function jump(text, id, className) {
    const link = el("a", className, text);
    link.href = "#" + id;
    return link;
}

function toolList(entries) {
    const list = el("ul", "fo-tool-list");
    for (const [title, description, id] of entries) {
        const row = el("li");
        const link = jump(undefined, id, "fo-tool");
        const text = el("span");
        text.append(el("strong", "", title), el("span", "fo-muted", description));
        link.append(text, decoration("fo-arrow", "→"));
        row.append(link);
        list.append(row);
    }
    return list;
}

function sectionTitle(title, description) {
    const block = el("div", "fo-section-heading");
    block.append(el("h3", "", title), el("p", "fo-muted", description));
    return block;
}

async function loadMetrics(target, metrics) {
    const update = target.querySelector(".fo-metric-status");
    update.replaceChildren(el("span", "", "Loading game totals…"));
    try {
        const response = await fetch("/api/admin/summary", {credentials:"same-origin", cache:"no-store"});
        const data = await response.json();
        if (!response.ok || data.ok === false || !data.stats) throw new Error("Game totals are temporarily unavailable.");
        for (const [key, node] of metrics) {
            const value = data.stats[key]?.total;
            node.textContent = Number.isFinite(value) ? new Intl.NumberFormat("en-US").format(value) : "—";
        }
        update.replaceChildren();
    } catch {
        const retry = el("button", "fo-action fo-retry", "Retry");
        retry.type = "button";
        retry.onclick = () => loadMetrics(target, metrics);
        update.replaceChildren(el("span", "", "Game totals could not load."), retry);
    }
}

export function mount(root) {
    const page = el("section", "firstlight-overview");
    const hero = el("header", "fo-hero");
    const crest = el("div", "fo-crest");
    crest.append(decoration("fo-crest-icon", "✦"));
    const title = el("div", "fo-hero-copy");
    title.append(el("h2", "", "Firstlight Operations"),
        el("p", "fo-muted", "Manage game settings, player accounts and live activity for Android and web."));
    const actions = el("div", "fo-actions");
    actions.append(jump("Live Settings", "live", "fo-action fo-primary"),
        jump("Sync & Diagnostics", "sync-diagnostics", "fo-action"));
    hero.append(crest, title, actions);
    page.append(hero);

    const summary = el("section", "fo-summary");
    summary.setAttribute("aria-label", "Game totals");
    const metrics = new Map();
    for (const [key, label] of [["users","Players"], ["cards","Active cards"], ["instances","Card copies"], ["adminGifts","Admin gifts"]]) {
        const item = el("div", "fo-metric");
        const value = el("strong", "", "—");
        metrics.set(key, value);
        item.append(el("span", "fo-muted", label), value);
        summary.append(item);
    }
    const status = el("p", "fo-metric-status fo-muted");
    status.setAttribute("aria-live", "polite");
    summary.append(status);
    page.append(summary);

    const tools = el("section", "fo-section");
    const heading = sectionTitle("Management workspaces", "Open the editor for the system you want to manage.");
    const search = el("input", "fo-search");
    search.type = "search";
    search.placeholder = "Find a tool";
    search.setAttribute("aria-label", "Find a management tool");
    heading.append(search);
    const groups = el("div", "fo-groups");
    const empty = el("p", "fo-muted fo-empty", "No management tools match that search.");
    empty.hidden = true;
    const cards = workspaces.map(({title, mark, tools:entries}) => {
        const group = el("article", "fo-group");
        const header = el("header", "fo-group-title");
        header.append(decoration("fo-icon fo-icon-" + mark), el("h4", "", title));
        const list = toolList(entries);
        group.append(header, list);
        groups.append(group);
        return {group, title, entries, rows:[...list.children]};
    });
    search.addEventListener("input", () => {
        const filter = search.value.trim().toLowerCase();
        for (const {group, title, entries, rows} of cards) {
            rows.forEach((row, index) => {
                row.hidden = ![title,...entries[index].slice(0,2)].join(" ").toLowerCase().includes(filter);
            });
            group.hidden = rows.every(row => row.hidden);
        }
        empty.hidden = cards.some(({group}) => !group.hidden);
    });
    tools.append(heading, groups, empty);
    page.append(tools);

    const monitoring = el("section", "fo-section fo-monitoring");
    monitoring.append(sectionTitle("Activity & reports", "Track player activity and review what happened in live systems."),
        toolList(operations));
    page.append(monitoring);

    const apk = el("section", "fo-section");
    apk.append(sectionTitle("Android feature access", "Check which features have a web editor, reports, or APK controls."));
    const coverageList = el("div", "fo-coverage");
    for (const item of coverage) {
        const card = el("article", "fo-coverage-item");
        const title = el("h4");
        title.append(el("span", "fo-status fo-status-" + item.type), document.createTextNode(item.title));
        card.append(title, el("p", "fo-muted", item.description));
        if (item.action) card.append(jump(...item.action, "fo-text-link"));
        coverageList.append(card);
    }
    apk.append(coverageList);
    page.append(apk);
    root.replaceChildren(page);
    void loadMetrics(summary, metrics);
}
