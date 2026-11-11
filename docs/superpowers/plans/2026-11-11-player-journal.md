# The Player's Journal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task
> (inline, this session; the user chose it). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Press J in a world and an open journal shows what the world holds about you: people, towns, law, favors.

**Architecture:** The server works out the whole journal as plain lines of words (`Journal.Book`: a left page and a
list of bookmarks, each a list of lines with an optional detail) and sends it when the client asks; the client only
draws it. So later parts (nation, chronicle, quests) add bookmarks on the server without touching the screen. Two small
facts are newly saved per player: Drifters met, and towns been to with the standing last seen there.

**Tech Stack:** Fabric 1.21.1, Mojang names, Fabric networking payloads, `SavedData`, vanilla GUI textures only.

**Spec:** `docs/superpowers/specs/2026-11-11-player-journal-design.md`

## Global Constraints

- No new art: only vanilla textures, sprites and font, plus flat fills drawn by code.
- Words, never numbers, for feelings; only people met, towns been to or heard of; no coordinates for heard-of towns.
- Client-only code in `src/civClient`; common code never imports `net.minecraft.client.*`.
- The addon reaches Drifter only through `com.dinglebob.drifter.api`; API growth documented in docs/API.md.
- Saved data versioned. No Loom or Gradle change. Stay out of whim files.
- No code tests (project rule): verification is `./gradlew build`, a headless smoke run, and dev-client screenshots.
- Version `0.23.41` (check the installed jar and side branches right before installing; memory "side-branch-installs").

## Review Focus

- A player with nothing (new world, no towns met): every page shows its empty line, nothing crashes.
- Friendships switched off: People and Favors say so.
- A long list (100+ people): scrolls, stays inside the page, no lag opening.
- Smallest window / GUI scale 4: the book still fits (it scales down or the pages narrow).
- A server without the addon, or J pressed with a screen open or while typing in chat: nothing happens.
- An old world: bonds, crimes and standings seed "met" and "been to", so the journal isn't blank.

Each is checked in Task 4's client test or smoke run.

---

### Task 1: Drifter's API: read the price on a player's head

**Files:**
- Modify: `src/main/java/com/dinglebob/drifter/encounter/Encounters.java` (a public reader beside `postBountyFor`)
- Modify: `src/main/java/com/dinglebob/drifter/api/DrifterApi.java` (beside `postBounty`, ~line 1166)
- Modify: `docs/API.md`

**Interfaces:**
- Produces: `DrifterApi.PlayerBounty(String issuer, String reason)` (a public record nested in `DrifterApi`) and
  `@Nullable DrifterApi.PlayerBounty DrifterApi.bounty(UUID player)`: the price on their head now, or null.

- [ ] Step 1: add both; `API_VERSION` up by one; one row in docs/API.md.
- [ ] Step 2: `./gradlew build` passes.
- [ ] Step 3: commit.

### Task 2: The journal on the server

**Files:**
- Create: `src/civ/java/com/dinglebob/drifterciv/Journal.java`
- Modify: `src/civ/java/com/dinglebob/drifterciv/DrifterCivilizations.java` (register; `/drifterciv journal`)

**Interfaces:**
- Consumes: `Friends.of(UUID)`, `Friends.Bond`, `Friends.Favor`, `CivConfig.friends().on`, `Towns.all()`, `Town.crimes`,
  `TownReputation.standing(server, town, player)`, `Nations.of(Town)`, `Towns.city(Town)`, `DrifterApi.bounty`,
  `DrifterApi.record(uuid)` (`playerTrust`), `TalkApi.lastSeen(drifter, player)`.
- Produces (all in `Journal`):
  - `record Line(String left, String right, int ink, List<String> detail)`: `ink` 0 plain, 1 good (green), 2 bad (red),
    3 faint; `detail` empty: not clickable.
  - `record Tab(String title, List<Line> lines, String empty)`: `empty` is shown when `lines` is empty.
  - `record Book(String name, List<Line> you, List<Tab> tabs) implements CustomPacketPayload` with `TYPE`, `CODEC`
    (`drifter_civ:journal`), S2C.
  - `record Ask() implements CustomPacketPayload` with `TYPE`, `CODEC` (`drifter_civ:journal_ask`), C2S.
  - `static void register()`: payload types, the C2S receiver (answers with `Book`), the 20-tick watcher, load/save.
  - `static Book of(ServerPlayer player)`.
  - `static List<String> text(Book book)`: the same as plain lines, for `/drifterciv journal` and the smoke run.

