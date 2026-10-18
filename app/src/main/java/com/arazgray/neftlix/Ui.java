package com.arazgray.neftlix;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.regex.Pattern;

/**
 * سیستم طراحی برنامه: رنگ‌ها، فاصله‌ها و ویجت‌های آمادهٔ تلویزیون.
 *
 * همهٔ اندازه‌ها در فضای طراحی «۹۶۰dp» نوشته شده‌اند (اندازهٔ معمول یک تلویزیون
 * ۱۰۸۰p) و با ضریب {@link #scale} به هر رزولوشن/چگالی‌ای که دستگاه گزارش کند
 * نگاشت می‌شوند؛ نتیجه این‌که ظاهر برنامه روی ۷۲۰p، ۱۰۸۰p و ۴K یکسان است.
 */
final class Ui {
    // ── پالت ──────────────────────────────────────────────────────────
    static final int BG = 0xFF0A0A0C;
    static final int BRAND = 0xFFE50914;
    static final int SURFACE = 0xFF14141A;
    static final int SURFACE_2 = 0xFF1D1D24;
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT_DIM = 0xFFB3B3BC;
    static final int TEXT_MUTED = 0xFF8A8A93;
    static final int GLASS = 0x14FFFFFF;
    static final int GLASS_STRONG = 0x24FFFFFF;
    static final int HAIRLINE = 0x1FFFFFFF;
    /** قرص وضعیت: نیمی‌شفاف از SURFACE_2 تا پس‌زمینه از زیرش دیده شود. */
    static final int STATUS_BG = 0x801D1D24;
    /** پنل توضیحات: شیشه‌ای و نیمه‌شفاف به‌جای صفحهٔ مات. */
    static final int PANEL_BG = 0x9914161C;
    static final int SCRIM_TOP = 0xE60A0A0C;
    static final int SCRIM_MID = 0x8C0A0A0C;

    // ── اندازه‌های چیدمان (بر حسب dp طراحی) ───────────────────────────
    static final float PAGE_PAD = 22f;
    static final float CARD_W = 136f;
    static final float CARD_GAP = 12f;
    static final float RADIUS_CARD = 10f;
    static final float RADIUS_BUTTON = 12f;
    static final float RADIUS_PILL = 999f;

    private static final Pattern SIZED_IMAGE = Pattern.compile(
            "-(\\d{2,4})x(\\d{2,4})(\\.(?:jpe?g|jpeg|png|webp|avif))$", Pattern.CASE_INSENSITIVE);

    final Activity activity;
    final float scale;
    private final float density;
    private final Drawable placeholder;

    Ui(Activity activity) {
        this.activity = activity;
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        this.density = Math.max(1f, metrics.density);
        float widthDp = metrics.widthPixels / this.density;
        this.scale = scaleForWidth(widthDp);
        this.placeholder = new android.graphics.drawable.ColorDrawable(SURFACE_2);
    }

    int dp(float value) {
        return Math.round(value * scale * density);
    }

    /** اندازهٔ متن در فضای طراحی (sp طراحی). */
    private float sp(float value) {
        return value * scale;
    }

    int screenWidthDp() {
        return Math.round(activity.getResources().getDisplayMetrics().widthPixels / density);
    }

    // ── متن ──────────────────────────────────────────────────────────
    TextView text(String value, float sizeSp, int color) {
        return text(value, sizeSp, color, false);
    }

    TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value == null ? "" : value);
        applyText(view, sizeSp, color, bold);
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return view;
    }

    void applyText(TextView view, float sizeSp, int color, boolean bold) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(sizeSp));
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
    }

    TextView heading(String value) {
        TextView view = text(value, 19, TEXT, true);
        view.setMaxLines(1);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    TextView dim(String value, float sizeSp) {
        return text(value, sizeSp, TEXT_MUTED);
    }

    // ── ظرف‌ها ────────────────────────────────────────────────────────
    LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return layout;
    }

    LinearLayout row() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    LinearLayout.LayoutParams params(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    LinearLayout.LayoutParams withTop(int width, int height, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.topMargin = dp(topDp);
        return lp;
    }

    void space(LinearLayout parent, float heightDp) {
        parent.addView(new View(activity), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    /** خط جداکنندهٔ مویی. */
    View divider() {
        View view = new View(activity);
        view.setBackgroundColor(HAIRLINE);
        return view;
    }

    // ── شکل‌ها ───────────────────────────────────────────────────────
    GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    GradientDrawable stroked(int color, float radiusDp, float strokeDp, int strokeColor) {
        GradientDrawable drawable = rounded(color, radiusDp);
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    static GradientDrawable gradient(int[] colors, GradientDrawable.Orientation orientation) {
        GradientDrawable drawable = new GradientDrawable(orientation, colors);
        drawable.setShape(GradientDrawable.RECTANGLE);
        return drawable;
    }

    /** پوشش تیرهٔ روی تصویر؛ سمت راست (محل متن فارسی) تیره‌تر است. */
    View scrim(GradientDrawable.Orientation orientation, int... colors) {
        View view = new View(activity);
        view.setBackground(gradient(colors, orientation));
        return view;
    }

    // ── آیکن‌ها ───────────────────────────────────────────────────────
    /** آیکن برداری با اندازهٔ دلخواه (برای drawable ترکیبی TextView). */
    Drawable icon(int res, float sizeDp) {
        Drawable base = androidx.core.content.ContextCompat.getDrawable(activity, res);
        if (base == null) return null;
        final int size = dp(sizeDp);
        final Drawable inner = base;
        return new Drawable() {
            @Override
            public void draw(android.graphics.Canvas canvas) {
                inner.setBounds(getBounds());
                inner.draw(canvas);
            }

            @Override
            public void setAlpha(int alpha) {
                inner.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter filter) {
                inner.setColorFilter(filter);
            }

            @Override
            public void setTintList(android.content.res.ColorStateList tint) {
                inner.setTintList(tint);
            }

            @Override
            public int getOpacity() {
                return inner.getOpacity();
            }

            @Override
            public int getIntrinsicWidth() {
                return size;
            }

            @Override
            public int getIntrinsicHeight() {
                return size;
            }
        };
    }

    ImageView iconView(int res, float sizeDp) {
        ImageView view = new ImageView(activity);
        view.setImageResource(res);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return view;
    }

    // ── دکمه‌ها ──────────────────────────────────────────────────────
    /**
     * دکمهٔ قرصی. حالت عادی شیشه‌ای و حالت فوکوس قرمز برند است؛ برای دکمه‌های
     * اصلی (primary) فوکوس سفید می‌شود تا روی ریموت کاملاً واضح باشد.
     */
    TextView pillButton(String label, int iconRes, boolean primary, float heightDp, float sizeSp) {
        TextView view = text(label, sizeSp, TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setPadding(dp(16), 0, dp(16), 0);
        if (iconRes != 0) {
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(iconRes, 17), null, null, null);
            view.setCompoundDrawablePadding(dp(7));
        }
        fillFocus(view, primary, heightDp);
        return view;
    }

    TextView ghostButton(String label, int iconRes) {
        return pillButton(label, iconRes, false, 40, 13);
    }

    TextView primaryButton(String label, int iconRes) {
        return pillButton(label, iconRes, true, 44, 14);
    }

    /** دکمهٔ مربعی فقط-آیکن (نوار بالا)؛ آیکن با ImageView دقیقاً وسط قاب است. */
    FrameLayout iconButton(int iconRes, String contentDesc) {
        FrameLayout box = new FrameLayout(activity);
        box.setFocusable(true);
        box.setClickable(true);
        box.setContentDescription(contentDesc);
        ImageView image = new ImageView(activity);
        image.setImageDrawable(icon(iconRes, 19));
        image.setScaleType(ImageView.ScaleType.CENTER);
        image.setDuplicateParentStateEnabled(true);
        box.addView(image, new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER));
        // بدون بزرگ‌نمایی فوکوس؛ فقط پس‌زمینه عوض می‌شود تا دایره سالم بماند
        final GradientDrawable idle = stroked(GLASS, 21, 1, HAIRLINE);
        final GradientDrawable focused = rounded(BRAND, 21);
        box.setBackground(idle);
        box.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(hasFocus ? focused : idle));
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return box;
    }

    /** تراشهٔ ژانر/فیلتر. */
    TextView chip(String label, String countText) {
        TextView view = text(label, 13, TEXT, false);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(15), 0, dp(15), 0);
        if (countText != null && !countText.isEmpty()) {
            android.text.SpannableString span = new android.text.SpannableString(label + "  " + countText);
            span.setSpan(new android.text.style.ForegroundColorSpan(TEXT_MUTED),
                    label.length(), span.length(), 0);
            view.setText(span);
        }
        fillFocus(view, false, 34);
        return view;
    }

    /** دکمهٔ کوچک بخش‌های تاشو (کیفیت/زیرنویس). */
    TextView rowButton(String label, int iconRes, boolean strong) {
        TextView view = text(label, strong ? 15 : 13, TEXT, strong);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setPadding(dp(strong ? 14 : 12), 0, dp(12), 0);
        if (iconRes != 0) {
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(iconRes, 16), null, null, null);
            view.setCompoundDrawablePadding(dp(8));
        }
        fillFocus(view, false, strong ? 44 : 38, strong ? RADIUS_BUTTON : 8);
        return view;
    }

    /** دکمهٔ قسمت‌ها در شبکهٔ لینک‌های دانلود. */
    TextView gridButton(String label, int iconRes) {
        TextView view = text(label, 13, TEXT, false);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setPadding(dp(10), 0, dp(10), 0);
        if (iconRes != 0) {
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(iconRes, 15), null, null, null);
            view.setCompoundDrawablePadding(dp(7));
        }
        fillFocus(view, false, 38, 8);
        return view;
    }

    /**
     * رنگ دکمه‌ها را با فوکوس عوض می‌کند؛ اندازه ثابت می‌ماند تا دکمهٔ فوکوس‌شده
     * از قاب خودش بیرون نزند و گوشه‌های گرد خراب نشود.
     */
    void fillFocus(final TextView view, final boolean primary, final float heightDp) {        fillFocus(view, primary, heightDp, RADIUS_PILL);
    }

    void fillFocus(final TextView view, final boolean primary, final float heightDp, final float radiusDp) {
        final float radius = radiusDp >= 999 ? heightDp / 2f : radiusDp;
        final GradientDrawable idle = primary
                ? rounded(BRAND, radius)
                : stroked(GLASS, radius, 1, HAIRLINE);
        final GradientDrawable focused = primary
                ? rounded(TEXT, radius)
                : rounded(BRAND, radius);
        view.setBackground(idle);
        view.setTextColor(primary ? TEXT : TEXT);
        view.setFocusable(true);
        view.setClickable(true);
        view.setMinimumHeight(dp(heightDp));
        view.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(hasFocus ? focused : idle);
            if (primary) {
                view.setTextColor(hasFocus ? 0xFF0A0A0C : TEXT);
                tintDrawable(view, hasFocus ? 0xFF0A0A0C : TEXT);
            } else {
                view.setTextColor(TEXT);
            }
        });
    }

    private void tintDrawable(TextView view, int color) {
        Drawable[] drawables = view.getCompoundDrawablesRelative();
        for (Drawable drawable : drawables) {
            if (drawable != null) {
                drawable.setTint(color);
            }
        }
    }

    // ── سرتیتر بخش + «مشاهده همه» ────────────────────────────────────
    LinearLayout sectionHeader(String title, String countText, TextView action) {
        LinearLayout header = row();
        header.setPadding(dp(PAGE_PAD), dp(18), dp(PAGE_PAD), dp(8));
        View accent = new View(activity);
        accent.setBackground(rounded(BRAND, 2));
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(3), dp(16));
        accentParams.setMarginEnd(dp(9));
        header.addView(accent, accentParams);
        header.addView(heading(title), new LinearLayout.LayoutParams(-2, -2));
        if (countText != null && !countText.isEmpty()) {
            TextView count = dim(countText, 12);
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-2, -2);
            countParams.setMarginStart(dp(8));
            header.addView(count, countParams);
        }
        header.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        if (action != null) header.addView(action, new LinearLayout.LayoutParams(-2, -2));
        return header;
    }

    // ── ریل افقی کارت‌ها ─────────────────────────────────────────────
    /**
     * ریل افقی؛ با جابه‌جایی فوکوس، کارت فعال به وسط دید می‌آید.
     * کارت‌ها در RTL از راست به چپ چیده می‌شوند.
     */
    HorizontalScrollView rail(List<View> cards) {
        LinearLayout content = row();
        content.setClipChildren(false);
        content.setPadding(dp(PAGE_PAD), dp(10), dp(PAGE_PAD), dp(12));
        for (int i = 0; i < cards.size(); i++) {
            View card = cards.get(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(CARD_W), -2);
            if (i < cards.size() - 1) lp.setMarginEnd(dp(CARD_GAP));
            content.addView(card, lp);
        }
        HorizontalScrollView scroller = new HorizontalScrollView(activity);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.addView(content, new FrameLayout.LayoutParams(-2, -2));
        for (View card : cards) {
            final View focused = card;
            final HorizontalScrollView target = scroller;
            View.OnFocusChangeListener existing = card.getOnFocusChangeListener();
            card.setOnFocusChangeListener((v, hasFocus) -> {
                if (existing != null) existing.onFocusChange(v, hasFocus);
                if (hasFocus) reveal(target, focused);
            });
        }
        return scroller;
    }

    void reveal(final HorizontalScrollView scroller, final View card) {
        scroller.post(() -> {
            int viewport = scroller.getWidth();
            if (viewport <= 0 || card.getWidth() <= 0) return;
            int target = card.getLeft() - (viewport - card.getWidth()) / 2;
            int content = scroller.getChildCount() > 0 ? scroller.getChildAt(0).getWidth() : 0;
            int max = Math.max(0, content - viewport);
            scroller.smoothScrollTo(Math.max(0, Math.min(target, max)), 0);
        });
    }

    // ── کارت پوستر ───────────────────────────────────────────────────
    /** کارت فیلم/سریال: پوستر ۲:۳، برچسب نوع، عنوان و امتیاز. */
    View posterCard(final BrowseItem item, float widthDp, boolean showMeta) {
        final int posterHeight = Math.round(dp(widthDp) * 1.5f);
        LinearLayout card = column();
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipChildren(false);
        card.setContentDescription(item.title);

        FrameLayout box = new FrameLayout(activity);
        final ImageView poster = new ImageView(activity);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(rounded(SURFACE_2, RADIUS_CARD));
        try {
            poster.setClipToOutline(true);
        } catch (Exception ignored) {
        }
        box.addView(poster, new FrameLayout.LayoutParams(-1, -1));
        String badge = faType(item.contentType);
        if (badge != null) {
            TextView tag = text(badge, 10, TEXT, true);
            tag.setGravity(Gravity.CENTER);
            tag.setBackground(stroked(0xB3000000, RADIUS_PILL, 1, HAIRLINE));
            tag.setPadding(dp(8), dp(2), dp(8), dp(2));
            FrameLayout.LayoutParams tagParams = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | Gravity.START);
            tagParams.setMargins(dp(7), dp(7), dp(7), 0);
            box.addView(tag, tagParams);
        }
        final View border = new View(activity);
        border.setBackground(stroked(0x00000000, RADIUS_CARD, 3, BRAND));
        border.setVisibility(View.INVISIBLE);
        box.addView(border, new FrameLayout.LayoutParams(-1, -1));
        card.addView(box, new LinearLayout.LayoutParams(-1, posterHeight));

        final TextView title = text(item.title, 13, 0xFFE9E9EE, false);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(7);
        card.addView(title, titleParams);

        final TextView meta = showMeta && item.meta != null && !item.meta.isEmpty()
                ? text(item.meta, 11, TEXT_MUTED, false) : null;
        if (meta != null) {
            meta.setMaxLines(1);
            meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
            metaParams.topMargin = dp(3);
            card.addView(meta, metaParams);
        }

        card.setOnFocusChangeListener((v, hasFocus) -> {
            // بدون تغییر اندازه: قاب قرمز و بولد شدن عنوان، فوکوس را نشان می‌دهد
            border.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
            applyText(title, 13, hasFocus ? TEXT : 0xFFE9E9EE, hasFocus);
            if (hasFocus) upgradePoster(poster, item.coverUrl);
        });
        loadCover(poster, item.coverUrl);
        return card;
    }

    /** کارت‌های یک ردیف با عرض مساوی (صفحهٔ آرشیو و نتایج جست‌وجو). */
    LinearLayout gridRow(List<View> cards) {
        LinearLayout row = row();
        row.setClipChildren(false);
        row.setGravity(Gravity.TOP);
        for (int i = 0; i < cards.size(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i < cards.size() - 1) lp.setMarginEnd(dp(CARD_GAP));
            row.addView(cards.get(i), lp);
        }
        return row;
    }

    int gridColumns() {
        return gridColumnsFor(PAGE_PAD, CARD_W, CARD_GAP);
    }

    /** تعداد ستون شبکه فقط از روی ثابت‌های طراحی؛ بدون نیاز به نمونه. */
    static int gridColumnsFor(float pagePad, float cardW, float cardGap) {
        float available = 960 - pagePad * 2;
        return Math.max(3, (int) ((available + cardGap) / (cardW + cardGap)));
    }

    /** عرض هر کارت در شبکه (dp طراحی) تا نسبت تصویر درست بماند. */
    float gridCardWidthDp(int columns) {
        return gridCardWidthDpFor(columns, PAGE_PAD, CARD_GAP);
    }

    /** نسخهٔ خالص محاسبهٔ عرض کارت برای تست واحد. */
    static float gridCardWidthDpFor(int columns, float pagePad, float cardGap) {
        float available = 960 - pagePad * 2 - cardGap * (columns - 1);
        return available / columns;
    }

    /** ضریب مقیاس فضای طراحی ۹۶۰dp؛ بدون نیاز به نمونه برای تست واحد. */
    static float scaleForWidth(float widthDp) {
        return Math.max(0.75f, Math.min(widthDp / 960f, 2.4f));
    }

    // ── بخش تاشو (فصل‌ها، کیفیت‌ها، نظرات) ───────────────────────────
    /**
     * یک بخش تاشو. تا زمانی که باز نشده، محتوا اندازه‌گیری نمی‌شود؛ پس
     * صفحه‌های سنگین (۳۵ کیفیت × ۹ قسمت) بدون هزینه ساخته می‌شوند.
     */
    View expandable(String title, View content) {
        return expandable(title, content, 0);
    }

    /**
     * @param depth ۰ = فصل، ۱ = دسته (زیرنویس/دوبله)، ۲ = کیفیت
     */
    View expandable(final String title, final View content, final int depth) {
        LinearLayout box = column();
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(-1, -2);
        boxParams.bottomMargin = dp(12);
        boxParams.setMarginStart(dp(depth * 16));
        box.setLayoutParams(boxParams);

        final float sizeSp = depth == 0 ? 15 : (depth == 1 ? 14 : 13);
        final TextView summary = text(title, sizeSp, depth == 0 ? TEXT : 0xFFE4E4EA, depth == 0);
        summary.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        summary.setPadding(dp(14), dp(depth == 0 ? 13 : 11), dp(14), dp(depth == 0 ? 13 : 11));
        summary.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_chevron_end, 15), null, null, null);
        summary.setCompoundDrawablePadding(dp(9));
        summary.setFocusable(true);
        summary.setClickable(true);
        final GradientDrawable idle = depth == 0
                ? stroked(SURFACE_2, RADIUS_BUTTON, 1, HAIRLINE)
                : stroked(0x0DFFFFFF, RADIUS_BUTTON, 1, 0x14FFFFFF);
        final GradientDrawable focused = rounded(BRAND, RADIUS_BUTTON);
        summary.setBackground(idle);
        summary.setOnFocusChangeListener((v, hasFocus) -> {
            summary.setBackground(hasFocus ? focused : idle);
            summary.setTextColor(depth == 0 || hasFocus ? TEXT : 0xFFE4E4EA);
            tintDrawable(summary, TEXT);
        });
        content.setVisibility(View.GONE);
        // فاصلهٔ تنفسی بین سربرگ و محتوای بازشده
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, -2);
        contentParams.topMargin = dp(6);
        content.setLayoutParams(contentParams);
        box.addView(summary);
        box.addView(content);
        summary.setOnClickListener(v -> {
            boolean expand = content.getVisibility() != View.VISIBLE;
            content.setVisibility(expand ? View.VISIBLE : View.GONE);
            summary.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    icon(expand ? R.drawable.ic_chevron_down : R.drawable.ic_chevron_end, 15), null, null, null);
        });
        return box;
    }

    // ── پنل ──────────────────────────────────────────────────────────
    LinearLayout panel() {
        LinearLayout panel = column();
        panel.setBackground(stroked(PANEL_BG, RADIUS_CARD, 1, 0x26FFFFFF));
        int pad = dp(18);
        panel.setPadding(pad, pad, pad, pad);
        return panel;
    }

    TextView tagPill(String label, int fill, int stroke, int color, boolean bold) {
        TextView view = text(label, 12, color, bold);
        view.setGravity(Gravity.CENTER);
        view.setBackground(stroked(fill, RADIUS_PILL, 1, stroke));
        view.setPadding(dp(12), dp(4), dp(12), dp(4));
        return view;
    }

    /** نام فارسی نوع محتوا برای برچسب روی پوستر. */
    static String faType(String type) {
        if ("series".equalsIgnoreCase(type)) return "سریال";
        if ("movie".equalsIgnoreCase(type)) return "فیلم";
        return "فیلم و سریال";
    }

    /** عدد با ارقام فارسی. */
    static String fa(Object value) {
        return String.valueOf(value).replace('0', '۰').replace('1', '۱').replace('2', '۲')
                .replace('3', '۳').replace('4', '۴').replace('5', '۵')
                .replace('6', '۶').replace('7', '۷').replace('8', '۸').replace('9', '۹');
    }

    // ── بارگذاری تصویر ───────────────────────────────────────────────
    void loadCover(final ImageView view, String url) {
        if (url == null || url.isEmpty()) return;
        Glide.with(activity).load(url).centerCrop()
                .placeholder(placeholder)
                .error(placeholder)
                .into(view);
    }

    /** هنر افقی بیلبورد که دیرهنگام می‌رسد؛ نرم محو می‌شود. */
    void loadBackdrop(final ImageView view, String url) {
        if (url == null || url.isEmpty()) return;
        Glide.with(activity).load(url).centerCrop()
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(450))
                .into(view);
    }

    /**
     * کارت‌های ریل با نسخهٔ کوچک تصویر (۱۸۰×۲۸۰) ساخته می‌شوند تا فهرست سریع
     * بیاید؛ وقتی کارتی فوکوس شد نسخهٔ بزرگ‌تر و شارپ‌تر همان تصویر بارگذاری
     * می‌شود (تصویر فعلی تا رسیدن آن دیده می‌ماند).
     */
    void upgradePoster(final ImageView view, String url) {
        final String large = hiRes(url);
        if (large.equals(url) || large.isEmpty()) return;
        Glide.with(activity).load(large).centerCrop()
                .placeholder(view.getDrawable())
                .error(view.getDrawable())
                .dontAnimate()
                .into(view);
    }

    /** نسخهٔ کامل تصویر بدون پسوند اندازه (`-180x280`) در صورت وجود. */
    static String hiRes(String url) {
        if (url == null) return "";
        return SIZED_IMAGE.matcher(url).replaceFirst("$3");
    }
}
