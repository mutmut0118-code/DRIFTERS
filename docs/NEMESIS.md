# The Nemesis milestone (milestone 24): grudges, manhunts, outlaws and rivalries

The plan for milestone 24, kept like docs/LIVED_WORLD.md: what's decided, how each part works, and the order. Written
2026-11-14, from a talk with the user that started as a comparison of Drifter and Drifter: Civilizations against the
Nemesis System of Shadow of Mordor and Shadow of War (Monolith, 2014 and 2017).

**When:** after the user finishes the work already under way (the user, 2026-11-14: "after i finish some other stuff
that needs to be done this will start"). It is its own milestone, so its versions are **0.24.x**, starting at 0.24.0
(CLAUDE.md "Version numbers"). Until then nothing here is built; milestone 23 work carries on as before.

---

## Status

| Part | What | Status |
| --- | --- | --- |
| N1 | The grudge loop: deaths found out, real avengers, grudges that burn out, four ways to settle | Planned |
| N2 | On the run: manhunts that follow sightings until the trail goes cold | Planned |
| N3 | Outlaws: illagers and witches replaced by half-mob Drifters; delvers become outlaw looters; the outlaw who becomes known | Planned |
| N4 | Rivalries you walk into, born from the towns' and nations' history | Planned |
| N5 | The encounters revamp: take out, make real, keep | Planned |
| N6 | Renown, callouts and a Rivals page in the journal | Planned |
| N7 | Later: fighting on horseback (mounted outlaws and hunters) | Planned, last |

---

## 1. Why: the user's words

The comparison found that Drifter has most of the Nemesis System's parts (named individuals, memory of the player,
one event changing other people, offscreen power struggles, followers) and goes past it in places (towns, nations,
friendships, a world that keeps going), but not its heart: an enemy who rises because of you, keeps coming back, and
makes you part of a story. The user (2026-11-14) described the story they want:

> "i kill a drifter, could be a random one at a house alone. drifters find out they died. a day later i either get
> hunted by bounty hunters, or the drifters friends. and its a grudge that keeps coming back, i become a villain in
> someones story"

> "would also work both ways if like i witness a rival story happening in front of my eyes. i can choose to watch, or
> intervene, whoever i side with makes me be rivals with theirs and i join a story the midst of it happening"

> "think of many C subplots happening in a B plot (the history and story between nations and towns) happening in a A
> plot (the player progressing in a singleplayer world)"

Then, on how a grudge should end: "it should burn out unless i keep killing them, or it can be settled. maybe some
convincing on why they shouldnt kill me". On escaping: "it can be like an on the run type of thing where it turns into
a manhunt, until they cant find me anymore". On the encounters: "we are also gonna have to revamp, take out some
drifter encounters that dont make sense like warbands". On illagers: "i also want to replace completely replace
illagers like we did villagers so they can also be drifters. they can have the bandit archetype but they are much more
temporary than nations and citizens. they are ... more of a mob than a drifter". And: "if i place a pillager, witch, or
vindicator egg (etc) a drifter of that archetype spawns, not completely falling into that archetype, but being half
mob and half drifter, along with the replacement idea". And: "keep account on people who will play this with the perm
death option".

### The three layers

