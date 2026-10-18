package com.arazgray.neftlix;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * صفحهٔ جزئیات فیلم/سریال: راست‌چین و فارسی.
 *
 * بالای صفحه: هنر پس‌زمینه، پوستر و عنوان با دکمهٔ «تماشا».
 * پایین صفحه دو ستون است: راست توضیحات، چپ فهرست پخش. فصل‌ها، دسته‌ها و
 * کیفیت‌ها پیش‌فرض جمع‌شده‌اند و با OK باز می‌شوند؛ لینک‌های هر کیفیت در شبکهٔ
 * چندستونی (قسمت‌ها) نمایش داده می‌شوند و مستقیم پخش می‌شوند.
 */
public class DetailsActivity extends AppCompatActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CatalogRepository repository = new CatalogRepository();
    private Ui ui;
    private LinearLayout page;
    private TextView status;
    private ImageView backdropView;
    private FrameLayout loadingOverlay;
    private TextView loadingLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLogger.install(this);
        ui = new Ui(this);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        String url = getIntent().getStringExtra(EXTRA_URL);
        final String fallbackTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "نشانی صفحه نامعتبر است", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout root = ui.column();

        LinearLayout toolbar = ui.row();
        toolbar.setPadding(ui.dp(Ui.PAGE_PAD), ui.dp(10), ui.dp(Ui.PAGE_PAD), ui.dp(4));

        TextView back = ui.ghostButton("بازگشت", R.drawable.ic_back);
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, ui.params(-2, ui.dp(42)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_transparent);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = ui.params(ui.dp(96), ui.dp(34));
        logoParams.setMarginStart(ui.dp(12));
        toolbar.addView(logo, logoParams);

        status = ui.text("", 13, Ui.TEXT_DIM);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams statusParams = ui.params(0, -2, 1f);
        statusParams.setMarginStart(ui.dp(14));
        toolbar.addView(status, statusParams);
        root.addView(toolbar, ui.params(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page = ui.column();
        page.setClipChildren(false);
        page.setPadding(0, ui.dp(4), 0, ui.dp(30));
        scroll.addView(page);
        root.addView(scroll, ui.params(-1, 0, 1f));

        backdropView = new ImageView(this);
        backdropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropView.setAlpha(0.42f);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.BG);
        frame.addView(backdropView, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(ui.scrim(GradientDrawable.Orientation.BOTTOM_TOP,
                        0xFF0A0A0C, 0xB30A0A0C, 0x400A0A0C, 0x000A0A0C),
                new FrameLayout.LayoutParams(-1, -1));
        frame.addView(ui.scrim(GradientDrawable.Orientation.TOP_BOTTOM,
                        0xD90A0A0C, 0x400A0A0C, 0x000A0A0C),
                new FrameLayout.LayoutParams(-1, -1, Gravity.TOP));
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        loadingOverlay = centeredOverlay();
        frame.addView(loadingOverlay, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);

        showLoading("در حال بارگذاری…");
        io.execute(() -> {
            try {
                final ItemDetails details = repository.loadItem(url);
                runOnUiThread(() -> {
                    try {
                        hideLoading();
                        if (details.backdropUrl != null && !details.backdropUrl.isEmpty()) {
                            ui.loadCover(backdropView, details.backdropUrl);
                        }
                        renderItem(details, fallbackTitle);
                    } catch (Throwable error) {
                        hideLoading();
                        status.setText("خطا در نمایش صفحه");
                        addSmallText(CrashLogger.describe(error));
                    }
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    hideLoading();
                    status.setText("خطا در بارگذاری صفحه");
                    addSmallText(CrashLogger.describe(e));
                });
            }
        });
    }

    private FrameLayout centeredOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xCC000000);
        overlay.setClickable(true);
        overlay.setFocusable(true);
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
        overlay.addView(card, new FrameLayout.LayoutParams(
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
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    // ── ساخت صفحه ────────────────────────────────────────────────────

    private void renderItem(ItemDetails details, String fallbackTitle) {
        page.removeAllViews();
        String rawTitle = details.title == null || details.title.isEmpty() ? fallbackTitle : details.title;
        String title = cleanTitle(rawTitle);
        status.setText(summaryOf(details));

        final LinearLayout downloadsColumn = ui.column();
        addHeader(title, details, downloadsColumn);

        if (details.groups.isEmpty() && details.story.isEmpty()) {
            page.addView(note("برای این عنوان اطلاعاتی پیدا نشد."));
            return;
        }

        // راست: توضیحات — چپ: فهرست پخش
        LinearLayout columns = ui.row();
        columns.setGravity(Gravity.TOP);
        LinearLayout infoColumn = ui.column();
        columns.addView(infoColumn, ui.params(0, -2, 1f));
        LinearLayout.LayoutParams downloadsParams = ui.params(0, -2, 1.15f);
        downloadsParams.setMarginStart(ui.dp(20));
        columns.addView(downloadsColumn, downloadsParams);
        page.addView(columns, ui.withTop(-1, -2, 4));

        addInfoPanel(infoColumn, details);
        addDownloadGroups(downloadsColumn, details);

        if (!details.comments.isEmpty()) {
            page.addView(ui.expandable("نظرات (" + Ui.fa(details.comments.size()) + ")",
                    commentsView(details), 0), ui.withTop(-1, -2, 10));
        }
        focusFirst(downloadsColumn);
    }

    /**
     * عنوان تمیز برای نمایش؛ منطق در {@link DisplayText} است تا تست واحد داشته باشد.
     */
    static String cleanTitle(String raw) {
        return DisplayText.cleanTitle(raw);
    }

    private String summaryOf(ItemDetails details) {
        StringBuilder summary = new StringBuilder();
        int links = countLinks(details);
        if (!details.imdbRating.isEmpty()) summary.append("IMDb ").append(details.imdbRating);
        if (!details.satisfaction.isEmpty()) {
            if (summary.length() > 0) summary.append(" · ");
            summary.append("رضایت ").append(details.satisfaction);
        }
        if (links > 0) {
            if (summary.length() > 0) summary.append(" · ");
            summary.append(Ui.fa(links)).append(" لینک پخش");
        }
        return summary.toString();
    }

    /** پوستر + عنوان + دکمه‌های اصلی صفحه. */
    private void addHeader(String title, ItemDetails details, LinearLayout downloadsColumn) {
        LinearLayout header = ui.row();
        header.setGravity(Gravity.TOP);

        if (details.coverUrl != null && !details.coverUrl.isEmpty()) {
            ImageView poster = new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackground(ui.rounded(Ui.SURFACE_2, Ui.RADIUS_CARD));
            try {
                poster.setClipToOutline(true);
            } catch (Exception ignored) {
            }
            ui.loadCover(poster, details.coverUrl);
            float posterWidth = 150f;
            header.addView(poster, ui.params(ui.dp(posterWidth), ui.dp(posterWidth * 4f / 3f)));
        }

        LinearLayout info = ui.column();
        info.setPadding(ui.dp(20), ui.dp(2), 0, 0);
        TextView titleView = ui.text(title, 23, Ui.TEXT, true);
        titleView.setMaxLines(3);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(titleView);

        LinearLayout tags = ui.row();
        tags.setPadding(0, ui.dp(12), 0, 0);
        boolean first = true;
        if (!details.imdbRating.isEmpty()) {
            tags.addView(ui.tagPill("IMDb ‏" + details.imdbRating + "/10", Ui.BRAND, Ui.BRAND, Ui.TEXT, true));
            first = false;
        }
        if (!details.satisfaction.isEmpty()) {
            first = addTag(tags, "رضایت " + details.satisfaction, first);
        }
        first = addTag(tags, Ui.faType(typeOf(details)), first);
        int links = countLinks(details);
        if (links > 0) {
            addTag(tags, Ui.fa(links) + " لینک پخش", first);
        }
        info.addView(tags);

        LinearLayout actions = ui.row();
        actions.setPadding(0, ui.dp(18), 0, 0);
        final MediaLink playable = firstLink(details);
        if (playable != null) {
            TextView play = ui.primaryButton("تماشا", R.drawable.ic_play);
            play.setTag("play");
            play.setOnClickListener(v -> play(playable));
            actions.addView(play, ui.params(-2, ui.dp(44)));
        }
        if (!details.groups.isEmpty()) {
            TextView watchList = ui.ghostButton("فهرست پخش", R.drawable.ic_play);
            LinearLayout.LayoutParams watchListButtonParams = ui.params(-2, ui.dp(44));
            if (playable != null) watchListButtonParams.setMarginStart(ui.dp(10));
            watchList.setOnClickListener(v -> focusFirst(downloadsColumn));
            actions.addView(watchList, watchListButtonParams);
        }
        info.addView(actions);

        header.addView(info, ui.params(0, -2, 1f));
        page.addView(header, ui.withTop(-1, -2, 2));
    }

    private boolean addTag(LinearLayout row, String label, boolean first) {
        TextView pill = ui.tagPill(label, Ui.GLASS, Ui.HAIRLINE, Ui.TEXT_DIM, false);
        LinearLayout.LayoutParams params = ui.params(-2, -2);
        if (!first) params.setMarginStart(ui.dp(8));
        row.addView(pill, params);
        return false;
    }

    private String typeOf(ItemDetails details) {
        for (DownloadGroup group : details.groups) {
            if (group.season) return "series";
        }
        return details.url != null && details.url.contains("/series/") ? "series" : "movie";
    }

    // ── ستون توضیحات ─────────────────────────────────────────────────

    private void addInfoPanel(LinearLayout parent, ItemDetails details) {
        LinearLayout panel = ui.panel();

        if (!details.genres.isEmpty() || !details.flags.isEmpty()) {
            LinearLayout genres = ui.row();
            genres.setGravity(Gravity.TOP);
            boolean first = true;
            for (String genre : details.genres) {
                TextView pill = ui.tagPill(genre, 0x00000000, Ui.BRAND, Ui.TEXT, false);
                LinearLayout.LayoutParams params = ui.params(-2, -2);
                if (!first) params.setMarginStart(ui.dp(8));
                genres.addView(pill, params);
                first = false;
            }
            panel.addView(genres);

            LinearLayout flags = ui.row();
            flags.setGravity(Gravity.TOP);
            flags.setPadding(0, ui.dp(8), 0, 0);
            first = true;
            for (String flag : details.flags) {
                TextView pill = ui.tagPill(flag, Ui.GLASS, Ui.HAIRLINE, Ui.TEXT_DIM, false);
                LinearLayout.LayoutParams params = ui.params(-2, -2);
                if (!first) params.setMarginStart(ui.dp(8));
                flags.addView(pill, params);
                first = false;
            }
            panel.addView(flags);
        }

        if (!details.story.isEmpty()) {
            TextView story = ui.text(details.story, 14, 0xFFE6E6EC);
            story.setLineSpacing(0, 1.5f);
            story.setTextDirection(View.TEXT_DIRECTION_RTL);
            panel.addView(story, ui.withTop(-1, -2, 14));
        }

        parent.addView(panel);
    }

    // ── فهرست پخش ──────────────────────────────────────────────────

    private void addDownloadGroups(LinearLayout parent, ItemDetails details) {
        for (DownloadGroup group : details.groups) {
            if (group.season) {
                LinearLayout seasonContent = ui.column();
                for (DownloadCategory category : group.categories) addCategory(seasonContent, category);
                String title = group.title
                        + (group.status == null || group.status.isEmpty() ? "" : "  ·  " + group.status);
                parent.addView(ui.expandable(title, seasonContent, 0));
            } else {
                for (DownloadCategory category : group.categories) addCategory(parent, category);
            }
        }
    }

    private void addCategory(LinearLayout parent, DownloadCategory category) {
        LinearLayout content = ui.column();
        for (DownloadOption option : category.options) {
            content.addView(ui.expandable(optionLabel(option), linkGrid(option), 2));
        }
        String title = category.title == null || category.title.isEmpty() ? "پخش" : category.title;
        parent.addView(ui.expandable(title, content, 1));
    }

    private String optionLabel(DownloadOption option) {
        StringBuilder label = new StringBuilder();
        if (option.quality != null && !option.quality.isEmpty()) label.append(option.quality);
        if (option.encoder != null && !option.encoder.isEmpty()) {
            if (label.length() > 0) label.append("  ·  ");
            label.append(option.encoder);
        }
        if (label.length() == 0) label.append("پخش");
        label.append("  ·  ").append(Ui.fa(option.links.size())).append(" لینک");
        return label.toString();
    }

    /** لینک‌های یک کیفیت، سه ستون در صفحه. */
    private View linkGrid(DownloadOption option) {
        int columns = 3;
        LinearLayout container = ui.column();
        container.setPadding(0, ui.dp(8), 0, ui.dp(2));
        List<View> rowCards = new ArrayList<>();
        for (final MediaLink link : option.links) {
            // برچسب سایت («دانلود …») به زبان تماشا برگردانده می‌شود؛ لینک مستقیم پخش می‌شود
            String label = link.label == null ? "" : link.label.replace("دانلود", "تماشا").trim();
            if (label.isEmpty()) label = "تماشا";
            TextView button = ui.gridButton(label, R.drawable.ic_play);
            button.setOnClickListener(v -> play(link));
            rowCards.add(button);
            if (rowCards.size() == columns) {
                container.addView(linkRow(rowCards, columns));
                rowCards = new ArrayList<>();
            }
        }
        if (!rowCards.isEmpty()) container.addView(linkRow(rowCards, columns));
        return container;
    }

    private LinearLayout linkRow(List<View> buttons, int columns) {
        List<View> cells = new ArrayList<>(buttons);
        while (cells.size() < columns) {
            // جای خالی تا دکمه‌های ردیف آخر هم‌اندازهٔ بقیه بمانند
            cells.add(new View(this));
        }
        LinearLayout row = ui.gridRow(cells);
        LinearLayout.LayoutParams params = ui.params(-1, -2);
        params.bottomMargin = ui.dp(8);
        params.setMarginStart(ui.dp(8));
        row.setLayoutParams(params);
        return row;
    }

    private void play(MediaLink link) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_URL, link.url);
        intent.putExtra(PlayerActivity.EXTRA_TITLE, link.label);
        startActivity(intent);
    }

    private MediaLink firstLink(ItemDetails details) {
        for (DownloadGroup group : details.groups) {
            for (DownloadCategory category : group.categories) {
                for (DownloadOption option : category.options) {
                    if (!option.links.isEmpty()) return option.links.get(0);
                }
            }
        }
        return null;
    }

    // ── نظرات ────────────────────────────────────────────────────────

    private View commentsView(ItemDetails details) {
        LinearLayout container = ui.column();
        container.setPadding(0, ui.dp(4), 0, 0);
        for (CommentItem comment : details.comments) {
            LinearLayout card = ui.column();
            card.setBackground(ui.stroked(Ui.SURFACE, Ui.RADIUS_CARD, 1, 0x14FFFFFF));
            card.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(14));
            LinearLayout.LayoutParams cardParams = ui.params(-1, -2);
            cardParams.bottomMargin = ui.dp(10);
            card.setLayoutParams(cardParams);

            String who = comment.author.isEmpty() ? "کاربر" : comment.author;
            TextView head = ui.text(who + (comment.time.isEmpty() ? "" : "  ·  " + comment.time),
                    12, Ui.BRAND, true);
            card.addView(head);

            TextView body = ui.text(comment.text, 14, Ui.TEXT_DIM);
            body.setLineSpacing(0, 1.35f);
            card.addView(body, ui.withTop(-1, -2, 6));
            container.addView(card);
        }
        return container;
    }

    private int countLinks(ItemDetails details) {
        int n = 0;
        for (DownloadGroup g : details.groups)
            for (DownloadCategory c : g.categories)
                for (DownloadOption o : c.options) n += o.links.size();
        return n;
    }

    private TextView note(String text) {
        TextView view = ui.dim(text, 14);
        view.setPadding(ui.dp(Ui.PAGE_PAD), ui.dp(8), ui.dp(Ui.PAGE_PAD), ui.dp(12));
        return view;
    }

    private void addSmallText(String text) {
        page.addView(note(text == null ? "" : text));
    }

    /** فوکوس اولیه: دکمهٔ تماشا، وگرنه اولین گروه پخش. */
    private void focusFirst(final LinearLayout downloadsColumn) {
        page.post(() -> {
            View play = findTagged(page, "play");
            if (play != null) {
                play.requestFocus();
                return;
            }
            if (downloadsColumn.getChildCount() > 0) downloadsColumn.getChildAt(0).requestFocus();
        });
    }

    private View findTagged(View root, String tag) {
        if (tag.equals(root.getTag()) && root.isFocusable()) return root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTagged(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
