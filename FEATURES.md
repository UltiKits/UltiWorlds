# UltiWorlds — Feature Inventory

This document catalogues every operator- or player-visible function, command, content item and
configuration key in this repository, as read directly from source. It is an internal reference
for UAT execution and issue reconciliation — the public description of these features lives on
<https://doc.ultikits.com/>. Update this file in the same pull request as any feature change.

## Conventions

- **ID grammar:** `<repo-slug>.<area>.<action>`, dot-separated, every segment lowercase ASCII
  drawn from `[a-z0-9-]`. `<repo-slug>` is the repository name lowercased with no separators —
  `ultiworlds` here, `ultitools`, `ultichat`, `ultilogin`, and `ultitools-example` for
  `UltiTools-External-Example`. `<area>` is the feature section's slug. `<action>` is the verb.
  A `config` row is the one shape that exceeds three segments and is exempt from the
  lowercase-ASCII rule for its key-path suffix:
  `<repo-slug>.config.<file-stem>.<yml key path>`, the key path keeping its own dots and its own
  casing verbatim from the yml file — a config ID is a citation of the key, not a re-derived slug,
  so lowercasing it would make it un-greppable against its own source line. An ID changes only
  when the feature's identity changes, never on rewording. IDs are unique within a repository.
- **Kind**, exactly these eight values: `command`, `config`, `event`, `gui`, `scheduled`,
  `placeholder`, `persistence`, `gate`. Each maps one-to-one onto a reconciliation-table line.
  This module has no `placeholder` rows (registers no PlaceholderAPI expansion, consumes none) —
  the Kind stays in the vocabulary for cross-repository consistency even though it does not
  appear below. This is the fan-out's clearest case of the executor-versus-mapping distinction:
  `@CmdExecutor` = 1 (one class, `WorldCommand`) while `@CmdMapping` = 21 (21 sub-command formats
  on that one class) — the row unit is the mapping, not the executor, so this document carries 21
  `command`-Kind rows against 1 `@CmdExecutor` site, and the two counts are not meant to match.
- **Tier**, exactly three: `player`, `admin`, `internal`. Judged from what the feature is for, not
  from whether it carries a permission string — every one of this module's 21 `@CmdMapping`
  methods checks a method-specific `player.hasPermission(...)` node by hand inside the method
  body (not via the framework's own `@CmdMapping(permission=...)` attribute, which none of them
  use), so the permission string is a hand-written guard here, not a framework-enforced one; a
  malformed guard would be silently absent, not merely misclassified.
- **Manual**, exactly three: `detailed`, `brief`, `none`.
- **Target**, exactly four: `player`, `console`, `both`, or `n/a` — the first three read straight
  off `@CmdTarget` for a `command` row; it is a property, not a tier. `n/a` is for every other
  Kind. `WorldCommand` carries class-level `@CmdTarget(PLAYER)`, so every one of its 21 rows below
  records Target `player`.
- **Permission:** the literal node string, `none`, or `n/a`, each optionally suffixed with the
  literal text `(requireOp=true)` (preceded by one space) when the row's class-level
  `@CmdExecutor` carries that flag — `WorldCommand`'s class-level `@CmdExecutor` does not set
  `requireOp = true`, so no row below carries the suffix. Every admin-only row's Permission cell
  names the specific `ultiworlds.admin.*` (or `ultiworlds.use`) node the method checks by hand —
  see the Tier note above for why this is a hand-written check, not a framework attribute.
- **Source:** `ClassName#member` — the class and member that actually reads or applies the
  feature — for every Kind, `config` included: all 27 `config` rows below cite the reading
  member. This module's one configuration file (`worlds.yml`) is a real
  `@ConfigEntity`/`@ConfigEntry`-bound class, so a config row's Source cites whichever class and
  method actually calls the generated getter — not the config class's own field declaration.
