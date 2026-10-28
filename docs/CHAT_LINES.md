# Every chat line a Drifter says

Everything Drifters say in chat, when they say it, and what you can say to them (generated from the data files; 2513 lines in the chat data, 242 in whims, and 2000 answer phrases they understand).

Lines come from `data/drifter/drifter_chat/*.json` (a datapack can add more: files add to each other) and the whims' own files. They're written the way players type in Minecraft chat (the user's guidebooks "How Minecraft Players Chat" and "The Science of Gamer Text Chat"): lowercase, no final period, clipped words (dias, obby, coords), rituals (o/, wb, rip, gg, gn, o7), and each personality its own voice. `/drifter config chat false` turns all of it off.

In the lines, `{player}` is your name, `{name}` the Drifter's own, `{who}` another's, `{way}` a direction, `{coords}` x y z, and the rest as each says.

## How it picks and how often it talks

- **Its voice.** A Drifter of a personality with lines of its own (newbie, builder, PvP sweat, bandit...) uses them about two times in three; otherwise everyone's.
- **Who hears it.** Only players near the Drifter (48 blocks, set by "Chat heard within" on the config screen), plus the player an encounter is with; the server log gets everything.
- **Not twice in a row.** Each remembers its last couple dozen lines and picks a different one while it can, and not a line anyone near said in the last half minute (one "gm", then "morning").
- **Chattiness** (config screen, or `/drifter config chattiness quiet|normal|lively`): how much they chat on their own. Quiet is less than half as much, lively twice as much: their talks with each other and with you, the canned reactions, the rounds, bystanders. Answers to what you ask them, and encounters, are always said.
- **Typing like players** ("Typos and split messages", or `/drifter config typing true|false`): now and then a typo (letters swapped, dropped, doubled, the key next to it), the tidy often fixing it with "*word" a moment later; a longer line with a comma (or a question midway) sometimes sent in two, as a joke and its punchline. Newbies slip most, builders and hermits least; never in names or numbers.
- **Habits.** For the canned reactions (section 1) it has a habit: the same Drifter greets the same way most of the time, now and then another line.
- **Rate limits.** Each Drifter says one line in 20 seconds at most (the same kind once a minute), and all of them together four in 10 seconds. Quiet ones (low sociability) mostly don't bother. Encounter lines (section 5) are always said: it's talking to you.
- **Try them.** `/drifter lines say <key>` makes the nearest Drifter say a line of that kind (with made-up names for the blanks); `/drifter lines meaning <text>` says what a message would mean to a Drifter.

## 1. Reactions (things that happen to it)

#### `hi`

A friendly Drifter that likes you walks over and greets you (crouching at you).

- **everyone:** hi · hey · hello · yo · o/ · sup · heyo · hiya · yo {player} · hey {player} · o/ {player} · hi there · ayo · wsg
- **casual:** yo · sup · hey hey · yo whats up · heyy · o/ · wassup {player}
- **newbie:** hiii · hello!! · hi {player}!! · hey can we be friends · hi im new here · hello :) · yoo hi · omg hi
- **builder:** o/ · hey {player} o/ · hello! nice to see someone · heya, come see the build later · o/ hi
- **miner:** yo · hey · o/ · sup, just got back from mining · hey, found any dias?
- **homebody:** hi! :) · hello, welcome · hiya {player} · oh hi! good to see you · o/ hello
- **speedrunner:** yo · hi · o/ · hey gtg soon tho · sup
- **nomad:** o7 traveller · hey stranger · o/ where you headed · well met · hey, long road?
- **hoarder:** hi. dont touch my chests · hey · oh. hi · hello {player}... what do you want
- **hermit:** hm · ...hi · oh · o/
- **PvP sweat:** yo · sup · hey 1v1 later? · o/ nice gear · yo {player}
- **bandit:** well hello there · hey {player} · nice stuff you got there · hey friend
- **helper:** hey!! need anything? · hi {player}! o/ · hello! welcome welcome · hey! you good? · o/ hi friend

#### `huh`

You say its name in chat (or a part of it, 4 letters or more) within 32 blocks: it turns to look for you, nods, and may answer.

- **everyone:** ? · yes? · hm? · what · yo? · ya? · wsp · huh · mhm? · whats up · you called? · me?
- **newbie:** yes?? · what what · me?? hi · huh??
- **homebody:** yes? :) · hm? what is it · oh, yes?
- **hermit:** what · ...? · hm
- **PvP sweat:** what · ? · 1v1?
- **bandit:** what do you want · yeah?
- **helper:** yes! need something? · hey whats up? · here! what is it?

#### `thanks`

You throw it an item from close by (a gift), or kill a mob that was attacking it.

- **everyone:** ty · thx · thanks · tysm · ty! · ty ty · thank you · tyvm · oh ty · appreciate it · ty o7 · ty fr
- **newbie:** omg thank you!! · tysm!!! · thanks!! :D · wait for me?? ty
- **homebody:** oh thank you! · thats so kind, ty · ty, really
- **hermit:** ...ty · thanks
- **PvP sweat:** ty · w · ty bro
- **bandit:** heh, thanks · ill take it
- **hoarder:** mine now. ty · ooh ty · ty, going in the chest
- **helper:** ahh ty!! you didnt have to · thank you so much! · ty! ill pay it back

#### `gg_won`

It killed you (a hostile Drifter, over your body).

- **everyone:** gg · ggs · gg wp · rip · gg go next · sorry lol · gg {player}
- **PvP sweat:** gg · ez · L · gg ez · skill issue · get good · too easy · sit
- **bandit:** should have paid · gg, ill take that · nothing personal · thanks for the loot
- **homebody:** sorry!! you came at me · oh no, sorry · i didnt want that
- **helper:** oh no, sorry {player} · gg, want your stuff back? · sorry!! that got out of hand
- **newbie:** i won?? · omg gg · wait i actually won

#### `gg_lost`

You killed it (said as it dies).

- **everyone:** gg · rip · gg wp · ggs · rip me · oof · ow · really? · wow ok · unlucky
- **PvP sweat:** gg · lag · nice ping · rematch · 1v1 again · ok that was lucky
- **bandit:** you got lucky · this isnt over · my crew will hear about this
- **homebody:** why · i didnt do anything · that was mean
- **newbie:** noooo · my stuff!! · why did you do that
- **hermit:** ... · figures
- **helper:** ow... why · i was trying to help

#### `stop`

Warning you off: it doesn't trust you and won't fight, so it tells you to back off.

- **everyone:** go away · stop · back off · wait · dont · leave me alone · dont come closer · personal space pls · bro back up · nope, stay there
- **homebody:** please go away · stay away from my house · im not looking for trouble
- **hermit:** leave · go · away · not today
- **hoarder:** get away from my stuff · dont even think about it · these are MY chests
- **newbie:** stop it!! · go awayyy · dont hurt me pls
- **PvP sweat:** dont · you want this? · try it
- **helper:** hey, easy · lets not do this · can we not?

#### `ouch`

You hit it once (the first hit).

- **everyone:** bruh · ? · hey! · stop · ow · what was that for · ????? · excuse me · oi · dude · rude
- **PvP sweat:** oh you want to fight? · bad idea · ? · 1v1 then
- **bandit:** big mistake · you just picked the wrong one
- **homebody:** ow! why? · please dont · that hurt
- **newbie:** ow!! · why did you hit me · hey!! no hitting
- **hermit:** ... · dont
- **helper:** ow, accident? · hey, careful!

#### `thief`

It caught you at its chest with things gone, or sees you holding something missing from its chest.

- **everyone:** thief · hey thats mine · give it back · my stuff?? · bro stole my stuff · thats MY gear · WHO TOOK MY STUFF · put it back · are you serious · who raided my chest
- **hoarder:** THIEF · THATS MINE · my chest!! my stuff!! · you will pay for that · i counted everything
- **homebody:** my things... why · please give it back · i trusted you
- **newbie:** hey thats mine!! · give it back!!! · not cool
- **bandit:** stealing from a thief? bold · you dont steal from me
- **PvP sweat:** youre dead · give it back or fight

#### `war_cry`

A raid (bandits, or a group at war) reaches the base it's raiding.

- **everyone:** CHARGE · get them · raid time · for the crew! · lets go · attack!! · LETS GOOO · GET IN THERE · go go go · now! · hit the base
- **bandit:** take everything · leave nothing · nobody walks away · loot it all
- **PvP sweat:** ez raid · lock in · theyre cooked · go go go
- **nomad:** for the road! · onward!
- **helper:** stay together! · watch each others backs!

## 2. Chat rituals (answering you and the game)

Players reply to each other and to the game's own messages; so do Drifters. A moment after (as someone types), one or two of them.

#### `greet_back`

You say hi in chat (hi, hey, yo, o/, sup, gm...): one or two Drifters within 64 blocks who don't mind you say it back.

- **everyone:** o/ · \o · hi · hey · yo · hiya · heyo · hey {player} · o/ {player} · sup · hello · yo yo · hey hey
- **casual:** yo · sup {player} · ayo
- **newbie:** hiii {player}!! · hello!! · HI
- **builder:** o/ morning · hey {player} o/ · heya
- **homebody:** hi {player}! :) · hello hello · oh hi!
- **nomad:** o7 · o/ traveller
- **hermit:** o/ · hm. hi
- **helper:** hey!! o/ · hi {player}! need anything? · o/ welcome
- **PvP sweat:** yo · sup

#### `night_back`

You say good night in chat (gn, night, going to bed...).

- **everyone:** gn · gn {player} · night · nn · gn o/ · night night · sleep well · gn o7 · gnight
- **homebody:** good night {player} :) · sleep well!
- **newbie:** gnnn · night night!!
- **PvP sweat:** gn · gn, 1v1 tomorrow
- **hermit:** gn
- **helper:** gn! sleep well o/

#### `bye_back`

You say bye in chat (cya, gtg, brb, o7...), or leave the game (by day).

- **everyone:** cya · o7 · bye · cya {player} · later · o/ · take care · see ya · bye o/
- **helper:** bye {player}! come back soon · cya! o/
- **nomad:** o7 safe travels
- **hermit:** bye

#### `bye_night`

You leave the game at night.

- **everyone:** gn · gn {player} · night · gn o7 · sleep well · gn all · gnn {player}

#### `you_welcome`

You say thanks in chat (ty, thx, tysm...): the nearest friendly Drifter within 16 blocks.

- **everyone:** np · no problem · np o/ · anytime · np np · all good · sure thing · dw about it · yw · ofc
- **helper:** anytime!! · np, happy to help · of course! :)
- **hoarder:** dont get used to it · np. you owe me
- **hermit:** mhm · np
- **PvP sweat:** np · ez
- **newbie:** yw!! · np :D

#### `welcome_back`

You join the world and a Drifter round you knows you.

- **everyone:** wb · wb {player} · o/ · welcome back · wb o/ · hey wb · oh hey wb · {player}! wb
- **newbie:** WB!! · yay {player} is back
- **homebody:** welcome back! :) · wb, missed you
- **hermit:** wb
- **helper:** wb!! o/ · welcome back {player}!

#### `welcome_new`

You join and none of the Drifters round you know you.

- **everyone:** welcome! · o/ welcome · hi new person · welcome {player} o/ · hey, welcome · new face! hi
- **helper:** welcome!! need help getting started? · hi {player}! ask if you need anything · welcome o/ spawn is safe-ish
- **newbie:** hi are you new too?? · welcome!! im new too
- **bandit:** fresh meat · welcome... heh
- **hermit:** hm. new one

#### `rip`

Your death message (Drifters within 160 blocks read chat): an ordinary death.

- **everyone:** rip · F · unlucky · rip {player} · oof · rip bro · rip your stuff · o7 · unlucky {player} · rip, want help getting your stuff? · f in chat
- **helper:** oh no, need help getting your stuff back? · rip! want me to come with? · unlucky, ill help you get your stuff
- **PvP sweat:** L · skill issue · rip lol · unlucky · get good
- **bandit:** free loot somewhere · rip... wheres the stuff
- **homebody:** oh no! are you ok? · rip, careful out there
- **hermit:** rip
- **newbie:** noooo rip · rip!!

#### `rip_funny`

Your death message, a silly death: a fall, lava, drowning, a cactus, a berry bush, a wall, starving, fire...

- **everyone:** lmao · rip lol · lmaooo · bro · im dead · how · rip lmao · nice one · skill issue · classic · unlucky lmao · {player} im crying
- **helper:** lmao rip, need help? · oh no lmao
- **homebody:** oh dear · rip, be careful!
- **hermit:** heh
- **PvP sweat:** L · skill issue · how do you even do that

#### `rip_drifter`

A Drifter's death message: another Drifter within 160 blocks (now and then).

- **everyone:** rip {who} · rip · F · o7 {who} · rip {who} o7 · nooo {who} · unlucky {who}
- **PvP sweat:** rip · unlucky
- **bandit:** one less to share with · rip i guess
- **hermit:** rip

#### `rip_friend`

A Drifter's death message: one of its friends (always).

- **everyone:** NOOO {who} · rip {who}... o7 · not {who} · {who} no · rest easy {who} · why {who} · o7 {who}, we wont forget

#### `congrats`

You make an advancement that shows in chat: one or two Drifters within 160 blocks.

- **everyone:** gg · W · gz · congrats · gg {player} · nice · W {player} · lets go · ggs · nice one · congrats o/
- **newbie:** woah gg!! · W!! · how did you do that
- **helper:** congrats!! :D · huge W {player}
- **PvP sweat:** gg · ok W
- **hermit:** gz
- **hoarder:** nice. what did you get

## 3. News, trades and the shop

#### `news.deed`

Two Drifters meet, one passes on news of something a player did ({what}); said a third of the time, with a player within 16 blocks.

- **everyone:** did you hear? {who} {what} · yo did u hear, {who} {what} · apparently {who} {what} · heard {who} {what} · psa: {who} {what} · ngl heard {who} {what} · word is {who} {what} · so {who} {what}. just saying

#### `news.death`

The same, news of a Drifter's death ({what} is its death message).

- **everyone:** did you hear? {who} {what} · rip {who}. {who} {what} · heard {who} {what} · {who} {what}. rip · apparently {who} {what} · o7 {who}, {what}

#### `trade.ask`

A Drifter asks another for things, for free (a player within 16 blocks).

- **everyone:** got any {things} to spare? · anyone got spare {things}? · need {things} pls · u got {things}? · can i have some {things} · spare {things}?

#### `trade.ask_pay`

A Drifter asks another for things, offering pay.

- **everyone:** got any {things} to spare? ill give you {payment} · wtb {things}, paying {payment} · {things} for {payment}? · trade you {payment} for {things} · need {things}, have {payment}

#### `trade.give_free`

The one asked says yes, giving it free.

- **everyone:** sure, have {goods} · here, {goods} · ofc, take {goods} · np, have {goods} · yeah have {goods}

#### `trade.agree`

The one asked says yes to a paid trade.

- **everyone:** deal · bet · ok deal · sure · W trade · ight · sounds good · deal o/

#### `trade.handover_gift`

The giver hands over a gift.

- **everyone:** here you go · there ya go · np · all yours · enjoy

#### `trade.handover_paid`

The giver hands over a paid trade.

- **everyone:** pleasure doing business · ty for the trade · W trade · good doing business · ggs on the trade

#### `shop.nothing`

You right-click a town's stall keeper who has nothing on offer (only you see it).

- **everyone:** nothing to sell right now, come back later · sold out rn, try tomorrow · shop's empty atm, come back later · nothing left today sorry · out of stock, check back later

## 4. Whims (things it does for fun)

Said as the whim starts (half the time), as the whole of a chat whim, or at one step of it. These live in the whims' own files (`data/drifter/drifter_whims/`, docs/WHIMS_HOWTO.md).

| Whim | When | Lines |
| --- | --- | --- |
| ring_the_bell | ringing the village bell (any) | ding ding · DING · bell go brr · ding dong · dinner time? · ding ding ding |
| slice_of_cake | having a slice of cake (any) | cake :) · cake time · free cake · mm cake · one slice wont hurt · cake W |
| snow_golem | building a snow golem (any) | made a snowman · built a snow golem · meet frosty · he lives · snow buddy acquired · look at my snowman |
| brag_about_gear | showing off its gear (any) | check out my armor · full iron btw · look at my sword · new pickaxe :) · prot 4 coming soon · just got eff 5 · look at this drip · new armor who dis · finally full iron · sharp 5 lets go |
| complain_about_mobs | complaining about the mobs (any) | so many zombies last night · creepers everywhere · a skeleton nearly got me · phantoms are the worst · who keeps letting creepers near my base · blew up my wall again · endermen took my grass block · drowned with tridents are op · spiders on my roof again · sleep pls the phantoms |
| ask_for_help | asking around for help (any) | anyone have iron? · need food lol · how do i make a piston · where are the diamonds · anyone got spare string · tpa? jk · drop coords to the nearest village · how do you get to the end · need obby for a portal · anyone got a spare bucket · how deep are dias again · can someone help me build a farm |
| smell_a_flower | stopping to smell a flower (day) | mm, nice · smells nice · flowers :) · pretty · cute flower |
| pat_a_dog | patting a dog (any) | good dog · who's a good boy · good boy · doggo!! · pets the dog · best boy |
| stare_at_its_reflection | staring at its reflection in the water (day) | looking good · huh. · is that me · ngl i look good · need a haircut · the drip |
| knock_on_a_door | knocking on a door to see who's in (day) | knock knock · anyone home? · hellooo? · anyone in? · room service · open up |
| hum_to_itself | humming to itself (any) | hm hm hmm · la la la · dum de dum · doo doo doo · mm mm mmm · la di da · hmm hm hmm hm |
| sunset_with_a_friend | watching the sunset with a friend (sunset) | look at that · nice sunset, huh · pretty sky tonight · golden hour o/ · we should do this more · nice view with you here |
| admire_a_diamond | turning a diamond over in its hands (any) | shiny · beautiful · dias :) · look at it sparkle · one day ill have a full set · precious |
| nod_to_the_golem | nodding to the iron golem (any) | evening, big guy · keep up the good work · o7 golem · stay strong big man · thanks for the protection · hey iron man |
| view_from_the_top | climbing up for the view (day) | nice view · woah · you can see so far from here · the view up here tho · worth the climb · is that a village over there · peak · ngl this view is crazy |
| good_morning | saying good morning (sunrise) | gm · morning · good morning · mornin · gm all · gm o/ · morning everyone · rise and grind · gm chat · another day another dollar |
| good_night | saying good night (sunset) | gn · night · going to bed · night all · gn all · gn o/ · sleep pls · bed · everyone sleep pls · skip the night? · zzz · nn |
| happy_dance | doing a little dance (any) | lets gooo · yesss · LETS GOOO · W · hype · we up · woo! |
| snowball_fight | starting a snowball fight (any) | snowball fight! · get rekt · incoming · SNOWBALL FIGHT · heads up · catch · you started this |
| oops_dropped_a_stack | dropped a stack by mistake (any) | oops |
| eats_a_spider_eye | eating a spider eye (not a good idea) (any) | ew · why did i eat that · that was a mistake · blegh · poison. great · note to self: dont eat that |
| brag_about_bow | showing off its unique bow (any) | found a unique bow · this bow is something else · look at this bow · wait till you see what this bow does · this bow is op · bow diff · new bow, who dis |
| brag_about_shield | showing off its shield (any) | new shield · this shield takes a beating · reinforced, finally · cant touch this · shield up · blocked lol |
| tallest_mountain | climbing the tallest mountain in sight (day) | made it to the top · top of the world · summit reached · W climb · king of the hill · worth it |
| hop_about | hopping about for no reason (any) | wheee · :) · boing · hop hop · bunny hopping · cant stop wont stop |
| go_for_a_swim | going for a swim (day) | water's nice · swim time · come in, waters warm · cannonball · just keep swimming |
| watch_sunset | watching the sunset (sunset) | sunset :) · pretty · look at that sunset · golden hour · nice sky tonight · mobs soon tho |
| watch_storm | watching the storm (thunder) | big storm · thunder!! · charged creeper weather · stay inside lol · look at that lightning · storms are cool ngl |
| small_talk | making small talk (any) | hows it going · nice day · anyone got food · found some iron earlier · this place is nice · what are you up to · hows everyone doing · anyone wanna go mining · whats everyone building · any1 need anything · nice weather · just vibing · chat is this real · productive day ngl · how long have you been on · what day is it · lowkey love this spot · whats the plan today |
| brag | bragging a bit (any) | got diamonds btw · my base is better · ez · not even hard · full stack of dias, no big deal · cant relate to being poor · my farm is goated · built that in a day · skill issue tbh · i dont lose · W me · aura +1000 |
| say_bored | saying it's bored (any) | bored · nothing to do · so bored rn · anyone wanna do something · bored out of my mind · someone entertain me · what now · ive done everything lol |
| warm_hands_by_the_fire | warming its hands by the fire (night) | ahh, that's better · nice and warm · cozy · warm finally · campfire vibes · dont let it go out |
| wave_to_a_friend | waving to a friend (any) | hey! · o/ · hiya · yo! · o/ hey · heyy · sup! |

