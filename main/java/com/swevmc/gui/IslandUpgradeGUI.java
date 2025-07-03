package com.swevmc.gui;

import com.swevmc.island.IslandData;
import com.swevmc.island.IslandManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class IslandUpgradeGUI {
    private final IslandManager manager;
    private final Player player;
    private final IslandData data;

    private static Economy econ = null;
    private void setupEconomy() {
        if (econ != null) return;
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) return;
        org.bukkit.plugin.RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
    }

    public IslandUpgradeGUI(IslandManager manager, Player player, IslandData data) {
        this.manager = manager;
        this.player = player;
        this.data = data;
    }

    public void open() {
        setupEconomy();

        List<Integer> borderSizes = manager.getBorderSizes();
        List<Integer> borderCosts = manager.getBorderCosts();
        int currentLevel = data.getBorderLevel();

        Inventory inv = Bukkit.createInventory(null, 9, "Island Border Upgrades");

        for (int i = 0; i < borderSizes.size(); i++) {
            Material mat;
            String name;
            List<String> lore = new java.util.ArrayList<>();

            if (i < currentLevel) {
                mat = Material.EMERALD_BLOCK;
                name = "§aBorder Level " + i + " (" + borderSizes.get(i) + "x" + borderSizes.get(i) + ") §7[Purchased]";
            } else if (i == currentLevel) {
                mat = Material.GOLD_BLOCK;
                name = "§eUpgrade: Border " + (borderSizes.get(i)) + "x" + (borderSizes.get(i));
                lore.add("§7Cost: §6$" + borderCosts.get(i));
                lore.add("§aClick to purchase!");
            } else {
                mat = Material.REDSTONE_BLOCK;
                name = "§cLocked: Border " + (borderSizes.get(i)) + "x" + (borderSizes.get(i));
                lore.add("§7Purchase previous upgrades to unlock.");
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        player.openInventory(inv);

        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
                if (e.getView().getTitle().equals("Island Border Upgrades") && e.getWhoClicked().equals(player)) {
                    e.setCancelled(true);
                    int slot = e.getRawSlot();
                    if (slot == currentLevel && slot < borderSizes.size()) {
                        if (econ == null) {
                            manager.send(player, "§cEconomy not setup! Contact admin.");
                            player.closeInventory();
                            org.bukkit.event.HandlerList.unregisterAll(this);
                            return;
                        }
                        double cost = borderCosts.get(slot);
                        if (econ.getBalance(player) < cost) {
                            manager.send(player, "§cYou need $" + cost + " to purchase this upgrade!");
                            return;
                        }
                        EconomyResponse resp = econ.withdrawPlayer(player, cost);
                        if (!resp.transactionSuccess()) {
                            manager.send(player, "§cPayment failed: " + resp.errorMessage);
                            return;
                        }
                        data.setBorderLevel(currentLevel + 1);
                        manager.updateIslandRegion(player, data);
                        manager.saveAll();
                        manager.send(player, "§aBorder upgraded to §e" + borderSizes.get(currentLevel + 1) + "x" + borderSizes.get(currentLevel + 1) + "§a!");
                        player.closeInventory();
                        Bukkit.getScheduler().runTaskLater(manager.getPlugin(), () ->
                                new IslandUpgradeGUI(manager, player, data).open(), 2L);
                        org.bukkit.event.HandlerList.unregisterAll(this);
                    }
                }
            }

            @org.bukkit.event.EventHandler
            public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
                if (e.getPlayer().equals(player) && e.getView().getTitle().equals("Island Border Upgrades")) {
                    org.bukkit.event.HandlerList.unregisterAll(this);
                }
            }
        }, manager.getPlugin());
    }
}
