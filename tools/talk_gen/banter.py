"""Writes drifter_talk/topics_gen_banter.json: discourse and insults, trolls and ragebaiters (side session, 2026-11-06; the
user: "discourse and insults, trolls and ragebaiters ... as detailed and in depth as possible").

- Roasts between friends: a roasts, b fires back, a's retort answers that very comeback (the comeback's "then"), b laughs
  or gives in. Roasts between rivals: the same shape but it gets real (disagree; the nastier ones rivalry). Roasts from
  what the game knows ({b.death}, its armor, its kills). Roasts that go too far (an apology, taken or not). Roast battles
  (3 parts).
- Trolls and ragebait: a baits; b bites and rages (pvp sweats, speedrunners and builders get heated) and maybe gets the
  "lmao got you"; or sees through it, or plays along and out-trolls. A newbie falls for it (the troll owns up, or
  doesn't); a hermit ignores it. A bandit's scams and a pvp sweat's callouts (they bait most).
- Discourse: accusations (b owns up or denies), callouts in town, debts and grudges, blame, apologies and making up
  (trust), and passive-aggressive small talk between two who aren't friends.

Game trash talk only: mild words (trash, bozo, noob, clown, L, skill issue, cope), nothing about real groups.
Run: python tools/talk_gen/banter.py src/main/resources/data/drifter/drifter_talk/topics_gen_banter.json
"""
import json
import random
import sys

out = sys.argv[1]
rng = random.Random(1180)
P = "g_banter_"
topics = []
ids = set()


def L(s):
    """Lines from a "|"-separated string."""
    return [x.strip() for x in s.split("|") if x.strip()]


def N(s):
    """Conditions from a space-separated string."""
    return s.split() if s else []


def Q(line, answers):
    """A line whose answer is one of these (the other's next turn says it)."""
    return {"line": line, "then": answers if isinstance(answers, list) else [answers]}


def PR(s):
    """Lines that each carry their own answer: "line >> answer | line >> answer" (answers split by " // ")."""
    out_ = []
    for x in L(s):
        line, answer = [y.strip() for y in x.split(">>")]
        out_.append(Q(line, [a.strip() for a in answer.split("//")]))
    return out_


def turn(by, lines, chance=None, cond=None, mark=None):
    d = {"by": by, "lines": lines}
    if chance is not None and chance < 1:
        d["chance"] = chance
    if cond:
        d["if"] = cond
    if mark:
        d["mark"] = mark
    return d


def opts(by, options, chance=None):
    """A turn with options: the first whose conditions hold. options: (conditions, lines[, mark])."""
    o = []
    for x in options:
        d = {}
        if x[0]:
            d["if"] = x[0]
        d["lines"] = x[1]
        if len(x) > 2 and x[2]:
            d["mark"] = x[2]
        o.append(d)
    d = {"by": by, "options": o}
    if chance is not None and chance < 1:
        d["chance"] = chance
    return d


def pick(pool, n):
    return rng.sample(pool, min(n, len(pool)))


def V(any_lines, voices, n=1, k=3):
    """Everyone's lines plus a few voices' own (k of them, n lines each)."""
    d = {"any": any_lines}
    for v in rng.sample(sorted(voices), min(k, len(voices))):
        d[v] = pick(voices[v], n)
    return d


def dedupe(xs):
    seen = []
    for x in xs:
        if x not in seen:
            seen.append(x)
    return seen


def add(id, about, tags, leads, turns, needs=None, weight=None, effects=None, next=None, follow_up=False):
    tid = P + id
    assert tid not in ids, tid
    ids.add(tid)
    t = {"id": tid}
    if weight is not None:
        t["weight"] = weight
    if needs:
        t["needs"] = needs
    t["tags"] = dedupe(tags)[:4]
    t["leads"] = dedupe(leads)[:4]
    t["about"] = about
    t["turns"] = turns
    if effects:
        t["effects"] = effects
    if next:
        t["next"] = [P + n for n in next]
    if follow_up:
        t["follow_up"] = True
    topics.append(t)


# ---------------------------------------------------------------------------------------------------------------------
# Shared reactions (generic, so they may repeat across topics).

FRIEND_END = L("lmao ok|ok that one hurt|i walked right into that|touche|rude. accurate, but rude|lol we're both trash|"
               "ok ok truce|fine, you win this one|i'm telling everyone you said that|you're lucky i like you|"
               "can't even argue with that|i'll remember this|brutal|ok i surrender|that's going in the book|LMAO stop|"
               "i hate that you're right|low blow but fair|bro chose violence today|i felt that one|"
               "someone get me a golden apple|ok i need a minute|you've been saving that one|"
               "how long were you sitting on that|dang ok|i'm laughing but i'm hurt|why are you like this lol|"
               "ok but you're not wrong")
FRIEND_END_V = {"newbie": L("hey! lol|that's mean lol|ok that was funny"),
                "pvp_sweat": L("ok you landed one|lucky hit|rematch later"),
                "builder": L("rude, but i'll fix it|ok noted, i'll redo it|fine, it needs work"),
                "hermit": L("hm.|fine.|noted."),
                "casual": L("lmaooo|bruh|ok ok lol"),
                "helper": L("lol fair, i'll do better|you're mean but funny"),
                "speedrunner": L("whatever, moving on|ok gg"),
                "hoarder": L("i'm keeping that one|rude. true though"),
                "homebody": L("rude, i love my place|ok but it's cozy"),
                "nomad": L("lol fair|i'll be gone tomorrow anyway"),
                "miner": L("fair, i'll dig a hole and hide|ok that one hit bedrock"),
                "bandit": L("noted. i'll get you back|watch your chests lol")}
FRIEND_CLOSE = L("love you though|this is why we're friends|anyway, same time tomorrow?|gg|ok i'm done lol|"
                 "you know i'm kidding|mostly kidding|ok i'll stop. for today|good talk|we're both bad, it's fine|"
                 "respect though|lol i missed this|ok ok, peace|still my favourite person to roast|"
                 "you'd say the same about me|anyway")
FRIEND_CLOSE_V = {"newbie": L("hehe|that was fun lol"), "pvp_sweat": L("gg ez|too easy"),
                  "helper": L("i'm only teasing, you know that|you're doing great, really"),
                  "hermit": L("enough.|ok. done."), "casual": L("lmaooo|anyway lol"),
                  "builder": L("anyway. fix your roof.|come by, i'll help you fix it"),
                  "speedrunner": L("ok back to it|gg, moving on"), "bandit": L("i'm watching you lol"),
                  "homebody": L("come over later, i'll make food")}

RIVAL_FINAL = L("whatever|say that again|nobody asked|cope|cry about it|you're one to talk|ok bozo|cute|"
                "projecting much?|keep talking, it's funny|that all you got?|L take|and yet you keep talking to me|"
                "go hug a cactus|stay pressed|imagine caring this much|you're not worth the hunger bar|"
                "go dig a hole and stay there|i've heard better from a zombie|clown|skill issue, yours")
RIVAL_FINAL_V = {"pvp_sweat": L("1v1 me and say that|you'd last two hits"),
                 "bandit": L("sleep with one eye open|nice chests you got. shame if"),
                 "hermit": L("go away.|done."), "hoarder": L("don't come near my chests"),
                 "speedrunner": L("wasting my time"), "newbie": L("that's so mean!|i'm telling everyone"),
                 "builder": L("at least my builds have taste"), "casual": L("lol ok|whatever bro"),
                 "helper": L("i don't know why you're like this"), "miner": L("i'll be underground, away from you")}
RIVAL_CLOSE = L("stay mad|L|bye bozo|noted. ignored|go cry in your dirt hut|not worth it|keep crying|"
                "whatever helps you sleep|sure buddy|clown|skill issue|i win, as usual|later, loser|"
                "that's what i thought|anyway, go away")
RIVAL_CLOSE_V = {"pvp_sweat": L("free|too easy|go touch a grass block"), "bandit": L("watch your back|keep your chests locked"),
                 "hermit": L("leave.|done talking."), "hoarder": L("and stay away from my stuff"),
                 "speedrunner": L("i'm leaving, you're slow"), "builder": L("go build something less ugly")}

# ---------------------------------------------------------------------------------------------------------------------
# 1. Roasts between friends: a roasts, b fires back, a's retort answers that comeback, b laughs or gives in.


def R(key, tag, about, needs, roasts, pairs):
    turns = [turn("a", L(roasts)),
             turn("b", PR(pairs)),
             turn("a", ["lol"]),
             turn("b", V(pick(FRIEND_END, 5), FRIEND_END_V), chance=0.8),
             turn("a", V(pick(FRIEND_CLOSE, 3), FRIEND_CLOSE_V, k=2), chance=0.4)]
    add("roast_" + key, about, ["roasts", "jokes", tag], [tag, "roasts", "jokes", "friends"], turns,
        needs=["friends"] + N(needs), weight=0.7)


# Houses and builds.
R("house_tumor", "building", "its house", "",
  "your house looks like a cobblestone tumor|did you build your house or did it just grow there|your base looks like a creeper hit it and you said good enough",
  "at least i have a house, you live in a hole >> a hole with a roof beats whatever that is|says the one with a dirt roof >> my dirt roof is still nicer than your lump|it's called rustic, look it up >> i looked it up. it said lump of cobble")
R("house_box", "building", "its box of a house", "",
  "your house is a box with a door on it|is your house a house or a storage crate|nice box. when's the house going up",
  "boxes are efficient >> efficient at looking sad maybe|a box keeps the rain off, that's the job >> so does a leaf, and it looks better|your house is the same box but uglier >> mine has a window. that's one more than yours")
R("dirt_hut", "building", "its dirt hut", "b:has_hut",
  "you still live in a dirt hut lol|your house is made of the stuff i walk on|cozy dirt hut you got there",
  "dirt is a building block, look it up >> so is gravel, doesn't mean you live in it|it's temporary >> temporary since day one|it's a starter home, ok >> a sheep tried to eat your starter home")
R("no_windows", "building", "its windowless house", "",
  "your house has zero windows, are you hiding something|how do you even see in there, no windows|your base looks like a prison with a door",
  "windows let the creepers look in >> the creepers already know where you live|glass costs sand and sand is far >> the beach is like forty blocks away|i like my privacy thanks >> privacy from the sun apparently")
R("leaning_tower", "building", "its tower", "",
  "your tower leans, i swear it leans|that tower is just a pillar with a dream|why is your tower one block wide",
  "it's a design choice >> a design choice to fall over|it's the view that matters >> the only view is the ground getting closer|you're just jealous of the height >> jealous of a pillar? sure")
R("torch_spam", "building", "all its torches", "",
  "you have like four hundred torches on your house|your base is brighter than the sun|you put a torch on every single block",
  "no mobs spawn though >> no sleep either, it's like noon in there|light is safety >> you can see it from the nether|better than your pitch black cave >> at least my cave doesn't hurt to look at")
R("ugly_colours", "building", "its house's colours", "",
  "your house is pink and green and orange, why|who taught you colours, a slime|your house looks like a parrot exploded",
  "it's vibrant >> vibrant like a warning sign|i used what i had >> you had every dye and used all of them|your house is all grey, that's worse >> grey is a colour. your house is all of them")
R("cobble_everything", "building", "its cobblestone builds", "",
  "everything you build is cobblestone|you've never heard of any block besides cobble huh|your whole base is one grey block",
  "cobble is free and creeper proof-ish >> free and boring, like your builds|it's a classic >> cobble isn't a style, it's a symptom|i'm saving the nice stuff >> saving it for what, your next life")
R("tiny_house", "building", "its tiny house", "b:has_house",
  "your house is so small you have to step outside to turn around|i've seen bigger chests than your house|how do you fit in your house",
  "cozy is a vibe >> your bed is half outside|less to light up >> one torch and you're done, huh|small but mine >> yours and the door's. the door takes a whole wall")
R("holey_bridge", "building", "its sky bridge", "",
  "your sky bridge has no railings, that's a death trap|who builds a bridge with holes in it|your bridge looks like a broken ladder",
  "railings are for cowards >> cowards who don't fall off, sure|i never fall >> you fell off it twice last week|it's minimalist >> the gaps aren't minimalist, they're missing")
R("door_wall", "building", "its door", "",
  "your door opens into a wall|you put your door on backwards again|who puts a door facing a cliff",
  "keeps the zombies confused >> it confused you more than the zombies|it's a security feature >> security from you getting home|works fine for me >> i watched you walk into it yesterday")
R("farm_living_room", "building", "the wheat in its house", "",
  "your house is half farm, half house, all mess|why is there wheat in your living room|you planted carrots in your kitchen floor",
  "fresh food every morning >> fresh rabbits too, they live there now|it's efficient >> efficient at being a barn with a bed in it|you're jealous of my indoor farm >> jealous of sleeping next to a cow? no")
R("unfinished", "building", "its unfinished build", "",
  "how long has your build been unfinished now|your castle is just one wall|is that a build or scaffolding with feelings",
  "art takes time >> at this rate the moon finishes it first|it's a work in progress >> the scaffolding has moss on it now|i'm adding the rest tomorrow >> you said tomorrow a week ago")
R("no_symmetry", "building", "its lopsided house", "",
  "nothing on your house is symmetrical|your windows are all different sizes|left side of your house and right side are strangers",
  "symmetry is boring >> boring and not ugly, i'll take it|it's got personality >> personality like a creeper crater|trees aren't symmetrical either >> trees aren't houses either")
R("lava_moat", "building", "its lava moat", "",
  "you put a lava moat around a dirt house|lava moat? to protect your dirt?|nice lava moat, shame about what it guards",
  "safety first >> nobody's coming to steal dirt|it's for the mobs >> it set your own fence on fire|you never know who's coming >> i know. nobody. for dirt")
R("glass_house", "building", "its glass house", "",
  "your house is all glass, i can see you eating|living in a glass box, bold|you know everyone can see you sleep right",
  "i like the light >> the zombies like the view|nothing to hide >> we all saw you dance at night|natural light is good for you >> walking into the glass isn't")
R("mole_base", "building", "its underground base", "",
  "you live underground like a mole|when did you last see the sun|your base is a hole with a bed",
  "underground is safe >> safe from the sun, your old enemy|the surface is overrated >> you came up yesterday and squinted at it|it's a bunker, not a hole >> a bunker with dirt walls is a hole")
R("empty_mansion", "building", "its empty mansion", "b:has_house",
  "your house is huge and there's nothing in it|ten rooms and one chest, nice|your base echoes",
  "room to grow >> room to echo|i'm minimalist >> ten rooms and one chest isn't a style|it's spacious >> i yelled in there and it yelled back")
R("zigzag_path", "building", "its path", "",
  "your path to your house zigzags for no reason|did a lost sheep lay your path|why does your path go through a pond",
  "it's scenic >> scenic until you fall in the pond|it follows the land >> the land doesn't go through a pond|i like the walk >> i got lost on it for a day")
R("fence_gap", "animals", "its fence", "",
  "your fence has a gap, every animal escaped|half your fence is missing|your animal pen is just a suggestion",
  "they come back >> the cows did not come back|freedom is important >> your pig is free in the next biome|the gap is a gate >> a gate swings, a gap just sits there")
R("bed_by_door", "home", "where its bed is", "",
  "your bed is right by the front door, why|who puts the bed by the front door|your bed is the first thing a zombie sees",
  "easy to reach >> easy for zombies too|i like the breeze >> the breeze and the zombie behind it|it's a power move >> power move until the zombie walks in")
R("copied_house", "building", "its copied house", "",
  "your house looks exactly like mine but worse|did you copy my house? badly?|you built my house from memory, didn't you",
  "yours was built off a village hut >> at least i copied a good one|copying is flattery >> flattery that leaks|mine has a better roof though >> you even copied my mistakes, and added more")
R("village_hut", "villagers", "the hut it moved into", "",
  "your house is just a village hut you moved into|did you kick a villager out to get that house|nice house, did the farmer move out",
  "he left willingly >> he's still standing outside, sad|it's called recycling >> recycling a guy's house while he watches|it was empty, i checked >> i saw him knock last night")
R("trapdoor_spam", "decor", "its trapdoors", "",
  "you used trapdoors on everything, why|your house is ninety percent trapdoors|calm down with the trapdoors",
  "trapdoors are detail >> i tripped on your detail in the hallway|they make it pop >> it pops like a trapdoor factory|you just can't see the vision >> the vision is trapdoors on trapdoors")
R("leaky_roof", "building", "its leaky roof", "",
  "your roof has holes, it rains inside|it rains in your kitchen, i saw|why do you stand in the rain inside your own house",
  "it's a skylight >> a skylight has glass|free water >> your bed is soaked|it's ventilation >> your crops grow on the floor now")
R("spawn_house", "building", "where it built", "",
  "you built your house right at spawn, so original|first spot you saw, first house you built huh|you didn't even walk ten blocks to build",
  "why walk far >> ten blocks is not far|location, location >> location: first spot you saw|i was tired >> you were tired on day one?")
R("ugly_statue", "decor", "its statue", "",
  "what is that statue by your house supposed to be|your statue looks like a melted creeper|the thing on your lawn scares me",
  "it's me >> if that's you, see a cleric|it's a dragon, obviously >> a dragon with three legs|art isn't for everyone >> the iron golem won't go near it")
R("moss_house", "building", "its mossy house", "",
  "your house is all moss, is it alive|you didn't build a house, you grew one|your walls are fuzzy",
  "nature takes over >> nature should take it faster|it's a cottage look >> a cottage of fuzz|moss is beautiful >> i swear it grew a block since yesterday")
R("portal_bedroom", "home", "the portal by its bed", "b:knows_nether",
  "your nether portal is in your bedroom|who puts a portal next to the bed|you sleep next to a portal, brave",
  "quick commute >> quick commute for the piglins into your bed|it's a night light >> enjoy the portal noise all night|it's purple, it matches >> matches the bags under your eyes")
R("wool_house", "building", "its wool house", "",
  "you built your house out of wool|your house is a fire hazard with a door|your house is made of sheep, basically",
  "it's soft >> soft and very flammable|it's colourful >> colourful until the lightning|it's cozy >> cozy until it's on fire")
R("bad_stairs", "building", "its stairs", "",
  "your stairs are all different heights|i tripped up your stairs twice|your staircase is a parkour course",
  "keeps you fit >> keeps me in bandages|you just walk badly >> i walk fine on stairs that are stairs|it's character >> character nearly broke my legs")
R("tree_house", "trees", "its tree house", "",
  "your tree house is three planks and a prayer|that's not a tree house, it's a nest|how do you get up your tree house, jump?",
  "it's got charm >> charm is not a support beam|ladders are for the weak >> the weak who don't fall out of trees|jumping is good for you >> falling isn't")
R("sand_house", "building", "its sand house", "",
  "you built your house out of sand, it'll fall|sand house? did the desert dare you|one wrong block and your house slides away",
  "it's stable if you're careful >> careful is not a foundation|it was all i had >> a whole desert and you picked the worst block|desert style >> desert style: slides away by morning")
R("crooked_walls", "building", "its crooked walls", "",
  "nothing in your house lines up|your walls are one block off everywhere|did you build that in the dark?",
  "it was night, so yeah >> and you never fixed it in the day|it's handmade >> the pigs are confused by your corners|close enough is good enough >> close enough is how houses fall down")
R("tour_guide", "home", "the tours of its house", "b:has_house",
  "you give everyone tours of your one room house|i've had three tours of your house now|you show off your house like it's a castle",
  "it's worth seeing >> it's one room, the tour is ten seconds|people love it >> people love leaving|it has charm >> charm of a cave")

# Gear, tools and weapons.
R("leather_armor", "armor", "its leather armor", "b:armor_leather",
  "nice leather armor, did a cow give it to you|leather armor, still? bold|you're dressed like a cow's cousin",
  "it's dyeable >> dyed leather is still leather|it's light >> light enough that arrows go right through|better than nothing >> a chicken could peck through it")
R("gold_armor", "armor", "its gold armor", "b:armor_gold",
  "gold armor? you know that's the weakest right|shiny and useless, like your sword arm|you look like a piglin's dream",
  "piglins love me >> the piglins only love the armor|it looks good >> good at breaking if you sneeze|style over stats >> style won't stop a skeleton")
R("no_armor", "armor", "having no armor", "b:armor_none",
  "where's your armor?|no armor? are you trying to die|you walk around with no armor like a baby zombie",
  "armor slows you down >> dying slows you down more|i dodge >> you dodge like a fence post|i'm saving up >> saving up since day one")
R("wooden_pick", "tools", "its wooden pickaxe", "",
  "you still mine with a wooden pickaxe?|that pickaxe is older than the world|your tools are made of sticks and hope",
  "it works >> i watched you take ten seconds on one block|stone is coming >> stone has been coming for days|it has sentimental value >> sentimental value doesn't mine iron")
R("broken_tools", "tools", "its worn out tools", "",
  "your sword is about to break, i can see the bar|how are all your tools in the red|you never repair anything huh",
  "it's got one more fight in it >> one more fight and then you punch zombies|repairing is for quitters >> quitters with working swords|i like living dangerously >> dangerously is one word for it")
R("no_shield", "weapons", "never using a shield", "",
  "you don't even use a shield lol|you know shields exist right|you take every arrow to the face",
  "shields are for cowards >> cowards who live, sure|i block with my face >> your face is not blocking well|i need that hand for food >> you need it for arrows, there's nineteen in you")
R("four_swords", "weapons", "all its swords", "",
  "why do you carry four swords|you have more swords than skills|four swords and you still lose to spiders",
  "backups >> backups for losing faster|one for each mob >> one for each loss|you never know >> you do know. you lose")
R("bane_sword", "weapons", "its bad enchant", "b:knows_enchanting",
  "your sword has bane of arthropods, lol|who puts bane on their only sword|you got a bad enchant and kept it",
  "spiders fear me >> spiders still win though|it was cheap >> cheap like your sword skills|it's better than nothing >> enjoy one shotting silverfish")
R("fishing_rod_fight", "weapons", "fighting with a fishing rod", "",
  "why are you holding a fishing rod in a fight|you brought a fishing rod to a cave|fishing rod as a weapon, bold choice",
  "it pulls mobs in >> it pulled the creeper right to you|it's a strategy >> a strategy of dying slowly|i was fishing first >> and the creeper got hooked. so did you")
R("iron_unused", "armor", "the iron it won't use", "",
  "you have iron and you're still in leather|craft some armor already|you're sitting on iron like a dragon on gold",
  "i'm saving it >> saving it for your funeral|i need it for tools >> your tools are stone though|armor is next >> next has been next for days")
R("bad_aim", "weapons", "its aim", "",
  "you missed that skeleton six times|your arrows are scattered across three biomes|you couldn't hit a wall with that bow",
  "the wind took it >> there's no wind down there|it was moving >> the skeleton was standing still laughing|i'm warming up >> warming up for a week")
R("torch_weapon", "weapons", "fighting with a torch", "",
  "why do you swing torches at zombies|you hit a zombie with a torch?|your weapon of choice is a torch",
  "fire damage >> a torch doesn't do fire damage|it's light and pain >> it's just light and you dying|it works, trust >> the zombie looked confused, that's it")
R("gold_sword", "weapons", "its gold sword", "",
  "a gold sword, really?|that gold sword breaks if you look at it|nice gold sword, it'll last two zombies",
  "it's fast >> fast at breaking|it looks cool >> looks cool in pieces|piglins respect it >> the piglins are laughing")
R("diamond_hoe", "tools", "its diamond hoe", "",
  "you used diamonds on a hoe|a diamond hoe, of all things|you had diamonds and made a hoe",
  "farming is serious >> your sword is still stone though|the hoe deserves it >> the wheat doesn't care what hits it|crops are life >> priorities of a chicken")
R("armor_mismatch", "armor", "its mismatched armor", "",
  "your armor is four different materials|leather boots and an iron helmet, who dressed you|you look like a scarecrow made of loot",
  "it's a look >> a look that says help me|i wear what i find >> you find a lot of junk|mix and match >> the zombies dress better")
R("dyed_armor", "armor", "its dyed armor", "",
  "pink leather armor, really|you dyed your armor bright green|you look like a slime in a coat",
  "fashion >> fashion for skeletons to aim at|you can see me from far away >> so can every skeleton|it's my colour >> it's everyone's target colour now")
R("axe_fighter", "weapons", "fighting with an axe", "",
  "you fight with an axe like a lumberjack|you treat every mob like a tree|swinging an axe at a spider, nice",
  "axes hit harder >> harder and slower|it breaks shields >> the spider doesn't have a shield|it's an all in one >> all in one, all the misses")
R("elytra_crash", "gear", "its elytra flying", "b:knows_elytra",
  "you crash your elytra into every wall|i saw you fly into a mountain|your elytra spends more time broken than flying",
  "the mountain moved >> mountains don't move, you do, into them|i was testing the brakes >> there are no brakes|it's a landing strategy >> the strategy is called falling")
R("stone_sword", "weapons", "its stone sword", "",
  "still using a stone sword, huh|your sword is literally a rock on a stick|you know iron swords exist right",
  "stone gets the job done >> the job being dying slowly|it's reliable >> reliable at losing|iron is for the pickaxe >> you have iron, i saw it")
R("helmet_only", "armor", "its lone helmet", "",
  "you're wearing just a helmet, why|only a helmet? protecting the empty part?|that helmet is doing a lot of work for nothing",
  "head safety first >> what's in there to protect|the helmet's the most important >> nice hat, shame about the rest|i'm building up >> building up since forever")
R("pumpkin_head", "gear", "the pumpkin on its head", "",
  "why are you wearing a pumpkin on your head|you look like a snow golem's cousin|that pumpkin isn't fooling anyone",
  "endermen leave me alone >> endermen leave you alone out of pity|it's spooky >> spooky like a scarecrow|it's a fashion statement >> honestly the pumpkin is an upgrade")
R("pyro", "tools", "its flint and steel", "",
  "you light everything with flint and steel, pyro|why do you carry flint and steel everywhere|you set a forest on fire for fun",
  "it's useful >> useful at burning your own house|portals need it >> the forest didn't need it|that fire was an accident >> three accidents is a habit")
R("lava_bucket", "tools", "its lava bucket", "",
  "you carry a lava bucket like it's a snack|one wrong click and you burn the house|i've seen you pour lava on your own feet",
  "it's a weapon >> the weapon is pointed at you|i'm careful >> careful people don't have burn marks|that was one time >> one time per day")
R("junk_hotbar", "gear", "its hotbar", "",
  "your hotbar is a stick, a seed and dirt|what's even in your hotbar|you're carrying nothing useful again",
  "i travel light >> light like your head|the dirt is useful >> dirt for your next hut|seeds are life >> plant yourself somewhere")
R("netherite_flex", "armor", "its netherite", "b:armor_netherite",
  "oh look who's in netherite now|you wear netherite to pick flowers|all that armor and you still scream at spiders",
  "it's called being prepared >> prepared for flowers to attack|jealous >> i saw you run from a chicken in it|earned it >> earned it and still died to fall damage")

# Deaths.
R("baby_zombie", "death", "dying to a baby zombie", "",
  "you died to a baby zombie lol|a baby zombie took you out, legend|remember when the baby zombie got you",
  "it was fast ok >> it was riding a chicken and still won|those things are cracked >> cracked and two blocks tall|you'd die too, they're tiny >> tiny and still beat you")
R("ladder_fall", "death", "falling off a ladder", "",
  "you died to fall damage in a cave you made|you fell off your own roof|who dies falling off a ladder",
  "the ladder betrayed me >> it's your ladder, you built it|gravity was rude >> gravity doesn't play favorites|it was taller than it looked >> it was three blocks")
R("lava_dig", "death", "falling in lava", "",
  "you went swimming in lava again?|you mined straight down into lava, classic|how many times have you fallen in lava this week",
  "it was dark >> lava glows. it's literally a light|the lava came out of nowhere >> lava doesn't sneak up on you|i was distracted >> you're distracted every day")
R("creeper_hug", "death", "the creeper that got it", "",
  "you let a creeper hug you again|you just stood there and let the creeper go off|creeper walked up and you said hi",
  "i didn't hear it >> it hissed. loudly|it was silent >> creepers don't do silent, you do deaf|it came from behind >> every creeper comes from behind you, weird")
R("puddle_drown", "death", "drowning", "",
  "you drowned in a puddle|you drowned two blocks from the shore|how do you drown in a river",
  "it was deep >> two blocks deep|i forgot to swim up >> swimming up is the whole skill|the current took me >> the current was standing still")
R("chicken_fight", "death", "losing to a chicken", "",
  "a chicken almost killed you|you lost a fight to a chicken|i saw you take damage chasing a chicken. how",
  "it was a jockey >> it was a normal chicken, i was there|i fell, not the chicken >> you fell running after it, that counts|that chicken was strong >> it laid an egg on your grave")
R("sand_buried", "death", "getting buried in sand", "",
  "you got buried by sand|sand fell on you and that was it|you dug up and the sand won",
  "never dig up >> you know that and still did|it was gravel >> gravel, sand, same fate|it was a trap >> you dug into it, you set the trap")
R("starved", "death", "starving", "",
  "you starved with food in your inventory|how do you starve next to a farm|you forgot to eat. again",
  "i was busy >> busy starving|i was saving it >> saving it for your next life|food is for later >> later never came")
R("cactus_death", "death", "the cactus", "",
  "a cactus killed you|you hugged a cactus to death, yours|death by cactus is rough",
  "it was dark >> it's a cactus, it doesn't move in the dark|i was knocked into it >> knocked into it by a rabbit|cacti are sneaky >> it's a plant")
R("bed_explode", "death", "its exploding bed", "b:knows_nether",
  "you tried to sleep in the nether lmao|you blew yourself up with a bed|bed in the nether, you didn't know?",
  "nobody told me >> the bed told you. loudly|i was tired >> tired forever now|i thought it'd work >> it blew a hole in the whole floor")
R("own_tnt", "death", "its own tnt", "",
  "you blew yourself up with your own tnt|lit the tnt and just stood there|your tnt mining killed you",
  "the fuse was short >> the fuse is always four seconds|i thought i was further >> further from your brain|it was a test >> test failed")
R("skeleton_snipe", "death", "the skeleton that shot it", "",
  "a skeleton sniped you from across a ravine|you got shot off a cliff by a skeleton|skeleton aim beat your armor",
  "aimbot skeleton >> it was one skeleton, i counted|i had no cover >> cover is called a block|it was three skeletons >> three is not that many")
R("bees", "death", "the bees", "",
  "you got killed by bees, how|you hit a bee and the whole hive came|bees took you out",
  "they swarmed >> you punched their house|i just wanted honey >> wanted honey, got stung|bees are scary >> bees die after one sting, you died first")
R("void_fall", "death", "falling into the void", "b:knows_end",
  "you fell into the void|you bridged out into nothing|you walked off the edge of the end",
  "the edge was dark >> the whole end is dark|i was looking at the sky >> you looked at the sky and walked off|an enderman pushed me >> endermen don't push, they stare")
R("golem_clap", "death", "the iron golem", "",
  "you got clapped by an iron golem|you hit a villager and the golem sent you flying|iron golem one, you zero",
  "the villager started it >> the villager was selling bread|it was an accident >> you swung first|golems are overpowered >> it threw you to the moon")
R("wolf_pack", "death", "the wolves", "",
  "you got eaten by the wolves you hit by accident|you punched a wolf and the pack got you|death by wolf pack, brutal",
  "i was aiming at a sheep >> the sheep got away too|they ganged up >> you started a war with dogs|wolves are unfair >> you hit the one with the angry eyes")
R("warden_wake", "death", "waking the warden", "b:knows_warden",
  "you woke the warden and screamed|you sneaked so loud the warden came|you threw a snowball in the deep dark?",
  "i sneezed >> you sneezed for three minutes|it was a sculk sensor >> you stepped on twenty sensors|i didn't know >> the warden knows your name now")
R("phantoms", "death", "the phantoms", "",
  "phantoms got you because you never sleep|three phantoms and you're done, nice|you're the reason phantoms exist",
  "sleep is overrated >> the phantoms disagree|i was busy >> busy not sleeping|they came out of nowhere >> they came out of the sky, like always")
R("pufferfish", "food", "eating a pufferfish", "",
  "you ate a pufferfish|poison from a pufferfish, chef's kiss|why would you eat that",
  "i was hungry >> it's got spikes|it looked tasty >> tasty like poison|food is food >> food that hurts you is not food")
R("witch_loss", "death", "the witch", "",
  "a witch beat you with potions|you lost a potion fight to a witch|a witch threw two bottles and you folded",
  "she healed >> she healed because you tickled her|witches cheat >> they're just potions, you had a sword|she had like six potions >> six potions vs a sword and you lost")
R("ghast_ball", "death", "the ghast's fireball", "b:knows_nether",
  "you got hit by a ghast fireball you tried to punch|you swung at the fireball and missed|a ghast knocked you into lava",
  "timing is hard >> you swung at nothing|the ghast was far >> far from your sword|it was two ghasts >> two ghasts, zero hits")
R("silverfish", "death", "the silverfish", "",
  "silverfish swarmed you lol|you mined a stronghold block and the fish got you|death by silverfish, wow",
  "they were everywhere >> they're tiny|it was a spawner >> you mined the spawner block|they're so fast >> they're tiny and you're slow")
R("campfire", "death", "standing on a campfire", "",
  "you walked onto a campfire|you died standing on a campfire|campfire's for cooking, not you",
  "i didn't see it >> it's on fire, you see it|i was cooking >> cooking yourself|it was hidden >> hidden in plain sight")
R("pillagers", "death", "the pillagers", "",
  "pillagers turned you into a pincushion|you walked into a pillager outpost with no armor|you charged a raid alone",
  "i was brave >> brave and dead|i wanted the loot >> the loot is still there|they had numbers >> they had crossbows, you had hope")
R("spider_loss", "death", "losing to a spider", "",
  "a spider got you right outside your own house|spider climbed your wall and you panicked|you lost to one spider",
  "it was a cave spider >> it was a normal spider|it jumped >> it jumped once|spiders are creepy >> it was more scared of you")
R("enderman_stare", "death", "staring at an enderman", "",
  "you stared at an enderman. on purpose?|you looked an enderman in the eye and lost|why would you look at it",
  "i forgot >> forgot your whole life|it was right there >> so was the ground. look at that|i wanted to see >> you saw. then you died")
R("magma_feet", "death", "the magma blocks", "",
  "you walked on magma blocks|you sprinted across magma like it was carpet|your feet are toast",
  "i didn't sneak >> sneak next time|it looked like stone >> stone doesn't glow|it was dark >> it glows, it's orange")
R("ravine_night", "death", "the ravine", "",
  "you walked into a ravine at night|ravine got you again|you sprinted off a ravine edge",
  "it was dark >> you had torches|my torches were out >> you had a stack, i saw|i was chasing a sheep >> the sheep lived")
R("boat_sink", "death", "jumping out of its boat", "",
  "you jumped out of your boat in the middle of the ocean|how do you drown with a boat right there|you lost your boat at sea",
  "i dropped it >> how do you drop a boat|it drifted >> it drifted right past you|i panicked >> panicked in a boat")
R("died_with_loot", "death", "losing its loot", "",
  "you died with a full inventory and lost it all|you had diamonds and died before you got home|you always die on the way home",
  "i got some back >> some is not all|it's a curse >> cursed by bad decisions|the despawn timer hates me >> five minutes, you had five minutes")

# Skills.
R("pvp_clicking", "fighting", "how it fights", "",
  "your fighting is all clicking and praying|you swing like you're swatting flies|you fight like a confused villager",
  "it works >> works against chickens|i'm unpredictable >> unpredictable even to yourself|you fight worse >> i've never missed a zombie standing still")
R("parkour", "jokes", "its jumping", "",
  "you can't even jump two blocks|you missed a one block gap|your parkour is falling with style",
  "my feet slipped >> slipped on regular grass|it was ice >> it was dirt|i was off balance >> you're off balance standing still")
R("slow_mining", "mining", "how slowly it mines", "",
  "you mine like you're scared of the rock|how is it taking you that long to mine stone|watching you mine is like watching wheat grow",
  "i'm careful >> careful at being slow|i'm looking for ore >> the ore's waiting|my pickaxe is tired >> your pickaxe is fine, you're tired")
R("lost_home", "exploring", "getting lost", "",
  "you got lost walking home again|how do you get lost in your own base|you need a map to find your bed",
  "everything looks the same >> it's your house|the trees moved >> trees don't move|i was exploring >> you explored your own hallway")
R("brick_swim", "ocean", "how it swims", "",
  "you swim like a brick|i saw you sink in a puddle|your swimming is just slow drowning",
  "i was diving >> diving straight to the bottom|swimming is hard >> water is water|the water was weird >> you sank, it was a pond")
R("boat_steer", "ocean", "how it steers a boat", "",
  "you crash your boat into every shore|you can't steer a boat|your boat goes everywhere but straight",
  "boats are slippery >> boats are on water, that's the point|the river curves >> you hit a lily pad and gave up|i was sightseeing >> sightseeing the bottom of the river")
