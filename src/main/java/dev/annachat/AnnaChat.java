package dev.annachat;

import dev.annachat.api.AnnaChatApi;
import dev.annachat.command.AnnaChatCommand;
import dev.annachat.config.ConfigManager;
import dev.annachat.config.RuntimeConfig;
import dev.annachat.database.DatabaseService;
import dev.annachat.internal.AnnaChatApiImpl;
import dev.annachat.integration.AnnaChatExpansion;
import dev.annachat.listener.ChatListener;
import dev.annachat.listener.CommandLogListener;
import dev.annachat.listener.ConsoleCommandBridge;
import dev.annachat.listener.MentionCompletionListener;
import dev.annachat.platform.PlatformMode;
import dev.annachat.service.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class AnnaChat extends JavaPlugin {
    private final AtomicReference<RuntimeConfig> runtime = new AtomicReference<>();
    private PlatformMode platformMode;
    private SchedulerService scheduler;
    private ConfigManager configManager;
    private TextService text;
    private MessageService messages;
    private ChannelService channels;
    private ProcessorService processors;
    private FilterService filters;
    private ContentModerationService moderation;
    private InteractionService interactions;
    private ItemDisplayService itemDisplay;
    private MentionService mentions;
    private FormatService formats;
    private StateService state;
    private HistoryService history;
    private OnlinePlayerService onlinePlayers;
    private RecipientService recipients;
    private ChatPipeline pipeline;
    private ChatIngressService ingress;
    private DatabaseService database;
    private ChatListener chatListener;
    private AnnaChatApi api;
    private AnnaChatExpansion placeholderExpansion;
    private SchedulerService.TaskHandle autosaveTask;
    private final AtomicBoolean reloadInProgress = new AtomicBoolean();
    private final ReentrantReadWriteLock configLock = new ReentrantReadWriteLock();

    @Override
    public void onEnable() {
        final int totalSteps = 10;
        getLogger().info("========== AnnaChat 启动流程开始 ==========");

        platformMode = PlatformMode.detect();
        startupStep(1, totalSteps, "平台识别完成：" + platformMode.displayName()
                + "；服务端=" + platformMode.serverName()
                + "；版本=" + platformMode.serverVersion());

        scheduler = new SchedulerService(this, platformMode);
        startupStep(2, totalSteps, "调度模式初始化：" + platformMode.schedulerDescription());

        configManager = new ConfigManager(this);
        configManager.ensureDefaults();
        startupStep(3, totalSteps, "配置文件检查完成：主配置、频道、格式、称号、分组策略、交互、过滤、审核、占位符和数据库配置已就绪");

        text = new TextService(this);
        messages = new MessageService(text);
        channels = new ChannelService();
        processors = new ProcessorService();
        filters = new FilterService();
        moderation = new ContentModerationService();
        itemDisplay = new ItemDisplayService();
        state = new StateService(this, scheduler);
        history = new HistoryService();
        onlinePlayers = new OnlinePlayerService(state);
        onlinePlayers.initialize();
        mentions = new MentionService(onlinePlayers);
        interactions = new InteractionService(text, itemDisplay, mentions);
        formats = new FormatService(text, interactions);
        recipients = new RecipientService(this, scheduler, state, mentions);
        pipeline = new ChatPipeline(
                this, scheduler, channels, state, processors, filters, moderation, mentions,
                formats, recipients, history, messages
        );
        ingress = new ChatIngressService(this, scheduler);
        database = new DatabaseService(this);
        chatListener = new ChatListener(this, ingress);
        api = new AnnaChatApiImpl(this);
        startupStep(4, totalSteps, "核心服务初始化完成：频道、格式、过滤、审核、状态、数据库和聊天管线已创建");

        state.load();
        startupStep(5, totalSteps, "玩家状态与好友数据加载完成：在线玩家索引=" + onlinePlayers.count());
        try {
            apply(configManager.load(text));
        } catch (RuntimeException exception) {
            getLogger().severe("首次加载配置失败，插件无法启动: " + rootMessage(exception));
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        RuntimeConfig loaded = runtime();
        startupStep(6, totalSteps, "运行配置加载完成：频道=" + channels.all().size()
                + "，格式=" + formats.count()
                + "（规则=" + loaded.formatRules().size() + "）"
                + "，称号规则=" + loaded.titles().rules().size()
                + "，分组策略=" + loaded.groups().rules().size()
                + "，审核词条=" + moderation.wordCount()
                + "，玩家提及=" + (loaded.mentions().enabled() ? "开启" : "关闭")
                + "，自定义占位符=" + loaded.customPlaceholders().size()
                + "，数据库=" + (loaded.database().getBoolean("enabled", false)
                ? loaded.database().getString("backend", "duckdb") + "开启" : "关闭"));
        startupStep(7, totalSteps, "聊天事件桥接完成：旧事件 + Paper AsyncChatEvent，首条消息回退="
                + loaded.legacyEventFallbackTicks() + " tick，原生事件取消="
                + (loaded.cancelNativeChatEvent() ? "开启" : "关闭"));

        AnnaChatCommand command = new AnnaChatCommand(this);
        Objects.requireNonNull(getCommand("annachat")).setExecutor(command);
        Objects.requireNonNull(getCommand("annachat")).setTabCompleter(command);
        Bukkit.getPluginManager().registerEvents(new CommandLogListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ConsoleCommandBridge(this), this);
        Bukkit.getPluginManager().registerEvents(new MentionCompletionListener(mentions), this);
        Bukkit.getPluginManager().registerEvents(onlinePlayers, this);
        Bukkit.getServicesManager().register(AnnaChatApi.class, api, this, ServicePriority.Normal);
        startupStep(8, totalSteps, "命令、Bukkit 事件和 AnnaChat API 注册完成");
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderExpansion = new AnnaChatExpansion(this);
            if (placeholderExpansion.register()) {
                getLogger().info("已注册 PlaceholderAPI 扩展");
            } else {
                getLogger().warning("PlaceholderAPI 扩展注册失败");
            }
        } else {
            getLogger().info("PlaceholderAPI 未安装，跳过可选占位符扩展");
        }
        startupStep(9, totalSteps, "可选集成检查完成：PlaceholderAPI="
                + (placeholderExpansion != null && placeholderExpansion.isRegistered() ? "已注册" : "未启用"));

        startupStep(10, totalSteps, "AnnaChat " + getPluginMeta().getVersion()
                + " 启动完成；模式=" + platformMode.displayName()
                + "；Folia 线程安全调度=" + (platformMode.isFolia() ? "启用" : "不适用"));
        getLogger().info("========== AnnaChat 启动流程完成 ==========");
    }

    @Override
    public void onDisable() {
        if (ingress != null) ingress.reset();
        if (scheduler != null) scheduler.cancelAll();
        if (state != null) state.saveBlocking();
        if (database != null) database.close();
        if (placeholderExpansion != null && placeholderExpansion.isRegistered()) {
            placeholderExpansion.unregister();
        }
        Bukkit.getServicesManager().unregisterAll(this);
    }

    public void reloadAll(CommandSender sender) {
        if (!reloadInProgress.compareAndSet(false, true)) {
            sendReloadMessage(sender, "reload-busy", Map.of());
            return;
        }
        sendReloadMessage(sender, "reload-started", Map.of());
        scheduler.async(() -> {
            try {
                RuntimeConfig candidate = configManager.load(text);
                scheduler.global(() -> {
                    try {
                        apply(candidate);
                        sendReloadMessage(sender, "reload-success", Map.of(
                                "channels", Integer.toString(channels.all().size()),
                                "formats", Integer.toString(formats.count()),
                                "placeholders", Integer.toString(runtime().customPlaceholders().size()),
                                "moderation_words", Integer.toString(moderation.wordCount())
                        ));
                    } catch (RuntimeException exception) {
                        getLogger().severe("应用热重载配置失败: " + rootMessage(exception));
                        sendReloadMessage(sender, "reload-failed", Map.of());
                    } finally {
                        reloadInProgress.set(false);
                    }
                });
            } catch (RuntimeException exception) {
                getLogger().severe("读取热重载配置失败: " + rootMessage(exception));
                sendReloadMessage(sender, "reload-failed", Map.of());
                reloadInProgress.set(false);
            }
        });
    }

    private void sendReloadMessage(CommandSender sender, String key, Map<String, String> values) {
        if (sender instanceof org.bukkit.entity.Player player) {
            scheduler.onEntity(player, () -> messages.send(player, key, values));
        } else {
            scheduler.global(() -> messages.send(sender, key, values));
        }
    }

    private void apply(RuntimeConfig candidate) {
        // 写锁保证聊天线程不会观察到一半是旧配置、一半是新配置的混合状态。
        Lock lock = configLock.writeLock();
        lock.lock();
        try {
            database.validate(candidate.database());
            text.applyCustomPlaceholders(candidate.customPlaceholders());
            formats.apply(candidate.formats(), candidate.formatRules());
            channels.apply(candidate.channels());
            state.reconcileChannels(channels.ids(), candidate.defaultChannel());
            filters.apply(candidate.filters());
            moderation.apply(candidate.moderation());
            itemDisplay.apply(candidate.itemDisplay());
            mentions.apply(candidate.mentions());
            interactions.apply(candidate.interactions());
            history.limit(candidate.historySize());
            messages.apply(candidate);
            runtime.set(candidate);
            chatListener.register(
                    candidate.eventPriority(),
                    candidate.respectCancelledChatEvents(),
                    candidate.cancelNativeChatEvent(),
                    candidate.legacyEventFallbackTicks()
            );
            database.apply(candidate.database());
            if (autosaveTask != null) autosaveTask.cancel();
            autosaveTask = scheduler.repeatGlobal(state::saveAsync,
                    candidate.autosaveSeconds() * 20L, candidate.autosaveSeconds() * 20L);
        } finally {
            lock.unlock();
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    private void startupStep(int step, int total, String message) {
        getLogger().info("[启动 " + step + "/" + total + "] " + message);
    }

    public AnnaChatApi getApi() { return api; }
    public RuntimeConfig runtime() { return Objects.requireNonNull(runtime.get(), "配置尚未加载"); }
    public PlatformMode platformMode() { return Objects.requireNonNull(platformMode, "平台模式尚未识别"); }
    public SchedulerService scheduler() { return scheduler; }
    public TextService text() { return text; }
    public MessageService messages() { return messages; }
    public ChannelService channels() { return channels; }
    public ProcessorService processors() { return processors; }
    public FilterService filters() { return filters; }
    public ContentModerationService moderation() { return moderation; }
    public InteractionService interactions() { return interactions; }
    public ItemDisplayService itemDisplay() { return itemDisplay; }
    public MentionService mentions() { return mentions; }
    public FormatService formats() { return formats; }
    public StateService state() { return state; }
    public HistoryService history() { return history; }
    public OnlinePlayerService onlinePlayers() { return onlinePlayers; }
    public RecipientService recipients() { return recipients; }
    public ChatPipeline pipeline() { return pipeline; }
    public DatabaseService database() { return database; }
    public Lock configReadLock() { return configLock.readLock(); }
}