- **Row order:** by section, then by ID ascending within the section.
- **No manual prose:** no troubleshooting column, no explanatory paragraphs, no draft page text.
  A hazard noticed while reading becomes a negative checklist row, not a note here. Where a
  feature's actual runtime behaviour genuinely diverges from what its config comment or lang key
  describes (a declared-but-dead key, a class never wired into any command), that fact is itself
  part of "what the feature does" and is stated here as a plain, sourced observation, with the
  filed issue number, never as advice on how to fix it.

### Reconciliation command family

The canonical form for counting an annotation site across this repository's real sources:

```bash
find <repo-root> -path '*/src/main/java/*' -name '*.java' -not -path '*/target/*' \
  -not -path '*/.worktrees/*' -print0 | xargs -0 grep -nE '^[[:space:]]*@AnnotationName\b' | wc -l
```

This form defeats three measured traps, each of which produces a wrong-but-plausible number
rather than an error:

1. **Multi-root repositories** — UltiBot's sources live under `ultibot-api/`, `ultibot-core/`
   and `ultibot-v1_21_R1/`, so a naive `<repo>/src/main/java` glob returns 0 for it, silently.
   This module is a single-root Maven project, so this trap does not apply to it, but the robust
   `find` form is used regardless — the same command must work unmodified across all 18
   repositories.
2. **Git worktrees and build output** — UltiEconomy carries
   `.worktrees/economy-v2/src/main/java`, so a `find` without the `-not -path` exclusions above
   reports 48 `@CmdMapping` sites where the real number is 24. This module carries no worktree
   directory.
3. **Javadoc and string literals** — requiring the annotation to start its own line (the
   `^[[:space:]]*@` anchor) is what defeats a javadoc mention or a warning-message string literal
   that merely contains the annotation's name as text. `WorldCommand`'s own javadoc discusses
   `WorldCreateConversation#WORLD_NAME_PATTERN` and `WorldService#isFilesystemSafeWorldName`
   extensively but never writes an annotation name at the start of a line, so this module's naive
   and line-start counts are identical for every annotation kind measured below — but the
   anchored form is still the one used, so the same command is trustworthy unmodified against
   every repository in the fan-out.

**Positive control:** the line-start form returns `@CmdExecutor` = 1, `@CmdMapping` = 21,
`@EventListener` = 1 (class), `@EventHandler` = 9 (handler methods), `@Scheduled` = 1,
`@ConditionalOnConfig` = 1, `@ConfigEntity` = 1 (class), `@ConfigEntry` = 27, `@Table` = 2
(`WorldSettings`, `WorldInventory`) — confirmed by reading all 12 source files directly, not by
trusting the count alone. `WorldCommand`'s own 21 `@CmdMapping` sites, all behind the single
`@CmdExecutor(alias = {"world", "worlds", "w"})` site, are this module's standing positive
control and the reconciliation table's clearest illustration of the executor-versus-mapping
distinction described above — every one of the 21 is confirmed present as its own row below,
none merged or dropped for sharing a class.

## World

