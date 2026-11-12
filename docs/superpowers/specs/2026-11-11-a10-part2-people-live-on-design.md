# A10 part 2: a built town's people live on while you're away

Design, 2026-11-11. Follows A10 part 1 (0.23.43, docs/superpowers/specs/2026-11-11-a10-history-never-stops-design.md).
The user's words are quoted; anything marked **(builder's call)** is mine and can be changed.

## Why

Part 1 made the world's history go on everywhere: nations far from the player fight, and a built town can change
hands. But inside a built town nobody's near, everything still stops. Its people freeze, and on return each catches up
on at most ten days of eating and crafting. A "death" while away only empties their pockets. Nobody arrives, no vote is
held, no tribute paid. The people a town lost in part 1's paper wars are only a number nothing reads.

The user plays with permanent death. For the world to feel lived in, coming back to a town after a hundred days has to
show that a hundred days went by for the people in it.

## What the user asked for

Coming back to a town not seen for a long time (the user: "Your guess is right"):

- **Some faces are gone:** those who died in the wars the town fought on paper, really dead, named.
- **Some faces are new:** newcomers in the empty beds.
- **The leader may have changed,** by a vote or a rising, and the town says who and why.
- **Those still there are up to date:** fed, equipped, stores gone up and down; a friend is still a friend.
- **Nobody changed in ways you couldn't explain:** no one suddenly rich or in diamond.

### Who can die (the user: "yes, that's who should be able to die; leaders can die as well but very rare (like an assassination)")

- **Fighters first:** guards and those who'd march to war die in battles; ordinary folk only seldom, when the town
  itself is fought over.
- **Friends get no protection.** A friend or best friend of the player can be among the dead.
- **A leader doesn't die in battle,** but can die, very rarely, by an assassination: its own event, named in the
  chronicle, a new leader chosen after.

### Death is real, by the world's own rule (the user: "it should be a different system because i will be playing with perm death, so while making this account for all death types (respawn, lives, permanent)")

A death on paper is a real death for that Drifter, replacing today's stand-in (pockets emptied) for people of a town:

- **Respawn:** they died and came back at their bed; they've lost what they carried and remember dying.
- **Lives:** it costs one life; one on their last life is gone for good.
- **Permanent:** gone for good.

### Newcomers (the user: "yes, that sounds right")

- **Slow:** an empty bed draws a new person after a while, about one newcomer per town every ten to twenty days;
  faster in a town at peace and doing well, none in a town at war or unhappy.
- **Strangers, not copies:** a new name, face and nature; they take the empty house and its line of work (a dead
  smith's forge gets a new smith). They don't know the player.
- **A town can still die out:** losing people faster than they arrive, it shrinks; down to nobody, it stands empty
  (what becomes of its buildings is part 3).
- **The same in every death setting:** beds emptied by those gone on their last life, or who left, refill too.

### What else goes on (the user: "yes, that's the right amount")

A day at a time, while nobody's near:

- **Leaders change:** votes held on their dates among the people the town has; an unhappy town can rise up and throw
  its leader out.
- **Money moves:** taxes come in, tribute is paid to the capital, a town that can't pay falls behind.
- **Stores go up and down within reason:** eaten and harvested; stocked after peace, short after a long war.
- **The living are up to date:** fed, equipped as their work would have them; friendships with the player as they were
  left (not cooled for the player's absence).

Not played out, only brought up to date on arrival: trade runs, bounties, the gazette, festival speeches. No crimes or
feuds between townsfolk on paper (the leader's rare assassination aside).

### How you find out (the user: "yes, that's how i should find out")

No message on the screen, as in part 1.

- **The town tells you:** its people mention a death in their own words, more so to a friend of the dead.
- **The journal marks it:** a friend's line says they died, where and of what, once the player is in a town that
  would know; until then it shows them as last known.
- **The chronicle names them:** "Marrow of Hollowbrook died fighting near Highcrest", not a count.
- **A grave in the town** for anyone gone for good: a simple marker with their name by the town's edge. None for
  those who came back (respawn, lives left): they tell of it themselves.
- **Newcomers** are mentioned by the town's people; the journal lists them once met.

## Goals

- A built town nobody's near has a day, every game day, from saved data alone, cheaply.
- What its days decided is true the moment a player can see it: the dead aren't standing there, the newcomers are, the
  leader is who the town says.
- All three death settings behave as they do for a death in front of the player.
- One truth: part 1's losses (`paperLost`) become these deaths; nothing is counted twice.

## Non-goals

- Buildings changing: new houses, burned ones, walls, a dead town turning to a ruin (part 3).
- Making far towns cheaper to keep loaded (part 4).
- Trade runs, bounties, crime between townsfolk played out on paper.
- Births, ageing, families.
- Drifters who aren't of a town (wanderers, camps): they catch up as today.

## Anti-goals (it's a failure if)

- A Drifter the summary called dead is seen alive, or one seen alive is listed dead.
- A death on paper gives or destroys items in a way a real death wouldn't (fairness: no free items).
- A town is found empty after a normal stretch of peace, or never loses anyone in a long war.
- The player is told on screen.
- A world from 0.23.43 or before opens with towns suddenly emptied: the count part 1 kept is applied gently
  **(builder's call: how)**.

## Constraints

- Fairness rules (DESIGN.md section 1). A newcomer carries a starting kit as any new Drifter does; the living are
  equipped by the existing catch-up (what they could have gathered and made), not handed gear.
- The addon reaches Drifter only through `com.dinglebob.drifter.api`; what's missing (a real death for an unloaded
  Drifter by the world's rule, lives left, turning off the generic catch-up death for a town's people) is added to the
  API in the same change and written in docs/API.md.
- "Away" is part 1's: no player (spectators don't count) within the town's land + 192 blocks. In front of a player the
  town's own live systems decide, as today.
- Only vanilla blocks and art; words, not numbers, in anything the player reads.
- Saved data versioned; tunable numbers in named constants.
- Stay out of the whim system.

## Left to the builder

- How a town's day on paper is kept (a ledger on the town of what's pending for whom) and when it's applied (as the
  town comes into range, before anyone can be seen; a Drifter loaded later gets its share as it loads).
