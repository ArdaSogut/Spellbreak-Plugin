package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.abilities.impl.UndyingPactAbility;
import me.ratatamakata.spellbreak.managers.AbilityManager;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class UndyingPactListener implements Listener {
    private final Spellbreak plugin = Spellbreak.getInstance();
    private final AbilityManager abilityManager = plugin.getAbilityManager();
    private final CooldownManager cooldownManager = plugin.getCooldownManager();
    private final ManaSystem manaSystem = plugin.getManaSystem();
    private final PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
    private final Set<UUID> sneaking = new HashSet<>();

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (event.isSneaking()) sneaking.add(id);
        else sneaking.remove(id);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!sneaking.contains(player.getUniqueId())) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;

        int slot = player.getInventory().getHeldItemSlot();
        String bound = playerDataManager.getAbilityAtSlot(player.getUniqueId(), slot);
        if (!"UndyingPact".equalsIgnoreCase(bound)) return;
        event.setCancelled(true);

        Ability ability = abilityManager.getAbilityByName(bound);
        if (!(ability instanceof UndyingPactAbility)) return;
        UndyingPactAbility pact = (UndyingPactAbility) ability;

        String cls = playerDataManager.getPlayerClass(player.getUniqueId());
        if (cls == null || !pact.getRequiredClass().equalsIgnoreCase(cls)) return;

        if (cooldownManager.isOnCooldown(player, pact.getName())) {
            int rem = cooldownManager.getRemainingCooldown(player, pact.getName());
            player.sendMessage(String.format("§c%s is on cooldown for %ds.", pact.getName(), rem));
            return;
        }
        if (!manaSystem.consumeMana(player, pact.getManaCost())) {
            player.sendMessage(String.format("§cNot enough mana for %s. Need %d mana.", pact.getName(), pact.getManaCost()));
            return;
        }

        pact.activate(player);
        cooldownManager.setCooldown(player, pact.getName(), pact.getCooldown());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
    }
}