`WorldCommand` — the sole `@CmdExecutor(alias = {"world", "worlds", "w"}, permission =
"ultiworlds.use")` (its `description` attribute is Simplified Chinese, not reproduced here per
D-02), class-level `@CmdTarget(PLAYER)`. All 21 `@CmdMapping` sites are listed below. The literal
`help` mapping (`format = "help"`, line 544) is never actually reached through `matchMethod`: the
framework's own `onCommand` short-circuits any literal `"help"` argument to `#handleHelp` before
method-matching runs at all (the same mechanism the framework's own `FEATURES.md` documents for
`/upm help`), so the row below cites `#handleHelp` (which itself delegates to `#help`) as its
Source, not `#help` directly — a live annotation site is not the same fact as a reachable dispatch
target. This is a mapping-level fact and does not change the 21-mapping count itself.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiworlds.world.block | Set `blocked: true` on a world and immediately teleport every currently-present player out to the default world (or, if unavailable, `Bukkit.getWorlds().get(0)`) | command | `/world block <world>` | ultiworlds.admin.block | player | admin | brief | WorldCommand#blockWorld |
| ultiworlds.world.create | Create and load a new NORMAL-environment, NORMAL-type world, refusing a name that already exists | command | `/world create <name>` | ultiworlds.admin.create | player | admin | brief | WorldCommand#createWorld |
| ultiworlds.world.create-with-type | Create and load a new world with an explicit environment (`NORMAL`/`NETHER`/`THE_END`), NORMAL terrain type | command | `/world create <name> <type>` | ultiworlds.admin.create | player | admin | brief | WorldCommand#createWorldWithType |
| ultiworlds.world.delete | Permanently delete a world: unload it if loaded, then remove its on-disk folder and its `WorldSettings` row. Refuses ONLY the configured default world — `protected_worlds` (`world_nether`/`world_the_end` by shipped default) is NEVER consulted here despite its own comment claiming deletion protection (`UltiKits/UltiWorlds#20`). Has NO confirmation step of any kind — `WorldDeleteConfirmPage` exists in this package but is never instantiated by this or any other class (`UltiKits/UltiWorlds#19`); this single command executes the deletion immediately | command | `/world delete <name>` | ultiworlds.admin.delete | player | admin | detailed | WorldCommand#deleteWorld |
| ultiworlds.world.difficulty | Set a world's difficulty (`PEACEFUL`/`EASY`/`NORMAL`/`HARD`), persisted to `WorldSettings` and applied to the live `World` immediately | command | `/world difficulty <world> <level>` | ultiworlds.admin.settings | player | admin | brief | WorldCommand#setDifficulty |
| ultiworlds.world.help | Print `/world` usage — a base block (`help.*` keys) plus, only for a sender holding `ultiworlds.admin`, an admin block; the admin block's `difficulty`/`postcmd` lines are read from a SEPARATE `command.help.*` namespace than the rest of the block, a fact this module's own `lang/en.yml` comment (line 207) already documents, not a silent inconsistency | command | bare `/world help`, or literal `/world help` argument (the `@CmdMapping(format = "help")` site itself is unreachable through `matchMethod`, per this section's own note above) | ultiworlds.use | player | player | none | WorldCommand#handleHelp |
| ultiworlds.world.info | Show the current world's name, display name, environment, seed, online-player count, PVP state, monster state, aggregate protection state, and blocked state. Shows only 4 of `WorldSettings`'s 12 flags in aggregate/partial form — `autoUnload` and the four individual `protect_*` flags have no display path here at all. Known product gap, `UltiKits/UltiWorlds#17` | command | `/world info` | ultiworlds.use | player | player | brief | WorldCommand#worldInfo |
| ultiworlds.world.list | List every currently loaded world as text, with display name and online-player count per line | command | `/world list` | ultiworlds.use | player | player | brief | WorldCommand#listWorlds |
| ultiworlds.world.load | Bring an on-disk-but-unloaded world back online; accepts a name that is either currently loaded (no-op success) or present as an on-disk folder, unlike `## GUI`'s `ultiworlds.gui.world-list-page` which only ever lists already-loaded worlds | command | `/world load <name>` | ultiworlds.admin.load | player | admin | brief | WorldCommand#loadWorld |
| ultiworlds.world.open-list | Open the paginated world-list GUI (`WorldListPage`) | command | bare `/world` | ultiworlds.use | player | player | brief | WorldCommand#openWorldList |
| ultiworlds.world.postcmd-add | Append one console command (with `{player}`/`{world}` placeholders, resolved at teleport time by `WorldService#executePostTeleportCommands`) to a world's post-teleport command list, newline-joined onto any existing ones | command | `/world postcmd <world> add <command...>` | ultiworlds.admin.settings | player | admin | brief | WorldCommand#addPostCmd |
| ultiworlds.world.postcmd-clear | Remove every post-teleport command configured for a world | command | `/world postcmd <world> clear` | ultiworlds.admin.settings | player | admin | brief | WorldCommand#clearPostCmd |
| ultiworlds.world.postcmd-list | List every post-teleport command currently configured for a world, one per line | command | `/world postcmd <world> list` | ultiworlds.admin.settings | player | admin | brief | WorldCommand#listPostCmd |
| ultiworlds.world.protect | Enable all four protection flags (`protectBreak`/`protectPlace`/`protectInteract`/`protectExplosion`) on a world in one call | command | `/world protect <world>` | ultiworlds.admin.protect | player | admin | brief | WorldCommand#protectWorld |
| ultiworlds.world.set-option | Set one named world option (a boolean flag, `displayname`, `description`, `icon`, or `difficulty`) by value, persisted to `WorldSettings`. The `pvp` option additionally applies immediately to the live `World#setPVP`; `difficulty` additionally applies immediately to the live `World#setDifficulty` — every other option is settings-only until something else reads it | command | `/world set <world> <option> <value>` | ultiworlds.admin.settings | player | admin | detailed | WorldCommand#setWorldOption |
| ultiworlds.world.setspawn | Set the CALLING player's current location as their current world's spawn point, applied both to `WorldSettings` and the live `World#setSpawnLocation` | command | `/world setspawn` | ultiworlds.admin.setspawn | player | admin | brief | WorldCommand#setWorldSpawn |
| ultiworlds.world.teleport | Teleport the caller to a named world's spawn (or configured custom spawn) location, subject to `tp_to_world.enabled`, a 5-second command cooldown (`@CmdCD(5)`, independent of `tp_to_world.cooldown`'s own separate cooldown enforced inside `WorldService#teleportToWorld`), and access checks | command | `/world tp <world>` | ultiworlds.use | player | player | brief | WorldCommand#teleportToWorld |
| ultiworlds.world.unblock | Clear `blocked` on a world, allowing teleportation again | command | `/world unblock <world>` | ultiworlds.admin.block | player | admin | brief | WorldCommand#unblockWorld |
| ultiworlds.world.unload | Unload a loaded world, teleporting any present players to the default world first. Refuses the configured default world | command | `/world unload <name>` | ultiworlds.admin.unload | player | admin | brief | WorldCommand#unloadWorld |
| ultiworlds.world.unprotect | Disable all four protection flags on a world in one call | command | `/world unprotect <world>` | ultiworlds.admin.protect | player | admin | brief | WorldCommand#unprotectWorld |
| ultiworlds.world.wizard | Multi-step, 60-second-timeout conversation wizard: name → environment → terrain type → generate-structures (y/n) → optional seed → confirm, then creates the world and teleports the player into it | command | `/world wizard` | ultiworlds.admin.create | player | admin | detailed | WorldCreateConversation#beginConversation |

