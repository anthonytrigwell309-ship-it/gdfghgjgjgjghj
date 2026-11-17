package com.yourserver.giveaway;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marker holder so GiveawayGUIListener can tell "this click happened in our GUI"
 * apart from any other inventory the player might have open, without relying on
 * comparing titles (which is fragile with Adventure Components).
 */
public class GiveawayGUIHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
