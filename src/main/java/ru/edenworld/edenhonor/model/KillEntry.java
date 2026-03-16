package ru.edenworld.edenhonor.model;

import java.util.UUID;

public record KillEntry(long timestamp, UUID victim, KillReason reason) {

    public String serialize() {
        return timestamp + ";" + victim + ";" + reason.name();
    }

    public static KillEntry deserialize(String value) {
        String[] split = value.split(";", 3);
        if (split.length != 3) {
            throw new IllegalArgumentException("Invalid kill entry: " + value);
        }
        return new KillEntry(
                Long.parseLong(split[0]),
                UUID.fromString(split[1]),
                KillReason.valueOf(split[2])
        );
    }
}
