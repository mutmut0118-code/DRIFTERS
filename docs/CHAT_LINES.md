# Every chat line a Drifter says

Everything a Drifter can say in chat as of 0.22.15, and when it says it. Lines show up as `<name> line`, like a player's
chat. `/drifter config chat false` turns all of them off (on by default).

`{player}` means the player's name, `{name}` the Drifter's own name, `{way}` a direction like "north".

## How often they talk

- **Canned lines** (section 1) have rate limits:
  - each Drifter says at most one line every 20 seconds, and the same kind of line at most once a minute;
  - all Drifters together say at most 4 lines in any 10 seconds;
  - quiet Drifters (low sociability) mostly don't bother: about 15% of the time for the quietest, 95% for the chattiest.
- **Whim lines and news** (sections 2, 3 and 4) have the same 20-second and server-wide limits, but not the sociability
  roll.
- **Encounter lines** (section 5) always get said: it's a Drifter talking *to you*.
- **Habits:** each Drifter has its own habit for the canned lines. The same Drifter always greets the same way (say,
  "yo"), and only now and then (a quarter of the time) uses another line from the list. PvP sweats always say "gg".

## 1. Canned lines (events)

| Kind | Lines | When |
| --- | --- | --- |
| HI | hi · hey · hello · yo · o/ · sup | **Greeting a player:** a friendly Drifter that likes you walks over to you and says hi, crouching at you as players do. |
| HUH | ? · yes? · hm? · what · yo? | **You say its name in chat** nearby: it stops, turns toward your voice, and once it sees you (and doesn't mind you) it nods and may answer. |
| THANKS | ty · thx · thanks · tysm · ty! | **You throw it an item** from close by while it can see you (a gift). **You kill a mob that was attacking it** (it was being chased or hit; if it was nearly dead, it counts as you saving it). |
| GG | gg · rip · gg wp · ggs | **It killed you:** a hostile Drifter, standing over your body before it loots it. **You killed it:** said as it dies. PvP sweats always say "gg". |
| STOP | go away · stop · back off · wait · dont | **Warning you off:** a Drifter that doesn't trust you, unarmed (or not willing to fight), tells you to back off instead of attacking. |
| OUCH | bruh · ? · hey! · stop | **You hit it once:** the first hit of a fight (a single hit; more hits and it treats it as an attack). |
| THIEF | thief · hey thats mine · give it back · my stuff?? · bro stole my stuff · thats MY gear | **It caught you stealing:** it saw you at its chest and came straight over to find things gone. **It sees you holding something** that went missing from its chest. |
| WAR_CRY | CHARGE · get them · raid time · for the crew! · lets go · attack!! | **A raid arrives:** bandits (or a group at war) reach the base they're raiding; one war cry per raid. |
| NICE | nice · lol · ok · nice one | Defined but **never used** at the moment. |

## 2. Whim lines (things it does for fun)

Whims are the little things a Drifter does when it has nothing urgent to do.

**"Chat" whims.** The whole whim is saying one of these lines to whoever's near, turning to look at them:

| Whim | Lines | When |
| --- | --- | --- |
| small_talk | hows it going · nice day · anyone got food · found some iron earlier · this place is nice · what are you up to | Any time, someone around. |
| brag | got diamonds btw · my base is better · ez · not even hard | Bragging a bit (show-offs, PvP sweats more). |
| say_bored | bored · nothing to do · so bored rn | It's bored. |
| good_morning | gm · morning · good morning · mornin | Sunrise. |
| good_night | gn · night · going to bed · night all | Sunset. |
| brag_about_gear | check out my armor · full iron btw · look at my sword · new pickaxe :) | Showing off its gear. |
| complain_about_mobs | so many zombies last night · creepers everywhere · a skeleton nearly got me · phantoms are the worst | Complaining about the mobs. |
| ask_for_help | anyone have iron? · need food lol · how do i make a piston · where are the diamonds | Asking around for help. |
| brag_about_bow | found a unique bow · this bow is something else · look at this bow · wait till you see what this bow does | Showing off a unique bow (with Simply Bows). |
| brag_about_shield | new shield · this shield takes a beating · reinforced, finally | Showing off a modded shield. |