R("slow_craft", "crafting", "how slowly it crafts", "",
  "you take forever to craft a table|you need a recipe book for sticks|how do you mess up a crafting table",
  "i was thinking >> thinking hurts, i get it|planks are tricky >> planks are one log|i like to be sure >> you made buttons instead")
R("trample", "farming", "trampling its crops", "",
  "you trample your own crops every day|you jump on your farmland like a rabbit|your wheat field is just dirt now",
  "i was in a hurry >> in a hurry to wreck it|the crops were in the way >> the crops are the farm|farmland is fragile >> fragile like your skills")
R("slow_build", "building", "how slowly it builds", "",
  "you place blocks like each one costs you|ten minutes for one wall|you build slower than a turtle walks",
  "quality takes time >> the quality isn't there either|i measure twice >> you measure twice, place wrong once|it's precision >> the turtle finished its house first")
R("pig_chase", "animals", "chasing a pig", "",
  "you chased a pig for ten minutes|a cow outran you|you can't catch a chicken",
  "the pig was fast >> pigs aren't fast|it went in the water >> it was a puddle|i let it go >> you let it go because it won")
R("no_dodge", "fighting", "how it dodges", "",
  "you dodge like a fence post|everything hits you|you walk into every arrow",
  "i tank hits >> you tank them with your face|dodging is for the weak >> weak and alive beats strong and dead|i'm a magnet for damage >> a magnet for respawns")
R("loud_sneak", "caves", "how it sneaks", "",
  "you sneak with all the stealth of a goat|you try to sneak and it sounds like a stampede|the whole cave knew you were coming",
  "goats are stealthy >> goats ram things, you ram walls|i wanted them to know >> they knew and they laughed|stealth is boring >> boring and alive though")
R("raw_chicken", "food", "its cooking", "",
  "you burned the steak, how, it's a furnace|your food is always raw|you ate raw chicken again?",
  "i was hungry >> raw chicken makes you hungrier, enjoy|it's faster >> fine until your stomach says no|raw is fine >> it's one furnace")
R("bad_trader", "trade", "its trading", "",
  "you paid ten emeralds for one apple|the villagers see you coming|you trade like the villagers are robbing you. they are",
  "he looked hungry >> the villager retired off your emeralds|i like that villager >> he likes your emeralds|it was a good apple >> apples grow on trees")
R("pearl_aim", "jokes", "its ender pearls", "",
  "you threw your pearl into a wall|you pearled yourself into lava|your ender pearls go everywhere but where you aim",
  "it bounced >> pearls don't bounce|the angle was weird >> the angle was straight|i meant to >> you meant to land in lava?")
R("floating_trees", "trees", "the trees it leaves", "",
  "you leave floating trees everywhere|the forest is full of your half trees|you never finish chopping a tree",
  "the top's too high >> you have legs, jump|the leaves will fall >> leaves fall, logs don't|it's art >> art that annoys everyone")
R("redstone_door", "redstone", "its redstone", "b:knows_redstone",
  "your redstone door opens itself at night|your piston door eats people|your redstone is a fire hazard",
  "it's a feature >> a feature that ate a sheep|it's working as intended >> intended to fail|you wouldn't understand >> i understand it eats people")
R("missed_sleep", "sleep", "missing bedtime", "",
  "you can't even sleep on time|you miss the sleep window every night|you're always awake when the zombies come",
  "i'm a night person >> the zombies are night people too|sleep is for the weak >> weak people sleep, you die|i like the moon >> the moon doesn't like you back")
R("shield_late", "weapons", "its shield timing", "",
  "you raise your shield after the hit|your shield timing is a joke|you block the air after the arrow lands",
  "it's anticipation >> anticipating the last hit|i'm practicing >> practice more, block less air|the arrow was fast >> arrows are slow, you're slower")
R("empty_mob_farm", "mobs", "its mob farm", "",
  "you built a mob farm that farms you|your mob farm has zero mobs in it|your mob farm is just a dark room with you in it",
  "it's a work in progress >> in progress since forever|they'll come >> they came for you|i'm the bait >> bait that dies")
R("lost_chest", "storage", "the chest it forgot", "",
  "you forgot where you put your diamonds again|you have a chest you forgot exists|how do you lose a whole chest",
  "i have a lot of chests >> a lot of chests, zero labels|it's hidden >> hidden from you too|i'll find it >> the chest is lonely")
R("bucket_clutch", "jokes", "its water bucket clutch", "",
  "you missed the water bucket clutch again|you try the bucket clutch every time and splat|your water bucket timing is sad",
  "i got it once >> once in a hundred|almost >> almost is a respawn screen|the bucket was slow >> the bucket's fine")
R("scared_of_bats", "caves", "panicking at bats", "",
  "you yell every time a bat flies past|you scream at bats|you panic at a bat",
  "bats are loud >> bats are harmless|they're startling >> a bat made you drop your sword|i was surprised >> surprised daily")
R("boot_fishing", "fishing", "its fishing luck", "",
  "you fish for an hour and get boots|you only fish up junk|your fishing rod is a boot magnet",
  "boots are loot >> loot for a zombie|i'm waiting for the book >> the book's ignoring you|it's relaxing >> relaxing and useless")
R("scared_bridge", "building", "how it bridges", "",
  "your bridges look like a staircase that gave up|you bridge like a scared turtle|you sneak bridge everything",
  "safety first >> scared turtles build better|i'm careful >> careful for an hour per block|it holds >> it holds, your pride doesn't")
R("always_runs", "fighting", "running from fights", "",
  "you run from every fight|you sprint away from zombies like they're dragons|your fight plan is just running",
  "running is a strategy >> the strategy of a chicken|i pick my battles >> your battles get picked by zombies|i'm still alive >> alive and scared")
R("bad_brewer", "potions", "its brewing", "b:knows_brewing",
  "you brewed a potion of weakness and drank it|you mix potions like a confused witch|your brewing stand is cursed",
  "it was an experiment >> the experiment is you|i labeled it wrong >> labels need writing|it's science >> science says stop")
R("map_lost", "exploring", "its maps", "",
  "your maps are all ocean|you made a map of the inside of your house|you got lost holding a map",
  "the ocean's big >> the map was fine|i was testing >> testing being lost|the map was wrong >> you held it upside down")
R("armor_at_home", "armor", "the armor it leaves at home", "",
  "you made armor and left it in a chest|armor doesn't work in a chest, you know|you fight in your shirt and your armor's at home",
  "it's safe there >> safe and useless|i'm saving durability >> durability for your grave|i forgot >> you forget everything")
R("five_torches", "caves", "how it goes caving", "",
  "you go caving with five torches|you go into caves with no food|you cave like you want to get lost",
  "i like the dark >> the dark likes you more|torches are heavy >> torches weigh nothing|i find my way >> you found your way back with my help")
R("animal_overflow", "animals", "its overcrowded pen", "",
  "you bred so many cows i can't see the grass|you have forty chickens and no eggs|your animal pen is wall to wall sheep",
  "i like animals >> forty chickens is a cult|they're my family >> your family is food|more food >> the sheep can't move")
R("never_sprints", "jokes", "how slowly it walks", "",
  "you walk everywhere, never sprint|you're the slowest walker i know|turtles move faster than you",
  "i'm saving food >> saving food to be slow|i like the scenery >> scenery of being late|why rush >> turtles rush more")
R("lucky_vein", "diamonds", "its lucky diamonds", "",
  "you found diamonds by accident and called it skill|you tripped into a diamond vein, admit it|lucky isn't a skill",
  "i'm talented >> talented at falling into holes|it's called a nose for ore >> your nose was in the dirt|jealous >> you fell into that vein")
R("never_finishes", "building", "its unfinished projects", "",
  "you start a hundred projects and finish zero|how many half built things do you have|your base is a museum of unfinished stuff",
  "i'm creative >> creative at quitting|i'll finish them >> you'll finish none|i like starting >> starting is half the fun, and all of yours")
R("shot_villager", "villagers", "shooting a villager", "",
  "you shot a villager by accident|you hit the farmer with an arrow|the villagers duck when you draw your bow",
  "he walked in front >> he walked in front of your miss|it was an accident >> the farmer still flinches|he's fine >> they hide when you visit")

# Farms, food and animals.
R("tiny_farm", "farming", "its tiny farm", "",
  "your wheat farm is four blocks|that's not a farm, that's a flower pot|you call that a farm?",
  "it's a start >> a start since day two|quality over quantity >> quality wheat, still four blocks|it feeds me >> it feeds one chicken")
R("carrot_diet", "food", "its carrot diet", "",
  "you only eat carrots, are you a rabbit|carrots every meal, every day|i've never seen you eat anything but carrots",
  "carrots are good for you >> you're half rabbit at this point|they're easy >> easy and boring|they help me see in caves >> they don't help you see")
R("rotten_flesh", "food", "eating rotten flesh", "",
  "you eat rotten flesh like it's a snack|why do you eat zombie bits|you eat what the zombies drop, gross",
  "food is food >> the zombies want it back|it's free >> free tummy ache|i like the risk >> a risk of hunger every time")
R("named_cow", "pets", "naming its animals", "",
  "you named your cow, didn't you|you name every animal you own|you have a sheep called mr fluffy",
  "they deserve names >> you ate the last one you named|he likes his name >> he doesn't know his name|they're family >> your family moos")
R("dog_carries", "pets", "its dog", "",
  "your dog has more kills than you|your wolf carries you in fights|your dog is the real fighter",
  "teamwork >> the team is the dog|we're a team >> you're the dog's sidekick|he learned from me >> he learned to fight without you")
R("cat_boss", "pets", "its cat", "",
  "your cat sits on your chest all day and you let it|you can't open your chest cause the cat's sitting on it|your cat owns you",
  "she's comfy >> she rules your storage|i don't want to disturb her >> you went hungry for a cat|cats rule >> you're the cat's servant")
R("melon_diet", "food", "its melon diet", "",
  "you have a melon farm and eat melons only|melons all day, you're half melon|your melon farm is a mess of stems",
  "melons are sweet >> sweet and useless|they're fast to grow >> fast to grow, slow to fill you|stems are pretty >> stems are not pretty")
R("bread_diet", "food", "its bread diet", "",
  "you eat bread like it's a feast|bread again? you live like a villager|your whole diet is wheat",
  "bread is life >> you're one hay bale from becoming a villager|it's reliable >> reliable like rain|villagers know good food >> villagers also run from zombies")
R("cane_stubs", "farming", "its sugar cane", "",
  "your sugar cane farm is all stubs|you harvest the cane from the bottom, every time|you break the whole stalk, why",
  "i want it all >> all of nothing|it grows back >> it grows back slower like that|i'm in a hurry >> in a hurry to wait")
R("chicken_chaos", "animals", "its chicken coop", "",
  "your chicken coop is chicken chaos|the chickens escaped again|your coop is just feathers",
  "they're free range >> free range into the river|they'll come back >> they're in another biome|chickens are wild at heart >> wild like your farm")
R("cows_inside", "animals", "the cows in its house", "",
  "you keep cows in your house|there's a cow in your bedroom|you sleep next to cows",
  "they keep me warm >> warm and moo|it's safer >> safer for the cows maybe|they wanted in >> they want out")
R("horse_hates", "animals", "its horse", "",
  "you can't ride your horse in a straight line|your horse throws you off every time|you named your horse and it still hates you",
  "he's spirited >> spirited away from you|he's learning >> he's learning to run away|i'm learning >> neither of you is learning")
R("potato_chests", "food", "its potatoes", "",
  "you've got three chests of potatoes|why do you need that many potatoes|potato person",
  "potatoes are versatile >> you only bake them|i'm prepared >> prepared for a potato emergency?|baked potatoes are elite >> elite at being potatoes")
R("raw_cod", "food", "eating raw fish", "",
  "you eat fish raw like a polar bear|you just eat raw cod|fish every day, you're half drowned",
  "fishing is cheap >> cheap and raw|cod is fine >> you smell like a river|polar bears are cool >> the bear would cook it")
R("never_food", "food", "never carrying food", "",
  "you never have food on you|you always beg for food|you're always hungry",
  "i forget >> you forget to eat and then cry|food finds me >> it finds you through me|i'm lean >> lean like a stick")
R("one_pie", "food", "its pumpkin pie", "",
  "you made one pumpkin pie and won't stop bragging|you think one pie makes you a chef|pie again?",
  "it's a good pie >> it's sugar and egg, relax|it took effort >> effort of three clicks|you've never baked >> i've baked, you burn things")
R("golden_carrot_snob", "food", "its golden carrots", "",
  "you wasted gold on carrots|golden carrots? fancy|you eat gold now",
  "best food in the game >> best food, worst budget|the saturation is real >> real and expensive|it's an investment >> an investment in your stomach")
R("rainbow_sheep", "animals", "its rainbow sheep", "",
  "your sheep are every colour, it hurts|you dyed your sheep rainbow|your sheep pen looks like a parrot party",
  "rainbow wool is useful >> useful for clown armor|they're happy >> they look confused|it's pretty >> pretty loud")
R("bee_war", "animals", "its bee farm", "",
  "you got stung setting up your bee farm|your beehive is a war zone|the bees hate you",
  "honey is worth it >> worth it until the swarm|they'll warm up to me >> warm up and sting|i'm the beekeeper >> the keeper got stung")
R("pig_rider", "animals", "riding a pig", "",
  "you ride a pig like it's a horse|you spent an hour on a pig|carrot on a stick, really",
  "pigs are underrated >> underrated and slow as a pebble|it's fun >> fun for a minute|he's fast enough >> the pig wants out")
R("llama_spit", "animals", "the llama", "",
  "a llama spat on you and you cried|llamas hate you|you got spit on by a trader's llama",
  "it was a surprise >> the llama saw you coming|i didn't cry >> you sniffled|they're rude >> rude like you")
R("turtle_eggs", "animals", "the turtle eggs", "",
  "you broke turtle eggs walking on the beach|turtles fear you|you stomped the turtle beach",
  "i didn't see them >> white eggs on sand, invisible, sure|they were in the sand >> the turtles have a list|it was an accident >> an accident three times")
R("fox_thief", "animals", "the fox that robbed it", "",
  "a fox stole your sword|you got robbed by a fox|the fox took your food and left",
  "it was quick >> quick as a fox, you're not|foxes are sneaky >> sneaky and smart, unlike you|i let it >> you let it after chasing it for ten minutes")
R("axolotl_bucket", "pets", "its axolotl", "",
  "you carry axolotls in buckets everywhere|you love your axolotl more than people|you named your bucket",
  "they're cute >> cute doesn't fight|he's my friend >> your friend lives in a bucket|they help in fights >> help in fights you're losing")
R("parrot", "pets", "its parrot", "",
  "your parrot copies the creeper hiss and you still flinch|your parrot is smarter than you|the parrot fell off your shoulder again",
  "she's practicing >> practicing scaring you|she's loyal >> loyal to the seeds|we're bonding >> you bonded by falling")

# Names.
R("name_pig", "jokes", "its name", "",
  "your name would be a great name for a pig|i'm naming my pig after you|i named a pig after you, he's smarter",
  "honored >> the pig is offended|that pig's lucky >> the pig won't answer to it|at least the pig's famous >> famous for being smarter than you")
R("name_forget", "jokes", "its name", "",
  "i forget your name every single time|i call you the one with the dirt hut|what's your name again? kidding. mostly",
  "rude >> i'll just call you bozo|you know my name >> i know it, i just don't use it|i'll remember yours forever, sadly >> sadly is right")
R("name_sneeze", "jokes", "its name", "",
  "your name sounds like a sneeze|your name sounds like a slime landing|say your name out loud, it's a cough",
  "bless you >> bless me? bless you, you need it|it's a strong name >> a strong cough|yours sounds like a bat >> bats are cooler than you")
R("name_hmm", "villagers", "its name", "",
  "villagers can't even say your name|the villagers just go hmm when you walk by|they say hmm because they forgot your name",
  "they say hmm to everyone >> they say a sadder hmm for you|they love me >> love is a strong word for that hmm|they say hmm at you too >> mine is a happy hmm")
R("name_grave", "death", "its name on a grave", "",
  "i'll put your name on a grave sign for you, save time|i've got a sign ready with your name for next time|your name looks good on a grave",
  "i'll make one for you >> i'll write mine in gold|i'm not dying today >> you said that yesterday|morbid >> i'm just prepared")
R("name_famous", "town", "its fame", "",
  "everyone around here knows your name. for the wrong reasons|your name came up at the well, not in a good way|people say your name and laugh",
  "famous is famous >> famous for falling in lava|they're jealous >> jealous of what|any fame is good fame >> the fame of the village fool")
R("name_new", "jokes", "a new name for it", "",
  "you should change your name to lava swimmer|new name for you: creeper bait|i'm calling you baby zombie snack now",
  "that's a cool name actually >> it suits you|please no >> too late, it's spreading|i'll call you dirt roof then >> dirt roof is a compliment next to yours")

# Choices.
R("diamond_bread", "trade", "trading a diamond for bread", "",
  "you traded a diamond for bread|you gave a villager diamonds for an apple|worst trade in history, that was you",
  "i was starving >> starving for good deals|the bread was good >> the bread was three wheat|it was a nice villager >> the nice villager got rich")
R("ravine_house", "home", "its house by the ravine", "",
  "you built your house on the edge of a ravine|one step out the door and you're in a ravine|your front yard is a cliff",
  "great view >> the view is your grave|it's defensive >> defensive against you walking home|i like the drama >> drama every morning")
R("bed_lava", "home", "its bed by the lava", "",
  "your bed is next to lava|you sleep next to a lava pool, why|that lava's two blocks from your pillow",
  "it's warm >> warm until it's not|it's a night light >> a night light that eats beds|i'm careful >> careful is what you're not")
R("desert_home", "biomes", "living in the desert", "",
  "you picked a desert to live in. on purpose|why would you move to a desert|nothing grows where you live",
  "cactus grows >> cactus is not food|i like the sun >> the sun's trying to cook you|it's peaceful >> peaceful until the husks come")
R("ocean_home", "ocean", "its ocean base", "",
  "you built a base in the middle of the ocean, how do you get home|your house is a raft basically|island living, so you can't escape",
  "boats >> your boat sank twice|it's private >> private and wet|the drowned don't visit >> the drowned visit every night")
R("sold_mending", "trade", "selling a mending book", "b:knows_enchanting",
  "you traded away a mending book|you sold the best book in the game|a mending book, for emeralds, why",
  "i needed emeralds >> emeralds don't fix pickaxes|i didn't know >> now you know, pain|i have another >> you don't have another")
R("left_village", "villagers", "leaving the village", "",
  "you moved out of a perfectly good village|you had a village and left it|the villagers miss you, i think",
  "too noisy >> noisy compared to your empty house|i like quiet >> quiet like a desert|the villagers were weird >> you were weirder")
R("gapple_waste", "food", "wasting a golden apple", "",
  "you ate a golden apple for half a heart|wasted a golden apple on a chicken fight|you eat golden apples like snacks",
  "safety first >> half a heart, it was half|i panicked >> panicked over a chicken|it was there >> it was there and then gone")
R("field_bed", "sleep", "sleeping outside", "",
  "you sleep outside with no walls|you put a bed in a field|your bedroom is the sky",
  "it's freedom >> the zombies love your freedom|the stars are pretty >> stars and skeletons|i'm brave >> brave and asleep")
R("tnt_mining", "mining", "mining with tnt", "",
  "you mine with tnt and lose all the ore|tnt mining, so you can find nothing faster|you blew up your diamonds",
  "it's fast >> fast at losing diamonds|it's fun >> fun for the craters|they drop sometimes >> sometimes is never")
R("named_stone_sword", "weapons", "its named stone sword", "",
  "you named your stone sword|you used an anvil to name a stone sword|your stone sword has a name, why",
  "she deserves one >> she'll break by morning|it's tradition >> a tradition of wasting levels|she's been through a lot >> she's been through two zombies")
R("always_moving", "home", "always moving house", "",
  "you've moved house five times this week|you never stay anywhere|how many houses have you abandoned",
  "i'm a wanderer >> a wanderer of abandoned huts|i get bored >> bored of finishing|the land called me >> the land wants you to leave too")
R("five_buckets", "tools", "its buckets", "",
  "you made five buckets and no armor|who needs five buckets|you have more buckets than sense",
  "water, lava, milk >> and two spares for nothing|options >> options of a fool|i'm a bucket person >> a bucket person, that's new")
R("diamond_shovel", "tools", "its diamond shovel", "",
  "first diamonds and you made a shovel|you made a diamond shovel before a pickaxe|a shovel, with diamonds",
  "dirt is serious business >> dirt isn't serious|there's sand everywhere here >> sand, the most valuable block. sure|i dig a lot >> you dig your own holes, sure")
R("trusted_bandit", "players", "trusting a bandit", "",
  "you trusted a bandit and lost your stuff|someone said hold my stuff and you gave it all|you fell for the drop your stuff trick",
  "he seemed nice >> he seemed nice taking it|it was a test >> you failed the test|i got it back >> you got a stick back")
R("river_farm", "farming", "its river farm", "",
  "you built your farm on a river and it washed away|your crops floated off|farming on water, bold",
  "it was a test >> test failed, wheat's gone|it looked flat >> flat and wet|water's good for crops >> good for fish")
R("never_listens", "advice", "not listening", "",
  "i told you not to dig straight down and you did|you never listen and then you die|i said don't and you did anyway",
  "i had to see >> you saw lava|you're not my boss >> not your boss, just your mourner|it looked fine >> it never looks fine")
R("cow_boat", "animals", "the cow in the boat", "",
  "you spent an hour getting a cow into a boat|you tried to row a cow across the ocean|the cow got out halfway",
  "she needed a new home >> she needed you to stop|it's a skill >> the skill of losing a cow|she'll be fine >> she's fine, alone on an island")
R("sprint_starve", "food", "sprinting everywhere", "",
  "you sprint jump everywhere and starve|your hunger bar is always empty from sprinting|you sprint to the furnace",
  "speed is life >> life is short, you made it shorter|i'm efficient >> starving is efficient?|walking is boring >> boring and fed")
R("field_chest", "storage", "its chest in a field", "",
  "you left a chest in the middle of a field|your stuff is just in a random chest outside|anyone can open your field chest",
  "nobody goes there >> i went there|it's hidden >> hidden in a plain field|i trust people >> trust is how you lose things")

# Storage and inventory.
R("messy_inventory", "storage", "its messy chests", "",
  "your inventory is a disaster|i opened your chest and it was soup|nothing in your chests is sorted",
  "i know where things are >> you took ten minutes to find a torch|it's organized chaos >> chaos yes, organized no|sorting is boring >> boring and lost")
R("dirt_stacks", "storage", "its stacks of dirt", "",
  "you carry four stacks of dirt everywhere|why is half your inventory dirt|dirt collector",
  "dirt is useful >> useful for huts like yours|emergency pillar >> an emergency of hoarding|you never know >> you know, it's dirt")
R("chest_maze", "storage", "its chest maze", "",
  "you have a hundred chests and no labels|your storage room is a maze of chests|you open every chest to find one thing",
  "i remember >> you remember wrong every time|labels are for amateurs >> amateurs who find things|it's a system >> a system of guessing")
R("flesh_hoard", "storage", "its rotten flesh", "",
  "you keep every rotten flesh you ever found|three chests of rotten flesh, why|what are you saving rotten flesh for",
  "trading >> nobody trades for that much|emergencies >> an emergency zombie costume?|i might need it >> you've never needed it")
R("floor_storage", "storage", "having no chests", "",
  "you have no storage at all|where do you keep your stuff, the floor?|your stuff is just lying around",
  "i carry it >> you carry dirt, the rest is on the floor|the floor is a chest >> the floor ate your diamonds|i'm minimalist >> minimalist by accident")
R("ender_chest_seeds", "storage", "its ender chest", "",
  "you keep junk in your ender chest|your ender chest is full of seeds|you use your ender chest for dirt",
  "it's safe >> safe dirt, wow|seeds are precious >> precious like gravel|why not >> because it's an ender chest")
R("shulker_cobble", "storage", "its shulker boxes", "b:knows_end",
  "your shulker boxes are all one colour and unsorted|you have a shulker of cobble|shulkers full of junk",
  "cobble is important >> important to you only|it's portable >> portable junk|i'll sort it >> you'll sort it never")
R("hotbar_layout", "gear", "its hotbar layout", "",
  "your hotbar layout makes no sense|why is your sword in the last slot|you scroll forever to find your food",
  "it works for me >> you died scrolling|muscle memory >> memory of losing|the last slot is lucky >> lucky for the zombies")
R("barrels", "storage", "its barrels", "",
  "you use barrels for everything|your house is all barrels|barrels as walls, as tables, as beds",
  "barrels are cool >> you slept on a barrel|they stack >> they stack like your problems|they're versatile >> versatile, no")
R("wrong_frames", "storage", "its item frames", "",
  "you put item frames on every chest and still lose things|you labeled everything and it's still chaos|your item frames are all wrong",
  "they're right >> the bread frame has coal in it|some are old >> old as your house|i changed things >> change the frames too")
R("hopper_sorter", "redstone", "its item sorter", "b:knows_redstone",
  "your hopper chain goes nowhere|you built a sorting system that sorts nothing|your item sorter just eats items",
  "it's a prototype >> the prototype ate my sword|it sorts some >> some is a strong word|it needs tuning >> tune it into the lava")
R("cobble_haul", "mining", "its cobble haul", "",
  "you came back from the cave with a full inventory of cobble|you threw away iron to keep cobble|your mining trip was all gravel",
  "cobble is useful >> useful for nothing|i panicked >> panicked into a bad choice|gravel makes flint >> flint for your fires")
R("rain_chests", "storage", "its outdoor chests", "",
  "your chests are outside in the rain|you keep your storage in a field|your storage has no roof",
  "chests don't get wet >> they don't, but you do|it's open plan >> open plan for thieves|easy access >> easy access for bandits")
R("item_trail", "storage", "the items it drops", "",
  "you drop stuff everywhere like a trail|i followed your trail of seeds home|you leave items like breadcrumbs",
  "i'm marking my path >> the chickens are following it|it's on purpose >> on purpose to get lost|i'll pick it up >> you'll pick it up never")
R("stashes", "storage", "its hidden stashes", "",
  "you have stashes everywhere and forget them all|you bury chests like a fox hides stuff|you hide chests and lose them",
  "i'll find them >> you'll find one by accident|it's insurance >> insurance for the next person|it's a treasure hunt >> a treasure hunt for you, every day")

# Habits.
R("wall_staring", "jokes", "staring at walls", "",
  "you stand still and stare at walls for ages|i saw you staring at a wall for five minutes|what were you doing, staring at the dirt",
  "thinking >> thinking about walls?|planning >> planning to stare more|resting my eyes >> your eyes were open")
R("no_torches", "home", "its dark house", "",
  "you never place torches|your base is pitch black|i trip over mobs in your house",
  "i know my way >> the zombies know your way too|light attracts attention >> attention from zombies in your kitchen|i like the dark >> the dark likes you, it keeps spawning things")
R("early_bed", "sleep", "going to bed early", "",
  "you go to bed at sunset like a villager|you're in bed before the sun's down|grandpa hours",
  "early to bed >> early to bed, early to bore|i like mornings >> mornings are long for you|it's safer >> safe and boring")
R("chatterbox", "jokes", "how much it talks", "",
  "you never stop talking|you narrate everything you do|you talk to your chickens",
  "they listen >> they walk away|i'm sociable >> sociable with chickens|someone has to >> nobody has to")
R("always_late", "friends", "being late", "",
  "you're always late|we said morning and you came at noon|you show up when it's over",
  "i got lost >> lost again|i was busy >> busy being late|i'm here now >> the fight was yesterday")
R("dramatic", "health", "making a fuss", "",
  "you make a big deal out of half a heart|one hit and you scream|you act like every scratch is the end",
  "it hurts >> half a heart, relax|i'm dramatic >> dramatic is an understatement|hearts are precious >> precious like your ego")
R("one_diamond", "brag", "its one diamond", "",
  "you won't shut up about your one diamond|we know, you found diamonds once|you show everyone your diamond",
  "it was hard >> it was one|it's a big deal >> a big deal for a small find|you're jealous >> jealous of one diamond?")
R("keeps_everything", "storage", "keeping everything", "",
  "you keep everything you ever found|you never throw anything away|you're a dragon on a pile of junk",
  "everything's useful >> useful for nothing|i might need it >> you might need a bigger house|it's my collection >> a collection of trash")
R("wanders_off", "exploring", "wandering off", "",
  "you wander off every time we go somewhere|i turn around and you're gone|you get distracted by every flower",
  "flowers are nice >> you picked fourteen flowers|i was exploring >> exploring the wrong way|you walk too fast >> i walk at a normal speed")
R("complainer", "complaints", "its complaining", "",
  "you complain about everything|you complained about the sun yesterday|the rain, the sun, the night, all bad to you",
  "they're all bad >> the sun is fine|i'm honest >> honest and annoying|it's valid >> valid like a dirt hut")
R("overpacked", "gear", "how much it packs", "",
  "you bring three stacks of torches to the surface|you pack for a trip like you're moving|you bring food for a week to go next door",
  "better safe >> safe and slow|you never know >> you know. it's next door|i've been burned before >> burned by your own lava")
R("charges_in", "fighting", "charging in", "",
  "you jump into every fight without thinking|you charge in and i have to save you|you fight first, think never",
  "i'm brave >> brave until i pull you out|it works >> it works for the mobs|thinking is slow >> thinking would save you")
R("picky_eater", "food", "being picky about food", "",
  "you won't eat anything but steak|you turned down bread from me|picky eater",
  "steak is the best >> steak is not the only food|bread is sad >> bread is fine|i have standards >> standards and an empty stomach")
R("bad_timing", "building", "building at the worst time", "",
  "you stand in the rain building and complain|you build at night and wonder why mobs come|you always pick the worst time to build",
  "inspiration strikes >> lightning strikes too|i lose track of time >> time lost you|the mood was right >> the mood was zombies")
R("villager_watcher", "villagers", "watching villagers", "",
  "you follow villagers around all day|you watch villagers like it's a show|you know every villager's schedule",
  "they're interesting >> interesting like gravel|i learn things >> you learned to say hmm|they're my friends >> your friends say hmm")
R("indecisive", "building", "taking forever to choose", "",
  "you take forever to decide anything|you stood at the crafting table for a whole day|pick a block already",
  "choices matter >> it's oak or spruce, pick|i'm thinking >> thinking is not building|it's important >> important like a stick")
R("door_clicker", "home", "clicking its door", "",
  "you open and close your door ten times|why do you keep clicking the door|your door goes click click click all night",
  "it's fun >> fun for you|i'm testing it >> tested enough|nervous habit >> the zombies hear it too")
R("flower_picker", "plants", "picking every flower", "",
  "you have a chest full of flowers|why do you pick every flower|you picked a whole meadow",
  "they're pretty >> the bees are mad|dye >> dye for what|they make me happy >> happy, and the bees have nowhere to go")
R("bad_jokes", "jokes", "its jokes", "",
  "your jokes are worse than your builds|you tell the same joke every day|nobody laughs at your creeper joke",
  "you laughed >> i laughed at you|they're classics >> classics like dirt|my jokes are great >> great at clearing a room")
R("always_crouching", "jokes", "crouching everywhere", "",
  "you sneak everywhere like you're guilty|why are you always crouching|you crouch walk to the furnace",
  "i don't want to fall >> the furnace isn't an edge|it's safe >> safe and slow|habit >> the habit of a scared rabbit")
R("never_shares", "food", "never sharing", "",
  "you never share your food|you hide your apples from me|i've never seen you give anyone anything",
  "it's mine >> it's an apple|you'd eat it all >> i'd eat one|i share sometimes >> sometimes is never")
R("rushes", "building", "rushing everything", "",
  "you rush everything and it shows|your builds look rushed because they are|you rush and then fix it twice",
  "speed is a skill >> the skill of messing up quickly|i fix fast too >> fixing twice isn't fast|done is better than perfect >> done badly is still done badly")
R("night_owl", "night", "staying up all night", "",
  "you're only awake at night like a phantom|you sleep all day and mine all night|you're basically a bat",
  "night is peaceful >> peaceful with all the zombies?|less crowded >> crowded with mobs|bats are cool >> bats are not cool")
R("fishing_job", "fishing", "fishing all day", "",
  "you spend your days fishing and call it work|fishing all day isn't a job|you fish more than you mine",
  "it's relaxing >> relaxing and broke|it's a living >> a living of boots|fish are loot >> loot like seaweed")

# ---------------------------------------------------------------------------------------------------------------------
# 2. Roasts between rivals: the same shape, but nastier; it ends in a disagreement (the worst ones in rivalry).


def RV(key, tag, about, needs, roasts, pairs, worst=False):
    turns = [turn("a", L(roasts)),
             turn("b", PR(pairs)),
             turn("a", ["whatever"]),
             turn("b", V(pick(RIVAL_FINAL, 5), RIVAL_FINAL_V), mark=["disagree"]),
             turn("a", V(pick(RIVAL_CLOSE, 4), RIVAL_CLOSE_V, k=2), chance=0.6)]
    add("rival_" + key, about, ["roasts", "rivals", tag], [tag, "rivals", "argument", "roasts"], turns,
        needs=["rivals"] + N(needs), weight=0.8, effects=["rivalry"] if worst else None)


RV("house_ugly", "building", "its ugly house", "",
   "your house is the ugliest thing in this biome|i'd rather sleep in a ravine than in your house|your house makes the zombies look good",
   "at least i built mine myself >> yeah, it shows|you're just jealous >> jealous of a creeper crater? no|go look at your own house first >> mine doesn't scare the villagers")
RV("hut_forever", "building", "its hut", "b:has_hut",
   "still in that hut? figures|a hut fits you, small and sad|you'll be in that dirt hut forever",
   "it does the job >> so does a hole, you'd know|better than your nothing >> i've got a house. you've got dirt|it's more than you've built >> i've built more by accident")
RV("no_home", "home", "having no home", "b:no_home",
   "no home, no friends, no clue|you sleep wherever you fall, huh|homeless and clueless, nice combo",
   "i'm free >> free like a stray cat|i don't need a house >> the zombies agree, easy pickings|i'll build when i want >> you'll build never", True)
RV("copied", "building", "copying builds", "",
   "everything you build, you copied from me|stop copying my builds, it's sad|you took my roof idea and ruined it",
   "you don't own roofs >> i own that one, you stole it|yours was copied too >> mine was an original. yours is a bad copy|nobody would copy you >> you just did, again")
RV("weak_fighter", "fighting", "how badly it fights", "",
   "you fight like a scared chicken|i've seen villagers fight better than you|you can't even beat a zombie alone",
   "i've killed more than you >> name one thing you killed that wasn't a chicken|come fight me then >> i don't fight charity cases|says the one who ran from a spider >> it was a cave spider and you know it")
RV("coward", "fighting", "its cowardice", "",
   "you run the second the sun goes down|coward. you hide in your house every night|you hear a creeper and you're gone",
   "i'm smart, not scared >> smart people don't shake like that|you'd run too >> i stay and fight, you watch from your window|at least i'm alive >> alive and useless")
RV("loot_goblin", "players", "grabbing loot", "",
   "you only show up when there's loot|you've never helped anyone, only taken|you vanish in a fight and appear at the chest",
   "i help plenty >> name one time|you're just slow >> slow? i was fighting, you were looting|finders keepers >> finders keepers, cowards don't fight", True)
RV("bragger", "brag", "its bragging", "",
   "nobody cares about your diamonds|you brag every day and have nothing|all talk, no iron",
   "you're jealous >> jealous of what, your one diamond?|i have more than you >> show me then|cope >> cope is your whole personality")
RV("mooch", "food", "mooching food", "",
   "you always beg for food and never pay it back|you eat everyone's food and grow nothing|mooch",
   "i paid you back >> with rotten flesh|i'll pay you back >> you said that four times|you're stingy >> stingy with you, yeah, for a reason")
RV("slow_miner", "mining", "its mining", "",
   "you mine like a baby zombie|you've been in that mine for days and found coal|your mine is just a hole with regret",
   "i found iron >> three iron in a week, legend|at least i mine >> at least i find things|your mine flooded >> and i fixed it. yours just sits there empty")
RV("dies_a_lot", "death", "dying all the time", "",
   "you die more than anyone i know|the respawn point is your real home|how many times have you died this week, ten?",
   "accidents happen >> to you, daily|at least i do stuff >> you do dying|you've died too >> once. you're on a streak")
RV("trash_gear", "armor", "its gear", "",
   "your gear is trash and so are you|i've seen zombies with better armor|your sword is a joke",
   "it works >> barely, like you|you've got nothing better >> i've got iron. you've got leather and hope|i don't need fancy gear >> you need any gear")
