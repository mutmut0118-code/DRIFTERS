# Every chat line a Drifter says

Everything a Drifter can say in chat as of 0.22.15, and when it says it; and what you can say to them (section 7). Lines show up as `<name> line`, like a player's
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

Always said, no rate limits. These are the encounters from milestone 21, in the order of docs/ENCOUNTERS.md. `{x y z}`
is a place's coordinates.

**Travellers who need something**

| Encounter | Line | When |
| --- | --- | --- |
| Hungry traveller | excuse me, {player}... I haven't eaten in days. could you spare some food? | It walks up to you. |
| | bless you. here, for your kindness. | You threw it food; it gives you something back. |
| | never mind... thanks anyway. | Nobody fed it. |
| Injured traveller | help! anyone! zombies! | Running from zombies, low on health, before it sees you. |
| | help! please, help me! · zombies! {player}, help! | Running from zombies, once it sees you. |
| | thank you... I'd be dead without you. Here, take this. | You killed the zombies and it lived. |
| Lost dog | excuse me... have you seen my dog? {dog}, red collar. he ran off {way} a while ago and won't come back. here, take this lead, in case you find him. | It asks you, handing you a lead. |
| | {dog}! here, boy! {dog}! | Calling for its dog. |
| | {dog}! there you are! oh, you silly dog. | The dog came back. |
| | thank you so much. here, it's not much, but take it. | You brought the dog back. |
| | ...no. {dog}? no... | The dog died. |
| Escort | excuse me, {player}! I'm going to the village {way} of here, about {far} blocks. the roads aren't safe... would you walk with me? {pay} emeralds when we get there. (say yes) | It asks you to walk it to a village. |
| | great! stay close, and keep your eyes open. | You said yes (see section 7). |
| | oh... alright. I'll take my chances. | You said no. |
| | never mind, then. | You didn't answer within a minute. |
| | well, well. a traveller with a bodyguard. | A bandit, when bandits jump you on the way. |
| | wait up! don't leave me out here. | You got too far ahead. |
| | fine, I'll go on alone. | You left it behind. |
| | we made it! thank you, really. as promised. | You got there; it pays. |
| Trapped miner | help! the tunnel caved in and my pickaxe broke. I'm stuck down here at {x y z} | Calling out from underground (the calls take turns)... |
| | anyone? still stuck at {x y z}. about {depth} blocks down | ...the second call... |
| | please... {x y z}. I can hear something up there | ...the third. |
| | light! someone's there! oh, thank goodness. | You dug through to it. |
| | I owe you one. here, take what I dug up. | It pays you. |
| Hostage | help! bandits have got me, at their camp at {x y z}! please! · anyone out there? {x y z}. there's {n} of them... | The captive calling out from the bandit camp (the calls take turns). |
| | {player}! over here! help me! | It sees you. |
| | we've got company! | A bandit, when you turn up. |
| | they're gone? they're gone! oh, thank you! | You beat the bandits. |
| | I hid these from them. they're yours. | It pays you. |
| Chased by a horde | RUN! there's loads of them! · zombies! run, {player}, RUN! | Someone runs past with a pack of zombies behind them. |

**Challenges and offers**

| Encounter | Line | When |
| --- | --- | --- |
| Duel | {player}! you look like you can handle yourself. 1v1? {stake} diamonds says I win. hit me if you're in. | It challenges you. |
| | let's go! | You hit it: the duel's on. |
| | coward. | You didn't hit it in time, or walked off. |
| | alright, alright! you win. | It's low on health and gives up. |
| | fair's fair. good fight. | You won; it pays the stake. |
| | ha! good fight. keep your stuff, it was for fun. | You're the one low on health: it stops and lets you off. |
| | hey! I gave up! it's over! | You kept hitting it after it gave up. |
| Race | hey {player}, race you! first one to the top of that hill {way} of here ({x y z}) wins. {bet} emeralds says I beat you. say go when you're ready! | It challenges you. |
| | 3... · 2... · 1... · go! | You said go (see section 7): the countdown. |
| | suit yourself. | You said no. |
| | chicken. | You didn't answer within a minute. |
| | no way! you win. fair and square. | You got to the top first. |
| | a bet's a bet. you're quick! | It pays you. |
| | ha! I win! better luck next time, {player}. | It got there first. |
| Travelling merchant | hello there! I've a few rare things, if you've got emeralds. Have a look. | It walks up to you; right-click it to trade. |
| | sold out, I'm afraid. | It has nothing left. |
| | safe travels, {player}. | It moves on. |
| Campfire party | hey, {player}! come join us! | Evening: Drifters round a campfire call you over. |
| | woo! · best night ever · ha! nice one · who brought the cake? · another one! | Cheering during the party. |
| | goodnight, everyone! | The party's over. |

**Danger**

