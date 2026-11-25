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
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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
    private boolean autoSyncEnabled = true;
    private boolean reduceMotionEnabled = false;
    private boolean purchaseConfirmationsEnabled = true;
    private int chatRefreshSeconds = 5;
    private static final int REQUEST_PROFILE_AVATAR = 7302;
    private TextView pendingProfileAvatar;
    private Runnable profileAvatarUploaded;
    private boolean missionsDailyLoading = false;
    private final Map<String, TextView> navLabels = new LinkedHashMap<>();
    private JSONObject arenaLeaderCard;
    private JSONObject arenaOpponentCard;
    private JSONObject adminProfile;
    private JSONObject me;
    private JSONObject progression;
    private String activeTab = "home";
    private int activePageRequest;
    private int summonHistoryOffset;
    private String historyMode = "ARENA";
    private int historyOffset;
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
            if (!isFinishing() && api != null && me != null && appRoot != null && autoSyncEnabled) {
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
        autoSyncEnabled = gamePrefs.getBoolean("auto_sync_enabled", true);
        reduceMotionEnabled = gamePrefs.getBoolean("reduce_motion_enabled", false);
        purchaseConfirmationsEnabled = gamePrefs.getBoolean("purchase_confirmations_enabled", true);
        chatRefreshSeconds = Math.max(3, Math.min(15, gamePrefs.getInt("chat_refresh_seconds", 5)));
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
                me.put("daily_claimed_today", payload.optBoolean("daily_claimed_today",
                        me.optBoolean("daily_claimed_today", false)));
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
        EditText usernameField = editField("Username (3–20 letters, numbers, _)", false);
        usernameField.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        usernameField.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});
        EditText emailField = editField("Email address", false);
        emailField.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText passwordField = editField("Password", true);
        passwordField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (!register) { nameField.setVisibility(View.GONE); usernameField.setVisibility(View.GONE); }

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
        form.addView(usernameField, fieldParams());
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
            String username = usernameField.getText().toString().trim().replaceFirst("^@", "")
                    .toLowerCase(java.util.Locale.ROOT);
            if (email.isEmpty() || password.isEmpty() || (register && displayName.length() < 2)) {
                errorText.setText(register ? "Enter your display name, username, email and password." : "Enter your email and password.");
                return;
            }
            if (register && !username.matches("[a-z][a-z0-9_]{2,19}")) {
                usernameField.setError("Use 3–20 characters, start with a letter, and use letters, numbers or underscores.");
                errorText.setText("Choose a valid unique username before creating your account.");
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
                if (register) { payload.put("displayName", displayName); payload.put("username", username); }
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
                {"encyclopedia", "▤", "ENCYCLOPEDIA"},
                {"binders", "▤", "BINDERS"},
                {"formation", "♟", "TEAM"},
                {"evolution", "✧", "EVOLVE"},
                {"achievements", "✦", "RECORDS"},
                {"progression", "⌁", "REWARDS"},
                {"journey", "✦", "JOURNEY"},
                {"events", "✺", "EVENTS"},
                {"leaderboards", "▥", "RANKINGS"},
                {"ranked", "♛", "RANKED"},
                {"summon", "✧", "SUMMON"},
                {"adventure", "⚔", "ADVENTURE"},
                {"dungeons", "⌘", "DUNGEON"},
                {"raids", "♜", "RAIDS"},
                {"arena", "◈", "ARENA"},
                {"training", "⚔", "TRAINING"},
                {"battle_history", "☷", "BATTLE HISTORY"},
                {"trades", "⇄", "TRADES"},
                {"friends", "♧", "FRIENDS"},
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
        right.setPadding(dp(6),dp(4),dp(8),dp(4));
        right.setBackground(gradient(Color.rgb(33,39,65),Color.rgb(12,19,34),Color.rgb(151,120,79),dp(13)));
        right.setMinimumHeight(dp(48));
        right.setContentDescription("Open collector profile");
        right.setFocusable(true);
        right.setOnClickListener(v -> showPlayerProfile());
        String collectorName=me.optString("display_name","Collector");
        FrameLayout avatarRing=phase11Avatar(collectorName,me.optString("avatarUrl",me.optString("avatar_url","")),GOLD_BRIGHT);
        right.addView(avatarRing,new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout account=new LinearLayout(this);account.setOrientation(LinearLayout.VERTICAL);account.setGravity(Gravity.CENTER_VERTICAL);
        account.setPadding(dp(8),0,dp(5),0);
        accountLabel=text(collectorName,11,TEXT,true);accountLabel.setSingleLine(true);accountLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        accountLabel.setMaxWidth(dp(compact?116:180));account.addView(accountLabel);
        currencyLabel=text("LV "+formatNumber(me.optLong("collector_level",1))+"  ·  "+walletLine(),8,GOLD_BRIGHT,true);
        currencyLabel.setSingleLine(true);currencyLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);currencyLabel.setMaxWidth(dp(compact?125:210));account.addView(currencyLabel);
        right.addView(account,new LinearLayout.LayoutParams(-2,-2));
        TextView open=text("›",20,GOLD_BRIGHT,true);open.setGravity(Gravity.CENTER);
        right.addView(open,new LinearLayout.LayoutParams(dp(15),-1));
        top.addView(right,new LinearLayout.LayoutParams(-2,dp(48)));
        mainColumn.addView(top,new LinearLayout.LayoutParams(-1,dp(54)));

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
        addRealmScene(phase17SceneMode(tab), screenHeading(tab).toUpperCase(java.util.Locale.ROOT),
                screenDescription(tab), 0, 0);
    }

    private String phase17SceneMode(String tab) {
        switch (tab) {
            case "summon": case "summon_history": case "evolution": return "summon";
            case "adventure": case "missions": case "journey": return "adventure";
            case "dungeons": case "tower": return "dungeon";
            case "raids": case "world_events": case "events": return "raid";
            case "arena": case "ranked": case "training": case "battle_history": return "arena";
            case "collection": case "encyclopedia": case "mastery": case "formation": return "archive";
            case "binders": case "binder_completion": return "binder";
            case "achievements": case "showcase": case "progression": case "leaderboards": return "records";
            case "shop": case "wallet_history": return "market";
            case "chat": case "trades": return "trade";
            case "guild": case "guild_coop": return "guild";
            default: return "home";
        }
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

    private long chatRefreshIntervalMs() {
        return Math.max(3, Math.min(15, chatRefreshSeconds)) * 1000L;
    }

    private void confirmPurchaseAction(String title, String message, Runnable action) {
        if (purchaseConfirmationsEnabled) confirmAction(title, message, action);
        else action.run();
    }

    private void addAudioSetting(LinearLayout parent, String title, String description, int kind) {
        boolean enabled = kind == 0 ? bgmEnabled : kind == 1 ? sfxEnabled
                : kind == 2 ? hapticsEnabled : kind == 3 ? autoSyncEnabled
                : kind == 4 ? reduceMotionEnabled : purchaseConfirmationsEnabled;
        LinearLayout row = dataRow(title, description, "");
        TextView toggle = button(enabled ? "ON" : "OFF", enabled);
        toggle.setOnClickListener(v -> {
            boolean next = !(kind == 0 ? bgmEnabled : kind == 1 ? sfxEnabled
                    : kind == 2 ? hapticsEnabled : kind == 3 ? autoSyncEnabled
                    : kind == 4 ? reduceMotionEnabled : purchaseConfirmationsEnabled);
            if (kind == 0) {
                bgmEnabled = next;
                gamePrefs.edit().putBoolean("bgm_enabled", next).apply();
                if (gameAudio != null) { if (next) gameAudio.startAmbient(); else gameAudio.stopAmbient(); }
            } else if (kind == 1) {
                sfxEnabled = next;
                gamePrefs.edit().putBoolean("sfx_enabled", next).apply();
                if (next && gameAudio != null) gameAudio.playSuccess();
            } else if (kind == 2) {
                hapticsEnabled = next;
                gamePrefs.edit().putBoolean("haptics_enabled", next).apply();
            } else if (kind == 3) {
                autoSyncEnabled = next;
                gamePrefs.edit().putBoolean("auto_sync_enabled", next).apply();
                if (next) startAutoSync(); else ui.removeCallbacks(autoSyncTick);
            } else if (kind == 4) {
                reduceMotionEnabled = next;
                gamePrefs.edit().putBoolean("reduce_motion_enabled", next).apply();
            } else {
                purchaseConfirmationsEnabled = next;
                gamePrefs.edit().putBoolean("purchase_confirmations_enabled", next).apply();
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
                || "raids".equals(tab) || "trades".equals(tab)
                || "chat".equals(tab) || "shop".equals(tab) || "missions".equals(tab)
                || "progression".equals(tab);
    }

    private void addRealmScene(String mode, String title, String subtitle, int progress, int total) {
        RealmSceneView scene = new RealmSceneView(this, mode, title, subtitle, progress, total);
        scene.setMotionEnabled(!reduceMotionEnabled);
        int availableWidth = content == null ? 0 : content.getWidth();
        if (availableWidth <= dp(160)) {
            availableWidth = getResources().getDisplayMetrics().widthPixels
                    - dp(compactLayout ? 22 : 120);
        }
        int sceneHeight = Math.max(dp(96), Math.min(dp(260),
                Math.round(availableWidth * 300f / 960f)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, sceneHeight);
        params.bottomMargin = dp(9);
        content.addView(scene, params);
    }

    private void renderTab(String tab) {
        ui.removeCallbacks(chatRefreshTick);
        activeTab = tab;
        int request = ++activePageRequest;
        refreshNav();
        String title = tab.toUpperCase().replace("_", " ");
        if (pageTitle != null) pageTitle.setText(title.equals("HOME") ? "REALM OVERVIEW" : title);
        if (content == null) return;
        content.removeAllViews();
        if ("home".equals(tab)) {
            renderHome();
            return;
        }
        if ("friends".equals(tab)) {
            renderFriendsScreen();
            return;
        }
        if (!usesRealmScene(tab)) addScreenHero(tab);
        String requestedMode = historyMode;
        int requestedOffset = historyOffset;
        if ("battle_history".equals(tab) && "PRACTICE".equals(requestedMode)) {
            renderBattleHistory(null, requestedMode, 0);
            return;
        }
        renderLoading();
        String path = endpointFor(tab);
        if (path == null) {
            showNotice("This section is not available for this account.");
            return;
        }
        api.get(path, result -> ui.post(() -> {
            if (!tab.equals(activeTab) || request != activePageRequest || content == null) return;
            content.removeAllViews();
            if (!usesRealmScene(tab)) addScreenHero(tab);
            if ("battle_history".equals(tab)) {
                renderBattleHistory(result.ok() ? result.data : new JSONObject(), requestedMode, requestedOffset);
                if (!result.ok()) showNotice(result.error);
                return;
            }
            if ("training".equals(tab)) {
                renderTrainingGrounds(result.ok() ? result.data : null);
                if (!result.ok()) showNotice("Owned cards unavailable. Training loans are ready.");
                return;
            }
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
            } else if ("formation".equals(tab)) {
                api.get("/api/decks", deckResult -> ui.post(() -> {
                    if (!"formation".equals(activeTab) || content == null) return;
                    if (deckResult.ok()) {
                        try { result.data.put("decks", deckResult.data.optJSONArray("decks")); }
                        catch (Exception ignored) {}
                    } else {
                        showResultDialog("TEAM DECKS UNAVAILABLE", deckResult.error);
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

        boolean dailyClaimedToday = me.optBoolean("daily_claimed_today", false);
        int dailyAccent = dailyClaimedToday ? Color.rgb(105, 116, 137) : GOLD_BRIGHT;
        LinearLayout dailyReward = new LinearLayout(this);
        dailyReward.setOrientation(LinearLayout.HORIZONTAL);
        dailyReward.setGravity(Gravity.CENTER_VERTICAL);
        dailyReward.setPadding(dp(13), dp(10), dp(13), dp(10));
        dailyReward.setBackground(gradient(
                dailyClaimedToday ? Color.rgb(39, 45, 59) : Color.rgb(66, 51, 29),
                dailyClaimedToday ? Color.rgb(17, 23, 35) : Color.rgb(24, 20, 15),
                dailyClaimedToday ? Color.rgb(75, 86, 106) : GOLD, dp(12)));
        FrameLayout gift = new FrameLayout(this);
        gift.setBackground(gradient(
                dailyClaimedToday ? Color.rgb(67, 77, 94) : Color.rgb(112, 80, 35),
                dailyClaimedToday ? Color.rgb(32, 40, 55) : Color.rgb(45, 31, 18),
                dailyClaimedToday ? Color.rgb(111, 125, 146) : GOLD_BRIGHT, dp(11)));
        TextView giftIcon = text("🎁", 24, dailyClaimedToday ? Color.rgb(181, 190, 204) : TEXT, true);
        giftIcon.setGravity(Gravity.CENTER);
        gift.addView(giftIcon, new FrameLayout.LayoutParams(-1, -1));
        dailyReward.addView(gift, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout dailyCopy = new LinearLayout(this);
        dailyCopy.setOrientation(LinearLayout.VERTICAL);
        dailyCopy.setPadding(dp(11), 0, 0, 0);
        dailyCopy.addView(text("DAILY LOGIN REWARD", 8, dailyAccent, true));
        dailyCopy.addView(text(dailyClaimedToday
                ? "Today's Starter Pack and 20 Gold have been collected."
                : "Claim your free Starter Pack and 20 Gold.", 8,
                dailyClaimedToday ? Color.rgb(147, 158, 177) : MUTED, false));
        dailyReward.addView(dailyCopy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView dailyClaim = button(dailyClaimedToday ? "CLAIMED" : "AVAILABLE", !dailyClaimedToday);
        dailyClaim.setEnabled(!dailyClaimedToday);
        dailyClaim.setTextColor(dailyClaimedToday ? Color.rgb(171, 181, 197) : BG);
        dailyClaim.setBackground(gradient(
                dailyClaimedToday ? Color.rgb(69, 79, 97) : Color.rgb(246, 202, 116),
                dailyClaimedToday ? Color.rgb(47, 56, 73) : Color.rgb(180, 129, 52),
                dailyClaimedToday ? Color.rgb(92, 104, 124) : GOLD_BRIGHT, dp(9)));
        if (!dailyClaimedToday) {
            dailyClaim.setOnClickListener(v -> confirmAction("Claim daily reward?",
                    "You will receive one Starter Pack and 20 Gold if today's reward is still available.", () -> {
                dailyClaim.setEnabled(false);
                api.post("/api/daily/claim", new JSONObject(), result -> ui.post(() -> {
                    dailyClaim.setEnabled(true);
                    if (!result.ok()) {
                        showResultDialog("Daily reward unavailable", result.error);
                        refreshAccount(() -> renderTab("home"));
                        return;
                    }
                    showResultDialog("✦ DAILY REWARD CLAIMED", result.data.optString("message",
                            "One Starter Pack and 20 Gold have been added."));
                    refreshAccount(() -> renderTab("home"));
                }));
            }));
        }
        dailyReward.addView(dailyClaim, new LinearLayout.LayoutParams(dp(88), dp(40)));
        LinearLayout.LayoutParams dailyParams = rowParams();
        dailyParams.bottomMargin = dp(14);
        content.addView(dailyReward, dailyParams);

        addPhase13Link("New Player Journey", "Track your first milestones across the realm.",
                () -> renderTab("journey"));
        TextView portalsTitle = text("CHOOSE YOUR NEXT MODE", 8, GOLD, true);
        portalsTitle.setLetterSpacing(0.13f);
        content.addView(portalsTitle);
        addGap(content, 7);

        HorizontalScrollView portalsScroll = new HorizontalScrollView(this);
        portalsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout portals = new LinearLayout(this);
        portals.setOrientation(LinearLayout.HORIZONTAL);
        portals.setPadding(dp(1), dp(2), dp(1), dp(8));
        portalsScroll.addView(portals);
        addHomePortal(portals, "adventure", "⚔", "ADVENTURE", "Explore the Firstlight frontier", "FIGHT", GOLD_BRIGHT);
        addHomePortal(portals, "training", "◈", "TRAINING GROUNDS", "Practise without affecting progression", "TRAIN", VIOLET);
        addHomePortal(portals, "dungeons", "⌘", "DUNGEON", "Descend into the deep vault", "ENTER", VIOLET);
        addHomePortal(portals, "raids", "♜", "WORLD RAID", "Challenge the realm guardian", "ATTACK", RED);
        addHomePortal(portals, "summon", "✧", "SUMMON", "Open a rare card seal", "SUMMON", CYAN);
        addHomePortal(portals, "collection", "▧", "CARD ARCHIVE", "Browse your discovered cards", "OPEN", BLUE);
        addHomePortal(portals, "binders", "▤", "BINDER VAULT", "Curate your favourite collection", "OPEN", Color.rgb(116, 206, 192));
        addHomePortal(portals, "tower", "♜", "WEEKLY TOWER", "Twenty floors and rotating challenges", "CLIMB", GOLD_BRIGHT);
        content.addView(portalsScroll, rowParams());
        content.addView(featureCard("♜", "Weekly Challenge Tower", "20 floors · weekly modifiers · exclusive rewards", () -> renderTab("tower")), rowParams());
        content.addView(featureCard("✺", "World Events", "Join the realm-wide weekly challenge", () -> renderTab("world_events")), rowParams());

        LinearLayout extras = new LinearLayout(this);
        extras.setOrientation(LinearLayout.HORIZONTAL);
        extras.addView(featureCard("♛", "Ranked", "Season ladder and standings", () -> renderTab("ranked")),
                new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams guildParams = new LinearLayout.LayoutParams(0, -2, 1f);
        guildParams.leftMargin = dp(8);
        extras.addView(featureCard("♜", "Guild Hall", "Your realm companions", () -> renderTab("guild")), guildParams);
        content.addView(extras);
        content.addView(featureCard("♧", "Friends", "Add collectors by username", () -> renderTab("friends")), rowParams());
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



    private void addHomePortal(LinearLayout portals, String tab, String glyph, String label,
                               String description, String action, int accent) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(12), dp(10), dp(12), dp(10));
        tile.setBackground(gradient(Color.rgb(33, 42, 65), Color.rgb(11, 17, 31), accent, dp(12)));
        tile.addView(text(glyph, 23, accent, true));
        TextView title = text(label, 9, TEXT, true);
        title.setPadding(0, dp(5), 0, dp(2));
        tile.addView(title);
        TextView detail = text(description, 7, MUTED, false);
        detail.setMaxLines(2);
        tile.addView(detail);
        TextView enter = text("›  " + action, 8, accent, true);
        enter.setPadding(0, dp(8), 0, 0);
        tile.addView(enter);
        tile.setFocusable(true);
        tile.setOnClickListener(v -> renderTab(tab));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(185), dp(126));
        params.rightMargin = dp(8);
        portals.addView(tile, params);
    }

    private void renderModule(String tab, JSONObject data) {
        switch (tab) {
            case "mastery": renderPhase16Mastery(data); break;
            case "world_events": renderPhase16World(data); break;
            case "tower": renderPhase15Tower(data); break;
            case "showcase": renderPhase15Showcase(data); break;
            case "wallet_history": renderPhase15Wallet(data); break;
            case "encyclopedia": renderCardEncyclopedia(data); break;
            case "summon_history": renderSummonHistory(data); break;
            case "binder_completion": renderBinderCompletion(data); break;
            case "guild_coop": renderGuildCooperative(data); break;
            case "journey": renderNewPlayerJourney(data); break;
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

    private void showSummonTransparency(String slug) {
        int page = activePageRequest;
        api.get("/api/summon/transparency?pack=" + Uri.encode(slug), result -> ui.post(() -> {
            if (page != activePageRequest) return;
            if (!result.ok()) {
                showResultDialog("Banner details unavailable", result.error);
                return;
            }
            JSONObject banner = result.data.optJSONObject("banner");
            if (banner == null) return;
            android.app.Dialog dialog = new android.app.Dialog(this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout shell = phase11Sheet("SUMMON TRANSPARENCY", banner.optString("name"),
                    "Rates for each slot in your next opening", VIOLET);
            ScrollView scroll = new ScrollView(this);
            LinearLayout body = sectionPanel();
            scroll.addView(body);
            shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

            body.addView(text(banner.optString("description"), 12, TEXT, false), rowParams());
            String bannerExpiry = banner.isNull("ends_at") ? "Permanent banner"
                    : "Ends " + phase14Timestamp(banner.optString("ends_at"));
            body.addView(dataRow(bannerExpiry, banner.optInt("cards_per_pack") + " cards · "
                    + humanize(banner.optString("ownership_type")), banner.optLong("price") > 0
                    ? formatNumber(banner.optLong("price")) + " " + banner.optString("currency") : ""), rowParams());
            JSONObject pity = result.data.optJSONObject("pity");
            if (pity != null) {
                int threshold = pity.optInt("threshold");
                int count = pity.optInt("count");
                String next = pity.optBoolean("guaranteed_next") ? "Next pack guarantees a Mythic in slot 1"
                        : "Guaranteed within " + Math.max(0, threshold - count) + " packs · resets on Mythic";
                body.addView(dataRow("Mythic pity", next, count + " / " + threshold), rowParams());
            } else {
                body.addView(text("This banner has no pity counter.", 11, MUTED, false), rowParams());
            }
            JSONArray guarantees = result.data.optJSONArray("guarantees");
            if (guarantees != null && guarantees.length() != 0) {
                for (int i = 0; i < guarantees.length(); i++) {
                    JSONObject guarantee = guarantees.optJSONObject(i);
                    if (guarantee != null) body.addView(dataRow("Next summon guarantee",
                            "Slot " + guarantee.optInt("slot"), guarantee.optString("rarity")), rowParams());
                }
            } else {
                body.addView(text("No guaranteed rarity on your next summon.", 11, MUTED, false), rowParams());
            }

            JSONArray slots = result.data.optJSONArray("slots");
            java.text.DecimalFormat rateFormat = new java.text.DecimalFormat("0.######",
                    java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
            if (slots != null) for (int i = 0; i < slots.length(); i++) {
                JSONObject slot = slots.optJSONObject(i);
                if (slot == null) continue;
                LinearLayout rates = sectionPanel();
                rates.addView(text("SLOT " + slot.optInt("slot"), 11, GOLD_BRIGHT, true));
                JSONArray odds = slot.optJSONArray("rates");
                if (odds == null) rates.addView(text("Odds unavailable for this slot.", 12, MUTED, false));
                if (odds != null) for (int j = 0; j < odds.length(); j++) {
                    JSONObject row = odds.optJSONObject(j);
                    if (row == null) continue;
                    double percent = row.optDouble("percent", 0);
                    String formatted = percent > 0 && percent < 0.000001 ? "<0.000001" : rateFormat.format(percent);
                    rates.addView(dataRow(humanize(row.optString("rarity")), "", formatted + "%"), rowParams());
                }
                body.addView(rates, rowParams());
            }
            JSONArray offers = result.data.optJSONArray("offers");
            if (offers != null && offers.length() > 0) {
                body.addView(text("LIMITED EVENT OFFERS", 11, VIOLET, true), rowParams());
                for (int i = 0; i < offers.length(); i++) {
                    JSONObject offer = offers.optJSONObject(i);
                    if (offer == null) continue;
                    body.addView(dataRow(offer.optString("title"), "Ends " + phase14Timestamp(offer.optString("ends_at")),
                            "EVENT SHOP"), rowParams());
                    String detail = offer.optString("description").trim();
                    if (!detail.isEmpty()) body.addView(text(detail, 11, MUTED, false), rowParams());
                }
            }
            addPhase13Close(shell, dialog);
            phase11ShowSheet(dialog, shell, 670);
        }));
    }

    private String phase14Timestamp(String value) {
        if (value.isEmpty()) return "";
        java.text.SimpleDateFormat utc = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT);
        utc.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        java.text.SimpleDateFormat local = new java.text.SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", java.util.Locale.ROOT);
        local.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Jakarta"));
        try { return local.format(utc.parse(value)); }
        catch (java.text.ParseException ignored) { return value; }
    }

    private void openSummonHistory(int offset) {
        summonHistoryOffset = offset;
        renderTab("summon_history");
    }

    private void renderSummonHistory(JSONObject data) {
        addPhase13Link("Summon Hall", "Return to available banners.", () -> renderTab("summon"));
        JSONArray entries = data.optJSONArray("entries");
        if (entries == null || entries.length() == 0) showNotice("No summon openings recorded yet.");
        if (entries != null) for (int i = 0; i < entries.length(); i++) {
            JSONObject opening = entries.optJSONObject(i);
            if (opening == null) continue;
            LinearLayout record = sectionPanel();
            record.addView(text(opening.optString("pack_name", "Summon"), 15, TEXT, true));
            record.addView(text(phase14Timestamp(opening.optString("created_at")), 11, MUTED, false));
            JSONArray cards = opening.optJSONArray("cards");
            if (cards != null) for (int j = 0; j < cards.length(); j++) {
                JSONObject card = cards.optJSONObject(j);
                if (card == null) continue;
                record.addView(dataRow(card.optString("name", "Card"), card.optString("card_code"),
                        card.optString("rarity")), rowParams());
            }
            content.addView(record, rowParams());
        }
        final int offset = summonHistoryOffset;
        if (offset > 0) addPhase13Link("Newer summons", "Previous page", () -> openSummonHistory(offset - 20));
        if (data.optBoolean("has_more")) addPhase13Link("Older summons", "Next page", () -> openSummonHistory(offset + 20));
    }

    private void renderBinderCompletion(JSONObject data) {
        addPhase13Link("Binder Vault", "Return to your binders.", () -> renderTab("binders"));
        showNotice("Set completion counts each distinct card you own once, across all binders.");
        JSONArray sets = data.optJSONArray("sets");
        if (sets == null || sets.length() == 0) {
            showNotice("No active card sets yet.");
            return;
        }
        for (int i = 0; i < sets.length(); i++) {
            JSONObject set = sets.optJSONObject(i);
            if (set == null) continue;
            JSONArray rewards = set.optJSONArray("milestones");
            boolean crest = false;
            if (rewards != null) for (int j = 0; j < rewards.length(); j++) {
                JSONObject reward = rewards.optJSONObject(j);
                if (reward != null && reward.optInt("threshold") == 75 && reward.optBoolean("claimed")) crest = true;
            }
            LinearLayout panel = sectionPanel();
            if (crest) {
                panel.setBackground(gradient(Color.rgb(45, 38, 67), PANEL, GOLD_BRIGHT, dp(14)));
                panel.addView(text("✦ ARCHIVE CREST", 10, GOLD_BRIGHT, true));
            }
            panel.addView(text(set.optString("name", "Card set"), 17, TEXT, true));
            addPhase14Progress(panel, "Cards collected", set.optInt("collected"), set.optInt("total"));
            if (rewards != null) for (int j = 0; j < rewards.length(); j++) {
                JSONObject reward = rewards.optJSONObject(j);
                if (reward == null) continue;
                int threshold = reward.optInt("threshold");
                String label = reward.optString("label");
                LinearLayout entry = dataRow(threshold + "% complete", label,
                        reward.optBoolean("claimed") ? "CLAIMED" : reward.optBoolean("ready") ? "CLAIM ›" : "LOCKED");
                if (reward.optBoolean("ready") && !reward.optBoolean("claimed")) {
                    entry.setFocusable(true);
                    entry.setOnClickListener(v -> postPhase14("/api/binders/completion/claim",
                            payload("set", set.optString("slug"), "threshold", threshold), entry));
                }
                panel.addView(entry, rowParams());
            }
            content.addView(panel, rowParams());
        }
    }

    private void addPhase14Progress(LinearLayout panel, String label, int count, int total) {
        panel.addView(dataRow(label, "", formatNumber(count) + " / " + formatNumber(total)), rowParams());
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(Math.max(1, total));
        bar.setProgress(Math.max(0, Math.min(count, total)));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        panel.addView(bar, new LinearLayout.LayoutParams(-1, dp(8)));
        addGap(panel, 5);
    }

    private void renderGuildCooperative(JSONObject data) {
        addPhase13Link("Guild Headquarters", "Return to your guild.", () -> renderTab("guild"));
        JSONObject calendar = data.optJSONObject("calendar");
        JSONObject boss = data.optJSONObject("boss");
        if (boss == null || calendar == null) {
            showNotice("Guild activities are not available.");
            return;
        }
        boolean admin = isAdminAccount();
        LinearLayout lair = sectionPanel();
        lair.setBackground(gradient(Color.rgb(51, 35, 63), PANEL, PINK, dp(15)));
        lair.addView(text("GUILD BOSS", 10, PINK, true));
        lair.addView(text(boss.optString("name"), 17, TEXT, true));
        addPhase14Progress(lair, "Shared vitality", boss.optInt("remaining"), boss.optInt("hp"));
        lair.addView(text("One strike per day · Next boss on " + calendar.optString("nextWeek")
                + " at 00:00 UTC+8", 11, MUTED, false), rowParams());
        TextView strike = button(boss.optInt("remaining") == 0 ? "BOSS DEFEATED"
                : boss.optBoolean("struck") ? "DAILY STRIKE USED" : "STRIKE GUILD BOSS", true);
        strike.setEnabled(!admin && boss.optInt("remaining") > 0 && !boss.optBoolean("struck"));
        strike.setOnClickListener(v -> confirmAction("Strike the Guild Boss?",
                "The server uses your active Formation. This uses today's Guild strike.", () ->
                        postPhase14("/api/guilds/cooperative/strike", new JSONObject(), strike)));
        lair.addView(strike, rowParams());
        content.addView(lair, rowParams());

        LinearLayout missions = sectionPanel();
        missions.addView(text("WEEKLY GUILD MISSIONS", 14, GOLD_BRIGHT, true));
        missions.addView(text("Strike at least once this week to earn shared mission rewards.", 11, MUTED, false), rowParams());
        JSONArray missionRows = data.optJSONArray("missions");
        if (missionRows != null) for (int i = 0; i < missionRows.length(); i++) {
            JSONObject mission = missionRows.optJSONObject(i);
            if (mission == null) continue;
            addPhase14Progress(missions, mission.optString("label"), mission.optInt("progress"), mission.optInt("target"));
            addGuildRewardButton(missions, mission, mission.optString("key"), guildRewardLabel(mission), admin);
        }
        content.addView(missions, rowParams());

        LinearLayout ranking = sectionPanel();
        ranking.addView(text("GUILD CONTRIBUTION RANKING", 14, GOLD_BRIGHT, true));
        ranking.addView(text("Weekly boss damage · Gold contributed breaks ties", 11, MUTED, false), rowParams());
        JSONArray members = data.optJSONArray("ranking");
        if (members != null) for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null) continue;
            ranking.addView(dataRow("#" + (i + 1) + " · " + member.optString("display_name"),
                    formatNumber(member.optLong("contribution")) + " Gold contributed",
                    formatNumber(member.optLong("damage")) + " DMG"), rowParams());
        }
        content.addView(ranking, rowParams());

        JSONObject season = data.optJSONObject("season");
        JSONObject reward = data.optJSONObject("season_reward");
        if (season != null && reward != null) {
            LinearLayout seasonPanel = sectionPanel();
            seasonPanel.addView(text("GUILD SEASONAL REWARDS", 14, GOLD_BRIGHT, true));
            seasonPanel.addView(text("Season ends " + season.optString("end")
                    + " at 00:00 UTC+8. Claim during the next season.", 11, MUTED, false), rowParams());
            addPhase14Progress(seasonPanel, "Guild Bosses defeated", season.optInt("bosses"), reward.optInt("bosses"));
            addPhase14Progress(seasonPanel, "Your season strikes", season.optInt("personal"), reward.optInt("strikes"));
            JSONObject previous = data.optJSONObject("previous");
            if (previous != null) {
                seasonPanel.addView(dataRow("Previous season", previous.optString("start") + " to " + previous.optString("end"),
                        previous.optInt("bosses") + " clears · " + previous.optInt("personal") + " strikes"), rowParams());
                addGuildRewardButton(seasonPanel, previous, "season", guildRewardLabel(reward), admin);
            } else {
                seasonPanel.addView(text(guildRewardLabel(reward) + " when the first season closes.", 11, MUTED, false), rowParams());
            }
            content.addView(seasonPanel, rowParams());
        }

        LinearLayout achievements = sectionPanel();
        achievements.addView(text("GUILD ACHIEVEMENTS", 14, GOLD_BRIGHT, true));
        JSONArray badges = data.optJSONArray("achievements");
        if (badges != null) for (int i = 0; i < badges.length(); i++) {
            JSONObject badge = badges.optJSONObject(i);
            if (badge != null) achievements.addView(dataRow(badge.optString("name"), badge.optString("description"),
                    badge.optBoolean("complete") ? "EARNED" : "IN PROGRESS"), rowParams());
        }
        content.addView(achievements, rowParams());
    }

    private String guildRewardLabel(JSONObject reward) {
        int coins = reward.optInt("coins");
        int shards = reward.optInt("shards");
        return (coins > 0 ? formatNumber(coins) + " Gold" : "")
                + (coins > 0 && shards > 0 ? " + " : "")
                + (shards > 0 ? formatNumber(shards) + " Shards" : "");
    }

    private void addGuildRewardButton(LinearLayout panel, JSONObject status, String key,
                                      String label, boolean admin) {
        boolean claimed = status.optBoolean("claimed");
        boolean available = status.optBoolean("ready");
        TextView reward = button((claimed ? "CLAIMED · " : available ? "CLAIM · " : "REWARD · ") + label, available);
        reward.setEnabled(available && !claimed && !admin);
        reward.setOnClickListener(v -> postPhase14("/api/guilds/cooperative/claim", payload("reward", key), reward));
        panel.addView(reward, rowParams());
    }

    private void postPhase14(String path, JSONObject body, View control) {
        control.setEnabled(false);
        String tab = activeTab;
        int page = activePageRequest;
        api.post(path, body, result -> ui.post(() -> {
            if (page != activePageRequest || !tab.equals(activeTab)) return;
            control.setEnabled(true);
            if (!result.ok()) {
                showResultDialog("Action unavailable", result.error);
                return;
            }
            Toast.makeText(this, result.data.optString("message", "Journey updated."), Toast.LENGTH_SHORT).show();
            renderTab(tab);
        }));
    }

    private void renderNewPlayerJourney(JSONObject data) {
        JSONObject steps = data.optJSONObject("steps");
        if (steps == null) return;
        String[][] journey = {
                {"tutorial", "Complete the tutorial", "See how to get cards and start battling.", ""},
                {"card", "Get your first card", "Open a pack or obtain a card.", "summon"},
                {"formation", "Build a Formation", "Assign at least one card and save your team.", "formation"},
                {"upgrade", "Upgrade a card", "Level up or ascend a card.", "evolution"},
                {"adventure", "Win your first Adventure", "Clear any Adventure encounter.", "adventure"},
                {"guild", "Join a Guild", "Join a guild or create one.", "guild"},
                {"arena", "Try Arena", "Submit a turn against an opponent in Arena.", "arena"}
        };
        int done = 0;
        for (String[] step : journey) if (steps.optInt(step[0]) == 1) done++;
        LinearLayout progress = sectionPanel();
        addPhase14Progress(progress, done == journey.length ? "Journey complete" : "Your journey", done, journey.length);
        content.addView(progress, rowParams());
        for (String[] step : journey) {
            boolean complete = steps.optInt(step[0]) == 1;
            LinearLayout row = dataRow(step[1], step[2], complete ? "COMPLETE" : "START ›");
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                if ("tutorial".equals(step[0])) showJourneyTutorial(0, complete);
                else renderTab(step[3]);
            });
            content.addView(row, rowParams());
        }
    }

    private void showJourneyTutorial(int page, boolean complete) {
        String[][] tutorial = {
                {"Collect a card", "Visit Summon Hall. Open a Starter Pack to reveal your first cards. Inspect a card in the Encyclopedia to learn its rarity and ability."},
                {"Build and upgrade your team", "In Team, choose cards for your Formation and save the deck. In Evolution, check the power preview before upgrading a card."},
                {"Play your first battle", "Win an Adventure encounter. Try moves in Training Grounds, then submit your first move in Arena. Join a Guild when you're ready for cooperative battles."}
        };
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = phase11Sheet("TUTORIAL " + (page + 1) + " / " + tutorial.length,
                tutorial[page][0], tutorial[page][1], CYAN);
        boolean last = page == tutorial.length - 1;
        TextView next = button(last ? "FINISH TUTORIAL" : "NEXT", true);
        next.setOnClickListener(v -> {
            dialog.dismiss();
            if (!last) showJourneyTutorial(page + 1, complete);
            else if (!complete) postPhase14("/api/journey/tutorial", new JSONObject(), next);
        });
        shell.addView(next, rowParams());
        addPhase13Close(shell, dialog);
        dialog.setContentView(shell);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(dp(500), getResources().getDisplayMetrics().widthPixels - dp(24)), -2);
        }
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
        addPhase13Link("Summon History", "Review cards from your previous openings.", () -> openSummonHistory(0));
        TextView sealsHeading = text("AVAILABLE SUMMON SEALS", 9, GOLD_BRIGHT, true);
        sealsHeading.setLetterSpacing(0.12f);
        sealsHeading.setPadding(dp(2), dp(3), 0, dp(7));
        content.addView(sealsHeading);
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
            card.setPadding(dp(10), dp(11), dp(10), dp(11));
            card.setElevation(dp(3));
            card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(Color.red(accent) / 5 + 28, Color.green(accent) / 5 + 26,
                            Color.blue(accent) / 5 + 35), Color.rgb(11, 17, 31)}));
            FrameLayout seal = createSummonSeal(accent,
                    i % 4 == 0 ? "✧" : i % 4 == 1 ? "✦" : i % 4 == 2 ? "◇" : "☼", 96);
            card.addView(seal, new LinearLayout.LayoutParams(dp(96), dp(96)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(10), 0, dp(7), 0);
            info.addView(text("ASTRAL SEAL · " + perPack + " CARDS", 7, accent, true));
            TextView title = text(name, 15, TEXT, true);
            title.setMaxLines(2);
            info.addView(title);
            TextView description = text(pack.optString("description",
                    "A sealed selection from the Firstlight Realm."), 8, MUTED, false);
            description.setMaxLines(2);
            info.addView(description);
            info.addView(text(canOpen ? (unlimited ? "INFINITE SUMMON ACCESS" : "IN STOCK · " + quantity)
                    : "OUT OF STOCK", 8, GOLD_BRIGHT, true));
            if ("mythic".equals(slug) && pack.has("pity_count"))
                info.addView(text("MYTHIC PITY · " + pack.optInt("pity_count") + " / 40", 8, PINK, true));
            TextView odds = button("RATES & GUARANTEES", false);
            odds.setOnClickListener(v -> showSummonTransparency(slug));
            info.addView(odds, rowParams());
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
                        android.app.Dialog openingScene = showSummonOpeningDialog(name);
                        api.post("/api/packs/" + slug + "/open", request, result -> ui.post(() -> {
                            if (openingScene.isShowing()) openingScene.dismiss();
                            open.setEnabled(true);
                            open.setText("OPEN SEAL");
                            if (!result.ok()) {
                                if (sfxEnabled && gameAudio != null) gameAudio.playError();
                                showResultDialog("Summon failed", result.error);
                                return;
                            }
                            if (sfxEnabled && gameAudio != null) gameAudio.playSuccess();
                            if ("summon".equals(activeTab)) renderTab("summon");
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
    private void addArenaHeader(JSONObject data) {
        JSONObject rating = data.optJSONObject("rating");
        JSONObject season = data.optJSONObject("season");
        JSONObject active = data.optJSONObject("activeMatch");
        JSONObject queue = data.optJSONObject("queue");
        boolean observer = isAdminAccount() || data.optBoolean("rankedHidden");
        boolean betaTester = data.optBoolean("betaTesting")
                || (me != null && me.optBoolean("is_beta_tester"));
        int accent = observer ? VIOLET : betaTester ? VIOLET : GOLD_BRIGHT;

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(15), dp(14), dp(15), dp(13));
        hero.setBackground(gradient(Color.rgb(43, 51, 89), Color.rgb(9, 16, 34),
                Color.rgb(117, 112, 179), dp(17)));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout crest = new FrameLayout(this);
        crest.setBackground(gradient(Color.rgb(76, 68, 111), Color.rgb(23, 25, 53),
                GOLD, dp(15)));
        TextView emblem = text("⚔", 38, GOLD_BRIGHT, true);
        emblem.setGravity(Gravity.CENTER);
        crest.addView(emblem, new FrameLayout.LayoutParams(-1, -1));
        top.addView(crest, new LinearLayout.LayoutParams(dp(68), dp(72)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        TextView eyebrow = text("FIRSTLIGHT COMPETITIVE CIRCUIT", 7, CYAN, true);
        eyebrow.setLetterSpacing(0.11f);
        copy.addView(eyebrow);
        copy.addView(text(observer ? "ARENA OBSERVER" : "THE GRAND ARENA",
                17, TEXT, true));
        String seasonName = season == null ? "SEASON ACTIVE"
                : season.optString("name", "SEASON ACTIVE");
        copy.addView(text(seasonName.toUpperCase(java.util.Locale.ROOT), 8,
                GOLD_BRIGHT, true));
        TextView detail = text(observer
                ? "Public standings and match records"
                : betaTester ? "Beta test mode · Ranked uses training bots only"
                : active != null ? "Your live duel is in progress"
                : queue != null ? "Searching for a worthy opponent"
                : "Build your formation. Prove your strategy.",
                8, MUTED, false);
        detail.setMaxLines(2);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(detail);
        top.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hero.addView(top);

        View divider = new View(this);
        divider.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(100, 190, 202), Color.rgb(87, 82, 136), Color.TRANSPARENT}));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(11);
        dividerParams.bottomMargin = dp(9);
        hero.addView(divider);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        String state = observer ? "OBSERVER ACCESS"
                : betaTester ? "BETA TESTER · BOT ONLY"
                : active != null ? "LIVE MATCH"
                : queue != null ? "MATCHMAKING"
                : "READY TO FIGHT";
        TextView stateChip = text("●  " + state, 7, (observer || betaTester) ? VIOLET : CYAN, true);
        stateChip.setPadding(dp(9), dp(6), dp(9), dp(6));
        stateChip.setBackground(gradient(Color.rgb(25, 36, 59),
                Color.rgb(15, 24, 43), (observer || betaTester) ? VIOLET : CYAN, dp(20)));
        footer.addView(stateChip);
        TextView rank = text(betaTester ? "BETA TEST · UNRANKED"
                : rating == null ? (observer ? "PUBLIC LADDER" : "CASUAL  ·  RANKED")
                : rating.optString("tier", "BRONZE") + "  ·  "
                + formatNumber(rating.optLong("rating")) + " RATING",
                8, GOLD_BRIGHT, true);
        rank.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        footer.addView(rank, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hero.addView(footer);
        content.addView(hero, rowParams());
    }

    private void renderTrainingGrounds(JSONObject data) {
        JSONArray owned = data == null ? null : data.optJSONArray("cards");
        JSONArray cards = new JSONArray();
        if (owned != null) {
            for (int i = 0; i < owned.length(); i++) {
                JSONObject card = owned.optJSONObject(i);
                if (card != null && !card.optString("card_id").isEmpty()) cards.put(card);
            }
        }
        if (cards.length() < 5) {
            String[] elements = {"FIRE", "WATER", "EARTH", "WIND", "LIGHT"};
            for (String element : elements) {
                cards.put(payload("card_id", "practice-" + element, "name", humanize(element) + " Trainee",
                        "element", element, "rarity", "COMMON", "card_power", 136, "description",
                        "Loaned for practice.", "skill_name", "Training Burst", "skill_power", 2, "skill_unlocked", 1));
            }
        }
        String[] lineup = new String[5];
        for (int i = 0; i < lineup.length; i++) lineup[i] = cards.optJSONObject(i).optString("card_id");
        LinearLayout panel = sectionPanel();
        panel.addView(text("PRACTICE ONLY", 11, CYAN, true));
        panel.addView(text("Practise moves and review damage without rating, rewards, or account progression.",
                12, TEXT, false));
        content.addView(panel, rowParams());
        addPhase13Link("Training Dummy", "Test damage against a stationary target.",
                () -> showPracticeBattle(cards, lineup, PracticeBattle.Mode.DUMMY, PracticeBattle.Difficulty.NORMAL, 0));
        addPhase13Link("AI Battle", "Choose Easy, Normal, or Hard.",
                () -> showChoiceDialog("AI difficulty", "Choose a training opponent.",
                        new String[]{"Easy", "Normal", "Hard"}, difficulty -> {
                            long power = practicePower(practiceLineup(cards, lineup));
                            long enemy = Math.max(1, power + (difficulty - 1) * 250);
                            showPracticeBattle(cards, lineup, PracticeBattle.Mode.AI,
                                    PracticeBattle.Difficulty.values()[difficulty], enemy);
                        }));
        addPhase13Link("Deck Testing", "Edit five practice slots and compare elemental synergy.",
                () -> showPracticeLineup(cards, lineup));
        addPhase13Link("Boss Simulation", "Set the boss power and learn its attack pattern.",
                () -> showBossPracticeSetup(cards, lineup));
        addPhase13Link("Battle History", "Review attacks and results from this device.",
                () -> openBattleHistory("PRACTICE", 0));
    }

    private void addPhase13Link(String label, String description, Runnable action) {
        LinearLayout row = dataRow(label, description, "›");
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());
        content.addView(row, rowParams());
    }

    private ArrayList<JSONObject> practiceLineup(JSONArray cards, String[] lineup) {
        Map<String, JSONObject> byId = new LinkedHashMap<>();
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.optJSONObject(i);
            if (card != null) byId.put(card.optString("card_id"), card);
        }
        ArrayList<JSONObject> selected = new ArrayList<>();
        for (String id : lineup) {
            JSONObject card = byId.get(id);
            if (card != null) selected.add(card);
        }
        return selected;
    }

    private int practiceSynergy(ArrayList<JSONObject> cards) {
        String[] elements = new String[cards.size()];
        for (int i = 0; i < cards.size(); i++) {
            elements[i] = cards.get(i).optString("element", "").toUpperCase(java.util.Locale.ROOT);
        }
        return PracticeBattle.synergyBonus(elements);
    }

    private long practicePower(ArrayList<JSONObject> cards) {
        long total = 0;
        for (JSONObject card : cards) total += card.optLong("card_power");
        return total + total * practiceSynergy(cards) / 100;
    }

    private void showPracticeLineup(JSONArray cards, String[] lineup) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = phase11Sheet("TRAINING GROUNDS", "Deck Testing",
                "Changes here only apply to this practice session.", CYAN);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = sectionPanel();
        scroll.addView(body);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView summary = text("", 14, GOLD_BRIGHT, true);
        body.addView(summary);
        TextView[] slots = new TextView[lineup.length];
        TextView battle = button("TEST AGAINST NORMAL AI", true);
        Runnable update = () -> {
            ArrayList<JSONObject> team = practiceLineup(cards, lineup);
            summary.setText("Practice power " + formatNumber(practicePower(team))
                    + "  ·  Elemental synergy +" + practiceSynergy(team) + "%");
            battle.setEnabled(team.size() == lineup.length);
            for (int i = 0; i < lineup.length; i++) {
                String name = "Choose a card";
                for (int j = 0; j < cards.length(); j++) {
                    JSONObject card = cards.optJSONObject(j);
                    if (card != null && lineup[i].equals(card.optString("card_id"))) {
                        name = card.optString("name") + " · " + card.optString("element");
                        if (lineup[i].startsWith("practice-")) name += " · Loan";
                        break;
                    }
                }
                slots[i].setText((i + 1) + "   " + name);
            }
        };
        for (int i = 0; i < slots.length; i++) {
            int slot = i;
            slots[i] = button("", false);
            slots[i].setOnClickListener(v -> showTeamCardPicker(cards, lineup, slot, card -> {
                lineup[slot] = card == null ? "" : card.optString("card_id");
                update.run();
            }));
            body.addView(slots[i], rowParams());
        }
        battle.setOnClickListener(v -> {
            long power = practicePower(practiceLineup(cards, lineup));
            dialog.dismiss();
            showPracticeBattle(cards, lineup, PracticeBattle.Mode.AI, PracticeBattle.Difficulty.NORMAL, power);
        });
        body.addView(battle, rowParams());
        update.run();
        addPhase13Close(shell, dialog);
        phase11ShowSheet(dialog, shell, 660);
    }

    private void showBossPracticeSetup(JSONArray cards, String[] lineup) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = phase11Sheet("TRAINING GROUNDS", "Boss Simulation",
                "Power changes how hard the boss hits.", PINK);
        EditText power = editField("Boss power (1–1,000,000)", false);
        power.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        power.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});
        power.setText(String.valueOf(Math.max(1, practicePower(practiceLineup(cards, lineup)))));
        shell.addView(power, fieldParams());
        TextView start = button("START SIMULATION", true);
        start.setOnClickListener(v -> {
            String value = power.getText().toString().trim();
            int chosen;
            try { chosen = Integer.parseInt(value); } catch (NumberFormatException ignored) { chosen = 0; }
            if (chosen < 1 || chosen > 1000000) {
                power.setError("Enter a power from 1 to 1,000,000.");
                return;
            }
            if (practiceLineup(cards, lineup).isEmpty()) {
                showResultDialog("Choose a card", "Add a card in Deck Testing before you start.");
                return;
            }
            dialog.dismiss();
            showPracticeBattle(cards, lineup, PracticeBattle.Mode.BOSS, PracticeBattle.Difficulty.HARD, chosen);
        });
        shell.addView(start, rowParams());
        addPhase13Close(shell, dialog);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setContentView(shell);
        dialog.show();
        if (window != null) window.setLayout(Math.min(dp(480), getResources().getDisplayMetrics().widthPixels - dp(24)), -2);
    }

    private void showPracticeBattle(JSONArray cards, String[] lineup, PracticeBattle.Mode mode,
                                    PracticeBattle.Difficulty difficulty, long enemyPower) {
        ArrayList<JSONObject> team = practiceLineup(cards, lineup);
        if (team.isEmpty()) {
            showResultDialog("Choose a card", "Add a card in Deck Testing before you start.");
            return;
        }
        PracticeBattle battle = new PracticeBattle(mode, difficulty, practicePower(team), enemyPower);
        String title = mode == PracticeBattle.Mode.DUMMY ? "Training Dummy"
                : mode == PracticeBattle.Mode.BOSS ? "Boss Simulation" : "AI Battle · " + humanize(difficulty.name());
        String owner = me == null ? "" : me.optString("user_id");
        JSONArray rounds = new JSONArray();
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        String briefing = mode == PracticeBattle.Mode.DUMMY ? "Stationary target · Select a card to test its skill."
                : "Team " + formatNumber(battle.teamPower) + " power  ·  Opponent " + formatNumber(enemyPower) + " power";
        LinearLayout shell = phase11Sheet("TRAINING GROUNDS", title, briefing, CYAN);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = sectionPanel();
        scroll.addView(body);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView status = text("", 15, GOLD_BRIGHT, true);
        body.addView(status);
        ArrayList<String> names = new ArrayList<>();
        for (JSONObject card : team) names.add(card.optString("name", "Card"));
        Spinner activeCard = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        activeCard.setAdapter(adapter);
        body.addView(activeCard, fieldParams());
        TextView skillInfo = text("", 11, MUTED, false);
        body.addView(skillInfo);
        LinearLayout moves = new LinearLayout(this);
        ArrayList<TextView> controls = new ArrayList<>();
        for (PracticeBattle.Move move : PracticeBattle.Move.values()) {
            TextView control = button(move.name(), move == PracticeBattle.Move.STRIKE);
            moves.addView(control, new LinearLayout.LayoutParams(0, dp(44), 1f));
            controls.add(control);
        }
        body.addView(moves, rowParams());
        TextView last = text("Choose your first move.", 12, TEXT, false);
        body.addView(last, rowParams());
        TextView finish = button("CLOSE PRACTICE", false);
        addGap(shell, 4);
        shell.addView(finish, new LinearLayout.LayoutParams(-1, dp(44)));
        Runnable update = () -> {
            JSONObject card = team.get(Math.max(0, activeCard.getSelectedItemPosition()));
            boolean hasSkill = card.optInt("skill_power") > 0;
            boolean unlocked = hasSkill && card.optInt("skill_unlocked") == 1;
            boolean canTest = unlocked || (mode == PracticeBattle.Mode.DUMMY && hasSkill);
            status.setText(mode == PracticeBattle.Mode.DUMMY
                    ? "Total damage " + formatNumber(battle.damageDealt) + "  ·  Focus " + battle.focus
                    : "Your HP " + battle.myHp + "  ·  Enemy HP " + battle.enemyHp + "  ·  Focus " + battle.focus);
            skillInfo.setText(!hasSkill ? "This card has no signature ability."
                    : card.optString("skill_name", "Signature Ability") + " · "
                    + (battle.skillUsed && mode != PracticeBattle.Mode.DUMMY ? "Used this session" : unlocked ? "Ready"
                    : canTest ? "Locked in battle · Dummy trial available" : "Unlock through Ascension"));
            controls.get(3).setEnabled(canTest && (mode == PracticeBattle.Mode.DUMMY || !battle.skillUsed)
                    && !battle.finished() && battle.turns < 30);
        };
        activeCard.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { update.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        for (PracticeBattle.Move move : PracticeBattle.Move.values()) {
            controls.get(move.ordinal()).setOnClickListener(v -> {
                JSONObject card = team.get(Math.max(0, activeCard.getSelectedItemPosition()));
                PracticeBattle.Round round = battle.play(move, card.optInt("skill_power"));
                String attack = move == PracticeBattle.Move.SKILL ? card.optString("skill_name", "Signature") : humanize(move.name());
                String enemy = round.enemyMove == null ? "Stationary target" : "Enemy " + humanize(round.enemyMove.name());
                String line = attack + " dealt " + round.damageDealt + " · " + enemy + " · " + round.damageTaken + " taken";
                rounds.put(payload("turn", round.turn, "summary", line,
                        "hp", round.playerHp + " / " + round.enemyHp));
                update.run();
                last.setText("Turn " + round.turn + " · " + line);
                finish.setText("FINISH & VIEW RECAP");
                if (battle.finished() || battle.turns >= 30) {
                    for (TextView control : controls) control.setEnabled(false);
                    last.append("\n" + battle.outcome());
                }
            });
        }
        finish.setOnClickListener(v -> {
            dialog.dismiss();
            if (rounds.length() == 0) return;
            JSONObject entry = payload("title", title,
                    "result", battle.finished() || mode == PracticeBattle.Mode.DUMMY ? battle.outcome() : "ENDED EARLY",
                    "turns", battle.turns, "damage", battle.damageDealt, "rounds", rounds,
                    "played_at", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(new java.util.Date()));
            savePracticeRecap(owner, entry);
            showMatchRecap("PRACTICE", entry, rounds);
        });
        dialog.setCancelable(false);
        update.run();
        phase11ShowSheet(dialog, shell, 660);
        dialog.setCanceledOnTouchOutside(false);
    }

    private JSONArray localPracticeHistory(String userId) {
        if (userId.isEmpty()) return new JSONArray();
        String raw = getSharedPreferences("cu_practice_history", MODE_PRIVATE).getString(userId, "[]");
        try { return new JSONArray(raw); } catch (org.json.JSONException ignored) { return new JSONArray(); }
    }

    private void savePracticeRecap(String userId, JSONObject entry) {
        if (userId.isEmpty()) return;
        JSONArray previous = localPracticeHistory(userId);
        JSONArray next = new JSONArray().put(entry);
        for (int i = 0; i < Math.min(previous.length(), 29); i++) {
            JSONObject row = previous.optJSONObject(i);
            if (row != null) next.put(row);
        }
        getSharedPreferences("cu_practice_history", MODE_PRIVATE).edit().putString(userId, next.toString()).apply();
    }

    private void addPhase13Close(LinearLayout shell, android.app.Dialog dialog) {
        TextView close = button("CLOSE", false);
        close.setOnClickListener(v -> dialog.dismiss());
        addGap(shell, 4);
        shell.addView(close, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private void renderCardEncyclopedia(JSONObject data) {
        JSONArray cards = data.optJSONArray("cards");
        if (cards == null || cards.length() == 0) {
            showNotice("The card catalogue is empty.");
            return;
        }
        EditText search = editField("Search name, code, skill or set", false);
        search.setSingleLine(true);
        content.addView(search, fieldParams());
        ArrayList<String> elements = new ArrayList<>();
        elements.add("All elements");
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.optJSONObject(i);
            if (card == null) continue;
            String element = card.optString("element", "AETHER");
            if (!elements.contains(element)) elements.add(element);
        }
        String[] rarities = {"All rarities", "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
        String[] filter = {elements.get(0), rarities[0]};
        LinearLayout filterRow = new LinearLayout(this);
        TextView elementFilter = button("ALL ELEMENTS", false);
        TextView rarityFilter = button("ALL RARITIES", false);
        filterRow.addView(elementFilter, new LinearLayout.LayoutParams(0, dp(44), 1f));
        filterRow.addView(rarityFilter, new LinearLayout.LayoutParams(0, dp(44), 1f));
        content.addView(filterRow, rowParams());
        TextView count = text("", 11, GOLD_BRIGHT, true);
        content.addView(count, rowParams());
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list, rowParams());
        TextView more = button("SHOW MORE CARDS", false);
        content.addView(more, rowParams());
        int[] limit = {30};
        Runnable update = () -> {
            String query = search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            list.removeAllViews();
            int matches = 0;
            for (int i = 0; i < cards.length(); i++) {
                JSONObject card = cards.optJSONObject(i);
                if (card == null) continue;
                String element = card.optString("element", "AETHER");
                String rarity = card.optString("rarity", "COMMON");
                if ((!filter[0].equals(elements.get(0)) && !filter[0].equalsIgnoreCase(element))
                        || (!filter[1].equals(rarities[0]) && !filter[1].equalsIgnoreCase(rarity))) continue;
                String name = card.optString("name", "Card");
                String code = card.optString("card_code");
                String searchable = name + " " + code + " " + element + " " + rarity + " "
                        + card.optString("set_name") + " " + card.optString("skill_name");
                if (!searchable.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                matches++;
                if (matches > limit[0]) continue;
                String owned = card.optInt("owned_count") > 0 ? "OWNED" : "UNDISCOVERED";
                LinearLayout row = dataRow(name, code + " · " + rarity + " · " + element, owned + " ›");
                row.setFocusable(true);
                row.setOnClickListener(v -> showEncyclopediaCard(card));
                list.addView(row, rowParams());
            }
            count.setText(matches == 0 ? "No matching cards"
                    : Math.min(limit[0], matches) + " of " + matches + " cards");
            more.setVisibility(matches > limit[0] ? View.VISIBLE : View.GONE);
        };
        elementFilter.setOnClickListener(v -> showChoiceDialog("Element", "", elements.toArray(new String[0]), i -> {
            filter[0] = elements.get(i);
            elementFilter.setText(filter[0]);
            limit[0] = 30;
            update.run();
        }));
        rarityFilter.setOnClickListener(v -> showChoiceDialog("Rarity", "", rarities, i -> {
            filter[1] = rarities[i];
            rarityFilter.setText(filter[1]);
            limit[0] = 30;
            update.run();
        }));
        more.setOnClickListener(v -> {
            limit[0] += 30;
            update.run();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                limit[0] = 30;
                update.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        update.run();
    }

    private void showEncyclopediaCard(JSONObject card) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        String rarity = card.optString("rarity", "COMMON");
        int accent = cardRarityColor(rarity);
        LinearLayout shell = phase11Sheet("CARD ENCYCLOPEDIA", card.optString("name", "Card"),
                card.optString("card_code") + " · " + rarity + " · " + card.optString("element"), accent);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = sectionPanel();
        scroll.addView(body);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout.LayoutParams art = new LinearLayout.LayoutParams(dp(128), dp(180));
        art.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(phase11Art(card, accent), art);
        body.addView(dataRow("Card set", card.optString("set_name", "Firstlight"), ""), rowParams());
        if (card.has("base_power")) body.addView(dataRow("Base power",
                "Power before level and ascension bonuses", formatNumber(card.optLong("base_power"))), rowParams());
        body.addView(dataRow("Collection", card.optInt("owned_count") > 0
                ? card.optInt("owned_count") + " available copies" : "Undiscovered", ""), rowParams());
        String description = card.optString("description", "");
        if (!description.isEmpty()) body.addView(text(description, 13, TEXT, false), rowParams());
        String skill = card.isNull("skill_name") ? "" : card.optString("skill_name", "");
        LinearLayout ability = sectionPanel();
        ability.addView(text("SIGNATURE ABILITY", 10, GOLD_BRIGHT, true));
        if (skill.isEmpty()) {
            ability.addView(text("No signature ability recorded.", 12, MUTED, false));
        } else {
            ability.addView(text(skill, 14, TEXT, true));
            ability.addView(text(card.optString("skill_description", ""), 12, TEXT, false));
            ability.addView(text("Unlocks at " + (card.optInt("unlock_star_rank", 1) + 1)
                    + "★ · Skill power " + card.optInt("skill_power"), 11, MUTED, false));
        }
        body.addView(ability, rowParams());
        addPhase13Close(shell, dialog);
        phase11ShowSheet(dialog, shell, 660);
    }

    private static final String[] HISTORY_MODES = {"ARENA", "ADVENTURE", "DUNGEON", "RAID", "PRACTICE"};

    private void openBattleHistory(String mode, int offset) {
        historyMode = mode;
        historyOffset = offset;
        renderTab("battle_history");
    }

    private void renderBattleHistory(JSONObject data, String mode, int offset) {
        JSONArray entries = data == null ? localPracticeHistory(me == null ? "" : me.optString("user_id"))
                : data.optJSONArray("entries");
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = new LinearLayout(this);
        strip.addView(filters);
        for (String option : HISTORY_MODES) {
            TextView choose = button(humanize(option), mode.equals(option));
            choose.setContentDescription(humanize(option) + " history");
            choose.setOnClickListener(v -> openBattleHistory(option, 0));
            filters.addView(choose, new LinearLayout.LayoutParams(dp(103), dp(42)));
        }
        content.addView(strip, rowParams());
        if (entries == null || entries.length() == 0) {
            showNotice("No " + humanize(mode).toLowerCase(java.util.Locale.ROOT) + " battles recorded yet.");
        } else {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) continue;
                LinearLayout row = dataRow(historyTitle(mode, entry), entry.optString("played_at"),
                        historyOutcome(mode, entry) + " ›");
                row.setFocusable(true);
                row.setOnClickListener(v -> {
                    if ("ARENA".equals(mode)) fetchArenaRecap(entry);
                    else showMatchRecap(mode, entry, entry.optJSONArray("rounds"));
                });
                content.addView(row, rowParams());
            }
        }
        if (offset > 0) addPhase13Link("Newer battles", "Previous page", () -> openBattleHistory(mode, offset - 20));
        if (data != null && data.optBoolean("hasMore")) {
            addPhase13Link("Older battles", "Next page", () -> openBattleHistory(mode, offset + 20));
        }
    }

    private boolean historyPlayerOne(JSONObject match) {
        return me != null && me.optString("user_id").equals(match.optString("player_one_id"));
    }

    private String historyTitle(String mode, JSONObject entry) {
        if (!"ARENA".equals(mode)) return entry.optString("title", humanize(mode));
        boolean first = historyPlayerOne(entry);
        boolean bot = entry.optInt(first ? "player_two_is_bot" : "player_one_is_bot") == 1;
        String opponent = entry.optString(first ? "player_two_name" : "player_one_name", "Collector");
        return opponent + " · " + (bot ? "Training bot" : humanize(entry.optString("mode", "CASUAL")));
    }

    private String historyOutcome(String mode, JSONObject entry) {
        if ("RAID".equals(mode)) return "STRIKE";
        if (!"ARENA".equals(mode)) return entry.optString("result", "COMPLETED");
        if (entry.isNull("winner_id") || entry.optString("winner_id").isEmpty()) return "DRAW";
        return me != null && me.optString("user_id").equals(entry.optString("winner_id")) ? "VICTORY" : "DEFEAT";
    }

    private void fetchArenaRecap(JSONObject match) {
        String matchId = match.optString("match_id");
        String tab = activeTab;
        int generation = activePageRequest;
        if (matchId.isEmpty()) return;
        api.get("/api/battle-history/arena/" + Uri.encode(matchId), result -> ui.post(() -> {
            if (!tab.equals(activeTab) || generation != activePageRequest) return;
            if (result.ok()) showMatchRecap("ARENA", match, result.data.optJSONArray("rounds"));
            else showResultDialog("Recap unavailable", result.error);
        }));
    }

    private void showMatchRecap(String mode, JSONObject entry, JSONArray rounds) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        String date = entry.optString("played_at", entry.optString("completed_at"));
        LinearLayout shell = phase11Sheet("MATCH RECAP · " + humanize(mode), historyTitle(mode, entry),
                historyOutcome(mode, entry) + "  ·  " + date, GOLD_BRIGHT);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = sectionPanel();
        scroll.addView(body);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        boolean first = historyPlayerOne(entry);
        if ("ARENA".equals(mode)) {
            String own = first ? "player_one_" : "player_two_";
            String other = first ? "player_two_" : "player_one_";
            body.addView(dataRow("Final HP", "You / Opponent",
                    entry.optInt(own + "score") + " / " + entry.optInt(other + "score")), rowParams());
            if (!entry.isNull(own + "delta")) {
                int rating = entry.optInt(own + "delta");
                body.addView(dataRow("Rating change", "", (rating > 0 ? "+" : "") + rating), rowParams());
            }
            if (rounds == null || rounds.length() == 0) body.addView(text("No turns were recorded.", 12, MUTED, false));
            if (rounds != null) for (int i = 0; i < rounds.length(); i++) {
                JSONObject round = rounds.optJSONObject(i);
                if (round == null) continue;
                body.addView(dataRow("Turn " + round.optInt("turn_no"),
                        humanize(round.optString(own + "move")) + " vs " + humanize(round.optString(other + "move"))
                                + "\n" + round.optInt(own + "damage") + " dealt · " + round.optInt(other + "damage") + " taken",
                        round.optInt(own + "hp") + " / " + round.optInt(other + "hp") + " HP"), rowParams());
            }
        } else if ("ADVENTURE".equals(mode)) {
            body.addView(dataRow(entry.optString("enemy_name", "Enemy"), "Your power / Enemy power",
                    formatNumber(entry.optLong("player_power")) + " / " + formatNumber(entry.optLong("enemy_power"))), rowParams());
            body.addView(dataRow("Rewards", formatNumber(entry.optLong("reward_xp")) + " XP",
                    formatNumber(entry.optLong("reward_coins")) + " Gold"), rowParams());
        } else if ("DUNGEON".equals(mode)) {
            body.addView(dataRow("Floors reached", "Difficulty " + entry.optInt("difficulty"),
                    String.valueOf(entry.optInt("floor_reached"))), rowParams());
        } else if ("RAID".equals(mode)) {
            body.addView(dataRow("Damage dealt", entry.isNull("reward_claimed_at") ? "" : "Reward claimed",
                    formatNumber(entry.optLong("damage"))), rowParams());
        } else {
            body.addView(dataRow("Total damage", entry.optInt("turns") + " turns",
                    formatNumber(entry.optLong("damage"))), rowParams());
            if (rounds != null) for (int i = 0; i < rounds.length(); i++) {
                JSONObject round = rounds.optJSONObject(i);
                if (round == null) continue;
                body.addView(dataRow("Turn " + round.optInt("turn"), round.optString("summary"),
                        round.optString("hp") + " HP"), rowParams());
            }
        }
        addPhase13Close(shell, dialog);
        phase11ShowSheet(dialog, shell, 660);
    }

    private void renderArena(JSONObject data) {
        addArenaHeader(data);
        addPhase13Link("Training Grounds", "Practise against a dummy, AI, or a simulated boss.",
                () -> renderTab("training"));
        addPhase13Link("Battle History", "Open completed battles and turn-by-turn recaps.",
                () -> openBattleHistory("ARENA", 0));
        boolean betaTester = data.optBoolean("betaTesting")
                || (me != null && me.optBoolean("is_beta_tester"));
        if (betaTester) {
            LinearLayout betaNotice = sectionPanel();
            betaNotice.setBackground(gradient(Color.rgb(45, 36, 76), Color.rgb(15, 17, 39),
                    VIOLET, dp(12)));
            betaNotice.addView(text("BETA TESTER MODE  ·  TRAINING ARENA", 8, VIOLET, true));
            betaNotice.addView(text("Beta accounts can use Training Grounds or play Casual Arena.",
                    8, MUTED, false));
            content.addView(betaNotice, rowParams());
        }

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
            boolean opponentIsBot = active.optBoolean("opponentIsBot");
            if (opponentIsBot) {
                LinearLayout botNotice = sectionPanel();
                botNotice.setOrientation(LinearLayout.HORIZONTAL);
                botNotice.setGravity(Gravity.CENTER_VERTICAL);
                botNotice.addView(text("◈", 19, CYAN, true));
                LinearLayout botCopy = new LinearLayout(this);
                botCopy.setOrientation(LinearLayout.VERTICAL);
                botCopy.setPadding(dp(9), 0, 0, 0);
                botCopy.addView(text("TRAINING BOT OPPONENT", 8, CYAN, true));
                botCopy.addView(text("Practice match · server-controlled bot · not a human player", 7, MUTED, false));
                botNotice.addView(botCopy, new LinearLayout.LayoutParams(0, -2, 1f));
                content.addView(botNotice, rowParams());
            }
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
            addPhase16ReconnectTimer(active);

            FrameLayout battleStage = createArenaBattleStage(active);
            content.addView(battleStage, rowParams());



            String myMove = active.isNull("myMove")?"":active.optString("myMove", "");
            if (myMove.isEmpty()) {
                content.addView(createArenaMoveHand(active), rowParams());
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
            if (!betaTester && !data.optBoolean("rankedHidden") && !data.optBoolean("rankedBanned")) {
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
            content.addView(text("RECENT MATCHES", 10, GOLD, true));
            for (int i = 0; i < recent.length(); i++) {
                JSONObject match = recent.optJSONObject(i);
                if (match == null) continue;
                LinearLayout row = dataRow(historyTitle("ARENA", match), match.optString("completed_at"),
                        historyOutcome("ARENA", match) + " ›");
                row.setFocusable(true);
                row.setOnClickListener(v -> fetchArenaRecap(match));
                content.addView(row, rowParams());
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


    private LinearLayout createArenaLifeStrip(String role,String name,long hp,long maxHp,int accent){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(10),dp(7),dp(10),dp(7));
        box.setBackground(gradient(Color.argb(226,10,20,34),Color.argb(236,6,12,24),accent,dp(10)));
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.addView(text(role,7,accent,true),new LinearLayout.LayoutParams(0,-2,1f));line.addView(text(name,9,TEXT,true));box.addView(line);
        LinearLayout hpLine=new LinearLayout(this);hpLine.setGravity(Gravity.CENTER_VERTICAL);hpLine.addView(text("LIFE",6,MUTED,true),new LinearLayout.LayoutParams(0,-2,1f));
        hpLine.addView(text(formatNumber(hp)+(maxHp>0?" / "+formatNumber(maxHp):"")+" HP",8,accent,true));box.addView(hpLine);
        if(maxHp>0){ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress((int)Math.max(0,Math.min(100,Math.round(hp*100d/maxHp))));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(5));bp.topMargin=dp(4);box.addView(bar,bp);}return box;
    }
    private HorizontalScrollView createArenaCardRow(JSONObject match,boolean playerSide,int accent){
        ArrayList<JSONObject> deck=new ArrayList<>();String[] keys=playerSide?new String[]{"myCards","my_cards","myFormation","my_formation","playerCards"}:new String[]{"opponentCards","opponent_cards","opponentFormation","opponent_formation","enemyCards"};
        for(String key:keys){JSONArray arr=match.optJSONArray(key);if(arr==null||arr.length()==0)continue;for(int i=0;i<arr.length();i++){JSONObject x=arr.optJSONObject(i);if(x!=null){JSONObject nested=x.optJSONObject("card");deck.add(nested==null?x:nested);}}break;}
        if(deck.isEmpty()){JSONObject single=firstArenaMatchCard(match,playerSide);if(single!=null)deck.add(single);}
        if(playerSide)arenaLeaderCard=deck.isEmpty()?null:deck.get(0);else arenaOpponentCard=deck.isEmpty()?null:deck.get(0);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setPadding(dp(3),dp(3),dp(3),dp(3));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER);row.setPadding(dp(2),0,dp(2),0);scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));scroll.setTag(row);
        if(deck.isEmpty()){ArenaCardFace face=new ArenaCardFace(playerSide?"YOUR FIELD":"RIVAL FIELD",accent);face.bind(null,playerSide?"NO ACTIVE CARD":"HIDDEN LOADOUT",playerSide?"Set a deck in Formation":"No card shared");row.addView(face.root,new LinearLayout.LayoutParams(dp(126),dp(163)));}
        else for(int i=0;i<deck.size();i++){ArenaCardFace face=new ArenaCardFace((playerSide?"YOUR CARD ":"RIVAL CARD ")+(i+1),accent);face.bind(deck.get(i),"CARD NOT FOUND","Card data unavailable");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(126),dp(163));p.setMargins(dp(4),0,dp(4),0);row.addView(face.root,p);}
        return scroll;
    }
    private FrameLayout createArenaZoneMarker(String label,String glyph,int accent){
        FrameLayout zone=new FrameLayout(this);zone.setBackground(gradient(Color.rgb(20,31,50),Color.rgb(8,14,27),accent,dp(8)));
        TextView rune=text(glyph,22,accent,true);rune.setGravity(Gravity.CENTER);zone.addView(rune,new FrameLayout.LayoutParams(-1,-1));
        TextView caption=text(label,6,TEXT,true);caption.setGravity(Gravity.CENTER);caption.setPadding(dp(2),dp(4),dp(2),dp(4));caption.setBackgroundColor(Color.argb(190,5,10,20));
        zone.addView(caption,new FrameLayout.LayoutParams(-1,dp(22),Gravity.BOTTOM));return zone;
    }
    private FrameLayout createArenaBattleStage(JSONObject active){
        FrameLayout stage=new FrameLayout(this);stage.setClipChildren(false);stage.setClipToPadding(false);
        stage.setBackground(gradient(Color.rgb(18,30,47),Color.rgb(5,9,19),GOLD,dp(16)));
        RealmSceneView backdrop=new RealmSceneView(this,"arena",active.optString("mode","LIVE DUEL")+" · TURN "+active.optInt("current_turn"),"A collector-to-collector card battle.",active.optInt("current_turn"),10);
        stage.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout table=new LinearLayout(this);table.setOrientation(LinearLayout.VERTICAL);table.setPadding(dp(9),dp(9),dp(9),dp(9));
        table.setBackground(gradient(Color.argb(222,8,15,28),Color.argb(234,5,10,22),Color.rgb(78,100,132),dp(14)));
        table.addView(createArenaLifeStrip("RIVAL SIDE",active.optString("opponentName","Opponent"),active.optLong("opponentHp"),active.optLong("opponentMaxHp"),RED));
        LinearLayout rival=new LinearLayout(this);rival.setGravity(Gravity.CENTER_VERTICAL);rival.setPadding(dp(3),dp(3),dp(3),dp(3));
        rival.setBackground(gradient(Color.argb(190,31,27,42),Color.argb(205,9,16,30),Color.rgb(125,74,91),dp(11)));
        rival.addView(createArenaZoneMarker("DISCARD","▤",RED),new LinearLayout.LayoutParams(dp(38),dp(58)));
        HorizontalScrollView rivalCards=createArenaCardRow(active,false,RED);LinearLayout.LayoutParams rcp=new LinearLayout.LayoutParams(0,dp(168),1f);rcp.setMargins(dp(4),0,dp(4),0);rival.addView(rivalCards,rcp);
        rival.addView(createArenaZoneMarker("DECK","▥",RED),new LinearLayout.LayoutParams(dp(38),dp(58)));
        LinearLayout.LayoutParams rfp=new LinearLayout.LayoutParams(-1,dp(176));rfp.topMargin=dp(5);table.addView(rival,rfp);
        LinearLayout versus=new LinearLayout(this);versus.setGravity(Gravity.CENTER);versus.setPadding(dp(5),dp(5),dp(5),dp(5));
        View left=new View(this);left.setBackgroundColor(Color.argb(170,178,147,101));versus.addView(left,new LinearLayout.LayoutParams(0,dp(1),1f));
        versus.addView(text("  ⚔  VS  ⚔  ",9,GOLD_BRIGHT,true));View right=new View(this);right.setBackgroundColor(Color.argb(170,178,147,101));versus.addView(right,new LinearLayout.LayoutParams(0,dp(1),1f));
        table.addView(versus,new LinearLayout.LayoutParams(-1,dp(32)));
        table.addView(createArenaLifeStrip("YOUR SIDE",me==null?"YOU":me.optString("display_name","YOU"),active.optLong("myHp"),active.optLong("myMaxHp"),GREEN));
        LinearLayout ours=new LinearLayout(this);ours.setGravity(Gravity.CENTER_VERTICAL);ours.setPadding(dp(3),dp(3),dp(3),dp(3));
        ours.setBackground(gradient(Color.argb(190,22,42,43),Color.argb(205,8,18,27),Color.rgb(71,132,118),dp(11)));
        ours.addView(createArenaZoneMarker("DECK","▥",GREEN),new LinearLayout.LayoutParams(dp(38),dp(58)));
        HorizontalScrollView ourCards=createArenaCardRow(active,true,GREEN);LinearLayout.LayoutParams ocp=new LinearLayout.LayoutParams(0,dp(168),1f);ocp.setMargins(dp(4),0,dp(4),0);ours.addView(ourCards,ocp);
        ours.addView(createArenaZoneMarker("DISCARD","▤",GREEN),new LinearLayout.LayoutParams(dp(38),dp(58)));
        LinearLayout.LayoutParams ofp=new LinearLayout.LayoutParams(-1,dp(176));ofp.topMargin=dp(5);table.addView(ours,ofp);
        TextView footer=text("FIRSTLIGHT BATTLEFIELD  ·  TURN "+active.optInt("current_turn"),7,GOLD_BRIGHT,true);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(7),0,0);table.addView(footer);
        stage.addView(table,new FrameLayout.LayoutParams(-1,-1));stage.setMinimumHeight(dp(480));
        if(arenaLeaderCard==null){api.get("/api/formation",result->ui.post(()->{if(!result.ok()||stage.getParent()==null)return;JSONObject card=firstFormationCard(result.data);if(card==null)return;arenaLeaderCard=card;
            Object tag=ourCards.getTag();if(tag instanceof LinearLayout){LinearLayout row=(LinearLayout)tag;row.removeAllViews();ArenaCardFace face=new ArenaCardFace("YOUR CARD 1",GREEN);face.bind(card,"NO ACTIVE CARD","Set a deck in Formation");row.addView(face.root,new LinearLayout.LayoutParams(dp(126),dp(163)));}}));}
        return stage;
    }
    private LinearLayout createArenaMoveHand(JSONObject active){
        LinearLayout holder=new LinearLayout(this);holder.setOrientation(LinearLayout.VERTICAL);holder.setPadding(dp(2),dp(2),dp(2),dp(2));
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(text("TACTIC HAND",8,GOLD_BRIGHT,true),new LinearLayout.LayoutParams(0,-2,1f));heading.addView(text("CHOOSE ONE ACTION",7,MUTED,true));holder.addView(heading);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(2),dp(7),dp(2),dp(4));scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));
        String[][] choices={{"STRIKE","⚔","Deal damage to the rival","BATTLE"},{"GUARD","◈","Reduce incoming damage","DEFENCE"},{"FOCUS","✦","Charge your next action","ENERGY"}};
        int[] accents={GOLD_BRIGHT,GREEN,Color.rgb(133,174,255)};
        for(int i=0;i<choices.length;i++){String[] c=choices[i];int tint=accents[i];FrameLayout card=new FrameLayout(this);card.setPadding(dp(9),dp(8),dp(9),dp(8));card.setBackground(gradient(Color.rgb(35,43,67),Color.rgb(10,17,34),tint,dp(12)));card.setElevation(dp(4));
            LinearLayout face=new LinearLayout(this);face.setOrientation(LinearLayout.VERTICAL);face.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView category=text(c[3],6,tint,true);category.setGravity(Gravity.CENTER);face.addView(category);TextView glyph=text(c[1],31,tint,true);glyph.setGravity(Gravity.CENTER);glyph.setShadowLayer(dp(12),0,0,tint);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,dp(54));gp.topMargin=dp(4);face.addView(glyph,gp);
            TextView label=text(c[0],11,TEXT,true);label.setGravity(Gravity.CENTER);face.addView(label);TextView desc=text(c[2],7,MUTED,false);desc.setGravity(Gravity.CENTER);desc.setMaxLines(2);desc.setPadding(0,dp(3),0,dp(4));face.addView(desc);
            TextView play=text("PLAY ACTION  ›",6,tint,true);play.setGravity(Gravity.CENTER);play.setPadding(0,dp(5),0,0);face.addView(play);card.addView(face,new FrameLayout.LayoutParams(-1,-1));
            card.setClickable(true);card.setFocusable(true);card.setOnClickListener(v->{card.animate().scaleX(.96f).scaleY(.96f).setDuration(75).withEndAction(()->card.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
                confirmAction("Play "+c[0]+"?","Your selected action is locked for this turn once submitted.",()->submitArenaMove(active.optString("match_id"),c[0],active.optInt("current_turn")));});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(112),dp(145));p.setMargins(dp(4),0,dp(4),0);row.addView(card,p);}
        holder.addView(scroll);return holder;
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
                node.addView(text("RECOMMENDED POWER",6,MUTED,true));
                long recommendedPower=stage.optLong("enemy_power");
                int powerAccent=data.optLong("playerPower")>=recommendedPower?CYAN:GOLD_BRIGHT;
                node.addView(text(formatNumber(recommendedPower),10,powerAccent,true));
                node.addView(text("YOUR POWER  "+formatNumber(data.optLong("playerPower")),6,MUTED,false));
                node.addView(text(cleared?"CLEARED":unlocked?"ENCOUNTER READY":"LOCKED",6,accent,true));
                if(unlocked){
                    TextView fight=button(cleared?"REPLAY":"BATTLE",true);
                    fight.setOnClickListener(v->confirmAction("Enter "+stageTitle+"?",
                            "Challenge "+enemy+". The server calculates the battle result and rewards.",()->{
                        JSONObject request=new JSONObject();
                        try{request.put("requestId",newActionId());}catch(Exception ignored){}
                        fight.setEnabled(false);fight.setText("FIGHTING...");
                        postWithModeDeck("pve", "/api/adventure/"+slug+"/battle",request,result->ui.post(()->{
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
        if (challenges == null) {
            addRealmScene("adventure", "QUEST BOARD",
                    "Fetching your daily contracts and collector objectives.", 0, 0);
            TextView loading = text("SYNCING DAILY CHALLENGES…", 8, MUTED, true);
            content.addView(loading, rowParams());
            if (missionsDailyLoading) return;
            missionsDailyLoading = true;
            api.get("/api/daily-challenges", result -> ui.post(() -> {
                missionsDailyLoading = false;
                if (!"missions".equals(activeTab) || content == null) return;
                content.removeView(loading);
                JSONArray daily = result.ok() ? result.data.optJSONArray("challenges") : null;
                try { data.put("daily_challenges", daily == null ? new JSONArray() : daily); }
                catch (Exception ignored) {}
                renderMissions(data);
            }));
            return;
        }
        addRealmScene("adventure", "QUEST BOARD",
                "Daily contracts, weekly trials and long-term collector objectives.",
                0, 0);
        if (missions == null && challenges == null) {
            renderObjectSummary(data, "MISSION STATUS");
            return;
        }

        ArrayList<String> claimPaths = new ArrayList<>();
        ArrayList<JSONObject> claimRewards = new ArrayList<>();
        if (challenges != null) for (int i = 0; i < challenges.length(); i++) {
            JSONObject item = challenges.optJSONObject(i);
            if (item == null) continue;
            int target = item.optInt("target_count");
            if (target > 0 && item.optInt("progress") >= target && item.isNull("claimed_at")) {
                String slug = item.optString("slug", "");
                if (!slug.isEmpty()) {
                    claimPaths.add("/api/daily-challenges/" + slug + "/claim");
                    claimRewards.add(createClaimRewardSnapshot(item, true));
                }
            }
        }
        if (missions != null) for (int i = 0; i < missions.length(); i++) {
            JSONObject item = missions.optJSONObject(i);
            if (item == null || !item.isNull("claimed_at")) continue;
            int target = item.optInt("target_count");
            if (item.optBoolean("completed") || (target > 0 && item.optInt("progress") >= target)) {
                String slug = item.optString("slug", "");
                if (!slug.isEmpty()) {
                    claimPaths.add("/api/missions/" + slug + "/claim");
                    claimRewards.add(createClaimRewardSnapshot(item, false));
                }
            }
        }

        LinearLayout overview = sectionPanel();
        overview.setPadding(dp(12), dp(10), dp(12), dp(10));
        overview.setBackground(gradient(Color.rgb(37, 36, 65), Color.rgb(10, 16, 32),
                Color.rgb(121, 106, 191), dp(13)));
        LinearLayout overviewRow = new LinearLayout(this);
        overviewRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout overviewCopy = new LinearLayout(this);
        overviewCopy.setOrientation(LinearLayout.VERTICAL);
        overviewCopy.addView(text("QUEST BOARD", 7, GOLD_BRIGHT, true));
        overviewCopy.addView(text((missions == null ? 0 : missions.length())
                + " MISSIONS  ·  " + (challenges == null ? 0 : challenges.length()) + " DAILY",
                10, TEXT, true));
        overviewRow.addView(overviewCopy, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!claimPaths.isEmpty()) {
            TextView claimAll = button("✦  CLAIM ALL  ·  " + claimPaths.size(), true);
            claimAll.setTextSize(7);
            claimAll.setOnClickListener(v -> confirmAction("Claim all completed rewards?",
                    "The game will claim each eligible reward from the server one at a time.",
                    () -> {
                        claimAll.setEnabled(false);
                        claimAll.setText("CLAIMING…");
                        claimAllMissions(new ArrayList<>(claimPaths), new ArrayList<>(claimRewards),
                                0, 0, 0, claimAll, new JSONObject(), new ArrayList<>());
                    }));
            overviewRow.addView(claimAll, new LinearLayout.LayoutParams(dp(142), dp(36)));
        } else {
            TextView ready = text("NO REWARDS READY", 6, MUTED, true);
            ready.setGravity(Gravity.CENTER);
            overviewRow.addView(ready);
        }
        overview.addView(overviewRow);
        content.addView(overview, rowParams());

        if (challenges != null) {
            content.addView(text("DAILY CHALLENGES  ·  " + challenges.length(), 8, GOLD, true));
            addGap(content, 4);
            for (int i = 0; i < challenges.length(); i++) {
                JSONObject challenge = challenges.optJSONObject(i);
                if (challenge == null) continue;
                String slug = challenge.optString("slug", "");
                int progress = challenge.optInt("progress"), target = challenge.optInt("target_count");
                boolean claimed = !challenge.isNull("claimed_at");
                LinearLayout card = sectionPanel();
                card.addView(text(challenge.optString("title", "Daily challenge"), 11, TEXT, true));
                card.addView(text(challenge.optString("description", "Daily challenge"), 8, MUTED, false));
                ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(Math.max(1, target));
                bar.setProgress(Math.min(target, progress));
                bar.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(7));
                barParams.topMargin = dp(7);
                card.addView(bar, barParams);
                card.addView(text(progress + " / " + target + "  ·  "
                        + formatNumber(challenge.optLong("reward_coins")) + " GOLD  +  "
                        + formatNumber(challenge.optLong("reward_xp")) + " XP",
                        8, GOLD_BRIGHT, true));
                if (claimed) card.addView(text("REWARD CLAIMED", 7, GREEN, true));
                else if (target > 0 && progress >= target) {
                    TextView claim = button("CLAIM CHALLENGE REWARD", true);
                    claim.setOnClickListener(v -> claimChallenge(slug, claim));
                    card.addView(claim, new LinearLayout.LayoutParams(-1, dp(36)));
                } else card.addView(text("KEEP GOING TO UNLOCK THE REWARD", 7, MUTED, true));
                content.addView(card, rowParams());
            }
        }

        if (missions != null) {
            addGap(content, 5);
            content.addView(text("REALM MISSIONS  ·  " + missions.length(), 8, GOLD, true));
            for (int i = 0; i < missions.length(); i++) {
                JSONObject mission = missions.optJSONObject(i);
                if (mission == null) continue;
                String slug = mission.optString("slug", "");
                int progress = mission.optInt("progress"), target = mission.optInt("target_count");
                boolean claimed = !mission.isNull("claimed_at");
                boolean completed = mission.optBoolean("completed") || (target > 0 && progress >= target);
                LinearLayout card = sectionPanel();
                LinearLayout top = new LinearLayout(this);
                top.setGravity(Gravity.CENTER_VERTICAL);
                top.addView(text(mission.optString("title", mission.optString("name", "Mission")),
                        11, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1f));
                top.addView(text(humanize(mission.optString("period_type", "MISSION")),
                        7, VIOLET, true));
                card.addView(top);
                card.addView(text(mission.optString("description", "Collector objective"),
                        8, MUTED, false));
                ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(Math.max(1, target));
                bar.setProgress(Math.min(target, progress));
                bar.setProgressTintList(android.content.res.ColorStateList.valueOf(completed ? GREEN : GOLD));
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(7));
                barParams.topMargin = dp(7);
                card.addView(bar, barParams);
                String reward = formatNumber(mission.optLong("reward_coins")) + " GOLD  ·  "
                        + formatNumber(mission.optLong("reward_xp")) + " XP";
                if (mission.optInt("reward_pack_quantity") > 0)
                    reward += "  ·  +" + mission.optInt("reward_pack_quantity") + " PACK";
                card.addView(text(progress + " / " + target + "  ·  " + reward, 8, GOLD_BRIGHT, true));
                if (claimed) card.addView(text("REWARD CLAIMED", 7, GREEN, true));
                else if (completed) {
                    TextView claim = button("CLAIM MISSION REWARD", true);
                    claim.setOnClickListener(v -> claimMission(slug, claim));
                    card.addView(claim, new LinearLayout.LayoutParams(-1, dp(36)));
                }
                content.addView(card, rowParams());
            }
        }
        if ((missions == null || missions.length() == 0)
                && (challenges == null || challenges.length() == 0))
            showNotice("No active missions or daily challenges are available right now.");
    }

    private JSONObject createClaimRewardSnapshot(JSONObject item, boolean dailyChallenge) {
        JSONObject snapshot = new JSONObject();
        if (item == null) return snapshot;
        try {
            snapshot.put("title", item.optString("title",
                    item.optString("name", dailyChallenge ? "Daily challenge" : "Mission")));
            snapshot.put("coins", item.optLong("reward_coins"));
            snapshot.put("xp", item.optLong("reward_xp"));
            snapshot.put("pack", dailyChallenge ? 0 : item.optInt("reward_pack_quantity"));
            snapshot.put("gems", item.optLong("reward_gems"));
            snapshot.put("shards", item.optLong("reward_shards"));
        } catch (Exception ignored) {}
        return snapshot;
    }

    private void addClaimRewardTotal(JSONObject totals, String key, long amount) {
        if (amount <= 0) return;
        try { totals.put(key, totals.optLong(key) + amount); }
        catch (Exception ignored) {}
    }

    private String claimRewardDetail(String title, long coins, long xp,
                                     int packs, long gems, long shards) {
        StringBuilder detail = new StringBuilder(title);
        detail.append("  ·  ");
        StringBuilder items = new StringBuilder();
        if (coins > 0) items.append("+").append(formatNumber(coins)).append(" Gold");
        if (xp > 0) {
            if (items.length() > 0) items.append("  /  ");
            items.append("+").append(formatNumber(xp)).append(" XP");
        }
        if (packs > 0) {
            if (items.length() > 0) items.append("  /  ");
            items.append("+").append(packs).append(" Pack");
        }
        if (gems > 0) {
            if (items.length() > 0) items.append("  /  ");
            items.append("+").append(formatNumber(gems)).append(" Gems");
        }
        if (shards > 0) {
            if (items.length() > 0) items.append("  /  ");
            items.append("+").append(formatNumber(shards)).append(" Shards");
        }
        detail.append(items.length() == 0 ? "Reward claimed" : items.toString());
        return detail.toString();
    }

    private LinearLayout claimMetric(String label, String value, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(9), dp(11), dp(9));
        card.setBackground(gradient(Color.rgb(27, 37, 58), Color.rgb(13, 20, 37),
                accent, dp(11)));
        String symbol=label.contains("GOLD")?"◈":label.contains("XP")?"✦":label.contains("PACK")?"▣":label.contains("GEMS")?"✧":"◆";
        card.addView(text(symbol, 19, accent, true));
        card.addView(text(label, 9, accent, true));
        TextView amount = text(value, 17, TEXT, true);
        amount.setPadding(0, dp(2), 0, 0);
        card.addView(amount);
        return card;
    }

    private void showClaimAllSummaryDialog(int succeeded, int failed,
                                           JSONObject totals, ArrayList<String> details) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(12));
        scroll.addView(body);

        TextView stamp = text("✦  REWARDS ADDED TO YOUR ACCOUNT", 8, GOLD_BRIGHT, true);
        stamp.setGravity(Gravity.CENTER);
        body.addView(stamp);
        TextView count = text(succeeded + " REWARD" + (succeeded == 1 ? "" : "S") + " CLAIMED",
                16, TEXT, true);
        count.setGravity(Gravity.CENTER);
        count.setPadding(0, dp(5), 0, dp(3));
        body.addView(count);
        if (failed > 0) {
            TextView warning = text(failed + " claim(s) could not be completed. Only successful rewards are included below.",
                    8, PINK, false);
            warning.setGravity(Gravity.CENTER);
            body.addView(warning);
        }

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.addView(claimMetric("TOTAL GOLD", formatNumber(totals.optLong("coins")), GOLD_BRIGHT),
                weightParams(1));
        firstRow.addView(claimMetric("TOTAL XP", formatNumber(totals.optLong("xp")), CYAN),
                weightParams(1));
        LinearLayout.LayoutParams metricRowParams = new LinearLayout.LayoutParams(-1, -2);
        metricRowParams.topMargin = dp(10);
        body.addView(firstRow, metricRowParams);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        secondRow.addView(claimMetric("SUMMON PACKS", formatNumber(totals.optLong("pack")), PINK),
                weightParams(1));
        secondRow.addView(claimMetric("GEMS", formatNumber(totals.optLong("gems")), VIOLET),
                weightParams(1));
        secondRow.addView(claimMetric("SHARDS", formatNumber(totals.optLong("shards")), BLUE),
                weightParams(1));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(-1, -2);
        secondParams.topMargin = dp(6);
        body.addView(secondRow, secondParams);

        addGap(body, 11);
        body.addView(text("CLAIM BREAKDOWN", 8, GOLD_BRIGHT, true));
        if (details == null || details.isEmpty()) {
            body.addView(text("No individual reward details were returned.", 8, MUTED, false));
        } else {
            for (String item : details) {
                LinearLayout row = sectionPanel();
                row.setPadding(dp(10), dp(8), dp(10), dp(8));
                TextView line = text("✓  " + item, 8, TEXT, false);
                line.setLineSpacing(dp(2), 1.02f);
                row.addView(line);
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, -2);
                lineParams.topMargin = dp(5);
                body.addView(row, lineParams);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .setPositiveButton("DONE", null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(gradient(Color.rgb(11, 17, 32),
                    Color.rgb(8, 13, 26), Color.rgb(100, 89, 139), dp(16)));
            dialog.getWindow().setLayout(
                    Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(440)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(GOLD_BRIGHT);
        if(!reduceMotionEnabled){firstRow.setAlpha(0f);firstRow.setTranslationY(dp(10));firstRow.animate().alpha(1f).translationY(0f).setDuration(260).start();secondRow.setAlpha(0f);secondRow.setTranslationY(dp(10));secondRow.animate().alpha(1f).translationY(0f).setStartDelay(120).setDuration(260).start();}
    }

    private void claimAllMissions(ArrayList<String> paths, ArrayList<JSONObject> plannedRewards,
                                  int index, int succeeded, int failed, TextView button,
                                  JSONObject totals, ArrayList<String> claimedDetails) {
        if (index >= paths.size()) {
            button.setEnabled(true);
            button.setText("CLAIM ALL AVAILABLE");
            showClaimAllSummaryDialog(succeeded, failed, totals, claimedDetails);
            refreshAccount(() -> renderTab("missions"));
            return;
        }
        button.setText("CLAIMING " + (index + 1) + " / " + paths.size());
        api.post(paths.get(index), new JSONObject(), result -> ui.post(() -> {
            int nextSucceeded = succeeded;
            int nextFailed = failed;
            if (result.ok()) {
                nextSucceeded++;
                JSONObject planned = index < plannedRewards.size()
                        ? plannedRewards.get(index) : new JSONObject();
                JSONObject actual = result.data == null ? null : result.data.optJSONObject("reward");
                JSONObject reward = actual == null ? planned : actual;
                long coins = reward.optLong("coins", planned.optLong("coins"));
                long xp = reward.optLong("xp", planned.optLong("xp"));
                int packs = reward.optInt("pack", planned.optInt("pack"));
                long gems = reward.optLong("gems", planned.optLong("gems"));
                long shards = reward.optLong("shards", planned.optLong("shards"));
                addClaimRewardTotal(totals, "coins", coins);
                addClaimRewardTotal(totals, "xp", xp);
                addClaimRewardTotal(totals, "pack", packs);
                addClaimRewardTotal(totals, "gems", gems);
                addClaimRewardTotal(totals, "shards", shards);
                claimedDetails.add(claimRewardDetail(planned.optString("title", "Mission reward"),
                        coins, xp, packs, gems, shards));
            } else {
                nextFailed++;
            }
            claimAllMissions(paths, plannedRewards, index + 1, nextSucceeded, nextFailed,
                    button, totals, claimedDetails);
        }));
    }
    private void renderShop(JSONObject data) {
        JSONArray items = data.optJSONArray("items");
        JSONObject wallet = data.optJSONObject("wallet");
        if (items == null) { renderObjectSummary(data, "SHOP INVENTORY"); return; }
        if (wallet == null) wallet = new JSONObject();

        addRealmScene("market", "THE FIRSTLIGHT EXCHANGE",
                "A rotating catalogue of summon seals, card items and collector supplies.",
                items.length(), Math.max(1, items.length()));

        LinearLayout hero = sectionPanel();
        hero.setBackground(gradient(Color.rgb(56, 43, 78), Color.rgb(11, 17, 33), GOLD, dp(15)));
        hero.addView(text("FIRSTLIGHT MARKET · LIVE CATALOGUE", 8, GOLD_BRIGHT, true));
        hero.addView(text("Find your next upgrade", 18, TEXT, true));
        hero.addView(text(items.length() + " listed item(s) are supplied by the game server. Prices, stock and purchase limits are checked when you buy.",
                8, MUTED, false));
        LinearLayout balances = new LinearLayout(this);
        balances.setOrientation(LinearLayout.HORIZONTAL);
        balances.addView(statCard("GOLD", formatNumber(wallet.optLong("coins")), "Available"), weightParams(1));
        balances.addView(statCard("GEMS", formatNumber(wallet.optLong("gems")), "Premium"), weightParams(1));
        balances.addView(statCard("SHARDS", formatNumber(wallet.optLong("shards")), "Upgrade"), weightParams(1));
        hero.addView(balances);
        content.addView(hero, rowParams());

        final String[] filters = {"ALL", "PACKS", "CARDS", "COSMETICS", "SUPPLIES"};
        final int[] currentFilter = {0};
        LinearLayout filterPanel = sectionPanel();
        filterPanel.addView(text("BROWSE BY CATEGORY", 7, GOLD_BRIGHT, true));
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        TextView[] chips = new TextView[filters.length];
        for (int i = 0; i < filters.length; i++) {
            final int index = i;
            TextView chip = button(filters[i], i == 0);
            chip.setTextSize(8);
            chips[i] = chip;
            chip.setOnClickListener(v -> {
                currentFilter[0] = index;
                for (int j = 0; j < chips.length; j++) {
                    chips[j].setBackground(gradient(j == index
                            ? Color.rgb(213, 169, 91) : Color.rgb(26, 36, 56),
                            Color.rgb(13, 20, 35), j == index ? GOLD_BRIGHT
                                    : Color.rgb(67, 87, 117), dp(8)));
                    chips[j].setTextColor(j == index ? BG : TEXT);
                }
                renderShopItems(items, currentFilter[0], results);
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(37));
            cp.rightMargin = dp(6);
            filterRow.addView(chip, cp);
        }
        filterScroll.addView(filterRow);
        filterPanel.addView(filterScroll);
        content.addView(filterPanel, rowParams());

        content.addView(results);
        renderShopItems(items, 0, results);
    }

    private void renderShopItems(JSONArray items, int filter, LinearLayout holder) {
        holder.removeAllViews();
        java.util.ArrayList<JSONObject> visible = new java.util.ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String type = item.optString("item_type", "ITEM").toUpperCase(java.util.Locale.ROOT);
            boolean match = filter == 0
                    || (filter == 1 && type.contains("PACK"))
                    || (filter == 2 && type.contains("CARD"))
                    || (filter == 3 && type.contains("COSMETIC"))
                    || (filter == 4 && !type.contains("PACK") && !type.contains("CARD")
                        && !type.contains("COSMETIC"));
            if (match) visible.add(item);
        }
        TextView count = text(filtersLabel(filter) + "  ·  " + visible.size() + " ITEM(S)",
                8, GOLD_BRIGHT, true);
        count.setPadding(dp(2), dp(2), 0, dp(8));
        holder.addView(count);

        if (visible.isEmpty()) {
            LinearLayout empty = sectionPanel();
            empty.setGravity(Gravity.CENTER);
            empty.addView(text("NO ITEMS IN THIS CATEGORY", 9, TEXT, true));
            empty.addView(text("Try another market category.", 8, MUTED, false));
            holder.addView(empty, rowParams());
            return;
        }

        int tileWidth = Math.max(dp(132), Math.min(dp(188),
                (getResources().getDisplayMetrics().widthPixels - dp(62)) / 2));
        int tileHeight = dp(242);
        for (int i = 0; i < visible.size(); i += 2) {
            LinearLayout gridRow = new LinearLayout(this);
            gridRow.setOrientation(LinearLayout.HORIZONTAL);
            gridRow.setGravity(Gravity.TOP);
            for (int column = 0; column < 2; column++) {
                int index = i + column;
                if (index >= visible.size()) {
                    View spacer = new View(this);
                    gridRow.addView(spacer, new LinearLayout.LayoutParams(tileWidth, tileHeight));
                    continue;
                }
                JSONObject item = visible.get(index);
                String itemId = item.optString("item_id", "");
                String title = item.optString("title", item.optString("name", "Realm item"));
                String description = item.optString("description",
                        item.optString("subtitle", item.optString("item_type", "REALM ITEM")));
                String currency = item.optString("currency", "COINS")
                        .toUpperCase(java.util.Locale.ROOT);
                long price = item.optLong("price");
                long stock = item.isNull("stock_remaining") ? -1 : item.optLong("stock_remaining");
                boolean cosmeticOwned = item.optInt("cosmetic_owned") == 1;
                boolean alreadyCollected = item.optLong("card_owned") > 0;
                int purchaseLimit = item.optInt("purchase_limit");
                int purchasedCount = item.optInt("purchased_count");
                boolean limitReached = purchaseLimit > 0 && purchasedCount >= purchaseLimit;
                boolean owned = cosmeticOwned || limitReached;
                boolean canBuy = !owned && stock != 0 && price >= 0 && !itemId.isEmpty();
                int accent = owned ? GREEN : "GEMS".equals(currency) ? VIOLET
                        : "SHARDS".equals(currency) ? CYAN : GOLD;

                LinearLayout tile = sectionPanel();
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER_HORIZONTAL);
                tile.setPadding(dp(9), dp(8), dp(9), dp(8));
                tile.setBackground(gradient(
                        owned ? Color.rgb(29, 42, 46) : Color.rgb(34, 37, 58),
                        Color.rgb(10, 16, 31), accent, dp(13)));

                FrameLayout art = new FrameLayout(this);
                art.setBackground(gradient(Color.rgb(Color.red(accent) / 4 + 16,
                                Color.green(accent) / 4 + 16, Color.blue(accent) / 4 + 21),
                        Color.rgb(8, 13, 26), accent, dp(10)));
                TextView glyph = text(item.optString("item_type", "ITEM")
                                .toUpperCase(java.util.Locale.ROOT).contains("PACK") ? "✧"
                                : item.optString("item_type", "").toUpperCase(java.util.Locale.ROOT)
                                        .contains("CARD") ? "▧"
                                : item.optString("item_type", "").toUpperCase(java.util.Locale.ROOT)
                                        .contains("COSMETIC") ? "✦" : "◇",
                        25, accent, true);
                glyph.setGravity(Gravity.CENTER);
                art.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
                LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(dp(58), dp(58));
                artParams.bottomMargin = dp(4);
                tile.addView(art, artParams);

                TextView category = text(item.optString("item_type", "ITEM").toUpperCase(
                                java.util.Locale.ROOT) + "  ·  " + currency,
                        6, accent, true);
                category.setGravity(Gravity.CENTER);
                category.setMaxLines(1);
                tile.addView(category);

                TextView name = text(title, 10, TEXT, true);
                name.setGravity(Gravity.CENTER);
                name.setMaxLines(2);
                name.setMinLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
                nameParams.topMargin = dp(2);
                tile.addView(name, nameParams);

                TextView details = text(description, 7, MUTED, false);
                details.setGravity(Gravity.CENTER);
                details.setMaxLines(1);
                details.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tile.addView(details);

                TextView cost = text(formatNumber(price) + " " + currency, 9, GOLD_BRIGHT, true);
                cost.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams costParams = new LinearLayout.LayoutParams(-1, -2);
                costParams.topMargin = dp(3);
                tile.addView(cost, costParams);

                String stockLabel = stock < 0 ? "OPEN STOCK" : stock == 0 ? "SOLD OUT"
                        : formatNumber(stock) + " LEFT";
                TextView stockView = text(stockLabel, 6,
                        stock == 0 ? RED : MUTED, true);
                stockView.setGravity(Gravity.CENTER);
                tile.addView(stockView);

                if (cosmeticOwned || limitReached) {
                    TextView status = text(cosmeticOwned ? "✓  OWNED" : "LIMIT REACHED",
                            7, cosmeticOwned ? GREEN : MUTED, true);
                    status.setGravity(Gravity.CENTER);
                    status.setPadding(0, dp(5), 0, 0);
                    tile.addView(status, new LinearLayout.LayoutParams(-1, dp(26)));
                } else {
                    TextView buy = button(canBuy ? "PURCHASE" : stock == 0 ? "SOLD OUT" : "LOCKED",
                            canBuy);
                    buy.setEnabled(canBuy);
                    buy.setTextSize(7);
                    LinearLayout.LayoutParams buyParams = new LinearLayout.LayoutParams(-1, dp(36));
                    buyParams.topMargin = dp(3);
                    tile.addView(buy, buyParams);
                    buy.setOnClickListener(v -> confirmPurchaseAction("Purchase " + title + "?",
                            "Cost: " + formatNumber(price) + " " + currency
                                    + ". Stock and purchase limits are verified securely by the server.",
                            () -> {
                        JSONObject request = payload("itemId", itemId,
                                "requestId", newActionId(), "quantity", 1);
                        buy.setEnabled(false);
                        buy.setText("PROCESSING…");
                        api.post("/api/shop/purchase", request, result -> ui.post(() -> {
                            if (!result.ok()) {
                                buy.setEnabled(canBuy);
                                buy.setText("PURCHASE");
                                if (sfxEnabled && gameAudio != null) gameAudio.playError();
                                showResultDialog("Purchase failed", result.error);
                            } else {
                                if (sfxEnabled && gameAudio != null) gameAudio.playSuccess();
                                showResultDialog("PURCHASE COMPLETE",
                                        result.data.optString("message",
                                                "Your item has been delivered."));
                                refreshAccount(() -> renderTab("shop"));
                            }
                        }));
                    }));
                }
                if (alreadyCollected && !cosmeticOwned && !limitReached) {
                    TextView ownedHint = text("IN YOUR BINDER", 6, CYAN, true);
                    ownedHint.setGravity(Gravity.CENTER);
                    tile.addView(ownedHint);
                }

                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(tileWidth, tileHeight);
                if (column == 0) tileParams.rightMargin = dp(8);
                gridRow.addView(tile, tileParams);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, tileHeight);
            rowParams.bottomMargin = dp(8);
            holder.addView(gridRow, rowParams);
        }
    }

    private String filtersLabel(int filter) {
        String[] labels = {"ALL ITEMS", "SUMMON PACKS", "CARD ITEMS", "COSMETICS", "SUPPLIES"};
        return labels[Math.max(0, Math.min(labels.length - 1, filter))];
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
        boolean inGuild = guild != null;
        String role = inGuild ? guild.optString("my_role", "MEMBER").toUpperCase(java.util.Locale.ROOT) : "";
        boolean leader = "LEADER".equals(role);
        boolean canManageMembers = leader || (inGuild && guild.optBoolean("canManageMembers"));
        boolean canEditIdentity = leader || (inGuild && guild.optBoolean("canEditIdentity"));
        boolean canManageGuildSettings = leader || (inGuild && (guild.optBoolean("canAnnounce")
                || guild.optBoolean("canManageRecruitment") || canEditIdentity));

        if (inGuild) addPhase13Link("Guild Cooperative Activities",
                "Shared Boss, weekly missions, contribution ranking and season rewards.", () -> renderTab("guild_coop"));
        if (!inGuild) {
            LinearLayout intro = sectionPanel();
            intro.setGravity(Gravity.CENTER);
            intro.setPadding(dp(17), dp(18), dp(17), dp(17));
            intro.setBackground(gradient(Color.rgb(33, 65, 70), Color.rgb(8, 17, 32), CYAN, dp(17)));
            FrameLayout crest = new FrameLayout(this);
            crest.setBackground(gradient(Color.rgb(55, 112, 98), Color.rgb(11, 28, 39), CYAN, dp(14)));
            TextView sigil = text("♜", 42, Color.rgb(197, 255, 226), true);
            sigil.setGravity(Gravity.CENTER);
            crest.addView(sigil, new FrameLayout.LayoutParams(-1, -1));
            intro.addView(crest, new LinearLayout.LayoutParams(dp(78), dp(78)));
            addGap(intro, 8);
            intro.addView(text("FIND YOUR GUILD", 8, CYAN, true));
            intro.addView(text("A house for every collector.", 19, TEXT, true));
            TextView sub = text("Create a guild, recruit players and build a shared legacy in the Firstlight Realm.",
                    8, MUTED, false);
            sub.setGravity(Gravity.CENTER);
            intro.addView(sub);
            TextView create = button("＋  CREATE YOUR GUILD", true);
            create.setOnClickListener(v -> showCreateGuildDialog());
            LinearLayout.LayoutParams createP = new LinearLayout.LayoutParams(dp(210), dp(40));
            createP.topMargin = dp(10);
            intro.addView(create, createP);
            content.addView(intro, rowParams());
        } else {
            LinearLayout hero = sectionPanel();
            hero.setPadding(dp(13), dp(13), dp(13), dp(12));
            hero.setBackground(gradient(Color.rgb(31, 75, 69), Color.rgb(8, 17, 32),
                    Color.rgb(109, 219, 177), dp(17)));
            LinearLayout identity = new LinearLayout(this);
            identity.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout crest = new FrameLayout(this);
            crest.setBackground(gradient(Color.rgb(70, 137, 108), Color.rgb(14, 34, 43),
                    Color.rgb(133, 238, 190), dp(12)));
            TextView emblem = text(guild.optString("emblem", "✦"), 38, Color.rgb(218, 255, 235), true);
            emblem.setGravity(Gravity.CENTER);
            crest.addView(emblem, new FrameLayout.LayoutParams(-1, -1));
            identity.addView(crest, new LinearLayout.LayoutParams(dp(66), dp(68)));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(dp(10), 0, 0, 0);
            copy.addView(text("[" + guild.optString("tag", "GUILD") + "]  ·  " + role, 7, CYAN, true));
            TextView name = text(guild.optString("name", "Guild"), 19, TEXT, true);
            name.setMaxLines(2);
            copy.addView(name);
            String motto = guild.optString("motto", "").trim();
            if (!motto.isEmpty()) {
                TextView mottoText = text("“" + motto + "”", 8, GOLD_BRIGHT, false);
                mottoText.setMaxLines(2);
                copy.addView(mottoText);
            }
            TextView leaderName = text("LED BY " + guild.optString("leader_name", "Guild Leader"),
                    7, MUTED, true);
            leaderName.setPadding(0, dp(3), 0, 0);
            copy.addView(leaderName);
            identity.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
            hero.addView(identity);

            TextView description = text(guild.optString("description", "A community of collectors."),
                    8, MUTED, false);
            description.setLineSpacing(dp(2), 1.04f);
            description.setMaxLines(3);
            description.setPadding(0, dp(9), 0, dp(9));
            hero.addView(description);

            LinearLayout stats = new LinearLayout(this);
            stats.setOrientation(LinearLayout.HORIZONTAL);
            stats.addView(statCard("GUILD LEVEL", String.valueOf(guild.optInt("guild_level")), "Realm progress"), weightParams(1));
            stats.addView(statCard("ROSTER", guild.optInt("member_count") + "/" + guild.optInt("member_limit"), "Collectors"), weightParams(1));
            stats.addView(statCard("TREASURY", formatNumber(guild.optLong("bank_coins")), "Gold"), weightParams(1));
            hero.addView(stats);

            LinearLayout status = new LinearLayout(this);
            status.setGravity(Gravity.CENTER_VERTICAL);
            status.addView(text(guild.optInt("is_recruiting", 1) == 1 ? "●  RECRUITING OPEN" : "●  RECRUITING CLOSED",
                    7, guild.optInt("is_recruiting", 1) == 1 ? GREEN : MUTED, true),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            status.addView(text("MIN LEVEL " + Math.max(1, guild.optInt("minimum_level", 1))
                    + "  ·  " + role, 7, GOLD_BRIGHT, true));
            hero.addView(status);
            content.addView(hero, rowParams());

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            TextView contribute = button("＋  CONTRIBUTE GOLD", true);
            contribute.setOnClickListener(v -> chooseGuildContribution());
            actions.addView(contribute, new LinearLayout.LayoutParams(0, dp(40), 1f));
            if (canManageGuildSettings) {
                TextView settings = button("GUILD SETTINGS", false);
                settings.setOnClickListener(v -> showGuildSettingsDialog(guild));
                LinearLayout.LayoutParams settingsP = new LinearLayout.LayoutParams(0, dp(40), 1f);
                settingsP.leftMargin = dp(7);
                actions.addView(settings, settingsP);
            }
            LinearLayout.LayoutParams actionsP = new LinearLayout.LayoutParams(-1, -2);
            actionsP.topMargin = dp(7);
            content.addView(actions, actionsP);

            String announcement = guild.optString("announcement", "").trim();
            if (!announcement.isEmpty()) {
                LinearLayout notice = sectionPanel();
                notice.setPadding(dp(12), dp(10), dp(12), dp(10));
                notice.setBackground(gradient(Color.rgb(39, 38, 69), Color.rgb(12, 18, 36), VIOLET, dp(12)));
                notice.addView(text("GUILD NOTICE", 7, GOLD_BRIGHT, true));
                TextView noticeText = text(announcement, 9, TEXT, false);
                noticeText.setLineSpacing(dp(2), 1.02f);
                noticeText.setMaxLines(5);
                notice.addView(noticeText);
                content.addView(notice, rowParams());
            }

            JSONArray members = guild.optJSONArray("members");
            if (members != null) {
                LinearLayout rosterHead = new LinearLayout(this);
                rosterHead.setGravity(Gravity.CENTER_VERTICAL);
                rosterHead.addView(text("GUILD ROSTER", 8, GOLD_BRIGHT, true),
                        new LinearLayout.LayoutParams(0, -2, 1f));
                rosterHead.addView(text(members.length() + " / " + guild.optInt("member_limit")
                        + " MEMBERS", 7, MUTED, true));
                content.addView(rosterHead, rowParams());
                for (int i = 0; i < members.length(); i++) {
                    JSONObject member = members.optJSONObject(i);
                    if (member == null) continue;
                    String memberRole = member.optString("role", "MEMBER").toUpperCase(java.util.Locale.ROOT);
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(9), dp(8), dp(9), dp(8));
                    row.setBackground(gradient(Color.rgb(23, 34, 52), Color.rgb(10, 17, 32),
                            Color.rgb(62, 85, 105), dp(10)));
                    TextView avatar = text(member.optString("display_name", "C").trim().isEmpty()
                            ? "C" : member.optString("display_name", "C").trim().substring(0, 1)
                            .toUpperCase(java.util.Locale.ROOT), 13, CYAN, true);
                    avatar.setGravity(Gravity.CENTER);
                    avatar.setBackground(gradient(Color.rgb(36, 65, 76), Color.rgb(14, 26, 40), CYAN, dp(8)));
                    row.addView(avatar, new LinearLayout.LayoutParams(dp(35), dp(35)));
                    LinearLayout meta = new LinearLayout(this);
                    meta.setOrientation(LinearLayout.VERTICAL);
                    meta.setPadding(dp(8), 0, dp(4), 0);
                    TextView memberName = text(member.optString("display_name", "Collector"), 9, TEXT, true);
                    memberName.setMaxLines(1);
                    meta.addView(memberName);
                    meta.addView(text("LV " + member.optInt("collector_level") + "  ·  "
                            + formatNumber(member.optLong("contribution")) + " GOLD CONTRIBUTED", 6, MUTED, true));
                    row.addView(meta, new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView rolePill = text(memberRole, 6,
                            "LEADER".equals(memberRole) ? GOLD_BRIGHT
                                    : "OFFICER".equals(memberRole) ? VIOLET : CYAN, true);
                    rolePill.setGravity(Gravity.CENTER);
                    rolePill.setPadding(dp(7), dp(5), dp(7), dp(5));
                    rolePill.setBackground(gradient(Color.rgb(29, 38, 56), Color.rgb(14, 22, 38),
                            rolePill.getCurrentTextColor(), dp(7)));
                    row.addView(rolePill);
                    if (canManageMembers && !"LEADER".equals(memberRole)) {
                        TextView manage = button("•••", false);
                        manage.setTextSize(10);
                        manage.setOnClickListener(v -> showGuildMemberRoleDialog(member));
                        LinearLayout.LayoutParams manageP = new LinearLayout.LayoutParams(dp(38), dp(33));
                        manageP.leftMargin = dp(5);
                        row.addView(manage, manageP);
                    }
                    LinearLayout.LayoutParams rowP = rowParams();
                    rowP.bottomMargin = dp(5);
                    content.addView(row, rowP);
                }
            }

            if (!leader) {
                TextView leave = button("LEAVE GUILD", false);
                leave.setOnClickListener(v -> confirmAction("Leave [" + guild.optString("tag", "GUILD") + "]?",
                        "You will leave this guild roster. Your past contribution remains in guild history.",
                        () -> api.post("/api/guilds/leave", new JSONObject(), result -> ui.post(() -> {
                            if (!result.ok()) showResultDialog("Unable to leave guild", result.error);
                            else { showResultDialog("GUILD LEFT", "You have left the guild."); refreshAccount(() -> renderTab("guild")); }
                        }))));
                LinearLayout.LayoutParams leaveP = new LinearLayout.LayoutParams(dp(145), dp(36));
                leaveP.topMargin = dp(5);
                content.addView(leave, leaveP);
            }

            JSONArray activity = guild.optJSONArray("activity");
            if (activity != null && activity.length() > 0) {
                content.addView(text("RECENT GUILD ACTIVITY", 8, GOLD_BRIGHT, true), rowParams());
                for (int i = 0; i < Math.min(6, activity.length()); i++) {
                    JSONObject event = activity.optJSONObject(i);
                    if (event == null) continue;
                    content.addView(dataRow(humanize(event.optString("event_type", "GUILD EVENT")),
                            event.optString("display_name", "Guild member") + "  ·  "
                                    + event.optString("created_at", ""), "ACTIVITY"), rowParams());
                }
            }
        }

        JSONArray directory = data.optJSONArray("directory");
        if (directory != null && directory.length() > 0) {
            LinearLayout directoryTitle = new LinearLayout(this);
            directoryTitle.setGravity(Gravity.CENTER_VERTICAL);
            directoryTitle.addView(text(inGuild ? "OTHER GUILDS RECRUITING" : "RECRUITING GUILDS",
                    8, GOLD_BRIGHT, true), new LinearLayout.LayoutParams(0, -2, 1f));
            directoryTitle.addView(text(directory.length() + " OPEN", 7, MUTED, true));
            content.addView(directoryTitle, rowParams());
            int playerLevel = me == null ? 1 : me.optInt("collector_level", 1);
            for (int i = 0; i < directory.length(); i++) {
                JSONObject entry = directory.optJSONObject(i);
                if (entry == null) continue;
                int minLevel = Math.max(1, entry.optInt("minimum_level", 1));
                int memberCount = entry.optInt("member_count");
                int memberLimit = entry.optInt("member_limit", 30);
                boolean eligible = playerLevel >= minLevel && memberCount < memberLimit;
                LinearLayout card = sectionPanel();
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                FrameLayout badge = new FrameLayout(this);
                badge.setBackground(gradient(Color.rgb(41, 78, 67), Color.rgb(12, 27, 36), GREEN, dp(10)));
                TextView tag = text(entry.optString("emblem", "✦") + "\n" + entry.optString("tag", "GUILD"),
                        8, CYAN, true);
                tag.setGravity(Gravity.CENTER);
                badge.addView(tag, new FrameLayout.LayoutParams(-1, -1));
                card.addView(badge, new LinearLayout.LayoutParams(dp(58), dp(62)));
                LinearLayout copy = new LinearLayout(this);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.setPadding(dp(9), 0, dp(5), 0);
                copy.addView(text(entry.optString("name", "Guild"), 11, TEXT, true));
                String motto = entry.optString("motto", "").trim();
                if (!motto.isEmpty()) copy.addView(text(motto, 7, MUTED, false));
                copy.addView(text("LV " + entry.optInt("guild_level") + "  ·  "
                        + memberCount + "/" + memberLimit + " MEMBERS  ·  MIN LV " + minLevel,
                        6, GOLD_BRIGHT, true));
                copy.addView(text("LEADER · " + entry.optString("leader_name", "Unknown"), 6, MUTED, false));
                card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView join = button(eligible ? "JOIN" : (playerLevel < minLevel ? "LV " + minLevel : "FULL"), eligible);
                join.setEnabled(eligible);
                join.setOnClickListener(v -> confirmAction("Join [" + entry.optString("tag") + "]?",
                        "You can only belong to one guild at a time.", () -> joinGuild(entry.optString("tag"))));
                card.addView(join, new LinearLayout.LayoutParams(dp(70), dp(36)));
                content.addView(card, rowParams());
            }
        } else if (!inGuild) {
            showNotice("No guilds are recruiting right now. You can found the first one.");
        }
    }

    private android.widget.CheckBox guildPermissionToggle(String label, boolean checked,
                                                            boolean enabled, int accent) {
        android.widget.CheckBox toggle = new android.widget.CheckBox(this);
        toggle.setText(label);
        toggle.setTextColor(enabled ? TEXT : MUTED);
        toggle.setTextSize(9);
        toggle.setPadding(dp(2), dp(5), dp(2), dp(5));
        toggle.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{accent, Color.rgb(100, 111, 132)}));
        toggle.setChecked(checked);
        toggle.setEnabled(enabled);
        return toggle;
    }

    private int parseGuildSetting(EditText input, int minValue, int maxValue) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value < minValue || value > maxValue) {
                input.setError("Enter a value from " + minValue + " to " + maxValue + ".");
                return -1;
            }
            input.setError(null);
            return value;
        } catch (Exception ignored) {
            if (input != null) input.setError("Enter a valid whole number.");
            return -1;
        }
    }

    private void showGuildSettingsDialog(JSONObject guild) {
        boolean leader = "LEADER".equalsIgnoreCase(guild.optString("my_role", "MEMBER"));
        boolean canAnnounce = leader || guild.optBoolean("canAnnounce");
        boolean canRecruit = leader || guild.optBoolean("canManageRecruitment");
        boolean canManageMembers = leader || guild.optBoolean("canManageMembers");
        boolean canEditIdentity = leader || guild.optBoolean("canEditIdentity");
        boolean canSave = canAnnounce || canRecruit || canEditIdentity;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(13), dp(12), dp(13), dp(10));

        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(11), dp(10), dp(11), dp(10));
        hero.setBackground(gradient(Color.rgb(34,66,67),Color.rgb(9,19,34),CYAN,dp(13)));
        FrameLayout crest = new FrameLayout(this);
        crest.setBackground(gradient(Color.rgb(64,122,99),Color.rgb(13,33,44),GREEN,dp(10)));
        TextView emblemPreview=text(guild.optString("emblem","✦"),29,Color.rgb(215,255,234),true);
        emblemPreview.setGravity(Gravity.CENTER);
        crest.addView(emblemPreview,new FrameLayout.LayoutParams(-1,-1));
        hero.addView(crest,new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout titleBox=new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(10),0,0,0);
        titleBox.addView(text("GUILD CONTROL ROOM",7,CYAN,true));
        titleBox.addView(text(guild.optString("name","Guild"),15,TEXT,true));
        titleBox.addView(text("["+guild.optString("tag","GUILD")+"] · "+(leader?"LEADER":"OFFICER"),7,GOLD_BRIGHT,true));
        hero.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1f));
        form.addView(hero);

        String rights=(canEditIdentity?"IDENTITY":"")+(canAnnounce?(canEditIdentity?" · ":"")+"ANNOUNCEMENTS":"")
                +(canRecruit?((canEditIdentity||canAnnounce)?" · ":"")+"RECRUITMENT":"")
                +(canManageMembers?((canEditIdentity||canAnnounce||canRecruit)?" · ":"")+"ROSTER":"");
        LinearLayout access=sectionPanel();access.setPadding(dp(10),dp(8),dp(10),dp(8));
        access.setBackground(gradient(Color.rgb(27,42,61),Color.rgb(11,18,33),leader?VIOLET:CYAN,dp(10)));
        access.addView(text(leader?"LEADER ACCESS":"YOUR OFFICER ACCESS",7,leader?VIOLET:CYAN,true));
        access.addView(text(rights.isEmpty()?"No settings permissions are currently delegated.":"You can edit: "+rights+".",8,MUTED,false));
        LinearLayout.LayoutParams accessP=rowParams();accessP.topMargin=dp(8);form.addView(access,accessP);

        String[] emblems={"✦","♜","⚔","✧","◈","❖","☼"};
        Spinner emblemPicker=new Spinner(this);
        ArrayAdapter<String> emblemAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,emblems);
        emblemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        emblemPicker.setAdapter(emblemAdapter);
        for(int i=0;i<emblems.length;i++)if(emblems[i].equals(guild.optString("emblem","✦")))emblemPicker.setSelection(i);
        emblemPicker.setBackground(gradient(Color.rgb(20,31,50),Color.rgb(9,15,28),Color.rgb(73,121,112),dp(9)));
        emblemPicker.setEnabled(canEditIdentity);

        LinearLayout identity=sectionPanel();identity.addView(text("01  ·  GUILD IDENTITY",7,GOLD_BRIGHT,true));addGap(identity,5);
        identity.addView(text("EMBLEM",7,MUTED,true));identity.addView(emblemPicker,new LinearLayout.LayoutParams(-1,dp(40)));
        EditText motto=editField("Short motto for the public guild card",false);motto.setSingleLine(true);
        motto.setText(guild.optString("motto",""));motto.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(72)});
        motto.setEnabled(canEditIdentity);
        TextView mottoLabel=text("MOTTO  ·  72 CHARACTERS MAX",7,MUTED,true);mottoLabel.setPadding(0,dp(7),0,dp(3));
        identity.addView(mottoLabel);identity.addView(motto,fieldParams());
        EditText description=editField("Explain your guild to new collectors",false);
        description.setSingleLine(false);description.setMinLines(2);description.setMaxLines(3);
        description.setText(guild.optString("description",""));
        description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});
        description.setEnabled(canEditIdentity);
        TextView descLabel=text("PUBLIC DESCRIPTION  ·  240 CHARACTERS MAX",7,MUTED,true);descLabel.setPadding(0,dp(7),0,dp(3));
        identity.addView(descLabel);identity.addView(description,new LinearLayout.LayoutParams(-1,dp(74)));
        LinearLayout.LayoutParams identityP=rowParams();identityP.topMargin=dp(8);form.addView(identity,identityP);

        LinearLayout notice=sectionPanel();notice.addView(text("02  ·  ANNOUNCEMENT BOARD",7,GOLD_BRIGHT,true));addGap(notice,5);
        EditText announcement=editField("Notice shown to every guild member",false);
        announcement.setSingleLine(false);announcement.setMinLines(2);announcement.setMaxLines(3);
        announcement.setText(guild.optString("announcement",""));
        announcement.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});
        announcement.setEnabled(canAnnounce);notice.addView(announcement,new LinearLayout.LayoutParams(-1,dp(76)));
        LinearLayout.LayoutParams noticeP=rowParams();noticeP.topMargin=dp(8);form.addView(notice,noticeP);

        LinearLayout recruitment=sectionPanel();recruitment.addView(text("03  ·  RECRUITMENT",7,GOLD_BRIGHT,true));
        android.widget.CheckBox recruiting=guildPermissionToggle("Show this guild in the public recruiting directory",
                guild.optInt("is_recruiting",1)==1,canRecruit,GREEN);
        recruitment.addView(recruiting);LinearLayout.LayoutParams recruitP=rowParams();recruitP.topMargin=dp(8);form.addView(recruitment,recruitP);

        LinearLayout limits=sectionPanel();limits.addView(text("04  ·  ROSTER REQUIREMENTS",7,GOLD_BRIGHT,true));addGap(limits,4);
        LinearLayout values=new LinearLayout(this);values.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout capBox=new LinearLayout(this);capBox.setOrientation(LinearLayout.VERTICAL);capBox.addView(text(isAdminAccount() ? "MEMBER CAP  ·  5–1000" : "MEMBER CAP  ·  5–100",6,MUTED,true));
        EditText cap=editField("30",false);cap.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);cap.setText(String.valueOf(guild.optInt("member_limit",30)));cap.setEnabled(leader);
        capBox.addView(cap,new LinearLayout.LayoutParams(-1,dp(42)));values.addView(capBox,new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout levelBox=new LinearLayout(this);levelBox.setOrientation(LinearLayout.VERTICAL);levelBox.setPadding(dp(8),0,0,0);levelBox.addView(text("MINIMUM JOIN LEVEL  ·  1–100",6,MUTED,true));
        EditText minimum=editField("1",false);minimum.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);minimum.setText(String.valueOf(Math.max(1,guild.optInt("minimum_level",1))));minimum.setEnabled(leader);
        levelBox.addView(minimum,new LinearLayout.LayoutParams(-1,dp(42)));values.addView(levelBox,new LinearLayout.LayoutParams(0,-2,1f));
        limits.addView(values);
        limits.addView(text(leader?"Level requirement is checked when a collector joins. Capacity cannot be set below the current roster."
                :"Only the guild leader can change roster size and minimum join level.",7,MUTED,false));
        LinearLayout.LayoutParams limitsP=rowParams();limitsP.topMargin=dp(8);form.addView(limits,limitsP);

        android.widget.CheckBox announceAccess=null,recruitmentAccess=null,memberAccess=null,identityAccess=null;
        if(leader){
            LinearLayout delegation=sectionPanel();
            delegation.setBackground(gradient(Color.rgb(39,34,69),Color.rgb(11,16,32),VIOLET,dp(12)));
            delegation.addView(text("05  ·  OFFICER DELEGATION",7,VIOLET,true));
            delegation.addView(text("Grant only the tools Officers need. Leadership transfer remains leader-only.",8,MUTED,false));
            announceAccess=guildPermissionToggle("Publish and edit guild announcements",guild.optInt("officer_can_announce")==1,true,VIOLET);
            recruitmentAccess=guildPermissionToggle("Open or close public recruitment",guild.optInt("officer_can_manage_recruitment")==1,true,VIOLET);
            memberAccess=guildPermissionToggle("Promote or demote regular members",guild.optInt("officer_can_manage_members")==1,true,VIOLET);
            identityAccess=guildPermissionToggle("Edit emblem, motto and public description",guild.optInt("officer_can_edit_identity")==1,true,VIOLET);
            delegation.addView(announceAccess);delegation.addView(recruitmentAccess);delegation.addView(memberAccess);delegation.addView(identityAccess);
            LinearLayout.LayoutParams delegationParams=rowParams();delegationParams.topMargin=dp(8);form.addView(delegation,delegationParams);
        }
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.addView(form);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setView(scroll).setNegativeButton(leader?"CANCEL":"CLOSE",null);
        if(canSave){
            final android.widget.CheckBox fAnnounce=announceAccess,fRecruit=recruitmentAccess,fMembers=memberAccess,fIdentity=identityAccess;
            builder.setPositiveButton("SAVE SETTINGS",(dialog,which)->{
                int capValue=leader?parseGuildSetting(cap,5,isAdminAccount()?1000:100):guild.optInt("member_limit",30);
                int levelValue=leader?parseGuildSetting(minimum,1,100):Math.max(1,guild.optInt("minimum_level",1));
                if(leader&&(capValue<0||levelValue<0))return;
                JSONObject body=payload(
                    "description",canEditIdentity?description.getText().toString().trim():guild.optString("description",""),
                    "motto",canEditIdentity?motto.getText().toString().trim():guild.optString("motto",""),
                    "emblem",canEditIdentity?String.valueOf(emblemPicker.getSelectedItem()):guild.optString("emblem","✦"),
                    "announcement",canAnnounce?announcement.getText().toString().trim():guild.optString("announcement",""),
                    "is_recruiting",canRecruit?(recruiting.isChecked()?1:0):guild.optInt("is_recruiting",1),
                    "member_limit",capValue,"minimum_level",levelValue,
                    "officer_can_announce",leader&&fAnnounce.isChecked()?1:leader?0:guild.optInt("officer_can_announce"),
                    "officer_can_manage_recruitment",leader&&fRecruit.isChecked()?1:leader?0:guild.optInt("officer_can_manage_recruitment"),
                    "officer_can_manage_members",leader&&fMembers.isChecked()?1:leader?0:guild.optInt("officer_can_manage_members"),
                    "officer_can_edit_identity",leader&&fIdentity.isChecked()?1:leader?0:guild.optInt("officer_can_edit_identity"));
                saveGuildSettings(body);
            });
        }
        AlertDialog dialog=builder.create();dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setBackgroundDrawable(gradient(Color.rgb(10,17,31),Color.rgb(7,12,25),Color.rgb(83,118,126),dp(17)));
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(26),dp(480)),
                    Math.min(getResources().getDisplayMetrics().heightPixels-dp(90),dp(730)));
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        if(dialog.getButton(AlertDialog.BUTTON_POSITIVE)!=null)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(GOLD_BRIGHT);
        if(dialog.getButton(AlertDialog.BUTTON_NEGATIVE)!=null)dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
    }

    private void saveGuildSettings(JSONObject body) {
        api.patch("/api/guilds/settings", body, result -> ui.post(() -> {
            if (!result.ok()) showResultDialog("Guild settings not saved", result.error);
            else {
                showResultDialog("GUILD SETTINGS SAVED",
                        "Your guild settings and delegated Officer access are updated.");
                refreshAccount(() -> renderTab("guild"));
            }
        }));
    }

    private void showGuildMemberRoleDialog(JSONObject member) {
        if (member == null) return;
        String currentRole = member.optString("role", "MEMBER")
                .toUpperCase(java.util.Locale.ROOT);
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        java.util.ArrayList<String> roles = new java.util.ArrayList<>();
        if ("OFFICER".equals(currentRole)) {
            labels.add("Demote to Member");
            roles.add("MEMBER");
        } else {
            labels.add("Promote to Officer");
            roles.add("OFFICER");
        }
        labels.add("Transfer Guild Leadership");
        roles.add("LEADER");
        new AlertDialog.Builder(this)
                .setTitle("Manage " + member.optString("display_name", "Collector"))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    String nextRole = roles.get(which);
                    String title = "LEADER".equals(nextRole)
                            ? "Transfer guild leadership?" : "Change member role?";
                    String message = "LEADER".equals(nextRole)
                            ? "Leadership will transfer to this collector. Your role will become Officer."
                            : "Their guild role will change to " + nextRole + ".";
                    confirmAction(title, message, () -> api.patch(
                            "/api/guilds/members/" + member.optString("user_id"),
                            payload("role", nextRole), result -> ui.post(() -> {
                                if (!result.ok()) {
                                    showResultDialog("Guild role not changed", result.error);
                                } else {
                                    showResultDialog("GUILD ROSTER UPDATED",
                                            "The member's role has been updated.");
                                    refreshAccount(() -> renderTab("guild"));
                                }
                            })));
                }).setNegativeButton("CANCEL", null).show();
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
        addGap(content, 8);

        LinearLayout gameplayPrefs = sectionPanel();
        gameplayPrefs.addView(text("GAMEPLAY & PERFORMANCE", 8, CYAN, true));
        addAudioSetting(gameplayPrefs, "Background account sync",
                "Keep wallet, profile and account status updated while the game is open.", 3);
        addAudioSetting(gameplayPrefs, "Reduced motion",
                "Skip card flip and decorative movement effects for a calmer experience.", 4);
        addAudioSetting(gameplayPrefs, "Purchase confirmations",
                "Ask before spending Gold, Gems or other shop currencies.", 5);
        LinearLayout refreshRow = dataRow("Chat refresh interval",
                "Current interval: " + chatRefreshSeconds + " seconds. Messages remain saved on the server.",
                "CHANGE");
        refreshRow.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Chat refresh interval")
                .setItems(new String[]{"3 seconds", "5 seconds", "10 seconds", "15 seconds"},
                        (dialog, which) -> {
                            int[] values = {3, 5, 10, 15};
                            chatRefreshSeconds = values[which];
                            gamePrefs.edit().putInt("chat_refresh_seconds", chatRefreshSeconds).apply();
                            renderTab("settings");
                        }).show());
        gameplayPrefs.addView(refreshRow, rowParams());
        content.addView(gameplayPrefs, rowParams());
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

    private void renderCollection(JSONObject data) {
        JSONArray cards = data.optJSONArray("cards");
        if (cards == null) {
            showNotice("No card data was returned.");
            return;
        }

        int ownedTypes = 0;
        int totalCopies = 0;
        JSONObject featured = null;
        java.util.LinkedHashSet<String> elementSet = new java.util.LinkedHashSet<>();
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.optJSONObject(i);
            if (card == null) continue;
            String element = card.optString("element", "AETHER")
                    .toUpperCase(java.util.Locale.ROOT);
            if (!element.isEmpty()) elementSet.add(element);
            int copies = card.optInt("owned_count");
            if (copies > 0) {
                ownedTypes++;
                totalCopies += copies;
                if (featured == null) featured = card;
            }
        }

        addRealmScene("archive", "THE CARD ARCHIVE",
                "Every discovery is catalogued in the Firstlight collection.", ownedTypes,
                Math.max(1, cards.length()));
        addPhase13Link("Card Mastery & Skins", "Earn cosmetic milestones and equip visual effects.", () -> renderTab("mastery"));
        addPhase13Link("Card Encyclopedia", "Look up any card, including cards you haven't collected.",
                () -> renderTab("encyclopedia"));

        LinearLayout showcase = sectionPanel();
        showcase.setOrientation(LinearLayout.HORIZONTAL);
        showcase.setGravity(Gravity.CENTER_VERTICAL);
        showcase.setPadding(dp(12), dp(11), dp(12), dp(11));
        showcase.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(59, 48, 85), Color.rgb(15, 24, 44), Color.rgb(8, 15, 29)}));

        FrameLayout featuredFrame = new FrameLayout(this);
        featuredFrame.setBackground(gradient(Color.rgb(36, 43, 75), Color.rgb(7, 12, 25),
                featured == null ? VIOLET : cardRarityColor(featured.optString("rarity", "COMMON")),
                dp(9)));
        featuredFrame.setClipToOutline(true);
        if (featured != null) {
            CardArtView featuredArt = new CardArtView(this);
            featuredArt.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            featuredArt.loadArtwork(cardArtworkPath(featured));
            featuredFrame.addView(featuredArt, new FrameLayout.LayoutParams(-1, -1));
            View featuredShade = new View(this);
            featuredShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.TRANSPARENT, Color.argb(190, 2, 6, 17)}));
            featuredFrame.addView(featuredShade, new FrameLayout.LayoutParams(-1, -1));
            TextView featuredTag = text("OWNED", 6, Color.WHITE, true);
            featuredTag.setPadding(dp(5), dp(3), dp(5), dp(3));
            featuredTag.setBackground(gradient(Color.argb(220, 8, 16, 30),
                    Color.argb(220, 8, 16, 30), GREEN, dp(4)));
            FrameLayout.LayoutParams tagParams = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | Gravity.LEFT);
            tagParams.setMargins(dp(5), dp(5), 0, 0);
            featuredFrame.addView(featuredTag, tagParams);
            TextView featuredName = text(featured.optString("name", "Featured card"),
                    7, Color.WHITE, true);
            featuredName.setMaxLines(2);
            featuredName.setPadding(dp(5), 0, dp(5), dp(5));
            featuredFrame.addView(featuredName, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        } else {
            TextView mark = text("◇", 38, GOLD_BRIGHT, true);
            mark.setGravity(Gravity.CENTER);
            featuredFrame.addView(mark, new FrameLayout.LayoutParams(-1, -1));
        }
        showcase.addView(featuredFrame, new LinearLayout.LayoutParams(dp(88), dp(120)));

        LinearLayout heroCopy = new LinearLayout(this);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        heroCopy.setPadding(dp(12), 0, 0, 0);
        heroCopy.addView(text("FIRSTLIGHT RECORDS  ·  COLLECTOR INDEX", 7, VIOLET, true));
        TextView heroTitle = text("THE CARD ARCHIVE", 18, TEXT, true);
        heroTitle.setMaxLines(1);
        heroCopy.addView(heroTitle);
        heroCopy.addView(text(ownedTypes + " OF " + cards.length() + " UNIQUE DESIGNS COLLECTED",
                8, GOLD_BRIGHT, true));
        ProgressBar collectionProgress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        collectionProgress.setMax(Math.max(1, cards.length()));
        collectionProgress.setProgress(Math.min(cards.length(), ownedTypes));
        collectionProgress.setProgressTintList(
                android.content.res.ColorStateList.valueOf(CYAN));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(7));
        progressParams.topMargin = dp(8);
        heroCopy.addView(collectionProgress, progressParams);
        heroCopy.addView(text(cards.length() == 0 ? "CATALOGUE NOT RELEASED"
                        : Math.round(ownedTypes * 100f / cards.length()) + "% COMPLETE"
                        + "  ·  " + formatNumber(totalCopies) + " TOTAL COPIES",
                7, MUTED, true));
        heroCopy.addView(text(featured == null ? "Your first discovery is waiting."
                        : "Featured relic  ·  " + featured.optString("name", "Collector card"),
                7, TEXT, false));
        showcase.addView(heroCopy, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(showcase, rowParams());

        LinearLayout totals = new LinearLayout(this);
        totals.setOrientation(LinearLayout.HORIZONTAL);
        totals.addView(statCard("UNIQUE OWNED", formatNumber(ownedTypes),
                "Different designs"), weightParams(1));
        totals.addView(statCard("TOTAL COPIES", formatNumber(totalCopies),
                "In your collection"), weightParams(1));
        totals.addView(statCard("CATALOGUE", formatNumber(cards.length()),
                "Released designs"), weightParams(1));
        content.addView(totals);
        addGap(content, 9);

        String[][] selectorOptions = new String[4][];
        selectorOptions[0] = new String[]{"ALL CARDS", "OWNED ONLY", "NOT OWNED"};
        selectorOptions[1] = new String[]{"ALL RARITIES", "COMMON", "UNCOMMON", "RARE",
                "EPIC", "LEGENDARY", "MYTHIC"};
        java.util.ArrayList<String> elementValues = new java.util.ArrayList<>();
        elementValues.add("ALL ELEMENTS");
        elementValues.addAll(elementSet);
        selectorOptions[2] = elementValues.toArray(new String[0]);
        selectorOptions[3] = new String[]{"CARD CODE", "NAME A–Z", "MOST COPIES", "RARITY"};
        final int[] filterState = {0, 0, 0, 0};
        final String[] filterNames = {"SCOPE", "RARITY", "ELEMENT", "SORT"};
        final TextView[] filterButtons = new TextView[4];
        final Runnable[] updateGridRef = new Runnable[1];

        LinearLayout filters = sectionPanel();
        filters.addView(text("FIND A RELIC", 8, GOLD, true));
        EditText search = editField("Search name, element, rarity, set or code…", false);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        filters.addView(search, fieldParams());

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 4; i++) {
            final int index = i;
            TextView chip = button(filterNames[i] + "  ·  " + selectorOptions[i][0], false);
            chip.setTextSize(8);
            chip.setPadding(dp(12), 0, dp(12), 0);
            filterButtons[i] = chip;
            chip.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("FILTER · " + filterNames[index])
                    .setItems(selectorOptions[index], (dialog, selected) -> {
                        filterState[index] = selected;
                        chip.setText(filterNames[index] + "  ·  " + selectorOptions[index][selected]);
                        if (updateGridRef[0] != null) updateGridRef[0].run();
                    }).show());
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, dp(39));
            chipParams.rightMargin = dp(7);
            filterRow.addView(chip, chipParams);
        }
        filterScroll.addView(filterRow);
        filters.addView(filterScroll, new LinearLayout.LayoutParams(-1, dp(43)));
        content.addView(filters, rowParams());

        TextView resultCount = text("INDEXING CARDS…", 8, GOLD_BRIGHT, true);
        resultCount.setPadding(dp(2), dp(9), dp(2), dp(5));
        content.addView(resultCount);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        content.addView(grid);

        Runnable updateGrid = () -> {
            if (grid.getParent() == null) return;
            grid.removeAllViews();
            String query = search.getText().toString().trim()
                    .toLowerCase(java.util.Locale.ROOT);
            int scope = filterState[0];
            String selectedRarity = selectorOptions[1][Math.max(0,
                    Math.min(selectorOptions[1].length - 1, filterState[1]))];
            String selectedElement = selectorOptions[2][Math.max(0,
                    Math.min(selectorOptions[2].length - 1, filterState[2]))];
            int sortMode = filterState[3];

            java.util.ArrayList<JSONObject> filtered = new java.util.ArrayList<>();
            int ownedInFilter = 0;
            for (int i = 0; i < cards.length(); i++) {
                JSONObject card = cards.optJSONObject(i);
                if (card == null) continue;
                int copies = card.optInt("owned_count");
                if (scope == 1 && copies <= 0) continue;
                if (scope == 2 && copies > 0) continue;
                String cardRarity = card.optString("rarity", "COMMON")
                        .toUpperCase(java.util.Locale.ROOT);
                if (!"ALL RARITIES".equals(selectedRarity)
                        && !selectedRarity.equals(cardRarity)) continue;
                String cardElement = card.optString("element", "AETHER")
                        .toUpperCase(java.util.Locale.ROOT);
                if (!"ALL ELEMENTS".equals(selectedElement)
                        && !selectedElement.equals(cardElement)) continue;
                String searchable = (card.optString("name") + " "
                        + card.optString("element") + " " + card.optString("card_code") + " "
                        + cardRarity + " " + card.optString("set_name"))
                        .toLowerCase(java.util.Locale.ROOT);
                if (!query.isEmpty() && !searchable.contains(query)) continue;
                filtered.add(card);
                if (copies > 0) ownedInFilter++;
            }

            java.util.Collections.sort(filtered, (left, right) -> {
                if (sortMode == 1) {
                    return left.optString("name").compareToIgnoreCase(right.optString("name"));
                }
                if (sortMode == 2) {
                    int copiesOrder = Integer.compare(right.optInt("owned_count"),
                            left.optInt("owned_count"));
                    if (copiesOrder != 0) return copiesOrder;
                } else if (sortMode == 3) {
                    int rarityOrder = Integer.compare(
                            cardRarityRank(right.optString("rarity")),
                            cardRarityRank(left.optString("rarity")));
                    if (rarityOrder != 0) return rarityOrder;
                }
                return left.optString("card_code").compareToIgnoreCase(
                        right.optString("card_code"));
            });

            resultCount.setText(filtered.size() + " CARDS FOUND  ·  "
                    + ownedInFilter + " OWNED  ·  "
                    + (filtered.size() - ownedInFilter) + " TO DISCOVER");
            if (filtered.isEmpty()) {
                LinearLayout empty = sectionPanel();
                empty.setGravity(Gravity.CENTER);
                TextView mark = text("◇", 28, VIOLET, true);
                mark.setGravity(Gravity.CENTER);
                empty.addView(mark);
                empty.addView(text("NO RELICS MATCH THIS SEARCH", 9, TEXT, true));
                empty.addView(text("Change the filters or try a different card name.", 8, MUTED, false));
                grid.addView(empty, rowParams());
                return;
            }

            int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                    / getResources().getDisplayMetrics().density);
            int columns = widthDp >= 1400 ? 6 : widthDp >= 1050 ? 5
                    : widthDp >= 680 ? 4 : 3;
            LinearLayout currentRow = null;
            for (int i = 0; i < filtered.size(); i++) {
                if (i % columns == 0) {
                    currentRow = new LinearLayout(this);
                    currentRow.setOrientation(LinearLayout.HORIZONTAL);
                    grid.addView(currentRow, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                LinearLayout tile = createCardTile(filtered.get(i));
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tileParams.setMargins(dp(3), dp(4), dp(3), dp(7));
                currentRow.addView(tile, tileParams);
            }
            int remainder = filtered.size() % columns;
            if (remainder != 0 && currentRow != null) {
                for (int i = remainder; i < columns; i++) {
                    currentRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                }
            }
        };

        updateGridRef[0] = updateGrid;
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateGrid.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        updateGrid.run();

        addGap(content, 9);
        TextView duplicates = button("OPEN DUPLICATE CONVERTER", false);
        duplicates.setOnClickListener(v -> loadDuplicateConversionPanel());
        content.addView(duplicates, new LinearLayout.LayoutParams(dp(220), dp(36)));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROFILE_AVATAR) return;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadProfileAvatar(data.getData());
        } else {
            pendingProfileAvatar = null;
            profileAvatarUploaded = null;
        }
    }

    private String selectedImageFilename(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? "avatar.webp" : segment;
    }

    private String cardArtworkPath(JSONObject card) {
        if (card == null) return "";
        if("ALTERNATE".equals(card.optString("skin"))){String alternate=card.optString("alternate_art","");if(alternate.startsWith("/media/cards/")||alternate.startsWith("/art/"))return alternate;}
        String code = card.optString("card_code", "").trim().toUpperCase(java.util.Locale.ROOT);
        String name = card.optString("name", "").toLowerCase(java.util.Locale.ROOT);
        if ("FL-001".equals(code) && name.contains("fox")) return "/art/cinder-fox.webp";
        String path = card.optString("art_path", "").trim();
        if (path.startsWith("/media/cards/") || path.startsWith("/art/")) return path;
        if (code.matches("(FL|MY|EV)-\\d{3}")) return "/media/cards/artwork/" + code + ".svg";
        return "";
    }

    private LinearLayout createCardTile(JSONObject card) {
        String rarity = card.optString("rarity", "COMMON").toUpperCase(java.util.Locale.ROOT);
        String element = card.optString("element", "AETHER").toUpperCase(java.util.Locale.ROOT);
        int accent = cardRarityColor(rarity);
        int copies = card.optInt("owned_count");
        long power = card.optLong("card_power", card.optLong("formation_power"));
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(4), dp(4), dp(4), dp(6));
        GradientDrawable frame = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{accent, Color.rgb(23, 29, 45), accent, Color.rgb(8, 12, 23)});
        frame.setCornerRadius(dp(10));
        frame.setStroke(dp(1), accent);
        tile.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(48,255,255,255)), frame, null));
        tile.setElevation(dp(2));

        // Use the measured grid width, not the display width, to keep 2:3 artwork on every device.
        FrameLayout artFrame = new FrameLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = View.MeasureSpec.getSize(widthSpec);
                if (width > 0 && View.MeasureSpec.getMode(widthSpec) != View.MeasureSpec.UNSPECIFIED) {
                    super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(
                            Math.round(width * 1.5f), View.MeasureSpec.EXACTLY));
                } else super.onMeasure(widthSpec, heightSpec);
            }
        };
        artFrame.setBackground(gradient(Color.rgb(28,37,57),Color.rgb(7,12,23),accent,dp(7)));
        artFrame.setClipToOutline(true);
        LinearLayout placeholder = new LinearLayout(this);
        placeholder.setOrientation(LinearLayout.VERTICAL);
        placeholder.setGravity(Gravity.CENTER);
        TextView sigil = text(elementSigil(element), 29, accent, true);
        placeholder.addView(sigil);
        TextView code = text(card.optString("card_code","CARD"), 9, accent, true);
        code.setGravity(Gravity.CENTER);
        placeholder.addView(code);
        artFrame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        CardArtView illustration = new CardArtView(this);
        illustration.setScaleType(ImageView.ScaleType.FIT_CENTER);
        illustration.loadArtwork(cardArtworkPath(card));
        artFrame.addView(illustration,new FrameLayout.LayoutParams(-1,-1));
        phase16SkinOverlay(artFrame,card,false);
        tile.addView(artFrame,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout plaque=new LinearLayout(this);
        plaque.setOrientation(LinearLayout.VERTICAL);
        plaque.setGravity(Gravity.CENTER_HORIZONTAL);
        plaque.setPadding(dp(3),dp(5),dp(3),dp(4));
        plaque.setBackground(gradient(Color.rgb(22,29,46),Color.rgb(8,13,25),accent,dp(6)));
        TextView name=text(card.optString("name","Unknown Card"),11,TEXT,true);
        name.setGravity(Gravity.CENTER);name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);plaque.addView(name);
        TextView meta=text(rarity+"  ·  "+element,8,accent,true);
        meta.setGravity(Gravity.CENTER);meta.setMaxLines(2);plaque.addView(meta);
        TextView stats=text((power>0?"✦ "+formatNumber(power)+"  ·  ":"")+(copies>0?"×"+copies:"LOCKED"),8,GOLD_BRIGHT,true);
        stats.setGravity(Gravity.CENTER);plaque.addView(stats);
        LinearLayout.LayoutParams plaqueParams=new LinearLayout.LayoutParams(-1,-2);
        plaqueParams.topMargin=dp(3);tile.addView(plaque,plaqueParams);
        tile.setOnClickListener(v->showCardDetails(card));
        return tile;
    }

    private int cardRarityRank(String rarity) {
        switch (String.valueOf(rarity).toUpperCase(java.util.Locale.ROOT)) {
            case "COMMON": return 1;
            case "UNCOMMON": return 2;
            case "RARE": return 3;
            case "EPIC": return 4;
            case "LEGENDARY": return 5;
            case "MYTHIC": return 6;
            default: return 0;
        }
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
        art.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        art.loadArtwork(cardArtworkPath(card));
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(-1, -1);
        ap.setMargins(dp(3), dp(3), dp(3), dp(3));
        artFrame.addView(art, ap);
        phase16SkinOverlay(artFrame,card,true);
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
        if(card.optInt("owned_count")>0){TextView mastery=button("MASTERY & SKINS  ›",true);shell.addView(mastery,new LinearLayout.LayoutParams(-1,dp(44)));mastery.setOnClickListener(v->{dialog.dismiss();phase16OpenCardId=card.optString("card_id");renderTab("mastery");});}

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
        JSONObject rotation = data.optJSONObject("rotation");
        int used = data.optInt("dailyRuns");
        int limit = data.optInt("dailyLimit", 3);
        int remaining = Math.max(0, limit - used);

        JSONObject activeDungeonReward = run == null ? null : run.optJSONObject("reward");
        int activeDungeonMaxFloors = activeDungeonReward == null ? 6
                : Math.max(1, activeDungeonReward.optInt("max_floors", 6));
        addRealmScene("dungeon", run == null ? "THE DEEP VAULT"
                : run.optString("dungeon_name", "ACTIVE EXPEDITION"),
                "Choose a gate, descend through its chambers and bring the treasure home.",
                run == null ? used : run.optInt("floor_reached"),
                run == null ? Math.max(1, limit) : activeDungeonMaxFloors);
        LinearLayout dungeonOverview = sectionPanel();
        dungeonOverview.setBackground(gradient(Color.rgb(43, 39, 89), Color.rgb(11, 17, 35), VIOLET, dp(15)));
        dungeonOverview.addView(text("THE DEEP VAULT · EXPEDITION DIRECTORY", 8, VIOLET, true));
        dungeonOverview.addView(text((dungeons == null ? 0 : dungeons.length())
                + " OF " + (rotation == null ? 8 : rotation.optInt("total_count", 8))
                + " DUNGEONS OPEN TODAY  ·  " + remaining + " ENTRIES LEFT",
                13, TEXT, true));
        if (rotation != null) {
            dungeonOverview.addView(text("DAILY ROTATION  ·  " + rotation.optString("day", "")
                    + "  ·  RESTING: " + rotation.optString("excluded_dungeon", "One rotating gate"),
                    7, GOLD_BRIGHT, true));
            dungeonOverview.addView(text("The closed gate changes at " + rotation.optString(
                    "resets_at", "00:00 Asia/Kuala_Lumpur") + ". Seven other dungeons remain open.",
                    7, MUTED, false));
        }
        dungeonOverview.addView(text("Each gate has its own difficulty, floor count and reward tier. Your run and rewards are stored on the server.",
                8, MUTED, false));
        content.addView(dungeonOverview, rowParams());

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
            content.addView(text("TODAY'S SEVEN DUNGEON GATES", 8, GOLD_BRIGHT, true));
            content.addView(text("Each gate has its own reward profile and challenge. The resting gate rotates at Malaysia midnight.",
                    7, MUTED, false));
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
                details.addView(text("TODAY'S ROTATION · GATE " + (i + 1) + "/7", 6, CYAN, true));
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
                        postWithModeDeck("pve", "/api/dungeons/" + dungeon.optString("slug") + "/start",
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

    private void addRaidHeader(JSONObject featuredBoss, int total,
                                int live, int scheduled, int defeated) {
        LinearLayout hero = sectionPanel();
        hero.setPadding(dp(15), dp(14), dp(15), dp(13));
        hero.setBackground(gradient(Color.rgb(73, 37, 73), Color.rgb(10, 16, 34),
                PINK, dp(18)));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout crest = new FrameLayout(this);
        crest.setBackground(gradient(Color.rgb(100, 48, 86), Color.rgb(28, 20, 48),
                PINK, dp(15)));
        TextView sigil = text("♜", 39, Color.rgb(255, 177, 197), true);
        sigil.setGravity(Gravity.CENTER);
        crest.addView(sigil, new FrameLayout.LayoutParams(-1, -1));
        top.addView(crest, new LinearLayout.LayoutParams(dp(68), dp(70)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        TextView eyebrow = text("FIRSTLIGHT WORLD EVENT", 7, PINK, true);
        eyebrow.setLetterSpacing(0.12f);
        copy.addView(eyebrow);
        copy.addView(text("WORLD RAID", 19, TEXT, true));
        copy.addView(text(total + " GUARDIANS IN THE ROTATION", 8, GOLD_BRIGHT, true));
        String featured = featuredBoss == null ? "The realm is waiting for its next guardian."
                : "Featured · " + featuredBoss.optString("name", "World Guardian");
        TextView detail = text(featured, 8, MUTED, false);
        detail.setMaxLines(2);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(detail);
        top.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hero.addView(top);

        View divider = new View(this);
        divider.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PINK, Color.rgb(116, 75, 119), Color.TRANSPARENT}));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(11);
        dividerParams.bottomMargin = dp(8);
        hero.addView(divider);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard("LIVE", String.valueOf(live), "Attack now"), weightParams(1));
        stats.addView(statCard("UPCOMING", String.valueOf(scheduled), "Scheduled"), weightParams(1));
        stats.addView(statCard("DEFEATED", String.valueOf(defeated), "Realm records"), weightParams(1));
        hero.addView(stats);
        content.addView(hero, rowParams());
    }


    private void showRaidStrikeDialog(JSONObject boss,long damage){
        int accent=Color.rgb(244,119,147);android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setGravity(Gravity.CENTER_HORIZONTAL);shell.setPadding(dp(18),dp(16),dp(18),dp(15));
        shell.setBackground(gradient(Color.rgb(42,29,53),Color.rgb(7,12,25),accent,dp(19)));
        TextView kicker=text("WORLD RAID  /  STRIKE CONFIRMED",7,GOLD_BRIGHT,true);kicker.setGravity(Gravity.CENTER);shell.addView(kicker);
        TextView title=text(boss==null?"GUARDIAN HIT":boss.optString("name","WORLD GUARDIAN"),19,TEXT,true);title.setGravity(Gravity.CENTER);title.setMaxLines(2);title.setPadding(0,dp(4),0,dp(7));shell.addView(title);
        FrameLayout stage=new FrameLayout(this);stage.setBackground(gradient(Color.rgb(62,35,62),Color.rgb(13,16,34),accent,dp(14)));
        RaidBossView art=new RaidBossView(this,accent,reduceMotionEnabled);stage.addView(art,new FrameLayout.LayoutParams(-1,-1));
        TextView impact=text("✦",46,GOLD_BRIGHT,true);impact.setGravity(Gravity.CENTER);impact.setShadowLayer(dp(16),0,0,GOLD_BRIGHT);stage.addView(impact,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,dp(194));stp.bottomMargin=dp(10);shell.addView(stage,stp);
        TextView damageText=text("−"+formatNumber(damage)+" DAMAGE",20,GOLD_BRIGHT,true);damageText.setGravity(Gravity.CENTER);shell.addView(damageText);
        TextView detail=text("Your formation struck the guardian. The realm's shared vitality has been updated.",8,MUTED,false);detail.setGravity(Gravity.CENTER);detail.setLineSpacing(dp(3),1f);detail.setPadding(dp(8),dp(4),dp(8),dp(10));shell.addView(detail);
        TextView close=button("RETURN TO WORLD RAID",true);close.setOnClickListener(v->{dialog.dismiss();refreshAccount(()->renderTab("raids"));});shell.addView(close,new LinearLayout.LayoutParams(-1,dp(42)));
        dialog.setContentView(shell);dialog.setCanceledOnTouchOutside(false);dialog.setOnShowListener(v->{impact.setScaleX(.2f);impact.setScaleY(.2f);impact.setAlpha(0f);
            impact.animate().scaleX(1.15f).scaleY(1.15f).alpha(.92f).setDuration(350).withEndAction(()->impact.animate().scaleX(1f).scaleY(1f).alpha(.55f).setDuration(480).start()).start();art.playStrike(null);});
        dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setDimAmount(.82f);w.setLayout(Math.min(dp(510),getResources().getDisplayMetrics().widthPixels-dp(30)),ViewGroup.LayoutParams.WRAP_CONTENT);}
    }

    private void renderRaids(JSONObject data) {
        JSONArray bosses = data.optJSONArray("bosses");
        if (bosses == null || bosses.length() == 0) {
            addRealmScene("raid", "WORLD RAID", "The next guardian has not been summoned yet.", 0, 0);
            showNotice("No world raid is scheduled. Return when the next guardian awakens.");
            return;
        }
        JSONObject featuredBoss = bosses.optJSONObject(0);
        int liveCount = 0, scheduledCount = 0, defeatedCount = 0;
        for (int i = 0; i < bosses.length(); i++) {
            JSONObject boss = bosses.optJSONObject(i);
            if (boss == null) continue;
            if (boss.optBoolean("is_event_open")) liveCount++;
            else if (boss.optBoolean("defeated")) defeatedCount++;
            else scheduledCount++;
        }
        addRaidHeader(featuredBoss, bosses.length(), liveCount, scheduledCount, defeatedCount);
        for (int i = 0; i < bosses.length(); i++) {
            JSONObject boss = bosses.optJSONObject(i);
            if (boss == null) continue;
            JSONObject reward = boss.optJSONObject("reward");
            if (reward == null) reward = new JSONObject();
            boolean live = boss.optBoolean("is_event_open");
            boolean defeated = boss.optBoolean("defeated");
            int accent = defeated ? CYAN : live ? PINK : GOLD_BRIGHT;
            LinearLayout panel = sectionPanel();
            panel.setPadding(dp(14), dp(13), dp(14), dp(13));
            panel.setBackground(gradient(Color.rgb(48, 35, 68), Color.rgb(9, 16, 34), accent, dp(18)));
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout portrait = new FrameLayout(this);
            portrait.setBackground(gradient(Color.rgb(105, 52, 89), Color.rgb(24, 21, 49), accent, dp(15)));
            RaidBossView bossArt = new RaidBossView(this, accent, reduceMotionEnabled);
            portrait.addView(bossArt, new FrameLayout.LayoutParams(-1, -1));
            header.addView(portrait, new LinearLayout.LayoutParams(dp(96), dp(108)));
            LinearLayout title = new LinearLayout(this);
            title.setOrientation(LinearLayout.VERTICAL);
            title.setPadding(dp(12), 0, 0, 0);
            TextView state = text((defeated ? "✓  DEFEATED" : live ? "●  LIVE ENCOUNTER" : "◷  SCHEDULED"),
                    7, accent, true);
            state.setPadding(dp(8), dp(5), dp(8), dp(5));
            state.setBackground(gradient(Color.rgb(29, 30, 49), Color.rgb(17, 21, 39), accent, dp(20)));
            title.addView(state);
            addGap(title, 3);
            title.addView(text(boss.optString("name", "World Guardian"), 18, TEXT, true));
            TextView description = text(boss.optString("description", ""), 8, MUTED, false);
            description.setMaxLines(3);
            title.addView(description);
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            panel.addView(header);
            addGap(panel, 6);
            LinearLayout hpInfo = new LinearLayout(this);
            hpInfo.setGravity(Gravity.CENTER_VERTICAL);
            hpInfo.addView(text("GUARDIAN VITALITY", 8, accent, true),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            hpInfo.addView(text(formatNumber(boss.optLong("remaining_hp")) + " / "
                    + formatNumber(boss.optLong("hp")) + " HP", 9, TEXT, true));
            panel.addView(hpInfo);
            ProgressBar hp = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            hp.setMax(100);
            hp.setProgress(Math.max(0, Math.min(100, boss.optInt("progress_percent"))));
            hp.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            LinearLayout.LayoutParams hpParams = new LinearLayout.LayoutParams(-1, dp(10));
            hpParams.topMargin = dp(5);
            hpParams.bottomMargin = dp(8);
            panel.addView(hp, hpParams);
            TextView realmProgress = text(boss.optInt("progress_percent") + "% REALM PROGRESS",
                    7, MUTED, true);
            realmProgress.setGravity(Gravity.RIGHT);
            panel.addView(realmProgress);
            LinearLayout contributions = new LinearLayout(this);
            contributions.setOrientation(LinearLayout.HORIZONTAL);
            contributions.addView(statCard("TOTAL DAMAGE", formatNumber(boss.optLong("total_damage")), "Realm contribution"), weightParams(1));
            contributions.addView(statCard("PARTICIPANTS", formatNumber(boss.optLong("participants")), "Collectors"), weightParams(1));
            contributions.addView(statCard("YOUR DAMAGE", formatNumber(boss.optLong("my_today_damage")), "Today"), weightParams(1));
            panel.addView(contributions);
            LinearLayout rewardRow = new LinearLayout(this);
            rewardRow.setOrientation(LinearLayout.HORIZONTAL);
            rewardRow.setGravity(Gravity.CENTER_VERTICAL);
            rewardRow.setPadding(0, dp(3), 0, dp(3));
            String[] rewardLabels = {
                    "◈  " + formatNumber(reward.optLong("coins")) + " GOLD",
                    "✦  " + formatNumber(reward.optLong("xp")) + " EXP",
                    "◇  " + formatNumber(reward.optLong("shards")) + " SHARDS"
            };
            int[] rewardColors = {GOLD_BRIGHT, CYAN, VIOLET};
            for (int rewardIndex = 0; rewardIndex < rewardLabels.length; rewardIndex++) {
                TextView rewardChip = text(rewardLabels[rewardIndex], 7,
                        rewardColors[rewardIndex], true);
                rewardChip.setGravity(Gravity.CENTER);
                rewardChip.setPadding(dp(7), dp(7), dp(7), dp(7));
                rewardChip.setBackground(gradient(Color.rgb(28, 31, 49),
                        Color.rgb(14, 20, 37), rewardColors[rewardIndex], dp(9)));
                LinearLayout.LayoutParams rewardParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                rewardParams.setMargins(dp(2), dp(3), dp(2), dp(3));
                rewardRow.addView(rewardChip, rewardParams);
            }
            panel.addView(rewardRow);
            if (live && !defeated) {
                TextView attack = button(boss.optLong("my_today_damage") > 0
                        ? "DAILY STRIKE USED" : "⚔ STRIKE THE GUARDIAN",
                        boss.optLong("my_today_damage") <= 0);
                attack.setEnabled(boss.optLong("my_today_damage") <= 0);
                attack.setOnClickListener(v -> confirmAction("Strike " + boss.optString("name") + "?",
                        "Your formation power determines the damage. One strike is allowed per day.", () ->
                        postWithModeDeck("pve", "/api/raids/" + boss.optString("slug") + "/attack",
                                payload("requestId", newActionId()), result -> ui.post(() -> {
                                    if (!result.ok()) showResultDialog("Raid strike failed", result.error);
                                    else {
                                        JSONObject attempt = result.data.optJSONObject("attempt");
                                        if (sfxEnabled && gameAudio != null) gameAudio.playSuccess();
                                        showRaidStrikeDialog(boss, attempt == null ? 0L : attempt.optLong("damage"));
                                    }
                                }))));
                panel.addView(attack, rowParams());
            }
            if (boss.optBoolean("my_claimable")) {
                TextView claim = button("✦  CLAIM RAID REWARDS", true);
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
        LinearLayout privateDesk = sectionPanel();
        privateDesk.setBackground(gradient(Color.rgb(27, 50, 70), Color.rgb(9, 17, 31), CYAN, dp(14)));
        privateDesk.addView(text("YOUR COLLECTION STAYS PRIVATE", 8, CYAN, true));
        privateDesk.addView(text(formatNumber(inventory.length()) + " AVAILABLE CARD INSTANCE(S)", 14, TEXT, true));
        privateDesk.addView(text("Your full collection is hidden from the Trade Desk. Choose exactly which card you want to publish; only that selected card becomes visible on the public board.",
                8, MUTED, false));
        TextView listPublic = button("＋  CHOOSE CARD TO LIST PUBLICLY", true);
        listPublic.setEnabled(inventory.length() > 0);
        listPublic.setOnClickListener(v -> showTradeListingDialog(inventory));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, dp(40));
        listParams.topMargin = dp(8);
        privateDesk.addView(listPublic, listParams);
        content.addView(privateDesk, rowParams());
        addGap(content, 7);
        loadPublicTradeBoard(inventory);
    }

        private void renderChat(JSONObject data) {
        addRealmScene("trade", "THE COLLECTOR LOUNGE",
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
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        ui.removeCallbacks(chatRefreshTick);
        ui.postDelayed(chatRefreshTick, chatRefreshIntervalMs());
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
            ui.postDelayed(chatRefreshTick, chatRefreshIntervalMs());
        }));
    }

    private void renderChatMessages(JSONObject data) {
        if (chatMessagesPanel == null || data == null) return;
        JSONArray messages = data.optJSONArray("messages");
        if (messages == null) messages = new JSONArray();
        chatMessagesPanel.removeAllViews();
        if (chatStatus != null) {
            chatStatus.setText(activeChatChannel + "  ·  " + messages.length()
                    + " RECENT MESSAGES  ·  AUTO-REFRESH " + chatRefreshSeconds + "S");
            chatStatus.setTextColor(MUTED);
        }
        if (messages.length() == 0) {
            LinearLayout empty = sectionPanel();
            empty.setGravity(Gravity.CENTER);
            TextView mark = text("✦", 24, CYAN, true);
            mark.setGravity(Gravity.CENTER);
            empty.addView(mark);
            empty.addView(text("THE LOUNGE IS QUIET", 9, TEXT, true));
            empty.addView(text("Start a conversation or find another collector to trade with.",
                    8, MUTED, false));
            chatMessagesPanel.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        String ownId = me == null ? "" : me.optString("user_id", "");
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            boolean own = ownId.equals(message.optString("user_id"));
            String playerName = message.optString("display_name", "Collector");
            String level = "LV." + formatNumber(message.optLong("collector_level", 1));
            LinearLayout messageBlock = new LinearLayout(this);
            messageBlock.setOrientation(LinearLayout.VERTICAL);
            messageBlock.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
            LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, -2);
            blockParams.bottomMargin = dp(10);
            chatMessagesPanel.addView(messageBlock, blockParams);

            LinearLayout messageRow = new LinearLayout(this);
            messageRow.setGravity(Gravity.BOTTOM | (own ? Gravity.RIGHT : Gravity.LEFT));
            if (own) messageRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            FrameLayout avatar = new FrameLayout(this);
            int avatarAccent = own ? CYAN : VIOLET;
            avatar.setBackground(gradient(Color.rgb(39, 62, 83), Color.rgb(13, 24, 42),
                    avatarAccent, dp(20)));
            TextView initialsView = text(initials(playerName), 9, avatarAccent, true);
            initialsView.setGravity(Gravity.CENTER);
            avatar.addView(initialsView, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(31), dp(31));
            if (own) avatarParams.leftMargin = dp(7); else avatarParams.rightMargin = dp(7);

            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
            TextView author = text((own ? "YOU" : playerName) + "  ·  " + level,
                    7, own ? CYAN : GOLD_BRIGHT, true);
            author.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
            author.setPadding(dp(5), 0, dp(5), dp(4));
            copy.addView(author);
            LinearLayout bubble = new LinearLayout(this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(dp(12), dp(9), dp(12), dp(9));
            bubble.setBackground(gradient(own ? Color.rgb(34, 74, 87) : Color.rgb(29, 39, 65),
                    own ? Color.rgb(13, 38, 52) : Color.rgb(13, 21, 39),
                    own ? CYAN : Color.rgb(85, 102, 151), dp(12)));
            TextView body = text(message.optString("message", ""), 9, TEXT, false);
            body.setLineSpacing(dp(2), 1.04f);
            body.setMaxWidth(dp(480));
            bubble.addView(body);
            copy.addView(bubble);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1f);
            copyParams.width = Math.min(dp(500), (int)(getResources().getDisplayMetrics().widthPixels * 0.74f));
            messageRow.addView(avatar, avatarParams);
            messageRow.addView(copy, copyParams);
            messageBlock.addView(messageRow, new LinearLayout.LayoutParams(-1, -2));

            String created = message.optString("created_at", "");
            if (created.length() > 16) created = created.substring(0, 16).replace("T", " ");
            TextView time = text(created, 6, MUTED, false);
            time.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
            time.setPadding(dp(42), dp(3), dp(4), 0);
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
        final JSONArray tradeInventory = inventory;
        final JSONArray communityListings = listings;

        addGap(content, 9);
        LinearLayout market = sectionPanel();
        market.setPadding(dp(14), dp(13), dp(14), dp(13));
        market.setBackground(gradient(Color.rgb(47, 43, 81), Color.rgb(9, 17, 34),
                VIOLET, dp(16)));
        LinearLayout marketHead = new LinearLayout(this);
        marketHead.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout mark = new FrameLayout(this);
        mark.setBackground(gradient(Color.rgb(76, 65, 119), Color.rgb(19, 29, 56), GOLD, dp(13)));
        TextView glyph = text("✧", 27, GOLD_BRIGHT, true);
        glyph.setGravity(Gravity.CENTER);
        mark.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
        marketHead.addView(mark, new LinearLayout.LayoutParams(dp(55), dp(58)));
        LinearLayout marketCopy = new LinearLayout(this);
        marketCopy.setOrientation(LinearLayout.VERTICAL);
        marketCopy.setPadding(dp(10), 0, dp(6), 0);
        marketCopy.addView(text("FIRSTLIGHT EXCHANGE", 7, VIOLET, true));
        marketCopy.addView(text("Public Trade Board", 17, TEXT, true));
        marketCopy.addView(text("Collector-to-collector card listings", 8, MUTED, false));
        marketHead.addView(marketCopy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView create = button("＋ LIST CARD", true);
        create.setTextSize(8);
        create.setOnClickListener(v -> showTradeListingDialog(tradeInventory));
        marketHead.addView(create);
        market.addView(marketHead);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statCard("COMMUNITY", formatNumber(communityListings.length()),
                "Public listings"), weightParams(1));
        stats.addView(statCard("YOUR LISTINGS", formatNumber(myListings.length()),
                "Your published cards"), weightParams(1));
        stats.addView(statCard("PRIVATE", formatNumber(tradeInventory.length()),
                "Available to list"), weightParams(1));
        market.addView(stats);
        market.addView(text("Only cards you explicitly publish appear here. Your unlisted collection remains private.",
                8, MUTED, false));
        content.addView(market, rowParams());

        LinearLayout ownPanel = sectionPanel();
        ownPanel.addView(text("YOUR PUBLIC LISTINGS  ·  " + myListings.length(), 8, GOLD_BRIGHT, true));
        if (myListings.length() == 0) {
            ownPanel.addView(text("Nothing published yet. Your collection stays private until you choose a card to list.",
                    8, MUTED, false));
            TextView publish = button("CHOOSE A CARD TO PUBLISH", false);
            publish.setOnClickListener(v -> showTradeListingDialog(tradeInventory));
            ownPanel.addView(publish, new LinearLayout.LayoutParams(-1, dp(37)));
        } else {
            for (int i = 0; i < myListings.length(); i++) {
                JSONObject listing = myListings.optJSONObject(i);
                if (listing == null) continue;
                LinearLayout item = dataRow(listing.optString("name", "Listed card"),
                        listing.optString("rarity", "CARD") + "  ·  "
                                + listing.optString("wanted_text", "Open to offers"),
                        listing.optString("status", "OPEN"));
                String listingId = listing.optString("listing_id", "");
                if ("OPEN".equalsIgnoreCase(listing.optString("status")) && !listingId.isEmpty()) {
                    TextView remove = button("REMOVE", false);
                    remove.setOnClickListener(v -> confirmAction("Remove public listing?",
                            "The reserved card will return to your private available inventory.",
                            () -> api.post("/api/trade-board/" + Uri.encode(listingId) + "/cancel",
                                    new JSONObject(), result -> ui.post(() -> {
                                        if (!result.ok()) showResultDialog("Listing not removed", result.error);
                                        else refreshAccount(() -> renderTab("trades"));
                                    }))));
                    item.addView(remove, new LinearLayout.LayoutParams(dp(82), dp(34)));
                }
                ownPanel.addView(item, rowParams());
            }
        }
        content.addView(ownPanel, rowParams());

        LinearLayout browse = sectionPanel();
        browse.addView(text("DISCOVER LISTED CARDS", 8, GOLD_BRIGHT, true));
        EditText search = editField("Search card name, element, rarity or player…", false);
        browse.addView(search, fieldParams());
        final String[] filterNames = {"ALL", "RARE+", "LEGENDARY+", "MYTHIC"};
        final int[] selectedFilter = {0};
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        final TextView[] chips = new TextView[filterNames.length];
        LinearLayout chipRow = new LinearLayout(this);
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < filterNames.length; i++) {
            final int index = i;
            TextView chip = button(filterNames[i], i == 0);
            chip.setTextSize(8);
            chips[i] = chip;
            chip.setOnClickListener(v -> {
                selectedFilter[0] = index;
                for (int j = 0; j < chips.length; j++) {
                    chips[j].setBackground(gradient(j == index
                                    ? Color.rgb(213, 169, 91) : Color.rgb(26, 36, 56),
                            Color.rgb(13, 20, 35), j == index ? GOLD_BRIGHT
                                    : Color.rgb(67, 87, 117), dp(8)));
                    chips[j].setTextColor(j == index ? BG : TEXT);
                }
                renderTradeBoardListings(communityListings, tradeInventory, selectedFilter[0],
                        search.getText().toString(), results);
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, dp(36));
            chipParams.rightMargin = dp(6);
            chipRow.addView(chip, chipParams);
        }
        chipScroll.addView(chipRow);
        browse.addView(chipScroll, new LinearLayout.LayoutParams(-1, dp(40)));
        content.addView(browse, rowParams());

        content.addView(results);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderTradeBoardListings(communityListings, tradeInventory,
                        selectedFilter[0], s.toString(), results);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        renderTradeBoardListings(communityListings, tradeInventory, 0, "", results);
    }

    private void renderTradeBoardListings(JSONArray listings, JSONArray inventory, int filter,
                                          String query, LinearLayout holder) {
        holder.removeAllViews();
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        int shown = 0;
        for (int i = 0; i < listings.length(); i++) {
            JSONObject listing = listings.optJSONObject(i);
            if (listing == null) continue;
            if (me != null && me.optString("user_id").equals(listing.optString("listing_owner_id"))) continue;
            String rarityValue = listing.optString("rarity", "COMMON").toUpperCase(java.util.Locale.ROOT);
            int rank = cardRarityRank(rarityValue);
            if (filter == 1 && rank < cardRarityRank("RARE")) continue;
            if (filter == 2 && rank < cardRarityRank("LEGENDARY")) continue;
            if (filter == 3 && !"MYTHIC".equals(rarityValue)) continue;
            String nameValue = listing.optString("name", "Card");
            String ownerValue = listing.optString("owner_name", "Collector");
            String searchValue = (nameValue + " " + ownerValue + " " + rarityValue + " "
                    + listing.optString("element", "") + " " + listing.optString("card_code", ""))
                    .toLowerCase(java.util.Locale.ROOT);
            if (!needle.isEmpty() && !searchValue.contains(needle)) continue;
            shown++;
            int accent = cardRarityColor(rarityValue);
            LinearLayout card = sectionPanel();
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(9), dp(9), dp(10), dp(9));
            FrameLayout art = new FrameLayout(this);
            art.setBackground(gradient(Color.rgb(25, 34, 54), Color.rgb(7, 11, 22), accent, dp(8)));
            art.setClipToOutline(true);
            CardArtView illustration = new CardArtView(this);
            illustration.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            illustration.loadArtwork(cardArtworkPath(listing));
            art.addView(illustration, new FrameLayout.LayoutParams(-1, -1));
            View shade = new View(this);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.TRANSPARENT, Color.argb(190, 3, 7, 15)}));
            art.addView(shade, new FrameLayout.LayoutParams(-1, -1));
            TextView rarity = text(rarityValue, 6, Color.WHITE, true);
            rarity.setPadding(dp(5), dp(4), dp(5), dp(4));
            rarity.setBackground(gradient(Color.argb(215, 9, 14, 28),
                    Color.argb(215, 9, 14, 28), accent, dp(5)));
            FrameLayout.LayoutParams rarityParams = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | Gravity.LEFT);
            rarityParams.setMargins(dp(5), dp(5), 0, 0);
            art.addView(rarity, rarityParams);
            TextView artName = text(nameValue, 8, Color.WHITE, true);
            artName.setMaxLines(2);
            artName.setPadding(dp(5), 0, dp(5), dp(6));
            art.addView(artName, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            card.addView(art, new LinearLayout.LayoutParams(dp(100), dp(140)));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(11), 0, 0, 0);
            info.addView(text("LISTED BY " + ownerValue.toUpperCase(java.util.Locale.ROOT),
                    7, CYAN, true));
            TextView name = text(nameValue, 13, TEXT, true);
            name.setMaxLines(2);
            info.addView(name);
            info.addView(text(rarityValue + "  ·  " + listing.optString("element", "AETHER"),
                    7, accent, true));
            String wanted = listing.optString("wanted_text", "");
            info.addView(text(wanted.isEmpty() ? "WANTS · OPEN TO OFFERS" : "WANTS · " + wanted,
                    8, GOLD_BRIGHT, true));
            String noteText = listing.optString("message", "");
            if (!noteText.isEmpty()) {
                TextView note = text(noteText, 7, MUTED, false);
                note.setMaxLines(2);
                info.addView(note);
            }
            String listingId = listing.optString("listing_id", "");
            TextView offer = button("MAKE TRADE OFFER", true);
            offer.setTextSize(8);
            offer.setEnabled(!listingId.isEmpty());
            offer.setOnClickListener(v -> showPublicTradeRequestDialog(listing, inventory));
            LinearLayout.LayoutParams offerParams = new LinearLayout.LayoutParams(-1, dp(36));
            offerParams.topMargin = dp(7);
            info.addView(offer, offerParams);
            card.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            holder.addView(card, rowParams());
        }
        TextView count = text(shown + " PUBLIC LISTING(S) MATCH YOUR SEARCH", 7, MUTED, true);
        count.setPadding(dp(3), dp(3), dp(3), dp(8));
        holder.addView(count, 0);
        if (shown == 0) {
            LinearLayout empty = sectionPanel();
            empty.setGravity(Gravity.CENTER);
            empty.addView(text("NO MATCHING LISTINGS", 10, TEXT, true));
            empty.addView(text("Try a different keyword or rarity filter.", 8, MUTED, false));
            holder.addView(empty, rowParams());
        }
    }


    private void showCardPickerDialog(JSONArray cards,String title,String subtitle,boolean allowEmpty,
                                      java.util.function.Consumer<JSONObject> onChoose){
        if(cards==null||cards.length()==0){if(allowEmpty&&onChoose!=null)onChoose.accept(null);else showResultDialog("No cards available","There are no eligible cards to choose from.");return;}
        android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(15),dp(14),dp(15),dp(13));
        shell.setBackground(gradient(Color.rgb(24,36,58),Color.rgb(7,13,25),Color.rgb(92,124,150),dp(18)));
        shell.addView(text("CARDUNIVERSE  /  CARD ARCHIVE",7,CYAN,true));
        TextView heading=text(title,17,TEXT,true);heading.setPadding(0,dp(3),0,dp(2));shell.addView(heading);
        TextView copy=text(subtitle==null?"":subtitle,8,MUTED,false);copy.setLineSpacing(dp(2),1f);shell.addView(copy);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(cards.length()+" CARD(S) AVAILABLE",7,GOLD_BRIGHT,true),new LinearLayout.LayoutParams(0,-2,1f));
        if(allowEmpty){TextView clear=button("CLEAR SLOT",false);clear.setTextSize(7);clear.setOnClickListener(v->{dialog.dismiss();if(onChoose!=null)onChoose.accept(null);});top.addView(clear,new LinearLayout.LayoutParams(dp(104),dp(32)));}
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(9);shell.addView(top,tp);
        EditText search=editField("Search name, rarity, element or card code",false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(42));sp.topMargin=dp(8);sp.bottomMargin=dp(8);shell.addView(search,sp);
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        TextView cancel=button("CANCEL",false);cancel.setOnClickListener(v->dialog.dismiss());
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(38));cp.topMargin=dp(8);shell.addView(cancel,cp);
        LinearLayout[] rows=new LinearLayout[cards.length()];
        for(int i=0;i<cards.length();i++){JSONObject card=cards.optJSONObject(i);if(card==null)continue;
            LinearLayout row=createCardPickerRow(card,false,false);rows[i]=row;LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(6);list.addView(row,rp);
            row.setOnClickListener(v->{dialog.dismiss();if(onChoose!=null)onChoose.accept(card);});}
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){String q=s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                for(int i=0;i<rows.length;i++){JSONObject c=cards.optJSONObject(i);if(rows[i]==null||c==null)continue;
                    String h=(c.optString("name","")+" "+c.optString("rarity","")+" "+c.optString("element","")+" "+c.optString("card_code","")).toLowerCase(java.util.Locale.ROOT);
                    rows[i].setVisibility(q.isEmpty()||h.contains(q)?View.VISIBLE:View.GONE);}}
            @Override public void afterTextChanged(Editable s){}
        });
        dialog.setContentView(shell);dialog.setCanceledOnTouchOutside(true);dialog.show();Window w=dialog.getWindow();
        if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setDimAmount(.76f);
            w.setLayout(Math.min(dp(540),getResources().getDisplayMetrics().widthPixels-dp(30)),Math.min(dp(660),getResources().getDisplayMetrics().heightPixels-dp(88)));
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
    }

    private LinearLayout createCardPickerRow(JSONObject card,boolean selected,boolean multi){
        String rarity=card.optString("rarity","COMMON").toUpperCase(java.util.Locale.ROOT),element=card.optString("element","AETHER").toUpperCase(java.util.Locale.ROOT);
        int accent=cardRarityColor(rarity);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(7),dp(8),dp(7));
        row.setBackground(gradient(Color.rgb(20,34,51),Color.rgb(11,19,34),Color.rgb(67,87,111),dp(11)));
        FrameLayout art=new FrameLayout(this);art.setBackground(gradient(Color.rgb(30,45,65),Color.rgb(7,13,25),accent,dp(7)));art.setClipToOutline(true);
        TextView fallback=text(elementSigil(element),20,accent,true);fallback.setGravity(Gravity.CENTER);art.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        CardArtView image=new CardArtView(this);image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);image.loadArtwork(cardArtworkPath(card));art.addView(image,new FrameLayout.LayoutParams(-1,-1));
        row.addView(art,new LinearLayout.LayoutParams(dp(62),dp(82)));
        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setPadding(dp(10),0,dp(7),0);
        TextView name=text(card.optString("name","Unknown Card"),10,TEXT,true);name.setMaxLines(2);details.addView(name);details.addView(text(rarity+"  ·  "+element,7,accent,true));
        String extra=card.optString("card_code","");long power=card.optLong("card_power",card.optLong("formation_power"));if(power>0)extra+=(extra.isEmpty()?"":"  ·  ")+formatNumber(power)+" POWER";
        if(card.has("owned_count"))extra+=(extra.isEmpty()?"":"  ·  ")+"×"+card.optInt("owned_count");
        TextView sub=text(extra,7,MUTED,false);sub.setMaxLines(1);details.addView(sub);row.addView(details,new LinearLayout.LayoutParams(0,-2,1f));
        TextView badge=text(multi?(selected?"✓ SELECTED":"＋ ADD"):"CHOOSE",6,selected?CYAN:GOLD_BRIGHT,true);badge.setGravity(Gravity.CENTER);badge.setPadding(dp(7),dp(7),dp(7),dp(7));
        badge.setBackground(gradient(Color.rgb(30,38,55),Color.rgb(14,21,37),selected?CYAN:accent,dp(8)));row.addView(badge);return row;
    }

    private void refreshCardPickerRow(LinearLayout row,boolean selected,int accent){
        row.setBackground(gradient(selected?Color.rgb(28,57,61):Color.rgb(20,34,51),selected?Color.rgb(12,31,38):Color.rgb(11,19,34),selected?CYAN:Color.rgb(67,87,111),dp(11)));
        if(row.getChildCount()>2&&row.getChildAt(2) instanceof TextView){TextView badge=(TextView)row.getChildAt(2);badge.setText(selected?"✓ SELECTED":"＋ ADD");badge.setTextColor(selected?CYAN:accent);}
    }

    private void showMultiCardPickerDialog(JSONArray cards,String title,String subtitle,String idKey,
            java.util.Set<String> preselected,int minimum,int maximum,String actionLabel,View footer,
            java.util.function.Consumer<java.util.Set<String>> onConfirm){
        if(cards==null||cards.length()==0){showResultDialog("No cards available","There are no eligible cards to choose from.");return;}
        java.util.LinkedHashSet<String> selected=new java.util.LinkedHashSet<>();if(preselected!=null)selected.addAll(preselected);
        android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(15),dp(14),dp(15),dp(13));
        shell.setBackground(gradient(Color.rgb(24,36,58),Color.rgb(7,13,25),Color.rgb(92,124,150),dp(18)));
        shell.addView(text("CARDUNIVERSE  /  CARD ARCHIVE",7,CYAN,true));TextView head=text(title,17,TEXT,true);head.setPadding(0,dp(3),0,dp(2));shell.addView(head);
        TextView copy=text(subtitle==null?"":subtitle,8,MUTED,false);copy.setLineSpacing(dp(2),1f);shell.addView(copy);
        LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.VERTICAL);summary.setPadding(dp(10),dp(8),dp(10),dp(8));
        summary.setBackground(gradient(Color.rgb(26,48,59),Color.rgb(12,27,38),CYAN,dp(10)));TextView counter=text("",8,CYAN,true);summary.addView(counter);
        TextView hint=text("Tap a card to add or remove it from your selection.",7,MUTED,false);summary.addView(hint);
        LinearLayout.LayoutParams smp=new LinearLayout.LayoutParams(-1,-2);smp.topMargin=dp(8);shell.addView(summary,smp);
        EditText search=editField("Search name, rarity, element or card code",false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(42));sp.topMargin=dp(8);sp.bottomMargin=dp(7);shell.addView(search,sp);
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        if(footer!=null){LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=dp(7);shell.addView(footer,fp);}
        LinearLayout[] rows=new LinearLayout[cards.length()];
        for(int i=0;i<cards.length();i++){JSONObject c=cards.optJSONObject(i);if(c==null)continue;String id=c.optString(idKey,"");LinearLayout row=createCardPickerRow(c,selected.contains(id),true);rows[i]=row;
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(6);list.addView(row,rp);
            row.setOnClickListener(v->{if(id.isEmpty())return;if(selected.contains(id))selected.remove(id);else{if(maximum>0&&selected.size()>=maximum){Toast.makeText(this,"You can select up to "+maximum+" cards.",Toast.LENGTH_SHORT).show();return;}selected.add(id);}
                refreshCardPickerRow(row,selected.contains(id),cardRarityColor(c.optString("rarity","COMMON")));updatePickerCount(counter,selected.size(),minimum,maximum);
                hint.setText(selected.isEmpty()?"Tap a card to add it to your selection.":selected.size()+" selected · tap another card to update the list.");});}
        search.addTextChangedListener(new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            @Override public void onTextChanged(CharSequence s,int st,int b,int count){String q=s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                for(int i=0;i<rows.length;i++){JSONObject c=cards.optJSONObject(i);if(rows[i]==null||c==null)continue;String h=(c.optString("name","")+" "+c.optString("rarity","")+" "+c.optString("element","")+" "+c.optString("card_code","")).toLowerCase(java.util.Locale.ROOT);rows[i].setVisibility(q.isEmpty()||h.contains(q)?View.VISIBLE:View.GONE);}}
            @Override public void afterTextChanged(Editable s){}});
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);TextView cancel=button("CANCEL",false);cancel.setOnClickListener(v->dialog.dismiss());
        actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(40),1f));actions.addView(new View(this),new LinearLayout.LayoutParams(dp(8),1));
        TextView confirm=button(actionLabel,true);confirm.setOnClickListener(v->{if(selected.size()<minimum){showResultDialog("Choose more cards","Select at least "+minimum+" card(s) to continue.");return;}
            if(maximum>0&&selected.size()>maximum){showResultDialog("Too many cards","Select no more than "+maximum+" card(s).");return;}dialog.dismiss();if(onConfirm!=null)onConfirm.accept(new java.util.LinkedHashSet<>(selected));});
        actions.addView(confirm,new LinearLayout.LayoutParams(0,dp(40),1f));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(40));ap.topMargin=dp(9);shell.addView(actions,ap);
        updatePickerCount(counter,selected.size(),minimum,maximum);dialog.setContentView(shell);dialog.setCanceledOnTouchOutside(true);dialog.show();Window w=dialog.getWindow();
        if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setDimAmount(.76f);w.setLayout(Math.min(dp(540),getResources().getDisplayMetrics().widthPixels-dp(30)),Math.min(dp(690),getResources().getDisplayMetrics().heightPixels-dp(88)));w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
    }

    private void updatePickerCount(TextView counter,int selected,int minimum,int maximum){
        counter.setText(selected+(maximum>0?" / "+maximum:"")+" CARD(S) SELECTED"+(minimum>0?"  ·  MIN "+minimum:""));
    }

    private void showTradeListingDialog(JSONArray inventory){
        if(inventory==null||inventory.length()==0){showResultDialog("No available cards","You need an available tradable card to create a public listing.");return;}
        showCardPickerDialog(inventory,"CHOOSE A CARD TO PUBLISH","Only the card you select will appear on the public trade board. Your other cards stay private.",false,card->showTradeListingForm(card));
    }

    private void showTradeListingForm(JSONObject selected){
        if(selected==null)return;android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(17),dp(16),dp(17),dp(15));
        shell.setBackground(gradient(Color.rgb(24,36,58),Color.rgb(7,13,25),CYAN,dp(18)));
        shell.addView(text("CARDUNIVERSE  /  TRADE DESK",7,CYAN,true));shell.addView(text("Create public listing",18,TEXT,true));
        shell.addView(text("Publish only this card. The rest of your collection remains private.",8,MUTED,false));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(9),dp(9),dp(9),dp(9));
        row.setBackground(gradient(Color.rgb(26,48,59),Color.rgb(12,27,38),CYAN,dp(11)));
        FrameLayout art=new FrameLayout(this);art.setBackground(gradient(Color.rgb(30,45,65),Color.rgb(7,13,25),cardRarityColor(selected.optString("rarity","COMMON")),dp(8)));
        CardArtView image=new CardArtView(this);image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);image.loadArtwork(cardArtworkPath(selected));art.addView(image,new FrameLayout.LayoutParams(-1,-1));
        row.addView(art,new LinearLayout.LayoutParams(dp(72),dp(96)));LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(10),0,0,0);
        TextView name=text(selected.optString("name","Card"),13,TEXT,true);name.setMaxLines(2);info.addView(name);
        info.addView(text(selected.optString("rarity","CARD").toUpperCase(java.util.Locale.ROOT)+" · "+selected.optString("element","AETHER").toUpperCase(java.util.Locale.ROOT),8,GOLD_BRIGHT,true));
        info.addView(text(selected.optString("card_code",""),7,MUTED,false));row.addView(info,new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);shell.addView(row,rp);
        TextView wl=text("WHAT YOU WANT IN RETURN",7,GOLD_BRIGHT,true);wl.setPadding(0,dp(12),0,dp(5));shell.addView(wl);
        EditText wanted=editField("Card name, rarity or open to offers",false);shell.addView(wanted,fieldParams());
        TextView nl=text("MESSAGE TO THE COMMUNITY",7,GOLD_BRIGHT,true);nl.setPadding(0,dp(10),0,dp(5));shell.addView(nl);
        EditText note=editField("Optional note for other collectors",false);shell.addView(note,fieldParams());
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);TextView cancel=button("CANCEL",false);cancel.setOnClickListener(v->dialog.dismiss());
        TextView publish=button("PUBLISH CARD",true);publish.setOnClickListener(v->{JSONObject body=new JSONObject();try{body.put("instanceId",selected.optString("instance_id"));body.put("wantedText",wanted.getText().toString().trim());body.put("message",note.getText().toString().trim());}catch(Exception ignored){}
            publish.setEnabled(false);api.post("/api/trade-board",body,r->ui.post(()->{publish.setEnabled(true);if(!r.ok())showResultDialog("Listing failed",r.error);else{dialog.dismiss();showResultDialog("CARD LISTED",r.data.optString("message","Your selected card is now visible to collectors."));renderTab("trades");}}));});
        actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(40),1f));actions.addView(new View(this),new LinearLayout.LayoutParams(dp(8),1));actions.addView(publish,new LinearLayout.LayoutParams(0,dp(40),1f));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(40));ap.topMargin=dp(14);shell.addView(actions,ap);dialog.setContentView(shell);dialog.setCanceledOnTouchOutside(true);dialog.show();
        Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setDimAmount(.76f);w.setLayout(Math.min(dp(520),getResources().getDisplayMetrics().widthPixels-dp(30)),ViewGroup.LayoutParams.WRAP_CONTENT);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
    }

    private void showPublicTradeRequestDialog(JSONObject listing,JSONArray inventory){
        if(inventory==null||inventory.length()==0){showResultDialog("No cards to offer","You need available tradable cards to make an offer.");return;}
        EditText note=editField("Optional message to the listing owner",false);LinearLayout footer=new LinearLayout(this);footer.setOrientation(LinearLayout.VERTICAL);
        footer.addView(text("MESSAGE TO OWNER",7,GOLD_BRIGHT,true));footer.addView(note,fieldParams());
        showMultiCardPickerDialog(inventory,"BUILD YOUR OFFER","You want "+listing.optString("name","the listed card")+". Choose one to five cards to exchange.",
                "instance_id",java.util.Collections.emptySet(),1,5,"SEND OFFER",footer,chosen->{JSONArray ids=new JSONArray();for(int i=0;i<inventory.length();i++){JSONObject c=inventory.optJSONObject(i);if(c!=null&&chosen.contains(c.optString("instance_id","")))ids.put(c.optString("instance_id"));}
                    JSONObject body=new JSONObject();try{body.put("instanceIds",ids);body.put("message",note.getText().toString().trim());}catch(Exception ignored){}
                    api.post("/api/trade-board/"+listing.optString("listing_id")+"/request",body,r->ui.post(()->{if(!r.ok())showResultDialog("Offer not sent",r.error);else{showResultDialog("TRADE OFFER SENT",r.data.optString("message","The listing owner can review your cards in the Trade Desk."));renderTab("trades");}}));});
    }

    private int tradeCardAccent(String rarity) {
        String value = rarity == null ? "" : rarity.toUpperCase(java.util.Locale.ROOT);
        if (value.contains("MYTHIC") || value.contains("LEGEND")) return GOLD_BRIGHT;
        if (value.contains("EPIC") || value.contains("ULTRA")) return VIOLET;
        if (value.contains("RARE")) return CYAN;
        return MUTED;
    }

    private void updateTradePickerSelection(boolean[] selected, TextView counter,
                                            AlertDialog dialog, TextView[] markers,
                                            LinearLayout[] rows) {
        int count = 0;
        for (boolean value : selected) if (value) count++;
        counter.setText(count + " / 5 CARDS SELECTED");
        Button next = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (next != null) {
            next.setText("NEXT  ·  " + count + " / 5");
            next.setEnabled(count > 0);
            next.setTextColor(count > 0 ? GOLD_BRIGHT : MUTED);
        }
        for (int i = 0; i < selected.length; i++) {
            int accent = selected[i] ? CYAN : Color.rgb(68, 82, 108);
            markers[i].setText(selected[i] ? "✓" : "+");
            markers[i].setTextColor(selected[i] ? Color.rgb(10, 24, 31) : GOLD_BRIGHT);
            markers[i].setBackground(gradient(selected[i] ? CYAN : Color.rgb(32, 42, 63),
                    selected[i] ? Color.rgb(75, 218, 220) : Color.rgb(17, 25, 43),
                    accent, dp(9)));
            rows[i].setBackground(gradient(selected[i] ? Color.rgb(29, 58, 64)
                            : Color.rgb(23, 32, 51),
                    selected[i] ? Color.rgb(15, 32, 43) : Color.rgb(13, 21, 38),
                    accent, dp(11)));
        }
    }

    private void showTradeCreateDialog(JSONArray inventory){
        if(inventory==null||inventory.length()==0){showResultDialog("No tradable cards","No available tradable cards were returned for this account.");return;}
        showMultiCardPickerDialog(inventory,"BUILD YOUR OFFER","Choose one to five cards from your tradable collection. Search by name, rarity or element.",
                "instance_id",java.util.Collections.emptySet(),1,5,"NEXT",null,chosen->{JSONArray ids=new JSONArray();for(int i=0;i<inventory.length();i++){JSONObject c=inventory.optJSONObject(i);if(c!=null&&chosen.contains(c.optString("instance_id","")))ids.put(c.optString("instance_id"));}showTradeRecipientDialog(ids);});
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

    private void showTradeAcceptDialog(String tradeId,JSONArray inventory){
        if(inventory==null||inventory.length()==0){showResultDialog("No exchange cards","You need available tradable cards to accept this offer.");return;}
        showMultiCardPickerDialog(inventory,"CHOOSE YOUR EXCHANGE","Select the cards you will send in return. At least one tradable card is required.",
                "instance_id",java.util.Collections.emptySet(),1,0,"REVIEW EXCHANGE",null,chosen->{JSONArray ids=new JSONArray();for(int i=0;i<inventory.length();i++){JSONObject c=inventory.optJSONObject(i);if(c!=null&&chosen.contains(c.optString("instance_id","")))ids.put(c.optString("instance_id"));}
                    confirmAction("Complete this exchange?","Both sides exchange the selected cards. This action cannot be reversed.",()->api.post("/api/trades/"+tradeId+"/accept",payload("instanceIds",ids),r->ui.post(()->{if(!r.ok())showResultDialog("Trade failed",r.error);else{showResultDialog("TRADE COMPLETE",r.data.optString("message","Cards exchanged."));refreshAccount(()->renderTab("trades"));}})));});
    }


    private LinearLayout phase11Sheet(String eyebrow,String title,String description,int accent){
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(16),dp(16),dp(16),dp(14));
        shell.setBackground(gradient(Color.rgb(27,36,57),Color.rgb(9,15,28),accent,dp(19)));
        TextView label=text(eyebrow,9,accent,true);label.setLetterSpacing(.12f);shell.addView(label);
        TextView name=text(title,21,TEXT,true);name.setPadding(0,dp(3),0,dp(3));shell.addView(name);
        if(!description.isEmpty()){TextView desc=text(description,11,MUTED,false);desc.setLineSpacing(dp(2),1f);shell.addView(desc);}
        addGap(shell,12);return shell;
    }

    private void phase11ShowSheet(android.app.Dialog dialog,LinearLayout shell,int desiredHeight){
        dialog.setContentView(shell);dialog.setCanceledOnTouchOutside(true);dialog.show();
        Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setDimAmount(.80f);
            int width=Math.max(dp(240),Math.min(dp(560),getResources().getDisplayMetrics().widthPixels-dp(24)));
            int height=Math.max(dp(200),Math.min(dp(desiredHeight),getResources().getDisplayMetrics().heightPixels-dp(70)));
            w.setLayout(width,height);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);}
    }

    private FrameLayout phase11Art(JSONObject card,int accent){
        FrameLayout frame=new FrameLayout(this);
        frame.setBackground(gradient(Color.rgb(35,43,62),Color.rgb(8,14,25),accent,dp(10)));frame.setClipToOutline(true);
        TextView sigil=text(elementSigil(card.optString("element","AETHER").toUpperCase(java.util.Locale.ROOT)),30,accent,true);sigil.setGravity(Gravity.CENTER);
        frame.addView(sigil,new FrameLayout.LayoutParams(-1,-1));
        CardArtView art=new CardArtView(this);art.setScaleType(ImageView.ScaleType.FIT_CENTER);art.loadArtwork(cardArtworkPath(card));frame.addView(art,new FrameLayout.LayoutParams(-1,-1));phase16SkinOverlay(frame,card,false);return frame;
    }

    private FrameLayout phase11Avatar(String name,String path,int accent){
        FrameLayout ring=new FrameLayout(this);ring.setBackground(gradient(Color.rgb(65,55,42),Color.rgb(16,24,40),accent,dp(50)));ring.setClipToOutline(true);
        TextView fallback=text(initials(name),16,accent,true);fallback.setGravity(Gravity.CENTER);ring.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setVisibility(View.GONE);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(-1,-1);ip.setMargins(dp(2),dp(2),dp(2),dp(2));ring.addView(image,ip);
        loadProfileAvatar(image,path);return ring;
    }

    private void showTeamCardPicker(JSONArray cards,String[] lineup,int slot,java.util.function.Consumer<JSONObject> choose){
        android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=phase11Sheet("FORMATION  /  POSITION 0"+(slot+1),"Choose your champion","Only cards not assigned to another position are shown.",GOLD_BRIGHT);
        EditText search=editField("Search cards, rarity or element",false);search.setSingleLine(true);search.setTextSize(13);shell.addView(search,new LinearLayout.LayoutParams(-1,dp(46)));
        HorizontalScrollView filters=new HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);
        LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.HORIZONTAL);filters.addView(strip);
        shell.addView(filters,new LinearLayout.LayoutParams(-1,dp(49)));
        TextView count=text("",10,MUTED,true);shell.addView(count);addGap(shell,5);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        ArrayList<JSONObject> eligible=new ArrayList<>();ArrayList<LinearLayout> rows=new ArrayList<>();
        String current=lineup[slot]==null?"":lineup[slot];java.util.HashSet<String> blocked=new java.util.HashSet<>();
        for(int i=0;i<lineup.length;i++)if(i!=slot&&lineup[i]!=null&&!lineup[i].isEmpty())blocked.add(lineup[i]);
        for(int i=0;i<cards.length();i++){JSONObject c=cards.optJSONObject(i);if(c==null||c.optString("card_id").isEmpty()||blocked.contains(c.optString("card_id")))continue;eligible.add(c);}
        eligible.sort((a,b)->Boolean.compare(current.equals(b.optString("card_id")),current.equals(a.optString("card_id"))));
        for(JSONObject c:eligible){
            boolean active=current.equals(c.optString("card_id"));String rarity=c.optString("rarity","COMMON").toUpperCase(java.util.Locale.ROOT);
            int accent=cardRarityColor(rarity);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(9),dp(8),dp(9),dp(8));
            row.setBackground(gradient(active?Color.rgb(49,43,31):Color.rgb(24,34,53),Color.rgb(10,17,30),active?GOLD_BRIGHT:Color.rgb(65,80,107),dp(12)));
            row.addView(phase11Art(c,accent),new LinearLayout.LayoutParams(dp(67),dp(94)));
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(11),0,dp(6),0);
            info.addView(text(rarity+"  ·  "+c.optString("element","AETHER"),9,accent,true));
            TextView name=text(c.optString("name","Card"),13,TEXT,true);name.setMaxLines(2);info.addView(name);
            info.addView(text("Power  "+formatNumber(c.optLong("card_power",c.optLong("formation_power")))+"  ·  ×"+c.optInt("owned_count",1),10,MUTED,false));
            info.addView(text(active?"CURRENT POSITION":c.optString("card_code",""),9,active?GOLD_BRIGHT:MUTED,true));
            row.addView(info,new LinearLayout.LayoutParams(0,-2,1f));
            TextView pick=text(active?"✓":"›",22,active?GOLD_BRIGHT:CYAN,true);pick.setGravity(Gravity.CENTER);row.addView(pick,new LinearLayout.LayoutParams(dp(25),dp(42)));
            row.setFocusable(true);row.setOnClickListener(v->{dialog.dismiss();choose.accept(c);});rows.add(row);list.addView(row,rowParams());
        }
        TextView empty=text("No matching cards.",12,MUTED,false);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(28),0,dp(28));list.addView(empty);
        String[] rarityFilter={"ALL","COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC"};String[] filter={"ALL"};ArrayList<TextView> chips=new ArrayList<>();
        Runnable update=()->{String q=search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);int shown=0;
            for(int i=0;i<eligible.size();i++){JSONObject c=eligible.get(i);String r=c.optString("rarity","COMMON").toUpperCase(java.util.Locale.ROOT);
                String h=(c.optString("name")+" "+r+" "+c.optString("element")+" "+c.optString("card_code")).toLowerCase(java.util.Locale.ROOT);
                boolean visible=(filter[0].equals("ALL")||filter[0].equals(r))&&h.contains(q);rows.get(i).setVisibility(visible?View.VISIBLE:View.GONE);if(visible)shown++;}
            count.setText(shown+" / "+eligible.size()+" AVAILABLE");empty.setVisibility(shown==0?View.VISIBLE:View.GONE);
            for(int i=0;i<chips.size();i++){boolean on=rarityFilter[i].equals(filter[0]);chips.get(i).setTextColor(on?BG:MUTED);chips.get(i).setBackground(gradient(on?GOLD_BRIGHT:PANEL_LIGHT,on?GOLD:PANEL,on?GOLD_BRIGHT:Color.rgb(66,79,105),dp(20)));}};
        for(String r:rarityFilter){TextView chip=text(r,9,MUTED,true);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(12),0,dp(12),0);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(34));cp.setMargins(0,dp(8),dp(6),0);strip.addView(chip,cp);chips.add(chip);chip.setOnClickListener(v->{filter[0]=r;update.run();});}
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){update.run();}public void afterTextChanged(Editable e){}});
        update.run();
        LinearLayout footer=new LinearLayout(this);TextView clear=button("CLEAR SLOT",false),close=button("CANCEL",false);
        footer.addView(clear,new LinearLayout.LayoutParams(0,dp(44),1f));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(44),1f);fp.leftMargin=dp(8);footer.addView(close,fp);
        clear.setOnClickListener(v->{dialog.dismiss();choose.accept(null);});close.setOnClickListener(v->dialog.dismiss());addGap(shell,8);shell.addView(footer);
        shell.setFocusableInTouchMode(true);shell.requestFocus();phase11ShowSheet(dialog,shell,690);
    }

    private void renderEvolution(JSONObject data) {
        JSONObject wallet=data.optJSONObject("wallet");if(wallet==null)wallet=new JSONObject();
        final JSONObject walletSnapshot=wallet;
        addModeSpotlight("ASCENSION SANCTUM","Awaken your champions","Train your cards, ascend their star ranks and unlock their true potential.","✧",PINK);
        LinearLayout balances=new LinearLayout(this);
        balances.addView(profileStat("GOLD",formatNumber(wallet.optLong("coins")),GOLD_BRIGHT),weightParams(1));
        balances.addView(profileStat("GEMS",formatNumber(wallet.optLong("gems")),VIOLET),weightParams(1));
        balances.addView(profileStat("SHARDS",formatNumber(wallet.optLong("shards")),CYAN),weightParams(1));content.addView(balances);addGap(content,14);
        JSONArray cards=data.optJSONArray("cards");
        if(cards==null||cards.length()==0){showNotice("Collect cards from Summon Hall to unlock ascension.");return;}
        int max=Math.max(1,data.optInt("max_level",30));
        content.addView(text("ASCENSION ROSTER  /  "+cards.length()+" CARDS",11,GOLD_BRIGHT,true));addGap(content,6);
        EditText search=editField("Find a card…",false);search.setTextSize(13);content.addView(search,new LinearLayout.LayoutParams(-1,dp(44)));addGap(content,7);
        ArrayList<LinearLayout> rows=new ArrayList<>();ArrayList<String> keys=new ArrayList<>();
        for(int i=0;i<cards.length();i++){JSONObject c=cards.optJSONObject(i);if(c==null)continue;
            String rarity=c.optString("rarity","COMMON").toUpperCase(java.util.Locale.ROOT);int accent=cardRarityColor(rarity);
            LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(10),dp(10),dp(10),dp(10));
            panel.setBackground(gradient(Color.rgb(27,35,53),Color.rgb(10,17,31),accent,dp(14)));
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(phase11Art(c,accent),new LinearLayout.LayoutParams(dp(84),dp(118)));
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(12),0,0,0);
            info.addView(text(rarity+"  ·  "+c.optString("element","AETHER"),9,accent,true));info.addView(text(c.optString("name","Card"),15,TEXT,true));
            info.addView(text(starMarks(c.optInt("star_rank")),15,GOLD_BRIGHT,true));int level=c.optInt("level",1);
            info.addView(text("LEVEL "+level+" / "+max,10,CYAN,true));
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(max);bar.setProgress(Math.min(max,level));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));info.addView(bar,new LinearLayout.LayoutParams(-1,dp(7)));
            info.addView(text(formatNumber(c.optLong("card_power"))+" POWER  ·  "+c.optInt("copies_available")+" COPIES",9,MUTED,true));head.addView(info,new LinearLayout.LayoutParams(0,-2,1f));panel.addView(head);
            TextView action=button("OPEN ASCENSION  ›",true);action.setOnClickListener(v->showEvolutionCardDialog(c,walletSnapshot,max));addGap(panel,9);panel.addView(action,new LinearLayout.LayoutParams(-1,dp(43)));content.addView(panel,rowParams());rows.add(panel);
            keys.add((c.optString("name")+" "+rarity+" "+c.optString("element")+" "+c.optString("card_code")).toLowerCase(java.util.Locale.ROOT));
        }
        TextView empty=text("No matching cards.",12,MUTED,false);empty.setVisibility(View.GONE);content.addView(empty);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){String q=s.toString().trim().toLowerCase(java.util.Locale.ROOT);int n=0;for(int i=0;i<rows.size();i++){boolean on=keys.get(i).contains(q);rows.get(i).setVisibility(on?View.VISIBLE:View.GONE);if(on)n++;}empty.setVisibility(n==0?View.VISIBLE:View.GONE);}public void afterTextChanged(Editable e){}});
    }

    private void showEvolutionCardDialog(JSONObject c,JSONObject wallet,int maxLevel) {
        android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        int accent=cardRarityColor(c.optString("rarity","COMMON").toUpperCase(java.util.Locale.ROOT));
        LinearLayout shell=phase11Sheet("ASCENSION SANCTUM",c.optString("name","Card Evolution"),c.optString("rarity")+"  ·  "+c.optString("element")+"  ·  "+c.optString("card_code"),accent);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        FrameLayout art=phase11Art(c,accent);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(128),dp(180));ap.gravity=Gravity.CENTER_HORIZONTAL;body.addView(art,ap);
        TextView stars=text(starMarks(c.optInt("star_rank")),19,GOLD_BRIGHT,true);stars.setGravity(Gravity.CENTER);body.addView(stars);
        LinearLayout stats=new LinearLayout(this);stats.addView(profileStat("LEVEL",""+c.optInt("level",1),CYAN),weightParams(1));stats.addView(profileStat("POWER",formatNumber(c.optLong("card_power")),GOLD),weightParams(1));stats.addView(profileStat("COPIES",""+c.optInt("copies_available"),VIOLET),weightParams(1));body.addView(stats);addGap(body,10);
        LinearLayout skill=sectionPanel();skill.addView(text("SIGNATURE ABILITY",9,accent,true));skill.addView(text(c.optString("skill_name","Firstlight Gift"),13,TEXT,true));skill.addView(text(c.optString("skill_description","Unlocks at 2★"),11,MUTED,false));body.addView(skill,rowParams());
        JSONObject cost=c.optJSONObject("next_star_cost");
        String levelCost=formatNumber(c.optLong("level_cost"))+" Gold  ·  "+formatNumber(c.optLong("level_shards"))+" Shards";
        String starCost=cost==null?"Next ascension cost unavailable":cost.optInt("duplicates")+" duplicates  ·  "+formatNumber(cost.optLong("coins"))+" Gold\n"+formatNumber(cost.optLong("gems"))+" Gems  ·  "+formatNumber(cost.optLong("shards"))+" Shards";
        for(int i=0;i<2;i++){final String action=i==0?"LEVEL_UP":"ASCEND";final String price=i==0?levelCost:starCost;
            LinearLayout option=sectionPanel();option.setBackground(gradient(Color.rgb(27,38,54),Color.rgb(12,20,33),i==0?CYAN:PINK,dp(12)));
            option.addView(text(i==0?"TRAIN  /  LEVEL UP":"AWAKEN  /  ASCEND",12,i==0?CYAN:PINK,true));
            option.addView(text(i==0?"Increase the card’s level.":"Raise the star rank using duplicate copies.",11,MUTED,false));addGap(option,4);
            boolean ascend = action.equals("ASCEND");
            int level = c.optInt("level", 1);
            int rank = c.optInt("star_rank");
            boolean atCap = ascend ? cost == null : level >= maxLevel;
            long currentPower = c.optLong("card_power", -1);
            long powerGain = 16;
            if (ascend) {
                // Match the Worker’s Evolution card_power formula, including the skill unlock step.
                long skillPowerPerRank = 10L * c.optInt("skill_power");
                int unlockRank = c.optInt("unlock_star_rank", 1);
                long currentSkill = rank >= unlockRank ? rank * skillPowerPerRank : 0;
                long upgradedSkill = rank + 1 >= unlockRank ? (rank + 1) * skillPowerPerRank : 0;
                powerGain = 70 + upgradedSkill - currentSkill;
            }
            String preview = atCap ? (ascend ? "No further ascension available" : "Maximum level reached")
                    : ascend ? "Stars  " + (rank + 1) + "★ → " + (rank + 2) + "★"
                    : "Level  " + level + " → " + (level + 1);
            if (!atCap && currentPower >= 0) {
                preview += "\nPower  " + formatNumber(currentPower) + " → "
                        + formatNumber(currentPower + powerGain) + " ("
                        + (powerGain >= 0 ? "+" : "−") + formatNumber(Math.abs(powerGain)) + ")";
            }
            final String upgradePreview = preview;
            option.addView(text("CARD POWER PREVIEW", 10, GOLD_BRIGHT, true));
            option.addView(text(upgradePreview, 12, TEXT, true));
            addGap(option, 6);
            if (!atCap) {
                option.addView(text(price, 11, GOLD_BRIGHT, true));
                boolean enough = ascend
                        ? c.optInt("copies_available") > cost.optInt("duplicates")
                            && wallet.optLong("coins") >= cost.optLong("coins")
                            && wallet.optLong("gems") >= cost.optLong("gems")
                            && wallet.optLong("shards") >= cost.optLong("shards")
                        : wallet.optLong("coins") >= c.optLong("level_cost")
                            && wallet.optLong("shards") >= c.optLong("level_shards");
                if (!enough) option.addView(text("Missing upgrade materials or currency.", 11, RED, false));
            }
            addGap(option, 7);
            TextView act=button(i==0?"LEVEL UP":"ASCEND",i==1);option.addView(act,new LinearLayout.LayoutParams(-1,dp(43)));
            act.setEnabled(!atCap);
            act.setOnClickListener(v->{dialog.dismiss();String warning=action.equals("ASCEND")?"Ascension permanently consumes duplicate copies of this card while preserving one primary copy.":"The server verifies your Gold and Shard balance before upgrading.";
                confirmAction(action.equals("ASCEND")?"Ascend card?":"Level up card?",warning+"\n\n"+upgradePreview+"\n\n"+price,()->api.post("/api/evolution/action",payload("action",action,"cardId",c.optString("card_id"),"requestId",newActionId()),r->ui.post(()->{
                    if(!r.ok())showResultDialog("Evolution unavailable",r.error);else{showResultDialog("EVOLUTION UPDATED",r.data.optString("message","Your card has been upgraded."));refreshAccount(()->renderTab("evolution"));}
                })));});body.addView(option,rowParams());
        }
        TextView close=button("CLOSE",false);close.setOnClickListener(v->dialog.dismiss());addGap(shell,8);shell.addView(close,new LinearLayout.LayoutParams(-1,dp(44)));phase11ShowSheet(dialog,shell,710);
    }

    private String modeDeckKey(String mode) {
        if (me == null || me.optString("user_id").isEmpty()) return "";
        return me.optString("user_id") + ":" + mode;
    }

    private void postWithModeDeck(String mode, String path, JSONObject body, ApiClient.Callback callback) {
        String deckId = getSharedPreferences("cu_mode_decks", MODE_PRIVATE).getString(modeDeckKey(mode), "");
        api.postWithDeck(deckId, path, body, callback);
    }

    private void addModeDeckSelector(LinearLayout panel, String mode, String label,
                                     String[] deckIds, String[] deckNames) {
        String key = modeDeckKey(mode);
        if (key.isEmpty()) return;
        android.content.SharedPreferences preferences = getSharedPreferences("cu_mode_decks", MODE_PRIVATE);
        String current = preferences.getString(key, "");
        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        ids.add("");
        names.add("Use active team deck");
        for (int i = 0; i < deckIds.length; i++) {
            if (deckIds[i].isEmpty()) continue;
            ids.add(deckIds[i]);
            names.add(deckNames[i]);
        }
        int index = ids.indexOf(current);
        panel.addView(text(label, 11, GOLD_BRIGHT, true));
        TextView choose = button(index < 0 ? "Deck unavailable · choose again" : names.get(index), false);
        choose.setTextSize(12);
        panel.addView(choose, rowParams());
        choose.setOnClickListener(v -> showChoiceDialog(label, "Choose from your saved decks.",
                names.toArray(new String[0]), selected -> {
                    preferences.edit().putString(key, ids.get(selected)).apply();
                    choose.setText(names.get(selected));
                }));
    }

    private void renderFormation(JSONObject data) {
        JSONArray cards = data.optJSONArray("cards");
        JSONArray saved = data.optJSONArray("formation");
        JSONArray decks = data.optJSONArray("decks");
        if (cards == null) cards = new JSONArray();
        if (saved == null) saved = new JSONArray();
        if (decks == null) decks = new JSONArray();
        if (cards.length() == 0) {
            showNotice("Collect at least one card before building a team.");
            return;
        }

        Map<Integer, String> savedSlots = new LinkedHashMap<>();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject slot = saved.optJSONObject(i);
            if (slot != null) savedSlots.put(slot.optInt("slot_no"), slot.optString("card_id"));
        }
        ArrayList<JSONObject> deckItems = new ArrayList<>();
        JSONObject activeDeck = null;
        for (int i = 0; i < decks.length(); i++) {
            JSONObject deck = decks.optJSONObject(i);
            if (deck == null) continue;
            deckItems.add(deck);
            if (deck.optInt("is_active") == 1) activeDeck = deck;
        }
        if (activeDeck != null) {
            JSONArray activeCards = activeDeck.optJSONArray("cards");
            if (activeCards != null) {
                savedSlots.clear();
                for (int i = 0; i < Math.min(5, activeCards.length()); i++) {
                    String cardId = activeCards.optString(i, "");
                    if (!cardId.isEmpty()) savedSlots.put(i + 1, cardId);
                }
            }
        }
        final String activeDeckName = activeDeck == null
                ? "Main Deck" : activeDeck.optString("name", "Main Deck");

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

        LinearLayout deckPanel = sectionPanel();
        deckPanel.setBackground(gradient(Color.rgb(38, 39, 67), Color.rgb(9, 15, 29),
                Color.rgb(147, 123, 220), dp(12)));
        deckPanel.addView(text("SAVED TEAM DECKS", 8, GOLD_BRIGHT, true));
        deckPanel.addView(text("Keep separate lineups and switch between them without rebuilding your team.",
                8, MUTED, false));
        String[] deckNames = new String[Math.max(1, deckItems.size())];
        String[] deckIds = new String[Math.max(1, deckItems.size())];
        int selectedDeck = 0;
        for (int i = 0; i < deckItems.size(); i++) {
            JSONObject deck = deckItems.get(i);
            deckNames[i] = deck.optString("name", "Team Deck " + (i + 1));
            deckIds[i] = deck.optString("deck_id", "");
            if (deck.optInt("is_active") == 1) selectedDeck = i;
        }
        if (deckItems.isEmpty()) {
            deckNames[0] = "Main Deck";
            deckIds[0] = "";
        }
        Spinner deckSelector = new Spinner(this);
        ArrayAdapter<String> deckAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, deckNames);
        deckAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deckSelector.setAdapter(deckAdapter);
        deckSelector.setBackground(gradient(Color.rgb(22, 31, 52), Color.rgb(9, 15, 28),
                Color.rgb(157, 132, 222), dp(9)));
        deckSelector.setPadding(dp(9), 0, dp(9), 0);
        deckSelector.setSelection(selectedDeck);
        LinearLayout.LayoutParams selectorParams = new LinearLayout.LayoutParams(-1, dp(42));
        selectorParams.topMargin = dp(7);
        deckPanel.addView(deckSelector, selectorParams);
        EditText deckName = editField("Deck name (e.g. Fire Team, Arena Team)", false);
        deckName.setSingleLine(true);
        deckName.setText(activeDeckName);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, dp(42));
        nameParams.topMargin = dp(7);
        deckPanel.addView(deckName, nameParams);

        LinearLayout deckActions = new LinearLayout(this);
        deckActions.setOrientation(LinearLayout.HORIZONTAL);
        TextView saveDeck = button("SAVE DECK", true);
        TextView saveAs = button("SAVE AS NEW", false);
        LinearLayout.LayoutParams deckButtonParams = new LinearLayout.LayoutParams(0, dp(39), 1f);
        deckButtonParams.setMargins(0, dp(7), dp(4), 0);
        deckActions.addView(saveDeck, deckButtonParams);
        LinearLayout.LayoutParams newButtonParams = new LinearLayout.LayoutParams(0, dp(39), 1f);
        newButtonParams.setMargins(dp(4), dp(7), 0, 0);
        deckActions.addView(saveAs, newButtonParams);
        deckPanel.addView(deckActions);
        TextView deleteDeck = button("DELETE SELECTED DECK", false);
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(-1, dp(34));
        deleteParams.topMargin = dp(6);
        deckPanel.addView(deleteDeck, deleteParams);
        content.addView(deckPanel, rowParams());
        if (data.has("decks")) {
            LinearLayout modeDecks = sectionPanel();
            modeDecks.addView(text("MODE-SPECIFIC DECKS", 13, TEXT, true));
            modeDecks.addView(text("Choose which saved deck Android activates when you enter each mode.",
                    11, MUTED, false));
            addGap(modeDecks, 8);
            addModeDeckSelector(modeDecks, "pvp", "PVP · Arena and Ranked", deckIds, deckNames);
            addModeDeckSelector(modeDecks, "pve", "PVE · Adventure, Dungeons and Raids", deckIds, deckNames);
            content.addView(modeDecks, rowParams());
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
        HorizontalScrollView lineupScroll = new HorizontalScrollView(this);
        lineupScroll.setHorizontalScrollBarEnabled(false);
        lineupScroll.setClipToPadding(false);
        lineupScroll.setPadding(0, 0, dp(3), dp(5));
        lineupScroll.addView(grid, new HorizontalScrollView.LayoutParams(-2, -2));
        final JSONArray pickerCards = cards;
        final String[] slotCardIds=new String[5];
        final LinearLayout comboPreview=sectionPanel();
        final int[] comboRevision={0};
        for(int i=0;i<5;i++){final int slotIndex=i;LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(6),dp(7),dp(6),dp(7));
            panel.setBackground(gradient(PANEL_LIGHT,PANEL,Color.rgb(72,88,67),dp(10)));panel.addView(text("POSITION 0"+(i+1),7,GOLD,true));
            FrameLayout art=new FrameLayout(this);art.setBackground(gradient(Color.rgb(26,45,37),Color.rgb(10,19,16),Color.rgb(82,104,77),dp(8)));
            TextView sigil=text("＋",29,MUTED,true);sigil.setGravity(Gravity.CENTER);art.addView(sigil,new FrameLayout.LayoutParams(-1,-1));
            CardArtView illustration=new CardArtView(this);illustration.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);art.addView(illustration,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout.LayoutParams ar=new LinearLayout.LayoutParams(-1,dp(164));ar.topMargin=dp(6);panel.addView(art,ar);
            TextView cardName=text("EMPTY SLOT",9,TEXT,true);cardName.setGravity(Gravity.CENTER);cardName.setMaxLines(2);cardName.setPadding(0,dp(5),0,dp(2));panel.addView(cardName);
            TextView cardMeta=text("Choose a card",7,MUTED,false);cardMeta.setGravity(Gravity.CENTER);cardMeta.setMaxLines(2);panel.addView(cardMeta);
            String savedId=savedSlots.get(i+1);JSONObject initial=savedId==null?null:cardsById.get(savedId);slotCardIds[i]=initial==null?"":initial.optString("card_id","");
            updateFormationCardPreview(initial,art,illustration,sigil,cardName,cardMeta);
            TextView selector=button(initial==null?"＋ CHOOSE CARD":"CHANGE · "+initial.optString("name","CARD"),false);selector.setTextSize(7);
            selector.setOnClickListener(v->showTeamCardPicker(pickerCards,slotCardIds,slotIndex,chosen->{
                String id=chosen==null?"":chosen.optString("card_id","");for(int j=0;j<slotCardIds.length;j++)if(j!=slotIndex&&!id.isEmpty()&&id.equals(slotCardIds[j])){showResultDialog("CARD ALREADY IN LINEUP","Each card can only occupy one team position.");return;}
                slotCardIds[slotIndex]=id;updateFormationCardPreview(chosen,art,illustration,sigil,cardName,cardMeta);selector.setText(chosen==null?"＋ CHOOSE CARD":"CHANGE · "+chosen.optString("name","CARD"));refreshPhase15Combos(comboPreview,slotCardIds,comboRevision);}));
            LinearLayout.LayoutParams pr=new LinearLayout.LayoutParams(-1,dp(40));pr.topMargin=dp(5);panel.addView(selector,pr);
            LinearLayout.LayoutParams col=new LinearLayout.LayoutParams(dp(154),-2);col.setMargins(dp(4),dp(5),dp(4),dp(5));grid.addView(panel,col);}
        content.addView(lineupScroll,rowParams());
        content.addView(comboPreview,rowParams());refreshPhase15Combos(comboPreview,slotCardIds,comboRevision);

        final boolean[] selectorReady = {false};
        deckSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!selectorReady[0]) { selectorReady[0] = true; return; }
                if (position < 0 || position >= deckIds.length || deckIds[position].isEmpty()) return;
                String targetId = deckIds[position];
                api.post("/api/decks/" + Uri.encode(targetId) + "/activate", new JSONObject(), result -> ui.post(() -> {
                    if (!result.ok()) {
                        showResultDialog("TEAM SWITCH FAILED", result.error);
                        return;
                    }
                    renderTab("formation");
                }));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        Runnable collectAndSave = () -> {
            JSONArray chosen = new JSONArray();
            java.util.HashSet<String> unique = new java.util.HashSet<>();
            for (String cardId : slotCardIds) {
                if (cardId.isEmpty()) continue;
                if (!unique.add(cardId)) {
                    showResultDialog("DUPLICATE CARD",
                            "Each card can only occupy one team position.");
                    return;
                }
                chosen.put(cardId);
            }
            String name = deckName.getText().toString().trim();
            if (name.isEmpty()) {
                deckName.setError("Enter a deck name.");
                return;
            }
            JSONObject target = deckItems.isEmpty() ? null : deckItems.get(Math.max(0,
                    Math.min(deckItems.size() - 1, deckSelector.getSelectedItemPosition())));
            if (target == null || target.optString("deck_id", "").isEmpty()) {
                api.post("/api/decks", payload("name", name, "cards", chosen), result ->
                        ui.post(() -> {
                            if (!result.ok()) showResultDialog("DECK NOT SAVED", result.error);
                            else {
                                showResultDialog("NEW DECK SAVED", result.data.optString("message",
                                        "Your deck was saved and activated."));
                                renderTab("formation");
                            }
                        }));
            } else {
                String deckId = target.optString("deck_id", "");
                api.put("/api/decks/" + Uri.encode(deckId),
                        payload("name", name, "cards", chosen), result -> ui.post(() -> {
                            if (!result.ok()) showResultDialog("DECK NOT SAVED", result.error);
                            else {
                                showResultDialog("DECK SAVED", result.data.optString("message",
                                        "Your team deck was saved."));
                                renderTab("formation");
                            }
                        }));
            }
        };
        saveDeck.setOnClickListener(v -> collectAndSave.run());
        saveAs.setOnClickListener(v -> {
            JSONArray chosen = new JSONArray();
            java.util.HashSet<String> unique = new java.util.HashSet<>();
            for (String cardId : slotCardIds) {
                if (cardId.isEmpty()) continue;
                if (!unique.add(cardId)) {
                    showResultDialog("DUPLICATE CARD",
                            "Each card can only occupy one team position.");
                    return;
                }
                chosen.put(cardId);
            }
            String name = deckName.getText().toString().trim();
            if (name.isEmpty()) {
                deckName.setError("Enter a name for the new deck.");
                return;
            }
            if (name.equalsIgnoreCase(activeDeckName)) name = name + " Copy";
            final String newName = name;
            saveAs.setEnabled(false);
            api.post("/api/decks", payload("name", newName, "cards", chosen), result -> ui.post(() -> {
                saveAs.setEnabled(true);
                if (!result.ok()) showResultDialog("NEW DECK NOT SAVED", result.error);
                else {
                    showResultDialog("NEW DECK SAVED", result.data.optString("message",
                            "Your new team deck was saved and activated."));
                    renderTab("formation");
                }
            }));
        });
        deleteDeck.setEnabled(deckItems.size() > 1);
        deleteDeck.setOnClickListener(v -> {
            int position = deckSelector.getSelectedItemPosition();
            if (position < 0 || position >= deckIds.length || deckIds[position].isEmpty()) return;
            String deckId = deckIds[position];
            String name = deckNames[position];
            confirmAction("Delete " + name + "?",
                    "This removes only the saved deck. Your cards and collection remain untouched.",
                    () -> api.delete("/api/decks/" + Uri.encode(deckId), result -> ui.post(() -> {
                        if (!result.ok()) showResultDialog("DECK NOT DELETED", result.error);
                        else {
                            android.content.SharedPreferences preferences = getSharedPreferences("cu_mode_decks", MODE_PRIVATE);
                            for (String mode : new String[]{"pvp", "pve"}) {
                                String key = modeDeckKey(mode);
                                if (deckId.equals(preferences.getString(key, ""))) preferences.edit().remove(key).apply();
                            }
                            showResultDialog("DECK DELETED", result.data.optString("message",
                                    "The saved deck was removed."));
                            renderTab("formation");
                        }
                    })));
        });
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
        meta.setText(rarity + " · " + element + " · POWER "
                + formatNumber(card.optLong("formation_power")) + " · ×"
                + card.optInt("owned_count"));
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
        addPhase13Link("Set Completion Rewards", "Track and claim milestones for each card set.",
                () -> renderTab("binder_completion"));

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
            art.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
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
        addPhase13Link("Dynamic World Events", "Shared weekly objectives across game modes.", () -> renderTab("world_events"));
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
        boolean beta = data.optBoolean("betaTesting") || (me != null && me.optBoolean("is_beta_tester"));
        if (beta) addPhase13Link("Training Grounds", "Practice modes for beta accounts.", () -> renderTab("training"));
        if (!beta && !data.optBoolean("rankedBanned")) {
            TextView enter = button("FIND RANKED MATCH", true);
            enter.setOnClickListener(v -> confirmAction("Queue for Ranked Arena?",
                    "Ranked matchmaking uses your live rating and season.", () ->
                    postWithModeDeck("pvp", "/api/arena/queue", payload("mode", "RANKED"), result -> ui.post(() -> {
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

    private void showBinderCardsDialog(String binderId){
        api.post("/api/binders/"+binderId,payload("set_active",true),a->ui.post(()->{if(!a.ok()){showResultDialog("Binder unavailable",a.error);return;}
            api.get("/api/binders",b->ui.post(()->{if(!b.ok()){showResultDialog("Unable to load binder",b.error);return;}
                api.get("/api/cards",c->ui.post(()->{if(!c.ok()){showResultDialog("Unable to load card archive",c.error);return;}
                    JSONArray bound=b.data.optJSONArray("cards"),all=c.data.optJSONArray("cards");if(bound==null)bound=new JSONArray();if(all==null)all=new JSONArray();
                    java.util.HashSet<String> preselected=new java.util.HashSet<>();for(int i=0;i<bound.length();i++){JSONObject x=bound.optJSONObject(i);if(x!=null)preselected.add(x.optString("card_id"));}
                    JSONArray owned=new JSONArray();for(int i=0;i<all.length();i++){JSONObject x=all.optJSONObject(i);if(x!=null&&x.optInt("owned_count")>0)owned.put(x);}
                    if(owned.length()==0){showResultDialog("No owned cards","Collect cards before adding them to a custom binder.");return;}
                    showMultiCardPickerDialog(owned,"CARDS IN THIS BINDER","Choose up to 200 unique cards. Existing selections are already marked.","card_id",preselected,0,200,"SAVE BINDER",null,chosen->{
                        JSONArray ids=new JSONArray();for(int i=0;i<owned.length();i++){JSONObject x=owned.optJSONObject(i);if(x!=null&&chosen.contains(x.optString("card_id","")))ids.put(x.optString("card_id"));}
                        api.post("/api/binders/"+binderId+"/cards",payload("cardIds",ids),r->ui.post(()->{if(!r.ok())showResultDialog("Binder contents not saved",r.error);else{showResultDialog("BINDER UPDATED",r.data.optString("message","Binder cards saved."));renderTab("binders");}}));});
                }));}));}));
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

    private android.app.Dialog showSummonOpeningDialog(String packName) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setGravity(Gravity.CENTER);
        shell.setPadding(dp(24), dp(22), dp(24), dp(22));
        shell.setBackground(gradient(Color.rgb(29, 27, 65), Color.rgb(5, 10, 24),
                Color.rgb(155, 125, 238), dp(18)));
        FrameLayout seal = createSummonSeal(GOLD_BRIGHT, "✦", 142);
        shell.addView(seal, new LinearLayout.LayoutParams(dp(142), dp(142)));
        TextView title = text("ASTRAL GATE OPENING", 15, TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(17);
        shell.addView(title, titleParams);
        TextView detail = text("The seal is breaking. Your cards are crossing the veil…",
                9, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(6);
        shell.addView(detail, detailParams);
        TextView pack = text(String.valueOf(packName).toUpperCase(java.util.Locale.ROOT),
                8, GOLD_BRIGHT, true);
        pack.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams packParams = new LinearLayout.LayoutParams(-1, -2);
        packParams.topMargin = dp(12);
        shell.addView(pack, packParams);
        dialog.setContentView(shell);
        dialog.setCancelable(false);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(dp(420), getResources().getDisplayMetrics().widthPixels - dp(42)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setWindowAnimations(android.R.style.Animation_Dialog);
        }
        if (!reduceMotionEnabled) {
            seal.animate().rotationBy(360f).scaleX(1.07f).scaleY(1.07f)
                    .setDuration(900L).withEndAction(() ->
                            seal.animate().scaleX(1f).scaleY(1f).setDuration(220L).start()).start();
        }
        return dialog;
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
        copy.addView(text(cards.length() + " card" + (cards.length() == 1 ? "" : "s")
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
        ArrayList<View> revealTiles = new ArrayList<>();
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.optJSONObject(i);
            if (card == null) continue;
            if (visible % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout tile = createSummonRevealTile(card, visible + 1);
            if (!reduceMotionEnabled) {
                tile.setAlpha(0.12f);
                tile.setRotationY(82f);
                tile.setScaleX(0.90f);
                tile.setScaleY(0.90f);
                tile.setCameraDistance(dp(1800));
            }
            revealTiles.add(tile);
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
        if (!reduceMotionEnabled) {
            for (int i = 0; i < revealTiles.size(); i++) {
                View tile = revealTiles.get(i);
                tile.postDelayed(() -> tile.animate().alpha(1f).rotationY(0f)
                        .scaleX(1f).scaleY(1f).setDuration(340L)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start(),
                        70L + i * 85L);
            }
            seal.animate().rotationBy(180f).scaleX(1.13f).scaleY(1.13f)
                    .setDuration(480L).withEndAction(() ->
                            seal.animate().scaleX(1f).scaleY(1f).setDuration(220L).start()).start();
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
        image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
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
                if (currencyLabel != null) currencyLabel.setText("LV " + formatNumber(me.optLong("collector_level", 1)) + "  ·  " + walletLine());
            }
            if (after != null) after.run();
        }));
    }

    // Refresh shared account data quietly while the game stays in the foreground.
    private void startAutoSync() {
        ui.removeCallbacks(autoSyncTick);
        if (api == null || me == null || appRoot == null || !autoSyncEnabled) return;
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
            if (currencyLabel != null) currencyLabel.setText("LV " + formatNumber(me.optLong("collector_level", 1)) + "  ·  " + walletLine());
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
            postWithModeDeck("pvp", "/api/arena/queue", payload, result -> ui.post(() -> {
                if (!result.ok()) {
                    showResultDialog("Matchmaking unavailable", result.error);
                    return;
                }
                JSONObject active = result.data.optJSONObject("activeMatch");
                showResultDialog(active != null
                                ? (active.optBoolean("opponentIsBot") ? "◈ TRAINING BOT FOUND" : "⚔ MATCH FOUND")
                                : "MATCHMAKING",
                        active != null ? "Opponent found: " + active.optString("opponentName", "Collector")
                                + (active.optBoolean("opponentIsBot") ? "\nTraining match · not a human player." : "")
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

    private void submitArenaMove(String matchId, String move, int turn) {
        if (matchId == null || matchId.isEmpty()) return;
        JSONObject payload = new JSONObject();
        try { payload.put("move", move); payload.put("turn",turn); payload.put("requestId", "arena:"+matchId+":"+turn+":"+UUID.randomUUID()); } catch (Exception ignored) {}
        api.post("/api/arena/matches/" + matchId + "/move", payload, result -> ui.post(() -> {
            if (!result.ok()) {
                showResultDialog("Battle state refresh", "Your match stays on the server during the reconnect window. Refreshing the match avoids submitting a second action.\n\n"+result.error);
                renderTab("arena");
                return;
            }
            if (result.data.optBoolean("completed") && "arena".equals(activeTab)) {
                JSONArray recent = result.data.optJSONArray("recent");
                if (recent != null) for (int i = 0; i < recent.length(); i++) {
                    JSONObject match = recent.optJSONObject(i);
                    if (match != null && matchId.equals(match.optString("match_id"))) {
                        renderTab("arena");
                        fetchArenaRecap(match);
                        return;
                    }
                }
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
        final int[] presets = {50, 100, 250, 500, 1000};
        final int[] selected = {100};
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(17), dp(18), dp(15));
        panel.setBackground(gradient(Color.rgb(37, 48, 72), Color.rgb(8, 15, 29),
                GOLD, dp(17)));
        panel.addView(text("GUILD TREASURY", 8, GOLD_BRIGHT, true));
        panel.addView(text("Support your guild", 19, TEXT, true));
        panel.addView(text("Choose a quick amount or enter a custom Gold contribution.",
                8, MUTED, false));
        addGap(panel, 9);

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        final EditText[] amountHolder = new EditText[1];
        for (int index = 0; index < presets.length; index++) {
            final int amount = presets[index];
            TextView chip = button(formatNumber(amount), amount == selected[0]);
            chip.setTextSize(8);
            chip.setOnClickListener(v -> {
                selected[0] = amount;
                for (int i = 0; i < choices.getChildCount(); i++) {
                    View child = choices.getChildAt(i);
                    if (child instanceof TextView) {
                        int value = presets[i];
                        child.setBackground(gradient(value == selected[0]
                                ? Color.rgb(213, 169, 91) : Color.rgb(26, 36, 56),
                                Color.rgb(14, 23, 40), value == selected[0] ? GOLD_BRIGHT
                                        : Color.rgb(67, 87, 117), dp(8)));
                        ((TextView) child).setTextColor(value == selected[0] ? BG : TEXT);
                    }
                }
                if (amountHolder[0] != null) amountHolder[0].setText(String.valueOf(amount));
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            cp.setMargins(dp(2), 0, dp(2), 0);
            choices.addView(chip, cp);
        }
        panel.addView(choices);
        addGap(panel, 8);
        panel.addView(text("CUSTOM AMOUNT · GOLD", 7, GOLD_BRIGHT, true));
        EditText amountInput = editField("Enter Gold amount", false);
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        amountInput.setText("100");
        amountHolder[0] = amountInput;
        panel.addView(amountInput, fieldParams());
        long availableGold = me != null && me.optJSONObject("wallet") != null
                ? me.optJSONObject("wallet").optLong("coins") : 0;
        panel.addView(text("Available Gold: " + formatNumber(availableGold), 8, MUTED, false));

        TextView contribute = button("CONTRIBUTE TO TREASURY", true);
        LinearLayout.LayoutParams contributeParams = new LinearLayout.LayoutParams(-1, dp(42));
        contributeParams.topMargin = dp(10);
        panel.addView(contribute, contributeParams);
        contribute.setOnClickListener(v -> {
            String value = amountInput.getText().toString().trim();
            int amount;
            try { amount = Integer.parseInt(value); }
            catch (Exception error) { amount = 0; }
            if (amount < 1 || amount > 1000000) {
                amountInput.setError("Enter an amount from 1 to 1,000,000 Gold.");
                return;
            }
            if (amount > availableGold) {
                amountInput.setError("Your wallet does not have enough Gold.");
                return;
            }
            final int gold = amount;
            confirmAction("Contribute " + formatNumber(gold) + " Gold?",
                    "Gold moves from your wallet into the shared guild treasury.", () -> {
                contribute.setEnabled(false);
                contribute.setText("PROCESSING…");
                JSONObject payload = new JSONObject();
                try { payload.put("amount", gold); payload.put("requestId", newActionId()); }
                catch (Exception ignored) {}
                api.post("/api/guilds/contribute", payload, result -> ui.post(() -> {
                    contribute.setEnabled(true);
                    contribute.setText("CONTRIBUTE TO TREASURY");
                    if (!result.ok()) showResultDialog("Contribution failed", result.error);
                    else {
                        dialog.dismiss();
                        showResultDialog("GUILD TREASURY UPDATED",
                                result.data.optString("message", "Contribution recorded."));
                        refreshAccount(() -> renderTab("guild"));
                    }
                }));
            });
        });
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(dp(520), getResources().getDisplayMetrics().widthPixels - dp(34)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showPlayerProfile() {
        if(me==null)return;
        android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=phase11Sheet("COLLECTOR PASSPORT","Your collector profile","",GOLD_BRIGHT);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setGravity(Gravity.CENTER_HORIZONTAL);hero.setPadding(dp(13),dp(15),dp(13),dp(15));hero.setBackground(gradient(Color.rgb(47,44,66),Color.rgb(13,23,41),GOLD_BRIGHT,dp(16)));
        FrameLayout crest=phase11Avatar(me.optString("display_name","Collector"),me.optString("avatarUrl",me.optString("avatar_url","")),GOLD_BRIGHT);
        hero.addView(crest,new LinearLayout.LayoutParams(dp(84),dp(84)));addGap(hero,7);
        TextView rank=text(isGodModeEnabled()?"MASTER COLLECTOR  ·  GOD MODE":"FIRSTLIGHT COLLECTOR",9,GOLD_BRIGHT,true);rank.setGravity(Gravity.CENTER);hero.addView(rank);
        TextView name=text(me.optString("display_name","Collector"),22,TEXT,true);name.setGravity(Gravity.CENTER);name.setMaxLines(2);hero.addView(name);
        TextView handle=text("@"+me.optString("username","collector"),11,CYAN,true);handle.setGravity(Gravity.CENTER);hero.addView(handle);
        TextView title=text("LEVEL "+formatNumber(me.optLong("collector_level",1))+"  ·  "+me.optString("role","collector").toUpperCase(java.util.Locale.ROOT),11,GOLD_BRIGHT,true);title.setGravity(Gravity.CENTER);hero.addView(title);
        if(me.optBoolean("is_beta_tester")){TextView beta=text("BETA TESTER  ·  RANKED BOT ONLY",9,VIOLET,true);hero.addView(beta);}
        TextView equipped=text("",12,VIOLET,true);equipped.setGravity(Gravity.CENTER);equipped.setVisibility(View.GONE);hero.addView(equipped);
        TextView email=text(me.optString("email","Account email unavailable"),10,MUTED,false);email.setGravity(Gravity.CENTER);hero.addView(email);
        body.addView(hero);addGap(body,12);
        JSONObject wallet=me.optJSONObject("wallet"),collection=me.optJSONObject("collection");if(wallet==null)wallet=new JSONObject();if(collection==null)collection=new JSONObject();
        body.addView(text("COLLECTION",11,GOLD_BRIGHT,true));addGap(body,5);LinearLayout stats=new LinearLayout(this);
        stats.addView(profileStat("UNIQUE CARDS",formatNumber(collection.optLong("unique_cards")),CYAN),new LinearLayout.LayoutParams(0,dp(67),1f));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(67),1f);p.leftMargin=dp(7);stats.addView(profileStat("TOTAL COPIES",formatNumber(collection.optLong("total_cards")),GOLD),p);body.addView(stats);addGap(body,12);
        body.addView(text("TREASURY",11,GOLD_BRIGHT,true));addGap(body,5);LinearLayout balances=new LinearLayout(this);
        balances.addView(profileStat("GOLD",formatNumber(wallet.optLong("coins")),GOLD),new LinearLayout.LayoutParams(0,dp(66),1f));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(66),1f);bp.leftMargin=dp(5);balances.addView(profileStat("GEMS",formatNumber(wallet.optLong("gems")),VIOLET),bp);
        LinearLayout.LayoutParams bp2=new LinearLayout.LayoutParams(0,dp(66),1f);bp2.leftMargin=dp(5);balances.addView(profileStat("SHARDS",formatNumber(wallet.optLong("shards")),CYAN),bp2);body.addView(balances);addGap(body,12);
        TextView showcase=button("PLAYER SHOWCASE  ›",true);showcase.setOnClickListener(v->{dialog.dismiss();renderTab("showcase");});body.addView(showcase,new LinearLayout.LayoutParams(-1,dp(47)));addGap(body,7);
        TextView ledger=button("TRANSACTION HISTORY  ›",false);ledger.setOnClickListener(v->{dialog.dismiss();walletHistoryOffset=0;renderTab("wallet_history");});body.addView(ledger,new LinearLayout.LayoutParams(-1,dp(45)));addGap(body,7);
        TextView edit=button("EDIT PROFILE  ›",true);edit.setOnClickListener(v->{dialog.dismiss();showProfileEditor();});body.addView(edit,new LinearLayout.LayoutParams(-1,dp(47)));addGap(body,7);
        LinearLayout actions=new LinearLayout(this);TextView friends=button("FRIENDS",false),settings=button("SETTINGS",false);actions.addView(friends,new LinearLayout.LayoutParams(0,dp(46),1f));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(46),1f);sp.leftMargin=dp(7);actions.addView(settings,sp);friends.setOnClickListener(v->{dialog.dismiss();renderTab("friends");});settings.setOnClickListener(v->{dialog.dismiss();renderTab("settings");});body.addView(actions);
        addGap(body,9);TextView privacy=text(isAdminAccount()?"ADMIN ACCESS  ·  GAMEPLAY MATCHMAKING DISABLED":"ACCOUNT DATA SAVED TO CARDUNIVERSE CLOUD",9,MUTED,true);privacy.setGravity(Gravity.CENTER);body.addView(privacy);
        TextView close=button("CLOSE",false);close.setOnClickListener(v->dialog.dismiss());addGap(shell,8);shell.addView(close,new LinearLayout.LayoutParams(-1,dp(44)));
        phase11ShowSheet(dialog,shell,660);
        api.get("/api/profile",result->ui.post(()->{if(!result.ok()||!dialog.isShowing())return;JSONObject profile=result.data.optJSONObject("profile");if(profile==null)return;
            String url=profile.optString("avatarUrl","");if(crest.getChildCount()>1&&crest.getChildAt(1) instanceof ImageView)loadProfileAvatar((ImageView)crest.getChildAt(1),url);
            String active=profile.optString("activeTitleId","");JSONArray ts=result.data.optJSONArray("titles");
            if(ts!=null&&!active.isEmpty())for(int i=0;i<ts.length();i++){JSONObject t=ts.optJSONObject(i);if(t!=null&&active.equals(t.optString("title_id"))&&t.optInt("owned")==1){equipped.setText("✦  "+t.optString("name","Collector title"));equipped.setTextColor(cardRarityColor(t.optString("rarity","COMMON").toUpperCase(java.util.Locale.ROOT)));equipped.setVisibility(View.VISIBLE);break;}}
        }));
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
        String originTab = activeTab;
        api.get("/api/profile", result -> ui.post(() -> {
            if (!originTab.equals(activeTab) || content == null) return;
            if (!result.ok()) { showResultDialog("Profile unavailable", result.error); return; }
            JSONObject profileUser = result.data.optJSONObject("user");
            JSONObject profileData = result.data.optJSONObject("profile");
            if (profileUser == null) profileUser = me == null ? new JSONObject() : me;
            if (profileData == null) profileData = new JSONObject();
            try {
                if (me != null) {
                    me.put("display_name", profileUser.optString("display_name", me.optString("display_name", "Collector")));
                    me.put("username", profileUser.optString("username", me.optString("username", "")));
                }
            } catch (Exception ignored) {}

            if (content == null) return;
            if (pageTitle != null) pageTitle.setText("EDIT PROFILE");
            content.removeAllViews();
            ScrollView editorScroll = (ScrollView) content.getParent();
            editorScroll.scrollTo(0, 0);

            LinearLayout heading = new LinearLayout(this);
            heading.setOrientation(LinearLayout.VERTICAL);
            heading.setPadding(dp(3), dp(2), dp(3), dp(8));
            TextView eyebrow = text("COLLECTOR IDENTITY  /  FIRSTLIGHT REALM", 7, GOLD, true);
            eyebrow.setLetterSpacing(0.13f);
            heading.addView(eyebrow);
            heading.addView(text("Your profile, your legend.", 18, TEXT, true));
            heading.addView(text("Manage how other collectors see you across the realm.", 8, MUTED, false));
            content.addView(heading, rowParams());

            LinearLayout avatarPanel = sectionPanel();
            avatarPanel.setBackground(gradient(Color.rgb(38, 43, 73), Color.rgb(11, 17, 32), VIOLET, dp(14)));
            avatarPanel.addView(text("LIVE PUBLIC PROFILE PREVIEW", 12, GOLD_BRIGHT, true));
            LinearLayout avatarRow = new LinearLayout(this);
            avatarRow.setGravity(Gravity.CENTER_VERTICAL);
            avatarRow.setPadding(0, dp(8), 0, 0);
            FrameLayout avatarFrame = new FrameLayout(this);
            avatarFrame.setBackground(gradient(Color.rgb(242, 213, 155), Color.rgb(117, 85, 52), GOLD_BRIGHT, dp(42)));
            avatarFrame.setClipToOutline(true);
            TextView avatarFallback = text(initials(profileUser.optString("display_name", "CU")), 22, BG, true);
            avatarFallback.setGravity(Gravity.CENTER);
            avatarFrame.addView(avatarFallback, new FrameLayout.LayoutParams(-1, -1));
            ImageView avatarImage = new ImageView(this);
            avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatarImage.setVisibility(View.GONE);
            avatarFrame.addView(avatarImage, new FrameLayout.LayoutParams(-1, -1));
            avatarRow.addView(avatarFrame, new LinearLayout.LayoutParams(dp(76), dp(76)));
            LinearLayout avatarCopy = new LinearLayout(this);
            avatarCopy.setOrientation(LinearLayout.VERTICAL);
            avatarCopy.setPadding(dp(12), 0, 0, 0);
            TextView previewName = text("", 17, TEXT, true);
            TextView previewHandle = text("", 11, CYAN, true);
            TextView previewTitle = text("", 11, GOLD_BRIGHT, true);
            avatarCopy.addView(previewName);
            avatarCopy.addView(previewHandle);
            avatarCopy.addView(previewTitle);
            avatarCopy.addView(text("JPG, PNG or WebP · up to 2 MB", 7, MUTED, false));
            TextView upload = button("CHANGE PHOTO", false);
            LinearLayout.LayoutParams uploadParams = new LinearLayout.LayoutParams(-2, dp(34));
            uploadParams.topMargin = dp(6);
            avatarCopy.addView(upload, uploadParams);
            avatarRow.addView(avatarCopy, new LinearLayout.LayoutParams(0, -2, 1f));
            avatarPanel.addView(avatarRow);
            TextView previewBio = text("", 12, MUTED, false);
            addGap(avatarPanel, 8);
            avatarPanel.addView(previewBio);
            TextView removeAvatar = button("REMOVE PHOTO", false);
            avatarPanel.addView(removeAvatar, rowParams());
            java.util.function.Consumer<String> updateAvatar = path -> {
                avatarImage.setTag(null);
                avatarImage.setVisibility(View.GONE);
                removeAvatar.setEnabled(!path.isEmpty());
                loadProfileAvatar(avatarImage, path);
            };
            updateAvatar.accept(profileData.optString("avatarUrl", ""));
            upload.setOnClickListener(v -> {
                pendingProfileAvatar = upload;
                profileAvatarUploaded = () -> api.get("/api/profile", fresh -> ui.post(() -> {
                    if (!upload.isAttachedToWindow() || !fresh.ok()) return;
                    JSONObject refreshed = fresh.data.optJSONObject("profile");
                    if (refreshed != null) updateAvatar.accept(refreshed.optString("avatarUrl", ""));
                }));
                chooseProfileAvatar();
            });
            removeAvatar.setOnClickListener(v -> confirmAction("Remove profile photo?",
                    "Your collector profile will return to its default emblem.", () -> {
                removeAvatar.setEnabled(false);
                api.delete("/api/profile/avatar", removed -> ui.post(() -> {
                    if (!removeAvatar.isAttachedToWindow()) return;
                    if (!removed.ok()) {
                        removeAvatar.setEnabled(true);
                        showResultDialog("Photo not removed", removed.error);
                    } else {
                        updateAvatar.accept("");
                        Toast.makeText(this, "Profile photo removed.", Toast.LENGTH_SHORT).show();
                    }
                }));
            }));
            content.addView(avatarPanel, rowParams());
            addGap(content, 8);

            JSONObject stats = result.data.optJSONObject("stats");
            if (stats != null) {
                LinearLayout overview = sectionPanel();
                overview.addView(text("COLLECTOR STATS", 12, GOLD_BRIGHT, true));
                LinearLayout collection = new LinearLayout(this);
                collection.addView(profileStat("LEVEL", formatNumber(stats.optLong("level", 1)), CYAN), weightParams(1));
                collection.addView(profileStat("UNIQUE CARDS", formatNumber(stats.optLong("uniqueCards")), GOLD), weightParams(1));
                collection.addView(profileStat("CARD COPIES", formatNumber(stats.optLong("cardCopies")), VIOLET), weightParams(1));
                overview.addView(collection);
                overview.addView(dataRow("Ranked", formatNumber(stats.optLong("rankedWins")) + " wins",
                        formatNumber(stats.optLong("rating")) + " rating"), rowParams());
                overview.addView(dataRow("Raids", formatNumber(stats.optLong("raidAttempts")) + " strikes",
                        formatNumber(stats.optLong("raidDamage")) + " damage"), rowParams());
                content.addView(overview, rowParams());
            }
            LinearLayout identity = sectionPanel();
            identity.addView(text("PUBLIC IDENTITY", 8, GOLD_BRIGHT, true));
            EditText displayName = editField("Display name", false);
            displayName.setText(profileUser.optString("display_name", ""));
            displayName.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(48)});
            identity.addView(text("DISPLAY NAME", 7, MUTED, true));
            identity.addView(displayName, fieldParams());

            EditText username = editField("your_username", false);
            username.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            username.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});
            username.setText(profileUser.optString("username", me == null ? "" : me.optString("username", "")));
            boolean canEditUsername = isAdminAccount();
            username.setEnabled(canEditUsername);
            username.setAlpha(canEditUsername ? 1f : 0.68f);
            identity.addView(text(canEditUsername ? "USERNAME  ·  MASTER ADMIN CAN CHANGE THIS"
                    : "USERNAME  ·  LOCKED AFTER REGISTRATION", 7, MUTED, true));
            identity.addView(username, fieldParams());
            identity.addView(text(canEditUsername
                    ? "3–20 characters. Start with a letter; use letters, numbers or underscores."
                    : "Share this unique handle with other collectors to receive friend requests.",
                    7, MUTED, false));
            content.addView(identity, rowParams());
            addGap(content, 8);

            LinearLayout about = sectionPanel();
            about.addView(text("ABOUT YOUR COLLECTOR", 8, GOLD_BRIGHT, true));
            EditText bio = editField("Write a short bio about your collection…", false);
            bio.setSingleLine(false);
            bio.setMinLines(4);
            bio.setMaxLines(5);
            bio.setGravity(Gravity.TOP | Gravity.START);
            bio.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(180)});
            bio.setText(profileData.optString("bio", ""));
            TextView bioCount = text(bio.length() + " / 180", 7, MUTED, true);
            bioCount.setGravity(Gravity.RIGHT);
            bio.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    bioCount.setText(s.length() + " / 180");
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            about.addView(text("PUBLIC BIO", 7, MUTED, true));
            about.addView(bio, new LinearLayout.LayoutParams(-1, dp(100)));
            about.addView(bioCount);
            content.addView(about, rowParams());
            addGap(content, 8);

            LinearLayout titlesPanel = sectionPanel();
            titlesPanel.addView(text("TITLE COLLECTION", 8, GOLD_BRIGHT, true));
            titlesPanel.addView(text("Equip a title you have already earned. Locked titles cannot be selected.", 7, MUTED, false));
            JSONArray titles = result.data.optJSONArray("titles");
            ArrayList<String> titleIds = new ArrayList<>();
            ArrayList<String> titleLabels = new ArrayList<>();
            titleIds.add("");
            titleLabels.add("No title equipped");
            if (titles != null) for (int i = 0; i < titles.length(); i++) {
                JSONObject t = titles.optJSONObject(i);
                if (t == null || t.optInt("owned") != 1) continue;
                titleIds.add(t.optString("title_id", ""));
                titleLabels.add(t.optString("name", "Collector title") + "  ·  " + t.optString("rarity", "COMMON"));
            }
            String activeTitle = profileData.optString("activeTitleId", "");
            final int[] selectedTitle={Math.max(0,titleIds.indexOf(activeTitle))};
            TextView titleChoice=button("✦  "+titleLabels.get(selectedTitle[0])+"   ›",false);
            titleChoice.setTextSize(12);titleChoice.setSingleLine(false);titleChoice.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
            addGap(titlesPanel,8);titlesPanel.addView(titleChoice,new LinearLayout.LayoutParams(-1,dp(54)));
            Runnable updatePreview = () -> {
                String name = displayName.getText().toString().trim();
                String handle = username.getText().toString().trim();
                previewName.setText(name.isEmpty() ? "Collector" : name);
                avatarFallback.setText(initials(name.isEmpty() ? "Collector" : name));
                previewHandle.setText(handle.isEmpty() ? "" : "@" + handle);
                previewTitle.setText(titleLabels.get(selectedTitle[0]));
                previewBio.setText(bio.getText().toString().trim());
            };
            TextWatcher previewWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updatePreview.run();
                }
                @Override public void afterTextChanged(Editable s) {}
            };
            displayName.addTextChangedListener(previewWatcher);
            username.addTextChangedListener(previewWatcher);
            bio.addTextChangedListener(previewWatcher);
            updatePreview.run();
            titleChoice.setOnClickListener(v -> showTitleCollectionPicker(titleLabels, selectedTitle[0], index -> {
                selectedTitle[0] = index;
                titleChoice.setText("✦  " + titleLabels.get(index) + "   ›");
                updatePreview.run();
            }));
            if (titles != null) {
                TextView catalogue = button("ALL TITLES · " + (titleIds.size() - 1)
                        + " / " + titles.length() + " EARNED", false);
                catalogue.setOnClickListener(v -> showProfileTitleCatalogue(titles, titleIds.get(selectedTitle[0])));
                titlesPanel.addView(catalogue, rowParams());
            }
            content.addView(titlesPanel, rowParams());
            addGap(content, 10);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            TextView save = button("SAVE PROFILE", true);
            TextView friends = button("FRIENDS", false);
            actions.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1.3f));
            LinearLayout.LayoutParams friendsParams = new LinearLayout.LayoutParams(0, dp(44), 0.8f);
            friendsParams.leftMargin = dp(7);
            actions.addView(friends, friendsParams);
            content.addView(actions, rowParams());
            friends.setOnClickListener(v -> renderTab("friends"));
            TextView back = text("←  BACK TO SETTINGS", 8, MUTED, true);
            back.setGravity(Gravity.CENTER);
            back.setPadding(0, dp(12), 0, dp(10));
            back.setOnClickListener(v -> renderTab("settings"));
            content.addView(back);

            save.setOnClickListener(v -> {
                String nameValue = displayName.getText().toString().trim();
                String usernameValue = username.getText().toString().trim().replaceFirst("^@", "")
                        .toLowerCase(java.util.Locale.ROOT);
                if (nameValue.length() < 2) {
                    displayName.setError("Use at least 2 characters.");
                    return;
                }
                if (canEditUsername && !usernameValue.matches("[a-z][a-z0-9_]{2,19}")) {
                    username.setError("Use 3–20 letters, numbers or underscores; start with a letter.");
                    return;
                }
                save.setEnabled(false);
                save.setText("SAVING PROFILE…");
                JSONObject payload = new JSONObject();
                try {
                    payload.put("displayName", nameValue);
                    payload.put("bio", bio.getText().toString().trim());
                    payload.put("activeTitleId", titleIds.get(Math.max(0,
                            Math.min(titleIds.size() - 1, selectedTitle[0]))));
                    if (canEditUsername) payload.put("username", usernameValue);
                } catch (Exception ignored) {}
                api.patch("/api/profile", payload, saved -> ui.post(() -> {
                    if (!save.isAttachedToWindow()) return;
                    if (!saved.ok()) {
                        save.setEnabled(true);
                        save.setText("SAVE PROFILE");
                        showResultDialog("Profile not saved", saved.error);
                        return;
                    }
                    JSONObject savedUser = saved.data.optJSONObject("user");
                    try {
                        if (me != null && savedUser != null) {
                            me.put("display_name", savedUser.optString("display_name", nameValue));
                            me.put("username", savedUser.optString("username", usernameValue));
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(this, "Collector profile saved.", Toast.LENGTH_SHORT).show();
                    save.setEnabled(true);
                    save.setText("SAVE PROFILE");
                }));
            });
        }));
    }

    private void showProfileTitleCatalogue(JSONArray titles, String selectedId) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = phase11Sheet("TITLE COLLECTION", "Titles & achievements", "", VIOLET);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < titles.length(); i++) {
            JSONObject title = titles.optJSONObject(i);
            if (title == null) continue;
            boolean earned = title.optInt("owned") == 1;
            boolean selected = earned && !selectedId.isEmpty() && selectedId.equals(title.optString("title_id"));
            LinearLayout row = sectionPanel();
            row.addView(text(title.optString("name", "Collector title"), 14, TEXT, true));
            row.addView(text(title.optString("rarity", "COMMON") + " · "
                    + (selected ? "SELECTED" : earned ? "EARNED" : "LOCKED"), 10, VIOLET, true));
            String description = title.optString("description");
            if (!description.isEmpty()) row.addView(text(description, 12, MUTED, false));
            String hint = title.optString("unlockHint");
            if (!earned && !hint.isEmpty()) row.addView(text(hint, 11, GOLD_BRIGHT, false));
            list.addView(row, rowParams());
        }
        scroll.addView(list);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView close = button("CLOSE", false);
        close.setOnClickListener(v -> dialog.dismiss());
        shell.addView(close, rowParams());
        phase11ShowSheet(dialog, shell, 640);
    }

    private void showTitleCollectionPicker(ArrayList<String> labels,int selected,java.util.function.IntConsumer choose){
        android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=phase11Sheet("COLLECTOR IDENTITY","Title collection","Choose an earned title. Changes apply when you save your profile.",VIOLET);
        EditText search=editField("Search earned titles",false);search.setTextSize(13);shell.addView(search,new LinearLayout.LayoutParams(-1,dp(45)));addGap(shell,9);
        TextView count=text("",10,MUTED,true);shell.addView(count);addGap(shell,5);
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        ArrayList<LinearLayout> rows=new ArrayList<>();
        for(int i=0;i<labels.size();i++){final int index=i;String[] parts=labels.get(i).split("  ·  ",2);String rarity=parts.length>1?parts[1].toUpperCase(java.util.Locale.ROOT):"";
            int accent=i==0?MUTED:cardRarityColor(rarity);boolean active=i==selected;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(11),dp(10),dp(11));
            row.setBackground(gradient(active?Color.rgb(45,39,65):Color.rgb(24,33,51),Color.rgb(11,18,31),active?VIOLET:Color.rgb(64,76,102),dp(12)));
            TextView sigil=text(i==0?"—":"✦",22,accent,true);sigil.setGravity(Gravity.CENTER);sigil.setBackground(gradient(PANEL_LIGHT,PANEL,accent,dp(10)));row.addView(sigil,new LinearLayout.LayoutParams(dp(42),dp(42)));
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(10),0,dp(4),0);info.addView(text(parts[0],13,TEXT,true));
            info.addView(text(active?"SELECTED":i==0?"Remove equipped title":rarity+"  ·  UNLOCKED",10,active?VIOLET:accent,true));row.addView(info,new LinearLayout.LayoutParams(0,-2,1f));
            TextView check=text(active?"✓":"›",18,active?VIOLET:MUTED,true);row.addView(check);row.setFocusable(true);row.setOnClickListener(v->{dialog.dismiss();choose.accept(index);});list.addView(row,rowParams());rows.add(row);}
        TextView empty=text("No titles match your search.",11,MUTED,false);empty.setGravity(Gravity.CENTER);empty.setVisibility(View.GONE);list.addView(empty);
        Runnable update=()->{String q=search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);int n=0;for(int i=0;i<labels.size();i++){boolean show=labels.get(i).toLowerCase(java.util.Locale.ROOT).contains(q);rows.get(i).setVisibility(show?View.VISIBLE:View.GONE);if(show)n++;}count.setText(n+" OPTIONS  ·  "+Math.max(0,labels.size()-1)+" EARNED TITLES");empty.setVisibility(n==0?View.VISIBLE:View.GONE);};update.run();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){update.run();}public void afterTextChanged(Editable e){}});
        TextView close=button("CANCEL",false);close.setOnClickListener(v->dialog.dismiss());addGap(shell,8);shell.addView(close,new LinearLayout.LayoutParams(-1,dp(44)));shell.setFocusableInTouchMode(true);shell.requestFocus();phase11ShowSheet(dialog,shell,600);
    }

    private void chooseProfileAvatar() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(pick, REQUEST_PROFILE_AVATAR); }
        catch (Exception error) {
            showResultDialog("Photo picker unavailable", "Unable to open the image picker on this device.");
        }
    }

    private void loadProfileAvatar(ImageView target, String path) {
        if (target == null || path == null || path.trim().isEmpty()) return;
        target.setTag(path);
        new Thread(() -> {
            java.net.HttpURLConnection connection = null;
            android.graphics.Bitmap bitmap = null;
            try {
                String url = path.startsWith("http") ? path : ApiClient.API_ROOT + path;
                connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setUseCaches(true);
                if (connection.getResponseCode() == 200) {
                    try (InputStream input = connection.getInputStream()) {
                        bitmap = android.graphics.BitmapFactory.decodeStream(input);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
            android.graphics.Bitmap loaded = bitmap;
            ui.post(() -> {
                if (loaded != null && path.equals(target.getTag())) {
                    target.setImageBitmap(loaded);
                    target.setVisibility(View.VISIBLE);
                }
            });
        }, "cu-profile-avatar").start();
    }

    private void uploadProfileAvatar(Uri uri) {
        if (uri == null) return;
        TextView status = pendingProfileAvatar;
        Runnable refreshAvatar = profileAvatarUploaded;
        pendingProfileAvatar = null;
        profileAvatarUploaded = null;
        if (status != null) {
            status.setEnabled(false);
            status.setText("UPLOADING PHOTO…");
        }
        new Thread(() -> {
            ApiClient.UploadFile upload = null;
            String problem = null;
            try (InputStream input = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) throw new IllegalStateException("Unable to read the selected photo.");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 2 * 1024 * 1024) {
                        throw new IllegalArgumentException("Profile photos must be 2 MB or smaller.");
                    }
                    output.write(buffer, 0, count);
                }
                String mime = getContentResolver().getType(uri);
                String filename = selectedImageFilename(uri);
                if (mime == null) {
                    String lower = filename.toLowerCase(java.util.Locale.ROOT);
                    mime = lower.endsWith(".png") ? "image/png"
                            : lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg"
                            : lower.endsWith(".webp") ? "image/webp" : "";
                }
                if ("image/jpg".equalsIgnoreCase(mime)) mime = "image/jpeg";
                if (!"image/jpeg".equalsIgnoreCase(mime)
                        && !"image/png".equalsIgnoreCase(mime)
                        && !"image/webp".equalsIgnoreCase(mime)) {
                    throw new IllegalArgumentException("Use a JPEG, PNG or WebP photo.");
                }
                upload = new ApiClient.UploadFile(filename, mime, output.toByteArray());
            } catch (Exception error) {
                problem = error.getMessage() == null ? "Unable to read the selected photo." : error.getMessage();
            }
            String finalProblem = problem;
            ApiClient.UploadFile finalUpload = upload;
            if (finalProblem != null || finalUpload == null) {
                ui.post(() -> {
                    if (status != null) { status.setEnabled(true); status.setText("CHANGE PHOTO"); }
                    showResultDialog("Profile photo not uploaded",
                            finalProblem == null ? "Unable to prepare the selected photo." : finalProblem);
                });
                return;
            }
            ArrayList<ApiClient.UploadFile> files = new ArrayList<>();
            files.add(finalUpload);
            api.postMultipart("/api/profile/avatar", "avatar", files, result -> ui.post(() -> {
                if (status != null) { status.setEnabled(true); status.setText("CHANGE PHOTO"); }
                if (!result.ok()) {
                    showResultDialog("Profile photo not uploaded", result.error);
                    return;
                }
                Toast.makeText(this, "Profile photo updated.", Toast.LENGTH_SHORT).show();
                if (refreshAvatar != null) refreshAvatar.run();
            }));
        }, "cu-profile-photo-upload").start();
    }

    private void renderFriendsScreen() {
        if (content == null) return;
        content.removeAllViews();
        LinearLayout intro = sectionPanel();
        intro.setBackground(gradient(Color.rgb(35, 55, 70), Color.rgb(11, 19, 34), CYAN, dp(13)));
        intro.addView(text("COLLECTOR NETWORK", 7, CYAN, true));
        intro.addView(text("Find your people. Build your party.", 17, TEXT, true));
        intro.addView(text("Add collectors using their unique username. You can accept, decline or cancel requests at any time.", 8, MUTED, false));
        content.addView(intro, rowParams());
        addGap(content, 8);

        LinearLayout addPanel = sectionPanel();
        addPanel.addView(text("ADD A FRIEND", 8, GOLD_BRIGHT, true));
        addPanel.addView(text("Your username  ·  @" + (me == null ? "collector" : me.optString("username", "collector")), 7, MUTED, false));
        LinearLayout addRow = new LinearLayout(this);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText query = editField("@username", false);
        query.setSingleLine(true);
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        query.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(21)});
        addRow.addView(query, new LinearLayout.LayoutParams(0, dp(43), 1f));
        TextView send = button("SEND REQUEST", true);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(124), dp(43));
        sendParams.leftMargin = dp(7);
        addRow.addView(send, sendParams);
        addPanel.addView(addRow);
        content.addView(addPanel, rowParams());
        addGap(content, 8);

        LinearLayout lists = new LinearLayout(this);
        lists.setOrientation(LinearLayout.VERTICAL);
        lists.addView(sectionPanel());
        content.addView(lists, rowParams());
        api.get("/api/friends", result -> ui.post(() -> {
            if (!"friends".equals(activeTab) || content == null) return;
            lists.removeAllViews();
            if (!result.ok()) {
                LinearLayout error = sectionPanel();
                error.addView(text("FRIEND LIST UNAVAILABLE", 8, RED, true));
                error.addView(text(result.error, 8, MUTED, false));
                TextView retry = button("TRY AGAIN", false);
                retry.setOnClickListener(v -> renderFriendsScreen());
                error.addView(retry, new LinearLayout.LayoutParams(-1, dp(38)));
                lists.addView(error, rowParams());
                return;
            }
            JSONArray incoming = result.data.optJSONArray("incoming");
            JSONArray outgoing = result.data.optJSONArray("outgoing");
            JSONArray friends = result.data.optJSONArray("friends");
            addFriendGroup(lists, "FRIEND REQUESTS", "Requests waiting for your response", incoming, 1);
            addFriendGroup(lists, "SENT REQUESTS", "Requests you are waiting on", outgoing, 2);
            addFriendGroup(lists, "YOUR FRIENDS", "Collectors connected to your account", friends, 0);
        }));
        send.setOnClickListener(v -> {
            String value = query.getText().toString().trim().replaceFirst("^@", "")
                    .toLowerCase(java.util.Locale.ROOT);
            if (!value.matches("[a-z][a-z0-9_]{2,19}")) {
                query.setError("Enter a valid @username.");
                return;
            }
            send.setEnabled(false);
            send.setText("SENDING…");
            api.post("/api/friends/request", payload("username", value), sent -> ui.post(() -> {
                send.setEnabled(true);
                send.setText("SEND REQUEST");
                if (!sent.ok()) {
                    showResultDialog("Friend request not sent", sent.error);
                    return;
                }
                Toast.makeText(this, sent.data.optString("message", "Friend request sent."), Toast.LENGTH_SHORT).show();
                renderFriendsScreen();
            }));
        });
    }

    private void addFriendGroup(LinearLayout parent, String title, String subtitle, JSONArray rows, int mode) {
        LinearLayout group = sectionPanel();
        group.addView(text(title + "  ·  " + (rows == null ? 0 : rows.length()), 8, GOLD_BRIGHT, true));
        group.addView(text(subtitle, 7, MUTED, false));
        if (rows == null || rows.length() == 0) {
            group.addView(text(mode == 1 ? "No incoming requests." : mode == 2
                    ? "No outgoing requests." : "No friends added yet. Search by username above.",
                    8, MUTED, false));
        } else {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject player = rows.optJSONObject(i);
                if (player == null) continue;
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(10), dp(9), dp(10), dp(9));
                card.setBackground(gradient(Color.rgb(27, 37, 60), Color.rgb(13, 20, 36),
                        mode == 0 ? CYAN : mode == 1 ? GOLD : VIOLET, dp(10)));
                LinearLayout top = new LinearLayout(this);
                top.setGravity(Gravity.CENTER_VERTICAL);
                FrameLayout emblem = new FrameLayout(this);
                emblem.setBackground(gradient(Color.rgb(242, 213, 155), Color.rgb(117, 85, 52), GOLD_BRIGHT, dp(22)));
                TextView initialsView = text(initials(player.optString("display_name", "CU")), 10, BG, true);
                initialsView.setGravity(Gravity.CENTER);
                emblem.addView(initialsView, new FrameLayout.LayoutParams(-1, -1));
                top.addView(emblem, new LinearLayout.LayoutParams(dp(42), dp(42)));
                LinearLayout identity = new LinearLayout(this);
                identity.setOrientation(LinearLayout.VERTICAL);
                identity.setPadding(dp(9), 0, 0, 0);
                identity.addView(text(player.optString("display_name", "Collector"), 9, TEXT, true));
                identity.addView(text("@" + player.optString("username", "unknown")
                        + "  ·  LEVEL " + player.optInt("collector_level", 1), 7, CYAN, true));
                top.addView(identity, new LinearLayout.LayoutParams(0, -2, 1f));
                card.addView(top);
                LinearLayout actions = new LinearLayout(this);
                actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                actions.setPadding(0, dp(7), 0, 0);
                if (mode == 1) {
                    TextView accept = button("ACCEPT", true);
                    TextView decline = button("DECLINE", false);
                    actions.addView(accept, new LinearLayout.LayoutParams(dp(94), dp(34)));
                    LinearLayout.LayoutParams declineParams = new LinearLayout.LayoutParams(dp(94), dp(34));
                    declineParams.leftMargin = dp(6);
                    actions.addView(decline, declineParams);
                    String requestId = player.optString("request_id", "");
                    accept.setOnClickListener(v -> performFriendAction("/api/friends/requests/" + Uri.encode(requestId) + "/accept", true));
                    decline.setOnClickListener(v -> performFriendAction("/api/friends/requests/" + Uri.encode(requestId) + "/decline", true));
                } else if (mode == 2) {
                    TextView cancel = button("CANCEL REQUEST", false);
                    actions.addView(cancel, new LinearLayout.LayoutParams(dp(142), dp(34)));
                    String requestId = player.optString("request_id", "");
                    cancel.setOnClickListener(v -> performFriendAction("/api/friends/requests/" + Uri.encode(requestId), false));
                } else {
                    TextView remove = button("REMOVE FRIEND", false);
                    actions.addView(remove, new LinearLayout.LayoutParams(dp(128), dp(34)));
                    String userId = player.optString("user_id", "");
                    remove.setOnClickListener(v -> confirmAction("Remove this friend?",
                            "The collector will be removed from your Friends list.", () ->
                            performFriendAction("/api/friends/" + Uri.encode(userId), false)));
                }
                card.addView(actions);
                LinearLayout.LayoutParams cardParams = rowParams();
                cardParams.topMargin = dp(7);
                group.addView(card, cardParams);
            }
        }
        parent.addView(group, rowParams());
        addGap(parent, 8);
    }

    private void performFriendAction(String path, boolean post) {
        ApiClient.Callback callback = result -> ui.post(() -> {
            if (!result.ok()) {
                showResultDialog("Friend action failed", result.error);
                return;
            }
            Toast.makeText(this, result.data.optString("message", "Friends updated."), Toast.LENGTH_SHORT).show();
            renderFriendsScreen();
        });
        if (post) api.post(path, new JSONObject(), callback);
        else api.delete(path, callback);
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


    private String walletHistoryCurrency="COINS";
    private int walletHistoryOffset=0;

    private void phase15Heading(LinearLayout parent,String label){
        TextView heading=text(label,11,GOLD_BRIGHT,true);heading.setLetterSpacing(.08f);heading.setPadding(0,dp(9),0,dp(5));parent.addView(heading);
    }

    private void renderPhase15Wallet(JSONObject data){
        LinearLayout filters=new LinearLayout(this);String[] codes={"COINS","GEMS","SHARDS"},names={"◈ GOLD","✧ GEMS","◆ SHARDS"};
        for(int i=0;i<codes.length;i++){final String currency=codes[i];TextView chip=button(names[i],currency.equals(walletHistoryCurrency));chip.setTextSize(10);filters.addView(chip,new LinearLayout.LayoutParams(0,dp(44),1f));chip.setOnClickListener(v->{walletHistoryCurrency=currency;walletHistoryOffset=0;renderTab("wallet_history");});}
        content.addView(filters,rowParams());JSONObject wallet=me==null?null:me.optJSONObject("wallet");String key=walletHistoryCurrency.toLowerCase(java.util.Locale.ROOT);String display=walletHistoryCurrency.equals("COINS")?"GOLD":walletHistoryCurrency;
        if(wallet!=null)content.addView(profileStat("CURRENT "+display,formatNumber(wallet.optLong(key)),GOLD_BRIGHT),new LinearLayout.LayoutParams(-1,dp(70)));
        phase15Heading(content,"TRANSACTION HISTORY");
        JSONArray tx=data.optJSONArray("transactions");
        if(tx==null||tx.length()==0)showNotice("No recorded transactions for this currency.");
        else for(int i=0;i<tx.length();i++){JSONObject t=tx.optJSONObject(i);if(t==null)continue;long amount=t.optLong("amount");LinearLayout row=sectionPanel();row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.addView(text(t.optString("reason","Transaction").replace('_',' '),12,TEXT,true));info.addView(text(t.optString("created_at",""),9,MUTED,false));row.addView(info,new LinearLayout.LayoutParams(0,-2,1f));TextView value=text((amount>=0?"+":"−")+formatNumber(Math.abs(amount)),14,amount>=0?GREEN:RED,true);row.addView(value);content.addView(row,rowParams());}
        LinearLayout paging=new LinearLayout(this);TextView prev=button("PREVIOUS",false),next=button("NEXT",false);prev.setEnabled(walletHistoryOffset>0);next.setEnabled(data.optBoolean("has_more"));prev.setOnClickListener(v->{walletHistoryOffset=Math.max(0,walletHistoryOffset-30);renderTab("wallet_history");});next.setOnClickListener(v->{walletHistoryOffset+=30;renderTab("wallet_history");});paging.addView(prev,new LinearLayout.LayoutParams(0,dp(44),1f));paging.addView(next,new LinearLayout.LayoutParams(0,dp(44),1f));content.addView(paging,rowParams());
    }

    private void renderPhase15Combos(LinearLayout panel,JSONArray combos){
        panel.removeAllViews();phase15Heading(panel,"TEAM COMBO  /  SYNERGY");panel.addView(text("Effects are active in Weekly Tower. Other modes retain their existing resonance rules.",10,MUTED,false));
        if(combos==null){panel.addView(text("No combo information returned.",10,MUTED,false));return;}
        for(int i=0;i<combos.length();i++){JSONObject c=combos.optJSONObject(i);if(c==null)continue;boolean active=c.optBoolean("active");LinearLayout row=sectionPanel();row.setBackground(gradient(active?Color.rgb(28,50,46):PANEL_LIGHT,PANEL,active?GREEN:Color.rgb(62,73,100),dp(9)));
            row.addView(text((active?"✓ ACTIVE  ·  ":"○ INACTIVE  ·  ")+c.optString("name"),11,active?GREEN:MUTED,true));row.addView(text(c.optString("requirement"),10,TEXT,true));row.addView(text(c.optString("effect"),10,MUTED,false));panel.addView(row,rowParams());}
    }

    private void refreshPhase15Combos(LinearLayout panel,String[] slots,int[] revision){
        int request=++revision[0];JSONArray ids=new JSONArray();for(String id:slots)if(id!=null&&!id.isEmpty())ids.put(id);
        panel.removeAllViews();panel.addView(text("Checking team combos…",10,MUTED,false));
        api.post("/api/team/combos",payload("cards",ids),result->ui.post(()->{if(request!=revision[0]||!"formation".equals(activeTab))return;if(result.ok())renderPhase15Combos(panel,result.data.optJSONArray("combos"));else{panel.removeAllViews();panel.addView(text("Combo preview unavailable. "+result.error,10,MUTED,false));}}));
    }

    private void renderPhase15Showcase(JSONObject data){
        boolean own=data.optBoolean("is_self");
        LinearLayout searchPanel=sectionPanel();phase15Heading(searchPanel,"COLLECTOR DIRECTORY");
        EditText handle=editField("Find collector by username",false);handle.setSingleLine(true);searchPanel.addView(handle,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout searchActions=new LinearLayout(this);TextView find=button("VIEW SHOWCASE",true),mine=button("MY SHOWCASE",false);searchActions.addView(find,new LinearLayout.LayoutParams(0,dp(42),1f));searchActions.addView(mine,new LinearLayout.LayoutParams(0,dp(42),1f));searchPanel.addView(searchActions);content.addView(searchPanel,rowParams());
        find.setOnClickListener(v->{String user=handle.getText().toString().trim().replaceFirst("^@","");if(user.isEmpty()){handle.setError("Enter a username");return;}find.setEnabled(false);int req=++activePageRequest;api.get("/api/showcase?username="+Uri.encode(user),res->ui.post(()->{if(req!=activePageRequest||!"showcase".equals(activeTab))return;find.setEnabled(true);if(!res.ok()){showResultDialog("Showcase unavailable",res.error);return;}content.removeAllViews();addScreenHero("showcase");renderPhase15Showcase(res.data);}));});mine.setOnClickListener(v->renderTab("showcase"));
        addModeSpotlight("COLLECTOR SHOWCASE",data.optString("display_name","Collector"),"@"+data.optString("username","collector"),"✦",GOLD_BRIGHT);
        LinearLayout favorite=sectionPanel();phase15Heading(favorite,"FAVORITE LEGENDARY");JSONObject card=data.optJSONObject("favorite");
        if(card!=null){LinearLayout.LayoutParams art=new LinearLayout.LayoutParams(dp(134),dp(188));art.gravity=Gravity.CENTER_HORIZONTAL;favorite.addView(phase11Art(card,GOLD_BRIGHT),art);TextView name=text(card.optString("name"),15,GOLD_BRIGHT,true);name.setGravity(Gravity.CENTER);favorite.addView(name);}
        else favorite.addView(text("No favorite Legendary selected.",11,MUTED,false));
        if(own){TextView choose=button("CHOOSE FAVORITE LEGENDARY",true);favorite.addView(choose,new LinearLayout.LayoutParams(-1,dp(44)));choose.setOnClickListener(v->{JSONArray candidates=data.optJSONArray("candidates");showCardPickerDialog(candidates,"Favorite Legendary","Choose a Legendary card you own.",false,c->phase15Favorite(c.optString("card_id")));});if(card!=null){TextView clear=button("REMOVE FAVORITE",false);favorite.addView(clear,new LinearLayout.LayoutParams(-1,dp(42)));clear.setOnClickListener(v->phase15Favorite(null));}}
        content.addView(favorite,rowParams());
        LinearLayout formation=sectionPanel();phase15Heading(formation,"MAIN FORMATION");JSONArray cards=data.optJSONArray("formation");HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);LinearLayout strip=new LinearLayout(this);sc.addView(strip);
        if(cards!=null)for(int i=0;i<cards.length();i++){JSONObject c=cards.optJSONObject(i);if(c==null)continue;LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setPadding(dp(4),0,dp(4),0);tile.addView(phase11Art(c,cardRarityColor(c.optString("rarity","COMMON"))),new LinearLayout.LayoutParams(dp(85),dp(118)));TextView n=text(c.optString("name"),9,TEXT,true);n.setMaxLines(2);tile.addView(n);strip.addView(tile,new LinearLayout.LayoutParams(dp(94),-2));}formation.addView(sc);
        if(cards==null||cards.length()==0)formation.addView(text("No formation saved.",10,MUTED,false));content.addView(formation,rowParams());
        LinearLayout completion=sectionPanel();phase15Heading(completion,"BINDER / SET COMPLETION");JSONArray sets=data.optJSONArray("sets");if(sets!=null)for(int i=0;i<sets.length();i++){JSONObject set=sets.optJSONObject(i);if(set==null)continue;int total=Math.max(1,set.optInt("total")),owned=set.optInt("collected");completion.addView(text(set.optString("name")+"  ·  "+owned+" / "+total,11,TEXT,true));ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(total);bar.setProgress(Math.min(owned,total));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));completion.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));addGap(completion,5);}content.addView(completion,rowParams());
        LinearLayout rank=sectionPanel();phase15Heading(rank,"HIGHEST RANK");JSONObject best=data.optJSONObject("best_rank");rank.addView(text(best==null?"Unranked":best.optString("tier")+"  ·  "+best.optInt("rating")+" Rating",17,GOLD_BRIGHT,true));content.addView(rank,rowParams());
        phase15ShowcaseList("EARNED TITLES",data.optJSONArray("titles"),"name");
        phase15ShowcaseList("ACHIEVEMENTS",data.optJSONArray("achievements"),"title");
        phase15ShowcaseList("OWNED COSMETICS",data.optJSONArray("cosmetics"),"name");
    }

    private void phase15Favorite(String id){
        confirmAction(id==null?"Remove favorite?":"Equip favorite Legendary?","This updates your public Showcase.",()->api.post("/api/showcase/favorite",payload("card_id",id==null?JSONObject.NULL:id),r->ui.post(()->{if(!r.ok()){showResultDialog("Showcase unavailable",r.error);return;}renderTab("showcase");})));
    }

    private void phase15ShowcaseList(String heading,JSONArray items,String key){
        LinearLayout panel=sectionPanel();phase15Heading(panel,heading+"  ·  "+(items==null?0:items.length()));
        if(items==null||items.length()==0)panel.addView(text("Nothing collected yet.",10,MUTED,false));
        else for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;String subtitle=item.optString("cosmetic_type",item.optString("rarity",""));panel.addView(dataRow(item.optString(key,"Record"),subtitle,item.optInt("equipped")==1?"EQUIPPED":"✦"),rowParams());}content.addView(panel,rowParams());
    }

    private void renderPhase15Tower(JSONObject data){
        JSONObject calendar=data.optJSONObject("calendar"),modifier=data.optJSONObject("modifier");
        String week=calendar==null?"":calendar.optString("week");int best=data.optInt("best");boolean viewOnly=data.optBoolean("view_only");
        addModeSpotlight("WEEKLY ASCENSION","Floor "+best+" / 20",calendar==null?"Weekly challenge":"Week of "+week+"  ·  Resets "+calendar.optString("next_week"),"♜",GOLD_BRIGHT);
        LinearLayout event=sectionPanel();phase15Heading(event,"ACTIVE WEEKLY MODIFIER");if(modifier!=null){event.addView(text(modifier.optString("name"),17,CYAN,true));event.addView(text(modifier.optString("description"),11,MUTED,false));}content.addView(event,rowParams());
        LinearLayout progress=sectionPanel();progress.addView(text("TOWER PROGRESS  ·  "+best+" / 20",11,GOLD_BRIGHT,true));ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(20);bar.setProgress(best);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD_BRIGHT));progress.addView(bar,new LinearLayout.LayoutParams(-1,dp(9)));content.addView(progress,rowParams());
        if(viewOnly)showNotice("This account can view the Tower but cannot challenge floors.");
        JSONArray formation=data.optJSONArray("formation");int teamSize=formation==null?0:formation.length();boolean trio=modifier!=null&&"TRIO_LIMIT".equals(modifier.optString("key"));boolean ready=teamSize>0&&(!trio||teamSize<=3);
        TextView team=button("FORMATION  ·  "+teamSize+" CARDS  ›",false);team.setOnClickListener(v->renderTab("formation"));content.addView(team,new LinearLayout.LayoutParams(-1,dp(44)));
        if(!ready)showNotice(trio?"This week requires a team of at most three cards.":"Save a Formation before challenging.");
        LinearLayout combos=sectionPanel();renderPhase15Combos(combos,data.optJSONArray("combos"));content.addView(combos,rowParams());
        phase15Heading(content,"THE TWENTY FLOORS");
        JSONArray floors=data.optJSONArray("floors");if(floors!=null)for(int i=0;i<floors.length();i++){JSONObject f=floors.optJSONObject(i);if(f==null)continue;int floor=f.optInt("floor"),tier=f.optInt("tier");boolean cleared=floor<=best,available=floor==best+1;int accent=tier==3?GOLD_BRIGHT:tier==2?VIOLET:tier==1?CYAN:GREEN;
            LinearLayout row=sectionPanel();row.setBackground(gradient(PANEL_LIGHT,PANEL,available?accent:Color.rgb(62,73,100),dp(12)));row.addView(text("FLOOR "+floor+"  ·  "+f.optString("name").toUpperCase(java.util.Locale.ROOT),13,accent,true));
            row.addView(text("HP "+f.optInt("hp")+"  ·  Attack "+f.optInt("attack"),10,MUTED,false));JSONObject reward=f.optJSONObject("reward");if(reward!=null){row.addView(text("◈ "+reward.optLong("coins")+" Gold   ◆ "+reward.optLong("shards")+" Shards",10,GOLD_BRIGHT,true));JSONObject cosmetic=reward.optJSONObject("cosmetic");if(cosmetic!=null)row.addView(text("✦ "+cosmetic.optString("name"),10,VIOLET,true));}
            TextView challenge=button(cleared?"✓ CLEARED":available?"CHALLENGE FLOOR":"LOCKED",available);challenge.setEnabled(available&&ready&&!viewOnly&&!week.isEmpty());challenge.setOnClickListener(v->{challenge.setEnabled(false);api.post("/api/tower/battle",payload("floor",floor,"week",week),r->ui.post(()->{challenge.setEnabled(true);if(!r.ok()){showResultDialog("Tower challenge unavailable",r.error);return;}if(!"tower".equals(activeTab))return;showPhase15Battle(r.data);refreshAccount(()->{if("tower".equals(activeTab))renderTab("tower");});}));});row.addView(challenge,new LinearLayout.LayoutParams(-1,dp(42)));content.addView(row,rowParams());}
        LinearLayout standings=sectionPanel();phase15Heading(standings,"WEEKLY LEADERBOARD");JSONArray leaders=data.optJSONArray("leaderboard");if(leaders==null||leaders.length()==0)standings.addView(text("No clears recorded this week.",10,MUTED,false));else for(int i=0;i<leaders.length();i++){JSONObject p=leaders.optJSONObject(i);if(p!=null)standings.addView(dataRow("#"+(i+1)+"  "+p.optString("display_name"),"Weekly Tower","FLOOR "+p.optInt("floor")),rowParams());}content.addView(standings,rowParams());
    }

    private void showPhase15Battle(JSONObject result){
        JSONObject battle=result.optJSONObject("battle");if(battle==null)return;boolean won=battle.optBoolean("won");
        android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout shell=phase11Sheet("WEEKLY TOWER  ·  FLOOR "+result.optInt("floor"),won?"Victory":"Defeat",won?"Floor cleared.":"Improve your formation and try again.",won?GOLD_BRIGHT:RED);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        JSONArray rounds=battle.optJSONArray("rounds");if(rounds!=null)for(int i=0;i<rounds.length();i++){JSONObject r=rounds.optJSONObject(i);if(r==null)continue;LinearLayout turn=sectionPanel();turn.addView(text("ROUND "+r.optInt("turn"),11,CYAN,true));turn.addView(text("Damage "+r.optInt("hit")+"  ·  Enemy HP "+r.optInt("enemy_hp")+"  ·  Team HP "+r.optInt("player_hp"),11,TEXT,false));if(r.optInt("burn")>0)turn.addView(text("✦ Burning Damage +"+r.optInt("burn"),10,GOLD_BRIGHT,true));if(r.optInt("blocked")>0)turn.addView(text("✧ Steam Shield blocked "+r.optInt("blocked"),10,CYAN,true));if(r.optInt("skill")>0)turn.addView(text("✦ Firstlight Steam +"+r.optInt("skill"),11,VIOLET,true));if(r.optInt("absorbed")>0)turn.addView(text("Enemy shield absorbed "+r.optInt("absorbed"),10,MUTED,false));if(r.optInt("healing")>0)turn.addView(text("Healing +"+r.optInt("healing"),10,GREEN,true));body.addView(turn,rowParams());if(!reduceMotionEnabled){turn.setAlpha(0f);turn.animate().alpha(1f).setStartDelay(i*110L).setDuration(240).start();}}
        JSONObject reward=result.optJSONObject("reward");if(reward!=null){phase15Heading(body,"REWARDS RECEIVED");LinearLayout loot=new LinearLayout(this);loot.addView(claimMetric("GOLD",formatNumber(reward.optLong("coins")),GOLD_BRIGHT),weightParams(1));loot.addView(claimMetric("SHARDS",formatNumber(reward.optLong("shards")),CYAN),weightParams(1));body.addView(loot);if(!reward.isNull("cosmetic_name"))body.addView(text("✦ "+reward.optString("cosmetic_name"),13,VIOLET,true));}
        TextView close=button("CONTINUE",true);close.setOnClickListener(v->dialog.dismiss());addGap(shell,8);shell.addView(close,new LinearLayout.LayoutParams(-1,dp(45)));phase11ShowSheet(dialog,shell,630);
    }


    private String phase16OpenCardId="";
    private final int[] masteryThresholds={5,15,35,70,120};
    private int masteryLevel(int uses){int level=0;for(int threshold:masteryThresholds)if(uses>=threshold)level++;return level;}

    private void phase16SkinOverlay(FrameLayout frame,JSONObject card,boolean animate){
        String skin=card.optString("skin","STANDARD");
        if("STANDARD".equals(skin)||"ALTERNATE".equals(skin))return;
        int color="GOLDEN".equals(skin)?GOLD_BRIGHT:"LIMITED".equals(skin)?PINK:"ANIMATED".equals(skin)?VIOLET:CYAN;
        GradientDrawable foil=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(45,Color.red(color),Color.green(color),Color.blue(color)),Color.TRANSPARENT,Color.argb(35,255,255,255),Color.TRANSPARENT,Color.argb(70,Color.red(color),Color.green(color),Color.blue(color))});
        foil.setCornerRadius(dp(9));foil.setStroke(dp("GOLDEN".equals(skin)||"LIMITED".equals(skin)?3:2),color);
        View effect=new View(this);effect.setBackground(foil);frame.addView(effect,new FrameLayout.LayoutParams(-1,-1));
        if("ANIMATED".equals(skin)&&animate&&!reduceMotionEnabled){
            android.animation.ObjectAnimator glow=android.animation.ObjectAnimator.ofFloat(effect,"alpha",.35f,1f);glow.setDuration(1000);glow.setRepeatMode(android.animation.ValueAnimator.REVERSE);glow.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            effect.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){glow.start();}public void onViewDetachedFromWindow(View v){glow.cancel();}});
        }
    }

    private void renderPhase16Mastery(JSONObject data){
        JSONArray cards=data.optJSONArray("cards");boolean limited=data.optBoolean("limited_unlocked");
        addModeSpotlight("MASTERY  /  COSMETICS","A champion's legacy","Earn mastery by using owned cards in battle. Cosmetics never increase power.","✦",VIOLET);
        LinearLayout milestone=sectionPanel();phase15Heading(milestone,"MASTERY MILESTONES");
        String[] benefits={"Profile badge · Holographic","Golden Frame","Alternate artwork access","Exclusive card title","Animated visual effect"};
        for(int i=0;i<5;i++)milestone.addView(dataRow("MASTERY "+(i+1),benefits[i],masteryThresholds[i]+" USES"),rowParams());content.addView(milestone,rowParams());
        EditText search=editField("Search owned cards",false);search.setSingleLine(true);content.addView(search,new LinearLayout.LayoutParams(-1,dp(44)));
        ArrayList<LinearLayout> rows=new ArrayList<>();ArrayList<String> keys=new ArrayList<>();JSONObject openCard=null;
        if(cards!=null)for(int i=0;i<cards.length();i++){JSONObject card=cards.optJSONObject(i);if(card==null)continue;int level=card.optInt("mastery",masteryLevel(card.optInt("uses")));int accent=cardRarityColor(card.optString("rarity","COMMON"));LinearLayout row=sectionPanel();row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(phase11Art(card,accent),new LinearLayout.LayoutParams(dp(75),dp(105)));
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(10),0,0,0);info.addView(text(card.optString("rarity"),9,accent,true));info.addView(text(card.optString("name"),14,TEXT,true));
            info.addView(text("✦ MASTERY "+level+" / 5",11,VIOLET,true));int uses=card.optInt("uses"),next=level>=5?120:masteryThresholds[level];
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(next);bar.setProgress(Math.min(uses,next));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(VIOLET));info.addView(bar,new LinearLayout.LayoutParams(-1,dp(7)));info.addView(text(uses+" uses · "+card.optString("skin","STANDARD"),10,MUTED,false));row.addView(info,new LinearLayout.LayoutParams(0,-2,1f));row.setOnClickListener(v->showPhase16Card(card,limited));content.addView(row,rowParams());rows.add(row);keys.add((card.optString("name")+" "+card.optString("card_code")+" "+card.optString("rarity")).toLowerCase(java.util.Locale.ROOT));if(card.optString("card_id").equals(phase16OpenCardId))openCard=card;}
        TextView empty=text("No matching owned cards.",11,MUTED,false);empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);content.addView(empty);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){String q=s.toString().trim().toLowerCase(java.util.Locale.ROOT);int n=0;for(int i=0;i<rows.size();i++){boolean show=keys.get(i).contains(q);rows.get(i).setVisibility(show?View.VISIBLE:View.GONE);if(show)n++;}empty.setVisibility(n==0?View.VISIBLE:View.GONE);}public void afterTextChanged(Editable e){}});
        phase16OpenCardId="";if(openCard!=null)showPhase16Card(openCard,limited);
    }

    private void showPhase16Card(JSONObject card,boolean limited){
        int uses=card.optInt("uses"),level=masteryLevel(uses);String equipped=card.optString("skin","STANDARD");
        android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=phase11Sheet("CARD CUSTOMIZATION",card.optString("name"),"Mastery "+level+" / 5 · "+uses+" battle uses",VIOLET);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        FrameLayout preview=new FrameLayout(this);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(132),dp(184));pp.gravity=Gravity.CENTER_HORIZONTAL;body.addView(preview,pp);
        String[] skins={"STANDARD","HOLOGRAPHIC","GOLDEN","ALTERNATE","ANIMATED","LIMITED"},labels={"Standard","Holographic","Golden Frame","Alternate Artwork","Animated Artwork Effect","Limited Edition Frame"};int[] required={0,1,2,3,5,0};
        String[] selected={equipped};TextView selectedLabel=text("",12,GOLD_BRIGHT,true);selectedLabel.setGravity(Gravity.CENTER);body.addView(selectedLabel);
        Runnable redraw=()->{try{JSONObject copy=new JSONObject(card.toString());copy.put("skin",selected[0]);preview.removeAllViews();FrameLayout art=phase11Art(copy,cardRarityColor(copy.optString("rarity","COMMON")));preview.addView(art,new FrameLayout.LayoutParams(-1,-1));if("ANIMATED".equals(selected[0]))phase16SkinOverlay(art,copy,true);selectedLabel.setText("PREVIEW · "+selected[0]);}catch(Exception ignored){}};
        redraw.run();phase15Heading(body,"AVAILABLE SKINS");
        TextView equip=button("EQUIP SKIN",true);
        for(int i=0;i<skins.length;i++){final String skin=skins[i];boolean alternateReady=!card.isNull("alternate_art")&&!card.optString("alternate_art","").isEmpty();boolean unlocked=level>=required[i]&&(!"LIMITED".equals(skin)||limited)&&(!"ALTERNATE".equals(skin)||alternateReady);String reason="LIMITED".equals(skin)?"Earn a World Event frame":"ALTERNATE".equals(skin)&&!alternateReady?"Artwork not available yet":"Mastery "+required[i];TextView row=button(labels[i]+(skin.equals(equipped)?" · EQUIPPED":unlocked?" · PREVIEW":" · LOCKED"),false);row.setEnabled(unlocked);body.addView(row,new LinearLayout.LayoutParams(-1,dp(43)));if(!unlocked)body.addView(text(reason,9,MUTED,false));row.setOnClickListener(v->{selected[0]=skin;redraw.run();});addGap(body,5);}
        if(level>=4){TextView title=button(card.optInt("title_claimed")==1?"✓ EXCLUSIVE TITLE UNLOCKED":"CLAIM EXCLUSIVE TITLE",false);title.setEnabled(card.optInt("title_claimed")!=1);body.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));title.setOnClickListener(v->{title.setEnabled(false);api.post("/api/mastery/title",payload("card_id",card.optString("card_id")),r->ui.post(()->{if(!r.ok()){title.setEnabled(true);showResultDialog("Title unavailable",r.error);return;}title.setText("✓ TITLE UNLOCKED");}));});}
        addGap(shell,8);shell.addView(equip,new LinearLayout.LayoutParams(-1,dp(44)));equip.setOnClickListener(v->{equip.setEnabled(false);api.post("/api/mastery/skin",payload("card_id",card.optString("card_id"),"skin",selected[0]),r->ui.post(()->{if(!r.ok()){equip.setEnabled(true);showResultDialog("Skin unavailable",r.error);return;}dialog.dismiss();phase16OpenCardId=card.optString("card_id");renderTab("mastery");}));});
        TextView close=button("CLOSE",false);close.setOnClickListener(v->dialog.dismiss());shell.addView(close,new LinearLayout.LayoutParams(-1,dp(42)));phase11ShowSheet(dialog,shell,690);
    }

    private void addPhase16ReconnectTimer(JSONObject active){
        int seconds=active.optInt("reconnect_seconds",180);long deadline=android.os.SystemClock.elapsedRealtime()+Math.max(0,seconds)*1000L;
        LinearLayout panel=sectionPanel();panel.setBackground(gradient(Color.rgb(30,52,65),PANEL,CYAN,dp(11)));TextView label=text("",12,CYAN,true);panel.addView(label);panel.addView(text("Your HP and turn are saved on the server. Return before the turn deadline. No move is replayed automatically.",10,MUTED,false));
        TextView resume=button("SYNC / RECONNECT",false);resume.setOnClickListener(v->renderTab("arena"));panel.addView(resume,new LinearLayout.LayoutParams(-1,dp(40)));content.addView(panel,rowParams());
        Runnable tick=new Runnable(){public void run(){if(!"arena".equals(activeTab)||!label.isAttachedToWindow())return;long remain=Math.max(0,(deadline-android.os.SystemClock.elapsedRealtime()+999)/1000);label.setText(String.format(java.util.Locale.ROOT,"RECONNECT WINDOW  %02d:%02d",remain/60,remain%60));if(remain>0)ui.postDelayed(this,1000);else label.setText("TURN WINDOW EXPIRED  ·  SYNC MATCH");}};label.setText("RECONNECT WINDOW · "+seconds+"s");ui.post(tick);
    }

    private void renderPhase16World(JSONObject data){
        JSONObject event=data.optJSONObject("event");if(event==null){showNotice("No active world event.");return;}
        boolean boss="GLOBAL_BOSS".equals(event.optString("key")),done=event.optBoolean("completed"),claimed=event.optBoolean("claimed");int target=Math.max(1,event.optInt("target")),progress=Math.max(0,event.optInt("progress"));
        addModeSpotlight("LIVE WORLD EVENT",event.optString("name"),event.optString("description"),"✺",PINK);
        LinearLayout goal=sectionPanel();phase15Heading(goal,boss?"SHARED BOSS HP":"COMMUNITY PROGRESS");goal.addView(text(boss?formatNumber(event.optLong("remaining"))+" / "+formatNumber(target)+" HP":formatNumber(progress)+" / "+formatNumber(target)+" POINTS",19,GOLD_BRIGHT,true));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(target);bar.setProgress(boss?Math.max(0,target-progress):Math.min(target,progress));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(boss?RED:GREEN));goal.addView(bar,new LinearLayout.LayoutParams(-1,dp(10)));goal.addView(text("Weekly reset · "+event.optString("next_week")+" (Malaysia time)",10,MUTED,false));goal.addView(text("YOUR CONTRIBUTION  ·  "+formatNumber(event.optLong("personal")),12,CYAN,true));content.addView(goal,rowParams());
        LinearLayout reward=sectionPanel();phase15Heading(reward,"LIMITED COSMETIC REWARD");reward.addView(text("✦  "+event.optString("reward_name"),15,VIOLET,true));reward.addView(text("Earned by participating and completing the shared goal before reset.",10,MUTED,false));
        TextView claim=button(claimed?"✓ CLAIMED":done?"CLAIM LIMITED FRAME":"GOAL IN PROGRESS",done&&!claimed);claim.setEnabled(done&&!claimed&&event.optLong("personal")>0&&!isAdminAccount());reward.addView(claim,new LinearLayout.LayoutParams(-1,dp(44)));claim.setOnClickListener(v->{claim.setEnabled(false);api.post("/api/world-events/claim",payload("week",event.optString("week")),r->ui.post(()->{if(!r.ok()){claim.setEnabled(true);showResultDialog("Reward unavailable",r.error);return;}renderTab("world_events");}));});content.addView(reward,rowParams());
        phase15Heading(content,"CONTRIBUTE THROUGH THE REALM");
        String key=event.optString("key");
        if(!boss){addPhase13Link("Adventure","Complete encounters",()->renderTab("adventure"));addPhase13Link("Dungeon","Battle or hunt treasure",()->renderTab("dungeons"));addPhase13Link("Weekly Tower","Clear floors",()->renderTab("tower"));}
        if(!"INVASION".equals(key))addPhase13Link("World Raid","Strike the global guardian",()->renderTab("raids"));
        if("ELEMENTAL".equals(key))addPhase13Link("Team","Equip the featured element for double event points",()->renderTab("formation"));
        TextView refresh=button("REFRESH WORLD PROGRESS",false);refresh.setOnClickListener(v->renderTab("world_events"));content.addView(refresh,new LinearLayout.LayoutParams(-1,dp(44)));
    }

    private String endpointFor(String tab) {
        switch (tab) {
            case "collection": return "/api/cards";
            case "mastery": return "/api/mastery";
            case "world_events": return "/api/world-events";
            case "tower": return "/api/tower";
            case "showcase": return "/api/showcase";
            case "wallet_history": return "/api/wallet/history?currency="+walletHistoryCurrency+"&offset="+walletHistoryOffset;
            case "encyclopedia": return "/api/encyclopedia";
            case "training": return "/api/evolution";
            case "summon_history": return "/api/summon/history?offset=" + summonHistoryOffset;
            case "binder_completion": return "/api/binders/completion";
            case "guild_coop": return "/api/guilds/cooperative";
            case "journey": return "/api/journey";
            case "battle_history": return "/api/battle-history?mode=" + historyMode + "&offset=" + historyOffset;
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
            case "mastery": return "Card Mastery & Skins";
            case "world_events": return "Dynamic World Events";
            case "tower": return "Weekly Challenge Tower";
            case "showcase": return "Player Showcase";
            case "wallet_history": return "Treasury Ledger";
            case "encyclopedia": return "Card Encyclopedia";
            case "training": return "Training Grounds";
            case "summon_history": return "Summon History";
            case "binder_completion": return "Binder Completion Rewards";
            case "guild_coop": return "Guild Cooperative Activities";
            case "journey": return "New Player Journey";
            case "battle_history": return "Battle History";
            case "binders": return "Collector Binders";
            case "formation": return "Team Decks";
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
            case "mastery": return "Earn cosmetics through actual card usage, without increasing battle power.";
            case "world_events": return "Join the realm-wide weekly objective and earn exclusive frames.";
            case "tower": return "Ascend twenty floors, conquer weekly modifiers and unlock rare rewards.";
            case "showcase": return "Your favorite legendary, formation, collection and achievements.";
            case "wallet_history": return "Review your Gold, Gems and Shards transactions.";
            case "encyclopedia": return "Explore card artwork, base power and signature abilities.";
            case "training": return "Practise moves and formations without affecting progression.";
            case "summon_history": return "Review previous summons and the cards each pack revealed.";
            case "binder_completion": return "Collect cards from each set and claim milestone rewards.";
            case "guild_coop": return "Strike the shared boss and complete Guild missions.";
            case "journey": return "Follow your first milestones from the tutorial to the Arena.";
            case "battle_history": return "Review results, rewards and Arena turns.";
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
