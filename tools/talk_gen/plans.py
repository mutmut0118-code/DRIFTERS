"""Writes drifter_talk/topics_plans.json: talks that end in doing something together (social/talk/Together.java)."""
import json
import sys

out = sys.argv[1]
topics = []


def plan(id, effect, needs, tags, leads, about, ask, yes, no, after_yes, after_no, pre=None, reluctant='b:loner', weight=2.0,
         keen=None):
    """a asks; b says yes (less often if reluctant, more if keen), or no; a answers. The yes makes the plan (effect)."""
    turns = list(pre or [])
    turns.append({"by": "a", "lines": ask})
    turns.append({"by": "b", "if": [reluctant], "chance": 0.3, "lines": yes, "mark": ["plan_yes", "agree"]})
    if keen:
        turns.append({"by": "b", "if": [keen, "!" + reluctant, "!t:plan_yes"], "chance": 0.95, "lines": yes, "mark": ["plan_yes", "agree"]})
    turns.append({"by": "b", "if": ["!" + reluctant, "!t:plan_yes"], "chance": 0.75, "lines": yes, "mark": ["plan_yes", "agree"]})
    turns.append({"by": "b", "if": ["!t:plan_yes"], "lines": no, "mark": ["decline"]})
    turns.append({"by": "a", "if": ["t:plan_yes"], "chance": 0.85, "lines": after_yes})
    turns.append({"by": "a", "if": ["t:decline"], "chance": 0.75, "lines": after_no})
    topics.append({"id": id, "weight": weight, "needs": needs, "tags": tags, "leads": leads, "about": about, "turns": turns,
                   "effects": [effect]})


# ---- Mining together ----
MINE_NEEDS = ["a:can_mine", "b:can_mine", "!rivals", "!a:keeps_shop", "!b:keeps_shop"]
plan("plan_mine_now", "plan_mine", MINE_NEEDS + ["!strangers", "!time_night", "!time_evening"], ["plans", "mining"],
     ["mining", "caves", "ore"], "going mining together",
     ask={"any": ["wanna go mining together?", "i'm heading down for iron soon, come with?", "mining trip? two pickaxes beat one",
                  "need a mining buddy, you in?", "wanna go caving later? safer with two", "you up for a mining run today?"],
          "miner": ["there's a cave mouth not far from here, wanna go down together?", "i'm going deep today. coming?",
                    "i know a good spot for iron, want in?"],
          "newbie": ["can i come mining with you? i always get lost down there", "will you go mining with me? caves are scary alone"],
          "speedrunner": ["quick iron run? together's faster", "strip mine sprint, you in?"],
          "casual": ["wanna go mine some stuff lol", "mining? i'm bored of the surface"],
          "hoarder": ["wanna go mining? we split everything... mostly"]},
     yes={"any": ["sure", "i'm down", "yeah let's go", "bet, i've got torches", "ok but i'm not going near lava",
                  "sure, i need iron anyway", "count me in"],
          "miner": ["always", "you don't have to ask twice", "finally someone who gets it"],
          "newbie": ["yes!! i'll stay close", "ok but you go first"],
          "hermit": ["fine. stay quiet down there.", "ok. don't talk too much."],
          "speedrunner": ["go go go", "yes, fast though"]},
     no={"any": ["nah i'm good up here", "maybe later", "not today, got stuff to do", "caves give me the creeps",
                 "i'll pass this time", "can't, i'm busy"],
         "hermit": ["i mine alone.", "no."],
         "homebody": ["i'd rather stay home", "too dark down there for me"],
         "builder": ["i'm in the middle of a build", "can't, the roof won't build itself"]},
     after_yes={"any": ["cool, i'll lead", "nice, bring torches", "sweet, stick close down there", "let's do it",
                        "ok, when i head down you follow", "awesome, finally some company down there"],
                "speedrunner": ["keep up", "no afk"]},
     after_no={"any": ["all good", "your loss lol", "another time then", "fair", "more ore for me"]})