## 5. Encounters (it's talking to you)

Always said, no rate limits.

#### `encounter.unsure`

You answer an escort or a race with something unclear (maybe, idk, hmm, why...): it asks again.

- **everyone:** is that a yes or a no · so... yes? · yes or no lol · ill take that as a maybe. yes? · uh, is that a yes? · need a yes or a no {player}

### Hungry traveller

#### `encounter.hungry.ask`

It walks up to you.

- **everyone:** excuse me, {player}... I haven't eaten in days. could you spare some food? · hey {player}, sorry to bother you... got any food? im starving · pls {player}, anything to eat? havent eaten in days · {player}... could you spare some food? even bread · hey, any food? im so hungry i could eat a raw potato
- **newbie:** {player} can i have food pls im starving · pls food :( · hiii do u have food im so hungry

#### `encounter.hungry.thanks`

You threw it food; it gives you something.

- **everyone:** bless you. here, for your kindness. · ty ty, here, take this · you saved me fr, here · omg ty. take this, its all i have · thank you!! here, for you

#### `encounter.hungry.giveup`

Nobody fed it.

- **everyone:** never mind... thanks anyway. · ok... nvm · its fine. ill find something · nvm, ty anyway

### Injured traveller

#### `encounter.injured.help_unseen`

Running from zombies, before it sees you.

- **everyone:** help! anyone! zombies! · HELP · somebody help!! zombies! · anyone?? zombies on me · HELP ME pls

#### `encounter.injured.help`

Running from zombies, once it sees you.

- **everyone:** help! please, help me! · zombies! {player}, help! · {player} HELP · pls help {player} im low · {player}!! zombies!! help · im on 2 hearts help

#### `encounter.injured.thanks`

You killed the zombies and it lived.

- **everyone:** thank you... I'd be dead without you. Here, take this. · ty ty ty. i was cooked. here · you saved me {player}. take this · omg ty, i was on half a heart. here · W {player}. take this, you earned it

### Lost dog

#### `encounter.lostdog.ask`

It asks you, handing you a lead.

- **everyone:** excuse me... have you seen my dog? {dog}, red collar. he ran off {way} a while ago and won't come back. here, take this lead, in case you find him. · hey {player}, have you seen a dog? {dog}, red collar. ran off {way}. here, take a lead in case · {player} pls, my dog {dog} ran off {way}... red collar. heres a lead if you find him

#### `encounter.lostdog.call`

Calling for its dog.

- **everyone:** {dog}! here, boy! {dog}! · {dog}!! come here! · {dog}? where are you boy · HERE {dog}! · {dog}!! treats!

#### `encounter.lostdog.found`

The dog came back.

- **everyone:** {dog}! there you are! oh, you silly dog. · {dog}!!! there you are · {dog}!! good boy, dont run off like that · omg {dog}!!

#### `encounter.lostdog.thanks`

You brought the dog back.

- **everyone:** thank you so much. here, it's not much, but take it. · ty {player}!! here, take this · you found him!! here, its yours · W human. take this

#### `encounter.lostdog.dead`

The dog died.

- **everyone:** ...no. {dog}? no... · {dog}... no... · no no no. {dog}... · not {dog}...

### Escort

#### `encounter.escort.ask`

It asks you to walk it to a village.

- **everyone:** excuse me, {player}! I'm going to the village {way} of here, about {far} blocks. the roads aren't safe... would you walk with me? {pay} emeralds when we get there. (say yes) · hey {player}, heading to the village {way} of here, ~{far} blocks. walk with me? {pay} emeralds when we get there (say yes) · {player}! could you escort me to the village {way}? its about {far} blocks, roads are sketchy. {pay} emeralds in it for you (say yes)

#### `encounter.escort.yes`

You said yes (section 10).

- **everyone:** great! stay close, and keep your eyes open. · ty!! stay close · W. lets go, stay near me · perfect. eyes open, mobs are everywhere · lets go. ill follow you

#### `encounter.escort.no`

You said no.

- **everyone:** oh... alright. I'll take my chances. · oh ok. ill go alone then · fair. cya · ok... wish me luck · nw, ill manage

#### `encounter.escort.noanswer`

No answer within a minute.

- **everyone:** never mind, then. · nvm then · ok... guess not · ill go alone i guess

#### `encounter.escort.bandit`

A bandit, when bandits jump you on the way.

- **everyone:** well, well. a traveller with a bodyguard. · look at this, brought a bodyguard · cute, a bodyguard · pay up, both of you

#### `encounter.escort.waitup`

You got too far ahead.

- **everyone:** wait up! don't leave me out here. · wait for me! · slow down pls · {player} wait up · dont leave me!!

#### `encounter.escort.alone`

You left it behind.

- **everyone:** fine, I'll go on alone. · guess im on my own · ok bye then · thanks for nothing

#### `encounter.escort.arrived`

You got there; it pays.

- **everyone:** we made it! thank you, really. as promised. · we made it!! ty, here you go · W, we're here. as promised · made it. ty {player}, heres your emeralds

### Trapped miner

#### `encounter.miner.call1`

Calling from underground (first call).

- **everyone:** help! the tunnel caved in and my pickaxe broke. I'm stuck down here at {coords} · HELP. cave-in, pick broke, stuck at {coords} · anyone?? stuck underground at {coords}, pick broke

#### `encounter.miner.call2`

Second call.

- **everyone:** anyone? still stuck at {coords}. about {depth} blocks down · still stuck at {coords}, ~{depth} blocks down · pls someone, {coords}, {depth} down

#### `encounter.miner.call3`

Third call.

- **everyone:** please... {coords}. I can hear something up there · {coords}... anyone? · still here. {coords}. pls

#### `encounter.miner.freed`

You dug through to it.

- **everyone:** light! someone's there! oh, thank goodness. · LIGHT. omg ty · you found me!! ty · finally, air

#### `encounter.miner.thanks`

It pays you.

- **everyone:** I owe you one. here, take what I dug up. · i owe you. take what i mined · ty fr. heres my haul · you saved me, take the ores

### Hostage

#### `encounter.hostage.call1`

Calling out from the bandit camp.

- **everyone:** help! bandits have got me, at their camp at {coords}! please! · HELP. bandits got me, camp at {coords} · anyone, bandits took me. {coords}

#### `encounter.hostage.call2`

Calling out again.

- **everyone:** anyone out there? {coords}. there's {n} of them... · still here at {coords}, {n} of them · pls... {coords}. {n} bandits

#### `encounter.hostage.seen`

It sees you.

- **everyone:** {player}! over here! help me! · {player}!! here!! help · OVER HERE {player} · {player} pls get me out

#### `encounter.hostage.bandit`

A bandit, when you turn up.

- **everyone:** we've got company! · someones here! · INCOMING · we got a visitor · inc!!

#### `encounter.hostage.freed`

You beat the bandits.

- **everyone:** they're gone? they're gone! oh, thank you! · theyre gone!! ty ty · omg youre a legend · free!! ty

#### `encounter.hostage.thanks`

It pays you.

- **everyone:** I hid these from them. they're yours. · hid these from them, theyre yours · here, kept these hidden. for you · take these, you earned them

### Chased by a horde

#### `encounter.horde.run`

Someone runs past with a pack of zombies behind them.

- **everyone:** RUN! there's loads of them! · zombies! run, {player}, RUN! · RUNNN · RUN THEYRE BEHIND ME · horde!! run!! · {player} RUN

### Duel

#### `encounter.duel.ask`

It challenges you (hit it to accept).

- **everyone:** {player}! you look like you can handle yourself. 1v1? {stake} diamonds says I win. hit me if you're in. · 1v1 {player}? {stake} dias says i win. hit me if youre in · yo {player}, duel? {stake} dias on it. hit me to start
- **PvP sweat:** 1v1 me {player}. {stake} dias. hit me if you're not scared · {player} 1v1? {stake} dias, first to fold loses. hit me · nice gear {player}. 1v1 for {stake} dias? hit me

#### `encounter.duel.accept`

You hit it: the duel is on.

- **everyone:** let's go! · LETS GO · bet. lets go · its on · lock in · gl
- **PvP sweat:** you'll regret that · lets go · ok its on · gl, youll need it

#### `encounter.duel.coward`

You didn't hit it in time, or walked off.

- **everyone:** coward. · scared? · L · figured · ok coward

#### `encounter.duel.yield`

It's low on health and gives up.

- **everyone:** alright, alright! you win. · ok ok gg you win · i yield!! gg · stop stop, gg · gg you got me

#### `encounter.duel.paid`

You won; it pays.

- **everyone:** fair's fair. good fight. · gg wp. heres your dias · ggs. as promised · fair. take the dias

#### `encounter.duel.spare`

You're low on health: it stops and lets you off.

- **everyone:** ha! good fight. keep your stuff, it was for fun. · gg, keep your stuff · gg wp, it was just for fun · ggs, no hard feelings
- **PvP sweat:** gg ez. keep your stuff · too easy. keep it · gg, practice more

#### `encounter.duel.after_yield`

You kept hitting it after it gave up.

- **everyone:** hey! I gave up! it's over! · i SAID gg!! · its over!! stop · dude i yielded · STOP i gave up

### Race

#### `encounter.race.ask`

It challenges you (say go).

- **everyone:** hey {player}, race you! first one to the top of that hill {way} of here ({coords}) wins. {bet} emeralds says I beat you. say go when you're ready! · race {player}? top of the hill {way} of here ({coords}). {bet} emeralds. say go! · {player}, race to that hill {way} ({coords})? {bet} emeralds on it. say go when ready
- **speedrunner:** race? hill {way}, {coords}. {bet} emeralds. say go · {player} race me to {coords} ({way}). {bet} emeralds. say go

#### `encounter.race.no`

You said no.

- **everyone:** suit yourself. · ok fair · your loss · chicken · boring

#### `encounter.race.noanswer`

No answer within a minute.

- **everyone:** chicken. · ok, chicken · guess not · scared lol

#### `encounter.race.count`

The countdown (3, 2, 1).

- **everyone:** {count}... · {count} · {count}!

#### `encounter.race.go`

Go!

- **everyone:** go! · GO · GOOO · go go go

#### `encounter.race.player_won`

You got there first.

- **everyone:** no way! you win. fair and square. · no way. gg you win · ok you're fast. gg · how?? gg

#### `encounter.race.paid`

It pays you.

- **everyone:** a bet's a bet. you're quick! · bets a bet, here · ggs, take the emeralds · fair. heres your emeralds

#### `encounter.race.won`

It got there first.

- **everyone:** ha! I win! better luck next time, {player}. · W. gg {player} · ez. better luck next time · first! gg {player}
- **speedrunner:** ez · too slow {player} · gg, work on your routing

### Travelling merchant

#### `encounter.merchant.hello`

It walks up to you (right-click to trade).

- **everyone:** hello there! I've a few rare things, if you've got emeralds. Have a look. · hey! got some rare stuff, emeralds only. have a look · wts rare stuff, emeralds only. take a look · o/ traveller, got rare goods if you've got emeralds

#### `encounter.merchant.soldout`

It has nothing left.

- **everyone:** sold out, I'm afraid. · sold out sorry · nothing left, ty for shopping · all gone

#### `encounter.merchant.bye`

It moves on.

- **everyone:** safe travels, {player}. · o7 {player} · cya {player}, safe travels · take care o/

### Campfire party

#### `encounter.party.invite`

Evening: they call you over.

- **everyone:** hey, {player}! come join us! · {player}! come hang · yo {player} come party · {player} theres cake, come join!

#### `encounter.party.cheer`

Cheering during the party.

- **everyone:** woo! · best night ever · ha! nice one · who brought the cake? · another one! · lets gooo · W night · cake time · this is the best · fireworks!! · WOOO

#### `encounter.party.end`

The party's over.

- **everyone:** goodnight, everyone! · gn all! · gn o/ · that was fun, gn · night everyone

### Mugging

#### `encounter.mugging.demand`

A bandit stops you (throw it the money).

- **everyone:** stop right there, {player}. {demand} emeralds (or gold, or diamonds), or your life. throw it here. · {player}. {demand} emeralds, or gold, or dias. throw it here or else · hold up {player}. {demand} emeralds (or gold, or dias). throw it here. now

#### `encounter.mugging.paid`

You threw it enough.

- **everyone:** pleasure doing business. move along. · smart. move along · good choice. bye · nice doing business

#### `encounter.mugging.refused`

You hit it, left or waited too long.

- **everyone:** your choice. · wrong answer · ok then · bad call

### Ambush

#### `encounter.ambush.spring`

Bandits lying in wait spring the trap.

- **everyone:** now! get them! · your money or your life, {player}! · NOW · get them!! · surprise · nobody move

### Burglar

#### `encounter.burglar.caught`

A thief in your base is caught after taking something.

- **everyone:** uh oh. · oh no · uhh · this isnt what it looks like · bye

### Thief

#### `encounter.thief.shout`

A Drifter chasing someone who robbed it.

- **everyone:** stop, thief! · somebody stop {thief}! · STOP {thief} · THIEF · {thief} stole my stuff!! stop them · get back here {thief}

#### `encounter.thief.thanks`

You stopped the thief; it pays you.

- **everyone:** you stopped them! here, a share for your trouble. · you got them!! here, a share · W. heres a cut · ty {player}, take half

### Bounty hunters

#### `encounter.bounty.found`

Hunters come for the price on your head.

- **everyone:** {player}! there's a price on your head, from {who}. we've come to collect. · {player}. {who} put a bounty on you. nothing personal · there you are {player}. {who} wants you gone

#### `encounter.bounty.done`

They killed you.

- **everyone:** job's done · bounty claimed · gg, just business · thats that

### Revenge squad

#### `encounter.revenge.found`

The friends of a Drifter you killed come for you.

- **everyone:** {player}! you killed {victim}. they were our friend. · {player}. you killed {victim} · remember {victim}, {player}? we do

#### `encounter.revenge.found_murder`

The same, if it had given up.

- **everyone:** {player}! {victim} gave up, and you killed them anyway. · {victim} SURRENDERED {player} · {victim} gave up and you still did it

#### `encounter.revenge.join`

Joining the fight.

- **everyone:** this is for {victim}! · for {victim}!! · {victim} o7 · for {victim}

#### `encounter.revenge.done`

They killed you.

- **everyone:** that was for {victim}. · rest easy {victim} · for {victim}. o7 · done. {victim} can rest

### Village raid

#### `encounter.raid.attack`

Raiders reaching the village you're in.

- **everyone:** take everything! · burn it down! · loot it all! · go go go · grab what you can! · leave nothing!

#### `encounter.raid.retreat`

The raiders lost.

- **everyone:** fall back! fall back! · RETREAT · pull out! · fall back!!

### Feud

#### `encounter.feud.shout`

Two Drifters fighting each other.

- **everyone:** you'll pay for what you did to my farm! · that was MY diamond! · liar! cheat! · you burned my house down! · come on then! · I'll teach you! · you griefed my base! · give back my elytra! · you killed my cat!! · 1v1 me then! · you stole from me!

#### `encounter.feud.thanks`

You helped one side and it won.

- **everyone:** thanks for the help. here, you earned it. · ty for the backup, here · W assist. take this · appreciate it, heres something

### Warbands

#### `encounter.war.rally`

Captains rallying their side.

- **everyone:** for {army}! · hold the line! · {army}, with me! · charge! · stand fast, {army}! · {army}! lock in! · together {army}!

#### `encounter.war.regret`

You attacked one side.

- **everyone:** you'll regret picking a side, {player}! · wrong side {player} · big mistake {player}

#### `encounter.war.stay_out`

A soldier, when you get in the middle.

- **everyone:** stay out of this, stranger! · not your fight! · back off stranger · this isnt about you

#### `encounter.war.victory`

One side won.

- **everyone:** victory! the field is ours! · WE WON · victory!! · gg, the field is ours

#### `encounter.war.retreat`

The other side lost.

- **everyone:** fall back! fall back! · RETREAT · pull back!! · gg, fall back

#### `encounter.war.thanks_chest`

You fought for the winners (a war chest).

- **everyone:** you fought well, friend. the war chest is yours. · gg friend, the war chest is yours · ggs. take the war chest, you earned it

#### `encounter.war.thanks_field`

You fought for the winners (no chest).

- **everyone:** you fought well, friend. take what you like from the field. · gg, take what you want from the field · W fight. loot the field

### Wanted poster and reward

#### `encounter.wanted.poster`

A Drifter tells you about a wanted Drifter...

- **everyone:** Wanted: {target}, for {crime}. {reward} diamonds to whoever brings them down. · wanted: {target}, for {crime}. {reward} dias to whoever gets them · theres a bounty on {target}, for {crime}. {reward} dias

#### `encounter.wanted.map_home`

...and hands you a map to where it lives.

- **everyone:** Here, this map shows where they live. · heres a map to their base · map to where they live, here

#### `encounter.wanted.map_seen`

...or to where it was last seen.

- **everyone:** Here, this map shows where they were last seen. · map to where they were last seen · last seen here, take the map

#### `encounter.wanted.reward`

You killed the wanted Drifter; it pays.

- **everyone:** Heard you brought down {target}. As promised. · heard you got {target}. as promised · {target} is down? W. here you go

### Convoy

#### `encounter.convoy.hello_morning`

Riders passing through greet you (before noon).

- **everyone:** morning, stranger! bound for {place}, {way} of here. safe travels! · o/ morning! heading to {place}, {way}. safe travels

#### `encounter.convoy.hello`

The same, after noon.

- **everyone:** good day, stranger! bound for {place}, {way} of here. safe travels! · o7 stranger, heading to {place} ({way}). safe travels · hey! off to {place}, {way} of here. stay safe

### Mourner

#### `encounter.mourner.1`

At a grave by the road, first.

- **everyone:** {friend} was a good friend... · {friend} was the best... · rip {friend}...

#### `encounter.mourner.2`

Then.

- **everyone:** creepers. never saw it coming. · creeper got them. didnt even hear it · one creeper. thats all it took

#### `encounter.mourner.3`

Last.

- **everyone:** did you know them? ...no. no one really did. · you didnt know them huh. nobody did · ...o7 {friend}

### Builders

#### `encounter.builders.hello`

A crew turns up to build a waystation.

- **everyone:** hello! putting up a waystation for travellers. you're welcome to it. · o/ building a waystation for travellers, feel free to use it · hey! making a rest stop here, all welcome

#### `encounter.builders.done`

They finished it.

- **everyone:** there. that'll keep the rain off. · done! all yours · finished o/ enjoy · there we go

## 6. Conversations between Drifters

Two Drifters near each other with nothing pressing, and a player near enough to overhear, now and then strike up a conversation (54 topics, 1569 lines). They walk up to each other and face each other, and take turns, a moment between each as it is typed. **a** brings it up, **b** answers. A topic comes up only when its conditions hold: what each has done lately (their diary: ore mined, a death, a close call, mobs killed, a build, a trade, a place found), their gear, mood, home and town, how they feel about each other, news one has that the other does not, players they like or distrust, and the time and weather. A question carries its own answers. Lines are in `data/drifter/drifter_talk/`; `/drifter talk [topic]` makes the nearest two talk, `/drifter diary` shows what one has done.

#### found ore

*Needs:* a:found_ore, !a:found_rare  ·  *Effects:* trust

1. **a**: found {a.ore_count} {a.ore} today · got {a.ore_count} {a.ore} earlier · mined {a.ore_count} {a.ore} btw · {a.ore_count} {a.ore} today, not bad · hit a little {a.ore} vein, {a.ore_count} of it · yo {b}, found {a.ore_count} {a.ore}
   - **miner:** {a.ore_count} {a.ore} today. the mine provides · branch mining paid off, {a.ore_count} {a.ore} · {a.ore_count} {a.ore}, still hunting dias tho
   - **newbie:** i found {a.ore}!! {a.ore_count} of them!! · look i got {a.ore_count} {a.ore} · is {a.ore} good? i got {a.ore_count}
   - **hoarder:** {a.ore_count} more {a.ore} for the chests · added {a.ore_count} {a.ore} to the pile
2. **b**:
   - *b:found_ore:* nice, i got {b.ore_count} {b.ore} · same, {b.ore_count} {b.ore} here · W, i only got {b.ore_count} {b.ore} · ooh. i found {b.ore_count} {b.ore} myself
   - *rivals:* cool story · and? · thats it? · ok · i had more yesterday
   - *otherwise:* nice · W · ooh nice · lets go · where at *(answered: deep down / near my base / in a cave / y 16 / not telling lol)* · jealous ngl *(answered: lol / you'll find some / keep mining)* · nice, im still on stone tools lol · gg
   - *otherwise:* **newbie:** woah cool!! · how do you find it
   - *otherwise:* **helper:** nice!! proud of you · W, need help smelting it?
3. **a** (60%): deep down, branch mining · found it in a cave · right under my base lol · y 16 is the spot · took forever tho · almost died for it lol · creeper nearly got me down there
4. **b** (50%):
   - *friends:* save me some? · we should mine together sometime · gonna go look too · nice, share the spot?
   - *otherwise:* nice · gl with it · W · ok
5. **a** (40%) if friends: sure · ill show you later · deal · maybe lol · for you? ofc

#### found rare

*Needs:* a:found_rare  ·  *Effects:* trust

1. **a**: {b} I FOUND {a.ore} · found {a.ore_count} {a.ore} today!! · guess who got {a.ore} · {a.ore}. {a.ore_count} of them. lets gooo · W day, {a.ore_count} {a.ore}
   - **miner:** finally. {a.ore_count} {a.ore} · the grind paid off. {a.ore} · {a.ore_count} {a.ore}, told you it was down there
   - **newbie:** OMG I FOUND {a.ore} · {b} {b} i found {a.ore}!!! · is this real?? {a.ore}!!
   - **PvP sweat:** {a.ore_count} {a.ore}. new gear incoming · {a.ore}. youre all cooked
   - **hermit:** found some {a.ore} · {a.ore}. dont tell anyone
2. **b**:
   - *rivals:* lucky · ok and · probably got lucky · cool. i dont care · bet you cant even use them
   - *b:found_rare:* no way, me too! · same!! {b.ore_count} {b.ore} · twins lol, i got {b.ore} too
   - *otherwise:* NO WAY · W · lets gooo · huge · where?? *(answered: deep down / not telling / in a cave / y -58 / secret lol)* · GG · jealous fr · wait fr? *(answered: fr / dead serious / look)* · nice!! what you making *(answered: pickaxe first / armor / dunno yet / enchanting table)*
   - *otherwise:* **helper:** thats amazing!! · so happy for you
   - *otherwise:* **hoarder:** ...where exactly · any spare? · id put those somewhere safe
   - *otherwise:* **newbie:** woahhh · can i see??
3. **a** (70%):
   - *a:found_ore:* pickaxe first · gonna make a pick · armor time · saving them · enchanting table maybe · not telling where lol
4. **b** (50%): smart · W choice · nice · dont lose them · careful with creepers lol
- *Someone standing by may add:* where?? · share pls · no way

#### died story

*Needs:* a:died_lately

1. **a**: i {a.death} lol · so i {a.death} · {b} i {a.death}. again · rip me, {a.death} · worst day. i {a.death} · cant believe i {a.death}
   - **newbie:** i died!! i {a.death} · {b} i {a.death} :(
   - **PvP sweat:** i {a.death}. lag · {a.death}. not my fault · dont laugh. i {a.death}
   - **hermit:** {a.death} · died. {a.death}
2. **b**:
   - *rivals:* lmao · skill issue · L · classic · deserved
   - *friends:* nooo rip · rip! you ok? *(answered: yeah im fine / barely lol / my pride isnt)* · oh no, what happened *(answered: dont even ask / it was dumb / mobs / i got careless)* · rip, did you get your stuff back? *(answered: yeah / nope, despawned / working on it / most of it)* · o7
   - *otherwise:* rip · F · unlucky · oof · lmao how *(answered: dont ask / it was dumb / skill issue tbh)* · rip lol · happens to the best of us
   - *otherwise:* **helper:** oh no, need help getting your stuff? · rip!! want me to come?
   - *otherwise:* **PvP sweat:** L · skill issue
3. **a** (80%): lost everything · got my stuff back at least · lost my good pickaxe · had a full inventory too · all my iron, gone · despawned before i got back · it was so dumb
4. **b** (60%):
   - *friends:* ill give you some stuff · want some iron? · ill help you get back on your feet · we'll get it back
   - *rivals:* sucks to be you · should have been careful
   - *otherwise:* rip · that sucks · unlucky · happens · next time
5. **a** (40%) if friends: ty · ur the best · appreciate it · ty fr
- *Someone standing by may add:* rip · classic

#### close call

*Needs:* a:close_call

1. **a**: {a.close_call} almost got me earlier · nearly died to {a.close_call} · half a heart left after {a.close_call} · {a.close_call} had me on one heart · so close. {a.close_call} almost did it
   - **homebody:** i almost died to {a.close_call}, im staying home · {a.close_call} scared me so bad
   - **PvP sweat:** {a.close_call} almost clutched me · one heart vs {a.close_call}, still won
   - **newbie:** {a.close_call} almost killed me!! · i was on half a heart because of {a.close_call}
2. **b**:
   - *b:close_call:* same, {b.close_call} nearly got me · lol same, {b.close_call} for me · {b.close_call} almost got me too
   - *otherwise:* yikes · close one · glad you made it · careful out there · jeez · thats scary · ur still here tho
   - *otherwise:* **PvP sweat:** skill issue · should have won that easy
   - *otherwise:* **helper:** eat something, heal up · need food?
3. **a** (50%): ate a whole stack of bread after · never again · im fine now · took a nap after that · gonna make better armor

#### mob hunter

*Needs:* a:killed_many

1. **a**: killed {a.kills} mobs today · {a.mob_count} {a.mob}s down · cleared out {a.mob_count} {a.mob}s earlier · the {a.mob}s kept coming, got {a.kills}
   - **PvP sweat:** {a.kills} kills today. ez · farmed {a.kills} mobs. warmup
   - **homebody:** had to fight {a.kills} mobs to get home · so many {a.mob}s, i got {a.kills}
2. **b**: nice · beast · W · save some for me lol · how many arrows did that take *(answered: too many / used a sword mostly / all of them)* · xp farm era
   - **newbie:** woah thats so many · teach me how
3. **a** (50%): the xp tho · got a bunch of string at least · bones everywhere · rotten flesh, so much rotten flesh

#### built something

*Needs:* a:built_lately  ·  *Effects:* trust

1. **a**: finished my {a.built} · {a.built} is done · built a {a.built}, come see · finally done with the {a.built} · my {a.built} is finished, took forever
   - **builder:** {a.built} is done. pretty proud of this one · finished the {a.built}. the roof was a pain · come see the {a.built}, i did the gables myself
   - **newbie:** i built a {a.built}!! · my {a.built} is done!! come see
   - **hermit:** {a.built}'s done. dont visit
2. **b**:
   - *rivals:* mine's better · looks mid · ok · the roof is crooked · saw it. meh
   - *otherwise:* nice!! · W build · looks clean · ngl thats clean · ill come see · congrats · gg · nice, need help with the inside?
   - *otherwise:* **builder:** love the frame · nice proportions · clean build fr
   - *otherwise:* **helper:** looks amazing!! · proud of you
3. **a** (60%): ty · ty!! · still need to furnish it · need more glass · took so many logs · next one's gonna be bigger

#### gear show

*Needs:* !a:armor_none, !a:armor_leather

1. **a**: got full {a.armor} now · check out the {a.armor} armor · {a.armor} armor, finally · upgraded to {a.armor} · new {a.armor} set btw
   - **PvP sweat:** {a.armor} armor. 1v1? · full {a.armor}. try me · {a.armor} set. you're cooked
   - **newbie:** look at my armor!! · i have {a.armor} armor now!!
2. **b**:
   - *b:armor_diamond, !a:armor_diamond, !a:armor_netherite:* cute. i have diamond · {a.armor}? im in diamond lol · nice, diamond is next
   - *b:armor_none:* im still in nothing lol · jealous, no armor here · i have no armor, carry me
   - *otherwise:* nice · W · looking good · drip · solid · clean · nice, enchanted? *(answered: not yet / prot 2 / working on it / nope)*
3. **a** (40%): no enchants yet · need prot 4 · working on it · next stop diamond · ty

#### diamonds stash

*Needs:* a:has_diamonds

1. **a**: got {a.diamonds} dias on me · sitting on {a.diamonds} dias · {a.diamonds} diamonds, what should i make
   - **hoarder:** {a.diamonds} dias. not sharing · keeping my {a.diamonds} dias safe · {a.diamonds} diamonds and counting
2. **b**:
   - *friends:* pickaxe first, always · enchanting table! · sword, trust · share one? jk
   - *otherwise:* pickaxe · dont lose them · nice · jealous · careful, people will want those
3. **a** (50%): pickaxe it is · maybe armor · saving for an enchanting table · decisions decisions

#### traded

*Needs:* a:traded_lately

1. **a**: traded with {a.traded_with} earlier · {a.traded_with} hooked me up today · did a trade with {a.traded_with} · got some stuff off {a.traded_with}
2. **b**:
   - *rivals:* hope they ripped you off · ok
   - *otherwise:* nice · W trade? *(answered: W / yeah / eh)* · they fair? *(answered: yeah fair / pretty fair / kinda overpaid lol)* · good deal? *(answered: W trade / decent / meh)* · {a.traded_with} is alright
3. **a** (60%): fair enough · W trade · kinda overpaid lol · they were cool · ill trade with them again

#### saw place

*Needs:* a:saw_place

1. **a**: found a {a.place} out there · saw a {a.place} earlier · theres a {a.place} not far from here · {b}, found a {a.place}
   - **nomad:** passed a {a.place} on the road · found a {a.place}, worth the walk · the {a.place} out there is huge
2. **b**:
   - *friends:* coords? *(answered: later / dont remember / ill show you / somewhere north)* · drop coords · wanna go check it out? · take me next time
   - *otherwise:* nice · far? *(answered: pretty far / not that far / a bit of a walk)* · which way *(answered: that way / north i think / past the river / follow the road)* · any loot? *(answered: didnt go in / maybe / some chests / got scared lol)* · cool
3. **a** (70%):
   - *friends:* ill show you · later, i'll take you · its not far · follow me sometime
   - *rivals:* not telling · find it yourself
   - *otherwise:* pretty far · dunno, somewhere out there · didnt go in · got lost getting back lol

#### hungry

*Needs:* a:hungry

1. **a**: so hungry rn · anyone got food · starving · out of food lol · need food pls
   - **newbie:** im so hungry :( · can i have food pls
2. **b**:
   - *friends:* here, take some · i got bread · come by the farm · have some of mine
   - *rivals:* not my problem · go farm · skill issue
   - *otherwise:* farm some wheat · kill a cow · theres cows out east · i barely have any myself · try the farm
3. **a** (50%):
   - *friends:* ty ty · ur a lifesaver · ill pay you back
   - *otherwise:* guess ill go hunt · ok · fine · brb, cows

#### hurt

*Needs:* a:hurt

1. **a**: on like 3 hearts · barely alive rn · need to heal · ow · dont hit me, im one shot
2. **b**: eat something · go rest · what happened *(answered: mobs / fell off something lol / creeper / dont ask)* · you good? *(answered: ill live / barely / yeah, need food)* · stay inside for a bit
3. **a** (50%): ill be fine · mobs · fell off something lol · yeah ill eat

#### what doing

*Needs:* any time

1. **b**: wyd · what you up to · busy? *(answered: kinda / not really / a bit / never too busy for you)* · whatcha doing · what are you doing {a}
2. **a**:
   - *a:found_ore:* was about to {a.doing} · just got back from mining · gonna {a.doing}
   - *otherwise:* was about to {a.doing} · gonna {a.doing} · nothing much · just vibing · chilling · trying to {a.doing}
3. **b** (60%): nice · cool · same tbh · gl · fun · sounds boring lol

#### homeless

*Needs:* a:no_home

1. **a**: still dont have a base · need to find a spot for a base · homeless rn lol · anyone know a good spot to settle
2. **b**:
   - *friends, b:has_house:* you can stay at mine · build near me · theres room by my house
   - *otherwise:* plains are nice · find a river · near a village maybe · just dig into a hill · spawn is fine
3. **a** (50%): ty · might do that · good idea · maybe

#### hut to house

*Needs:* a:has_hut, !a:in_town

1. **a**: thinking of building a real house · my hut is so small · need an upgrade from the hut · gonna build a proper house soon
2. **b**: do it · you should · need logs? *(answered: yeah lots / i got some / always)* · stone house, fire proof · two floors
   - **builder:** timber frame, trust me · use a stone base, it looks better · do a gable roof
3. **a** (50%): gonna need so many logs · yeah · ty · maybe tomorrow

#### heard place

*Needs:* a:heard_place, !rivals  ·  *Effects:* pass_place

1. **a**: {a.heard_from} said theres {a.heard_what} {a.heard_where} · {a.heard_from} told me about {a.heard_what} {a.heard_where} · apparently theres {a.heard_what} {a.heard_where} · yo {b}, heard theres {a.heard_what} {a.heard_where}
   - **hoarder:** dont tell anyone, but {a.heard_what} {a.heard_where}
   - **helper:** oh {b}! {a.heard_from} said theres {a.heard_what} {a.heard_where}
2. **b**:
   - *friends:* ooh ty · nice, lets go check it out · ty, might go look · we should go together
   - *otherwise:* oh nice · might check it out · you believe them? · huh, ok · ty
   - *otherwise:* **hermit:** hm
   - *otherwise:* **nomad:** ooh, new place
   - *otherwise:* **miner:** ooh, any ore?

#### heard word bad

*Needs:* a:heard_bad, !rivals, !strangers  ·  *Effects:* pass_word

1. **a**: {a.word_from} says {a.heard_about} is trouble · watch out for {a.heard_about}, {a.word_from} said · heard {a.heard_about} is sketchy · {a.word_from} told me not to trust {a.heard_about}
2. **b**: for real? · noted · {a.heard_about}? wow · figures · ugh · i'll keep an eye out
   - **helper:** hm, maybe its a misunderstanding
   - **bandit:** heh

#### heard word good

*Needs:* a:heard_good, !rivals, !strangers  ·  *Effects:* pass_word

1. **a**: {a.word_from} says {a.heard_about} is cool · heard {a.heard_about} is nice · {a.word_from} vouches for {a.heard_about}
2. **b**: nice · good to know · cool · oh nice · yeah {a.heard_about} seems alright

#### heard player news

*Needs:* a:heard_news

1. **a**:
   - *a:heard_news_diamonds:* {a.news_from} found diamonds today · did you hear {a.news_from} got dias · {a.news_from} found dias, lucky
   - *a:heard_news_died:* {a.news_from} died earlier lol · rip {a.news_from}, died today
   - *a:heard_news_dragon:* {a.news_from} beat the dragon!! · {a.news_from} killed the ender dragon, crazy
   - *a:heard_news_built:* {a.news_from} finished a build · {a.news_from} built something new
   - *a:heard_news_nether:* {a.news_from} went to the nether and survived lol
   - *a:heard_news_end:* {a.news_from} went to the end
2. **b**: no way · nice · lol · huh · W · rip
   - **hermit:** hm
   - **newbie:** woah!!

#### heard player plan

*Needs:* a:heard_plan

1. **a**: {a.plan_from} went {a.heard_plan} · saw {a.plan_from} earlier, said they were going {a.heard_plan} · {a.plan_from} is off {a.heard_plan}
2. **b** (70%): nice · gl to them · hope they come back lol · cool · ok

#### how are you

*Needs:* !rivals  ·  *Effects:* trust

1. **a**: hows it going {b} · hey {b}, you good? · yo {b} how you doing · sup {b} · hows your day · o/ {b}, hows things
   - **newbie:** hi {b}!! how are you · heyyy {b}
   - **hermit:** {b}. · hm. {b}. you alright
   - **helper:** hey {b}! how are you doing? · {b}! all good?
2. **b**:
   - *b:mood_bored:* bored out of my mind · bored tbh · nothing to do lol · so bored · eh. bored
   - *b:mood_frustrated:* rough day · dont ask · everything keeps going wrong · ngl not great · im about to crash out
   - *b:mood_triumphant:* great actually!! · W day · best day ever · cant complain, lets go
   - *b:mood_confident:* good, feeling good · never better · pretty good ngl · locked in
   - *b:mood_anxious:* kinda on edge · jumpy, too many mobs · a bit nervous ngl · could be better
   - *b:mood_grieving:* not great · lost someone · ...been better · rough week
   - *b:mood_angry:* annoyed · dont even · someone messed with my stuff · mad tbh
   - *otherwise:* good hbu *(answered: good / same / alright)* · pretty good, you? *(answered: same / good / cant complain)* · not bad · chilling, you? *(answered: same / same lol / working)* · all good · cant complain · decent, wbu *(answered: not bad / same / alright)*
   - *otherwise:* **hermit:** fine · alive · ok
   - *otherwise:* **newbie:** good!! you? · great!!
3. **a**:
   - *a:mood_bored:* same, so bored · bored too · nothing to do here either
   - *a:mood_triumphant:* having a great day · W day for me · good, really good
   - *a:mood_frustrated:* rough one here too · not great tbh · dont ask lol
   - *otherwise:* good · same old · not bad · alright · chilling · cant complain · decent
4. **b** (40%): nice · cool · W · ok cya around · o/ · good to hear

#### morning

*Needs:* morning

1. **a**: gm · morning · gm {b} · mornin · good morning · gm o/
2. **b**: gm · morning · gm o/ · mornin · gm, sleep well? *(answered: yeah / nope, zombies / like a log / phantoms tho)* · morning {a}
   - **hermit:** mm · gm
   - **newbie:** good morninggg · GM!!
3. **a** (70%): phantoms kept me up · slept fine · zombies were banging on my door all night · barely slept · slept like a log · skipped the night, nice
4. **b** (50%): same · phantoms are the worst · nice · rip · gotta sleep more · ugh zombies

#### evening

*Needs:* evening, outside

1. **a**: getting dark · sun's going down · night soon · almost night · its getting late
2. **b**: yeah, heading home · gonna sleep soon · time to light some torches · mobs soon · already? · night shift time
3. **a** (60%): watch out for creepers · stay safe · gn when you go · same, going to bed · dont get blown up
4. **b** (40%): you too · gn · will do · o7

#### night out

*Needs:* night, outside

1. **a**: why are we outside at night lol · its so dark · mobs everywhere · did you hear that · zombies all over
2. **b**: good point · lets go in · i heard a creeper · stay close · just dont look at endermen
3. **a** (50%): yeah lets go · spooky · night is the worst · should have slept

#### rain

*Needs:* rain, outside

1. **a**: this rain tho · rain again · its pouring · ugh rain · nice weather huh
2. **b**: good for the crops at least · great fishing weather · the mobs dont burn tho · i like rain ngl · stay dry
3. **a** (50%): true · fair · still annoying · might go fishing actually

#### thunder

*Needs:* thunder, outside

1. **a**: thunderstorm! · did you see that lightning · huge storm · charged creeper weather lol · stay inside
2. **b**: dont stand near trees · mobs everywhere in this · i love storms · scary · lightning almost hit me
3. **a** (50%): lets wait it out · careful · same · lol
- *Someone standing by may add:* stay inside · charged creeper weather lol

#### nice day

*Needs:* clear, day, outside

1. **a**: nice day · good weather today · perfect day for building · sun's out · beautiful day
2. **b**: for real · good mining weather lol · yeah no mobs · nice · perfect
3. **a** (40%): gonna get stuff done · might go exploring · yeah

#### news

*Needs:* a:has_news  ·  *Effects:* news

1. **a**: did you hear? {a.news_who} {a.news_what} · yo, {a.news_who} {a.news_what} · apparently {a.news_who} {a.news_what} · heard {a.news_who} {a.news_what} · so {a.news_who} {a.news_what}. crazy
   - **homebody:** did you hear what happened? {a.news_who} {a.news_what} · {b}, {a.news_who} {a.news_what}. scary
   - **newbie:** omg did you hear?? {a.news_who} {a.news_what}
2. **b**: no way · wait what · fr? · dang · really? · huh · wow · thats crazy · who told you · rip
3. **a** (60%): yeah · for real · thats what i heard · crazy right · word gets around
4. **b** (40%): wild · gotta be careful · noted · glad it wasnt me

#### warn player

*Needs:* a:dislikes_a_player

1. **a**: watch out for {a.disliked_player} · dont trust {a.disliked_player} · {a.disliked_player} is bad news · stay away from {a.disliked_player} · if you see {a.disliked_player}, dont let them near your chests
   - **hoarder:** {a.disliked_player} better stay away from my chests · {a.disliked_player}. thief. remember the name
   - **PvP sweat:** {a.disliked_player}? ill handle them · if {a.disliked_player} shows up, call me
2. **b**:
   - *b:dislikes_a_player:* same, {b.disliked_player} too · agreed. and {b.disliked_player} · yeah, i dont trust {b.disliked_player} either
   - *otherwise:* why? *(answered: took stuff from my chest / hit me for no reason / just trust me / they're trouble)* · what did they do *(answered: robbed me / attacked me / broke my stuff / long story)* · noted · really? *(answered: yeah / dead serious / trust me)* · ok, will do
3. **a** (70%): took stuff from my chest · hit me for no reason · just a feeling · they're trouble · dont ask
4. **b** (50%): noted · thanks for the heads up · ill keep an eye out · wow ok
- *Someone standing by may add:* agreed · yeah, watch out for them

#### praise player

*Needs:* a:likes_a_player

1. **a**: {a.liked_player} is cool · {a.liked_player} helped me out earlier · if you see {a.liked_player}, say hi from me · {a.liked_player}'s alright · {a.liked_player} is a real one
2. **b**:
   - *b:likes_a_player:* yeah, so is {b.liked_player} · agreed. {b.liked_player} too
   - *b:dislikes_a_player:* better than {b.disliked_player} at least · i'll take your word for it
   - *otherwise:* good to know · nice · never met them · cool · ill say hi

#### town talk

*Needs:* a:in_town, groupmates  ·  *Effects:* trust

1. **a**: {a.town} is coming along · {a.town}'s getting big · love what we did with {a.town} · the store could use more cobble · anyone checked the market today? · need more houses in {a.town}
2. **b**: for real · proud of this place · ill drop some cobble off · more people moving in soon · we need a well · the roads look nice · agreed
3. **a** (60%): W town · best town around · we should build a wall · maybe a farm expansion · lets keep going
4. **b** (40%): {a.town} forever · o7 · lets go · W

#### group pride

*Needs:* groupmates, !a:in_town  ·  *Effects:* trust

1. **a**: {a.group} is the best crew · glad we teamed up · the crew's doing good · we should find more people
2. **b**: facts · agreed · o7 · W crew · we should start a town
3. **a** (40%): a town... · maybe someday · lets go · o7

#### catch up

*Needs:* friends  ·  *Effects:* trust

1. **a**: good to see you {b} · {b}! been a while · missed you around · {b} o/ long time
2. **b**: you too! · hey!! been busy · o/ {a}! · same, whats new
3. **a**:
   - *a:found_ore:* found {a.ore_count} {a.ore} the other day · been mining, got some {a.ore}
   - *a:built_lately:* finished my {a.built} · been building, come see the {a.built}
   - *a:died_lately:* i {a.death} lol · died again. {a.death}
   - *otherwise:* not much · same old · just surviving · farming mostly · nothing new
4. **b** (70%): nice · we should hang out more · lets go mining sometime · ill visit soon · o7 friend
- *Someone standing by may add:* o/ · hey guys

#### rivals needle

*Needs:* rivals  ·  *Effects:* rivalry

1. **a**: oh. its you · {b}. · what do you want · you again · look who it is
   - **PvP sweat:** {b}. still bad? · 1v1 me {b} · you still alive?
   - **bandit:** nice stuff you got, {b} · careful {b}
   - **hoarder:** stay away from my chests {b}
2. **b**: nothing from you · relax · whatever · ok · same to you · bye · funny
3. **a** (60%):
   - *a:armor_diamond, !b:armor_diamond:* nice armor. oh wait · {a.armor} beats whatever that is
   - *otherwise:* thought so · stay over there · L · ok · dont touch my stuff · go away
4. **b** (50%): whatever · cringe · ok buddy · not worth it · ...
- *Someone standing by may add:* ooooh · fight fight

#### strangers meet

*Needs:* strangers  ·  *Effects:* trust

1. **a**: hey, havent seen you around · o/ hi, im {a} · hey, new around here? · hi stranger · hey there
   - **hermit:** ...who are you · hm. new face
   - **newbie:** hi!! im {a}! · are you new too?
   - **bandit:** well hello there · nice gear, stranger
2. **b**: hey, im {b} · o/ {b} · hi, {b} · just passing through · hey hey · {b}. nice to meet you
   - **hermit:** {b} · just leave me be
   - **newbie:** hi im {b}!! nice to meet you
3. **a** (80%):
   - *a:has_house:* i live just over there *(answered: cool / ill drop by / nice spot)* · my base is nearby *(answered: nice / neighbours then / cool)* · drop by sometime *(answered: will do / maybe / sure)*
   - *a:in_town:* im from {a.town} *(answered: nice town / heard of it / cool)* · welcome to {a.town} *(answered: ty / nice town / thanks!)*
   - *otherwise:* where's your base? *(answered: around / over the hill / far away / dont have one yet / not telling lol / by the river)* · where you from? *(answered: far away / up north / around / dont ask lol / everywhere tbh)* · staying long? *(answered: maybe / just passing through / depends / might settle here / we'll see)* · nice to meet you *(answered: you too / likewise / o7 / same)*

#### bored

*Needs:* a:mood_bored

1. **a**: im so bored · bored · nothing to do · chat is this real. bored · someone entertain me
2. **b**: go mining · build something · go explore · lets go fight mobs · same tbh · start a farm
3. **a** (60%): eh · maybe · good idea · too lazy · fine

#### plans

*Needs:* day  ·  *Effects:* trust

1. **a**: going mining later, wanna come? · thinking of exploring tomorrow · gonna build a farm, need help? · wanna go to the nether sometime? · lets go raid a dungeon
2. **b**:
   - *friends:* im down · bet · sure! · lets go · count me in
   - *rivals:* no thanks · pass · with you? no · nah
   - *otherwise:* maybe · depends · sure why not · busy tbh · let me know
3. **a** (50%): cool · ok · ill let you know · W · aight

#### complaints

*Needs:* any time

1. **a**: a creeper blew up my wall · enderman took a block from my house · skeletons are so annoying · my crops arent growing · lost my pickaxe in lava · a zombie broke my door · spiders on my roof again · phantoms should be illegal
2. **b**: same · ugh · rip · classic · use stone next time · light more torches · lol · the worst
3. **a** (50%): right? · every time · minecraft moment · never again

#### brag vs

*Needs:* !strangers

1. **a**: my base is better than yours · bet i have more dias · i could beat you in a 1v1 · my farm is bigger
   - **PvP sweat:** 1v1 me · im better than you and you know it
2. **b**:
   - *friends:* lol sure · keep dreaming · in your dreams · ok buddy · prove it
   - *otherwise:* cap · doubt it · no · ok · whatever
3. **a** (50%): watch me · facts · you'll see · lol

#### joke road

*Needs:* !rivals

1. **a**: why did the creeper cross the road · why did the creeper cross the road lol
2. **b**: why · what · tell me · ...what · oh no · idk what
3. **a**: to get to the other ssssside · to get to the other sssside
4. **b**: lmao · ... · terrible · im leaving · that was awful · lol · -1000 aura · boo · ok that was good · why are you like this
- *Someone standing by may add:* lmao · old one · bruh

#### joke food

*Needs:* !rivals

1. **a**: whats a creepers favorite food · guess a creepers favorite food
2. **b**: why · what · tell me · ...what · oh no · idk what
3. **a**: ssssspaghetti · sssspaghetti
4. **b**: lmao · ... · terrible · im leaving · that was awful · lol · -1000 aura · boo · ok that was good · why are you like this
- *Someone standing by may add:* lol · boo

#### joke worse

*Needs:* !rivals

1. **a**: you know whats worse than a creeper · whats worse than one creeper
2. **b**: why · what · tell me · ...what · oh no · idk what
3. **a**: two creepers · a charged one
4. **b**: lmao · ... · terrible · im leaving · that was awful · lol · -1000 aura · boo · ok that was good · why are you like this
- *Someone standing by may add:* lmao true

#### joke skeleton

*Needs:* !rivals

1. **a**: why do skeletons never fight each other · why dont skeletons fight
2. **b**: why · what · tell me · ...what · oh no · idk what
3. **a**: they dont have the guts · no guts lol
4. **b**: lmao · ... · terrible · im leaving · that was awful · lol · -1000 aura · boo · ok that was good · why are you like this
- *Someone standing by may add:* ba dum tss · lol

#### joke enderman

*Needs:* !rivals

1. **a**: why was the enderman upset · whats an endermans favorite game
2. **b**: why · what · tell me · ...what · oh no · idk what
3. **a**: someone looked at it wrong · hide and seek, but with your blocks
4. **b**: lmao · ... · terrible · im leaving · that was awful · lol · -1000 aura · boo · ok that was good · why are you like this
- *Someone standing by may add:* lmao

#### joke jockey

*Needs:* !rivals

1. **a**: what do you call a chicken with a baby zombie on it · what rides a chicken
2. **b**: why · what · tell me · ...what · oh no · idk what
3. **a**: CHICKEN JOCKEY · chicken jockey.
4. **b**: lmao · ... · terrible · im leaving · that was awful · lol · -1000 aura · boo · ok that was good · why are you like this
- *Someone standing by may add:* lol

#### joke ghast

*Needs:* !rivals

1. **a**: why do ghasts cry so much · whats a ghasts favorite music
2. **b**: why · what · tell me · ...what · oh no · idk what
3. **a**: they had a rough nether · soul music
4. **b**: lmao · ... · terrible · im leaving · that was awful · lol · -1000 aura · boo · ok that was good · why are you like this
- *Someone standing by may add:* oof · lol

#### running joke

*Needs:* a:has_joke, !strangers

1. **a**: remember when {a.joke_who} {a.joke_what} lol · {a.joke_who} {a.joke_what} lmao · still laughing about {a.joke_who} · {a.joke_nick} lmao
   - **helper:** poor {a.joke_who}, they {a.joke_what}
   - **hermit:** {a.joke_who} {a.joke_what}. idiot
2. **b**:
   - *b:shares_joke:* LMAO · never forget · i was there, it was tragic · {a.joke_nick} lol · legendary
   - *otherwise:* wait what · no way · how?? · they WHAT · lmao really?
3. **a** (50%): classic {a.joke_who} · we're never letting that go · {a.joke_nick} forever
- *Someone standing by may add:* LMAO · never forget · lol

#### campaign

*Needs:* a:candidate, !b:candidate, !rivals

1. **a**: vote for me tomorrow! · {a} for leader, you know it · i'd make a better leader than {a.rival} · got my vote? i'm standing tomorrow · you'll vote for me right?
   - **helper:** vote for me and i'll help everyone, promise!
   - **bandit:** vote for me. it'd be wise · you'll vote right, won't you
   - **PvP sweat:** vote for me, i'll keep this town safe
   - **hoarder:** vote for me, lower taxes. probably
2. **b**:
   - *!b:likes_leader:* you've got my vote · sure, anything's better than now · maybe. what's in it for me? · we'll see
   - *otherwise:* {b.leader}'s been fine tho · hmm, i like {b.leader} · i'll think about it · no promises
3. **a** (60%): think about it · i'll remember who voted for me · ty! · just saying

#### election chat

*Needs:* a:election_soon, b:election_soon, !a:candidate, !b:candidate

1. **a**: who you voting for? *(answered: {b.my_vote} / {b.my_vote} probably / not telling / {b.my_vote}, obviously)* · {a.candidate_a} or {a.candidate_b}? *(answered: {b.my_vote} / tough one / {b.my_vote} i guess / dunno yet)*
2. **a** (60%): i'm going with {a.my_vote} · {a.my_vote} for me · same · fair enough

#### leader praise

*Needs:* a:likes_leader, !strangers

1. **a**: {a.leader} is doing a good job · gotta say, {a.leader} runs this place well · {a.town}'s in good hands with {a.leader}
2. **b**:
   - *b:dislikes_leader:* if you say so · eh · you would say that
   - *otherwise:* yeah · agreed · for sure

#### leader gripe

*Needs:* a:dislikes_leader, !strangers

1. **a**: {a.leader} is useless · can't wait for the next election · who put {a.leader} in charge anyway · {a.leader} needs to go
2. **b**:
   - *b:likes_leader:* hey, {b.leader}'s alright · that's harsh · nah they're fine
   - *otherwise:* true · fr · shh, they'll hear you · lol same

#### taxes

*Needs:* a:taxed, !a:is_leader

1. **a**:
   - *a:tax_high:* {a.tax} emeralds tax, really? · these taxes are robbery · {a.leader} and these taxes smh
   - *otherwise:* tax day again · paid my tax · where do the taxes even go · another emerald for the till
2. **b**: it pays the wages i guess · ugh tell me about it · at least the roads are nice · same

#### new leader talk

*Needs:* a:new_leader

1. **a**: {a.leader} won! · so {a.leader}'s in charge now · new leader, new rules · did you hear? {a.leader} won the vote
2. **b**:
   - *b:likes_leader:* finally · good, they deserve it · W
   - *b:dislikes_leader:* ugh · this is gonna be rough · great...
   - *otherwise:* we'll see how it goes · huh · ok

#### leader rounds

*Needs:* a:is_leader, !strangers

1. **a**: how's everything going? · need anything for your house? · the town's doing well, right? · any complaints? (don't say taxes)
2. **b**:
   - *b:dislikes_leader:* could be better · since you ask... the taxes · fine
   - *otherwise:* all good · doing great · no complaints

## 7. Drifters talking to you

A Drifter near you (8 blocks, seeing you) with nothing pressing now and then brings something up with you (30 topics in `drifter_talk/players.json`; **a** is the Drifter, **b** you): a stranger gets a hi, a friend their name and its news, your gear, how hurt you look, the night coming, what it found, a running joke about you, and questions you answer in chat ("wanna team up?": a yes however you say it, and it does it). One Drifter at a time with you, every minute and a half at most; each Drifter every 3 to 15 minutes (the chatty sooner). `/drifter talk me [topic]` makes the nearest one bring something up with you.

**Running jokes.** Drifters who see someone (you, or a Drifter) die a silly death (lava, a fall, drowning, a cactus...) have a joke for a week: they bring it up with each other ("remember when Tester fell off a cliff lol", and one who was there: "never forget"), and with you ("hey its the cliff diver lol"). The words are `joke.what.*` and `joke.nick.*` below.

**Rounds.** The first time you're about in the morning near two or more Drifters who know you, a few say gm, one after another (`round.gm`); going to bed near them, gn (`round.gn`).

**Standing by.** When a Drifter finishes a talk or answers you, another nearby may add its bit (the *chime* lines of a topic, and `chime.<what you asked>` below).

#### heard plan

*Needs:* b:plan_due, !b:plan_home

1. **a**:
   - *b:plan_mining:* how was mining? find dias? · back from mining? find anything good? · any luck mining? · find any diamonds?
   - *b:plan_mining:* **miner:** how deep did you go? find dias?
   - *b:plan_caving:* how was the cave? find anything? · back from caving? find anything good? · survived the caves? find anything?
   - *b:plan_nether:* how was the nether? find a fortress? · you survived the nether lol. get anything good? · back from the nether? find anything?
   - *b:plan_nether:* **newbie:** you came back from the nether!! was it scary? find anything?
   - *b:plan_end:* did you kill the dragon?? · how was the end? beat the dragon?
   - *b:plan_exploring:* how was exploring? find anything cool? · back from your adventure? find anything? · see anything cool out there?
   - *b:plan_exploring:* **nomad:** find anywhere new out there?
   - *b:plan_building:* hows the build going? done yet? · finish the build?
   - *b:plan_building:* **builder:** finished the build? i wanna see
   - *b:plan_fishing:* catch anything good? · get a mending book? lol
   - *b:plan_farming:* hows the farm? good harvest? · get a good harvest?
   - *b:plan_hunting:* good hunt? get food? · how was the hunt? get enough food?
   - *b:plan_trading:* get good trades? · the villagers give you mending?
   - *b:plan_stronghold:* find the stronghold? · any luck with the stronghold?
   - *otherwise:* how was {b.plan}? go well? · how'd {b.plan} go? good? · {b.plan} go ok?
- *You say yes* (it just answers): nice! · W · lets go · ayy nice · gg · **newbie:** yay!! · **hoarder:** share? · **PvP sweat:** nice · **helper:** yay, so happy for you!
- *You say no:* unlucky · rip · next time · f · aw, next time · **helper:** aw, you'll get it next time! · **PvP sweat:** skill issue lol

#### heard promise

*Needs:* b:promise_due

1. **a**: still waiting on that {b.promise} lol · you said you'd bring {b.promise} · hey, where's my {b.promise}? · didnt you say you'd bring {b.promise}? · {b.promise}? still coming?
   - **hoarder:** {b.promise}. you promised · i havent forgotten about the {b.promise}
   - **bandit:** you owe me {b.promise}. dont forget
   - **helper:** no rush on the {b.promise} btw!
   - **hermit:** {b.promise}. you said

#### heard promise broken

*Needs:* b:promise_broken

1. **a**: guess you forgot about the {b.promise} · so much for the {b.promise} huh · {b.promise} never came lol · you never brought the {b.promise} · still no {b.promise}. noted
   - **hoarder:** you never paid. i remember · no {b.promise}. i wont forget
   - **bandit:** you broke your word. bad idea
   - **helper:** its ok about the {b.promise}, really
   - **newbie:** you forgot the {b.promise} :(

#### heard news

*Needs:* b:told_news

1. **a**:
   - *b:news_died:* you get your stuff back? · did you get your items back? · find your stuff after you died?
   - *b:news_died:* **helper:** did you get your stuff back ok?
   - *b:news_diamonds:* find any more dias? · make anything with the diamonds? · more diamonds since?
   - *b:news_diamonds:* **hoarder:** still have those diamonds?
   - *b:news_hungry:* you eat yet? · still hungry? · find food?
   - *b:news_built:* still building? · adding more to the build?
   - *b:news_built:* **builder:** can i see your build sometime?
   - *b:news_nether:* going back to the nether? · gonna go back to the nether?
   - *b:news_dragon:* still cant believe you beat the dragon. gonna do it again? · dragon slayer! do it again?
   - *b:news_lost:* you find your way back ok? · not lost anymore?
   - *b:news_tired:* get some sleep? · feeling better?
- *You say yes* (it just answers): nice · good · W · ayy · good to hear · **helper:** yay! good
- *You say no:* rip · aw · unlucky · f · next time · **helper:** aw, i can help if you want

#### heard like

*Needs:* b:told_like, !b:stranger

1. **a**: still into {b.like}? · you still like {b.like}? · thought of you, saw some {b.like} lol · {b.like} fan still?
   - **newbie:** i tried {b.like} like you said! its cool
   - **hermit:** still {b.like}?
- *You say yes* (it just answers): nice · same · respect · ofc lol
- *You say no:* oh lol, ok · changed your mind huh · fair

#### heard hate

*Needs:* b:told_hate, !b:stranger

1. **a**: still hate {b.hate}? · saw some {b.hate} and thought of you lol · {b.hate}, your favorite lol

#### heard base

*Needs:* b:told_base, b:friend

1. **a**: hows the base? · hows your place doing? · still living at {b.base}? · might come visit your base sometime
   - **builder:** hows the base? added anything?
   - **bandit:** hows the base? still unlocked? · nice base you got. lots of chests?
   - **homebody:** hows home? :)

#### heard place tell

*Needs:* a:heard_place, !b:stranger

1. **a**: btw {a.heard_from} said theres {a.heard_what} {a.heard_where} · {a.heard_from} told me about {a.heard_what} {a.heard_where} · heard theres {a.heard_what} {a.heard_where}, {a.heard_from} said · fyi {a.heard_what} {a.heard_where}, says {a.heard_from}
   - **hoarder:** dont tell anyone but {a.heard_from} found {a.heard_what} {a.heard_where}
   - **helper:** oh! {a.heard_from} said theres {a.heard_what} {a.heard_where}, if you need it

#### heard word tell

*Needs:* a:heard_bad, !b:stranger

1. **a**: careful, {a.word_from} says {a.heard_about} is trouble · {a.word_from} said to watch out for {a.heard_about} · heads up, {a.heard_about} might be sketchy, {a.word_from} said
   - **helper:** be careful around {a.heard_about}, {a.word_from} said they're trouble

#### heard player news

*Needs:* a:heard_news, !b:stranger

1. **a**:
   - *a:heard_news_diamonds:* {a.news_from} found diamonds btw · did you hear {a.news_from} found dias
   - *a:heard_news_died:* {a.news_from} died earlier lol · rip {a.news_from}, died today
   - *a:heard_news_dragon:* {a.news_from} beat the dragon!! · did you hear? {a.news_from} killed the dragon
   - *a:heard_news_built:* {a.news_from} built something, go look
   - *a:heard_news_nether:* {a.news_from} went to the nether and came back

#### hi stranger

*Needs:* b:stranger

1. **a**: hey, new here? · o/ havent seen you before · hi stranger · hey there · oh hi, didnt see you there · yo, who are you
   - **newbie:** hi!! are you new too? · hello!! im {a}!
   - **hermit:** ...hm. hi · you lost?
   - **bandit:** well well. nice gear · lost, traveller?
   - **helper:** hi! need anything? · welcome! o/
   - **PvP sweat:** you any good at pvp? · sup
2. **a** (50%): im {a} btw · {a}, nice to meet you · name's {a}

#### friend hi

*Needs:* b:friend

1. **a**: {b}! o/ · yo {b} · hey {b} · {b} o/ · oh hey {b} · ayy {b}
   - **newbie:** {b}!! hi!! · HI {b}
   - **hermit:** {b}. · oh. its you. hi
   - **helper:** {b}! good to see you!
2. **a**:
   - *a:found_rare:* found {a.ore_count} {a.ore} today btw · guess who found {a.ore}
   - *a:in_town:* {a.town}'s coming along · come see {a.town} sometime
   - *a:built_lately:* just finished my {a.built} · built a {a.built}, come see
   - *otherwise:* whatcha up to · wyd · been a while · whats new

#### nice gear

*Needs:* b:armor_good, !b:stranger

1. **a**: nice armor · ooh {b.armor} armor · where'd you get all that {b.armor} · drip · rich much?
   - **bandit:** nice armor. be a shame if something happened to it
   - **PvP sweat:** nice gear. still wouldnt save you · {b.armor}? 1v1 me
- *Someone standing by may add:* fr · jealous · where'd you get that

#### nice weapon

*Needs:* b:holding_fancy

1. **a**: nice {b.held} · ooh a {b.held} · is that a {b.held}?? · where'd you get the {b.held} · {b.held} :o

#### you hurt

*Needs:* b:hurt, !b:stranger

1. **a**: you ok? you look rough · youre low, you good? · yikes, need food? · you're like on 2 hearts lol
   - **helper:** you're hurt! want some food? · oh no, you ok? need food?
   - **hermit:** you're bleeding. fix that
- *You say yes* (give_food): here · catch · here, eat something · take this
- *You say no:* ok, be careful · suit yourself · ok, stay safe

#### night warning

*Needs:* night, outside, !b:stranger

1. **a**: careful, mobs are out · its dark, watch out · you got a bed? · stay safe out here · dont get blown up lol

#### evening warning

*Needs:* evening, outside

1. **a**: sun's going down · night soon · getting dark, better find shelter · almost night, careful

#### team offer

*Needs:* b:friend, day

1. **a**: wanna team up for a bit? · wanna duo? · wanna come with me for a while? · want some company?
   - **newbie:** can i come with you?? please · wanna be teammates?
   - **PvP sweat:** need a bodyguard?
   - **helper:** want a hand with anything? i can come along
- *You say yes* (team): lets go! · W, lead the way · duo time · ok, right behind you
- *You say no:* ok np · maybe later then · all good · ok, cya

#### mining invite

*Needs:* b:friend, day, !a:no_home

1. **a**: going mining soon, wanna come? · wanna go caving? · need iron, wanna come mining?
- *You say yes* (follow): cool, lead the way and i'll follow · nice, you go first · ok, lets go
- *You say no:* ok, more ores for me · your loss lol · ok np

#### brag ore

*Needs:* a:found_rare, !b:stranger

1. **a**: {b} look, i found {a.ore_count} {a.ore}!! · guess who found {a.ore} · {a.ore_count} {a.ore} today, not bad right?
   - **hoarder:** found {a.ore} today. not telling where · {a.ore}. mine.
   - **newbie:** {b}!! i found {a.ore}!! is that good??
- *Someone standing by may add:* no way · where?? · share pls · lucky

#### died story

*Needs:* a:died_lately, !b:stranger

1. **a**: i died to {a.death} earlier lol · {a.death} got me today · rip me, {a.death} · dont ask how i died. ({a.death})
- *Someone standing by may add:* classic · rip · f

#### news

*Needs:* b:has_news, !b:stranger

1. **a**: heads up, {b.news_who} {b.news_what} · did you hear? {b.news_who} {b.news_what} · word is {b.news_who} {b.news_what}

#### tease

*Needs:* b:joked_about

1. **a**: hey its {b.joke_nick} lol · remember when you {b.joke_what} lmao · {b.joke_nick} is back · still thinking about when you {b.joke_what}
   - **helper:** hope you're ok after you {b.joke_what} lol
   - **PvP sweat:** {b.joke_nick} lmao · skill issue ({b.joke_what})
- *Someone standing by may add:* LMAO · never forget · legendary · lol

#### still mad

*Needs:* b:insulted

1. **a**: oh its you. the one who called me {b.insult} · still mad you called me {b.insult} · {b.insult}? really?
   - **helper:** i forgive you for calling me {b.insult} btw
   - **hermit:** you again.

#### thanks again

*Needs:* b:owed

1. **a**: thanks again for earlier · i still owe you one · ty for the stuff btw · hey, thanks for the help earlier

#### hungry ask

*Needs:* a:hungry, b:friend

1. **a**: got any food? starving · spare some food? · im so hungry, got anything?
- *You say yes* (it just answers): ty!! throw it over · W, toss it · thank you!!
- *You say no:* rip ok · its fine · ok, ill find something

#### rain chat

*Needs:* rain, outside

1. **a**: this rain tho · ugh rain · great weather huh · rain again

#### bored ask

*Needs:* a:mood_bored, !b:stranger

1. **a**: im so bored, wanna do something? · bored. entertain me · {b} im bored, wanna go somewhere?
- *You say yes* (follow): lets go, lead the way · finally, ok where to · W, i'll follow
- *You say no:* ugh ok · fine · boring

#### leader welcome

*Needs:* a:is_leader, b:stranger

1. **a**: welcome to {a.town}, i'm the leader here · hey, new face. i run {a.town} · welcome! i'm {a}, i lead {a.town}
   - **bandit:** this is my town. behave · {a.town}'s mine. no trouble, got it?
2. **a** (60%): no stealing, no killing, and we get along · keep your hands off our chests and we're good · enjoy your stay

#### campaign player

*Needs:* a:candidate, !b:stranger

1. **a**: i'm standing for leader tomorrow! · vote for me! well, you can't, but tell everyone · {a} for leader. spread the word · election's tomorrow, wish me luck

#### `chime.bot`

A round, or a bystander's bit.

- **everyone:** we're all bots lol · beep boop · aren't we all · sus

#### `chime.compliment`

A round, or a bystander's bit.

- **everyone:** true · fr · facts · real · W

#### `chime.duel`

A round, or a bystander's bit.

- **everyone:** ooh fight · get him {name} · fight fight fight · my money's on {name} · popcorn time · this'll be good

#### `chime.give`

A round, or a bystander's bit.

- **everyone:** me too pls · can i have some too? · generous · share with me next

#### `chime.help`

A round, or a bystander's bit.

- **everyone:** coming too! · hold on! · omw too

#### `chime.insult`

A round, or a bystander's bit.

- **everyone:** oof · ratio · rude · dang · ooooh · chill
- **helper:** be nice
- **PvP sweat:** lmao · ratio

#### `chime.joke`

A round, or a bystander's bit.

- **everyone:** lmao · old one · bruh · lol · boo · lmaooo · i'm stealing that · terrible. 10/10

#### `chime.team`

A round, or a bystander's bit.

- **everyone:** can i come too? · trio? · dont leave me out · W

#### `round.gm`

A round, or a bystander's bit.

- **everyone:** gm · gm {player} · morning · mornin · gm o/ · good morning · gm gm · morning {player} · o/ gm
- **newbie:** good morning!! · gm {player}!!
- **hermit:** mm. morning · gm.
- **homebody:** morning! sleep well? · gm {player} :)
- **builder:** gm, big build day today · morning o/
- **PvP sweat:** gm · morning. 1v1 later?
- **helper:** good morning {player}! · gm! need anything today?

#### `round.gn`

A round, or a bystander's bit.

- **everyone:** gn · gn {player} · night · sleep well · gn o/ · nn · gnight
- **newbie:** gn {player}!! · good night!!
- **hermit:** gn. · finally, quiet
- **homebody:** sleep tight {player} · gn :)
- **helper:** sleep well {player}! · gn, i'll keep watch

- `joke.nick.cactus`: cactus hugger · the cactus guy
- `joke.nick.cramming`: squish · pancake
- `joke.nick.drown`: fish · the swimmer · the drowned one
- `joke.nick.fall`: the cliff diver · skydiver · gravity's victim
- `joke.nick.falling_anvil`: anvil head · flat
- `joke.nick.falling_block`: sand castle · gravel guy
- `joke.nick.falling_stalactite`: bonk victim
- `joke.nick.fly_into_wall`: top gun · wall pilot
- `joke.nick.freeze`: popsicle · frosty
- `joke.nick.hot_floor`: magma dancer · hot feet
- `joke.nick.in_fire`: campfire guy · crispy
- `joke.nick.in_wall`: wall guy · the wall enjoyer
- `joke.nick.lava`: the lava swimmer · lava guy · hot tub enjoyer
- `joke.nick.on_fire`: crispy · the torch
- `joke.nick.stalagmite`: kebab · the skewer
- `joke.nick.starve`: the hungry one · snack forgetter
- `joke.nick.sweet_berry_bush`: the berry bush victim · bush loser
- `joke.what.cactus`: hugged a cactus · lost to a cactus
- `joke.what.cramming`: got squished · got crammed
- `joke.what.drown`: forgot to breathe · drowned · tried to breathe water
- `joke.what.fall`: fell off a cliff · forgot gravity exists · jumped off a mountain
- `joke.what.falling_anvil`: got anvilled · got hit by an anvil
- `joke.what.falling_block`: got crushed by sand · got buried by gravel
- `joke.what.falling_stalactite`: got bonked by a stalactite
- `joke.what.fly_into_wall`: flew into a wall · elytra'd into a wall
- `joke.what.freeze`: froze in powder snow · turned into a popsicle
- `joke.what.hot_floor`: stood on magma · danced on magma
- `joke.what.in_fire`: walked into fire · sat in a campfire
- `joke.what.in_wall`: suffocated in a wall · got stuck in a wall
- `joke.what.lava`: swam in lava · took a lava bath · fell in lava
- `joke.what.on_fire`: burned to a crisp · caught fire
- `joke.what.stalagmite`: fell on a stalagmite · got skewered by a dripstone
- `joke.what.starve`: starved · forgot to eat
- `joke.what.sweet_berry_bush`: got killed by a berry bush · lost to a bush

## 8. Asking a Drifter something

Say it to a Drifter: with its name (or a good part of it) in the message, or to the one you've been talking with this last minute, or the one you're looking at (16 blocks), or the only one within 10 blocks. It turns to look at you and answers a moment later, as it's typed. What it tells you and what it does depends on how it feels about you (`/drifter social show <you>`): trust 25 is a friend. To someone it's hostile to it only says to get lost. Only what it knows: its memory, its pockets, its home. The phrases are in section 10 (`requests.json`).

| You say (intent) | What it does | For whom |
| --- | --- | --- |
| wyd | What it's doing (its job right now) | anyone it isn't hostile to |
| hru | How it is: hurt, hungry, or its mood | anyone |
| name | Its name | anyone |
| coords | Where it is | its coords to a friend (trust 25+), "around" to someone it knows (10+), else no |
| base | Where it lives | coords to a friend (a helper: to anyone it knows), a direction and distance to someone it knows, else no |
| seen_village, seen_diamonds, seen_iron, seen_dungeon, seen_portal, seen_fortress | Somewhere it has seen one (its memory only) | as for its base; a greedy one keeps ore to itself |
| follow | It comes along (once you walk off; as crouching twice does) | a friend, or someone it knows (10+) and has spent time near |
| team | It comes along for 15 minutes (and helps against mobs, as friends do) | a friend |
| come | It walks over to you | as follow |
| stop | It stops following | anyone |
| help | It drops what it's doing and runs to you, then sticks around a minute | a friend; a helper for anyone it doesn't dislike; a brave one it knows |
| give | "can i have 3 bread": it brings it over and tosses it, from what it can spare, about what you're worth to it; no item named, it asks what | a friend, or anyone it owes for a gift |
| trade | Points you to its shop, or asks what you need (then name the item) | anyone |
| duel | A friendly 1v1: it fights you, nobody goes below a heart; whoever would have lost it says gg | a PvP sweat, a bandit or a brave, aggressive one, within 20 blocks, not on Peaceful; most others say no |
| compliment | It thanks you, and likes you a little more (3 a day count) | anyone |
| insult | It answers back and likes you less (5 a day count); for a day it brings it up when you ask for something | anyone |
| joke | A joke | anyone |
| bot | "are you a bot" | anyone |

#### `ask.wyd`

Answering **wyd**.

- **everyone:** {doing} · just {doing} · {doing} rn · {doing}, wbu · nm, {doing} · {doing} lol · {doing}. you? · oh just {doing} · busy {doing}
- **casual:** {doing} hbu · chillin, {doing} · eh {doing}
- **newbie:** {doing}!! i think im doing it right · trying to {doing} lol · {doing}! wbu!
- **builder:** {doing}, gotta finish the build · {doing}. planning a build after
- **miner:** {doing}, then back to the mines · {doing}. mining after
- **homebody:** {doing}, then home · {doing} near home
- **speedrunner:** {doing}, cant stop · {doing}. on a timer
- **nomad:** {doing}, then moving on · {doing} before i head out
- **hoarder:** {doing}. why · {doing}, not sharing
- **hermit:** {doing}. · busy. · ...
- **PvP sweat:** {doing}. 1v1 after? · {doing}, wanna fight after
- **bandit:** {doing}. why do you ask · mind your business
- **helper:** {doing}! need anything? · {doing}, but i can help if you want

#### `ask.wyd_idle`

Answering **wyd**.

- **everyone:** nm · nothing much · chilling · just vibing · nothing rn · idk, chilling · afk ish · not much, wbu · standing here lol
- **newbie:** idk what to do lol · nothing! any ideas?
- **hermit:** nothing. · being left alone. ideally
- **helper:** nothing, need help with something?

#### `ask.hru_good`

Answering **hru**.

- **everyone:** good · good hbu · pretty good · not bad · doing alright · good, you? · cant complain · chilling · alright · decent · great actually
- **newbie:** good!! this game is fun · great!! wbu
- **hermit:** fine. · fine
- **PvP sweat:** cracked as always · good. undefeated
- **helper:** good! you? · doing great, thanks for asking!

#### `ask.hru_triumphant`

Answering **hru**.

- **everyone:** great!! · amazing actually · on top of the world · never better

#### `ask.hru_hurt`

Answering **hru**.

- **everyone:** not great, low on hp · bad, need to heal · barely alive lol · on like 3 hearts · hurt ngl

#### `ask.hru_hungry`

Answering **hru**.

- **everyone:** hungry · starving ngl · need food · could eat · hungry lol, got food?

#### `ask.hru_bored`

Answering **hru**.

- **everyone:** bored · so bored · bored lol · kinda bored ngl · bored out of my mind

#### `ask.hru_scared`

Answering **hru**.

- **everyone:** kinda scared ngl · nervous · spooked · not great, something's out there

#### `ask.hru_angry`

Answering **hru**.

- **everyone:** annoyed · dont ask · mad rn · could be better

#### `ask.hru_sad`

Answering **hru**.

- **everyone:** not great · sad ngl · rough day · lost a friend

#### `ask.name`

Answering **name**.

- **everyone:** {name} · im {name} · {name}, you? · its {name} · {name}. and you? · name's {name} · {name} o/
- **newbie:** im {name}!! nice to meet you · {name}! whats yours?
- **hermit:** {name}. · why
- **bandit:** {name}. remember it · who's asking
- **helper:** {name}! nice to meet you {player}

#### `ask.coords_yes`

Answering **coords**.

- **everyone:** {coords} · im at {coords} · {coords} why · {coords}, come by · around {coords} · {coords} rn

#### `ask.coords_vague`

Answering **coords**.

- **everyone:** around · near you kinda · not far · close by · around here · you can see me lol

#### `ask.coords_no`

Answering **coords**.

- **everyone:** why do you want to know · nice try · not telling · why · nah · dont think so
- **bandit:** you first · nice try lol
- **hermit:** no. · nowhere

#### `ask.base_yes`

Answering **base**.

- **everyone:** {coords} · my base is at {coords} · {coords}, come by sometime · at {coords} · {coords}. dont grief it lol · {coords}, door's open
- **homebody:** {coords}! come visit :)
- **hoarder:** {coords}. and dont touch my chests

#### `ask.base_way`

Answering **base**.

- **everyone:** {way} of here, about {dist} blocks · that way, {way}, like {dist} blocks · {dist} blocks {way}ish · out {way}, not far

#### `ask.base_none`

Answering **base**.

- **everyone:** dont have one yet · homeless rn lol · still looking for a spot · no base yet · i live out here lol

#### `ask.base_no`

Answering **base**.

- **everyone:** not telling a stranger · nice try lol · why · maybe when i know you · nah · secret
- **bandit:** why, wanna rob it? · nice try
- **hoarder:** absolutely not · so you can raid my chests? no

#### `ask.seen_yes`

Answering **seen_village, seen_diamonds, seen_iron, seen_dungeon, seen_portal, seen_fortress**.

- **everyone:** yeah, {what} at {coords} · one at {coords} · {coords} · saw {what} at {coords} · yeah, around {coords}

#### `ask.seen_way`

Answering **seen_village, seen_diamonds, seen_iron, seen_dungeon, seen_portal, seen_fortress**.

- **everyone:** yeah, {way} of here, like {dist} blocks · {way}, about {dist} blocks · saw {what} out {way} · yeah, {dist} blocks {way}ish

#### `ask.seen_none`

Answering **seen_village, seen_diamonds, seen_iron, seen_dungeon, seen_portal, seen_fortress**.

- **everyone:** havent seen any · nope · no idea · not around here · nah, havent seen {what} · wish i knew lol

#### `ask.seen_no`

Answering **seen_village, seen_diamonds, seen_iron, seen_dungeon, seen_portal, seen_fortress**.

- **everyone:** maybe · find your own lol · not telling · maybe i have, maybe i havent · why would i tell you
- **hoarder:** mine. find your own
- **miner:** a miner never tells

#### `ask.follow_yes`

Answering **follow**.

- **everyone:** omw · k · lead the way · sure · ok · coming · right behind you · aight · sure, where to
- **newbie:** yay ok!! · ok!! where are we going
- **nomad:** always down for a trip · lead on
- **helper:** of course! lead the way

#### `ask.follow_no`

Answering **follow**.

- **everyone:** nah · im busy · i dont even know you · why would i · no thanks · maybe later · not rn
- **hermit:** no. · leave me alone
- **bandit:** you first · not falling for that
- **homebody:** i'd rather stay near home

#### `ask.team_yes`

Answering **team**.

- **everyone:** sure, lets go · duo time · W, lets do it · sure! · ok, lead the way · teammates it is · lets gooo
- **newbie:** really?? yes!! · yes!! i'll follow you
- **PvP sweat:** fine, dont slow me down · ok but i get the kills
- **helper:** of course!! lets go

#### `ask.team_no`

Answering **team**.

- **everyone:** maybe later · i play solo · dont know you well enough · nah · maybe some other time · not rn
- **hermit:** i work alone · no.
- **speedrunner:** youd slow me down · solo run, sorry

#### `ask.come_yes`

Answering **come**.

- **everyone:** coming · omw · 1 sec · on my way · ok · k · coming coming

#### `ask.follow_no`

Answering **come**.

- **everyone:** nah · im busy · i dont even know you · why would i · no thanks · maybe later · not rn
- **hermit:** no. · leave me alone
- **bandit:** you first · not falling for that
- **homebody:** i'd rather stay near home

#### `ask.stop`

Answering **stop**.

- **everyone:** ok · fine · k · aight · ok ill stay · np · cya then · ok o/
- **newbie:** ok! bye!!
- **hermit:** finally

#### `ask.help_yes`

Answering **help**.

- **everyone:** coming! · omw!! · hold on! · coming, hang in there · on my way! · im coming
- **helper:** coming!! hold on! · dont die, im coming
- **PvP sweat:** move, ill handle it · omw, watch this

#### `ask.help_no`

Answering **help**.

- **everyone:** good luck · lol no · youre on your own · cant rn · sorry, cant
- **hermit:** not my problem
- **bandit:** lol · what's in it for me

#### `ask.give_yes`

Answering **give**.

- **everyone:** here · sure, take {count} · catch · here you go · here, {count} {item} · take these · sure · np, here
- **helper:** of course! here · here, take {count}!
- **hoarder:** fine. just {count} · ugh fine, here
- **newbie:** here!! hope it helps

#### `ask.give_back`

Answering **give**.

- **everyone:** you helped me out, here · here, i owe you one · for earlier, take it

#### `ask.give_need`

Answering **give**.

- **everyone:** i need mine · need those sorry · only got enough for me · cant spare any · need them myself

#### `ask.give_none`

Answering **give**.

- **everyone:** dont have any · none, sorry · fresh out · i dont have {item} · nope, none

#### `ask.give_no`

Answering **give**.

- **everyone:** no · get your own · why would i give you that · nah · earn it · lol no
- **hoarder:** absolutely not · mine
- **bandit:** what's it worth to you

#### `ask.give_much`

Answering **give**.

- **everyone:** thats a lot to ask · i can't give that much away · too much, sorry · maybe a few

#### `ask.give_what`

Answering **give**.

- **everyone:** what do you need? · what do you want? · like what · need what? · what are you looking for

#### `ask.trade`

Answering **trade**.

- **everyone:** what you got? · what do you need? · maybe, what for · i might have stuff · depends what you want · sure, whatcha need

#### `ask.shop`

Answering **trade**.

- **everyone:** check my shop · right-click me · see what i got, right-click · come look at my stuff

#### `ask.duel_yes`

Answering **duel**.

- **everyone:** bet · youre on · lets go · ok, to 3 hearts · sure, first to 3 hearts · alright, square up · fine, hit me when ready
- **PvP sweat:** bet. you're done · finally, someone with guts · 1v1 me then, go
- **bandit:** you'll regret that · bet
- **newbie:** ok but go easy!! · uh ok!

#### `ask.duel_far`

Answering **duel**.

- **everyone:** come here then · where are you lol · come find me first · get over here then · bet, come here
- **PvP sweat:** come here and say that · pull up then

#### `ask.duel_no`

Answering **duel**.

- **everyone:** nah im good · not now · no thanks · i'll pass · maybe later
- **helper:** i dont fight friends · lets not
- **hermit:** no. · go away
- **homebody:** not into fighting

#### `ask.duel_scared`

Answering **duel**.

- **everyone:** no way lol · with that gear? no · i'd lose lol · not a chance

#### `ask.duel_won`

Answering **duel**.

- **everyone:** gg · gg ez · too easy · gg, close one · get good lol · gg wp
- **helper:** gg! you almost had me
- **newbie:** i won?? gg!!

#### `ask.duel_lost`

Answering **duel**.

- **everyone:** gg you win · ok ok you win · gg wp · gg, rematch later · fine, you win · gg, you're good
- **PvP sweat:** lag · gg, i wasnt trying · rematch. now
- **bandit:** lucky

#### `ask.compliment`

Answering **compliment**.

- **everyone:** ty · thx :) · aw ty · thanks! · ty ty · thank you · you too · appreciate it · :)
- **newbie:** thank you!! · omg ty!!
- **hermit:** hm. thanks · ok
- **PvP sweat:** i know · obviously
- **builder:** ty! took ages · thanks, still working on it
- **bandit:** flattery wont work

