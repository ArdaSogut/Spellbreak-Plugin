package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.GaleVortexAbility;
import me.ratatamakata.spellbreak.managers.*;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

public class GaleVortexListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final CooldownManager cd = plugin.getCooldownManager();
    private final ManaSystem mana = plugin.getManaSystem();
    private final PlayerDataManager pdm = plugin.getPlayerDataManager();
    private final GaleVortexAbility ability;

    public GaleVortexListener() {
        this.ability = (GaleVortexAbility) plugin.getAbilityManager().getAbilityByName("GaleVortex");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action action = e.getAction();
        // Only react to left clicks
        if (!(action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) return;

        int slot = p.getInventory().getHeldItemSlot();
        String bound = pdm.getAbilityAtSlot(p.getUniqueId(), slot);
        if (!"GaleVortex".equalsIgnoreCase(bound)) return;
        if (!ability.getRequiredClass().equalsIgnoreCase(pdm.getPlayerClass(p.getUniqueId()))) return;

        int adjustedCooldown = ability.getAdjustedCooldown(p);
        int adjustedManaCost = ability.getAdjustedManaCost(p);
        if (cd.isOnCooldown(p, "GaleVortex")) {
            p.sendMessage(ChatColor.GRAY + "Gale Vortex on cooldown: " + cd.getRemainingCooldown(p, "GaleVortex") + "s");
            return;
        }
        if (!mana.consumeMana(p, adjustedManaCost)) {
            p.sendMessage(ChatColor.GRAY + "Not enough mana for Gale Vortex! (Need " + adjustedManaCost + ")");
            return;
        }

        // Activate the vortex
        ability.activate(p);
        cd.setCooldown(p, "GaleVortex", adjustedCooldown);

        // Visual cue on the player
        p.getWorld().spawnParticle(
                org.bukkit.Particle.CLOUD,
                p.getLocation().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.2
        );
        p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WHIRL, 0.7f, 1.8f);
    }
}
