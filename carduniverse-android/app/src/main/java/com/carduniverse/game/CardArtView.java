package com.carduniverse.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Picture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.PictureDrawable;
import android.util.AttributeSet;
import android.util.LruCache;
import android.widget.ImageView;

import com.caverock.androidsvg.SVG;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CardArtView extends ImageView {
    private static final ExecutorService LOADERS = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Picture> SVG_CACHE = new LruCache<>(48);
    private static final int MAX_DOWNLOAD_BYTES = 12 * 1024 * 1024;
    private static final int MAX_BITMAP_EDGE = 1536;
    // Accessed only from the Android UI thread; weak references do not retain detached screens.
    private static final java.util.Set<CardArtView> VIEWS =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<CardArtView, Boolean>());
    private static final java.util.regex.Pattern STABLE_CARD = java.util.regex.Pattern.compile(
            "^/media/cards/(?:artwork|full)/((?:FL|MY|EV)-\\d{3})\\.svg$",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    static void refreshPublishedCards(java.util.Map<String, String> previous,
                                      java.util.Map<String, String> current) {
        java.util.Map<String, String> reverse = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, String> item : previous.entrySet()) {
            reverse.put(item.getValue(), item.getKey());
        }
        for (CardArtView view : new java.util.ArrayList<>(VIEWS)) {
            if (view == null || !view.isAttachedToWindow() || !(view.getTag() instanceof String)) continue;
            String old = (String) view.getTag();
            String code = reverse.get(old);
            if (code == null) {
                java.util.regex.Matcher match = STABLE_CARD.matcher(old);
                if (match.matches()) code = match.group(1).toUpperCase(java.util.Locale.ROOT);
            }
            String next = code == null ? null : current.get(code);
            if (next != null && !next.equals(old)) view.loadArtwork(next);
        }
    }

    private static boolean rasterImage(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255) return true;
        if (bytes.length >= 8 && (bytes[0] & 255) == 137 && bytes[1] == 80
                && bytes[2] == 78 && bytes[3] == 71 && bytes[4] == 13
                && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) return true;
        return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }
    private static final LruCache<String, Bitmap> BITMAP_CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    };

    public CardArtView(Context context) { super(context); init(); }
    public CardArtView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        VIEWS.add(this);
        setScaleType(ScaleType.FIT_CENTER);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Card character artwork");
    }

    public void clearArtwork() {
        setTag(null);
        setImageDrawable(null);
    }

    public void loadArtwork(String path) {
        if (path == null || path.trim().isEmpty()) { clearArtwork(); return; }
        final String key = path.trim();
        if (!key.startsWith("/media/cards/") && !key.startsWith("/art/")) {
            clearArtwork(); return;
        }
        setTag(key);
        setImageDrawable(null);

        // Mutable legacy aliases must revalidate; immutable revision URLs can use the LRU.
        final boolean immutable = !key.startsWith("/media/cards/full/");
        Picture cachedPicture = immutable ? SVG_CACHE.get(key) : null;
        if (cachedPicture != null) {
            setImageDrawable(new PictureDrawable(cachedPicture));
            return;
        }
        Bitmap cachedBitmap = immutable ? BITMAP_CACHE.get(key) : null;
        if (cachedBitmap != null) {
            setImageDrawable(new BitmapDrawable(getResources(), cachedBitmap));
            return;
        }

        LOADERS.execute(() -> {
            Object asset = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiClient.API_ROOT + key);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setUseCaches(immutable);
                if (!immutable) connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("Accept", "image/svg+xml,image/webp,image/*;q=0.9");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new IllegalStateException("Artwork HTTP " + status);

                byte[] bytes;
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() > MAX_DOWNLOAD_BYTES - count)
                            throw new IllegalStateException("Artwork exceeds safe download size");
                        output.write(buffer, 0, count);
                    }
                    bytes = output.toByteArray();
                }

                if (!rasterImage(bytes) && (key.toLowerCase(java.util.Locale.ROOT).endsWith(".svg")
                        || String.valueOf(connection.getContentType()).startsWith("image/svg+xml"))) {
                    SVG svg = SVG.getFromInputStream(new java.io.ByteArrayInputStream(bytes));
                    Picture picture = svg.renderToPicture();
                    if (immutable) SVG_CACHE.put(key, picture);
                    asset = picture;
                } else {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                        throw new IllegalStateException("Invalid artwork dimensions");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 1;
                    while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > MAX_BITMAP_EDGE)
                        options.inSampleSize *= 2;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    if (bitmap == null) throw new IllegalStateException("Unsupported artwork image");
                    if (immutable) BITMAP_CACHE.put(key, bitmap);
                    asset = bitmap;
                }
            } catch (Exception ignored) {
                asset = null;
            } finally {
                if (connection != null) connection.disconnect();
            }

            final Object loaded = asset;
            post(() -> {
                if (!key.equals(getTag()) || loaded == null) return;
                if (loaded instanceof Picture) {
                    setImageDrawable(new PictureDrawable((Picture) loaded));
                } else if (loaded instanceof Bitmap) {
                    setImageDrawable(new BitmapDrawable(getResources(), (Bitmap) loaded));
                }
            });
        });
    }
}