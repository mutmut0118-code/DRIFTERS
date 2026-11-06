"""Writes topics_home_life.json (side session batch, 0.23.10-side.3): home life, decorating, flat roofs, deserts, towns."""
import json
import sys

out = sys.argv[1]
topics = []


def T(id, about, tags, leads, turns, needs=None, weight=None):
    t = {"id": id}
    if weight:
        t["weight"] = weight
    if needs:
        t["needs"] = needs
    t["tags"] = tags
    t["leads"] = leads
    t["about"] = about
    t["turns"] = turns
    topics.append(t)


def turn(by, lines, chance=None, mark=None, cond=None):
    d = {"by": by, "lines": lines}
    if chance is not None:
        d["chance"] = chance
    if cond:
        d["if"] = cond
    if mark:
        d["mark"] = mark
    return d


def debate(id, about, tags, leads, claims, agrees, disagrees, a_yes, a_no, needs=None):
    T(id, about, tags + ["argument"], leads, [
        turn("a", claims),
        turn("b", agrees, chance=0.5, mark=["agree"]),
        turn("b", disagrees, cond=["!t:agree"], mark=["disagree"]),
        turn("a", a_yes, chance=0.6, cond=["t:agree"]),
        turn("a", a_no, cond=["t:disagree"]),
    ], needs)


# --- Decorating (the rug and the painting).
T("laid_a_rug", "the rug it laid", ["decor", "home", "brag"], ["decor", "home", "building"], [
    turn("a", ["finally put a rug down in my house", "laid a carpet in the main room", "my house has a rug now",
               "made a little rug for the floor", "added carpet, house feels way cozier"]),
    turn("b", ["nice", "fancy", "cozy", "rugs make a house", "what colour?", "ooh"]),
    turn("a", ["white, it's all i had", "plain white lol", "whatever wool i had", "it's a start"], chance=0.5),
], needs=["a:has_house"], weight=2)
T("hung_a_painting", "a painting it hung", ["decor", "home"], ["decor", "home", "random"], [
    turn("a", ["hung a painting in my house", "got a painting on the wall now", "put up a painting, it's a weird one",
               "my painting is just some guy and a sunset i think", "random painting turned out pretty nice"]),
    turn("b", ["which one?", "nice", "paintings are a gamble", "hope it's not the skull one", "art"]),
    turn("a", ["no idea what it is", "the one with the pond", "it's fine", "lol", "it's growing on me"], chance=0.5),
], needs=["a:has_house"])
T("rug_colours", "rug colours", ["decor", "opinion"], ["decor", "animals", "home"], [
    turn("a", ["need more coloured wool", "wish i had red wool for a rug", "white carpet gets boring", "want a blue rug",
               "might dye some wool for a rug"]),
    turn("b", ["shear a coloured sheep", "dye is easy", "find a red flower", "white is classic", "lapis makes blue"]),
    turn("a", ["good idea", "true", "maybe later", "ok"], chance=0.5),
])
T("decor_vs_useful", "decorating or not", ["decor", "philosophy"], ["decor", "home", "storage"], [
    turn("a", ["decorating is a waste of wool", "why decorate when chests are useful", "rugs dont do anything",
               "i only build what i need"]),
    turn("b", ["it's your home though", "makes it feel lived in", "you live there", "fair", "a bit of cozy never hurt"]),
    turn("a", ["hmm", "maybe", "still", "eh"], chance=0.5),
])
debate("debate_paintings", "paintings", ["decor"], ["decor", "home", "jokes"],
       ["paintings are the best decoration", "every house needs a painting", "paintings make a wall"],
       ["true", "agreed", "yeah", "love them"], ["they're so random", "nah", "item frames are better", "too ugly"],
       ["see", "exactly", "lol"], ["fair", "each their own", "ok"])

