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

final class Ui {
    
    static final int BG = 0xFF0A0A0C;
    static final int BRAND_DEFAULT = 0xFFE50914;
    static final int SURFACE = 0xFF14141A;
    static final int SURFACE_2 = 0xFF1D1D24;
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT_DIM = 0xFFB3B3BC;
    static final int TEXT_MUTED = 0xFF8A8A93;
    static final int GLASS = 0x14FFFFFF;
    static final int GLASS_STRONG = 0x24FFFFFF;
    static final int HAIRLINE = 0x1FFFFFFF;
    
    static final int STATUS_BG = 0x801D1D24;
    
    static final int PANEL_BG = 0x9914161C;
    static final int SCRIM_TOP = 0xE60A0A0C;
    static final int SCRIM_MID = 0x8C0A0A0C;

    static final float PAGE_PAD = 22f;
    static final float CARD_W = 136f;
    static final float CARD_GAP = 12f;
    static final float RADIUS_CARD = 10f;
    
    static final float FOCUS_SCALE = 1.15f;
    static final float FOCUS_STROKE = 3f;
    static final float RADIUS_BUTTON = 12f;
    static final float RADIUS_PILL = 999f;

    private static final Pattern SIZED_IMAGE = Pattern.compile(
            "-(\\d{2,4})x(\\d{2,4})(\\.(?:jpe?g|jpeg|png|webp|avif))$", Pattern.CASE_INSENSITIVE);

    final Activity activity;
    final float scale;
    final android.graphics.Typeface font;

    final int brand;
    final boolean persian;
    private final float density;
    private final Drawable placeholder;

    Ui(Activity activity) {
        this.activity = activity;
        this.brand = SettingsStore.getAccentColor(activity);
        this.persian = SettingsStore.isPersian(activity);
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        this.density = Math.max(1f, metrics.density);
        float widthDp = metrics.widthPixels / this.density;
        this.scale = scaleForWidth(widthDp);
        this.placeholder = new android.graphics.drawable.ColorDrawable(SURFACE_2);
        android.graphics.Typeface loaded = android.graphics.Typeface.DEFAULT;
        try {
            android.graphics.Typeface family =
                    androidx.core.content.res.ResourcesCompat.getFont(activity, R.font.vazirmatn);
            if (family != null) loaded = family;
        } catch (Exception ignored) {
        }
        this.font = loaded;
    }

    int dp(float value) {
        return Math.round(value * scale * density);
    }

    private float sp(float value) {
        return value * scale;
    }

    String siteTitle(String title) {
        if (title == null) return "";
        return persian ? title : CatalogLabels.english(title);
    }

    String metaText(BrowseItem item) {
        if (item == null || item.meta == null) return "";
        return persian ? item.meta : CatalogLabels.englishMeta(item.meta);
    }

    interface FocusListener {
        void onCardFocused(BrowseItem item, View card);
    }

    private FocusListener focusListener;

    void setCardFocusListener(FocusListener listener) {
        focusListener = listener;
    }

