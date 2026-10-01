package net.tfminecraft.musicalinstruments.studio;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tfminecraft.musicalinstruments.InstrumentPlugin;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native title input and a tempo slider, without routing private input through roleplay chat. */
public final class StudioDialogs {
    @FunctionalInterface
    interface Reopen { void open(Player player, Block station) throws IOException; }
    @FunctionalInterface
    private interface Response { void apply(Player player, DialogResponseView view) throws IOException; }

    private final InstrumentPlugin plugin;
    private final StudioService studio;
    private final Reopen reopen;

    StudioDialogs(InstrumentPlugin plugin, StudioService studio, Reopen reopen) {
        this.plugin = plugin;
        this.studio = studio;
        this.reopen = reopen;
    }

    public void settings(Player player, Location station) throws IOException {
        studio.requireIdle(player);
        Project project = studio.requireProject(player);
        AtomicBoolean used = new AtomicBoolean();
        UUID id = project.song().id();
        var accept = action(player, station, used, (target, view) -> {
            Float bpm = view.getFloat("tempo");
            Boolean metronome = view.getBoolean("metronome");
            String title = view.getText("title");
            if (bpm == null || !Float.isFinite(bpm) || bpm != bpm.intValue() || metronome == null || title == null) {
                throw new IllegalArgumentException("Please enter a title and a valid tempo");
            }
            applySettings(target, id, title, bpm.intValue(), metronome);
        });
        var cancel = action(player, station, used, (target, view) -> {});
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Song settings", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text("Name your song and set the recording click."))))
                        .inputs(List.of(
                                DialogInput.text("title", Component.text("Song title")).initial(project.song().title()).maxLength(64).width(300).build(),
                                DialogInput.numberRange("tempo", Component.text("Tempo (BPM)"), 40, 240)
                                        .step(1f).initial((float) project.bpm()).width(300).build(),
                                DialogInput.bool("metronome", Component.text("Private metronome")).initial(project.metronome()).build()))
                        .canCloseWithEscape(true).build())
                .type(DialogType.confirmation(
                        ActionButton.create(Component.text("Save", NamedTextColor.GREEN), null, 150, accept),
                        ActionButton.create(Component.text("Back", NamedTextColor.GRAY), null, 150, cancel))));
        player.closeInventory();
        player.showDialog(dialog);
    }

    public void reset(Player player, Location station) throws IOException {
        studio.requireIdle(player);
        Project project = studio.requireProject(player);
        AtomicBoolean used = new AtomicBoolean();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Start a new song?", NamedTextColor.RED))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "This clears the editable tracks and pending take of '" + project.song().title()
                                        + "'. Published discs keep their music.")))).canCloseWithEscape(true).build())
                .type(DialogType.confirmation(
                        ActionButton.create(Component.text("Clear project", NamedTextColor.RED), null, 150,
                                action(player, station, used, (target, view) -> {
                                    if (!studio.requireProject(target).equals(project)) {
                                        throw new IllegalArgumentException("The project changed. Open the confirmation again");
                                    }
                                    studio.create(target, "Untitled", true);
                                })),
                        ActionButton.create(Component.text("Keep my song", NamedTextColor.GREEN), null, 150,
                                action(player, station, used, (target, view) -> {})))));
        player.closeInventory();
        player.showDialog(dialog);
    }

    void applySettings(Player player, UUID projectId, String title, int bpm, boolean metronome) throws IOException {
        Project project = studio.requireProject(player);
        Song song = project.song();
        if (!song.id().equals(projectId)) {
            throw new IllegalArgumentException("The project changed. Open its settings again");
        }
        studio.edit(player, new Project(new Song(song.id(), song.owner(), song.author(), title.strip(), song.tracks()),
                project.pending(), bpm, metronome));
    }

    private DialogAction action(Player owner, Location station, AtomicBoolean used, Response response) {
        UUID ownerId = owner.getUniqueId();
        return DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player target) || !target.getUniqueId().equals(ownerId)
                    || !used.compareAndSet(false, true) || !plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!target.isOnline()) return;
                try {
                    Block block = StudioMenu.requireStation(target, station);
                    response.apply(target, view);
                    reopen.open(target, block);
                } catch (IllegalArgumentException ex) {
                    target.sendMessage(ex.getMessage());
                } catch (IOException ex) {
                    studio.failure(target, ex);
                }
            });
        }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build());
    }
}
