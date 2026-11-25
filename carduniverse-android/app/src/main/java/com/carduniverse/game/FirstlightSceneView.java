package com.carduniverse.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/** Small perspective renderer for Firstlight's stonework, crystals and portal. */
final class FirstlightSceneView extends View {
    enum Scene { GATE, ARENA, TOWER }

    private static final int FOG = Color.rgb(13, 19, 37);
    private static final int STONE = Color.rgb(44, 59, 88);
    private static final int SLATE = Color.rgb(66, 76, 109);
    private static final int BRASS = Color.rgb(202, 151, 81);
    private static final int LIGHT = Color.rgb(255, 225, 166);
    private static final int CYAN = Color.rgb(87, 229, 217);
    private static final int VIOLET = Color.rgb(165, 139, 246);
    private static final int EMBER = Color.rgb(248, 121, 110);
    private static final float ANGLE = 0.35f;
    private static final Comparator<Face> FURTHEST_FIRST = Comparator.comparingDouble(face -> -face.depth);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Scene scene;
    private final boolean motion;
    private final boolean orbit;
    private final Face[] faces;
    private final Face[] depthOrder;
    private final float[] stars = new float[45 * 3];
    private final long began = SystemClock.uptimeMillis();
    private LinearGradient sky;
    private float yaw;
    private float touchX;
    private float touchY;
    private float touchYaw;
    private boolean dragging;

