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
import dev.annachat.service.*;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
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
    private SchedulerService scheduler;
    private ConfigManager configManager;
    private TextService text;
    private MessageService messages;
    private ChannelService channels;
    private ProcessorService processors;
    private FilterService filters;
    private ContentModerationService moderation;
    private InteractionService interactions;
    private FormatService formats;
    private StateService state;
    private HistoryService history;
    private OnlinePlayerService onlinePlayers;
    private RecipientService recipients;
    private ChatPipeline pipeline;
    private DatabaseService database;
    private ChatListener chatListener;
    private AnnaChatApi api;
    private AnnaChatExpansion placeholderExpansion;
    private ScheduledTask autosaveTask;
    private final AtomicBoolean reloadInProgress = new AtomicBoolean();
    private final ReentrantReadWriteLock configLock = new ReentrantReadWriteLock();

    @Override
    public void onEnable() {
        scheduler = new SchedulerService(this);
        configManager = new ConfigManager(this);
        configManager.ensureDefaults();
        text = new TextService(this);
        messages = new MessageService(text);
        channels = new ChannelService();
        processors = new ProcessorService();
        filters = new FilterService();
        moderation = new ContentModerationService();
        interactions = new InteractionService(text);
        formats = new FormatService(text, interactions);
        state = new StateService(this, scheduler);
        history = new HistoryService();
        onlinePlayers = new OnlinePlayerService(state);
        onlinePlayers.initialize();
        recipients = new RecipientService(this, scheduler, state);
        pipeline = new ChatPipeline(
                this, scheduler, channels, state, processors, filters, moderation,
                formats, recipients, history, messages
        );
        database = new DatabaseService(this);
        chatListener = new ChatListener(this);
        api = new AnnaChatApiImpl(this);

        state.load();
        try {
            apply(configManager.load(text));
        } catch (RuntimeException exception) {
            getLogger().severe("首次加载配置失败，插件无法启动: " + rootMessage(exception));
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        AnnaChatCommand command = new AnnaChatCommand(this);
        Objects.requireNonNull(getCommand("annachat")).setExecutor(command);
        Objects.requireNonNull(getCommand("annachat")).setTabCompleter(command);
        Bukkit.getPluginManager().registerEvents(new CommandLogListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ConsoleCommandBridge(this), this);
        Bukkit.getPluginManager().registerEvents(onlinePlayers, this);
        Bukkit.getServicesManager().register(AnnaChatApi.class, api, this, ServicePriority.Normal);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderExpansion = new AnnaChatExpansion(this);
            if (placeholderExpansion.register()) {
                getLogger().info("已注册 PlaceholderAPI 扩展");
            } else {
                getLogger().warning("PlaceholderAPI 扩展注册失败");
            }
        }

        getLogger().info("AnnaChat " + getPluginMeta().getVersion()
                + " 已加载；运行平台: " + (isFolia() ? "Folia" : "Paper"));
    }

    @Override
    public void onDisable() {
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
            formats.apply(candidate.formats());
            channels.apply(candidate.channels());
            state.reconcileChannels(channels.ids(), candidate.defaultChannel());
            filters.apply(candidate.filters());
            moderation.apply(candidate.moderation());
            interactions.apply(candidate.interactions());
            history.limit(candidate.historySize());
            messages.apply(candidate);
            runtime.set(candidate);
            chatListener.register(candidate.eventPriority());
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

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public AnnaChatApi getApi() { return api; }
    public RuntimeConfig runtime() { return Objects.requireNonNull(runtime.get(), "配置尚未加载"); }
    public SchedulerService scheduler() { return scheduler; }
    public TextService text() { return text; }
    public MessageService messages() { return messages; }
    public ChannelService channels() { return channels; }
    public ProcessorService processors() { return processors; }
    public FilterService filters() { return filters; }
    public ContentModerationService moderation() { return moderation; }
    public InteractionService interactions() { return interactions; }
    public FormatService formats() { return formats; }
    public StateService state() { return state; }
    public HistoryService history() { return history; }
    public OnlinePlayerService onlinePlayers() { return onlinePlayers; }
    public RecipientService recipients() { return recipients; }
    public ChatPipeline pipeline() { return pipeline; }
    public DatabaseService database() { return database; }
    public Lock configReadLock() { return configLock.readLock(); }
}
