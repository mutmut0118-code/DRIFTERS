"""Generates Drifter: Civilizations' friendship talk topics (D2, 0.23.37): player topics a Drifter brings up with a player,
by how well it knows them (Friends.java's flags: b:acq acquaintance or closer, b:pal friend, b:bff best friend, b:shared a
shared opinion to bring up as {b.shared}).

  python tools/talk_gen/friends.py

writes src/civ/resources/data/drifter_civ/drifter_talk/friends_*.json. Hot takes end on a question (do "opinion"): the
player's yes is agreeing, no is disagreeing (FriendTalk keeps what they said, and warms or cools it by its nature).
"""
import json
import os
import random

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'civ', 'resources', 'data', 'drifter_civ', 'drifter_talk')
random.seed(37)

AGREE = ["right??", "exactly", "FINALLY someone gets it", "see, you get it", "thank you", "facts", "ok youre smart", "we think alike lol",
         "knew you'd get it", "yesss", "based", "glad im not crazy", "ok we're friends now lol", "W take", "this is why i like you"]
DISAGREE = ["nah youre wrong lol", "hmm ok agree to disagree", "lol ok", "wow. ok", "fair i guess", "youre tripping", "ok but youre wrong",
            "respectfully, no", "debatable", "we cant be friends (jk)", "i'll change your mind one day", "huh. ok", "L take but ok",
            "bold of you", "fine, everyones wrong sometimes"]
TAGS = ["right?", "agree?", "you feel me?", "or nah?", "tell me im wrong", "am i right?", "yeah?"]

