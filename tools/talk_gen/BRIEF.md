# Writing Drifter-to-Drifter topics in bulk (tools/talk_gen)

Drifter is a Minecraft 1.21.1 mod: Drifters are player-like mobs with player usernames who live in the world (they mine,
build houses, live in towns, fight mobs, trade). Two Drifters near each other chat in the game's chat, as players do. A
chat between them is a string of **topics**: each topic is a few turns, `a` (the one who brought it up) and `b` taking
turns, every turn a random pick from its lines. After a topic, the next one is picked from topics whose `tags` match this
topic's `leads` (or its `next`), so talks wander naturally from subject to subject. Strangers talk one or two topics,
friends 3 to 6, best friends up to 10.

Each generator here is a Python script that writes one JSON file into
`src/main/resources/data/drifter/drifter_talk/` (any `*.json` there is loaded). Look at `tools/talk_gen/plans.py`,
`tools/talk_gen/lore.py` and `tools/talk_gen/home_life.py` for working examples, and at the hand-written files
`src/main/resources/data/drifter/drifter_talk/topics_*.json` for the tone (read a few: `topics_stories.json`,
`topics_debates.json`, `topics_banter.json`, `topics_moods.json`, `topics_voices.json`).

## How to run and check

```
python tools/talk_gen/<yours>.py src/main/resources/data/drifter/drifter_talk/topics_gen_<yours>.json
python tools/check_talk_web.py . --quiet
```

The checker must print no `ERROR:` line for your topics (warnings are fine; repeated short reactions are fine). Other
generators may be being written at the same time: ignore errors in topics that aren't yours (other id prefixes).

## The file

```json
{"_comment": "what this file is", "topics": [ { ...topic... }, ... ]}
```

A topic:

