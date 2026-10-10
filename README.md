# Musical Instruments

> Live music, played by the players of TF-Minecraft.

Musical Instruments turns held items into instruments for performances, tavern gatherings, and spontaneous music. An instrument in the off-hand makes the hotbar a set of playable notes, with sneaking providing an alternate note or chord layout.

Sounds play at the performer's location for nearby listeners, while floating note particles give each performance a visual presence.

## Features

- **Multitrack recording** — record successive takes while hearing the other tracks, preview the mix, and keep or discard each take.
- **Player-made records** — publish immutable song editions onto blank discs, copy them, and play them from ordinary jukeboxes.
- **Visual recording studio** — sneak-right-click an empty jukebox for track cards, volume and mute controls, take review, and publishing from your inventory.
- **Song projects** — open your project library, create multiple songs, and keep each project's tracks and pending take independently.
- **Song settings form** — name your song, choose tempo with a slider, and toggle the private metronome without commands.
- **Live hotbar performance** — changing hotbar slots triggers the instrument's notes immediately.
- **Alternate notes and chords** — sneaking opens a second set of sounds for the same instrument.
- **Repeatable notes** — the selected slot resets after a note so players can play it again.
- **Distinct instrument voices** — each instrument has its own sound mappings, pitch, and volume.
- **Custom instrument items** — supports vanilla items and instrument items from MMOItems, ItemsAdder, and Nexo.
- **In-game note reference** — players can view the keybind layout for the instrument they are holding.

## License

MusicalInstruments is distributed under the [Artistic License 2.0](LICENSE).

Copyright (c) 2026 Justinas Launikonis.
Copyright (c) 2026 TF-Minecraft contributors.

Third-party dependencies retain their own licenses.

## Credits

Created by [Justinas Launikonis](https://github.com/JustinasLa).

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/MusicalInstruments/README.md)

Sneak-right-click an empty jukebox to open your song projects. Choose a song or
**Create a new song**, enter its title, and hold an instrument in your off-hand.
Each horizontal row controls one track: record, volume, mute, listen, keep and
discard. The bottom bar controls the full mix and stops recording or preview.
**Project actions** opens title, tempo, metronome, blank-disc and copy controls.
Use **Publish full song** with a blank disc in your inventory when the mix is ready.
The recording guide explains the steps; projects and tracks have pages when needed.

Commands are also available: start with `/music new <title>` and use
`/music record 1`. After `/music stop`, listen with `/music preview take` and
accept with `/music keep`. Repeat for the other tracks. Convert an ordinary
music disc with `/music blank`, then hold it in your main hand and use
`/music publish`. `/music help` lists the recording commands.

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).
