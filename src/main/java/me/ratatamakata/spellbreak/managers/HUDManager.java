package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.PlayerLevel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashSet;
import java.util.Set;

public class HUDManager {
    private final ScoreboardManager scoreboardManager;
    private final ManaSystem manaSystem = Spellbreak.getInstance().getManaSystem();
    private final PlayerDataManager playerDataManager = Spellbreak.getInstance().getPlayerDataManager();
    private final CooldownManager cooldownManager = Spellbreak.getInstance().getCooldownManager();
    private final AbilityManager abilityManager = Spellbreak.getInstance().getAbilityManager();
    private final Spellbreak plugin = Spellbreak.getInstance();

    public HUDManager() {
        this.scoreboardManager = Bukkit.getScoreboardManager();
    }

    public void updateHUD(Player player) {
        PlayerLevel plevel = plugin.getLevelManager().getPlayerLevel(player.getUniqueId(), playerDataManager.getPlayerClass(player.getUniqueId()));
        Scoreboard board = player.getScoreboard();
        if (board == null || board == scoreboardManager.getMainScoreboard()) {
            board = scoreboardManager.getNewScoreboard();
        }

        Objective obj = board.getObjective("spellbreak");
        if (obj == null) {
            obj = board.registerNewObjective("spellbreak", "dummy",
                    ChatColor.GOLD + "" + ChatColor.BOLD + "Spellbreak");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        int currentSlot = player.getInventory().getHeldItemSlot();
        int mana = manaSystem.getMana(player);
        int maxMana = manaSystem.getMaxMana(player); // Updated to player-specific max mana
        double health = Math.ceil(player.getHealth());
        int maxHealth = plevel.getMaxHealth(); // Get from PlayerLevel

        String playerClass = playerDataManager.getPlayerClass(player.getUniqueId());
        if (playerClass != null && !playerClass.isEmpty()) {
            playerClass = playerClass.substring(0, 1).toUpperCase() + playerClass.substring(1).toLowerCase();
        }

        Set<String> currentEntries = new HashSet<>();

        // Add health display
        String healthEntry = ChatColor.RED + "❤ Health: " + (int) health + "/" + maxHealth;
        obj.getScore(healthEntry).setScore(11);
        currentEntries.add(healthEntry);

        // Add class display
        String classEntry = ChatColor.LIGHT_PURPLE + "Class: " + playerClass;
        obj.getScore(classEntry).setScore(10);
        currentEntries.add(classEntry);

        // Updated mana display with player-specific max
        String manaEntry = ChatColor.BLUE + "⛊ Mana: " + mana + "/" + maxMana;
        obj.getScore(manaEntry).setScore(9);
        currentEntries.add(manaEntry);

        // Add level display
        String levelEntry = ChatColor.YELLOW + "★ Level: " + plevel.getLevel();
        obj.getScore(levelEntry).setScore(8);
        currentEntries.add(levelEntry);

        String[] abilities = playerDataManager.getBindings(player.getUniqueId());

        for(int i = 0; i < 9; i++) {
            String abilityName = abilities[i];
            Ability ability = abilityName != null ? abilityManager.getAbilityByName(abilityName) : null;
            boolean isActiveSlot = (i == currentSlot);

            String formattedEntry = formatSlot(i + 1, ability, player, cooldownManager, isActiveSlot);
            obj.getScore(formattedEntry).setScore(7 - i); // Adjusted score position
            currentEntries.add(formattedEntry);
        }

        // Clean up old entries
        for (String entry : new HashSet<>(board.getEntries())) {
            Score score = obj.getScore(entry);
            if (score != null && score.getObjective() != null && score.getObjective().equals(obj)) {
                if (!currentEntries.contains(entry)) {
                    board.resetScores(entry);
                }
            }
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    private String formatSlot(int slot, Ability ability, Player player, CooldownManager cooldownManager, boolean isActiveSlot) {
        String slotPrefix = ChatColor.GRAY + "[" + slot + "] ";
        String indent = isActiveSlot ? "  " : "";

        if (ability == null) {
            return indent + slotPrefix + ChatColor.DARK_GRAY + "Empty";
        }

        String displayName = ability.getName();
        int globalCooldown = cooldownManager.getRemainingCooldown(player, displayName);
        int maxCharges = ability.getMaxCharges();

        String suffix = "";
        String color = ChatColor.GREEN.toString();

        if (globalCooldown > 0) {
            color = ChatColor.RED.toString();
            suffix = " " + ChatColor.RED + "(" + globalCooldown + "s)";
        } else if (maxCharges > 0) {
            int currentCharges = ability.getCurrentCharges(player);
            color = (currentCharges > 0) ? ChatColor.GREEN.toString() : ChatColor.YELLOW.toString();
            suffix = " " + ChatColor.WHITE + "(" + currentCharges + "/" + maxCharges + ")";
        }

        return indent + slotPrefix + color + displayName + suffix;
    }
}