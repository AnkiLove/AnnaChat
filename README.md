# AnnaChat

AnnaChat 是面向 Paper 26.1+、Java 25 的模块化聊天管理插件。

## 主要能力

- 全服、附近和好友频道，支持前缀临时切换与玩家默认频道。
- MiniMessage 分段格式，每个片段可独立配置悬停文字、点击动作和插入文本。
- 可配置正则交互规则，适合链接、提及、命令建议等聊天内点击事件。
- 过滤器、冷却、禁言、频道屏蔽、监听模式、历史查询与格式预览。
- PlaceholderAPI 可选支持，并提供独立 Java API 与 Bukkit 事件。
- Paper/Folia 共用同一份 JAR，实体访问和消息投递遵循区域线程所有权。
- 双事件聊天入口衔接旧式兼容事件与 Paper 事件；转换链中断时会在下一 tick 自动回退，避免进服第一句消息丢失或等待第二句才发送。
- 可选 MySQL 聊天与玩家指令审计，默认关闭，连接池与敏感指令排除项均可配置。
- 配置重载采用先校验后替换，错误配置不会覆盖当前可用状态。
- 本地分类内容审核支持脏话与涉政词库、规避字符归一化、白名单、玩家警告及 MySQL 明文审计。

## 构建

```powershell
& 'C:\Program Files\Zulu\zulu-25\bin\java.exe' -version
.\gradlew.bat clean build
```

产物位于 `out/AnnaChat-1.1.2.jar`。

## 命令

使用 `/annachat help` 查看带分页和点击操作的完整帮助。常用别名为 `/ac`。

普通玩家默认拥有聊天、频道切换、频道屏蔽和好友管理权限。使用 `/annachat friend add <玩家>` 发送申请，`/annachat friend requests` 查看并点击处理申请。好友关系与未处理申请保存于 `data.yml`；好友频道只发送给自己及已建立双向关系的在线好友。

## 开发接口

其他插件可通过 `AnnaChat#getApi()` 获取 API，注册频道、占位符、消息处理器、过滤器、交互提供器、格式片段提供器和发送后处理器。接口位于 `dev.annachat.api` 包。

## 占位符

安装 PlaceholderAPI 后，AnnaChat 会自动注册内部扩展，并在聊天格式、悬停文字、点击值、插入文字、交互规则与面向玩家的系统消息中解析 PAPI 变量。

内置变量：

- `%annachat_channel%`：玩家当前频道 ID。
- `%annachat_channel_display%`：当前频道显示名。
- `%annachat_muted%`：是否被禁言。
- `%annachat_mute_remaining%`：禁言剩余时间。
- `%annachat_mute_reason%`：禁言原因。
- `%annachat_spy%`：是否开启频道监听。
- `%annachat_hidden_<频道ID>%`：是否屏蔽指定频道。
- `%annachat_online%`：在线人数。
- `%annachat_version%`：插件版本。
- `%annachat_custom_<键名>%`：读取 `placeholders.yml` 中的自定义变量。

格式文件也可以使用短变量，如 `{player}`、`{world}`、`{channel_display}` 和 `{custom:键名}`。玩家在聊天正文中输入 PAPI 变量需要 `annachat.chat.placeholders` 权限。

第三方占位符返回的 `§0`—`§f`、`§k`—`§o`、`§r` 和 `§x§R§R§G§G§B§B` 旧式格式码会在显示边界自动转换为 MiniMessage，点击命令与插入文字仍保留原始值。

## 热重载

`/annachat reload` 会异步读取并校验 `config.yml`、频道、格式、交互、通用过滤器、分类审核词库、帮助、自定义占位符和数据库配置。校验成功后在全局区域线程切换配置，玩家聊天处理通过读写锁避免观察到混合配置；失败时保留当前运行状态。

## 分类内容审核

`moderation.yml` 默认开启脏话和涉政言论分类。匹配会统一大小写与全角字符，并可忽略空白、标点和指定字符；每个分类可独立维护词条、优先级、原因和白名单。命中后消息不会广播，玩家会收到分类警告。拥有 `annachat.bypass.moderation` 的玩家可绕过此模块。

审核词库只读取本地配置，不会在运行时下载远程词表。其他插件可通过 `AnnaChatApi#inspectModeration(String)` 复用当前不可变词库快照。

## MySQL 记录

`database.yml` 中的 `enabled` 默认为 `false`。开启后可分别控制聊天、玩家指令和审核记录，插件会自动建立 `annachat_chat_logs`、`annachat_command_logs` 与 `annachat_moderation_logs` 表。审核表的 `original_message` 保存被屏蔽消息的完整明文，`warning_count` 保存当前警告窗口内的累计次数。数据库操作使用虚拟线程与 HikariCP，写入内容是在玩家实体线程创建的不可变快照。

## Folia 线程约定

- 异步聊天事件只捕获不可变文本；频道、权限、冷却和玩家状态统一回到发送者的 `EntityScheduler` 处理。
- 正常聊天由 Paper 事件认领，旧事件只保留一条短期回退任务；同一输入不会被两套事件重复广播。
- 玩家与发送者数据在其 `EntityScheduler` 上读取。
- 每个接收者的权限、世界、距离、屏蔽状态与消息发送均在该接收者自己的 `EntityScheduler` 上执行。
- 在线玩家快照与周期维护使用 `GlobalRegionScheduler`。
- 数据库存取和文件保存使用异步调度，不在区域线程执行 I/O。
- API 中接收者谓词运行于候选接收者线程，不应从该回调访问发送者实体；请使用 `PlayerSnapshot`。
