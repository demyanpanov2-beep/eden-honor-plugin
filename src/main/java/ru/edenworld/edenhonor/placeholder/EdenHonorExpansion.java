package ru.edenworld.edenhonor.placeholder;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.edenworld.edenhonor.EdenHonorPlugin;
import ru.edenworld.edenhonor.model.HonorStatus;
import ru.edenworld.edenhonor.service.HonorService;

public final class EdenHonorExpansion extends PlaceholderExpansion {

    private final EdenHonorPlugin plugin;
    private final HonorService honorService;

    public EdenHonorExpansion(EdenHonorPlugin plugin, HonorService honorService) {
        this.plugin = plugin;
        this.honorService = honorService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "edenhonor";
    }

    @Override
    public @NotNull String getAuthor() {
        return "OpenAI";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        HonorStatus status = honorService.getStatus(player.getUniqueId());
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "indicator" -> honorService.getIndicator(player.getUniqueId());
            case "status" -> status.name();
            case "status_text" -> honorService.getStatusText(player.getUniqueId());
            case "damage" -> honorService.formatDamage(honorService.getRecentDamage(player.getUniqueId()));
            case "kills" -> Integer.toString(honorService.getRecentKillCount(player.getUniqueId()));
            case "timeleft", "time_to_peaceful" -> honorService.getTimeLeft(player.getUniqueId());
            case "time_to_white" -> honorService.getTimeUntilWhite(player.getUniqueId());
            case "damage_to_yellow" -> honorService.formatDamage(honorService.getDamageUntilYellow(player.getUniqueId()));
            case "black_last_kill_ago" -> honorService.getBlackLastKillAgo(player.getUniqueId());
            case "reason" -> honorService.getLatestReasonText(player.getUniqueId());
            case "is_black" -> status == HonorStatus.BLACK ? "true" : "false";
            case "is_red" -> status == HonorStatus.RED ? "true" : "false";
            case "is_yellow", "is_orange" -> status == HonorStatus.YELLOW ? "true" : "false";
            case "is_green" -> status == HonorStatus.GREEN ? "true" : "false";
            case "is_white" -> status == HonorStatus.WHITE ? "true" : "false";
            default -> null;
        };
    }
}
