(() => {
    const byId = id => document.getElementById(id);
    const root = byId("ops-main");
    if (!root) return;

    const rarities = ["COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC"];
    const steps = [
        ["tutorial","Tutorial"],["card","First card"],["formation","Formation"],
        ["upgrade","Card upgrade"],["adventure","Adventure win"],["guild","Guild"],["arena","Arena turn"]
    ];
    const fmt = new Intl.NumberFormat("en");
    const oddsFormat = new Intl.NumberFormat("en", {maximumFractionDigits:6});
    const time = new Intl.DateTimeFormat("en-GB",{
        timeZone:"Asia/Jakarta",day:"2-digit",month:"short",year:"numeric",hour:"2-digit",minute:"2-digit"
    });
    let data;
    const status = byId("ops-status");
    const refresh = byId("ops-refresh");
    const select = byId("ops-pack");

    function node(tag, text, className) {
        const element = document.createElement(tag);
        if (text !== undefined) element.textContent = text;
        if (className) element.className = className;
        return element;
    }

    function table(id, label, columns, rows, emptyMessage) {
        const element = byId(id);
        element.replaceChildren();
        if (!rows.length) {
            element.append(node("p", emptyMessage, "ops-empty"));
            return;
        }
        const frame = node("div", undefined, "ops-table-frame");
        const grid = node("table");
        grid.setAttribute("aria-label", label);
        const header = node("tr");
        for (const column of columns) {
            const cell = node("th", column);
            cell.scope = "col";
            header.append(cell);
        }
        grid.createTHead().append(header);
        const body = grid.createTBody();
        for (const row of rows) {
            const line = node("tr");
            for (const value of row) line.append(node("td", value));
            body.append(line);
        }
        frame.append(grid);
        element.append(frame);
    }

    function date(text) {
        if (!text) return "—";
        const parsed = new Date(text.includes("T") ? text : text.replace(" ","T")+"Z");
        return Number.isFinite(parsed.getTime()) ? time.format(parsed)+" WIB" : "—";
    }

    function percentage(value) {
        return value > 0 && value < 0.000001 ? "<0.000001%" : oddsFormat.format(value)+"%";
    }

    function renderOdds() {
        const pack = data?.packs.find(entry=>entry.slug===select.value);
        const pity = byId("ops-pity");
        const rows = [];
        if (pack) {
            pity.textContent = (pack.active?"Active":"Inactive") + " · " + fmt.format(pack.opened)
                + " opens in 7 days · " + (pack.slug==="mythic"
                    ? "Mythic guaranteed in slot 1 on pull "+data.mythic_pity+" if no Mythic appears earlier."
                    : "No pity system for this banner.");
            for (let slot=1;slot<=pack.cards_per_pack;slot++) {
                const config=pack.slots.find(value=>value.number===slot);
                rows.push([slot,...rarities.map(rarity=>{
                    if (!config) return "—";
                    return percentage(config.rates.find(item=>item.rarity===rarity)?.percent ?? 0);
                })]);
            }
        } else {
            pity.textContent = "";
        }
        table("ops-odds","Configured rarity odds",["Slot",...rarities],rows,"No banner configuration available.");
    }

    function render(result) {
        const selected = select.value;
        data = result;
        const kpis=byId("ops-kpis");
        kpis.replaceChildren();
        const binderTotal=data.sets.reduce((sum,set)=>sum+Object.values(set.claims).reduce((a,b)=>a+b,0),0);
        const metrics=[
            ["Summons in 7 days",data.packs.reduce((sum,pack)=>sum+pack.opened,0)],
            ["Binder claims · active sets",binderTotal],
            ["Guild rewards claimed",data.claims.reduce((sum,row)=>sum+row.claimed,0)],
            ["New collectors · 30 days",data.journey.players]
        ];
        for (const [label,value] of metrics) {
            const box = node("article",undefined,"ops-kpi");
            box.append(node("span",label),node("strong",fmt.format(value ?? 0)));
            kpis.append(box);
        }
        select.replaceChildren();
        for (const pack of data.packs) {
            const option = node("option",pack.name);
            option.value = pack.slug;
            select.append(option);
        }
        if (data.packs.some(pack=>pack.slug===selected)) select.value = selected;
        select.disabled = !data.packs.length;
        renderOdds();

        table("ops-offers","Event pack offers",["Event","Pack","Starts","Ends"],
            data.offers.map(offer=>[offer.title,offer.name,date(offer.starts_at),date(offer.ends_at)]),
            "No active or upcoming event pack offers.");
        table("ops-binders","Binder reward claims",["Card set","25%","50%","75%","100%"],
            data.sets.map(set=>[set.name,...[25,50,75,100].map(threshold=>fmt.format(set.claims[threshold]??0))]),
            "No active card sets.");
        byId("ops-week").textContent = "Week of "+data.calendar.week+" · Top 30 Guilds by damage";
        table("ops-guilds","Guild Boss activity",["Guild","Strikes","Players","Boss damage"],
            data.guilds.map(guild=>[guild.name+" ["+guild.tag+"]",fmt.format(guild.strikes),fmt.format(guild.players),
                fmt.format(guild.damage)+" / "+fmt.format(data.boss_hp)]),
            "No Guild Boss strikes logged this week.");

        const rewardNames={BINDER:"Binder",GUILD_WEEK:"Guild weekly",GUILD_SEASON:"Guild season"};
        table("ops-activity","Daily reward claims",["Date (UTC)","Reward","Claims"],
            (data.activity??[]).map(row=>[row.day,rewardNames[row.reward_kind]??row.reward_kind,fmt.format(row.claims)]),
            "No recent reward claims.");
        table("ops-weekly","Weekly Guild Boss history",["Week starts","Active Guilds","Total strikes","Bosses cleared"],
            (data.weekly??[]).map(row=>[row.week_start,fmt.format(row.guilds),fmt.format(row.strikes),fmt.format(row.cleared)]),
            "No Guild Boss history yet.");

        const journey = byId("ops-journey");
        journey.replaceChildren();
        const players = data.journey.players ?? 0;
        byId("ops-cohort").textContent = "Collectors who joined in the last 30 days.";
        if (!players) journey.append(node("p","No new collectors in this period.","ops-empty"));
        for (const [key,label] of players ? steps : []) {
            const value = data.journey[key] ?? 0;
            const row = node("div",undefined,"ops-step");
            const bar = node("progress");
            bar.max = players;
            bar.value = value;
            bar.setAttribute("aria-label", label);
            row.append(node("span",label),bar,node("small",fmt.format(value)+" / "+fmt.format(players)));
            journey.append(row);
        }
    }

    async function update() {
        refresh.disabled = true;
        status.classList.remove("ops-error");
        status.textContent = "Loading dashboard…";
        try {
            const response = await fetch("/api/admin/operations/phase14",{credentials:"same-origin",cache:"no-store"});
            const result = await response.json();
            if (!response.ok || !result.ok) throw new Error(result.error || "Could not load data.");
            render(result);
            status.textContent = "Updated " + date(result.updated_at);
        } catch (error) {
            status.classList.add("ops-error");
            status.textContent = error.message;
        } finally {
            refresh.disabled = false;
        }
    }

    const anchors = [...document.querySelectorAll(".ops-sidebar nav a")];
    function markActive() {
        const active = anchors.find(anchor=>anchor.hash===location.hash) ?? anchors[0];
        for (const link of anchors) {
            if (link===active) link.setAttribute("aria-current","location");
            else link.removeAttribute("aria-current");
        }
    }
    addEventListener("hashchange",markActive);
    markActive();
    refresh.addEventListener("click",update);
    select.addEventListener("change",renderOdds);
    update();
})();
