package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import me.ratatamakata.spellbreak.listeners.CustomDeathMessageListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

public class TestDeathMessageCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§6Usage: /testdeath <ability> [target]");
            player.sendMessage("§6Available abilities: LifeDrain, PlagueCloud, MistDash, UndyingRage, BoneChoir, Tentacles");
            return true;
        }

        String abilityName = args[0];
        Player target = args.length > 1 ? Bukkit.getPlayer(args[1]) : player;

        if (target == null) {
            player.sendMessage("§cTarget player not found!");
            return true;
        }

        Ability ability = Spellbreak.getInstance().getAbilityManager().getAbilityByName(abilityName);
        if (ability == null) {
            player.sendMessage("§cUnknown ability! Available: LifeDrain, PlagueCloud, MistDash, UndyingRage, BoneChoir, Tentacles");
            return true;
        }

        // Set up the death message metadata on the caster (player)
        player.setMetadata(CustomDeathMessageListener.METADATA_KEY_ABILITY_NAME, 
            new FixedMetadataValue(Spellbreak.getInstance(), abilityName));

        // Deal damage to trigger death message
        target.damage(1000, player);
        
        // Clean up metadata
        player.removeMetadata(CustomDeathMessageListener.METADATA_KEY_ABILITY_NAME, Spellbreak.getInstance());

        return true;
    }
} 