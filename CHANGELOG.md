# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- Unloading this module (`/upm uninstall UltiWorlds`, or server shutdown) now runs the framework's
  command unregistration and then its listener unregistration. Previously this module's unload
  method replaced the framework's and only logged a line, so its commands were never unregistered
  on any unload path, and its listeners were not unregistered on `/upm uninstall`
  (UltiKits/UltiWorlds#27).
- 卸载本模块（`/upm uninstall UltiWorlds` 或关闭服务器）现在会由框架先注销命令，再注销监听器。此前本模块的
  卸载方法替换了框架的卸载方法且只输出一行日志，因此任何卸载途径都不会注销其命令，`/upm uninstall`
  也不会注销其监听器（UltiKits/UltiWorlds#27）。

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
