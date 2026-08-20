<p align="center">
  <img src="docs/assets/annachat-banner.jpg" alt="AnnaChat Paper and Folia chat management" width="100%">
</p>

<div align="center">
  <h1>AnnaChat</h1>
  <p>面向 Paper 与 Folia 的模块化聊天管理插件</p>
  <p>
    <a href="https://github.com/AnkiLove/AnnaChat/releases"><img src="https://img.shields.io/github/v/release/AnkiLove/AnnaChat?style=flat-square&label=release" alt="Release"></a>
    <a href="https://github.com/AnkiLove/AnnaChat/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/AnkiLove/AnnaChat/build.yml?style=flat-square&label=build" alt="Build"></a>
    <img src="https://img.shields.io/badge/Java-25-437291?style=flat-square" alt="Java 25">
    <img src="https://img.shields.io/badge/Paper%20%7C%20Folia-26.x-00a9e0?style=flat-square" alt="Paper and Folia 26.x">
  </p>
</div>

## 项目概览

AnnaChat 是一个以配置为中心的 Minecraft 聊天管理插件。它将频道、格式、点击交互、内容审核、玩家关系、历史记录和审计存储拆分为独立模块，适合需要长期维护和扩展的 Paper/Folia 服务器。

### 核心能力

| 模块 | 能力 |
| --- | --- |
| 频道 | 全局、附近、好友三种频道；支持默认频道、快捷前缀、频道屏蔽和权限控制 |
| 格式 | MiniMessage 分段格式；每个片段可配置悬停、点击、插入文本和占位符 |
| 交互 | 链接、在线玩家提及、聊天补全、命令建议和物品展示，并配置独立点击事件 |
| 审核 | 脏话与涉政词库、大小写/全角归一化、忽略字符、白名单和警告计数 |
| 审计 | MySQL 可选记录聊天、玩家指令和审核原文，默认关闭 |
| 兼容 | Paper/Folia 自动识别；同一 JAR 使用对应平台调度策略 |
| 扩展 | PlaceholderAPI、Java API、Bukkit 事件、频道/过滤器/处理器扩展 |

## 兼容性

| 项目 | 要求 |
| --- | --- |
| 服务端 | Paper 26.1+ 或 Folia 26.1+ |
| Java | Java 25 |
| PlaceholderAPI | 可选；安装后自动注册 AnnaChat 扩展 |
| MySQL | 可选；`database.yml` 中 `enabled` 默认为 `false` |

插件启动时会自动判断平台，并在控制台输出完整的 10 阶段加载流程：平台识别、调度器、配置、核心服务、状态、运行配置、聊天事件、命令/API、可选集成和最终完成状态。

## 快速开始

