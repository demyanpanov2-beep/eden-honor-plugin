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
import java.util.OptionalLong;
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
                data.setForcedBlackUntil(section.getLong("forced-black-until", 0L));
                data.setLastBlackDeathTimestamp(section.getLong("last-black-death-timestamp", 0L));
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
            if (!data.hasPersistentState()) {
                continue;
            }

            String basePath = "players." + uuid;
            configuration.set(basePath + ".damage", data.getDamageEntries().stream()
                    .map(DamageEntry::serialize)
                    .toList());
            configuration.set(basePath + ".kills", data.getKillEntries().stream()
                    .map(KillEntry::serialize)
                    .toList());
            configuration.set(basePath + ".forced-black-until", data.getForcedBlackUntil());
            configuration.set(basePath + ".last-black-death-timestamp", data.getLastBlackDeathTimestamp());
        }

        try {
            configuration.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось сохранить data.yml: " + exception.getMessage());
        }
    }

    public synchronized void pruneAll() {
        long cutoff = getCutoffTimestamp();
        long now = System.currentTimeMillis();
        long blackVictimCutoff = now - getBlackVictimChainMillis();

        playerData.values().forEach(data -> {
            data.prune(cutoff);
            if (data.getForcedBlackUntil() <= now) {
                data.setForcedBlackUntil(0L);
            }
            if (data.getLastBlackDeathTimestamp() < blackVictimCutoff) {
                data.setLastBlackDeathTimestamp(0L);
            }
        });
        playerData.entrySet().removeIf(entry -> !entry.getValue().hasPersistentState());

        long aggressorCutoff = now - (getLastAggressorSeconds() * 1000L);
        lastAggressors.entrySet().removeIf(entry -> entry.getValue().timestamp() < aggressorCutoff);
    }

    public synchronized void recordDamage(UUID attacker, UUID victim, double finalDamage) {
        if (attacker.equals(victim) || finalDamage <= 0D) {
            return;
        }
        PlayerHonorData data = getOrCreate(attacker);
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
        PlayerHonorData data = getOrCreate(killer);
        data.getKillEntries().add(new KillEntry(System.currentTimeMillis(), victim, reason));
    }

    public synchronized void forceBlack(UUID playerId) {
        long forcedUntil = System.currentTimeMillis() + getWindowMillis();
        PlayerHonorData data = getOrCreate(playerId);
        data.setForcedBlackUntil(Math.max(data.getForcedBlackUntil(), forcedUntil));
    }

    public synchronized void recordBlackDeath(UUID playerId) {
        getOrCreate(playerId).setLastBlackDeathTimestamp(System.currentTimeMillis());
    }

    public synchronized boolean wasBlackKilledRecently(UUID playerId) {
        long lastKill = getData(playerId).getLastBlackDeathTimestamp();
        return lastKill > 0L && (System.currentTimeMillis() - lastKill) < getBlackVictimChainMillis();
    }

    public synchronized HonorStatus getStatus(UUID playerId) {
        PlayerHonorData data = getSnapshot(playerId);
        if (isForcedBlack(data) || data.getActiveKillCount(getCutoffTimestamp()) >= getBlackKillThreshold()) {
            return HonorStatus.BLACK;
        }
        if (data.getActiveKillCount(getCutoffTimestamp()) > 0) {
            return HonorStatus.RED;
        }

        double damage = data.getRecentDamage(getCutoffTimestamp());
        if (damage >= getYellowThreshold()) {
            return HonorStatus.YELLOW;
        }
        if (damage < getWhiteThreshold()) {
            return HonorStatus.WHITE;
        }
        return HonorStatus.GREEN;
    }

    public synchronized boolean isRed(UUID playerId) {
        return getStatus(playerId) == HonorStatus.RED;
    }

    public synchronized boolean isBlack(UUID playerId) {
        return getStatus(playerId) == HonorStatus.BLACK;
    }

    public synchronized boolean isOutlaw(UUID playerId) {
        HonorStatus status = getStatus(playerId);
        return status == HonorStatus.RED || status == HonorStatus.BLACK;
    }

    public synchronized boolean isPeaceful(UUID playerId) {
        HonorStatus status = getStatus(playerId);
        return status == HonorStatus.WHITE || status == HonorStatus.GREEN;
    }

    public synchronized double getRecentDamage(UUID playerId) {
        return getSnapshot(playerId).getRecentDamage(getCutoffTimestamp());
    }

    public synchronized int getRecentKillCount(UUID playerId) {
        return getSnapshot(playerId).getActiveKillCount(getCutoffTimestamp());
    }

    public synchronized String getIndicator(UUID playerId) {
        HonorStatus status = getStatus(playerId);
        String key = status.name().toLowerCase(Locale.ROOT);
        String fallback = switch (status) {
            case WHITE -> "&f●";
            case GREEN -> "&a●";
            case YELLOW -> "&e●";
            case RED -> "&c●";
            case BLACK -> "&0●";
        };
        String value = plugin.getConfig().getString("indicators." + key);
        if (value == null && status == HonorStatus.YELLOW) {
            value = plugin.getConfig().getString("indicators.orange");
        }
        return value == null ? fallback : value;
    }

    public Component getIndicatorComponent(UUID playerId) {
        return TextUtil.colorize(getIndicator(playerId));
    }

    public synchronized String getStatusText(UUID playerId) {
        HonorStatus status = getStatus(playerId);
        String key = status.name().toLowerCase(Locale.ROOT);
        String fallback = switch (status) {
            case WHITE -> "&fБелая";
            case GREEN -> "&aЗелёная";
            case YELLOW -> "&eЖёлтая";
            case RED -> "&cКрасная";
            case BLACK -> "&0Чёрная";
        };
        String value = plugin.getConfig().getString("status-text." + key);
        if (value == null && status == HonorStatus.YELLOW) {
            value = plugin.getConfig().getString("status-text.orange");
        }
        return value == null ? fallback : value;
    }

    public synchronized String getTimeLeft(UUID playerId) {
        return DurationFormatter.formatMillis(getTimeUntilPeacefulMillis(playerId));
    }

    public synchronized String getTimeUntilWhite(UUID playerId) {
        return DurationFormatter.formatMillis(getTimeUntilWhiteMillis(playerId));
    }

    public synchronized double getDamageUntilYellow(UUID playerId) {
        return Math.max(0D, getYellowThreshold() - getRecentDamage(playerId));
    }

    public synchronized String getLatestReasonText(UUID playerId) {
        Optional<KillEntry> latestKill = getSnapshot(playerId).getLatestKill(getCutoffTimestamp());
        if (latestKill.isEmpty()) {
            return "нет";
        }
        return switch (latestKill.get().reason()) {
            case CRIMINAL -> "убийство обычного игрока";
            case RETALIATION -> "убийство красного/чёрного игрока";
        };
    }

    public synchronized String getBlackLastKillAgo(UUID playerId) {
        Optional<KillEntry> latestKill = getSnapshot(playerId).getLatestKill(getCutoffTimestamp());
        if (latestKill.isEmpty()) {
            return "0м";
        }
        return DurationFormatter.formatMillis(System.currentTimeMillis() - latestKill.get().timestamp());
    }

    public synchronized List<String> getStatusLines(UUID playerId) {
        List<String> lines = new ArrayList<>();
        HonorStatus status = getStatus(playerId);

        lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-1", "&fСтатус: %status_text% %indicator%"), playerId));
        lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-2", "&fУрон за окно: &6%damage%"), playerId));
        lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-3", "&fАктивных убийств за окно: &c%kills%"), playerId));

        if (status == HonorStatus.WHITE || status == HonorStatus.GREEN) {
            lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-peaceful", "&fДо жёлтой метки: &e%damage_to_yellow%"), playerId));
        } else {
            lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-hostile-1", "&fДо зелёной/белой метки: &b%timeleft%"), playerId));
            lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-hostile-2", "&fДо белой метки: &f%white_time%"), playerId));
        }

        if (status == HonorStatus.BLACK) {
            lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-black", "&fС последнего убийства прошло: &8%black_last_kill_ago%"), playerId));
        }

        lines.add(replaceStatusTokens(plugin.getConfig().getString("messages.status-line-reason", "&fПоследняя причина убийства: &e%reason%"), playerId));
        return lines;
    }

    public synchronized void pardon(UUID playerId) {
        getOrCreate(playerId).clearAll();
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

    public double getWhiteThreshold() {
        return Math.max(0D, plugin.getConfig().getDouble("white-damage-threshold", 5D));
    }

    public double getYellowThreshold() {
        if (plugin.getConfig().contains("yellow-damage-threshold")) {
            return Math.max(1D, plugin.getConfig().getDouble("yellow-damage-threshold", 450D));
        }
        return Math.max(1D, plugin.getConfig().getDouble("orange-damage-threshold", 450D));
    }

    public int getBlackKillThreshold() {
        return Math.max(2, plugin.getConfig().getInt("black-kill-threshold", 3));
    }

    public long getBlackVictimChainMillis() {
        long hours = Math.max(1L, plugin.getConfig().getLong("black-victim-chain-hours", 24L));
        return hours * 60L * 60L * 1000L;
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

    private PlayerHonorData getSnapshot(UUID playerId) {
        PlayerHonorData source = getData(playerId);
        PlayerHonorData copy = new PlayerHonorData();
        copy.getDamageEntries().addAll(source.getDamageEntries());
        copy.getKillEntries().addAll(source.getKillEntries());
        copy.setForcedBlackUntil(source.getForcedBlackUntil());
        copy.setLastBlackDeathTimestamp(source.getLastBlackDeathTimestamp());
        copy.prune(getCutoffTimestamp());
        long now = System.currentTimeMillis();
        if (copy.getForcedBlackUntil() <= now) {
            copy.setForcedBlackUntil(0L);
        }
        if (copy.getLastBlackDeathTimestamp() < now - getBlackVictimChainMillis()) {
            copy.setLastBlackDeathTimestamp(0L);
        }
        return copy;
    }

    private boolean isForcedBlack(PlayerHonorData data) {
        return data.getForcedBlackUntil() > System.currentTimeMillis();
    }

    private long getCutoffTimestamp() {
        return System.currentTimeMillis() - getWindowMillis();
    }

    private long getTimeUntilPeacefulMillis(UUID playerId) {
        PlayerHonorData data = getSnapshot(playerId);
        return Math.max(
                getTimeUntilKillsClearMillis(data),
                Math.max(
                        getTimeUntilDamageBelowMillis(data, getYellowThreshold()),
                        getTimeUntilForcedBlackEndsMillis(data)
                )
        );
    }

    private long getTimeUntilWhiteMillis(UUID playerId) {
        PlayerHonorData data = getSnapshot(playerId);
        return Math.max(
                getTimeUntilKillsClearMillis(data),
                Math.max(
                        getTimeUntilDamageBelowMillis(data, getWhiteThreshold()),
                        getTimeUntilForcedBlackEndsMillis(data)
                )
        );
    }

    private long getTimeUntilForcedBlackEndsMillis(PlayerHonorData data) {
        return Math.max(0L, data.getForcedBlackUntil() - System.currentTimeMillis());
    }

    private long getTimeUntilKillsClearMillis(PlayerHonorData data) {
        long cutoff = getCutoffTimestamp();
        OptionalLong latestExpiry = data.getKillEntries().stream()
                .filter(entry -> entry.timestamp() >= cutoff)
                .mapToLong(entry -> entry.timestamp() + getWindowMillis())
                .max();
        return latestExpiry.isPresent() ? Math.max(0L, latestExpiry.getAsLong() - System.currentTimeMillis()) : 0L;
    }

    private long getTimeUntilDamageBelowMillis(PlayerHonorData data, double threshold) {
        if (threshold <= 0D) {
            return 0L;
        }

        long cutoff = getCutoffTimestamp();
        List<DamageEntry> activeEntries = data.getDamageEntries().stream()
                .filter(entry -> entry.timestamp() >= cutoff)
                .sorted(Comparator.comparingLong(entry -> entry.timestamp() + getWindowMillis()))
                .toList();

        double currentDamage = activeEntries.stream()
                .mapToDouble(DamageEntry::finalDamage)
                .sum();

        if (currentDamage < threshold) {
            return 0L;
        }

        double rollingDamage = currentDamage;
        long now = System.currentTimeMillis();
        for (DamageEntry entry : activeEntries) {
            rollingDamage -= entry.finalDamage();
            if (rollingDamage < threshold) {
                long expiresAt = entry.timestamp() + getWindowMillis();
                return Math.max(0L, expiresAt - now);
            }
        }

        return 0L;
    }

    private String replaceStatusTokens(String template, UUID playerId) {
        return template
                .replace("%status_text%", getStatusText(playerId))
                .replace("%indicator%", getIndicator(playerId))
                .replace("%damage%", formatDamage(getRecentDamage(playerId)))
                .replace("%kills%", Integer.toString(getRecentKillCount(playerId)))
                .replace("%timeleft%", getTimeLeft(playerId))
                .replace("%white_time%", getTimeUntilWhite(playerId))
                .replace("%reason%", getLatestReasonText(playerId))
                .replace("%damage_to_yellow%", formatDamage(getDamageUntilYellow(playerId)))
                .replace("%black_last_kill_ago%", getBlackLastKillAgo(playerId));
    }
}
