package dev.annachat.api;

/**
 * 内容审核词库的一次命中结果。
 *
 * @param categoryId 分类 ID，适合程序判断
 * @param categoryDisplayName 面向管理员和玩家的分类名称
 * @param matchedWord 配置文件中命中的原始词条
 * @param reason 面向玩家的拦截原因
 */
public record ModerationMatch(
        String categoryId,
        String categoryDisplayName,
        String matchedWord,
        String reason
) {
}
