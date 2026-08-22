package dev.annachat.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyChatColorTest {
    @Test
    void parsesNamedColorAndDecoration() {
        Component component = TextService.legacyPlayerText("&a&l测试");

        assertEquals("测试", PlainTextSupport.plain(component));
        assertEquals(NamedTextColor.GREEN, component.color());
        assertEquals(net.kyori.adventure.text.format.TextDecoration.State.TRUE,
                component.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD));
    }

    @Test
    void parsesCompactHexColor() {
        Component component = TextService.legacyPlayerText("&#12AB34颜色");

        assertEquals("颜色", PlainTextSupport.plain(component));
        assertEquals(TextColor.color(0x12AB34), component.color());
    }

    @Test
    void parsesRepeatedHexColor() {
        Component component = TextService.legacyPlayerText("&x&F&F&0&0&A&A颜色");

        assertEquals("颜色", PlainTextSupport.plain(component));
        assertEquals(TextColor.color(0xFF00AA), component.color());
    }

    @Test
    void allowsLegacyColorsTogetherWithMiniMessage() {
        String prepared = TextService.playerMiniMessageSafe("&a<bold>测试</bold>", true);

        assertTrue(prepared.startsWith("<reset><green><bold>"));
        assertEquals("测试", PlainTextSupport.plain(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(prepared)
        ));
    }

    @Test
    void keepsOnlyIndividuallyAuthorizedAdvancedTags() {
        String prepared = TextService.restrictAdvancedTags(
                "<gradient:red:blue>渐变</gradient> <rainbow>彩虹</rainbow> <bold>普通标签</bold>",
                true, false, false);
        Component component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(prepared);

        assertEquals("渐变 <rainbow>彩虹</rainbow> <bold>普通标签</bold>", PlainTextSupport.plain(component));
        assertTrue(prepared.contains("<gradient:red:blue>"));
        assertTrue(prepared.contains("\\<rainbow>"));
        assertTrue(prepared.contains("\\<bold>"));
    }

    private static final class PlainTextSupport {
        private static final net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer SERIALIZER =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();

        private static String plain(Component component) {
            return SERIALIZER.serialize(component);
        }
    }
}
