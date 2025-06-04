// LifeDrainListener.java
package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.LifeDrainAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LifeDrainListener implements Listener {
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        int slot = p.getInventory().getHeldItemSlot();
        String abilityName = Spellbreak.getInstance()
                .getPlayerDataManager().getAbilityAtSlot(id, slot);
        if (!"LifeDrain".equalsIgnoreCase(abilityName)) return;

        LifeDrainAbility ability = (LifeDrainAbility) Spellbreak.getInstance()
                .getAbilityManager().getAbilityByName(abilityName);
        if (ability == null) return;

        if (e.isSneaking()) {
            // Start only if not active and not on cooldown
            if (tasks.containsKey(id)) return;
            if (Spellbreak.getInstance().getCooldownManager().isOnCooldown(p, abilityName)) return;
            // initialize successful casts count
            p.setMetadata("drainCasts", new FixedMetadataValue(Spellbreak.getInstance(), 0));

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isSneaking()) { cancel(); return; }
                    int casts = p.getMetadata("drainCasts").get(0).asInt();
                    if (casts >= 3) { cancel(); return; }

                    // Attempt ability without consuming mana first
                    ability.activate(p);
                    // Only if ability found a target
                    if (ability.isSuccessful()) {
                        // Now consume mana, cancel if insufficient
                        if (!Spellbreak.getInstance().getManaSystem().consumeMana(p, ability.getManaCost())) {
                            cancel();
                            return;
                        }
                        // increment successful casts
                        p.setMetadata("drainCasts", new FixedMetadataValue(
                                Spellbreak.getInstance(), casts + 1));
                    }
                }
            }.runTaskTimer(Spellbreak.getInstance(), 0L, 10L);

            tasks.put(id, task);
        } else {
            // Stop draining
            if (!tasks.containsKey(id)) return;
            tasks.remove(id).cancel();

            int casts = 0;
            if (!p.getMetadata("drainCasts").isEmpty()) {
                casts = p.getMetadata("drainCasts").get(0).asInt();
            }
            p.removeMetadata("drainCasts", Spellbreak.getInstance());

            // Only apply cooldown if at least one successful drain cast
            if (casts > 0) {
                Spellbreak.getInstance().getCooldownManager()
                        .setCooldown(p, abilityName, ability.getCooldown());
            }
        }
    }
}