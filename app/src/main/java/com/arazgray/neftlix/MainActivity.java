package com.arazgray.neftlix;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
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

/**
 * صفحهٔ اصلی: راست‌چین، فارسی و مناسب ریموت‌کنترل تلویزیون.
 *
 * ساختار: نوار بالا (لوگو، جست‌وجو، کاتالوگ، به‌روزرسانی، درباره) → بیلبورد ویژه →
 * ریل‌های افقی فیلم و سریال → دسته‌بندی ژانرها. هر بار که باز می‌شود فهرست از
 * سایت تازه می‌شود و تا وقتی پاسخ بیاید، فهرست ذخیره‌شدهٔ روی دستگاه نشان
 * داده می‌شود.
 */
public class MainActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CatalogRepository repository = new CatalogRepository();
    private Ui ui;
    private CatalogData catalog;
    private LinearLayout page;
    private TextView status;
    private EditText searchBox;
    private boolean showingSubPage;

    private BrowseSection activeSection;
    private BrowseCategory activeCategory;
    private int listingPage = 1;
    private boolean listingHasNext;

    /** اولین کارت ریل‌ها؛ دکمهٔ «مرور کاتالوگ» بیلبورد فوکوس را به آن می‌دهد. */
    private View firstCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLogger.install(this);
        ui = new Ui(this);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        // پاک‌سازی فایل به‌روزرسانی قبلی (بعد از نصب دیگر لازم نیست)
        try {
            java.io.File updatesDir = new java.io.File(getExternalFilesDir("updates"), "");
            java.io.File[] leftovers = updatesDir.listFiles();
            if (leftovers != null) {
                for (java.io.File file : leftovers) {
                    //noinspection ResultOfMethodCallIgnored
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
            status.setText("نمایش فهرست ذخیره‌شده…");
            renderHome();
            refreshCatalog(true);
        } else {
            refreshCatalog(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // فهرست تازه در onCreate گرفته می‌شود؛ اینجا فقط اگر چیزی نداریم تلاش کن
        if (catalog == null) refreshCatalog();
    }

    // ── پوستهٔ صفحه ──────────────────────────────────────────────────

    private void buildShell() {
        LinearLayout root = ui.column();

        // نوار بالا
        LinearLayout bar = ui.row();
        bar.setPadding(ui.dp(Ui.PAGE_PAD), ui.dp(9), ui.dp(Ui.PAGE_PAD), ui.dp(5));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = ui.params(ui.dp(104), ui.dp(38));
        logoParams.setMarginEnd(ui.dp(14));
        bar.addView(logo, logoParams);

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextColor(Ui.TEXT);
        searchBox.setHintTextColor(Ui.TEXT_MUTED);
        searchBox.setHint("جست‌وجوی فیلم و سریال…");
        searchBox.setTextDirection(View.TEXT_DIRECTION_RTL);
        searchBox.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        searchBox.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setBackground(ui.stroked(Ui.SURFACE_2, 22, 1, Ui.HAIRLINE));
        searchBox.setPadding(ui.dp(15), 0, ui.dp(15), 0);
        searchBox.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ui.icon(R.drawable.ic_search, 17), null, null, null);
        searchBox.setCompoundDrawablePadding(ui.dp(9));
        searchBox.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(
                ui.stroked(hasFocus ? 0xFF17171C : Ui.SURFACE_2, 22, 1,
                        hasFocus ? Ui.BRAND : Ui.HAIRLINE)));
        LinearLayout.LayoutParams searchParams = ui.params(0, ui.dp(42), 1f);
        searchParams.setMarginEnd(ui.dp(10));
        bar.addView(searchBox, searchParams);
        searchBox.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                showSearchResults();
                return true;
            }
            return false;
        });
        // با ریموت از داخل متن خارج شو: چپ/راست به‌جای حرکت مکان‌نما، فوکوس را جابه‌جا می‌کند
        searchBox.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) {
                View next = v.focusSearch(keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                        ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
                if (next != null) {
                    next.requestFocus();
                    return true;
                }
            }
            return false;
        });

        TextView refresh = ui.ghostButton("کاتالوگ", R.drawable.ic_refresh);
        refresh.setOnClickListener(view -> refreshCatalog(false));
        LinearLayout.LayoutParams refreshParams = ui.params(-2, ui.dp(42));
        refreshParams.setMarginEnd(ui.dp(8));
        bar.addView(refresh, refreshParams);

        TextView update = ui.ghostButton("به‌روزرسانی", R.drawable.ic_download);
        update.setOnClickListener(view -> checkAppUpdate());
        LinearLayout.LayoutParams updateParams = ui.params(-2, ui.dp(42));
        updateParams.setMarginEnd(ui.dp(8));
        bar.addView(update, updateParams);

        TextView info = ui.iconButton(R.drawable.ic_info, "درباره");
        info.setLayoutParams(ui.params(ui.dp(42), ui.dp(42)));
        info.setOnClickListener(view -> showInfoDialog());
        bar.addView(info);

        root.addView(bar, ui.params(-1, -2));

        status = ui.text("", 12, Ui.TEXT_MUTED);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setPadding(ui.dp(Ui.PAGE_PAD), 0, ui.dp(Ui.PAGE_PAD), ui.dp(2));
        root.addView(status, ui.params(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page = ui.column();
        page.setClipChildren(false);
        page.setPadding(0, 0, 0, ui.dp(30));
        scroll.addView(page);
        root.addView(scroll, ui.params(-1, 0, 1f));

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.BG);
        ImageView backdrop = new ImageView(this);
        backdrop.setImageResource(R.drawable.main_bg);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.55f);
        frame.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        loadingOverlay = centeredOverlay();
        loadingOverlay.setVisibility(View.GONE);
        frame.addView(loadingOverlay, new FrameLayout.LayoutParams(-1, -1));

        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackgroundColor(Ui.BG);
        splashOverlay.setClickable(true);
        splashOverlay.setFocusable(true);
        ImageView splashLogo = new ImageView(this);
        splashLogo.setImageResource(R.drawable.app_logo);
        splashLogo.setAdjustViewBounds(true);
        splashLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams splashParams =
                new FrameLayout.LayoutParams(ui.dp(300), -2, Gravity.CENTER);
        splashOverlay.addView(splashLogo, splashParams);
        frame.addView(splashOverlay, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
    }

    private FrameLayout splashOverlay;

    private void hideSplash() {
        if (splashOverlay == null || splashOverlay.getVisibility() != View.VISIBLE) return;
        splashOverlay.animate().alpha(0f).setDuration(280).withEndAction(() -> {
            if (splashOverlay != null) splashOverlay.setVisibility(View.GONE);
        }).start();
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

    // ── فهرست و به‌روزرسانی ─────────────────────────────────────────

    private void refreshCatalog() {
        refreshCatalog(false);
    }

    private void refreshCatalog(boolean quiet) {
        if (quiet) {
            status.setText("در حال به‌روزرسانی پس‌زمینه…");
        } else {
            status.setText("در حال به‌روزرسانی فهرست از سایت…");
            showLoading("در حال به‌روزرسانی فهرست…");
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
                        status.setText("فهرست به‌روز شد · " + Ui.fa(countItems(latest)) + " عنوان");
                        if (!showingSubPage && page.getChildCount() == 0) renderHome();
                        return;
                    }
                    showingSubPage = false;
                    activeSection = null;
                    activeCategory = null;
                    status.setText("فهرست به‌روز شد · " + Ui.fa(countItems(latest)) + " عنوان · با ریموت انتخاب کنید");
                    renderHome();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    hideLoading();
                    status.setText("به‌روزرسانی ناموفق بود. اینترنت را بررسی کنید و دوباره تلاش کنید.");
                    if (catalog == null) {
                        hideSplash();
                        page.removeAllViews();
                        addMessage("فهرست در دسترس نیست", error.getMessage());
                    } else {
                        Toast.makeText(this, "به‌روزرسانی ناموفق بود؛ آخرین فهرست نمایش داده شد", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/arazgray/neftlix/main/version.json";

    private void checkAppUpdate() {
        status.setText("در حال بررسی نسخه جدید…");
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
                    runOnUiThread(() -> status.setText("نسخه جدید پیدا شد"));
                } else {
                    runOnUiThread(() -> {
                        status.setText("اپ به‌روز است");
                        Toast.makeText(this, "نسخه جدیدی نیست", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    status.setText("بررسی نسخه ناموفق بود");
                    Toast.makeText(this, "خطا در بررسی به‌روزرسانی", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showUpdateDialog(String versionName, String notes, String apkUrl) {
        String title = "نسخه جدید" + (versionName.isEmpty() ? "" : " " + versionName);
        String message = notes.isEmpty() ? "دانلود و نصب شود؟" : notes + "\n\nدانلود و نصب شود؟";
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("دانلود و نصب", (dialog, which) -> downloadAndInstall(apkUrl))
                .setNegativeButton("بعداً", null)
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
                .setTitle("خطای اجرای قبلی")
                .setMessage("برنامه دفعه قبل به‌طور غیرمنتظره بسته شد. متن خطا:")
                .setView(scroll)
                .setPositiveButton("کپی متن خطا", (d, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("crash", trace));
                        Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
        if (crashDialog.getWindow() != null) {
            crashDialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK));
        }
    }

    private void showInfoDialog() {
        LinearLayout layout = ui.column();
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(6));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = ui.params(ui.dp(170), ui.dp(58));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        layout.addView(logo, logoParams);

        TextView desc = ui.text("اپ تلویزیون فارسی برای مرور فیلم‌ها و سریال‌ها، جست‌وجوی سراسری آرشیو و پخش مستقیم با پخش‌کننده داخلی.",
                13, Ui.TEXT_DIM);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, ui.dp(12), 0, 0);
        layout.addView(desc);

        TextView version = ui.text("نسخه " + BuildConfig.VERSION_NAME + " (" + Ui.fa(BuildConfig.VERSION_CODE) + ")",
                12, Ui.TEXT_MUTED);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, ui.dp(10), 0, 0);
        layout.addView(version);

        TextView author = ui.text("سازنده: Araz Gray · arazgray.com", 12, Ui.TEXT_MUTED);
        author.setGravity(Gravity.CENTER);
        author.setPadding(0, ui.dp(4), 0, ui.dp(10));
        layout.addView(author);

        android.graphics.Bitmap qr = makeQrBitmap("https://arazgray.com", 420);
        if (qr != null) {
            ImageView qrView = new ImageView(this);
            qrView.setImageBitmap(qr);
            qrView.setBackground(ui.stroked(android.graphics.Color.WHITE, 8, 0, 0));
            qrView.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
            LinearLayout.LayoutParams qrParams = ui.params(ui.dp(190), ui.dp(190));
            qrParams.gravity = Gravity.CENTER_HORIZONTAL;
            layout.addView(qrView, qrParams);
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(layout)
                .setPositiveButton("بستن", null)
                .setNeutralButton("باز کردن سایت", (d, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://arazgray.com")));
                    } catch (Exception ignored) {
                    }
                })
                .show()
                .getWindow()
                .setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));
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
        final TextView label = ui.text("در حال دانلود… ٪۰", 13, Ui.TEXT);
        layout.addView(label, ui.params(-1, -2));
        layout.addView(progress, ui.params(-1, ui.dp(24)));
        final boolean[] cancelled = {false};
        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("دانلود به‌روزرسانی")
                        .setView(layout)
                        .setNegativeButton("لغو", (d, which) -> cancelled[0] = true)
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
                            //noinspection ResultOfMethodCallIgnored
                            apk.delete();
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                status.setText("دانلود لغو شد");
                            });
                            return;
                        }
                        out.write(buffer, 0, read);
                        done += read;
                        if (total > 0) {
                            final int percent = (int) (done * 100 / total);
                            runOnUiThread(() -> {
                                progress.setProgress(percent);
                                label.setText("در حال دانلود… ٪" + Ui.fa(percent));
                            });
                        }
                    }
                }
                runOnUiThread(() -> {
                    dialog.dismiss();
                    status.setText("دانلود تمام شد");
                    installApk(apk);
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    if (!cancelled[0]) dialog.dismiss();
                    status.setText("دانلود ناموفق بود");
                    Toast.makeText(this, "خطا در دانلود به‌روزرسانی", Toast.LENGTH_LONG).show();
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
                        "نصب از منابع ناشناس را مجاز کنید و دوباره تلاش کنید",
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
            Toast.makeText(this, "نصب ممکن نشد", Toast.LENGTH_LONG).show();
        }
    }

    // ── صفحهٔ اصلی ───────────────────────────────────────────────────

    private void renderHome() {
        hideSplash();
        page.removeAllViews();
        showingSubPage = false;
        activeSection = null;
        activeCategory = null;
        firstCard = null;
        if (catalog == null) {
            addMessage("در حال بارگذاری فهرست", "لطفاً کمی صبر کنید.");
            return;
        }

        BrowseSection featured = featuredSection();
        if (featured != null) addHero(featured);

        for (BrowseSection section : catalog.sections) {
            addSectionRail(section);
        }

        if (!catalog.movieCategories.isEmpty()) {
            page.addView(ui.sectionHeader("دسته‌بندی فیلم‌ها",
                    Ui.fa(catalog.movieCategories.size()) + " ژانر", null));
            page.addView(ui.rail(chipViews(catalog.movieCategories)));
        }
        if (!catalog.seriesCategories.isEmpty()) {
            page.addView(ui.sectionHeader("دسته‌بندی سریال‌ها",
                    Ui.fa(catalog.seriesCategories.size()) + " ژانر", null));
            page.addView(ui.rail(chipViews(catalog.seriesCategories)));
        }
        focusFirst();
    }

    /** بخش ویژهٔ سایت (بیلبورد)؛ اگر نبود، هیچ بیلبوردی ساخته نمی‌شود. */
    private BrowseSection featuredSection() {
        if (catalog == null) return null;
        for (BrowseSection section : catalog.sections) {
            if (section.title != null && section.title.contains("ویژه") && !section.items.isEmpty()) {
                return section;
            }
        }
        return null;
    }

    private void addHero(BrowseSection section) {
        // هر روز یک عنوان ویژه تازه (بدون تایمر و بدون پرش ناگهانی)
        int index = (int) (System.currentTimeMillis() / 86400000L) % section.items.size();
        final BrowseItem item = section.items.get(Math.abs(index));

        FrameLayout hero = new FrameLayout(this);
        hero.setClipChildren(false);
        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ui.loadCover(art, item.coverUrl);
        hero.addView(art, new FrameLayout.LayoutParams(-1, -1));
        hero.addView(ui.scrim(GradientDrawable.Orientation.RIGHT_LEFT,
                        0xF70A0A0C, 0xCC0A0A0C, 0x400A0A0C, 0x050A0A0C),
                new FrameLayout.LayoutParams(-1, -1));
        hero.addView(ui.scrim(GradientDrawable.Orientation.BOTTOM_TOP,
                        0xFF0A0A0C, 0x000A0A0C),
                new FrameLayout.LayoutParams(-1, -1));

        LinearLayout info = ui.column();
        info.setPadding(ui.dp(Ui.PAGE_PAD), ui.dp(34), ui.dp(Ui.PAGE_PAD), ui.dp(18));

        TextView kicker = ui.text("ویژهٔ نفتلیکس", 11, Ui.BRAND, true);
        kicker.setLetterSpacing(0.08f);
        info.addView(kicker);

        TextView title = ui.text(item.title, 27, Ui.TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title, ui.withTop(-1, -2, 8));

        LinearLayout tags = ui.row();
        tags.addView(ui.tagPill(Ui.faType(item.contentType), Ui.BRAND, Ui.BRAND, Ui.TEXT, true));
        if (item.meta != null && !item.meta.isEmpty()) {
            TextView meta = ui.tagPill(item.meta, Ui.GLASS, Ui.HAIRLINE, Ui.TEXT_DIM, false);
            LinearLayout.LayoutParams metaParams = ui.params(-2, -2);
            metaParams.setMarginStart(ui.dp(8));
            tags.addView(meta, metaParams);
        }
        info.addView(tags, ui.withTop(-1, -2, 14));

        LinearLayout ctas = ui.row();
        TextView watch = ui.primaryButton("تماشا و دانلود", R.drawable.ic_play);
        watch.setOnClickListener(v -> openItem(item));
        ctas.addView(watch, ui.params(-2, ui.dp(44)));
        TextView browse = ui.ghostButton("مرور کاتالوگ", 0);
        browse.setOnClickListener(v -> {
            if (firstCard != null) firstCard.requestFocus();
        });
        LinearLayout.LayoutParams browseParams = ui.params(-2, ui.dp(44));
        browseParams.setMarginStart(ui.dp(10));
        ctas.addView(browse, browseParams);
        info.addView(ctas, ui.withTop(-1, -2, 18));

        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                ui.dp(560), -2, Gravity.START | Gravity.BOTTOM);
        hero.addView(info, infoParams);
        page.addView(hero, ui.params(-1, ui.dp(224)));
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
            page.addView(note("موردی پیدا نشد."));
            return;
        }
        if (firstCard == null) firstCard = cards.get(0);
        TextView more = null;
        if (section.url != null && !section.url.isEmpty()) {
            more = ui.ghostButton("مشاهده همه", R.drawable.ic_chevron_end);
            more.setLayoutParams(ui.params(-2, ui.dp(34)));
            more.setOnClickListener(v -> openSection(section, 1));
        }
        page.addView(ui.sectionHeader(section.title, Ui.fa(section.items.size()) + " عنوان", more));
        page.addView(ui.rail(cards));
    }

    private List<View> chipViews(List<BrowseCategory> categories) {
        List<View> chips = new ArrayList<>();
        for (final BrowseCategory category : categories) {
            TextView chip = ui.chip(category.title, Ui.fa(category.count));
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

    // ── زیرصفحه‌ها ───────────────────────────────────────────────────

    private void showSearchResults() {
        if (catalog == null) return;
        final String query = searchBox.getText().toString().trim();
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
        searchBox.clearFocus();
        showingSubPage = true;
        activeSection = null;
        activeCategory = null;
        if (query.isEmpty()) {
            page.removeAllViews();
            addBackButton();
            addHeading("همه عنوان‌ها", Ui.fa(uniqueItems(catalog.sections).size()) + " عنوان");
            addItemsGrid(uniqueItems(catalog.sections));
            focusFirst();
            return;
        }
        page.removeAllViews();
        addBackButton();
        addHeading("نتایج جست‌وجو", query);
        showLoading("در حال جست‌وجو…");
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
            final String source = found.isEmpty() ? "فهرست" : "سایت";
            runOnUiThread(() -> {
                hideLoading();
                page.removeAllViews();
                addBackButton();
                addHeading("نتایج جست‌وجو", query);
                status.setText(Ui.fa(direct.size()) + " نتیجه از " + source);
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
        showLoading("در حال بارگذاری " + section.title + "…");
        io.execute(() -> {
            try {
                ListingPage result = repository.loadListingPage(section.url, section.contentType, pageNum);
                runOnUiThread(() -> {
                    hideLoading();
                    listingHasNext = result.hasNext;
                    status.setText(section.title + " · صفحه " + Ui.fa(pageNum));
                    page.removeAllViews();
                    addBackButton();
                    addHeading(section.title, "صفحه " + Ui.fa(pageNum));
                    addItemsGrid(result.items);
                    addPaginationBar();
                    focusFirst();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    hideLoading();
                    status.setText("بارگذاری ناموفق بود: " + section.title);
                });
            }
        });
    }

    private void openCategory(BrowseCategory category, int pageNum) {
        activeCategory = category;
        activeSection = null;
        listingPage = pageNum;
        showingSubPage = true;
        showLoading("در حال بارگذاری " + category.title + "…");
        io.execute(() -> {
            try {
                ListingPage result = repository.loadListingPage(category.url, category.contentType, pageNum);
                runOnUiThread(() -> {
                    hideLoading();
                    listingHasNext = result.hasNext;
                    status.setText(category.title + " · صفحه " + Ui.fa(pageNum));
                    page.removeAllViews();
                    addBackButton();
                    String count = category.count == null || category.count.isEmpty()
                            ? "" : Ui.fa(category.count) + " عنوان";
                    addHeading(category.title, count);
                    addItemsGrid(result.items);
                    addPaginationBar();
                    focusFirst();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    hideLoading();
                    status.setText("بارگذاری دسته ناموفق بود: " + category.title);
                });
            }
        });
    }

    /** شبکهٔ کارت‌ها برای صفحه‌های آرشیو، ژانر و نتیجهٔ جست‌وجو. */
    private void addItemsGrid(List<BrowseItem> items) {
        if (items == null || items.isEmpty()) {
            page.addView(note("موردی پیدا نشد."));
            return;
        }
        int columns = ui.gridColumns();
        float cardWidth = ui.gridCardWidthDp(columns);
        LinearLayout grid = ui.column();
        List<View> rowCards = new ArrayList<>();
        for (final BrowseItem item : items) {
            View card = ui.posterCard(item, cardWidth, true);
            card.setOnClickListener(v -> openItem(item));
            rowCards.add(card);
            if (rowCards.size() == columns) {
                grid.addView(ui.gridRow(rowCards), bottomMargin());
                rowCards = new ArrayList<>();
            }
        }
        if (!rowCards.isEmpty()) grid.addView(ui.gridRow(rowCards), bottomMargin());
        page.addView(grid);
    }

    private LinearLayout.LayoutParams bottomMargin() {
        LinearLayout.LayoutParams params = ui.params(-1, -2);
        params.bottomMargin = ui.dp(16);
        return params;
    }

    private void addPaginationBar() {
        LinearLayout bar = ui.row();
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(0, ui.dp(6), 0, ui.dp(10));

        if (listingPage > 1) {
            TextView prev = ui.ghostButton("صفحه قبل", R.drawable.ic_back);
            prev.setOnClickListener(v -> {
                if (activeSection != null) openSection(activeSection, listingPage - 1);
                else if (activeCategory != null) openCategory(activeCategory, listingPage - 1);
            });
            bar.addView(prev, ui.params(-2, ui.dp(42)));
        }
        TextView num = ui.text("صفحه " + Ui.fa(listingPage), 14, Ui.TEXT, true);
        num.setPadding(ui.dp(18), 0, ui.dp(18), 0);
        bar.addView(num);
        if (listingHasNext) {
            TextView next = ui.ghostButton("صفحه بعد", R.drawable.ic_chevron_end);
            next.setOnClickListener(v -> {
                if (activeSection != null) openSection(activeSection, listingPage + 1);
                else if (activeCategory != null) openCategory(activeCategory, listingPage + 1);
            });
            bar.addView(next, ui.params(-2, ui.dp(42)));
        }
        page.addView(bar);
    }

    // ── اجزای مشترک ─────────────────────────────────────────────────

    private void addBackButton() {
        TextView back = ui.ghostButton("بازگشت به فهرست", R.drawable.ic_back);
        back.setTag("back");
        back.setOnClickListener(view -> renderHome());
        LinearLayout.LayoutParams params = ui.params(-2, ui.dp(42));
        params.topMargin = ui.dp(4);
        page.addView(back, params);
    }

    private void addHeading(String text, String countText) {
        LinearLayout header = ui.row();
        header.setPadding(0, ui.dp(16), 0, ui.dp(6));
        View accent = new View(this);
        accent.setBackground(ui.rounded(Ui.BRAND, 2));
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
        view.setPadding(0, ui.dp(4), 0, ui.dp(12));
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
            // دکمه بازگشت هیچ‌وقت فوکوس خودکار نگیرد؛ اولین آیتم نتیجه فوکوس شود
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
    public void onBackPressed() {
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
