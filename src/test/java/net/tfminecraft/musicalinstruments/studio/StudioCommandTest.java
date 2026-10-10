package net.tfminecraft.musicalinstruments.studio;

import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudioCommandTest {
    @Test
    void staffStudioOpensWithoutLookingAtABlock() throws Exception {
        StudioMenu menu = mock(StudioMenu.class);
        Player player = mock(Player.class);
        when(player.hasPermission("instruments.record")).thenReturn(true);
        when(player.hasPermission("instruments.studio")).thenReturn(true);
        StudioCommand command = new StudioCommand(mock(StudioService.class), menu);
        command.onCommand(player, mock(Command.class), "music", new String[]{"studio"});
        verify(menu).open(player, null);
        verify(player, never()).getTargetBlockExact(anyInt());
    }

    @Test
    void ordinaryRecordingPermissionDoesNotGrantStaffAccessOrTabSuggestion() {
        StudioMenu menu = mock(StudioMenu.class);
        Player player = mock(Player.class);
        when(player.hasPermission("instruments.record")).thenReturn(true);
        StudioCommand command = new StudioCommand(mock(StudioService.class), menu);
        command.onCommand(player, mock(Command.class), "music", new String[]{"studio"});
        verifyNoInteractions(menu);
        assertFalse(command.onTabComplete(player, mock(Command.class), "music", new String[]{""}).contains("studio"));
    }
}
