package com.arazgray.neftlix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;

public class CatalogCacheTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private CatalogData sampleCatalog() {
        CatalogData catalog = new CatalogData();
        catalog.sections.add(new BrowseSection("فیلم‌ها", "https://site/movies/",
                "movie", Arrays.asList(
                        new BrowseItem("Godzilla", "page", "https://cdn/g.jpg",
                                "https://site/m/godzilla/", "movie", "★ 7 ★ اکشن"))));
        catalog.movieCategories.add(new BrowseCategory("اکشن", "movie",
                "https://site/genre/action/?type=movie", "۱۲"));
        catalog.seriesCategories.add(new BrowseCategory("درام", "series",
                "https://site/genre/drama/?type=series", "۸"));
        return catalog;
    }

    @Test
    public void saveAndLoad_roundTrips() throws Exception {
        File file = folder.newFile("catalog_cache.json");
        CatalogCache.save(file, sampleCatalog());
        assertTrue(file.length() > 0);
        CatalogData loaded = CatalogCache.load(file);
        assertEquals(1, loaded.sections.size());
        assertEquals("فیلم‌ها", loaded.sections.get(0).title);
        assertEquals("Godzilla", loaded.sections.get(0).items.get(0).title);
        assertEquals("★ 7 ★ اکشن", loaded.sections.get(0).items.get(0).meta);
        assertEquals(1, loaded.movieCategories.size());
        assertEquals("اکشن", loaded.movieCategories.get(0).title);
        assertEquals("https://site/genre/action/?type=movie",
                loaded.movieCategories.get(0).url);
        assertEquals(1, loaded.seriesCategories.size());
    }

    @Test
    public void load_missingFileReturnsNull() {
        assertNull(CatalogCache.load(new File(folder.getRoot(), "nope.json")));
    }

    @Test
    public void load_corruptFileReturnsNull() throws Exception {
        File file = folder.newFile("broken.json");
        java.nio.file.Files.write(file.toPath(), "{oops".getBytes("UTF-8"));
        assertNull(CatalogCache.load(file));
    }
}
