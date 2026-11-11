# The player's journal (profile screen): design

Drifter: Civilizations (the addon: `src/civ`, `src/civClient`). Part 1 of four of the user's "RPG profile" idea
(2026-11-11). Everything here was decided with the user in chat unless marked **(builder's call)**.

## The whole idea, and its parts

The user: "while creating a new world and the town plan is loading we get a menu, picking our name and pronouns, and a
nation we want to be a citizen of, or no nation at all. and then when it loads we kind of get our own profile showing us
what nation we are in, our relationships with other people, our global or regional reputation, if we are wanted or have a
huge bounty, maybe quests or favors that we have to fulfill for other drifters, and the chronicle could be in that profile
too ... kind of giving it the rpg element and vibe with the whole thing."

Asked what matters most: "all the data about my player with other drifters/myself that is only accessible doing
/drifterciv commands easily displayed in a ui thats immersive and faithful to vanilla: my reputation, my relationships,
my crimes, my favors, my nation that i live in, etc".

Agreed order (the user: "yes, profile screen first"):

1. **The journal** (this document): the profile screen.
2. The start-of-world menu: name, pronouns, nation (its own design later; adds the nation line to the journal).
3. The chronicle revamp: only noteworthy events and history, read in the journal (adds a Chronicle bookmark).
4. Quests: new jobs from Drifters, tracked in the journal.

## Why

What a player has done and who they are to the world is real and saved (friend stages, town standing, crimes, wanted,
bounties, favors), but it can only be read a piece at a time through `/drifterciv` commands, as chat text. One screen
that gathers it makes the world feel like an RPG with a character sheet of your own.

## Goals

- One key press shows everything the world holds about *you*, readable at a glance.
- It looks like something vanilla could have shipped.
- It feels like your own journal: what you'd know, in words.

## Not in this part

- The nation line, the Chronicle bookmark, quests, name and pronouns: parts 2 to 4. Nothing half-finished shows: no
  empty "Nation: none" line, no Chronicle bookmark, until their parts are built.
- No new game systems. The journal only shows what already exists. (One small new thing is saved: which towns a player
  has been to, see "Towns".)
- The `/drifterciv` commands stay as they are.

## Anti-goals (what would make it a failure)

- New art. The user: "this should be made only using vanilla resources, no ai generated art". Only vanilla's own
  textures, sprites and font; flat colours drawn by code where vanilla has no texture are fine **(builder's call)**.
- A cheat sheet. No exact numbers (trust scores, opinion values), no people you've never met, no towns you've never
  been to or heard of. The user: "yes, only what i'd know, in words".
- Looking like a mod settings page.

## What it is

### Opening it

A key, **J** by default, rebindable in Controls (category "Drifter: Civilizations"). Pressing it in a world opens the
journal; J or Esc closes it. The game doesn't pause (like the inventory) **(builder's call)**. On a server without the
addon the key does nothing.

### The look (the user picked "A combined with the look of B", then "nothing, i like this one")

An open journal: two paper pages side by side, in the vanilla book's paper look and the vanilla font in the book's dark
ink, with **bookmark tabs along the top** of the book, one per subject. The chosen bookmark looks joined to the page.

- **Left page: always you.** Your name, your skin (the player drawn as in the inventory screen), your overall
  reputation in words, and, when it applies, where you're wanted and the bounty on you (in red ink).
- **Right page: the chosen bookmark's subject**, as a list that scrolls (mouse wheel) when long. A line can be clicked
  for more about it; the detail takes the right page, with a way back.

How the pages and bookmarks are drawn from vanilla's textures (the book page texture twice, the creative inventory's or
advancements' tabs, or flat fills) is left to the builder; it must hold at the smallest window and every GUI scale
**(builder's call)**.

### Bookmarks in this part (the user: "yes, that's the right cut")

1. **People.** Everyone you've met: Drifters with a friendship record with you, or who feel something about you.
   Each line: name, and how they stand with you in words: stranger, acquaintance, friend, best friend; "used to be
   friends" (cooled); "fell out" (ended), "holds a grudge" for someone who has come to distrust you; "gone" (dead for
   good). Closest first. Clicking one: their town (as last known to you), where you last saw them and how many days
   ago, how long you've been at this stage, why it cooled or ended, whether you saved their life, and any favor
   between you.
2. **Towns.** Towns you've been to, and towns you've only heard of (a friend's town, a town that wants you because word
   spread): name, its nation if it has one, and where you stand: welcome, neutral, wary, unwelcome, wanted. A town
   only heard of says so, and shows a standing only if that's why you heard of it (wanted there). Clicking one: its
   size (homestead, town, city), where it is, your friends and acquaintances there, and your record there (below).
3. **Law.** Your record, town by town: what you did (as the town knows it), how many offences, a fine still owed, exiled
   or wanted and for how many more days, and the price on your head if there is one. A clean record says so in a line.
4. **Favors.** What friends have asked of you: who, what, how far along (12 of 16 bread), how many days left, and
   whether you said yes. And whatever is owed to you the same way, if the game holds any such thing; if it doesn't,
   this page is only what you owe (no invented debts) **(builder's call, from the code)**.

Empty pages get a plain line in the journal's voice ("Nobody yet. Go say hi.") **(builder's call on wording, player
speak like the rest of the mod)**.

### Overall reputation, in words

From your standing in the towns you've been to: for example "well liked", "known here and there", "nobody knows you
yet", "not trusted", "an outlaw" (wanted anywhere). A short second line counts it out: "welcome in 3 towns, wary in 1".
The wording and thresholds are the builder's **(builder's call)**.

