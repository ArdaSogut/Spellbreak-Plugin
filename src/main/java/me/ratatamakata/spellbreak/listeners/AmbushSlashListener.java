package me.ratatamakata.spellbreak.listeners;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.abilities.impl.AmbushSlashAbility;
import me.ratatamakata.spellbreak.managers.AbilityManager;
import me.ratatamakata.spellbreak.managers.CooldownManager;
import me.ratatamakata.spellbreak.managers.ManaSystem;
import me.ratatamakata.spellbreak.managers.PlayerDataManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.MetadataValue;

public class AmbushSlashListener implements Listener {
    private final Spellbreak plugin;
    private final AbilityManager abilityManager;
    private final CooldownManager cooldownManager;
    private final PlayerDataManager playerDataManager;
    private final ManaSystem manaSystem;

    public AmbushSlashListener(Spellbreak plugin) {
        this.plugin = plugin;
        this.abilityManager = plugin.getAbilityManager();
        this.cooldownManager = plugin.getCooldownManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.manaSystem = plugin.getManaSystem();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Ability rawAbility = this.abilityManager.getAbilityByName("AmbushSlash");
        if (rawAbility instanceof AmbushSlashAbility) {
            AmbushSlashAbility ambushSlash = (AmbushSlashAbility)rawAbility;
            if (ambushSlash.isTriggerAction(event.getAction())) {
                int heldSlot = player.getInventory().getHeldItemSlot();
                String boundAbilityName = this.playerDataManager.getAbilityAtSlot(player.getUniqueId(), heldSlot);
                if (ambushSlash.getName().equalsIgnoreCase(boundAbilityName)) {
                    String requiredClass = ambushSlash.getRequiredClass();
                    if (requiredClass != null && !requiredClass.isEmpty()) {
                        String playerClass = this.playerDataManager.getPlayerClass(player.getUniqueId());
                        if (!requiredClass.equalsIgnoreCase(playerClass)) {
                            return;
                        }
                    }

                    boolean isStage2Attempt = ambushSlash.getMarkedTargetsView().containsKey(player.getUniqueId());
                    if (isStage2Attempt) {
                        this.plugin.getLogger().info("[AmbushSlashListener] Attempting Stage 2 for " + player.getName());
                        ambushSlash.activate(player);
                        if (ambushSlash.isSuccessful()) {
                            this.plugin.getLogger().info("[AmbushSlashListener] Stage 2 SUCCESS for " + player.getName());
                            event.setCancelled(true);
                        } else {
                            this.plugin.getLogger().info("[AmbushSlashListener] Stage 2 FAILED for " + player.getName());
                        }
                    } else {
                        this.plugin.getLogger().info("[AmbushSlashListener] Attempting Stage 1 for " + player.getName());
                        if (ambushSlash.hasActiveProjectile(player.getUniqueId())) {
                            this.plugin.getLogger().info("[AmbushSlashListener] Stage 1 FAILED (Active Projectile) for " + player.getName());
                            return;
                        }

                        if (this.cooldownManager.isOnCooldown(player, ambushSlash.getName())) {
                            this.plugin.getLogger().info("[AmbushSlashListener] Stage 1 FAILED (Cooldown) for " + player.getName());
                            return;
                        }

                        if (this.manaSystem.consumeMana(player, ambushSlash.getManaCost())) {
                            this.plugin.getLogger().info("[AmbushSlashListener] Stage 1 Mana OK. Activating for " + player.getName());
                            ambushSlash.activate(player);
                            if (ambushSlash.isSuccessful()) {
                                this.plugin.getLogger().info("[AmbushSlashListener] Stage 1 HIT (Target Marked) for " + player.getName());
                                event.setCancelled(true);
                            } else {
                                this.plugin.getLogger().info("[AmbushSlashListener] Stage 1 MISS for " + player.getName());
                                this.cooldownManager.setCooldown(player, ambushSlash.getName(), ambushSlash.getCooldown());
                            }
                        } else {
                            this.plugin.getLogger().info("[AmbushSlashListener] Stage 1 FAILED (Mana) for " + player.getName());
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(String.valueOf(ChatColor.RED) + "Not enough mana!"));
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0F, 0.5F);
                        }
                    }

                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity deceased = event.getEntity();
        AmbushSlashAbility ambushSlashAbility = null;
        Ability rawAbility = this.abilityManager.getAbilityByName("AmbushSlash");
        if (rawAbility instanceof AmbushSlashAbility) {
            ambushSlashAbility = (AmbushSlashAbility)rawAbility;
        }

        if (deceased.hasMetadata("NatureBurnSource")) {
            List<MetadataValue> values = deceased.getMetadata("NatureBurnSource");
            if (!values.isEmpty() && ((MetadataValue)values.get(0)).value() instanceof UUID) {
                UUID sourceUUID = (UUID)((MetadataValue)values.get(0)).value();
                if (ambushSlashAbility != null) {
                    ambushSlashAbility.handleNatureBurnSpread(deceased, sourceUUID);
                }
            }
        }

        if (ambushSlashAbility != null) {
            Iterator var10 = (new HashMap(ambushSlashAbility.getMarkedTargetsView())).entrySet().iterator();

            while(var10.hasNext()) {
                Entry<UUID, UUID> entry = (Entry)var10.next();
                UUID casterUUID = (UUID)entry.getKey();
                UUID markedTargetUUID = (UUID)entry.getValue();
                if (deceased.getUniqueId().equals(markedTargetUUID)) {
                    Player caster = this.plugin.getServer().getPlayer(casterUUID);
                    if (caster != null && caster.isOnline()) {
                        ambushSlashAbility.clearMark(casterUUID);
                        this.plugin.getCooldownManager().setCooldown(caster, ambushSlashAbility.getName(), ambushSlashAbility.getCooldown());
                        String var10001 = String.valueOf(ChatColor.YELLOW);
                        caster.sendMessage(var10001 + "Your Ambush Slash mark on " + deceased.getName() + " was lost as they perished.");
                    } else {
                        ambushSlashAbility.clearMark(casterUUID);
                    }
                    break;
                }
            }
        }

    }
}