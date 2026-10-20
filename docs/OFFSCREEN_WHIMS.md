# Whims offscreen: a proposal (milestone 20, part 1)

The user (2026-10-19): "think: what are some ways we can simulate/catch up with these whims in a offscreen sense?"

This is a proposal to pick from. Nothing here is built yet.

## Where whims stand today

- **Loaded and near a player (Full tier):** whims run for real. You see the dance, the dirt tower, the snowball fight.
- **Loaded but nobody near (Reduced and Far tiers):** whims still run for real, at full cost, though nobody sees them.
  With hundreds of Drifters (milestone 16) that's time spent on dances in empty fields.
- **Not loaded (frozen):** nothing happens. When the area loads again, catch-up (`world/CatchUp`) fast-forwards work:
  gathering, eating, crafting, smelting, the hut shell. It does **no whims at all**. A Drifter that "lived" three days
  offscreen comes back having done only chores.

So the gap is: a player comes back to a base and finds no sign that its Drifter had a life while away (no little
monument, no flowers, no new sign, no friendships grown), and the thought log only lists work.

## The options

### A. Memory only (cheapest)
During catch-up, for each quarter-day slice, roll some leisure time from its personality, pick whims with the same
weights and gates as live (archetype, traits, mood, stage, the slice's time of day and weather, cooldowns), and record
them: a thought each ("watched the sunset", "had a snowball fight with Ann"), the mood relief they give (boredom
down), cooldowns set, and for the social ones a little trust with the friend involved.
- Good: cheap, safe, nothing in the world changes, nothing can go wrong in a chunk nobody's looking at.
- Bad: no traces. The log says it built a dirt tower, but there's no tower.

### B. Memory plus traces at home (most "lived-in")
Option A, plus: whims that leave something behind leave it, but only at its own home and only while home is loaded
(the same rules catch-up already uses for the hut shell): a little build (the dirt tower, the monument) on free ground
by home, a sign with the day on it, a sapling or flowers planted, a torch in a dark corner, a crafted keepsake (a
painting, a lantern) made from what it has with the real recipe, a gift to a group mate. Every placement goes through
the same checks a live whim uses (nothing floating, nothing over a player's block, its own blocks only), and there's a
cap (say 2 traces per catch-up) so a week away doesn't bury the base in junk.
- Good: you come back and see what it got up to. Fits the "lived-in world" idea from milestone 12.
- Bad: more code per kind of whim; a trace in the wrong place is permanent (mitigated by the cap and by keeping traces
  to its own base).

### C. Skip what nobody can see (a performance win for loaded Drifters)
For loaded Drifters nobody is near (Far tier), whims that are only for show (dancing, spinning round, watching the
sky or a mob, looking round, hopping, chatting with nobody) are settled at once: the thought, the mood relief and the
cooldown, but no walking over and standing there. Whims that change something (build, plant, craft, gift, sign) still
run for real. When a player comes close it's Full tier again and everything plays out as now.
- Good: hundreds of unwatched Drifters stop spending time on shows nobody sees; the time goes to real work.
- Bad: an unwatched Drifter "watches the sunset" in one tick; if you're spying from far away with a spyglass, you'd
  miss it (Far is 128+ blocks, so rarely).

### D. A leisure budget (statistical)
Instead of picking whims one by one offscreen, give each Drifter a leisure share of its day by personality (a
Speedrunner 5%, a Homebody 20%) and convert that share into effects: boredom relief, a few thoughts, a chance of one
trace. Simpler than A and B but blurrier: no specific whim, no specific story.

### E. A "while you were away" moment
Whatever is simulated, when a player first meets the Drifter after it was away, it says so once: a chat line from the
whims it did ("built a little tower while you were gone, it's by the hut"), or a gift of something it made. Pairs with
A or B.

## Recommendation

**A + B + C + E, with B kept small.** In order:
1. **C first**: it's a pure win at scale and easy (a flag per whim action: "show" or "does something").
2. **A**: every whim gets a memory and its mood effect during catch-up, picked the way live whims are.
3. **B for a short list of trace whims** (little builds, signs, saplings and flowers, torches, keepsakes), at its own
   loaded home only, capped at 2 per catch-up.
4. **E**: one line or a small gift when you first meet it after a long absence.

Each whim in the data files would get an optional `"offscreen"` field (`"trace"`, `"memory"` or `"skip"`), with a
sensible default per action type, so datapacks (and later modded whims) decide for themselves.

## Questions for the user

1. Is B (real traces while you're away) what you want, or would you rather only see a log of it (A)?
2. Should the "while you were away" line (E) be chat, or only in `/drifter thoughts`?
3. C changes what an unwatched Drifter does from far away: fine?
