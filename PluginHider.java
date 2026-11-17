package com.yourserver.giveaway;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Hides this plugin from /plugins and /pl, and its commands from tab-complete, for players (ops included).
 * The console still sees the real list. Players get the same list minus GiveawayGUI, split into Paper/Bukkit sections like the real one.
 */
public class PluginHider implements Listener {

    private static final Set<String> LIST_COMMANDS = Set.of(
            "pl", "plugins", "bukkit:pl", "bukkit:plugins", "minecraft:pl", "minecraft:plugins");

    private final GiveawayPlugin plugin;

    public PluginHider(GiveawayPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim();
        if (message.length() < 2) {
            return;
        }
        String label = message.substring(1).split("\\s+")[0].toLowerCase();
        if (!LIST_COMMANDS.contains(label)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        List<Plugin> visible = Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .filter(p -> !p.equals(plugin))
                .sorted(Comparator.comparing(p -> p.getName().toLowerCase()))
                .toList();

        List<Plugin> paperPlugins = visible.stream().filter(PluginHider::isPaperPlugin).toList();
        List<Plugin> bukkitPlugins = visible.stream().filter(p -> !isPaperPlugin(p)).toList();

        player.sendMessage(Component.text("\u2139 Server Plugins (" + visible.size() + "):", NamedTextColor.WHITE));
        if (!paperPlugins.isEmpty()) {
            player.sendMessage(Component.text("Paper Plugins (" + paperPlugins.size() + "):", TextColor.color(0x02A5E0)));
            player.sendMessage(nameLine(paperPlugins));
        }
        if (!bukkitPlugins.isEmpty()) {
            player.sendMessage(Component.text("Bukkit Plugins (" + bukkitPlugins.size() + "):", TextColor.color(0xED7202)));
            player.sendMessage(nameLine(bukkitPlugins));
        }
    }

    /** Paper plugins (paper-plugin.yml) use a different meta class than legacy plugin.yml plugins. */
    private static boolean isPaperPlugin(Plugin p) {
        try {
            return !p.getPluginMeta().getClass().getSimpleName().equals("PluginDescriptionFile");
        } catch (Throwable t) {
            return false;
        }
    }

    private static Component nameLine(List<Plugin> plugins) {
        Component line = Component.text("- ", NamedTextColor.DARK_GRAY);
        for (int i = 0; i < plugins.size(); i++) {
            Plugin p = plugins.get(i);
            line = line.append(Component.text(p.getName(), p.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (i < plugins.size() - 1) {
                line = line.append(Component.text(", ", NamedTextColor.WHITE));
            }
        }
        return line;
    }

    /** Removes /giveaway and /giveawaygui from the command suggestions sent to the client. */
    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        event.getCommands().removeIf(c -> c.equals("giveaway")
                || c.equals("giveawaygui")
                || c.startsWith("giveawaygui:"));
    }
}
