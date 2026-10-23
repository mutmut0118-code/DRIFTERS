# Every chat line a Drifter says

Everything Drifters say in chat, when they say it, and what you can say to them (generated from the data files; 946 lines in the chat data, 242 in whims, and 569 answer phrases they understand).

Lines come from `data/drifter/drifter_chat/*.json` (a datapack can add more: files add to each other) and the whims' own files. They're written the way players type in Minecraft chat (the user's guidebooks "How Minecraft Players Chat" and "The Science of Gamer Text Chat"): lowercase, no final period, clipped words (dias, obby, coords), rituals (o/, wb, rip, gg, gn, o7), and each personality its own voice. `/drifter config chat false` turns all of it off.

In the lines, `{player}` is your name, `{name}` the Drifter's own, `{who}` another's, `{way}` a direction, `{coords}` x y z, and the rest as each says.

## How it picks and how often it talks

- **Its voice.** A Drifter of a personality with lines of its own (newbie, builder, PvP sweat, bandit...) uses them about two times in three; otherwise everyone's.
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

You said yes (section 7).

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

## 6. Not chat, but close

- **Manhunt:** messages like "[Drifter] Bob respawned and is back on the hunt." are the mod's announcements.
- **Thoughts:** what a Drifter thinks (`/drifter thoughts`) never goes to chat.
- **The chronicle:** Drifter: Civilizations's morning news ("Day 4: ...") is the mod reporting.

## 7. What you can say to them

A message means whichever of these it has the longest phrase of: "no problem" is a yes though it has "no" in it, "hell no" a no, "yeah no" a no. Capitals, punctuation and stretched letters don't matter ("NOOOO." is no, "yesss" yes); apostrophes are dropped ("im" is "i'm"). The words can be anywhere in the message.

| Where | What it listens for |
| --- | --- |
| An escort, after it asks | **yes** or **ready** (it sets off), **no** (it goes alone), **unsure** (it asks again) |
| A race, after it challenges you | **ready** or **yes** (the countdown), **no**, **unsure** |
| A Drifter's name, or a part of it 4 letters or longer ("grumpy" or "baker" for xXGrumpybakerXx; not everyday words like "over" or "just"), within 32 blocks | It turns to look for you and may answer (huh) |
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

## Where they live

- Lines and answers: `src/main/resources/data/drifter/drifter_chat/` (`reactions.json`, `encounters.json`, `answers.json`); whims' lines in `drifter_whims/*.json`. This doc is generated from them.
- Code: `social/Lines.java` (loading, picking in a voice, reading answers), `social/ChatLines.java` (rate limits), `social/ChatReplies.java` (the rituals), `encounter/*.java`.
