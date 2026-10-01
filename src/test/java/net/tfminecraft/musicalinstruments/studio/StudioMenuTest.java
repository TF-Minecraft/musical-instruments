package net.tfminecraft.musicalinstruments.studio;

import net.kyori.adventure.text.Component;
import net.tfminecraft.musicalinstruments.InstrumentPlugin;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudioMenuTest {
    private final InstrumentPlugin plugin = mock(InstrumentPlugin.class);
    private final StudioService studio = mock(StudioService.class);
    private final StudioDialogs dialogs = mock(StudioDialogs.class);
    private final Player player = mock(Player.class);
    private final Inventory inventory = mock(Inventory.class);
    private final InventoryView view = mock(InventoryView.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final World world = mock(World.class);
    private final Block station = mock(Block.class);
    private final BukkitTask refreshTask = mock(BukkitTask.class);
    private StudioMenu menu;
    private Project project;

    @BeforeEach
    void setup() throws Exception {
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(10L), eq(10L))).thenReturn(refreshTask);
        when(server.createInventory(any(InventoryHolder.class), eq(54), any(Component.class))).thenAnswer(call -> {
            when(inventory.getHolder()).thenReturn(call.getArgument(0));
            return inventory;
        });
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("instruments.record")).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getBlockAt(0, 0, 0)).thenReturn(station);
        when(world.getBlockAt(any(Location.class))).thenReturn(station);
        when(station.getWorld()).thenReturn(world);
        when(station.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(station.getState()).thenReturn(mock(Jukebox.class));
        Track saved = new Track(1, 20, 1, false, List.of(new Note(0, "lute", "note", 4, 1)));
        Track pending = new Track(2, 20, 1, false, saved.notes());
        project = new Project(new Song(UUID.randomUUID(), player.getUniqueId(), "Player", "Song", List.of(saved)), pending, 100, false);
        when(studio.project(player)).thenReturn(project);
        when(studio.requireProject(player)).thenReturn(project);
        when(studio.settings()).thenReturn(new StudioSettings(4, 40, 20, 60, 8, 32, 2, 0, 2));
        when(studio.activity(player)).thenReturn(new StudioService.Activity(0, 0, 0, 0, false));
        StudioIcons icons = mock(StudioIcons.class);
        when(icons.item(any(), any(), anyList())).thenReturn(mock(ItemStack.class));
        menu = new StudioMenu(plugin, studio, icons, dialogs);
        menu.open(player, station);
    }

    private InventoryClickEvent click(int slot, ClickType type) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(slot);
        when(event.getClick()).thenReturn(type);
        menu.click(event);
        return event;
    }

    private void executeClick() {
        var callback = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), callback.capture());
        callback.getValue().run();
    }

    @Test
    void trackClickRecordsAndClosesOnlyAfterTheInventoryEvent() throws Exception {
        InventoryClickEvent event = click(12, ClickType.LEFT);
        verify(event).setCancelled(true);
        verify(studio, never()).record(any(), anyInt());
        executeClick();
        verify(studio).record(player, 2);
        verify(player).closeInventory();
    }

    @Test
    void volumeMixingPreservesNotesAndThePendingTake() throws Exception {
        click(19, ClickType.RIGHT);
        executeClick();
        var edited = ArgumentCaptor.forClass(Project.class);
        verify(studio).edit(eq(player), edited.capture());
        assertEquals(0.75f, edited.getValue().song().track(1).gain());
        assertEquals(project.song().track(1).notes(), edited.getValue().song().track(1).notes());
        assertEquals(project.pending(), edited.getValue().pending());
        assertEquals(project.song().id(), edited.getValue().song().id());
    }

    @Test
    void muteOnlyChangesTheSavedTrackMix() throws Exception {
        click(28, ClickType.LEFT);
        executeClick();
        var edited = ArgumentCaptor.forClass(Project.class);
        verify(studio).edit(eq(player), edited.capture());
        assertTrue(edited.getValue().song().track(1).muted());
        assertEquals(project.pending(), edited.getValue().pending());
    }

    @Test
    void publishingUsesInventoryDiscs() throws Exception {
        click(49, ClickType.LEFT);
        executeClick();
        verify(studio).publishFromInventory(player);
        verify(studio, never()).publish(player);
    }

    @Test
    void settingsOpensTheInputForm() throws Exception {
        click(45, ClickType.LEFT);
        executeClick();
        verify(dialogs).settings(player, new Location(world, 0, 0, 0));
    }

    @Test
    void bottomShiftClicksNumberKeysDoubleClicksAndDragsCannotMoveMenuItems() {
        for (var type : List.of(ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.DROP)) {
            verify(click(49, type)).setCancelled(true);
        }
        verify(click(54, ClickType.SHIFT_LEFT)).setCancelled(true);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(view);
        menu.drag(drag);
        verify(drag).setCancelled(true);
        verify(scheduler, never()).runTask(eq(plugin), any(Runnable.class));
    }

    @Test
    void movingAwayBeforeTheDeferredClickCannotRecordOrLoadTheStationChunk() throws Exception {
        click(10, ClickType.LEFT);
        when(player.getLocation()).thenReturn(new Location(world, 100, 0, 0));
        executeClick();
        verify(studio, never()).record(any(), anyInt());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(world, never()).getBlockAt(any(Location.class));
    }

    @Test
    void permissionRevocationPreventsADeferredRecording() throws Exception {
        click(10, ClickType.LEFT);
        when(player.hasPermission("instruments.record")).thenReturn(false);
        executeClick();
        verify(studio, never()).record(any(), anyInt());
    }

    @Test
    void closingThePluginCancelsRefreshAndClosesItsInventory() {
        menu.close();
        verify(refreshTask).cancel();
        verify(player).closeInventory();
    }
}
