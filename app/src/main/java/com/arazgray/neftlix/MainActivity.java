package com.arazgray.neftlix;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * صفحه اصلی راست‌چین و فارسی، مناسب ریموت‌کنترل تلویزیون.
 * هر بار که باز می‌شود فهرست را از سایت تازه می‌کند.
 */
public class MainActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CatalogRepository repository = new CatalogRepository();
    private CatalogData catalog;
    private LinearLayout page;
    private TextView status;
    private EditText searchBox;
    private boolean showingSubPage;

    private BrowseSection activeSection;
    private BrowseCategory activeCategory;
    private int listingPage = 1;
    private boolean listingHasNext;

    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> voiceLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        getWindow().setStatusBarColor(Color.rgb(10, 10, 10));
        getWindow().setNavigationBarColor(Color.rgb(10, 10, 10));
        voiceLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        java.util.ArrayList<String> matches = result.getData().getStringArrayListExtra(
                                android.speech.RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            searchBox.setText(matches.get(0));
                            showSearchResults();
                        }
                    }
                });
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

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(22), dp(14), dp(22), dp(10));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(120), dp(40));
        logoParams.setMargins(0, 0, 0, 0);
        toolbar.addView(logo, logoParams);

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextColor(Color.WHITE);
        searchBox.setHintTextColor(Color.LTGRAY);
        searchBox.setHint("جست‌وجوی فیلم و سریال…");
        searchBox.setTextDirection(View.TEXT_DIRECTION_RTL);
        searchBox.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        searchBox.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setPadding(dp(12), 0, dp(12), 0);
        android.graphics.drawable.GradientDrawable searchBg =
                new android.graphics.drawable.GradientDrawable();
        searchBg.setColor(Color.rgb(20, 20, 20));
        searchBg.setStroke(dp(2), Color.rgb(229, 9, 20));
        searchBg.setCornerRadius(dp(10));
        searchBox.setBackground(searchBg);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        searchParams.setMargins(dp(8), 0, dp(8), 0);
        toolbar.addView(searchBox, searchParams);
        searchBox.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                showSearchResults();
                return true;
            }
            return false;
        });

        Button search = toolbarButton("🔍");
        search.setOnClickListener(view -> showSearchResults());
        toolbar.addView(search);

        Button mic = toolbarButton("🎙");
        mic.setOnClickListener(view -> startVoiceSearch());
        toolbar.addView(mic);

        Button all = toolbarButton("همه");
        all.setOnClickListener(view -> showAllItems());
        toolbar.addView(all);

        Button refresh = toolbarButton("⟳ کاتالوگ");
        refresh.setOnClickListener(view -> refreshCatalog(false));
        toolbar.addView(refresh);

        Button update = toolbarButton("⬇ نفتلیکس");
        update.setOnClickListener(view -> checkAppUpdate());
        toolbar.addView(update);

        Button info = toolbarButton("ⓘ");
        info.setOnClickListener(view -> showInfoDialog());
        toolbar.addView(info);
        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        toolbarScroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        toolbarScroll.addView(toolbar);
        root.addView(toolbarScroll, new LinearLayout.LayoutParams(-1, dp(56)));

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(14);
        status.setTextDirection(View.TEXT_DIRECTION_RTL);
        status.setPadding(0, dp(4), 0, dp(6));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        page.setPadding(0, dp(4), 0, dp(28));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        View backdrop = new View(this);
        android.graphics.drawable.GradientDrawable gradient =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFF3A0A0E, 0xFF000000});
        backdrop.setBackground(gradient);
        backdrop.setAlpha(0.5f);
        frame.addView(backdrop, new android.widget.FrameLayout.LayoutParams(-1, -1));
        frame.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));

        loadingOverlay = centeredOverlay("⏳ در حال بارگذاری…");
        loadingOverlay.setVisibility(View.GONE);
        frame.addView(loadingOverlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
    }

    private android.widget.FrameLayout loadingOverlay;
    private TextView loadingText;

    private android.widget.FrameLayout centeredOverlay(String text) {
        android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
        overlay.setBackgroundColor(0xCC000000);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        android.widget.ProgressBar spinner = new android.widget.ProgressBar(this);
        android.widget.LinearLayout.LayoutParams spinnerParams =
                new android.widget.LinearLayout.LayoutParams(dp(56), dp(56));
        spinnerParams.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(spinner, spinnerParams);
        loadingText = new TextView(this);
        loadingText.setText(text);
        loadingText.setTextColor(Color.WHITE);
        loadingText.setTextSize(16);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setTextDirection(View.TEXT_DIRECTION_RTL);
        loadingText.setPadding(0, dp(12), 0, 0);
        box.addView(loadingText);
        overlay.addView(box, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return overlay;
    }

    private void showLoading(String text) {
        if (loadingText != null) loadingText.setText(text);
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    private void refreshCatalog() {
        refreshCatalog(false);
    }

    private void refreshCatalog(boolean quiet) {
        if (quiet) {
            status.setText("در حال به‌روزرسانی پس‌زمینه…");
        } else {
            status.setText("در حال به‌روزرسانی فهرست از سایت…");
            showLoading("⏳ در حال به‌روزرسانی فهرست…");
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
                        status.setText("فهرست به‌روز شد · " + toFaDigits(countItems(latest)) + " عنوان");
                        if (!showingSubPage && page.getChildCount() == 0) renderHome();
                        return;
                    }
                    showingSubPage = false;
                    activeSection = null;
                    activeCategory = null;
                    status.setText("فهرست به‌روز شد · " + toFaDigits(countItems(latest)) + " عنوان · با ریموت انتخاب کنید");
                    renderHome();
                });
            } catch (IOException error) {
                runOnUiThread(() -> {
                    hideLoading();
                    status.setText("به‌روزرسانی ناموفق بود. اینترنت را بررسی کنید و دوباره تلاش کنید.");
                    if (catalog == null) {
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
            } catch (Exception e) {
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

    private void startVoiceSearch() {
        try {
            android.content.Intent intent =
                    new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say the movie or series name");
            voiceLauncher.launch(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Voice search is not supported on this device", Toast.LENGTH_LONG).show();
        }
    }

    private void showInfoDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        layout.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView name = new TextView(this);
        name.setText("نفتلیکس · Neftlix");
        name.setTextColor(Color.rgb(229, 9, 20));
        name.setTextSize(20);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        layout.addView(name);

        TextView desc = new TextView(this);
        desc.setText("اپ تلویزیون فارسی برای مرور فیلم‌ها و سریال‌ها، جست‌وجوی سراسری آرشیو و پخش مستقیم با پخش‌کننده داخلی.");
        desc.setTextColor(Color.WHITE);
        desc.setTextSize(14);
        desc.setGravity(Gravity.CENTER);
        desc.setTextDirection(View.TEXT_DIRECTION_RTL);
        desc.setPadding(0, dp(8), 0, 0);
        layout.addView(desc);

        TextView version = new TextView(this);
        version.setText("نسخه " + BuildConfig.VERSION_NAME + " (" + toFaDigits(BuildConfig.VERSION_CODE) + ")");
        version.setTextColor(Color.LTGRAY);
        version.setTextSize(13);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(8), 0, 0);
        layout.addView(version);

        TextView author = new TextView(this);
        author.setText("سازنده: Araz Gray · arazgray.com");
        author.setTextColor(Color.LTGRAY);
        author.setTextSize(13);
        author.setGravity(Gravity.CENTER);
        author.setPadding(0, dp(4), 0, dp(8));
        layout.addView(author);

        android.graphics.Bitmap qr = makeQrBitmap("https://arazgray.com", 420);
        if (qr != null) {
            ImageView qrView = new ImageView(this);
            qrView.setImageBitmap(qr);
            qrView.setBackgroundColor(Color.WHITE);
            qrView.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams qrParams =
                    new LinearLayout.LayoutParams(dp(220), dp(220));
            qrParams.gravity = Gravity.CENTER_HORIZONTAL;
            layout.addView(qrView, qrParams);
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("درباره نفتلیکس")
                .setView(layout)
                .setPositiveButton("بستن", null)
                .setNeutralButton("باز کردن سایت", (d, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://arazgray.com")));
                    } catch (Exception ignored) {
                    }
                })
                .show();
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
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
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
        progress.setPadding(dp(8), dp(8), dp(8), dp(8));
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(8));
        android.widget.TextView label = new android.widget.TextView(this);
        label.setText("در حال دانلود… ٪۰");
        label.setTextColor(Color.WHITE);
        label.setTextDirection(View.TEXT_DIRECTION_RTL);
        layout.addView(label, new LinearLayout.LayoutParams(-1, -2));
        layout.addView(progress, new LinearLayout.LayoutParams(-1, dp(24)));
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
                                label.setText("در حال دانلود… ٪" + toFaDigits(percent));
                            });
                        }
                    }
                }
                runOnUiThread(() -> {
                    dialog.dismiss();
                    status.setText("دانلود تمام شد");
                    installApk(apk);
                });
            } catch (Exception e) {
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
        } catch (Exception e) {
            Toast.makeText(this, "نصب ممکن نشد", Toast.LENGTH_LONG).show();
        }
    }

    private void renderHome() {
        page.removeAllViews();
        showingSubPage = false;
        activeSection = null;
        activeCategory = null;
        if (catalog == null) {
            addMessage("در حال بارگذاری فهرست", "لطفاً کمی صبر کنید.");
            return;
        }

        addHeading("دسته‌بندی فیلم‌ها");
        addCategoryRow(catalog.movieCategories);
        addHeading("دسته‌بندی سریال‌ها");
        addCategoryRow(catalog.seriesCategories);

        for (BrowseSection section : catalog.sections) {
            addSectionPreview(section);
        }
        focusFirst();
    }

    private void addSectionPreview(BrowseSection section) {
        addHeading(section.title);
        if (section.items.isEmpty()) {
            addSmallText("موردی پیدا نشد.");
            return;
        }
        List<BrowseItem> preview = section.items.size() > 20 ? section.items.subList(0, 20) : section.items;
        addItemRow(preview);
        if (section.url != null && !section.url.isEmpty()) {
            Button more = toolbarButton("مشاهده همه ←");
            more.setOnClickListener(v -> openSection(section, 1));
            page.addView(more, new LinearLayout.LayoutParams(-2, dp(46)));
        }
    }

    private void showAllItems() {
        if (catalog == null) return;
        showingSubPage = true;
        activeSection = null;
        activeCategory = null;
        page.removeAllViews();
        addBackButton();
        addHeading("همه عنوان‌ها");
        addItemRow(uniqueItems(catalog.sections));
        focusFirst();
    }

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
            addHeading("همه عنوان‌ها");
            addItemRow(uniqueItems(catalog.sections));
            return;
        }
        page.removeAllViews();
        addBackButton();
        addHeading("نتایج جست‌وجو: " + query);
        showLoading("⏳ در حال جست‌وجو…");
        io.execute(() -> {
            List<BrowseItem> found = new ArrayList<>();
            try {
                found = repository.quickSearch(query);
                if (found.isEmpty()) {
                    found = repository.search(query);
                }
            } catch (IOException e) {
                try {
                    found = repository.search(query);
                } catch (IOException ignored) {
                    found = new ArrayList<>();
                }
            }
            final List<BrowseItem> direct = found.isEmpty() ? filterLocal(query) : found;
            final String source = found.isEmpty() ? "فهرست" : "سایت";
            runOnUiThread(() -> {
                hideLoading();
                page.removeAllViews();
                addBackButton();
                addHeading("نتایج جست‌وجو: " + query);
                status.setText(toFaDigits(direct.size()) + " نتیجه از " + source);
                addItemRow(direct);
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
        showLoading("⏳ در حال بارگذاری " + section.title + "…");
        io.execute(() -> {
            try {
                ListingPage result = repository.loadListingPage(section.url, section.contentType, pageNum);
                runOnUiThread(() -> {
                    hideLoading();
                    listingHasNext = result.hasNext;
                    status.setText(section.title + " · صفحه " + toFaDigits(pageNum));
                    page.removeAllViews();
                    addBackButton();
                    addHeading(section.title);
                    addItemRow(result.items);
                    addPaginationBar();
                    focusFirst();
                });
            } catch (IOException error) {
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
        showLoading("⏳ در حال بارگذاری " + category.title + "…");
        io.execute(() -> {
            try {
                ListingPage result = repository.loadListingPage(category.url, category.contentType, pageNum);
                runOnUiThread(() -> {
                    hideLoading();
                    listingHasNext = result.hasNext;
                    status.setText(category.title + " · صفحه " + toFaDigits(pageNum));
                    page.removeAllViews();
                    addBackButton();
                    String count = category.count == null || category.count.isEmpty() ? "" : "  (" + toFaDigits(category.count) + ")";
                    addHeading(category.title + count);
                    addItemRow(result.items);
                    addPaginationBar();
                    focusFirst();
                });
            } catch (IOException error) {
                runOnUiThread(() -> {
                    hideLoading();
                    status.setText("بارگذاری دسته ناموفق بود: " + category.title);
                });
            }
        });
    }

    private void addPaginationBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(8), 0, dp(8));

        if (listingPage > 1) {
            Button prev = toolbarButton("صفحه قبل");
            prev.setOnClickListener(v -> {
                if (activeSection != null) openSection(activeSection, listingPage - 1);
                else if (activeCategory != null) openCategory(activeCategory, listingPage - 1);
            });
            bar.addView(prev, new LinearLayout.LayoutParams(-2, dp(48)));
        }
        TextView num = new TextView(this);
        num.setText("صفحه " + toFaDigits(listingPage));
        num.setTextColor(Color.WHITE);
        num.setTextSize(15);
        num.setPadding(dp(14), 0, dp(14), 0);
        bar.addView(num);
        if (listingHasNext) {
            Button next = toolbarButton("صفحه بعد");
            next.setOnClickListener(v -> {
                if (activeSection != null) openSection(activeSection, listingPage + 1);
                else if (activeCategory != null) openCategory(activeCategory, listingPage + 1);
            });
            bar.addView(next, new LinearLayout.LayoutParams(-2, dp(48)));
        }
        page.addView(bar);
    }

    private void openItem(BrowseItem item) {
        Intent intent = new Intent(this, DetailsActivity.class);
        intent.putExtra(DetailsActivity.EXTRA_URL, item.url);
        intent.putExtra(DetailsActivity.EXTRA_TITLE, item.title);
        startActivity(intent);
    }

    private void addCategoryRow(List<BrowseCategory> categories) {
        if (categories.isEmpty()) {
            addSmallText("دسته‌ای پیدا نشد.");
            return;
        }
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (BrowseCategory category : categories) {
            String label = category.title;
            if (category.count != null && !category.count.isEmpty()) label += " (" + toFaDigits(category.count) + ")";
            Button button = toolbarButton(label);
            button.setOnClickListener(view -> openCategory(category, 1));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(48));
            params.setMargins(0, 0, dp(8), 0);
            row.addView(button, params);
        }
        scroll.addView(row);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, dp(58)));
    }

    private void addItemRow(List<BrowseItem> items) {
        if (items.isEmpty()) {
            addSmallText("موردی پیدا نشد.");
            return;
        }
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (BrowseItem item : items) row.addView(makeCard(item));
        scroll.addView(row);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, dp(268)));
    }

    private View makeCard(BrowseItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackgroundResource(android.R.drawable.list_selector_background);
        card.setPadding(dp(5), dp(5), dp(5), dp(5));
        card.setOnClickListener(view -> openItem(item));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(148), dp(256));
        cardParams.setMargins(0, 0, dp(10), 0);
        card.setLayoutParams(cardParams);
        card.setOnFocusChangeListener((v, hasFocus) -> v.setScaleX(hasFocus ? 1.05f : 1f));

        ImageView poster = new ImageView(this);
        poster.setBackgroundColor(Color.rgb(35, 45, 54));
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (item.coverUrl != null && !item.coverUrl.isEmpty()) {
            Glide.with(this).load(item.coverUrl).centerCrop().into(poster);
        }
        card.addView(poster, new LinearLayout.LayoutParams(-1, dp(176)));

        TextView kind = new TextView(this);
        kind.setText(faType(item.contentType));
        kind.setTextColor(Color.rgb(229, 9, 20));
        kind.setTextSize(11);
        kind.setTextDirection(View.TEXT_DIRECTION_RTL);
        kind.setPadding(0, dp(3), 0, 0);
        card.addView(kind);

        TextView name = new TextView(this);
        name.setText(item.title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(13);
        name.setMaxLines(2);
        name.setTextDirection(View.TEXT_DIRECTION_RTL);
        card.addView(name);
        if (item.meta != null && !item.meta.isEmpty()) {
            TextView meta = new TextView(this);
            meta.setText(item.meta);
            meta.setTextColor(Color.LTGRAY);
            meta.setTextSize(11);
            meta.setMaxLines(1);
            meta.setTextDirection(View.TEXT_DIRECTION_RTL);
            card.addView(meta);
        }
        return card;
    }

    private String faType(String type) {
        if ("series".equalsIgnoreCase(type)) return "سریال";
        if ("movie".equalsIgnoreCase(type)) return "فیلم";
        return "فیلم و سریال";
    }

    private String toFaDigits(Object value) {
        String s = String.valueOf(value);
        return s.replace('0', '۰').replace('1', '۱').replace('2', '۲')
                .replace('3', '۳').replace('4', '۴').replace('5', '۵')
                .replace('6', '۶').replace('7', '۷').replace('8', '۸').replace('9', '۹');
    }

    private void addBackButton() {
        Button back = toolbarButton("← بازگشت به فهرست");
        back.setOnClickListener(view -> renderHome());
        page.addView(back, new LinearLayout.LayoutParams(-2, dp(48)));
    }

    private void addHeading(String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(20);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextDirection(View.TEXT_DIRECTION_RTL);
        heading.setPadding(0, dp(12), 0, dp(7));
        page.addView(heading);
    }

    private void addSmallText(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.LTGRAY);
        label.setTextSize(14);
        label.setTextDirection(View.TEXT_DIRECTION_RTL);
        page.addView(label);
    }

    private void addMessage(String title, String message) {
        addHeading(title);
        addSmallText(message == null ? "" : message);
    }

    private void focusFirst() {
        page.post(() -> {
            View first = findFirstFocusable(page);
            if (first != null) first.requestFocus();
        });
    }

    private View findFirstFocusable(View root) {
        if (root.isFocusable()) return root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findFirstFocusable(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private Button toolbarButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextDirection(View.TEXT_DIRECTION_RTL);
        button.setAllCaps(false);
        button.setFocusable(true);
        final android.content.res.ColorStateList idle =
                android.content.res.ColorStateList.valueOf(Color.rgb(45, 45, 45));
        final android.content.res.ColorStateList focused =
                android.content.res.ColorStateList.valueOf(Color.rgb(229, 9, 20));
        button.setBackgroundTintList(idle);
        button.setOnFocusChangeListener((v, hasFocus) -> {
            button.setBackgroundTintList(hasFocus ? focused : idle);
            v.setScaleX(hasFocus ? 1.06f : 1f);
            v.setScaleY(hasFocus ? 1.06f : 1f);
        });
        return button;
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

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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
