package dev.annachat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemDisplayTokenTest {
    @Test
    void parsesHotbarSlotsOneToNine() {
        for (int slot = 1; slot <= 9; slot++) {
            ItemDisplayToken token = ItemDisplayToken.parse("%" + slot).orElseThrow();
            assertFalse(token.inventory());
            assertEquals(slot - 1, token.hotbarSlot());
        }
    }

    @Test
    void parsesWholeInventoryToken() {
        ItemDisplayToken token = ItemDisplayToken.parse("%i").orElseThrow();
        assertTrue(token.inventory());
        assertEquals(-1, token.hotbarSlot());
    }

    @Test
    void rejectsUnsupportedTokenForms() {
        assertTrue(ItemDisplayToken.parse("%0").isEmpty());
        assertTrue(ItemDisplayToken.parse("%10").isEmpty());
        assertTrue(ItemDisplayToken.parse("%x").isEmpty());
        assertTrue(ItemDisplayToken.parse("1").isEmpty());
    }
}
