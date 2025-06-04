package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.TidepoolAbility;
import me.ratatamakata.spellbreak.managers.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class TidepoolListener implements Listener {
    private final Spellbreak plugin;
    private final AbilityManager abilityManager;
    private final PlayerDataManager playerData;
    private final CooldownManager cooldowns;
    private final ManaSystem mana;

    public TidepoolListener(Spellbreak plugin) {
        this.plugin = plugin;
        this.abilityManager = plugin.getAbilityManager();
        this.playerData = plugin.getPlayerDataManager();
        this.cooldowns = plugin.getCooldownManager();
        this.mana = plugin.getManaSystem();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking()) return;

        String boundAbility = playerData.getAbilityAtSlot(p.getUniqueId(), p.getInventory().getHeldItemSlot());
        if (!"Tidepool".equalsIgnoreCase(boundAbility)) return;

        TidepoolAbility ability = (TidepoolAbility) abilityManager.getAbilityByName("Tidepool");
        if (!ability.getRequiredClass().equalsIgnoreCase(playerData.getPlayerClass(p.getUniqueId()))) return;

        int adjustedCooldown = ability.getAdjustedCooldown(p);
        int adjustedManaCost = ability.getAdjustedManaCost(p);
        if (cooldowns.isOnCooldown(p, ability.getName())) {
            p.sendMessage("§cTidepool is on cooldown! " + cooldowns.getRemainingCooldown(p, ability.getName()) + "s remaining");
            return;
        }
        if (!mana.consumeMana(p, adjustedManaCost)) {
            p.sendMessage("§cNot enough mana for Tidepool! (Need " + adjustedManaCost + ")");
            return;
        }
        ability.activate(p);
        cooldowns.setCooldown(p, ability.getName(), adjustedCooldown);
        p.sendMessage("§aTidepool activated!");
    }
}

