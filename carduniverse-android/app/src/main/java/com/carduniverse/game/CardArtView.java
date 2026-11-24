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
    private static final LruCache<String, Bitmap> BITMAP_CACHE = new LruCache<>(8);

    public CardArtView(Context context) { super(context); init(); }
    public CardArtView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
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

        Picture cachedPicture = SVG_CACHE.get(key);
        if (cachedPicture != null) {
            setImageDrawable(new PictureDrawable(cachedPicture));
            return;
        }
        Bitmap cachedBitmap = BITMAP_CACHE.get(key);
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
                connection.setUseCaches(true);
                connection.setRequestProperty("Accept", "image/svg+xml,image/webp,image/*;q=0.9");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new IllegalStateException("Artwork HTTP " + status);

                byte[] bytes;
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    bytes = output.toByteArray();
                }

                if (key.toLowerCase(java.util.Locale.ROOT).endsWith(".svg")) {
                    SVG svg = SVG.getFromInputStream(new java.io.ByteArrayInputStream(bytes));
                    Picture picture = svg.renderToPicture();
                    SVG_CACHE.put(key, picture);
                    asset = picture;
                } else {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap == null) throw new IllegalStateException("Unsupported artwork image");
                    BITMAP_CACHE.put(key, bitmap);
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