plan("plan_mine_tomorrow", "plan_mine", MINE_NEEDS + ["!strangers", "time_evening"], ["plans", "mining"],
     ["mining", "night", "plans"], "a mining trip tomorrow",
     ask={"any": ["wanna go mining tomorrow? too late now", "mining trip tomorrow morning?", "tomorrow, me and you, mining. yes?",
                  "first thing tomorrow, wanna hit the caves?"],
          "miner": ["i'm going deep tomorrow, come with", "tomorrow i'm after diamonds. want in?"],
          "newbie": ["can we go mining together tomorrow?"]},
     yes={"any": ["deal, tomorrow", "sure, first thing", "ok, wake me up", "yeah, tomorrow works", "sounds like a plan"],
          "casual": ["bet, tomorrow", "ok if i wake up lol"]},
     no={"any": ["can't tomorrow", "tomorrow's no good", "i'll see how i feel", "nah, got plans"],
         "hermit": ["probably not."]},
     after_yes={"any": ["cool, see you in the morning", "nice, bring a spare pick", "it's a plan", "don't oversleep"]},
     after_no={"any": ["ok, some other time", "no worries", "fair enough"]})

plan("plan_mine_ore_spot", "plan_mine", MINE_NEEDS + ["a:found_ore", "!time_night"], ["plans", "mining", "ore"],
     ["ore", "mining", "caves"], "the ore it found",
     pre=[{"by": "a", "lines": ["i found a {a.ore} vein not far from here", "there's {a.ore} in a cave near here, i saw it",
                                "saw some {a.ore} the other day, left it for later"]},
          {"by": "b", "chance": 0.8, "lines": ["oh nice", "how much?", "where?", "jealous", "you didn't mine it?"]}],
     ask={"any": ["wanna go get it together?", "come help me mine it out?", "we could split it, wanna come?",
                  "i'll show you where, if you help"]},
     yes={"any": ["yes please", "i'm in", "split it fifty fifty?", "show me", "say less"]},
     no={"any": ["nah, it's yours", "you go ahead", "i'm good, thanks though"]},
     after_yes={"any": ["deal", "ok, next time i head down", "cool, half's yours"]},
     after_no={"any": ["more for me then", "ok, your loss"]})

# ---- Hunting tonight ----
HUNT_NEEDS = ["a:can_hunt", "b:can_hunt", "!rivals", "!time_morning", "!time_night", "!a:timid"]
plan("plan_hunt_tonight", "plan_hunt", HUNT_NEEDS + ["!strangers"], ["plans", "night", "fighting"],
     ["night", "mobs", "fighting"], "hunting tonight", reluctant="b:timid", keen="b:brave",
     ask={"any": ["hunt tonight?", "wanna go mob hunting when it gets dark?", "i need bones and string, come hunt with me tonight?",
                  "tonight, mob hunt, you and me?", "you up for some night fighting?", "i'm going out after dark, wanna come?"],
          "pvp_sweat": ["tonight. you and me. let's farm some mobs the hard way", "night hunt? i need the practice"],
          "speedrunner": ["need pearls. endermen tonight?", "blaze rods later, but tonight, pearls. you in?"],
          "casual": ["wanna go beat up some zombies tonight lol", "night hunt? could be fun"],
          "nomad": ["the night's when things happen. come out with me?"]},
     yes={"any": ["bet", "i'm in, i need xp anyway", "ok but you're on creeper duty", "let's do it", "sure, i'll bring a sword",
                  "yeah, safer with two"],
          "pvp_sweat": ["finally", "let's go, i'll take the skeletons"],
          "newbie": ["ok... you'll protect me right?", "eek ok, i'll try"],
          "casual": ["sure why not lol", "ok but if i die it's on you"]},
     no={"any": ["no way, i'm staying inside", "it's dangerous out there at night", "i'll pass, i like my hearts",
                 "not tonight", "i'm too beat"],
         "homebody": ["absolutely not, i'll be in bed", "nights are for staying in"],
         "newbie": ["no no no, the zombies scare me"],
         "hermit": ["no."]},
     after_yes={"any": ["see you at dusk", "bring a sword", "nice, back to back", "don't run off", "it's a date lol"],
                "pvp_sweat": ["don't steal my kills", "we go at dark"]},
     after_no={"any": ["fine, more xp for me", "suit yourself", "ok, stay safe", "scaredy cat lol"]})