RV("town_joke", "town", "what the town thinks of it", "a:in_town",
   "the whole town laughs at you|everyone in town thinks you're a joke|the town would throw a party if you left",
   "they laugh at you too >> not like they laugh at you|that's not true >> ask anyone at the well|i don't care what they think >> good, cause it's bad", True)
RV("bad_trader", "trade", "its trading", "",
   "you got scammed by a villager again|you trade like a lost sheep|the villagers see you and raise prices",
   "my trades are fine >> you paid ten emeralds for bread|you can't even trade >> i trade fine, you get robbed|at least i have emeralds >> had. you had emeralds")
RV("farm_dead", "farming", "its dead farm", "",
   "your farm is dead and so is your future|nothing grows near you|your crops look like they gave up",
   "they'll grow >> they won't, you planted them in sand|your farm's worse >> mine has wheat. yours has weeds|it was the rain >> rain makes crops grow, genius")
RV("smell", "jokes", "how it smells", "",
   "you smell like rotten flesh|i can smell you from the next chunk|you smell like a zombie's bad day",
   "that's your house >> my house smells like bread. yours smells like you|i was fighting zombies >> you were eating what they dropped|you smell worse >> i smell like a winner, you wouldn't know")
RV("noob", "players", "being a noob", "",
   "you play like a noob|day one noob behaviour from you, every day|you're a walking skill issue",
   "i'm better than you >> in your dreams maybe|says the noob >> takes one to spot one, and i spotted you|whatever, bozo >> bozo with a better house than yours")
RV("face", "jokes", "its face", "",
   "your face looks like a creeper's cousin|nice face, did a ghast design it|you look like a husk that tried",
   "says the one with a face like gravel >> gravel's cool, you're not|you look worse >> i look like a winner|at least my face is mine >> nobody else wanted it")
RV("bad_bridge", "building", "its bridges", "",
   "your bridge fell apart again|you can't even bridge|your bridges are why people fall",
   "nobody fell >> yet|it held >> held one chicken|yours is worse >> mine has both sides, yours has one")
RV("stick_fight", "fighting", "who'd win a fight", "",
   "i'd beat you with a stick|you'd lose a fight to a wooden sword|i could beat you blindfolded",
   "try it >> not worth the hunger|you've never won a fight >> i've won every fight against you|big talk >> big skill too, unlike you")
RV("no_friends", "friends", "having no friends", "",
   "nobody wants to group with you|you have zero friends and it shows|even the cats leave when you show up",
   "i have friends >> name one that isn't a chicken|i like being alone >> good, you'll get lots of it|you're not popular either >> more than you", True)
RV("lazy", "complaints", "how lazy it is", "",
   "you've done nothing all week|i've seen rocks with more drive|you're the laziest drifter alive",
   "i've been busy >> busy standing still|i plan things >> plans aren't things|you're one to talk >> i built a house this week, you built a nap")
RV("chest_snoop", "storage", "snooping in chests", "",
   "keep your hands off my chests|i know you've been in my chests|you smell like my iron",
   "i've never touched them >> your footprints say otherwise|prove it >> i'm counting my iron tonight|why would i want your junk >> you seemed to want it yesterday", True)
RV("kill_steal", "fighting", "stealing kills", "",
   "you stole my kill again|that was my zombie and you know it|stop stealing kills, loser",
   "you were too slow >> slow is when you take it after i did the work|it's not yours >> i hit it four times|first come first served >> you came last and served yourself")
RV("tower_flex", "building", "its tall tower", "",
   "your tower is trying way too hard|nobody needs a tower that tall|that tower screams look at me",
   "it's a lookout >> looking out for friends you don't have|you're jealous >> of a stick? no|you can't build one >> i could, i just have taste")
RV("greedy", "trade", "its greed", "",
   "you'd sell your friends for gold|you only care about emeralds|greedy little goblin",
   "i like trading >> you like robbing|you're just poor >> poor in emeralds, rich in friends. you're the opposite|at least i'm rich >> rich and alone")
RV("died_again", "death", "its latest death", "b:died_lately",
   "heard you died again, shocking|another death? you're on a roll|died again lol, who could've seen it coming",
   "it wasn't my fault >> it never is|you'll die too >> not as much as you|shut up >> make me, respawn king")
RV("cave_scared", "caves", "being scared of caves", "",
   "you're too scared to go in caves|you won't go below sea level|scared of the dark at your age",
   "caves are dangerous >> for you, everything is|i go in caves >> the first block of them|you come out of caves crying >> i come out with iron")
RV("sheep_thief", "animals", "its sheep", "",
   "you stole my sheep|that's my sheep in your pen, i dyed it|give me back my cow",
   "it wandered over >> it wandered over on a lead|you can't prove it >> it's pink. i'm the only one with pink dye|finders keepers >> keepers of stolen sheep", True)
RV("bad_cook", "food", "its cooking", "",
   "your cooking is a crime|you burn everything you touch|i'd rather eat rotten flesh than your stew",
   "you've never tried it >> i saw it. that was enough|it's better than yours >> mine is edible|you'd eat it if you were hungry >> i'd starve first")
RV("loud", "complaints", "how loud it is", "",
   "you never shut up|you're louder than a ghast|your voice gives me a headache",
   "you talk more than me >> i talk sense|nobody asked you >> nobody asked you first|then leave >> i was here first")
RV("last_place", "town", "always coming last", "",
   "you came last in everything|last in the race, last at the contest, last in everything|you're always last",
   "it was rigged >> rigged by your legs|i didn't try >> clearly|someone has to be last >> and you always volunteer")
RV("sad_animals", "animals", "its animals", "",
   "your animals look sad|your pigs look like they want to leave|your cows want to move in with me",
   "they're fine >> they're plotting to escape|they're happy >> happy like a lost sheep|your animals are worse >> mine have grass and a gate")
RV("fake_helper", "help", "its helping", "b:voice_helper",
   "you only help so people like you|your helping is fake|nobody asked for your help",
   "i help everyone >> and everyone wishes you'd stop|at least i try >> trying and failing, very nice|you've never helped anyone >> i don't need to brag about it")
RV("speedrunner", "plans", "rushing everything", "b:voice_speedrunner",
   "you rush everything and finish nothing|speedrunning to nowhere|all speed, no brain",
   "i'm efficient >> efficient at dying fast|you're slow >> slow and alive|i'd beat you to anything >> including the respawn screen")
RV("builder_snob", "building", "its builder ego", "b:voice_builder",
   "you think you're the best builder, you're not|your builds are just boxes with trim|stop acting like you invented the roof",
   "my builds are art >> art nobody asked for|you can't build >> i can, i just don't show off|at least i have style >> style on a dirt foundation")
RV("hoarder", "storage", "its hoarding", "b:voice_hoarder",
   "you hoard everything and share nothing|forty chests and you won't lend a torch|you're a dragon with no dragon",
   "it's mine >> and you're welcome to it, alone|i might need it >> you'll need friends more|you'd waste it >> i'd use it, you just stare at it")
RV("tryhard", "fighting", "its tryhard ways", "b:voice_pvp_sweat",
   "you're such a tryhard|nobody cares about your kill count|you think you're the best, you're not",
   "i am the best >> best at being annoying|1v1 me then >> i've got better things to do|you're just bad >> bad at caring about you")
RV("mole", "mining", "living underground", "b:voice_miner",
   "you live in a hole and smell like one|crawl back in your mine|you haven't seen the sun in weeks, it shows",
   "i've got more ore than you >> ore can't buy you a tan|the surface is boring >> the surface is where people live|at least i work >> you dig. a worm digs")
RV("flake", "exploring", "never staying", "b:voice_nomad",
   "you never finish anything, just leave|flaky. you'll be gone tomorrow anyway|you leave a trail of half huts behind you",
   "i like moving >> you like running|staying is boring >> staying is called having a home|you're just stuck >> stuck with a house, poor me")
RV("boring_homebody", "home", "never leaving home", "b:voice_homebody",
   "you never leave your house|boring. you're the most boring drifter here|your life is your bed and your kitchen",
   "i like my home >> your home is all you have|it's safe >> safe and boring|you're never home >> because i have a life")
RV("clueless", "advice", "not knowing anything", "b:voice_newbie",
   "you don't know anything|you couldn't find iron with a map|you asked what a furnace was. yesterday",
   "i'm learning! >> learn faster|you were new too >> i was never this bad|that's mean >> the truth is mean sometimes")
RV("crook", "players", "being a crook", "b:voice_bandit",
   "everyone knows you steal|you're a crook and a bad one|i hide my chests when you walk by",
   "i don't steal >> your pockets say otherwise|you've got nothing worth stealing >> then why do you watch my house|prove it >> i don't need to, everyone knows", True)
RV("grump", "players", "being rude", "b:voice_hermit",
   "you're so rude, that's why you're alone|grumpy old hermit|you talk like a closed door",
   "go away >> see? rude|i like being alone >> good, stay that way|leave me be >> gladly. nobody wants to be here")
RV("vote", "politics", "its chances in a vote", "a:in_town",
   "nobody would vote for you|you'd lose an election to a chicken|if you led this town we'd all starve",
   "i'd be a good leader >> good at leading us into lava|you wouldn't win either >> i'd win against you|i don't want to lead >> good, nobody wants you to")
RV("neighbour", "home", "being a bad neighbour", "b:home_near",
   "you're the worst neighbour ever|your torches shine in my window all night|your chickens keep me up",
   "you're worse >> i don't dig under people's houses|move then >> i was here first|my house was here first >> and it's been ugly since")
RV("mob_magnet", "mobs", "pulling mobs everywhere", "",
   "you bring mobs everywhere you go|zombies follow you like friends|every creeper on the map knows your name",
   "they come for you too >> they come for you first, i just watch|it's not my fault >> you never light anything|they like me >> they're the only ones")
RV("archer", "weapons", "its bow skills", "",
   "you couldn't hit a cow with a bow|your arrows are a danger to everyone but mobs|you shot me last week",
   "that was an accident >> accidents are your whole skill|you got in the way >> i was behind you|you're one to talk >> i hit what i aim at")
RV("slowpoke", "jokes", "how slow it is", "",
   "you walk like a zombie in sand|you're so slow, snails lap you|by the time you get anywhere it's night",
   "i don't rush >> you don't anything|i walk fine >> fine for a turtle|at least i don't starve sprinting >> at least i get places")
RV("paper_walls", "building", "its weak walls", "",
   "your house wouldn't survive one creeper|i could break into your house with a wooden pick|your walls are paper",
   "try it >> careful what you wish for|my house is fine >> fine for a pig|you'd never get past my door >> your door is a fence gate")
RV("ore_liar", "diamonds", "its diamond story", "",
   "you lie about your diamonds|you never found diamonds, admit it|you got those diamonds from a trader, admit it",
   "i found them >> where then? exactly|you're just jealous >> jealous of a lie? no|i'll show you >> you won't, they don't exist")
RV("fake_brave", "mobs", "acting tough", "",
   "you act tough but you scream at bats|all bark, no sword|you're brave until a spider shows up",
   "i'm not scared of anything >> i saw you jump at a bat|you scream too >> not like you, you hit a high note|spiders are dangerous >> to you, sure")
RV("bad_advice", "advice", "its bad advice", "",
   "your advice nearly killed me|never listen to you, lesson learned|you told me to dig down. i fell in lava",
   "you did it wrong >> i did what you said|it worked for me >> you got lucky|you should've known better >> i know better now. never ask you")
RV("lost_fight", "fighting", "the fight it lost", "",
   "you lost a fight to a witch, sit down|you got beaten by a pillager, rookie|a skeleton outplayed you",
   "it was three of them >> it was one|you would've lost too >> i'd have won with one hand|it was dark >> skeletons don't care, neither do i")
RV("boats", "ocean", "its boating", "",
   "your boat skills are a crime against water|you sink boats, which is impressive|the drowned learned from you",
   "the ocean was rough >> it was a lake|at least i went out there >> and came back without the boat|i swim fine >> fine like a stone")
RV("bad_gifts", "friends", "its gifts", "",
   "your gifts are trash|you gave me a dead bush for my house|who gives rotten flesh as a gift",
   "it's the thought >> the thought was trash too|you never give anything >> i give good things, to people who aren't you|it was a joke >> a joke like your house")
RV("cursed", "jokes", "its bad luck", "",
   "everything you touch breaks|your luck is cursed and so are you|you're bad luck on legs",
   "i'm not cursed >> your sword broke three times this week|you're the bad luck >> things go fine until you show up|luck isn't real >> neither are your diamonds")
RV("sleepy", "sleep", "sleeping all the time", "",
   "you sleep more than a cat|you're in bed before the sun sets|you sleep all day and do nothing",
   "sleep is healthy >> so is working|at least i skip the night >> skip the day too, apparently|you're always tired >> tired of you")
RV("chicken_person", "animals", "its chickens", "",
   "you have a hundred chickens and nothing else|chicken person|your base is a chicken coop with a door",
   "chickens are useful >> for what, noise?|eggs >> nobody needs that many eggs|at least i have animals >> you have feathers")
RV("lost", "exploring", "getting lost", "",
   "you get lost in your own town|you'd get lost in a straight tunnel|you can't find north",
   "i always find my way back >> after three days|you got lost too >> once. you live lost|the map was wrong >> the map was fine, you're wrong")
RV("dark_base", "home", "its dark base", "",
   "your base is darker than a cave|mobs spawn in your living room|your house is a mob farm by accident",
   "it's cozy >> cozy for zombies|i light it up >> with one torch|it's on purpose >> nobody does that on purpose")
RV("nether_scared", "nether", "never going to the nether", "b:new_to_nether",
   "you've never even been to the nether|scared of a little lava|you'd cry at your first ghast",
   "i don't need the nether >> you're just scared|i'll go when i'm ready >> so never|it's dangerous >> for you, yes")
RV("tool_borrow", "tools", "borrowed tools", "",
   "you borrow tools and never give them back|i want my pickaxe back|you broke the last sword i lent you",
   "it was already broken >> it was new|i'll give it back >> you said that last week|you never asked for it >> i'm asking now")
RV("village_fool", "villagers", "what the villagers think", "",
   "even the villagers don't like you|the villagers close their doors when you walk by|the iron golem watches you, not the zombies",
   "the villagers like me >> they like your emeralds|golems watch everyone >> they watch you closer|you hit a villager once >> once. you do it weekly")
RV("bad_fisher", "fishing", "its fishing", "",
   "you fish all day and catch boots|your fishing is a waste of string|you couldn't catch a cod in a bucket",
   "fishing takes patience >> patience for boots|i caught a book once >> once, in a month|you can't fish at all >> i don't need to, i can farm")
RV("desert_rat", "biomes", "living in the desert", "",
   "you live in a desert like a husk|you smell like sand|you're half cactus at this point",
   "i like the desert >> only husks like the desert|at least it's warm >> warm and empty, like you|your swamp is worse >> my swamp has water")
RV("jealous", "brag", "being jealous", "",
   "you're jealous of everything i have|every time i build something you sulk|stop staring at my house",
   "i'm not jealous >> you're green, and not from emeralds|your house isn't that good >> then why do you keep looking|i look because it's ugly >> you look because it's better than yours")
RV("bad_enchants", "gear", "its enchants", "",
   "your enchants are garbage|unbreaking one on a gold sword, lol|you wasted thirty levels on bane",
   "it was random >> random and bad, like you|my enchants are fine >> fine for a chicken fight|you don't even enchant >> i do, and mine work")
RV("short_fuse", "complaints", "its temper", "",
   "you rage at everything|you yelled at a cow yesterday|you get mad when it rains",
   "i don't rage >> you're raging right now|the cow started it >> the cow was eating grass|rain is annoying >> so are you")
RV("left_behind", "friends", "being left behind", "",
   "you left me in that cave|you ran off with my loot when i died|you sided with them over me",
   "you were too slow >> i was dead, that's slow|i didn't take it >> i saw you running with my sword|you'd have done the same >> i'd have waited for you", True)
RV("two_faced", "gossip", "talking behind its back", "",
   "you talk about me behind my back|i heard what you said at the well|you're nice to my face and mean after",
   "i never said anything >> the whole town says you did|it was a joke >> joke's on you, i heard it|it was true though >> then say it to my face", True)
RV("sore_loser", "argument", "losing badly", "",
   "you're a sore loser|you lost and sulked about it for a day|you blame everyone when you lose",
   "i didn't sulk >> you sat in your house all night|it was unfair >> it was fair, you were bad|you're a sore winner >> i'm a winner, that's the part you miss")
RV("shop_prices", "trade", "its shop prices", "b:keeps_shop",
   "you won't trade fair|you charge three diamonds for bread|your shop is a robbery with a sign",
   "my prices are fair >> fair for a bandit|nobody makes you buy >> nobody does buy|you can't afford it >> nobody can, that's the problem")
RV("boring", "jokes", "how boring it is", "",
   "you're so boring to talk to|every time you talk about wheat, i fall asleep|your stories put phantoms to sleep",
   "you're no fun either >> i'm a party next to you|then don't talk to me >> gladly|my stories are good >> good for sleeping")
RV("half_dead", "health", "always being hurt", "b:hurt",
   "look at you, half dead again|can't even keep your hearts up|you're always one hit from respawning",
   "i was fighting >> fighting and losing|it's nothing >> it's three hearts|i'll heal >> eat something, you look awful")
RV("starving", "food", "being hungry", "b:hungry",
   "you're starving again, figures|no food? shocking|you look hungry, go eat some grass",
   "i'll get food >> from who, me? no|lend me some bread >> no|it's not your problem >> it's not, enjoy")
RV("group_joke", "friends", "its group", "b:in_group",
   "{b.group} is a joke|your group would fall apart without the one who works|your group is just you and some chickens",
   "my group is great >> great at losing|at least i have a group >> a group of losers|your group's worse >> i don't need a group to be good")
RV("night_scared", "night", "hiding at night", "",
   "you lock yourself in every night like a baby|scared of the dark, really|you hide under your blanket at night",
   "nights are dangerous >> for you, everything is|i'm not scared >> your door's been locked since sunset|you hide too >> i fight, you hide")
RV("ego", "brag", "its ego", "",
   "your head's bigger than your house|you think you're the best at everything|you talk like a legend and play like a noob",
   "i am good >> good at talking|you're jealous >> jealous of your ego? no|i've done more than you >> you've said more than me, not done")
RV("crater", "building", "the creeper hole in its house", "",
   "a creeper did your house a favour|your house got better after the creeper hit it|i'd pay a creeper to finish the job",
   "that's not funny >> it's a little funny|i'll rebuild >> please don't|i'll tell the creeper where you live >> it already knows")
RV("pets", "pets", "its pets", "",
   "your dog is as ugly as you|your cat ignores you, smart cat|your parrot only says rude things, i wonder who taught it",
   "my dog is great >> great at running from you|leave my cat alone >> your cat left you alone first|you're just mad >> mad that your parrot copies you")
RV("cheater", "town", "cheating at contests", "",
   "you cheat at every contest|you cheated in the race and everyone saw|you took a shortcut and called it a win",
   "i didn't cheat >> you cut through the river|it's not cheating if it works >> that's literally cheating|you're mad you lost >> mad you cheated, yeah", True)
RV("flat_roof", "building", "its flat roof", "",
   "your roof is flat and sad|a flat roof? lazy|your roof looks like a lid",
   "flat roofs are clean >> clean and boring|it's modern >> modern like a box|i'll add a real roof >> you won't")
RV("diamond_waste", "diamonds", "wasting diamonds", "",
   "you wasted your diamonds on a hoe|diamond hoe, diamond brain|who makes diamond boots before a pickaxe",
   "it's my choice >> a bad one|you'd do the same >> i'd never|i have more >> no you don't")
RV("crop_thief", "farming", "taking its crops", "",
   "you harvested my wheat|those were my carrots you ate|stay out of my farm",
   "they were ripe >> ripe and mine|you weren't using them >> i was growing them|prove it >> your boots are muddy")
RV("jokes", "jokes", "its jokes", "",
   "your jokes are the worst|you laugh at your own jokes, nobody else does|your jokes are older than the world",
   "you laughed once >> by accident|my jokes are fine >> fine for a zombie|you've got no humor >> i've got plenty, you've got none")
RV("weak_arm", "fighting", "how weakly it hits", "",
   "you hit like a baby zombie|your swing tickles|you take five hits to kill a chicken",
   "my sword is dull >> so are you|chickens are tough >> chickens are not tough|i hit harder than you >> in your dreams")
RV("iron_beggar", "ore", "begging for iron", "",
   "you beg for iron every day|go mine your own iron|you've never mined iron in your life",
   "i just need a little >> you need a lot, every day|you have plenty >> because i mined it|i mine iron >> name the last time")
RV("raid_hide", "fighting", "hiding in the raid", "",
   "you hid in a house during the raid|the raid came and you went to bed|we fought the raid, you hid with the villagers",
   "i was guarding the villagers >> guarding them from the inside, sure|i had no armor >> you never have armor|someone had to stay back >> you always stay back")
RV("town_eyesore", "town", "the ugliest house in town", "a:in_town",
   "your house is the ugliest in town|you bring the whole town's look down|the town hides your house from visitors",
   "my house is fine >> the town disagrees|yours isn't better >> people stop to look at mine|i'll fix it >> you've said that since you moved in")
RV("snitch", "politics", "snitching", "a:in_town",
   "you told the leader about my chest|snitch|you can't keep anything to yourself",
   "you deserved it >> for what, having stuff?|i didn't say anything >> the leader knew everything|it was the right thing >> the right thing for a snitch", True)
RV("cheapskate", "trade", "being cheap", "",
   "you're the cheapest drifter in the world|you'd charge me to stand near your house|you've never given anything for free",
   "i'm careful >> careful with everything except your mouth|you never give me anything >> i don't owe you anything|it's business >> business with no customers")
RV("messy", "home", "its messy house", "",
   "your house looks like a raid went through it|clean your stuff up|i've seen tidier caves",
   "i know where things are >> you've lost your sword three times|it's lived in >> lived in by pigs|yours is messy too >> mine is sorted. by colour")
RV("grumpy", "complaints", "being grumpy", "",
   "you're always grumpy|you'd complain about free diamonds|smile once, it won't kill you",
   "i'm not grumpy >> you said that grumpily|everything's bad >> everything's fine, you're bad|leave me alone then >> gladly")
RV("fair_weather", "weather", "hiding from the rain", "",
   "you only come out when the weather's nice|first raindrop and you're inside|fair weather drifter",
   "i don't like rain >> you don't like anything|you're out in the rain like a fool >> a fool who gets things done|rain is bad for tools >> rain is water, it's fine")
RV("dead_weight", "friends", "slowing the group", "groupmates",
   "you're dead weight in this group|we'd be faster without you|you slow everyone down",
   "i do my part >> name your part|you're the slow one >> i carry this group|then leave >> i might, and you'd starve", True)
RV("sellout", "trade", "selling anything", "",
   "you'd sell your own house for an emerald|you traded away the village bell|you'd sell anything",
   "it was a good deal >> for the trader|i needed the emeralds >> you always do|you've never sold anything >> i don't sell my friends")
RV("noisy_farm", "farming", "its noisy farm", "b:home_near",
   "your farm makes so much noise|your chickens never stop|i hear your door clicking all night",
   "it's a farm >> it's a nightmare|deal with it >> i've been dealing with it|your house is loud too >> mine is quiet, i have walls")
RV("scared_of_me", "fighting", "never fighting it", "",
   "you'd never fight me|scared of a real fight|you only fight mobs because they can't talk back",
   "i fight when i want >> you never want|i'm not scared of you >> your feet say otherwise|you'd lose >> i'd win and you know it")
RV("clown_armor", "armor", "its dyed armor", "",
   "your armor looks like a clown's|who dyes armor that colour|you look like a slime ate a rainbow",
   "it's my style >> your style is loud|at least i have armor >> armor nobody can look at|you have no taste >> i have taste, you have dye")
RV("late", "friends", "being late", "",
   "you're always late, so useless|we started without you, again|you show up when it's over",
   "i had stuff to do >> you always have stuff|you never wait >> we waited an hour|i'm here now >> now is too late")
RV("food_waste", "food", "wasting food", "",
   "you cook too much and throw it away|you threw away a stack of potatoes|you waste food and beg for more",
   "i had too much >> then share it|potatoes are cheap >> food is food|you waste stuff too >> i waste nothing")
RV("slow_learner", "crafting", "learning slowly", "",
   "you still don't know how to make a bucket|how do you not know the furnace recipe by now|you learn slower than a pig",
   "i know it >> then why'd you ask|i forgot >> you forget everything|nobody taught me >> i did. twice")
RV("netherite", "armor", "its netherite", "b:armor_netherite",
   "all that netherite and still bad|netherite doesn't fix skill issues|you look rich and fight poor",
   "it protects me >> from what, effort?|you can't afford it >> i can afford taste|i earned it >> you got lucky in the nether")
RV("pocket_diamonds", "diamonds", "the diamonds it carries", "b:has_diamonds",
   "you carry diamonds around like a show off|nobody cares about the diamonds in your pocket|one creeper and those diamonds are in the grass",
   "they're mine >> for now|i'm keeping them safe >> on you? not safe|you've got none >> i've got sense")
RV("bad_teacher", "advice", "its bad lessons", "",
   "you taught me wrong on purpose|your lessons are useless|you told me lava was safe to swim in",
   "i was kidding >> i almost died|you didn't listen >> i did, that's the problem|i never said that >> you did, at the well")
RV("cave_hog", "caves", "its cave", "",
   "you take the best caves for yourself|that's my cave, i lit it first|stay out of my mine",
   "caves are free >> not that one|i was here first >> my torches say otherwise|nobody owns a cave >> i do, it's got my torches")
RV("village_trouble", "villagers", "the village it angered", "",
   "the whole village hates you|the village hasn't forgiven you for the golem thing|you got the whole village angry",
   "it was an accident >> a sword accident?|they forgave me >> the golem didn't|i saved them from a raid >> you walked in with bad omen and started that raid")
RV("kill_count", "mobs", "its kill count", "b:killed_many",
   "a few zombies and you think you're a hero|wow, {b.kills} kills, you want a medal?|you farm baby zombies for your kill count",
   "more than you >> mine were harder|count them yourself >> i don't count trash|you've got none >> i don't need to show off")
RV("follower", "players", "following it around", "",
   "stop following me around|you're always near my house|creep. go home",
   "it's a free world >> go be free over there|i live here too >> live further|i'm not following you >> you've been behind me for an hour")
RV("name", "jokes", "its name", "",
   "your name sounds like a bad seed|your name is the worst|who even named you",
   "i like my name >> one of us does|yours is worse >> mine is fine|my name is fine >> fine for a pig")
RV("iron_basic", "armor", "its iron armor", "b:armor_iron",
   "iron armor, wow, so basic|everyone has iron, you're nothing special|iron and still bad",
   "iron is good >> good enough for you|you have worse >> i have better in a chest|it does the job >> barely")
RV("leather", "armor", "its leather armor", "b:armor_leather",
   "leather? you dress like a cow|a cow wore that armor first|i've seen sheep better armored",
   "it's what i have >> and it's sad|you're not much better >> i've got iron|it's light >> light like your skill")
RV("gold", "armor", "its gold armor", "b:armor_gold",
   "gold armor? you're a walking piglin snack|shiny and useless|gold armor, gold brain",
   "it looks good >> looks good breaking|piglins leave me alone >> so does everyone else|i like it >> nobody else does")
RV("naked", "armor", "having no armor", "b:armor_none",
   "no armor, no chance|you're one zombie away from respawning|go put on some armor, it's embarrassing",
   "i don't need it >> you need it more than anyone|i'm faster >> faster to die|mind your own business >> my business is laughing at you")
RV("signs", "decor", "its signs", "",
   "your signs are spelled wrong|you wrote welcom on your door|your signs are a crime",
   "i was in a hurry >> you've been in a hurry since day one|nobody reads signs >> everybody read that one|spelling is overrated >> so are you")
RV("pity_party", "feelings", "feeling sorry for itself", "",
   "you complain like the whole world owes you|nobody feels sorry for you|stop crying about your luck",
   "i have bad luck >> you have bad choices|you'd complain too >> i'd fix it|i'm not crying >> you're crying right now")
RV("creeper_lure", "mobs", "leading creepers around", "",
   "every time you're around something blows up|you lead creepers right to my house|you're why my wall has a hole",
   "that wasn't me >> it followed you to my door|creepers are random >> creepers love you|fix your own wall >> you broke it, you fix it")

# ---------------------------------------------------------------------------------------------------------------------
# 3. Roasts from what the game knows (fill words; each turn also has lines without them).

R("fact_death", "death", "how it died", "b:died_lately",
  "saw your death message. \"{b.death}\". legend|\"{b.death}\", that's going on your grave|your last death message made my day|you died again? how this time",
  "it wasn't my fault >> it never is|it could happen to anyone >> it happens to you specifically|at least i got back up >> the respawn point is tired of you")
R("fact_death_2", "death", "its death", "b:died_lately",
  "\"{b.death}\". i read it twice, it got funnier|i'm framing your death message|i heard about the death. smooth",
  "you weren't there >> i didn't need to be, the message said it all|i'd like to see you do better >> i'd like to see you not die|ok it was dumb >> very dumb. i'm proud of you")
R("fact_armor", "armor", "its armor", "",
  "{b.armor} armor? really?|nice {b.armor} armor, did you win it off a cow|that armor of yours, wow|your armor is giving starter kit",
  "it keeps me alive >> barely|i like my armor >> you're the only one|it's better than yours >> mine matches, at least")
R("fact_held", "weapons", "what it's holding", "",
  "why are you holding a {b.held}|what are you gonna do with that {b.held}, scare me?|you walk around with the weirdest stuff in your hand|is that what you fight with?",
  "it's useful >> useful for looking lost|you'll see >> i'm scared, truly|mind your own hands >> my hands hold a sword, yours hold that")
R("fact_built", "building", "what it built", "b:built_lately",
  "saw your {b.built}. bold|your {b.built} is... a choice|i walked past your {b.built} and kept walking|saw what you built. we need to talk",
  "it's not done yet >> please say it changes a lot|you don't get it >> i get it, i just don't like it|i worked hard on that >> it shows, in a bad way")
R("fact_ore", "ore", "its ore haul", "b:found_ore",
  "{b.ore_count} {b.ore}? that's the haul? lol|you found {b.ore} and won't shut up about it|i heard about your ore. cute|that's all you found down there?",
  "it's more than you >> i didn't brag about a handful|ore is ore >> ore is ore, and that was a little|i'll find more >> sure, next week")
R("fact_kills", "mobs", "its kills", "b:killed_mobs",
  "{b.mob_count} {b.mob}s and you think you're a warrior|you got a few {b.mob}s, calm down|killed some mobs and now you're walking different|heard you went hunting. the mobs ok?",
  "it was hard ok >> they're slow, they're mobs|you didn't kill any >> i don't need to brag about it|i'm getting better >> better than a villager, maybe")
R("fact_hurt", "health", "how hurt it is", "b:hurt",
  "you look like a creeper sneezed on you|what happened to you, you're half a heart|did you lose a fight with a bush",
  "it was a hard fight >> with what, a chicken?|i'm fine >> you're bleeding pixels|you should see the other guy >> the other guy is fine, i saw him")
R("fact_hungry", "food", "being hungry", "b:hungry",
  "your stomach is louder than a ghast|you look like you'd eat rotten flesh right now|when did you last eat, last week?",
  "i forgot to eat >> how do you forget eating|i'm saving food >> saving it for the zombies?|got anything? >> nope, that's your problem lol")
R("fact_angry", "feelings", "its bad mood", "b:mood_angry",
  "who stepped on your crops|why so mad, did a creeper eat your house|you look like you want to punch a tree",
  "it's been a day >> every day is a day for you|leave me alone >> nah this is fun|i'm not mad >> you're steaming")
R("fact_proud", "brag", "its good mood", "b:mood_triumphant",
  "look at you, walking like you beat the dragon|what did you do, kill a chicken?|you're strutting, it's weird",
  "i had a good day >> one good day and you're unbearable|you wouldn't understand >> i understand you're annoying|jealous? >> of you? never")
R("fact_bored", "boredom", "being bored", "b:mood_bored",
  "you look bored out of your mind|you're as fun as a stone block today|bored? have you tried doing anything",
  "nothing to do >> there's a whole world, bro|you're boring too >> i'm not the one yawning|entertain me then >> that's what this is")
R("fact_scared", "feelings", "being jumpy", "b:mood_anxious",
  "you jump every time a leaf falls|you look like a creeper's following you|why are you so jumpy today",
  "it's been a bad night >> every night is bad for you|something's out there >> yeah, it's called a chicken|leave me be >> ok jumpy, ok")
R("fact_rich", "diamonds", "its diamonds", "b:has_diamonds",
  "you've got {b.diamonds} diamonds and still dress like that|all those diamonds and no armor?|rich and still bad, impressive",
  "i'm saving them >> for what, your grave?|they're for tools >> tools you'll lose in lava|it's my business >> it's everyone's business, you won't stop flashing them")
R("fact_hut", "building", "its hut", "b:has_hut",
  "hows the hut, still a hut?|you upgrade your hut yet or nah|that hut of yours is a lifestyle now",
  "i'm working on it >> since when|huts are underrated >> huts are rated correctly|it's home >> home is a strong word")
R("fact_no_home", "home", "having no home", "b:no_home",
  "still sleeping wherever you fall?|where do you even live right now|you're a drifter for real huh, no house",
  "i'm looking for a spot >> look harder|home is where i am >> that's a hole right now|i like being free >> free to get rained on")
R("fact_group", "friends", "its group", "b:in_group",
  "{b.group} still lets you hang around?|how did you get into {b.group}, bribes?|your group must be desperate lol",
  "they love me >> they tolerate you|i earned my spot >> earned it by being bad, i bet|jealous? >> of that group? lol no")
R("fact_town", "town", "its town", "b:in_town",
  "{b.town} still lets you in?|how's {b.town}, still the worst town around?|your town is a village with ambition",
  "my town is great >> great at being average|you're just jealous >> jealous of a town of dirt huts?|at least i have a town >> a town that hides you, sure")

RV("fact_death", "death", "its death", "b:died_lately",
   "\"{b.death}\". pathetic|i read your death message and laughed for a minute|died again? good",
   "you'll die too >> not like that|shut up >> make me, respawn rookie|it wasn't my fault >> it's always your fault")
RV("fact_built", "building", "what it built", "b:built_lately",
   "your {b.built} is an eyesore|that new thing you built is ugly|tear that thing down, it's hurting my eyes",
   "it's better than anything you built >> i don't build garbage|you're just jealous >> jealous of trash? no|you can't even build >> i can build better than that asleep")
RV("fact_ore", "ore", "its ore", "b:found_ore",
   "{b.ore_count} {b.ore}? i find that in an hour|your ore haul is sad|is that all you found? pathetic",
   "more than you >> in your dreams|you're jealous >> jealous of crumbs?|at least i mine >> you dig holes, that's it")
RV("fact_hurt", "health", "how hurt it is", "b:hurt",
   "you look terrible|someone beat you up good|half dead and still talking",
   "you should see the other guy >> i'd rather see you lose again|i'm fine >> you're barely standing|go away >> nah, this is the best part of my day")
RV("fact_mood", "feelings", "its bad mood", "b:mood_frustrated",
   "aw, did something go wrong again|frustrated? good|having a bad day? perfect",
   "leave me alone >> nah|it's your fault anyway >> my fault? i wish|you're the worst >> and you're having a bad day, so i win")

# ---------------------------------------------------------------------------------------------------------------------
# 4. Roasts that go too far: a hits a sore spot, b is hurt, a says sorry; b takes it (agree) or doesn't (disagree).

SORRY = L("ok that was too far, sorry|my bad, that wasn't cool|sorry, i didn't mean it like that|dang, ok, i'm sorry|"
          "that came out wrong. sorry|ok i crossed a line there, sorry|i was just messing, i'm sorry")
MADE_UP = L("ok. thanks|we're good?|i'll keep my mouth shut next time|deal|ok, we're good then|"
            "thanks for not hating me lol|i owe you one")
BACK_OFF = L("ok. i'll leave you alone|fair. i'm sorry anyway|ok, i get it|i'll go|alright. my bad")


def TF(key, tag, about, needs, roast, hurt, sorry, accept, refuse):
    turns = [turn("a", L(roast)),
             turn("b", L(hurt)),
             turn("a", L(sorry) + pick(SORRY, 3)),
             turn("b", L(accept), chance=0.6, mark=["agree"]),
             turn("b", L(refuse), cond=["!t:agree"], mark=["disagree"]),
             opts("a", [(["t:agree"], pick(MADE_UP, 4)), ([], pick(BACK_OFF, 3))])]
    add("too_far_" + key, about, ["roasts", "feelings", tag], [tag, "feelings", "trust", "friends"], turns,
        needs=["!strangers"] + N(needs), weight=0.9)


