# UltiWorlds — UAT Checklist

This document is the executable companion to `FEATURES.md`: one row per feature stating the
steps to exercise it and the observable truth that proves it works. It is an internal reference
for real-machine verification, not user-facing documentation.

> Batches are dispatched at 60 rows or fewer, and a batch never spans two repositories. There are
> exactly two legitimate exits to `human-uat-pending`: a row needing the pixel layer while the
> real-client harness is not ready, and a row needing personal credentials. Every other row must
> reach `pass`, `fail`, or `blocked`.

## Conventions

- **Columns:** `ID`, `Preconditions`, `Steps`, `Expected`, `Layer`, `Covers`.
- **ID:** cites its `FEATURES.md` ID verbatim. A negative case suffixes the checklist ID only,
  as `.neg-<slug>` — a negative case still tests the same feature, so the base ID is unchanged.
- **Layer**, copied verbatim from Laojun's own `ultitools-real-client-uat` skill so no
  translation step exists at dispatch time: `protocol`, `java-client`, `os-input`, `pixel`,
  `server`, `human`.
- This module has no row needing personal credentials or a maintainer-authenticated UltiCloud
  panel session — the D-27b pattern (stated here for template consistency) does not currently
  apply to any row below.
- **Dead-code rows:** `ultiworlds.gui.world-list-gui` and `ultiworlds.gui.world-delete-confirm`
  cannot be exercised on a live server at all — neither class is ever instantiated by any other
  class in this module (`UltiKits/UltiWorlds#18`, `#19`). Their rows are Layer `protocol`
  (confirmed by reading the source directly, the same treatment the framework's and other
  modules' checklists give an analogous unobservable-from-the-command-surface path), not
  `human-uat-pending` — the class is not merely hard to reach, it is verifiably unreachable.
- **Destructive-command safety (D-05/T-10-19):** every world-creating or world-deleting row below
  uses its own throwaway world name, never reused by another row, and every deleting row appears
  strictly after every row that reads or writes the world it deletes. Throwaway names used in
  this document: `uatworld1` (created first, used throughout, deleted last), `uatworld2`
  (NETHER, exercises unload/load/block/unblock), `uatworld3` (created via the wizard), and
  `uatworld-delete` (created and deleted by the same row, used nowhere else).
- **Expected** must name an observable truth — an exact chat line, a log line, a database row,
  an inventory slot — and never the words "it works".
- **Covers** back-references a Phase 9 GUI-excluded class name; left blank when no such class
  applies.
- A row whose Preconditions name a prior row must appear after that row in file order — asserted
  mechanically: for every row, every checklist ID cited in its Preconditions cell must have a
  strictly smaller line number in this file than the row citing it (sweep class 8, D-27a).
- **Config-per-file rule (D-06):** one checklist row per `@ConfigEntity`-annotated class, never
  one row per key. The row's ID is suffixed `-yml` (`ultiworlds.config.worlds-yml`), aggregating
  every per-key `ultiworlds.config.worlds.*` row rather than citing a single one of them.
- Every row's Expected quotes the ACTUAL i18n text from `lang/en.yml` (`§`-coded, not `&`-coded —
  this module's own lang catalogue already uses the real section-sign character, so no
  `ChatColor.translateAlternateColorCodes` gap exists here the way it does in other modules).
  `gui_title`'s shipped default is the one Simplified Chinese string in this module's live
  surface (not reproduced per D-02).
- This module ships `tp_to_world.enabled: true`, `tp_to_world.cooldown: 10`,
  `auto_unload.enabled: false`, `world_isolation.enabled: false`, `default_world: "world"`,
  `protected_worlds: [world, world_nether, world_the_end]` as its defaults; every row below
  assumes these unless its own Preconditions say otherwise.

