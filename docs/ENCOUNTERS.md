# Encounters and bounties (milestone 21, planned)

The user (2026-10-20), on Bountiful compat: "different drifters also have bounties on them aswell. like imagine i check
the bounty board and its a map to a drifters house (or something like that) lets say and the reward is super valuable.
i kill the drifter, collect the bounty. can also happen if bounty hunters are looking for me. i could be chilling at my
base, or going somewhere, and suddenly a group of armed bounty hunters are here to kill me."

And: "an 'Encounter' system, where random events happen with temporary drifters you will never see again (think of it
as like the random event system in the GTA franchise)."

Saved for a milestone of its own: "save all of this for a mile, we will do this soon". Nothing is built yet. The list
below is the brainstorm; the user picks what goes in.

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
