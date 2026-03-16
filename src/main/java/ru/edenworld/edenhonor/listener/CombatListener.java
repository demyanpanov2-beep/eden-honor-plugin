package ru.edenworld.edenhonor.listener;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import ru.edenworld.edenhonor.EdenHonorPlugin;
import ru.edenworld.edenhonor.service.HonorService;

public final class CombatListener implements Listener {

    private final EdenHonorPlugin plugin;
    private final HonorService honorService;

    public CombatListener(EdenHonorPlugin plugin, HonorService honorService) {
        this.plugin = plugin;
        this.honorService = honorService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!honorService.isWorldTracked(victim.getWorld().getName())) {
            return;
        }

        Player attacker = findAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0D) {
            return;
        }

        honorService.recordDamage(attacker.getUniqueId(), victim.getUniqueId(), finalDamage);
        honorService.rememberAggressor(victim.getUniqueId(), attacker.getUniqueId());
    }

    private Player findAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) {
            return player;
        }
        return null;
    }
}
