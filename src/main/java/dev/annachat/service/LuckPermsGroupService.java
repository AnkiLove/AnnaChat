package dev.annachat.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

/**
 * LuckPerms 的软集成。没有安装 LuckPerms 时返回空值，格式和称号仍可通过
 * 普通 Bukkit 权限节点工作；读取用户缓存不会执行网络或数据库 I/O。
 */
public final class LuckPermsGroupService {
    public Optional<String> primaryGroup(Player player) {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
            ClassLoader loader = plugin == null
                    ? LuckPermsGroupService.class.getClassLoader()
                    : plugin.getClass().getClassLoader();
            Class<?> apiType = Class.forName("net.luckperms.api.LuckPerms", false,
                    loader);
            Object luckPerms = Bukkit.getServicesManager().load(apiType);
            if (luckPerms == null) return Optional.empty();
            Method getUserManager = apiType.getMethod("getUserManager");
            Object userManager = getUserManager.invoke(luckPerms);
            Class<?> userManagerType = Class.forName("net.luckperms.api.model.user.UserManager", false,
                    loader);
            Method getUser = userManagerType.getMethod("getUser", java.util.UUID.class);
            Object user = getUser.invoke(userManager, player.getUniqueId());
            if (user == null) return Optional.empty();
            Class<?> userType = Class.forName("net.luckperms.api.model.user.User", false,
                    loader);
            Method getPrimaryGroup = userType.getMethod("getPrimaryGroup");
            Object rawGroup = getPrimaryGroup.invoke(user);
            if (!(rawGroup instanceof String group) || group.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(group.toLowerCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            // 权限插件重载期间可能暂时取不到用户，回退到默认格式。
            return Optional.empty();
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }
}
