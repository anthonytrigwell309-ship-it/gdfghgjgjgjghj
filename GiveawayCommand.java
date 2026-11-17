package com.yourserver.giveaway;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GiveawayCommand implements CommandExecutor, TabCompleter {

    private final GiveawayPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public GiveawayCommand(GiveawayPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("giveawaygui")) {
            return handleAdmin(sender, args);
        } else if (command.getName().equalsIgnoreCase("giveaway")) {
            return handlePlayer(sender);
        }
        return false;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        // Bukkit permission checks already return true for ops automatically;
        // granting this node via a permissions plugin lets non-ops use it too.
        if (!sender.hasPermission("giveaway.admin")) {
            sender.sendMessage(mm.deserialize("<red>You do not have permission to use this command.</red>"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(mm.deserialize("<yellow>Usage: /giveawaygui [add [time]|cancel|info]</yellow>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(mm.deserialize("<red>Only a player can add an item - you must be holding it.</red>"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize("<yellow>Usage: /giveawaygui add [time] (e.g. 30s, 5m, 1h)</yellow>"));
                    return true;
                }
                long seconds = TimeUtil.parseSeconds(args[1]);
                if (seconds <= 0) {
                    sender.sendMessage(mm.deserialize("<red>Invalid time format. Use things like 30s, 5m, 1h, 1d.</red>"));
                    return true;
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType().isAir()) {
                    sender.sendMessage(mm.deserialize("<red>You must be holding the item you want to give away.</red>"));
                    return true;
                }

                ItemStack toGive = held.clone();
                boolean added = plugin.getGiveawayManager().addPrize(toGive, player.getUniqueId(), seconds);
                if (!added) {
                    sender.sendMessage(mm.deserialize("<red>The giveaway is full (max "
                            + GiveawayManager.PRIZE_SLOTS.length + " items).</red>"));
                    return true;
                }
                // The item is actually taken from the host's hand so it can't be duplicated.
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                sender.sendMessage(mm.deserialize("<green>Added " + toGive.getType().name() + " x" + toGive.getAmount()
                        + " to the giveaway. Ends in " + TimeUtil.formatSeconds(plugin.getGiveawayManager().getSecondsLeft())
                        + ".</green>"));
            }
            case "cancel" -> {
                if (!plugin.getGiveawayManager().isActive()) {
                    sender.sendMessage(mm.deserialize("<red>There is no active giveaway to cancel.</red>"));
                    return true;
                }
                plugin.getGiveawayManager().cancel();
                sender.sendMessage(mm.deserialize("<green>Giveaway cancelled. Prizes were returned to their hosts.</green>"));
                plugin.getGiveawayManager().broadcast("<gray>The giveaway has been cancelled by an admin.</gray>");
            }
            case "info" -> {
                if (!plugin.getGiveawayManager().isActive()) {
                    sender.sendMessage(mm.deserialize("<yellow>No giveaway is currently running.</yellow>"));
                } else {
                    sender.sendMessage(mm.deserialize("<green>Giveaway active! Prizes: "
                            + plugin.getGiveawayManager().getPrizes().size()
                            + " | Entries: " + plugin.getGiveawayManager().getParticipants().size()
                            + " | Time left: " + TimeUtil.formatSeconds(plugin.getGiveawayManager().getSecondsLeft())
                            + "</green>"));
                }
            }
            default -> sender.sendMessage(mm.deserialize("<yellow>Usage: /giveawaygui [add [time]|cancel|info]</yellow>"));
        }
        return true;
    }

    private boolean handlePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command.</red>"));
            return true;
        }
        if (!plugin.getGiveawayManager().isActive()) {
            player.sendMessage(mm.deserialize("<yellow>There is no giveaway running right now.</yellow>"));
            return true;
        }
        player.openInventory(plugin.getGiveawayManager().buildGUI());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("giveawaygui") && args.length == 1) {
            return filter(Arrays.asList("add", "cancel", "info"), args[0]);
        }
        if (command.getName().equalsIgnoreCase("giveawaygui") && args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return filter(Arrays.asList("30s", "1m", "5m", "10m", "1h"), args[1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(input.toLowerCase())) {
                result.add(option);
            }
        }
        return result;
    }
}
