package net.tfminecraft.musicalinstruments.studio;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Vanilla items and native text styling; no additional resource pack is needed. */
public final class StudioIcons {
    public ItemStack item(Material material, Component title, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(title.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        if (material.name().startsWith("MUSIC_DISC_")) hideDiscDescription(item);
        return item;
    }

    static void hideDiscDescription(ItemStack item) {
        TooltipDisplay previous = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        var display = TooltipDisplay.tooltipDisplay();
        if (previous != null) display.hideTooltip(previous.hideTooltip()).hiddenComponents(previous.hiddenComponents());
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, display.addHiddenComponents(DataComponentTypes.JUKEBOX_PLAYABLE));
    }
}