- **A plot:** the player, progressing through a single-player world (Drifter's survival world).
- **B plot:** the history between nations and towns (the addon: towns, leaders, wars, nations, the 1,000-day past,
  A10's world that keeps going).
- **C subplots:** many personal stories inside B: a grudge against the player, two neighbours at odds, a family split by
  a war, an outlaw who beat the player once. **This milestone builds the C layer**, and joins it both ways: B makes C
  (a war leaves grudges across the border) and C reaches B (killing a town's guard, siding with a plotter).

### What success looks like

- Killing a Drifter, even a loner nobody saw, can come back to you a day later as people with names and reasons.
- A grudge comes back more than once, and you can end it: by not killing again (it burns out), or by settling it.
- Being hunted feels like being on the run: they follow word of you; you can get away.
- Walking around the world, you come across stories already going, and can step into them.
- An outlaw who kills you, or gets away from you, becomes someone you know by name.
- All of it under every respawn setting, and especially with permanent death (section 4).

### Anti-goals (a failure even if it works)

- **No rigged odds.** Nemesis tilts who survives toward orcs you know (its "Player Interaction Score"). Drifter can't:
  DESIGN.md section 1. Nobody survives, finds you or wins because the story needs it. What's tuned is timing and
  who is told what, never outcomes.
- **No knowledge from nothing.** Avengers know who did it only if someone saw it, was told, or reads a clue. Hunters
  know where you are only from sightings. A loner killed unseen can stay a secret.
- **No snowball that never ends.** One kill must not turn a nation against you for good. Grudges weaken as they pass on,
  stop after two steps, and burn out.
- **No cinematics or menus.** No unskippable intros, no markers, no ranked board of enemies with power levels. Lines
  in chat, the way players type; the journal in words, never numbers (the user's rule for the journal).
- **Not corny.** Nobody explains the rules ("I only have one life", "my grudge will fade"). Show it through behaviour
  and short in-the-moment lines (the user, 2026-10-28, on permanent death).
- **No magic, no mob powers.** Half-mob Drifters still only do what a player can: no evoker fangs, no vexes, no riding
  ravagers.
- **Vanilla art only.** No scar textures or new models (the user's rule for the RPG profile); history shows through
  gear and words.

---

## 2. What's there to build on (checked in the code, 2026-11-14)

Drifter (`src/main`):
- **Relationship records** (`social/Relationship`, `social/Relationships`): per player, trust, fear, familiarity, a list
  of grudges (event, severity, since, forever), debts, strength estimate, known base, last seen (time and place), a
  "wronged" flag, compliments and insults. Saved with the Drifter and kept through respawn. KILLED is -80 trust, +40
  fear, grudge 80. Forever grudges: the Grudge-holder disposition, vengeful (0.75+) with a grudge of 60+, or nearly killed
  with high stakes.
- **News** (`social/NewsBook`): up to 32 items each, passed on when two Drifters meet who don't dislike each other,
  believed by trust, weaker each telling; a death it saw, and who did it. A friend's death for good is a grudge for life
  (0.22.32).
- **Group reputation** (`Relationships.tellGroup`): wronging one member costs half that trust with the rest.
- **Respawn modes** (`world/Respawns`): bed, lives (default 5), permanent. **Stakes** (`brain/Stakes`): warier the
  less it has left; giving in at 4 hearts ("ok ok take it", throws its best 3 stacks) when wary, not brave, not out to
  kill (0.22.32).
- **Revenge squad** (`encounter/RevengeSquad`): 1 in 4 kills, 2 strangers made for it (3 for a murder), look 3 days,
  find within 160 blocks, 10 minutes. **Bounty hunters** (`encounter/BountyHunters`, `Scout`): a grudge of 40+ or 3
  kills; 3 to 5 strangers, a day to two and a half later, go to the newest sighting of the player (a day old at most) or
  their base, 10 minutes. **Murder**: killing one who gave in.
- **Encounter Drifters** (`encounter/EncounterSpawner`): made for the part, not saved, not in `/drifter list`, walk in
  from out of sight.
- **When a Drifter kills a player** (`social/HostileGoals`): "gg" and it picks up the drops. Nothing else: the diary
  skips player victims (`social/talk/Diary`), the triumphant mood and practice come only from monster kills.
- **Talk about the past** (`social/talk/PlayerConvo.grudge`, `drifter_chat/convo.json` `convo.grudge.*`,
  `heard.json` `memory.*`): "last time you came at me swinging", "you killed my friend", "you owe me".
- **Bandits**: an archetype; kill anyone outside their group on sight (`social/BanditGoals`); half of pillager outposts
  held by a gang of 3 full Bandits (`world/gen/LivedInStructures`).
- **Delvers** (`world/gen/Delvers`): made near a player at an unlooted chest of a mineshaft (16 to 48 blocks off, out of
  sight) or at a ruin (`drifter:looter_ruins`: half the ruins' looters are delvers, half townsfolk out scavenging), never
  saved, gone 30 s after no player is within 96 blocks; every one a Bandit (kills on sight); iron to diamond kit, some
  enchanted, a favourite weapon, bow, shield; `DelveActivity` loots every chest it sees. Setting "Mineshaft delvers".
- **Horses**: tamed and ridden for trips (`skills/HorseGoal`, `RideTask`); never in a fight.
- **Group feuds and wars** (`world/DrifterWars`); two Drifters near each other can become rivals (DESIGN.md 21).

Drifter: Civilizations (`src/civ`):
- **Justice**: witnesses, fines, exile, wanted, murder, massacre, a leader's murder turns the nation on you.
- **Town reputation** (`TownReputation`), welcome to wanted, spreading town to town. **Blood goods** (`BloodGoods`): a
  dead townsperson's armour is recognised on you.
- **Friends** (`Friends`, `FriendPerks`, `FriendGoals`): stranger to best friend, perks, a warning when its town's after
  you, endings.
- **Politics and unrest**: elections with candidates, coups, revolts, a ruthless plotter's attempt on the leader.
- **Diplomacy, wars, nations; History and A10** (the world goes on while you're away; `TownAway`, `AwayDeaths`).
- **Quests** (`Quests`): "deal with someone" (a named Bandit, sanctioned so it's no crime). **Hired swords** (`Hires`,
  0.23.64). **Words** (letters, 0.23.64). **Whereabouts**: asking where someone is.
- **Graves** (`TownGraves`) for the gone-for-good. **Town raids** (`TownRaids`, A7): Bad Omen near a town brings waves of
  vanilla pillagers and vindicators, a ravager in a city's last wave.
- **The journal** (`Journal`, J): People (friends by stage, "Holds a grudge", "Gone for good"), Towns, Law, Quests,
  Chronicle (`Annals`: story pages, "a life").

---

## 3. Hard constraints (every part)

- DESIGN.md sections 1 and 2 as always: perception only, player time and reach, no free items, no impossible placement.
  Half-mob Drifters too (they're still Drifters).
- Money and rewards are real: blood money, a bounty's price and a poster's reward come out of someone's real pockets,
  chests or a town's till. A starting kit is the one source of things "brought from elsewhere".
- **Drifter alone keeps working.** Everything that needs towns (a town hunting you, blood money to a till, rivalries
  from wars, replacing illagers and witches across the world) is in the addon. Without it, Drifter falls back to what it
  has: friends and groups for avengers, strangers for chance encounters.
- The addon reaches Drifter only through the API (docs/API.md); grow it in the same change.
- Tunable numbers in data files or named constants. Every number in this document is a starting point.
- Saved data versioned (DESIGN.md 20). Old worlds load: old grudges, squads and posters carry on or end quietly.
- Every new setting goes on the config screens (Drifter's or the addon's), with a plain description.
- Performance measured each part (the real-play scale: about 200 loaded Drifters at most, about 1,000 in the world).

---

## 4. Permanent death, and the other respawn settings

The user asked for this to be kept in mind throughout. Nemesis leans on the enemy coming back ("cheating death"). With
permanent death, killing a Drifter ends its story. So each setting tells the story differently:

| | Bed respawn | Lives (default 5) | Permanent death |
| --- | --- | --- | --- |
| Killing a Drifter | It's back in seconds and remembers: the first avenger is often the victim itself | Each death costs it; its last life is the tensest and warier | It's gone. The story passes to its friends |
| How a death is found out | The victim tells (it saw who did it) | The victim tells, until its last life | Only witnesses, or friends who come looking (N1) |
| Grudges | Normal | Normal; stronger as lives run low | Longer, and the closest friend's for life (already true since 0.22.32) |
| The "one that got away" | Less special (it comes back anyway) | Matters more each life | **The** Nemesis moment: a Drifter that escapes you on its last legs |
| Sparing someone | Small | Grows as lives run out | Large: you gave back the only life it had; a life debt |
| Blood money | Cheap | Normal | Dearer |
| Hunters | Bold | By lives left | Wary: they wait for an opening, so hunts last longer and kill less often |
| Rivalries between Drifters | Fights don't end them | A loss costs a life | Fights to the death end them; heirs take them up |
| Outlaws (N3) | Always one life, whatever the setting | | |

How: every part reads `Stakes` (0.1 bed, 0.3 all lives left, up to 1.0 when the next death is the last) rather than the
setting's name, so mixed cases (the last of five lives) behave like permanent death.

**Hardcore players.** If the player plays a hardcore world, their own death ends it, so stories where a Drifter kills
the player don't carry on for that player. Everything else works the same.

---

## 5. Part N1: the grudge loop

The heart of the user's story: kill someone, word gets out, people with names come for you, and it keeps coming back
until it burns out or is settled.

### N1.1 A death gets found out (fairly)

Today only Drifters that see a death know of it. Add the ways a player would find out:

1. **The victim tells** (bed and lives): it respawns remembering who killed it (if it saw them). It already holds the
   KILLED grudge; now it also tells its friends as news at once ("muto killed me at my house"), first-hand.
2. **A witness tells** (built): news of the death, with the killer if seen.
3. **A friend comes looking** (new; matters most with permanent death): a friend who expects to see the dead one
   (group mate, town neighbour, a friend whose home is within 128 blocks) and hasn't for **1 game day** goes to check on
   it: its house, its usual places. There it finds what's there to find: its things on the ground (5 minutes only), its
   grave (the addon puts one up for the gone-for-good), an empty house, a dead pet. That proves it died, not who did it.
   Offscreen (the friend not loaded), the check is decided on paper the next day by the same rule.
4. **Who did it, when nobody saw:** the friend works it out only from clues it actually has:
   - someone saw the player near that house that day (any Drifter's sighting of the player, asked around as news);
   - the player is seen wearing or holding the dead one's things (Drifter's theft rule, "taken for the thief, right or
     wrong", grown to deaths; the addon's blood goods);
   - the player is the one who had a grudge with it, or it had a grudge with the player.
   With a clue it's **suspicion**, not certainty: it can be wrong (another player, a Bandit), and it matters when
   settling (N1.5: denying works on suspicion, not on a witness).
   With no clue, the death is a mystery: friends grieve, nobody comes. A careful killer can stay unknown.

Every dead one's dropped gear (armour, weapons, tools: things that don't stack) is marked as theirs when it falls (the
item's custom data: whose, when), so it can be recognised later and given back (N1.5). The mark is invisible on the
item. Stackable things aren't marked, so stacks still merge.

### N1.2 Who comes for you

Replaces the revenge squad's strangers and its flat 1 in 4.

- **Avengers are real.** Once a death and its killer are known to them, the dead one's friends weigh going after the
  killer. Each one's will to go: closeness to the dead (friend, best friend, group mate), Vengefulness, Bravery, its
  grudge, less its Stakes (a careful one on its last life may stay home and grieve), less how much it fears the player
  (their known deeds and gear). Those over the bar go, **one to three** of them, led by the angriest; with bed or lives
  the respawned victim usually leads.
- **When:** half a day to a day and a half after they know (the user: "a day later"). They gear up from their own
  things first (real gear: a friend who's been mining diamonds comes in diamond), eat, and set off.
- **How they travel:** for real when loaded; offscreen by Drifter's abstract travel (DESIGN.md 22), arriving on loaded
  land from out of sight as encounter Drifters do now.
- **Where they look:** where word has it the player is (N2: sightings), else the player's known base, else where the
  friend died. Finding the player starts the parley (N1.5), then the fight.
- **No friends willing, or none at all** (a loner): someone else may pay to have it done. A Drifter with a grudge of 50+
  against the player (the dead one's group, a neighbour) or, with the addon, the dead one's town (its Justice, its till)
  **hires hunters** with real money: the bounty hunters (N5), led by the one who paid when it's brave enough, else
  hired blades on their own. The price comes out of the payer's real money; can't pay, no hunters.
- **Who the hired hunters are** (my call, after the builder check): hired blades of the outlaw kind (N3's light mode),
  mercenaries with no town and no friends. They're **kept for the length of the hunt** (saved, so a hunt survives an
  unload or a restart, and they travel offscreen like avengers), then gone when it ends. The price they were paid is
  **carried by their leader** in its pack: kill the hunters and the money's yours; it never vanishes. Nobody mourns a
  hired blade, so **killing one starts no new grudge** (the chain grows through friends and towns, not mercenaries).
  With the addon, a town that wants the player can instead send its own **guards** (real townspeople, N1 rules: killing
  them is a death in the town, and the story grows). Without the addon there are no towns, so hunters are always
  hired blades (they work in Drifter alone: the light mode lives in Drifter, see N3).
- **The victim's town** (addon): a murder in town is already Justice's (wanted, exile, bounties). The town's avengers
  and the town's bounty are one hunt, not two.
- **One hunting party per player at a time.** Others wait their turn (at most 3 waiting, the strongest grudge first),
  so it never piles up into a siege.

### N1.3 It keeps coming back

- An avenger that fails and lives (it fled, gave in, lost you) goes home, cools for **2 to 4 days**, and comes again if
  its grudge is still over the bar (N1.4). Its friends may join the second time.
- Each return it comes better prepared: what it learned of you (your gear seen, where you live), more friends, better
  gear if it got some. Nothing it didn't really gather.
- Killing one of them is a new death (N1.1) for *their* friends: that's how a story grows. It's also how it ends,
  under permanent death: run out of avengers, or they run out of will.

### N1.4 Grudges that burn out

The user: "it should burn out unless i keep killing them". Today some grudges never fade at all. New rules for grudges
over a death (theft and insult grudges keep today's rules):

| Who | Grudge | Notes |
| --- | --- | --- |
| The victim itself (bed, lives) | 80 | first-hand |
| Its closest friend | 70 (+15 with permanent death or its last life) | |
| Its other friends, group mates | 50 | |
| Friends of those friends | 20 | never enough to hunt; just dislike |
| Anyone further | nothing | the chain stops at two steps |

- **The hunting bar is 50.** Under it they dislike you (prices, talk, no help) but don't come.
- **It fades every day**: about 4, more for the forgiving, less for the vengeful (from Forgiveness and Vengefulness;
  never under 1). With permanent death, six tenths as fast.
- **Each failed hunt takes 15 off** (10 with permanent death): the trail went cold, they lost you, they ran.
- **A grudge "for life"** (today's forever rule) no longer means hunting forever: it means it never falls under **30**.
  Dislike stays; the hunting burns out like any other.
- **Killing again tops it up**: a new death in their circle is a new grudge (as the table), on top of what's left.
- **Getting your revenge eases it**: an avenger that kills the player (bed, lives: the player respawns) takes 40 off
  its grudge ("that was for bob"). Two such and it's usually done.

### N1.5 Settling it (four ways)

The user picked all four ("the settling ideas sound good").

**1. Talk them down (the parley).** When avengers find the player and the player isn't attacking them, they stop at
about 8 to 10 blocks, weapons out, and say why they've come ("you killed bob", "bob says you jumped him"). For about
**12 seconds** what the player says in chat counts (through Drifter's player talk: statements are already read;
`social/talk/Telling`). What's understood (in many wordings, player speak, like the rest of player talk):

| The player says | Works when | Effect |
| --- | --- | --- |
| "he attacked me first" / "self defense" | It's true and they know it (a witness, the victim's own word, news that says who started) | Mostly settled (chance by their Forgiveness; the closest friend may still want a fight) |
| the same | It's true but they can't know | Doubt: a chance by trust and Forgiveness; on a yes they go home, grudge halved |
| the same | It's false and they know it | Worse: grudge +10, the parley's over, they attack |
| "he was robbing me" / "he was a bandit" | The dead was a Bandit, a thief, wanted | Usually settled ("he had it coming", as the encounters already judge) |
| "wasn't me" | They only suspect (N1.1 clues) | A fair chance they're not sure enough: they go, grudge halved, watching you |
| the same | They know (witness, victim) | Worse, as a lie |
| "sorry" / "i didnt mean it" | Always heard | Works only on the forgiving or the fond (trust before); lowers the grudge a little either way |
| an offer ("ill pay", "take this") | | Starts blood money (3) |

Saying nothing for the 12 seconds (or walking off) is taken as no answer: the fight starts, as today. Truth is checked
against what they know, never against the world. How easily they're swayed: Forgiveness, Sociability,
their trust in the player before, how much they fear the player (a feared player gets more "fine, but stay away"),
the setting (with permanent death the grief is heavier: harder to talk down, but fear of dying makes an offer easier
to take). Murder (killing one who gave in) can still be talked about, but only an offer or giving back has much chance.
Attacking during the parley ends it: no warning owed after.

**2. Spare them.** Avengers can give in now (today they're relentless): the same rule as 0.22.32 (4 hearts, wary, not
brave, losing), so the wary and those on their last life give in more. A Drifter that gave in and isn't hit again
for 5 seconds while it backs off is **spared**: its grudge is settled, and it owes the player its life (a debt, kept
for good). It tells its friends ("muto let me go"), which cools theirs too (a third). Killing it after it gave in is
murder (built). With permanent death a spared life is worth the most: the debt is large and the story told for longer.

**3. Pay (blood money).** Offered in the parley ("ill pay"), or to the one who posted a price, or (addon) to the dead
one's town's till. The asking price: about **8 emeralds' worth** (ItemValues), twice with permanent death, half again
for a murder, more the closer the dead was to them; coins with Numismatic Overhaul. The player throws it as with any
Drifter trade. Paid, the grudge is settled; for a bounty, the price is called off (this is the "buying off a bounty"
already on the roadmap: if that step is built before milestone 24, N1 grows it rather than adding a second one).

**4. Give back what you took.** The dead one's own marked things (N1.1) handed back to its friends: each piece settles
part (its armour and weapon most). Wearing them in front of its friends does the opposite: proof, and an insult.

**Settled** means: the grudge is cleared to a "settled" note in the record ("we're square"); trust stays where it was
(low), no hunts; they say so if asked. Killing one of theirs again after settling is worse than before (broken word,
grudge +20 on top).

### N1.6 Talk for N1

New line keys (each with many lines, player speak, by personality; no rule ever spelled out):
`avenge.found` (why they've come), `avenge.found_suspect` (not sure, but), `parley.listen`, `parley.believed`,
`parley.doubt`, `parley.lie`, `parley.offer`, `parley.paid`, `parley.refused`, `spared` (as it backs off),
`spared.told` (to friends), `settled` (if asked later), `avenge.done` ("that was for bob"), `avenge.again` (a second
visit), `found_out.grave` / `found_out.things` (a friend finding out, said aloud only if a player is near),
`suspect.wearing` ("thats bobs sword"). And `convo.grudge.killed_me` (today KILLED falls under "hit").

---

## 6. Part N2: on the run

The user: "an on the run type of thing where it turns into a manhunt, until they cant find me anymore".

### N2.1 The trail is sightings

- Every Drifter that sees the player keeps when and where (built: last seen). New: it keeps the **last three
  sightings** of a player it has reason to (a grudge, a wanted notice it knows of, news of a death), not just one.
- **Hunters ask around.** On meeting any Drifter that doesn't hate them, and in any town they reach, hunters ask, and
  are told that Drifter's sightings of the player (Whereabouts already answers "where is X"). Being told is fair (DESIGN
  5: "told by another Drifter"). The newest sighting any of them knows is where they head.
- **Towns spread it** (addon): a wanted player seen in a town is talked about there (built: news), and the word goes out
  along the roads with traders and travellers (news carried). A town's guards, seeing a wanted player, already act.
- Outlaws (N3) don't report to anyone.

### N2.2 Losing them

- They go to the newest sighting they know, search round it (a few spots, a few minutes), and ask anyone near.
- **The trail goes cold** when the newest sighting they know is over **a day old** and the spot's been searched. Then
  they try the player's known base once. Nothing: they head home. That's a failed hunt (N1.4: grudge -15).
- **How long they keep at it:** **3 days** with no fresh sighting, a day more for the vengeful, a day more with
  permanent death (they're slower and more careful, so it lasts longer).
- **What helps the player:** keeping out of sight (sneaking hides the name, as for players), off the roads, out of towns,
  travelling far, going underground, crossing water, lying low somewhere nobody lives. Nothing magic: just not being
  seen by people who'll tell.
- **What doesn't:** going home if they know where it is; walking through a town where you're wanted; a friend of the
  dead seeing you.

### N2.3 How hunters behave

- Real Drifters on a trip: they walk or ride (riding to a far sighting: built), eat, and stop for the night (by their
  Stakes and night style: built).
- On finding the player: the parley first (N1.5) for avengers; hired hunters skip it unless the player speaks first
  ("there's a price on your head").
- **With permanent death** (or wariness 0.5+): they don't charge in. They stalk (DESIGN 14's stalking: 24 to 40 blocks
  back, out of sight) and wait for an opening: the player fighting mobs, hurt, mining, crossing a bridge, at night.
  Hunts last longer and kill less often, as a careful player would.
- Fair as always: no bed camping, no spawn camping, never near the player's bed (DESIGN 23).
- **The end of a hunt:** the player killed (bed or lives) and the avenger's revenge (N1.4), all hunters dead or fled,
  settled, or the trail cold. Today's 10-minute fight limit goes; a fight lasts as a fight does, and a hunt lasts as N2.2
  says.

### N2.4 What the player knows

Only what they'd be told (the journal's rule):
- A friend's warning (built for its town; now also "bobs friends are looking for you"); the scout's orders (built); a
  Drifter mentioning it ("heard theres people after you"); the Gazette for a town's wanted.
- The journal (N6): "Bob's friends are looking for you. Last you heard, they were asking about you in Aby."
- Nothing on screen: no compass, no marker.

---

## 7. Part N3: outlaws (illagers and witches as half-mob Drifters)

The user: replace illagers "completely ... like we did villagers", Bandits "much more temporary than nations and
citizens", "more of a mob than a drifter"; every way one appears ("if i place a pillager, witch, or vindicator egg ...
a drifter of that archetype spawns ... half mob and half drifter").

### N3.1 What an outlaw is

The name "outlaw" for the half-mob kind is a proposal (to tell it from the full Bandit Drifters that live in the world
and towns' crooks; the user can rename it). An outlaw is **the same Drifter in a light mode** (the way encounter
Drifters are already a lighter kind). **Where it lives** (my call, after the builder check): the light mode, the roles
and the known outlaw (N3.4) are in **Drifter**, so Drifter alone has them too (its ambushes, muggings, snipers and
hired hunters use them, and a full Bandit or encounter bandit that kills or escapes the player can become known the
same way). **Swapping vanilla illagers and witches for outlaws across the world is the addon's** (N3.3), like
replacing villages.

- **Looks and fights like a Drifter:** a player name, a real skin, the Bandit archetype's traits with Drifter's own
  noise on top ("not completely falling into that archetype"), perception, player reach and timing, player fighting.
- **A slim brain:** fight, loot, guard its spot, roam, raid, flee, eat. No survival life: no mining, building, farming,
  crafting chains, homes, whims, trading or chat topics beyond a few lines.
- **A mob's life:** spawns as the mob it replaces would; saved with its chunk like a mob (so an outpost's gang is still
  there next visit); **one life always**, whatever the respawn setting; no entry in the world's directory, no
  relationships kept except with the player who fights it (N3.4), no town, no friends outside its gang, not in the
  chronicle (except a known outlaw).
- **Hostile like the mob was:** outlaws attack players, town people and other Drifters on sight (the Bandits' "kill on
  sight" rule and setting), and not each other (one side, as illagers are). A witch's kind keeps a witch's ways (N3.2).
- **Its kit is its loot:** everything it has came in its role's starting kit (a data file per role), and drops as the
  mob's would, so farms and drops players rely on still pay out.
- **A cap:** at most **48** loaded outlaws in a world (a setting); a spawn over the cap doesn't happen.

### N3.2 The roles

| Vanilla mob | Outlaw role | Kit and ways (starting point; data files) |
| --- | --- | --- |
| Pillager | Crossbowman | crossbow and arrows, a sword now and then; keeps range; drops its crossbow sometimes, arrows |
| Vindicator | Brute | iron axe; charges; drops an emerald now and then, its axe rarely |
| Evoker | Chief | the gang's leader: good armour and a weapon, a **totem of undying** (it uses it, as a player would), emeralds; no magic |
| Illusioner (unused in vanilla) | Crossbowman | |
| Witch | Brewer | splash potions (harming, poison, slowness, weakness) thrown at range, drinks healing, fire resistance, water breathing when it needs them (as a player drinks); carries what witches drop (glowstone dust, redstone, sugar, gunpowder, spider eyes, sticks, glass bottles), so witch farms still work. A loner, not a gang, except in a raid |
| Raid or patrol captain | the gang's captain | the band's colours; carries an **ominous bottle** (1.21: a captain drops one; it's how players get Bad Omen for raids and ominous trials) |
| Vex | (gone) | only evokers made them |
| Ravager | (gone from raids) | a player can't ride one; bigger raids bring more outlaws instead (mounted outlaws later, N7). A ravager spawn egg still makes a ravager: it's a beast, not a person |

**Totems stay obtainable** (only evokers and raids give them in vanilla): chiefs in mansions and a raid's last wave's
chief carry one. **Ominous bottles stay obtainable**: captains carry them.

### N3.3 Every way they appear (the addon)

Whatever would make a pillager, vindicator, evoker, illusioner or witch makes an outlaw of that role instead:
spawn eggs, `/summon`, spawners (modded ones too), natural spawns (witches at night), patrols, structure pieces
(outposts, woodland mansions, witch huts), raids (vanilla's and the addon's town raids), and other mods' spawns of
those mob types. One hook where the world first adds one of these mobs; it's swapped for an outlaw on the spot.

- **Already in a world** (my call, after the builder check): illagers and witches already saved in an existing world
  (an explored mansion, an outpost, a witch hut) are swapped too, the first time they're loaded after the update, so
  it's complete. Except ones a player has named with a name tag (somebody cared about that one): those stay vanilla.
  Farms that make fresh witches or raiders make outlaws from then on, unless the setting below is on.
- **Pillager outposts:** with the addon, every new one is held by an outlaw gang (3 to 5 crossbowmen and brutes, a
  captain), no longer half of them by Drifter's full-Bandit gangs. Like vanilla's, new ones drift in over the days (one
  every half day or so, up to the gang's size), so it never stays empty. Outposts already held by full Bandits keep
  them. Without the addon, Drifter's half-and-half stays as it is.
- **Patrols:** outlaw patrols of 3 to 5 with a captain, on vanilla's patrol timing, roaming; despawn when far, as patrols
  do.
- **Woodland mansions:** a clan: a chief per wing, brutes and crossbowmen, a brewer or two.
- **Witch huts:** a brewer living there (and its cat).
- **Raids** (town raids, A7, and vanilla ones where villagers are allowed): waves of outlaws, a captain per wave, the
  last wave led by a chief; Hero of the Village as now.
- **Setting: "Illagers and witches allowed"** (the addon), **off** by default (like "villagers allowed", decision 5 of
  LIVED_WORLD): on, nothing is swapped, for players who want raid or witch farms. Without the addon, nothing is swapped.

### N3.4 The outlaw who becomes known (the Nemesis twist)

Outlaws are disposable, until one does something that makes it someone:

- **It kills the player**; or
- **it gets away from the player**: it fought the player (hit or was hit), broke off, and got out of sight with **40%
  of its health or less**; or it saw the player kill **two or more** of its gang and got away.

Then it's **known**: kept as a full record (named, saved, in the directory marked as an outlaw), its traits and gear as
they are, and its grudge against the player (N1 rules, first-hand). What follows:

- **Word of it:** it's talked about (news as for any Drifter): "the outlaw that got muto at the ravine". Players hear it.
- **It comes back:** 2 to 5 days later with a new gang (3 to 4 outlaws gathered from the nearest outpost or a patrol),
  looking for the player where word has it (N2), wearing what it took (a trophy: N6).
- **Its story ends** when it's killed (one life), or after **10 days** without meeting the player it moves on ("went
  south", in the news) and is gone.
- At most **6** known outlaws in a world (the oldest forgotten first).
- This is where Nemesis's "the one who killed you rises" lives: in the villain layer, where it fits, without touching
  towns' people. With permanent death nothing changes for outlaws (always one life), but the one that got away is the
  story.

### N3.5 Delvers become looters

The user (2026-11-14): "we also have to find a way to incorperate delvers, in structures and stuff, maybe remove them
and turn it into bandits aswell?", then "yes" to this. Delvers are already nearly outlaws (Bandits that kill on sight,
made near the player, never saved, "more of a mob than a drifter" in the user's words of 2026-10-17), so they become an
outlaw role, the **looter**:

- **The same feel:** you come across one in the middle of looting. It works through the place chest by chest
  (`DelveActivity`, kept), kills on sight, and carries what it really looted from the structure's chests, so its pockets
  are fair loot.
- **Toned-down kit** (today iron to diamond, enchanted, is a lot to hand out for nothing): mostly iron and chain, a
  diamond piece now and then, rarely enchanted; a weapon, a bow, a pickaxe, torches, food. The good things on it are
  what it looted. A data file like the other roles.
- **More places** (a structure tag, so other mods' structures can be added): mineshafts and ruins as now (Hopo's
  mineshafts and portals, Philips Ruins included), plus dungeons (spawner rooms), desert and jungle temples, strongholds
  now and then, and ancient cities rarely (sneaking, warden-wary; the old TODO "ancient cities"). Made the way delvers
  are: near the player, at an unlooted chest out of sight, on the place's roll (fixed by the seed, again after 3 days).
- **A looter can become known** (N3.4): one that gets away from you down a mineshaft, or kills you there, is the one
  that got away.
- **What stays real:** the townsfolk out scavenging at ruins (the other half of ruin looters) stay real townspeople who go
  home; they aren't outlaws.
- **Where it lives:** in Drifter, like the rest of the outlaw kind, so it works without the addon. The setting
  "Mineshaft delvers" becomes **"Looters"** (on/off), its old value kept. The outlaw cap (N3.1) counts looters.

---

## 8. Part N4: rivalries you walk into

The user: "i witness a rival story happening in front of my eyes. i can choose to watch, or intervene, whoever i side
with makes me be rivals with theirs". All three ways in (the user: "all three"): by chance, by hearing, pulled in.

### N4.1 What a rivalry is

A record between two sides: two Drifters, or a Drifter and its friends against another and theirs. Its kind, why it
started (a real event), its heat, its history (incidents), who's joined either side (the player included), and how it
ended. Saved (addon, versioned). Limits: about one per 8 people in a town, at most 5 per town, 40 in the world.

### N4.2 Where they come from (B makes C)

Only from things that really happened:

| Kind | Starts when |
| --- | --- |
| Neighbours | two homes close, competing for land, ore, trees (Drifter's rivalry, DESIGN 21), a block broken, a chest raided |
| Candidates | two stood in an election; the loser holds it against the winner (Politics) |
| The plot | a ruthless plotter against its leader, and those who know (Unrest) |
| Across a border | people of two towns at war, one's friend killed in a raid or battle (Diplomacy, wars, History's grudges) |
| Thief and victim | a theft seen or worked out (Justice, Drifter's theft rule) |
| The exile | a crook thrown out, against the guard or witness who saw it done |
| Debts | a debt never paid, a promise broken (Drifter's debts, broken words) |
| A death | a killing between Drifters: the dead one's friends against the killer (N1's rules, between Drifters) |
| An outlaw | a known outlaw (N3.4) against the town it raided |

### N4.3 How a rivalry plays out

- **Incidents** every 2 to 4 days while it's hot: words in chat (bystanders hear: only nearby players see Drifter chat),
  a snub, a broken fence (Drifter vs Drifter raiding rules), a theft, a fight to a few hearts, a challenge, friends
  brought in, and when it's hot enough a fight to the death.
- **Loaded:** played out for real, by the same goals and rules as everything else. **Unloaded:** decided on paper by the
  same numbers (A10's way) and told afterwards (news, the Gazette, the people involved).
- **Endings:** one side dead (with permanent death that ends it; the dead one's friends may take it up), one leaves
  town, a duel decided, made up (trust repaired), the cause gone (the war ends, the debt is paid), or it cools for good.
- **C reaches B:** a rivalry's end can tip an election, a revolt or a plot (the leader's rival killed, a plotter
  exposed), and a death in a rivalry is a death like any other (Justice, graves, the chronicle).
- No outcome is chosen for the story. Timing only: an incident that has a reason to happen happens.

### N4.4 The three ways in

1. **By chance:** an incident happening where the player is (two shouting in the street, a fight, someone's fence
   coming down at night).
2. **By hearing:** gossip lines ("carl and dave are at it again"), the Gazette, a notice ("anyone seen who took my
   pickaxe?"), what a friend tells you.
3. **Pulled in:** one side asks the player (Quests grows: "help me deal with Carl", "tell Dave he owes me", "get my
   pickaxe back", "stand with me when we have it out", "find out who did it"). Through the quest system's own asking
   (one question at a time, yes or no in chat or the page).

### N4.5 Taking a side

- **Watching** costs nothing.
- **Helping a side** (hitting the other in a fight, doing one side's quest against the other, telling on the other,
  standing with them): trust up with that side and its friends; the other side and its friends think worse of you, by
  how much you did. Fighting or killing for one side makes you part of the rivalry: its other side's grudge rules (N1)
  now apply to you, so you can be hunted for it.
- **Making peace** (later, if wanted): talking the two down with N1's parley, a gift, carrying an apology. Listed as a
  maybe; not in the first build.
- The journal shows which side you're on (N6).

---

## 9. Part N5: the encounters revamp

The user: "take out some drifter encounters that dont make sense like warbands". The rule: an encounter that implies a
reason, a relationship or a side uses real people who have that reason; strangers made for the moment only for pure
chance meetings. Without the addon, encounters fall back to strangers as now (Drifter alone stays whole).

**Take out**
| Encounter | Why |
| --- | --- |
| Warband battle | Real wars between towns and nations exist; a battle the player sees should be one of theirs (a raid or a battle from the addon's wars, seen as it happens) |
| Village raid | Villages are gone; towns get raids (A7, outlaws after N3) |
| Feud | Two strangers shouting gives way to N4's real rivalries |
| Revenge squad | Becomes N1's avengers (real friends) |

**Make real**
| Encounter | Becomes |
| --- | --- |
| Bounty hunters, the scout | Hired by a real Drifter or town with real money, led by the payer when it's brave; the start of a manhunt (N2) |
| Wanted poster | A real crime from a town's wanted list or a real grudge, not one made up; the reward paid by whoever posted it, from their money |
| Thief, thief in your base | A town's crook or a real Bandit; remembers being caught |
| Ambush, mugging, sniper | Outlaws from a nearby outpost or patrol, or hunters with a price on the player |
| Hostage | A real person from a nearby town held by outlaws; rescuing them makes friends there |
| Mourner | Someone mourning a real death at a real grave |
| Merchant, convoy | Real traders on real trade runs between towns (the addon's trade runs) |

**Keep as chance meetings** (with the addon, the traveller is someone real from a nearby town when one fits, and is
kept so it can remember the player): injured traveller, lost and hungry, trapped miner, lost dog, escort, duel
challenge, race, chased by a horde, campfire party, builders, the watcher, treasure maps.

**Drifter alone** (my call, after the builder check): the reasons for taking these out only hold with the addon
(Drifter alone still has villages and has no wars or N4 rivalries). So they're **turned off when the addon is
installed** (through the API), not deleted: Drifter-alone players keep warband battles, village raids and feuds, and
their switches stay on Drifter's encounter page (shown as "handled by Drifter: Civilizations" when the addon's there).
The revenge squad gives way to N1's avengers everywhere (avengers work in Drifter alone, from friends and groups). Old
saves with a removed kind running end it quietly.

---

## 10. Part N6: renown, callouts and the Rivals page

### N6.1 Killing the player counts

Today a Drifter that kills the player only says "gg" and picks up the drops. Now:
- It goes in its diary (no longer skipped), so it talks about it later ("i got muto once").
- The triumphant mood and confidence, as after any big win.
- **A trophy:** it keeps one of the player's things it picked up (the best, or one with a name) and wears or uses it.
  That's the visible history Nemesis did with scars: the outlaw wearing the player's helmet.
- **Word spreads:** news of it, so others know ("did you hear? frost got muto at the ravine").
- The player's friends (D2) think worse of it when they hear.

### N6.2 Renown (theirs and yours)

- **A Drifter's renown** is what others have heard of it: killed the player, got away from the player, won a rivalry,
  survived a manhunt. Others then speak of it by what it did, in player speak ("the guy who got muto", "the one muto
  couldnt catch"): built from the deed, not a title out of a list, and only by those who heard.
- **The player's renown** works the same way from the player's deeds: killings, mercy (spared), settling, siding in
  rivalries, a manhunt survived. It moves what people do: the feared get more "fine, but stay away" in a parley (N1.5),
  a merciful name makes giving in likelier, a murderer's name brings hunters faster.
- No numbers or ranks shown, anywhere. Renown is news, not a ladder (see section 13).

### N6.3 Callouts when you meet again

Once per meeting (rate-limited like other lines; the chat setting applies), a Drifter or known outlaw with history with
the player says so as it sees them: `history.killed_you` ("back for more?"), `history.you_killed_me` (respawned),
`history.got_away` ("you again"), `history.spared_me`, `history.i_spared_you`, `history.settled` ("we're square,
right?"), `history.owes`, `history.rival_side` ("you're with carl now?"). Naming the place when it knows it. Not a
cutscene.

### N6.4 The Rivals section of the journal

On the People page (or its own bookmark, the builder's call by how the page fits): only what the player knows, in words.
- **After you:** who's hunting you as far as you've been told, and where they were asking last.
- **History:** who killed you, who you killed and came back, who got away, who you spared and who spared you, grudges
  settled.
- **Stories you're in:** rivalries you've joined and on which side.
- The chronicle (Annals) gets rivalry and manhunt stories as "a life" pages when one ends.

---

## 11. Part N7 (later): fighting on horseback

Drifters ride horses for trips today and always get off before trouble. A player can fight from a horse, so it's fair:
- **Mounted hunters** (N2): riding between sightings and in the chase, so escaping on foot gets much harder (the player
  needs a horse, or ground horses can't cross).
- **Mounted outlaws** (N3): the ravager's place in big raids; patrol riders now and then.
Last, because it needs its own work on fighting while steering (the user: later, unless wanted sooner).

---

## 12. Settings, commands, saves, API

**Settings** (on the config screens, each with a plain description):
- Drifter: Grudges over deaths (on); How long grudges last (short, normal, long); Avengers (on); Parley (on); Hired
  hunters (on); How long a manhunt lasts (days); Known outlaws (on); Callouts (on).
- Addon: Illagers and witches allowed (off: swapped for outlaws; on: vanilla ones, nothing swapped; one setting, not
  two); Outlaws loaded at most (48); Rivalries (on) and how many (few, normal, many).
- Everything is per player in multiplayer: grudges, hunts, parleys, renown, sides taken.

**Debug commands** (op): `/drifter grudge [drifter]` (its grudges and why), `/drifter avenge <victim>` (send its
friends now), `/drifter hunt` (any hunt on the player: who, where they're looking, how cold), `/drifter parley` (start
one with the nearest avenger), `/drifterciv outlaw <role>` (spawn one), `/drifterciv outlaw known` (make the nearest
known), `/drifterciv rivalry` (list; `start <kind> <a> <b>`, `incident <id>`).

**Saves:** relationships grow (settled notes, life debts, suspicion, last three sightings), Drifter's save version up;
the addon's rivalries and known outlaws in its own versioned data; the dead's item marks in item custom data.

**API** (docs/API.md, grown with the parts that need it): events for a death found out, avengers sent, a parley's
outcome, a grudge settled, a hunt started and ended, a Drifter becoming known, the player's renown changing; readers
for a Drifter's grudges and a hunt's state; a way for the addon to swap a spawn for an outlaw and to post a hunt for a
town.

---

## 13. A note on the patent (not legal advice)

The Nemesis System's patent (US 10,926,179 and its continuations, to about 2036) claims a *combination*: an event
between the player and one character changing a second character's stored parameters, inside a ranked faction where
rank goes with fighting power, shown to the player. It has never been enforced, and Drifter is a free mod. Still, the
design keeps its own shape: renown is word of mouth, not promotion up a ranked ladder; there's no ranked board of
enemies with power levels; rivalries are webs between people, not a faction hierarchy. That's also simply more like
Drifter. If the mod were ever sold, get a proper opinion first.

---

## 14. Order and versions

1. **N1, the grudge loop** (0.24.0 on): found out, avengers, burn-out, the four ways to settle. Maybe two steps
   (finding out and avengers; then settling).
2. **N2, on the run.**
3. **N3, outlaws**, then the known outlaw.
4. **N4, rivalries** (needs N1's rules between Drifters).
5. **N5, the encounters revamp** (needs N1 to N4: avengers, hunts, outlaws, rivalries).
6. **N6, renown and the Rivals page** (pieces of it can ride along with earlier parts: the trophy and the diary with N1
   or N3).
7. **N7, horseback fighting**, later.

Each part: a version, a headless smoke run (tools/smoke.py, `sprint` for days), a client test, TESTING.md in plain
language, an entry in the progress below and docs/PROGRESS.md.

## 15. Testing, part by part

- **N1:** kill a lone Drifter unseen with permanent death: nothing until a friend comes looking a day later; with a clue
  (seen near, wearing its sword) its friends come; with none, nobody does. Same with lives: the victim leads them.
  Parley each way (true, false, unknown, sorry, pay); spare one and see its friends cool; give back its sword; keep
  killing and see it grow; stop and see it burn out over days (`sprint`).
- **N2:** hunted, lose them by going underground and far; let them see you in a town and watch them come; a cold trail
  ends the hunt; permanent-death hunters stalk rather than charge.
- **N3:** spawn eggs of each mob give outlaws; an outpost refills; a raid of outlaws with a captain's ominous bottle; a
  mansion chief's totem used and dropped; witch farm drops; the setting off brings vanilla back; get away from one at
  low health and meet it again with a gang wearing your things. Looters: one in a mineshaft, a dungeon, a temple and a
  ruin (scavenging townsfolk still go home); its kit toned down, its pockets what it looted; "Looters" off stops them.
- **N4:** a town over several days: rivalries from an election and a war, incidents seen and heard, a quest to pull you
  in, siding and being hunted for it, one ending in a death with permanent death and the heir taking it up.
- **N5:** each kind kept, made real or gone; Drifter alone falls back to strangers.
- **N6:** die to a Drifter: its diary, trophy, the news, the callout next time, the journal's Rivals.
- Performance with a raid of outlaws, three hunts and twenty rivalries going.

## 16. Left to the builder

- How the swap hook is done (where the game first adds the mob), and how the light mode slims the brain.
- Whether Rivals is a section of People or its own bookmark.
- The exact wordings players can say in a parley (lots, like the rest of player talk).
- Fine numbers: every number here is a starting point to tune in testing.

## 17. Calls made after the builder check (the user can overturn any)

1. **Hired hunters** are outlaw-kind hired blades, kept for the hunt, carrying their pay; killing them starts no new
   grudge. With the addon, a town may send its real guards instead (N1.2).
2. **Removed encounters** are off only when the addon is installed; Drifter alone keeps them (N5).
3. **Outlaws and the known outlaw live in Drifter**; the addon does the swapping across the world (N3.1). With the addon,
   new outposts are all outlaw gangs.
4. **Existing worlds:** saved illagers and witches are swapped on their first load, except name-tagged ones (N3.3).
5. **Looters** (N3.5, approved by the user): delvers become an outlaw role with a toned-down kit, in more kinds of
   structures; ruin scavengers from towns stay real.
6. Saying nothing in a parley means the fight starts; only gear is marked as the dead's; the two illager settings are
   one; everything is per player.

## 18. Questions for the user

None open yet. New ones go here as they come up.

## 19. Progress

Nothing built yet (milestone 24 starts after the user's current work).
