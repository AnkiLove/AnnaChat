package dev.annachat.service;

/**
 * 线性扫描聊天正文中的 @玩家片段，避免复杂正则回溯阻塞 Folia 区域线程。
 */
final class MentionScanner {
    private MentionScanner() {
    }

    static MentionRange next(String message, int fromIndex) {
        int start = Math.max(0, fromIndex);
        while (start < message.length()) {
            int at = message.indexOf('@', start);
            if (at < 0) return null;
            if (!isMentionBoundary(message, at)) {
                start = at + 1;
                continue;
            }
            int end = at + 1;
            while (end < message.length() && isNameCharacter(message.charAt(end)) && end - at <= 16) end++;
            if (end > at + 1 && end - at <= 17
                    && (end == message.length() || !isNameCharacter(message.charAt(end)))) {
                return new MentionRange(at, end, message.substring(at + 1, end));
            }
            start = at + 1;
        }
        return null;
    }

    private static boolean isMentionBoundary(String message, int at) {
        if (at == 0 || !isNameCharacter(message.charAt(at - 1))) return true;
        return at >= 2 && (message.charAt(at - 2) == '&' || message.charAt(at - 2) == '§')
                && isLegacyCode(message.charAt(at - 1));
    }

    private static boolean isNameCharacter(char character) {
        return character <= 0x7F
                && (character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '_');
    }

    private static boolean isLegacyCode(char character) {
        char code = Character.toLowerCase(character);
        return code >= '0' && code <= '9'
                || code >= 'a' && code <= 'f'
                || code >= 'k' && code <= 'o'
                || code == 'r';
    }

    record MentionRange(int start, int end, String name) {
    }
}
