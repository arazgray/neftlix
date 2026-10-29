package com.arazgray.neftlix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CatalogLabelsTest {

    @Test
    public void sections_translate() {
        assertEquals("Updated movies", CatalogLabels.english("فیلم‌های به روز شده"));
        assertEquals("Updated series", CatalogLabels.english("سریال‌های به روز شده"));
        assertEquals("Global series", CatalogLabels.english("سریال‌های جهانی"));
        assertEquals("Coming soon", CatalogLabels.english("بزودی"));
        assertEquals("Featured", CatalogLabels.english("ویژه‌ها"));
    }

    @Test
    public void genres_translate() {
        assertEquals("Action", CatalogLabels.english("اکشن"));
        assertEquals("Comedy", CatalogLabels.english("کمدی"));
        assertEquals("Drama", CatalogLabels.english("درام"));
        assertEquals("Thriller", CatalogLabels.english("هیجان انگیز"));
        assertEquals("TV shows", CatalogLabels.english("برنامه‌ تلویزیونی‌"));
        assertEquals("Sports", CatalogLabels.english("ورزشی"));
    }

    @Test
    public void patterns_and_fallback() {
        assertEquals("Top movies of 2027", CatalogLabels.english("برترین فیلم‌های 2027"));
        assertEquals("Persian subtitles", CatalogLabels.english("زیرنویس فارسی"));
        assertEquals("Some New Section", CatalogLabels.english("Some New Section"));
        assertEquals("", CatalogLabels.english(""));
    }

    @Test
    public void meta_translatesGenresOnly() {
        assertEquals("★ 7.5 · Drama, Romance", CatalogLabels.englishMeta("★ 7.5 · درام، عاشقانه"));
        assertEquals("Comedy", CatalogLabels.englishMeta("کمدی"));
        assertEquals("", CatalogLabels.englishMeta(""));
    }

    @Test
    public void linkLabels_translate() {
        assertEquals("Direct play", CatalogLabels.english("دانلود مستقیم"));
        assertEquals("Direct play", CatalogLabels.english("تماشا مستقیم"));
        assertEquals("Episode 3", CatalogLabels.english("قسمت ۳"));
        assertEquals("Download in 1080p quality", CatalogLabels.english("دانلود با کیفیت 1080p"));
    }

    @Test
    public void everyEntry_hasEnglish() {
        assertTrue(CatalogLabels.english("نامشخص").length() > 0);
        assertFalse(CatalogLabels.english("نامشخص").equals("نامشخص"));
    }
}