# id, tags, about (as said later: "remember we both ..."), the take (a few ways), agree lines, disagree lines
TAKES = [
    ("diamonds_overrated", ["diamonds", "gear"], "think diamonds are overrated", ["honestly diamonds are overrated, iron does the job", "iron gear is all you really need ngl"], ["iron gang", "save the dias for a table"], ["ok mr full netherite"]),
    ("nether_best", ["nether"], "love the nether", ["the nether is the best dimension, no contest", "nether > overworld and its not close"], ["finally a nether enjoyer", "the vibes down there are unmatched"], ["you just hate lava lol"]),
    ("nether_worst", ["nether"], "hate the nether", ["i hate the nether so much, everything wants me dead", "nether is the worst place in the game"], ["ghasts are pure evil", "i go in, get quartz, leave"], ["you must like dying then"]),
    ("phantoms_worst", ["phantoms", "mobs"], "hate phantoms", ["phantoms are the worst mob ever added", "whoever made phantoms hates sleep deprived people"], ["they should delete them", "just let me build at night man"], ["you like getting swooped?"]),
    ("creepers_worst", ["creepers", "mobs"], "hate creepers", ["creepers have ruined more builds than anything", "creepers are the real enemy here"], ["rip every wall ive ever made", "sssss gives me trauma"], ["you havent lost a house yet then"]),
    ("endermen_chill", ["endermen", "mobs"], "think endermen are chill", ["endermen are lowkey the chillest mob", "endermen just wanna move some blocks around and vibe"], ["just dont look at them", "respect the block movers"], ["they stole my grass block once"]),
    ("spiders_ok", ["spiders", "mobs"], "think spiders aren't that bad", ["spiders arent even that bad tbh, free string", "i kinda like spiders, they leave you alone in the day"], ["string is string", "day spiders are friends"], ["they climb walls. thats evil"]),
    ("skeletons_worst", ["skeletons", "mobs"], "hate skeletons", ["skeletons are worse than creepers fr", "skeleton aim is actually unfair"], ["the knockback into a ravine...", "they never miss"], ["just use a shield lol"]),
    ("strip_mining", ["mining"], "like strip mining", ["strip mining beats caving, fight me", "caves are for people who like dying, strip mine"], ["safe and steady", "just you and the stone"], ["boring though"]),
    ("caving", ["caving", "mining"], "love caving", ["caving is way more fun than strip mining", "strip mining is so boring, caves are where its at"], ["the ores just sit there", "exploring is half the fun"], ["you like getting jumped by zombies?"]),
    ("branch_mining_y", ["mining", "diamonds"], "mine at the same depth", ["deepslate diamond level is the only way to mine", "everyone should just mine at the bottom"], ["thats where the good stuff is", "go deep or go home"], ["deepslate takes forever to mine tho"]),
    ("fortune_vs_silk", ["enchanting", "gear"], "pick fortune over silk touch", ["fortune is better than silk touch, easy", "silk touch is useless next to fortune"], ["more dias more happy", "fortune 3 is a life changer"], ["silk touch for glass tho"]),
    ("mending_best", ["enchanting", "gear"], "think mending is the best enchant", ["mending is the best enchant in the game", "if it doesnt have mending i dont want it"], ["never crafting a new pick again", "best thing ever"], ["unbreaking 3 is fine"]),
    ("swords_vs_axes", ["pvp", "gear"], "pick swords over axes", ["swords are better than axes, the swing is just nicer", "axes are mid, sword all day"], ["sweep attack goes hard", "sword gang"], ["axes break shields tho"]),
    ("axes_better", ["pvp", "gear"], "pick axes over swords", ["axes hit way harder than swords", "axe gang, swords are for show"], ["crits with an axe hit different", "and they chop trees"], ["slow though"]),
    ("bows_op", ["bows", "pvp"], "love bows", ["a good bow beats any sword", "bows are lowkey the best weapon"], ["power 5 says hi", "never let them get close"], ["miss every shot lol"]),
    ("shields_must", ["shields", "pvp"], "always carry a shield", ["never leave home without a shield", "anyone without a shield is asking to die"], ["shield saves lives", "skeletons cry"], ["it slows you down"]),
    ("wood_houses", ["building"], "like wood houses", ["wood houses look better than stone ones", "nothing beats a cozy wood house"], ["cozy is the word", "spruce supremacy"], ["one creeper and its gone"]),
    ("stone_houses", ["building"], "like stone houses", ["stone houses are the only real houses", "wood is for beginners, build in stone"], ["solid as anything", "cant burn it down either"], ["looks like a bunker though"]),
    ("dirt_huts", ["building"], "think dirt huts are fine", ["theres nothing wrong with a dirt hut", "dirt hut gang, its about the vibes"], ["it keeps the mobs out", "humble beginnings"], ["please build a real house lol"]),
    ("big_builds", ["building"], "love big builds", ["the bigger the build the better", "go big or dont build at all"], ["mega builds go crazy", "more blocks more fun"], ["takes forever tho"]),
    ("small_builds", ["building"], "like small builds", ["small builds look nicer than huge ones", "huge builds are overrated, small and cozy wins"], ["detail over size", "cozy wins"], ["thats just lazy"]),
    ("cobble_ugly", ["building"], "think cobblestone is ugly", ["cobblestone is the ugliest block", "cobble houses look so bad"], ["stone bricks or nothing", "smooth stone at least"], ["its classic though"]),
    ("spruce_best", ["building", "wood"], "think spruce is the best wood", ["spruce is the best wood, no debate", "spruce planks are just better"], ["dark and cozy", "spruce for life"], ["oak is the classic"]),
    ("oak_best", ["building", "wood"], "think oak is the best wood", ["oak is still the best wood", "nothing beats plain oak"], ["classic for a reason", "oak never fails"], ["oak is boring"]),
    ("bread_best_food", ["food"], "think bread is the best food", ["bread is the best food, its so easy", "wheat farm, bread, done. best food"], ["simple and good", "bread gang"], ["steak exists"]),
    ("steak_best_food", ["food"], "think steak is the best food", ["steak is the best food, change my mind", "cooked beef is unbeatable"], ["steak supremacy", "cows are worth it"], ["golden carrots tho"]),
    ("golden_carrots", ["food"], "love golden carrots", ["golden carrots are the best food in the game", "once you have golden carrots you never go back"], ["saturation is crazy", "so good"], ["too expensive"]),
    ("fish_food", ["food", "fishing"], "like eating fish", ["fish is underrated food tbh", "cooked salmon slaps"], ["salmon is great", "and fishing is relaxing"], ["fish? eww"]),
    ("fishing_fun", ["fishing"], "think fishing is fun", ["fishing is the most relaxing thing in the game", "i could fish for hours"], ["so peaceful", "and the treasure!"], ["sooo boring"]),
    ("farming_fun", ["farming"], "like farming", ["farming is lowkey fun", "theres something nice about a big wheat field"], ["growing stuff is great", "so satisfying"], ["boring chore"]),
    ("animals_cute", ["animals"], "love the animals", ["the animals are the best part of the game", "cows are so cute when they stare at you"], ["animals are life", "protect the cows"], ["theyre food"]),
    ("cats_vs_dogs", ["pets"], "pick cats over dogs", ["cats are better than dogs, they scare creepers", "dogs die too easy, cats are better"], ["cat gang", "creeper repellent"], ["dogs fight for you tho"]),
    ("dogs_vs_cats", ["pets"], "pick dogs over cats", ["dogs are better than cats, they fight for you", "a wolf pack is the best thing you can have"], ["loyal for life", "good boys"], ["they die so easy"]),
    ("horses_useless", ["horses"], "think horses are useless", ["horses are kinda useless honestly", "walking is fine, horses are a pain"], ["they never go where you want", "boats are better"], ["you just havent got a good one"]),
    ("horses_great", ["horses"], "love horses", ["a fast horse is the best thing to have", "horses are so underrated"], ["zoom zoom", "fast horse = freedom"], ["they get stuck everywhere"]),
    ("rain_nice", ["weather", "rain"], "like the rain", ["rain is the best weather, so cozy", "i love when it rains"], ["the sound is so nice", "perfect for building inside"], ["rain is annoying"]),
    ("night_best", ["night"], "love the night", ["night is the best time, more mobs more loot", "i like the nights better than the days"], ["mobs mean loot", "night owls unite"], ["you must like dying"]),
    ("sleep_every_night", ["night", "beds"], "sleep every night", ["you should sleep every single night", "skipping the night is the smart move"], ["no phantoms, no problems", "beds are life"], ["night is where the fun is"]),
    ("end_boring", ["end"], "think the end is boring", ["the end is kinda boring after the dragon", "end is just purple and empty"], ["elytra though", "nothing to do there"], ["end cities are fun"]),
    ("dragon_easy", ["end", "dragon"], "think the dragon is easy", ["the dragon is easier than people say", "the ender dragon is overhyped"], ["beds go boom", "bring arrows, done"], ["easy for you maybe"]),
    ("villages_missed", ["towns", "villages"], "miss the old villages", ["i kinda miss the old villages sometimes", "villagers were annoying but i miss them"], ["hrmm", "the noises lol"], ["towns are way better"]),
    ("towns_better", ["towns"], "think towns are better than villages", ["towns like ours beat those old villages any day", "real people beat villagers, easy"], ["villagers just went hrmm", "at least we talk"], ["villagers had good trades"]),
    ("city_life", ["towns", "city"], "like city life", ["living in a big city would be sick", "cities are where its at, so much going on"], ["always something to do", "city life"], ["too crowded"]),
    ("small_town", ["towns"], "like small towns", ["small towns are better than cities", "id rather live somewhere small and quiet"], ["everyone knows everyone", "quiet is nice"], ["boring though"]),
    ("guards_useless", ["guards", "towns"], "think the guards are useless", ["our guards are kinda useless lol", "the guards just stand around all day"], ["lmao true", "they sleep on the job"], ["theyve saved us before"]),
    ("trading_fun", ["trading"], "like trading", ["trading is more fun than mining", "id rather trade for stuff than mine it"], ["work smart not hard", "emeralds go brrr"], ["mining is the real way"]),
    ("emeralds_useless", ["emeralds", "trading"], "think emeralds are pointless", ["emeralds are kinda pointless honestly", "what do you even do with emeralds"], ["shiny green rocks", "pretty though"], ["you buy stuff with them lol"]),
    ("redstone_hard", ["redstone"], "find redstone hard", ["redstone is way too complicated", "i cant do redstone to save my life"], ["same", "one door is my limit"], ["its easy once you learn it"]),
    ("pvp_fun", ["pvp"], "like pvp", ["pvp is the best part of the game", "nothing beats a good 1v1"], ["sweaty but fun", "lets go 1v1"], ["pvp is just stress"]),
    ("pvp_bad", ["pvp"], "don't like pvp", ["pvp ruins everything, just build", "i dont get why people like pvp"], ["peace and love", "build dont fight"], ["scared to lose?"]),
    ("speedrun_fun", ["speedrun"], "think speedrunning is cool", ["speedrunning is actually so cool", "those speedrunners are crazy good"], ["insane skill", "i could never"], ["whats the rush"]),
    ("chill_play", ["playstyle"], "like playing slow", ["playing slow is the best way to play", "no rush, just vibe and build"], ["no rush ever", "chill gang"], ["you'll never beat the game like that"]),
    ("hoarding", ["storage"], "keep everything", ["you should keep everything, you never know", "never throw anything away"], ["you'll need it one day", "chests are free"], ["your chests must be a mess"]),
    ("organised_chests", ["storage"], "keep chests organised", ["messy chests drive me insane", "organised chests are a must"], ["labels on everything", "sorted is happy"], ["it all ends up in one chest anyway"]),
    ("torches_everywhere", ["torches", "light"], "put torches everywhere", ["you can never have too many torches", "torches everywhere is the only way"], ["no spawns allowed", "light it up"], ["it looks so ugly"]),
    ("lanterns_better", ["light", "building"], "think lanterns beat torches", ["lanterns look way better than torches", "torches are so ugly next to lanterns"], ["lanterns are pretty", "style points"], ["torches are cheaper"]),
    ("bees_cute", ["bees"], "think bees are cute", ["bees are the cutest mob", "i love bees so much"], ["save the bees", "honey too!"], ["they sting"]),
    ("ocean_scary", ["ocean"], "think the ocean is scary", ["the ocean is the scariest place in the game", "deep water freaks me out"], ["drowned everywhere", "cant see anything"], ["its so pretty though"]),
    ("deep_dark_scary", ["deep_dark", "caving"], "think the deep dark is terrifying", ["the deep dark is the scariest place ever", "never going near a warden again"], ["sneak everywhere", "pure fear"], ["the loot is worth it"]),
    ("mountains_best", ["mountains"], "love the mountains", ["mountains are the best biome, the views", "nothing beats building on a mountain"], ["views for days", "peak vibes"], ["climbing is such a pain"]),
    ("desert_worst", ["desert"], "hate the desert", ["the desert is the worst biome", "deserts are so boring"], ["nothing but sand", "and husks"], ["temples though"]),
    ("snow_cozy", ["snow"], "like the snow", ["snowy places are so cozy", "snow biomes are underrated"], ["cozy cabin vibes", "so pretty"], ["freezing though"]),
    ("cherry_pretty", ["cherry"], "love cherry groves", ["cherry groves are the prettiest place", "pink trees are just better"], ["so pretty", "pink wood goes hard"], ["too pink"]),
]

