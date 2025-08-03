// src/main/java/me/ratatamakata/spellbreak/managers/AbilityManager.java
package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.abilities.impl.*;
import me.ratatamakata.spellbreak.abilities.impl.MeteorLashAbility;
import me.ratatamakata.spellbreak.listeners.StunHandler;

import java.util.*;

public class AbilityManager {
    private final Map<String, Ability> map = new HashMap<>();
    private final Spellbreak plugin; // Add this line to store the plugin

    public AbilityManager(Spellbreak plugin) {
        this.plugin = plugin; // Initialize the plugin
    }

    public void loadAbilities() {
        // Necro abilities
        register(new LifeDrainAbility());
        register(new PlagueCloudAbility());
        register(new MistDashAbility());
        StunHandler stunHandler = plugin.getStunHandler();  // Assuming you have a method in Spellbreak to get StunHandler
        register(new TentaclesAbility(stunHandler));
        register(new BoneChoirAbility());
        register(new UndyingPactAbility());

        // Archdruid abilities
        register(new NatureStepAbility());
        register(new AmbushSlashAbility());
        register(new SporeBlossomAbility());
        register(new CanopyCrashAbility());
        register(new QuillflareSurgeAbility());
        register(new IronwoodShellAbility());
        // TODO: Register other Archdruid abilities here later

        // Lightbringer abilities
        register(new LightCageAbility());
        register(new ConsecrationAbility());
        register(new PurifyingPrismAbility(Spellbreak.getInstance().getAbilityDamage()));
        register(new RadiantDashAbility());
        register(new BeaconOfClarityAbility());
        register(new RadiantPhaseAbility());

        // Mindshaper abilities
        register(new EchoPulseAbility());
        register(new PhantomEchoAbility());
        register(new NeuralTrapAbility());
        register(new DreamwalkerAbility());
        register(new ShadowCreaturesAbility(Spellbreak.getInstance()));
        register(new CloneSwarmAbility());

        // Elementalist abilities
        register(new TidepoolAbility());
        register(new EmberstormAbility());
        register(new GaleVortexAbility());
        register(new EarthShardsAbility());
        register(new AvalancheAbility());
        register(new ThunderSlamAbility());

        // Runesmith abilities
        register(new WardingSigilAbility());
        register(new RunecarverAbility());
        register(new RunicJumpPadAbility());
        register(new RunicTurretAbility());
        register(new BladeSpinAbility());
        register(new SwarmSigilAbility());

        // Starcaller abilities
        register(new MeteorLashAbility());


        map.values().forEach(Ability::loadConfig);
    }
    private void register(Ability a) {
        map.put(a.getName().toLowerCase(), a);
    }
    public Ability getAbilityByName(String name) {
        return map.get(name.toLowerCase());
    }
    public Collection<Ability> getAllAbilities() {
        return map.values();
    }
}
