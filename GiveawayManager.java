package com.yourserver.giveaway;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Folia-safe: the countdown runs on the global region scheduler and anything that
 * touches a player's inventory goes through that player's entity scheduler.
 * Works the same on regular Paper.
 */
public class GiveawayManager {

    /** Middle column, all 3 rows of a 27-slot inventory. */
    public static final int[] PRIZE_SLOTS = {4, 13, 22};
    public static final int GUI_SIZE = 27;

    /** A prize plus the player who donated it, so it can be refunded. */
    public record Prize(ItemStack item, UUID host) {
    }

    private final GiveawayPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private volatile boolean active = false;
    private final List<Prize> prizes = new CopyOnWriteArrayList<>();
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private volatile long endEpochSeconds;
    private volatile ScheduledTask countdownTask;

    public GiveawayManager(GiveawayPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    public List<Prize> getPrizes() {
        return prizes;
    }

    public Set<UUID> getParticipants() {
        return participants;
    }

    public long getSecondsLeft() {
        return Math.max(0, endEpochSeconds - System.currentTimeMillis() / 1000L);
    }

    /**
     * Adds a prize. Starts a new giveaway if none is running, otherwise adds another
     * prize slot and resets the countdown. Returns false if all slots are taken.
     */
    public synchronized boolean addPrize(ItemStack item, UUID host, long durationSeconds) {
        if (prizes.size() >= PRIZE_SLOTS.length) {
            return false;
        }
        prizes.add(new Prize(item.clone(), host));
        boolean wasActive = active;
        active = true;
        endEpochSeconds = System.currentTimeMillis() / 1000L + durationSeconds;

        if (!wasActive) {
            participants.clear();
            startCountdown();
            broadcast("<gradient:#89f7fe:#66a6ff>\u2726 A new giveaway has started! Run </gradient><white>/giveaway</white><gradient:#89f7fe:#66a6ff> and click the item to enter! \u2726</gradient>");
        } else {
            broadcast("<gradient:#a18cd1:#fbc2eb>\u2726 Another prize was added to the giveaway! \u2726</gradient>");
        }
        return true;
    }

    /** Stops the giveaway and gives every prize back to whoever donated it. */
    public synchronized void cancel() {
        stop(false);
    }

    /** Called on plugin disable: schedulers are gone, so prizes are saved to disk instead. */
    public synchronized void shutdown() {
        stop(true);
    }

    private void stop(boolean shuttingDown) {
        active = false;
        ScheduledTask task = countdownTask;
        if (task != null) {
            task.cancel();
            countdownTask = null;
        }
        for (Prize prize : prizes) {
            refund(prize, shuttingDown);
        }
        prizes.clear();
        participants.clear();
        if (!shuttingDown) {
            closeGUIs();
        }
    }

    public boolean join(Player player) {
        if (!active) {
            return false;
        }
        return participants.add(player.getUniqueId());
    }

    public boolean hasJoined(Player player) {
        return participants.contains(player.getUniqueId());
    }

    public Inventory buildGUI() {
        GiveawayGUIHolder holder = new GiveawayGUIHolder();
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, Component.text("Giveaway", TextColor.color(0x55FF55)));
        holder.setInventory(inv);

        ItemStack filler = createFiller();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, filler.clone());
        }
        List<Prize> snapshot = new ArrayList<>(prizes);
        for (int i = 0; i < snapshot.size() && i < PRIZE_SLOTS.length; i++) {
            inv.setItem(PRIZE_SLOTS[i], snapshot.get(i).item().clone());
        }
        return inv;
    }

    private ItemStack createFiller() {
        ItemStack pane = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private void startCountdown() {
        ScheduledTask old = countdownTask;
        if (old != null) {
            old.cancel();
        }
        countdownTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (!active) {
                task.cancel();
                return;
            }
            long left = getSecondsLeft();
            if (left <= 0) {
                task.cancel();
                endGiveaway();
                return;
            }
            if (left == 30 || left == 10 || left <= 5) {
                broadcast("<gradient:#fda085:#f6d365>Giveaway ending in " + left + "s!</gradient>");
            }
        }, 20L, 20L);
    }

    private synchronized void endGiveaway() {
        if (!active) {
            return;
        }
        active = false;
        countdownTask = null;

        // Only players who are still online can win.
        List<Player> candidates = new ArrayList<>();
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                candidates.add(p);
            }
        }

        if (candidates.isEmpty()) {
            broadcast("<gray>The giveaway has ended, but nobody (online) entered. The prizes were returned to the hosts.</gray>");
            for (Prize prize : prizes) {
                refund(prize, false);
            }
        } else {
            Player winner = candidates.get(new Random().nextInt(candidates.size()));
            for (Prize prize : prizes) {
                deliver(winner, prize.item().clone());
            }
            broadcast("<gradient:#89f7fe:#66a6ff>\u2726 Congratulations <white><bold>" + winner.getName()
                    + "</bold></white> won the giveaway! \u2726</gradient>");
        }
        prizes.clear();
        participants.clear();
        closeGUIs();
    }

    private void refund(Prize prize, boolean shuttingDown) {
        Player host = Bukkit.getPlayer(prize.host());
        if (!shuttingDown && host != null && host.isOnline()) {
            deliver(host, prize.item().clone());
            host.sendMessage(mm.deserialize("<yellow>Your giveaway item was returned to you.</yellow>"));
        } else {
            plugin.getPendingReturns().add(prize.host(), prize.item());
        }
    }

    /** Hands an item to a player on their own thread; if they vanish meanwhile it is saved for later. */
    private void deliver(Player player, ItemStack item) {
        UUID id = player.getUniqueId();
        player.getScheduler().run(plugin, task -> {
            var leftover = player.getInventory().addItem(item);
            for (ItemStack extra : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }, () -> plugin.getPendingReturns().add(id, item));
    }

    private void closeGUIs() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getScheduler().run(plugin, task -> {
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof GiveawayGUIHolder) {
                    p.closeInventory();
                }
            }, null);
        }
    }

    public void broadcast(String miniMessageText) {
        Component component = mm.deserialize(miniMessageText);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(component);
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }
}
