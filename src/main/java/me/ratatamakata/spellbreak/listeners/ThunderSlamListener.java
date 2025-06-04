package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.ThunderSlamAbility;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class ThunderSlamListener implements Listener {
    private final Spellbreak plugin;
    private final ThunderSlamAbility ability;
    private final PlayerDataManager playerData;
    private final CooldownManager cooldowns;
    private final ManaSystem mana;

    public ThunderSlamListener(Spellbreak plugin, ThunderSlamAbility ability) {
        this.plugin = plugin;
        this.ability = ability;
        this.playerData = plugin.getPlayerDataManager();
        this.cooldowns = plugin.getCooldownManager();
        this.mana = plugin.getManaSystem();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (e.isSneaking() && this.isHoldingAbility(p)) {
            int adjustedCooldown = this.ability.getAdjustedCooldown(p);
            int adjustedManaCost = this.ability.getAdjustedManaCost(p);
            if (this.cooldowns.isOnCooldown(p, this.ability.getName())) {
                String var10001 = String.valueOf(ChatColor.BLUE);
                p.sendMessage(var10001 + "ThunderSlam ready in: " + this.cooldowns.getRemainingCooldown(p, this.ability.getName()) + "s");
            } else if (!this.mana.consumeMana(p, adjustedManaCost)) {
                p.sendMessage(String.valueOf(ChatColor.RED) + "Not enough mana!");
            } else {
                this.ability.activate(p);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (this.isHoldingAbility(p)) {
            if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
                if (this.ability.isCharging(p)) {
                    this.ability.slamPlayer(p);
                    int adjustedCooldown = this.ability.getAdjustedCooldown(p);
                    this.cooldowns.setCooldown(p, this.ability.getName(), adjustedCooldown);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.cleanupPlayer(e.getPlayer());
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) {
            this.cleanupPlayer((Player)e.getEntity());
        }

    }

    private void cleanupPlayer(Player p) {
        if (this.ability.isCharging(p)) {
            this.ability.disableChargeMode(p);
        }

    }

    private boolean isHoldingAbility(Player p) {
        String currentAbility = this.playerData.getAbilityAtSlot(p.getUniqueId(), p.getInventory().getHeldItemSlot());
        return this.ability.getName().equalsIgnoreCase(currentAbility) && this.ability.getRequiredClass().equalsIgnoreCase(this.playerData.getPlayerClass(p.getUniqueId()));
    }
}