SMALL = [
    ("how_goes", ["sup", "how's it going?", "hey, hows your day", "whats up", "yo, you good?"]),
    ("weather_small", ["nice day huh", "weather's been decent lately", "at least its not raining"]),
    ("mobs_last_night", ["mobs were crazy last night", "heard so many zombies last night", "anyone else hear the skeletons last night"]),
    ("busy_day", ["been busy all day lol", "so much to do today", "long day"]),
    ("whatcha_doing", ["what you up to?", "whatcha doing round here", "you working on something?"]),
    ("town_quiet", ["pretty quiet round here today", "town's busy today", "lot going on today"]),
    ("hungry_small", ["im starving ngl", "could go for some bread right now", "when's dinner lol"]),
    ("nice_gear_small", ["nice gear btw", "your armor's looking good", "where'd you get that stuff"]),
]

PERSONAL = [
    ("vent_long_day", ["ugh, rough day", "today was so bad lol", "i need to vent for a sec"], ["my pick broke right before i hit diamonds", "fell in a hole and lost half my stuff", "a creeper blew up my farm. again"]),
    ("vent_neighbour", ["ok dont tell anyone but", "between you and me"], ["my neighbour keeps taking stuff from the store", "some people round here are so annoying", "i swear someone keeps opening my chests"]),
    ("story_close_call", ["ok so funny story", "you wont believe what happened"], ["i almost died to a baby zombie today lol", "fell 20 blocks and landed in water, still shaking", "a skeleton knocked me into lava, made it out on half a heart"]),
    ("up_to_lately", ["been thinking", "so ive been working on something"], ["im saving up for a better pickaxe", "planning a bigger house", "want to start a proper farm"]),
    ("dream_build", ["ok random but", "you know what i wanna do one day"], ["build a huge castle on the mountain", "go to the end and get elytra", "have a whole wall of diamond blocks"]),
    ("glad_youre_here", ["honestly", "not gonna lie"], ["its nice having you around", "town's better with you here", "glad you showed up round here"]),
    ("worried", ["can i be real for a sec", "ok kinda serious"], ["worried about the bandits lately", "the nights have been getting scary", "not sure i like how things are going in town"]),
    ("miss_someone", ["you ever miss someone", "kinda sad today"], ["miss someone who used to live here", "a friend of mine left and never came back", "some people just log off and dont come back"]),
]

