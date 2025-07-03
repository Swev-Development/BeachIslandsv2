package com.swevmc.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

public class IslandRegionListener implements Listener {
    private final IslandManager manager;

    public IslandRegionListener(IslandManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        IslandData data = manager.getIsland(player.getUniqueId());
        if (data == null) return;

        int border = manager.getBorderSizes().get(
                Math.max(0, Math.min(manager.getBorderSizes().size() - 1, data.getBorderLevel()))
        );
        int x = data.getX();
        int z = data.getZ();
        int half = border / 2;

        Location to = event.getTo();
        if (to == null) return;

        double px = to.getX(), pz = to.getZ();
        boolean outX = px < (x - half) || px > (x + half);
        boolean outZ = pz < (z - half) || pz > (z + half);

        if (outX || outZ) {
            // Calculate push direction back to center
            Location center = new Location(player.getWorld(), x + 0.5, to.getY(), z + 0.5);
            Vector pushDir = center.toVector().subtract(to.toVector()).normalize().multiply(1.5); // 1.5 block push
            player.setVelocity(pushDir);

            // Optional: Small teleport to prevent stuck
            Location inside = to.clone();
            if (outX) inside.setX(Math.max(x - half + 0.5, Math.min(x + half - 0.5, px)));
            if (outZ) inside.setZ(Math.max(z - half + 0.5, Math.min(z + half - 0.5, pz)));
            player.teleport(inside);

            player.sendActionBar("§cYou can't leave your island!");
        }
    }
}
