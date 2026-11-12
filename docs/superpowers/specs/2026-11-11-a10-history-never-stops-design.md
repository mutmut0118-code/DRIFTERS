# A10 part 1: the world's history never stops: design

Drifter: Civilizations (the addon, `src/civ`). A10 / W7 of docs/LIVED_WORLD.md ("towns nobody's near live as
summaries"), the first of four parts. Everything here was decided with the user in chat (2026-11-11) unless marked
**(builder's call)**.

## A10 as a whole, and its parts

Checked in the code (2026-11-11): when nobody is near, most of the world stands still. A built town's people freeze
(they catch up one by one when someone comes back, ten days at most; nobody is born, leaves or dies for good); wars
between far towns cost nobody (losses only come from real kills: no town is ever taken or burned); traders don't set out
and tribute piles up unpaid; elections have nobody to stand or vote; comings and goings stop and are never made up. And
the summary history (W6, W7: `History`) stops for a whole nation, its unbuilt towns and its dealings with every other
power, the moment one of its towns is built; since 0.23.42 that is every nation in the first minute (a home town each).

What the user wants to notice coming back to a town after twenty days (their "your guess is right"): different faces
(someone died, a child grew up, a family moved in), a new house or a burned one, the stores fuller or emptier, maybe a
new leader or a new flag because the war went badly, and the chronicle telling how it happened.

The split and order (the user: "yes, that's the right split and order"):

1. **The world's history never stops** (this document).
2. A built town lives while you're away: births, deaths, leaving, moving in, harvests and stores, traders, tribute,
   real elections; the town made to match when you come back.
3. The town itself changes: a new house, a burned house, a town fallen to ruin (blocks of a town already seen).
4. Far towns stop costing anything: their people put away and brought back. Only if measuring shows it's needed.

Each its own version and test.

## Part 1: why

A world whose nations stop living the moment you arrive isn't the civilization the user asked for ("like i loaded into a
civilization server in the middle of their 1000 days"). The past is written; the present must go on being written,
everywhere, whether or not anyone is watching.

## Goals

- Every nation and free town goes on living by the summary wherever no player is near: rulers change, towns rise up,
  powers quarrel, make pacts, go to war and make peace, towns are taken.
- Wars between far-away towns have outcomes.
- You find out the way a person would: in towns, late, or by going there.
- It costs next to nothing.

## Not in this part

- The faces, stores, trade, tribute and elections of a built town changing (part 2). People lost on paper by a built
  town are kept as a number for part 2 to apply.
- Blocks changing: a built town burned to a ruin, houses added (part 3). Until then a built town that would have burned
  is taken instead.
- Putting a far town's people away (part 4).
- The chronicle showing only what you'd know (the RPG profile's part 3). It stays the full record.
- Anything in the whim system or its files.

## What it does

### Its reach (the user: "yes, that's the right reach")

- **A town with a player near is never decided by summary.** What happens there is what really happens: real fighters,
  real deaths. "Near" is within the distance its people are loaded and acting **(builder's call on the exact test)**.
- **A built town far from everyone can lose a war on paper:** taken by another nation. Coming back, it flies a new
  banner and answers to a new capital; the chronicle says when and how.
- **It can lose people on paper,** kept as a number in this part.
- **It can't be burned to a ruin yet.**
- **A citizen's home town gets no protection.** It can be taken while they're away; they still belong to their nation
  (0.23.42).
- Towns not built yet go on as the summary has them, as before, whether or not their nation has a built town: their
  people, rulers, risings, and (not built) they can still fall to a ruin and are then made as one.

### Its pace (the user: "yes, that's the pace i want, keep it configurable")

- **Normal (the default):** something noteworthy somewhere in the world every week or two of game days (a quarrel at a
  border, a pact, a new ruler, a town rising up); a war starting or ending about once every fifty to a hundred days.
- **A setting,** in the addon's settings screen: Quiet (the pace of the written past: about one thing in forty days),
  Normal, Lively (about twice Normal).
- Wars stay rare and matter: a town changing hands is news; even on Lively a nation is seldom wiped out.
- The written past (the thousand days before day 1) keeps its own pace: the setting is for the present.

### How you find out (the user: "yes, that's right, no exceptions")

- **No message on your screen** when something happens, not even for your own nation or home town.
- **In towns:** a nation's people talk of their own nation's news, and its towns' news lecterns carry it (as they do
  today for pacts and wars between built towns).
- **News travels:** a town hears of its own nation's affairs at once; of other powers' only if they're near it or tied
  to its nation (at war, allied, a pact). The rest reaches a player late or not at all, until they go there.
  **(builder's call: how near, how late)**
- **The chronicle records everything,** as today.
- **On arriving:** the new banner on the plaza, a different leader.

## Constraints

- One truth: the summary and the built towns' own diplomacy (`Diplomacy`, `Nations`: envoys, pacts, wars between built
  towns) must not contradict each other. Two towns can't be at war in one and at peace in the other; a town can't be in
  two nations. How the two are joined is the builder's, but every pair of powers has exactly one authority at a time,
  and handing over (a player arrives, a player leaves) loses nothing.
- Fairness (DESIGN.md section 1): no Drifter learns of far news except as a townsperson would (its nation's word). The
  summary gives no Drifter items, kills no loaded Drifter, and moves no blocks.
- Cost: a day's step for the whole world stays well under a millisecond on average and never a visible hitch;
  measured **(builder's call on the budget; say what it measured)**.
- Saved data versioned; worlds from 0.23.42 and before load and carry on (their frozen nations start living again from
  where they stopped).
- A world that isn't planned (old worlds, the setting off): its built towns' diplomacy goes on as today; wars between
  far towns get outcomes there too if that's simple, else it's left for part 2 **(builder's call)**.
- The addon reaches Drifter only through `com.dinglebob.drifter.api`. Mojang names, 1.21.1, no Loom or Gradle change.
- The pace setting goes on the addon's settings screen (memory: every new setting gets its control there).

## Left to the builder

- How the summary and the built towns' diplomacy share the powers (see "One truth").
- The numbers behind each pace, tuned by running many days headless and counting what the chronicle got.
- What "taken" does to a built town's data today (its nation, its member record, its banner to be changed by its
  leader when someone's there to see it).
- Debug commands to step days and to print the state, for tests.

## Testing

- Headless: a new planned world; `tick sprint` through a few hundred game days at each pace with nobody near; the
  chronicle's count of each kind of thing against the pace above; a war that ends with a built town taken; nothing
  contradicting between the summary and the towns (a debug check command); the step's cost. Then a restart: it carries
  on. A world saved by 0.23.42: its nations start living again.
- Dev client: stand in a town through a war its nation is in: nothing is decided on paper there; walk away and back
  after it was taken: the new nation, the news in its people's talk and on its lectern.
- TESTING.md: plain steps for the user (with the debug command that skips days).

## Done

Built, tested headless and in the dev client, both jars installed in DRIFTER TEST with a new version number,
PROGRESS.md, TESTING.md, LIVED_WORLD.md (A10 status and progress) and API.md (if it grew) updated, committed and pushed.

## Settled after the builder's check (builder's calls unless marked)

Facts from the code:

- Built towns far away already run their diplomacy every 200 ticks, but with their leaders not loaded every trait reads
  as the middle (0.5): a far war never starts, and a war already on never costs anyone (losses only come from real
  deaths). Pacts, peace and independence do still happen.
- Each morning the chronicle's entries of the day before are sent to every player's chat (on by default), and a town's
  gazette prints every entry of the last three days under "From afar" with no rule for distance.
- Built towns never write back to the plan: a town that joined or left a nation in play is stale in the plan.

How the one truth is kept:

- **One record, written through.** The summary's ties between two planned powers (standing, peace, alliance, war) are
  the record, always; the relations between their built towns mirror it. The rule only decides who rolls the dice for a
  pair of powers: the summary, unless a built town of either is watched; then the towns' own diplomacy. "Watched": a
  player within the distance its people are loaded, or one of its people loaded. Whatever either decides is written to
  both at once (a war begun, peace made, a pact), so a pair changing hands mid-war loses nothing.
- Pairs inside one nation (a rebellion), towns Drifters founded and nations formed in play stay the towns' own
  diplomacy's, as today; nations formed in play are entered in the plan so the summary knows them.
- **"Near" is per town.** A player in one town of a nation doesn't shield its other towns: those can still be lost on
  paper. A watched town is never where a paper battle is fought.
- **A built town's ruler doesn't change on paper** in this part (its leader is a real Drifter: part 2). "Rulers change"
  is for towns not built.
- **A nation with built towns can be conquered whole,** at lower odds than in the written past (seldom, as the user
  asked); its citizens are then "once of" it (0.23.42).
- **Taken, for a built town:** it leaves its nation and joins the winner's as a conquered member (its banner and its
  people's colours to be changed by its people when someone's there), at peace and allied with its new fellows, in its
  new nation's wars. A winner that's a free town not built: only peace is made.
- **People lost on paper** by a built town: a number saved on the town, for part 2 to turn into faces.
- **The pace setting scales the paper only** in this part. What built towns' own diplomacy does in front of a player
  keeps its own speed (shorter wars, as today).
- **The morning chat summary** (the user decides: see the question in chat): far-away news is left out of it; only
  what happened where a player could know of it stays.
- A world saved by 0.23.42 or before: on first load the ties are filled in from the built towns' relations and the
  plan's nations from the towns' real ones, once (a plan version number).

## Settled while building (0.23.43)

- **News reach is narrower than written above:** a town hears of its own power, powers at war or allied with it (not a
  plain pact), and powers seated within 1,200 blocks (not 2,600). With the wider reach every town in a five-nation world
  heard everything, which defeats "the rest reaches a player late or not at all".
- **Pace as measured:** Normal is about one piece of news every 13 days and a war starting or ending every 30 to 60
  days (livelier than "fifty to a hundred"); Quiet about one every 33 days; Lively is told apart mostly by more quarrels
  and battles. All three are numbers in `WorldPlan.PACE` and `History`.
- **A free town not built, beaten by a nation in the present, is taken by it** (in the written past only peace was
  made): otherwise no town ever changed hands at these war lengths.
- **Who decides a pair:** a player within a made town's land + 192 blocks of either power's towns (spectators don't
  count). Read back: a war on is over once any two of their towns have made peace; no war becomes war once any two are
  at war; the first read of an older world goes by what most of their towns have.
- **A made town's people on paper aren't worn down by its losses** (they'd never grow back in this part): the losses
  are only counted (`paperLost`).
