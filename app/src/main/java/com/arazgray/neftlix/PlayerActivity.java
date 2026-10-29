package com.arazgray.neftlix;

import android.net.Uri;
import android.content.Context;
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

public class PlayerActivity extends AppCompatActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";

    private ExoPlayer player;
    private PlayerView playerView;
    private Ui ui;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, getString(R.string.p_bad_url), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        playerView = new PlayerView(this);
        playerView.setKeepContentOnPlayerReset(true);
        playerView.setShutterBackgroundColor(android.graphics.Color.BLACK);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setShowSubtitleButton(true);
        
        playerView.setKeepScreenOn(true);
        FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(playerView, videoParams);
        
        playerView.post(() -> {
            setupCustomControlIcons(playerView);
            stylePlayerControls(playerView);
            android.view.View ccButton =
                    playerView.findViewById(androidx.media3.ui.R.id.exo_subtitle);
            if (ccButton != null) {
                ccButton.setOnClickListener(v -> showSubtitleDialog());
            }
            
            android.view.View settingsButton =
                    playerView.findViewById(androidx.media3.ui.R.id.exo_settings);
            if (settingsButton != null) {
                settingsButton.setVisibility(View.GONE);
            }
        });
        playerView.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility -> {
                    if (visibility == View.VISIBLE) {
                        playerView.post(() -> {
                            setupCustomControlIcons(playerView);
                            stylePlayerControls(playerView);
                        });
                    }
                });

        setContentView(root);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setControllerShowTimeoutMs(4000);
        
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
            
            subtitles.setApplyEmbeddedStyles(false);
            subtitles.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
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
                showCenterError(getString(R.string.p_error));
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
                centerError.setTypeface(ui.font, android.graphics.Typeface.NORMAL);
                centerError.setGravity(android.view.Gravity.CENTER);
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

    private android.graphics.drawable.GradientDrawable controlChip(int color) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(12f);
        return bg;
    }

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
        list.setPadding(dp(12), dp(12), dp(12), dp(12));

        boolean hasSection = false;
        try {
            androidx.media3.common.Tracks tracks = player.getCurrentTracks();

            java.util.List<androidx.media3.common.Tracks.Group> textGroups = new java.util.ArrayList<>();
            java.util.List<String> labels = new java.util.ArrayList<>();
            java.util.List<Integer> selected = new java.util.ArrayList<>();
            collectTextTracks(tracks, textGroups, labels, selected);
            if (!textGroups.isEmpty()) {
                hasSection = true;
                list.addView(settingsSectionLabel(getString(R.string.p_subtitles), false));
                for (int i = 0; i < labels.size(); i++) {
                    final boolean isActive = selected.contains(i);
                    Button option = dialogOptionButton(labels.get(i), isActive);
                    final int trackIndex = i;
                    option.setOnClickListener(v -> applySubtitleChoice(textGroups, trackIndex));
                    list.addView(option);
                }
                boolean isOff = selected.isEmpty();
                Button off = dialogOptionButton(getString(R.string.p_off), isOff);
                off.setOnClickListener(v -> applySubtitleChoice(textGroups, -1));
                list.addView(off);
            }

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
                list.addView(settingsSectionLabel(getString(R.string.p_audio), true));
                boolean anySelected = false;
                for (AudioOption a : audios) if (a.selected) anySelected = true;
                Button auto = dialogOptionButton(getString(R.string.p_auto), !anySelected);
                auto.setOnClickListener(v -> selector.setParameters(selector.buildUponParameters()
                        .setPreferredAudioLanguage(null)));
                list.addView(auto);
                for (final AudioOption audio : audios) {
                    Button option = dialogOptionButton(audio.label, audio.selected);
                    option.setOnClickListener(v -> selector.setParameters(
                            selector.buildUponParameters()
                                    .setPreferredAudioLanguage(audio.code)));
                    list.addView(option);
                }
            }

            java.util.List<String> videoLabels = new java.util.ArrayList<>();
            int maxHeight = 0;
            for (androidx.media3.common.Tracks.Group group : tracks.getGroups()) {
                if (group.getType() != androidx.media3.common.C.TRACK_TYPE_VIDEO
                        || !group.isSupported()) {
                    continue;
                }
                for (int i = 0; i < group.length; i++) {
                    int height = group.getTrackFormat(i).height;
                    String label = height > 0 ? height + "p" : getString(R.string.p_quality_n, ui.digits(videoLabels.size() + 1));
                    if (videoLabels.contains(label)) continue;
                    videoLabels.add(label);
                    if (height > maxHeight) maxHeight = height;
                }
            }
            if (videoLabels.size() > 1) {
                hasSection = true;
                list.addView(settingsSectionLabel(getString(R.string.p_quality), true));
                Button auto = dialogOptionButton(getString(R.string.p_auto), true);
                auto.setOnClickListener(v -> selector.setParameters(selector.buildUponParameters()
                        .setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)));
                list.addView(auto);
                int[][] presets = {{2160, 3840, 2160}, {1080, 1920, 1080}, {720, 1280, 720}, {480, 854, 480}};
                for (int[] preset : presets) {
                    if (preset[0] > maxHeight) continue;
                    Button quality = dialogOptionButton(preset[0] + "p", false);
                    final int width = preset[1];
                    final int height = preset[2];
                    quality.setOnClickListener(v -> selector.setParameters(
                            selector.buildUponParameters().setMaxVideoSize(width, height)));
                    list.addView(quality);
                }
            }
        } catch (Exception ignored) {
        }

        if (!hasSection) {
            android.widget.Toast.makeText(this, getString(R.string.p_no_options), Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(list);
        final androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(scroll)
                        .setNegativeButton(getString(R.string.btn_close), null)
                        .show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    ui.stroked(0xF214141A, 16, 1, Ui.HAIRLINE));
            android.view.WindowManager.LayoutParams attributes = dialog.getWindow().getAttributes();
            attributes.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.52);
            dialog.getWindow().setAttributes(attributes);
        }
    }

    private Button dialogOptionButton(String text, boolean active) {
        Button button = new Button(this);
        button.setText(active ? "✓ " + text : text);
        button.setTextColor(android.graphics.Color.WHITE);
        button.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        button.setAllCaps(false);
        button.setTypeface(ui.font, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        final android.graphics.drawable.GradientDrawable idle =
                new android.graphics.drawable.GradientDrawable();
        idle.setColor(active ? ((ui.brand & 0x00FFFFFF) | 0x29000000) : Ui.GLASS);
        idle.setStroke(dp(1), active ? ui.brand : Ui.HAIRLINE);
        idle.setCornerRadius(dp(10));
        final android.graphics.drawable.GradientDrawable focused =
                new android.graphics.drawable.GradientDrawable();
        focused.setColor(ui.brand);
        focused.setCornerRadius(dp(10));
        button.setBackground(idle);
        button.setOnFocusChangeListener((v, hasFocus) -> {
            button.setBackground(hasFocus ? focused : idle);
            button.setTextColor(android.graphics.Color.WHITE);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(46));
        params.setMargins(0, dp(4), 0, dp(4));
        button.setLayoutParams(params);
        return button;
    }

    private String languageName(String code) {
        if (code == null || code.isEmpty() || "und".equals(code)) return getString(R.string.p_unknown);
        try {
            return new java.util.Locale(code).getDisplayLanguage(
                    new java.util.Locale(SettingsStore.getLang(this)));
        } catch (Exception ignored) {
            return code;
        }
    }

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
                androidx.media3.common.Format format = group.getTrackFormat(i);
                String labelText;
                if (format.label != null && !format.label.trim().isEmpty()) {
                    labelText = format.label.trim();
                } else {
                    labelText = getString(R.string.p_sub_for, languageName(format.language));
                }
                labels.add(labelText);
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

    private android.widget.LinearLayout settingsSectionLabel(String text, boolean spaced) {
        LinearLayout row = ui.row();
        row.setPadding(dp(4), dp(spaced ? 16 : 4), dp(4), dp(6));
        View accent = new View(this);
        accent.setBackground(ui.rounded(ui.brand, 2));
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(3), dp(16));
        accentParams.setMarginEnd(dp(8));
        row.addView(accent, accentParams);
        android.widget.TextView label = new android.widget.TextView(this);
        label.setText(text);
        label.setTextColor(Ui.TEXT);
        label.setTextSize(14);
        label.setTypeface(ui.font, android.graphics.Typeface.BOLD);
        label.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        row.addView(label);
        return row;
    }

    private void setupCustomControlIcons(PlayerView playerView) {
        if (playerView == null) return;
        View rewView = playerView.findViewById(androidx.media3.ui.R.id.exo_rew);
        if (rewView == null) rewView = playerView.findViewById(androidx.media3.ui.R.id.exo_rew_with_amount);
        if (rewView instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) rewView;
            tv.setText("");
            tv.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_rew_5, 0, 0, 0);
            tv.setPadding(dp(10), dp(10), dp(10), dp(10));
        } else if (rewView instanceof android.widget.ImageView) {
            ((android.widget.ImageView) rewView).setImageResource(R.drawable.ic_rew_5);
        } else if (rewView instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) rewView;
            group.removeAllViews();
            android.widget.ImageView img = new android.widget.ImageView(this);
            img.setImageResource(R.drawable.ic_rew_5);
            img.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            group.addView(img, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }

        View ffwdView = playerView.findViewById(androidx.media3.ui.R.id.exo_ffwd);
        if (ffwdView == null) ffwdView = playerView.findViewById(androidx.media3.ui.R.id.exo_ffwd_with_amount);
        if (ffwdView instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) ffwdView;
            tv.setText("");
            tv.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_ffwd_15, 0, 0, 0);
            tv.setPadding(dp(10), dp(10), dp(10), dp(10));
        } else if (ffwdView instanceof android.widget.ImageView) {
            ((android.widget.ImageView) ffwdView).setImageResource(R.drawable.ic_ffwd_15);
        } else if (ffwdView instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) ffwdView;
            group.removeAllViews();
            android.widget.ImageView img = new android.widget.ImageView(this);
            img.setImageResource(R.drawable.ic_ffwd_15);
            img.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            group.addView(img, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
    }

    private void stylePlayerControls(PlayerView playerView) {
        if (playerView == null) return;
        int[] buttonIds = new int[] {
            androidx.media3.ui.R.id.exo_play_pause,
            androidx.media3.ui.R.id.exo_play,
            androidx.media3.ui.R.id.exo_pause,
            androidx.media3.ui.R.id.exo_rew,
            androidx.media3.ui.R.id.exo_ffwd,
            androidx.media3.ui.R.id.exo_rew_with_amount,
            androidx.media3.ui.R.id.exo_ffwd_with_amount,
            androidx.media3.ui.R.id.exo_prev,
            androidx.media3.ui.R.id.exo_next,
            androidx.media3.ui.R.id.exo_subtitle
        };
        for (int id : buttonIds) {
            View button = playerView.findViewById(id);
            if (button != null) {
                wireSingleButton(button);
            }
        }
        androidx.media3.ui.DefaultTimeBar timeBar =
                playerView.findViewById(androidx.media3.ui.R.id.exo_progress);
        if (timeBar != null) {
            timeBar.setKeyTimeIncrement(60000); 
            timeBar.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    timeBar.setPlayedColor(ui.brand);
                    timeBar.setScrubberColor(ui.brand);
                } else {
                    timeBar.setPlayedColor(0xFFFFFFFF);
                    timeBar.setScrubberColor(0xFFFFFFFF);
                }
            });
        }
    }

    private void wireSingleButton(View button) {
        if (button == null) return;
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        clearInternalBackgrounds(button);
        
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            button.setDefaultFocusHighlightEnabled(false);
        }
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            button.setForeground(null);
        }
        
        if (button.getParent() instanceof View) {
            View wrapper = (View) button.getParent();
            if (wrapper.getId() == View.NO_ID) {
                wrapper.setBackground(null);
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    wrapper.setDefaultFocusHighlightEnabled(false);
                }
            }
        }
        paintControlBackground(button, false);
        button.setOnFocusChangeListener((v, hasFocus) -> paintControlBackground(v, hasFocus));
    }

    private void paintControlBackground(View button, boolean focused) {
        button.setBackground(controlChip(focused ? ui.brand : 0x00000000));
        androidx.core.view.ViewCompat.setBackgroundTintList(button, null);
    }

    private void clearInternalBackgrounds(View v) {
        if (v == null) return;
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                child.setBackground(null);
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    child.setForeground(null);
                }
                clearInternalBackgrounds(child);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (player != null
                && (playerView == null || !playerView.isControllerFullyVisible())) {
            
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_REWIND) {
                player.seekTo(Math.max(0, player.getCurrentPosition() - 60000));
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                long duration = player.getDuration();
                long target = player.getCurrentPosition() + 60000;
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
