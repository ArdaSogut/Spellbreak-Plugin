
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.CloneSwarmAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class CloneSwarmListener implements Listener {

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Player p = e.getPlayer();
        if (!isAbilityActive(p)) return;

        CloneSwarmAbility ability = (CloneSwarmAbility) Spellbreak.getInstance()
                .getAbilityManager().getAbilityByName("CloneSwarm");
        if (ability == null) return;

        // Use level-adjusted cooldown
        int adjustedCooldown = ability.getAdjustedCooldown(p);
        if (Spellbreak.getInstance().getCooldownManager().isOnCooldown(p, ability.getName())) {
            int remainingCooldown = Spellbreak.getInstance().getCooldownManager().getRemainingCooldown(p, ability.getName());
            p.sendMessage(ChatColor.GOLD + "Clone Swarm is on cooldown! (" + remainingCooldown + "s remaining)");
            return;
        }

        ability.activate(p);
    }

    private boolean isAbilityActive(Player p) {
        String ability = Spellbreak.getInstance().getPlayerDataManager()
                .getAbilityAtSlot(p.getUniqueId(), p.getInventory().getHeldItemSlot());
        return "CloneSwarm".equalsIgnoreCase(ability);
    }
}