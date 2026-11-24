package com.carduniverse.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AdapterView;
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
    private static final int BG = Color.rgb(8, 11, 24);
    private static final int PANEL = Color.rgb(20, 26, 45);
    private static final int PANEL_LIGHT = Color.rgb(29, 37, 62);
    private static final int GOLD = Color.rgb(157, 135, 238);
    private static final int GOLD_BRIGHT = Color.rgb(211, 201, 255);
    private static final int TEXT = Color.rgb(241, 242, 255);
    private static final int MUTED = Color.rgb(158, 169, 198);
    private static final int GREEN = Color.rgb(104, 219, 190);
    private static final int RED = Color.rgb(255, 112, 142);

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
    private JSONObject arenaLeaderCard;
    private JSONObject arenaOpponentCard;
    private JSONObject adminProfile;
    private JSONObject me;
    private JSONObject progression;
    private String activeTab = "home";
    private boolean registering = false;
    private boolean requestBusy = false;
    private boolean syncingAccount = false;
    private boolean compactLayout = false;
    private boolean showingAuth = false;
    private long lastAutoSyncAt = 0L;
    private static final long AUTO_SYNC_INTERVAL_MS = 60_000L;
    private final Runnable autoSyncTick = new Runnable() {
        @Override public void run() {
            if (!isFinishing() && api != null && me != null && appRoot != null) {
                syncWithSharedBackend(false);
                ui.postDelayed(this, AUTO_SYNC_INTERVAL_MS);
            }
        }
    };

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
        showingAuth = false;
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
        showingAuth = false;
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
        showingAuth = true;
        compactLayout = isCompactLayout();
        registering = register;
        requestBusy = false;
        FrameLayout root = new FrameLayout(this);
        root.setBackground(gradient(BG, Color.rgb(16, 34, 28), Color.rgb(75, 93, 68), 0));

        boolean compact = isCompactLayout();
        LinearLayout split = new LinearLayout(this);
        split.setOrientation(compact ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        split.setPadding(dp(compact ? 14 : 18), dp(compact ? 10 : 14),
                dp(compact ? 14 : 18), dp(compact ? 14 : 14));

        LinearLayout world = new LinearLayout(this);
        world.setOrientation(LinearLayout.VERTICAL);
        world.setGravity(compact ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        world.setPadding(dp(compact ? 4 : 20), dp(compact ? 4 : 14),
                dp(compact ? 4 : 18), dp(compact ? 2 : 14));
        TextView eyebrow = text("A NEW CHAPTER BEGINS", 9, GOLD, true);
        eyebrow.setLetterSpacing(0.24f);
        TextView title = text(compact ? "ENTER THE FIRSTLIGHT REALM" : "ENTER THE\nFIRSTLIGHT REALM",
                compact ? 19 : 29, TEXT, true);
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
        addGap(world, compact ? 4 : 9);
        world.addView(title);
        addGap(world, compact ? 3 : 10);
        world.addView(copy);
        addGap(world, compact ? 7 : 14);
        LinearLayout.LayoutParams artworkParams = compact
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(86))
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        world.addView(artwork, artworkParams);
        addGap(world, compact ? 6 : 12);
        world.addView(facts);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(compact ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        form.setPadding(dp(compact ? 15 : 22), dp(compact ? 13 : 18),
                dp(compact ? 15 : 22), dp(compact ? 13 : 18));
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
        addGap(form, compact ? 7 : 13);
        form.addView(heading);
        addGap(form, 4);
        form.addView(hint);
        addGap(form, compact ? 8 : 13);
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
                    activeTab = "home";
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

        if (compact) {
            split.addView(world, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams formParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            formParams.topMargin = dp(10);
            split.addView(form, formParams);
            ScrollView authScroll = new ScrollView(this);
            authScroll.setFillViewport(true);
            authScroll.setClipToPadding(false);
            authScroll.addView(split);
            root.addView(authScroll, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            split.addView(world, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            LinearLayout.LayoutParams formParams = new LinearLayout.LayoutParams(dp(330), ViewGroup.LayoutParams.MATCH_PARENT);
            formParams.leftMargin = dp(12);
            split.addView(form, formParams);
            root.addView(split, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        setContentView(root);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    private boolean isCompactLayout() {
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT
                || widthDp < 680;
    }

    private void showApp() {
        showingAuth = false;
        boolean compact = isCompactLayout();
        compactLayout = compact;
        navLabels.clear();
        rail = null;
        appRoot = new LinearLayout(this);
        appRoot.setOrientation(compact ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        appRoot.setBackgroundColor(BG);

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

        if (!compact) {
            rail = new LinearLayout(this);
            rail.setOrientation(LinearLayout.VERTICAL);
            rail.setPadding(dp(7), dp(9), dp(7), dp(8));
            rail.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            rail.setBackground(gradient(Color.rgb(20, 22, 47), Color.rgb(8, 11, 25), Color.rgb(72, 65, 125), 0));
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
            for (String[] item : items) addNavItem(navStack, item[0], item[1], item[2]);
            navScroll.addView(navStack);
            rail.addView(navScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        mainColumn = new LinearLayout(this);
        mainColumn.setOrientation(LinearLayout.VERTICAL);
        mainColumn.setPadding(dp(compact ? 10 : 12), dp(compact ? 7 : 9),
                dp(compact ? 10 : 14), dp(compact ? 6 : 8));
        appRoot.addView(mainColumn, compact
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), 0, dp(4), dp(5));
        pageTitle = text(compact ? "CARDUNIVERSE" : "REALM OVERVIEW",
                compact ? 11 : 13, TEXT, true);
        pageTitle.setSingleLine(true);
        pageTitle.setLetterSpacing(0.09f);
        top.addView(pageTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        accountLabel = text(me.optString("display_name", "Collector"), compact ? 8 : 9, GOLD_BRIGHT, true);
        accountLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        accountLabel.setSingleLine(true);
        accountLabel.setMaxWidth(dp(compact ? 70 : 180));
        currencyLabel = text(walletLine(), compact ? 6 : 8, MUTED, true);
        currencyLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        currencyLabel.setSingleLine(true);
        currencyLabel.setMaxWidth(dp(compact ? 116 : 220));
        LinearLayout account = new LinearLayout(this);
        account.setOrientation(LinearLayout.VERTICAL);
        account.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        account.addView(accountLabel);
        account.addView(currencyLabel);
        TextView avatar = text(initials(me.optString("display_name", "CU")), 10, BG, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(gradient(GOLD_BRIGHT, GOLD, GOLD, dp(24)));
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(
                dp(compact ? 27 : 32), dp(compact ? 27 : 32));
        avp.leftMargin = dp(compact ? 6 : 10);
        right.addView(account);

        if ("admin".equalsIgnoreCase(me.optString("role", ""))) {
            TextView adminPill = text("GOD MODE", 7, Color.rgb(220, 207, 255), true);
            adminPill.setGravity(Gravity.CENTER);
            adminPill.setPadding(dp(8), dp(5), dp(8), dp(5));
            adminPill.setBackground(gradient(Color.rgb(52, 39, 91),
                    Color.rgb(28, 24, 58), Color.rgb(149, 117, 255), dp(20)));
            LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(27));
            pillParams.leftMargin = dp(8);
            right.addView(adminPill, pillParams);
        }
        right.addView(avatar, avp);
        top.addView(right);
        mainColumn.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 39 : 44)));

        if (compact) {
            HorizontalScrollView navScroll = new HorizontalScrollView(this);
            navScroll.setHorizontalScrollBarEnabled(false);
            navScroll.setFillViewport(false);
            LinearLayout navStack = new LinearLayout(this);
            navStack.setOrientation(LinearLayout.HORIZONTAL);
            for (String[] item : items) addCompactNavItem(navStack, item[0], item[1], item[2]);
            navScroll.addView(navStack);
            mainColumn.addView(navScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(43)));
        }
        addDivider(mainColumn, false);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(compact ? 1 : 3), dp(compact ? 8 : 11),
                dp(compact ? 1 : 3), dp(22));
        scroller.addView(content);
        mainColumn.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(appRoot);
        refreshNav();
        renderTab(activeTab == null ? "home" : activeTab);
        loadAdminProfile();
        startAutoSync();
    }

    private void addCompactNavItem(LinearLayout target, String key, String icon, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setPadding(dp(10), dp(6), dp(10), dp(6));
        item.setBackground(gradient(PANEL, PANEL, Color.TRANSPARENT, dp(9)));
        TextView glyph = text(icon, 14, MUTED, true);
        glyph.setGravity(Gravity.CENTER);
        TextView name = text(label, 7, MUTED, true);
        name.setGravity(Gravity.CENTER);
        name.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.leftMargin = dp(5);
        item.addView(glyph);
        item.addView(name, nameParams);
        item.setOnClickListener(v -> renderTab(key));
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        itemParams.rightMargin = dp(5);
        target.addView(item, itemParams);
        navLabels.put(key, name);
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
                    active ? Color.rgb(55, 45, 94) : PANEL,
                    active ? Color.rgb(28, 25, 56) : PANEL,
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
        hero.setBackground(gradient(Color.rgb(47, 40, 91), Color.rgb(13, 18, 39), Color.rgb(147, 124, 231), dp(16)));

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

        FrameLayout heroMark = new FrameLayout(this);
        heroMark.setBackground(gradient(Color.rgb(44, 34, 79), Color.rgb(13, 17, 37),
                Color.rgb(152, 126, 255), dp(16)));
        TextView star = text("✦", 42, GOLD_BRIGHT, true);
        star.setGravity(Gravity.CENTER);
        heroMark.addView(star, new FrameLayout.LayoutParams(-1, -1));
        CardArtView fox = new CardArtView(this);
        fox.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        fox.loadArtwork("/art/cinder-fox.webp");
        FrameLayout.LayoutParams foxParams = new FrameLayout.LayoutParams(-1, -1);
        foxParams.setMargins(dp(5), dp(5), dp(5), dp(5));
        heroMark.addView(fox, foxParams);
        LinearLayout.LayoutParams hmp = new LinearLayout.LayoutParams(dp(104), dp(104));
        hmp.leftMargin = dp(14);
        hero.addView(heroMark, hmp);
        content.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addGap(content, 12);
        if (isGodModeEnabled()) {
            LinearLayout god = sectionPanel();
            god.setOrientation(LinearLayout.HORIZONTAL);
            god.setGravity(Gravity.CENTER_VERTICAL);
            TextView mark = text("✦", 25, Color.rgb(205, 185, 255), true);
            god.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout godCopy = new LinearLayout(this);
            godCopy.setOrientation(LinearLayout.VERTICAL);
            godCopy.addView(text("MASTER ADMIN · GOD MODE ONLINE", 9,
                    Color.rgb(215, 200, 255), true));
            godCopy.addView(text("Access Level 100 · Level 1,000 · Admin privileges active",
                    8, MUTED, false));
            god.addView(godCopy, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            content.addView(god, rowParams());
            addGap(content, 8);
        }
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
            default: renderObjectSummary(data, "REALM DATA");
        }
    }

    
    private void renderPacks(JSONObject data) {
        JSONArray packs = data.optJSONArray("packs");
        if (packs == null) { showNotice("No summon packs are available."); return; }
        boolean unlimited = data.optBoolean("unlimited") || isGodModeEnabled();
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
                        showPackRevealDialog(result.data.optJSONArray("cards"));
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
            LinearLayout ranked = sectionPanel();
            ranked.setOrientation(LinearLayout.HORIZONTAL);
            ranked.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout rankedCopy = new LinearLayout(this);
            rankedCopy.setOrientation(LinearLayout.VERTICAL);
            rankedCopy.addView(text("RANKED SEASON · "
                    + (season == null ? "ACTIVE" : season.optString("name", "ACTIVE")), 7, GOLD, true));
            rankedCopy.addView(text(rating.optString("tier", "BRONZE")
                    + " · " + rating.optInt("rating") + " RATING", 17, TEXT, true));
            ranked.addView(rankedCopy, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ranked.addView(text("#" + rating.optInt("position") + " LADDER", 8, GOLD_BRIGHT, true));
            content.addView(ranked, rowParams());
        } else if (data.optBoolean("rankedHidden")) {
            showNotice("Ranked matchmaking and the public ranked ladder are disabled for administrator accounts.");
        } else if (data.optBoolean("rankedBanned")) {
            showNotice("Ranked access is restricted: "
                    + data.optString("rankedBanReason", "moderator action"));
        }

        JSONObject active = data.optJSONObject("activeMatch");
        JSONObject queue = data.optJSONObject("queue");
        if (active != null) {
            LinearLayout matchHeader = sectionPanel();
            matchHeader.setOrientation(LinearLayout.HORIZONTAL);
            matchHeader.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout matchTitle = new LinearLayout(this);
            matchTitle.setOrientation(LinearLayout.VERTICAL);
            matchTitle.addView(text("LIVE ARENA", 8, GOLD, true));
            matchTitle.addView(text(active.optString("mode", "CASUAL") + " · TURN "
                    + active.optInt("current_turn"), 16, TEXT, true));
            matchHeader.addView(matchTitle, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            matchHeader.addView(text("⚔  VS  ⚔", 13, GOLD_BRIGHT, true));
            content.addView(matchHeader, rowParams());

            FrameLayout battleStage = createArenaBattleStage(active);
            content.addView(battleStage, rowParams());

            LinearLayout fighters = new LinearLayout(this);
            fighters.setOrientation(LinearLayout.HORIZONTAL);
            fighters.addView(createArenaFighterPanel("YOU",
                    me == null ? "COLLECTOR" : me.optString("display_name", "COLLECTOR"),
                    active.optLong("myHp"), active.optLong("myMaxHp"),
                    Color.rgb(115, 184, 139)), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            fighters.addView(createArenaFighterPanel("OPPONENT",
                    active.optString("opponentName", "Opponent"),
                    active.optLong("opponentHp"), active.optLong("opponentMaxHp"),
                    Color.rgb(210, 122, 105)), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            content.addView(fighters, rowParams());

            String myMove = active.optString("myMove", "");
            if (myMove.isEmpty()) {
                content.addView(text("CHOOSE YOUR ACTION", 8, GOLD, true));
                addGap(content, 5);
                LinearLayout moves = new LinearLayout(this);
                moves.setOrientation(LinearLayout.HORIZONTAL);
                String[][] choices = {
                        {"STRIKE", "⚔", "Deal damage to the opponent"},
                        {"GUARD", "◈", "Reduce incoming damage"},
                        {"FOCUS", "✦", "Charge your next action"}
                };
                for (String[] choice : choices) {
                    LinearLayout moveCard = new LinearLayout(this);
                    moveCard.setOrientation(LinearLayout.VERTICAL);
                    moveCard.setGravity(Gravity.CENTER);
                    moveCard.setPadding(dp(8), dp(8), dp(8), dp(8));
                    int tint = "STRIKE".equals(choice[0]) ? GOLD_BRIGHT
                            : "GUARD".equals(choice[0]) ? GREEN : Color.rgb(115, 165, 218);
                    moveCard.setBackground(gradient(PANEL_LIGHT, PANEL, tint, dp(10)));
                    TextView icon = text(choice[1], 23, tint, true);
                    icon.setGravity(Gravity.CENTER);
                    moveCard.addView(icon);
                    TextView label = text(choice[0], 10, TEXT, true);
                    label.setGravity(Gravity.CENTER);
                    moveCard.addView(label);
                    TextView explanation = text(choice[2], 7, MUTED, false);
                    explanation.setGravity(Gravity.CENTER);
                    explanation.setMaxLines(2);
                    moveCard.addView(explanation);
                    moveCard.setOnClickListener(v -> {
                        moveCard.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                                .withEndAction(() -> moveCard.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(150).start()).start();
                        confirmAction("Use " + choice[0] + "?",
                                "Your move is locked for this turn once submitted.",
                                () -> submitArenaMove(active.optString("match_id"), choice[0]));
                    });
                    LinearLayout.LayoutParams moveParams = new LinearLayout.LayoutParams(0,
                            dp(90), 1f);
                    moveParams.setMargins(dp(3), 0, dp(3), 0);
                    moves.addView(moveCard, moveParams);
                }
                content.addView(moves);
            } else {
                LinearLayout status = sectionPanel();
                status.setOrientation(LinearLayout.HORIZONTAL);
                status.setGravity(Gravity.CENTER_VERTICAL);
                status.addView(text("YOUR MOVE", 8, GOLD, true));
                TextView move = text(myMove.toUpperCase(java.util.Locale.ROOT), 12, GOLD_BRIGHT, true);
                move.setPadding(dp(8), dp(4), dp(8), dp(4));
                move.setBackground(gradient(Color.rgb(36, 46, 31), Color.rgb(23, 34, 27),
                        GOLD, dp(7)));
                LinearLayout.LayoutParams moveParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                moveParams.leftMargin = dp(10);
                status.addView(move, moveParams);
                TextView wait = text(active.optBoolean("opponentSubmitted")
                        ? "OPPONENT MOVE RECEIVED · RESOLVING"
                        : "WAITING FOR OPPONENT", 8, MUTED, true);
                wait.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                status.addView(wait, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                content.addView(status, rowParams());
            }
            TextView refresh = button("↻ REFRESH MATCH", false);
            refresh.setOnClickListener(v -> renderTab("arena"));
            content.addView(refresh, new LinearLayout.LayoutParams(dp(170), dp(35)));
        } else if (queue != null) {
            LinearLayout waiting = sectionPanel();
            waiting.setGravity(Gravity.CENTER);
            TextView icon = text("◌", 30, GOLD_BRIGHT, true);
            icon.setGravity(Gravity.CENTER);
            waiting.addView(icon);
            TextView headline = text("MATCHMAKING IN PROGRESS", 12, GOLD, true);
            headline.setGravity(Gravity.CENTER);
            waiting.addView(headline);
            TextView details = text(queue.optString("mode", "CASUAL") + " QUEUE · POWER "
                    + formatNumber(queue.optLong("power")), 15, TEXT, true);
            details.setGravity(Gravity.CENTER);
            waiting.addView(details);
            TextView hint = text("Refresh to check whether an opponent has joined.",
                    8, MUTED, false);
            hint.setGravity(Gravity.CENTER);
            waiting.addView(hint);
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER);
            TextView refresh = button("REFRESH QUEUE", true);
            refresh.setOnClickListener(v -> renderTab("arena"));
            TextView cancel = button("CANCEL SEARCH", false);
            cancel.setOnClickListener(v -> confirmAction("Cancel matchmaking?",
                    "You will leave the current arena queue.", this::cancelArenaQueue));
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(140), dp(36));
            actionParams.setMargins(dp(4), dp(8), dp(4), 0);
            actions.addView(refresh, actionParams);
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(140), dp(36));
            cancelParams.setMargins(dp(4), dp(8), dp(4), 0);
            actions.addView(cancel, cancelParams);
            waiting.addView(actions);
            content.addView(waiting, rowParams());
        } else {
            LinearLayout hero = sectionPanel();
            hero.setOrientation(LinearLayout.HORIZONTAL);
            hero.setGravity(Gravity.CENTER_VERTICAL);
            TextView crest = text("⚔", 42, GOLD_BRIGHT, true);
            hero.addView(crest, new LinearLayout.LayoutParams(dp(74),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.addView(text("ENTER THE ARENA", 15, TEXT, true));
            copy.addView(text("Choose a queue and challenge another collector.", 8, MUTED, false));
            hero.addView(copy, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            content.addView(hero, rowParams());
            TextView casual = button("CASUAL MATCH · FIND OPPONENT", true);
            casual.setOnClickListener(v -> joinArenaQueue("CASUAL"));
            content.addView(casual, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
            if (!data.optBoolean("rankedHidden") && !data.optBoolean("rankedBanned")) {
                TextView rankedMatch = button("RANKED MATCH · SEASON LADDER", false);
                rankedMatch.setOnClickListener(v -> joinArenaQueue("RANKED"));
                LinearLayout.LayoutParams rankedParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
                rankedParams.topMargin = dp(7);
                content.addView(rankedMatch, rankedParams);
            }
            showNotice("Strike deals damage, Guard reduces incoming damage, and Focus charges your next action.");
        }

        JSONArray recent = data.optJSONArray("recent");
        if (recent != null && recent.length() > 0) {
            addGap(content, 9);
            content.addView(text("RECENT MATCHES", 8, GOLD, true));
            for (int i = 0; i < recent.length(); i++) {
                JSONObject match = recent.optJSONObject(i);
                if (match == null) continue;
                boolean winner = me != null && me.optString("user_id")
                        .equals(match.optString("winner_id"));
                String opponent = me != null && me.optString("user_id")
                        .equals(match.optString("player_one_id"))
                        ? match.optString("player_two_name") : match.optString("player_one_name");
                content.addView(dataRow(opponent, match.optString("mode", "CASUAL") + " · "
                        + match.optString("completed_at", ""),
                        winner ? "VICTORY" : "COMPLETED"), rowParams());
            }
        }
    }

    private final class ArenaCardFace {
        final LinearLayout root;
        final CardArtView artwork;
        final TextView sigil;
        final TextView title;
        final TextView meta;
        final int accent;

        ArenaCardFace(String role, int accent) {
            this.accent = accent;
            root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(7), dp(7), dp(7), dp(7));
            root.setBackground(gradient(PANEL_LIGHT, PANEL, accent, dp(11)));

            TextView roleLabel = text(role, 7, accent, true);
            roleLabel.setGravity(Gravity.CENTER);
            roleLabel.setPadding(0, 0, 0, dp(4));
            root.addView(roleLabel);

            FrameLayout portrait = new FrameLayout(MainActivity.this);
            portrait.setBackground(gradient(Color.rgb(27, 43, 39),
                    Color.rgb(8, 15, 18), accent, dp(8)));
            sigil = text("✦", 38, accent, true);
            sigil.setGravity(Gravity.CENTER);
            sigil.setAlpha(0.9f);
            portrait.addView(sigil, new FrameLayout.LayoutParams(-1, -1));

            artwork = new CardArtView(MainActivity.this);
            FrameLayout.LayoutParams artworkParams = new FrameLayout.LayoutParams(-1, -1);
            artworkParams.setMargins(dp(4), dp(2), dp(4), dp(2));
            portrait.addView(artwork, artworkParams);
            root.addView(portrait, new LinearLayout.LayoutParams(-1, dp(116)));

            title = text("NO ACTIVE CARD", 9, TEXT, true);
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(1);
            title.setPadding(0, dp(4), 0, 0);
            root.addView(title);

            meta = text("Assign a card in Formation", 7, MUTED, false);
            meta.setGravity(Gravity.CENTER);
            meta.setMaxLines(1);
            root.addView(meta);
        }

        void bind(JSONObject card, String emptyTitle, String emptyMeta) {
            if (card == null) {
                artwork.clearArtwork();
                sigil.setText("?");
                sigil.setTextColor(MUTED);
                sigil.setAlpha(0.9f);
                title.setText(emptyTitle);
                meta.setText(emptyMeta);
                return;
            }
            String element = card.optString("element", "AETHER")
                    .toUpperCase(java.util.Locale.ROOT);
            String rarity = card.optString("rarity", "COMMON")
                    .toUpperCase(java.util.Locale.ROOT);
            String path = cardArtworkPath(card);
            artwork.loadArtwork(path);
            sigil.setText(elementSigil(element));
            sigil.setTextColor(accent);
            sigil.setAlpha(path.isEmpty() ? 0.72f : 0.16f);
            title.setText(card.optString("name", "Unknown Card"));
            meta.setText(rarity + " · " + element);
        }
    }

    private FrameLayout createArenaBattleStage(JSONObject active) {
        FrameLayout stage = new FrameLayout(this);
        stage.setPadding(dp(5), dp(5), dp(5), dp(5));
        stage.setBackground(gradient(Color.rgb(23, 39, 38),
                Color.rgb(7, 13, 18), GOLD, dp(14)));

        LinearLayout duel = new LinearLayout(this);
        duel.setOrientation(LinearLayout.HORIZONTAL);
        duel.setGravity(Gravity.CENTER_VERTICAL);

        ArenaCardFace playerFace = new ArenaCardFace("YOUR ACTIVE CARD", GREEN);
        ArenaCardFace opponentFace = new ArenaCardFace("RIVAL CARD", RED);
        JSONObject playerCard = firstArenaMatchCard(active, true);
        JSONObject opponentCard = firstArenaMatchCard(active, false);
        arenaLeaderCard = playerCard;
        arenaOpponentCard = opponentCard;
        playerFace.bind(playerCard, "NO ACTIVE CARD", "Assign a card in Formation");
        opponentFace.bind(opponentCard, "RIVAL LOADOUT", "Opponent artwork not shared");

        LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(0, -1, 1f);
        sideParams.setMargins(dp(3), 0, dp(3), 0);
        duel.addView(playerFace.root, sideParams);
        LinearLayout.LayoutParams rivalParams = new LinearLayout.LayoutParams(0, -1, 1f);
        rivalParams.setMargins(dp(3), 0, dp(3), 0);
        duel.addView(opponentFace.root, rivalParams);
        stage.addView(duel, new FrameLayout.LayoutParams(-1, -1));

        TextView versus = text("VS", 8, GOLD_BRIGHT, true);
        versus.setGravity(Gravity.CENTER);
        versus.setPadding(dp(5), dp(5), dp(5), dp(5));
        versus.setBackground(gradient(Color.rgb(24, 33, 30),
                Color.rgb(12, 20, 24), GOLD, dp(20)));
        FrameLayout.LayoutParams versusParams = new FrameLayout.LayoutParams(
                dp(36), dp(30), Gravity.CENTER);
        stage.addView(versus, versusParams);
        stage.setMinimumHeight(dp(184));

        if (playerCard == null) {
            api.get("/api/formation", result -> ui.post(() -> {
                if (!result.ok() || stage.getParent() == null) return;
                JSONObject leader = firstFormationCard(result.data);
                if (leader == null) return;
                arenaLeaderCard = leader;
                playerFace.bind(leader, "NO ACTIVE CARD", "Assign a card in Formation");
            }));
        }
        return stage;
    }

    private JSONObject firstArenaMatchCard(JSONObject match, boolean playerSide) {
        if (match == null) return null;
        String[] objectKeys = playerSide
                ? new String[]{"myCard", "my_card", "myActiveCard", "my_active_card", "playerCard", "player_card"}
                : new String[]{"opponentCard", "opponent_card", "opponentActiveCard",
                        "opponent_active_card", "enemyCard", "enemy_card"};
        String[] arrayKeys = playerSide
                ? new String[]{"myCards", "my_cards", "myFormation", "my_formation", "playerCards"}
                : new String[]{"opponentCards", "opponent_cards", "opponentFormation",
                        "opponent_formation", "enemyCards"};
        for (String key : objectKeys) {
            JSONObject card = match.optJSONObject(key);
            if (card != null) {
                JSONObject nested = card.optJSONObject("card");
                return nested == null ? card : nested;
            }
        }
        for (String key : arrayKeys) {
            JSONArray cards = match.optJSONArray(key);
            if (cards != null && cards.length() > 0) {
                JSONObject card = cards.optJSONObject(0);
                if (card != null) {
                    JSONObject nested = card.optJSONObject("card");
                    return nested == null ? card : nested;
                }
            }
        }
        if (!playerSide) {
            JSONObject opponent = match.optJSONObject("opponent");
            if (opponent != null) {
                JSONObject card = opponent.optJSONObject("card");
                if (card == null) card = opponent.optJSONObject("activeCard");
                if (card == null) {
                    JSONArray cards = opponent.optJSONArray("formation");
                    if (cards != null && cards.length() > 0) card = cards.optJSONObject(0);
                }
                return card;
            }
        }
        return null;
    }

    private JSONObject firstFormationCard(JSONObject formationData) {
        if (formationData == null) return null;
        JSONArray cards = formationData.optJSONArray("cards");
        JSONArray slots = formationData.optJSONArray("formation");
        if (cards == null || slots == null) return null;
        Map<String, JSONObject> byId = new LinkedHashMap<>();
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.optJSONObject(i);
            if (card == null) continue;
            String id = card.optString("card_id", "");
            if (!id.isEmpty()) byId.put(id, card);
        }
        JSONObject selected = null;
        int selectedSlot = Integer.MAX_VALUE;
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.optJSONObject(i);
            if (slot == null) continue;
            int slotNo = slot.optInt("slot_no", i + 1);
            JSONObject card = byId.get(slot.optString("card_id", ""));
            if (card != null && slotNo < selectedSlot) {
                selected = card;
                selectedSlot = slotNo;
            }
        }
        return selected;
    }

    private LinearLayout createArenaFighterPanel(String role, String name,
                                                  long hp, long maxHp, int accent) {
        LinearLayout panel = sectionPanel();
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.addView(text(role, 7, accent, true));
        TextView fighter = text(name, 13, TEXT, true);
        fighter.setMaxLines(1);
        panel.addView(fighter);
        TextView health = text(formatNumber(hp) + " HP"
                + (maxHp > 0 ? " / " + formatNumber(maxHp) : ""), 16, accent, true);
        health.setPadding(0, dp(6), 0, dp(5));
        panel.addView(health);
        if (maxHp > 0) {
            ProgressBar bar = new ProgressBar(this, null,
                    android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress((int) Math.max(0, Math.min(100, Math.round(hp * 100d / maxHp))));
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            panel.addView(bar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
        } else {
            panel.addView(text("LIVE HEALTH", 7, MUTED, false));
        }
        return panel;
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
        JSONArray cards = data.optJSONArray("cards");
        if (cards == null) {
            showNotice("No card data was returned.");
            return;
        }

        int ownedTypes = 0;
        int totalCopies = 0;
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.optJSONObject(i);
            if (card != null && card.optInt("owned_count") > 0) {
                ownedTypes++;
                totalCopies += card.optInt("owned_count");
            }
        }

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.addView(statCard("UNIQUE CARDS", String.valueOf(ownedTypes), "In your collection"),
                weightParams(1));
        summary.addView(statCard("TOTAL COPIES", formatNumber(totalCopies), "Owned card copies"),
                weightParams(1));
        summary.addView(statCard("CATALOGUE", String.valueOf(cards.length()), "Available card designs"),
                weightParams(1));
        content.addView(summary);
        addGap(content, 10);

        LinearLayout filters = sectionPanel();
        filters.addView(text("SEARCH THE ARCHIVE", 8, GOLD, true));
        EditText search = editField("Card name, element or card code", false);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        filters.addView(search, fieldParams());

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        Spinner ownership = new Spinner(this);
        String[] ownershipOptions = {"ALL CARDS", "OWNED ONLY", "NOT OWNED"};
        ArrayAdapter<String> ownershipAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, ownershipOptions);
        ownershipAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ownership.setAdapter(ownershipAdapter);
        Spinner rarity = new Spinner(this);
        String[] rarityOptions = {"ALL RARITIES", "COMMON", "UNCOMMON", "RARE",
                "EPIC", "LEGENDARY", "MYTHIC"};
        ArrayAdapter<String> rarityAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, rarityOptions);
        rarityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rarity.setAdapter(rarityAdapter);
        filterRow.addView(ownership, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams rarityParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        rarityParams.leftMargin = dp(7);
        filterRow.addView(rarity, rarityParams);
        filters.addView(filterRow);
        content.addView(filters, rowParams());

        TextView resultCount = text("LOADING CARDS", 8, GOLD_BRIGHT, true);
        resultCount.setPadding(dp(2), dp(9), dp(2), dp(5));
        content.addView(resultCount);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        content.addView(grid);

        Runnable updateGrid = () -> {
            if (grid.getParent() == null) return;
            grid.removeAllViews();
            String query = search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            int scope = ownership.getSelectedItemPosition();
            String selectedRarity = rarityOptions[Math.max(0, rarity.getSelectedItemPosition())];
            java.util.ArrayList<JSONObject> filtered = new java.util.ArrayList<>();
            int ownedInFilter = 0;
            for (int i = 0; i < cards.length(); i++) {
                JSONObject card = cards.optJSONObject(i);
                if (card == null) continue;
                int copies = card.optInt("owned_count");
                if (scope == 1 && copies <= 0) continue;
                if (scope == 2 && copies > 0) continue;
                String cardRarity = card.optString("rarity", "COMMON").toUpperCase(java.util.Locale.ROOT);
                if (!"ALL RARITIES".equals(selectedRarity) && !selectedRarity.equals(cardRarity)) continue;
                String searchable = (card.optString("name") + " " + card.optString("element")
                        + " " + card.optString("card_code") + " " + cardRarity)
                        .toLowerCase(java.util.Locale.ROOT);
                if (!query.isEmpty() && !searchable.contains(query)) continue;
                filtered.add(card);
                if (copies > 0) ownedInFilter++;
            }

            resultCount.setText(filtered.size() + " CARDS FOUND  ·  " + ownedInFilter + " OWNED");
            if (filtered.isEmpty()) {
                TextView empty = text("No cards match these filters. Try another search or rarity.",
                        9, MUTED, false);
                empty.setPadding(dp(8), dp(18), dp(8), dp(18));
                grid.addView(empty);
                return;
            }

            int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                    / getResources().getDisplayMetrics().density);
            int columns = widthDp >= 1100 ? 4 : widthDp >= 800 ? 3 : 2;
            LinearLayout currentRow = null;
            for (int i = 0; i < filtered.size(); i++) {
                if (i % columns == 0) {
                    currentRow = new LinearLayout(this);
                    currentRow.setOrientation(LinearLayout.HORIZONTAL);
                    grid.addView(currentRow, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                JSONObject card = filtered.get(i);
                LinearLayout tile = createCardTile(card);
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tileParams.setMargins(dp(3), dp(3), dp(3), dp(6));
                currentRow.addView(tile, tileParams);
            }
            int remainder = filtered.size() % columns;
            if (remainder != 0 && currentRow != null) {
                for (int i = remainder; i < columns; i++) {
                    currentRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                }
            }
        };

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateGrid.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateGrid.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        ownership.setOnItemSelectedListener(filterListener);
        rarity.setOnItemSelectedListener(filterListener);
        updateGrid.run();
        addGap(content, 10);
        TextView duplicates = button("OPEN DUPLICATE CONVERTER", false);
        duplicates.setOnClickListener(v -> loadDuplicateConversionPanel());
        content.addView(duplicates, new LinearLayout.LayoutParams(dp(220), dp(36)));
    }

    private String cardArtworkPath(JSONObject card) {
        if (card == null) return "";
        String code = card.optString("card_code", "").trim().toUpperCase(java.util.Locale.ROOT);
        String name = card.optString("name", "").toLowerCase(java.util.Locale.ROOT);
        if ("FL-001".equals(code) && name.contains("fox")) return "/art/cinder-fox.webp";
        String path = card.optString("art_path", "").trim();
        if (path.startsWith("/media/cards/") || path.startsWith("/art/")) return path;
        if (code.matches("(FL|MY|EV)-\\d{3}")) return "/media/cards/artwork/" + code + ".svg";
        return "";
    }

    private LinearLayout createCardTile(JSONObject card) {
        String name = card.optString("name", "Unknown Card");
        String rarity = card.optString("rarity", "COMMON").toUpperCase(java.util.Locale.ROOT);
        String element = card.optString("element", "AETHER").toUpperCase(java.util.Locale.ROOT);
        int copies = card.optInt("owned_count");
        int accent = cardRarityColor(rarity);

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(8), dp(8), dp(8), dp(8));
        tile.setBackground(gradient(PANEL_LIGHT, PANEL, accent, dp(12)));
        tile.setMinimumHeight(dp(184));

        FrameLayout art = new FrameLayout(this);
        art.setBackground(gradient(
                Color.rgb(Color.red(accent) / 3 + 8, Color.green(accent) / 3 + 12,
                        Color.blue(accent) / 3 + 14),
                Color.rgb(12, 21, 19), accent, dp(9)));
        TextView sigil = text(elementSigil(element), 34, accent, true);
        sigil.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams sigilParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        sigil.setAlpha(0.16f);
        art.addView(sigil, sigilParams);
        CardArtView illustration = new CardArtView(this);
        illustration.loadArtwork(cardArtworkPath(card));
        FrameLayout.LayoutParams illustrationParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        illustrationParams.setMargins(dp(5), dp(2), dp(5), dp(2));
        art.addView(illustration, illustrationParams);
        TextView elementLabel = text(element, 7, GOLD_BRIGHT, true);
        elementLabel.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        FrameLayout.LayoutParams elementParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.RIGHT);
        elementParams.setMargins(0, 0, dp(7), dp(5));
        art.addView(elementLabel, elementParams);
        tile.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(116)));

        TextView rarityLabel = text(rarity, 7, accent, true);
        rarityLabel.setPadding(0, dp(6), 0, dp(2));
        tile.addView(rarityLabel);
        TextView title = text(name, 11, TEXT, true);
        title.setMaxLines(2);
        tile.addView(title);
        TextView code = text(card.optString("card_code", "CARD"), 7, MUTED, false);
        code.setPadding(0, dp(2), 0, dp(5));
        tile.addView(code);

        TextView ownership = text(copies > 0 ? "OWNED  ×" + copies : "NOT COLLECTED",
                7, copies > 0 ? GREEN : MUTED, true);
        ownership.setPadding(dp(7), dp(5), dp(7), dp(5));
        ownership.setBackground(gradient(Color.rgb(10, 20, 16), Color.rgb(10, 20, 16),
                copies > 0 ? GREEN : Color.rgb(67, 78, 69), dp(6)));
        tile.addView(ownership);
        tile.setOnClickListener(v -> showCardDetails(card));
        return tile;
    }

    private int cardRarityColor(String rarity) {
        switch (rarity) {
            case "UNCOMMON": return Color.rgb(106, 183, 137);
            case "RARE": return Color.rgb(100, 157, 216);
            case "EPIC": return Color.rgb(172, 132, 220);
            case "LEGENDARY": return Color.rgb(222, 179, 91);
            case "MYTHIC": return Color.rgb(222, 116, 145);
            default: return Color.rgb(151, 166, 153);
        }
    }

    private String elementSigil(String element) {
        switch (element) {
            case "FIRE": return "炎";
            case "WATER": return "≈";
            case "EARTH": return "◆";
            case "WIND": return "〰";
            case "LIGHT": return "☼";
            case "SHADOW": return "◐";
            case "VOID": return "◈";
            default: return "✧";
        }
    }

    private void showCardDetails(JSONObject c) {
        String rarity = c.optString("rarity", "COMMON").toUpperCase(java.util.Locale.ROOT);
        String element = c.optString("element", "AETHER").toUpperCase(java.util.Locale.ROOT);
        int accent = cardRarityColor(rarity);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(8), dp(14), dp(8));
        panel.setBackground(gradient(PANEL_LIGHT, PANEL, accent, dp(14)));
        FrameLayout showcase = new FrameLayout(this);
        showcase.setBackground(gradient(Color.rgb(24, 38, 35), Color.rgb(8, 16, 19), accent, dp(12)));
        TextView fallback = text(elementSigil(element), 42, accent, true);
        fallback.setGravity(Gravity.CENTER);
        fallback.setAlpha(0.22f);
        showcase.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        CardArtView art = new CardArtView(this);
        art.loadArtwork(cardArtworkPath(c));
        showcase.addView(art, new FrameLayout.LayoutParams(-1, -1));
        panel.addView(showcase, new LinearLayout.LayoutParams(-1, dp(220)));
        TextView code = text(c.optString("card_code", "CARD") + "  ·  " + rarity + "  ·  " + element, 8, GOLD_BRIGHT, true);
        code.setPadding(0, dp(10), 0, dp(5));
        panel.addView(code);
        TextView description = text(c.optString("description", "A collectible from the Firstlight Realm."), 10, MUTED, false);
        description.setLineSpacing(dp(3), 1f);
        panel.addView(description);
        panel.addView(text("OWNED COPIES  ·  " + c.optInt("owned_count"), 8, GREEN, true));
        new AlertDialog.Builder(this).setTitle(c.optString("name", "Card Details"))
                .setView(panel).setPositiveButton("CLOSE", null).show();
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
        JSONArray cards = data.optJSONArray("cards");
        JSONArray saved = data.optJSONArray("formation");
        if (cards == null) cards = new JSONArray();
        if (saved == null) saved = new JSONArray();
        if (cards.length() == 0) {
            showNotice("Collect at least one card before building a formation.");
            return;
        }

        Map<Integer, String> savedSlots = new LinkedHashMap<>();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject slot = saved.optJSONObject(i);
            if (slot != null) savedSlots.put(slot.optInt("slot_no"), slot.optString("card_id"));
        }

        String[] labels = new String[cards.length() + 1];
        String[] ids = new String[cards.length() + 1];
        labels[0] = "Empty slot";
        ids[0] = "";
        Map<String, JSONObject> cardsById = new LinkedHashMap<>();
        for (int i = 0; i < cards.length(); i++) {
            labels[i + 1] = "Unknown card";
            ids[i + 1] = "";
            JSONObject card = cards.optJSONObject(i);
            if (card == null) continue;
            String id = card.optString("card_id", "");
            ids[i + 1] = id;
            labels[i + 1] = card.optString("name", "Unknown card") + " · "
                    + card.optString("element", "AETHER") + " · "
                    + card.optString("rarity", "COMMON") + " ×" + card.optInt("owned_count");
            if (!id.isEmpty()) cardsById.put(id, card);
        }

        LinearLayout power = sectionPanel();
        power.setOrientation(LinearLayout.HORIZONTAL);
        power.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout powerCopy = new LinearLayout(this);
        powerCopy.setOrientation(LinearLayout.VERTICAL);
        powerCopy.addView(text("FORMATION POWER", 8, GOLD, true));
        powerCopy.addView(text(formatNumber(data.optLong("power")), 20, TEXT, true));
        power.addView(powerCopy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        JSONObject synergy = data.optJSONObject("synergy");
        if (synergy != null) {
            LinearLayout resonance = sectionPanel();
            resonance.addView(text("RESONANCE", 7, GOLD, true));
            resonance.addView(text(synergy.optString("title", "No active resonance"), 9, TEXT, true));
            resonance.addView(text("+" + synergy.optInt("bonus_pct") + "% BONUS", 8, GREEN, true));
            power.addView(resonance);
        }
        content.addView(power, rowParams());
        addGap(content, 8);
        content.addView(text("YOUR FIVE-CARD LINEUP", 8, GOLD, true));
        content.addView(text("Choose a card for each position. Each card can only be used once.",
                8, MUTED, false));
        addGap(content, 5);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        Spinner[] slots = new Spinner[5];

        for (int i = 0; i < 5; i++) {
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(6), dp(7), dp(6), dp(7));
            panel.setBackground(gradient(PANEL_LIGHT, PANEL, Color.rgb(72, 88, 67), dp(10)));
            panel.addView(text("POSITION 0" + (i + 1), 7, GOLD, true));

            FrameLayout art = new FrameLayout(this);
            art.setBackground(gradient(Color.rgb(26, 45, 37), Color.rgb(10, 19, 16),
                    Color.rgb(82, 104, 77), dp(8)));
            TextView sigil = text("＋", 29, MUTED, true);
            sigil.setGravity(Gravity.CENTER);
            art.addView(sigil, new FrameLayout.LayoutParams(-1, -1));
            CardArtView illustration = new CardArtView(this);
            illustration.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            art.addView(illustration, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(112));
            artParams.topMargin = dp(6);
            panel.addView(art, artParams);

            TextView cardName = text("EMPTY SLOT", 9, TEXT, true);
            cardName.setMaxLines(2);
            cardName.setGravity(Gravity.CENTER);
            cardName.setPadding(0, dp(5), 0, dp(2));
            panel.addView(cardName);
            TextView cardMeta = text("Choose a card", 7, MUTED, false);
            cardMeta.setGravity(Gravity.CENTER);
            cardMeta.setMaxLines(2);
            panel.addView(cardMeta);

            Spinner spinner = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            String savedId = savedSlots.get(i + 1);
            for (int j = 0; j < ids.length; j++) {
                if (savedId != null && savedId.equals(ids[j])) {
                    spinner.setSelection(j);
                    break;
                }
            }
            final int slotIndex = i;
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String selectedId = position >= 0 && position < ids.length ? ids[position] : "";
                    updateFormationCardPreview(cardsById.get(selectedId), art, illustration, sigil, cardName, cardMeta);
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
            slots[i] = spinner;
            panel.addView(spinner, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            colParams.setMargins(dp(3), dp(5), dp(3), dp(5));
            grid.addView(panel, colParams);
            String initialId = spinner.getSelectedItemPosition() >= 0
                    && spinner.getSelectedItemPosition() < ids.length
                    ? ids[spinner.getSelectedItemPosition()] : "";
            updateFormationCardPreview(cardsById.get(initialId), art, illustration, sigil, cardName, cardMeta);
        }

        content.addView(grid);
        TextView save = button("SAVE FORMATION", true);
        save.setOnClickListener(v -> {
            JSONArray chosen = new JSONArray();
            java.util.HashSet<String> unique = new java.util.HashSet<>();
            for (Spinner spinner : slots) {
                int index = spinner.getSelectedItemPosition();
                String id = index >= 0 && index < ids.length ? ids[index] : "";
                if (id.isEmpty()) continue;
                if (!unique.add(id)) {
                    showResultDialog("Duplicate selection",
                            "Each card can only occupy one formation slot.");
                    return;
                }
                chosen.put(id);
            }
            save.setEnabled(false);
            save.setText("SAVING...");
            api.post("/api/formation", payload("cards", chosen), result -> ui.post(() -> {
                save.setEnabled(true);
                save.setText("SAVE FORMATION");
                if (!result.ok()) {
                    showResultDialog("Formation not saved", result.error);
                } else {
                    showResultDialog("FORMATION SAVED",
                            "Effective power: " + formatNumber(result.data.optLong("power")));
                    refreshAccount(() -> renderTab("formation"));
                }
            }));
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(dp(190), dp(40));
        saveParams.topMargin = dp(10);
        content.addView(save, saveParams);
    }

    private void updateFormationCardPreview(JSONObject card, FrameLayout art,
                                            CardArtView illustration, TextView sigil,
                                            TextView name, TextView meta) {
        if (card == null) {
            art.setBackground(gradient(Color.rgb(26, 45, 37), Color.rgb(10, 19, 16),
                    Color.rgb(82, 104, 77), dp(8)));
            illustration.clearArtwork();
            sigil.setAlpha(1f);
            sigil.setText("＋");
            sigil.setTextColor(MUTED);
            name.setText("EMPTY SLOT");
            name.setTextColor(TEXT);
            meta.setText("Choose a card");
            meta.setTextColor(MUTED);
            return;
        }
        String element = card.optString("element", "AETHER").toUpperCase(java.util.Locale.ROOT);
        String rarity = card.optString("rarity", "COMMON").toUpperCase(java.util.Locale.ROOT);
        int accent = cardRarityColor(rarity);
        art.setBackground(gradient(Color.rgb(31, 50, 39), Color.rgb(10, 19, 16), accent, dp(8)));
        sigil.setText(elementSigil(element));
        sigil.setTextColor(accent);
        sigil.setAlpha(0.16f);
        illustration.loadArtwork(cardArtworkPath(card));
        name.setText(card.optString("name", "Unknown Card"));
        name.setTextColor(TEXT);
        meta.setText(rarity + " · " + element + " · ×" + card.optInt("owned_count"));
        meta.setTextColor(accent);
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
        int level=data.optInt("level",1),xp=data.optInt("xp");
        boolean maxLevel=level>=1000;
        int need=maxLevel?Math.max(1,xp):Math.max(1,data.optInt("xpRequired"));
        int page=data.optInt("rewardPage"),pageSize=data.optInt("rewardPageSize",30),total=data.optInt("rewardTotal");
        int pages=Math.max(1,(int)Math.ceil(total/(double)Math.max(1,pageSize)));
        LinearLayout hero=sectionPanel();hero.addView(text(maxLevel?"✦ MAX COLLECTOR LEVEL":"COLLECTOR LEVEL "+level,9,GOLD,true));
        hero.addView(text(maxLevel?"LEVEL CAP REACHED · "+formatNumber(xp)+" TOTAL XP":formatNumber(xp)+" / "+formatNumber(need)+" XP TO NEXT LEVEL",13,TEXT,true));
        ProgressBar xpBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);xpBar.setMax(100);
        xpBar.setProgress(maxLevel?100:Math.min(100,Math.round(xp*100f/need)));xpBar.setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD));
        hero.addView(xpBar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(8)));
        hero.addView(text((maxLevel?"MAX RANK · ":"")+data.optInt("claimableCount")+" milestone rewards ready · level cap 1,000",8,MUTED,false));
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
        showGameDialog("ACTION REQUIRED", title, message, true, action);
    }

    private void showResultDialog(String title, String message) {
        showGameDialog("REALM UPDATE", title,
                message == null || message.trim().isEmpty() ? "Action completed." : message,
                false, null);
    }

    private void showGameDialog(String eyebrow, String title, String message,
                                boolean confirm, Runnable action) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(20), dp(17), dp(20), dp(17));
        shell.setBackground(gradient(Color.rgb(29, 30, 59), Color.rgb(9, 13, 29),
                Color.rgb(133, 111, 228), dp(18)));

        TextView label = text(eyebrow, 8, Color.rgb(181, 164, 255), true);
        label.setLetterSpacing(0.18f);
        shell.addView(label);
        TextView heading = text(title == null ? "REALM MESSAGE" :
                title.toUpperCase(java.util.Locale.ROOT), 17, TEXT, true);
        heading.setPadding(0, dp(6), 0, dp(7));
        heading.setMaxLines(2);
        shell.addView(heading);
        View rule = new View(this);
        rule.setBackgroundColor(Color.rgb(69, 67, 108));
        shell.addView(rule, new LinearLayout.LayoutParams(-1, dp(1)));
        TextView body = text(message == null ? "" : message, 10, MUTED, false);
        body.setLineSpacing(dp(4), 1.05f);
        body.setPadding(0, dp(12), 0, dp(16));
        shell.addView(body);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (confirm) {
            TextView cancel = button("CANCEL", false);
            actions.addView(cancel, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
            View gap = new View(this);
            actions.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));
            cancel.setOnClickListener(v -> dialog.dismiss());
        }
        TextView proceed = button(confirm ? "CONTINUE" : "GOT IT", true);
        actions.addView(proceed, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        proceed.setOnClickListener(v -> {
            dialog.dismiss();
            if (action != null) action.run();
        });
        shell.addView(actions);
        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(!confirm);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            int width = Math.min(dp(560), getResources().getDisplayMetrics().widthPixels - dp(36));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showPackRevealDialog(JSONArray cards) {
        if (cards == null || cards.length() == 0) {
            showResultDialog("PACK OPENED", "Your collection has been updated.");
            return;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(12), dp(10), dp(12), dp(10));
        scroll.addView(layout);

        LinearLayout hero = sectionPanel();
        hero.setGravity(Gravity.CENTER);
        TextView seal = text("✦", 40, GOLD_BRIGHT, true);
        seal.setGravity(Gravity.CENTER);
        hero.addView(seal);
        TextView headline = text("SUMMON COMPLETE", 13, GOLD_BRIGHT, true);
        headline.setGravity(Gravity.CENTER);
        headline.setLetterSpacing(0.12f);
        hero.addView(headline);
        TextView subline = text(cards.length() + " CARD" + (cards.length() == 1 ? "" : "S")
                + " ADDED TO YOUR ARCHIVE", 8, MUTED, true);
        subline.setGravity(Gravity.CENTER);
        hero.addView(subline);
        layout.addView(hero, rowParams());
        addGap(layout, 8);
        layout.addView(text("YOUR NEWLY SUMMONED CARDS", 8, GOLD, true));
        addGap(layout, 5);

        int columns = cards.length() >= 3 ? 3 : Math.max(1, cards.length());
        LinearLayout row = null;
        int visible = 0;
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.optJSONObject(i);
            if (card == null) continue;
            if (visible % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                layout.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout tile = createSummonRevealTile(card, visible + 1);
            tile.setAlpha(0f);
            tile.setTranslationY(dp(12));
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tileParams.setMargins(dp(3), dp(3), dp(3), dp(6));
            row.addView(tile, tileParams);
            tile.animate().alpha(1f).translationY(0f)
                    .setStartDelay(Math.min(visible, 8) * 75L).setDuration(240).start();
            visible++;
        }
        if (row != null) {
            int remainder = visible % columns;
            if (remainder != 0) {
                for (int i = remainder; i < columns; i++) {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                }
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("✦  PACK REVEAL")
                .setView(scroll)
                .setPositiveButton("BACK TO SUMMON",
                        (dialog, which) -> refreshAccount(() -> renderTab("summon")))
                .setOnCancelListener(dialog -> refreshAccount(null))
                .show();
    }

    private LinearLayout createSummonRevealTile(JSONObject card, int order) {
        String rarity = card.optString("rarity", "COMMON").toUpperCase(java.util.Locale.ROOT);
        String element = card.optString("element", "AETHER").toUpperCase(java.util.Locale.ROOT);
        int accent = cardRarityColor(rarity);
        LinearLayout tile = sectionPanel();
        tile.setPadding(dp(7), dp(7), dp(7), dp(8));
        FrameLayout art = new FrameLayout(this);
        art.setBackground(gradient(Color.rgb(30, 48, 39), Color.rgb(10, 18, 16), accent, dp(9)));
        TextView glyph = text(elementSigil(element), 34, accent, true);
        glyph.setGravity(Gravity.CENTER);
        glyph.setAlpha(0.16f);
        art.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
        CardArtView illustration = new CardArtView(this);
        illustration.loadArtwork(cardArtworkPath(card));
        art.addView(illustration, new FrameLayout.LayoutParams(-1, -1));
        tile.addView(art, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(116)));
        TextView number = text(String.format(java.util.Locale.US, "CARD %02d", order),
                7, MUTED, true);
        number.setPadding(0, dp(5), 0, 0);
        tile.addView(number);
        TextView title = text(card.optString("name", "Unknown Card"), 10, TEXT, true);
        title.setMaxLines(2);
        tile.addView(title);
        TextView badge = text(rarity, 7, accent, true);
        badge.setPadding(0, dp(3), 0, 0);
        tile.addView(badge);
        tile.addView(text(element, 7, MUTED, false));
        return tile;
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

    // Refresh shared account data quietly while the game stays in the foreground.
    private void startAutoSync() {
        ui.removeCallbacks(autoSyncTick);
        if (api == null || me == null || appRoot == null) return;
        syncWithSharedBackend(false);
        ui.postDelayed(autoSyncTick, AUTO_SYNC_INTERVAL_MS);
    }

    private void syncWithSharedBackend(boolean force) {
        if (api == null || me == null || syncingAccount) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (!force && now - lastAutoSyncAt < 45_000L) return;
        lastAutoSyncAt = now;
        syncingAccount = true;
        api.get("/api/me", result -> ui.post(() -> {
            if (!result.ok()) {
                syncingAccount = false;
                if (result.status == 401) {
                    api.clearSession();
                    me = null;
                    adminProfile = null;
                    showAuth(false, "Your session expired. Please sign in again.");
                }
                return;
            }
            acceptMe(result.data);
            if (accountLabel != null) accountLabel.setText(me.optString("display_name", "Collector"));
            if (currencyLabel != null) currencyLabel.setText(walletLine());
            api.get("/api/progression", progressResult -> ui.post(() -> {
                if (progressResult.ok()) progression = progressResult.data;
                syncingAccount = false;
                if (isAdminAccount()) loadAdminProfile();
            }));
        }));
    }

    private boolean isAdminAccount() {
        return me != null && "admin".equalsIgnoreCase(me.optString("role", ""));
    }

    private boolean isGodModeEnabled() {
        if (!isAdminAccount()) return false;
        int access = me.optInt("admin_access_level", 0);
        if (adminProfile != null) {
            JSONObject profile = adminProfile.optJSONObject("profile");
            if (profile != null) access = Math.max(access, profile.optInt("access_level", 0));
        }
        return access >= 100;
    }

    private void loadAdminProfile() {
        if (!isAdminAccount() || api == null) {
            adminProfile = null;
            return;
        }
        api.get("/api/admin/profile", result -> ui.post(() -> {
            if (!result.ok()) return;
            adminProfile = result.data;
            if ("home".equals(activeTab) && content != null) renderTab("home");
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
            String message = result.data.optString("message",
                    result.data.optBoolean("waiting") ? "Move locked. Waiting for your opponent." : "Match updated.");
            showArenaMoveEffect(move, message, result.data.optBoolean("waiting"));
        }));
    }

    private void showArenaMoveEffect(String move, String serverMessage, boolean waiting) {
        String action = move == null ? "FOCUS" : move.toUpperCase(java.util.Locale.ROOT);
        int accent = "STRIKE".equals(action) ? Color.rgb(244, 151, 100)
                : "GUARD".equals(action) ? Color.rgb(124, 211, 164)
                : Color.rgb(153, 174, 255);
        String glyph = "STRIKE".equals(action) ? "⚔" : "GUARD".equals(action) ? "◈" : "✦";

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(gradient(Color.rgb(25, 37, 37), Color.rgb(8, 15, 22), accent, dp(18)));

        FrameLayout stage = new FrameLayout(this);
        stage.setBackground(gradient(Color.rgb(31, 47, 50), Color.rgb(10, 19, 26), accent, dp(15)));

        CardArtView attacker = new CardArtView(this);
        if (arenaLeaderCard != null) attacker.loadArtwork(cardArtworkPath(arenaLeaderCard));
        FrameLayout.LayoutParams attackerParams = new FrameLayout.LayoutParams(
                dp(112), dp(118), Gravity.CENTER_VERTICAL | Gravity.LEFT);
        attackerParams.leftMargin = dp(7);
        stage.addView(attacker, attackerParams);

        CardArtView defender = new CardArtView(this);
        if (arenaOpponentCard != null) defender.loadArtwork(cardArtworkPath(arenaOpponentCard));
        FrameLayout.LayoutParams defenderParams = new FrameLayout.LayoutParams(
                dp(112), dp(118), Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        defenderParams.rightMargin = dp(7);
        stage.addView(defender, defenderParams);

        TextView rune = text(glyph, 58, accent, true);
        rune.setGravity(Gravity.CENTER);
        rune.setShadowLayer(dp(18), 0, 0, accent);
        stage.addView(rune, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(-1, dp(136));
        stageParams.bottomMargin = dp(12);
        panel.addView(stage, stageParams);

        TextView kicker = text("ARENA ACTION · " + (waiting ? "TURN LOCKED" : "SERVER CONFIRMED"),
                8, GOLD_BRIGHT, true);
        kicker.setGravity(Gravity.CENTER);
        panel.addView(kicker);
        TextView title = text(action + (waiting ? " READY" : " RESOLVED"), 19, TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(4), 0, dp(6));
        panel.addView(title);
        TextView detail = text(serverMessage, 9, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        detail.setLineSpacing(dp(3), 1f);
        panel.addView(detail);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("⚔  BATTLE EFFECT")
                .setView(panel)
                .setPositiveButton("CONTINUE", (d, w) -> renderTab("arena"))
                .create();
        dialog.setOnShowListener(d -> {
            rune.setScaleX(0.45f);
            rune.setScaleY(0.45f);
            rune.setAlpha(0f);
            stage.setAlpha(0.65f);
            attacker.setTranslationX(-dp(13));
            defender.setTranslationX(dp(13));
            rune.animate().scaleX(1.08f).scaleY(1.08f).alpha(1f).setDuration(320)
                    .withEndAction(() -> rune.animate().scaleX(1f).scaleY(1f)
                            .setDuration(150).start()).start();
            stage.animate().alpha(1f).setDuration(300).start();

            if ("STRIKE".equals(action)) {
                attacker.animate().translationX(dp(24)).scaleX(1.08f).scaleY(1.08f)
                        .setDuration(210).withEndAction(() ->
                                attacker.animate().translationX(0).scaleX(1f).scaleY(1f)
                                        .setDuration(190).start()).start();
                defender.animate().alpha(0.38f).setStartDelay(160).setDuration(100)
                        .withEndAction(() -> defender.animate().alpha(1f).setDuration(220).start()).start();
                rune.animate().rotation(-28f).translationY(-dp(6)).setDuration(170)
                        .withEndAction(() -> rune.animate().rotation(0f).translationY(0)
                                .setDuration(200).start()).start();
            } else if ("GUARD".equals(action)) {
                attacker.animate().scaleX(1.08f).scaleY(1.08f).setDuration(220)
                        .withEndAction(() -> attacker.animate().scaleX(1f).scaleY(1f)
                                .setDuration(220).start()).start();
                rune.animate().rotation(180f).setDuration(420)
                        .withEndAction(() -> rune.setRotation(0f)).start();
            } else {
                rune.animate().scaleX(1.34f).scaleY(1.34f).alpha(0.65f).setDuration(300)
                        .withEndAction(() -> rune.animate().scaleX(1f).scaleY(1f).alpha(1f)
                                .setDuration(300).start()).start();
                attacker.animate().translationY(-dp(8)).setDuration(300)
                        .withEndAction(() -> attacker.animate().translationY(0)
                                .setDuration(260).start()).start();
            }
        });
        dialog.show();
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
        description.setBackground(gradient(Color.rgb(10, 14, 30), Color.rgb(10, 14, 30), Color.rgb(73, 75, 122), dp(10)));
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
        row.setBackground(gradient(Color.rgb(24, 31, 55), Color.rgb(13, 18, 35), Color.rgb(63, 66, 112), dp(11)));
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
        panel.setBackground(gradient(PANEL_LIGHT, PANEL, Color.rgb(72, 70, 129), dp(13)));
        panel.setElevation(dp(2));
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
        edit.setTextSize(isCompactLayout() ? 13 : 11);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setHint(hint);
        edit.setPadding(dp(12), 0, dp(12), 0);
        edit.setBackground(gradient(Color.rgb(10, 14, 30), Color.rgb(10, 14, 30), Color.rgb(73, 75, 122), dp(10)));
        edit.setSelectAllOnFocus(false);
        return edit;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        lp.bottomMargin = dp(8);
        return lp;
    }

    private TextView button(String label, boolean primary) {
        int top = primary ? Color.rgb(197, 180, 255) : Color.rgb(34, 40, 68);
        int bottom = primary ? Color.rgb(126, 102, 219) : Color.rgb(17, 22, 42);
        int ink = primary ? Color.rgb(13, 14, 29) : Color.rgb(219, 212, 255);
        TextView control = text(label, 9, ink, true);
        control.setGravity(Gravity.CENTER);
        control.setLetterSpacing(0.07f);
        control.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable shape = gradient(top, bottom,
                primary ? Color.rgb(218, 204, 255) : Color.rgb(93, 85, 145), dp(10));
        control.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(74, 255, 255, 255)),
                shape, null));
        control.setMinimumHeight(dp(40));
        control.setElevation(dp(2));
        control.setClickable(true);
        control.setFocusable(true);
        return control;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(isCompactLayout() ? size * 1.12f : size);
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
    protected void onResume() {
        super.onResume();
        startAutoSync();
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(autoSyncTick);
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean nextCompact = isCompactLayout();
        if (nextCompact == compactLayout) return; // Ignore keyboard-only configuration changes.
        compactLayout = nextCompact;
        if (me != null && appRoot != null) {
            showApp();
        } else if (showingAuth && !isFinishing()) {
            showAuth(registering, "");
        }
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(autoSyncTick);
        super.onDestroy();
    }
}
