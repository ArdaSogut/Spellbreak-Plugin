// src/main/java/me/ratatamakata/spellbreak/managers/SpellClassManager.java
package me.ratatamakata.spellbreak.managers;

import me.ratatamakata.spellbreak.Spellbreak;
import me.ratatamakata.spellbreak.classes.SpellClass;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class SpellClassManager {
    private final Map<String,SpellClass> classes = new HashMap<>();

    public void loadClasses() {
        ConfigurationSection sec = Spellbreak.getInstance()
                .getConfig().getConfigurationSection("classes");
        if (sec==null) return;
        for(String key : sec.getKeys(false)) {
            String proper = key;  // expect key is capitalized in config.yml
            List<String> abs = sec.getStringList(key+".abilities");
            classes.put(proper.toLowerCase(), new SpellClass(proper, abs));
        }
    }

    public String getProperClassName(String input) {
        return classes.values().stream()
                .map(SpellClass::getName)
                .filter(n -> n.equalsIgnoreCase(input))
                .findFirst().orElse(null);
    }

    public List<String> getClassAbilities(String cls) {
        SpellClass sc = classes.get(cls.toLowerCase());
        return sc!=null?sc.getAbilities():List.of();
    }

    public List<String> getAllClasses() {
        List<String> out = new ArrayList<>();
        for (SpellClass sc : classes.values()) out.add(sc.getName());
        return out;
    }
    public SpellClass getClassByName(String name) {
        return classes.get(name.toLowerCase()); // Case-insensitive lookup
    }

}