# --- Flat roofs and the shape of houses.
debate("debate_flat_roofs", "flat roofs", ["building"], ["building", "home", "argument"],
       ["flat roofs look clean", "flat roofs are underrated", "square houses are nice actually", "i like a flat roof"],
       ["same", "true", "clean look", "agree"], ["pitched roofs look better", "nah, a roof needs a slope", "boring", "looks unfinished"],
       ["see", "nice", "flat gang"], ["fair", "each their own", "ok ok"])
T("roof_overhang", "an overhang", ["building", "opinion"], ["building", "home"], [
    turn("a", ["my roof sticks out two blocks", "gave my roof an overhang", "overhangs make the house look bigger",
               "the roof over my door keeps the rain off lol"]),
    turn("b", ["nice", "shade", "looks good", "classic", "roofs are hard"]),
    turn("a", ["took forever", "yeah", "lol", "worth it"], chance=0.5),
], needs=["a:has_house"])
T("full_block_roof", "a thick roof", ["building", "jokes"], ["building", "home"], [
    turn("a", ["my roof is solid planks, no slabs", "used full blocks for my roof", "thick roof, very sturdy"]),
    turn("b", ["mobs can spawn on that", "solid", "nice", "light it up", "at least it's solid"]),
    turn("a", ["oh no", "worth it", "torches it is", "lol"], chance=0.6),
], needs=["a:has_house"])
T("everyone_same_house", "houses looking alike", ["building", "town", "complaints"], ["building", "town", "home"], [
    turn("a", ["every house in this town looks the same", "we all built the same house lol", "need some variety around here"]),
    turn("b", ["build a weird one then", "lol true", "mine's different", "flat roof, be different", "honestly"]),
    turn("a", ["maybe i will", "lol", "fair", "next house"], chance=0.6),
], needs=["a:in_town"])
T("next_house_plans", "the next house", ["building", "plans", "dreams"], ["building", "plans", "home"], [
    turn("a", ["next house i'm doing two floors", "i want a bigger house", "my next build is gonna be stone",
               "planning a house with a wing", "want a house with a balcony"]),
    turn("b", ["go for it", "nice", "you'll need a lot of stone", "dream big", "send pics lol"]),
    turn("a", ["one day", "haha", "yeah", "slowly"], chance=0.5),
], needs=["a:has_house"])

# --- Deserts and sandstone.
T("desert_living", "living in the desert", ["biomes", "home"], ["biomes", "building", "complaints"], [
    turn("a", ["the desert is rough to live in", "no trees out here", "sand everywhere", "wood is so hard to find in the desert",
               "desert life is brutal"]),
    turn("b", ["build with sandstone", "move lol", "at least it's flat", "cactus farm", "no creepers hiding in trees"]),
    turn("a", ["already am", "never", "true", "ha"], chance=0.5),
])
T("sandstone_house", "a sandstone house", ["building", "biomes", "brag"], ["building", "biomes", "home"], [
    turn("a", ["my house is all sandstone", "built in sandstone, fits the desert", "sandstone house looks so good",
               "used sand for my whole house"]),
    turn("b", ["smart", "matches", "nice", "sand is free", "classy"]),
    turn("a", ["took a lot of sand", "yeah", "lol", "four sand a block"], chance=0.5),
], needs=["a:has_house"])
T("sand_falls", "falling sand", ["mining", "danger", "complaints"], ["mining", "danger", "biomes"], [
    turn("a", ["sand fell on my head again", "nearly suffocated digging under sand", "dug under some sand, bad idea",
               "never mine stone with sand on top"]),
    turn("b", ["classic", "oof", "dig from the side", "gravel's worse", "lol rookie mistake"]),
    turn("a", ["learned that", "lol", "never again", "yeah"], chance=0.5),
])
T("lost_furnace", "a furnace it lost", ["crafting", "complaints", "jokes"], ["crafting", "mining", "complaints"], [
    turn("a", ["left my furnace down a hole", "can't get back to my furnace", "my stone is still cooking somewhere down there",
               "forgot where i put my furnace"]),
    turn("b", ["just make another", "lol", "furnaces are cheap", "that's rough", "dig back down"]),
    turn("a", ["fine", "yeah", "lesson learned", "8 cobble later..."], chance=0.5),
])

