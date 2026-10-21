# Drifter's API (the door for other mods)

Drifter: Civilizations (milestone 22, docs/CIVILIZATION.md) and any later addon reach into Drifter only through
`com.dinglebob.drifter.api`, and the things it hands out (a `DrifterEntity`, its `Home`, a `Goals.Goal`). Inside
Drifter anything can move; this door stays put. Both mods live in this repository and build together, so a Drifter
change that would break the addon fails the build at once and is fixed in the same change.

`DrifterApi.API_VERSION` goes up when something here changes in a way a user of it would notice. The addon asks for
exactly the Drifter it was built with (its fabric.mod.json: `"drifter": "${version}"`): the two jars always ship as a pair
with the same version number.

## Adding to Drifters (at mod start)

| Call | What it does |
| --- | --- |
| `DrifterApi.registerGoal(Supplier<Goals.Goal>)` | A goal every Drifter has (one made per Drifter), scored and picked like Drifter's own. Staying alive still comes first. |
| `DrifterApi.registerCatchUpStep(CatchUpStep)` | A step of catch-up, run for each slice of time a Drifter missed while its area was unloaded, after Drifter's own steps. Keep it quick. |

## Events (`DrifterEvents`, Fabric-style: `DrifterEvents.DIED.register(...)`)

| Event | When |
| --- | --- |
| `DIED (drifter, source, comesBack)` | A Drifter died (its things already dropped); `comesBack`: it will respawn. |
| `RESPAWNED (drifter)` | A Drifter came back from the dead, with nothing. |
| `HOME_SET (drifter, home or null)` | Its home was set, moved or forgotten. |
| `GROUP_CHANGED (member UUID, group id or null)` | It joined a group, or left one (or the group ended). |
| `PLAYER_DEED (drifter, player, deed, trustAfter)` | A player did something it noticed and took to heart (the `SocialEvent` table, DESIGN.md section 14). |

More come with the features that make them (trading, news, crimes seen).

## Reading and changing a Drifter

`DrifterApi.loaded(server)`, `home(drifter)`, `setHome(drifter, home)`, `group(uuid)`, `trustInPlayer(drifter, player)`,
`trustInDrifter(drifter, other)`, `think(drifter, text)`, `temporary(drifter)`.

## The addon's own data on a Drifter

Fabric's data attachments (`AttachmentRegistry.createPersistent(...)` with a codec, then `drifter.setAttached(...)`):
saved with the Drifter, versioned by the addon, and Drifter's own save format never changes for it.

## Rules for the addon

- Only this door. If the addon needs something Drifter doesn't offer, Drifter grows the door (a method or an event
  here), in the same change.
- The fairness rules (DESIGN.md sections 1 and 2) hold for everything the addon makes Drifters do.