- Who counts as a fighter from saved data; the odds of an ordinary person dying, of an assassination, of a rising.
- The newcomer pace within "one every ten to twenty days, faster in good times, none at war or unhappy".
- How votes are held among people who aren't loaded (from the directory's records: nature, standing).
- What a grave is made of and where the graveyard goes, so a Drifter could have built it.
- How much of the living's catch-up beyond today's ten days is worth doing.
- Debug commands and what the headless and client tests cover.

## Settled after the builder's check

The user's (asked together, answered "yea"):

- **People can leave a town on paper, without dying:** seldom, as they do in front of the player (fallen out,
  restless, grieving). In a respawn world it's the only way a far town loses anyone. The town says they moved on; the
  journal says "left Hollowbrook", not "died".
- **Graves and "the town tells you" are for every townsperson gone for good, from this version on,** in front of the
  player or away. Not backdated. An assassinated leader gets a grave as well as the leader's monument towns already
  build.
- **An assassinated leader loses the office in every death setting.** With respawn or lives left they come back as an
  ordinary townsperson, and a vote is held.

The builder's calls:

- **Only the summary's wars kill on paper in this part.** Wars between Drifter-founded towns, nations formed in play,
  or in a world with no plan cost nobody while the player's away; those towns get leavers, newcomers, votes, money and
  stores only.
- **What the dead carried is lost; what's in their house stays with the house.** A newcomer who takes the house and
  its work finds its chests as they were (the town's property passing on).
- **The ten-to-twenty-day newcomer pace is for towns the player is away from.** In front of the player, arrivals keep
  today's pace.

## Settled while building (0.23.44)

- **Taxes aren't collected on paper.** A person not loaded has pockets nobody can count, so collecting would make
  emeralds. What's owed adds up as before and is paid when they're loaded. The till itself does pay and receive its
  dues on paper, out of what it was last seen to hold.
- **Friendships:** D2's own cooling (days apart, a setting) is unchanged; a town's days on paper cool nothing more.
- **The living catch up as before** (ten days at most of eating, gathering and gear).
- **Losses part 1 counted before this version are let go.**
- **A fighter is a guard** (the town's line of work); 85 in 100 of a battle's dead are guards while it has any.
- **A grave** is a mossy cobblestone block with an oak sign on the side facing the town and coarse dirt before it,
  in rows of four, in a patch found on open level ground just outside its houses; leaves over it are cut back. It's
  put up by the game the morning after, not built by a Drifter's hands.
- **Told on walking in:** the latest three things since the player was last in the town, one at a time, by whoever of
  its people is nearest; someone who died and came back says so themself.
