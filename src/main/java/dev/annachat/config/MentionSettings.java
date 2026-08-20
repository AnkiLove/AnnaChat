package dev.annachat.config;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;

/**
 * 玩家提及功能的不可变运行配置。
 */
public record MentionSettings(
        boolean enabled,
        String permission,
        boolean autocomplete,
        boolean soundEnabled,
        Sound sound,
        SoundCategory soundCategory,
        float volume,
        float pitch
) {
    public MentionSettings {
        permission = permission == null ? "" : permission.strip();
        if (sound == null) throw new IllegalArgumentException("提及提示音不能为空");
        if (soundCategory == null) throw new IllegalArgumentException("提及提示音分类不能为空");
        if (!Float.isFinite(volume) || volume < 0.0F) {
            throw new IllegalArgumentException("提及提示音音量必须是大于或等于 0 的有限数值");
        }
        if (!Float.isFinite(pitch) || pitch < 0.5F || pitch > 2.0F) {
            throw new IllegalArgumentException("提及提示音音调必须在 0.5 到 2.0 之间");
        }
    }
}
