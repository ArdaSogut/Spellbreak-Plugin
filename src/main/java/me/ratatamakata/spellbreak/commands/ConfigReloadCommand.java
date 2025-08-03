package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ConfigReloadCommand implements CommandExecutor, TabCompleter {
    private final Spellbreak plugin;

    public ConfigReloadCommand(Spellbreak plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("spellbreak.reload")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        try {
            // Reload the config file
            plugin.reloadConfig();
            
            // Reload all ability configurations
            plugin.getAbilityManager().getAllAbilities().forEach(ability -> {
                try {
                    ability.loadConfig();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to reload config for ability: " + ability.getName());
                    e.printStackTrace();
                }
            });

            sender.sendMessage(ChatColor.GREEN + "Spellbreak configuration has been reloaded successfully!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "An error occurred while reloading the configuration!");
            plugin.getLogger().severe("Error reloading config: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>(); // No tab completion needed for this command
    }
} 