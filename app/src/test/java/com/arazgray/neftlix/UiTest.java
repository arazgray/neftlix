package com.arazgray.neftlix;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** تست منطق خالص Ui؛ بدون نیاز به دستگاه یا شبیه‌ساز. */
public class UiTest {

    @Test
    public void fa_convertsLatinDigits() {
        assertEquals("۰۱۲۳۴۵۶۷۸۹", Ui.fa("0123456789"));
        assertEquals("۱۲ لینک پخش", Ui.fa(12) + " لینک پخش");
        assertEquals("نسخه ۱٫۱۲", Ui.fa("نسخه 1.12"));
    }

    @Test
    public void faType_mapsContentTypes() {
        assertEquals("سریال", Ui.faType("series"));
        assertEquals("سریال", Ui.faType("SERIES"));
        assertEquals("فیلم", Ui.faType("movie"));
        assertEquals("فیلم و سریال", Ui.faType("mixed"));
        assertEquals("فیلم و سریال", Ui.faType(null));
        assertEquals("فیلم و سریال", Ui.faType(""));
    }

    @Test
    public void hiRes_stripsSizedSuffix() {
        assertEquals("https://cdn/x/poster.jpg",
                Ui.hiRes("https://cdn/x/poster-180x280.jpg"));
        assertEquals("https://cdn/x/poster.PNG",
                Ui.hiRes("https://cdn/x/poster-360x480.PNG"));
        assertEquals("https://cdn/x/cover.jpg",
                Ui.hiRes("https://cdn/x/cover.jpg"));
        assertEquals("", Ui.hiRes(null));
        assertEquals("", Ui.hiRes(""));
    }

    @Test
    public void scaleForWidth_clampsDesignSpace() {
        assertEquals(1f, Ui.scaleForWidth(960f), 0.001f);
        // تلویزیون 1080p با چگالی ۲ → فضای ۹۶۰dp
        assertEquals(1f, Ui.scaleForWidth(960f), 0.001f);
        // صفحهٔ خیلی کوچک نباید از حداقل کمتر شود
        assertEquals(0.75f, Ui.scaleForWidth(400f), 0.001f);
        // چگالی ۱ (باکس‌های ارزان) نباید از سقف بیشتر شود
        assertEquals(2.4f, Ui.scaleForWidth(5000f), 0.001f);
        // 720p معمولی تلویزیون
        assertEquals(1280f / 960f, Ui.scaleForWidth(1280f), 0.001f);
    }

    @Test
    public void gridMath_matches960dpDesignSpace() {
        int columns = Ui.gridColumnsFor(Ui.PAGE_PAD, Ui.CARD_W, Ui.CARD_GAP);
        // (960-44+12)/(136+12) = 6 ستون
        assertEquals(6, columns);
        float card = Ui.gridCardWidthDpFor(columns, Ui.PAGE_PAD, Ui.CARD_GAP);
        // ۶ ستون + ۵ فاصله باید دقیقاً پهنای مفید را پر کند
        assertEquals(960f - Ui.PAGE_PAD * 2, card * columns + Ui.CARD_GAP * (columns - 1), 0.01f);
    }
}