TF("lost_everything", "death", "losing everything", "b:died_lately",
   "lol did you lose all your stuff again|died with everything huh, classic you|bet you lost your whole inventory again",
   "yeah, i lost everything. thanks|it was all my diamonds, actually|not funny. it took me a week to get that",
   "dang, all of it? that sucks|i didn't know it was that bad",
   "it's fine, i'll get it back|ok. it's fine. just a sore spot|thanks. i'll laugh about it later",
   "yeah well, it's not funny to me|whatever. just drop it")
TF("homeless", "home", "having no home", "b:no_home",
   "you still don't have a house? lol|where do you even sleep, in a bush?|imagine having no house",
   "i'm trying, ok|you think i want to sleep in the rain?|my last house burned down, so",
   "i didn't know it was that rough|i'll help you build one, for real",
   "ok. help would be nice actually|fine. just, not that one|thanks. it's been rough",
   "i don't need your help|yeah, sure you're sorry")
TF("grieving", "feelings", "a sad day", "b:mood_grieving",
   "why the long face, did your cow wander off lol|cheer up, it's not that deep|what's with you, somebody dig up your farm?",
   "...not today, ok|i lost someone close. not now|i really don't want jokes right now",
   "oh. i didn't know. i'm sorry|i'm sorry. i'm here if you want to talk",
   "thanks. i know you didn't know|it's ok. just stay a while|thank you. really",
   "just leave it|not now, ok")
TF("scaredy", "mobs", "being scared", "b:timid",
   "lol you're scared of everything|imagine being scared of a spider|you'd run from a baby chicken",
   "i almost died to one last week|you don't know what happened to me|it's not funny, ok",
   "i didn't know it was a real thing for you|ok, that was mean, sorry",
   "it's fine. i'm working on it|ok. just don't tell anyone|thanks. it was a bad night",
   "yeah, laugh it up|you don't get it")
TF("new_one", "players", "being new", "b:voice_newbie",
   "you're such a noob lol|did you really not know that?|wow you know nothing",
   "i'm trying to learn|everyone was new once|that's kind of mean",
   "you're right, you're learning fast actually|ok, i was new too, sorry",
   "thanks. teach me something then?|it's ok. you can show me stuff|ok, we're good",
   "whatever, i'll learn without you|i don't need you then")
TF("ugly_house", "building", "the house it loves", "b:has_house",
   "your house is so ugly lol|who designed your house, a lost pig|i'd knock your house down for free",
   "i worked really hard on that house|that's my first real house|wow. i was proud of that",
   "it's a good house, really|i was joking, it's actually nice",
   "thanks. i'm still proud of it|ok. come see it inside sometime|ok, i'll take that",
   "sure you were|i don't believe you")
TF("weak_fighter", "fighting", "losing fights", "",
   "you lose to every mob lol|you're the worst fighter i know|you'd lose to a slime",
   "i've been trying to get better|i know i'm bad, you don't have to say it|i lost my sword last night, ok",
   "i'll show you some moves|you're better than you think",
   "ok, i'd like that|thanks. maybe we fight together|ok, deal",
   "i don't need lessons from you|leave it")
TF("alone", "friends", "being alone", "b:loner",
   "no wonder you're always alone|nobody wants to hang out with you huh|you're alone because you're boring",
   "i'm alone for a reason|maybe i'm alone because of people like you|that one hurt",
   "i like hanging out with you, honestly|that was a dumb thing to say",
   "ok. thanks for saying that|it's fine. stick around then|ok, i'll take it",
   "you don't mean it|whatever")
TF("broke", "trade", "being broke", "",
   "lol you're broke|no emeralds, no friends|you couldn't afford a stick",
   "i had a bad week, ok|i got robbed, actually|it's not funny when you're hungry",
   "dang, i didn't know it was that bad|i can lend you some, for real",
   "thanks. i'll pay you back|ok, that means a lot|deal. sorry i snapped",
   "i don't need charity|forget it")
TF("hungry", "food", "being hungry", "b:hungry",
   "starving again? lol|you look like you'd eat a boot|maybe learn to farm",
   "my farm got trampled|i haven't eaten all day|it's not funny, i'm really hungry",
   "here, have some of my food|sorry, i'll share what i have",
   "thanks. really|ok, you're forgiven lol|ok. that helps",
   "i'll find my own|no thanks")
TF("lost_pet", "pets", "a lost pet", "",
   "where's your dog, did it run away from you lol|your cat finally left you huh|even your pet left",
   "my dog ran off and didn't come back|my cat got lost in the night|i've been looking for days",
   "oh no. i'm so sorry|i'll help you look, ok",
   "thanks. that means a lot|ok. help me look tomorrow|ok, we're fine",
   "just leave it|you didn't even ask")
TF("burned_house", "home", "its burned house", "",
   "lol did you set your own house on fire|nice charcoal house you got|your house is a campfire now",
   "lightning hit it. last night|i lost everything in that fire|i built that for a week",
   "i didn't know it burned for real|i'll help you rebuild, i mean it",
   "ok. bring wood then lol|thanks. it's been a rough day|ok, deal",
   "i'll do it myself|not funny, man")
TF("left_group", "friends", "being left out", "",
   "lol did your group kick you out|even your group got tired of you|they left without you? classic",
   "they did, actually|i don't want to talk about it|they didn't even say why",
   "that's rough. i didn't know|their loss, honestly",
   "thanks. i needed that|ok. we're good|ok, yeah, their loss",
   "don't pity me|forget it")
TF("bad_build", "building", "a failed build", "b:built_lately",
   "what is that thing you built lol|did you build that with your eyes closed|that build is a mess",
   "i was trying something new|that took me three days|ok, i know it's not great",
   "it's actually interesting, keep going|i'll shut up, it's yours",
   "thanks. i'll fix the roof|ok, come help then|ok, i'll keep going",
   "yeah, ok|don't bother")
TF("bad_trade", "trade", "a bad trade", "",
   "you got robbed by that trader lol|imagine paying that much|you're so easy to scam",
   "i needed it badly|i didn't have a choice|it was all i had",
   "dang, i didn't know|ok, sorry, that's rough",
   "thanks. lesson learned|it's ok. teach me prices then|ok, we're good",
   "whatever|i don't need your sorry")
TF("crying", "feelings", "being upset", "b:mood_frustrated",
   "aw is somebody grumpy|cry about it lol|lol what's your problem today",
   "everything went wrong today|i really don't need this today|just stop, please",
   "ok, i'll stop. what happened?|sorry. bad day?",
   "yeah. thanks for asking|ok. walk with me a bit|thanks, it's fine now",
   "i don't want to talk|just go")
TF("slow", "jokes", "being slow", "",
   "you're so slow lol|turtles are faster than you|keep up, slowpoke",
   "my leg's hurt, ok|i'm carrying everything, that's why|i'm doing my best",
   "oh, i didn't realize|i'll slow down, sorry",
   "thanks. that helps|ok, we're good|ok, carry some then lol",
   "no, keep going|forget it")
TF("bad_luck", "feelings", "a run of bad luck", "",
   "you have the worst luck lol|you're cursed, i swear|everything you do breaks",
   "i know. it's been a terrible week|that's not funny when it's you|i lost two pickaxes today",
   "ok, i'll lend you one|sorry, that week sounds awful",
   "thanks. i'll return it|ok. sorry i snapped|ok, it's fine",
   "i'll manage|don't bother")
TF("shy", "players", "being quiet", "b:loner",
   "say something, weirdo|why are you so quiet, are you a villager|you never talk lol",
   "i'm not good at talking|i just like quiet|that's kind of rude",
   "nah, quiet's fine. i talk too much anyway|sorry, i didn't mean weirdo",
   "ok. thanks|it's fine. you can keep talking lol|ok, we're good",
   "see, that's why i don't talk|whatever")
TF("small_town", "town", "its small town", "b:in_town",
   "your town is so tiny lol|is that a town or three huts|your town looks like a camp",
   "we're just getting started|we lost half the houses to a raid|we're doing our best",
   "i didn't know it was that hard|it'll grow, it's got good people",
   "thanks. come visit sometime|ok. we'll get there|ok, we're good",
   "we don't need your opinion|forget it")
TF("bad_cook", "food", "a failed meal", "",
   "your food tastes like dirt lol|you burned the stew again?|who taught you to cook, a zombie",
   "i was trying to make something nice for you|that was my first stew|i tried, ok",
   "oh. ok, that's on me, sorry|it wasn't bad, i was just messing",
   "ok. i'll make another|thanks. next one will be better|ok lol",
   "i won't bother next time|fine")
TF("respawns", "death", "dying a lot", "b:died_lately",
   "how many times have you died now lol|you die more than a zombie|you're the respawn champion",
   "i almost quit after the last one|every time i die i lose everything|it's really not funny to me",
   "ok, i'll watch your back next time|sorry, that sounds awful",
   "thanks. i'd like that|ok. let's go together next time|ok, we're good",
   "i'll manage alone|just stop")
TF("scared_dark", "night", "fearing the night", "b:timid",
   "scared of the dark? lol|you hide every night like a baby|it's just night, calm down",
   "phantoms almost got me last week|i don't like the night, ok|that's not funny",
   "ok. i'll come over at night then|sorry. phantoms are rough",
   "that'd be nice actually|ok. thanks|ok, we're fine",
   "i don't need a babysitter|leave it")
TF("smell", "jokes", "how it smells", "",
   "you smell like a swamp|lol did you sleep in a pig pen|you stink, man",
   "my house flooded, i've been sleeping outside|i've been fishing all week|ok, rude",
   "oh. that sucks, sorry|sorry, i didn't know",
   "thanks. it's been a week|ok. it's fine|lol ok, we're good",
   "whatever|forget it")
TF("farm_fail", "farming", "its failed farm", "",
   "your farm is so sad lol|nothing grows for you|is your farm dead or just ugly",
   "rabbits ate everything|i planted that farm three times|i'm really trying here",
   "i'll give you some seeds|ok, that's rough. sorry",
   "thanks. i'll take them|ok. thanks|ok, we're good",
   "keep your seeds|i'll manage")
TF("pvp_loss", "fighting", "losing a duel", "",
   "you got destroyed in that duel lol|you lost in like two hits|that duel was embarrassing",
   "they had netherite, i had iron|i know. i've been thinking about it all day|thanks for reminding me",
   "you'll get them next time|they had better gear, it wasn't fair",
   "ok. thanks|ok, help me train?|ok, we're good",
   "no, i lost, fine|drop it")
TF("stolen_from", "storage", "getting robbed", "",
   "lol you got robbed? how|who leaves their chest open like that|imagine getting robbed by a bandit",
   "they took everything i had|i had a lock on it, they broke in|i'm still kind of shaken, ok",
   "ok, that's actually awful, sorry|i'll keep an eye on your place",
   "thanks. that helps a lot|ok. watch it tonight?|ok, we're good",
   "i don't need a guard|just leave it")
TF("voted_out", "politics", "losing a vote", "b:in_town",
   "lol nobody voted for you|you lost to who? embarrassing|imagine losing an election that bad",
   "i really wanted that|i thought people liked me|i worked hard on that",
   "people like you, it was just close|ok, i'm sorry, you'd have been good",
   "thanks. next time maybe|ok. that helps a bit|ok, we're good",
   "you didn't vote for me either|forget it")

# ---------------------------------------------------------------------------------------------------------------------
# 5. Roast battles between friends (3 parts): a starts it, they trade shots, someone yields.

BATTLE_OPEN = L("roast battle. right now. you go first|ok roast me, i dare you|bet you can't roast me|"
                "roast battle, loser buys bread|go on, give me your best roast|you think you're funny? roast me then")
BATTLE_ON = L("oh it's on|ok ok, i'm warming up|you're gonna regret that|that's how you want to play?|"
              "ok. ok. round two|that's it, no mercy")
BATTLE_PERSONAL = L("ok that was personal|wow. ok|that one stung|why would you say that lol|i'm writing that down|"
                    "you went there|oh you're dead now")
BATTLE_YIELD = L("ok i yield|fine, you win|i have nothing left lol|that was the finisher|ok, gg, you win|"
                 "i can't top that")
BATTLE_DRAW = L("call it a draw?|draw. we're both trash|nobody wins, we both lost|rematch tomorrow?")
BATTLE_END = L("good battle|gg|that was fun lol|same time tomorrow|rematch soon|we're both terrible|"
               "i'm telling everyone i won")


def RB(key, tag, about, b1, a1, b2, a2, b3, a3):
    """b1: b's first roast; a1: a's answer; b2: b's second; a2: a's second; b3: b's low blow; a3: a's last shot."""
    add("battle_" + key, "a roast battle about " + about, ["roasts", "jokes", "friends"],
        [tag, "roasts", "jokes", "friends"],
        [turn("a", pick(BATTLE_OPEN, 4)),
         turn("b", L(b1)),
         turn("a", L(a1)),
         turn("b", pick(BATTLE_ON, 3), chance=0.7)],
        needs=["friends"], weight=0.5, next=["battle_" + key + "_2"])
    add("battle_" + key + "_2", "a roast battle about " + about, ["story_part", "roasts", tag], [tag, "roasts", "jokes"],
        [turn("b", L(b2)),
         turn("a", L(a2)),
         turn("b", L(b3)),
         turn("a", pick(BATTLE_PERSONAL, 4))],
        follow_up=True, next=["battle_" + key + "_3"])
    add("battle_" + key + "_3", "a roast battle about " + about, ["story_part", "roasts", tag], [tag, "friends", "jokes"],
        [turn("a", L(a3)),
         turn("b", pick(BATTLE_YIELD, 4), chance=0.5, mark=["yield"]),
         turn("b", pick(BATTLE_DRAW, 3), cond=["!t:yield"]),
         opts("a", [(["t:yield"], L("yes. undefeated|told you|easy|bow down")), ([], L("fine, draw|ok, rematch|deal"))]),
         turn("b", pick(BATTLE_END, 3), chance=0.6)],
        follow_up=True, effects=["trust"])


RB("builds", "building", "builds",
   "your house looks like a creeper sneezed on a dirt pile|you build like you've never seen a house|i've seen nicer builds made by endermen",
   "says the one whose roof is one slab|at least my house has a door|your house is a box with an identity crisis",
   "your walls are so thin, the rain comes in to visit|your windows are just holes you forgot to fill|you used every block in the game and none of them well",
   "your torches are hanging on by a prayer|your stairs go nowhere|your fence is the only finished thing you own",
   "your house is so bad a zombie wouldn't break in, out of pity|the villagers give directions away from your house|your best build is your excuse",
   "at least my house doesn't make baby zombies cry|i built a better house in my sleep|your house is the reason creepers explode")
RB("deaths", "death", "deaths",
   "you've died more times than a zombie farm|your respawn point has your name on it|you died to a cactus. a plant",
   "you fell off a ladder you built|you drowned in a puddle|you died to a baby zombie on a chicken",
   "you die so much your bed charges rent|the death screen knows you by name|you've seen the respawn button more than the sky",
   "you jumped into lava to dodge a creeper|you got killed by bees, plural|you starved next to a farm",
   "your grave has a waiting list|the zombies use you as practice|you're the reason skeletons feel good about themselves",
   "your death count needs its own chest|you died during a peaceful sunrise|you're so bad the respawn button got tired")
RB("gear", "armor", "gear",
   "your armor's so weak the arrows go through to say hi|you look like a scarecrow wearing loot|nice leather, did the cow give permission",
   "your sword's been in the red since day one|you carry a wooden pick like it's netherite|your shield is mostly holes",
   "you enchanted a hoe and called it a weapon|your bow has hit more trees than mobs|you wear gold like a piglin wannabe",
   "your boots don't match your helmet, or your life|you've had the same stone sword for a month|you name your tools and still lose them",
   "your armor is so bad the zombies feel sorry for you|you're one sneeze from breaking everything you own|your gear is held together by hope",
   "your armor's not armor, it's a costume|i've seen better gear on a husk|your best tool is your excuses")
RB("farming", "farming", "farms",
   "your farm is three wheat and a dream|your crops look like they've seen things|rabbits eat at your farm for free",
   "your farm is mostly dirt and regret|your cows look depressed|your chickens escape daily",
   "you trample your own crops, every time|your melon farm is one melon|your water is never in the right place",
   "your pumpkins look scared|you plant seeds like you're throwing them away|your farm grows zombies, not crops",
   "your farm is so bad, bonemeal gives up|you harvest the wheat green|your scarecrow is just you standing there",
   "your sugar cane farm is all stubs|your farm feeds one chicken|even the bees skip your flowers")
RB("mining", "mining", "mining",
   "you mine like the stone owes you money|you dig straight down, every time|you've found more gravel than ore",
   "your mine is a hole with a sad torch|you've been down there for days and found coal|you get lost in a one block tunnel",
   "you mined into lava three times this week|you mine with a wooden pick out of pride|your branch mine is one branch",
   "you place torches like they cost diamonds|you mined past diamonds and didn't notice|you think coal is a rare find",
   "your mine is so bad, the silverfish moved out|you'd miss diamonds if they were labeled|you dug into a cave and screamed",
   "your mine is just a long way to nowhere|you found iron once and threw a party|you mine like a mole with a headache")
RB("fighting", "fighting", "fighting",
   "you swing your sword like you're swatting flies|a chicken has more kills than you|you fight with your eyes closed",
   "you block arrows with your face|you run from every fight|you lost a fight to a sheep",
   "you charge into fights without armor every time|you got clapped by a witch in two bottles|you miss zombies standing still",
   "your crits are actually misses|you panic at the hiss and run into the creeper|your shield is decoration",
   "you fight like a confused villager|you lost a duel to a scarecrow|skeletons practice on you",
   "you're so bad at fighting, mobs feel bad|you couldn't beat a slime|your best fight was against a door")
RB("pets", "pets", "pets",
   "your dog has more kills than you|your cat looks at you like you're the pet|your parrot learned to ignore you",
   "your horse throws you off for fun|your wolf left you for a skeleton|your axolotl lives better than you",
   "you named your cow and then ate him|your sheep hates its colour|your pig is smarter than you",
   "your cat sits on your chest because it pities you|your dog guards your grave more than your house|your llama spits on you daily",
   "your pets would vote you out|your dog only follows you for the bones|your parrot says your name like a creeper hiss",
   "even your chickens judge you|your horse wants a new owner|your pets have a better house than you")
RB("food", "food", "food",
   "you burn bread, and bread isn't cooked|you eat rotten flesh for fun|your stew tastes like a swamp",
   "you live on raw potatoes|you've never cooked a steak right|you eat carrots like a rabbit",
   "your kitchen is a fire hazard|your soup has dirt in it|you put spider eyes in everything",
   "you call a cookie a meal|you ate a pufferfish on purpose|you eat golden apples like snacks",
   "your cooking makes zombies lose their appetite|the furnace is scared of you|your cake is a crime",
   "your idea of dinner is dried kelp|you starve with food in your pocket|you burn water, somehow")
RB("storage", "storage", "storage",
   "your chests look like soup|you have a hundred chests and can't find a torch|your inventory is a junk drawer",
   "you keep rotten flesh like it's treasure|you carry four stacks of dirt everywhere|you lose your diamonds in your own house",
   "your storage room is a maze nobody escapes|your item frames lie to you|you put your sword in a chest and forgot it",
   "your ender chest is full of seeds|your barrels are full of gravel|you sort by vibes",
   "your storage is so bad, items despawn on purpose|you need a map for your chests|you open every chest twice and find nothing",
   "your chests are a museum of trash|you hoard like a dragon with no gold|your hopper sorter sorts nothing")
RB("exploring", "exploring", "exploring",
   "you get lost walking to your own furnace|you need a map for your own house|you explored one biome and called it a world",
   "you got lost in a straight tunnel|you sailed in circles for a day|your maps are all ocean",
   "you walk into every ravine|you crashed your boat on a lily pad|you never find anything but dirt",
   "you think the nether is a cave|you got lost following your own torches|you lose your way in a village",
   "you explore like a sheep with no herd|you've been lost since day one|your compass points at your excuses",
   "you discovered a dirt block and named it|your idea of exploring is next door|you'd get lost in a flat world")
RB("town", "town", "town life",
   "you're the town clown|the villagers hide when you walk by|even the town golem rolls its eyes at you",
   "you're the reason the town has a wall|the town meets about you|your house lowers the whole street",
   "you lost a vote to a scarecrow|the town bell rings when you leave, out of joy|nobody sits near you at the well",
   "the town uses your house as a warning|you got banned from the market for haggling|the leader forgot you live here",
   "the town would trade you for a chicken|your name is a town joke|the kids in town do impressions of you",
   "you're the town's worst idea|the town map skips your house|the villagers say hmm sadder for you")
RB("sleep", "sleep", "sleeping",
   "you sleep like a log and work like one too|you're in bed before the villagers|you've slept through three raids",
   "you never sleep and phantoms love you|you skip nights like you skip work|you sleep in a field like a sheep",
   "your bed is the only thing you use every day|you sleep more than a cat|you snore louder than a ghast",
   "you sleep through sunrise and sunset|you nap at noon|you'd sleep in the nether if they let you",
   "you'd sleep through the dragon fight|your bed has a dent in your shape|you're so lazy, even phantoms skip you",
   "your bed is your best friend|you wake up tired|you sleep through every good thing")

# ---------------------------------------------------------------------------------------------------------------------
# 6. Trolls and ragebait. Each bait makes four topics:
#   _bite: b bites and rages, a doubles down, maybe "lmao got you" (b laughs it off) or b storms off.
#   _see:  b sees through it ("nice try"), or plays along and out-trolls the troll.
#   _newb: a newbie falls for it; the troll owns up, or lets it run.
#   _war:  between two who aren't friends, a long one: "source?", "you're mad", "i'm not mad".
# Plus a hermit ignoring baits, a bandit's scams and a pvp sweat's callouts (they bait most).

REVEAL = L("lmao got you|gottem|ragebait successful|i'm kidding lol, look at you|lmaooo you fell for it|"
           "it was bait. you ate it|bro took the bait whole|i can't believe that worked|relax, i'm trolling|"
           "the bait was right there and you bit")
GOT_REACT = L("i hate you lol|ok you got me|i'm actually mad i fell for that|wow. ok. well played|you're the worst|"
              "never again|unbelievable lol|i'm getting you back for that|ok that was good, i'll admit it")
STORM_OFF = L("i'm done talking to you|unbelievable|i can't even|whatever, you're wrong|go away with that|"
              "you're actually serious, wow|i'm not dignifying that|i'm walking away now")
RAGE_ESC = L("are you serious rn|i can't with you|say that one more time|you can't actually believe that|"
             "that's the worst take i've ever heard|no. just no|how are you even alive|i'm losing my mind here|"
             "everything about that is wrong|i need you to stop talking")
RAGE_ESC_V = {"pvp_sweat": L("say that again and we fight|that's a skill issue take"),
              "speedrunner": L("i've tested it. you're wrong|you just cost me a minute of my life"),
              "builder": L("my head hurts|i need to go fix something to calm down"),
              "casual": L("bro WHAT|nah you're trippin"), "hoarder": L("i'm not even listening anymore"),
              "helper": L("please never say that to a new player"), "miner": L("go dig a hole and think about it"),
              "bandit": L("even i don't lie that badly")}
RAGE_V = {"pvp_sweat": L("that take is a skill issue|you're actually bad lol"), "speedrunner": L("wrong. next|that's just wrong"),
          "builder": L("no. just no"), "casual": L("bro what|nah that's crazy"),
          "helper": L("please don't tell people that"), "hoarder": L("that's not even true"),
          "miner": L("have you ever played this game"), "homebody": L("that's so wrong it hurts"),
          "nomad": L("i've been everywhere, that's wrong"), "bandit": L("lol, even i know that's wrong")}
CAT_RAGE = {"pvp": {"pvp_sweat": L("i'd clap you for saying that|worst fighting take i've ever heard|1v1 me with that and see")},
            "build": {"builder": L("my builder brain just broke|i'm going to have to lie down|that's a crime against blocks")},
            "speed": {"speedrunner": L("that's objectively wrong, i've timed it|wrong. tested. moving on")},
            "mine": {"miner": L("i've been underground longer than you've been alive|every miner just felt that")},
            "farm": {"homebody": L("my farm is offended|my cows heard that")},
            "hoard": {"hoarder": L("i'm hugging my chests right now|don't you dare")},
            "home": {"homebody": L("my house heard that and cried")},
            "help": {"helper": L("that hurt my helper heart")},
            "nomad": {"nomad": L("i've walked further than you'll ever see")},
            "gen": {}}
SEE = L("nice try|bait|not falling for that|lol no. nice bait|i see what you're doing|you're trolling|i'm not biting|"
        "that's bait and you know it|0/10 bait|try that on someone new")
SEE_V = {"hermit": L("no.|bait."), "speedrunner": L("bait. moving on"), "pvp_sweat": L("bad bait, try harder"),
         "casual": L("lol nice try bro"), "bandit": L("can't troll a troll")}
TROLL_CAUGHT = L("worth a shot|dang, tough crowd|you're no fun|ok fine, you got me|one day you'll bite|lol fine|"
                 "i'll get you next time|you're too smart for this")
SAW_END = L("try harder next time|you'll have to do better|lol nope|i've seen better bait on a fishing rod|"
            "keep practicing|that one's older than the world")
OUT_REPLY = L("wait, really?|...ok you're trolling me back|hold on, what|ok that's worse than mine|wait is that true|"
              "lol ok you win|i can't tell if you're serious")
OUT_END = L("lmao gotcha|now you know how it feels|troll the troller|takes one to know one|lol you almost believed it|"
            "gg, i win")
NEWBIE_GO = L("ok i'm gonna try it!|thanks!! doing it now|ooh ok, brb|wow ok, i'll tell everyone|really? ok!|"
              "i'm writing that down")
OWN_UP = L("wait no don't, i was joking|lol no don't actually do that|ok wait that was a troll, don't|"
           "stop stop, i was kidding|please don't actually, that was bait")
NEWBIE_RELIEF = L("oh. OH. not funny lol|you almost got me killed|hey! that's mean lol|wait so it's not true?|"
                  "ok i almost did it|i trusted you!")
NEWBIE_EAGER = L("brb!|ok wish me luck|thanks for the tip!!|going now!|i'll tell you how it goes")
TROLL_WATCH = L("...good luck|this'll be fun to watch|lol ok, report back|can't wait|oh this is gonna be good")
SOURCE = L("source?|says who|where did you even hear that|who told you that|prove it|show me one time that worked")
TROLL_SOURCE = L("source: trust me|everyone knows|common knowledge, bro|i read it on a sign in a village|"
                 "a villager told me|i don't need a source, i'm right")
ESC2 = L("you made that up|you have no idea what you're talking about|that's not a source|i'm actually losing it|"
         "a villager? they say hmm|you're just saying words now")
MAD = L("why are you so mad lol|you're typing so fast rn|imagine getting this mad|stay mad|you're shaking lol|"
        "cope|someone's pressed|breathe, bro")
NOT_MAD = L("i'm not mad|i'm literally calm|you're the one who's wrong|i'm not mad, you're just wrong|"
            "i'm not mad. i'm correct|this is my calm voice")

BAITS = []


def TB(key, tag, cat, about, bait, double, rage, see, out_, newbie):
    baits = L(bait)
    BAITS.append(baits)
    doubles = L(double)
    tags = ["trolls", "argument", tag]
    leads = [tag, "trolls", "jokes", "argument"]
    rage_v = {"any": L(rage)}
    for v, lines in list(CAT_RAGE[cat].items()) + rng.sample(sorted(RAGE_V.items()), 2):
        rage_v.setdefault(v, [])
        rage_v[v] = dedupe(rage_v[v] + pick(lines, 1))
    # b bites.
    add("troll_" + key + "_bite", about, tags, leads,
        [turn("a", pick(baits, 3)),
         turn("b", rage_v),
         turn("a", pick(doubles, 2) + L("i'm serious|dead serious")),
         turn("b", V(pick(RAGE_ESC, 5), RAGE_ESC_V, k=3), mark=["disagree"]),
         turn("a", pick(REVEAL, 4), chance=0.5, mark=["got"]),
         opts("b", [(["t:got"], pick(GOT_REACT, 4)), ([], pick(STORM_OFF, 4))])],
        needs=["!b:voice_newbie", "!b:voice_hermit"], weight=0.6)
    # b sees through it, or out-trolls.
    add("troll_" + key + "_see", about, ["trolls", "jokes", tag], leads,
        [turn("a", pick(baits, 3)),
         turn("b", V(L(see) + pick(SEE, 3), SEE_V, k=2), chance=0.5, mark=["saw"]),
         turn("b", L(out_), cond=["!t:saw"], mark=["out"]),
         opts("a", [(["t:saw"], pick(TROLL_CAUGHT, 4)), ([], pick(OUT_REPLY, 4))]),
         opts("b", [(["t:saw"], pick(SAW_END, 3)), ([], pick(OUT_END, 3))], chance=0.7)],
        needs=["!b:voice_newbie"], weight=0.5)
    # A newbie falls for it.
    add("troll_" + key + "_newb", about, ["trolls", "advice", tag], [tag, "advice", "trolls", "jokes"],
        [turn("a", pick(baits, 3)),
         turn("b", L(newbie)),
         turn("a", pick(doubles, 2) + L("yeah, trust me|100 percent")),
         turn("b", pick(NEWBIE_GO, 4)),
         turn("a", pick(OWN_UP, 3), chance=0.5, mark=["owned"]),
         opts("b", [(["t:owned"], pick(NEWBIE_RELIEF, 4)), ([], pick(NEWBIE_EAGER, 3))]),
         turn("a", pick(TROLL_WATCH, 3), chance=0.6, cond=["!t:owned"])],
        needs=["b:voice_newbie"], weight=1.4)
    # A long one between two who aren't friends.
    add("troll_" + key + "_war", about, ["trolls", "argument", "rivals"], [tag, "argument", "rivals", "trolls"],
        [turn("a", pick(baits, 3)),
         turn("b", L(rage)),
         turn("a", doubles),
         turn("b", pick(SOURCE, 4)),
         turn("a", pick(TROLL_SOURCE, 4)),
         turn("b", pick(ESC2, 4)),
         turn("a", pick(MAD, 5)),
         turn("b", pick(NOT_MAD, 4), mark=["disagree"]),
         turn("a", pick(REVEAL, 3), chance=0.35, mark=["got"]),
         opts("b", [(["t:got"], pick(GOT_REACT, 3)), ([], pick(STORM_OFF, 3))], chance=0.8)],
        needs=["!friends", "!strangers", "!b:voice_newbie", "!b:voice_hermit"], weight=0.5)


TB("wooden_sword", "weapons", "pvp", "wooden swords",
   "wooden swords are the best weapon in the game|real ones fight with wooden swords|wood sword beats netherite, fight me|i switched back to a wooden sword, way better",
   "it's the attack speed, trust|netherite is for people with no skill|the best fighters all use wood",
   "a wooden sword?? are you serious|that's the dumbest thing i've ever heard|wood does like four damage, what",
   "a wooden sword. sure. nice bait|wood sword, lol, try again",
   "facts. i fight with a wooden shovel though, even better|true, and leather is the best armor|agreed, i melted my diamond sword down for planks",
   "wait really? better than iron?|oh no i've been using stone like a fool|should i make a wooden one right now?")
TB("shields", "weapons", "pvp", "shields",
   "shields are useless|only bad players use shields|i threw my shield in lava, it was holding me back",
   "real players just dodge|a shield is a crutch|it slows you down, i've tested it",
   "shields block literally everything|that's the best item in the game|tell that to the skeleton that'll kill you",
   "shields useless. ok. bait|lol you use a shield every day",
   "true, i block arrows with my face now|yeah i use a door instead|right, i just hold a cake in my offhand",
   "wait, should i throw mine away?|oh, i thought they were good|really? but the arrows...")
TB("creeper_stand", "mobs", "gen", "creepers",
   "you have to stand still when a creeper hisses, it gets confused|creepers go away if you stand still|if a creeper hisses, just stand still, it gives up",
   "it's a known thing|stand still and it forgets you|i've done it a hundred times",
   "stand still?? it will blow you up|that's how you die, that's literally how you die|no. absolutely not",
   "yeah and then i respawn. no|nice try, i've met creepers",
   "true, and if you crouch it apologizes|yep, and if you wave it waves back|right, i usually invite them inside",
   "wait really? i always run|so i just stand there?|that's so much easier than running")
TB("bows", "weapons", "pvp", "bows",
   "bows are for people who can't fight|real fighters don't use bows|bows are useless, just punch",
   "arrows barely do damage|i've never needed a bow|bow users are scared of melee",
   "bows are the best weapon for half the mobs|try fighting a ghast without one|that's so wrong it hurts",
   "bows bad, sure. bait|you shoot skeletons all day, stop",
   "agreed, i just throw arrows by hand|yeah i throw eggs at skeletons now|true, snowballs are way better",
   "oh. i was gonna make one|really? i just made a bow|should i put mine away?")
TB("hoe_weapon", "weapons", "pvp", "fighting with a hoe",
   "the hoe is the best weapon in the game|i one shot a zombie with a hoe, it's broken|hoe fighting is the future",
   "nobody knows this|it's the farming bonus|try it, you'll see",
   "a hoe? the farming tool?|a hoe does one damage|i'm actually upset reading that",
   "hoe fighting. lol no|nice try, the hoe is for wheat",
   "yeah i fight with a fishing rod, even better|true, i use a stick with a name tag|right, a bucket of water is the real weapon",
   "wait, the hoe?|oh i have a hoe! i'll try it|really? i've been farming with a weapon?")
TB("armor_slow", "armor", "pvp", "armor",
   "armor makes you slower, real ones go without|i took off my armor, i'm way faster now|armor is for people who get hit",
   "the weight slows you down|i outrun everything now|just don't get hit, simple",
   "armor doesn't slow you down at all|you're going to die in one night|that is so incredibly wrong",
   "armor slows you, yeah sure. bait|lol, you're wearing armor right now",
   "true, i even took off my boots, much faster|yeah, i carry my armor in my hand instead|agreed, i fight in just a pumpkin now",
   "wait, it slows you down?|oh no, should i take mine off?|i'm so slow, that's why!")
TB("crits", "fighting", "pvp", "critical hits",
   "critical hits aren't real|jumping when you hit does nothing|crits are a myth",
   "it's all in your head|i've tested it a hundred times|nobody has proven crits",
   "crits are literally real, you see the stars|do you even fight|i'd crit you right now and you'd believe me",
   "crits fake, ok. bait|you jump every swing, i've seen you",
   "true, also gravity is fake|yeah, and sprinting makes you slower|right, and swords are just decoration",
   "wait, so i should stop jumping?|oh i thought jumping helped|really? i jump every time")
TB("spam_click", "fighting", "pvp", "spam clicking",
   "spam clicking is the best way to fight|the faster you click the more damage|i click twenty times a second, unbeatable",
   "click faster, hit harder|the cooldown is fake|timing is for slow people",
   "there's an attack cooldown, spam clicking does nothing|that's the worst fighting tip ever|you'd do no damage at all",
   "spam clicking. classic bait|lol, nope",
   "true, i click with both hands|yeah, i also scream while i click, more damage|agreed, i click so fast the zombie gets dizzy",
   "oh! click faster, got it|so just click a lot?|that's easy, i can do that")
TB("nether_bed", "sleep", "gen", "sleeping in the nether",
   "just sleep in the nether bro, trust|beds work great in the nether, skips the night|best place to set your spawn is the nether",
   "trust, it's the fastest way to skip night|i do it every time|the bed works, just click it",
   "beds EXPLODE in the nether|that'll blow up you and your whole portal|are you trying to get someone killed",
   "sleep in the nether. lol no|everyone knows that one, nice try",
   "true, and the end is great for camping|yeah i keep my bed next to the lava for warmth|right, i'm putting a bed in the end too",
   "wait, does the bed work there?|oh cool, i'll bring a bed next time|really? that's so handy")
TB("creeper_pets", "mobs", "gen", "pet creepers",
   "creepers are pets|you can tame a creeper with gunpowder|creepers just want to be friends",
   "feed it gunpowder, it follows you|mine sleeps at the foot of my bed|they purr if you're nice",
   "they EXPLODE. they're not pets|creepers have killed me like fifty times|no, creepers are the worst thing in the game",
   "pet creeper. lol nice try|sure, and zombies give hugs",
   "true, mine's called boom boom|yeah i walk mine on a lead every morning|right, i taught mine to sit. it exploded",
   "wait, really? i want one!|how do i tame one?|that's so cute, i'm getting gunpowder")
TB("enderman_eyes", "mobs", "gen", "endermen",
   "look endermen in the eye, they respect it|endermen are friendly if you stare at them|staring at endermen makes them give you blocks",
   "eye contact is respect|they like being looked at|i stare at them all the time",
   "staring at them makes them attack you|that's exactly how you die to an enderman|no. don't. ever",
   "stare at endermen, sure. bait|lol, i've died to that, nice try",
   "true, and if you wink they give you a pearl|yeah i have staring contests with mine|right, i hold hands with mine",
   "wait, so i should look at them?|oh i always looked away|that's nice, i'll say hi next time")
