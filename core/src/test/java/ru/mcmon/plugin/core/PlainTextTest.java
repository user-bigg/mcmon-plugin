package ru.mcmon.plugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlainTextTest {

    @Test
    void stripsColorCodesControlCharactersAndMarkupTricks() {
        assertEquals("grief at spawn", PlainText.sanitize("§c§lgrief\u0007 at\nspawn§r", 200));
        assertEquals("cc", PlainText.sanitize("§§cc§\u200Bc", 200));
        assertEquals("R&D 🙂", PlainText.sanitize("  R&D   🙂\u2028", 200));
    }

    @Test
    void cutsToLengthWithoutSplittingEmoji() {
        assertEquals("a", PlainText.sanitize("a🙂", 2));
    }
}
