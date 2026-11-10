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
`DrifterApi.setErrand(drifter, errand)` / `errand(drifter)`: an errand to another town (`Errand`: there, its name, home,
whom to meet and where they live, the message; `arrived`, `back`, `told`, `gaveUp`), walked by `ErrandGoal` (0.22.39).
`DrifterApi.tension(a, b)` / `setTension(server, a, b, t)`: two groups' feud tension (0.22.39).
`DrifterApi.setWarManaged(group, on)`, `declareWar(server, a, b)`, `makePeace(server, a, b, days)`,
`setRaidTargets((attackers, defenders) -> pos)`: wars run by the addon (0.22.40).
`DrifterApi.setRaidMusters(group -> pos)`: where a group's raiders gather before setting off (a town's plaza; 0.22.41).
`DrifterApi.setTownsForVillages(true)`: an addon makes its own towns where villages would be; with the setting on
(`villagesReplaced()`), no village (#minecraft:village) generates or can be located, and no wandering trader comes;
`noVillagers()`: zombie villagers kept out too (0.23.0).
`DrifterApi.placeHouse(level, plan, origin, rotation, wood, stone, front)`: a house with its cobblestone of another stone
("sandstone", "mossy_cobblestone"); `PlacedHouse.stone()`; kept with its home's palette (0.23.2).
`DrifterApi.stillBuilding(drifter, days)`: its house isn't finished; it builds what's missing of its plan over those days.
`DrifterApi.randomName(server, random)`: a name as Drifters have them (0.23.4).
`DrifterApi.addTrustInPlayer(drifter, player, name, change)`: it thinks better (or worse) of a player (0.23.6).
`TownWork.leaning(drifter, goal)`: how much more a goal (by id) counts for it (its line of work; added to the goal's urgency; 0.23.7).
`TownWork.wares(drifter)` and `TownWork.Wares(items, count, what)`: what its line has it make to sell (any of the items till it carries count of them; core goal "wares"; 0.23.9).
`ShopKeeper.open(keeper)`: its open hours (default: the stall's, morning to mid-afternoon; 0.23.9).
`DrifterApi.frontDoor(server, planId, origin, rotation, front)`: a house's front door, as put up (0.23.9).
`DrifterApi.setBond(drifter, otherId, name, trust, familiarity)`: what it thinks of another Drifter, by id (0.22.37).
`DrifterApi.settleDown(drifter)`: a Bandit living in a town gives up killing on sight (an Opportunist toward strangers
from now on; 0.22.29). `DrifterApi.bandit(drifter)` (born one, or one now), `notABandit(drifter)` (a new personality of
another kind), `turnBandit(drifter)`, `registerJoinRule((drifter, group) -> mayJoin)` (0.22.30; checked when friends
team up), `crookSoon(drifter)` (debug). `TownWork.crook(drifter)`, `othersChest(drifter, chest)`, `crime(drifter, kind,
victim, where)`: a town's crook and what it did (0.22.30). `TownWork.banner(drifter)` (a `TownWork.Banner`: a banner item,
a spot, what it is) / `bannerUp(drifter, where)`: a banner its town wants it to make and stand (a nation's, on the plaza;
the core goal `TownBanner` makes it from its own things and stands it; 0.22.42). `DrifterApi.setNoHut(drifter, true)`: it builds no hut (a town's people live in houses). `DrifterApi.formGroup(drifters,
name)` and `DrifterApi.setTrustInDrifter(drifter, other, trust)`.

Towns founded by their people (API 3):
`DrifterApi.housePlansFor(drifter)`: the plans it would weigh for a house of its own (to its taste).
`DrifterApi.siteFor(drifter, plan, corner, rotation)`: where the house's corner goes (its height found) if the ground
there as that Drifter knows it suits a house (seen, not too steep, no water, clear of its own things); null if not. A
plot along a road is checked with this: a Drifter only builds where it has seen the ground.
`DrifterApi.buildHouseAt(drifter, plan, origin, rotation)`: its home's house from now on (group mates living at the same
home build it with it). `DrifterApi.homeAtHouse(drifter, plan, origin, rotation, chests)`: a home of its own there (it
moves out of a group's base; no hut, no bed until the house is up; its old home's chests and farm still its).
Town builds (API 3, 0.22.9): `DrifterApi.townBuild(builders, plan, origin, rotation, label)`: they build it together (in
the first one's woods), each by day when near it, alongside its own house; `DrifterEvents.BUILT` says `"town:" + label`
when it's up. `joinTownBuild`, `townBuildOf`, `endTownBuild`. `DrifterApi.storePlan(housePlan)`: that house with chests
along its inside walls; `DrifterApi.chestsIn(server, plan, origin, rotation)`: where its chests stand once built.
Shared houses (0.22.15): `DrifterApi.households(level, planId, origin, rotation)` (how many households a house takes:
one, and one more for each other room big enough to sleep in, up to four); `settleInToo(drifter, house, shared)` (a town
made ready: another household in a house already lived in, its bed in another room). A Drifter given a home in a
finished house with `homeAtHouse` moves its bed in away from the beds already there.

Town work (0.22.14): `DrifterApi.setTownWork(drifter, TownWork)` puts a Drifter in on its town's shared work (not saved;
set it again each check): `TownWork.roads` (columns to make into path, nearest the middle first), `townGround(x, z)`
(kept lit), `center()`, `radius()`. With it set, the Drifter works on the roads with a shovel by day, lights the town's
ground (making torches), and eases the ground round its finished house.

Towns made at once (0.22.13): `DrifterApi.settleIn` now furnishes the house (`building/Furnishing`: storage wall with
the crafting table, furnace and smoker with a fuel chest, dump chest, bedside chest) and its Drifter knows its things and
the inside of its house; `knowsBox(drifter, box)` (debug: it knows every block in a box), `fellTree(level, pos)` (the tree
a log or leaf belongs to, felled whole), `equipSettled(drifter, random)` (mid-to-late game gear), `tidyTrees(level, box)`.

Shops (API 3, 0.22.11): `DrifterApi.setShop(drifter, shopKeeper)`: right-clicked, it opens the villager trading screen
with what `ShopKeeper.offers` gives (each `ShopOffer`: what the player pays, what they get, how many times); every trade
moves the real things in and out of its pockets. `ShopKeeper.stand` (where it keeps shop by day) and `stock` (what it
takes from its home's chests to sell). `DrifterApi.stallPlan()`, `stallStand(...)`, `worth(stack)`, `money(points)`,
`moneyWorth(stack)`, `coins()`, `trait(drifter, name)`, `openShop(drifter, player)`, `rememberChest(drifter, chest)`.
`DrifterApi.addChests(drifter, chests)`, `DrifterApi.addTrustInDrifter(drifter, other, delta)`, `DrifterApi.groups()`,
and `DrifterApi.setNeighbourly(true)`: neighbours who get along don't turn into rivals over the same ground (only those
already at odds); off in the plain mod.

Acting through its helper (0.23.28): `DrifterApi.actingHelper()` is the helper player of the Drifter acting through it
right now (inside its break, place, use: what that sets off runs then, such as a Bountiful bounty's reward command, which
gets this helper as its player), or null; things given to it reach the Drifter when the action ends (give them to the
helper, not the Drifter's own pack, which is overwritten then). `DrifterApi.drifterBehind(entity)`: the Drifter behind an
entity (itself, or the one whose helper it is), or null.

The alarm bell (0.23.30): `TownWork.Bell` carries `alarm` (rung fast, twelve strokes) and `trouble` (where it is, or
null); a bell struck five times in five seconds is an alarm to Drifters within 32 blocks, passed on through a town
(`perception.BellHearing`: `say` what it's for before ringing; `Senses.heardAlarm()` what it heard). Its own town's alarm:
its guards and brave go to the trouble, the rest go in (`social.AlarmGoal`). `DrifterApi.timid(drifter)`. A town's chests:
`Home.townChests` (the store's chests in a home's list: the town's, for its work, not the home's own gear; `settleIn`,
`settleInToo` and `moveIntoHouse` mark their `shared` chests, `DrifterApi.townChest(home, chest)` marks one more),
`Home.ownChests()`, `Planner.planOwn`. `DrifterApi.holdStill(drifter, ticks)`: still and deciding nothing for a while (a
town being made; not saved).

0.23.33: `ShopKeeper.stocksFromTown(keeper)`: whether its shop stocks up from the town's chests too (default true; false:
its household's own chests only, as a trade's shop). `DrifterApi.groupAtDeath(drifter)`: the group it was in as it died
(with death for good it's out of its group before the death event; null if none).

0.23.35: `DrifterEvents.ALARM_RUNG` (player, bell): a player rang a bell as an alarm (once a ringing); whether it was for
nothing is the listener's call. `BellHearing.callDown(teller, heard, at, reach)`: an alarm shouted from a spot (down a
mine) to the Drifters within reach of it. A town's alarm is answered by anyone not timid (`DrifterApi.timid`), and they
keep watch till it's over; against a player wanted in its town (`TownWork.standing` "wanted"), a Drifter that isn't
timid stands and fights as at war.

0.23.36: `TownWork.killOnSight(drifter, player)`: its town wants them past forgiving (its leader's murder, a massacre):
its people who aren't cowards (`DrifterApi.timid`: Bravery under 0.3, no guard) go for them wherever they see them.
`DrifterApi.markBuilt(level, pos)`: a block of a build (a town mine's stairs): not dug for stone nor tunnelled through.

0.23.38: `DrifterApi.onServerThread(server, task)`: work a background thread hands back, run on the server thread's next
turn, dropped if the server's stopping (use it, not `server.execute`, from any background thread).

0.23.37: `DrifterApi.quicksave()`: Quicksave is installed. `DrifterApi.autosave(player, label)`: a Quicksave autosave now
(called this in its list), tried again for 30 s while Quicksave refuses for the moment (a fight, a fall).

Seen taking (0.23.29): `DrifterEvents.SEEN_TAKING` (player, chest, what they took, the Drifters who saw them at it): a
player took things out of a chest or a barrel while Drifters saw them; whether that was theft is the listener's call.
`TownWork.standing` may answer "wanted" too: about the town, its guards and bold people go for them on sight, the timid
keep clear ("exiled": driven out). `Home.townMine`: a town's mine as faces to dig on from (`Home.MineFace`: where, the
way on, and `Dig` STRIP, BRANCH or DOWN); a home's mining trips start there. `Home.chestWith(item, wanted, busy)`: the
chest to fetch from (one with enough, nearest home, not crowded). `DrifterApi.furnitureSpot(level, home, notHere)`: a spot
in the room round its bed for one more piece of furniture.

## Gear (API 4)

Any mod's weapons, armor, shields and trinkets, read from the items themselves (their attribute modifiers, rarity and
recipes), with what each Drifter knows of them and its taste. All on the server thread.

| Call | What it does |
| --- | --- |
| `DrifterApi.isGear(stack)` | A weapon of some kind, a piece of armor, a shield, or a trinket (Trinkets, optional): any mod's. |
| `DrifterApi.isLauncher(stack)` | A bow or crossbow, vanilla or any mod's. |
| `DrifterApi.isWeapon(stack)` | A weapon it can fight with in hand, vanilla or any mod's: a sword, a katana, a weapon file's item, an axe, and a trident (thrown or swung). Not a bow, crossbow or shield, and not a pickaxe, shovel or hoe. |
| `DrifterApi.gearValue(drifter, stack)` | What it's worth to that Drifter for its use: armor worn in its slot, a trinket in its best slot, a bow, crossbow or trident by its ranged score, any other weapon by damage per second in its hands; 0 otherwise. Compare within one use only. |
| `DrifterApi.armorPoints(stack)` | Its armor points in the slot it's made for, as the armor bar counts them (an iron chestplate 6); 0 if not armor. |
| `DrifterApi.knowsItem(drifter, item)` | It knows the item exists (vanilla, a weapon family, a veteran's craftable gear, or seen, owned, unlocked, told). |
| `DrifterApi.learnItem(drifter, item, where)` | It has seen the item (`where`: for its thought); nothing if it knew it. Only for what it could really have seen. |
| `DrifterApi.gearStyle(drifter)` | Its taste: `"practical"`, `"flashy"`, `"loyal"` or `"follower"`. |
| `DrifterApi.gearLiking(drifter, stack)` | Its `gearValue` with its taste in it (never below 0). |
| `DrifterApi.gearItCanMake(drifter, maxTier)` | The gear it knows how to make at a crafting table, at `maxTier` or lower (1 wood, 2 stone, 3 iron, 4 diamond, 5 netherite; a weapon by the sword it matches, armor by the vanilla piece its score matches, a bow, crossbow, shield or trinket as iron): every such vanilla piece, then at most four modded pieces it knows (two weapons and two armor pieces, a side's unused places going to the other), those it likes best by their use value with its taste, each against the iron piece of the same use (they hold until what it knows changes). Kept per Drifter: cheap to ask often. |
| `DrifterApi.gearItCanMake(drifter, maxTier, which)` | The same, only what `which` (a `Predicate<ItemStack>`) takes; the four modded pieces counted among those. |

A weapon whose tier is unknown counts as iron (3) in `gearItCanMake`.

`DrifterApi.worth(stack)` prices modded gear by its value (`GearValue.points`: its tier, rarity and what it's made of),
even a mod's `ArmorItem` or `TieredItem`; vanilla items keep their prices. Drifter: Civilizations' smiths make and sell
the modded weapons and armor they know (no trinkets or shields; fletchers their bows and crossbows), the stall doesn't sell any mod's gear as
goods, and a town's veteran is one in a chestplate of 6 armor points or more, any mod's.

## Groups (a town's beginnings)

`DrifterApi.group(uuid)` is a Drifter's group: `name`, `leader`, `members`, `base` (the leader's home) and `chests` (the
base's chests, the group's stockpile), kept with the world. Members live at the leader's base and share what they know
of it every few seconds (chests, the house plan, the farm, pen...), build one house together (each takes different
blocks) and only one builds the hut. `DrifterApi.setGroupLimit(group, n)`: how many it takes (0: the "Group size"
setting, four by default, up to 32).

## The civilized world (milestone 23, W4)

- `DrifterApi.civilizedWorld()`: new worlds come with the addon's world plan (the setting "A world of nations", with
  towns replacing villages); `nationsSetting()` (1 few, 2 normal, 3 many), `spreadSetting()` (1 round spawn to 5 over the
  world).
- `DrifterApi.naturalSlab(state)`: a slab the land has (Terrain Slabs' `generated` slabs), not one somebody put down:
  half a block of ground, cleared like a plant where something's built.
- `DrifterApi.setNotPlaced(Predicate<Holder<Structure>>)`: structures the addon places itself get no placements (so
  /locate and generation don't look for them); `notPlaced(holder)` asks.

## Roads

Drifters keep to paths they've seen: blocks in the tag `drifter:roads` (dirt paths) cost less to walk on in their
routes. An addon that lays roads of other blocks (gravel, stone bricks) adds them to that tag (a data pack file
`data/drifter/tags/block/roads.json` with `"replace": false`). Drifters wear dirt paths in themselves where they walk often
near home.

## The addon's own data on a Drifter

Fabric's data attachments (`AttachmentRegistry.createPersistent(...)` with a codec, then `drifter.setAttached(...)`):
saved with the Drifter, versioned by the addon, and Drifter's own save format never changes for it.

## Friends: talk and closeness with players (0.23.38-side.1, API 5, `TalkApi`)

For the addon's friendships (D2, docs/LIVED_WORLD.md). In a class of its own so this door grows in one place per
feature. Each hook is one listener, a no-op until the addon sets it.

| Member | What it does |
| --- | --- |
| `DrifterEvents.PLAYER_ANSWERED (drifter, player, topic, does, yes)` | A player answered a question a Drifter asked in chat (a topic's question, or one it asked itself: topic `"told.hungry"`), before it answers back. `does`: what a yes means (`"team"`, `"follow"`, `"come"`, `"give_food"`, or an addon's own word such as `"opinion"`). |
| `TalkApi.registerChatHandler((player, drifter, text, intent) -> handled)` | Asked first when a player says something a Drifter takes as said to it (after the encounter check); true: the addon answered, Drifter says nothing of its own. |
| `TalkApi.registerPlayerFacts((drifter, player, words, flags) -> ...)` | Adds to what a Drifter knows of a player for its talk: flags for `b:` conditions in topics, words for `{b.word}`. |
| `TalkApi.setTrustFloor((drifter, player) -> floor)` | The least its trust in a player drifts down to each day (`Double.NEGATIVE_INFINITY`: none). |
| `TalkApi.setNameNote((drifter, viewer) -> note)` | A line for that player on the hover of its name in chat ("your friend"). |
| `TalkApi.setWhisperRule((drifter, player, key) -> whisper)` | Its answers to a player that say where something is (coordinates) are whispered when this says so. |
| `TalkApi.whisper(drifter, player, text)` | A line to one player, shown as a /msg ("X whispers to you: ..."); nobody else sees it, no Drifter overhears. |
| `TalkApi.sayTo(drifter, player, text)`, `line(drifter, key, vars...)` | Says a line aloud to a player; picks a line from the chat files in its voice. |
| `TalkApi.familiarity(drifter, player)`, `addFamiliarity(drifter, player, name, change)`, `lastSeen(drifter, player)` | How well it knows a player (0 to 100), and when it last saw them. |
| `TalkApi.memories(drifter, kind)` | What it remembers of a `MemoryKind` (ORE, STRUCTURE, LANDMARK...): a copy. Only what it saw or was told. |
| `TalkApi.spareFood(drifter)`, `bring(drifter, player, item, count)` | Food it can spare; it walks over and tosses a player that many (gives up after a minute). |
| `TalkApi.startTalk(drifter, player, topic)` | It brings up a player topic (by id, or one that suits) with a player now. Addon topics go in `data/<addon>/drifter_talk/` as `player_topics`. |
| `TalkApi.hostileOnSight(drifter)`, `talking(drifter)` | A Bandit (now) or a delver; in a talk now. |
| `TalkApi.callName(drifter, player)`, `meaning(text, keys...)` | What it calls a player; which of the chat files' `answers` meanings a message has (an addon's own phrases too). |
| `TalkApi.topicAbout(topic)`, `playerTopics(tag)` | A player topic's `about` words; the player topics with a tag (or a word of their id). |

## Rules for the addon

- Only this door. If the addon needs something Drifter doesn't offer, Drifter grows the door (a method or an event
  here), in the same change.
- The fairness rules (DESIGN.md sections 1 and 2) hold for everything the addon makes Drifters do.
