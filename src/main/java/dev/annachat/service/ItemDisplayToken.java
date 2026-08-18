package dev.annachat.service;

import java.util.Optional;

/**
 * 聊天正文中的物品展示占位符。数字只代表快捷栏槽位，i 代表整个背包。
 */
record ItemDisplayToken(boolean inventory, int hotbarSlot) {
    static Optional<ItemDisplayToken> parse(String token) {
        if ("%i".equals(token)) return Optional.of(new ItemDisplayToken(true, -1));
        if (token.length() != 2 || token.charAt(0) != '%') return Optional.empty();
        char digit = token.charAt(1);
        if (digit < '1' || digit > '9') return Optional.empty();
        return Optional.of(new ItemDisplayToken(false, digit - '1'));
    }
}
