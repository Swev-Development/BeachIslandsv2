package com.swevmc.commands;

import com.swevmc.event.ShoreSupplyEvent;
import com.swevmc.BeachIslands;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class ShoreSupplyCommand implements CommandExecutor {
    private final BeachIslands plugin;
    private final ShoreSupplyEvent event;

    public ShoreSupplyCommand(BeachIslands plugin, ShoreSupplyEvent event) {
        this.plugin = plugin;
        this.event = event;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("beachislands.supplyevent")) {
            sender.sendMessage("§cYou do not have permission.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("start")) {
            if (event.isRunning()) {
                sender.sendMessage("§eEvent already running.");
                return true;
            }
            event.startEvent();
            sender.sendMessage("§aShore Supply event started!");
            return true;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            if (!event.isRunning()) {
                sender.sendMessage("§eNo event running.");
                return true;
            }
            event.stopEvent();
            sender.sendMessage("§aShore Supply event stopped.");
            return true;
        }
        sender.sendMessage("§e/shorestart [start|stop]");
        return true;
    }
}
