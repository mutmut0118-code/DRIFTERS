# How to add a whim

A whim is something a Drifter does just because: watching the sunset, a little build, waving to a friend. Each whim is a
few lines in a data file. You don't need to touch any code: write the lines, start the game, and read the log.

(The ideas list is `docs/WHIMS.md`; this page is about writing them.)

## Where whims live

- In the mod: `src/main/resources/data/drifter/drifter_whims/*.json`.
- In a datapack: `data/<your namespace>/drifter_whims/<any name>.json`. Datapacks can add whims without rebuilding the
  mod. A whim with the same `id` as an existing one replaces it.

Each file looks like this:

```json
{
	"whims": [
		{ "id": "watch_the_sunset", "action": "watch_sky", "params": {"what": "sun"}, "when": "sunset", "thought": "watching the sunset" },
		{ "id": "another_one", "action": "...", "thought": "..." }
	]
}
```

## Checking your whim

When the game (or a server) starts, and after `/reload`, every whim is checked. Look in the log (`logs/latest.log`) for
lines starting with `[Drifter] whim`:

- `whim my_whim in drifter:drifter_whims/mine.json: unknown field "wieght" - did you mean "weight"? (ignored)`: a
  warning. The whim still loads; that one setting is ignored.
- `whim my_whim in ... left out: no trait called "curiousity" - did you mean "curiosity"? (there are: ...)`: the whim
  can't work, so it isn't loaded. Fix it and `/reload`.
- The last line says how many loaded: `loaded 121 whims (13 left out); 1 with warnings above`. ("Left out" also counts
  whims for mods you don't have installed.)

In the game:
- `/drifter whims`: every whim for the nearest Drifter, how much it likes each one, and why one doesn't fit right now.
- `/drifter whim <id>`: makes the nearest Drifter do that whim now, if it can (or says why not).

## The fields

Only `id`, `action` and (really) `thought` are needed. Everything else has a default.

| Field | What it is | Default |
| --- | --- | --- |
| `id` | Its name, lowercase with underscores. Unique. | (needed) |
| `action` | What kind of thing it does: one of the actions below. | (needed) |
| `thought` | What it thinks as it starts, as in `/drifter thoughts`: "watching the sunset". It's also used in the "while away, for fun" line, so write it as something it's doing. | "felt like" + the id |
| `params` | The action's own settings (each action's list is below). | none |
| `category` | A label for grouping: micro, social, builds, ... It changes nothing. | misc |
| `weight` | How often it comes up against other whims. 2 = twice as often. | 1 |
| `archetypes` | Who likes it more or less: `{"homebody": 2.0, "speedrunner": 0.3}`. Names: casual, newbie, builder, miner, homebody, speedrunner, nomad, hoarder, hermit, pvp_sweat, bandit, helper. | all 1 |
| `traits` | How much a trait counts: `{"curiosity": 0.5}` means curious ones like it more; a minus number means less. Names: aggression, bravery, caution, patience, tidiness, curiosity, sociability, greed, vengefulness, forgiveness, industriousness, creativity, efficiency, thrift, fidget, skill, knowledge. | none |
| `moods` | A mood that makes it likelier: `{"bored": 1.0}`. Names: calm, anxious, panicked, confident, frustrated, bored, triumphant, grieving, angry. | none |
| `stage` | How far along it must be first: 0 arrival, 1 wood, 2 stone, 3 first night, 4 settling, 5 iron, 6 settlement, 7 diamond, 8 enchanting, 9 Nether. | 0 |
| `when` | any, day, night, sunset, sunrise, rain, thunder, clear. | any |
| `needs` | What it has to carry: `[{"item": "minecraft:torch", "count": 2}]`. An item can be a tag (`"#minecraft:logs"`). | none |
| `duration` | How long it watches or stays, in ticks (20 ticks = 1 second), for the actions that use it. | 400 |
| `cooldown` | Ticks before it does this one again (24000 = one in-game day). | 24000 |
| `gates` | Letters: `G` griefing (only hostile Drifters, with griefing on), `M` only with a player or Drifter about, `R` risky (careful ones rarely), `L` later (switched off for now). | none |
| `chat` | Lines it may say in chat as it starts (half the time; the chat setting applies). | none |
| `offscreen` | What happens when no player is near or it's unloaded: see "Offscreen" below. | depends on the action |
| `comment` | A note for whoever reads the file. Ignored. | |

