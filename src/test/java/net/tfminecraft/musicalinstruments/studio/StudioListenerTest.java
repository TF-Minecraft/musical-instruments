package net.tfminecraft.musicalinstruments.studio;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudioListenerTest {
    private StudioService studio;
    private StudioListener listener;
    private DiscItems discs;
    private Player player;
    private PlayerInventory inventory;
    private Block block;
    private Jukebox box;
    private ItemStack disc;

    @BeforeEach
    void setup() {
        studio = mock(StudioService.class);
        discs = mock(DiscItems.class);
        listener = new StudioListener(studio, mock(StudioMenu.class));
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        block = mock(Block.class);
        box = mock(Jukebox.class);
        disc = mock(ItemStack.class);
        when(studio.discs()).thenReturn(discs);
        when(block.getState()).thenReturn(box);
        when(player.getInventory()).thenReturn(inventory);
        when(player.hasPermission("instruments.play")).thenReturn(true);
        when(discs.custom(disc)).thenReturn(true);
    }

    private PlayerInteractEvent event(EquipmentSlot hand) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, disc, block, BlockFace.UP, hand);
    }

    @Test
    void customInsertionIsCancelledAndMovesExactlyOneDisc() throws Exception {
        ItemStack remaining = mock(ItemStack.class);
        when(disc.clone()).thenReturn(remaining);
        when(disc.getAmount()).thenReturn(2);
        when(remaining.getAmount()).thenReturn(1);
        PlayerInteractEvent event = event(EquipmentSlot.HAND);
        listener.interact(event);
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
        verify(studio).play(block, disc);
        verify(remaining).setAmount(1);
        verify(inventory).setItemInMainHand(remaining);
    }

    @Test
    void offhandInsertionUpdatesTheCorrectInventorySlot() throws Exception {
        ItemStack remaining = mock(ItemStack.class);
        when(disc.clone()).thenReturn(remaining);
        when(disc.getAmount()).thenReturn(2);
        when(remaining.getAmount()).thenReturn(1);
        listener.interact(event(EquipmentSlot.OFF_HAND));
        verify(studio).play(block, disc);
        verify(inventory).setItemInOffHand(remaining);
        verify(inventory, never()).setItemInMainHand(any());
    }

    @Test
    void failedInsertionPreservesThePlayersDisc() throws Exception {
        doThrow(new IOException("Missing edition")).when(studio).play(block, disc);
        listener.interact(event(EquipmentSlot.HAND));
        verifyNoInteractions(inventory);
        verify(studio).failure(eq(player), any(IOException.class));
    }

    @Test
    void protectionDenialsAndPermissionChecksPreventPlayback() throws Exception {
        PlayerInteractEvent protectedEvent = event(EquipmentSlot.HAND);
        protectedEvent.setUseInteractedBlock(Event.Result.DENY);
        listener.interact(protectedEvent);
        when(player.hasPermission("instruments.play")).thenReturn(false);
        listener.interact(event(EquipmentSlot.HAND));
        verify(studio, never()).play(any(), any());
        verifyNoInteractions(inventory);
    }

    @Test
    void itemUseDenialPreventsCustomInsertion() throws Exception {
        PlayerInteractEvent event = event(EquipmentSlot.HAND);
        event.setUseItemInHand(Event.Result.DENY);
        listener.interact(event);
        verify(studio, never()).play(any(), any());
        verifyNoInteractions(inventory);
    }

    @Test
    void recordedDiscIsEjectedOnlyForTheMainHandEvent() {
        when(box.getRecord()).thenReturn(disc);
        listener.interact(event(EquipmentSlot.HAND));
        listener.interact(event(EquipmentSlot.OFF_HAND));
        verify(box, times(1)).eject();
        verify(studio, times(1)).stop(block);
    }

    @Test
    void vanillaDiscsRetainTheirNormalInteraction() throws Exception {
        when(discs.custom(disc)).thenReturn(false);
        PlayerInteractEvent event = event(EquipmentSlot.HAND);
        Event.Result original = event.useInteractedBlock();
        listener.interact(event);
        assertEquals(original, event.useInteractedBlock());
        verify(studio, never()).play(any(), any());
    }
}
