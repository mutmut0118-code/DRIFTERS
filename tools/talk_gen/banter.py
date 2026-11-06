"""Writes drifter_talk/topics_gen_banter.json: discourse and insults, trolls and ragebaiters (side session, 2026-11-06; the
user: "discourse and insults, trolls and ragebaiters ... as detailed and in depth as possible").

- Roasts between friends: a roasts, b fires back (a "then" answer, so the comeback fits the roast), a doubles down, they
  laugh it off. Roasts between rivals: it gets real (disagree; the nastier ones rivalry). Roasts from what the game knows
  ({b.death}, its armor, its mood, its voice). Roasts that go too far (an apology, taken or not). Roast battles (3 parts).
- Trolls and ragebait: a baits; b bites and rages (pvp sweats, speedrunners and builders get heated), sees through it, or
  plays along and out-trolls; the troll's "lmao got you" on some. A newbie falls for it (the troll owns up, or doesn't); a
  hermit ignores it. Baits aimed at what a voice cares about; a bandit's scams; a pvp sweat's callouts.
- Discourse: blame, grudges and debts, accusations, callouts in town (b says sorry or digs in), making up (trust), and
  passive-aggressive small talk between two who aren't friends.

Game trash talk only: mild words (trash, bozo, noob, clown, L, skill issue, ratio, cope), nothing about real groups.
Run: python tools/talk_gen/banter.py src/main/resources/data/drifter/drifter_talk/topics_gen_banter.json
"""
import json
import random
import re
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


def turn(by, lines, chance=None, cond=None, mark=None):
    d = {"by": by, "lines": lines}
    if chance is not None and chance < 1:
        d["chance"] = chance
    if cond:
        d["if"] = cond
    if mark:
        d["mark"] = mark
    return d


def ask(lines, answers):
    """Lines whose answer is one of these (the other's next turn says it)."""
    return [{"line": line, "then": answers} for line in lines]


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