**What it works out** (wording is the builder's, player speak, sentence case):

- **Saved knowledge** (`SavedData` "drifter_civ_journal", `SAVE_VERSION = 1`), per player: `met` (Drifter UUID → name)
  and `been` (town UUID → standing word last seen, day). Watcher every 20 ticks per online non-spectator player:
  Drifters within 6 blocks whose `TalkApi.lastSeen` of the player is within the last 40 ticks are met; the town whose
  land the player stands in is been to and its standing (`TownReputation.standing`) written down.
  Seeding at read time (not saved): any Drifter with a bond counts as met; any town with a bond's town name, a
  `crimes` entry, or a `heard` entry... no: `heard` is the town's hearsay, not yours. Bond towns and crime towns count
  as been to; a town where you're wanted without a `been` entry and without `offences` there (word spread) is "heard of".
- **You (left page):** reputation line and count line from `been` standings plus live wanted/exiled:
  wanted anywhere → "an outlaw"; no towns → "nobody knows you yet"; mostly welcome (welcome > half, none unwelcome)
  → "well liked"; any unwelcome or more wary than welcome → "not trusted"; else "known here and there". Count line:
  "welcome in 3 towns, wary in 1" (only non-zero, neutral left out unless it's all there is). Then, in red: "wanted in
  X" per town, and "a price on your head: <issuer>, for <reason>" if `DrifterApi.bounty` isn't null.
- **People:** bonds (not STRANGER unless ended) first by stage, then met Drifters without a bond: "holds a grudge"
  if `DrifterApi.record(id).playerTrust` for you is under -0.2 (check the trust scale in the code and pick the mod's
  own "distrusts" threshold), else "stranger". States as in `Friends.list`: cooled "used to be friends", ended "fell
  out", gone "gone". Detail lines: town (bond's), last seen place and days ago, days at this stage, why, "you saved
  their life", the favor in words.
- **Towns:** been-to towns nearest first, then heard-of. Right text: standing word; exiled/wanted override from
  `crimes` (live). Detail: kind (homestead, town, city by the mod's own size words), nation name, coordinates (been to
  only), friends there (`Friends.inTown`), record there.
- **Law:** one line per town with a `crimes` entry: crime, offences, fine owed, "exiled N more days" / "wanted N more
  days"; the bounty line tied to its town when the issuer is "the people of <town>". Empty: "a clean record".
- **Favors:** each active bond with `favor != null`: who, what (the mod's own favor words in `FriendPerks`), progress,
  days left, promised or not. Owed to you: only if the code holds such a thing (check `FriendPerks`); otherwise nothing.
- Friendships off (`!CivConfig.friends().on`): People and Favors have no lines and their `empty` says friendships are
  switched off in settings.

- [ ] Step 1: write `Journal` (data, saved knowledge, `of`, `text`, payloads, register).
- [ ] Step 2: `/drifterciv journal` (a player: their own, as chat lines from `text`); register in `DrifterCivilizations`.
- [ ] Step 3: `./gradlew build` passes.
- [ ] Step 4: commit.

### Task 3: The journal screen and its key

**Files:**
- Create: `src/civClient/java/com/dinglebob/drifterciv/client/JournalScreen.java`
- Modify: `src/civClient/java/com/dinglebob/drifterciv/client/DrifterCivilizationsClient.java`
- Create: `src/civ/resources/assets/drifter_civ/lang/en_us.json` (the key's and category's names; check the build
  packs it into the addon jar, else put it where the addon's client resources go)

**Interfaces:**
- Consumes: `Journal.Book`, `Journal.Tab`, `Journal.Line`, `Journal.Ask` (Task 2).
- Produces: `JournalScreen(Journal.Book book)`, `void JournalScreen.update(Journal.Book)`; key mapping
  `key.drifter_civ.journal` (J, category `key.categories.drifter_civ`).

**Behaviour:**
- Key pressed with no screen open and `ClientPlayNetworking.canSend(Journal.Ask.TYPE)`: send `Ask`; when `Book`
  arrives, open the screen (or update it if open). J or Esc closes. `isPauseScreen()` false.
- Drawn from vanilla only: two pages from `textures/gui/book.png` (the right page as is, the left one beside it;
  mirror or overlap as looks best), bookmarks along the top as flat paper-coloured tabs with a dark outline (or the
  advancements tab sprites if they sit well on the book), book ink `0x000000`-ish text without shadow, red `0xAA0000`,
  green `0x2A6A2A`, faint `0x7A6A50`. Try it in the dev client and keep what reads as a vanilla book.
- Left page: name centred, the player drawn with `InventoryScreen.renderEntityInInventoryFollowsMouse`, then `you`
  lines wrapped to the page.
- Right page: the chosen tab's lines (left text, right text right-aligned, a dotted rule under each), mouse wheel
  scroll clipped to the page (`enableScissor`), a hovered clickable line underlined; click: its detail replaces the
  list, with "< back" at the top (click or right mouse button goes back). Long text wraps (`font.split`).
- Fits any window: if the two pages are wider or taller than the screen, draw one scale smaller (pose scale).
- Debug: env `DRIFTER_CIV_SCREEN=journal` or `journal:<tab>` or `journal:<tab>:<line>` sends `Ask` 300 ticks after the
  world opens and opens that tab / that line's detail (existing value `1` keeps opening the Friendship page).

- [ ] Step 1: write the screen, the key, the receiver, the debug hook, the lang file.
- [ ] Step 2: `./gradlew build` passes.
- [ ] Step 3: commit.

### Task 4: Test, document, ship

- [ ] Step 1: headless smoke (`python tools/smoke.py`, an existing planned world or seed 779): server starts, a town
  made, stopped and started again with no error from the journal's saved data (log lines for load and save).
- [ ] Step 2: dev client test (fresh copy of a test world; `DRIFTER_CLIENT_TEST` script with `cmd execute as @p ...`):
  a Drifter befriended with the debug stage command, a favor offered, a crime and wanted set with `drifterciv town
  crime`, a bounty with `drifter encounter bounty`; then `drifterciv journal` output in the log and screenshots of each
  bookmark and one detail via `DRIFTER_CIV_SCREEN=journal:<tab>`, at the default window and a small one. Look at every
  screenshot; fix what looks wrong (overflow, unreadable ink, pages off-centre). Also: a brand-new player (empty
  pages), friendships off.
- [ ] Step 3: docs: PROGRESS.md (done, next: parts 2 to 4), TESTING.md (plain steps: press J, what each bookmark
  shows, how to get something onto each page), LIVED_WORLD.md (status line and progress entry), API.md (Task 1).
- [ ] Step 4: `version=0.23.41` in gradle.properties; `./gradlew build`; fetch, check side branches and the installed
  jar; `tasklist /FI "IMAGENAME eq javaw.exe"`; if the game is closed, remove both old jars from DRIFTER TEST and copy
  both new ones; else leave them in `build/libs` and say so.
- [ ] Step 5: commit and push.
