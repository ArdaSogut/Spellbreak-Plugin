// EarthShardsListener.java
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.EarthShardsAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class EarthShardsListener implements Listener {
    private final Spellbreak plugin;
    private final EarthShardsAbility ability;

    public EarthShardsListener(Spellbreak plugin) {
        this.plugin = plugin;
        this.ability = (EarthShardsAbility) plugin.getAbilityManager().getAbilityByName("EarthShards");
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent e) {
        Player player = e.getPlayer();
        int slot = player.getInventory().getHeldItemSlot();
        int cooldown = ability.getAdjustedCooldown(player);
        int mana = ability.getAdjustedManaCost(player);
        String abilityName = plugin.getPlayerDataManager().getAbilityAtSlot(player.getUniqueId(), slot);
        if (!"EarthShards".equalsIgnoreCase(abilityName)) return;

        if (e.isSneaking()) {
            if (plugin.getCooldownManager().isOnCooldown(player, abilityName)) {
                player.sendMessage(ChatColor.GOLD + "EarthShards is on cooldown!");
                e.setCancelled(true);
                return;
            }
            if (!plugin.getManaSystem().consumeMana(player, mana)) {
                player.sendMessage(ChatColor.GOLD + "Not enough mana for EarthShards!");
                e.setCancelled(true);
                return;
            }
            ability.startCharging(player);
            plugin.getCooldownManager().setCooldown(player, abilityName, cooldown);
        } else {
            ability.stopCharging(player);
        }
    }

    @EventHandler
    public void onLeftClick(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        int slot = player.getInventory().getHeldItemSlot();
        String abilityName = plugin.getPlayerDataManager().getAbilityAtSlot(player.getUniqueId(), slot);
        if (!"EarthShards".equalsIgnoreCase(abilityName)) return;

        ability.shootShard(player);
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.FallingBlock)) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent e) {
        Player player = e.getPlayer();
        // If the ability was in the previous slot, clear shards
        int prevSlot = e.getPreviousSlot();
        String prevAbility = plugin.getPlayerDataManager().getAbilityAtSlot(player.getUniqueId(), prevSlot);
        if ("EarthShards".equalsIgnoreCase(prevAbility)) {
            ability.clearShards(player);
        }
    }
}
