package com.arazgray.neftlix;

import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * صفحه‌کلید روی صفحه برای جست‌وجو. فارسی و انگلیسی را عوض می‌کند و
 * به جای کیبورد سیستم می‌نشیند.
 */
final class SearchPad {
    private final Ui ui;
    private final TextView field;
    private final Runnable onSearch;
    private final LinearLayout panel;
    private final LinearLayout rows;
    private boolean persian = true;
    private boolean open;
    /** هنگام عوض کردن زبان فوکوس لحظه‌ای از صفحه بیرون می‌رود؛ نباید کیبورد بسته شود. */
    private boolean holdingFocus;
    private View defaultKey;

    SearchPad(Ui ui, TextView field, Runnable onSearch) {
        this.ui = ui;
        this.field = field;
        this.onSearch = onSearch;
        if (field.getId() == View.NO_ID) field.setId(View.generateViewId());
        panel = ui.column();
        panel.setVisibility(View.GONE);
        panel.setBackground(ui.rounded(0xF20A0A0C, 16));
        panel.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(10));
        rows = ui.column();
        panel.addView(rows, ui.params(-1, -2));
        rebuild();
    }

    View view() {
        return panel;
    }

    void open() {
        open = true;
        panel.setVisibility(View.VISIBLE);
        if (defaultKey != null) {
            field.setNextFocusDownId(defaultKey.getId());
            defaultKey.requestFocus();
        }
    }

    void close() {
        open = false;
        field.setNextFocusDownId(View.NO_ID);
        panel.setVisibility(View.GONE);
    }

    boolean isOpen() {
        return open && panel.getVisibility() == View.VISIBLE;
    }

    boolean holdsFocus() {
        return holdingFocus;
    }

    boolean hosts(View candidate) {
        View current = candidate;
        while (current != null) {
            if (current == panel) return true;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    /** حروف سخت‌افزاری، وقتی صفحه‌کلید باز است، مستقیم به متن جست‌وجو می‌روند. */
    boolean offerKey(KeyEvent event) {
        if (!isOpen() || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
            field.setText(SearchKeys.backspace(field.getText().toString()));
            return true;
        }
        int ch = event.getUnicodeChar();
        if (ch > 0 && !Character.isISOControl(ch)) {
            field.setText(SearchKeys.insert(field.getText().toString(), String.valueOf((char) ch)));
            return true;
        }
        return false;
    }

    private void rebuild() {
        rows.removeAllViews();
        List<LinearLayout> built = new ArrayList<>();
        built.add(addRow(SearchKeys.DIGITS));
        String[] letters = SearchKeys.letterRows(persian);
        for (int i = 0; i < letters.length; i++) {
            // چیدمان استاندارد صفحه‌کلید: از چپ به راست. ش و ض سمت چپ می‌نشینند.
            LinearLayout row = addRow(letters[i]);
            built.add(row);
            if (i == 0 && row.getChildCount() > 0) defaultKey = row.getChildAt(0);
        }
        built.add(actionRow());
        wireFocus(built);
    }

    private LinearLayout addRow(String chars) {
        LinearLayout row = ui.row();
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int i = 0; i < chars.length(); i++) {
            String label = String.valueOf(chars.charAt(i));
            row.addView(key(label, "char", 1f, false));
        }
        rows.addView(row, ui.params(-1, -2));
        return row;
    }

    private LinearLayout actionRow() {
        LinearLayout row = ui.row();
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView lang = key(persian ? "EN" : "فا", "lang", 1.3f, false);
        TextView space = key(persian ? "فاصله" : "space", "space", 4f, false);
        TextView back = key("حذف", "back", 1.3f, false);
        TextView search = key("جستجو", "search", 1.6f, false);
        styleSearchKey(search);
        back.setOnLongClickListener(v -> {
            field.setText("");
            return true;
        });
        row.addView(lang);
        row.addView(space);
        row.addView(back);
        row.addView(search);
        rows.addView(row, ui.params(-1, -2));
        return row;
    }

    private TextView key(String label, String tag, float weight, boolean primary) {
        TextView view = new TextView(ui.activity);
        ui.applyText(view, 15, Ui.TEXT, true);
        view.setText(label);
        view.setTag(tag);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        if (Build.VERSION.SDK_INT >= 26) view.setDefaultFocusHighlightEnabled(false);
        ui.fillFocus(view, primary, 34, 8);
        view.setOnClickListener(v -> onKey(label, tag));
        LinearLayout.LayoutParams params = ui.params(0, ui.dp(34), weight);
        params.setMargins(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        view.setLayoutParams(params);
        return view;
    }

    /** دکمهٔ جستجو سفید است و فقط با فوکوس قرمز می‌شود. */
    private void styleSearchKey(TextView view) {
        android.graphics.drawable.GradientDrawable idle = ui.rounded(Ui.TEXT, 8);
        android.graphics.drawable.GradientDrawable focused = ui.rounded(Ui.BRAND, 8);
        view.setBackground(idle);
        view.setTextColor(0xFF0A0A0C);
        view.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(hasFocus ? focused : idle);
            view.setTextColor(hasFocus ? Ui.TEXT : 0xFF0A0A0C);
        });
    }

    private void onKey(String label, String tag) {
        if ("lang".equals(tag)) {
            persian = !persian;
            holdingFocus = true;
            rebuild();
            panel.post(() -> {
                View lang = panel.findViewWithTag("lang");
                if (lang != null) lang.requestFocus();
                holdingFocus = false;
            });
            return;
        }
        if ("back".equals(tag)) {
            field.setText(SearchKeys.backspace(field.getText().toString()));
            return;
        }
        if ("space".equals(tag)) {
            field.setText(SearchKeys.insert(field.getText().toString(), " "));
            return;
        }
        if ("search".equals(tag)) {
            onSearch.run();
            return;
        }
        field.setText(SearchKeys.insert(field.getText().toString(), label));
    }

    private void wireFocus(List<LinearLayout> built) {
        for (LinearLayout row : built) {
            for (int i = 0; i < row.getChildCount(); i++) {
                if (row.getChildAt(i).getId() == View.NO_ID) {
                    row.getChildAt(i).setId(View.generateViewId());
                }
            }
        }
        for (int r = 0; r < built.size(); r++) {
            LinearLayout row = built.get(r);
            boolean rtl = row.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            int count = row.getChildCount();
            for (int i = 0; i < count; i++) {
                View key = row.getChildAt(i);
                int leftIndex = rtl ? (i + 1) % count : (i - 1 + count) % count;
                int rightIndex = rtl ? (i - 1 + count) % count : (i + 1) % count;
                key.setNextFocusLeftId(row.getChildAt(leftIndex).getId());
                key.setNextFocusRightId(row.getChildAt(rightIndex).getId());
                if (r == 0) {
                    key.setNextFocusUpId(field.getId());
                } else {
                    LinearLayout above = built.get(r - 1);
                    key.setNextFocusUpId(above.getChildAt(
                            Math.min(i, above.getChildCount() - 1)).getId());
                }
                if (r == built.size() - 1) {
                    key.setNextFocusDownId(key.getId());
                } else {
                    LinearLayout below = built.get(r + 1);
                    key.setNextFocusDownId(below.getChildAt(
                            Math.min(i, below.getChildCount() - 1)).getId());
                }
            }
        }
    }
}