RIVAL_FINAL = L("whatever|say that again|nobody asked|cope|ratio|cry about it|you're one to talk|ok bozo|cute|"
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
# 1. Roasts between friends: a roasts, b fires back (the comeback fits), a doubles down, b laughs or gives in.


def R(key, tag, about, needs, roasts, backs, more):
    turns = [turn("a", ask(L(roasts), L(backs))),
             turn("b", ["lol"]),
             turn("a", L(more)),
             turn("b", V(pick(FRIEND_END, 4), FRIEND_END_V), chance=0.75),
             turn("a", V(pick(FRIEND_CLOSE, 3), FRIEND_CLOSE_V, k=2), chance=0.4)]
    add("roast_" + key, about, ["roasts", "jokes", tag], [tag, "roasts", "jokes", "friends"], turns,
        needs=["friends"] + N(needs), weight=0.7)


# Houses and builds.
R("house_tumor", "building", "its house", "",
  "your house looks like a cobblestone tumor|did you build your house or did it just grow there|your base looks like a creeper hit it and you said good enough",
  "at least i have a house, you live in a hole|says the one with a dirt roof|it's called rustic, look it up",
  "a pig walked up to it and turned around|the villagers point at it when they walk past|i'm just saying, even zombies skip it")
R("house_box", "building", "its box of a house", "",
  "your house is a box with a door on it|is your house a house or a storage crate|nice box. when's the house going up",
  "boxes are efficient|a box keeps the rain off, that's the job|your house is the same box but uglier",
  "efficient at looking sad maybe|i've seen better shapes on a sponge|even a chicken coop has more going on")
R("dirt_hut", "building", "its dirt hut", "b:has_hut",
  "you still live in a dirt hut lol|your house is made of the stuff i walk on|cozy dirt hut you got there",
  "dirt is a building block, look it up|it's temporary|it's a starter home, ok",
  "temporary since day one|grass is growing on your roof bro|a sheep tried to eat your wall")
R("no_windows", "building", "its windowless house", "",
  "your house has zero windows, are you hiding something|how do you even see in there, no windows|your base looks like a prison with a door",
  "windows let the creepers look in|glass costs sand and sand is far|i like my privacy thanks",
  "the creepers already know where you live|it's giving dungeon|a mob spawner has more windows")
R("leaning_tower", "building", "its tower", "",
  "your tower leans, i swear it leans|that tower is just a pillar with a dream|why is your tower one block wide",
  "it's a design choice|it's the view that matters|you're just jealous of the height",
  "the only view is the ground getting closer|one creeper and it's a stump|a parrot landed on it and it wobbled")
R("torch_spam", "building", "all its torches", "",
  "you have like four hundred torches on your house|your base is brighter than the sun|you put a torch on every single block",
  "no mobs spawn though|light is safety|better than your pitch black cave",
  "you can see it from the nether|it's like living inside a lava lake|the moon is jealous")
R("ugly_colours", "building", "its house's colours", "",
  "your house is pink and green and orange, why|who taught you colours, a slime|your house looks like a parrot exploded",
  "it's vibrant|i used what i had|your house is all grey, that's worse",
  "vibrant like a warning sign|i need to squint to look at it|the sheep you dyed are embarrassed")
R("cobble_everything", "building", "its cobblestone builds", "",
  "everything you build is cobblestone|you've never heard of any block besides cobble huh|your whole base is one grey block",
  "cobble is free and creeper proof-ish|it's a classic|i'm saving the nice stuff",
  "saving it for what, your next life|cobble isn't a style, it's a symptom|even a silverfish would find it boring")
R("tiny_house", "building", "its tiny house", "b:has_house",
  "your house is so small you have to step outside to turn around|i've seen bigger chests than your house|how do you fit in your house",
  "cozy is a vibe|less to light up|small but mine",
  "your bed is half outside|you sleep standing up in there i bet|the door takes up a whole wall")
R("holey_bridge", "building", "its sky bridge", "",
  "your sky bridge has no railings, that's a death trap|who builds a bridge with holes in it|your bridge looks like a broken ladder",
  "railings are for cowards|i never fall|it's minimalist",
  "you fell off it twice last week|the gaps are not minimalist, they're missing|i watched you walk it, very brave, very dumb")
R("door_wall", "building", "its door", "",
  "your door opens into a wall|you put your door on backwards again|who puts a door facing a cliff",
  "keeps the zombies confused|it's a security feature|works fine for me",
  "it confused you more than the zombies|you walked into it yesterday|security from you getting home")
R("farm_living_room", "building", "the wheat in its house", "",
  "your house is half farm, half house, all mess|why is there wheat in your living room|you planted carrots in your kitchen floor",
  "fresh food every morning|it's efficient|you're jealous of my indoor farm",
  "efficient at bringing in rabbits|you sleep next to a cow|it's a barn with a bed in it")
R("unfinished", "building", "its unfinished build", "",
  "how long has your build been unfinished now|your castle is just one wall|is that a build or scaffolding with feelings",
  "art takes time|it's a work in progress|i'm adding the rest tomorrow",
  "you said tomorrow a week ago|the scaffolding has moss on it now|at this rate the moon finishes it first")
R("no_symmetry", "building", "its lopsided house", "",
  "nothing on your house is symmetrical|your windows are all different sizes|left side of your house and right side are strangers",
  "symmetry is boring|it's got personality|trees aren't symmetrical either",
  "personality like a creeper crater|my eyes hurt from the left side|i counted, three windows, three sizes")
R("lava_moat", "building", "its lava moat", "",
  "you put a lava moat around a dirt house|lava moat? to protect your dirt?|nice lava moat, shame about what it guards",
  "safety first|it's for the mobs|you never know who's coming",
  "nobody's coming to steal dirt|it set your own fence on fire|you burned your own door last week")
R("glass_house", "building", "its glass house", "",
  "your house is all glass, i can see you eating|living in a glass box, bold|you know everyone can see you sleep right",
  "i like the light|nothing to hide|natural light is good for you",
  "i've watched you walk into the glass twice|the zombies just stare at you all night|i saw you dance at night, we all did")
R("mole_base", "building", "its underground base", "",
  "you live underground like a mole|when did you last see the sun|your base is a hole with a bed",
  "underground is safe|the surface is overrated|it's a bunker, not a hole",
  "a bunker with dirt walls is a hole|you came out yesterday and squinted at the sun|the bats think you're one of them")
R("empty_mansion", "building", "its empty mansion", "b:has_house",
  "your house is huge and there's nothing in it|ten rooms and one chest, nice|your base echoes",
  "room to grow|i'm minimalist|it's spacious",
  "spacious like an empty desert|the bats moved in for the room|i yelled in there and it yelled back")
R("zigzag_path", "building", "its path", "",
  "your path to your house zigzags for no reason|did a lost sheep lay your path|why does your path go through a pond",
  "it's scenic|it follows the land|i like the walk",
  "scenic until you fall in the pond|i got lost on it for a day|your path needs a path")
R("fence_gap", "animals", "its fence", "",
  "your fence has a gap, every animal escaped|half your fence is missing|your animal pen is just a suggestion",
  "they come back|freedom is important|the gap is a gate",
  "the cows did not come back|a gate swings, a gap just sits there|your pig is in the next biome")
R("bed_by_door", "home", "where its bed is", "",
  "your bed is in the middle of the room for some reason|who puts the bed by the front door|your bed is right next to the furnace",
  "easy to reach|i like it warm|it's a power move",
  "power move until the zombie walks in|you're one door away from a nightmare|you sleep like mob bait")
R("copied_house", "building", "its copied house", "",
  "your house looks exactly like mine but worse|did you copy my house? badly?|you built my house from memory, didn't you",
  "yours was built off a village hut|copying is flattery|mine has a better roof though",
  "better roof? it leaks|flattery that leaks|you even copied my mistakes")
R("village_hut", "villagers", "the hut it moved into", "",
  "your house is just a village hut you moved into|did you kick a villager out to get that house|nice house, did the farmer move out",
  "he left willingly|it's called recycling|it was empty, i checked",
  "he's still standing outside, sad|i saw him knock last night|the villager wants his bed back")
R("trapdoor_spam", "decor", "its trapdoors", "",
  "you used trapdoors on everything, why|your house is ninety percent trapdoors|calm down with the trapdoors",
  "trapdoors are detail|they make it pop|you just can't see the vision",
  "the vision is a trapdoor factory|i tripped on one in your hallway|you trapdoored the trapdoors")
R("leaky_roof", "building", "its leaky roof", "",
  "your roof has holes, it rains inside|it rains in your kitchen, i saw|why do you stand in the rain inside your own house",
  "it's a skylight|free water|it's ventilation",
  "a skylight has glass|your crops grow on the floor now|your bed is soaked")
R("spawn_house", "building", "where it built", "",
  "you built your house right at spawn, so original|first spot you saw, first house you built huh|you didn't even walk ten blocks to build",
  "why walk far|location, location|i was tired",
  "you were tired on day one?|lazy is a location now|you build wherever you stop")
R("ugly_statue", "decor", "its statue", "",
  "what is that statue by your house supposed to be|your statue looks like a melted creeper|the thing on your lawn scares me",
  "it's me|it's a dragon, obviously|art isn't for everyone",
  "if that's you, see a cleric|a dragon with three legs|the iron golem won't go near it")
R("moss_house", "building", "its mossy house", "",
  "your house is all moss, is it alive|you didn't build a house, you grew one|your walls are fuzzy",
  "nature takes over|it's a cottage look|moss is beautiful",
  "nature should take it faster|a cottage of fuzz|i swear it grew a block since yesterday")
R("portal_bedroom", "home", "the portal by its bed", "",
  "your nether portal is in your bedroom|who puts a portal next to the bed|you sleep next to a portal, brave",
  "quick commute|it's a night light|it's purple, it matches",
  "a zombie piglin will walk into your bed one day|enjoy the portal noise all night|one day you'll wake up in the nether")
R("wool_house", "building", "its wool house", "",
  "you built your house out of wool|your house is a fire hazard with a door|your house is made of sheep, basically",
  "it's soft|it's colourful|it's cozy",
  "one lightning bolt and you're sleeping outside|cozy until it's on fire|the sheep are scared of you now")
R("bad_stairs", "building", "its stairs", "",
  "your stairs are all different heights|i tripped up your stairs twice|your staircase is a parkour course",
  "keeps you fit|you just walk badly|it's character",
  "character nearly broke my legs|i need a ladder to use your stairs|the zombies give up halfway")
R("tree_house", "trees", "its tree house", "",
  "your tree house is three planks and a prayer|that's not a tree house, it's a nest|how do you get up your tree house, jump?",
  "it's got charm|ladders are for the weak|jumping is good for you",
  "charm is not a support beam|i fell out of it just looking at it|a sheep sneezes and it's leaves again")
R("sand_house", "building", "its sand house", "",
  "you built your house out of sand, it'll fall|sand house? did the desert dare you|one wrong block and your house slides away",
  "it's stable if you're careful|it was all i had|desert style",
  "careful is not a foundation|i saw a wall slide this morning|the cacti are laughing at you")
R("crooked_walls", "building", "its crooked walls", "",
  "nothing in your house lines up|your walls are one block off everywhere|did you build that in the dark?",
  "it was night, so yeah|it's handmade|close enough is good enough",
  "close enough is how houses fall down|i see you built the wall twice|the pigs are confused by your corners")
R("tour_guide", "home", "the tours of its house", "b:has_house",
  "you give everyone tours of your one room house|i've had three tours of your house now|you show off your house like it's a castle",
  "it's worth seeing|people love it|it has charm",
  "the tour is ten seconds long|people love leaving|charm of a cave")

# Gear, tools and weapons.
R("leather_armor", "armor", "its leather armor", "b:armor_leather",
  "nice leather armor, did a cow give it to you|leather armor, still? bold|you're dressed like a cow's cousin",
  "it's dyeable|it's light|better than nothing, which is what you had last week",
  "dyed leather is still leather|light enough that a zombie's punch goes right through|a chicken could peck through that")
R("gold_armor", "armor", "its gold armor", "b:armor_gold",
  "gold armor? you know that's the weakest right|shiny and useless, like your sword arm|you look like a piglin's dream",
  "piglins love me|it looks good|style over stats",
  "style won't stop a skeleton|the piglins only love the armor|it breaks if you sneeze")
R("no_armor", "armor", "having no armor", "b:armor_none",
  "where's your armor?|no armor? are you trying to die|you walk around with no armor like a baby zombie",
  "armor slows you down|i dodge|i'm saving up",
  "dodging isn't a thing when you're this slow|saving up since day one|the skeletons love you")
R("wooden_pick", "tools", "its wooden pickaxe", "",
  "you still mine with a wooden pickaxe?|that pickaxe is older than the world|your tools are made of sticks and hope",
  "it works|stone is coming|it has sentimental value",
  "sentimental value doesn't mine iron|stone has been coming for days|i watched you take ten seconds on one block")
R("broken_tools", "tools", "its worn out tools", "",
  "your sword is about to break, i can see the bar|how are all your tools in the red|you never repair anything huh",
  "it's got one more fight in it|repairing is for quitters|i like living dangerously",
  "one more fight and then you punch zombies|dangerously is one word for it|your pickaxe is crying")
R("no_shield", "weapons", "never using a shield", "",
  "you don't even use a shield lol|you know shields exist right|you take every arrow to the face",
  "shields are for cowards|i block with my face|i need that hand for food",
  "your face is not blocking well|cowards who live, sure|i counted the arrows in you, nineteen")
R("four_swords", "weapons", "all its swords", "",
  "why do you carry four swords|you have more swords than skills|four swords and you still lose to spiders",
  "backups|one for each mob|you never know",
  "backups for losing faster|one for each loss|you do know, you lose")
R("bane_sword", "weapons", "its bad enchant", "",
  "your sword has bane of arthropods, lol|who puts bane on their only sword|you got a bad enchant and kept it",
  "spiders fear me|it was cheap|it's better than nothing",
  "spiders still win though|cheap like your sword skills|enjoy one shotting silverfish")
R("fishing_rod_fight", "weapons", "fighting with a fishing rod", "",
  "why are you holding a fishing rod in a fight|you brought a fishing rod to a cave|fishing rod as a weapon, bold choice",
  "it pulls mobs in|it's a strategy|i was fishing first",
  "it pulled the creeper right to you|a strategy of dying slowly|that creeper got hooked, so did you")
R("iron_unused", "armor", "the iron it won't use", "",
  "you have iron and you're still in leather|craft some armor already|you're sitting on iron like a dragon on gold",
  "i'm saving it|i need it for tools|armor is next",
  "saving it for your funeral|next has been next for days|the iron is lonely in that chest")
R("bad_aim", "weapons", "its aim", "",
  "you missed that skeleton six times|your arrows are scattered across three biomes|you couldn't hit a wall with that bow",
  "the wind took it|it was moving|i'm warming up",
  "there's no wind down there|warming up for a week|the skeleton was standing still laughing")
R("torch_weapon", "weapons", "fighting with a torch", "",
  "why do you swing torches at zombies|you hit a zombie with a torch?|your weapon of choice is a torch",
  "fire damage|it's light and pain|it works, trust",
  "a torch doesn't do fire damage|it's just light and you dying|the zombie looked confused, that's it")
R("gold_sword", "weapons", "its gold sword", "",
  "a gold sword, really?|that gold sword breaks if you look at it|nice gold sword, it'll last two zombies",
  "it's fast|it looks cool|piglins respect it",
  "fast at breaking|looks cool in pieces|the piglins are laughing")
R("diamond_hoe", "tools", "its diamond hoe", "",
  "you used diamonds on a hoe|a diamond hoe, of all things|you had diamonds and made a hoe",
  "farming is serious|the hoe deserves it|crops are life",
  "your sword is still stone though|the wheat doesn't care what hits it|priorities of a chicken")
R("armor_mismatch", "armor", "its mismatched armor", "",
  "your armor is four different materials|leather boots and an iron helmet, who dressed you|you look like a scarecrow made of loot",
  "it's a look|i wear what i find|mix and match",
  "a look that says help me|you find a lot of junk|the zombies dress better")
R("dyed_armor", "armor", "its dyed armor", "",
  "pink leather armor, really|you dyed your armor bright green|you look like a slime in a coat",
  "fashion|you can see me from far away|it's my colour",
  "so can every skeleton|fashion for mobs to aim at|it's everyone's target colour now")
R("axe_fighter", "weapons", "fighting with an axe", "",
  "you fight with an axe like a lumberjack|you treat every mob like a tree|swinging an axe at a spider, nice",
  "axes hit harder|it breaks shields|it's an all in one",
  "harder and slower|the spider doesn't have a shield|all in one, all the misses")
R("elytra_crash", "gear", "its elytra flying", "b:knows_elytra",
  "you crash your elytra into every wall|i saw you fly into a mountain|your elytra spends more time broken than flying",
  "the mountain moved|i was testing the brakes|it's a landing strategy",
  "mountains don't move, you do, into them|there are no brakes|the strategy is called falling")
R("stone_sword", "weapons", "its stone sword", "",
  "still using a stone sword, huh|your sword is literally a rock on a stick|you know iron swords exist right",
  "stone gets the job done|it's reliable|iron is for the pickaxe",
  "the job being dying slowly|reliable at losing|you have iron, i saw it")
R("helmet_only", "armor", "its lone helmet", "",
  "you're wearing just a helmet, why|only a helmet? protecting the empty part?|that helmet is doing a lot of work for nothing",
  "head safety first|the helmet's the most important|i'm building up",
  "what's in there to protect|building up since forever|nice hat, shame about the rest")
R("pumpkin_head", "gear", "the pumpkin on its head", "",
  "why are you wearing a pumpkin on your head|you look like a snow golem's cousin|that pumpkin isn't fooling anyone",
  "endermen leave me alone|it's spooky|it's a fashion statement",
  "endermen leave you alone out of pity|spooky like a scarecrow|honestly the pumpkin is an upgrade")
R("pyro", "tools", "its flint and steel", "",
  "you light everything with flint and steel, pyro|why do you carry flint and steel everywhere|you set a forest on fire for fun",
  "it's useful|portals need it|that fire was an accident",
  "useful at burning your own house|the forest disagrees|three accidents is a habit")
R("lava_bucket", "tools", "its lava bucket", "",
  "you carry a lava bucket like it's a snack|one wrong click and you burn the house|i've seen you pour lava on your own feet",
  "it's a weapon|i'm careful|that was one time",
  "careful people don't have burn marks|one time per day|the weapon is pointed at you")
R("junk_hotbar", "gear", "its hotbar", "",
  "your hotbar is a stick, a seed and dirt|what's even in your hotbar|you're carrying nothing useful again",
  "i travel light|the dirt is useful|seeds are life",
  "light like your head|dirt for your next hut|plant yourself somewhere")
R("netherite_flex", "armor", "its netherite", "b:armor_netherite",
  "oh look who's in netherite now|you wear netherite to pick flowers|all that armor and you still scream at spiders",
  "it's called being prepared|jealous|earned it",
  "prepared for flowers to attack|i saw you run from a chicken in it|earned it and still died to fall damage")

# Deaths.
R("baby_zombie", "death", "dying to a baby zombie", "",
  "you died to a baby zombie lol|a baby zombie took you out, legend|remember when the baby zombie got you",
  "it was fast ok|those things are cracked|you'd die too, they're tiny",
  "it was riding a chicken and still won|tiny and still beat you|the baby has more kills than you")
R("ladder_fall", "death", "falling off a ladder", "",
  "you died to fall damage in a cave you made|you fell off your own roof|who dies falling off a ladder",
  "the ladder betrayed me|gravity was rude|it was taller than it looked",
  "it's your ladder, you built it|gravity doesn't play favorites|it was three blocks")
R("lava_dig", "death", "falling in lava", "",
  "you went swimming in lava again?|you mined straight down into lava, classic|how many times have you fallen in lava this week",
  "it was dark|the lava came out of nowhere|i was distracted",
  "lava glows, it's not dark|lava doesn't sneak up on you|you're distracted every day")
R("creeper_hug", "death", "the creeper that got it", "",
  "you let a creeper hug you again|you just stood there and let the creeper go off|creeper walked up and you said hi",
  "i didn't hear it|it was silent|it came from behind",
  "it hissed. loudly|creepers don't do silent, you do deaf|every creeper comes from behind you, weird")
R("puddle_drown", "death", "drowning", "",
  "you drowned in a puddle|you drowned two blocks from the shore|how do you drown in a river",
  "it was deep|i forgot to swim up|the current took me",
  "two blocks deep|swimming up is the whole skill|the current was standing still")
R("chicken_fight", "death", "losing to a chicken", "",
  "a chicken almost killed you|you lost a fight to a chicken|i saw you take damage chasing a chicken. how",
  "it was a jockey|i fell, not the chicken|that chicken was strong",
  "chickens don't fight back|you fell running after it|it laid an egg on your grave")
R("sand_buried", "death", "getting buried in sand", "",
  "you got buried by sand|sand fell on you and that was it|you dug up and the sand won",
  "never dig up|it was gravel|it was a trap",
  "the trap was gravity|gravel, sand, same fate|you dug into it, you set the trap")
R("starved", "death", "starving", "",
  "you starved with food in your inventory|how do you starve next to a farm|you forgot to eat. again",
  "i was busy|i was saving it|food is for later",
  "later never came|saving it for your next life|the farm was right there")
R("cactus_death", "death", "the cactus", "",
  "a cactus killed you|you hugged a cactus to death, yours|death by cactus is rough",
  "it was dark|i was knocked into it|cacti are sneaky",
  "cacti don't move|knocked into it by a rabbit|it's a plant")
R("bed_explode", "death", "its exploding bed", "",
  "you tried to sleep in the nether lmao|you blew yourself up with a bed|bed in the nether, you didn't know?",
  "nobody told me|i was tired|i thought it'd work",
  "tired forever now|the bed did not work|it blew a hole in the whole floor")
R("own_tnt", "death", "its own tnt", "",
  "you blew yourself up with your own tnt|lit the tnt and just stood there|your tnt mining killed you",
  "the fuse was short|i thought i was further|it was a test",
  "the fuse is always four seconds|further from your brain|test failed")
R("skeleton_snipe", "death", "the skeleton that shot it", "",
  "a skeleton sniped you from across a ravine|you got shot off a cliff by a skeleton|skeleton aim beat your armor",
  "aimbot skeleton|i had no cover|it was three skeletons",
  "it was one, i counted|cover is called a block|three is not that many")
R("bees", "death", "the bees", "",
  "you got killed by bees, how|you hit a bee and the whole hive came|bees took you out",
  "they swarmed|i just wanted honey|bees are scary",
  "you punched their house|wanted honey, got stung|bees die after one sting, you died first")
R("void_fall", "death", "falling into the void", "b:knows_end",
  "you fell into the void|you bridged out into nothing|you walked off the edge of the end",
  "the edge was dark|i was looking at the sky|an enderman pushed me",
  "the whole end is dark|endermen don't push, they stare|you looked at the sky and walked off")
R("golem_clap", "death", "the iron golem", "",
  "you got clapped by an iron golem|you hit a villager and the golem sent you flying|iron golem one, you zero",
  "the villager started it|it was an accident|golems are overpowered",
  "the villager was selling bread|you swung first|it threw you to the moon")
R("wolf_pack", "death", "the wolves", "",
  "you got eaten by the wolves you hit by accident|you punched a wolf and the pack got you|death by wolf pack, brutal",
  "i was aiming at a sheep|they ganged up|wolves are unfair",
  "the sheep got away too|you started a war with dogs|you hit the one with the angry eyes")
R("warden_wake", "death", "waking the warden", "b:knows_warden",
  "you woke the warden and screamed|you sneaked so loud the warden came|you threw a snowball in the deep dark?",
  "i sneezed|it was a sculk sensor|i didn't know",
  "you sneezed for three minutes|you stepped on twenty sensors|the warden knows your name now")
R("phantoms", "death", "the phantoms", "",
  "phantoms got you because you never sleep|three phantoms and you're done, nice|you're the reason phantoms exist",
  "sleep is overrated|i was busy|they came out of nowhere",
  "they came out of the sky, like always|busy not sleeping|go to bed")
R("pufferfish", "food", "eating a pufferfish", "",
  "you ate a pufferfish|poison from a pufferfish, chef's kiss|why would you eat that",
  "i was hungry|it looked tasty|food is food",
  "it's got spikes|tasty like poison|food that hurts you is not food")
R("witch_loss", "death", "the witch", "",
  "a witch beat you with potions|you lost a potion fight to a witch|a witch threw two bottles and you folded",
  "she healed|witches cheat|she had like six potions",
  "she healed because you tickled her|they're just potions|you had a sword")
R("ghast_ball", "death", "the ghast's fireball", "b:knows_nether",
  "you got hit by a ghast fireball you tried to punch|you swung at the fireball and missed|a ghast knocked you into lava",
  "timing is hard|the ghast was far|it was two ghasts",
  "you swung at nothing|far from your sword|two ghasts, zero hits")
R("silverfish", "death", "the silverfish", "",
  "silverfish swarmed you lol|you mined a stronghold block and the fish got you|death by silverfish, wow",
  "they were everywhere|it was a spawner|they're so fast",
  "they're tiny|you mined the spawner block|they're tiny and you're slow")
R("campfire", "death", "standing on a campfire", "",
  "you walked onto a campfire|you died standing on a campfire|campfire's for cooking, not you",
  "i didn't see it|i was cooking|it was hidden",
  "it's on fire, you see it|cooking yourself|hidden in plain sight")
R("pillagers", "death", "the pillagers", "",
  "pillagers turned you into a pincushion|you walked into a pillager outpost with no armor|you charged a raid alone",
  "i was brave|i wanted the loot|they had numbers",
  "brave and dead|the loot is still there|they had crossbows, you had hope")
R("spider_loss", "death", "losing to a spider", "",
  "a spider got you right outside your own house|spider climbed your wall and you panicked|you lost to one spider",
  "it was a cave spider|it jumped|spiders are creepy",
  "it was a normal spider|it jumped once|it was more scared of you")
R("enderman_stare", "death", "staring at an enderman", "",
  "you stared at an enderman. on purpose?|you looked an enderman in the eye and lost|why would you look at it",
  "i forgot|it was right there|i wanted to see",
  "forgot your whole life|you saw. then you died|it teleported behind you, classic")
R("magma_feet", "death", "the magma blocks", "",
  "you walked on magma blocks|you sprinted across magma like it was carpet|your feet are toast",
  "i didn't sneak|it looked like stone|it was dark",
  "it glows, it's orange|stone doesn't glow|sneak next time")
R("ravine_night", "death", "the ravine", "",
  "you walked into a ravine at night|ravine got you again|you sprinted off a ravine edge",
  "it was dark|my torches were out|i was chasing a sheep",
  "you had torches|the sheep lived|ravines are huge, how")
R("boat_sink", "death", "jumping out of its boat", "",
  "you jumped out of your boat in the middle of the ocean|how do you drown with a boat right there|you lost your boat at sea",
  "i dropped it|it drifted|i panicked",
  "panicked in a boat|it drifted right past you|boats float, you don't")
R("died_with_loot", "death", "losing its loot", "",
  "you died with a full inventory and lost it all|you had diamonds and died before you got home|you always die on the way home",
  "i got some back|it's a curse|the despawn timer hates me",
  "some is not all|cursed by bad decisions|five minutes, you had five minutes")

# Skills.
R("pvp_clicking", "fighting", "how it fights", "",
  "your fighting is all clicking and praying|you swing like you're swatting flies|you fight like a confused villager",
  "it works|i'm unpredictable|you fight worse",
  "unpredictable even to yourself|works against chickens|i've seen you miss a zombie standing still")
R("parkour", "jokes", "its jumping", "",
  "you can't even jump two blocks|you missed a one block gap|your parkour is falling with style",
  "my feet slipped|it was ice|i was off balance",
  "slipped on regular grass|it was dirt|you're off balance standing still")
R("slow_mining", "mining", "how slowly it mines", "",
  "you mine like you're scared of the rock|how is it taking you that long to mine stone|watching you mine is like watching wheat grow",
  "i'm careful|i'm looking for ore|my pickaxe is tired",
  "careful at being slow|the ore's waiting|your pickaxe is fine, you're tired")
R("lost_home", "exploring", "getting lost", "",
  "you got lost walking home again|how do you get lost in your own base|you need a map to find your bed",
  "everything looks the same|the trees moved|i was exploring",
  "trees don't move|you explored your own hallway|you live there")
R("brick_swim", "ocean", "how it swims", "",
  "you swim like a brick|i saw you sink in a puddle|your swimming is just slow drowning",
  "i was diving|swimming is hard|the water was weird",
  "diving straight to the bottom|water is water|you sank, it was a pond")
R("boat_steer", "ocean", "how it steers a boat", "",
  "you crash your boat into every shore|you can't steer a boat|your boat goes everywhere but straight",
  "boats are slippery|the river curves|i was sightseeing",
  "boats are on water, that's the point|you hit a lily pad and gave up|sightseeing the bottom of the river")
R("slow_craft", "crafting", "how slowly it crafts", "",
  "you take forever to craft a table|you need a recipe book for sticks|how do you mess up a crafting table",
  "i was thinking|planks are tricky|i like to be sure",
  "thinking hurts, i get it|planks are one log|you made buttons instead")
R("trample", "farming", "trampling its crops", "",
  "you trample your own crops every day|you jump on your farmland like a rabbit|your wheat field is just dirt now",
  "i was in a hurry|the crops were in the way|farmland is fragile",
  "in a hurry to wreck it|the crops are the farm|fragile like your skills")
R("slow_build", "building", "how slowly it builds", "",
  "you place blocks like each one costs you|ten minutes for one wall|you build slower than a turtle walks",
  "quality takes time|i measure twice|it's precision",
  "the quality isn't there either|you measure twice, place wrong once|the turtle finished its house")
R("pig_chase", "animals", "chasing a pig", "",
  "you chased a pig for ten minutes|a cow outran you|you can't catch a chicken",
  "the pig was fast|it went in the water|i let it go",
  "pigs aren't fast|cows don't swim fast|you let it go because it won")
R("no_dodge", "fighting", "how it dodges", "",
  "you dodge like a fence post|everything hits you|you walk into every arrow",
  "i tank hits|dodging is for the weak|i'm a magnet for damage",
  "you tank them with your face|weak and alive beats strong and dead|a magnet for respawns")
R("loud_sneak", "caves", "how it sneaks", "",
  "you sneak with all the stealth of a goat|you try to sneak and it sounds like a stampede|the whole cave knew you were coming",
  "goats are stealthy|i wanted them to know|stealth is boring",
  "goats ram things, you ram walls|they knew and they laughed|boring and alive though")
R("raw_chicken", "food", "its cooking", "",
  "you burned the steak, how, it's a furnace|your food is always raw|you ate raw chicken again?",
  "i was hungry|it's faster|raw is fine",
  "raw chicken gives you hunger, enjoy|fine until your stomach says no|it's one furnace")
R("bad_trader", "trade", "its trading", "",
  "you paid ten emeralds for one apple|the villagers see you coming|you trade like the villagers are robbing you. they are",
  "he looked hungry|i like that villager|it was a good apple",
  "the villager retired off your emeralds|they laugh after you leave|apples grow on trees")
R("pearl_aim", "jokes", "its ender pearls", "",
  "you threw your pearl into a wall|you pearled yourself into lava|your ender pearls go everywhere but where you aim",
  "it bounced|the angle was weird|i meant to",
  "pearls don't bounce|you meant to land in lava?|the angle was straight")
R("floating_trees", "trees", "the trees it leaves", "",
  "you leave floating trees everywhere|the forest is full of your half trees|you never finish chopping a tree",
  "the top's too high|the leaves will fall|it's art",
  "art that annoys everyone|leaves fall, logs don't|you have legs, jump")
R("redstone_door", "redstone", "its redstone", "b:knows_redstone",
  "your redstone door opens itself at night|your piston door eats people|your redstone is a fire hazard",
  "it's a feature|it's working as intended|you wouldn't understand",
  "a feature that ate a sheep|intended to fail|i understand it eats people")
R("missed_sleep", "sleep", "missing bedtime", "",
  "you can't even sleep on time|you miss the sleep window every night|you're always awake when the zombies come",
  "i'm a night person|sleep is for the weak|i like the moon",
  "the zombies are night people too|weak people sleep, you die|the moon doesn't like you back")
R("shield_late", "weapons", "its shield timing", "",
  "you raise your shield after the hit|your shield timing is a joke|you block the air after the arrow lands",
  "it's anticipation|i'm practicing|the arrow was fast",
  "anticipating the last hit|practice more, block less air|arrows are slow, you're slower")
R("empty_mob_farm", "mobs", "its mob farm", "",
  "you built a mob farm that farms you|your mob farm has zero mobs in it|your mob farm is just a dark room with you in it",
  "it's a work in progress|they'll come|i'm the bait",
  "bait that dies|they came for you|in progress since forever")
R("lost_chest", "storage", "the chest it forgot", "",
  "you forgot where you put your diamonds again|you have a chest you forgot exists|how do you lose a whole chest",
  "i have a lot of chests|it's hidden|i'll find it",
  "hidden from you too|a lot of chests, zero labels|the chest is lonely")
R("bucket_clutch", "jokes", "its water bucket clutch", "",
  "you missed the water bucket clutch again|you try the bucket clutch every time and splat|your water bucket timing is sad",
  "i got it once|almost|the bucket was slow",
  "once in a hundred|almost is a respawn screen|the bucket's fine")
R("scared_of_bats", "caves", "panicking at bats", "",
  "you yell every time you see a spider|you scream at every creeper|you panic at a bat",
  "bats are loud|they're startling|i was surprised",
  "bats are harmless|surprised daily|a bat made you drop your sword")
R("boot_fishing", "fishing", "its fishing luck", "",
  "you fish for an hour and get boots|you only fish up junk|your fishing rod is a boot magnet",
  "boots are loot|i'm waiting for the book|it's relaxing",
  "loot for a zombie|the book's ignoring you|relaxing and useless")
R("scared_bridge", "building", "how it bridges", "",
  "your bridges look like a staircase that gave up|you bridge like a scared turtle|you sneak bridge everything",
  "safety first|i'm careful|it holds",
  "it holds, your pride doesn't|scared turtles build better|sneaking for an hour")
R("always_runs", "fighting", "running from fights", "",
  "you run from every fight|you sprint away from zombies like they're dragons|your fight plan is just running",
  "running is a strategy|i pick my battles|i'm still alive",
  "alive and scared|your battles get picked by zombies|the strategy of a chicken")
R("bad_brewer", "potions", "its brewing", "b:knows_brewing",
  "you brewed a potion of weakness and drank it|you mix potions like a confused witch|your brewing stand is cursed",
  "it was an experiment|i labeled it wrong|it's science",
  "the experiment is you|labels need writing|science says stop")
R("map_lost", "exploring", "its maps", "",
  "your maps are all ocean|you made a map of the inside of your house|you got lost holding a map",
  "the ocean's big|i was testing|the map was wrong",
  "the map was fine|testing being lost|you held it upside down")
R("armor_at_home", "armor", "the armor it leaves at home", "",
  "you made armor and left it in a chest|armor doesn't work in a chest, you know|you fight in your shirt and your armor's at home",
  "it's safe there|i'm saving durability|i forgot",
  "safe and useless|durability for your grave|you forget everything")
R("five_torches", "caves", "how it goes caving", "",
  "you go caving with five torches|you go into caves with no food|you cave like you want to get lost",
  "i travel light|torches are heavy|i find my way",
  "you found your way back with my help|torches weigh nothing|light like your head")
R("animal_overflow", "animals", "its overcrowded pen", "",
  "you bred so many cows i can't see the grass|you have forty chickens and no eggs|your animal pen is wall to wall sheep",
  "i like animals|they're my family|more food",
  "your family is food|forty chickens is a cult|the sheep can't move")
R("never_sprints", "jokes", "how slowly it walks", "",
  "you walk everywhere, never sprint|you're the slowest walker i know|turtles move faster than you",
  "i'm saving food|i like the scenery|why rush",
  "turtles rush more|scenery of being late|saving food to be slow")
R("lucky_vein", "diamonds", "its lucky diamonds", "",
  "you found diamonds by accident and called it skill|you tripped into a diamond vein, admit it|lucky isn't a skill",
  "i'm talented|it's called a nose for ore|jealous",
  "talented at falling into holes|your nose was in the dirt|you fell into that vein")
R("never_finishes", "building", "its unfinished projects", "",
  "you start a hundred projects and finish zero|how many half built things do you have|your base is a museum of unfinished stuff",
  "i'm creative|i'll finish them|i like starting",
  "creative at quitting|you'll finish none|starting is half the fun, and apparently all of it")
R("shot_villager", "villagers", "shooting a villager", "",
  "you shot a villager by accident|you hit the farmer with an arrow|the villagers duck when you draw your bow",
  "he walked in front|it was an accident|he's fine",
  "he walked in front of your miss|the farmer still flinches|they hide when you visit")

# Farms, food and animals.
R("tiny_farm", "farming", "its tiny farm", "",
  "your wheat farm is four blocks|that's not a farm, that's a flower pot|you call that a farm?",
  "it's a start|quality over quantity|it feeds me",
  "it feeds one chicken|quality wheat, still four blocks|a start since day two")
R("carrot_diet", "food", "its carrot diet", "",
  "you only eat carrots, are you a rabbit|carrots every meal, every day|i've never seen you eat anything but carrots",
  "carrots are good for you|they're easy|they help me see in caves",
  "they don't help you see|easy and boring|you're half rabbit at this point")
R("rotten_flesh", "food", "eating rotten flesh", "",
  "you eat rotten flesh like it's a snack|why do you eat zombie bits|you eat what the zombies drop, gross",
  "food is food|it's free|i like the risk",
  "free tummy ache|the zombies want it back|a risk of hunger every time")
R("named_cow", "pets", "naming its animals", "",
  "you named your cow, didn't you|you name every animal you own|you have a sheep called mr fluffy",
  "they deserve names|he likes his name|they're family",
  "you ate the last one you named|your family moos|he doesn't know his name")
R("dog_carries", "pets", "its dog", "",
  "your dog has more kills than you|your wolf carries you in fights|your dog is the real fighter",
  "teamwork|we're a team|he learned from me",
  "he learned to fight without you|the team is the dog|you're the dog's sidekick")
R("cat_boss", "pets", "its cat", "",
  "your cat sits on your chest all day and you let it|you can't open your chest cause the cat's sitting on it|your cat owns you",
  "she's comfy|i don't want to disturb her|cats rule",
  "she rules your storage|you went hungry for a cat|you're the cat's servant")
R("melon_diet", "food", "its melon diet", "",
  "you have a melon farm and eat melons only|melons all day, you're half melon|your melon farm is a mess of stems",
  "melons are sweet|they're fast to grow|stems are pretty",
  "sweet and useless|fast to grow, slow to fill you|stems are not pretty")
R("bread_diet", "food", "its bread diet", "",
  "you eat bread like it's a feast|bread again? you live like a villager|your whole diet is wheat",
  "bread is life|it's reliable|villagers know good food",
  "villagers also run from zombies|reliable like rain|you're one hay bale from becoming one")
R("cane_stubs", "farming", "its sugar cane", "",
  "your sugar cane farm is all stubs|you harvest the cane from the bottom, every time|you break the whole stalk, why",
  "i want it all|it grows back|i'm in a hurry",
  "it grows back slower like that|all of nothing|in a hurry to wait")
R("chicken_chaos", "animals", "its chicken coop", "",
  "your chicken coop is chicken chaos|the chickens escaped again|your coop is just feathers",
  "they're free range|they'll come back|chickens are wild at heart",
  "free range into the river|they're in another biome|wild like your farm")
R("cows_inside", "animals", "the cows in its house", "",
  "you keep cows in your house|there's a cow in your bedroom|you sleep next to cows",
  "they keep me warm|it's safer|they wanted in",
  "warm and moo|safer for the cows maybe|they want out")
R("horse_hates", "animals", "its horse", "",
  "you can't ride your horse in a straight line|your horse throws you off every time|you named your horse and it still hates you",
  "he's spirited|he's learning|i'm learning",
  "spirited away from you|he's learning to run away|neither of you is learning")
R("potato_chests", "food", "its potatoes", "",
  "you've got three chests of potatoes|why do you need that many potatoes|potato person",
  "potatoes are versatile|i'm prepared|baked potatoes are elite",
  "you only bake them|prepared for a potato emergency?|elite at being potatoes")
R("raw_cod", "food", "eating raw fish", "",
  "you eat fish raw like a polar bear|you just eat raw cod|fish every day, you're half drowned",
  "fishing is cheap|cod is fine|polar bears are cool",
  "the bear would cook it|cheap and raw|you smell like a river")
R("never_food", "food", "never carrying food", "",
  "you never have food on you|you always beg for food|you're always hungry",
  "i forget|food finds me|i'm lean",
  "it finds you through me|lean like a stick|you forget to eat and then cry")
R("one_pie", "food", "its pumpkin pie", "",
  "you made one pumpkin pie and won't stop bragging|you think one pie makes you a chef|pie again?",
  "it's a good pie|it took effort|you've never baked",
  "it's sugar and egg, relax|effort of three clicks|i've baked, you burn things")
R("golden_carrot_snob", "food", "its golden carrots", "",
  "you wasted gold on carrots|golden carrots? fancy|you eat gold now",
  "best food in the game|the saturation is real|it's an investment",
  "an investment in your stomach|real and expensive|you're broke now")
R("rainbow_sheep", "animals", "its rainbow sheep", "",
  "your sheep are every colour, it hurts|you dyed your sheep rainbow|your sheep pen looks like a parrot party",
  "rainbow wool is useful|they're happy|it's pretty",
  "pretty loud|they look confused|useful for clown armor")
R("bee_war", "animals", "its bee farm", "",
  "you got stung setting up your bee farm|your beehive is a war zone|the bees hate you",
  "honey is worth it|they'll warm up to me|i'm the beekeeper",
  "the keeper got stung|warm up and sting|worth it until the swarm")
R("pig_rider", "animals", "riding a pig", "",
  "you ride a pig like it's a horse|you spent an hour on a pig|carrot on a stick, really",
  "pigs are underrated|it's fun|he's fast enough",
  "fast as a pebble|fun for a minute|the pig wants out")
R("llama_spit", "animals", "the llama", "",
  "a llama spat on you and you cried|llamas hate you|you got spit on by a trader's llama",
  "it was a surprise|i didn't cry|they're rude",
  "they're right though|you sniffled|rude like you")
R("turtle_eggs", "animals", "the turtle eggs", "",
  "you broke turtle eggs walking on the beach|turtles fear you|you stomped the turtle beach",
  "i didn't see them|they were in the sand|it was an accident",
  "white eggs on sand, invisible, sure|an accident three times|the turtles have a list")
R("fox_thief", "animals", "the fox that robbed it", "",
  "a fox stole your sword|you got robbed by a fox|the fox took your food and left",
  "it was quick|foxes are sneaky|i let it",
  "quick as a fox, you're not|sneaky and smart, unlike you|you let it after chasing it for ten minutes")
R("axolotl_bucket", "pets", "its axolotl", "",
  "you carry axolotls in buckets everywhere|you love your axolotl more than people|you named your bucket",
  "they're cute|he's my friend|they help in fights",
  "your friend lives in a bucket|cute doesn't fight|help in fights you're losing")
R("parrot", "pets", "its parrot", "",
  "your parrot copies the creeper hiss and you still flinch|your parrot is smarter than you|the parrot fell off your shoulder again",
  "she's practicing|she's loyal|we're bonding",
  "practicing scaring you|loyal to the seeds|you bonded by falling")

# Names.
R("name_pig", "jokes", "its name", "",
  "your name would be a great name for a pig|i'm naming my pig after you|i named a pig after you, he's smarter",
  "honored|that pig's lucky|at least the pig's famous",
  "the pig is offended|the pig's lucky it's not you|the pig won't answer to it")
R("name_forget", "jokes", "its name", "",
  "i forget your name every single time|i call you the one with the dirt hut|what's your name again? kidding. mostly",
  "rude|you know my name|i'll remember yours forever, sadly",
  "i'll just call you bozo|i know it, i just don't use it|sadly is right")
R("name_sneeze", "jokes", "its name", "",
  "your name sounds like a sneeze|your name sounds like a slime landing|say your name out loud, it's a cough",
  "bless you|it's a strong name|yours sounds like a bat",
  "a strong cough|bats are cooler than you|bless me? bless you, you need it")
R("name_hmm", "villagers", "its name", "",
  "villagers can't even say your name|the villagers just go hmm when you walk by|they say hmm because they forgot your name",
  "they say hmm to everyone|they love me|they say hmm at you too",
  "they say a sadder hmm for you|love is a strong word for that hmm|mine is a happy hmm")
R("name_grave", "death", "its name on a grave", "",
  "i'll put your name on a grave sign for you, save time|i've got a sign ready with your name for next time|your name looks good on a grave",
  "i'll make one for you|i'm not dying today|morbid",
  "i'll write it in gold|you died yesterday|i'm just prepared")
R("name_famous", "town", "its fame", "",
  "everyone around here knows your name. for the wrong reasons|your name came up at the well, not in a good way|people say your name and laugh",
  "famous is famous|they're jealous|any fame is good fame",
  "famous for falling in lava|jealous of what|the fame of the village fool")
R("name_new", "jokes", "a new name for it", "",
  "you should change your name to lava swimmer|new name for you: creeper bait|i'm calling you baby zombie snack now",
  "that's a cool name actually|please no|i'll call you dirt roof then",
  "it suits you|too late, it's spreading|dirt roof is a compliment next to yours")

# Choices.
R("diamond_bread", "trade", "trading a diamond for bread", "",
  "you traded a diamond for bread|you gave a villager diamonds for an apple|worst trade in history, that was you",
  "i was starving|the bread was good|it was a nice villager",
  "the nice villager got rich|starving for good deals|the bread was three wheat")
R("ravine_house", "home", "its house by the ravine", "",
  "you built your house on the edge of a ravine|one step out the door and you're in a ravine|your front yard is a cliff",
  "great view|it's defensive|i like the drama",
  "the view is your grave|defensive against you walking home|drama every morning")
R("bed_lava", "home", "its bed by the lava", "",
  "your bed is next to lava|you sleep next to a lava pool, why|that lava's two blocks from your pillow",
  "it's warm|it's a night light|i'm careful",
  "warm until it's not|a night light that eats beds|careful is what you're not")
R("desert_home", "biomes", "living in the desert", "",
  "you picked a desert to live in. on purpose|why would you move to a desert|nothing grows where you live",
  "cactus grows|i like the sun|it's peaceful",
  "peaceful until the husks come|the sun's trying to cook you|cactus is not food")
R("ocean_home", "ocean", "its ocean base", "",
  "you built a base in the middle of the ocean, how do you get home|your house is a raft basically|island living, so you can't escape",
  "boats|it's private|the drowned don't visit",
  "the drowned visit every night|private and wet|your boat sank twice")
R("sold_mending", "trade", "selling a mending book", "b:knows_enchanting",
  "you traded away a mending book|you sold the best book in the game|a mending book, for emeralds, why",
  "i needed emeralds|i didn't know|i have another",
  "you don't have another|now you know, pain|emeralds don't fix pickaxes")
R("left_village", "villagers", "leaving the village", "",
  "you moved out of a perfectly good village|you had a village and left it|the villagers miss you, i think",
  "too noisy|i like quiet|the villagers were weird",
  "you were weirder|quiet like a desert|noisy compared to your empty house")
R("gapple_waste", "food", "wasting a golden apple", "",
  "you ate a golden apple for half a heart|wasted a golden apple on a chicken fight|you eat golden apples like snacks",
  "safety first|i panicked|it was there",
  "half a heart, it was half|panicked over a chicken|it was there and then gone")
R("field_bed", "sleep", "sleeping outside", "",
  "you sleep outside with no walls|you put a bed in a field|your bedroom is the sky",
  "it's freedom|the stars are pretty|i'm brave",
  "the zombies love your freedom|stars and skeletons|brave and asleep")
R("tnt_mining", "mining", "mining with tnt", "",
  "you mine with tnt and lose all the ore|tnt mining, so you can find nothing faster|you blew up your diamonds",
  "it's fast|it's fun|they drop sometimes",
  "fast at losing diamonds|fun for the craters|sometimes is never")
R("named_stone_sword", "weapons", "its named stone sword", "",
  "you named your stone sword|you used an anvil to name a stone sword|your stone sword has a name, why",
  "she deserves one|it's tradition|she's been through a lot",
  "she's been through two zombies|a tradition of wasting levels|she'll break by morning")
R("always_moving", "home", "always moving house", "",
  "you've moved house five times this week|you never stay anywhere|how many houses have you abandoned",
  "i'm a wanderer|i get bored|the land called me",
  "the land wants you to leave too|bored of finishing|a wanderer of abandoned huts")
R("five_buckets", "tools", "its buckets", "",
  "you made five buckets and no armor|who needs five buckets|you have more buckets than sense",
  "water, lava, milk|options|i'm a bucket person",
  "and two spares for nothing|options of a fool|a bucket person, that's new")
R("diamond_shovel", "tools", "its diamond shovel", "",
  "first diamonds and you made a shovel|you made a diamond shovel before a pickaxe|a shovel, with diamonds",
  "dirt is serious business|there's sand everywhere here|i dig a lot",
  "dirt isn't serious|you dig your own holes, sure|the business of digging graves")
R("trusted_bandit", "players", "trusting a bandit", "",
  "you trusted a bandit and lost your stuff|someone said hold my stuff and you gave it all|you fell for the drop your stuff trick",
  "he seemed nice|it was a test|i got it back",
  "he seemed nice taking it|you failed the test|you got a stick back")
R("river_farm", "farming", "its river farm", "",
  "you built your farm on a river and it washed away|your crops floated off|farming on water, bold",
  "it was a test|it looked flat|water's good for crops",
  "test failed, wheat's gone|flat and wet|good for fish")
R("never_listens", "advice", "not listening", "",
  "i told you not to dig straight down and you did|you never listen and then you die|i said don't and you did anyway",
  "i had to see|you're not my boss|it looked fine",
  "you saw lava|not your boss, just your mourner|it never looks fine")
R("cow_boat", "animals", "the cow in the boat", "",
  "you spent an hour getting a cow into a boat|you tried to row a cow across the ocean|the cow got out halfway",
  "she needed a new home|it's a skill|she'll be fine",
  "she's fine, alone on an island|the skill of losing a cow|she needed you to stop")
R("sprint_starve", "food", "sprinting everywhere", "",
  "you sprint jump everywhere and starve|your hunger bar is always empty from sprinting|you sprint to the furnace",
  "speed is life|i'm efficient|walking is boring",
  "starving is efficient?|boring and fed|life is short, you made it shorter")
R("field_chest", "storage", "its chest in a field", "",
  "you left a chest in the middle of a field|your stuff is just in a random chest outside|anyone can open your field chest",
  "nobody goes there|it's hidden|i trust people",
  "i went there|hidden in a plain field|trust is how you lose things")

# Storage and inventory.
R("messy_inventory", "storage", "its messy chests", "",
  "your inventory is a disaster|i opened your chest and it was soup|nothing in your chests is sorted",
  "i know where things are|it's organized chaos|sorting is boring",
  "you took ten minutes to find a torch|chaos yes, organized no|boring and lost")
R("dirt_stacks", "storage", "its stacks of dirt", "",
  "you carry four stacks of dirt everywhere|why is half your inventory dirt|dirt collector",
  "dirt is useful|emergency pillar|you never know",
  "you know, it's dirt|an emergency of hoarding|useful for huts like yours")
R("chest_maze", "storage", "its chest maze", "",
  "you have a hundred chests and no labels|your storage room is a maze of chests|you open every chest to find one thing",
  "i remember|labels are for amateurs|it's a system",
  "a system of guessing|amateurs who find things|you remember wrong every time")
R("flesh_hoard", "storage", "its rotten flesh", "",
  "you keep every rotten flesh you ever found|three chests of rotten flesh, why|what are you saving rotten flesh for",
  "trading|emergencies|i might need it",
  "an emergency zombie costume?|nobody trades for that much|you've never needed it")
R("floor_storage", "storage", "having no chests", "",
  "you have no storage at all|where do you keep your stuff, the floor?|your stuff is just lying around",
  "i carry it|the floor is a chest|i'm minimalist",
  "items vanish off the floor|the floor ate your diamonds|minimalist by accident")
R("ender_chest_seeds", "storage", "its ender chest", "",
  "you keep junk in your ender chest|your ender chest is full of seeds|you use your ender chest for dirt",
  "it's safe|seeds are precious|why not",
  "safe dirt, wow|precious like gravel|why not use it right")
R("shulker_cobble", "storage", "its shulker boxes", "b:knows_end",
  "your shulker boxes are all one colour and unsorted|you have a shulker of cobble|shulkers full of junk",
  "cobble is important|it's portable|i'll sort it",
  "important to you only|portable junk|you'll sort it never")
R("hotbar_layout", "gear", "its hotbar layout", "",
  "your hotbar layout makes no sense|why is your sword in the last slot|you scroll forever to find your food",
  "it works for me|muscle memory|the last slot is lucky",
  "you died scrolling|memory of losing|lucky for the zombies")
R("barrels", "storage", "its barrels", "",
  "you use barrels for everything|your house is all barrels|barrels as walls, as tables, as beds",
  "barrels are cool|they stack|they're versatile",
  "you slept on a barrel|versatile, no|they stack like your problems")
R("wrong_frames", "storage", "its item frames", "",
  "you put item frames on every chest and still lose things|you labeled everything and it's still chaos|your item frames are all wrong",
  "they're right|some are old|i changed things",
  "the bread frame has coal in it|old as your house|change the frames too")
R("hopper_sorter", "redstone", "its item sorter", "b:knows_redstone",
  "your hopper chain goes nowhere|you built a sorting system that sorts nothing|your item sorter just eats items",
  "it's a prototype|it sorts some|it needs tuning",
  "the prototype ate my sword|some is a strong word|tune it into the lava")
R("cobble_haul", "mining", "its cobble haul", "",
  "you came back from the cave with a full inventory of cobble|you threw away iron to keep cobble|your mining trip was all gravel",
  "cobble is useful|i panicked|gravel makes flint",
  "useful for nothing|panicked into a bad choice|flint for your fires")
R("rain_chests", "storage", "its outdoor chests", "",
  "your chests are outside in the rain|you keep your storage in a field|your storage has no roof",
  "chests don't get wet|it's open plan|easy access",
  "easy access for bandits|open plan for thieves|they don't, but you do")
R("item_trail", "storage", "the items it drops", "",
  "you drop stuff everywhere like a trail|i followed your trail of seeds home|you leave items like breadcrumbs",
  "i'm marking my path|it's on purpose|i'll pick it up",
  "the chickens are following it|on purpose to get lost|you'll pick it up never")
R("stashes", "storage", "its hidden stashes", "",
  "you have stashes everywhere and forget them all|you bury chests like a fox hides stuff|you hide chests and lose them",
  "i'll find them|it's insurance|it's a treasure hunt",
  "a treasure hunt for you, every day|insurance for the next person|you'll find one by accident")

# Habits.
R("wall_staring", "jokes", "staring at walls", "",
  "you stand still and stare at walls for ages|i saw you staring at a wall for five minutes|what were you doing, staring at the dirt",
  "thinking|planning|resting my eyes",
  "thinking about walls?|planning to stare more|your eyes were open")
R("no_torches", "home", "its dark house", "",
  "you never place torches|your base is pitch black|i trip over mobs in your house",
  "i know my way|light attracts attention|i like the dark",
  "the zombies know your way too|attention from zombies in your kitchen|the dark likes you, it keeps spawning things")
R("early_bed", "sleep", "going to bed early", "",
  "you go to bed at sunset like a villager|you're in bed before the sun's down|grandpa hours",
  "early to bed|i like mornings|it's safer",
  "safe and boring|mornings are long for you|the villagers go to bed after you")
R("chatterbox", "jokes", "how much it talks", "",
  "you never stop talking|you narrate everything you do|you talk to your chickens",
  "they listen|i'm sociable|someone has to",
  "they walk away|sociable with chickens|nobody has to")
R("always_late", "friends", "being late", "",
  "you're always late|we said morning and you came at noon|you show up when it's over",
  "i got lost|i was busy|i'm here now",
  "lost again|busy being late|the fight was yesterday")
R("dramatic", "health", "making a fuss", "",
  "you make a big deal out of half a heart|one hit and you scream|you act like every scratch is the end",
  "it hurts|i'm dramatic|hearts are precious",
  "half a heart, relax|dramatic is an understatement|precious like your ego")
R("one_diamond", "brag", "its one diamond", "",
  "you won't shut up about your one diamond|we know, you found diamonds once|you show everyone your diamond",
  "it was hard|it's a big deal|you're jealous",
  "it was one|a big deal for a small find|jealous of one diamond?")
R("keeps_everything", "storage", "keeping everything", "",
  "you keep everything you ever found|you never throw anything away|you're a dragon on a pile of junk",
  "everything's useful|i might need it|it's my collection",
  "useful for nothing|you might need a bigger house|a collection of trash")
R("wanders_off", "exploring", "wandering off", "",
  "you wander off every time we go somewhere|i turn around and you're gone|you get distracted by every flower",
  "flowers are nice|i was exploring|you walk too fast",
  "you picked fourteen flowers|exploring the wrong way|i walk at a normal speed")
R("complainer", "complaints", "its complaining", "",
  "you complain about everything|you complained about the sun yesterday|the rain, the sun, the night, all bad to you",
  "they're all bad|i'm honest|it's valid",
  "the sun is fine|honest and annoying|valid like a dirt hut")
R("overpacked", "gear", "how much it packs", "",
  "you bring three stacks of torches to the surface|you pack for a trip like you're moving|you bring food for a week to go next door",
  "better safe|you never know|i've been burned before",
  "safe and slow|you know|burned by your own lava")
R("charges_in", "fighting", "charging in", "",
  "you jump into every fight without thinking|you charge in and i have to save you|you fight first, think never",
  "i'm brave|it works|thinking is slow",
  "brave until i pull you out|it works for the mobs|thinking would save you")
R("picky_eater", "food", "being picky about food", "",
  "you won't eat anything but steak|you turned down bread from me|picky eater",
  "steak is the best|bread is sad|i have standards",
  "standards and an empty stomach|bread is fine|steak is not the only food")
R("bad_timing", "building", "building at the worst time", "",
  "you stand in the rain building and complain|you build at night and wonder why mobs come|you always pick the worst time to build",
  "inspiration strikes|i lose track of time|the mood was right",
  "lightning strikes too|time lost you|the mood was zombies")
R("villager_watcher", "villagers", "watching villagers", "",
  "you follow villagers around all day|you watch villagers like it's a show|you know every villager's schedule",
  "they're interesting|i learn things|they're my friends",
  "your friends say hmm|you learned to say hmm|interesting like gravel")
R("indecisive", "building", "taking forever to choose", "",
  "you take forever to decide anything|you stood at the crafting table for a whole day|pick a block already",
  "choices matter|i'm thinking|it's important",
  "it's oak or spruce, pick|thinking is not building|important like a stick")
R("door_clicker", "home", "clicking its door", "",
  "you open and close your door ten times|why do you keep clicking the door|your door goes click click click all night",
  "it's fun|i'm testing it|nervous habit",
  "fun for you|tested enough|the zombies hear it too")
R("flower_picker", "plants", "picking every flower", "",
  "you have a chest full of flowers|why do you pick every flower|you picked a whole meadow",
  "they're pretty|dye|they make me happy",
  "the bees are mad|dye for what|happy, and the bees have nowhere to go")
R("bad_jokes", "jokes", "its jokes", "",
  "your jokes are worse than your builds|you tell the same joke every day|nobody laughs at your creeper joke",
  "you laughed|they're classics|my jokes are great",
  "i laughed at you|classics like dirt|great at clearing a room")
R("always_crouching", "jokes", "crouching everywhere", "",
  "you sneak everywhere like you're guilty|why are you always crouching|you crouch walk to the furnace",
  "i don't want to fall|it's safe|habit",
  "the furnace isn't an edge|safe and slow|the habit of a scared rabbit")
R("never_shares", "food", "never sharing", "",
  "you never share your food|you hide your apples from me|i've never seen you give anyone anything",
  "it's mine|you'd eat it all|i share sometimes",
  "it's an apple|i'd eat one|sometimes is never")
R("rushes", "building", "rushing everything", "",
  "you rush everything and it shows|your builds look rushed because they are|you rush and then fix it twice",
  "speed is a skill|i fix fast too|done is better than perfect",
  "done badly is still done badly|fixing twice isn't fast|the skill of messing up quickly")
R("night_owl", "night", "staying up all night", "",
  "you're only awake at night like a phantom|you sleep all day and mine all night|you're basically a bat",
  "night is peaceful|less crowded|bats are cool",
  "peaceful with all the zombies?|crowded with mobs|bats are not cool")
R("fishing_job", "fishing", "fishing all day", "",
  "you spend your days fishing and call it work|fishing all day isn't a job|you fish more than you mine",
  "it's relaxing|it's a living|fish are loot",
  "relaxing and broke|a living of boots|loot like seaweed")
