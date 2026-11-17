package com.yourserver.giveaway;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Items that could not be handed back right away (their owner was offline) are
 * saved to plugins/GiveawayGUI/pending.yml and delivered when that player joins.
 * Folia-safe: uses the entity scheduler and synchronizes file access.
 */
public class PendingReturns implements Listener {

    private final GiveawayPlugin plugin;
    private final File file;
    private final YamlConfiguration yml;

    public PendingReturns(GiveawayPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "pending.yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void add(UUID owner, ItemStack item) {
        List<ItemStack> items = read(owner);
        items.add(item.clone());
        yml.set(owner.toString(), items);
        save();
    }

    private List<ItemStack> read(UUID owner) {
        List<ItemStack> items = new ArrayList<>();
        List<?> raw = yml.getList(owner.toString());
        if (raw != null) {
            for (Object o : raw) {
                if (o instanceof ItemStack stack) {
                    items.add(stack);
                }
            }
        }
        return items;
    }

    private void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save pending.yml: " + e.getMessage());
        }
    }

    private synchronized List<ItemStack> take(UUID owner) {
        if (!yml.contains(owner.toString())) {
            return List.of();
        }
        List<ItemStack> items = read(owner);
        yml.set(owner.toString(), null);
        save();
        return items;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        List<ItemStack> items = take(id);
        if (items.isEmpty()) {
            return;
        }
        // Small delay so the player is fully loaded in; if they leave again, keep the items safe.
        player.getScheduler().runDelayed(plugin, task -> {
            for (ItemStack item : items) {
                var leftover = player.getInventory().addItem(item);
                for (ItemStack extra : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
            }
            player.sendMessage(Component.text("Your giveaway items were returned to you.", NamedTextColor.GREEN));
        }, () -> items.forEach(i -> add(id, i)), 20L);
    }
}