    FirstlightSceneView(Context context, Scene scene, boolean motion, boolean orbit) {
        super(context);
        this.scene = scene;
        this.motion = motion;
        this.orbit = orbit;
        if (orbit) {
            setClickable(true);
            setFocusable(true);
            setContentDescription(scene == Scene.TOWER ? "Tower in 3D. Swipe sideways to orbit. Tap to reset."
                    : "Firstlight Gate in 3D. Swipe sideways to orbit. Tap to reset.");
        } else {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        ArrayList<Face> mesh = new ArrayList<>();
        ground(mesh);
        switch (scene) {
            case GATE: gate(mesh); break;
            case ARENA: arena(mesh); break;
            case TOWER: tower(mesh); break;
        }
        faces = mesh.toArray(new Face[0]);
        depthOrder = faces.clone();
        Random random = new Random(2719);
        for (int i = 0; i < stars.length; i += 3) {
            stars[i] = random.nextFloat();
            stars[i + 1] = random.nextFloat() * 0.72f;
            stars[i + 2] = 0.55f + random.nextFloat() * 1.25f;
        }
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h > 0) {
            sky = new LinearGradient(0, 0, 0, h,
                    new int[]{0xff070c19, 0xff1b2444, 0xff303b57, 0xff101829},
                    new float[]{0f, 0.42f, 0.72f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0 || sky == null) return;
        paint.setShader(sky);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        drawStars(canvas, width, height);

        float seconds = motion ? (SystemClock.uptimeMillis() - began) / 1000f : 0f;
        float angle = yaw + (scene == Scene.ARENA ? 0.28f : -0.12f);
        if (motion && !dragging) angle += (float) Math.sin(seconds * 0.24f) * 0.095f;
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        float aim = scene == Scene.TOWER ? 2.3f : 1.55f;
        float distance = scene == Scene.ARENA ? 14.8f : 13.3f;
        float focal = Math.min(width * 0.88f, height * 1.3f);
        float sinTilt = (float) Math.sin(ANGLE), cosTilt = (float) Math.cos(ANGLE);
        for (Face face : faces) {
            float bob = face.hover ? (float) Math.sin(seconds * 1.8f + face.phase) * 0.15f : 0f;
            float depth = 0;
            face.visible = true;
            for (int i = 0; i < face.xyz.length; i += 3) {
                float x = face.xyz[i];
                float y = face.xyz[i + 1] - aim + bob;
                float z = face.xyz[i + 2];
                float cameraX = x * cos + z * sin;
                float rotatedZ = z * cos - x * sin;
                float cameraY = y * cosTilt + rotatedZ * sinTilt;
                float cameraZ = distance + rotatedZ * cosTilt - y * sinTilt;
                if (cameraZ < 0.5f) {
                    face.visible = false;
                    break;
                }
                float scale = focal / cameraZ;
                int pos = i / 3 * 2;
                face.screen[pos] = width * 0.5f + cameraX * scale;
                face.screen[pos + 1] = height * 0.54f - cameraY * scale;
                depth += cameraZ;
            }
            face.depth = depth * 3 / face.xyz.length;
        }
        Arrays.sort(depthOrder, FURTHEST_FIRST);
        for (Face face : depthOrder) {
            if (!face.visible) continue;
            float fog = Math.max(0f, Math.min(0.68f, (face.depth - 12f) / 34f));
            int r = (int) (Color.red(face.color) * (1f - fog) + Color.red(FOG) * fog);
            int g = (int) (Color.green(face.color) * (1f - fog) + Color.green(FOG) * fog);
            int b = (int) (Color.blue(face.color) * (1f - fog) + Color.blue(FOG) * fog);
            paint.setColor(Color.argb(Color.alpha(face.color), r, g, b));
            path.rewind();
            path.moveTo(face.screen[0], face.screen[1]);
            for (int i = 2; i < face.screen.length; i += 2) path.lineTo(face.screen[i], face.screen[i + 1]);
            path.close();
            canvas.drawPath(path, paint);
        }
        if (motion && isShown() && getWindowVisibility() == VISIBLE) postInvalidateDelayed(45);
    }

    private void drawStars(Canvas canvas, float width, float height) {
        paint.setColor(0x9ad7e1f4);
        for (int i = 0; i < stars.length; i += 3) {
            canvas.drawCircle(stars[i] * width, stars[i + 1] * height, stars[i + 2], paint);
        }
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!orbit) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchX = event.getX();
                touchY = event.getY();
                touchYaw = yaw;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - touchX;
                float dy = event.getY() - touchY;
                float threshold = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (!dragging && Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    yaw = Math.max(-0.85f, Math.min(0.85f, touchYaw + dx / Math.max(1, getWidth()) * 2.3f));
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging && Math.abs(event.getY() - touchY) < android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                    performClick();
                }
                // Fall through to release the enclosing ScrollView.
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() {
        if (!orbit) return super.performClick();
        super.performClick();
        yaw = 0;
        invalidate();
        return true;
    }

    private static final class Face {
        final int color;
        final float[] xyz;
        final float[] screen;
        final boolean hover;
        final float phase;
        float depth;
        boolean visible;

        Face(int color, boolean hover, float[] xyz) {
            this.color = color;
            this.hover = hover;
            this.phase = xyz[0];
            this.xyz = xyz;
            this.screen = new float[xyz.length / 3 * 2];
        }
    }

    private static int shade(int color, float factor) {
        return Color.argb(Color.alpha(color), Math.min(255, (int) (Color.red(color) * factor)),
                Math.min(255, (int) (Color.green(color) * factor)), Math.min(255, (int) (Color.blue(color) * factor)));
    }

    private static void polygon(ArrayList<Face> mesh, int color, boolean hover, float[]... vertices) {
        float[] xyz = new float[vertices.length * 3];
        for (int i = 0; i < vertices.length; i++) System.arraycopy(vertices[i], 0, xyz, i * 3, 3);
        mesh.add(new Face(color, hover, xyz));
    }

    private static float[] point(float x, float y, float z) { return new float[]{x, y, z}; }

    private static float[] ring(float x, float y, float z, float radius, double angle) {
        return point(x + (float) Math.cos(angle) * radius, y, z + (float) Math.sin(angle) * radius);
    }

    private static void platform(ArrayList<Face> mesh, float x, float y, float z,
                                 float radius, float depth, int sides, int color) {
        float[] top = point(x, y, z);
        for (int side = 0; side < sides; side++) {
            double a = side * Math.PI * 2 / sides, b = (side + 1) * Math.PI * 2 / sides;
            float[] left = ring(x, y, z, radius, a), right = ring(x, y, z, radius, b);
            polygon(mesh, shade(color, 1.08f), false, top, left, right);
            polygon(mesh, shade(color, 0.53f + (float) Math.sin(a + 0.8) * 0.19f), false,
                    left, right, ring(x, y - depth, z, radius * 0.94f, b),
                    ring(x, y - depth, z, radius * 0.94f, a));
        }
    }

    private static void crystal(ArrayList<Face> mesh, float x, float y, float z, float radius,
                                float height, int color, boolean hover) {
        float[] tip = point(x, y + height, z);
        float[] base = point(x, y - height * 0.65f, z);
        for (int side = 0; side < 5; side++) {
            double a = side * Math.PI * 2 / 5, b = (side + 1) * Math.PI * 2 / 5;
            float[] left = ring(x, y, z, radius, a), right = ring(x, y, z, radius, b);
            float intensity = 0.56f + 0.48f * (float) (Math.cos(a - 0.2) * 0.5 + 0.5);
            polygon(mesh, shade(color, intensity), hover, tip, left, right);
            polygon(mesh, shade(color, intensity * 0.66f), hover, base, left, right);
        }
    }

    private static float[] portalPoint(float centerY, float z, float radius, double angle) {
        return point((float) Math.cos(angle) * radius, centerY + (float) Math.sin(angle) * radius, z);
    }

    private static void portal(ArrayList<Face> mesh, float y, float z, float radius, int metal, int glow) {
        for (int part = 0; part < 20; part++) {
            double a = part * Math.PI * 2 / 20, b = (part + 1) * Math.PI * 2 / 20;
            polygon(mesh, shade(glow, (part % 3 == 0) ? 1f : 0.76f), false,
                    portalPoint(y, z - 0.1f, radius * 0.86f, a), portalPoint(y, z - 0.1f, radius * 0.86f, b),
                    portalPoint(y, z, radius * 0.92f, b), portalPoint(y, z, radius * 0.92f, a));
            polygon(mesh, shade(metal, (part % 2 == 0) ? 1.12f : 0.8f), false,
                    portalPoint(y, z, radius * 0.92f, a), portalPoint(y, z, radius * 0.92f, b),
                    portalPoint(y, z + 0.12f, radius, b), portalPoint(y, z + 0.12f, radius, a));
        }
    }

    private static void ground(ArrayList<Face> mesh) {
        platform(mesh, 0, -0.15f, 0.2f, 6.9f, 0.75f, 18, STONE);
        for (int i = 0; i < 7; i++) {
            float z = -4.8f + i * 1.37f;
            platform(mesh, i % 2 == 0 ? -3.95f : 3.95f, 0.12f, z, 0.48f, 0.2f, 4,
                    shade(STONE, i % 2 == 0 ? 1.26f : 1.1f));
        }
        for (int i = 0; i < 8; i++) {
            float x = -9.1f + i * 2.6f;
            float z = 8f + i % 3;
            crystal(mesh, x, 0.1f, z, 1.05f, 1.6f + i % 4 * 0.7f, shade(SLATE, 0.78f), false);
        }
    }

    private static void gate(ArrayList<Face> mesh) {
        for (int step = 0; step < 5; step++) {
            float z = -2.6f + step * 1.0f;
            platform(mesh, 0, 0.06f + step * 0.12f, z, 1.55f, 0.24f, 4, shade(SLATE, 0.9f + step * 0.06f));
        }
        platform(mesh, 0, 0.68f, 2.6f, 2.55f, 0.45f, 12, SLATE);
        portal(mesh, 2.62f, 2.6f, 2.12f, BRASS, LIGHT);
        crystal(mesh, 0, 2.58f, 2.46f, 0.5f, 1.08f, CYAN, true);
        for (int side : new int[]{-1,1}) {
            platform(mesh, side * 3.5f, 0.34f, 1.45f, 0.85f, 0.36f, 6, STONE);
            crystal(mesh, side * 3.5f, 1.23f, 1.45f, 0.49f, 1.04f, VIOLET, true);
            crystal(mesh, side * 2.55f, 0.65f, -2.2f, 0.24f, 0.55f, CYAN, true);
        }
    }

    private static void arena(ArrayList<Face> mesh) {
        platform(mesh, 0, 0.11f, 0f, 5.05f, 0.19f, 20, shade(SLATE, 0.93f));
        platform(mesh, 0, 0.16f, 0f, 3.65f, 0.1f, 18, shade(BRASS, 0.7f));
        platform(mesh, 0, 0.20f, 0f, 3.49f, 0.09f, 18, STONE);
        for (int side : new int[]{-1,1}) {
            int accent = side < 0 ? CYAN : EMBER;
            platform(mesh, side * 2.3f, 0.25f, 0, 1.28f, 0.24f, 8, shade(accent, 0.39f));
            crystal(mesh, side * 2.3f, 1.25f, 0, 0.49f, 0.92f, accent, true);
            for (int p = 0; p < 3; p++)
                crystal(mesh, side * (4.5f + p * 0.15f), 0.5f, -3.2f + p * 2.55f, 0.19f, 0.48f, accent, true);
        }
    }

    private static void tower(ArrayList<Face> mesh) {
        for (int level = 0; level < 7; level++) {
            float y = 0.08f + level * 0.54f, z = 1.0f + level * 0.18f;
            float radius = 3.05f - level * 0.33f;
            platform(mesh, 0, y, z, radius, 0.34f, 8, level % 2 == 0 ? SLATE : shade(STONE, 1.12f));
            if (level < 6) crystal(mesh, radius * 0.7f * (level % 2 == 0 ? -1 : 1),
                    y + 0.05f, z - radius * 0.54f, 0.22f, 0.45f, VIOLET, true);
        }
        crystal(mesh, 0, 4.18f, 2.1f, 0.56f, 1.2f, LIGHT, true);
        portal(mesh, 4.06f, 2.35f, 0.98f, BRASS, VIOLET);
    }
}
