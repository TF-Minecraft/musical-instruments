package dev.lone.itemsadder.api;

import org.bukkit.block.Block;

import java.util.function.Function;

/** Stands in for the ItemsAdder placed-block lookup that StudioStation reaches through reflection. */
public final class CustomBlock {

    public static Function<Block, CustomBlock> lookup = block -> null;

    private final String id;

    public CustomBlock(String id) {
        this.id = id;
    }

    public static CustomBlock byAlreadyPlaced(Block block) {
        return lookup.apply(block);
    }

    public String getNamespacedID() {
        return id;
    }
}
