package com.swevmc.island;

import com.swevmc.BeachIslands;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.File;
import java.io.IOException;
import java.util.*;

import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.flags.Flags;

public class IslandManager {
    private final BeachIslands plugin;
    private final Map<UUID, IslandData> playerIslands = new HashMap<>();
    private final List<IslandType> islandTypes = new ArrayList<>();
    private final File dataFile;
    private YamlConfiguration dataConfig;
    private final Map<UUID, BukkitRunnable> titleTasks = new HashMap<>();
    private final Map<UUID, Set<Location>> borderBlocksShown = new HashMap<>();
    private final Map<String, Clipboard> schematicCache = new ConcurrentHashMap<>();
    private final ExecutorService generationExecutor;
    private final Set<String> occupiedCoordinates = ConcurrentHashMap.newKeySet();
    private final AtomicInteger spiralIndex = new AtomicInteger(0);
    private static final String THEME = "§x§F§D§F§9§C§3";

    public IslandManager(BeachIslands plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        int threads = plugin.getConfig().getInt("generation-threads", 2);
        this.generationExecutor = Executors.newFixedThreadPool(Math.max(1, threads));
        loadIslandTypes();
        loadData();
        startBorderDisplayTask();
    }

    private void loadIslandTypes() {
        for (var entry : plugin.getConfig().getMapList("islands")) {
            String id = entry.get("id").toString();
            String name = entry.get("name").toString();
            String schematic = entry.get("schematic").toString();
            islandTypes.add(new IslandType(id, name, schematic));
        }
    }

