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
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
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
    private static final int BG = Color.rgb(7, 10, 22);
    private static final int PANEL = Color.rgb(15, 21, 42);
    private static final int PANEL_LIGHT = Color.rgb(26, 36, 64);
    private static final int GOLD = Color.rgb(234, 188, 95);
    private static final int VIOLET = Color.rgb(143, 116, 255);
    private static final int CYAN = Color.rgb(75, 218, 220);
    private static final int BLUE = Color.rgb(86, 154, 255);
    private static final int PINK = Color.rgb(242, 112, 174);
    private static final int GOLD_BRIGHT = Color.rgb(255, 224, 154);
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
    private GameAudioEngine gameAudio;
    private SharedPreferences gamePrefs;
    private boolean bgmEnabled = true;
    private boolean sfxEnabled = true;
    private boolean hapticsEnabled = true;
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
    private String activeChatChannel = "WORLD";
    private LinearLayout chatMessagesPanel;
    private ScrollView chatScroll;
    private TextView chatStatus;
    private TextView chatWorldButton;
    private TextView chatTradeButton;
    private EditText chatComposer;
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

    private final Runnable chatRefreshTick = new Runnable() {
        @Override public void run() {
            if ("chat".equals(activeTab) && !isFinishing()) {
                loadChatMessages(activeChatChannel, false);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new ApiClient(this);
        gamePrefs = getSharedPreferences("carduniverse_game_settings", MODE_PRIVATE);
        bgmEnabled = gamePrefs.getBoolean("bgm_enabled", true);
        sfxEnabled = gamePrefs.getBoolean("sfx_enabled", true);
        hapticsEnabled = gamePrefs.getBoolean("haptics_enabled", true);
        gameAudio = new GameAudioEngine();
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
                {"chat", "◉", "CHAT"},
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
        account.setClickable(true);
        account.setFocusable(true);
        account.setContentDescription("Open collector profile");
        account.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(55, 255, 255, 255)),
                gradient(Color.argb(20, 255, 255, 255), Color.argb(20, 255, 255, 255),
                        Color.TRANSPARENT, dp(8)), null));
        account.setOnClickListener(v -> showPlayerProfile());

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
        avatar.setClickable(true);
        avatar.setFocusable(true);
        avatar.setContentDescription("Open collector profile");
        avatar.setOnClickListener(v -> showPlayerProfile());
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

    private int screenAccent(String tab) {
        if ("collection".equals(tab) || "binders".equals(tab)) return CYAN;
        if ("evolution".equals(tab) || "summon".equals(tab)) return PINK;
        if ("progression".equals(tab) || "events".equals(tab) || "missions".equals(tab)) return GOLD_BRIGHT;
        if ("adventure".equals(tab) || "dungeons".equals(tab) || "raids".equals(tab) || "arena".equals(tab)) return Color.rgb(255, 126, 112);
        if ("trades".equals(tab) || "shop".equals(tab)) return CYAN;
        if ("guild".equals(tab)) return Color.rgb(106, 210, 153);
        if ("ranked".equals(tab) || "leaderboards".equals(tab)) return Color.rgb(255, 198, 92);
        return VIOLET;
    }

    private String screenSigil(String tab) {
        if ("collection".equals(tab)) return "▧";
        if ("binders".equals(tab)) return "▤";
        if ("evolution".equals(tab) || "summon".equals(tab)) return "✧";
        if ("progression".equals(tab) || "events".equals(tab)) return "✺";
        if ("adventure".equals(tab) || "dungeons".equals(tab) || "raids".equals(tab) || "arena".equals(tab)) return "⚔";
        if ("trades".equals(tab)) return "⇄";
        if ("shop".equals(tab)) return "◇";
        if ("guild".equals(tab)) return "♜";
        if ("ranked".equals(tab) || "leaderboards".equals(tab)) return "♛";
        return "✦";
    }

    private void addScreenHero(String tab) {
        int accent = screenAccent(tab);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(3), dp(2), dp(3), dp(10));
        TextView eyebrow = text("FIRSTLIGHT REALM   /   " + tab.toUpperCase(java.util.Locale.ROOT),
                7, accent, true);
        eyebrow.setLetterSpacing(0.17f);
        hero.addView(eyebrow);
        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(screenHeading(tab), 23, TEXT, true);
        heading.setLetterSpacing(-0.015f);
        titleLine.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        FrameLayout crest = new FrameLayout(this);
        GradientDrawable crestBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(Color.red(accent) / 2 + 18, Color.green(accent) / 2 + 18,
                        Color.blue(accent) / 2 + 24), Color.rgb(13, 19, 34)});
        crestBg.setShape(GradientDrawable.OVAL);
        crestBg.setStroke(dp(1), accent);
        crest.setBackground(crestBg);
        TextView emblem = text(screenSigil(tab), 23, accent, true);
        emblem.setGravity(Gravity.CENTER);
        crest.addView(emblem, new FrameLayout.LayoutParams(-1, -1));
        titleLine.addView(crest, new LinearLayout.LayoutParams(dp(43), dp(43)));
        hero.addView(titleLine);
        TextView description = text(screenDescription(tab), 9, MUTED, false);
        description.setMaxLines(2);
        description.setPadding(0, dp(3), 0, dp(9));
        hero.addView(description);
        View rule = new View(this);
        rule.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{accent, Color.argb(42, Color.red(accent), Color.green(accent),
                        Color.blue(accent)), Color.TRANSPARENT}));
        hero.addView(rule, new LinearLayout.LayoutParams(-1, dp(2)));
        content.addView(hero, rowParams());
        addGap(content, 7);
    }

    private void addModeSpotlight(String eyebrow, String title, String description, String emblem, int accent) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(14), dp(11), dp(14), dp(11));
        panel.setBackground(gradient(
                Color.rgb(Math.min(255, Color.red(accent) / 4 + 26),
                        Math.min(255, Color.green(accent) / 4 + 28),
                        Math.min(255, Color.blue(accent) / 4 + 48)),
                Color.rgb(10, 16, 34), accent, dp(15)));
        FrameLayout mark = new FrameLayout(this);
        mark.setBackground(gradient(Color.rgb(37, 43, 81), Color.rgb(12, 19, 38), accent, dp(12)));
        TextView icon = text(emblem, 31, accent, true);
        icon.setGravity(Gravity.CENTER);
        mark.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        panel.addView(mark, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(text(eyebrow, 7, accent, true));
        copy.addView(text(title, 14, TEXT, true));
        TextView detail = text(description, 8, MUTED, false);
        detail.setMaxLines(2);
        copy.addView(detail);
        panel.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(panel, rowParams());
        addGap(content, 9);
    }

    private void addAudioSetting(LinearLayout parent, String title, String description, int kind) {
        boolean enabled = kind == 0 ? bgmEnabled : kind == 1 ? sfxEnabled : hapticsEnabled;
        LinearLayout row = dataRow(title, description, "");
        TextView toggle = button(enabled ? "ON" : "OFF", enabled);
        toggle.setOnClickListener(v -> {
            boolean next = !(kind == 0 ? bgmEnabled : kind == 1 ? sfxEnabled : hapticsEnabled);
            if (kind == 0) {
                bgmEnabled = next;
                gamePrefs.edit().putBoolean("bgm_enabled", next).apply();
                if (gameAudio != null) {
                    if (next) gameAudio.startAmbient();
                    else gameAudio.stopAmbient();
                }
            } else if (kind == 1) {
                sfxEnabled = next;
                gamePrefs.edit().putBoolean("sfx_enabled", next).apply();
                if (next && gameAudio != null) gameAudio.playSuccess();
            } else {
                hapticsEnabled = next;
                gamePrefs.edit().putBoolean("haptics_enabled", next).apply();
            }
            toggle.setText(next ? "ON" : "OFF");
            toggle.setBackground(gradient(next ? Color.rgb(46, 112, 101) : Color.rgb(40, 48, 72),
                    next ? Color.rgb(20, 60, 57) : Color.rgb(16, 24, 43),
                    next ? CYAN : Color.rgb(83, 102, 143), dp(10)));
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(68), dp(34));
        p.leftMargin = dp(7);
        row.addView(toggle, p);
        parent.addView(row, rowParams());
    }


    private boolean usesRealmScene(String tab) {
        return "collection".equals(tab) || "binders".equals(tab) || "achievements".equals(tab)
                || "summon".equals(tab) || "adventure".equals(tab) || "dungeons".equals(tab)
                || "raids".equals(tab) || "arena".equals(tab) || "trades".equals(tab)
                || "chat".equals(tab) || "shop".equals(tab) || "guild".equals(tab) || "missions".equals(tab)
                || "progression".equals(tab);
    }

    private void addRealmScene(String mode, String title, String subtitle, int progress, int total) {
        RealmSceneView scene = new RealmSceneView(this, mode, title, subtitle, progress, total);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(226));
        params.bottomMargin = dp(9);
        content.addView(scene, params);
    }

    private void renderTab(String tab) {
        ui.removeCallbacks(chatRefreshTick);
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
        if (!usesRealmScene(tab)) addScreenHero(tab);
        renderLoading();
        String path = endpointFor(tab);
        if (path == null) {
            showNotice("This section is not available for this account.");
            return;
        }
        api.get(path, result -> ui.post(() -> {
            if (!tab.equals(activeTab) || content == null) return;
            content.removeAllViews();
            if (!usesRealmScene(tab)) addScreenHero(tab);
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
        JSONObject wallet = me.optJSONObject("wallet");
        JSONObject collection = me.optJSONObject("collection");
        if (wallet == null) wallet = new JSONObject();
        if (collection == null) collection = new JSONObject();

        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);
        welcome.setPadding(dp(3), dp(1), dp(3), dp(7));
        TextView season = text("SEASON 01  ·  FIRSTLIGHT AWAKENING", 7, GOLD, true);
        season.setLetterSpacing(0.17f);
        welcome.addView(season);
        TextView greeting = text("WELCOME BACK, " + me.optString("display_name", "COLLECTOR").toUpperCase(java.util.Locale.ROOT),
                14, TEXT, true);
        greeting.setMaxLines(1);
        welcome.addView(greeting);
        content.addView(welcome);

        if (isGodModeEnabled()) {
            LinearLayout god = new LinearLayout(this);
            god.setGravity(Gravity.CENTER_VERTICAL);
            god.setPadding(dp(11), dp(7), dp(11), dp(7));
            god.setBackground(gradient(Color.rgb(45, 35, 78), Color.rgb(17, 20, 40),
                    Color.rgb(143, 116, 255), dp(10)));
            god.addView(text("✦", 17, Color.rgb(207, 188, 255), true));
            TextView godLabel = text("MASTER ADMIN · GOD MODE ONLINE", 8,
                    Color.rgb(220, 205, 255), true);
            godLabel.setPadding(dp(8), 0, 0, 0);
            god.addView(godLabel);
            LinearLayout.LayoutParams godParams = rowParams();
            godParams.bottomMargin = dp(8);
            content.addView(god, godParams);
        }

        RealmSceneView lobby = new RealmSceneView(this, "home", "FIRSTLIGHT REALM",
                "Your legend begins here. Choose a path and enter the world.",
                me.optInt("collector_level", 1), Math.max(100, me.optInt("collector_level", 1)));
        lobby.setContentDescription("Animated fantasy realm lobby");
        LinearLayout.LayoutParams lobbyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(252));
        lobbyParams.bottomMargin = dp(8);
        content.addView(lobby, lobbyParams);

        LinearLayout primaryActions = new LinearLayout(this);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        TextView adventure = button("⚔  ENTER ADVENTURE", true);
        adventure.setTextSize(11);
        adventure.setOnClickListener(v -> renderTab("adventure"));
        primaryActions.addView(adventure, new LinearLayout.LayoutParams(0, dp(48), 1.35f));
        TextView battle = button("◈  BATTLE ARENA", false);
        battle.setTextSize(10);
        battle.setOnClickListener(v -> renderTab("arena"));
        LinearLayout.LayoutParams battleParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        battleParams.leftMargin = dp(7);
        primaryActions.addView(battle, battleParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionParams.bottomMargin = dp(12);
        content.addView(primaryActions, actionParams);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(lobbyStat("COLLECTOR LV.", formatNumber(me.optLong("collector_level", 1)), CYAN),
                new LinearLayout.LayoutParams(0, dp(53), 1f));
        stats.addView(lobbyStat("CARDS", formatNumber(collection.optLong("unique_cards")), GOLD_BRIGHT),
                new LinearLayout.LayoutParams(0, dp(53), 1f));
        stats.addView(lobbyStat("GOLD", formatNumber(wallet.optLong("coins")), Color.rgb(255, 207, 108)),
                new LinearLayout.LayoutParams(0, dp(53), 1f));
        stats.addView(lobbyStat("GEMS", formatNumber(wallet.optLong("gems")), VIOLET),
                new LinearLayout.LayoutParams(0, dp(53), 1f));
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-1, dp(53));
        statsParams.bottomMargin = dp(13);
        content.addView(stats, statsParams);

        LinearLayout dailyReward = new LinearLayout(this);
        dailyReward.setOrientation(LinearLayout.HORIZONTAL);
        dailyReward.setGravity(Gravity.CENTER_VERTICAL);
        dailyReward.setPadding(dp(13), dp(10), dp(13), dp(10));
        dailyReward.setBackground(gradient(Color.rgb(54, 43, 76), Color.rgb(15, 19, 37),
                Color.rgb(183, 143, 224), dp(12)));
        FrameLayout gift = new FrameLayout(this);
        gift.setBackground(gradient(Color.rgb(103, 74, 128), Color.rgb(35, 27, 63), VIOLET, dp(11)));
        TextView giftIcon = text("🎁", 24, TEXT, true);
        giftIcon.setGravity(Gravity.CENTER);
        gift.addView(giftIcon, new FrameLayout.LayoutParams(-1, -1));
        dailyReward.addView(gift, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout dailyCopy = new LinearLayout(this);
        dailyCopy.setOrientation(LinearLayout.VERTICAL);
        dailyCopy.setPadding(dp(11), 0, 0, 0);
        dailyCopy.addView(text("DAILY LOGIN REWARD", 8, GOLD_BRIGHT, true));
        dailyCopy.addView(text("Claim your free Starter Pack and 20 Gold.", 8, MUTED, false));
        dailyReward.addView(dailyCopy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView dailyClaim = button("CLAIM", true);
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
        dailyReward.addView(dailyClaim, new LinearLayout.LayoutParams(dp(88), dp(40)));
        LinearLayout.LayoutParams dailyParams = rowParams();
        dailyParams.bottomMargin = dp(14);
        content.addView(dailyReward, dailyParams);

        TextView portalsTitle = text("CHOOSE YOUR NEXT MODE", 8, GOLD, true);
        portalsTitle.setLetterSpacing(0.13f);
        content.addView(portalsTitle);
        addGap(content, 7);

        String[][] modes = {
                {"adventure", "⚔", "ADVENTURE", "Explore the Firstlight frontier", "FIGHT"},
                {"dungeons", "⌘", "DUNGEON", "Descend into the deep vault", "ENTER"},
                {"raids", "♜", "WORLD RAID", "Challenge the realm guardian", "ATTACK"},
                {"summon", "✧", "SUMMON", "Open a rare card seal", "SUMMON"},
                {"collection", "▧", "CARD ARCHIVE", "Browse your discovered cards", "OPEN"},
                {"binders", "▤", "BINDER VAULT", "Curate your favourite collection", "OPEN"}
        };
        HorizontalScrollView portalsScroll = new HorizontalScrollView(this);
        portalsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout portals = new LinearLayout(this);
        portals.setOrientation(LinearLayout.HORIZONTAL);
        portals.setPadding(dp(1), dp(2), dp(1), dp(8));
        portalsScroll.addView(portals);
        int[] accents = {GOLD_BRIGHT, VIOLET, RED, CYAN, BLUE, Color.rgb(116, 206, 192)};
        for (int i = 0; i < modes.length; i++) {
            final String key = modes[i][0];
            int accent = accents[i];
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(12), dp(10), dp(12), dp(10));
            tile.setBackground(gradient(Color.rgb(33, 42, 65), Color.rgb(11, 17, 31), accent, dp(12)));
            TextView icon = text(modes[i][1], 23, accent, true);
            tile.addView(icon);
            TextView title = text(modes[i][2], 9, TEXT, true);
            title.setPadding(0, dp(5), 0, dp(2));
            tile.addView(title);
            TextView desc = text(modes[i][3], 7, MUTED, false);
            desc.setMaxLines(2);
            tile.addView(desc);
            TextView enter = text("›  " + modes[i][4], 8, accent, true);
            enter.setPadding(0, dp(8), 0, 0);
            tile.addView(enter);
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setOnClickListener(v -> renderTab(key));
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(185), dp(126));
            tileParams.rightMargin = dp(8);
            portals.addView(tile, tileParams);
        }
        content.addView(portalsScroll, rowParams());

        LinearLayout extras = new LinearLayout(this);
        extras.setOrientation(LinearLayout.HORIZONTAL);
        extras.addView(featureCard("♛", "Ranked", "Season ladder and standings", () -> renderTab("ranked")),
                new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams guildParams = new LinearLayout.LayoutParams(0, -2, 1f);
        guildParams.leftMargin = dp(8);
        extras.addView(featureCard("♜", "Guild Hall", "Your realm companions", () -> renderTab("guild")), guildParams);
        content.addView(extras);
    }

    private LinearLayout lobbyStat(String label, String value, int accent) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(5), dp(5), dp(5), dp(5));
        chip.setBackground(gradient(Color.rgb(22, 31, 51), Color.rgb(10, 17, 30),
                Color.rgb(Color.red(accent) / 2, Color.green(accent) / 2, Color.blue(accent) / 2),
                dp(8)));
        TextView kicker = text(label, 6, accent, true);
        kicker.setGravity(Gravity.CENTER);
        chip.addView(kicker);
        TextView amount = text(value, 12, TEXT, true);
        amount.setGravity(Gravity.CENTER);
        chip.addView(amount);
        return chip;
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
            case "chat": renderChat(data); break;
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

    
    private FrameLayout createSummonSeal(int accent, String symbol, int sizeDp) {
        FrameLayout outer = new FrameLayout(this);
        GradientDrawable aura = new GradientDrawable();
        aura.setShape(GradientDrawable.OVAL);
        aura.setColor(Color.rgb(Color.red(accent) / 5 + 21,
                Color.green(accent) / 5 + 20, Color.blue(accent) / 5 + 31));
        aura.setStroke(dp(1), accent);
        outer.setBackground(aura);
        FrameLayout ring = new FrameLayout(this);
        GradientDrawable ringShape = new GradientDrawable();
        ringShape.setShape(GradientDrawable.OVAL);
        ringShape.setColor(Color.TRANSPARENT);
        ringShape.setStroke(dp(2), Color.argb(220, Color.red(accent),
                Color.green(accent), Color.blue(accent)));
        ring.setBackground(ringShape);
        outer.addView(ring, new FrameLayout.LayoutParams(dp(sizeDp - 20),
                dp(sizeDp - 20), Gravity.CENTER));
        FrameLayout inner = new FrameLayout(this);
        GradientDrawable innerBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(55, 48, 91), Color.rgb(12, 20, 39)});
        innerBg.setShape(GradientDrawable.OVAL);
        innerBg.setStroke(dp(1), Color.argb(150, 230, 220, 255));
        inner.setBackground(innerBg);
        outer.addView(inner, new FrameLayout.LayoutParams(dp(sizeDp - 42),
                dp(sizeDp - 42), Gravity.CENTER));
        TextView mark = text(symbol, Math.max(23, sizeDp / 3), accent, true);
        mark.setGravity(Gravity.CENTER);
        inner.addView(mark, new FrameLayout.LayoutParams(-1, -1));
        for (int i = 0; i < 4; i++) {
            TextView star = text("✦", 7, accent, true);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(10), dp(10));
            if (i == 0) p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            else if (i == 1) p.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            else if (i == 2) p.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
            else p.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
            outer.addView(star, p);
        }
        return outer;
    }

    private void renderPacks(JSONObject data) {
        JSONArray packs = data.optJSONArray("packs");
        if (packs == null) { showNotice("No summon packs are available."); return; }
        boolean unlimited = data.optBoolean("unlimited") || isGodModeEnabled();
        int totalQuantity = 0;
        for (int i = 0; i < packs.length(); i++) {
            JSONObject p = packs.optJSONObject(i);
            if (p != null) totalQuantity += Math.max(0, p.optInt("quantity"));
        }
        addRealmScene("summon", "THE ASTRAL GATE", "Choose a seal below to open a portal and reveal your next cards.", totalQuantity, Math.max(1, totalQuantity));
        content.addView(text("AVAILABLE SUMMON SEALS", 8, GOLD_BRIGHT, true));
        for (int i = 0; i < packs.length(); i++) {
            JSONObject pack = packs.optJSONObject(i);
            if (pack == null) continue;
            String slug = pack.optString("slug", "");
            String name = pack.optString("name", "Summon pack");
            int quantity = pack.optInt("quantity");
            String currency = pack.optString("currency", "COINS");
            long price = pack.optLong("price");
            int perPack = Math.max(1, pack.optInt("cards_per_pack", 1));
            int accent = i % 4 == 0 ? VIOLET : i % 4 == 1 ? CYAN
                    : i % 4 == 2 ? GOLD_BRIGHT : PINK;
            boolean canOpen = quantity > 0 || unlimited;

            LinearLayout card = sectionPanel();
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(12), dp(12), dp(12));
            card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(Color.red(accent) / 5 + 28, Color.green(accent) / 5 + 26,
                            Color.blue(accent) / 5 + 35), Color.rgb(11, 17, 31)}));
            FrameLayout seal = createSummonSeal(accent,
                    i % 4 == 0 ? "✧" : i % 4 == 1 ? "✦" : i % 4 == 2 ? "◇" : "☼", 112);
            card.addView(seal, new LinearLayout.LayoutParams(dp(112), dp(112)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(13), 0, dp(9), 0);
            info.addView(text("ASTRAL SEAL · " + perPack + " CARDS", 7, accent, true));
            TextView title = text(name, 16, TEXT, true);
            title.setMaxLines(2);
            info.addView(title);
            TextView description = text(pack.optString("description",
                    "A sealed selection from the Firstlight Realm."), 8, MUTED, false);
            description.setMaxLines(2);
            info.addView(description);
            info.addView(text(canOpen ? (unlimited ? "INFINITE SUMMON ACCESS" : "IN STOCK · " + quantity)
                    : "OUT OF STOCK", 8, GOLD_BRIGHT, true));
            if (pack.has("pity_count"))
                info.addView(text("MYTHIC PITY · " + pack.optInt("pity_count"), 7, PINK, true));
            info.addView(text("COST · " + formatNumber(price) + " " + currency, 7, MUTED, true));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.VERTICAL);
            actions.setGravity(Gravity.CENTER);
            TextView open = button(canOpen ? "OPEN SEAL" : "LOCKED", canOpen);
            open.setEnabled(canOpen);
            open.setOnClickListener(v -> confirmAction("Open " + name + "?",
                    "One seal will reveal " + perPack + " card" + (perPack == 1 ? "" : "s")
                            + " and add them to your collection.", () -> {
                        JSONObject request = new JSONObject();
                        try { request.put("requestId", newActionId()); } catch (Exception ignored) {}
                        open.setEnabled(false);
                        open.setText("OPENING...");
                        api.post("/api/packs/" + slug + "/open", request, result -> ui.post(() -> {
                            open.setEnabled(true);
                            open.setText("OPEN SEAL");
                            if (!result.ok()) {
                                if (sfxEnabled && gameAudio != null) gameAudio.playError();
                                showResultDialog("Summon failed", result.error);
                                return;
                            }
                            if (sfxEnabled && gameAudio != null) gameAudio.playSuccess();
                            showPackRevealDialog(result.data.optJSONArray("cards"));
                        }));
                    }));
            actions.addView(open, new LinearLayout.LayoutParams(dp(112), dp(39)));
            if (!canOpen && "COINS".equalsIgnoreCase(currency) && price > 0) {
                TextView buy = button("BUY · " + formatNumber(price) + "G", false);
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(112), dp(35));
                bp.topMargin = dp(7);
                actions.addView(buy, bp);
                buy.setOnClickListener(v -> confirmAction("Acquire " + name + "?",
                        "Spend " + formatNumber(price) + " Gold for one summon seal.", () ->
                        api.post("/api/packs/" + slug + "/buy", new JSONObject(), result -> ui.post(() -> {
                            if (!result.ok()) showResultDialog("Purchase failed", result.error);
                            else {
                                showResultDialog("SEAL ACQUIRED", result.data.optString("message", "Pack added to inventory."));
                                refreshAccount(() -> renderTab("summon"));
                            }
                        }))));
            }
            card.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(actions);
            content.addView(card, rowParams());
        }
        if (packs.length() == 0) showNotice("No summon seals are available right now.");
    }
    private void renderArena(JSONObject data) {
        addRealmScene("arena", isAdminAccount() ? "THE GRAND ARENA · OBSERVER" : "THE GRAND ARENA",
                isAdminAccount() ? "Spectator access · public competitive standings only."
                        : "Prepare your formation and enter the battle arena.", 0, 0);

if (isAdminAccount() || data.optBoolean("rankedHidden")) {
            JSONArray ladder = data.optJSONArray("ladder");
            if (ladder == null) ladder = new JSONArray();
            LinearLayout note = sectionPanel();
            note.addView(text("PVP MATCHMAKING DISABLED", 9, PINK, true));
            note.addView(text("No queue, match controls or player moves are available to this account.", 9, MUTED, false));
            content.addView(note, rowParams());
            content.addView(text("PUBLIC RANKED LEADERS · " + ladder.length(), 8, GOLD_BRIGHT, true));
            for (int i = 0; i < ladder.length(); i++) {
                JSONObject player = ladder.optJSONObject(i);
                if (player != null) content.addView(dataRow("#" + (i + 1) + " · "
                        + player.optString("display_name", "Collector"),
                        formatNumber(player.optLong("rating")) + " RATING · " + player.optInt("wins")
                                + "W / " + player.optInt("losses") + "L",
                        player.optString("tier", "")), rowParams());
            }
            return;
        }

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
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        stage.setBackground(gradient(Color.rgb(19, 26, 45), Color.rgb(5, 8, 17), GOLD, dp(15)));

        RealmSceneView arena = new RealmSceneView(this, "arena",
                active.optString("mode", "LIVE DUEL") + " · TURN " + active.optInt("current_turn"),
                "The clash is underway. Choose your next move carefully.",
                active.optInt("current_turn"), 10);
        stage.addView(arena, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout duel = new LinearLayout(this);
        duel.setOrientation(LinearLayout.HORIZONTAL);
        duel.setGravity(Gravity.CENTER_VERTICAL);
        duel.setPadding(dp(5), dp(5), dp(5), dp(5));
        duel.setBackground(gradient(Color.argb(220, 8, 13, 25),
                Color.argb(232, 8, 13, 25), Color.rgb(91, 101, 135), dp(12)));

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

        FrameLayout.LayoutParams duelParams = new FrameLayout.LayoutParams(-1, dp(118), Gravity.BOTTOM);
        duelParams.setMargins(dp(10), 0, dp(10), dp(9));
        stage.addView(duel, duelParams);

        TextView versus = text("VS", 9, GOLD_BRIGHT, true);
        versus.setGravity(Gravity.CENTER);
        versus.setPadding(dp(5), dp(5), dp(5), dp(5));
        versus.setElevation(dp(9));
        versus.setBackground(gradient(Color.rgb(52, 38, 31), Color.rgb(20, 19, 27),
                GOLD_BRIGHT, dp(20)));
        FrameLayout.LayoutParams versusParams = new FrameLayout.LayoutParams(dp(38), dp(34),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        versusParams.bottomMargin = dp(51);
        stage.addView(versus, versusParams);
        stage.setMinimumHeight(dp(246));

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
        int totalStages = 0, clearedStages = 0;
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            if (chapter == null) continue;
            JSONArray stages = chapter.optJSONArray("stages");
            if (stages == null) continue;
            totalStages += stages.length();
            for (int j = 0; j < stages.length(); j++) {
                JSONObject stage = stages.optJSONObject(j);
                if (stage != null && stage.optBoolean("cleared")) clearedStages++;
            }
        }
        addRealmScene("adventure", "FIRSTLIGHT FRONTIER",
                "Explore the realm, clear encounters and unlock the next region.",
                clearedStages, Math.max(1, totalStages));
        LinearLayout objective = sectionPanel();
        objective.setOrientation(LinearLayout.HORIZONTAL);
        objective.setGravity(Gravity.CENTER_VERTICAL);
        objective.addView(text(formatNumber(data.optLong("playerPower")) + " COMBAT POWER",
                14, GOLD_BRIGHT, true), new LinearLayout.LayoutParams(0, -2, 1f));
        objective.addView(text(clearedStages + " / " + totalStages + " CLEARED", 8, CYAN, true));
        ProgressBar campaign = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        campaign.setMax(Math.max(1, totalStages));
        campaign.setProgress(clearedStages);
        campaign.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        objective.addView(campaign, new LinearLayout.LayoutParams(-1, dp(7)));
        content.addView(objective, rowParams());

        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            if (chapter == null) continue;
            JSONArray stages = chapter.optJSONArray("stages");
            if (stages == null) stages = new JSONArray();
            int chapterCleared = 0;
            for (int j = 0; j < stages.length(); j++) {
                JSONObject stage = stages.optJSONObject(j);
                if (stage != null && stage.optBoolean("cleared")) chapterCleared++;
            }
            LinearLayout chapterPanel = sectionPanel();
            LinearLayout chapterHead = new LinearLayout(this);
            chapterHead.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout crest = new FrameLayout(this);
            crest.setBackground(gradient(Color.rgb(84,55,69),Color.rgb(24,25,47),Color.rgb(255,147,126),dp(11)));
            TextView numeral = text(String.format(java.util.Locale.ROOT,"%02d",i+1),17,TEXT,true);
            numeral.setGravity(Gravity.CENTER);
            crest.addView(numeral,new FrameLayout.LayoutParams(-1,-1));
            chapterHead.addView(crest,new LinearLayout.LayoutParams(dp(45),dp(45)));
            LinearLayout chapterCopy = new LinearLayout(this);
            chapterCopy.setOrientation(LinearLayout.VERTICAL);
            chapterCopy.setPadding(dp(10),0,0,0);
            chapterCopy.addView(text("CHAPTER "+(i+1),7,Color.rgb(255,166,148),true));
            chapterCopy.addView(text(chapter.optString("title","Unknown chapter"),14,TEXT,true));
            TextView desc = text(chapter.optString("description",""),8,MUTED,false);
            desc.setMaxLines(2); chapterCopy.addView(desc);
            chapterHead.addView(chapterCopy,new LinearLayout.LayoutParams(0,-2,1f));
            chapterHead.addView(text(chapterCleared+" / "+stages.length(),9,GOLD_BRIGHT,true));
            chapterPanel.addView(chapterHead);

            HorizontalScrollView routeScroll = new HorizontalScrollView(this);
            routeScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout route = new LinearLayout(this);
            route.setOrientation(LinearLayout.HORIZONTAL);
            routeScroll.addView(route);
            for (int j = 0; j < stages.length(); j++) {
                JSONObject stage = stages.optJSONObject(j);
                if (stage == null) continue;
                final String slug = stage.optString("slug","");
                final String stageTitle = stage.optString("title","Stage");
                final String enemy = stage.optString("enemy_name","Realm encounter");
                final boolean unlocked = stage.optBoolean("unlocked");
                final boolean cleared = stage.optBoolean("cleared");
                int accent = cleared ? CYAN : unlocked ? Color.rgb(255,166,148) : Color.rgb(92,105,139);
                LinearLayout node = new LinearLayout(this);
                node.setOrientation(LinearLayout.VERTICAL);
                node.setPadding(dp(8),dp(8),dp(8),dp(8));
                node.setBackground(gradient(cleared?Color.rgb(26,68,65):unlocked?Color.rgb(67,42,57):Color.rgb(27,34,51),
                        Color.rgb(10,16,30),accent,dp(11)));
                FrameLayout stone = new FrameLayout(this);
                stone.setBackground(gradient(cleared?Color.rgb(39,112,101):unlocked?Color.rgb(117,64,76):Color.rgb(56,65,87),
                        Color.rgb(15,20,37),accent,dp(30)));
                TextView mark=text(cleared?"✓":unlocked?String.valueOf(j+1):"⌑",18,Color.WHITE,true);
                mark.setGravity(Gravity.CENTER);
                stone.addView(mark,new FrameLayout.LayoutParams(-1,-1));
                node.addView(stone,new LinearLayout.LayoutParams(dp(48),dp(48)));
                TextView label=text(stageTitle,9,TEXT,true);label.setMaxLines(2);label.setPadding(0,dp(6),0,dp(2));node.addView(label);
                TextView enemyLabel=text(enemy,7,MUTED,false);enemyLabel.setMaxLines(1);node.addView(enemyLabel);
                node.addView(text(cleared?"CLEARED":unlocked?"ENCOUNTER READY":"LOCKED",6,accent,true));
                if(unlocked){
                    TextView fight=button(cleared?"REPLAY":"BATTLE",true);
                    fight.setOnClickListener(v->confirmAction("Enter "+stageTitle+"?",
                            "Challenge "+enemy+". The server calculates the battle result and rewards.",()->{
                        JSONObject request=new JSONObject();
                        try{request.put("requestId",newActionId());}catch(Exception ignored){}
                        fight.setEnabled(false);fight.setText("FIGHTING...");
                        api.post("/api/adventure/"+slug+"/battle",request,result->ui.post(()->{
                            fight.setEnabled(true);fight.setText(cleared?"REPLAY":"BATTLE");
                            if(!result.ok()){showResultDialog("Encounter unavailable",result.error);return;}
                            JSONObject run=result.data.optJSONObject("run");
                            String resultName=run==null?"COMPLETE":run.optString("result","COMPLETE");
                            String resultText=result.data.optString("message","The encounter has ended.");
                            if(run!=null)resultText+="\n\nEnemy: "+run.optString("enemy_name",enemy)
                                    +"\nYour power: "+formatNumber(run.optLong("player_power"))
                                    +"\nEnemy power: "+formatNumber(run.optLong("enemy_power"))
                                    +"\nXP: +"+formatNumber(run.optLong("reward_xp"))
                                    +"\nGold: +"+formatNumber(run.optLong("reward_coins"));
                            if(sfxEnabled&&gameAudio!=null){if("VICTORY".equalsIgnoreCase(resultName))gameAudio.playSuccess();else gameAudio.playError();}
                            showResultDialog("VICTORY".equalsIgnoreCase(resultName)?"✦ VICTORY":"ENCOUNTER RESULT",resultText);
                            refreshAccount(()->renderTab("adventure"));
                        }));
                    }));
                    node.addView(fight,new LinearLayout.LayoutParams(-1,dp(34)));
                }else{
                    node.addView(text("Clear the previous stage to unlock.",7,MUTED,false));
                }
                LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(dp(154),-2);np.rightMargin=dp(9);
                route.addView(node,np);
            }
            chapterPanel.addView(routeScroll,rowParams());
            content.addView(chapterPanel,rowParams());
        }
    }
    private void renderMissions(JSONObject data) {
        JSONArray missions = data.optJSONArray("missions");
        JSONArray challenges = data.optJSONArray("daily_challenges");
        addRealmScene("adventure", "QUEST BOARD", "Daily contracts and long-term objectives for your collector.", 0, 0);
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
        if (wallet == null) wallet = new JSONObject();
        addRealmScene("market", "FIRSTLIGHT MARKET", "A realm bazaar for cards, supplies and collector cosmetics.", items.length(), Math.max(1, items.length()));
        LinearLayout balances = new LinearLayout(this);
        balances.setOrientation(LinearLayout.HORIZONTAL);
        balances.addView(statCard("GOLD", formatNumber(wallet.optLong("coins")), "Available"), weightParams(1));
        balances.addView(statCard("GEMS", formatNumber(wallet.optLong("gems")), "Premium"), weightParams(1));
        balances.addView(statCard("SHARDS", formatNumber(wallet.optLong("shards")), "Upgrade"), weightParams(1));
        content.addView(balances);
        addGap(content, 10);
        content.addView(text("MARKET CATALOGUE · " + items.length() + " LISTINGS", 8, GOLD_BRIGHT, true));
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            final JSONObject selectedItem = item;
            String itemId = item.optString("item_id", "");
            String title = item.optString("title", item.optString("name", "Realm item"));
            String description = item.optString("description", item.optString("item_type", "REALM ITEM"));
            String currency = item.optString("currency", "COINS").toUpperCase(java.util.Locale.ROOT);
            long price = item.optLong("price");
            long stock = item.isNull("stock_remaining") ? -1 : item.optLong("stock_remaining");
            boolean owned = item.optInt("cosmetic_owned") == 1;
            boolean canBuy = !owned && stock != 0 && price > 0 && !itemId.isEmpty();
            int accent = owned ? GREEN : "GEMS".equals(currency) ? VIOLET
                    : "SHARDS".equals(currency) ? CYAN : GOLD;
            LinearLayout card = sectionPanel();
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout icon = new FrameLayout(this);
            icon.setBackground(gradient(Color.rgb(Color.red(accent) / 3 + 22,
                    Color.green(accent) / 3 + 22, Color.blue(accent) / 3 + 30),
                    Color.rgb(11, 17, 34), accent, dp(13)));
            String type = item.optString("item_type", "ITEM").toUpperCase(java.util.Locale.ROOT);
            TextView glyph = text(type.contains("PACK") ? "✧" : type.contains("CARD") ? "▧"
                    : type.contains("COSMETIC") ? "✦" : "◇", 31, accent, true);
            glyph.setGravity(Gravity.CENTER);
            icon.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
            card.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(82)));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(dp(11), 0, dp(7), 0);
            copy.addView(text(type + " · " + currency, 7, accent, true));
            copy.addView(text(title, 12, TEXT, true));
            TextView details = text(description, 8, MUTED, false);
            details.setMaxLines(2);
            copy.addView(details);
            copy.addView(text(formatNumber(price) + " " + currency, 10, GOLD_BRIGHT, true));
            copy.addView(text(stock < 0 ? "OPEN STOCK" : stock == 0 ? "SOLD OUT" : stock + " UNITS LEFT",
                    7, stock == 0 ? RED : MUTED, true));
            card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
            if (owned) {
                TextView status = text("OWNED", 8, GREEN, true);
                card.addView(status);
            } else {
                TextView buy = button(canBuy ? "PURCHASE" : stock == 0 ? "SOLD OUT" : "UNAVAILABLE", true);
                buy.setEnabled(canBuy);
                buy.setOnClickListener(v -> confirmAction("Purchase " + title + "?",
                        "Cost: " + formatNumber(price) + " " + currency
                                + ". Stock and purchase limits are verified securely by the server.", () -> {
                    JSONObject request = new JSONObject();
                    try {
                        request.put("itemId", itemId);
                        request.put("requestId", newActionId());
                        request.put("quantity", 1);
                    } catch (Exception ignored) {}
                    buy.setEnabled(false);
                    buy.setText("PROCESSING...");
                    api.post("/api/shop/purchase", request, result -> ui.post(() -> {
                        buy.setEnabled(true);
                        buy.setText("PURCHASE");
                        if (!result.ok()) {
                            if (sfxEnabled && gameAudio != null) gameAudio.playError();
                            showResultDialog("Purchase failed", result.error);
                        } else {
                            if (sfxEnabled && gameAudio != null) gameAudio.playSuccess();
                            showResultDialog("PURCHASE COMPLETE", result.data.optString("message", "Your item has been delivered."));
                            refreshAccount(() -> renderTab("shop"));
                        }
                    }));
                }));
                card.addView(buy, new LinearLayout.LayoutParams(dp(104), dp(38)));
            }
            content.addView(card, rowParams());
        }
        if (items.length() == 0) showNotice("The market has no active listings right now.");
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
        addRealmScene("guild", guild == null ? "GUILD FRONTIER" : guild.optString("name", "GUILD HALL"), guild == null ? "Find your banner or found a new guild." : "Your community headquarters · build a shared legacy.", guild == null ? 0 : guild.optInt("member_count"), guild == null ? 1 : Math.max(1, guild.optInt("member_limit")));
        if (guild == null) {
            LinearLayout create = sectionPanel();
            create.setGravity(Gravity.CENTER);
            FrameLayout crest = new FrameLayout(this);
            crest.setBackground(gradient(Color.rgb(44, 97, 78), Color.rgb(13, 30, 38), CYAN, dp(16)));
            TextView sigil = text("♜", 43, Color.rgb(170, 255, 220), true);
            sigil.setGravity(Gravity.CENTER);
            crest.addView(sigil, new FrameLayout.LayoutParams(-1, -1));
            create.addView(crest, new LinearLayout.LayoutParams(dp(86), dp(86)));
            addGap(create, 6);
            create.addView(text("NO GUILD BANNER YET", 9, CYAN, true));
            create.addView(text("Create a guild or join a recruiting banner to enter the community.", 8, MUTED, false));
            TextView createButton = button("CREATE YOUR GUILD", true);
            createButton.setOnClickListener(v -> showCreateGuildDialog());
            create.addView(createButton, new LinearLayout.LayoutParams(dp(185), dp(40)));
            content.addView(create, rowParams());
        } else {
            LinearLayout home = sectionPanel();
            home.setBackground(gradient(Color.rgb(26, 76, 68), Color.rgb(9, 24, 34), Color.rgb(103, 222, 170), dp(18)));
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout crest = new FrameLayout(this);
            crest.setBackground(gradient(Color.rgb(73, 131, 105), Color.rgb(15, 43, 43),
                    Color.rgb(125, 244, 187), dp(16)));
            TextView emblem = text(guild.optString("emblem", "✦"), 42, Color.rgb(210, 255, 230), true);
            emblem.setGravity(Gravity.CENTER);
            crest.addView(emblem, new FrameLayout.LayoutParams(-1, -1));
            header.addView(crest, new LinearLayout.LayoutParams(dp(86), dp(86)));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(dp(12), 0, 0, 0);
            copy.addView(text("[" + guild.optString("tag", "GUILD") + "] · GUILD BANNER",
                    8, CYAN, true));
            copy.addView(text(guild.optString("name", "Guild"), 20, TEXT, true));
            TextView description = text(guild.optString("description", "Guild headquarters"),
                    8, MUTED, false);
            description.setMaxLines(2);
            copy.addView(description);
            header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
            home.addView(header);
            LinearLayout stats = new LinearLayout(this);
            stats.setOrientation(LinearLayout.HORIZONTAL);
            stats.addView(statCard("GUILD LEVEL", String.valueOf(guild.optInt("guild_level")), "Progress"), weightParams(1));
            stats.addView(statCard("MEMBERS", guild.optInt("member_count") + "/" + guild.optInt("member_limit"), "Roster"), weightParams(1));
            stats.addView(statCard("TREASURY", formatNumber(guild.optLong("bank_coins")), "Gold"), weightParams(1));
            home.addView(stats);
            TextView contribute = button("CONTRIBUTE GOLD", true);
            contribute.setOnClickListener(v -> chooseGuildContribution());
            home.addView(contribute, new LinearLayout.LayoutParams(dp(180), dp(40)));
            content.addView(home, rowParams());

            JSONArray members = guild.optJSONArray("members");
            if (members != null) {
                content.addView(text("GUILD ROSTER · " + members.length() + " MEMBERS", 8, GOLD_BRIGHT, true));
                for (int i = 0; i < members.length(); i++) {
                    JSONObject member = members.optJSONObject(i);
                    if (member == null) continue;
                    LinearLayout row = dataRow(member.optString("display_name", "Collector"),
                            member.optString("role", "MEMBER").toUpperCase(java.util.Locale.ROOT)
                                    + " · LEVEL " + member.optInt("collector_level")
                                    + " · CONTRIBUTION " + formatNumber(member.optLong("contribution")),
                            member.optString("role", "MEMBER"));
                    content.addView(row, rowParams());
                }
            }
            JSONArray activity = guild.optJSONArray("activity");
            if (activity != null && activity.length() > 0) {
                addGap(content, 8);
                content.addView(text("GUILD ACTIVITY FEED", 8, GOLD_BRIGHT, true));
                for (int i = 0; i < activity.length(); i++) {
                    JSONObject event = activity.optJSONObject(i);
                    if (event == null) continue;
                    LinearLayout item = dataRow(humanize(event.optString("event_type", "GUILD EVENT")),
                            event.optString("display_name", "Guild member")
                                    + " · " + event.optString("created_at", ""), "ACTIVITY");
                    content.addView(item, rowParams());
                }
            }
        }
        JSONArray directory = data.optJSONArray("directory");
        if (directory != null && directory.length() > 0) {
            addGap(content, 10);
            content.addView(text(guild == null ? "RECRUITING GUILDS" : "OTHER RECRUITING GUILDS",
                    8, GOLD_BRIGHT, true));
            for (int i = 0; i < directory.length(); i++) {
                JSONObject entry = directory.optJSONObject(i);
                if (entry == null) continue;
                LinearLayout card = sectionPanel();
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                FrameLayout crest = new FrameLayout(this);
                crest.setBackground(gradient(Color.rgb(41, 74, 66), Color.rgb(12, 28, 36),
                        Color.rgb(104, 215, 164), dp(12)));
                TextView symbol = text("[" + entry.optString("tag", "??") + "]", 9, CYAN, true);
                symbol.setGravity(Gravity.CENTER);
                crest.addView(symbol, new FrameLayout.LayoutParams(-1, -1));
                card.addView(crest, new LinearLayout.LayoutParams(dp(60), dp(58)));
                LinearLayout copy = new LinearLayout(this);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.setPadding(dp(10), 0, dp(7), 0);
                copy.addView(text(entry.optString("name", "Guild"), 12, TEXT, true));
                copy.addView(text("LEADER " + entry.optString("leader_name", "Unknown")
                        + " · " + entry.optInt("member_count") + "/" + entry.optInt("member_limit") + " MEMBERS",
                        7, MUTED, false));
                copy.addView(text("LEVEL " + entry.optInt("guild_level"), 7, GOLD_BRIGHT, true));
                card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView join = button("JOIN", false);
                join.setOnClickListener(v -> confirmAction("Join [" + entry.optString("tag") + "]?",
                        "You can only belong to one guild at a time.", () -> joinGuild(entry.optString("tag"))));
                card.addView(join, new LinearLayout.LayoutParams(dp(72), dp(36)));
                content.addView(card, rowParams());
            }
        } else if (guild == null) {
            showNotice("There are no recruiting guilds right now. You can create the first one.");
        }
    }
    private void renderSettings(JSONObject data) {
addModeSpotlight("GAME EXPERIENCE", "Make the realm feel yours",
                "Tune the soundtrack, interface feedback and your player account preferences.",
                "⚙", CYAN);
        LinearLayout experience = sectionPanel();
        experience.addView(text("AUDIO & FEEDBACK", 8, CYAN, true));
        experience.addView(text("These preferences are saved on this device.", 8, MUTED, false));
        addAudioSetting(experience, "Ambient soundtrack", "Original Firstlight ambience while the game is open.", 0);
        addAudioSetting(experience, "Sound effects", "Subtle button and reward tones.", 1);
        addAudioSetting(experience, "Haptic feedback", "Light tactile response on game controls.", 2);
        content.addView(experience, rowParams());
        addGap(content, 9);

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

    private void renderCollection(JSONObject data) {JSONArray cards = data.optJSONArray("cards");
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

        addRealmScene("archive", "THE CARD ARCHIVE", "Every card you discover becomes part of your legend.", ownedTypes, cards.length());
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
            int columns = widthDp >= 1400 ? 6 : widthDp >= 1050 ? 5 : widthDp >= 680 ? 4 : 3;
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
        String code = card.optString("card_code", "CARD");
        int copies = card.optInt("owned_count");
        int accent = cardRarityColor(rarity);

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(3), dp(3), dp(3), dp(6));
        GradientDrawable frame = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(37, 48, 72), Color.rgb(8, 13, 25)});
        frame.setCornerRadius(dp(11));
        frame.setStroke(dp(1), accent);
        tile.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(65, 255, 255, 255)),
                frame, null));
        tile.setElevation(dp(4));

        FrameLayout artFrame = new FrameLayout(this);
        GradientDrawable artBack = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(21, 31, 51), Color.rgb(4, 8, 17)});
        artBack.setCornerRadius(dp(8));
        artFrame.setBackground(artBack);
        artFrame.setClipToOutline(true);
        CardArtView illustration = new CardArtView(this);
        illustration.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        illustration.loadArtwork(cardArtworkPath(card));
        artFrame.addView(illustration, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(35, 4, 8, 16), Color.argb(235, 4, 8, 16)}));
        artFrame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        View topGlow = new View(this);
        topGlow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(65, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.TRANSPARENT}));
        FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(-1, dp(52), Gravity.TOP);
        artFrame.addView(topGlow, glowParams);

        TextView rarityTag = text(rarity, 7, Color.WHITE, true);
        rarityTag.setGravity(Gravity.CENTER);
        rarityTag.setPadding(dp(7), dp(5), dp(7), dp(5));
        rarityTag.setBackground(gradient(Color.argb(220, 12, 18, 32),
                Color.argb(220, 12, 18, 32), accent, dp(6)));
        FrameLayout.LayoutParams rarityParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.LEFT);
        rarityParams.setMargins(dp(7), dp(7), 0, 0);
        artFrame.addView(rarityTag, rarityParams);

        TextView copyTag = text(copies > 0 ? "×" + copies : "LOCKED", 7, Color.WHITE, true);
        copyTag.setGravity(Gravity.CENTER);
        copyTag.setPadding(dp(7), dp(5), dp(7), dp(5));
        copyTag.setBackground(gradient(Color.argb(220, 12, 18, 32),
                Color.argb(220, 12, 18, 32),
                copies > 0 ? GREEN : Color.rgb(99, 119, 156), dp(6)));
        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.RIGHT);
        copyParams.setMargins(0, dp(7), dp(7), 0);
        artFrame.addView(copyTag, copyParams);

        TextView artworkName = text(name, 11, Color.WHITE, true);
        artworkName.setMaxLines(2);
        artworkName.setShadowLayer(dp(5), 0, dp(1), Color.BLACK);
        artworkName.setPadding(dp(9), 0, dp(9), dp(9));
        artFrame.addView(artworkName, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int columns = widthDp >= 1400 ? 6 : widthDp >= 1050 ? 5 : widthDp >= 680 ? 4 : 3;
        int artHeight = Math.max(222, Math.min(318,
                Math.round((widthDp / (float) columns) * 1.34f)));
        tile.addView(artFrame, new LinearLayout.LayoutParams(-1, dp(artHeight)));

        TextView meta = text(code + "   ·   " + element, 7, MUTED, true);
        meta.setPadding(dp(3), dp(7), dp(3), 0);
        meta.setGravity(Gravity.CENTER);
        tile.addView(meta);
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

    private void showCardDetails(JSONObject card) {
        String rarity = card.optString("rarity", "COMMON").toUpperCase(java.util.Locale.ROOT);
        String element = card.optString("element", "AETHER").toUpperCase(java.util.Locale.ROOT);
        int accent = cardRarityColor(rarity);
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(13), dp(13), dp(13), dp(13));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(25, 36, 57), Color.rgb(8, 13, 24)});
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), accent);
        shell.setBackground(bg);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("CARD ARCHIVE  /  DETAILS", 7, accent, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = button("CLOSE", false);
        close.setOnClickListener(v -> dialog.dismiss());
        top.addView(close, new LinearLayout.LayoutParams(dp(78), dp(34)));
        shell.addView(top);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(0, dp(10), 0, dp(10));
        FrameLayout artFrame = new FrameLayout(this);
        artFrame.setBackground(gradient(Color.rgb(22, 35, 52), Color.rgb(5, 10, 18), accent, dp(11)));
        CardArtView art = new CardArtView(this);
        art.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        art.loadArtwork(cardArtworkPath(card));
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(-1, -1);
        ap.setMargins(dp(3), dp(3), dp(3), dp(3));
        artFrame.addView(art, ap);
        body.addView(artFrame, new LinearLayout.LayoutParams(dp(218), dp(294)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(16), 0, dp(6), 0);
        info.addView(text(card.optString("card_code", "CARD"), 8, accent, true));
        TextView name = text(card.optString("name", "Card Details"), 22, TEXT, true);
        name.setMaxLines(2);
        info.addView(name);
        info.addView(text(rarity + "  ·  " + element, 8, GOLD_BRIGHT, true));
        TextView description = text(card.optString("description",
                "A collectible from the Firstlight Realm."), 10, MUTED, false);
        description.setLineSpacing(dp(3), 1.03f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = dp(10);
        info.addView(description, descParams);
        View rule = new View(this);
        rule.setBackgroundColor(Color.rgb(51, 67, 88));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(1));
        rp.topMargin = dp(11);
        rp.bottomMargin = dp(9);
        info.addView(rule, rp);
        info.addView(text("OWNED COPIES", 7, MUTED, true));
        info.addView(text(String.valueOf(card.optInt("owned_count")), 19, GREEN, true));
        body.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        shell.addView(body);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(dp(760), getResources().getDisplayMetrics().widthPixels - dp(40)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setWindowAnimations(android.R.style.Animation_Dialog);
        }
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
        JSONArray dungeons = data.optJSONArray("dungeons");
        JSONArray claimable = data.optJSONArray("claimableRuns");
        JSONArray history = data.optJSONArray("history");
        JSONObject run = data.optJSONObject("activeRun");
        int used = data.optInt("dailyRuns");
        int limit = data.optInt("dailyLimit", 3);
        int remaining = Math.max(0, limit - used);

        addRealmScene("dungeon", run == null ? "THE DEEP VAULT" : run.optString("dungeon_name", "ACTIVE EXPEDITION"), "Explore the chambers. Choose your route. Bring the treasure home.", run == null ? used : run.optInt("floor_reached"), run == null ? Math.max(1, limit) : 6);
        LinearLayout daily = sectionPanel();
        daily.setBackground(gradient(Color.rgb(43, 39, 89), Color.rgb(11, 17, 35), VIOLET, dp(16)));
        daily.addView(text("EXPEDITION ENERGY", 8, CYAN, true));
        daily.addView(text(remaining + " / " + limit + " ENTRIES REMAINING TODAY", 16, TEXT, true));
        ProgressBar energy = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        energy.setMax(Math.max(1, limit));
        energy.setProgress(used);
        energy.setProgressTintList(android.content.res.ColorStateList.valueOf(VIOLET));
        daily.addView(energy, new LinearLayout.LayoutParams(-1, dp(8)));
        content.addView(daily, rowParams());

        if (run != null) {
            JSONObject reward = run.optJSONObject("reward");
            if (reward == null) reward = new JSONObject();
            boolean done = "COMPLETED".equalsIgnoreCase(run.optString("result"))
                    || "DEFEAT".equalsIgnoreCase(run.optString("result"));
            int maxFloors = Math.max(1, reward.optInt("max_floors", 6));
            int floor = Math.max(0, run.optInt("floor_reached"));
            LinearLayout active = sectionPanel();
            active.setBackground(gradient(Color.rgb(63, 42, 92), Color.rgb(11, 17, 37), PINK, dp(17)));
            active.addView(text(done ? "EXPEDITION RESULT" : "ACTIVE EXPEDITION", 8,
                    done ? CYAN : PINK, true));
            active.addView(text(run.optString("dungeon_name", "Dungeon"), 19, TEXT, true));
            active.addView(text("FLOOR " + floor + " / " + maxFloors + " · "
                    + (done ? run.optString("result") : "CHOOSE YOUR NEXT CHAMBER"), 8, GOLD_BRIGHT, true));
            ProgressBar floorBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            floorBar.setMax(maxFloors);
            floorBar.setProgress(Math.min(maxFloors, floor));
            floorBar.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
            active.addView(floorBar, new LinearLayout.LayoutParams(-1, dp(8)));
            active.addView(text(formatNumber(reward.optLong("coins")) + " GOLD · "
                    + formatNumber(reward.optLong("xp")) + " EXP · "
                    + formatNumber(reward.optLong("shards")) + " SHARDS",
                    8, CYAN, true));
            if (done) {
                TextView claim = button("CLAIM EXPEDITION TREASURE", true);
                claim.setOnClickListener(v -> api.post("/api/dungeons/runs/" + run.optString("run_id") + "/claim",
                        new JSONObject(), result -> ui.post(() -> {
                            if (!result.ok()) showResultDialog("Claim failed", result.error);
                            else {
                                if (sfxEnabled && gameAudio != null) gameAudio.playSuccess();
                                showResultDialog("TREASURE CLAIMED", result.data.optString("message", "Rewards added."));
                                refreshAccount(() -> renderTab("dungeons"));
                            }
                        })));
                active.addView(claim, rowParams());
            } else {
                content.addView(text("CHOOSE YOUR NEXT ROUTE", 8, GOLD_BRIGHT, true));
                String[][] routes = {
                        {"battle","⚔","BATTLE","Risk the encounter"},
                        {"treasure","◇","TREASURE","Take the vault loot"},
                        {"sanctuary","✧","SANCTUARY","Recover your strength"}
                };
                LinearLayout routeChoices = new LinearLayout(this);
                routeChoices.setOrientation(LinearLayout.HORIZONTAL);
                for(String[] route:routes){
                    LinearLayout choice=sectionPanel();choice.setGravity(Gravity.CENTER);
                    choice.addView(text(route[1],25,route[0].equals("battle")?RED:route[0].equals("treasure")?GOLD_BRIGHT:CYAN,true));
                    choice.addView(text(route[2],8,TEXT,true));
                    TextView detail=text(route[3],7,MUTED,false);detail.setGravity(Gravity.CENTER);detail.setMaxLines(2);choice.addView(detail);
                    choice.setOnClickListener(v->confirmAction("Choose "+route[2]+"?",
                            "Your route and expedition progress are saved after this step.",()->api.post(
                            "/api/dungeons/runs/"+run.optString("run_id")+"/step",
                            payload("choice",route[0],"requestId",newActionId()),result->ui.post(()->{
                                if(!result.ok())showResultDialog("Dungeon step failed",result.error);
                                else{JSONObject step=result.data.optJSONObject("step");if(sfxEnabled&&gameAudio!=null)gameAudio.playSuccess();
                                    showResultDialog("CHAMBER RESOLVED",step==null?result.data.optString("message","Route updated."):step.optString("title","Route updated."));
                                    refreshAccount(()->renderTab("dungeons"));}
                            }))));;
                    LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(112),1f);
                    rp.setMargins(dp(3),dp(3),dp(3),dp(3));routeChoices.addView(choice,rp);
                }
                active.addView(routeChoices);
            }
            content.addView(active, rowParams());
        }

        if (dungeons != null) {
            content.addView(text("DUNGEON GATES · SELECT AN EXPEDITION", 8, GOLD_BRIGHT, true));
            HorizontalScrollView gateScroll = new HorizontalScrollView(this);
            gateScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout gateShelf = new LinearLayout(this);
            gateShelf.setOrientation(LinearLayout.HORIZONTAL);
            gateScroll.addView(gateShelf);
            for (int i = 0; i < dungeons.length(); i++) {
                JSONObject dungeon = dungeons.optJSONObject(i);
                if (dungeon == null) continue;
                JSONObject reward = dungeon.optJSONObject("reward");
                if (reward == null) reward = new JSONObject();
                LinearLayout gate = sectionPanel();
                gate.setOrientation(LinearLayout.VERTICAL);
                gate.setGravity(Gravity.CENTER_VERTICAL);
                FrameLayout seal = new FrameLayout(this);
                int accent = i % 2 == 0 ? VIOLET : PINK;
                seal.setBackground(gradient(Color.rgb(65, 48, 112), Color.rgb(13, 19, 39), accent, dp(12)));
                TextView sigil = text("⌘", 35, accent, true);
                sigil.setGravity(Gravity.CENTER);
                seal.addView(sigil, new FrameLayout.LayoutParams(-1, -1));
                gate.addView(seal, new LinearLayout.LayoutParams(-1, dp(76)));
                LinearLayout details = new LinearLayout(this);
                details.setOrientation(LinearLayout.VERTICAL);
                details.setPadding(dp(10), 0, dp(7), 0);
                details.addView(text("DIFFICULTY " + dungeon.optInt("difficulty") + " · "
                        + reward.optInt("max_floors", 6) + " FLOORS", 7, accent, true));
                details.addView(text(dungeon.optString("name", "Dungeon"), 13, TEXT, true));
                TextView desc = text(dungeon.optString("description", ""), 8, MUTED, false);
                desc.setMaxLines(2);
                details.addView(desc);
                details.addView(text(formatNumber(reward.optLong("coins")) + " GOLD · "
                        + formatNumber(reward.optLong("xp")) + " EXP", 7, GOLD_BRIGHT, true));
                gate.addView(details, new LinearLayout.LayoutParams(-1, -2));
                TextView enter = button(run != null ? "ACTIVE" : remaining <= 0 ? "LIMIT" : "ENTER",
                        run == null && remaining > 0);
                enter.setEnabled(run == null && remaining > 0);
                enter.setOnClickListener(v -> confirmAction("Enter " + dungeon.optString("name") + "?",
                        "This uses one of your daily expedition entries.", () ->
                        api.post("/api/dungeons/" + dungeon.optString("slug") + "/start",
                                new JSONObject(), result -> ui.post(() -> {
                                    if (!result.ok()) showResultDialog("Cannot enter dungeon", result.error);
                                    else refreshAccount(() -> renderTab("dungeons"));
                                }))));
                gate.addView(enter, new LinearLayout.LayoutParams(-1, dp(36)));
                LinearLayout.LayoutParams gateParams = new LinearLayout.LayoutParams(dp(220), -2);
                gateParams.setMargins(dp(4), dp(4), dp(7), dp(7));
                gateShelf.addView(gate, gateParams);
            }
            content.addView(gateScroll,rowParams());
        }

        if (claimable != null && claimable.length() > 0) {
            content.addView(text("UNCLAIMED TREASURE", 8, GOLD_BRIGHT, true));
            for (int i = 0; i < claimable.length(); i++) {
                JSONObject item = claimable.optJSONObject(i);
                if (item == null) continue;
                TextView claim = button("CLAIM · " + item.optString("dungeon_name", "EXPEDITION"), false);
                claim.setOnClickListener(v -> api.post("/api/dungeons/runs/" + item.optString("run_id")
                        + "/claim", new JSONObject(), result -> ui.post(() -> {
                            if (!result.ok()) showResultDialog("Claim failed", result.error);
                            else refreshAccount(() -> renderTab("dungeons"));
                        })));
                content.addView(claim, rowParams());
            }
        }
        if (history != null && history.length() > 0) {
            content.addView(text("RECENT EXPEDITIONS", 8, GOLD_BRIGHT, true));
            for (int i = 0; i < history.length(); i++) {
                JSONObject h = history.optJSONObject(i);
                if (h != null) content.addView(dataRow(h.optString("dungeon_name", "Expedition"),
                        "FLOOR " + h.optInt("floor_reached") + " · " + h.optString("result"), ""), rowParams());
            }
        }
    }

    private void renderRaids(JSONObject data) {
        JSONArray bosses = data.optJSONArray("bosses");
        if (bosses == null || bosses.length() == 0) {
            showNotice("No world raid is scheduled. Return when the next guardian awakens.");
            return;
        }
        JSONObject featuredBoss = bosses.optJSONObject(0);
        if (featuredBoss != null) addRealmScene("raid", featuredBoss.optString("name", "WORLD BOSS"),
                "The realm shares one health pool. Every strike matters.", featuredBoss.optInt("progress_percent"), 100);
        for (int i = 0; i < bosses.length(); i++) {
            JSONObject boss = bosses.optJSONObject(i);
            if (boss == null) continue;
            JSONObject reward = boss.optJSONObject("reward");
            if (reward == null) reward = new JSONObject();
            boolean live = boss.optBoolean("is_event_open");
            boolean defeated = boss.optBoolean("defeated");
            int accent = defeated ? CYAN : live ? PINK : GOLD_BRIGHT;
            LinearLayout panel = sectionPanel();
            panel.setBackground(gradient(Color.rgb(70, 35, 72), Color.rgb(11, 17, 36), accent, dp(18)));
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout portrait = new FrameLayout(this);
            portrait.setBackground(gradient(Color.rgb(105, 52, 89), Color.rgb(24, 21, 49), accent, dp(15)));
            TextView crest = text("♜", 43, accent, true);
            crest.setGravity(Gravity.CENTER);
            portrait.addView(crest, new FrameLayout.LayoutParams(-1, -1));
            header.addView(portrait, new LinearLayout.LayoutParams(dp(86), dp(92)));
            LinearLayout title = new LinearLayout(this);
            title.setOrientation(LinearLayout.VERTICAL);
            title.setPadding(dp(12), 0, 0, 0);
            title.addView(text("WORLD RAID · " + (defeated ? "DEFEATED" : live ? "LIVE ENCOUNTER" : "SCHEDULED"),
                    7, accent, true));
            title.addView(text(boss.optString("name", "World Guardian"), 18, TEXT, true));
            TextView description = text(boss.optString("description", ""), 8, MUTED, false);
            description.setMaxLines(3);
            title.addView(description);
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            panel.addView(header);
            addGap(panel, 6);
            LinearLayout hpInfo = new LinearLayout(this);
            hpInfo.setGravity(Gravity.CENTER_VERTICAL);
            hpInfo.addView(text("GUARDIAN HP", 8, accent, true),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            hpInfo.addView(text(formatNumber(boss.optLong("remaining_hp")) + " / "
                    + formatNumber(boss.optLong("hp")), 9, TEXT, true));
            panel.addView(hpInfo);
            ProgressBar hp = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            hp.setMax(100);
            hp.setProgress(Math.max(0, Math.min(100, boss.optInt("progress_percent"))));
            hp.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            panel.addView(hp, new LinearLayout.LayoutParams(-1, dp(9)));
            LinearLayout contributions = new LinearLayout(this);
            contributions.setOrientation(LinearLayout.HORIZONTAL);
            contributions.addView(statCard("TOTAL DAMAGE", formatNumber(boss.optLong("total_damage")), "Realm contribution"), weightParams(1));
            contributions.addView(statCard("PARTICIPANTS", formatNumber(boss.optLong("participants")), "Collectors"), weightParams(1));
            contributions.addView(statCard("YOUR DAMAGE", formatNumber(boss.optLong("my_today_damage")), "Today"), weightParams(1));
            panel.addView(contributions);
            panel.addView(text("REWARD POOL · " + formatNumber(reward.optLong("coins")) + " GOLD · "
                    + formatNumber(reward.optLong("xp")) + " EXP · "
                    + formatNumber(reward.optLong("shards")) + " SHARDS",
                    8, GOLD_BRIGHT, true));
            if (live && !defeated) {
                TextView attack = button(boss.optLong("my_today_damage") > 0
                        ? "DAILY STRIKE USED" : "⚔ STRIKE THE GUARDIAN",
                        boss.optLong("my_today_damage") <= 0);
                attack.setEnabled(boss.optLong("my_today_damage") <= 0);
                attack.setOnClickListener(v -> confirmAction("Strike " + boss.optString("name") + "?",
                        "Your formation power determines the damage. One strike is allowed per day.", () ->
                        api.post("/api/raids/" + boss.optString("slug") + "/attack",
                                payload("requestId", newActionId()), result -> ui.post(() -> {
                                    if (!result.ok()) showResultDialog("Raid strike failed", result.error);
                                    else {
                                        JSONObject attempt = result.data.optJSONObject("attempt");
                                        if (sfxEnabled && gameAudio != null) gameAudio.playSuccess();
                                        showResultDialog("WORLD RAID STRIKE", "Damage dealt: "
                                                + (attempt == null ? "—" : formatNumber(attempt.optLong("damage"))));
                                        refreshAccount(() -> renderTab("raids"));
                                    }
                                }))));
                panel.addView(attack, rowParams());
            }
            if (boss.optBoolean("my_claimable")) {
                TextView claim = button("CLAIM RAID REWARDS", false);
                claim.setOnClickListener(v -> api.post("/api/raids/" + boss.optString("slug") + "/claim",
                        new JSONObject(), result -> ui.post(() -> {
                            if (!result.ok()) showResultDialog("Raid claim failed", result.error);
                            else {
                                if (sfxEnabled && gameAudio != null) gameAudio.playSuccess();
                                showResultDialog("REWARDS CLAIMED", result.data.optString("message", "Rewards added."));
                                refreshAccount(() -> renderTab("raids"));
                            }
                        })));
                panel.addView(claim, rowParams());
            }
            content.addView(panel, rowParams());
        }
    }

    private void renderTrades(JSONObject data) {
        JSONArray inventoryRaw = data.optJSONArray("inventory");
        JSONArray tradesRaw = data.optJSONArray("trades");
        final JSONArray inventory = inventoryRaw == null ? new JSONArray() : inventoryRaw;
        final JSONArray trades = tradesRaw == null ? new JSONArray() : tradesRaw;
        String userId = me == null ? "" : me.optString("user_id");
        int incomingCount = 0;
        int outgoingCount = 0;
        for (int i = 0; i < trades.length(); i++) {
            JSONObject trade = trades.optJSONObject(i);
            if (trade == null || !"PENDING".equalsIgnoreCase(trade.optString("status"))) continue;
            if (userId.equals(trade.optString("target_user_id"))) incomingCount++;
            if (userId.equals(trade.optString("creator_user_id"))) outgoingCount++;
        }
        addRealmScene("trade", "COLLECTOR EXCHANGE", "Two collectors. Two offers. One fair exchange.", incomingCount + outgoingCount, Math.max(1, trades.length()));
        LinearLayout dashboard = sectionPanel();
        dashboard.setBackground(gradient(Color.rgb(26, 59, 77), Color.rgb(10, 17, 34), CYAN, dp(16)));
        dashboard.addView(text("SECURE PLAYER-TO-PLAYER EXCHANGE", 8, CYAN, true));
        dashboard.addView(text("Your trade desk", 18, TEXT, true));
        dashboard.addView(text("Reserved cards stay locked until an offer is accepted, declined or cancelled.",
                8, MUTED, false));
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard("INCOMING", String.valueOf(incomingCount), "Awaiting you"), weightParams(1));
        stats.addView(statCard("OUTGOING", String.valueOf(outgoingCount), "Awaiting reply"), weightParams(1));
        stats.addView(statCard("TRADABLE", String.valueOf(inventory.length()), "Available cards"), weightParams(1));
        dashboard.addView(stats);
        TextView create = button("＋ CREATE TRADE OFFER", true);
        create.setOnClickListener(v -> showTradeCreateDialog(inventory));
        dashboard.addView(create, new LinearLayout.LayoutParams(dp(205), dp(40)));
        content.addView(dashboard, rowParams());

        content.addView(text("ACTIVE TRADE CONTRACTS", 8, GOLD_BRIGHT, true));
        int visibleTrades = 0;
        for (int i = 0; i < trades.length(); i++) {
            JSONObject trade = trades.optJSONObject(i);
            if (trade == null) continue;
            boolean incoming = userId.equals(trade.optString("target_user_id"));
            boolean outgoing = userId.equals(trade.optString("creator_user_id"));
            if (!incoming && !outgoing) continue;
            visibleTrades++;
            boolean pending = "PENDING".equalsIgnoreCase(trade.optString("status"));
            String counterparty = incoming ? trade.optString("creator_name", "Collector")
                    : trade.optString("target_name", "Collector");
            LinearLayout card = sectionPanel();
            card.setBackground(gradient(incoming ? Color.rgb(25, 63, 74) : Color.rgb(40, 43, 77),
                    Color.rgb(10, 17, 34), incoming ? CYAN : VIOLET, dp(15)));
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(text(incoming ? "INCOMING OFFER" : "OUTGOING OFFER",
                    7, incoming ? CYAN : VIOLET, true), new LinearLayout.LayoutParams(0, -2, 1f));
            TextView status = text(trade.optString("status", "PENDING").toUpperCase(java.util.Locale.ROOT),
                    7, pending ? GOLD_BRIGHT : MUTED, true);
            status.setPadding(dp(8), dp(5), dp(8), dp(5));
            status.setBackground(gradient(Color.rgb(34, 40, 67), Color.rgb(13, 19, 36),
                    pending ? GOLD : Color.rgb(91, 107, 142), dp(8)));
            top.addView(status);
            card.addView(top);
            card.addView(text(counterparty, 14, TEXT, true));
            JSONObject publicListing = trade.optJSONObject("publicListing");
            if (publicListing != null) {
                card.addView(text("PUBLIC TRADE BOARD · " + publicListing.optString("name", "LISTED CARD"),
                        8, CYAN, true));
                String wanted = publicListing.optString("wanted_text", "");
                if (!wanted.isEmpty()) card.addView(text("OWNER WANTS · " + wanted, 8, GOLD_BRIGHT, true));
            }
            if (!trade.optString("message").isEmpty())
                card.addView(text("“" + trade.optString("message") + "”", 8, MUTED, false));
            JSONArray offered = trade.optJSONArray("items");
            if (offered != null && offered.length() > 0) {
                card.addView(text("OFFERED CARDS · " + offered.length(), 7, GOLD_BRIGHT, true));
                for (int j = 0; j < offered.length(); j++) {
                    JSONObject item = offered.optJSONObject(j);
                    if (item == null) continue;
                    String itemName = item.optString("name", "Card");
                    String detail = item.optString("rarity", "CARD") + " · "
                            + item.optString("element", "UNKNOWN") + " · " + item.optString("card_code", "");
                    card.addView(dataRow(itemName, detail, "OFFER"), rowParams());
                }
            }
            if (pending && incoming) {
                LinearLayout actions = new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                JSONObject publicListing = trade.optJSONObject("publicListing");
                TextView accept = button(publicListing == null ? "ACCEPT DEAL" : "ACCEPT LISTED TRADE", true);
                accept.setOnClickListener(v -> {
                    if (publicListing == null) {
                        showTradeAcceptDialog(trade.optString("trade_id"), inventory);
                    } else {
                        confirmAction("Accept public listing trade?",
                                "You will receive " + publicListing.optString("name", "the listed card")
                                        + " and send the cards offered by " + counterparty + ".",
                                () -> api.post("/api/trades/" + trade.optString("trade_id") + "/accept",
                                        new JSONObject(), result -> ui.post(() -> {
                                            if (!result.ok()) showResultDialog("Trade failed", result.error);
                                            else {
                                                showResultDialog("PUBLIC TRADE COMPLETE",
                                                        result.data.optString("message", "Cards exchanged."));
                                                refreshAccount(() -> renderTab("trades"));
                                            }
                                        })));
                    }
                });
                TextView decline = button("DECLINE", false);
                decline.setOnClickListener(v -> confirmAction("Decline trade?",
                        "The offered cards will be released to their owner.", () ->
                        api.post("/api/trades/" + trade.optString("trade_id") + "/decline",
                                new JSONObject(), result -> ui.post(() -> {
                                    if (!result.ok()) showResultDialog("Trade error", result.error);
                                    else renderTab("trades");
                                }))));
                actions.addView(accept, new LinearLayout.LayoutParams(0, dp(38), 1f));
                LinearLayout.LayoutParams declineParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
                declineParams.leftMargin = dp(7);
                actions.addView(decline, declineParams);
                card.addView(actions);
            } else if (pending && outgoing) {
                TextView cancel = button("CANCEL OFFER", false);
                cancel.setOnClickListener(v -> confirmAction("Cancel trade offer?",
                        "Your reserved cards will become available again.", () ->
                        api.post("/api/trades/" + trade.optString("trade_id") + "/cancel",
                                new JSONObject(), result -> ui.post(() -> {
                                    if (!result.ok()) showResultDialog("Trade error", result.error);
                                    else renderTab("trades");
                                }))));
                card.addView(cancel, new LinearLayout.LayoutParams(dp(145), dp(36)));
            }
            content.addView(card, rowParams());
        }
        if (visibleTrades == 0) showNotice("No offers are waiting. Create your first contract to start exchanging cards.");

        addGap(content, 8);
        content.addView(text("AVAILABLE FOR TRADE · " + inventory.length(), 8, GOLD_BRIGHT, true));
        if (inventory.length() == 0) {
            showNotice("No cards are currently available. Bound cards and cards reserved in open offers cannot be traded.");
        } else {
            int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                    / getResources().getDisplayMetrics().density);
            int columns = widthDp >= 1350 ? 6 : widthDp >= 1000 ? 5 : widthDp >= 700 ? 4 : 3;
            LinearLayout grid = null;
            int visible = 0;
            for (int i = 0; i < inventory.length(); i++) {
                JSONObject card = inventory.optJSONObject(i);
                if (card == null) continue;
                if (visible % columns == 0) {
                    grid = new LinearLayout(this);
                    grid.setOrientation(LinearLayout.HORIZONTAL);
                    content.addView(grid, new LinearLayout.LayoutParams(-1, -2));
                }
                LinearLayout tile = createCardTile(card);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
                p.setMargins(dp(3), dp(3), dp(3), dp(6));
                grid.addView(tile, p);
                visible++;
            }
        }
        loadPublicTradeBoard(inventory);
    }

        private void renderChat(JSONObject data) {
        addRealmScene("guild", "THE COLLECTOR LOUNGE",
                "Meet collectors, talk builds and arrange trades without leaving the game.",
                0, 0);

        LinearLayout intro = sectionPanel();
        intro.setBackground(gradient(Color.rgb(31, 52, 76), Color.rgb(9, 17, 31), CYAN, dp(13)));
        intro.addView(text("CARDUNIVERSE SERVER CHAT", 8, CYAN, true));
        intro.addView(text("Talk to the realm. Find a trade partner. Keep playing.", 9, TEXT, true));
        intro.addView(text("Messages are shared with signed-in players and limited to 240 characters.",
                7, MUTED, false));
        content.addView(intro, rowParams());
        addGap(content, 7);

        LinearLayout channels = new LinearLayout(this);
        channels.setOrientation(LinearLayout.HORIZONTAL);
        chatWorldButton = button("◉  WORLD", "WORLD".equals(activeChatChannel));
        chatTradeButton = button("⇄  TRADE", "TRADE".equals(activeChatChannel));
        chatWorldButton.setOnClickListener(v -> switchChatChannel("WORLD"));
        chatTradeButton.setOnClickListener(v -> switchChatChannel("TRADE"));
        channels.addView(chatWorldButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams tradeParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        tradeParams.leftMargin = dp(7);
        channels.addView(chatTradeButton, tradeParams);
        content.addView(channels);

        chatStatus = text("CONNECTING TO " + activeChatChannel + " CHANNEL…", 7, MUTED, true);
        chatStatus.setPadding(dp(3), dp(8), dp(3), dp(5));
        content.addView(chatStatus);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(false);
        chatScroll.setClipToPadding(false);
        chatMessagesPanel = new LinearLayout(this);
        chatMessagesPanel.setOrientation(LinearLayout.VERTICAL);
        chatMessagesPanel.setPadding(dp(4), dp(5), dp(4), dp(8));
        chatScroll.addView(chatMessagesPanel);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(252));
        scrollParams.bottomMargin = dp(8);
        content.addView(chatScroll, scrollParams);
        chatMessagesPanel.removeAllViews();

        LinearLayout composer = sectionPanel();
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(8), dp(7), dp(8), dp(7));
        chatComposer = editField("Write a message… (240 characters)", false);
        chatComposer.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(240)
        });
        LinearLayout.LayoutParams composerParams = new LinearLayout.LayoutParams(0, dp(43), 1f);
        composer.addView(chatComposer, composerParams);
        TextView send = button("SEND", true);
        send.setOnClickListener(v -> {
            String message = chatComposer.getText().toString().trim();
            if (message.isEmpty()) {
                chatComposer.setError("Write a message first.");
                return;
            }
            JSONObject body = new JSONObject();
            try {
                body.put("channel", activeChatChannel);
                body.put("message", message);
            } catch (Exception ignored) {}
            send.setEnabled(false);
            api.post("/api/chat", body, result -> ui.post(() -> {
                send.setEnabled(true);
                if (!result.ok()) {
                    showResultDialog("Message not sent", result.error);
                    return;
                }
                chatComposer.setText("");
                hideKeyboard();
                loadChatMessages(activeChatChannel, true);
            }));
        });
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(82), dp(43));
        sendParams.leftMargin = dp(7);
        composer.addView(send, sendParams);
        content.addView(composer, rowParams());

        renderChatMessages(data);
        ui.removeCallbacks(chatRefreshTick);
        ui.postDelayed(chatRefreshTick, 5000L);
    }

    private void switchChatChannel(String channel) {
        if (!"WORLD".equals(channel) && !"TRADE".equals(channel)) return;
        activeChatChannel = channel;
        styleChatChannels();
        loadChatMessages(channel, true);
    }

    private void styleChatChannels() {
        if (chatWorldButton != null)
            chatWorldButton.setBackground(gradient("WORLD".equals(activeChatChannel)
                    ? Color.rgb(45, 73, 91) : Color.rgb(17, 25, 43),
                    Color.rgb(9, 17, 31), "WORLD".equals(activeChatChannel) ? CYAN : Color.rgb(54, 68, 91), dp(9)));
        if (chatTradeButton != null)
            chatTradeButton.setBackground(gradient("TRADE".equals(activeChatChannel)
                    ? Color.rgb(64, 48, 91) : Color.rgb(17, 25, 43),
                    Color.rgb(9, 17, 31), "TRADE".equals(activeChatChannel) ? VIOLET : Color.rgb(54, 68, 91), dp(9)));
    }

    private void loadChatMessages(String channel, boolean scrollToBottom) {
        if (!"chat".equals(activeTab) || api == null) return;
        api.get("/api/chat?channel=" + channel, result -> ui.post(() -> {
            if (!"chat".equals(activeTab) || !channel.equals(activeChatChannel)
                    || chatMessagesPanel == null) return;
            if (result.ok()) {
                renderChatMessages(result.data);
                if (scrollToBottom && chatScroll != null)
                    chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
            } else if (chatStatus != null) {
                chatStatus.setText("CHAT CONNECTION INTERRUPTED · " + result.error);
                chatStatus.setTextColor(RED);
            }
            ui.removeCallbacks(chatRefreshTick);
            ui.postDelayed(chatRefreshTick, 5000L);
        }));
    }

    private void renderChatMessages(JSONObject data) {
        if (chatMessagesPanel == null || data == null) return;
        JSONArray messages = data.optJSONArray("messages");
        if (messages == null) messages = new JSONArray();
        chatMessagesPanel.removeAllViews();
        if (chatStatus != null) {
            chatStatus.setText(activeChatChannel + " CHANNEL  ·  " + messages.length()
                    + " RECENT MESSAGES  ·  REFRESHES AUTOMATICALLY");
            chatStatus.setTextColor(MUTED);
        }
        if (messages.length() == 0) {
            TextView empty = text("No messages yet. Start the conversation with the first hello.",
                    9, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(28), dp(16), dp(28));
            chatMessagesPanel.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        String ownId = me == null ? "" : me.optString("user_id", "");
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            boolean own = ownId.equals(message.optString("user_id"));
            LinearLayout messageBlock = new LinearLayout(this);
            messageBlock.setOrientation(LinearLayout.VERTICAL);
            messageBlock.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
            LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, -2);
            blockParams.bottomMargin = dp(7);
            chatMessagesPanel.addView(messageBlock, blockParams);

            String playerName = message.optString("display_name", "Collector");
            String level = "LV." + formatNumber(message.optLong("collector_level", 1));
            TextView author = text((own ? "YOU" : playerName) + "  ·  " + level,
                    7, own ? CYAN : GOLD_BRIGHT, true);
            author.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
            author.setPadding(dp(6), dp(1), dp(6), dp(3));
            messageBlock.addView(author);

            LinearLayout bubble = new LinearLayout(this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(dp(11), dp(8), dp(11), dp(8));
            bubble.setBackground(gradient(own ? Color.rgb(30, 63, 76) : Color.rgb(27, 34, 55),
                    Color.rgb(11, 18, 32), own ? CYAN : Color.rgb(67, 83, 112), dp(11)));
            TextView body = text(message.optString("message", ""), 9, TEXT, false);
            body.setLineSpacing(dp(2), 1.02f);
            body.setMaxWidth(dp(430));
            bubble.addView(body);
            messageBlock.addView(bubble, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            String created = message.optString("created_at", "");
            if (created.length() > 16) created = created.substring(0, 16).replace("T", " ");
            TextView time = text(created, 6, MUTED, false);
            time.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
            time.setPadding(dp(5), dp(2), dp(5), 0);
            messageBlock.addView(time);
        }
    }

    private void loadPublicTradeBoard(JSONArray fallbackInventory) {
        TextView loading = text("LOADING THE PUBLIC TRADE BOARD…", 8, MUTED, true);
        loading.setPadding(dp(3), dp(15), dp(3), dp(15));
        content.addView(loading);
        api.get("/api/trade-board", result -> ui.post(() -> {
            if (!"trades".equals(activeTab) || content == null) return;
            content.removeView(loading);
            if (!result.ok()) {
                TextView error = text("PUBLIC TRADE BOARD UNAVAILABLE · " + result.error, 8, RED, false);
                content.addView(error, rowParams());
                return;
            }
            renderPublicTradeBoard(result.data, fallbackInventory);
        }));
    }

    private void renderPublicTradeBoard(JSONObject data, JSONArray fallbackInventory) {
        JSONArray listings = data.optJSONArray("listings");
        JSONArray myListings = data.optJSONArray("myListings");
        JSONArray inventory = data.optJSONArray("inventory");
        if (listings == null) listings = new JSONArray();
        if (myListings == null) myListings = new JSONArray();
        if (inventory == null) inventory = fallbackInventory == null ? new JSONArray() : fallbackInventory;

        addGap(content, 12);
        LinearLayout market = sectionPanel();
        market.setBackground(gradient(Color.rgb(48, 41, 76), Color.rgb(11, 17, 34), VIOLET, dp(15)));
        LinearLayout marketHead = new LinearLayout(this);
        marketHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout marketCopy = new LinearLayout(this);
        marketCopy.setOrientation(LinearLayout.VERTICAL);
        marketCopy.addView(text("PUBLIC TRADE BOARD", 8, VIOLET, true));
        marketCopy.addView(text("Browse cards listed by every collector.", 9, TEXT, true));
        marketHead.addView(marketCopy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView create = button("＋ LIST A CARD", true);
        create.setOnClickListener(v -> showTradeListingDialog(inventory));
        marketHead.addView(create);
        market.addView(marketHead);
        market.addView(text("Each listing reserves the exact tradable card instance until it is removed, expires or changes hands.",
                7, MUTED, false));
        content.addView(market, rowParams());

        content.addView(text("YOUR LISTINGS  ·  " + myListings.length(), 8, GOLD_BRIGHT, true));
        if (myListings.length() == 0) {
            TextView none = text("You have no active listings. Put a tradable card on the public board.",
                    8, MUTED, false);
            none.setPadding(dp(4), dp(7), dp(4), dp(7));
            content.addView(none);
        } else {
            for (int i = 0; i < myListings.length(); i++) {
                JSONObject listing = myListings.optJSONObject(i);
                if (listing == null) continue;
                LinearLayout row = dataRow(listing.optString("name", "Listed card"),
                        listing.optString("rarity", "CARD") + " · " + listing.optString("wanted_text", "Open to offers"),
                        listing.optString("status", "OPEN"));
                if ("OPEN".equalsIgnoreCase(listing.optString("status"))) {
                    TextView remove = button("REMOVE", false);
                    remove.setOnClickListener(v -> confirmAction("Remove public listing?",
                            "The card will be returned to your available inventory.",
                            () -> api.post("/api/trade-board/" + listing.optString("listing_id") + "/cancel",
                                    new JSONObject(), result -> ui.post(() -> {
                                        if (!result.ok()) showResultDialog("Listing not removed", result.error);
                                        else renderTab("trades");
                                    }))));
                    row.addView(remove, new LinearLayout.LayoutParams(dp(90), dp(34)));
                } else {
                    TextView reserved = text("CHECK TRADE DESK", 7, CYAN, true);
                    reserved.setGravity(Gravity.CENTER);
                    row.addView(reserved, new LinearLayout.LayoutParams(dp(110), dp(34)));
                }
                content.addView(row, rowParams());
            }
        }

        content.addView(text("COMMUNITY LISTINGS  ·  " + listings.length(), 8, GOLD_BRIGHT, true));
        if (listings.length() == 0) {
            LinearLayout empty = sectionPanel();
            empty.setGravity(Gravity.CENTER);
            empty.addView(text("THE MARKET IS QUIET", 10, TEXT, true));
            empty.addView(text("Be the first collector to list a card for trade.", 8, MUTED, false));
            TextView list = button("LIST YOUR FIRST CARD", true);
            list.setOnClickListener(v -> showTradeListingDialog(inventory));
            empty.addView(list, new LinearLayout.LayoutParams(dp(190), dp(38)));
            content.addView(empty, rowParams());
            return;
        }

        for (int i = 0; i < listings.length(); i++) {
            JSONObject listing = listings.optJSONObject(i);
            if (listing == null) continue;
            if (me != null && me.optString("user_id").equals(listing.optString("listing_owner_id"))) continue;
            LinearLayout card = sectionPanel();
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(9), dp(9), dp(10), dp(9));
            int accent = cardRarityColor(listing.optString("rarity", "COMMON"));
            FrameLayout art = new FrameLayout(this);
            art.setBackground(gradient(Color.rgb(25, 34, 54), Color.rgb(7, 11, 22), accent, dp(8)));
            art.setClipToOutline(true);
            CardArtView illustration = new CardArtView(this);
            illustration.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            illustration.loadArtwork(cardArtworkPath(listing));
            art.addView(illustration, new FrameLayout.LayoutParams(-1, -1));
            View shade = new View(this);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.TRANSPARENT, Color.argb(190, 3, 7, 15)}));
            art.addView(shade, new FrameLayout.LayoutParams(-1, -1));
            TextView rarity = text(listing.optString("rarity", "CARD"), 6, Color.WHITE, true);
            rarity.setPadding(dp(5), dp(4), dp(5), dp(4));
            rarity.setBackground(gradient(Color.argb(210, 9, 14, 28), Color.argb(210, 9, 14, 28),
                    accent, dp(5)));
            FrameLayout.LayoutParams rarityParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
            rarityParams.setMargins(dp(5), dp(5), 0, 0);
            art.addView(rarity, rarityParams);
            TextView artName = text(listing.optString("name", "Card"), 8, Color.WHITE, true);
            artName.setMaxLines(2);
            artName.setPadding(dp(5), 0, dp(5), dp(6));
            art.addView(artName, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            card.addView(art, new LinearLayout.LayoutParams(dp(105), dp(146)));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, 0, 0);
            info.addView(text("LISTED BY " + listing.optString("owner_name", "COLLECTOR").toUpperCase(java.util.Locale.ROOT),
                    7, CYAN, true));
            TextView name = text(listing.optString("name", "Card"), 13, TEXT, true);
            name.setMaxLines(2);
            info.addView(name);
            String wanted = listing.optString("wanted_text", "");
            info.addView(text(wanted.isEmpty() ? "WANTS · OPEN TO OFFERS" : "WANTS · " + wanted,
                    8, GOLD_BRIGHT, true));
            if (!listing.optString("message", "").isEmpty()) {
                TextView note = text(listing.optString("message"), 7, MUTED, false);
                note.setMaxLines(2);
                info.addView(note);
            }
            TextView offer = button("MAKE TRADE OFFER", true);
            offer.setOnClickListener(v -> showPublicTradeRequestDialog(listing, inventory));
            LinearLayout.LayoutParams offerParams = new LinearLayout.LayoutParams(dp(160), dp(36));
            offerParams.topMargin = dp(7);
            info.addView(offer, offerParams);
            card.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            content.addView(card, rowParams());
        }
    }

    private void showTradeListingDialog(JSONArray inventory) {
        if (inventory == null || inventory.length() == 0) {
            showResultDialog("No available cards", "You need an available tradable card to create a public listing.");
            return;
        }
        String[] labels = new String[inventory.length()];
        for (int i = 0; i < inventory.length(); i++) {
            JSONObject card = inventory.optJSONObject(i);
            labels[i] = card == null ? "Unknown card" : card.optString("name", "Card")
                    + " · " + card.optString("rarity", "CARD");
        }
        Spinner cardPicker = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cardPicker.setAdapter(adapter);
        EditText wanted = editField("What card or rarity are you looking for?", false);
        EditText note = editField("Optional note for other collectors", false);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(7), dp(8), dp(7), dp(2));
        form.addView(text("CHOOSE THE CARD TO LIST", 7, GOLD_BRIGHT, true));
        form.addView(cardPicker, new LinearLayout.LayoutParams(-1, dp(45)));
        form.addView(text("WHAT YOU WANT IN RETURN", 7, GOLD_BRIGHT, true));
        form.addView(wanted, fieldParams());
        form.addView(text("MESSAGE TO THE COMMUNITY", 7, GOLD_BRIGHT, true));
        form.addView(note, fieldParams());
        new AlertDialog.Builder(this).setTitle("Create public listing").setView(form)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("PUBLISH", (dialog, which) -> {
                    JSONObject selected = inventory.optJSONObject(cardPicker.getSelectedItemPosition());
                    if (selected == null) return;
                    JSONObject body = new JSONObject();
                    try {
                        body.put("instanceId", selected.optString("instance_id"));
                        body.put("wantedText", wanted.getText().toString().trim());
                        body.put("message", note.getText().toString().trim());
                    } catch (Exception ignored) {}
                    api.post("/api/trade-board", body, result -> ui.post(() -> {
                        if (!result.ok()) showResultDialog("Listing failed", result.error);
                        else {
                            showResultDialog("CARD LISTED", result.data.optString("message",
                                    "Your card is visible to all collectors."));
                            renderTab("trades");
                        }
                    }));
                }).show();
    }

    private void showPublicTradeRequestDialog(JSONObject listing, JSONArray inventory) {
        if (inventory == null || inventory.length() == 0) {
            showResultDialog("No cards to offer", "You need available tradable cards to make an offer.");
            return;
        }
        String[] labels = new String[inventory.length()];
        boolean[] selected = new boolean[inventory.length()];
        for (int i = 0; i < inventory.length(); i++) {
            JSONObject card = inventory.optJSONObject(i);
            labels[i] = card == null ? "Unknown card" : card.optString("name", "Card")
                    + " · " + card.optString("rarity", "CARD");
        }
        EditText note = editField("Optional message to the listing owner", false);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(7), dp(8), dp(7), dp(2));
        form.addView(text("YOU WANT", 7, CYAN, true));
        form.addView(text(listing.optString("name", "Listed card"), 15, TEXT, true));
        form.addView(text("Choose 1–5 cards you are willing to exchange.", 8, MUTED, false));
        for (int i = 0; i < inventory.length(); i++) {
            android.widget.CheckBox choice = new android.widget.CheckBox(this);
            choice.setText(labels[i]);
            choice.setTextColor(TEXT);
            choice.setTextSize(10);
            final int index = i;
            choice.setOnCheckedChangeListener((buttonView, checked) -> {
                selected[index] = checked;
                int count = 0;
                for (boolean value : selected) if (value) count++;
                if (count > 5) {
                    selected[index] = false;
                    buttonView.setChecked(false);
                    Toast.makeText(this, "You can offer up to five cards.", Toast.LENGTH_SHORT).show();
                }
            });
            form.addView(choice);
        }
        form.addView(text("MESSAGE TO OWNER", 7, GOLD_BRIGHT, true));
        form.addView(note, fieldParams());
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(form);
        new AlertDialog.Builder(this).setTitle("Make public trade offer").setView(scroll)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SEND OFFER", (dialog, which) -> {
                    JSONArray ids = new JSONArray();
                    for (int i = 0; i < selected.length; i++) {
                        if (selected[i] && inventory.optJSONObject(i) != null)
                            ids.put(inventory.optJSONObject(i).optString("instance_id"));
                    }
                    if (ids.length() < 1 || ids.length() > 5) {
                        showResultDialog("Choose cards", "Select between one and five cards to offer.");
                        return;
                    }
                    JSONObject body = new JSONObject();
                    try {
                        body.put("instanceIds", ids);
                        body.put("message", note.getText().toString().trim());
                    } catch (Exception ignored) {}
                    api.post("/api/trade-board/" + listing.optString("listing_id") + "/request",
                            body, result -> ui.post(() -> {
                                if (!result.ok()) showResultDialog("Offer not sent", result.error);
                                else {
                                    showResultDialog("TRADE OFFER SENT", result.data.optString("message",
                                            "The listing owner can review your cards in the Trade Desk."));
                                    renderTab("trades");
                                }
                            }));
                }).show();
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
        JSONObject wallet = data.optJSONObject("wallet");
        if (wallet == null) wallet = new JSONObject();
        addModeSpotlight("CARD ASCENSION", "Awaken their true potential",
                "Invest Gold, Gems and Shards to raise the level and star rank of your strongest cards.",
                "✧", PINK);
        LinearLayout money = new LinearLayout(this);
        money.setOrientation(LinearLayout.HORIZONTAL);
        money.addView(statCard("GOLD", formatNumber(wallet.optLong("coins")), "Training"), weightParams(1));
        money.addView(statCard("GEMS", formatNumber(wallet.optLong("gems")), "Ascension"), weightParams(1));
        money.addView(statCard("SHARDS", formatNumber(wallet.optLong("shards")), "Awakening"), weightParams(1));
        content.addView(money);
        addGap(content, 9);
        final JSONObject walletSnapshot = wallet;
        JSONArray cards = data.optJSONArray("cards");
        if (cards == null || cards.length() == 0) {
            showNotice("No cards are ready for evolution. Collect cards from Summon Hall first.");
            return;
        }
        content.addView(text("YOUR ASCENSION ROSTER · " + cards.length() + " CARDS", 8, GOLD_BRIGHT, true));
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.optJSONObject(i);
            if (card == null) continue;
            LinearLayout row = sectionPanel();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout artFrame = new FrameLayout(this);
            int accent = cardRarityColor(card.optString("rarity", "COMMON"));
            artFrame.setBackground(gradient(Color.rgb(34, 38, 71), Color.rgb(10, 16, 31), accent, dp(10)));
            CardArtView illustration = new CardArtView(this);
            illustration.loadArtwork(cardArtworkPath(card));
            artFrame.addView(illustration, new FrameLayout.LayoutParams(-1, -1));
            row.addView(artFrame, new LinearLayout.LayoutParams(dp(78), dp(92)));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(dp(10), 0, dp(7), 0);
            copy.addView(text(card.optString("rarity", "COMMON").toUpperCase() + " · "
                    + card.optString("element", "AETHER").toUpperCase(), 7, accent, true));
            copy.addView(text(card.optString("name", "Card"), 11, TEXT, true));
            int level = card.optInt("level", 1);
            int maxLevel = Math.max(1, data.optInt("max_level", 30));
            copy.addView(text("LEVEL " + level + " / " + maxLevel + " · "
                    + starMarks(card.optInt("star_rank")), 8, MUTED, false));
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(maxLevel);
            progress.setProgress(Math.min(maxLevel, level));
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            copy.addView(progress, new LinearLayout.LayoutParams(-1, dp(6)));
            copy.addView(text(card.optInt("copies_available") + " EVOLUTION COPIES READY", 7, CYAN, true));
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView ascend = button("ASCEND", true);
            ascend.setOnClickListener(v -> showEvolutionCardDialog(card, walletSnapshot));
            row.addView(ascend, new LinearLayout.LayoutParams(dp(86), dp(37)));
            row.setOnClickListener(v -> showEvolutionCardDialog(card, walletSnapshot));
            content.addView(row, rowParams());
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
        JSONArray binders = data.optJSONArray("binders");
        JSONArray cards = data.optJSONArray("cards");
        JSONObject active = data.optJSONObject("activeBinder");
        if (binders == null) binders = new JSONArray();
        if (cards == null) cards = new JSONArray();
        if (active == null) active = new JSONObject();
        String activeName = active.optString("name", "Genesis Archive");

        addRealmScene("binder", "THE BINDER VAULT",
                "A private gallery for the cards that define your journey.", cards.length(),
                Math.max(1, cards.length()));

        LinearLayout feature = sectionPanel();
        feature.setOrientation(LinearLayout.HORIZONTAL);
        feature.setGravity(Gravity.CENTER_VERTICAL);
        feature.setPadding(dp(14), dp(13), dp(14), dp(13));
        feature.setBackground(gradient(Color.rgb(34, 53, 76), Color.rgb(10, 18, 32), CYAN, dp(15)));

        FrameLayout activeCover = createBinderCover(active, cards, true, 120, 166, 0);
        feature.addView(activeCover, new LinearLayout.LayoutParams(dp(120), dp(166)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(15), 0, 0, 0);
        copy.addView(text("CURRENTLY EQUIPPED", 7, CYAN, true));
        TextView name = text(activeName, 18, TEXT, true);
        name.setMaxLines(2);
        copy.addView(name);
        copy.addView(text(cards.length() + " CARDS STORED IN THIS ALBUM", 8, GOLD_BRIGHT, true));
        TextView hint = text(binders.length() + " ALBUMS IN YOUR PERSONAL LIBRARY",
                8, MUTED, false);
        hint.setPadding(0, dp(4), 0, 0);
        copy.addView(hint);
        TextView create = button("＋  CREATE NEW BINDER", true);
        create.setOnClickListener(v -> showCreateBinderDialog());
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(dp(176), dp(39));
        createParams.topMargin = dp(11);
        copy.addView(create, createParams);
        feature.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(feature, rowParams());

        addGap(content, 12);
        LinearLayout shelfHeader = new LinearLayout(this);
        shelfHeader.setGravity(Gravity.CENTER_VERTICAL);
        shelfHeader.addView(text("YOUR COLLECTION SHELF", 8, GOLD_BRIGHT, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        shelfHeader.addView(text(binders.length() + " ALBUMS", 7, CYAN, true));
        content.addView(shelfHeader);

        if (binders.length() > 0) {
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout shelf = new LinearLayout(this);
            shelf.setOrientation(LinearLayout.HORIZONTAL);
            shelf.setPadding(dp(1), dp(8), dp(1), dp(8));
            scroll.addView(shelf);
            for (int i = 0; i < binders.length(); i++) {
                JSONObject binder = binders.optJSONObject(i);
                if (binder == null) continue;
                boolean selected = binder.optInt("is_active") == 1;
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER_HORIZONTAL);
                FrameLayout cover = createBinderCover(binder, cards, selected, 102, 146, i);
                item.addView(cover, new LinearLayout.LayoutParams(dp(102), dp(146)));
                TextView binderName = text(binder.optString("name", "Untitled Binder"),
                        9, TEXT, true);
                binderName.setGravity(Gravity.CENTER);
                binderName.setMaxLines(2);
                binderName.setPadding(dp(3), dp(7), dp(3), dp(2));
                item.addView(binderName, new LinearLayout.LayoutParams(-1, -2));
                TextView count = text(binder.optInt("card_count") + " CARDS  ·  "
                        + (selected ? "ACTIVE" : "OPEN"), 7,
                        selected ? CYAN : MUTED, true);
                count.setGravity(Gravity.CENTER);
                item.addView(count);
                item.setClickable(true);
                item.setFocusable(true);
                item.setOnClickListener(v -> showBinderActions(binder));
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(dp(120), -2);
                itemParams.rightMargin = dp(9);
                shelf.addView(item, itemParams);
            }
            content.addView(scroll, rowParams());
        } else {
            TextView emptyShelf = text("Your binder shelf is empty. Create your first album to start organising cards.",
                    9, MUTED, false);
            emptyShelf.setPadding(dp(8), dp(12), dp(8), dp(12));
            content.addView(emptyShelf);
        }

        addGap(content, 8);
        LinearLayout cardHeading = new LinearLayout(this);
        cardHeading.setGravity(Gravity.CENTER_VERTICAL);
        cardHeading.addView(text("CARDS IN ACTIVE ALBUM", 8, GOLD_BRIGHT, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        cardHeading.addView(text(String.valueOf(cards.length()), 8, CYAN, true));
        content.addView(cardHeading);

        if (cards.length() == 0) {
            LinearLayout empty = sectionPanel();
            empty.setGravity(Gravity.CENTER);
            TextView mark = text("✧", 28, GOLD_BRIGHT, true);
            mark.setGravity(Gravity.CENTER);
            empty.addView(mark);
            empty.addView(text("THIS ALBUM IS WAITING FOR CARDS", 9, TEXT, true));
            empty.addView(text("Move cards from your archive to build a collection worth displaying.",
                    8, MUTED, false));
            TextView viewCards = button("OPEN CARD ARCHIVE", true);
            viewCards.setOnClickListener(v -> renderTab("collection"));
            empty.addView(viewCards, new LinearLayout.LayoutParams(dp(180), dp(38)));
            content.addView(empty, rowParams());
        } else {
            int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                    / getResources().getDisplayMetrics().density);
            int columns = widthDp >= 1400 ? 6 : widthDp >= 1050 ? 5 : widthDp >= 680 ? 4 : 3;
            LinearLayout grid = null;
            int visible = 0;
            for (int i = 0; i < cards.length(); i++) {
                JSONObject card = cards.optJSONObject(i);
                if (card == null) continue;
                if (visible % columns == 0) {
                    grid = new LinearLayout(this);
                    grid.setOrientation(LinearLayout.HORIZONTAL);
                    content.addView(grid, new LinearLayout.LayoutParams(-1, -2));
                }
                LinearLayout tile = createCardTile(card);
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, -2, 1f);
                tileParams.setMargins(dp(4), dp(4), dp(4), dp(8));
                grid.addView(tile, tileParams);
                visible++;
            }
            int remainder = visible % columns;
            if (remainder != 0 && grid != null) {
                for (int i = remainder; i < columns; i++)
                    grid.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    private FrameLayout createBinderCover(JSONObject binder, JSONArray cards,
                                          boolean selected, int width, int height, int index) {
        String theme = binder == null ? "" : binder.optString("theme_key", "").toLowerCase(java.util.Locale.ROOT);
        int accent = selected ? CYAN : theme.contains("fire") ? RED
                : theme.contains("shadow") ? VIOLET : theme.contains("gold") ? GOLD_BRIGHT
                : theme.contains("ocean") ? BLUE
                : new int[]{GOLD_BRIGHT, VIOLET, CYAN, PINK, BLUE}[Math.floorMod(index, 5)];
        FrameLayout cover = new FrameLayout(this);
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(Color.red(accent) / 2 + 20, Color.green(accent) / 2 + 18,
                        Color.blue(accent) / 2 + 23), Color.rgb(10, 16, 31)});
        base.setCornerRadius(dp(9));
        base.setStroke(dp(selected ? 2 : 1), accent);
        cover.setBackground(base);
        cover.setClipToOutline(true);

        if (selected && cards != null && cards.length() > 0 && cards.optJSONObject(0) != null) {
            CardArtView art = new CardArtView(this);
            art.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            art.setAlpha(0.88f);
            art.loadArtwork(cardArtworkPath(cards.optJSONObject(0)));
            FrameLayout.LayoutParams artParams = new FrameLayout.LayoutParams(-1, -1);
            artParams.setMargins(dp(6), dp(6), dp(6), dp(6));
            cover.addView(art, artParams);
        } else {
            TextView field = text("✧", 48, accent, true);
            field.setGravity(Gravity.CENTER);
            field.setShadowLayer(dp(12), 0, 0, accent);
            cover.addView(field, new FrameLayout.LayoutParams(-1, -1));
            View ornament = new View(this);
            ornament.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.TRANSPARENT, Color.argb(130, Color.red(accent),
                            Color.green(accent), Color.blue(accent)), Color.TRANSPARENT}));
            FrameLayout.LayoutParams ornamentParams = new FrameLayout.LayoutParams(-1, dp(2), Gravity.CENTER);
            ornamentParams.leftMargin = dp(15);
            ornamentParams.rightMargin = dp(15);
            cover.addView(ornament, ornamentParams);
        }

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(20, 2, 5, 13), Color.TRANSPARENT,
                        Color.argb(215, 2, 5, 13)}));
        cover.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View spine = new View(this);
        spine.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(230, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent))}));
        cover.addView(spine, new FrameLayout.LayoutParams(dp(5), -1, Gravity.LEFT));
        TextView seal = text(selected ? "ACTIVE ALBUM" : "CARDUNIVERSE", 6, Color.WHITE, true);
        seal.setGravity(Gravity.CENTER);
        seal.setPadding(dp(5), dp(4), dp(5), dp(4));
        seal.setBackground(gradient(Color.argb(220, 9, 15, 29), Color.argb(190, 9, 15, 29),
                accent, dp(5)));
        FrameLayout.LayoutParams sealParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        sealParams.topMargin = dp(9);
        cover.addView(seal, sealParams);
        TextView label = text("✦", 17, accent, true);
        label.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(-1, dp(28), Gravity.BOTTOM);
        labelParams.bottomMargin = dp(7);
        cover.addView(label, labelParams);
        return cover;
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
        JSONArray list = data.optJSONArray("achievements");
        JSONObject metrics = data.optJSONObject("metrics");
        if (list == null) list = new JSONArray();
        int unlocked = 0, claimed = 0, ready = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            if (!item.isNull("unlocked_at")) {
                unlocked++;
                if (item.isNull("claimed_at")) ready++;
            }
            if (!item.isNull("claimed_at")) claimed++;
        }
        addRealmScene("records", "HALL OF LEGENDS", "Permanent records of every achievement, collection and victory.", unlocked, Math.max(1, list.length()));
        LinearLayout hall = sectionPanel();
        hall.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(66, 54, 42), Color.rgb(17, 23, 39)}));
        hall.addView(text("THE FIRSTLIGHT RECORDS", 8, GOLD_BRIGHT, true));
        hall.addView(text("A legacy written by your actions", 19, TEXT, true));
        hall.addView(text("Every collection, victory and milestone leaves a permanent mark.", 8, MUTED, false));
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard("UNLOCKED", formatNumber(unlocked), "Achievements"), weightParams(1));
        stats.addView(statCard("CLAIMED", formatNumber(claimed), "Rewards earned"), weightParams(1));
        stats.addView(statCard("READY", formatNumber(ready), "Awaiting claim"), weightParams(1));
        hall.addView(stats);
        content.addView(hall, rowParams());

        if (metrics != null) {
            content.addView(text("LIFETIME RECORDS", 8, GOLD_BRIGHT, true));
            LinearLayout lifetime = new LinearLayout(this);
            lifetime.setOrientation(LinearLayout.HORIZONTAL);
            lifetime.addView(statCard("CARDS", formatNumber(metrics.optLong("unique_cards")), "Unique collected"), weightParams(1));
            lifetime.addView(statCard("ADVENTURE", formatNumber(metrics.optLong("adventure_clears")), "Stages cleared"), weightParams(1));
            lifetime.addView(statCard("BOSSES", formatNumber(metrics.optLong("boss_clears")), "Defeated"), weightParams(1));
            lifetime.addView(statCard("PACKS", formatNumber(metrics.optLong("packs_opened")), "Opened"), weightParams(1));
            content.addView(lifetime);
        }

        content.addView(text("ACHIEVEMENT REGISTER · " + list.length() + " RECORDS",
                8, GOLD_BRIGHT, true));
        if (list.length() == 0) { showNotice("No achievements are configured yet."); return; }
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            int progress = Math.max(0, item.optInt("progress"));
            int target = Math.max(1, item.optInt("target"));
            int pct = Math.min(100, Math.round(progress * 100f / target));
            boolean isUnlocked = !item.isNull("unlocked_at");
            boolean isClaimed = !item.isNull("claimed_at");
            int accent = isClaimed ? GREEN : isUnlocked ? GOLD_BRIGHT : VIOLET;
            LinearLayout card = sectionPanel();
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);

            FrameLayout medal = new FrameLayout(this);
            GradientDrawable medalBg = new GradientDrawable();
            medalBg.setShape(GradientDrawable.OVAL);
            medalBg.setColor(Color.rgb(Color.red(accent) / 5 + 23,
                    Color.green(accent) / 5 + 23, Color.blue(accent) / 5 + 27));
            medalBg.setStroke(dp(1), accent);
            medal.setBackground(medalBg);
            TextView mark = text(isClaimed ? "✓" : isUnlocked ? "✦" : "◇", 27, accent, true);
            mark.setGravity(Gravity.CENTER);
            medal.addView(mark, new FrameLayout.LayoutParams(-1, -1));
            card.addView(medal, new LinearLayout.LayoutParams(dp(60), dp(60)));

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(dp(11), 0, dp(7), 0);
            details.addView(text(item.optString("title", "Achievement"), 11, TEXT, true));
            TextView description = text(item.optString("description", ""), 8, MUTED, false);
            description.setMaxLines(2);
            details.addView(description);
            LinearLayout progressLine = new LinearLayout(this);
            progressLine.setGravity(Gravity.CENTER_VERTICAL);
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(pct);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            progressLine.addView(bar, new LinearLayout.LayoutParams(0, dp(7), 1f));
            TextView percentage = text(pct + "%", 7, accent, true);
            percentage.setPadding(dp(7), 0, 0, 0);
            progressLine.addView(percentage);
            details.addView(progressLine);
            details.addView(text(formatNumber(item.optLong("reward_coins")) + " GOLD · "
                    + formatNumber(item.optLong("reward_xp")) + " EXP", 7, GOLD_BRIGHT, true));
            card.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));

            if (isClaimed) {
                card.addView(text("CLAIMED", 7, GREEN, true));
            } else if (isUnlocked) {
                TextView claim = button("CLAIM", true);
                claim.setOnClickListener(v -> confirmAction("Claim " + item.optString("title") + "?",
                        "Your achievement reward will be added to your account.",
                        () -> api.post("/api/achievements/" + item.optString("slug") + "/claim",
                                new JSONObject(), result -> ui.post(() -> {
                                    if (!result.ok()) showResultDialog("Achievement unavailable", result.error);
                                    else {
                                        JSONObject reward = result.data.optJSONObject("reward");
                                        showResultDialog("ACHIEVEMENT CLAIMED", "Gold: "
                                                + (reward == null ? 0 : reward.optLong("coins"))
                                                + " · EXP: " + (reward == null ? 0 : reward.optLong("xp")));
                                        refreshAccount(() -> renderTab("achievements"));
                                    }
                                }))));
                card.addView(claim, new LinearLayout.LayoutParams(dp(76), dp(36)));
            } else {
                card.addView(text(progress + " / " + target, 7, MUTED, true));
            }
            content.addView(card, rowParams());
        }
    }

    private void renderProgression(JSONObject data) {
        int level = data.optInt("level", 1);
        int xp = data.optInt("xp");
        boolean maxLevel = level >= 1000;
        int need = maxLevel ? Math.max(1, xp) : Math.max(1, data.optInt("xpRequired"));
        int page = data.optInt("rewardPage");
        int pageSize = data.optInt("rewardPageSize", 30);
        int total = data.optInt("rewardTotal");
        int pages = Math.max(1, (int) Math.ceil(total / (double) Math.max(1, pageSize)));

        addRealmScene("records", "COLLECTOR MILESTONES", "Your journey through the Firstlight ranks.", level, 1000);
        LinearLayout hero = sectionPanel();
        hero.setBackground(gradient(Color.rgb(71, 55, 106), Color.rgb(12, 18, 39), GOLD, dp(17)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout crest = new FrameLayout(this);
        crest.setBackground(gradient(Color.rgb(118, 88, 166), Color.rgb(40, 35, 76), GOLD_BRIGHT, dp(14)));
        TextView levelText = text(String.valueOf(level), 22, Color.WHITE, true);
        levelText.setGravity(Gravity.CENTER);
        crest.addView(levelText, new FrameLayout.LayoutParams(-1, -1));
        heading.addView(crest, new LinearLayout.LayoutParams(dp(70), dp(70)));
        LinearLayout levelCopy = new LinearLayout(this);
        levelCopy.setOrientation(LinearLayout.VERTICAL);
        levelCopy.setPadding(dp(12), 0, 0, 0);
        levelCopy.addView(text(maxLevel ? "LEVEL CAP REACHED" : "COLLECTOR LEVEL", 8, GOLD_BRIGHT, true));
        levelCopy.addView(text(maxLevel ? "MASTER COLLECTOR" : "LEVEL " + level, 20, TEXT, true));
        levelCopy.addView(text(maxLevel ? formatNumber(xp) + " TOTAL XP" : formatNumber(xp) + " / " + formatNumber(need) + " XP",
                8, MUTED, true));
        heading.addView(levelCopy, new LinearLayout.LayoutParams(0, -2, 1f));
        hero.addView(heading);
        addGap(hero, 10);
        ProgressBar xpBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        xpBar.setMax(100);
        xpBar.setProgress(maxLevel ? 100 : Math.min(100, Math.round(xp * 100f / need)));
        xpBar.setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD_BRIGHT));
        hero.addView(xpBar, new LinearLayout.LayoutParams(-1, dp(9)));
        LinearLayout xpLabels = new LinearLayout(this);
        xpLabels.setGravity(Gravity.CENTER_VERTICAL);
        xpLabels.addView(text(maxLevel ? "MAX RANK" : "NEXT LEVEL PROGRESS", 7, GOLD_BRIGHT, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        xpLabels.addView(text(data.optInt("claimableCount") + " REWARDS READY", 7, CYAN, true));
        hero.addView(xpLabels);
        content.addView(hero, rowParams());

        LinearLayout totals = new LinearLayout(this);
        totals.setOrientation(LinearLayout.HORIZONTAL);
        totals.addView(statCard("LEVEL", formatNumber(level), "Collector rank"), weightParams(1));
        totals.addView(statCard("CLAIMABLE", formatNumber(data.optInt("claimableCount")), "Ready now"), weightParams(1));
        totals.addView(statCard("MILESTONES", formatNumber(total), "Total rewards"), weightParams(1));
        content.addView(totals);
        addGap(content, 10);

        JSONArray rewards = data.optJSONArray("rewards");
        if (rewards == null) rewards = new JSONArray();
        content.addView(text("REWARD TRACK  ·  PAGE " + (page + 1) + " / " + pages,
                8, GOLD_BRIGHT, true));
        for (int i = 0; i < rewards.length(); i++) {
            JSONObject reward = rewards.optJSONObject(i);
            if (reward == null) continue;
            int rewardLevel = reward.optInt("level");
            boolean unlocked = rewardLevel <= level;
            boolean claimed = reward.optInt("claimed") == 1;
            StringBuilder rewardText = new StringBuilder();
            rewardText.append(formatNumber(reward.optLong("reward_coins"))).append(" Gold  ·  ")
                    .append(formatNumber(reward.optLong("reward_gems"))).append(" Gems  ·  ")
                    .append(formatNumber(reward.optLong("reward_shards"))).append(" Shards");
            if (reward.optInt("reward_pack_quantity") > 0)
                rewardText.append("\n").append(reward.optInt("reward_pack_quantity")).append(" × ")
                        .append(reward.optString("reward_pack_name", "Summon Pack"));
            if (!reward.isNull("reward_cosmetic_name") && !reward.optString("reward_cosmetic_name").isEmpty())
                rewardText.append("\n").append(reward.optString("reward_cosmetic_name"));
            LinearLayout card = sectionPanel();
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            int accent = claimed ? GREEN : unlocked ? GOLD_BRIGHT : Color.rgb(94, 111, 151);
            FrameLayout marker = new FrameLayout(this);
            marker.setBackground(gradient(unlocked ? Color.rgb(89, 65, 111) : Color.rgb(35, 43, 66),
                    Color.rgb(13, 19, 38), accent, dp(12)));
            TextView badge = text(claimed ? "✓" : String.valueOf(rewardLevel), 16, accent, true);
            badge.setGravity(Gravity.CENTER);
            marker.addView(badge, new FrameLayout.LayoutParams(-1, -1));
            card.addView(marker, new LinearLayout.LayoutParams(dp(54), dp(54)));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(dp(10), 0, dp(6), 0);
            copy.addView(text("LEVEL " + rewardLevel, 7, accent, true));
            copy.addView(text(reward.optString("reward_title", "Milestone reward"), 11, TEXT, true));
            TextView details = text(rewardText.toString(), 8, MUTED, false);
            details.setMaxLines(3);
            copy.addView(details);
            card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
            if (claimed) {
                card.addView(text("CLAIMED", 7, GREEN, true));
            } else if (unlocked) {
                TextView claim = button("CLAIM", true);
                claim.setOnClickListener(v -> confirmAction("Claim level " + rewardLevel + " reward?",
                        "This milestone can only be claimed once.", () ->
                        api.post("/api/progression/claim", payload("level", rewardLevel), result -> ui.post(() -> {
                            if (!result.ok()) showResultDialog("Reward unavailable", result.error);
                            else {
                                if (sfxEnabled && gameAudio != null) gameAudio.playSuccess();
                                showResultDialog("LEVEL REWARD CLAIMED", result.data.optString("message", "Milestone reward added."));
                                refreshAccount(() -> loadProgressionPage(page));
                            }
                        }))));
                card.addView(claim, new LinearLayout.LayoutParams(dp(78), dp(36)));
            } else {
                card.addView(text("LOCKED", 7, MUTED, true));
            }
            content.addView(card, rowParams());
        }
        if (total > pageSize) {
            LinearLayout pager = new LinearLayout(this);
            pager.setGravity(Gravity.CENTER);
            TextView previous = button("← PREVIOUS", false);
            previous.setEnabled(page > 0);
            previous.setOnClickListener(v -> loadProgressionPage(page - 1));
            TextView next = button("NEXT →", false);
            next.setEnabled(page + 1 < pages);
            next.setOnClickListener(v -> loadProgressionPage(page + 1));
            pager.addView(previous, new LinearLayout.LayoutParams(dp(120), dp(38)));
            TextView label = text("PAGE " + (page + 1) + " / " + pages, 8, GOLD_BRIGHT, true);
            label.setGravity(Gravity.CENTER);
            pager.addView(label, new LinearLayout.LayoutParams(0, dp(38), 1f));
            pager.addView(next, new LinearLayout.LayoutParams(dp(100), dp(38)));
            content.addView(pager);
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
        addModeSpotlight("LIVE EVENT CIRCUIT", "Limited-time rewards are waiting",
                "Follow the login journey, collect each day's gift and spend event currency in the festival exchange.",
                "✺", PINK);
        JSONObject login = data.optJSONObject("login_event");
        if (login == null) login = new JSONObject();
        JSONObject eventDefinition = login.optJSONObject("event");
        JSONArray rewards = login.optJSONArray("rewards");
        if (rewards == null) rewards = new JSONArray();
        if (eventDefinition != null) {
            int day = login.optInt("currentDay");
            LinearLayout journey = sectionPanel();
            journey.setBackground(gradient(Color.rgb(77, 39, 76), Color.rgb(13, 18, 39), PINK, dp(17)));
            journey.addView(text("LIMITED-TIME LOGIN JOURNEY", 8, PINK, true));
            journey.addView(text(eventDefinition.optString("title", "Realm Login Event"), 19, TEXT, true));
            TextView description = text(eventDefinition.optString("description", "Claim a reward for every event day."),
                    8, MUTED, false);
            journey.addView(description);
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(Math.max(1, rewards.length()));
            bar.setProgress(Math.min(rewards.length(), Math.max(0, day)));
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(PINK));
            journey.addView(bar, new LinearLayout.LayoutParams(-1, dp(8)));
            journey.addView(text(login.optBoolean("completed") ? "JOURNEY COMPLETE"
                    : "DAY " + day + " OF " + rewards.length(), 8, GOLD_BRIGHT, true));
            LinearLayout track = new LinearLayout(this);
            track.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < rewards.length(); i++) {
                JSONObject reward = rewards.optJSONObject(i);
                if (reward == null) continue;
                int index = reward.optInt("day_index");
                boolean claimed = reward.optInt("is_claimed") == 1;
                boolean today = index == day;
                int accent = claimed ? CYAN : today ? PINK : Color.rgb(99, 111, 149);
                LinearLayout tile = new LinearLayout(this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(3), dp(7), dp(3), dp(7));
                tile.setBackground(gradient(claimed ? Color.rgb(28, 74, 70) : today
                        ? Color.rgb(92, 46, 82) : Color.rgb(26, 34, 58),
                        Color.rgb(10, 17, 34), accent, dp(10)));
                tile.addView(text("DAY " + index, 6, accent, true));
                tile.addView(text(claimed ? "✓" : today ? "✦" : "◇", 15, accent, true));
                tile.addView(text(claimed ? "CLAIMED" : today ? "TODAY" : index < day ? "PASSED" : "NEXT",
                        6, TEXT, true));
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(62), 1f);
                tp.setMargins(dp(2), dp(4), dp(2), dp(4));
                track.addView(tile, tp);
            }
            journey.addView(track);
            if (login.optBoolean("canClaim")) {
                TextView claim = button("CLAIM TODAY'S EVENT REWARD", true);
                claim.setOnClickListener(v -> confirmAction("Claim event login reward?",
                        "Today's event reward can only be claimed once.", () ->
                        api.post("/api/events/login/claim", new JSONObject(), result -> ui.post(() -> {
                            if (!result.ok()) showResultDialog("Event reward unavailable", result.error);
                            else {
                                if (sfxEnabled && gameAudio != null) gameAudio.playSuccess();
                                showResultDialog("EVENT REWARD CLAIMED", result.data.optString("message", "Your login reward has been added."));
                                refreshAccount(() -> renderTab("events"));
                            }
                        }))));
                journey.addView(claim, rowParams());
            }
            content.addView(journey, rowParams());
            content.addView(text("YOUR LOGIN REWARD SCHEDULE", 8, GOLD_BRIGHT, true));
            for (int i = 0; i < rewards.length(); i++) {
                JSONObject reward = rewards.optJSONObject(i);
                if (reward == null) continue;
                int index = reward.optInt("day_index");
                String details = formatNumber(reward.optLong("reward_coins")) + " Gold  ·  "
                        + formatNumber(reward.optLong("reward_gems")) + " Gems  ·  "
                        + formatNumber(reward.optLong("reward_shards")) + " Shards  ·  "
                        + formatNumber(reward.optLong("reward_xp")) + " EXP";
                if (reward.optInt("reward_pack_quantity") > 0)
                    details += "  ·  " + reward.optInt("reward_pack_quantity") + " × "
                            + reward.optString("reward_pack_name", "Pack");
                if (!reward.optString("reward_card_name").isEmpty())
                    details += "\n" + reward.optString("reward_card_name");
                content.addView(dataRow("DAY " + index + " · " + reward.optString("title", "Event reward"),
                        details, reward.optInt("is_claimed") == 1 ? "CLAIMED"
                                : index == day ? "TODAY" : index < day ? "PASSED" : "UPCOMING"), rowParams());
            }
        } else {
            showNotice("There is no active limited-time login journey right now.");
        }

        JSONObject event = data.optJSONObject("event");
        if (event == null) {
            addGap(content, 12);
            content.addView(sectionHeading("FESTIVAL EXCHANGE"));
            showNotice("No limited-time shop event is currently open.");
            return;
        }
        addGap(content, 12);
        JSONObject wallet = data.optJSONObject("wallet");
        if (wallet == null) wallet = new JSONObject();
        LinearLayout festival = sectionPanel();
        festival.setBackground(gradient(Color.rgb(52, 46, 99), Color.rgb(12, 18, 38), VIOLET, dp(17)));
        festival.addView(text("SEASONAL FESTIVAL · EVENT SHOP", 8, VIOLET, true));
        festival.addView(text(event.optString("title", "Festival"), 20, TEXT, true));
        festival.addView(text(event.optString("description", ""), 8, MUTED, false));
        festival.addView(text("ENDS · " + event.optString("ends_at", ""), 8, GOLD_BRIGHT, true));
        LinearLayout balances = new LinearLayout(this);
        balances.setOrientation(LinearLayout.HORIZONTAL);
        balances.addView(statCard("GOLD", formatNumber(wallet.optLong("coins")), "Balance"), weightParams(1));
        balances.addView(statCard("GEMS", formatNumber(wallet.optLong("gems")), "Balance"), weightParams(1));
        balances.addView(statCard("SHARDS", formatNumber(wallet.optLong("shards")), "Balance"), weightParams(1));
        festival.addView(balances);
        content.addView(festival, rowParams());

        JSONArray items = data.optJSONArray("items");
        if (items == null) items = new JSONArray();
        content.addView(text("FESTIVAL MARKET · " + items.length() + " ITEMS", 8, GOLD_BRIGHT, true));
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String itemName = item.optString("name", "Festival reward");
            String currency = item.optString("currency", "COINS").toUpperCase(java.util.Locale.ROOT);
            long price = item.optLong("price");
            long balance = wallet.optLong(currency.toLowerCase(java.util.Locale.ROOT));
            boolean soldOut = !item.isNull("stock_remaining") && item.optLong("stock_remaining") <= 0;
            boolean affordable = balance >= price;
            LinearLayout card = sectionPanel();
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout icon = new FrameLayout(this);
            icon.setBackground(gradient(Color.rgb(69, 42, 92), Color.rgb(15, 20, 41), PINK, dp(12)));
            TextView glyph = text("✧", 31, PINK, true);
            glyph.setGravity(Gravity.CENTER);
            icon.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
            card.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(70)));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(dp(10), 0, dp(7), 0);
            copy.addView(text(item.optString("item_type", "REWARD").toUpperCase() + " · "
                    + item.optString("rarity", "EVENT"), 7, PINK, true));
            copy.addView(text(itemName, 11, TEXT, true));
            copy.addView(text(formatNumber(price) + " " + currency + " · "
                    + (item.isNull("stock_remaining") ? "OPEN STOCK" : item.optLong("stock_remaining") + " LEFT"),
                    8, GOLD_BRIGHT, true));
            card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView buy = button(soldOut ? "SOLD OUT" : !affordable ? "NEED MORE" : "EXCHANGE", true);
            buy.setEnabled(!soldOut && affordable);
            buy.setOnClickListener(v -> confirmAction("Purchase " + itemName + "?",
                    "Spend " + formatNumber(price) + " " + currency + ". Stock and price are checked again by the server.",
                    () -> api.post("/api/events/purchase", payload("itemId", item.optString("item_id")),
                            result -> ui.post(() -> {
                                if (!result.ok()) showResultDialog("Purchase failed", result.error);
                                else {
                                    if (sfxEnabled && gameAudio != null) gameAudio.playSuccess();
                                    showResultDialog("FESTIVAL REWARD ADDED", result.data.optString("message", "Purchase completed."));
                                    refreshAccount(() -> renderTab("events"));
                                }
                            }))));
            card.addView(buy, new LinearLayout.LayoutParams(dp(100), dp(36)));
            content.addView(card, rowParams());
        }
        if (items.length() == 0) showNotice("The festival exchange has no active listings.");
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
        JSONObject season = data.optJSONObject("season");
        JSONObject rating = data.optJSONObject("rating");
        JSONArray rows = data.optJSONArray("leaderboard");
        if (rows == null) rows = new JSONArray();
        boolean adminViewer = isAdminAccount() || data.optBoolean("hidden");
        addModeSpotlight(adminViewer ? "ADMIN OBSERVER MODE" : "COMPETITIVE SEASON",
                adminViewer ? "Public Ranked ladder" : (season == null ? "Ranked Season" : season.optString("name", "Ranked Season")),
                adminViewer ? "Read-only access. Admin accounts are never placed on the public ladder and cannot enter PvP."
                        : "Climb the seasonal ladder through tactical duels and earn your place among the top collectors.",
                "♛", GOLD_BRIGHT);
        if (season == null || season.length() == 0) {
            showNotice("There is no active Ranked Season at the moment.");
            return;
        }
        if (adminViewer) {
            LinearLayout observer = sectionPanel();
            observer.addView(text("MASTER ADMIN · SPECTATOR ONLY", 8, CYAN, true));
            observer.addView(text(season.optString("name", "Ranked Season"), 17, TEXT, true));
            observer.addView(text("Season " + season.optString("starts_at", "") + " · Ends " + season.optString("ends_at", ""),
                    8, MUTED, false));
            observer.addView(text("This account has no Ranked rating or ladder placement. The public standings remain visible below.",
                    8, MUTED, false));
            content.addView(observer, rowParams());
            content.addView(text("PUBLIC SEASON LADDER · " + rows.length() + " COLLECTORS", 8, GOLD_BRIGHT, true));
            for (int i = 0; i < rows.length(); i++) {
                JSONObject player = rows.optJSONObject(i);
                if (player == null) continue;
                String rank = i == 0 ? "✦ #1" : i == 1 ? "✧ #2" : i == 2 ? "◆ #3" : "#" + (i + 1);
                LinearLayout row = dataRow(rank + " · " + player.optString("display_name", "Collector"),
                        formatNumber(player.optLong("rating")) + " RATING · " + player.optInt("wins") + "W / "
                                + player.optInt("losses") + "L / " + player.optInt("draws") + "D",
                        player.optString("tier", "BRONZE"));
                if (i == 0) row.setBackground(gradient(Color.rgb(79, 62, 41), Color.rgb(22, 25, 43), GOLD, dp(13)));
                else if (i < 3) row.setBackground(gradient(Color.rgb(42, 49, 83), Color.rgb(13, 20, 38), VIOLET, dp(13)));
                content.addView(row, rowParams());
            }
            if (rows.length() == 0) showNotice("The public Ranked ladder has no listed collectors yet.");
            return;
        }
        if (data.optBoolean("rankedBanned")) {
            LinearLayout banned = sectionPanel();
            banned.addView(text("RANKED ACCESS SUSPENDED", 9, RED, true));
            banned.addView(text(data.optString("rankedBanReason", "Moderator review"), 12, TEXT, true));
            banned.addView(text(data.optString("rankedBanUntil", "Permanent restriction"), 8, MUTED, false));
            content.addView(banned, rowParams());
        }
        if (rating != null) {
            LinearLayout hero = sectionPanel();
            hero.addView(text("COMPETITIVE SEASON", 8, GOLD, true));
            hero.addView(text(season.optString("name", "Ranked Season"), 20, TEXT, true));
            hero.addView(text(season.optString("starts_at", "") + " · " + season.optString("ends_at", ""),
                    8, MUTED, false));
            hero.addView(text(rating.optString("tier", "BRONZE"), 26, GOLD_BRIGHT, true));
            hero.addView(text(formatNumber(rating.optLong("rating")) + " RATING · #"
                    + rating.optInt("position") + " ON THE LADDER", 9, TEXT, true));
            content.addView(hero, rowParams());
            LinearLayout stats = new LinearLayout(this);
            stats.setOrientation(LinearLayout.HORIZONTAL);
            stats.addView(statCard("WINS", String.valueOf(rating.optInt("wins")), "Ranked"), weightParams(1));
            stats.addView(statCard("LOSSES", String.valueOf(rating.optInt("losses")), "Ranked"), weightParams(1));
            stats.addView(statCard("DRAWS", String.valueOf(rating.optInt("draws")), "Ranked"), weightParams(1));
            stats.addView(statCard("STREAK", String.valueOf(rating.optInt("win_streak")), "Wins"), weightParams(1));
            content.addView(stats);
        }
        content.addView(text("SEASON LEADERBOARD", 8, GOLD_BRIGHT, true));
        for (int i = 0; i < rows.length(); i++) {
            JSONObject player = rows.optJSONObject(i);
            if (player != null) content.addView(dataRow("#" + (i + 1) + " · "
                    + player.optString("display_name", "Collector"),
                    player.optString("rating") + " rating · " + player.optInt("wins") + "W / "
                            + player.optInt("losses") + "L",
                    player.optString("tier", "BRONZE")), rowParams());
        }
        if (!data.optBoolean("rankedBanned")) {
            TextView enter = button("FIND RANKED MATCH", true);
            enter.setOnClickListener(v -> confirmAction("Queue for Ranked Arena?",
                    "Ranked matchmaking uses your live rating and season.", () ->
                    api.post("/api/arena/queue", payload("mode", "RANKED"), result -> ui.post(() -> {
                        if (!result.ok()) showResultDialog("Ranked queue unavailable", result.error);
                        else {
                            showResultDialog("RANKED MATCHMAKING", result.data.optString("message",
                                    result.data.optBoolean("matched") ? "Match found." : "Searching for an opponent."));
                            renderTab("arena");
                        }
                    }))));
            content.addView(enter, new LinearLayout.LayoutParams(dp(210), dp(40)));
        }
    }

    private void showChoiceDialog(String title, String subtitle, String[] options,
                                  java.util.function.IntConsumer onChoose) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(17), dp(16), dp(17), dp(15));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(25, 36, 56), Color.rgb(8, 13, 24)});
        bg.setCornerRadius(dp(15));
        bg.setStroke(dp(1), Color.rgb(65, 81, 108));
        shell.setBackground(bg);
        shell.addView(text("COLLECTOR ARCHIVE", 7, CYAN, true));
        shell.addView(text(title, 18, TEXT, true));
        if (subtitle != null && !subtitle.isEmpty()) shell.addView(text(subtitle, 8, MUTED, false));
        addGap(shell, 10);
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView option = button(options[i], i == 0);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setPadding(dp(12), 0, dp(12), 0);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(40));
            p.bottomMargin = dp(6);
            shell.addView(option, p);
            option.setOnClickListener(v -> {
                dialog.dismiss();
                onChoose.accept(index);
            });
        }
        TextView cancel = button("CLOSE", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        shell.addView(cancel, new LinearLayout.LayoutParams(-1, dp(36)));
        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(dp(430), getResources().getDisplayMetrics().widthPixels - dp(42)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showBinderActions(JSONObject binder) {
        String id = binder.optString("binder_id");
        boolean isDefault = binder.optInt("is_default") == 1;
        String[] options = isDefault
                ? new String[]{"SET ACTIVE", "RENAME / CHANGE THEME"}
                : new String[]{"SET ACTIVE", "RENAME / CHANGE THEME", "MANAGE CARD CONTENTS", "DELETE BINDER"};
        showChoiceDialog(binder.optString("name", "Binder"),
                binder.optInt("card_count") + " cards · "
                        + binder.optString("theme_key", "classic").toUpperCase(java.util.Locale.ROOT),
                options, selected -> {
                    String choice = options[selected];
                    if ("SET ACTIVE".equals(choice)) {
                        api.post("/api/binders/" + id, payload("set_active", true), result -> ui.post(() -> {
                            if (!result.ok()) showResultDialog("Binder not changed", result.error);
                            else renderTab("binders");
                        }));
                    } else if ("RENAME / CHANGE THEME".equals(choice)) {
                        showBinderEditDialog(binder);
                    } else if ("MANAGE CARD CONTENTS".equals(choice)) {
                        showBinderCardsDialog(id);
                    } else if ("DELETE BINDER".equals(choice)) {
                        confirmAction("Delete " + binder.optString("name") + "?",
                                "Only the binder is deleted. Your cards and collection remain untouched.",
                                () -> api.delete("/api/binders/" + id, result -> ui.post(() -> {
                                    if (!result.ok()) showResultDialog("Binder not deleted", result.error);
                                    else {
                                        showResultDialog("BINDER DELETED", result.data.optString("message", "Binder removed."));
                                        renderTab("binders");
                                    }
                                })));
                    }
                });
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
        boolean success = !confirm && title != null
                && (title.toUpperCase(java.util.Locale.ROOT).contains("CLAIM")
                || title.toUpperCase(java.util.Locale.ROOT).contains("COMPLETE")
                || title.toUpperCase(java.util.Locale.ROOT).contains("ACQUIRED")
                || title.toUpperCase(java.util.Locale.ROOT).contains("VICTORY")
                || title.toUpperCase(java.util.Locale.ROOT).contains("PURCHASE"));
        boolean error = !confirm && title != null
                && (title.toUpperCase(java.util.Locale.ROOT).contains("FAILED")
                || title.toUpperCase(java.util.Locale.ROOT).contains("UNAVAILABLE")
                || title.toUpperCase(java.util.Locale.ROOT).contains("ERROR")
                || title.toUpperCase(java.util.Locale.ROOT).contains("COULD NOT"));
        int accent = confirm ? GOLD_BRIGHT : success ? CYAN : error ? RED : VIOLET;

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(20), dp(18), dp(20), dp(17));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(27, 38, 58), Color.rgb(8, 13, 25)});
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(Color.red(accent) / 2 + 22,
                Color.green(accent) / 2 + 22, Color.blue(accent) / 2 + 24));
        shell.setBackground(bg);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout crest = new FrameLayout(this);
        GradientDrawable crestBg = new GradientDrawable();
        crestBg.setShape(GradientDrawable.OVAL);
        crestBg.setColor(Color.rgb(Color.red(accent) / 5 + 22,
                Color.green(accent) / 5 + 22, Color.blue(accent) / 5 + 25));
        crestBg.setStroke(dp(1), accent);
        crest.setBackground(crestBg);
        TextView symbol = text(confirm ? "?" : success ? "✓" : error ? "!" : "✦", 25, accent, true);
        symbol.setGravity(Gravity.CENTER);
        crest.addView(symbol, new FrameLayout.LayoutParams(-1, -1));
        head.addView(crest, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        TextView label = text(eyebrow == null ? "CARDUNIVERSE" : eyebrow.toUpperCase(java.util.Locale.ROOT),
                7, accent, true);
        label.setLetterSpacing(0.17f);
        copy.addView(label);
        TextView heading = text(title == null ? "REALM MESSAGE" : title, 17, TEXT, true);
        heading.setMaxLines(2);
        heading.setPadding(0, dp(3), 0, 0);
        copy.addView(heading);
        head.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        shell.addView(head);

        View rule = new View(this);
        rule.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{accent, Color.argb(35, Color.red(accent), Color.green(accent),
                        Color.blue(accent)), Color.TRANSPARENT}));
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(-1, dp(2));
        ruleParams.topMargin = dp(14);
        shell.addView(rule, ruleParams);

        ScrollView scroll = new ScrollView(this);
        TextView body = text(message == null || message.trim().isEmpty()
                ? "No additional details were provided." : message, 10, Color.rgb(213, 222, 237), false);
        body.setLineSpacing(dp(4), 1.04f);
        body.setPadding(dp(2), dp(13), dp(2), dp(15));
        scroll.addView(body);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (confirm) {
            TextView cancel = button("CANCEL", false);
            cancel.setOnClickListener(v -> dialog.dismiss());
            actions.addView(cancel, new LinearLayout.LayoutParams(dp(108), dp(40)));
            actions.addView(new View(this), new LinearLayout.LayoutParams(dp(8), 1));
        }
        TextView proceed = button(confirm ? "CONFIRM" : error ? "CLOSE" : "CONTINUE", true);
        proceed.setOnClickListener(v -> {
            dialog.dismiss();
            if (action != null) action.run();
        });
        actions.addView(proceed, new LinearLayout.LayoutParams(dp(124), dp(40)));
        shell.addView(actions);
        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(!confirm);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(dp(560), getResources().getDisplayMetrics().widthPixels - dp(42)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setWindowAnimations(android.R.style.Animation_Dialog);
        }
    }

    private void showPackRevealDialog(JSONArray cards) {
        if (cards == null || cards.length() == 0) {
            showResultDialog("PACK OPENED", "Your collection has been updated.");
            return;
        }
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(14), dp(14), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(36, 35, 75), Color.rgb(7, 12, 25)});
        bg.setCornerRadius(dp(17));
        bg.setStroke(dp(1), Color.rgb(133, 111, 228));
        shell.setBackground(bg);

        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout seal = createSummonSeal(GOLD_BRIGHT, "✦", 76);
        hero.addView(seal, new LinearLayout.LayoutParams(dp(76), dp(76)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(text("ASTRAL GATE · COMPLETE", 7, GOLD_BRIGHT, true));
        copy.addView(text("Summon revealed", 20, TEXT, true));
        copy.addView(text(cards.length() + " new card" + (cards.length() == 1 ? "" : "s")
                + " added to your archive", 8, MUTED, false));
        hero.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        shell.addView(hero);

        ScrollView scroll = new ScrollView(this);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(10), 0, dp(8));
        scroll.addView(grid);
        int columns = cards.length() >= 4 ? 4 : cards.length() >= 2 ? 2 : 1;
        LinearLayout row = null;
        int visible = 0;
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.optJSONObject(i);
            if (card == null) continue;
            if (visible % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout tile = createSummonRevealTile(card, visible + 1);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(dp(4), dp(4), dp(4), dp(7));
            row.addView(tile, p);
            visible++;
        }
        if (row != null && visible % columns != 0)
            for (int i = visible % columns; i < columns; i++)
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView done = button("RETURN TO SUMMON", true);
        done.setOnClickListener(v -> {
            dialog.dismiss();
            refreshAccount(() -> renderTab("summon"));
        });
        shell.addView(done, new LinearLayout.LayoutParams(-1, dp(42)));
        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(d -> refreshAccount(null));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(dp(850), getResources().getDisplayMetrics().widthPixels - dp(42)),
                    Math.min(getResources().getDisplayMetrics().heightPixels - dp(48), dp(560)));
            window.setWindowAnimations(android.R.style.Animation_Dialog);
        }
    }

    private LinearLayout createSummonRevealTile(JSONObject card, int order) {
        String rarity = card.optString("rarity", "COMMON").toUpperCase(java.util.Locale.ROOT);
        String element = card.optString("element", "AETHER").toUpperCase(java.util.Locale.ROOT);
        int accent = cardRarityColor(rarity);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(5), dp(5), dp(5), dp(7));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(31, 42, 62), Color.rgb(9, 15, 27)});
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), accent);
        tile.setBackground(bg);
        FrameLayout art = new FrameLayout(this);
        art.setBackgroundColor(Color.rgb(7, 12, 22));
        CardArtView image = new CardArtView(this);
        image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        image.loadArtwork(cardArtworkPath(card));
        art.addView(image, new FrameLayout.LayoutParams(-1, -1));
        TextView badge = text(rarity, 7, Color.WHITE, true);
        badge.setPadding(dp(6), dp(4), dp(6), dp(4));
        badge.setBackground(gradient(accent, Color.rgb(Color.red(accent) / 2,
                Color.green(accent) / 2, Color.blue(accent) / 2), accent, dp(6)));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        bp.setMargins(dp(5), dp(5), 0, 0);
        art.addView(badge, bp);
        tile.addView(art, new LinearLayout.LayoutParams(-1, dp(186)));
        tile.addView(text("NEW DISCOVERY · " + String.format(java.util.Locale.ROOT, "%02d", order),
                6, GOLD_BRIGHT, true));
        TextView title = text(card.optString("name", "Unknown Card"), 10, TEXT, true);
        title.setMaxLines(2);
        tile.addView(title);
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

    private void showPlayerProfile() {
        if (me == null) return;
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(20), dp(18), dp(20), dp(17));
        shell.setBackground(gradient(Color.rgb(29, 40, 65), Color.rgb(8, 13, 26),
                Color.rgb(155, 128, 219), dp(20)));

        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout crest = new FrameLayout(this);
        crest.setBackground(gradient(Color.rgb(246, 217, 151), Color.rgb(157, 103, 52),
                GOLD_BRIGHT, dp(40)));
        TextView initials = text(initials(me.optString("display_name", "CU")), 24, BG, true);
        initials.setGravity(Gravity.CENTER);
        crest.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        identity.addView(crest, new LinearLayout.LayoutParams(dp(68), dp(68)));

        LinearLayout identityCopy = new LinearLayout(this);
        identityCopy.setOrientation(LinearLayout.VERTICAL);
        identityCopy.setPadding(dp(13), 0, 0, 0);
        TextView kicker = text(isGodModeEnabled() ? "MASTER COLLECTOR · GOD MODE" : "CARDUNIVERSE COLLECTOR",
                7, isGodModeEnabled() ? Color.rgb(207, 187, 255) : GOLD, true);
        kicker.setLetterSpacing(0.12f);
        identityCopy.addView(kicker);
        TextView playerName = text(me.optString("display_name", "Collector"), 20, TEXT, true);
        playerName.setMaxLines(1);
        identityCopy.addView(playerName);
        TextView email = text(me.optString("email", "Account email unavailable"), 9, MUTED, false);
        email.setMaxLines(1);
        identityCopy.addView(email);
        identity.addView(identityCopy, new LinearLayout.LayoutParams(0, -2, 1f));
        shell.addView(identity);

        View divider = new View(this);
        divider.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{GOLD, Color.argb(35, Color.red(GOLD), Color.green(GOLD), Color.blue(GOLD)), Color.TRANSPARENT}));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(2));
        dividerParams.topMargin = dp(16);
        dividerParams.bottomMargin = dp(12);
        shell.addView(divider, dividerParams);

        JSONObject wallet = me.optJSONObject("wallet");
        JSONObject collection = me.optJSONObject("collection");
        if (wallet == null) wallet = new JSONObject();
        if (collection == null) collection = new JSONObject();

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(profileStat("LEVEL", formatNumber(me.optLong("collector_level", 1)), CYAN),
                new LinearLayout.LayoutParams(0, dp(67), 1f));
        stats.addView(profileStat("CARDS", formatNumber(collection.optLong("unique_cards")), GOLD_BRIGHT),
                new LinearLayout.LayoutParams(0, dp(67), 1f));
        stats.addView(profileStat("GOLD", formatNumber(wallet.optLong("coins")), Color.rgb(255, 208, 105)),
                new LinearLayout.LayoutParams(0, dp(67), 1f));
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-1, dp(67));
        statsParams.bottomMargin = dp(13);
        shell.addView(stats, statsParams);

        TextView role = text((isAdminAccount() ? "ADMINISTRATOR ACCESS" : "PLAYER ACCOUNT")
                + "    ·    " + me.optString("role", "collector").toUpperCase(java.util.Locale.ROOT),
                8, isGodModeEnabled() ? Color.rgb(205, 188, 255) : MUTED, true);
        role.setGravity(Gravity.CENTER);
        role.setPadding(dp(9), dp(9), dp(9), dp(9));
        role.setBackground(gradient(Color.rgb(24, 29, 51), Color.rgb(15, 19, 35),
                Color.rgb(62, 70, 103), dp(9)));
        shell.addView(role);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(14), 0, 0);
        TextView edit = button("EDIT PROFILE", true);
        edit.setOnClickListener(v -> {
            dialog.dismiss();
            showProfileEditor();
        });
        actions.addView(edit, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView settings = button("ACCOUNT SETTINGS", false);
        settings.setOnClickListener(v -> {
            dialog.dismiss();
            renderTab("settings");
        });
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        settingsParams.leftMargin = dp(8);
        actions.addView(settings, settingsParams);
        shell.addView(actions);

        TextView close = text("CLOSE PROFILE", 8, MUTED, true);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, dp(13), 0, 0);
        close.setOnClickListener(v -> dialog.dismiss());
        shell.addView(close);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(dp(520), getResources().getDisplayMetrics().widthPixels - dp(36)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setWindowAnimations(android.R.style.Animation_Dialog);
        }
    }

    private LinearLayout profileStat(String label, String value, int accent) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackground(gradient(Color.rgb(31, 42, 65), Color.rgb(13, 20, 35), accent, dp(10)));
        panel.addView(text(label, 7, accent, true));
        TextView amount = text(value, 17, TEXT, true);
        amount.setPadding(0, dp(3), 0, 0);
        panel.addView(amount);
        return panel;
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
                            refreshAccount(() -> {
                                if ("settings".equals(activeTab)) renderTab("settings");
                                else showPlayerProfile();
                            });
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
            case "chat": return "/api/chat?channel=" + activeChatChannel;
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
        card.addView(text(kicker, 7, CYAN, true));
        TextView valueView = text(value, 19, TEXT, true);
        valueView.setPadding(0, dp(4), 0, dp(3));
        card.addView(valueView);
        card.addView(text(caption, 7, MUTED, false));
        return card;
    }

    private LinearLayout featureCard(String icon, String title, String description, Runnable action) {
        LinearLayout card = sectionPanel();
        card.setMinimumHeight(dp(104));
        card.addView(text(icon, 21, CYAN, true));
        addGap(card, 2);
        card.addView(text(title, 11, TEXT, true));
        TextView detail = text(description, 8, MUTED, false);
        detail.setMaxLines(2);
        card.addView(detail);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            if (sfxEnabled && gameAudio != null) gameAudio.playClick();
            action.run();
        });
        return card;
    }

    private LinearLayout dataRow(String title, String subtitle, String trailing) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(24, 34, 53), Color.rgb(16, 24, 40)});
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.rgb(43, 57, 79));
        row.setBackground(bg);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(0, 0, dp(8), 0);
        TextView name = text(title, 10, TEXT, true);
        name.setMaxLines(2);
        copy.addView(name);
        TextView details = text(subtitle, 8, MUTED, false);
        details.setMaxLines(3);
        details.setLineSpacing(dp(1), 1.02f);
        copy.addView(details);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (trailing != null && !trailing.isEmpty()) {
            TextView badge = text(trailing, 7, GOLD_BRIGHT, true);
            badge.setGravity(Gravity.CENTER);
            badge.setMaxWidth(dp(118));
            badge.setPadding(dp(8), dp(6), dp(8), dp(6));
            GradientDrawable chip = new GradientDrawable();
            chip.setColor(Color.rgb(42, 39, 52));
            chip.setCornerRadius(dp(7));
            chip.setStroke(dp(1), Color.rgb(94, 78, 65));
            badge.setBackground(chip);
            row.addView(badge);
        }
        row.setMinimumHeight(dp(48));
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
        panel.setPadding(dp(13), dp(11), dp(13), dp(11));
        GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(25, 34, 53), Color.rgb(15, 22, 37)});
        surface.setCornerRadius(dp(13));
        surface.setStroke(dp(1), Color.rgb(46, 59, 82));
        panel.setBackground(surface);
        panel.setClipToPadding(false);
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
        int fill = primary ? Color.rgb(213, 169, 91) : Color.rgb(26, 36, 56);
        int stroke = primary ? Color.rgb(246, 208, 139) : Color.rgb(67, 87, 117);
        int ink = primary ? Color.rgb(28, 23, 19) : Color.rgb(228, 235, 248);
        TextView control = text(label, 9, ink, true);
        control.setGravity(Gravity.CENTER);
        control.setLetterSpacing(0.055f);
        control.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(8));
        shape.setStroke(dp(1), stroke);
        control.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(50, 255, 255, 255)),
                shape, null));
        control.setMinimumHeight(dp(40));
        control.setClickable(true);
        control.setFocusable(true);
        control.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (sfxEnabled && gameAudio != null) gameAudio.playClick();
                if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            return false;
        });
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
        if (bgmEnabled && gameAudio != null) gameAudio.startAmbient();
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(autoSyncTick);
        if (gameAudio != null) gameAudio.stopAmbient();
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
        if (gameAudio != null) gameAudio.release();
        super.onDestroy();
    }
}