#### `ask.insult`

Answering **insult**.

- **everyone:** ok · rude · cope · whatever · wow · k · and? · ok buddy · cool story
- **PvP sweat:** 1v1 then · says you · L yourself · cope
- **newbie:** hey :( · that's mean
- **hermit:** ... · go away
- **helper:** that's not nice · hope your day gets better
- **bandit:** watch it · say that again

#### `ask.insult_again`

Answering **insult**.

- **everyone:** you again with that · keep talking · wow, again · you done? · ok we get it · so original

#### `ask.refuse_insulted`

Answering **insult**.

- **everyone:** not after you called me {insult} · maybe dont call me {insult} next time · you called me {insult}. no · ask nicely next time · after calling me {insult}? lol no

#### `ask.joke`

Answering **joke**.

- **everyone:** why did the creeper cross the road? to get to the other ssssside · whats a creepers favorite food? ssssalad · why dont skeletons fight each other? no guts · what do you call a chicken with a baby zombie on it? lunch with extra steps · why did the enderman stare? he was looking for his block · knock knock. creeper. creeper wh- *boom* · my sword's like my jokes. dull · i'd tell you a redstone joke but you wouldnt get the signal · why do ghasts cry? fire in their eyes
- **hermit:** no.
- **newbie:** umm... creepers are green? lol

#### `ask.bot`

Answering **bot**.

- **everyone:** no lol · beep boop · im as real as you · what? no · are YOU a bot · lol no · no im just bad
- **newbie:** no!! im real!!
- **hermit:** does it matter
- **PvP sweat:** bots dont play this good

## 8b. Telling a Drifter things (it remembers)

Said to a Drifter as in section 8 (statements, not questions). What it was told is kept with it (saved) and shown by `/drifter heard`; `/drifter heard age <minutes>` makes it older, to see the follow-ups at once. Phrases in `drifter_chat/heard.json` (section 10), the topics it brings up later in `drifter_talk/heard.json` (sections 6 and 7, ids starting `heard_`).

| You say | What it does |
| --- | --- |
| call me X / my name is X | It calls you that from now on (in its answers and talks with you) |
| my base is at x y z / i live here / my house is up north | It remembers where you live (coords, here, or a direction and distance) |
| theres a village 300 blocks north / found dias at x y z | It remembers the place, who said so, and passes it on to other Drifters; every Drifter within 32 blocks reads it in chat. "here" or "over there" needs a word like "theres" or "found" |
| going mining / heading to the nether / going home | It wishes you luck, and asks how it went a few minutes later |
| ill bring you 5 iron / i owe you | It holds you to it: reminds you after a day; three days and nothing costs some trust; bring it and it thanks you for keeping your word (and likes you more) |
| i love cats / i hate creepers | It remembers (things players talk about, items, creatures) and brings it up |
| bob is a griefer / annie is cool | If it trusts you (10+) and isn't sure of them itself, it thinks a little less (or more) of them, once a day from each teller, and passes the word on |
| i died / found diamonds / im hungry / im back / im lost | It reacts; a hungry friend gets offered food (say yes); it asks after it later |

#### `told.nick`

You said: *call me X / my name is X*.

- **everyone:** ok {nick} it is · got it, {nick} · {nick}, noted · ok {nick} · sure thing {nick} · {nick}. got it · alr {nick} · will do, {nick}
- **newbie:** ok {nick}!! cool name · {nick}! i'll remember!!
- **hermit:** {nick}. fine · hm. {nick}
- **PvP sweat:** {nick}. ok · sure {nick}, whatever
- **bandit:** {nick}. i'll remember that · {nick}, huh
- **helper:** nice to meet you {nick}! · {nick}! got it :)
- **builder:** {nick}, nice o/
- **homebody:** {nick}! lovely name :)

