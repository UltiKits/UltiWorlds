# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Changed

- `/world delete <name>` now asks before it deletes. After the checks it always made (permission,
  a world by that name exists, not the default world, not in `protected_worlds`), it opens a
  confirmation window titled "Confirm Delete: <name>" instead of deleting on the spot; the world is
  deleted only when you click the window's green `OK` button. The red `Cancel` button, or closing
  the window any other way, deletes nothing. Clicking `OK` checks the permission, the default world
  and `protected_worlds` again, because they can change while the window is open, and a window
  deletes at most once, never after it has been closed, and only for a click on its own `OK` button
  (not for a click at the same slot in your own inventory). The chat lines change with it: the old "Deleting world <name>, please
  wait..." / "World <name> has been deleted!" / "Failed to delete the world!" lines
  (`world.delete.deleting`, `world.delete.success`, `world.delete.failed`) are no longer sent to a
  player; the window sends "World <name> has been deleted" or "Failed to delete world <name>"
  (`command.delete.success`, `command.delete.failed`), and "Delete operation cancelled"
  (`command.delete.cancelled`) for `Cancel`. If you customised those three old lines in the language
  files for players, carry your text over to the new keys; the old lines are now the console's (next
  entry) (UltiKits/UltiWorlds#19).
- **The server console can now delete a world, behind a typed confirmation.** Until now the whole
  `/world` command was player-only, so the console could not run `/world delete` at all. It now
  can, and it never deletes on the first request: `/world delete <name>` from the console makes the
  same checks as for a player (a world by that name exists, it is not the default world, it is not
  in `protected_worlds`), deletes nothing, and asks you to run the same command again within 30
  seconds. Only that repeat -- same world name, spelled and cased exactly the same, within 30
  seconds -- deletes, and it makes every check again first; it then prints "Deleting world <name>,
  please wait..." followed by "World <name> has been deleted!" or "Failed to delete the world!". A
  repeat after 30 seconds deletes nothing and starts a new 30-second wait; a check that refuses the
  request or the repeat cancels the pending request; each request allows one deletion. A pending
  request lives in memory only and is lost on restart. Command blocks and other non-player,
  non-console senders are refused. Every other `/world` subcommand stays player-only, as before;
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
- `/world delete <名称>` 现在会在删除前先询问。它在原有检查（权限、该名称的世界存在、不是默认世界、
  不在 `protected_worlds` 中）之后，不再立即删除，而是打开标题为"Confirm Delete: <名称>"的确认窗口；
  只有点击窗口中绿色的 `OK` 按钮才会删除世界。点击红色的 `Cancel` 按钮，或以其他任何方式关闭窗口，都
  不会删除任何内容。点击 `OK` 时会再次检查权限、默认世界与 `protected_worlds`，因为窗口打开期间它们可能
  发生变化；并且一个窗口最多只执行一次删除，关闭后不再执行，且只响应窗口自身 `OK` 按钮的点击（不会响应在
  自己背包同一格位的点击）。聊天提示也随之改变：原来的"正在删除世界……"、"世界已删除"、
  "删除世界失败"三行（`world.delete.deleting`、`world.delete.success`、`world.delete.failed`）不再发送给玩家；
  改由窗口发送"世界 <名称> 已删除"或"删除世界 <名称> 失败"（`command.delete.success`、
  `command.delete.failed`），点击 `Cancel` 时发送"已取消删除操作"（`command.delete.cancelled`）。如果你在
  语言文件中为玩家自定义过旧的三行文本，请把文本迁移到新的键上；旧的三行现在由控制台使用（见下一条）
  （UltiKits/UltiWorlds#19）。
- **服务器控制台现在可以删除世界，但需要打字确认。** 此前整个 `/world` 命令仅限玩家使用，控制台完全无法
  执行 `/world delete`。现在可以执行，但第一次请求绝不会删除：控制台执行 `/world delete <名称>` 时会做与
  玩家相同的检查（存在该名称的世界、不是默认世界、不在 `protected_worlds` 中），不删除任何内容，并提示在
  30 秒内再次执行同一条命令。只有这次重复——世界名称的拼写和大小写完全相同、且在 30 秒内——才会删除，并且
  删除前会再次完成全部检查；随后输出"正在删除世界……"，再输出"世界已删除"或"删除世界失败"。超过 30 秒的
  重复不会删除任何内容，而是重新开始 30 秒等待；请求或重复被任一检查拒绝时，待确认的请求随之取消；每个
  请求只允许一次删除。待确认的请求只保存在内存中，重启后即失效。命令方块等既非玩家也非控制台的发送者会被
  拒绝。其余所有 `/world` 子命令与以前一样仅限玩家；控制台执行 `/world help` 时现在只列出
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

### Fixed

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
