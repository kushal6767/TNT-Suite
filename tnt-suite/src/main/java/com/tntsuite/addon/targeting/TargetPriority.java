package com.tntsuite.addon.targeting;

public enum TargetPriority {
    Closest("Closest"),
    LowestHealth("Lowest Health"),
    HighestHealth("Highest Health"),
    LookingAt("Looking At");

    private final String label;

    TargetPriority(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