| Encounter | Line | When |
| --- | --- | --- |
| Mugging | stop right there, {player}. {demand} emeralds (or gold, or diamonds), or your life. throw it here. | A bandit stops you. |
| | pleasure doing business. move along. | You threw it enough. |
| | your choice. | You hit it, walked off or waited too long: it attacks. |
| Ambush | now! get them! · your money or your life, {player}! | Bandits lying in wait on the road spring the trap. |
| Burglar | uh oh. | A thief in your base, caught (you hit it, or it saw you near) after it took something. |
| Thief | stop, thief! · somebody stop {thief}! | A Drifter chasing someone who robbed it. |
| | you stopped them! here, a share for your trouble. | You stopped the thief; it pays you. |
| Bounty hunters | {player}! there's a price on your head, from {who}. we've come to collect. | Hunters come for you (a price on your head for killing Drifters). |
| | job's done | They killed you. |
| Revenge squad | {player}! you killed {victim}. they were our friend. · {player}! {victim} gave up, and you killed them anyway. | The friends of a Drifter you killed come for you (the second line if it had given up). |
| | this is for {victim}! | Joining the fight. |
| | that was for {victim}. | They killed you. |
| Village raid | take everything! · burn it down! | Raiders reaching the village you're in. |
| | fall back! fall back! | The raiders lost. |

**Wars, feuds, the wanted and the passing**

| Encounter | Line | When |
| --- | --- | --- |
| Feud | you'll pay for what you did to my farm! · that was MY diamond! · liar! cheat! · you burned my house down! · come on then! · I'll teach you! | Two Drifters fighting each other, shouting. |
| | thanks for the help. here, you earned it. | You helped one side and it won. |
| Warbands | for {army}! · hold the line! · {army}, with me! · charge! · stand fast, {army}! | Captains rallying their side. |
| | you'll regret picking a side, {player}! | You attacked one side (its captain). |
| | stay out of this, stranger! | A soldier, when you get in the middle. |
| | victory! the field is ours! | One side won. |
| | fall back! fall back! | The other side lost. |
| | you fought well, friend. the war chest is yours. · you fought well, friend. take what you like from the field. | You fought for the winners. |
| Wanted poster | Wanted: {target}, for {crime}. {reward} diamonds to whoever brings them down. | A Drifter tells you about a wanted Drifter... |
| | Here, this map shows where they live. · Here, this map shows where they were last seen. | ...and hands you a map. |
| Wanted reward | Heard you brought down {target}. As promised. | You killed the wanted Drifter; it pays. |
| Convoy | morning, stranger! bound for {place}, {way} of here. safe travels! (or "good day, stranger!...") | Riders passing through greet you ("morning" before noon). |
| Mourner | {friend} was a good friend... · creepers. never saw it coming. · did you know them? ...no. no one really did. | Someone at a grave by the road, talking to you (one after the other). |
| Builders | hello! putting up a waystation for travellers. you're welcome to it. | A building crew turns up near you. |
| | there. that'll keep the rain off. | They finished it. |

## 6. Not chat, but close

- **Shop keeper:** when you right-click a town's stall keeper with nothing on offer, you alone see "<{name}> nothing to
  sell right now, come back later".
- **Manhunt:** messages like "[Drifter] Bob respawned and is back on the hunt." are announcements from the mod, not a
  Drifter talking.
- **Thoughts:** what a Drifter is thinking (`/drifter thoughts`) never goes to chat.
- **The chronicle:** Drifter: Civilizations's morning news ("Day 4: ...") is the mod reporting, not a Drifter talking.

## 7. What you can say to them

Drifters only listen to chat in three places. Everything else in an encounter is done, not said. To accept a duel you
hit the challenger; to pay a mugger or feed a hungry traveller you throw the items at it (Q); to trade with a merchant or a
stall keeper you right-click it.

| What you type | Who listens | What happens |
| --- | --- | --- |
| **A Drifter's name** (anywhere in your message, any capitals, as a whole word), within 32 blocks | Any Drifter with that name | It stops, turns toward where your voice came from and looks for you. Seeing someone it doesn't mind, it nods and may answer (HUH: "?", "yes?", "hm?", "what", "yo?"). |
| **yes** · ok · okay · sure · yeah · yep · fine · go · deal · alright | The escort, after it asks | It accepts: "great! stay close, and keep your eyes open." |
| **no** · nope · nah | The escort, after it asks | "oh... alright. I'll take my chances." and it goes off alone. |
| **go** · ready · yes · ok · okay · sure · deal · lets · yeah | The racer, after it challenges you | The countdown starts ("3... 2... 1... go!"). |
| **no** · nope · nah | The racer, after it challenges you | "suit yourself." and it leaves. |

The words can be anywhere in your message ("yeah sure!", "ok lets go"). Capitals and punctuation don't matter. The escort
and the racer only listen to the player they asked, and each gives up after a minute with no answer.

## Where they live in the code

- Canned lines: `social/ChatLines.java` (the `LINES` table), said from `social/FriendlyGoals`, `SocialGoals`,
  `SocialWatcher`, `HostileGoals`, `BanditGoals`, `WarGoals`.
- Whim lines: the `drifter_whims/*.json` data files: the `chat` field, a `chat` whim's `lines`, or a `say` step. A
  datapack can add more (docs/WHIMS_HOWTO.md).
- News: `social/NewsBook.java`, its "what" texts in `social/SocialEvent.java`.
- Trades: `trade/DrifterDeals.java`.
- Encounters: `encounter/*.java`.
