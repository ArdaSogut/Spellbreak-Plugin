package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CooldownResetCommand implements CommandExecutor {

    private final Spellbreak plugin; // plugin instance is kept for consistency, though not strictly needed for static bypass access

    public CooldownResetCommand(Spellbreak plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();

        if (Spellbreak.hasBypass(playerId)) {
            Spellbreak.removeBypass(playerId);
            player.sendMessage(ChatColor.GREEN + "Ability cooldowns are now ACTIVE for you.");
        } else {
            Spellbreak.addBypass(playerId);
            player.sendMessage(ChatColor.YELLOW + "Ability cooldowns are now BYPASSED for you indefinitely.");
            player.sendMessage(ChatColor.YELLOW + "Run " + ChatColor.AQUA + "/" + label + ChatColor.YELLOW + " again to re-activate cooldowns.");
        }
        return true;
    }
} 