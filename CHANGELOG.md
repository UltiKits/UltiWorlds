# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Added

- On startup, and again on every `/ul reload`, this module now checks your own `config/worlds.yml`
  for the eight keys this version no longer reads (the entries under Removed) and logs one warning per
  leftover, naming the module, the file and the key, and saying where the text comes from. Deleting
  a key from the module stops a *fresh* file being written with it but does nothing to the file you
  already have, so without this warning an edited value would go on meaning nothing, silently. Delete
  the keys from the file to silence it (part of UltiKits/UltiWorlds#38).
- 本模块现在会在启动时、以及每次 `/ul reload` 时检查你自己的 `config/worlds.yml` 中是否仍存在本版本不再读取的
  八个配置键（见下方 Removed 中的条目），并为每个残留键各记一条警告，点明模块、文件与键名，并说明文本来自哪里。
  从模块中删除一个键，只会让**新生成**的文件里不再有它，对你已有的文件没有任何影响，因此若没有这条警告，被改过的值
  会继续悄无声息地不起作用。把这些键从文件中删除即可不再提示（UltiKits/UltiWorlds#38 的一部分）。

### Changed

- `/world delete <name>` now asks before it deletes. After the checks it always made (permission,
  a world by that name exists, not the default world, not in `protected_worlds`), it opens a
  confirmation window titled "Confirm Delete: <name>" instead of deleting on the spot; the world is
  deleted only when you click the window's green `OK` button. The red `Cancel` button, or closing
  the window any other way, deletes nothing. Clicking `OK` checks the permission, the default world
  and `protected_worlds` again, because they can change while the window is open, and a window
  deletes at most once, never after it has been closed, and only for a click on its own `OK` button
  (not for a click at the same slot in your own inventory). The window is valid for 30 seconds, the
  same limit and clock as the console's confirmation: `OK` after that deletes nothing and asks you
  to run the command again. If this module deletes that world for another request while your window
  is open -- or starts to and fails part-way -- your `OK` deletes nothing either. The chat lines change with it: the old "Deleting world <name>, please
  wait..." / "World <name> has been deleted!" / "Failed to delete the world!" lines
  (`world.delete.deleting`, `world.delete.success`, `world.delete.failed`) are no longer sent to a
  player; the window sends "World <name> has been deleted" or "Failed to delete world <name>"
  (`command.delete.success`, `command.delete.failed`), and "Delete operation cancelled"
  (`command.delete.cancelled`) for `Cancel`. If you customised those three old lines for players, carry
  your text over to the new keys in a custom language file, not in the official `lang/*.yml`: copy the
  official file in the same `lang/` folder to a name that starts with its language code and a hyphen
  (for example `lang/en-myserver.yml`) and set `language: en-myserver` in `plugins/UltiTools/config.yml`.
  An earlier edit made in an official file is not kept: the first start after the upgrade restores the
  file and keeps your previous file as `.bak`, where your old text is (UltiKits/UltiTools-Reborn#616). The
  old lines are now the console's (next entry) (UltiKits/UltiWorlds#19).
- **The server console can now delete a world, behind a typed confirmation.** Until now the whole
  `/world` command was player-only, so the console could not run `/world delete` at all. It now
  can, and it never deletes on the first request: `/world delete <name>` from the console makes the
  same checks as for a player (a world by that name exists, it is not the default world, it is not
  in `protected_worlds`), deletes nothing, and asks you to run the same command again within 30
  seconds. Only that repeat -- same world name, spelled and cased exactly the same, within 30
  seconds -- deletes, and it makes every check again first; it then prints "Deleting world <name>,
  please wait..." followed by "World <name> has been deleted!" or "Failed to delete the world!". A
  repeat after 30 seconds deletes nothing and starts a new 30-second wait; a repeat made after this
  module has deleted (or started deleting) a world by that name for another request deletes nothing, says so, and counts
  as a new request; a check that refuses the
  request or the repeat cancels the pending request; each request allows one deletion. A pending
  request lives in memory only and is lost on restart, and the 30 seconds are measured on a clock
  that changes to the system time do not affect. Command blocks and other non-player,
  non-console senders are refused, and so is RCON (the remote console protocol used by tools such
  as `mcrcon` and many chat bridges): it cannot delete a world. Every other `/world` subcommand stays player-only, as before;
  `/world help` from the console now lists only `/world delete` (UltiKits/UltiWorlds#19).
- **Every command that runs as the server console counts as the console**, and they all share one
  identity: each reports the name `CONSOLE`, so there is one pending confirmation per world name
  for all of them together, and any one of them confirms a request made by any other. That
  includes UltiPanel remote commands, post-teleport commands added with `/world postcmd add`, and
  console commands run by other modules or plugins (for example scheduled commands, menu actions,
  kit commands and mail commands).
  - **UltiPanel.** A panel user can delete a world by sending `world delete <name>` twice within 30
    seconds, but the panel never shows the prompt, the outcome or a refusal: the command body runs
    one tick after the panel has already read its output, so the panel's command result always
    reads "Command executed successfully". The prompt, the outcome and any refusal appear only as
    lines in the server log (and in the panel's log view, if log streaming is on).
  - **Post-teleport commands.** A `world delete <name>` stored as a post-teleport command runs every
    time any player teleports into that world with `/world tp` or the world list. Any two such
    teleports within 30 seconds, by the same player or by different players, delete the named world;
    so do one such teleport plus the same command from the console, the panel or any other
    console-run command within 30 seconds. Stored as `world delete {world}`, it targets whichever
    world is teleported into. The prompt goes to the console, so the teleporting players are never
    told. Do not add one. Adding post-teleport commands requires `ultiworlds.admin.settings`, so that
    permission is in effect enough to delete any unprotected world (UltiKits/UltiWorlds#19).
- **Known limitation.** A delete confirmation, from a player or from the console, does not try to
  recognise "the same world": it is bound to its 30 seconds and to this module's own deletions. A
  world deleted by another plugin or by hand, and created again under the same name within those 30
  seconds, is deleted by a confirmation given for the one before it (UltiKits/UltiWorlds#19).
- `/world delete <名称>` 现在会在删除前先询问。它在原有检查（权限、该名称的世界存在、不是默认世界、
  不在 `protected_worlds` 中）之后，不再立即删除，而是打开标题为"Confirm Delete: <名称>"的确认窗口；
  只有点击窗口中绿色的 `OK` 按钮才会删除世界。点击红色的 `Cancel` 按钮，或以其他任何方式关闭窗口，都
  不会删除任何内容。点击 `OK` 时会再次检查权限、默认世界与 `protected_worlds`，因为窗口打开期间它们可能
  发生变化；并且一个窗口最多只执行一次删除，关闭后不再执行，且只响应窗口自身 `OK` 按钮的点击（不会响应在
  自己背包同一格位的点击）。窗口有效期为 30 秒，与控制台确认使用相同的时限和时钟：超过后点击 `OK` 不会删除
  任何内容，并提示重新执行命令。若窗口打开期间本模块因另一个请求删除了该世界（或开始删除但中途失败），你的 `OK` 同样不会删除任何内容。
  聊天提示也随之改变：原来的"正在删除世界……"、"世界已删除"、
  "删除世界失败"三行（`world.delete.deleting`、`world.delete.success`、`world.delete.failed`）不再发送给玩家；
  改由窗口发送"世界 <名称> 已删除"或"删除世界 <名称> 失败"（`command.delete.success`、
  `command.delete.failed`），点击 `Cancel` 时发送"已取消删除操作"（`command.delete.cancelled`）。如果你为玩家
  自定义过旧的三行文本，请把文本迁移到自定义语言文件的新键上，而不是官方的 `lang/*.yml`：在同一 `lang/` 目录中把
  官方文件复制为以其语言代码加连字符开头的文件（例如 `lang/zh-myserver.yml`），并在 `plugins/UltiTools/config.yml`
  中设置 `language: zh-myserver`。在官方文件中做过的修改不会保留：升级后的首次启动会恢复该文件，并把原文件保留为
  `.bak`，你原来的文本就在其中（UltiKits/UltiTools-Reborn#616）。旧的三行现在由控制台使用（见下一条）
  （UltiKits/UltiWorlds#19）。
- **服务器控制台现在可以删除世界，但需要打字确认。** 此前整个 `/world` 命令仅限玩家使用，控制台完全无法
  执行 `/world delete`。现在可以执行，但第一次请求绝不会删除：控制台执行 `/world delete <名称>` 时会做与
  玩家相同的检查（存在该名称的世界、不是默认世界、不在 `protected_worlds` 中），不删除任何内容，并提示在
  30 秒内再次执行同一条命令。只有这次重复——世界名称的拼写和大小写完全相同、且在 30 秒内——才会删除，并且
  删除前会再次完成全部检查；随后输出"正在删除世界……"，再输出"世界已删除"或"删除世界失败"。超过 30 秒的
  重复不会删除任何内容，而是重新开始 30 秒等待；若在请求之后本模块因另一个请求删除（或开始删除）了该名称的世界，重复不会删除
  任何内容，会给出提示，并视为新的请求；请求或重复被任一检查拒绝时，待确认的请求随之取消；每个
  请求只允许一次删除。待确认的请求只保存在内存中，重启后即失效；这 30 秒按不受系统时间调整影响的时钟计算。命令方块等既非玩家也非控制台的发送者会被
  拒绝，RCON（`mcrcon` 及许多聊天桥接工具使用的远程控制台协议）同样被拒绝：它不能删除世界。其余所有 `/world` 子命令与以前一样仅限玩家；控制台执行 `/world help` 时现在只列出
  `/world delete`（UltiKits/UltiWorlds#19）。
- **所有以服务器控制台身份执行的命令都算作控制台**，并且它们共用同一个身份：都报告名称 `CONSOLE`，因此对每个
  世界名称，它们合起来只有一个待确认请求，其中任何一个都能确认由另一个发起的请求。这包括 UltiPanel 远程命令、
  用 `/world postcmd add` 添加的传送后命令，以及其他模块或插件以控制台身份执行的命令（例如定时命令、菜单
  动作、礼包命令、邮件命令）。
  - **UltiPanel。** 面板用户在 30 秒内发送两次 `world delete <名称>` 即可删除世界，但面板从不显示确认提示、
    结果或拒绝原因：命令主体在面板读取输出之后的下一刻（一个 tick）才执行，所以面板的命令结果永远是
    "Command executed successfully"。提示、结果与拒绝只会以服务器日志行的形式出现（若开启了日志流，也会出现
    在面板的日志视图中）。
  - **传送后命令。** 作为传送后命令保存的 `world delete <名称>`，会在任何玩家通过 `/world tp` 或世界列表传送
    进该世界时执行。30 秒内任意两次这样的传送（同一玩家或不同玩家）都会删除该世界；30 秒内一次这样的传送加上
    来自控制台、面板或任何其他控制台命令的同一条命令，同样会删除。若保存为 `world delete {world}`，它会作用于
    被传送进入的那个世界。确认提示发给控制台，传送的玩家不会得到任何提示。请不要添加这样的命令。添加传送后
    命令需要 `ultiworlds.admin.settings` 权限，因此该权限实际上足以删除任何未受保护的世界
    （UltiKits/UltiWorlds#19）。
- **已知限制。** 删除确认（无论来自玩家还是控制台）不会尝试识别"是否还是同一个世界"：它只受 30 秒时限和本模块
  自身删除操作的约束。若某个世界在这 30 秒内被其他插件或手工删除、又以同名重新创建，为原世界给出的确认会删除
  新创建的世界（UltiKits/UltiWorlds#19）。

### Fixed

- `/world delete` typed in another letter case than the world's name now deletes the world's own folder.
  The world was found ignoring case and unloaded, but the folder was looked up under the typed text; on a
  case-sensitive filesystem (Linux) that folder does not exist, so nothing was removed and the deletion
  was reported as done, leaving the world on disk (UltiKits/UltiWorlds#52).
- 以与世界名不同的大小写输入的 `/world delete` 现在会删除该世界自己的文件夹。此前世界按不区分大小写找到并被卸载，
  文件夹却按输入的文字去找；在区分大小写的文件系统（Linux）上该文件夹并不存在，所以什么也没删除，却报告已删除，
  世界仍留在磁盘上（UltiKits/UltiWorlds#52）。

- A world-settings change whose stored row no longer exists is now logged on the console as failed
  ("Failed to update world settings"); before, it passed as saved without a trace. This can happen when
  two servers share one database and one deletes a world's row while the other still has it in memory,
  or when someone deletes the row by hand: the command answered success and the setting was gone after
  the next restart. The command's own reply is unchanged; only the console line is new. Needs
  UltiTools-API 6.3.0 (UltiKits/UltiWorlds#51, UltiKits/UltiTools-Reborn#558).
- 世界设置的改动若发现其对应的已存记录已不存在，现在会在控制台记录为失败（“更新世界设置失败”）；此前它会不留痕迹地被当作已保存。
  两台服务器共用一个数据库、其中一台删除了某世界的记录而另一台内存里仍有它时，或有人手动删除该记录时都会这样：命令回复成功，
  下次重启后设置却没了。命令本身的回复不变，只多了这条控制台输出。需要 UltiTools-API 6.3.0
  （UltiKits/UltiWorlds#51，UltiKits/UltiTools-Reborn#558）。

- Clicking a world in the world list now teleports you one tick after the click, not inside it. The
  teleport runs the world's post-teleport commands, and since UltiTools 6.3.0 a command of an
  UltiTools module runs the moment it is dispatched; one that opens another module's window (a menu,
  say) would otherwise try to open it inside the click event, which Paper refuses. A player who
  leaves in that tick is not teleported. `/world tp` typed as a command is unchanged
  (UltiKits/UltiWorlds#50).
- 在世界列表中点击一个世界后，现在会在点击的下一个 tick 传送，而不是在点击事件之内。传送会运行该世界的传送后命令，
  而自 UltiTools 6.3.0 起，UltiTools 模块的命令在被分派的那一刻就运行；若某条命令会打开另一个模块的界面（例如菜单），
  就会在点击事件之内尝试打开，而 Paper 不允许这样做。在那一个 tick 内离线的玩家不会被传送。作为命令输入的 `/world tp` 不变
  （UltiKits/UltiWorlds#50）。

- A world-inventory save whose stored row no longer exists is now reported as failed on every storage
  type, in the same console line a failed write always produced ("Failed to save inventory for
  <player>"). Before, only the JSON storage reported it; on SQLite and MySQL the save wrote nothing
  and passed as saved. Needs UltiTools-API 6.3.0 (UltiKits/UltiWorlds#49, UltiKits/UltiTools-Reborn#558).
- 世界背包的保存若发现其对应的已存记录已不存在，现在在所有存储类型上都会报告失败，沿用保存失败时一贯的那一行控制台输出
  （“保存 <玩家> 的背包失败”）。此前只有 JSON 存储会报告；SQLite 和 MySQL 上这次保存什么也没写，却被当作已保存。
  需要 UltiTools-API 6.3.0（UltiKits/UltiWorlds#49，UltiKits/UltiTools-Reborn#558）。

- `/world list` no longer lists a world you marked hidden (`/world set <world> hidden true`). The world
  list window already left it out; the text list showed it anyway, and counted it in the total
  (UltiKits/UltiWorlds#48).
- `/world list` 不再列出被你标记为隐藏的世界（`/world set <世界> hidden true`）。世界列表界面本来就不显示它，
  文字列表却照样列出并计入总数（UltiKits/UltiWorlds#48）。

- Every `/world` command now reads and writes a world's settings under the world's own name, however
  you type it. `/world set MYWORLD pvp false` found `myworld` (the server ignores case) but stored the
  change in a new settings row named `MYWORLD` that nothing reads, so the change applied once and was
  undone the next time a player entered the world, while the command reported success. `set`,
  `protect`, `unprotect`, `block`, `unblock`, `difficulty`, `postcmd`, `tp` (and a click in the world
  list) and `delete` all use the world's own name now; `/world tp` also no longer opens a blocked or
  locked world typed in another case. Rows already stored under another spelling are not merged:
  at start-up each one that matches a loaded world only when case is ignored is listed in one warning,
  naming the row and the world, and is otherwise left alone; the warning says that on MySQL, whose default
  collation ignores case, a lookup may still reach such a row (UltiKits/UltiWorlds#46).
- 现在每条 `/world` 命令都按世界自己的名字读写该世界的设置，不论你怎么输入。此前 `/world set MYWORLD pvp false`
  能找到 `myworld`（服务器不区分大小写），却把改动存进一行名为 `MYWORLD`、无人读取的新设置里，于是改动只生效一次，
  下次有玩家进入该世界时就被撤销，而命令却报告成功。`set`、`protect`、`unprotect`、`block`、`unblock`、`difficulty`、
  `postcmd`、`tp`（以及世界列表中的点击）和 `delete` 现在都使用世界自己的名字；`/world tp` 也不再让以其他大小写输入的
  被封锁或被锁定的世界放行。已按其他写法保存的旧行不会被合并：启动时，只有忽略大小写才与某个已加载世界同名的每一行，
  会各用一条警告列出，点明该行与该世界，其余保持原样；警告中说明，在默认排序规则忽略大小写的 MySQL 上，查找仍可能读到这样的行
  （UltiKits/UltiWorlds#46）。

- A value UltiWorlds cannot use is now named instead of being passed over silently: a
  `default_world` that is no loaded world is reported at start and on `/ul reload`, with the world
  players are sent to instead (the server's first world); a `load_worlds_on_start` entry that cannot
  be loaded is reported at start; and `/world set <world> icon <item>` refuses a name that is no
  item, where it stored it and showed the default icon.
- UltiWorlds 无法使用的值现在会被点名，而不是被悄悄忽略：不是已加载世界的 `default_world` 会在启动和 `/ul reload` 时提示，
  并说明玩家实际会被送往的世界（服务器的第一个世界）；无法加载的 `load_worlds_on_start` 条目会在启动时提示；
  `/world set <世界> icon <物品>` 会拒绝不是物品的名字，此前它会被保存并显示为默认图标。

- `auto_unload.check_interval` now decides how often empty worlds are checked, in seconds; a value
  changed with `/ul reload` applies within a second. The check ran every 60 seconds whatever the key
  said (UltiKits/UltiWorlds#38).
- `auto_unload.check_interval` 现在决定检查无人世界的间隔（秒）；用 `/ul reload` 修改后一秒内生效。此前无论该键为何值，
  都每 60 秒检查一次（UltiKits/UltiWorlds#38）。

- `world_isolation.shared_worlds` now matches world names ignoring case, as the server does. An
  entry typed in another case than the world's name (`MyWorld` for `myworld`) matched nothing, so
  each world became its own group and walking through a portal between them emptied the player's
  inventory. A world listed twice, in any case, is reported at start and stays in the group of its
  first entry (UltiKits/UltiWorlds#34).
- `world_isolation.shared_worlds` 现在与服务器一样按不区分大小写的方式匹配世界名。此前大小写与世界名不同的条目（例如用
  `MyWorld` 指 `myworld`）匹配不到任何世界，于是每个世界各自成组，玩家在它们之间穿过传送门时背包会被清空。
  同一个世界（不论大小写）被列出两次时，会在启动时提示，并保留在第一次出现的组中（UltiKits/UltiWorlds#34）。

- Console and chat lines show a path, a world name or text you typed exactly as it is. A value that
  contained a later placeholder of its line, such as `{KEY}` in the configuration file's path, or
  `{WORLD}` in a description set with `/world set`, was rewritten by it. This covers the removed-key
  warning, the world-environment and invalid-difficulty warnings, the `/world list` lines and the
  `/world set` confirmation (UltiKits/UltiWorlds#43).
- 控制台与聊天中的行现在按原样显示路径、世界名或你输入的文字。此前若值中含有该行随后的占位符（例如配置文件路径中的
  `{KEY}`，或用 `/world set` 设置的描述中的 `{WORLD}`），会被其改写。涉及已移除配置键的警告、世界环境与无效难度的警告、
  `/world list` 的各行以及 `/world set` 的确认消息（UltiKits/UltiWorlds#43）。

- `/world info` shows every setting a world stores: its difficulty and all twelve flags by name
  (PvP, monsters, animals, weather, the four protections, hidden, locked, blocked and auto-unload).
  It showed four, one of them a single "protection" line for all four protections. `/world info
  <world>` shows a named world. `/world set` now also accepts `autoUnload`, `protectBreak`,
  `protectPlace`, `protectInteract` and `protectExplosion`, which had no command to change them
  (UltiKits/UltiWorlds#17).
- `/world info` 现在显示世界保存的全部设置：难度，以及按名称列出的全部十二个开关（PvP、怪物、动物、天气、四项保护、隐藏、
  锁定、禁止进入和自动卸载）。此前只显示四项，其中一项是把四种保护合并成的一行。`/world info <世界>` 可查看指定世界。
  `/world set` 现在还接受 `autoUnload`、`protectBreak`、`protectPlace`、`protectInteract` 与 `protectExplosion`，
  此前没有任何命令能修改它们（UltiKits/UltiWorlds#17）。

- A world description with several lines shows every line in the world list's icon. The whole
  description was one lore line, and the client shows nothing after a line break inside one, so
  only the first line appeared. A Windows line break (`\r\n`) now splits the same way in the list
  and in the lines shown on teleport (UltiKits/UltiWorlds#26).
- 多行的世界描述现在会在世界列表图标中显示每一行。此前整段描述是一行 lore，而客户端不显示一行 lore 中换行之后的内容，
  因此只显示了第一行。Windows 换行（`\r\n`）现在在列表和传送时显示的描述中按同样方式拆分（UltiKits/UltiWorlds#26）。

- A player who hits another player in a world with PvP off is now told "PVP is disabled in this
  world!". The hit was stopped, but the line never arrived: the server stops such a hit before the
  module's damage handler runs, so the line is now sent on the attack attempt (UltiKits/UltiWorlds#23).
- 在关闭 PvP 的世界中攻击其他玩家时，攻击者现在会收到“此世界已禁用 PVP”的提示。此前攻击会被阻止，但提示从未送达：
  服务器会在模块的伤害处理之前拦下这次攻击，因此提示现在改为在攻击发起时发送（UltiKits/UltiWorlds#23）。

- A world with weather disabled (`/world set <world> weather false`) now also keeps thunder from
  starting. Rain was blocked, but thunder is a separate state, so a world could still turn
  thundering (UltiKits/UltiWorlds#24).
- 关闭天气的世界（`/world set <世界> weather false`）现在也会阻止雷暴开始。此前只阻止了下雨，而雷暴是独立的状态，
  因此世界仍可能进入雷暴（UltiKits/UltiWorlds#24）。

- `language: zh` now applies to this module's console lines that were fixed English text: the
  lines about a world's environment when it is loaded, the refusal to delete a protected world, the
  symbolic-link and incomplete-deletion lines of `/world delete`, the empty-world auto-unload line,
  an invalid stored difficulty, and a failed save of world settings or of a player's inventory.
  Their English wording is unchanged. The `/world` command's description (shown by `/help`) now
  follows `language` too; it was fixed Chinese text.
- `/world help` now shows an admin the `/world unprotect` and `/world unblock` lines. Both commands
  worked, and the language files carried their help text, but the help never printed it.
- `language: zh` 现在也对本模块原先写死为英文的控制台日志生效：加载世界时关于其环境的日志、拒绝删除受保护世界的日志、
  `/world delete` 遇到符号链接与未能完整删除时的日志、空世界自动卸载日志、已存储的无效难度，以及保存世界设置或玩家背包
  失败的日志。它们的英文措辞不变。`/world` 命令的描述（由 `/help` 显示）现在也跟随 `language`；原先是写死的中文。
- `/world help` 现在会向管理员显示 `/world unprotect` 与 `/world unblock` 两行。这两个命令本来就能用，语言文件里
  也有它们的帮助文本，但帮助从未打印过。

- `/world load` now brings a NETHER or THE_END world back as itself instead of as an overworld.
  Reloading such a world previously reported success while rebinding it to the `NORMAL`
  environment, so overworld terrain generated over the stored world. The environment is read from
  the dimension folder the server writes inside the world folder, and it is read again every time
  the command runs -- so it survives a restart, and it describes the folder as it is now rather
  than as it was earlier in the session. That is what makes the `move X out ->` column below
  something you can act on without restarting the server (UltiKits/UltiWorlds#22).
- `/world load` 现在会把下界或末地世界按其原本维度载入，而不再变为主世界。此前重新载入这类世界会提示成功，
  却把世界改绑到 `NORMAL` 维度，导致主世界地形覆盖原有世界。维度读取自服务器写入世界文件夹中的维度目录，
  且每次执行该命令时都会重新读取——因此重启后同样有效，并且反映文件夹当前的状态，而不是本次会话中较早时
  的状态。这也是下方 `move X out ->` 一列无需重启服务器即可生效的原因（UltiKits/UltiWorlds#22）。
- `/world load` now works out a world's environment when it has to, refuses to work it out when
  the world folder is ambiguous, and says in the console which of those happened and what it saw.
  The console line reports what was found; it never tells you what to do, because the module has
  just said it cannot read the folder. The shapes below are listed in the order the module tries
  them, and the first one that matches decides — so a folder answering to more than one description,
  both dimension directories and no `region`, say, is the earlier shape:
  - **shape 1 — a dimension entry that is a link leading nowhere** — an unmounted volume or a moved
    directory. Nothing is applied. Restore what the link points at, or replace the link with a real
    directory.
  - **shape 2 — no dimension entry at all** — the ordinary overworld case. Nothing is worked out,
    nothing is logged, and nothing changes from previous versions.
  - **shape 3 — a dimension entry that is a symbolic link** — the module does not follow links when
    reading a world folder, so it did not read what the link points at. Nothing is applied. Because
    the order above reaches this before the two shapes under it, moving other directories out cannot
    help while the link is still a link; replace it with a real directory and the folder is read as
    whichever shape it then matches.
  - **shape 4 — both `DIM-1` and `DIM1` at the top level** — a single-player save or a downloaded
    map, where all three dimensions share one folder. Nothing is applied. Moving the dimension
    directory you do not want out leaves you in shape 5 if the folder also has a top-level `region`
    directory, as a single-player save does, and in shape 6 if it does not.
  - **shape 5 — a dimension directory AND a top-level `region` directory** — two worlds' terrain in
    one folder, which is what `UltiKits/UltiWorlds#22` produced. Nothing is applied. Whichever
    directory you move out, **move** it and do not delete it: players may have built in either one.
  - **shape 6 — a dimension directory and no top-level `region`** — an ordinary nether (or end)
    world, and the one shape where an environment is applied.

  The outcomes are the table below rather than prose, because a test reads this table and fails if
  it and the code ever disagree. `folder holds` lists the world folder's top-level entries,
  `(link)` marking a symbolic link to a directory and `(dangling)` one whose target is missing;
  `applied` is the environment the server is given, `none` meaning the module supplies none and the
  server uses its own default; `move X out ->` is what you get after moving that entry to a path
  outside the world folder.

  | folder holds | applied | move X out -> |
  |---|---|---|
  | `DIM-1` | NETHER | `DIM-1` -> none |
  | `DIM1` | THE_END | `DIM1` -> none |
  | `DIM-1`, `region` | none | `region` -> NETHER; `DIM-1` -> none |
  | `DIM1`, `region` | none | `region` -> THE_END; `DIM1` -> none |
  | `DIM-1`, `DIM1`, `region` | none | `DIM1` -> none; `DIM-1` -> none |
  | `DIM-1`, `DIM1` | none | `DIM1` -> NETHER; `DIM-1` -> THE_END |
  | `DIM-1(link)`, `region` | none | `region` -> none |
  | `DIM-1(dangling)` | none | `DIM-1` -> none |
  | `region` | none | |

  (UltiKits/UltiWorlds#22)
- `/world load` 在需要时推断世界维度；当世界文件夹自相矛盾时则拒绝推断，并在控制台说明发生了哪一种
  情况、以及它看到了什么。该控制台行只陈述观察到的事实，不会告诉你该怎么做——因为模块刚刚声明自己无法
  读懂这个文件夹。下列形态按模块实际判断的顺序排列，第一个匹配的形态即为结果——因此同时符合多条描述的
  文件夹（例如两个维度目录都在、又没有 `region`）按靠前的那一条处理：
  - **形态 1 —— 维度目录是指向不存在位置的链接** —— 例如卷未挂载或目录被移走。不应用任何维度。请恢复
    链接指向的内容，或用真实目录替换该链接。
  - **形态 2 —— 完全没有维度目录条目** —— 普通主世界。不推断、不输出日志，与旧版本完全一致。
  - **形态 3 —— 维度目录是符号链接** —— 本模块读取世界文件夹时不跟随链接，因此没有读取链接指向的内容。
    不应用任何维度。由于上述顺序会在下面两种形态之前到达这一项，只要链接还是链接，移动其他目录都无济
    于事；用真实目录替换该链接后，该文件夹会按它当时匹配的形态重新判断。
  - **形态 4 —— 顶层同时有 `DIM-1` 与 `DIM1`** —— 单人存档或下载的地图，三个维度共用一个文件夹。不应用
    任何维度。移出不需要的那个维度目录后：若该文件夹还有顶层 `region` 目录（单人存档就是如此），会落到
    形态 5；若没有，则落到形态 6。
  - **形态 5 —— 同时有维度目录与顶层 `region` 目录** —— 一个文件夹里存着两个世界的地形，正是
    `UltiKits/UltiWorlds#22` 造成的。不应用任何维度。无论移出哪一个目录，都请**移动**而不要删除：两个
    目录里都可能有玩家建造的地形。
  - **形态 6 —— 有维度目录且没有顶层 `region`** —— 普通的下界（或末地）世界，也是唯一会应用维度的形态。

  各形态的结果见上方英文表格，而不是散在正文里：有一项测试会读取那张表，一旦表与代码不一致即失败
  （UltiKits/UltiWorlds#22）。
- `/world unload` and `/world delete` now compare the name you typed against `default_world`
  without regard to letter case. With `default_world: lobby`, `/world unload LOBBY` used to miss
  the "Cannot unload the default world!" refusal and unload the lobby anyway, after which players
  ejected from other worlds landed somewhere else; and `/world delete WORLD` could be told it was
  "listed in protected_worlds" when that list was empty (UltiKits/UltiWorlds#20).
- `/world unload` 与 `/world delete` 现在在与 `default_world` 比对时忽略字母大小写。此前在
  `default_world: lobby` 下，`/world unload LOBBY` 不会触发"无法卸载默认世界！"的拒绝，仍会把
  lobby 卸载，随后从其他世界被移出的玩家会落到别处；`/world delete WORLD` 也可能被告知它"已列入
  protected_worlds"，而那份列表其实是空的（UltiKits/UltiWorlds#20）。
- `/world delete` now refuses a world listed in `protected_worlds`, as that key's own comment
  ("Worlds that cannot be auto-unloaded or deleted") always promised. The sender is told
  "World `<name>` is listed in protected_worlds and cannot be deleted!" and nothing is removed —
  neither the world folder nor its stored settings. With the shipped defaults this means
  `/world delete world_nether` and `/world delete world_the_end` no longer permanently delete those
  worlds. The refusal lives in the service rather than in the command, so any future caller
  inherits it (UltiKits/UltiWorlds#20).
- `/world delete` 现在会拒绝删除列入 `protected_worlds` 的世界，兑现该配置项自身注释
  （"Worlds that cannot be auto-unloaded or deleted"）一直以来的承诺。执行者会收到
  "World `<名称>` is listed in protected_worlds and cannot be deleted!"，并且不会移除任何内容——
  世界文件夹和已保存的设置都会保留。按出厂默认配置，这意味着 `/world delete world_nether` 与
  `/world delete world_the_end` 不再永久删除这两个世界。该拒绝逻辑位于服务层而非命令层，因此今后
  新增的调用方也会自动受到同一保护（UltiKits/UltiWorlds#20）。
- `/world delete` no longer deletes through a symbolic link. A world folder that is itself a link
  had that link followed: everything under the directory it pointed at was deleted, and only the
  link entry was removed, so the command reported success for data that lived somewhere else
  entirely. The module already held that a linked directory is not part of a world folder and that
  only the link entry is removed — that rule covered a link found inside a world folder and not one
  standing in the world folder's own position, and it now covers both. A link whose own name is not
  in `protected_worlds` is therefore not a route to a protected world's data. The console says when
  the thing named is a link, and says it as a statement of what the command can reach rather than of
  what it has already done, so the line stays true even when the link cannot be removed; a link that
  could not be removed is reported as a link left in place, not as files left in a folder
  (UltiKits/UltiWorlds#20).
- `/world delete` 不再沿符号链接删除。当世界文件夹本身就是一个链接时，该链接此前会被跟随：链接指向的
  目录下的全部内容都会被删除，而被移除的只有链接本身，于是命令为一份存放在别处的数据报告了成功。本模块
  一向认为链接目录不属于世界文件夹、只移除链接条目——此前该规则只覆盖世界文件夹内部的链接，不覆盖处于
  世界文件夹自身位置的链接，现在两者都覆盖。因此，名称本身不在 `protected_worlds` 中的链接，也不再是
  通往受保护世界数据的通路。当目标是链接时，控制台会说明这一点，并且以「本命令能触及什么」而非
  「已经做了什么」的方式陈述，因此即使链接删除失败，该行依然成立；删除失败时报告的是「链接仍留在世界
  容器中」，而不是「文件夹中还有文件残留」（UltiKits/UltiWorlds#20）。
- `protected_worlds` is now matched without regard to letter case, both when refusing a deletion and
  when the empty-world auto-unload task skips a world. A world listed as `MyWorld` while the server
  calls it `myworld` is now covered by both; previously only an exact spelling matched
  (UltiKits/UltiWorlds#20).
- `protected_worlds` 现在在两处读取时都忽略字母大小写：拒绝删除时，以及空世界自动卸载任务跳过世界时。
  若配置写作 `MyWorld` 而服务器中的世界名为 `myworld`，现在两处都会生效；此前只有完全一致的拼写才匹配
  （UltiKits/UltiWorlds#20）。
- After `/upm uninstall UltiWorlds`, this module's commands are now really removed and its
  listeners stop firing. Previously this module's unload method replaced the framework's and only
  logged a line, so both stayed active until the server restarted (UltiKits/UltiWorlds#27).
- 执行 `/upm uninstall UltiWorlds` 后，本模块的命令现在会被真正移除，其监听器也不再触发。此前本模块的卸载
  方法替换了框架的卸载方法且只输出一行日志，因此两者都会一直保持生效，直到服务器重启
  （UltiKits/UltiWorlds#27）。

### Removed

- The `/w` alias of `/world`. `w` is a vanilla command label (`/msg`'s alias, with `/tell`), so
  `/w <player> <message>` could reach this module instead of a private message. Use `/world` or
  `/worlds` (UltiKits/UltiWorlds#45).
- 移除 `/world` 的别名 `/w`。`w` 是原版命令标签（`/msg` 的别名，与 `/tell` 相同），因此 `/w <玩家> <消息>`
  可能被本模块接收，而不是发送私聊。请使用 `/world` 或 `/worlds`（UltiKits/UltiWorlds#45）。

- Six `config/worlds.yml` keys that no code ever read: `gui_title` and `messages.world_teleport`,
  `messages.world_not_found`, `messages.no_permission`, `messages.world_created`,
  `messages.world_deleted`. Changing any of them never changed anything: the world list's title and
  every world message come from the language files (`lang/en.yml`, `lang/zh.yml`); to customise them, copy
  the official language file to one whose name starts with its language code and a hyphen (for example
  `lang/en-myserver.yml`), edit the entries there and set `language: en-myserver` in
  `plugins/UltiTools/config.yml` (an edit made in the official file itself is restored at the next start or module reload,
  UltiKits/UltiTools-Reborn#616). They are gone from the shipped `config/worlds.yml` too, so a new server's file no
  longer carries them. A leftover key in an existing file is reported at startup and on reload (see
  Added) (part of UltiKits/UltiWorlds#38).
- Two deprecated `config/worlds.yml` keys that no code read: `unload_empty_worlds` (the key that
  decides is `auto_unload.enabled`) and `unload_delay` (`auto_unload.unload_after`). They are gone
  from the shipped file, and a leftover in an existing file is reported like the six above
  (UltiKits/UltiWorlds#38).
- 移除 `config/worlds.yml` 中两个从未被任何代码读取的已弃用键：`unload_empty_worlds`（起作用的是 `auto_unload.enabled`）
  与 `unload_delay`（起作用的是 `auto_unload.unload_after`）。它们已从出厂文件中删除，已有文件中残留的这两个键会像上面六个键一样被提示
  （UltiKits/UltiWorlds#38）。
- 38 language-file entries that no code displayed, from `lang/en.yml` and `lang/zh.yml`: the older
  `command.help.*` help lines (other than `unprotect`, `unblock`, `difficulty` and `postcmd`, which
  `/world help` shows), `command.usage`, `command.set_options`, the unused `common` words for on and
  off, the `error.*` and `success.*` lines whose messages come from other entries, the
  `gui.delete.confirm`, `gui.delete.cancel` and `gui.delete.warning` lines (the confirmation window's
  buttons come from UltiTools itself), and the four `inventory.*` lines.
- 移除 `config/worlds.yml` 中从未被任何代码读取的六个键：`gui_title` 以及 `messages.world_teleport`、
  `messages.world_not_found`、`messages.no_permission`、`messages.world_created`、`messages.world_deleted`。
  修改其中任何一个都从未改变任何东西：世界列表的标题和所有世界相关消息都来自语言文件（`lang/en.yml`、`lang/zh.yml`），
  要自定义，请把官方语言文件复制为以其语言代码加连字符开头的文件（例如 `lang/zh-myserver.yml`），在副本中修改，并在
  `plugins/UltiTools/config.yml` 中设置 `language: zh-myserver`（直接修改官方文件的改动会在下次启动或模块重载时被恢复，
  UltiKits/UltiTools-Reborn#616）。随插件分发的 `config/worlds.yml` 中也已删除它们，新服务器的文件不再包含这些键。已有文件中残留的键会在启动和
  重载时报告（见 Added）（UltiKits/UltiWorlds#38 的一部分）。
- 从 `lang/en.yml` 与 `lang/zh.yml` 中移除 38 条从未被任何代码显示的条目：旧的 `command.help.*` 帮助行（`unprotect`、
  `unblock`、`difficulty`、`postcmd` 除外，`/world help` 会显示它们）、`command.usage`、`command.set_options`、
  `common` 中未使用的「开」「关」、其消息改由其他条目提供的 `error.*` 与 `success.*` 行、`gui.delete.confirm`、
  `gui.delete.cancel`、`gui.delete.warning`（确认窗口的按钮由 UltiTools 自身提供），以及四条 `inventory.*`。

- The unused `WorldListGUI` class. It was the predecessor of the world list that bare `/world`
  opens (`WorldListPage`), and nothing in the module ever constructed it, so removing it changes
  nothing a player or operator can see. The `gui_title` setting it read still has no effect, as
  before; that key and eight others in `config/worlds.yml` that no code reads are tracked in
  UltiKits/UltiWorlds#38 (UltiKits/UltiWorlds#18).
- 移除未使用的 `WorldListGUI` 类。它是裸命令 `/world` 所打开的世界列表（`WorldListPage`）的前身，
  模块中从未有任何代码构造它，因此移除它不会改变玩家或运维可见的任何行为。它所读取的 `gui_title`
  设置项与以前一样仍不起作用；该键以及 `config/worlds.yml` 中另外八个没有任何代码读取的键记录在
  UltiKits/UltiWorlds#38（UltiKits/UltiWorlds#18）。
- The module's own console lines "UltiWorlds has been disabled!" on unload (and its
  `worlds_disabled` language key in `lang/en.yml` and `lang/zh.yml`) and
  "UltiWorlds configuration reloaded!" on `/ul reload UltiWorlds` (printed in English under either
  `language` setting). UltiTools 6.3.0 logs one reload line per module
  (`Module 'UltiWorlds' reloaded.`); reloading still re-reads `config/worlds.yml` and reports
  `@ConditionalOnConfig` drift, because the framework's reload method does both
  (UltiKits/UltiWorlds#27).
- 移除本模块在卸载时输出的"UltiWorlds 已禁用！"控制台行（及 `lang/en.yml`、`lang/zh.yml` 中的
  `worlds_disabled` 语言键），以及在 `/ul reload UltiWorlds` 时输出的"UltiWorlds configuration
  reloaded!"控制台行（无论 `language` 设置如何均以英文输出）。UltiTools 6.3.0 会为每个模块输出一行重载日志；
  重载仍会重新读取 `config/worlds.yml` 并报告 `@ConditionalOnConfig` 漂移，因为框架的重载方法会完成这两步
  （UltiKits/UltiWorlds#27）。
