# Encounters and bounties (milestone 21, planned)

The user (2026-10-20), on Bountiful compat: "different drifters also have bounties on them aswell. like imagine i check
the bounty board and its a map to a drifters house (or something like that) lets say and the reward is super valuable.
i kill the drifter, collect the bounty. can also happen if bounty hunters are looking for me. i could be chilling at my
base, or going somewhere, and suddenly a group of armed bounty hunters are here to kill me."

And: "an 'Encounter' system, where random events happen with temporary drifters you will never see again (think of it
as like the random event system in the GTA franchise)."

Saved for a milestone of its own: "save all of this for a mile, we will do this soon". Then (2026-10-20) "work on
milestone 21": with no pick from the list yet, the suggested first batch was built (below, "Built"). The rest of the
list is still the brainstorm to pick from.

## How an encounter works

- **Rare:** about one every in-game day or two per player, never two at once.
- **Arrives fairly:** the Drifters walk in from beyond view distance. Nobody pops into existence.
- **Leaves for good:** when it's over they walk away and vanish once out of sight. They aren't saved, so you never see
  them again.
- **Fits the moment:** the time of day, the biome, whether you're at home or on the road, how strong your gear is, and
  your reputation (who you've killed, who holds a grudge against you).
- **Can be switched off:** settings turn each kind off.
- Everything they do follows the usual fairness rules: player reach and time, perception only, no free items.

## Bounties (the user's ideas)

1. **Wanted Drifter:** the bounty board posts a map to a real Drifter's house with a big reward. Kill it and collect.
2. **Bounty hunters:** a Drifter with a big grudge against you (or your kill count) puts a price on your head. A few days
   later 3 to 5 armed hunters turn up at your base or on the road. They find you fairly, from where you were last seen,
   like the manhunt hunter.
3. **Turning the tables:** you spot a hunter scouting your base before the rest arrive.

## Danger

4. **Ambush on the road:** bandits waiting behind trees on a path you use.
5. **Mugging:** one Drifter blocks your way: "hand over the diamonds". Pay up or fight.
6. **Thief in your base:** you catch someone going through your chests.
7. **Revenge squad:** friends of a Drifter you killed come looking for you.
8. **Sniper:** an archer on a ridge taking shots at you while you work.
9. **Village raid:** Drifter raiders attacking a village while you're there.

## Someone needs help

10. **Injured traveller:** low on health and fleeing mobs, begging for help. Save them for a reward; ignore them and
    they might die.
11. **Trapped miner:** "help, I'm stuck!" from a cave. Dig them out.
12. **Lost and hungry:** asks for food or directions, then leaves a small gift.
13. **Escort:** wants you to walk them to the next village safely, and bandits might jump you on the way.
14. **Hostage:** bandits holding a villager or Drifter at a camp. Rescue them.
15. **Lost pet:** a Drifter looking for its dog. Find it.

## Chaos and drama

16. **Stop, thief!:** a Drifter sprints past with stolen loot and the owner is chasing. Catch the thief, or join them.
17. **Feud:** two Drifters fighting each other. Pick a side or just watch.
18. **Duel challenge:** a PvP sweat wants a 1v1 for a stake.
19. **Race:** "first to that mountain top wins".
20. **Chased by a horde:** a Drifter runs past you with zombies on its heels, leading them to you.

## Peaceful and odd

21. **Travelling merchant:** a player-like trader with a few rare items for emeralds.
22. **Treasure map seller:** sells a map to a real buried chest.
23. **Campfire party:** a group celebrating at night, with fireworks and cake, invites you over.
24. **Convoy:** riders on horses passing through, heading somewhere far.
25. **Mourner:** someone grieving at a grave by the road.
26. **Builders:** a crew throws up a bridge or a shelter nearby, then leaves.
27. **Watcher:** a figure at the edge of render distance at night, gone when you get close. A Herobrine joke, harmless.

## Bountiful compat (part of this milestone)

- **Drifters doing bounties** (the user said yes, 2026-10-20): a Drifter reads a board's offers, picks one it can fill
  from what it has or can gather (deliver items, kill mobs), and turns it in for the reward, as a player does. The
  board's screen can't open for its helper player, so this goes through Bountiful's own code (like villager trading).
  Kambrik is now in the DRIFTER TEST profile, so Bountiful loads in the user's game; it may not load on the development
  server (as with Farmer's Delight), in which case testing is in the user's game.
- **Check first:** Bountiful's own bounties are "deliver these items" and "kill N of this mob type". It may have no way
  to say "kill this particular Drifter". If not, the wanted Drifter comes as our own wanted poster (a map plus a notice)
  at the board.
- Already built: the "reading the bounty board" whim (milestone 19).

## Suggested first batch

Bounty hunters, wanted Drifter, injured traveller, stop thief, travelling merchant, and ambush: danger, helping and
trading, which tests the whole system.

## Built (first batch, 2026-10-20)

Code: `encounter/` (`Encounters` the director, `Encounter` the base, one class per kind). How it works:
- **The director** looks at each player every 10 s. One encounter at a time per player, about one every 1.5 in-game
  days by default (each between half and one and a half of that), only for players on the surface in the Overworld in
  survival or adventure, and only kinds that fit the moment. Nothing fits: it tries again in two minutes.
- **The Drifters** are made for the part: a kit that fits it (and, for fighters, how well armed the player is:
  leather and stone, iron and chain, or iron and diamond), traits that fit (a vengeful hunter, a timid traveller). They
  appear on open ground no player can see, 22 to 80 blocks off, and walk in. Never saved; over, they walk off and are
  gone once nobody can see them (or nobody's near). Not in `/drifter list`. Same rules as any Drifter: they see and hear
  for themselves, and fighting, fleeing and eating still come first. The members of one encounter are on the same side
  (bandits of one ambush don't fight each other); a thief and the one it robbed aren't.
- **Bounty hunters:** a Drifter with a heavy grudge against a player (40+, it was wronged) or the friends of three or
  more Drifters they killed put a price on their head (half a chance each day it holds). A day to two and a half later
  three to five armed hunters come, told only what word got round: the newest sighting any Drifter has of the player (a
  day old at most), or their base. They search there, and on sight go after them with no warning, relentless
  ("Player! there's a price on your head, from <who>. we've come to collect."). Over when the player dies, they all die, or ten
  minutes pass. The price is spent either way.
- **Wanted poster:** a messenger brings a map: a real Drifter 150 to 1200 blocks off (a Bandit first), its home marked
  if it has one (else where it was last seen), "WANTED: name", its crime and the reward (3 to 8 diamonds, more for far
  or dangerous) written on it. Bring it down within a week and a courier comes with the reward ("Heard you brought down
  ... As promised.").
- **Injured traveller:** a hurt Drifter (4 hearts) running from two or three zombies (in caps, so daylight doesn't end
  it), shouting for help. It runs away from them (and so toward the player, who's on the other side); once it has seen
  the player it runs to them. Saved, it thanks them and hands over the best thing in its pack (emeralds, gold, a golden
  apple, a diamond). Nobody comes in half a minute after it's safe: it goes on alone. More likely at night.
- **Thief:** a thief sprints past with gold, emeralds and maybe a diamond; the owner chases it, shouting "stop,
  thief!". Stop the thief and the owner picks its things up and gives the player half. Let it go and both are gone.
  The owner counts the player killing the thief as killing its attacker (trust up), not a friend. Daytime.
- **Travelling merchant:** walks up ("hello there! I've a few rare things, if you've got emeralds. Have a look.");
  right-click it for the game's own trading screen: 3 to 5 of diamonds, golden apples, ender pearls, name tags,
  saddles, spyglasses, nautilus shells, blaze rods, slime balls, glow ink, an enchanted book, a music disc, bottles o'
  enchanting, priced in emeralds. Sold goods leave its pack, the emeralds go in. Stays three minutes, or until the
  player walks off. Daytime.
- **Ambush:** a player travelling (20+ blocks in 10 s), 100+ blocks from their bed and the world spawn: two to four
  bandits lie in wait, crouched, 55 blocks ahead on their way, watching the road. A noise or a glimpse near them and they
  go to look; on sight, close or after a hit, they spring it, no warning ("now! get them!"). Nobody came by in four
  minutes: they go. Not at night.
- **Settings:** Settings > Encounters... (on or off, how often, each kind), or `/drifter config encounters true|false`,
  `/drifter config encounterdays <days>`, `/drifter config encounter <kind> true|false`.
- **Commands (testing):** `/drifter encounter` (what's going on, what's due, prices and posters), `/drifter encounter
  start <kind>`, `stop`, `bounty` (a price on your head now), `now` (the next one due now), `talk` (as a right-click on
  the nearest of your encounter's Drifters), `hit <part>` (strikes one down as if you had).
- **Tested** in the game client: each kind started, ran and ended as above (the merchant's trading screen opened with
  its wares; the poster's map came with the red X and the reward paid in diamonds; the hunters found and chased the
  player; the ambush sprang on a player walking past).
- **Not yet:** the rest of the list above.

## Built (second batch, 2026-10-20)

The user: "yea pick some more events". Picked for variety (danger, odd, friendly), each tested in the game client:
- **The watcher (27):** at night, a still figure with no name over its head at the edge of sight (35 to 70 blocks, open
  ground the player can see), looking their way. Come within 32 blocks and it walks off and is gone. Needs open
  country (in thick forest it can't find a spot, and another encounter comes instead).
- **Duel challenge (18):** a PvP sweat as well armed as the player walks up: "1v1? N diamonds says I win. hit me if
  you're in." Hit it to accept. A fight to a few hearts: at three hearts it gives up and pays its diamonds ("fair's
  fair. good fight."); the player down to four and it stops ("keep your stuff, it was for fun"). No answer in 30 s:
  "coward."
- **Mugging (5):** away from home, an armed Drifter blocks the way: "N emeralds (or gold, or diamonds), or your life.
  throw it here." Throw it that much (a diamond counts three) and it goes; walk off or hit it and it attacks.
- **Lost and hungry (12):** a starving traveller asks for food; throw it some, it eats, and gives something from its
  pack ("bless you. here, for your kindness.").
- **Feud (17):** two Drifters fighting ("you burned my house down!"). Hit one and the other, if it wins, thanks you with
  something; or just watch.
- **Campfire party (23):** of an evening, three or four Drifters set up a campfire and a cake on bare ground, dance,
  cheer and set off fireworks, and call the player over. After four minutes, "goodnight, everyone!"; the campfire and
  cake stay.
- **Mourner (25):** someone puts up a grave by the road (a sign, "Here lies <name>. Gone too soon.", and a poppy) and
  kneels there; come close and it talks about them. The grave stays.
- **Treasure maps (22):** some travelling merchants (2 in 5) also sell a buried treasure map, the game's own kind,
  pointing at a real buried treasure, for 8 emeralds.
- Along the way: encounter lines are said even when they come quickly (the chatter limit doesn't apply to an
  encounter talking to you; the chat setting still does); encounter Drifters never dig in for the night or go hunting
  at night (they're out for their part); a merchant's same goods are one offer; `/drifter encounter throw <item>` and
  `hit <part> [amount]` for testing.

## Built: killing encounter Drifters, and revenge squads (2026-10-20)

The user asked "what will be the punishment for killing in a duel?" There was none (and killing paid better: its stake
plus its gear). Now (`Encounter.killing`, `Encounters.killed`, `RevengeSquad`), tested in the game client:
- **How a kill counts.** Self-defence or it had it coming (a bandit, a bounty hunter, a mugger, a thief, a revenge
  squad): nothing. Anyone else from an encounter (a traveller, a merchant, a feud's fighter, a duellist in the fight it
  asked for): like killing any Drifter, one towards the three that put a price on their head. A duellist that had
  given up: **murder**, a price on their head at once (the hunters come a day or two later as usual).
- **The duel** checks for "I give up" (three hearts) and "you lose" (four) as each blow lands, not once a second, so a
  crit doesn't take it past the check (a big enough blow can still kill it outright). Hit after it gave up, it says
  "hey! I gave up! it's over!"; from then on killing it is murder.
- **Revenge squad (7):** a Drifter they killed that didn't have it coming (one of the world's or an encounter's; not
  one that was after them, nor a wanted one they had the bounty on): one time in four its friends come for them, a
  quarter of a day to a day later; a murder, always. Two of them (three for a murder, one an archer), armed about as
  well as the player. They look where word has it the player is (the newest sighting), else where their friend died;
  only near the player (160 blocks) do they find them; three days of not finding them and they give up. On sight:
  "Player! you killed <name>. they were our friend." (a murder: "<name> gave up, and you killed them anyway.") and
  "this is for <name>!", then after them, relentless. Over when the player dies ("that was for <name>."), they all die,
  or ten minutes pass. One squad at a time (a murder takes the place of a lesser grudge). Settings: "Revenge squads".
- `/drifter encounter` shows a squad on its way; `/drifter encounter start revenge` brings the one on its way now (or,
  with none, a squad for a made-up friend).

## Built: Drifters doing Bountiful bounties (2026-10-20)

`compat/BountifulCompat` and `compat/BountyGoal`, only with Bountiful installed (it loads in the development setup too).
- A board it has seen within 48 blocks, in the daytime with nothing pressing (a little sooner for the greedy and
  curious): it walks over and reads it, as a player opens it. A bounty asking only for things it can spare (what it
  sells, over what it keeps, as when it trades; never its gear; 8 of a food, 32 of a building block, 4 of anything else
  kept) it takes and turns in at once by right-clicking the board with it, as a player does: Bountiful's own code checks
  and takes the items and pays the reward into its pack. The best-paying one if several.
- Bounties that ask for kills it leaves (what it kills isn't counted for its helper player). A board it has read it
  leaves for half a day. Its thoughts say what it took ("took a bounty: 4 flint for 2 emerald", "turned in the bounty")
  or what the board wanted ("nothing on it it can do now (... they want 2 lectern, 9 book ...)").
- Taking one marks it taken on the board for that Drifter (the board's own record, as its screen does; by reflection).
- Tested (headless, Bountiful in the dev mods): a board with a known bounty (4 flint for 2 emeralds): taken and turned
  in, 64 flint to 60, 8 emeralds to 10. Random boards mostly ask for several things at once (a librarian's books and
  lecterns, a cleric's nether wart): those wait until it has the lot.
- Not yet: gathering things for a bounty it can't fill now; bounties on Drifters from the board (the wanted posters
  above do that part, from our own messenger).
