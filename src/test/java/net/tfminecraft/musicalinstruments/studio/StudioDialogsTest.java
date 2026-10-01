package net.tfminecraft.musicalinstruments.studio;

import net.tfminecraft.musicalinstruments.InstrumentPlugin;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudioDialogsTest {
    @Test
    void applyingTheFormRetainsTheLatestTracksAndPendingTake() throws Exception {
        Player player = mock(Player.class);
        StudioService studio = mock(StudioService.class);
        Track track = new Track(1, 20, 0.5f, true, List.of(new Note(0, "lute", "note", 4, 1)));
        Track pending = new Track(2, 20, 1, false, track.notes());
        Song song = new Song(UUID.randomUUID(), UUID.randomUUID(), "Author", "Old", List.of(track));
        when(studio.requireProject(player)).thenReturn(new Project(song, pending, 100, false));
        StudioDialogs dialogs = new StudioDialogs(mock(InstrumentPlugin.class), studio, (p, b) -> {});
        dialogs.applySettings(player, song.id(), "  New title  ", 140, true);
        var result = ArgumentCaptor.forClass(Project.class);
        verify(studio).edit(eq(player), result.capture());
        assertEquals("New title", result.getValue().song().title());
        assertEquals(song.tracks(), result.getValue().song().tracks());
        assertEquals(song.owner(), result.getValue().song().owner());
        assertEquals(pending, result.getValue().pending());
        assertEquals(140, result.getValue().bpm());
        assertTrue(result.getValue().metronome());
        assertThrows(IllegalArgumentException.class, () -> dialogs.applySettings(player, UUID.randomUUID(), "Stale", 100, false));
        assertThrows(IllegalArgumentException.class, () -> dialogs.applySettings(player, song.id(), "New", 241, false));
        assertThrows(IllegalArgumentException.class, () -> dialogs.applySettings(player, song.id(), " ", 100, false));
        verify(studio, times(1)).edit(any(), any());
    }
}
