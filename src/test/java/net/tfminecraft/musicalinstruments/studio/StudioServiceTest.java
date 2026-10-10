package net.tfminecraft.musicalinstruments.studio;

import net.tfminecraft.musicalinstruments.InstrumentPlugin;
import net.tfminecraft.musicalinstruments.managers.InstrumentManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudioServiceTest {
    @TempDir Path directory;
    private InstrumentPlugin plugin;
    private Server server;
    private PluginManager pluginManager;
    private Player player;
    private PlayerInventory inventory;
    private World world;
    private DiscItems discs;
    private StudioStore store;
    private StudioService studio;
    private BukkitTask task;
    private Runnable tick;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        plugin = mock(InstrumentPlugin.class);
        server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        task = mock(BukkitTask.class);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        world = mock(World.class);
        discs = mock(DiscItems.class);
        store = spy(new StudioStore(directory));
        InstrumentManager manager = mock(InstrumentManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getManager()).thenReturn(manager);
        when(manager.getInstrument(any())).thenReturn("lute");
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getPlayer(owner)).thenReturn(player);
        when(player.getUniqueId()).thenReturn(owner);
        when(player.getName()).thenReturn("Musician");
        when(player.getInventory()).thenReturn(inventory);
        when(player.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(server.getWorld(world.getUID())).thenReturn(world);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(player));
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);
        studio = new StudioService(plugin, store, new StudioSettings(4, 40, 20, 60, 8, 32, 2, 0, 2), discs);
        var callback = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), callback.capture(), eq(1L), eq(1L));
        tick = callback.getValue();
    }

    @AfterEach
    void close() {
        studio.close();
    }

    private Song song(Track... tracks) {
        return new Song(UUID.randomUUID(), owner, "Musician", "Song", List.of(tracks));
    }

    private Track track(int slot, String sound) {
        return new Track(slot, 10, 1, false, List.of(new Note(0, "lute", sound, 4, 1)));
    }

    @Test
    void countInIgnoresNotesAndOverdubRetainsThePreviousTrackUntilKept() throws Exception {
        Song original = song(track(1, "backing"), track(2, "old_take"));
        studio.save(player, new Project(original, null, 100, false));
        studio.record(player, 2);
        studio.capture(player, "flute", "early", 4, 1);
        tick.run();
        studio.capture(player, "flute", "early", 4, 1);
        tick.run();
        verify(player).playSound(any(Location.class), eq("backing"), eq(SoundCategory.RECORDS), eq(4f), eq(1f));
        verify(player, never()).playSound(any(Location.class), eq("old_take"), any(SoundCategory.class), anyFloat(), anyFloat());
        studio.capture(player, "flute", "new_take", 2, 1.5f);
        studio.stop(player);
        Project pending = studio.requireProject(player);
        assertEquals(original, pending.song());
        assertEquals(1, pending.pending().notes().size());
        assertEquals(0, pending.pending().notes().getFirst().tick());
        studio.edit(player, pending.accept());
        assertEquals("new_take", studio.requireProject(player).song().track(2).notes().getFirst().sound());
    }

    @Test
    void previewsPlaySimultaneousTracksWithoutEmittingLivePerformanceEvents() throws Exception {
        studio.save(player, new Project(song(track(1, "lute"), track(2, "flute")), null, 100, false));
        studio.preview(player, false);
        tick.run();
        verify(player).playSound(any(Location.class), eq("lute"), eq(SoundCategory.RECORDS), eq(4f), eq(1f));
        verify(player).playSound(any(Location.class), eq("flute"), eq(SoundCategory.RECORDS), eq(4f), eq(1f));
        verifyNoInteractions(pluginManager);
    }

    @Test
    void newProjectsPreserveOlderTracksPendingTakesAndActiveSelectionAfterRestart() throws Exception {
        Project first = new Project(song(track(1, "first")), track(2, "pending"), 120, false);
        studio.save(player, first);
        studio.create(player, "Second", false);
        Project second = studio.requireProject(player);
        assertEquals(2, studio.listProjects(player).size());
        studio.selectProject(player, first.song().id());
        assertEquals(first, studio.requireProject(player));
        assertEquals(first, new StudioStore(directory).project(owner).orElseThrow());
        studio.create(player, "Reset", true);
        assertEquals(first.song().id(), studio.requireProject(player).song().id());
        assertTrue(studio.requireProject(player).song().tracks().isEmpty());
        assertEquals(second, store.project(owner, second.song().id()));
    }

    @Test
    void changingProjectsIsBlockedDuringCaptureAndIsSafeAfterFinishing() throws Exception {
        studio.create(player, "First", false);
        UUID first = studio.requireProject(player).song().id();
        studio.create(player, "Second", false);
        studio.record(player, 1);
        assertThrows(IllegalArgumentException.class, () -> studio.selectProject(player, first));
        assertThrows(IllegalArgumentException.class, () -> studio.create(player, "Third", false));
        studio.stop(player);
        studio.selectProject(player, first);
        assertEquals(first, studio.requireProject(player).song().id());
    }

    @Test
    void listeningToOneTrackDoesNotEmitTheRestOfTheMix() throws Exception {
        studio.save(player, new Project(song(track(1, "solo"), track(2, "other")), null, 100, false));
        studio.previewTrack(player, 1);
        tick.run();
        verify(player).playSound(any(Location.class), eq("solo"), eq(SoundCategory.RECORDS), anyFloat(), anyFloat());
        verify(player, never()).playSound(any(Location.class), eq("other"), any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @Test
    void checkpointsAndShutdownRecoverAnUnacceptedTake() throws Exception {
        studio.create(player, "Song", false);
        studio.record(player, 1);
        tick.run();
        tick.run();
        studio.capture(player, "lute", "first", 4, 1);
        tick.run();
        tick.run();
        verify(store, timeout(3000).times(2)).save(any());
        // Mockito observes the call at entry; await the atomically installed file contents.
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (store.project(owner).orElseThrow().pending() == null && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals("first", new StudioStore(directory).project(owner).orElseThrow().pending().notes().getFirst().sound());
        studio.capture(player, "lute", "second", 4, 1);
        studio.close();
        Project saved = new StudioStore(directory).project(owner).orElseThrow();
        assertEquals(2, saved.pending().notes().size());
        assertTrue(saved.song().tracks().isEmpty());
        verify(task).cancel();
    }

    @Test
    void emptyTakeDoesNotReplaceASavedTrack() throws Exception {
        Song original = song(track(1, "saved"));
        studio.save(player, new Project(original, null, 100, false));
        studio.record(player, 1);
        studio.stop(player);
        assertEquals(original, studio.requireProject(player).song());
        assertNull(studio.requireProject(player).pending());
    }

    @Test
    void anOlderBackgroundCheckpointCannotOverwriteTheFinalTake() throws Exception {
        studio.create(player, "Song", false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals("musical-instruments-checkpoints")) {
                started.countDown();
                if (!release.await(3, TimeUnit.SECONDS)) {
                    throw new IOException("Test checkpoint timed out");
                }
            }
            return invocation.callRealMethod();
        }).when(store).save(any());
        studio.record(player, 1);
        tick.run();
        tick.run();
        studio.capture(player, "lute", "first", 4, 1);
        tick.run();
        tick.run();
        assertTrue(started.await(3, TimeUnit.SECONDS));
        studio.capture(player, "lute", "second", 4, 1);
        CompletableFuture<Void> stopped = CompletableFuture.runAsync(() -> studio.stop(player));
        release.countDown();
        stopped.get(3, TimeUnit.SECONDS);
        Project saved = new StudioStore(directory).project(owner).orElseThrow();
        assertEquals(List.of("first", "second"), saved.pending().notes().stream().map(Note::sound).toList());
    }

    @Test
    void noteLimitFinishesTheTakeWithoutOverwritingTheOldTrack() throws Exception {
        Song original = song(track(1, "saved"));
        studio.save(player, new Project(original, null, 100, false));
        studio.record(player, 1);
        tick.run();
        tick.run();
        for (int i = 0; i < 21; i++) {
            studio.capture(player, "lute", "new", 4, 1);
        }
        Project project = studio.requireProject(player);
        assertEquals(original, project.song());
        assertEquals(20, project.pending().notes().size());
        studio.requireIdle(player);
    }

    @Test
    void maximumDurationFinishesAndPersistsTheTake() throws Exception {
        studio.create(player, "Song", false);
        studio.record(player, 1);
        tick.run();
        tick.run();
        studio.capture(player, "lute", "sample", 4, 1);
        for (int i = 0; i < 40; i++) {
            tick.run();
        }
        Project project = studio.requireProject(player);
        assertEquals(40, project.pending().lengthTicks());
        studio.requireIdle(player);
        assertEquals(project, store.project(owner).orElseThrow());
    }

    @Test
    void failedPublicationPreservesThePhysicalBlankDisc() throws Exception {
        studio.save(player, new Project(song(track(1, "sample")), null, 100, false));
        ItemStack blank = mock(ItemStack.class);
        when(inventory.getItemInMainHand()).thenReturn(blank);
        when(blank.getAmount()).thenReturn(1);
        when(discs.blank(blank)).thenReturn(true);
        when(discs.disc(any())).thenReturn(mock(ItemStack.class));
        doThrow(new IOException("Disk full")).when(store).publish(any());
        assertThrows(IOException.class, () -> studio.publish(player));
        verify(inventory, never()).setItemInMainHand(any());
    }

    private ItemStack inventoryItem(Material type, int amount, boolean blank) {
        ItemStack item = mock(ItemStack.class);
        Material material = mock(Material.class);
        when(material.isAir()).thenReturn(false);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(amount);
        when(discs.blank(item)).thenReturn(blank);
        return item;
    }

    @Test
    void menuPublicationConsumesOneInventoryDiscWithoutChangingTheHeldInstrument() throws Exception {
        studio.save(player, new Project(song(track(1, "sample")), null, 100, false));
        ItemStack blank = inventoryItem(Material.MUSIC_DISC_13, 3, true);
        ItemStack remainder = mock(ItemStack.class);
        ItemStack published = mock(ItemStack.class);
        when(blank.clone()).thenReturn(remainder);
        when(discs.disc(any())).thenReturn(published);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{blank, null});
        studio.publishFromInventory(player);
        verify(remainder).setAmount(2);
        verify(inventory).setItem(0, remainder);
        verify(inventory).setItem(1, published);
        verify(inventory, never()).setItemInMainHand(any());
        assertEquals(3, studio.blankDiscs(player));
    }

    @Test
    void fullInventoryPreventsPublicationBeforeWritingOrConsumingADisc() throws Exception {
        studio.save(player, new Project(song(track(1, "sample")), null, 100, false));
        ItemStack blank = inventoryItem(Material.MUSIC_DISC_13, 2, true);
        ItemStack stone = inventoryItem(Material.STONE, 64, false);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{blank, stone});
        assertThrows(IllegalArgumentException.class, () -> studio.publishFromInventory(player));
        verify(store, never()).publish(any());
        verify(inventory, never()).setItem(anyInt(), any());
    }

    @Test
    void fullInventoryCanUseALaterSingleDiscRatherThanSplitTheFirstStack() throws Exception {
        studio.save(player, new Project(song(track(1, "sample")), null, 100, false));
        ItemStack stack = inventoryItem(Material.MUSIC_DISC_13, 2, true);
        ItemStack single = inventoryItem(Material.MUSIC_DISC_13, 1, true);
        ItemStack published = mock(ItemStack.class);
        when(discs.disc(any())).thenReturn(published);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{stack, single});
        studio.publishFromInventory(player);
        verify(inventory).setItem(1, published);
        verify(inventory, never()).setItem(eq(0), any());
    }

    @Test
    void failedMenuPublicationPreservesAllInventoryItems() throws Exception {
        studio.save(player, new Project(song(track(1, "sample")), null, 100, false));
        ItemStack blank = inventoryItem(Material.MUSIC_DISC_13, 1, true);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{blank});
        doThrow(new IOException("Disk full")).when(store).publish(any());
        assertThrows(IOException.class, () -> studio.publishFromInventory(player));
        verify(inventory, never()).setItem(anyInt(), any());
    }

    @Test
    void menuCopyRespectsItsPermissionBeforeInspectingOrConsumingDiscs() {
        when(player.hasPermission("instruments.copy")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> studio.copyFromInventory(player));
        verifyNoInteractions(inventory);
    }

    @Test
    void publicationAndCopiesReferenceAnImmutableEdition() throws Exception {
        Song original = song(track(1, "original"));
        studio.save(player, new Project(original, null, 100, false));
        ItemStack blank = mock(ItemStack.class);
        ItemStack published = mock(ItemStack.class);
        when(inventory.getItemInMainHand()).thenReturn(blank);
        when(blank.getAmount()).thenReturn(1);
        when(discs.blank(blank)).thenReturn(true);
        when(discs.disc(any())).thenReturn(published);
        studio.publish(player);
        var captured = ArgumentCaptor.forClass(Song.class);
        verify(store).publish(captured.capture());
        Song edition = captured.getValue();
        studio.edit(player, new Project(original.replace(track(1, "changed")), null, 100, false));
        when(inventory.getItemInOffHand()).thenReturn(published);
        when(discs.songId(published)).thenReturn(edition.id());
        studio.copy(player);
        assertEquals("original", store.song(edition.id()).track(1).notes().getFirst().sound());
        verify(inventory, times(2)).setItemInMainHand(published);
    }

    private Block jukebox(Song song, ItemStack disc) throws IOException {
        store.publish(song);
        Block block = mock(Block.class);
        Jukebox box = mock(Jukebox.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getState()).thenReturn(box);
        when(block.getType()).thenReturn(Material.JUKEBOX);
        when(discs.custom(disc)).thenReturn(true);
        when(discs.playbackDisc(disc)).thenReturn(disc);
        when(block.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(world.getBlockAt(0, 0, 0)).thenReturn(block);
        when(box.update(false, false)).thenReturn(true);
        when(box.getRecord()).thenReturn(disc);
        when(disc.asOne()).thenReturn(disc);
        when(discs.songId(disc)).thenReturn(song.id());
        return block;
    }

    @Test
    void jukeboxAppliesItsSnapshotAndPlaysEveryTrackOnlyToNearbyListeners() throws Exception {
        Song song = song(track(1, "lute"), track(2, "flute"));
        ItemStack disc = mock(ItemStack.class);
        Block block = jukebox(song, disc);
        Player farAway = mock(Player.class);
        when(farAway.getLocation()).thenReturn(new Location(world, 100, 0, 0));
        when(world.getPlayers()).thenReturn(List.of(player, farAway));
        studio.play(block, disc);
        var order = inOrder((Jukebox) block.getState());
        order.verify((Jukebox) block.getState()).stopPlaying();
        order.verify((Jukebox) block.getState()).setRecord(disc);
        order.verify((Jukebox) block.getState()).update(false, false);
        order.verify((Jukebox) block.getState()).stopPlaying();
        tick.run();
        verify(player).playSound(eq(new Location(world, 0.5, 0.5, 0.5)), eq("lute"), eq(SoundCategory.RECORDS), eq(4f), eq(1f));
        verify(player).playSound(any(Location.class), eq("flute"), eq(SoundCategory.RECORDS), eq(4f), eq(1f));
        verify(farAway, never()).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());
        verifyNoInteractions(pluginManager);
    }

    @Test
    void ejectionAndChunkUnloadingCancelFutureNotes() throws Exception {
        Song song = song(new Track(1, 10, 1, false, List.of(new Note(3, "lute", "late", 4, 1))));
        ItemStack disc = mock(ItemStack.class);
        Block block = jukebox(song, disc);
        studio.play(block, disc);
        studio.stop(block);
        for (int i = 0; i < 5; i++) tick.run();
        studio.play(block, disc);
        studio.unload(world.getUID(), 0, 0);
        for (int i = 0; i < 5; i++) tick.run();
        verify(player, never()).playSound(any(Location.class), eq("late"), any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @Test
    void missingEditionDoesNotAlterTheJukebox() {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        ItemStack disc = mock(ItemStack.class);
        when(discs.songId(disc)).thenReturn(UUID.randomUUID());
        assertThrows(IOException.class, () -> studio.play(block, disc));
        verify(block, never()).getState();
    }
    @Test
    void fullLibraryRejectsCreationUntilAProjectIsDeletedAndResetRemainsAllowed() throws Exception {
        for (int index = 0; index < StudioService.MAX_PROJECTS; index++) studio.create(player, "Song " + index, false);
        Project active = studio.requireProject(player);
        assertThrows(IllegalArgumentException.class, () -> studio.create(player, "Extra", false));
        assertEquals(active, studio.requireProject(player));
        studio.create(player, "Reset", true);
        assertEquals(36, studio.listProjects(player).size());
        Project inactive = studio.listProjects(player).stream().filter(p -> !p.song().id().equals(active.song().id())).findFirst().orElseThrow();
        studio.deleteProject(player, inactive);
        studio.create(player, "Replacement", false);
        assertEquals(36, studio.listProjects(player).size());
    }

    @Test
    void deletingActiveProjectDoesNotResurrectItAndKeepsOtherProjectsAndEditions() throws Exception {
        studio.create(player, "Other", false);
        Project other = studio.requireProject(player);
        Project active = new Project(song(track(1, "note")), null, 100, false);
        studio.save(player, active);
        Song edition = active.song().edition();
        store.publish(edition);
        studio.deleteProject(player, active);
        assertNull(studio.project(player));
        studio.quit(player);
        assertTrue(store.project(owner).isEmpty());
        assertEquals(List.of(other), store.projects(owner));
        assertEquals(edition, store.song(edition.id()));
    }

    @Test
    void deletionRejectsStaleConfirmationForeignOwnerAndActiveRecording() throws Exception {
        Project before = new Project(song(track(1, "note")), null, 100, false);
        studio.save(player, before);
        Project changed = new Project(before.song(), null, 105, false);
        studio.edit(player, changed);
        assertThrows(IllegalArgumentException.class, () -> studio.deleteProject(player, before));
        Project foreign = new Project(new Song(UUID.randomUUID(), UUID.randomUUID(), "Other", "Other", List.of()), null, 100, false);
        assertThrows(IllegalArgumentException.class, () -> studio.deleteProject(player, foreign));
        studio.record(player, 2);
        assertThrows(IllegalArgumentException.class, () -> studio.deleteProject(player, changed));
        assertEquals(changed, store.project(owner).orElseThrow());
    }

    @Test
    void publishingOnProvidedDiscKeepsEditionsSeparateAndRejectsStacks() throws Exception {
        studio.save(player, new Project(song(track(1, "note")), null, 100, false));
        ItemStack original = mock(ItemStack.class);
        ItemStack encoded = mock(ItemStack.class);
        when(discs.musicDisc(original)).thenReturn(true);
        when(original.getAmount()).thenReturn(2);
        assertThrows(IllegalArgumentException.class, () -> studio.publishOnDisc(player, original));
        verify(store, never()).publish(any());
        when(original.getAmount()).thenReturn(1);
        when(discs.disc(any(Song.class), eq(original))).thenReturn(encoded);
        assertSame(encoded, studio.publishOnDisc(player, original));
        var published = ArgumentCaptor.forClass(Song.class);
        verify(store).publish(published.capture());
        assertNotEquals(studio.requireProject(player).song().id(), published.getValue().id());
        assertEquals(published.getValue(), store.song(published.getValue().id()));
        verifyNoInteractions(inventory);
    }

    @Test
    void providedDiscSurvivesPublicationFailureAndPendingTakesCannotBePublished() throws Exception {
        Project project = new Project(song(track(1, "note")), track(2, "pending"), 100, false);
        studio.save(player, project);
        ItemStack original = mock(ItemStack.class);
        when(discs.musicDisc(original)).thenReturn(true);
        when(original.getAmount()).thenReturn(1);
        assertThrows(IllegalArgumentException.class, () -> studio.publishOnDisc(player, original));
        studio.edit(player, project.discard());
        doThrow(new IOException("disk full")).when(store).publish(any());
        assertThrows(IOException.class, () -> studio.publishOnDisc(player, original));
        verify(discs, never()).disc(any(Song.class), any(ItemStack.class));
        verify(original, never()).setAmount(anyInt());
        verifyNoInteractions(inventory);
    }

    @Test
    void legacyDiscIsNormalizedBeforeInsertionAndStopTargetsOnlyItsJukeboxChannel() throws Exception {
        Song song = song(track(1, "note"));
        ItemStack oldDisc = mock(ItemStack.class);
        ItemStack silentDisc = mock(ItemStack.class);
        Block block = jukebox(song, oldDisc);
        when(discs.playbackDisc(oldDisc)).thenReturn(silentDisc);
        when(discs.songId(silentDisc)).thenReturn(song.id());
        Jukebox box = (Jukebox) block.getState();
        when(box.getRecord()).thenReturn(silentDisc);
        var order = inOrder(discs, box);
        studio.play(block, oldDisc);
        order.verify(discs).playbackDisc(oldDisc);
        order.verify(box).setRecord(silentDisc);
        order.verify(box).update(false, false);
        order.verify(box).stopPlaying();
        clearInvocations(world, box);
        studio.stop(block);
        verify(box).stopPlaying();
        verify(world).playEffect(block.getLocation(), org.bukkit.Effect.SOUND_STOP_JUKEBOX_SONG, 0, 64);
        verify(player, never()).stopAllSounds();
        tick.run();
        verify(player, never()).playSound(any(Location.class), eq("note"), any(SoundCategory.class), anyFloat(), anyFloat());
        verify(oldDisc, never()).setAmount(anyInt());
    }

    @Test
    void finishedCustomRecordStillClearsVanillaSoundWhileOrdinaryDiscsAreUntouched() throws Exception {
        Song song = song(track(1, "note"));
        ItemStack disc = mock(ItemStack.class);
        Block block = jukebox(song, disc);
        studio.play(block, disc);
        for (int index = 0; index < 12; index++) tick.run();
        Jukebox box = (Jukebox) block.getState();
        clearInvocations(world, box);
        studio.stop(block);
        verify(world).playEffect(block.getLocation(), org.bukkit.Effect.SOUND_STOP_JUKEBOX_SONG, 0, 64);
        clearInvocations(world, box);
        when(discs.custom(disc)).thenReturn(false);
        studio.stop(block);
        verify(world, never()).playEffect(any(Location.class), any(org.bukkit.Effect.class), anyInt(), anyInt());
        verify(box, never()).stopPlaying();
    }

}
