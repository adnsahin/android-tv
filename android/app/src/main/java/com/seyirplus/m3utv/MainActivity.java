package com.seyirplus.m3utv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class MainActivity extends Activity {
    private static final String PREFS = "seyirplus_m3u_prefs";
    private static final String SOURCES_KEY = "sources_json";
    private static final int MAX_TEXT_BYTES = 40 * 1024 * 1024;
    private WebView webView;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(13, 13, 26));
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " SeyirPlusM3UTV/1.0");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && "file".equalsIgnoreCase(u.getScheme())) return false;
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && "seyirplus.local".equalsIgnoreCase(u.getHost()) && "/image".equals(u.getPath())) {
                    try {
                        return downloadImage(u.getQueryParameter("url"), u.getQueryParameter("ref"), u.getQueryParameter("ua"));
                    } catch (Exception ignored) {
                        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(this), "Android");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("window.handleAndroidBack ? String(window.handleAndroidBack()) : 'false'", null);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
        }
        super.onDestroy();
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "[]";
        }
    }

    private String initialSources() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (p.contains(SOURCES_KEY)) return p.getString(SOURCES_KEY, "[]");
        String raw = readAsset("embedded_sources.json");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                if (!o.has("id") || o.optString("id").isEmpty()) o.put("id", "embedded_" + i);
                if (!o.has("enabled")) o.put("enabled", true);
                if (!o.has("type")) o.put("type", "auto");
            }
            raw = a.toString();
        } catch (Exception ignored) {
            raw = "[]";
        }
        p.edit().putString(SOURCES_KEY, raw).apply();
        return raw;
    }

    private File cacheFile(String id) {
        String safe = id == null ? "none" : id.replaceAll("[^A-Za-z0-9._-]", "_");
        File dir = new File(getFilesDir(), "m3u_cache");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, safe + ".m3u");
    }

    private String readCacheFile(String id) {
        File f = cacheFile(id);
        if (!f.exists() || f.length() > MAX_TEXT_BYTES) return "";
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private void writeCacheFile(String id, String text) {
        if (text == null) return;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) return;
        try (FileOutputStream out = new FileOutputStream(cacheFile(id))) {
            out.write(bytes);
        } catch (Exception ignored) {}
    }


    private WebResourceResponse downloadImage(String rawUrl, String referrer, String userAgent) throws Exception {
        if (rawUrl == null || rawUrl.isEmpty()) throw new Exception("empty image url");
        URL url = new URL(rawUrl);
        for (int redirect = 0; redirect < 5; redirect++) {
            String protocol = url.getProtocol().toLowerCase(Locale.US);
            if (!protocol.equals("http") && !protocol.equals("https")) throw new Exception("bad protocol");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(9000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "image/*,*/*;q=0.8");
            if (referrer != null && !referrer.isEmpty()) c.setRequestProperty("Referer", referrer);
            c.setRequestProperty("User-Agent", userAgent == null || userAgent.isEmpty() ? "Mozilla/5.0 SeyirPlusM3UTV/1.0" : userAgent);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null || loc.isEmpty()) throw new Exception("redirect missing");
                url = new URL(url, loc);
                continue;
            }
            if (code < 200 || code >= 300) { c.disconnect(); throw new Exception("HTTP " + code); }
            String type = c.getContentType();
            if (type == null || type.isEmpty()) type = "image/*";
            try (InputStream in = new BufferedInputStream(c.getInputStream()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n, total = 0;
                while ((n = in.read(buf)) >= 0) {
                    total += n;
                    if (total > 8 * 1024 * 1024) throw new Exception("image too large");
                    out.write(buf, 0, n);
                }
                byte[] body = out.toByteArray();
                c.disconnect();
                return new WebResourceResponse(type.split(";")[0], null, new ByteArrayInputStream(body));
            }
        }
        throw new Exception("too many redirects");
    }

    private static Charset charsetFrom(String contentType) {
        if (contentType != null) {
            String low = contentType.toLowerCase(Locale.US);
            int p = low.indexOf("charset=");
            if (p >= 0) {
                String c = contentType.substring(p + 8).trim().replace("\"", "");
                int semicolon = c.indexOf(';');
                if (semicolon > 0) c = c.substring(0, semicolon).trim();
                try { return Charset.forName(c); } catch (Exception ignored) {}
            }
        }
        return StandardCharsets.UTF_8;
    }

    private FetchResult downloadText(String rawUrl, String headersJson) throws Exception {
        URL url = new URL(rawUrl);
        String protocol = url.getProtocol().toLowerCase(Locale.US);
        if (!protocol.equals("http") && !protocol.equals("https")) throw new Exception("Yalnızca HTTP/HTTPS desteklenir");

        JSONObject headers;
        try { headers = new JSONObject(headersJson == null ? "{}" : headersJson); }
        catch (Exception e) { headers = new JSONObject(); }

        for (int redirect = 0; redirect < 6; redirect++) {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(12000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Accept", "*/*");
            c.setRequestProperty("Accept-Encoding", "gzip");
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String v = headers.optString(k, "");
                if (!v.isEmpty()) c.setRequestProperty(k, v);
            }
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null || loc.isEmpty()) throw new Exception("Yönlendirme adresi eksik");
                url = new URL(url, loc);
                continue;
            }
            if (code < 200 || code >= 300) {
                c.disconnect();
                throw new Exception("HTTP " + code);
            }
            InputStream raw = new BufferedInputStream(c.getInputStream());
            InputStream in = "gzip".equalsIgnoreCase(c.getContentEncoding()) ? new GZIPInputStream(raw) : raw;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int total = 0, n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > MAX_TEXT_BYTES) {
                    in.close(); c.disconnect();
                    throw new Exception("Yanıt çok büyük (40 MB sınırı)");
                }
                out.write(buf, 0, n);
            }
            in.close();
            Charset cs = charsetFrom(c.getContentType());
            String text = new String(out.toByteArray(), cs);
            c.disconnect();
            return new FetchResult(code, text);
        }
        throw new Exception("Çok fazla yönlendirme");
    }

    private void callback(String id, boolean ok, String payload, int status) {
        final String body = payload == null ? "" : payload;
        if (!ok || body.length() <= 250000) {
            String js = "window.__nativeFetchResult(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(body) + "," + status + ")";
            runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(js, null); });
            return;
        }
        final int chunkSize = 200000;
        runOnUiThread(() -> {
            if (webView == null) return;
            for (int start = 0; start < body.length(); start += chunkSize) {
                int end = Math.min(body.length(), start + chunkSize);
                boolean last = end >= body.length();
                String chunk = body.substring(start, end);
                String js = "window.__nativeFetchChunk(" + JSONObject.quote(id) + "," + JSONObject.quote(chunk) + "," + last + "," + status + ")";
                webView.evaluateJavascript(js, null);
            }
        });
    }

    private static class FetchResult {
        final int status; final String text;
        FetchResult(int status, String text) { this.status = status; this.text = text; }
    }

    public class AndroidBridge {
        private final Context context;
        AndroidBridge(Context context) { this.context = context; }

        @JavascriptInterface
        public String loadSources() { return initialSources(); }

        @JavascriptInterface
        public void saveSources(String json) {
            try {
                new JSONArray(json);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(SOURCES_KEY, json).apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String readCache(String id) { return readCacheFile(id); }

        @JavascriptInterface
        public void writeCache(String id, String text) { executor.execute(() -> writeCacheFile(id, text)); }

        @JavascriptInterface
        public void fetchText(String requestId, String url, String headersJson) {
            executor.execute(() -> {
                try {
                    FetchResult r = downloadText(url, headersJson);
                    callback(requestId, true, r.text, r.status);
                } catch (Exception e) {
                    callback(requestId, false, e.getMessage() == null ? "Bağlantı hatası" : e.getMessage(), 0);
                }
            });
        }

        @JavascriptInterface
        public void play(String title, String variantsJson) {
            Intent i = new Intent(context, PlayerActivity.class);
            i.putExtra("title", title == null ? "Video" : title);
            i.putExtra("variants", variantsJson == null ? "[]" : variantsJson);
            startActivity(i);
        }

        @JavascriptInterface
        public void exitApp() { runOnUiThread(MainActivity.this::finish); }

        @JavascriptInterface
        public String appVersion() { return "1.0.0"; }
    }
}
