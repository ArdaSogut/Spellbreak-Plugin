package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.managers.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TeamCommand implements CommandExecutor {
    private final Spellbreak plugin;
    private final TeamManager teamManager;

    public TeamCommand(Spellbreak plugin) {
        this.plugin = plugin;
        this.teamManager = plugin.getTeamManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /team create <teamname>");
                    return true;
                }
                teamManager.createTeam(player, args[1]);
                break;
            case "disband":
                teamManager.disbandTeam(player);
                break;
            case "invite":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /team invite <playername>");
                    return true;
                }
                Player targetToInvite = Bukkit.getPlayerExact(args[1]);
                if (targetToInvite == null) {
                    player.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found.");
                    return true;
                }
                String inviterTeam = teamManager.getPlayerTeamName(player.getUniqueId());
                if (inviterTeam == null) {
                    player.sendMessage(ChatColor.RED + "You are not in a team to invite players to.");
                    return true;
                }
                teamManager.invitePlayer(player, targetToInvite, inviterTeam);
                break;
            case "accept":
                String teamToAccept = args.length > 1 ? args[1] : null;
                teamManager.acceptInvite(player, teamToAccept);
                break;
            case "decline":
                teamManager.declineInvite(player);
                break;
            case "leave":
                teamManager.leaveTeam(player);
                break;
            case "list":
                 String teamToList = teamManager.getPlayerTeamName(player.getUniqueId());
                 if (teamToList == null && args.length < 2) {
                     player.sendMessage(ChatColor.RED + "You are not in a team. Usage: /team list [teamname]");
                     return true;
                 } else if (args.length >= 2) {
                     teamToList = args[1];
                 }
                 Set<UUID> members = teamManager.getTeamMembers(teamToList);
                 if (members == null || members.isEmpty()) {
                     player.sendMessage(ChatColor.RED + "Team '" + teamToList + "' not found or is empty.");
                     return true;
                 }
                 player.sendMessage(ChatColor.GOLD + "Members of team '" + teamToList + "':");
                 UUID leaderId = teamManager.isTeamLeader(player.getUniqueId(), teamToList) ? player.getUniqueId() : null; // A bit of a workaround to get actual leader if needed
                 // Actually, let's get the real leader from TeamManager if method exists, or assume first for now.
                 // For now, just list members.
                 String memberNames = members.stream()
                                          .map(uuid -> {
                                              Player p = Bukkit.getPlayer(uuid);
                                              return p != null ? p.getName() : "Offline Player";
                                          })
                                          .collect(Collectors.joining(", "));
                 player.sendMessage(ChatColor.YELLOW + memberNames);
                break;
            // case "kick": // Optional: /team kick <playername>
            //     if (args.length < 2) {
            //         player.sendMessage(ChatColor.RED + "Usage: /team kick <playername>");
            //         return true;
            //     }
            //     Player targetToKick = Bukkit.getPlayerExact(args[1]);
            //     // ... add kick logic to TeamManager and call here ...
            //     break;
            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Team Commands ---");
        player.sendMessage(ChatColor.AQUA + "/team create <name>" + ChatColor.GRAY + " - Creates a new team.");
        player.sendMessage(ChatColor.AQUA + "/team invite <player>" + ChatColor.GRAY + " - Invites a player to your team.");
        player.sendMessage(ChatColor.AQUA + "/team accept [name]" + ChatColor.GRAY + " - Accepts a team invite.");
        player.sendMessage(ChatColor.AQUA + "/team decline" + ChatColor.GRAY + " - Declines a pending team invite.");
        player.sendMessage(ChatColor.AQUA + "/team leave" + ChatColor.GRAY + " - Leaves your current team.");
        player.sendMessage(ChatColor.AQUA + "/team disband" + ChatColor.GRAY + " - Disbands your team (leader only).");
        player.sendMessage(ChatColor.AQUA + "/team list [name]" + ChatColor.GRAY + " - Lists members of your team or a specified team.");
    }
} 