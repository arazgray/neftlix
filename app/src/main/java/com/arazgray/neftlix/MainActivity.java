package com.arazgray.neftlix;

import android.content.Intent;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CatalogRepository repository = new CatalogRepository();
    private Ui ui;
    private CatalogData catalog;
    private WatchlistStore watchlistStore;
    private LinearLayout page;
    private TextView status;
    private TextView searchBox;
    private SearchPad searchPad;
    private boolean showingSubPage;
    private boolean showingWatchlist;

    private BrowseSection activeSection;
    private BrowseCategory activeCategory;
    private int listingPage = 1;
    private boolean listingHasNext;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLogger.install(this);
        ui = new Ui(this);
        watchlistStore = new WatchlistStore(new java.io.File(getFilesDir(), "watchlist.json"));
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));
        
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        
        try {
            java.io.File updatesDir = new java.io.File(getExternalFilesDir("updates"), "");
            java.io.File[] leftovers = updatesDir.listFiles();
            if (leftovers != null) {
                for (java.io.File file : leftovers) {
                    
                    file.delete();
                }
            }
        } catch (Exception ignored) {
        }
        String lastCrash = CrashLogger.consume(this);
        if (lastCrash != null) showCrashDialog(lastCrash);
        buildShell();
        java.io.File cacheFile = new java.io.File(getFilesDir(), "catalog_cache.json");
        CatalogData cached = CatalogCache.load(cacheFile);
        if (cached != null) {
            catalog = cached;
            setStatus(getString(R.string.status_cached));
            renderHome();
            refreshCatalog(true);
        } else {
            refreshCatalog(false);
        }
        
        checkAppUpdate(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        if (catalog == null) refreshCatalog();
        
        if (showingWatchlist) openWatchlist();
    }

    private void buildShell() {
        LinearLayout root = ui.column();

        LinearLayout bar = ui.row();
        bar.setPadding(ui.dp(Ui.PAGE_PAD), ui.dp(9), ui.dp(Ui.PAGE_PAD), ui.dp(5));
        
        bar.setBackgroundColor(0xB30A0A0C);
        bar.setElevation(ui.dp(16));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_transparent);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setFocusable(true);
        logo.setClickable(true);
        logo.setOnClickListener(v -> renderHome());
        LinearLayout.LayoutParams logoParams = ui.params(ui.dp(104), ui.dp(38));
        logoParams.setMarginEnd(ui.dp(14));
        bar.addView(logo, logoParams);

        searchBox = new TextView(this);
        searchBox.setSingleLine(true);
        searchBox.setEllipsize(android.text.TextUtils.TruncateAt.END);
        searchBox.setFocusable(true);
        searchBox.setFocusableInTouchMode(true);
        searchBox.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            searchBox.setDefaultFocusHighlightEnabled(false);
        }
        searchBox.setTextColor(Ui.TEXT);
        searchBox.setTypeface(ui.font, Typeface.NORMAL);
        searchBox.setHintTextColor(Ui.TEXT_MUTED);
        searchBox.setHint(getString(R.string.search_hint));
        searchBox.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        searchBox.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        searchBox.setBackground(ui.stroked(Ui.SURFACE_2, 22, 1, Ui.HAIRLINE));
        searchBox.setPadding(ui.dp(15), 0, ui.dp(15), 0);
        searchBox.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ui.icon(R.drawable.ic_search, 17), null, null, null);
        searchBox.setCompoundDrawablePadding(ui.dp(9));
        searchBox.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(
                ui.stroked(hasFocus ? 0xFF17171C : Ui.SURFACE_2, 22, 1,
                        hasFocus ? ui.brand : Ui.HAIRLINE)));
        LinearLayout.LayoutParams searchParams = ui.params(0, ui.dp(42), 1f);
        searchParams.setMarginEnd(ui.dp(10));
        bar.addView(searchBox, searchParams);
        searchPad = new SearchPad(ui, searchBox, this::showSearchResults);
        searchBox.setOnClickListener(v -> searchPad.open());
        
        searchBox.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                searchPad.open();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                searchBox.setText(SearchKeys.backspace(searchBox.getText().toString()));
                return true;
            }
            int typed = event.getUnicodeChar();
            if (typed > 0 && !Character.isISOControl(typed)) {
                searchBox.setText(SearchKeys.insert(
                        searchBox.getText().toString(), String.valueOf((char) typed)));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                View next = v.focusSearch(keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                        ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
                if (next != null && !searchPad.hosts(next)) {
                    next.requestFocus();
                    return true;
                }
            }
            return false;
        });

        TextView homeBtn = ui.ghostButton(getString(R.string.nav_home), R.drawable.ic_home);
        homeBtn.setOnClickListener(view -> renderHome());
        LinearLayout.LayoutParams homeBtnParams = ui.params(-2, ui.dp(42));
        homeBtnParams.setMarginEnd(ui.dp(8));
        bar.addView(homeBtn, homeBtnParams);

        TextView refresh = ui.ghostButton(getString(R.string.nav_catalog), R.drawable.ic_refresh);
        refresh.setOnClickListener(view -> refreshCatalog(false));
        LinearLayout.LayoutParams refreshParams = ui.params(-2, ui.dp(42));
        refreshParams.setMarginEnd(ui.dp(8));
        bar.addView(refresh, refreshParams);

        TextView update = ui.ghostButton(getString(R.string.nav_update), R.drawable.ic_download);
        update.setOnClickListener(view -> checkAppUpdate());
        LinearLayout.LayoutParams updateParams = ui.params(-2, ui.dp(42));
        updateParams.setMarginEnd(ui.dp(8));
        bar.addView(update, updateParams);

        TextView watchlist = ui.ghostButton(getString(R.string.nav_watchlist), R.drawable.ic_bookmark);
        watchlist.setOnClickListener(view -> openWatchlist());
        LinearLayout.LayoutParams watchlistParams = ui.params(-2, ui.dp(42));
        watchlistParams.setMarginEnd(ui.dp(8));
        bar.addView(watchlist, watchlistParams);

        TextView settings = ui.ghostButton(getString(R.string.nav_settings), R.drawable.ic_settings);
        settings.setOnClickListener(view -> openSettings());
        LinearLayout.LayoutParams settingsParams = ui.params(-2, ui.dp(42));
        settingsParams.setMarginEnd(ui.dp(8));
        bar.addView(settings, settingsParams);

        View info = ui.iconButton(R.drawable.ic_info, getString(R.string.about_title));
        info.setLayoutParams(ui.params(ui.dp(42), ui.dp(42)));
        info.setOnClickListener(view -> showInfoDialog());
        bar.addView(info);

        root.addView(bar, ui.params(-1, -2));

        status = ui.text("", 12, Ui.TEXT_MUTED);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        
        status.setBackground(ui.stroked(Ui.STATUS_BG, Ui.RADIUS_PILL, 1, Ui.HAIRLINE));
        status.setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(6));
        status.setVisibility(View.GONE);
        status.setElevation(ui.dp(16));
        LinearLayout.LayoutParams statusParams = ui.params(-1, -2);
        statusParams.setMargins(ui.dp(Ui.PAGE_PAD), ui.dp(4), ui.dp(Ui.PAGE_PAD), 0);
        root.addView(status, statusParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page = ui.column();
        page.setClipChildren(false);
        page.setClipToPadding(false);
        root.setClipChildren(false);
        page.setPadding(0, 0, 0, ui.dp(30));
        scroll.addView(page);
        root.addView(scroll, ui.params(-1, 0, 1f));

        FrameLayout frame = new FrameLayout(this);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        frame.setBackgroundColor(Ui.BG);
        ImageView backdrop = new ImageView(this);
        backdrop.setImageResource(R.drawable.main_bg);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.55f);
        frame.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        int keyboardWidth = getResources().getDisplayMetrics().widthPixels / 2;
        FrameLayout.LayoutParams keyboardParams = new FrameLayout.LayoutParams(
                keyboardWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        keyboardParams.bottomMargin = ui.dp(12);
        frame.addView(searchPad.view(), keyboardParams);
        frame.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (searchPad.isOpen() && !searchPad.holdsFocus() && newFocus != null
                    && newFocus != searchBox && !searchPad.hosts(newFocus)) {
                searchPad.close();
            }
        });

        loadingOverlay = centeredOverlay();
        loadingOverlay.setVisibility(View.GONE);
        frame.addView(loadingOverlay, new FrameLayout.LayoutParams(-1, -1));

        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackgroundColor(Ui.BG);
        splashOverlay.setClickable(true);
        splashOverlay.setFocusable(true);
        ImageView splashLogo = new ImageView(this);
        splashLogo.setImageResource(R.drawable.logo_transparent);
        splashLogo.setAdjustViewBounds(true);
        splashLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams splashParams =
                new FrameLayout.LayoutParams(ui.dp(300), -2, Gravity.CENTER);
        splashOverlay.addView(splashLogo, splashParams);
        frame.addView(splashOverlay, new FrameLayout.LayoutParams(-1, -1));
        splashShownAt = android.os.SystemClock.uptimeMillis();
        setContentView(frame);
    }

    private FrameLayout splashOverlay;
    private long splashShownAt;

    private void hideSplash() {
        if (splashOverlay == null || splashOverlay.getVisibility() != View.VISIBLE) return;
        
        long wait = 900 - (android.os.SystemClock.uptimeMillis() - splashShownAt);
        Runnable fade = () -> {
            if (splashOverlay == null) return;
            splashOverlay.animate().alpha(0f).setDuration(280).withEndAction(() -> {
                if (splashOverlay != null) splashOverlay.setVisibility(View.GONE);
            }).start();
        };
        if (wait > 0) splashOverlay.postDelayed(fade, wait);
        else fade.run();
    }

    private FrameLayout loadingOverlay;
    private TextView loadingLabel;

    private FrameLayout centeredOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xCC000000);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        LinearLayout box = ui.column();
        box.setGravity(Gravity.CENTER);
        LinearLayout card = ui.column();
        card.setGravity(Gravity.CENTER);
        card.setBackground(ui.stroked(Ui.SURFACE, 14, 1, Ui.HAIRLINE));
        card.setPadding(ui.dp(30), ui.dp(22), ui.dp(30), ui.dp(22));
        android.widget.ProgressBar spinner = new android.widget.ProgressBar(this);
        LinearLayout.LayoutParams spinnerParams = ui.params(ui.dp(42), ui.dp(42));
        spinnerParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(spinner, spinnerParams);
        loadingLabel = ui.text("", 13, Ui.TEXT_DIM);
        loadingLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = ui.params(-2, -2);
        labelParams.topMargin = ui.dp(12);
        labelParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(loadingLabel, labelParams);
        box.addView(card);
        overlay.addView(box, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return overlay;
    }

    private void showLoading(String text) {
        if (loadingOverlay == null) return;
        if (text != null && loadingLabel != null) loadingLabel.setText(text);
        loadingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        if (loadingOverlay == null) return;
        loadingOverlay.setVisibility(View.GONE);
        if (loadingOverlay.hasFocus()) focusFirst();
    }

    private void setStatus(String text) {
        if (status == null) return;
        status.setText(text == null ? "" : text);
        status.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void refreshCatalog() {
        refreshCatalog(false);
    }

    private void refreshCatalog(boolean quiet) {
        if (quiet) {
            setStatus(getString(R.string.status_updating_bg));
        } else {
            setStatus(getString(R.string.status_updating_site));
            showLoading(getString(R.string.loading_list));
        }
        io.execute(() -> {
            try {
                CatalogData latest = repository.loadCatalog();
                try {
                    CatalogCache.save(new java.io.File(getFilesDir(), "catalog_cache.json"), latest);
                } catch (Exception ignored) {
                }
                runOnUiThread(() -> {
                    catalog = latest;
                    hideLoading();
                    if (quiet) {
                        setStatus(getString(R.string.status_updated, ui.digits(countItems(latest))));
                        if (!showingSubPage && page.getChildCount() == 0) renderHome();
                        return;
                    }
                    showingSubPage = false;
                    activeSection = null;
                    activeCategory = null;
                    setStatus(getString(R.string.status_updated_pick, ui.digits(countItems(latest))));
                    renderHome();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    hideLoading();
                    setStatus(getString(R.string.status_update_failed));
                    if (catalog == null) {
                        hideSplash();
                        page.removeAllViews();
                        addMessage(getString(R.string.catalog_unavailable), error.getMessage());
                    } else {
                        Toast.makeText(this, getString(R.string.toast_stale), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/arazgray/neftlix/main/version.json";

    private void checkAppUpdate() {
        checkAppUpdate(false);
    }

    private void checkAppUpdate(boolean silent) {
        if (!silent) setStatus(getString(R.string.status_checking));
        io.execute(() -> {
            try {
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new java.net.URL(VERSION_URL).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Accept", "application/json");
                java.util.Scanner scanner =
                        new java.util.Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                String body = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                org.json.JSONObject info = new org.json.JSONObject(body);
                int latest = info.optInt("versionCode", BuildConfig.VERSION_CODE);
                String apkUrl = info.optString("apkUrl", "");
                String versionName = info.optString("versionName", "");
                String notes = info.optString("notes", "");
                if (latest > BuildConfig.VERSION_CODE && !apkUrl.isEmpty()) {
                    runOnUiThread(() -> showUpdateDialog(versionName, notes, apkUrl));
                    runOnUiThread(() -> setStatus(getString(R.string.status_update_found)));
                } else if (!silent) {
                    runOnUiThread(() -> {
                        setStatus(getString(R.string.status_up_to_date));
                        Toast.makeText(this, getString(R.string.toast_no_update), Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Throwable e) {
                if (silent) return;
                runOnUiThread(() -> {
                    setStatus(getString(R.string.status_check_failed));
                    Toast.makeText(this, getString(R.string.toast_check_error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showUpdateDialog(String versionName, String notes, String apkUrl) {
        String title = getString(R.string.update_new) + (versionName.isEmpty() ? "" : " " + versionName);
        String message = notes.isEmpty() ? getString(R.string.update_confirm) : notes + "\n\n" + getString(R.string.update_confirm);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.btn_download_install), (dialog, which) -> downloadAndInstall(apkUrl))
                .setNegativeButton(getString(R.string.btn_later), null)
                .show();
    }

    private void showCrashDialog(String trace) {
        ScrollView scroll = new ScrollView(this);
        TextView traceView = new TextView(this);
        traceView.setText(trace);
        traceView.setTextColor(Ui.TEXT);
        traceView.setTextSize(11);
        traceView.setTextDirection(View.TEXT_DIRECTION_LTR);
        traceView.setGravity(Gravity.START);
        traceView.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        scroll.addView(traceView);
        androidx.appcompat.app.AlertDialog crashDialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.crash_title))
                .setMessage(getString(R.string.crash_msg))
                .setView(scroll)
                .setPositiveButton(getString(R.string.crash_copy), (d, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("crash", trace));
                        Toast.makeText(this, getString(R.string.crash_copied), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.btn_close), null)
                .show();
        if (crashDialog.getWindow() != null) {
            crashDialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK));
        }
    }

    private void showInfoDialog() {
        LinearLayout layout = ui.column();
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(ui.dp(24), ui.dp(18), ui.dp(24), ui.dp(16));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_transparent);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = ui.params(ui.dp(130), ui.dp(44));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        layout.addView(logo, logoParams);

        TextView desc = ui.text(getString(R.string.info_desc),
                13, Ui.TEXT_DIM);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, ui.dp(12), 0, 0);
        layout.addView(desc);

        TextView source = ui.text(getString(R.string.info_source),
                12, Ui.TEXT_MUTED);
        source.setGravity(Gravity.CENTER);
        source.setPadding(0, ui.dp(8), 0, 0);
        layout.addView(source);

        TextView version = ui.text(getString(R.string.info_version, BuildConfig.VERSION_NAME, ui.digits(BuildConfig.VERSION_CODE)),
                12, Ui.TEXT_MUTED);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, ui.dp(10), 0, 0);
        layout.addView(version);

        TextView author = ui.text(getString(R.string.info_maker), 12, Ui.TEXT_MUTED);
        author.setGravity(Gravity.CENTER);
        author.setPadding(0, ui.dp(4), 0, 0);
        layout.addView(author);

        android.graphics.Bitmap qr = makeQrBitmap("https://arazgray.com", 420);
        if (qr != null) {
            ImageView qrView = new ImageView(this);
            qrView.setImageBitmap(qr);
            qrView.setBackground(ui.stroked(android.graphics.Color.WHITE, 8, 0, 0));
            qrView.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
            LinearLayout.LayoutParams qrParams = ui.params(ui.dp(150), ui.dp(150));
            qrParams.gravity = Gravity.CENTER_HORIZONTAL;
            qrParams.topMargin = ui.dp(8);
            layout.addView(qrView, qrParams);
        }

        LinearLayout buttons = ui.row();
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, ui.dp(12), 0, 0);
        TextView close = infoDialogButton(getString(R.string.btn_close));
        TextView site = infoDialogButton(getString(R.string.btn_open_site));
        LinearLayout.LayoutParams closeParams = ui.params(-2, ui.dp(40));
        closeParams.setMarginEnd(ui.dp(14));
        buttons.addView(close, closeParams);
        buttons.addView(site, ui.params(-2, ui.dp(40)));
        layout.addView(buttons);

        final androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(layout)
                        .show();
        close.setOnClickListener(v -> dialog.dismiss());
        site.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://arazgray.com")));
            } catch (Exception ignored) {
            }
        });
        close.requestFocus();
        if (dialog.getWindow() != null) {
            
            dialog.getWindow().setBackgroundDrawable(
                    ui.stroked(0xCC000000, 16, 1, ui.brand));
            android.view.WindowManager.LayoutParams attributes = dialog.getWindow().getAttributes();
            attributes.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.45);
            dialog.getWindow().setAttributes(attributes);
        }
    }

    private TextView infoDialogButton(String label) {
        TextView button = ui.text(label, 14, 0xFF0A0A0C, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setPadding(ui.dp(26), 0, ui.dp(26), 0);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) button.setDefaultFocusHighlightEnabled(false);
        final android.graphics.drawable.GradientDrawable idle = ui.rounded(Ui.TEXT, 20);
        final android.graphics.drawable.GradientDrawable focused = ui.rounded(ui.brand, 20);
        button.setBackground(idle);
        button.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(hasFocus ? focused : idle);
            button.setTextColor(hasFocus ? Ui.TEXT : 0xFF0A0A0C);
        });
        return button;
    }

    private android.graphics.Bitmap makeQrBitmap(String text, int size) {
        try {
            com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix matrix = writer.encode(
                    text, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    size, size, android.graphics.Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y)
                            ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                }
            }
            return bitmap;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void downloadAndInstall(String apkUrl) {
        android.widget.ProgressBar progress = new android.widget.ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        LinearLayout layout = ui.column();
        layout.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
        final TextView label = ui.text(getString(R.string.downloading_pct, ui.digits(0)), 13, Ui.TEXT);
        layout.addView(label, ui.params(-1, -2));
        layout.addView(progress, ui.params(-1, ui.dp(24)));
        final boolean[] cancelled = {false};
        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dlg_downloading))
                        .setView(layout)
                        .setNegativeButton(getString(R.string.btn_cancel), (d, which) -> cancelled[0] = true)
                        .setCancelable(false)
                        .show();
        io.execute(() -> {
            try {
                java.io.File dir = new java.io.File(getExternalFilesDir("updates"), "");
                if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("no dir");
                java.io.File apk = new java.io.File(dir, "neftlix-update.apk");
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new java.net.URL(apkUrl).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("Accept", "application/vnd.android.package-archive");
                long total = conn.getContentLengthLong();
                try (java.io.InputStream in = conn.getInputStream();
                     java.io.FileOutputStream out = new java.io.FileOutputStream(apk)) {
                    byte[] buffer = new byte[65536];
                    long done = 0;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (cancelled[0]) {
                            
                            apk.delete();
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                setStatus(getString(R.string.status_dl_canceled));
                            });
                            return;
                        }
                        out.write(buffer, 0, read);
                        done += read;
                        if (total > 0) {
                            final int percent = (int) (done * 100 / total);
                            runOnUiThread(() -> {
                                progress.setProgress(percent);
                                label.setText(getString(R.string.downloading_pct, ui.digits(percent)));
                            });
                        }
                    }
                }
                runOnUiThread(() -> {
                    dialog.dismiss();
                    setStatus(getString(R.string.status_dl_done));
                    installApk(apk);
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    if (!cancelled[0]) dialog.dismiss();
                    setStatus(getString(R.string.status_dl_failed));
                    Toast.makeText(this, getString(R.string.toast_dl_error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void installApk(java.io.File apk) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26
                    && !getPackageManager().canRequestPackageInstalls()) {
                startActivity(new Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:" + getPackageName())));
                Toast.makeText(this,
                        getString(R.string.toast_allow_unknown),
                        Toast.LENGTH_LONG).show();
                return;
            }
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Throwable e) {
            Toast.makeText(this, getString(R.string.toast_install_failed), Toast.LENGTH_LONG).show();
        }
    }

    private void renderHome() {
        hideSplash();
        page.removeAllViews();
        showingSubPage = false;
        showingWatchlist = false;
        activeSection = null;
        activeCategory = null;
        if (catalog == null) {
            addMessage(getString(R.string.loading_catalog), getString(R.string.loading_wait));
            return;
        }

        for (BrowseSection section : catalog.sections) {
            addSectionRail(section);
        }

        if (!catalog.movieCategories.isEmpty()) {
            page.addView(ui.sectionHeader(getString(R.string.movie_genres),
                    getString(R.string.genres_n, ui.digits(catalog.movieCategories.size())), null));
            page.addView(ui.rail(chipViews(catalog.movieCategories)));
        }
        if (!catalog.seriesCategories.isEmpty()) {
            page.addView(ui.sectionHeader(getString(R.string.series_genres),
                    getString(R.string.genres_n, ui.digits(catalog.seriesCategories.size())), null));
            page.addView(ui.rail(chipViews(catalog.seriesCategories)));
        }
        focusFirst();
    }

    private void addSectionRail(BrowseSection section) {
        List<View> cards = new ArrayList<>();
        int limit = Math.min(section.items.size(), 20);
        for (int i = 0; i < limit; i++) {
            final BrowseItem item = section.items.get(i);
            View card = ui.posterCard(item, Ui.CARD_W, true);
            card.setOnClickListener(v -> openItem(item));
            cards.add(card);
        }
        if (cards.isEmpty()) {
            page.addView(ui.sectionHeader(section.title, null, null));
            page.addView(note(getString(R.string.no_items)));
            return;
        }
        TextView more = null;
        if (section.url != null && !section.url.isEmpty()) {
            more = ui.ghostButton(getString(R.string.see_all), R.drawable.ic_chevron_end);
            more.setLayoutParams(ui.params(-2, ui.dp(34)));
            more.setOnClickListener(v -> openSection(section, 1));
        }
        page.addView(ui.sectionHeader(section.title, getString(R.string.titles_n, ui.digits(section.items.size())), more));
        page.addView(ui.rail(cards));
    }

    private List<View> chipViews(List<BrowseCategory> categories) {
        List<View> chips = new ArrayList<>();
        for (final BrowseCategory category : categories) {
            TextView chip = ui.chip(category.title, getString(R.string.genres_n, ui.digits(category.count)));
            chip.setOnClickListener(view -> openCategory(category, 1));
            chips.add(chip);
        }
        return chips;
    }

    private void openItem(BrowseItem item) {
        Intent intent = new Intent(this, DetailsActivity.class);
        intent.putExtra(DetailsActivity.EXTRA_URL, item.url);
        intent.putExtra(DetailsActivity.EXTRA_TITLE, item.title);
        startActivity(intent);
    }

    private void showSearchResults() {
        if (catalog == null) return;
        final String query = searchBox.getText().toString().trim();
        if (searchPad != null) searchPad.close();
        searchBox.clearFocus();
        showingSubPage = true;
        showingWatchlist = false;
        activeSection = null;
        activeCategory = null;
        if (query.isEmpty()) {
            page.removeAllViews();
            addBackButton();
            addHeading(getString(R.string.all_titles), getString(R.string.titles_n, ui.digits(uniqueItems(catalog.sections).size())));
            addItemsGrid(uniqueItems(catalog.sections));
            focusFirst();
            return;
        }
        page.removeAllViews();
        addBackButton();
        addHeading(getString(R.string.search_results), query);
        showLoading(getString(R.string.loading_searching));
        io.execute(() -> {
            List<BrowseItem> found = new ArrayList<>();
            try {
                found = repository.quickSearch(query);
                if (found.isEmpty()) {
                    found = repository.search(query);
                }
            } catch (Throwable e) {
                try {
                    found = repository.search(query);
                } catch (Throwable ignored) {
                    found = new ArrayList<>();
                }
            }
            final List<BrowseItem> direct = found.isEmpty() ? filterLocal(query) : found;
            final String source = found.isEmpty() ? getString(R.string.source_catalog) : getString(R.string.source_site);
            runOnUiThread(() -> {
                hideLoading();
                page.removeAllViews();
                addBackButton();
                addHeading(getString(R.string.search_results), query);
                setStatus(getString(R.string.results_from, ui.digits(direct.size()), source));
                addItemsGrid(direct);
                focusFirst();
            });
        });
    }

    private List<BrowseItem> filterLocal(String query) {
        String q = query.trim();
        List<BrowseItem> out = new ArrayList<>();
        for (BrowseItem item : uniqueItems(catalog.sections)) {
            String text = item.title + " " + item.pageTitle;
            if (text.contains(q) || text.toLowerCase(java.util.Locale.ROOT).contains(q.toLowerCase(java.util.Locale.ROOT))) {
                out.add(item);
            }
        }
        return out;
    }

    private void openSection(BrowseSection section, int pageNum) {
        activeSection = section;
        activeCategory = null;
        listingPage = pageNum;
        showingSubPage = true;
        showingWatchlist = false;
        showLoading(getString(R.string.loading_section, section.title));
        io.execute(() -> {
            try {
                ListingPage result = repository.loadListingPage(section.url, section.contentType, pageNum);
                runOnUiThread(() -> {
                    hideLoading();
                    listingHasNext = result.hasNext;
                    setStatus(section.title + " · " + getString(R.string.page_n, ui.digits(pageNum)));
                    page.removeAllViews();
                    addBackButton();
                    addHeading(section.title, getString(R.string.page_n, ui.digits(pageNum)));
                    addItemsGrid(result.items);
                    addPaginationBar();
                    focusFirst();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    hideLoading();
                    setStatus(getString(R.string.d_err_load, section.title));
                });
            }
        });
    }

    private void openCategory(BrowseCategory category, int pageNum) {
        activeCategory = category;
        activeSection = null;
        listingPage = pageNum;
        showingSubPage = true;
        showingWatchlist = false;
        showLoading(getString(R.string.loading_section, category.title));
        io.execute(() -> {
            try {
                ListingPage result = repository.loadListingPage(category.url, category.contentType, pageNum);
                runOnUiThread(() -> {
                    hideLoading();
                    listingHasNext = result.hasNext;
                    setStatus(category.title + " · " + getString(R.string.page_n, ui.digits(pageNum)));
                    page.removeAllViews();
                    addBackButton();
                    String count = category.count == null || category.count.isEmpty()
                            ? "" : getString(R.string.titles_n, ui.digits(category.count));
                    addHeading(category.title, count);
                    addItemsGrid(result.items);
                    addPaginationBar();
                    focusFirst();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    hideLoading();
                    setStatus(getString(R.string.d_err_load, category.title));
                });
            }
        });
    }

    private void openWatchlist() {
        showingSubPage = true;
        showingWatchlist = true;
        activeSection = null;
        activeCategory = null;
        List<BrowseItem> saved = watchlistStore.items();
        page.removeAllViews();
        addBackButton();
        addHeading(getString(R.string.nav_watchlist), getString(R.string.titles_n, ui.digits(saved.size())));
        setStatus(getString(R.string.watchlist_n, ui.digits(saved.size())));
        if (saved.isEmpty()) {
            page.addView(note(getString(R.string.watchlist_empty)));
        } else {
            addItemsGrid(saved, true);
        }
        focusFirst();
    }

    private void openSettings() {
        showingSubPage = true;
        showingWatchlist = false;
        activeSection = null;
        activeCategory = null;
        page.removeAllViews();
        addBackButton();
        addHeading(getString(R.string.nav_settings), null);
        setStatus(getString(R.string.settings_sub));
        page.addView(ui.sectionHeader(getString(R.string.set_language), null, null));
        page.addView(languageRow());
        page.addView(ui.sectionHeader(getString(R.string.set_accent), null, null));
        page.addView(accentRow());
        focusFirst();
    }

    private LinearLayout languageRow() {
        LinearLayout row = ui.row();
        row.setPadding(ui.dp(Ui.PAGE_PAD), ui.dp(4), ui.dp(Ui.PAGE_PAD), 0);
        boolean persian = SettingsStore.isPersian(this);
        TextView fa = persian
                ? ui.primaryButton(getString(R.string.lang_fa), 0)
                : ui.ghostButton(getString(R.string.lang_fa), 0);
        TextView en = !persian
                ? ui.primaryButton(getString(R.string.lang_en), 0)
                : ui.ghostButton(getString(R.string.lang_en), 0);
        fa.setOnClickListener(v -> {
            SettingsStore.setLang(this, SettingsStore.LANG_FA);
            recreate();
        });
        en.setOnClickListener(v -> {
            SettingsStore.setLang(this, SettingsStore.LANG_EN);
            recreate();
        });
        LinearLayout.LayoutParams faParams = ui.params(-2, ui.dp(44));
        faParams.setMarginEnd(ui.dp(10));
        row.addView(fa, faParams);
        row.addView(en, ui.params(-2, ui.dp(44)));
        return row;
    }

    private LinearLayout accentRow() {
        LinearLayout row = ui.row();
        row.setPadding(ui.dp(Ui.PAGE_PAD), ui.dp(4), ui.dp(Ui.PAGE_PAD), 0);
        int current = SettingsStore.getAccentIndex(this);
        int[] names = {
                R.string.accent_red, R.string.accent_blue, R.string.accent_green,
                R.string.accent_purple, R.string.accent_orange,
        };
        for (int i = 0; i < SettingsStore.ACCENTS.length; i++) {
            final int index = i;
            TextView swatch = accentSwatch(SettingsStore.ACCENTS[i],
                    getString(names[i]), index == current);
            swatch.setOnClickListener(v -> {
                SettingsStore.setAccentIndex(this, index);
                recreate();
            });
            LinearLayout.LayoutParams params = ui.params(0, ui.dp(48), 1f);
            if (i < SettingsStore.ACCENTS.length - 1) params.setMarginEnd(ui.dp(10));
            row.addView(swatch, params);
        }
        return row;
    }

    private TextView accentSwatch(int color, String name, boolean selected) {
        TextView view = ui.text(selected ? "✓ " + name : name, 13, Ui.TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) view.setDefaultFocusHighlightEnabled(false);
        final android.graphics.drawable.GradientDrawable idle = ui.rounded(color, 12);
        final android.graphics.drawable.GradientDrawable focused = ui.stroked(color, 12, 3, 0xFFFFFFFF);
        view.setBackground(idle);
        view.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(hasFocus ? focused : idle));
        return view;
    }

    private void confirmRemoveFromWatchlist(final BrowseItem item) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(getString(R.string.confirm_remove))
                .setPositiveButton(getString(R.string.btn_remove), (dialog, which) -> {
                    watchlistStore.remove(item.url);
                    openWatchlist();
                })
                .setNegativeButton(getString(R.string.btn_dismiss), null)
                .show();
    }

    private void addItemsGrid(List<BrowseItem> items) {
        addItemsGrid(items, false);
    }

    private void addItemsGrid(List<BrowseItem> items, boolean deletable) {
        if (items == null || items.isEmpty()) {
            page.addView(note(getString(R.string.no_items)));
            return;
        }
        int columns = ui.gridColumns();
        float cardWidth = ui.gridCardWidthDp(columns);
        LinearLayout grid = ui.column();
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(ui.dp(Ui.PAGE_PAD), ui.dp(16), ui.dp(Ui.PAGE_PAD), 0);
        List<View> rowCards = new ArrayList<>();
        for (final BrowseItem item : items) {
            View card = ui.posterCard(item, cardWidth, true);
            card.setOnClickListener(v -> openItem(item));
            if (deletable) {
                card.setOnLongClickListener(v -> {
                    confirmRemoveFromWatchlist(item);
                    return true;
                });
            }
            rowCards.add(card);
            if (rowCards.size() == columns) {
                grid.addView(ui.gridRow(rowCards), bottomMargin());
                rowCards = new ArrayList<>();
            }
        }
        if (!rowCards.isEmpty()) {
            
            padRow(rowCards, columns);
            grid.addView(ui.gridRow(rowCards), bottomMargin());
        }
        page.addView(grid);
    }

    private LinearLayout.LayoutParams bottomMargin() {
        LinearLayout.LayoutParams params = ui.params(-1, -2);
        params.bottomMargin = ui.dp(22);
        return params;
    }

    private void padRow(List<View> cells, int columns) {
        while (cells.size() < columns) cells.add(new View(this));
    }

    private void addPaginationBar() {
        LinearLayout bar = ui.row();
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(0, ui.dp(6), 0, ui.dp(10));

        if (listingPage > 1) {
            TextView prev = ui.ghostButton(getString(R.string.prev_page), R.drawable.ic_back);
            prev.setOnClickListener(v -> {
                if (activeSection != null) openSection(activeSection, listingPage - 1);
                else if (activeCategory != null) openCategory(activeCategory, listingPage - 1);
            });
            bar.addView(prev, ui.params(-2, ui.dp(42)));
        }
        TextView num = ui.text(getString(R.string.page_n, ui.digits(listingPage)), 14, Ui.TEXT, true);
        num.setPadding(ui.dp(18), 0, ui.dp(18), 0);
        bar.addView(num);
        if (listingHasNext) {
            TextView next = ui.ghostButton(getString(R.string.next_page), R.drawable.ic_chevron_end);
            next.setOnClickListener(v -> {
                if (activeSection != null) openSection(activeSection, listingPage + 1);
                else if (activeCategory != null) openCategory(activeCategory, listingPage + 1);
            });
            bar.addView(next, ui.params(-2, ui.dp(42)));
        }
        page.addView(bar);
    }

    private void addBackButton() {
        LinearLayout bar = ui.row();
        bar.setPadding(ui.dp(Ui.PAGE_PAD), ui.dp(4), ui.dp(Ui.PAGE_PAD), 0);

        TextView back = ui.ghostButton(getString(R.string.back), R.drawable.ic_back);
        back.setTag("back");
        back.setOnClickListener(view -> renderHome());
        bar.addView(back, ui.params(-2, ui.dp(42)));

        page.addView(bar);
    }

    private void addHeading(String text, String countText) {
        LinearLayout header = ui.row();
        header.setPadding(ui.dp(Ui.PAGE_PAD), ui.dp(16), ui.dp(Ui.PAGE_PAD), ui.dp(6));
        View accent = new View(this);
        accent.setBackground(ui.rounded(ui.brand, 2));
        LinearLayout.LayoutParams accentParams = ui.params(ui.dp(3), ui.dp(18));
        accentParams.setMarginEnd(ui.dp(9));
        header.addView(accent, accentParams);
        header.addView(ui.heading(text));
        if (countText != null && !countText.isEmpty()) {
            TextView count = ui.dim(countText, 12);
            LinearLayout.LayoutParams countParams = ui.params(-2, -2);
            countParams.setMarginStart(ui.dp(8));
            header.addView(count, countParams);
        }
        page.addView(header);
    }

    private TextView note(String text) {
        TextView view = ui.dim(text, 14);
        view.setPadding(ui.dp(Ui.PAGE_PAD), ui.dp(4), ui.dp(Ui.PAGE_PAD), ui.dp(12));
        return view;
    }

    private void addMessage(String title, String message) {
        addHeading(title, null);
        page.addView(note(message == null ? "" : message));
    }

    private void focusFirst() {
        page.post(() -> {
            View first = findFirstFocusable(page);
            if (first != null) first.requestFocus();
        });
    }

    private View findFirstFocusable(View root) {
        if ("back".equals(root.getTag())) {
            
        } else if (root.isFocusable()) {
            return root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findFirstFocusable(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private List<BrowseItem> uniqueItems(List<BrowseSection> sections) {
        Map<String, BrowseItem> unique = new LinkedHashMap<>();
        for (BrowseSection section : sections) {
            for (BrowseItem item : section.items) unique.putIfAbsent(item.url, item);
        }
        return new ArrayList<>(unique.values());
    }

    private int countItems(CatalogData data) {
        return uniqueItems(data.sections).size();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (searchPad != null && searchPad.offerKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (searchPad != null && searchPad.isOpen()) {
            searchPad.close();
            searchBox.requestFocus();
            return;
        }
        if (showingSubPage) {
            renderHome();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
