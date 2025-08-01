package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.abilities.impl.BeaconOfClarityAbility;
import me.ratatamakata.spellbreak.managers.AbilityManager;
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
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final AbilityManager abilityManager = plugin.getAbilityManager();
    private final CooldownManager cooldownManager = plugin.getCooldownManager();
    private final ManaSystem manaSystem = plugin.getManaSystem();
    private final PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
    private final Set<UUID> sneakingPlayers = new HashSet<>();

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            sneakingPlayers.add(event.getPlayer().getUniqueId());
        } else {
            sneakingPlayers.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!sneakingPlayers.contains(player.getUniqueId())) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;

        int slot = player.getInventory().getHeldItemSlot();
        String bound = playerDataManager.getAbilityAtSlot(player.getUniqueId(), slot);
        if (!"BeaconOfClarity".equalsIgnoreCase(bound)) return;
        event.setCancelled(true);

        Ability ability = abilityManager.getAbilityByName(bound);
        if (!(ability instanceof BeaconOfClarityAbility)) return;
        BeaconOfClarityAbility beacon = (BeaconOfClarityAbility) ability;

        String cls = playerDataManager.getPlayerClass(player.getUniqueId());
        if (cls == null || !beacon.getRequiredClass().equalsIgnoreCase(cls)) return;

        if (cooldownManager.isOnCooldown(player, beacon.getName())) {
            player.sendMessage(String.format("§c%s is on cooldown for %ds.", beacon.getName(), cooldownManager.getRemainingCooldown(player, beacon.getName())));
            return;
        }
        if (!manaSystem.consumeMana(player, beacon.getManaCost())) {
            player.sendMessage(String.format("§cNot enough mana for %s. Need %d mana.", beacon.getName(), beacon.getManaCost()));
            return;
        }

        beacon.activate(player, cls);
        cooldownManager.setCooldown(player, beacon.getName(), beacon.getCooldown());
    }
}
