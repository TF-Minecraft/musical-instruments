package net.tfminecraft.musicalinstruments.studio;

import java.util.List;

public record Track(int slot, int lengthTicks, float gain, boolean muted, List<Note> notes) {
    public Track {
        notes = List.copyOf(notes);
        if (slot < 1 || slot > Song.MAX_TRACKS || lengthTicks < 1 || lengthTicks > Song.MAX_TICKS
                || !Float.isFinite(gain) || gain < 0 || gain > 2 || notes.size() > Song.MAX_NOTES) {
            throw new IllegalArgumentException("Invalid track");
        }
        int previous = -1;
        for (Note note : notes) {
            if (note.tick() < previous || note.tick() >= lengthTicks) {
                throw new IllegalArgumentException("Notes must be ordered and inside the track");
            }
            previous = note.tick();
        }
    }

    public Track mix(float newGain, boolean newMuted) {
        return new Track(slot, lengthTicks, newGain, newMuted, notes);
    }
}
