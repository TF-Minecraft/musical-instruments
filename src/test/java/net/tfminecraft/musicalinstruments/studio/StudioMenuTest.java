package net.tfminecraft.musicalinstruments.studio;

import net.kyori.adventure.text.Component;
import net.tfminecraft.musicalinstruments.InstrumentPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
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
            when(inventory.getSize()).thenReturn(call.getArgument(1));
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
        when(station.getType()).thenReturn(Material.LOOM);
        when(studio.station()).thenReturn(StudioStation.vanilla(Material.LOOM));
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
        InventoryClickEvent event = click(19, ClickType.LEFT);
        verify(event).setCancelled(true);
        verify(studio, never()).record(any(), anyInt());
        executeClick();
        verify(studio).record(player, 2);
        verify(player).closeInventory();
    }

    @Test
    void volumeMixingPreservesNotesAndThePendingTake() throws Exception {
        click(11, ClickType.RIGHT);
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
        click(12, ClickType.LEFT);
        executeClick();
        var edited = ArgumentCaptor.forClass(Project.class);
        verify(studio).edit(eq(player), edited.capture());
        assertTrue(edited.getValue().song().track(1).muted());
        assertEquals(project.pending(), edited.getValue().pending());
    }

    @Test
    void publishingRequiresADiscOnTheCursor() throws Exception {
        click(50, ClickType.LEFT);
        assertNull(queued);
        verify(studio, never()).publishFromInventory(player);
        verify(studio, never()).publish(player);
    }

    @Test
    void settingsOpensItsInventoryAndTitleUsesOnlyARenameForm() throws Exception {
        click(4, ClickType.LEFT);
        executeClick();
        assertEquals(27, inventory.getSize());
        click(11, ClickType.LEFT);
        executeClick();
        verify(dialogs).rename(player, new Location(world, 0, 0, 0));
    }

    @Test
    void bottomShiftClicksNumberKeysDoubleClicksAndDragsCannotMoveMenuItems() {
        for (var type : List.of(ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.DROP)) {
            verify(click(51, type)).setCancelled(true);
        }
        verify(click(54, ClickType.SHIFT_LEFT)).setCancelled(true);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(view);
        when(drag.getRawSlots()).thenReturn(java.util.Set.of(9));
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

    @Test
    void studioOpensAProjectLibraryAndSelectingASongOpensItsEditor() throws Exception {
        menu.open(player, station);
        verify(studio, never()).create(any(), anyString(), anyBoolean());
        click(9, ClickType.LEFT);
        executeClick();
        verify(studio).selectProject(player, project.song().id());
        click(19, ClickType.LEFT);
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
    void settingsHasTempoMetronomeAndConfirmedResetWithoutDiscActions() throws Exception {
        click(51, ClickType.LEFT);
        executeClick();
        click(13, ClickType.RIGHT);
        executeClick();
        var changed = ArgumentCaptor.forClass(Project.class);
        verify(studio).edit(eq(player), changed.capture());
        assertEquals(95, changed.getValue().bpm());
        click(15, ClickType.LEFT);
        executeClick();
        verify(studio).edit(player, new Project(project.song(), project.pending(), 100, true));
        click(18, ClickType.LEFT);
        executeClick();
        verify(dialogs).reset(player, new Location(world, 0, 0, 0));
        verify(studio, never()).record(any(), anyInt());
        verify(studio, never()).prepareBlankFromInventory(any());
        verify(studio, never()).copyFromInventory(any());
    }

    @Test
    void switchingTheActiveProjectRejectsAnAlreadyQueuedTrackClick() throws Exception {
        click(10, ClickType.LEFT);
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
        click(10, ClickType.LEFT);
        executeClick();
        verify(studio).record(player, 5);
    }

    @Test
    void libraryFillsAllThirtySixSlotsAndRightClickRequestsDeletion() throws Exception {
        List<Project> library = java.util.stream.IntStream.range(0, 36).mapToObj(index ->
                new Project(new Song(UUID.randomUUID(), player.getUniqueId(), "Player", "Song " + index, List.of()), null, 100, true)).toList();
        when(studio.listProjects(player)).thenReturn(library);
        menu.open(player, station);
        click(9, ClickType.RIGHT);
        executeClick();
        verify(dialogs).delete(player, new Location(world, 0, 0, 0), library.getFirst().song().id());
        when(studio.requireProject(player)).thenReturn(library.getLast());
        click(44, ClickType.LEFT);
        executeClick();
        verify(studio).selectProject(player, library.getLast().song().id());
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
    private ItemStack prepareCursorDisc() {
        DiscItems discs = mock(DiscItems.class);
        when(studio.discs()).thenReturn(discs);
        ItemStack disc = mock(ItemStack.class);
        when(disc.getType()).thenReturn(org.bukkit.Material.MUSIC_DISC_CAT);
        when(disc.getAmount()).thenReturn(1);
        when(disc.clone()).thenReturn(disc);
        when(discs.musicDisc(disc)).thenReturn(true);
        when(player.getItemOnCursor()).thenReturn(disc);
        return disc;
    }

    private void deposit(ItemStack disc) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(50);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getCursor()).thenReturn(disc);
        menu.click(event);
        verify(event).setCancelled(true);
    }

    @Test
    void publishingChangesExactlyTheCursorDiscAfterTheEvent() throws Exception {
        ItemStack disc = prepareCursorDisc();
        ItemStack result = mock(ItemStack.class);
        when(studio.publishOnDisc(player, disc)).thenReturn(result);
        deposit(disc);
        verify(studio, never()).publishOnDisc(any(), any());
        executeClick();
        verify(studio).publishOnDisc(player, disc);
        verify(player).setItemOnCursor(result);
        verify(studio, never()).publishFromInventory(any());
    }

    @Test
    void recordedDiscNeedsASecondClickAndChangingProjectInvalidatesConfirmation() throws Exception {
        ItemStack disc = prepareCursorDisc();
        when(studio.discs().recorded(disc)).thenReturn(true);
        deposit(disc);
        executeClick();
        verify(studio, never()).publishOnDisc(any(), any());
        when(studio.requireProject(player)).thenReturn(new Project(project.song(), project.pending(), 105, false));
        deposit(disc);
        executeClick();
        verify(studio, never()).publishOnDisc(any(), any());
        deposit(disc);
        executeClick();
        verify(studio).publishOnDisc(player, disc);
    }

    @Test
    void cursorChangesAndFailedWritesDoNotConsumeDiscs() throws Exception {
        ItemStack disc = prepareCursorDisc();
        deposit(disc);
        when(player.getItemOnCursor()).thenReturn(mock(ItemStack.class));
        executeClick();
        verify(studio, never()).publishOnDisc(any(), any());
        when(player.getItemOnCursor()).thenReturn(disc);
        doThrow(new java.io.IOException("disk full")).when(studio).publishOnDisc(player, disc);
        deposit(disc);
        executeClick();
        verify(player, never()).setItemOnCursor(any());
        verify(studio).failure(eq(player), any(java.io.IOException.class));
    }

    @Test
    void bottomOrdinaryClicksCanPickUpDiscsAndDragAcrossMenuCannotPublish() throws Exception {
        verify(click(54, ClickType.LEFT), never()).setCancelled(true);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(view);
        when(drag.getRawSlots()).thenReturn(java.util.Set.of(50, 51));
        menu.drag(drag);
        verify(drag).setCancelled(true);
        verify(studio, never()).publishOnDisc(any(), any());
    }

    @Test
    void staffCanOpenWithoutStationButRevocationBlocksDeferredControls() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> menu.open(player, null));
        when(player.hasPermission("instruments.studio")).thenReturn(true);
        menu.openProject(player, null);
        click(19, ClickType.LEFT);
        when(player.hasPermission("instruments.studio")).thenReturn(false);
        executeClick();
        verify(studio, never()).record(any(), anyInt());
    }

}
