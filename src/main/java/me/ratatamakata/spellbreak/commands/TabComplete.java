// src/main/java/me/ratatamakata/spellbreak/commands/TabComplete.java
package me.ratatamakata.spellbreak.commands;

import me.ratatamakata.spellbreak.Spellbreak;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TabComplete implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd,
                                      String alias, String[] args) {
        String name = cmd.getName().toLowerCase();
        String last = args.length > 0 ? args[args.length-1].toLowerCase() : "";

        if (name.equals("class") && args.length == 1) {
            return Spellbreak.getInstance().getSpellClassManager()
                    .getAllClasses().stream()
                    .filter(c -> c.toLowerCase().startsWith(last))
                    .collect(Collectors.toList());
        }
        if (name.equals("bind")) {
            if (args.length == 1) {
                return IntStream.rangeClosed(1,9)
                        .mapToObj(String::valueOf)
                        .filter(s1 -> s1.startsWith(last))
                        .collect(Collectors.toList());
            } else if (args.length == 2 && s instanceof Player p) {
                String cls = Spellbreak.getInstance()
                        .getPlayerDataManager().getPlayerClass(p.getUniqueId());
                return Spellbreak.getInstance().getSpellClassManager()
                        .getClassAbilities(cls).stream()
                        .filter(a -> a.toLowerCase().startsWith(last))
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }
}
