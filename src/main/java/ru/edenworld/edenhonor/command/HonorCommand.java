package ru.edenworld.edenhonor.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.edenworld.edenhonor.EdenHonorPlugin;
import ru.edenworld.edenhonor.menu.HonorStatsMenu;
import ru.edenworld.edenhonor.service.HonorService;
import ru.edenworld.edenhonor.util.TextUtil;

public final class HonorCommand implements CommandExecutor, TabCompleter {

    private final EdenHonorPlugin plugin;
    private final HonorService honorService;

    public HonorCommand(EdenHonorPlugin plugin, HonorService honorService) {
        this.plugin = plugin;
        this.honorService = honorService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendMessage(sender, plugin.getConfig().getString("messages.player-only", "&cЭту команду может использовать только игрок."));
                return true;
            }
            showStatus(sender, player.getUniqueId(), player.getName());
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                if (args.length < 2) {
                    sendRaw(sender, "&cИспользование: /honor status <player>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if ((target.getName() == null || target.getUniqueId() == null) && !target.hasPlayedBefore()) {
                    sendMessage(sender, plugin.getConfig().getString("messages.unknown-player", "&cИгрок не найден."));
                    return true;
                }
                showStatus(sender, target.getUniqueId(), target.getName() == null ? args[1] : target.getName());
                return true;
            }
            case "interface", "gui" -> {
                if (!sender.hasPermission("edenhonor.admin")) {
                    sendMessage(sender, plugin.getConfig().getString("messages.no-permission", "&cУ тебя нет прав."));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sendMessage(sender, plugin.getConfig().getString("messages.player-only", "&cЭту команду может использовать только игрок."));
                    return true;
                }
                player.openInventory(new HonorStatsMenu(plugin, honorService, 0).getInventory());
                return true;
            }
            case "pardon" -> {
                if (!sender.hasPermission("edenhonor.admin")) {
                    sendMessage(sender, plugin.getConfig().getString("messages.no-permission", "&cУ тебя нет прав."));
                    return true;
                }
                if (args.length < 2) {
                    sendRaw(sender, "&cИспользование: /honor pardon <player>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                honorService.pardon(target.getUniqueId());
                honorService.save();
                sendMessage(sender, plugin.getConfig().getString("messages.pardon-done", "&aСтатус игрока очищен."));
                return true;
            }
            case "wipe" -> {
                if (!sender.hasPermission("edenhonor.admin")) {
                    sendMessage(sender, plugin.getConfig().getString("messages.no-permission", "&cУ тебя нет прав."));
                    return true;
                }
                if (args.length < 2) {
                    sendRaw(sender, "&cИспользование: /honor wipe <player|all>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    honorService.wipeAll();
                    honorService.save();
                    sendMessage(sender, plugin.getConfig().getString("messages.wipe-all", "&aИстория всех игроков очищена."));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                honorService.pardon(target.getUniqueId());
                honorService.save();
                sendMessage(sender, plugin.getConfig().getString("messages.wipe-one", "&aИстория игрока очищена."));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("edenhonor.admin")) {
                    sendMessage(sender, plugin.getConfig().getString("messages.no-permission", "&cУ тебя нет прав."));
                    return true;
                }
                honorService.reloadAll();
                sendMessage(sender, plugin.getConfig().getString("messages.reloaded", "&aКонфиг и данные перезагружены."));
                return true;
            }
            default -> {
                sendRaw(sender, "&cИспользование: /honor [status <player>|interface|pardon <player>|wipe <player|all>|reload]");
                return true;
            }
        }
    }

    private void showStatus(CommandSender sender, UUID playerId, String fallbackName) {
        String playerName = fallbackName == null ? playerId.toString() : fallbackName;
        sendRaw(sender, "&d&l" + playerName);
        for (String line : honorService.getStatusLines(playerId)) {
            sendRaw(sender, line);
        }
    }

    private void sendMessage(CommandSender sender, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&dEdenHonor&8] &7");
        sender.sendMessage(TextUtil.colorize(prefix + message));
    }

    private void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(TextUtil.colorize(message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("status"));
            if (sender.hasPermission("edenhonor.admin")) {
                base.addAll(List.of("interface", "gui", "pardon", "wipe", "reload"));
            }
            return base.stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        if (args.length == 2 && List.of("status", "pardon", "wipe").contains(args[0].toLowerCase(Locale.ROOT))) {
            Stream<String> onlineNames = Bukkit.getOnlinePlayers().stream().map(Player::getName);
            Stream<String> knownNames = honorService.getKnownNames().stream();
            Stream<String> special = args[0].equalsIgnoreCase("wipe") ? Stream.of("all") : Stream.empty();
            return Stream.concat(Stream.concat(onlineNames, knownNames), special)
                    .distinct()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .sorted()
                    .toList();
        }

        return completions;
    }
}
