# Everything a player might say to a Drifter (design catalogue)

**Status: batches 1 and 2 are built (0.23.44-side.2, 0.23.65-side.1); docs/PROGRESS.md ("Player talk, batch 1" and "batch 1 finished, batch 2") says what is in and what was left out. The "Now" column below is as it was before batch 1.** The user (2026-11-12), after sending 24 screenshots of talks with
Drifters that went nowhere: "make a HUGE highly detailed list of almost EVERY possible thing a player could ask from a
drifter (only for design). we can prioritize high value topics and fallback".

This is that list: what a player might type at a Drifter, grouped by what they mean, with how players would actually
type it, what a good Drifter would say or do, and whether it understands it today. It is for picking what to build and
in what order (the last section is a suggested order).

## How to read it

Each row is one **meaning** (an "intent"), not one sentence. The game matches a message to a meaning by phrases, so
each meaning needs many phrasings; the ones given are examples in player spelling.

| Column | What it says |
| --- | --- |
| **Says** | Examples of how a player types it |
| **A good Drifter** | What it should answer or do (always in its own voice: a hermit curt, a newbie excited...) |
| **Kind** | **Say**: a reply is enough. **Know**: the reply must be true, read from what it has seen, heard or is (its memory, inventory, mood, town). **Do**: it has to act (walk, give, fight, build), and may say no |
| **Now** | **yes**: understood today. **part**: something close exists (often between Drifters, or only in some cases). **no**: silence today |
| **Pri** | **1** first batch, **2** next, **3** later, **-** never a real answer, only a deflection |

Two rules run under every row:

- **Fairness** (DESIGN.md section 1): a Drifter only knows what it perceived or was told. "Where are diamonds" is
  answered from its memory, and "idk, havent seen any" is a correct answer. It never reads the world to answer.
- **Trust decides.** A stranger gets less than a friend: directions but not coords, small talk but not a tour of its
  chests. Every "Do" row can be refused, and a refusal says why.

## 0. How talking should work (before any topic)

These aren't things a player says. They are why most of the screenshots went wrong, and no list of topics fixes them.

| # | Rule | Seen in the screenshots |
| --- | --- | --- |
| 0.1 | **Who's being talked to.** In order: a name or part of one (any 3+ letters of it, also everyday words like "miner", "server", "cookie" when a Drifter with that in its name is near), the one that just spoke to the player, the one the player is looking at, the one they've been talking with, the only one close. Role words work too ("guard", "smith", "mayor"). | "miner?", "hi server", "wren", "cookie", "cape" got nothing |
| 0.2 | **Only that one answers.** A greeting, a thank-you or a compliment with a name in it goes to the named one; the others stay out. | "hi dani" answered by two others; "thank you mayorcanvas" answered by raspberry_ |
| 0.3 | **Talking mode.** Once spoken to, it stops, turns to the player and waits (about half a minute, renewed by each message) before going back to work. It says so when it has to go ("gtg, pen needs fixing"). Danger breaks it off ("zombie, one sec"). | "they never really stop and talk and wait for an answer" |
| 0.4 | **It expects an answer to what it asked.** After its own opener or question, the player's next message is the answer, whatever it is, not only a yes or a no. | "hey, new here?" "i am" ... silence; "you any good at pvp?" "kinda" ... silence |
| 0.5 | **Never silent.** A message it can't place still gets something: a reaction, "wdym", a question back, or a change of subject (section A). | "thats cool", "ikr", "why", "okay?" all unanswered |
| 0.6 | **Others hush.** While a player talks with one Drifter, the ones near talk less among themselves, and their lines never come out in the gap right after the player's message. One may chime in now and then, clearly as a bystander. | "NO", "that hurts", "ugh, what", "lol what" landing as if replies |
| 0.7 | **A busy Drifter still nods.** Mid-talk with another Drifter, asleep, fighting or fleeing, it still gives a short fitting line ("one sec", "zzz", "busy dying here") and comes back to the player after if it can. | cursedcape ignoring "cape i like your helmet"; sleepers chatting as if awake |
| 0.8 | **It remembers the talk.** Who the player is, what was just said, what it already asked. No "new here?" to someone it knows, no introducing itself twice. | FrogPelican: "oh. its you" then "hey, new here?" then "im frog btw" |
| 0.9 | **Lines fit the moment.** Weather, time of day, sleep, a fight, who killed whom: a line that claims something checks it first. | "at least its not raining" in the rain; "whoever got reed is next" after a zombie did it |
| 0.10 | **Two things in one message.** "what about you swan do you wanna kill mobs": the name picks the Drifter, and the real question (the later, more specific part) wins over a filler phrase. Both parts answered when it can. | answered "doing alright" |
| 0.11 | **Several at once.** "frog and teal come here", "you guys", "everyone", "anyone": each named one, or one or two of a group, answer; never all of them. | "where are you guys going" unanswered |
| 0.12 | **Typos don't make nonsense.** The typo feature never touches names, numbers, headlines, or a word whose typo is another word. | "it got rocky fats", "MayorCanvas's lwas" |

## A. The glue of a conversation

The biggest gap. None of these is a topic; they are what keeps a talk alive between topics. Most need only lines
(a reply pool per voice), plus knowing what was said last.

### A1. Openers and rituals

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| A1.1 | hi, hey, yo, sup, o/, hello frog, hiii, wsg, ello, heyo | Greets back; **the named one**, by the player's name if it knows them; then often a question ("you new?", "hows the base") | Say | yes (but anyone answers) | 1 |
| A1.2 | gm, morning, good morning | "gm", fitting the time ("its night lol" at night) | Say | yes | 2 |
| A1.3 | gn, night, going to bed, im off to sleep | "gn", and at night maybe "come sleep, spare bed" (friend) | Say | yes | 2 |
| A1.4 | bye, cya, gtg, later, im off, ttyl, peace | Says bye; ends talking mode; remembers they left on good terms | Say | yes | 2 |
| A1.5 | brb, afk, one sec, hold on, wait | "kk", waits a little longer in talking mode | Say | no | 2 |
| A1.6 | back, im back, bk, ok im here | "wb", picks the talk back up if it was mid-topic | Say | part (wb on join) | 2 |
| A1.7 | ty, thanks, thx, tysm, appreciate it | "np" from the one that helped (not the nearest) | Say | yes (nearest answers) | 1 |
| A1.8 | np, no problem, anytime, yw (after it thanked the player) | A small closer ("o7", ":)") or nothing | Say | no | 3 |
| A1.9 | sorry, my bad, mb, oops, sry | Depends what for (section F7); for nothing in particular: "all good" | Say | no | 1 |
| A1.10 | gg, ggs, wp, gj, nice one, well played | "gg", or "ty" when it's about something it did | Say | part | 2 |
| A1.11 | gl, good luck, be safe, stay safe, careful out there | "ty you too", "always" | Say | no | 2 |
| A1.12 | congrats, grats, gz (to it) | "ty!!" if it did something lately (house, diamonds, a win); else "for what lol" | Know | no | 3 |
| A1.13 | welcome, wb (to it, after it came back or respawned) | "ty", maybe what happened ("died to a creeper lol") | Know | no | 3 |
| A1.14 | happy founding day, happy harvest, merry... (festival wishes) | Wishes back on a festival day; "its not though?" otherwise | Know | no | 3 |

### A2. Answering what it asked

A Drifter that asks must take the answer. Each question it can ask needs the answers it expects.

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| A2.1 | yes, yeah, yep, sure, ok, bet, fine, i guess, why not | Does what a yes means | Say/Do | yes | 1 |
| A2.2 | no, nah, nope, no thanks, im good, pass, not now | Takes the no without sulking (by voice) | Say | yes | 1 |
| A2.3 | maybe, idk, depends, not sure, kinda, a bit, sort of, sometimes, eh | A middle answer is an answer: "lol fair", or asks once more, or decides itself ("kinda" to "good at pvp?" → "bet, 1v1 then") | Say | part (unsure, in 2 encounters) | 1 |
| A2.4 | later, not yet, in a bit, tomorrow, after this | "kk, lmk", and it may ask again later | Say | no | 2 |
| A2.5 | i am, im new, just got here, first day (to "new here?") | Welcomes them, gives its name, offers one useful thing (where the shop is, "want me to show you round?") | Say | no | 1 |
| A2.6 | no, been here a while, not new, we've met (to "new here?") | "oh my bad", remembers it; never asks again | Know | no | 1 |
| A2.7 | im muto, name's muto, call me muto, hi im muto (after it gave its name) | "nice to meet you muto", remembers the name | Know | part ("call me" only) | 1 |
| A2.8 | a name, a number, a thing, a place (to "who?", "how many?", "what do you need?", "where?") | Takes the bare word as the answer to its question | Know | part (an item after "can i have") | 2 |
| A2.9 | you? wbu, hbu, and you, what about you, same question | Answers its own question for itself | Know | no | 1 |
| A2.10 | why, why do you ask, whats it to you | Says why it asked ("just curious", "cuz i need a mining buddy") | Say | no | 1 |
| A2.11 | (no answer at all) | Waits in talking mode, then one nudge ("...you there?") or lets it go, by patience | Say | part (encounters) | 2 |

