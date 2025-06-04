package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeamManager {
    private final Spellbreak plugin;
    // Team Name -> Set of Member UUIDs
    private final Map<String, Set<UUID>> teams = new ConcurrentHashMap<>();
    // Player UUID -> Team Name they are in
    private final Map<UUID, String> playerTeam = new ConcurrentHashMap<>();
    // Team Name -> Leader UUID
    private final Map<String, UUID> teamLeaders = new ConcurrentHashMap<>();
    // Invited Player UUID -> Team Name they were invited to
    private final Map<UUID, String> pendingInvites = new ConcurrentHashMap<>();

    public TeamManager(Spellbreak plugin) {
        this.plugin = plugin;
    }

    public boolean createTeam(Player leader, String teamName) {
        if (teamName == null || teamName.trim().isEmpty() || teamName.length() > 16) {
            leader.sendMessage(ChatColor.RED + "Team name must be 1-16 characters.");
            return false;
        }
        if (teams.containsKey(teamName.toLowerCase())) {
            leader.sendMessage(ChatColor.RED + "A team with that name already exists.");
            return false;
        }
        if (playerTeam.containsKey(leader.getUniqueId())) {
            leader.sendMessage(ChatColor.RED + "You are already in a team. Leave your current team first.");
            return false;
        }

        String teamKey = teamName.toLowerCase();
        Set<UUID> members = new HashSet<>();
        members.add(leader.getUniqueId());
        teams.put(teamKey, members);
        playerTeam.put(leader.getUniqueId(), teamKey);
        teamLeaders.put(teamKey, leader.getUniqueId());
        leader.sendMessage(ChatColor.GREEN + "Team '" + teamName + "' created!");
        return true;
    }

    public boolean invitePlayer(Player inviter, Player targetPlayer, String teamName) {
        String teamKey = teamName.toLowerCase();
        if (!isTeamLeader(inviter.getUniqueId(), teamKey)) {
            inviter.sendMessage(ChatColor.RED + "Only the team leader can invite players.");
            return false;
        }
        if (targetPlayer == null) {
            inviter.sendMessage(ChatColor.RED + "Player not found.");
            return false;
        }
        if (playerTeam.containsKey(targetPlayer.getUniqueId())) {
            inviter.sendMessage(ChatColor.RED + targetPlayer.getName() + " is already in a team.");
            return false;
        }
        if (pendingInvites.containsKey(targetPlayer.getUniqueId())) {
             inviter.sendMessage(ChatColor.RED + targetPlayer.getName() + " already has a pending invite.");
             targetPlayer.sendMessage(ChatColor.YELLOW + "You already have a pending invite. Type /team accept or /team decline.");
             return false;
        }

        pendingInvites.put(targetPlayer.getUniqueId(), teamKey);
        inviter.sendMessage(ChatColor.GREEN + "Invited " + targetPlayer.getName() + " to team '" + teamName + "'.");
        targetPlayer.sendMessage(ChatColor.AQUA + inviter.getName() + " has invited you to join team '" + teamName + "'. Type " + ChatColor.GOLD + "/team accept " + teamName + ChatColor.AQUA + " or " + ChatColor.GOLD + "/team decline" + ChatColor.AQUA + " to respond.");
        
        // Optional: Invite timeout
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingInvites.containsKey(targetPlayer.getUniqueId()) && pendingInvites.get(targetPlayer.getUniqueId()).equals(teamKey)) {
                pendingInvites.remove(targetPlayer.getUniqueId());
                targetPlayer.sendMessage(ChatColor.YELLOW + "Your invite to team '" + teamName + "' has expired.");
                Player currentInviter = Bukkit.getPlayer(inviter.getUniqueId());
                if(currentInviter != null && currentInviter.isOnline()){
                    currentInviter.sendMessage(ChatColor.YELLOW + targetPlayer.getName() + "'s invite to team '" + teamName + "' has expired.");
                }
            }
        }, 20L * 60); // 60 seconds invite timeout
        return true;
    }

    public boolean acceptInvite(Player player, String teamNameToJoin) {
        if (teamNameToJoin == null) { // Generic accept if only one invite
            if (pendingInvites.containsKey(player.getUniqueId())) {
                teamNameToJoin = pendingInvites.get(player.getUniqueId());
            } else {
                player.sendMessage(ChatColor.RED + "You have no pending invites.");
                return false;
            }
        }
        String teamKey = teamNameToJoin.toLowerCase();
        if (!pendingInvites.containsKey(player.getUniqueId()) || !pendingInvites.get(player.getUniqueId()).equals(teamKey)) {
            player.sendMessage(ChatColor.RED + "You don't have an invite to team '" + teamNameToJoin + "'." + (pendingInvites.containsKey(player.getUniqueId()) ? " Did you mean '" + pendingInvites.get(player.getUniqueId()) + "'?" : ""));
            return false;
        }
        if (playerTeam.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already in a team. Leave your current team first.");
            pendingInvites.remove(player.getUniqueId()); // Remove invite as it can't be accepted now
            return false;
        }
        if (!teams.containsKey(teamKey)) {
            player.sendMessage(ChatColor.RED + "Team '" + teamNameToJoin + "' no longer exists.");
            pendingInvites.remove(player.getUniqueId());
            return false;
        }

        pendingInvites.remove(player.getUniqueId());
        teams.get(teamKey).add(player.getUniqueId());
        playerTeam.put(player.getUniqueId(), teamKey);
        broadcastToTeam(teamKey, ChatColor.AQUA + player.getName() + " has joined the team!");
        return true;
    }

    public boolean declineInvite(Player player) {
        if (!pendingInvites.containsKey(player.getUniqueId())){
            player.sendMessage(ChatColor.RED + "You have no pending invites to decline.");
            return false;
        }
        String teamKey = pendingInvites.remove(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + "You have declined the invite to team '" + teamKey + "'.");
        UUID leaderId = teamLeaders.get(teamKey);
        if(leaderId != null){
            Player leader = Bukkit.getPlayer(leaderId);
            if(leader != null && leader.isOnline()){
                leader.sendMessage(ChatColor.YELLOW + player.getName() + " has declined the invite to your team.");
            }
        }
        return true;
    }

    public boolean leaveTeam(Player player) {
        UUID playerId = player.getUniqueId();
        if (!playerTeam.containsKey(playerId)) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return false;
        }

        String teamKey = playerTeam.remove(playerId);
        Set<UUID> members = teams.get(teamKey);
        if (members != null) {
            members.remove(playerId);
            broadcastToTeam(teamKey, ChatColor.YELLOW + player.getName() + " has left the team.");
            if (members.isEmpty()) {
                teams.remove(teamKey);
                teamLeaders.remove(teamKey);
                plugin.getLogger().info("Team '" + teamKey + "' disbanded as it became empty.");
            } else if (teamLeaders.getOrDefault(teamKey, null).equals(playerId)) {
                // If leader leaves, assign a new leader (e.g., first in list, or oldest member)
                UUID newLeader = members.stream().findFirst().orElse(null);
                if (newLeader != null) {
                    teamLeaders.put(teamKey, newLeader);
                    Player newLeaderPlayer = Bukkit.getPlayer(newLeader);
                    if(newLeaderPlayer != null) {
                         broadcastToTeam(teamKey, ChatColor.GOLD + newLeaderPlayer.getName() + " is now the new team leader.");
                    }
                } else { // Should not happen if members is not empty
                    teams.remove(teamKey);
                    teamLeaders.remove(teamKey);
                }
            }
        }
        player.sendMessage(ChatColor.GREEN + "You have left team '" + teamKey + "'.");
        return true;
    }

    public boolean disbandTeam(Player player) {
        UUID playerId = player.getUniqueId();
        String teamKey = playerTeam.get(playerId);

        if (teamKey == null) {
            player.sendMessage(ChatColor.RED + "You are not in a team.");
            return false;
        }
        if (!isTeamLeader(playerId, teamKey)) {
            player.sendMessage(ChatColor.RED + "Only the team leader can disband the team.");
            return false;
        }

        Set<UUID> members = teams.remove(teamKey);
        if (members != null) {
            for (UUID memberId : members) {
                playerTeam.remove(memberId);
                Player memberPlayer = Bukkit.getPlayer(memberId);
                if (memberPlayer != null && memberPlayer.isOnline()) {
                    memberPlayer.sendMessage(ChatColor.GOLD + "Team '" + teamKey + "' has been disbanded by the leader.");
                }
            }
        }
        teamLeaders.remove(teamKey);
        player.sendMessage(ChatColor.GREEN + "Team '" + teamKey + "' disbanded.");
        return true;
    }

    public boolean arePlayersInSameTeam(UUID player1Id, UUID player2Id) {
        if (player1Id.equals(player2Id)) return true; // Players are always on their own "team" for self-targeting if logic allows
        String team1 = playerTeam.get(player1Id);
        String team2 = playerTeam.get(player2Id);
        return team1 != null && team1.equals(team2);
    }
    
    public boolean isTeamLeader(UUID playerId, String teamKey) {
        return teamLeaders.getOrDefault(teamKey.toLowerCase(), null).equals(playerId);
    }

    public String getPlayerTeamName(UUID playerId) {
        return playerTeam.get(playerId);
    }

    public Set<UUID> getTeamMembers(String teamName) {
        return teams.get(teamName.toLowerCase());
    }

    public void broadcastToTeam(String teamKey, String message) {
        Set<UUID> members = teams.get(teamKey.toLowerCase());
        if (members != null) {
            for (UUID memberId : members) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.sendMessage(ChatColor.DARK_AQUA + "[Team] " + ChatColor.RESET + message);
                }
            }
        }
    }

    // Method to be called when a player quits, to clean them up from teams/invites
    public void handlePlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        pendingInvites.remove(playerId); // Remove any pending invites for this player

        String teamKey = playerTeam.get(playerId);
        if (teamKey != null) {
            Set<UUID> members = teams.get(teamKey);
            if (members != null) {
                members.remove(playerId);
                 broadcastToTeam(teamKey, ChatColor.YELLOW + player.getName() + " has left the team (disconnected).");
                if (members.isEmpty()) {
                    teams.remove(teamKey);
                    teamLeaders.remove(teamKey);
                    plugin.getLogger().info("Team '" + teamKey + "' auto-disbanded as it became empty due to disconnect.");
                } else if (teamLeaders.getOrDefault(teamKey, null).equals(playerId)) {
                    UUID newLeader = members.stream().findFirst().orElse(null);
                    if (newLeader != null) {
                        teamLeaders.put(teamKey, newLeader);
                        Player newLeaderPlayer = Bukkit.getPlayer(newLeader);
                        if(newLeaderPlayer != null && newLeaderPlayer.isOnline()) {
                            broadcastToTeam(teamKey, ChatColor.GOLD + newLeaderPlayer.getName() + " is now the new team leader (previous leader disconnected).");
                        }
                    }
                }
            }
            playerTeam.remove(playerId); // Ensure player is removed from the playerTeam map too
        }
    }
} 