plan("plan_hunt_drops", "plan_hunt", HUNT_NEEDS, ["plans", "night", "mobs"], ["mobs", "night", "crafting"], "the drops it needs",
     reluctant="b:timid", keen="b:brave",
     pre=[{"by": "a", "lines": ["i'm out of string", "need gunpowder for tnt", "i need more bones for my farm", "out of arrows, need feathers and string"]},
          {"by": "b", "chance": 0.7, "lines": ["mobs tonight then", "night's coming", "spiders drop string", "that's a night job"]}],
     ask={"any": ["come out with me tonight?", "wanna help me farm some tonight?", "you wanna come? we split the drops"]},
     yes={"any": ["ok, i need some too", "sure, split the drops", "why not", "fine, but i keep the pearls"]},
     no={"any": ["good luck with that", "i'll stay in, thanks", "nope, too risky"]},
     after_yes={"any": ["cool, meet at dusk", "nice, we'll clean up", "great, stick close"]},
     after_no={"any": ["ok, i'll go alone", "fair"]})

# ---- Showing its home ----
plan("plan_show_home", "plan_show_home", ["a:home_near", "!b:been_to_theirs", "!rivals"], ["plans", "home"],
     ["home", "building", "decor"], "its house",
     ask={"any": ["wanna see my house?", "come check out my place, it's right over there", "want a tour of my base?",
                  "i live just over there, wanna see?", "you wanna come see my place?"],
          "builder": ["wanna see what i've been building? it's close", "i just finished the roof, come look",
                      "i need a second opinion on my build, come see"],
          "newbie": ["do you wanna see my house?? i built it myself", "come see my house! it's my first one"],
          "homebody": ["come over, i'll show you around", "want to see where i live? it's cozy"],
          "hoarder": ["wanna see my storage room? it's beautiful"],
          "casual": ["wanna see my house? it's kinda mid but"]},
     yes={"any": ["sure, show me", "yeah let's see it", "ooh yes", "lead the way", "sure, i've been curious"],
          "builder": ["always, show me", "let's see your style"],
          "newbie": ["yes!!", "ooh ok!"],
          "hermit": ["fine. quickly."]},
     no={"any": ["maybe another time", "i'm kinda busy, later?", "nah, i'm good", "some other time"],
         "hermit": ["no.", "i don't do visits."],
         "speedrunner": ["no time, sorry"]},
     after_yes={"any": ["awesome", "cool cool", "ok you're gonna love it", "nice, it's not far", "yay"]},
     after_no={"any": ["ok, offer stands", "no worries", "another time then"]})

plan("plan_show_home_asked", "plan_show_home", ["a:home_near", "!b:been_to_theirs", "!rivals", "!strangers"], ["plans", "home"],
     ["home", "building", "town"], "where it lives",
     pre=[{"by": "b", "lines": ["where do you live anyway?", "you got a base around here?", "do you have a house?",
                                "where's your place at?"]}],
     ask={"any": ["yeah, it's close, wanna see it?", "just over there, want to see?", "right nearby, i'll show you",
                  "close by. want the tour?"]},
     yes={"any": ["sure", "yeah ok", "go on then", "lead on"]},
     no={"any": ["oh another time", "maybe later, i was just asking"]},
     after_yes={"any": ["ok", "great", "cool"]},
     after_no={"any": ["ok lol", "fair"]})

