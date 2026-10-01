package net.tfminecraft.musicalinstruments.studio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

public final class DiscItems {
    private final NamespacedKey blankKey;
    private final NamespacedKey songKey;

    public DiscItems(Plugin plugin) {
        blankKey = new NamespacedKey(plugin, "blank_disc");
        songKey = new NamespacedKey(plugin, "song_id");
    }

    public boolean blank(ItemStack item) {
        return musicDisc(item) && Integer.valueOf(1).equals(
                item.getItemMeta().getPersistentDataContainer().get(blankKey, PersistentDataType.INTEGER));
    }

    public boolean recorded(ItemStack item) {
        return musicDisc(item) && item.getItemMeta().getPersistentDataContainer().has(songKey);
    }

    public boolean custom(ItemStack item) {
        return blank(item) || recorded(item);
    }

    public UUID songId(ItemStack item) {
        if (!recorded(item)) {
            return null;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(songKey, PersistentDataType.STRING);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }

    public boolean musicDisc(ItemStack item) {
        return item != null && item.getType().name().startsWith("MUSIC_DISC_");
    }

    public ItemStack blankDisc() {
        ItemStack item = new ItemStack(Material.MUSIC_DISC_13);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(blankKey, PersistentDataType.INTEGER, 1);
        meta.displayName(text("Blank recording disc", NamedTextColor.YELLOW));
        meta.lore(List.of(text("Use /music publish or /music copy", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack disc(Song song) {
        ItemStack item = new ItemStack(Material.MUSIC_DISC_13);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(songKey, PersistentDataType.STRING, song.id().toString());
        meta.displayName(text(song.title(), NamedTextColor.GOLD));
        meta.lore(List.of(text("By " + song.author(), NamedTextColor.GRAY),
                text(song.tracks().size() + " tracks | " + ((song.lengthTicks() + 19) / 20) + " seconds", NamedTextColor.GRAY),
                text("Edition: " + song.id(), NamedTextColor.DARK_GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }
}