1. 从 [Releases](https://github.com/AnkiLove/AnnaChat/releases) 下载最新 JAR。
2. 将 JAR 放入服务端的 `plugins` 目录。
3. 使用 Java 25 启动 Paper 或 Folia。
4. 首次启动后按需编辑生成的 YAML 文件。
5. 执行 `/annachat help` 查看带分页和点击操作的帮助。

升级时直接替换 JAR 并重启服务器即可。旧配置会继续使用，新增配置项会在内存中采用安全默认值。

## 默认频道

| 频道 | 用途 | 快捷前缀 |
| --- | --- | --- |
| `global` | 向全服在线玩家发送 | `!` |
| `local` | 向附近且同世界的玩家发送 | `~` |
| `friends` | 向已建立双向好友关系的在线好友发送 | `#` |

快捷前缀只影响当前消息，不会改变玩家的默认频道。默认频道和前缀均可在 `config.yml` 调整。

## 常用命令

| 命令 | 说明 |
| --- | --- |
| `/annachat help [页码]` | 查看分页帮助 |
| `/annachat channel [频道]` | 查看或切换默认频道 |
| `/annachat toggle <频道>` | 屏蔽或恢复接收指定频道 |
| `/annachat friend add <玩家>` | 发送好友申请 |
| `/annachat friend accept <玩家>` | 接受好友申请 |
| `/annachat friend deny <玩家>` | 拒绝好友申请 |
| `/annachat friend remove <玩家>` | 移除好友 |
| `/annachat friend list` | 查看好友列表 |
| `/annachat friend requests` | 查看待处理申请 |
| `/annachat reload` | 热重载全部配置 |
| `/annachat history [页码]` | 查看内存聊天历史 |
| `/annachat preview <频道或格式> [消息]` | 预览聊天格式 |
| `/annachat mute <玩家> <时长> [原因]` | 管理员禁言玩家 |
| `/annachat unmute <玩家>` | 解除禁言 |
| `/annachat spy` | 开关频道监听 |

命令别名：`/ac`、`/achat`。普通玩家默认拥有聊天、频道切换、频道屏蔽和好友管理权限。

## 聊天颜色与玩家提及

- 拥有 `annachat.chat.color` 权限的玩家可在聊天中使用 `&a`、`&l`、`&#RRGGBB` 和 `&x&F&F&0&0&A&A` 等颜色或样式代码；该权限默认授予 OP。
- `annachat.chat.minimessage` 控制玩家直接使用 MiniMessage 标签，同样默认授予 OP。
- 输入 `@玩家名` 可提及在线玩家，按 Tab 能自动补全名字；`annachat.chat.mention` 默认授予所有玩家。
- 被提及者只有在实际收到当前频道消息时才会播放铁砧提示音，发送者提及自己不会播放。
- 提及开关、权限、自动补全、声音、音量和音调均可在 `config.yml` 的 `mentions` 区域热重载。

## 配置文件

| 文件 | 作用 |
| --- | --- |
| `config.yml` | 主设置、快捷频道前缀、消息文本、格式权限和启动策略 |
| `channels.yml` | 频道受众、半径、权限、冷却和格式绑定 |
| `formats.yml` | 聊天格式片段、悬停文字、点击动作和插入文本 |
| `interactions.yml` | 正则交互规则，例如链接、提及和命令建议 |
| `filters.yml` | 通用过滤器、替换、拦截和影子消息规则 |
| `moderation.yml` | 脏话/涉政分类、词条、白名单和警告策略 |
| `placeholders.yml` | 自定义占位符模板 |
| `database.yml` | MySQL 连接、记录类型和敏感指令排除项 |
| `messages.yml` | 分页帮助和系统消息的可选独立配置 |

执行 `/annachat reload` 会异步校验全部配置。校验失败时保留当前运行配置，不会替换为半成品状态。

## 占位符与点击事件

安装 PlaceholderAPI 后可使用 `%annachat_*%` 变量，例如：

- `%annachat_channel%`
- `%annachat_channel_display%`
- `%annachat_muted%`
- `%annachat_mute_remaining%`
- `%annachat_spy%`
- `%annachat_online%`
- `%annachat_version%`
- `%annachat_custom_<键名>%`

聊天正文还支持只读物品预览：

- `%1` 到 `%9`：展示快捷栏第 1 到第 9 格的物品。
- `%i`：展示储物栏中的全部物品，并可选包含装备栏和副手。

鼠标悬停物品名称即可查看原版物品信息。物品展示不会附加点击动作、执行命令或物品取出功能；`config.yml` 的 `item-display.enabled`、`permission`、`include-armor` 和 `include-offhand` 可控制该功能。

格式片段支持 `{player}`、`{world}`、`{channel_display}` 和 `{custom:键名}`。玩家在聊天正文中使用 PAPI 变量需要 `annachat.chat.placeholders` 权限。

格式片段的点击动作支持：

- `RUN_COMMAND`
- `SUGGEST_COMMAND`
- `OPEN_URL`
- `COPY_TO_CLIPBOARD`

第三方占位符返回的旧式 `§` 颜色码会在显示边界转换为 MiniMessage；点击命令和插入文本仍保留原始值。

## 内容审核与 MySQL 审计

`moderation.yml` 默认启用本地分类审核。命中后消息不会广播，玩家会收到分类警告；拥有 `annachat.bypass.moderation` 权限的玩家可以绕过审核。

审核词库只从本地配置读取，不会在运行时下载远程词表。开启 MySQL 后，插件会自动建立：

- `annachat_chat_logs`
- `annachat_command_logs`
- `annachat_moderation_logs`

被拦截消息的 `original_message` 会以明文写入审核表，便于管理员审计。数据库默认关闭，连接池、记录类型和敏感指令排除项均可配置。

## Paper/Folia 线程模型

- Paper 模式使用 Bukkit 主线程、异步调度器和 Bukkit 定时任务。
- Folia 模式使用玩家实体调度器、全局区域调度器和 Folia 异步调度器。
- 异步聊天入口只捕获不可变文本，频道、权限、冷却和玩家状态回到当前平台的发送者调度器处理。
- 每个接收者的权限、世界、距离、屏蔽状态和消息发送在接收者所属的平台调度器执行。
- 聊天事件同时衔接旧式事件和 Paper `AsyncChatEvent`；转换链中断时，默认在 1 tick 后自动回退。
- 同一输入只允许一次正式认领，避免首条消息丢失、等待下一条消息或重复广播。
- 数据库存取和文件保存在异步调度中执行，不在区域线程进行 I/O。
- API 的接收者谓词运行于候选接收者线程，不应从回调访问发送者实体；请使用 `PlayerSnapshot`。

## 开发 API

其他插件可通过 `AnnaChat#getApi()` 获取 API，注册或管理：

- 频道
- 占位符
- 消息处理器
- 过滤器
- 交互提供器
- 格式片段提供器
- 消息发送后处理器
- 内容审核检查

API 类型位于 `dev.annachat.api` 包，插件同时提供 `AnnaChatProcessEvent` 和 `AnnaChatPostEvent`。

## 构建

```powershell
& 'C:\Program Files\Zulu\zulu-25\bin\java.exe' -version
.\gradlew.bat clean build
```

构建产物：`out/AnnaChat-1.1.6.jar`。

## 目录结构

```text
src/main/java/dev/annachat/api       公共 API、上下文和事件
src/main/java/dev/annachat/config    配置模型与校验
src/main/java/dev/annachat/service   频道、聊天、审核、数据库和调度服务
src/main/java/dev/annachat/platform  Paper/Folia 平台识别
src/main/resources                   默认 YAML、plugin.yml 和帮助文本
docs/assets                          README 横幅资源
```

## 发布

当前稳定版本：[v1.1.8](https://github.com/AnkiLove/AnnaChat/releases/tag/v1.1.8)

仓库主题标签：`minecraft`、`minecraft-plugin`、`paper`、`folia`、`chat`、`java`、`gradle`、`placeholderapi`、`mysql`、`minimessage`。
