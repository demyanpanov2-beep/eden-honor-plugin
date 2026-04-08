package ru.edenworld.edenhonor.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import ru.edenworld.edenhonor.EdenHonorPlugin;
import ru.edenworld.edenhonor.menu.HonorStatsMenu;
import ru.edenworld.edenhonor.service.HonorService;

public final class HonorMenuListener implements Listener {

    private final EdenHonorPlugin plugin;
    private final HonorService honorService;

    public HonorMenuListener(EdenHonorPlugin plugin, HonorService honorService) {
        this.plugin = plugin;
        this.honorService = honorService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HonorStatsMenu menu)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 45 && menu.getPage() > 0) {
            player.openInventory(new HonorStatsMenu(plugin, honorService, menu.getPage() - 1).getInventory());
        } else if (slot == 53) {
            player.openInventory(new HonorStatsMenu(plugin, honorService, menu.getPage() + 1).getInventory());
        }
    }
}