## Protection and World Rules

`WorldListener` — one `@EventListener`-annotated class (confirmed: exactly 1), registering 9
`@EventHandler` methods, each guarding one distinct mechanic — no bundling needed, unlike the
per-action guard clusters in other modules' listeners.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiworlds.protection.block-break | Cancel block-breaking in a world with `protect_break` enabled, unless the breaker holds `ultiworlds.bypass.protection` | event | break a block in a protected world | n/a | n/a | player | brief | WorldListener#onBlockBreak |
| ultiworlds.protection.block-place | Cancel block-placing in a world with `protect_place` enabled, unless the placer holds `ultiworlds.bypass.protection` | event | place a block in a protected world | n/a | n/a | player | brief | WorldListener#onBlockPlace |
| ultiworlds.protection.explosion | Clear the destroyed-block list of any explosion in a world with `protect_explosion` enabled (the explosion itself still occurs — only block damage is suppressed) | event | trigger an explosion in a protected world | n/a | n/a | player | brief | WorldListener#onEntityExplode |
| ultiworlds.protection.interact | Cancel block interaction in a world with `protect_interact` enabled, unless the actor holds `ultiworlds.bypass.protection` | event | right-click a block in a protected world | n/a | n/a | player | brief | WorldListener#onPlayerInteract |
| ultiworlds.protection.pvp | Cancel player-vs-player damage in a world with PVP disabled | event | attack another player in a PVP-disabled world | n/a | n/a | player | brief | WorldListener#onPlayerDamage |
| ultiworlds.rules.mob-spawn | Cancel monster or animal spawning per-world, gated independently by `monstersEnabled`/`animalsEnabled` — a fixed, hardcoded entity-type switch list (17 monster types, 15 animal types), not derived from any Bukkit category enum | event | a mob attempts to spawn in a world with the matching flag disabled | n/a | n/a | internal | brief | WorldListener#onCreatureSpawn |
| ultiworlds.rules.weather | Cancel a transition INTO a weather state (rain/thunder) in a world with `weatherEnabled: false`; does not affect an already-active weather state or a transition back to clear | event | weather attempts to change in a world with weather disabled | n/a | n/a | internal | brief | WorldListener#onWeatherChange |
| ultiworlds.world.access-guard | Cancel a cross-world teleport into a blocked or locked world (respecting `ultiworlds.bypass.blocked`/`ultiworlds.bypass.locked`), or into a permission-gated world without the matching `ultiworlds.world.<name>`/`ultiworlds.world.*` node when `tp_to_world.permission_per_world` is enabled; sends the specific reason (blocked/locked/no-permission) | event | attempt any cross-world teleport (not only `/world tp`) into a restricted world | n/a | n/a | player | detailed | WorldListener#onPlayerTeleport |
| ultiworlds.world.change-effects | On crossing into a different world, apply that world's `pvpEnabled` flag to the live `World#setPVP`, and (if `InventoryIsolationService` is registered) trigger an inventory save/load swap when the two worlds are in different inventory-isolation groups | event | change world (teleport, portal, etc.) | n/a | n/a | internal | brief | WorldListener#onPlayerChangeWorld |