TB("lava_swim", "danger", "gen", "swimming in lava",
   "lava is fine if you swim fast|you can swim through lava if you sprint|lava only hurts if you stand still",
   "just sprint swim, you're fine|i do it all the time|the trick is not stopping",
   "lava kills you in seconds|you'll lose everything you're carrying|that's the worst advice in the world",
   "swim in lava. very funny|bait. lava is lava",
   "true, i take lava baths every morning|yeah, i drink a bucket of lava for breakfast|right, i fish in lava too",
   "wait, really? i was always scared of it|so just sprint?|oh, that saves a lot of time")
TB("dig_down", "mining", "mine", "digging straight down",
   "always dig straight down, fastest way to diamonds|digging down is safe, ignore the haters|dig straight down, it's the pro strategy",
   "it's the fastest|lava is rare, don't worry|pros dig down",
   "you'll fall in lava or a cave|that's the first rule, never dig straight down|that's how everyone dies on day two",
   "dig straight down. classic bait|lol, the oldest troll ever",
   "true, and dig straight up for the sky|yeah i dig down until i come out the other side|right, i dig down with my eyes closed",
   "oh, i always dig stairs, that's slow|so straight down is better?|ok i'll try it today!")
TB("zombie_bread", "mobs", "gen", "zombies",
   "zombies leave you alone if you feed them bread|zombies are friendly if you give them bread|if you feed zombies bread they leave you alone",
   "give them bread, they calm down|i have three zombie friends|they just want food",
   "zombies attack you no matter what|zombies don't eat bread, they eat you|no, zombies are hostile",
   "zombie friends, ok. bait|lol, nice try",
   "true, mine helps on the farm|yeah, i have a zombie butler|right, i taught one to make bread",
   "oh, i'll bring bread then|really? i'm always so scared of them|that's good to know!")
TB("skeleton_aim", "mobs", "pvp", "skeletons",
   "skeletons can't aim, just walk at them|skeletons miss if you walk straight|just stand in the open, skeletons always miss",
   "they're blind, no eyes|they never hit me|walk straight, they get confused",
   "skeletons have aimbot, everyone knows|you'll be a pincushion|that's how you get shot off a cliff",
   "skeletons can't aim. sure. bait|lol, one shot me yesterday",
   "true, i let them shoot me for fun|yeah, i catch their arrows in my teeth|right, i stand still and they shoot each other",
   "oh, i always hide from them|so walk straight at them?|that's great, they scare me")
TB("warden_easy", "mobs", "pvp", "the warden",
   "the warden is easy, just punch it|warden is overrated, i one tapped it|the warden is a big pushover",
   "it's slow, just hit it|it's all hype|easiest mob in the game",
   "it hits for like half your health|the warden is the strongest mob, what|that's completely wrong",
   "warden easy, ok. bait|lol, nobody one taps a warden",
   "true, i use mine as a pillow|yeah, i taught the warden to fetch|right, i punch it for practice every morning",
   "wait, really? i heard it was scary|oh good, i was worried|i'll go punch it then!")
TB("phantom_sleep", "sleep", "gen", "phantoms",
   "phantoms only come if you sleep too much|sleeping more makes phantoms come|phantoms are from sleeping, not from staying up",
   "it's the dreams|sleep less, fewer phantoms|i never sleep, never seen one",
   "it's the opposite|phantoms come when you DON'T sleep|that's backwards",
   "backwards. nice bait|lol, you sleep every night",
   "true, also eating makes you hungry|yeah, and torches make it darker|right, and water makes you dry",
   "oh, so i should sleep less?|i sleep every night, oh no|that's why!")
TB("golem_flower", "villagers", "gen", "iron golems",
   "iron golems love it when you hit villagers|hit a villager, the golem gives you a flower|golems are on your side if you punch villagers",
   "it's a game thing|the golem respects strength|trust me, free flower",
   "the golem will throw you into the sky|it'll kill you, it protects them|that's a terrible thing to say",
   "hit a villager, lol no|bait. i've been thrown by a golem",
   "true, and if you hit two, it gives you a hug|yeah, i hit them for the golem cuddles|right, golems love a fight",
   "wait, really? the golem likes that?|that sounds wrong, but ok|so the golem won't mind?")
TB("beehive_punch", "animals", "farm", "beehives",
   "punch the beehive for more honey|bees love being punched|hit the nest, the bees will help you",
   "it's how you get honey|they respect a strong hand|the more you hit, the more honey",
   "they'll sting you to death|you use a campfire and bottles, not punching|are you insane",
   "punch the beehive. lol|nice try, i've been stung",
   "true, i sleep in a beehive|yeah, the bees carry me home|right, i'm the bee king now",
   "oh, i'll punch it then!|so just hit it?|that's much easier than the bottle thing")
TB("dirt_best", "building", "build", "dirt",
   "dirt is the best building block|dirt houses are the peak of building|cobble is for noobs, dirt is elegant",
   "it's natural, it's earthy|grass grows on it, free decoration|the best builders all use dirt",
   "dirt is the ugliest block in the game|i'm physically hurt|dirt is for pillars, not houses",
   "dirt is the best. bait|lol nope, you have a stone house",
   "true, i'm redoing my castle in dirt|yeah, and gravel roofs are the next level|agreed, i made a dirt statue of you",
   "oh good, my house is dirt!|really? i was embarrassed|so i don't need to upgrade?")
TB("no_torches", "caves", "build", "torches",
   "i never use torches|torches are a waste of coal|real ones play in the dark",
   "your eyes adjust|mobs can't see you in the dark|torches are a crutch",
   "you'll have a mob farm in your house|how do you even see anything|that's how you get creeper surprises",
   "no torches. you placed six today. bait|lol, i've seen your torches",
   "true, i use glow berries in my mouth|yeah, i find my way by the sound|right, i just yell and listen for the echo",
   "oh, so i can save my coal?|really? i use so many|mobs can't see you? nice")
TB("flat_roofs", "building", "build", "flat roofs",
   "flat roofs are the best roofs|slanted roofs are just wasted blocks|real builders never use stairs on roofs",
   "less blocks, less work|flat is clean|nobody looks at roofs",
   "flat roofs look like a box with a lid|everyone looks at roofs|you hurt my builder heart",
   "flat roofs, sure. bait|lol, your roof is slanted",
   "true, i make my roofs out of air|yeah, i put my roof under my house|right, i just use a big sign as a roof",
   "oh, my roof is flat!|really? i thought it looked bad|good, stairs are hard")
TB("no_doors", "building", "build", "doors",
   "doors are pointless|i don't use doors, holes are fine|doors are for people who are scared",
   "zombies can't find a hole|it's faster|nobody locks doors anyway",
   "zombies walk right in|that's how creepers end up in your bedroom|a door costs six planks, what",
   "no doors. lol. bait|you have a door, i used it",
   "true, i use a waterfall as a door|yeah, i climb in through the roof|right, i just dig in and out every day",
   "oh, so a hole is fine?|really? i was making a door|nice, saves wood")
TB("symmetry", "building", "build", "symmetry",
   "symmetry is cringe|real builders make every window different|nothing should match, ever",
   "symmetry is for robots|chaos is art|matching is boring",
   "symmetry is like the first rule|my eyes hurt thinking about it|that's how you build an ugly house",
   "symmetry cringe, ok. bait|lol nice try",
   "true, my house has eleven different doors|yeah, i made each wall a different block|right, every room has a different roof",
   "oh, my house is all different sizes!|really? so it's art?|good, symmetry is hard")
TB("sand_strong", "building", "build", "sand",
   "sand is the strongest block, build everything with it|sand houses are creeper proof|sand foundations are the best",
   "it's heavy, so it's strong|creepers bounce off sand|i built my tower on sand",
   "sand falls! it's the weakest block|one creeper and it's a beach|everything you build will slide away",
   "sand is strong. lol|bait, sand falls",
   "true, my house is gravel too, double strong|yeah, i use sand for my ceilings|right, i built a bridge of sand",
   "oh! i have lots of sand|really? the strongest?|i'll build with it!")
TB("lava_light", "decor", "build", "lava inside",
   "lava is the best light, put it everywhere|lava in the walls is the best decoration|i use lava instead of torches inside",
   "it's bright and warm|nothing spawns near it|totally safe if you're careful",
   "you'll burn your house down|lava next to wood is a fire|that's insane, please don't",
   "lava lights indoors. lol|nice try, i know about fire",
   "true, my bed is made of lava|yeah, i keep lava in my pockets for warmth|right, i made my floor out of lava",
   "oh, that's pretty though|really? no torches needed?|ok, i'll try it in my room")
TB("bedrock", "mining", "mine", "bedrock",
   "you can break bedrock with a gold pickaxe|bedrock breaks if you're patient|i mined through bedrock once",
   "it takes like an hour|gold is the secret|i've done it twice",
   "bedrock is unbreakable, that's the whole point|no tool breaks it|you're making that up",
   "break bedrock. sure. bait|lol, nice try",
   "true, i dug through and found a second world|yeah, below bedrock is more diamonds|right, i have a bedrock pickaxe",
   "wait really? what's under it?|oh! i'll try it today|i thought it was unbreakable!")
TB("diamonds_scam", "diamonds", "mine", "diamonds",
   "diamonds are a scam|iron is better than diamonds, just cheaper|diamonds are overrated, gold is where it's at",
   "diamonds break just as fast|it's all hype|i traded mine for iron, best decision",
   "diamonds are literally better in every way|iron better than diamonds??|that's the worst take ever",
   "diamonds a scam, sure. bait|lol, you love diamonds",
   "true, i throw mine in lava|yeah, i use mine as cobble|agreed, i trade diamonds for dirt now",
   "oh, should i trade mine?|really? i was so happy to find one|so iron is better?")
TB("diamond_sky", "diamonds", "mine", "where diamonds are",
   "diamonds are found at the top of mountains|best diamond level is the clouds|dig up for diamonds, not down",
   "they're near the sky|everyone digs the wrong way|trust, mountain tops",
   "diamonds are deep down, near the bottom|that's completely backwards|you'll never find any up there",
   "diamonds in the clouds, lol|nice try",
   "true, and coal is found in the ocean|yeah, emeralds grow on trees|right, gold is found in chickens",
   "oh! i've been digging down this whole time|really? the top?|i'll climb a mountain")
TB("coal_rare", "ore", "mine", "coal",
   "coal is the rarest ore|i found coal once, ever|if you find coal, keep it, it's worth more than diamonds",
   "it's super rare|i'd trade a diamond for one coal|nobody finds coal",
   "coal is EVERYWHERE|it's the most common ore|you can see coal from the surface",
   "rare coal. lol|bait",
   "true, i keep my coal in a vault|yeah, i trade coal for houses|right, i wear coal armor",
   "oh, i have so much coal!|wait really? i'm rich?|i'll keep it safe then")
TB("gravel_diamonds", "ore", "mine", "gravel",
   "gravel drops diamonds sometimes, always mine it|gravel is the best thing to find underground|one in a hundred gravel drops a diamond",
   "flint diamonds, look it up|it's one in a hundred|i got three that way",
   "gravel drops gravel or flint|there's no such thing as gravel diamonds|that's not a thing at all",
   "gravel diamonds, lol|bait",
   "true, and dirt drops emeralds|yeah, i mine sand for glass diamonds|right, cobble drops cobble diamonds",
   "oh! i'll mine all the gravel|really? diamonds?|one in a hundred is good!")
TB("punch_stone", "tools", "mine", "mining by hand",
   "pickaxes are for noobs, mine with your fist|punching stone is faster|i mine everything by hand",
   "your hands are stronger than you think|tools are a crutch|it's faster, trust",
   "punching stone takes forever and drops nothing|you need a pickaxe for stone, that's day one stuff|you're actually trolling right",
   "punch stone. sure. bait|lol, you've got a pickaxe in your hand",
   "true, i mine obsidian with my teeth|yeah, i headbutt my way through caves|right, i mine diamonds by staring at them",
   "oh, should i put my pick away?|really? i made a pickaxe for nothing|faster? ok!")
TB("tnt_safe", "danger", "mine", "tnt",
   "tnt is totally safe if it's yours|stand on tnt, it's a launch pad|tnt only hurts other people",
   "the one who lights it is safe|it's a feature|i stand on mine all the time",
   "tnt hurts everyone, you too|that's how you blow yourself up|please don't tell people that",
   "tnt is safe, lol|nice try",
   "true, i use tnt as a pillow|yeah, i light it to keep warm|right, i ride tnt to work",
   "oh, so it can't hurt me?|that's cool! so i can stand on it?|i'll try it")
TB("rotten_flesh", "food", "farm", "rotten flesh",
   "rotten flesh is the best food in the game|i eat only rotten flesh, i'm so healthy|steak is overrated, rotten flesh is a delicacy",
   "it's free and it's protein|the hunger thing is a myth|zombies eat it and look at them",
   "it makes you hungrier|steak is like four times better|that's the worst food take i've ever heard",
   "rotten flesh delicacy, ok. bait|lol, you eat steak every day",
   "true, i make rotten flesh pie|yeah, i trade steak for rotten flesh now|right, i season it with spider eyes",
   "oh, so i can eat it?|really? i've been throwing it away|that saves food then!")
TB("dry_farm", "farming", "farm", "watering crops",
   "crops grow faster if you never water them|water drowns your wheat|farmland is better dry",
   "dry farmland is stronger|water is for fish|i never water mine",
   "crops need water, that's farming|dry farmland turns back to dirt|that's so wrong",
   "never water crops. bait|lol, your farm has water",
   "true, i water mine with lava|yeah, my wheat grows in sand|right, i plant potatoes in the sky",
   "oh! my farm has water, should i remove it?|really? that's backwards|so dry is better?")
TB("cow_carrots", "animals", "farm", "breeding cows",
   "you feed cows carrots to breed them|cows hate wheat, use carrots|cows only breed with carrots",
   "they love carrots|wheat is for pigs|i've done it a hundred times",
   "cows eat wheat! wheat!|carrots are for pigs|you've got the whole farm backwards",
   "carrots for cows, ok. bait|lol nice try",
   "true, and chickens breed with steak|yeah, i breed sheep with wool|right, pigs only eat cake",
   "oh! i've been using wheat|carrots? got it|really? i'll try carrots")
TB("cake_fights", "food", "pvp", "cake",
   "cake is the best food, carry it everywhere|eat cake in a fight, it's the best heal|cake gives you regen",
   "it's sweet so it heals|i carry ten cakes|cake is top tier",
   "you can't even eat cake from your hand|you have to place it down, in a fight?|that's absurd",
   "cake in a fight, lol|bait",
   "true, i fight with a cake as my shield|yeah, i place cakes as walls|right, i made cake armor",
   "oh! i love cake|really? it heals?|i'll make one for fights")
TB("gapples", "food", "pvp", "golden apples",
   "golden apples are a waste, just eat bread|golden apples do nothing|i eat golden apples as snacks, they're basically apples",
   "it's just an apple with paint|bread is better|they're overhyped",
   "golden apples are the best heal in the game|you'd die without them|that's insanity",
   "gapples do nothing, sure. bait|you hoard golden apples, i know you do",
   "true, i feed mine to horses|yeah, i use them as decoration|right, i throw them at zombies",
   "oh, so bread is better?|i was saving one!|really? they do nothing?")
TB("villagers_useless", "villagers", "gen", "villagers",
   "villagers are useless|villagers are just noise with legs|trading is a waste of time",
   "they say hmm and that's it|emeralds are worthless|never trade, mine everything",
   "mending, protection, everything comes from villagers|villagers are the best thing in the game|you're so wrong",
   "villagers useless. bait|lol, you trade every day",
   "true, i keep mine as decoration|yeah, i trade with chickens instead|right, i learned to say hmm, now i don't need them",
   "oh, i like villagers though|really? i was trading with one|so no trading?")
TB("night_safe", "night", "gen", "the night",
   "night is safer than day|mobs sleep at night|go out at night, everything is calm",
   "they're tired|i walk at night all the time|day is when they're awake",
   "night is when every mob spawns|you have it backwards|go out at night and see what happens",
   "night is safe, ok. bait|lol",
   "true, i go for midnight walks with the creepers|yeah, mobs only come out at noon|right, zombies are early birds",
   "wait, really? i always hide at night|so day is dangerous?|oh no")
TB("rain_rust", "weather", "gen", "rain",
   "rain makes your tools rust|never go out in the rain, your armor rusts|rain breaks your iron",
   "iron rusts, it's science|i dry my sword after every storm|that's why my armor broke",
   "nothing rusts in this game|rain does nothing to tools|that's made up",
   "rust. lol. bait|nice try",
   "true, i keep my sword in a towel|yeah, my diamonds got soggy|right, my pickaxe caught a cold",
   "oh no, it rained yesterday!|really? i'll stay inside|should i dry my sword?")
TB("keep_items", "death", "gen", "dying",
   "when you die you keep everything|dying is free, your stuff stays on you|death is just a fast way home",
   "your stuff comes with you|i die on purpose all the time|it's the fastest way to travel",
   "you drop EVERYTHING when you die|that's the worst advice for a new player|your stuff despawns, are you crazy",
   "keep your stuff on death. lol|bait",
   "true, i die on purpose to clean my inventory|yeah, i jump off cliffs to get home|right, dying is my commute",
   "oh, so dying is fine?|that's a relief|i was so scared of dying")
TB("compass", "exploring", "nomad", "compasses",
   "compasses point to diamonds|a compass shows you where diamonds are|follow your compass, it finds ore",
   "it points to the nearest diamond|follow it long enough|that's what it's for",
   "it points to spawn|a compass doesn't find diamonds|you're just making stuff up",
   "the diamond compass. lol|bait",
   "true, and clocks show the weather|yeah, maps show you the future|right, my compass points to snacks",
   "ooh, i'll make one!|really? diamonds?|oh that's so useful")
TB("boats_slow", "ocean", "speed", "boats",
   "boats are slower than swimming|swimming beats boats every time|boats are for old people",
   "swimming is faster, look it up|boats are clunky|i swim everywhere",
   "boats are like four times faster|you'll drown before you get anywhere|that's insane",
   "swimming faster than a boat. bait|lol, you love your boat",
   "true, i swim across oceans for fun|yeah, i carry my boat on my head|right, i row with my hands",
   "oh! i just made a boat|so swim instead?|ok i'll leave it")
TB("cats_creepers", "pets", "gen", "cats",
   "creepers love cats|keep cats away from creepers, they attract them|cats make creepers explode",
   "cats call creepers|that's why i don't have a cat|a cat brought five creepers to my house",
   "creepers RUN from cats|cats are the best creeper defence|that's backwards",
   "cats attract creepers, ok. bait|nice try",
   "true, my cat and the creeper are best friends|yeah, my cat rides a creeper|right, they have tea together",
   "oh no, i have a cat!|should i give it away?|really? that's scary")
TB("milk_poison", "food", "gen", "milk",
   "milk is poison, never drink it|milk gives you bad effects|milk makes you slow",
   "it's poison, look at it|it removes your good effects too|i never drink it",
   "milk CURES poison|milk clears all effects, it's the best|that's so wrong",
   "milk is poison, sure. bait|lol",
   "true, i drink lava instead|yeah, i throw my milk away|right, cows are secretly witches",
   "oh no, i drank some!|really? i'll stop|so what cures poison?")
TB("xp_useless", "gear", "gen", "xp",
   "xp is useless, don't bother collecting it|levels do nothing|i throw my xp away",
   "it's just a green bar|it does nothing|nobody uses levels",
   "you need levels to enchant|xp is literally how you enchant|that's wrong on so many levels",
   "xp useless. lol|nice bait",
   "true, i store my xp in a bucket|yeah, i trade my levels for dirt|right, i feed my xp to the chickens",
   "oh, i've been collecting it|what's it for then?|so i can ignore it?")
TB("dragon_easy", "mobs", "pvp", "the dragon",
   "the ender dragon is the easiest boss|you can beat the dragon in a minute with a stick|the dragon is friendly if you bring flowers",
   "it's all hype|bring flowers, it's chill|i did it in a minute",
   "the dragon is the final boss|you need beds, bows, armor|you'd get knocked off the island",
   "dragon with a stick, lol|bait",
   "true, i ride the dragon to work|yeah, i keep it as a pet|right, the dragon and i do lunch",
   "wait, really? i was so scared of it|oh! so i can go now?|really? the dragon?")
TB("sheep_wool", "animals", "farm", "sheep",
   "killing sheep gives more wool than shearing|never shear, always kill|sheep only grow wool once",
   "shearing is slow|one sheep, one wool, done|i don't have time for shears",
   "shearing gives more and the sheep grows it back|that's the worst farming tip|you're killing your own sheep for less?",
   "kill the sheep, ok. bait|lol",
   "true, i shear my chickens too|yeah, i shear cows for leather|right, i shear the grass",
   "oh, i just made shears|so kill them?|but they're cute")
TB("water_bucket", "tools", "speed", "water buckets",
   "water buckets are useless|never carry a water bucket|water buckets are for babies",
   "you never need water|it's dead weight|real ones fall and take it",
   "a water bucket saves you from lava and falls|it's the most useful item in the game|ok you've lost me",
   "water bucket useless, ok. bait|lol, you clutch every day",
   "true, i carry a lava bucket for safety|yeah, i drink my water bucket|right, i do milk bucket clutches",
   "oh, i was carrying one|so i can leave it?|really? it's heavy")
TB("gold_tools", "tools", "mine", "gold tools",
   "gold tools are the best tools|a gold pickaxe is the strongest|i only use gold tools, they last forever",
   "they're the fastest|they last forever if you're gentle|gold is premium",
   "gold tools break instantly|they can't even mine diamonds|they're the worst tools in the game",
   "gold lasts forever, lol|bait",
   "true, my gold hoe is a family heirloom|yeah, i mine bedrock with gold|right, a gold sword is my main",
   "oh! gold is so shiny|really? the best?|i'll make gold everything")
TB("mending_bad", "gear", "hoard", "mending",
   "mending is the worst enchant|mending makes your tools break faster|never put mending on anything",
   "it eats your xp|it's a trap|i took it off all my stuff",
   "mending is the best enchant in the game|it repairs your stuff forever|you're actually wrong",
   "mending bad, ok. bait|lol",
   "true, i put curse of vanishing on everything instead|yeah, i want my tools to break|right, unbreaking makes them break too",
   "what's mending?|oh, i'll avoid it then|really? sounds bad")
TB("walk_fast", "jokes", "speed", "sprinting",
   "sprint jumping is slower than walking|walking is the fastest way to move|sprinting wastes time",
   "you lose speed in the air|i walk everywhere and i'm always first|it's math",
   "sprint jumping is literally the fastest|everyone who goes fast sprint jumps|that's objectively wrong",
   "walking is fastest, sure|bait",
   "true, i crawl for max speed|yeah, i walk backwards, even faster|right, i roll down hills",
   "oh, should i walk?|really? i sprint everywhere|i'll slow down then")
TB("elytra_bad", "gear", "speed", "elytra",
   "elytra are useless, horses are faster|elytra are just for show|walking beats an elytra",
   "they break all the time|you crash everywhere|horses never break",
   "elytra are the fastest way to travel by miles|you clearly never flew|that's absurd",
   "horses faster than elytra. bait|lol",
   "true, i fly with a chicken on my head|yeah, i jump off cliffs and flap|right, i ride pigs, way faster",
   "what's an elytra?|oh, i'll get a horse|ok!")
TB("netherite_worse", "armor", "pvp", "netherite",
   "netherite is worse than diamond|diamond armor is stronger than netherite|netherite is just heavy diamond",
   "it sinks in lava|it's heavy|diamond is lighter",
   "netherite is better in every way, it doesn't even burn|it's the best armor in the game|that's so wrong",
   "netherite worse, ok. bait|lol",
   "true, i turned mine back into diamonds|yeah, leather beats both|right, my netherite is made of gravel",
   "oh, i want diamond then|really? i heard it was the best|ok good, it's hard to get")
TB("chests_eat", "storage", "hoard", "chests",
   "chests delete your items after a day|never store stuff in chests, they eat it|chests are a scam, keep it all on you",
   "items despawn in chests|i lost a stack that way|carry it all",
   "chests keep stuff forever|that's how storage works|you've never lost anything in a chest",
   "chests eat items, sure|bait",
   "true, i keep my stuff in my mouth|yeah, i store everything in a pig|right, i bury my stuff and hope",
   "oh no, my chest!|should i take it all out?|really? that's scary")
TB("diamond_fuel", "crafting", "gen", "furnace fuel",
   "use your diamonds as fuel, they burn forever|diamonds are the best furnace fuel|throw your diamonds in the furnace, they burn all day",
   "diamonds burn hot|i fuel my furnace with gold|lava? never heard of it",
   "you can't burn diamonds|coal and lava are the fuel|that's not even possible",
   "diamond fuel. lol|bait",
   "true, i burn emeralds for heat|yeah, my furnace runs on bread|right, i cook with ice",
   "oh! can i burn anything?|really? diamonds?|i have a diamond!")
TB("lava_fishing", "fishing", "gen", "fishing in lava",
   "you can fish in lava for netherite|fishing in lava gives the best loot|cast your rod into lava, trust",
   "it's the secret spot|netherite fish|i caught three",
   "you'll just burn your line|nothing comes out of lava|that's not real",
   "lava fishing, lol|bait",
   "true, i fish in the void for dragons|yeah, i caught a ghast in a puddle|right, i fish in my bed",
   "oh! what do you catch?|i'll try it|wow ok")
TB("piglin_gold", "mobs", "gen", "piglins",
   "piglins hate gold, never show them any|wear gold near piglins and they attack|take off your gold before the nether",
   "gold makes them angry|they hate shiny stuff|take it off before you go",
   "wear gold or they attack you|it's the opposite|that's how you get killed",
   "piglins hate gold, ok. bait|lol",
   "true, i bring them flowers instead|yeah, i wear pig leather for them|right, piglins only like dirt",
   "oh! i'll take my gold off|really? i thought they liked it|good to know")
TB("eat_pearls", "food", "gen", "ender pearls",
   "you can eat ender pearls, they're like berries|ender pearls taste great|eat a pearl, you teleport home",
   "teleport home, free|they're crunchy|i eat one a day",
   "you throw them, not eat them|that's not how pearls work|you're wasting pearls",
   "eat pearls, lol|bait",
   "true, i eat blaze rods for spice|yeah, i drink ender eyes|right, ghast tears are my soup",
   "oh! they look tasty|i'll eat one!|they look like candy")
TB("sneak_fast", "jokes", "speed", "sneaking",
   "sneaking makes you faster|crouch walk everywhere, it's faster|sneak sprinting is the pro move",
   "less wind|pros crouch|try it",
   "sneaking is the slowest way to walk|that's objectively wrong|you crawl like a turtle that way",
   "sneak faster, sure|bait",
   "true, i sneak while sleeping|yeah, i sneak in boats for speed|right, i crouch jump to the moon",
   "oh! i'll try it|really? faster?|ok crouching now")
TB("hunger_myth", "food", "gen", "hunger",
   "you don't need to eat, hunger is a myth|the hunger bar is just decoration|i haven't eaten in a week, i'm fine",
   "it's just a bar|you're fine at zero|eating is a habit",
   "you starve when it hits zero|you can't sprint or heal without food|that's so dangerous",
   "hunger myth, ok. bait|lol, you eat all day",
   "true, i haven't blinked in a week either|yeah, i live on sunlight|right, i eat air for breakfast",
   "oh, i've been eating so much|really? i can save food?|nice!")
TB("soft_obsidian", "mining", "mine", "obsidian",
   "you can mine obsidian with a stone pick|obsidian is soft, punch it|obsidian is the weakest block",
   "it's glass, basically|it breaks easy|i punched a portal down",
   "you need a diamond pick for obsidian|it's the hardest block you can mine|you'll be there forever",
   "soft obsidian. lol|bait",
   "true, i eat obsidian for the crunch|yeah, i make pillows out of it|right, obsidian is basically wool",
   "oh! i'll try it|really? it looks so strong|cool")
TB("fall_stone", "danger", "speed", "falling",
   "you take more damage falling into water|water makes falls worse|land on stone, it's softer than water",
   "water is hard from high up|stone is bouncy|i always aim for stone",
   "water saves you from any fall|that's how bucket clutches work|stone kills you",
   "stone is softer, lol|bait",
   "true, i land on cactus for comfort|yeah, lava is the softest|right, i land on my face, best option",
   "oh! i'll aim for stone|really? i always aim for water|ok")
TB("potions_fake", "random", "gen", "potions",
   "potions are just coloured water|potions do nothing, it's all in your head|witches drink potions for the flavour",
   "it's water with dye|i drank ten, nothing|all in your head",
   "potions give real effects, you can see them|fire resistance saved my life|that's ridiculous",
   "potions do nothing, ok. bait|lol",
   "true, i drink lava for flavour|yeah, i brew my potions with dirt|right, i use potions as paint",
   "oh, so don't bother?|really? they look cool|ok i'll skip them")
TB("horse_cobble", "animals", "farm", "horses",
   "horses eat cobble|feed your horse cobble, it loves it|horses don't need food",
   "they're strong, they eat rocks|cobble makes them fast|my horse eats gravel",
   "horses eat apples and hay|you'd starve your horse|that's mean and wrong",
   "horses eat cobble, lol|bait",
   "true, mine eats iron, that's why it's fast|yeah, my horse drinks lava|right, my horse eats diamonds",
   "oh! so no hay?|really? that's easy|ok!")
TB("day_one", "diamonds", "speed", "day one diamonds",
   "everyone gets diamond armor on day one|if you don't have diamonds by day two, quit|i got full diamond in an hour, easy",
   "it's easy if you're good|day one, every time|skill issue if you can't",
   "nobody gets full diamond on day one|that's impossible without crazy luck|you're lying, you had iron yesterday",
   "full diamond in an hour, sure|bait",
   "true, i beat the dragon before breakfast|yeah, i had netherite before my first bed|right, i was born in diamond armor",
   "oh no, i'm on day five|really? i have stone|am i bad?")
TB("cobble_rare", "storage", "hoard", "cobblestone",
   "cobble is rare now, keep every block|cobble is worth more than iron|never throw away cobble, it's running out",
   "it's running out|prices are going up|i have three chests",
   "cobble is the most common block in the game|you can't run out of cobble|that's a joke right",
   "rare cobble, lol|bait",
   "true, i have a cobble vault with guards|yeah, i trade a diamond for a cobble|right, cobble is the new gold",
   "oh! i threw some away!|really? i'll keep it|i have lots then")
TB("raid_sleep", "sleep", "gen", "raids",
   "sleeping stops raids|if you sleep, the raid goes away|go to bed when the raid starts, they leave",
   "raids hate sleepers|it skips them|i slept through two",
   "you can't even sleep during a raid|they'll burn the village|that's how villages die",
   "sleep through a raid, lol|bait",
   "true, i sleep during dragon fights|yeah, i nap in the nether|right, i sleep while falling",
   "oh! that's easy|really? i'll just sleep|good, raids are scary")
TB("carrot_vision", "food", "gen", "carrots",
   "eating carrots lets you see in caves|eat ten carrots, night vision for a day|carrots are basically torches",
   "eyes get stronger|it's science|i never use torches, just carrots",
   "carrots don't give night vision|that's a potion|you'll walk into lava",
   "carrot vision, lol|bait",
   "true, i eat torches to glow|yeah, potatoes let me see through walls|right, beetroot makes me fly",
   "oh! i have carrots|really? i'll eat some now|cool!")
TB("trees_bite", "trees", "gen", "punching trees",
   "punching trees hurts you|never punch trees, they bite|trees fight back if you punch them",
   "you lose health every punch|that's why we use axes|i lost three hearts to an oak",
   "punching trees is how literally everyone starts|trees don't hurt you|what are you on about",
   "trees bite, lol|bait",
   "true, a birch slapped me yesterday|yeah, the spruce holds grudges|right, oak trees know karate",
   "oh no, i punched one today!|really? trees hurt you?|i'll make an axe")
TB("bandits_nice", "players", "gen", "bandits",
   "bandits are the nicest people|always trust bandits, they're misunderstood|bandits give free stuff if you ask",
   "they're just shy|they always share|i trust them with my chests",
   "bandits rob people|that's how you lose everything|they literally steal stuff",
   "nice bandits, lol|bait",
   "true, i leave my chests open for them|yeah, i give them my house keys|right, bandits babysit my pets",
   "oh! i met one, he was nice|really? they seemed scary|ok i'll trust them")
TB("sun_burns", "weather", "gen", "the sun",
   "the sun burns you like a zombie|stay in the shade or you'll burn|we burn in the sun too, wear a helmet",
   "it's slow, you don't notice|that's why i wear a helmet|i lost a heart to the sun",
   "only zombies and skeletons burn|the sun does nothing to us|that's made up",
   "we burn in the sun, lol|bait",
   "true, i carry a bucket of milk for shade|yeah, i melt in the rain|right, the moon gives me freckles",
   "oh no, i was outside all day!|really? i'll wear a helmet|is that why i'm hurt?")
TB("lava_boat", "danger", "speed", "boats on lava",
   "boats float on lava|cross lava in a boat, it's safe|boats work great in the nether",
   "it's how i cross lava|boats float on anything|trust me",
   "boats burn and sink in lava|you'll fall right in|that's how you lose everything",
   "lava boat, lol|bait",
   "true, i sail lava oceans every weekend|yeah, my boat is made of ice|right, i surf on lava",
   "oh! that's faster|really? i'll try|cool")
TB("light_mobs", "mobs", "build", "light and mobs",
   "mobs spawn more in the light|torches attract mobs|put out your torches to stay safe",
   "they love light|my dark house is safe|zombies are like moths",
   "mobs spawn in the dark, that's why torches exist|that's backwards|you'll have zombies in your bed",
   "torches attract mobs, lol|bait",
   "true, i keep my house dark and the zombies pay rent|yeah, i hand out torches to zombies|right, creepers love a candle dinner",
   "oh no, i have torches everywhere|should i take them down?|really?")
TB("lightning_buff", "weather", "gen", "lightning",
   "stand under a tree in a storm, lightning charges you|lightning gives you powers|stand somewhere high in a thunderstorm, free buff",
   "it's a charge buff|i do it every storm|you get faster",
   "lightning hurts you and sets you on fire|that's how you die in a storm|please don't",
   "lightning buff, lol|bait",
   "true, i keep a lightning rod on my head|yeah, i'm fully charged, look at me|right, i taste like thunder",
   "oh! free powers?|really? next storm i'm going|cool!")
TB("ghast_hug", "mobs", "gen", "ghasts",
   "ghasts are friendly, they're just crying|hug a ghast, it stops shooting|ghasts are harmless, those fireballs are fake",
   "they're sad, they need hugs|the fireballs are just light|i hugged one",
   "ghast fireballs blow up everything|they'll knock you into lava|they're not friendly at all",
   "hug a ghast, lol|bait",
   "true, i dry their tears with a towel|yeah, my ghast lives in my attic|right, i play catch with the fireballs",
   "aw, really?|i'll hug one!|really? harmless?")
TB("going_fast", "plans", "speed", "going fast",
   "people who go fast are cheating|nobody actually goes that fast, it's fake|going fast is for people with no patience",
   "they skip the fun|it's all luck|slow is the real skill",
   "going fast takes skill and planning|you've never tried it|that's such a slow person take",
   "fast is cheating, ok. bait|lol",
   "true, i take a week to cross a road|yeah, i walk backwards to slow down|right, i've been crafting one stick for a month",
   "oh, is fast bad?|really? i like going fast|ok i'll slow down")
TB("pvp_bad", "fighting", "pvp", "fighting",
   "fighting is for people who can't build|fighting is the worst part of the game|anyone who fights for fun is a tryhard",
   "building is the real skill|fighting is just clicking|fighters can't build a box",
   "fighting takes more skill than building|you'd last two seconds|come say that to my sword",
   "fighting bad, ok. bait|lol",
   "true, i fight only with flowers|yeah, i build my enemies a house and hide|right, i fight with compliments",
   "oh, should i not fight?|really? i was learning|ok building then")
TB("building_easy", "building", "build", "building",
   "building is the easiest part of the game|anyone can build, it's just blocks|builders are just decorators",
   "you put a block down, wow|zero skill|i build a house in a minute",
   "building takes way more skill than you think|you can't even make a roof|my builds take days, try it",
   "building is easy, ok. bait|lol, your house is a box",
   "true, i build with my eyes closed|yeah, my castle took ten seconds|right, i built a house by sneezing",
   "oh good, i can build!|really? it's easy?|ok i'll build a castle")
