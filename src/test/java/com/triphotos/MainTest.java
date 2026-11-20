package com.triphotos;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void describeFallsBackToExceptionTypeWhenThereIsNoMessage() {
        assertEquals("NullPointerException", Main.describe(new NullPointerException()));
        assertEquals("IOException", Main.describe(new IOException("  ")));
        assertEquals("disque plein", Main.describe(new IOException("disque plein")));
    }

    @Test
    void invalidArgumentsReturnUsageExitCode() {
        assertEquals(Main.EXIT_USAGE, Main.run(new String[]{"seulement-un-dossier"}));
        assertEquals(Main.EXIT_USAGE, Main.run(new String[]{"a", "b", "--inconnue"}));
    }

    @Test
    void helpReturnsSuccess() {
        assertEquals(Main.EXIT_OK, Main.run(new String[]{"--help"}));
    }

    @Test
    void guiOpensOnlyWithoutArgumentsAndWithAScreen() {
        assertTrue(Main.shouldOpenGui(new String[0], false));
        assertFalse(Main.shouldOpenGui(new String[]{"a", "b"}, false));
        assertFalse(Main.shouldOpenGui(new String[0], true));
    }
}