#### `told.base`

You said: *my base is at x y z / i live here / my house is up north*.

- **everyone:** noted · {coords}, got it · cool, might visit · ok, i'll remember · nice, where's it at? oh {coords}, ok · ty, noted · got it
- **newbie:** can i come visit?? {coords}!! · ooh i wanna see it
- **builder:** ooh i'll come see the build · nice, i wanna see it sometime
- **bandit:** {coords}. interesting · good to know
- **hoarder:** nice. got chests there?
- **hermit:** ok · hm
- **helper:** noted! i'll come by if you need help

#### `told.base_here`

You said: *my base is at x y z / i live here / my house is up north*.

- **everyone:** oh nice, this is yours? · nice place · cool base · oh this is your base? nice · nice, noted · ooh nice spot
- **builder:** nice build! love the spot · oh this is yours? nice work
- **newbie:** woah this is yours?? so cool
- **bandit:** nice place. be a shame if... · nice. good to know
- **hoarder:** nice. how many chests?
- **hermit:** hm. nice
- **homebody:** so cozy! · lovely home :)

#### `told.base_way`

You said: *my base is at x y z / i live here / my house is up north*.

- **everyone:** {way}, ok · noted, {way} · {way}? cool · ok, somewhere {way} · got it · ok, {way} then
- **newbie:** {way}!! ok!!
- **bandit:** {way}, huh. noted
- **hermit:** hm