BFF = [
    ("bff_remember_shared", ["lol remember when we both said we {b.shared}", "still cant believe you {b.shared} too", "you and me, the only ones who {b.shared}"]),
    ("bff_inside_joke", ["you know what i was thinking about? the time we met lol", "remember the first time you showed up here", "we've come a long way huh"]),
    ("bff_trust", ["youre the only one i'd trust with my chest key", "if i ever need help youre the first i'd call", "honestly you're my favourite person round here"]),
    ("bff_plans", ["we should build something together one day", "one day we're going to the end together", "we should make a base somewhere, just us"]),
    ("bff_tease", ["you still {b.shared}? good", "never change", "still the best person on this server"]),
]


def take_topic(entry):
    tid, tags, about, takes, agree, disagree = entry
    lines = []
    for t in takes:
        for _ in range(2):
            lines.append(t + " " + random.choice(TAGS))
    lines = sorted(set(lines))
    return {
        "id": "friends_op_" + tid,
        "weight": 0.5,
        "needs": ["b:acq"],
        "tags": tags + ["opinion"],
        "about": about,
        "turns": [{"by": "a", "lines": {"any": lines}}],
        "question": {
            "yes": {"lines": {"any": agree + random.sample(AGREE, 5)}, "do": "opinion"},
            "no": {"lines": {"any": disagree + random.sample(DISAGREE, 5),
                             "pvp_sweat": ["wrong. 1v1 me", "you'll see", "cope"],
                             "helper": ["oh thats ok! everyone's different", "fair enough!"],
                             "hermit": ["hm.", "...ok"]}},
        },
    }


