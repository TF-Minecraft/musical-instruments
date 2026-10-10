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
    private Runnable queued;

    @BeforeEach
    void setup() throws Exception {
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(10L), eq(10L))).thenReturn(refreshTask);
        when(server.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class))).thenAnswer(call -> {
            when(inventory.getHolder()).thenReturn(call.getArgument(0));
            return inventory;
        });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            queued = call.getArgument(1);
            return mock(BukkitTask.class);
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
        when(studio.listProjects(player)).thenReturn(List.of(project));
        when(studio.settings()).thenReturn(new StudioSettings(4, 40, 20, 60, 8, 32, 2, 0, 2));
        when(studio.activity(player)).thenReturn(new StudioService.Activity(0, 0, 0, 0, false));
        StudioIcons icons = mock(StudioIcons.class);
        when(icons.item(any(), any(), anyList())).thenReturn(mock(ItemStack.class));
        menu = new StudioMenu(plugin, studio, icons, dialogs);
        menu.openProject(player, station);
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
        assertNotNull(queued);
        Runnable action = queued;
        queued = null;
        action.run();
    }

    @Test
    void trackClickRecordsAndClosesOnlyAfterTheInventoryEvent() throws Exception {
        InventoryClickEvent event = click(18, ClickType.LEFT);
        verify(event).setCancelled(true);
        verify(studio, never()).record(any(), anyInt());
        executeClick();
        verify(studio).record(player, 2);
        verify(player).closeInventory();
    }

    @Test
    void volumeMixingPreservesNotesAndThePendingTake() throws Exception {
        click(10, ClickType.RIGHT);
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
        click(11, ClickType.LEFT);
        executeClick();
        var edited = ArgumentCaptor.forClass(Project.class);
        verify(studio).edit(eq(player), edited.capture());
        assertTrue(edited.getValue().song().track(1).muted());
        assertEquals(project.pending(), edited.getValue().pending());
    }

    @Test
    void publishingUsesInventoryDiscs() throws Exception {
        click(50, ClickType.LEFT);
        executeClick();
        verify(studio).publishFromInventory(player);
        verify(studio, never()).publish(player);
    }

    @Test
    void settingsOpensTheInputForm() throws Exception {
        click(4, ClickType.LEFT);
        executeClick();
        verify(dialogs).settings(player, new Location(world, 0, 0, 0));
    }

    @Test
    void bottomShiftClicksNumberKeysDoubleClicksAndDragsCannotMoveMenuItems() {
        for (var type : List.of(ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.DROP)) {
            verify(click(51, type)).setCancelled(true);
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
        click(9, ClickType.LEFT);
        when(player.getLocation()).thenReturn(new Location(world, 100, 0, 0));
        executeClick();
        verify(studio, never()).record(any(), anyInt());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(world, never()).getBlockAt(any(Location.class));
    }

    @Test
    void permissionRevocationPreventsADeferredRecording() throws Exception {
        click(9, ClickType.LEFT);
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

    @Test
    void studioOpensAProjectLibraryAndSelectingASongOpensItsEditor() throws Exception {
        menu.open(player, station);
        verify(studio, never()).create(any(), anyString(), anyBoolean());
        click(10, ClickType.LEFT);
        executeClick();
        verify(studio).selectProject(player, project.song().id());
        click(18, ClickType.LEFT);
        executeClick();
        verify(studio).record(player, 2);
    }

    @Test
    void newProjectButtonUsesATitleForm() throws Exception {
        menu.open(player, station);
        click(49, ClickType.LEFT);
        executeClick();
        verify(dialogs).create(player, new Location(world, 0, 0, 0));
    }

    @Test
    void takingActionOnAnotherRowsPendingTakeCannotAcceptIt() throws Exception {
        click(15, ClickType.LEFT); // Keep on track 1, while the pending take belongs to track 2.
        executeClick();
        verify(studio, never()).edit(any(), any());
        verify(player).sendMessage("No pending take on this track");
    }

    @Test
    void eachRowsReviewAndTheGlobalPreviewTargetDifferentAudio() throws Exception {
        click(13, ClickType.LEFT);
        executeClick();
        verify(studio).previewTrack(player, 1);
        click(23, ClickType.LEFT);
        executeClick();
        verify(studio).preview(player, true);
        click(47, ClickType.LEFT);
        executeClick();
        verify(studio).preview(player, false);
        click(48, ClickType.LEFT);
        executeClick();
        verify(studio).stop(player);
    }

    @Test
    void projectActionsLiveOnTheirOwnScreen() throws Exception {
        click(51, ClickType.LEFT);
        executeClick();
        click(14, ClickType.LEFT);
        executeClick();
        verify(studio).prepareBlankFromInventory(player);
        click(18, ClickType.LEFT);
        executeClick();
        verify(dialogs).reset(player, new Location(world, 0, 0, 0));
        verify(studio, never()).record(any(), anyInt());
    }

    @Test
    void switchingTheActiveProjectRejectsAnAlreadyQueuedTrackClick() throws Exception {
        click(9, ClickType.LEFT);
        Song other = new Song(UUID.randomUUID(), player.getUniqueId(), "Player", "Other", List.of());
        when(studio.requireProject(player)).thenReturn(new Project(other, null, 100, true));
        executeClick();
        verify(studio, never()).record(any(), anyInt());
    }

    @Test
    void tracksFiveToEightHaveTheirOwnPageAndPreserveTrackNumbers() throws Exception {
        when(studio.settings()).thenReturn(new StudioSettings(8, 40, 20, 60, 8, 32, 2, 0, 2));
        menu.openProject(player, station);
        click(53, ClickType.LEFT);
        executeClick();
        click(9, ClickType.LEFT);
        executeClick();
        verify(studio).record(player, 5);
    }

    @Test
    void projectPaginationSelectsTheSongShownOnTheNextPage() throws Exception {
        List<Project> library = java.util.stream.IntStream.range(0, 29).mapToObj(index ->
                new Project(new Song(UUID.randomUUID(), player.getUniqueId(), "Player", "Song " + index, List.of()), null, 100, true)).toList();
        when(studio.listProjects(player)).thenReturn(library);
        menu.open(player, station);
        click(53, ClickType.LEFT);
        executeClick();
        when(studio.requireProject(player)).thenReturn(library.get(28));
        click(10, ClickType.LEFT);
        executeClick();
        verify(studio).selectProject(player, library.get(28).song().id());
    }

    @Test
    void keepingTheMatchingRowsTakePreservesTheOtherSavedTracks() throws Exception {
        click(24, ClickType.LEFT);
        executeClick();
        var result = ArgumentCaptor.forClass(Project.class);
        verify(studio).edit(eq(player), result.capture());
        assertNull(result.getValue().pending());
        assertEquals(project.song().track(1), result.getValue().song().track(1));
        assertEquals(project.pending(), result.getValue().song().track(2));
    }
}