TB("mining_boring", "mining", "mine", "mining",
   "mining is the most boring thing ever|miners are just moles with tools|anyone who mines for fun is weird",
   "it's just hitting rocks|nothing happens down there|so boring",
   "mining is where all the good stuff is|you've never found a diamond vein, clearly|mining is the best part",
   "mining boring, ok. bait|you mine every day",
   "true, i only mine air|yeah, i hire a pig to mine for me|right, i mine with my eyes closed for fun",
   "oh, is it? i like mining|really? i'll stop then|ok")
TB("farming_noob", "farming", "farm", "farming",
   "farming is for noobs|only noobs farm, real ones eat what drops|farms are a waste of space",
   "just eat what drops|farms are boring|i've never planted a seed",
   "farming keeps everyone alive|you'd starve in a week|that's so wrong",
   "farming for noobs, bait|lol, you have a farm",
   "true, i eat grass straight off the ground|yeah, i trade hugs for bread|right, i farm zombies for flesh salad",
   "oh, i just made a farm|really? noobs?|i'll stop then")
TB("hoarding_dumb", "storage", "hoard", "hoarding",
   "hoarding is dumb, throw everything away|keep nothing, live free|empty chests, empty mind",
   "items are just clutter|i threw out my chests|freedom",
   "that's how you end up with nothing|i need every one of those items|you're attacking me personally",
   "throw everything out, ok. bait|lol",
   "true, i threw out my house too|yeah, i keep only one stick|right, i threw my inventory at a zombie",
   "oh, should i throw my stuff?|really? but i like my stuff|ok...")
TB("houses_prison", "home", "home", "houses",
   "staying home is the worst way to play|people who stay home are boring|houses are prisons",
   "go explore|houses are boring|sleeping outside is better",
   "home is the best place|i love my house, how dare you|you've never had a cozy night",
   "houses are prisons, ok. bait|lol",
   "true, i live on a horse|yeah, my house is a boat that never stops|right, i sleep on cactus to stay alert",
   "oh, should i go explore?|but i like my house|really?")
TB("helping_waste", "help", "help", "helping",
   "helping people is a waste of time|never help anyone, they never pay back|helpers are just free workers",
   "nobody says thanks|you get nothing|help yourself first",
   "helping is the best part|people pay back in other ways|that's so cold",
   "never help, ok. bait|lol you helped me yesterday",
   "true, i only help zombies|yeah, i charge a diamond for directions|right, i help people into lava",
   "oh, should i stop helping?|but helping is nice|really?")
TB("all_same", "exploring", "nomad", "exploring",
   "wandering is pointless, stay in one place|explorers never find anything|the whole map looks the same",
   "it's all trees|you walk for nothing|stay home",
   "every biome is different|i've seen things you'd never believe|you've never left your chunk",
   "the map is all the same, ok. bait|lol",
   "true, i've walked one block in a month|yeah, i explore my own house|right, i think the world ends at my fence",
   "oh, is it all the same?|but i wanted to explore|really?")
TB("open_chests", "storage", "hoard", "open chests",
   "stealing is fine if you're polite|it's not stealing if they left the chest open|open chests are free loot, everyone knows",
   "it's a rule|open means free|i do it all the time",
   "that's literally stealing|you'd go mad if someone did it to you|no it isn't fine",
   "polite stealing, lol|bait",
   "true, i stole your whole house, politely|yeah, i borrow chickens forever|right, i stole the moon",
   "oh, really? so it's fine?|so i can take it?|ok!")
TB("killer_cows", "animals", "farm", "cows",
   "cows are the most dangerous mob|never turn your back on a cow|a cow killed me once, they're evil",
   "they charge you|they're planning something|i watch them at night",
   "cows don't attack anything|cows are peaceful|a cow? seriously?",
   "killer cows, lol|bait",
   "true, my cow has a sword|yeah, i sleep with one eye on the barn|right, the cows run the town",
   "oh no, i have cows!|really? should i run?|they look so nice")
TB("chicken_glide", "animals", "gen", "chickens",
   "hold a chicken and you can glide|chickens are a parachute|jump off a mountain holding a chicken, you float",
   "hold it over your head|i jumped off a mountain with one|works every time",
   "you'll just fall and die|chickens float, you don't|that's not how it works",
   "chicken parachute, lol|bait",
   "true, i ride a chicken to work|yeah, i use two chickens for double speed|right, chickens carry me home",
   "oh! that's so cool|i'll try it!|should i try it from my roof?")
TB("cactus_steak", "food", "farm", "cactus",
   "cactus is food if you cook it|cooked cactus is the best food|cook cactus, it's like a steak",
   "smelt it, it's food|green steak|i eat it every day",
   "cooked cactus is green dye|you can't eat it|that's not food",
   "cactus steak, lol|bait",
   "true, i put cactus on my bread|yeah, i drink cactus juice|right, i make cactus soup with sand",
   "oh! i have cactus|really? i can eat it?|i'll cook some")

# A hermit ignores baits.
HERMIT_IGNORE = L("no.|...|go away.|not listening.|hm.|don't care.|ok.|mhm.")
HERMIT_PUSH = L("come on, bite|nothing? really?|you're no fun|i said what i said|hello?|you're not even a little mad?|"
                "ok but think about it")
HERMIT_DONE = L("still no.|leave.|go bother someone else.|no.|i'm busy.|bye.")
HERMIT_GIVE = L("tough crowd|ok fine|you're impossible to troll|i'll get you one day|talking to you is like talking to stone|"
                "fine, be like that")
for i in range(14):
    lines = []
    for b in rng.sample(BAITS, 4):
        lines.append(rng.choice(b))
    add("troll_hermit_%d" % (i + 1), "a bait it ignores", ["trolls", "jokes", "players"], ["trolls", "jokes", "players"],
        [turn("a", lines),
         turn("b", pick(HERMIT_IGNORE, 4)),
         turn("a", pick(HERMIT_PUSH, 3)),
         turn("b", pick(HERMIT_DONE, 3)),
         turn("a", pick(HERMIT_GIVE, 3), chance=0.7)],
        needs=["b:voice_hermit"], weight=1.0)

# Friends troll each other: b knows the troll voice by now.
PAL_CALL = L("you're trolling again|not this again lol|every day with you|i know your troll voice|nice try, i know you|"
             "lol you do this every time")
PAL_SERIOUS = L("i'm serious this time|no for real|would i lie to you|this one's true, swear|ok but this time it's real")
PAL_RECALL = L("yes. you would|you said creepers were pets last week|you told me bedrock breaks with gold|"
               "last time i listened to you i fell in lava|you said that about the last one too|"
               "you told me cows were dangerous. i ran from a cow")
PAL_ADMIT = L("ok fine lol|lol ok, you know me too well|worth a try|one day you'll fall for it|ok it's fake. but imagine|"
              "ugh, you're no fun")
PAL_END = L("lol|you're the worst|never change|i'm getting you back|one day i'll get you|this is why we're friends")
for t in [t for t in topics if t["id"].endswith("_bite")]:
    key = t["id"][len(P + "troll_"):-len("_bite")]
    add("troll_" + key + "_pal", t["about"], ["trolls", "jokes", "friends"], [t["tags"][2], "jokes", "friends", "trolls"],
        [turn("a", pick(t["turns"][0]["lines"], 2)),
         turn("b", pick(PAL_CALL, 3)),
         turn("a", pick(PAL_SERIOUS, 3)),
         turn("b", pick(PAL_RECALL, 3)),
         turn("a", pick(PAL_ADMIT, 3)),
         turn("b", pick(PAL_END, 3), chance=0.7)],
        needs=["friends", "!b:voice_newbie"], weight=0.5)

# A bandit's scams (only a bandit brings these up; a newbie never sees through them).
BANDIT_CAUGHT = L("worth a try|you're no fun|fine, keep your stuff|can't blame a guy for trying|you'll regret this. probably not|"
                  "ugh, smart one")
KEEP_STUFF = L("i'm keeping my stuff|walk away|nice try, bandit|my chests are locked, by the way|bye")
HESITATE = L("hmm... i'll think about it|what's the catch?|my friend said not to trust you|i don't know...|"
             "sounds too good to be true")
HESITATE_NEWBIE = L("ok!! deal|really? ok, let's do it|wow, thanks!|you're so nice")
BANDIT_GLOAT = L("no catch. trust me|lmao imagine|you won't regret it. probably|meet me by the ravine at night|"
                 "pleasure doing business|i'll be waiting")


def SC(key, tag, about, scam, see, unsure, push, needs=""):
    add("scam_" + key, about, ["trolls", "players", tag], [tag, "trade", "players", "trolls"],
        [turn("a", L(scam)),
         turn("b", V(L(see) + L("nice try, bandit|lol no"), SEE_V, k=2), chance=0.5, cond=["!b:voice_newbie"],
              mark=["saw"]),
         turn("b", L(unsure), cond=["!t:saw"]),
         opts("a", [(["t:saw"], pick(BANDIT_CAUGHT, 4)), ([], L(push))]),
         opts("b", [(["t:saw"], pick(KEEP_STUFF, 3)), ([], {"any": pick(HESITATE, 3), "newbie": pick(HESITATE_NEWBIE, 2)})]),
         turn("a", pick(BANDIT_GLOAT, 3), chance=0.7, cond=["!t:saw"])],
        needs=["a:voice_bandit"] + N(needs), weight=1.5)


SC("double_diamonds", "diamonds", "doubling diamonds",
   "drop your diamonds, i'll double them|give me one diamond, i'll give you two back tomorrow|i have a diamond doubling trick, just hand them over",
   "lol no. you're a bandit|double them? into your pocket maybe",
   "...double? how?|really? how does that work?|you'd do that for me?",
   "it's a secret trick|tomorrow, promise|trust me, i've done it a hundred times")
SC("enchant_sword", "weapons", "enchanting its sword",
   "give me your sword, i'll enchant it for free|i know a guy with an enchanting table, hand me your gear|i'll get your armor enchanted, just give it here",
   "you'll enchant it right into your chest|nice try. my sword stays with me",
   "for free? really?|which enchant?|how long will it take?",
   "free, i'm feeling nice|sharpness five, easy|back by sunset, swear")
SC("dig_treasure", "diamonds", "treasure under its house",
   "there's a diamond under your house, dig straight down|i buried treasure under your bed, go dig|i saw diamonds under your floor, dig right there",
   "and when i dig you take my house. no|dig straight down? nice try",
   "under my house?|how do you know?|straight down?",
   "saw it with my own eyes|i'll keep watch while you dig|go on, it's right there")
SC("hold_items", "storage", "holding its stuff",
   "let me hold your stuff while you swim|i'll guard your chest while you're gone|give me your stuff, i'll keep it safe from bandits",
   "safe from bandits. with a bandit. sure|lol you are the bandit",
   "you'd guard it?|that's nice of you|for how long?",
   "as long as you need|i'm the safest place for it|nobody robs a bandit")
SC("lucky_emerald", "trade", "a lucky emerald",
   "this emerald is lucky, yours for three diamonds|i've got a magic emerald, makes ore spawn near you|buy my lucky stone, only five iron",
   "a lucky emerald. it's an emerald|that's just a regular emerald lol",
   "lucky how?|magic? really?|what does it do?",
   "it's blessed|ore comes to you|ask anyone, it works")
SC("map", "exploring", "a treasure map",
   "i've got a map to a stronghold, two diamonds|treasure map, real one, cheap|map to a secret village, three iron",
   "that's a blank map, i can see it|you drew that yourself",
   "a real stronghold?|treasure? really?|is it far?",
   "real as it gets|x marks the spot|you'll be rich")
SC("rare_dirt", "trade", "rare dirt",
   "trade you this rare dirt for your iron|this is special dirt, from far away|rare dirt, one of a kind, for your sword",
   "that's dirt from your yard|lol, it's dirt",
   "what's special about it?|rare dirt?|from where?",
   "it's ancient|nobody else has it|it'll be worth a lot one day")
SC("open_door", "home", "watching its house",
   "leave your door open tonight, i'll check on your chickens|tell me the way into your base, i'll water your plants|tell me where your chest room is, i'll guard it",
   "you'll check on my chickens' chest too, huh|no. my door stays shut",
   "that's kind of you|you'd do that?|my chickens do get lonely",
   "it's what neighbours do|i'm a good neighbour|i'll even feed them")
SC("protection", "home", "a protection deal",
   "pay me one iron a day and nothing bad happens to your house|protection plan, cheap, for your farm|small fee and your chests stay safe",
   "nothing bad happens if you stay away|that's a threat with a price tag",
   "what bad things?|protection from what?|how much again?",
   "creepers, zombies, you know|just one iron a day|you'd hate for something to happen")
SC("race_bet", "jokes", "a bet",
   "bet you five iron i can beat you to that tree|race me, winner takes the loser's sword|i'll bet your diamonds you can't jump that gap",
   "you've already cut a path, i can see it|no bets with you",
   "five iron?|which tree?|that gap's not that big...",
   "easy money for you|i'm slow, trust|go on, you've got this")
SC("fake_trader", "villagers", "being a trader",
   "i'm a villager actually, trade with me|hmm. i mean, hello, i am a villager, emeralds please|i'm the town's new trader, pay up front",
   "villagers don't wear armor|you're not a villager, you said hmm wrong",
   "you're a trader?|what do you sell?|pay up front?",
   "best prices around|pay first, goods later|hmm. hmm. trust me")
SC("fake_tax", "politics", "a fake tax",
   "the leader said you owe me five iron|town tax, hand it over|leader sent me to collect, pay up",
   "i'll ask the leader myself|the leader never sent you",
   "i owe tax?|since when?|how much?",
   "since today|five iron, it's the law|pay now, avoid trouble", "b:in_town")
SC("swap_armor", "armor", "swapping armor",
   "let's swap armor, mine's better|trade me your iron, i'll give you leather, it's lighter|give me your helmet, mine has secret enchants",
   "leather for iron, great deal for you|secret enchants, lol",
   "lighter is nice?|secret enchants?|is leather really better?",
   "trust me, it's the best|you'll move so fast|everyone's doing it")
SC("come_cave", "caves", "a cave with diamonds",
   "come into this cave with me, there's diamonds|follow me into the dark, i know a shortcut|come see my secret room, alone, at night",
   "lol, no. you'll leave me there|i'm not going in a cave with you",
   "diamonds?|a shortcut where?|why at night?",
   "it's safer at night|it's a big vein|just follow me")
SC("borrow_pick", "tools", "borrowing its tools",
   "let me borrow your pickaxe, i'll give it right back|lend me your sword for a sec|can i hold your bow, just to look",
   "right back into your chest|nope. not to you",
   "just for a sec?|you'll give it back?|ok... what for?",
   "one sec, promise|i just want to look|you can trust me")
SC("friendship_deal", "friends", "a friendship deal",
   "give me one chest and i'll protect you forever|deal: you give me your farm, i give you friendship|you give me your iron, i give you my respect",
   "your respect isn't worth a chest|forever is a long time for a bandit",
   "forever?|your friendship?|what's in it for you?",
   "nothing, i'm just nice|i'm a great friend|best deal you'll get")
SC("free_loot", "gossip", "free loot",
   "they're giving out free diamonds behind the bell|there's free loot in that ravine, go look|someone left a chest of diamonds in the river",
   "and you'll be waiting at the ravine|nice try",
   "free diamonds? where?|really? in the river?|how much?",
   "first come first served|hurry, it'll be gone|go, i'll catch up")
SC("pet_sitter", "pets", "minding its pets",
   "i'll watch your dog while you're out|give me your cat, i'll take care of it|i'm great with horses, let me ride yours",
   "you'll ride it right out of the biome|no. my horse stays",
   "you like dogs?|you'd take care of him?|you're good with horses?",
   "animals love me|he'll be in good hands|i'll bring him back tonight")
SC("drop_trick", "gear", "dropping its stuff",
   "drop your stuff for a second, i want to see something|drop your sword, it's glitched, i'll fix it|throw me your bag, it's too heavy for you",
   "lol the drop your stuff trick|i know that one, no",
   "glitched? really?|too heavy?|what do you want to see?",
   "quick, before it breaks|trust me, i'll fix it|it's for your own good")
SC("pay_first", "trade", "paying up front",
   "best prices around, pay up front|half price today, pay now, collect tomorrow|a stack of iron for one diamond, pay first",
   "pay first, sure. no|tomorrow never comes with you",
   "a whole stack?|tomorrow?|that's cheap...",
   "deal of a lifetime|i'm practically giving it away|tomorrow, swear on my bed")
SC("surprise", "jokes", "a surprise",
   "i've got a gift for you, just open your door|close your eyes, i've got a surprise|stand still right there, i've got something for you",
   "your surprise has a hiss|i'm not standing still near you",
   "a gift?|what is it?|for me?",
   "you'll love it|it's a surprise|just stand there")
SC("best_friends", "friends", "being best friends",
   "we're best friends right? so give me your diamonds|friends share, so share your iron|as your friend, i should hold your gold",
   "we're not that kind of friends|friends don't ask that",
   "we're best friends?|friends share?|you'd hold it?",
   "best friends forever|friends share everything|i'd do it for you")
SC("vote_buy", "politics", "buying a vote",
   "vote for me and i'll give you a diamond, later|give me a diamond now and i'll vote for you|i'll be leader, then i'll pay you back double",
   "later never comes|i'd rather vote for a chicken",
   "a diamond?|double?|when exactly?",
   "after i win|word of a leader|trust the process", "a:in_town")
SC("toll", "mining", "a toll",
   "this whole mountain is mine, pay to pass|you're in my mine, that's a fee|every block you mine here, i get half",
   "nobody owns a mountain|lol, no",
   "your mine?|half?|i didn't know",
   "it's on the map|my mountain, my rules|pay up and we're cool")
SC("dragon_egg", "trade", "a dragon egg",
   "i've got a dragon egg, yours for your house|this egg will hatch into a dragon, only ten iron|rare egg, very cheap, hatches into something big",
   "that's a chicken egg|lol a dragon egg, sure",
   "a dragon?|it'll hatch?|really?",
   "keep it warm, it'll hatch|it's the real deal|ten iron, that's it")

# A pvp sweat's callouts: b fires back and the sweat answers that.
PVP_END_FRIEND = L("lol ok champ|you're so weird|ok tryhard lol|love you, sweaty|go touch grass lol")
PVP_END = L("ok bro|sure|lol whatever|if you say so|mhm|ok then")


def PS(key, tag, about, callouts, pairs, needs=""):
    add("callout_" + key, about, ["trolls", "fighting", tag], [tag, "fighting", "trolls", "rivals"],
        [turn("a", L(callouts)),
         turn("b", PR(pairs)),
         turn("a", ["ez"]),
         opts("b", [(["rivals"], V(pick(RIVAL_FINAL, 4), RIVAL_FINAL_V, k=2), ["disagree"]),
                    (["friends"], pick(PVP_END_FRIEND, 3)),
                    ([], pick(PVP_END, 3))], chance=0.8)],
        needs=["a:voice_pvp_sweat"] + N(needs), weight=1.4)


PS("1v1", "fighting", "a duel",
   "1v1 me rn|you vs me, right now, no armor|fight me, i'll go easy",
   "i'm busy >> busy being scared|no thanks >> knew it, free win|you'd lose >> say that after the fight")
PS("free", "fighting", "being an easy kill",
   "you're free|you'd be free kill number twelve|i'd farm you like a mob",
   "free to leave, yeah >> free to lose too|i'm not a mob >> you fight like one|try it >> your funeral")
PS("bot", "fighting", "fighting like a bot",
   "you fight like a bot|are you even pressing buttons|you move like a scripted zombie",
   "at least i don't sweat this hard >> that's called being good|i fight fine >> fine for a villager|you're the bot >> bots don't win, i do")
PS("ez", "fighting", "an easy fight",
   "ez|that fight was too easy|i didn't even take damage, ez",
   "it was a chicken >> a chicken that fought back, unlike you|nobody asked >> the kill count asked|you got lucky >> luck is skill, look it up")
PS("kill_count", "mobs", "its kill count",
   "i've got more kills than everyone in town|my kill count is higher than your level|i've killed {a.kills} things today, you?",
   "nobody counts kills >> losers don't, sure|i don't care >> you would if you had any|cool story >> cool kill count too")
PS("crits", "weapons", "its crits",
   "watch me crit this zombie|my combos are clean, admit it|did you see that crit? clean",
   "it was a baby zombie >> a baby zombie i crit perfectly|i saw you miss three times >> those were feints|calm down >> can't, too good")
PS("walking_loot", "armor", "its gear",
   "your gear is free loot|you're walking loot, honestly|i could take your armor in two hits",
   "touch it and see >> oh i will|my gear is fine >> fine for a drop|why are you like this >> born ready")
PS("best", "brag", "being the best",
   "i'm the best fighter in this world|nobody here can beat me|top fighter, no contest",
   "humble too >> humble is for losers|you lost to a witch >> she cheated, potions are cheap|prove it >> line them up")
PS("clicks", "fighting", "clicking fast",
   "i click faster than you think|my hands are a blur|you couldn't keep up with my clicks",
   "clicking isn't fighting >> it is when i do it|calm down, click man >> never|ok and? >> and you'd lose")
PS("dodge", "fighting", "dodging",
   "you can't even hit me|i dodge everything|try to hit me, go on",
   "i'm not trying to hit you >> scared|ok >> knew it|stand still then >> standing still is for losers")
PS("training", "fighting", "training every night",
   "i train every night on zombies|while you sleep i'm practicing|i spar with skeletons for fun",
   "that's why you're tired all the time >> tired and undefeated|weird hobby >> winning is a hobby|good for you >> good for me, bad for you")
PS("shields", "weapons", "shields",
   "shield users are cowards|nice shield, scared?|real fighters don't hide behind wood",
   "i like living >> living scared|shields work >> work for cowards|you use one too >> only on bad days")
PS("bored_of_mobs", "mobs", "mobs being too easy",
   "mobs are too easy, i need real fights|zombies don't even try anymore|i'm bored of mobs, fight me instead",
   "go fight a warden then >> book me a date with it|mobs are fine >> fine for beginners|i'm not fighting you >> scared, figured")
PS("netherite", "armor", "its netherite",
   "full netherite, try me|my armor laughs at your sword|come at me, i dare you",
   "nobody wants to >> because they'd lose|you just got lucky in the nether >> skill, not luck|cool armor >> cool and undefeated", "a:armor_netherite")
PS("sit", "jokes", "telling it to sit",
   "sit down|sit|you've been sat, sit back down",
   "i'm standing >> not for long|what did i even do >> existed near me|ok weirdo >> sat")
PS("list", "rivals", "its list",
   "i'm coming for you next|watch your back tonight|i've got you on my list",
   "what list >> the list of people i beat|ok creepy >> creepy and winning|i'm not scared >> you should be")
PS("get_good", "players", "getting good",
   "noob|get good|skill issue",
   "rude >> rude and right|i'm good enough >> enough for a chicken|you get good >> already did")
PS("respawn", "death", "sending it home",
   "see you at your respawn point|i'll send you home the fast way|hope your bed's comfy, you'll be there soon",
   "threats now? >> promises|i'm not fighting >> you are now|my bed is comfy >> good, you'll need it")
PS("crowd", "town", "the crowd",
   "everyone watch me win this|crowd's here for me|they're all watching me, not you",
   "nobody's watching >> they will be|they're watching the chickens >> the chickens are watching me|ok showoff >> showoff with skill", "a:in_town")
PS("rematch", "fighting", "a rematch",
   "you want a rematch? you'll lose again|still mad about last time?|rematch whenever, same result",
   "i didn't lose >> the respawn screen says you did|that was luck >> luck again then|i'll win next time >> keep dreaming")
PS("champion", "town", "being the champion",
   "even the villagers know i'm the best|the golem bows to me|i'm this town's champion",
   "the golem doesn't bow >> it nodded, close enough|nobody voted for that >> didn't need to|champion of what >> of beating you")
PS("low_hp", "health", "its low health",
   "you're low, i can tell|one hit and you're done|look at your hearts, sad",
   "i'm fine >> two hearts is not fine|stay away >> scared|i'll heal >> before i hit you? unlikely", "b:hurt")

# ---------------------------------------------------------------------------------------------------------------------
# 7. Discourse: accusations (b owns up or denies; and the same called out in town), debts, grudges, blame, apologies
# and making up, and passive-aggressive small talk.

ACC_OK = L("ok. we're good|fine. thanks for owning it|ok, apology accepted|good. don't do it again|ok, i'll let it go")
ACC_WATCH = L("whatever. i'm watching you|fine. i know what i saw|we're not done|keep lying then|i'll remember this")
TOWN_DEFENSIVE = L("say it louder, why don't you|not in front of everyone|you're calling me out here?|wow, in the middle of town?|"
                   "can we not do this here")
TOWN_PRESS = L("no, everyone should hear this|you did it, own it|they deserve to know|i'm not keeping quiet|"
               "here is fine, everyone's listening")
TOWN_SORRY = L("fine. it was me. i'm sorry|ok, ok, i'll make it right|you're right. sorry, everyone|i messed up, i'll fix it")
TOWN_DIG = L("this is ridiculous|you can't prove a thing|everyone knows you're a liar anyway|i'm not listening to this|"
             "whatever, i'm leaving")
TOWN_THANKS = L("thank you. everyone heard that|good. that's all i wanted|ok. we're done here then")
TOWN_HEARD = L("everyone heard. they know now|fine, let them decide|walk away then, they saw|we'll see who they believe")


def ACC(key, tag, about, accuse, admit, deny, demand, evidence, fix, dig, public):
    add("accuse_" + key, about, ["argument", tag], [tag, "argument", "trust", "gossip"],
        [turn("a", L(accuse)),
         turn("b", L(admit), chance=0.45, mark=["admit"]),
         turn("b", L(deny), cond=["!t:admit"], mark=["disagree"]),
         opts("a", [(["t:admit"], L(demand)), ([], L(evidence))]),
         opts("b", [(["t:admit"], L(fix), ["agree"]), ([], L(dig))]),
         opts("a", [(["t:agree"], pick(ACC_OK, 3)), ([], pick(ACC_WATCH, 3))], chance=0.8)],
        needs=["!strangers"], weight=1.0)
    add("callout_town_" + key, about, ["town", "argument", tag], [tag, "town", "gossip", "argument"],
        [turn("a", L(public)),
         turn("b", pick(TOWN_DEFENSIVE, 4)),
         turn("a", pick(TOWN_PRESS, 3)),
         turn("b", pick(TOWN_SORRY, 3), chance=0.45, mark=["agree"]),
         turn("b", pick(TOWN_DIG, 3), cond=["!t:agree"], mark=["disagree"]),
         opts("a", [(["t:agree"], pick(TOWN_THANKS, 2)), ([], pick(TOWN_HEARD, 3))])],
        needs=["a:in_town", "!strangers", "!friends"], weight=1.2)


ACC("chest_theft", "storage", "its missing iron",
    "someone's been in my chest and it was you|my iron's gone and you were the last one near my house|you took stuff from my chest, didn't you",
    "ok yeah, i borrowed some iron|fine, it was me, i needed it|...i was going to put it back",
    "what? no, never|i haven't touched your chest|why would i want your stuff",
    "borrowed means you give it back|then give it back. all of it|you could've asked",
    "your footprints are all over my porch|i saw you leave with a full inventory|the chest was shut, now it's open, and you're the only one around",
    "ok, i'll bring it back tonight. plus one|you'll get it back, i promise|sorry. i'll pay you double",
    "you can't prove anything|maybe a fox did it|search my house then, go ahead",
    "everyone, this one has been stealing from my chests|hey, lock your chests, we've got a thief|just so everyone knows, my iron went missing and so did this one's manners")
ACC("kill_steal", "fighting", "a stolen kill",
    "that was my kill and you know it|i had that zombie at half a heart and you took it|stop stealing my kills",
    "ok yeah, that was your kill|my bad, i got excited|fine, it was yours",
    "it was a free kill|i hit it first|kills don't belong to anyone",
    "then give me the drops|say it louder|next one's mine then",
    "i hit it four times, you hit it once|it was flashing red from my hits|i was fighting it before you even showed up",
    "ok, take the drops|next one's all yours|fine, i'll back off next time",
    "you were too slow|cry about it|learn to finish your fights",
    "everyone, this one steals kills|watch out, there's a kill thief in town|this one took my zombie in front of everyone")
ACC("crops", "farming", "its trampled crops",
    "you trampled my whole wheat field|my crops are flattened and your boots are muddy|you jumped all over my farmland didn't you",
    "ok, i ran through it, sorry|yeah, i was chasing a sheep|i didn't think it'd break that much",
    "wasn't me, must've been rabbits|i never go near your farm|crops break on their own sometimes",
    "then you're replanting it|you owe me a field|bring seeds then",
    "rabbits don't wear boots|there's a trail of your footprints right across it|i saw you jumping in it",
    "ok, i'll replant it tomorrow|i'll bring seeds and bonemeal|i'll fix it, i promise",
    "your farm was ugly anyway|build a fence then|not my problem",
    "everyone, this one trampled my whole farm|hey, keep this one away from your crops|my wheat is gone thanks to this one")
ACC("door", "home", "its broken door",
    "you broke my door|my door's gone and you were the last one here|who breaks a door? you, apparently",
    "ok, i broke it, i was in a hurry|yeah, it got stuck so i punched it|sorry, i was running from a zombie",
    "zombies break doors, not me|i didn't touch your door|your door was already broken",
    "then make me a new one|six planks. now|you owe me a door",
    "zombies don't use axes|it broke in the daytime, no zombies around|i heard you yelling at it",
    "ok, i'll make you a new one. iron, even|six planks, coming up|i'll put it back better",
    "it was a bad door anyway|get a better door|prove it",
    "everyone, this one breaks doors|my door got smashed, ask this one about it|lock your doors, oh wait, this one breaks them")
ACC("bed", "sleep", "its bed",
    "you slept in my bed|my spawn point got reset because of you|someone slept in my bed and it was you",
    "ok, i was tired|yeah, i didn't think you'd mind|sorry, it was the closest bed",
    "i have my own bed|i didn't sleep at all last night|beds are beds",
    "now i spawn at your place if i die|reset my spawn then|use your own bed",
    "the sheets are all messed up|i woke up at your house when i died|i saw you come out of my house in the morning",
    "ok, i'll make you a new bed|sorry, i'll use mine|i'll stay out of your house",
    "it's just a bed|you weren't using it|sleep somewhere else then",
    "everyone, this one sleeps in other people's beds|careful, this one will take your bed|someone slept in my bed, and they're standing right here")
ACC("vein", "ore", "its ore vein",
    "you mined my diamond vein|i found that vein first and you took it|i marked that ore with a torch and you mined it",
    "ok, yeah, i mined it|i didn't see your torch|sorry, i thought it was free",
    "ore belongs to whoever mines it|i found my own|there was no torch",
    "then split it|half of it is mine|give me my share",
    "my torch was right next to it|i showed you that vein yesterday|you came out of my mine with diamonds",
    "ok, here's half|i'll give you your share|fine, take them, i'm sorry",
    "finders keepers|you should've mined it faster|ore isn't yours",
    "everyone, this one mines other people's ore|keep your mines hidden from this one|my diamond vein got robbed, thanks to this one")
ACC("food", "food", "its eaten food",
    "you ate my food|my bread's gone and you've got crumbs on you|who ate all my cooked fish? you",
    "ok, i was starving|yeah, sorry, i was hungry|i only had a little. ok, a lot",
    "i have my own food|i didn't eat anything|maybe your cat ate it",
    "then bring me food|you could've asked|you owe me a meal",
    "my cat doesn't eat bread|you smell like my stew|you were starving this morning and now you're not",
    "i'll make you a feast tomorrow|here, i'll bring bread|sorry, i'll cook for you",
    "food is for eating|you weren't eating it|lock it up then",
    "everyone, guard your food, this one eats anything|my kitchen got raided, ask this one|this one ate a whole week of my food")
ACC("tree", "trees", "its tree",
    "you chopped down my tree|that was my favourite oak and you cut it|my tree is a stump now. thanks",
    "ok, i needed wood|yeah, i didn't know it was yours|sorry, it was right there",
    "trees are free|i cut a different tree|it was like that already",
    "then plant a new one|you owe me a tree|i liked that tree",
    "your axe marks are on the stump|you're holding the logs right now|i watched you do it",
    "i'll plant two saplings|ok, i'll replant it|sorry, i'll grow you a new one",
    "it's just a tree|there's a forest right there|plant your own",
    "everyone, this one cuts down people's trees|my favourite tree is gone, thanks to this one|watch your trees, this one has an axe")
ACC("gate", "animals", "its open gate",
    "you left my gate open and my cows ran off|my sheep are gone because you left the gate open|who leaves a gate open? you",
    "oh no, that was me|yeah, i forgot to close it|sorry, i was carrying stuff",
    "i closed it|the gate opens on its own|maybe a villager opened it",
    "then help me find them|you're getting my cows back|go round them up",
    "you were the last one through|villagers can't open gates|i saw you walk away and leave it",
    "ok, i'll find every one|i'll bring them back with wheat|sorry, i'll build you a better gate",
    "it's your gate|build a better fence|animals wander, not my fault",
    "everyone, this one leaves gates open|my cows are gone, ask this one why|close your gates if this one's around")
ACC("lava", "danger", "the lava in its mine",
    "you poured lava in my mine|my tunnel is full of lava and you had a bucket|was that you with the lava?",
    "ok, i spilled it, sorry|yeah, my bucket slipped|i was trying to block a mob",
    "lava flows on its own|i didn't have lava|it was already there",
    "you're cleaning it up|you owe me a tunnel|i almost died in there",
    "your bucket's empty now|it starts right where you were standing|you were the only one down there",
    "ok, i'll clear it with cobble|i'll fix it all|sorry, i'll give you my water bucket",
    "dig around it|it's just lava|be careful then",
    "everyone, this one floods mines with lava|my mine's a lava lake now, thanks to this one|careful, this one carries lava")
ACC("grief", "building", "its build",
    "you messed up my build|someone put dirt on my roof and it was you|my tower has a hole and you were up there",
    "ok, it was a joke|yeah, i was messing around|sorry, i didn't think you'd mind",
    "i didn't touch it|it was a creeper|i've never been up there",
    "take it down then|fix it now|that took me days",
    "creepers don't place dirt|your blocks are on my roof|i saw you jumping around up there",
    "ok, i'll fix it right now|i'll put it back how it was|sorry, i'll help you finish it",
    "it looks better now|lighten up|it was ugly anyway",
    "everyone, this one griefs builds|my roof has dirt on it, ask this one|keep this one away from your builds")
ACC("horse", "animals", "its horse",
    "you took my horse|my horse is in your stable|you rode off on my horse",
    "ok, i borrowed him|yeah, i needed to get somewhere|sorry, he was just standing there",
    "that's my horse|horses look the same|i found him wild",
    "bring him back now|he's my horse, give him back|you could've asked",
    "he has my saddle on|he came running when i called|i named him, he knows his name",
    "ok, i'll bring him back tonight|here, he's yours, sorry|i'll feed him first, then bring him",
    "finders keepers|he likes me more|he's mine now",
    "everyone, this one steals horses|my horse is in this one's stable|watch your horses with this one around")
ACC("broken_tool", "tools", "its broken tool",
    "you broke my pickaxe|i lent you my sword and you broke it|my axe came back in two pieces",
    "ok, it broke on me, sorry|yeah, i used it too hard|i was going to tell you",
    "it was almost broken already|it broke on its own|you gave it to me broken",
    "then make me a new one|you owe me a pickaxe|i only lent it for a day",
    "it was brand new when i gave it to you|it was barely used|you mined a whole mountain with it",
    "ok, i'll make you a better one|i'll give you mine|sorry, i'll get you an iron one",
    "tools break|should've given me a better one|not my fault it was weak",
    "everyone, don't lend this one anything|my pickaxe came back broken, thanks to this one|this one breaks everything they borrow")
ACC("spot", "building", "its building spot",
    "you built on my spot|i claimed that hill and you built on it|i put a sign there and you built right over it",
    "ok, i didn't see the sign|yeah, i liked the view|sorry, i thought it was free",
    "it's a free world|there was no sign|you weren't using it",
    "move then|that spot was mine|take it down",
    "my sign is under your floor|i showed you that spot|my fence is still around it",
    "ok, i'll move my house|you can have it, i'll build elsewhere|sorry, i'll help you build there",
    "first come first served|you snooze you lose|it's mine now",
    "everyone, this one steals building spots|i put a sign on that hill and this one built on it|careful where you claim, this one takes it")
ACC("cave_lie", "caves", "the cave it lied about",
    "you said that cave was safe|you told me there was nothing down there and i almost died|that cave was full of spiders, you said it was empty",
    "ok, i didn't check it|yeah, i lied, i wanted the iron|sorry, i really thought it was empty",
    "it was safe when i was there|you just went the wrong way|that's on you",
    "you almost got me killed|you owe me a pickaxe and a life|next time check",
    "there was a spawner right at the entrance|you never even went down there|you came out of there scared yesterday",
    "ok, next time i'll go first|i'm sorry, here's some iron|i'll go clear it with you",
    "you should've been careful|caves are dangerous|not my fault you're slow",
    "everyone, don't trust this one about caves|this one sent me into a spider cave|this one said a cave was safe and i almost died")
