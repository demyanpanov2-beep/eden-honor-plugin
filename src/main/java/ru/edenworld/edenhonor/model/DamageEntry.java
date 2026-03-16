package ru.edenworld.edenhonor.model;

import java.util.UUID;

public record DamageEntry(long timestamp, UUID victim, double finalDamage) {

    public String serialize() {
        return timestamp + ";" + victim + ";" + finalDamage;
    }

    public static DamageEntry deserialize(String value) {
        String[] split = value.split(";", 3);
        if (split.length != 3) {
            throw new IllegalArgumentException("Invalid damage entry: " + value);
        }
        return new DamageEntry(
                Long.parseLong(split[0]),
                UUID.fromString(split[1]),
                Double.parseDouble(split[2])
        );
    }
}
