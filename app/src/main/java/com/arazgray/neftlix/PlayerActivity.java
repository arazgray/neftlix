package com.arazgray.neftlix;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/**
 * پخش‌کننده داخلی تلویزیون: مناسب ریموت‌کنترل، تمام‌صفحه، افقی.
 * دکمه‌های کنترلر پیش‌فرض PlayerView با D-Pad کار می‌کنند.
 */
public class PlayerActivity extends AppCompatActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";

    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "نشانی پخش نامعتبر است", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        playerView = new PlayerView(this);
        playerView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        playerView.setKeepContentOnPlayerReset(true);
        // دکمه زیرنویس (CC) همیشه در کنترلر دیده شود
        playerView.setShowSubtitleButton(true);
        FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(playerView, videoParams);
        // هایلایت فوکوس دکمه‌های کنترلر برای ریموت
        playerView.post(() -> markFocusable(playerView));

        setContentView(root);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setControllerShowTimeoutMs(4000);
        // زیرنویس فارسی در اولویت باشد
        if (player.getTrackSelector()
                instanceof androidx.media3.exoplayer.trackselection.DefaultTrackSelector) {
            androidx.media3.exoplayer.trackselection.DefaultTrackSelector selector =
                    (androidx.media3.exoplayer.trackselection.DefaultTrackSelector)
                            player.getTrackSelector();
            selector.setParameters(
                    selector.buildUponParameters().setPreferredTextLanguage("fa"));
        }
        androidx.media3.ui.SubtitleView subtitles = playerView.getSubtitleView();
        if (subtitles != null) {
            // پس‌زمینه مشکی حذف شود؛ فقط سایه ساده + راست‌چین + فونت وزیرمتن
            subtitles.setApplyEmbeddedStyles(false);
            subtitles.setTextDirection(View.TEXT_DIRECTION_RTL);
            subtitles.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            android.graphics.Typeface subtitleFont = null;
            try {
                subtitleFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.vazirmatn);
            } catch (Exception ignored) {
            }
            subtitles.setStyle(new androidx.media3.ui.CaptionStyleCompat(
                    android.graphics.Color.WHITE,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                    android.graphics.Color.BLACK,
                    subtitleFont));
        }
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                showCenterError("خطا در پخش. اتصال اینترنت یا نشانی فایل را بررسی کنید.");
            }

            @Override
            public void onTracksChanged(androidx.media3.common.Tracks tracks) {
                ensureSubtitleSelected();
            }
        });

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    /** اگر هیچ زیرنویسی انتخاب نشده ولی ترک متن دارد، اولی را روشن کن. */
    private void ensureSubtitleSelected() {
        if (player == null) return;
        try {
            androidx.media3.common.Tracks tracks = player.getCurrentTracks();
            boolean hasText = false;
            boolean textSelected = false;
            androidx.media3.common.Tracks.Group firstTextGroup = null;
            for (androidx.media3.common.Tracks.Group group : tracks.getGroups()) {
                if (group.getType() != androidx.media3.common.C.TRACK_TYPE_TEXT) continue;
                if (!group.isSupported()) continue;
                hasText = true;
                if (firstTextGroup == null) firstTextGroup = group;
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSelected(i)) {
                        textSelected = true;
                        break;
                    }
                }
                if (textSelected) break;
            }
            if (hasText && !textSelected && firstTextGroup != null
                    && player.getTrackSelector() instanceof androidx.media3.exoplayer.trackselection.DefaultTrackSelector) {
                androidx.media3.exoplayer.trackselection.DefaultTrackSelector selector =
                        (androidx.media3.exoplayer.trackselection.DefaultTrackSelector)
                                player.getTrackSelector();
                selector.setParameters(selector.buildUponParameters()
                        .setPreferredTextLanguage("fa")
                        .setOverrideForType(new androidx.media3.common.TrackSelectionOverride(firstTextGroup.getMediaTrackGroup(), 0)));
            }
        } catch (Exception ignored) {
        }
    }

    private android.widget.TextView centerError;

    private void showCenterError(String message) {
        runOnUiThread(() -> {
            if (centerError == null) {
                centerError = new android.widget.TextView(this);
                centerError.setTextColor(android.graphics.Color.WHITE);
                centerError.setTextSize(16);
                centerError.setGravity(android.view.Gravity.CENTER);
                centerError.setTextDirection(View.TEXT_DIRECTION_RTL);
                centerError.setBackgroundColor(0xCC000000);
                centerError.setPadding(32, 24, 32, 24);
                ((android.widget.FrameLayout) findViewById(android.R.id.content))
                        .addView(centerError, new android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                android.view.Gravity.CENTER));
            }
            centerError.setText(message);
            centerError.setVisibility(View.VISIBLE);
            centerError.postDelayed(() -> centerError.setVisibility(View.GONE), 5000);
        });
    }

    private android.graphics.drawable.GradientDrawable focusBackground;

    private void markFocusable(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                markFocusable(group.getChildAt(i));
            }
            return;
        }
        if (v.isFocusable()) {
            if (focusBackground == null) {
                focusBackground = new android.graphics.drawable.GradientDrawable();
                focusBackground.setColor(0xFFE50914);
                focusBackground.setCornerRadius(12f);
            }
            if (v instanceof androidx.media3.ui.DefaultTimeBar) {
                // نوار پیشرفت: روی فوکوس قرمز شود، بدون تغییر اندازه
                androidx.media3.ui.DefaultTimeBar bar = (androidx.media3.ui.DefaultTimeBar) v;
                v.setOnFocusChangeListener((view, hasFocus) -> {
                    if (hasFocus) {
                        bar.setPlayedColor(0xFFE50914);
                        bar.setScrubberColor(0xFFE50914);
                    } else {
                        bar.setPlayedColor(0xFFFFFFFF);
                        bar.setScrubberColor(0xFFFFFFFF);
                    }
                });
                return;
            }
            final android.graphics.drawable.Drawable normal = v.getBackground();
            v.setOnFocusChangeListener((view, hasFocus) -> {
                if (view instanceof android.widget.ImageButton) {
                    view.setBackground(hasFocus ? focusBackground : null);
                    if (!hasFocus && normal != null) view.setBackground(normal);
                }
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (player != null
                && (playerView == null || !playerView.isControllerFullyVisible())) {
            // فقط وقتی کنترلر پنهان است: چپ/راست = ۱۵ ثانیه عقب/جلو
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_REWIND) {
                player.seekTo(Math.max(0, player.getCurrentPosition() - 15000));
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                long duration = player.getDuration();
                long target = player.getCurrentPosition() + 15000;
                player.seekTo(duration == androidx.media3.common.C.TIME_UNSET
                        ? target : Math.min(duration, target));
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // بار اول کنترلر پنهان شود، بار دوم خروج
        if (playerView != null && playerView.isControllerFullyVisible()) {
            playerView.hideController();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) player.play();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
