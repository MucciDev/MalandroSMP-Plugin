package me.nezarc.smp;

import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

public final class SMP extends JavaPlugin {
    public Logger logger = getLogger();

    @Override
    public void onEnable() {
        // Plugin startup logic
        logger.info("Haii :3");

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        logger.info("Bye :3");
    }
}
