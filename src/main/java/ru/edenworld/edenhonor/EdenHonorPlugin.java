package ru.edenworld.edenhonor;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.edenworld.edenhonor.command.HonorCommand;
import ru.edenworld.edenhonor.listener.ChatListener;
import ru.edenworld.edenhonor.listener.CombatListener;
import ru.edenworld.edenhonor.listener.DeathListener;
import ru.edenworld.edenhonor.placeholder.EdenHonorExpansion;
import ru.edenworld.edenhonor.service.HonorService;

public final class EdenHonorPlugin extends JavaPlugin {

    private HonorService honorService;
    private EdenHonorExpansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.honorService = new HonorService(this);
        this.honorService.load();

        Bukkit.getPluginManager().registerEvents(new CombatListener(this, honorService), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(this, honorService), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this, honorService), this);

        HonorCommand command = new HonorCommand(this, honorService);
        Objects.requireNonNull(getCommand("honor"), "Command honor missing in plugin.yml")
                .setExecutor(command);
        Objects.requireNonNull(getCommand("honor"), "Command honor missing in plugin.yml")
                .setTabCompleter(command);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new EdenHonorExpansion(this, honorService);
            expansion.register();
            getLogger().info("PlaceholderAPI найден. Плейсхолдеры EdenHonor зарегистрированы.");
        } else {
            getLogger().warning("PlaceholderAPI не найден. TAB-интеграция через плейсхолдеры будет недоступна.");
        }

        long autosaveSeconds = Math.max(30L, getConfig().getLong("autosave-seconds", 300L));
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                this,
                () -> {
                    try {
                        honorService.pruneAll();
                        honorService.save();
                    } catch (Exception exception) {
                        getLogger().warning("Не удалось автосохранить EdenHonor: " + exception.getMessage());
                    }
                },
                autosaveSeconds * 20L,
                autosaveSeconds * 20L
        );
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
        }
        if (honorService != null) {
            honorService.save();
        }
    }

    public HonorService getHonorService() {
        return honorService;
    }
}
