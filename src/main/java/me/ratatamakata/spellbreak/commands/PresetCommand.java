package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PresetCommand implements CommandExecutor, TabCompleter {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final File presetFile;
    private FileConfiguration presetConfig;

    public PresetCommand() {
        presetFile = new File(plugin.getDataFolder(), "presets.yml");
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        try {
            if (!presetFile.exists()) presetFile.createNewFile();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create presets.yml! " + e.getMessage());
        }
        presetConfig = YamlConfiguration.loadConfiguration(presetFile);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        if (args.length < 1) {
            sendUsage(player);
            return true;
        }
        String action = args[0].toLowerCase();
        UUID uuid = player.getUniqueId();
        String className = plugin.getPlayerDataManager().getPlayerClass(uuid);
        String baseNode = "presets." + uuid;

        switch (action) {
            case "create": case "save":
                if (args.length < 2) { sendUsage(player); break; }
                createPreset(player, baseNode + "." + className.toLowerCase(), args[1].toLowerCase());
                break;
            case "bind": case "load":
                if (args.length < 2) { sendUsage(player); break; }
                bindPreset(player, baseNode + "." + className.toLowerCase(), args[1].toLowerCase());
                break;
            case "remove": case "delete":
                if (args.length < 2) { sendUsage(player); break; }
                removePreset(player, baseNode + "." + className.toLowerCase(), args[1].toLowerCase());
                break;
            case "list":
                listPresets(player, baseNode);
                break;
            default:
                sendUsage(player);
        }
        return true;
    }

    private void createPreset(Player player, String classPath, String presetName) {
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        String[] binds = pdm.getBindings(player.getUniqueId());
        String path = classPath + "." + presetName;
        presetConfig.set(path, null);
        for (int i = 0; i < binds.length; i++) if (binds[i] != null) presetConfig.set(path + "." + i, binds[i]);
        saveConfig();
        player.sendMessage(ChatColor.GREEN + "Preset '" + ChatColor.YELLOW + presetName + ChatColor.GREEN + "' saved for class " + ChatColor.AQUA + classPath.substring(classPath.lastIndexOf('.')+1));
    }

    private void bindPreset(Player player, String classPath, String presetName) {
        String path = classPath + "." + presetName;
        if (!presetConfig.isConfigurationSection(path)) {
            player.sendMessage(ChatColor.RED + "Preset '" + ChatColor.YELLOW + presetName + ChatColor.RED + "' not found.");
            return;
        }
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        pdm.clearBindings(player.getUniqueId());
        ConfigurationSection section = presetConfig.getConfigurationSection(path);
        int count = 0;
        for (String key : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                String ability = section.getString(key);
                if (plugin.getAbilityManager().getAbilityByName(ability) != null) {
                    pdm.bindAbility(player.getUniqueId(), slot, ability);
                    count++;
                }
            } catch (NumberFormatException ignored) {}
        }
        player.sendMessage(count > 0 ?
                ChatColor.GREEN + "Loaded preset '" + ChatColor.YELLOW + presetName + ChatColor.GREEN + "' (" + count + " abilities)." :
                ChatColor.RED + "Preset '" + ChatColor.YELLOW + presetName + ChatColor.RED + "' is empty or invalid.");
    }

    private void removePreset(Player player, String classPath, String presetName) {
        String path = classPath + "." + presetName;
        if (!presetConfig.contains(path)) {
            player.sendMessage(ChatColor.RED + "Preset '" + ChatColor.YELLOW + presetName + ChatColor.RED + "' not found.");
            return;
        }
        presetConfig.set(path, null);
        saveConfig();
        player.sendMessage(ChatColor.GREEN + "Preset '" + ChatColor.YELLOW + presetName + ChatColor.GREEN + "' removed.");
    }

    private void listPresets(Player player, String baseNode) {
        if (!presetConfig.isConfigurationSection(baseNode)) {
            player.sendMessage(ChatColor.GRAY + "You have no presets.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "--- Your Presets ---");
        ConfigurationSection allClasses = presetConfig.getConfigurationSection(baseNode);
        for (String cls : allClasses.getKeys(false)) {
            ConfigurationSection presets = allClasses.getConfigurationSection(cls);
            Set<String> names = presets.getKeys(false);
            if (names.isEmpty()) continue;
            player.sendMessage(ChatColor.AQUA + cls + ": " + ChatColor.YELLOW + String.join(", ", names));
        }
    }

    private void saveConfig() {
        try { presetConfig.save(presetFile); }
        catch (IOException e) { plugin.getLogger().severe("Could not save presets.yml! " + e.getMessage()); }
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Preset Commands ---");
        player.sendMessage(ChatColor.YELLOW + "/preset create <name>" + ChatColor.GRAY + " - save your preset.");
        player.sendMessage(ChatColor.YELLOW + "/preset bind <name>" + ChatColor.GRAY + " - load a preset.");
        player.sendMessage(ChatColor.YELLOW + "/preset remove <name>" + ChatColor.GRAY + " - delete a preset.");
        player.sendMessage(ChatColor.YELLOW + "/preset list" + ChatColor.GRAY + " - list your presets.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        UUID uuid = player.getUniqueId();
        String className = plugin.getPlayerDataManager().getPlayerClass(uuid);
        String baseNode = "presets." + uuid + "." + className.toLowerCase();
        String last = args.length > 1 ? args[args.length-1].toLowerCase() : "";
        if (args.length == 1) {
            return List.of("create","save","bind","load","remove","delete","list").stream()
                    .filter(cmd -> cmd.startsWith(last)).collect(Collectors.toList());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("bind")||args[0].equalsIgnoreCase("load")
                ||args[0].equalsIgnoreCase("remove")||args[0].equalsIgnoreCase("delete"))) {
            if (presetConfig.isConfigurationSection(baseNode)) {
                return presetConfig.getConfigurationSection(baseNode).getKeys(false).stream()
                        .filter(name -> name.toLowerCase().startsWith(last))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