# --- Tidying up after itself.
T("tidy_pillars", "taking pillars down", ["building", "home"], ["building", "home", "complaints"], [
    turn("a", ["spent all morning taking down my old pillars", "cleaned up my dirt towers", "took down the pillars by the trees",
               "finally removed my old bridges"]),
    turn("b", ["nice", "respect", "i never do that", "neat freak", "the town thanks you"]),
    turn("a", ["someone has to", "lol", "it was bugging me", "yeah"], chance=0.5),
])
T("messy_neighbour", "pillars everywhere", ["town", "complaints", "gossip"], ["town", "gossip", "building"], [
    turn("a", ["someone keeps leaving cobble pillars around", "who left that pillar by the tree", "there's cobble on the road again",
               "random blocks everywhere, come on"]),
    turn("b", ["wasn't me", "lol guilty", "probably a tree pillar", "annoying", "tell them"]),
    turn("a", ["sure", "hmm", "lol", "whoever it is"], chance=0.6),
], needs=["a:in_town"])
T("tree_pillar", "cutting a tall tree", ["trees", "building"], ["trees", "tools", "building"], [
    turn("a", ["had to pillar up to cut a tall spruce", "the top logs are always out of reach", "tall trees are a pain",
               "had to build up to get the last logs"]),
    turn("b", ["leave the top", "big trees are worth it", "hope you took the pillar down", "just use a bigger tree farm"]),
    turn("a", ["i did", "most of it", "lol", "yeah"], chance=0.5),
])

# --- Town and neighbours.
T("neighbour_chests", "keeping to its own chests", ["town", "storage", "trust"], ["town", "storage", "friends"], [
    turn("a", ["i don't touch other people's chests", "never open a neighbour's chest", "my chests are mine, yours are yours"]),
    turn("b", ["good rule", "same", "unless they say so", "trust", "respect"]),
    turn("a", ["exactly", "yeah", "simple", "right"], chance=0.5),
], needs=["a:in_town"])
T("neighbour_house", "a neighbour's house", ["town", "building", "gossip"], ["town", "building", "decor"], [
    turn("a", ["the house next to mine is really nice", "my neighbour's house is huge", "saw the inside of the house down the road",
               "someone in town has a rug and a painting, fancy"]),
    turn("b", ["jealous?", "lol", "go visit", "nice", "who?"]),
    turn("a", ["a little", "maybe", "lol", "someone"], chance=0.5),
], needs=["a:in_town"])
T("town_road_steps", "the town's roads", ["town", "complaints"], ["town", "building", "complaints"], [
    turn("a", ["these roads go up and down a lot", "the steps on the road are steep", "our roads need work",
               "the road up the hill is a climb"]),
    turn("b", ["better than no roads", "take the stairs", "good exercise", "true", "we should fix them"]),
    turn("a", ["i guess", "lol", "someday", "yeah"], chance=0.5),
], needs=["a:in_town"])
T("town_quiet_day_talk", "a quiet day in town", ["town", "day", "feelings"], ["town", "feelings", "random"], [
    turn("a", ["quiet day in town", "nothing happening today", "peaceful around here today", "everyone's busy today"]),
    turn("b", ["nice for once", "enjoy it", "i like quiet", "too quiet", "calm before the storm"]),
    turn("a", ["yeah", "lol", "for real", "mhm"], chance=0.5),
], needs=["a:in_town", "day"])
T("town_lamps", "the lamp posts", ["town", "night"], ["town", "night", "building"], [
    turn("a", ["the lamp posts make the town look nice at night", "glad the roads are lit", "town looks good with the lamps on"]),
    turn("b", ["keeps the mobs out", "agreed", "nice", "very cozy", "true"]),
    turn("a", ["yeah", "mhm", "exactly"], chance=0.5),
], needs=["a:in_town"])

