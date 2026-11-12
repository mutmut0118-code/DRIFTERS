# Quests (RPG profile part 4)

Design, 2026-11-12. The last part of the user's "RPG profile" idea (1 the journal 0.23.41, 2 the start-of-world menu
0.23.42, 3 the chronicle revamp 0.23.47). The user's words are quoted; anything marked **(builder's call)** is mine.

## Why

The world already asks things of the player, in three unconnected places: a friend asks a favor (iron, food, clear
the zombies), a town's board asks for goods and pays, and a town can put a price on a head. None of it has a reason
the player can read, none of it comes from what's happening in the world, and it's scattered over two bookmarks and a
board. The journal knows who the player is, the Chronicle knows what has happened; quests are what the world asks the
player to do about it.

## What the user asked for

### What a quest is (the user: "Your guess is right")

- **One Quests page in the journal** that gathers what's asked of the player: what, by whom, where, what they'd get,
  how it's going. Favors and notices show there instead of being scattered.
- **Quests come from what's happening in the world,** not from a fixed list: a town at war asks for iron or for word
  to be carried; a town that lost its smith asks for one to be found; a grieving friend asks for what the dead
  carried; a leader asks for a crook to be dealt with.
- **Offered in person, in words.** Nothing over anyone's head; the player can say no.
- **Real stakes and real rewards:** emeralds or goods the giver actually has, standing with the town, a closer
  friendship; failing or ignoring it costs something with that person; a time limit where it makes sense.
- **Written into the Chronicle** when it's a big one, as one of the player's deeds.
- **Not** a main storyline, experience points, levels or map markers.

### The first set (the user: "yes, that's the right first set")

- **Bring something:** a town or a person needs goods (iron in a war, food after a long one, wool, tools). What
  notices and favors already are, with reasons.
- **Carry word:** take a message or a parcel to a named person in another town; done when it's handed over.
- **Find someone:** a town that lost its smith or its guards asks for a newcomer; done when a Drifter the player
  brought moves in.
- **Deal with someone:** a leader wants a crook, a bandit or a wanted person dealt with; done when the town hears
  of it.
- **See to the dead:** a grieving friend asks for what someone carried to be brought back, or for their grave to be
  visited.

Left for later: escorting someone along a road, fighting in a war for a town, building something for a town, chains
of quests.

### Getting one, and answering (the user: "yes, that's how it should work")

- **Someone asks, out loud,** when the player is near and they think well enough of them: in chat, in their own
  voice. A leader asks for the town; a friend for themself.
- **It appears in the journal as "Offered":** who asked, what for, what they'd give. Nothing promised yet.
- **The player answers either way:** yes or no to them in chat, or "Take it" / "Not for me" on the journal page.
  Walking away lets the offer lapse after a day or so, at no cost.
- **The town board still works:** its notices are open offers anyone can take by doing them; they show on the page
  once the player has read the board or been told.
- **Not too many at once:** a few open quests at most (five); a person doesn't ask again while the player owes them
  one; a wary town doesn't ask; a town the player is wanted in doesn't.
- **Strangers ask small things; friends and the player's own nation ask big ones.**

### Rewards and failing (the user: "yes, that's right")

- **Rewards are real things the giver has:** emeralds from the town's till or the person's pockets, goods from their
  stores, nothing conjured. An empty till: the town says so and offers less, or something else.
