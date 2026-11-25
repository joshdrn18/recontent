package com.carduniverse.game;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.filament.Engine;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.LightManager;
import com.google.android.filament.Skybox;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.utils.KTX1Loader;
import com.google.android.filament.utils.Manipulator;
import com.google.android.filament.utils.ModelViewer;
import com.google.android.filament.utils.Utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class FirstlightSceneView extends FrameLayout {
    enum Scene {
        GATE("gate", "Firstlight Gate", 4.1f, 6.8f, 15.7f, 3.56f, -1.30f, 32f),
        ARENA("arena", "Arena", 4.5f, 8.1f, 14.1f, 1.2f, 0f, 31f),
        TOWER("tower", "Weekly Tower", 6.4f, 7.5f, 17.4f, 4.9f, -1f, 31f);

        final String asset;
        final String label;
        final float x, y, z;
        final float targetY, targetZ;
        final float lens;

        Scene(String asset, String label, float x, float y, float z,
              float targetY, float targetZ, float lens) {
            this.asset = asset;
            this.label = label;
            this.x = x;
            this.y = y;
            this.z = z;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.lens = lens;
        }
    }

    private static final String TAG = "FirstlightScene";
    private static final String ASSET_PATH = "firstlight/";
    private static final ExecutorService ASSET_IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean nativeRendererUnavailable;

    private final Scene scene;
    private final boolean use3d;
    private final boolean orbit;
    private final boolean reducedMotion;
    private final ImageView poster;
    private final Choreographer.FrameCallback frameCallback = this::drawFrame;
    private ModelViewer viewer;
    private Manipulator cameraOrbit;
    private UiHelper surface;
    private TextureView texture;
    private TextView orbitHint;
    private Skybox skybox;
    private IndirectLight indirectLight;
    private int attachment;
    private int framesAfterLoad;
    private float touchX, touchY;
    private boolean dragging;
    private boolean framePending;
    private boolean cameraConfigured;

    FirstlightSceneView(Context context, Scene scene, boolean use3d, boolean reducedMotion, boolean orbit) {
        super(context);
        this.scene = scene;
        this.use3d = use3d;
        this.orbit = orbit;
        this.reducedMotion = reducedMotion;
        setImportantForAccessibility(orbit ? IMPORTANT_FOR_ACCESSIBILITY_YES : IMPORTANT_FOR_ACCESSIBILITY_NO);
        setContentDescription(scene.label);
        setBackgroundColor(0xff151a24);
        poster = new ImageView(context);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(poster, new LayoutParams(-1, -1));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final int generation = ++attachment;
        ASSET_IO.execute(() -> {
            try (InputStream image = getContext().getAssets().open(ASSET_PATH + scene.asset + ".webp")) {
                var artwork = BitmapFactory.decodeStream(image);
                MAIN.post(() -> {
                    if (generation == attachment && isAttachedToWindow()) poster.setImageBitmap(artwork);
                });
            } catch (IOException error) {
                Log.w(TAG, "Could not load scene artwork", error);
            }

            if (!use3d || !supportsOpenGl()) return;
            try {
                ByteBuffer model = mapAsset(scene.asset + ".glb");
                ByteBuffer sky = mapAsset("sky.ktx");
                ByteBuffer ambient = mapAsset("ambient.ktx");
                MAIN.post(() -> {
                    if (generation == attachment && isAttachedToWindow()) startRenderer(model, sky, ambient);
                });
            } catch (IOException error) {
                Log.w(TAG, "Could not load Firstlight geometry", error);
            }
        });
    }

    private boolean supportsOpenGl() {
        ActivityManager manager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        return manager != null && manager.getDeviceConfigurationInfo().reqGlEsVersion >= 0x00030000;
    }

    private ByteBuffer mapAsset(String file) throws IOException {
        try (AssetFileDescriptor asset = getContext().getAssets().openFd(ASSET_PATH + file);
             FileInputStream stream = asset.createInputStream()) {
            return stream.getChannel().map(FileChannel.MapMode.READ_ONLY, asset.getStartOffset(), asset.getLength());
        }
    }

    private void startRenderer(ByteBuffer model, ByteBuffer sky, ByteBuffer ambient) {
        if (nativeRendererUnavailable) return;
        try {
            Utils.INSTANCE.init();
            cameraOrbit = new Manipulator.Builder()
                    .orbitHomePosition(scene.x, scene.y, scene.z)
                    .targetPosition(0, scene.targetY, scene.targetZ)
                    .orbitSpeed(0.0035f, 0.0035f)
                    .build(Manipulator.Mode.ORBIT);
            texture = new TextureView(getContext());
            texture.setOpaque(true);
            texture.setAlpha(0f);
            texture.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            // Register before ModelViewer so the textures are released before it destroys the engine.
            texture.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {}
                @Override public void onViewDetachedFromWindow(View v) { releaseEnvironment(); }
            });
            addView(texture, new LayoutParams(-1, -1));
            surface = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
            Engine engine = Engine.create(Engine.Backend.OPENGL);
            viewer = new ModelViewer(texture, engine, surface, cameraOrbit);
            configureLight(engine, sky, ambient);
            viewer.loadModelGlb(model);
            if (viewer.getAsset() == null) {
                showPoster();
                return;
            }
            framesAfterLoad = 0;
            cameraConfigured = false;
            requestFrame();
        } catch (RuntimeException | LinkageError error) {
            nativeRendererUnavailable = true;
            Log.w(TAG, "Falling back to Firstlight artwork", error);
            showPoster();
        }
    }

    private void configureLight(Engine engine, ByteBuffer sky, ByteBuffer ambient) {
        KTX1Loader.Options options = new KTX1Loader.Options();
        skybox = KTX1Loader.INSTANCE.createSkybox(engine, sky, options);
        viewer.getScene().setSkybox(skybox);
        indirectLight = KTX1Loader.INSTANCE.createIndirectLight(engine, ambient, options);
        indirectLight.setIntensity(24000);
        viewer.getScene().setIndirectLight(indirectLight);

        LightManager lights = engine.getLightManager();
        int sun = lights.getInstance(viewer.getLight());
        lights.setDirection(sun, 0.51f, -0.78f, -0.39f);
        lights.setIntensity(sun, 63000);
        lights.setColor(sun, 1.0f, 0.86f, 0.71f);
        viewer.getCamera().setExposure(11f, 1f / 100f, 150f);
        com.google.android.filament.View view = viewer.getView();
        view.setAntiAliasing(com.google.android.filament.View.AntiAliasing.FXAA);
        com.google.android.filament.View.BloomOptions glow = new com.google.android.filament.View.BloomOptions();
        glow.enabled = true;
        glow.resolution = 160;
        glow.strength = 0.16f;
        view.setBloomOptions(glow);

        com.google.android.filament.View.FogOptions fog = new com.google.android.filament.View.FogOptions();
        fog.enabled = true;
        fog.distance = 19f;
        fog.maximumOpacity = 0.52f;
        fog.density = 0.021f;
        fog.color = new float[]{0.07f, 0.09f, 0.13f};
        view.setFogOptions(fog);
    }

    private void requestFrame() {
        if (framePending || viewer == null || !isShown() || getWindowVisibility() != VISIBLE) return;
        framePending = true;
        Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 22);
    }

    private void drawFrame(long time) {
        framePending = false;
        if (viewer == null || !isShown() || getWindowVisibility() != VISIBLE) return;
        try {
            if (!cameraConfigured && surface.isReadyToRender()) {
                var viewport = viewer.getView().getViewport();
                if (viewport.width > 0 && viewport.height > 0) {
                    viewer.setCameraFocalLength(scene.lens);
                    cameraConfigured = true;
                }
            }
            viewer.render(time);
            if (surface.isReadyToRender() && viewer.getProgress() >= 1f && framesAfterLoad < 3) {
                if (++framesAfterLoad == 3) {
                    if (reducedMotion) texture.setAlpha(1f);
                    else texture.animate().alpha(1f).setDuration(180).start();
                    setClickable(orbit);
                    setFocusable(orbit);
                    if (orbit) showOrbitHint();
                }
            }
            if (framesAfterLoad < 3 || dragging) requestFrame();
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "Could not render Firstlight geometry", error);
            showPoster();
        }
    }

    private void showOrbitHint() {
        setContentDescription(scene.label + ". Drag sideways to orbit. Tap to reset.");
        orbitHint = new TextView(getContext());
        orbitHint.setText("Drag to orbit");
        orbitHint.setTextSize(11);
        orbitHint.setTextColor(0xffffe6b6);
        orbitHint.setBackgroundColor(0xdd101727);
        orbitHint.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        float density = getResources().getDisplayMetrics().density;
        int horizontal = Math.round(8 * density);
        int vertical = Math.round(4 * density);
        orbitHint.setPadding(horizontal, vertical, horizontal, vertical);
        LayoutParams badge = new LayoutParams(-2, -2, android.view.Gravity.TOP | android.view.Gravity.END);
        badge.setMargins(horizontal, horizontal, horizontal, 0);
        addView(orbitHint, badge);
    }

    private void releaseEnvironment() {
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        framePending = false;
        dragging = false;
        if (viewer != null) {
            Engine engine = viewer.getEngine();
            if (skybox != null) {
                viewer.getScene().setSkybox(null);
                var cubemap = skybox.getTexture();
                engine.destroySkybox(skybox);
                if (cubemap != null) engine.destroyTexture(cubemap);
            }
            if (indirectLight != null) {
                viewer.getScene().setIndirectLight(null);
                var reflections = indirectLight.getReflectionsTexture();
                engine.destroyIndirectLight(indirectLight);
                if (reflections != null) engine.destroyTexture(reflections);
            }
        }
        viewer = null;
        cameraOrbit = null;
        skybox = null;
        indirectLight = null;
    }

    private void showPoster() {
        if (texture != null) {
            removeView(texture);
            texture = null;
        }
        if (orbitHint != null) {
            removeView(orbitHint);
            orbitHint = null;
        }
        setContentDescription(scene.label);
        setClickable(false);
        setFocusable(false);
    }

    @Override protected void onDetachedFromWindow() {
        attachment++;
        showPoster();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) requestFrame();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        requestFrame();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!orbit || framesAfterLoad < 3 || cameraOrbit == null) return super.onTouchEvent(event);
        int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchX = event.getX();
                touchY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - touchX;
                float dy = event.getY() - touchY;
                int cameraY = Math.round(getHeight() - touchY);
                if (!dragging && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    cameraOrbit.grabBegin(Math.round(touchX), cameraY, false);
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    cameraOrbit.grabUpdate(Math.round(event.getX()), cameraY);
                    requestFrame();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging && Math.abs(event.getY() - touchY) < touchSlop) performClick();
                // Release the scroll container for either gesture ending.
            case MotionEvent.ACTION_CANCEL:
                if (dragging) cameraOrbit.grabEnd();
                dragging = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        if (cameraOrbit != null && orbit) {
            cameraOrbit.jumpToBookmark(cameraOrbit.getHomeBookmark());
            requestFrame();
        }
        return true;
    }
}
