package me.ratatamakata.spellbreak.abilities.impl;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.util.AbilityDamage;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class PurifyingPrismAbility implements Ability {

    private static final Map<UUID, PurifyingPrismAbility> activePrisms = new HashMap<>();

    private String name = "PurifyingPrism";
    private String description = "Deploys a rotating prism above the caster that shoots damaging rays at foes.";
    private int cooldown = 25;
    private int manaCost = 50;
    private String requiredClass = "lightbringer";

    private int prismDurationTicks = 200;
    private int maxRayInstances = 10;
    private int rayFireIntervalTicks = 20;
    private double raySearchRadius = 10.0;
    private double damagePerRay = 3.0;
    private double prismHeightOffset = 2.5;
    private int prismVisualParticleCount = 8;
    private float prismRotationSpeed = 0.1f;

    private Particle.DustOptions prismBodyParticle = new Particle.DustOptions(Color.fromRGB(255, 255, 180), 0.9f);
    private Particle.DustOptions damageRayParticle = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.1f);

    private transient Player caster;
    private transient BukkitTask activePrismLogicTask = null;
    private transient BukkitTask prismVisualRenderTask = null;
    private transient int raysFiredCount = 0;
    private transient int currentPrismTick = 0;
    private transient Location currentPrismCenterLocation = null;
    private transient double currentPrismRotationAngle = 0.0;

    private boolean successfulActivation = false;
    private final AbilityDamage abilityDamage;

    public PurifyingPrismAbility(AbilityDamage abilityDamage) {
        this.abilityDamage = abilityDamage;
    }

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public int getCooldown() { return cooldown; }
    @Override public int getManaCost() { return manaCost; }
    @Override public String getRequiredClass() { return requiredClass; }
    @Override public boolean isTriggerAction(Action action) { return false; }

    @Override
    public void activate(Player player) {
        this.successfulActivation = false;

        if (activePrisms.containsKey(player.getUniqueId())) {
            PurifyingPrismAbility existingPrism = activePrisms.get(player.getUniqueId());
            if (existingPrism != null) {
                existingPrism.deactivatePrism(false);
            }
        }

        this.caster = player;
        this.raysFiredCount = 0;
        this.currentPrismTick = 0;
        this.currentPrismRotationAngle = 0.0;
        this.currentPrismCenterLocation = getPrismBaseLocation(player);

        activePrisms.put(player.getUniqueId(), this);

        this.prismVisualRenderTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (caster == null || !caster.isOnline() || currentPrismCenterLocation == null) {
                    deactivatePrism(true);
                    return;
                }
                currentPrismCenterLocation = getPrismBaseLocation(caster);

                World world = caster.getWorld();
                double prismSize = 0.7;

                Vector top = new Vector(0, prismSize, 0);
                Vector bottom = new Vector(0, -prismSize, 0);
                Vector[] equatorial = {
                        new Vector(prismSize * 0.7, 0, 0),
                        new Vector(0, 0, prismSize * 0.7),
                        new Vector(-prismSize * 0.7, 0, 0),
                        new Vector(0, 0, -prismSize * 0.7)
                };

                Consumer<Vector> spawnParticleAtRel = (relVec) -> {
                    Location particleLoc = currentPrismCenterLocation.clone().add(relVec);
                    world.spawnParticle(Particle.DUST, particleLoc, 1, 0,0,0,0, prismBodyParticle);
                };

                spawnParticleAtRel.accept(top);
                spawnParticleAtRel.accept(bottom);
                for (Vector eqPoint : equatorial) {
                    spawnParticleAtRel.accept(eqPoint);
                }

                for (Vector eqPoint : equatorial) {
                    drawEdge(currentPrismCenterLocation, top, eqPoint, prismVisualParticleCount / 4, world, prismBodyParticle);
                }
                for (Vector eqPoint : equatorial) {
                    drawEdge(currentPrismCenterLocation, bottom, eqPoint, prismVisualParticleCount / 4, world, prismBodyParticle);
                }
                for (int i = 0; i < equatorial.length; i++) {
                    drawEdge(currentPrismCenterLocation, equatorial[i], equatorial[(i + 1) % equatorial.length], prismVisualParticleCount / 4, world, prismBodyParticle);
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), 0L, 3L);

        this.activePrismLogicTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (caster == null || !caster.isOnline() || currentPrismCenterLocation == null) {
                    deactivatePrism(true);
                    return;
                }

                if (caster.isOnline()) {
                    double timeRemaining = (prismDurationTicks - currentPrismTick) / 20.0;
                    int raysRemaining = maxRayInstances - raysFiredCount;
                    String actionBarMsg = String.format("§bPrism: §f%.1fs §7| §e%d rays left", Math.max(0, timeRemaining), raysRemaining);
                    caster.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarMsg));
                }

                currentPrismTick += rayFireIntervalTicks;

                if (raysFiredCount >= maxRayInstances || currentPrismTick > prismDurationTicks) {
                    deactivatePrism(true);
                    return;
                }

                World world = caster.getWorld();
                LivingEntity target = findTarget();
                if (target != null) {
                    drawRayBetweenPoints(currentPrismCenterLocation, target.getEyeLocation(), damageRayParticle, world);
                    abilityDamage.damage(target, damagePerRay, caster, PurifyingPrismAbility.this, "PurifyingPrism");
                    world.playSound(currentPrismCenterLocation, Sound.ENTITY_BLAZE_SHOOT, 0.7f, 1.6f);
                    world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
                    raysFiredCount++;
                }
            }
        }.runTaskTimer(Spellbreak.getInstance(), rayFireIntervalTicks, rayFireIntervalTicks);

        caster.getWorld().playSound(currentPrismCenterLocation, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        this.successfulActivation = true;
    }

    private Location getPrismBaseLocation(Player player) {
        return player.getEyeLocation().add(0, prismHeightOffset - (player.isSneaking() ? 0.25 : 0) , 0);
    }

    public void deactivatePrism(boolean playSound) {
        if (this.caster != null) {
            activePrisms.remove(this.caster.getUniqueId());
            if (caster.isOnline()) {
                caster.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
            }
            if (playSound && this.currentPrismCenterLocation != null) {
                caster.getWorld().playSound(this.currentPrismCenterLocation, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
            }
        }

        if (this.activePrismLogicTask != null) {
            this.activePrismLogicTask.cancel();
        }
        if (this.prismVisualRenderTask != null) {
            this.prismVisualRenderTask.cancel();
        }
        this.activePrismLogicTask = null;
        this.prismVisualRenderTask = null;
        this.caster = null;
        this.currentPrismCenterLocation = null;
    }

    @Override
    public boolean isSuccessful() {
        return successfulActivation;
    }

    @Override
    public void loadConfig() {
        FileConfiguration cfg = Spellbreak.getInstance().getConfig();
        String base = "abilities.purifyingprism.";
        name = cfg.getString(base + "name", name);
        description = cfg.getString(base + "description", description);
        cooldown = cfg.getInt(base + "cooldown", cooldown);
        manaCost = cfg.getInt(base + "mana-cost", manaCost);
        requiredClass = cfg.getString(base + "required-class", requiredClass);

        prismDurationTicks = cfg.getInt(base + "prism-duration-ticks", prismDurationTicks);
        maxRayInstances = cfg.getInt(base + "max-ray-instances", maxRayInstances);
        rayFireIntervalTicks = cfg.getInt(base + "ray-fire-interval-ticks", rayFireIntervalTicks);
        raySearchRadius = cfg.getDouble(base + "ray-search-radius", raySearchRadius);
        damagePerRay = cfg.getDouble(base + "damage-per-ray", damagePerRay);
        prismHeightOffset = cfg.getDouble(base + "prism-height-offset", prismHeightOffset);
        prismVisualParticleCount = cfg.getInt(base + "prism-visual-particle-count", prismVisualParticleCount);
        prismRotationSpeed = (float) cfg.getDouble(base + "prism-rotation-speed", prismRotationSpeed);
    }

    @Override
    public String getDeathMessage(String victimName, String casterName, String subAbilityName) {
        return String.format("§e%s §bwas obliterated by §e%s§b's prismatic energy.", victimName, casterName);
    }

    private LivingEntity findTarget() {
        if (caster == null || currentPrismCenterLocation == null) return null;

        return caster.getWorld().getNearbyEntities(currentPrismCenterLocation, raySearchRadius, raySearchRadius, raySearchRadius)
                .stream()
                .filter(e -> e instanceof LivingEntity &&
                        !e.equals(caster) &&
                        !e.isDead() &&
                        !(e instanceof org.bukkit.entity.ArmorStand))
                .map(e -> (LivingEntity) e)
                .min((e1, e2) -> Double.compare(
                        e1.getLocation().distanceSquared(currentPrismCenterLocation),
                        e2.getLocation().distanceSquared(currentPrismCenterLocation)
                ))
                .orElse(null);
    }

    private void drawRayBetweenPoints(Location start, Location end, Particle.DustOptions dust, World world) {
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        if (distance < 0.1) return;
        direction.normalize();
        double particleSpacing = 0.3;

        for (double d = 0; d < distance; d += particleSpacing) {
            Location particleLoc = start.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, dust);
        }
    }

    private void drawEdge(Location center, Vector relVec1, Vector relVec2, int particleCount, World world, Particle.DustOptions dustOptions) {
        if (particleCount <= 0) particleCount = 1;
        Vector edgeDirection = relVec2.clone().subtract(relVec1);
        double edgeLength = edgeDirection.length();
        if (edgeLength < 0.01) return;
        edgeDirection.normalize();

        for (int i = 0; i <= particleCount; i++) {
            double step = (edgeLength / particleCount) * i;
            Location particleLoc = center.clone().add(relVec1).add(edgeDirection.clone().multiply(step));
            world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, dustOptions);
        }
    }
}