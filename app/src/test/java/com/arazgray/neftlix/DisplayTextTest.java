package com.arazgray.neftlix;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DisplayTextTest {

    @Test
    public void cleanTitle_nullAndBlank() {
        assertEquals("", DisplayText.cleanTitle(null));
        assertEquals("", DisplayText.cleanTitle("   "));
    }

    @Test
    public void cleanTitle_stripsDownloadPrefix() {
        assertEquals("Godzilla 2024",
                DisplayText.cleanTitle("دانلود فیلم Godzilla 2024"));
        assertEquals("Animals حیوانات",
                DisplayText.cleanTitle("دانلود سریال Animals حیوانات"));
        assertEquals("دانلود از سایت",
                DisplayText.cleanTitle("دانلود از سایت"));
    }

    @Test
    public void cleanTitle_stripsPromoTails() {
        assertEquals("God Will Not Help 2025",
                DisplayText.cleanTitle("God Will Not Help 2025 بدون سانسور با زیرنویس فارسی چسبیده"));
        assertEquals("سریال Animals 2026",
                DisplayText.cleanTitle("سریال Animals 2026 دوبله فارسی کامل رایگان"));
        assertEquals("فیلم X",
                DisplayText.cleanTitle("فیلم X زیرنویس چسبیده"));
    }

    @Test
    public void cleanTitle_stripsStackedTails() {
        assertEquals("فیلم Y",
                DisplayText.cleanTitle("فیلم Y بدون سانسور رایگان"));
    }

    @Test
    public void cleanTitle_plainTitleUnchanged() {
        assertEquals("Güller ve Günahlar",
                DisplayText.cleanTitle("Güller ve Günahlar"));
    }

    @Test
    public void cleanTitle_neverReturnsEmpty() {
        assertEquals("رایگان", DisplayText.cleanTitle("رایگان"));
    }

    @Test
    public void watchLabel_rewritesSiteWording() {
        assertEquals("تماشا با کیفیت 1080p",
                DisplayText.watchLabel("دانلود با کیفیت 1080p"));
        assertEquals("پخش مستقیم", DisplayText.watchLabel("دانلود مستقیم"));
        assertEquals("پخش مستقیم", DisplayText.watchLabel("تماشا مستقیم"));
        assertEquals("تماشا", DisplayText.watchLabel(""));
        assertEquals("تماشا", DisplayText.watchLabel(null));
        assertEquals("قسمت ۱", DisplayText.watchLabel("قسمت ۱"));
    }

    @Test
    public void trailerQuery_buildsYoutubeSearch() {
        assertEquals("spiderman 2012 trailer",
                DisplayText.trailerQuery("Spider-Man (2012)"));
        assertEquals("animals 2026 trailer",
                DisplayText.trailerQuery("حیوانات Animals 2026"));
        assertEquals("god will not help 2025 trailer",
                DisplayText.trailerQuery("God Will Not Help 2025"));
        
        assertEquals("حیوانات trailer", DisplayText.trailerQuery("حیوانات"));
        assertEquals("trailer", DisplayText.trailerQuery(""));
        assertEquals("trailer", DisplayText.trailerQuery(null));
    }
}
