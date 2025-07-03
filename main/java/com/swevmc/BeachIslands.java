package com.swevmc;

import com.swevmc.commands.IslandCommand;
import com.swevmc.commands.ShoreSupplyCommand;
import com.swevmc.event.ShoreSupplyEvent;
import com.swevmc.island.IslandManager;
import com.swevmc.island.IslandRegionListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class BeachIslands extends JavaPlugin {
    private static BeachIslands instance;
    private IslandManager islandManager;
    private ShoreSupplyEvent shoreSupplyEvent;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getDataFolder().mkdirs();
        File schemFolder = new File(getDataFolder(), "schematics");
        if (!schemFolder.exists()) schemFolder.mkdirs();

        // Init islandManager first!
        islandManager = new IslandManager(this);

        // Register listeners that need the manager AFTER manager is created
        Bukkit.getPluginManager().registerEvents(new IslandRegionListener(islandManager), this);

        if (getCommand("island") != null) {
            IslandCommand cmd = new IslandCommand(islandManager);
            getCommand("island").setExecutor(cmd);
            getCommand("island").setTabCompleter(cmd);
        } else {
            getLogger().warning("Command /island not defined in plugin.yml!");
        }

        shoreSupplyEvent = new ShoreSupplyEvent(this);
        if (getCommand("shorestart") != null) {
            getCommand("shorestart").setExecutor(new ShoreSupplyCommand(this, shoreSupplyEvent));
        } else {
            getLogger().warning("Command /shorestart not defined in plugin.yml!");
        }

        getLogger().info("\n" +
                "          ___   ____\n" +
                "        /' --;^/ ,-_\\     \\ | /\n" +
                "       / / --o\\ o-\\ \\\\   --(_)--\n" +
                "      /-/-/|o|-|\\-\\\\|\\\\   / | \\\n" +
                "       '`  ` |-|   `` '\n" +
                "             |-|\n" +
                "             |-|O\n" +
                "             |-(\\,__\n" +
                "          ...|-|\\--,\\_....\n" +
                "      ,;;;;;;;;;;;;;;;;;;;;;;;;,.\n" +
                "~~,;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;,~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "~;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;,  ______   ---------   _____     ------\n"
        );
        getLogger().info("MADE AND MAINTAINED BY SWEVMC");
        getLogger().info("BeachIslands enabled. Place 3 schematic files in /plugins/beachislands/schematics/ and configure config.yml.");
    }

    @Override
    public void onDisable() {
        if (islandManager != null) {
            islandManager.saveAll();
            islandManager.shutdown();
        }
        if (shoreSupplyEvent != null && shoreSupplyEvent.isRunning()) {
            shoreSupplyEvent.stopEvent();
        }
    }

    public static BeachIslands getInstance() {
        return instance;
    }

    public IslandManager getIslandManager() {
        return islandManager;
    }

    public String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix", "&b[BeachIslands]&r "));
    }

    public ShoreSupplyEvent getShoreSupplyEvent() {
        return shoreSupplyEvent;
    }
}
