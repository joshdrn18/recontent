const sections = [
    {id:"overview",name:"Overview",group:"Command Center",description:"Game health, player activity, and frequently used tools."},
    {id:"live",name:"Live Settings",group:"Command Center",description:"Pause or resume incoming actions on Android and web.",custom:"live"},
    {id:"operations",name:"Game Operations",group:"Command Center",description:"Guild and Binder rewards, pack odds, battles, and the new player journey.",href:"/admin/operations"},
    {id:"players",name:"Players",group:"Accounts",description:"Collector accounts and account operations.",api:"users",rows:"users",cols:["display_name","username","collector_level","role"],legacy:"players"},
    {id:"mail",name:"Global Mail",group:"Accounts",description:"Announcements and player inbox rewards.",api:"mail",rows:"messages",cols:["title","status","created_at","expires_at"],legacy:"mail"},
    {id:"beta",name:"Beta & Titles",group:"Accounts",description:"Manage test access and collector titles.",api:"titles",rows:"titles",cols:["name","category","rarity","award_count"],href:"/admin/phase6"},
    {id:"guilds",name:"Guild Settings",group:"Accounts",description:"Set member capacity, minimum level, and recruitment.",api:"guilds",rows:"guilds",cols:["name","tag","member_count","member_limit","minimum_level","is_recruiting"],editor:"guild"},
    {id:"economy",name:"Economy Center",group:"Economy",description:"Review balances, transaction activity, and the game ledger.",api:"economy",rows:"ledger",cols:["currency","reason","amount","created_at"],legacy:"economy"},
    {id:"shop",name:"Universal Shop",group:"Economy",description:"Stock, offers, pricing, and purchase limits.",api:"shop",rows:"items",cols:["title","category","currency","price","is_active"],legacy:"shop"},
    {id:"welcome",name:"Welcome Bonus",group:"Economy",description:"Rewards granted when collectors register.",api:"welcome-bonus",object:"config",legacy:"welcome"},
    {id:"rewards",name:"Level Rewards",group:"Economy",description:"Reward schedule by collector level.",api:"rewards",rows:"rewards",cols:["level","reward_title","reward_coins","reward_gems","reward_shards"],legacy:"rewards"},
    {id:"cards",name:"Cards",group:"Content",description:"Card catalogue, artwork, rarity, and card status.",api:"cards",rows:"cards",cols:["card_code","name","rarity","element","is_active"],legacy:"cards"},
    {id:"packs",name:"Packs & Odds",group:"Content",description:"Summon banners, price, availability, and per-slot weights.",api:"packs",rows:"packs",cols:["name","slug","price","currency","cards_per_pack","is_active"],editor:"pack",legacy:"packs"},
    {id:"world",name:"World Builder",group:"Content",description:"Adventure chapters and encounters.",api:"world",rows:"stages",cols:["chapter_slug","title","enemy_name","enemy_power","is_active"],legacy:"world"},
    {id:"quests",name:"Quests",group:"Content",description:"Mission goals and completion rewards.",api:"missions",rows:"missions",cols:["title","period_type","progress_type","target_count","is_active"],legacy:"quests"},
    {id:"systems",name:"Systems Studio",group:"Content",description:"Bosses, Dungeons, achievements and daily challenge templates.",api:"systems",rows:"bosses",cols:["name","slug","hp","is_active"],legacy:"systems"},
    {id:"events",name:"Event Studio",group:"Content",description:"Live events, schedules, login bonuses, and event shop.",api:"events",rows:"events",cols:["title","event_type","starts_at","ends_at","is_active"],legacy:"events"},
    {id:"community",name:"Community Ops",group:"Trust & Safety",description:"Season operations and community activity.",legacy:"community"},
    {id:"ranked",name:"Ranked Security",group:"Trust & Safety",description:"Review ranked integrity and existing account controls.",legacy:"ranked"},
    {id:"moderation",name:"Moderation & Security",group:"Trust & Safety",description:"Reports, appeals, player sanctions, and security flags.",legacy:"moderation"},
    {id:"audit",name:"Audit Log",group:"Trust & Safety",description:"Review recorded administrative changes.",api:"audit",rows:"entries",cols:["action","target_name","created_at"],legacy:"audit"},
    {id:"support",name:"Support Center",group:"Trust & Safety",description:"Manage official support channels published to the APK and web.",href:"/admin/support",legacy:"support"}
];

