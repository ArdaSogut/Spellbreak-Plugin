package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TestPvPCommand implements CommandExecutor {

    private final Spellbreak plugin;

    public TestPvPCommand(Spellbreak plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.GOLD + "PvP Test Commands:");
            player.sendMessage(ChatColor.YELLOW + "/testpvp damage <amount> <ability> [attacker]");
            player.sendMessage(ChatColor.YELLOW + "/testpvp fakestreak <number>");
            player.sendMessage(ChatColor.YELLOW + "/testpvp fakekill [victimName] [victimStreak]");
            player.sendMessage(ChatColor.YELLOW + "/testpvp die");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "damage":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /testpvp damage <amount> <ability> [attacker]");
                    return true;
                }
                double damage = Double.parseDouble(args[1]);
                String ability = args[2];
                String attacker = args.length > 3 ? args[3] : "TestDummy";

                plugin.getPvpManager().markInCombat(player);
                plugin.getPvpManager().addDamageRecord(player, attacker, ability, damage);
                player.sendMessage(ChatColor.GREEN + "Simulated taking " + damage + " damage from " + attacker + " using " + ability);
                player.sendMessage(ChatColor.GRAY + "You are now [In Combat]. Check your scoreboard!");
                break;

            case "fakestreak":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /testpvp fakestreak <amount>");
                    return true;
                }
                int streak = Integer.parseInt(args[1]);
                plugin.getPvpManager().setStreakForTesting(player, streak);
                player.sendMessage(ChatColor.GREEN + "Your kill streak is now artificially set to " + streak);
                break;

            case "fakekill":
                String victimName = args.length > 1 ? args[1] : "DummyVictim";
                int vStreak = args.length > 2 ? Integer.parseInt(args[2]) : 0;

                plugin.getPvpManager().simulateKillForTesting(player, victimName, vStreak);
                player.sendMessage(ChatColor.GREEN + "Simulated killing " + victimName + " (who had a streak of " + vStreak + ")");
                break;

            case "die":
                player.setHealth(0);
                player.sendMessage(ChatColor.RED + "You triggered a manual death to see the Death Recap.");
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown sub-command.");
                break;
        }

        return true;
    }
}
