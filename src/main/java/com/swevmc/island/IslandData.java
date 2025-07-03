package com.swevmc.island;

import java.util.ArrayList;
import java.util.List;

public class IslandData {
    private final String typeId;
    private final int x, z;
    private final List<Integer> upgrades = new ArrayList<>();
    private int borderLevel = 0;

    private String schematicFile = "";
    private int originX = 0, originY = 0, originZ = 0;
    private int sizeX = 100, sizeY = 100, sizeZ = 100;

    public IslandData(String typeId, int x, int z) {
        this.typeId = typeId;
        this.x = x;
        this.z = z;
    }

    public String getTypeId() { return typeId; }
    public int getX() { return x; }
    public int getZ() { return z; }
    public List<Integer> getUpgrades() { return upgrades; }
    public int getBorderLevel() { return borderLevel; }
    public void setBorderLevel(int borderLevel) { this.borderLevel = borderLevel; }

    public String getSchematicFile() { return schematicFile; }
    public void setSchematicFile(String schematicFile) { this.schematicFile = schematicFile; }
    public int getOriginX() { return originX; }
    public void setOriginX(int originX) { this.originX = originX; }
    public int getOriginY() { return originY; }
    public void setOriginY(int originY) { this.originY = originY; }
    public int getOriginZ() { return originZ; }
    public void setOriginZ(int originZ) { this.originZ = originZ; }
    public int getSizeX() { return sizeX; }
    public void setSizeX(int sizeX) { this.sizeX = sizeX; }
    public int getSizeY() { return sizeY; }
    public void setSizeY(int sizeY) { this.sizeY = sizeY; }
    public int getSizeZ() { return sizeZ; }
    public void setSizeZ(int sizeZ) { this.sizeZ = sizeZ; }
}