const $ = id => document.getElementById(id);
const stage = $("stage");
const number = new Intl.NumberFormat("en");
let renderVersion = 0;

function element(tag, label, cls) {
    const value = document.createElement(tag);
    if (label !== undefined) value.textContent = label;
    if (cls) value.className = cls;
    return value;
}

function link(title, href, cls = "button") {
    const value = element("a", title, cls);
    value.href = href;
    return value;
}

function button(title, action, cls = "secondary") {
    const value = element("button", title, cls);
    value.type = "button";
    value.addEventListener("click", action);
    return value;
}

class RequestError extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
    }
}

async function api(path, options = {}) {
    const response = await fetch(path, {
        credentials:"same-origin",cache:"no-store",...options,
        headers:{Accept:"application/json",...(options.body ? {"Content-Type":"application/json"} : {})}
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data.ok === false) throw new RequestError(data.error || "Request failed.",response.status);
    return data;
}

function status(message, isError = false) {
    const p = element("p",message,isError?"notice error":"notice");
    p.setAttribute("role","status");
    return p;
}

function legacy(section) {
    return "/legacy?section=" + encodeURIComponent(section);
}

function readSection() {
    return sections.find(section => "#" + section.id === location.hash) || sections[0];
}

function renderNav(active) {
    const filter = $("nav-filter").value.trim().toLowerCase();
    const nav = $("nav");
    nav.replaceChildren();
    const groups = new Map();
    for (const section of sections) {
        if (!section.name.toLowerCase().includes(filter) && !section.group.toLowerCase().includes(filter)) continue;
        if (!groups.has(section.group)) {
            const panel = element("div",undefined,"nav-group");
            panel.append(element("p",section.group));
            groups.set(section.group,panel);
            nav.append(panel);
        }
        const item = link(section.name,"#"+section.id,"");
        if (section.id === active.id) item.setAttribute("aria-current","page");
        item.addEventListener("click",closeMenu);
        groups.get(section.group).append(item);
    }
    if (!groups.size) nav.append(status("No tools match your search."));
}

function closeMenu() {
    $("sidebar").classList.remove("open");
    $("menu").setAttribute("aria-expanded","false");
}

function pageHeader(section) {
    const header = element("header",undefined,"hero");
    const text = element("div");
    text.append(element("h1",section.name),element("p",section.description));
    const actions = element("div",undefined,"hero-actions");
    if (section.api || section.custom) actions.append(button("Refresh",render));
    if (section.legacy) actions.append(link("Open full tools",legacy(section.legacy)));
    if (section.href) actions.append(link("Open workspace",section.href,"button primary"));
    header.append(text,actions);
    return header;
}

function metric(title, total) {
    const card = element("article",undefined,"metric");
    card.append(element("p",title),element("strong",total === undefined ? "—" : number.format(total)));
    return card;
}

async function overview(host) {
    const metrics = element("div",undefined,"metric-grid");
    const links = element("div",undefined,"panel-grid");
    host.replaceChildren(metrics,links);
    for (const [title,description,target] of [
        ["Live Settings","Control incoming actions without shipping another APK.","#live"],
        ["Guild Settings","Capacity, minimum level, and recruitment.","#guilds"],
        ["Packs & Odds","Manage banner availability and drop weights.","#packs"],
        ["Audit Log","Review recorded server and account operations.","#audit"]
    ]) {
        const panel = element("article",undefined,"panel");
        panel.append(element("h2",title),element("p",description),link("Open "+title,target));
        links.append(panel);
    }
    try {
        const [summary,ops] = await Promise.allSettled([api("/api/admin/summary"),api("/api/admin/operations/phase14")]);
        const totals = summary.status === "fulfilled" ? summary.value.stats || {} : {};
        metrics.append(metric("Players",totals.users?.total),metric("Cards",totals.cards?.total),
            metric("Copies",totals.instances?.total),metric("Weekly guilds shown",
                ops.status === "fulfilled" ? ops.value.guilds?.length : undefined));
        if (summary.status === "rejected") metrics.after(status(summary.reason.message,true));
    } catch (error) {
        metrics.append(status(error.message,true));
    }
}

function humanize(value) {
    return String(value).replace(/_/g," ").replace(/\b[a-z]/g,char=>char.toUpperCase());
}

function display(value) {
    if (value === null || value === undefined || value === "") return "—";
    if (typeof value === "boolean") return value ? "Yes" : "No";
    if (typeof value === "number") return number.format(value);
    return String(value);
}

function csvCell(value) {
    let text=String(value ?? "");
    if (/^[\s]*[=+@-]|^[\t\r\n]/.test(text)) text="'"+text;
    return '"' + text.replaceAll('"','""') + '"';
}

function exportCSV(section, rows) {
    const fields=section.cols;
    const csv=[fields.map(csvCell).join(","),...rows.map(row=>fields.map(key=>csvCell(row[key])).join(","))].join("\r\n");
    const url=URL.createObjectURL(new Blob([csv],{type:"text/csv;charset=utf-8"}));
    const download=link("Export",url);
    download.download=section.id+".csv";
    download.click();
    setTimeout(()=>URL.revokeObjectURL(url),1000);
}

function table(section, rows, body, editor) {
    const frame=element("div",undefined,"table-wrap");
    const table=element("table");
    table.setAttribute("aria-label",section.name);
    const heading=table.createTHead().insertRow();
    for(const field of section.cols) {
        const column=element("th",humanize(field));
        column.scope="col";
        heading.append(column);
    }
    if (editor) heading.append(element("th","Actions"));
    const grid=table.createTBody();
    rows.forEach(row=>{
        const line=grid.insertRow();
        for (const field of section.cols) line.append(element("td",display(row[field])));
        if(editor) line.insertCell().append(button("Edit",()=>editor(row)));
    });
    frame.append(table);
    body.replaceChildren(rows.length ? frame : status("No records match this view."));
}

function field(label, name, value, {type="text",min,max,choices} = {}) {
    const outer=element("label",label);
    const input=element(choices ? "select" : "input");
    input.name=name;
    input.required=true;
    if (choices) {
        for (const [key,name] of choices) {
            const option=element("option",name);
            option.value=key;
            input.append(option);
        }
    } else {
        input.type=type;
        if(min!==undefined) input.min=min;
        if(max!==undefined) input.max=max;
    }
    input.value=value;
    outer.append(input);
    return outer;
}

function editForm(host, title, inputs, save) {
    const form=element("form",undefined,"panel editor");
    const fields=element("div",undefined,"editor-fields");
    fields.append(...inputs);
    const actions=element("div",undefined,"form-actions");
    const submit=element("button","Save changes","primary");
    submit.type="submit";
    const feedback=status("");
    actions.append(submit,button("Close",()=>host.replaceChildren()));
    form.append(element("h2",title),fields,actions,feedback);
    host.replaceChildren(form);
    form.addEventListener("submit",async event=>{
        event.preventDefault();
        submit.disabled=true;
        feedback.classList.remove("error");
        feedback.textContent="Saving changes…";
        try {
            const values=Object.fromEntries(new FormData(form));
            const result=await save(values);
            feedback.textContent=result.message || "Saved.";
        } catch(error) {
            feedback.classList.add("error");
            feedback.textContent=error.message;
        } finally { submit.disabled=false; }
    });
    form.scrollIntoView({block:"nearest"});
}

function guildEditor(host, row, refresh) {
    editForm(host,"Guild policy · "+row.name,[
        field("Member limit","member_limit",row.member_limit,{type:"number",min:Math.max(5,row.member_count),max:1000}),
        field("Minimum Collector Level","minimum_level",row.minimum_level,{type:"number",min:1,max:100}),
        field("Recruitment","is_recruiting",row.is_recruiting,{choices:[[1,"Open"],[0,"Closed"]]})
    ],async values=>{
        for (const key in values) values[key]=Number(values[key]);
        const result=await api("/api/admin/guilds/"+encodeURIComponent(row.guild_id),{method:"PATCH",body:JSON.stringify(values)});
        Object.assign(row,values);
        refresh();
        return result;
    });
}

const rarities=["COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC"];

function packEditor(host,row,refresh) {
    const address="/api/admin/packs/" + encodeURIComponent(row.pack_id);
    const settings=element("div");
    const odds=element("div");
    host.replaceChildren(settings,odds);
    editForm(settings,"Banner settings · "+row.name,[
        field("Display name","name",row.name),
        field("Price","price",row.price,{type:"number",min:0}),
        field("Availability","is_active",row.is_active,{choices:[[1,"Active"],[0,"Inactive"]]})
    ],async values=>{
        values.price=Number(values.price);
        values.is_active=Number(values.is_active);
        const result=await api(address,{method:"PATCH",body:JSON.stringify(values)});
        Object.assign(row,values);
        refresh();
        return result;
    });
    const fields=[];
    const forPack=row.odds||[];
    for(let slot=1;slot<=row.cards_per_pack;slot++){
        const group=element("fieldset");
        const slots=element("div",undefined,"odds-grid");
        group.append(element("legend","Slot "+slot),slots);
        for(const rarity of rarities){
            const weight=forPack.find(item=>item.slot_index===slot&&item.rarity===rarity)?.weight??0;
            slots.append(field(humanize(rarity),slot+"_"+rarity,weight,{type:"number",min:0,max:1000000}));
        }
        fields.push(group);
    }
    editForm(odds,"Per-slot rarity weights",fields,async values=>{
        const slots=Array.from({length:row.cards_per_pack},(_,index)=>{
            const slot_index=index+1;
            const weights=Object.fromEntries(rarities.map(rarity=>[rarity,Number(values[slot_index+"_"+rarity])]));
            if(!Object.values(weights).some(value=>value>0)) throw new Error("Add at least one positive weight in slot "+slot_index+".");
            return {slot_index,weights};
        });
        const result=await api(address+"/odds",{method:"PUT",body:JSON.stringify({slots})});
        row.odds=[];
        for(const slot of slots) for(const [rarity,weight] of Object.entries(slot.weights))
            if(weight>0) row.odds.push({slot_index:slot.slot_index,rarity,weight});
        return result;
    });
}

async function listView(section,host) {
    const data=await api("/api/admin/"+section.api);
    if (section.object) {
        const config=data[section.object];
        if(!config) throw new Error("Settings unavailable.");
        const rows=Object.entries(config).filter(([key,value])=>!key.endsWith("_id")&&typeof value!=="object")
            .map(([setting,value])=>({setting:humanize(setting),value}));
        table({...section,cols:["setting","value"]},rows,host);
        return;
    }
    let rows=data[section.rows];
    if(!Array.isArray(rows) && section.id==="audit") rows=data.logs||data.items;
    if(!Array.isArray(rows)) rows=[];
    const panels=element("div",undefined,"controls");
    const filter=element("input");
    filter.type="search";
    filter.placeholder="Filter "+section.name.toLowerCase();
    filter.setAttribute("aria-label",filter.placeholder);
    const summary=element("p",undefined,"notice");
    const listing=element("div");
    const editor=element("div");
    let matches=rows;
    const exportButton=button("Export CSV",()=>exportCSV(section,matches));
    panels.append(filter,exportButton);
    host.replaceChildren(panels,summary,listing,editor);
    const update=()=>{
        const q=filter.value.trim().toLowerCase();
        matches=rows.filter(row=>section.cols.some(key=>String(row[key]??"").toLowerCase().includes(q)));
        exportButton.disabled=!matches.length;
        summary.textContent=number.format(matches.length)+" records";
        table(section,matches,listing,section.editor ? row=>{
            if(section.editor==="guild") guildEditor(editor,row,update);
            if(section.editor==="pack") packEditor(editor,row,update);
        } : null);
    };
    filter.addEventListener("input",update);
    update();
}

async function liveSettings(host) {
    let result;
    try { result=await api("/api/admin/operations/settings"); }
    catch(error) {
        if(error.status===404) {
            host.replaceChildren(status("Live switches are pending server activation. Existing game settings are still available."),link("Open Game Operations","/admin/operations"));
            return;
        }
        throw error;
    }
    const form=element("form",undefined,"panel");
    const flags=new Map();
    let group;
    for(const control of result.controls) {
        if(group!==control.group) {
            group=control.group;
            form.append(element("h2",group));
        }
        const label=element("label",undefined,"toggle");
        const copy=element("span");
        copy.append(element("strong",control.name),element("small",control.description));
        const check=element("input");
        check.type="checkbox";
        check.checked=control.enabled;
        label.append(copy,check);
        form.append(label);
        flags.set(control.key,{check,enabled:control.enabled});
    }
    const actions=element("div",undefined,"form-actions");
    const save=element("button","Save live settings","primary");
    save.type="submit";
    const feedback=status("");
    const changed=()=>Object.fromEntries([...flags].filter(([,entry])=>entry.check.checked!==entry.enabled)
        .map(([key,entry])=>[key,entry.check.checked]));
    const update=()=>{save.disabled=Object.keys(changed()).length===0;};
    form.addEventListener("change",update);
    form.addEventListener("submit",async event=>{
        event.preventDefault();
        const controls=changed();
        save.disabled=true;
        for(const {check} of flags.values()) check.disabled=true;
        try{
            const response=await api("/api/admin/operations/settings",{method:"PATCH",body:JSON.stringify({
                revision:result.revision,controls
            })});
            result.revision=response.revision;
            for(const [key,enabled] of Object.entries(controls)) flags.get(key).enabled=enabled;
            feedback.classList.remove("error");
            feedback.textContent=response.message||"Saved.";
        }catch(error){
            feedback.classList.add("error");
            feedback.textContent=error.message;
        }finally{
            for(const {check} of flags.values()) check.disabled=false;
            update();
        }
    });
    actions.append(save,button("Reload settings",render));
    form.append(actions,feedback);
    update();
    host.replaceChildren(form);
}

async function render() {
    const request=++renderVersion;
    const section=readSection();
    renderNav(section);
    const content=element("div");
    stage.replaceChildren(pageHeader(section),content);
    if(section.id==="overview") return overview(content);
    if(!section.api&&!section.custom) {
        const panel=element("article",undefined,"panel");
        panel.append(link("Open "+section.name,section.href||legacy(section.legacy),"button primary"));
        content.append(panel);
        return;
    }
    content.append(status("Loading…"));
    try {
        if(section.custom==="live") await liveSettings(content);
        else await listView(section,content);
    } catch(error) {
        if(request!==renderVersion) return;
        if(error.status===401) { showSignIn("Your session expired. Sign in again.");return; }
        content.replaceChildren(status(error.message,true));
        if(section.legacy) content.append(link("Open full tools",legacy(section.legacy)));
    }
}

function showSignIn(message="",locked=false) {
    $("boot").hidden=true;
    $("app").hidden=true;
    $("signin").hidden=false;
    $("login-feedback").textContent=message;
    $("switch-account").hidden=!locked;
}

async function verifySession() {
    try {
        const result=await api("/api/admin/profile");
        $("operator").textContent=result.profile?.title || "Master Admin";
        $("boot").hidden=true;
        $("signin").hidden=true;
        $("app").hidden=false;
        await render();
    } catch(error) {
        showSignIn(error.status===401?"":error.status===403?"This account requires Master Admin Level 100.":error.message,
            error.status===403);
    }
}

$("login-form").addEventListener("submit",async event=>{
    event.preventDefault();
    const form=event.currentTarget;
    const submit=form.querySelector('[type="submit"]');
    submit.disabled=true;
    $("login-feedback").textContent="Signing in…";
    try {
        await api("/api/auth/login",{method:"POST",body:JSON.stringify(Object.fromEntries(new FormData(form)))});
        form.reset();
        await verifySession();
    } catch(error) {
        showSignIn(error.message);
    } finally {submit.disabled=false;}
});

async function signOut() {
    try { await api("/api/auth/logout",{method:"POST",body:"{}"}); }
    catch(error){ showSignIn(error.message,true);return; }
    stage.replaceChildren();
    showSignIn();
}
$("logout").addEventListener("click",signOut);
$("switch-account").addEventListener("click",signOut);
$("menu").addEventListener("click",()=>{
    const open=$("sidebar").classList.toggle("open");
    $("menu").setAttribute("aria-expanded",String(open));
});
$("nav-filter").addEventListener("input",()=>renderNav(readSection()));
addEventListener("hashchange",()=>{if(!$("app").hidden) render();});
verifySession();
