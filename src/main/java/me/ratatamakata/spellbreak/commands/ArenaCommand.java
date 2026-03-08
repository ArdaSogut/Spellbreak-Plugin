package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ArenaCommand implements CommandExecutor {

    private final Spellbreak plugin;

    public ArenaCommand(Spellbreak plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /pvp <start|setspawn>");
            return true;
        }

        if (args[0].equalsIgnoreCase("setspawn")) {
            if (!player.hasPermission("spellbreak.admin")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to do this.");
                return true;
            }
            plugin.getArenaManager().setSpawnLocation(player.getLocation());
            player.sendMessage(ChatColor.GREEN + "Arena spawn location set!");
            return true;
        } else if (args[0].equalsIgnoreCase("start")) {
            if (!player.hasPermission("spellbreak.admin")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to do this.");
                return true;
            }
            plugin.getArenaManager().startArenaMatch(player);
            return true;
        }

        player.sendMessage(ChatColor.RED + "Unknown argument. Usage: /pvp <start|setspawn>");
        return true;
    }
}
