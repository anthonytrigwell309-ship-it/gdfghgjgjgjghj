package com.yourserver.giveaway;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class GiveawayPlugin extends JavaPlugin {

    private GiveawayManager giveawayManager;
    private PendingReturns pendingReturns;

    @Override
    public void onEnable() {
        pendingReturns = new PendingReturns(this);
        giveawayManager = new GiveawayManager(this);

        GiveawayCommand handler = new GiveawayCommand(this);
        PluginCommand admin = getCommand("giveawaygui");
        PluginCommand player = getCommand("giveaway");
        if (admin == null || player == null) {
            getLogger().severe("Commands missing from plugin.yml - disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        admin.setExecutor(handler);
        admin.setTabCompleter(handler);
        player.setExecutor(handler);

        getServer().getPluginManager().registerEvents(new GiveawayGUIListener(this), this);
        getServer().getPluginManager().registerEvents(pendingReturns, this);
        getServer().getPluginManager().registerEvents(new PluginHider(this), this);

        getLogger().info("GiveawayGUI enabled.");
    }

    @Override
    public void onDisable() {
        if (giveawayManager != null) {
            giveawayManager.shutdown(); // saves prizes for their hosts
        }
        getLogger().info("GiveawayGUI disabled.");
    }

    public GiveawayManager getGiveawayManager() {
        return giveawayManager;
    }

    public PendingReturns getPendingReturns() {
        return pendingReturns;
    }
}