## GUI

Phase 9 excluded all three classes below from this module's JaCoCo `check` gate
(`.planning/phases/09-module-ecosystem-readiness-and-test-coverage/gui-exclusions/UltiWorlds.md`).
Unlike UltiLogin's and UltiMail's GUI-excluded classes, two of this module's three are dead code
with no live entry point — see each row's own note.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiworlds.gui.world-delete-confirm | A confirm/cancel dialog for world deletion, applying the same default-world refusal `/world delete` itself has. Never instantiated by any class in this module — `/world delete` deletes immediately with no confirmation step at all (`UltiKits/UltiWorlds#19`); this page has NO live entry point | gui | none — dead code, unreachable through any command, listener, or other class | n/a | n/a | internal | detailed | WorldDeleteConfirmPage#onConfirm |
| ultiworlds.gui.world-list-gui | A predecessor to `WorldListPage`: implements `InventoryHolder` directly, entirely hardcoded Simplified Chinese lore/labels, no i18n at all, no protection/locked/blocked indicator. Never instantiated by any class in this module — fully superseded by `WorldListPage`, which `/world` (bare) actually opens (`UltiKits/UltiWorlds#18`); this page has NO live entry point | gui | none — dead code, unreachable through any command, listener, or other class | n/a | n/a | internal | brief | WorldListGUI#createWorldItem |
| ultiworlds.gui.world-list-page | Paginated (45 items/page) world list: one icon per VISIBLE (non-hidden) world, lore showing description/environment/player-count/time/weather/PVP/monster state, plus protected/locked/blocked indicators; clicking closes the GUI and teleports the viewer to that world via the same path as `/world tp` | gui | `ultiworlds.world.open-list` | n/a | n/a | player | detailed | WorldListPage#createWorldIcon |

## Scheduled Tasks

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiworlds.task.auto-unload-empty-worlds | Every 60 seconds, when `auto_unload.enabled`, unload any non-protected world (per `protected_worlds` AND its own `WorldSettings#autoUnload` flag) that has had zero players for at least `auto_unload.unload_after` seconds | scheduled | runs automatically every 1200 ticks (60s) while the server is up | n/a | n/a | internal | brief | WorldService#checkAutoUnloadEmptyWorlds |