Two params work with every action:
- `"requires_mod": "farmersdelight"`: the whim loads only if that mod is installed.
- `"make": {"<a need's item>": "<item to craft>"}`: if it lacks something in `needs`, it may craft it quickly first.

Item, block and mob ids are written like `minecraft:oak_log`, lowercase. A wrong one gets a warning with a guess.
Items can also be a tag (`#minecraft:logs`) or one of `@jukebox_playable`, `@modded_bow`, `@modded_shield`.

## The actions

Each action and its `params`. "Offscreen" is what it does by default with nobody near (explained below).

| Action | What it does | Params | Offscreen |
| --- | --- | --- | --- |
| `watch_sky` | Looks at the sun, moon, stars, clouds or rain from a spot under the sky. | `what`: sun, moon, stars, clouds, rain. `from`: outside, or door (from its hut doorway). | show |
| `lookout` | Pillars up, looks round, digs back down. | `height` (6), `block` (an item; else any building block) | show |
| `high_point` | Walks up to the highest spot it knows nearby for the view. | `radius` (40), `rise` (8: at least this much higher) | show |
| `visit` | Goes to see a place it remembers. | `kind`: landmark or structure. `radius` (96). `labels`: only these. | show |
| `watch_mob` | Watches an animal or mob it can see. | `mobs`: ids. `range` (16). | show |
| `feed_mob` | Feeds an animal. | `food`: `{"minecraft:cow": "minecraft:wheat"}` | real |
| `hop` | Jumps on the spot or on its bed. | `times` (6), `on`: here or bed | show |
| `swim` | A swim in water nearby. | `radius` (24) | show |
| `pick_flower` | Picks a flower. | `radius` (20), `new_only` (true: a kind it hasn't got) | real |
| `torch_dark` | Puts a torch on a dark spot. | `radius` (10) | trace |
| `plant_sapling` | Plants a sapling it carries. | `radius` (12) | trace |
| `sign` | Puts up a sign with a line on it. | `lines` (`{name}`, `{group}`, `{day}` are filled in), `near`: here or player_base, `radius` | trace |
| `build` | A little build from a pattern. | `blocks`: a list of `[dx, dy, dz, "item"]`, from the ground up | trace |
| `chat` | Says something to whoever's about. | `lines` | show |
| `look_around` | A good look round. | `times` (2) | show |
| `peek_chest` | Opens one of its chests at home for a look. | (none) | show |
| `dance` | A crouch dance. | `ticks` (60) | show |
| `gift` | Gives a friend something it can spare. | `items`, `keep` (how many it keeps) | real |
| `hang_out` | Walks about with a friend. | `rounds` (6) | show |
| `visit_friend` | Drops by a friend's home. | `radius` (160) | real |
| `watch_block` | Watches a block: crops, a sapling, a fire. | `blocks`: ids or tags. `radius` (16). | show |
| `craft` | Makes something it doesn't need. | `items`: the first it can make quickly | real |
| `eat` | Eats something in particular. | `items` | real |
| `oops_drop` | Drops a stack by mistake and picks it back up. | `items` | show |
| `throw_at` | Throws snowballs or eggs at a friend. | `items`, `times` (3) | real |
| `use_block` | Right-clicks a block: a bell, a lever, a note block. | `blocks`, `item` (held), `times` (1), `gap` (10 ticks), `radius` (16) | real |
| `sit` | Sits in a parked boat or minecart. | `ticks` (200) | show |
| `steps` | Your own whim, step by step (next section). | `find`, `steps` | real |

## Your own whim, step by step (`"action": "steps"`)

When no action fits, write the whim as steps. First name what it needs to find (`find`), then list what it does in
order (`steps`). Everything goes through the Drifter's normal tasks, so it walks, reaches and uses items like a player.
If something it needs isn't about, the whim doesn't start.

```json
{
	"id": "warm_hands_by_the_fire", "action": "steps", "when": "night",
	"params": {
		"find": {"fire": {"type": "block", "ids": ["#minecraft:campfires"], "radius": 16}},
		"steps": [
			{"do": "walk_to", "target": "fire", "near": 2},
			{"do": "crouch", "ticks": 30},
			{"do": "look_at", "target": "fire", "ticks": 120},
			{"do": "say", "lines": ["ahh, that's better"], "chance": 0.3}
		]
	},
	"cooldown": 12000, "offscreen": "show",
	"thought": "warming its hands by the fire"
}
```

### Things to find (`find`)

Each gets a name you choose (`"fire"` above) and a `type`:

| Type | What it finds | Settings |
| --- | --- | --- |
| `block` | A block it has seen and can get to. | `ids` (needed): block ids or tags. `radius` (16). |
| `mob` | An animal or mob it can see. | `ids` (needed): mob ids. `radius` (16). |
| `friend` | A friend it can see: a group mate, a Drifter it trusts, a player it likes. | `radius` (20) |
| `company` | Anyone it can see: a player or another Drifter. | `radius` (16) |
| `home` | The middle of its home, if it's near. | `radius` (48) |
| `bed` | Its bed at home. | `radius` (24) |
| `chest` | Its nearest chest at home. | `radius` (16) |
| `sky` | A spot it knows under the open sky. | `radius` (12) |
| `ground` | A free bit of flat ground (to put something down). | `radius` (8) |

Add `"optional": true` if the whim can go ahead without it (steps that use it are then skipped).

### The steps (`steps`)

Each step is `{"do": "<step>", ...settings}`. Any step can have `"chance": 0.5` (it happens half the time).

| Step | What it does | Settings |
| --- | --- | --- |
| `walk_to` | Walks over to a target. | `target` (needed), `near` (2.5 blocks) |
| `look_at` | Looks at a target, or at: sun, moon, stars, clouds, rain, ahead, up, down. | `target` or `at`, `ticks` (60), `hops` (true: little hops, happy) |
| `use` | Right-clicks a block or a mob (walks close enough first). | `target` (needed), `item` (held while it clicks), `times` (1), `gap` (10 ticks) |
| `hold` | Takes an item in hand. | `item` (needed) |
| `place` | Puts a block down on a ground, sky or block target. | `item` (needed), `target` (needed) |
| `wait` | Stands still. | `ticks` (40) |
| `say` | Says one of the lines in chat (the chat setting applies). `{name}`, `{group}`, `{day}` are filled in. | `lines` (needed), `to` (a target to face first) |
| `think` | Adds a thought. | `text` (needed) |
| `jump` | Jumps. | `times` (3) |
| `swing` | Swings its arm (a wave). | `times` (1) |
| `look_around` | Looks all round. | `times` (1) |
| `eat` | Eats an item it carries. | `item` (needed) |
| `drop` | Drops an item. | `item` (needed), `count` (1), `keep` (true: picks it back up later) |
| `pick_up` | Picks up items lying nearby. | `radius` (4) |
| `crouch` | Crouches. | `ticks` (40) |

Items it holds, uses, places, eats or drops must be carried (or listed in `needs`), or the whim doesn't come up.

The examples in the mod are in `drifter_whims/steps.json`: warming its hands by a fire, waving to a friend, checking on
the crops, a morning stretch.

## Offscreen

What a whim does when no player is near (more than 128 blocks away) or while its area isn't loaded (milestone 20,
`docs/OFFSCREEN_WHIMS.md`). Set `"offscreen"` to one of:

| Value | With nobody near | While it's away (unloaded) |
| --- | --- | --- |
| `show` | Done at once: the thought, the mood and the cooldown, without walking over and standing there. Nobody's there to see it, and the time goes to real work. | Remembered, if it fits what's round its home (a cat to watch, a campfire to stare at). |
| `trace` | Done for real. | Done for real at its home if that's loaded and it carries what it takes, two at most per catch-up (a little build, a sign, a sapling, a torch). Otherwise not at all. |
| `real` | Done for real. | Not done (it would use things up, or needs someone with it). |
| `skip` | Not done at all: it waits for someone to be about. | Not done. |

What it did while away goes in one line of its thoughts (`/drifter thoughts`), never in chat: "while away, for fun:
watching the sunset twice, putting up a little monument (left by home)".

Most whims can keep their action's default. Set it when your whim is different: a steps whim that only looks at things
can be `show`; a whim meant as a prank on a player can be `skip`.
