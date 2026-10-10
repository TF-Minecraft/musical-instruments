package net.tfminecraft.musicalinstruments.studio;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscItemsTest {
    @Test
    void encodingKeepsTheOriginalMaterialAndUnrelatedMetadataOnADetachedItem() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("MusicalInstruments");
        when(plugin.namespace()).thenReturn("musicalinstruments");
        DiscItems discs = new DiscItems(plugin);
        ItemStack original = mock(ItemStack.class);
        ItemStack detached = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(original.asOne()).thenReturn(detached);
        when(detached.getType()).thenReturn(Material.MUSIC_DISC_CAT);
        when(detached.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        Song song = new Song(UUID.randomUUID(), UUID.randomUUID(), "Author", "Title", List.of());
        assertSame(detached, discs.disc(song, original));
        assertEquals(Material.MUSIC_DISC_CAT, detached.getType());
        verify(data).remove(new NamespacedKey(plugin, "blank_disc"));
        verify(data).set(new NamespacedKey(plugin, "song_id"), PersistentDataType.STRING, song.id().toString());
        verify(detached).setItemMeta(meta);
        verify(original, never()).setItemMeta(any());
        verify(meta, never()).setCustomModelData(any());
        verify(detached, never()).setType(any());
    }
}
