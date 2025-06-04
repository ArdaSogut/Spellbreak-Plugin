// Enhanced EchoPulseListener.java with level system integration
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.EchoPulseAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class EchoPulseListener implements Listener {
    private final Spellbreak plugin;

    public EchoPulseListener(Spellbreak plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLeftClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        int slot = p.getInventory().getHeldItemSlot();
        String abilityName = plugin.getPlayerDataManager().getAbilityAtSlot(p.getUniqueId(), slot);
        if (!"EchoPulse".equalsIgnoreCase(abilityName)) return;

        EchoPulseAbility ability = (EchoPulseAbility) plugin.getAbilityManager().getAbilityByName(abilityName);
        if (ability == null) return;

        // Use level-adjusted cooldown and mana cost
        int adjustedCooldown = ability.getAdjustedCooldown(p);
        int adjustedManaCost = ability.getAdjustedManaCost(p);

        if (plugin.getCooldownManager().isOnCooldown(p, abilityName)) {
            int remainingCooldown = plugin.getCooldownManager().getRemainingCooldown(p, abilityName);
            p.sendMessage(ChatColor.GOLD + "Echo Pulse is on cooldown! (" + remainingCooldown + "s remaining)");
            return;
        }

        if (!plugin.getManaSystem().consumeMana(p, adjustedManaCost)) {
            p.sendMessage(ChatColor.GOLD + "Not enough mana for Echo Pulse! (Need " + adjustedManaCost + " mana)");
            return;
        }

        ability.activate(p);
        plugin.getCooldownManager().setCooldown(p, abilityName, adjustedCooldown);

        // Give player XP for successfully casting
       // plugin.getLevelManager().givePlayerExperience(p, 5, "Spell Cast");
    }
}