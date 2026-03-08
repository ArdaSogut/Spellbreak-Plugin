package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.util.LastDamageInfo;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.UUID;

public class CustomDeathMessageListener implements Listener {

    private final Spellbreak plugin;

    // New metadata key for storing info directly on the victim
    public static final String METADATA_KEY_LAST_DAMAGE_INFO = "SpellbreakLastDamageInfo";

    // Existing metadata keys for identifying ability source - now defined in AbilityDamageTracker
    // These constants are kept here for backward compatibility
    public static final String METADATA_KEY_CASTER_UUID = "SpellbreakCasterUUID";
    public static final String METADATA_KEY_ABILITY_NAME = "SpellbreakAbilityName";
    public static final String METADATA_KEY_SUB_ABILITY_NAME = "SpellbreakSubAbilityName";
    public static final String METADATA_KEY_SUMMON_TYPE_NAME = "SpellbreakSummonTypeName";

    public CustomDeathMessageListener(Spellbreak plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return; // Victim is not a player
        }

        Entity damager = event.getDamager();
        Player caster = null;
        String abilityName = null;
        String subAbilityName = null;
        String attackerNameDisplay = null;
        String casterNameForInfo = null;
        boolean bySummon = false;
        LastDamageInfo damageInfo = null; // Prepare to store the info object

        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player pShooter) {
                caster = pShooter;
            }
            abilityName = getMetadataString(projectile, METADATA_KEY_ABILITY_NAME);
            subAbilityName = getMetadataString(projectile, METADATA_KEY_SUB_ABILITY_NAME);
            if (caster == null && projectile.hasMetadata(METADATA_KEY_CASTER_UUID)) {
                try {
                    UUID casterUUID = UUID.fromString(getMetadataString(projectile, METADATA_KEY_CASTER_UUID));
                    caster = Bukkit.getPlayer(casterUUID);
                    casterNameForInfo = (caster != null) ? caster.getName() : Bukkit.getOfflinePlayer(casterUUID).getName();
                    if (casterNameForInfo == null) casterNameForInfo = "Unknown Caster";
                } catch (IllegalArgumentException ignored) { casterNameForInfo = "Mysterious Force"; }
            } else if (caster != null) {
                casterNameForInfo = caster.getName();
            }
            attackerNameDisplay = casterNameForInfo != null ? casterNameForInfo : "Someone";

        } else if (!(damager instanceof Player)) { // Assume summon/other entity
            bySummon = true;
            String summonTypeName = getMetadataString(damager, METADATA_KEY_SUMMON_TYPE_NAME);
            if (summonTypeName == null && damager.getCustomName() != null) summonTypeName = damager.getCustomName();
            else if (summonTypeName == null) summonTypeName = damager.getType().getName();
            attackerNameDisplay = summonTypeName != null ? summonTypeName : "A Minion";
            abilityName = getMetadataString(damager, METADATA_KEY_ABILITY_NAME);
            subAbilityName = getMetadataString(damager, METADATA_KEY_SUB_ABILITY_NAME);
            if (damager.hasMetadata(METADATA_KEY_CASTER_UUID)) {
                try {
                    UUID casterUUID = UUID.fromString(getMetadataString(damager, METADATA_KEY_CASTER_UUID));
                    caster = Bukkit.getPlayer(casterUUID);
                    casterNameForInfo = (caster != null) ? caster.getName() : Bukkit.getOfflinePlayer(casterUUID).getName();
                    if (casterNameForInfo == null) casterNameForInfo = "An Unseen Force";
                } catch (IllegalArgumentException ignored) {casterNameForInfo = "A Mysterious Entity";}
            } else {
                casterNameForInfo = "Unknown Caster";
            }

        } else if (damager instanceof Player pDamager) { // Direct player damage
            caster = pDamager;
            casterNameForInfo = caster.getName();
            attackerNameDisplay = caster.getName();
            abilityName = getMetadataString(caster, METADATA_KEY_ABILITY_NAME);
            if (abilityName == null) abilityName = "Melee"; // Default to Melee
            subAbilityName = getMetadataString(caster, METADATA_KEY_SUB_ABILITY_NAME);
        }

        // Record Combat
        if (caster != null && caster != victim) {
            plugin.getPvpManager().markInCombat(victim, caster);
            String finalAbility = abilityName != null ? abilityName : "Unknown";
            plugin.getPvpManager().addDamageRecord(victim, attackerNameDisplay, finalAbility, event.getFinalDamage());
        }

        // If we found ability information, create a LastDamageInfo
        if (abilityName != null) {
            damageInfo = new LastDamageInfo(
                    victim.getName(), attackerNameDisplay, casterNameForInfo,
                    abilityName, subAbilityName, bySummon
            );

            // Store the info on the victim
            victim.setMetadata(METADATA_KEY_LAST_DAMAGE_INFO, new FixedMetadataValue(plugin, damageInfo));

            // Clean up ability metadata if it's on a player
            long delayTicks = 20L;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline() && victim.hasMetadata(METADATA_KEY_LAST_DAMAGE_INFO)) {
                    victim.removeMetadata(METADATA_KEY_LAST_DAMAGE_INFO, plugin);
                }
            }, delayTicks);

            // Clean up ability metadata if it's on a player
            if (damager instanceof Player) {
                damager.removeMetadata(METADATA_KEY_ABILITY_NAME, plugin);
                if (subAbilityName != null) {
                    damager.removeMetadata(METADATA_KEY_SUB_ABILITY_NAME, plugin);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        LastDamageInfo damageInfo = null;

        if (victim.hasMetadata(METADATA_KEY_LAST_DAMAGE_INFO)) {
            List<MetadataValue> values = victim.getMetadata(METADATA_KEY_LAST_DAMAGE_INFO);
            if (!values.isEmpty()) {
                Object metaValue = values.get(0).value();
                if (metaValue instanceof LastDamageInfo) {
                    damageInfo = (LastDamageInfo) metaValue;
                }
            }
        }

        victim.removeMetadata(METADATA_KEY_LAST_DAMAGE_INFO, plugin);

        Player killer = victim.getKiller();
        if (killer == null && damageInfo != null && damageInfo.getCasterName() != null) {
            killer = Bukkit.getPlayer(damageInfo.getCasterName());
        }

        if (killer != null && killer != victim) {
            // Kill Streak Check
            plugin.getPvpManager().handleKill(killer, victim);

            // Notify killer
            killer.sendMessage(Component.text("You eliminated " + victim.getName() + "!").color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
            killer.playSound(killer.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            // Death Recap for Victim
            victim.sendMessage(Component.text("☠ ").color(net.kyori.adventure.text.format.NamedTextColor.DARK_RED)
                .append(Component.text("You were eliminated by " + killer.getName()).color(net.kyori.adventure.text.format.NamedTextColor.RED)));
            victim.sendMessage(Component.text("Damage Breakdown (Last 30s):").color(net.kyori.adventure.text.format.NamedTextColor.GRAY));
            
            java.util.List<me.ratatamakata.spellbreak.managers.PvPManager.DamageRecord> records = plugin.getPvpManager().getRecentDamage(victim);
            if (records.isEmpty()) {
                victim.sendMessage(Component.text(" - No recent damage recorded.").color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
            } else {
                java.util.Map<String, Double> damageByAbility = new java.util.HashMap<>();
                for (me.ratatamakata.spellbreak.managers.PvPManager.DamageRecord rec : records) {
                    damageByAbility.put(rec.abilityName, damageByAbility.getOrDefault(rec.abilityName, 0.0) + rec.damage);
                }
                for (java.util.Map.Entry<String, Double> entry : damageByAbility.entrySet()) {
                    victim.sendMessage(Component.text(" - ").color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .append(Component.text(entry.getKey() + ": ").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                        .append(Component.text(String.format("%.1f", entry.getValue())).color(net.kyori.adventure.text.format.NamedTextColor.RED)));
                }
            }
        } else {
            // If they died to environment, clear streaks anyway
            plugin.getPvpManager().resetStreak(victim);
        }

        if (damageInfo != null) {
            String message = damageInfo.getFormattedDeathMessage();
            if (message != null) {
                event.deathMessage(Component.text(message));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoneChoirSummonDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        Entity effectiveDamager = damager;
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Entity shooter) {
                effectiveDamager = shooter;
            }
        }

        String damagerAbilityName = getMetadataString(effectiveDamager, METADATA_KEY_ABILITY_NAME);
        String victimAbilityName = getMetadataString(victim, METADATA_KEY_ABILITY_NAME);

        if ("BoneChoir".equals(damagerAbilityName) && "BoneChoir".equals(victimAbilityName)) {
            String damagerCasterUUID = getMetadataString(effectiveDamager, METADATA_KEY_CASTER_UUID);
            String victimCasterUUID = getMetadataString(victim, METADATA_KEY_CASTER_UUID);

            if (damagerCasterUUID != null && damagerCasterUUID.equals(victimCasterUUID)) {
                event.setCancelled(true);
            } else if (damagerCasterUUID != null && victimCasterUUID != null && !damagerCasterUUID.equals(victimCasterUUID)) {
                plugin.getLogger().info("[BoneChoir Listener] Allowing ENEMY CHOIR damage: " + effectiveDamager.getName() + " (Caster: " + damagerCasterUUID + ") -> " + victim.getName() + " (Caster: " + victimCasterUUID + ")");
            }
        }
    }

    private String getMetadataString(Entity entity, String key) {
        if (entity == null || !entity.hasMetadata(key)) {
            return null;
        }

        List<MetadataValue> values = entity.getMetadata(key);
        if (!values.isEmpty()) {
            return values.get(0).asString();
        }
        return null;
    }
}