package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.DreamwalkerAbility;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.UUID;

public class DreamwalkerListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final CooldownManager cd = plugin.getCooldownManager();
    private final ManaSystem mana = plugin.getManaSystem();
    private final PlayerDataManager pdm = plugin.getPlayerDataManager();

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action action = e.getAction();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (p.isSneaking()) {
                // Handle cancellation first
                if (DreamwalkerAbility.activePlayers.contains(p.getUniqueId())) {
                    e.setCancelled(true);
                    DreamwalkerAbility.endAbility(p);
                    p.sendMessage(ChatColor.LIGHT_PURPLE + "Returned to physical form early!");
                    return;
                }

                // Handle activation
                int slot = p.getInventory().getHeldItemSlot();
                String bound = pdm.getAbilityAtSlot(p.getUniqueId(), slot);
                if ("Dreamwalker".equalsIgnoreCase(bound)) {
                    e.setCancelled(true);
                    handleActivation(p, bound);
                }
            }
        }
    }

    private void handleActivation(Player p, String bound) {
        DreamwalkerAbility ability = (DreamwalkerAbility) plugin.getAbilityManager().getAbilityByName(bound);
        if (cd.isOnCooldown(p, bound)) {
            p.sendMessage(ChatColor.RED + "Dreamwalker on cooldown: " + cd.getRemainingCooldown(p, bound) + "s");
            return;
        }
        if (!mana.consumeMana(p, ability.getManaCost())) {
            p.sendMessage(ChatColor.RED + "Not enough mana for Dreamwalker.");
            return;
        }
        ability.activate(p);
        if (ability.isSuccessful()) {
            cd.setCooldown(p, bound, ability.getCooldown());
            p.sendMessage(ChatColor.LIGHT_PURPLE + "Your consciousness phases into the dream realm!");
        } else {
            mana.restoreMana(p, ability.getManaCost());
        }
    }

    @EventHandler
    public void onDamageIn(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && DreamwalkerAbility.activePlayers.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageOut(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            if (DreamwalkerAbility.activePlayers.contains(p.getUniqueId())) {
                if (!p.hasMetadata("DREAMWALKER_DAMAGE")) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (DreamwalkerAbility.activePlayers.contains(p.getUniqueId())) {
            if (e.getCause() == TeleportCause.SPECTATE) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        DreamwalkerAbility.activePlayers.remove(uuid);
        DreamwalkerAbility.initialYLevels.remove(uuid);
        DreamwalkerAbility.activeTasks.remove(uuid);

        GameMode previous = DreamwalkerAbility.previousGamemodes.remove(uuid);
        if (previous != null) {
            p.setGameMode(previous);
        }

        p.setInvulnerable(false);
        p.setCollidable(true);
    }
}