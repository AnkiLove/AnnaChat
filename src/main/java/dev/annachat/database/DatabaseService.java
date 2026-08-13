package dev.annachat.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.annachat.AnnaChat;
import dev.annachat.api.ModerationMatch;
import dev.annachat.api.context.ChatContext;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public final class DatabaseService {
    private static final Pattern SAFE_PREFIX = Pattern.compile("[A-Za-z0-9_]+");

    private final AnnaChat plugin;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private volatile Handle current;

    public DatabaseService(AnnaChat plugin) {
        this.plugin = plugin;
    }

    public synchronized void apply(YamlConfiguration file) {
        if (!file.getBoolean("enabled", false)) {
            Handle previous = current;
            current = null;
            if (previous != null) closeAsync(previous);
            plugin.getLogger().info("MySQL 记录功能已关闭");
            return;
        }

        DatabaseSettings settings = parse(file);
        HikariConfig hikari = new HikariConfig();
        String url = "jdbc:mysql://" + settings.host + ":" + settings.port + "/" + settings.database
                + (settings.parameters.isBlank() ? "" : "?" + settings.parameters);
        hikari.setJdbcUrl(url);
        hikari.setUsername(settings.username);
        hikari.setPassword(settings.password);
        hikari.setPoolName("AnnaChat-MySQL-" + generation.incrementAndGet());
        hikari.setMinimumIdle(settings.minimumIdle);
        hikari.setMaximumPoolSize(settings.maximumPoolSize);
        hikari.setConnectionTimeout(settings.connectionTimeout);
        hikari.setIdleTimeout(settings.idleTimeout);
        hikari.setMaxLifetime(settings.maxLifetime);
        hikari.setInitializationFailTimeout(-1);

        HikariDataSource pool = new HikariDataSource(hikari);
        CompletableFuture<Void> ready = CompletableFuture.runAsync(() -> createTables(pool, settings), executor);
        Handle next = new Handle(pool, settings, ready);
        Handle previous = current;
        current = next;
        ready.whenComplete((ignored, error) -> {
            if (error == null) {
                plugin.getLogger().info("MySQL 记录功能已就绪");
            } else {
                plugin.getLogger().severe("MySQL 初始化失败: " + rootMessage(error));
            }
        });
        if (previous != null) {
            closeAsync(previous);
        }
    }

    public void validate(YamlConfiguration file) {
        if (file.getBoolean("enabled", false)) {
            parse(file);
        }
    }

    public boolean enabled() {
        return current != null;
    }

    public void logChat(ChatContext context) {
        Handle handle = current;
        if (handle == null || !handle.settings.logChat) return;
        ChatRecord record = new ChatRecord(
                handle.settings.serverId,
                context.senderSnapshot().uniqueId().toString(),
                context.senderSnapshot().name(),
                context.channel().id(),
                context.senderSnapshot().worldKey().asString(),
                context.originalMessage(),
                context.message(),
                context.createdAt()
        );
        enqueue(handle, () -> insertChat(handle, record), "写入聊天记录失败");
    }

    public void logCommand(String uuid, String playerName, String world, String command, Instant createdAt) {
        Handle handle = current;
        if (handle == null || !handle.settings.logCommands || excluded(handle.settings, command)) return;
        CommandRecord record = new CommandRecord(
                handle.settings.serverId, uuid, playerName, world, command, createdAt
        );
        enqueue(handle, () -> insertCommand(handle, record), "写入指令记录失败");
    }

    /**
     * 异步记录被内容审核拦截的聊天。原文在发送者实体线程创建快照后传入，
     * 数据库线程不会访问 Player 或其他 Bukkit 实体对象。
     */
    public void logModeration(ChatContext context, ModerationMatch match, int warningCount) {
        Handle handle = current;
        if (handle == null || !handle.settings.logModeration) return;
        ModerationRecord record = new ModerationRecord(
                handle.settings.serverId,
                context.senderSnapshot().uniqueId().toString(),
                context.senderSnapshot().name(),
                context.channel().id(),
                context.senderSnapshot().worldKey().asString(),
                match.categoryId(),
                match.matchedWord(),
                match.reason(),
                warningCount,
                context.originalMessage(),
                context.createdAt()
        );
        enqueue(handle, () -> insertModeration(handle, record), "写入内容审核记录失败");
    }

    public synchronized void close() {
        Handle handle = current;
        current = null;
        if (handle != null) {
            handle.beginClosing();
            handle.closeWhenDrained();
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("数据库写入队列未能在 10 秒内完全结束");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 把一次写入登记到对应连接池。连接池热切换时会等待所有已登记任务结束。
     */
    private void enqueue(Handle handle, Runnable operation, String failureMessage) {
        if (!handle.acquire()) return;
        handle.ready.thenRunAsync(operation, executor).whenComplete((ignored, error) -> {
            handle.release();
            if (error != null) plugin.getLogger().warning(failureMessage + ": " + rootMessage(error));
        });
    }

    private void closeAsync(Handle handle) {
        handle.beginClosing();
        executor.submit(handle::closeWhenDrained);
    }

    private void createTables(HikariDataSource pool, DatabaseSettings settings) {
        String chatTable = table(settings, "chat_logs");
        String commandTable = table(settings, "command_logs");
        String moderationTable = table(settings, "moderation_logs");
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                      server_id VARCHAR(64) NOT NULL,
                      player_uuid CHAR(36) NOT NULL,
                      player_name VARCHAR(32) NOT NULL,
                      channel_id VARCHAR(64) NOT NULL,
                      world_key VARCHAR(128) NOT NULL,
                      original_message TEXT NOT NULL,
                      final_message TEXT NOT NULL,
                      created_at TIMESTAMP(3) NOT NULL,
                      PRIMARY KEY (id),
                      INDEX idx_player_time (player_uuid, created_at),
                      INDEX idx_channel_time (channel_id, created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(chatTable));
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                      server_id VARCHAR(64) NOT NULL,
                      player_uuid CHAR(36) NOT NULL,
                      player_name VARCHAR(32) NOT NULL,
                      world_key VARCHAR(128) NOT NULL,
                      command_text TEXT NOT NULL,
                      created_at TIMESTAMP(3) NOT NULL,
                      PRIMARY KEY (id),
                      INDEX idx_player_time (player_uuid, created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(commandTable));
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                      server_id VARCHAR(64) NOT NULL,
                      player_uuid CHAR(36) NOT NULL,
                      player_name VARCHAR(32) NOT NULL,
                      channel_id VARCHAR(64) NOT NULL,
                      world_key VARCHAR(128) NOT NULL,
                      category_id VARCHAR(64) NOT NULL,
                      matched_word TEXT NOT NULL,
                      reason TEXT NOT NULL,
                      warning_count INT UNSIGNED NOT NULL,
                      original_message TEXT NOT NULL,
                      created_at TIMESTAMP(3) NOT NULL,
                      PRIMARY KEY (id),
                      INDEX idx_player_time (player_uuid, created_at),
                      INDEX idx_category_time (category_id, created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(moderationTable));
        } catch (SQLException exception) {
            throw new CompletionException(exception);
        }
    }

    private void insertChat(Handle handle, ChatRecord record) {
        String sql = "INSERT INTO " + table(handle.settings, "chat_logs")
                + " (server_id,player_uuid,player_name,channel_id,world_key,original_message,final_message,created_at)"
                + " VALUES (?,?,?,?,?,?,?,?)";
        try (Connection connection = handle.pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.serverId);
            statement.setString(2, record.uuid);
            statement.setString(3, record.playerName);
            statement.setString(4, record.channel);
            statement.setString(5, record.world);
            statement.setString(6, record.originalMessage);
            statement.setString(7, record.finalMessage);
            statement.setTimestamp(8, Timestamp.from(record.createdAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new CompletionException(exception);
        }
    }

    private void insertCommand(Handle handle, CommandRecord record) {
        String sql = "INSERT INTO " + table(handle.settings, "command_logs")
                + " (server_id,player_uuid,player_name,world_key,command_text,created_at) VALUES (?,?,?,?,?,?)";
        try (Connection connection = handle.pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.serverId);
            statement.setString(2, record.uuid);
            statement.setString(3, record.playerName);
            statement.setString(4, record.world);
            statement.setString(5, record.command);
            statement.setTimestamp(6, Timestamp.from(record.createdAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new CompletionException(exception);
        }
    }

    private void insertModeration(Handle handle, ModerationRecord record) {
        String sql = "INSERT INTO " + table(handle.settings, "moderation_logs")
                + " (server_id,player_uuid,player_name,channel_id,world_key,category_id,matched_word,reason,warning_count,original_message,created_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = handle.pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.serverId);
            statement.setString(2, record.uuid);
            statement.setString(3, record.playerName);
            statement.setString(4, record.channel);
            statement.setString(5, record.world);
            statement.setString(6, record.categoryId);
            statement.setString(7, record.matchedWord);
            statement.setString(8, record.reason);
            statement.setInt(9, record.warningCount);
            statement.setString(10, record.originalMessage);
            statement.setTimestamp(11, Timestamp.from(record.createdAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new CompletionException(exception);
        }
    }

    private static DatabaseSettings parse(YamlConfiguration file) {
        String prefix = file.getString("table-prefix", "annachat_");
        if (!SAFE_PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("database.yml 的 table-prefix 只能包含字母、数字和下划线");
        }
        ConfigurationSection mysql = required(file, "mysql");
        ConfigurationSection pool = required(file, "pool");
        Set<String> exclusions = new HashSet<>();
        for (String command : file.getStringList("logging.command-exclusions")) {
            exclusions.add(command.toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        }
        return new DatabaseSettings(
                file.getString("server-id", "default"),
                prefix,
                mysql.getString("host", "127.0.0.1"),
                mysql.getInt("port", 3306),
                mysql.getString("database", "minecraft"),
                mysql.getString("username", "root"),
                mysql.getString("password", ""),
                mysql.getString("parameters", ""),
                Math.max(0, pool.getInt("minimum-idle", 1)),
                Math.max(1, pool.getInt("maximum-pool-size", 6)),
                Math.max(250, pool.getLong("connection-timeout-millis", 5000)),
                Math.max(10000, pool.getLong("idle-timeout-millis", 600000)),
                Math.max(30000, pool.getLong("max-lifetime-millis", 1800000)),
                file.getBoolean("logging.chat", true),
                file.getBoolean("logging.commands", true),
                file.getBoolean("logging.moderation", true),
                Set.copyOf(exclusions)
        );
    }

    private static boolean excluded(DatabaseSettings settings, String command) {
        String value = command.startsWith("/") ? command.substring(1) : command;
        int space = value.indexOf(' ');
        String root = (space < 0 ? value : value.substring(0, space)).toLowerCase(Locale.ROOT);
        return settings.commandExclusions.contains(root);
    }

    private static String table(DatabaseSettings settings, String suffix) {
        return "`" + settings.tablePrefix + suffix + "`";
    }

    private static ConfigurationSection required(YamlConfiguration file, String path) {
        ConfigurationSection section = file.getConfigurationSection(path);
        if (section == null) throw new IllegalArgumentException("database.yml 缺少 " + path);
        return section;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    /**
     * 单代连接池及其待处理写入计数。同步范围只覆盖计数，不覆盖数据库 I/O。
     */
    private static final class Handle {
        private final HikariDataSource pool;
        private final DatabaseSettings settings;
        private final CompletableFuture<Void> ready;
        private int pendingWrites;
        private boolean closing;

        private Handle(HikariDataSource pool, DatabaseSettings settings, CompletableFuture<Void> ready) {
            this.pool = pool;
            this.settings = settings;
            this.ready = ready;
        }

        private synchronized boolean acquire() {
            if (closing) return false;
            pendingWrites++;
            return true;
        }

        private synchronized void release() {
            pendingWrites--;
            if (pendingWrites == 0) notifyAll();
        }

        private synchronized void beginClosing() {
            closing = true;
        }

        private void closeWhenDrained() {
            synchronized (this) {
                while (pendingWrites > 0) {
                    try {
                        wait();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            pool.close();
        }
    }
    private record ChatRecord(String serverId, String uuid, String playerName, String channel, String world,
                              String originalMessage, String finalMessage, Instant createdAt) {}
    private record CommandRecord(String serverId, String uuid, String playerName, String world,
                                 String command, Instant createdAt) {}
    private record ModerationRecord(String serverId, String uuid, String playerName, String channel,
                                    String world, String categoryId, String matchedWord, String reason,
                                    int warningCount, String originalMessage, Instant createdAt) {}
    private record DatabaseSettings(
            String serverId, String tablePrefix, String host, int port, String database,
            String username, String password, String parameters,
            int minimumIdle, int maximumPoolSize, long connectionTimeout, long idleTimeout, long maxLifetime,
            boolean logChat, boolean logCommands, boolean logModeration, Set<String> commandExclusions
    ) {}
}
