// src/main/java/me/ratatamakata/spellbreak/classes/SpellClass.java
package me.ratatamakata.spellbreak.classes;

import java.util.List;

public class SpellClass {
    private final String name;
    private final List<String> abilities;

    public SpellClass(String name, List<String> abilities) {
        this.name = name;
        this.abilities = abilities;
    }
    public String getName() { return name; }
    public List<String> getAbilities() { return abilities; }
}
