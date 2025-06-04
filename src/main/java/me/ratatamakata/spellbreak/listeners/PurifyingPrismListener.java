package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.managers.AbilityManager;
import me.ratatamakata.spellbreak.abilities.impl.PurifyingPrismAbility;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class PurifyingPrismListener implements Listener {

    private final Spellbreak plugin;
    private final AbilityManager abilityManager;
    private final CooldownManager cooldownManager;
    private final ManaSystem manaSystem;
    private final PlayerDataManager playerDataManager;

    public PurifyingPrismListener(Spellbreak plugin) {
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
            int slot = player.getInventory().getHeldItemSlot();
            String boundAbilityName = playerDataManager.getAbilityAtSlot(player.getUniqueId(), slot);

            if (!"PurifyingPrism".equalsIgnoreCase(boundAbilityName)) {
                return;
            }

            event.setCancelled(true);

            Ability ability = abilityManager.getAbilityByName(boundAbilityName);
            if (ability == null || !(ability instanceof PurifyingPrismAbility)) {
                plugin.getLogger().warning("[PurifyingPrismListener] Mismatch: Bound ability was '" + boundAbilityName + "' but could not retrieve/cast PurifyingPrismAbility instance.");
                return;
            }
            
            PurifyingPrismAbility prismAbility = (PurifyingPrismAbility) ability;
            String playerClass = playerDataManager.getPlayerClass(player.getUniqueId());
            if (playerClass == null || !prismAbility.getRequiredClass().equalsIgnoreCase(playerClass)) {
                return;
            }
            
            if (cooldownManager.isOnCooldown(player, prismAbility.getName())) {
                int remainingSeconds = cooldownManager.getRemainingCooldown(player, prismAbility.getName());
                String remainingFormatted = String.format("%ds", remainingSeconds);
                player.sendMessage(String.format("§c%s is on cooldown for %s.", prismAbility.getName(), remainingFormatted));
                return;
            }

            if (!manaSystem.consumeMana(player, prismAbility.getManaCost())) {
                player.sendMessage(String.format("§cNot enough mana for %s. Need %d mana.", prismAbility.getName(), prismAbility.getManaCost()));
                return;
            }

            prismAbility.activate(player);
            if (prismAbility.isSuccessful()) {
                cooldownManager.setCooldown(player, prismAbility.getName(), prismAbility.getCooldown());
                player.sendMessage(String.format("§aUsed %s!", prismAbility.getName()));
            } else {
                manaSystem.restoreMana(player, prismAbility.getManaCost()); 
            }
        }
    }
} 