### What it knows

- Only you: each player sees their own journal.
- People: only those who've met you. "Met" is a saved fact from this version on: a Drifter that has been close to you
  (talking distance) and seen you, or has a friendship record with you (so old worlds aren't blank). Townsfolk who
  distrust you only by word (a town turned on you after a killing they didn't see) are not listed: the town's standing
  says that **(builder's call)**.
- Towns: been to (inside its bounds at least once, saved per player from this version on; towns where you already have
  a friend, a crime or a standing count as been to, so old worlds aren't blank) or heard of.
- A town's standing is the one you last saw there: saved per player when you're in the town (its people have to be
  about to work it out), so a town far away shows what you knew when you left, not a guess. Wanted and exiled come
  from your record there and are always current (you'd know) **(builder's call)**.
- A town only heard of shows no coordinates; a town you've been to does **(builder's call)**.
- The bounty: the game holds a price on your head as who put it up and why ("the people of Oakhold, for the killing of
  Ann"), with no amount. The journal says exactly that; no amount is invented (it would be a new system). The Law
  page ties it to its town by who put it up **(builder's call)**.
- Feelings in words, as above. Days and counts of things you'd see yourself (bread handed over, days left) are fine.
- Fresh each time it's opened; it needn't update while open **(builder's call)**.

## Constraints

- Client-only code in `src/civClient`; common code never imports `net.minecraft.client.*`.
- The addon reaches Drifter only through `com.dinglebob.drifter.api`; if it needs more (a bounty on a player to read,
  who distrusts a player), the API grows in the same change and docs/API.md says so.
- The journal's contents are worked out on the server and sent to the client when asked (a request, an answer); the
  client holds no world data of its own. Works in single player and on a server.
- Saved data versioned (the visited towns).
- Mojang names, Minecraft 1.21.1, no Loom or Gradle change.
- Stay out of the whim system and its files.
- Friendships switched off in settings: the People and Favors pages say so rather than showing stale records
  **(builder's call)**.

## Testing

- Dev client: open it in a world with towns; each bookmark screenshotted with real data (a friend made, a favor asked,
  a theft seen, wanted) using the existing debug commands, at two window sizes. A debug switch may open it for the
  harness (as `DRIFTER_CIV_SCREEN` does) **(builder's call)**.
- Headless: a smoke run that the server starts, answers a journal request without error (a debug command printing what
  the journal would say), and saves and loads the visited towns.
- TESTING.md: plain steps for the user.

## Done

Built, tested in the dev client and headless, both jars installed in DRIFTER TEST with a new version number (next on
the main line), PROGRESS.md, TESTING.md, LIVED_WORLD.md and (if it grew) API.md updated, committed and pushed.

## As built (0.23.41)

- A friend's town you've never been to is "heard of" (no coordinates, no standing), not been to.
- "Wanted" is never kept as a last-seen standing: it shows only while your record there says so; otherwise "unwelcome".
- Nothing is owed to the player in the game today, so Favors is only what friends asked of you.
- The book is not scaled down: two pages (292 by 197) fit the smallest window the game allows (320 by 240).