## World

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultiworlds.world.create | `language: en`; sender holds `ultiworlds.admin.create`; no world named `uatworld1` exists | Run `/world create uatworld1` | `world.create.creating` with `{WORLD}` substituted (gold), then `world.create.success` with `{WORLD}` substituted (green); `uatworld1` is now a loaded, NORMAL-environment world | server | |
| ultiworlds.world.create.neg-exists | `language: en`; sender holds `ultiworlds.admin.create`; `uatworld1` already exists (from the row above) | Run `/world create uatworld1` | `world.create.exists` with `{WORLD}` substituted (red); no new world is created | server | |
| ultiworlds.world.create-with-type | `language: en`; sender holds `ultiworlds.admin.create`; no world named `uatworld2` exists | Run `/world create uatworld2 NETHER` | `world.create.creating` then `world.create.success`, both with `{WORLD}` substituted; `uatworld2` is now a loaded, NETHER-environment world | server | |
| ultiworlds.world.create-with-type.neg-invalid-type | `language: en`; sender holds `ultiworlds.admin.create` | Run `/world create uatworld-badtype notanenvironment` | `world.create.invalid_type`: "Invalid world type! Use: NORMAL, NETHER, THE_END" (red); no world is created | server | |
| ultiworlds.world.difficulty | `language: en`; sender holds `ultiworlds.admin.settings`; `uatworld1` exists (loaded) | Run `/world difficulty uatworld1 HARD` | `success.difficulty_set` with `%value%`/`%world%` substituted to `HARD`/`uatworld1` (green); the live world's difficulty is now `HARD`; a subsequent `/world info` while standing in `uatworld1` would NOT show this (see `ultiworlds.world.info`'s own known gap, `UltiKits/UltiWorlds#17`) | server | |
| ultiworlds.world.difficulty.neg-invalid | `language: en`; sender holds `ultiworlds.admin.settings`; `uatworld1` exists | Run `/world difficulty uatworld1 notadifficulty` | `error.invalid_difficulty`: "Invalid difficulty! Use: PEACEFUL, EASY, NORMAL, HARD" (red); the world's difficulty is unchanged | server | |
| ultiworlds.world.help | `language: en`; sender does NOT hold `ultiworlds.admin` | Run bare `/world help` (the annotated `@CmdMapping(format = "help")` site is unreachable through `matchMethod` — the framework's literal-`help` short-circuit reaches `#handleHelp` first, per this module's own `FEATURES.md` note) | `help.header` (gold) then four lines (`help.list`, `help.tp`, `help.info`, `help.wizard`); the admin block (`help.admin_header` onward) is NOT shown | server | |
| ultiworlds.world.help.neg-admin | `language: en`; sender holds `ultiworlds.admin` | Run bare `/world help` | The same four-line base block as `ultiworlds.world.help`, PLUS `help.admin_header` and eight further admin lines (`help.create`, `help.load`, `help.unload`, `help.delete`, `help.setspawn`, `help.set`, `help.protect`, `help.block`), PLUS two more lines read from the SEPARATE `command.help.*` namespace (`command.help.difficulty`, `command.help.postcmd`) — a fact this module's own `lang/en.yml` comment (line 207) already documents as deliberate, not a defect | server | |
| ultiworlds.world.info | `language: en`; standing in a world with default settings | Run `/world info` | `world.info.header` (gold) then eight lines: name, display name, environment, seed, player count, PVP state, monster state, aggregate protection state, blocked state — via `common.enabled`/`common.disabled`/`common.yes`/`common.no`. Does NOT show `autoUnload` or the four individual `protect_*` flags (only an aggregate "any protection enabled" boolean) — known gap, `UltiKits/UltiWorlds#17` | server | |
| ultiworlds.world.list | `language: en`; at least `uatworld1` and `uatworld2` loaded (from the two rows above) | Run `/world list` | `world.list.header` with `{COUNT}` substituted to the actual loaded-world count (gold); one `world.list.item` line per world, each showing display name and online-player count | server | |
| ultiworlds.world.unload | `language: en`; sender holds `ultiworlds.admin.unload`; `uatworld2` currently loaded, NOT the configured `default_world` | Run `/world unload uatworld2` | `world.unload.success` with `{WORLD}` substituted (yellow); `uatworld2` is no longer in `Bukkit#getWorlds()`, but its on-disk folder and `WorldSettings` row remain | server | |
| ultiworlds.world.unload.neg-default | `language: en`; sender holds `ultiworlds.admin.unload` | Run `/world unload world` (the configured `default_world`) | `world.unload.default`: "Cannot unload the default world!" (red); the default world remains loaded | server | |
| ultiworlds.world.load | `language: en`; sender holds `ultiworlds.admin.load`; `uatworld2` currently UNLOADED (run `ultiworlds.world.unload` on it first) | Run `/world load uatworld2` | `world.load.success` with `{WORLD}` substituted (green); `uatworld2` is loaded again, its `WorldSettings` (including the `HARD`-adjacent NETHER environment) intact | server | |
| ultiworlds.world.load.neg-not-found | `language: en`; sender holds `ultiworlds.admin.load`; no world (loaded or on-disk) named `uatworld-nonexistent` | Run `/world load uatworld-nonexistent` | `world.not_found` with `{WORLD}` substituted (red) | server | |
| ultiworlds.world.open-list | `language: en`; at least one visible (non-hidden) world loaded | Run bare `/world` | `WorldListPage` opens, titled `gui.title` ("World List"); one icon per visible world with description/environment/player-count/time/weather/PVP/monster lore, plus protected/locked/blocked indicators when applicable; clicking an icon closes the GUI and teleports the viewer there | pixel | WorldListPage |
| ultiworlds.world.postcmd-add | `language: en`; sender holds `ultiworlds.admin.settings`; `uatworld1` exists; no post-teleport commands configured for it yet | Run `/world postcmd uatworld1 add say hello {player}` | `success.post_cmd_added` with `%world%` substituted (green) | server | |
| ultiworlds.world.postcmd-clear | `language: en`; sender holds `ultiworlds.admin.settings`; `uatworld1` has at least one post-teleport command (from the row above) | Run `/world postcmd uatworld1 clear` | `success.post_cmd_cleared` with `%world%` substituted (green); a subsequent `/world postcmd uatworld1 list` shows `success.post_cmd_empty` | server | |
| ultiworlds.world.postcmd-list | `language: en`; `uatworld1` has the post-teleport command from `ultiworlds.world.postcmd-add` | Run `/world postcmd uatworld1 list` | `success.post_cmd_list_header` with `%world%` substituted (gold) then one `success.post_cmd_list_item` line per configured command (gray dash, white command text) | server | |
| ultiworlds.world.protect | `language: en`; sender holds `ultiworlds.admin.protect`; `uatworld1` exists | Run `/world protect uatworld1` | `world.protect.enabled` with `{WORLD}` substituted (green); all four of `uatworld1`'s `WorldSettings` protection flags (`protectBreak`/`protectPlace`/`protectInteract`/`protectExplosion`) are now `true` | server | |
| ultiworlds.world.set-option | `language: en`; sender holds `ultiworlds.admin.settings`; `uatworld1` exists and loaded | Run `/world set uatworld1 pvp false` | `world.set.success` with `{OPTION}`/`{VALUE}`/`{WORLD}` substituted (green); `uatworld1`'s live `World#getPVP()` is now `false`, AND `WorldSettings#isPvpEnabled()` is `false` — `pvp` is the one boolean option this command applies to the live world immediately, not just to settings | server | |
| ultiworlds.world.set-option.neg-invalid-option | `language: en`; sender holds `ultiworlds.admin.settings`; `uatworld1` exists | Run `/world set uatworld1 notanoption somevalue` | `world.set.invalid_option`: "Invalid option! Use: pvp, monsters, animals, weather, hidden, locked, blocked, displayname, description, icon, difficulty" (red) | server | |
| ultiworlds.world.set-option.neg-world-not-found | `language: en`; sender holds `ultiworlds.admin.settings`; no world named `uatworld-nonexistent` is loaded | Run `/world set uatworld-nonexistent pvp true` | `world.not_found` with `{WORLD}` substituted (red) | server | |
| ultiworlds.world.setspawn | `language: en`; sender holds `ultiworlds.admin.setspawn`; standing anywhere in a loaded world | Run `/world setspawn` | `world.setspawn.success` with `{WORLD}` substituted (green) — NOT `success.spawn_set`, a separate catalogue entry `WorldCommand#setWorldSpawn` never reads; the CALLER's current world's spawn point (both `WorldSettings#spawnX/Y/Z/Yaw/Pitch` and the live `World#getSpawnLocation()`) is now the caller's location at the moment the command ran | server | |
| ultiworlds.world.teleport | `language: en`; `tp_to_world.enabled: true` (shipped default); `uatworld1` exists and loaded; caller not on the 5-second `@CmdCD` cooldown or the separate `tp_to_world.cooldown` (10s) | Run `/world tp uatworld1` | `success.teleported` with `%world%` substituted to `uatworld1`'s display name (green); the caller is now standing in `uatworld1` | server | |
| ultiworlds.world.teleport.neg-disabled | `language: en`; `tp_to_world.enabled: false` (NOT the shipped default) | Run `/world tp uatworld1` | `world.tp.disabled`: "Teleporting to worlds via this command is disabled!" (red); the caller does not move | server | |
| ultiworlds.world.block | `language: en`; sender holds `ultiworlds.admin.block`; `uatworld2` exists and loaded; a second player standing inside `uatworld2` | Run `/world block uatworld2` | The second player is teleported to the default world's spawn and sees `world.block.kicked` with `{WORLD}` substituted (red); the sender sees `world.block.enabled` with `{WORLD}` substituted (red); `uatworld2`'s `blocked` flag is now `true` | server | |