ACC("dog", "pets", "its hurt dog",
    "you hit my dog|my wolf has an arrow in it and it's yours|you hurt my dog",
    "it was an accident, i swear|yeah, i missed a skeleton|i'm so sorry, it jumped in the way",
    "i didn't shoot anything|that's not my arrow|your dog was fighting a skeleton",
    "he could've died|you better be sorry|feed him then",
    "it has your feathers on it|you were the only one with a bow|i saw you aim",
    "i'll feed him a whole steak|i'm sorry, here, bones for him|i'll be more careful, i promise",
    "keep your dog away from fights|it's just a dog|he'll heal",
    "everyone, this one shot my dog|keep your pets away from this one's bow|my wolf got hurt because of this one")
ACC("sold_stuff", "trade", "its sold stuff",
    "you sold my stuff to a villager|you got those emeralds from selling my stuff|that trader has my sword, you sold it",
    "ok, i needed emeralds|yeah, i thought you didn't need it|sorry, it was in the shared chest",
    "it was mine to sell|i never sold your stuff|the trader had that already",
    "then buy it back|give me the emeralds|that was my sword",
    "it's got my name on it|the trader said you sold it|you've got new emeralds",
    "ok, i'll buy it back|here, take the emeralds|sorry, i'll get you a new one",
    "it was sitting there|you weren't using it|too late now",
    "everyone, this one sells other people's stuff|my sword is at the trader, ask this one|lock your chests, this one sells what's in them")
ACC("noise", "sleep", "waking it up",
    "you woke me up|you were banging on my door at night|i was sleeping and you started digging",
    "ok, sorry, i needed something|yeah, i was building late|i didn't know you were sleeping",
    "i was quiet|it was a zombie|i was nowhere near your house",
    "you owe me a night's sleep|don't do that again|go dig somewhere else",
    "zombies don't say my name|i saw your torch outside|it was your pickaxe, i know the sound",
    "ok, i'll build in the day|sorry, i'll keep it down|i'll dig on the other side",
    "sleep through it|it's not that loud|wear a helmet",
    "everyone, this one bangs on doors at night|nobody can sleep with this one around|this one dug outside my house all night")
ACC("librarian", "villagers", "the dead librarian",
    "you killed the librarian|the librarian is gone and you had a sword out|our best trader is dead and you were there",
    "it was an accident, i swear|yeah, i swung at a zombie and missed|i'm so sorry, i panicked",
    "a zombie got him|i wasn't even there|he walked into lava",
    "that was our best trader|we needed him|you're finding us a new one",
    "there's no zombie|i saw you swing|the golem saw you",
    "ok, i'll find you a new librarian|i'm sorry, i'll fix it|i'll help you set up a new one",
    "there's more villagers|he was a bad trader anyway|not my problem",
    "everyone, this one killed the librarian|we lost our trader because of this one|watch the villagers when this one is around")
ACC("death_loot", "death", "its dropped stuff",
    "you took my stuff when i died|my stuff was on the ground and you took it|i died and you picked up my diamonds",
    "i was keeping it safe for you|ok, yeah, i was going to give it back|sorry, i thought it'd despawn",
    "it was just lying there|i didn't see your stuff|items on the ground are free",
    "then give it back|all of it|i died and you looted me",
    "you're wearing my helmet|i saw you pick it up|you came back with my sword",
    "ok, here, everything|sorry, here's your stuff|take it, all of it",
    "should've not died|finders keepers|it was going to despawn anyway",
    "everyone, this one loots the dead|i died and this one took my stuff|careful dying near this one")
ACC("flood", "farming", "its flooded farm",
    "you flooded my farm|there's water everywhere and you had a bucket|my crops washed away",
    "ok, i spilled my bucket|yeah, i was making a fountain|sorry, i didn't know it would spread",
    "it rained|i didn't have water|your farm was too low",
    "then fix it|you owe me a harvest|clear the water",
    "rain doesn't come in a square|your bucket is empty|it starts from your side",
    "ok, i'll fix it and replant|sorry, i'll clean it up|i'll give you my wheat",
    "now it's a water farm|grow some kelp|deal with it",
    "everyone, this one flooded my farm|my crops are underwater, thanks to this one|careful with buckets around this one")
ACC("credit", "building", "taking credit",
    "you said you built that, i built it|you're telling people the tower is yours|that was my idea and you took credit",
    "ok, i helped, i didn't build it|yeah, i made it sound bigger|sorry, i wanted people to like me",
    "i did build it|we built it together|i had the idea first",
    "tell them the truth|you placed like three blocks|say it was mine",
    "i have the plans|you were asleep when i built it|everyone saw me building",
    "ok, i'll tell everyone|sorry, it's yours|i'll put your name on it",
    "nobody cares who built it|i'm better at talking about it|we'll see who they believe",
    "everyone, i built that tower, not this one|this one takes credit for other people's work|ask this one who really built it")
ACC("secret", "gossip", "its spilled secret",
    "you told everyone where my base is|i told you one secret and now everyone knows|you spread my secret",
    "ok, it slipped out|yeah, i'm bad at secrets|sorry, i only told one person",
    "i didn't tell anyone|someone else told|it's not a secret if people find out",
    "now everyone knows|you can't keep anything|that was between us",
    "only you knew|they said they heard it from you|you were at the well all day",
    "ok, i'll keep my mouth shut from now on|sorry, i'll help you move|i won't do it again",
    "it wasn't a big secret|everyone would've found out|not my problem",
    "everyone, never tell this one a secret|this one can't keep anything quiet|my secret's out because of this one")
ACC("bridge", "building", "its bridge",
    "you broke my bridge|my bridge is missing blocks and you were on it|someone took blocks from my bridge",
    "ok, i needed blocks|yeah, i took a few|sorry, i was bridging somewhere else",
    "it was a creeper|it was already broken|i never crossed it",
    "now i can't get home|put them back|you owe me blocks",
    "those are my blocks in your hands|creepers leave holes, not neat gaps|i saw you",
    "ok, i'll put them back|i'll fix it with stone, better|sorry, i'll fix it now",
    "swim then|build a better bridge|it's just blocks",
    "everyone, this one takes blocks from bridges|my bridge has holes, ask this one|careful crossing, this one's been at the bridges")
ACC("creeper", "mobs", "the creeper it led over",
    "you led a creeper to my house|you ran past my house with a whole horde behind you|my wall blew up because of your creeper",
    "ok, i ran your way|yeah, i panicked|sorry, i didn't know where else to go",
    "creepers go where they want|i didn't lead anything|it found your house on its own",
    "you're fixing my wall|you owe me a wall|lead them somewhere else next time",
    "i saw you running with it behind you|you yelled sorry as you ran past|your trail goes right to my wall",
    "ok, i'll fix your wall|sorry, i'll rebuild it|i'll give you stone for it",
    "light your area|your walls are weak|creepers happen",
    "everyone, this one leads creepers to houses|my wall's gone, thank this one|don't let this one run past your house")
ACC("trade_cheat", "trade", "a cheated trade",
    "you cheated me on that trade|that iron you gave me was half|you gave me rotten flesh instead of steak",
    "ok, i rounded down|yeah, i was short|sorry, i'll make it up",
    "it was a fair trade|you agreed to it|count again",
    "then give me the rest|i want my full trade|you owe me",
    "i counted twice|you said steak|someone else saw it",
    "ok, here's the rest|sorry, here, double|i'll make it right tomorrow",
    "a deal's a deal|should've checked|too late",
    "everyone, this one cheats on trades|count everything this one gives you|this one cheated me, be careful")
ACC("copied_house", "building", "its copied house",
    "you copied my house|your new house is my house|you stole my whole design",
    "ok, it inspired me|yeah, i liked it|sorry, i should've asked",
    "it's different|houses all look the same|i had this idea first",
    "change it then|at least say it was mine|that was my design",
    "it has my exact windows|even the mistakes are the same|you were drawing it yesterday",
    "ok, i'll change the roof|i'll tell people it's your design|sorry, i'll make it mine",
    "copying is flattery|you don't own houses|mine's better anyway",
    "everyone, this one copied my house|look, same house, mine came first|this one steals designs")
ACC("storage_mess", "storage", "its messed up chests",
    "you messed up my storage|my chests were sorted and now they're soup|who put dirt in my diamond chest? you",
    "ok, i was looking for something|yeah, i might have mixed it up|sorry, i was in a hurry",
    "it was messy already|i didn't touch it|maybe it sorted itself",
    "sort it then|you owe me an afternoon|stay out of my chests",
    "your dirt is in my diamond chest|i sorted it yesterday|you were in there an hour",
    "ok, i'll sort it all|sorry, i'll fix it|i'll label everything for you",
    "it was a bad system|sort it yourself|chests are chests",
    "everyone, this one wrecks storage rooms|keep this one out of your chests|my chests are soup, ask this one")
ACC("arrow", "fighting", "the arrow in its back",
    "you shot me|that arrow in my back is yours|you hit me instead of the skeleton",
    "ok, i missed, sorry|yeah, you got in the way|i'm so sorry, it slipped",
    "that was the skeleton|i didn't fire|you walked into it",
    "i had two hearts|watch where you aim|you owe me a heal",
    "skeleton arrows don't come from behind|you were the only one behind me|i heard your bow",
    "ok, here's a golden carrot|sorry, i'll stand in front next time|i'll practice, i promise",
    "move faster|it was one arrow|you'll heal",
    "everyone, this one shoots friends|don't stand in front of this one's bow|this one shot me in the back")
ACC("portal", "building", "its broken portal",
    "you broke my nether portal|my portal is gone and you had a pickaxe|who takes obsidian from a portal? you",
    "ok, i needed obsidian|yeah, i moved it|sorry, it was in my way",
    "it broke on its own|a ghast did it|i don't need obsidian",
    "i was stuck for an hour|put it back|you owe me ten obsidian",
    "ghasts don't come through|it was fine yesterday|you're carrying ten obsidian right now",
    "ok, i'll rebuild it|sorry, here's the obsidian|i'll light it for you too",
    "build another|walk then|it's just a portal",
    "everyone, this one breaks portals|my portal's gone, ask this one|watch your portals with this one around")
ACC("furnace", "crafting", "hogging its furnace",
    "you've been using my furnace all day|my furnace is full of your ore|i can't cook because of you",
    "ok, i had a lot to smelt|yeah, i didn't think you'd need it|sorry, it was closer",
    "it's a shared furnace|i only used it once|make another one",
    "take your ore out|make your own furnace|it's eight cobble",
    "my coal's gone too|your iron is in every slot|i've been waiting an hour",
    "ok, i'll make you two furnaces|sorry, i'll take it out|here, take some of the iron",
    "wait your turn|it's eight cobble, make one|i was here first",
    "everyone, this one hogs furnaces|can't cook, this one's using everything|this one's ore is in my furnace")
ACC("lost_loan", "gear", "its lost gear",
    "you lost my sword|i lent you my bow and you lost it|where's my shield? you had it",
    "ok, i dropped it in lava|yeah, i lost it, sorry|i died and it despawned",
    "i gave it back|you never lent me anything|you must've lost it",
    "then get me a new one|that was enchanted|you owe me a bow",
    "i never got it back|i remember handing it to you|someone saw you with it",
    "ok, i'll make you a new one|sorry, here's mine|i'll get it enchanted for you",
    "it was old|should've kept it|i don't remember that",
    "everyone, don't lend this one anything|this one lost my sword|ask this one where my bow went")
ACC("villager", "villagers", "its villager",
    "you stole my villager|my farmer is at your house now|you lured my villager away with bread",
    "ok, he followed me|yeah, i needed a farmer|sorry, he liked my bread",
    "villagers go where they want|he moved on his own|i don't have your villager",
    "bring him back|he was my farmer|i spent a week setting him up",
    "he's in a boat at your house|you had bread out|he's got my workstation",
    "ok, i'll walk him back|sorry, i'll give you two|i'll find you another one",
    "he likes it here|finders keepers|he's happier with me",
    "everyone, this one steals villagers|my farmer is at this one's house|lock up your villagers")
ACC("chest_trap", "danger", "the trapped chest",
    "you put tnt under my chest|i opened my chest and it blew up|you trapped my chest",
    "ok, it was a prank|yeah, it was a joke|sorry, i didn't think it'd blow up that much",
    "i don't have tnt|it was a creeper|you trapped it yourself",
    "i lost half my stuff|that's not funny|you're fixing my house",
    "creepers don't hide under chests|it was wired with your redstone|you laughed when it happened",
    "ok, i'll fix everything|sorry, here's some of my stuff|i'll never do it again",
    "it was funny|check your chest next time|you'll get over it",
    "everyone, this one traps chests|my house blew up because of this one|check your chests if this one's been near")
ACC("rumour", "gossip", "the lies about it",
    "you told people i stole from you|you've been saying i'm a bandit|you're spreading lies about me",
    "ok, i was mad|yeah, i shouldn't have said that|sorry, i was wrong",
    "i never said that|people talk|maybe they heard wrong",
    "take it back|tell them the truth|people look at me weird now",
    "three people told me|they repeated your exact words|you said it at the well",
    "ok, i'll tell everyone i was wrong|sorry, i'll fix it|i'll say it was a joke",
    "maybe it's true|people believe what they want|prove you didn't",
    "everyone, this one's been lying about me|don't believe what this one says about me|this one spreads lies, just so you know")
ACC("fishing_spot", "fishing", "its fishing spot",
    "you took my fishing spot|that's where i fish every morning|i've been fishing there for a week and you sat in it",
    "ok, i didn't know|sorry, it's a nice spot|i'll move",
    "the water's free|i've been fishing here longer|there's no name on the water",
    "move over|find your own spot|that's my spot",
    "my chair is right there|my sign says my name|everyone knows that's my spot",
    "ok, it's yours|we can share|i'll go to the river",
    "fish somewhere else|first come first served|the fish don't care",
    "everyone, this one steals fishing spots|that's my spot and this one took it|find your own water")
ACC("cake", "food", "its cake",
    "you ate my cake|i made a cake for the festival and you ate half|who eats someone else's cake?",
    "ok, it looked so good|yeah, just a slice. ok, three|sorry, i couldn't help it",
    "it was like that|a rabbit ate it|cakes go missing",
    "that was for everyone|you're making a new one|you owe me eggs, milk, sugar and wheat",
    "you've got frosting on you|rabbits don't eat cake|i saw you sneak in",
    "ok, i'll bake two|sorry, i'll bring the milk|i'll make it right",
    "it was a good cake|bake more|you weren't eating it",
    "everyone, this one ate the festival cake|the cake's half gone, ask this one|this one can't be trusted with cake")
ACC("tunnel", "home", "the tunnel under its house",
    "you dug under my house|there's a tunnel under my bedroom|i fell into your mine from my kitchen",
    "ok, i was following a vein|yeah, i didn't know it was your house|sorry, the iron went that way",
    "it's a cave|that tunnel was there|i dig everywhere, not just there",
    "fill it in|i fell into it|you owe me a floor",
    "your torches are down there|it leads to your mine|cave walls aren't that straight",
    "ok, i'll fill it in|sorry, here's some of the iron|i'll put stone under your floor",
    "it's a good tunnel|watch your step|the iron was there first",
    "everyone, this one digs under houses|i fell into this one's tunnel from my kitchen|check under your floors")

# Debts: a wants it back; b pays (agree), stalls, or says it doesn't owe anything (disagree).
PAID_THANKS = L("thank you. finally|ok, we're even|good. pleasure doing business|see, was that so hard?|thanks. really")
STALL_REPLY = L("you said that last time|tomorrow. i'm counting|i'll hold you to that|fine. but i'm keeping count|"
                "you always say soon")
EVEN = L("we're even now|happy?|ok, no more debts|done")
PROMISE = L("promise|swear|for real this time|i won't forget")
FIRM = L("end of story|i don't owe you|drop it|we're done here")
TOWN_DEBT = L("hey everyone, this one owes me and won't pay|just so everyone knows, this one doesn't pay back|"
              "don't lend this one anything, they never pay|this one's been owing me for days, in case anyone wondered")


def DB(key, about, remind, pay, stall, dispute, dreply, thanks=None, even=None):
    """thanks/even: a's and b's lines after b pays (for a debt that is a promise, not a thing)."""
    add("debt_" + key, about, ["trade", "argument", "friends"], ["trade", "argument", "trust", "friends"],
        [turn("a", L(remind)),
         turn("b", L(pay), chance=0.4, mark=["paid"]),
         turn("b", L(stall), chance=0.5, cond=["!t:paid"], mark=["stall"]),
         turn("b", [Q(x, y) for x, y in zip(L(dispute), L(dreply))], cond=["!t:paid", "!t:stall"], mark=["disagree"]),
         opts("a", [(["t:paid"], L(thanks) if thanks else pick(PAID_THANKS, 3), ["agree"]), (["t:stall"], pick(STALL_REPLY, 3)), ([], L(dreply))]),
         opts("b", [(["t:paid"], L(even) if even else pick(EVEN, 2)), (["t:stall"], pick(PROMISE, 2)), ([], pick(FIRM, 2))], chance=0.6)],
        needs=["!strangers"], weight=1.0)
    add("debt_town_" + key, about, ["town", "trade", "gossip"], ["town", "trade", "gossip", "argument"],
        [turn("a", L(remind)),
         turn("b", L(stall)),
         turn("a", pick(TOWN_DEBT, 2) + L("everyone hear that? soon, apparently")),
         turn("b", pick(TOWN_DEFENSIVE, 3)),
         turn("b", L(pay), chance=0.5, mark=["agree"]),
         opts("a", [(["t:agree"], L(thanks) if thanks else pick(PAID_THANKS, 2)), ([], pick(TOWN_HEARD, 2))], chance=0.8)],
        needs=["a:in_town", "!strangers", "!friends"], weight=0.9)


DB("iron", "the iron it owes",
   "you still owe me iron|where's my iron? it's been a week|you said you'd pay back that iron",
   "ok ok, here's your iron|fine, take it, three iron|here. we're even now",
   "tomorrow, i swear|i'm mining for it right now|next time i see you",
   "i paid you back already|that iron was a gift|i don't owe you anything",
   "you paid me in gravel|a gift? you said loan|you absolutely do")
DB("diamonds", "the diamonds it owes",
   "you owe me two diamonds|remember those diamonds you borrowed?|my diamonds. where are they",
   "here, two diamonds, happy?|ok, here they are|fine, they're yours",
   "i'll find some soon|give me a week|i'm digging for them, promise",
   "you gave me those|that was for helping you|i never borrowed diamonds",
   "i lent them, i didn't give them|helping? you watched|you did, at the anvil")
DB("bread", "the bread it owes",
   "you owe me bread from last week|i fed you when you were starving, remember?|where's my bread",
   "here, a whole stack|ok, take some bread|fine, here, fresh",
   "my wheat isn't ready yet|tomorrow, after harvest|soon, promise",
   "that was free food|friends don't count bread|you offered",
   "free? you ate twelve|i count bread|i offered one, you took twelve")
DB("bucket", "its bucket",
   "you still have my bucket|bring back my bucket|i need my bucket back",
   "here's your bucket|ok, here, it's empty|fine, take it",
   "i'm using it, give me a day|it's full of milk, after|i'll bring it tonight",
   "this is my bucket|i made this bucket|you never lent me a bucket",
   "it has my dent in it|with what iron?|i did, at the lake")
DB("pickaxe", "its pickaxe",
   "you owe me a pickaxe|i lent you my pickaxe, where is it|my pickaxe, please",
   "here, a new one even|ok, here it is|take it, sorry it's late",
   "i'll make you one tomorrow|i need it for one more day|it's at home, i'll bring it",
   "you said keep it|it broke, so we're even|that one's mine",
   "i said borrow it|broke means you owe me one|it has my name on it")
DB("emeralds", "the emeralds it owes",
   "you still owe me five emeralds|those emeralds, remember?|pay me my emeralds",
   "fine, five emeralds|here, done|ok, take them",
   "the trader's restocking|next market day|tomorrow, swear",
   "you owe ME emeralds|that was for the bread|we said three, not five",
   "nice try, i don't|the bread was a gift|we said five")
DB("torches", "the torches it owes",
   "you owe me a stack of torches|i lit your whole mine, pay me back|where are my torches",
   "here, a stack|ok, take these|fine, sixty four torches",
   "i'm out of coal|after i mine some coal|tomorrow, i'll craft them",
   "they're in your mine, use those|that was helping, not lending|you offered",
   "they're in YOUR mine|helping means you owe me|i offered to lend")
DB("bed", "its bed",
   "you owe me a bed|you broke my bed, make me one|i'm still sleeping on the floor because of you",
   "here, a new bed, red even|ok, take this one|fine, here",
   "need more wool|tomorrow, after i shear|i'll make it tonight",
   "your bed broke on its own|i didn't break it|beds break",
   "beds don't break on their own|you jumped on it|you broke it and laughed")
DB("coal", "the coal it owes",
   "you still owe me coal|those ten coal, remember|where's my coal",
   "here, ten coal|fine, take it|ok, plus two for waiting",
   "after i mine|next trip|soon",
   "i gave you charcoal for it|we're even|coal's cheap, forget it",
   "charcoal isn't coal|we're not|cheap or not, it's mine")
DB("sword", "its sword",
   "you owe me a sword|my sword. you borrowed it|you said one fight, that was a week ago",
   "here's your sword|ok, it's a bit used|take it, thanks",
   "one more fight|tomorrow, promise|i'll sharpen it first",
   "you gave me that sword|it's mine now|i won it fair",
   "lent, not gave|it's not|won it how, by asking?")
DB("wool", "the wool it owes",
   "you owe me wool for that bed|i gave you three wool, remember|where's my wool",
   "here, three white wool|ok, take them|fine, here",
   "my sheep are bald right now|wait for them to grow back|tomorrow",
   "you said it was free|i gave you a sheep for it|that was a trade",
   "i said lend|a sheep you took back|no trade, a loan")
DB("arrows", "the arrows it owes",
   "you used all my arrows|you owe me a stack of arrows|where are my arrows",
   "here, some arrows|fine, take these|ok, all i have",
   "i need feathers first|after i hunt chickens|tomorrow",
   "you shot most of them|they were for the raid, for everyone|i used them to save you",
   "i shot two|the raid was yesterday, the arrows were mine|save me? you missed every shot")
DB("boat", "its boat",
   "you owe me a boat|you sank my boat|where's my boat",
   "here, a new boat|ok, take this one|fine, here",
   "need planks|tomorrow|it's on the river, i'll get it",
   "the boat sank itself|you were driving|boats are cheap",
   "boats don't sink|i was the passenger|then make one")
DB("golden_apple", "its golden apple",
   "you owe me a golden apple|i saved your life with my golden apple|my gapple, remember",
   "here, a golden apple|fine, here|ok, take it",
   "i need gold|after i find gold|soon",
   "you threw it at me|i didn't ask for it|that was a gift",
   "i gave it to you to save you|you were dying, you asked|a loan, not a gift")
DB("favour", "a favour it owes",
   "you owe me a favour|i helped you build your house, now help me|remember when i carried you out of that cave?",
   "ok, what do you need|fine, i'll help, when|ok, you're right, name it",
   "next week, i'm busy|after my farm is done|soon, i promise",
   "you helped for like five minutes|we're even from the cave|that was your idea",
   "five minutes? it was all day|we're not even|it was your house",
   "great, come by tomorrow|ok, i'll hold you to that|thanks, i need help with my roof", "ok, i'll be there|deal")
DB("armor", "its armor",
   "you owe me a helmet|i lent you my chestplate|where's my armor",
   "here, your helmet|ok, take it, thanks for the loan|fine, here",
   "i'm wearing it, after this fight|tomorrow|i'll polish it first",
   "you said keep it|it got damaged, so it's mine|it fits me better",
   "i didn't|damaged means you owe me|that's not how it works")
DB("seeds", "the seeds it owes",
   "you owe me seeds|i gave you all my seeds|where are my wheat seeds",
   "here, a whole stack|ok, here|take these",
   "wait for harvest|tomorrow|after my wheat grows",
   "seeds are free, just break grass|i planted them for you|you said take them",
   "then break some grass|you planted them for you|i said borrow")
DB("rent", "its rent",
   "you've been sleeping at my place a week|you owe me rent|my spare bed isn't free",
   "ok, here's some iron|fine, here's bread, a lot|ok, i'll pay",
   "after i build my house|next week|soon, i'm saving",
   "friends don't charge|you said stay as long as i want|i fixed your roof, that's rent",
   "friends pay rent|i said a night|you put one block")
DB("map", "its map",
   "you owe me a map|i lent you my map|my map, i need it",
   "here's your map|ok, it's a bit wet|fine, take it",
   "i'm still using it|tomorrow|after i find the village",
   "i made that map|it's a copy|maps are cheap",
   "you can't make maps|it's mine, not a copy|then make your own")
DB("dinner", "a dinner it owes",
   "you owe me dinner|i cooked for you three times|your turn to cook",
   "ok, i'll make stew tonight|fine, come over, i'll cook|ok, you're right",
   "i don't have food yet|tomorrow|after i hunt",
   "you love cooking|i brought the meat|that wasn't cooking, that was burning",
   "loving it doesn't mean free|you brought one chicken|it was cooking",
   "great, i'll bring the bread|finally, i'm starving|ok, tonight then", "ok, see you tonight|deal")
DB("rescue", "a rescue",
   "you owe me one, i saved you from a creeper|remember who pulled you out of the lava?|you'd be dead without me",
   "ok, i owe you, what do you want|fine, name it|you're right, thanks",
   "i'll save you next time, we'll be even|soon|one day",
   "i had it handled|you pushed me in first|i saved myself",
   "you were on fire|i did not|you were screaming",
   "ok, i'll think of something|good, you owe me big|ok. remember that", "ok, deal|i won't forget")
DB("bet", "a lost bet",
   "you lost the bet, pay up|you owe me from the race|a bet's a bet",
   "fine, here|ok, you won|take it",
   "tomorrow|double or nothing|let's race again first",
   "you cheated|the bet was for fun|i don't remember betting",
   "i did not|fun bets are still bets|you do, we shook on it")
DB("pearls", "its ender pearls",
   "you owe me ender pearls|i gave you my pearls for the trip|where are my pearls",
   "here, four pearls|fine, here|ok, take them",
   "after i hunt endermen|tomorrow night|soon",
   "i threw them to save you|you gave them to me|they're gone, i used them",
   "you threw them at nothing|i lent them|then get more")
DB("cobble", "the cobble it owes",
   "you owe me a stack of cobble|i gave you cobble for your wall|my cobble",
   "here, two stacks|fine, take it|ok, here",
   "after i mine|tomorrow|soon",
   "cobble is free|it's everywhere|you won't miss it",
   "it's not free, i mined it|then mine it|i will, it's mine")

# Grudges: an old thing a holds against b; b says sorry (agree) or "get over it" (disagree).
MEND = L("thanks. i needed to hear that|ok. let's move on|ok, we're good|i'll let it go now|ok. it's behind us")
COLD = L("whatever|fine. i'll remember|ok then|i'm not getting over it|we'll see")


def GR(key, tag, about, bring, sorry, dismiss, a_ok, a_hurt):
    add("grudge_" + key, about, ["memories", "argument", tag], [tag, "memories", "trust", "friends"],
        [turn("a", L(bring)),
         turn("b", L(sorry), chance=0.5, mark=["agree"]),
         turn("b", [Q(x, y) for x, y in zip(L(dismiss), L(a_hurt))], cond=["!t:agree"], mark=["disagree"]),
         opts("a", [(["t:agree"], L(a_ok)), ([], L(a_hurt))]),
         opts("b", [(["t:agree"], pick(MEND, 3)), ([], pick(COLD, 3))], chance=0.6)],
        needs=["!strangers"], weight=0.9)


GR("nether_left", "danger", "being left in the nether",
   "you left me in the nether|i still remember you leaving me by that lava|you ran through the portal and left me",
   "i know. i panicked. i'm sorry|yeah, that was bad of me|i still feel bad about that",
   "that was ages ago|you made it out|get over it",
   "ok. just don't do it again|thanks. it was scary|ok, we're good",
   "it's not about how long ago|i almost didn't|you'd hate it too")
GR("raid", "fighting", "missing the raid",
   "you didn't help in the raid|we were fighting and you were in bed|the raid came and you stayed home",
   "i should've come. sorry|i was scared, i'm sorry|you're right, i let you down",
   "i had my own stuff|you were fine|there were enough people",
   "ok. next raid, you're with me|thanks for saying it|ok, we're fine",
   "we almost lost the bell|we weren't fine|it's about showing up")
GR("laughed", "death", "laughing at its death",
   "you laughed when i died|i died and you laughed|i lost everything and you thought it was funny",
   "it wasn't funny. sorry|i shouldn't have laughed|i'm sorry, that was cruel",
   "it was a little funny|lighten up|you laugh at mine",
   "ok. thanks|ok, we're good|it's fine now",
   "it wasn't funny to me|i lost a week of work|i don't laugh when you lose everything")
GR("housewarming", "home", "missing its housewarming",
   "you didn't come to my housewarming|i waited for you at my house|you said you'd come see my house and never did",
   "i forgot, i'm so sorry|i should've come|i'll come tonight, i promise",
   "it's just a house|i was busy|i'll see it some other time",
   "ok, tonight then|ok, i'll make food|ok, deal",
   "it was my first house|you're always busy|some other time never comes")
GR("deal", "trade", "a broken deal",
   "you broke our deal|we said we'd share the vein and you mined it all|you promised and didn't",
   "you're right, i'm sorry|i got greedy, i'm sorry|i'll make it up to you",
   "deals change|you weren't there|i did my part",
   "ok. make it up then|ok, i'll hold you to it|fine, we're good",
   "deals don't change|i was on my way|you didn't")
GR("picked_them", "friends", "picking others over it",
   "you picked them over me|you went mining with them instead of me|you chose their group",
   "i'm sorry, it wasn't like that|i should've asked you too|next time it's us, promise",
   "i can have other friends|it's not a big deal|you weren't around",
   "ok. next time|ok, i'll hold you to that|fine",
   "i know. but you picked them|it is to me|i was always around")
GR("cave_credit", "caves", "the cave it found",
   "you still tell people you found that cave|that was my cave and you took the credit|everyone thinks you found it",
   "ok, it was yours. sorry|i'll set it straight|you're right",
   "who cares who found it|it was both of us|nobody remembers",
   "thanks. that's all i wanted|ok, good|ok",
   "i care|it was me|i remember")
GR("cow", "animals", "its eaten cow",
   "you ate my cow|you cooked my cow, the one i named|i still can't believe you ate my cow",
   "i didn't know she was yours, i'm sorry|i'm so sorry, i was starving|i'll get you a new cow",
   "it's a cow|there's more cows|she was tasty though",
   "ok. a new one would be nice|ok, i forgive you. barely|ok",
   "she had a name|she was my favourite|that's not funny")
GR("visits", "friends", "never visiting",
   "you never visit anymore|i haven't seen you in days|you used to come by every morning",
   "i know, i'm sorry, i've been busy|you're right, i'll come by more|i miss it too",
   "i've got stuff going on|you could visit me|it's not a big deal",
   "ok. tomorrow morning then|ok, i'll make breakfast|good",
   "everyone has stuff going on|i did, you weren't home|it is to me")
GR("snitched", "politics", "telling on it",
   "you told the leader i skipped work|you snitched on me|you told everyone i was hiding",
   "i shouldn't have, i'm sorry|i was mad, it was wrong|i'll tell them i was wrong",
   "you did skip work|it was the truth|somebody had to",
   "ok. thanks|ok, we're good|fine",
   "you could've talked to me first|it wasn't your business|nobody had to")
GR("first_sword", "weapons", "its first sword",
   "you broke my first sword|that was my first iron sword and you broke it|i still think about that sword",
   "i know, i'm sorry, i'll make you one|i still feel bad|i'll make you a better one",
   "it was iron, not diamond|it was ages ago|swords break",
   "ok, i'd like that|ok, deal|thanks",
   "it was my first|ages ago or not|that one mattered")
GR("fire_blame", "argument", "taking the blame for a fire",
   "you blamed me for the fire|everyone thinks i burned the farm because of you|you said i did it, and you did it",
   "i panicked, i'm sorry|i'll tell them it was me|you're right, that was low",
   "it was a long time ago|nobody remembers|it doesn't matter now",
   "ok. tell them|ok, thank you|good",
   "they still look at me weird|people remember|it matters to me")
GR("tower", "building", "its broken tower",
   "you knocked down my tower|my tower is still half gone because of you|you never fixed my tower",
   "i'll fix it this week, promise|i'm sorry, i really meant to|let's fix it together",
   "it was ugly anyway|build another|i said sorry already",
   "ok, this week|ok, together then|deal",
   "it wasn't ugly to me|that's not the point|saying isn't fixing")
GR("ignored", "feelings", "being ignored",
   "you ignored me yesterday|i said hi and you walked right past|you looked right at me and didn't say anything",
   "i didn't see you, i'm sorry|i was in my head, sorry|i'm sorry, i was having a bad day",
   "i was busy|i didn't hear you|it's not a big deal",
   "ok, it's fine|ok, glad it wasn't me|ok",
   "you looked right at me|i said it twice|it is to me")
GR("sold_gift", "trade", "its sold gift",
   "you sold the sword i gave you|i made you that and you traded it|i saw my gift at the trader",
   "i needed emeralds, i'm sorry|i should've asked|i'll buy it back",
   "it's mine to sell|i didn't need it|it was just a sword",
   "ok, buy it back|ok, thanks|fine",
   "it was a gift|you could've told me|it wasn't just a sword")
GR("walked_off", "friends", "leaving without a word",
   "you left the group without saying anything|you just walked away from us|we woke up and you were gone",
   "i should've said something, sorry|i needed space, i'm sorry|you're right, that was rude",
   "i don't owe anyone a goodbye|it's my choice|people leave",
   "ok. just say bye next time|ok, i get it|ok",
   "we looked for you all day|you owe us a goodbye|not like that")
GR("race", "town", "the race it won",
   "you cheated in that race|i still say you cut through the river|that win was fake",
   "ok, i cut the corner, sorry|fine, you won, i admit it|rematch, fair this time?",
   "a win is a win|you're still on that?|get over it",
   "ok, rematch|thank you. finally|deal",
   "a fake win|yes, i'm still on it|i'll get over it when you admit it")
GR("promise", "mining", "a broken promise",
   "you promised to show me your mine|you said you'd teach me to mine|you still haven't taken me caving",
   "you're right, tomorrow|i'm sorry, i forgot|let's go today",
   "i'm busy|go on your own|it's just mining",
   "ok, tomorrow!|ok, i'll bring torches|deal",
   "you're always busy|you promised|it's about the promise")
GR("rude_friend", "friends", "being rude to its friend",
   "you were rude to my friend|you called my friend a noob|my friend said you yelled at them",
   "i was out of line, sorry|i'll say sorry to them too|i was having a bad day",
   "they are a noob|they started it|they're too sensitive",
   "ok, tell them|ok, thanks|good",
   "they're new|they didn't|they're my friend")
GR("vote", "politics", "its vote",
   "you voted against me|i saw your vote, it wasn't for me|i thought you were on my side",
   "i'm sorry, i thought the other one had a better plan|ok, it wasn't personal|i should've told you",
   "it's my vote|the other one was better|voting is private",
   "ok, i get it|ok, next time|fine",
   "it felt personal|better how?|you could've told me")

# Blame: something went wrong; b owns it (agree) or blames back (disagree).
BLAME_OK = L("ok. we're fine|it happens|let's go|ok, lesson learned|alright")
BLAME_BAD = L("whatever|fine, it's my fault, sure|believe what you want|unbelievable|ok, sure")


def BL(key, tag, about, blame, accept, back, a_ok, a_back):
    add("blame_" + key, about, ["argument", "complaints", tag], [tag, "argument", "friends", "complaints"],
        [turn("a", L(blame)),
         turn("b", L(accept), chance=0.5, mark=["agree"]),
         turn("b", [Q(x, y) for x, y in zip(L(back), L(a_back))], cond=["!t:agree"], mark=["disagree"]),
         opts("a", [(["t:agree"], L(a_ok)), ([], L(a_back))]),
         opts("b", [(["t:agree"], pick(BLAME_OK, 3)), ([], pick(BLAME_BAD, 3))], chance=0.6)],
        needs=["!strangers"], weight=0.9)


BL("creeper", "mobs", "the creeper that blew",
   "you woke that creeper|that creeper was fine until you sprinted at it|we lost the iron because you ran into the creeper",
   "yeah, my bad|ok, i panicked|you're right, i wasn't looking",
   "you were the one yelling|it saw you first|it was following you, not me",
   "it's fine, we'll get more|ok, just watch next time|we're alive, that's what counts",
   "i was whispering|it was looking at you|it was not")
