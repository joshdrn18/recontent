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
                {"summon", "✧", "SUMMON"},
                {"adventure", "⚔", "ADVENTURE"},
                {"arena", "◈", "ARENA"},
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
            case "summon": renderPacks(data); break;
            case "adventure": renderAdventure(data); break;
            case "arena": renderArena(data); break;
            case "missions": renderMissions(data); break;
            case "shop": renderShop(data); break;
            case "inbox": renderInbox(data); break;
            case "guild": renderGuild(data); break;
            case "settings": renderSettings(data); break;
            case "admin": renderObjectSummary(data, "ADMIN OVERVIEW"); break;
            default: renderObjectSummary(data, "REALM DATA");
        }
    }

    private void renderCollection(JSONObject data) {
        JSONArray cards = data.optJSONArray("cards");
        if (cards == null) { showNotice("No card data was returned."); return; }
        int owned = 0;
        for (int i = 0; i < cards.length(); i++) if (cards.optJSONObject(i) != null && cards.optJSONObject(i).optInt("owned_count") > 0) owned++;
        content.addView(statCard("OWNED CARD TYPES", String.valueOf(owned), "Cards with at least one copy"));
        addGap(content, 10);
        content.addView(text("ARCHIVE CATALOGUE", 8, GOLD, true));
        for (int i = 0; i < cards.length(); i++) {
            JSONObject item = cards.optJSONObject(i);
            if (item == null || item.optInt("owned_count") <= 0) continue;
            String sub = item.optString("rarity", "COMMON") + "  ·  " + item.optString("element", "UNKNOWN")
                    + "  ·  OWNED " + item.optInt("owned_count");
            content.addView(dataRow(item.optString("name", "Unknown card"), sub, item.optString("card_code", "CARD")), rowParams());
        }
        if (owned == 0) showNotice("Your archive is waiting for its first card.");
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
            case "collection": return "Your owned cards and archive catalogue.";
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
