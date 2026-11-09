package com.arazgray.neftlix;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PlaybackProgressStoreTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void progressPersistsExactlyAcrossInstances() throws Exception {
        File file = folder.newFile();
        PlaybackProgressStore store = new PlaybackProgressStore(file);
        store.save("https://cdn/movie.mp4", 123456);
        assertEquals(123456, new PlaybackProgressStore(file).position("https://cdn/movie.mp4"));
    }

    @Test
    public void episodesAndMoviesKeepSeparatePositions() throws Exception {
        File file = folder.newFile();
        PlaybackProgressStore store = new PlaybackProgressStore(file);
        store.save("https://cdn/play?id=1", 20000);
        store.save("https://cdn/play?id=2", 30000);
        store.save("https://cdn/movie.mp4", 40000);
        PlaybackProgressStore restored = new PlaybackProgressStore(file);
        assertEquals(20000, restored.position("https://cdn/play?id=1"));
        assertEquals(30000, restored.position("https://cdn/play?id=2"));
        assertEquals(40000, restored.position("https://cdn/movie.mp4"));
        assertEquals(0, restored.position("https://cdn/unknown.mp4"));
    }

    @Test
    public void rewindAndFinishedItemsReplacePreviousProgress() throws Exception {
        File file = folder.newFile();
        PlaybackProgressStore store = new PlaybackProgressStore(file);
        store.save("movie", 123456);
        store.save("movie", 45678);
        assertEquals(45678, new PlaybackProgressStore(file).position("movie"));
        store.save("movie", 0);
        assertEquals(0, new PlaybackProgressStore(file).position("movie"));
        store.save("movie", -1);
        assertEquals(0, new PlaybackProgressStore(file).position("movie"));
    }

    @Test
    public void corruptOrMissingFilesStartEmpty() throws Exception {
        File file = new File(folder.getRoot(), "progress.json");
        assertEquals(0, new PlaybackProgressStore(file).position("movie"));
        Files.write(file.toPath(), "broken".getBytes(StandardCharsets.UTF_8));
        PlaybackProgressStore store = new PlaybackProgressStore(file);
        assertEquals(0, store.position("movie"));
        store.save("movie", 1000);
        assertEquals(1000, new PlaybackProgressStore(file).position("movie"));
    }

    @Test
    public void historyIsBoundedAndRecentlyWatchedItemsStay() throws Exception {
        File file = folder.newFile();
        org.json.JSONArray entries = new org.json.JSONArray();
        for (int i = 0; i < 500; i++) {
            entries.put(new org.json.JSONObject().put("url", "movie" + i).put("position", 1000));
        }
        Files.write(file.toPath(), entries.toString().getBytes(StandardCharsets.UTF_8));
        PlaybackProgressStore store = new PlaybackProgressStore(file);
        store.save("movie0", 2000);
        store.save("new", 3000);
        PlaybackProgressStore restored = new PlaybackProgressStore(file);
        assertEquals(2000, restored.position("movie0"));
        assertEquals(0, restored.position("movie1"));
        assertEquals(3000, restored.position("new"));
    }

    @Test
    public void emptyKeysAreIgnored() throws Exception {
        File file = folder.newFile();
        PlaybackProgressStore store = new PlaybackProgressStore(file);
        store.save(null, 1000);
        store.save("", 1000);
        assertEquals(0, store.position(null));
        assertEquals(0, store.position(""));
    }
}
