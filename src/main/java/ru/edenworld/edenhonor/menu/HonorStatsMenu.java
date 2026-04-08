package ru.edenworld.edenhonor.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.edenworld.edenhonor.EdenHonorPlugin;
import ru.edenworld.edenhonor.model.HonorStatus;
import ru.edenworld.edenhonor.service.HonorService;
import ru.edenworld.edenhonor.util.TextUtil;

public final class HonorStatsMenu implements InventoryHolder {

    private static final int PAGE_SIZE = 45;

    private final EdenHonorPlugin plugin;
    private final HonorService honorService;
    private final int page;
    private final Inventory inventory;

    public HonorStatsMenu(EdenHonorPlugin plugin, HonorService honorService, int page) {
        this.plugin = plugin;
        this.honorService = honorService;
        this.page = Math.max(0, page);
        this.inventory = Bukkit.createInventory(this, 54, "EdenHonor • Статистика");
        build();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int getPage() {
        return page;
    }

    private void build() {
        List<UUID> players = new ArrayList<>(honorService.getKnownPlayers());
        players.sort(Comparator
                .comparingInt((UUID uuid) -> getWeight(honorService.getStatus(uuid))).reversed()
                .thenComparingInt((UUID uuid) -> honorService.getRecentKillCount(uuid)).reversed()
                .thenComparingDouble((UUID uuid) -> honorService.getRecentDamage(uuid)).reversed()
                .thenComparing(this::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        int totalPages = Math.max(1, (int) Math.ceil(players.size() / (double) PAGE_SIZE));
        int safePage = Math.min(page, totalPages - 1);
        int start = safePage * PAGE_SIZE;
        int end = Math.min(players.size(), start + PAGE_SIZE);

        for (int i = start; i < end; i++) {
            UUID playerId = players.get(i);
            inventory.setItem(i - start, createPlayerItem(playerId));
        }

        inventory.setItem(45, createNavItem(Material.ARROW, "&e← Назад", safePage > 0));
        inventory.setItem(49, createInfoItem(safePage + 1, totalPages, players.size()));
        inventory.setItem(53, createNavItem(Material.ARROW, "&eВперёд →", safePage < totalPages - 1));
    }

    private ItemStack createPlayerItem(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        String name = getDisplayName(playerId);

        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(TextUtil.colorize("&d" + name));

        HonorStatus status = honorService.getStatus(playerId);
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.colorize("&fМетка: " + honorService.getStatusText(playerId) + " " + honorService.getIndicator(playerId)));
        lore.add(TextUtil.colorize("&fУрон за окно: &6" + honorService.formatDamage(honorService.getRecentDamage(playerId))));
        lore.add(TextUtil.colorize("&fУбийств за окно: &c" + honorService.getRecentKillCount(playerId)));

        if (status == HonorStatus.WHITE || status == HonorStatus.GREEN) {
            lore.add(TextUtil.colorize("&fДо жёлтой: &e" + honorService.formatDamage(honorService.getDamageUntilYellow(playerId))));
        } else {
            lore.add(TextUtil.colorize("&fДо мира: &b" + honorService.getTimeLeft(playerId)));
            lore.add(TextUtil.colorize("&fДо белой: &f" + honorService.getTimeUntilWhite(playerId)));
        }

        if (status == HonorStatus.BLACK) {
            lore.add(TextUtil.colorize("&fС последнего убийства прошло: &8" + honorService.getBlackLastKillAgo(playerId)));
        }

        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createNavItem(Material material, String name, boolean enabled) {
        ItemStack stack = new ItemStack(enabled ? material : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(TextUtil.colorize(enabled ? name : "&7Недоступно"));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createInfoItem(int page, int totalPages, int totalPlayers) {
        ItemStack stack = new ItemStack(Material.BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(TextUtil.colorize("&dEdenHonor"));
        meta.lore(List.of(
                TextUtil.colorize("&fСтраница: &d" + page + "&7/&d" + totalPages),
                TextUtil.colorize("&fИгроков в статистике: &d" + totalPlayers),
                TextUtil.colorize("&7Сортировка: чёрные → красные → жёлтые → мирные")
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private String getDisplayName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString() : player.getName();
    }

    private int getWeight(HonorStatus status) {
        return switch (status) {
            case WHITE -> 0;
            case GREEN -> 1;
            case YELLOW -> 2;
            case RED -> 3;
            case BLACK -> 4;
        };
    }
}
