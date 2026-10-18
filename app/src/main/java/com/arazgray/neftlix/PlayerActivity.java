package com.arazgray.neftlix;

import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
    private Ui ui;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
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
        playerView.setShutterBackgroundColor(android.graphics.Color.BLACK);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setShowSubtitleButton(true);
        // هنگام پخش، دستگاه نباید به اسکرین‌سیور/قفل برود (مثل Mi Box)
        playerView.setKeepScreenOn(true);
        FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(playerView, videoParams);
        // هایلایت فوکوس دکمه‌های کنترلر برای ریموت
        playerView.post(() -> {
            markFocusable(playerView);
            android.view.View ccButton =
                    playerView.findViewById(androidx.media3.ui.R.id.exo_subtitle);
            if (ccButton != null) {
                ccButton.setOnClickListener(v -> showSubtitleDialog());
            }
            // عقب/جلو ۵/۱۵ ثانیه‌ای مستقیم سیم‌کشی می‌شوند تا هایلایت قرمز حتمی باشد
            wireControlButton(playerView, androidx.media3.ui.R.id.exo_rew);
            wireControlButton(playerView, androidx.media3.ui.R.id.exo_ffwd);
            // تنظیمات جدا حذف شد؛ همه‌چیز در همان دیالوگ گزینه‌هاست
            android.view.View settingsButton =
                    playerView.findViewById(androidx.media3.ui.R.id.exo_settings);
            if (settingsButton != null) {
                settingsButton.setVisibility(View.GONE);
            }
        });

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
                centerError.setTextColor(Ui.TEXT);
                centerError.setTextSize(15);
                centerError.setGravity(android.view.Gravity.CENTER);
                centerError.setTextDirection(View.TEXT_DIRECTION_RTL);
                centerError.setBackground(ui.stroked(0xE6141415, 14, 1, Ui.HAIRLINE));
                centerError.setPadding(ui.dp(28), ui.dp(20), ui.dp(28), ui.dp(20));
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

    private android.graphics.drawable.GradientDrawable focusBackgroundFor() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFE50914);
        bg.setCornerRadius(12f);
        return bg;
    }

    /** سیم‌کشی مستقیم یک دکمهٔ کنترلر با هایلایت قرمز، مستقل از کلاس ویو. */
    private void wireControlButton(PlayerView root, int id) {
        android.view.View button = root.findViewById(id);
        if (button == null || !button.isFocusable()) return;
        final android.graphics.drawable.Drawable normal = button.getBackground();
        button.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                view.setBackground(focusBackgroundFor());
            } else if (normal != null) {
                view.setBackground(normal);
            } else {
                view.setBackground(null);
            }
        });
    }

    /** دیالوگ گزینه‌های پخش (زیرنویس/صدا/کیفیت) با دکمه‌های قرمز؛ انتخاب همان‌جا اعمال می‌شود. */
    private void showSubtitleDialog() {
        if (player == null) return;
        if (!(player.getTrackSelector()
                instanceof androidx.media3.exoplayer.trackselection.DefaultTrackSelector)) {
            return;
        }
        final androidx.media3.exoplayer.trackselection.DefaultTrackSelector selector =
                (androidx.media3.exoplayer.trackselection.DefaultTrackSelector)
                        player.getTrackSelector();

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));

        boolean hasSection = false;
        try {
            androidx.media3.common.Tracks tracks = player.getCurrentTracks();

            // زیرنویس
            java.util.List<androidx.media3.common.Tracks.Group> textGroups = new java.util.ArrayList<>();
            java.util.List<String> labels = new java.util.ArrayList<>();
            java.util.List<Integer> selected = new java.util.ArrayList<>();
            collectTextTracks(tracks, textGroups, labels, selected);
            if (!textGroups.isEmpty()) {
                hasSection = true;
                list.addView(settingsSectionLabel("زیرنویس", false));
                for (int i = 0; i < labels.size(); i++) {
                    Button option = dialogOptionButton(
                            labels.get(i) + (selected.contains(i) ? " (فعال)" : ""));
                    final int trackIndex = i;
                    option.setOnClickListener(v -> applySubtitleChoice(textGroups, trackIndex));
                    list.addView(option, new LinearLayout.LayoutParams(-1, dp(52)));
                }
                Button off = dialogOptionButton("خاموش");
                off.setOnClickListener(v -> applySubtitleChoice(textGroups, -1));
                list.addView(off, new LinearLayout.LayoutParams(-1, dp(52)));
            }

            // صدا (فقط وقتی چند صدا هست)
            java.util.List<AudioOption> audios = new java.util.ArrayList<>();
            java.util.List<String> seenCodes = new java.util.ArrayList<>();
            for (androidx.media3.common.Tracks.Group group : tracks.getGroups()) {
                if (group.getType() != androidx.media3.common.C.TRACK_TYPE_AUDIO
                        || !group.isSupported()) {
                    continue;
                }
                for (int i = 0; i < group.length; i++) {
                    String code = group.getTrackFormat(i).language;
                    String key = code == null ? "" : code;
                    if (seenCodes.contains(key)) continue;
                    seenCodes.add(key);
                    audios.add(new AudioOption(code, languageName(code), group.isTrackSelected(i)));
                }
            }
            if (audios.size() > 1) {
                hasSection = true;
                list.addView(settingsSectionLabel("صدا", true));
                Button auto = dialogOptionButton("خودکار");
                auto.setOnClickListener(v -> selector.setParameters(selector.buildUponParameters()
                        .setPreferredAudioLanguage(null)));
                list.addView(auto, new LinearLayout.LayoutParams(-1, dp(52)));
                for (final AudioOption audio : audios) {
                    Button option = dialogOptionButton(
                            audio.label + (audio.selected ? " (فعال)" : ""));
                    option.setOnClickListener(v -> selector.setParameters(
                            selector.buildUponParameters()
                                    .setPreferredAudioLanguage(audio.code)));
                    list.addView(option, new LinearLayout.LayoutParams(-1, dp(52)));
                }
            }

            // کیفیت تصویر (فقط وقتی چند کیفیت هست)
            java.util.List<String> videoLabels = new java.util.ArrayList<>();
            int maxHeight = 0;
            for (androidx.media3.common.Tracks.Group group : tracks.getGroups()) {
                if (group.getType() != androidx.media3.common.C.TRACK_TYPE_VIDEO
                        || !group.isSupported()) {
                    continue;
                }
                for (int i = 0; i < group.length; i++) {
                    int height = group.getTrackFormat(i).height;
                    String label = height > 0 ? height + "p" : "کیفیت " + (videoLabels.size() + 1);
                    if (videoLabels.contains(label)) continue;
                    videoLabels.add(label);
                    if (height > maxHeight) maxHeight = height;
                }
            }
            if (videoLabels.size() > 1) {
                hasSection = true;
                list.addView(settingsSectionLabel("کیفیت تصویر", true));
                Button auto = dialogOptionButton("خودکار");
                auto.setOnClickListener(v -> selector.setParameters(selector.buildUponParameters()
                        .setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)));
                list.addView(auto, new LinearLayout.LayoutParams(-1, dp(52)));
                int[][] presets = {{2160, 3840, 2160}, {1080, 1920, 1080}, {720, 1280, 720}, {480, 854, 480}};
                for (int[] preset : presets) {
                    if (preset[0] > maxHeight) continue;
                    Button quality = dialogOptionButton(preset[0] + "p");
                    final int width = preset[1];
                    final int height = preset[2];
                    quality.setOnClickListener(v -> selector.setParameters(
                            selector.buildUponParameters().setMaxVideoSize(width, height)));
                    list.addView(quality, new LinearLayout.LayoutParams(-1, dp(52)));
                }
            }
        } catch (Exception ignored) {
        }

        if (!hasSection) {
            android.widget.Toast.makeText(this, "گزینه‌ای برای این ویدیو نیست", Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(list);
        final androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("تنظیمات پخش")
                        .setView(scroll)
                        .setNegativeButton("بستن", null)
                        .show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(0xFF000000));
            // دیالوگ کوچک‌تر وسط صفحه
            android.view.WindowManager.LayoutParams attributes = dialog.getWindow().getAttributes();
            attributes.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.55);
            dialog.getWindow().setAttributes(attributes);
        }
    }

    /** دکمهٔ گزینه‌های دیالوگ (زیرنویس/تنظیمات)؛ روی فوکوس قطعاً قرمز. */
    private Button dialogOptionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(android.graphics.Color.WHITE);
        button.setTextDirection(View.TEXT_DIRECTION_RTL);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        final android.graphics.drawable.GradientDrawable idle =
                new android.graphics.drawable.GradientDrawable();
        idle.setColor(0xFF2D2D2D);
        idle.setCornerRadius(dp(8));
        final android.graphics.drawable.GradientDrawable focused =
                new android.graphics.drawable.GradientDrawable();
        focused.setColor(0xFFE50914);
        focused.setCornerRadius(dp(8));
        button.setBackground(idle);
        button.setOnFocusChangeListener((v, hasFocus) ->
                button.setBackground(hasFocus ? focused : idle));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(0, dp(3), 0, dp(3));
        button.setLayoutParams(params);
        return button;
    }

    /** نام نمایشی زبان ترک (فارسی‌سازی‌شده) برای دیالوگ‌های پخش. */
    private String languageName(String code) {
        if (code == null || code.isEmpty() || "und".equals(code)) return "نامشخص";
        try {
            return new java.util.Locale(code).getDisplayLanguage(new java.util.Locale("fa"));
        } catch (Exception ignored) {
            return code;
        }
    }

    /** فهرست ترک‌های زیرنویس؛ خروجی‌ها به ترتیب نمایشی پر می‌شوند. */
    private void collectTextTracks(androidx.media3.common.Tracks tracks,
            java.util.List<androidx.media3.common.Tracks.Group> textGroups,
            java.util.List<String> labels,
            java.util.List<Integer> selected) {
        int index = 0;
        for (androidx.media3.common.Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != androidx.media3.common.C.TRACK_TYPE_TEXT
                    || !group.isSupported()) {
                continue;
            }
            for (int i = 0; i < group.length; i++) {
                labels.add("زیرنویس " + languageName(group.getTrackFormat(i).language));
                if (group.isTrackSelected(i)) selected.add(index);
                textGroups.add(group);
                index++;
            }
        }
    }

    private void applySubtitleChoice(java.util.List<androidx.media3.common.Tracks.Group> textGroups, int chosen) {
        if (player == null) return;
        try {
            if (!(player.getTrackSelector()
                    instanceof androidx.media3.exoplayer.trackselection.DefaultTrackSelector)) {
                return;
            }
            androidx.media3.exoplayer.trackselection.DefaultTrackSelector selector =
                    (androidx.media3.exoplayer.trackselection.DefaultTrackSelector)
                            player.getTrackSelector();
            if (chosen < 0) {
                selector.setParameters(selector.buildUponParameters()
                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true));
            } else {
                androidx.media3.common.Tracks.Group group = textGroups.get(chosen);
                int trackInGroup = 0;
                int seen = -1;
                for (int i = 0; i <= chosen; i++) {
                    if (textGroups.get(i) == group) {
                        seen++;
                        if (i == chosen) trackInGroup = seen;
                    }
                }
                selector.setParameters(selector.buildUponParameters()
                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage(null)
                        .setOverrideForType(new androidx.media3.common.TrackSelectionOverride(
                                group.getMediaTrackGroup(), trackInGroup)));
            }
        } catch (Exception ignored) {
        }
    }

    private static final class AudioOption {
        final String code;
        final String label;
        final boolean selected;

        AudioOption(String code, String label, boolean selected) {
            this.code = code;
            this.label = label;
            this.selected = selected;
        }
    }

    private android.widget.TextView settingsSectionLabel(String text, boolean spaced) {
        android.widget.TextView label = new android.widget.TextView(this);
        label.setText(text);
        label.setTextColor(Ui.TEXT_DIM);
        label.setTextSize(13);
        label.setTextDirection(View.TEXT_DIRECTION_RTL);
        label.setPadding(dp(4), dp(spaced ? 12 : 2), dp(4), dp(4));
        return label;
    }

    private void markFocusable(View v) {
        if (v instanceof ViewGroup) {
            // فقط داخل کانتینرها می‌گردیم؛ خود کانتینر هرگز قرمز نمی‌شود تا هیچ پنلی رنگ نگیرد
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                markFocusable(group.getChildAt(i));
            }
            return;
        }
        if (!v.isFocusable()) return;
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
        // فقط دکمه‌ها (نه هر نمایی) قرمز شوند: عقب/جلو و بقیه از هر کلاسی باشند پوشش داده می‌شوند
        if (!(v instanceof android.widget.ImageView) && !(v instanceof android.widget.Button)) {
            return;
        }
        final android.graphics.drawable.Drawable normal = v.getBackground();
        v.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                view.setBackground(focusBackgroundFor());
            } else if (normal != null) {
                view.setBackground(normal);
            } else {
                view.setBackground(null);
            }
        });
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

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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