- **Standing and friendship as well,** sometimes the main reward, said in words on the page.
- **Bigger asks, bigger thanks;** a big one is written into the Chronicle as a deed.
- **Paid by the person, in person:** the player goes back to whoever asked (or the town's leader). If they've died
  since, their town honours it or the quest quietly ends, by who was asking.
- **Time limits only where the world has one:** iron for a war while the war is on; a message goes stale; wool for
  a notice doesn't expire quickly.
- **Failing costs a little; breaking one's word costs more.** Letting a taken quest run out makes that person or
  town think a bit less of the player; saying "I can't do it after all" to their face costs less than vanishing;
  declining an offer costs nothing.
- **No quest makes the player do wrong by accident:** if it would set a town against them, the page says so before
  they accept.

### The page (the user, of the mockup: "yes, that's the right shape")

- **A Quests bookmark replaces Favors** (a friend's favor is a quest from a friend); the book stays at five
  bookmarks.
- **The list is grouped by where each stands:** Offered, Your word given, Done. To the right: who asked, or how long
  is left.
- **A quest opens like a notice,** across both pages, in the Chronicle's style: a headline, where and when it was
  asked, who asked (their head, their nation's banner), the words they said in a box, why they're asking, exactly
  what's wanted (the vanilla item's icon, how many the player carries), what they'd get and where it comes from, how
  long they have.
- **"Take it" and "Not for me"** at the foot of an offer; once taken, "I can't do it after all".
- **Finished quests stay under Done for about a year,** then drop off; the big ones live on in the Chronicle.

## Goals

- Every quest has a reason from the world as it is, and a giver who would really ask.
- The game can tell fairly that it was done: by things that really happen (goods handed over, a Drifter moved in,
  word reaching the town), never by a flag the player could set without doing it.
- One place to see everything asked of the player.
- The three things that exist (favors, notices, a price on a head) keep working and become part of it.

## Non-goals

- A story, chains, experience, levels, map markers, quest markers over heads.
- Escort, fighting in a war, building for a town (later).
- Quests between Drifters (they have their own lives; this is what's asked of players).
- Changing how a notice or a favor is done (throwing the goods to the person): only how it's offered, reasoned and
  shown.

## Anti-goals (it's a failure if)

- A reward appears from nowhere, or a giver pays what they don't have.
- A quest can be finished without doing it, or can't be finished though it was done.
- The player is nagged: asked every minute, or by everyone at once.
- A quest quietly makes the player a criminal somewhere.
- The page shows a quest the player was never told of.

## Constraints

- Fairness rules (DESIGN.md section 1): givers know only what they'd know (a town knows its own needs; a friend
  knows who died if the town does); rewards are real items moved, not made.
- Vanilla art only (item icons, heads, banners as in the Chronicle); words, not numbers, where a word will do (a
  count of goods is a number).
- The journal is built on the server; client-only code in `src/civClient`. The addon reaches Drifter through
  `com.dinglebob.drifter.api` (asking a yes/no in chat, having a Drifter come along: what's missing is added to the
  API in the same change and written in docs/API.md).
- Each player has their own quests; a notice on a board stays open to everyone.
- Saved data versioned; numbers in named constants; stay out of the whim system.
- Old worlds: favors under way and notices up carry over.

## Left to the builder

- **How often and who asks:** within "not nagged": about one offer a day at most across a town, fewer for a
  stranger.
- **What each kind needs to be offered,** from what the game tracks (a war on and the town's stores short; a line of
  work nobody holds; a crook with offences; a friend's fate known to the town).
- **Carry word:** what the parcel is (a written book or paper the giver really has), and who it's for.
- **Find someone:** how a Drifter is brought (the main mod's "come with me"), and that they must want to move in by
  the town's own rule.
- **Deal with someone:** what counts (killed, driven off, brought to justice), how the town hears, and the warning
  when it would count as a crime elsewhere.
- **See to the dead:** what "what they carried" is when it was lost far away (a keepsake at the grave, or the
  visit alone).
- **Amounts, time limits, what standing changes by,** and which quests are big enough for the Chronicle.
- **How yes/no in chat and the journal's buttons meet** (one answer either way).
- Debug commands and the tests.

## Settled after the builder's check

The user's (asked together, answered "yes"):

- **A leader's ask makes it lawful in that town, for that person only:** killing or driving out the crook they named
  is no crime there. Other towns that liked the crook may think less of the player; the page says so before they
  accept.
- **Two stages.** Stage 1: the Quests page, bring something (favors and notices with reasons, a war's and a
  shortage's asks), carry word, see to the dead. Stage 2: find someone, deal with someone (new doors into Drifter and
  the change to justice). The user plays stage 1 while stage 2 is built.

The user's, added then ("make it compatible with numismatic overhauls when it comes to giving emeralds; it should
also be a very big sum; the whole economy should center around NO if its installed"):

- **With Numismatic Overhaul installed, a quest pays in its coins, not emeralds.** Drifter's money already does this
  for trading and shops (`Money`, milestone 22: bronze, silver, gold; found at runtime, never required): rewards go
  through the same door.
- **The whole economy centres on those coins when the mod is there:** every place the addon still counts raw emeralds
  (a town's till, tax, wages, tribute and the price of a peace, fines, notices' pay, what a ready-made town's chests
  hold) deals in coins instead. **(builder's call: this is its own piece of work, done first, as stage 0, since quest
  rewards are paid from the same tills.)**
- **"A very big sum":** to be asked: see the question in chat.

The builder's calls:

- **"Deal with someone" only ever targets Drifters** (crooks, bandits, wanted Drifters), never another player. A
  price on the player's own head stays on the Law page.
- **Board notices stay no-strings:** listed in their own group ("On the boards"), not counted toward the five, no
  cost for ignoring one. A leader asking in person for the same goods is the same quest.
- **"Find someone" means a wanderer with no home,** who takes up the vacant work. Someone who lives in another town
  says no.
- **"Clear the zombies" stays** as a sixth kind.
- **A friend's favor still pays in friendship,** and letting one lapse after a yes still costs a little trust, as
  now. Towns and leaders pay money.
