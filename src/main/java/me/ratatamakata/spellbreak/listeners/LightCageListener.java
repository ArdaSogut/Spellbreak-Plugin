// LightCageListener.java
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.LightCageAbility;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class LightCageListener implements Listener {
    private final Spellbreak plugin;

    public LightCageListener(Spellbreak plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLeftClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        int slot = p.getInventory().getHeldItemSlot();
        String abilityName = plugin.getPlayerDataManager().getAbilityAtSlot(p.getUniqueId(), slot);
        if (!"LightCage".equalsIgnoreCase(abilityName)) return;

        LightCageAbility ability = (LightCageAbility) plugin.getAbilityManager().getAbilityByName(abilityName);
        if (ability == null) return;

        if (plugin.getCooldownManager().isOnCooldown(p, abilityName)) {
            p.sendMessage(org.bukkit.ChatColor.GOLD + "Light Cage is on cooldown!");
            return;
        }

        if (!plugin.getManaSystem().consumeMana(p, ability.getManaCost())) {
            p.sendMessage(org.bukkit.ChatColor.GOLD + "Not enough mana for Light Cage!");
            return;
        }

        ability.activate(p);
        plugin.getCooldownManager().setCooldown(p, abilityName, ability.getCooldown());
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile projectile = e.getEntity();
        if (!(projectile instanceof Snowball) || !projectile.hasMetadata("LightCage")) return;

        LightCageAbility ability = (LightCageAbility) plugin.getAbilityManager().getAbilityByName("LightCage");
        if (ability == null || !(projectile.getShooter() instanceof Player)) return;

        ability.createCage(projectile.getLocation(), (Player) projectile.getShooter());
    }
}