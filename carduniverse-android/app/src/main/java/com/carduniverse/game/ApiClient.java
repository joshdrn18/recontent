package com.carduniverse.game;

import android.content.Context;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ApiClient {
    public static final String API_ROOT = "https://carduniverse.inipastiuy-41f.workers.dev";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SessionStore sessionStore;
    private volatile String session;

    public interface Callback { void complete(Result result); }

    public static final class Result {
        public final int status;
        public final JSONObject data;
        public final String error;
        public Result(int status, JSONObject data, String error) {
            this.status = status;
            this.data = data;
            this.error = error;
        }
        public boolean ok() { return status >= 200 && status < 300 && data != null && data.optBoolean("ok", true); }
    }

    public ApiClient(Context context) {
        sessionStore = new SessionStore(context.getApplicationContext());
        session = sessionStore.read();
    }

    public boolean hasSession() { return session != null && !session.isEmpty(); }

    public void get(String path, Callback callback) { request("GET", path, null, callback); }
    public void post(String path, JSONObject body, Callback callback) { request("POST", path, body, callback); }
    // Android HttpURLConnection does not permit PATCH; the Worker accepts POST as a profile-update alias.
    public void patch(String path, JSONObject body, Callback callback) { request("POST", path, body, callback); }
    public void delete(String path, Callback callback) { request("DELETE", path, null, callback); }

    public static final class UploadFile {
        public final String filename;
        public final String mimeType;
        public final byte[] bytes;
        public UploadFile(String filename, String mimeType, byte[] bytes) {
            this.filename = filename;
            this.mimeType = mimeType;
            this.bytes = bytes;
        }
    }

    public void postMultipart(String path, List<UploadFile> files, Callback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            Result result;
            try {
                String boundary = "----CardUniverse" + UUID.randomUUID().toString().replace("-", "");
                connection = (HttpURLConnection) new URL(API_ROOT + path).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(45000);
                connection.setUseCaches(false);
                connection.setDoOutput(true);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("X-Client-Platform", "android-native");
                connection.setRequestProperty("X-Client-Version", "2.17.0");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                if (session != null && !session.isEmpty()) {
                    connection.setRequestProperty("Cookie", "cu_session=" + session);
                }
                try (OutputStream output = connection.getOutputStream()) {
                    if (files != null) for (UploadFile file : files) {
                        if (file == null || file.bytes == null || file.bytes.length == 0) continue;
                        String filename = file.filename == null ? "artwork.webp"
                                : file.filename.replace("\\", "_").replace("/", "_")
                                .replace("\"", "_").replace("\r", "").replace("\n", "");
                        String mime = file.mimeType == null || file.mimeType.isEmpty()
                                ? "application/octet-stream" : file.mimeType;
                        output.write(("--" + boundary + "\r\n"
                                + "Content-Disposition: form-data; name=\"artwork\"; filename=\""
                                + filename + "\"\r\n"
                                + "Content-Type: " + mime + "\r\n\r\n")
                                .getBytes(StandardCharsets.UTF_8));
                        output.write(file.bytes);
                        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
                    }
                    output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                int status = connection.getResponseCode();
                captureSession(connection.getHeaderField("Set-Cookie"));
                InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String response = stream == null ? "" : readAll(stream);
                JSONObject json = response.isEmpty() ? new JSONObject() : new JSONObject(response);
                String error = json.optString("error",
                        status >= 400 ? "Artwork upload failed (" + status + ")" : "");
                result = new Result(status, json, error);
            } catch (Exception ex) {
                result = new Result(0, null, ex.getMessage() == null
                        ? "Unable to upload card artwork." : ex.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
            Result finalResult = result;
            if (callback != null) callback.complete(finalResult);
        });
    }

    public void clearSession() {
        session = null;
        sessionStore.clear();
    }

    private void request(String method, String path, JSONObject body, Callback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            Result result;
            try {
                URL url = new URL(API_ROOT + path);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("X-Client-Platform", "android-native");
                connection.setRequestProperty("X-Client-Version", "2.17.0");
                if (session != null && !session.isEmpty()) {
                    connection.setRequestProperty("Cookie", "cu_session=" + session);
                }
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(payload);
                    }
                }
                int status = connection.getResponseCode();
                String setCookie = connection.getHeaderField("Set-Cookie");
                captureSession(setCookie);
                InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String response = stream == null ? "" : readAll(stream);
                JSONObject json = response.isEmpty() ? new JSONObject() : new JSONObject(response);
                if (status == 401 && path.equals("/api/me")) {
                    clearSession();
                }
                String error = json.optString("error", status >= 400 ? "Request failed (" + status + ")" : "");
                result = new Result(status, json, error);
            } catch (Exception ex) {
                result = new Result(0, null, ex.getMessage() == null ? "Unable to connect to CardUniverse." : ex.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
            Result finalResult = result;
            if (callback != null) callback.complete(finalResult);
        });
    }

    private void captureSession(String header) {
        if (header == null) return;
        for (String part : header.split(";")) {
            String item = part.trim();
            if (item.startsWith("cu_session=")) {
                String token = item.substring("cu_session=".length());
                if (!token.isEmpty()) {
                    session = token;
                    sessionStore.write(token);
                } else {
                    clearSession();
                }
                return;
            }
        }
    }

    private static String readAll(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
