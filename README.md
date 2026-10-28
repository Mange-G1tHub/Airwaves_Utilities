# Airwaves : Utilities

A companion add-on for **Create: Airwaves** (NeoForge 1.21.1) that adds two tools:

- **Airwaves Detector** – suggests where to build a relay and shows how uncertain that suggestion is.
- **Airwaves Tape Importer** – puts an audio file of your choice (WAV, AIFF, AU or MP3) onto an Airwaves tape.

> **Beta (0.1.0-beta.1).** Expect rough edges. Bug reports and feedback are welcome in the issue tracker.

## AI disclosure

The first version of this add-on's code was generated with OpenAI Codex. It was then reviewed, debugged, extended and partly hand rewritten by the author with the help of Claude (Anthropic), who also helped write this README. Design decisions, testing and in-game validation were done by the author. The items textures were created by the author.

## Requirements

- Minecraft 1.21.1 with NeoForge 21.1.x (developed and tested on 21.1.264)
- **Create: Airwaves 0.1.0** (the community re-upload) and the mods it requires (Create, Create Aeronautics, Sable)
- Recommended: **FTB Chunks** (or any chunk-loading mod) to keep transmitters and relays running while you are far away – see [Known limitations](#known-limitations)

## Airwaves Detector

### Using it

- **Right-click** – scans the already-loaded chunks around your active transmitters and shows the recommended relay site. A particle ring marks the *zone*, with a column of particles at its center. The chat shows the coordinates, the average reception, the uncertainty, and the zone radius with a per-factor breakdown (for example `base 16 | +5 weak signal | +6 weather`).
- **Airwaves Frequency Card in your offhand** – the scan is restricted to that frequency. Without a card, the result balances **every active frequency**.
- **Shift + right-click on a block** – stores that block as a **target**. The suggested relay is then pulled towards the target (reception still comes first, so the relay stays receivable). The target is saved on the item and shown in its tooltip.
- **Shift + right-click in the air** – clears the target. A target in another dimension is ignored.

### How a site is chosen

1. Candidate sites are sampled on a ring from about 42 % to 90 % of each active station's range, so several transmitters in different places are all taken into account.
2. Each candidate climbs to the nearest local summit, so suggestions land on peaks rather than hillsides (improvements of this system are planned).
3. Each site is scored on:
   - reception quality, taken from Airwaves' own result (so terrain, weather and jammers are included),
   - a preferred distance from the station,
   - height above the station and prominence over the surrounding terrain,
   - your side of the transmitter (only when no target is set),
   - the target (when set),
   - minus an uncertainty estimate, and minus a penalty near active jammers.
4. Sites closer than eight blocks to a transmitter are rejected.

### The zone

The ring is not a promise that the exact block is best: it shows how far you can drift from the center. Its radius grows with the factors that make the estimate less reliable:

| Factor | What it measures |
|---|---|
| Weak signal | How far the average reception is from perfect |
| Weather | How much reception an electrical storm would cost at that spot (AM suffers more than FM, and sheltered spots less) |
| Terrain | How much the terrain blocks the signal |
| Jammers | Active jammers close to the signal path (full effect within 48 blocks, fading to nothing at 96) |
| Obstacles | Solid blocks on the line of sight (leaves are ignored) |

### Configuration

`config/airwaves_utilities-common.toml`, section `[zone]`:

| Key | Default | Meaning |
|---|---|---|
| `baseRadius` | 16 | Minimum radius in blocks |
| `maxRadius` | 96 | Maximum radius in blocks |
| `weakSignal` | 24 | Blocks added when reception drops to 0 % |
| `weather` | 30 | Blocks added if a storm would cut all the signal |
| `terrain` | 30 | Blocks added if terrain blocks all the signal |
| `jammers` | 48 | Blocks added when jammers nearly cut the signal |
| `obstacles` | 12 | Blocks added for a heavily obstructed line of sight |

## Airwaves Tape Importer

- Recipe: tuner board, frequency card, memory chip, copper wire, radio board, display board, polished amethyst and a brass sheet (from Create) (shaped, available from the Airwaves creative tab).

## Airwaves Tape Importer

- Recipe: tape player, crystal oscillator, transistor, counter chip, silver sheet and a memory chip (shaped, also available from the Airwaves creative tab).
- Use it to choose an inventory tape, then pick a **WAV, AIFF, AU or MP3** file. The file browser is drawn inside Minecraft (no native window); it starts in the instance's game folder and also accepts a typed or pasted folder path.
- Audio is converted to Airwaves' 16 kHz mono voice format, sent to the server in small chunks, and stored with the world's Airwaves tape data. Maximum length: **five minutes**. Importing **replaces** the selected tape's content.
- Expect radio-like quality: the limit comes from Airwaves' own audio format.

## Radio range

Fixed radios now deliver and play audio out to **96 blocks** from the radio. The sound position and stereo direction stay anchored to the radio block. The portable radio is unchanged.

## Known limitations

- **No chunk loading.** A transmitter or relay only works while its chunk is loaded. An earlier version tried to keep them loaded itself; it was removed in favor of FTB Chunks (claim and force-load the chunk of the transmitter).
- **Patches on Airwaves internals.** The range changes use mixins on Airwaves classes, and the zone breakdown calls a few of its internal classes. With a different Airwaves version they may silently stop applying; the add-on falls back to simpler behavior rather than crashing.
- The Detector only looks at chunks that are already loaded. If it reports no usable site, move closer to the area or raise the view/simulation distance.
- A very narrow summit may not have room for the relay structure: only one block is checked.
- Most of the texts are currently **in French only** (sry about that).
- Imported audio is resampled simply and not loudness-normalized.

## Building

Java 21. Place the Airwaves JAR you play with at `libs/airwaves-0.1.0.jar` (it is **not** included in this repository), then run:

```
gradlew.bat build
```

(`./gradlew build` on Linux and macOS.) The mod JAR is written to `build/libs/`.

## Credits and licenses

- This add-on: MIT License (see `LICENSE`).
- **Create: Airwaves** is a separate mod by its original authors, not distributed here.
- MP3 decoding uses **JLayer** (LGPL 2.1), bundled inside the mod JAR as a separate library; its Maven coordinate and version are pinned in `build.gradle`.
- Item texture: Mange_.
