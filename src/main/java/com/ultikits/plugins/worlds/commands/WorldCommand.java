package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.conversation.WorldCreateConversation;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.gui.WorldDeleteConfirmPage;
import com.ultikits.plugins.worlds.gui.WorldListPage;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * World management command executor.
 * Migrated to BaseCommandExecutor with improved annotations.
 *
 * <p>Sender restriction: the class admits both players and the console only so that
 * {@code /world delete} can reach the console (UltiKits/UltiWorlds#19). Every other mapping carries
 * its own {@code @CmdTarget(PLAYER)} and so stays player-only, exactly as it was when the whole
 * class was player-only. A new mapping must carry one too unless the console is meant to reach it.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.BOTH)
@CmdExecutor(
    alias = {"world", "worlds", "w"},
    permission = "ultiworlds.use",
    description = "世界管理系统"
)
public class WorldCommand extends BaseCommandExecutor {

    /**
     * The options handled by the boolean-token parser in {@code set &lt;world&gt; &lt;option&gt; &lt;value&gt;}.
     */
    private static final List<String> BOOLEAN_OPTIONS = Arrays.asList(
            "pvp", "monsters", "animals", "weather", "hidden", "locked", "blocked");

    @Autowired
    private UltiToolsPlugin plugin;

    @Autowired
    private WorldService worldService;

    /** Time source for the console's delete confirmation window; replaced in tests. */
    private LongSupplier clock = System::currentTimeMillis;

    /** The console's pending {@code /world delete} requests (UltiKits/UltiWorlds#19). */
    private final ConsoleDeleteConfirmations consoleDeleteConfirmations = new ConsoleDeleteConfirmations();

