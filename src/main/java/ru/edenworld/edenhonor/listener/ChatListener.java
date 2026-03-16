package ru.edenworld.edenhonor.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.edenworld.edenhonor.EdenHonorPlugin;
import ru.edenworld.edenhonor.service.HonorService;

public final class ChatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final EdenHonorPlugin plugin;
    private final HonorService honorService;

    public ChatListener(EdenHonorPlugin plugin, HonorService honorService) {
        this.plugin = plugin;
        this.honorService = honorService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }

        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) -> {
            Component indicator = honorService.getIndicatorComponent(source.getUniqueId());
            Component separator = LEGACY.deserialize("&7: ");
            return Component.empty()
                    .append(sourceDisplayName)
                    .append(Component.space())
                    .append(indicator)
                    .append(separator)
                    .append(message);
        }));
    }
}
