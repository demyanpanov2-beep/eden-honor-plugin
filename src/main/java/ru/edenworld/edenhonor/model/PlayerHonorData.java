package ru.edenworld.edenhonor.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class PlayerHonorData {

    private final List<DamageEntry> damageEntries = new ArrayList<>();
    private final List<KillEntry> killEntries = new ArrayList<>();

    public List<DamageEntry> getDamageEntries() {
        return damageEntries;
    }

    public List<KillEntry> getKillEntries() {
        return killEntries;
    }

    public void prune(long cutoff) {
        damageEntries.removeIf(entry -> entry.timestamp() < cutoff);
        killEntries.removeIf(entry -> entry.timestamp() < cutoff);
    }

    public double getRecentDamage(long cutoff) {
        return damageEntries.stream()
                .filter(entry -> entry.timestamp() >= cutoff)
                .mapToDouble(DamageEntry::finalDamage)
                .sum();
    }

    public int getActiveKillCount(long cutoff) {
        return (int) killEntries.stream()
                .filter(entry -> entry.timestamp() >= cutoff)
                .count();
    }

    public Optional<KillEntry> getLatestKill(long cutoff) {
        return killEntries.stream()
                .filter(entry -> entry.timestamp() >= cutoff)
                .max(Comparator.comparingLong(KillEntry::timestamp));
    }

    public void clearAll() {
        damageEntries.clear();
        killEntries.clear();
    }
}
