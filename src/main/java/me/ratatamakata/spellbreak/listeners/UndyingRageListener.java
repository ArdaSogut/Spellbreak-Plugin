package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.abilities.impl.UndyingRageAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class UndyingRageListener implements Listener {

    private final Spellbreak plugin;

    public UndyingRageListener(Spellbreak plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR || !event.getPlayer().isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        int slot = player.getInventory().getHeldItemSlot();
        String boundAbilityName = plugin.getPlayerDataManager().getAbilityAtSlot(player.getUniqueId(), slot);

        if (!"UndyingRage".equalsIgnoreCase(boundAbilityName)) {
            return;
        }

        Ability ability = plugin.getAbilityManager().getAbilityByName(boundAbilityName);
        if (!(ability instanceof UndyingRageAbility)) {
            // This should ideally not happen if names match and registration is correct
            plugin.getLogger().warning("[UndyingRageListener] Ability found by name is not an instance of UndyingRageAbility.");
            return;
        }

        if (plugin.getCooldownManager().isOnCooldown(player, ability.getName())) {
            long remainingSeconds = plugin.getCooldownManager().getRemainingCooldown(player, ability.getName()) / 1000;
            player.sendMessage(ChatColor.RED + ability.getName() + " is on cooldown! (" + remainingSeconds + "s)");
            return;
        }

        if (!plugin.getManaSystem().consumeMana(player, ability.getManaCost())) {
            player.sendMessage(ChatColor.RED + "Not enough mana for " + ability.getName() + ".");
            return;
        }

        ability.activate(player);
        plugin.getCooldownManager().setCooldown(player, ability.getName(), ability.getCooldown());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDealDamageToRestoreHealth(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        Player player = (Player) event.getDamager();
        if (player.hasMetadata(UndyingRageAbility.ACTIVE_METADATA_KEY)) {
            UndyingRageAbility.tryHealOnHit(player, event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) // Highest to check health before other plugins potentially cancel/modify death
    public void onPlayerTakeDamageCheckExplosion(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();

        if (player.hasMetadata(UndyingRageAbility.ACTIVE_METADATA_KEY) && 
            player.hasMetadata(UndyingRageAbility.ORIGINAL_MAX_HEALTH_METADATA_KEY) &&
            !player.getMetadata(UndyingRageAbility.EXPLOSION_TRIGGERED_METADATA_KEY).get(0).asBoolean()) {
            
            double originalMaxHealth = player.getMetadata(UndyingRageAbility.ORIGINAL_MAX_HEALTH_METADATA_KEY).get(0).asDouble();
            double healthAfterDamage = player.getHealth() - event.getFinalDamage();

            if (healthAfterDamage <= originalMaxHealth) {
                UndyingRageAbility.triggerExplosion(player);
                // The main runnable in UndyingRageAbility will handle full cleanup when it notices the EXPLOSION_TRIGGERED_METADATA_KEY flag
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasMetadata(UndyingRageAbility.ACTIVE_METADATA_KEY)) {
            UndyingRageAbility.cleanupAbilityState(player, true); // True to restore health state fully
        }
    }
} 