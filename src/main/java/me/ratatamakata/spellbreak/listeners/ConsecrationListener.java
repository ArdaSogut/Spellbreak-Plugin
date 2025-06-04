package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.abilities.impl.ConsecrationAbility;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.AbilityManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class ConsecrationListener implements Listener {

    private final Spellbreak plugin;
    private final AbilityManager abilityManager;
    private final PlayerDataManager playerDataManager;
    private final CooldownManager cooldownManager;
    private final ManaSystem manaSystem;

    public ConsecrationListener(Spellbreak plugin) {
        this.plugin = plugin;
        this.abilityManager = plugin.getAbilityManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.cooldownManager = plugin.getCooldownManager();
        this.manaSystem = plugin.getManaSystem();
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        if (!event.isSneaking()) {
            return;
        }

        int slot = player.getInventory().getHeldItemSlot();
        String boundAbilityName = playerDataManager.getAbilityAtSlot(player.getUniqueId(), slot);

        if (!"Consecration".equalsIgnoreCase(boundAbilityName)) {
            return;
        }

        Ability ability = abilityManager.getAbilityByName("Consecration");

        if (!(ability instanceof ConsecrationAbility)) {
            plugin.getLogger().warning("[ConsecrationListener] Mismatch: Bound ability was 'Consecration' but could not retrieve/cast ConsecrationAbility instance.");
            return;
        }
        
        ConsecrationAbility consecrationAbility = (ConsecrationAbility) ability;
        String playerClass = playerDataManager.getPlayerClass(player.getUniqueId());

        if (playerClass == null || !playerClass.equalsIgnoreCase(consecrationAbility.getRequiredClass())) {
            return;
        }
        
        if (cooldownManager.isOnCooldown(player, consecrationAbility.getName())) {
            int remainingSeconds = cooldownManager.getRemainingCooldown(player, consecrationAbility.getName());
            player.sendMessage(String.format("§c%s is on cooldown for %ds.", consecrationAbility.getName(), remainingSeconds));
            return;
        }

        if (!manaSystem.consumeMana(player, consecrationAbility.getManaCost())) {
            player.sendMessage(String.format("§cNot enough mana for %s. Need %d mana.", consecrationAbility.getName(), consecrationAbility.getManaCost()));
            return;
        }

        consecrationAbility.activate(player);
        if(consecrationAbility.isSuccessful()){
            cooldownManager.setCooldown(player, consecrationAbility.getName(), consecrationAbility.getCooldown());
            player.sendMessage(String.format("§aUsed %s!", consecrationAbility.getName()));
        } else {
            manaSystem.restoreMana(player, consecrationAbility.getManaCost());
        }
    }
} 