package com.carduniverse.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 13, 14);
    private static final int PANEL = Color.rgb(17, 29, 27);
    private static final int PANEL_LIGHT = Color.rgb(24, 39, 35);
    private static final int GOLD = Color.rgb(216, 184, 119);
    private static final int GOLD_BRIGHT = Color.rgb(244, 218, 164);
    private static final int TEXT = Color.rgb(240, 237, 224);
    private static final int MUTED = Color.rgb(157, 169, 158);
    private static final int GREEN = Color.rgb(115, 184, 139);
    private static final int RED = Color.rgb(224, 126, 111);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ApiClient api;
    private FrameLayout frame;
    private LinearLayout appRoot;
    private LinearLayout mainColumn;
    private LinearLayout content;
    private LinearLayout rail;
    private TextView pageTitle;
    private TextView accountLabel;
    private TextView currencyLabel;
    private final Map<String, TextView> navLabels = new LinkedHashMap<>();
    private JSONObject me;
    private JSONObject progression;
    private String activeTab = "home";
    private boolean registering = false;
    private boolean requestBusy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new ApiClient(this);
        configureWindow();
        showSplash();
        api.get("/api/me", result -> ui.post(() -> {
            if (result.ok()) {
                acceptMe(result.data);
                loadProgressionAndShow();
            } else {
                if (result.status == 0) showConnectionError(result.error);
                else showAuth(false, "");
            }
        }));
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void showSplash() {
        FrameLayout splash = new FrameLayout(this);
        splash.setBackground(gradient(BG, Color.rgb(17, 39, 31), GOLD, 0));
        LinearLayout center = new LinearLayout(this);
        center.setGravity(Gravity.CENTER);
        center.setOrientation(LinearLayout.VERTICAL);
        TextView sigil = text("✦", 42, GOLD_BRIGHT, true);
        sigil.setGravity(Gravity.CENTER);
        TextView brand = text("CARDUNIVERSE", 23, TEXT, true);
        brand.setLetterSpacing(0.22f);
        brand.setGravity(Gravity.CENTER);
        TextView subtitle = text("FIRSTLIGHT REALM  ·  ANDROID", 9, GOLD, true);
        subtitle.setLetterSpacing(0.2f);
        subtitle.setGravity(Gravity.CENTER);
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(GOLD));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(30), dp(30));
        sp.topMargin = dp(24);
        center.addView(sigil);
        center.addView(brand);
        center.addView(subtitle);
        center.addView(spinner, sp);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        splash.addView(center, cp);
        setContentView(splash);
    }

    private void acceptMe(JSONObject payload) {
        me = payload == null ? null : payload.optJSONObject("user");
        if (me != null) {
            try {
                me.put("wallet", payload.optJSONObject("wallet"));
                me.put("collection", payload.optJSONObject("collection"));
                me.put("packs", payload.optJSONArray("packs"));
            } catch (Exception ignored) {}
        }
    }

    private void loadProgressionAndShow() {
        if (me == null) {
            showAuth(false, "");
            return;
        }
        api.get("/api/progression", result -> ui.post(() -> {
            progression = result.ok() ? result.data : new JSONObject();
            showApp();
        }));
    }

    private void showConnectionError(String message) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(28), dp(22), dp(28), dp(22));
        panel.setBackgroundColor(BG);
        TextView icon = text("◌", 40, GOLD, true);
        icon.setGravity(Gravity.CENTER);
        TextView title = text("THE REALM IS UNREACHABLE", 17, TEXT, true);
        title.setGravity(Gravity.CENTER);
        TextView detail = text(message == null ? "Check your internet connection and reconnect." : message, 11, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(dp(10), dp(10), dp(10), dp(12));
        TextView retry = button("RECONNECT", true);
        retry.setOnClickListener(v -> {
            showSplash();
            api.get("/api/me", result -> ui.post(() -> {
                if (result.ok()) {
                    acceptMe(result.data);
                    loadProgressionAndShow();
                } else if (result.status == 0) showConnectionError(result.error);
                else showAuth(false, "");
            }));
        });
        panel.addView(icon);
        panel.addView(title);
        panel.addView(detail);
        panel.addView(retry, new LinearLayout.LayoutParams(dp(180), dp(42)));
        setContentView(panel);
    }

    // Native account entry — no website landing page or embedded browser.
    private void showAuth(boolean register, String error) {
        registering = register;
        requestBusy = false;
        FrameLayout root = new FrameLayout(this);
        root.setBackground(gradient(BG, Color.rgb(16, 34, 28), Color.rgb(75, 93, 68), 0));

        LinearLayout split = new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        split.setPadding(dp(18), dp(14), dp(18), dp(14));

        LinearLayout world = new LinearLayout(this);
        world.setOrientation(LinearLayout.VERTICAL);
        world.setGravity(Gravity.CENTER_VERTICAL);
        world.setPadding(dp(20), dp(14), dp(18), dp(14));
        TextView eyebrow = text("A NEW CHAPTER BEGINS", 9, GOLD, true);
        eyebrow.setLetterSpacing(0.24f);
        TextView title = text("ENTER THE\nFIRSTLIGHT REALM", 29, TEXT, true);
        title.setLineSpacing(0, 0.94f);
        TextView copy = text("Collect rare cards. Build your formation.\nExplore a living universe made for collectors.", 11, MUTED, false);
        copy.setLineSpacing(dp(4), 1f);

        FrameLayout artwork = new FrameLayout(this);
        artwork.setBackground(gradient(Color.rgb(35, 65, 49), Color.rgb(13, 25, 22), Color.rgb(120, 105, 71), dp(18)));
        TextView moon = text("◉", 68, GOLD_BRIGHT, true);
        moon.setAlpha(0.8f);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(100), dp(100), Gravity.TOP | Gravity.END);
        mp.setMargins(0, dp(4), dp(24), 0);
        artwork.addView(moon, mp);
        TextView crest = text("✦", 62, GOLD_BRIGHT, true);
        FrameLayout.LayoutParams crestP = new FrameLayout.LayoutParams(dp(100), dp(100), Gravity.CENTER);
        artwork.addView(crest, crestP);
        LinearLayout artCaption = new LinearLayout(this);
        artCaption.setOrientation(LinearLayout.VERTICAL);
        artCaption.setPadding(dp(15), 0, 0, dp(13));
        TextView c1 = text("THE ARCHIVE IS OPEN", 8, GOLD, true);
        TextView c2 = text("Your collection awaits.", 12, TEXT, true);
        artCaption.addView(c1);
        artCaption.addView(c2);
        FrameLayout.LayoutParams cap = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        artwork.addView(artCaption, cap);

        LinearLayout facts = new LinearLayout(this);
        facts.setOrientation(LinearLayout.HORIZONTAL);
        facts.setGravity(Gravity.CENTER_VERTICAL);
        TextView f1 = text("✧  COLLECT", 8, GOLD_BRIGHT, true);
        TextView f2 = text("⚔  BATTLE", 8, GOLD_BRIGHT, true);
        TextView f3 = text("◈  DISCOVER", 8, GOLD_BRIGHT, true);
        facts.addView(f1, weightParams(1));
        facts.addView(f2, weightParams(1));
        facts.addView(f3, weightParams(1));

        world.addView(eyebrow);
        addGap(world, 9);
        world.addView(title);
        addGap(world, 10);
        world.addView(copy);
        addGap(world, 14);
        world.addView(artwork, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        addGap(world, 12);
        world.addView(facts);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_VERTICAL);
        form.setPadding(dp(22), dp(18), dp(22), dp(18));
        form.setBackground(gradient(Color.rgb(24, 35, 30), Color.rgb(13, 22, 19), Color.rgb(119, 102, 67), dp(18)));

        TextView mini = text("✦  CARDUNIVERSE", 9, GOLD, true);
        mini.setLetterSpacing(0.14f);
        TextView heading = text(register ? "Create your account" : "Welcome back", 22, TEXT, true);
        TextView hint = text(register ? "Begin your collection in the Firstlight Realm." : "Sign in to return to your collection.", 10, MUTED, false);
        EditText nameField = editField("Display name", false);
        EditText emailField = editField("Email address", false);
        emailField.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText passwordField = editField("Password", true);
        passwordField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (!register) nameField.setVisibility(View.GONE);

        TextView errorText = text(error == null ? "" : error, 9, RED, false);
        errorText.setMaxLines(3);
        TextView submit = button(register ? "CREATE ACCOUNT" : "SIGN IN", true);
        TextView switchMode = text(register ? "Already have an account?  SIGN IN" : "New to the realm?  CREATE ACCOUNT", 9, GOLD_BRIGHT, true);
        switchMode.setGravity(Gravity.CENTER);
        switchMode.setPadding(0, dp(11), 0, dp(5));
        switchMode.setOnClickListener(v -> showAuth(!register, ""));

        TextView security = text("SECURE SIGN-IN  ·  YOUR GAME DATA STAYS SAVED", 7, MUTED, true);
        security.setGravity(Gravity.CENTER);
        security.setLetterSpacing(0.05f);

        form.addView(mini);
        addGap(form, 13);
        form.addView(heading);
        addGap(form, 4);
        form.addView(hint);
        addGap(form, 13);
        form.addView(nameField, fieldParams());
        form.addView(emailField, fieldParams());
        form.addView(passwordField, fieldParams());
        if (!register) addGap(form, 2);
        form.addView(errorText);
        addGap(form, 7);
        form.addView(submit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        form.addView(switchMode);
        addGap(form, 5);
        form.addView(security);

        submit.setOnClickListener(v -> {
            if (requestBusy) return;
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString();
            String displayName = nameField.getText().toString().trim();
            if (email.isEmpty() || password.isEmpty() || (register && displayName.length() < 2)) {
                errorText.setText(register ? "Enter your display name, email and password." : "Enter your email and password.");
                return;
            }
            if (register && password.length() < 10) {
                errorText.setText("Password must contain at least 10 characters.");
                return;
            }
            requestBusy = true;
            submit.setText("CONNECTING...");
            JSONObject payload = new JSONObject();
            try {
                payload.put("email", email);
                payload.put("password", password);
                if (register) payload.put("displayName", displayName);
            } catch (Exception ignored) {}
            api.post(register ? "/api/auth/register" : "/api/auth/login", payload, result -> ui.post(() -> {
                requestBusy = false;
                if (result.ok() && api.hasSession()) {
                    hideKeyboard();
                    showSplash();
                    api.get("/api/me", meResult -> ui.post(() -> {
                        if (meResult.ok()) {
                            acceptMe(meResult.data);
                            loadProgressionAndShow();
                        } else {
                            showAuth(false, meResult.error);
                        }
                    }));
                } else {
                    submit.setText(register ? "CREATE ACCOUNT" : "SIGN IN");
                    errorText.setText(result.error == null || result.error.isEmpty() ? "Unable to sign in. Please try again." : result.error);
                }
            }));
        });

        split.addView(world, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams formParams = new LinearLayout.LayoutParams(dp(330), ViewGroup.LayoutParams.MATCH_PARENT);
        formParams.leftMargin = dp(12);
        split.addView(form, formParams);
        root.addView(split, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    private void showApp() {
        appRoot = new LinearLayout(this);
        appRoot.setOrientation(LinearLayout.HORIZONTAL);
        appRoot.setBackgroundColor(BG);

        rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(dp(7), dp(9), dp(7), dp(8));
        rail.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        rail.setBackground(gradient(Color.rgb(17, 29, 25), Color.rgb(9, 17, 15), Color.rgb(49, 65, 51), 0));
        appRoot.addView(rail, new LinearLayout.LayoutParams(dp(82), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView emblem = text("✦", 25, GOLD_BRIGHT, true);
        emblem.setGravity(Gravity.CENTER);
        rail.addView(emblem, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        TextView brand = text("CU", 8, GOLD, true);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(0.18f);
        rail.addView(brand, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
        addDivider(rail, true);

        ScrollView navScroll = new ScrollView(this);
        navScroll.setFillViewport(false);
        LinearLayout navStack = new LinearLayout(this);
        navStack.setOrientation(LinearLayout.VERTICAL);
        String[][] items = {
                {"home", "⌂", "HOME"},
                {"collection", "▧", "CARDS"},
                {"binders", "▤", "BINDERS"},
                {"formation", "♟", "TEAM"},
                {"evolution", "✧", "EVOLVE"},
                {"achievements", "✦", "RECORDS"},
                {"progression", "⌁", "REWARDS"},
                {"events", "✺", "EVENTS"},
                {"leaderboards", "▥", "RANKINGS"},
                {"ranked", "♛", "RANKED"},
                {"summon", "✧", "SUMMON"},
                {"adventure", "⚔", "ADVENTURE"},
                {"dungeons", "⌘", "DUNGEON"},
                {"raids", "♜", "RAIDS"},
                {"arena", "◈", "ARENA"},
                {"trades", "⇄", "TRADES"},
                {"missions", "☷", "MISSIONS"},
                {"shop", "♢", "SHOP"},
                {"inbox", "✉", "INBOX"},
                {"guild", "♜", "GUILD"},
                {"settings", "⚙", "SETTINGS"}
        };
        for (String[] item : items) addNavItem(navStack, item[0], item[1], item[2]);
        if ("admin".equalsIgnoreCase(me.optString("role"))) {
            addDivider(navStack, true);
            addNavItem(navStack, "admin", "♛", "ADMIN");
        }
        navScroll.addView(navStack);
        rail.addView(navScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mainColumn = new LinearLayout(this);
        mainColumn.setOrientation(LinearLayout.VERTICAL);
        mainColumn.setPadding(dp(12), dp(9), dp(14), dp(8));
        appRoot.addView(mainColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), 0, dp(4), dp(6));
        pageTitle = text("REALM OVERVIEW", 13, TEXT, true);
        pageTitle.setLetterSpacing(0.09f);
        top.addView(pageTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        accountLabel = text(me.optString("display_name", "Collector"), 9, GOLD_BRIGHT, true);
        accountLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        currencyLabel = text(walletLine(), 8, MUTED, true);
        currencyLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout account = new LinearLayout(this);
        account.setOrientation(LinearLayout.VERTICAL);
        account.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        account.addView(accountLabel);
        account.addView(currencyLabel);
        TextView avatar = text(initials(me.optString("display_name", "CU")), 11, BG, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(gradient(GOLD_BRIGHT, GOLD, GOLD, dp(24)));
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(dp(32), dp(32));
        avp.leftMargin = dp(10);
        right.addView(account);
        right.addView(avatar, avp);
        top.addView(right);
        mainColumn.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        addDivider(mainColumn, false);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(3), dp(11), dp(3), dp(22));
        scroller.addView(content);
        mainColumn.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(appRoot);
        refreshNav();
        renderTab("home");
    }

    private void addNavItem(LinearLayout target, String key, String icon, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(2), dp(7), dp(2), dp(7));
        item.setBackground(gradient(PANEL, PANEL, Color.TRANSPARENT, dp(9)));
        TextView glyph = text(icon, 17, MUTED, true);
        glyph.setGravity(Gravity.CENTER);
        TextView name = text(label, 6, MUTED, true);
        name.setGravity(Gravity.CENTER);
        name.setLetterSpacing(0.03f);
        item.addView(glyph);
        item.addView(name);
        item.setOnClickListener(v -> renderTab(key));
        target.addView(item, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(47)));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        View spacer = new View(this);
        target.addView(spacer, gap);
        navLabels.put(key, name);
    }

    private void refreshNav() {
        for (Map.Entry<String, TextView> entry : navLabels.entrySet()) {
            TextView label = entry.getValue();
            boolean active = entry.getKey().equals(activeTab);
            label.setTextColor(active ? GOLD_BRIGHT : MUTED);
            View parent = (View) label.getParent();
            if (parent != null) parent.setBackground(gradient(
                    active ? Color.rgb(54, 61, 43) : PANEL,
                    active ? Color.rgb(30, 46, 34) : PANEL,
                    active ? GOLD : Color.TRANSPARENT, dp(9)));
        }
    }

    private void renderTab(String tab) {
        activeTab = tab;
        refreshNav();
        String title = tab.toUpperCase().replace("_", " ");
        if (pageTitle != null) pageTitle.setText(title.equals("HOME") ? "REALM OVERVIEW" : title);
        if (content == null) return;
        content.removeAllViews();
        if ("home".equals(tab)) {
            renderHome();
            return;
        }
        TextView eyebrow = text("FIRSTLIGHT REALM  /  " + title, 8, GOLD, true);
        eyebrow.setLetterSpacing(0.14f);
        content.addView(eyebrow);
        addGap(content, 5);
        content.addView(text(screenHeading(tab), 23, TEXT, true));
        content.addView(text(screenDescription(tab), 10, MUTED, false));
        addGap(content, 14);
        renderLoading();
        String path = endpointFor(tab);
        if (path == null) {
            showNotice("This section is not available for this account.");
            return;
        }
        api.get(path, result -> ui.post(() -> {
            if (!tab.equals(activeTab) || content == null) return;
            content.removeAllViews();
            content.addView(eyebrow);
            addGap(content, 5);
            content.addView(text(screenHeading(tab), 23, TEXT, true));
            content.addView(text(screenDescription(tab), 10, MUTED, false));
            addGap(content, 14);
            if (!result.ok()) {
                showNotice(result.error == null ? "Unable to load this section." : result.error);
                TextView retry = button("TRY AGAIN", false);
                retry.setOnClickListener(v -> renderTab(tab));
                content.addView(retry, new LinearLayout.LayoutParams(dp(140), dp(38)));
                return;
            }
            if ("missions".equals(tab)) {
                api.get("/api/daily-challenges", dailyResult -> ui.post(() -> {
                    if (!"missions".equals(activeTab) || content == null) return;
                    if (dailyResult.ok()) {
                        try { result.data.put("daily_challenges", dailyResult.data.optJSONArray("challenges")); }
                        catch (Exception ignored) {}
                    }
                    renderModule(tab, result.data);
                }));
            } else if ("events".equals(tab)) {
                api.get("/api/events/login", loginResult -> ui.post(() -> {
                    if (!"events".equals(activeTab) || content == null) return;
                    if (loginResult.ok()) {
                        try { result.data.put("login_event", loginResult.data); }
                        catch (Exception ignored) {}
                    }
                    renderModule(tab, result.data);
                }));
            } else {
                renderModule(tab, result.data);
            }
        }));
    }

    private void renderHome() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(18), dp(15), dp(18), dp(15));
        hero.setBackground(gradient(Color.rgb(41, 67, 48), Color.rgb(17, 33, 27), Color.rgb(143, 116, 73), dp(16)));

        LinearLayout heroCopy = new LinearLayout(this);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        heroCopy.addView(text("WELCOME TO FIRSTLIGHT", 8, GOLD_BRIGHT, true));
        addGap(heroCopy, 5);
        heroCopy.addView(text("The realm is yours, " + me.optString("display_name", "Collector") + ".", 20, TEXT, true));
        heroCopy.addView(text("Your next discovery is waiting beyond the archive.", 9, MUTED, false));
        TextView level = text("COLLECTOR LEVEL  " + me.optInt("collector_level", 1), 8, GOLD_BRIGHT, true);
        level.setPadding(0, dp(8), 0, 0);
        heroCopy.addView(level);
        hero.addView(heroCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout heroMark = new LinearLayout(this);
        heroMark.setGravity(Gravity.CENTER);
        heroMark.setBackground(gradient(Color.rgb(78, 76, 49), Color.rgb(36, 49, 34), Color.rgb(172, 144, 91), dp(14)));
        TextView star = text("✦", 40, GOLD_BRIGHT, true);
        star.setGravity(Gravity.CENTER);
        heroMark.addView(star);
        LinearLayout.LayoutParams hmp = new LinearLayout.LayoutParams(dp(86), dp(86));
        hmp.leftMargin = dp(12);
        hero.addView(heroMark, hmp);
        content.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addGap(content, 12);
        TextView statTitle = text("YOUR COLLECTION AT A GLANCE", 8, GOLD, true);
        statTitle.setLetterSpacing(0.12f);
        content.addView(statTitle);
        addGap(content, 7);

        JSONObject wallet = me.optJSONObject("wallet");
        JSONObject collection = me.optJSONObject("collection");
        if (wallet == null) wallet = new JSONObject();
        if (collection == null) collection = new JSONObject();

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard("LEVEL", String.valueOf(me.optInt("collector_level", 1)), "Collector rank"), weightParams(1));
        stats.addView(statCard("UNIQUE CARDS", formatNumber(collection.optLong("unique_cards")), "In your archive"), weightParams(1));
        stats.addView(statCard("GOLD", formatNumber(wallet.optLong("coins")), "Available coins"), weightParams(1));
        stats.addView(statCard("GEMS", formatNumber(wallet.optLong("gems")), "Premium currency"), weightParams(1));
        content.addView(stats);
        addGap(content, 13);
        LinearLayout dailyReward = sectionPanel();
        dailyReward.setOrientation(LinearLayout.HORIZONTAL);
        dailyReward.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout dailyCopy = new LinearLayout(this);
        dailyCopy.setOrientation(LinearLayout.VERTICAL);
        dailyCopy.addView(text("DAILY LOGIN REWARD", 8, GOLD, true));
        dailyCopy.addView(text("Claim one Starter Pack and 20 Gold each day.", 8, MUTED, false));
        dailyReward.addView(dailyCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView dailyClaim = button("CLAIM", true);
        dailyClaim.setPadding(dp(14), 0, dp(14), 0);
        dailyClaim.setOnClickListener(v -> confirmAction("Claim daily reward?",
                "You will receive one Starter Pack and 20 Gold if today's reward is still available.", () -> {
            dailyClaim.setEnabled(false);
            api.post("/api/daily/claim", new JSONObject(), result -> ui.post(() -> {
                dailyClaim.setEnabled(true);
                if (!result.ok()) {
                    showResultDialog("Daily reward unavailable", result.error);
                    return;
                }
                showResultDialog("✦ DAILY REWARD CLAIMED", result.data.optString("message",
                        "One Starter Pack and 20 Gold have been added."));
                refreshAccount(() -> renderTab("home"));
            }));
        }));
        dailyReward.addView(dailyClaim);
        content.addView(dailyReward, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addGap(content, 14);

        TextView featureTitle = text("CHOOSE YOUR NEXT ADVENTURE", 8, GOLD, true);
        featureTitle.setLetterSpacing(0.12f);
        content.addView(featureTitle);
        addGap(content, 7);

        String[][] features = {
                {"collection", "▧", "Card Archive", "Browse cards and owned copies"},
                {"summon", "✧", "Summon Hall", "View your available packs"},
                {"adventure", "⚔", "Adventure", "Explore chapters and stages"},
                {"arena", "◈", "Battle Arena", "View your battle status"},
                {"missions", "☷", "Missions", "Track goals and daily rewards"},
                {"shop", "♢", "Realm Shop", "Browse available items"}
        };
        LinearLayout row = null;
        for (int i = 0; i < features.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                if (i > 0) addGap(content, 7);
            }
            final String key = features[i][0];
            row.addView(featureCard(features[i][1], features[i][2], features[i][3], () -> renderTab(key)), weightParams(1));
            if (i % 3 != 2) {
                View gap = new View(this);
                row.addView(gap, new LinearLayout.LayoutParams(dp(7), 1));
            }
        }
        addGap(content, 12);
        LinearLayout lower = new LinearLayout(this);
        lower.setOrientation(LinearLayout.HORIZONTAL);
        lower.addView(featureCard("✉", "Inbox", "Announcements and gifts", () -> renderTab("inbox")), weightParams(1));
        View gap = new View(this);
        lower.addView(gap, new LinearLayout.LayoutParams(dp(7), 1));
        lower.addView(featureCard("⚙", "Player Settings", "Privacy, support and account", () -> renderTab("settings")), weightParams(1));
        content.addView(lower);
    }

    private void renderModule(String tab, JSONObject data) {
        switch (tab) {
            case "collection": renderCollection(data); break;
            case "binders": renderBinders(data); break;
            case "formation": renderFormation(data); break;
            case "evolution": renderEvolution(data); break;
            case "dungeons": renderDungeons(data); break;
            case "raids": renderRaids(data); break;
            case "trades": renderTrades(data); break;
            case "achievements": renderAchievements(data); break;
            case "progression": renderProgression(data); break;
            case "events": renderEvents(data); break;
            case "leaderboards": renderLeaderboards(data); break;
            case "ranked": renderRanked(data); break;
            case "summon": renderPacks(data); break;
            case "adventure": renderAdventure(data); break;
            case "arena": renderArena(data); break;
            case "missions": renderMissions(data); break;
            case "shop": renderShop(data); break;
            case "inbox": renderInbox(data); break;
            case "guild": renderGuild(data); break;
            case "settings": renderSettings(data); break;
            case "admin": renderAdmin(data); break;
            default: renderObjectSummary(data, "REALM DATA");
        }
    }

    
    private void renderPacks(JSONObject data) {
        JSONArray packs = data.optJSONArray("packs");
        if (packs == null) { showNotice("No summon packs are available."); return; }
        boolean unlimited = data.optBoolean("unlimited");
        content.addView(statCard("AVAILABLE PACK TYPES", String.valueOf(packs.length()),
                unlimited ? "Master Admin inventory" : "Your current summon inventory"));
        addGap(content, 10);
        for (int i = 0; i < packs.length(); i++) {
            JSONObject pack = packs.optJSONObject(i);
            if (pack == null) continue;
            String slug = pack.optString("slug", "");
            String name = pack.optString("name", "Summon pack");
            int quantity = pack.optInt("quantity");
            String currency = pack.optString("currency", "COINS");
            long price = pack.optLong("price");
            LinearLayout card = sectionPanel();
            card.addView(text(name, 13, TEXT, true));
            card.addView(text(pack.optString("description",
                    pack.optInt("cards_per_pack") + " cards per pack"), 8, MUTED, false));
            addGap(card, 5);
            card.addView(text(pack.optInt("cards_per_pack") + " CARDS  ·  " + currency + " " + formatNumber(price),
                    8, GOLD_BRIGHT, true));
            card.addView(text(unlimited ? "MASTER ADMIN · PACKS DO NOT DECREASE"
                    : "INVENTORY  " + quantity + (pack.has("pity_count") ? "  ·  MYTHIC PITY " + pack.optInt("pity_count") : ""),
                    8, MUTED, true));
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            if (quantity > 0 || unlimited) {
                TextView open = button("OPEN PACK", true);
                open.setPadding(dp(16), 0, dp(16), 0);
                open.setOnClickListener(v -> confirmAction("Open " + name + "?",
                        "This opens one pack and adds its cards to your live collection.", () -> {
                    JSONObject request = new JSONObject();
                    try { request.put("requestId", newActionId()); } catch (Exception ignored) {}
                    open.setEnabled(false);
                    open.setText("OPENING...");
                    api.post("/api/packs/" + slug + "/open", request, result -> ui.post(() -> {
                        open.setEnabled(true);
                        open.setText("OPEN PACK");
                        if (!result.ok()) {
                            showResultDialog("Pack could not be opened", result.error);
                            return;
                        }
                        String message = formatPackReveal(result.data.optJSONArray("cards"));
                        showResultDialog("✦  PACK REVEAL", message);
                        refreshAccount(() -> renderTab("summon"));
                    }));
                }));
                actions.addView(open);
            } else if ("COINS".equalsIgnoreCase(currency) && price > 0) {
                TextView buy = button("BUY PACK  ·  " + formatNumber(price) + " GOLD", false);
                buy.setPadding(dp(12), 0, dp(12), 0);
                buy.setOnClickListener(v -> confirmAction("Purchase " + name + "?",
                        "Spend " + formatNumber(price) + " Gold to add one pack to your inventory.", () -> {
                    buy.setEnabled(false);
                    api.post("/api/packs/" + slug + "/buy", new JSONObject(), result -> ui.post(() -> {
                        buy.setEnabled(true);
                        if (!result.ok()) {
                            showResultDialog("Purchase failed", result.error);
                            return;
                        }
                        showResultDialog("Pack added", "The pack has been added to your inventory.");
                        refreshAccount(() -> renderTab("summon"));
                    }));
                }));
                actions.addView(buy);
            } else {
                actions.addView(text("NO PACKS AVAILABLE", 7, MUTED, true));
            }
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            actionParams.topMargin = dp(8);
            card.addView(actions, actionParams);
            content.addView(card, rowParams());
        }
        if (packs.length() == 0) showNotice("No packs are currently available.");
    }
    private void renderArena(JSONObject data) {
        JSONObject rating = data.optJSONObject("rating");
        JSONObject season = data.optJSONObject("season");
        if (rating != null) {
            LinearLayout rated = sectionPanel();
            rated.addView(text("RANKED SEASON  ·  " + (season == null ? "ACTIVE" : season.optString("name", "ACTIVE")), 7, GOLD, true));
            rated.addView(text(rating.optString("tier", "BRONZE") + "  ·  " + rating.optInt("rating") + " RATING", 17, TEXT, true));
            rated.addView(text("W " + rating.optInt("wins") + "  ·  L " + rating.optInt("losses")
                    + "  ·  D " + rating.optInt("draws") + "  ·  STREAK " + rating.optInt("win_streak")
                    + "  ·  POSITION #" + rating.optInt("position"), 8, MUTED, true));
            content.addView(rated, rowParams());
        } else if (data.optBoolean("rankedHidden")) {
            showNotice("Ranked matchmaking and the public ranked ladder are disabled for administrator accounts.");
        } else if (data.optBoolean("rankedBanned")) {
            showNotice("Ranked access is restricted: " + data.optString("rankedBanReason", "moderator action"));
        }

        JSONObject active = data.optJSONObject("activeMatch");
        JSONObject queue = data.optJSONObject("queue");
        if (active != null) {
            LinearLayout match = sectionPanel();
            match.addView(text("LIVE ARENA MATCH  ·  " + active.optString("mode", "CASUAL"), 8, GOLD, true));
            match.addView(text("VS  " + active.optString("opponentName", "Opponent"), 19, TEXT, true));
            match.addView(text("TURN " + active.optInt("current_turn")
                    + "  ·  YOU " + active.optLong("myHp") + " HP"
                    + "  ·  OPPONENT " + active.optLong("opponentHp") + " HP", 9, GOLD_BRIGHT, true));
            content.addView(match, rowParams());
            if (active.optString("myMove", "").isEmpty()) {
                content.addView(text("CHOOSE YOUR MOVE", 8, GOLD, true));
                LinearLayout moves = new LinearLayout(this);
                moves.setOrientation(LinearLayout.HORIZONTAL);
                String[][] choices = {{"STRIKE", "⚔ STRIKE"}, {"GUARD", "◈ GUARD"}, {"FOCUS", "✦ FOCUS"}};
                for (String[] choice : choices) {
                    TextView move = button(choice[1], "STRIKE".equals(choice[0]));
                    move.setOnClickListener(v -> confirmAction("Use " + choice[0] + "?",
                            "Your move is locked for this turn once submitted.",
                            () -> submitArenaMove(active.optString("match_id"), choice[0])));
                    LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(40), 1f);
                    mp.rightMargin = dp(6);
                    moves.addView(move, mp);
                }
                content.addView(moves);
            } else {
                content.addView(text("YOUR MOVE: " + active.optString("myMove")
                        + (active.optBoolean("opponentSubmitted") ? "  ·  RESOLVING" : "  ·  WAITING FOR OPPONENT"),
                        9, GOLD_BRIGHT, true));
            }
            TextView refresh = button("REFRESH MATCH STATUS", false);
            refresh.setOnClickListener(v -> renderTab("arena"));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(180), dp(34));
            rp.topMargin = dp(8);
            content.addView(refresh, rp);
        } else if (queue != null) {
            LinearLayout waiting = sectionPanel();
            waiting.addView(text("MATCHMAKING IN PROGRESS", 9, GOLD, true));
            waiting.addView(text(queue.optString("mode", "CASUAL") + " QUEUE  ·  POWER "
                    + formatNumber(queue.optLong("power")), 13, TEXT, true));
            waiting.addView(text("Refresh the Arena screen to check whether an opponent has joined.", 8, MUTED, false));
            TextView cancel = button("CANCEL SEARCH", false);
            cancel.setOnClickListener(v -> confirmAction("Cancel matchmaking?", "You will leave the current arena queue.", this::cancelArenaQueue));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(150), dp(34));
            cp.topMargin = dp(8);
            waiting.addView(cancel, cp);
            content.addView(waiting, rowParams());
        } else {
            content.addView(text("FIND AN OPPONENT", 8, GOLD, true));
            addGap(content, 6);
            TextView casual = button("CASUAL MATCH  ·  FIND OPPONENT", true);
            casual.setOnClickListener(v -> joinArenaQueue("CASUAL"));
            content.addView(casual, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
            if (!data.optBoolean("rankedHidden") && !data.optBoolean("rankedBanned")) {
                TextView ranked = button("RANKED MATCH  ·  SEASON LADDER", false);
                ranked.setOnClickListener(v -> joinArenaQueue("RANKED"));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
                rp.topMargin = dp(7);
                content.addView(ranked, rp);
            }
            showNotice("Strike deals damage, Guard reduces incoming damage, and Focus charges your next action.");
        }

        JSONArray recent = data.optJSONArray("recent");
        if (recent != null && recent.length() > 0) {
            addGap(content, 8);
            content.addView(text("RECENT MATCHES", 8, GOLD, true));
            for (int i = 0; i < recent.length(); i++) {
                JSONObject match = recent.optJSONObject(i);
                if (match == null) continue;
                boolean winner = me != null && me.optString("user_id").equals(match.optString("winner_id"));
                String opponent = me != null && me.optString("user_id").equals(match.optString("player_one_id"))
                        ? match.optString("player_two_name") : match.optString("player_one_name");
                content.addView(dataRow(opponent, match.optString("mode", "CASUAL")
                        + "  ·  " + match.optString("completed_at", ""), winner ? "VICTORY" : "COMPLETED"), rowParams());
            }
        }
    }

    private void renderAdventure(JSONObject data) {
        JSONArray chapters = data.optJSONArray("chapters");
        if (chapters == null) { renderObjectSummary(data, "ADVENTURE PROGRESS"); return; }
        content.addView(statCard("COLLECTOR POWER", formatNumber(data.optLong("playerPower")), "Current expedition strength"));
        addGap(content, 9);
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            if (chapter == null) continue;
            LinearLayout block = sectionPanel();
            block.addView(text("CHAPTER " + (i + 1), 7, GOLD, true));
            block.addView(text(chapter.optString("title", "Unknown chapter"), 15, TEXT, true));
            block.addView(text(chapter.optString("description", ""), 9, MUTED, false));
            addGap(block, 7);
            JSONArray stages = chapter.optJSONArray("stages");
            if (stages != null) {
                for (int j = 0; j < stages.length(); j++) {
                    JSONObject stage = stages.optJSONObject(j);
                    if (stage == null) continue;
                    String slug = stage.optString("slug", "");
                    String title = stage.optString("title", "Stage");
                    String enemy = stage.optString("enemy_name", "Realm encounter");
                    boolean unlocked = stage.optBoolean("unlocked");
                    boolean cleared = stage.optBoolean("cleared");
                    String status = cleared ? "CLEARED" : (unlocked ? "READY" : "LOCKED");
                    String sub = enemy + "  ·  POWER " + formatNumber(stage.optLong("enemy_power"))
                            + "  ·  REWARD " + formatNumber(stage.optLong("reward_coins")) + " GOLD";
                    LinearLayout stageCard = dataRow(title, sub, status);
                    if (unlocked) {
                        TextView fight = button(cleared ? "REPLAY" : "BATTLE", true);
                        fight.setPadding(dp(14), 0, dp(14), 0);
                        fight.setOnClickListener(v -> confirmAction("Enter " + title + "?",
                                "Challenge " + enemy + ". The server calculates the battle result and awards eligible rewards.", () -> {
                            JSONObject payload = new JSONObject();
                            try { payload.put("requestId", newActionId()); } catch (Exception ignored) {}
                            fight.setEnabled(false);
                            fight.setText("FIGHTING...");
                            api.post("/api/adventure/" + slug + "/battle", payload, result -> ui.post(() -> {
                                fight.setEnabled(true);
                                fight.setText(cleared ? "REPLAY" : "BATTLE");
                                if (!result.ok()) {
                                    showResultDialog("Encounter unavailable", result.error);
                                    return;
                                }
                                JSONObject run = result.data.optJSONObject("run");
                                String resultName = run == null ? "BATTLE COMPLETE" : run.optString("result", "COMPLETE");
                                String details = result.data.optString("message", "The encounter has ended.");
                                if (run != null) {
                                    details += "\n\nEnemy: " + run.optString("enemy_name", enemy)
                                            + "\nYour power: " + formatNumber(run.optLong("player_power"))
                                            + "\nEnemy power: " + formatNumber(run.optLong("enemy_power"))
                                            + "\nXP: +" + formatNumber(run.optLong("reward_xp"))
                                            + "\nGold: +" + formatNumber(run.optLong("reward_coins"));
                                }
                                showResultDialog("VICTORY".equalsIgnoreCase(resultName) ? "✦ VICTORY" : "ENCOUNTER RESULT", details);
                                refreshAccount(() -> renderTab("adventure"));
                            }));
                        }));
                        LinearLayout.LayoutParams fightParams = new LinearLayout.LayoutParams(dp(100), dp(34));
                        fightParams.leftMargin = dp(8);
                        stageCard.addView(fight, fightParams);
                    }
                    LinearLayout.LayoutParams stageParams = rowParams();
                    block.addView(stageCard, stageParams);
                }
            }
            content.addView(block, rowParams());
        }
    }
    private void renderMissions(JSONObject data) {
        JSONArray missions = data.optJSONArray("missions");
        JSONArray challenges = data.optJSONArray("daily_challenges");
        if (missions == null && challenges == null) { renderObjectSummary(data, "MISSION STATUS"); return; }

        if (challenges != null) {
            content.addView(text("DAILY CHALLENGES", 8, GOLD, true));
            addGap(content, 5);
            for (int i = 0; i < challenges.length(); i++) {
                JSONObject challenge = challenges.optJSONObject(i);
                if (challenge == null) continue;
                String title = challenge.optString("title", "Daily challenge");
                int progress = challenge.optInt("progress");
                int target = challenge.optInt("target_count");
                boolean claimed = !challenge.isNull("claimed_at");
                LinearLayout card = sectionPanel();
                card.addView(text(title, 11, TEXT, true));
                card.addView(text(challenge.optString("description", "Daily challenge"), 8, MUTED, false));
                card.addView(text(progress + " / " + target + "  ·  REWARD "
                        + formatNumber(challenge.optLong("reward_coins")) + " GOLD  +  "
                        + formatNumber(challenge.optLong("reward_xp")) + " XP",
                        8, GOLD_BRIGHT, true));
                if (claimed) {
                    card.addView(text("REWARD CLAIMED", 7, GREEN, true));
                } else if (progress >= target && target > 0) {
                    TextView claim = button("CLAIM CHALLENGE REWARD", true);
                    claim.setOnClickListener(v -> claimChallenge(challenge.optString("slug", ""), claim));
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(205), dp(34));
                    cp.topMargin = dp(7);
                    card.addView(claim, cp);
                } else {
                    card.addView(text("KEEP GOING TO UNLOCK THE REWARD", 7, MUTED, true));
                }
                content.addView(card, rowParams());
            }
            addGap(content, 8);
        }

        if (missions != null) {
            content.addView(text("REALM MISSIONS", 8, GOLD, true));
            addGap(content, 5);
            for (int i = 0; i < missions.length(); i++) {
                JSONObject mission = missions.optJSONObject(i);
                if (mission == null) continue;
                String slug = mission.optString("slug", "");
                String title = mission.optString("title", mission.optString("name", "Mission"));
                String sub = mission.optString("description", mission.optString("period_type", "MISSION"));
                int progress = mission.optInt("progress");
                int target = mission.optInt("target_count");
                boolean claimed = !mission.isNull("claimed_at");
                LinearLayout card = sectionPanel();
                card.addView(text(title, 11, TEXT, true));
                card.addView(text(sub, 8, MUTED, false));
                String reward = formatNumber(mission.optLong("reward_coins")) + " GOLD  ·  "
                        + formatNumber(mission.optLong("reward_xp")) + " XP";
                if (mission.optInt("reward_pack_quantity") > 0) reward += "  ·  +" + mission.optInt("reward_pack_quantity") + " PACK";
                card.addView(text(progress + " / " + target + "  ·  " + reward, 8, GOLD_BRIGHT, true));
                if (claimed) card.addView(text("REWARD CLAIMED", 7, GREEN, true));
                else if (mission.optBoolean("completed") || (target > 0 && progress >= target)) {
                    TextView claim = button("CLAIM MISSION REWARD", true);
                    claim.setOnClickListener(v -> claimMission(slug, claim));
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(190), dp(34));
                    cp.topMargin = dp(7);
                    card.addView(claim, cp);
                }
                content.addView(card, rowParams());
            }
        }
        if ((missions == null || missions.length() == 0) && (challenges == null || challenges.length() == 0))
            showNotice("No active missions or daily challenges are available right now.");
    }
    private void renderShop(JSONObject data) {
        JSONArray items = data.optJSONArray("items");
        JSONObject wallet = data.optJSONObject("wallet");
        if (items == null) { renderObjectSummary(data, "SHOP INVENTORY"); return; }
        if (wallet != null) {
            content.addView(text("YOUR WALLET  ·  " + formatNumber(wallet.optLong("coins")) + " GOLD  ·  "
                    + formatNumber(wallet.optLong("gems")) + " GEMS  ·  " + formatNumber(wallet.optLong("shards")) + " SHARDS",
                    8, GOLD_BRIGHT, true));
            addGap(content, 8);
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String itemId = item.optString("item_id", "");
            String title = item.optString("title", item.optString("name", "Realm item"));
            String sub = item.optString("description", item.optString("item_type", "REALM ITEM"));
            String currency = item.optString("currency", "COINS");
            long price = item.optLong("price");
            String priceLabel = formatNumber(price) + " " + currency;
            if (!item.isNull("stock_remaining")) priceLabel += "  ·  STOCK " + item.optLong("stock_remaining");
            LinearLayout card = sectionPanel();
            card.addView(text(title, 11, TEXT, true));
            card.addView(text(sub, 8, MUTED, false));
            card.addView(text(priceLabel, 8, GOLD_BRIGHT, true));
            long stock = item.isNull("stock_remaining") ? -1 : item.optLong("stock_remaining");
            boolean alreadyOwned = item.optInt("cosmetic_owned") == 1;
            boolean canBuy = !alreadyOwned && (stock != 0) && price > 0 && !itemId.isEmpty();
            if (canBuy) {
                TextView buy = button("PURCHASE", true);
                final String purchaseTitle = title;
                final String purchasePriceLabel = priceLabel;
                final String purchaseItemId = itemId;
                buy.setOnClickListener(v -> confirmAction("Purchase " + purchaseTitle + "?",
                        "Cost: " + purchasePriceLabel + ". The server will validate your wallet, stock and purchase limits.", () -> {
                    JSONObject payload = new JSONObject();
                    try {
                        payload.put("itemId", purchaseItemId);
                        payload.put("requestId", newActionId());
                        payload.put("quantity", 1);
                    } catch (Exception ignored) {}
                    buy.setEnabled(false);
                    buy.setText("PROCESSING...");
                    api.post("/api/shop/purchase", payload, result -> ui.post(() -> {
                        buy.setEnabled(true);
                        buy.setText("PURCHASE");
                        if (!result.ok()) {
                            showResultDialog("Purchase failed", result.error);
                            return;
                        }
                        showResultDialog("✦ PURCHASE COMPLETE", result.data.optString("message", "Your item has been delivered."));
                        refreshAccount(() -> renderTab("shop"));
                    }));
                }));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(125), dp(34));
                bp.topMargin = dp(7);
                card.addView(buy, bp);
            } else if (alreadyOwned) {
                card.addView(text("ALREADY OWNED", 7, GREEN, true));
            } else if (stock == 0) {
                card.addView(text("OUT OF STOCK", 7, RED, true));
            }
            content.addView(card, rowParams());
        }
        if (items.length() == 0) showNotice("The shop has no active items right now.");
    }
    private void renderInbox(JSONObject data) {
        JSONArray items = data.optJSONArray("items");
        if (items == null) { showNotice("Your inbox is clear."); return; }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String mailId = item.optString("mail_id", "");
            String subject = item.optString("subject", "Realm message");
            String body = item.optString("body", "");
            JSONObject reward = item.optJSONObject("reward");
            boolean claimed = item.optBoolean("is_claimed");
            boolean hasReward = hasReward(reward);
            LinearLayout card = sectionPanel();
            card.addView(text(subject, 11, TEXT, true));
            TextView bodyText = text(body, 8, MUTED, false);
            bodyText.setMaxLines(4);
            card.addView(bodyText);
            if (hasReward) {
                card.addView(text("REWARD  ·  " + rewardSummary(reward), 8, GOLD_BRIGHT, true));
            }
            if (claimed) {
                card.addView(text("REWARD CLAIMED", 7, GREEN, true));
            } else if (hasReward) {
                TextView claim = button("CLAIM REWARD", true);
                claim.setOnClickListener(v -> claimMail(mailId, claim));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(135), dp(34));
                cp.topMargin = dp(7);
                card.addView(claim, cp);
            } else if (!item.optBoolean("is_read")) {
                TextView markRead = button("MARK AS READ", false);
                markRead.setOnClickListener(v -> markMailRead(mailId, markRead));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(130), dp(34));
                rp.topMargin = dp(7);
                card.addView(markRead, rp);
            } else {
                card.addView(text("READ", 7, MUTED, true));
            }
            content.addView(card, rowParams());
        }
        if (items.length() == 0) showNotice("Your inbox is clear. New messages will appear here.");
    }
    private void renderGuild(JSONObject data) {
        JSONObject guild = data.optJSONObject("guild");
        if (guild == null) {
            LinearLayout create = sectionPanel();
            create.addView(text("BUILD YOUR OWN GUILD", 9, GOLD, true));
            create.addView(text("Create a banner and invite other collectors into your realm.", 8, MUTED, false));
            TextView createButton = button("CREATE GUILD", true);
            createButton.setOnClickListener(v -> showCreateGuildDialog());
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(145), dp(36));
            cp.topMargin = dp(8);
            create.addView(createButton, cp);
            content.addView(create, rowParams());
        } else {
            LinearLayout home = sectionPanel();
            home.addView(text(guild.optString("emblem", "✦") + "  [" + guild.optString("tag", "GUILD") + "]", 8, GOLD, true));
            home.addView(text(guild.optString("name", "Guild"), 20, TEXT, true));
            home.addView(text(guild.optString("description", "Guild headquarters"), 9, MUTED, false));
            home.addView(text("LEVEL " + guild.optInt("guild_level") + "  ·  "
                    + guild.optInt("member_count") + "/" + guild.optInt("member_limit") + " MEMBERS"
                    + "  ·  " + formatNumber(guild.optLong("bank_coins")) + " GOLD TREASURY",
                    8, GOLD_BRIGHT, true));
            TextView contribute = button("CONTRIBUTE GOLD", true);
            contribute.setOnClickListener(v -> chooseGuildContribution());
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(170), dp(36));
            cp.topMargin = dp(8);
            home.addView(contribute, cp);
            content.addView(home, rowParams());
            JSONArray members = guild.optJSONArray("members");
            if (members != null) {
                content.addView(text("GUILD MEMBERS", 8, GOLD, true));
                for (int i = 0; i < members.length(); i++) {
                    JSONObject member = members.optJSONObject(i);
                    if (member == null) continue;
                    String sub = member.optString("role", "MEMBER") + "  ·  LEVEL " + member.optInt("collector_level")
                            + "  ·  CONTRIBUTION " + formatNumber(member.optLong("contribution"));
                    content.addView(dataRow(member.optString("display_name", "Collector"), sub, ""), rowParams());
                }
            }
            JSONArray activity = guild.optJSONArray("activity");
            if (activity != null && activity.length() > 0) {
                addGap(content, 6);
                content.addView(text("GUILD ACTIVITY", 8, GOLD, true));
                for (int i = 0; i < activity.length(); i++) {
                    JSONObject event = activity.optJSONObject(i);
                    if (event != null) content.addView(dataRow(humanize(event.optString("event_type", "GUILD EVENT")),
                            event.optString("display_name", "Guild member") + "  ·  " + event.optString("created_at", ""), ""), rowParams());
                }
            }
        }
        JSONArray directory = data.optJSONArray("directory");
        if (directory != null && directory.length() > 0) {
            addGap(content, 8);
            content.addView(text(guild == null ? "RECRUITING GUILDS" : "OTHER RECRUITING GUILDS", 8, GOLD, true));
            for (int i = 0; i < directory.length(); i++) {
                JSONObject entry = directory.optJSONObject(i);
                if (entry == null) continue;
                LinearLayout item = dataRow("[" + entry.optString("tag", "??") + "] " + entry.optString("name", "Guild"),
                        "LEADER " + entry.optString("leader_name", "Unknown") + "  ·  "
                                + entry.optInt("member_count") + "/" + entry.optInt("member_limit") + " MEMBERS",
                        "LV " + entry.optInt("guild_level"));
                TextView join = button("JOIN", false);
                join.setPadding(dp(12), 0, dp(12), 0);
                join.setOnClickListener(v -> confirmAction("Join [" + entry.optString("tag") + "]?",
                        "You can only belong to one guild at a time.", () -> joinGuild(entry.optString("tag"))));
                LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(dp(76), dp(32));
                jp.leftMargin = dp(7);
                item.addView(join, jp);
                content.addView(item, rowParams());
            }
        } else if (guild == null) {
            showNotice("There are no recruiting guilds right now. You can create the first one.");
        }
    }
    private void renderSettings(JSONObject data) {
        showPanelHeading("ACCOUNT & PRIVACY");
        TextView name = text(me.optString("display_name", "Collector"), 16, TEXT, true);
        content.addView(name);
        content.addView(text(me.optString("email", ""), 9, MUTED, false));
        addGap(content, 10);
        LinearLayout safetyTools = new LinearLayout(this);
        safetyTools.setOrientation(LinearLayout.HORIZONTAL);
        TextView editProfile = button("EDIT PROFILE", false);
        editProfile.setOnClickListener(v -> showProfileEditor());
        TextView reportPlayer = button("REPORT PLAYER", false);
        reportPlayer.setOnClickListener(v -> showReportPlayerDialog());
        safetyTools.addView(editProfile, new LinearLayout.LayoutParams(0, dp(36), 1f));
        LinearLayout.LayoutParams reportParams = new LinearLayout.LayoutParams(0, dp(36), 1f);
        reportParams.leftMargin = dp(7);
        safetyTools.addView(reportPlayer, reportParams);
        content.addView(safetyTools);
        addGap(content, 12);
        TextView supportTitle = text("OFFICIAL SUPPORT", 8, GOLD, true);
        content.addView(supportTitle);
        JSONArray contacts = data.optJSONArray("contacts");
        if (contacts != null && contacts.length() > 0) {
            for (int i = 0; i < contacts.length(); i++) {
                JSONObject c = contacts.optJSONObject(i);
                if (c == null) continue;
                LinearLayout contact = dataRow(c.optString("label", c.optString("platform", "Support")),
                        c.optString("description", "Contact the CardUniverse team"), c.optString("platform", "OPEN"));
                contact.setOnClickListener(v -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(c.optString("url")))); }
                    catch (Exception ex) { Toast.makeText(this, "Unable to open this support link.", Toast.LENGTH_SHORT).show(); }
                });
                content.addView(contact, rowParams());
            }
        } else {
            showNotice("Official support contacts have not been configured yet.");
        }
        addGap(content, 12);
        content.addView(text("BLOCKED PLAYERS", 8, GOLD, true));
        TextView blockedLoading = text("Loading your blocked list...", 8, MUTED, false);
        content.addView(blockedLoading);
        api.get("/api/blocks", result -> ui.post(() -> {
            if (!"settings".equals(activeTab) || content == null) return;
            content.removeView(blockedLoading);
            JSONArray blocked = result.ok() ? result.data.optJSONArray("blocked") : null;
            if (blocked == null || blocked.length() == 0) {
                content.addView(text(result.ok() ? "No players are blocked on this account." : result.error, 8, MUTED, false));
            } else {
                for (int i = 0; i < blocked.length(); i++) {
                    JSONObject player = blocked.optJSONObject(i);
                    if (player == null) continue;
                    final String playerId = player.optString("blocked_user_id");
                    LinearLayout blockedRow = dataRow(player.optString("display_name", "Collector"),
                            "Collector level " + player.optInt("collector_level"), "UNBLOCK");
                    blockedRow.setOnClickListener(v -> api.delete("/api/blocks/" + playerId, deleteResult -> ui.post(() -> {
                        if (deleteResult.ok()) renderTab("settings");
                        else Toast.makeText(this, deleteResult.error, Toast.LENGTH_LONG).show();
                    })));
                    content.addView(blockedRow, rowParams());
                }
            }
        }));
        addGap(content, 12);
        TextView logout = button("SIGN OUT", false);
        logout.setOnClickListener(v -> {
            JSONObject payload = new JSONObject();
            api.post("/api/auth/logout", payload, result -> ui.post(() -> {
                api.clearSession();
                me = null;
                progression = null;
                showAuth(false, "");
            }));
        });
        content.addView(logout, new LinearLayout.LayoutParams(dp(150), dp(40)));
        addGap(content, 8);
        content.addView(text("Your collector account, inventory and progress remain stored in the shared CardUniverse backend.", 8, MUTED, false));
    }


    private String starMarks(int rank) {
        StringBuilder value=new StringBuilder();
        for(int i=0;i<Math.max(1,Math.min(6,rank+1));i++) value.append("★");
        return value.toString();
    }

    private JSONObject payload(Object... pairs) {
        JSONObject body = new JSONObject();
        try { for (int i=0; i+1<pairs.length; i+=2) body.put(String.valueOf(pairs[i]), pairs[i+1]); }
        catch (Exception ignored) {}
        return body;
    }

    private void renderCollection(JSONObject data) {
        JSONArray cards=data.optJSONArray("cards");
        if(cards==null){showNotice("No card data was returned.");return;}
        int owned=0,copies=0;
        for(int i=0;i<cards.length();i++){JSONObject c=cards.optJSONObject(i);if(c!=null&&c.optInt("owned_count")>0){owned++;copies+=c.optInt("owned_count");}}
        LinearLayout stats=new LinearLayout(this);
        stats.addView(statCard("OWNED TYPES",String.valueOf(owned),"Unique cards"),weightParams(1));
        stats.addView(statCard("TOTAL COPIES",String.valueOf(copies),"Available archive"),weightParams(1));
        content.addView(stats);addGap(content,10);content.addView(text("YOUR CARD ARCHIVE",8,GOLD,true));
        for(int i=0;i<cards.length();i++){
            JSONObject c=cards.optJSONObject(i);if(c==null||c.optInt("owned_count")<=0)continue;
            LinearLayout row=dataRow(c.optString("name","Unknown card"),c.optString("rarity","COMMON")+" · "+c.optString("element","AETHER")+" · ×"+c.optInt("owned_count"),c.optString("card_code",""));
            row.setOnClickListener(v->showCardDetails(c));content.addView(row,rowParams());
        }
        addGap(content,12);content.addView(text("CARD CATALOGUE",8,GOLD,true));
        for(int i=0;i<cards.length();i++){
            JSONObject c=cards.optJSONObject(i);if(c==null||c.optInt("owned_count")>0)continue;
            LinearLayout row=dataRow(c.optString("name","Unknown card"),c.optString("rarity","COMMON")+" · "+c.optString("element","AETHER")+" · Not owned",c.optString("card_code",""));
            row.setOnClickListener(v->showCardDetails(c));content.addView(row,rowParams());
        }
        if(owned==0)showNotice("Your archive is waiting for its first card. Visit Summon Hall to collect.");
        loadDuplicateConversionPanel();
    }

    private void showCardDetails(JSONObject c) {
        new AlertDialog.Builder(this).setTitle(c.optString("name","Card Details"))
            .setMessage(c.optString("card_code","CARD")+"\\n"+c.optString("rarity","COMMON")+" · "+c.optString("element","AETHER")
                +"\\n\\n"+c.optString("description","A collectible from the Firstlight Realm.")
                +"\\n\\nOwned copies: "+c.optInt("owned_count"))
            .setPositiveButton("CLOSE",null).show();
    }

    private void loadDuplicateConversionPanel() {
        api.get("/api/duplicates",r->ui.post(()->{
            if(!"collection".equals(activeTab)||content==null)return;
            LinearLayout p=sectionPanel();p.addView(text("DUPLICATE CONVERTER",9,GOLD,true));
            if(!r.ok()){p.addView(text(r.error,8,MUTED,false));content.addView(p,rowParams());return;}
            JSONArray groups=r.data.optJSONArray("cards");int total=r.data.optInt("total_convertible");
            p.addView(text(total+" duplicate copies can become Shards. One primary copy is always protected.",8,MUTED,false));
            TextView convert=button("CONVERT UP TO 20 COPIES",true);convert.setEnabled(total>0);
            convert.setOnClickListener(v->{
                JSONArray ids=new JSONArray();
                if(groups!=null)outer:for(int i=0;i<groups.length();i++){JSONObject g=groups.optJSONObject(i);JSONArray a=g==null?null:g.optJSONArray("instance_ids");if(a==null)continue;for(int j=0;j<a.length();j++){if(ids.length()>=20)break outer;ids.put(a.optString(j));}}
                if(ids.length()==0)return;
                confirmAction("Convert duplicate cards?","This permanently converts "+ids.length()+" copies into Shards. Your main copy and trade-locked cards are protected.",()->api.post("/api/duplicates/convert",payload("instanceIds",ids,"requestId",newActionId()),x->ui.post(()->{
                    if(!x.ok())showResultDialog("Conversion failed",x.error);else{showResultDialog("DUPLICATES CONVERTED","Copies converted: "+x.data.optInt("converted_count")+"\\nShards awarded: "+x.data.optInt("shards_awarded"));refreshAccount(()->renderTab("collection"));}
                })));
            });
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(220),dp(38));bp.topMargin=dp(8);p.addView(convert,bp);content.addView(p,rowParams());
        }));
    }

    private void renderDungeons(JSONObject data) {
        JSONArray dungeons=data.optJSONArray("dungeons"),claimable=data.optJSONArray("claimableRuns"),history=data.optJSONArray("history");
        JSONObject run=data.optJSONObject("activeRun");
        int used=data.optInt("dailyRuns"),limit=data.optInt("dailyLimit",3),remaining=Math.max(0,limit-used);
        LinearLayout summary=sectionPanel();summary.addView(text("DAILY EXPEDITION LIMIT",8,GOLD,true));
        summary.addView(text(remaining+" / "+limit+" entries remaining today",14,TEXT,true));content.addView(summary,rowParams());
        if(run!=null){
            JSONObject rw=run.optJSONObject("reward");if(rw==null)rw=new JSONObject();
            boolean done="COMPLETED".equalsIgnoreCase(run.optString("result"))||"DEFEAT".equalsIgnoreCase(run.optString("result"));
            LinearLayout p=sectionPanel();p.addView(text(done?"EXPEDITION RESULT":"ACTIVE EXPEDITION",8,GOLD,true));
            p.addView(text(run.optString("dungeon_name","Dungeon"),18,TEXT,true));
            p.addView(text("Floor "+run.optInt("floor_reached")+" / "+rw.optInt("max_floors",6)+" · "+(done?run.optString("result"):"Choose your next chamber"),9,MUTED,false));
            p.addView(text(formatNumber(rw.optLong("coins"))+" Gold · "+formatNumber(rw.optLong("xp"))+" EXP · "+formatNumber(rw.optLong("shards"))+" Shards",8,GOLD_BRIGHT,true));
            if(done){
                TextView claim=button("CLAIM EXPEDITION REWARDS",true);
                claim.setOnClickListener(v->api.post("/api/dungeons/runs/"+run.optString("run_id")+"/claim",new JSONObject(),x->ui.post(()->{
                    if(!x.ok())showResultDialog("Claim failed",x.error);else{showResultDialog("EXPEDITION CLAIMED",x.data.optString("message","Rewards added."));refreshAccount(()->renderTab("dungeons"));}
                })));
                p.addView(claim,rowParams());
            }else{
                p.addView(text("CHOOSE YOUR NEXT CHAMBER",8,GOLD,true));
                String[][] choices={{"battle","⚔ BATTLE"},{"treasure","◇ TREASURE"},{"sanctuary","✧ SANCTUARY"}};
                for(String[] ch:choices){
                    TextView b=button(ch[1],false);String choice=ch[0];
                    b.setOnClickListener(v->confirmAction("Choose "+choice+"?","Your route and rewards are saved after the step.",()->api.post("/api/dungeons/runs/"+run.optString("run_id")+"/step",payload("choice",choice,"requestId",newActionId()),x->ui.post(()->{
                        if(!x.ok())showResultDialog("Dungeon step failed",x.error);else{JSONObject step=x.data.optJSONObject("step");showResultDialog("FLOOR RESOLVED",step==null?x.data.optString("message","Route updated."):step.optString("title","Route updated."));refreshAccount(()->renderTab("dungeons"));}
                    }))));
                    p.addView(b,rowParams());
                }
            }
            content.addView(p,rowParams());
        }
        if(dungeons!=null){
            content.addView(text("EXPEDITION GATES",8,GOLD,true));
            for(int i=0;i<dungeons.length();i++){
                JSONObject d=dungeons.optJSONObject(i);if(d==null)continue;JSONObject rw=d.optJSONObject("reward");if(rw==null)rw=new JSONObject();
                LinearLayout row=dataRow(d.optString("name","Dungeon"),d.optString("description","")+"\\nDifficulty "+d.optInt("difficulty")+" · "+rw.optInt("max_floors",6)+" floors",d.optString("slug",""));
                TextView start=button(run!=null?"ACTIVE":remaining<=0?"LIMIT REACHED":"ENTER",true);start.setEnabled(run==null&&remaining>0);
                start.setOnClickListener(v->confirmAction("Enter "+d.optString("name")+"?","This uses one of your daily dungeon entries.",()->api.post("/api/dungeons/"+d.optString("slug")+"/start",new JSONObject(),x->ui.post(()->{
                    if(!x.ok())showResultDialog("Cannot enter dungeon",x.error);else refreshAccount(()->renderTab("dungeons"));
                }))));
                row.addView(start,new LinearLayout.LayoutParams(dp(94),dp(34)));content.addView(row,rowParams());
            }
        }
        if(claimable!=null&&claimable.length()>0){content.addView(text("UNCLAIMED EXPEDITION REWARDS",8,GOLD,true));for(int i=0;i<claimable.length();i++){JSONObject item=claimable.optJSONObject(i);if(item==null)continue;TextView b=button("CLAIM · "+item.optString("dungeon_name","Expedition"),false);b.setOnClickListener(v->api.post("/api/dungeons/runs/"+item.optString("run_id")+"/claim",new JSONObject(),x->ui.post(()->{if(!x.ok())showResultDialog("Claim failed",x.error);else renderTab("dungeons");})));content.addView(b,rowParams());}}
        if(history!=null&&history.length()>0){content.addView(text("RECENT EXPEDITIONS",8,GOLD,true));for(int i=0;i<history.length();i++){JSONObject h=history.optJSONObject(i);if(h!=null)content.addView(dataRow(h.optString("dungeon_name","Expedition"),"Floor "+h.optInt("floor_reached")+" · "+h.optString("result"),""),rowParams());}}
    }

    private void renderRaids(JSONObject data) {
        JSONArray bosses=data.optJSONArray("bosses");
        if(bosses==null||bosses.length()==0){showNotice("No world raid is scheduled. Return when the next guardian awakens.");return;}
        for(int i=0;i<bosses.length();i++){
            JSONObject b=bosses.optJSONObject(i);if(b==null)continue;JSONObject rw=b.optJSONObject("reward");if(rw==null)rw=new JSONObject();
            boolean live=b.optBoolean("is_event_open");
            LinearLayout p=sectionPanel();p.addView(text("WORLD RAID · "+(b.optBoolean("defeated")?"DEFEATED":live?"EVENT LIVE":"SCHEDULED"),8,GOLD,true));
            p.addView(text(b.optString("name","World Guardian"),19,TEXT,true));p.addView(text(b.optString("description",""),9,MUTED,false));
            p.addView(text("BOSS HP  "+formatNumber(b.optLong("remaining_hp"))+" / "+formatNumber(b.optLong("hp")),9,GOLD_BRIGHT,true));
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(Math.max(0,Math.min(100,b.optInt("progress_percent"))));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD));
            p.addView(bar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(8)));
            p.addView(text(formatNumber(b.optLong("total_damage"))+" total damage · "+formatNumber(b.optLong("participants"))+" collectors",8,MUTED,false));
            p.addView(text("Reward: "+formatNumber(rw.optLong("coins"))+" Gold · "+formatNumber(rw.optLong("xp"))+" EXP · "+formatNumber(rw.optLong("shards"))+" Shards",8,GOLD_BRIGHT,true));
            if(live&&!b.optBoolean("defeated")){
                TextView attack=button(b.optLong("my_today_damage")>0?"DAILY STRIKE USED":"STRIKE WORLD BOSS",true);attack.setEnabled(b.optLong("my_today_damage")<=0);
                attack.setOnClickListener(v->confirmAction("Strike "+b.optString("name")+"?","Your formation power determines damage. One strike per day.",()->api.post("/api/raids/"+b.optString("slug")+"/attack",payload("requestId",newActionId()),x->ui.post(()->{
                    if(!x.ok())showResultDialog("Raid strike failed",x.error);else{JSONObject a=x.data.optJSONObject("attempt");showResultDialog("WORLD RAID STRIKE","Damage dealt: "+(a==null?"—":formatNumber(a.optLong("damage"))));refreshAccount(()->renderTab("raids"));}
                }))));
                p.addView(attack,rowParams());
            }
            if(b.optBoolean("my_claimable")){
                TextView claim=button("CLAIM RAID REWARDS",false);claim.setOnClickListener(v->api.post("/api/raids/"+b.optString("slug")+"/claim",new JSONObject(),x->ui.post(()->{
                    if(!x.ok())showResultDialog("Raid claim failed",x.error);else{showResultDialog("REWARDS CLAIMED",x.data.optString("message","Rewards added."));refreshAccount(()->renderTab("raids"));}
                })));
                p.addView(claim,rowParams());
            }
            content.addView(p,rowParams());
        }
    }

    private void renderTrades(JSONObject data) {
        JSONArray inventoryRaw=data.optJSONArray("inventory"),tradesRaw=data.optJSONArray("trades");
        final JSONArray inventory=inventoryRaw==null?new JSONArray():inventoryRaw;
        final JSONArray trades=tradesRaw==null?new JSONArray():tradesRaw;
        LinearLayout head=sectionPanel();head.addView(text("SECURE PLAYER-TO-PLAYER EXCHANGE",8,GOLD,true));
        head.addView(text("Only available tradable cards can be offered. Reserved or bound cards are excluded.",8,MUTED,false));
        TextView create=button("CREATE TRADE OFFER",true);create.setOnClickListener(v->showTradeCreateDialog(inventory));
        head.addView(create,new LinearLayout.LayoutParams(dp(190),dp(38)));content.addView(head,rowParams());
        String uid=me==null?"":me.optString("user_id");int count=0;content.addView(text("TRADE OFFERS",8,GOLD,true));
        for(int i=0;i<trades.length();i++){
            JSONObject t=trades.optJSONObject(i);if(t==null)continue;boolean incoming=uid.equals(t.optString("target_user_id")),outgoing=uid.equals(t.optString("creator_user_id"));if(!incoming&&!outgoing)continue;
            String title=incoming?t.optString("creator_name","Collector")+" sent an offer":"Offer to "+t.optString("target_name","Collector");
            StringBuilder detail=new StringBuilder(t.optString("status","PENDING"));if(!t.optString("message").isEmpty())detail.append(" · ").append(t.optString("message"));
            JSONArray items=t.optJSONArray("items");if(items!=null)for(int j=0;j<items.length();j++){JSONObject it=items.optJSONObject(j);if(it!=null)detail.append("\\n").append(it.optString("name")).append(" · ").append(it.optString("rarity"));}
            LinearLayout row=dataRow(title,detail.toString(),t.optString("status"));
            if(incoming&&"PENDING".equalsIgnoreCase(t.optString("status"))){
                TextView accept=button("ACCEPT",true);accept.setOnClickListener(v->showTradeAcceptDialog(t.optString("trade_id"),inventory));
                TextView decline=button("DECLINE",false);decline.setOnClickListener(v->confirmAction("Decline trade?","The offered cards will be released.",()->api.post("/api/trades/"+t.optString("trade_id")+"/decline",new JSONObject(),x->ui.post(()->{if(!x.ok())showResultDialog("Trade error",x.error);else renderTab("trades");}))));
                row.addView(accept,new LinearLayout.LayoutParams(dp(72),dp(32)));LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(dp(78),dp(32));dp2.leftMargin=dp(5);row.addView(decline,dp2);
            }else if(outgoing&&"PENDING".equalsIgnoreCase(t.optString("status"))){
                TextView cancel=button("CANCEL",false);cancel.setOnClickListener(v->confirmAction("Cancel trade offer?","Your reserved cards will become available again.",()->api.post("/api/trades/"+t.optString("trade_id")+"/cancel",new JSONObject(),x->ui.post(()->{if(!x.ok())showResultDialog("Trade error",x.error);else renderTab("trades");}))));
                row.addView(cancel,new LinearLayout.LayoutParams(dp(78),dp(32)));
            }
            content.addView(row,rowParams());count++;
        }
        if(count==0)showNotice("No trade offers yet. Create one to start exchanging cards.");
        content.addView(text("AVAILABLE TRADABLE CARDS · "+inventory.length(),8,GOLD,true));
        for(int i=0;i<inventory.length();i++){JSONObject c=inventory.optJSONObject(i);if(c!=null)content.addView(dataRow(c.optString("name"),c.optString("rarity")+" · Tradable",""),rowParams());}
    }

    private void showTradeCreateDialog(JSONArray inventory) {
        if(inventory.length()==0){showResultDialog("No tradable cards","No available tradable cards were returned for this account.");return;}
        String[] labels=new String[inventory.length()];boolean[] selected=new boolean[inventory.length()];
        for(int i=0;i<inventory.length();i++){JSONObject c=inventory.optJSONObject(i);labels[i]=c==null?"Unknown card":c.optString("name")+" · "+c.optString("rarity");}
        new AlertDialog.Builder(this).setTitle("Select 1–5 cards to offer").setMultiChoiceItems(labels,selected,(d,n,checked)->selected[n]=checked)
            .setNegativeButton("CANCEL",null).setPositiveButton("NEXT",(d,w)->{
                JSONArray ids=new JSONArray();for(int i=0;i<selected.length;i++)if(selected[i]&&inventory.optJSONObject(i)!=null)ids.put(inventory.optJSONObject(i).optString("instance_id"));
                if(ids.length()<1||ids.length()>5){showResultDialog("Select cards","Choose between 1 and 5 tradable cards.");return;}showTradeRecipientDialog(ids);
            }).show();
    }

    private void showTradeRecipientDialog(JSONArray ids) {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(18),dp(8),dp(18),0);
        EditText email=editField("Recipient account email",false);email.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText message=editField("Optional message",false);form.addView(email,fieldParams());form.addView(message,fieldParams());
        new AlertDialog.Builder(this).setTitle("Send trade offer").setView(form).setNegativeButton("CANCEL",null).setPositiveButton("SEND OFFER",(d,w)->{
            String target=email.getText().toString().trim();if(target.isEmpty()){showResultDialog("Recipient required","Enter the recipient's account email.");return;}
            api.post("/api/trades",payload("targetEmail",target,"message",message.getText().toString().trim(),"instanceIds",ids),r->ui.post(()->{
                if(!r.ok())showResultDialog("Trade offer failed",r.error);else{showResultDialog("OFFER SENT",r.data.optString("message","Your offer has been sent."));refreshAccount(()->renderTab("trades"));}
            }));
        }).show();
    }

    private void showTradeAcceptDialog(String tradeId,JSONArray inventory) {
        if(inventory.length()==0){showResultDialog("No exchange cards","You need available tradable cards to accept this offer.");return;}
        String[] labels=new String[inventory.length()];boolean[] selected=new boolean[inventory.length()];
        for(int i=0;i<inventory.length();i++){JSONObject c=inventory.optJSONObject(i);labels[i]=c==null?"Unknown card":c.optString("name")+" · "+c.optString("rarity");}
        new AlertDialog.Builder(this).setTitle("Choose cards to exchange").setMultiChoiceItems(labels,selected,(d,n,checked)->selected[n]=checked)
            .setNegativeButton("CANCEL",null).setPositiveButton("ACCEPT",(d,w)->{
                JSONArray ids=new JSONArray();for(int i=0;i<selected.length;i++)if(selected[i]&&inventory.optJSONObject(i)!=null)ids.put(inventory.optJSONObject(i).optString("instance_id"));
                if(ids.length()==0){showResultDialog("Choose an exchange","Select at least one tradable card in return.");return;}
                confirmAction("Complete this exchange?","Both sides exchange selected cards. This action cannot be reversed.",()->api.post("/api/trades/"+tradeId+"/accept",payload("instanceIds",ids),r->ui.post(()->{
                    if(!r.ok())showResultDialog("Trade failed",r.error);else{showResultDialog("TRADE COMPLETE",r.data.optString("message","Cards exchanged."));refreshAccount(()->renderTab("trades"));}
                })));
            }).show();
    }

    private void renderEvolution(JSONObject data) {
        JSONObject wallet=data.optJSONObject("wallet");if(wallet==null)wallet=new JSONObject();
        LinearLayout money=new LinearLayout(this);money.addView(statCard("GOLD",formatNumber(wallet.optLong("coins")),"Training"),weightParams(1));money.addView(statCard("GEMS",formatNumber(wallet.optLong("gems")),"Ascension"),weightParams(1));money.addView(statCard("SHARDS",formatNumber(wallet.optLong("shards")),"Upgrade"),weightParams(1));content.addView(money);
        JSONArray cards=data.optJSONArray("cards");if(cards==null||cards.length()==0){showNotice("No cards are ready for evolution. Collect cards from Summon Hall first.");return;}
        content.addView(text("YOUR EVOLUTION ROSTER",8,GOLD,true));
        for(int i=0;i<cards.length();i++){
            JSONObject c=cards.optJSONObject(i);if(c==null)continue;
            String ranks=starMarks(c.optInt("star_rank"));
            String sub=c.optString("rarity")+" · "+c.optString("element")+" · Lv "+c.optInt("level")+"/"+data.optInt("max_level",30)+" · "+ranks+" · "+c.optInt("copies_available")+" copies";
            LinearLayout row=dataRow(c.optString("name","Card"),sub,"UPGRADE");JSONObject walletCopy=wallet;
            row.setOnClickListener(v->showEvolutionCardDialog(c,walletCopy));content.addView(row,rowParams());
        }
    }

    private void showEvolutionCardDialog(JSONObject c,JSONObject wallet) {
        String message=c.optString("card_code")+" · "+c.optString("element")+" · "+c.optString("rarity")
            +"\\nLevel "+c.optInt("level")+" · Ascension "+c.optInt("star_rank")+"★"
            +"\\nAvailable copies: "+c.optInt("copies_available")
            +"\\n\\nLevel-up: "+formatNumber(c.optLong("level_cost"))+" Gold + "+formatNumber(c.optLong("level_shards"))+" Shards"
            +"\\n\\nSignature: "+c.optString("skill_name","Firstlight Gift")+"\\n"+c.optString("skill_description","Unlocks at 2★");
        String[] actions={"LEVEL UP","ASCEND"};
        new AlertDialog.Builder(this).setTitle(c.optString("name","Card Evolution")).setMessage(message).setItems(actions,(d,w)->{
            String action=w==0?"LEVEL_UP":"ASCEND";JSONObject cost=c.optJSONObject("next_star_cost");
            String extra=action.equals("ASCEND")&&cost!=null?"\\nDuplicates "+cost.optInt("duplicates")+" · Gold "+cost.optLong("coins")+" · Gems "+cost.optLong("gems")+" · Shards "+cost.optLong("shards"):"";
            String warning=action.equals("ASCEND")?"Ascension is permanent. It consumes duplicate copies of this exact card while preserving one primary copy.":"The server will verify your Gold and Shard balance before upgrading.";
            confirmAction(action.equals("ASCEND")?"Ascend card?":"Level up card?",warning+extra,()->api.post("/api/evolution/action",payload("action",action,"cardId",c.optString("card_id"),"requestId",newActionId()),r->ui.post(()->{
                if(!r.ok())showResultDialog("Evolution unavailable",r.error);else{showResultDialog("EVOLUTION UPDATED",r.data.optString("message","Your card has been upgraded."));refreshAccount(()->renderTab("evolution"));}
            })));
        }).show();
    }

    private void renderFormation(JSONObject data) {
        JSONArray cards=data.optJSONArray("cards"),saved=data.optJSONArray("formation");if(cards==null)cards=new JSONArray();if(saved==null)saved=new JSONArray();
        if(cards.length()==0){showNotice("Collect at least one card before building a formation.");return;}
        Map<Integer,String> savedSlots=new LinkedHashMap<>();for(int i=0;i<saved.length();i++){JSONObject s=saved.optJSONObject(i);if(s!=null)savedSlots.put(s.optInt("slot_no"),s.optString("card_id"));}
        String[] labels=new String[cards.length()+1],ids=new String[cards.length()+1];labels[0]="Empty slot";ids[0]="";
        for(int i=0;i<cards.length();i++){JSONObject c=cards.optJSONObject(i);ids[i+1]=c==null?"":c.optString("card_id");labels[i+1]=c==null?"Unknown card":c.optString("name")+" · "+c.optString("element")+" · "+c.optString("rarity")+" ×"+c.optInt("owned_count");}
        content.addView(text("YOUR EFFECTIVE POWER · "+formatNumber(data.optLong("power")),10,GOLD_BRIGHT,true));
        LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.HORIZONTAL);Spinner[] slots=new Spinner[5];
        for(int i=0;i<5;i++){
            LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(dp(4),dp(4),dp(4),dp(4));col.setBackground(gradient(PANEL_LIGHT,PANEL,Color.rgb(72,88,67),dp(8)));
            col.addView(text("POSITION 0"+(i+1),7,GOLD,true));Spinner spinner=new Spinner(this);
            ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);
            String sid=savedSlots.get(i+1);for(int j=0;j<ids.length;j++)if(ids[j].equals(sid))spinner.setSelection(j);slots[i]=spinner;col.addView(spinner);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);cp.setMargins(dp(3),dp(5),dp(3),dp(5));grid.addView(col,cp);
        }
        content.addView(grid);JSONObject synergy=data.optJSONObject("synergy");
        if(synergy!=null)content.addView(text("RESONANCE · "+synergy.optString("title","No active resonance")+" · +"+synergy.optInt("bonus_pct")+"%",9,GOLD_BRIGHT,true));
        TextView save=button("SAVE FORMATION",true);save.setOnClickListener(v->{
            JSONArray chosen=new JSONArray();java.util.HashSet<String> unique=new java.util.HashSet<>();
            for(Spinner sp:slots){int index=sp.getSelectedItemPosition();String id=index>=0&&index<ids.length?ids[index]:"";if(id.isEmpty())continue;if(!unique.add(id)){showResultDialog("Duplicate selection","Each card can only occupy one formation slot.");return;}chosen.put(id);}
            save.setEnabled(false);api.post("/api/formation",payload("cards",chosen),r->ui.post(()->{save.setEnabled(true);if(!r.ok())showResultDialog("Formation not saved",r.error);else{showResultDialog("FORMATION SAVED","Effective power: "+formatNumber(r.data.optLong("power")));refreshAccount(()->renderTab("formation"));}}));
        });
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(190),dp(40));sp.topMargin=dp(12);content.addView(save,sp);
    }

    private void renderBinders(JSONObject data) {
        JSONArray binders=data.optJSONArray("binders"),cards=data.optJSONArray("cards");JSONObject active=data.optJSONObject("activeBinder");
        if(binders==null)binders=new JSONArray();if(cards==null)cards=new JSONArray();
        LinearLayout p=sectionPanel();p.addView(text("PERSONAL CARD BINDERS",8,GOLD,true));
        p.addView(text(active==null?"Genesis Archive":active.optString("name","Genesis Archive"),18,TEXT,true));
        p.addView(text("Create up to 12 binders. Select a binder below to switch, rename, change its cover or manage its cards.",8,MUTED,false));
        TextView create=button("CREATE NEW BINDER",true);create.setOnClickListener(v->showCreateBinderDialog());
        p.addView(create,new LinearLayout.LayoutParams(dp(180),dp(38)));content.addView(p,rowParams());
        content.addView(text("YOUR BINDERS",8,GOLD,true));
        for(int i=0;i<binders.length();i++){
            JSONObject b=binders.optJSONObject(i);if(b==null)continue;
            String label=b.optString("name")+(b.optInt("is_active")==1?" · ACTIVE":"")+(b.optInt("is_default")==1?" · DEFAULT":"");
            LinearLayout row=dataRow(label,b.optString("theme_key")+" theme · "+b.optInt("card_count")+" unique cards","MANAGE");
            row.setOnClickListener(v->showBinderActions(b));content.addView(row,rowParams());
        }
        content.addView(text("CARDS IN ACTIVE BINDER",8,GOLD,true));
        for(int i=0;i<cards.length();i++){JSONObject c=cards.optJSONObject(i);if(c!=null)content.addView(dataRow(c.optString("name"),c.optString("rarity")+" · "+c.optString("element")+" · ×"+c.optInt("owned_count"),c.optString("card_code")),rowParams());}
        if(cards.length()==0)showNotice("This binder is empty. Your full archive remains safe under Cards.");
    }

    private void showCreateBinderDialog() {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(18),dp(8),dp(18),0);
        EditText name=editField("Binder name",false);Spinner theme=new Spinner(this);String[] themes={"verdant","ember","tide","astral","void","gold"};
        theme.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,themes));form.addView(name,fieldParams());form.addView(text("COVER THEME",8,GOLD,true));form.addView(theme);
        new AlertDialog.Builder(this).setTitle("Create binder").setView(form).setNegativeButton("CANCEL",null).setPositiveButton("CREATE",(d,w)->{
            String value=name.getText().toString().trim();if(value.length()<2){showResultDialog("Name required","Binder names need at least two characters.");return;}
            api.post("/api/binders",payload("name",value,"theme_key",themes[theme.getSelectedItemPosition()]),r->ui.post(()->{if(!r.ok())showResultDialog("Binder not created",r.error);else renderTab("binders");}));
        }).show();
    }

    private void renderAdmin(JSONObject data) {
        JSONObject stats=data.optJSONObject("stats");if(stats==null)stats=new JSONObject();
        JSONObject users=stats.optJSONObject("users"),cards=stats.optJSONObject("cards"),instances=stats.optJSONObject("instances");
        LinearLayout row=new LinearLayout(this);row.addView(statCard("PLAYERS",String.valueOf(users==null?0:users.optLong("total")),"Registered"),weightParams(1));
        row.addView(statCard("ACTIVE CARDS",String.valueOf(cards==null?0:cards.optLong("total")),"Catalogue"),weightParams(1));
        row.addView(statCard("COPIES",String.valueOf(instances==null?0:instances.optLong("total")),"Inventory"),weightParams(1));content.addView(row);
        addGap(content,12);content.addView(text("MASTER ADMIN CONSOLE",8,GOLD,true));
        content.addView(text("Read access follows the existing Worker admin authorization. Creation actions are recorded by the server.",8,MUTED,false));
        String[][] sections={{"cards","CARD CATALOGUE","Create and inspect cards"},{"users","PLAYER DIRECTORY","Inspect account records"},{"economy","ECONOMY","Wallet totals and transactions"},{"moderation","MODERATION","Reports, flags and appeals"},{"mail","GAME MAIL","Publish player announcements"},{"events","LIVE EVENTS","Schedules and rewards"},{"world","WORLD BUILDER","Chapters and encounters"},{"missions","MISSIONS","Mission configuration"},{"packs","PACKS","Pack settings and odds"},{"rewards","LEVEL REWARDS","Milestone configuration"}};
        for(String[] s:sections){LinearLayout item=dataRow(s[1],s[2],"OPEN");item.setOnClickListener(v->loadAdminSection(s[0]));content.addView(item,rowParams());}
    }

    private void loadAdminSection(String section) {
        String path;
        switch(section){
            case "cards":path="/api/admin/cards";break;case "users":path="/api/admin/users";break;case "economy":path="/api/admin/economy";break;
            case "moderation":path="/api/admin/moderation/overview";break;case "mail":path="/api/admin/mail";break;case "events":path="/api/admin/events";break;
            case "world":path="/api/admin/world";break;case "missions":path="/api/admin/missions";break;case "packs":path="/api/admin/packs";break;
            case "rewards":path="/api/admin/rewards";break;default:return;
        }
        content.removeAllViews();TextView back=button("← BACK TO ADMIN",false);back.setOnClickListener(v->renderTab("admin"));content.addView(back,new LinearLayout.LayoutParams(dp(170),dp(36)));
        content.addView(text(section.toUpperCase()+" MANAGEMENT",9,GOLD,true));renderLoading();
        api.get(path,r->ui.post(()->{
            if(!"admin".equals(activeTab)||content==null)return;content.removeViews(2,Math.max(0,content.getChildCount()-2));
            if(!r.ok()){showNotice(r.error);return;}
            switch(section){case "cards":renderAdminCards(r.data);break;case "users":renderAdminUsers(r.data);break;case "mail":renderAdminMail(r.data);break;
                case "moderation":renderObjectSummary(r.data,"MODERATION OVERVIEW");api.get("/api/admin/moderation/reports?status=OPEN",x->ui.post(()->{if(x.ok()&&"admin".equals(activeTab))renderAdminReports(x.data);}));break;
                default:renderObjectSummary(r.data,section.toUpperCase());}
        }));
    }

    private void renderAdminCards(JSONObject data) {
        TextView add=button("+ CREATE CARD",true);add.setOnClickListener(v->showAdminCreateCardDialog());content.addView(add,new LinearLayout.LayoutParams(dp(160),dp(38)));
        JSONArray list=data.optJSONArray("cards");if(list==null)return;
        for(int i=0;i<list.length();i++){JSONObject c=list.optJSONObject(i);if(c!=null)content.addView(dataRow(c.optString("name"),c.optString("card_code")+" · "+c.optString("rarity")+" · "+c.optString("element")+" · "+c.optInt("copies")+" copies",c.optInt("is_active")==1?"ACTIVE":"INACTIVE"),rowParams());}
    }

    private void showAdminCreateCardDialog() {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(18),dp(8),dp(18),0);
        EditText name=editField("Card name",false),desc=editField("Description",false),element=editField("Element (e.g. AETHER)",false);Spinner rarity=new Spinner(this);
        String[] rarities={"COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC"};rarity.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,rarities));
        form.addView(name,fieldParams());form.addView(desc,fieldParams());form.addView(element,fieldParams());form.addView(text("RARITY",8,GOLD,true));form.addView(rarity);
        new AlertDialog.Builder(this).setTitle("Create catalogue card").setView(form).setNegativeButton("CANCEL",null).setPositiveButton("CREATE",(d,w)->{
            String n=name.getText().toString().trim();if(n.length()<2){showResultDialog("Card name required","Enter at least two characters.");return;}
            api.post("/api/admin/cards",payload("name",n,"description",desc.getText().toString().trim(),"element",element.getText().toString().trim().isEmpty()?"AETHER":element.getText().toString().trim(),"rarity",rarities[rarity.getSelectedItemPosition()]),r->ui.post(()->{
                if(!r.ok())showResultDialog("Card not created",r.error);else{showResultDialog("CARD CREATED",r.data.optString("message","Card added."));loadAdminSection("cards");}
            }));
        }).show();
    }

    private void renderAdminUsers(JSONObject data) {
        JSONArray list=data.optJSONArray("users");if(list==null)return;content.addView(text(list.length()+" ACCOUNTS",8,GOLD,true));
        for(int i=0;i<list.length();i++){JSONObject u=list.optJSONObject(i);if(u==null)continue;LinearLayout item=dataRow(u.optString("display_name","Collector"),u.optString("email")+" · Lv "+u.optInt("collector_level")+" · "+u.optInt("card_copies")+" copies",u.optString("role","collector").toUpperCase());item.setOnClickListener(v->loadAdminPlayer(u.optString("user_id")));content.addView(item,rowParams());}
    }

    private void loadAdminPlayer(String userId) {
        content.removeAllViews();TextView back=button("← BACK TO PLAYERS",false);back.setOnClickListener(v->loadAdminSection("users"));content.addView(back,new LinearLayout.LayoutParams(dp(170),dp(36)));renderLoading();
        api.get("/api/admin/users/"+userId,r->ui.post(()->{
            if(!"admin".equals(activeTab))return;if(!r.ok()){showNotice(r.error);return;}content.removeViews(1,content.getChildCount()-1);
            JSONObject u=r.data.optJSONObject("user"),stats=r.data.optJSONObject("stats");
            if(u!=null){content.addView(text(u.optString("display_name")+" · "+u.optString("email"),15,TEXT,true));content.addView(text("ROLE "+u.optString("role")+" · LEVEL "+u.optInt("collector_level")+" · "+u.optLong("coins")+" Gold · "+u.optLong("gems")+" Gems · "+u.optLong("shards")+" Shards",8,GOLD_BRIGHT,true));}
            if(stats!=null)renderObjectSummary(stats,"PLAYER STATISTICS");
            for(String key:new String[]{"cards","transactions","packs","eventClaims","purchases","ownership","audit","ranked"}){JSONArray a=r.data.optJSONArray(key);if(a!=null){content.addView(text(key.toUpperCase()+" · "+a.length(),8,GOLD,true));for(int i=0;i<Math.min(a.length(),8);i++){JSONObject it=a.optJSONObject(i);if(it!=null)renderObjectSummary(it,key.toUpperCase());}}}
        }));
    }

    private void renderAdminMail(JSONObject data) {
        TextView publish=button("PUBLISH ANNOUNCEMENT",true);publish.setOnClickListener(v->showAdminMailDialog());content.addView(publish,new LinearLayout.LayoutParams(dp(220),dp(38)));
        JSONArray messages=data.optJSONArray("messages");if(messages==null)return;content.addView(text("RECENT GAME MAIL",8,GOLD,true));
        for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null)content.addView(dataRow(m.optString("subject","Announcement"),m.optString("mail_type")+" · "+m.optString("created_at")+" · "+m.optInt("read_count")+" read · "+m.optInt("claim_count")+" claimed",m.optString("status","SENT")),rowParams());}
    }

    private void showAdminMailDialog() {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(18),dp(8),dp(18),0);
        EditText subject=editField("Announcement subject",false),body=new EditText(this);body.setHint("Message to all players");body.setTextColor(TEXT);body.setHintTextColor(MUTED);body.setMinLines(3);body.setGravity(Gravity.TOP);body.setPadding(dp(10),dp(8),dp(10),dp(8));body.setBackground(gradient(Color.rgb(9,18,15),Color.rgb(9,18,15),Color.rgb(70,83,67),dp(9)));
        form.addView(subject,fieldParams());form.addView(body,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(100)));
        new AlertDialog.Builder(this).setTitle("Publish to all players").setView(form).setNegativeButton("CANCEL",null).setPositiveButton("PUBLISH",(d,w)->{
            String s=subject.getText().toString().trim(),b=body.getText().toString().trim();if(s.length()<2||b.length()<2){showResultDialog("Complete the message","Subject and message are required.");return;}
            api.post("/api/admin/mail",payload("mail_type","ANNOUNCEMENT","subject",s,"body",b,"target_type","ALL","coins",0,"gems",0,"shards",0,"xp",0,"pack_quantity",0),r->ui.post(()->{
                if(!r.ok())showResultDialog("Announcement failed",r.error);else{showResultDialog("ANNOUNCEMENT PUBLISHED",r.data.optString("message","Message published."));loadAdminSection("mail");}
            }));
        }).show();
    }

    private void renderAdminReports(JSONObject data) {
        JSONArray list=data.optJSONArray("reports");if(list==null||list.length()==0)return;content.addView(text("OPEN PLAYER REPORTS",8,GOLD,true));
        for(int i=0;i<list.length();i++){JSONObject r=list.optJSONObject(i);if(r!=null)content.addView(dataRow(r.optString("category"),r.optString("reporter_name")+" → "+r.optString("target_name")+" · "+r.optString("created_at")+"\\n"+r.optString("description"),r.optString("status")),rowParams());}
    }


    private void renderAchievements(JSONObject data) {
        JSONArray list=data.optJSONArray("achievements");
        JSONObject metrics=data.optJSONObject("metrics");
        if(list==null)list=new JSONArray();
        int unlocked=0,claimed=0,ready=0;
        for(int i=0;i<list.length();i++){
            JSONObject a=list.optJSONObject(i);if(a==null)continue;
            if(!a.isNull("unlocked_at")){unlocked++;if(a.isNull("claimed_at"))ready++;}
            if(!a.isNull("claimed_at"))claimed++;
        }
        LinearLayout stats=new LinearLayout(this);
        stats.addView(statCard("UNLOCKED",String.valueOf(unlocked),"Records earned"),weightParams(1));
        stats.addView(statCard("CLAIMED",String.valueOf(claimed),"Rewards collected"),weightParams(1));
        stats.addView(statCard("READY",String.valueOf(ready),"Awaiting claim"),weightParams(1));
        content.addView(stats);addGap(content,12);
        if(metrics!=null){
            content.addView(text("YOUR LIFETIME RECORDS",8,GOLD,true));
            content.addView(text("Cards "+metrics.optInt("unique_cards")+" · Adventure clears "+metrics.optInt("adventure_clears")
                    +" · Boss clears "+metrics.optInt("boss_clears")+" · Packs opened "+metrics.optInt("packs_opened")
                    +" · Collector Lv "+metrics.optInt("collector_level"),8,MUTED,false));
            addGap(content,10);
        }
        content.addView(text("ACHIEVEMENT REGISTER",8,GOLD,true));
        if(list.length()==0){showNotice("No active achievements are configured yet.");return;}
        for(int i=0;i<list.length();i++){
            JSONObject a=list.optJSONObject(i);if(a==null)continue;
            int progress=a.optInt("progress"),target=Math.max(1,a.optInt("target"));
            int pct=Math.min(100,Math.round(progress*100f/target));
            boolean isUnlocked=!a.isNull("unlocked_at"),isClaimed=!a.isNull("claimed_at");
            String status=isClaimed?"CLAIMED":isUnlocked?"UNLOCKED":"IN PROGRESS";
            LinearLayout panel=sectionPanel();
            panel.addView(text(a.optString("title","Achievement"),13,TEXT,true));
            panel.addView(text(a.optString("description",""),8,MUTED,false));
            panel.addView(text(status+" · "+progress+" / "+target+" · "+pct+"%",8,GOLD_BRIGHT,true));
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);bar.setProgress(pct);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(isUnlocked?GREEN:GOLD));
            panel.addView(bar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(7)));
            panel.addView(text("REWARD · "+formatNumber(a.optLong("reward_coins"))+" Gold · "
                    +formatNumber(a.optLong("reward_xp"))+" EXP"+(a.isNull("reward_cosmetic_id")?"":" · Cosmetic"),
                    8,GOLD_BRIGHT,true));
            if(isUnlocked&&!isClaimed){
                TextView claim=button("CLAIM REWARD",true);
                claim.setOnClickListener(v->confirmAction("Claim "+a.optString("title")+"?",
                        "Your achievement reward will be added to your account.",()->api.post("/api/achievements/"+a.optString("slug")+"/claim",new JSONObject(),r->ui.post(()->{
                            if(!r.ok())showResultDialog("Achievement unavailable",r.error);
                            else{JSONObject reward=r.data.optJSONObject("reward");showResultDialog("ACHIEVEMENT CLAIMED",
                                    "Gold: "+(reward==null?0:reward.optLong("coins"))+" · EXP: "+(reward==null?0:reward.optLong("xp")));
                                refreshAccount(()->renderTab("achievements"));}
                        }))));
                panel.addView(claim,rowParams());
            }
            content.addView(panel,rowParams());
        }
    }

    private void renderProgression(JSONObject data) {
        int level=data.optInt("level",1),xp=data.optInt("xp"),need=Math.max(1,data.optInt("xpRequired"));
        int page=data.optInt("rewardPage"),pageSize=data.optInt("rewardPageSize",30),total=data.optInt("rewardTotal");
        int pages=Math.max(1,(int)Math.ceil(total/(double)Math.max(1,pageSize)));
        LinearLayout hero=sectionPanel();hero.addView(text("COLLECTOR LEVEL "+level,9,GOLD,true));
        hero.addView(text(formatNumber(xp)+" / "+formatNumber(need)+" XP TO NEXT LEVEL",13,TEXT,true));
        ProgressBar xpBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);xpBar.setMax(100);
        xpBar.setProgress(Math.min(100,Math.round(xp*100f/need)));xpBar.setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD));
        hero.addView(xpBar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(8)));
        hero.addView(text(data.optInt("claimableCount")+" milestone rewards ready · up to level 1,000",8,MUTED,false));
        content.addView(hero,rowParams());
        JSONArray rewards=data.optJSONArray("rewards");if(rewards==null)rewards=new JSONArray();
        content.addView(text("LEVEL MILESTONES",8,GOLD,true));
        for(int i=0;i<rewards.length();i++){
            JSONObject r=rewards.optJSONObject(i);if(r==null)continue;
            int rewardLevel=r.optInt("level");boolean unlocked=rewardLevel<=level,claimed=r.optInt("claimed")==1;
            String desc=formatNumber(r.optLong("reward_coins"))+" Gold · "+formatNumber(r.optLong("reward_gems"))+" Gems · "
                    +formatNumber(r.optLong("reward_shards"))+" Shards";
            if(r.optInt("reward_pack_quantity")>0)desc+=" · "+r.optInt("reward_pack_quantity")+" × "+r.optString("reward_pack_name","Pack");
            if(!r.isNull("reward_cosmetic_name"))desc+=" · "+r.optString("reward_cosmetic_name");
            LinearLayout row=dataRow("LEVEL "+rewardLevel+(r.optString("reward_title").isEmpty()?"":" · "+r.optString("reward_title")),
                    desc,claimed?"CLAIMED":unlocked?"READY":"LOCKED");
            if(unlocked&&!claimed){
                TextView claim=button("CLAIM",true);
                claim.setOnClickListener(v->confirmAction("Claim level "+rewardLevel+" reward?","This milestone reward can only be claimed once.",()->api.post("/api/progression/claim",payload("level",rewardLevel),x->ui.post(()->{
                    if(!x.ok())showResultDialog("Reward unavailable",x.error);
                    else{showResultDialog("LEVEL REWARD CLAIMED",x.data.optString("message","Milestone reward added."));refreshAccount(()->loadProgressionPage(page));}
                }))));
                row.addView(claim,new LinearLayout.LayoutParams(dp(82),dp(32)));
            }
            content.addView(row,rowParams());
        }
        if(total>pageSize){
            addGap(content,10);
            LinearLayout pager=new LinearLayout(this);pager.setGravity(Gravity.CENTER);
            TextView previous=button("← PREVIOUS",false);previous.setEnabled(page>0);
            previous.setOnClickListener(v->loadProgressionPage(page-1));
            TextView next=button("NEXT →",false);next.setEnabled(page+1<pages);
            next.setOnClickListener(v->loadProgressionPage(page+1));
            pager.addView(previous,new LinearLayout.LayoutParams(dp(120),dp(36)));
            TextView pageLabel=text("PAGE "+(page+1)+" / "+pages,8,GOLD,true);pageLabel.setGravity(Gravity.CENTER);
            pager.addView(pageLabel,new LinearLayout.LayoutParams(0,dp(36),1f));
            pager.addView(next,new LinearLayout.LayoutParams(dp(100),dp(36)));content.addView(pager);
        }
    }

    private void loadProgressionPage(int page) {
        int safe=Math.max(0,page);
        api.get("/api/progression?rewardPage="+safe+"&rewardPageSize=30",r->ui.post(()->{
            if(!"progression".equals(activeTab)||content==null)return;
            content.removeAllViews();
            if(!r.ok()){showNotice(r.error);return;}
            renderProgression(r.data);
        }));
    }

    private TextView sectionHeading(String label) {
        TextView heading=text(label,8,GOLD,true);
        heading.setLetterSpacing(0.12f);
        heading.setPadding(0,dp(6),0,dp(5));
        return heading;
    }

    private void renderEvents(JSONObject data) {
        JSONObject login=data.optJSONObject("login_event");
        if(login==null)login=new JSONObject();
        JSONObject loginEvent=login.optJSONObject("event");
        JSONArray rewards=login.optJSONArray("rewards");
        if(rewards==null)rewards=new JSONArray();
        if(loginEvent!=null){
            LinearLayout panel=sectionPanel();
            panel.addView(text("LIMITED-TIME LOGIN JOURNEY",8,GOLD,true));
            panel.addView(text(loginEvent.optString("title","Realm Login Event"),18,TEXT,true));
            panel.addView(text(loginEvent.optString("description","Claim a reward for each active event day."),8,MUTED,false));
            int day=login.optInt("currentDay");
            panel.addView(text(login.optBoolean("completed")?"JOURNEY COMPLETE":"CURRENT DAY · "+day,8,GOLD_BRIGHT,true));
            for(int i=0;i<rewards.length();i++){
                JSONObject r=rewards.optJSONObject(i);if(r==null)continue;
                int idx=r.optInt("day_index");boolean claimed=r.optInt("is_claimed")==1;
                String desc=formatNumber(r.optLong("reward_coins"))+" Gold · "+formatNumber(r.optLong("reward_gems"))+" Gems · "
                        +formatNumber(r.optLong("reward_shards"))+" Shards · "+formatNumber(r.optLong("reward_xp"))+" EXP";
                if(r.optInt("reward_pack_quantity")>0)desc+=" · "+r.optInt("reward_pack_quantity")+" × "+r.optString("reward_pack_name","Pack");
                if(!r.optString("reward_card_name").isEmpty())desc+=" · "+r.optString("reward_card_name");
                if(!r.optString("reward_cosmetic_name").isEmpty())desc+=" · "+r.optString("reward_cosmetic_name");
                panel.addView(dataRow("DAY "+idx+" · "+r.optString("title","Event reward"),desc,
                        claimed?"CLAIMED":idx==day?"TODAY":idx<day?"PASSED":"UPCOMING"),rowParams());
            }
            if(login.optBoolean("canClaim")){
                TextView claim=button("CLAIM TODAY'S LOGIN REWARD",true);
                claim.setOnClickListener(v->confirmAction("Claim event login reward?","Today's event reward can only be claimed once.",()->api.post("/api/events/login/claim",new JSONObject(),r->ui.post(()->{
                    if(!r.ok())showResultDialog("Event reward unavailable",r.error);
                    else{showResultDialog("EVENT REWARD CLAIMED",r.data.optString("message","Your login reward has been added."));refreshAccount(()->renderTab("events"));}
                }))));
                panel.addView(claim,rowParams());
            }
            content.addView(panel,rowParams());
        }else{
            content.addView(sectionHeading("NO ACTIVE LOGIN EVENT"));
            showNotice("There is no active limited-time login journey right now.");
        }
        JSONObject event=data.optJSONObject("event");
        if(event==null){
            addGap(content,10);content.addView(sectionHeading("FESTIVAL EXCHANGE"));
            showNotice("No limited-time shop event is currently open.");
            return;
        }
        addGap(content,12);
        LinearLayout eventPanel=sectionPanel();
        eventPanel.addView(text("LIMITED-TIME FESTIVAL",8,GOLD,true));
        eventPanel.addView(text(event.optString("title","Festival"),20,TEXT,true));
        eventPanel.addView(text(event.optString("description",""),8,MUTED,false));
        eventPanel.addView(text("ENDS · "+event.optString("ends_at",""),8,GOLD_BRIGHT,true));
        JSONObject wallet=data.optJSONObject("wallet");if(wallet==null)wallet=new JSONObject();
        eventPanel.addView(text("WALLET · "+formatNumber(wallet.optLong("coins"))+" GOLD · "
                +formatNumber(wallet.optLong("gems"))+" GEMS · "+formatNumber(wallet.optLong("shards"))+" SHARDS",8,MUTED,true));
        content.addView(eventPanel,rowParams());
        JSONArray items=data.optJSONArray("items"),history=data.optJSONArray("history");
        if(items==null)items=new JSONArray();
        content.addView(text("FESTIVAL EXCHANGE",8,GOLD,true));
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            String currency=item.optString("currency","COINS").toUpperCase();
            long balance=wallet.optLong(currency.toLowerCase());
            boolean noStock=!item.isNull("stock_remaining")&&item.optInt("stock_remaining")<=0;
            boolean affordable=balance>=item.optLong("price");
            LinearLayout row=dataRow(item.optString("name","Festival reward"),
                    item.optString("item_type")+" · "+item.optString("rarity")+" · "+formatNumber(item.optLong("price"))+" "+currency
                            +" · "+(item.isNull("stock_remaining")?"OPEN STOCK":item.optInt("stock_remaining")+" LEFT"),
                    noStock?"SOLD OUT":!affordable?"INSUFFICIENT":currency);
            TextView buy=button("PURCHASE",true);buy.setEnabled(!noStock&&affordable);
            buy.setOnClickListener(v->confirmAction("Purchase "+item.optString("name")+"?",
                    "Spend "+formatNumber(item.optLong("price"))+" "+currency+". The server checks price and stock again.",
                    ()->api.post("/api/events/purchase",payload("itemId",item.optString("item_id")),r->ui.post(()->{
                        if(!r.ok())showResultDialog("Purchase failed",r.error);
                        else{showResultDialog("FESTIVAL REWARD ADDED",r.data.optString("message","Purchase completed."));refreshAccount(()->renderTab("events"));}
                    }))));
            row.addView(buy,new LinearLayout.LayoutParams(dp(106),dp(34)));content.addView(row,rowParams());
        }
    }

    private void drawLeaderboard(JSONObject data,String category) {
        content.removeAllViews();
        content.addView(text("FIRSTLIGHT HALL OF FAME",8,GOLD,true));
        content.addView(text("TOP 50 COLLECTORS",19,TEXT,true));
        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        String[][] choices={{"level","LEVEL"},{"collection","COLLECTION"},{"ranked","RANKED"},{"guild","GUILD"}};
        for(String[] choice:choices){
            TextView b=button(choice[1],category.equals(choice[0]));
            b.setPadding(dp(8),0,dp(8),0);
            b.setOnClickListener(v->drawLeaderboard(data,choice[0]));
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(34),1f);tp.setMargins(dp(2),dp(5),dp(2),dp(5));tabs.addView(b,tp);
        }
        content.addView(tabs);
        JSONArray rows=category.equals("level")?data.optJSONArray("levels"):category.equals("collection")?data.optJSONArray("collections"):
                category.equals("ranked")?data.optJSONArray("ranked"):data.optJSONArray("guilds");
        if(rows==null||rows.length()==0){showNotice("No rankings have been recorded in this category yet.");return;}
        for(int i=0;i<rows.length();i++){
            JSONObject p=rows.optJSONObject(i);if(p==null)continue;
            String name=p.optString("display_name",p.optString("name","Collector"));
            if(!p.optString("tag").isEmpty())name+=" ["+p.optString("tag")+"]";
            String detail,score;
            if(category.equals("level")){detail="LEVEL "+p.optInt("collector_level")+" · "+formatNumber(p.optLong("collector_xp"))+" XP";score=p.optInt("collector_level")+" LV";}
            else if(category.equals("collection")){detail=p.optInt("unique_cards")+" unique cards · "+p.optInt("card_copies")+" copies";score=p.optInt("unique_cards")+" CARDS";}
            else if(category.equals("ranked")){detail=p.optString("tier")+" · "+p.optInt("wins")+"W / "+p.optInt("losses")+"L";score=p.optInt("rating")+" RATING";}
            else{detail="LEVEL "+p.optInt("guild_level")+" · "+p.optInt("member_count")+" members · "+formatNumber(p.optLong("guild_contribution"))+" contribution";score=formatNumber(p.optLong("guild_xp"))+" XP";}
            content.addView(dataRow("#"+(i+1)+" · "+name,detail,score),rowParams());
        }
    }

    private void renderLeaderboards(JSONObject data) {
        drawLeaderboard(data,"level");
    }

    private void renderRanked(JSONObject data) {
        JSONObject season=data.optJSONObject("season"),rating=data.optJSONObject("rating");
        if(data.optBoolean("rankedBanned")){
            LinearLayout p=sectionPanel();p.addView(text("RANKED ACCESS SUSPENDED",9,RED,true));
            p.addView(text(data.optString("rankedBanReason","Moderator review"),12,TEXT,true));
            p.addView(text(data.optString("rankedBanUntil","Permanent restriction"),8,MUTED,false));content.addView(p);return;
        }
        if(data.optBoolean("hidden")){
            showNotice("Master Admin accounts are excluded from the public Ranked Season leaderboard. Private administration access remains unchanged.");return;
        }
        if(season==null||season.length()==0){showNotice("There is no active Ranked Season at the moment.");return;}
        if(rating==null){showNotice("Your Ranked rating is not available yet.");return;}
        LinearLayout hero=sectionPanel();
        hero.addView(text("COMPETITIVE SEASON",8,GOLD,true));
        hero.addView(text(season.optString("name","Ranked Season"),20,TEXT,true));
        hero.addView(text(season.optString("starts_at","")+"  ·  "+season.optString("ends_at",""),8,MUTED,false));
        hero.addView(text(rating.optString("tier","BRONZE"),25,GOLD_BRIGHT,true));
        hero.addView(text(formatNumber(rating.optLong("rating"))+" RATING · #"+rating.optInt("position")+" ON THE LADDER",9,TEXT,true));
        content.addView(hero,rowParams());
        LinearLayout stats=new LinearLayout(this);
        stats.addView(statCard("WINS",String.valueOf(rating.optInt("wins")),"Ranked"),weightParams(1));
        stats.addView(statCard("LOSSES",String.valueOf(rating.optInt("losses")),"Ranked"),weightParams(1));
        stats.addView(statCard("DRAWS",String.valueOf(rating.optInt("draws")),"Ranked"),weightParams(1));
        stats.addView(statCard("STREAK",String.valueOf(rating.optInt("win_streak")),"Wins"),weightParams(1));
        content.addView(stats);addGap(content,10);
        content.addView(text("SEASON LEADERBOARD",8,GOLD,true));
        JSONArray rows=data.optJSONArray("leaderboard");
        if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject p=rows.optJSONObject(i);if(p!=null)content.addView(dataRow("#"+(i+1)+" · "+p.optString("display_name","Collector"),p.optString("rating")+" rating · "+p.optInt("wins")+"W / "+p.optInt("losses")+"L",p.optString("tier","BRONZE")),rowParams());}
        TextView enter=button("FIND RANKED MATCH",true);
        enter.setOnClickListener(v->confirmAction("Queue for Ranked Arena?","Ranked matchmaking uses your live rating and season. Master Admin accounts are excluded.",()->{
            api.post("/api/arena/queue",payload("mode","RANKED"),r->ui.post(()->{
                if(!r.ok())showResultDialog("Ranked queue unavailable",r.error);
                else{showResultDialog("RANKED MATCHMAKING",r.data.optString("message",r.data.optBoolean("matched")?"Match found. Open Arena to continue.":"Searching for an opponent. Open Arena to check the queue."));renderTab("arena");}
            }));
        }));
        content.addView(enter,new LinearLayout.LayoutParams(dp(210),dp(38)));
    }

    private void showBinderActions(JSONObject binder) {
        String id=binder.optString("binder_id");
        boolean isDefault=binder.optInt("is_default")==1;
        String[] options=isDefault?new String[]{"SET ACTIVE","RENAME / CHANGE THEME"}:
                new String[]{"SET ACTIVE","RENAME / CHANGE THEME","MANAGE CARD CONTENTS","DELETE BINDER"};
        new AlertDialog.Builder(this).setTitle(binder.optString("name","Binder")).setItems(options,(d,w)->{
            String choice=options[w];
            if("SET ACTIVE".equals(choice)){
                api.post("/api/binders/"+id,payload("set_active",true),r->ui.post(()->{
                    if(!r.ok())showResultDialog("Binder not changed",r.error);else renderTab("binders");
                }));
            }else if("RENAME / CHANGE THEME".equals(choice))showBinderEditDialog(binder);
            else if("MANAGE CARD CONTENTS".equals(choice))showBinderCardsDialog(id);
            else if("DELETE BINDER".equals(choice))confirmAction("Delete "+binder.optString("name")+"?",
                    "Only the binder is deleted. Your cards and collection remain untouched.",()->api.delete("/api/binders/"+id,r->ui.post(()->{
                        if(!r.ok())showResultDialog("Binder not deleted",r.error);else{showResultDialog("BINDER DELETED",r.data.optString("message","Binder removed."));renderTab("binders");}
                    })));
        }).show();
    }

    private void showBinderEditDialog(JSONObject binder) {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(18),dp(8),dp(18),0);
        EditText name=editField("Binder name",false);name.setText(binder.optString("name",""));
        Spinner theme=new Spinner(this);String[] themes={"verdant","ember","tide","astral","void","gold"};
        theme.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,themes));
        for(int i=0;i<themes.length;i++)if(themes[i].equals(binder.optString("theme_key")))theme.setSelection(i);
        form.addView(name,fieldParams());form.addView(text("COVER THEME",8,GOLD,true));form.addView(theme);
        new AlertDialog.Builder(this).setTitle("Binder settings").setView(form).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",(d,w)->{
            String value=name.getText().toString().trim();if(value.length()<2){showResultDialog("Name required","Binder names need at least two characters.");return;}
            api.post("/api/binders/"+binder.optString("binder_id"),payload("name",value,"theme_key",themes[theme.getSelectedItemPosition()]),r->ui.post(()->{
                if(!r.ok())showResultDialog("Binder settings not saved",r.error);else renderTab("binders");
            }));
        }).show();
    }

    private void showBinderCardsDialog(String binderId) {
        api.post("/api/binders/"+binderId,payload("set_active",true),activeResult->ui.post(()->{
            if(!activeResult.ok()){showResultDialog("Binder unavailable",activeResult.error);return;}
            api.get("/api/binders",binderResult->ui.post(()->{
                if(!binderResult.ok()){showResultDialog("Unable to load binder",binderResult.error);return;}
                api.get("/api/cards",cardsResult->ui.post(()->{
                    if(!cardsResult.ok()){showResultDialog("Unable to load card archive",cardsResult.error);return;}
                    JSONArray binderCards=binderResult.data.optJSONArray("cards"),allCards=cardsResult.data.optJSONArray("cards");
                    if(binderCards==null)binderCards=new JSONArray();if(allCards==null)allCards=new JSONArray();
                    java.util.HashSet<String> selectedIds=new java.util.HashSet<>();
                    for(int i=0;i<binderCards.length();i++){JSONObject c=binderCards.optJSONObject(i);if(c!=null)selectedIds.add(c.optString("card_id"));}
                    java.util.ArrayList<JSONObject> owned=new java.util.ArrayList<>();
                    for(int i=0;i<allCards.length();i++){JSONObject c=allCards.optJSONObject(i);if(c!=null&&c.optInt("owned_count")>0)owned.add(c);}
                    if(owned.isEmpty()){showResultDialog("No owned cards","Collect cards before adding them to a custom binder.");return;}
                    String[] labels=new String[owned.size()];boolean[] checked=new boolean[owned.size()];
                    for(int i=0;i<owned.size();i++){JSONObject c=owned.get(i);labels[i]=c.optString("name")+" · "+c.optString("rarity")+" ×"+c.optInt("owned_count");checked[i]=selectedIds.contains(c.optString("card_id"));}
                    new AlertDialog.Builder(this).setTitle("Cards in this binder · up to 200")
                            .setMultiChoiceItems(labels,checked,(dialog,which,isChecked)->checked[which]=isChecked)
                            .setNegativeButton("CANCEL",null).setPositiveButton("SAVE",(dialog,which)->{
                                JSONArray cardIds=new JSONArray();for(int i=0;i<owned.size();i++)if(checked[i])cardIds.put(owned.get(i).optString("card_id"));
                                if(cardIds.length()>200){showResultDialog("Binder limit","A binder can contain up to 200 unique cards.");return;}
                                api.post("/api/binders/"+binderId+"/cards",payload("cardIds",cardIds),r->ui.post(()->{
                                    if(!r.ok())showResultDialog("Binder contents not saved",r.error);else{showResultDialog("BINDER UPDATED",r.data.optString("message","Binder cards saved."));renderTab("binders");}
                                }));
                            }).show();
                }));
            }));
        }));
    }

    private void renderObjectSummary(JSONObject data, String label) {
        showPanelHeading(label);
        boolean rendered = false;
        java.util.Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if ("ok".equals(key) || data.isNull(key)) continue;
            Object value = data.opt(key);
            String display;
            if (value instanceof JSONObject) display = "Available";
            else if (value instanceof JSONArray) display = ((JSONArray) value).length() + " entries";
            else display = String.valueOf(value);
            content.addView(dataRow(humanize(key), display, ""), rowParams());
            rendered = true;
        }
        if (!rendered) showNotice("No additional status is available at the moment.");
    }

    private void showPanelHeading(String label) {
        LinearLayout p = sectionPanel();
        p.addView(text(label, 8, GOLD, true));
        p.addView(text("CardUniverse native account", 14, TEXT, true));
        p.addView(text("Connected directly to the live game services.", 9, MUTED, false));
        content.addView(p);
        addGap(content, 10);
    }

    private void renderLoading() {
        LinearLayout box = sectionPanel();
        box.setGravity(Gravity.CENTER);
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(GOLD));
        box.addView(progress, new LinearLayout.LayoutParams(dp(26), dp(26)));
        box.addView(text("SYNCING WITH THE REALM...", 8, MUTED, true));
        content.addView(box);
    }

    private void showNotice(String message) {
        LinearLayout box = sectionPanel();
        TextView title = text("REALM NOTICE", 8, GOLD, true);
        TextView body = text(message, 9, MUTED, false);
        box.addView(title);
        addGap(box, 5);
        box.addView(body);
        content.addView(box);
    }

    private String newActionId() {
        return UUID.randomUUID().toString();
    }

    private void confirmAction(String title, String message, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("CONTINUE", (dialog, which) -> action.run())
                .show();
    }

    private void showResultDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message == null || message.trim().isEmpty() ? "Action completed." : message)
                .setPositiveButton("CONTINUE", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private String formatPackReveal(JSONArray cards) {
        if (cards == null || cards.length() == 0) return "The pack was opened and your collection has been updated.";
        StringBuilder message = new StringBuilder("Your new cards have been added to the archive.\n\n");
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.optJSONObject(i);
            if (card == null) continue;
            message.append(String.format(java.util.Locale.US, "%02d", i + 1))
                    .append("  ").append(card.optString("name", "Unknown card"))
                    .append("\n     ").append(card.optString("rarity", "COMMON"))
                    .append("  ·  ").append(card.optString("element", "UNKNOWN"))
                    .append("\n");
        }
        return message.toString();
    }

    private boolean hasReward(JSONObject reward) {
        if (reward == null) return false;
        return reward.optLong("coins") > 0 || reward.optLong("gems") > 0
                || reward.optLong("shards") > 0 || reward.optLong("xp") > 0
                || (reward.optString("pack_id", "").length() > 0 && reward.optLong("pack_quantity") > 0);
    }

    private String rewardSummary(JSONObject reward) {
        if (reward == null) return "No reward";
        StringBuilder value = new StringBuilder();
        if (reward.optLong("coins") > 0) value.append(formatNumber(reward.optLong("coins"))).append(" Gold  ");
        if (reward.optLong("gems") > 0) value.append(formatNumber(reward.optLong("gems"))).append(" Gems  ");
        if (reward.optLong("shards") > 0) value.append(formatNumber(reward.optLong("shards"))).append(" Shards  ");
        if (reward.optLong("xp") > 0) value.append(formatNumber(reward.optLong("xp"))).append(" XP  ");
        if (reward.optLong("pack_quantity") > 0) value.append(reward.optLong("pack_quantity")).append(" Pack");
        return value.toString().trim();
    }

    private void refreshAccount(Runnable after) {
        api.get("/api/me", result -> ui.post(() -> {
            if (result.ok()) {
                acceptMe(result.data);
                if (accountLabel != null) accountLabel.setText(me.optString("display_name", "Collector"));
                if (currencyLabel != null) currencyLabel.setText(walletLine());
            }
            if (after != null) after.run();
        }));
    }

    private void claimMission(String slug, TextView button) {
        if (slug == null || slug.isEmpty()) return;
        button.setEnabled(false);
        button.setText("CLAIMING...");
        api.post("/api/missions/" + slug + "/claim", new JSONObject(), result -> ui.post(() -> {
            if (!result.ok()) {
                button.setEnabled(true);
                button.setText("CLAIM MISSION REWARD");
                showResultDialog("Reward unavailable", result.error);
                return;
            }
            JSONObject reward = result.data.optJSONObject("reward");
            String summary = reward == null ? result.data.optString("message", "Mission reward claimed.")
                    : formatNumber(reward.optLong("coins")) + " Gold  ·  "
                    + formatNumber(reward.optLong("xp")) + " XP  ·  "
                    + reward.optInt("pack") + " Pack";
            showResultDialog("✦ MISSION REWARD", summary);
            refreshAccount(() -> renderTab("missions"));
        }));
    }

    private void claimChallenge(String slug, TextView button) {
        if (slug == null || slug.isEmpty()) return;
        button.setEnabled(false);
        button.setText("CLAIMING...");
        api.post("/api/daily-challenges/" + slug + "/claim", new JSONObject(), result -> ui.post(() -> {
            if (!result.ok()) {
                button.setEnabled(true);
                button.setText("CLAIM CHALLENGE REWARD");
                showResultDialog("Challenge reward unavailable", result.error);
                return;
            }
            JSONObject reward = result.data.optJSONObject("reward");
            String summary = reward == null ? "Daily challenge reward claimed."
                    : formatNumber(reward.optLong("coins")) + " Gold  ·  "
                    + formatNumber(reward.optLong("xp")) + " XP";
            showResultDialog("✦ DAILY CHALLENGE", summary);
            refreshAccount(() -> renderTab("missions"));
        }));
    }

    private void claimMail(String mailId, TextView button) {
        if (mailId == null || mailId.isEmpty()) return;
        button.setEnabled(false);
        button.setText("CLAIMING...");
        api.post("/api/mail/" + Uri.encode(mailId) + "/claim", new JSONObject(), result -> ui.post(() -> {
            if (!result.ok()) {
                button.setEnabled(true);
                button.setText("CLAIM REWARD");
                showResultDialog("Reward unavailable", result.error);
                return;
            }
            showResultDialog("✦ INBOX REWARD", rewardSummary(result.data.optJSONObject("reward")));
            refreshAccount(() -> renderTab("inbox"));
        }));
    }

    private void markMailRead(String mailId, TextView button) {
        if (mailId == null || mailId.isEmpty()) return;
        button.setEnabled(false);
        api.post("/api/mail/" + Uri.encode(mailId) + "/read", new JSONObject(), result -> ui.post(() -> {
            if (!result.ok()) {
                button.setEnabled(true);
                showResultDialog("Unable to update message", result.error);
                return;
            }
            renderTab("inbox");
        }));
    }

    private void joinArenaQueue(String mode) {
        confirmAction("Find a " + mode + " match?", "CardUniverse will search for an opponent within the current matchmaking rules.", () -> {
            JSONObject payload = new JSONObject();
            try { payload.put("mode", mode); } catch (Exception ignored) {}
            api.post("/api/arena/queue", payload, result -> ui.post(() -> {
                if (!result.ok()) {
                    showResultDialog("Matchmaking unavailable", result.error);
                    return;
                }
                JSONObject active = result.data.optJSONObject("activeMatch");
                showResultDialog(active != null ? "⚔ MATCH FOUND" : "MATCHMAKING",
                        active != null ? "Opponent found: " + active.optString("opponentName", "Collector")
                                : "You have joined the " + mode + " queue.");
                renderTab("arena");
            }));
        });
    }

    private void cancelArenaQueue() {
        api.post("/api/arena/queue/cancel", new JSONObject(), result -> ui.post(() -> {
            if (!result.ok()) showResultDialog("Unable to cancel queue", result.error);
            else renderTab("arena");
        }));
    }

    private void submitArenaMove(String matchId, String move) {
        if (matchId == null || matchId.isEmpty()) return;
        JSONObject payload = new JSONObject();
        try { payload.put("move", move); payload.put("requestId", newActionId()); } catch (Exception ignored) {}
        api.post("/api/arena/matches/" + matchId + "/move", payload, result -> ui.post(() -> {
            if (!result.ok()) {
                showResultDialog("Move not accepted", result.error);
                renderTab("arena");
                return;
            }
            showResultDialog("ARENA MOVE", result.data.optString("message",
                    result.data.optBoolean("waiting") ? "Move locked. Waiting for your opponent." : "Match updated."));
            renderTab("arena");
        }));
    }

    private void joinGuild(String tag) {
        if (tag == null || tag.isEmpty()) return;
        api.post("/api/guilds/" + tag.toUpperCase(java.util.Locale.US) + "/join", new JSONObject(), result -> ui.post(() -> {
            if (!result.ok()) { showResultDialog("Unable to join guild", result.error); return; }
            showResultDialog("WELCOME TO THE GUILD", "You joined [" + tag.toUpperCase(java.util.Locale.US) + "].");
            refreshAccount(() -> renderTab("guild"));
        }));
    }

    private void showCreateGuildDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), dp(8), dp(8), 0);
        EditText name = editField("Guild name (3–28 characters)", false);
        EditText tag = editField("Guild tag (2–5 letters/numbers)", false);
        EditText description = editField("Guild description (optional)", false);
        description.setSingleLine(false);
        description.setMinLines(2);
        form.addView(name, fieldParams());
        form.addView(tag, fieldParams());
        form.addView(description, fieldParams());
        new AlertDialog.Builder(this).setTitle("Found a guild")
                .setMessage("Guild names and tags must be unique.")
                .setView(form)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CREATE", (dialog, which) -> {
                    JSONObject payload = new JSONObject();
                    try {
                        payload.put("name", name.getText().toString().trim());
                        payload.put("tag", tag.getText().toString().trim().toUpperCase(java.util.Locale.US));
                        payload.put("description", description.getText().toString().trim());
                        payload.put("emblem", "✦");
                    } catch (Exception ignored) {}
                    api.post("/api/guilds", payload, result -> ui.post(() -> {
                        if (!result.ok()) { showResultDialog("Guild creation failed", result.error); return; }
                        showResultDialog("✦ GUILD FOUNDED", "Your guild has been created.");
                        refreshAccount(() -> renderTab("guild"));
                    }));
                }).show();
    }

    private void chooseGuildContribution() {
        String[] labels = {"50 Gold", "100 Gold", "250 Gold", "500 Gold", "1,000 Gold"};
        int[] amounts = {50, 100, 250, 500, 1000};
        new AlertDialog.Builder(this).setTitle("Contribute to treasury")
                .setItems(labels, (dialog, which) -> confirmAction("Contribute " + labels[which] + "?",
                        "Gold moves from your wallet into the guild treasury.", () -> {
                    JSONObject payload = new JSONObject();
                    try { payload.put("amount", amounts[which]); payload.put("requestId", newActionId()); }
                    catch (Exception ignored) {}
                    api.post("/api/guilds/contribute", payload, result -> ui.post(() -> {
                        if (!result.ok()) { showResultDialog("Contribution failed", result.error); return; }
                        showResultDialog("GUILD TREASURY UPDATED", result.data.optString("message", "Contribution recorded."));
                        refreshAccount(() -> renderTab("guild"));
                    }));
                })).show();
    }

    private void showProfileEditor() {
        api.get("/api/profile", result -> ui.post(() -> {
            if (!result.ok()) { showResultDialog("Profile unavailable", result.error); return; }
            JSONObject profileData = result.data.optJSONObject("profile");
            if (profileData == null) profileData = new JSONObject();
            EditText name = editField("Display name", false);
            name.setText(me == null ? "" : me.optString("display_name", ""));
            EditText bio = editField("Short bio (max 180 characters)", false);
            bio.setSingleLine(false);
            bio.setMinLines(3);
            bio.setText(profileData.optString("bio", ""));
            LinearLayout form = new LinearLayout(this);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(dp(8), dp(8), dp(8), 0);
            form.addView(name, fieldParams());
            form.addView(bio, fieldParams());
            String activeTitle = profileData.optString("activeTitleId", "");
            new AlertDialog.Builder(this).setTitle("Collector profile").setView(form)
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("SAVE", (dialog, which) -> {
                        JSONObject payload = new JSONObject();
                        try {
                            payload.put("displayName", name.getText().toString().trim());
                            payload.put("bio", bio.getText().toString().trim());
                            payload.put("activeTitleId", activeTitle);
                        } catch (Exception ignored) {}
                        api.patch("/api/profile", payload, saved -> ui.post(() -> {
                            if (!saved.ok()) { showResultDialog("Profile not saved", saved.error); return; }
                            showResultDialog("PROFILE UPDATED", "Your collector profile has been saved.");
                            refreshAccount(() -> renderTab("settings"));
                        }));
                    }).show();
        }));
    }

    private void showReportPlayerDialog() {
        EditText query = editField("Player display name (2+ characters)", false);
        new AlertDialog.Builder(this).setTitle("Find a collector")
                .setMessage("Search by exact or partial display name.")
                .setView(query)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SEARCH", (dialog, which) -> {
                    String value = query.getText().toString().trim();
                    if (value.length() < 2) { showResultDialog("Search needs more detail", "Enter at least two characters."); return; }
                    api.get("/api/players/search?q=" + Uri.encode(value), result -> ui.post(() -> {
                        if (!result.ok()) { showResultDialog("Player search failed", result.error); return; }
                        JSONArray players = result.data.optJSONArray("players");
                        if (players == null || players.length() == 0) { showResultDialog("No collectors found", "Try another display name."); return; }
                        String[] labels = new String[players.length()];
                        for (int i = 0; i < players.length(); i++) {
                            JSONObject player = players.optJSONObject(i);
                            labels[i] = player == null ? "Collector" :
                                    player.optString("display_name", "Collector") + "  ·  LEVEL " + player.optInt("collector_level");
                        }
                        new AlertDialog.Builder(this).setTitle("Select player")
                                .setItems(labels, (selection, index) -> showReportComposeDialog(players.optJSONObject(index)))
                                .setNegativeButton("CANCEL", null).show();
                    }));
                }).show();
    }

    private void showReportComposeDialog(JSONObject player) {
        if (player == null) return;
        String[] values = {"HARASSMENT", "SPAM", "SCAM", "CHEATING", "EXPLOIT", "IMPERSONATION", "OTHER"};
        String[] labels = {"Harassment", "Spam", "Scam or suspicious trade", "Cheating", "Exploit abuse", "Impersonation", "Other"};
        Spinner category = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        category.setAdapter(adapter);
        EditText description = new EditText(this);
        description.setTextColor(TEXT);
        description.setHintTextColor(MUTED);
        description.setHint("Describe what happened (12–1500 characters)");
        description.setSingleLine(false);
        description.setMinLines(4);
        description.setGravity(Gravity.TOP);
        description.setPadding(dp(10), dp(10), dp(10), dp(10));
        description.setBackground(gradient(Color.rgb(9, 18, 15), Color.rgb(9, 18, 15), Color.rgb(70, 83, 67), dp(9)));
        EditText evidence = editField("Evidence URL (optional)", false);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), dp(8), dp(8), 0);
        form.addView(text("REPORTING " + player.optString("display_name", "COLLECTOR"), 9, GOLD, true));
        form.addView(category, fieldParams());
        form.addView(description, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));
        form.addView(evidence, fieldParams());
        new AlertDialog.Builder(this).setTitle("Submit private report").setView(form)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SUBMIT", (dialog, which) -> {
                    String details = description.getText().toString().trim();
                    if (details.length() < 12 || details.length() > 1500) {
                        showResultDialog("Report details required", "Please enter between 12 and 1,500 characters.");
                        return;
                    }
                    JSONObject payload = new JSONObject();
                    try {
                        payload.put("target_user_id", player.optString("user_id"));
                        payload.put("category", values[category.getSelectedItemPosition()]);
                        payload.put("description", details);
                        payload.put("evidence_url", evidence.getText().toString().trim());
                    } catch (Exception ignored) {}
                    api.post("/api/reports", payload, result -> ui.post(() -> {
                        if (!result.ok()) { showResultDialog("Report not submitted", result.error); return; }
                        showResultDialog("REPORT SUBMITTED", "Your private report has been sent to moderation.");
                    }));
                }).show();
    }

    private String endpointFor(String tab) {
        switch (tab) {
            case "collection": return "/api/cards";
            case "binders": return "/api/binders";
            case "formation": return "/api/formation";
            case "evolution": return "/api/evolution";
            case "dungeons": return "/api/dungeons";
            case "raids": return "/api/raids";
            case "trades": return "/api/trades";
            case "achievements": return "/api/achievements";
            case "progression": return "/api/progression?rewardPage=0&rewardPageSize=30";
            case "events": return "/api/events";
            case "leaderboards": return "/api/leaderboards";
            case "ranked": return "/api/ranked";
            case "summon": return "/api/packs";
            case "adventure": return "/api/adventure";
            case "arena": return "/api/arena";
            case "missions": return "/api/missions";
            case "shop": return "/api/shop";
            case "inbox": return "/api/mail";
            case "guild": return "/api/guilds";
            case "settings": return "/api/support/contacts";
            case "admin": return "/api/admin/summary";
            default: return null;
        }
    }

    private String screenHeading(String tab) {
        switch (tab) {
            case "collection": return "The Card Archive";
            case "binders": return "Collector Binders";
            case "formation": return "Battle Formation";
            case "evolution": return "Evolution Forge";
            case "dungeons": return "Roguelike Dungeon";
            case "raids": return "World Boss Raid";
            case "trades": return "Trade Desk";
            case "achievements": return "Achievement Hall";
            case "progression": return "Level Rewards";
            case "events": return "Live Realm Events";
            case "leaderboards": return "Hall of Fame";
            case "ranked": return "Ranked Season";
            case "summon": return "Summon Hall";
            case "adventure": return "Realm Expedition";
            case "arena": return "Battle Arena";
            case "missions": return "Missions & Challenges";
            case "shop": return "Realm Marketplace";
            case "inbox": return "Realm Inbox";
            case "guild": return "Guild Headquarters";
            case "settings": return "Player Settings";
            case "admin": return "Master Console";
            default: return "Realm";
        }
    }

    private String screenDescription(String tab) {
        switch (tab) {
            case "collection": return "Your owned cards, full catalogue and duplicate copies.";
            case "binders": return "Organize the cards in your personal archive binders.";
            case "formation": return "Assign up to five cards to strengthen every battle.";
            case "evolution": return "Train cards, awaken signature skills and ascend ranks.";
            case "dungeons": return "Choose a route, clear floors and claim expedition rewards.";
            case "raids": return "Contribute daily damage to the shared world boss.";
            case "trades": return "Exchange tradable cards securely with other collectors.";
            case "achievements": return "Permanent records for collecting, exploring and competing.";
            case "progression": return "Claim milestone rewards earned through collector levels.";
            case "events": return "Limited-time login journeys and festival exchanges.";
            case "leaderboards": return "Compare collector level, collection, Ranked and guild prestige.";
            case "ranked": return "Your competitive rating, tier and season standing.";
            case "summon": return "Your available packs and summon inventory.";
            case "adventure": return "Chapters, encounters and expedition progress.";
            case "arena": return "Your current player-versus-player status.";
            case "missions": return "Live mission progress from the game server.";
            case "shop": return "Current items, prices and availability.";
            case "inbox": return "Messages, rewards and announcements.";
            case "guild": return "Your guild membership and contribution.";
            case "settings": return "Account information, privacy and support.";
            case "admin": return "Administrative overview for authorized accounts.";
            default: return "";
        }
    }

    private LinearLayout statCard(String kicker, String value, String caption) {
        LinearLayout card = sectionPanel();
        card.addView(text(kicker, 7, GOLD, true));
        TextView valueView = text(value, 18, TEXT, true);
        valueView.setPadding(0, dp(3), 0, dp(2));
        card.addView(valueView);
        card.addView(text(caption, 7, MUTED, false));
        return card;
    }

    private LinearLayout featureCard(String icon, String title, String description, Runnable action) {
        LinearLayout card = sectionPanel();
        card.setMinimumHeight(dp(80));
        card.addView(text(icon, 18, GOLD_BRIGHT, true));
        addGap(card, 2);
        card.addView(text(title, 11, TEXT, true));
        card.addView(text(description, 8, MUTED, false));
        card.setOnClickListener(v -> action.run());
        return card;
    }

    private LinearLayout dataRow(String title, String subtitle, String trailing) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(11), dp(8), dp(11), dp(8));
        row.setBackground(gradient(Color.rgb(19, 31, 28), Color.rgb(14, 24, 21), Color.rgb(51, 66, 53), dp(10)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 10, TEXT, true));
        TextView details = text(subtitle, 8, MUTED, false);
        details.setMaxLines(2);
        copy.addView(details);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (trailing != null && !trailing.isEmpty()) {
            TextView badge = text(trailing, 7, GOLD_BRIGHT, true);
            badge.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            badge.setMaxWidth(dp(130));
            row.addView(badge);
        }
        row.setTag(title);
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        return params;
    }

    private LinearLayout sectionPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(gradient(PANEL_LIGHT, PANEL, Color.rgb(56, 73, 58), dp(12)));
        return panel;
    }

    private void addDivider(LinearLayout parent, boolean verticalSpace) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(55, 68, 55));
        LinearLayout.LayoutParams lp = verticalSpace
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        parent.addView(divider, lp);
    }

    private EditText editField(String hint, boolean password) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(11);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setHint(hint);
        edit.setPadding(dp(12), 0, dp(12), 0);
        edit.setBackground(gradient(Color.rgb(9, 18, 15), Color.rgb(9, 18, 15), Color.rgb(70, 83, 67), dp(9)));
        edit.setSelectAllOnFocus(false);
        return edit;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        lp.bottomMargin = dp(8);
        return lp;
    }

    private TextView button(String label, boolean primary) {
        TextView button = text(label, 9, primary ? Color.rgb(24, 28, 20) : GOLD_BRIGHT, true);
        button.setGravity(Gravity.CENTER);
        button.setLetterSpacing(0.1f);
        button.setBackground(gradient(
                primary ? GOLD_BRIGHT : Color.rgb(32, 46, 35),
                primary ? GOLD : Color.rgb(18, 31, 25),
                primary ? GOLD_BRIGHT : Color.rgb(113, 99, 67), dp(9)));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(bold ? "sans-serif" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(true);
        return view;
    }

    private GradientDrawable gradient(int top, int bottom, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        d.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams weightParams(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        return lp;
    }

    private void addGap(LinearLayout parent, int amount) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(amount)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String walletLine() {
        JSONObject wallet = me == null ? null : me.optJSONObject("wallet");
        if (wallet == null) return "SYNCING ACCOUNT";
        return "◈ " + formatNumber(wallet.optLong("coins")) + "    ✧ " + formatNumber(wallet.optLong("gems"));
    }

    private String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        String value = parts.length > 1 ? parts[0].substring(0, 1) + parts[1].substring(0, 1)
                : name.substring(0, Math.min(2, name.length()));
        return value.toUpperCase();
    }

    private String formatNumber(long value) {
        if (value >= 1_000_000) return String.format(java.util.Locale.US, "%.1fM", value / 1_000_000d);
        if (value >= 10_000) return String.format(java.util.Locale.US, "%.1fK", value / 1_000d);
        return String.format(java.util.Locale.US, "%,d", value);
    }

    private String humanize(String key) {
        String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        return spaced.toUpperCase();
    }

    private void onLogout() {
        api.post("/api/auth/logout", new JSONObject(), result -> ui.post(() -> {
            api.clearSession();
            me = null;
            showAuth(false, "");
        }));
    }

    @Override
    public void onBackPressed() {
        if (!"home".equals(activeTab) && content != null) {
            renderTab("home");
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
