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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** A visual mixer: one column per track, transport controls and physical disc actions. */
public final class StudioMenu implements Listener {
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
        this.dialogs = dialogs == null ? new StudioDialogs(plugin, studio, this::open) : dialogs;
        refresher = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 10L, 10L);
    }

    static void requireStation(Player player, Block station) {
        if (!player.hasPermission("instruments.record")) {
            throw new IllegalArgumentException("You don't have permission to use the recording studio");
        }
        if (station == null || !station.getWorld().equals(player.getWorld())
                || station.getLocation().distanceSquared(player.getLocation()) > 36
                || !(station.getState() instanceof Jukebox box) || box.hasRecord()) {
            throw new IllegalArgumentException("Use an empty jukebox within 5 blocks as your recording station");
        }
    }

    static Block requireStation(Player player, Location location) {
        if (!player.getWorld().equals(location.getWorld())
                || location.distanceSquared(player.getLocation()) > 36
                || !player.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            throw new IllegalArgumentException("Stay near your recording station to use its controls");
        }
        Block station = location.getBlock();
        requireStation(player, station);
        return station;
    }

    public void open(Player player, Block station) throws IOException {
        requireStation(player, station);
        if (studio.project(player) == null) studio.create(player, "Untitled", false);
        Holder holder = new Holder(player.getUniqueId(), station.getLocation());
        holder.inventory = plugin.getServer().createInventory(holder, 54,
                Component.text("\u266b Recording Studio", NamedTextColor.DARK_PURPLE));
        render(player, holder);
        player.openInventory(holder.inventory);
    }

    private void render(Player player, Holder holder) throws IOException {
        Project project = studio.requireProject(player);
        StudioService.Activity activity = studio.activity(player);
        Inventory inventory = holder.inventory;
        ItemStack background = button(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
        ItemStack frame = button(Material.PURPLE_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_PURPLE);
        for (int index = 0; index < 54; index++) inventory.setItem(index, index < 9 || index >= 45 ? frame : background);
        inventory.setItem(0, button(Material.WRITABLE_BOOK, "How to record", NamedTextColor.AQUA,
                "1. Hold an instrument in your off-hand.", "2. Click a track and wait for the count-in.",
                "3. Play normally with keys 1-8.", "4. Open this station again and click Stop.",
                "5. Listen to the take, then Keep or Discard.", "6. Add tracks and publish on a blank disc."));
        String status = activity.recording() ? activity.countdown() > 0 ? "Starting in " + activity.countdown() + "s"
                : "Recording track " + activity.track() + " - " + time(activity.seconds() * 20)
                : activity.previewing() ? "Listening to your mix" : project.pending() != null ? "A take is waiting for your review" : "Ready to record";
        inventory.setItem(4, button(Material.MUSIC_DISC_5, project.song().title(), NamedTextColor.GOLD,
                "By " + project.song().author(), status,
                project.song().tracks().size() + " saved tracks | " + time(project.song().lengthTicks()),
                "Tempo: " + project.bpm() + " BPM", "Click to edit the title and tempo."));
        inventory.setItem(8, button(Material.BARRIER, "Close studio", NamedTextColor.GRAY));
        for (int track = 1; track <= studio.settings().tracks(); track++) {
            Track saved = project.song().track(track);
            boolean recording = activity.track() == track;
            boolean pending = project.pending() != null && project.pending().slot() == track;
            Material material = recording ? Material.RED_CONCRETE : pending ? Material.YELLOW_CONCRETE
                    : saved == null ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE;
            NamedTextColor color = recording ? NamedTextColor.RED : pending ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
            List<String> details = new ArrayList<>();
            details.add(recording ? "RECORDING - " + activity.notes() + " notes" : pending ? "NEW TAKE READY" : saved == null ? "EMPTY TRACK" : "SAVED TRACK");
            if (saved != null) {
                details.add(time(saved.lengthTicks()) + " | " + saved.notes().size() + " notes");
                details.add("Instruments: " + String.join(", ", saved.notes().stream().map(Note::instrument).distinct().toList()));
            }
            details.add("Click: record a take for this track.");
            details.add("Your other saved tracks play as backing.");
            details.add("The old take stays safe until you click Keep.");
            int column = trackSlot(studio.settings().tracks(), track);
            inventory.setItem(column, button(material, "Track " + track, color, details.toArray(String[]::new)));
            float gain = saved == null ? 1 : saved.gain();
            inventory.setItem(column + 9, button(Material.COMPARATOR, "Volume " + Math.round(gain * 100) + "%", NamedTextColor.AQUA,
                    meter(gain), "Left click: +25%", "Right click: -25%", "Shift-click: reset to 100%",
                    saved == null ? "Record and keep this track to mix it." : "Changes affect your saved mix."));
            boolean muted = saved != null && saved.muted();
            inventory.setItem(column + 18, button(muted ? Material.RED_DYE : Material.LIME_DYE,
                    muted ? "Muted" : "Audible", muted ? NamedTextColor.RED : NamedTextColor.GREEN,
                    "Click to toggle this track's sound.", saved == null ? "No saved take yet." : "Its notes and timing are preserved."));
        }
        boolean pending = project.pending() != null;
        inventory.setItem(37, button(Material.REDSTONE_BLOCK, "Stop", NamedTextColor.RED,
                activity.recording() ? "Finish this recording and save its take." : "Stop the private preview.", "Existing audio samples finish naturally."));
        inventory.setItem(39, button(Material.JUKEBOX, "Listen to saved mix", NamedTextColor.AQUA, "Hear all saved, audible tracks together."));
        inventory.setItem(40, button(pending ? Material.NOTE_BLOCK : Material.GRAY_DYE, "Listen to new take", pending ? NamedTextColor.YELLOW : NamedTextColor.GRAY,
                pending ? "Track " + project.pending().slot() + " with your other saved tracks." : "Record a take first."));
        inventory.setItem(41, button(pending ? Material.EMERALD : Material.GRAY_DYE, "Keep take", pending ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                "Accept the new take and replace its previous track."));
        inventory.setItem(43, button(pending ? Material.FLINT : Material.GRAY_DYE, "Discard take", pending ? NamedTextColor.RED : NamedTextColor.GRAY,
                "Remove the pending take; keep the previous track."));
        inventory.setItem(45, button(Material.NAME_TAG, "Song settings", NamedTextColor.GOLD,
                "Edit the title using an input field.", "Set tempo with a slider and toggle the metronome."));
        inventory.setItem(46, button(Material.CLOCK, "Tempo: " + project.bpm() + " BPM", NamedTextColor.AQUA,
                "Left click: +5 BPM | Right click: -5 BPM", "Shift-click: open the settings slider.", "Recorded timing stays the same."));
        inventory.setItem(47, button(Material.BELL, "Metronome: " + (project.metronome() ? "ON" : "OFF"), NamedTextColor.YELLOW,
                "Click to toggle the private recording click."));
        inventory.setItem(49, button(Material.MUSIC_DISC_13, "Publish song", NamedTextColor.GOLD,
                "Blank discs in your inventory: " + studio.blankDiscs(player), "Uses one blank disc from your inventory.",
                "Published copies keep this edition forever.", "Keep or discard pending takes first."));
        inventory.setItem(50, button(Material.PAPER, "Prepare a blank disc", NamedTextColor.AQUA,
                "Uses one ordinary music disc from your inventory.", "Then click Publish song."));
        inventory.setItem(51, button(Material.HONEYCOMB, "Copy a recorded disc", NamedTextColor.YELLOW,
                "Hold the recorded disc in your off-hand.", "Uses one blank disc from your inventory."));
        inventory.setItem(53, button(Material.LAVA_BUCKET, "New song", NamedTextColor.RED,
                "Clear your editable project after confirmation.", "Published discs keep their songs."));
        holder.project = project;
        holder.activity = activity;
        holder.blanks = studio.blankDiscs(player);
    }

    static int trackSlot(int count, int track) { return 10 + (track - 1) * (count <= 4 ? 2 : 1); }

    private String time(int ticks) {
        int seconds = ticks / 20;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private String meter(float gain) {
        int filled = Math.round(gain * 4);
        return "\u25a0".repeat(filled) + "\u25a1".repeat(8 - filled);
    }

    private ItemStack button(Material material, String title, NamedTextColor color, String... lore) {
        return icons.item(material, Component.text(title, color).decorate(TextDecoration.BOLD),
                Arrays.stream(lore).map(line -> (Component) Component.text(line, NamedTextColor.GRAY)).toList());
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())
                || event.getRawSlot() < 0 || event.getRawSlot() >= 54
                || !(event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT
                || event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) return;
        int raw = event.getRawSlot();
        ClickType click = event.getClick();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            try {
                requireStation(player, holder.station);
                if (raw == 8) { player.closeInventory(); return; }
                for (int track = 1; track <= studio.settings().tracks(); track++) {
                    int column = trackSlot(studio.settings().tracks(), track);
                    if (raw == column) {
                        studio.record(player, track);
                        player.closeInventory();
                        return;
                    }
                    if (raw == column + 9 || raw == column + 18) {
                        Project project = studio.requireProject(player);
                        Track saved = project.song().track(track);
                        if (saved == null) throw new IllegalArgumentException("Record and keep this track first");
                        float gain = raw == column + 18 ? saved.gain() : click.isShiftClick() ? 1
                                : Math.clamp(saved.gain() + (click.isRightClick() ? -0.25f : 0.25f), 0, 2);
                        boolean muted = raw == column + 18 ? !saved.muted() : saved.muted();
                        studio.edit(player, new Project(project.song().replace(saved.mix(gain, muted)),
                                project.pending(), project.bpm(), project.metronome()));
                        render(player, holder);
                        return;
                    }
                }
                switch (raw) {
                    case 0 -> { player.sendMessage("Use the colored track cards to record. Volume and mute controls are directly below each track."); return; }
                    case 4, 45 -> { dialogs.settings(player, holder.station); return; }
                    case 37 -> studio.stop(player);
                    case 39 -> studio.preview(player, false);
                    case 40 -> studio.preview(player, true);
                    case 41 -> studio.edit(player, studio.requireProject(player).accept());
                    case 43 -> studio.edit(player, studio.requireProject(player).discard());
                    case 46 -> {
                        if (click.isShiftClick()) { dialogs.settings(player, holder.station); return; }
                        Project project = studio.requireProject(player);
                        studio.edit(player, new Project(project.song(), project.pending(),
                                Math.clamp(project.bpm() + (click.isRightClick() ? -5 : 5), 40, 240), project.metronome()));
                    }
                    case 47 -> {
                        Project project = studio.requireProject(player);
                        studio.edit(player, new Project(project.song(), project.pending(), project.bpm(), !project.metronome()));
                    }
                    case 49 -> studio.publishFromInventory(player);
                    case 50 -> studio.prepareBlankFromInventory(player);
                    case 51 -> studio.copyFromInventory(player);
                    case 53 -> { dialogs.reset(player, holder.station); return; }
                    default -> { return; }
                }
                render(player, holder);
            } catch (IllegalArgumentException ex) {
                player.sendMessage(ex.getMessage());
            } catch (IOException ex) {
                studio.failure(player, ex);
            }
        });
    }

    private void refresh() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder)) continue;
            try {
                if (!holder.owner.equals(player.getUniqueId())) { player.closeInventory(); continue; }
                if (!player.getWorld().equals(holder.station.getWorld())
                        || !player.getWorld().isChunkLoaded(holder.station.getBlockX() >> 4, holder.station.getBlockZ() >> 4)) {
                    player.closeInventory();
                    continue;
                }
                requireStation(player, holder.station.getBlock());
                if (studio.requireProject(player) != holder.project || !studio.activity(player).equals(holder.activity)
                        || studio.blankDiscs(player) != holder.blanks) render(player, holder);
            } catch (IllegalArgumentException ex) {
                player.closeInventory();
            } catch (IOException ex) {
                player.closeInventory();
                studio.failure(player, ex);
            }
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
        private Inventory inventory;
        private Project project;
        private StudioService.Activity activity;
        private int blanks;

        private Holder(UUID owner, Location station) { this.owner = owner; this.station = station; }
        @Override
        public Inventory getInventory() { return inventory; }
    }
}