# ---- Sharing food ----
FOOD_NEEDS = ["a:spare_food", "b:hungry", "!a:hungry", "!rivals"]
plan("plan_share_food_offer", "plan_share_food", FOOD_NEEDS, ["plans", "food", "help"], ["food", "farming", "help"],
     "food", weight=3.0, reluctant="b:loner",
     ask={"any": ["you look hungry, want some food?", "you hungry? i've got extra", "want some of my food?",
                  "here, i have more than i need, want some?", "when did you last eat? want some?"],
          "helper": ["you're starving, let me give you something", "take some of my food, please"],
          "hoarder": ["i guess i could spare some food. maybe.", "i have food. it'll cost you. kidding. want some?"],
          "newbie": ["are you hungry? you can have some of mine!"],
          "casual": ["yo you're starving, want some food?"]},
     yes={"any": ["yes please", "omg yes", "i'm starving, thank you", "you're a lifesaver", "if you don't mind"],
          "hermit": ["...ok. thanks."],
          "newbie": ["really?! yes please"]},
     no={"any": ["i'm ok, thanks", "nah i'll find my own", "i'm fine, really", "keep it, you'll need it"],
         "hermit": ["i don't need help."]},
     after_yes={"any": ["hold on", "one sec", "coming right up", "sure thing"]},
     after_no={"any": ["ok, just ask", "if you change your mind", "ok, stubborn"]})

plan("plan_share_food_complain", "plan_share_food", FOOD_NEEDS, ["plans", "food", "complaints"], ["food", "farming", "feelings"],
     "being hungry", weight=2.5, reluctant="b:loner",
     pre=[{"by": "b", "lines": ["i'm so hungry", "i haven't eaten all day", "my stomach won't stop growling", "i'm running on empty",
                                "i could eat a whole cow"]}],
     ask={"any": ["here, i can give you some", "want some of mine?", "i've got spare, want it?", "i'll give you some, hold on"],
          "helper": ["i got you, here"], "hoarder": ["...fine. i have some. you want it?"]},
     yes={"any": ["really? thanks", "you'd do that?", "please", "you're the best"]},
     no={"any": ["nah, i'll manage", "i'll go hunt something", "don't worry about it"]},
     after_yes={"any": ["of course", "no problem", "course"]},
     after_no={"any": ["ok, if you're sure", "suit yourself"]})

# ---- Tagging along ----
TAG_NEEDS = ["!rivals", "!a:keeps_shop", "!strangers"]
plan("plan_tag_along", "plan_tag_along", TAG_NEEDS, ["plans", "friends"], ["friends", "exploring", "doing"],
     "tagging along",
     pre=[{"by": "a", "lines": ["what are you up to?", "whatcha doing?", "busy?", "what's the plan today?"]},
          {"by": "b", "lines": ["just doing stuff", "gathering, mostly", "nothing much, wandering", "this and that",
                                "the usual"]}],
     ask={"any": ["mind if i tag along?", "can i come with?", "want company?", "i'll come with you, if that's ok"],
          "newbie": ["can i follow you? i wanna learn how you do stuff", "can i come? i'll be quiet"],
          "casual": ["mind if i tag along lol", "i'll come, i'm bored"],
          "nomad": ["mind if i wander with you a while?"]},
     yes={"any": ["sure, come along", "yeah why not", "the more the merrier", "sure, keep up", "ok, stick close"],
          "newbie": ["yes! company!"], "helper": ["of course, come on"]},
     no={"any": ["i work better alone", "nah, it's boring stuff", "maybe next time", "i'd rather go alone today"],
         "hermit": ["no.", "i'm better alone."]},
     after_yes={"any": ["sweet", "cool, lead on", "nice", "let's go"]},
     after_no={"any": ["fair enough", "ok, see you", "no worries"]})

