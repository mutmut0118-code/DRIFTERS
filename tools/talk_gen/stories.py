"""Writes drifter_talk/topics_gen_stories.json: stories told in several parts (the user: "add more parts to each story").

Usage: python tools/talk_gen/stories.py src/main/resources/data/drifter/drifter_talk/topics_gen_stories.json

Two kinds of thing in here:

1. New stories in 3 to 5 parts (id prefix g_story_). Each story skeleton is a function that writes the parts of one
   story from a variant (the place, the mob, the item, the twist, the ending), so one skeleton gives several different
   stories. Every line in a turn says the same beat in other words, so whichever line the game picks, the next turn and
   the next part still follow. Part 1 is a normal topic with "next"; later parts are follow_ups chained by "next".
2. Part 3 (and sometimes 4) for the stories that were already told in two parts (topics_stories.json,
   topics_stories_more.json), hooked on with the file's top-level "links" (the loader merges them into those topics).

Later parts are tagged story_part only (SUBJECT_TAGS_ON_PARTS): Conversations.linked() picks any topic with a tag the
last topic leads to, follow_ups included, so a part 3 tagged "mobs" could come up out of nowhere in a talk about mobs.
They cost 0.5 interest each, so a long story isn't cut off as soon as a short talk would be.

The listener's reactions come from pools by kind (ask, gasp, laugh, sad, wow, relief, creepy, aw, done), mixed with
lines written for that beat; some turns carry a few voices (a newbie says "omg", a pvp_sweat "skill issue").
"""
import json
import random
import re
import sys
from types import SimpleNamespace as NS

OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/data/drifter/drifter_talk/topics_gen_stories.json"
PREFIX = "g_story_"
SUBJECT_TAGS_ON_PARTS = False
PART_COST = 0.5
rng = random.Random(20261106)
topics = []
links = {}

# ---------------------------------------------------------------------------------------------------------------------
# The listener's reactions, by kind. "any" for everyone, then a few lines in some voices.
POOLS = {
    "ask": {
        "any": ["and then?", "then what", "go on", "and??", "ok and?", "what happened", "dont stop there", "keep going",
                "and then what", "wait what happened"],
        "newbie": ["omg then what", "wait what happened next", "go on go on"],
        "hermit": ["and.", "go on.", "hm. and"],
        "speedrunner": ["skip to the end", "and? quick", "faster, then what"],
        "pvp_sweat": ["get to the good part", "and? hurry up"],
        "helper": ["oh no, go on", "i'm listening, go on"],
        "nomad": ["go on, i love a good tale", "and then?"],
        "casual": ["lol go on", "ok ok and"],
        "bandit": ["heh. go on", "and then"],
        "homebody": ["oh dear. go on", "go on, i'm nervous"],
    },
    "gasp": {
        "any": ["oh no", "no way", "bro", "yikes", "dude", "NO", "uh oh", "oh no no no", "nooo", "that's terrifying"],
        "newbie": ["thats my worst nightmare", "omg", "i would have cried"],
        "pvp_sweat": ["skill issue", "should've seen it coming", "i'd have clutched that"],
        "hermit": ["bad.", "hm. bad spot"],
        "helper": ["oh no, that sounds awful", "poor you"],
        "speedrunner": ["that's a reset", "huge time loss"],
        "nomad": ["the road is cruel", "seen worse. barely"],
        "casual": ["bro no", "oof"],
        "homebody": ["this is why i stay home", "no thank you"],
        "miner": ["happens down there", "classic deep stuff"],
    },
    "laugh": {
        "any": ["lmao", "LMAO", "lol", "im dying", "classic", "bro what", "lol no way", "hahaha", "that's so dumb lol", "iconic"],
        "newbie": ["hahaha thats so me", "lol i did that too once"],
        "pvp_sweat": ["lol noob", "bozo moment"],
        "hermit": ["heh", "dumb. funny though"],
        "helper": ["aw lol", "oh no lol"],
        "speedrunner": ["lol wasted time", "ok that's funny"],
        "casual": ["lmaooo", "bro"],
        "bandit": ["hah", "lol nice one"],
        "builder": ["lol", "that's beautiful in a dumb way"],
    },
    "smirk": {
        "any": ["lol", "oh no", "here we go", "uh oh", "lol go on", "i can already tell", "this'll be good"],
        "newbie": ["oh no lol", "hehe go on"],
        "pvp_sweat": ["this'll be good", "lol ok"],
        "hermit": ["heh. go on", "hm. go on"],
        "helper": ["oh dear, go on", "oh no, what happened"],
        "casual": ["lmao already", "oh no lol"],
    },
    "sad": {
        "any": ["aw", "oh no", "that's sad", "sorry", "rip", "f", "noo", "that hurts", "oof", "that's rough"],
        "helper": ["i'm so sorry", "that's rough, friend"],
        "hermit": ["happens", "hm. sorry"],
        "newbie": ["thats so sad", "noooo"],
        "pvp_sweat": ["rip", "unlucky"],
        "homebody": ["oh, that's awful", "poor thing"],
        "nomad": ["loss is part of the road", "that's the way of it"],
        "hoarder": ["losing stuff is the worst", "my heart"],
    },
    "wow": {
        "any": ["no way", "lucky", "jealous", "insane", "w", "huge w", "nice", "lets go", "what a find"],
        "newbie": ["woah!!", "i want that so bad"],
        "pvp_sweat": ["ok that's cracked", "not bad"],
        "hoarder": ["jealous. so jealous", "i'd never let that go"],
        "speedrunner": ["nice rng", "good luck there"],
        "hermit": ["nice.", "huh. lucky"],
        "builder": ["that'd look great on a wall", "ooh"],
        "casual": ["yo", "W"],
        "bandit": ["now that's a score", "nice haul"],
    },
    "relief": {
        "any": ["phew", "lucky", "close one", "glad you made it", "that was close", "clutch", "you got lucky"],
        "newbie": ["phew omg", "i was so worried"],
        "pvp_sweat": ["clutch", "ok that's skill"],
        "helper": ["thank goodness", "so glad you're ok"],
        "hermit": ["lucky.", "hm. close"],
        "speedrunner": ["no reset, nice", "saved"],
        "casual": ["phew lol", "close"],
    },
    "creepy": {
        "any": ["creepy", "nope", "no thanks", "chills", "i'm scared now", "that's creepy", "brr"],
        "newbie": ["i'm never going outside again", "nope nope nope"],
        "hermit": ["the dark talks. don't listen", "hm."],
        "pvp_sweat": ["just fight it lol", "scared? lol"],
        "homebody": ["and this is why i stay home", "i'd have gone straight home"],
        "helper": ["that sounds so scary", "you poor thing"],
        "casual": ["nah that's creepy", "bro stop"],
    },
    "aw": {
        "any": ["aw", "aww", "cute", "that's sweet", "wholesome", "love that"],
        "newbie": ["aww i want one", "so cute"],
        "hermit": ["hm. nice", "ok. that's nice"],
        "pvp_sweat": ["ok that's kinda cute", "soft"],
        "helper": ["that's lovely", "oh that's precious"],
        "homebody": ["so sweet", "that warms my heart"],
    },
    "done": {
        "any": ["what a story", "crazy", "wild", "good story", "what a day", "legend", "you should write that down"],
        "newbie": ["thats the best story ever", "wow"],
        "hermit": ["good story.", "hm. good one"],
        "pvp_sweat": ["not bad", "decent story"],
        "nomad": ["that's a story worth telling", "a good tale"],
        "helper": ["thanks for telling me", "what a thing to go through"],
        "speedrunner": ["good story, long though", "nice"],
    },
}


def dedupe(lines):
    seen, out = set(), []
    for line in lines:
        key = line["line"] if isinstance(line, dict) else line
        if key not in seen:
            seen.add(key)
            out.append(line)
    return out


def turn(by, lines, chance=None, cond=None, mark=None):
    d = {"by": by, "lines": lines}
    if chance is not None:
        d["chance"] = chance
    if cond:
        d["if"] = cond
    if mark:
        d["mark"] = mark
    return d


def A(*lines, chance=None, cond=None, mark=None, **voices):
    """The teller's turn: lines that all say the same beat (and maybe a voice's own way of saying it)."""
    lines = [l for l in lines if l]
    if voices:
        body = {"any": lines}
        body.update({k: v for k, v in voices.items() if v})
    else:
        body = lines
    return turn("a", body, chance, cond, mark)


def B(kind, *spec, chance=None, cond=None, mark=None, gen=2):
    """The listener's turn: lines written for this beat, plus a few from the pool of that kind (and maybe some voices)."""
    spec = [l for l in spec if l]
    if kind is None:
        return turn("b", spec, chance, cond, mark)
    pool = POOLS[kind]
    plain = [l for l in spec if not isinstance(l, dict)]
    picks = rng.sample(pool["any"], min(gen, len(pool["any"])))
    body = dedupe(spec + picks)
    if rng.random() < 0.5:
        voices = [v for v in pool if v != "any"]
        out = {"any": body}
        for name in rng.sample(voices, 2 if rng.random() < 0.4 else 1):
            own = rng.sample(pool[name], min(2, len(pool[name])))
            out[name] = dedupe(own + ([rng.choice(plain)] if plain else []))
        return turn("b", out, chance, cond, mark)
    return turn("b", body, chance, cond, mark)


def Q(line, *answers):
    """A question whose answers the other says next (instead of its own lines)."""
    return {"line": line, "then": list(answers)}


def P(*turns):
    return [t for t in turns if t]


def tell(key, parts, tags, about, leads=None, end_leads=None, needs=None, weight=0.6):
    """One story: its parts (lists of turns; None parts are left out) chained by next."""
    parts = [p for p in parts if p]
    assert 3 <= len(parts) <= 5, (key, len(parts))
    base = PREFIX + key
    leads = leads or tags[:3]
    end_leads = end_leads or leads
    for i, turns in enumerate(parts):
        tid = base if i == 0 else "%s_%d" % (base, i + 1)
        t = {"id": tid}
        if i == 0:
            if weight != 1.0:
                t["weight"] = weight
            if needs:
                t["needs"] = list(needs)
            t["tags"] = ["stories"] + tags[:3]
        else:
            t["follow_up"] = True
            t["cost"] = PART_COST
            t["tags"] = ["story_part"] + (tags[:3] if SUBJECT_TAGS_ON_PARTS else [])
        t["leads"] = list(end_leads if i == len(parts) - 1 else leads)
        t["about"] = about
        if i < len(parts) - 1:
            t["next"] = ["%s_%d" % (base, i + 2)]
        t["turns"] = turns
        topics.append(t)


def link_part(existing_id, key, parts, about, leads, start=3):
    """More parts for a story already told elsewhere (its last part is existing_id, which has no next of its own),
    numbered on from start and hooked on to that part with links."""
    parts = [p for p in parts if p]
    base = PREFIX + key
    for i, turns in enumerate(parts):
        tid = "%s_%d" % (base, i + start)
        t = {"id": tid, "follow_up": True, "cost": PART_COST, "tags": ["story_part"], "leads": list(leads), "about": about}
        if i < len(parts) - 1:
            t["next"] = ["%s_%d" % (base, i + start + 1)]
        t["turns"] = turns
        topics.append(t)
    links[existing_id] = {"next": ["%s_%d" % (base, start)]}


def each(variants, fn):
    """Runs a skeleton over its variants (dicts), numbering them v1, v2..."""
    for i, v in enumerate(variants):
        fn("v%d" % (i + 1), NS(**v))


# =====================================================================================================================
# DEATHS AND CLOSE CALLS
# =====================================================================================================================

# --- Mining straight into lava.
def lava_dig(n, v):
    p1 = P(
        A(f"so i'm strip mining {v.where}", f"ok picture this. me, {v.where}, mining away",
          f"was digging a tunnel {v.where}, nice and quiet", f"story time. i was mining {v.where}"),
        B("ask", "uh oh", "i don't like where this is going", "go on"),
        A(f"already had {v.loot} on me", f"best trip in ages, {v.loot} already", f"{v.loot} in my bag. life was good"),
        B("wow", "nice haul", "don't jinx it", "i sense a but coming"),
        A("then i break one more block", "so i mine the block in front of me", "i swing at the next bit of stone and"),
        B(None, "oh no", "it's lava isn't it", "don't say it", "please not lava", "noooo"),
    )
    if v.save == "bucket":
        save = P(A("but i had a water bucket on my hotbar", "luckily i had water. slapped it down", "water bucket. never been so fast"),
                 B("relief", "CLUTCH", "the bucket saves lives", "always carry water", "nice reflexes"))
    elif v.save == "potion":
        save = P(A("but i had one fire resistance potion", "so i chug the one fire res potion i had", "luckily i'd brewed fire resistance"),
                 B("relief", "who carries fire res lol", "big brain", "the potion!", "prepared"))
    else:
        save = P(A("and my water bucket was back home in a chest", "no water bucket. nothing. just a pickaxe", "of course i'd left my bucket at home"),
                 B("gasp", "oh no", "always carry water", "rookie move", "you're doomed"))
    p2 = P(
        A("lava. pouring straight at my feet", "and lava just gushes out of the wall", "lava comes pouring in like a waterfall",
          "the whole wall turns orange. lava"),
        B("gasp", "NOOO", "lava is the worst", "run", "with all that loot on you??"),
        *save,
        A("so i'm backing up as fast as i can", "i turn around and run back down the tunnel", "i start running backwards up the tunnel",
          chance=0.7),
    )
    if v.end == "dead":
        p3 = P(
            A("didn't matter. i caught fire anyway", "too slow. the lava got my feet", "i tripped on a torch and fell right in",
              "i was on fire before i'd gone three blocks"),
            B("sad", "noooo", f"not the {v.short}", "pain", "rip everything"),
            A(f"{v.short} gone. burned up with me", f"everything burned. {v.short} too", f"respawned at home. {v.short} was ash"),
            B("sad", "that's the worst way to lose stuff", "lava doesn't give anything back", "f in the chat"),
            A("now i carry two water buckets", "i check every wall for lava now", "now i dig with a bucket in my offhand",
              "lesson learned the hard way", chance=0.7),
        )
    elif v.end == "pick":
        p3 = P(
            A("i got out. my pickaxe didn't", "i lived, but i dropped my pickaxe in", "my pick slipped right out of my hand into it",
              "made it out, minus one pickaxe"),
            B("laugh", "the pick noooo", "rip pickaxe", "better it than you", "it died so you could live"),
            A(f"at least i kept {v.loot}", f"still had {v.loot} though", f"the {v.short} made it, that's what matters"),
            B("relief", "worth it", "fair trade", "a pickaxe is cheap"),
            A("gave that pickaxe a funeral", "i still miss that pick honestly", "made a sign for it. rip pick", chance=0.6),
        )
    else:
        p3 = P(
            A("got out with half a heart left", "crawled out on half a heart, smoking", "stumbled out on one heart",
              "made it out. barely. half a heart"),
            B("relief", "half a heart lol", "how", "you have nine lives"),
            A(f"and i kept the {v.short}. all of it", f"kept everything. {v.short} too", f"and the {v.short}? safe. every bit"),
            B("wow", "huge", "lucky lucky lucky", "luck likes you"),
            A("sat in my bed for an hour after", "walked home so slowly lol", "i didn't mine again for a week", chance=0.6),
        )
    p4 = None
    if v.epi:
        p4 = P(
            A("but get this. i went back later", "the best part is what i found after", "so a few days later i go back there"),
            B("ask", "why would you go back", "brave", "for what"),
            A(f"the lava had cooled into obsidian. {v.epi}", f"water hit it, made obsidian. {v.epi}",
              f"i poured water on it and there was obsidian. {v.epi}"),
            B("wow", "the lava paid you back", "silver lining", "nice"),
            A("best mistake i ever made", "so it all worked out", "lava giveth, lava taketh", chance=0.6),
        )
    tell("lava_" + n, [p1, p2, p3, p4], ["danger", "mining", "death"], "the lava", leads=["danger", "mining", "caves"],
         end_leads=["mining", "danger", "death"])


each([
    dict(where="down at the deepslate", loot="nine diamonds", short="diamonds", save="none", end="dead", epi=""),
    dict(where="in the side of a ravine", loot="a stack of iron", short="iron", save="bucket", end="safe", epi="enough for a portal"),
    dict(where="right under my base", loot="gold and two diamonds", short="diamonds", save="potion", end="safe", epi=""),
    dict(where="near a big lava lake", loot="a full bag of ore", short="ore", save="none", end="pick", epi=""),
    dict(where="way down past the caves", loot="six diamonds and some lapis", short="diamonds", save="bucket", end="pick",
         epi="i mined it with my new pick"),
    dict(where="at the bottom of a cave", loot="emeralds, can you believe it", short="emeralds", save="none", end="dead", epi=""),
    dict(where="by the old mineshaft", loot="redstone and a stack of coal", short="redstone", save="potion", end="pick",
         epi="built a portal right there"),
    dict(where="in my own branch mine", loot="three diamonds", short="diamonds", save="bucket", end="safe", epi=""),
], lava_dig)


# --- A creeper sneaking up while it's busy.
def creeper_sneak(n, v):
    p1 = P(
        A(f"so i'm {v.task}, totally focused", f"there i am, {v.task}, minding my business", f"picture me {v.task}, peaceful as anything"),
        B("ask", "uh oh", "i know that peaceful feeling", "this ends badly doesn't it"),
        A(f"and i hear it. tssssss", "and then. that sound. the hiss", "then right behind me: tsssss", "and from behind me comes a hiss"),
        B("gasp", "NO", "the worst sound in the world", "RUN", "not the hiss"),
        A("i don't even turn around", "i didn't have time to look", "i spun round and it was right there", chance=0.7),
    )
    p2 = P(
        A(f"it was right at my back, {v.where}", f"it had crept up {v.where}", f"creeper. green. smiling. {v.where}"),
        B("gasp", "how do they always do that", "silent killers", "how did you not hear it walk up"),
        A(*v.boom),
        B(v.react, *v.react_lines),
        A(*v.after, chance=0.8),
    )
    p3 = P(
        A(*v.result),
        B(v.react2, *v.react2_lines),
        A(*v.result2),
        B("done", "never turn your back", "they're everywhere", "creepers, man"),
        A(*v.lesson, chance=0.6),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A("and the next day, guess what", "and it wasn't over. the next night", "you'd think that was the end of it"),
            B("ask", "not another one", "no", "what now"),
            A(*v.epi),
            B("laugh", "they're hunting you", "the creepers have a grudge", "lol"),
            A("i sleep with a sword now", "i put a fence round everything now", "i got a cat after that", chance=0.6),
        )
    tell("creeper_" + n, [p1, p2, p3, p4], ["mobs", "danger"], "the creeper", leads=["mobs", "danger", "home"],
         end_leads=["mobs", "jokes", "danger"])


each([
    dict(task="fixing the roof on my house", where="up on the roof with me", react="gasp", react_lines=["on the ROOF?", "how did it get up there"],
         boom=["BOOM. blew me clean off the roof", "it went off and launched me off the roof", "it exploded and i went flying off the roof"],
         after=["flew like ten blocks", "i saw the whole sky", "i was airborne for a while"],
         result=["landed in my own pond. not a scratch", "splashed down in the pond out front", "landed in the water by the door"],
         react2="relief", react2_lines=["the pond saved you lol", "what luck", "clutch landing"],
         result2=["but there's a crater in my roof now", "the roof though. big hole", "half my roof is just sky now"],
         lesson=["it's a skylight now. on purpose", "i call it a skylight", "i kept the hole. it's a feature"], epi=None),
    dict(task="harvesting my wheat", where="right in the middle of the farm", react="gasp", react_lines=["not the farm", "the wheat!"],
         boom=["it blew up the whole middle of the farm", "BOOM. wheat everywhere", "it went off and took half the crops"],
         after=["seeds raining down on me", "i was covered in dirt and seeds", "i was just standing there in the dust"],
         result=["i survived with like two hearts", "lived. barely. two hearts left", "i lived, somehow"],
         react2="sad", react2_lines=["your farm though", "rip the wheat", "all that wheat"],
         result2=["the water got loose and flooded everything", "the water spread and washed the rest away", "water everywhere, every crop popped off"],
         lesson=["torches round the farm now. lots", "lit the farm up like daytime after", "fence and torches. never again"],
         epi=["another creeper. same farm. same spot", "a second creeper walks in through the hole in the fence", "ANOTHER hiss, same farm"]),
    dict(task="sorting my chests", where="inside my house", react="gasp", react_lines=["INSIDE?", "how did it get in"],
         boom=["it blew up my whole storage wall", "BOOM. chests everywhere", "it went off right next to my chests"],
         after=["items flying everywhere", "stuff raining down like confetti", "cobble and seeds everywhere"],
         result=["i died. respawned in the bed next to the hole", "it killed me. woke up in my bed, looking at the hole",
                 "i died and respawned right next to the crater"],
         react2="laugh", react2_lines=["respawned at the scene lol", "front row seat", "lmao the bed survived"],
         result2=["picked up most of it. lost a few stacks", "i got most of my stuff back", "spent an hour picking stuff up"],
         lesson=["i'd left the door open. my fault", "door stays shut now. always", "always close the door"], epi=None),
    dict(task="fishing at the pond", where="right on the dock with me", react="gasp", react_lines=["while fishing?", "fishing is supposed to be chill"],
         boom=["BOOM. me and the dock, gone", "it went off and blew the dock to bits", "it exploded and i landed in the water"],
         after=["and my rod flew off somewhere", "i lost my fishing rod", "the rod went flying"],
         result=["i surfaced and the dock was just gone", "came up and there was no dock", "climbed out and there's a crater by the pond"],
         react2="laugh", react2_lines=["the dock lol", "rip dock", "free pond expansion"],
         result2=["the pond's twice as big now", "made the pond bigger at least", "now the pond has a weird bite out of it"],
         lesson=["the fish are happier, i guess", "honestly the pond looks better", "i built the dock back with stone"],
         epi=["i'm fishing again and i hear the hiss", "same pond. same hiss", "i cast my line and tsssss"]),
    dict(task="chopping a big oak tree", where="behind the tree i was cutting", react="gasp", react_lines=["hiding behind the tree", "sneaky"],
         boom=["BOOM. me and half the tree went flying", "it blew up and took the tree with it", "it went off and the tree just vanished"],
         after=["logs everywhere", "leaves raining down", "i was covered in leaves"],
         result=["i landed with one heart", "one heart left. one", "lived on one heart"],
         react2="relief", react2_lines=["one heart lol", "lucky", "so close"],
         result2=["but i got all the logs at least", "upside: free logs", "the tree was chopped for me lol"],
         lesson=["creepers are free lumberjacks i guess", "efficient, honestly", "i look behind trees now"], epi=None),
    dict(task="building a wall round my place", where="on the other side of the wall", react="gasp", react_lines=["the wall was supposed to stop that", "irony"],
         boom=["it blew a hole right through the wall", "BOOM. hole in the wall, me on the ground", "the wall took most of it"],
         after=["cobble everywhere", "dust everywhere", "i couldn't see a thing"],
         result=["the wall saved me though. i lived", "i lived because of the wall", "the wall did its job. kind of"],
         react2="laugh", react2_lines=["the wall worked lol", "half a win", "good wall"],
         result2=["but now there's a creeper-shaped hole", "so i had to build that bit again", "now i fix that same hole every week"],
         lesson=["made it two blocks thick", "thicker walls now", "stone bricks this time"],
         epi=["another creeper blows up the same spot", "a creeper hits the patch. same spot", "the same bit of wall, boom again"]),
    dict(task="lighting up a cave", where="right next to me in the dark", react="gasp", react_lines=["in the dark", "of course in the dark"],
         boom=["BOOM. and my torches all popped off", "it blew up and the cave went dark", "it went off and i was in the dark"],
         after=["couldn't see anything", "pitch black", "total darkness"],
         result=["i died in the dark. great", "it killed me in the dark", "died. respawned at home"],
         react2="sad", react2_lines=["rip", "worst way to go", "and your stuff?"],
         result2=["walked back and found my stuff by its glow", "went back and found my things lying in the dark",
                  "found my stuff in the dark, glowing a bit"],
         lesson=["torches every five blocks now", "i light up behind me as well now", "always light behind you"], epi=None),
], creeper_sneak)


# --- A long fall.
def big_fall(n, v):
    p1 = P(
        A(f"you know {v.place}?", f"ok so remember {v.place}?", f"so there's {v.place}, right"),
        B(None, "yeah?", "uh huh", "go on", "the tall one?", "yeah, why"),
        A(f"i was up there {v.why}", f"i went up there {v.why}", f"climbed all the way up {v.why}"),
        B("ask", "uh oh", "i see where this goes", "heights are bad"),
        A("and i step back. just one step", "then i take one step backwards", "and i forget where the edge is"),
        B("gasp", "NO", "not backwards", "oh no oh no"),
    )
    p2 = P(
        A(f"and i'm falling. {v.height}", f"next thing i know, i'm falling. {v.height}", f"over the edge i go. {v.height}"),
        B("gasp", f"{v.height}??", "you're dead", "that's a lot of blocks"),
        A(*v.try_),
        B(v.try_react, *v.try_lines),
        A(*v.land, chance=0.85),
    )
    p3 = P(
        A(*v.result),
        B(v.result_react, *v.result_lines),
        A(*v.after),
        B("done", "never stand near edges", "heights man", "that's a wild one"),
        A(*v.lesson, chance=0.6),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A("and then. the part i don't tell people", "and then, this is the bad bit", "but here's the embarrassing part"),
            B("laugh", "tell me", "oh no there's more", "i need to hear this"),
            A(*v.epi),
            B("laugh", "LMAO", "no", "that's the best part"),
            A("i never went back up there", "i built a fence round the edge after", "my friend still teases me", chance=0.6),
        )
    tell("fall_" + n, [p1, p2, p3, p4], ["danger", "death", "places"], "the fall", leads=["danger", "places", "exploring"],
         end_leads=["danger", "jokes", "death"])


each([
    dict(place="that big cliff by the sea", why="to look at the view", height="like forty blocks",
         try_=["pulled out my water bucket mid air", "water bucket out, aim, pray", "i grab my water bucket on the way down"],
         try_react="ask", try_lines=["the bucket clutch!", "did it work", "come on"],
         land=["timed it perfect. splash", "and i put the water down right before the ground", "and the water lands right under me"],
         result=["landed in the water. not one heart lost", "walked away like nothing happened", "zero damage. none"],
         result_react="wow", result_lines=["MLG water bucket", "no way you hit that", "that's the coolest thing ever"],
         after=["picked the water back up and walked off", "scooped up the water, cool as anything", "i just picked the water back up and left"],
         lesson=["i've never hit one since", "never managed it again", "it was pure luck honestly"], epi=None),
    dict(place="my tall cobble tower", why="to fix a torch on top", height="sixty blocks",
         try_=["no bucket. no water. nothing", "i had nothing. not even a block", "i flailed. that's all i could do"],
         try_react="gasp", try_lines=["oh no", "you're done", "rip"],
         land=["and landed right on my own front door", "and hit the ground by my door", "and hit the dirt right outside"],
         result=["splat. respawned in my bed upstairs", "died. woke up in bed in the same tower", "dead. respawned at the top of the tower"],
         result_react="laugh", result_lines=["respawned at the top lol", "the tower won", "full circle"],
         after=["so i had to walk down sixty blocks of stairs", "then i had to walk all the way down again", "had to climb down the ladder, slowly"],
         lesson=["fixed the torch from a ladder after", "i put railings up there now", "railings, everyone. railings"],
         epi=["i fell off again fixing the railing", "fell again putting up the railings", "i walked down and tripped into my own well"]),
    dict(place="that huge ravine by the forest", why="to look for ore in the walls", height="all the way to the bottom",
         try_=["tried to put a block under me", "spammed place block, nothing", "tried to grab the wall with a block"],
         try_react="gasp", try_lines=["that never works", "no no no", "please"],
         land=["and landed on a tiny bit of water at the bottom", "hit a one block puddle at the bottom", "fell right into a little puddle"],
         result=["the puddle saved me. one block of water", "lived because of one block of water", "one tiny puddle. survived"],
         result_react="wow", result_lines=["the puddle!!", "what are the odds", "lucky puddle"],
         after=["then i looked up and saw iron everywhere", "and the walls down there were full of iron", "the bottom had iron all over the walls"],
         lesson=["best fall i ever had", "falling paid off for once", "i call it the lucky puddle now"], epi=None),
    dict(place="the snowy mountain past the river", why="because i wanted to see the top", height="way down the side",
         try_=["no bucket, i'm just dropping", "i had nothing to stop it", "i'm falling and thinking: that's it"],
         try_react="gasp", try_lines=["no", "oh no", "this is it"],
         land=["and i fall right into powder snow", "plop, right into a pile of powder snow", "landed in a big drift of powder snow"],
         result=["the snow caught me. no damage. but i'm sinking", "no fall damage, but i'm stuck in the snow freezing",
                 "survived the fall, now i'm freezing in the snow"],
         result_react="gasp", result_lines=["freezing??", "out of the frying pan", "climb out!"],
         after=["climbed out shaking, one heart left", "dug my way out right before i froze", "got out just before i froze solid"],
         lesson=["leather boots now. always", "never climbing a mountain again", "i wear leather boots everywhere now"],
         epi=["i walked down and fell in more powder snow", "on the way home i fell into another drift", "i stepped out and sank into another one"]),
    dict(place="the giant oak by my house", why="to cut the top logs", height="like twenty blocks",
         try_=["tried to grab the leaves", "tried to land on the leaves", "hoped the leaves would catch me"],
         try_react="laugh", try_lines=["leaves don't work like that", "lol", "you're not a squirrel"],
         land=["and went straight through them onto the ground", "they did nothing. ground", "fell through every leaf"],
         result=["broke both legs. well, three hearts left", "three hearts left. ouch", "i lived on three hearts"],
         result_react="relief", result_lines=["you lived though", "could be worse", "lucky"],
         after=["and the top logs are still up there", "never got those top logs", "the top of that tree is still floating there"],
         lesson=["it's a floating tree now. it's fine", "i left them. they're decoration now", "i call it art"], epi=None),
    dict(place="the big waterfall in the mountains", why="to find where the water comes from", height="the whole height of the waterfall",
         try_=["tried to swim into the waterfall", "i reached for the waterfall", "swung toward the falling water"],
         try_react="ask", try_lines=["did you make it", "come on", "the waterfall!"],
         land=["grabbed the water and slid all the way down", "caught the waterfall and rode it down", "hit the water and slid down slowly"],
         result=["landed at the bottom. totally fine", "rode it all the way down, no damage", "slid down and walked away"],
         result_react="wow", result_lines=["you rode a waterfall", "that's so cool", "water slide"],
         after=["so i climbed back up to do it again", "then i did it on purpose like five more times", "i went back up and did it again"],
         lesson=["it's my favourite way down now", "best water slide in the world", "i go there just to slide down"],
         epi=["the sixth time i missed the water", "the last time i missed the water completely", "and the last time i jumped, i missed it"]),
], big_fall)


# --- Drowning (or nearly).
def drowning(n, v):
    p1 = P(
        A(f"so i dove down to {v.place}", f"there's {v.place} out past the beach, so i went down", f"i went swimming down to {v.place}"),
        B("ask", "underwater?", "brave", "i hate the ocean", "go on"),
        A(f"wanted {v.goal}", f"i was after {v.goal}", f"heard there was {v.goal} down there"),
        B(None, "worth it?", "and?", "risky", "nice, did you get it", "ooh"),
        A("so i'm way down there and my bubbles start popping", "then my air bubbles start going", "and i look up and my air is running out"),
        B("gasp", "swim up!", "oh no", "the bubbles", "go up go up"),
    )
    p2 = P(
        A(f"but {v.blocker}", f"except {v.blocker}", f"and of course {v.blocker}"),
        B("gasp", "no", "this is a nightmare", "the ocean hates you"),
        A(*v.try_),
        B(v.try_react, *v.try_lines),
        A(*v.close, chance=0.8),
    )
    p3 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.after),
        B("done", "the ocean is scary", "that's why i stay on land", "never going in deep water"),
        A(*v.lesson, chance=0.6),
    )
    tell("drown_" + n, [p1, p2, p3], ["ocean", "danger", "death"], "the dive", leads=["ocean", "danger", "structures"],
         end_leads=["ocean", "danger", "death"])


each([
    dict(place="an ocean ruin", goal="the chest in the middle", blocker="a drowned was between me and the surface",
         try_=["so i swam round it, slow as anything", "i tried to go round it", "so i swim sideways trying to dodge it"],
         try_react="ask", try_lines=["did it see you", "and?", "go go"],
         close=["it saw me and followed", "it spotted me and came after me", "it turned and came straight for me"],
         end=["drowned. ironically, by a drowned", "i drowned right there. with the drowned watching", "ran out of air two blocks from the top"],
         end_react="sad", end_lines=["drowned by a drowned", "noooo", "so close"],
         after=["and it picked up my sword and kept it", "the drowned picked up my sword", "and now that drowned has my sword"],
         lesson=["it's still down there with my sword", "i see it sometimes. holding my sword", "one day i'll get it back"]),
    dict(place="a shipwreck", goal="the treasure chest at the back", blocker="the ship was upside down and i got lost inside",
         try_=["i broke the planks to make a door", "started chopping through the hull", "i punched through the wood looking for air"],
         try_react="ask", try_lines=["smart", "did it work", "quick"],
         close=["and found a pocket of air under the deck", "and there was a little air pocket", "found air trapped under the floor"],
         end=["breathed in that air pocket and made it", "that air pocket saved my life", "gulped some air and swam out alive"],
         end_react="relief", end_lines=["the air pocket!", "lucky", "phew"],
         after=["and i still got the chest. a map and some gold", "got the chest anyway, gold and a map", "grabbed the chest on the way out"],
         lesson=["i put doors down now, for air", "always bring doors underwater", "doors give you air, remember that"]),
    dict(place="a flooded cave", goal="the iron i saw through the water", blocker="the way back was further than i thought",
         try_=["i swam as fast as i could", "i kicked like crazy for the exit", "swam back the way i came"],
         try_react="gasp", try_lines=["too far", "you won't make it", "come on"],
         close=["two hearts. one heart.", "my hearts started going", "i was down to my last heart"],
         end=["popped up into a dry tunnel just in time", "found a dry bit with half a heart", "broke the surface on half a heart"],
         end_react="relief", end_lines=["half a heart", "phew", "how"],
         after=["and the iron was right there in the dry tunnel", "and turns out the iron was in the dry bit anyway", "the iron was in the dry tunnel the whole time"],
         lesson=["never swimming in caves again", "i walk the long way now", "dry tunnels only"]),
    dict(place="an underwater ravine", goal="a trident a drowned was holding", blocker="the drowned threw the trident at me",
         try_=["it hit me and i lost half my hearts", "the trident took four hearts", "i got stabbed, from far away"],
         try_react="gasp", try_lines=["ouch", "tridents hurt", "get out of there"],
         close=["and then my air ran out on top of it", "and now i'm out of air too", "and my air hit zero"],
         end=["drowned and stabbed. a bad day", "died. drowned, stabbed, sad", "i didn't make it"],
         end_react="sad", end_lines=["rip", "worst combo", "noooo"],
         after=["and it didn't even drop the trident later", "killed it the next day, no trident", "got revenge later. still no trident"],
         lesson=["tridents are a myth", "i'm done with tridents", "i'll never get a trident"]),
    dict(place="a big kelp forest", goal="kelp for a farm", blocker="i got turned around in the kelp",
         try_=["everything looked the same, just green", "swam up but kept hitting kelp", "i couldn't see which way was up"],
         try_react="creepy", try_lines=["kelp is creepy", "sounds scary", "nope"],
         close=["and then a dolphin swims right past me", "and this dolphin shows up", "then something swims past. a dolphin"],
         end=["i followed it and it led me straight up", "followed the dolphin up to the air", "swam after it and broke the surface"],
         end_react="aw", end_lines=["the dolphin saved you", "aww", "dolphin hero"],
         after=["i fed it a fish to say thanks", "gave it a cod as a thank you", "threw it a fish, it did a flip"],
         lesson=["i love dolphins now", "dolphins are the best", "i always carry fish for them now"]),
    dict(place="a deep part of the river", goal="clay for bricks", blocker="i'd dug so deep i was under a ledge",
         try_=["i swam up and hit my head on the ledge", "swam up, bonk, stone", "i went up and hit solid stone"],
         try_react="gasp", try_lines=["no", "trapped", "oh no"],
         close=["so i dug up through the ledge with my shovel", "started digging straight up through it", "dug up like a mole"],
         end=["popped out with half a heart", "made it with half a heart, shovel first", "broke out into air, barely"],
         end_react="relief", end_lines=["close", "phew", "nice dig"],
         after=["but i got the clay. a whole stack", "and i kept all the clay", "the clay made it"],
         lesson=["my house has a brick chimney now. worth it", "the bricks are in my house now", "i look at those bricks and remember"]),
    dict(place="a sunken ruin by a coral reef", goal="the chest by the coral", blocker="i stopped to look at the coral too long",
         try_=["it was so pretty i forgot to breathe", "i just stared at the colours", "i was looking at fish, not my air"],
         try_react="laugh", try_lines=["lol", "distracted", "worth it?"],
         close=["then the bubbles were gone", "and then: no bubbles", "suddenly i'm taking damage"],
         end=["made it up with one heart. totally worth it", "one heart. but i saw a reef", "surfaced on one heart, smiling"],
         end_react="relief", end_lines=["one heart lol", "you got lucky", "worth it?"],
         after=["went back with a water breathing potion later", "came back later with water breathing", "next time i brought a potion"],
         lesson=["i go there to look at fish now", "best place to sit and look at fish", "the reef's my favourite place"]),
], drowning)


# --- The warden.
def warden(n, v):
    p1 = P(
        A("so i went down to the deep dark", "ok so, i found an ancient city", f"went way down past the deepslate {v.why}",
          "you know the deep dark? i went in"),
        B("gasp", "are you crazy", "the warden lives there", "nope", "you went in?"),
        A(f"sneaking the whole time, {v.why}", "crouched the whole way, not a sound", "sneaking past sculk everywhere, super slow"),
        B("ask", "go on", "this is so tense", "did you make it"),
        A(*v.trigger),
        B("gasp", "NO", "not the shrieker", "you woke it", "oh no oh no"),
    )
    p2 = P(
        A("then the darkness starts pulsing", "then everything goes dark, then darker", "and my screen starts going dark in pulses"),
        B("creepy", "the darkness thing", "it's coming", "nope nope"),
        A("and it digs up out of the floor right by me", "and it climbs up out of the ground. right there", "the warden crawls up out of the floor"),
        B("gasp", "RUN", "you're dead", "how close"),
        A("it's sniffing. i can hear it sniffing", "it's just sniffing the air", "it sniffs around, looking for me", chance=0.8),
    )
    p3 = P(
        A(*v.escape),
        B(v.esc_react, *v.esc_lines),
        A(*v.end),
        B("done", "never going down there", "warden stories are the worst", "you're crazy"),
        A(*v.lesson, chance=0.6),
    )
    tell("warden_" + n, [p1, p2, p3], ["danger", "caves", "mobs"], "the warden", leads=["danger", "caves", "structures"],
         end_leads=["danger", "mobs", "caves"], needs=["a:knows_warden"], weight=0.9)


each([
    dict(why="looking for loot chests",
         trigger=["and i step on a sculk sensor. one. step", "then my foot lands on a sculk sensor", "i stepped right on a sensor by accident"],
         escape=["i threw a snowball the other way", "threw a snowball off to the side, it went for the noise",
                 "chucked a snowball, it chased the sound"],
         esc_react="wow", esc_lines=["SNOWBALL", "big brain", "the snowball trick"],
         end=["snuck out while it was busy with the snowball", "crept away while it smashed the wall", "and i crawled out the other way"],
         lesson=["i carry snowballs everywhere now", "snowballs are my best weapon", "never go down without snowballs"]),
    dict(why="hunting for the swift sneak book",
         trigger=["i opened a chest and it creaked", "the chest lid clunked open. loud", "the chest made that opening sound"],
         escape=["i dug straight up. spammed the pickaxe", "pillared and dug up as fast as i could", "dug up through the ceiling like crazy"],
         esc_react="ask", esc_lines=["did it get you", "dig dig dig", "go"],
         end=["made it out the top with the book in my bag", "popped out and closed the hole. book and all", "got out, book safe"],
         lesson=["swift sneak was worth it. barely", "the book was worth it", "never again though"]),
    dict(why="just to look at it, honestly",
         trigger=["an arrow from a skeleton hit the wall near me", "a skeleton shot at me and the arrow hit sculk", "something shot an arrow and it clattered"],
         escape=["i ran. didn't sneak, just ran", "i sprinted. forget sneaking", "i ran blind into the dark"],
         esc_react="gasp", esc_lines=["it's faster than you", "no", "don't run"],
         end=["it caught me in like three seconds. one hit", "it got me. one smack. dead", "it hit me once and i was gone"],
         lesson=["respawned at home and stayed there", "never going back. ever", "my stuff is still down there. it can have it"]),
    dict(why="for the echo shards",
         trigger=["i sneezed. well, i placed a block", "i put a block down and it heard it", "placed a torch. the shrieker went off"],
         escape=["i put wool down and stood on it", "stood perfectly still on a wool block", "froze on top of wool, not moving"],
         esc_react="ask", esc_lines=["did it work", "wool is quiet", "don't move"],
         end=["it walked right past me and dug back down", "it sniffed my face and left", "it walked past me, then went back underground"],
         lesson=["longest minute of my life", "i didn't move for ages after", "my heart still hurts thinking about it"]),
    dict(why="following a friend who wanted loot",
         trigger=["my friend walked right onto a shrieker", "my friend stepped on a shrieker. loud", "and my friend set off the shrieker"],
         escape=["we split up and ran opposite ways", "we both ran different ways", "we just scattered"],
         esc_react="gasp", esc_lines=["which one did it chase", "split up is smart", "oh no"],
         end=["it chased them. i felt bad. a little", "it went for them. i got out", "they died. i made it. they're still mad"],
         lesson=["they don't invite me anymore", "we don't talk about it", "they say i ran first. i didn't. i did"]),
    dict(why="to see if the stories were true",
         trigger=["my boots clinked on the floor", "i stepped off the wool onto sculk", "i jumped. you can't jump there"],
         escape=["i hid behind a pillar of wool", "i hid round a corner and waited", "ducked behind a wall, totally still"],
         esc_react="creepy", esc_lines=["i'd be shaking", "so tense", "don't breathe"],
         end=["it sniffed for ages, then went back under", "it gave up and dug back down", "after forever it sank back into the floor"],
         lesson=["the stories are true", "the stories are real, don't go", "everything they say is true"]),
], warden)


# --- A night out that went wrong.
def night_ambush(n, v):
    p1 = P(
        A(f"i was walking home through {v.place} at night", f"so it's night and i'm in {v.place}", f"got caught out in {v.place} after dark"),
        B("ask", "at night?", "bad idea", "uh oh", "go on"),
        A("no torches, no bed, nothing", "no bed on me. stupid", "i'd used all my torches already"),
        B("gasp", "no torches??", "rookie", "you're doomed"),
        A(f"and i hear {v.sound}", f"then out of nowhere, {v.sound}", f"and then. {v.sound}"),
        B("gasp", "oh no", "i know that sound", "run"),
    )
    p2 = P(
        A(*v.mob),
        B("gasp", *v.mob_react),
        A(*v.fight),
        B(v.fight_react, *v.fight_lines),
        A(*v.fight2, chance=0.8),
    )
    p3 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.after),
        B("done", "never walk home at night", "nights are rough", "that's a night"),
        A(*v.lesson, chance=0.6),
    )
    tell("ambush_" + n, [p1, p2, p3], ["night", "mobs", "fighting"], "that night", leads=["night", "mobs", "danger"],
         end_leads=["night", "fighting", "mobs"])


each([
    dict(place="the forest", sound="spider clicking above me",
         mob=["a spider jockey. skeleton riding a spider", "skeleton on a spider. a jockey", "spider with a skeleton on its back"],
         mob_react=["a jockey??", "those are so rare", "worst combo"],
         fight=["the skeleton's shooting, the spider's jumping", "arrows from the top, spider bites from the bottom",
                "getting shot and bitten at once"],
         fight_react="gasp", fight_lines=["how do you even fight that", "two mobs in one", "nope"],
         fight2=["i swung at the spider until it dropped", "killed the spider first, skeleton fell off", "i took out the spider, the skeleton landed"],
         end=["then killed the skeleton with two hearts left", "finished the skeleton on two hearts", "two hearts left, but they're both dead"],
         end_react="wow", end_lines=["you beat a jockey", "respect", "huge"], after=["got a bow and string out of it", "bow and string, nice", "got its bow"],
         lesson=["i still use that bow", "that bow's on my wall now", "trophy bow"]),
    dict(place="the plains", sound="a bunch of zombies groaning",
         mob=["five zombies. one had full iron armor", "a group of zombies, and one in iron armor", "zombies, and one wearing iron"],
         mob_react=["armored zombie??", "they're tanks", "run"],
         fight=["i ran, but they chased me across the plains", "i sprinted and they followed", "i ran and they kept coming"],
         fight_react="ask", fight_lines=["where'd you go", "run run", "and?"],
         fight2=["found a village and ran in", "led them right into a village", "ran into a village, the golem woke up"],
         end=["the iron golem smashed every one of them", "the golem threw them all in the air", "the golem handled it. all of them"],
         end_react="laugh", end_lines=["golem carry", "lol", "the golem's a legend"], after=["and the iron armor zombie dropped its helmet", "got an iron helmet out of it", "the armored one dropped its helmet"],
         lesson=["i give that golem flowers now", "i visit the golem sometimes", "best golem ever"]),
    dict(place="a dark forest", sound="a bow string",
         mob=["three skeletons on the ridge above me", "skeletons, three of them, shooting down at me", "a row of skeletons on the hill"],
         mob_react=["three??", "skeletons are the worst", "take cover"],
         fight=["arrows everywhere. i hid behind a tree", "i got behind a big oak", "dove behind a tree"],
         fight_react="ask", fight_lines=["did they come down", "smart", "and?"],
         fight2=["they shot each other by accident", "one shot another and they started fighting", "they started shooting each other"],
         end=["they killed each other while i watched", "they took each other out", "i just watched them fight"],
         end_react="laugh", end_lines=["lmao", "skeleton civil war", "free kills"], after=["i walked up and took the bones", "collected the bones after", "free bones and arrows"],
         lesson=["skeletons are dumb", "they're dumber than they look", "best night ever honestly"]),
    dict(place="a swamp", sound="a witch laughing",
         mob=["a witch, and zombies all round her", "a witch with a bunch of zombies", "witch plus zombies. a party"],
         mob_react=["witches are worse than anything", "potions and zombies", "nope"],
         fight=["she threw a slowness potion at me", "she slowed me down with a potion", "i got splashed with slowness"],
         fight_react="gasp", fight_lines=["slowness with zombies around", "no", "you're stuck"],
         fight2=["the zombies caught up because i was so slow", "the zombies got me, i was crawling", "i couldn't outrun anything"],
         end=["died in a swamp. slowly", "i died crawling through the swamp", "died, very slowly"],
         end_react="sad", end_lines=["worst death", "rip", "swamps are evil"], after=["respawned at home, my stuff was in the swamp", "woke up at home with nothing", "went back the next day for my stuff"],
         lesson=["never going in swamps at night", "i hate swamps", "swamps can stay swamps"]),
    dict(place="the hills", sound="a creeper hiss and a skeleton at once",
         mob=["a creeper in front, a skeleton behind", "creeper one side, skeleton the other", "skeleton shooting, creeper walking up"],
         mob_react=["both??", "the combo", "uh oh"],
         fight=["i dodged and the arrow hit the creeper", "the skeleton's arrow hit the creeper", "the skeleton shot the creeper by mistake"],
         fight_react="ask", fight_lines=["and?", "what happened", "go on"],
         fight2=["the creeper blew up right next to the skeleton", "the creeper went off right by the skeleton", "boom, took the skeleton out"],
         end=["and the creeper dropped a music disc", "and i found a music disc on the ground", "music disc. a real one"],
         end_react="wow", end_lines=["a music disc!", "rare", "no way"], after=["i put it in a jukebox at home", "it's in my jukebox now", "play it every night"],
         lesson=["best night of my life", "worth it", "i love that song"]),
    dict(place="the taiga", sound="wolves howling",
         mob=["a pack of wolves. and i'd hit one by mistake", "wolves, and i'd swung at one by accident", "i hit a wolf, the whole pack turned"],
         mob_react=["never hit a wolf", "oh no", "the pack"],
         fight=["they all turned red-eyed at once", "all of them, angry, coming at me", "six angry wolves"],
         fight_react="gasp", fight_lines=["run", "that's a death sentence", "no"],
         fight2=["i jumped in a river and swam for it", "dove into the river", "i swam across a river to escape"],
         end=["they gave up at the water. i lived", "they stopped at the river bank", "they couldn't follow me in the water"],
         end_react="relief", end_lines=["phew", "smart", "the river saved you"], after=["slept on the other side in a hole", "dug a hole on the other side and hid", "spent the night in a hole"],
         lesson=["i say sorry to every wolf i see now", "i'm very careful near wolves now", "i don't swing near wolves anymore"]),
], night_ambush)


# --- Phantoms, after not sleeping.
def phantoms(n, v):
    p1 = P(
        A(f"so i didn't sleep for like {v.nights} nights", f"i went {v.nights} nights without sleeping", f"i skipped sleep for {v.nights} nights straight"),
        B(None, "why", "that's a bad idea", "phantoms", "uh oh", "you know what happens"),
        A(f"i was busy {v.busy}", f"too busy {v.busy}", f"i was {v.busy}, didn't want to stop"),
        B("ask", "and?", "go on", "you know what comes next"),
        A("and then i hear this screech above me", "then something screeches up in the sky", "and there's this shriek from the sky"),
        B("gasp", "phantoms", "told you", "oh no"),
    )
    p2 = P(
        A(f"phantoms. {v.count} of them, swooping", f"{v.count} phantoms, diving at me", f"there were {v.count} of them circling"),
        B("gasp", "they're so annoying", "you can't even see them coming", "nope"),
        A(*v.fight),
        B(v.react, *v.react_lines),
        A(*v.fight2, chance=0.8),
    )
    p3 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A("now i sleep every night. every night", "i go to bed the second it's dark now", "now i sleep every single night"),
        B("done", "sleep is important", "bed is life", "good call"),
        A(*v.lesson, chance=0.6),
    )
    tell("phantom_" + n, [p1, p2, p3], ["sleep", "mobs", "night"], "the phantoms", leads=["sleep", "night", "mobs"],
         end_leads=["sleep", "mobs", "jokes"])


each([
    dict(nights="four", busy="building my tower", count="three",
         fight=["one knocked me right off my tower", "one hit me and i fell off the tower", "they kept hitting me off the scaffolding"],
         react="gasp", react_lines=["off the tower??", "oh no", "how high"],
         fight2=["fell into my own scaffolding though", "landed on scaffolding", "the scaffolding caught me"],
         end=["went straight to bed after that", "climbed into bed and the phantoms left", "slept right there, they stopped"],
         end_react="relief", end_lines=["phew", "the bed fixes it", "lucky"], lesson=["tower got finished. after a nap", "i finished the tower well rested", "naps are underrated"]),
    dict(nights="six", busy="mining", count="five",
         fight=["i came up to the surface and they were waiting", "the second i came out of the cave, there they were",
                "walked out of the mine and they dove at me"],
         react="laugh", react_lines=["they were waiting for you lol", "a welcome party", "lol"],
         fight2=["i went straight back into the cave", "ran back underground", "i hid back in the mine"],
         end=["stayed underground till i found a bed", "waited it out underground", "slept in the mine with a spare bed"],
         end_react="laugh", end_lines=["mole life", "lol", "smart"], lesson=["always keep a bed in the mine now", "spare bed in the mine. always", "i keep a bed down there now"]),
    dict(nights="five", busy="exploring", count="four",
         fight=["i shot at them with my bow and missed every time", "tried my bow, they're too fast", "i shot arrows. all missed"],
         react="laugh", react_lines=["they're hard to hit", "aim lol", "they dodge"],
         fight2=["so i stood under a tree to hide", "i hid under a big tree", "i ducked under the leaves"],
         end=["they couldn't get me under the tree. safe", "the tree saved me", "they gave up when i hid"],
         end_react="relief", end_lines=["trees are good for something", "phew", "nice"], lesson=["i got phantom membrane from one at least", "got one membrane. fixed my elytra... i wish", "got membrane for potions at least"]),
    dict(nights="three", busy="fishing all night", count="two",
         fight=["they dove at me while i fished", "they came while i was fishing", "they bugged me all night on the dock"],
         react="laugh", react_lines=["fishing and phantoms", "lol", "can't fish in peace"],
         fight2=["one knocked me in the water", "i fell off the dock", "got knocked into the pond"],
         end=["i just slept on the dock bed after", "put a bed on the dock and slept", "slept right there by the pond"],
         end_react="laugh", end_lines=["bed on a dock lol", "cozy", "nice"], lesson=["the dock bed is still there", "best spot to sleep honestly", "i sleep there a lot now"]),
    dict(nights="seven", busy="farming", count="six",
         fight=["they ate me alive. well, nearly", "they swooped till i had one heart", "they took me down to a heart"],
         react="gasp", react_lines=["six??", "you're lucky", "nope"],
         fight2=["i crawled into a hole in my farm", "dug a hole in the field and hid", "jumped into the water channel"],
         end=["slept in the hole. woke up and they were gone", "slept in a hole like a rabbit", "made a hole, put a bed, slept"],
         end_react="laugh", end_lines=["rabbit life", "lol", "the hole saves the day again"], lesson=["seven nights was too many", "never again, seven is crazy", "i learned my lesson"]),
], phantoms)

# =====================================================================================================================
# LUCKY FINDS
# =====================================================================================================================

# --- Diamonds in a silly place.
DIA_PROBLEM = {
    "stone": (["only problem: all i had was a stone pickaxe", "but all i had on me was a stone pickaxe", "and me with just a stone pickaxe"],
              "gasp", ["stone can't mine diamonds", "oh no", "don't do it", "you know what stone does to diamonds"],
              ["i was too excited to think", "i didn't think. i just swung", "my brain switched off"]),
    "lava": (["but there was lava right under them", "and lava was sitting right below the vein", "one wrong move and they'd drop in lava"],
             "gasp", ["careful", "don't drop them in", "lava always guards the good stuff"],
             ["so i went really slow, block by block", "i put blocks under them first", "i mined from the side, super careful"]),
    "night": (["but the sun was going down fast", "and it was getting dark", "and night was coming on"],
              "ask", ["mine fast", "and?", "you didn't leave them"],
              ["so i mined them in the dark with zombies groaning", "mined in the dark as fast as i could", "i mined with zombies all round me"]),
    "friend": (["but my friend saw them the same second i did", "my friend was right next to me and saw them too", "we both saw them at the same time"],
               "laugh", ["uh oh", "drama", "who got them"],
               ["we just stared at each other", "nobody moved. for ages", "a full staring contest"]),
    "skeleton": (["and a skeleton was shooting me from across the cave", "but a skeleton kept shooting me the whole time", "with a skeleton plinking arrows at me"],
                 "gasp", ["skeletons ruin everything", "mine fast", "ignore it"],
                 ["so i mined with arrows sticking out my back", "i just took the hits and kept mining", "tanked every arrow, kept swinging"]),
}
DIA_END = {
    "stone": (["mined them anyway. they just broke into nothing", "they popped. nothing dropped. nothing", "broke every one. not one diamond came out"],
              "laugh", ["NOOO", "you didn't", "the stone pick lol", "you destroyed diamonds"],
              ["{c} diamonds. gone. into thin air", "i stood there for a full minute", "i destroyed {c} diamonds with my own hands"],
              ["iron pick in every chest now", "i carry an iron pick everywhere now", "never leaving home without iron again"]),
    "kept": (["got every single one. {c} diamonds", "and i got them all. all {c}", "every one came out. {c} diamonds, mine"],
             "wow", ["lets go", "huge", "the best feeling", "rich"],
             ["walked home holding them like a baby", "i walked home so carefully lol", "i didn't even run, i walked home slow"],
             ["best day i ever had", "still my best find", "i'll never forget it"]),
    "lost": (["got them all, then a creeper got me on the way home", "had them all, and died to a creeper walking home",
              "got them, then fell in a ravine on the way back"],
             "sad", ["NOOO", "on the way home??", "that's evil"],
             ["{c} diamonds, gone, never found the spot again", "never found my stuff again", "they despawned before i got back"],
             ["i still dream about those diamonds", "i'm not over it", "it still hurts honestly"]),
    "shared": (["so we split them. half each", "we just shared them, half and half", "we split them right down the middle"],
               "aw", ["that's fair", "good friends", "wholesome"],
               ["we both made a diamond pickaxe that day", "made matching diamond picks", "we each made a pick and went mining together"],
               ["best mining buddy i ever had", "we still mine together", "still the best day"]),
}
DIA_EPI = {
    "pick": "made a diamond pickaxe and named it lucky",
    "table": "made an enchanting table with two of them",
    "chest": "they're still in a chest. i just look at them",
    "jukebox": "made a jukebox. one diamond, best tunes",
}


def diamond_spot(n, v):
    prob, prob_react, prob_lines, prob2 = DIA_PROBLEM[v.prob]
    end, end_react, end_lines, end2, lesson = DIA_END[v.end]
    c = v.count
    p1 = P(
        A(f"funniest place i ever found diamonds? {v.where}", f"guess where i found diamonds once. {v.where}",
          f"i found diamonds {v.where} one time. true story"),
        B(None, "no way", "how", "wait what", "for real?", "explain"),
        A(*v.why),
        B("ask", "and?", "go on", "ok"),
        A("and i see a little white and blue speck", "then i see it. blue dots in the stone", "and there's this sparkle in the wall"),
        B(None, "DIAMONDS", "no way", "diamonds??", "it's diamonds isn't it", "the sparkle!"),
    )
    p2 = P(
        A(f"diamonds. {c} of them", f"a whole vein of them. {c}", f"{c} diamonds, just sitting there"),
        B("wow", f"{c}??", "the dream", "i'd have screamed"),
        A(*prob),
        B(prob_react, *prob_lines),
        A(*prob2, chance=0.8),
    )
    p3 = P(
        A(*[x.replace("{c}", c) for x in end]),
        B(end_react, *end_lines),
        A(*[x.replace("{c}", c) for x in end2]),
        B("done", "what a find", "diamonds man", "wild"),
        A(*lesson, chance=0.6),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A("and you wanna know what i did with them?", "and you know what i made?", "guess what i did with them"),
            B(None, "what", "tell me", "a sword?", "what did you make", "go on"),
            A(DIA_EPI[v.epi], {"pick": "diamond pickaxe. named it lucky", "table": "enchanting table. two diamonds in it",
                               "chest": "nothing. they're in a chest. i look at them", "jukebox": "a jukebox. worth it"}[v.epi]),
            B("laugh" if v.epi in ("chest", "jukebox") else "wow", "of course", "classic", "perfect"),
            A("still makes me smile", "no regrets", "best use of diamonds", chance=0.6),
        )
    tell("diamonds_" + n, [p1, p2, p3, p4], ["diamonds", "mining", "brag"], "the diamonds", leads=["diamonds", "mining", "ore"],
         end_leads=["diamonds", "mining", "brag"])


each([
    dict(where="at the bottom of my well", why=["i was digging my well deeper", "my well kept running dry so i dug down"],
         count="four", prob="stone", end="stone", epi=None),
    dict(where="right under my own house", why=["i was digging a basement for chests", "wanted a basement, started digging"],
         count="six", prob="lava", end="kept", epi="table"),
    dict(where="in a creeper crater", why=["a creeper blew a hole by my farm", "a creeper went off by my fence, made a hole"],
         count="two", prob="night", end="kept", epi="jukebox"),
    dict(where="behind a waterfall", why=["i walked behind a waterfall just to look", "wanted to see what was behind the waterfall"],
         count="eight", prob="skeleton", end="lost", epi=None),
    dict(where="in a ravine wall i walked past every day", why=["i'd walked past that wall a hundred times", "it was on my way to work, every day"],
         count="five", prob="friend", end="shared", epi="pick"),
    dict(where="in the wall of my own mine", why=["i was making my mine wider", "i was fixing the walls in my mine"],
         count="three", prob="stone", end="stone", epi=None),
    dict(where="under a tree i chopped", why=["i dug out a stump i didn't like", "i was pulling up an old tree stump"],
         count="seven", prob="lava", end="lost", epi=None),
    dict(where="in a cave right by spawn", why=["i was looking for coal, nothing else", "i only went down for coal"],
         count="four", prob="night", end="kept", epi="chest"),
], diamond_spot)


# --- A buried treasure map.
TREASURE_TRIP = {
    "ocean": (["sailed for ages across open water", "took a boat way out to sea", "rowed out across the ocean"],
              ["drowned popped up round the boat at night", "night came and drowned started swimming at me", "the drowned came out at night"],
              "gasp", ["drowned at night, nope", "row faster", "oh no"]),
    "jungle": (["walked through a whole jungle", "hacked my way through jungle for hours", "went through the thickest jungle ever"],
               ["got lost in the leaves twice", "kept getting stuck in vines and leaves", "i couldn't see the sky for an hour"],
               "laugh", ["jungles are the worst", "lol", "vines everywhere"]),
    "desert": (["walked across a huge desert", "crossed a desert with no water", "went across the desert"],
               ["husks chased me the whole way", "husks everywhere, even in the day", "the husks don't burn, you know"],
               "gasp", ["husks are nasty", "no shade either", "oof"]),
    "beach": (["followed the coast for ages", "walked along the beach for a whole day", "went along the shore forever"],
              ["a thunderstorm rolled in halfway", "it started storming, lightning everywhere", "there was a big storm"],
              "gasp", ["lightning", "scary", "oh no"]),
}
TREASURE_LOOT = {
    "iron": "a heart of the sea, iron and some gold",
    "diamonds": "a heart of the sea and two diamonds",
    "tnt": "a heart of the sea and a stack of tnt",
    "emeralds": "a heart of the sea and emeralds",
    "fish": "a heart of the sea and some cooked fish",
}
TREASURE_EPI = {
    "conduit": (["made a conduit with the heart. under my dock", "used the heart to make a conduit", "made a conduit out of the heart"],
                ["i can breathe underwater by my house now", "now i can breathe underwater by my dock", "it lights up the water and i can breathe"]),
    "frame": (["put the heart in an item frame on my wall", "the heart's in an item frame above my bed", "it's on my wall in a frame"],
              ["don't even know what it does. it's pretty", "no idea what it's for. looks nice", "i just like how it looks"]),
    "gift": (["gave the heart to a friend who loves the ocean", "gave it away to a friend", "my friend wanted it more than me"],
             ["they made a conduit and named it after me", "they still thank me for it", "they built a whole underwater house"]),
}


def treasure(n, v):
    trip, trouble, tr_react, tr_lines = TREASURE_TRIP[v.trip]
    p1 = P(
        A(f"found a treasure map {v.source} once", f"ever followed a treasure map? i got one {v.source}", f"so i got this old map {v.source}"),
        B(None, "a treasure map?", "treasure!", "no way", "ooh", "where does it go"),
        A("it had a big red x on it", "there's an x on it, miles away", "an x, way off the edge of what i knew"),
        B("ask", "you followed it", "of course you went", "adventure"),
        A("so obviously i went", "so i packed up and went", "so i grabbed a shovel and left"),
        B(None, "of course", "go on", "with a shovel lol", "and?"),
    )
    p2 = P(
        A(*trip),
        B(None, "how long", "brave", "ok", "go on"),
        A(*trouble),
        B(tr_react, *tr_lines),
        A("but i kept going. i wanted that treasure", "kept going anyway", "nothing was stopping me", chance=0.85),
    )
    p3 = P(
        A("finally i'm standing on the x", "so i get to the spot on the map", "and i'm right on the x"),
        B("ask", "dig!", "and?", "dig dig"),
        A(f"dug down. nothing. dug again. nothing", f"dug {v.holes} holes. nothing", f"i dug like {v.holes} holes, all empty"),
        B("laugh", f"{v.holes} holes lol", "classic", "the map lied"),
        A("then i dug right in the middle of the x. thunk", "then i tried the exact middle. clunk", "then one more, dead center. chest"),
        B("wow", "THE CHEST", "finally", "lets go"),
    )
    p4 = P(
        A(f"inside: {TREASURE_LOOT[v.loot]}", f"and in it, {TREASURE_LOOT[v.loot]}", f"opened it. {TREASURE_LOOT[v.loot]}"),
        B("tnt" == v.loot and "laugh" or "wow", "a heart of the sea!", "nice", "worth the trip?", "jealous"),
        A({"iron": "the iron paid for the trip at least", "diamonds": "two diamonds. i yelled", "tnt": "tnt. in a treasure chest. ok",
           "emeralds": "the emeralds bought me a mending book", "fish": "the fish was a nice touch"}[v.loot],
          "it was worth every hole", "worth it, every block of the way"),
        B("done", "worth it", "real treasure", "treasure hunter"),
        A("carried it home like a baby", "rowed home grinning the whole way", "took it home, smiling the whole way", chance=0.7),
    )
    p5 = None
    if v.epi:
        epi, epi2 = TREASURE_EPI[v.epi]
        p5 = P(
            A("you know what i did with the heart?", "and the heart of the sea, guess what i did", "the heart, though. listen"),
            B(None, "what", "what does it even do", "what", "tell me"),
            A(*epi),
            B("aw" if v.epi == "gift" else "wow", "nice", "cool", "smart"),
            A(*epi2, chance=0.8),
        )
    tell("treasure_" + n, [p1, p2, p3, p4, p5], ["ocean", "exploring", "brag"], "the treasure", leads=["ocean", "exploring", "structures"],
         end_leads=["ocean", "brag", "exploring"], needs=["!strangers"] if p5 else None)


each([
    dict(source="in a shipwreck", trip="ocean", holes="six", loot="iron", epi="conduit"),
    dict(source="in an ocean ruin chest", trip="beach", holes="three", loot="diamonds", epi=None),
    dict(source="in a sunken ship", trip="jungle", holes="eleven", loot="tnt", epi="frame"),
    dict(source="in a shipwreck on a beach", trip="desert", holes="four", loot="emeralds", epi="gift"),
    dict(source="from a chest by the sea", trip="ocean", holes="nine", loot="fish", epi=None),
    dict(source="in an old wreck", trip="beach", holes="five", loot="diamonds", epi="conduit"),
    dict(source="in a ruin by the coast", trip="jungle", holes="seven", loot="iron", epi=None),
], treasure)


# --- An enchanted golden apple.
def golden_apple(n, v):
    p1 = P(
        A(f"i found an enchanted golden apple once. {v.place}", f"ever seen a golden apple that glows? i found one {v.place}",
          f"so, {v.place}, there's a chest, and in it"),
        B(None, "no way", "the glowing one??", "the shiny one?", "those are so rare", "go on"),
        A("the shiny purple one. a real one", "the glowing one, not the normal gold one", "the real thing. the glowing one"),
        B("wow", "those are super rare", "jackpot", "did you eat it"),
        A("i held it like it was made of glass", "i put it in my bag so carefully", "i kept checking it was still there"),
        B("laugh", "lol", "protect it", "as you should"),
    )
    if v.end == "oops":
        p2 = P(
            A("so i'm walking home with it on my hotbar", "i put it on my hotbar to keep it close", "it was on my hotbar, slot nine"),
            B("ask", "uh oh", "hotbar is risky", "i see where this goes"),
            A(f"then i got hungry and {v.oops}", f"and then i went to eat and {v.oops}", f"and i wanted a snack and {v.oops}"),
            B("gasp", "NO", "you ate it", "you didn't"),
            A("ate the whole thing. full health. for nothing", "crunch. gone. i was at full health too",
              "ate it walking through a meadow. totally safe"),
        )
        p3 = P(
            A("all the hearts and absorption, for nothing", "glowing gold for two minutes, nothing to fight",
              "i had all those extra hearts and a bunny looked at me"),
            B("laugh", "lmao", "the most expensive snack", "what a waste"),
            A("i just stood there, shining", "i was fully buffed in a flower field", "strongest i've ever been, picking flowers"),
            B("laugh", "iconic", "rip apple", "a waste and a legend"),
            A("food on slot one now. only food", "the apple never goes on the hotbar now", "i check what i eat now", chance=0.6),
        )
    elif v.end == "saved":
        p2 = P(
            A("kept it in a chest for months. for emergencies", "saved it for an emergency. months", "didn't touch it for ages"),
            B(None, "smart", "the emergency apple", "for the dragon?", "for a bad day"),
            A(f"and then the emergency came. {v.danger}", f"then one day: {v.danger}", f"finally the day came. {v.danger}"),
            B("gasp", "oh no", "the apple!", "eat it eat it"),
            A("one heart. reached for the apple", "down to one heart, apple in hand", "i'm on one heart and i grab it"),
        )
        p3 = P(
            A("crunch. full health and then some", "ate it. hearts everywhere. fire res too", "ate it and i was a tank"),
            B("relief", "clutch", "that's what it's for", "the apple came through"),
            A("won the fight, walked home glowing", "beat it, then walked home all gold", "i won. barely. thanks to the apple"),
            B("done", "saved by an apple", "worth the wait", "legend"),
            A("best thing i ever found", "that apple saved my life", "i owe that apple everything", chance=0.6),
        )
    else:
        p2 = P(
            A("and then a friend saw it in my chest", "then my friend saw it", "and someone in town spotted it"),
            B(None, "uh oh", "did they want it", "drama", "go on", "oh no"),
            A("they needed it. they were going to fight the wither", "they wanted it for a big fight", "they had a dragon fight coming up"),
            B("ask", "did you give it", "what did you do", "the dilemma"),
            A("i thought about it all night", "i didn't sleep, thinking about it", "it was the hardest choice"),
        )
        p3 = P(
            A("and i gave it to them", "then i just gave it to them", "next morning i handed it over"),
            B("aw", "that's a real friend", "wow", "big heart"),
            A("they won the fight because of it", "it saved them in the fight", "they said it was the only reason they won"),
            B("aw", "worth it then", "good friend", "it went to a good cause"),
            A("they gave me a shulker box after. fair trade", "they still owe me one apple lol", "we're best friends now", chance=0.6),
        )
    p4 = None
    if v.epi:
        p4 = P(
            A("and you know what's funny?", "and the funny thing is", "and get this"),
            B("ask", "what", "tell me"),
            A(f"i went back to that same place. {v.epi}", f"i went back there later. {v.epi}", f"same spot, a week later: {v.epi}"),
            B("laugh", "of course", "luck is weird", "lol"),
            A("i'll never understand luck", "some days you just win", "luck is weird", chance=0.6),
        )
    tell("apple_" + n, [p1, p2, p3, p4], ["food", "brag"], "the golden apple", leads=["food", "brag", "structures"],
         end_leads=["food", "jokes", "friends"], needs=getattr(v, "needs", None))


each([
    dict(place="in a mineshaft chest", end="oops", oops="clicked the wrong slot", epi=None),
    dict(place="in a ruined portal", end="saved", danger="a ravager in a raid", epi="another chest. just rotten flesh"),
    dict(place="in a desert temple", end="give", epi=None),
    dict(place="in a dungeon under my village", end="oops", oops="grabbed the apple instead of bread", epi="found a second one"),
    dict(place="in a bastion in the nether", end="saved", danger="a piglin brute had me cornered", epi=None, needs=["a:knows_nether"]),
    dict(place="in an old mineshaft", end="give", epi="another apple. a normal one"),
    dict(place="in a jungle temple", end="saved", danger="three skeletons and a creeper", epi=None),
], golden_apple)


# --- A desert temple and its tnt.
def temple(n, v):
    p1 = P(
        A(f"found a desert temple once {v.how}", f"so i'm in the desert and {v.how} there's a temple",
          f"desert temple story. found it {v.how}"),
        B(None, "the tnt one?", "ooh loot", "careful", "nice", "the orange pyramid"),
        A("broke through the floor in the middle", "dug down through the middle of the floor", "went down the hole in the middle"),
        B("ask", "the pressure plate", "careful", "go on"),
        A("and at the bottom: four chests and a pressure plate", "four chests, one pressure plate in the middle", "chests round the walls, plate in the middle"),
        B(None, "don't step on it", "DON'T", "uh oh", "the plate is the trap", "careful careful"),
    )
    p2 = P(
        A(*v.twist),
        B(v.tw_react, *v.tw_lines),
        A(*v.twist2),
        B(v.tw2_react, *v.tw2_lines),
        A(*v.twist3, chance=0.8),
    )
    p3 = P(
        A(f"and the chests? {v.loot}", f"opened the chests: {v.loot}", f"in the chests, {v.loot}"),
        B("wow" if v.good else "laugh", *v.loot_lines),
        A(*v.end),
        B("done", "temples are wild", "classic desert", "good story"),
        A(*v.lesson, chance=0.6),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A("and then, the walk home", "and the walk home was a whole thing", "and getting home was the worst part"),
            B("ask", "what now", "of course", "go on"),
            A(*v.epi),
            B("laugh", "lol", "the desert hates you", "classic"),
            A("never going to a desert again", "deserts are cursed", "i'll take a forest, thanks", chance=0.6),
        )
    tell("temple_" + n, [p1, p2, p3, p4], ["structures", "danger", "exploring"], "the temple", leads=["structures", "exploring", "biomes"],
         end_leads=["structures", "brag", "jokes"])


each([
    dict(how="by walking right into it",
         twist=["so i mine the plate first. carefully", "i break the pressure plate before anything", "i mine the plate from the side"],
         tw_react="relief", tw_lines=["smart", "pro move", "that's the way"],
         twist2=["then i dig under it. nine tnt", "and under it: nine blocks of tnt", "dug down and there's the tnt"],
         tw2_react="wow", tw2_lines=["free tnt", "nine tnt", "nice"],
         twist3=["took every block of it", "i took all the tnt", "pocketed all nine"],
         loot="gold, bones and two diamonds", good=True, loot_lines=["diamonds!", "nice haul", "two diamonds!"],
         end=["walked out rich and alive", "left with everything", "walked out like a pro"],
         lesson=["i used the tnt to make a pond lol", "blew up a hill with the tnt later", "the tnt's in a chest at home"], epi=None),
    dict(how="half buried in sand",
         twist=["and i step right on the plate", "and i walk straight onto the plate", "and my foot lands right on it"],
         tw_react="gasp", tw_lines=["NO", "run", "you're dead"],
         twist2=["click. hiss. BOOM", "click. then a lot of hissing. then boom", "the tnt goes off under me"],
         tw2_react="gasp", tw2_lines=["oh no", "rip", "all of it?"],
         twist3=["blew up me and all four chests", "me and the loot, gone", "everything gone, me included"],
         loot="nothing. they blew up", good=False, loot_lines=["lol", "rip loot", "the temple won"],
         end=["i respawned and the temple had a crater in it", "came back to a big crater", "the temple was a hole now"],
         lesson=["i look at my feet now", "always mine the plate", "watch your step"],
         epi=["i walked home and a husk followed me the whole way", "a husk chased me home", "husks chased me back across the sand"]),
    dict(how="with a friend",
         twist=["my friend jumps down first", "my friend goes down the hole before me", "my friend jumped down ahead of me"],
         tw_react="gasp", tw_lines=["onto the plate", "oh no", "no no"],
         twist2=["lands right on the plate", "right on the pressure plate", "and lands on the plate"],
         tw2_react="gasp", tw2_lines=["NO", "rip friend", "boom"],
         twist3=["i hear the boom from up top", "i'm up top, i hear it all go", "boom. i'm standing at the top, staring"],
         loot="gone. and my friend respawned at home", good=False, loot_lines=["lol your friend", "rip", "poor friend"],
         end=["they still blame me for not warning them", "they say i should have yelled", "they say i let it happen"],
         lesson=["i did not let it happen. probably", "it wasn't my fault. mostly", "i'm innocent. i think"], epi=None),
    dict(how="by falling into it",
         twist=["i fall through the roof hole", "i fell right down the middle hole", "the sand gave way and i dropped in"],
         tw_react="gasp", tw_lines=["onto the plate??", "oh no", "where did you land"],
         twist2=["and land one block from the plate", "landed right next to the plate", "landed one block off the plate"],
         tw2_react="relief", tw2_lines=["one block!", "lucky", "phew"],
         twist3=["i didn't move for a full minute", "i stood very, very still", "i did not breathe"],
         loot="a saddle, emeralds and a golden apple", good=True, loot_lines=["nice loot", "a saddle!", "lucky lucky"],
         end=["climbed out with everything", "took it all and climbed out", "got out with the lot"],
         lesson=["luckiest fall of my life", "falling works sometimes", "i fall into things a lot"],
         epi=["got lost in the dunes till dark", "walked the wrong way for an hour", "every dune looks the same. i got lost"]),
    dict(how="already dug open",
         twist=["someone had already been there", "the floor was already dug up", "someone got there first"],
         tw_react="sad", tw_lines=["oh no", "looted", "rip"],
         twist2=["chests open, tnt gone", "they took everything, even the tnt", "empty chests, no tnt"],
         tw2_react="sad", tw2_lines=["nothing left?", "the worst", "who"],
         twist3=["but one chest had one thing left", "but one chest still had something", "except one chest. one item"],
         loot="a single rotten flesh", good=False, loot_lines=["LOL", "a gift", "the rotten flesh lol"],
         end=["i took it. it's mine now", "i kept the rotten flesh", "took it home out of spite"],
         lesson=["it's in a frame on my wall", "i call it the treasure", "best story i have"], epi=None),
    dict(how="at sunset",
         twist=["i put a block on the plate first", "i covered the plate with a block", "slapped a block over the plate"],
         tw_react="relief", tw_lines=["smart", "nice", "big brain"],
         twist2=["then a husk falls down the hole behind me", "and then a husk drops in behind me", "a husk falls into the room"],
         tw2_react="gasp", tw2_lines=["oh no", "on the plate?", "uh oh"],
         twist3=["lands right next to my block. phew", "it misses the plate by one block", "it lands beside the plate"],
         loot="iron, gold and an enchanted book", good=True, loot_lines=["a book!", "nice", "loot!"],
         end=["killed the husk and took everything", "fought the husk, then looted", "beat the husk and cleaned it out"],
         lesson=["the book was unbreaking. nice", "unbreaking three, by the way", "just unbreaking, but still"],
         epi=["walked home in the dark with husks all round", "the walk home was husks and spiders", "it got dark and i had to run"]),
], temple)

# --- A dungeon with a spawner.
SPAWNER_LOOT = {
    "saddle": (["a saddle. a real saddle", "a saddle and some string", "a saddle, finally"], ["a saddle!", "ride time", "nice"]),
    "disc": (["a music disc. the green one", "a music disc and some bread", "a music disc, an actual disc"], ["a disc!", "jukebox time", "tunes"]),
    "tag": (["a name tag", "a name tag and some wheat", "one name tag. just one"], ["a name tag!", "nice", "rare"]),
    "book": (["an enchanted book. protection", "a protection book", "an enchanted book, protection two"], ["nice", "a book", "useful"]),
    "junk": (["bread, string and a rotten flesh", "string. gunpowder. a bucket", "some wheat and a bone"], ["lol", "that's it?", "rip"]),
}
SPAWNER_END = {
    "farm": (["so i built an xp farm round the spawner", "turned the whole room into a mob farm", "made an xp farm out of it"],
             "wow", ["smart", "xp for days", "nice"],
             ["i got level 30 in like an hour", "level 30 in no time", "now i go there every time i need levels"]),
    "rode": (["put the saddle on a pig and rode it home", "found a pig and rode it home with the saddle", "rode a pig all the way home"],
             "laugh", ["a pig??", "lol pig rider", "iconic"],
             ["took two days. pigs are slow", "slowest ride of my life", "it went everywhere but home"]),
    "named": (["named my dog with it. biscuit", "used it on my cat. now she's called boss", "named my horse thunder"],
              "aw", ["cute", "great name", "aww"],
              ["best use of a name tag", "worth more than diamonds", "they wear it proud"]),
    "jukebox": (["made a jukebox and played it every night", "played it in my house every night", "got a jukebox just for that disc"],
                "aw", ["nice", "vibes", "cozy"],
                ["i know every note now", "it never gets old", "my house has a soundtrack now"]),
    "lost": (["then i fell in a ravine on the way home with all of it", "then a creeper got me on the way out", "and then i died on the way home"],
             "sad", ["NO", "rip", "the spawner won"],
             ["lost the loot and all", "never got it back", "the dungeon took it all back"]),
}


def spawner(n, v):
    loot, loot_lines = SPAWNER_LOOT[v.loot]
    end, end_react, end_lines, end2 = SPAWNER_END[v.end]
    p1 = P(
        A(f"found a dungeon once {v.where}", f"so i'm {v.where} and i find a dungeon", f"dungeon story. {v.where}"),
        B(None, "a dungeon!", "with a spawner?", "the mossy cobble room", "ooh", "nice"),
        A("mossy cobble, two chests, and a spawner in the middle", "little room, mossy walls, a cage spinning in the middle",
          "a spawner going round and round, chests on the sides"),
        B("ask", "what was in the cage", "what mob", "and?"),
        A(f"a little {v.mob} spinning in it", f"{v.mob} spawner", f"it was a {v.mob} one"),
        B("gasp" if v.mob != "zombie" else "ask", f"{v.mob}s", "uh oh", "careful"),
    )
    p2 = P(
        A(f"and {v.mob}s just start popping out", f"{v.mob}s, one after another", f"it spits out {v.mob} after {v.mob}"),
        B("gasp", "kill the spawner", "torches!", "too many"),
        A("so i'm fighting with one hand, torches in the other", "i'm swinging and slapping torches down", "i fought my way in, torch by torch"),
        B("ask", "did it work", "torch it", "go go"),
        A("got torches on the cage and it stopped", "torched the spawner. quiet finally", "torches on every side. it stopped", chance=0.85),
    )
    p3 = P(
        A(f"then the chests. first one: {loot[0]}", f"opened the chests and found {loot[1]}", f"chests had {loot[2]}"),
        B("wow" if v.loot != "junk" else "laugh", *loot_lines),
        A("and the other chest had bread and string", "the second chest was just bread", "other chest: wheat and a bucket"),
        B(None, "classic", "bread lol", "standard", "of course", "dungeon bread"),
        A("but that first one made it worth it", "still worth it for the first chest", "the first chest was the prize", chance=0.7)
        if v.loot != "junk" else A("so, a great day", "best dungeon ever lol", "worth almost dying for", chance=0.7),
    )
    p4 = P(
        A(*end),
        B(end_react, *end_lines),
        A(*end2),
        B("done", "good find", "dungeons are the best", "nice"),
        A("still go back there sometimes", "it's still there, all lit up", "best dungeon i ever found", chance=0.6),
    )
    tell("dungeon_" + n, [p1, p2, p3, p4], ["structures", "mobs", "brag"], "the dungeon", leads=["structures", "caves", "mobs"],
         end_leads=["structures", "brag", "mobs"])


each([
    dict(where="in a cave by my house", mob="skeleton", loot="saddle", end="rode"),
    dict(where="under a mountain", mob="zombie", loot="disc", end="farm"),
    dict(where="digging a basement", mob="spider", loot="tag", end="named"),
    dict(where="down by the lava lakes", mob="skeleton", loot="book", end="lost"),
    dict(where="exploring a ravine", mob="zombie", loot="junk", end="farm"),
    dict(where="in a cave under the village", mob="spider", loot="disc", end="jukebox"),
    dict(where="in the old caves", mob="zombie", loot="tag", end="named"),
], spawner)


# --- The best thing it ever fished up.
FISH_CATCH = {
    "bow": (["an enchanted bow. power three", "a bow. enchanted. power and unbreaking", "an enchanted bow, all glowing"],
            ["a bow? from a pond?", "fishing for weapons", "no way"],
            ["used it for years", "that bow got me through every night", "best bow i ever had"],
            ["and it saved my life a week later", "a week later, that bow saved my life", "and that bow saved me, not long after"], ["a skeleton had me cornered, the bow won", "shot a creeper right before it blew",
                                                  "shot three zombies off my roof with it"]),
    "tag": (["a name tag. from a pond", "a name tag on the hook", "out comes a name tag"],
            ["a name tag!", "nice", "rare"],
            ["kept it for the right animal", "saved it for something special", "didn't use it for months"],
            ["then i found the perfect animal for it", "and then i met the right animal", "then one day the perfect animal showed up"], ["a tiny baby fox. named her pebble", "a sheep that was blue. named him sky",
                                                "a cat that kept following me. named her boots"]),
    "saddle": (["a saddle. a whole saddle", "a saddle, dripping wet", "pulled up a saddle"],
               ["a saddle!", "free ride", "lucky"],
               ["went and found a horse that day", "tamed a horse that same day", "rode off that afternoon"],
               ["and the horse i found was the fastest one ever", "and that horse turned out to be crazy fast", "the horse i got was so fast"], ["it ran like the wind", "it was so fast i got lost",
                                                                 "fastest horse in the world, i swear"]),
    "book": (["an enchanted book. mending", "a mending book. from fishing", "out comes a book. mending"],
             ["MENDING?", "from fishing??", "that's insane"],
             ["put it on my pickaxe right away", "my pickaxe has mending now", "slapped it on my pick"],
             ["and that pickaxe is still going", "and that pick never died", "that pickaxe is still alive today"], ["it never breaks", "same pick, years later", "it's older than my house"]),
    "puffer": (["a pufferfish. and i was so hungry", "a pufferfish, and i was starving", "a puffer. and my food bar was empty"],
               ["don't eat it", "no", "puffers are poison"],
               ["ate it anyway", "i ate it. i was hungry", "i was too hungry to care"],
               ["and then everything went green", "and then it hit me. everything green", "then the poison kicked in"], ["poisoned, sick, nauseous, all of it", "everything spinning, health going down",
                                              "nausea, poison, the whole set"]),
}


def fishing(n, v):
    catch, c_lines, after, turn_, turn_lines = FISH_CATCH[v.catch]
    p1 = P(
        A(f"best thing i ever fished up? ok listen", f"i was fishing {v.where} once", f"fishing story, {v.where}"),
        B(None, "fishing?", "how exciting can fishing be", "go on", "chill", "ok i'm listening"),
        A(f"{v.weather}, just me and the rod", f"it was {v.weather}, perfect fishing", f"{v.weather}, fish biting like crazy"),
        B("ask", "nice", "rain is good for fishing", "and?"),
        A("caught a boot. then another boot", "first i got a boot, then a stick", "got a boot, a bowl, and a bone"),
        B("laugh", "the boots lol", "junk", "classic fishing"),
    )
    p2 = P(
        A("then the bobber goes down HARD", "then the bobber gets pulled right under", "and then a big tug on the line"),
        B(None, "big one", "pull!", "a fish?", "go go", "here we go"),
        A(*catch),
        B("wow" if v.catch != "puffer" else "gasp", *c_lines),
        A(*after),
    )
    p3 = P(
        A(*turn_),
        B("ask", "what happened", "tell me", "go on"),
        A(*turn_lines),
        B("laugh" if v.catch == "puffer" else ("aw" if v.catch == "tag" else "wow"), *v.r3),
        A(*v.end, chance=0.8),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A("and i've fished there every day since", "i fished in that same spot for a month after", "so of course i went back every day"),
            B("ask", "and?", "anything good?", "for another one"),
            A(*v.epi),
            B("laugh", "lol", "fishing is a scam", "the pond gave all it had"),
            A("still go there. you never know", "i still try. every day", "one day it'll happen again", chance=0.6),
        )
    tell("fishing_" + n, [p1, p2, p3, p4], ["fishing", "brag", "jokes"], "the fishing trip", leads=["fishing", "food", "ocean"],
         end_leads=["fishing", "brag", "jokes"])


each([
    dict(where="off my dock", weather="raining", catch="bow", r3=["the bow!", "clutch", "pond loot saves lives"],
         end=["i still have it, all scratched", "it's on my wall now", "retired now. on the wall"], epi=["boots. so many boots", "nothing but boots", "boots and sticks forever"]),
    dict(where="in a swamp lake", weather="storming", catch="tag", r3=["aww", "cute name", "perfect"],
         end=["it follows me everywhere now", "best friend i have", "it sleeps by my bed"], epi=None),
    dict(where="from my boat at sea", weather="raining", catch="saddle", r3=["a fast horse!", "nice", "zoom"],
         end=["named it too, after the sea", "it's still the fastest one around", "nobody can catch me now"], epi=["a second saddle. i don't need two", "another saddle lol", "more saddles. i have three now"]),
    dict(where="in the river by town", weather="pouring", catch="book", r3=["insane", "best pick ever", "mending is life"],
         end=["luckiest cast ever", "one cast. one mending book", "best day of fishing ever"], epi=["a pufferfish. that's it", "pufferfish and boots", "junk, and one salmon"]),
    dict(where="at the pond behind my house", weather="raining", catch="puffer", r3=["LOL", "told you", "why would you"],
         end=["i lay on the grass for a minute", "ate some bread after, felt better", "milk. i needed milk"], epi=None),
    dict(where="in a lake in the hills", weather="drizzling", catch="bow", r3=["clutch bow", "w", "nice shot"],
         end=["fishing is a combat skill now", "fishing made me a fighter", "best fishing trip ever"], epi=None),
    dict(where="off the end of a jetty", weather="raining hard", catch="tag", r3=["aww", "cute", "lovely name"],
         end=["she still has the tag on", "it's family now", "i'd do anything for that animal"], epi=["more boots", "a fishing rod. from fishing", "just cod. so much cod"]),
], fishing)


# =====================================================================================================================
# DISASTERS
# =====================================================================================================================

# --- The house that burned.
BURN_CAUSE = {
    "lightning": (["a thunderstorm. lightning hit the roof", "lightning struck right on my roof", "big storm, and lightning hit my house"],
                  "gasp", ["lightning??", "the sky hates you", "no way"]),
    "lava": (["i'd put lava in the wall for light. bad idea", "i used a lava wall for a fireplace", "a lava light, right next to the planks"],
             "laugh", ["lava next to wood??", "oh no", "you did it to yourself"]),
    "flint": (["i lit a fire on the floor to make it cozy", "lit a fire with flint and steel. indoors", "i made a little fire inside to keep warm"],
              "gasp", ["inside??", "oh no", "why would you"]),
    "fire": (["a netherrack fireplace, no glass in front", "a fireplace with no cover", "fire on netherrack, right by the wall"],
             "gasp", ["oh no", "fire spreads", "not the fireplace"]),
}
BURN_SAVE = {
    "chests": (["ran in and grabbed what i could from the chests", "ran in through the smoke to my chests", "dove into the chest room"],
               "ask", ["brave", "your stuff!", "get out of there"],
               ["got most of it out. lost my bed and the floor", "saved the chests. the house, not so much",
                "saved the stuff, lost everything else"]),
    "water": (["threw water everywhere", "i was dumping water buckets like crazy", "water bucket, splash, splash, splash"],
              "ask", ["did it work", "water on the roof?", "go go"],
              ["water can't reach a roof that well. it burned anyway", "the water ran off the roof. useless", "half went out. half didn't"]),
    "nothing": (["i just stood there watching", "i couldn't do anything. just watched", "i stood in the rain and watched it burn"],
                "sad", ["oh no", "that's awful", "nothing you could do"],
                ["it burned down to the floor", "the whole thing was gone by morning", "nothing left but the stone bits"]),
}
BURN_AFTER = {
    "stone": (["rebuilt it all in stone. cobble and stone brick", "built it back out of stone, every block", "built the new one all stone"],
              ["it's ugly but it'll never burn", "it's not as cozy but it's fireproof", "stone forever"]),
    "friends": (["my friends came and helped me rebuild", "friends showed up with wood and helped", "everyone helped me build it again"],
                ["it's better than the first one now", "took two days with help", "the new one has a second floor"]),
    "hole": (["slept in a hole in the hill for a week", "lived in a dirt hole for days", "camped in a hole till i could rebuild"],
             ["then built a tiny hut, then a house", "slowly built a new place", "worked my way back up"]),
}


def house_burn(n, v):
    cause, c_react, c_lines = BURN_CAUSE[v.cause]
    save, s_react, s_lines, save2 = BURN_SAVE[v.save]
    after, after2 = BURN_AFTER[v.after]
    p1 = P(
        A(f"did i ever tell you my old house burned down", f"my old {v.house} burned down once", f"you know my {v.house}? it burned down"),
        B("gasp", "WHAT", "no", "how", "it burned??"),
        A(f"it was {v.what}", f"and it was {v.what}", f"this was {v.what}"),
        B("sad", "oh no", "that hurts", "the worst"),
        A("it was a normal night, i was home", "i was right there when it started", "i was in bed when it started"),
        B("ask", "what happened", "how did it start", "go on"),
    )
    p2 = P(
        A(*cause),
        B(c_react, *c_lines),
        A("the fire jumped from block to block", "it spread across the roof so fast", "in seconds the roof was on fire"),
        B("gasp", "wood burns so fast", "run", "oh no"),
        A("i could hear the planks crackling", "the whole room went orange", "smoke and fire everywhere", chance=0.8),
    )
    p3 = P(
        A(*save),
        B(s_react, *s_lines),
        A(*save2),
        B("sad", "your house", "nooo", "so sad"),
        A("i sat outside in the rain afterwards", "sat on a log looking at it all night", "i just stood there till morning", chance=0.7),
    )
    p4 = P(
        A(*after),
        B("aw" if v.after == "friends" else "done", "good", "you came back", "respect"),
        A(*after2),
        B(None, "nice", "good", "that's the spirit", "glad you're ok", "that's how it goes"),
        A("i still have one burnt plank from the old one", "i kept one burnt plank, just to remember", "kept one log from the old place", chance=0.6),
    )
    p5 = None
    if v.epi:
        p5 = P(
            A(*v.epi),
            B(None, *v.r5a),
            A(*v.epi2),
            B(None, *v.r5b),
            A("anyway. stone houses. trust me", "anyway. watch your fire", "anyway, that's my fire story", chance=0.6),
        )
    tell("burned_" + n, [p1, p2, p3, p4, p5], ["home", "danger", "building"], "the fire", leads=["home", "building", "danger"],
         end_leads=["home", "building", "feelings"], needs=["a:has_house"], weight=0.9)


each([
    dict(house="oak cabin", what="my first real house", cause="lightning", save="chests", after="stone",
         epi=["and the next storm? lightning hit the stone house", "and you know what, lightning hit again", "a week later, another storm"],
         epi2=["it hit stone. nothing happened. i laughed", "nothing burned. stone. ha", "it hit the stone and nothing happened. i cheered"],
         r5a=["no way", "again??", "the sky hates you"], r5b=["lol", "stone wins", "take that, sky"]),
    dict(house="spruce house", what="the house i spent a month on", cause="lava", save="water", after="friends", epi=None),
    dict(house="birch cottage", what="the cutest house i ever made", cause="flint", save="nothing", after="hole", epi=None),
    dict(house="big wooden house", what="two floors, all wood", cause="fire", save="chests", after="friends",
         epi=["my cat was in there the whole time", "i thought my cat was gone", "my cat went missing in the fire"],
         epi2=["found her the next day on the roof of the shed", "she was sleeping in the garden. fine", "she was on a fence post, totally fine"],
         r5a=["oh no", "the cat!", "please say she was ok"], r5b=["phew", "cats are tough", "aww"]),
    dict(house="dark oak house", what="my favourite place in the world", cause="lightning", save="nothing", after="stone",
         epi=["then i learned about lightning rods", "and then someone told me about lightning rods", "after that i found out lightning rods exist"],
         epi2=["there are four on my roof now", "my roof is all lightning rods now", "copper on every corner now"],
         r5a=["smart", "good idea", "nice"], r5b=["lol", "safe now", "overkill, love it"]),
    dict(house="little wooden house", what="right by the lake", cause="lava", save="chests", after="hole", epi=None),
    dict(house="jungle wood house", what="up in the jungle trees", cause="flint", save="water", after="stone",
         epi=["and the jungle burned for a week", "the fire went into the jungle", "the trees round it caught fire too"],
         epi2=["the whole jungle round it is a clearing now", "there's a big burnt clearing now", "now it's a field. kind of nice"],
         r5a=["oh no", "the jungle!", "a jungle fire"], r5b=["nice", "happy accident", "lol"]),
], house_burn)


# --- Dying far away, and the race to get its stuff back.
RUN_END = {
    "made": (["got there with seconds to spare", "picked it all up right as it started blinking", "grabbed everything just in time"],
             "relief", ["CLUTCH", "just in time", "no way"],
             ["every single item. even the dirt", "got all of it back", "everything was still there"]),
    "gone": (["got there and it was all gone", "got there. empty. it had despawned", "nothing. just grass. it was gone"],
             "sad", ["NOOO", "after all that", "so close"],
             ["i just stood there in the empty spot", "i stared at the grass for a minute", "i sat down right there"]),
    "zombie": (["got there and a zombie was wearing my armor", "and there's a zombie. in my armor. holding my sword", "a zombie had picked up my stuff and put it on"],
               "laugh", ["LMAO", "the zombie took it", "fashion zombie"],
               ["so i fought my own armor. it was hard", "had to fight a zombie in my own gear", "i fought it with my fists. it had my sword"]),
}


def death_run(n, v):
    end, end_react, end_lines, end2 = RUN_END[v.end]
    p1 = P(
        A(f"worst run of my life. i died {v.where}", f"so i died {v.where} with everything on me", f"i once died {v.where}, all my stuff on me"),
        B("gasp", "with everything?", "oh no", "the run back"),
        A(f"all my armor, my tools, {v.loot}", f"full iron, my good pickaxe, {v.loot}", f"everything i owned, plus {v.loot}"),
        B("ask", "and you respawned at home", "the clock is ticking", "uh oh"),
        A(f"and i respawn at home. {v.far} away", f"woke up at home, {v.far} away", f"respawned in my bed, {v.far} from my stuff"),
        B(None, "RUN", "go go go", "that's so far", "you'll never make it", "five minutes, go"),
    )
    p2 = P(
        A("so i'm sprinting. no armor, no food, nothing", "ran out the door with literally nothing", "sprinting with empty hands"),
        B("ask", "go go", "faster", "and?"),
        A(*v.obstacle),
        B(v.ob_react, *v.ob_lines),
        A(*v.obstacle2, chance=0.85),
    )
    p3 = P(
        A(*end),
        B(end_react, *end_lines),
        A(*end2),
        B("done", "the death run", "every time", "worst feeling"),
        A(*v.lesson, chance=0.6),
    )
    p4 = None
    if v.end == "zombie":
        p4 = P(
            A("so who won? guess", "and the fight. guess who won", "the fight went on forever"),
            B(None, "you?", "the zombie?", "who won", "please say you won"),
            A("i won. punched it to death. it took ages", "i won, with a stick and a dream", "won it. barely. with my fists"),
            B("laugh", "a legend", "fist fight with a zombie", "lol"),
            A("got everything back. even the helmet it was wearing", "it dropped my stuff. all of it", "everything came back", chance=0.8),
        )
    elif v.epi:
        p4 = P(
            A(*v.epi),
            B("laugh", "of course", "lol", "nooo"),
            A(*v.epi2),
            B(None, "lol", "classic", "of course", "that's life"),
            A("i keep a bed everywhere now", "beds everywhere i go now", "spawn points, people. spawn points", chance=0.6),
        )
    tell("deathrun_" + n, [p1, p2, p3, p4], ["death", "danger", "exploring"], "the run back", leads=["death", "danger", "gear"],
         end_leads=["death", "jokes", "gear"])


each([
    dict(where="deep in a cave", loot="nine diamonds", far="a thousand blocks", end="made",
         obstacle=["it gets dark halfway there", "night falls on the way", "and then the sun goes down"], ob_react="gasp",
         ob_lines=["no armor at night", "oh no", "keep going"], obstacle2=["ran past zombies, didn't stop", "dodged everything, kept running", "zombies everywhere, didn't care"],
         lesson=["walked home in my armor like a king", "never been so happy to see dirt", "i hugged my pickaxe"],
         epi=["and on the way home i died again", "and then i fell in a hole on the way home", "i died AGAIN on the way back"],
         epi2=["same spot. same cave. same everything", "fell down the exact same cave", "back to the start"]),
    dict(where="in a jungle", loot="a stack of cocoa beans", far="eight hundred blocks", end="gone",
         obstacle=["the jungle is a maze of leaves", "i got lost in the jungle running", "leaves and vines everywhere"], ob_react="laugh",
         ob_lines=["jungles lol", "no", "worst place to run"], obstacle2=["got stuck in vines for a minute", "climbed a tree to look", "fell in a melon patch"],
         lesson=["cocoa beans are not worth dying for", "i don't go to jungles now", "jungles hate me"], epi=None),
    dict(where="in a mountain cave", loot="a stack of iron", far="six hundred blocks", end="zombie",
         obstacle=["the mountain was in the way", "i had to climb the whole mountain", "a mountain between me and my stuff"], ob_react="ask",
         ob_lines=["climb!", "go go", "and?"], obstacle2=["climbed it like a goat", "pillared up with dirt", "jumped up block by block"],
         lesson=["zombies love free gear", "zombies are thieves", "they love dressing up"], epi=None),
    dict(where="out at sea", loot="all my tools", far="nine hundred blocks", end="gone",
         obstacle=["no boat. i had to swim", "i swam the whole way. no boat", "had to swim it. i lost my boat too"], ob_react="gasp",
         ob_lines=["swimming??", "that's so slow", "oh no"], obstacle2=["drowned were everywhere", "drowned chasing me the whole way", "swimming past drowned at night"],
         lesson=["boats in every chest now", "i always carry a spare boat now", "two boats, always"],
         epi=["then i got home and found my tools", "and the tools? i'd left them in my chest at home", "then i found my tools in a chest at home"],
         epi2=["i never had them on me. i swam for nothing", "i never even took them. swam for nothing", "the whole swim was for nothing"]),
    dict(where="in a ravine", loot="a full map", far="five hundred blocks", end="made",
         obstacle=["i ran into a river and had to swim", "a river in the way, swam it", "had to cross a river"], ob_react="ask",
         ob_lines=["swim!", "go go", "faster"], obstacle2=["came out the other side soaking", "climbed out, kept running", "out of the water, sprinting"],
         lesson=["i put a bed right by that ravine after", "i keep a spare bed out there now", "spare bed in every biome now"], epi=None),
    dict(where="in a swamp", loot="all my potions", far="seven hundred blocks", end="zombie",
         obstacle=["a witch threw slowness at me on the way", "a witch slowed me down halfway", "got hit with slowness, of course"], ob_react="gasp",
         ob_lines=["a witch!", "slowness, no", "oh no"], obstacle2=["crawled the rest of the way", "walked there at snail speed", "slowly. very slowly"],
         lesson=["swamps and i are done", "i'm never going to a swamp again", "the swamp wins"], epi=None),
    dict(where="in a badlands mineshaft", loot="a stack of gold", far="twelve hundred blocks", end="gone",
         obstacle=["the badlands are so big", "orange hills forever", "all the hills look the same"], ob_react="laugh",
         ob_lines=["badlands lol", "pretty though", "go go"], obstacle2=["got turned around twice", "went the wrong way once", "nearly fell in a canyon"],
         lesson=["gold isn't worth this", "i don't even like gold", "gold, who needs it"], epi=None),
], death_run)


# --- Tnt to clear some land.
def tnt(n, v):
    p1 = P(
        A("i had a stack of tnt and a dream", f"so i had a stack of tnt and i wanted to clear {v.what}", "ever used tnt to save time? don't"),
        B("smirk", "oh no", "i see where this goes", "this will end well"),
        A(f"the plan: blow up {v.what}", f"i wanted {v.what} gone", f"{v.what} was in the way of my build"),
        B("ask", "why not dig it", "with tnt??", "go on"),
        A("digging takes forever. tnt is fast", "digging is slow. i'm impatient", "i didn't want to dig it all by hand"),
        B(None, "fair", "famous last words", "here we go", "ok", "lol"),
    )
    p2 = P(
        A(f"put down {v.amount} tnt in a line", f"placed {v.amount} blocks, nice and even", f"{v.amount} tnt, lined up perfect"),
        B(None, "that's a lot", "ok", "go on", "uh oh", "here we go"),
        A("lit the first one and ran", "flint and steel, click, ran", "lit it and sprinted off"),
        B("ask", "and?", "boom?", "far enough?"),
        A(*v.boom),
    )
    p3 = P(
        A(*v.twist),
        B(v.tw_react, *v.tw_lines),
        A(*v.twist2),
        B("done", "tnt is never the answer", "lol", "classic tnt"),
        A(*v.lesson, chance=0.6),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A(*v.epi),
            B("laugh", "lol", "of course", "no way"),
            A(*v.epi2),
            B(None, "honestly great", "lol", "a happy accident", "nice"),
            A("best mistake ever", "tnt fixed it in the end", "i'd do it again", chance=0.6),
        )
    tell("tnt_" + n, [p1, p2, p3, p4], ["building", "danger", "jokes"], "the tnt", leads=["building", "jokes", "danger"],
         end_leads=["building", "jokes", "home"])


each([
    dict(what="a hill next to my house", amount="twenty",
         boom=["BOOM BOOM BOOM, one after the other", "they all went off in a chain. BOOM", "the whole line went off"],
         twist=["the hill was gone. so was my front wall", "hill gone. also half my house", "the blast took the front of my house too"],
         tw_react="laugh", tw_lines=["lmao", "the house too??", "two for one"],
         twist2=["i'd put the last tnt right by my door", "the last one was a bit too close to the house", "the tnt line ended at my door"],
         lesson=["i measure twice now", "count blocks before tnt", "never put tnt near home"],
         epi=["so i rebuilt the front with glass", "rebuilt the front wall with big windows", "fixed the wall. with windows this time"],
         epi2=["and now i can see the view where the hill was", "best view in the world now", "it looks way better now honestly"]),
    dict(what="a big mountain", amount="forty",
         boom=["a huge boom, rocks flying everywhere", "it went off and the ground shook", "BOOM. dust everywhere"],
         twist=["the mountain was still there. one dent", "it made a tiny dent. a dent", "mountain: fine. one dent. me: out of tnt"],
         tw_react="laugh", tw_lines=["lol a dent", "mountains win", "forty tnt for a dent"],
         twist2=["mountains are bigger than you think", "i didn't think it through", "stone barely blows up"],
         lesson=["i just built on the mountain instead", "i moved the build up onto it", "if you can't beat it, build on it"], epi=None, epi2=None),
    dict(what="a forest for my farm", amount="thirty",
         boom=["BOOM, and the trees went up in flames", "it went off and lit the trees on fire", "boom, and then fire"],
         twist=["the whole forest caught fire", "and the fire spread through the forest", "the forest burned for an hour"],
         tw_react="gasp", tw_lines=["a forest fire??", "oh no", "the animals!"],
         twist2=["but hey, it was cleared", "i got my clearing. a burnt one", "well, the land was clear"],
         lesson=["never tnt near trees", "trees and tnt don't mix", "i plant saplings to say sorry now"],
         epi=["and the ash field turned into the best farm", "and the farm i built there is amazing", "and that burnt field is my farm now"],
         epi2=["biggest wheat farm in the area", "wheat as far as you can see", "best crops i ever grew"]),
    dict(what="a flat spot for a big build", amount="fifteen",
         boom=["BOOM. and the ground fell away", "BOOM, and there was a hole", "it blew and the floor went"],
         twist=["it blew right into a cave under it", "there was a cave under there, now it's a pit", "the blast opened up a huge cave"],
         tw_react="gasp", tw_lines=["a cave!", "oh no", "how deep"],
         twist2=["zombies started climbing out of my building spot", "mobs walking out of my new hole", "skeletons shooting out of the pit"],
         lesson=["filled it in. took a week", "i filled the hole for days", "should've just dug"],
         epi=["but the cave had iron everywhere", "and the cave was full of iron", "turned out the cave had loads of iron"],
         epi2=["so i built a mine instead of a house", "the build is a mine entrance now", "my build is a mine now"]),
    dict(what="the sand by the beach", amount="ten",
         boom=["BOOM. sand flying everywhere", "it went off and the sand rained down", "boom, and sand dropped all over"],
         twist=["the sea flooded straight into the hole", "the ocean rushed into the crater", "water poured in from the sea"],
         tw_react="laugh", tw_lines=["a new lake", "lol", "the ocean wins"],
         twist2=["so now i have a bay. by accident", "it's a little bay now", "i made a harbor by mistake"],
         lesson=["i built a dock there. on purpose, obviously", "put a dock in it. planned", "now it's my boat spot. totally planned"], epi=None, epi2=None),
    dict(what="a swamp next to my place", amount="twenty five",
         boom=["BOOM. mud and water everywhere", "it went off and slime flew everywhere", "boom, and the swamp water rose up"],
         twist=["tnt does nothing under water", "the tnt in the water did nothing", "half the tnt was in water. nothing"],
         tw_react="laugh", tw_lines=["tnt hates water", "lol", "a waste"],
         twist2=["the half on land blew up my fence", "the dry ones took out my fence", "the other half got my fence"],
         lesson=["learned tnt doesn't work in water", "water stops tnt. now i know", "tnt and water, no"], epi=None, epi2=None),
], tnt)

# --- A flooded basement.
def flood(n, v):
    p1 = P(
        A(f"i flooded my own {v.room} once", f"so i was digging {v.room} under my house", f"you know how i wanted {v.room}?"),
        B(None, "flooded?", "how", "uh oh", "go on", "oh no"),
        A(f"wanted somewhere for {v.why}", f"it was going to be for {v.why}", f"the plan was {v.why} down there"),
        B(None, "nice idea", "sounds cozy", "smart", "ok", "and?"),
        A("so i'm digging down, nice and neat", "dug down a few blocks, all good", "i'm digging out the room, block by block"),
        B("ask", "and then water", "i can guess", "uh oh"),
    )
    p2 = P(
        A(f"and i break into {v.water}", f"one block, and it's {v.water}", f"and the wall opens into {v.water}"),
        B("gasp", "oh no", "water everywhere", "plug it!"),
        A("water pouring in from every side", "the water just keeps coming", "it fills the room in seconds"),
        B("ask", "what did you do", "block it", "swim!"),
        A(*v.fix),
    )
    p3 = P(
        A(*v.fix2),
        B(v.fix_react, *v.fix_lines),
        A(*v.result),
        B("done", "classic", "water always wins", "lol"),
        A(*v.lesson, chance=0.6),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A("and then something moved in", "and then a few days later, i see something in there", "and the best part. something lives there now"),
            B(None, "what", "a drowned?", "uh oh", "something??", "what moved in"),
            A(*v.epi),
            B("aw", "no way", "cute", "best roommate"),
            A("i'd never fill it in now", "it's the best room in the house", "i visit it every day", chance=0.7),
        )
    tell("flood_" + n, [p1, p2, p3, p4], ["home", "building", "jokes"], "the flood", leads=["home", "building", "storage"],
         end_leads=["home", "building", "jokes"], needs=["a:has_house"], weight=0.9)


each([
    dict(room="a basement", why="all my chests", water="an underground lake",
         fix=["threw sand at it. it just sank", "dumped sand in, it sank away", "tried sand. the sand vanished"],
         fix2=["so i gave up and climbed out", "so i swam up and left it", "i got out and looked down at it"],
         fix_react="laugh", fix_lines=["lol gave up", "fair", "the lake wins"],
         result=["now i have an indoor pool", "it's a pool now. on purpose", "basement pool. very fancy"],
         lesson=["the chests went in the attic", "chests live upstairs now", "i built an attic for the chests"],
         epi=["a glow squid. it lights up the whole room", "a glow squid, glowing away down there", "a glow squid moved in"]),
    dict(room="a potion cellar", why="my potions", water="a river running under the hill",
         fix=["blocked it with cobble, block by block", "placed cobble as fast as i could", "slapped cobble on every leak"],
         fix2=["took an hour, but i plugged every hole", "i plugged it all. one hour of cobble", "every leak blocked, finally"],
         fix_react="relief", fix_lines=["nice", "you won", "take that, water"],
         result=["but it was wet in there for days", "the floor was soggy for ages", "water kept dripping through for days"],
         lesson=["potions are fine. they're in bottles", "potions don't mind water lol", "the potions survived, they're in glass"], epi=None),
    dict(room="a storage room", why="my chests", water="an aquifer",
         fix=["used buckets. bucket after bucket", "tried to empty it with buckets", "scooped it with a bucket. one at a time"],
         fix2=["it was infinite water. buckets do nothing", "it never ends. it's an infinite source", "the water just refilled every time"],
         fix_react="laugh", fix_lines=["infinite water lol", "buckets vs the sea", "classic"],
         result=["moved my storage to a shed outside", "built a shed for the chests instead", "the chests live in a shed now"],
         lesson=["the basement's a fish tank now", "i put fish in it", "it's a fish tank now"],
         epi=["axolotls. pink ones", "two axolotls. pink and blue", "an axolotl. i named him puddle"]),
    dict(room="a bedroom underground", why="a cozy bed out of the rain", water="the ocean, somehow",
         fix=["sponges? i had no sponges", "no sponges. no plan", "i had nothing to stop it"],
         fix2=["so i just let it fill up", "i let it flood", "i just watched it fill"],
         fix_react="sad", fix_lines=["rip bedroom", "your cozy room", "oh no"],
         result=["my bed's underwater now", "the bed is at the bottom of a pool now", "underwater bed. can't sleep in it"],
         lesson=["i sleep upstairs like a normal person now", "bedrooms go above ground", "never dig a bedroom by the sea"],
         epi=["a dolphin. it found a way in", "a dolphin. how?", "a turtle. a little turtle"]),
    dict(room="a mine under my house", why="a quick way to the ores", water="a flooded cave",
         fix=["gravel! i threw gravel down", "dumped a stack of gravel in", "poured gravel into the hole"],
         fix2=["gravel worked. it fell and plugged it", "the gravel sank down and blocked it", "it filled the hole. done"],
         fix_react="wow", fix_lines=["gravel did something useful", "nice", "gravel w"],
         result=["but the mine was all wet and gross", "but now the mine smells like a pond", "the mine's damp forever now"],
         lesson=["keep gravel for that now", "gravel is underrated", "i always carry gravel now"], epi=None),
    dict(room="a secret room", why="my best stuff", water="a little pond i forgot about",
         fix=["blocked it with dirt. quick", "slapped dirt in the hole", "put a dirt block in the hole"],
         fix2=["stopped it fast. one block of water got in", "only a bit got in", "only a puddle got in"],
         fix_react="relief", fix_lines=["only a puddle", "phew", "lucky"],
         result=["the secret room has a puddle now", "there's a puddle in my secret room", "it's a secret room with a puddle"],
         lesson=["the puddle is the secret now", "nobody knows about the puddle", "i kept the puddle"],
         epi=["a frog. a tiny frog", "a little frog in the puddle", "a frog moved into the puddle"]),
], flood)


# =====================================================================================================================
# GETTING LOST
# =====================================================================================================================

# --- Lost in a cave.
def lost_cave(n, v):
    p1 = P(
        A(f"i got so lost in a cave once. {v.cave}", f"ever been properly lost underground? {v.cave}", f"so there's {v.cave}, and i went in"),
        B(None, "how lost", "oh no", "caves are mazes", "go on", "uh oh"),
        A(*v.mistake),
        B("gasp", "classic", "torches on one side, always", "oh no"),
        A("i turn round to go home and everything looks the same", "time to go home, and nothing looks familiar", "and when i want to go back, i have no idea where i am"),
        B("creepy", "nightmare", "oh no", "the worst feeling"),
    )
    p2 = P(
        A("every tunnel looks the same", "left, right, up, down, all the same", "stone, stone, more stone"),
        B("ask", "what did you do", "panic?", "and?"),
        A("then i see a torch. one of mine", "and i find a torch. MY torch", "then i see my own torch on the wall"),
        B("laugh", "circles", "you went in a circle", "lol"),
        A("i'd gone in a full circle", "i'd been walking in circles for ages", "a circle. a big dumb circle", chance=0.85),
    )
    p3 = P(
        A(*v.try_),
        B(v.try_react, *v.try_lines),
        A(*v.out),
        B("wow" if v.lucky else "laugh", *v.out_lines),
        A(*v.out2, chance=0.8),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A(*v.epi),
            B(None, *v.epi_r),
            A(*v.epi2),
            B("done", "caves are wild", "what a trip", "never going caving again"),
            A("torches on the right. always. forever", "right side going in. always", "i mark everything now", chance=0.6),
        )
    tell("lostcave_" + n, [p1, p2, p3, p4], ["caves", "exploring", "danger"], "the cave", leads=["caves", "exploring", "mining"],
         end_leads=["caves", "exploring", "jokes"])


each([
    dict(cave="a huge one under the hills", mistake=["i put torches on both sides. both", "torches on both walls. big mistake", "i lit both sides, so no way to tell"],
         try_=["so i dug straight up. up up up", "dug a staircase straight up", "i just started digging up"], try_react="ask",
         try_lines=["did it work", "smart", "how far"], out=["came out in the middle of a desert", "popped out in a desert", "came up in a desert"],
         lucky=False, out_lines=["a desert??", "how far did you go", "lol"], out2=["my house was in a forest. far away", "home was in the forest. miles off", "i walked home for a day"],
         epi=["and i got home and my friends had made a sign", "my friends thought i was gone. they'd made a sign", "they'd put up a sign for me in town"],
         epi_r=["a sign?", "aw", "what did it say"], epi2=["it said MISSING. with a drawing of me", "it said: gone caving. probably dead", "it said: rip. lol"]),
    dict(cave="a lush cave with glow berries", mistake=["it was so pretty i stopped watching where i went", "i was too busy looking at the glow berries", "i got distracted by the plants"],
         try_=["i followed the glow berries back", "i followed the vines i'd eaten from", "tried to follow the bits i'd eaten"], try_react="laugh",
         try_lines=["a berry trail", "smart-ish", "lol"], out=["ended up deeper. way deeper", "they just led me deeper", "the berries were everywhere. deeper and deeper"],
         lucky=False, out_lines=["lol", "berries betray you", "oh no"], out2=["finally found a hole with sky in it", "found a hole up to the sky, climbed out", "saw daylight through a crack and dug to it"], epi=None),
    dict(cave="a dripstone cave", mistake=["my torches ran out halfway", "ran out of torches deep down", "i used my last torch and kept going"],
         try_=["followed the water up. water flows down, right", "i followed a stream uphill", "followed water back up"], try_react="wow",
         try_lines=["big brain", "smart", "nature compass"], out=["came out right by a village", "came up next to a village", "the stream took me to a village"],
         lucky=True, out_lines=["lucky", "nice", "the stream saved you"], out2=["slept there and found home in the morning", "they let me sleep in a bed. well, i took one", "slept in the village, walked home next day"],
         epi=None),
    dict(cave="one right by my house", mistake=["i didn't put any torches. it was right by home", "no torches. i thought i'd be quick", "i went in with no torches at all"],
         try_=["so i just picked a direction and dug", "dug in a straight line, for ages", "i picked one way and mined forward"], try_react="ask",
         try_lines=["and?", "where did you end up", "brave"], out=["popped out in my own basement", "i dug straight into my own basement", "came through the wall of my own house"],
         lucky=True, out_lines=["LOL", "your own house", "you were home the whole time"], out2=["i was like twenty blocks from home the whole time", "i was under my house the whole time", "i'd been right under my bed"],
         epi=["now there's a hole in my basement wall", "and now my basement has a door to the cave", "there's a hole in my wall now"],
         epi_r=["lol", "a cave door", "fix it"], epi2=["i put a door on it. it's a feature", "it's my back door now", "it's my shortcut to the mine now"]),
    dict(cave="a cave with a ravine through it", mistake=["i kept jumping down ledges i couldn't climb back up", "jumped down every drop, couldn't get back up", "i went down drops i couldn't climb"],
         try_=["i pillared up the ravine wall", "built a dirt tower up the ravine", "towered up the side of the ravine"], try_react="ask",
         try_lines=["how high", "careful", "go go"], out=["and at the top, the sun was coming up", "made it to the top at sunrise", "reached the top as the sun came up"],
         lucky=True, out_lines=["nice", "pretty", "made it"], out2=["best sunrise i ever saw", "i just sat there and watched it", "never been so happy to see the sun"],
         epi=None),
    dict(cave="a big one under the mountains", mistake=["i followed some iron and then more iron", "chased ore deeper and deeper", "one more iron, one more iron..."],
         try_=["i ate my last bread and kept walking", "no food left, kept walking", "i was starving and walking"], try_react="gasp",
         try_lines=["no food??", "oh no", "eat something"], out=["found a mineshaft with rails going up", "found an old mineshaft and followed the rails", "found rails and followed them"],
         lucky=True, out_lines=["the rails!", "nice", "rails go somewhere"], out2=["they took me out near the surface", "came out on a mountainside", "found the way out at the end of the rails"],
         epi=["and i still had all the iron", "and the best part, i kept all that iron", "and i still had my iron. a lot of it"],
         epi_r=["nice", "worth it then", "w"], epi2=["two stacks. worth getting lost for", "made a whole set of armor", "made armor, tools, a bucket"]),
], lost_cave)


# --- Lost at sea.
SEA_FOUND = {
    "mushroom": (["a mushroom island. red cows with mushrooms on them", "a mushroom island, mooshrooms everywhere", "an island with giant mushrooms"],
                 "wow", ["mushroom island!", "rare", "no way"],
                 ["no mobs spawn there. i slept like a baby", "slept on the beach, no mobs at all", "no monsters there. best night ever"]),
    "tree": (["a tiny island with one tree", "a little island with one tree on it", "an island, one tree, some sand"],
             "laugh", ["one tree lol", "cozy", "a deserted island"],
             ["i lived there for three days", "i stayed there three days, eating apples", "sat under that tree for days"]),
    "wreck": (["a shipwreck sticking out of the water", "an old shipwreck on a sandbar", "the top of a sunken ship"],
              "wow", ["loot!", "ooh", "nice"],
              ["slept in the ship. it was creepy", "spent the night in the captain's room", "slept in the wreck, all creaky"]),
    "village": (["a village right on the beach", "a fishing village, on the coast", "a little village by the water"],
                "relief", ["people!", "saved", "lucky"],
                ["they had beds. i slept so well", "slept in a bed for the first time in days", "they had food and a bed"]),
    "monument": (["an ocean monument. huge. glowing", "a giant underwater temple, glowing", "a monument, right under me"],
                 "gasp", ["guardians!", "nope", "row away"],
                 ["got blasted by guardians and rowed off", "a guardian zapped me, i left fast", "rowed away as fast as i could"]),
}


def lost_sea(n, v):
    found, f_react, f_lines, found2 = SEA_FOUND[v.found]
    p1 = P(
        A(f"i got lost at sea once", f"so i took a boat out {v.why}", f"ever been lost at sea? i went out {v.why}"),
        B(None, "lost at sea?", "brave", "no map?", "go on", "uh oh"),
        A("no map, no compass, nothing", "didn't bring a map. obviously", "i just rowed and rowed"),
        B("ask", "bad idea", "uh oh", "and?"),
        A("and the land disappears behind me", "then i look back and there's no land", "soon there's just water everywhere"),
        B("creepy", "nightmare", "just blue", "oh no"),
    )
    p2 = P(
        A(*v.trouble),
        B(v.tr_react, *v.tr_lines),
        A("i rowed all night. all of it", "kept rowing, didn't stop", "kept rowing in what i hoped was a straight line"),
        B("ask", "and?", "keep going", "did you find land"),
        A("then in the morning i see something", "when the sun comes up, i see it", "at sunrise, something on the water"),
    )
    p3 = P(
        A(*found),
        B(f_react, *f_lines),
        A(*found2),
        B(None, "and then?", "and home?", "ok", "go on"),
        A("but i still had to get home", "still had no idea where home was", "but where was home", chance=0.8),
    )
    p4 = P(
        A(*v.home),
        B(v.home_react, *v.home_lines),
        A(*v.home2),
        B("done", "the sea is huge", "what a trip", "never without a map"),
        A("i carry a map and a compass now", "maps, people. maps", "never leaving without a compass again", chance=0.6),
    )
    tell("lostsea_" + n, [p1, p2, p3, p4], ["ocean", "exploring", "places"], "the boat trip", leads=["ocean", "exploring", "places"],
         end_leads=["ocean", "exploring", "places"])


each([
    dict(why="to find a new island", found="mushroom",
         trouble=["night came and drowned started popping up", "at night the drowned came out", "night fell and the drowned were everywhere"], tr_react="gasp",
         tr_lines=["drowned at night", "row!", "oh no"], home=["i stayed. built a house there", "i never went back. i live there now", "i just moved there"],
         home_react="laugh", home_lines=["you moved??", "lol", "best plan"], home2=["got lost so hard i moved", "best mistake i ever made", "my old house is somewhere out there"]),
    dict(why="to fish", found="tree",
         trouble=["a storm came in, huge waves", "a thunderstorm hit, rain everywhere", "the sky went black and it stormed"], tr_react="gasp",
         tr_lines=["a storm at sea", "scary", "hold on"], home=["followed some dolphins back", "dolphins swam by and i followed them", "a bunch of dolphins showed up, i followed"],
         home_react="aw", home_lines=["dolphins!", "aww", "nature's map"], home2=["they took me right to the coast", "they led me to land", "they swam me home basically"]),
    dict(why="to see what was out there", found="wreck",
         trouble=["the sun set and i couldn't see anything", "it got so dark i couldn't see the water", "night came, total darkness"], tr_react="creepy",
         tr_lines=["dark sea is scary", "brr", "nope"], home=["found a map in the wreck. a real one", "the wreck had a map in a chest", "there was a map in the ship"],
         home_react="wow", home_lines=["a map!", "lucky", "the wreck saved you"], home2=["followed it all the way home", "used the map to get home", "map home. easy"]),
    dict(why="to find a jungle", found="village",
         trouble=["my boat hit something and broke", "i crashed my boat into ice and it broke", "my boat broke on a rock"], tr_react="gasp",
         tr_lines=["swimming??", "oh no", "no boat"], home=["traded them for a map. the explorer kind", "a cartographer sold me a map", "i bought a map from a villager"],
         home_react="relief", home_lines=["villagers to the rescue", "nice", "smart"], home2=["the map didn't show my house though", "it showed a mansion. not my house", "i found my way back eventually"]),
    dict(why="to explore the ocean", found="monument",
         trouble=["fog. well, it rained so hard i couldn't see", "rain so thick i couldn't see anything", "it started pouring, couldn't see"], tr_react="gasp",
         tr_lines=["no visibility", "oh no", "scary"], home=["i rowed toward the sunset. somehow it worked", "rowed in one direction for a day", "i just rowed until i saw trees"],
         home_react="relief", home_lines=["lucky", "made it", "phew"], home2=["landed miles from home and walked", "landed somewhere random and walked home", "walked the last bit home"]),
    dict(why="to look for a shipwreck", found="tree",
         trouble=["i ran out of food on the first day", "no food after day one", "ate my last bread the first night"], tr_react="gasp",
         tr_lines=["no food??", "oh no", "fish!"], home=["built a tower on the island so people could see me", "built a big tower of dirt and torches", "made a tall lit tower on it"],
         home_react="ask", home_lines=["did anyone come", "smart", "and?"], home2=["nobody came. but from the top i saw land", "from the top i could see the coast", "climbed it and saw my mountain"]),
], lost_sea)


# --- Lost in an old mineshaft.
def lost_mineshaft(n, v):
    p1 = P(
        A(f"found an old mineshaft once. {v.where}", f"ever been in a mineshaft? i found one {v.where}", f"mineshaft story. {v.where}"),
        B(None, "the wooden ones?", "cobwebs", "oh no", "spiders", "go on"),
        A("rails, wooden beams, cobwebs everywhere", "old rails going off in every direction", "planks and rails and so many cobwebs"),
        B("creepy", "cobwebs mean spiders", "creepy", "ugh webs"),
        A("so i go in. obviously", "and i go in, of course", "and of course i went in"),
        B("ask", "of course", "brave", "and?"),
    )
    p2 = P(
        A("it goes on forever. tunnel after tunnel", "every tunnel splits into three more", "it never ends. tunnels everywhere"),
        B(None, "they're huge", "classic mineshaft", "so big", "a maze", "go on"),
        A(*v.trouble),
        B(v.tr_react, *v.tr_lines),
        A(*v.trouble2, chance=0.85),
    )
    p3 = P(
        A("and by then i had no idea where i was", "then i realise i'm totally lost", "and that's when i notice i'm lost"),
        B("gasp", "of course", "oh no", "mineshafts do that"),
        A(*v.find),
        B(v.find_react, *v.find_lines),
        A(*v.find2, chance=0.85),
    )
    p4 = P(
        A(*v.out),
        B(v.out_react, *v.out_lines),
        A(*v.out2),
        B("done", "mineshafts are the worst", "what a trip", "good story"),
        A("i always bring shears to mineshafts now", "shears and a sword now. always", "i bring a bucket of water for the webs now", chance=0.6),
    )
    tell("mineshaft_" + n, [p1, p2, p3, p4], ["caves", "structures", "danger"], "the mineshaft", leads=["caves", "structures", "mining"],
         end_leads=["caves", "structures", "mobs"])


each([
    dict(where="in a ravine", trouble=["a cave spider spawner. tiny blue spiders", "cave spiders everywhere, the blue ones", "a spawner pumping out cave spiders"],
         tr_react="gasp", tr_lines=["poison!", "cave spiders are the worst", "nope"], trouble2=["got poisoned three times", "poison, poison, poison", "i was green the whole time"],
         find=["then a chest in a minecart", "found a minecart with a chest", "a chest minecart, just sitting there"], find_react="wow",
         find_lines=["loot!", "nice", "what was in it"], find2=["name tag and some bread", "a name tag and melon seeds", "rails and a name tag"],
         out=["followed the rails until i saw light", "rode the minecart out. well, pushed it", "followed rails up to daylight"], out_react="relief",
         out_lines=["nice", "out!", "phew"], out2=["came out by a lake, miles away", "ended up far from home but alive", "came out somewhere totally new"]),
    dict(where="in a badlands hill", trouble=["it was half in the open air, all broken", "the floor was missing in places", "holes in the floor everywhere"],
         tr_react="gasp", tr_lines=["don't fall", "careful", "oh no"], trouble2=["i fell through one. two floors down", "fell through a gap, two levels", "dropped down a hole into another tunnel"],
         find=["then i saw gold. the walls were full of it", "and then: gold everywhere in the walls", "then i noticed gold ore everywhere"], find_react="wow",
         find_lines=["badlands gold", "nice", "rich"], find2=["mined a whole stack", "filled my bag with gold", "took every bit"],
         out=["dug out the side of the hill", "dug straight out through the hill", "tunneled out sideways"], out_react="relief",
         out_lines=["easy", "nice", "smart"], out2=["came out at sunset on an orange hill", "out into the orange hills at sunset", "came out to a really pretty view"]),
    dict(where="deep underground", trouble=["my torches ran out", "ran out of torches", "no more torches"],
         tr_react="gasp", tr_lines=["in a mineshaft??", "oh no", "make more"], trouble2=["broke the old fences for sticks and made more", "made more torches from the fence posts", "turned the old fences into torches"],
         find=["then i hear a zombie groan somewhere", "then a zombie groans in the dark", "then groaning. zombies"], find_react="creepy",
         find_lines=["nope", "creepy", "uh oh"], find2=["killed it in the dark, just swinging", "swung at the noise until it stopped", "fought it blind"],
         out=["found a ladder i'd placed. my own ladder", "found my own ladder from earlier", "found the ladder i came down"], out_react="relief",
         out_lines=["your ladder!", "phew", "lucky"], out2=["climbed out and lay on the grass", "lay on the grass for a while", "so happy to see sky"]),
    dict(where="under my town", trouble=["cobwebs everywhere. i got stuck in one", "got stuck in a cobweb", "a cobweb grabbed me"],
         tr_react="gasp", tr_lines=["webs are the worst", "cut it", "oh no"], trouble2=["and a spider dropped in next to me", "a spider showed up while i was stuck", "spider. right there. while i'm stuck"],
         find=["got out and found a whole room of rails", "found a room full of rails", "found stacks of rails"], find_react="wow",
         find_lines=["free rails", "nice", "rails!"], find2=["took them all. thirty rails", "took every rail", "pocketed them all"],
         out=["built a railway home with them", "made a rail line home", "laid them all the way home"], out_react="laugh",
         out_lines=["lol", "smart", "a railway!"], out2=["there's a railway from town to that mine now", "now there's a rail line from town", "it's the town railway now"]),
    dict(where="in a jungle", trouble=["it was so dark. darker than anywhere", "pitch black and twisty", "dark tunnels everywhere"],
         tr_react="creepy", tr_lines=["creepy", "nope", "brr"], trouble2=["a skeleton kept shooting from the dark", "arrows coming out of nowhere", "arrows from a skeleton i couldn't see"],
         find=["found a bit where it opened into a cave with lava", "found a huge cave lit by lava", "came out into a big lava cave"], find_react="gasp",
         find_lines=["lava", "careful", "oh no"], find2=["followed the light from the lava", "the lava light helped me see", "used the lava glow to find my way"],
         out=["climbed up a waterfall out", "swam up a waterfall to the surface", "went up a water column to the top"], out_react="wow",
         out_lines=["nice", "smart", "a waterfall!"], out2=["came out in the jungle at night. great", "jungle at night. ugh", "came out in the dark jungle"]),
], lost_mineshaft)

# =====================================================================================================================
# FUNNY MISHAPS
# =====================================================================================================================

# --- Bragging, then falling in a hole.
def brag_fall(n, v):
    p1 = P(
        A(f"so i was bragging to a friend about {v.brag}", f"i was telling someone all about {v.brag}", f"ever brag and instantly regret it? i was going on about {v.brag}"),
        B("smirk", "oh no", "i know where this goes", "pride before the fall"),
        A(f"like really bragging. {v.quote}", f"i actually said: {v.quote}", f"and i go, {v.quote}"),
        B("laugh", "you didn't", "famous last words", "oh no"),
        A("and they said: prove it", "and they go: ok, show me then", "so they said: go on then"),
        B("ask", "and you did", "uh oh", "and?"),
    )
    p2 = P(
        A(f"so i go to {v.stunt}", f"i said watch this and went to {v.stunt}", f"watch this, i said, and started to {v.stunt}"),
        B(None, "watch this. the worst words", "oh no", "here we go", "go on", "watch this lol"),
        A("first bit went perfect", "and it's going great at first", "i'm doing great, looking cool"),
        B(None, "and then", "uh oh", "ok", "i'm waiting for it", "go on"),
        A("then i look back at them to make sure they're watching", "i turn to check they're watching", "so i look back to see their face"),
    )
    p3 = P(
        A(*v.fail),
        B("laugh", *v.fail_lines),
        A(*v.fail2),
        B("laugh", "i'd never live that down", "lmao", "perfect"),
        A("they laughed so hard they fell over", "they were laughing so much", "i heard them laughing from down there", chance=0.8),
    )
    p4 = P(
        A(*v.after),
        B(None, *v.after_lines),
        A(*v.after2),
        B("done", "that's going on your grave", "never brag", "legendary"),
        A("never bragging again", "i don't brag anymore", "humble now. very humble", chance=0.6),
    )
    tell("brag_" + n, [p1, p2, p3, p4], ["jokes", "friends", "danger"], "the bragging", leads=["jokes", "friends", "brag"],
         end_leads=["jokes", "friends", "brag"])


each([
    dict(brag="my parkour", quote="i never miss a jump", stunt="jump across a ravine",
         fail=["and i walked right off the edge", "missed the jump by a mile", "fell straight down the ravine"],
         fail_lines=["NOOO", "lmao", "never miss lol"], fail2=["landed in water at the bottom. alive. embarrassed", "landed in the river at the bottom", "splashed down at the bottom"],
         after=["they made a sign at the ravine", "they put up a sign where i fell", "there's a sign at that ravine now"],
         after_lines=["what does it say", "a sign lol", "oh no"], after2=["it says: i never miss", "it says: never miss. lol", "it says: watch this"]),
    dict(brag="never dying", quote="i haven't died in a hundred days", stunt="walk across my lava moat on a one block bridge",
         fail=["and i stepped off the side. into the lava", "missed a step and went straight in", "and fell right into the lava"],
         fail_lines=["NO", "a hundred days lol", "one day"], fail2=["day one again", "back to zero days", "now it's been zero days"],
         after=["they still count my days out loud", "they ask me how many days every time", "they keep asking how many days now"],
         after_lines=["lol", "that's cruel", "how many now"], after2=["they started counting from that day", "it's a running joke now", "i can never live it down"]),
    dict(brag="my bridge building", quote="i can bridge across anything", stunt="bridge across a lake sneaking backwards",
         fail=["placed one block wrong and fell in the lake", "forgot to sneak. fell straight in", "stepped back into nothing. splash"],
         fail_lines=["lmao", "into the lake", "classic"], fail2=["climbed out, soaking", "swam back to shore", "dripping wet, everyone laughing"],
         after=["they built the rest of the bridge for me", "they finished my bridge. perfectly", "they bridged it in like a minute"],
         after_lines=["oof", "rude", "lol"], after2=["it's still there. their bridge, not mine", "everyone uses their bridge now", "the bridge has their name on it"]),
    dict(brag="my mining", quote="i can find diamonds blindfolded", stunt="find diamonds right then and there",
         fail=["dug straight down. into a cave. fell twenty blocks", "dug down and fell into a cave", "dug straight down, which you never do, and fell"],
         fail_lines=["never dig straight down", "lmao", "rule one"], fail2=["landed next to a skeleton", "landed right by a zombie", "landed in the dark with mobs"],
         after=["and then i saw diamonds right there", "and right in front of me, diamonds", "and there were diamonds down there"],
         after_lines=["no way", "LOL", "it worked??"], after2=["so technically i was right", "so i found them. sort of", "so i won. kind of"]),
    dict(brag="my sword skills", quote="i could beat any mob with a stone sword", stunt="fight a zombie with a stone sword",
         fail=["swung, missed, tripped into a cactus", "missed every swing and walked into a cactus", "swung, missed, fell back into a cactus"],
         fail_lines=["a cactus lol", "lmao", "the cactus wins"], fail2=["the zombie and the cactus beat me together", "the zombie got me while i was in the cactus", "died to a zombie and a cactus"],
         after=["they call me cactus now", "my nickname is cactus now", "they named me cactus"],
         after_lines=["cactus lol", "fitting", "love it"], after2=["even villagers call me that, i swear", "everyone calls me cactus", "it stuck. forever"]),
    dict(brag="my boat skills", quote="i'm the best boater around", stunt="boat down a river through the rapids",
         fail=["crashed into a lily pad. a lily pad", "hit a lily pad and the boat broke", "and the boat broke on a lily pad"],
         fail_lines=["a lily pad lol", "the most dangerous plant", "lmao"], fail2=["floated there with no boat", "i was just floating there", "bobbing in the river, no boat"],
         after=["they gave me a lily pad as a gift later", "they put a lily pad in my chest", "they left a lily pad on my door"],
         after_lines=["lol", "evil", "perfect gift"], after2=["it's on my wall now", "i put it in a pot. it's my enemy", "i keep it to stay humble"]),
], brag_fall)


# --- Eggs everywhere, chickens everywhere.
def egg_chickens(n, v):
    p1 = P(
        A(f"so i had a stack of eggs and {v.idea}", f"ok so i had sixteen eggs and {v.idea}", f"once i had a pile of eggs and {v.idea}"),
        B(None, "oh no", "eggs?", "go on", "what could go wrong", "lol"),
        A("so i'm throwing them one by one", "threw them all, one after another", "i chucked every egg"),
        B("laugh", "eggs hatch", "you know they hatch right", "uh oh"),
        A("and then: peep", "and then i hear a little peep", "and then a tiny chicken pops out"),
        B("laugh", "LOL", "here we go", "a baby chicken"),
    )
    p2 = P(
        A("then another. and another", "then two more. then three", "peep. peep. peep peep"),
        B("laugh", "how many", "chicken time", "oh no"),
        A(f"by the end there were like {v.count} chickens", f"i had {v.count} chickens in like a minute", f"{v.count} baby chickens running around"),
        B("laugh", f"{v.count}??", "lmao", "chicken army"),
        A("and they grew up. and laid more eggs", "and they grow up and lay more eggs", "then they grew up and started laying"),
    )
    p3 = P(
        A(*v.twist),
        B(v.tw_react, *v.tw_lines),
        A(*v.twist2),
        B("laugh", "chaos", "lol", "i'm crying"),
        A(*v.twist3, chance=0.8),
    )
    p4 = P(
        A(*v.end),
        B(None, *v.end_lines),
        A(*v.end2),
        B("done", "chicken story of the year", "lol", "never throw eggs"),
        A("i don't throw eggs anymore", "eggs stay in the chest now", "i respect eggs now", chance=0.6),
    )
    tell("eggs_" + n, [p1, p2, p3, p4], ["animals", "jokes", "farming"], "the chickens", leads=["animals", "jokes", "farming"],
         end_leads=["animals", "jokes", "farming"])


each([
    dict(idea="wanted to see how far i could throw", count="nine",
         twist=["they got into my house. all of them", "they wandered into my house through the door", "they went inside my house"],
         tw_react="laugh", tw_lines=["in the house", "lol", "chicken house"], twist2=["chickens on the bed, chickens on the chests", "chickens everywhere. on the table. on the bed", "a chicken on every block"],
         twist3=["feathers everywhere", "so many feathers", "my house was a coop"],
         end=["so i built them a coop", "built a coop and walked them in", "made them a proper coop outside"], end_lines=["nice", "the coop!", "good owner"],
         end2=["best egg supply in town now", "now i have more eggs than anyone", "i have eggs for days now"]),
    dict(idea="was bored", count="twelve",
         twist=["a fox showed up", "then a fox came out of the woods", "a fox spotted them"],
         tw_react="gasp", tw_lines=["the fox!", "oh no", "run chickens"], twist2=["i chased the fox. the chickens chased me", "i ran after the fox, the chickens followed me", "it was a parade. me, fox, chickens"],
         twist3=["it was a whole parade", "we ran in circles", "round and round the field"],
         end=["the fox got one. i got eleven back", "the fox took one and ran", "lost one to the fox, saved the rest"], end_lines=["rip one", "eleven is good", "the fox ate well"],
         end2=["named the fence round them fox-proof", "built a fox-proof fence", "built a big fence after"]),
    dict(idea="messed up making a cake", count="seven",
         twist=["one of the chickens had a baby zombie on it", "then a baby zombie jumped on one of the chickens", "a baby zombie climbed on a chicken"],
         tw_react="gasp", tw_lines=["a chicken jockey!", "noooo", "the worst mob"], twist2=["chicken jockey. fastest thing alive", "a chicken jockey, zooming around", "it zoomed round my yard"],
         twist3=["it got me twice before i hit it", "it hit me so many times", "i couldn't even catch it"],
         end=["finally killed the zombie. kept the chicken", "killed the zombie, the chicken lived", "beat the zombie. the chicken stayed"], end_lines=["you kept it??", "a war veteran chicken", "lol"],
         end2=["that chicken is my favourite now", "it's called jockey now", "it has a little fence all to itself"]),
    dict(idea="threw them at a friend for fun", count="ten",
         twist=["they threw them back", "and they threw eggs right back at me", "they started throwing back"],
         tw_react="laugh", tw_lines=["egg war", "lol", "fair"], twist2=["full egg war. chickens everywhere", "egg fight across the whole field", "eggs flying both ways"],
         twist3=["we were laughing so hard", "couldn't stop laughing", "we both fell over laughing"],
         end=["by the end we had thirty chickens between us", "thirty chickens by sunset", "the field was full of chickens"], end_lines=["thirty lol", "a chicken town", "chaos"],
         end2=["we share a chicken farm now", "now we run a chicken farm together", "we built a farm for them together"]),
    dict(idea="wanted to see if they'd hatch", count="three",
         twist=["only three hatched. i was sad", "only got three chickens. disappointing", "three chickens. that's it"],
         tw_react="laugh", tw_lines=["only three", "lol", "rng"], twist2=["so i got more eggs from them and threw those", "so i threw their eggs too", "then threw the next eggs"],
         twist3=["and those hatched too", "more chickens", "and more, and more"],
         end=["now i have fifty chickens", "it's like fifty now", "i've lost count. fifty?"], end_lines=["fifty??", "too many", "lmao"],
         end2=["i can hear them from inside my house", "the noise never stops", "they cluck all night"]),
], egg_chickens)


# --- It named an animal, then got hungry.
def named_food(n, v):
    p1 = P(
        A(f"i named a {v.animal} once. called it {v.name}", f"had a {v.animal} called {v.name}. i named it", f"so there was this {v.animal}. i named it {v.name}"),
        B("aw", f"{v.name}!", "cute", "aww"),
        A(f"{v.name} followed me everywhere", f"{v.name} lived by my house", f"{v.name} would wait by my door"),
        B("aw", "so cute", "best friend", "love that"),
        A("and then one day i was really hungry", "but then i ran out of food", "then came a really hungry week"),
        B("gasp", f"no. not {v.name}", "don't you dare", "oh no"),
    )
    p2 = P(
        A("no food. nothing. just me and the animals", "chests empty. stomach empty", "starving. every chest empty"),
        B(None, "eat bread", "go fishing", "anything else", "no", "find berries"),
        A(f"and {v.name} walks up to me", f"and there's {v.name}, looking at me", f"{v.name} just walks over"),
        B("sad", "no", "you can't", "look away"),
        A("i couldn't do it", "i looked at it. it looked at me", "i stood there for ages", chance=0.85),
    )
    p3 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B(None, *v.end2_lines),
        A(*v.end3),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A(*v.epi),
            B(None, *v.epi_lines),
            A(*v.epi2),
            B("done", "what a story", "farm life", "aw"),
            A("i never name food now", "names are for pets. not food", "if it has a name, it's family", chance=0.6),
        )
    tell("named_" + n, [p1, p2, p3, p4], ["animals", "food", "jokes"], v.name, leads=["animals", "food", "farming"],
         end_leads=["animals", "food", "jokes"])


each([
    dict(animal="pig", name="bacon",
         end=["i went fishing instead. caught a cod", "went and fished all day instead", "i went fishing so i wouldn't have to"], end_react="aw",
         end_lines=["good", "bacon lives", "aww"], end2=["bacon is still alive. that name aged badly", "bacon is fine. he doesn't know his name is food", "he lives. ironic name though"],
         end2_lines=["lol the name", "you named it bacon", "lmao"], end3=["i didn't think it through", "naming is hard", "i named him before i got attached"],
         epi=None),
    dict(animal="cow", name="daisy",
         end=["i hit the wrong cow. by accident. it was daisy", "i swung at another cow and hit daisy", "misclick. it was daisy"], end_react="gasp",
         end_lines=["NO", "you didn't", "daisy!!"], end2=["and she dropped steak", "and there was steak on the ground", "and i got a steak"],
         end2_lines=["did you eat it", "oh no", "the steak"], end3=["i ate it. crying", "ate it. it was the best steak. i hate myself", "i ate it. sorry daisy"],
         epi=["i named the next cow daisy two", "next cow got called daisy two", "there's a daisy two now"], epi_lines=["lol", "the legacy", "aw"],
         epi2=["i guard her with my life", "she has her own field", "nobody touches daisy two"]),
    dict(animal="chicken", name="nugget",
         end=["nugget ran off into the woods", "nugget ran away, like he knew", "nugget fled. smart chicken"], end_react="laugh",
         end_lines=["he knew", "smart chicken", "lol"], end2=["i found him a week later with a fox", "saw him later with a fox. friends?", "later he was hanging out with a fox"],
         end2_lines=["a fox??", "uh oh", "friends?"], end3=["they were friends. i don't get it either", "the fox didn't touch him. weird", "best friends. no idea how"],
         epi=None),
    dict(animal="sheep", name="fluffy",
         end=["i realised i could just shear her. wool doesn't fill you up though", "i sheared her instead. you can't eat wool", "sheared her. still hungry. but she lived"], end_react="laugh",
         end_lines=["wool isn't food", "lol", "at least she's alive"], end2=["so i ate rotten flesh instead", "i ate rotten flesh. for fluffy", "rotten flesh it was"],
         end2_lines=["gross", "the sacrifice", "respect"], end3=["got food poisoning. worth it", "felt sick. worth it for fluffy", "worth it"],
         epi=["fluffy had a lamb later", "a few days later, fluffy had a baby", "later fluffy had a lamb"], epi_lines=["aww", "a lamb!", "cute"],
         epi2=["named it fluffy two. obviously", "it's called little fluffy", "it's fluffy junior"]),
    dict(animal="rabbit", name="stew",
         end=["i let stew go. into the wild", "i opened the fence and let stew go", "set stew free"], end_react="aw",
         end_lines=["aww", "free stew", "good choice"], end2=["then i ate carrots for a week", "lived on carrots for days", "ate the carrots i'd grown for stew"],
         end2_lines=["the carrots lol", "fair", "good trade"], end3=["stew is out there somewhere. free", "stew lives free", "somewhere, stew is happy"],
         epi=None),
    dict(animal="pig", name="sir oink",
         end=["i put a saddle on him instead and rode to a village", "rode sir oink to a village for food", "saddled him and rode to get food"], end_react="laugh",
         end_lines=["lol", "sir oink the noble steed", "a pig ride"], end2=["the villagers stared. i traded for bread", "traded for bread, rode home on a pig", "got bread and rode him home"],
         end2_lines=["lmao", "noble", "classic"], end3=["sir oink is a hero", "he's a knight now", "he saved us both"],
         epi=["now i ride him everywhere", "he's my main ride now", "i take him on every trip"], epi_lines=["lol", "pig rider", "slowest ride ever"],
         epi2=["slowest horse in the world", "takes forever but i love him", "we get there eventually"]),
], named_food)


# --- A bed in the nether.
def bed_nether(n, v):
    p1 = P(
        A("first time i went to the nether, i was so tired", "my first nether trip, it got late", "ok so, first night in the nether"),
        B(None, "oh no", "don't tell me", "you didn't", "i know where this goes", "uh oh"),
        A(f"and i thought, i'll just sleep here. {v.where}", f"so i put my bed down {v.where}", f"i put a bed down {v.where} to sleep"),
        B("gasp", "NO", "beds blow up there", "noooo"),
        A("i didn't know. nobody told me", "i had no idea", "nobody tells you"),
        B("ask", "and?", "what happened", "oh no"),
    )
    p2 = P(
        A("i click the bed. BOOM", "click. BOOM", "lay down, and BOOM"),
        B("laugh", "lmao", "every newbie", "classic"),
        A("the biggest explosion i've ever seen", "it was bigger than tnt", "fire and boom everywhere"),
        B("gasp", "beds are bombs there", "yikes", "rip"),
        A(*v.result),
    )
    p3 = P(
        A(*v.after),
        B(v.after_react, *v.after_lines),
        A(*v.after2),
        B("laugh", "the nether teaches", "lesson learned", "lol"),
        A("nobody warns you. nobody", "someone should put up a sign", "they need a warning on beds", chance=0.6),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A("but here's the funny part", "you know what's funny now?", "but the twist is"),
            B("ask", "what", "tell me", "go on"),
            A(*v.epi),
            B("laugh", "lol", "full circle", "bed bombing"),
            A(*v.epi2, chance=0.8),
        )
    tell("netherbed_" + n, [p1, p2, p3, p4], ["nether", "jokes", "sleep"], "the bed", leads=["nether", "jokes", "danger"],
         end_leads=["nether", "jokes", "sleep"])


each([
    dict(where="right next to the portal",
         result=["blew up me and the portal", "it took the portal with it", "the portal broke"],
         after=["respawned at home. my stuff was in the nether", "woke up at home, stuff stuck in the nether", "i respawned in the overworld, everything in the nether"],
         after_react="gasp", after_lines=["and the portal was broken", "oh no", "how did you get back"], after2=["had to build a whole new portal to get back", "made a new portal, came out somewhere else", "built another portal, landed miles away"],
         epi=["now i use beds to blow up ancient debris", "now i blow up netherite with beds", "beds are my mining tool in the nether now"],
         epi2=["i'm a bed bomber now", "beds are tools", "best way to get netherite"]),
    dict(where="on a netherrack ledge",
         result=["blew me off the ledge into lava", "launched me straight into the lava", "sent me flying into the lava sea"],
         after=["everything burned. all of it", "lost all my stuff in the lava", "everything i had, gone in lava"],
         after_react="sad", after_lines=["all of it?", "rip", "noooo"], after2=["my first trip, and i lost everything", "first trip ever, total loss", "worst first trip possible"],
         epi=None, epi2=None),
    dict(where="by a fortress",
         result=["blew a hole in the fortress", "the fortress wall had a new door", "the fortress lost a whole wall"],
         after=["and every blaze in there came out", "blazes everywhere, all angry", "and blazes came pouring out"],
         after_react="gasp", after_lines=["blazes!", "run", "oh no"], after2=["died to fireballs, respawned at home", "they burned me up", "fireballs everywhere. dead"],
         epi=["but the hole i made? best way in now", "now that hole is how i get into the fortress", "my bed hole is the fortress door now"],
         epi2=["i call it the bedroom", "it's called the bedroom door", "everyone uses my bed door"]),
    dict(where="in a little hole i dug",
         result=["blew up the hole, and me", "the hole got bigger. a lot bigger", "the hole and me, gone"],
         after=["i respawned in my bed at home. the one i'd brought was gone", "woke up at home. my nether bed was gone", "respawned at home with no bed in the nether"],
         after_react="laugh", after_lines=["lol", "rip bed", "classic"], after2=["so i went back and found a bigger hole", "went back. big crater", "came back to a huge crater"],
         epi=None, epi2=None),
    dict(where="on a bridge over lava",
         result=["blew up the bridge", "the bridge was gone. me too", "no more bridge"],
         after=["i fell into the lava sea with my good sword", "took my sword into the lava with me", "my sword and me went in the lava"],
         after_react="sad", after_lines=["your sword", "noo", "rip sword"], after2=["respawned at home with nothing", "back home, sword gone", "nothing left. not even a sword"],
         epi=["i tell every newbie now. never sleep there", "now i warn every newbie i meet", "i've told twenty people now"],
         epi2=["two of them did it anyway", "one did it anyway. right in front of me", "and some still try it"]),
], bed_nether)


# --- The night it left the door open.
def door_open(n, v):
    p1 = P(
        A("i left my door open one night", "one night i forgot to close my door", "ever leave your door open at night? don't"),
        B(None, "oh no", "uh oh", "never", "what came in", "doors exist for a reason"),
        A("i was so tired i just went to bed", "went straight to bed, door wide open", "just fell into bed and slept"),
        B("ask", "and?", "something came in", "oh no"),
        A(f"i woke up to {v.sound}", f"and in the night i hear {v.sound}", f"middle of the night: {v.sound}"),
        B("creepy", "nope", "no", "something's inside"),
    )
    p2 = P(
        A(*v.who),
        B(v.who_react, *v.who_lines),
        A(*v.doing),
        B(v.doing_react, *v.doing_lines),
        A(*v.doing2, chance=0.85),
    )
    p3 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B("done", "doors. close them", "lol", "never forget the door") if not getattr(v, "good", False) else B("aw", "lol", "best accident"),
        A("i check the door three times now", "door closed, every night, twice", "i close the door before i even sit down", chance=0.6)
        if not getattr(v, "good", False) else None,
    )
    p4 = None
    if v.epi:
        p4 = P(
            A(*v.epi),
            B(None, *v.epi_lines),
            A(*v.epi2),
            B(None, "lol", "perfect", "nice", "love it"),
            A("so maybe leaving it open wasn't so bad", "best mistake i ever made", "so it worked out", chance=0.6),
        )
    tell("door_" + n, [p1, p2, p3, p4], ["home", "mobs", "jokes"], "the open door", leads=["home", "mobs", "night"],
         end_leads=["home", "jokes", "mobs"], needs=["a:has_house"], weight=0.9)


each([
    dict(sound="tiny footsteps", who=["a baby zombie. in my house", "a baby zombie was running round my room", "baby zombie. inside. fast"], who_react="gasp",
         who_lines=["the worst mob", "baby zombies are so fast", "nooo"], doing=["it was running in circles round my bed", "it ran round and round my bed", "it zoomed round the room"],
         doing_react="laugh", doing_lines=["lol", "zoom zoom", "chaos"], doing2=["i couldn't hit it, it was too fast", "i kept missing it", "every swing missed"],
         end=["finally got it in a corner", "cornered it by the chests", "got it stuck in the corner"], end_react="relief", end_lines=["finally", "nice", "phew"],
         end2=["it had a carrot. it dropped a carrot", "it dropped a potato. a gift i guess", "it dropped rotten flesh"], epi=None),
    dict(sound="a hiss", who=["a creeper. at the foot of my bed", "creeper. standing over my bed", "a creeper, right by my bed"], who_react="gasp",
         who_lines=["NO", "inside??", "run"], doing=["i jumped out the window", "dove out the window", "jumped straight out the window"],
         doing_react="ask", doing_lines=["did it blow", "and?", "smart"], doing2=["BOOM. behind me", "and boom, behind me", "it went off as i landed"],
         end=["my bedroom has a hole in it now", "half the bedroom gone", "bed gone, wall gone"], end_react="sad", end_lines=["oh no", "but you lived", "rip bedroom"],
         end2=["but i lived. that's what counts", "i lived though", "but i was alive"],
         epi=["i rebuilt it with a big window where the hole was", "i put a window where the hole was", "the hole became a window"],
         epi_lines=["nice", "a happy accident", "lol"], epi2=["best view from my bed now", "i can see the sunrise from bed now", "wake up to the sunrise now"]),
    dict(good=True, sound="a villager going hrmm", who=["a villager. in my house", "a villager had walked in", "a villager standing in my kitchen"], who_react="laugh",
         who_lines=["a villager??", "lol", "a guest"], doing=["it was trying to sleep in my bed", "it was eyeing my bed", "it walked straight to my bed"],
         doing_react="laugh", doing_lines=["the audacity", "lol", "it wants your bed"], doing2=["i was in it. it just stood there", "i was in it, so it just stared", "it stared at me in my bed"],
         end=["so i got up and it took the bed", "i got up for a second and it jumped in", "the second i got up it took my bed"], end_react="laugh",
         end_lines=["lmao", "rent free", "it stole your bed"], end2=["i slept on the floor. in my own house", "slept on the floor next to it", "i slept on a chest"],
         epi=["i built it its own bed next door", "built the villager a little house", "made it a house of its own"], epi_lines=["aw", "a neighbour", "nice"],
         epi2=["now we're neighbours", "it waves at me every morning", "it lives next door now"]),
    dict(sound="a rattle of bones", who=["a skeleton in the doorway", "a skeleton standing in my door", "skeleton. right in the doorway"], who_react="gasp",
         who_lines=["inside??", "skeletons don't go inside", "oh no"], doing=["it shot at me in bed", "arrows flying across my room", "it started shooting at my bed"],
         doing_react="gasp", doing_lines=["in bed??", "no", "hide"], doing2=["arrows stuck in my wall everywhere", "my walls full of arrows", "arrows in my paintings"],
         end=["hit it with my pillow. well, my sword", "got up and fought it in my pajamas", "fought it right there in my room"], end_react="relief",
         end_lines=["you won", "nice", "phew"], end2=["took the arrows out of my walls. free arrows", "collected twenty arrows from the walls", "got free arrows at least"], epi=None),
    dict(good=True, sound="purring", who=["a stray cat, sleeping on my bed", "a cat. on my bed", "a cat curled up on my bed"], who_react="aw",
         who_lines=["aww", "a cat!", "cute"], doing=["it wouldn't move", "it refused to move", "it took the whole bed"],
         doing_react="laugh", doing_lines=["cats do that", "lol", "it's the cat's bed now"], doing2=["i slept on the edge", "i got one corner", "slept curled round it"],
         end=["fed it some fish. now it's mine", "gave it a fish and it stayed", "tamed it with fish"], end_react="aw", end_lines=["aww", "family", "cute"],
         end2=["it sleeps on my bed every night now", "it still takes the bed", "it's the boss of the house now"],
         epi=["and it brings me presents every morning", "and every morning it leaves me a present", "and it gives me gifts now"], epi_lines=["what presents", "aww", "cute"],
         epi2=["string. feathers. once a phantom membrane", "feathers, string, rabbit feet", "string mostly. sometimes a feather"]),
], door_open)

# =====================================================================================================================
# ANIMALS AND PETS
# =====================================================================================================================

# --- The wolf that saved it.
WOLF_END = {
    "saved": (["it jumped in and took it down", "my wolf went straight for it", "the wolf leapt in, teeth first"],
              "wow", ["good dog", "the wolf!", "hero"],
              ["killed it. then sat down next to me like nothing happened", "won the fight, then sat down by me", "finished it, then wagged its tail"],
              "aw", ["best dog", "aww", "loyal"],
              ["fed it all my steak. it earned it", "gave it every bit of meat i had", "it got all my pork chops that night"]),
    "lost": (["it jumped in front of me", "my wolf jumped in the way", "it threw itself at it to protect me"],
             "sad", ["no", "oh no", "brave dog"],
             ["it saved me. it didn't make it", "i lived. it didn't", "it got me out. but it was gone after"],
             "sad", ["i'm so sorry", "the best dog", "that's so sad"],
             ["i buried its collar under a flower", "there's a flower where it fell", "i put a sign there"]),
    "both": (["we fought it together. me and my wolf", "me and the wolf, side by side", "we took it on together"],
             "wow", ["teamwork", "a duo", "nice"],
             ["it bit, i swung. we won", "i hit, it bit, done", "between us it didn't stand a chance"],
             "aw", ["partners", "aww", "best team"],
             ["shared a steak after. half each", "split my food with it after", "gave it the biggest bone i had"]),
}


def wolf(n, v):
    end, e_react, e_lines, end2, e2_react, e2_lines, end3 = WOLF_END[v.end]
    p1 = P(
        A(f"i had a wolf once. called it {v.name}", f"let me tell you about my wolf, {v.name}", f"my old wolf {v.name}. best dog ever"),
        B("aw", f"{v.name}!", "a doggo", "tell me about it"),
        A(f"took {v.bones} bones to tame it", f"tamed it with {v.bones} bones. stubborn", f"it ate {v.bones} bones before it trusted me"),
        B("laugh", f"{v.bones}??", "worth it", "picky dog"),
        A("then it got its red collar and that was it. best friends", "red collar, wagging tail, best friends", "the collar popped on and it was mine"),
        B("aw", "aww", "love that", "the collar moment"),
    )
    p2 = P(
        A("it went everywhere with me. caves, forests, everywhere", "it followed me into every cave", "went everywhere i went"),
        B("aw", "loyal", "good boy", "cute"),
        A(f"one day we're out in {v.place}", f"so one day we're in {v.place}", f"we were walking through {v.place}"),
        B("ask", "uh oh", "go on", "and?"),
        A(*v.danger),
    )
    p3 = P(
        A(*v.danger2),
        B("gasp", "oh no", "run", "get out of there"),
        A(*end),
        B(e_react, *e_lines),
        A(*end2, chance=0.9),
    )
    p4 = P(
        A(*end3),
        B(e2_react, *e2_lines),
        A(*v.after),
        B("done" if v.end != "lost" else "sad", "dogs are the best", "a real friend", "good dog"),
        A(*v.after2, chance=0.7),
    )
    p5 = None
    if v.epi:
        p5 = P(
            A(*v.epi),
            B("aw", "puppies!", "aww", "the family grows"),
            A(*v.epi2),
            B("aw", "cute", "love that", "a whole pack"),
            A("i'd do anything for those dogs", "dogs are family", "best pack in the world", chance=0.6),
        )
    tell("wolf_" + n, [p1, p2, p3, p4, p5], ["pets", "animals", "fighting"], v.name, leads=["pets", "animals", "feelings"],
         end_leads=["pets", "feelings", "memories"])


each([
    dict(name="biscuit", bones="eight", place="a snowy forest", end="saved",
         danger=["and a skeleton starts shooting at us", "arrows. a skeleton on a hill", "a skeleton shows up, bow drawn"],
         danger2=["i'm on two hearts. it's still shooting", "i'm down to two hearts", "it's winning. i'm almost dead"],
         after=["biscuit lived to be super old", "biscuit was with me for years", "we went on for ages after"],
         after2=["still the best dog ever", "i miss that dog", "best dog i ever had"],
         epi=["then biscuit met another wolf", "later biscuit found a friend", "then i fed biscuit and another wolf some meat"],
         epi2=["three puppies. tiny ones", "two pups. they follow me now", "puppies everywhere"]),
    dict(name="shadow", bones="three", place="a dark forest at night", end="lost",
         danger=["and a zombie with an iron sword comes out", "a zombie holding a sword walks up", "an armored zombie, sword out"],
         danger2=["it got me down to one heart", "it hit me down to one heart", "one heart left, zombie swinging"],
         after=["i didn't tame another wolf for a long time", "took me ages to get another dog", "couldn't get another dog for months"],
         after2=["i still carry a bone, just in case", "i think about shadow a lot", "shadow was the best"], epi=None),
    dict(name="pepper", bones="five", place="a big taiga", end="both",
         danger=["and a pillager patrol walks out of the trees", "pillagers. a whole patrol", "a pillager patrol with a banner"],
         danger2=["crossbows pointing at us", "four of them, crossbows up", "arrows flying already"],
         after=["got the captain's banner too", "took the banner off the captain", "picked up their banner"],
         after2=["it's on my wall. pepper's banner", "i call it pepper's flag", "pepper's trophy"], epi=None),
    dict(name="ghost", bones="four", place="a cave", end="saved",
         danger=["and a cave spider jumps out at us", "the little blue spiders come out", "cave spiders. out of nowhere"],
         danger2=["i'm poisoned and swinging blind", "poisoned, can't see, swinging", "poison ticking, one heart"],
         after=["ghost was all white. hard to see in the snow", "ghost was so fluffy", "ghost loved caves after that"],
         after2=["the best cave buddy", "always took ghost mining", "ghost was my mining partner"],
         epi=["ghost had puppies later. four", "later ghost had four pups", "ghost became a mom"],
         epi2=["all white like her", "every one fluffy and white", "they all look like ghosts"]),
    dict(name="rusty", bones="six", place="a swamp", end="lost",
         danger=["and a witch starts throwing potions", "a witch, cackling, potions out", "a witch hut, and a very angry witch"],
         danger2=["poison on me, slowness on me", "i'm poisoned and slow", "i can barely move"],
         after=["the swamp is rusty's place now", "i don't go to that swamp anymore", "that swamp is quiet now"],
         after2=["i put a sign: good dog", "i left a bone there", "i made a little fence round the spot"], epi=None),
    dict(name="mochi", bones="two", place="the mountains", end="both",
         danger=["and a group of zombies come over the hill", "three zombies over the ridge", "zombies, coming down the mountain"],
         danger2=["they surround us", "they come from every side", "zombies on all sides"],
         after=["we walked home like heroes", "walked back to town, heads up", "we were heroes that night"],
         after2=["mochi is still with me", "mochi sleeps by my door", "mochi's asleep at home right now"],
         epi=["mochi found a girlfriend", "then mochi met another wolf", "mochi had a family later"],
         epi2=["five puppies. i had to build a dog house", "five pups. built a kennel", "now there's a whole kennel"]),
], wolf)


# --- The parrot that wouldn't shut up.
def parrot(n, v):
    p1 = P(
        A(f"i had a parrot called {v.name}. it never shut up", f"my parrot {v.name}. loudest bird alive", f"ever had a parrot? mine, {v.name}, never stopped talking"),
        B("laugh", "parrots lol", "they're so loud", "cute though"),
        A("tamed it in the jungle with seeds", "found it in a jungle, gave it seeds", "it took a stack of seeds to tame"),
        B("aw", "aww", "the seeds trick", "cute"),
        A("it sat on my shoulder everywhere i went", "rode on my shoulder all day", "always on my shoulder"),
        B("aw", "shoulder buddy", "cute", "nice"),
    )
    p2 = P(
        A("but parrots copy mob sounds. you know that?", "the thing about parrots, they copy mobs", "and parrots do mob noises"),
        B(None, "they do?", "oh no", "i forgot about that", "uh oh", "lol yes"),
        A(*v.sound),
        B("laugh", *v.sound_lines),
        A(*v.sound2, chance=0.85),
    )
    p3 = P(
        A(*v.twist),
        B(v.tw_react, *v.tw_lines),
        A(*v.twist2),
        B(v.tw2_react, *v.tw2_lines),
        A(*v.twist3, chance=0.8),
    )
    p4 = P(
        A(*v.end),
        B(None, *v.end_lines),
        A(*v.end2),
        B("done", "parrots man", "best bird", "lol"),
        A("never getting a quiet pet", "loud bird, good bird", "i love that bird", chance=0.6),
    )
    tell("parrot_" + n, [p1, p2, p3, p4], ["pets", "animals", "jokes"], v.name, leads=["pets", "animals", "jokes"],
         end_leads=["pets", "jokes", "animals"])


each([
    dict(name="kiwi", sound=["it would hiss like a creeper in my ear", "hisssss. right in my ear. every day", "it did the creeper hiss on my shoulder"],
         sound_lines=["NO", "the worst sound", "evil bird"], sound2=["i jumped every single time", "i'd jump a mile every time", "nearly fell off a cliff once"],
         twist=["then one day it hissed and i ignored it", "one day: hiss. i just rolled my eyes", "then one day it hissed again and i didn't look"],
         tw_react="gasp", tw_lines=["oh no", "it was real", "the boy who cried creeper"], twist2=["and it was a real creeper", "BOOM. that one was real", "it was real that time"],
         tw2_react="gasp", tw2_lines=["noooo", "the bird warned you", "lol"], twist3=["took half my health", "nearly died", "blew me into a bush"],
         end=["now when it hisses, i run", "now i listen to the bird", "now every hiss, i run first"], end_lines=["smart", "trust the bird", "lol"],
         end2=["ninety nine times fake, once real", "it's usually fake. usually", "it's real like one time in fifty"]),
    dict(name="mango", sound=["it groaned like a zombie all night", "zombie noises. every night", "it made zombie sounds while i slept"],
         sound_lines=["creepy", "lol", "spooky bird"], sound2=["i'd wake up with my sword out", "kept waking up ready to fight", "i slept with a sword because of it"],
         twist=["then i put a jukebox on", "one night i played a music disc", "then i got a jukebox"],
         tw_react="ask", tw_lines=["and?", "parrots dance", "oh?"], twist2=["and it started dancing", "it danced on its perch", "it bobbed its head like crazy"],
         tw2_react="aw", tw2_lines=["they dance!", "cute", "aww"], twist3=["it danced all night instead of groaning", "no more groaning. just dancing", "dancing bird, quiet nights"],
         end=["now the jukebox plays every night", "music every night now", "i play it every night"], end_lines=["cute", "smart", "nice"],
         end2=["best sleep i've had in ages", "i sleep fine now", "and i sleep great"]),
    dict(name="pickles", sound=["it clicked like a spider", "spider clicks, right in my ear", "it did spider noises in the cave"],
         sound_lines=["nope", "lol", "spiders"], sound2=["in caves it was so creepy", "it made caves ten times scarier", "mining with that bird was terrifying"],
         twist=["then a friend came over and it hissed", "my friend came to visit and it did the creeper hiss", "a friend visited. hiss"],
         tw_react="laugh", tw_lines=["oh no", "lol", "poor friend"], twist2=["they jumped off my balcony", "they jumped right off my roof", "they ran out the door screaming"],
         tw2_react="laugh", tw2_lines=["lmao", "into the pond?", "the bird wins"], twist3=["landed in my pond", "fell in my flower bed", "landed in a bush"],
         end=["they won't visit unless the bird is outside", "they make me put the bird out now", "they ask where the bird is first"], end_lines=["lol", "fair", "trust issues"],
         end2=["pickles is proud of it", "pickles won", "the bird knows what it did"]),
    dict(name="sunny", sound=["it copied skeleton bones. rattle rattle", "it rattled like a skeleton", "skeleton rattles. all day"],
         sound_lines=["creepy", "lol", "spooky"], sound2=["kept looking for skeletons that weren't there", "i'd draw my bow at nothing", "always looking round for skeletons"],
         twist=["then i jumped with it on my shoulder", "then i jumped down a ledge with it on me", "one day i jumped and it fell off"],
         tw_react="gasp", tw_lines=["they fall off", "oh no", "the bird!"], twist2=["and it flew off into the jungle", "it flew off and didn't come back", "off it went into the trees"],
         tw2_react="sad", tw2_lines=["noo", "come back", "oh no"], twist3=["i looked for hours", "searched all day", "i called for it all day"],
         end=["found it a week later on a tree, rattling", "a week later i heard rattling in a tree", "followed the rattling a week later"], end_lines=["the rattle!", "aww", "found it"],
         end2=["came right back to my shoulder", "it flew right back to me", "jumped straight on my shoulder"]),
    dict(name="biscuit", sound=["it did the ghast cry. in the overworld", "it made ghast noises in my house", "ghast cries in my living room"],
         sound_lines=["the ghast noise", "creepy", "that's so sad sounding"], sound2=["the saddest noise in my house", "every night: a crying ghast", "my house sounded haunted"],
         twist=["then i fed it a cookie", "one day i gave it a cookie", "i fed it a cookie. by mistake"],
         tw_react="gasp", tw_lines=["NO", "cookies kill parrots", "don't feed them cookies"], twist2=["and it got poisoned", "it went all green", "poisoned. right away"],
         tw2_react="gasp", tw2_lines=["no", "oh no", "the bird!"], twist3=["it lived. barely", "it survived with a tiny bit of health", "it pulled through"],
         end=["no cookies in my house now. at all", "i threw out every cookie", "cookies banned"], end_lines=["good", "phew", "lesson learned"],
         end2=["it still does the ghast noise. it's fine", "still haunts my house every night", "still cries like a ghast. love it"]),
], parrot)


# --- The horse that ran off.
def horse_runaway(n, v):
    p1 = P(
        A(f"my horse ran off once. {v.name}, {v.look}", f"i had a horse called {v.name}. {v.look}", f"so my horse {v.name}, {v.look}, ran away"),
        B(None, "oh no", "horses do that", "what happened", "go on", "aw"),
        A("took forever to tame. it threw me off like ten times", "it bucked me off over and over", "tamed it after a hundred tries"),
        B("laugh", "they do that", "lol", "stubborn"),
        A("but once it was tamed, it was the fastest thing ever", "then it was mine, and so fast", "after that, best horse ever"),
        B("wow", "nice", "zoom", "fast horse"),
    )
    p2 = P(
        A(f"one day i hopped off to {v.doing}", f"so one day i get off to {v.doing}", f"i got off for a minute to {v.doing}"),
        B("ask", "no lead?", "you tied it up right", "uh oh"),
        A("didn't tie it up. just a minute, i thought", "no lead. no fence. nothing", "i didn't tie it. why would it leave"),
        B("gasp", "no", "they wander", "oh no"),
        A("came back. no horse", "turned around. horse gone", "and when i came back, empty grass"),
    )
    p3 = P(
        A(*v.search),
        B("ask", "and?", "did you find it", "where was it"),
        A(*v.found),
        B(v.f_react, *v.f_lines),
        A(*v.found2, chance=0.85),
    )
    p4 = P(
        A(*v.end),
        B(None, *v.end_lines),
        A(*v.end2),
        B("done", "horses man", "always tie them up", "leads exist"),
        A("leads. always leads", "i carry three leads now", "fence and lead, every time", chance=0.6),
    )
    tell("horse_" + n, [p1, p2, p3, p4], ["animals", "pets", "exploring"], v.name, leads=["animals", "pets", "exploring"],
         end_leads=["animals", "pets", "jokes"])


each([
    dict(name="thunder", look="black with white socks", doing="mine some iron",
         search=["looked everywhere. three days", "searched for three days", "i walked everywhere for three days"],
         found=["found it with a whole herd of wild horses", "it had joined a herd", "it was running with wild horses"], f_react="aw",
         f_lines=["it found friends", "aww", "free horse"], found2=["it still had my saddle on", "saddle still on, running free", "the only one with a saddle"],
         end=["walked up, hopped on, rode home", "it let me get right back on", "jumped on and rode it home"], end_lines=["nice", "reunited", "aww"],
         end2=["like nothing happened", "it acted like it never left", "totally chill about it"]),
    dict(name="clover", look="all brown", doing="pick some flowers",
         search=["followed hoof prints. well, there aren't any. i guessed", "i went the way it was facing", "walked in circles shouting its name"],
         found=["found it in a village eating hay", "it was in a village, eating hay bales", "it had walked to a village"], f_react="laugh",
         f_lines=["eating hay lol", "it went shopping", "smart horse"], found2=["the villagers didn't care", "the villagers ignored it", "nobody noticed it"],
         end=["now i keep hay bales at home", "i put hay by my house now", "hay at home, so it stays"], end_lines=["smart", "nice", "lol"],
         end2=["it never leaves now", "it stays home now", "it just eats all day"]),
    dict(name="pebble", look="grey and speckled", doing="chop a tree",
         search=["looked for a week. nothing", "searched every day for a week", "spent a whole week looking"],
         found=["never found it", "never saw it again", "it was just gone"], f_react="sad",
         f_lines=["oh no", "that's sad", "rip"], found2=["maybe it's out there somewhere", "i hope it's ok", "maybe it found a better life"],
         end=["got a new horse eventually", "took a while to get another", "got another horse months later"], end_lines=["aw", "good", "nice"],
         end2=["i tie this one up every single time", "this one never leaves a fence", "this one has three leads"]),
    dict(name="dash", look="white as snow", doing="fight a zombie",
         search=["heard it neighing somewhere", "i could hear it but not see it", "heard a horse noise and followed it"],
         found=["it was stuck at the bottom of a hole", "it had fallen in a hole", "stuck in a ditch two blocks deep"], f_react="laugh",
         f_lines=["lol", "poor horse", "a hole"], found2=["just standing there looking sad", "looking up at me all sad", "staring at me like it was my fault"],
         end=["dug a ramp and got it out", "dug it out with a shovel", "made a ramp, walked it out"], end_lines=["nice", "rescue!", "good owner"],
         end2=["it's never forgiven me", "it still gives me looks", "it still blames me"]),
    dict(name="ember", look="chestnut red", doing="sleep in a village",
         search=["went looking at sunrise", "looked for it all morning", "searched at dawn"],
         found=["it had climbed a mountain somehow", "found it way up on a mountain", "it was on top of a hill, miles away"], f_react="laugh",
         f_lines=["how", "a mountain goat horse", "lol"], found2=["it was just looking at the view", "standing there, enjoying the view", "it was admiring the sunrise"],
         end=["so i sat with it and looked too", "i sat down next to it", "we watched the sunrise together"], end_lines=["aww", "cute", "wholesome"],
         end2=["best morning ever, honestly", "we go up there sometimes now", "it's our spot now"]),
], horse_runaway)


# --- The cat's presents.
def cat_gifts(n, v):
    p1 = P(
        A(f"my cat {v.name} brings me presents every morning", f"you know cats bring you gifts? {v.name} does", f"{v.name}, my cat, leaves me gifts when i wake up"),
        B("aw", "aww", "cats are the best", "cute"),
        A(f"usually {v.gift}", f"mostly {v.gift}", f"most days it's {v.gift}"),
        B(None, "cute", "thoughtful", "lol", "a good cat", "aw"),
        A("but one morning it was different", "but one day it brought something else", "but there was this one morning"),
        B("ask", "what was it", "uh oh", "go on"),
    )
    p2 = P(
        A(*v.special),
        B(v.sp_react, *v.sp_lines),
        A(*v.special2),
        B(v.sp2_react, *v.sp2_lines),
        A(*v.special3, chance=0.85),
    )
    p3 = P(
        A(*v.twist),
        B(v.tw_react, *v.tw_lines),
        A(*v.twist2),
        B(None, *v.tw2_lines),
        A(*v.twist3, chance=0.8),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A(*v.epi),
            B("aw", "aww", "cats", "cute"),
            A(*v.epi2),
            B("done", "best cat", "cats are the best", "love that"),
            A(f"{v.name} runs the house now", f"{v.name} is the boss", f"it's {v.name}'s house, i just live there", chance=0.6),
        )
    tell("cat_" + n, [p1, p2, p3, p4], ["pets", "animals", "home"], v.name, leads=["pets", "animals", "home"],
         end_leads=["pets", "home", "feelings"])


each([
    dict(name="mittens", gift="string", special=["a phantom membrane. right on my pillow", "a phantom membrane, on my bed", "phantom membrane. how?"],
         sp_react="wow", sp_lines=["how??", "cats are hunters", "rare"], special2=["and that day my elytra was broken", "my elytra needed fixing that exact day", "i needed one for my elytra that day"],
         sp2_react="wow", sp2_lines=["perfect timing", "the cat knew", "what"], special3=["fixed it right away", "fixed my wings with it", "repaired it on the anvil"],
         twist=["then a creeper came by my house", "that week, a creeper walked up to my door", "later a creeper showed up"],
         tw_react="gasp", tw_lines=["oh no", "the house!", "uh oh"], twist2=["mittens looked at it. it ran away", "it saw mittens and turned right around", "creepers are scared of cats. it left"],
         tw2_lines=["cats scare creepers", "the cat!", "guard cat"], twist3=["mittens didn't even get up", "she didn't even open her eyes", "she just yawned"],
         epi=["now i have four cats", "so i got three more cats", "now there are cats on every side of the house"], epi2=["no creeper has come near since", "zero creepers. zero", "my house is creeper-proof"]),
    dict(name="socks", gift="feathers", special=["a rabbit's foot", "a lucky rabbit's foot", "a rabbit foot. on my bed"],
         sp_react="wow", sp_lines=["lucky", "nice", "for potions"], special2=["i didn't even have a brewing stand", "i don't brew. at all", "no idea what to do with it"],
         sp2_react="laugh", sp2_lines=["lol", "a sign", "the cat says brew"], special3=["so i built one", "so i learned to brew", "so i made a brewing stand"],
         twist=["made a jump boost potion with it", "brewed a leaping potion", "made a potion of leaping"],
         tw_react="ask", tw_lines=["and?", "nice", "did it work"], twist2=["jumped over my house. landed on the roof", "jumped so high i landed on my roof", "leapt onto my own roof"],
         tw2_lines=["lol", "lol socks", "nice"], twist3=["socks just stared at me", "socks looked up like i was crazy", "socks was not impressed"], epi=None),
    dict(name="pumpkin", gift="raw chicken", special=["nothing. no gift. and no cat", "no gift. and pumpkin was gone", "the bed was empty. no cat"],
         sp_react="gasp", sp_lines=["oh no", "where's pumpkin", "no"], special2=["looked everywhere. the whole house", "searched every room", "i looked all over"],
         sp2_react="ask", sp2_lines=["and?", "did you find her", "where"], special3=["checked the chests. silly, but i did", "even looked in the chests", "looked under the bed"],
         twist=["found her in the barn with kittens", "she was in the barn. with three kittens", "she'd had kittens in the barn"],
         tw_react="aw", tw_lines=["KITTENS", "aww", "omg"], twist2=["three tiny orange kittens", "three of them, all orange", "tiny orange kittens"],
         tw2_lines=["cute", "aww", "family"], twist3=["now i get four gifts every morning", "now four cats bring me gifts", "four times the gifts now"],
         epi=["they all sleep on my bed", "all four sleep on my bed", "the bed is full of cats"], epi2=["i sleep on the edge now", "i get one corner", "there's no room for me"]),
    dict(name="shadow", gift="string", special=["it sat on my chest and wouldn't move", "it sat on my storage chest. wouldn't get off", "it parked itself on my chest"],
         sp_react="laugh", sp_lines=["cats do that", "lol", "you can't open it"], special2=["i needed my sword. it was in that chest", "my sword was in that chest", "everything i needed was in there"],
         sp2_react="laugh", sp2_lines=["lol", "no sword for you", "the cat says no"], special3=["i begged. it just blinked", "i asked nicely. nothing", "it looked at me and blinked"],
         twist=["so i went out without my sword", "had to go out unarmed", "so i went out with no sword"],
         tw_react="gasp", tw_lines=["oh no", "no sword at night?", "risky"], twist2=["punched a zombie to death. took forever", "fought a zombie with my fists", "fist fight with a zombie"],
         tw2_lines=["lol", "brutal", "blame the cat"], twist3=["came home and shadow had moved. to my bed", "got home, shadow was on my bed now", "shadow had moved to the pillow"], epi=None),
    dict(name="biscuit", gift="feathers", special=["a raw rabbit, right on my pillow", "a raw rabbit, on the doorstep", "a rabbit. raw. by my door"],
         sp_react="laugh", sp_lines=["a gift", "lol", "the hunter"], special2=["and i was starving that morning", "i had no food that day", "i'd run out of food"],
         sp2_react="aw", sp2_lines=["the cat knew", "aww", "the cat feeds you"], special3=["cooked it and ate it", "made rabbit stew", "cooked it up"],
         twist=["so the next day i gave biscuit a fish", "i gave biscuit fish to say thanks", "i fed biscuit my best salmon"],
         tw_react="aw", tw_lines=["aww", "fair trade", "cute"], twist2=["and the next morning, two rabbits", "next day: two rabbits", "and then it brought two"],
         tw2_lines=["lol", "a deal", "business partners"], twist3=["we have a deal now", "we trade now", "fish for rabbits, every day"],
         epi=["the whole town knows biscuit now", "everyone in town knows that cat", "biscuit is famous in town"], epi2=["people bring it fish", "villagers just stare at it", "it sits on the bell"]),
], cat_gifts)


# --- The fox that stole something.
def fox(n, v):
    p1 = P(
        A(f"a fox stole my {v.item} once", f"so a fox took my {v.item}", f"you know foxes steal stuff? one took my {v.item}"),
        B("laugh", "lol foxes", "they do that", "sneaky"),
        A(f"i'd put it down for a second", f"i dropped it by accident", f"it was on the ground for one second"),
        B(None, "and the fox grabbed it", "uh oh", "fast", "of course", "lol"),
        A("and this fox just trots up and takes it in its mouth", "a fox ran up and picked it up in its mouth", "the fox grabbed it and looked at me"),
        B("laugh", "the audacity", "lmao", "rude"),
    )
    p2 = P(
        A("and then it ran. so fast", "then it bolted", "and off it went"),
        B("ask", "chase it", "go go", "after it"),
        A(*v.chase),
        B(v.ch_react, *v.ch_lines),
        A(*v.chase2, chance=0.85),
    )
    p3 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B(None, *v.end2_lines),
        A(*v.end3, chance=0.8),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A(*v.epi),
            B(None, *v.epi_lines),
            A(*v.epi2),
            B("done", "foxes man", "cute thief", "lol"),
            A("foxes are the best", "love that fox", "best thief ever", chance=0.6),
        )
    tell("fox_" + n, [p1, p2, p3, p4], ["animals", "jokes", "pets"], "the fox", leads=["animals", "jokes", "pets"],
         end_leads=["animals", "jokes", "pets"])


each([
    dict(item="diamond sword", chase=["chased it through a whole forest", "ran after it through the trees", "chased it across the taiga"],
         ch_react="laugh", ch_lines=["lol", "the fox is winning", "faster"], chase2=["it kept stopping to look back at me", "it'd stop and wait. then run", "it waited for me every time. then ran"],
         end=["then i remembered: foxes drop stuff for food", "then i remembered foxes love food", "then i threw down some sweet berries"],
         end_react="ask", end_lines=["food!", "berries", "and?"], end2=["it dropped my sword and ate the berries", "it spat out my sword for berries", "sword for berries. it took the deal"],
         end2_lines=["lol", "a trade", "smart fox"], end3=["the cheapest trade i ever made", "best deal ever", "sweet berries for a diamond sword"],
         epi=["then it followed me home", "and it followed me after that", "and then it trusted me"], epi_lines=["aww", "a pet", "cute"],
         epi2=["it's my fox now", "it lives by my house now", "i have a fox now"]),
    dict(item="emerald", chase=["chased it to a den under a spruce", "followed it to its den", "it ran straight to its den"],
         ch_react="aw", ch_lines=["a den", "aww", "a fox home"], chase2=["there were baby foxes inside", "baby foxes in the den", "two baby foxes"],
         end=["i didn't have the heart to take it back", "i let it keep the emerald", "couldn't take it from the babies"], end_react="aw",
         end_lines=["aww", "so kind", "good person"], end2=["it was just one emerald", "one emerald, who cares", "it's just one emerald"],
         end2_lines=["true", "fair", "aww"], end3=["they're rich foxes now", "the richest foxes in the forest", "fox family with an emerald"], epi=None),
    dict(item="pickaxe", chase=["it ran into a cave", "it ran down into a cave", "it dove into a hole"],
         ch_react="gasp", ch_lines=["into a cave?", "oh no", "don't follow"], chase2=["i followed it in", "i went in after it", "and i went in"],
         end=["lost it in the dark", "it was gone in the dark", "never saw it again"], end_react="sad",
         end_lines=["rip pickaxe", "oh no", "the fox won"], end2=["somewhere down there a fox is mining", "a fox has my pickaxe somewhere", "a fox owns my pick now"],
         end2_lines=["lol", "miner fox", "the fox mines now"], end3=["it was my good pick too", "it had efficiency", "my best pickaxe"], epi=None),
    dict(item="porkchop", chase=["it ran off, i didn't chase it", "i let it go, it was a porkchop", "it was just food, i let it go"],
         ch_react="laugh", ch_lines=["fair", "lol", "let it eat"], chase2=["it ate it in front of me", "it sat and ate it right there", "it ate it, looking right at me"],
         end=["so i gave it another one", "so i gave it a second porkchop", "i dropped another for it"], end_react="aw",
         end_lines=["aww", "soft", "you fed it"], end2=["and it came back every day for more", "now it comes back every day", "it visits every morning"],
         end2_lines=["lol", "you're its food source", "aww"], end3=["i keep porkchops just for it", "i have a fox food chest now", "it has its own chest"],
         epi=["then one day it brought a friend", "and then it brought another fox", "then another fox came with it"], epi_lines=["two foxes", "lol", "the gang"],
         epi2=["now i feed four foxes", "it's a fox gang now", "four foxes every morning"]),
    dict(item="name tag", chase=["chased it round my farm three times", "it ran circles round my farm", "round and round the farm"],
         ch_react="laugh", ch_lines=["lol", "circles", "the fox is playing"], chase2=["it jumped the fence like nothing", "it jumped every fence", "it hopped over the fence"],
         end=["then it got tired and lay down", "then it lay down and fell asleep", "it got tired and took a nap"], end_react="laugh",
         end_lines=["lol", "a nap", "grab it"], end2=["took the name tag. then put it on the fox", "got the tag back. used it on the fox", "used the tag on the fox itself"],
         end2_lines=["lol", "perfect", "the fox owns it now"], end3=["named it thief", "it's called thief now", "named it sticky"], epi=None),
], fox)


# =====================================================================================================================
# VILLAGERS AND TOWNS
# =====================================================================================================================

# --- The villager that kept taking its bed.
def villager_bed(n, v):
    p1 = P(
        A("a villager kept stealing my bed", "there was this villager that kept taking my bed", "ever had a villager steal your bed? every night"),
        B("laugh", "lol villagers", "they do that", "the audacity"),
        A(f"i was staying in a village {v.why}", f"i'd moved into a village {v.why}", f"lived in a village for a while, {v.why}"),
        B(None, "nice", "ok", "go on", "and?", "cozy"),
        A("i put my bed in a little house", "i put my bed down in an empty house", "found an empty house, put my bed in"),
        B("ask", "and?", "uh oh", "the villager"),
    )
    p2 = P(
        A("first night, i come home. a villager's in it", "first night: villager in my bed", "i come back at dark. villager. in my bed"),
        B("laugh", "lol", "squatter", "the nerve"),
        A("so i wait for it to wake up", "i stood there till morning", "waited all night for it to get out"),
        B("laugh", "polite", "lol", "you waited??"),
        A("next night, same villager. same bed", "and the next night, same thing", "next night: there it is again"),
    )
    p3 = P(
        A(*v.try_),
        B(v.try_react, *v.try_lines),
        A(*v.try2),
        B(v.try2_react, *v.try2_lines),
        A(*v.try3, chance=0.85),
    )
    p4 = P(
        A(*v.end),
        B(None, *v.end_lines),
        A(*v.end2),
        B("done", "villagers are wild", "lol", "never trust a villager"),
        A("villagers. honestly", "can't win with villagers", "they always win", chance=0.6),
    )
    tell("vbed_" + n, [p1, p2, p3, p4], ["villagers", "sleep", "jokes"], "the villager", leads=["villagers", "town", "jokes"],
         end_leads=["villagers", "jokes", "sleep"])


each([
    dict(why="while i built my house",
         try_=["so i built a second bed for it", "made it its own bed next to mine", "put another bed down for it"], try_react="aw",
         try_lines=["nice", "kind", "smart"], try2=["it took the new one. a different villager took mine", "it took the new bed, and another one took mine", "two villagers now. two beds. zero for me"],
         try2_react="laugh", try2_lines=["lmao", "more villagers", "they multiply"], try3=["they just kept coming", "every bed got a villager", "more beds, more villagers"],
         end=["ended up with five beds and five villagers", "five beds, five villagers, no bed for me", "built five beds. all taken"], end_lines=["lol", "you built them a hotel", "lmao"],
         end2=["i finished my house and left them the beds", "left them all the beds when i moved", "they still sleep there. my hotel"]),
    dict(why="because my house burned",
         try_=["so i locked my door with a fence gate", "put a fence gate in the doorway", "blocked the door"], try_react="ask",
         try_lines=["did it work", "smart", "and?"], try2=["it opened the gate. villagers can't open gates. this one did", "the villager just walked through", "it got in somehow"],
         try2_react="laugh", try2_lines=["lol how", "a super villager", "a sneaky villager"], try3=["i still don't know how", "no idea how", "a mystery"],
         end=["so i slept on the roof", "i ended up sleeping on the roof", "slept on top of the house"], end_lines=["the roof lol", "outside??", "lol"],
         end2=["phantoms came. i blame the villager", "phantoms visited. thanks villager", "got attacked by phantoms. on the roof"]),
    dict(why="to trade for a while",
         try_=["so i broke the bed and put it in a hole underground", "so i hid my bed in a hole", "dug a hole and put my bed in it"], try_react="laugh",
         try_lines=["lol", "a secret bed", "smart"], try2=["it couldn't find it. i slept great", "it worked. it couldn't get in", "it couldn't reach it. finally"],
         try2_react="relief", try2_lines=["you won", "finally", "nice"], try3=["first good sleep in a week", "slept like a baby", "the best sleep"],
         end=["then the villager slept on the grass above me", "it slept on the grass right above my hole", "it lay on the dirt above my hole"], end_lines=["lol", "petty", "it was waiting"],
         end2=["standing guard. or sulking", "i think it was sulking", "it stared at the ground all night"]),
    dict(why="for a few nights",
         try_=["so i traded with it, thought it'd like me", "i bought stuff from it to be friends", "so i traded with it a lot"], try_react="laugh",
         try_lines=["bribery", "lol", "smart"], try2=["it liked me so much it still took my bed", "it gave me good prices. still took my bed", "great prices. still in my bed"],
         try2_react="laugh", try2_lines=["lol", "business and pleasure", "it loves you"], try3=["cheap bread though", "at least the trades were good", "i got cheap stuff out of it"],
         end=["so i just slept in its bed", "so i took its old bed", "i slept in its bed instead"], end_lines=["fair", "a swap", "lol"],
         end2=["we swapped. we're fine now", "we swapped beds. friends now", "we just traded beds. peace"]),
    dict(why="after a long trip",
         try_=["so i got in the bed first, before dark", "i got in before sunset", "jumped in early"], try_react="ask",
         try_lines=["did it work", "smart", "and?"], try2=["it stood next to the bed and stared at me", "it stood there staring at me all night", "it watched me all night"],
         try2_react="creepy", try2_lines=["creepy", "lol", "unblinking"], try3=["didn't sleep a wink", "couldn't sleep with it there", "i couldn't sleep at all"],
         end=["left the next morning", "packed up and left the next day", "i left that village"], end_lines=["fair", "lol", "it won"],
         end2=["it won. it always wins", "the villager won", "villager one, me zero"]),
], villager_bed)


# --- A raid.
def raid(n, v):
    p1 = P(
        A(f"i fought a raid once. {v.how}", f"raid story. {v.how}", f"you ever been in a raid? {v.how}"),
        B("gasp", "a raid??", "pillagers", "oh no", "the bell"),
        A("the bell starts ringing. the villagers run inside", "the bell rings, villagers run for doors", "the bell went off and everyone ran"),
        B("ask", "and you?", "go on", "here they come"),
        A("and i'm standing in the middle with a sword", "and i'm the only one left outside", "me, in the square, sword out"),
        B(None, "brave", "go on", "hero time", "uh oh", "and?"),
    )
    p2 = P(
        A("first wave: pillagers with crossbows", "first wave was pillagers, crossbows", "wave one. crossbows everywhere"),
        B("gasp", "crossbows hurt", "shield?", "go on"),
        A(*v.wave1),
        B(v.w1_react, *v.w1_lines),
        A("then the next wave", "then the next wave came", "and then wave two", chance=0.85),
    )
    p3 = P(
        A(*v.big),
        B("gasp", *v.big_lines),
        A(*v.big2),
        B(v.big2_react, *v.big2_lines),
        A(*v.big3, chance=0.85),
    )
    p4 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B("done", "raids are crazy", "what a fight", "respect"),
        A("i'd do it again", "never again. maybe", "worth it", chance=0.6),
    )
    p5 = None
    if v.epi:
        p5 = P(
            A(*v.epi),
            B(None, *v.epi_lines),
            A(*v.epi2),
            B(None, "lol", "worth it", "nice", "of course"),
            A("all because of one raid", "best night of my life", "raids are worth it", chance=0.6),
        )
    tell("raid_" + n, [p1, p2, p3, p4, p5], ["villagers", "fighting", "danger"], "the raid", leads=["villagers", "fighting", "town"],
         end_leads=["villagers", "fighting", "brag"], needs=["a:knows_raids"], weight=1.0)


each([
    dict(how="i drank the bad omen by accident",
         wave1=["blocked with my shield, hit back", "shield up, swing, shield up, swing", "i shielded every bolt"], w1_react="wow",
         w1_lines=["shield pro", "nice", "clean"], big=["a ravager came charging in", "then a ravager. huge, charging", "and a ravager stomps in"],
         big_lines=["a ravager", "the big one", "run"], big2=["it threw me across the square", "it knocked me into a house", "it sent me flying"],
         big2_react="gasp", big2_lines=["ouch", "you ok?", "get up"], big3=["the golem jumped in", "the golem took it on", "the iron golem showed up"],
         end=["me and the golem took it down", "the golem and i beat it", "we beat it together"], end_react="wow", end_lines=["teamwork", "go golem", "nice"],
         end2=["won the raid. hero of the village", "raid won. green effect, hero", "we won. hero of the village"],
         epi=["the next day the villagers threw me gifts", "the villagers started throwing me stuff", "every villager tossed me something"],
         epi_lines=["gifts!", "what did you get", "aw"], epi2=["arrows, bread, a book. cheap trades for days", "carrots, books, cheap trades", "bread, arrows, and half price everything"]),
    dict(how="pillagers came for my town",
         wave1=["the town fought them off together", "everyone grabbed a sword", "the whole town came out"], w1_react="wow",
         w1_lines=["the town!", "nice", "together"], big=["then an evoker showed up", "then an evoker. the fang guy", "then the evoker arrived"],
         big_lines=["evokers are evil", "the vexes", "oh no"], big2=["vexes flying through the walls everywhere", "vexes everywhere, through walls", "it summoned vexes. so many"],
         big2_react="gasp", big2_lines=["vexes are the worst", "through walls", "no"], big3=["i focused the evoker. ignored the vexes", "went straight for the evoker", "ran right at the evoker"],
         end=["got it. it dropped a totem", "killed it. totem of undying", "it went down and dropped a totem"], end_react="wow", end_lines=["a totem!", "huge", "nice"],
         end2=["the town was a mess but everyone lived", "lots of broken doors, nobody lost", "the town survived"],
         epi=["and that totem saved my life a month later", "a month later, the totem saved me", "the totem saved me later"],
         epi_lines=["no way", "how", "nice"], epi2=["fell in a ravine. totem popped. alive", "fell off a cliff, totem went off", "a creeper got me, totem popped"]),
    dict(how="i walked into a village with bad omen",
         wave1=["they shot me full of bolts", "i got hit so many times", "i couldn't block them all"], w1_react="gasp",
         w1_lines=["oof", "shield!", "hide"], big=["then witches started throwing potions", "then witches joined. potions everywhere", "and then the witches came"],
         big_lines=["witches!", "potions", "no"], big2=["poison, slowness, everything", "slowed down and poisoned", "every bad potion at once"],
         big2_react="gasp", big2_lines=["brutal", "milk!", "oh no"], big3=["drank milk, kept fighting", "milk bucket, kept swinging", "drank milk and got back in"],
         end=["lost. died. the raid won", "i died. they won", "the raid beat me"], end_react="sad", end_lines=["oh no", "rip", "the village?"],
         end2=["the village lost a few villagers", "a lot of villagers didn't make it", "the village was half empty after"],
         epi=None),
    dict(how="a patrol followed me home",
         wave1=["shot them from the bell tower", "climbed the bell tower and shot down", "used the roof, bow out"], w1_react="wow",
         w1_lines=["high ground", "smart", "sniper"], big=["then a ravager with a pillager riding it", "a ravager with a rider", "ravager, pillager on top"],
         big_lines=["a ravager jockey", "nope", "oh no"], big2=["it broke every crop in the village", "it ate the farms. trampled everything", "stomped every field flat"],
         big2_react="sad", big2_lines=["the crops!", "rip farms", "oh no"], big3=["i shot it till it fell", "kept shooting till it dropped", "arrows. so many arrows"],
         end=["won. but the farms were gone", "we won, farms ruined", "won the raid, lost the crops"], end_react="sad", end_lines=["at least you won", "rip farms", "bittersweet"],
         end2=["helped them replant everything", "replanted every farm with them", "i helped replant"],
         epi=["now that village calls me when there's trouble", "now they ring the bell and i come", "now i'm their guard basically"],
         epi_lines=["aw", "hero", "nice"], epi2=["i built a watchtower there", "i have a tower there now", "there's a little guard tower with my name"]),
    dict(how="it was the middle of the night",
         wave1=["i couldn't see them in the dark", "dark, crossbows, can't see a thing", "it was pitch black"], w1_react="creepy",
         w1_lines=["at night??", "scary", "no"], big=["then vindicators with axes", "then vindicators, axes out", "vindicators came running"],
         big_lines=["axes", "run", "oh no"], big2=["one broke down a door and went inside", "they broke into a house", "one got into a villager's house"],
         big2_react="gasp", big2_lines=["the villagers!", "go help", "no"], big3=["i ran in after it", "i chased it inside", "followed it in"],
         end=["got it before it hurt anyone", "stopped it right in time", "saved the villager inside"], end_react="relief", end_lines=["hero", "phew", "nice"],
         end2=["the villager gave me the best trade in town after", "that villager gives me great trades now", "that villager loves me now"],
         epi=None),
], raid)

# --- A zombie siege on a village.
def siege(n, v):
    p1 = P(
        A(f"i was in a village once when a zombie siege hit. {v.where}", f"zombie siege story. {v.where}", f"ever seen a zombie siege? i was {v.where}"),
        B("gasp", "a siege??", "oh no", "the bad nights", "zombies everywhere"),
        A("night falls and they just start appearing. inside the village", "dark, then zombies spawning right in the middle of the village",
          "they spawn right in the village. not outside, inside"),
        B("creepy", "inside?", "nowhere is safe", "nope"),
        A("dozens of them", "like twenty zombies", "way more than a normal night"),
        B("ask", "what did you do", "and?", "go on"),
    )
    p2 = P(
        A("they're banging on every door", "every door, bang bang bang", "zombies pounding on doors"),
        B("gasp", "they break doors", "the villagers!", "oh no"),
        A("then one door breaks", "one door gives way", "crack. a door goes down"),
        B("gasp", "no", "go help", "the villager inside"),
        A(*v.mid, chance=0.9),
    )
    p3 = P(
        A(*v.twist),
        B(v.tw_react, *v.tw_lines),
        A(*v.twist2),
        B(v.tw2_react, *v.tw2_lines),
        A(*v.twist3, chance=0.85),
    )
    p4 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B("done", "sieges are scary", "brave", "what a night"),
        A("i check on that village every week now", "i built them walls after", "i go back to visit them", chance=0.6),
    )
    tell("siege_" + n, [p1, p2, p3, p4], ["villagers", "mobs", "night"], "the siege", leads=["villagers", "mobs", "fighting"],
         end_leads=["villagers", "fighting", "night"])


each([
    dict(where="i'd just stopped to sleep there", mid=["i ran over with my sword", "i ran at it, sword out", "i sprinted over"],
         twist=["but i was too late. the villager got turned", "too late. it was a zombie villager now", "the villager turned into a zombie"],
         tw_react="sad", tw_lines=["oh no", "noooo", "rip villager"], twist2=["so i trapped it in a hole instead of killing it", "i didn't kill it. trapped it in a hole", "i pushed it into a pit and kept it"],
         tw2_react="ask", tw2_lines=["why", "trapped it?", "and?"], twist3=["i had a plan", "because you can cure them", "because i knew a trick"],
         end=["next day, weakness potion and a golden apple", "cured it with a golden apple and weakness", "threw weakness, fed it a golden apple"], end_react="wow",
         end_lines=["you cured it", "nice", "big brain"], end2=["it shook for a bit and turned back", "it turned back into a villager", "back to normal. and cheap trades"]),
    dict(where="it was my first week in that village", mid=["i rang the bell. the villagers ran inside", "i rang the bell to warn everyone", "rang the bell as hard as i could"],
         twist=["then the iron golem woke up", "the golem came out swinging", "then the golem showed up"],
         tw_react="wow", tw_lines=["the golem!", "go golem", "hero"], twist2=["it threw zombies into the sky", "zombies flying everywhere", "it was launching zombies"],
         tw2_react="laugh", tw2_lines=["lol", "golem carry", "yeet"], twist3=["i just helped", "i did the cleanup", "i got the stragglers"],
         end=["by morning, only one door broken", "morning came. one door broken, that's it", "sunrise. everyone fine"], end_react="relief",
         end_lines=["nice", "everyone lived", "phew"], end2=["i fixed the door. iron this time", "put an iron door on it", "made them a better door"]),
    dict(where="i was trading there", mid=["i ran inside a house and blocked the door", "hid inside with a villager", "jumped into a house and held the door"],
         twist=["me and a villager, hiding all night", "just me and a villager in a dark house", "me and one librarian, waiting"],
         tw_react="creepy", tw_lines=["scary", "all night?", "the banging"], twist2=["zombies banging on the walls all night", "they banged on the door for hours", "the door shook all night"],
         tw2_react="creepy", tw2_lines=["no thanks", "nope", "brr"], twist3=["the villager just stared at me", "the villager just said hrmm", "the librarian kept staring"],
         end=["the door held. sunrise burned them all", "the sun came up and they burned", "morning came and they burned up"], end_react="relief",
         end_lines=["phew", "the sun!", "saved"], end2=["that librarian gives me the best price now", "we bonded. great trades now", "best trading partner since"]),
    dict(where="i was just passing through", mid=["i fought them in the square", "stood in the middle and fought", "fought them one by one"],
         twist=["and there were baby zombies", "and then the babies came", "baby zombies everywhere"],
         tw_react="gasp", tw_lines=["babies are the worst", "so fast", "no"], twist2=["they ran under everything", "they ran right past me into houses", "they slipped past me"],
         tw2_react="gasp", tw2_lines=["oh no", "the villagers", "catch them"], twist3=["i chased them house to house", "running house to house", "i chased them everywhere"],
         end=["we lost two villagers", "two villagers didn't make it", "the village lost two people"], end_react="sad",
         end_lines=["oh no", "that's sad", "you tried"], end2=["i made them beds so more would come", "i put down beds for new villagers", "i gave them extra beds so the village would grow"]),
    dict(where="it was a village i'd built up myself", mid=["i'd put up fences. they didn't help", "the fences weren't enough", "my walls had a gap"],
         twist=["i found the gap they came through", "they'd come in through a gap in the wall", "there was a gap in my wall"],
         tw_react="gasp", tw_lines=["block it!", "oh no", "go"], twist2=["filled it with cobble while they hit me", "blocked it while getting hit", "patched the gap mid fight"],
         tw2_react="wow", tw2_lines=["nice", "clutch", "builder skills"], twist3=["then fought the ones inside", "then cleared the ones already in", "then cleaned up inside"],
         end=["everyone lived. not one villager lost", "nobody died. not one", "every villager made it"], end_react="wow",
         end_lines=["perfect", "hero", "nice"], end2=["i made the wall two blocks higher", "made the walls taller after", "built the wall higher"]),
], siege)


# --- An angry iron golem.
def golem(n, v):
    p1 = P(
        A(f"i hit a villager by accident once", f"so i was fighting {v.mob} in a village and i hit a villager", f"ever hit a villager by mistake? i swung at {v.mob}"),
        B("gasp", "oh no", "the golem", "you didn't", "uh oh"),
        A(f"i was swinging at {v.mob}", f"swinging at {v.mob} and missed", f"i swung at {v.mob}, the villager walked in front"),
        B("laugh", "they always walk in front", "classic", "villagers have no sense"),
        A("the villager went hrmm. angry hrmm", "the villager made the angry noise", "angry villager noise"),
        B("ask", "and the golem", "uh oh", "the golem saw?"),
    )
    p2 = P(
        A("and the golem turns around. slowly", "the iron golem turns. looks right at me", "then the golem stops and turns its head"),
        B("gasp", "RUN", "no no no", "you're done"),
        A("and walks at me", "starts walking toward me", "and comes for me"),
        B("ask", "run!", "what did you do", "go"),
        A(*v.escape),
    )
    p3 = P(
        A(*v.escape2),
        B(v.e_react, *v.e_lines),
        A(*v.escape3),
        B(v.e3_react, *v.e3_lines),
        A(*v.escape4, chance=0.8),
    )
    p4 = P(
        A(*v.end),
        B(None, *v.end_lines),
        A(*v.end2),
        B("done", "never hit villagers", "golems hold grudges", "lol"),
        A("i walk around villagers very carefully now", "no swinging in villages now", "i put my sword away in villages", chance=0.6),
    )
    tell("golem_" + n, [p1, p2, p3, p4], ["villagers", "mobs", "jokes"], "the golem", leads=["villagers", "mobs", "town"],
         end_leads=["villagers", "jokes", "mobs"])


each([
    dict(mob="a zombie", escape=["i ran. straight into the river", "jumped in the river", "dove into the river"],
         escape2=["golems walk underwater. i did not know that", "it walked right in after me. underwater", "it just walked along the bottom after me"],
         e_react="gasp", e_lines=["they can do that??", "nope", "swim!"], escape3=["it grabbed me and threw me out of the water", "it threw me out of the river", "it launched me onto the bank"],
         e3_react="laugh", e3_lines=["lmao", "yeet", "rejected"], escape4=["landed on two hearts", "two hearts left", "landed hurt but alive"],
         end=["then it just stood there staring at me", "it stood on the bank staring", "it watched me from the river"], end_lines=["lol", "creepy", "the stare"],
         end2=["i left that village forever", "never went back", "i left and never returned"]),
    dict(mob="a spider", escape=["pillared up. golems can't climb", "built a dirt tower fast", "jumped and pillared straight up"],
         escape2=["it stood at the bottom of my tower", "it just waited under me", "it stood there looking up"],
         e_react="laugh", e_lines=["lol", "patience", "a standoff"], escape3=["waited up there for a whole day", "i sat on top all day", "i stayed up there till sunset"],
         e3_react="laugh", e3_lines=["a whole day", "lol", "stubborn golem"], escape4=["it got bored eventually", "it finally wandered off", "it left at last"],
         end=["climbed down, walked away very slowly", "snuck down and tiptoed off", "came down and crept away"], end_lines=["safe", "lol", "careful"],
         end2=["the tower's still there. a monument to shame", "the dirt tower's still there", "my tower of shame stands"]),
    dict(mob="a skeleton", escape=["i ran for it. sprinting", "ran as fast as i could", "sprinted away"],
         escape2=["golems are slow. i got away", "it was slow. i got away", "i outran it. barely"],
         e_react="relief", e_lines=["phew", "lucky", "nice"], escape3=["then i went back for my stuff in that village", "but my bed was in that village", "then i remembered my bed was there"],
         e3_react="laugh", e3_lines=["oh no", "lol", "go get it"], escape4=["so i snuck back at night", "snuck back in the dark", "went back sneaking"],
         end=["it was waiting by my bed. waiting", "the golem was standing by my bed", "the golem was at my door"], end_lines=["lol", "it remembered", "creepy"],
         end2=["i left the bed. it's the golem's bed now", "the golem keeps my bed now", "i let it have the bed"]),
    dict(mob="a pillager", escape=["i didn't even run", "i just stood there", "i froze"],
         escape2=["it smacked me twenty blocks up", "it launched me into the sky", "one punch, i was flying"],
         e_react="laugh", e_lines=["LOL", "yeet", "launched"], escape3=["landed on a roof", "landed on the church roof", "landed on top of a house"],
         e3_react="laugh", e3_lines=["lucky", "lmao", "the roof"], escape4=["one heart. on a roof. thinking about my choices", "on one heart, on a roof", "one heart left, on the roof"],
         end=["the golem forgave me after a while", "after a bit it calmed down", "it forgot after a few minutes"], end_lines=["phew", "lucky", "nice"],
         end2=["we're fine now. i give it a wide berth", "we're cool. i stay away from it", "it's fine now. from a distance"]),
    dict(mob="a creeper", escape=["ran behind a house", "hid behind a hay bale", "ducked behind a well"],
         escape2=["then the creeper blew up the golem", "and the creeper went off right by the golem", "the creeper exploded next to the golem"],
         e_react="laugh", e_lines=["lol", "the creeper helped", "karma"], escape3=["the golem forgot all about me", "the golem was too busy to care", "it lost track of me"],
         e3_react="laugh", e3_lines=["lucky", "lol", "saved by a creeper"], escape4=["i slipped away", "i walked off whistling", "i left quietly"],
         end=["first time a creeper ever saved me", "a creeper saved me. once", "only creeper i'll ever thank"], end_lines=["lol", "rip creeper", "the hero"],
         end2=["i still feel bad for the golem", "the golem was fine though", "the golem healed up"]),
], golem)


# --- A wandering trader at the door.
def trader(n, v):
    p1 = P(
        A("a wandering trader showed up at my house once", "this wandering trader just appeared by my door", "ever had a wandering trader visit?"),
        B(None, "the llama guy", "they're weird", "what did he sell", "the blue guy", "ooh"),
        A("two llamas on leads, the whole thing", "with his two llamas", "llamas and all"),
        B("laugh", "the llamas", "lol", "llama guy"),
        A(f"and he was selling {v.items}", f"he had {v.items}", f"his trades: {v.items}"),
        B(None, *v.item_lines),
    )
    p2 = P(
        A(*v.buy),
        B(v.buy_react, *v.buy_lines),
        A(*v.buy2),
        B(None, "lol", "and?", "nice", "of course", "classic trader"),
        A("then the sun went down", "then night came", "and then it got dark", chance=0.85),
    )
    p3 = P(
        A(*v.twist),
        B(v.tw_react, *v.tw_lines),
        A(*v.twist2),
        B(v.tw2_react, *v.tw2_lines),
        A(*v.twist3, chance=0.8),
    )
    p4 = P(
        A(*v.end),
        B(None, *v.end_lines),
        A(*v.end2),
        B("done", "traders are weird", "lol", "what a guy"),
        A("i always check what they sell now", "never ignore a trader", "i wait for him now", chance=0.6),
    )
    tell("trader_" + n, [p1, p2, p3, p4], ["trade", "villagers", "jokes"], "the trader", leads=["trade", "villagers", "jokes"],
         end_leads=["trade", "jokes", "animals"])


each([
    dict(items="blue ice and a pufferfish bucket", item_lines=["blue ice??", "random", "lol"],
         buy=["i bought the blue ice for one emerald", "paid an emerald for blue ice", "got the blue ice, one emerald"], buy_react="wow",
         buy_lines=["that's a steal", "nice", "blue ice is rare"], buy2=["made a little ice road for my boat", "made a boat road with it", "built an ice highway"],
         twist=["at night he drank an invisibility potion", "at night he drank a potion and vanished", "he went invisible at night"],
         tw_react="creepy", tw_lines=["creepy", "where'd he go", "they do that"], twist2=["but his llamas were still there. floating leads", "the llamas were just standing there", "two llamas tied to nothing"],
         tw2_react="laugh", tw2_lines=["lol", "ghost llamas", "creepy"], twist3=["it looked so haunted", "spooky", "the leads were floating"],
         end=["morning, he was gone", "next morning, gone. llamas too", "by morning he'd vanished"], end_lines=["typical", "gone", "lol"],
         end2=["i still have that ice road", "the ice road is still there", "i use the ice road every day"]),
    dict(items="seeds, ferns and a nautilus shell", item_lines=["random", "lol", "a nautilus shell!"],
         buy=["bought the nautilus shell", "got the nautilus shell", "paid five emeralds for the shell"], buy_react="wow",
         buy_lines=["for a conduit", "nice", "rare"], buy2=["now i need seven more", "eight for a conduit. one down", "only seven more to go"],
         twist=["then i walked too close to a llama", "i went up to pet a llama", "then i walked by his llama"],
         tw_react="gasp", tw_lines=["don't", "oh no", "they spit"], twist2=["it spat at me. right in the face", "SPIT. right on me", "it spat on me"],
         tw2_react="laugh", tw2_lines=["lmao", "rude llama", "disgusting"], twist3=["it does damage too. half a heart", "half a heart of spit damage", "it actually hurt"],
         end=["so i kept my distance", "stayed away from the llamas", "steered clear after that"], end_lines=["smart", "lol", "fair"],
         end2=["but i'm still mad at that llama", "i still hold a grudge", "that llama and me, enemies"]),
    dict(items="flowers, sand and some saplings", item_lines=["a gardener", "lol", "nice"],
         buy=["bought a jungle sapling. i'd never seen one", "got a jungle sapling", "bought every sapling he had"], buy_react="aw",
         buy_lines=["nice", "a jungle tree", "cool"], buy2=["planted it by my house", "planted it in my garden", "put it next to my door"],
         twist=["then i put a lead on one of his llamas", "then i took one of his llamas with a lead", "then i stole a llama. borrowed. with a lead"],
         tw_react="laugh", tw_lines=["you stole a llama", "lol", "llama thief"], twist2=["he didn't even notice", "he didn't care", "he didn't even look"],
         tw2_react="laugh", tw2_lines=["lol", "free llama", "rude"], twist3=["the llama noticed though", "the llama was not happy", "the llama gave me a look"],
         end=["it lives in my garden now", "it's my llama now", "it lives by the jungle tree"], end_lines=["lol", "a llama farm", "cute"],
         end2=["it spits at everyone but me", "it spits at visitors", "great guard llama"]),
    dict(items="gunpowder, and slime balls", item_lines=["useful", "nice", "ooh"],
         buy=["bought all the gunpowder", "got every bit of gunpowder", "cleared out his gunpowder"], buy_react="wow",
         buy_lines=["rockets!", "nice", "explosions"], buy2=["made rockets and set them off all night", "made fireworks for fun", "fireworks all night"],
         twist=["one firework hit his llama", "a firework flew into his llama", "i hit his llama with a firework"],
         tw_react="gasp", tw_lines=["oh no", "lol", "the llama"], twist2=["the llama spat at me. the trader stared", "llama spat, trader stared", "both of them glared at me"],
         tw2_react="laugh", tw2_lines=["lmao", "fair", "you deserved it"], twist3=["he packed up the next day", "he left early", "he left a bit faster than usual"],
         end=["haven't seen a trader since", "no trader's come back since", "i think word got around"], end_lines=["lol", "banned", "blacklisted"],
         end2=["i think they talk to each other", "the traders know", "i'm on a list somewhere"]),
    dict(items="a podzol block and a bucket of tropical fish", item_lines=["weird", "random", "lol"],
         buy=["bought the tropical fish. it was orange and white", "got the fish in a bucket", "bought the little fish"], buy_react="aw",
         buy_lines=["cute", "a fish!", "nice"], buy2=["put it in my pond", "let it go in my pond", "it lives in my pond now"],
         twist=["then a zombie came for him at night", "a zombie went after him at night", "zombies came for the trader"],
         tw_react="gasp", tw_lines=["oh no", "help him", "the llama guy"], twist2=["i fought it off the trader", "fought the zombie off him", "i fought it off"],
         tw2_react="wow", tw2_lines=["hero", "nice", "good neighbour"], twist3=["he didn't say thanks", "no thanks from him", "he just stared"],
         end=["but his trades got cheaper. i swear", "he dropped his prices. i think", "next morning, cheaper fish"], end_lines=["lol", "a thanks", "nice"],
         end2=["he visits every few weeks now", "he comes back sometimes", "he's my regular now"]),
], trader)


# =====================================================================================================================
# SCARY THINGS
# =====================================================================================================================

# --- Looking an enderman in the eye.
def enderman(n, v):
    p1 = P(
        A(f"i looked an enderman in the eye once. {v.where}", f"ever made eye contact with an enderman? {v.where}", f"enderman story. {v.where}"),
        B("gasp", "you looked??", "never look", "oh no", "why would you"),
        A("didn't mean to. it was just there", "it was an accident. i turned and there it was", "i didn't see it till i was looking right at it"),
        B("creepy", "the stare", "nope", "brr"),
        A("it starts shaking. that sound", "it started shaking and making that noise", "it shook, then that scream"),
        B("gasp", "the scream", "run", "no no no"),
    )
    p2 = P(
        A("then it's gone", "then it teleports. gone", "and then it vanishes"),
        B("creepy", "behind you", "it's behind you", "nope"),
        A("and then it's right behind me", "and it's right behind me", "then i turn and it's there, behind me"),
        B("gasp", "NO", "run", "nope nope"),
        A("it hit me for like three hearts", "one hit, three hearts gone", "it hits so hard", chance=0.85),
    )
    p3 = P(
        A(*v.escape),
        B(v.e_react, *v.e_lines),
        A(*v.escape2),
        B(v.e2_react, *v.e2_lines),
        A(*v.escape3, chance=0.8),
    )
    p4 = P(
        A(*v.end),
        B(None, *v.end_lines),
        A(*v.end2),
        B("done", "never look", "endermen are scary", "creepy"),
        A("i look at my feet now", "i don't look at anything tall now", "eyes down, always", chance=0.6),
    )
    tell("enderman_" + n, [p1, p2, p3, p4], ["mobs", "danger", "night"], "the enderman", leads=["mobs", "danger", "night"],
         end_leads=["mobs", "danger", "night"])


each([
    dict(where="in a plains at night", escape=["jumped into a pond", "dove into a pond", "jumped into the nearest water"],
         e_react="wow", e_lines=["water!", "they hate water", "smart"], escape2=["it couldn't follow me into the water", "it teleported around the edge", "it couldn't get me in there"],
         e2_react="relief", e2_lines=["safe", "phew", "nice"], escape3=["i sat in that pond till morning", "stayed in the water all night", "spent the whole night in the pond"],
         end=["it gave up at sunrise", "it left in the morning", "by morning it was gone"], end_lines=["phew", "lucky", "nice"],
         end2=["i was so wet and so scared", "soggy and shaking", "i was a wet mess"]),
    dict(where="in my own mine", escape=["dug a hole two blocks high", "made a little two block tunnel", "ducked into a two high tunnel"],
         e_react="ask", e_lines=["they can't fit", "smart", "did it work"], escape2=["it can't fit. it's three blocks tall", "it couldn't get in. too tall", "it was too tall to follow"],
         e2_react="wow", e2_lines=["nice", "big brain", "short tunnel trick"], escape3=["i hit its legs from inside", "i swung at its feet from my hole", "hit its legs till it died"],
         end=["it dropped an ender pearl", "it dropped a pearl", "i got a pearl out of it"], end_lines=["a pearl!", "nice", "worth it"],
         end2=["first pearl i ever got", "i still have it", "i kept that pearl"]),
    dict(where="during a rainstorm", escape=["i didn't even have to do anything", "i just stood there", "i was ready to die"],
         e_react="ask", e_lines=["why", "rain!", "what happened"], escape2=["the rain was hurting it. it kept teleporting", "the rain burned it, it kept zapping around", "the rain hurt it more than me"],
         e2_react="laugh", e2_lines=["rain saves the day", "lol", "nice"], escape3=["it teleported away and didn't come back", "it zapped off and never came back", "it vanished for good"],
         end=["i love rain now", "rain is my best friend now", "rain, my hero"], end_lines=["lol", "fair", "rain gang"],
         end2=["i do a little dance when it rains now", "i thank the clouds", "every storm, i say thanks"]),
    dict(where="by my house at dusk", escape=["i ran inside and shut the door", "ran into my house", "ran inside, door closed"],
         e_react="gasp", e_lines=["they teleport though", "doors don't stop them", "oh no"], escape2=["it teleported inside. into my kitchen", "it showed up in my house", "it appeared in my living room"],
         e2_react="gasp", e2_lines=["INSIDE?", "nope", "no"], escape3=["we fought around my furniture", "fought it round the table", "fought it in the kitchen"],
         end=["won, but it took a block from my wall", "beat it, but it grabbed a block first", "it died, but my wall had a hole"], end_lines=["lol", "they steal blocks", "rude"],
         end2=["it took one dirt block. why", "it took a grass block from my roof", "it stole a block from my wall"]),
    dict(where="in a forest", escape=["i put on a carved pumpkin. after that", "after that i made a pumpkin helmet", "next time i wore a pumpkin"],
         e_react="laugh", e_lines=["the pumpkin trick", "lol", "smart"], escape2=["but that first time i just ran", "but that time i just ran and ran", "but right then i just ran"],
         e2_react="gasp", e2_lines=["they're faster", "oh no", "run run"], escape3=["it got me. died", "it caught me. dead", "i didn't make it"],
         end=["now i wear a pumpkin at night", "pumpkin head every night now", "i wear a pumpkin everywhere"], end_lines=["lol", "fashion", "smart"],
         end2=["can't see much but i'm alive", "the view is bad but i live", "orange vision, but alive"]),
], enderman)


# --- Footsteps in the dark.
def footsteps(n, v):
    p1 = P(
        A(f"i was mining alone and heard footsteps", f"ever hear footsteps when you're alone? i was {v.where}", f"so i'm {v.where}, by myself"),
        B("creepy", "nope", "who was it", "oh no"),
        A("not mine. i was standing still", "i stopped. they didn't", "i stood still. the footsteps kept going"),
        B("creepy", "nope nope", "i'd run", "no thanks"),
        A("step. step. step. getting closer", "closer and closer", "they were coming my way"),
        B("ask", "and?", "who was it", "what did you do"),
    )
    p2 = P(
        A("i put my torch down and held my sword", "sword out, back to the wall", "i backed into a corner, sword up"),
        B(None, "ready", "brave", "go on", "uh oh", "here it comes"),
        A("then they stopped", "and then they stopped. right around the corner", "then silence"),
        B("creepy", "worse", "the silence is worse", "nope"),
        A("so i peeked round the corner", "i slowly looked round the corner", "i leaned out to look", chance=0.9),
    )
    p3 = P(
        A(*v.reveal),
        B(v.r_react, *v.r_lines),
        A(*v.reveal2),
        B(v.r2_react, *v.r2_lines),
        A(*v.reveal3, chance=0.8),
    )
    p4 = P(
        A(*v.end),
        B(None, *v.end_lines),
        A(*v.end2),
        B("done", "creepy story", "good one", "chills"),
        A("i still jump at footsteps", "i mine with music now", "caves are too quiet", chance=0.6),
    )
    tell("footsteps_" + n, [p1, p2, p3, p4], ["caves", "danger", "mining"], "the footsteps", leads=["caves", "mining", "danger"],
         end_leads=["caves", "friends", "mobs"])


each([
    dict(where="deep in my mine", reveal=["it was someone else. just mining", "another miner, just mining away", "someone else, mining my tunnel"],
         r_react="laugh", r_lines=["lol", "a person", "phew"], reveal2=["they screamed. i screamed", "we both screamed", "we both jumped"],
         r2_react="laugh", r2_lines=["lmao", "both of you", "lol"], reveal3=["we laughed about it after", "then we laughed for ages", "then we couldn't stop laughing"],
         end=["we mine together now", "we became mining buddies", "now we mine together"], end_lines=["aw", "nice", "friends"],
         end2=["best friend i have, honestly", "met my best friend that way", "best accident ever"]),
    dict(where="in a cave under my house", reveal=["a zombie. just standing there", "a zombie, staring at me", "zombie. inches away"],
         r_react="gasp", r_lines=["NO", "nope", "kill it"], reveal2=["swung so hard i fell over", "i swung like crazy", "i hit it a hundred times"],
         r2_react="laugh", r2_lines=["lol", "panic", "overkill"], reveal3=["it dropped a carrot. great", "it dropped a potato", "it dropped rotten flesh"],
         end=["i blocked that cave off with stone", "sealed that cave forever", "walled it off"], end_lines=["smart", "good", "nope"],
         end2=["it's still sealed", "never opened it again", "it stays shut"]),
    dict(where="in a big dark cave", reveal=["nothing. nothing was there", "empty. totally empty", "nothing. just stone"],
         r_react="creepy", r_lines=["NOPE", "creepy", "no"], reveal2=["but the footsteps started again behind me", "then the steps started behind me", "then i heard them behind me"],
         r2_react="creepy", r2_lines=["i'd leave", "run", "no no no"], reveal3=["i ran. didn't look back", "i ran all the way up", "i was out of there"],
         end=["never found out what it was", "i still don't know what it was", "no idea what it was"], end_lines=["creepy", "nope", "the caves"],
         end2=["probably a skeleton. probably", "maybe a skeleton. maybe not", "i tell myself it was a skeleton"]),
    dict(where="in a mineshaft", reveal=["a goat. a goat in a mineshaft", "a goat, somehow", "it was a goat"],
         r_react="laugh", r_lines=["a goat??", "lol", "how"], reveal2=["it looked at me and rammed me down a hole", "it rammed me off a ledge", "it charged me down the tunnel"],
         r2_react="laugh", r2_lines=["lmao", "evil goat", "the goat wins"], reveal3=["fell into a lower tunnel", "fell two levels down", "i fell into a cave below"],
         end=["climbed back up, goat was gone", "came back up, no goat", "the goat had vanished"], end_lines=["lol", "ghost goat", "where'd it go"],
         end2=["i still don't know how it got there", "how does a goat get into a mine", "no idea how it got down there"]),
    dict(where="digging a long tunnel", reveal=["a villager. lost. in my tunnel", "a villager wandering my tunnel", "a villager. hrmm"],
         r_react="laugh", r_lines=["a villager??", "lol", "lost villager"], reveal2=["it had walked in from somewhere", "it wandered in from a cave", "it must have fallen in a cave"],
         r2_react="ask", r2_lines=["what did you do", "help it", "lol"], reveal3=["i led it out with my torch", "walked it all the way out", "showed it the way out"],
         end=["walked it back to its village", "took it home to its village", "it found its village"], end_lines=["aw", "good deed", "nice"],
         end2=["it gives me a discount now. i think", "it waves at me now. kind of", "we're friends now"]),
], footsteps)


# --- A ghast on a nether bridge.
def ghast(n, v):
    p1 = P(
        A(f"i was building a bridge across the lava sea in the nether", f"ok nether story. i'm bridging over lava. {v.why}", f"so i'm on a netherrack bridge over lava. {v.why}"),
        B("gasp", "over lava??", "brave", "the lava sea", "nope"),
        A("one block wide. no railings", "one block wide, sneaking the whole way", "thin little bridge, sneaking"),
        B("ask", "and?", "go on", "uh oh"),
        A("and then i hear it. that cry", "then a ghast cries somewhere", "and then i hear a ghast"),
        B("gasp", "NO", "the ghast", "oh no"),
    )
    p2 = P(
        A("i look up and there it is. huge. white", "i look up. a ghast. right there", "a big white ghast floating above me"),
        B("gasp", "it's going to shoot", "get off the bridge", "run"),
        A("its eyes open and it shoots", "it opens its mouth. fireball", "it fires a fireball at me"),
        B("ask", "hit it back!", "dodge", "what did you do"),
        A(*v.fire),
    )
    p3 = P(
        A(*v.result),
        B(v.r_react, *v.r_lines),
        A(*v.result2),
        B(v.r2_react, *v.r2_lines),
        A(*v.result3, chance=0.8),
    )
    p4 = P(
        A(*v.end),
        B(None, *v.end_lines),
        A(*v.end2),
        B("done", "ghasts are the worst", "the nether man", "wild"),
        A("cobblestone walls on every bridge now", "every bridge gets walls now", "i build covered bridges now", chance=0.6),
    )
    tell("ghast_" + n, [p1, p2, p3, p4], ["nether", "danger", "mobs"], "the ghast", leads=["nether", "danger", "mobs"],
         end_leads=["nether", "danger", "brag"])


each([
    dict(why="trying to get to a fortress", fire=["i swung at the fireball with my sword", "i hit the fireball with my sword", "swung right at it"],
         result=["it flew straight back and killed the ghast", "sent it right back. ghast dead", "it went back and hit the ghast"],
         r_react="wow", r_lines=["NO WAY", "legend", "return to sender"], result2=["it dropped a ghast tear into the lava", "it dropped a tear. into the lava", "the tear fell in the lava"],
         r2_react="laugh", r2_lines=["of course", "rip tear", "lol"], result3=["of course it did", "naturally", "every time"],
         end=["but i got the kill. that's what matters", "still, i killed it", "best hit of my life"], end_lines=["true", "legend", "w"],
         end2=["i tell everyone about it", "i'll never stop talking about it", "i still brag about it"]),
    dict(why="looking for a bastion", fire=["i tried to dodge", "i stepped back to dodge", "i moved to the side"],
         result=["it hit the bridge in front of me", "it blew the bridge in half", "it took out the bridge ahead"],
         r_react="gasp", r_lines=["the bridge!", "you're stuck", "oh no"], result2=["now i'm on a bit of bridge with no way forward", "stranded in the middle of the lava", "stuck on half a bridge"],
         r2_react="gasp", r2_lines=["oh no", "and the ghast?", "build!"], result3=["and the ghast is charging another one", "and it's charging up again", "and it shoots again"],
         end=["i built back the way i came. so fast", "i bridged back as fast as i could", "turned and ran back"], end_lines=["smart", "phew", "fast"],
         end2=["and went the long way. through a tunnel", "dug a tunnel the long way round", "went round underground"]),
    dict(why="trying to find netherite", fire=["i hid behind a block i placed", "put a cobble block up fast", "slapped down a cobble wall"],
         result=["the fireball blew up the netherrack. the cobble held", "the cobble took it", "the cobble saved me"],
         r_react="wow", r_lines=["cobble!", "nice", "blast proof"], result2=["so i covered the whole bridge in cobble", "i put cobble walls up the whole way", "built walls the whole way"],
         r2_react="laugh", r2_lines=["a tunnel bridge", "smart", "lol"], result3=["the ghast cried at me the whole time", "it cried outside the whole time", "it kept shooting the walls"],
         end=["made it across, safe in my tube", "got across in my little tube", "crossed safe"], end_lines=["nice", "smart", "the tube"],
         end2=["the ghast is probably still out there crying", "it's still crying out there", "it never got me"]),
    dict(why="just exploring", fire=["i froze", "i didn't move. at all", "i couldn't move"],
         result=["it hit me and knocked me off the bridge", "it blew me off the bridge", "the blast knocked me off"],
         r_react="gasp", r_lines=["into the lava??", "NO", "oh no"], result2=["into the lava sea. with everything", "straight into the lava", "fell right into the lava"],
         r2_react="sad", r2_lines=["rip", "all your stuff", "nooo"], result3=["all my stuff. gone. in lava", "everything burned", "lost everything"],
         end=["respawned at home with nothing", "woke up at home, empty", "back home with nothing"], end_lines=["oof", "pain", "rip"],
         end2=["i didn't go back to the nether for a month", "took a month to go back", "stayed away from the nether for weeks"]),
    dict(why="heading for a portal", fire=["i shot it with my bow first", "pulled out my bow and shot first", "shot it before it fired again"],
         result=["three arrows and it fell", "it took three arrows", "three shots, ghast down"],
         r_react="wow", r_lines=["nice shot", "sniper", "w"], result2=["it dropped gunpowder on the bridge", "gunpowder landed on the bridge", "it dropped gunpowder next to me"],
         r2_react="wow", r2_lines=["nice", "free gunpowder", "loot"], result3=["and a tear, right on the bridge", "and a tear too", "and a ghast tear"],
         end=["made a regeneration potion with the tear", "brewed regeneration with it", "turned it into a regen potion"], end_lines=["smart", "nice", "useful"],
         end2=["that potion saved me later", "used it in a big fight", "it saved my life once"]),
], ghast)


# --- Something odd down in the caves.
def cave_sign(n, v):
    p1 = P(
        A(f"i found something creepy in a cave once", f"so i'm way down in a cave and i find {v.thing}", f"weirdest thing i ever found underground: {v.thing}"),
        B("creepy", "what", "creepy", "uh oh", "tell me"),
        A(*v.what),
        B("creepy", "who made it", "nope", "creepy"),
        A("and nobody i know mines down there", "nobody lives anywhere near", "miles from anyone"),
        B("creepy", "nope", "i'd leave", "spooky"),
    )
    p2 = P(
        A(*v.closer),
        B("ask", "and?", "go on", "don't"),
        A(*v.closer2),
        B("creepy", *v.c2_lines),
        A(*v.closer3, chance=0.85),
    )
    p3 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B("done", "creepy", "caves are weird", "good story"),
        A(*v.lesson, chance=0.6),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A(*v.epi),
            B(None, *v.epi_lines),
            A(*v.epi2),
            B(None, "lol", "aw", "nice", "of course"),
            A("caves are less scary now", "small world", "funny how that works", chance=0.6),
        )
    tell("cavesign_" + n, [p1, p2, p3, p4], ["caves", "players", "mining"], "the cave", leads=["caves", "mining", "players"],
         end_leads=["caves", "players", "friends"])


each([
    dict(thing="a sign", what=["a sign on the wall. it said turn back", "an old sign: turn back", "a sign that just said: turn back"],
         closer=["so obviously i didn't turn back", "so of course i kept going", "and i kept going anyway"],
         closer2=["round the corner, another sign: i said turn back", "next sign: i said turn back", "another sign said: seriously"],
         c2_lines=["NOPE", "turn back", "nope nope"], closer3=["i kept going", "i still kept going", "i went further"],
         end=["last sign: ok fine. there's a chest", "last sign: fine. chest's on the left", "the last one said: ok, loot's here"], end_react="laugh",
         end_lines=["lol", "a prank", "loot!"], end2=["the chest had cookies and a note: gotcha", "the chest had bread and a note", "chest had a cake and a note: told you"],
         lesson=["best cave i ever explored", "i left a sign too", "i added my own sign"],
         epi=["years later i met the one who made them", "later i met the one who made the signs", "i met the sign maker later"],
         epi_lines=["no way", "lol", "small world"], epi2=["they laughed so hard", "they're my neighbour now", "they still make signs"]),
    dict(thing="a tunnel i didn't dig", what=["a perfect one by two tunnel. not mine", "a straight tunnel going on forever", "a mined tunnel. torches every ten blocks"],
         closer=["i followed it", "so i followed it", "followed it for ages"],
         closer2=["the torches got older. then stopped", "the torches ran out", "then the torches stopped"],
         c2_lines=["creepy", "uh oh", "nope"], closer3=["just darkness after that", "dark after that", "pitch black"],
         end=["at the end, a little room. a bed and a chest", "the end: a tiny room with a bed", "found a little room, a bed, a sign"], end_react="creepy",
         end_lines=["someone lived there", "creepy", "whose"], end2=["the sign said: gone up. back soon", "a sign: back soon", "it said: went to get food"],
         lesson=["i didn't touch anything", "i left it all alone", "i left them a torch"], epi=None),
    dict(thing="a chest with one torch in it", what=["a chest, alone, in the dark. one torch in it", "just a chest with a single torch inside", "an old chest. one torch inside"],
         closer=["so i put the torch on the wall", "i placed the torch", "i lit it up with the torch"],
         closer2=["and the light showed writing on the wall. signs everywhere", "the light showed signs all over the wall", "and there were signs everywhere"],
         c2_lines=["signs?", "creepy", "nope"], closer3=["it was a map. of the caves", "they were directions", "they were a map of the cave"],
         end=["i followed them to a huge ore room", "they led to a room full of ore", "the map led to iron and gold everywhere"], end_react="wow",
         end_lines=["no way", "loot!", "nice"], end2=["someone had left it for whoever found it", "someone left it for the next person", "a gift from a stranger"],
         lesson=["i left a chest for the next one too", "now i leave chests for others", "i pay it forward now"], epi=None),
    dict(thing="an old base", what=["a whole base. furnaces, chests, beds", "an old base with furnaces still lit", "a full underground base"],
         closer=["the furnaces were still burning", "one furnace was still on", "a furnace was still cooking"],
         closer2=["someone had been there recently", "very recently", "like an hour ago"],
         c2_lines=["leave", "creepy", "they're coming back"], closer3=["then i heard them coming back", "then footsteps", "and then someone coming"],
         end=["it was a bandit. we stared at each other", "a bandit walked in, sword out", "the owner walked in. a bandit"], end_react="gasp",
         end_lines=["a bandit!", "run", "uh oh"], end2=["they said: you didn't see anything. i agreed", "they just said: leave. i left", "i said sorry and left fast"],
         lesson=["i never went back", "never told anyone. well, until now", "i don't go to that cave"], epi=None),
    dict(thing="a little garden underground", what=["flowers, in a lit up room, deep underground", "a garden with flowers, under a mountain", "flower pots and grass, way down there"],
         closer=["there was a bench and a sign", "a bench, a sign, a little pond", "it had a bench and a sign"],
         closer2=["the sign said: for when it's too loud up there", "the sign said: a quiet place", "it said: sit a while"],
         c2_lines=["aww", "cozy", "nice"], closer3=["so i sat there for a while", "i sat on the bench", "i sat down"],
         end=["it was so quiet. so nice", "best quiet i ever had", "the calmest place i've ever been"], end_react="aw",
         end_lines=["aw", "lovely", "nice"], end2=["i added a flower before i left", "i planted one flower", "i left a flower pot"],
         lesson=["i go back when i need quiet", "i visit sometimes", "it's my quiet spot too now"],
         epi=["then one day i found a hermit sitting there", "one time the owner was there", "once i met the person who made it"],
         epi_lines=["a hermit?", "and?", "oh?"], epi2=["we didn't say a word. just sat", "they nodded. we sat in silence", "neither of us talked. perfect"]),
], cave_sign)

# =====================================================================================================================
# FRIENDSHIP
# =====================================================================================================================

# --- How it met its best friend.
def met_friend(n, v):
    p1 = P(
        A("did i ever tell you how i met my best friend?", "you know how i met my best friend?", "want to hear how i met my best friend?"),
        B("aw", "no, tell me", "aww", "go on", "i love this kind of story"),
        A(*v.how),
        B(v.how_react, *v.how_lines),
        A(*v.how2),
        B("ask", "and?", "go on", "then what"),
    )
    p2 = P(
        A(*v.mid),
        B(v.mid_react, *v.mid_lines),
        A(*v.mid2),
        B(None, *v.mid2_lines),
        A(*v.mid3, chance=0.85),
    )
    p3 = P(
        A(*v.bond),
        B("aw", *v.bond_lines),
        A(*v.bond2),
        B("aw", "that's a real friend", "aww", "wholesome"),
        A("that's when i knew", "from then on we were a team", "best friends ever since", chance=0.8),
    )
    p4 = P(
        A(*v.now),
        B(None, *v.now_lines),
        A(*v.now2),
        B("done", "good story", "friends are everything", "aw"),
        A("you're a good friend too, you know", "anyway. i'm glad i have friends", "friends make this place better", chance=0.5),
    )
    tell("metfriend_" + n, [p1, p2, p3, p4], ["friends", "memories", "feelings"], "my best friend", leads=["friends", "memories", "feelings"],
         end_leads=["friends", "feelings", "trust"], needs=["!strangers"], weight=0.8)


each([
    dict(how=["we were both lost in the same cave", "we were both lost in a cave. the same one", "we got lost in the same cave, from different sides"],
         how_react="laugh", how_lines=["lol", "both lost", "what are the odds"],
         how2=["i bumped into them in the dark and we both screamed", "we ran into each other in the dark", "we met in the dark and screamed"],
         mid=["we decided to find the way out together", "so we teamed up to get out", "we stuck together after that"], mid_react="aw",
         mid_lines=["teamwork", "aw", "smart"], mid2=["they had torches, i had food", "they had torches. i had bread", "i had the food, they had the light"],
         mid2_lines=["perfect team", "lol", "good trade"], mid3=["took us a whole day to get out", "took ages", "we were down there till night"],
         bond=["a creeper came for me and they pushed me out of the way", "they shoved me away from a creeper", "they took a creeper blast for me"],
         bond_lines=["no way", "a hero", "aww"], bond2=["lost half their health saving me", "nearly died for me", "they were on one heart after"],
         now=["we live next door now", "now we're neighbours", "our houses are side by side now"], now_lines=["cute", "aww", "nice"],
         now2=["we still go caving every week", "we still go mining together", "still caving buddies"]),
    dict(how=["we both wanted the same tree", "we both went to chop the same tree", "we both started chopping the same oak"],
         how_react="laugh", how_lines=["lol", "tree drama", "a fight?"],
         how2=["we just stared at each other with our axes", "axes out, staring", "we stood there with our axes up"],
         mid=["then they said: half each?", "then they offered to split it", "they said, you take the bottom, i'll take the top"], mid_react="laugh",
         mid_lines=["fair", "lol", "a deal"], mid2=["so we chopped it together", "we chopped it together", "we shared the tree"],
         mid2_lines=["nice", "cute", "teamwork"], mid3=["then another tree. then a whole forest", "then we did the next tree too", "then we cleared the whole hill"],
         bond=["that night they shared their bed spot", "they let me sleep at their place that night", "they gave me a bed for the night"],
         bond_lines=["aww", "so kind", "lovely"], bond2=["i didn't have a house yet", "i was homeless back then", "i had nowhere to sleep"],
         now=["we built a house together after", "we built a house together", "now we share a house"], now_lines=["aww", "cute", "nice"],
         now2=["out of that same forest", "made out of that same wood", "with wood from that forest"]),
    dict(how=["they saved me from drowning", "i was drowning and they pulled me out", "they saved me in the ocean"],
         how_react="gasp", how_lines=["drowning?", "oh no", "whoa"],
         how2=["i was stuck in a shipwreck out of air", "i was trapped in a shipwreck", "stuck under a ship, no air"],
         mid=["they broke the planks and pulled me up", "they broke through and got me", "they dug me out"], mid_react="wow",
         mid_lines=["hero", "wow", "a real one"], mid2=["i didn't even know them", "they were a total stranger", "never met them before"],
         mid2_lines=["wow", "a stranger?", "kind"], mid3=["they just saw me go down and followed", "they saw me dive and didn't come up", "they noticed i didn't come up"],
         bond=["i gave them my best sword to say thanks", "i gave them my best thing. my sword", "i gave them my sword as thanks"],
         bond_lines=["aww", "fair", "a good thanks"], bond2=["they gave it back and said: just be my friend", "they said keep it, just be friends", "they didn't want it. they wanted a friend"],
         now=["that was years ago", "that was a long time ago", "a long time ago now"], now_lines=["aww", "still friends?", "nice"],
         now2=["still my best friend", "still the best friend i have", "we still talk every day"]),
    dict(how=["we were fighting the same zombie", "we both hit the same zombie", "we were both fighting one zombie"],
         how_react="laugh", how_lines=["lol", "teamwork", "rip zombie"],
         how2=["we both went for the kill and hit each other", "and we hit each other instead", "swung, missed, hit each other"],
         mid=["we both just stood there. awkward", "awkward silence", "we stared at each other"], mid_react="laugh",
         mid_lines=["lol", "awkward", "oops"], mid2=["the zombie hit us both", "then the zombie hit us both", "and the zombie got us both"],
         mid2_lines=["lmao", "rip", "the zombie wins"], mid3=["we both respawned", "we both died", "both of us, dead"],
         bond=["then we went back for our stuff together", "we went back together to get our things", "we walked back together"],
         bond_lines=["aww", "together", "nice"], bond2=["killed that zombie properly that time", "this time we got the zombie", "teamed up on it that time"],
         now=["we fight everything together now", "now we fight side by side", "we're a fighting duo now"], now_lines=["cute", "nice", "a duo"],
         now2=["we haven't hit each other since. mostly", "only hit each other twice since", "we're better at it now"]),
    dict(how=["they moved in next door", "a newbie moved in next to me", "someone built a dirt hut next to my house"],
         how_react="laugh", how_lines=["a dirt hut lol", "a neighbour", "lol"],
         how2=["a dirt hut. one torch. no door", "dirt hut with no door", "tiny dirt box, one torch"],
         mid=["so i went over and showed them how to make a door", "i showed them how to make a door", "i taught them doors"], mid_react="aw",
         mid_lines=["kind", "aww", "a mentor"], mid2=["then a bed. then a furnace", "then how to make a bed", "then furnaces, beds, everything"],
         mid2_lines=["nice", "aww", "teacher"], mid3=["they learned so fast", "they picked it up quick", "they were a fast learner"],
         bond=["one day they brought me a diamond", "then one day they gave me a diamond", "they gave me their first diamond"],
         bond_lines=["their first one?", "aww", "omg"], bond2=["their first diamond. they gave it to me", "the very first one they ever found", "first diamond ever. to me"],
         now=["now their house is better than mine", "now they build better than me", "their house is huge now"], now_lines=["lol", "the student", "aww"],
         now2=["they still call me teacher", "they still ask me stuff sometimes", "still friends. best ones"]),
], met_friend)


# --- A falling out, and making up.
def falling_out(n, v):
    p1 = P(
        A("i had a big fight with a friend once", "me and my best friend fell out once. badly", "ever had a real fight with a friend?"),
        B("sad", "oh no", "what happened", "about what"),
        A(f"over {v.over}", f"it was about {v.over}", f"all over {v.over}"),
        B(None, *v.over_lines),
        A(*v.fight),
        B("sad", "oof", "ouch", "that's bad"),
    )
    p2 = P(
        A(f"we didn't talk for {v.time}", f"{v.time} of silence", f"no talking for {v.time}"),
        B("sad", f"{v.time}??", "that's long", "oof"),
        A("i'd walk past their house and look away", "we'd see each other and just walk off", "we avoided each other everywhere"),
        B("sad", "so awkward", "that's sad", "oof"),
        A("i missed them. a lot", "i missed them every day", "honestly i missed them so much", chance=0.85),
    )
    p3 = P(
        A(*v.turn),
        B(v.turn_react, *v.turn_lines),
        A(*v.turn2),
        B("ask", "and?", "did you make up", "go on"),
        A(*v.turn3),
    )
    p4 = P(
        A(*v.makeup),
        B("aw", *v.makeup_lines),
        A(*v.makeup2),
        B("aw", "good", "friends again", "that's how it should be"),
        A("fights don't matter. friends do", "best friends again", "never fighting over stuff again", chance=0.7),
    )
    p5 = None
    if v.epi:
        p5 = P(
            A(*v.epi),
            B(None, "lol", "aw", "of course", "nice"),
            A(*v.epi2),
            B("done", "good friends", "love that", "that's the dream"),
            A("we still laugh about it", "now we joke about it", "it's a funny story now", chance=0.6),
        )
    tell("fallout_" + n, [p1, p2, p3, p4, p5], ["friends", "feelings", "argument"], "the fight", leads=["friends", "feelings", "trust"],
         end_leads=["friends", "feelings", "trust"], needs=["!strangers"], weight=0.8)


each([
    dict(over="diamonds", over_lines=["classic", "diamonds ruin friendships", "of course"],
         fight=["we found them together but i mined them", "we found a vein. i grabbed all of it", "i took all six. we found them together"],
         time="a week",
         turn=["then i got stuck in a ravine at night", "then i fell into a ravine and couldn't get out", "then i got trapped in a ravine"],
         turn_react="gasp", turn_lines=["oh no", "alone?", "uh oh"], turn2=["and guess who came down with torches", "and who shows up with ladders", "and someone came down with ladders"],
         turn3=["it was them", "them. of course it was them", "my friend. the one i fought with"],
         makeup=["i gave them all six diamonds right there", "i handed them every diamond", "i gave them the diamonds. all of them"],
         makeup_lines=["aww", "fair", "good"], makeup2=["they gave three back. half each", "they gave half back", "they said: half each, like it should've been"],
         epi=None),
    dict(over="where to build a house", over_lines=["really?", "a building fight", "lol"],
         fight=["we both wanted the hill by the lake", "we both wanted the same hilltop", "same spot. both wanted it"],
         time="two weeks",
         turn=["they built there first. i was so mad", "they started building first", "they put their house on it"],
         turn_react="sad", turn_lines=["oh no", "ouch", "they won"], turn2=["then a creeper blew a hole in their house", "then a creeper wrecked their house", "a creeper hit their new house"],
         turn3=["and i went to help fix it", "and i showed up with planks", "so i went over with blocks"],
         makeup=["we fixed it together. and made it bigger", "we rebuilt it together, bigger", "we made it twice the size"],
         makeup_lines=["aww", "teamwork", "nice"], makeup2=["now there's room for both of us", "now it's a house for two", "we both live there now"],
         epi=["and we put a sign out front", "there's a sign out front now", "we made a sign for the door"], epi2=["it says: our house", "it says both our names", "it says: no fighting"]),
    dict(over="a broken promise", over_lines=["oh no", "promises matter", "what promise"],
         fight=["i said i'd help them fight a raid. i forgot", "i promised to help in a raid. i overslept", "i was meant to help them. i didn't show"],
         time="ages",
         turn=["they nearly died in that raid", "they almost lost their village", "they barely made it"],
         turn_react="sad", turn_lines=["oh no", "that's bad", "you felt awful"], turn2=["so i spent a week building them walls", "so i built them a wall. a whole wall", "i built walls round their village"],
         turn3=["didn't say anything. just built", "never said a word. just built", "just built, every day"],
         makeup=["one day they came out and started building with me", "then they joined in, quiet", "then they came out and helped"],
         makeup_lines=["aww", "no words needed", "lovely"], makeup2=["we finished it together", "we finished the wall together", "finished it side by side"],
         epi=None),
    dict(over="a pickaxe they borrowed", over_lines=["lol", "a pickaxe?", "ok"],
         fight=["they borrowed my diamond pick and broke it", "they broke my pickaxe. my good one", "they used it till it broke"],
         time="three days",
         turn=["then they showed up at my door", "then they knocked on my door", "then one day they were at my door"],
         turn_react="ask", turn_lines=["and?", "to say sorry?", "oh?"], turn2=["holding a brand new pickaxe", "with a new pickaxe", "holding a pick for me"],
         turn3=["with efficiency on it", "enchanted. better than mine", "better than the one they broke"],
         makeup=["i said sorry for being mad", "i apologised for yelling", "i said sorry too"],
         makeup_lines=["aww", "good", "both sorry"], makeup2=["we went mining with the new pick right away", "we tested it together", "went straight mining together"],
         epi=["they broke that one too, by the way", "they broke the new one too", "a month later they broke it again"], epi2=["but this time i laughed", "i just laughed this time", "this time i didn't care"]),
    dict(over="my dog", over_lines=["your dog?", "oh no", "what happened"],
         fight=["they took my dog into the nether. it didn't come back", "my dog followed them into a cave and was lost", "they took my dog on a trip and lost it"],
         time="a month",
         turn=["then one day they came to my door with a puppy", "then they turned up with a puppy", "they showed up with a wolf puppy"],
         turn_react="aw", turn_lines=["aww", "a puppy!", "oh"], turn2=["they'd spent a week finding one", "they searched a week for it", "they looked for days"],
         turn3=["it had a red collar. like my old one", "same red collar as my old dog", "red collar. like before"],
         makeup=["i cried a bit. ok a lot", "i cried, not gonna lie", "i got all teary"],
         makeup_lines=["aww", "of course", "so sweet"], makeup2=["we named it together", "we picked the name together", "we named it after my old one"],
         epi=None),
], falling_out)


# --- A friend who's gone.
def friend_gone(n, v):
    p1 = P(
        A(f"i had a friend once. {v.who}", f"there was someone i was close to. {v.who}", f"i don't talk about this much. i had a friend, {v.who}"),
        B("sad", "had?", "oh", "what happened"),
        A(*v.memory),
        B("aw", "sounds nice", "aww", "good times"),
        A(*v.memory2),
        B(None, "nice", "aw", "go on", "and?", "they sound great"),
    )
    p2 = P(
        A(*v.what),
        B("sad", *v.what_lines),
        A(*v.what2),
        B("sad", "i'm sorry", "that's hard", "oh no"),
        A(*v.what3, chance=0.85),
    )
    p3 = P(
        A(*v.after),
        B("sad", *v.after_lines),
        A(*v.after2),
        B("aw", "that's a nice thing to do", "they'd like that", "aw"),
        A(*v.after3, chance=0.8),
    )
    p4 = P(
        A("anyway. sorry. it's a sad one", "anyway, sorry, that got sad", "sorry. heavy story"),
        B(None, "don't be sorry", "thanks for telling me", "i'm glad you told me", "it's ok", "no, i'm glad you said"),
        A(*v.end),
        B("aw", "they'd be proud", "i'm sure", "that's lovely"),
        A("i think about them a lot", "they'd like you", "you remind me of them a bit", chance=0.6),
    )
    tell("gonefriend_" + n, [p1, p2, p3, p4], ["friends", "memories", "feelings"], "an old friend", leads=["friends", "memories", "feelings"],
         end_leads=["feelings", "friends", "memories"], needs=["!strangers"], weight=0.7)


each([
    dict(who="the best miner i ever knew", memory=["we mined together every day", "we'd mine every day, side by side", "every day, down in the mines"],
         memory2=["they always knew where the diamonds were", "they could find diamonds anywhere", "they had a nose for diamonds"],
         what=["one day they went down alone", "one day they went mining without me", "they went deep alone one day"],
         what_lines=["oh no", "alone", "uh oh"], what2=["and they never came back up", "they never came back", "and they didn't come home"],
         what3=["i looked for days", "i searched every tunnel", "i went down every day looking"],
         after=["i found their pickaxe down there", "i found their pick at the bottom of a ravine", "all i found was their pickaxe"],
         after_lines=["oh no", "i'm so sorry", "that's hard"], after2=["i hung it on my wall", "it's on my wall now", "i keep it on my wall"],
         after3=["i never use it", "i don't mine with it", "it stays on the wall"],
         end=["sometimes i mine where they used to. for them", "i still mine their old tunnels", "i finish their tunnels sometimes"]),
    dict(who="we built our first town together", memory=["we built the first houses side by side", "we put up the first houses together", "the first two houses were ours"],
         memory2=["they always made the roofs. i hated roofs", "they did roofs, i did walls", "they were great at roofs"],
         what=["one day they packed up and left", "one morning they were just gone", "they left one day. no goodbye"],
         what_lines=["no goodbye?", "oh", "why"], what2=["left a sign on their door: gone exploring", "a sign said: gone exploring", "just a sign: off to see the world"],
         what3=["that was a long time ago", "years now", "it's been ages"],
         after=["i still keep their house tidy", "i look after their house", "i keep their house the way they left it"],
         after_lines=["aww", "that's sweet", "loyal"], after2=["in case they come back", "just in case", "for when they come home"],
         after3=["the torches are always lit", "their door stays shut, the torches lit", "i light their torches every night"],
         end=["maybe one day they'll walk back in", "i hope they come back someday", "one day they'll be back. i think"]),
    dict(who="they taught me everything", memory=["how to make a bed, a furnace, all of it", "they taught me how to survive", "showed me how to farm, how to fight"],
         memory2=["i was a total newbie back then", "i knew nothing back then", "i was so bad back then"],
         what=["they ran out of lives. that was the last one", "they died one last time. no coming back", "one day they died and didn't come back"],
         what_lines=["oh", "i'm sorry", "that's awful"], what2=["a skeleton. one arrow. that was it", "it was just one bad fall", "a creeper. one creeper"],
         what3=["i wasn't there", "i was far away when it happened", "i only heard later"],
         after=["i planted a flower where it happened", "there's a poppy where they fell", "i put a flower there"],
         after_lines=["that's lovely", "aw", "they'd like that"], after2=["i visit it sometimes", "i go there now and then", "i check on it"],
         after3=["it's still growing", "it's still there", "it's a whole patch of flowers now"],
         end=["everything i know, i learned from them", "i still do things their way", "i still make beds the way they showed me"]),
    dict(who="they lived by the sea", memory=["we went fishing every evening", "we'd fish off their dock every night", "every evening, fishing on the dock"],
         memory2=["never caught much. didn't care", "we never caught anything good", "it was never about the fish"],
         what=["one day they sailed off to find a new land", "they took a boat out to find somewhere new", "they set sail one day"],
         what_lines=["sailed off?", "oh", "alone?"], what2=["and they never came back", "and i never heard from them again", "and that was the last i saw of them"],
         what3=["i watched the boat get smaller", "i watched till i couldn't see the boat", "i stood on the dock till it was gone"],
         after=["i still fish off their dock", "i still go to that dock", "i still fish there in the evenings"],
         after_lines=["aww", "that's sweet", "nice"], after2=["i leave a fish in their chest sometimes", "i put a fish in their chest", "i leave them the best catch"],
         after3=["silly, i know", "i know it's silly", "just in case"],
         end=["maybe they found their land", "i hope they found a good place", "i like to think they're happy somewhere"]),
], friend_gone)


# =====================================================================================================================
# JOURNEYS
# =====================================================================================================================

# --- The first trip to the nether.
def first_nether(n, v):
    p1 = P(
        A("my first trip to the nether. what a day", "you remember your first nether trip? mine was wild", "first time i went through a portal..."),
        B(None, "oh no", "tell me", "the first time is always bad", "go on", "how bad"),
        A(f"built the portal {v.portal}", f"made my portal {v.portal}", f"got the obsidian {v.portal}"),
        B(None, *v.portal_lines),
        A("stepped in. the purple swirl. the sound", "walked into the purple. whoosh", "jumped in, screen went all wobbly"),
        B("ask", "and?", "the heat", "what did you see"),
    )
    p2 = P(
        A("red everywhere. lava everywhere. so hot", "everything red, lava seas, that hum", "it was all red and loud and hot"),
        B("creepy", "scary place", "it's so loud", "nope"),
        A(*v.event),
        B(v.ev_react, *v.ev_lines),
        A(*v.event2, chance=0.85),
    )
    p3 = P(
        A(*v.event3),
        B(v.ev3_react, *v.ev3_lines),
        A(*v.event4),
        B(None, *v.ev4_lines),
        A(*v.event5, chance=0.8),
    )
    p4 = P(
        A(*v.home),
        B(v.home_react, *v.home_lines),
        A(*v.home2),
        B("done", "the nether is no joke", "first trips are wild", "good story"),
        A("i go there all the time now", "i still don't like it there", "i've been back a hundred times since", chance=0.6),
    )
    tell("firstnether_" + n, [p1, p2, p3, p4], ["nether", "exploring", "memories"], "the first nether trip", leads=["nether", "exploring", "danger"],
         end_leads=["nether", "memories", "brag"])


each([
    dict(portal="with a lava pool and a water bucket", portal_lines=["smart", "the bucket method", "nice"],
         event=["piglins. everywhere. and i had no gold on", "a group of piglins, and me with no gold armor", "piglins saw me. no gold on me"],
         ev_react="gasp", ev_lines=["no gold??", "they'll attack", "run"], event2=["they all came at me", "they charged me", "they all turned angry"],
         event3=["i ran back to the portal", "i dove back into the portal", "sprinted back to the portal"], ev3_react="ask", ev3_lines=["did you make it", "go go", "run"],
         event4=["made it. barely", "jumped through on two hearts", "got through just in time"], ev4_lines=["phew", "close", "lucky"],
         event5=["the portal took forever to work", "the portal sound took so long", "those seconds felt like forever"],
         home=["went back the next day in gold boots", "next day i went back with gold boots", "went back wearing a gold helmet"], home_react="laugh",
         home_lines=["fashion", "lol", "smart"], home2=["they just ignored me after that", "they didn't care after that", "the piglins left me alone"]),
    dict(portal="the proper way, with a diamond pick", portal_lines=["fancy", "the proper way", "nice"],
         event=["came out right next to a fortress", "a fortress, right there", "landed next to a nether fortress"],
         ev_react="wow", ev_lines=["lucky", "first try", "nice"], event2=["dark brick walls and bridges everywhere", "huge bridges, dark bricks", "big dark bridges"],
         event3=["went in and a blaze set me on fire", "blazes shot fire at me", "a blaze lit me up"], ev3_react="gasp", ev3_lines=["blazes!", "fire!", "oh no"],
         event4=["but i got two blaze rods before i ran", "still got two rods", "i got two blaze rods out of it"], ev4_lines=["nice", "worth it", "blaze rods!"],
         event5=["and a wither skeleton chased me out", "then a wither skeleton came after me", "then the black skeletons came"],
         home=["got home burnt and withered with two rods", "made it home, burnt but happy", "home with two rods and no health"], home_react="wow",
         home_lines=["two rods though", "worth it", "nice"], home2=["made a brewing stand the next day", "brewing stand time", "made my first brewing stand"]),
    dict(portal="from a ruined portal i fixed up", portal_lines=["smart", "a ruined one?", "nice"],
         event=["came out on a tiny ledge over the lava sea", "came out on a ledge above lava", "the portal was on a cliff over lava"],
         ev_react="gasp", ev_lines=["oh no", "don't move", "yikes"], event2=["one block to stand on", "barely room to stand", "a two block ledge"],
         event3=["then a ghast saw me", "and a ghast started crying", "then a ghast spotted me"], ev3_react="gasp", ev3_lines=["oh no", "run!", "the portal!"],
         event4=["it blew up my portal", "the fireball hit my portal", "my portal got blown out"], ev4_lines=["NO", "stuck", "oh no"],
         event5=["flint and steel. i had flint and steel", "luckily i had flint and steel", "relit it with flint and steel"],
         home=["went home and didn't go back for a week", "went home and stayed home a while", "i didn't go back for ages"], home_react="laugh",
         home_lines=["fair", "lol", "understandable"], home2=["the nether can wait, i said", "i said never again", "i needed a break"]),
    dict(portal="with a friend", portal_lines=["nice", "together", "aw"],
         event=["we split up to look around. big mistake", "we decided to split up", "we went different ways"],
         ev_react="gasp", ev_lines=["never split up", "oh no", "bad idea"], event2=["i lost them in like a minute", "lost them right away", "couldn't find them after a minute"],
         event3=["i found a bastion. alone", "i stumbled into a bastion", "i found a huge dark castle. a bastion"], ev3_react="gasp", ev3_lines=["a bastion!", "piglin brutes", "nope"],
         event4=["got chased out by a brute with an axe", "a brute with an axe chased me out", "piglin brute. axe. ran"], ev4_lines=["run", "brutes are terrifying", "lol"],
         event5=["ran straight into my friend", "ran right into my friend", "found my friend by running into them"],
         home=["we went home together and never split up again", "went home together", "we stuck together after that"], home_react="aw",
         home_lines=["good", "together", "aww"], home2=["we still go in together. always", "we always go together now", "never split up since"]),
    dict(portal="right next to my house", portal_lines=["smart", "close to home", "nice"],
         event=["came out in a blue forest. warped forest", "came out in a weird blue forest", "a blue forest with endermen"],
         ev_react="creepy", ev_lines=["endermen", "creepy", "pretty though"], event2=["endermen everywhere. so many", "endermen everywhere i looked", "so many endermen"],
         event3=["i didn't look at a single one", "eyes on my feet the whole time", "i never looked up once"], ev3_react="laugh", ev3_lines=["smart", "lol", "eyes down"],
         event4=["and walked straight into a lava pool", "and walked into lava because i was looking down", "and fell in lava because i wasn't looking"], ev4_lines=["LOL", "nooo", "irony"],
         event5=["died. first trip. ten minutes", "dead in ten minutes", "lasted ten minutes"],
         home=["respawned at home, right next to the portal", "woke up at home, the portal right there", "respawned at home"], home_react="laugh",
         home_lines=["lol", "short trip", "rip"], home2=["the portal just hummed at me", "the portal hummed at me like it was laughing", "the portal was mocking me"]),
], first_nether)


# --- A woodland mansion.
def mansion(n, v):
    p1 = P(
        A("i went to a woodland mansion once", "you know the giant dark houses in the forest? i went in one", "mansion story. long one"),
        B(None, "a mansion?", "the vindicator place", "brave", "go on", "those are huge"),
        A(f"got an explorer map {v.how}", f"followed an explorer map {v.how}", f"had a map {v.how}"),
        B(None, "the map!", "nice", "how far", "go on", "and?"),
        A(f"walked for {v.days}. through a dark forest", f"{v.days} of walking to a dark forest", f"took {v.days} to get there"),
        B("ask", "and?", "worth it?", "what was it like"),
    )
    p2 = P(
        A("it was huge. dark oak and cobble, three floors", "huge dark house, three floors", "massive. dark wood. so many windows"),
        B("creepy", "creepy", "who built it", "spooky"),
        A("went in. long dark hallways", "inside: hallways forever", "hallways and rooms everywhere"),
        B("ask", "anyone home", "uh oh", "and?"),
        A(*v.meet),
    )
    p3 = P(
        A(*v.fight),
        B(v.f_react, *v.f_lines),
        A(*v.fight2),
        B(v.f2_react, *v.f2_lines),
        A(*v.fight3, chance=0.85),
    )
    p4 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B("done", "mansions are wild", "brave", "what a trip"),
        A("worth the walk", "never going back. maybe", "best trip i've done", chance=0.6),
    )
    tell("mansion_" + n, [p1, p2, p3, p4], ["structures", "exploring", "fighting"], "the mansion", leads=["structures", "exploring", "fighting"],
         end_leads=["structures", "brag", "fighting"], needs=["!a:voice_newbie"], weight=0.8)


each([
    dict(how="from a cartographer", days="three days",
         meet=["a vindicator round the corner. axe up", "a vindicator jumped out with an axe", "vindicator. axe. right there"],
         fight=["it took four hearts in one hit", "one swing, four hearts", "it hit me so hard"], f_react="gasp", f_lines=["ouch", "axes hurt", "vindicators are brutal"],
         fight2=["i shielded the next one and killed it", "shield up, then i got it", "blocked and swung back"], f2_react="wow", f2_lines=["nice", "shield!", "clean"],
         fight3=["then three more", "then more came", "then the next room had three"],
         end=["found a secret room behind a wall. a diamond block", "found a hidden room with a diamond block", "a secret room. a diamond block in it"],
         end_react="wow", end_lines=["a diamond block!", "nine diamonds", "no way"], end2=["nine diamonds in one block", "worth every step", "best loot ever"]),
    dict(how="from a chest in a ship", days="five days",
         meet=["an evoker. fangs coming out of the floor", "an evoker, and fangs from the floor", "evoker. fangs. vexes"],
         fight=["the vexes came through the walls", "vexes flying through walls", "three vexes through the wall"], f_react="gasp", f_lines=["vexes!", "nope", "oh no"],
         fight2=["i went for the evoker and ignored them", "i ran at the evoker", "went straight for the evoker"], f2_react="ask", f2_lines=["and?", "did you get it", "go go"],
         fight3=["it died and the vexes vanished", "got it, vexes gone", "dead evoker, no more vexes"],
         end=["it dropped a totem of undying", "it dropped a totem", "a totem of undying!"],
         end_react="wow", end_lines=["a totem!", "nice", "huge"], end2=["saved my life two times since", "it already saved me once", "keeps me alive"]),
    dict(how="from a villager", days="four days",
         meet=["nothing. it was quiet. too quiet", "empty halls, nothing", "nobody. for ages"],
         fight=["then a vindicator ran out of a room screaming", "then a vindicator came running", "suddenly a vindicator, sprinting at me"],
         f_react="gasp", f_lines=["oh no", "run", "fight!"], fight2=["i ran down the stairs", "i ran the wrong way", "fled down a hallway"], f2_react="laugh", f2_lines=["lol", "run run", "where to"],
         fight3=["into a room with five more", "right into a room full of them", "into their room. all of them"],
         end=["died. first room. didn't even see a chest", "died right there", "they got me in seconds"],
         end_react="sad", end_lines=["rip", "first room lol", "nooo"], end2=["four days walk for that", "four days, for nothing", "all that walk for nothing"]),
    dict(how="from a cartographer in my town", days="a week",
         meet=["a room full of wool. like a weird art room", "rooms with weird statues", "a room with a statue made of wool"],
         fight=["then a vindicator in the next room", "a vindicator guarding the next room", "then the next room had a vindicator"],
         f_react="gasp", f_lines=["uh oh", "fight", "careful"], fight2=["i shot it from the doorway", "bow from the doorway", "shot it before it moved"], f2_react="wow", f2_lines=["smart", "nice", "sniper"],
         fight3=["cleared the whole floor like that", "did every room that way", "cleared it all with the bow"],
         end=["took all the dark oak home. so much wood", "took the whole place apart for the wood", "i took it apart for dark oak"],
         end_react="laugh", end_lines=["lol", "you took the house", "builder brain"], end2=["my house is made of that mansion now", "my house is mansion wood now", "built my house out of it"]),
    dict(how="from a friend", days="two days",
         meet=["my friend went in first. a vindicator was waiting", "my friend opened a door. vindicator", "a vindicator jumped my friend"],
         fight=["we fought it together", "we both went at it", "teamed up on it"], f_react="wow", f_lines=["teamwork", "nice", "two on one"],
         fight2=["then an evoker summoned fangs under us", "fangs came out of the floor", "evoker fangs, right under us"], f2_react="gasp", f2_lines=["oh no", "jump", "run"],
         fight3=["we ran different ways. classic", "split up. bad idea", "we got separated"],
         end=["we found each other in a secret room with a chest", "met up in a hidden room full of loot", "found each other in a secret room"],
         end_react="wow", end_lines=["lucky", "the secret room!", "nice"], end2=["split the loot fair and square", "we shared it all", "half each"]),
], mansion)


# --- An ocean monument.
def monument(n, v):
    p1 = P(
        A("i went into an ocean monument once", "ever tried an ocean monument? i did", "ocean monument story"),
        B("gasp", "guardians", "nope", "brave", "the underwater temple"),
        A(f"i went {v.prep}", f"went in {v.prep}", f"i was {v.prep}"),
        B(None, *v.prep_lines),
        A("the guardians start shooting lasers right away", "guardians, lasers, right away", "the second i get close: lasers"),
        B("gasp", "the lasers", "oh no", "they hurt"),
    )
    p2 = P(
        A("then the big one. the elder guardian", "then the elder guardian shows up. huge", "and then i see the big grey one"),
        B("gasp", "the elder!", "mining fatigue", "oh no"),
        A("and it gives me mining fatigue. the ghost face", "mining fatigue. that ghost face in my eyes", "the ghost face. mining fatigue three"),
        B("creepy", "that face", "the worst", "creepy"),
        A("now i can't break anything", "can't mine at all now", "i can barely break a block", chance=0.85),
    )
    p3 = P(
        A(*v.mid),
        B(v.m_react, *v.m_lines),
        A(*v.mid2),
        B(v.m2_react, *v.m2_lines),
        A(*v.mid3, chance=0.85),
    )
    p4 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B("done", "monuments are hard", "respect", "never going there"),
        A("once was enough", "i'd go back with more potions", "never again. probably", chance=0.6),
    )
    tell("monument_" + n, [p1, p2, p3, p4], ["ocean", "structures", "danger"], "the monument", leads=["ocean", "structures", "danger"],
         end_leads=["ocean", "structures", "brag"], needs=["!a:voice_newbie"], weight=0.8)


each([
    dict(prep="with water breathing potions", prep_lines=["smart", "prepared", "nice"],
         mid=["i found the elder and drank milk first", "drank milk to clear it", "milk. bye mining fatigue"], m_react="wow", m_lines=["milk!", "smart", "nice"],
         mid2=["fought it with my sword. took forever", "fought it for ages", "swung at it forever"], m2_react="ask", m2_lines=["did you win", "and?", "keep going"],
         mid3=["got it. then the other two", "beat it, then found the others", "one down, two to go"],
         end=["found the gold room. eight gold blocks", "the treasure room. gold blocks", "gold blocks, eight of them"], end_react="wow",
         end_lines=["gold!", "worth it", "nice"], end2=["and a stack of sponges", "plus all the sponges", "and sponges. so many"]),
    dict(prep="with no plan at all", prep_lines=["oh no", "bad idea", "classic"],
         mid=["i just swam in, totally lost", "swam around lost", "got lost inside right away"], m_react="laugh", m_lines=["of course", "lol", "it's a maze"],
         mid2=["ran out of air in a hallway", "my air ran out", "no air, no plan"], m2_react="gasp", m2_lines=["no", "oh no", "swim up"],
         mid3=["no doors, no potions", "nothing to breathe with", "no way to get air"],
         end=["drowned. in the monument. with the guardians watching", "drowned in a hallway", "died in there"], end_react="sad",
         end_lines=["rip", "of course", "nooo"], end2=["my stuff is still in there", "everything i had is in there", "my gear lives there now"]),
    dict(prep="with a friend and a stack of doors", prep_lines=["doors?", "for air", "smart"],
         mid=["we put doors down to breathe", "placed doors for air pockets", "doors everywhere, breathing"], m_react="wow", m_lines=["the door trick", "smart", "nice"],
         mid2=["dug through the walls with fatigue. so slow", "mined through with fatigue, forever", "breaking prismarine at snail speed"], m2_react="laugh", m2_lines=["lol", "patience", "slow"],
         mid3=["it took the whole day", "all day long", "hours"],
         end=["found the gold. split it", "got the gold, half each", "gold blocks for both of us"], end_react="wow",
         end_lines=["nice", "worth it", "teamwork"], end2=["we built a glass dome there after", "we made a dome over it later", "built a base there"]),
    dict(prep="with a conduit", prep_lines=["a conduit!", "fancy", "smart"],
         mid=["set it up near the entrance", "placed the conduit outside", "put the conduit near it"], m_react="wow", m_lines=["nice", "infinite air", "smart"],
         mid2=["it zapped the guardians for me", "it attacked the guardians too", "the conduit fought the guardians"], m2_react="wow", m2_lines=["no way", "a helper", "nice"],
         mid3=["easiest fight ever", "it did half the work", "it was so easy"],
         end=["drained it with sponges later", "drained the whole thing", "dried it out with sponges"], end_react="wow",
         end_lines=["insane", "so much work", "wow"], end2=["it's my base now. an underwater castle", "i live in it now", "it's my castle now"]),
    dict(prep="to get sponges", prep_lines=["sponges?", "ok", "brave"],
         mid=["found a room full of wet sponges", "a sponge room", "a room of sponges"], m_react="wow", m_lines=["nice", "jackpot", "sponges!"],
         mid2=["then an elder guardian came round the corner", "then the elder showed up", "the elder found me"], m2_react="gasp", m2_lines=["oh no", "run", "fight"],
         mid3=["i grabbed as many as i could and swam", "grabbed sponges and fled", "took a few and swam for it"],
         end=["got out with twelve sponges", "made it out with twelve", "twelve sponges, alive"], end_react="relief",
         end_lines=["phew", "nice", "good haul"], end2=["used them to drain my flooded basement", "drained my basement with them", "dried out my cellar"]),
], monument)

# --- The dragon fight.
def dragon(n, v):
    p1 = P(
        A("i fought the ender dragon once", "you want to hear about my dragon fight?", "the day i fought the dragon. listen"),
        B("wow", "the DRAGON?", "no way", "you did?", "tell me everything"),
        A(f"went in with {v.gear}", f"jumped in the portal with {v.gear}", f"i had {v.gear}"),
        B(None, *v.gear_lines),
        A("came out on the obsidian platform. black void everywhere", "landed on a little obsidian platform in the void", "the platform, the void, all that black"),
        B("creepy", "the void", "scary", "endermen?"),
    )
    p2 = P(
        A("endermen everywhere. like a hundred", "so many endermen", "hundreds of endermen standing around"),
        B("creepy", "don't look", "eyes down", "nope"),
        A("and the towers with the crystals on top", "and those tall towers, crystals on top", "obsidian towers, crystals glowing"),
        B("ask", "shoot them", "the crystals heal it", "and the dragon?"),
        A(*v.crystals),
    )
    p3 = P(
        A("then the dragon. flying circles, roaring", "and the dragon swoops right over me", "then the dragon comes at me"),
        B("gasp", "the dragon!", "huge", "oh no"),
        A(*v.fight),
        B(v.f_react, *v.f_lines),
        A(*v.fight2, chance=0.85),
    )
    p4 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B(None, *v.end2_lines),
        A(*v.end3, chance=0.8),
    )
    p5 = None
    if v.epi:
        p5 = P(
            A(*v.epi),
            B(None, *v.epi_lines),
            A(*v.epi2),
            B("done", "legend", "what a story", "the end is wild"),
            A("best day of my life", "i'll never forget it", "still think about it", chance=0.6),
        )
    tell("dragon_" + n, [p1, p2, p3, p4, p5], ["end", "fighting", "brag"], "the dragon", leads=["end", "fighting", "brag"],
         end_leads=["end", "brag", "fighting"], needs=["a:knows_dragon"], weight=1.0)


each([
    dict(gear="full diamond and a stack of arrows", gear_lines=["prepared", "nice", "full diamond!"],
         crystals=["shot every crystal with my bow", "shot them all down one by one", "arrows. every crystal. one by one"],
         fight=["hit it with arrows when it flew", "bow when it flew, sword when it landed", "arrows, arrows, then sword on the perch"],
         f_react="wow", f_lines=["nice", "the plan", "smart"], fight2=["took forever", "it took ages", "a long, long fight"],
         end=["and then it died. the purple light everywhere", "it died. purple beams shooting out", "it exploded in purple light"],
         end_react="wow", end_lines=["you did it!", "legend", "no way"], end2=["so much xp. i went up like thirty levels", "the xp was insane", "levels for days"],
         end2_lines=["nice", "the xp!", "rich"], end3=["and the egg sat there on the portal", "the egg was sitting on top", "the dragon egg appeared"],
         epi=["got the egg with the torch trick", "knocked the egg down with a piston and grabbed it", "got the egg home somehow"],
         epi_lines=["you have the egg?", "nice", "the egg!"], epi2=["it's on a pedestal in my house", "it sits on my mantle", "on a pillar in my house"]),
    dict(gear="iron armor and a lot of beds", gear_lines=["beds??", "bed strats", "risky"],
         crystals=["climbed the towers. one of them had cages", "pillared up each tower", "climbed up and broke the crystals by hand"],
         fight=["when it perched i put down a bed and clicked it", "beds. when it landed, boom", "bed explosions when it landed"],
         f_react="wow", f_lines=["bed bombing!", "smart", "insane"], fight2=["three beds and it was half dead", "three beds, half its health", "beds hit so hard"],
         end=["the fourth bed killed it. and me too", "the last bed killed it. and me", "got it. also blew myself up"],
         end_react="laugh", end_lines=["lol both", "a trade", "worth it"], end2=["respawned at home. still counts", "woke up at home. still won", "still counts"],
         end2_lines=["it counts", "lol", "a win's a win"], end3=["i went back for the xp. it was gone", "the xp was gone when i got back", "missed all the xp"], epi=None),
    dict(gear="a friend and two stacks of arrows", gear_lines=["two of you", "teamwork", "nice"],
         crystals=["they shot crystals, i guarded them from endermen", "they shot, i guarded", "they took the crystals, i fought endermen"],
         fight=["the dragon breath got my friend", "my friend got hit by the dragon's breath", "the purple breath hit my friend"],
         f_react="gasp", f_lines=["oh no", "the breath", "your friend"], fight2=["so i finished it alone", "i had to finish it myself", "it was all on me"],
         end=["got it with my last arrow", "last arrow. dead dragon", "it died on my last arrow"],
         end_react="wow", end_lines=["LAST ARROW", "clutch", "no way"], end2=["my friend respawned and missed it", "my friend missed the whole ending", "they respawned too late"],
         end2_lines=["lol", "poor friend", "rip"], end3=["they still say they helped. they did", "they claim half the kill", "they say it was half theirs"],
         epi=["then we found an end city", "after that we found an end city", "then we went to an end city"],
         epi_lines=["an end city!", "elytra?", "nice"], epi2=["got elytra off the ship", "we got elytra", "found elytra on the ship"]),
    dict(gear="nothing but a stone sword. long story", gear_lines=["stone??", "brave", "oh no"],
         crystals=["didn't break any crystals. didn't know", "i didn't know about the crystals", "ignored the crystals"],
         fight=["it just kept healing", "every time i hit it, it healed", "it healed every time"],
         f_react="laugh", f_lines=["the crystals lol", "of course", "you need to break them"], fight2=["it knocked me into the void", "it threw me off the island", "one hit and i was in the void"],
         end=["fell into the void. everything gone", "fell into the void forever", "the void took everything"],
         end_react="sad", end_lines=["the void!", "rip", "noooo"], end2=["the void is the worst way to die", "nothing comes back from the void", "everything gone, forever"],
         end2_lines=["the worst", "rip", "pain"], end3=["went back a month later, prepared", "came back later, properly ready", "next time i did it right"],
         epi=["second time i won", "the second time i beat it", "and the second time, i got it"], epi_lines=["nice", "redemption", "w"],
         epi2=["broke every crystal first", "crystals first this time", "learned about the crystals"]),
    dict(gear="potions of every kind", gear_lines=["fancy", "a brewer", "nice"],
         crystals=["shot the crystals, drank strength, waited", "broke the crystals, drank strength", "crystals down, strength up"],
         fight=["hit it on the perch with strength two", "strength two and a diamond sword", "smacked it on the perch"],
         f_react="wow", f_lines=["strength!", "nice", "huge hits"], fight2=["slow falling when it threw me", "drank slow falling when it launched me", "slow falling saved me"],
         end=["it died fast. so fast", "it died in like two minutes", "fastest fight ever"],
         end_react="wow", end_lines=["potions op", "fast", "nice"], end2=["potions are the best gear", "potions win fights", "brewing is underrated"],
         end2_lines=["true", "maybe", "nice"], end3=["now i brew everything", "brewer forever", "i brew all the time now"], epi=None),
], dragon)


# --- A trial chamber.
def trial(n, v):
    p1 = P(
        A("i found a trial chamber once", "ever been in a trial chamber? copper everywhere", "trial chamber story"),
        B(None, "the copper place", "ooh", "the breeze place", "go on", "nice"),
        A(f"found it {v.how}", f"i found it {v.how}", f"came across it {v.how}"),
        B(None, "nice", "go on", "and?", "lucky", "cool"),
        A("copper and tuff everywhere. trial spawners", "orange copper rooms, spawners in every room", "huge copper halls"),
        B("ask", "go in", "and?", "what was in it"),
    )
    p2 = P(
        A(*v.first),
        B(v.f_react, *v.f_lines),
        A("then the breeze", "then a breeze showed up", "and then a breeze"),
        B("gasp", "breezes are annoying", "the wind guy", "oh no"),
        A("it kept bouncing me around with wind", "it blasted me with wind charges", "wind charges, flinging me everywhere", chance=0.85),
    )
    p3 = P(
        A(*v.mid),
        B(v.m_react, *v.m_lines),
        A(*v.mid2),
        B(v.m2_react, *v.m2_lines),
        A(*v.mid3, chance=0.85),
    )
    p4 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B("done", "trial chambers are fun", "nice", "good one"),
        A("going back next week", "i go back all the time", "best place to fight", chance=0.6),
    )
    tell("trial_" + n, [p1, p2, p3, p4], ["structures", "fighting", "brag"], "the trial chamber", leads=["structures", "fighting", "caves"],
         end_leads=["structures", "brag", "weapons"], needs=["a:knows_trial_chambers"], weight=1.2)


each([
    dict(how="by digging into it by accident", first=["first room: zombies from a spawner", "zombies from the first spawner", "first spawner made zombies"],
         f_react="ask", f_lines=["easy", "and?", "go on"], mid=["beat every wave and got a trial key", "beat the waves, got a key", "won the room, got a key"],
         m_react="wow", m_lines=["a key!", "the vault!", "nice"], mid2=["put it in a vault", "used it on a vault", "opened a vault"],
         m2_react="ask", m2_lines=["what did you get", "and?", "loot?"], mid3=["it spun and spun", "it rolled through items", "it spat out loot"],
         end=["a mace. a heavy core and a mace", "got a heavy core. then made a mace", "a heavy core. mace time"],
         end_react="wow", end_lines=["A MACE", "no way", "insane"], end2=["now i jump off things and smash mobs", "falling attacks with a mace. so good", "jump, smash, done"]),
    dict(how="under a mountain", first=["first room: spiders. lots", "spider spawner first", "spiders, everywhere"],
         f_react="gasp", f_lines=["spiders", "ugh", "nope"], mid=["i got knocked into the breeze's room", "the wind threw me into another room", "got blasted into a room with three breezes"],
         m_react="gasp", m_lines=["three??", "oh no", "run"], mid2=["i just hit the wind charges back", "i hit their charges right back at them", "batted their charges back"],
         m2_react="wow", m2_lines=["nice", "return to sender", "skills"], mid3=["got them all", "beat all three", "cleared the room"],
         end=["got so many breeze rods", "breeze rods for days", "a stack of breeze rods"],
         end_react="wow", end_lines=["wind charges!", "nice", "rods!"], end2=["now i use wind charges to jump really high", "i launch myself with wind charges now", "wind charge jumps everywhere"]),
    dict(how="with a friend", first=["first room: skeletons", "skeleton spawner first", "skeletons shooting from all sides"],
         f_react="gasp", f_lines=["skeletons", "shields!", "oof"], mid=["my friend activated the ominous one", "my friend had bad omen. ominous trial", "it went ominous because of my friend"],
         m_react="gasp", m_lines=["ominous!", "harder", "oh no"], mid2=["everything was harder. so much harder", "way harder mobs", "the waves were brutal"],
         m2_react="gasp", m2_lines=["oof", "rough", "and?"], mid3=["we barely made it", "we both nearly died", "one heart each"],
         end=["but the ominous vault gave us a heavy core", "ominous vault: heavy core", "we got a heavy core from it"],
         end_react="wow", end_lines=["worth it", "nice", "a core!"], end2=["we flipped a coin for it. i lost", "we argued over it. they won", "they got it. i'm fine. i'm fine"]),
    dict(how="by following a long tunnel", first=["first room was empty. weird", "nothing in the first room", "first room, totally quiet"],
         f_react="creepy", f_lines=["creepy", "too quiet", "uh oh"], mid=["found a big hall with five spawners at once", "a hall with five spawners", "a huge room, five spawners"],
         m_react="gasp", m_lines=["five??", "nope", "run"], mid2=["i ran through to the vaults", "sprinted through to the vaults", "ran past everything"],
         m2_react="laugh", m2_lines=["lol", "no key though", "speedrun"], mid3=["but i didn't have a key", "no key. of course", "keyless"],
         end=["went back and fought them all for keys", "had to go back and fight for keys", "went back and earned the keys"],
         end_react="laugh", end_lines=["lol", "no shortcuts", "fair"], end2=["got an enchanted book and some emeralds", "got emeralds and a book", "decent loot. worth it"]),
    dict(how="under my own base", first=["first room: slimes. bouncy", "a slime spawner. bouncy chaos", "slimes bouncing everywhere"],
         f_react="laugh", f_lines=["slimes lol", "bouncy", "cute"], mid=["then a breeze knocked me into a pit", "fell in a pit from a wind charge", "wind charge, into a hole"],
         m_react="gasp", m_lines=["oh no", "how deep", "ouch"], mid2=["landed on a slime. bounced out", "a slime cushioned me", "landed on a slime block. bounced"],
         m2_react="laugh", m2_lines=["lol", "saved by slime", "bouncy"], mid3=["bounced right back up", "boing, back up", "launched back out"],
         end=["cleared it and made it part of my base", "it's part of my base now", "connected it to my base"],
         end_react="wow", end_lines=["nice", "a dungeon basement", "cool"], end2=["best basement anyone has", "my basement is a trial chamber", "nobody has a basement like mine"]),
], trial)


# --- Its first flight with elytra.
def elytra(n, v):
    p1 = P(
        A("my first time flying with elytra. oh boy", "you remember your first elytra flight? mine", "first flight with wings. let me tell you"),
        B("smirk", "oh no", "the wall", "first flights are rough"),
        A(f"got them {v.how}", f"found my elytra {v.how}", f"elytra {v.how}"),
        B("wow", "lucky", "nice", "wings!"),
        A(f"so i climbed {v.where} to jump", f"went up {v.where} to take off", f"stood on top of {v.where}"),
        B("ask", "and jumped", "uh oh", "and?"),
    )
    p2 = P(
        A("jumped. pressed space. wings out", "jumped, opened the wings", "jump, space, flying"),
        B(None, "flying!", "and?", "nice", "go go", "here we go"),
        A("and i'm FLYING. it's the best feeling", "and i'm gliding. so smooth", "i'm actually flying"),
        B("wow", "the dream", "jealous", "so cool"),
        A(*v.fly, chance=0.85),
    )
    p3 = P(
        A(*v.crash),
        B(v.c_react, *v.c_lines),
        A(*v.crash2),
        B(v.c2_react, *v.c2_lines),
        A(*v.crash3),
    )
    p4 = P(
        A(*v.end),
        B(None, *v.end_lines),
        A(*v.end2),
        B("done", "elytra are the best", "flying is everything", "nice"),
        A("i fly everywhere now", "never walking again", "the sky is mine now", chance=0.6),
    )
    tell("elytra_" + n, [p1, p2, p3, p4], ["sky", "end", "jokes"], "the elytra", leads=["sky", "end", "brag"],
         end_leads=["sky", "jokes", "brag"], needs=["a:knows_elytra"], weight=1.2)


each([
    dict(how="from an end ship", where="my house", fly=["then i look down. too high", "then i see how high i am", "and then i look down"],
         crash=["i panicked and dove", "i panicked, pointed down", "panic. nose down"], c_react="gasp", c_lines=["pull up!", "no", "oh no"],
         crash2=["straight into my own roof", "right into my roof", "smashed into my roof"], c2_react="laugh", c2_lines=["lmao", "your own roof", "of course"],
         crash3=["hole in the roof, three hearts left", "three hearts and a hole", "roof broken, me too"],
         end=["learned to land in water", "now i land in my pond", "always aim for water now"], end_lines=["smart", "lol", "good"],
         end2=["the roof hole is still there", "kept the hole, it's a landing pad", "i fly in through the roof now"]),
    dict(how="after three end cities", where="a big mountain", fly=["flew over the forest, so pretty", "everything looked so small", "flew over rivers and trees"],
         crash=["then i ran into a tree. a big one", "then a giant spruce. face first", "straight into a tall tree"], c_react="laugh", c_lines=["lmao", "trees", "the tree won"],
         crash2=["fell through the leaves", "slid down through the leaves", "fell all the way down"], c2_react="ask", c2_lines=["alive?", "and?", "ouch"],
         crash3=["alive. but the tree won", "lived. barely", "lived, embarrassed"],
         end=["now i fly above the trees", "now i fly higher", "high above everything now"], end_lines=["smart", "lol", "good plan"],
         end2=["way above the trees", "nothing can hit me up there", "the sky is safe"]),
    dict(how="from a friend", where="a tall tower", fly=["then i tried a firework", "then i used a rocket", "then i fired a firework"],
         crash=["it launched me straight into a cliff", "zoom, into a cliff", "rocketed right into a mountain"], c_react="gasp", c_lines=["no", "oh no", "the cliff"],
         crash2=["died. first flight. one rocket", "dead. one rocket in", "that was it. dead"], c2_react="laugh", c2_lines=["lol", "rip", "the cliff wins"],
         crash3=["my friend saw the whole thing", "my friend watched it all", "my friend was laughing so hard"],
         end=["they still call me rocket", "now they call me rocket", "my name is rocket now"], end_lines=["lol", "fitting", "rocket"],
         end2=["i got better though", "i'm good at it now", "i'm a pro now"]),
    dict(how="after fighting the dragon", where="the end island", fly=["then i flew off the edge of the island", "flew right off the island", "and off the edge i went"],
         crash=["over the void. no land anywhere", "and there's just void below", "nothing under me but void"], c_react="gasp", c_lines=["the VOID", "no", "turn around"],
         crash2=["gliding slower and slower", "losing height", "going down"], c2_react="gasp", c2_lines=["fireworks!", "do you have rockets", "no no no"],
         crash3=["one firework left. used it", "my last firework. boost", "one rocket, used it"],
         end=["landed on an end island just in time", "made it to another island", "landed on a little island"], end_lines=["phew", "clutch", "lucky"],
         end2=["stuck there with no rockets for an hour", "had to bridge back. took forever", "bridged home for an hour"]),
    dict(how="from a ship near an end city", where="my town's bell tower", fly=["flew over the town. everyone looked up", "everyone in town looked up", "flew right over the town"],
         crash=["then i tried to land on the town square", "tried to land in the square", "went for a cool landing"], c_react="ask", c_lines=["and?", "nailed it?", "uh oh"],
         crash2=["landed on a villager", "landed right on a villager", "hit a villager"], c2_react="laugh", c2_lines=["lmao", "the villager", "oh no"],
         crash3=["the golem was not happy", "the iron golem saw", "the golem saw it all"],
         end=["ran away. well, flew away", "flew away fast", "took off again"], end_lines=["lol", "smart", "escape"],
         end2=["i land outside town now", "i land outside the walls now", "no more town landings"]),
], elytra)


# =====================================================================================================================
# BUILDING
# =====================================================================================================================

# --- A build that went wrong.
def build_fail(n, v):
    p1 = P(
        A(f"i built {v.what} once. it did not go well", f"so i was building {v.what}", f"you know how i wanted to build {v.what}?"),
        B("smirk", "oh no", "what happened", "go on"),
        A(*v.plan),
        B(None, "sounds cool", "ambitious", "nice", "fancy", "go on"),
        A("spent days on it", "took me like four days", "days and days of work"),
        B("ask", "and?", "uh oh", "i sense a but"),
    )
    p2 = P(
        A(*v.problem),
        B(v.p_react, *v.p_lines),
        A(*v.problem2),
        B(v.p2_react, *v.p2_lines),
        A(*v.problem3, chance=0.85),
    )
    p3 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B("done", "builder life", "classic", "lol"),
        A(*v.lesson, chance=0.6),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A(*v.epi),
            B(None, *v.epi_lines),
            A(*v.epi2),
            B(None, "nice", "lol", "fair", "love that"),
            A("so it worked out", "happy ending", "turned out ok", chance=0.6),
        )
    tell("buildfail_" + n, [p1, p2, p3, p4], ["building", "jokes", "home"], "the build", leads=["building", "decor", "jokes"],
         end_leads=["building", "jokes", "decor"])


each([
    dict(what="a sand castle. a real one", plan=["a whole castle out of sand. towers and all", "sand walls, sand towers", "all sand. very fancy"],
         problem=["then i broke one block at the bottom to fix it", "i took out one bottom block", "fixed one block at the bottom"],
         p_react="gasp", p_lines=["sand falls", "no", "oh no"], problem2=["and the whole tower came down", "the tower fell. all of it", "everything above it fell"],
         p2_react="laugh", p2_lines=["lmao", "physics", "rip castle"], problem3=["then the next one", "then the wall next to it", "it kept falling"],
         end=["ended up with a pile of sand", "just a big pile of sand now", "a sand pile. that's it"], end_react="laugh",
         end_lines=["lol", "rip", "a sand hill"], end2=["called it a beach and moved on", "made it a beach", "it's a beach now"],
         lesson=["sandstone next time", "always sandstone", "never build with sand"], epi=None),
    dict(what="a roof out of gravel", plan=["i had so much gravel. why not", "gravel roof, grey and nice", "all my extra gravel, on the roof"],
         problem=["i placed the first block of the roof", "placed the first roof block", "put the first gravel block up"],
         p_react="ask", p_lines=["and?", "gravel falls", "uh oh"], problem2=["and it fell on my head", "it fell right on me", "it dropped on my head"],
         p2_react="laugh", p2_lines=["lmao", "gravel physics", "classic"], problem3=["so i put slabs under it first", "so i propped it up with slabs", "put a layer under it"],
         end=["then i broke the slabs to make it look nicer", "then i removed the slabs", "took the slabs out later"], end_react="gasp",
         end_lines=["no", "oh no", "the gravel"], end2=["and the whole roof fell into my house", "the roof filled my house with gravel", "gravel everywhere inside"],
         lesson=["i have a lot of flint now", "got so much flint from it", "flint for life"],
         epi=["so i dug it all out and found flint everywhere", "digging it out gave me flint", "i got flint out of it at least"],
         epi_lines=["silver lining", "flint", "lol"], epi2=["made a hundred arrows", "arrows for days", "made flint and steel too"]),
    dict(what="a fountain in my yard", plan=["a big stone fountain. water pouring down", "a fountain with water flowing", "fancy fountain, four levels"],
         problem=["i put the water source on top", "put the water in at the top", "placed the water"],
         p_react="ask", p_lines=["and?", "water spreads", "uh oh"], problem2=["it flowed everywhere. my whole yard", "it spread across the yard", "it ran everywhere"],
         p2_react="laugh", p2_lines=["lol", "a flood", "water is hard"], problem3=["into my house. into my farm", "into my farm and broke my crops", "washed out my crops"],
         end=["my farm popped. seeds everywhere", "every crop popped off", "my farm was destroyed"], end_react="sad",
         end_lines=["rip farm", "oh no", "the crops"], end2=["so i turned the yard into a pond", "made the whole yard a pond", "now my yard is a pond"],
         lesson=["water needs walls", "always build walls for water", "contain your water"], epi=None),
    dict(what="a house on a floating island", plan=["a little house up in the sky", "sky house, floating", "a cottage on a floating island"],
         problem=["no stairs. no ladder", "forgot to build a way down", "i didn't make a way down"],
         p_react="laugh", p_lines=["oh no", "how did you get down", "lol"], problem2=["so i jumped. into a pond", "jumped and aimed for a pond", "jumped into the lake"],
         p2_react="ask", p2_lines=["did you hit it", "and?", "nice"], problem3=["missed by a block", "missed the water by one block", "landed one block off"],
         end=["died. respawned in my sky bed", "died, and respawned up in the sky house", "respawned up there again"], end_react="laugh",
         end_lines=["lmao", "stuck forever", "the loop"], end2=["then i built a ladder", "finally built a ladder down", "made a water drop"],
         lesson=["always build the way down first", "stairs first next time", "think of the way down"],
         epi=["now it has a water elevator", "i put a water elevator in", "it has a water drop now"], epi_lines=["nice", "smart", "fancy"],
         epi2=["best house i ever built", "i love living up there", "best view in the world"]),
    dict(what="a giant statue of myself", plan=["a huge statue of me, in wool", "a giant me. in wool", "me, but huge, out of wool"],
         problem=["took a week to finish", "after a week of building", "finished it after forever"],
         p_react="ask", p_lines=["and?", "did it look good", "ok"], problem2=["it looked like a potato", "it looked nothing like me", "it was a lumpy blob"],
         p2_react="laugh", p2_lines=["LOL", "a potato", "lumpy you"], problem3=["my friends said it looked like a creeper", "someone said it looked like a creeper", "everyone said creeper"],
         end=["then lightning hit it and it burned", "then lightning set it on fire", "a storm came and lightning burned it"], end_react="laugh",
         end_lines=["wool burns", "lol", "karma"], end2=["burned right down to the ground", "the whole thing burned", "gone in an hour"],
         lesson=["stone statues only now", "never building myself again", "statues are hard"], epi=None),
], build_fail)


# --- A build contest in town.
def contest(n, v):
    p1 = P(
        A("we had a build contest in town once", "remember the build contest? i was in it", "our town did a build contest once"),
        B(None, "ooh", "what did you build", "did you win", "go on", "fun"),
        A(f"i built {v.mine}", f"my entry was {v.mine}", f"i went with {v.mine}"),
        B(None, *v.mine_lines),
        A("spent three days on it. every detail", "three days. no sleep. well, some sleep", "worked on it for days"),
        B("ask", "and the others?", "and?", "who else"),
    )
    p2 = P(
        A(*v.rival),
        B(v.r_react, *v.r_lines),
        A(*v.rival2),
        B(None, "lol", "and?", "the judging", "and then", "go on"),
        A("then it was judging day", "then came the judging", "and then everyone voted", chance=0.85),
    )
    p3 = P(
        A(*v.result),
        B(v.res_react, *v.res_lines),
        A(*v.result2),
        B(None, *v.res2_lines),
        A(*v.result3, chance=0.8),
    )
    p4 = P(
        A(*v.end),
        B(None, *v.end_lines),
        A(*v.end2),
        B("done", "build contests are fun", "lol", "next year"),
        A("next year i'm winning", "next contest is mine", "i'm already planning the next one", chance=0.6),
    )
    tell("contest_" + n, [p1, p2, p3, p4], ["building", "town", "brag"], "the build contest", leads=["building", "town", "decor"],
         end_leads=["building", "town", "jokes"], needs=["a:in_town"], weight=1.0)


each([
    dict(mine="a castle with a moat", mine_lines=["fancy", "a moat!", "nice"],
         rival=["someone else built a dirt hut", "one entry was a dirt hut", "there was a dirt hut entry"], r_react="laugh",
         r_lines=["lol", "a dirt hut", "bold"], rival2=["with one flower in front", "and a single flower by the door", "one torch, one flower"],
         result=["the dirt hut won", "and the dirt hut won", "the dirt hut. won."], res_react="laugh", res_lines=["WHAT", "lmao", "no way"],
         result2=["everyone said it was charming", "they said it had charm", "everyone loved it, somehow"], res2_lines=["charming lol", "the flower", "unbelievable"],
         result3=["i'm still not over it", "i'm still upset", "the castle lost to dirt"],
         end=["i asked them how they did it", "i went and asked the winner", "i talked to the winner after"], end_lines=["and?", "what'd they say", "lol"],
         end2=["they said: heart", "they said it was the flower", "they said: you tried too hard"]),
    dict(mine="a big treehouse", mine_lines=["cute", "a treehouse!", "nice"],
         rival=["my neighbour built a giant pumpkin", "the house next door built a pumpkin", "someone made a huge pumpkin"], r_react="laugh",
         r_lines=["a pumpkin?", "lol", "big pumpkin"], rival2=["a house inside a giant pumpkin", "you could live inside it", "it was a pumpkin house"],
         result=["i came second. to the pumpkin", "the pumpkin beat me", "second place. behind the pumpkin"], res_react="laugh", res_lines=["lol", "second is good", "the pumpkin"],
         result2=["second place got a cake", "i got a cake for second", "my prize was a cake"], res2_lines=["a cake!", "nice", "lol"],
         result3=["shared it with the pumpkin builder", "i shared it with the winner", "split it with them"],
         end=["we're friends now", "now we build together", "we're building partners now"], end_lines=["aw", "nice", "cute"],
         end2=["next year we're entering together", "teaming up next time", "we'll win next year"]),
    dict(mine="a tall stone tower", mine_lines=["classic", "tall", "nice"],
         rival=["a creeper blew up half the entries the night before", "a creeper blew up some entries", "creepers got into the contest area"], r_react="gasp",
         r_lines=["oh no", "sabotage", "creepers!"], rival2=["mine was stone. it survived", "my stone tower was fine", "stone survived it"],
         result=["i won. because mine was still standing", "won by default. mostly", "i won. the only one standing"], res_react="laugh", res_lines=["lol", "a win's a win", "stone gang"],
         result2=["everyone else's were craters", "the rest were holes", "craters everywhere"], res2_lines=["lol", "rough", "poor them"],
         result3=["i felt a bit bad", "i felt kinda guilty", "it didn't feel right"],
         end=["so i helped everyone rebuild", "so i helped them all fix theirs", "i helped rebuild the others"], end_lines=["aww", "good", "a hero"],
         end2=["they voted me best neighbour after", "town loved me after that", "everyone thanked me"]),
    dict(mine="a giant fish statue", mine_lines=["a fish?", "lol", "bold"],
         rival=["everyone else built houses", "all the others were houses", "only houses, and my fish"], r_react="laugh",
         r_lines=["the fish stands out", "lol", "unique"], rival2=["mine was the only weird one", "the fish was the weird one", "and then my fish"],
         result=["got the most votes. the fish won", "the fish won!", "first place: fish"], res_react="wow", res_lines=["THE FISH", "no way", "w"],
         result2=["they made it the town mascot", "it's the town mascot now", "the fish is our symbol now"], res2_lines=["lol", "iconic", "love it"],
         result3=["it's on our banner", "it's on the town banner", "we have fish banners now"],
         end=["now people call our town fish town", "everyone calls it fish town now", "the town's called fish town now"], end_lines=["lmao", "fish town", "perfect"],
         end2=["i'm a legend here", "i'm famous here now", "i made fish town"]),
    dict(mine="a little garden with a bench", mine_lines=["cute", "aw", "cozy"],
         rival=["the others built huge things. castles, towers", "everyone went big", "others built giant stuff"], r_react="laugh",
         r_lines=["your little garden", "lol", "small but cozy"], rival2=["mine was the smallest by far", "mine was tiny next to theirs", "my garden looked so small"],
         result=["came last. dead last", "i came last", "last place"], res_react="sad", res_lines=["aw", "oh no", "rip"],
         result2=["but everyone kept sitting on my bench", "but everyone sat on my bench", "but people kept using my bench"], res2_lines=["aww", "the real winner", "nice"],
         result3=["still do, every day", "they still sit there every day", "every evening someone sits there"],
         end=["the castle that won? nobody goes there", "the winner's castle is empty", "nobody visits the winning castle"], end_lines=["lol", "true winner", "aww"],
         end2=["my garden's the busiest spot in town", "my bench is the most popular place", "everyone loves my garden"]),
], contest)

# --- The tallest tower.
def tower(n, v):
    p1 = P(
        A("i built a tower once. the tallest one around", "ever tried building to the sky? i did", "my tower story. it's a tall one"),
        B(None, "how tall", "lol", "why", "go on", "to the sky?"),
        A(f"all the way up. {v.material}", f"out of {v.material}, all the way up", f"{v.material}, block after block, up and up"),
        B(None, "that's a lot of blocks", "dedication", "why though", "nice", "go on"),
        A("until i hit the top of the world. you can't go higher", "until the sky said: no more", "right up to where you can't build anymore"),
        B("wow", "the top!", "no way", "that's so high"),
    )
    p2 = P(
        A("the view from up there. everything", "you could see everything from up there", "the view was unreal"),
        B("aw", "worth it", "beautiful", "nice"),
        A(*v.event),
        B(v.e_react, *v.e_lines),
        A(*v.event2, chance=0.85),
    )
    p3 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B("done", "tower life", "what a build", "lol"),
        A(*v.lesson, chance=0.6),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A(*v.epi),
            B(None, *v.epi_lines),
            A(*v.epi2),
            B(None, "nice", "lol", "love it", "perfect"),
            A("best build i ever made", "still my favourite thing i built", "i'm proud of it", chance=0.6),
        )
    tell("tower_" + n, [p1, p2, p3, p4], ["building", "sky", "brag"], "the tower", leads=["building", "sky", "brag"],
         end_leads=["building", "sky", "jokes"])


each([
    dict(material="cobblestone", event=["then a storm rolled in", "then the sky went dark. thunder", "and a thunderstorm started"],
         e_react="gasp", e_lines=["lightning!", "get down", "oh no"], event2=["i'm the tallest thing for miles", "i was the highest point anywhere", "tallest thing in the world, in a storm"],
         end=["lightning hit right next to me", "lightning struck the top. right by me", "it hit the tower"], end_react="gasp", end_lines=["NO", "you ok?", "oh no"],
         end2=["set me on fire. jumped off. landed in a lake", "i caught fire and jumped. lake. lucky", "burning, i jumped. into water"],
         lesson=["lightning rod on top now", "put a lightning rod up there", "copper on top now"],
         epi=["now i go up there to watch storms", "now i watch storms from up there. safely", "i watch the lightning from the top now"],
         epi_lines=["safely?", "brave", "lol"], epi2=["with a lightning rod. very safely", "the rod takes every hit", "the rod gets hit, i watch"]),
    dict(material="dirt. i know. i know", event=["then the phantoms came", "phantoms started circling", "it got dark and phantoms showed up"],
         e_react="gasp", e_lines=["phantoms up there", "oh no", "the worst place"], event2=["they knocked me around up there", "they kept hitting me", "swooping at me nonstop"],
         end=["one knocked me right off the top", "one hit me off the edge", "got knocked off"], end_react="gasp", end_lines=["off the TOP?", "no", "rip"],
         end2=["fell for like ten seconds. splat", "longest fall ever. dead", "a very long fall. dead"],
         lesson=["i sleep before i build now", "sleep first, build after", "go to bed, then build"], epi=None),
    dict(material="stone bricks, with windows", event=["then the clouds went past. below me", "the clouds were under me", "clouds floating by under my feet"],
         e_react="aw", e_lines=["above the clouds", "so cool", "wow"], event2=["i just sat there for an hour", "sat on the edge for ages", "i didn't want to come down"],
         end=["then i had to come down", "then i realised i had to climb down", "and then: the way down"], end_react="laugh", end_lines=["lol", "the stairs", "uh oh"],
         end2=["took longer to get down than to build", "climbing down took forever", "so many stairs"],
         lesson=["put a water drop in after", "built a water drop down the middle", "water elevator now"],
         epi=["now my friends come up to watch the sunset", "now people come up for the sunset", "it's where everyone goes for sunsets"],
         epi_lines=["aw", "nice", "cute"], epi2=["we bring food and sit up there", "we have picnics up there", "it's the best spot in town"]),
    dict(material="glass, so i could see out", event=["then i looked down. through the glass floor", "then i looked straight down through the glass", "i looked down. glass floor. so high"],
         e_react="laugh", e_lines=["nope", "lol", "glass floor??"], event2=["my legs went weak", "i got so scared", "i froze up"],
         end=["i had to crawl down. sneaking the whole way", "sneaked all the way down", "came down crouching the whole time"], end_react="laugh",
         end_lines=["lol", "sneak mode", "careful"], end2=["took an hour", "took ages", "slowest climb ever"],
         lesson=["no more glass floors", "stone floors now. always", "never glass under my feet again"], epi=None),
    dict(material="logs from a whole forest", event=["then an enderman took a block from the middle", "an enderman pulled a log out halfway up", "an enderman stole a block from my tower"],
         e_react="laugh", e_lines=["rude", "lol", "endermen"], event2=["now there's a hole in it", "left a hole right in the middle", "a gap in my tower"],
         end=["i left it. it's a window now", "i kept the hole. it's a window", "the hole is a window now"], end_react="laugh",
         end_lines=["lol", "a feature", "design"], end2=["the enderman's window", "i call it the enderman window", "the endermen helped design it"],
         lesson=["builders adapt", "every mistake is a feature", "it's art now"],
         epi=["then a parrot started sitting in it", "a parrot sits in the window now", "a parrot lives in the hole"],
         epi_lines=["aww", "cute", "lol"], epi2=["it's the best part of the tower now", "best part of the build", "now it's perfect"]),
], tower)


# --- Bridging across something.
def bridge(n, v):
    p1 = P(
        A(f"i built a bridge across {v.across} once", f"so i had to cross {v.across}", f"ever bridged across {v.across}? i did"),
        B(None, "brave", "how far", "nice", "go on", "oh no"),
        A("sneaking. one block at a time. backwards", "crouching, placing, one block at a time", "sneak, place, step. over and over"),
        B(None, "careful", "classic bridging", "don't look down", "go on", "steady"),
        A(f"about halfway, {v.below} below me", f"halfway there. {v.below} under me", f"the middle. nothing but {v.below} underneath"),
        B("ask", "uh oh", "don't fall", "and?"),
    )
    p2 = P(
        A(*v.problem),
        B(v.p_react, *v.p_lines),
        A(*v.problem2),
        B(v.p2_react, *v.p2_lines),
        A(*v.problem3, chance=0.85),
    )
    p3 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B("done", "bridges are scary", "lol", "good story"),
        A(*v.lesson, chance=0.6),
    )
    p4 = None
    if v.epi:
        p4 = P(
            A(*v.epi),
            B(None, *v.epi_lines),
            A(*v.epi2),
            B(None, "nice", "lol", "love it", "perfect"),
            A("all from one scary trip", "funny how it worked out", "best bridge ever", chance=0.6),
        )
    tell("bridge_" + n, [p1, p2, p3, p4], ["building", "danger", "exploring"], "the bridge", leads=["building", "danger", "exploring"],
         end_leads=["building", "jokes", "danger"])


each([
    dict(across="a huge ravine", below="a long drop",
         problem=["a skeleton starts shooting at me from the other side", "a skeleton on the far side starts shooting", "arrows from across the ravine"],
         p_react="gasp", p_lines=["no", "the knockback", "oh no"], problem2=["every arrow knocked me back a bit", "each hit pushed me to the edge", "knockback, right at the edge"],
         p2_react="gasp", p2_lines=["don't fall", "hold on", "oh no"], problem3=["i put up a block to hide behind", "placed a block as a shield", "built a little wall"],
         end=["made it across, killed the skeleton", "got across and got it", "made it, and hit it off the edge"], end_react="wow",
         end_lines=["nice", "revenge", "clutch"], end2=["kicked it into the ravine", "it fell in the ravine", "it fell. not me"],
         lesson=["walls on all my bridges now", "railings, always", "always railings"], epi=None),
    dict(across="a wide river", below="water",
         problem=["i ran out of blocks", "my last block. still five to go", "out of blocks, five blocks short"],
         p_react="laugh", p_lines=["lol", "swim the rest", "classic"], problem2=["so i jumped in and swam", "swam the rest", "jumped in and swam it"],
         p2_react="laugh", p2_lines=["lol", "the bridge to nowhere", "fair"], problem3=["the bridge just stopped in the middle", "half a bridge", "the bridge ends in the river"],
         end=["it's still there. half a bridge", "the half bridge is still there", "it's a pier now. on purpose"], end_react="laugh",
         end_lines=["a pier lol", "a feature", "lol"], end2=["people fish off the end now", "it's a fishing spot now", "everyone fishes there now"],
         lesson=["count your blocks", "always bring extra blocks", "count before you build"],
         epi=["someone finished it for me later", "a stranger finished it", "someone added the last five blocks"],
         epi_lines=["aww", "nice", "teamwork"], epi2=["in a different block. it looks weird", "in a different colour. i love it", "with stone. mine was wood"]),
    dict(across="the ocean to an island", below="deep water",
         problem=["night came halfway", "it got dark halfway across", "the sun set, middle of the sea"],
         p_react="gasp", p_lines=["drowned", "oh no", "keep going"], problem2=["drowned came up on both sides", "drowned climbing out of the water", "drowned grabbing at the bridge"],
         p2_react="gasp", p2_lines=["no", "go go", "faster"], problem3=["i just kept placing", "kept going, didn't look", "didn't stop"],
         end=["made it to the island at sunrise", "reached the island in the morning", "got there just as the sun came up"], end_react="relief",
         end_lines=["made it", "phew", "nice"], end2=["it was the most beautiful island", "best island ever", "white sand and one tree"],
         lesson=["i live there now", "moved there", "best move ever"], epi=None),
    dict(across="a lava lake", below="lava",
         problem=["i let go of sneak for one second", "my finger slipped off sneak", "i stopped sneaking for a moment"],
         p_react="gasp", p_lines=["NO", "the edge", "oh no"], problem2=["stepped right off the edge", "slid off the side", "walked right off"],
         p2_react="gasp", p2_lines=["into the lava??", "no", "rip"], problem3=["caught the edge of the block. somehow", "grabbed the edge somehow", "landed on the block below"],
         end=["there was one block i'd placed by accident. landed on it", "an accidental block saved me", "a stray block i'd placed caught me"], end_react="wow",
         end_lines=["luck", "what", "no way"], end2=["one random block saved my life", "a misclick saved me", "a mistake block saved me"],
         lesson=["i never let go of sneak now", "my sneak finger is glued down", "sneaking forever"], epi=None),
    dict(across="a valley between two mountains", below="trees, way down",
         problem=["a phantom swooped at me", "phantoms came out", "a phantom hit me from above"],
         p_react="gasp", p_lines=["phantoms!", "oh no", "hold on"], problem2=["knocked me off the side", "pushed me off the edge", "i went over the side"],
         p2_react="gasp", p2_lines=["NO", "the fall", "oh no"], problem3=["into the trees", "fell into the leaves", "landed in a tree"],
         end=["the leaves broke the fall. kind of", "the leaves helped. a bit", "leaves, then ground. two hearts left"], end_react="relief",
         end_lines=["phew", "trees!", "lucky"], end2=["climbed back up and finished it", "climbed back up, finished the bridge", "went back up and finished"],
         lesson=["now i build bridges in the day", "daytime building only", "no bridges at night"],
         epi=["now it's the main road between towns", "it's a road now", "everyone uses it now"], epi_lines=["nice", "famous", "wow"],
         epi2=["they named it after me", "it has my name on a sign", "my name's on it"]),
], bridge)


# =====================================================================================================================
# IN ITS OWN VOICE
# =====================================================================================================================

# --- A newbie's first night.
def newbie_night(n, v):
    p1 = P(
        A("ok so my first night ever was SO scary", "my first night!! i didnt know anything", "can i tell you about my first night? it was crazy"),
        B("aw", "first nights are the worst", "aww", "tell me"),
        A("i punched a tree and got wood and i was so proud", "i figured out how to punch trees!! wood!!", "i got wood by punching a tree. so cool"),
        B("aw", "the first wood", "lol", "nice"),
        A("and then the sun went down and i didnt have a house", "then it got dark and i had nowhere to go", "and then the sky went orange and i panicked"),
        B("ask", "uh oh", "what did you do", "and?"),
    )
    p2 = P(
        A(*v.shelter),
        B(v.s_react, *v.s_lines),
        A(*v.shelter2),
        B(None, *v.s2_lines),
        A("and then i heard noises", "and then the noises started", "and then i heard stuff outside", chance=0.9),
    )
    p3 = P(
        A(*v.event),
        B(v.e_react, *v.e_lines),
        A(*v.event2),
        B(v.e2_react, *v.e2_lines),
        A(*v.event3, chance=0.85),
    )
    p4 = P(
        A("and then the sun came up and i SURVIVED", "and then it was morning!! i made it", "and in the morning i was alive!!"),
        B("wow", "you made it!", "proud of you", "first night done"),
        A(*v.learned),
        B(None, "good lesson", "smart", "you're learning", "nice", "true"),
        A("now i know way more stuff", "im way better now", "i still get scared a bit tho", chance=0.6),
    )
    tell("newbienight_" + n, [p1, p2, p3, p4], ["night", "memories", "mobs"], "my first night", leads=["night", "memories", "help"],
         end_leads=["night", "advice", "help"], needs=["a:voice_newbie"], weight=1.5)


each([
    dict(shelter=["i dug into a hill and blocked the hole", "i dug a hole in a hill and closed it up", "i made a little hole in a hill"],
         s_react="aw", s_lines=["the classic hole", "smart", "everyone starts there"], shelter2=["it was so dark in there", "it was pitch black", "no torches. so dark"], s2_lines=["scary", "no torches?", "brave"],
         event=["something was scratching the dirt", "i heard something hitting the dirt", "scratch scratch on the wall"],
         e_react="creepy", e_lines=["a zombie", "uh oh", "it can't get in"], event2=["i just stood there and didnt move", "i stayed so still", "i held my breath"],
         e2_react="aw", e2_lines=["good call", "smart", "aw"], event3=["it went away eventually", "then it stopped", "after a while it left"],
         learned=["torches!! you make them with coal and sticks", "i learned about torches the next day", "now i always have torches"]),
    dict(shelter=["i built a dirt hut. one block wide lol", "i made a tiny dirt house", "dirt hut. very small. very ugly"],
         s_react="laugh", s_lines=["the dirt hut", "lol", "iconic"], shelter2=["i didnt make a door. just a hole", "no door, just a hole", "i forgot the door"], s2_lines=["no door??", "uh oh", "lol"],
         event=["a spider climbed on the roof", "a spider got on top of my hut", "a big spider on my roof"],
         e_react="gasp", e_lines=["spiders climb", "oh no", "nope"], event2=["and then it jumped in the hole", "it came in through the hole", "it found the door hole"],
         e2_react="gasp", e2_lines=["no", "fight it", "run"], event3=["i hit it with my wood sword like 20 times", "i hit it so much with my wooden sword", "i fought it with a wood sword"],
         learned=["doors are important. like really important", "always make a door!!", "i make doors first now"]),
    dict(shelter=["i climbed a tree and stayed up there", "i hid up in a tree", "i sat on top of a tree all night"],
         s_react="laugh", s_lines=["a tree lol", "creative", "not bad"], shelter2=["zombies just walked under me", "the zombies couldnt reach me", "i watched zombies walk by under me"], s2_lines=["lol", "safe up there", "smart"],
         event=["then a skeleton started shooting at me", "a skeleton saw me and started shooting", "arrows!! from a skeleton"],
         e_react="gasp", e_lines=["oh no", "get down", "hide"], event2=["i fell out of the tree", "i got knocked off the tree", "i fell down"],
         e2_react="gasp", e2_lines=["into the zombies?", "oh no", "run"], event3=["i ran all night in circles lol", "ran around till morning", "i just ran and ran"],
         learned=["you need a bed to skip the night", "beds skip the night!! nobody told me", "beds are the best thing ever"]),
    dict(shelter=["i found a village and hid in a house", "i ran into a village and got in a house", "i hid in a villager house"],
         s_react="relief", s_lines=["lucky", "smart", "villages are safe-ish"], shelter2=["a villager was in there just staring at me", "the villager kept looking at me", "a villager stared at me all night"], s2_lines=["lol", "they do that", "creepy"],
         event=["then zombies came and knocked on the door", "zombies came to the door", "something banged on the door"],
         e_react="gasp", e_lines=["zombies!", "hold the door", "oh no"], event2=["the iron golem came and smashed them", "a big iron guy beat them up", "a golem fought them"],
         e2_react="wow", e2_lines=["the golem!", "hero", "golems are great"], event3=["i didnt know golems existed", "i thought it was going to hit me too", "i was so scared of it at first"],
         learned=["iron golems are friends. dont hit them", "golems are good guys", "villages are safe because of golems"]),
    dict(shelter=["i didnt find anywhere so i just kept walking", "i just walked around all night", "i didnt hide i just walked"],
         s_react="gasp", s_lines=["oh no", "brave", "that's dangerous"], shelter2=["i didnt know night was dangerous", "i thought night was just dark", "nobody told me about mobs"], s2_lines=["oh no", "uh oh", "oh it is"],
         event=["then a green guy walked up to me", "a green thing came up really close", "this green thing walked up to me. no arms"],
         e_react="gasp", e_lines=["a creeper", "RUN", "oh no"], event2=["it hissed and then BOOM", "it made a hiss noise and exploded", "tssss and then it blew up"],
         e2_react="sad", e2_lines=["oh no", "rip", "welcome to the world"], event3=["i woke up back where i started", "i woke up at the start", "i respawned with nothing"],
         learned=["creepers. green. no arms. RUN", "never let the green ones get close", "if it hisses you run"]),
], newbie_night)


# --- A speedrunner's rush to the dragon.
def speed_rush(n, v):
    p1 = P(
        A("tried to beat the dragon in one day once", "one day dragon attempt. listen", "fastest i ever tried to beat the dragon"),
        B(None, "one day??", "why", "crazy", "go on", "did it work"),
        A("iron in the first hour. bucket, shield, go", "first hour: iron, bucket, straight to lava", "got iron fast, made a bucket, found lava"),
        B(None, "fast", "efficient", "go on", "and the portal?", "nice"),
        A("bucket portal by noon. into the nether", "portal by noon. nether", "lit a portal by midday"),
        B("ask", "and?", "blazes next", "go go"),
    )
    p2 = P(
        A(*v.nether),
        B(v.n_react, *v.n_lines),
        A(*v.nether2),
        B(v.n2_react, *v.n2_lines),
        A(*v.nether3, chance=0.85),
    )
    p3 = P(
        A(*v.stronghold),
        B(v.s_react, *v.s_lines),
        A(*v.stronghold2),
        B(None, *v.s2_lines),
        A(*v.stronghold3, chance=0.85),
    )
    p4 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B("done", "you're crazy", "one day lol", "respect"),
        A("next time. sub one day", "trying again tomorrow", "i'll get it next time", chance=0.7),
    )
    tell("rush_" + n, [p1, p2, p3, p4], ["end", "nether", "brag"], "the rush", leads=["end", "nether", "brag"],
         end_leads=["end", "brag", "jokes"], needs=["a:voice_speedrunner", "a:knows_dragon"], weight=1.5)


each([
    dict(nether=["found a fortress in five minutes", "fortress right away. lucky", "fortress was close"], n_react="wow", n_lines=["lucky", "nice", "good start"],
         nether2=["seven blaze rods. done", "got seven rods fast", "seven rods. out"], n2_react="wow", n2_lines=["efficient", "fast", "nice"],
         nether3=["then bartered for pearls", "traded gold with piglins for pearls", "piglins gave me pearls"],
         stronghold=["threw an eye. it went way off", "eye of ender pointed miles away", "stronghold was miles off"], s_react="gasp", s_lines=["far?", "oh no", "how far"],
         stronghold2=["two thousand blocks", "two thousand blocks away", "so far"], s2_lines=["ouch", "that's a lot", "oof"],
         stronghold3=["ran the whole way", "sprinted it all", "ran through the night"],
         end=["got there. dragon took forever. day two", "made it. beat it. day two", "beat it the next morning"], end_react="laugh",
         end_lines=["close", "day two lol", "almost"], end2=["one day and a bit", "a day and a half", "not one day. close"]),
    dict(nether=["no fortress. looked for an hour", "couldn't find a fortress. an hour", "an hour, no fortress"], n_react="laugh", n_lines=["rip", "time wasted", "lol"],
         nether2=["then fell in lava looking", "then i fell in lava", "lava. dead"], n2_react="gasp", n2_lines=["NO", "reset", "rip"],
         nether3=["lost everything", "lost the whole kit", "everything gone"],
         stronghold=["so i started over", "started from zero", "reset"], s_react="laugh", s_lines=["of course", "dedication", "lol"],
         stronghold2=["second try, fortress in two minutes", "second time, fortress right away", "found one instantly the second time"], s2_lines=["of course", "lol", "typical"],
         stronghold3=["but it was night by then", "but the sun was already down", "already dark"],
         end=["beat it on day three", "finished on day three", "day three. dragon dead"], end_react="laugh",
         end_lines=["lol", "three days", "still fast"], end2=["worst run ever. still beat it", "bad run. still won", "slow. still counts"]),
    dict(nether=["bastion first. for pearls", "went to a bastion for pearls", "bastion. for pearls and gold"], n_react="gasp", n_lines=["risky", "brutes", "bold"],
         nether2=["a brute chased me out", "a piglin brute ran me out", "brute with an axe. ran"], n2_react="laugh", n2_lines=["lol", "brutes", "run"],
         nether3=["got four pearls though", "still got four pearls", "four pearls, worth it"],
         stronghold=["stronghold under a village", "the stronghold was under a village", "dug down in a village. stronghold"], s_react="wow", s_lines=["lucky", "close", "nice"],
         stronghold2=["portal room right there", "found the portal room fast", "portal room in minutes"], s2_lines=["fast", "nice", "go go"],
         stronghold3=["only needed two eyes. lucky", "only two eyes missing", "eyes already in, mostly"],
         end=["forgot beds. had to fight it normal", "no beds. fought it with a sword", "forgot the beds. sword only"], end_react="laugh",
         end_lines=["lol beds", "the beds!", "slow"], end2=["still won. day one, barely", "won. just before night", "beat it by sunset"]),
    dict(nether=["portal came out over a lava sea", "spawned over a lava ocean", "portal on a lava sea"], n_react="gasp", n_lines=["bad spawn", "oof", "careful"],
         nether2=["bridged for ages", "bridged forever", "so much bridging"], n2_react="laugh", n2_lines=["slow", "lol", "rip time"],
         nether3=["ghast blew up the bridge twice", "ghasts broke my bridge", "two ghasts, two broken bridges"],
         stronghold=["gave up on the nether. went back", "left the nether", "got out"], s_react="laugh", s_lines=["lol", "giving up?", "rip"],
         stronghold2=["found a new portal spot", "new portal, new spawn", "made a new portal"], s2_lines=["better?", "and?", "ok"],
         stronghold3=["came out next to a fortress. of course", "right next to a fortress", "fortress right there. now"],
         end=["didn't beat it that day", "didn't finish that day", "ran out of day"], end_react="laugh",
         end_lines=["rip", "next time", "lol"], end2=["beat it a week later. not a speedrun", "took a week in the end", "beat it eventually. slowly"]),
    dict(nether=["fortress, rods, pearls. perfect", "perfect nether. everything fast", "everything went right in the nether"], n_react="wow", n_lines=["perfect", "nice", "god run"],
         nether2=["out in twenty minutes", "out of the nether fast", "twenty minutes in there"], n2_react="wow", n2_lines=["fast", "insane", "nice"],
         nether3=["threw the first eye", "first eye of ender up", "eye up"],
         stronghold=["stronghold close. portal room fast", "stronghold right there", "found the portal room fast"], s_react="wow", s_lines=["perfect", "nice", "go go"],
         stronghold2=["jumped in. dragon time", "into the end", "in the end. dragon"], s2_lines=["go go", "nice", "now the dragon"],
         stronghold3=["one-cycled it with beds", "bed damage, dead in one go", "beds. one cycle. dead"],
         end=["beat it before sunset. one day", "dragon dead. one day", "one day. done"], end_react="wow",
         end_lines=["ONE DAY", "insane", "respect"], end2=["never managed it again", "never done it since", "once in my life"]),
], speed_rush)


# --- A hoarder and its chests.
def hoarder(n, v):
    p1 = P(
        A("i had the best storage room anyone's ever seen", "my storage room was perfect. was", "let me tell you about my chests"),
        B("smirk", "here we go", "how many chests", "of course"),
        A(f"{v.count} double chests. all full", f"{v.count} double chests, every one full", f"full chests. {v.count} of them"),
        B(None, f"{v.count}??", "too many", "why", "a lot", "hoarder"),
        A("cobble, dirt, every flower, everything", "i kept everything. even the dirt", "every item. never threw anything out"),
        B("laugh", "even dirt", "classic", "lol"),
    )
    p2 = P(
        A(*v.disaster),
        B(v.d_react, *v.d_lines),
        A(*v.disaster2),
        B(v.d2_react, *v.d2_lines),
        A(*v.disaster3, chance=0.85),
    )
    p3 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B(None, *v.end2_lines),
        A(*v.end3, chance=0.8),
    )
    p4 = P(
        A(*v.now),
        B("laugh", *v.now_lines),
        A(*v.now2),
        B("done", "never change", "hoarder forever", "lol"),
        A("my stuff. mine", "never throwing anything out", "you never know when you'll need dirt", chance=0.6),
    )
    tell("hoarder_" + n, [p1, p2, p3, p4], ["storage", "home", "jokes"], "my chests", leads=["storage", "home", "jokes"],
         end_leads=["storage", "jokes", "home"], needs=["a:voice_hoarder"], weight=1.5)


each([
    dict(count="forty", disaster=["one day i couldn't find my diamonds", "i lost my diamonds. somewhere in there", "i couldn't find one thing. diamonds"],
         d_react="laugh", d_lines=["lol", "too many chests", "oh no"], disaster2=["opened every chest. every one", "checked all forty", "went through every chest"],
         d2_react="laugh", d2_lines=["forty chests", "lol", "took forever"], disaster3=["took the whole day", "all day", "hours and hours"],
         end=["they were in my pocket the whole time", "they were in my inventory", "i had them on me"], end_react="laugh",
         end_lines=["LMAO", "your pocket", "no way"], end2=["i'd been carrying them all day", "the whole time. in my pocket", "all day, on me"],
         end2_lines=["lol", "classic", "of course"], end3=["i labelled every chest after that", "signs on every chest now", "labels everywhere now"],
         now=["now i have fifty chests. labelled", "fifty chests now. all signed", "fifty. with signs"], now_lines=["fifty lol", "growing", "of course"],
         now2=["still can't find anything", "still lose stuff", "still lose things all the time"]),
    dict(count="thirty", disaster=["a creeper got into the storage room", "creeper. storage room. you know", "a creeper walked in"],
         d_react="gasp", d_lines=["NO", "the chests", "oh no"], disaster2=["blew up six chests", "six chests gone", "boom. six chests"],
         d2_react="gasp", d2_lines=["six!", "items everywhere", "pick it up"], disaster3=["items everywhere. everywhere", "stuff flying all over", "items in every corner"],
         end=["picked up every single thing", "picked it all up. every block", "got every last item"], end_react="laugh",
         end_lines=["every dirt block?", "lol", "of course you did"], end2=["even the dirt. especially the dirt", "even the seeds", "every seed. every dirt"],
         end2_lines=["lol", "dedication", "hoarder"], end3=["took three hours", "hours", "all night"],
         now=["now the storage room is underground. stone walls", "storage is underground now", "moved it underground"], now_lines=["smart", "creeper proof", "lol"],
         now2=["three blocks of stone on every side", "stone walls three thick", "nothing gets in"]),
    dict(count="twenty", disaster=["had to move house. had to carry it all", "i moved. had to carry all of it", "moved house with all my chests"],
         d_react="laugh", d_lines=["oh no", "lol", "how"], disaster2=["twenty trips. back and forth", "back and forth for days", "trip after trip"],
         d2_react="laugh", d2_lines=["lol", "days", "dedication"], disaster3=["the new house was full on day one", "the new place filled up instantly", "full right away"],
         end=["so i built a bigger room", "built a second storage room", "dug out more room"], end_react="laugh",
         end_lines=["of course", "lol", "more chests"], end2=["filled that too", "full again", "that filled up too"],
         end2_lines=["lol", "endless", "classic"], end3=["a week later", "in a week", "a week"],
         now=["now i'm thinking of a warehouse", "planning a warehouse now", "a warehouse. that's next"], now_lines=["a warehouse lol", "of course", "hoarder"],
         now2=["for my extra dirt", "the dirt needs a home", "dirt deserves space"]),
    dict(count="fifty", disaster=["a friend asked to borrow one stack of cobble", "someone wanted one stack of cobble", "a friend asked me for cobble"],
         d_react="laugh", d_lines=["and?", "did you", "uh oh"], disaster2=["i said no. then yes. then no", "i thought about it for an hour", "i couldn't decide"],
         d2_react="laugh", d2_lines=["lol", "for cobble?", "it's cobble"], disaster3=["i have forty stacks of cobble", "i have so much cobble", "forty stacks. still hard"],
         end=["gave them half a stack", "i gave them thirty two cobble", "half a stack. final offer"], end_react="laugh",
         end_lines=["lol", "generous", "wow"], end2=["they said thanks. i said bring it back", "i asked for it back", "i told them to return it"],
         end2_lines=["lol", "it's cobble", "of course"], end3=["they did. a week later", "they brought it back", "got it back"],
         now=["we're still friends", "we're still friends somehow", "they still talk to me"], now_lines=["somehow", "lol", "nice"],
         now2=["they don't ask for stuff anymore", "they don't borrow anymore", "they never ask now"]),
    dict(count="sixty", disaster=["a bandit broke in", "bandits broke in one night", "someone raided my chests"],
         d_react="gasp", d_lines=["NO", "a bandit", "oh no"], disaster2=["they opened three chests and left", "they looked in three chests", "they took a look and gave up"],
         d2_react="laugh", d2_lines=["gave up?", "why", "lol"], disaster3=["too much stuff. they couldn't find anything good", "they couldn't find anything in the mess", "too much junk to search"],
         end=["they took one stack of dirt", "they left with a stack of dirt", "all they took was dirt"], end_react="laugh",
         end_lines=["LOL", "dirt", "the mess saved you"], end2=["my mess saved my diamonds", "the clutter protected me", "being messy saved me"],
         end2_lines=["lol", "big brain", "strategy"], end3=["i still want that dirt back", "i want my dirt back", "that was my dirt"],
         now=["now i call it a security system", "it's my security system now", "messy is safe"], now_lines=["lol", "genius", "sure"],
         now2=["nobody can find anything. not even me", "even i can't find stuff", "nobody finds anything"]),
], hoarder)


# --- A duel (pvp_sweat).
def duel(n, v):
    p1 = P(
        A("had a duel once. someone talked big", "someone challenged me once. big mistake. maybe", "duel story. you'll like this"),
        B(None, "a duel?", "lol", "who won", "go on", "of course"),
        A(f"they said they could beat me with {v.their}", f"they had {v.their} and a big mouth", f"they showed up with {v.their}"),
        B(None, *v.their_lines),
        A("i had my diamond sword. easy, i thought", "i had my sword. thought it'd be quick", "diamond sword. should be easy"),
        B("ask", "famous last words", "and?", "was it"),
    )
    p2 = P(
        A("we count down. three two one", "countdown. go", "three, two, one, fight"),
        B(None, "go", "and?", "here we go", "who hit first", "go on"),
        A(*v.fight),
        B(v.f_react, *v.f_lines),
        A(*v.fight2, chance=0.9),
    )
    p3 = P(
        A(*v.end),
        B(v.end_react, *v.end_lines),
        A(*v.end2),
        B(None, *v.end2_lines),
        A(*v.end3, chance=0.8),
    )
    p4 = P(
        A(*v.after),
        B(None, *v.after_lines),
        A(*v.after2),
        B("done", "duels are fun", "rematch", "lol"),
        A("anyone else want to try", "i'm ready for the next one", "i'll take anyone on", chance=0.6),
    )
    tell("duel_" + n, [p1, p2, p3, p4], ["fighting", "brag", "rivals"], "the duel", leads=["fighting", "brag", "weapons"],
         end_leads=["fighting", "brag", "rivals"], needs=["a:voice_pvp_sweat"], weight=1.5)


each([
    dict(their="a stone sword", their_lines=["stone lol", "easy", "bold"],
         fight=["i rush in. they back off", "i go in, they step back", "i charge, they dodge"], f_react="ask", f_lines=["and?", "go on", "hit them"],
         fight2=["three crits. they're done", "jump crits. three. done", "crit crit crit. over"],
         end=["won in like five seconds", "five seconds. done", "over before it started"], end_react="laugh", end_lines=["lol", "too easy", "rip them"],
         end2=["they said i got lucky", "they called it luck", "they said lucky"], end2_lines=["lol", "cope", "sure"],
         end3=["rematch. won again", "best of three. won all", "beat them again"],
         after=["they asked me to teach them", "now i'm teaching them", "they wanted lessons after"], after_lines=["aww", "a student", "nice"],
         after2=["they're not bad now", "they're getting better", "they're decent now"]),
    dict(their="a shield and an axe", their_lines=["axe and shield", "uh oh", "the axe"],
         fight=["i swing. shield. swing. shield", "every hit, blocked", "they blocked everything"], f_react="laugh", f_lines=["the shield", "lol", "annoying"],
         fight2=["then the axe came down. hard", "their axe hit like a truck", "one axe crit took half my health"],
         end=["lost. i actually lost", "they won. i lost", "i lost. don't tell anyone"], end_react="laugh", end_lines=["LOL", "you lost?", "telling everyone"],
         end2=["first time i've lost in ages", "first loss in forever", "my first loss"], end2_lines=["it happens", "lol", "humbled"],
         end3=["i got a shield the next day", "made a shield right after", "got an axe too"],
         after=["rematch the next day. i won", "won the rematch", "won the second time"], after_lines=["redemption", "nice", "w"],
         after2=["we duel every week now", "it's a weekly thing now", "rivals for life"]),
    dict(their="a bow and a lot of arrows", their_lines=["a bow?", "range", "cheese"],
         fight=["they ran and shot. ran and shot", "they kited me all over", "run, shoot, run, shoot"], f_react="laugh", f_lines=["kiting", "lol", "coward strats"],
         fight2=["i chased them for ages", "chased them across a field", "i chased them forever"],
         end=["they ran out of arrows", "out of arrows. finally", "their quiver ran dry"], end_react="laugh", end_lines=["lol", "and?", "now you"],
         end2=["then it was over fast", "then it was quick", "one combo. done"], end2_lines=["lol", "nice", "easy"],
         end3=["they said it was a tactical retreat", "they called it strategy", "they said they meant to"],
         after=["now i carry a bow too", "i carry a bow now too", "bow in my offhand now"], after_lines=["fair", "smart", "lol"],
         after2=["just in case", "can't be too careful", "for the runners"]),
    dict(their="full netherite. FULL", their_lines=["netherite??", "uh oh", "you're done"],
         fight=["i couldn't do any damage", "my hits did nothing", "every hit barely tickled"], f_react="gasp", f_lines=["netherite though", "run", "oof"],
         fight2=["so i dug down and hid", "i dug a hole and hid", "i pillared away"],
         end=["called it a draw", "i said: draw", "we agreed: draw"], end_react="laugh", end_lines=["a draw lol", "you ran", "draw?"],
         end2=["it was a draw. it was", "draw. don't argue", "it's a draw"], end2_lines=["sure", "lol", "ok"],
         end3=["they say they won. they didn't", "they tell everyone they won", "they claim the win"],
         after=["i'm saving for netherite now", "mining for netherite every day", "i need netherite now"], after_lines=["revenge", "lol", "good luck"],
         after2=["rematch when i'm ready", "then we'll see", "one day"]),
    dict(their="a fishing rod. a fishing rod", their_lines=["a rod??", "lol", "what"],
         fight=["they hooked me and pulled me around", "they kept yanking me with the hook", "hooked me and pulled"], f_react="laugh", f_lines=["lmao", "rod pvp", "annoying"],
         fight2=["pulled me right into a pond", "pulled me into the water", "dragged me into a lake"],
         end=["i was laughing too hard to fight", "couldn't stop laughing", "i was crying laughing"], end_react="laugh", end_lines=["lol", "and then", "who won"],
         end2=["they hit me with a stick and won", "they won with a stick", "they finished me with a stick"], end2_lines=["LMAO", "a stick", "no way"],
         end3=["i'll never live it down", "everyone knows now", "it's famous now"],
         after=["now i fear fishing rods", "fishing rods scare me now", "i hate fishing rods"], after_lines=["lol", "fair", "as you should"],
         after2=["best duel i ever had though", "most fun i've had fighting", "honestly the best fight ever"]),
], duel)

# =====================================================================================================================
# MORE PARTS FOR THE STORIES ALREADY TOLD IN TWO (topics_stories.json, topics_stories_more.json)
# Each part 2 there picks one of several lines (different twists), so these parts follow from any of them.
# =====================================================================================================================

link_part("story_creeper_house_2", "old_creeper_house", [P(
    A("so the next morning i'm patching the hole", "next day i'm fixing the crater", "so i start rebuilding the next morning"),
    B("ask", "and?", "please tell me that's the end", "uh oh"),
    A("and i hear ANOTHER hiss", "and there's a second one, right in the crater", "and another creeper walks right into the hole"),
    B("gasp", "not again", "no way", "they were lining up"),
    A("this time i got it with my sword first", "this one i hit before it could go off", "i swung first this time. it died"),
    B("relief", "redemption", "nice", "w"),
    A("cats by every door now. creepers hate cats", "torches all round the house now", "lit up every dark spot round the house", chance=0.6),
)], "the creeper story", ["mobs", "home", "jokes"])

link_part("story_lost_diamonds_2", "old_lost_diamonds", [P(
    A("so a few days later i went back to that same vein", "i went back down there with a new iron pick", "next week i went back to the spot"),
    B("ask", "brave", "and?", "please say there were more"),
    A("and the vein went further. i'd missed half of it", "dug one block past where i stopped. more diamonds", "found four more right next to the old spot"),
    B("wow", "no way", "the caves said sorry", "redemption"),
    A("made it home that time. walked so slowly", "walked home on tiptoes", "took the long way home. no lava, no ravines"),
    B("done", "happy ending", "nice", "finally"),
    A("only half what i lost, but i'll take it", "still not as many as before", "the caves owed me", chance=0.6),
)], "the diamonds", ["diamonds", "mining", "brag"])

link_part("story_night_lost_2", "old_night_lost", [P(
    A("and when the sun came up, nothing looked familiar", "morning came and i had no idea where i was", "so morning comes and i'm totally lost"),
    B("gasp", "oh no", "classic", "lost twice"),
    A("so i climbed the tallest tree i could find", "climbed a hill to look around", "i built a dirt pillar to look around"),
    B("ask", "smart", "see anything?", "and?"),
    A("saw my torch tower way off in the distance", "spotted my house, tiny, way off", "saw the smoke from my campfire far away"),
    B("relief", "phew", "lucky", "home!"),
    A("now there's a tall torch tower on my roof", "i built a tower of torches on my roof after", "now i carry a compass", chance=0.7),
)], "that night", ["night", "exploring", "home"])

link_part("story_village_rescue_2", "old_village_rescue", [P(
    A("and the next morning was the best part", "but wait, the morning after", "and you know what happened the next day?"),
    B("ask", "what", "tell me", "go on"),
    A("the villagers started throwing me gifts", "villagers kept tossing me stuff", "every villager i walked past threw me something"),
    B("aw", "they do that?", "free loot", "cute"),
    A("it's a hero thing. bread, arrows, books", "hero of the village. they throw you stuff", "bread from the farmer, arrows from the fletcher"),
    B("aw", "they love you", "aww", "nice"),
    A("i built them a wall after that", "i go back to check on them", "i named their golem buddy", chance=0.6),
)], "the village", ["villagers", "town", "brag"])

link_part("story_ghast_2", "old_ghast", [P(
    A("so the next time i went back, i came prepared", "next trip i went back with a bow and a stack of cobble", "obviously i went back for revenge"),
    B("laugh", "revenge arc", "and?", "go on"),
    A("covered the whole path with cobble. roof and all", "built a cobble tunnel over the lava", "made a covered bridge this time"),
    B("wow", "smart", "ghast proof", "builder brain"),
    A("and a ghast sat outside crying at me for an hour", "a ghast cried at my tunnel all night", "a ghast kept shooting it. nothing"),
    B("laugh", "cry about it", "petty ghast", "lol"),
    A("best nether path i ever made", "that tunnel's still there", "still use that tunnel", chance=0.6),
)], "the ghast", ["nether", "building", "jokes"])

link_part("story_pet_2", "old_pet", [P(
    A("funny thing, last week i saw one that looked just like it", "the other day i saw one exactly like it", "saw one just like it near the river the other day"),
    B("aw", "no way", "was it the same one?", "aww"),
    A("couldn't tell. but it came right up to me", "no idea. but it walked up like it knew me", "maybe. it came straight to me"),
    B("aw", "it remembered you", "it's fate", "cute"),
    A("and it sat next to me like old times", "and it followed me home", "it followed me all the way home"),
    B("aw", "meant to be", "a second chance", "aww"),
    A("it lives with me now", "kept it, obviously", "i think it's the same one. don't tell me otherwise", chance=0.7),
)], "the dog", ["pets", "feelings", "animals"])

link_part("story_first_house_2", "old_first_house", [P(
    A("my second house wasn't much better", "the second house was worse honestly", "then i built a second one. oh boy"),
    B("laugh", "lol go on", "worse??", "how"),
    A("built it over a cave. zombies came up through the floor", "built it all out of glass. mobs could see right in", "built it in a swamp. slimes every night"),
    B("laugh", "lmao", "rookie", "of course"),
    A("third house was the charm", "third time, i built in stone", "the third one had a door AND a roof"),
    B("aw", "growth", "proud of you", "character development"),
    A("still standing today", "that one's still up", "still my favourite", chance=0.6),
)], "my first house", ["building", "home", "jokes"])

link_part("story_witch_2", "old_witch", [P(
    A("so the next night i went back for round two", "anyway, i went back the next night with a bow", "obviously i went back. round two"),
    B("ask", "round two!", "revenge", "and?"),
    A("shot her from across the swamp", "got her from far away with arrows", "she never even saw me this time"),
    B("wow", "smart", "bows beat witches", "nice"),
    A("she dropped glowstone and a glass bottle", "dropped some redstone and a potion", "she dropped sugar and sticks. wow"),
    B(None, "loot is loot", "lol", "worth it?", "nice"),
    A("i keep her hut as a trophy now", "her hut is my fishing spot now", "i moved into her hut. it's cozy", chance=0.6),
)], "the witch", ["mobs", "potions", "jokes"])

link_part("story_shipwreck_2", "old_shipwreck", [P(
    A("then i had to get home with it all", "the trip home was the scary part", "so i'm heading home with all that loot"),
    B("gasp", "uh oh", "oh no", "of course"),
    A("night fell and drowned started popping up", "a drowned with a trident followed my boat", "drowned all round the boat at night"),
    B("gasp", "row faster", "not the drowned", "go go"),
    A("rowed like my life depended on it. it did", "made it to shore on two hearts", "rowed all night and made it home"),
    B("relief", "phew", "you made it", "w"),
    A("put it all in one chest marked treasure", "the map's still on my wall", "still have that map", chance=0.6),
)], "the treasure", ["ocean", "brag", "danger"])

link_part("story_end_2", "old_end", [P(
    A("and you know what they brought back?", "and the one who told me, they brought back proof", "and they didn't come back empty handed"),
    B(None, "what", "no way", "what did they bring"),
    A("a dragon egg. sitting on a pillar in their house", "wings. elytra. they flew right over my house", "a purple box that holds stuff. a shulker box"),
    B("wow", "no way", "jealous", "wow"),
    A("said the trip almost killed them twice", "they said the void is the scary part, not the dragon", "they said never again. then went back"),
    B(None, "brave", "scary", "respect", "i believe it"),
    A("someday that's us", "we're going someday, i'm calling it", "one day we'll go", chance=0.6),
)], "the end", ["end", "dreams", "plans"])

link_part("story_trade_scam_2", "old_trade_scam", [P(
    A("but i got my revenge", "i didn't let it go though", "so i got that villager back"),
    B("laugh", "revenge on a villager?", "how", "uh oh"),
    A("put a sign on his house: do not trade", "made a sign by his door: bad trades here", "told every villager in town. well, i told the golem"),
    B("laugh", "lol petty", "justice", "the people deserve to know"),
    A("and next week his prices dropped. i swear he read it", "then his prices got better. i think he saw", "a week later he had a great trade"),
    B("laugh", "he learned", "lol", "respect"),
    A("we're cool now. kind of", "i still check his trades every day", "still don't trust him", chance=0.6),
)], "the trade", ["trade", "villagers", "jokes"])

link_part("story_cave_in_2", "old_cave_in", [P(
    A("but when i finally dug through all that gravel", "and behind all that gravel was the best part", "so i went back and dug through the gravel"),
    B("ask", "what", "go on", "and?"),
    A("a whole cave full of iron", "two diamonds, right there", "a dungeon with two chests"),
    B("wow", "no way", "worth it then", "lucky"),
    A("the gravel was guarding it", "so the cave-in paid off", "almost worth it. almost"),
    B("laugh", "gravel guards", "classic", "lol"),
    A("i still never mine up", "still won't mine upward", "lesson stands. never mine up", chance=0.6),
)], "the gravel", ["caves", "mining", "ore"])

link_part("story_horse_2", "old_horse", [P(
    A("so i tried again with a new horse", "after that i got a new horse", "anyway, i got another horse later"),
    B("ask", "brave", "round two", "and?"),
    A("this one was slow. like really slow", "this one jumped like five blocks high", "this one was a donkey. it's fine"),
    B("laugh", "lol", "nice", "ha"),
    A("we've been through a lot together now", "built it a stable with a roof", "still have it, in a stable now"),
    B("aw", "cute", "good owner", "aww"),
    A("it still throws me off sometimes", "it still bucks me off now and then", "it's grumpy but it's mine", chance=0.6),
)], "the horse", ["animals", "pets", "jokes"])

link_part("story_trident_2", "old_trident", [P(
    A("so i went back. i'm not a quitter", "i went back out there with a stack of arrows", "obviously i went back for another"),
    B("laugh", "of course you did", "and?", "obsessed"),
    A("fought drowned every night for a week", "killed like thirty drowned", "hunted drowned for days"),
    B("laugh", "dedication", "obsessed lol", "go on"),
    A("and then one night, the last drowned of the night", "and on the very last one", "and then, finally, one night"),
    B(None, "it dropped one?", "no way", "please", "don't say it"),
), P(
    A("it dropped a trident. i screamed", "a trident. finally. i yelled", "trident. on the ground. mine"),
    B("wow", "FINALLY", "w", "the grind paid off"),
    A("then i put loyalty on it", "got loyalty on it right away", "loyalty on it so i never lose it again"),
    B("wow", "smart", "it comes back now", "nice"),
    A("throw it, it comes back. every time", "now it always comes back", "it always comes home"),
    B("done", "worth it", "what a saga", "happy ending"),
    A("the ocean paid me back", "me and the ocean are even now", "finally even", chance=0.6),
)], "the trident", ["ocean", "weapons", "brag"])

link_part("story_bees_2", "old_bees", [P(
    A("so the next day i came back with a campfire", "the next day i came back, campfire in hand", "next day: campfire under the nest"),
    B(None, "smart", "learned", "and?", "finally"),
    A("the smoke calmed them. not one sting", "the smoke worked. they just chilled", "no stings. finally"),
    B("wow", "nice", "bee whisperer", "w"),
    A("got the honey. then a bee followed me home anyway", "a bee followed me home and lives on my roof now", "one bee followed me home. now it's mine"),
    B("aw", "aww", "cute", "bee buddy"),
    A("honey bottles for everyone", "now i have three hives", "named it stinger", chance=0.6),
)], "the bees", ["animals", "farming", "jokes"])

link_part("story_villager_trade_2", "old_villager_trade", [P(
    A("then something good happened, finally", "but a week later i found a zombie villager", "then i stumbled on a zombie villager"),
    B("ask", "oh?", "uh oh", "and?"),
    A("threw weakness on it and fed it a golden apple", "cured it. weakness and a golden apple", "cured it with a golden apple"),
    B(None, "clever", "nice", "and?", "the cure!"),
    A("it became a librarian. mending. one emerald", "it took a lectern. mending for one emerald", "librarian, mending, dirt cheap"),
    B("wow", "NO WAY", "the dream", "curing discount"),
    A("i guard that guy with my life", "he has his own golem now", "he lives in a stone house now", chance=0.6),
)], "the librarian", ["villagers", "trade", "enchanting"])

link_part("story_raft_2", "old_raft", [P(
    A("and on the way back i found the weirdest island", "the best bit was on the way back", "so on the way home i see this island"),
    B("ask", "what island", "weird how", "go on"),
    A("a mushroom island. red cows with mushrooms on them", "mushroom fields. giant mushrooms everywhere", "an island covered in giant mushrooms"),
    B("wow", "no way", "mushroom island", "i've heard of those"),
    A("no mobs at all there. slept on the beach", "no monsters spawn there. best sleep ever", "slept on the sand, not one mob"),
    B("aw", "dream", "move there", "nice"),
    A("thinking of moving there honestly", "i marked it on my map", "i go back sometimes", chance=0.6),
)], "the boat trip", ["ocean", "places", "exploring"])

link_part("story_sheep_2", "old_sheep", [P(
    A("then i found out you can just dye sheep pink", "then i learned you can dye a sheep pink", "then someone told me you can dye sheep"),
    B(None, "you can?", "lol", "wait really", "oh no"),
    A("pink dye from a pink tulip. one click. pink sheep", "pink dye, right on the sheep", "pink flower, pink dye, pink sheep"),
    B("laugh", "lol", "cute", "pink army"),
    A("now my whole farm is pink", "every sheep i have is pink", "i have twelve pink sheep now"),
    B("laugh", "the pink farm", "lol", "cute"),
    A("they're all called strawberry", "strawberry two, three and four", "all strawberries", chance=0.7),
)], "the sheep", ["animals", "farming", "jokes"])

link_part("story_lava_bucket_2", "old_lava_bucket", [P(
    A("and the fire jumped to the trees next door", "then the trees around my house caught too", "and then the forest behind it started burning"),
    B("gasp", "oh no", "forest fire", "this keeps getting worse"),
    A("i just stood there with a water bucket, useless", "dumped water everywhere. didn't help", "rain came and put it out. an hour too late"),
    B("sad", "rip", "pain", "oof"),
    A("now everything i build is stone. even the furniture", "my house is all cobblestone now", "i don't use wood anymore. at all"),
    B(None, "makes sense", "fireproof gang", "fair", "lol"),
    A("and lava stays far, far away", "lava stays in the nether now", "no lava within fifty blocks", chance=0.6),
)], "the lava", ["building", "danger", "home"])

link_part("story_creeper_farm_2", "old_creeper_farm", [P(
    A("then someone showed me what i did wrong", "turns out i'd built it wrong the whole time", "so i asked someone who knows redstone"),
    B("ask", "what was it", "and?", "lol what"),
    A("i built it near caves. mobs spawned down there instead", "it was too close to the caves", "i forgot to light up the caves under it"),
    B("laugh", "classic mistake", "rookie", "oh lol"),
    A("rebuilt it out over the ocean. works great now", "rebuilt it way up in the sky", "lit up the caves and it pumps out gunpowder now"),
    B("wow", "nice", "gunpowder for days", "w"),
    A("rockets for life now", "chests of gunpowder now", "more gunpowder than i'll ever need", chance=0.6),
)], "the farm", ["redstone", "mobs", "brag"])

link_part("story_snow_night_2", "old_snow_night", [P(
    A("and in the morning i found something cool", "but when the sun came up i saw it", "the next morning made it worth it"),
    B("ask", "what", "go on", "oh?"),
    A("an igloo. with a secret basement under the carpet", "found an igloo. there was a basement under it", "an igloo, and a trapdoor under the carpet"),
    B(None, "a basement?", "no way", "secret basement!", "what was down there"),
    A("a brewing stand and a zombie villager in a cell", "a zombie villager locked up, and a golden apple", "a little lab. and a zombie villager behind bars"),
    B("creepy", "creepy", "who builds that", "spooky"),
    A("never found out who built it", "i cured the villager later", "i still don't know who made it", chance=0.6),
)], "the snow", ["biomes", "structures", "villagers"])

link_part("story_stronghold_3", "old_stronghold", [P(
    A("and then i had to find my way out", "and finding the way out took longer than finding it", "then i had to get out of that maze"),
    B("laugh", "strongholds are mazes", "lol", "and?"),
    A("every hallway looked the same", "torches on the right going in. then i forgot which side", "ran into the same library three times"),
    B("laugh", "classic", "torches on the right, always", "lol"),
    A("ended up digging straight up to the surface", "gave up and dug straight up", "pillared up through the ceiling"),
    B(None, "smart", "fair", "big brain", "the easy way"),
    A("came out in the middle of a lake. of course", "popped out in a lake", "came up right in a river", chance=0.6),
)], "the stronghold", ["structures", "end", "jokes"], start=4)

link_part("story_first_friend_3", "old_first_friend", [P(
    A("i went back to where we used to live once", "sometimes i go back to our old spot", "i visited our old place a while ago"),
    B(None, "aw", "what was it like", "was it the same"),
    A("everything was just how we left it", "their chest was still there. one diamond in it", "the farm was overgrown but still growing"),
    B("aw", "that's sweet", "maybe they left it for you", "aw"),
    A("i left a sign there. just in case", "i left them a note on a sign", "i put a sign up: i was here"),
    B("aw", "aww", "that's lovely", "they'll see it"),
    A("maybe one day they'll read it", "i hope they see it someday", "you never know", chance=0.7),
)], "a friend", ["friends", "memories", "feelings"], start=4)

# === END OF STORIES ===


# =====================================================================================================================
# Checks and output
# =====================================================================================================================
def lines_of(element):
    out = []
    groups = [element] if isinstance(element, list) else list(element.values())
    for group in groups:
        for line in group:
            if isinstance(line, dict):
                out.append(line["line"])
                out += line["then"]
            else:
                out.append(line)
    return out


def check():
    ids = set()
    problems = []
    for t in topics:
        assert t["id"] not in ids, t["id"]
        ids.add(t["id"])
        if not 4 <= len(t["turns"]) <= 7:
            problems.append("%s: %d turns" % (t["id"], len(t["turns"])))
        for turn_ in t["turns"]:
            ls = lines_of(turn_["lines"])
            if not ls:
                problems.append("%s: empty turn" % t["id"])
            for line in ls:
                if len(line) > 90:
                    problems.append("%s: long line (%d): %s" % (t["id"], len(line), line))
                if not line.isascii():
                    problems.append("%s: not ascii: %s" % (t["id"], line))
                if "{" in line or "}" in line:
                    problems.append("%s: brace: %s" % (t["id"], line))
                for word in re.findall(r"[A-Za-z]+", line):
                    if word != word.lower() and word != word.upper():
                        problems.append("%s: capital letter: %s" % (t["id"], line))
    for tid, link in links.items():
        for nxt in link["next"]:
            assert nxt in ids, nxt
    for p in problems:
        print("problem: " + p)
    return problems


problems = check()
with open(OUT, "w", encoding="utf-8") as f:
    json.dump({"_comment": "Stories told in several parts (tools/talk_gen/stories.py; the user: 'add more parts to each story'). "
                           "Part 1 names part 2 in 'next', and so on; later parts are follow_ups tagged story_part. The 'links' "
                           "add parts 3 and 4 to the two-part stories in topics_stories.json and topics_stories_more.json. "
                           "Check with: python tools/check_talk_web.py .",
               "topics": topics, "links": links}, f, indent="\t", ensure_ascii=True)
stories = sum(1 for t in topics if not t.get("follow_up"))
print("%d topics (%d new stories, %d linked parts), %d problems" % (
    len(topics), stories, sum(1 for t in topics if t["id"].startswith(PREFIX + "old_")), len(problems)))
