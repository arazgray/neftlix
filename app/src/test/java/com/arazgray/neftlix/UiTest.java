package com.arazgray.neftlix;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UiTest {

    @Test
    public void fa_convertsLatinDigits() {
        assertEquals("۰۱۲۳۴۵۶۷۸۹", Ui.fa("0123456789"));
        assertEquals("۱۲ لینک پخش", Ui.fa(12) + " لینک پخش");
        assertEquals("نسخه ۱.۱۲", Ui.fa("نسخه 1.12"));
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
        
        assertEquals(1f, Ui.scaleForWidth(960f), 0.001f);
        
        assertEquals(0.75f, Ui.scaleForWidth(400f), 0.001f);
        
        assertEquals(2.4f, Ui.scaleForWidth(5000f), 0.001f);
        
        assertEquals(1280f / 960f, Ui.scaleForWidth(1280f), 0.001f);
    }

    @Test
    public void gridMath_matches960dpDesignSpace() {
        int columns = Ui.gridColumnsFor(Ui.PAGE_PAD, Ui.CARD_W, Ui.CARD_GAP);
        
        assertEquals(6, columns);
        float card = Ui.gridCardWidthDpFor(columns, Ui.PAGE_PAD, Ui.CARD_GAP);
        
        assertEquals(960f - Ui.PAGE_PAD * 2, card * columns + Ui.CARD_GAP * (columns - 1), 0.01f);
    }
}
