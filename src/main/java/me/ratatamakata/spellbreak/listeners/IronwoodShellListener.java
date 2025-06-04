package me.ratatamakata.spellbreak.listeners;


import org.bukkit.Particle;
import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.impl.IronwoodShellAbility;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.ChatColor;
import java.util.UUID;

public class IronwoodShellListener implements Listener {

    private final IronwoodShellAbility ability;

    public IronwoodShellListener(IronwoodShellAbility ability) {
        this.ability = ability;
    }

    @EventHandler
    public void onActivateIronwoodShell(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!player.isSneaking()) return; // Must be sneaking

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return; // Must be left-click

        int slot = player.getInventory().getHeldItemSlot();
        String boundAbilityName = Spellbreak.getInstance().getPlayerDataManager().getAbilityAtSlot(player.getUniqueId(), slot);

        if (!this.ability.getName().equalsIgnoreCase(boundAbilityName)) return; // Check if IronwoodShell is bound

        // Class Requirement Check
        String playerClass = Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId());
        if (this.ability.getRequiredClass() != null && 
            !this.ability.getRequiredClass().isEmpty() && 
            !this.ability.getRequiredClass().equalsIgnoreCase(playerClass)) {
            player.sendMessage(ChatColor.RED + "Your class cannot use " + this.ability.getName() + ".");
            return;
        }

        // Cooldown Check
        if (Spellbreak.getInstance().getCooldownManager().isOnCooldown(player, this.ability.getName())) {
            long remaining = Spellbreak.getInstance().getCooldownManager().getRemainingCooldown(player, this.ability.getName());
            player.sendActionBar(ChatColor.RED + String.format("%s is on cooldown! (%.1fs)", this.ability.getName(), remaining / 1000.0));
            return;
        }

        // Mana Check
        if (!Spellbreak.getInstance().getManaSystem().consumeMana(player, this.ability.getManaCost())) {
            player.sendActionBar(ChatColor.RED + "Not enough mana for " + this.ability.getName() + "!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.5f);
            return;
        }

        event.setCancelled(true);
        this.ability.activate(player); // Activate the ability instance we have
        Spellbreak.getInstance().getCooldownManager().setCooldown(player, this.ability.getName(), this.ability.getCooldown());

        // Activation feedback
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.8f);
        player.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, player.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, Material.OAK_WOOD.createBlockData());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        UUID playerUUID = player.getUniqueId();

        if (!ability.isShellActive(player)) return;

        // --- Max Instances Check ---
        // Increment first, then check. The hit that breaks the instance limit still occurs.
        ability.incrementBlockedInstances(playerUUID);
        if (ability.hasReachedMaxInstances(playerUUID)) {
            // Damage from this hit is NOT absorbed as the shell breaks from too many instances.
            // However, the reflection from onDamageByEntity might still occur if applicable.
            ability.deactivateShell(player);
            // Don't return yet, let the original damage pass through, just don't absorb.
            // But we need to make sure absorption logic below isn't run.
            // For simplicity, if it breaks by instances, the current hit is not absorbed.
             player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.5f); // Sound for breaking by instances
            return; // Shell broke, no absorption this hit.
        }
        // --- End Max Instances Check ---

        // --- Damage Absorption & Max Absorbable Damage Check ---
        double originalDamage = event.getDamage();
        double damageToAbsorb = originalDamage * ability.getDamageAbsorption();
        
        double currentTotalAbsorbed = ability.getCurrentAbsorbedDamage(playerUUID);
        double maxCanAbsorbConfig = ability.getMaxAbsorbableDamageConfig();

        if (currentTotalAbsorbed + damageToAbsorb > maxCanAbsorbConfig) {
            damageToAbsorb = maxCanAbsorbConfig - currentTotalAbsorbed; // Absorb only up to the cap
            if (damageToAbsorb < 0) damageToAbsorb = 0; // Should not happen if logic is right
            
            event.setDamage(originalDamage - damageToAbsorb);
            if (damageToAbsorb > 0) { // Only record if some damage was actually absorbed
                 ability.recordDamageAbsorbed(playerUUID, damageToAbsorb);
            }
            ability.deactivateShell(player); // Shell breaks due to reaching damage cap
             player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f); // Sound for breaking by damage cap

        } else {
            // Absorb as normal
            event.setDamage(originalDamage - damageToAbsorb);
            ability.recordDamageAbsorbed(playerUUID, damageToAbsorb);
            // Visual feedback (already here, but maybe only if not broken?)
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_WOOD_HIT, 0.8f, 1.2f);
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (!ability.isShellActive(player)) return;

        // Get original attacker
        Entity damager = event.getDamager();
        LivingEntity source = getDamageSource(damager);
        if (source == null || source.getUniqueId().equals(player.getUniqueId())) return;

        // Prevent reflection loops
        if (ability.addToImmunity(source.getUniqueId())) {
            double reflectDamage = event.getOriginalDamage(EntityDamageEvent.DamageModifier.BASE) * ability.getDamageReflection();

            // Apply reflection damage
            Spellbreak.getInstance().getAbilityDamage().damage(source, reflectDamage, player, null, null);

            // Visual/audio feedback
            source.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE,
                    source.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3,
                    Material.OAK_WOOD.createBlockData());
            source.getWorld().playSound(source.getLocation(),
                    Sound.BLOCK_WOOD_STEP, 1f, 1.2f);

            // Remove immunity after 1 tick
            new BukkitRunnable() {
                @Override
                public void run() {
                    ability.removeFromImmunity(source.getUniqueId());
                }
            }.runTaskLater(Spellbreak.getInstance(), 1);
        }
    }

    private LivingEntity getDamageSource(Entity damager) {
        if (damager instanceof LivingEntity) {
            return (LivingEntity) damager;
        } else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            ProjectileSource source = projectile.getShooter();
            if (source instanceof LivingEntity) {
                return (LivingEntity) source;
            }
        }
        return null;
    }
}