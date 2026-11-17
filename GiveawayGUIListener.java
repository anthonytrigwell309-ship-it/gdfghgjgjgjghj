package com.yourserver.giveaway;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public class GiveawayGUIListener implements Listener {

    private final GiveawayPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public GiveawayGUIListener(GiveawayPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GiveawayGUIHolder)) {
            return;
        }
        // Always cancel: nobody should be able to take items out of this menu.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GiveawayManager.GUI_SIZE) {
            return;
        }

        boolean isPrizeSlot = Arrays.stream(GiveawayManager.PRIZE_SLOTS).anyMatch(s -> s == slot);
        if (!isPrizeSlot) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        GiveawayManager manager = plugin.getGiveawayManager();
        if (!manager.isActive()) {
            player.sendMessage(mm.deserialize("<red>This giveaway has already ended.</red>"));
            return;
        }
        if (manager.hasJoined(player)) {
            player.sendMessage(mm.deserialize("<yellow>You have already entered this giveaway!</yellow>"));
            return;
        }
        manager.join(player);
        player.sendMessage(mm.deserialize("<green>You have entered the giveaway! Good luck!</green>"));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GiveawayGUIHolder) {
            event.setCancelled(true);
        }
    }
}
