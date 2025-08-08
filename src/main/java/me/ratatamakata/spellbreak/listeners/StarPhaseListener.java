package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.StarPhaseAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public class StarPhaseListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final StarPhaseAbility ability;

    public StarPhaseListener() {
        this.ability = (StarPhaseAbility)
                plugin.getAbilityManager().getAbilityByName("StarPhase");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (!(action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) return;

        Player p = e.getPlayer();
        String bound = plugin.getPlayerDataManager()
                .getAbilityAtSlot(p.getUniqueId(), p.getInventory().getHeldItemSlot());
        if (!"StarPhase".equalsIgnoreCase(bound)) return;
        if (!ability.getRequiredClass().equalsIgnoreCase(
                plugin.getPlayerDataManager().getPlayerClass(p.getUniqueId()))) return;

        e.setCancelled(true);

        // If already in StarPhase mode, launch projectile
        if (ability.isInStarPhase(p.getUniqueId())) {
            ability.activate(p);  // will launch projectile
            return;
        }

        // Initial activation: check cooldown & mana
        if (plugin.getCooldownManager().isOnCooldown(p, ability.getName())) {
            p.sendMessage(ChatColor.BLUE + "StarPhase on cooldown: "
                    + plugin.getCooldownManager().getRemainingCooldown(p, ability.getName()) + "s");
            return;
        }
        if (!plugin.getManaSystem().consumeMana(p, ability.getManaCost())) {
            p.sendMessage(ChatColor.BLUE + "Not enough mana for StarPhase! (Need " + ability.getManaCost() + ")");
            return;
        }

        ability.activate(p);
        plugin.getCooldownManager().setCooldown(p, ability.getName(), ability.getCooldown());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        // Clean up StarPhase data when player leaves
        Player p = e.getPlayer();
        if (ability.isInStarPhase(p.getUniqueId())) {
            ability.endStarPhase(p.getUniqueId());
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();

        // If player is in StarPhase mode and tries to stop flying, allow it
        // but keep flight capability enabled
        if (ability.isInStarPhase(p.getUniqueId())) {
            if (!e.isFlying()) {
                // Player landed, but keep flight enabled for StarPhase
                e.setCancelled(false);
                // Re-enable flight after a short delay to prevent issues
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && ability.isInStarPhase(p.getUniqueId())) {
                        p.setAllowFlight(true);
                    }
                }, 1L);
            }
        }
    }
}