package net.tfminecraft.musicalinstruments.studio;

import net.kyori.adventure.text.Component;
import net.tfminecraft.musicalinstruments.InstrumentPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

public final class StudioMenu implements Listener {
    private final InstrumentPlugin plugin;
    private final StudioService studio;

    public StudioMenu(InstrumentPlugin plugin, StudioService studio) {
        this.plugin = plugin;
        this.studio = studio;
    }

    public void open(Player player, Block station) throws IOException {
        if (!player.hasPermission("instruments.record")) {
            throw new IllegalArgumentException("You don't have permission to use the recording studio");
        }
        if (station == null || !(station.getState() instanceof Jukebox box) || box.hasRecord()
                || !station.getWorld().equals(player.getWorld())
                || station.getLocation().distanceSquared(player.getLocation()) > 36) {
            throw new IllegalArgumentException("Use an empty jukebox within 5 blocks as your recording station");
        }
        if (studio.project(player) == null) {
            studio.create(player, "Untitled", false);
        }
        Project project = studio.requireProject(player);
        Holder holder = new Holder(player.getUniqueId(), station.getLocation());
        holder.inventory = plugin.getServer().createInventory(holder, 36, Component.text("Recording: " + project.song().title()));
        Inventory inventory = holder.inventory;
        inventory.setItem(4, button(Material.BOOK, project.song().title(),
                "Rename: /music title <title>", "Tempo: " + project.bpm() + " BPM",
                project.pending() == null ? "No pending take" : "Pending take: track " + project.pending().slot()));
        for (int slot = 1; slot <= studio.settings().tracks(); slot++) {
            Track track = project.song().track(slot);
            inventory.setItem(9 + slot, button(track == null ? Material.GRAY_DYE : Material.LIME_DYE,
                    "Track " + slot, track == null ? "Empty" : track.notes().size() + " notes | volume " + track.gain(),
                    "Click: record a new take", "Shift-click: toggle mute",
                    track != null && track.muted() ? "Muted" : "Audible"));
        }
        inventory.setItem(21, button(Material.REDSTONE, "Stop", "Stop recording or preview"));
        inventory.setItem(22, button(Material.NOTE_BLOCK, "Preview", "Click: saved mix", "Shift-click: pending take with the mix"));
        inventory.setItem(23, button(Material.EMERALD, "Keep take", "Replace the old track with the pending take"));
        inventory.setItem(24, button(Material.BARRIER, "Discard take", "Preserve the old track"));
        inventory.setItem(25, button(Material.MUSIC_DISC_13, "Publish disc", "Hold a blank disc in your main hand"));
        inventory.setItem(26, button(Material.PAPER, "Prepare blank disc", "Hold an ordinary music disc in your main hand"));
        player.openInventory(inventory);
    }

    private ItemStack button(Material material, String title, String... lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(title));
        meta.lore(Arrays.stream(lore).map(Component::text).toList());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true); // Also block shift, number-key, double-click and bottom-inventory transfers.
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())
                || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        int raw = event.getRawSlot();
        boolean shifted = event.isShiftClick();
        // Bukkit inventory mutations/open/close must happen after InventoryClickEvent completes.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) {
                return;
            }
            try {
                Block block = holder.station.getBlock();
                if (!player.hasPermission("instruments.record") || !player.getWorld().equals(holder.station.getWorld())
                        || player.getLocation().distanceSquared(holder.station) > 36
                        || !(block.getState() instanceof Jukebox box) || box.hasRecord()) {
                    player.closeInventory();
                    return;
                }
                if (raw >= 10 && raw < 10 + studio.settings().tracks()) {
                    int slot = raw - 9;
                    if (shifted) {
                        Project project = studio.requireProject(player);
                        Track track = project.song().track(slot);
                        if (track != null) {
                            studio.edit(player, new Project(project.song().replace(track.mix(track.gain(), !track.muted())),
                                    project.pending(), project.bpm(), project.metronome()));
                        }
                    } else {
                        studio.record(player, slot);
                        player.closeInventory();
                        return;
                    }
                } else {
                    switch (raw) {
                        case 21 -> studio.stop(player);
                        case 22 -> studio.preview(player, shifted);
                        case 23 -> studio.edit(player, studio.requireProject(player).accept());
                        case 24 -> studio.edit(player, studio.requireProject(player).discard());
                        case 25 -> studio.publish(player);
                        case 26 -> studio.makeBlank(player);
                        default -> { return; }
                    }
                }
                open(player, block);
            } catch (IllegalArgumentException ex) {
                player.sendMessage(ex.getMessage());
            } catch (IOException ex) {
                studio.failure(player, ex);
            }
        });
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static final class Holder implements InventoryHolder {
        private final UUID owner;
        private final Location station;
        private Inventory inventory;

        private Holder(UUID owner, Location station) {
            this.owner = owner;
            this.station = station;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }
}