# --- The house itself.
T("chest_labels_talk", "labelled chests", ["storage", "home", "brag"], ["storage", "home", "decor"], [
    turn("a", ["put signs on all my chests", "labelled my chests finally", "my chests have signs now, so organised",
               "every chest of mine says what's in it"]),
    turn("b", ["organised", "nice", "i should do that", "show off", "item frames are better"]),
    turn("a", ["took a while", "lol", "you should", "never lose anything now"], chance=0.5),
], needs=["a:has_house"])
T("cozy_house", "a cozy house", ["home", "feelings"], ["home", "decor", "feelings"], [
    turn("a", ["my house finally feels like home", "love coming home at night", "my bed, my chests, my house, perfect",
               "nothing beats your own house"]),
    turn("b", ["home sweet home", "agreed", "that's the dream", "cozy", "nice"]),
    turn("a", ["mhm", "yeah", "took a while", "lol"], chance=0.5),
], needs=["a:has_house"])
T("house_too_small", "a small house", ["home", "complaints"], ["home", "building", "storage"], [
    turn("a", ["my house is too small already", "running out of room for chests", "need an extension", "no room left in my house"]),
    turn("b", ["build a wing", "dig a basement", "more chests", "build up", "that's a good problem"]),
    turn("a", ["maybe", "good idea", "lol", "true"], chance=0.5),
], needs=["a:has_house"])
T("hut_to_house_talk", "moving out of the hut", ["home", "building", "brag"], ["home", "building", "plans"], [
    turn("a", ["moved out of my little hut", "finally done with the hut", "upgraded from a hut to a real house"]),
    turn("b", ["congrats", "big step", "nice", "the hut served you well", "about time"]),
    turn("a", ["thanks", "lol", "yeah", "rip hut"], chance=0.5),
], needs=["a:has_house"])
T("no_house_yet", "still no house", ["home", "complaints"], ["home", "building", "plans"], [
    turn("a", ["still don't have a house", "i'm basically homeless lol", "need to build somewhere", "sleeping in a hole again tonight"]),
    turn("b", ["build one", "you'll get there", "come stay in town", "a hole is a house", "rough"]),
    turn("a", ["one day", "lol", "maybe", "yeah"], chance=0.5),
], needs=["a:no_home"])
T("stairs_inside", "stairs in the house", ["building", "home"], ["building", "home"], [
    turn("a", ["my stairs go right through the middle of my house", "stairs take up so much room", "upstairs is my favourite room"]),
    turn("b", ["ladders save space", "stairs are nicer", "classic", "true", "two floors is nice though"]),
    turn("a", ["yeah", "maybe", "lol", "true"], chance=0.5),
], needs=["a:has_house"])

