package net.tfminecraft.musicalinstruments.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StudioStoreTest {
    @TempDir Path directory;

    private Project project() {
        var old = new Track(1, 100, 0.5f, true, List.of(new Note(15, "lute", "instruments.lute", 4, 1)));
        var pending = new Track(1, 50, 1, false, List.of(new Note(0, "flute", "tfmc:flute", 2, 1.5f)));
        return new Project(new Song(UUID.randomUUID(), UUID.randomUUID(), "Musician", "Música", List.of(old)), pending, 120, false);
    }

    @Test
    void projectsAndPendingTakesSurviveANewStoreInstance() throws Exception {
        Project project = project();
        new StudioStore(directory).save(project);
        assertEquals(project, new StudioStore(directory).project(project.song().owner()).orElseThrow());
    }

    @Test
    void publishedEditionRemainsImmutableAfterProjectChanges() throws Exception {
        StudioStore store = new StudioStore(directory);
        Project project = project();
        Song edition = project.song().edition();
        store.publish(edition);
        store.save(project.accept());
        assertEquals(edition, new StudioStore(directory).song(edition.id()));
        assertThrows(IOException.class, () -> store.publish(edition));
        assertEquals(edition, store.song(edition.id()));
    }

    @Test
    void atomicReplacementLeavesNoTemporaryFiles() throws Exception {
        StudioStore store = new StudioStore(directory);
        Project project = project();
        store.save(project);
        store.save(project.accept());
        assertEquals(project.accept(), store.project(project.song().owner()).orElseThrow());
        try (var files = Files.walk(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void corruptionAndUnknownFormatsAreReportedWithoutOverwritingData() throws Exception {
        StudioStore store = new StudioStore(directory);
        UUID owner = UUID.randomUUID();
        Path file = directory.resolve("projects").resolve(owner + ".yml");
        for (String invalid : List.of("format: 2\n", "format: 1\ntracks: nonsense\n", "[broken yaml")) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> store.project(owner));
            assertEquals(invalid, Files.readString(file));
        }
    }

    @Test
    void filenameIdentityMustMatchTheStoredOwnerOrEdition() throws Exception {
        StudioStore store = new StudioStore(directory);
        Project project = project();
        store.save(project);
        UUID anotherOwner = UUID.randomUUID();
        Files.copy(directory.resolve("projects").resolve(project.song().owner() + ".yml"),
                directory.resolve("projects").resolve(anotherOwner + ".yml"));
        assertThrows(IOException.class, () -> store.project(anotherOwner));
        Song edition = project.song().edition();
        store.publish(edition);
        UUID anotherId = UUID.randomUUID();
        Files.copy(directory.resolve("songs").resolve(edition.id() + ".yml"),
                directory.resolve("songs").resolve(anotherId + ".yml"));
        assertThrows(IOException.class, () -> store.song(anotherId));
    }
}
