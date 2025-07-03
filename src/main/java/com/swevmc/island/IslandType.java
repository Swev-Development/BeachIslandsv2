package com.swevmc.island;

public class IslandType {
    private final String id;
    private final String name;
    private final String schematic;

    public IslandType(String id, String name, String schematic) {
        this.id = id;
        this.name = name;
        this.schematic = schematic;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSchematic() { return schematic; }
}
