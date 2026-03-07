package me.ratatamakata.spellbreak.player;

/**
 * Represents a single character slot for a player.
 * Each slot has an associated class and ability bindings.
 */
public class CharacterSlot {

    private final int slotIndex;
    private String className;   // null when the slot is empty
    private String[] bindings;  // hotbar bindings for this slot

    /** Creates an empty slot. */
    public CharacterSlot(int slotIndex) {
        this.slotIndex = slotIndex;
        this.className = null;
        this.bindings = new String[9];
    }

    /** Creates a populated slot. */
    public CharacterSlot(int slotIndex, String className, String[] bindings) {
        this.slotIndex = slotIndex;
        this.className = className;
        this.bindings = bindings != null ? bindings : new String[9];
    }

    public int getSlotIndex() { return slotIndex; }

    public boolean isEmpty() { return className == null || className.equalsIgnoreCase("None"); }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String[] getBindings() { return bindings; }
    public void setBindings(String[] bindings) { this.bindings = bindings != null ? bindings : new String[9]; }

    public void clearBindings() { this.bindings = new String[9]; }
}