| field | what |
| --- | --- |
| `id` | unique, lowercase, `[a-z0-9_]`, starting with your prefix |
| `weight` | how often it's picked among the topics that fit (1.0 normal; 0.4 to 0.8 for generic filler, 1.2 to 2.5 for ones with situational `needs`) |
| `needs` | conditions for it to come up at all (see Conditions) |
| `tags` | what it's about (1 to 4). Use existing tags when they fit (list below); a new tag must be on 2+ topics |
| `leads` | tags the talk may go on to after it (2 to 4). Every lead must be a tag some topic has (an existing tag, or one of yours) |
| `about` | a few words for the subject ("the dragon fight") |
| `turns` | the turns, in order |
| `effects` | optional, what it does to them at its end: `trust` (+2 both), `rivalry` (-2 both); see Effects |
| `next` | optional, ids of topics that continue this one (story part 2) |
| `follow_up` | true on a continuation (part 2+): it only comes up through a `next` |
| `chance` | — (that's on turns) |

A turn: `{"by": "a" or "b", "lines": [...], "chance": 0.0-1.0 (optional, default 1), "if": [conditions] (optional), "mark": ["agree"] (optional)}`.
`lines` is a list (everyone's) or voices `{"any": [...], "newbie": [...], "pvp_sweat": [...]}`: a Drifter of that kind
uses its own voice's lines most of the time. Voices: casual, newbie, builder, miner, homebody, speedrunner, nomad, hoarder,
hermit, pvp_sweat, bandit, helper.

A line can carry the answers it expects: `{"line": "what's the best food?", "then": ["steak", "golden carrots"]}`: the
other one's next turn then says one of those instead of its own lines (use this for questions, so the answer fits). Don't
put questions and plain statements in the same turn's lines when the next turn answers (a "lol true" after a question
reads wrong).

Marks: a turn's `mark` adds `t:<mark>` to the world for the rest of the topic, so later turns can branch with
`"if": ["t:agree"]` / `"if": ["!t:agree"]`. The usual pattern (topics_debates.json): b agrees (chance 0.5, mark agree),
else b disagrees (`if: ["!t:agree"]`, mark disagree), then a answers each way. At a topic's end `agree` gives +1 trust
both ways, `disagree` -1.5 (so arguments matter). `plan_yes`/`decline` are for plans (don't use).

Story parts: part 1 has `"next": ["<id>_2"]`; part 2 has `"follow_up": true`, tag `story_part` plus its subject tags,
and maybe `"next": ["<id>_3"]`, and so on. A continuation comes up right after (most of the time).

Fill words: `{a}` / `{b}` are their names (rarely needed: the game already adds names); `{a.<word>}` / `{b.<word>}` are
facts about one of them, and a line whose word that Drifter doesn't have is just skipped (so always give lines without
fill words too). Words the game fills: ally armor base built capital close_call crook day dead death diamonds
disliked_player doing enemy group hate held insult kills leader like liked_player mob mob_count nation news_what news_who
ore ore_count other place plan rival town traded_with. Don't invent others (the checker errors).

## Conditions (`needs` and `if`)

`a:<flag>` (a has it), `b:<flag>`, a bare `<flag>` (the world, or a), `!` in front for not. Flags the game sets (all
in `src/main/java/com/dinglebob/drifter/social/talk/Facts.java` and `Conversations.planFacts`; the checker reads them):

- about one: acquainted armor_good built_lately close_call died_lately found_ore found_rare has_diamonds has_house has_hut
  no_home hungry hurt in_town in_group killed_mobs killed_many likes_a_player dislikes_a_player
  has_news has_joke traded_lately outside, `mood_<calm|anxious|panicked|confident|frustrated|bored|triumphant|grieving|angry>`,
  `voice_<kind>` (casual, newbie, builder, miner, ...), can_mine can_hunt spare_food home_near sociable loner brave timid
  keeps_shop been_to_theirs, `knows_<area>` / `new_to_<area>` (areas: trading enchanting nether brewing raids redstone
  anvils fortresses bartering end trial_chambers netherite warden dragon elytra beacons)
- between them / the world: strangers friends rivals acquainted groupmates rain thunder clear time_morning time_day
  time_evening time_night, `t:<mark>`

Topics tagged nether, end, enchanting, potions or redstone are only brought up by one who knows that part of the game
(the game adds `a:knows_<area>` itself), so a newbie never starts them.

## Effects

Only these: `trust`, `rivalry`, `teach_<area>` (a tells b about it), `learn_<area>` (b tells a). Leave out effects on
most topics.

## Existing tags

feelings building argument jokes danger mobs home friends town complaints exploring plans mining brag decor memories
animals story_part stories gear night day fighting random caves tools storage trade philosophy biomes structures nether
crafting weather food villagers places end ocean redstone enchanting death ore advice gossip help plants weapons pets
dreams trees farming sleep players potions armor sky health diamonds boredom music doing fishing greeting rivals politics
opinion trust

## Style

- Lowercase internet chat, as Minecraft players type: short lines (mostly 10 to 60 characters, never over 90), plain
  ASCII, no emojis. Slang and abbreviations where natural (lol, ngl, fr, tbh, idk, rn, bro, nah, w, L).
- Each kind of player sounds like itself: a newbie is excited and unsure, a speedrunner terse and min-maxing, a hermit
  curt, a builder fussy about looks, a pvp_sweat cocky, a helper warm, a hoarder possessive, a nomad restless.
- They live in this world: their houses, towns, mines, deaths and respawns, mobs, villagers, the weather. They never talk
  about the real world (no real places, people, brands, politics or religion), and nothing about being in a mod.
- Keep it friendly-PG-13: mild words (dang, heck, trash, noob, bozo) are fine; no slurs, no sexual content, no cruelty about
  real groups. Insults are game insults ("you built a dirt hut and called it a house").
- Every topic must make sense line after line, whatever random pick each turn makes. Read your output back.
- Substance lines should be specific to the topic (don't reuse the same sentence across hundreds of topics); generic
  reactions ("lol", "true", "fair") may repeat.
- Aim for 4 to 8 turns per topic and 3 to 8 lines per turn; about 20 to 35 lines per topic in all (the game holds all
  10,000 topics in memory, so don't go far past that).
