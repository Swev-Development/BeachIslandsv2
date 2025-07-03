package com.swevmc.gui;

import com.swevmc.island.IslandManager;
import com.swevmc.island.IslandType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class IslandSelectionGUI {
    private final IslandManager manager;
    private final Player player;

    public IslandSelectionGUI(IslandManager manager, Player player) {
        this.manager = manager;
        this.player = player;
    }

    public void open() {
        List<IslandType> types = manager.getIslandTypes();
        Inventory inv = Bukkit.createInventory(null, 9, "Select Your Island");

        // Map type indices to slot numbers
        int[] slots = {2, 4, 6}; // Up to 3 island types; extend as needed
        for (int i = 0; i < types.size() && i < slots.length; i++) {
            IslandType t = types.get(i);
            ItemStack item = new ItemStack(Material.GRASS_BLOCK); // Replace with actual type
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§a" + t.getName());
            meta.setLore(List.of("§7Click to choose!"));
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }

        player.openInventory(inv);

        Bukkit.getPluginManager().registerEvents(new Listener() {
            final AtomicBoolean handled = new AtomicBoolean(false); // Guards against double-create

            @EventHandler
            public void onInventoryClick(InventoryClickEvent e) {
                if (!e.getView().getTitle().equals("Select Your Island")) return;
                if (!e.getWhoClicked().equals(player)) return;
                e.setCancelled(true);

                int slot = e.getRawSlot();
                int idx = -1;
                for (int i = 0; i < slots.length; i++) {
                    if (slots[i] == slot) {
                        idx = i;
                        break;
                    }
                }
                if (idx == -1 || idx >= types.size()) return; // Not a valid island button

                // Prevent double creation
                if (handled.getAndSet(true)) return;

                HandlerList.unregisterAll(this); // Unregister first!
                e.getWhoClicked().closeInventory();

                IslandType chosen = types.get(idx);
                Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
                    manager.createIsland(player, chosen);
                });
            }

            @EventHandler
            public void onInventoryClose(InventoryCloseEvent e) {
                if (!e.getPlayer().equals(player)) return;
                if (!e.getView().getTitle().equals("Select Your Island")) return;
                HandlerList.unregisterAll(this);
            }
        }, manager.getPlugin());
    }
}