#### `told.place`

You said: *theres a village 300 blocks north / found dias at x y z*.

- **everyone:** ooh ty · noted, {what} {where} · nice, might check it out · ty for the tip · oh nice, {what}? · good to know · ty, ill remember · {what} {where}? nice
- **newbie:** ooh {what}!! ty!! · omg really? ty!!
- **miner:** ooh {what}? ty · nice, ty for the coords
- **nomad:** ooh, i'll go see · a new place, ty
- **hoarder:** {what}. ty. noted · dont tell anyone else
- **hermit:** hm. ok · noted
- **bandit:** useful. ty · heh, good to know
- **speedrunner:** ty, saves time
- **helper:** ty! i'll tell the others

#### `told.place_known`

You said: *theres a village 300 blocks north / found dias at x y z*.

- **everyone:** yeah i know that one · yep, seen it · oh i know, been there · yeah i saw that · i know lol
- **miner:** yep, i found it first lol
- **hermit:** i know

#### `told.place_heard`

You said: *theres a village 300 blocks north / found dias at x y z*.

- **everyone:** yeah someone said · i heard · yep, heard about that · oh yeah, someone told me · i heard lol

#### `told.plan`

You said: *going mining / heading to the nether / going home*.

- **everyone:** gl · have fun · gl with {plan_go} · have fun {plan_go} · good luck · ok, gl · be safe · gl, be careful · have fun! · nice, gl · o7 gl
- **newbie:** gl!! be careful!! · can i come??
- **helper:** good luck! shout if you need me · be safe! o/
- **hermit:** ok · bye
- **PvP sweat:** gl · dont die lol
- **homebody:** be careful out there! · come back safe

