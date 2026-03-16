package ru.edenworld.edenhonor.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class TextUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private TextUtil() {
    }

    public static Component colorize(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    public static String stripLegacyPrefix(String text) {
        return text == null ? "" : text;
    }
}