def small_topic(entry):
    tid, lines = entry
    return {"id": "friends_small_" + tid, "weight": 0.6, "needs": [], "tags": ["small_talk"], "turns": [{"by": "a", "lines": {"any": lines}}]}


def personal_topic(entry):
    tid, openers, bodies = entry
    return {"id": "friends_me_" + tid, "weight": 0.8, "needs": ["b:pal"], "tags": ["personal"],
            "turns": [{"by": "a", "lines": {"any": openers}}, {"by": "a", "lines": {"any": bodies}}]}


def bff_topic(entry):
    tid, lines = entry
    needs = ["b:bff"] + (["b:shared"] if any("{b.shared}" in l for l in lines) else [])
    return {"id": "friends_" + tid, "weight": 1.0, "needs": needs, "tags": ["best_friends"], "turns": [{"by": "a", "lines": {"any": lines}}]}


WELCOME = {
    "id": "welcome_back", "weight": 0.0, "needs": ["b:pal"], "tags": ["welcome"],
    "turns": [{"by": "a", "lines": {"any": ["{b}!! youre back", "ayy {b}, welcome back", "{b}! where you been", "look who's back, hey {b}",
                                            "{b}! missed you round here"],
                                    "hermit": ["oh. youre back. good", "{b}. back in one piece i see"],
                                    "helper": ["{b}!!! welcome back :D", "yay {b}'s back!"]}},
              {"by": "a", "options": [
                  {"if": ["b:told_plan"], "lines": {"any": ["how'd the {b.plan} go?", "so hows the {b.plan} thing going?"]}},
                  {"lines": {"any": ["you find anything good out there?", "how was the trip?", "anything cool happen?"]}}]}],
    "question": {"yes": {"lines": {"any": ["nice!! tell me everything", "lets gooo", "jealous ngl", "W"]}, "do": "welcome"},
                 "no": {"lines": {"any": ["rip, next time", "ah thats rough", "well youre back safe at least", "unlucky"]}}},
}


