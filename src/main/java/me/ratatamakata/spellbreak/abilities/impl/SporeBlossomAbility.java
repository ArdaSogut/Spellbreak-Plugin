package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.level.SpellLevel;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.*;

public class SporeBlossomAbility implements Ability {
    private int cooldown = 10;
    private int manaCost = 35;
    private String requiredClass = "archdruid";
    private double radius = 5.0;
    private int sporeCount = 50;
    private double damage = 2.0;
    private double effectHeight = 2.0;
    private int riseTicks = 30;  // Longer bloom time
    private double knockbackHorizontal = 1.2;
    private double knockbackVertical = 0.8;
    private int slownessDuration = 100;
    private int lingerDuration = 70; // 10 seconds in ticks
    private final Random random = new Random();
    private final Map<UUID, Location> activeBlossoms = new HashMap<>();

    // Color range for pollen effect
    private final Color[] pollenColors = {
            Color.fromRGB(245, 255, 154), // Pale yellow
            Color.fromRGB(215, 235, 134),
            Color.fromRGB(185, 215, 114)
    };
    private Color brightenColor(Color original, float factor) {
        int r = Math.min(255, (int)(original.getRed() * (1 + factor)));
        int g = Math.min(255, (int)(original.getGreen() * (1 + factor)));
        int b = Math.min(255, (int)(original.getBlue() * (1 + factor)));
        return Color.fromRGB(r, g, b);
    }


    @Override public String getName() { return "SporeBlossom"; }
    @Override public String getDescription() {
        return "Releases lingering pollen clouds that slow enemies and propel you backwards";
    }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }

    @Override
    public boolean isTriggerAction(org.bukkit.event.block.Action action) {
        return action == org.bukkit.event.block.Action.LEFT_CLICK_AIR ||
                action == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK;
    }

    @Override
    public void activate(Player player) {
        SpellLevel sl = Spellbreak.getInstance().getLevelManager()
                .getSpellLevel(player.getUniqueId(),
                        Spellbreak.getInstance().getPlayerDataManager().getPlayerClass(player.getUniqueId()),
                        getName());

        double scaledRadius = radius * sl.getRangeMultiplier();
        double scaledDamage = damage * sl.getDamageMultiplier();
        int scaledSporeCount = sporeCount + (sl.getLevel() >= 3 ? 20 : 0);

        // Color shift for L3+ (brighter gold)
        Color[] activeColors = (sl.getLevel() >= 3)
                ? new Color[]{ Color.fromRGB(255, 200, 60), Color.fromRGB(240, 180, 40), Color.fromRGB(220, 165, 30) }
                : pollenColors;

        Location center = player.getLocation().getBlock().getLocation().add(0.5, 0.1, 0.5);
        World world = center.getWorld();
        UUID blossomId = UUID.randomUUID();

        // Knockback
        Vector back = player.getLocation().getDirection().setY(0).normalize().multiply(-knockbackHorizontal);
        back.setY(knockbackVertical);
        player.setVelocity(back);

        // Initial damage
        world.getNearbyEntities(center, scaledRadius, effectHeight, scaledRadius).forEach(ent -> {
            if (!(ent instanceof LivingEntity) || ent.equals(player)) return;
            Spellbreak.getInstance().getAbilityDamage().damage((LivingEntity) ent, scaledDamage, player, this, null);
        });

        // Sounds
        float pitch = 0.6f + sl.getLevel() * 0.08f;
        world.playSound(center, Sound.BLOCK_GRASS_BREAK, 1.2f, pitch);
        world.playSound(center, Sound.ENTITY_BEE_POLLINATE, 0.8f, pitch);
        // Level 5: thunderous bloom burst
        if (sl.getLevel() >= 5) {
            world.playSound(center, Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.5f);
        }

        activeBlossoms.put(blossomId, center);
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ > lingerDuration) { activeBlossoms.remove(blossomId); cancel(); return; }

                for (int i = 0; i < scaledSporeCount; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = random.nextDouble() * scaledRadius;
                    double yOffset = Math.sin(tick * 0.1) * 0.5;
                    Location loc = center.clone().add(
                            Math.cos(angle) * dist,
                            yOffset + (random.nextDouble() * effectHeight),
                            Math.sin(angle) * dist);

                    Color color = activeColors[random.nextInt(activeColors.length)];
                    Color endColor = brightenColor(color, 0.2f);
                    float size = 2.5f + random.nextFloat() * 2.5f;
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 1, 0.3, 0.3, 0.3, 0.05,
                            new Particle.DustTransition(color, endColor, size));
                }

                // Level 5: Blindness pulse every 2s
                if (sl.getLevel() >= 5 && tick % 40 == 0) {
                    world.getNearbyEntities(center, scaledRadius, effectHeight, scaledRadius).forEach(ent -> {
                        if (!(ent instanceof LivingEntity) || ent.equals(player)) return;
                        ((LivingEntity) ent).addPotionEffect(
                                new org.bukkit.potion.PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, true, true));
                    });
                }

                activeBlossoms.values().forEach(loc -> applyLingeringEffectsScaled(loc, world, player, scaledRadius));
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 1L);
    }

    private void applyInitialDamage(Location center, World world, Player caster) {
        world.getNearbyEntities(center, radius, effectHeight, radius).forEach(ent -> {
            if (!(ent instanceof LivingEntity) || ent.equals(caster)) return;
            LivingEntity e = (LivingEntity) ent;
            Spellbreak.getInstance().getAbilityDamage().damage(e, damage, caster, this, null);
        });
    }

    private void applyLingeringEffectsScaled(Location center, World world, Player caster, double scaledRadius) {
        world.getNearbyEntities(center, scaledRadius, effectHeight, scaledRadius).forEach(ent -> {
            if (!(ent instanceof LivingEntity) || ent.equals(caster)) return;
            LivingEntity e = (LivingEntity) ent;
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true));
            e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0));
        });
    }

    private void applyLingeringEffects(Location center, World world, Player caster) {
        applyLingeringEffectsScaled(center, world, caster, radius);
    }

    @Override public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String p = "abilities.sporeblossom.";
        cooldown = cfg.getInt(p+"cooldown", cooldown);
        manaCost = cfg.getInt(p+"mana-cost", manaCost);
        radius = cfg.getDouble(p+"radius", radius);
        damage = cfg.getDouble(p+"damage", damage);
        sporeCount = cfg.getInt(p+"spore-count", sporeCount);
        effectHeight = cfg.getDouble(p+"effect-height", effectHeight);
        lingerDuration = cfg.getInt(p+"linger-duration", lingerDuration);
        knockbackHorizontal = cfg.getDouble(p+"knockback-horizontal", knockbackHorizontal);
        knockbackVertical = cfg.getDouble(p+"knockback-vertical", knockbackVertical);
        slownessDuration = cfg.getInt(p+"slowness-duration", slownessDuration);
    }

    @Override public boolean isSuccessful() { return true; }
    @Override public String getDeathMessage(String victim, String caster, String ability) {
        return String.format("%s was overwhelmed by %s's toxic spores!", victim, caster);
    }
}