#### `told.plan.building`

You said: *going mining / heading to the nether / going home*.

- **everyone:** ooh show me when its done · gl with the build · have fun building
- **builder:** ooh what are you building? · show me after!

#### `told.plan.caving`

You said: *going mining / heading to the nether / going home*.

- **everyone:** bring torches · gl, watch the creepers · careful down there · bring a water bucket

#### `told.plan.chopping`

You said: *going mining / heading to the nether / going home*.

- **everyone:** replant the trees lol · gl, get some logs

#### `told.plan.end`

You said: *going mining / heading to the nether / going home*.

- **everyone:** gl with the dragon! · bring blocks and beds lol · watch the endermen · o7 gl · dont look at the endermen

#### `told.plan.exploring`

You said: *going mining / heading to the nether / going home*.

- **everyone:** have fun, find something cool · bring food · tell me what you find · gl, dont get lost
- **nomad:** nice, which way? · the best thing to do

#### `told.plan.farming`

You said: *going mining / heading to the nether / going home*.

- **everyone:** nice, the farm life · gl, get some wheat lol
- **homebody:** love that, have fun

#### `told.plan.fishing`

You said: *going mining / heading to the nether / going home*.

- **everyone:** get me a mending book lol · gl, catch something good · relaxing, nice

#### `told.plan.home`

You said: *going mining / heading to the nether / going home*.

- **everyone:** cya · ok, cya later · safe travels · gn if you're sleeping
- **homebody:** home is the best

#### `told.plan.hunting`

You said: *going mining / heading to the nether / going home*.

- **everyone:** gl, bring me some steak lol · happy hunting · gl

#### `told.plan.mining`

You said: *going mining / heading to the nether / going home*.

- **everyone:** bring back dias lol · gl, find dias · dont dig straight down · gl, watch for lava · bring me some iron lol
- **miner:** ooh where at? gl · y -58 is the spot

#### `told.plan.nether`

You said: *going mining / heading to the nether / going home*.

- **everyone:** bring fire res · gl, watch out for ghasts · dont fall in lava lol · wear gold · careful with the piglins · gl, find a fortress
- **newbie:** isnt it scary there??

#### `told.plan.stronghold`

You said: *going mining / heading to the nether / going home*.

- **everyone:** gl, follow the eyes · bring lots of eyes · almost there then
- **speedrunner:** nice, triangulate it

#### `told.plan.trading`

You said: *going mining / heading to the nether / going home*.

- **everyone:** get mending! · dont let the villagers rip you off · gl with the trades

#### `told.promise`

You said: *ill bring you 5 iron / i owe you*.

- **everyone:** deal · ill hold you to that · ok, {item}, dont forget lol · bet · deal, {item} · noted. {item} · sweet, ty · ill remember that · holding you to it · ok ok, {item}
- **newbie:** really?? ty!! · omg ok!! {item}!!
- **hoarder:** {item}. i'll be waiting · dont forget. {item} · i'm writing that down
- **hermit:** we'll see · sure
- **bandit:** you better · or else · {item}. dont make me come get it
- **PvP sweat:** ok · sure, {item}
- **helper:** aw you dont have to! ty · ty! no rush
- **miner:** nice, {item} is always good

#### `told.promise_any`

You said: *ill bring you 5 iron / i owe you*.

- **everyone:** you owe me one then · ill remember that lol · noted · deal · ok, you owe me · holding you to that · lol ok
- **helper:** you dont owe me anything! · nah youre good
- **hoarder:** yes you do. i'm counting
- **bandit:** you do. dont forget
- **hermit:** hm

#### `told.promise_kept`

You said: *ill bring you 5 iron / i owe you*.

- **everyone:** you actually brought it, ty · ayy you remembered · a person of their word, ty · the {item}! ty · ty, as promised lol · ty {player}, you kept your word · W, thanks
- **newbie:** you remembered!! tysm · omg the {item}!! ty
- **hoarder:** {item}. good. thank you · right on time
- **hermit:** ...you came back. ty
- **bandit:** good. smart · heh, you kept it
- **helper:** aww ty, you didnt have to! · you remembered! ty!!
- **PvP sweat:** ty, respect

#### `told.promise_late_kept`

You said: *ill bring you 5 iron / i owe you*.

- **everyone:** finally lol, ty · better late than never · took you long enough lol, ty · oh you remembered, ty · ayy finally
- **hoarder:** late. but ty
- **helper:** no worries, ty!
- **bandit:** about time

#### `told.like`

You said: *i love cats / i hate creepers*.

- **everyone:** {thing}? nice · same honestly · respect · {thing} is cool · nice taste · oh me too · yeah {thing} is great · {thing}, nice · fair, {thing} is good
- **newbie:** ooh {thing}!! me too!! · whats {thing}? jk lol
- **PvP sweat:** {thing}? ok · eh, pvp is better
- **builder:** nice, {thing} is cool · same, building is better tho
- **miner:** nice. mining is better tho lol
- **hermit:** hm. ok · {thing}. fine
- **helper:** aw nice, {thing} is great! · love that!
- **hoarder:** {thing}? i'd keep it all

#### `told.hate`

You said: *i love cats / i hate creepers*.

- **everyone:** fair · {thing} is the worst lol · real · same tbh · lol why · ok fair · valid · {thing}? really?
- **newbie:** me too!! {thing} is scary
- **PvP sweat:** skill issue lol
- **hermit:** hm
- **helper:** aw, thats ok lol

#### `told.word_bad`

You said: *bob is a griefer / annie is cool*.

- **everyone:** noted, ill watch out for {who} · {who}? really? · ugh, ok · good to know · ty for the warning · yikes, ok · i'll keep an eye on {who} · for real? ok
- **newbie:** oh no, ok!! i'll be careful · {who}?? scary
- **PvP sweat:** let {who} try me · noted. i'll deal with it
- **hermit:** figures · noted
- **bandit:** heh, ok · noted
- **helper:** oh no, are you ok? · ty for telling me, be safe
- **hoarder:** locking my chests · noted. hiding my stuff

#### `told.word_good`

You said: *bob is a griefer / annie is cool*.

- **everyone:** good to know · {who} seems nice yeah · nice, noted · oh cool · yeah {who} is alright · noted · ty for telling me
- **newbie:** {who} is nice!! i agree · yay
- **hermit:** hm. noted
- **bandit:** noted. soft target · heh, ok
- **helper:** aw thats nice to hear!

#### `told.word_bad_disagree`

You said: *bob is a griefer / annie is cool*.

- **everyone:** nah {who} is cool · {who}? cant be · {who} has been fine to me · idk, {who} is my friend · hm, not what i've seen · nah
- **hermit:** no
- **helper:** {who}? i'm sure it's a misunderstanding

#### `told.word_good_disagree`

You said: *bob is a griefer / annie is cool*.

- **everyone:** {who}? not to me · hm. we'll see · {who}? nah · lol no · not my experience · i dont trust {who}
- **bandit:** {who}? no
- **helper:** hm, i hope so

#### `told.word_doubt`

You said: *bob is a griefer / annie is cool*.

- **everyone:** why should i believe you · says you · hm. sure · and why would i trust you · i dont even know you · ok lol · sure buddy
- **helper:** hm, i'll see for myself
- **bandit:** nice try

#### `told.news.back`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** wb · welcome back · wb {player} · wb o/ · ayy wb
- **homebody:** welcome back!! :)
- **hermit:** mm

#### `told.news.built`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** ooh show me · nice, i wanna see it · W, gg · nice!! where? · ooh what did you build?
- **builder:** ooh show me!! what style? · nice, i love builds
- **newbie:** can i see?? :D
- **hermit:** nice

#### `told.news.diamonds`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** W · lets gooo · nice!! · how many? · W, gg · ooh nice · dias!! W · W, where at?
- **newbie:** omg W!!! · diamonds!! lucky!!
- **miner:** W, how deep? · nice, y -58?
- **hoarder:** ...can i have one · nice. how many exactly
- **bandit:** oh really. nice · heh, W
- **PvP sweat:** W, get armor
- **hermit:** nice

#### `told.news.died`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** rip · f · oof, rip · rip, what got you? · f, get your stuff back? · rip, need help getting your stuff? · o7 · oof
- **newbie:** oh no!! rip · noooo
- **PvP sweat:** skill issue · lol rip
- **helper:** oh no! want help getting your stuff? · rip, you ok?
- **hermit:** happens
- **bandit:** where'd you die? asking for a friend

#### `told.news.dragon`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** W!!! · gg!! · no way, gg · lets gooo · gg wp, you beat the game
- **PvP sweat:** gg, how long did it take
- **speedrunner:** what time? lol

#### `told.news.end`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** woah, how was it? · nice, see the dragon? · W

#### `told.news.hungry`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** same lol · eat something · i got nothing sorry · go kill a cow lol · same, i'm out too · rip, try some berries
- **helper:** oh no, sorry i dont have any!
- **hermit:** eat then

#### `told.news.hungry_offer`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** want some {food}? · i got {food}, want some? · here, want {food}? · need some {food}?
- **helper:** i have {food}! want some? · take some {food}?
- **hoarder:** ...want some {food}? just a little

#### `told.news.lost`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** what are your coords? · check your coords, im at {coords} · f3 is your friend lol · im at {coords} if that helps · follow the sun lol
- **helper:** oh no! im at {coords}, come find me · want me to come get you? im at {coords}
- **hermit:** hm. good luck

#### `told.news.nether`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** how was it? · W, you survived · nice, find anything? · welcome back lol · ooh nice
- **newbie:** woah you went there??

#### `told.news.tired`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** go sleep lol · get some rest · same tbh · sleep is op · take a break
- **homebody:** go rest! :)

#### `told.hungry_yes`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** here · catch · here you go · take this
- **helper:** here! eat up

#### `told.hungry_no`

You said: *i died / found diamonds / im hungry / im back / im lost*.

- **everyone:** ok · suit yourself · ok lol

### More to ask

| Intent | For example | What it answers |
| --- | --- | --- |
| me | who am i, whats my name, do you remember me | What it calls you, and something it remembers |
| opinion | what do you think of me / of bob, do you like cats, are we cool | How it feels about you or them (trust in words), or the thing |
| where_is | wheres bob, have you seen annie | In sight, or where and when it last saw them (to someone it knows) |
| going | where are you going | Where its current goal takes it |
| need | what do you need, need anything | What it's short of: health, food, a home, the next thing it's after |
| friends | who are your friends | The players and Drifters it likes best |
| told | what did i tell you | Something you told it (or did: a gift, an insult) |
| my_base | wheres my base | Where you said you live, or where it saw you live |
| forget | forget that, forget everything | Forgets the last thing you told it, or all of it |
| likes | what do you like | Its favourite things, in its voice |
| seen_... (heard) | have you seen a village | Not seen one, but told of one: says who said where |

#### `ask.me_known`

Answering **me**.

- **everyone:** youre {nick} · {nick}, ofc · yeah, {nick} · ofc i know you, {nick} · {nick}! how could i forget · {nick}. you {memory} · ofc, {nick}. {memory} · yeah {nick}, {memory}
- **newbie:** {nick}!! ofc!!
- **hermit:** {nick}. yes · you're {nick}
- **helper:** {nick}! ofc i remember you :)
- **bandit:** {nick}. i remember you

#### `ask.me_stranger`

Answering **me**.

- **everyone:** idk, who are you? · never seen you before · should i? · nope, who are you · uh, no · havent met you lol
- **hermit:** no · no idea
- **newbie:** no, sorry! who are you?
- **bandit:** not yet

#### `ask.opinion_dislike`

Answering **opinion**.

- **everyone:** {who}? not a fan · {who} is annoying · {who} is kinda sus · dont trust {who} · eh, {who}...

#### `ask.opinion_hate`

Answering **opinion**.

- **everyone:** {who}? dont get me started · cant stand {who} · {who} is the worst · stay away from {who} · {who}? ugh

#### `ask.opinion_like`

