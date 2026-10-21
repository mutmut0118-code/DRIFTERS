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
| `HEARD_NEWS (drifter, news, from or null)` | It heard news it hadn't heard, from another Drifter (or put there by a mod), and took it to heart already. |
| `TRADED (asker, giver, paid, got)` | Two Drifters traded: the asker gave `paid` (empty: a gift) for `got`; both have what they were given. |
| `BUILT (drifter, what, where)` | It finished building something at home: `"hut"` or `"house"`. |
| `FEUD (groupA, groupB, what)` | Two groups' feud moved on: `"came to blows"`, `"went to war"`, `"made peace (why)"`, or `"set off to raid"` (A raiding B). |

More come with the features that make them (crimes seen).

## News

Drifters pass news on to the Drifters they meet and get on with (milestone 22 groundwork; docs/CIVILIZATION.md):
a player's big deeds, deaths they saw, and anything a mod gives them. `DrifterApi.tell(drifter, key, about, aboutName,
what, lasts, data)` gives one Drifter news to carry (only what it could know: it saw it, or did it); the same `key`
means the same happening, heard once however many tell it. A `News` has its kind (`PLAYER_DEED`, `DEATH`, `CUSTOM`),
who it's about, what happened in words, when, how many tellings on (`hops`, at most five), how long it's worth telling
(`lasts`, game ticks), and a `data` tag for the mod that made it. `DrifterApi.news(drifter)` reads what one carries.
Each listener fires `HEARD_NEWS`; a mod acts on its own `CUSTOM` news there.

## The world's directory of Drifters

`DrifterApi.directory()` (every Drifter that has lived in the world, gone ones too) and `DrifterApi.record(uuid)`: a
`DrifterRecord` each, readable while the Drifter is far away and unloaded: name, status (alive, dead and coming back,
gone), where it was last and when, when it was first seen, its home and bed, group, stage, deaths, archetype,
disposition, traits, and its trust in its 16 closest Drifters and the 16 players it feels most strongly about. Kept up
to date while it's loaded (position every 5 s, the rest about once a minute), as it was when it unloaded otherwise.
`/drifter directory` shows it. For the world's bookkeeping (a town summary), never for one Drifter to know about another.

## Reading and changing a Drifter

`DrifterApi.loaded(server)`, `home(drifter)`, `setHome(drifter, home)`, `group(uuid)`, `trustInPlayer(drifter, player)`,
`trustInDrifter(drifter, other)`, `think(drifter, text)`, `temporary(drifter)`, `tell(...)` and `news(drifter)` (above).
`DrifterDeals.of(drifter)` is the trade it's in the middle of, if any; `DrifterDeals.want(drifter, stack)` how much it
wants something for itself (0 to 1), what a market would price by.

## Making Drifters, and keeping them in one place

`DrifterApi.spawn(level, feet, name or null)`: a new Drifter standing there, carrying nothing (a fresh start), with a
personality and skin of its own. `DrifterApi.setRoam(drifter, center, radius)`: it explores no further than that from
the middle ("one island"; null lets it go anywhere again; saved with it). `DrifterApi.inBattle(drifter)`: a soldier in a
battle going on now. `DrifterApi.groupById(id)`: a group by its id, as events give it.

## Houses and towns

`DrifterApi.housePlans(server)`: every house plan (id, size, whether its lowest layer is its own ground).
`DrifterApi.doorSides(server, plan, rotation)`: the sides its doors are on once turned (to put its front to a road).
`DrifterApi.placeHouse(level, plan, corner, rotation, wood, front)`: (testing) the house put up at once.
`DrifterApi.settleIn(drifter, house, sharedChests)`: it lives there from now on (home, bed, a chest of its own, no hut).
`DrifterApi.setNoHut(drifter, true)`: it builds no hut (a town's people live in houses). `DrifterApi.formGroup(drifters,
name)` and `DrifterApi.setTrustInDrifter(drifter, other, trust)`.

## Groups (a town's beginnings)

`DrifterApi.group(uuid)` is a Drifter's group: `name`, `leader`, `members`, `base` (the leader's home) and `chests` (the
base's chests, the group's stockpile), kept with the world. Members live at the leader's base and share what they know
of it every few seconds (chests, the house plan, the farm, pen...), build one house together (each takes different
blocks) and only one builds the hut. `DrifterApi.setGroupLimit(group, n)`: how many it takes (0: the "Group size"
setting, four by default, up to 32).

## Roads

Drifters keep to paths they've seen: blocks in the tag `drifter:roads` (dirt paths) cost less to walk on in their
routes. An addon that lays roads of other blocks (gravel, stone bricks) adds them to that tag (a data pack file
`data/drifter/tags/block/roads.json` with `"replace": false`). Drifters wear dirt paths in themselves where they walk often
near home.

## The addon's own data on a Drifter

Fabric's data attachments (`AttachmentRegistry.createPersistent(...)` with a codec, then `drifter.setAttached(...)`):
saved with the Drifter, versioned by the addon, and Drifter's own save format never changes for it.

## Rules for the addon

- Only this door. If the addon needs something Drifter doesn't offer, Drifter grows the door (a method or an event
  here), in the same change.
- The fairness rules (DESIGN.md sections 1 and 2) hold for everything the addon makes Drifters do.