FAVOR_TOPICS = [
    {"id": "friends_favor_iron", "weight": 0.0, "needs": ["b:pal"], "tags": ["favor"],
     "turns": [{"by": "a", "lines": {"any": ["hey {b}, you got any spare iron? like 8? i'll pay you back", "{b} could you lend me 8 iron? short on it rn",
                                             "any chance you got 8 iron on you? ill owe you one", "need iron bad, could you get me 8?"]}}],
     "question": {"yes": {"lines": {"any": ["legend", "ty!! just throw it over when you got it", "youre the best", "ok sick, no rush"]}, "do": "favor_iron"},
                  "no": {"lines": {"any": ["all good", "np, i'll find some", "ok no worries", "fair"]}}}},
    {"id": "friends_favor_food", "weight": 0.0, "needs": ["b:pal"], "tags": ["favor"],
     "turns": [{"by": "a", "lines": {"any": ["{b} you got any food? im all out", "could you spare some food? like 4?", "any chance you got food on you, im starving",
                                             "out of food again lol, got any?"]}}],
     "question": {"yes": {"lines": {"any": ["ty!! throw it over", "life saver", "youre the best"]}, "do": "favor_food"},
                  "no": {"lines": {"any": ["ok np", "ill figure it out", "all good"]}}}},
    {"id": "friends_favor_zombies", "weight": 0.0, "needs": ["b:pal"], "tags": ["favor"],
     "turns": [{"by": "a", "lines": {"any": ["{b} can you help me clear the zombies by my place tonight?", "zombies keep camping my house, help me out tonight?",
                                             "wanna help me fight off the mobs at mine tonight?", "the mobs round my house are crazy, help me tonight?"]}}],
     "question": {"yes": {"lines": {"any": ["lets go", "ty!! meet you there", "sick, bring a sword"]}, "do": "favor_zombies"},
                  "no": {"lines": {"any": ["ok ill manage", "fair, its scary out", "np"]}}}},
]


def write(name, comment, topics):
    path = os.path.join(OUT, name)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump({"comment": comment, "player_topics": topics}, f, indent=1, ensure_ascii=False)
        f.write('\n')
    print(name, len(topics))


write("friends_opinions.json", "Generated by tools/talk_gen/friends.py (D2): hot takes a Drifter shares with an acquaintance or closer, ending on a question; yes = agree (do opinion).",
      [take_topic(t) for t in TAKES])
write("friends_small.json", "Generated by tools/talk_gen/friends.py (D2): small talk, for anyone.", [small_topic(t) for t in SMALL])
write("friends_personal.json", "Generated by tools/talk_gen/friends.py (D2): what a Drifter tells a friend (b:pal) and a best friend (b:bff); welcome_back is started by FriendPerks.",
      [personal_topic(t) for t in PERSONAL] + [bff_topic(t) for t in BFF] + [WELCOME])
write("friends_favors.json", "Generated by tools/talk_gen/friends.py (D2): favors a friend asks (started by FriendPerks; do favor_<kind>).", FAVOR_TOPICS)