**Said while doing something.** These whims have a line too, said half the time as the whim starts:

| Whim | Lines | When |
| --- | --- | --- |
| ring_the_bell | ding ding | Ringing a village bell. |
| slice_of_cake | cake :) | Eating a slice of cake from a cake it sees. |
| snow_golem | made a snowman | Building a snow golem. |
| view_from_the_top | nice view · woah · you can see so far from here | By day, climbing up somewhere high for the view (said at the top). |
| tallest_mountain | made it to the top | By day, climbing the tallest mountain in sight (said at the top). |
| happy_dance | lets gooo · yesss | Doing a little dance. |
| snowball_fight | snowball fight! · get rekt · incoming | Starting a snowball fight with a friend (or you). |
| eats_a_spider_eye | ew · why did i eat that | Eating a spider eye (a mistake). |
| hop_about | wheee · :) | Hopping about for no reason. |
| go_for_a_swim | water's nice | By day, going for a swim. |
| watch_sunset | sunset :) · pretty | Sunset, watching the sky. |
| watch_storm | big storm | Thunderstorm, watching the sky. |
| oops_dropped_a_stack | oops | Fumbling and dropping a stack by mistake (half the time; it picks it back up). |

**Step-by-step whims.** A line at one point of the whim, with the chance shown:

| Whim | Lines | Chance | When |
| --- | --- | --- | --- |
| smell_a_flower | mm, nice | 20% | By day: walks to a flower, crouches, looks at it. |
| pat_a_dog | good dog · who's a good boy | 30% | Patting a dog. |
| stare_at_its_reflection | looking good · huh. | 25% | By day, staring at its reflection in water. |
| knock_on_a_door | knock knock · anyone home? | 40% | By day, knocking on a door to see who's in. |
| hum_to_itself | hm hm hmm · la la la · dum de dum | always | Humming to itself. |
| sunset_with_a_friend | look at that · nice sunset, huh | 50% | Sunset, watching it with a friend (said to the friend). |
| admire_a_diamond | shiny · beautiful | 30% | Turning a diamond over in its hands. |
| nod_to_the_golem | evening, big guy · keep up the good work | 30% | Nodding to an iron golem. |
| warm_hands_by_the_fire | ahh, that's better · nice and warm | 30% | At night, crouching by a campfire it has seen. |
| wave_to_a_friend | hey! · o/ · hiya | 50% | Waving to a friend (said to the friend). |

## 3. News (gossip)

When two Drifters meet and one has fresh news, it sometimes says it out loud. This happens a third of the time, only for
news it saw or heard first-hand, and only with a player within 16 blocks:

- **did you hear? {player} {what they did}**, about something a player did to a Drifter that changed its trust a lot.
  The "what" is one of:
  - attacked {name} again and again
  - nearly killed {name}
  - killed {name}
  - killed {name}'s pet
  - took things from {name}'s chest
  - was caught stealing from {name}'s chest
  - turned up with things missing from {name}'s chest
  - killed {name}'s animals
  - set fire near {name}'s home
  - killed a mob that was attacking {name}
  - saved {name} from death
- **did you hear? {drifter} {how it died}**, using the death message, for example "did you hear? Bob was shot by
  Skeleton". This is about a Drifter it saw die.

Town news (like "X was founded by A, B and C") is passed between Drifters but not said out loud.

## 4. Trading between Drifters

When a Drifter trades with another and a player is within 16 blocks:

| Who | Line |
| --- | --- |
| The one asking | got any {things} to spare? |
| The one asking, if it pays | got any {things} to spare? I'll give you {payment} |
| The one asked, giving it free | sure, have {amount things} |
| The one asked, for pay | deal |
| The giver, handing it over | here you go (a gift) · pleasure doing business (paid) |

## 5. Encounters (it's talking to you)

