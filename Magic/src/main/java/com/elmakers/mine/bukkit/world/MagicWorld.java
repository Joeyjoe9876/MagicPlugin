package com.elmakers.mine.bukkit.world;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.elmakers.mine.bukkit.magic.MagicController;
import com.elmakers.mine.bukkit.utility.NMSUtils;
import com.elmakers.mine.bukkit.world.populator.MagicChunkHandler;
import com.elmakers.mine.bukkit.world.spawn.MagicSpawnHandler;
import com.elmakers.mine.bukkit.world.tasks.CheckWorldCreateTask;

public class MagicWorld {
    private enum WorldState { UNLOADED, LOADING, LOADED }

    private final MagicController controller;
    private final MagicChunkHandler chunkHandler;
    private final MagicSpawnHandler spawnHandler;
    private String copyFrom = "";
    private boolean autoLoad = false;
    private World.Environment worldEnvironment = World.Environment.NORMAL;
    private World.Environment appearanceEnvironment = null;
    private WorldType worldType = WorldType.NORMAL;
    private String worldName;
    private String resourcePack;
    private long seed;
    private boolean synchronizeTime = true;
    private long synchronizedTimeOffset = 0;
    private static Random random = new Random();
    private WorldState state = WorldState.UNLOADED;

    public MagicWorld(MagicController controller) {
        this.controller = controller;
        seed = random.nextLong();
        chunkHandler = new MagicChunkHandler(controller);
        spawnHandler = new MagicSpawnHandler(controller);
    }

    public void load(String name, ConfigurationSection config) {
        worldName = name;
        copyFrom = config.getString("copy", copyFrom);
        synchronizeTime = config.getBoolean("synchronize_time", synchronizeTime);
        synchronizedTimeOffset = config.getLong("time_offset", synchronizedTimeOffset);
        resourcePack = config.getString("resource_pack", resourcePack);
        if (config.contains("environment")) {
            String typeString = config.getString("environment");
            try {
                worldEnvironment = World.Environment.valueOf(typeString.toUpperCase());
            } catch (Exception ex) {
                controller.getLogger().warning("Invalid world environment: " + typeString);
            }
        }
        if (config.contains("appearance")) {
            String typeString = config.getString("appearance");
            try {
                appearanceEnvironment = World.Environment.valueOf(typeString.toUpperCase());
            } catch (Exception ex) {
                controller.getLogger().warning("Invalid world appearance: " + typeString);
            }
        }
        if (config.contains("type")) {
            String typeString = config.getString("type");
            try {
                worldType = WorldType.valueOf(typeString.toUpperCase());
            } catch (Exception ex) {
                controller.getLogger().warning("Invalid world type: " + typeString);
            }
        }
        seed = config.getLong("seed", this.seed);
        autoLoad = config.getBoolean("autoload", autoLoad);
        ConfigurationSection chunkConfig = config.getConfigurationSection("chunk_generate");
        if (chunkConfig != null) {
            chunkHandler.load(worldName, chunkConfig);
        }
        ConfigurationSection entityConfig = config.getConfigurationSection("entity_spawn");
        if (entityConfig != null) {
            spawnHandler.load(worldName, entityConfig);
        }
    }

    public void finalizeLoad() {
        // Autoload worlds
        if (autoLoad) {
            // Wait a few ticks to do this, to avoid errors during initialization
            Bukkit.getScheduler().runTaskLater(controller.getPlugin(), new CheckWorldCreateTask(this), 1L);
        }
        spawnHandler.finalizeLoad();
    }

    public void checkWorldCreate() {
        // Loaded check
        if (state != WorldState.UNLOADED) {
            return;
        }

        if (copyFrom.isEmpty()) {
            createWorld();
        } else {
            World targetWorld = controller.getPlugin().getServer().getWorld(copyFrom);
            if (targetWorld != null) {
                copyWorld(targetWorld);
            }
        }
    }

    public void createWorld() {
        state = WorldState.LOADING;
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            controller.info("Loading " + worldName + " as " + worldEnvironment + " (" + worldType + ")");
            WorldCreator worldCreator = WorldCreator.name(worldName);
            worldCreator.seed(seed);
            worldCreator.environment(worldEnvironment);
            worldCreator.type(worldType);
            worldCreator.generateStructures(true);
            try {
                world = worldCreator.createWorld();
            } catch (Exception ex) {
                world = null;
                ex.printStackTrace();
            }
            if (world == null) {
                controller.getLogger().warning("Failed to create world: " + worldName);
            }
        }
        if (world != null && appearanceEnvironment != null) {
            NMSUtils.setEnvironment(world, appearanceEnvironment);
            controller.info("Changed " + worldName + " appearance to " + appearanceEnvironment);
        }
    }

    public void installPopulators(World world) {
        if (chunkHandler.isEmpty()) return;
        controller.info("Installing Populators in " + world.getName());
        world.getPopulators().addAll(chunkHandler.getPopulators());
    }

    public boolean processEntitySpawn(Plugin plugin, LivingEntity entity) {
        return spawnHandler.process(plugin, entity);
    }

    public void onWorldInit(final World initWorld) {
        // Flag loaded worlds
        if (initWorld.getName().equals(worldName)) {
            state = WorldState.LOADED;
            updateTime();
            return;
        }

        // Loaded check
        if (state != WorldState.UNLOADED) {
            return;
        }

        // See if this is a world we want to make a copy of
        copyWorld(initWorld);
    }

    public void copyWorld(World targetWorld) {
        if (copyFrom.isEmpty() || !targetWorld.getName().equals(copyFrom)) return;

        state = WorldState.LOADING;
        // Create this world if it doesn't exist
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
           controller.info("Loading " + worldName + " using settings copied from " + targetWorld.getName());
           world = Bukkit.createWorld(new WorldCreator(worldName).copy(targetWorld));
           if (world == null) {
               controller.getLogger().warning("Failed to create world: " + worldName);
           } else if (appearanceEnvironment != null) {
               NMSUtils.setEnvironment(world, appearanceEnvironment);
               controller.info("Changed " + worldName + " appearance to " + appearanceEnvironment);
           }
        }
    }

    public void playerEntered(Player player) {
        if (resourcePack != null) {
            player.setResourcePack(resourcePack);
        }
    }

    public void updateTime() {
        updateTimeFrom(null, 0);
    }

    public boolean updateTimeFrom(World changedWorld, long skipAmount) {
        if (!synchronizeTime || copyFrom.isEmpty() || state != WorldState.LOADED) {
            return true;
        }
        if (changedWorld != null && worldName.equals(changedWorld.getName())) {
            return false;
        }
        if (changedWorld == null) {
            changedWorld = Bukkit.getWorld(copyFrom);
        } else if (!changedWorld.getName().equals(copyFrom)) {
            return true;
        }
        if (changedWorld == null) {
            return true;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            world.setTime(changedWorld.getTime() + synchronizedTimeOffset + skipAmount);
        }
        return true;
    }
}