Answering **opinion**.

- **everyone:** {who} is cool · {who}? yeah, good friend · i like {who} · {who} is nice · {who} is alright

#### `ask.opinion_love`

Answering **opinion**.

- **everyone:** {who}? love {who} · {who} is my best friend · {who} is the best · {who} is great · would die for {who} lol

#### `ask.opinion_me_dislike`

Answering **opinion**.

- **everyone:** not really · eh, no · you're kinda sus · not after what you did · no comment · nah
- **helper:** i'm still a bit upset with you

#### `ask.opinion_me_hate`

Answering **opinion**.

- **everyone:** no · lol no · i dont trust you at all · stay away from me · absolutely not
- **bandit:** you're on my list

#### `ask.opinion_me_like`

Answering **opinion**.

- **everyone:** yeah we're cool · ofc · youre cool · yeah youre alright · we're good · yep, friends
- **hermit:** you're ok

#### `ask.opinion_me_love`

Answering **opinion**.

- **everyone:** youre the best · ofc we're friends · love you man · best friends lol · youre my favorite person · ride or die
- **hermit:** you're alright. more than alright
- **PvP sweat:** you're cool, dont tell anyone

#### `ask.opinion_me_meh`

Answering **opinion**.

- **everyone:** i barely know you · idk you yet · ask me later · eh, we'll see · meh · jury's still out
- **hermit:** dont know you

#### `ask.opinion_me_ok`

Answering **opinion**.

- **everyone:** youre alright · you seem ok · we're good i guess · sure · youre fine · still getting to know you
- **hermit:** hm. fine

#### `ask.opinion_meh`

Answering **opinion**.

- **everyone:** eh, {who} · {who}? meh · barely know {who} · no opinion · {who}? never really talked

#### `ask.opinion_ok`

Answering **opinion**.

- **everyone:** {who} is ok · {who}? fine i guess · {who} is alright · dont know {who} that well · {who} seems fine

#### `ask.opinion_thing`

Answering **opinion**.

- **everyone:** {thing}? its alright · love {thing} · {thing} is cool · eh, {thing} · {thing}? yeah its good · not really into {thing}
- **newbie:** {thing} is so cool!!
- **hermit:** {thing}. fine
- **PvP sweat:** {thing}? eh, pvp is better
- **builder:** {thing}? i'd rather build
- **miner:** {thing}? i'd rather mine

#### `ask.opinion_unknown`

Answering **opinion**.

- **everyone:** who's {who}? · never met {who} · dont know {who} · {who}? no idea · never heard of {who}

#### `ask.where_here`

Answering **where_is**.

- **everyone:** {who}? right there lol · {who} is right there · uh, behind you · right there lol · look around lol

#### `ask.where_seen`

Answering **where_is**.

- **everyone:** saw {who} {when}, {where} · {who} was {where} {when} · {when}, {where} · last saw {who} {where}, {when} · {who}? {where}, {when}
- **hermit:** {where}. {when}

#### `ask.where_unknown`

Answering **where_is**.

- **everyone:** no idea · havent seen {who} · dunno · havent seen {who} around · not sure, sorry
- **hermit:** dont know

#### `ask.where_no`

Answering **where_is**.

- **everyone:** why do you wanna know · not telling · ask {who} yourself · idk you well enough · why?
- **bandit:** what's it worth to you

#### `ask.where_me`

Answering **where_is**.

- **everyone:** right here lol · uh, here? · im right here · here lol · you're looking at me
- **hermit:** here.

#### `ask.going`

Answering **going**.

- **everyone:** {doing} · just {doing} · gonna go {doing} · {doing}, wbu · nowhere really, {doing}
- **hermit:** away · {doing}.

#### `ask.going_idle`

Answering **going**.

- **everyone:** nowhere lol · nowhere really · just chilling · dunno yet · wherever · no plans
- **nomad:** wherever the road goes · somewhere new, eventually
- **hermit:** nowhere

#### `ask.need_heal`

Answering **need**.

- **everyone:** to heal up lol · food and a nap · a minute to heal · health lol
- **newbie:** help im hurt

#### `ask.need_food`

Answering **need**.

- **everyone:** food tbh · food, im starving · some food · anything to eat? · food pls
- **hoarder:** food. lots of it

#### `ask.need_home`

Answering **need**.

- **everyone:** a place to live lol · a home · somewhere to settle · a base
- **nomad:** nothing. home is overrated

#### `ask.need_target`

Answering **need**.

- **everyone:** {what} · need {what} · working on {what} · {what} rn · looking for {what} · {what}, got any?
- **hermit:** {what}.
- **hoarder:** {what}. and more of everything

#### `ask.need_nothing`

Answering **need**.

- **everyone:** nothing, im good · im good, ty · all good · nah im set · nothing rn
- **hoarder:** more. always more
- **helper:** im good! do you need anything?

#### `ask.friends`

Answering **friends**.

- **everyone:** {friends} · {friends} mostly · {friends}, why · uh {friends} · {friends} :)
- **hermit:** {friends}. that's it
- **bandit:** friends? {friends}, i guess

#### `ask.friends_you`

Answering **friends**.

- **everyone:** you lol · just you · you, ofc · you're my only friend lol
- **hermit:** you. i guess

#### `ask.friends_none`

Answering **friends**.

- **everyone:** nobody really · dont have any lol · working on it · no one yet
- **hermit:** dont need any
- **PvP sweat:** friends are a weakness
- **newbie:** none yet :( wanna be friends?

#### `ask.told`

Answering **told**.

- **everyone:** {memory} · uh, {memory} · i remember {memory} · {memory}, right? · you said {memory}

#### `ask.told_none`

Answering **told**.

- **everyone:** nothing really · you havent told me anything lol · not much · uh, nothing? · idk, you tell me

#### `ask.my_base_told`

Answering **my_base**.

- **everyone:** you said {where} · {where}, right? · {where} · uh, {where} i think

#### `ask.my_base_seen`

Answering **my_base**.

- **everyone:** {where}, i saw it · {where} · its {where}

#### `ask.my_base_none`

Answering **my_base**.

- **everyone:** no idea, you never said · you didnt tell me · idk · you never showed me

#### `ask.forget`

Answering **forget**.

- **everyone:** ok, forgot · forgot what? lol · ok ok · already forgotten · what thing?
- **hermit:** fine

#### `ask.forget_all`

Answering **forget**.

- **everyone:** who are you again? lol · ok, forgot everything · done, clean slate · ok, never met you
- **helper:** ok! clean slate :)

#### `ask.forget_nothing`

Answering **forget**.

- **everyone:** forget what? · you didnt tell me anything lol · nothing to forget

#### `ask.likes`

Answering **likes**.

- **everyone:** mining and exploring · sunsets tbh · building stuff · a good cave · finding diamonds · chilling · just playing
- **casual:** just chilling, doing whatever · a bit of everything
- **newbie:** everything!! its all new · pigs!! and diamonds
- **builder:** building, obviously · a good build · making things look nice
- **miner:** mining. obviously · strip mining at y -58 · finding dias
- **homebody:** my home :) · a cozy base · farming and cooking
- **speedrunner:** going fast · beating my pb · the dragon fight
- **nomad:** the road · new places · seeing what's over the next hill
- **hoarder:** full chests · having lots of stuff · my collection
- **hermit:** quiet · being left alone
- **PvP sweat:** pvp · winning · 1v1s
- **bandit:** other people's stuff lol · easy loot
- **helper:** helping people! · making friends

#### `ask.seen_heard`

Answering **seen_... (heard)**.

- **everyone:** havent seen {what} myself, but {from} said {where} · {from} said theres {what} {where} · not myself, but {from} told me {where} · heard about {what} {where}, from {from}

### Words it fills in

Where it's going, by its current goal (`going.<goal>`), and the words for places, plans, times and memories:

- `going.bank`: the bank
- `going.buy`: shopping lol · to buy stuff
- `going.cavehunch`: checking out a cave · caving · theres a cave i wanna see
- `going.collect`: picking up my furnace stuff
- `going.converse`: talking lol
- `going.cook`: cooking · to the furnace
- `going.corpse_run`: getting my stuff back · to where i died
- `going.delve`: mining · down to mine · caving
- `going.enchant`: enchanting
- `going.explore`: just exploring · wherever lol · exploring, wanna come? · no idea, just wandering · looking for stuff
- `going.farm`: the farm · to my crops
- `going.fight`: fighting · busy fighting
- `going.fish`: fishing
- `going.flee`: AWAY · anywhere but here
- `going.followme`: with you lol · following you
- `going.food`: getting food · food run
- `going.house`: building my house · working on my house
- `going.hunt`: hunting · getting food · to kill some cows lol
- `going.hut`: building a hut
- `going.light`: putting torches up
- `going.loot`: looting
- `going.nether_go`: the nether · nether trip
- `going.nighthunt`: hunting mobs · out hunting
- `going.pen`: my animal pen
- `going.planhouse`: planning my house
- `going.portal`: building a portal
- `going.prey`: hunting
- `going.pursue`: after someone
- `going.roads`: building a road
- `going.settle`: looking for a place to live · finding a spot to settle
- `going.shelter`: home, its getting dark · inside, night's coming · somewhere safe
- `going.stall`: my stall
- `going.store`: home to drop stuff off · to my chests
- `going.tax`: paying taxes ugh
- `going.tidy`: cleaning up
- `going.townbuild`: building for the town · town building stuff
- `going.trade`: trading
- `going.trade_run`: on a trade run · trading in another town
- `going.visit`: visiting someone · to see a friend
- `going.wages`: getting paid lol
- `going.whim`: nowhere really, just messing around · just doing something fun
- `place.where.exact`: at {coords} · around {coords}
- `place.where.near`: near here · around here · close by
- `place.where.way`: {way} of here · somewhere {way}, about {dist} blocks · like {dist} blocks {way} · {way}, {dist} blocks or so
- `place.word.ancient_city`: an ancient city
- `place.word.bastion`: a bastion
- `place.word.bees`: bees
- `place.word.cave`: a cave · a big cave
- `place.word.cherry`: a cherry grove
- `place.word.coal`: coal
- `place.word.cows`: cows
- `place.word.desert`: a desert
- `place.word.diamonds`: diamonds
- `place.word.dungeon`: a dungeon · a spawner
- `place.word.end_city`: an end city
- `place.word.fortress`: a fortress
- `place.word.gold`: gold
- `place.word.horses`: horses
- `place.word.iron`: iron
- `place.word.jungle`: a jungle
- `place.word.lava`: lava
- `place.word.mansion`: a mansion
- `place.word.mineshaft`: a mineshaft
- `place.word.monument`: an ocean monument
- `place.word.mountain`: mountains
- `place.word.mushroom`: a mushroom island
- `place.word.ocean`: an ocean
- `place.word.outpost`: a pillager outpost
- `place.word.portal`: a portal
- `place.word.pumpkins`: pumpkins
- `place.word.ravine`: a ravine
- `place.word.sheep`: sheep
- `place.word.shipwreck`: a shipwreck
- `place.word.snow`: a snowy biome
- `place.word.stronghold`: a stronghold
- `place.word.sugar_cane`: sugar cane
- `place.word.swamp`: a swamp
- `place.word.temple`: a temple
- `place.word.trial_chambers`: trial chambers
- `place.word.village`: a village
- `plan.go.building`: building
- `plan.go.caving`: caving
- `plan.go.chopping`: chopping trees
- `plan.go.enchanting`: enchanting
- `plan.go.end`: to the end
- `plan.go.exploring`: exploring
- `plan.go.farming`: farming
- `plan.go.fishing`: fishing
- `plan.go.home`: home
- `plan.go.hunting`: hunting
- `plan.go.mining`: mining
- `plan.go.nether`: to the nether
- `plan.go.raid`: raiding
- `plan.go.stronghold`: looking for the stronghold
- `plan.go.trading`: trading
- `plan.word.building`: the build
- `plan.word.caving`: caving
- `plan.word.chopping`: chopping
- `plan.word.enchanting`: enchanting
- `plan.word.end`: the end
- `plan.word.exploring`: exploring
- `plan.word.farming`: the farm
- `plan.word.fishing`: fishing
- `plan.word.home`: home
- `plan.word.hunting`: the hunt
- `plan.word.mining`: mining
- `plan.word.nether`: the nether
- `plan.word.raid`: the raid
- `plan.word.stronghold`: the stronghold hunt
- `plan.word.trading`: trading
- `when.days`: a few days ago · a while ago · days ago
- `when.just`: just now · a sec ago · like a minute ago
- `when.minutes`: a few minutes ago · a bit ago · earlier
- `when.today`: earlier today · today · this morning
- `when.yesterday`: yesterday
- `memory.base`: you live {where} · your base is {where}
- `memory.gift`: you gave me {item} · you gave me {item}, ty again
- `memory.hate`: you hate {thing} · you dont like {thing}
- `memory.insult`: you called me {insult} · you said i was {insult}
- `memory.like`: you like {thing} · you're into {thing}
- `memory.news.back`: you came back
- `memory.news.built`: you built something
- `memory.news.diamonds`: you found diamonds · you got dias
- `memory.news.died`: you died lol · you died earlier
- `memory.news.dragon`: you beat the dragon
- `memory.news.end`: you went to the end
- `memory.news.hungry`: you were starving
- `memory.news.lost`: you got lost lol
- `memory.news.nether`: you went to the nether
- `memory.news.tired`: you were tired
- `memory.nick`: you go by {nick} · you're {nick}
- `memory.plan`: you went {plan_go} · you were off {plan_go}
- `memory.promise`: you owe me {item} · you said you'd bring {item}

## 9. Not chat, but close

- **Manhunt:** messages like "[Drifter] Bob respawned and is back on the hunt." are the mod's announcements.
- **Thoughts:** what a Drifter thinks (`/drifter thoughts`) never goes to chat.
- **The chronicle:** Drifter: Civilizations's morning news ("Day 4: ...") is the mod reporting.

## 10. What you can say to them

A message means whichever of these it has the longest phrase of: "no problem" is a yes though it has "no" in it, "hell no" a no, "yeah no" a no. Capitals, punctuation and stretched letters don't matter ("NOOOO." is no, "yesss" yes); apostrophes are dropped ("im" is "i'm"). The words can be anywhere in the message.

| Where | What it listens for |
| --- | --- |
| An escort, after it asks | **yes** or **ready** (it sets off), **no** (it goes alone), **unsure** (it asks again) |
| A race, after it challenges you | **ready** or **yes** (the countdown), **no**, **unsure** |
| A Drifter's name, or a part of it 4 letters or longer ("grumpy" or "baker" for xXGrumpybakerXx; not everyday words like "over" or "just"), within 32 blocks | It turns to look for you and may answer (huh) |
| To a Drifter (section 8) | **wyd**, **hru**, **name**, **coords**, **base**, **seen_...**, **follow**, **team**, **come**, **stop**, **help**, **give**, **trade**, **duel**, **compliment**, **insult**, **joke**, **bot**; and **me**, **opinion**, **where_is**, **going**, **need**, **friends**, **told**, **my_base**, **forget**, **likes** (section 8b) |
| Telling a Drifter (section 8b) | **tell_base**, **place_...** (with **place_cue** for "here"), **plan_...**, **promise**, **tell_like**, **tell_hate** (with **things**), **word_good**, **word_bad**, **news_...**; "call me ..." |
| Anyone near (not in an encounter) | **greeting**, **night**, **bye**, **thanks**: the rituals in section 2 |

Other encounters are answered by doing, not typing: hit a duellist to accept, throw items (Q) at a mugger or a hungry traveller, right-click a merchant or a stall keeper.

