package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.NeuralTrapAbility;
import me.ratatamakata.spellbreak.level.SpellLevel;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class NeuralTrapListener implements Listener {
    private final CooldownManager cd;
    private final ManaSystem mana;
    private final PlayerDataManager pdm;

    public NeuralTrapListener() {
        this.cd = Spellbreak.getInstance().getCooldownManager();
        this.mana = Spellbreak.getInstance().getManaSystem();
        this.pdm = Spellbreak.getInstance().getPlayerDataManager();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        int slot = p.getInventory().getHeldItemSlot();
        String abilityName = pdm.getAbilityAtSlot(p.getUniqueId(), slot);
        if (!"NeuralTrap".equalsIgnoreCase(abilityName)) return;

        NeuralTrapAbility ability = (NeuralTrapAbility) Spellbreak.getInstance()
                .getAbilityManager().getAbilityByName(abilityName);
        if (ability == null) return;

        SpellLevel spellLevel = Spellbreak.getInstance().getLevelManager().getSpellLevel(p.getUniqueId(), Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(p.getUniqueId()), "NeuralTrap");
        int adjustedCooldown = (int) (ability.getCooldown() * spellLevel.getCooldownReduction());
        if (cd.isOnCooldown(p, abilityName)) {
            p.sendMessage(ChatColor.RED + "Neural Trap on cooldown: "
                    + cd.getRemainingCooldown(p, abilityName) + "s");
            return;
        }

        if (!mana.consumeMana(p, ability.getManaCost())) {
            p.sendMessage(ChatColor.RED + "Not enough mana for Neural Trap!");
            return;
        }

        // Activate the ability
        ability.activate(p);
        cd.setCooldown(p, abilityName, adjustedCooldown);
    }
}