package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.PlagueCloudAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class PlagueCloudListener implements Listener {
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking()) return;

        int slot = p.getInventory().getHeldItemSlot();
        String name = Spellbreak.getInstance().getPlayerDataManager().getAbilityAtSlot(p.getUniqueId(),slot);
        if (!"PlagueCloud".equalsIgnoreCase(name)) return;
        PlagueCloudAbility ability = (PlagueCloudAbility) Spellbreak.getInstance()
                .getAbilityManager().getAbilityByName(name);

        if (ability == null) return;
        if (!ability.getRequiredClass().equalsIgnoreCase(Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(p.getUniqueId()))) {
            p.sendMessage("§cYou're not a Necromancer!"); return;
        }
        if (Spellbreak.getInstance().getCooldownManager().isOnCooldown(p,name)) { p.sendMessage("§cAbility on cooldown!"); return; }
        if (!Spellbreak.getInstance().getManaSystem().consumeMana(p,ability.getManaCost())) { p.sendMessage("§cNot enough mana!"); return; }

        ability.activate(p);
        Spellbreak.getInstance().getCooldownManager().setCooldown(p,name,ability.getCooldown());
    }
}
