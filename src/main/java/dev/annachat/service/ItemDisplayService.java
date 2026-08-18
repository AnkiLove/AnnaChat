package dev.annachat.service;

import dev.annachat.api.InteractionProvider;
import dev.annachat.api.InteractiveMatch;
import dev.annachat.api.context.ChatContext;
import dev.annachat.config.ItemDisplaySettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将聊天正文中的物品占位符渲染为只读物品悬停组件。
 *
 * <p>物品读取发生在聊天管线已经回到发送者所属调度器之后。组件只设置
 * Adventure 的 {@code show_item} 悬停事件，不设置任何点击动作，因此客户端
 * 只能预览物品，不能通过聊天消息执行操作。</p>
 */
public final class ItemDisplayService implements InteractionProvider {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("%([1-9i])(?![0-9])");
    private volatile ItemDisplaySettings settings = new ItemDisplaySettings(true, "", true, true);

    public void apply(ItemDisplaySettings settings) {
        this.settings = settings;
    }

    @Override
    public Optional<InteractiveMatch> find(ChatContext context, String message, int fromIndex) {
        ItemDisplaySettings current = settings;
        if (!current.enabled()) return Optional.empty();
        if (!current.permission().isBlank() && !context.sender().hasPermission(current.permission())) {
            return Optional.empty();
        }

        Matcher matcher = TOKEN_PATTERN.matcher(message);
        if (!matcher.find(fromIndex)) return Optional.empty();
        String rawToken = matcher.group();
        ItemDisplayToken token = ItemDisplayToken.parse(rawToken).orElse(null);
        if (token == null) return Optional.empty();
        Component rendered = token.inventory()
                ? renderInventory(context.sender(), current)
                : renderHotbar(context.sender(), token.hotbarSlot());
        return Optional.of(new InteractiveMatch(matcher.start(), matcher.end(), rendered));
    }

    /** 让内置物品占位符优先于管理员配置的宽泛正则规则。 */
    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    private Component renderHotbar(Player player, int slot) {
        ItemStack item = player.getInventory().getItem(slot);
        if (isEmpty(item)) return emptySlot(slot + 1);
        return itemComponent(item, "快捷栏 " + (slot + 1));
    }

    private Component renderInventory(Player player, ItemDisplaySettings current) {
        PlayerInventory inventory = player.getInventory();
        List<Component> items = new ArrayList<>();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (!isEmpty(storage[slot])) items.add(itemComponent(storage[slot], "槽位 " + (slot + 1)));
        }
        if (current.includeArmor()) {
            ItemStack[] armor = inventory.getArmorContents();
            String[] names = {"靴子", "护腿", "胸甲", "头盔"};
            for (int index = 0; index < armor.length; index++) {
                if (!isEmpty(armor[index])) items.add(itemComponent(armor[index], names[index]));
            }
        }
        if (current.includeOffhand() && !isEmpty(inventory.getItemInOffHand())) {
            items.add(itemComponent(inventory.getItemInOffHand(), "副手"));
        }
        if (items.isEmpty()) return Component.text("[背包为空]", NamedTextColor.DARK_GRAY);

        Component result = Component.text("[背包] ", NamedTextColor.GRAY);
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) result = result.append(Component.text("  ", NamedTextColor.DARK_GRAY));
            result = result.append(items.get(index));
        }
        return result;
    }

    private Component itemComponent(ItemStack source, String slotName) {
        ItemStack item = source.clone();
        Component displayName = item.displayName();
        if (item.getAmount() > 1) {
            displayName = displayName.append(Component.text(" x" + item.getAmount(), NamedTextColor.GRAY));
        }
        return Component.text("[" + slotName + "] ", NamedTextColor.DARK_GRAY)
                .append(displayName)
                .hoverEvent(item.asHoverEvent());
    }

    private static Component emptySlot(int slot) {
        return Component.text("[快捷栏 " + slot + " 为空]", NamedTextColor.DARK_GRAY);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
}
