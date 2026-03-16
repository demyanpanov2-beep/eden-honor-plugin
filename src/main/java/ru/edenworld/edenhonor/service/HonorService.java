package ru.edenworld.edenhonor.service;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.edenworld.edenhonor.EdenHonorPlugin;
import ru.edenworld.edenhonor.model.DamageEntry;
import ru.edenworld.edenhonor.model.HonorStatus;
import ru.edenworld.edenhonor.model.KillEntry;
import ru.edenworld.edenhonor.model.KillReason;
import ru.edenworld.edenhonor.model.LastAggressor;
import ru.edenworld.edenhonor.model.PlayerHonorData;
import ru.edenworld.edenhonor.util.DurationFormatter;
import ru.edenworld.edenhonor.util.TextUtil;

public final class HonorService {

    private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));

    private final EdenHonorPlugin plugin;
    private final File dataFile;
    private final Map<UUID, PlayerHonorData> playerData = new HashMap<>();
    private final Map<UUID, LastAggressor> lastAggressors = new ConcurrentHashMap<>();

    public HonorService(EdenHonorPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public synchronized void load() {
        playerData.clear();
        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection players = configuration.getConfigurationSection("players");
        if (players == null) {
            return;
        }

        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection section = players.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }

                PlayerHonorData data = new PlayerHonorData();
                for (String encoded : section.getStringList("damage")) {
                    try {
                        data.getDamageEntries().add(DamageEntry.deserialize(encoded));
                    } catch (Exception ignored) {
                        plugin.getLogger().warning("Пропущена битая damage-запись для " + key + ": " + encoded);
                    }
                }
                for (String encoded : section.getStringList("kills")) {
                    try {
                        data.getKillEntries().add(KillEntry.deserialize(encoded));
                    } catch (Exception ignored) {
                        plugin.getLogger().warning("Пропущена битая kill-запись для " + key + ": " + encoded);
                    }
                }
                playerData.put(uuid, data);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Пропущен некорректный UUID в data.yml: " + key);
            }
        }

        pruneAll();
    }

    public synchronized void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку плагина для сохранения данных.");
            return;
        }

        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerHonorData> entry : playerData.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerHonorData data = entry.getValue();
            if (data.getDamageEntries().isEmpty() && data.getKillEntries().isEmpty()) {
                continue;
            }

            String basePath = "players." + uuid;
            configuration.set(basePath + ".damage", data.getDamageEntries().stream()
                    .map(DamageEntry::serialize)
                    .toList());
            configuration.set(basePath + ".kills", data.getKillEntries().stream()
                    .map(KillEntry::serialize)
                    .toList());
        }

        try {
            configuration.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось сохранить data.yml: " + exception.getMessage());
        }
    }

    public synchronized void pruneAll() {
        long cutoff = getCutoffTimestamp();
        playerData.values().forEach(data -> data.prune(cutoff));
        playerData.entrySet().removeIf(entry -> entry.getValue().getDamageEntries().isEmpty() && entry.getValue().getKillEntries().isEmpty());

        long aggressorCutoff = System.currentTimeMillis() - (getLastAggressorSeconds() * 1000L);
        lastAggressors.entrySet().removeIf(entry -> entry.getValue().timestamp() < aggressorCutoff);
    }

    public synchronized void recordDamage(UUID attacker, UUID victim, double finalDamage) {
        if (attacker.equals(victim) || finalDamage <= 0D) {
            return;
        }
        PlayerHonorData data = playerData.computeIfAbsent(attacker, ignored -> new PlayerHonorData());
        data.getDamageEntries().add(new DamageEntry(System.currentTimeMillis(), victim, finalDamage));
    }

    public void rememberAggressor(UUID victim, UUID attacker) {
        if (!victim.equals(attacker)) {
            lastAggressors.put(victim, new LastAggressor(attacker, System.currentTimeMillis()));
        }
    }

    public UUID getRecentAggressor(UUID victim) {
        LastAggressor aggressor = lastAggressors.get(victim);
        if (aggressor == null) {
            return null;
        }
        long ageMillis = System.currentTimeMillis() - aggressor.timestamp();
        if (ageMillis > getLastAggressorSeconds() * 1000L) {
            lastAggressors.remove(victim);
            return null;
        }
        return aggressor.attacker();
    }

    public synchronized void recordKill(UUID killer, UUID victim, KillReason reason) {
        if (killer.equals(victim)) {
            return;
        }
        PlayerHonorData data = playerData.computeIfAbsent(killer, ignored -> new PlayerHonorData());
        data.getKillEntries().add(new KillEntry(System.currentTimeMillis(), victim, reason));
    }

    public synchronized HonorStatus getStatus(UUID playerId) {
        PlayerHonorData data = getData(playerId);
        data.prune(getCutoffTimestamp());

        if (data.getActiveKillCount(getCutoffTimestamp()) > 0) {
            return HonorStatus.RED;
        }
        if (data.getRecentDamage(getCutoffTimestamp()) >= getOrangeThreshold()) {
            return HonorStatus.ORANGE;
        }
        return HonorStatus.GREEN;
    }

    public synchronized boolean isRed(UUID playerId) {
        return getStatus(playerId) == HonorStatus.RED;
    }

    public synchronized double getRecentDamage(UUID playerId) {
        return getData(playerId).getRecentDamage(getCutoffTimestamp());
    }

    public synchronized int getRecentKillCount(UUID playerId) {
        return getData(playerId).getActiveKillCount(getCutoffTimestamp());
    }

    public synchronized String getIndicator(UUID playerId) {
        return switch (getStatus(playerId)) {
            case GREEN -> plugin.getConfig().getString("indicators.green", "&a●");
            case ORANGE -> plugin.getConfig().getString("indicators.orange", "&6●");
            case RED -> plugin.getConfig().getString("indicators.red", "&c●");
        };
    }

    public Component getIndicatorComponent(UUID playerId) {
        return TextUtil.colorize(getIndicator(playerId));
    }

    public synchronized String getStatusText(UUID playerId) {
        return switch (getStatus(playerId)) {
            case GREEN -> plugin.getConfig().getString("status-text.green", "&aЗелёный");
            case ORANGE -> plugin.getConfig().getString("status-text.orange", "&6Оранжевый");
            case RED -> plugin.getConfig().getString("status-text.red", "&cКрасный");
        };
    }

    public synchronized String getTimeLeft(UUID playerId) {
        Optional<KillEntry> latestKill = getData(playerId).getLatestKill(getCutoffTimestamp());
        if (latestKill.isEmpty()) {
            return "0м";
        }
        long expiresAt = latestKill.get().timestamp() + getWindowMillis();
        return DurationFormatter.formatMillis(expiresAt - System.currentTimeMillis());
    }

    public synchronized String getLatestReasonText(UUID playerId) {
        Optional<KillEntry> latestKill = getData(playerId).getLatestKill(getCutoffTimestamp());
        if (latestKill.isEmpty()) {
            return "нет";
        }
        return switch (latestKill.get().reason()) {
            case CRIMINAL -> "убийство обычного игрока";
            case RETALIATION -> "убийство красного игрока";
        };
    }

    public synchronized List<String> getStatusLines(UUID playerId) {
        List<String> lines = new ArrayList<>();
        lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-1", "&fСтатус: %status_text% %indicator%"), playerId));
        lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-2", "&fУрон за окно: &6%damage%"), playerId));
        lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-3", "&fАктивных убийств за окно: &c%kills%"), playerId));
        lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-4", "&fДо зелёного: &b%timeleft%"), playerId));
        lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-5", "&fПоследняя причина красного: &e%reason%"), playerId));
        return lines;
    }

    public synchronized void pardon(UUID playerId) {
        playerData.computeIfAbsent(playerId, ignored -> new PlayerHonorData()).clearAll();
    }

    public synchronized void wipeAll() {
        playerData.clear();
        lastAggressors.clear();
    }

    public synchronized Set<String> getKnownNames() {
        return playerData.keySet().stream()
                .map(Bukkit::getOfflinePlayer)
                .map(OfflinePlayer::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());
    }

    public boolean isWorldTracked(String worldName) {
        List<String> ignoredWorlds = plugin.getConfig().getStringList("ignored-worlds");
        return !ignoredWorlds.contains(worldName);
    }

    public long getWindowMillis() {
        long hours = Math.max(1L, plugin.getConfig().getLong("window-hours", 96L));
        return hours * 60L * 60L * 1000L;
    }

    public long getLastAggressorSeconds() {
        return Math.max(5L, plugin.getConfig().getLong("last-aggressor-seconds", 20L));
    }

    public double getOrangeThreshold() {
        return Math.max(1D, plugin.getConfig().getDouble("orange-damage-threshold", 300D));
    }

    public synchronized Collection<UUID> getKnownPlayers() {
        return Collections.unmodifiableSet(playerData.keySet());
    }

    public synchronized void reloadAll() {
        plugin.reloadConfig();
        load();
    }

    public String formatDamage(double damage) {
        return DAMAGE_FORMAT.format(damage);
    }

    private PlayerHonorData getOrCreate(UUID playerId) {
        return playerData.computeIfAbsent(playerId, ignored -> new PlayerHonorData());
    }

    private PlayerHonorData getData(UUID playerId) {
        return playerData.getOrDefault(playerId, new PlayerHonorData());
    }

    private long getCutoffTimestamp() {
        return System.currentTimeMillis() - getWindowMillis();
    }

    private String replaceStatusTokens(String template, UUID playerId) {
        return template
                .replace("%status_text%", getStatusText(playerId))
                .replace("%indicator%", getIndicator(playerId))
                .replace("%damage%", formatDamage(getRecentDamage(playerId)))
                .replace("%kills%", Integer.toString(getRecentKillCount(playerId)))
                .replace("%timeleft%", getTimeLeft(playerId))
                .replace("%reason%", getLatestReasonText(playerId));
    }
}
