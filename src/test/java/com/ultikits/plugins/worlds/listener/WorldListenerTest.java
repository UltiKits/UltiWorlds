package com.ultikits.plugins.worlds.listener;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.service.InventoryIsolationService;
import com.ultikits.plugins.worlds.service.WorldService;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WorldListener event handling.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("WorldListener Tests")
class WorldListenerTest {

    private WorldListener listener;
    private WorldService mockWorldService;
    private InventoryIsolationService mockInventoryService;
    private UltiToolsPlugin mockPlugin;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();

        listener = new WorldListener();
        mockWorldService = mock(WorldService.class);
        mockInventoryService = mock(InventoryIsolationService.class);

        UltiWorldsTestHelper.setField(listener, "worldService", mockWorldService);
        UltiWorldsTestHelper.setField(listener, "inventoryService", mockInventoryService);
        UltiWorldsTestHelper.setField(listener, "plugin", mockPlugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("Block Protection Events")
    class BlockProtectionEvents {

        @Test
        @DisplayName("onBlockBreak should cancel when protection enabled")
        void onBlockBreakProtected() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
            when(player.hasPermission("ultiworlds.bypass.protection")).thenReturn(false);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Block block = mock(Block.class);
            when(block.getWorld()).thenReturn(world);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setProtectBreak(true);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            BlockBreakEvent event = new BlockBreakEvent(block, player);

            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isTrue();
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("onBlockBreak should allow when protection disabled")
        void onBlockBreakNotProtected() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Block block = mock(Block.class);
            when(block.getWorld()).thenReturn(world);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setProtectBreak(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            BlockBreakEvent event = new BlockBreakEvent(block, player);

            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("onBlockBreak should allow with bypass permission")
        void onBlockBreakBypass() {
            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
            when(player.hasPermission("ultiworlds.bypass.protection")).thenReturn(true);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Block block = mock(Block.class);
            when(block.getWorld()).thenReturn(world);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setProtectBreak(true);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            BlockBreakEvent event = new BlockBreakEvent(block, player);

            listener.onBlockBreak(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("onBlockPlace should cancel when protection enabled")
        void onBlockPlaceProtected() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
            when(player.hasPermission("ultiworlds.bypass.protection")).thenReturn(false);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Block block = mock(Block.class);
            when(block.getWorld()).thenReturn(world);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setProtectPlace(true);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            BlockPlaceEvent event = mock(BlockPlaceEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getBlock()).thenReturn(block);
            when(event.isCancelled()).thenReturn(false);

            listener.onBlockPlace(event);

            verify(event).setCancelled(true);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("onPlayerInteract should cancel when protection enabled")
        void onPlayerInteractProtected() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
            when(player.hasPermission("ultiworlds.bypass.protection")).thenReturn(false);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Block block = mock(Block.class);
            when(block.getWorld()).thenReturn(world);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setProtectInteract(true);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getClickedBlock()).thenReturn(block);
            when(event.isCancelled()).thenReturn(false);

            listener.onPlayerInteract(event);

            verify(event).setCancelled(true);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("onEntityExplode should clear blocks when protection enabled")
        void onEntityExplodeProtected() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setProtectExplosion(true);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            EntityExplodeEvent event = mock(EntityExplodeEvent.class);
            when(event.getLocation()).thenReturn(location);
            List<Block> blockList = new ArrayList<>();
            blockList.add(mock(Block.class));
            when(event.blockList()).thenReturn(blockList);

            listener.onEntityExplode(event);

            assertThat(blockList).isEmpty();
        }
    }

    @Nested
    @DisplayName("PVP Control")
    class PVPControl {

        @Test
        @DisplayName("onPlayerDamage should cancel when PVP disabled")
        void onPlayerDamagePvpDisabled() {
            Player victim = UltiWorldsTestHelper.createMockPlayer("Victim", UUID.randomUUID());
            Player attacker = UltiWorldsTestHelper.createMockPlayer("Attacker", UUID.randomUUID());

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            when(victim.getWorld()).thenReturn(world);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setPvpEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
            when(event.getEntity()).thenReturn(victim);
            when(event.getDamager()).thenReturn(attacker);
            when(event.isCancelled()).thenReturn(false);

            listener.onPlayerDamage(event);

            verify(event).setCancelled(true);
            verify(attacker).sendMessage(anyString());
        }

        @Test
        @DisplayName("onPlayerDamage should allow when PVP enabled")
        void onPlayerDamagePvpEnabled() {
            Player victim = UltiWorldsTestHelper.createMockPlayer("Victim", UUID.randomUUID());
            Player attacker = UltiWorldsTestHelper.createMockPlayer("Attacker", UUID.randomUUID());

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            when(victim.getWorld()).thenReturn(world);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setPvpEnabled(true);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
            when(event.getEntity()).thenReturn(victim);
            when(event.getDamager()).thenReturn(attacker);
            when(event.isCancelled()).thenReturn(false);

            listener.onPlayerDamage(event);

            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Nested
    @DisplayName("Access Control")
    class AccessControl {

        @Test
        @DisplayName("onPlayerTeleport should cancel when cannot enter blocked world")
        void onPlayerTeleportBlocked() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World fromWorld = mock(World.class);
            World toWorld = mock(World.class);
            when(toWorld.getName()).thenReturn("blocked_world");

            Location from = mock(Location.class);
            Location to = mock(Location.class);
            when(from.getWorld()).thenReturn(fromWorld);
            when(to.getWorld()).thenReturn(toWorld);

            when(mockWorldService.canEnterWorld(player, "blocked_world")).thenReturn(false);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("blocked_world");
            settings.setBlocked(true);
            when(mockWorldService.getOrCreateSettings("blocked_world")).thenReturn(settings);

            PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to);

            listener.onPlayerTeleport(event);

            assertThat(event.isCancelled()).isTrue();
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("onPlayerTeleport should allow when can enter world")
        void onPlayerTeleportAllowed() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World fromWorld = mock(World.class);
            World toWorld = mock(World.class);
            when(toWorld.getName()).thenReturn("world");

            Location from = mock(Location.class);
            Location to = mock(Location.class);
            when(from.getWorld()).thenReturn(fromWorld);
            when(to.getWorld()).thenReturn(toWorld);

            when(mockWorldService.canEnterWorld(player, "world")).thenReturn(true);

            PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to);

            listener.onPlayerTeleport(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("onPlayerTeleport should skip same world teleports")
        void onPlayerTeleportSameWorld() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World world = mock(World.class);
            Location from = mock(Location.class);
            Location to = mock(Location.class);
            when(from.getWorld()).thenReturn(world);
            when(to.getWorld()).thenReturn(world);

            PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to);

            listener.onPlayerTeleport(event);

            assertThat(event.isCancelled()).isFalse();
            verify(mockWorldService, never()).canEnterWorld(any(), anyString());
        }
    }

    @Nested
    @DisplayName("Creature Spawn Control")
    class CreatureSpawnControl {

        @Test
        @DisplayName("onCreatureSpawn should cancel monsters when disabled")
        void onCreatureSpawnMonstersDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setMonstersEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.ZOMBIE);

            listener.onCreatureSpawn(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onCreatureSpawn should cancel animals when disabled")
        void onCreatureSpawnAnimalsDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setAnimalsEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.COW);

            listener.onCreatureSpawn(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onCreatureSpawn should allow when enabled")
        void onCreatureSpawnEnabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setMonstersEnabled(true);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.ZOMBIE);

            listener.onCreatureSpawn(event);

            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Nested
    @DisplayName("Weather Control")
    class WeatherControl {

        @Test
        @DisplayName("onWeatherChange should cancel when weather disabled")
        void onWeatherChangeDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setWeatherEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            WeatherChangeEvent event = mock(WeatherChangeEvent.class);
            when(event.getWorld()).thenReturn(world);
            when(event.toWeatherState()).thenReturn(true);

            listener.onWeatherChange(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onWeatherChange should allow when weather enabled")
        void onWeatherChangeEnabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setWeatherEnabled(true);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            WeatherChangeEvent event = mock(WeatherChangeEvent.class);
            when(event.getWorld()).thenReturn(world);
            when(event.toWeatherState()).thenReturn(true);

            listener.onWeatherChange(event);

            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Nested
    @DisplayName("Access Control Extended")
    class AccessControlExtended {

        @Test
        @DisplayName("onPlayerTeleport should show locked message when world is locked")
        void onPlayerTeleportLocked() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World fromWorld = mock(World.class);
            World toWorld = mock(World.class);
            when(toWorld.getName()).thenReturn("locked_world");

            Location from = mock(Location.class);
            Location to = mock(Location.class);
            when(from.getWorld()).thenReturn(fromWorld);
            when(to.getWorld()).thenReturn(toWorld);

            when(mockWorldService.canEnterWorld(player, "locked_world")).thenReturn(false);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("locked_world");
            settings.setLocked(true);
            settings.setBlocked(false);
            when(mockWorldService.getOrCreateSettings("locked_world")).thenReturn(settings);

            PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to);

            listener.onPlayerTeleport(event);

            assertThat(event.isCancelled()).isTrue();
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("onPlayerTeleport should show permission message when neither blocked nor locked")
        void onPlayerTeleportPermissionDenied() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World fromWorld = mock(World.class);
            World toWorld = mock(World.class);
            when(toWorld.getName()).thenReturn("restricted_world");

            Location from = mock(Location.class);
            Location to = mock(Location.class);
            when(from.getWorld()).thenReturn(fromWorld);
            when(to.getWorld()).thenReturn(toWorld);

            when(mockWorldService.canEnterWorld(player, "restricted_world")).thenReturn(false);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("restricted_world");
            settings.setBlocked(false);
            settings.setLocked(false);
            when(mockWorldService.getOrCreateSettings("restricted_world")).thenReturn(settings);

            PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to);

            listener.onPlayerTeleport(event);

            assertThat(event.isCancelled()).isTrue();
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("onPlayerTeleport should skip when destination world is null")
        void onPlayerTeleportNullDestination() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World fromWorld = mock(World.class);

            Location from = mock(Location.class);
            Location to = mock(Location.class);
            when(from.getWorld()).thenReturn(fromWorld);
            when(to.getWorld()).thenReturn(null);

            PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to);

            listener.onPlayerTeleport(event);

            assertThat(event.isCancelled()).isFalse();
            verify(mockWorldService, never()).canEnterWorld(any(), anyString());
        }
    }

    @Nested
    @DisplayName("Interact Protection Extended")
    class InteractProtectionExtended {

        @Test
        @DisplayName("onPlayerInteract should skip when clicked block is null")
        void onPlayerInteractNullBlock() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getClickedBlock()).thenReturn(null);

            listener.onPlayerInteract(event);

            verify(event, never()).setCancelled(anyBoolean());
            verify(mockWorldService, never()).getOrCreateSettings(anyString());
        }
    }

    @Nested
    @DisplayName("Explosion Protection Extended")
    class ExplosionProtectionExtended {

        @Test
        @DisplayName("onEntityExplode should skip when world is null")
        void onEntityExplodeNullWorld() {
            Location location = mock(Location.class);
            when(location.getWorld()).thenReturn(null);

            EntityExplodeEvent event = mock(EntityExplodeEvent.class);
            when(event.getLocation()).thenReturn(location);

            listener.onEntityExplode(event);

            verify(mockWorldService, never()).getOrCreateSettings(anyString());
        }

        @Test
        @DisplayName("onEntityExplode should not clear blocks when protection disabled")
        void onEntityExplodeNotProtected() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setProtectExplosion(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            EntityExplodeEvent event = mock(EntityExplodeEvent.class);
            when(event.getLocation()).thenReturn(location);
            List<org.bukkit.block.Block> blockList = new ArrayList<>();
            blockList.add(mock(org.bukkit.block.Block.class));
            when(event.blockList()).thenReturn(blockList);

            listener.onEntityExplode(event);

            assertThat(blockList).hasSize(1);
        }
    }

    @Nested
    @DisplayName("PVP Control Extended")
    class PVPControlExtended {

        @Test
        @DisplayName("onPlayerDamage should ignore when entity is not Player")
        void onPlayerDamageEntityNotPlayer() {
            org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
            Player attacker = UltiWorldsTestHelper.createMockPlayer("Attacker", UUID.randomUUID());

            EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
            when(event.getEntity()).thenReturn(entity);
            when(event.getDamager()).thenReturn(attacker);

            listener.onPlayerDamage(event);

            verify(event, never()).setCancelled(anyBoolean());
        }

        @Test
        @DisplayName("onPlayerDamage should ignore when damager is not Player")
        void onPlayerDamageNotPlayerDamager() {
            Player victim = UltiWorldsTestHelper.createMockPlayer("Victim", UUID.randomUUID());
            org.bukkit.entity.Entity damager = mock(org.bukkit.entity.Entity.class);

            EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
            when(event.getEntity()).thenReturn(victim);
            when(event.getDamager()).thenReturn(damager);

            listener.onPlayerDamage(event);

            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Nested
    @DisplayName("Creature Spawn Extended")
    class CreatureSpawnExtended {

        @Test
        @DisplayName("onCreatureSpawn should skip when world is null")
        void onCreatureSpawnNullWorld() {
            Location location = mock(Location.class);
            when(location.getWorld()).thenReturn(null);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);

            listener.onCreatureSpawn(event);

            verify(mockWorldService, never()).getOrCreateSettings(anyString());
        }

        @Test
        @DisplayName("onCreatureSpawn should cancel SKELETON when monsters disabled")
        void onCreatureSpawnSkeletonDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setMonstersEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.SKELETON);

            listener.onCreatureSpawn(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onCreatureSpawn should cancel CREEPER when monsters disabled")
        void onCreatureSpawnCreeperDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setMonstersEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.CREEPER);

            listener.onCreatureSpawn(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onCreatureSpawn should cancel SPIDER when monsters disabled")
        void onCreatureSpawnSpiderDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setMonstersEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.SPIDER);

            listener.onCreatureSpawn(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onCreatureSpawn should cancel ENDERMAN when monsters disabled")
        void onCreatureSpawnEndermanDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setMonstersEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.ENDERMAN);

            listener.onCreatureSpawn(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onCreatureSpawn should cancel WITCH when monsters disabled")
        void onCreatureSpawnWitchDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setMonstersEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.WITCH);

            listener.onCreatureSpawn(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onCreatureSpawn should cancel PHANTOM when monsters disabled")
        void onCreatureSpawnPhantomDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setMonstersEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.PHANTOM);

            listener.onCreatureSpawn(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onCreatureSpawn should cancel PIG when animals disabled")
        void onCreatureSpawnPigDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setAnimalsEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.PIG);

            listener.onCreatureSpawn(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onCreatureSpawn should cancel SHEEP when animals disabled")
        void onCreatureSpawnSheepDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setAnimalsEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.SHEEP);

            listener.onCreatureSpawn(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onCreatureSpawn should cancel CHICKEN when animals disabled")
        void onCreatureSpawnChickenDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setAnimalsEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.CHICKEN);

            listener.onCreatureSpawn(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onCreatureSpawn should cancel WOLF when animals disabled")
        void onCreatureSpawnWolfDisabled() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setAnimalsEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.WOLF);

            listener.onCreatureSpawn(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("onCreatureSpawn should not cancel unknown entity type")
        void onCreatureSpawnUnknownType() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            Location location = new Location(world, 0, 64, 0);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setMonstersEnabled(false);
            settings.setAnimalsEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            when(event.getLocation()).thenReturn(location);
            when(event.getEntityType()).thenReturn(EntityType.VILLAGER);

            listener.onCreatureSpawn(event);

            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Nested
    @DisplayName("Weather Control Extended")
    class WeatherControlExtended {

        @Test
        @DisplayName("onWeatherChange should not cancel when weather is clearing")
        void onWeatherChangeClearing() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setWeatherEnabled(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            WeatherChangeEvent event = mock(WeatherChangeEvent.class);
            when(event.getWorld()).thenReturn(world);
            when(event.toWeatherState()).thenReturn(false);

            listener.onWeatherChange(event);

            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Nested
    @DisplayName("Block Place Extended")
    class BlockPlaceExtended {

        @Test
        @DisplayName("onBlockPlace should allow when protection disabled")
        void onBlockPlaceNotProtected() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
            when(block.getWorld()).thenReturn(world);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setProtectPlace(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            BlockPlaceEvent event = mock(BlockPlaceEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getBlock()).thenReturn(block);

            listener.onBlockPlace(event);

            verify(event, never()).setCancelled(anyBoolean());
        }

        @Test
        @DisplayName("onBlockPlace should allow with bypass permission")
        void onBlockPlaceBypass() {
            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
            when(player.hasPermission("ultiworlds.bypass.protection")).thenReturn(true);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
            when(block.getWorld()).thenReturn(world);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setProtectPlace(true);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            BlockPlaceEvent event = mock(BlockPlaceEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getBlock()).thenReturn(block);

            listener.onBlockPlace(event);

            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Nested
    @DisplayName("Interact Protection Bypass")
    class InteractProtectionBypass {

        @Test
        @DisplayName("onPlayerInteract should allow when protection disabled")
        void onPlayerInteractNotProtected() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
            when(block.getWorld()).thenReturn(world);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setProtectInteract(false);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getClickedBlock()).thenReturn(block);

            listener.onPlayerInteract(event);

            verify(event, never()).setCancelled(anyBoolean());
        }

        @Test
        @DisplayName("onPlayerInteract should allow with bypass permission")
        void onPlayerInteractBypass() {
            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
            when(player.hasPermission("ultiworlds.bypass.protection")).thenReturn(true);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");

            org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
            when(block.getWorld()).thenReturn(world);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setProtectInteract(true);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getClickedBlock()).thenReturn(block);

            listener.onPlayerInteract(event);

            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Nested
    @DisplayName("World Change Handling")
    class WorldChangeHandling {

        @Test
        @DisplayName("onPlayerChangeWorld should apply PVP settings")
        void onPlayerChangeWorldPvp() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World fromWorld = mock(World.class);
            World toWorld = mock(World.class);
            when(toWorld.getName()).thenReturn("pvp_world");

            when(player.getWorld()).thenReturn(toWorld);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("pvp_world");
            settings.setPvpEnabled(true);
            when(mockWorldService.getOrCreateSettings("pvp_world")).thenReturn(settings);

            PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(player, fromWorld);

            listener.onPlayerChangeWorld(event);

            verify(toWorld).setPVP(true);
        }

        @Test
        @DisplayName("onPlayerChangeWorld should trigger inventory isolation")
        void onPlayerChangeWorldInventory() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World fromWorld = mock(World.class);
            World toWorld = mock(World.class);
            when(toWorld.getName()).thenReturn("world");

            when(player.getWorld()).thenReturn(toWorld);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(player, fromWorld);

            listener.onPlayerChangeWorld(event);

            verify(mockInventoryService).onWorldChange(player, fromWorld, toWorld);
        }

        @Test
        @DisplayName("onPlayerChangeWorld should handle null inventory service")
        void onPlayerChangeWorldNullInventory() throws Exception {
            UltiWorldsTestHelper.setField(listener, "inventoryService", null);

            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World fromWorld = mock(World.class);
            World toWorld = mock(World.class);
            when(toWorld.getName()).thenReturn("world");

            when(player.getWorld()).thenReturn(toWorld);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(player, fromWorld);

            listener.onPlayerChangeWorld(event);

            // Should not throw NPE
            verify(toWorld).setPVP(anyBoolean());
        }
    }
}
