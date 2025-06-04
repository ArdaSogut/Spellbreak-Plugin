package me.ratatamakata.spellbreak.listeners;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.ai.BaritoneAI;
import me.ratatamakata.spellbreak.ai.SkeletonAI;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.ChatColor;

public class BoneChoirListener implements Listener {

    private final Spellbreak plugin = Spellbreak.getInstance(); // Cache instance for logging

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_AIR && event.getPlayer().isSneaking()) {
            Player player = event.getPlayer();
            plugin.getLogger().info("[BoneChoirListener] Player " + player.getName() + " sneaked and LEFT-clicked air.");

            int slot = player.getInventory().getHeldItemSlot();
            String abilityName = plugin.getPlayerDataManager().getAbilityAtSlot(player.getUniqueId(), slot);
            plugin.getLogger().info("[BoneChoirListener] Ability in slot " + slot + ": " + abilityName);

            if (!"BoneChoir".equalsIgnoreCase(abilityName)) {
                // plugin.getLogger().info("[BoneChoirListener] Not BoneChoir. Exiting."); // Only log if really needed
                return;
            }
            plugin.getLogger().info("[BoneChoirListener] Ability is BoneChoir. Proceeding...");

            Ability ability = plugin.getAbilityManager().getAbilityByName(abilityName);
            if (ability == null) {
                plugin.getLogger().warning("[BoneChoirListener] BoneChoir ability instance is NULL from AbilityManager!");
                return;
            }
            plugin.getLogger().info("[BoneChoirListener] Got BoneChoir instance: " + ability.getName());

            if (plugin.getCooldownManager().isOnCooldown(player, abilityName)) {
                plugin.getLogger().info("[BoneChoirListener] BoneChoir is on COOLDOWN for " + player.getName() + ". Cooldown check returned true.");
                // This log will appear even if bypass is active because isOnCooldown logs *before* returning false for bypass.
                // Check for the [CM DEBUG] bypass log to confirm if bypass was the reason.
                return;
            }
            plugin.getLogger().info("[BoneChoirListener] BoneChoir is NOT on cooldown for " + player.getName() + ". Proceeding...");

            if (plugin.getManaSystem().consumeMana(player, ability.getManaCost())) {
                plugin.getLogger().info("[BoneChoirListener] Mana sufficient and consumed. Activating BoneChoir for " + player.getName());
                ability.activate(player);
                plugin.getCooldownManager().setCooldown(player, abilityName, ability.getCooldown());
            } else {
                plugin.getLogger().info("[BoneChoirListener] Insufficient mana for BoneChoir for " + player.getName() + ". Mana: " + plugin.getManaSystem().getMana(player) + "/" + ability.getManaCost());
                player.sendMessage(ChatColor.RED + "Not enough mana to cast " + ability.getName() + "."); // User feedback
            }
        }
    }

    @EventHandler
    public void onSkeletonTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Skeleton &&
                event.getEntity().hasMetadata("BoneChoirCaster")) {

            Player caster = (Player) event.getEntity().getMetadata("BoneChoirCaster")
                    .get(0).value();

            if (event.getTarget() != null &&
                    event.getTarget().getUniqueId().equals(caster.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDamagesChoir(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Skeleton &&
                event.getEntity().hasMetadata("BoneChoirCaster") &&
                event.getDamager() instanceof Player) {

            Player damager = (Player) event.getDamager();
            Player caster = (Player) event.getEntity().getMetadata("BoneChoirCaster")
                    .get(0).value();

            if (damager.getUniqueId().equals(caster.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Snowball && event.getEntity().hasMetadata("TenorDebuff")) {
            if (event.getHitEntity() instanceof LivingEntity) {
                LivingEntity target = (LivingEntity) event.getHitEntity();
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
                event.getEntity().getWorld().playSound(
                        event.getEntity().getLocation(),
                        Sound.BLOCK_BELL_USE,
                        1.0f,
                        1.5f
                );
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Existing player damage check
        if (event.getEntity() instanceof Skeleton &&
                event.getEntity().hasMetadata("BoneChoirCaster") &&
                event.getDamager() instanceof Player) {

            Player damager = (Player) event.getDamager();
            Player caster = (Player) event.getEntity().getMetadata("BoneChoirCaster")
                    .get(0).value();

            if (damager.getUniqueId().equals(caster.getUniqueId())) {
                event.setCancelled(true);
            }
        }

        // New AI damage handling
        if (event.getDamager() instanceof Skeleton &&
                ((Skeleton) event.getDamager()).hasMetadata("SkeletonAI")) {

            Skeleton skeleton = (Skeleton) event.getDamager();
            SkeletonAI ai = (SkeletonAI) skeleton.getMetadata("SkeletonAI").get(0).value();

            if (ai instanceof BaritoneAI) {
                event.setDamage(5.0);
                skeleton.getWorld().playSound(
                        skeleton.getLocation(),
                        Sound.ITEM_TRIDENT_THUNDER,
                        0.8f,
                        0.6f
                );
            }
        }
    }
}