# --- Everyday.
T("long_day", "a long day", ["feelings", "day"], ["feelings", "random"], [
    turn("a", ["long day", "today was a lot", "so much walking today", "my legs hurt lol"]),
    turn("b", ["same", "rest up", "you earned it", "same here", "tomorrow's another day"]),
    turn("a", ["yeah", "mhm", "lol", "true"], chance=0.4),
], needs=["evening"])
T("good_morning_plans", "the day's plans", ["plans", "day"], ["plans", "mining", "building"], [
    turn("a", ["what's the plan today?", "busy day ahead?", "got anything planned today?"]),
    turn("b", ["mining", "building", "farm stuff", "nothing", "no idea", "whatever comes up"]),
    turn("a", ["nice", "same", "fair", "sounds good"], chance=0.6),
], needs=["morning"])
T("lost_track_of_time", "losing track of time", ["random", "jokes"], ["random", "night"], [
    turn("a", ["lost track of time again", "didn't notice it got dark", "how is it night already"]),
    turn("b", ["happens", "lol", "time flies", "classic", "every time"]),
    turn("a", ["yeah", "lol", "rip"], chance=0.4),
], needs=["evening"])
T("forgot_tool", "a forgotten tool", ["tools", "jokes"], ["tools", "complaints"], [
    turn("a", ["walked all the way out and forgot my pickaxe", "left my axe at home", "forgot my sword, great"]),
    turn("b", ["lol", "classic", "happens to everyone", "make another", "rookie"]),
    turn("a", ["ugh", "lol", "yeah", "never again"], chance=0.5),
])
T("full_inventory", "a full inventory", ["storage", "complaints"], ["storage", "home"], [
    turn("a", ["inventory full again", "no room for anything", "why do i carry so much dirt", "full of junk again"]),
    turn("b", ["throw out the dirt", "go home and sort", "need more chests", "same every time", "toss the seeds"]),
    turn("a", ["yeah", "never", "lol", "on it"], chance=0.5),
])
T("seen_a_sheep", "sheep for wool", ["animals", "decor"], ["animals", "decor", "farming"], [
    turn("a", ["need wool, seen any sheep?", "looking for sheep for wool", "anyone got a sheep farm?"]),
    turn("b", ["there's some by the river", "no", "check the hills", "i have a few", "nope"]),
    turn("a", ["thanks", "ok", "i'll look", "hm"], chance=0.6),
])
T("compliment_house", "complimenting a house", ["friends", "home", "building"], ["friends", "building", "decor"], [
    turn("a", ["your house looks great", "love what you did with your house", "nice place you've got"]),
    turn("b", ["thanks", "aw thanks", "took me ages", "appreciate it", "yours is nice too"]),
    turn("a", ["for real", "mhm", "lol", "no problem"], chance=0.4),
], needs=["friends", "b:has_house"])
T("housewarming", "a housewarming", ["friends", "home", "jokes"], ["friends", "home", "decor"], [
    turn("a", ["when's the housewarming?", "do i get a tour of the house?", "i'm coming over to see the new place"]),
    turn("b", ["anytime", "bring wool", "lol sure", "wipe your feet", "it's not done yet"]),
    turn("a", ["lol", "deal", "ok", "can't wait"], chance=0.6),
], needs=["friends", "b:has_house"])
T("cobble_everywhere", "too much cobblestone", ["building", "opinion"], ["building", "mining"], [
    turn("a", ["i have way too much cobblestone", "cobble everywhere in my chests", "drowning in cobblestone"]),
    turn("b", ["build something", "stone bricks", "smelt it", "make a wall", "same"]),
    turn("a", ["good idea", "maybe", "lol", "ok"], chance=0.5),
])
debate("debate_stone_or_wood", "stone or wood houses", ["building"], ["building", "danger", "argument"],
       ["stone houses are better, no fires", "wood houses burn too easily", "stone is the way to build"],
       ["true", "fair", "stone gang", "yeah"], ["wood looks better", "nah, wood is cozy", "just don't light fires", "stone is ugly"],
       ["see", "exactly", "lol"], ["fair", "each their own", "ok"])
debate("debate_carpet_or_slab", "carpet or slabs", ["decor"], ["decor", "building", "argument"],
       ["carpet beats slabs for floors", "carpet is the best floor", "rugs over bare floor any day"],
       ["agreed", "true", "yeah", "cozy"], ["slabs are cleaner", "nah", "bare wood is nice", "carpet burns"],
       ["see", "lol", "cozy wins"], ["fair", "ok", "each their own"])
debate("debate_two_floors", "one floor or two", ["building", "home"], ["building", "home", "argument"],
       ["two floors is the minimum", "every house needs an upstairs", "one floor houses are too small"],
       ["true", "agreed", "yeah", "upstairs is nice"], ["one floor is fine", "nah, stairs are annoying", "big one floor house is better"],
       ["exactly", "see", "lol"], ["fair", "ok", "suit yourself"])

with open(out, "w", encoding="utf-8") as f:
    json.dump({"_comment": "Topics batch (side session, 0.23.10-side.3): home life now that Drifters decorate (rugs, paintings), "
                           "flat roofs and overhangs, desert living and sandstone houses, tidying pillars, town neighbours, everyday "
                           "talk, debates (agree/disagree pattern). Check with: python tools/check_talk_web.py .",
               "topics": topics}, f, indent="\t", ensure_ascii=False)
print(len(topics), "topics")
