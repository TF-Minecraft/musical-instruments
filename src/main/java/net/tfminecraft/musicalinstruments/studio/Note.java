package net.tfminecraft.musicalinstruments.studio;

/** A resolved sample trigger, captured independently of subsequent config changes. */
public record Note(int tick, String instrument, String sound, float volume, float pitch) {
    public Note {
        if (tick < 0 || tick >= Song.MAX_TICKS || instrument == null || instrument.length() > 100
                || sound == null || sound.length() > 200 || !sound.matches("[a-z0-9_.-]+(:[a-z0-9_./-]+)?")
                || !Float.isFinite(volume) || volume < 0 || volume > 16
                || !Float.isFinite(pitch) || pitch < 0.5f || pitch > 2) {
            throw new IllegalArgumentException("Invalid recorded note");
        }
    }
}
