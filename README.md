# UltiWorlds

<div align="center">

[![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)](https://github.com/UltiKits/UltiTools-Reborn)
[![Paper](https://img.shields.io/badge/Paper-1.19%2B-green.svg)](https://papermc.io)
[![License](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE)

**UltiWorlds** 是 UltiTools-API 框架下的多世界管理模块，提供完整的世界创建、传送、保护和背包隔离功能。

Runs on Paper, like the UltiTools-API framework it is a module of (Spigot is not supported). On a Paper build older than 1.19.3 everything works except the line that tells an attacker PvP is off in a world. /
与所依附的 UltiTools-API 框架一样只支持 Paper（不支持 Spigot）。在早于 1.19.3 的 Paper 上，除「此世界已禁用 PVP」提示外其余功能均可用。

[English](#english) | [简体中文](#简体中文)

</div>

---

## 简体中文

### ✨ 功能特性

#### 🌍 世界管理

- **世界创建** - 支持多种环境类型（主世界/下界/末地）和地形类型（标准/超平坦/放大化/巨型生物群系）
- **创建向导** - 交互式对话引导，60秒超时保护
- **世界加载/卸载** - 动态加载和卸载世界，节省服务器资源
- **世界删除** - 安全删除世界（包含确认机制）
- **自动卸载** - 空世界自动卸载，可配置等待时间

#### 🚀 传送系统

- **快速传送** - 支持GUI界面和命令行传送
- **传送冷却** - 可配置的传送冷却时间
- **出生点管理** - 每个世界独立的出生点设置
- **权限控制** - 每个世界可单独设置访问权限

#### 🛡️ 世界保护

- **方块保护** - 禁止破坏/放置方块
- **交互保护** - 禁止与方块交互
- **爆炸保护** - 防止爆炸破坏地形
- **PVP 控制** - 每个世界独立的 PVP 开关
- **生物控制** - 独立控制怪物/动物生成
- **天气控制** - 锁定世界天气

#### 📦 背包隔离
- **独立背包** - 每个世界（组）独立的背包
- **末影箱隔离** - 每个世界独立的末影箱
- **经验隔离** - 可选的经验等级隔离
- **状态隔离** - 可选的生命值/饥饿度/药水效果隔离
- **世界分组** - 支持多个世界共享同一背包组

#### 🔒 访问控制

- **世界锁定** - 临时锁定世界入口
- **世界封禁** - 完全禁止进入世界
- **隐藏世界** - 从列表中隐藏世界

### 📋 命令列表

| 命令 | 权限 | 描述 |
|------|------|------|
| `/world` | `ultiworlds.use` | 打开世界列表 GUI |
| `/world list` | `ultiworlds.use` | 列出所有世界 |
| `/world tp <世界>` | `ultiworlds.use` | 传送到指定世界 |
| `/world info [世界]` | `ultiworlds.use` | 查看世界的全部设置（省略时为当前世界） |
| `/world wizard` | `ultiworlds.admin.create` | 启动创建向导 |
| `/world create <名称> [类型]` | `ultiworlds.admin.create` | 创建世界 |
| `/world delete <世界>` | `ultiworlds.admin.delete` | 删除世界 |
| `/world load <世界>` | `ultiworlds.admin.load` | 加载世界 |
| `/world unload <世界>` | `ultiworlds.admin.unload` | 卸载世界 |
| `/world setspawn` | `ultiworlds.admin.setspawn` | 设置世界出生点 |
| `/world set <世界> <选项> <值>` | `ultiworlds.admin.settings` | 修改世界设置 |
| `/world protect <世界>` | `ultiworlds.admin.protect` | 启用完整保护 |
| `/world unprotect <世界>` | `ultiworlds.admin.protect` | 禁用所有保护 |
| `/world block <世界>` | `ultiworlds.admin.block` | 禁止进入世界 |
| `/world unblock <世界>` | `ultiworlds.admin.block` | 允许进入世界 |

### 🔧 可设置选项

通过 `/world set <世界> <选项> <值>` 命令可以修改以下选项：

| 选项 | 值类型 | 描述 |
|------|--------|------|
| `pvp` | true/false | PVP 开关 |
| `monsters` | true/false | 怪物生成 |
| `animals` | true/false | 动物生成 |
| `weather` | true/false | 天气变化 |
| `hidden` | true/false | 从列表隐藏 |
| `locked` | true/false | 锁定世界 |
| `blocked` | true/false | 禁止进入 |
| `autoUnload` | true/false | 无人时自动卸载 |
| `protectBreak` | true/false | 禁止破坏方块 |
| `protectPlace` | true/false | 禁止放置方块 |
| `protectInteract` | true/false | 禁止交互 |
| `protectExplosion` | true/false | 禁止爆炸破坏 |
| `displayname` | 文本 | 显示名称 |
| `description` | 文本 | 世界描述 |
| `icon` | Material | GUI 图标 |

### ⚙️ 配置文件

```yaml
# config/worlds.yml

# 默认世界名称
default_world: "world"

# 受保护的世界（不能自动卸载或删除）
protected_worlds:
  - "world"
  - "world_nether"
  - "world_the_end"

# 服务器启动时自动加载的世界
load_worlds_on_start: []

# 自动卸载配置
auto_unload:
  enabled: false
  check_interval: 60       # 检查间隔（秒）
  unload_after: 300        # 空世界等待时间（秒）

# 传送配置
tp_to_world:
  enabled: true
  permission_per_world: false
  cooldown: 10

# 背包隔离配置
world_isolation:
  enabled: false
  separate_inventory: true
  separate_ender_chest: true
  separate_experience: false
  separate_health: false
  separate_hunger: false
  separate_effects: false
  shared_worlds:
    - "world,world_nether,world_the_end"
```

### 🔑 权限节点

| 权限 | 描述 |
|------|------|
| `ultiworlds.use` | 基础使用权限 |
| `ultiworlds.admin` | 管理员权限（包含所有子权限） |
| `ultiworlds.admin.create` | 创建世界 |
| `ultiworlds.admin.delete` | 删除世界 |
| `ultiworlds.admin.load` | 加载世界 |
| `ultiworlds.admin.unload` | 卸载世界 |
| `ultiworlds.admin.setspawn` | 设置出生点 |
| `ultiworlds.admin.settings` | 修改世界设置 |
| `ultiworlds.admin.protect` | 管理世界保护 |
| `ultiworlds.admin.block` | 管理世界访问 |
| `ultiworlds.bypass.locked` | 绕过世界锁定 |
| `ultiworlds.bypass.blocked` | 绕过世界封禁 |
| `ultiworlds.bypass.protection` | 绕过世界保护 |
| `ultiworlds.world.*` | 访问所有世界 |
| `ultiworlds.world.<世界名>` | 访问指定世界 |

### ⚠️ 已知限制

- **手工编辑共享 SQLite 数据库中的布尔设置时，只能写 `0` 或 `1`。** 修改世界设置时，模块会把该世界存储的每个值与读取时的值比较，
  只在记录未被改动时才写入（见 `FEATURES.md` 的 `ultiworlds.settings.shared-db-external-change-kept`）。SQLite 把布尔设置存为文本，
  如果手工写入其他拼写，例如 `UPDATE world_settings SET pvp_enabled = 'false' …`，之后对该世界设置的每一次修改都会被当作冲突拒绝，
  控制台记录“更新世界设置失败”，直到该值被改回 `0` 或 `1`。MySQL 按数值比较，不受其他拼写影响。无论 SQLite 还是 MySQL，
  都不要手工把开关类或数值类设置写成 `NULL`：模块会把它读成 `false` 或 `0`，与存储的 `NULL` 永远不相等，于是之后对该世界设置的每一次修改
  都会同样被拒绝，直到重新写入一个值——开关类设置写 `0` 或 `1`，数值类设置写一个数字。

---

## English

### ✨ Features

#### 🌍 World Management

- **World Creation** - Multiple environment types (Normal/Nether/End) and terrain types (Normal/Flat/Amplified/Large Biomes)
- **Creation Wizard** - Interactive dialog with 60-second timeout protection
- **Load/Unload** - Dynamic world loading/unloading to save server resources
- **World Deletion** - Safe world deletion with confirmation
- **Auto-Unload** - Automatic unloading of empty worlds with configurable delay

#### 🚀 Teleportation System

- **Quick Teleport** - GUI and command-line teleportation
- **Teleport Cooldown** - Configurable cooldown between teleports
- **Spawn Management** - Independent spawn points per world
- **Permission Control** - Per-world access permissions

#### 🛡️ World Protection
- **Block Protection** - Prevent block breaking/placing
- **Interaction Protection** - Prevent block interaction
- **Explosion Protection** - Prevent explosion damage
- **PVP Control** - Per-world PVP toggle
- **Mob Control** - Independent monster/animal spawn control
- **Weather Control** - Lock world weather

#### 📦 Inventory Isolation
- **Separate Inventory** - Independent inventory per world (group)
- **Ender Chest Isolation** - Per-world ender chest
- **Experience Isolation** - Optional XP level isolation
- **Status Isolation** - Optional health/hunger/potion effect isolation
- **World Groups** - Multiple worlds can share the same inventory group

#### 🔒 Access Control

- **World Lock** - Temporarily lock world entrance
- **World Block** - Completely block world access
- **Hidden Worlds** - Hide worlds from the list

### 📋 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/world` | `ultiworlds.use` | Open world list GUI |
| `/world list` | `ultiworlds.use` | List all worlds |
| `/world tp <world>` | `ultiworlds.use` | Teleport to world |
| `/world info [world]` | `ultiworlds.use` | View all of a world's settings (the current world when omitted) |
| `/world wizard` | `ultiworlds.admin.create` | Start creation wizard |
| `/world create <name> [type]` | `ultiworlds.admin.create` | Create world |
| `/world delete <world>` | `ultiworlds.admin.delete` | Delete world |
| `/world load <world>` | `ultiworlds.admin.load` | Load world |
| `/world unload <world>` | `ultiworlds.admin.unload` | Unload world |
| `/world setspawn` | `ultiworlds.admin.setspawn` | Set world spawn |
| `/world set <world> <option> <value>` | `ultiworlds.admin.settings` | Modify world settings |
| `/world protect <world>` | `ultiworlds.admin.protect` | Enable full protection |
| `/world unprotect <world>` | `ultiworlds.admin.protect` | Disable all protection |
| `/world block <world>` | `ultiworlds.admin.block` | Block world access |
| `/world unblock <world>` | `ultiworlds.admin.block` | Unblock world access |

### 📦 Installation

1. Install [UltiTools-API](https://github.com/UltiKits/UltiTools-Reborn) (required)
2. Download UltiWorlds module
3. Place in `plugins/UltiTools/plugins/` directory
4. Restart server
5. Configure `config/worlds.yml` as needed

### ⚠️ Known limitations

- **When editing a boolean setting by hand in a shared SQLite database, write only `0` or `1`.** A world-settings
  change compares every stored value of that world with what it read and writes only if the row is unchanged (see
  `ultiworlds.settings.shared-db-external-change-kept` in `FEATURES.md`). SQLite stores boolean settings as text, so a
  hand edit that writes another spelling, such as `UPDATE world_settings SET pvp_enabled = 'false' ...`, makes every
  later change of that world's settings be refused as contended, and the console logs "Failed to update world
  settings", until the value is set back to `0` or `1`. MySQL compares numerically and is not affected by other
  spellings. On SQLite and MySQL alike, never write `NULL` by hand into a true/false or number setting: it is read back
  as `false` or `0`, which never compares equal to the stored `NULL`, so every later change of that world's settings is
  refused the same way until a value is written back -- `0` or `1` for a true/false setting, a number for a number setting.

### 🔄 Migration from UltiTools 5.x

UltiWorlds 2.0 is the successor to the multi-world functionality in UltiTools 5.x. Key improvements:

| Feature | UltiTools 5.x | UltiWorlds 2.0 |
|---------|---------------|----------------|
| Framework | Manual registration | Annotation-driven |
| Command System | AbstractTabExecutor | BaseCommandExecutor |
| Data Storage | AbstractDataEntity | BaseDataEntity<Integer> |
| GUI | Manual InventoryHolder | BasePaginationPage |
| World Protection | Basic | Full (break/place/interact/explosion) |
| Inventory Isolation | None | Full with groups |
| Creation Wizard | None | Conversation API |

### 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

### 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### 📧 Support

- GitHub Issues: [Report a bug](https://github.com/UltiKits/UltiTools-Reborn/issues)
- Discord: [UltiKits Community](https://discord.gg/ultikits)
