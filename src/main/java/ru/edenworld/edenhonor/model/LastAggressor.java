package ru.edenworld.edenhonor.model;

import java.util.UUID;

public record LastAggressor(UUID attacker, long timestamp) {
}
