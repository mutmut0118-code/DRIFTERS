# Drifter mod: rules for every session

Drifter is a Minecraft mod adding a player-skinned humanoid mob that plays survival by player rules.
This is a long, multi-session project. Follow these rules in every session.

## Read first, every session

1. `docs/DESIGN.md` — the full design and the source of truth. Sections 1 (fairness rules) and 2 (architecture) are hard constraints.
2. `docs/PROGRESS.md` — what is done, half-done, next, and open design questions.
3. `docs/TESTING.md` — what the user checks in-game.

## Version targets

| Thing | Value |
| --- | --- |
| Minecraft | 1.21.1 |
| Mod loader | Fabric Loader 0.19.5 (or newer 0.x), Fabric API `0.116.17+1.21.1` |
| Java | 21 (mod bytecode and the JDK that runs Gradle) |
| Build | Gradle 9.5.0 wrapper, Fabric Loom `1.17-SNAPSHOT` |
| Mappings | Official Mojang mappings (`loom.officialMojangMappings()`), as in the template. Use Mojang names (`ResourceLocation`, `Mob`, `PathfinderMob`, `PlayerModel`, ...), not Yarn names. |
| Mod ID / name | `drifter` / Drifter |
| Package | `com.dinglebob.drifter` (common code in `src/main`, client-only code in `src/client`) |

The project is based on the official `FabricMC/fabric-example-mod` template, branch `1.21.1`. The one deviation:
the template pins Loom 1.18, which needs a Java 25 JDK just to run Gradle; we pin Loom 1.17 (runs on Java 21)
and the Gradle wrapper it was built with (9.5.0). Don't bump Loom or Gradle without asking.

Client-only code (renderers, models, screens) must stay in `src/client`. Common code must never import
`net.minecraft.client.*`.

## Fairness rules (hard constraints, DESIGN.md section 1)

Every system must obey these. Never add a shortcut that breaks them, even temporarily, unless it is a
clearly labeled debug command.

- **Knowledge only through perception** (section 5). Decisions read the Drifter's memory, never the world directly. No x-ray: a buried ore is unknown until a face of it has been seen.
- **Actions take player time.** Mining speed follows tool tier, Efficiency and Haste; eating takes 1.6 s; crafting includes a short "inventory open" pause.
- **Player reach.** 4.5 blocks to interact with blocks, 3 blocks to hit entities.
- **Player vulnerability.** It takes every kind of damage a player does, and hostile mobs target it like a player.
- **No impossible placement.** No blocks floating in air, no opening chests out of reach, no interacting through solid blocks.
- No free items: everything it carries was gathered, crafted, looted, traded, or came from its starting kit.

## Architecture (hard constraint, DESIGN.md section 2)

- The Drifter is a **custom mob entity** (option C, hybrid). It is **not** a fake server player.
- It borrows Fabric API's server-side `FakePlayer` **only at the moment it interacts with the world** (break, place, use, craft), syncing its inventory into the helper per interaction.
- Control is a custom layered controller (strategist, selector, planner, executor, reflexes, motor; section 8), with a Brain-style split: sensors write memory, behaviors read memory.
- Movement goes through virtual keys (forward, strafe, jump, sneak, sprint) into the normal living-entity movement physics; the head moves through the camera model (section 16.1).
- Use the player's own numbers (attributes, constants) rather than hardcoded copies wherever the game exposes them (section 3).

## How to work

- **One roadmap milestone at a time** (DESIGN.md section 25, tracked in `docs/PROGRESS.md`). Don't start the next milestone's systems early, beyond small stubs needed to compile.
- **Don't change the design on your own.** If something in DESIGN.md seems wrong or impossible on 1.21.1, write it under "Design questions" in `docs/PROGRESS.md` and ask the user.
- **Soft dependencies** (Simply Swords, Simply Bows, Better Combat, Combat Roll, Mob Control, EMF) are optional integrations for later milestones. Don't add them until their milestone, and then only as optional (detected at runtime, never required).
- Keep tunable numbers in data files or clearly named constants, so they can move to datapacks later (section 25).
- Version saved data from day one (section 20).

## Cloud vs local split

- **Cloud sessions** (like Claude Code on the web) write code and check that it compiles with `./gradlew build`. That is the **only** verification done in the cloud.
- **Never run `./gradlew runClient` or `./gradlew runServer`, and never try to launch Minecraft in a cloud session.** All in-game testing happens on the user's own machine.
- If a build fails because dependencies can't download (network or sandbox limits), **stop and tell the user what failed**. Don't retry over and over. The build needs these hosts: `maven.fabricmc.net`, `meta.fabricmc.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`, plus `services.gradle.org`, `repo.maven.apache.org` and `plugins.gradle.org`.
- The GitHub Actions workflow (`.github/workflows/build.yml`) also runs `./gradlew build` on every push.

## End of every session

1. The build must compile at the end of every session, even if a feature is half-done (stub it behind a `TODO`).
2. Update `docs/PROGRESS.md`: done, half-done, next steps, design questions.
3. Update `docs/TESTING.md`: exactly what the user should check in-game for everything built so far, with the commands to use.
4. Commit and push to the session's branch.