## Protection and World Rules

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultiworlds.protection.block-break | `language: en`; `uatworld1` has `protect_break: true` (see `ultiworlds.world.protect`); the acting player does NOT hold `ultiworlds.bypass.protection` | Attempt to break a block in `uatworld1` | The break is cancelled; `protection.break_denied`: "Block breaking is protected in this world!" (red) | server | |
| ultiworlds.protection.block-place | `language: en`; `uatworld1` has `protect_place: true`; the acting player does NOT hold `ultiworlds.bypass.protection` | Attempt to place a block in `uatworld1` | The placement is cancelled; `protection.place_denied`: "Block placing is protected in this world!" (red) | server | |
| ultiworlds.protection.explosion | `language: en`; `uatworld1` has `protect_explosion: true` | Trigger an explosion (e.g. detonate a creeper or TNT) in `uatworld1` | The explosion occurs (sound/particles), but zero blocks are destroyed — `event.blockList()` is cleared, not the event itself cancelled | server | |
| ultiworlds.protection.interact | `language: en`; `uatworld1` has `protect_interact: true`; the acting player does NOT hold `ultiworlds.bypass.protection` | Right-click an interactive block (e.g. a door or button) in `uatworld1` | The interaction is cancelled; `protection.interact_denied`: "Interaction is protected in this world!" (red) | server | |
| ultiworlds.protection.pvp | `language: en`; `uatworld1`'s `pvpEnabled: false` (see `ultiworlds.world.set-option`) | One player attacks another in `uatworld1` | The damage event is cancelled — the target takes no damage; the attacker sees `protection.pvp_disabled`: "PVP is disabled in this world!" (red) | server | |
| ultiworlds.rules.mob-spawn | `language: en`; `uatworld2`'s `monstersEnabled: false` (set via `/world set uatworld2 monsters false`) | Wait for or force a hostile-mob spawn attempt (e.g. a zombie) in `uatworld2` at night | No zombie (or other monster-category entity from the fixed 17-type list) spawns; a passive mob (e.g. a cow, still on the separate `animalsEnabled` flag) is unaffected | server | |
| ultiworlds.rules.weather | `language: en`; `uatworld2`'s `weatherEnabled: false` (set via `/world set uatworld2 weather false`) | Force or wait for a weather-change attempt (rain/thunder) in `uatworld2` | The world remains clear — the transition into rain/thunder is cancelled; an existing already-active weather state (if any) is unaffected by this cancellation alone | server | |
| ultiworlds.world.access-guard | `language: en`; `uatworld2`'s `blocked: true` (see `ultiworlds.world.block`); a player NOT holding `ultiworlds.bypass.blocked` attempts ANY cross-world teleport into it (not only `/world tp` — e.g. a portal) | Attempt to enter `uatworld2` by a means other than `/world tp` (e.g. right-click a nether portal that would lead there) | The teleport is cancelled; the player sees `error.world_blocked`: "This world has been blocked!" (red) | server | |
| ultiworlds.world.change-effects | `language: en`; `world_isolation.enabled: false` (shipped default — the inventory-isolation half of this handler is exercised separately, under its own `@ConditionalOnConfig` gate, in `## Configuration Gate` below); `uatworld1`'s `pvpEnabled: true`, `uatworld2`'s `pvpEnabled: false` (from earlier rows) | Cross from `uatworld1` into `uatworld2` (or back) | The live `World#getPVP()` of the DESTINATION world is applied — entering `uatworld2` shows PVP now disabled for that world specifically, confirmed via a PVP attempt (`ultiworlds.protection.pvp`); with isolation disabled, no inventory swap occurs | server | |

