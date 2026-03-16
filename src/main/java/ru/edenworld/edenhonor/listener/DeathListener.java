package ru.edenworld.edenhonor.listener;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import ru.edenworld.edenhonor.EdenHonorPlugin;
import ru.edenworld.edenhonor.model.KillReason;
import ru.edenworld.edenhonor.service.HonorService;

public final class DeathListener implements Listener {

    private final EdenHonorPlugin plugin;
    private final HonorService honorService;

    public DeathListener(EdenHonorPlugin plugin, HonorService honorService) {
        this.plugin = plugin;
        this.honorService = honorService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (!honorService.isWorldTracked(victim.getWorld().getName())) {
            return;
        }

        Player killer = victim.getKiller();
        if (killer == null) {
            UUID attackerId = honorService.getRecentAggressor(victim.getUniqueId());
            if (attackerId != null) {
                killer = Bukkit.getPlayer(attackerId);
            }
        }

        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        boolean victimWasRed = honorService.isRed(victim.getUniqueId());
        honorService.recordKill(
                killer.getUniqueId(),
                victim.getUniqueId(),
                victimWasRed ? KillReason.RETALIATION : KillReason.CRIMINAL
        );
    }
}
