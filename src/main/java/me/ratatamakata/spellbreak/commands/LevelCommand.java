package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.level.PlayerLevel;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class LevelCommand implements CommandExecutor, TabCompleter {
    private final Spellbreak plugin;
    private final List<String> validClasses = Arrays.asList("Elementalist", "Mindshaper", "Runesmith", "Necromancer", "Archdruid", "Lightbringer");

    public LevelCommand(Spellbreak plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player!");
                return true;
            }
            showPlayerStats((Player) sender, (Player) sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "stats":
                return handleStatsCommand(sender, args);
            case "set":
                return handleSetCommand(sender, args);
            case "give":
                return handleGiveCommand(sender, args);
            case "spells":
                return handleSpellsCommand(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleStatsCommand(CommandSender sender, String[] args) {
        Player target;

        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player!");
                return true;
            }
            target = (Player) sender;
        } else {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found!");
                return true;
            }
        }

        showPlayerStats(sender, target);
        return true;
    }

    private boolean handleSetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spellbreak.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /level set <player> <class> <player|spell> <level/spellname> [level]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found!");
            return true;
        }

        String className = args[2];
        if (!validClasses.contains(className)) {
            sender.sendMessage(ChatColor.RED + "Invalid class! Valid classes: " + String.join(", ", validClasses));
            return true;
        }

        String type = args[3].toLowerCase();

        if (type.equals("player")) {
            try {
                int level = Integer.parseInt(args[4]);
                if (level < 1 || level > 100) {
                    sender.sendMessage(ChatColor.RED + "Player level must be between 1 and 100!");
                    return true;
                }

                plugin.getLevelManager().setPlayerLevel(target.getUniqueId(), className, level);
                plugin.getLevelManager().refreshPlayerStats(target);
                sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s " + className + " level to " + level);
                target.sendMessage(ChatColor.YELLOW + "Your " + className + " level has been set to " + level + "!");

            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid level number!");
            }
        } else if (type.equals("spell")) {
            if (args.length < 6) {
                sender.sendMessage(ChatColor.RED + "Usage: /level set <player> <class> spell <spellname> <level>");
                return true;
            }

            String spellName = args[4];
            try {
                int level = Integer.parseInt(args[5]);
                if (level < 1 || level > 5) {
                    sender.sendMessage(ChatColor.RED + "Spell level must be between 1 and 5!");
                    return true;
                }

                plugin.getLevelManager().setSpellLevel(target.getUniqueId(), className, spellName, level);
                sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s " + className + " " + spellName + " to level " + level);
                target.sendMessage(ChatColor.YELLOW + "Your " + className + " " + spellName + " has been set to level " + level + "!");

            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid level number!");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Type must be 'player' or 'spell'!");
        }

        return true;
    }

    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spellbreak.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /level give <player> <class> <player|spell> <amount> [spellname]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found!");
            return true;
        }

        String className = args[2];
        if (!validClasses.contains(className)) {
            sender.sendMessage(ChatColor.RED + "Invalid class! Valid classes: " + String.join(", ", validClasses));
            return true;
        }

        String type = args[3].toLowerCase();

        try {
            int amount = Integer.parseInt(args[4]);

            if (type.equals("player")) {
                plugin.getLevelManager().getPlayerLevel(target.getUniqueId(), className).addExperience(amount);
                sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " " + amount + " " + className + " XP");
                target.sendMessage(ChatColor.GREEN + "Received " + amount + " " + className + " XP!");

            } else if (type.equals("spell")) {
                if (args.length < 6) {
                    sender.sendMessage(ChatColor.RED + "Usage: /level give <player> <class> spell <amount> <spellname>");
                    return true;
                }

                String spellName = args[5];
                plugin.getLevelManager().getSpellLevel(target.getUniqueId(), className, spellName).addExperience(amount);
                sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " " + amount + " " + className + " " + spellName + " XP");
                target.sendMessage(ChatColor.GREEN + "Received " + amount + " " + spellName + " XP!");
            } else {
                sender.sendMessage(ChatColor.RED + "Type must be 'player' or 'spell'!");
            }

        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid XP amount!");
        }

        return true;
    }

    private boolean handleSpellsCommand(CommandSender sender, String[] args) {
        Player target;

        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player!");
                return true;
            }
            target = (Player) sender;
        } else {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found!");
                return true;
            }
        }

        showSpellLevels(sender, target);
        return true;
    }

    private void showPlayerStats(CommandSender sender, Player target) {
        String className = plugin.getPlayerDataManager().getPlayerClass(target.getUniqueId());
        if (className.equals("None")) {
            sender.sendMessage(ChatColor.RED + "Player has no class selected!");
            return;
        }

        PlayerLevel playerLevel = plugin.getLevelManager().getPlayerLevel(target.getUniqueId(), className);

        sender.sendMessage(ChatColor.GOLD + "=== " + target.getName() + "'s " + className + " Stats ===");
        sender.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + playerLevel.getLevel());

        if (playerLevel.getLevel() < 100) {
            sender.sendMessage(ChatColor.YELLOW + "XP: " + ChatColor.WHITE +
                    playerLevel.getExperienceProgress() + "/" + playerLevel.getExperienceForNextLevel() +
                    " (" + String.format("%.1f", playerLevel.getExperiencePercentage()) + "%)");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "XP: " + ChatColor.GOLD + "MAX LEVEL");
        }

        sender.sendMessage(ChatColor.YELLOW + "Max Health: " + ChatColor.RED + playerLevel.getMaxHealth());
        sender.sendMessage(ChatColor.YELLOW + "Max Mana: " + ChatColor.BLUE + playerLevel.getMaxMana());
        sender.sendMessage(ChatColor.YELLOW + "Spell Power: " + ChatColor.LIGHT_PURPLE +
                String.format("%.1f", playerLevel.getSpellPower() * 100) + "%");
        sender.sendMessage(ChatColor.YELLOW + "Mana Regen: " + ChatColor.AQUA +
                String.format("%.1f", playerLevel.getManaRegenRate() * 100) + "%");
    }

    private void showSpellLevels(CommandSender sender, Player target) {
        String className = plugin.getPlayerDataManager().getPlayerClass(target.getUniqueId());
        if (className.equals("None")) {
            sender.sendMessage(ChatColor.RED + "Player has no class selected!");
            return;
        }

        Map<String, Map<String, SpellLevel>> playerSpells = plugin.getLevelManager().spellLevels.get(target.getUniqueId());
        if (playerSpells == null || !playerSpells.containsKey(className)) {
            sender.sendMessage(ChatColor.GRAY + "No spells have been used yet.");
            return;
        }

        Map<String, SpellLevel> classSpells = playerSpells.get(className);

        sender.sendMessage(ChatColor.GOLD + "=== " + target.getName() + "'s " + className + " Spell Levels ===");

        List<SpellLevel> sortedSpells = classSpells.values().stream()
                .sorted((a, b) -> Integer.compare(b.getLevel(), a.getLevel()))
                .collect(Collectors.toList());

        for (SpellLevel spell : sortedSpells) {
            String progressBar = createProgressBar(spell.getExperiencePercentage());

            sender.sendMessage(ChatColor.LIGHT_PURPLE + spell.getSpellName() +
                    ChatColor.WHITE + " - Level " + spell.getLevel() +
                    (spell.getLevel() < 5 ? " " + progressBar : ChatColor.GOLD + " [MAX]"));
        }
    }

    private String createProgressBar(double percentage) {
        int bars = (int) (percentage / 10);
        StringBuilder sb = new StringBuilder(ChatColor.GRAY + "[");

        for (int i = 0; i < 10; i++) {
            if (i < bars) {
                sb.append(ChatColor.GREEN + "|");
            } else {
                sb.append(ChatColor.DARK_GRAY + "|");
            }
        }

        sb.append(ChatColor.GRAY + "] " + String.format("%.1f", percentage) + "%");
        return sb.toString();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Level Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/level" + ChatColor.WHITE + " - Show your stats");
        sender.sendMessage(ChatColor.YELLOW + "/level stats [player]" + ChatColor.WHITE + " - Show player stats");
        sender.sendMessage(ChatColor.YELLOW + "/level spells [player]" + ChatColor.WHITE + " - Show spell levels");

        if (sender.hasPermission("spellbreak.admin")) {
            sender.sendMessage(ChatColor.RED + "Admin Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/level set <player> <class> player <level>" + ChatColor.WHITE + " - Set class level");
            sender.sendMessage(ChatColor.YELLOW + "/level set <player> <class> spell <spellname> <level>" + ChatColor.WHITE + " - Set spell level");
            sender.sendMessage(ChatColor.YELLOW + "/level give <player> <class> player <xp>" + ChatColor.WHITE + " - Give class XP");
            sender.sendMessage(ChatColor.YELLOW + "/level give <player> <class> spell <xp> <spellname>" + ChatColor.WHITE + " - Give spell XP");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("stats", "spells"));
            if (sender.hasPermission("spellbreak.admin")) {
                completions.addAll(Arrays.asList("set", "give"));
            }
        } else if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("give"))) {
            completions.addAll(validClasses);
        } else if (args.length == 4 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("give"))) {
            completions.addAll(Arrays.asList("player", "spell"));
        } else if (args.length == 5 && args[0].equalsIgnoreCase("set") && args[3].equalsIgnoreCase("spell")) {
            // Dynamically fetch spell names for the given class from config
            String classNameArg = args[2];
            List<String> classSpells = plugin.getSpellClassManager().getClassAbilities(classNameArg);
            if (classSpells != null && !classSpells.isEmpty()) {
                completions.addAll(classSpells);
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}