package com.arazgray.neftlix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchKeysTest {

    @Test
    public void persianRows_coverAlphabet() {
        assertTrue(SearchKeys.coversPersianAlphabet());
    }

    @Test
    public void englishRows_coverAlphabet() {
        assertTrue(SearchKeys.coversEnglishAlphabet());
    }

    @Test
    public void digits_areWestern() {
        assertEquals("1234567890", SearchKeys.DIGITS);
    }

    @Test
    public void insertAndBackspace() {
        assertEquals("فیلم", SearchKeys.insert("فیل", "م"));
        assertEquals("a", SearchKeys.insert("", "a"));
        assertEquals("a", SearchKeys.insert(null, "a"));
        assertEquals("فیل", SearchKeys.backspace("فیلم"));
        assertEquals("", SearchKeys.backspace(""));
        assertEquals("", SearchKeys.backspace(null));
    }
}