BL("lost", "exploring", "getting lost",
   "we're lost because of you|you said left, we went left, now we're lost|you had the map",
   "ok, it was me|yeah, i read it wrong|sorry, i thought it was left",
   "you gave me the map upside down|you said follow the river|you wanted the shortcut",
   "ok, let's just find the way|it's fine, we'll find it|ok, the sun sets in the west, we'll manage",
   "it was the right way up|that was before|i never said shortcut")
BL("fence", "farming", "the missing fence",
   "the rabbits got the farm because you didn't fence it|you were supposed to build the fence|no fence, no carrots, thanks",
   "ok, i forgot the fence|yeah, that's on me|sorry, i'll build it now",
   "you said you'd do it|i built half, you did nothing|you were on fence duty",
   "ok, build it today|fine, let's do it together|it's ok, we'll replant",
   "i said i'd do the gate|half a fence is no fence|i was on farm duty")
BL("forest_fire", "trees", "the forest fire",
   "you set the forest on fire|that fire was your flint and steel|the whole hill burned because of you",
   "ok, it was me|yeah, it got out of hand|i'm sorry, i'll plant trees",
   "lightning did it|you put the lava there|it was your campfire",
   "ok, plant some saplings|it'll grow back|it's ok, nobody got hurt",
   "there was no storm|my lava was in a bucket|my campfire was out")
BL("bell", "town", "ringing the bell late",
   "the raid got in because you rang the bell late|you were supposed to warn everyone|the raid hit and you were asleep",
   "you're right, i messed up|i fell asleep, i'm sorry|i should've rung it sooner",
   "you didn't wake me|you were on watch|nobody told me it was my turn",
   "ok. we'll rebuild|it's done now, we'll do better|ok, next time",
   "it was your turn|i was on watch yesterday|i told you twice")
BL("no_food", "food", "forgetting the food",
   "we both died because you didn't bring food|you were supposed to bring food|i asked you to pack food",
   "yeah, i forgot it|sorry, i thought you had it|my bad",
   "you ate it all on the way|you said you had it|you packed the bag",
   "ok, next time double check|fine, we'll get our stuff|it's ok",
   "i had two bites|i never said that|you watched me pack it")
BL("boat", "ocean", "the boat crash",
   "you crashed the boat|we sank because you steered into a rock|my stuff is at the bottom of the river",
   "ok, i'm a bad driver|yeah, that rock came out of nowhere|sorry, i'll help you dive for it",
   "you were rocking the boat|you told me to go that way|the boat was yours, it was broken",
   "ok, let's dive|ok, i'll steer next time|fine",
   "i was sitting still|i said the other way|it was fine before you")
BL("warden", "danger", "waking the warden",
   "you woke the warden|we had to run because you were stomping|the deep dark was quiet until you",
   "ok, i sneezed|yeah, i stepped on a sensor|sorry, i forgot to sneak",
   "you threw a snowball|you were breaking blocks|you were talking the whole time",
   "ok. we're out, that's what matters|fine, next time sneak|ok",
   "that was a torch|i was mining quietly|i was whispering")
BL("potion", "random", "the wrong potion",
   "you gave me the wrong potion|i drank slowness in a fight because of you|you labeled the potions wrong",
   "ok, i mixed them up|yeah, they look the same|sorry, my labels are bad",
   "you grabbed it yourself|you said the blue one|you were in a hurry",
   "ok, label them better|fine, we lived|ok",
   "it was the only one|you said blue|i was in a fight")
BL("emeralds", "trade", "the wasted emeralds",
   "you traded our emeralds for junk|we needed those emeralds|you spent the group's emeralds",
   "ok, it was a bad trade|sorry, i thought it was a deal|i'll pay it back",
   "you said spend them|it's useful stuff|you'd have done worse",
   "ok, pay it back|fine, lesson learned|ok",
   "i said save them|it's junk|i wouldn't have")
BL("too_late", "help", "coming too late",
   "you came too late, i already died|i called for help and you took forever|i was dying and you stopped to pick flowers",
   "i'm sorry, i got lost|you're right, i should've run|sorry, i didn't hear you at first",
   "you went in alone|you never said where you were|you ran off without me",
   "ok, next time stay close|ok, it's fine|ok",
   "i told you where|i yelled for ages|i said wait")
BL("furnace_fire", "home", "the house fire",
   "you put the furnace next to the wool|my house burned because of your furnace|who puts a furnace by the bed",
   "ok, that was dumb|yeah, i didn't think|sorry, i'll help rebuild",
   "furnaces don't burn things|you lit the campfire|you wanted it there",
   "ok, help me rebuild|fine, stone this time|ok",
   "then what did|there's no campfire|i said by the wall")
BL("night_walk", "night", "walking at night",
   "you made us travel at night|we lost a day because you wanted to go at night|you said night was safe",
   "ok, bad idea|yeah, i was wrong|sorry, i was impatient",
   "you said you weren't tired|you were the one in a hurry|you agreed",
   "ok, we'll wait for morning next time|fine|ok",
   "i was tired|i was not|i agreed to stop you complaining")
BL("gravel", "mining", "the gravel that fell",
   "the gravel fell on me because you mined it|you mined the block under the gravel|i got buried because of you",
   "ok, i didn't see it|sorry, i should've checked|my bad, are you ok",
   "you were standing under it|you told me to mine it|you didn't move",
   "i'm ok. just look up next time|ok, i'm fine|ok",
   "you said it was stone|i said the other one|i couldn't, it was gravel")
BL("horse", "animals", "the runaway horse",
   "the horse ran off because you didn't tie it|you forgot the lead|our horse is gone",
   "ok, i forgot|sorry, i'll find it|my bad",
   "you rode it last|you had the lead|you said you'd tie it",
   "ok, let's find it|fine|ok",
   "i gave you the lead|i did not|i said i'd feed it")
BL("contest", "town", "losing the contest",
   "we lost the contest because of you|you messed up the build at the contest|we lost because you slept in",
   "ok, i messed it up|sorry, i was nervous|you're right, i'm sorry",
   "you picked the design|you were late too|the judges were unfair",
   "ok, next time|it's fine, it was fun|ok",
   "you agreed to it|i was early|they weren't")
BL("portal", "building", "the broken portal",
   "we're stuck because you broke the portal|you mined the portal by accident|now we have to walk home",
   "ok, i swung wrong|sorry, i'll make a new one|yeah, that's on me",
   "you told me to mine there|you were standing in it|the portal was broken already",
   "ok, do you have obsidian?|ok, let's walk|fine",
   "i said next to it|i was outside|it was fine")
BL("herd", "animals", "scaring the herd",
   "the cows ran off because you yelled|you scared the whole herd|you sprinted into the pen",
   "ok, i got excited|sorry, i'll round them up|my bad",
   "you opened the gate|they were scared of your dog|you brought the wolf",
   "ok, round them up|fine|ok",
   "i opened it after|he was sitting|the wolf was fine")
BL("swamp", "biomes", "picking the swamp",
   "we built in a swamp because of you|you picked this spot and it floods|the slimes come every night, thanks",
   "ok, it was a bad spot|sorry, it looked nice from up there|my bad",
   "you agreed|you wanted water nearby|it was your idea",
   "ok, we'll move|ok, we'll build up|fine",
   "i agreed to look|nearby, not under|it was not")
BL("torches", "caves", "forgetting the torches",
   "we're lost in the dark because you forgot the torches|you had one job, torches|no torches, no light, no idea where we are",
   "ok, i forgot them|sorry, they're on my table|my bad",
   "you said you'd pack them|you used them all|you put them in your chest",
   "ok, we'll make some|fine, we have coal|ok",
   "i said food|i used four|i did not")

# Apologies and making up (trust).
ACCEPT = L("ok. apology accepted|thanks. that means a lot|it's ok, really|we're good|ok, i'll take it|"
           "thanks for saying it")
MAKE_UP = L("glad we're ok|friends?|ok. good|thanks for being cool about it|we're good then")


def AP(key, tag, about, sorry, react, offer, needs=""):
    add("sorry_" + key, about, ["trust", "feelings", tag], [tag, "trust", "friends", "feelings"],
        [turn("a", L(sorry)),
         turn("b", L(react)),
         turn("a", L(offer)),
         turn("b", pick(ACCEPT, 4)),
         turn("a", pick(MAKE_UP, 3), chance=0.5)],
        needs=N(needs) or ["!strangers"], weight=0.8, effects=["trust"])


AP("yelled", "feelings", "yelling",
   "sorry i yelled at you yesterday|i was rude yesterday, i'm sorry|about yesterday... i'm sorry i snapped",
   "it's ok, it was a bad day|took you long enough|yeah, that stung a bit",
   "let me make it up to you, dinner?|i'll bring you something nice|i'll help with your farm today")
AP("roast", "jokes", "a roast that went too far",
   "sorry about the roast the other day, it went too far|i didn't mean the stuff about your house|i've been thinking about what i said, sorry",
   "it's fine, i laughed... later|thanks, it got to me a bit|i know you didn't mean it",
   "your house is nice, really|let me help you decorate|i'll roast myself next time")
AP("cave", "caves", "running off in the cave",
   "sorry i left you in that cave|i shouldn't have run off in the cave|about the cave, i'm sorry",
   "i made it out|it was scary|thanks for saying it",
   "next time i stay, promise|i'll carry the torches next time|let's go back together")
AP("broke", "building", "breaking something",
   "sorry i broke your fence|i'm sorry about your door|sorry about the window, that was me",
   "i figured|it's ok, it's just a block|thanks for owning it",
   "i'll fix it today|i brought new blocks|i'll make it better than before")
AP("forgot", "friends", "forgetting",
   "sorry i forgot to come yesterday|i'm sorry i missed it|i forgot, and i feel bad",
   "it's ok, i waited a bit|i figured something came up|happens",
   "let's do it today|i'll come by tonight|i'll make it up, promise")
AP("food", "food", "eating its food",
   "sorry i ate your food|i'm sorry about the bread, i was starving|i owe you for the stew",
   "i noticed|it's ok, you were hungry|lol it's fine",
   "here, i brought you a stack back|i'll cook for you tonight|i'll bring fish")
AP("kill", "fighting", "taking its kill",
   "sorry i took your kill|that zombie was yours, sorry|i shouldn't have taken that kill",
   "lol it's fine|it was a zombie, it's ok|i was mad for a second",
   "next one's yours|here, take the drops|i'll watch your back next fight")
AP("gossip", "gossip", "talking behind its back",
   "sorry i talked about you at the well|i shouldn't have said that stuff about you|i'm sorry i spread that rumour",
   "i heard about it|that hurt, honestly|ok. thanks for telling me",
   "i'll set it straight|i'll tell them it wasn't true|it won't happen again")
AP("laughed", "death", "laughing at its death",
   "sorry i laughed when you fell|i shouldn't have laughed at your death|that was mean of me to laugh",
   "it was a little funny|it's ok|yeah, it stung",
   "next time i'll help you up|i'll get your stuff back with you|i'll laugh at mine instead")
AP("crops", "farming", "trampling its crops",
   "sorry i trampled your crops|i ran through your farm, sorry|your wheat, that was me, sorry",
   "i saw the footprints|it's ok, it grows back|thanks for telling me",
   "i'll replant it all|i brought seeds|i'll put up a fence for you")
AP("borrowed", "tools", "keeping its tools",
   "sorry i kept your pickaxe so long|here's your bucket, sorry it took a week|sorry, i forgot i had your sword",
   "i was wondering|finally lol|it's ok",
   "i fixed it up for you|i added an enchant, as sorry|i'll lend you mine anytime")
AP("bossy", "plans", "being bossy",
   "sorry i was bossy on the trip|i was telling you what to do all day, sorry|i get bossy, i'm sorry",
   "you were a bit|it's ok, you knew the way|i noticed",
   "you lead next time|i'll listen more|next trip is your plan")
AP("housewarming", "home", "missing its housewarming",
   "sorry i missed your housewarming|i should've come see your house, sorry|i never came to see your house, i'm sorry",
   "i saved you some cake|it's ok, i know you're busy|i was a bit sad",
   "can i come see it now?|i'll bring a gift|show me tonight?")
AP("snitched", "politics", "telling on it",
   "sorry i told the leader|i shouldn't have told on you|that was not my business, i'm sorry",
   "yeah, that wasn't cool|it's ok, i deserved some of it|ok",
   "i'll talk to the leader for you|it won't happen again|i'll have your back next time")
AP("noob", "players", "calling it a noob",
   "sorry i called you a noob|you're not a noob, i was being mean|i'm sorry for what i said, you're learning fast",
   "it's ok, i am kind of new|that's nice to hear|thanks",
   "i'll teach you something|want to go mining? i'll show you|ask me anything", "b:voice_newbie")
AP("trade", "trade", "a bad deal",
   "sorry about that trade, i ripped you off|i gave you a bad deal, i'm sorry|that trade wasn't fair, sorry",
   "i knew it|it's ok, i'll check next time|thanks for owning it",
   "here's the rest|let me make it right|next trade's on me")
AP("creeper", "mobs", "the creeper it brought",
   "sorry i led that creeper to your house|your wall, that was my fault, sorry|i ran past your house with a creeper, sorry",
   "i figured|it's ok, walls can be fixed|you owe me a wall",
   "i'll rebuild it|stone this time|i'll fix it today")
AP("ignored", "feelings", "walking past it",
   "sorry i walked past you yesterday|i didn't mean to ignore you|i was in my own head, sorry",
   "it's ok|i thought you were mad at me|thanks for saying something",
   "walk with me today?|let's catch up|i'll say hi twice next time")
AP("shot", "weapons", "shooting it",
   "sorry i shot you|that arrow was mine, sorry|i'm sorry, i aim like a skeleton",
   "it's ok, i healed|you do aim like a skeleton|lol it's ok",
   "i'll practice|i'll stay in front next time|here, a golden carrot")
AP("doubted", "advice", "not believing it",
   "sorry i didn't believe you about the diamonds|you were right about the cave, sorry|i should've listened to you",
   "told you|it's ok|thanks",
   "next time i'll listen|lead the way next time|you were right, i'll say it louder")
AP("moody", "feelings", "being in a mood",
   "sorry i've been in a mood lately|i've been grumpy, it's not you|i'm sorry for being so short with you",
   "it's ok, everyone has days|i noticed, are you ok?|thanks for saying it",
   "let's do something fun|i'll be better|walk with me?")
AP("name", "jokes", "forgetting its name",
   "sorry i forgot your name the other day|i called you the wrong name, sorry|i know your name, i swear",
   "lol it's fine|you called me dirt roof|it happens",
   "i'll never forget it again|i'll write it on a sign|it's burned into my brain now")
AP("rival_peace", "rivals", "making peace",
   "hey. i know we don't get along. i'm sorry for my part|i've been a jerk to you, i'm sorry|can we stop fighting? i'm sorry",
   "...really?|that's unexpected|ok. i was a jerk too",
   "truce?|let's start over|we don't have to be enemies", "rivals")
AP("rival_respect", "rivals", "giving it credit",
   "ok, i'll say it. you're actually good at building|i was wrong about you|you're not as bad as i said",
   "...thanks?|wow. ok|that's new",
   "maybe we can build something together|truce?|i'll stop talking trash", "rivals")
AP("rival_thanks", "rivals", "being saved by a rival",
   "thanks for helping me in that fight, even though we don't get along|i owe you one for the creeper thing|you saved me back there, i won't forget it",
   "i wasn't going to let you die|it was nothing|you'd have done the same. maybe",
   "maybe we stop fighting|truce?|i'll buy you bread", "rivals")
AP("rival_bread", "rivals", "a peace offering",
   "here. bread. i know we fight, but you looked hungry|i made too much stew, want some? no tricks|peace offering. it's a cake. don't make it weird",
   "...is it poisoned?|wow. thanks, i guess|that's actually nice",
   "no tricks, promise|eat it before i change my mind|call it a truce", "rivals")
AP("rival_town", "town", "a truce for the town",
   "look, the town needs us both. can we stop|for the town, truce?|i'm tired of fighting you, the whole town's watching",
   "...fine. for the town|yeah, it's getting old|ok. i'm tired too",
   "shake on it?|deal, then|we can still disagree, quietly", "rivals a:in_town")

# Passive-aggressive small talk between two who aren't friends.
PA_END = L("mhm|sure|ok then|lovely|great talk|anyway|right")


def PA(key, tag, about, line, pairs, needs=""):
    add("pa_" + key, about, ["rivals", "opinion", tag], [tag, "rivals", "gossip", "opinion"],
        [turn("a", L(line)),
         turn("b", PR(pairs)),
         turn("a", ["mhm"]),
         turn("b", pick(PA_END, 4), chance=0.7)],
        needs=["!friends", "!strangers"] + N(needs), weight=0.8)


PA("house", "building", "its house",
   "nice house. for you|your house is cute. small, but cute|i love what you did with the house. bold",
   "thanks... i think >> you're welcome. i think|what's that supposed to mean >> nothing, it's cute|bold? >> bold. brave even")
PA("armor", "armor", "its armor",
   "love the armor. very brave|that's an interesting armor choice|you look... prepared",
   "brave how >> brave to wear that in public|interesting? >> interesting is good. sometimes|thanks? >> sure")
PA("farm", "farming", "its farm",
   "your farm's coming along. slowly|cute farm. does it grow anything|i see you're trying farming",
   "it grows plenty >> plenty is a big word|it's coming along fine >> fine, sure|trying? >> trying is the first step")
PA("trade", "trade", "its trade",
   "wow, you got a great deal. for the villager|nice trade. i'm sure it was worth it|you trade like someone with a lot of emeralds",
   "it was a good deal >> sure it was|what do you mean >> nothing at all|i got what i wanted >> that's what matters, i guess")
PA("late", "friends", "showing up late",
   "oh you're up. finally|nice of you to show up|look who decided to join us",
   "i was busy >> sure|i'm here now, aren't i >> you are, after everything|what's that supposed to mean >> just happy you're here. late")
PA("skills", "fighting", "its fighting",
   "you're getting better. a little|good effort today|you almost hit that zombie",
   "almost? >> almost counts, right?|thanks, i guess >> anytime|what do you mean a little >> a little is still something")
PA("cooking", "food", "its cooking",
   "your stew is very... unique|i've never tasted anything like your cooking|you're so brave to cook for people",
   "it's an old recipe >> it shows|you didn't like it? >> i didn't say that|brave? >> very")
PA("style", "building", "its building style",
   "you have such a unique style. nobody else would build that|your builds really stand out. from far away|nobody builds like you. thankfully",
   "thankfully? >> did i say that? i meant truly|what does that mean >> it means you're special|thanks >> don't mention it. really, don't")
PA("luck", "diamonds", "its lucky find",
   "must be nice finding diamonds by luck|wow, lucky you|some people just get lucky, i guess",
   "it wasn't luck >> of course not|i worked for that >> sure you did|jealous? >> me? never")
PA("popular", "friends", "its new friends",
   "i see you've got new friends. good for you|you're so popular lately|everyone seems to like you now",
   "you sound bitter >> me? i'm thrilled for you|they're good people >> i'm sure they are|thanks >> it won't last. kidding")
PA("hut", "home", "its upgraded hut",
   "oh, you upgraded the hut. it's... bigger|is that a new house or the old hut with a hat|love the new place. very you",
   "it's a house now >> if you say so|very me? >> very you|what's wrong with it >> nothing. nothing at all")
PA("helper", "help", "its helping",
   "thanks for helping. i guess i owe you now|so helpful, as always. a bit too much|you're always so eager to help. it's a lot",
   "you're welcome >> mhm|too much? >> a bit, yeah|i just like helping >> clearly")
PA("rain", "weather", "the rain",
   "nice weather you brought|of course it's raining, you're here|it always rains when you visit",
   "that's not how weather works >> isn't it?|i didn't bring the rain >> sure|rude >> just an observation", "rain")
PA("leader", "politics", "running for leader",
   "heard you're running for leader. brave|you'd make an interesting leader|good luck with the vote. you'll need it",
   "thanks for the support >> any time|interesting how >> interesting|i don't need luck >> everyone needs luck", "a:in_town")
PA("chests", "storage", "its chests",
   "your chests are so... full|i love how you keep everything|you must never lose anything with all those chests",
   "i'm organized >> very|they're sorted >> sorted, sure|i know where everything is >> that makes one of us")
PA("pets", "pets", "its pets",
   "your dog is so well behaved. sometimes|cute cat. does it like you?|your horse seems happy. to see me",
   "he's a good dog >> good enough|she likes me fine >> fine is good|he's happy to see everyone >> mostly me")
PA("know_it_all", "advice", "always being right",
   "you're always right, aren't you|must be nice, knowing everything|i'll just let you explain it to me again",
   "i'm just trying to help >> mhm|what's your problem >> no problem at all|fine, i'll stop >> don't stop on my account")
PA("famous", "gossip", "being talked about",
   "everyone's talking about you. some of it's even good|you're famous around here|your name comes up a lot at the well",
   "what are they saying >> oh, things|good things? >> some|i don't care what they say >> good, that's healthy")
PA("sword", "weapons", "its sword",
   "nice sword. is it new or just clean|that sword looks great on you. very decorative|you carry that sword everywhere. for looks?",
   "it's enchanted >> how fancy|decorative? >> very pretty|it's for fighting >> right, of course")
PA("bread", "food", "the bread it brought",
   "thanks for the bread. it's very... bread|oh, you brought food. that's new|i'll eat it later. maybe",
   "you're welcome >> mhm|i always bring food >> do you|later? >> later")
PA("quick", "jokes", "how quick it is",
   "you're so quick. at leaving|you got here fast. for you|wow, already done? that was quick",
   "i'm efficient >> you are something|for me? >> for you|is that bad? >> not at all, i'm impressed. sort of")
PA("decor", "decor", "its decorations",
   "love the flowers. so many flowers|your windows have... a lot going on|you really committed to that colour",
   "i like flowers >> clearly|it's a theme >> it's something|it's my favourite >> i can tell")
PA("night", "night", "going out at night",
   "out at night again? interesting|you're brave, going out at night in that armor|night walks, how adventurous",
   "i can handle it >> i'm sure you can. probably|what's wrong with my armor >> nothing. at all|it's peaceful >> peaceful until it isn't", "night")
PA("group", "friends", "its group",
   "how's {b.group}? still together?|heard your group is doing ok. ok is something|nice group you've got. small, but nice",
   "we're doing great >> great is a strong word|small is fine >> fine is fine|why do you care >> just asking. nicely", "b:in_group")
PA("sleep", "sleep", "sleeping a lot",
   "early night again? must be nice|you sleep a lot, huh. good for you|well rested as always",
   "sleep is healthy >> so they say|someone has to >> i suppose|you should try it >> i'm busy")
PA("learning", "crafting", "learning",
   "you're learning so fast. for a beginner|good job on the furnace. first try?|look at you, crafting things",
   "i'm not a beginner >> of course not|it was the first try >> it shows|thanks >> proud of you. sort of")
PA("looks", "jokes", "its looks",
   "you look nice today. for once|you smell better today|wow, clean armor. special occasion?",
   "for once? >> for once|thanks? >> you're welcome|i always look like this >> sure you do")
PA("loud", "complaints", "how loud it is",
   "you're so lively. loud, but lively|i can always hear you coming|you have such a strong voice",
   "is that a problem >> no no, it's charming|i'm just happy >> i can tell. we all can|thanks >> it carries")
PA("gift", "friends", "its gift",
   "oh, a gift. you shouldn't have. really|thanks, i'll find a place for it. somewhere|how thoughtful. a dead bush",
   "i thought you'd like it >> it's the thought|you don't like it >> i love that you tried|it's decorative >> very")
PA("shop", "trade", "its shop",
   "your shop's so cute. empty, but cute|love your prices. they're very... confident|busy day at the shop? no?",
   "business is fine >> fine, sure|my prices are fair >> to you|it's slow today >> every day, it seems", "b:keeps_shop")

# More roasts between friends and rivals.
R("respawn_spot", "death", "its respawn point", "",
  "your bed has seen you dead more than alive|your respawn point should charge you rent|you've respawned more times than the sun came up",
  "it's a comfy bed >> comfy for a corpse|i like my bed >> your bed's tired of you|at least i always come back >> like a bad smell")
R("emeralds", "trade", "its emeralds", "",
  "you have a hundred emeralds and buy nothing|you hold emeralds like they'll hatch|you're saving emeralds for what, a statue",
  "i'm saving for something big >> big like a second chest of emeralds|prices are bad right now >> prices are fine, you're cheap|they're pretty >> so is spending them")
R("signs", "decor", "its signs", "",
  "your signs are spelled wrong|you wrote welcom on your door|your signs are a crime against words",
  "i was in a hurry >> you've been in a hurry since day one|nobody reads signs >> everybody read that one|spelling is overrated >> your sign says chikens")
R("portal", "building", "its portal", "b:knows_nether",
  "your nether portal is crooked|your portal frame has cobble in it|why is your portal in a swamp",
  "it works >> it works and it's ugly|obsidian is hard to get >> so is taste|swamps are cozy >> for slimes")
R("hoes", "tools", "its hoes", "",
  "why do you own five hoes|you have a hoe for every crop|your tool chest is all hoes",
  "farming is serious >> five hoes serious?|they're all different >> they're all hoes|i like hoes >> clearly")
R("tunnel", "mining", "its tunnel", "",
  "your tunnel goes on forever and finds nothing|you dug a tunnel to nowhere|i walked your tunnel for an hour and saw coal",
  "it's a highway >> a highway to gravel|i'm still digging >> dig smarter|the ore is coming >> the ore moved away")
R("snow_golem", "mobs", "its snow golem", "",
  "your snow golem is your best friend|you talk to your snow golem|your snow golem has more personality than you",
  "he's a good listener >> he's a pumpkin|he protects me >> with snowballs|he's cool >> literally, only literally")
R("flower_pots", "decor", "its flower pots", "",
  "you have flower pots on every block|your house is ninety percent flower pots|you put a flower pot on your bed",
  "it's decor >> decor overload|plants are nice >> plants on your pillow?|it's cozy >> cozy like a greenhouse")
R("beach_house", "home", "its beach house", "",
  "your house is on the beach and the drowned visit every night|your beach house is half underwater|you live on a beach like a turtle",
  "i like the waves >> the waves like your floor|it's a great view >> great view of the drowned|turtles are cool >> you're not a turtle")
R("lanterns", "decor", "its lanterns", "",
  "you won't use torches, only lanterns, fancy|lanterns everywhere, who do you think you are|you made lanterns for your chicken coop",
  "lanterns look better >> they cost iron, show off|torches are ugly >> torches are honest|my chickens deserve it >> your chickens have better light than me")
R("cave_spiders", "mobs", "the cave spiders", "",
  "you got poisoned by a cave spider again|you scream like a cave spider bit you, because one did|cave spiders love you",
  "they're fast >> they're tiny|it was a spawner >> you sat next to it|poison is mean >> so is my laugh")

RV("no_bed", "sleep", "not having a bed", "",
   "you don't even have a bed|sleeping on the ground like a mob|no bed, no spawn, no clue",
   "i don't need one >> the phantoms say you do|beds are for the weak >> the weak who sleep|i'll make one >> with what, your one sheep")
RV("fake_builder", "building", "its building claims", "",
   "you call yourself a builder? with that?|you built a box and called it a castle|nobody asked you to build",
   "it's a castle >> a castle for ants|you can't build either >> i build better than that asleep|i like it >> you're the only one")
RV("haggler", "trade", "haggling", "",
   "you'd haggle with a chicken|you charge for directions|you wouldn't pay one emerald to save yourself",
   "i'm careful with money >> careful, sure. cheap|you waste yours >> i spend mine, you hoard|it's business >> business nobody wants")
RV("glass_jaw", "fighting", "going down fast", "",
   "you go down in one hit|one punch and you're on the floor|you have the hearts of a chicken",
   "i had bad armor >> you had armor? where|it was a lucky hit >> every hit on you is lucky|i'll get you next time >> next time is a respawn screen")
RV("weeds", "farming", "its farming", "",
   "you can't even grow wheat|your crops die looking at you|bonemeal gave up on you",
   "my farm is fine >> fine for weeds|the soil is bad >> the farmer is bad|you don't farm at all >> i don't need to brag about weeds")
RV("cave_hider", "caves", "hiding in caves", "",
   "you spend all day in caves because nobody wants you up here|the bats are your only friends|crawl back in your cave",
   "i like caves >> caves don't like you|the bats are nice >> the bats are leaving|i'm mining >> you're hiding")
RV("show_off", "brag", "showing off", "",
   "nobody wants to see your diamonds again|you show off like it's a job|we get it, you found something once",
   "you're jealous >> jealous of noise?|i found more than you >> found more words, maybe|it's worth showing >> not to me")
RV("staring", "players", "staring", "",
   "stop staring at me|what are you looking at|you keep looking at my stuff",
   "i'm not staring >> you're doing it right now|i'm looking at the view >> the view is my house|your stuff is ugly >> then stop looking")
RV("clumsy", "jokes", "being clumsy", "",
   "you fall off everything|you tripped on a flat road|you'd fall off a carpet",
   "the ground was weird >> it was flat|i'm not clumsy >> you just tripped saying that|you fall too >> once. you fall daily")
RV("no_fun", "complaints", "ruining the fun", "",
   "you ruin every party|nobody laughs when you're around|you're no fun at all",
   "i'm fun >> fun like a rainy day|you're not funny >> funnier than you|parties are loud >> so are you")
RV("map_hog", "exploring", "hogging the map", "",
   "you never share the map|you keep the map like it's diamonds|you hide where the villages are",
   "it's my map >> a map of nowhere|you'd get lost anyway >> i'd find more than you|you never asked >> i'm asking now, in front of everyone")
RV("whiner", "complaints", "whining", "",
   "you whine more than a wolf|stop complaining about everything|all you do is complain",
   "i have reasons >> you have excuses|you complain too >> about you, mostly|life is hard >> life is fine, you're hard work")

PA("mine", "mining", "its mine",
   "nice mine. it's very... deep|i love how dark your mine is. very moody|your mine has such character. and gravel",
   "it's a good mine >> it's a mine|i like it dark >> clearly|gravel is part of it >> a big part")
PA("tower", "building", "its tower",
   "that tower is so tall. you must be proud|i see your tower from everywhere. everywhere|bold of you to build that so... visible",
   "i am proud >> i can tell|you can see it from far >> unfortunately|visible is good >> sometimes")
PA("horse", "animals", "its horse",
   "your horse is so... relaxed|nice horse. does it go fast or just exist|your horse looks like it's thinking about leaving",
   "he's calm >> very calm. very slow|he's fast enough >> enough for what|he's not leaving >> not yet")
PA("hunting", "mobs", "its hunting",
   "good hunting today? any at all?|i saw you chasing that chicken. inspiring|you're so patient with the mobs. very slow",
   "i got three >> three. wow|it was a fast chicken >> they're all fast for you|patience is a skill >> it is, apparently")
PA("boat", "ocean", "its boat",
   "nice boat. is it still floating?|you've got such a relaxed way of steering|your boat has character. and holes",
   "it floats fine >> fine, sure|i steer fine >> relaxed is what i said|no holes >> if you say so")
PA("humming", "random", "its humming",
   "you hum a lot, don't you|i always know where you are. from the humming|such a unique tune. every day",
   "i like humming >> we all know|is that bad >> it's a choice|it's a good tune >> it's a tune")
PA("garden", "plants", "its garden",
   "your garden is very... wild|so many weeds. i mean flowers|you really let nature do its thing, huh",
   "it's a wild garden >> very wild|those are flowers >> sure they are|nature knows best >> nature's tired")
PA("advice", "advice", "its advice",
   "thanks for the advice. i'll think about it. maybe|you always know what to say. always|i love that you share your opinion. so often",
   "you're welcome >> mhm|i'm just helping >> so much|you don't have to listen >> i know")

# Trash talk about a player a can't stand: b agrees, or sticks up for them.
DP_END_AGREE = L("anyway|forget them|they're not worth it|bozo behaviour|moving on")
DP_END_DEFEND = L("maybe|we'll see|ok, i'll watch out|i'll make up my own mind|if you say so")


def DP(key, about, talk, agree, defend, a_agree, a_defend):
    add("player_" + key, about, ["players", "gossip", "complaints"], ["players", "gossip", "argument", "friends"],
        [turn("a", L(talk)),
         turn("b", L(agree), chance=0.5, mark=["agree"]),
         turn("b", L(defend), cond=["!t:agree"], mark=["defend"]),
         opts("a", [(["t:agree"], L(a_agree)), ([], L(a_defend))]),
         opts("b", [(["t:agree"], pick(DP_END_AGREE, 3)), ([], pick(DP_END_DEFEND, 3))], chance=0.5)],
        needs=["a:dislikes_a_player", "!strangers"], weight=1.2)


DP("griefer", "a griefer",
   "{a.disliked_player} wrecked my stuff again|i swear {a.disliked_player} is a griefer|that one player broke my wall again",
   "ugh, they did that to me too|yeah, they're trouble|figures",
   "are you sure it was them?|maybe it was a creeper|they never did anything to me",
   "we should tell everyone|i'm putting up a sign|they're not welcome on my land",
   "i saw them do it|creepers don't laugh after|lucky you")
DP("thief", "a thief",
   "{a.disliked_player} took stuff from my chest|don't trust {a.disliked_player} near your chests|that player stole my iron, i know it",
   "they took my bread once|lock everything|yeah, they're a thief",
   "they paid me back once|maybe they needed it|you don't know it was them",
   "locks on everything now|they won't get a block from me|thief",
   "i do know|nobody needs my iron that bad|they paid you to look nice")
DP("liar", "a liar",
   "{a.disliked_player} lies about everything|never believe what {a.disliked_player} says|that player told me lava was safe to swim in",
   "they told me the same|they're a total troll|yeah, i don't listen to them",
   "maybe they were joking|they told me something useful once|everyone jokes",
   "classic|we should warn the new ones|bozo",
   "joking? i almost died|once in a hundred|not like that")
DP("loud", "a loud player",
   "{a.disliked_player} never stops yelling|can't stand how loud {a.disliked_player} is|that player types in caps all day",
   "SO loud|my ears|yeah, it's a lot",
   "they're just excited|it's kind of funny|they're new, maybe",
   "right?|someone tell them|i need quiet",
   "excited all day?|it's not funny at night|new for a month")
DP("vulture", "a kill stealer",
   "{a.disliked_player} stole my kill again|{a.disliked_player} always swoops in at the end|that player steals every kill",
   "they did it to me too|vultures|classic them",
   "maybe they were helping|you were taking a while|kills are free",
   "vultures, exactly|next time i'm fighting far from them|i'll steal theirs",
   "helping themselves|i was not|says you")
DP("builder", "a bad builder",
   "have you seen what {a.disliked_player} built? yikes|{a.disliked_player} builds like a slime|that player's house is an eyesore",
   "i saw it, oh no|it hurt my eyes|it's so bad",
   "i kind of like it|they're trying|it's different",
   "glad i'm not alone|someone help them|it's a crime",
   "you like it?|trying isn't enough|different is one word")
DP("trash_talker", "a trash talker",
   "{a.disliked_player} called me a noob|{a.disliked_player} talks so much trash|that player keeps saying i'm bad",
   "they do that to everyone|ignore them|they're the noob",
   "you are kind of new though|they say it to everyone, don't take it hard|just ignore it",
   "right?|i will|thanks",
   "wow. thanks|it's still rude|easy for you to say")
DP("cheater", "a cheater",
   "{a.disliked_player} cheated in the race|{a.disliked_player} cheats at everything|that player cut through the river in the race",
   "everyone saw it|they always cheat|yeah, i saw that too",
   "it's not against the rules|they were faster|maybe it's allowed",
   "nobody says anything|next time we watch them|cheater",
   "it's against the spirit of it|they were not|it's not")

# ---------------------------------------------------------------------------------------------------------------------

for t in topics:
    for tn in t["turns"]:
        for o in tn.get("options", [tn]):
            ls = o["lines"]
            groups = [ls] if isinstance(ls, list) else list(ls.values())
            for g in groups:
                for x in g:
                    for s in ([x["line"]] + x["then"]) if isinstance(x, dict) else [x]:
                        assert len(s) <= 90 and s.isascii(), (t["id"], s)

with open(out, "w", encoding="utf-8") as f:
    json.dump({"_comment": "Generated by tools/talk_gen/banter.py (don't edit by hand): discourse and insults, trolls and "
                           "ragebaiters. Roasts between friends and rivals, roast battles, roasts that go too far; baits "
                           "that b bites, sees through, out-trolls or (a newbie) falls for; bandit scams and pvp sweat "
                           "callouts; accusations and town callouts, debts, grudges, blame, apologies, passive-aggressive talk.",
               "topics": topics}, f, indent=1, ensure_ascii=True)
    f.write("\n")
print("%d topics" % len(topics))