    // ==================== Basic Commands ====================
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "")
    public void openWorldList(@CmdSender Player player) {
        new WorldListPage(player, worldService, plugin).open();
    }
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "list")
    public void listWorlds(@CmdSender Player player) {
        List<World> worlds = worldService.getAllWorlds();
        
        player.sendMessage(i18n("world.list.header").replace("{COUNT}", String.valueOf(worlds.size())));
        for (World world : worlds) {
            WorldSettings settings = worldService.getOrCreateSettings(world.getName());
            String displayName = settings.getDisplayName() != null ? settings.getDisplayName() : world.getName();
            player.sendMessage(i18n("world.list.item")
                .replace("{NAME}", displayName)
                .replace("{PLAYERS}", String.valueOf(world.getPlayers().size())));
        }
    }
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "tp <world>")
    @CmdCD(5)
    public void teleportToWorld(@CmdSender Player player, @CmdParam(value = "world", suggest = "suggestWorlds") String worldName) {
        if (!worldService.getConfig().isTpToWorldEnabled()) {
            player.sendMessage(i18n("world.tp.disabled"));
            return;
        }
        
        worldService.teleportToWorld(player, worldName);
    }
    
    // ==================== Create Commands ====================
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "wizard")
    public void startWizard(@CmdSender Player player) {
        if (!player.hasPermission("ultiworlds.admin.create")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }
        
        WorldCreateConversation.start(player, worldService, plugin);
    }
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "create <name>")
    public void createWorld(@CmdSender Player player, @CmdParam("name") String name) {
        if (!player.hasPermission("ultiworlds.admin.create")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }
        
        if (Bukkit.getWorld(name) != null) {
            player.sendMessage(i18n("world.create.exists").replace("{WORLD}", name));
            return;
        }
        
        player.sendMessage(i18n("world.create.creating").replace("{WORLD}", name));
        
        if (worldService.createWorld(name, World.Environment.NORMAL, WorldType.NORMAL, null)) {
            player.sendMessage(i18n("world.create.success").replace("{WORLD}", name));
        } else {
            player.sendMessage(i18n("world.create.failed"));
        }
    }
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "create <name> <type>")
    public void createWorldWithType(@CmdSender Player player,
                                    @CmdParam("name") String name,
                                    @CmdParam(value = "type", suggest = "suggestWorldTypes") String type) {
        if (!player.hasPermission("ultiworlds.admin.create")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }
        
        World.Environment environment;
        try {
            environment = World.Environment.valueOf(type.toUpperCase());
        } catch (Exception e) {
            player.sendMessage(i18n("world.create.invalid_type"));
            return;
        }
        
        player.sendMessage(i18n("world.create.creating").replace("{WORLD}", name));
        
        if (worldService.createWorld(name, environment, WorldType.NORMAL, null)) {
            player.sendMessage(i18n("world.create.success").replace("{WORLD}", name));
        } else {
            player.sendMessage(i18n("world.create.failed"));
        }
    }
    
    // ==================== Load/Unload Commands ====================
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "load <name>")
    public void loadWorld(@CmdSender Player player, @CmdParam("name") String name) {
        if (!player.hasPermission("ultiworlds.admin.load")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }

        if (!requireLoadableWorld(player, name)) {
            return;
        }

        if (worldService.loadWorld(name)) {
            player.sendMessage(i18n("world.load.success").replace("{WORLD}", name));
        } else {
            player.sendMessage(i18n("world.load.failed"));
        }
    }
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "unload <name>")
    public void unloadWorld(@CmdSender Player player, @CmdParam(value = "name", suggest = "suggestWorlds") String name) {
        if (!player.hasPermission("ultiworlds.admin.unload")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }
        
        // Case-insensitive on purpose: CraftServer#getWorld resolves its argument as
        // name.toLowerCase(Locale.ROOT), so an exact comparison here lets "/world unload LOBBY"
        // past the guard and then unloads the real "lobby".
        if (name.equalsIgnoreCase(worldService.getConfig().getDefaultWorld())) {
            player.sendMessage(i18n("world.unload.default"));
            return;
        }
        
        if (worldService.unloadWorld(name, true)) {
            player.sendMessage(i18n("world.unload.success").replace("{WORLD}", name));
        } else {
            player.sendMessage(i18n("world.unload.failed"));
        }
    }
    
    @CmdMapping(format = "delete <name>")
    public void deleteWorld(@CmdSender CommandSender sender, @CmdParam(value = "name", suggest = "suggestWorlds") String name) {
        // The class admits any non-player sender, but this command is opened to the server console
        // only (UltiKits/UltiWorlds#19): a command block, a minecart or an /execute proxy repeating
        // the same line would otherwise confirm a deletion with no one at the keyboard.
        boolean fromConsole = sender instanceof ConsoleCommandSender;
        if (!(sender instanceof Player) && !fromConsole) {
            sender.sendMessage(i18n("world.delete.sender_not_allowed"));
            return;
        }

        if (!passesDeleteChecks(sender, name)) {
            // A refused request or repeat never leaves a console confirmation behind for a later
            // repeat to use: after a refusal the console starts again from the first request.
            if (fromConsole) {
                consoleDeleteConfirmations.discard(sender.getName(), name);
            }
            return;
        }

        if (sender instanceof Player) {
            // Deleting a world cannot be undone, so this command only asks: the deletion happens
            // when the player presses confirm on this page, and cancelling or closing it deletes
            // nothing (UltiKits/UltiWorlds#19). The page repeats the refusals above at that
            // moment, because the world's protection or the player's permission can change while
            // it is open.
            new WorldDeleteConfirmPage((Player) sender, worldService, name, plugin).open();
            return;
        }

        // The console cannot use the page, so it confirms by repeating this exact command within
        // the window. Every check above has run again on the repeat before this point.
        if (!consoleDeleteConfirmations.confirm(sender.getName(), name, clock.getAsLong())) {
            sender.sendMessage(i18n("world.delete.confirm_console")
                .replace("{WORLD}", name)
                .replace("{SECONDS}", String.valueOf(ConsoleDeleteConfirmations.WINDOW_SECONDS)));
            return;
        }

        sender.sendMessage(i18n("world.delete.deleting").replace("{WORLD}", name));
        if (worldService.deleteWorld(name)) {
            sender.sendMessage(i18n("world.delete.success").replace("{WORLD}", name));
        } else {
            sender.sendMessage(i18n("world.delete.failed"));
        }
    }

    /**
     * Every refusal {@code /world delete} makes, for either sender, in the order it makes them;
     * sends the refusal and returns {@code false} at the first one that applies. Runs on the
     * console's first request and again on its repeat.
     */
    private boolean passesDeleteChecks(CommandSender sender, String name) {
        if (!sender.hasPermission("ultiworlds.admin.delete")) {
            sender.sendMessage(i18n("error.no_permission"));
            return false;
        }

        if (!requireDeletableWorld(sender, name)) {
            return false;
        }

        // Case-insensitive for the same reason as the unload guard above, and additionally so
        // that "/world delete WORLD" is not told it is "listed in protected_worlds" when the real
        // reason is that it is the default world -- a statement about the operator's own
        // configuration has to be true.
        if (name.equalsIgnoreCase(worldService.getConfig().getDefaultWorld())) {
            sender.sendMessage(i18n("world.delete.default"));
            return false;
        }

        // WorldService#deleteWorld refuses this on its own -- asking here only decides WHICH
        // message the sender gets, so that a protected world is not reported as a generic failure.
        if (worldService.isDeleteProtected(name)) {
            sender.sendMessage(i18n("world.delete.protected").replace("{WORLD}", name));
            return false;
        }
        return true;
    }
    
    // ==================== Settings Commands ====================
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "set <world> <option> <value>")
    public void setWorldOption(@CmdSender Player player,
                               @CmdParam(value = "world", suggest = "suggestWorlds") String worldName,
                               @CmdParam(value = "option", suggest = "suggestOptions") String option,
                               @CmdParam(value = "value", suggest = "suggestBooleans") String value) {
        if (!player.hasPermission("ultiworlds.admin.settings")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(i18n("world.not_found").replace("{WORLD}", worldName));
            return;
        }
        
        WorldSettings settings = worldService.getOrCreateSettings(worldName);

        Boolean boolValue = null;
        if (BOOLEAN_OPTIONS.contains(option.toLowerCase())) {
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equals("1")) {
                boolValue = Boolean.TRUE;
            } else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off") || value.equals("0")) {
                boolValue = Boolean.FALSE;
            } else {
                player.sendMessage(i18n("error.invalid_value"));
                return;
            }
        }

        switch (option.toLowerCase()) {
            case "pvp":
                settings.setPvpEnabled(boolValue);
                world.setPVP(boolValue);
                break;
            case "monsters":
                settings.setMonstersEnabled(boolValue);
                break;
            case "animals":
                settings.setAnimalsEnabled(boolValue);
                break;
            case "weather":
                settings.setWeatherEnabled(boolValue);
                break;
            case "hidden":
                settings.setHidden(boolValue);
                break;
            case "locked":
                settings.setLocked(boolValue);
                break;
            case "blocked":
                settings.setBlocked(boolValue);
                break;
            case "displayname":
            case "name":
                settings.setDisplayName(value);
                break;
            case "description":
            case "desc":
                settings.setDescription(value);
                break;
            case "icon":
                settings.setIcon(value.toUpperCase());
                break;
            case "difficulty":
                try {
                    Difficulty diff = Difficulty.valueOf(value.toUpperCase());
                    settings.setDifficulty(diff.name());
                    World w = Bukkit.getWorld(worldName);
                    if (w != null) {
                        w.setDifficulty(diff);
                    }
                } catch (IllegalArgumentException e) {
                    player.sendMessage(i18n("error.invalid_difficulty"));
                    return;
                }
                break;
            default:
                player.sendMessage(i18n("world.set.invalid_option"));
                return;
        }
        
        worldService.updateSettings(settings);
        player.sendMessage(i18n("world.set.success")
            .replace("{OPTION}", option)
            .replace("{VALUE}", value)
            .replace("{WORLD}", worldName));
    }
    
    // ==================== Protection Commands ====================
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "protect <world>")
    public void protectWorld(@CmdSender Player player, @CmdParam(value = "world", suggest = "suggestWorlds") String worldName) {
        if (!player.hasPermission("ultiworlds.admin.protect")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(i18n("world.not_found").replace("{WORLD}", worldName));
            return;
        }
        
        WorldSettings settings = worldService.getOrCreateSettings(worldName);
        settings.enableFullProtection();
        worldService.updateSettings(settings);
        
        player.sendMessage(i18n("world.protect.enabled").replace("{WORLD}", worldName));
    }
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "unprotect <world>")
    public void unprotectWorld(@CmdSender Player player, @CmdParam(value = "world", suggest = "suggestWorlds") String worldName) {
        if (!player.hasPermission("ultiworlds.admin.protect")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(i18n("world.not_found").replace("{WORLD}", worldName));
            return;
        }
        
        WorldSettings settings = worldService.getOrCreateSettings(worldName);
        settings.disableAllProtection();
        worldService.updateSettings(settings);
        
        player.sendMessage(i18n("world.protect.disabled").replace("{WORLD}", worldName));
    }
    
    // ==================== Block Commands ====================
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "block <world>")
    public void blockWorld(@CmdSender Player player, @CmdParam(value = "world", suggest = "suggestWorlds") String worldName) {
        if (!player.hasPermission("ultiworlds.admin.block")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(i18n("world.not_found").replace("{WORLD}", worldName));
            return;
        }
        
        WorldSettings settings = worldService.getOrCreateSettings(worldName);
        settings.setBlocked(true);
        worldService.updateSettings(settings);
        
        // Kick all players from the world
        World defaultWorld = Bukkit.getWorld(worldService.getConfig().getDefaultWorld());
        if (defaultWorld == null) {
            defaultWorld = Bukkit.getWorlds().get(0);
        }
        
        for (Player p : world.getPlayers()) {
            p.teleport(defaultWorld.getSpawnLocation());
            p.sendMessage(i18n("world.block.kicked").replace("{WORLD}", worldName));
        }
        
        player.sendMessage(i18n("world.block.enabled").replace("{WORLD}", worldName));
    }
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "unblock <world>")
    public void unblockWorld(@CmdSender Player player, @CmdParam(value = "world", suggest = "suggestWorlds") String worldName) {
        if (!player.hasPermission("ultiworlds.admin.block")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(i18n("world.not_found").replace("{WORLD}", worldName));
            return;
        }
        
        WorldSettings settings = worldService.getOrCreateSettings(worldName);
        settings.setBlocked(false);
        worldService.updateSettings(settings);
        
        player.sendMessage(i18n("world.block.disabled").replace("{WORLD}", worldName));
    }
    
    // ==================== Other Commands ====================
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "setspawn")
    public void setWorldSpawn(@CmdSender Player player) {
        if (!player.hasPermission("ultiworlds.admin.setspawn")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }
        
        worldService.setWorldSpawn(player.getWorld().getName(), player.getLocation());
        player.sendMessage(i18n("world.setspawn.success").replace("{WORLD}", player.getWorld().getName()));
    }
    
    // ==================== Difficulty Command ====================

    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "difficulty <world> <level>")
    public void setDifficulty(@CmdSender Player player,
                              @CmdParam(value = "world", suggest = "suggestWorlds") String worldName,
                              @CmdParam(value = "level", suggest = "suggestDifficulties") String level) {
        if (!player.hasPermission("ultiworlds.admin.settings")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(i18n("error.world_not_found").replace("%world%", worldName));
            return;
        }

        Difficulty difficulty;
        try {
            difficulty = Difficulty.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(i18n("error.invalid_difficulty"));
            return;
        }

        WorldSettings settings = worldService.getOrCreateSettings(worldName);
        settings.setDifficulty(difficulty.name());
        worldService.updateSettings(settings);
        world.setDifficulty(difficulty);

        player.sendMessage(i18n("success.difficulty_set")
            .replace("%value%", difficulty.name())
            .replace("%world%", worldName));
    }

    // ==================== Post-Teleport Command Management ====================

    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "postcmd <world> add <command...>")
    public void addPostCmd(@CmdSender Player player,
                           @CmdParam(value = "world", suggest = "suggestWorlds") String worldName,
                           @CmdParam("command") String[] command) {
        if (!player.hasPermission("ultiworlds.admin.settings")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }

        if (!requireWorld(player, worldName)) {
            return;
        }

        // Varargs binding hands back a zero-length array (never null) when the caller supplied no
        // trailing words -- see BaseCommandExecutor#parseParameterValue -- so an empty join is the
        // signal to refuse, not a NullPointerException to guard against.
        String joinedCommand = String.join(" ", command);
        if (joinedCommand.isEmpty()) {
            player.sendMessage(i18n("error.invalid_value"));
            return;
        }

        WorldSettings settings = worldService.getOrCreateSettings(worldName);
        String existing = settings.getPostTeleportCommands();
        if (existing == null || existing.isEmpty()) {
            settings.setPostTeleportCommands(joinedCommand);
        } else {
            settings.setPostTeleportCommands(existing + "\n" + joinedCommand);
        }
        worldService.updateSettings(settings);

        player.sendMessage(i18n("success.post_cmd_added").replace("%world%", worldName));
    }

    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "postcmd <world> list")
    public void listPostCmd(@CmdSender Player player,
                            @CmdParam(value = "world", suggest = "suggestWorlds") String worldName) {
        if (!player.hasPermission("ultiworlds.admin.settings")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }

        if (!requireWorld(player, worldName)) {
            return;
        }

        WorldSettings settings = worldService.getOrCreateSettings(worldName);
        String commands = settings.getPostTeleportCommands();

        player.sendMessage(i18n("success.post_cmd_list_header").replace("%world%", worldName));
        if (commands == null || commands.isEmpty()) {
            player.sendMessage(i18n("success.post_cmd_empty"));
        } else {
            for (String cmd : commands.split("\\n")) {
                player.sendMessage(i18n("success.post_cmd_list_item").replace("%command%", cmd.trim()));
            }
        }
    }

    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "postcmd <world> clear")
    public void clearPostCmd(@CmdSender Player player,
                             @CmdParam(value = "world", suggest = "suggestWorlds") String worldName) {
        if (!player.hasPermission("ultiworlds.admin.settings")) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }

        if (!requireWorld(player, worldName)) {
            return;
        }

        WorldSettings settings = worldService.getOrCreateSettings(worldName);
        settings.setPostTeleportCommands(null);
        worldService.updateSettings(settings);

        player.sendMessage(i18n("success.post_cmd_cleared").replace("%world%", worldName));
    }

    // ==================== Info Command ====================

    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "info")
    public void worldInfo(@CmdSender Player player) {
        World world = player.getWorld();
        WorldSettings settings = worldService.getOrCreateSettings(world.getName());
        
        player.sendMessage(i18n("world.info.header"));
        player.sendMessage(i18n("world.info.name").replace("{VALUE}", world.getName()));
        player.sendMessage(i18n("world.info.displayname").replace("{VALUE}", 
            settings.getDisplayName() != null ? settings.getDisplayName() : world.getName()));
        player.sendMessage(i18n("world.info.environment").replace("{VALUE}", world.getEnvironment().name()));
        player.sendMessage(i18n("world.info.seed").replace("{VALUE}", String.valueOf(world.getSeed())));
        player.sendMessage(i18n("world.info.players").replace("{VALUE}", String.valueOf(world.getPlayers().size())));
        player.sendMessage(i18n("world.info.pvp").replace("{VALUE}", 
            settings.isPvpEnabled() ? i18n("common.enabled") : i18n("common.disabled")));
        player.sendMessage(i18n("world.info.monsters").replace("{VALUE}", 
            settings.isMonstersEnabled() ? i18n("common.enabled") : i18n("common.disabled")));
        player.sendMessage(i18n("world.info.protection").replace("{VALUE}", 
            settings.hasProtection() ? i18n("common.enabled") : i18n("common.disabled")));
        player.sendMessage(i18n("world.info.blocked").replace("{VALUE}", 
            settings.isBlocked() ? i18n("common.yes") : i18n("common.no")));
    }
    
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdMapping(format = "help")
    public void help(@CmdSender Player player) {
        player.sendMessage(i18n("help.header"));
        player.sendMessage(i18n("help.list"));
        player.sendMessage(i18n("help.tp"));
        player.sendMessage(i18n("help.info"));
        player.sendMessage(i18n("help.wizard"));
        
        if (player.hasPermission("ultiworlds.admin")) {
            player.sendMessage(i18n("help.admin_header"));
            player.sendMessage(i18n("help.create"));
            player.sendMessage(i18n("help.load"));
            player.sendMessage(i18n("help.unload"));
            player.sendMessage(i18n("help.delete"));
            player.sendMessage(i18n("help.setspawn"));
            player.sendMessage(i18n("help.set"));
            player.sendMessage(i18n("help.protect"));
            player.sendMessage(i18n("help.block"));
            player.sendMessage(i18n("command.help.difficulty"));
            player.sendMessage(i18n("command.help.postcmd"));
        }
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        if (sender instanceof Player) {
            help((Player) sender);
            return;
        }
        // A non-player reaches this through "/world help" (the framework answers that literal
        // argument before matching a mapping, gated only by the class-level target, which admits
        // non-players for /world delete). The console is shown the one subcommand it can run; any
        // other non-player can run none of them.
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(i18n("world.delete.sender_not_allowed"));
            return;
        }
        sender.sendMessage(i18n("help.header"));
        sender.sendMessage(i18n("help.delete_console")
            .replace("{SECONDS}", String.valueOf(ConsoleDeleteConfirmations.WINDOW_SECONDS)));
    }
    
    // ==================== Suggestion Methods ====================
    
    /**
     * Suggest world names for tab completion.
     */
    public List<String> suggestWorlds(Player player, String input) {
        return Bukkit.getWorlds().stream()
            .map(World::getName)
            .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
            .collect(Collectors.toList());
    }
    
    /**
     * Suggest world types for tab completion.
     */
    public List<String> suggestWorldTypes(Player player, String input) {
        return Arrays.asList("NORMAL", "NETHER", "THE_END").stream()
            .filter(type -> type.toLowerCase().startsWith(input.toLowerCase()))
            .collect(Collectors.toList());
    }
    
    /**
     * Suggest setting options for tab completion.
     */
    public List<String> suggestOptions(Player player, String input) {
        return Arrays.asList("pvp", "monsters", "animals", "weather", "hidden",
            "locked", "blocked", "displayname", "description", "icon", "difficulty").stream()
            .filter(opt -> opt.toLowerCase().startsWith(input.toLowerCase()))
            .collect(Collectors.toList());
    }
    
    /**
     * Suggest boolean values for tab completion.
     */
    public List<String> suggestBooleans(Player player, String input) {
        return Arrays.asList("true", "false", "on", "off").stream()
            .filter(val -> val.startsWith(input.toLowerCase()))
            .collect(Collectors.toList());
    }

    /**
     * Suggest difficulty levels for tab completion.
     */
    public List<String> suggestDifficulties(Player player, String input) {
        return Arrays.asList("PEACEFUL", "EASY", "NORMAL", "HARD").stream()
            .filter(d -> d.toLowerCase().startsWith(input.toLowerCase()))
            .collect(Collectors.toList());
    }
    
    /**
     * Refuse a name that is not filesystem-safe, or that does not name a loaded world. Sends the
     * same refusal message the other validating handlers already send.
     *
     * <p>This deliberately does not apply the creation wizard's narrower alphanumeric/length
     * naming convention ({@link WorldCreateConversation#WORLD_NAME_PATTERN}): the world this checks
     * already exists, so it may have been named before the wizard shipped, or by other tooling.
     * See {@link WorldService#isFilesystemSafeWorldName(String)}.
     *
     * @return true if the caller should continue, false if a refusal was already sent
     */
    private boolean requireWorld(Player player, String worldName) {
        if (!WorldService.isFilesystemSafeWorldName(worldName)
                || Bukkit.getWorld(worldName) == null) {
            player.sendMessage(i18n("world.not_found").replace("{WORLD}", worldName));
            return false;
        }
        return true;
    }

    /**
     * Whether {@code worldName} is filesystem-safe and names a world that is loaded, or any
     * ENTRY present in the world container -- a symbolic link included, whether or not the thing
     * it points at still exists. Used by {@link #requireDeletableWorld}. Like
     * {@link #requireLoadableWorld} and unlike {@link #requireWorld}, it accepts a world that is on
     * disk but not currently loaded.
     *
     * <p>This and {@link #existsLoadedOrHasWorldDataOnDisk(String)} were one method until gate-2
     * round 6, and that is what the defect was: deleting and loading ask different questions of the
     * same path, and one link-following call cannot answer both. {@link File#exists()} resolves a
     * link, so for a link whose target is missing it answers about the target -- and an entry
     * plainly present in the container was reported to the operator as a world that does not exist,
     * then left in place after its settings row had already been removed. Deleting asks about the
     * entry, so this one does not follow.
     */
    private static boolean existsLoadedOrHasAnEntryOnDisk(String worldName) {
        return WorldService.isFilesystemSafeWorldName(worldName)
                && (Bukkit.getWorld(worldName) != null
                    || Files.exists(new File(Bukkit.getWorldContainer(), worldName).toPath(),
                                    LinkOption.NOFOLLOW_LINKS));
    }

    /**
     * Whether {@code worldName} is filesystem-safe and names a world that is loaded, or one whose
     * world DATA is on disk. Used by {@link #requireLoadableWorld}, and deliberately follows a
     * symbolic link: a link whose target is missing has nothing to load, so refusing it here gives
     * the operator a clearer message than the service's generic load failure would.
     */
    private static boolean existsLoadedOrHasWorldDataOnDisk(String worldName) {
        return WorldService.isFilesystemSafeWorldName(worldName)
                && (Bukkit.getWorld(worldName) != null
                    || new File(Bukkit.getWorldContainer(), worldName).exists());
    }

    /**
     * Refuse a name that is not filesystem-safe, or that names neither a loaded world nor an
     * on-disk world folder. Unlike {@link #requireWorld}, this does not require the world to be
     * currently loaded: {@code WorldService.deleteWorld} deliberately supports removing an unloaded
     * world's folder and settings, so requiring "loaded" here would reject the ordinary
     * {@code /world unload} then {@code /world delete} workflow. See the note on
     * {@link #requireWorld} about why the wizard's naming convention does not apply here either.
     *
     * @return true if the caller should continue, false if a refusal was already sent
     */
    private boolean requireDeletableWorld(CommandSender sender, String worldName) {
        if (!existsLoadedOrHasAnEntryOnDisk(worldName)) {
            sender.sendMessage(i18n("world.not_found").replace("{WORLD}", worldName));
            return false;
        }
        return true;
    }

    /**
     * Refuse a name that is not filesystem-safe, or that names neither a loaded world nor an
     * on-disk world folder. {@code load} is meant to bring an unloaded-but-on-disk world back
     * online, so -- like {@link #requireDeletableWorld} and unlike {@link #requireWorld} -- this
     * does not require the world to already be loaded. Without this check, {@code WorldService}'s
     * generic load failure message was indistinguishable from "this name does not exist at all".
     *
     * @return true if the caller should continue, false if a refusal was already sent
     */
    private boolean requireLoadableWorld(Player player, String worldName) {
        if (!existsLoadedOrHasWorldDataOnDisk(worldName)) {
            player.sendMessage(i18n("world.not_found").replace("{WORLD}", worldName));
            return false;
        }
        return true;
    }

    /**
     * Get i18n message from plugin.
     */
    private String i18n(String key) {
        return plugin.i18n(key);
    }
}
