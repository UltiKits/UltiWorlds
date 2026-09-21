# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- `/world load` now works out a world's environment when it has to, refuses to work it out when
  the world folder is ambiguous, and says in the console which of those happened and what it saw.
  The console line reports what was found; it never tells you what to do, because the module has
  just said it cannot read the folder. What each shape means:
  - **a top-level `DIM-1` (or `DIM1`) directory and no top-level `region`** — an ordinary nether
    (or end) world. The environment is applied and the line says so. If that is wrong, stop the
    server and move that directory to a path outside the world folder; the world then loads with
    the server's default.
  - **a dimension directory AND a top-level `region` directory** — two worlds' terrain in one
    folder, which is what the defect below produced. No environment is applied. To load it as the
    other world, stop the server and **move** the directory you are not keeping to a path outside
    the world folder — do not delete it: players may have built in either one.
  - **both `DIM-1` and `DIM1` at the top level** — a single-player save layout. No environment is
    applied. Move the dimension directory you do not want out of the world folder to reach the
    first case.
  - **a dimension entry that is a symbolic link** — the module does not follow links when reading a
    world folder, so it did not read what the link points at, and no environment is applied.
    Moving directories out will not change that; either replace the link with a real directory, or
    leave it and accept the server's default environment.
  - **no dimension directory** — the ordinary overworld case. Nothing is worked out, nothing is
    logged, and nothing changes from previous versions (UltiKits/UltiWorlds#22).
- `/world load` 现在会在需要时推断世界维度；当世界文件夹自相矛盾时则拒绝推断，并在控制台说明发生了
  哪一种情况、以及它看到了什么。该控制台行只陈述观察到的事实，不会告诉你该怎么做——因为模块刚刚声明
  自己无法读懂这个文件夹。各种形态的含义：
  - **有顶层 `DIM-1`（或 `DIM1`）目录且没有顶层 `region`** —— 普通的下界（或末地）世界，会应用该
    维度并在日志中说明。若这不正确，请停服并把该目录移动到世界文件夹之外，该世界随后会按服务器默认
    维度加载。
  - **同时有维度目录与顶层 `region` 目录** —— 一个文件夹里存着两个世界的地形，正是下面那个缺陷造成
    的。不应用任何维度。若要按另一个世界加载，请停服并把不保留的那个目录**移动**到世界文件夹之外
    ——不要删除：两个目录里都可能有玩家建造的地形。
  - **顶层同时有 `DIM-1` 与 `DIM1`** —— 单人存档式布局。不应用任何维度。把不需要的那个维度目录移出
    世界文件夹即可回到第一种情况。
  - **维度目录是符号链接** —— 本模块读取世界文件夹时不跟随链接，因此没有读取链接指向的内容，也不应用
    任何维度。移动其他目录不会改变这一点；请改为用真实目录替换该链接，或保持现状并接受服务器默认维度。
  - **没有维度目录** —— 普通主世界，不推断、不输出日志，与旧版本完全一致（UltiKits/UltiWorlds#22）。
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