Always said, no rate limits. These are the encounters from milestone 21:

| Encounter | Line | When |
| --- | --- | --- |
| Hungry traveller | excuse me, {player}... I haven't eaten in days. could you spare some food? | It walks up to you. |
| | bless you. here, for your kindness. | You gave it food. |
| Injured traveller | thank you... I'd be dead without you. Here, take this. | It ran to you, low on health with zombies after it; you killed them and it lived. |
| Lost dog | excuse me... have you seen my dog? {dog}, red collar. he ran off {way} a while ago and won't come back. here, take this lead, in case you find him. | It asks you, handing you a lead. |
| | thank you so much. here, it's not much, but take it. | You brought the dog back. |
| Escort | excuse me, {player}! I'm going to the village {way} of here, about {far} blocks. the roads aren't safe... would you walk with me? {pay} emeralds when we get there. (say yes) | It asks you to walk it to a village. |
| | wait up! don't leave me out here. | You got too far ahead. |
| | we made it! thank you, really. as promised. | You got there; it pays. |
| Duel | {player}! you look like you can handle yourself. 1v1? {stake} diamonds says I win. hit me if you're in. | It challenges you. |
| | fair's fair. good fight. | You won; it pays the stake. |
| | hey! I gave up! it's over! | You kept hitting it after it gave up. |
| Race | hey {player}, race you! first one to the top of that hill {way} of here ({x y z}) wins. {bet} emeralds says I beat you. say go when you're ready! | It challenges you to a race. |
| | a bet's a bet. you're quick! | You won; it pays. |
| Travelling merchant | hello there! I've a few rare things, if you've got emeralds. Have a look. | It walks up to you; right-click it to trade. |
| | sold out, I'm afraid. | It has nothing left. |
| Mugging | stop right there, {player}. {demand} emeralds (or gold, or diamonds), or your life. throw it here. | A bandit stops you. |
| Campfire party | hey, {player}! come join us! | Evening: Drifters round a campfire call you over. |
| Feud | thanks for the help. here, you earned it. | You helped one side of a fight between Drifters. |
| Thief | you stopped them! here, a share for your trouble. | You stopped a thief who robbed a Drifter. |
| Hostage | I hid these from them. they're yours. | You freed it from its captors. |
| Trapped miner | I owe you one. here, take what I dug up. | You dug it out. |
| Wanted poster | Wanted: {target}, for {crime}. {reward} diamonds to whoever brings them down. | A Drifter tells you about a wanted Drifter... |
| | Here, this map shows where they live. · Here, this map shows where they were last seen. | ...and hands you a map. |
| Wanted reward | Heard you brought down {target}. As promised. | You killed the wanted Drifter; it pays. |
| Warbands | you fought well, friend. the war chest is yours. · you fought well, friend. take what you like from the field. | You helped one side win a war between groups. |

## 6. Not chat, but close

- **Shop keeper:** when you right-click a town's stall keeper with nothing on offer, you alone see "<{name}> nothing to
  sell right now, come back later".
- **Manhunt:** messages like "[Drifter] Bob respawned and is back on the hunt." are announcements from the mod, not a
  Drifter talking.
- **Thoughts:** what a Drifter is thinking (`/drifter thoughts`) never goes to chat.
- **The chronicle:** Drifter: Civilizations's morning news ("Day 4: ...") is the mod reporting, not a Drifter talking.

## Where they live in the code

- Canned lines: `social/ChatLines.java` (the `LINES` table), said from `social/FriendlyGoals`, `SocialGoals`,
  `SocialWatcher`, `HostileGoals`, `BanditGoals`, `WarGoals`.
- Whim lines: the `drifter_whims/*.json` data files: the `chat` field, a `chat` whim's `lines`, or a `say` step. A
  datapack can add more (docs/WHIMS_HOWTO.md).
- News: `social/NewsBook.java`, its "what" texts in `social/SocialEvent.java`.
- Trades: `trade/DrifterDeals.java`.
- Encounters: `encounter/*.java`.