- **yes** (186): yes · yeah · yea · ye · yep · yup · ya · yah · yas · yus · yuh · yurr · yerr · yh · y · ok · okay · okey · okie · oki · okie dokie · okey dokey · k · kk · mkay · mk · okk · sure · sure thing · surely · sure why not · why not · sure sure · yeah sure · sure man · sure bro · of course · ofc · course · obviously · duh · naturally · absolutely · absolutely yes · definitely · def · defo · for sure · fs · for real yes · totally · certainly · indeed · bet · bet bet · ight bet · say less · say no more · lets go · lets gooo · lets do it · lets do this · lets roll · lets · im down · down · im in · count me in · sign me up · im game · game · im so down · deal · its a deal · you got a deal · you have a deal · deal deal · alright · aight · ight · alr · all right · allright · fine · fine by me · fine ok · ok fine · sounds good · sounds great · sounds fun · sounds like a plan · sgtm · cool · cool cool · nice · perfect · go for it · i guess · i suppose · guess so · suppose so · i think so · why not lol · aye · aye aye · yessir · yes sir · yes maam · roger · roger that · copy · copy that · affirmative · 10 4 · you bet · u bet · you know it · mhm · uh huh · mm hm · yesyes · yes yes · yep yep · ye ye · ya ya · hell yeah · heck yeah · hell yes · heck yes · fuck yeah · oh yeah · oh yes · yes please · yes pls · ye pls · please · lead the way · follow me · come on · come with me · lets move · i got you · i gotchu · got you · ill help · i ll help · ill do it · ill go · on it · i can do that · can do · gladly · happy to · with pleasure · no problem · no prob · np · no worries · no biggie · why the heck not · accept · accepted · i accept · agreed · agree · i agree · yeah ok · ok sure · ok ok · okay okay · ok lets go · alright lets go · true · facts · w · big w · absolutely lets go · lock in · lets lock in · you're on · youre on · ur on · it's on · its on · bring it · bring it on
- **no** (187): no · nah · na · naw · nope · nop · nay · noo · nu · nuh · nuh uh · naur · nada · nein · non non · no thanks · no thank you · no ty · no thx · nty · nah ty · nah thanks · no but thanks · no but ty · thanks but no · ty but no · nah im good · im good · im ok · im okay · im fine · all good · its fine · thats ok · thats okay · im alright · not really · not now · not today · not right now · maybe later · later · another time · some other time · next time · not this time · pass · ill pass · i pass · hard pass · gonna pass · imma pass · ill skip · skip · i ll pass · i will pass · no way · no chance · not a chance · fat chance · no shot · aint no way · in your dreams · dream on · keep dreaming · never · never ever · not ever · not in a million years · over my dead body · when pigs fly · absolutely not · definitely not · def not · certainly not · of course not · obviously not · hell no · heck no · hell nah · heck nah · fuck no · god no · lord no · oh no · oh hell no · nah fam · nah bro · nah man · nah dude · no bro · no man · no dude · nope nope · no no · nonono · nah nah · negative · decline · declined · i decline · denied · rejected · i refuse · refuse · no sir · no maam · no can do · cant do · cant · i cant · can t · busy · im busy · too busy · kinda busy · got stuff to do · no time · dont have time · gtg · gotta go · not interested · no interest · not for me · not my thing · not feeling it · dont feel like it · rather not · id rather not · i would rather not · prefer not · dont want to · dont wanna · i dont want to · i dont wanna · no want · nah dont want to · leave me alone · go away · get lost · buzz off · shoo · scram · beat it · back off · no deal · no dice · not happening · aint happening · thats a no · thats gonna be a no · big no · hard no · strong no · no from me · no lol · lol no · nah lol · lmao no · ew no · eww no · yeah no · yea no · ye no · im out · count me out · deal me out · not with you · find someone else · ask someone else · sorry no · sorry cant · no sorry · sorry nah · unfortunately not · unfortunately no · afraid not · im afraid not · sadly no · i dont think so · dont think so · i think not · i dont think thats a good idea · bad idea · nope not doing that · not doing that · absolutely not happening
- **ready** (39): go · go go · go go go · gogogo · gogo · ready · im ready · ready go · ready set go · set go · on your marks · on your mark · lets race · race · race you · start · start it · begin · 3 2 1 · 321 · countdown · count it down · now · ok go · alright go · send it · lets go · lets gooo · bring it · bring it on · youre on · ur on · its on · lets do it · say when · whenever youre ready · im set · set · hit it
- **unsure** (30): maybe · idk · i dont know · dunno · not sure · im not sure · hmm · hm · let me think · lemme think · depends · it depends · eh · kinda · sort of · sorta · possibly · perhaps · we will see · well see · i guess not sure · what · huh · wdym · what do you mean · how much · how far · is it safe · why · why me
- **greeting** (43): hi · hii · hey · heyo · heya · hello · hullo · helo · yo · yoo · sup · wsg · wsp · wassup · whats up · what up · wazzup · howdy · hiya · ayo · ayy · o · o/ · good morning · gm · morning · mornin · good afternoon · good evening · evening · hey all · hi all · hey everyone · hi everyone · hello everyone · greetings · hey guys · hi guys · yo guys · hey yall · im back · back · im here
- **night** (23): gn · gnn · good night · goodnight · night · nite · nighty night · night all · gn all · gn everyone · night everyone · nn · gnight · going to bed · off to bed · bedtime · time to sleep · sleep well · sweet dreams · logging off for the night · heading to bed · going to sleep · zzz
- **thanks** (30): ty · tyy · thx · thanks · thank you · thank u · tysm · tyvm · ty so much · thanks so much · thanks a lot · much appreciated · appreciate it · appreciate you · cheers · ta · merci · gracias · danke · ty o7 · thank you so much · tyty · ty ty · thanks man · thanks bro · ty bro · you rock · ur the best · youre the best · legend
- **bye** (31): bye · byee · bye bye · bb · cya · see ya · see you · see you later · see ya later · later · laters · l8r · take care · o7 · peace · peace out · im out · gtg · got to go · gotta go · have to go · heading out · logging off · logging · brb · afk · farewell · goodbye · good bye · catch you later · ttyl
- **me** (18): whats my name · what is my name · do you remember me · remember me · do you know me · you know me · u know me · who am i · know who i am · do you know who i am · you remember me · u remember me · forgot me · did you forget me · whats my nickname · what do you call me · what do u call me · say my name
- **opinion** (31): what do you think of · what do you think about · what do u think of · what do u think about · what you think of · what you think about · thoughts on · do you like · do u like · you like · how do you feel about · how do u feel about · do you trust · do u trust · you trust · are we friends · are we cool · are we good · we cool · we good · we friends · do you hate · do u hate · you hate · is he nice · is she nice · are they nice · whats your opinion on · opinion on · your opinion of · rate
- **where_is** (14): where is · wheres · where s · have you seen · you seen · u seen · seen · know where · where did · whered · where can i find · any sign of · where went · where are they
- **going** (17): where are you going · where you going · where u going · where r u going · where are u going · where you headed · where are you headed · where u headed · where you off to · where are you off to · where u off to · where to · going somewhere · heading somewhere · where are you heading · where you heading · where u heading
- **need** (28): what do you need · what do u need · whatcha need · what you need · what u need · need anything · do you need anything · you need anything · u need anything · anything you need · need something · do you need help · do u need help · you need help · u need help · need any help · need a hand · what are you looking for · what you looking for · what u looking for · what are you after · what you after · what do you want · what do u want · whatcha want · can i help · can i help you · how can i help
- **friends** (17): who are your friends · who r your friends · whos your friend · who is your friend · your friends · ur friends · best friend · bestie · who do you like · who do u like · who do you hang out with · do you have friends · got any friends · any friends · whos your bestie · who are ur friends · whos ur friend
- **told** (13): what did i tell you · what did i tell u · what did i say · remember what i said · what do you know about me · what do u know about me · what do you remember · what do u remember · do you remember what i said · what have i told you · tell me what you know about me · what do you know of me · what did i say earlier
- **my_base** (17): wheres my base · where is my base · where do i live · wheres my house · where is my house · wheres my home · where is my home · remember my base · do you know my base · know my base · where i live · know where i live · my base coords · wheres my place · where is my place · wheres my castle · where did i say my base was
- **forget** (15): forget it · forget that · forget what i said · forget everything · forget about it · forget me · forget all · forget all of it · scratch that · ignore that · ignore what i said · dont remember that · never mind that · forget everything i said · forget everything i told you
- **likes** (18): what do you like · what do u like · whats your favorite · whats your favourite · whats your fav · whats ur fav · whats ur favorite · favorite thing · fav thing · what are you into · what r u into · what u into · what do you enjoy · any hobbies · whats your hobby · whats ur hobby · what do you do for fun · what do u do for fun
- **word_good** (36): is cool · is nice · is chill · is my friend · is a friend · is my buddy · is my bestie · is good · is great · is awesome · is kind · is the best · is legit · is trustworthy · you can trust · u can trust · trust him · trust her · trust them · helped me · saved me · is a good guy · is good people · is goated · is sweet · is funny · is a legend · is a w · is cracked · is pro · is a pro · is helpful · is generous · gave me · is friendly · is safe
- **word_bad** (50): is a griefer · griefer · griefed · griefed me · is mean · is bad · is evil · is a thief · is a scammer · scammed me · scammer · stole · stole from me · stole my · robbed me · killed me · is toxic · is annoying · is a jerk · is rude · is sus · is shady · is a liar · lied to me · liar · dont trust · do not trust · never trust · cant trust · watch out for · be careful of · be careful around · be careful with · stay away from · keep away from · hates me · attacked me · hit me · is dangerous · is trash · is a bandit · is a pk · is a killer · is a menace · spawn killed me · trapped me · burned my · blew up my · broke my · took my stuff
- **tell_base** (28): my base · my house · my home · i live · my place · my castle · my town · my village · my bunker · my hut · my cabin · my build · our base · our house · our town · we live · my mansion · my tower · my plot · my starter base · my main base · this is my base · this is my house · welcome to my base · welcome to my house · my camp · my shack · my hideout
- **promise** (38): ill bring you · ill give you · ill get you · ill pay you · ill send you · ill craft you · ill make you · ill find you · ill bring u · ill give u · ill get u · ill pay u · i will bring you · i will give you · i will get you · i will pay you · gonna bring you · gonna give you · gonna get you · im gonna bring you · im gonna give you · i promise · promise ill · pinky promise · i owe you · i owe u · owe you one · owe u one · ill pay you back · ill pay u back · ill make it up to you · ill repay you · next time ill bring · ill bring some · ill bring it · ill bring them · ill save you some · ill save u some
- **tell_like** (23): i like · i love · i really like · i really love · i luv · i enjoy · im into · im really into · im a fan of · my favorite · my fav · my favourite · i adore · love me some · i prefer · i rly like · i kinda like · big fan of · huge fan of · im obsessed with · cant get enough of · i lowkey love · i lowkey like
- **tell_hate** (17): i hate · i dont like · i do not like · i cant stand · i despise · i really hate · not a fan of · im not a fan of · i loathe · i dislike · i hate it when · i rly hate · i kinda hate · i lowkey hate · screw · i really dont like · ugh i hate
- **things** (215): building · mining · pvp · pve · farming · exploring · exploration · fishing · redstone · parkour · speedrunning · speedruns · the nether · nether · the end · caves · caving · villages · trading · enchanting · brewing · potions · hardcore · survival · creative · bedwars · skywars · skyblock · minigames · boats · elytra · flying · music discs · mods · modded · shaders · sunsets · sunrises · the night · night time · rain · storms · thunderstorms · snow · mountains · forests · deserts · the ocean · oceans · jungles · swamps · cherry trees · cherry blossoms · mushroom islands · ice spikes · structures · dungeons · raids · bosses · the dragon · the wither · archery · swords · axes · tridents · maces · bows · crossbows · armor trims · trims · banners · decorating · interior design · terraforming · gardening · pixel art · statues · castles · bridges · roads · railways · rails · minecarts · horse racing · pets · taming · villager trading · grinding · afk farms · farms · mob farms · iron farms · gold farms · xp farms · automation · tech · storage systems · sorting · hoarding · collecting · loot · treasure · treasure hunting · shipwrecks · temples · ancient cities · sculk · the deep dark · netherite · diamonds · emeralds · copper · amethyst · lanterns · torches · campfires · music · note blocks · books · maps · map art · cartography · teamwork · multiplayer · singleplayer · coop · challenges · nostalgia · old versions · updates · snapshots · glitches · texture packs · resource packs · skins · creepers · zombies · skeletons · spiders · endermen · the warden · phantoms · drowned · slimes · ghasts · blazes · piglins · hoglins · baby zombies · chickens · pigs · sheep · cows · cats · dogs · wolves · foxes · axolotls · bees · frogs · parrots · pandas · goats · sniffers · allays · camels · llamas · turtles · dolphins · squids · glow squids · horses · mooshrooms · rabbits · bunnies · polar bears · iron golems · snow golems · villagers · wandering traders · pillagers · witches · lava · water · sand · gravel · dirt · cobblestone · deepslate · wood · spruce · birch · dark oak · oak · acacia · jungle wood · mangroves · bamboo · cactus · flowers · sunflowers · pumpkins · melons · cake · cookies · bread · steak · golden apples · golden carrots · beetroot soup · rabbit stew · pumpkin pie · honey · sweet berries · glow berries
- **place_cue** (21): theres · there is · there are · theres a · found · i found · spotted · i spotted · saw · i saw · is here · are here · is over there · are over there · look · check out · come see · loads of · lots of · tons of · a bunch of
- **place_village** (5): village · villages · villagers · a town · some villagers
- **place_diamonds** (5): diamonds · diamond · dias · dia · diamond ore
- **place_iron** (3): iron · iron ore · raw iron
- **place_gold** (2): gold · gold ore
- **place_coal** (2): coal · coal ore
- **place_dungeon** (7): dungeon · spawner · mob spawner · zombie spawner · skeleton spawner · spider spawner · monster spawner
- **place_portal** (4): portal · nether portal · ruined portal · broken portal
- **place_fortress** (3): fortress · nether fortress · blaze spawner
- **place_bastion** (2): bastion · bastion remnant
- **place_cave** (5): cave · caves · cavern · big cave · huge cave
- **place_mineshaft** (3): mineshaft · abandoned mineshaft · mine shaft
- **place_ravine** (2): ravine · canyon
- **place_temple** (4): temple · desert temple · jungle temple · pyramid
- **place_shipwreck** (4): shipwreck · ship wreck · wrecked ship · sunken ship
- **place_monument** (4): monument · ocean monument · guardians · guardian temple
- **place_stronghold** (2): stronghold · end portal
- **place_ancient_city** (3): ancient city · deep dark · warden
- **place_outpost** (3): outpost · pillager outpost · pillager tower
- **place_mansion** (2): mansion · woodland mansion
- **place_trial_chambers** (4): trial chamber · trial chambers · trial spawner · vault
- **place_lava** (3): lava pool · lava lake · lava
- **place_end_city** (2): end city · end ship
- **place_mountain** (4): mountain · mountains · big mountain · peak
- **place_ocean** (3): ocean · the sea · a sea
- **place_desert** (1): desert
- **place_jungle** (1): jungle
- **place_swamp** (2): swamp · mangrove swamp
- **place_cherry** (5): cherry grove · cherry trees · cherry blossom · cherry forest · pink trees
- **place_mushroom** (4): mushroom island · mushroom biome · mooshrooms · mushroom fields
- **place_snow** (6): snow biome · snowy biome · snowy · ice spikes · igloo · snowy plains
- **place_sheep** (1): sheep
- **place_cows** (2): cows · cow
- **place_horses** (2): horses · horse
- **place_bees** (3): bees · bee nest · beehive
- **place_sugar_cane** (2): sugar cane · sugarcane
- **place_pumpkins** (4): pumpkins · pumpkin · melons · melon
- **plan_mining** (18): going mining · gonna go mining · gonna mine · going to mine · off to mine · heading to mine · go mining · going strip mining · going branch mining · time to mine · mining time · gonna strip mine · gonna go mine · heading down to mine · off mining · back to mining · gonna dig · going down to mine
- **plan_caving** (10): going caving · gonna go caving · going spelunking · going into a cave · going in the cave · gonna explore caves · gonna explore some caves · heading into the caves · cave diving · off caving
- **plan_nether** (12): going to the nether · gonna go to the nether · heading to the nether · off to the nether · going nether · nether trip · going in the nether · into the nether · gonna do the nether · gonna go nether · back to the nether · going to the nether rn
- **plan_end** (9): going to the end · heading to the end · fight the dragon · kill the dragon · gonna kill the dragon · off to the end · end trip · gonna fight the dragon · going to fight the dragon
- **plan_exploring** (15): going exploring · gonna explore · going to explore · off exploring · heading out exploring · going on an adventure · gonna go on an adventure · going for a walk · gonna explore a bit · gonna look around · going to look around · going scouting · gonna scout · off to explore · exploring time
- **plan_building** (11): going to build · gonna build · working on my build · off to build · gonna work on my base · working on my base · going to work on my house · build time · gonna work on my house · back to building · gonna decorate
- **plan_fishing** (6): going fishing · gonna fish · gonna go fish · fishing time · gonna go fishing · off fishing
- **plan_farming** (9): going farming · gonna farm · working on my farm · tend the farm · gonna harvest · gonna plant · going to harvest · farm time · gonna work on my farm
- **plan_hunting** (11): going hunting · gonna hunt · gonna kill some mobs · mob hunting · gonna get food · going to get food · getting food · gonna get some food · off hunting · monster hunting · gonna grind mobs
- **plan_home** (14): going home · heading home · head home · going back home · back to base · going back to base · going back to my base · gonna go home · off home · heading back home · heading back to base · going back to my house · going to my base · going to my house
- **plan_trading** (8): going trading · gonna trade with villagers · going to the village · heading to the village · trading with villagers · gonna go trade · going to trade · villager trading time
- **plan_raid** (5): going to raid · gonna raid · going raiding · fight the raid · gonna start a raid
- **plan_stronghold** (7): going to find the stronghold · looking for the stronghold · stronghold hunting · throwing eyes · gonna find the end portal · gonna find the stronghold · going to find the end portal
- **plan_chopping** (10): going to chop · gonna chop wood · chopping trees · get some wood · getting wood · gonna get wood · going to get wood · wood run · gonna chop some trees · going to chop trees
- **plan_enchanting** (5): gonna enchant · going to enchant · enchanting my · gonna enchant my stuff · enchanting time
- **news_died** (21): i died · just died · died lol · i got killed · got killed · i fell · fell in lava · i died to · died to a · rip me · i got blown up · creeper got me · creeper blew me up · lost my stuff · lost all my stuff · lost everything · lost my items · i drowned · fell off a cliff · i burned · killed me
- **news_diamonds** (14): found diamonds · got diamonds · found dias · got dias · found a diamond · got a diamond · finally diamonds · found my first diamonds · mined diamonds · dias lets go · diamonds lets go · i found diamonds · i got diamonds · first diamonds
- **news_hungry** (13): im hungry · so hungry · im starving · starving · no food · out of food · need food · i need food · im so hungry · hungry af · im out of food · got no food · i have no food
- **news_built** (13): finished my house · finished my base · built a house · just built · finished building · done building · my build is done · finished the build · built my base · finished my build · built a base · built my house · finally finished
- **news_nether** (6): back from the nether · been to the nether · just got back from the nether · survived the nether · went to the nether · came back from the nether
- **news_end** (4): back from the end · been to the end · went to the end · came back from the end
- **news_dragon** (8): killed the dragon · beat the dragon · killed the ender dragon · beat the ender dragon · beat the game · beat minecraft · dragon is dead · the dragon is dead
- **news_back** (8): im back · back now · just got back · back again · i am back · im home · made it back · im back home
- **news_tired** (7): im tired · so tired · im sleepy · sleepy · exhausted · im exhausted · im so tired
- **news_lost** (10): im lost · i got lost · got lost · i am lost · i dont know where i am · dont know where i am · lost my way · cant find my way · cant find my base · cant find my house
- **wyd** (23): wyd · wud · what you doing · what are you doing · what r u doing · what are u doing · whatcha doing · watcha doing · what you up to · what are you up to · what r u up to · whats up with you · what u doing · what you doin · what are you doin · you busy · are you busy · u busy · busy · what are you working on · what you working on · whatcha up to · up to anything
- **hru** (24): how are you · how r u · how are u · hru · how you doing · how are you doing · how u doing · hows it going · how is it going · you good · u good · you ok · u ok · are you ok · are you okay · you okay · how have you been · hows life · hows your day · how was your day · you alright · u alright · wbu · hbu
- **name** (13): whats your name · what is your name · whats ur name · ur name · your name · who are you · who r u · who are u · who is this · whos this · who dis · do i know you · have we met
- **coords** (16): coords · cords · ur coords · your coords · send coords · what are your coords · where are you · where r u · where are u · wya · where you at · where u at · location · where abouts · send location · drop coords
- **base** (20): where do you live · where do u live · where you live · where is your base · wheres your base · wheres ur base · where is ur base · your base · ur base · base coords · where is your house · wheres your house · wheres ur house · where is your home · wheres your home · where do you stay · show me your base · can i see your base · where is your town · wheres your town
- **seen_village** (14): village · villages · seen a village · seen any villages · know a village · any villages · any village · where is a village · wheres a village · wheres the village · where is the village · village nearby · villagers · any villagers
- **seen_diamonds** (11): diamonds · diamond · dias · seen diamonds · seen any diamonds · where are diamonds · wheres diamonds · found diamonds · found any diamonds · know where diamonds · any diamonds
- **seen_iron** (9): iron · seen iron · seen any iron · where is iron · wheres iron · found iron · found any iron · any iron · iron ore
- **seen_dungeon** (9): dungeon · dungeons · spawner · spawners · mob spawner · seen a dungeon · seen a spawner · any dungeons · any spawners
- **seen_portal** (7): portal · nether portal · seen a portal · any portals · where is the portal · wheres the portal · ruined portal
- **seen_fortress** (6): fortress · nether fortress · seen a fortress · where is the fortress · wheres the fortress · blazes
- **follow** (20): follow me · follow · come with me · come with · come along · lets go · lets go together · with me · folow me · follow pls · follow me pls · follow me please · come on · cmon · c mon · tag along · you coming · u coming · walk with me · lead the way
- **come** (13): come here · come to me · over here · get over here · c here · come over · come over here · here pls · to me · come here pls · come here please · where are you go · come back
- **stop** (20): stop following · stop following me · stay · stay here · wait here · stay there · wait there · go away · leave me alone · dont follow me · stop follow · quit following me · go home · you can go · u can go · bye for now · thats enough · thanks thats all · ill go alone · stop it
- **help** (21): help · help me · help pls · help please · pls help · plz help · halp · save me · im dying · i am dying · need help · i need help · someone help · anyone help · sos · mobs · theyre attacking me · im being attacked · backup · need backup · assist
- **team** (24): team up · wanna team · wanna team up · want to team up · lets team · lets team up · team with me · be my teammate · be my team · join me · join my team · duo · wanna duo · lets duo · wanna be friends · be my friend · friends · wanna play together · play together · partner · partners · be partners · join my group · wanna join
- **give** (29): can i have · can i get · can you give me · can u give me · could you give me · give me · gimme · got any · do you have · do u have · you have any · u have any · spare · any spare · can you spare · need some · i need · lend me · share · can you share · give pls · pls give · hand me · toss me · throw me · drop me · send me · could i have · may i have
- **trade** (18): trade · trades · wanna trade · want to trade · lets trade · trade me · trading · can we trade · you trading · u trading · buy · sell · selling · buying · whats for sale · what do you sell · shop · store
- **duel** (22): 1v1 · 1 v 1 · 1vs1 · 1v1 me · duel · duel me · fight me · wanna fight · wanna 1v1 · wanna duel · lets fight · lets 1v1 · lets duel · pvp · wanna pvp · spar · wanna spar · lets spar · square up · 1v1 rn · come fight · fight
- **compliment** (52): nice · cool · ur cool · youre cool · you are cool · u r cool · nice house · nice base · nice build · nice armor · nice gear · nice skin · nice sword · nice job · good job · well done · love you · ily · i like you · ur awesome · youre awesome · you are awesome · ur the best · youre the best · you are the best · goat · ur goated · goated · legend · ur a legend · based · pog · poggers · W · big W · huge W · ur nice · youre nice · ur sweet · ur funny · youre funny · sick · so cool · ur so cool · nice one · great job · amazing · ur pro · youre pro · pro · ur good · youre good
- **insult** (53): noob · nub · newb · n00b · trash · ur trash · youre trash · you are trash · ur bad · youre bad · you are bad · u suck · you suck · ur stupid · stupid · idiot · dumb · ur dumb · dummy · L · take the L · loser · ur a loser · bot · ur a bot · ratio · cringe · ur cringe · shut up · stfu · ez · too ez · garbage · clown · ur a clown · skill issue · hate you · i hate you · ugly · ur ugly · get good · git gud · bozo · ur mid · mid · go away loser · useless · ur useless · annoying · ur annoying · weirdo · freak · nerd
- **joke** (8): tell me a joke · tell a joke · joke · know any jokes · say something funny · make me laugh · got any jokes · funny
- **bot** (15): are you a bot · r u a bot · are u a bot · you a bot · u a bot · are you real · r u real · are you human · are you ai · are you an ai · is this a bot · are you a robot · npc · are you an npc · ur an npc

## Where they live

- Lines and answers: `src/main/resources/data/drifter/drifter_chat/` (`reactions.json`, `encounters.json`, `answers.json`, `requests.json`, `rounds.json`, `heard.json`); whims' lines in `drifter_whims/*.json`. This doc is generated from them.
- Code: `social/Lines.java` (loading, picking in a voice, reading answers), `social/ChatLines.java` (rate limits), `social/ChatReplies.java` (the rituals), `social/talk/PlayerTalk.java` (asking a Drifter), `social/talk/Telling.java` and `Heard.java` (telling it things; what it remembers), `encounter/*.java`.
