package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class AbilityHUDUpdater extends BukkitRunnable {
    @Override
    public void run() {
        for(Player player : Bukkit.getOnlinePlayers()) {
            updateHUD(player);
        }
    }

    private void updateHUD(Player player) {
        StringBuilder hud = new StringBuilder();
        ManaSystem manaSystem = Spellbreak.getInstance().getManaSystem();
        PlayerDataManager playerDataManager = Spellbreak.getInstance().getPlayerDataManager();
        CooldownManager cooldownManager = Spellbreak.getInstance().getCooldownManager();
        AbilityManager abilityManager = Spellbreak.getInstance().getAbilityManager();

        // Mana display
        int mana = manaSystem.getMana(player);
        int maxMana = manaSystem.getMaxMana(player.getPlayer());
        hud.append("§b⛊ §f").append(mana).append("/").append(maxMana).append("   ");

        // Ability slots
        String[] abilities = playerDataManager.getBindings(player.getUniqueId());

        for (int i = 0; i < 9; i++) {
            String abilityName = abilities[i];
            String displayName = abilityName != null ? abilityName : "§8-";
            
            Ability ability = abilityName != null ? abilityManager.getAbilityByName(abilityName) : null;
            
            hud.append(formatSlot(i + 1, displayName, ability, player, cooldownManager));
            if (i < 8) hud.append(" §7| ");
        }

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(hud.toString()));
    }

    private String formatSlot(int slot, String displayName, Ability ability, Player player, CooldownManager cooldownManager) {
        if (ability == null || displayName.equals("§8-")) {
            return "§7[" + slot + "]§8 -"; // Grayed out empty slot
        }

        int globalCooldown = cooldownManager.getRemainingCooldown(player, ability.getName());
        int maxCharges = ability.getMaxCharges();
        
        String prefix = "§7[" + slot + "] ";
        String suffix = "";
        String color = "§a"; // Default available color (green)

        if (globalCooldown > 0) {
            color = "§c"; // On cooldown color (red)
            suffix = " §c(" + globalCooldown + "s)";
        } else if (maxCharges > 0) {
            // Ability uses charges and is off global cooldown
            int currentCharges = ability.getCurrentCharges(player);
            color = (currentCharges > 0) ? "§a" : "§e"; // Green if charges > 0, Yellow if 0 charges
            suffix = " §f(" + currentCharges + "/" + maxCharges + ")";
            // Optional: If currentCharges < maxCharges, indicate regenerating?
             // if (currentCharges < maxCharges) suffix += " §e⌛"; 
        }

        return prefix + color + displayName + suffix;
    }
}