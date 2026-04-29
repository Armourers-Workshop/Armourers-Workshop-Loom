package net.cocoonmc.loom.core;

import org.gradle.api.Project;

public enum Platform {

    FABRIC("Fabric"),
    FORGE("Forge"),
    NEOFORGE("NeoForge"),
    UNKNOWN("Common");

    public final String name;

    Platform(String name) {
        this.name = name;
    }

    public boolean isFabricLike() {
        return this == FABRIC;
    }

    public boolean isForgeLike() {
        return this == FORGE || this == NEOFORGE;
    }

    public static Platform by(Project project) {
        var value = project.findProperty("loom.platform");
        if ("fabric".equals(value)) {
            return FABRIC;
        }
        if ("forge".equals(value)) {
            return FORGE;
        }
        if ("neoforge".equals(value)) {
            return NEOFORGE;
        }
        return UNKNOWN;
    }
}
