package com.swevmc.commands;

import com.swevmc.gui.IslandSelectionGUI;
import com.swevmc.gui.IslandUpgradeGUI;
import com.swevmc.gui.IslandDeleteGUI;
import com.swevmc.island.IslandManager;
import com.swevmc.island.IslandData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class IslandCommand implements CommandExecutor, TabCompleter {
    private final IslandManager manager;

    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "upgrades", "delete");

    public IslandCommand(IslandManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[BeachIslands] Players only."); // Console fallback
            return true;
        }

        // /island create (or no args)
        if (args.length == 0 || args[0].equalsIgnoreCase("create")) {
            IslandData data = manager.getIsland(player.getUniqueId());
            if (data != null) {
                // Player already has an island, open upgrade GUI
                new IslandUpgradeGUI(manager, player, data).open();
            } else {
                new IslandSelectionGUI(manager, player).open();
            }
            return true;
        }

        // /island upgrades
        if (args[0].equalsIgnoreCase("upgrades")) {
            IslandData data = manager.getIsland(player.getUniqueId());
            if (data == null) {
                manager.send(player, "§cYou don't have an island. Use /island create.");
                return true;
            }
            new IslandUpgradeGUI(manager, player, data).open();
            return true;
        }

        // /island delete
        if (args[0].equalsIgnoreCase("delete")) {
            IslandData data = manager.getIsland(player.getUniqueId());
            if (data == null) {
                manager.send(player, "§cYou don't have an island to delete!");
                return true;
            }
            new com.swevmc.gui.IslandDeleteGUI(manager, player, data).open();
            return true;
        }

        // Show help
        manager.send(player,
                "§e/island create §7- Create your island or open upgrades\n" +
                        "§e/island upgrades §7- Open upgrades GUI\n" +
                        "§e/island delete §7- Delete your island"
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
