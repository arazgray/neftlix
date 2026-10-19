package com.arazgray.neftlix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/** تست فهرست علاقه‌مندی‌ها: افزودن/حذف/ماندگاری. */
public class WatchlistStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private BrowseItem item(String url, String title) {
        return new BrowseItem(title, "", "https://cdn/x.jpg", url, "movie", "");
    }

    @Test
    public void toggle_addsAndRemoves() throws Exception {
        WatchlistStore store = new WatchlistStore(folder.newFile("watchlist.json"));
        assertTrue(store.toggle(item("https://site/m/a/", "A")));
        assertTrue(store.contains("https://site/m/a/"));
        assertEquals(1, store.size());
        assertFalse(store.toggle(item("https://site/m/a/", "A")));
        assertEquals(0, store.size());
    }

    @Test
    public void toggle_movesDuplicatesToFront() throws Exception {
        WatchlistStore store = new WatchlistStore(folder.newFile("watchlist.json"));
        store.toggle(item("https://site/m/a/", "A"));
        store.toggle(item("https://site/m/b/", "B"));
        store.remove("https://site/m/a/");
        store.toggle(item("https://site/m/a/", "A"));
        assertEquals("https://site/m/a/", store.items().get(0).url);
        assertEquals(2, store.size());
    }

    @Test
    public void roundTrip_persistsAcrossInstances() throws Exception {
        File file = folder.newFile("watchlist.json");
        WatchlistStore first = new WatchlistStore(file);
        first.toggle(item("https://site/m/a/", "A"));
        first.toggle(item("https://site/s/b/", "B"));
        WatchlistStore second = new WatchlistStore(file);
        assertEquals(2, second.size());
        assertEquals("B", second.items().get(0).title);
        assertEquals("https://cdn/x.jpg", second.items().get(0).coverUrl);
    }

    @Test
    public void load_corruptFileYieldsEmpty() throws Exception {
        File file = folder.newFile("broken.json");
        java.nio.file.Files.write(file.toPath(), "[oops".getBytes("UTF-8"));
        WatchlistStore store = new WatchlistStore(file);
        assertEquals(0, store.size());
    }

    @Test
    public void toggle_rejectsBadItems() throws Exception {
        WatchlistStore store = new WatchlistStore(folder.newFile("watchlist.json"));
        assertFalse(store.toggle(null));
        assertFalse(store.toggle(item("", "NoUrl")));
        assertEquals(0, store.size());
    }
}
