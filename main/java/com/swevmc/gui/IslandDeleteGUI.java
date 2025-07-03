package com.swevmc.gui;

import com.swevmc.island.IslandData;
import com.swevmc.island.IslandManager;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Location;

import java.util.Arrays;

public class IslandDeleteGUI {
    private final IslandManager manager;
    private final Player player;
    private final IslandData data;

    public IslandDeleteGUI(IslandManager manager, Player player, IslandData data) {
        this.manager = manager;
        this.player = player;
        this.data = data;
    }

    public void open() {
        Inventory inv = Bukkit.createInventory(null, 9, "Delete Island?");

        ItemStack grayGlass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta grayMeta = grayGlass.getItemMeta();
        grayMeta.setDisplayName(" ");
        grayGlass.setItemMeta(grayMeta);
        for (int i = 0; i < 9; i++) inv.setItem(i, grayGlass);

        ItemStack cancel = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName("§cCancel");
        cancel.setItemMeta(cancelMeta);
        inv.setItem(3, cancel);

        ItemStack delete = new ItemStack(Material.RED_WOOL);
        ItemMeta deleteMeta = delete.getItemMeta();
        deleteMeta.setDisplayName("§c§lDELETE ISLAND");
        deleteMeta.setLore(Arrays.asList("§7This action is §4IRREVERSIBLE§7!", "§7You will be teleported to spawn."));
        delete.setItemMeta(deleteMeta);
        inv.setItem(5, delete);

        player.openInventory(inv);

        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
                if (e.getView().getTitle().equals("Delete Island?") && e.getWhoClicked().equals(player)) {
                    e.setCancelled(true);
                    int slot = e.getRawSlot();
                    if (slot == 5) {
                        manager.deleteIsland(player);

                        org.bukkit.World bukkitWorld = Bukkit.getWorld("beachislands_ocean");
                        if (bukkitWorld != null) {
                            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(bukkitWorld));
                            String regionId = "island_" + player.getUniqueId().toString().replace("-", "");
                            if (regionManager != null && regionManager.hasRegion(regionId)) {
                                regionManager.removeRegion(regionId);
                            }
                        }

                        player.closeInventory();
                        manager.send(player, "§cYour island has been deleted!");
                        Location spawn = player.getWorld().getSpawnLocation();
                        player.teleport(spawn);

                        org.bukkit.event.HandlerList.unregisterAll(this);
                    } else if (slot == 3) {
                        player.closeInventory();
                        manager.send(player, "§eIsland delete cancelled.");
                        org.bukkit.event.HandlerList.unregisterAll(this);
                    }
                }
            }

            @org.bukkit.event.EventHandler
            public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
                if (e.getPlayer().equals(player) && e.getView().getTitle().equals("Delete Island?")) {
                    org.bukkit.event.HandlerList.unregisterAll(this);
                }
            }
        }, manager.getPlugin());
    }
}