plan("plan_tag_along_bored", "plan_tag_along", TAG_NEEDS + ["a:mood_bored"], ["plans", "boredom"], ["boredom", "friends", "random"],
     "being bored",
     pre=[{"by": "a", "lines": ["i'm so bored", "there's nothing to do", "i've done everything today", "so bored i started counting blocks"]}],
     ask={"any": ["can i just follow you around for a bit?", "entertain me. can i come with you?", "whatever you're doing, can i join?"]},
     yes={"any": ["lol sure", "ok, but it's not exciting", "come on then", "sure, you can help"]},
     no={"any": ["go find a hobby lol", "nah, go build something", "you'll be more bored with me"]},
     after_yes={"any": ["thank you", "anything's better than this", "yay"]},
     after_no={"any": ["rude", "fine", "ugh ok"]})

plan("plan_explore_together", "plan_tag_along", TAG_NEEDS + ["!time_night", "!time_evening"], ["plans", "exploring"],
     ["exploring", "biomes", "structures"], "exploring together",
     ask={"any": ["wanna go exploring? you lead, i'll follow", "show me around? you know this area better",
                  "take me somewhere cool?", "where's the best spot round here? show me"],
          "newbie": ["can you show me around? i don't know this place"],
          "nomad": ["i've never been this way. show me?"]},
     yes={"any": ["sure, follow me", "ok, i know a spot", "come on then", "yeah, i'll show you"]},
     no={"any": ["i'm not a tour guide lol", "not right now", "maybe later"]},
     after_yes={"any": ["nice", "cool", "lead on"]},
     after_no={"any": ["ok", "fine lol"]})

# ---- Visiting later ----
plan("plan_visit_later", "plan_visit", ["a:has_house", "!rivals", "!b:keeps_shop", "!time_night", "!time_evening"],
     ["plans", "home", "friends"], ["home", "friends", "town"], "visiting later",
     ask={"any": ["you should come by my place sometime", "drop by my house later if you want", "come visit later, i'll show you around",
                  "stop by mine later?", "come over later, i'll be home"],
          "homebody": ["come over later, i'll be in", "visit me later, i never get visitors"],
          "builder": ["come by later, i'll have the new room done"],
          "newbie": ["will you visit me later?"],
          "hermit": ["you can come by later. if you must."]},
     yes={"any": ["will do", "sure, i'll swing by", "ok i'll stop by later", "sounds good", "i'll be there"],
          "casual": ["bet", "ok i'll pull up"]},
     no={"any": ["maybe, we'll see", "i'm not much for visiting", "can't today", "we'll see"],
         "hermit": ["probably not."]},
     after_yes={"any": ["cool, it's not far", "see you later then", "i'll be around", "great, see you"]},
     after_no={"any": ["offer's open", "ok, whenever", "no pressure"]})

plan("plan_visit_ask", "plan_visit", ["a:has_house", "!b:been_to_theirs", "!rivals", "!strangers", "!time_night", "!time_evening"],
     ["plans", "home"], ["home", "friends"], "never having been over",
     pre=[{"by": "b", "lines": ["i've never seen your place", "you never invite me over", "where even is your house"]}],
     ask={"any": ["come by later then", "come over later, i'll show you", "you're always welcome, come by later"]},
     yes={"any": ["ok i will", "deal", "sure, later", "i'll hold you to that"]},
     no={"any": ["eh, maybe", "maybe some day"]},
     after_yes={"any": ["good", "see you then", "looking forward to it"]},
     after_no={"any": ["ok lol", "fair"]})

data = {"_comment": "Talks that end in doing something together (side session, 2026-11-06; the user: \"ofc the talking leading to actions\"): "
                    "a asks, b says yes (less often if a loner, or timid for a night hunt) or no, and a yes makes a plan "
                    "(social/talk/Together.java): go mining (now: b tags along till a mining trip; either way the next trip that "
                    "day is together), hunt tonight, come see a's house, share food, tag along, visit later.",
        "topics": topics}
json.dump(data, open(out, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print(len(topics), 'topics')
