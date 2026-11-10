# D2 friends: design

Drifter: Civilizations, milestone 23, phase D, step D2 (docs/LIVED_WORLD.md section 7). Agreed with the user 2026-11-10,
question by question. Version: the next free main-line number (0.23.37 at the time of writing).

## Intent

The user's picture: you come back to town after a long trip and your friend spots you, comes over, says hi by name, asks
how it went last time, and if it's evening asks you in or offers food. **It notices you in particular**, not players in
general. A friend in a Drifter town should feel like a friend on a long-running server, not an NPC with a friendship
meter (LIVED_WORLD.md "The rule that keeps it player-like"; decision 3: "friendship with real perks... no romance").

## What's already there (Drifter, not to be rebuilt)

At trust 25 (`Mode.FRIENDLY_TRUST`) a Drifter already greets a player (`FriendlyGoals.Greet`), helps fight mobs
attacking them (`HelpFight`), warns of a creeper behind them (`WarnOfCreeper`), pays back gifts (`PayBack`), follows on a
crouch (`FollowMe`); at 60 it's an ally (`Mode.ALLY`). Trust drifts back toward its baseline each day by Forgiveness, and
grudges fade (`Relationships`). Requests from a player in chat (`RequestGoal`), telling and asking about places
(`Telling`, `Heard`), plans made in talk (`Together`). D2 builds on these and changes none of their behaviour for a
Drifter that isn't a friend.

## Goals (all agreed)

