package dev.annachat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MentionScannerTest {
    @Test
    void findsMentionAfterLegacyColorCode() throws Exception {
        MentionScanner.MentionRange range = MentionScanner.next("§c@Alane11", 0);
        assertEquals(2, range.start());
        assertEquals(10, range.end());
        assertEquals("Alane11", range.name());
    }

    @Test
    void skipsUnknownMentionWithoutLooping() throws Exception {
        MentionScanner.MentionRange range = MentionScanner.next("@offline hi @Online", 0);
        assertEquals(0, range.start());
        assertEquals(8, range.end());
        assertEquals("offline", range.name());
        MentionScanner.MentionRange next = MentionScanner.next("@offline hi @Online", 8);
        assertEquals(12, next.start());
    }

    @Test
    void doesNotTreatEmbeddedAtAsMention() throws Exception {
        assertNull(MentionScanner.next("mail@example", 0));
    }
}
