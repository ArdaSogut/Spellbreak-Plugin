// src/main/java/me/ratatamakata/spellbreak/commands/BindCommand.java
package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.abilities.Ability;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class BindCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("§cOnly players can use this.");
            return true;
        }
        if (args.length != 2) {
            p.sendMessage("§6Usage: /bind <slot 1-9> <ability>");
            return true;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[0]) - 1;
            if (slot < 0 || slot > 8) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            p.sendMessage("§cSlot must be 1-9.");
            return true;
        }

        String requested = args[1].replaceAll("\\s+", "").toLowerCase();
        var manager = Spellbreak.getInstance().getAbilityManager();
        // match against registered abilities
        String match = manager.getAllAbilities().stream()
                .map(Ability::getName)
                .filter(name -> name.replaceAll("\\s+","").equalsIgnoreCase(requested))
                .findFirst().orElse(null);
        if (match == null) {
            p.sendMessage("§cUnknown ability. Available: " +
                    manager.getAllAbilities().stream()
                            .map(Ability::getName)
                            .collect(Collectors.joining(", ")));
            return true;
        }

        // class check
        String cls = Spellbreak.getInstance()
                .getPlayerDataManager().getPlayerClass(p.getUniqueId());
        if (!Spellbreak.getInstance().getSpellClassManager()
                .getClassAbilities(cls)
                .contains(match)) {
            p.sendMessage("§cYour class cannot use '" + match + "'.");
            return true;
        }

        Spellbreak.getInstance().getPlayerDataManager()
                .bindAbility(p.getUniqueId(), slot, match);
        p.sendMessage("§aBound §e" + match + " §ato slot §e" + (slot+1));
        return true;
    }
}