    static void hoverFocus(View v) {
        v.setOnHoverListener((view, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_HOVER_ENTER) view.requestFocus();
            return false;
        });
    }

    int screenWidthDp() {
        return Math.round(activity.getResources().getDisplayMetrics().widthPixels / density);
    }

    TextView text(String value, float sizeSp, int color) {
        return text(value, sizeSp, color, false);
    }

    TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value == null ? "" : value);
        applyText(view, sizeSp, color, bold);
        return view;
    }

    String digits(Object value) {
        return persian ? fa(value) : String.valueOf(value);
    }

    String typeName(String type) {
        if ("series".equalsIgnoreCase(type)) return activity.getString(R.string.type_series);
        if ("movie".equalsIgnoreCase(type)) return activity.getString(R.string.type_movie);
        return activity.getString(R.string.type_mixed);
    }

    void applyText(TextView view, float sizeSp, int color, boolean bold) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(sizeSp));
        view.setTextColor(color);
        view.setTypeface(font, bold ? Typeface.BOLD : Typeface.NORMAL);
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

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout row() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
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

    View divider() {
        View view = new View(activity);
        view.setBackgroundColor(HAIRLINE);
        return view;
    }

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

    View scrim(GradientDrawable.Orientation orientation, int... colors) {
        View view = new View(activity);
        view.setBackground(gradient(colors, orientation));
        return view;
    }

    Drawable icon(int res, float sizeDp) {
        Drawable base = androidx.core.content.ContextCompat.getDrawable(activity, res);
        if (base == null) return null;
        if (res == R.drawable.ic_back || res == R.drawable.ic_chevron_end) {
            base.setAutoMirrored(true);
            base.setLayoutDirection(
                    activity.getResources().getConfiguration().getLayoutDirection());
        }
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
                
                invalidateSelf();
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

    FrameLayout iconButton(int iconRes, String contentDesc) {
        FrameLayout box = new FrameLayout(activity);
        box.setFocusable(true);
        box.setClickable(true);
        box.setFocusableInTouchMode(true);
        box.setContentDescription(contentDesc);
        ImageView image = new ImageView(activity);
        image.setImageDrawable(icon(iconRes, 19));
        image.setScaleType(ImageView.ScaleType.CENTER);
        image.setDuplicateParentStateEnabled(true);
        box.addView(image, new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER));
        
        final GradientDrawable idle = stroked(GLASS, 21, 1, HAIRLINE);
        final GradientDrawable focused = rounded(brand, 21);
        box.setBackground(idle);
        box.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(hasFocus ? focused : idle));
        hoverFocus(box);
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return box;
    }

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

    void fillFocus(final TextView view, final boolean primary, final float heightDp) {        fillFocus(view, primary, heightDp, RADIUS_PILL);
    }

    void fillFocus(final TextView view, final boolean primary, final float heightDp, final float radiusDp) {
        final float radius = radiusDp >= 999 ? heightDp / 2f : radiusDp;
        final GradientDrawable idle = primary
                ? rounded(brand, radius)
                : stroked(GLASS, radius, 1, HAIRLINE);
        final GradientDrawable focused = primary
                ? rounded(TEXT, radius)
                : rounded(brand, radius);
        view.setBackground(idle);
        view.setTextColor(primary ? TEXT : TEXT);
        view.setFocusable(true);

        view.setFocusableInTouchMode(true);
        view.setClickable(true);
        hoverFocus(view);
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

    LinearLayout sectionHeader(String title, String countText, TextView action) {
        LinearLayout header = row();
        header.setPadding(dp(PAGE_PAD), dp(18), dp(PAGE_PAD), dp(8));
        View accent = new View(activity);
        accent.setBackground(rounded(brand, 2));
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(3), dp(16));
        accentParams.setMarginEnd(dp(9));
        header.addView(accent, accentParams);
        header.addView(heading(title), new LinearLayout.LayoutParams(-2, -2));
        if (action != null) {
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-2, -2);
            actionParams.setMarginStart(dp(10));
            header.addView(action, actionParams);
        }
        if (countText != null && !countText.isEmpty()) {
            TextView count = dim(countText, 12);
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-2, -2);
            countParams.setMarginStart(dp(8));
            header.addView(count, countParams);
        }
        return header;
    }

    HorizontalScrollView rail(List<View> cards) {
        LinearLayout content = row();
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(dp(PAGE_PAD), dp(20), dp(PAGE_PAD), dp(22));
        for (int i = 0; i < cards.size(); i++) {
            View card = cards.get(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(CARD_W), -2);
            if (i < cards.size() - 1) lp.setMarginEnd(dp(CARD_GAP));
            content.addView(card, lp);
        }
        HorizontalScrollView scroller = new HorizontalScrollView(activity) {
            @Override
            public void addFocusables(java.util.ArrayList<View> views, int direction, int focusableMode) {
                if (isVerticalEntry(direction)) {
                    View first = firstDirectFocusable(getChildCount() > 0 ? getChildAt(0) : null);
                    if (first != null) {
                        first.addFocusables(views, direction, focusableMode);
                        return;
                    }
                }
                super.addFocusables(views, direction, focusableMode);
            }
        };
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.addView(content, new FrameLayout.LayoutParams(-2, -2));
        
        scroller.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                scroller.removeOnLayoutChangeListener(this);
                alignRailStart(scroller);
            }
        });
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

    private static boolean isVerticalEntry(int direction) {
        return direction == View.FOCUS_UP || direction == View.FOCUS_DOWN;
    }

    private static View firstDirectFocusable(View container) {
        if (!(container instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup group = (android.view.ViewGroup) container;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.isFocusable()) return child;
        }
        return null;
    }

    private static void alignRailStart(HorizontalScrollView scroller) {
        if (scroller.getChildCount() == 0 || scroller.getWidth() <= 0) return;
        int max = Math.max(0, scroller.getChildAt(0).getWidth() - scroller.getWidth());
        if (scroller.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            scroller.scrollTo(max, 0);
        }
    }

    void reveal(final HorizontalScrollView scroller, final View card) {
        scroller.post(() -> {
            int viewport = scroller.getWidth();
            if (viewport <= 0 || card.getWidth() <= 0) return;
            int scroll = scroller.getScrollX();
            int left = card.getLeft();
            int right = left + card.getWidth();
            int pad = dp(8);
            int content = scroller.getChildCount() > 0 ? scroller.getChildAt(0).getWidth() : 0;
            int max = Math.max(0, content - viewport);
            if (left < scroll + pad) {
                scroller.smoothScrollTo(Math.max(0, left - pad), 0);
            } else if (right > scroll + viewport - pad) {
                scroller.smoothScrollTo(Math.min(max, right - viewport + pad), 0);
            }
        });
    }

    View posterCard(final BrowseItem item, float widthDp, boolean showMeta) {
        final int posterHeight = Math.round(dp(widthDp) * 1.5f);
        LinearLayout card = column();
        card.setFocusable(true);
        card.setClickable(true);
        card.setFocusableInTouchMode(true);
        card.setClipChildren(false);
        card.setContentDescription(item.title);

        FrameLayout box = new FrameLayout(activity);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        final ImageView poster = new ImageView(activity);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(rounded(SURFACE_2, RADIUS_CARD));
        try {
            poster.setClipToOutline(true);
        } catch (Exception ignored) {
        }
        box.addView(poster, new FrameLayout.LayoutParams(-1, -1));
        String badge = typeName(item.contentType);
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
        border.setBackground(stroked(0x00000000, RADIUS_CARD + FOCUS_STROKE, FOCUS_STROKE, brand));
        border.setVisibility(View.INVISIBLE);
        final int stroke = dp(FOCUS_STROKE);
        FrameLayout.LayoutParams borderParams = new FrameLayout.LayoutParams(-1, -1);
        borderParams.setMargins(-stroke, -stroke, -stroke, -stroke);
        box.addView(border, borderParams);
        card.addView(box, new LinearLayout.LayoutParams(-1, posterHeight));

        final TextView title = text(item.title, 13, 0xFFE9E9EE, false);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(7);
        card.addView(title, titleParams);

        final TextView meta = showMeta && item.meta != null && !item.meta.isEmpty()
                ? text(metaText(item), 11, TEXT_MUTED, false) : null;
        if (meta != null) {
            meta.setMaxLines(1);
            meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
            metaParams.topMargin = dp(3);
            card.addView(meta, metaParams);
        }

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            card.setDefaultFocusHighlightEnabled(false);
        }
        hoverFocus(card);
        card.setOnFocusChangeListener((v, hasFocus) -> {

            border.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
            applyText(title, 13, hasFocus ? TEXT : 0xFFE9E9EE, hasFocus);
            v.animate().cancel();
            float scale = hasFocus ? FOCUS_SCALE : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(160)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            v.setTranslationZ(hasFocus ? dp(12) : 0);
            if (hasFocus) upgradePoster(poster, item.coverUrl);
            if (hasFocus && focusListener != null) focusListener.onCardFocused(item, v);
        });
        loadCover(poster, item.coverUrl);
        return card;
    }

    LinearLayout gridRow(List<View> cards) {
        LinearLayout row = new LinearLayout(activity) {
            @Override
            public void addFocusables(java.util.ArrayList<View> views, int direction, int focusableMode) {
                if (isVerticalEntry(direction)) {
                    View first = firstDirectFocusable(this);
                    if (first != null) {
                        first.addFocusables(views, direction, focusableMode);
                        return;
                    }
                }
                super.addFocusables(views, direction, focusableMode);
            }
        };
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setClipChildren(false);
        row.setClipToPadding(false);
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

    static int gridColumnsFor(float pagePad, float cardW, float cardGap) {
        float available = 960 - pagePad * 2;
        return Math.max(3, (int) ((available + cardGap) / (cardW + cardGap)));
    }

    float gridCardWidthDp(int columns) {
        return gridCardWidthDpFor(columns, PAGE_PAD, CARD_GAP);
    }

    static float gridCardWidthDpFor(int columns, float pagePad, float cardGap) {
        float available = 960 - pagePad * 2 - cardGap * (columns - 1);
        return available / columns;
    }

    static float scaleForWidth(float widthDp) {
        return Math.max(0.75f, Math.min(widthDp / 960f, 2.4f));
    }

    View expandable(String title, View content) {
        return expandable(title, content, 0);
    }

    View expandable(String title, View content, int depth) {
        return expandable(title, content, depth, null);
    }

    View expandable(final String title, final View content, final int depth, final Runnable onToggle) {
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
        summary.setFocusableInTouchMode(true);
        hoverFocus(summary);
        final GradientDrawable idle = depth == 0
                ? stroked(SURFACE_2, RADIUS_BUTTON, 1, HAIRLINE)
                : stroked(0x0DFFFFFF, RADIUS_BUTTON, 1, 0x14FFFFFF);
        final GradientDrawable focused = rounded(brand, RADIUS_BUTTON);
        summary.setBackground(idle);
        summary.setOnFocusChangeListener((v, hasFocus) -> {
            summary.setBackground(hasFocus ? focused : idle);
            summary.setTextColor(depth == 0 || hasFocus ? TEXT : 0xFFE4E4EA);
            tintDrawable(summary, TEXT);
        });
        content.setVisibility(View.GONE);
        
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
            wireFocus(summary, content, expand);
            if (expand && onToggle != null) onToggle.run();
        });
        return box;
    }

    private void wireFocus(View summary, View content, boolean expand) {
        if (summary.getId() == View.NO_ID) summary.setId(View.generateViewId());
        View first = firstFocusable(content);
        if (first != null && first.getId() == View.NO_ID) first.setId(View.generateViewId());
        if (expand && first != null) {
            summary.setNextFocusDownId(first.getId());
            first.setNextFocusUpId(summary.getId());
        } else {
            summary.setNextFocusDownId(View.NO_ID);
            if (first != null) first.setNextFocusUpId(View.NO_ID);
        }
    }

    private View firstFocusable(View root) {
        if (root.isFocusable()) return root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = firstFocusable(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

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

    static String faType(String type) {
        if ("series".equalsIgnoreCase(type)) return "سریال";
        if ("movie".equalsIgnoreCase(type)) return "فیلم";
        return "فیلم و سریال";
    }

    static String fa(Object value) {
        return String.valueOf(value).replace('0', '۰').replace('1', '۱').replace('2', '۲')
                .replace('3', '۳').replace('4', '۴').replace('5', '۵')
                .replace('6', '۶').replace('7', '۷').replace('8', '۸').replace('9', '۹');
    }

    void loadCover(final ImageView view, String url) {
        if (url == null || url.isEmpty()) return;
        Glide.with(activity).load(url).centerCrop()
                .placeholder(placeholder)
                .error(placeholder)
                .into(view);
    }

    void loadBackdrop(final ImageView view, String url) {
        if (url == null || url.isEmpty()) return;
        Glide.with(activity).load(url).centerCrop()
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(450))
                .into(view);
    }

    void upgradePoster(final ImageView view, String url) {
        final String large = hiRes(url);
        if (large.equals(url) || large.isEmpty()) return;
        Glide.with(activity).load(large).centerCrop()
                .placeholder(view.getDrawable())
                .error(view.getDrawable())
                .dontAnimate()
                .into(view);
    }

    static String hiRes(String url) {
        if (url == null) return "";
        return SIZED_IMAGE.matcher(url).replaceFirst("$3");
    }
}