    private String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("prefix", "&b[BeachIslands]&r "));
    }

    public void send(Player player, String msg) {
        player.sendMessage(getPrefix() + msg);
    }

    private int[] getNextAvailableCoords(int minDist) {
        int index = spiralIndex.getAndIncrement();
        while (true) {
            int[] coords = getSpiralCoords(index, minDist);
            String key = coords[0] + "," + coords[1];
            if (occupiedCoordinates.add(key)) {
                return coords;
            }
            index = spiralIndex.getAndIncrement();
        }
    }

    private int[] getSpiralCoords(int n, int spacing) {
        if (n == 0) return new int[]{0, 0};

        int ring = (int) Math.ceil((Math.sqrt(n) - 1) / 2);
        int ringSideLength = 2 * ring;
        int ringStartIndex = (2 * ring - 1) * (2 * ring - 1);
        int posInRing = n - ringStartIndex;

        int side = posInRing / ringSideLength;
        int posOnSide = posInRing % ringSideLength;

        int x, z;
        switch (side) {
            case 0:
                x = ring;
                z = -ring + posOnSide;
                break;
            case 1:
                x = ring - posOnSide;
                z = ring;
                break;
            case 2:
                x = -ring;
                z = ring - posOnSide;
                break;
            default:
                x = -ring + posOnSide;
                z = -ring;
                break;
        }

        return new int[]{x * spacing, z * spacing};
    }

    private void showPersistentTitle(Player player, String main, String sub) {
        cancelPersistentTitle(player);
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', main),
                        ChatColor.translateAlternateColorCodes('&', sub),
                        0, 40, 0
                );
            }
        };
        task.runTaskTimer(plugin, 0, 20);
        titleTasks.put(player.getUniqueId(), task);
    }

    private void cancelPersistentTitle(Player player) {
        BukkitRunnable task = titleTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        player.resetTitle();
    }

    private Clipboard loadSchematic(File file) throws Exception {
        String key = file.getName();
        Clipboard cached = schematicCache.get(key);
        if (cached != null) return cached;
        Clipboard clipboard = ClipboardFormats.findByFile(file)
                .getReader(new java.io.FileInputStream(file))
                .read();
        schematicCache.put(key, clipboard);
        return clipboard;
    }

    private CompletableFuture<Void> preloadChunks(org.bukkit.World world, int centerX, int centerZ, int radiusBlocks) {
        List<CompletableFuture<org.bukkit.Chunk>> futures = new ArrayList<>();

        int chunkRadius = (radiusBlocks / 16) + 1;
        int centerChunkX = centerX >> 4;
        int centerChunkZ = centerZ >> 4;

        for (int ring = 0; ring <= chunkRadius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) continue;

                    int chunkX = centerChunkX + dx;
                    int chunkZ = centerChunkZ + dz;

                    futures.add(world.getChunkAtAsync(chunkX, chunkZ));
                }
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private BlockVector3 findGoldBlockInClipboard(Clipboard clipboard) {
        Region region = clipboard.getRegion();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockVector3 pos = BlockVector3.at(x, y, z);
                    if (clipboard.getBlock(pos).getBlockType() == BlockTypes.GOLD_BLOCK) {
                        return pos.subtract(clipboard.getOrigin());
                    }
                }
            }
        }
        return null;
    }

    public void createIsland(Player player, IslandType type) {
        org.bukkit.World bukkitWorld = Bukkit.getWorld("world");
        if (bukkitWorld == null) {
            send(player, "§cWorld not found (world). Contact admin.");
            return;
        }
        showPersistentTitle(player, THEME + "Creating Island...", "§7Preparing area..");
        generationExecutor.submit(() -> {
            File schematicFile = new File(plugin.getDataFolder(), "schematics/" + type.getSchematic());
            if (!schematicFile.exists()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    cancelPersistentTitle(player);
                    send(player, "§cSchematic file not found: " + type.getSchematic());
                });
                return;
            }
            Clipboard clipboard;
            try {
                clipboard = loadSchematic(schematicFile);
            } catch (Exception ex) {
                ex.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    cancelPersistentTitle(player);
                    send(player, "§cError loading schematic. Contact admin.");
                });
                return;
            }
            int width = clipboard.getRegion().getWidth();
            int length = clipboard.getRegion().getLength();
            int height = clipboard.getRegion().getHeight();

            BlockVector3 relativeSpawn = findGoldBlockInClipboard(clipboard);

            int[] coords = getNextAvailableCoords(750);
            int x = coords[0];
            int z = coords[1];
            final int surfaceY = 71;

            int chunkRadius = Math.max(width, length) / 2 + 16;
            preloadChunks(bukkitWorld, x, z, chunkRadius).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    showPersistentTitle(player, THEME + "Creating Island...", "§7Placing island...");

                    World weWorld = BukkitAdapter.adapt(bukkitWorld);
                    BlockVector3 pasteLocation = BlockVector3.at(x, surfaceY - 1, z);
                    try (EditSession editSession = com.sk89q.worldedit.WorldEdit.getInstance()
                            .newEditSessionBuilder()
                            .world(weWorld)
                            .maxBlocks(-1)
                            .fastMode(true)
                            .build()) {
                        clipboard.paste(weWorld, pasteLocation, false, false, null);
                        CuboidRegion pastedRegion = new CuboidRegion(
                                pasteLocation,
                                pasteLocation.add(width - 1, height - 1, length - 1)
                        );
                        relightRegion(editSession, pastedRegion);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        cancelPersistentTitle(player);
                        send(player, "§cError pasting schematic. Contact admin.");
                        return;
                    }

                    Location spawn;
                    if (relativeSpawn != null) {
                        int spawnX = x + relativeSpawn.getX();
                        int spawnY = surfaceY + relativeSpawn.getY() + 1;
                        int spawnZ = z + relativeSpawn.getZ();
                        spawn = new Location(bukkitWorld, spawnX + 0.5, spawnY, spawnZ + 0.5);
                    } else {
                        spawn = new Location(bukkitWorld, x + width / 2.0, surfaceY + 2, z + length / 2.0);
                    }

                    IslandData data = new IslandData(type.getId(), spawn.getBlockX(), spawn.getBlockZ());
                    data.setBorderLevel(0);
                    data.setSchematicFile(type.getSchematic());
                    data.setOriginX(x);
                    data.setOriginY(surfaceY);
                    data.setOriginZ(z);
                    data.setSizeX(width);
                    data.setSizeY(height);
                    data.setSizeZ(length);
                    setIsland(player.getUniqueId(), data);
                    saveAll();
                    updateIslandRegion(player, data);
                    player.teleport(spawn);
                    cancelPersistentTitle(player);
                    send(player, "§aIsland created!");
                });
            });
        });
    }

    public void setWorldBorder(Player player, int centerX, int centerZ, int size) {
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(centerX + 0.5, centerZ + 0.5);
        border.setSize(size);
        border.setWarningTime(2);
        border.setWarningDistance(2);
        player.setWorldBorder(border);
    }

    public void clearWorldBorder(Player player) {
        player.setWorldBorder(null);
    }

    private void relightRegion(EditSession editSession, CuboidRegion region) {
        try {
            Class<?> faweApi = Class.forName("com.fastasyncworldedit.core.FaweAPI");
            faweApi.getMethod("fixLighting", EditSession.class, Region.class)
                    .invoke(null, editSession, region);
        } catch (Throwable ignored) {
        }
    }

    public void deleteIsland(Player player) {
        IslandData data = getIsland(player.getUniqueId());
        if (data == null) {
            send(player, "§cYou do not have an island.");
            return;
        }
        String key = data.getOriginX() + "," + data.getOriginZ();
        occupiedCoordinates.remove(key);
        org.bukkit.World bukkitWorld = Bukkit.getWorld("world");
        if (bukkitWorld == null) {
            send(player, "§cWorld not found (world). Contact admin.");
            return;
        }
        Location spawn = bukkitWorld.getSpawnLocation();
        Bukkit.getScheduler().runTask(plugin, () -> player.teleport(spawn));
        Bukkit.getScheduler().runTask(plugin, () -> {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(bukkitWorld));
            String safeName = player.getName().toLowerCase().replaceAll("[^a-z0-9_]", "_");
            String regionId = "island_" + safeName;
            if (regionManager != null && regionManager.hasRegion(regionId)) {
                regionManager.removeRegion(regionId);
            }
        });
        removeVirtualGlassBorder(player);
        generationExecutor.submit(() -> {
            int minX = data.getOriginX();
            int minY = data.getOriginY();
            int minZ = data.getOriginZ();
            int maxX = minX + data.getSizeX() - 1;
            int maxY = minY + data.getSizeY() - 1;
            int maxZ = minZ + data.getSizeZ() - 1;
            World weWorld = BukkitAdapter.adapt(bukkitWorld);
            CuboidRegion region = new CuboidRegion(
                    BlockVector3.at(minX, minY, minZ),
                    BlockVector3.at(maxX, maxY, maxZ)
            );
            Bukkit.getScheduler().runTask(plugin, () -> {
                try (EditSession editSession = com.sk89q.worldedit.WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(weWorld)
                        .maxBlocks(-1)
                        .fastMode(true)
                        .build()) {
                    Pattern air = new BlockPattern(BlockTypes.AIR.getDefaultState());
                    editSession.setBlocks((Region) region, air);
                    relightRegion(editSession, region);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                setIsland(player.getUniqueId(), null);
                saveAll();
                clearWorldBorder(player);
                send(player, "§aYour island has been deleted!");
            });
        });
    }

    private void startBorderDisplayTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    IslandData data = getIsland(player.getUniqueId());
                    if (data == null) {
                        removeVirtualGlassBorder(player);
                        continue;
                    }
                    showVirtualGlassBorder(player, data);
                }
            }
        }.runTaskTimer(plugin, 7, 7);
    }

    private void showVirtualGlassBorder(Player player, IslandData data) {
        int border = getBorderSizes().get(
                Math.max(0, Math.min(getBorderSizes().size() - 1, data.getBorderLevel()))
        );
        int x = data.getX();
        int z = data.getZ();
        int half = border / 2;
        int minY = data.getOriginY();
        int maxY = minY + 7;
        Location pLoc = player.getLocation();
        BlockData glass = Bukkit.createBlockData(Material.YELLOW_STAINED_GLASS);

        Set<Location> nowShowing = new HashSet<>();

        for (int y = minY; y <= maxY; y++) {
            for (int dx = -half; dx <= half; dx++) {
                Location l1 = new Location(player.getWorld(), x + dx, y, z - half);
                Location l2 = new Location(player.getWorld(), x + dx, y, z + half);
                if (l1.distance(pLoc) <= 3) nowShowing.add(l1);
                if (l2.distance(pLoc) <= 3) nowShowing.add(l2);
            }
            for (int dz = -half + 1; dz < half; dz++) {
                Location l1 = new Location(player.getWorld(), x - half, y, z + dz);
                Location l2 = new Location(player.getWorld(), x + half, y, z + dz);
                if (l1.distance(pLoc) <= 3) nowShowing.add(l1);
                if (l2.distance(pLoc) <= 3) nowShowing.add(l2);
            }
        }

        Set<Location> previously = borderBlocksShown.getOrDefault(player.getUniqueId(), new HashSet<>());
        for (Location loc : previously) {
            if (!nowShowing.contains(loc)) {
                player.sendBlockChange(loc, loc.getBlock().getBlockData());
            }
        }
        for (Location loc : nowShowing) {
            if (!previously.contains(loc)) {
                player.sendBlockChange(loc, glass);
            }
        }
        borderBlocksShown.put(player.getUniqueId(), nowShowing);
    }

    public void removeVirtualGlassBorder(Player player) {
        Set<Location> shown = borderBlocksShown.remove(player.getUniqueId());
        if (shown == null) return;
        for (Location loc : shown) {
            player.sendBlockChange(loc, loc.getBlock().getBlockData());
        }
    }

    public void upgradeIslandBorder(Player player) {
        IslandData data = getIsland(player.getUniqueId());
        if (data == null) return;
        int current = data.getBorderLevel();
        List<Integer> sizes = getBorderSizes();
        if (current + 1 >= sizes.size()) {
            send(player, "§eYour border is already at max size.");
            return;
        }
        data.setBorderLevel(current + 1);
        saveAll();
        updateIslandRegion(player, data);
        setWorldBorder(player, data.getX(), data.getZ(), sizes.get(current + 1));
        send(player, "§aYour island border has been upgraded!");
    }

    private void loadData() {
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (dataConfig.contains("islands")) {
            for (String uuidStr : dataConfig.getConfigurationSection("islands").getKeys(false)) {
                String typeId = dataConfig.getString("islands." + uuidStr + ".type");
                int x = dataConfig.getInt("islands." + uuidStr + ".x");
                int z = dataConfig.getInt("islands." + uuidStr + ".z");
                IslandData data = new IslandData(typeId, x, z);
                List<Integer> upgrades = dataConfig.getIntegerList("islands." + uuidStr + ".upgrades");
                data.getUpgrades().addAll(upgrades);
                int borderLevel = dataConfig.getInt("islands." + uuidStr + ".borderLevel", 0);
                data.setBorderLevel(borderLevel);
                data.setSchematicFile(dataConfig.getString("islands." + uuidStr + ".schematic", ""));
                data.setOriginX(dataConfig.getInt("islands." + uuidStr + ".originX", x));
                data.setOriginY(dataConfig.getInt("islands." + uuidStr + ".originY", 71));
                data.setOriginZ(dataConfig.getInt("islands." + uuidStr + ".originZ", z));
                data.setSizeX(dataConfig.getInt("islands." + uuidStr + ".sizeX", 100));
                data.setSizeY(dataConfig.getInt("islands." + uuidStr + ".sizeY", 100));
                data.setSizeZ(dataConfig.getInt("islands." + uuidStr + ".sizeZ", 100));
                playerIslands.put(UUID.fromString(uuidStr), data);
            }
            occupiedCoordinates.clear();
            for (IslandData data : playerIslands.values()) {
                String key = data.getOriginX() + "," + data.getOriginZ();
                occupiedCoordinates.add(key);
            }
        }
    }

    public void saveAll() {
        for (var entry : playerIslands.entrySet()) {
            IslandData data = entry.getValue();
            String path = "islands." + entry.getKey();
            dataConfig.set(path + ".type", data.getTypeId());
            dataConfig.set(path + ".x", data.getX());
            dataConfig.set(path + ".z", data.getZ());
            dataConfig.set(path + ".upgrades", new ArrayList<>(data.getUpgrades()));
            dataConfig.set(path + ".borderLevel", data.getBorderLevel());
            dataConfig.set(path + ".schematic", data.getSchematicFile());
            dataConfig.set(path + ".originX", data.getOriginX());
            dataConfig.set(path + ".originY", data.getOriginY());
            dataConfig.set(path + ".originZ", data.getOriginZ());
            dataConfig.set(path + ".sizeX", data.getSizeX());
            dataConfig.set(path + ".sizeY", data.getSizeY());
            dataConfig.set(path + ".sizeZ", data.getSizeZ());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Integer> getBorderSizes() {
        return plugin.getConfig().getIntegerList("border-upgrades.sizes");
    }

    public List<Integer> getBorderCosts() {
        return plugin.getConfig().getIntegerList("border-upgrades.costs");
    }

    public void updateIslandRegion(Player player, IslandData data) {
        org.bukkit.World bukkitWorld = Bukkit.getWorld("world");
        if (bukkitWorld == null) return;
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(bukkitWorld));
        if (regionManager == null) return;
        String safeName = player.getName().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String regionId = "island_" + safeName;
        if (regionManager.hasRegion(regionId)) {
            regionManager.removeRegion(regionId);
        }
        int border = getBorderSizes().get(
                Math.max(0, Math.min(getBorderSizes().size() - 1, data.getBorderLevel()))
        );
        int x = data.getX();
        int z = data.getZ();
        int half = border / 2;
        BlockVector3 min = BlockVector3.at(x - half, 0, z - half);
        BlockVector3 max = BlockVector3.at(x + half, 255, z + half);
        ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);
        region.getOwners().addPlayer(player.getUniqueId());
        region.setFlag(Flags.BLOCK_BREAK, com.sk89q.worldguard.protection.flags.StateFlag.State.ALLOW);
        region.setFlag(Flags.BLOCK_PLACE, com.sk89q.worldguard.protection.flags.StateFlag.State.ALLOW);
        region.setFlag(Flags.PVP, com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
        regionManager.addRegion(region);
        setWorldBorder(player, x, z, border);
    }

    public IslandData getIsland(UUID uuid) {
        return playerIslands.get(uuid);
    }

    public void setIsland(UUID uuid, IslandData data) {
        if (data == null) {
            playerIslands.remove(uuid);
        } else {
            playerIslands.put(uuid, data);
        }
    }

    public List<IslandType> getIslandTypes() {
        return islandTypes;
    }

    public BeachIslands getPlugin() {
        return plugin;
    }

    public void shutdown() {
        generationExecutor.shutdownNow();
    }
}
