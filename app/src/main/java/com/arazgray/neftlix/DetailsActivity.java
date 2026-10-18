package com.arazgray.neftlix;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * صفحه جزئیات فیلم/سریال: راست‌چین و فارسی.
 * گروه‌ها و زیرگروه‌های دانلود به‌صورت پیش‌فرض جمع‌شده‌اند و با ریموت باز می‌شوند.
 */
public class DetailsActivity extends AppCompatActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CatalogRepository repository = new CatalogRepository();
    private LinearLayout page;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        String url = getIntent().getStringExtra(EXTRA_URL);
        String fallbackTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "نشانی صفحه نامعتبر است", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(22), dp(14), dp(22), dp(10));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        Button back = toolbarButton("← بازگشت");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back);

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(14);
        status.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        statusParams.setMargins(dp(12), 0, 0, 0);
        toolbar.addView(status, statusParams);
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        backdropView = new ImageView(this);
        backdropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropView.setAlpha(0.22f);

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        frame.addView(backdropView, new android.widget.FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackgroundColor(0x99000000);
        frame.addView(shade, new android.widget.FrameLayout.LayoutParams(-1, -1));
        frame.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));

        loadingOverlay = centeredOverlay("⏳ در حال بارگذاری…");
        frame.addView(loadingOverlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);

        showLoading("⏳ در حال بارگذاری…");
        io.execute(() -> {
            try {
                ItemDetails details = repository.loadItem(url);
                runOnUiThread(() -> {
                    hideLoading();
                    if (details.backdropUrl != null && !details.backdropUrl.isEmpty()) {
                        Glide.with(this).load(details.backdropUrl).into(backdropView);
                    }
                    renderItem(details, fallbackTitle);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    hideLoading();
                    status.setText("خطا در بارگذاری صفحه");
                    addSmallText(e.getMessage() == null ? "" : e.getMessage());
                });
            }
        });
    }

    private ImageView backdropView;
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
        overlay.setVisibility(View.GONE);
        return overlay;
    }

    private void showLoading(String text) {
        if (loadingText != null) loadingText.setText(text);
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    private void renderItem(ItemDetails details, String fallbackTitle) {
        page.removeAllViews();
        String title = details.title == null || details.title.isEmpty() ? fallbackTitle : details.title;
        status.setText(title == null ? "" : title);

        LinearLayout intro = new LinearLayout(this);
        intro.setOrientation(LinearLayout.HORIZONTAL);
        intro.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        intro.setGravity(Gravity.CENTER_VERTICAL);

        if (details.coverUrl != null && !details.coverUrl.isEmpty()) {
            ImageView poster = new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(details.coverUrl).into(poster);
            intro.addView(poster, new LinearLayout.LayoutParams(dp(140), dp(195)));
        }
        TextView titleView = new TextView(this);
        titleView.setText(title == null ? "" : title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(22);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setTextDirection(View.TEXT_DIRECTION_RTL);
        titleView.setPadding(dp(16), 0, 0, 0);
        intro.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(intro);

        addInfoBlock(details);

        if (details.groups.isEmpty() && details.story.isEmpty()) {
            addSmallText("برای این عنوان اطلاعاتی پیدا نشد.");
            return;
        }
        for (DownloadGroup group : details.groups) {
            if (group.season) {
                LinearLayout seasonContent = verticalContainer();
                for (DownloadCategory category : group.categories) addCategoryDetails(seasonContent, category);
                String t = group.title + (group.status == null || group.status.isEmpty() ? "" : "  ·  " + group.status);
                page.addView(expandable(t, seasonContent));
            } else {
                for (DownloadCategory category : group.categories) addCategoryDetails(page, category);
            }
        }
        if (!details.comments.isEmpty()) {
            page.addView(expandable("نظرات (" + toFa(details.comments.size()) + ")", commentsView(details)));
        }
        // فوکوس اولیه روی اولین گروه برای راحتی کار با ریموت
        if (page.getChildCount() > 1) page.getChildAt(1).requestFocus();
    }

    private void addInfoBlock(ItemDetails details) {
        List<String> meta = new ArrayList<>();
        if (!details.imdbRating.isEmpty()) meta.add("IMDb ‏" + details.imdbRating + "/10");
        if (!details.satisfaction.isEmpty()) meta.add("رضایت " + details.satisfaction);
        if (!meta.isEmpty()) {
            TextView ratings = new TextView(this);
            ratings.setText(android.text.TextUtils.join(" · ", meta));
            ratings.setTextColor(Color.rgb(229, 9, 20));
            ratings.setTextSize(15);
            ratings.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            ratings.setTextDirection(View.TEXT_DIRECTION_RTL);
            ratings.setPadding(0, dp(8), 0, 0);
            page.addView(ratings);
        }
        List<String> tags = new ArrayList<>();
        tags.addAll(details.genres);
        tags.addAll(details.flags);
        if (!tags.isEmpty()) {
            TextView tagsView = new TextView(this);
            tagsView.setText(android.text.TextUtils.join(" · ", tags));
            tagsView.setTextColor(Color.LTGRAY);
            tagsView.setTextSize(14);
            tagsView.setTextDirection(View.TEXT_DIRECTION_RTL);
            tagsView.setPadding(0, dp(4), 0, 0);
            page.addView(tagsView);
        }
        if (!details.story.isEmpty()) {
            TextView story = new TextView(this);
            story.setText(details.story);
            story.setTextColor(Color.WHITE);
            story.setTextSize(15);
            story.setTextDirection(View.TEXT_DIRECTION_RTL);
            story.setLineSpacing(0, 1.35f);
            story.setPadding(0, dp(10), 0, dp(4));
            page.addView(story);
        }
        int linkCount = countLinks(details);
        if (linkCount > 0) {
            TextView counts = new TextView(this);
            counts.setText(toFa(linkCount) + " لینک دانلود در " + toFa(details.groups.size()) + " گروه");
            counts.setTextColor(Color.LTGRAY);
            counts.setTextSize(13);
            counts.setTextDirection(View.TEXT_DIRECTION_RTL);
            counts.setPadding(0, 0, 0, dp(4));
            page.addView(counts);
        }
    }

    private View commentsView(ItemDetails details) {
        LinearLayout container = verticalContainer();
        for (CommentItem comment : details.comments) {
            TextView head = new TextView(this);
            String who = comment.author.isEmpty() ? "کاربر" : comment.author;
            head.setText(who + (comment.time.isEmpty() ? "" : " · " + comment.time));
            head.setTextColor(Color.rgb(229, 9, 20));
            head.setTextSize(13);
            head.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            head.setTextDirection(View.TEXT_DIRECTION_RTL);
            head.setPadding(0, dp(8), 0, 0);
            container.addView(head);
            TextView body = new TextView(this);
            body.setText(comment.text);
            body.setTextColor(Color.LTGRAY);
            body.setTextSize(14);
            body.setTextDirection(View.TEXT_DIRECTION_RTL);
            body.setLineSpacing(0, 1.3f);
            container.addView(body);
        }
        return container;
    }

    private String toFa(int n) {
        return String.valueOf(n).replace('0', '۰').replace('1', '۱').replace('2', '۲')
                .replace('3', '۳').replace('4', '۴').replace('5', '۵')
                .replace('6', '۶').replace('7', '۷').replace('8', '۸').replace('9', '۹');
    }

    private void addCategoryDetails(LinearLayout parent, DownloadCategory category) {
        LinearLayout categoryContent = verticalContainer();
        for (DownloadOption option : category.options) {
            LinearLayout optionContent = verticalContainer();
            for (MediaLink link : option.links) {
                String displayLabel = "دانلود مستقیم".equals(link.label) ? "پخش رایگان" : link.label;
                Button play = toolbarButton("▶  " + displayLabel);
                play.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                play.setTextDirection(View.TEXT_DIRECTION_RTL);
                play.setOnClickListener(v -> {
                    Intent intent = new Intent(this, PlayerActivity.class);
                    intent.putExtra(PlayerActivity.EXTRA_URL, link.url);
                    intent.putExtra(PlayerActivity.EXTRA_TITLE, link.label);
                    startActivity(intent);
                });
                optionContent.addView(play, new LinearLayout.LayoutParams(-1, dp(56)));
            }
            String quality = option.quality == null || option.quality.isEmpty() ? "دانلود" : option.quality;
            if (option.encoder != null && !option.encoder.isEmpty()) quality += "  ·  " + option.encoder;
            categoryContent.addView(expandable(quality, optionContent));
        }
        String title = category.title == null ? "دانلودها" : category.title;
        parent.addView(expandable(title, categoryContent));
    }

    private View expandable(String title, View content) {
        LinearLayout box = verticalContainer();
        TextView summary = new TextView(this);
        summary.setText("◀  " + title);
        summary.setTextColor(Color.WHITE);
        summary.setTextSize(17);
        summary.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        summary.setTextDirection(View.TEXT_DIRECTION_RTL);
        summary.setPadding(dp(14), dp(13), dp(14), dp(13));
        summary.setFocusable(true);
        summary.setClickable(true);
        summary.setBackgroundResource(android.R.drawable.list_selector_background);
        content.setVisibility(View.GONE);
        summary.setOnClickListener(v -> {
            boolean expand = content.getVisibility() != View.VISIBLE;
            content.setVisibility(expand ? View.VISIBLE : View.GONE);
            summary.setText((expand ? "▼  " : "◀  ") + title);
        });
        box.addView(summary);
        box.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(3), 0, dp(3));
        box.setLayoutParams(params);
        return box;
    }

    private int countLinks(ItemDetails details) {
        int n = 0;
        for (DownloadGroup g : details.groups)
            for (DownloadCategory c : g.categories)
                for (DownloadOption o : c.options) n += o.links.size();
        return n;
    }

    private void addSmallText(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.LTGRAY);
        label.setTextSize(14);
        label.setTextDirection(View.TEXT_DIRECTION_RTL);
        page.addView(label);
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

    private LinearLayout verticalContainer() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return container;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
