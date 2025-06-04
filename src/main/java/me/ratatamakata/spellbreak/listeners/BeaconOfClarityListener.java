package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.managers.AbilityManager;
import me.ratatamakata.spellbreak.abilities.impl.BeaconOfClarityAbility;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BeaconOfClarityListener implements Listener {

    private final Spellbreak plugin;
    private final AbilityManager abilityManager;
    private final CooldownManager cooldownManager;
    private final ManaSystem manaSystem;
    private final PlayerDataManager playerDataManager;
    private final Set<UUID> sneakingPlayers = new HashSet<>();

    public BeaconOfClarityListener(Spellbreak plugin) {
        this.plugin = plugin;
        this.abilityManager = plugin.getAbilityManager();
        this.cooldownManager = plugin.getCooldownManager();
        this.manaSystem = plugin.getManaSystem();
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            sneakingPlayers.add(player.getUniqueId());
        } else {
            sneakingPlayers.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (sneakingPlayers.contains(player.getUniqueId()) && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            int slot = player.getInventory().getHeldItemSlot();
            String boundAbilityName = playerDataManager.getAbilityAtSlot(player.getUniqueId(), slot);

            if (!"BeaconOfClarity".equalsIgnoreCase(boundAbilityName)) {
                return;
            }
            
            event.setCancelled(true);

            Ability ability = abilityManager.getAbilityByName(boundAbilityName);
            if (ability == null || !(ability instanceof BeaconOfClarityAbility)) {
                plugin.getLogger().warning("[BeaconOfClarityListener] Mismatch: Bound ability was '" + boundAbilityName + "' but could not retrieve/cast BeaconOfClarityAbility instance.");
                return;
            }
            
            BeaconOfClarityAbility beaconAbility = (BeaconOfClarityAbility) ability;
            String playerClass = playerDataManager.getPlayerClass(player.getUniqueId());
            if (playerClass == null || !beaconAbility.getRequiredClass().equalsIgnoreCase(playerClass)) {
                return;
            }
            
            if (cooldownManager.isOnCooldown(player, beaconAbility.getName())) {
                int remainingSeconds = cooldownManager.getRemainingCooldown(player, beaconAbility.getName());
                String remainingFormatted = String.format("%ds", remainingSeconds);
                player.sendMessage(String.format("§c%s is on cooldown for %s.", beaconAbility.getName(), remainingFormatted));
                return;
            }

            if (!manaSystem.consumeMana(player, beaconAbility.getManaCost())) {
                player.sendMessage(String.format("§cNot enough mana for %s. Need %d mana.", beaconAbility.getName(), beaconAbility.getManaCost()));
                return;
            }

            beaconAbility.activate(player);
            if (beaconAbility.isSuccessful()) { 
                cooldownManager.setCooldown(player, beaconAbility.getName(), beaconAbility.getCooldown());
            } else {
                manaSystem.restoreMana(player, beaconAbility.getManaCost()); 
            }
        }
    }
} 