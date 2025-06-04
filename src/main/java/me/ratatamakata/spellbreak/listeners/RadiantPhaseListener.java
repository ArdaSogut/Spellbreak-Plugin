package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.RadiantPhaseAbility;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class RadiantPhaseListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final CooldownManager cd = plugin.getCooldownManager();
    private final ManaSystem mana = plugin.getManaSystem();
    private final PlayerDataManager pdm = plugin.getPlayerDataManager();

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!(e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK)) return;
        if (!p.isSneaking()) return;
        int slot = p.getInventory().getHeldItemSlot();
        String bound = pdm.getAbilityAtSlot(p.getUniqueId(), slot);
        if (!"RadiantPhase".equalsIgnoreCase(bound)) return;
        e.setCancelled(true);
        RadiantPhaseAbility ability = (RadiantPhaseAbility) plugin.getAbilityManager().getAbilityByName(bound);
        if (cd.isOnCooldown(p, bound)) {
            p.sendMessage(ChatColor.RED + "Radiant Phase on cooldown: " + cd.getRemainingCooldown(p, bound) + "s");
            return;
        }
        if (!mana.consumeMana(p, ability.getManaCost())) {
            p.sendMessage(ChatColor.RED + "Not enough mana for Radiant Phase.");
            return;
        }
        ability.activate(p);
        if (ability.isSuccessful()) {
            cd.setCooldown(p, bound, ability.getCooldown());
            p.sendMessage(ChatColor.GREEN + "Radiant Phase activated!");
        } else {
            mana.restoreMana(p, ability.getManaCost());
        }
    }

    @EventHandler
    public void onDamageIn(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && RadiantPhaseAbility.activePlayers.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageOut(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p
                && RadiantPhaseAbility.activePlayers.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        RadiantPhaseAbility.activePlayers.remove(e.getPlayer().getUniqueId());
    }
}