0. **Stages, Sims 4 style** (the user, 2026-11-10: "we should also make friends by conversating and thatll make an
   aquaintance kind of like how drifter does w/ another drifter; small talks and responding to their opinions and
   agreeing on stuff; think of like a Sims 4 system"): **Stranger → Acquaintance → Friend → Best friend**, each opening
   more.
   - **Stranger:** small talk only ("sup", the weather, last night's mobs); short talks.
   - **Acquaintance:** you've talked a few times and it knows your name; it shares opinions and hot takes and asks what
     you think; it greets you; talks run a bit longer.
   - **Friend:** everything in goals 1 to 7 below (the announcement, the perks, favors); personal talks too (venting,
     stories, what it's been up to).
   - **Best friend:** the longest talks, inside jokes about things you did together, its secret diamond spot, and it
     backs you even against its own town over small stuff. About twice as long to reach as a friend; only the very
     sociable let more than a couple of players that close (my guess, agreed).
   - **Conversation builds it, as between two Drifters** (`Conversations`): you talk in chat as on a server (no menus).
     It opens talks with you (more often the closer you are), and you can open them ("hows it going", "what do you think
     about the nether?", "you like creepers?"): it answers from the same topic web it uses with Drifters and goes on if
     you reply.
   - **Agreeing and disagreeing:** agreeing with its opinions warms it most. Disagreeing depends on its nature: most
     barely mind a polite "nah", argumentative ones (hot-headed, the debaters and ragebaiters of the topics) enjoy a
     back-and-forth as long as there's no insult, thin-skinned ones take it harder. Insults always cost (as now).
   - **Shared opinions remembered:** what you agreed on ("we both hate phantoms", "you're a pickaxe-only guy too") is
     kept and brought up later; shared opinions are what move an acquaintance to a friend.
   - **Less and less from spam:** a few good talks spread over days count for more than an hour of chat in one go.
   - **Mood:** hungry, hurt, grieving, mid-fight or busy, it brushes you off ("not rn"): costs nothing, counts nothing.
   - **Seeing where you stand:** `/drifterciv friends` shows each one's stage; hovering its name in chat shows it too
     ("your friend"); reaching friend and best friend each get their own line in chat ("ok youre officially my best
     friend lol").
1. **Friend is a status, reached and announced.** Not a hidden number: once its trust in you is high and it has spent
   real time around you, it says so once in chat in player speak ("ngl you're alright, come by mine whenever"). From then
   on it's your friend. `/drifterciv town` lists your friends in that town (and a command lists them all; see below).
2. **Pace: a few in-game days of being decent** (about two or three play sessions round a town): trading, gifts, help in
   a fight, chatting, doing the town's notices. One big deed (saving its life) skips most of the wait. Easygoing natures
   get there sooner, guarded or grumpy ones later. Not one evening of gifts; not weeks.
3. **Who:** any Drifter — townspeople, wanderers, camp folk, miners met out in the world — **except outpost Bandits and
   delvers** (the ones hostile on sight; the user). The home-bound perks (invited in, a spare bed, a friend's price) only
   when it has a house or a stall. A friend who moves into a town keeps the friendship.
4. **Perks** (all seven agreed):
   1. **Comes over when you get back** (after a while apart), says hi by your name (what it calls you: `Heard`), and asks
      about last time ("you find any diamonds?", "you survive that cave?") from what it knows of you (what you told it,
      what it saw, news of you).
   2. **Food when you're low:** it sees you hungry and tosses you some of its own spare food. Only when you're actually
      low, not a stream of free things.
   3. **Invites you in** at night or in a storm ("come in, it's pouring"), walks you to its door; and its spare bed (a bed
      in its house nobody of its household sleeps in) is yours for the night, said so.
   4. **Tells you what it knows, in a whisper:** diamonds and other ores it has seen, a cave, a dungeon, a mineshaft,
      bandits up the road, a dangerous town. Only from its real memory (perception rule). Coordinates typed like a player:
      careless or chatty natures exact, others rounded or a direction and a landmark ("somewhere north past the lake, deep
      down"). It doesn't give away its own unfinished diamond spot unless it trusts you very much.
   5. **A friend's price** at its stall or shop (cheaper than "welcome").
   6. **Backs you up in fights** against Bandits and hostile Drifters too, not only mobs.
   7. **Warns you, in a whisper, when you're in trouble with its town** ("dude the guards are looking for you").
5. **Favors, both ways:** now and then (about one ask every in-game day or two) a friend asks you for something a server
   friend would: help clearing zombies by its farm, some spare iron ("i'll pay you back"), mining together tomorrow.
   Yes and done: the friendship grows. No: nothing lost. Yes and never shown up: it cools a little ("you said you'd come
   lol").
6. **Endings:**
   - Small trouble with its town (theft, a fine, horse theft, killing the town's animals, a false alarm): still your
     friend; it won't join in going after you and warns you; a little disappointed.
   - Big trouble (murder in its town, its leader's murder, a massacre): over; it treats you as its town does (kill on
     sight included), but a coward stays away instead.
   - Big trouble in another town (wanted in its town only because word spread, e.g. a leader of its nation murdered
     elsewhere): not over, but cooled to "used to be friends": it won't hunt you or help you, and whispers something
     ("bro what did you do in stonegate"). It ends only for big trouble in its own town or against its own people (the
     user, 2026-11-10).
   - Wronging it or its own people (hitting it, stealing from it, killing a close friend of it or one of its household):
     over at once, with the grudge it already holds.
   - **Apart about ten in-game days:** cools to "used to be friends": still warm and knows you, the perks stop, and a little
     time together brings it back quickly.
7. **Whispers:** private lines to one player show like a real `/msg` ("FrostByte whispers to you: diamonds at like -340
   12 220"), only that player sees them, and no Drifter overhears (the user's idea). Used for tips and warnings; other
   friend lines stay in open chat.

## Settings: a Friendship page of its own, with presets (the user, 2026-11-10)

"you should make a seperate option category for friendship because i want this part to be customizable, with presets
aswell". Drifter: Civilizations gets **its own settings screen** in Mod Menu (the addon's Configure button), saved in the
addon's own config file (not Drifter's), with **Friendship** as a page of it. (Today the addon's few settings live in
Drifter's `DrifterConfig`; they stay there for now.)

- **Presets** (one sets every slider; touching any slider shows "Custom"):
  - **Relaxed:** friends in about a day, generous perks, slow to cool.
  - **Normal** (default): this design: a few in-game days, cools after ten days apart.
  - **Slow burn:** about twice as long, best friends rare, smaller perks.
  - **Hardcore:** hard won, cools fast, favors and wrongs count more.
- **Settings:** friendships on/off; how fast you get closer (0.25 to 4 times) and how much harder best friend is; days
  apart before it cools (5 to 30, or never); how much agreeing, disagreeing and spam count; each perk on/off (greeting
  when back, food, invites and the spare bed, tips and how often, backup in fights, trouble warnings); the friend's price
  discount (0 to 40%); favors on/off and how often; tips and warnings whispered or said aloud; chat lines on moving up a
  stage on/off; who can be friends (everyone, or only townspeople).
- The same settings by command (`/drifterciv config friendship ...`), as Drifter's settings have them.

## Non-goals

- No romance or marriage (decision 3). No companions on trips yet: that's D3 (a friend "coming along" beyond today's
  follow and plans is D3's).
- No separate friendship meter beside trust: the stages sit on the trust, familiarity, grudges and deeds Drifter
  already keeps, plus what's new for talk with players (agreeing, shared opinions, less from spam).
- No Sims pie menu or any screen: talk is typed in chat.
- No limit on how many friends you have (my call; it's slow anyway).

## Anti-goals (would make it a failure even if it works)

- A vending machine: free food, gifts or tips on demand or every few minutes.
- A quest log: favors piling up, markers, or asks every few minutes.
- Knowledge from nothing: a tip about a place it never saw or heard of.
- NPC barks or corny lines ("Greetings, friend!"); every line player speak (the user, 2026-10-28).
- Friendship surviving a massacre of its own town, or ending over a fine.

## Constraints

- Fairness rules (DESIGN.md section 1): tips from memory only; food from its own pockets; reach and player time for
  tossing and walking; the spare bed a real bed in its house.
- The addon works through `com.dinglebob.drifter.api`; what it needs that Drifter lacks (whispers, reading its
  memory of ores and places, familiarity with a player, what it calls a player, backing a friend in a fight, a
  friendship hook in Drifter's own modes) is added there and to docs/API.md in the same change. The weapons-and-armor
  session (branch claude/drifter-chat-expansion-574ebe) also grows docs/API.md: add D2's lines as their own block so a
  merge keeps both.
- Saves versioned: friendship state is the addon's own data on a Drifter (a Fabric data attachment, versioned).
- Settings: see "Settings" above (Mod Menu stays optional: without it, the commands).
- Lines in data files (the addon's `drifter_chat`/`drifter_talk` content), player speak, several variants each.
- Performance: a check per Drifter about once a second at most for the "near a friend" perks; nothing per tick.

## The builder's calls (left open, with my suggestions)

- **Acquaintance:** a few talks and its knowing your name (familiarity about 10, trust above 0; my call).
- **Talk with players on the topic web:** the player's reply read for agree / disagree / insult / question (as
  `PlayerTalk` reads intents today, `Lines.answer`), the Drifter-to-Drifter topics (their `agree`/`disagree` marks and
  `t:` conditions) played with the player as the other side where they fit; which topics suit which stage (small talk,
  opinions, personal) from the topic files' kinds. Shared opinions kept per player (the topic and the side), a few dozen
  at most. Built in the addon (this session's rule: Drifter is touched only to grow the API): Drifter grows hooks only, e.g. an
  event when a player says something to a Drifter (with what it means), a way to play a topic with a player as the other
  side, familiarity read and changed, whispers, memory of places read. New topic lines go in the addon's own data.
- **When it becomes a friend:** trust at least about 50 and familiarity at least about 30, both shifted by its nature
  (sociable, forgiving, trusting ones lower; paranoid, grumpy, vengeful ones higher); saving its life (`SAVED_IT`) counts
  big as it already does. Tune in a sprinted test to land at "a few in-game days".
- **Where the status lives:** per Drifter, a small record per friend player (since when, state FRIEND / COOLED / ENDED,
  last together, favors owed, last tip, last food); read by the town's commands and reputation.
- **Cooling:** ten days since it last saw you (`Relationship.lastSeenAt`); back to friend after some minutes together
  if trust is still high.
- **Endings:** listen to `PLAYER_DEED` (its own wrongs) and the addon's crimes (`Justice`): the kinds above. "Close
  friend of it" = a Drifter it trusts highly (`DrifterBonds`) or in its household.
- **Friend's price:** a step beyond "welcome" (e.g. 0.8 times) in `Market.price` for its own stall/shop.
- **Backing up in fights:** if Drifter's own `HelpFight` already covers attackers of any kind, widen it for friends
  (through the API); otherwise an addon goal using Drifter's attack task.
- **Kill-on-sight and friends:** a friend never joins a manhunt or "wanted" pursuit over small crimes; `TownWork.standing`
  / `killOnSight` answers per Drifter, so a friend can answer differently from its town.
- **Spare bed and coming in:** whether sleeping in a friend's bed is already harmless (no trespass rule exists today);
  the invite walks to its door and opens it.
- **Favors:** from what it actually needs (its wants, its farm's mobs, its next mining trip), using the plans in talk
  (`Together`) and requests where they fit; one open favor at a time.
- **Whisper look:** the vanilla "commands.message.display.incoming" style (grey italic), with the hover name as other
  Drifter lines have.
- **"Back after a while"** (perk 1): not seen for at least about an in-game day (my call); once per return.
- **Tips** (perk 4): both unprompted now and then (no more than one every in-game day or two per friend) and when you ask
  ("where are diamonds?", Drifter's `Telling` asks), whispered for a friend, as now for anyone else (my call).
- **Commands:** `/drifterciv friends` (yours, everywhere you have them: who, where, since when, cooled or not); debug ones
  to make or end a friendship and to fire each perk.

## Testing

In the dev client (a town, a player made friends with by the debug command and by real play in a sprinted run) and a
headless smoke run: the announcement, each perk seen once, the friend's price, a whisper (only the player sees it),
cooling after ten days, each kind of ending. TESTING.md: plain steps for the user.
