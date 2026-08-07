package com.seyirplus.m3utv;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class PlayerActivity extends Activity {
    private PlayerView playerView;
    private ExoPlayer player;
    private TextView topText;
    private TextView hintText;
    private ProgressBar loading;
    private JSONArray variants = new JSONArray();
    private int index = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable prepareTimeout;
    private String title = "Video";
    private boolean changingSource = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        title = getIntent().getStringExtra("title");
        if (title == null) title = "Video";
        try { variants = new JSONArray(getIntent().getStringExtra("variants")); }
        catch (Exception ignored) { variants = new JSONArray(); }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setKeepContentOnPlayerReset(true);
        playerView.setFocusable(true);
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(playerView, vp);

        loading = new ProgressBar(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(72, 72, Gravity.CENTER);
        root.addView(loading, lp);

        topText = new TextView(this);
        topText.setTextColor(Color.WHITE);
        topText.setTextSize(16);
        topText.setBackgroundColor(0x99000000);
        topText.setPadding(22, 14, 22, 14);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        root.addView(topText, tp);

        hintText = new TextView(this);
        hintText.setTextColor(0xFFBBBBCC);
        hintText.setTextSize(12);
        hintText.setGravity(Gravity.CENTER);
        hintText.setText("OK: Oynat/Duraklat   ←/→: 15 sn   CH+/CH-: Kaynak değiştir   Geri: Çıkış");
        hintText.setBackgroundColor(0x99000000);
        hintText.setPadding(16, 10, 16, 10);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(hintText, hp);

        setContentView(root);
        hideSystemUi();
        playIndex(0);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private JSONObject current() {
        return index >= 0 && index < variants.length() ? variants.optJSONObject(index) : null;
    }

    private Map<String, String> headers(JSONObject v) {
        Map<String, String> out = new HashMap<>();
        JSONObject h = v == null ? null : v.optJSONObject("headers");
        if (h != null) {
            Iterator<String> keys = h.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String val = h.optString(k, "");
                if (!val.isEmpty()) out.put(k, val);
            }
        }
        return out;
    }

    private String inferMime(String url) {
        String low = url == null ? "" : url.toLowerCase(Locale.US);
        if (low.contains(".m3u8")) return MimeTypes.APPLICATION_M3U8;
        if (low.contains(".mpd")) return MimeTypes.APPLICATION_MPD;
        return null;
    }

    private void releasePlayer() {
        cancelPrepareTimeout();
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private void playIndex(int requested) {
        if (variants.length() == 0) {
            loading.setVisibility(View.GONE);
            topText.setText(title + "\nOynatma kaynağı bulunamadı");
            return;
        }
        int i = requested;
        if (i < 0) i = variants.length() - 1;
        if (i >= variants.length()) i = 0;
        index = i;
        JSONObject v = current();
        if (v == null) return;
        String url = v.optString("url", "");
        if (url.isEmpty()) {
            tryNext("Geçersiz kaynak");
            return;
        }

        changingSource = true;
        releasePlayer();
        loading.setVisibility(View.VISIBLE);
        updateTop();

        Map<String, String> requestHeaders = headers(v);
        String ua = requestHeaders.remove("User-Agent");
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000)
                .setUserAgent(ua == null || ua.isEmpty() ? "SeyirPlusM3UTV/1.0" : ua)
                .setDefaultRequestProperties(requestHeaders);
        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, httpFactory);
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build();
        playerView.setPlayer(player);
        playerView.requestFocus();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) loading.setVisibility(View.VISIBLE);
                if (playbackState == Player.STATE_READY) {
                    changingSource = false;
                    cancelPrepareTimeout();
                    loading.setVisibility(View.GONE);
                    updateTop();
                }
                if (playbackState == Player.STATE_ENDED) updateTop();
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (!changingSource) tryNext("Akış açılamadı");
                else tryNext("Kaynak hazırlanamadı");
            }
        });

        MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(url));
        String mime = inferMime(url);
        if (mime != null) item.setMimeType(mime);
        player.setMediaItem(item.build());
        player.prepare();
        player.setPlayWhenReady(true);

        prepareTimeout = () -> {
            if (player != null && player.getPlaybackState() != Player.STATE_READY) tryNext("Kaynak zaman aşımı");
        };
        handler.postDelayed(prepareTimeout, 25000);
    }

    private boolean tryNext(String reason) {
        cancelPrepareTimeout();
        if (variants.length() <= 1) {
            loading.setVisibility(View.GONE);
            changingSource = false;
            topText.setVisibility(View.VISIBLE);
            topText.setText(title + "\n" + reason);
            Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
            return true;
        }
        int next = (index + 1) % variants.length();
        Toast.makeText(this, reason + " · sonraki kaynak deneniyor", Toast.LENGTH_SHORT).show();
        handler.postDelayed(() -> playIndex(next), 400);
        return true;
    }

    private void cancelPrepareTimeout() {
        if (prepareTimeout != null) handler.removeCallbacks(prepareTimeout);
        prepareTimeout = null;
    }

    private void updateTop() {
        JSONObject v = current();
        String source = v == null ? "" : v.optString("sourceName", "M3U");
        topText.setText(title + (source.isEmpty() ? "" : "  ·  " + source) + (variants.length() > 1 ? "  [" + (index + 1) + "/" + variants.length() + "]" : ""));
        topText.setVisibility(View.VISIBLE);
        hintText.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOverlay);
        handler.postDelayed(hideOverlay, 4500);
    }

    private final Runnable hideOverlay = () -> {
        if (player != null && player.isPlaying()) {
            topText.setVisibility(View.GONE);
            hintText.setVisibility(View.GONE);
        }
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        int k = event.getKeyCode();
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (player != null) {
                if (player.isPlaying()) player.pause(); else player.play();
                updateTop();
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_MEDIA_REWIND) {
            if (player != null && player.isCurrentMediaItemSeekable()) player.seekTo(Math.max(0, player.getCurrentPosition() - 15000));
            updateTop();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            if (player != null && player.isCurrentMediaItemSeekable()) {
                long p = player.getCurrentPosition() + 15000;
                long d = player.getDuration();
                if (d > 0) p = Math.min(d, p);
                player.seekTo(p);
            }
            updateTop();
            return true;
        }
        if (k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_MEDIA_NEXT || k == KeyEvent.KEYCODE_PAGE_UP) {
            playIndex(index + 1);
            return true;
        }
        if (k == KeyEvent.KEYCODE_CHANNEL_DOWN || k == KeyEvent.KEYCODE_MEDIA_PREVIOUS || k == KeyEvent.KEYCODE_PAGE_DOWN) {
            playIndex(index - 1);
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
            updateTop();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onPause() {
        if (player != null) player.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }
}