## Configuration Gate

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiworlds.gate.inventory-isolation | Register the `InventoryIsolationService` bean (and therefore every inventory/ender-chest/XP/health/hunger/effects save-swap behaviour in `## Protection and World Rules`'s `ultiworlds.world.change-effects` row) only if `world_isolation.enabled` is `true` at component-scan time; a change to this key takes effect only on a full server restart, not `/ul reload` — `@ConditionalOnConfig` is evaluated once, at boot | gate | `world_isolation.enabled` in `plugins/UltiTools/UltiWorlds/config/worlds.yml`, applied only on a full server restart | n/a | n/a | admin | brief | InventoryIsolationService#InventoryIsolationService |

## Data persistence

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiworlds.settings.restart-survival | Per-world settings (`world_settings` table: rules, protection, spawn, post-teleport commands) and, when `world_isolation.enabled`, per-player-per-group inventory snapshots (`ulti_world_inventory` table) both survive a full server restart | persistence | change a world's settings (or trigger an inventory-isolation swap), then restart the server | n/a | n/a | admin | none | WorldSettings, WorldInventory |

## Configuration

Every `@ConfigEntry`-annotated field on this module's one `@ConfigEntity` class (27 keys total,
matching the reconciliation table's own `@ConfigEntry` count exactly). Several of these keys
already have a behavioural row above (block/protect/teleport/wizard/auto-unload/inventory
isolation) — that row documents the *feature* the key drives, this row documents the *key*
itself, at key granularity, so the reconciliation table can prove every key is accounted for
without also making every behavioural row carry a `config` Kind.

**Seven keys are declared, self-documented in this module's own comments (both the Java field
comment and the shipped `worlds.yml` file) as legacy/deprecated, and never read by any production
code** — this is a documented, deliberate deprecation, not a silent defect, and is called out per
row below without a filed issue: `unload_empty_worlds`, `unload_delay` (both explicitly commented
"Deprecated: use ... instead"), and the five `messages.*` keys (the shipped `worlds.yml` heads
that whole block "Messages (legacy, prefer i18n)").

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultiworlds.config.worlds.auto_unload.check_interval | Declared as the auto-unload check interval; never read — `WorldService#checkAutoUnloadEmptyWorlds`'s own `@Scheduled(period = 1200, ...)` annotation is a fixed, hardcoded 60-second period regardless of this key's value | config | `config/worlds.yml: auto_unload.check_interval (default: 60, has no effect)` | n/a | n/a | admin | brief | WorldConfig#emptyWorldCheckInterval (declared, never read outside this class) |
| ultiworlds.config.worlds.auto_unload.enabled | Master switch for the empty-world auto-unload scheduled task | config | `config/worlds.yml: auto_unload.enabled (default: false)` | n/a | n/a | admin | brief | WorldService#checkAutoUnloadEmptyWorlds |
| ultiworlds.config.worlds.auto_unload.unload_after | Seconds a non-protected, `autoUnload`-eligible world must sit empty before the scheduled task unloads it | config | `config/worlds.yml: auto_unload.unload_after (default: 300)` | n/a | n/a | admin | brief | WorldService#checkAutoUnloadEmptyWorlds |
| ultiworlds.config.worlds.default_world | The world name treated as the server's default — `/world unload`/`/world delete` both refuse to act on it, and it is the fallback teleport target when kicking players out of a blocked/unloading world | config | `config/worlds.yml: default_world (default: "world")` | n/a | n/a | admin | detailed | WorldCommand#unloadWorld, WorldCommand#deleteWorld, WorldCommand#blockWorld |
| ultiworlds.config.worlds.gui_title | World-list GUI title; shipped default is Simplified Chinese, not reproduced per D-02 — see this same file's source line for the exact characters, customizable, independent of `language` | config | `config/worlds.yml: gui_title (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | WorldListPage#WorldListPage |
| ultiworlds.config.worlds.load_worlds_on_start | World names to load automatically during this module's own `@PostConstruct` boot step | config | `config/worlds.yml: load_worlds_on_start (default: [])` | n/a | n/a | admin | brief | WorldService#init |
| ultiworlds.config.worlds.messages.no_permission | Declared as the no-permission-for-world message; never read — every actual no-permission refusal in this module goes through `plugin.i18n(...)` (`error.no_permission`), not this config field | config | `config/worlds.yml: messages.no_permission (default: Simplified Chinese text, not reproduced per D-02, has no effect)` | n/a | n/a | admin | none | WorldConfig#noPermissionMessage (declared, never read outside this class) |
| ultiworlds.config.worlds.messages.world_created | Declared as the world-created message; never read — the actual `/world create` success line comes from `world.create.success` via `plugin.i18n(...)` | config | `config/worlds.yml: messages.world_created (default: Simplified Chinese text, not reproduced per D-02, has no effect)` | n/a | n/a | admin | none | WorldConfig#worldCreatedMessage (declared, never read outside this class) |
| ultiworlds.config.worlds.messages.world_deleted | Declared as the world-deleted message; never read — the actual `/world delete` success line comes from `world.delete.success` via `plugin.i18n(...)` | config | `config/worlds.yml: messages.world_deleted (default: Simplified Chinese text, not reproduced per D-02, has no effect)` | n/a | n/a | admin | none | WorldConfig#worldDeletedMessage (declared, never read outside this class) |
| ultiworlds.config.worlds.messages.world_not_found | Declared as the world-not-found message; never read — the actual refusal line comes from `world.not_found` via `plugin.i18n(...)` | config | `config/worlds.yml: messages.world_not_found (default: Simplified Chinese text, not reproduced per D-02, has no effect)` | n/a | n/a | admin | none | WorldConfig#worldNotFoundMessage (declared, never read outside this class) |
| ultiworlds.config.worlds.messages.world_teleport | Declared as the successful-teleport message; never read — the actual `/world tp` success line comes from `success.teleported` via `plugin.i18n(...)` | config | `config/worlds.yml: messages.world_teleport (default: Simplified Chinese text, not reproduced per D-02, has no effect)` | n/a | n/a | admin | none | WorldConfig#worldTeleportMessage (declared, never read outside this class) |
| ultiworlds.config.worlds.protected_worlds | This key's own declared comment (`WorldConfig.java:30`) reads "Worlds that cannot be auto-unloaded or deleted" — the auto-unload half is true (`checkAutoUnloadEmptyWorlds` consults it), the deletion half is false: `/world delete` never consults this list at all, guarded solely by the separate `default_world` check. `UltiKits/UltiWorlds#20` | config | `config/worlds.yml: protected_worlds (default: [world, world_nether, world_the_end])` | n/a | n/a | admin | detailed | WorldService#checkAutoUnloadEmptyWorlds |
| ultiworlds.config.worlds.tp_to_world.cooldown | Per-player teleport cooldown (seconds) enforced inside `WorldService#teleportToWorld`, independent of `/world tp`'s own separate 5-second `@CmdCD` command-level cooldown | config | `config/worlds.yml: tp_to_world.cooldown (default: 10)` | n/a | n/a | admin | detailed | WorldService#canTeleport |
| ultiworlds.config.worlds.tp_to_world.enabled | Master switch for `/world tp` specifically; does NOT gate teleports triggered by clicking a world in the GUI, nor any other cross-world teleport `WorldListener#onPlayerTeleport` intercepts | config | `config/worlds.yml: tp_to_world.enabled (default: true)` | n/a | n/a | admin | detailed | WorldCommand#teleportToWorld |
| ultiworlds.config.worlds.tp_to_world.permission_per_world | Whether entering a world requires the specific `ultiworlds.world.<name>` (or `ultiworlds.world.*`) permission node, checked both by `/world tp` and by the general cross-world teleport guard | config | `config/worlds.yml: tp_to_world.permission_per_world (default: false)` | n/a | n/a | admin | brief | WorldService#checkTeleportPermissions |
| ultiworlds.config.worlds.tp_to_world.show_description | Whether a world's configured description is sent to the player immediately after a successful teleport | config | `config/worlds.yml: tp_to_world.show_description (default: true)` | n/a | n/a | admin | brief | WorldService#sendDescription |
| ultiworlds.config.worlds.unload_delay | Declared as the deprecated predecessor to `auto_unload.unload_after`; both this module's own field comment and the shipped `worlds.yml` label it "Deprecated: use auto_unload.unload_after instead"; never read by any production code | config | `config/worlds.yml: unload_delay (default: 300, deprecated, has no effect)` | n/a | n/a | admin | none | WorldConfig#unloadDelay (declared, never read outside this class) |
| ultiworlds.config.worlds.unload_empty_worlds | Declared as the deprecated predecessor to `auto_unload.enabled`; both this module's own field comment and the shipped `worlds.yml` label it "Deprecated: use auto_unload.enabled instead"; never read by any production code | config | `config/worlds.yml: unload_empty_worlds (default: false, deprecated, has no effect)` | n/a | n/a | admin | none | WorldConfig#unloadEmptyWorlds (declared, never read outside this class) |
| ultiworlds.config.worlds.world_isolation.enabled | Master switch for per-world inventory isolation — see `## Configuration Gate`'s own row for this key's boot-time-only, `@ConditionalOnConfig` semantics | config | `config/worlds.yml: world_isolation.enabled (default: false)` | n/a | n/a | admin | detailed | InventoryIsolationService#InventoryIsolationService |
| ultiworlds.config.worlds.world_isolation.separate_effects | Whether active potion effects are saved/restored per inventory-isolation group | config | `config/worlds.yml: world_isolation.separate_effects (default: false)` | n/a | n/a | admin | brief | InventoryIsolationService#saveInventory |
| ultiworlds.config.worlds.world_isolation.separate_ender_chest | Whether ender chest contents are saved/restored per inventory-isolation group | config | `config/worlds.yml: world_isolation.separate_ender_chest (default: true)` | n/a | n/a | admin | brief | InventoryIsolationService#saveInventory |
| ultiworlds.config.worlds.world_isolation.separate_experience | Whether XP level/points are saved/restored per inventory-isolation group | config | `config/worlds.yml: world_isolation.separate_experience (default: false)` | n/a | n/a | admin | brief | InventoryIsolationService#saveInventory |
| ultiworlds.config.worlds.world_isolation.separate_health | Whether health/max-health are saved/restored per inventory-isolation group | config | `config/worlds.yml: world_isolation.separate_health (default: false)` | n/a | n/a | admin | brief | InventoryIsolationService#saveInventory |
| ultiworlds.config.worlds.world_isolation.separate_hunger | Whether food level/saturation are saved/restored per inventory-isolation group | config | `config/worlds.yml: world_isolation.separate_hunger (default: false)` | n/a | n/a | admin | brief | InventoryIsolationService#saveInventory |
| ultiworlds.config.worlds.world_isolation.separate_inventory | Whether main inventory, armor, and off-hand contents are saved/restored per inventory-isolation group | config | `config/worlds.yml: world_isolation.separate_inventory (default: true)` | n/a | n/a | admin | brief | InventoryIsolationService#saveInventory |
| ultiworlds.config.worlds.world_isolation.shared_worlds | Comma-separated world-name groups that share one inventory-isolation record; a world not named in any group is its own singleton group | config | `config/worlds.yml: world_isolation.shared_worlds (default: ["world,world_nether,world_the_end"])` | n/a | n/a | admin | detailed | InventoryIsolationService#parseWorldGroups |
| ultiworlds.config.worlds.world_spawn.use_spawn_location | Whether a teleport destination uses the world's configured custom spawn (`WorldSettings#spawnX/Y/Z`, when non-zero X) instead of the world's own Bukkit spawn location | config | `config/worlds.yml: world_spawn.use_spawn_location (default: true)` | n/a | n/a | admin | brief | WorldService#resolveDestination |
