package org.domaja;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class Sulfurlogger extends JavaPlugin {

    private CoreProtectAPI coreProtect;

    @Override
    public void onEnable() {
        coreProtect = hookCoreProtect();
        if (coreProtect == null) {
            getLogger().severe("CoreProtect не найден или его API отключено (нужен API v9+). Плагин выключен.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(new SulfurListener(this, coreProtect), this);
        getLogger().info("Sulfurlogger запущен, взрывы серных кубов пишутся в CoreProtect.");
    }

    private CoreProtectAPI hookCoreProtect() {
        Plugin p = getServer().getPluginManager().getPlugin("CoreProtect");
        if (!(p instanceof CoreProtect cp)) return null;
        CoreProtectAPI api = cp.getAPI();
        if (api == null || !api.isEnabled() || api.APIVersion() < 9) return null;
        return api;
    }
}