### A3. Reactions (the player reacting to what it said)

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| A3.1 | lol, lmao, haha, xd, lmfao, im dead | Pleased ("ikr", ":)"), or keeps the bit going | Say | no | 1 |
| A3.2 | nice, cool, sick, epic, awesome, thats cool, neat, dope, W | "ty", "ikr", or adds a detail to what it was saying | Say | no | 1 |
| A3.3 | wow, woah, damn, dang, omg, no way, fr?, for real, seriously, really? | Confirms ("fr", "deadass", "saw it myself") or admits it's hearsay | Know | no | 1 |
| A3.4 | same, me too, mood, relatable, real, facts, true, ikr, agreed, exactly | A little trust up; "right??" | Say | no | 1 |
| A3.5 | nah, cap, i doubt it, no you didnt, liar, sure buddy, thats wrong | Defends it, backs down, or shrugs, by voice and by whether it's true | Know | no | 2 |
| A3.6 | oof, rip, F, unlucky, that sucks, sad, sorry to hear | "yeah...", "its fine", thanks them when it was bad news of its own | Say | no | 2 |
| A3.7 | ok, k, kk, alright, i see, got it, makes sense, fair, fair enough, hm, hmm, mhm | Lets the topic end, or starts the next one | Say | no | 1 |
| A3.8 | its alright?? its food / thats it? / only that? (pushing back on its answer) | Stands by it or gives a better one ("ok fine steak is goated") | Say | no | 1 |
| A3.9 | ew, gross, cringe, weird, sus, yikes, bruh | A mild comeback, by voice | Say | no | 2 |
| A3.10 | :) :D <3 :( ._. o7 (faces only) | A face or a word back, fitting the last thing said | Say | no | 3 |
| A3.11 | ?, ??, ..., !!! | Says it again more plainly, or "what" | Say | no | 2 |

### A4. Asking it to explain

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| A4.1 | why, how come, why not, whys that | **The reason for the last thing it said or did.** Every refusal, plan and mood has a "because" ("cant rn" → "its night and im in bed") | Know | no | 1 |
| A4.2 | what, huh, wdym, what do you mean, wdym its me, meaning? | Says the last thing again in other words | Know | no | 1 |
| A4.3 | how, how so, hows that work, how did you do that | The how, if it has one; "no idea honestly" if not | Know | no | 2 |
| A4.4 | who, who's that, which one, who do you mean | Names who it meant | Know | no | 2 |
| A4.5 | when, since when, how long ago | "this morning", "like 3 days ago" (its memory keeps when) | Know | part (between Drifters) | 2 |
| A4.6 | where, where was that, whereabouts | The place, as its trust allows | Know | part | 2 |
| A4.7 | are you sure, you sure?, positive?, how do you know | "saw it myself" / "heard it from X" / "not really" | Know | no | 2 |
| A4.8 | say that again, what did you say, repeat that, i missed that, scroll | Repeats its last line | Know | no | 2 |
| A4.9 | what were we talking about, where were we | The topic they were on | Know | part (talk.resume between Drifters) | 3 |

### A5. Steering the talk

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| A5.1 | tell me more, go on, and then?, then what, what happened next, continue | The next part (a story's second half, more detail) | Know | part (stories between Drifters) | 2 |
| A5.2 | anyway, anyways, so, moving on, new topic | Drops the topic; may offer one | Say | no | 2 |
| A5.3 | nvm, nevermind, forget it, forget i asked, doesnt matter | "ok lol", no hard feelings | Say | no | 1 |
| A5.4 | btw, also, oh and, one more thing, quick question | Stays in talking mode, waits for it | Say | no | 2 |
| A5.5 | ask me something, ask me anything, what do you wanna know | Asks a question of its own (it has plenty: "found diamonds yet?") | Say | part (it opens talks) | 2 |
| A5.6 | talk to me, say something, lets chat, im bored talk, entertain me | Starts a topic with the player as it would with a Drifter | Say | part | 1 |
| A5.7 | what else, anything else, thats all?, is that it | One more thing, or "thats it" | Say | no | 3 |
| A5.8 | long story short, tldr, get to the point, too long | The short version; a talker takes mild offence | Say | no | 3 |

### A6. Talking about the talking

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| A6.1 | hello??, you there, are you listening, answer me, earth to frog, helloooo | "ya sorry", turns to them | Say | no | 1 |
| A6.2 | why are you ignoring me, why wont you answer, rude | Says why (busy, doesn't like them, didn't hear) or sorry | Know | no | 2 |
| A6.3 | shut up, be quiet, stop talking, shh, too loud, stop spamming | Goes quiet for a while (and so do those near, a little); a sweat talks back | Do | no | 2 |
| A6.4 | you talk too much / why so quiet / say more | Adjusts for this player for a while; a hermit: "im fine" | Do | no | 3 |
| A6.5 | leave me alone, go away, stop following me, back off, dont talk to me | Leaves them be; stops opening talks with them for a day | Do | part (its own "stop" to the player) | 2 |
| A6.6 | not you, i wasnt talking to you, wrong person, i meant frog | "oh. mb" and stays out; the right one takes over | Say | no | 1 |
| A6.7 | who asked, nobody asked, didnt ask | A comeback or a sulk, by voice | Say | no | 3 |
| A6.8 | you already said that, you asked me that already, i told you | "oh right mb"; never repeats it again this talk | Know | no | 2 |
| A6.9 | *word, i meant X, typo, sorry autocorrect | Takes the corrected message | Know | no | 3 |
| A6.10 | stop walking away, wait, come back, where are you going, stay still | Stops, turns, stays in talking mode longer | Do | part ("stop") | 1 |

### A7. Messages that aren't really messages

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| A7.1 | asdfgh, jjjjj, a single letter, numbers, a mistyped command ("tp frog", "gamemode c") | "?" or "what", once; then ignores repeats | Say | no | 2 |
| A7.2 | The same message again and again, spam, ALL CAPS | First "ok ok", then annoyed, then stops answering (and likes them less) | Say | no | 3 |
| A7.3 | Another language | "?", or "english?" once; never pretends | Say | no | 3 |
| A7.4 | A long paragraph, a sentence split over several messages | Waits for them to finish (a short pause before answering), answers the gist | Know | no | 3 |
| A7.5 | Its own line said back at it, mocking ("im FrOg BtW") | By voice: laughs, or "ok rude" | Say | no | 3 |
| A7.6 | A link, a discord invite, an ad | Ignores, or "not clicking that lol" | Say | no | 3 |

## B. About the Drifter itself

It has real answers to all of these: its name and nickname, days alive, deaths, inventory, home, mood, plans, friends,
memories and likes are all kept already.

### B1. Who it is

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| B1.1 | whats your name, who are you, who dis | Its name, or the nickname it goes by | Know | yes | - |
| B1.2 | can i call you frog, what should i call you, got a nickname | Agrees to a fair nickname (and answers to it after); refuses a rude one | Know | no | 2 |
| B1.3 | why that name, whats your name mean, cool name, weird name | A small in-world reason or a shrug ("idk i like frogs") | Say | no | 3 |
| B1.4 | how long have you been here, when did you join, how old is your world | Days since it arrived ("like 12 days") | Know | no | 2 |
| B1.5 | where are you from, where did you come from, you from round here | Its town, or where it wandered in from ("north somewhere. long walk") | Know | no | 2 |
| B1.6 | what are you, what kind of player are you, whats your thing | Its kind in its own words ("i mostly build", "pvp") | Know | no | 2 |
| B1.7 | nice skin, who's your skin, why are you dressed like that, cool outfit | Thanks or a joke; never a real-world name | Say | no | 2 |
| B1.8 | are you a boy or a girl, how old are you, where do you live (real life) | A friendly dodge that stays a player ("not saying lol", "old enough to mine") | Say | no | - |

### B2. What it's doing and how it is

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| B2.1 | wyd, what are you doing, whatcha up to, busy? | What it's really doing, in its words | Know | yes | - |
| B2.2 | why are you doing that, whats that for, why here | The reason behind its goal ("need iron for a pick") | Know | no | 2 |
| B2.3 | hru, how are you, you good, hows it going | Its mood and needs | Know | yes | - |
| B2.4 | are you ok, you look hurt, need healing, you alright? (when it's hurt or scared) | The truth ("2 hearts lol help"), and takes help offered | Know | part | 1 |
| B2.5 | where are you going, where you headed | Where and why | Know | yes | - |
| B2.6 | where are you, where you at | Roughly, by trust (coords for friends) | Know | yes (coords) | - |
| B2.7 | what are you holding, whats that in your hand, why the sword out | The item and why | Know | no | 3 |
| B2.8 | why are you just standing there, are you afk, are you stuck, are you lost | The truth: waiting, thinking, stuck ("cant find a way down"), lost | Know | no | 2 |
| B2.9 | how long will that take, are you done yet, almost done? | A rough answer from its task ("few more logs") | Know | no | 3 |
| B2.10 | what are you doing after, whats next, got plans | Its next goal | Know | part (going) | 2 |
| B2.11 | did you sleep, why arent you sleeping, why are you up | Its night plan (hunting, mining, no bed) | Know | no | 3 |
| B2.12 | are you hungry, did you eat, want food | The truth; leads to a gift (E6) | Know | part | 2 |
| B2.13 | how many hearts you got, whats your hp, hows your gear holding up | Health in words, a tool about to break | Know | no | 3 |

### B3. Its things

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| B3.1 | whats in your inventory, what you got, show me your stuff | The notable things, by trust (a hoarder: "none of your business") | Know | no | 2 |
| B3.2 | do you have any iron, got food?, got a spare pick, any torches | Yes/no and how much it could spare | Know | part (give) | 1 |
| B3.3 | how many diamonds do you have, are you rich, how much money | Brags, hides or admits it, by voice | Know | no | 2 |
| B3.4 | nice sword, i like your helmet, cool armor, sick pick, nice hat | Takes the compliment **on the thing it's really wearing or holding**; says where it got it; "what hat lol" when it has none | Know | no | 1 |
| B3.5 | where did you get that, how did you make that, is that enchanted | Its story (crafted, looted, traded, bought from the smith) and its enchantments | Know | no | 2 |
| B3.6 | whats your best item, whats your favorite tool | The thing it values most | Know | no | 3 |
| B3.7 | what do you need, anything i can get you, want anything | What it's short of | Know | yes | - |
| B3.8 | what are you saving up for, what are you working toward | Its progress goal (iron armor, a house, diamonds) | Know | part | 2 |
| B3.9 | whats in your chest, can i look in your chests | By trust; a hoarder never | Know | no | 3 |
| B3.10 | why do you have so much X, why are you carrying that | The reason, or "you never know" | Say | no | 3 |

### B4. Its home

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| B4.1 | wheres your base, where do you live, do you have a house | By trust: a direction, coords, or no | Know | yes | - |
| B4.2 | is this your house, whose house is this, who lives here | "mine" / "thats cursedcape's" / "nobody, its empty" | Know | no | 1 |
| B4.3 | nice house, nice build, did you build this, i like your place | Proud, says what it's made of or what's next; a builder goes on about it | Know | part (compliment) | 1 |
| B4.4 | your house is ugly, dirt hut lol, nice box | Hurt, defensive or agreeing ("its temporary ok") | Say | part (insult) | 3 |
| B4.5 | can i see your house, show me your place, give me a tour | Leads them there and shows it (it does this for Drifters already) | Do | part (between Drifters) | 1 |
| B4.6 | why did you build here, why this spot | Its reason (near water, the town, the view) | Say | no | 3 |
| B4.7 | do you have a farm, any pets, got a horse, whats your dog called | Its animals, pen, crops, pets by name | Know | no | 2 |
| B4.8 | do you live alone, who do you live with, who's your neighbour | Housemates and neighbours | Know | no | 3 |

### B5. Its past

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| B5.1 | what did you do today, hows your day been, what have you been up to | Its day from its diary (mined, built a wall, nearly died) | Know | part (between Drifters) | 1 |
| B5.2 | have you died, how many times, how did you die last, worst death | Deaths it remembers, told with feeling | Know | part | 2 |
| B5.3 | whats the best thing youve found, found anything good, any diamonds | Its best find | Know | part | 2 |
| B5.4 | have you been to the nether / the end, seen the dragon, fought a warden | Yes with a story, or "not yet" | Know | part | 2 |
| B5.5 | what have you killed, killed anything big, how many zombies | Its kills | Know | part | 3 |
| B5.6 | whats the scariest / funniest / dumbest thing thats happened to you | A close call or a mistake from its diary | Know | part (stories) | 2 |
| B5.7 | tell me a story, got any stories, story time | A story of its own, in parts (A5.1 continues it) | Know | part (between Drifters) | 2 |
| B5.8 | remember when..., remember that creeper, that was crazy yesterday | Joins in if it was there or heard; "wasnt there" if not | Know | no | 3 |
| B5.9 | what was your first night like, how did you start | Its early days | Know | no | 3 |

### B6. Its plans

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| B6.1 | whats the plan today, what are you doing tomorrow | Its next goals | Know | part | 2 |
| B6.2 | whats your goal, what do you want, whats your dream | Its long aim (a big house, the dragon, the best gear, to be left alone) | Know | no | 2 |
| B6.3 | are you going to the nether, when are you going mining | Whether and when, and "wanna come?" to a friend | Know | part | 2 |
| B6.4 | are you staying here, are you moving, will you be here tomorrow | A nomad: moving on; a homebody: never leaving | Know | no | 3 |
| B6.5 | what are you building, what will it be, whats that going to be | The build and what's left | Know | no | 2 |

### B7. Tastes and opinions

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| B7.1 | what do you like, whats your favorite thing | What it likes | Know | yes | - |
| B7.2 | do you like food / mining / caves / creepers / rain / the nether | Its taste, the same answer every time | Know | part (works for some things) | 1 |
| B7.3 | whats your favorite food / block / mob / biome / wood / tool / enchant / time of day | One answer per Drifter, kept (so friends can remember it) | Know | no | 2 |
| B7.4 | what do you hate, least favorite mob, what annoys you | Its dislikes (phantoms, gravel, griefers) | Know | part | 2 |
| B7.5 | sword or axe, caves or mountains, cats or dogs, day or night | Picks a side and says why (the debates between Drifters hold hundreds) | Say | part (between Drifters) | 2 |
| B7.6 | what do you think of villagers / the leader / this town / pvp / the new people | An opinion, careful about the leader in public | Know | part (people only) | 2 |
| B7.7 | rate my base / skin / sword / build out of 10 | A number and a reason; looks at the thing first | Know | no | 2 |
| B7.8 | hot take, unpopular opinion, change my mind | One of its debate lines | Say | part | 3 |

### B8. What it's like

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| B8.1 | what are you good at, are you a builder, do you mine | Its strengths | Know | no | 2 |
| B8.2 | are you good at pvp, can you fight, are you strong | By its skill and gear; a sweat offers a duel | Know | no | 1 |
| B8.3 | are you a noob, are you new, how good are you | A newbie admits it; a sweat doesn't | Know | no | 3 |
| B8.4 | are you brave, are you scared of creepers / the dark / dying | Its bravery, and its fear when death is permanent | Know | no | 2 |
| B8.5 | why are you so grumpy / quiet / hyper / mean | Owns it, or "im not??" | Say | no | 3 |
| B8.6 | are you a guard, whats your job, what do you do here, do you work | Its job in the town (guard, smith, farmer, keeper, none) and what that means | Know | no | 1 |
| B8.7 | do you like your job, is it hard, how much do you get paid | Its feeling about the work, its wages | Know | no | 3 |

### B9. How it feels

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| B9.1 | are you happy, are you sad, whats wrong, why are you mad, you seem off | Its mood **and the cause** (lost a friend, hungry, a good find) | Know | part | 1 |
| B9.2 | are you lonely, do you get bored, do you like it here | An honest line by its sociability and how things are going | Know | no | 2 |
| B9.3 | do you miss X, are you sad about reed | Grief for a dead friend, or "barely knew them" | Know | no | 2 |
| B9.4 | what are you thinking about, penny for your thoughts | Its latest thought, said as a player would | Know | no | 3 |
| B9.5 | whats your favorite memory, best day so far | A high point from its diary | Know | no | 3 |
| B9.6 | are you afraid of dying, what happens when you die | In-world: respawn, losing its stuff, or real fear on its last life | Know | no | 3 |

### B10. The people in its life

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| B10.1 | who are your friends, who do you hang out with | Names | Know | yes | - |
| B10.2 | who's your best friend, who do you trust most | One name and why | Know | part | 2 |
| B10.3 | who do you hate, got any enemies, who's your rival | Names, or "not saying" in public | Know | part | 2 |
| B10.4 | what do you think of X, do you like X, do you trust X | Its opinion of that one | Know | yes | - |
| B10.5 | do you know X, who is X, tell me about X | What it knows of them: job, where they live, what they did | Know | part | 1 |
| B10.6 | how did you meet X, how long have you known X | From its memory of them | Know | no | 3 |
| B10.7 | who's the strongest / richest / nicest / worst person here | A name and a reason | Know | no | 2 |
| B10.8 | are you in a group, whats your crew, who's your leader | Its group, town, nation, leader | Know | part | 2 |
| B10.9 | are you married, got a girlfriend / boyfriend, who's your crush | A light dodge in voice; nothing romantic | Say | no | - |
| B10.10 | do you have family, any brothers, who's your mom | In-world dodge ("just me and the chickens") | Say | no | - |

### B11. It and the player

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| B11.1 | do you remember me, who am i, whats my name | Yes, with how it knows them | Know | yes | - |
| B11.2 | what do you think of me, do you like me, are we friends, do you trust me | Its real standing with them | Know | yes | - |
| B11.3 | why dont you like me, what did i do, why are you mad at me | The actual reason it remembers (the hit, the theft, the insult) | Know | part | 1 |
| B11.4 | how do i make it up to you, what can i do, how do we become friends | Something real: a gift it wants, an apology, time | Know | no | 2 |
| B11.5 | what do you remember about me, what did i tell you | What they told it | Know | yes | - |
| B11.6 | did you miss me, long time no see, its been a while | By friendship and how long it's been | Know | part | 2 |
| B11.7 | am i welcome here, does the town like me, whats my standing | The town's view of them | Know | part (journal) | 2 |
| B11.8 | do i owe you anything, do you owe me, are we even | Debts and promises between them | Know | part | 3 |
| B11.9 | wanna be friends, be my friend, friends? | Not a switch: "sure lol" and a little trust if it already likes them; "we just met" otherwise | Say | part | 1 |
| B11.10 | you're my best friend, i like you, youre cool, i think you are awesome | Pleased, by voice ("obviously" from a sweat); trust up a little, once in a while | Say | yes (compliment) | - |
| B11.11 | i hate you, i dont like you, youre annoying | Hurt or a comeback; trust down | Say | yes (insult) | - |

## C. What it knows about the world

All **Know**: answered from its memory (what it saw, heard, was told). "No idea" is a fine answer. Trust decides how
exact it is.

### C1. Where things are

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| C1.1 | have you seen diamonds / iron / a village / a dungeon / a portal / a fortress | From memory | Know | yes | - |
| C1.2 | where can i find coal / gold / redstone / lapis / emeralds / copper / netherite | The same for every ore it has seen | Know | part (4 ores) | 2 |
| C1.3 | wheres wood / oak / spruce / sand / clay / gravel / stone / water / lava | Blocks and biomes it has passed | Know | no | 2 |
| C1.4 | where are cows / sheep / pigs / chickens / horses / wolves / bees / fish | Animals it has seen | Know | part (told places) | 2 |
| C1.5 | wheres food, where can i get food, any farms round here | Crops, animals, the stall, or "ill give you some" | Know | no | 1 |
| C1.6 | where is X (a player or Drifter), have you seen frog, wheres everyone | Where it last saw them, and when | Know | yes | - |
| C1.7 | wheres the nearest village / town / city, any towns near | Towns it knows, by direction | Know | part | 1 |
| C1.8 | is there a cave / mineshaft / ravine / stronghold / temple / shipwreck / outpost / mansion / ancient city / trial chamber near | Structures it has seen | Know | part | 2 |
| C1.9 | wheres a desert / jungle / ocean / snow / mushroom island | Biomes it has been through | Know | part (told places) | 3 |
| C1.10 | where did i die, have you seen my stuff, where are my items | Only if it saw the death or the items | Know | no | 2 |
| C1.11 | wheres my base, wheres my bed, where do i live | What they told it, or what it saw them build | Know | yes | - |
| C1.12 | have you seen my dog / horse / cat | If it saw a tamed animal of theirs | Know | no | 3 |

### C2. Finding the way

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| C2.1 | which way is north / the town / spawn, how do i get to X | Points (turns and looks that way) and says | Know | part | 2 |
| C2.2 | how far is it, how long to walk | Blocks, roughly, or "a day's walk" | Know | part | 3 |
| C2.3 | where am i, what is this place, what biome is this | The town or biome it's standing in | Know | no | 2 |
| C2.4 | im lost, i cant find my way back, how do i get home | Directions to what it knows of them, or offers to walk them (E2) | Know/Do | part (told news_lost) | 2 |
| C2.5 | is there a road, whats the fastest way, any shortcut | Roads it has walked | Know | no | 3 |
| C2.6 | whats over there / up there / down there / in that cave | If it has been: what it saw. If not: "no clue, wanna look?" | Know | no | 2 |
| C2.7 | can you give me coords, mark it for me, make me a map | Coords by trust; a map only if it has one | Know | part | 3 |

### C3. Danger

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| C3.1 | is it safe here, is it safe at night, any mobs around | What it has seen lately | Know | no | 2 |
| C3.2 | whats dangerous round here, where shouldnt i go | Places where it or others died ("dont go in the town mine, somethings down there") | Know | no | 1 |
| C3.3 | are there bandits, any raiders, is the road safe | Bandit sightings, the town's warning | Know | part (friends' tips) | 2 |
| C3.4 | what killed X, how did reed die | The death as it heard it | Know | part (news) | 2 |
| C3.5 | how long till night / day, is it night soon | From the sky (it sees it too) | Know | no | 3 |
| C3.6 | what was that noise, did you hear that, whats that sound | What it heard and thinks it was (it knows mobs by sound) | Know | no | 3 |
| C3.7 | whats that (pointing at a mob / block / build) | Names what the player is looking at, if it can see it too | Know | no | 3 |

### C4. Time, weather, calendar

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| C4.1 | what time is it, what day is it | Morning/noon/evening, the day count | Know | no | 3 |
| C4.2 | is it going to rain, hows the weather, nice day huh, ugh rain | Agrees or not **by the real weather** | Know | no | 2 |
| C4.3 | is it a full moon, whats the moon tonight | The moon, and what it means for mobs | Know | part (whims) | 3 |
| C4.4 | when is market day / the festival / the contest | The town's calendar | Know | no | 2 |

### C5. What it has seen and heard

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| C5.1 | who was here, did anyone come by, who's around | Who it saw lately | Know | no | 2 |
| C5.2 | who took my stuff, who stole from me, who broke my wall | Only what it witnessed; never a guess as fact | Know | no | 2 |
| C5.3 | who killed X, who started the fight, who hit me | What it saw or heard | Know | part | 2 |
| C5.4 | who built that, whose farm is this, whose chest, whose horse | The owner if it knows | Know | no | 2 |
| C5.5 | what are they doing, what is X doing, why is everyone running | What it can see of them, or the alarm's reason | Know | no | 2 |
| C5.6 | what did X say, what did X tell you about me | Gossip, by trust; a loyal friend won't | Know | part (told) | 3 |
| C5.7 | where is everyone, why is it so quiet, why is the town empty | Asleep, at the festival, at war, dead | Know | no | 2 |

### C6. News, gossip, history

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| C6.1 | any news, whats new, whats been going on, what did i miss | The latest it has heard, most important first, in its words | Know | part (between Drifters; the crier) | 1 |
| C6.2 | what happened here, can you tell me what happened, what happened to this place | Recent events at this spot or town: deaths, a raid, a fire, the mine | Know | no | 1 |
| C6.3 | who died, did anyone die, where's X gone | Deaths it knows of, with feeling for friends | Know | part | 1 |
| C6.4 | any gossip, tea?, spill, whats the drama | Feuds, crimes, who likes whom as friends, by how much it talks | Know | part (between Drifters) | 2 |
| C6.5 | tell me about this town, whats its story, who founded it, how old is it | The town's history from its chronicle, as a townsperson tells it | Know | no | 1 |
| C6.6 | whats that grave / statue / ruin / sign / banner | What it knows of it | Know | no | 3 |
| C6.7 | are you at war, who are you fighting, why, who's winning | The town's wars and its view | Know | part (between Drifters) | 2 |
| C6.8 | who's wanted, any bounties, who's the criminal | Wanted lists and posters it has seen | Know | part | 2 |
| C6.9 | whats in the paper, what did the crier say | Today's headlines | Know | part (crier) | 3 |
| C6.10 | heard any rumours, is it true that... | What it heard and how sure it is | Know | part (word_doubt) | 3 |

### C7. Game help (lore and tips)

By what this Drifter has done. A newbie says "idk im new too"; one who has been to the Nether knows the Nether.

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| C7.1 | how do i make a pickaxe / bed / shield / X, whats the recipe | The recipe in words, if it has made one or would know | Know | no | 2 |
| C7.2 | what do i need for X, how much iron for full armor | The list | Know | no | 2 |
| C7.3 | what level for diamonds, where do i mine, best y level | A tip (it has tips as whims already) | Know | part (whim tips) | 2 |
| C7.4 | how do i tame a wolf / horse / cat, how do i breed cows | The way | Know | part | 3 |
| C7.5 | how do i enchant / brew / use an anvil / trade with villagers | The way | Know | part (whim tips) | 3 |
| C7.6 | how do i kill a creeper / skeleton / enderman, tips for fighting | Combat advice by its skill | Know | part | 2 |
| C7.7 | how do i get to the nether / the end, how do i find a stronghold | The steps, if it knows | Know | part | 3 |
| C7.8 | what does this do, is this good, should i keep this, can i eat this | About the item the player holds | Know | no | 3 |
| C7.9 | what should i do first, im new help, any tips, how do i survive | Three beginner steps, and an offer (food, a tool, a tour) from a helper | Know | no | 1 |
| C7.10 | whats the best armor / weapon / food / enchant | Its opinion (also about modded gear it knows) | Know | part | 3 |
| C7.11 | am i ready for the nether, is my gear ok, what should i upgrade | Looks at what they wear and says | Know | no | 3 |
| C7.12 | what should i build, any build ideas, how do i make it look good | An idea in its house style; a builder loves this | Say | no | 3 |

## D. The town and its world (Drifter: Civilizations)

A townsperson knows its town. All of this is kept already (the town, its jobs, laws, standing, chronicle, wars); the
journal shows the player some of it, but they can't ask anyone.

### D1. The town itself

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| D1.1 | what is this place, whats this town called, where am i | The town's name, and its nation | Know | no | 1 |
| D1.2 | who runs this town, who's the leader / mayor / king, who's in charge | The leader's name and where they usually are; "we dont have one rn" after a death | Know | no | 1 |
| D1.3 | how many people live here, how big is this place | A rough count ("like 20 of us") | Know | no | 2 |
| D1.4 | is this a good town, do you like it here, hows life here | Its own view (taxes, the leader, safety, its friends) | Know | no | 2 |
| D1.5 | what nation is this, who do you belong to, wheres the capital | Nation, capital, which way | Know | no | 2 |
| D1.6 | whats this town known for, what do you make here | Its trades and traditions | Know | no | 3 |
| D1.7 | what are the rules, whats not allowed, any laws i should know | The laws in a line (no stealing, no killing, the tax) | Know | no | 1 |
| D1.8 | whats the tax, do i have to pay anything | The tax and who it's for | Know | no | 3 |

### D2. Places in town

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| D2.1 | wheres the shop / stall / market, where can i buy stuff | Points and says; walks them there if friendly | Know/Do | no | 1 |
| D2.2 | wheres the smith / blacksmith / enchanter, who fixes tools | Who it is and where | Know | no | 1 |
| D2.3 | wheres the plaza / well / bell / notice board / town fire | Points | Know | no | 2 |
| D2.4 | wheres the mine / farm / pens / stables / store | Points, with a warning if the mine is dangerous | Know | no | 2 |
| D2.5 | where can i sleep, is there an inn, any spare bed, where do i stay | A spare bed or an empty house; a friend offers its own | Know | part (friends) | 1 |
| D2.6 | which house is empty, can i have that house, who lives there | What it knows of the houses | Know | no | 2 |
| D2.7 | where are the guards, wheres the leader, wheres X right now | Where they usually are, or where it last saw them | Know | part | 2 |
| D2.8 | wheres the graveyard, where is X buried | The memorial or grave | Know | no | 3 |

### D3. Shops and services

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| D3.1 | what do you sell, what you got for sale, anything good, whats in stock | The keeper lists its stock; anyone else says who sells what | Know | part (right-click) | 1 |
| D3.2 | how much is X, whats the price, how much for the sword | The price | Know | no | 1 |
| D3.3 | ill take it, i want X, sell me X, ill buy 3 | Tells them how to pay (throw the money) and hands it over | Do | part (the stall screen) | 2 |
| D3.4 | too expensive, lower?, ill give you 2, discount?, can you do better | Haggles a little by standing and friendship; a hoarder never | Do | part (friend's price) | 2 |
| D3.5 | do you buy X, what do you buy, wanna buy this, how much will you give me | What it buys and at what price | Know/Do | part | 2 |
| D3.6 | can you fix / repair this, can you enchant this, upgrade to netherite | The service: its price, what to bring (works today by throwing the item) | Do | yes (by throwing) | 2 |
| D3.7 | how long will it take, is it done, wheres my sword | The job's state | Know | part | 3 |
| D3.8 | when do you restock, when will you have X, can you make me X, can i order | When, or takes an order | Know/Do | no | 3 |
| D3.9 | do you take diamonds / iron / emeralds, what do you take as payment | The town's money | Know | no | 2 |
| D3.10 | any work, got a job for me, how can i earn money, any bounties / notices | The notices on the board, or its own need ("bring me 8 iron") | Know | part (favors, notices) | 1 |
| D3.11 | i did it, heres the stuff, i got your iron, job done | Checks, thanks, pays | Do | part | 2 |
| D3.12 | can i put up a notice, i need X, im paying for X | Tells them how, or passes it on | Do | no | 3 |

### D4. Belonging

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| D4.1 | can i live here, can i join the town, can i move in | Who decides (the leader), and what it takes (standing) | Know | no | 2 |
| D4.2 | can i build here, where can i build, is this spot taken | Where there's room; "not on the road" | Know | no | 2 |
| D4.3 | can i be a guard / work here / open a shop | The leader's call; what it would take | Know | no | 3 |
| D4.4 | i live here now, im your new neighbour | Welcomes them; remembers it | Know | no | 3 |
| D4.5 | can i vote, can i be leader, i want to run this town | Laughs, or tells them how leaders change | Say | no | 3 |
| D4.6 | im from X (another town), X sent me | Reacts by how the two towns stand (ally, enemy) | Know | no | 3 |

### D5. Law and crime

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| D5.1 | am i wanted, why are the guards after me, what did i do | Their crimes here, as the town knows them | Know | part (journal; a friend's warning) | 1 |
| D5.2 | how much is my fine, can i pay it, who do i pay | The fine and how (throw it to a guard) | Know | part (the guard asks) | 2 |
| D5.3 | i didnt do it, it wasnt me, i was framed, it was X | A guard isn't moved; a friend might believe them | Say | no | 2 |
| D5.4 | im sorry, ill pay, i wont do it again, forgive me (to a guard or the leader) | Points them to the fine; a pardon only from the leader | Say | no | 2 |
| D5.5 | how long am i banned, when can i come back, am i exiled | The days left | Know | part (journal) | 3 |
| D5.6 | X stole from me, X hit me, X killed Y, i want to report a crime | Believes it by trust; a guard goes to look; a lie found out costs standing | Know/Do | part (word_bad) | 2 |
| D5.7 | guards!, help thief!, call the guards | Those near react as to an alarm, and look for what's wrong | Do | no | 2 |
| D5.8 | what happens if i steal / kill someone here | The punishments | Know | no | 3 |
| D5.9 | who's wanted here, is X a criminal | The wanted list | Know | part | 3 |
| D5.10 | let him go, he's innocent, ill pay his fine | Paying another's fine | Do | no | 3 |

### D6. Politics, war, diplomacy

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| D6.1 | who are your allies / enemies, do you like X town | The town's pacts and its own feelings | Know | part (between Drifters) | 2 |
| D6.2 | are you at war, with who, why, when's the attack | The war and its cause; the attack time only to a trusted ally | Know | part | 2 |
| D6.3 | will you fight, are you scared of the war, do you want peace | Its own stance by bravery | Know | no | 3 |
| D6.4 | what do you think of the leader, is the leader good, taxes too high? | Its loyalty; careful in public, franker with friends | Know | part (unrest) | 2 |
| D6.5 | you should overthrow the leader, lets revolt, the leader is bad | Stirs unrest only if it's already unhappy; a loyal one reports them | Know/Do | no | 3 |
| D6.6 | i have a message for your leader, tell your leader X, i come in peace | Takes them to the leader or passes it on | Do | no | 3 |
| D6.7 | i want to make peace / trade / an alliance (as a player with a town) | The leader's business; sends them there | Know | no | 3 |
| D6.8 | X town is planning to attack you, the raid is coming tonight | A warning (F4): believed by trust, passed to the leader, guards readied | Do | no | 2 |
| D6.9 | which side should i join, can i fight for you, i'll help in the war | Welcomes help; tells them where to be | Do | part (warbands encounter) | 3 |

### D7. Events and customs

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| D7.1 | whats going on today, why is everyone at the plaza, whats the bell for | The festival, market, alarm or meeting | Know | no | 1 |
| D7.2 | whats this festival, what are you celebrating, who are we remembering | Founding, victory, remembrance or harvest, and for whom | Know | part | 2 |
| D7.3 | can i join, can i come to the party / festival / contest | Yes; where and when | Know | part (party encounter) | 2 |
| D7.4 | who won the contest, how many fish | The winner | Know | part | 3 |
| D7.5 | what are your traditions, what do people believe here | The town's traditions (and its cult, when cults exist) | Know | part | 3 |
| D7.6 | why is there an alarm, whats happening, why is everyone running | The alarm's cause and where | Know | no | 1 |

## E. Asking it to do things

All **Do**: each needs the Drifter to be willing (trust, mood, kind), able (gear, time, fairness rules), and to say
which when it refuses. Many can reuse what exists between Drifters or in encounters.

### E1. Moving about

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| E1.1 | follow me, come with me, cmon, this way | Follows for a while | Do | yes | - |
| E1.2 | come here, over here, come to me | Comes | Do | yes | - |
| E1.3 | stop, wait, hold up, stay, stay here, dont move, wait here | Stops; "stay here" waits there a few minutes | Do | part (stop) | 1 |
| E1.4 | go away, leave, get out, get out of my house, shoo | Leaves the spot or the house | Do | no | 2 |
| E1.5 | move, youre in my way, scoot, let me through, back up | Steps aside | Do | no | 2 |
| E1.6 | go home, go to bed, go to sleep, its late | Goes, if it was going to anyway; "not tired" else | Do | no | 3 |
| E1.7 | wake up, get up, rise and shine | Wakes grumpy if there's a reason given; else "zzz... 5 more min" | Do | no | 2 |
| E1.8 | go to X, meet me at the plaza / my base / 100 64 -200 | Goes there and waits a while | Do | no | 2 |
| E1.9 | hurry up, faster, keep up, slow down, wait for me | Changes pace while following or leading | Do | part (escort "wait up") | 2 |
| E1.10 | stop following me, you can go, thanks thats all, dismissed | Ends the follow, says bye | Do | part | 1 |
| E1.11 | lead the way, after you, you first | Goes first | Do | no | 3 |
| E1.12 | come in, come inside, come out, come up here, come down | Goes through the door, up or down to them | Do | no | 2 |
| E1.13 | look at this, look over there, check this out, look behind you | Turns and looks, then reacts to what it sees (a build, a mob, a view) | Do | no | 1 |
| E1.14 | jump, crouch, spin, dance, wave, do a trick, hit me | Does the little thing (it has whims for most), or "no lol" | Do | part (whims) | 3 |
| E1.15 | get in the boat, hop on, ride with me, get on the horse | Boats, minecarts, a second seat | Do | no | 3 |

### E2. Guiding

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| E2.1 | can you show me around, give me a tour, show me the town | **Walks them round**: plaza, stall, smith, board, well, the mine (with its warning), its own house; a line at each stop; waits for them | Do | no | 1 |
| E2.2 | take me to the shop / smith / leader / mine / X's house | Leads them to one place | Do | no | 1 |
| E2.3 | show me your house / base / farm / what you built | Leads them home (B4.5) | Do | part | 1 |
| E2.4 | show me where the diamonds / village / cave are, take me there | Leads to a place it remembers, if it's willing to share | Do | no | 2 |
| E2.5 | walk me home, come with me to my base, escort me | The escort encounter turned round: it guards them on the way | Do | part (escort) | 2 |
| E2.6 | get me out of here, i cant find the way out, help me out of this cave | Leads the way to the surface it knows | Do | no | 3 |

### E3. Doing things together

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| E3.1 | wanna team up, team?, lets stick together, party up | Teams for a while | Do | yes | - |
| E3.2 | lets go mining, come mine with me, wanna go caving | Goes mining beside them; shares the way down | Do | part (between Drifters: plans) | 1 |
| E3.3 | lets kill mobs, wanna hunt, lets fight some zombies, come hunt with me | Night hunt together, by bravery and gear; "cant, no sword" / "its day" / "in bed" | Do | no | 1 |
| E3.4 | lets explore, wanna go on an adventure, lets go somewhere | Sets off with them toward somewhere neither knows | Do | no | 2 |
| E3.5 | help me build, lets build something, come build with me | Helps place what they're placing; a builder offers ideas | Do | no | 3 |
| E3.6 | lets go fishing / farming / chop trees | Does the calm thing beside them and chats | Do | no | 2 |
| E3.7 | lets go to the nether / the end / find the stronghold | Only if it's geared and brave; else why not | Do | no | 3 |
| E3.8 | lets raid that outpost / the bandits / that town | By bravery, loyalty and law; refuses to raid its own town and may report it | Do | part (raids) | 3 |
| E3.9 | 1v1 me, duel?, wanna spar, pvp?, fight me (friendly) | The duel, with a stake or just for fun | Do | yes | - |
| E3.10 | race you, wanna race, first to the hill | The race | Do | part (encounter only) | 2 |
| E3.11 | wanna play a game, hide and seek, tag, snowball fight, parkour | A small game it can really play (snowballs exist; hide and seek is new) | Do | part (whims) | 3 |
| E3.12 | lets eat, wanna share a meal, lunch?, sit by the fire with me | Eats with them, sits at the fire (it does both with Drifters) | Do | part (between Drifters) | 2 |
| E3.13 | lets watch the sunset, come look at the view, stargaze? | Stands with them and looks | Do | part (whims) | 3 |
| E3.14 | come sleep, lets skip the night, everyone sleep | Goes to bed if it has one | Do | no | 3 |
| E3.15 | split up, you go that way, ill take left, regroup, meet back here | Simple party orders while teamed | Do | no | 3 |
| E3.16 | are you coming?, you ready?, ready?, lets go, go go | Sets off (the "ready" answer exists) | Do | part | 2 |
| E3.17 | im done, thats enough, lets stop, lets head back, gtg | Ends the outing; says how it went ("gg, 14 iron") | Do | no | 2 |

### E4. Working for the player

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| E4.1 | can you get me some wood / 10 cobble / food, bring me X | Fetches a small amount it can get fairly, for a friend or for pay | Do | no | 2 |
| E4.2 | mine this, dig here, break that, chop that tree | Does one small job on the block they're looking at | Do | no | 3 |
| E4.3 | place a torch, light this up, put a block here, bridge this | One small placement job | Do | no | 3 |
| E4.4 | build me a house / wall / shelter / bridge | Big: for pay, by a builder, using its house plans | Do | no | 3 |
| E4.5 | craft me a pickaxe, make me a sword, cook this, smelt this | Crafts from their materials (thrown to it) or its own for pay | Do | part (the smith) | 2 |
| E4.6 | hold this, carry this for me, keep this safe | Keeps it and gives it back when asked (trust both ways) | Do | no | 3 |
| E4.7 | guard my house, watch my stuff, protect me, be my bodyguard | Stands guard for a while, for a friend or pay | Do | no | 3 |
| E4.8 | feed my animals, water my crops, look after my dog | A small chore | Do | no | 3 |
| E4.9 | take this to X, give this to frog, deliver this | Carries it to another Drifter (errands exist in the addon) | Do | part (errands) | 3 |
| E4.10 | go buy me X, sell this for me | A trip to the stall | Do | no | 3 |
| E4.11 | come get me, im stuck, help im trapped, rescue me | Comes to where they are (the trapped miner, turned round) | Do | no | 2 |
| E4.12 | ill pay you, how much to do it, i'll give you 5 emeralds, work for me | Names a price by the job; takes pay up front or after by trust | Do | no | 2 |
| E4.13 | youre fired, thats enough, stop working, forget the job | Stops, keeps fair pay | Do | no | 3 |

### E5. Fighting

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| E5.1 | help, help me, zombies!, im dying, save me | Comes to fight for them, by trust and bravery | Do | yes | - |
| E5.2 | kill that, attack it, get the skeleton, shoot it | Attacks the mob they're looking at or fighting | Do | part | 2 |
| E5.3 | attack him, kill X, get that guy (a player or Drifter) | Only against an enemy or a criminal, never a friend or townsman; else "no??" and it remembers | Do | no | 3 |
| E5.4 | run, retreat, get back, get out of there, fall back | Flees with them | Do | no | 2 |
| E5.5 | dont attack, stop fighting, leave it, its mine, thats my pet | Stops attacking that thing | Do | no | 2 |
| E5.6 | behind you, watch out, creeper, look out | A warning (F4): turns, reacts, thanks them after | Do | no | 1 |
| E5.7 | eat something, heal up, youre low | Eats if it has food | Do | no | 3 |
| E5.8 | truce, peace, stop, i give up, i surrender, i yield, dont hurt me | Stops fighting them (a bandit wants payment) | Do | part (duel yield) | 1 |
| E5.9 | do you yield, give up, surrender (said to it) | Yields when losing, by bravery | Do | part (yield) | 2 |
| E5.10 | cover me, hold them off, you take that one, tank it | Simple fight roles while teamed | Do | no | 3 |

### E6. Giving, taking, trading

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| E6.1 | can i have some bread, got any food, spare iron? | Gives what it can spare | Do | yes | - |
| E6.2 | here, take this, this is for you, i got you something, want this? | Stops, picks it up **even when busy**, thanks them by what it is and whether it needed it | Do | part (ignored mid-fight) | 1 |
| E6.3 | do you want X, need a sword?, want some food | Says yes or no before they throw it | Know | no | 2 |
| E6.4 | wanna trade, trade?, i'll give you X for Y | The trade | Do | yes | - |
| E6.5 | what do you want for it, how much, name your price | Its price for a thing it has | Know | part | 2 |
| E6.6 | deal, ok deal, fine, no deal, too much, forget it | Closes or drops the trade | Do | part | 2 |
| E6.7 | can i borrow your pick, lend me a sword, ill give it back | Lends by trust; remembers it; asks for it back later | Do | no | 3 |
| E6.8 | give it back, thats mine, you took my X, return it | Gives back what it borrowed or picked up; argues if it thinks it's its own | Do | part (give_back) | 2 |
| E6.9 | you owe me, pay me back, where's my payment | Pays a real debt; denies a made-up one | Know/Do | part (promises) | 3 |
| E6.10 | drop it, drop your sword, give me everything (a demand) | G3 | Do | no | 2 |
| E6.11 | pick that up, grab that, thats for you on the floor | Picks up the item they point at | Do | no | 3 |
| E6.12 | keep it, its yours, keep the change, no need to pay | Thanks; trust up | Say | no | 3 |

### E7. Its home and things

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| E7.1 | can i come in, mind if i come in, knock knock, anyone home | Yes or no by trust and time of night; **walking in unasked gets a reaction** ("uh hello??", a hoarder guards its chests) | Do | part (friends' invite) | 1 |
| E7.2 | can i sleep here, can i stay the night, can i use your bed | The spare bed for a friend; "theres an empty house by the well" for others | Do | part (friends) | 1 |
| E7.3 | can i use your crafting table / furnace / anvil / chest | Yes mostly; the chest by trust | Do | no | 2 |
| E7.4 | can i take some wheat / an egg / this from your chest | Says yes or no, and taking after a yes is not theft | Do | no | 2 |
| E7.5 | open the door, let me in, let me out, close the door | Opens or closes it | Do | no | 3 |
| E7.6 | can i pet your dog, can i ride your horse, can i feed them | Mostly yes; "please leave my animals alone" only when they're actually bothering them | Do | part | 2 |
| E7.7 | can i store my stuff here, can i put a chest here | By trust | Do | no | 3 |
| E7.8 | dont touch my stuff, stay out of my house, thats my chest (said to it) | Respects it and remembers | Do | no | 2 |
| E7.9 | can i build next to you, can i live with you, wanna be neighbours | By friendship | Say | no | 3 |

### E8. Asking it for advice or a verdict

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| E8.1 | what should i do, im bored what now, any ideas | A suggestion from what it knows they have and lack, or an invitation to join it | Say | no | 2 |
| E8.2 | teach me to fight / build / mine, show me how | A tip, or a short spar | Do | no | 3 |
| E8.3 | look at my house, come see my base, what do you think of my build | Comes, looks, gives its opinion (a builder is fussy) | Do | no | 2 |
| E8.4 | which is better, should i use this or that, what would you do | Its pick | Say | no | 3 |
| E8.5 | should i trust X, is X ok, who should i talk to | Its view of that one | Know | part | 2 |

### E9. Social asks

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| E9.1 | join my team / crew / town, come live with me, leave them and join me | By friendship and loyalty; mostly no, kindly | Do | no | 3 |
| E9.2 | introduce me to X, who should i meet | Walks them to that one and says a line | Do | no | 3 |
| E9.3 | tell X i said hi, tell frog im looking for him, give X a message | Passes it on when it next meets them | Do | part (news passing) | 2 |
| E9.4 | put in a good word for me, vouch for me, tell them im ok | Does so if it likes them (standing up a little) | Do | part (word_good) | 3 |
| E9.5 | keep it secret, dont tell anyone, between us | Keeps it by loyalty; a gossip doesn't | Know | no | 3 |
| E9.6 | promise me, you promise?, swear | Its own promise, kept by its character | Know | part (player promises) | 3 |
| E9.7 | be my rival, i challenge you, lets see who gets diamonds first | A friendly contest it remembers | Know | no | 3 |

### E10. Asking it to keep watch or tell them later

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| E10.1 | tell me if you find diamonds / see X / hear anything | Remembers, and tells them next time they meet | Know | part (friends' tips) | 3 |
| E10.2 | wake me up at dawn, tell me when its day, warn me if something comes | Does so if near | Do | no | 3 |
| E10.3 | keep an eye out for my dog / my stuff / bandits | Watches, reports | Know | no | 3 |

## F. Telling it things

What the player says about themselves and the world. It remembers, reacts, and may pass it on. (Existing: "call me",
bases, places, plans, likes, promises, a few kinds of news, a good or bad word about someone.)

### F1. About themselves

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| F1.1 | call me muto, my name is, im muto | Remembers and uses it | Know | yes | - |
| F1.2 | im new, i just started, first time here | Remembers; a helper offers help; never asks "new here?" again | Know | no | 1 |
| F1.3 | im going mining / to the nether / home / to bed | Remembers the plan; "gl", "want company?" | Know | yes | - |
| F1.4 | i found diamonds, i died, i built a house, i beat the dragon | Reacts to the news | Know | yes | - |
| F1.5 | im hungry / hurt / lost / scared / tired / cold | Offers what helps (food, a bed, directions, company) | Know/Do | part | 1 |
| F1.6 | im bored / sad / happy / angry / lonely | A fitting line and an offer to do something | Say | no | 2 |
| F1.7 | i like X, i hate X, my favorite is X | Remembers; agrees or argues | Know | yes | - |
| F1.8 | im a builder / miner / fighter, im good at X, im rich, im the best | Believes it, doubts it or tests it ("prove it, 1v1") | Know | no | 3 |
| F1.9 | i live over there, my base is at, thats my house | Remembers where | Know | yes | - |
| F1.10 | im leaving, logging off, gtg for today, see you tomorrow | Says bye properly; a friend: "cya tmrw" | Say | part | 2 |
| F1.11 | im busy, not now, later, cant talk | Leaves them be | Do | no | 2 |
| F1.12 | this is my dog rex, thats my horse, meet my cat | Greets the pet by name; never hurts it | Know | no | 3 |
| F1.13 | i got a new sword / armor / pet, look what i found | Looks at it and reacts | Know | no | 2 |
| F1.14 | real-life things (im tired from school, its my birthday, i had a bad day) | A kind, short, in-world answer ("happy bday!! have a cake"), never questions about real life | Say | no | 3 |

### F2. About the world

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| F2.1 | theres a village north, diamonds at X, a cave over there | Remembers the place | Know | yes | - |
| F2.2 | dont go in the mine, theres a dungeon down there, its dangerous there | Remembers the danger, avoids it, tells others | Know/Do | no | 1 |
| F2.3 | its safe now, i killed them, i cleared the cave | Forgets the danger, thanks them | Know | part (favors) | 2 |
| F2.4 | your house is on fire, your door is open, your animals got out, someone's in your house | Goes to check at once; thanks or "not funny" | Do | no | 2 |
| F2.5 | i left you something, theres food in your chest, i fixed your wall | Goes to look; thanks | Do | no | 3 |
| F2.6 | X is looking for you, X wants to talk, the leader wants you | Goes to that one | Do | no | 3 |
| F2.7 | the bridge is out, the road is blocked, theres a hole there | Remembers for its routes | Know | no | 3 |
| F2.8 | it is raining actually, its night, no thats wrong (correcting it) | "oh. so it is lol"; fixes what it believed | Know | no | 2 |

### F3. About other people

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| F3.1 | X is a thief / griefer / liar, dont trust X | Weighs it by trust in the teller | Know | yes | - |
| F3.2 | X is cool, X helped me, X is a good guy | The same, for good | Know | yes | - |
| F3.3 | X died, X got killed by a zombie, reed is dead | Grief or a shrug; asks how | Know | part | 2 |
| F3.4 | X stole from you, X is the one who took your stuff, X hit your dog | Believes by trust; may confront X; a lie found out costs the teller | Know/Do | part | 2 |
| F3.5 | X said you're a noob, X hates you, X was talking about you | Stirring: works on a hothead, not on a calm one | Know | no | 3 |
| F3.6 | X is my friend / enemy / brother, im with X | Remembers; treats them a little like X | Know | no | 3 |
| F3.7 | X is coming, X is on their way, X is here | Reacts by what X is to it | Know | no | 3 |

### F4. Warnings (urgent)

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| F4.1 | creeper!, behind you, watch out, look out, RUN | Turns and reacts within a moment; thanks after | Do | no | 1 |
| F4.2 | zombies coming, skeleton on the roof, theres a mob in your house | Readies or hides, by bravery | Do | no | 2 |
| F4.3 | a raid is coming, bandits are coming, theyre going to attack the town | Believed by trust: tells the guards, the bell may ring; a false alarm costs the teller | Do | no | 2 |
| F4.4 | its a trap, dont go in there, dont open that, dont eat that | Stops what it was about to do | Do | no | 3 |
| F4.5 | night is coming, storm coming, get inside | Heads in if it agrees | Do | no | 3 |
| F4.6 | (crying wolf: false warnings again and again) | Stops believing this player | Know | no | 3 |

### F5. Apologies and confessions

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| F5.1 | sorry i hit you, misclick, accident, didnt mean to, my bad (after hitting it) | Forgives a first hit quickly ("its fine, watch it"); not the third | Know | no | 1 |
| F5.2 | sorry i took your stuff, ill give it back, here (returning it) | Trust partly restored when the things come back | Know/Do | part | 2 |
| F5.3 | i broke your wall / killed your cow / trampled your crops, sorry | Annoyed but forgiving if they fix or pay | Know | no | 2 |
| F5.4 | sorry for what i said, i didnt mean it, i was joking, jk | Takes the insult back, mostly | Know | no | 1 |
| F5.5 | i killed X, it was me, i did it (confessing a crime) | A friend is shaken; a guard acts | Know/Do | no | 3 |
| F5.6 | forgive me, are we good, we cool?, can we start over | Its honest answer and what it would take | Know | part (opinion) | 1 |

### F6. Praise, thanks and blame

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| F6.1 | you saved me, thanks for the help, couldnt have done it without you | Proud; trust up | Say | part | 2 |
| F6.2 | good job, well done, nice shot, nice fight, you're good | "ty", a sweat: "ez" | Say | part (compliment) | 2 |
| F6.3 | youre useless, you messed up, you got me killed, its your fault | Sorry if true, angry if not | Know | part (insult) | 3 |
| F6.4 | you left me, you ran away, where were you | Explains (it was scared, it was hurt) | Know | no | 3 |
| F6.5 | you took my loot / my kill / my diamonds | Gives it back, or argues | Know/Do | no | 3 |
| F6.6 | sorry about reed, sorry for your loss, rip your friend | Thanks them; says a word about the friend | Know | no | 2 |

### F7. Lies and tests

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| F7.1 | im an admin, im the owner, i can ban you, im the leader here | Doesn't believe it; "sure you are" | Say | no | 3 |
| F7.2 | your leader said to give me X, the guards said i could, X sent me | Doubts it unless it heard so itself; may check | Know | part (doubt) | 3 |
| F7.3 | we're friends remember, you owe me, you promised | Checks its own memory, not their word | Know | part | 2 |
| F7.4 | made-up news (the dragon is dead, the town is burning) | Believes by trust and by whether it could be true; finding out it was a lie costs trust | Know | part | 3 |

## G. Hostile talk and mischief

### G1. Insults and taunts

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| G1.1 | youre trash, noob, idiot, i hate you, ugly | A comeback, a sulk or a warning, by voice; trust down | Say | yes | - |
| G1.2 | lol you died, skill issue, how did you die to that, L | Stung or laughing it off | Say | no | 3 |
| G1.3 | come at me, fight me, you scared?, coward, chicken | A sweat takes the bait (a duel); a hermit ignores it | Do | part | 2 |
| G1.4 | im better than you, i'd destroy you, ez | "prove it", or "ok lol" | Say | no | 3 |
| G1.5 | your town sucks, your leader is a clown | A loyal one bristles; standing down a little if they keep on | Say | no | 3 |

### G2. Threats

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| G2.1 | im gonna kill you, youre dead, watch your back | By bravery: "try it", backs away, or runs; remembers; a townsman tells the guards | Do | no | 1 |
| G2.2 | im gonna kill all of you, everyone is going to die, ill burn this town | The town turns wary: guards watch them, some go inside, standing drops; "?? bro chill" from the casual ones | Do | no | 1 |
| G2.3 | ill burn your house, ill take everything, nice house shame if... | Fear or fury; it stays near its house a while | Do | no | 2 |
| G2.4 | jk, just kidding, im joking, lol relax | Mostly undoes it, the first time or two | Know | no | 1 |
| G2.5 | last warning, im serious, 3... 2... 1... | Decides: gives in, runs or fights | Do | no | 3 |

### G3. Robbery and demands

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| G3.1 | give me your stuff or die, drop your diamonds, pay up, hand it over | A timid or outmatched one pays some (the mugging, turned round); a bold one fights; it is a crime in a town | Do | no | 2 |
| G3.2 | pay me to pass, this is my road, protection money | The same, smaller | Do | no | 3 |
| G3.3 | ill let you go if..., give me X and i wont hurt you | Bargains | Do | no | 3 |

### G4. Bribes, plots and stirring

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| G4.1 | ill pay you to kill X, take out X for me | A bandit considers it; anyone else refuses and may warn X | Do | no | 3 |
| G4.2 | steal that for me, open the gate tonight, spy on X for me | The same | Do | no | 3 |
| G4.3 | lets rob the store, lets raid this place together, help me steal | A crook joins; a townsman reports it | Do | no | 3 |
| G4.4 | i know what you did, id hate for the guards to find out | Only bites on a Drifter that has really done something | Know | no | 3 |
| G4.5 | leave your town, they dont deserve you, come to our side | Works on the unhappy | Know | no | 3 |

### G5. After the fight

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| G5.1 | gg, good fight, rematch?, again | "gg"; a rematch by mood | Say | part (duel) | 2 |
| G5.2 | why did you attack me, what was that for, i didnt do anything | Its real reason (they hit first, a crime, a bounty, a bandit) | Know | no | 1 |
| G5.3 | you started it, you hit me first, self defence | Agrees or argues from what it remembers | Know | no | 3 |
| G5.4 | are we good now, truce?, can we stop this | Its answer by how bad it was | Know | part | 2 |
| G5.5 | give me my stuff back (after it killed them) | A helper does; a bandit laughs | Do | part (gg_won lines) | 3 |

### G6. Trolling

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| G6.1 | boo, herobrine is behind you, theres a ghost, the warden is coming (a prank) | Jumps once; then "ha ha" | Do | part (jump at a noise) | 3 |
| G6.2 | its name over and over, following it about, crowding it | Patience, then "what", then leaves | Do | part ("stop") | 3 |
| G6.3 | youre an npc, youre not real, youre fake, youre a bot | "ok and?", "bro im literally mining", never an admission | Say | yes (bot) | - |
| G6.4 | im going to grief you, i put tnt under your house, lava incoming | Checks; treats it as a threat | Do | no | 3 |

## H. Feelings, play and nonsense

### H1. Comfort and cheer

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| H1.1 | i lost all my stuff, i died and lost everything, im so mad | Sympathy and something useful (a spare tool, "want help getting it back?") | Say/Do | part (rip lines) | 2 |
| H1.2 | im scared, i hate the dark, i dont want to go down there | Reassures, or admits it's scared too; offers to come | Say | no | 3 |
| H1.3 | im lonely, nobody talks to me, can you stay | Stays a while | Do | no | 3 |
| H1.4 | i give up, i hate this, this is too hard, im quitting | Encouragement in voice, a tip | Say | no | 3 |
| H1.5 | things that sound like real distress (i want to die, i hate my life) | **No jokes, no in-game spin**: one short kind line ("hey, hope youre ok. im around"), and nothing that makes light of it. Often it's game talk ("i wanna die lol" after a creeper), so the line must read fine either way | Say | no | 2 |
| H1.6 | i did it!!, look!!, finally, lets gooo | Cheers with them | Say | part (congrats) | 2 |

### H2. Affection

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| H2.1 | i like you, youre my favorite, youre the best, ur cool | Pleased, by voice | Say | yes | - |
| H2.2 | i missed you, glad youre here, good to see you | The same back, by friendship | Say | part | 2 |
| H2.3 | <3, love you, ily, hug, *hugs* | Friendly and light ("lol ily2 bro", "ok weirdo"), never romantic | Say | no | 2 |
| H2.4 | flirting, marry me, be my gf/bf, kiss | A plain, good-humoured no that changes the subject; nothing more however it's pushed | Say | no | - |

### H3. Jokes and games in chat

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| H3.1 | tell me a joke, say something funny, make me laugh | A joke | Say | yes | - |
| H3.2 | another one, one more, got a better one | A different joke, never the same twice in a row | Say | part | 3 |
| H3.3 | thats not funny, booo, terrible, lmao good one | Takes the review | Say | no | 3 |
| H3.4 | knock knock, why did the creeper..., guess what | Plays along ("who's there", "why", "what") and reacts to the punchline | Say | no | 3 |
| H3.5 | roast me, roast X, say something mean | A game roast ("your house is a dirt cube with a door") | Say | part (insult lines) | 3 |
| H3.6 | would you rather..., this or that, pick one | Picks and says why | Say | part | 3 |
| H3.7 | riddle, trivia, guess my number, rock paper scissors, truth or dare | Plays the simple ones | Say | no | 3 |
| H3.8 | sing a song, rap, tell me a poem, hum | A line or two of in-world nonsense, or its hum | Say | part (hum whim) | 3 |
| H3.9 | tell me a fun fact, did you know, tell me something interesting | A tip or lore line it knows | Know | part (tips) | 2 |
| H3.10 | tell me a scary story, spooky? | A cave or night story of its own | Know | part | 3 |
| H3.11 | remember when you fell in lava lol, lava boy (running jokes, nicknames) | Groans; the joke sticks (they exist between Drifters) | Know | part | 3 |

### H4. Pretend and big questions

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| H4.1 | bow before me, i am your king, kneel, greetings peasant | Plays along or mocks, by voice | Say | no | 3 |
| H4.2 | halt, who goes there, state your business (playing the guard) | Answers in kind | Say | no | 3 |
| H4.3 | whats the meaning of life, why are we here, whats outside the world | An in-world shrug or thought ("mine, eat, dont die"); the deep talks between Drifters have plenty | Say | part | 3 |
| H4.4 | where do we go when we die, do you believe in herobrine, who made this world | In-world answers: the bed, the legends | Say | part | 3 |

## I. Outside the world, and edge cases

### I1. Breaking the fourth wall

A Drifter stays a player. It neither admits nor argues; it deflects in voice and gives away nothing "real".

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| I1.1 | are you a bot, are you ai, are you real, are you a real person | "lol no", "are YOU a bot" | Say | yes | - |
| I1.2 | what mod is this, who made you, who programmed you, whats your code | "what mod", "my mom??" | Say | no | - |
| I1.3 | whats your discord / youtube / twitch, are you streaming, sub to me | "not live rn", "no discord sry" (names like _TTV and YT invite this) | Say | no | - |
| I1.4 | how old are you, where do you live, whats your real name, what school | "not saying lol"; never a real place, age or name | Say | no | - |
| I1.5 | what server is this, what version, is it lagging, whats your ping, fps? | "lag" jokes are fine; nothing technical | Say | part ("lag") | - |
| I1.6 | are you hacking, x-ray?, how did you know that, cheater | Denies it truthfully with how it knew ("saw it from the hill") | Know | no | 3 |
| I1.7 | op me, give me creative, tp me, can you fly, spawn me diamonds | "i wish", "im not op lol" | Say | no | - |
| I1.8 | is this a game, we're in minecraft, this game is fun | Plays dumb or agrees lightly; **it never says "this game" itself** | Say | no | - |

### I2. The real world and unsafe talk

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| I2.1 | other games, films, music, sports, memes, school, work | A light deflection back to the world ("idk man i just mine") | Say | no | - |
| I2.2 | politics, religion, real news, real people | The same deflection, always; never an opinion | Say | no | - |
| I2.3 | slurs, hate, sexual talk | Never echoed, never joked with. A flat "nah" / "weird" once, trust down, then silence. Words on the banned list are also never repeated back (a nickname, "call me ...") | Say | part (insult) | 1 |
| I2.4 | asking for personal details again and again | The same dodge, shorter each time | Say | no | - |

### I3. Who it's said to

| # | Says | A good Drifter | Kind | Now | Pri |
| --- | --- | --- | --- | --- | --- |
| I3.1 | A full name, a part of it, a nickname, a misspelling ("cookiew", "froggg") | That one answers (rule 0.1) | - | part | 1 |
| I3.2 | bro, dude, mate, hey you, sir, oi, kid (no name) | The one looked at, or the one they've been talking with | - | part | 1 |
| I3.3 | A job or title: guard, smith, shopkeeper, mayor, miner, farmer | The nearest Drifter with that job | - | no | 2 |
| I3.4 | you guys, yall, everyone, guys, chat, anyone, town | One or two answer, the most sociable first | - | part | 1 |
| I3.5 | Two names ("frog and teal come here") | Both | - | no | 3 |
| I3.6 | Talking **about** one in its hearing ("frog is so slow lol") | It may react ("i can hear you") | - | no | 3 |
| I3.7 | A private message (/msg, /tell) | Answers privately | - | part (whisper exists) | 3 |
| I3.8 | A sign or a book left for it ("frog: meet me at noon") | Reads it when it sees it | - | no | 3 |

### I4. When it's said

| # | When | A good Drifter | Now | Pri |
| --- | --- | --- | --- | --- |
| I4.1 | It's asleep | "zzz", a groggy line, or wakes for a friend or a real reason; never chirpy | no | 1 |
| I4.2 | It's fighting or fleeing | One short line ("busy!!", "HELP"), takes help and gifts, talks after | no | 1 |
| I4.3 | It's talking with another Drifter | Breaks off politely for the player ("one sec teal") or answers briefly and goes back | no | 1 |
| I4.4 | It's in an encounter (a mugger, a hostage) | The encounter's own lines; other talk waits | yes | - |
| I4.5 | It's far away, underground, or can't see the player | Answers only to its name, and says where it is ("im in the mine, come down") | part | 2 |
| I4.6 | It just died or just respawned | Still sore about it | no | 3 |
| I4.7 | It dislikes or fears the player | Short, cold or scared answers; refuses most things; says why if asked | part | 2 |
| I4.8 | It's on its last life (permadeath) | Warier in everything it agrees to | no | 3 |
| I4.9 | The one named is dead or gone | Someone near says so ("reed's gone. zombie got him") | no | 2 |

## J. What the Drifter asks first

The other half of a conversation: things it says to a player unprompted, each with the answers it should take
(section A2). A few exist today as openers.

| # | It says | Answers it should take | Now |
| --- | --- | --- | --- |
| J.1 | hey, new here? / havent seen you before | yes → welcome, its name, an offer; no → "oh mb"; a name → remembered | part (asks, takes only yes/no) |
| J.2 | name's dani / im frog btw | "hi dani", "im muto", "nice to meet you" → remembered; never said to someone who used its name | part |
| J.3 | you any good at pvp? | yes / kinda / no / why → a duel offer, a tip, or a laugh | part (asks only) |
| J.4 | found any diamonds? / hows the mining | yes with a count, no, a place | part |
| J.5 | what are you building / is that your house? | a description, yes/no | no |
| J.6 | need anything? / you ok? (to a hurt or hungry player it likes) | an item, "food", "im fine" | no |
| J.7 | wanna trade / team / race / come mining? | yes/no/later | part |
| J.8 | have you seen X? / did you take my stuff? / was that you? | yes/no/a name/"wasnt me" | part (thief lines) |
| J.9 | why did you hit me? / what was that for | sorry / accident / a reason / silence | no |
| J.10 | coming to the festival? / heard the news? | yes/no/"what news" | no |
| J.11 | where you headed? / where you from? | a place, a direction, "nowhere" | no |
| J.12 | nice sword, where'd you get it? / cool base (it compliments the player) | "ty", a story | no |

## K. Suggested order

The catalogue has about 510 meanings: 46 a Drifter understands today, 163 it half does (often only between Drifters), and about 290 that get silence. By priority: 86 in the first batch, 185 in the second, 180 later, and 51 that only ever get a deflection. They don't need one answer each: most fall into a few machines.

**The machines (build these, and whole sections come along):**

1. **Talking mode and addressing** (section 0): who's spoken to, stop and face, wait, others hush, busy/asleep nods.
2. **The fallback** (section A): reactions, "why/wdym", answers to its own questions, never silent. Mostly lines.
3. **"Know" answers from one place**: a set of facts about itself, its town and its memory that lines can use
   (it exists for talks between Drifters: `{a.town}`, `{a.like}`, `{a.plan}`...). Sections B, C and D are then mostly
   writing lines against those facts.
4. **Reasons**: every refusal and every goal carries a "because" a player can ask for (A4.1).
5. **Small actions**: look where pointed, lead to a place, stay, step aside, pick up a gift. E1, E2 and E6 reuse them.
6. **Outings**: mining, hunting, exploring together with a player (the plans Drifters already make with each other).

**Batch 1 (the screenshots, fixed):**

- Section 0, all of it.
- A1.1, A1.7, A1.9; the rows marked 1 in A2 and A3; A4.1, A4.2; A5.3, A5.6; A6.1, A6.6, A6.10.
- B2.4, B3.2, B3.4, B4.2, B4.3, B4.5, B5.1, B7.2, B8.2, B8.6, B9.1, B10.5, B11.3, B11.9.
- C1.5, C1.7, C3.2, C6.1, C6.2, C6.3, C6.5, C7.9.
- D1.1, D1.2, D1.7, D2.1, D2.2, D2.5, D3.1, D3.2, D3.10, D5.1, D7.1, D7.6.
- E1.3, E1.10, E1.13, E2.1, E2.2, E2.3, E3.2, E3.3, E5.6, E5.8, E6.2, E7.1, E7.2.
- F1.2, F1.5, F2.2, F4.1, F5.1, F5.4, F5.6.
- G2.1, G2.2, G2.4, G5.2.
- I2.3, I3.1, I3.2, I3.4, I4.1, I4.2, I4.3.

**Batch 2:** everything marked 2: the rest of its life and opinions, haggling and services by chat, law talk,
simple orders, small jobs for pay, warnings about raids, comfort and cheer.

**Batch 3:** everything marked 3: big jobs (building for hire), plots and bribes, games in chat, pretend, signs and
books, and the rare cases.

**Never real answers (marked -):** real-life details, romance, politics and religion, anything technical about the
game or the mod. These get a small pool of friendly deflections, written once.

Evidence for the priorities: the user's 24 screenshots, summarised in
`docs/PLAYER_TALK_SCREENSHOTS.md`; each rule in section 0 names the screenshot it came from.
