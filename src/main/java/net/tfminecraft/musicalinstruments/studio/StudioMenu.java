package net.tfminecraft.musicalinstruments.studio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tfminecraft.musicalinstruments.InstrumentPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Project library, horizontal track editor, and a separate panel for project-wide actions. */
public final class StudioMenu implements Listener {
    private enum Screen { LIBRARY, EDITOR, ACTIONS }
    private final InstrumentPlugin plugin;
    private final StudioService studio;
    private final StudioIcons icons;
    private final StudioDialogs dialogs;
    private final BukkitTask refresher;

    public StudioMenu(InstrumentPlugin plugin, StudioService studio) {
        this(plugin, studio, new StudioIcons(), null);
    }

    StudioMenu(InstrumentPlugin plugin, StudioService studio, StudioIcons icons, StudioDialogs dialogs) {
        this.plugin = plugin;
        this.studio = studio;
        this.icons = icons;
        this.dialogs = dialogs == null ? new StudioDialogs(plugin, studio, this::openProject, this::open) : dialogs;
        refresher = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 10L, 10L);
    }

    static void requireStation(Player player, Block station) {
        if (!player.hasPermission("instruments.record")) throw new IllegalArgumentException("You don't have permission to use the recording studio");
        if (station == null || !station.getWorld().equals(player.getWorld())
                || station.getLocation().distanceSquared(player.getLocation()) > 36
                || !(station.getState() instanceof Jukebox box) || box.hasRecord()) {
            throw new IllegalArgumentException("Use an empty jukebox within 5 blocks as your recording station");
        }
    }

    static Block requireStation(Player player, Location location) {
        if (!player.getWorld().equals(location.getWorld()) || location.distanceSquared(player.getLocation()) > 36
                || !player.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            throw new IllegalArgumentException("Stay near your recording station to use its controls");
        }
        Block station = location.getBlock();
        requireStation(player, station);
        return station;
    }

    public void open(Player player, Block station) throws IOException { open(player, station, Screen.LIBRARY, 0); }
    public void openProject(Player player, Block station) throws IOException { open(player, station, Screen.EDITOR, 0); }

    private void open(Player player, Block station, Screen screen, int page) throws IOException {
        requireStation(player, station);
        UUID projectId = screen == Screen.LIBRARY ? null : studio.requireProject(player).song().id();
        Holder holder = new Holder(player.getUniqueId(), station.getLocation(), screen, page, projectId);
        int size = screen == Screen.ACTIONS ? 27 : 54;
        holder.inventory = plugin.getServer().createInventory(holder, size,
                Component.text(screen == Screen.LIBRARY ? "\u266b Your song projects" : screen == Screen.ACTIONS
                        ? "\u266b Project actions" : "\u266b Recording Studio", NamedTextColor.DARK_PURPLE));
        render(player, holder);
        player.openInventory(holder.inventory);
    }

    private void render(Player player, Holder holder) throws IOException {
        Inventory inventory = holder.inventory;
        int size = holder.screen == Screen.ACTIONS ? 27 : 54;
        ItemStack background = button(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
        ItemStack frame = button(Material.PURPLE_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_PURPLE);
        for (int index = 0; index < size; index++) inventory.setItem(index, index < 9 || index >= size - 9 ? frame : background);
        holder.choices.clear();
        inventory.setItem(8, button(Material.BARRIER, "Close studio", NamedTextColor.GRAY));
        if (holder.screen == Screen.LIBRARY) { renderLibrary(player, holder); return; }
        Project project = studio.requireProject(player);
        if (!project.song().id().equals(holder.projectId)) throw new IllegalArgumentException("The active project changed. Open it again from your projects");
        StudioService.Activity activity = studio.activity(player);
        String status = activity.recording() ? "Recording track " + activity.track() + " | " + time(activity.seconds() * 20)
                : activity.previewing() ? "Listening" : "Ready";
        inventory.setItem(0, button(Material.ARROW, holder.screen == Screen.EDITOR ? "All projects" : "Back to tracks", NamedTextColor.AQUA));
        inventory.setItem(4, button(Material.MUSIC_DISC_5, project.song().title(), NamedTextColor.GOLD,
                "By " + project.song().author(), status, project.song().tracks().size() + " saved tracks | " + time(project.song().lengthTicks()),
                "Click for project settings."));
        if (holder.screen == Screen.EDITOR) renderTracks(player, holder, project, activity);
        else renderActions(player, holder, project);
        holder.project = project;
        holder.activity = activity;
        holder.blanks = studio.blankDiscs(player);
    }

    private void renderLibrary(Player player, Holder holder) throws IOException {
        List<Project> projects = studio.listProjects(player);
        int pages = Math.max(1, (projects.size() + 27) / 28);
        holder.page = Math.clamp(holder.page, 0, pages - 1);
        inventoryItem(holder, 4, Material.BOOKSHELF, "Your songs", NamedTextColor.GOLD,
                projects.size() + " projects | Page " + (holder.page + 1) + "/" + pages, "Click a song to open its tracks.");
        for (int index = holder.page * 28; index < Math.min(projects.size(), (holder.page + 1) * 28); index++) {
            Project project = projects.get(index);
            int offset = index % 28;
            int slot = 10 + offset / 7 * 9 + offset % 7;
            holder.choices.put(slot, project.song().id());
            inventoryItem(holder, slot, Material.MUSIC_DISC_13, project.song().title(), NamedTextColor.YELLOW,
                    project.song().tracks().size() + " tracks | " + time(project.song().lengthTicks()),
                    project.pending() == null ? "No pending take" : "Pending take on track " + project.pending().slot(),
                    "Click to open this project.");
        }
        if (projects.isEmpty()) inventoryItem(holder, 22, Material.WRITABLE_BOOK, "Your studio is empty", NamedTextColor.AQUA,
                "Create your first song with the green button below.");
        if (holder.page > 0) inventoryItem(holder, 45, Material.ARROW, "Previous projects", NamedTextColor.AQUA);
        inventoryItem(holder, 49, Material.EMERALD_BLOCK, "Create a new song", NamedTextColor.GREEN,
                "Choose its title, then open its recording studio.", "Your existing projects are kept.");
        if (holder.page + 1 < pages) inventoryItem(holder, 53, Material.ARROW, "More projects", NamedTextColor.AQUA);
        holder.pages = pages;
    }

    private int trackCount(Project project) {
        int saved = project.song().tracks().stream().mapToInt(Track::slot).max().orElse(0);
        return Math.max(studio.settings().tracks(), Math.max(saved, project.pending() == null ? 0 : project.pending().slot()));
    }

    private void renderTracks(Player player, Holder holder, Project project, StudioService.Activity activity) {
        holder.pages = (trackCount(project) + 3) / 4;
        holder.page = Math.clamp(holder.page, 0, holder.pages - 1);
        for (int row = 0; row < 4; row++) {
            int slot = holder.page * 4 + row + 1;
            if (slot > trackCount(project)) break;
            int base = 9 + row * 9;
            Track saved = project.song().track(slot);
            boolean pending = project.pending() != null && project.pending().slot() == slot;
            boolean recording = activity.track() == slot;
            Material material = recording ? Material.RED_CONCRETE : pending ? Material.YELLOW_CONCRETE
                    : saved == null ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE;
            inventoryItem(holder, base, material, "Record track " + slot, NamedTextColor.GOLD,
                    recording ? "RECORDING" : pending ? "NEW TAKE READY" : saved == null ? "EMPTY TRACK" : "SAVED TRACK",
                    saved == null ? "No saved notes yet." : time(saved.lengthTicks()) + " | " + saved.notes().size() + " notes",
                    saved == null ? "" : "Instruments: " + String.join(", ", saved.notes().stream().map(Note::instrument).distinct().toList()),
                    "Click to record; other saved tracks are your backing.");
            float gain = saved == null ? 1 : saved.gain();
            inventoryItem(holder, base + 1, Material.COMPARATOR, "Track " + slot + " volume: " + Math.round(gain * 100) + "%", NamedTextColor.AQUA,
                    "Left: +25% | Right: -25% | Shift: 100%", saved == null ? "Keep a take before mixing." : "Only this track is changed.");
            boolean muted = saved != null && saved.muted();
            inventoryItem(holder, base + 2, muted ? Material.RED_DYE : Material.LIME_DYE,
                    "Track " + slot + (muted ? ": muted" : ": audible"), muted ? NamedTextColor.RED : NamedTextColor.GREEN,
                    "Click to toggle this track's sound.");
            inventoryItem(holder, base + 4, Material.JUKEBOX, "Listen to track " + slot, NamedTextColor.AQUA,
                    "Hear only this saved track.", "Use Listen to full mix below to hear the whole song.");
            inventoryItem(holder, base + 5, Material.NOTE_BLOCK, "Listen to take " + slot, pending ? NamedTextColor.YELLOW : NamedTextColor.GRAY,
                    pending ? "Hear this new take with the saved backing tracks." : "No pending take on this track.");
            inventoryItem(holder, base + 6, Material.EMERALD, "Keep take " + slot, pending ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                    pending ? "Replace this track's saved version." : "No pending take on this track.");
            inventoryItem(holder, base + 7, Material.FLINT, "Discard take " + slot, pending ? NamedTextColor.RED : NamedTextColor.GRAY,
                    pending ? "Keep the previous saved version." : "No pending take on this track.");
        }
        if (holder.page > 0) inventoryItem(holder, 45, Material.ARROW, "Previous tracks", NamedTextColor.AQUA);
        inventoryItem(holder, 46, Material.WRITABLE_BOOK, "Recording guide", NamedTextColor.AQUA,
                "Each row controls one track.", "Hold your instrument in your off-hand and click Record.",
                "Reopen your project to stop; then listen, keep or discard.", "The bottom bar controls the whole project.");
        inventoryItem(holder, 47, Material.JUKEBOX, "Listen to full mix", NamedTextColor.AQUA, "All saved, audible tracks in this project.");
        inventoryItem(holder, 48, Material.REDSTONE_BLOCK, "Stop recording / preview", NamedTextColor.RED,
                "Finish the active take or stop private listening.");
        inventoryItem(holder, 50, Material.MUSIC_DISC_13, "Publish full song", NamedTextColor.GOLD,
                "Uses one blank disc from your inventory.", "Review pending takes first.");
        inventoryItem(holder, 51, Material.NETHER_STAR, "Project actions", NamedTextColor.GOLD,
                "Title, tempo, metronome, blank discs and copies.");
        if (holder.page + 1 < holder.pages) inventoryItem(holder, 53, Material.ARROW, "More tracks", NamedTextColor.AQUA);
    }

    private void renderActions(Player player, Holder holder, Project project) {
        inventoryItem(holder, 10, Material.NAME_TAG, "Song settings", NamedTextColor.GOLD, "Title, tempo and metronome form.");
        inventoryItem(holder, 11, Material.CLOCK, "Tempo: " + project.bpm() + " BPM", NamedTextColor.AQUA,
                "Left: +5 BPM | Right: -5 BPM | Shift: settings form");
        inventoryItem(holder, 12, Material.BELL, "Metronome: " + (project.metronome() ? "ON" : "OFF"), NamedTextColor.YELLOW);
        inventoryItem(holder, 14, Material.PAPER, "Prepare a blank disc", NamedTextColor.AQUA, "Uses one ordinary disc from your inventory.");
        inventoryItem(holder, 15, Material.MUSIC_DISC_13, "Publish full song", NamedTextColor.GOLD,
                "Blank discs: " + studio.blankDiscs(player), "Uses one blank disc from your inventory.");
        inventoryItem(holder, 16, Material.HONEYCOMB, "Copy a recorded disc", NamedTextColor.YELLOW,
                "Hold the recorded disc in your off-hand.", "Uses one blank disc from your inventory.");
        inventoryItem(holder, 18, Material.LAVA_BUCKET, "Clear this project", NamedTextColor.RED,
                "Requires confirmation; other projects and published discs are kept.");
    }

    private void inventoryItem(Holder holder, int slot, Material material, String title, NamedTextColor color, String... lore) {
        holder.inventory.setItem(slot, button(material, title, color, lore));
    }

    private String time(int ticks) { return String.format(Locale.ROOT, "%02d:%02d", ticks / 1200, ticks / 20 % 60); }
    private ItemStack button(Material material, String title, NamedTextColor color, String... lore) {
        return icons.item(material, Component.text(title, color).decorate(TextDecoration.BOLD),
                Arrays.stream(lore).map(line -> (Component) Component.text(line, NamedTextColor.GRAY)).toList());
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())
                || event.getRawSlot() < 0 || event.getRawSlot() >= (holder.screen == Screen.ACTIONS ? 27 : 54)
                || !(event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT
                || event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) return;
        int raw = event.getRawSlot();
        ClickType click = event.getClick();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            try {
                Block station = requireStation(player, holder.station);
                if (raw == 8) { player.closeInventory(); return; }
                if (holder.screen == Screen.LIBRARY) {
                    if (holder.choices.containsKey(raw)) {
                        studio.selectProject(player, holder.choices.get(raw));
                        openProject(player, station);
                    } else if (raw == 49) dialogs.create(player, holder.station);
                    else if (raw == 45 && holder.page > 0) open(player, station, Screen.LIBRARY, holder.page - 1);
                    else if (raw == 53 && holder.page + 1 < holder.pages) open(player, station, Screen.LIBRARY, holder.page + 1);
                    return;
                }
                Project project = studio.requireProject(player);
                if (!project.song().id().equals(holder.projectId)) throw new IllegalArgumentException("The active project changed. Open it again from your projects");
                if (raw == 0) { open(player, station, holder.screen == Screen.EDITOR ? Screen.LIBRARY : Screen.EDITOR, 0); return; }
                if (raw == 4) { dialogs.settings(player, holder.station); return; }
                if (holder.screen == Screen.EDITOR) {
                    if (raw >= 9 && raw < 45) {
                        int track = holder.page * 4 + (raw - 9) / 9 + 1;
                        if (track > trackCount(project)) return;
                        int control = (raw - 9) % 9;
                        Track saved = project.song().track(track);
                        switch (control) {
                            case 0 -> { studio.record(player, track); player.closeInventory(); return; }
                            case 1, 2 -> {
                                if (saved == null) throw new IllegalArgumentException("Record and keep this track first");
                                float gain = control == 2 ? saved.gain() : click.isShiftClick() ? 1
                                        : Math.clamp(saved.gain() + (click.isRightClick() ? -0.25f : 0.25f), 0, 2);
                                studio.edit(player, new Project(project.song().replace(saved.mix(gain, control == 2 ? !saved.muted() : saved.muted())),
                                        project.pending(), project.bpm(), project.metronome()));
                            }
                            case 4 -> studio.previewTrack(player, track);
                            case 5, 6, 7 -> {
                                if (project.pending() == null || project.pending().slot() != track) throw new IllegalArgumentException("No pending take on this track");
                                if (control == 5) studio.preview(player, true);
                                else studio.edit(player, control == 6 ? project.accept() : project.discard());
                            }
                            default -> { return; }
                        }
                    } else switch (raw) {
                        case 45 -> { if (holder.page > 0) open(player, station, Screen.EDITOR, holder.page - 1); return; }
                        case 46 -> { player.sendMessage("Each row controls one track. The bottom bar controls the full project. Hold an instrument in your off-hand, record, stop, then review your take."); return; }
                        case 47 -> studio.preview(player, false);
                        case 48 -> studio.stop(player);
                        case 50 -> studio.publishFromInventory(player);
                        case 51 -> { open(player, station, Screen.ACTIONS, 0); return; }
                        case 53 -> { if (holder.page + 1 < holder.pages) open(player, station, Screen.EDITOR, holder.page + 1); return; }
                        default -> { return; }
                    }
                } else switch (raw) {
                    case 10 -> { dialogs.settings(player, holder.station); return; }
                    case 11 -> {
                        if (click.isShiftClick()) { dialogs.settings(player, holder.station); return; }
                        studio.edit(player, new Project(project.song(), project.pending(),
                                Math.clamp(project.bpm() + (click.isRightClick() ? -5 : 5), 40, 240), project.metronome()));
                    }
                    case 12 -> studio.edit(player, new Project(project.song(), project.pending(), project.bpm(), !project.metronome()));
                    case 14 -> studio.prepareBlankFromInventory(player);
                    case 15 -> studio.publishFromInventory(player);
                    case 16 -> studio.copyFromInventory(player);
                    case 18 -> { dialogs.reset(player, holder.station); return; }
                    default -> { return; }
                }
                render(player, holder);
            } catch (IllegalArgumentException ex) { player.sendMessage(ex.getMessage()); }
            catch (IOException ex) { studio.failure(player, ex); }
        });
    }

    private void refresh() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder)) continue;
            try {
                if (!holder.owner.equals(player.getUniqueId())) { player.closeInventory(); continue; }
                requireStation(player, holder.station);
                if (holder.screen == Screen.LIBRARY) continue;
                Project project = studio.requireProject(player);
                if (!project.song().id().equals(holder.projectId)) { open(player, holder.station.getBlock()); continue; }
                if (project != holder.project || !studio.activity(player).equals(holder.activity)
                        || studio.blankDiscs(player) != holder.blanks) render(player, holder);
            } catch (IllegalArgumentException ex) { player.closeInventory(); }
            catch (IOException ex) { player.closeInventory(); studio.failure(player, ex); }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    public void close() {
        refresher.cancel();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) player.closeInventory();
        }
    }

    private static final class Holder implements InventoryHolder {
        private final UUID owner;
        private final Location station;
        private final Screen screen;
        private final UUID projectId;
        private final Map<Integer, UUID> choices = new HashMap<>();
        private int page;
        private int pages;
        private Inventory inventory;
        private Project project;
        private StudioService.Activity activity;
        private int blanks;
        private Holder(UUID owner, Location station, Screen screen, int page, UUID projectId) {
            this.owner = owner; this.station = station; this.screen = screen; this.page = page; this.projectId = projectId;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
