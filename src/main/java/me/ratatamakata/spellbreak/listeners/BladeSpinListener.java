// File: src/main/java/me/ratatamakata/spellbreak/listeners/BladeSpinListener.java
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.BladeSpinAbility;
import me.ratatamakata.spellbreak.abilities.impl.BladeSpinAbility.SpinData;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class BladeSpinListener implements Listener {

    private final Spellbreak plugin = Spellbreak.getInstance();
    private final CooldownManager cd = plugin.getCooldownManager();
    private final ManaSystem mana = plugin.getManaSystem();
    private final PlayerDataManager pdm = plugin.getPlayerDataManager();
    private final BladeSpinAbility ability;

    public BladeSpinListener() {
        this.ability = (BladeSpinAbility) plugin.getAbilityManager().getAbilityByName("BladeSpin");
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        int cd2 = ability.getAdjustedCooldown(p);
        int manaCost = ability.getAdjustedManaCost(p);
        // Only trigger when starting to sneak (shift down)
        if (!e.isSneaking()) return;

        String bound = pdm.getAbilityAtSlot(p.getUniqueId(), p.getInventory().getHeldItemSlot());
        if (!"BladeSpin".equalsIgnoreCase(bound)) return;

        // Check class requirement
        if (!ability.getRequiredClass().equalsIgnoreCase(pdm.getPlayerClass(p.getUniqueId()))) {
            p.sendMessage(ChatColor.RED + "You must be a " +
                    ChatColor.YELLOW + ability.getRequiredClass() +
                    ChatColor.RED + " to use this ability!");
            return;
        }

        // Check cooldown
        if (cd.isOnCooldown(p, "BladeSpin")) {
            p.sendMessage(ChatColor.RED + "BladeSpin on cooldown: " +
                    cd.getRemainingCooldown(p, "BladeSpin") + "s");
            return;
        }

        // Check mana
        if (!mana.consumeMana(p, manaCost)) {
            p.sendMessage(ChatColor.RED + "Not enough mana for BladeSpin!");
            return;
        }

        // Activate ability
        ability.activate(p);

        // Set cooldown
        cd.setCooldown(p, "BladeSpin", cd2);

        // Prevent default sneak action from interfering
        e.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        // Only handle left clicks
        if (e.getAction() != Action.LEFT_CLICK_AIR &&
                e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player p = e.getPlayer();

        // Check if player has active BladeSpin
        BladeSpinAbility.SpinData data = BladeSpinAbility.getActiveSpin(p.getUniqueId());
        if (data == null) return;

        // Execute dash and prevent other interactions
        data.doDash();
        e.setCancelled(true);
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        BladeSpinAbility.removeSpin(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        BladeSpinAbility.removeSpin(e.getEntity().getUniqueId());
    }
}
