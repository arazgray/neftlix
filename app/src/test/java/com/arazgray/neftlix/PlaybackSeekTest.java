package com.arazgray.neftlix;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackSeekTest {
    @Test
    public void seekUsesFiveBackAndFifteenForward() {
        assertEquals(15000, PlaybackSeek.target(20000, 120000, false));
        assertEquals(35000, PlaybackSeek.target(20000, 120000, true));
    }

    @Test
    public void seekClampsToStartAndEnd() {
        assertEquals(0, PlaybackSeek.target(2000, 120000, false));
        assertEquals(120000, PlaybackSeek.target(115000, 120000, true));
        assertEquals(0, PlaybackSeek.target(0, 0, true));
    }

    @Test
    public void unknownDurationDoesNotPreventSeeking() {
        assertEquals(35000, PlaybackSeek.target(20000, -9223372036854775807L, true));
        assertEquals(15000, PlaybackSeek.target(20000, -9223372036854775807L, false));
    }
}