## World (continued)

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultiworlds.world.unblock | `language: en`; sender holds `ultiworlds.admin.block`; `uatworld2` currently blocked (from `ultiworlds.world.block` below, run first) | Run `/world unblock uatworld2` | `world.block.disabled` with `{WORLD}` substituted (green); `uatworld2`'s `blocked` flag is now `false`; a subsequent `/world tp uatworld2` now succeeds | server | |
| ultiworlds.world.unprotect | `language: en`; sender holds `ultiworlds.admin.protect`; `uatworld1` currently protected (from `ultiworlds.world.protect` above) | Run `/world unprotect uatworld1` | `world.protect.disabled` with `{WORLD}` substituted (yellow); all four protection flags on `uatworld1` are now `false` | server | |
| ultiworlds.world.wizard | `language: en`; sender holds `ultiworlds.admin.create`; no world named `uatworld3` exists | Run `/world wizard`, then respond: `uatworld3` (name), `1` (Overworld), `2` (Flat), `n` (no structures), `random` (seed), `y` (confirm) | `wizard.header` then `wizard.cancel_hint`, then five sequential prompts matching each answer's step; `wizard.creating` then `wizard.success` with `%world%` substituted; `uatworld3` is created (Overworld, Flat terrain) and the player is teleported into it | server | |
| ultiworlds.world.wizard.neg-timeout | `language: en`; sender holds `ultiworlds.admin.create` | Run `/world wizard`, then do not respond to any prompt for over 60 seconds | `wizard.timeout`: "Operation timed out, world creation cancelled" (red, `sendRawMessage`); no world is created | server | |
| ultiworlds.world.delete | `language: en`; sender holds `ultiworlds.admin.delete`; a throwaway world `uatworld-delete` created via `/world create uatworld-delete` for this row alone, used nowhere else in this document | Run `/world delete uatworld-delete` | `world.delete.deleting` with `{WORLD}` substituted (gold), then `world.delete.success` with `{WORLD}` substituted (red-colored success line, matching the actual `world.delete.success` key's own red color code) — executed IMMEDIATELY with no confirmation step of any kind (`UltiKits/UltiWorlds#19`); `uatworld-delete`'s on-disk folder and `WorldSettings` row are both gone | server | |
| ultiworlds.world.delete.neg-default | `language: en`; sender holds `ultiworlds.admin.delete` | Run `/world delete world` (the configured `default_world`) | `world.delete.default`: "Cannot delete the default world!" (red); the default world's folder is untouched | server | |
| ultiworlds.world.delete.neg-protected-not-actually-protected | `language: en`; sender holds `ultiworlds.admin.delete`; a throwaway world `uatworld-protected-test` created via `/world create uatworld-protected-test`, then added to `protected_worlds` in `config/worlds.yml` (requires a restart to take effect, since it is read at multiple call sites, none of which reload live) | Run `/world delete uatworld-protected-test` | The world IS deleted despite being listed in `protected_worlds` — `protected_worlds` is consulted only by the auto-unload scheduled task, never by `/world delete`, contradicting that key's own declared comment ("Worlds that cannot be auto-unloaded or deleted"). Known product defect, `UltiKits/UltiWorlds#20` | server | |


## GUI

Phase 9 excluded all three classes below from this module's JaCoCo `check` gate
(`.planning/phases/09-module-ecosystem-readiness-and-test-coverage/gui-exclusions/UltiWorlds.md`).

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultiworlds.gui.world-delete-confirm | none — `WorldDeleteConfirmPage` is never instantiated anywhere in this module's source (`grep -rln WorldDeleteConfirmPage src/main/java` returns only its own file) | Read the pinned copies `checklist-copy/src@969ae5a2/WorldDeleteConfirmPage.java` (SHA-256 `a9ae0b4b4a9bffc9ab293ec42e34e12cfb72fa024e781e88b10407b7765db605`) and `checklist-copy/src@969ae5a2/WorldCommand.java` (SHA-256 `2d84a5eb7b8f710a493ee06d3076b5eae33e94c0e11d33f1a0770b9a16ca514c`), not the live source tree, per D-11 (a structural assertion about unreachable code with no runtime observable a real-machine dispatch can produce) | `onConfirm` correctly re-checks the `default_world` refusal and calls `WorldService#deleteWorld`, and `onCancel` sends `command.delete.cancelled` — the CLASS's own logic is sound, but `WorldCommand#deleteWorld` deletes immediately without ever constructing this page, so none of this logic is reachable from a running server. Known product gap, `UltiKits/UltiWorlds#19` | protocol | WorldDeleteConfirmPage |
| ultiworlds.gui.world-list-gui | none — `WorldListGUI` is never instantiated anywhere in this module's source (`grep -rln "new WorldListGUI" src/main/java` returns nothing) | Read the pinned copies `checklist-copy/src@969ae5a2/WorldListGUI.java` (SHA-256 `41b1136bd05370f89824d242a27bc324f835a44488eed025001086e4e4b64350`) and `checklist-copy/src@969ae5a2/WorldCommand.java` (SHA-256 `2d84a5eb7b8f710a493ee06d3076b5eae33e94c0e11d33f1a0770b9a16ca514c`), not the live source tree, per D-11 (a structural assertion about unreachable code with no runtime observable a real-machine dispatch can produce) | `WorldListGUI`'s own icon-building logic (hardcoded Simplified Chinese lore, no protected/locked/blocked indicator) is intact but unreachable — `openWorldList` (bare `/world`) opens `WorldListPage` instead, a separate, i18n-driven, currently-maintained class. Known product gap, `UltiKits/UltiWorlds#18` | protocol | WorldListGUI |
| ultiworlds.gui.world-list-page | `language: en`; at least one visible world loaded | Same steps as `ultiworlds.world.open-list` | Same observable as `ultiworlds.world.open-list` — this row exists to satisfy the GUI-exclusion back-reference for `WorldListPage` specifically, distinct from the command-trigger row above | pixel | WorldListPage |

## Scheduled Tasks

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultiworlds.task.auto-unload-empty-worlds | `auto_unload.enabled: true` (NOT the shipped default), reloaded via `/ul reload` (or restart) after the edit; `auto_unload.unload_after: 60` (the minimum value `@Range(min = 60, max = 86400)` on `WorldConfig#emptyWorldUnloadAfter` accepts — NOT the shipped default 300, since a lower value fails config validation and would not exercise the real path at all), also reloaded; a non-protected, `autoUnload`-eligible throwaway world `uatworld-autounload` created via `/world create uatworld-autounload`, then left with zero players | Wait at least 130 seconds (past the 60-second empty threshold, plus at least one full 60-second scheduler tick to guarantee the sweep actually ran after that threshold elapsed) | `uatworld-autounload` is no longer in `Bukkit#getWorlds()` — the console log shows "Auto-unloading empty world: uatworld-autounload" | server | |

## Configuration Gate

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultiworlds.gate.inventory-isolation | `world_isolation.enabled: true` set BEFORE a full server restart (a `/ul reload` alone does NOT apply this — `@ConditionalOnConfig` is evaluated once, at boot) | Restart the server, then cross between two worlds in DIFFERENT `world_isolation.shared_worlds` groups while `world_isolation.separate_inventory: true` (shipped default) | The player's main inventory contents differ between the two worlds (items placed in one group's inventory are absent when standing in a different group, and reappear on returning) — `InventoryIsolationService` was registered at boot because the gate was `true` at component-scan time | server | |
| ultiworlds.gate.inventory-isolation.neg-disabled | `world_isolation.enabled: false` (shipped default), confirmed before a restart | Restart the server, then cross between any two worlds | The player's inventory is identical in both worlds (no isolation) — `InventoryIsolationService` was never registered because the gate was `false` at component-scan time | server | |

