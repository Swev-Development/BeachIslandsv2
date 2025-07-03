package com.swevmc.event;

import com.swevmc.BeachIslands;
import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.*;

public class ShoreSupplyEvent {
    private final BeachIslands plugin;
    private boolean running = false;
    private final Set<Location> crateLocations = new HashSet<>();
    private final Map<Location, ArmorStand> holograms = new HashMap<>();
    private BukkitRunnable timerTask = null;

    private static final String THEME = "§x§F§D§F§9§C§3";

    public ShoreSupplyEvent(BeachIslands plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(new BarrelPoofListener(), plugin);
    }

    public boolean isRunning() {
        return running;
    }

    public void startEvent() {
        if (running) return;
        running = true;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), "minecraft:event.raid.horn", SoundCategory.MASTER, 1.4f, 1.0f);
        }

        String prefix = plugin.getPrefix();
        Bukkit.broadcastMessage(prefix + THEME + " Barrels are falling on every island! Find them for random loot!");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(THEME + "Shore Supply active! Look for falling barrels!");
        }

        crateLocations.clear();
        holograms.clear();

        World bukkitWorld = Bukkit.getWorld("world");
        if (bukkitWorld == null) return;

        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(bukkitWorld));
        if (regionManager == null) return;

        List<ProtectedRegion> islandRegions = new ArrayList<>();
        for (ProtectedRegion reg : regionManager.getRegions().values()) {
            if (reg.getId().startsWith("island_")) {
                islandRegions.add(reg);
            }
        }

        List<Runnable> allDrops = new ArrayList<>();
        Random rng = new Random();

        for (ProtectedRegion reg : islandRegions) {
            int tries = 0, drops = 0;
            while (drops < 6 && tries < 30) {
                tries++;
                int minX = reg.getMinimumPoint().getX() + 3;
                int minZ = reg.getMinimumPoint().getZ() + 3;
                int maxX = reg.getMaximumPoint().getX() - 3;
                int maxZ = reg.getMaximumPoint().getZ() - 3;
                if (maxX <= minX || maxZ <= minZ) continue;
                int x = minX + rng.nextInt(maxX - minX);
                int z = minZ + rng.nextInt(maxZ - minZ);

                int y = 150;
                boolean found = false;
                for (; y >= 60; y--) {
                    Material m = bukkitWorld.getBlockAt(x, y, z).getType();
                    if (m != Material.AIR && m != Material.WATER && m != Material.KELP && m != Material.SEAGRASS && m != Material.TALL_SEAGRASS) {
                        found = true;
                        break;
                    }
                }
                if (!found) continue;

                int barrelY = y + 1;
                Location landLoc = new Location(bukkitWorld, x, barrelY, z);
                if (crateLocations.contains(landLoc)) continue;

                allDrops.add(() -> dropBarrelWithHologram(bukkitWorld, x, barrelY, z));
                drops++;
            }
        }

        Collections.shuffle(allDrops);

        int delay = 0;
        for (Runnable drop : allDrops) {
            Bukkit.getScheduler().runTaskLater(plugin, drop, delay);
            delay += 20 + rng.nextInt(20);
        }

        timerTask = new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!running) {
                    this.cancel();
                    return;
                }
                t++;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendActionBar(THEME + "Shore Supply active! Find the barrels! [" + (20 - t) + "s]");
                }
                if (t >= 20) {
                    stopEvent();
                    this.cancel();
                }
            }
        };
        timerTask.runTaskTimer(plugin, 0, 20);
    }

    public void stopEvent() {
        if (!running) return;
        running = false;
        String prefix = plugin.getPrefix();
        Bukkit.broadcastMessage(prefix + THEME + " The event has ended! Barrels remain until opened.");
        for (Player p : Bukkit.getOnlinePlayers()) p.sendActionBar("");
        if (timerTask != null) timerTask.cancel();
    }

    private void dropBarrelWithHologram(World world, int x, int y, int z) {
        Location landLoc = new Location(world, x, y, z);
        crateLocations.add(landLoc);

        Location start = landLoc.clone().add(0.5, 13, 0.5);
        double totalTicks = 36;
        double stepY = (start.getY() - (y + 0.5)) / totalTicks;

        ArmorStand as = world.spawn(start, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(false);
            stand.getEquipment().setHelmet(new ItemStack(Material.BARREL));
            stand.setInvulnerable(true);
        });

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (as.isDead() || !as.isValid()) {
                    this.cancel();
                    return;
                }
                double ny = start.getY() - (stepY * ticks);
                as.teleport(new Location(world, start.getX(), ny, start.getZ(), 0, 0));
                ticks++;
                if (ny <= y + 0.5 || ticks >= totalTicks) {
                    as.remove();
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(Material.BARREL);
                    Barrel barrel = (Barrel) block.getState();
                    addRandomLootToBarrel(barrel.getInventory());
                    world.spawnParticle(Particle.LARGE_SMOKE, block.getLocation().clone().add(0.5, 1, 0.5), 32, 0.5, 0.3, 0.5, 0.08);
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, block.getLocation().clone().add(0.5, 1, 0.5), 16, 0.3, 0.15, 0.3, 0.05);
                    world.playSound(block.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.9F, 1.1F);
                    ArmorStand holo = world.spawn(block.getLocation().clone().add(0.5, 1.1, 0.5), ArmorStand.class, stand -> {
                        stand.setVisible(false);
                        stand.setMarker(true);
                        stand.setSmall(true);
                        stand.setGravity(false);
                        stand.setInvulnerable(true);
                        stand.setCustomName(THEME + "Shore Supply Crate!");
                        stand.setCustomNameVisible(true);
                    });
                    holograms.put(landLoc, holo);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 2, 2);
    }

    private void addRandomLootToBarrel(org.bukkit.inventory.Inventory inv) {
        List<ItemStack> loot = getRandomLoot();
        List<Integer> availableSlots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) availableSlots.add(i);
        Collections.shuffle(availableSlots);

        int slotIdx = 0;
        for (ItemStack item : loot) {
            if (slotIdx >= availableSlots.size()) break;
            int slot = availableSlots.get(slotIdx++);
            inv.setItem(slot, item);
        }
    }

    private List<ItemStack> getRandomLoot() {
        FileConfiguration config = plugin.getConfig();
        List<String> lootList = config.getStringList("shore-supply-loot");
        List<ItemStack> result = new ArrayList<>();
        Random rng = new Random();

        for (String entry : lootList) {
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;
            Material mat = Material.getMaterial(parts[0].toUpperCase());
            int amt = 1;
            try { amt = Integer.parseInt(parts[1]); } catch (Exception ignore) {}
            if (mat != null && amt > 0) {
                if (rng.nextBoolean()) {
                    result.add(new ItemStack(mat, amt));
                }
            }
        }
        if (result.isEmpty() && !lootList.isEmpty()) {
            String[] parts = lootList.get(0).split(":");
            if (parts.length == 2) {
                Material mat = Material.getMaterial(parts[0].toUpperCase());
                int amt = 1;
                try { amt = Integer.parseInt(parts[1]); } catch (Exception ignore) {}
                if (mat != null && amt > 0) result.add(new ItemStack(mat, amt));
            }
        }
        return result;
    }

    private class BarrelPoofListener implements Listener {
        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            Location invLoc = event.getInventory().getLocation();
            if (invLoc == null) return;
            if (!crateLocations.contains(invLoc)) return;
            Block block = invLoc.getBlock();
            if (block.getType() == Material.BARREL) {
                block.getWorld().spawnParticle(Particle.CLOUD, invLoc.clone().add(0.5, 1.0, 0.5), 32, 0.3, 0.2, 0.3, 0.02);
                block.getWorld().playSound(invLoc, Sound.ENTITY_ITEM_PICKUP, 0.7F, 1.2F);
                block.setType(Material.AIR);
                ArmorStand holo = holograms.remove(invLoc);
                if (holo != null && !holo.isDead()) holo.remove();
                crateLocations.remove(invLoc);
            }
        }
    }
}
