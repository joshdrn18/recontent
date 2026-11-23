package com.carduniverse.game;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.net.http.SslError;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String GAME_URL = "https://carduniverse.pages.dev/";
    private static final int FILE_CHOOSER_REQUEST = 4101;
    private static final int WEB_PERMISSION_REQUEST = 4102;

    private FrameLayout root;
    private WebView webView;
    private ProgressBar progressBar;
    private View offlinePanel;
    private ValueCallback<Uri[]> fileChooserCallback;

    private static final String ANDROID_APP_CSS = """
            
            /* CardUniverse Android — landscape-first app presentation */
            html,body{min-height:100%;background:#0b1212!important;overscroll-behavior:none}
            body{ -webkit-text-size-adjust:100%; }
            *{-webkit-tap-highlight-color:transparent}
            button,a,input,select,textarea{-webkit-tap-highlight-color:transparent}
            .wrap{width:100%!important;max-width:none!important;padding-left:max(12px,env(safe-area-inset-left))!important;padding-right:max(12px,env(safe-area-inset-right))!important}
            .modal,#cu-report-overlay,#cu-support-layer,#cu-appeal-overlay,#cu-blocks-layer{padding:max(10px,env(safe-area-inset-top)) max(10px,env(safe-area-inset-right)) max(10px,env(safe-area-inset-bottom)) max(10px,env(safe-area-inset-left))}
            button{touch-action:manipulation}
            .game-section-title h2,.phase2-heading h2{line-height:1.15}
            #content .panel,#content .game-card,#content .mission-card,#content .stage-card{min-width:0}
            #content input,#content select,#content textarea{font-size:16px!important}
            .cu-settings-page{padding-bottom:22px}
            @media (orientation:landscape) and (max-height:700px) and (min-width:700px){
             .wrap{padding:8px 12px 14px!important}
             .game-masthead{height:42px!important;margin-bottom:7px!important;padding:0 3px 6px!important}
             .masthead-brand{gap:8px}.brand-crest{width:29px;height:29px}
             .masthead-wordmark strong{font-size:11px!important;letter-spacing:1.5px!important}
             .masthead-wordmark small{font-size:6px!important;margin-top:3px!important}
             .masthead-season{font-size:7px!important;letter-spacing:1px!important}
             #landing .landing-world-panel{min-height:0!important;padding:22px 26px 16px!important}
             #landing .landing-world-panel h1{font-size:clamp(32px,4vw,52px)!important;margin:12px 0 7px!important}
             #landing .landing-world-panel .auth-lede{font-size:10px!important;line-height:1.5!important;max-width:580px}
             #landing .landing-world-panel .auth-landscape{height:clamp(115px,24vh,175px)!important;margin:0 -10px!important}
             #landing .landing-world-panel .auth-card-stack{transform:scale(.65)!important;right:2%!important;bottom:7px!important;transform-origin:bottom right!important}
             #landing .landing-actions{margin-top:10px!important;padding:13px 17px!important;gap:12px!important}
             #landing .landing-actions-copy h2{font-size:20px!important}
             #landing .landing-actions-copy p{font-size:9px!important}
             #landing .landing-primary{min-width:145px!important;padding:10px 14px!important}
             #landing .landing-secondary{padding:10px 12px!important}
             #landing .landing-feature-section{margin:20px 0 12px!important}
             #landing .landing-feature-grid{gap:9px!important}
             #landing .landing-feature-card{min-height:130px!important;padding:13px!important}
             #auth{padding:0!important}
             .auth-layout{display:grid!important;grid-template-columns:minmax(0,1.18fr) minmax(290px,.82fr)!important;gap:12px!important;min-height:0!important;max-width:none!important;margin:0!important;align-items:stretch!important}
             .auth-world-panel{height:calc(100vh - 62px)!important;min-height:0!important;padding:18px 20px 12px!important}
             .auth-world-panel h1{font-size:clamp(27px,3.3vw,42px)!important;line-height:1.02!important;margin:11px 0 7px!important}
             .auth-lede{font-size:10px!important;line-height:1.45!important;max-width:440px!important}
             .auth-landscape{height:clamp(90px,20vh,140px)!important;margin:1px -9px 0!important}
             .auth-card-stack{transform:scale(.55)!important;right:0!important;bottom:3px!important;transform-origin:bottom right!important}
             .auth-world-facts{font-size:6px!important}
             .auth-form-panel{width:auto!important;max-width:none!important;height:calc(100vh - 62px)!important;min-height:0!important;padding:17px clamp(17px,2.1vw,28px)!important;display:flex!important;flex-direction:column!important;justify-content:center!important}
             .auth-panel-emblem{width:35px!important;height:35px!important;font-size:16px!important;margin-bottom:8px!important}
             .auth-panel-kicker{font-size:7px!important}
             .auth-form-panel h2{font-size:clamp(22px,2.4vw,28px)!important;margin:5px 0!important}
             .auth-panel-copy{font-size:9px!important;line-height:1.45!important;margin:0 0 9px!important}
             .auth-form-panel .field{margin:8px 0!important}
             .auth-form-panel .field label{font-size:7px!important;margin-bottom:4px!important}
             .auth-form-panel .field input{padding:9px 11px!important;font-size:13px!important}
             .auth-submit{padding:10px 13px!important;font-size:10px!important}
             .auth-switch{margin-top:9px!important;font-size:9px!important}
             .auth-switch .secondary{padding:6px 9px!important;font-size:9px!important}
             .auth-security{margin-top:10px!important;font-size:6px!important}
             .game-topbar{grid-template-columns:auto minmax(0,1fr) auto!important;gap:9px!important;padding:5px 0 8px!important}
             .topbar-crest{width:29px!important;height:29px!important;font-size:14px!important}
             .game-topbar-brand{gap:7px!important}
             .game-topbar-brand b{font-size:8px!important;letter-spacing:1.1px!important}
             .game-topbar-brand small{font-size:6px!important;letter-spacing:1px!important}
             .game-player-identity h1{font-size:15px!important}
             .game-player-identity small{font-size:6px!important}
             .game-topbar-actions{gap:6px!important}
             .season-chip{display:none!important}
             .logout-btn{padding:6px 9px!important;font-size:8px!important}
             #app.game-app{max-width:none!important;width:100%!important;padding:0 10px 10px!important}
             .game-layout{grid-template-columns:minmax(0,1fr)!important;gap:7px!important;margin-top:7px!important;min-height:0!important}
             .game-rail{position:sticky!important;top:0!important;z-index:55!important;display:block!important;height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;padding:5px!important;border-radius:9px!important}
             .game-rail-head,.game-rail-footer{display:none!important}
             #app .nav.game-nav,.game-rail .game-nav{position:relative!important;top:auto!important;display:flex!important;flex-direction:row!important;align-items:center!important;gap:4px!important;overflow-x:auto!important;overflow-y:hidden!important;flex-wrap:nowrap!important;white-space:nowrap!important;margin:0!important;padding:3px!important;border-radius:7px!important;scrollbar-width:none!important}
             .game-nav::-webkit-scrollbar{display:none!important}
             .game-nav .nav-cluster{display:contents!important;flex:0 0 auto!important;margin:0!important;padding:0!important}
             .game-nav .nav-cluster-label{display:none!important}
             .game-nav button,#app .nav.game-nav button{display:inline-flex!important;flex:0 0 auto!important;width:auto!important;min-width:max-content!important;min-height:32px!important;padding:6px 9px!important;gap:6px!important;font-size:8px!important;border-radius:6px!important}
             .game-nav button i,#app .nav.game-nav button i{font-size:12px!important}
             .game-stage{min-width:0!important;padding:0 0 12px!important}
             .game-stage-toolbar{min-height:21px!important;font-size:6px!important;margin:0 2px 4px!important}
             .game-content{min-height:0!important;padding-bottom:16px!important}
             .world-banner{min-height:clamp(220px,44vh,315px)!important}
             .world-banner-copy{padding:20px 20px 65px!important;width:68%!important}
             .world-banner-copy h2{font-size:clamp(30px,4vw,48px)!important}
             .world-banner-copy p{font-size:10px!important;line-height:1.5!important}
             .world-banner-emblem{transform:scale(.72)!important;transform-origin:bottom right!important;right:8px!important}
             .home-hud{grid-template-columns:minmax(0,1fr) minmax(0,1fr)!important;gap:9px!important}
             .economy-console{grid-template-columns:repeat(3,minmax(0,1fr))!important}
             .economy-item{padding:7px 9px!important}
             .phase2-page{padding:0 0 15px!important}
             .phase2-heading{margin:4px 0 12px!important}
             .phase2-heading h2{font-size:26px!important}
             #content .game-section-title{margin:14px 0 8px!important}
             #content .game-section-title h2{font-size:19px!important}
             #content .panel,#content .game-card{padding:13px!important}
             #content .grid{gap:9px!important}
             #content .btn{min-height:34px!important;padding:8px 11px!important}
             .modal{padding:8px!important}
             .modal>.panel{max-height:88vh!important;overflow:auto!important;padding:14px!important}
             #cu-report-dialog,#cu-support-dialog,#cu-appeal-dialog,#cu-blocks-dialog{max-height:88vh!important;overflow:auto!important}
             .cu-settings-page{gap:10px!important;padding:0 0 14px!important}
             .cu-settings-hero{padding:14px 17px!important}
             .cu-settings-hero h2{font-size:23px!important;margin:5px 0!important}
             .cu-settings-hero p{font-size:9px!important}
             .cu-settings-section{padding:12px!important}
             .cu-settings-section-head h3{font-size:16px!important}
            }
            @media (orientation:landscape) and (max-height:700px) and (max-width:699px){
             #auth{padding:0!important}
             .auth-layout{display:grid!important;grid-template-columns:1fr!important;max-width:540px!important;min-height:0!important}
             .auth-world-panel{display:none!important}
             .auth-form-panel{height:auto!important;min-height:calc(100vh - 72px)!important;max-width:none!important;padding:24px!important;justify-content:center!important}
             .auth-panel-emblem{margin-bottom:10px!important}
             .auth-form-panel h2{font-size:26px!important}
             .auth-form-panel .field{margin:10px 0!important}
            }
            
            """;


    private PermissionRequest pendingWebPermission;
    private String[] pendingWebResources = new String[0];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        buildLayout();
        configureWebView();

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            return;
        }
        webView.loadUrl(GAME_URL);
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(11, 18, 18));
        window.setNavigationBarColor(Color.rgb(7, 13, 12));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            window.getDecorView().setSystemUiVisibility(flags);
            window.getDecorView().setOnSystemUiVisibilityChangeListener(
                    visibility -> window.getDecorView().setSystemUiVisibility(flags));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private void buildLayout() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(11, 16, 35));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(8, 15, 14));
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(211, 183, 121)));
        progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        root.addView(progressBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP));

        offlinePanel = makeOfflinePanel();
        offlinePanel.setVisibility(View.GONE);
        root.addView(offlinePanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private View makeOfflinePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(24), dp(22), dp(24), dp(22));
        panel.setBackgroundColor(Color.rgb(8, 15, 14));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(24), dp(24), dp(24));
        android.graphics.drawable.GradientDrawable cardBackground =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[]{Color.rgb(30, 49, 39), Color.rgb(15, 26, 22)});
        cardBackground.setCornerRadius(dp(18));
        cardBackground.setStroke(dp(1), Color.rgb(104, 126, 91));
        card.setBackground(cardBackground);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("CARDUNIVERSE  ·  FIRSTLIGHT REALM");
        eyebrow.setTextColor(Color.rgb(211, 183, 121));
        eyebrow.setTextSize(9);
        eyebrow.setLetterSpacing(0.14f);
        eyebrow.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("The realm is out of reach");
        title.setTextColor(Color.rgb(243, 234, 214));
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView detail = new TextView(this);
        detail.setText("CardUniverse needs an internet connection. Check your connection, then reconnect to continue your adventure.");
        detail.setTextColor(Color.rgb(174, 188, 171));
        detail.setTextSize(13);
        detail.setGravity(Gravity.CENTER);
        detail.setLineSpacing(dp(3), 1.0f);

        Button retry = new Button(this);
        retry.setText("RECONNECT");
        retry.setTextColor(Color.rgb(25, 31, 22));
        retry.setTextSize(11);
        retry.setAllCaps(false);
        retry.setTypeface(null, android.graphics.Typeface.BOLD);
        retry.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(211, 183, 121)));
        retry.setOnClickListener(v -> {
            offlinePanel.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.reload();
        });

        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        eyebrowParams.bottomMargin = dp(12);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(10);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(18);

        card.addView(eyebrow, eyebrowParams);
        card.addView(title);
        card.addView(detail, detailParams);
        card.addView(retry, buttonParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.width = Math.min(dp(540), getResources().getDisplayMetrics().widthPixels - dp(32));
        panel.addView(card, cardParams);
        return panel;
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setTextZoom(100);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        settings.setUserAgentString(settings.getUserAgentString() + " CardUniverseAndroid/1.0.0");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return routeUrl(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                offlinePanel.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(5);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                installAndroidPresentation();
                progressBar.setProgress(100);
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showOfflinePanel();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showOfflinePanel();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = callback;
                try {
                    Intent picker = params.createIntent();
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(picker, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermissionRequest(request));
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                downloadFile(url, userAgent, contentDisposition, mimeType));
    }

    private void installAndroidPresentation() {
        if (webView == null) return;
        String script = "(function(){try{"
                + "document.documentElement.classList.add('cu-android-app');"
                + "var viewport=document.querySelector('meta[name=viewport]');"
                + "if(!viewport){viewport=document.createElement('meta');viewport.name='viewport';document.head.appendChild(viewport);}"
                + "viewport.content='width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover';"
                + "var style=document.getElementById('cu-android-app-css');"
                + "if(!style){style=document.createElement('style');style.id='cu-android-app-css';document.head.appendChild(style);}"
                + "style.textContent=" + org.json.JSONObject.quote(ANDROID_APP_CSS) + ";"
                + "}catch(error){console.warn('CardUniverse Android presentation could not be applied',error);}})();";
        webView.evaluateJavascript(script, null);
    }

    private boolean routeUrl(String value) {
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if (scheme == null || "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            // Keep the game and its web-based flows inside this native app.
            return false;
        }
        try {
            if ("intent".equalsIgnoreCase(scheme)) {
                Intent intent = Intent.parseUri(value, Intent.URI_INTENT_SCHEME);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                    return true;
                }
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (fallback != null) webView.loadUrl(fallback);
                return true;
            }
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        } catch (Exception ignored) {
            Toast.makeText(this, "This link cannot be opened on this device.", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        List<String> osPermissions = new ArrayList<>();
        List<String> webResources = new ArrayList<>();

        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                webResources.add(resource);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    osPermissions.add(Manifest.permission.CAMERA);
                }
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                webResources.add(resource);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    osPermissions.add(Manifest.permission.RECORD_AUDIO);
                }
            }
        }

        if (webResources.isEmpty()) {
            request.deny();
            return;
        }
        if (osPermissions.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            request.grant(webResources.toArray(new String[0]));
            return;
        }

        pendingWebPermission = request;
        pendingWebResources = webResources.toArray(new String[0]);
        requestPermissions(osPermissions.toArray(new String[0]), WEB_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != WEB_PERMISSION_REQUEST || pendingWebPermission == null) return;

        boolean granted = grantResults.length > 0;
        for (int result : grantResults) granted &= result == PackageManager.PERMISSION_GRANTED;
        try {
            if (granted) pendingWebPermission.grant(pendingWebResources);
            else pendingWebPermission.deny();
        } catch (IllegalStateException ignored) {
            // The page may cancel the media request while Android shows the permission prompt.
        }
        pendingWebPermission = null;
        pendingWebResources = new String[0];
    }

    private void downloadFile(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            request.setMimeType(mimeType);
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(filename);
            request.setDescription("Downloading from CardUniverse");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "Download started.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Unable to start the download.", Toast.LENGTH_LONG).show();
        }
    }

    private void showOfflinePanel() {
        progressBar.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        offlinePanel.setVisibility(View.VISIBLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileChooserCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileChooserCallback.onReceiveValue(results);
            fileChooserCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