## Data persistence

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultiworlds.settings.restart-survival | `uatworld1`'s settings changed via `ultiworlds.world.set-option`/`.protect` earlier in this document; `world_isolation.enabled: true` with at least one inventory-isolation swap having occurred (see `ultiworlds.gate.inventory-isolation`) | Stop the server completely, then start it again, then run `/world info` while standing in `uatworld1`, and separately cross back into the isolated world group | `uatworld1`'s settings (PVP-disabled state, protection flags) read back identically to their pre-restart values; the isolated inventory snapshot from before the restart is still applied on crossing back into that group | server | |

## Configuration

One row per shipped yml file (D-06's config-per-file rule): `worlds.yml` (27 keys). The row
confirms every key is present at its `FEATURES.md`-documented default, then flips one or more
representative keys and observes the behaviour follow — **except the seven keys `FEATURES.md`
documents as declared-legacy-and-dead** (`unload_empty_worlds`, `unload_delay`, and the five
`messages.*` keys), which this row deliberately does NOT attempt to exercise for an effect.

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultiworlds.config.worlds-yml | Fresh `config/worlds.yml` at its shipped default | Load the file; confirm all 27 keys listed under `FEATURES.md`'s `## Configuration` section are present at their documented defaults; then, for EACH scenario below, edit the key on disk and run `/ul reload` (or restart) before testing — the commands read the already-bound `WorldConfig` instance, and merely editing the file does not change it: (a) set `tp_to_world.enabled: false` (default true), reload, and confirm `/world tp` now refuses with `world.tp.disabled`, matching `ultiworlds.world.teleport.neg-disabled`; (b) separately (after restoring `tp_to_world.enabled: true` and reloading again) set `default_world: uatworld1` (default `world`, using the throwaway world created earlier in this document), reload, and confirm `/world unload world` NOW succeeds (the ORIGINAL default is no longer protected) while `/world unload uatworld1` is NOW refused with `world.unload.default`. Do NOT vary `unload_empty_worlds`, `unload_delay`, or any `messages.*` key expecting an observable effect — none has one (`UltiKits/UltiWorlds#20`'s sibling finding on `protected_worlds` is a separate, already-documented gap, not exercised by this row) | All 27 keys present at their documented defaults before any change; (a) `/world tp` is refused with `tp_to_world.enabled: false` after reload, where it previously succeeded; (b) after changing `default_world` to `uatworld1` and reloading, `/world unload world` succeeds (previously refused) and `/world unload uatworld1` is refused (previously would have succeeded) — proving the default-world check reads the LIVE, reloaded config value, not a value captured once at boot | server | |
