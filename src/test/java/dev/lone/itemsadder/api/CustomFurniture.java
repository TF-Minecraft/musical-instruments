package dev.lone.itemsadder.api;

import org.bukkit.entity.Entity;

import java.util.function.Function;

/** Stands in for the ItemsAdder furniture lookup that StudioStation reaches through reflection. */
public final class CustomFurniture {

    public static Function<Entity, CustomFurniture> lookup = entity -> null;

    private final String id;

    public CustomFurniture(String id) {
        this.id = id;
    }

    public static CustomFurniture byAlreadySpawned(Entity entity) {
        return lookup.apply(entity);
    }

    public String getNamespacedID() {
        return id;
    }
}
