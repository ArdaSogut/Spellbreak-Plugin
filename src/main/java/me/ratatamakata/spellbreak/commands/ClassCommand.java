// src/main/java/me/ratatamakata/spellbreak/commands/ClassCommand.java
package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class ClassCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this.");
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(ChatColor.GOLD + "Classes: " +
                    Spellbreak.getInstance().getSpellClassManager()
                            .getAllClasses().stream().collect(Collectors.joining(", ")));
            p.sendMessage(ChatColor.YELLOW + "Usage: /class <className>");
            return true;
        }

        String chosen = Spellbreak.getInstance()
                .getSpellClassManager()
                .getProperClassName(args[0]);
        if (chosen == null) {
            p.sendMessage(ChatColor.RED + "Invalid class! Available: " +
                    Spellbreak.getInstance().getSpellClassManager()
                            .getAllClasses().stream().collect(Collectors.joining(", ")));
            return true;
        }

        // set new class
        Spellbreak.getInstance().getPlayerDataManager()
                .setClass(p.getUniqueId(), chosen);

        // clear old bindings
        Spellbreak.getInstance().getPlayerDataManager()
                .clearBindings(p.getUniqueId());

        p.sendMessage(ChatColor.GREEN + "You are now a " +
                ChatColor.AQUA + chosen + ChatColor.GREEN + "! Your ability slots have been reset.");
        return true;
    }
}
