# Drifter: in-game testing checklist

All in-game testing happens on your own machine. Cloud sessions only check that `./gradlew build` compiles.
Each session updates this file with exactly what to check for everything built so far.

## Setup (local)

1. JDK 21 installed and on `PATH` (`java -version` shows 21).
2. From the repo root: `./gradlew build` (Windows: `gradlew.bat build`). It should end with `BUILD SUCCESSFUL`.
   The mod jar is `build/libs/drifter-0.1.0.jar`.
3. Launch a dev client with `./gradlew runClient`, or copy the jar into a 1.21.1 Fabric instance's `mods` folder together with Fabric API.
4. Create a new Creative world with cheats on for the tests below.

## Milestone 0: project setup

> Session 1 couldn't build in the cloud (Fabric and Mojang download hosts are blocked there), so this is
> the first time the project will have been compiled at all. If anything here fails, paste the error into
> the next session.

- [ ] `./gradlew build` finishes with `BUILD SUCCESSFUL`.
- [ ] `./gradlew runClient` reaches the title screen without crashing.
- [ ] The game log (console, or `run/logs/latest.log`) contains the line `Drifter initialized`.
- [ ] (Optional, if you have Mod Menu installed) "Drifter" appears in the Mods list with the icon and description.
- [ ] A new world loads and you can walk around normally (nothing gameplay-related exists yet).

## Milestone 1: Body

Nothing to test yet. No Drifter entity exists so far.
