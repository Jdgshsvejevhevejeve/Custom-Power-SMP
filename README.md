# Custom Powers SMP — Paper 1.21.11 / Java 21

## Included
- Eight powers: Chronos, Mirage, Glitch, Rift, Prism, Phantom, Mimic, Soul
- 3 starting lives; PvP kills transfer the victim's selected power to the killer
- `/power`, `/power list`, `/power select <name>`, `/power use`
- `/cpsmp reload` (OP)
- Custom Power Core item with CustomModelData and bundled resource pack source
- Player data saved in `plugins/CustomPowersSMP/config.yml`

## Build
1. Install JDK 21 and Maven.
2. Open this folder in IntelliJ IDEA or Eclipse as a Maven project.
3. Run `mvn package`.
4. Copy `target/custompowerssmp-1.0.0.jar` into the server's `plugins` folder.
5. Restart Paper 1.21.11.

## Resource pack
The `resource-pack/` folder contains the pack source. Zip the CONTENTS of that folder (pack.mcmeta must be at the ZIP root), host the ZIP at a direct-download HTTPS URL, then set `resource-pack-url` in the plugin config. Set `resource-pack-sha1` if you have its SHA-1 hash. Restart the server.

The included item model uses CustomModelData overrides on amethyst shards. If your exact Minecraft client version uses the newer item-model format, migrate the override file to that version's item definition format.

## Notes
- Powers are currently basic Bukkit effects/particles; Mirage and Mimic are visual prototypes, not true NPC clones/player disguises.
- The plugin is a starter project; compile and test on a staging server before using it live.
