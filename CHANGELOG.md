# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- `/world load` now says in the console what it is doing when it has to work out a world's
  environment from the world folder, and refuses to work it out at all when the folder is
  ambiguous. A folder holding both a top-level `DIM-1` (or `DIM1`) and a top-level `region`
  directory has been served as two different worlds at different times — the footprint of the
  defect below — and the folder cannot say which one you want back, so the world is loaded with the
  server's default environment, exactly as before this version, and a WARNING names the world and
  what to do about it. The same applies to a single-player save layout carrying both dimension
  directories, and to a dimension entry that is a symbolic link. An ordinary world with no
  dimension directory is unaffected and logs nothing (UltiKits/UltiWorlds#22).
- `/world load` 在需要从世界文件夹推断维度时，现在会在控制台说明它做了什么；当文件夹证据自相矛盾时，
  则不再推断。同时含有顶层 `DIM-1`（或 `DIM1`）与顶层 `region` 目录的文件夹，说明它在不同时期被当作
  两个不同的世界加载过——这正是下面那个缺陷留下的痕迹——文件夹本身无法说明你想要哪一个，因此该世界会
  按服务器默认维度加载（与升级前完全一致），并输出一条指明世界名与处理方式的 WARNING。单人存档式
  （同时含两个维度目录）以及维度目录为符号链接的情况同样如此。没有维度目录的普通世界不受影响，也不会
  输出任何日志（UltiKits/UltiWorlds#22）。
- `/world unload` and `/world delete` now compare the name you typed against `default_world`
  without regard to letter case. With `default_world: lobby`, `/world unload LOBBY` used to miss
  the "Cannot unload the default world!" refusal and unload the lobby anyway, after which players
  ejected from other worlds landed somewhere else; and `/world delete WORLD` could be told it was
  "listed in protected_worlds" when that list was empty (UltiKits/UltiWorlds#20).
- `/world unload` 与 `/world delete` 现在在与 `default_world` 比对时忽略字母大小写。此前在
  `default_world: lobby` 下，`/world unload LOBBY` 不会触发"无法卸载默认世界！"的拒绝，仍会把
  lobby 卸载，随后从其他世界被移出的玩家会落到别处；`/world delete WORLD` 也可能被告知它"已列入
  protected_worlds"，而那份列表其实是空的（UltiKits/UltiWorlds#20）。

- `/world load` now brings a NETHER or THE_END world back as itself instead of as an overworld.
  Reloading such a world previously reported success while rebinding it to the `NORMAL`
  environment, so overworld terrain generated over the stored world. The environment comes from
  what the module recorded when it last created, loaded or unloaded that world, and failing that
  from the dimension folder the server writes inside the world folder, so it also survives a
  restart (UltiKits/UltiWorlds#22).
- `/world load` 现在会把下界或末地世界按其原本维度载入，而不再变为主世界。此前重新载入这类世界会提示成功，
  却把世界改绑到 `NORMAL` 维度，导致主世界地形覆盖原有世界。维度取自本模块上次创建、载入或卸载该世界时
  记录的值；若无记录，则读取服务器写入世界文件夹中的维度目录，因此重启后同样有效
  （UltiKits/UltiWorlds#22）。
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
