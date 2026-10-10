package net.tfminecraft.musicalinstruments.studio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One cursor across all tracks; notes at the same tick are dispatched together. */
public final class Timeline {
    public record Trigger(Note note, float gain) {}

    private final List<Trigger> triggers = new ArrayList<>();
    private int cursor;

    public Timeline(Song song, int excludedSlot) {
        for (Track track : song.tracks()) {
            if (!track.muted() && track.gain() > 0 && track.slot() != excludedSlot) {
                for (Note note : track.notes()) {
                    if (note.volume() > 0) {
                        triggers.add(new Trigger(note, track.gain()));
                    }
                }
            }
        }
        triggers.sort(Comparator.comparingInt(trigger -> trigger.note().tick()));
    }

    public List<Trigger> due(int tick) {
        int start = cursor;
        while (cursor < triggers.size() && triggers.get(cursor).note().tick() <= tick) {
            cursor++;
        }
        return triggers.subList(start, cursor);
    }
}
