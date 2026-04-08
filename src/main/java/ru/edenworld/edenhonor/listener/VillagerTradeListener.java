package ru.edenworld.edenhonor.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import ru.edenworld.edenhonor.EdenHonorPlugin;
import ru.edenworld.edenhonor.model.HonorStatus;
import ru.edenworld.edenhonor.service.HonorService;
import ru.edenworld.edenhonor.util.TextUtil;

public final class VillagerTradeListener implements Listener {

    private final EdenHonorPlugin plugin;
    private final HonorService honorService;

    public VillagerTradeListener(EdenHonorPlugin plugin, HonorService honorService) {
        this.plugin = plugin;
        this.honorService = honorService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractVillager villager)) {
            return;
        }

        Player player = event.getPlayer();
        if (!honorService.isWorldTracked(player.getWorld().getName())) {
            return;
        }

        HonorStatus status = honorService.getStatus(player.getUniqueId());
        if (status == HonorStatus.RED || status == HonorStatus.BLACK) {
            event.setCancelled(true);
            player.sendMessage(TextUtil.colorize(plugin.getConfig().getString(
                    "messages.trade-blocked",
                    "&cЖители отказываются торговать с игроками с красной и чёрной меткой."
            )));
            return;
        }

        if (status != HonorStatus.YELLOW) {
            return;
        }

        event.setCancelled(true);
        Merchant merchant = Bukkit.createMerchant(villager.getName());
        merchant.setRecipes(villager.getRecipes().stream()
                .map(this::cloneWithoutDiscounts)
                .toList());

        Bukkit.getScheduler().runTask(plugin, () -> player.openMerchant(merchant, true));
    }

    private MerchantRecipe cloneWithoutDiscounts(MerchantRecipe recipe) {
        MerchantRecipe clone = new MerchantRecipe(
                recipe.getResult().clone(),
                recipe.getUses(),
                recipe.getMaxUses(),
                recipe.hasExperienceReward(),
                recipe.getVillagerExperience(),
                recipe.getPriceMultiplier()
        );
        clone.setDemand(recipe.getDemand());
        clone.setIngredients(recipe.getIngredients().stream()
                .map(ItemStack::clone)
                .toList());
        clone.setSpecialPrice(Math.max(0, recipe.getSpecialPrice()));
        return clone;
    }
}
