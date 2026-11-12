# The chronicle revamp (RPG profile part 3)

Design, 2026-11-12. Part 3 of the user's "RPG profile" idea (1 the journal, 0.23.41; 2 the start-of-world menu,
0.23.42), after A10 parts 1 and 2 (0.23.43, 0.23.44) filled the world with history worth reading. The user's words are
quoted; anything marked **(builder's call)** is mine. The user gave creative freedom for the deeper pages ("you have
creative freedom").

## Why

The chronicle today is one long list of everything, a dozen kinds mixed with the same weight: someone moved house, a far
town re-elected its leader, a tax law, a trade count, a war, a death. It's read with `/drifterciv chronicle` or as a
written book, and each morning yesterday's lines are printed in chat. Since A10 it holds much more (far news, deaths by
name, far towns' votes), and none of it is told as a story. The journal (J) is where the player reads about their
world; the chronicle belongs there, as history, not as a log.

## What the user asked for

### A Chronicle bookmark in the journal (the user: "Your guess is right")

- **Only what a history book would keep:** wars begun and ended, towns taken, nations formed or fallen, leaders killed
  or overthrown, people gone for good, towns founded and ruined, the player's own big deeds. Not who moved house,
  routine re-elections, tax notices or daily trade counts.
- **Only what the player would know** (the rule below).
- **In words, grouped by time:** "This year", "Last year", "Before your time", not a wall of "Day 912:" lines. A
  nation's written past (the thousand days before day 1) sits at the back as its history.
- **An entry opens,** as a person or a town does on the other bookmarks.
- **The morning chat lines go away** (a setting, off by default).

### How news gets in (the user: "yes, that's the right rule")

- **You were there:** what happens in a town while the player is in it goes in at once.
- **A town tells you:** walking into a town adds what happened to it since the player was last there, and the far news
  that town has heard (its nation's wars, its enemies and allies, near neighbours).
- **Your own nation, as a citizen:** not by magic either; when next in one of its towns.
- **The written past:** a nation's history from before day 1 is added the first time the player sets foot in one of
  its towns. A citizen starts with their own nation's.
- **Your own deeds:** written as they're done.
- **Nothing else.** Each player has their own Chronicle.

### An opened entry looks like a front page (the user, of the mockup: "yea"; "would love to see images, like player heads, banners of the nation, (again everything vanilla)")

In the journal's own book, font and paper (the user's reference: a newspaper front page):

- a headline in capitals and a dateline (where it was heard, roughly when) between double rules;
- the banners of the nations involved, as the real banner items;
- player heads of the people named, from their own skins;
- a few lines of story in words, and how it stands now;
- a boxed quote from whoever told the player, in the words they said.

### And it opens further (the user: "you can have like an expanded EXPANDED entry, like quotes from interviews, biographies about people who fallen, how the war started, etc; you have creative freedom")

From a front page, further pages, each a click away and each with a way back:

- **How it began:** for a war, the quarrel that started it, the old grudge between the two (how many died between
  them before), who struck first and what their rulers are like.
- **The fighting:** its battles in order, where, and what each cost; towns that changed hands.
- **The fallen:** a head for each of those gone for good that the player has heard of; a head opens their page.
- **A life** (a biography): who they were in words (their nature, their work, where they lived and for how long, whom
  they led or followed), what they did that the chronicle kept, what they were to the player (a friend, a stranger,
  someone who once sold them bread), how they died and where they lie.
- **Voices:** what people said about it: the line the player was told, and a few more from named people of the towns
  that know, each in their own voice (a cautious one, a proud one, someone who lost a friend).
- **A nation's page:** its banner, its ruler's head, its capital and towns the player knows of, its wars and
  friends, and its past in a few paragraphs.

## Goals

- The Chronicle reads as the history of the player's world: few entries, each worth opening.
- Every entry is made from things that really happened in the game (A10's summary, the towns' ledgers, deaths, votes,
  the player's deeds): nothing invented that contradicts the world.
- The pictures are the game's own: banner items with the nation's real colours and pattern, heads from real skins.
- It costs nothing noticeable: built when the journal is opened, as the other bookmarks are.

## Non-goals

- Quests (part 4).
- A newspaper item, printing, or delivering it: the town gazette on its lectern stays as it is.
- Changing what Drifters know or say (this is the narrator's book; fairness rules about Drifters' knowledge don't
  apply to it, but the player's knowledge rule above does).
- Rewriting `/drifterciv chronicle`: it stays as the full log, for testing.

## Anti-goals (it's a failure if)

- The Chronicle shows something the player couldn't know by the rule.
- It reads like a log: numbers, "Day 912:", twenty entries about elections.
- A page shows a head or banner that isn't that person's or that nation's.
- Any art isn't vanilla's.
- An old world opens with an empty Chronicle for a player who's been everywhere **(builder's call: how much is
  filled in from the old log)**.

## Constraints

- Vanilla textures and font only; no AI-generated art. Words, not numbers, where a word will do ("three days ago",
  "last year"; a count of the dead may be a number).
- The journal is built on the server and sent as pages; client-only code stays in `src/civClient`.
- The addon reaches Drifter only through `com.dinglebob.drifter.api` (a dead Drifter's skin and a few facts for its
  life need to be on the world's record: added to the API in the same change, written in docs/API.md).
- Saved data versioned; numbers in named constants; stay out of the whim system.
- Works in multiplayer: each player's own knowledge.

## Left to the builder

- **Time in words (builder's call):** no seasons in the game, so "today", "three days ago", "last year" (a year: the
  town festivals' own length), "long ago"; groups "This year", "Last year", "Earlier", "Before your time".
- **What makes an entry and what's folded into one:** a war is one entry that grows (begun, its battles, towns
  taken, its end), not a line each; a town's quiet year isn't an entry.
- **Which facts make a life,** and how a nature is put in words.
- **Voices:** how many, who's picked, and that each line comes from the chat line data in that person's voice.
- **How pages are described to the client** (headline, rules, text, banners, heads, quote, links) and how far the
  book can be turned (a page's own pages, back).
- **Old worlds:** what's rebuilt from the flat log and A10's saved news.
- **The morning summary setting's name and where it sits** (Mod Menu); the written book command stays.
- Debug commands and the tests.

## Settled after the builder's check

The user's (asked together, answered "yes, those three are right"):

- **People who exist only on paper stay names:** a ruler of a town not built is a name, a battle's dead a count. A far
  war between towns not built shows banners, rulers by name and the story in words. Heads, lives and voices are for
  people of built towns (real Drifters); as towns are built, more entries get faces.
- **A first visit tells about the last hundred days** of what the town knows, and its nation's written past. Older
  things the player missed stay unknown, but for a line in that past.
- **Nobody has to say it in chat for it to go in.** Entries go in quietly as the player walks in; a line or two is
  spoken, as now. A front page's boxed quote is there only when someone really said it to the player. The Voices page
  holds remarks from named people of the town who were there when the player learned of it, in their own voice,
  picked once and kept.

The builder's calls:

- **Banners are one plain colour,** as nations have today (the mockups' patterns were decoration). Patterned nation
  banners would change the banners standing in towns too: a small job of its own, later.
- **A free town shows its leader's head** where a nation would show its banner.
