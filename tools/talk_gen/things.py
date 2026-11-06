"""Writes drifter_talk/topics_gen_things.json: things and doings, subject by subject, from small talk to debate.

The user: "more topics for each subject matter (from small talks to debate)". Each subject (a block, a tool, a weapon, a
piece of armor, a food, a workstation, a way of mining, building, farming or travelling...) gets a ladder of topics, each
one its own rung (a subject skips the rungs it has nothing for):

  mention    a passing mention ("finally got a stack of copper"), low weight
  ask        a question and its answers (the answers come through "then", so they fit the question)
  like       one says what it likes about it
  tip        a how-to tip from one who knows
  brag       a brag (often needs the situation: a house, diamonds, a town)
  gripe      a complaint, sympathy, then a fix
  story      a short story in one part (beats said in order, each beat a few ways of saying the same thing)
  take       a mild disagreement (agree/disagree marks, as topics_debates.json)
  debate     a debate on a claim ("copper is useless"), with a comeback when the other disagrees

Subject fields (lines are "|"-separated; "<voice>: line" is that kind of player's line; "question? > answer / answer"
gives the answers the other one says next): m mr mf (mention, reactions, follow-up; mg=False leaves out the usual "nice"), q (questions), qr (after the
answer), lk lkr lkf (like), tp tpr (tips: first half, then the rest), br brr brf (brag), cp cps cpm cpx (complaint,
sympathy, more, fix), st (mood, "beat >> beat >> beat"), dg (claims, agree, disagree, yes, no), db (claims, agree,
disagree, comeback, yes, last word); `<rung>n` is that rung's needs ("a:has_house !rivals"), `gate` a knowledge tag
(nether, end, ...) put on every rung but the question (so one new to it can still ask).
Facts are Minecraft 1.21.1.

Usage: python tools/talk_gen/things.py src/main/resources/data/drifter/drifter_talk/topics_gen_things.json
"""
import json
import re
import sys

PREFIX = "g_things_"
VOICES = {"casual", "newbie", "builder", "miner", "homebody", "speedrunner", "nomad", "hoarder", "hermit", "pvp_sweat",
          "bandit", "helper"}
# Flags that make a topic situational (it gets a higher weight when it needs one of them).
PLAIN_NEEDS = {"!rivals", "!strangers", "friends", "acquainted"}

topics = []
problems = []


def parse(spec):
    """'line|line|newbie: line|question? > answer / answer' -> a list of lines, or voices when some lines are a kind's."""
    if not spec:
        return []
    items = list(spec) if isinstance(spec, (list, tuple)) else spec.split("|")
    anyl, voices = [], {}
    for it in items:
        it = it.strip()
        if not it:
            continue
        target = anyl
        m = re.match(r"^([a-z_]+): (.+)$", it)
        if m and m.group(1) in VOICES:
            target = voices.setdefault(m.group(1), [])
            it = m.group(2).strip()
        if " > " in it:
            q, ans = it.split(" > ", 1)
            it = {"line": q.strip(), "then": [x.strip() for x in ans.split(" / ") if x.strip()]}
        target.append(it)
    for v in list(voices):
        if len(voices[v]) < 2:  # (one line for a kind would be all it ever says)
            anyl += voices.pop(v)
    if voices:
        d = {"any": anyl}
        d.update(voices)
        return d
    return anyl


def mix(*specs):
    """Several specs' lines in one turn (a subject's own lines first, then the usual reactions)."""
    anyl, voices = [], {}
    for s in specs:
        p = parse(s)
        if isinstance(p, dict):
            anyl += p["any"]
            for k, v in p.items():
                if k != "any":
                    voices.setdefault(k, []).extend(v)
        else:
            anyl += p
    seen, out = set(), []
    for line in anyl:
        key = line["line"] if isinstance(line, dict) else line
        if key not in seen:
            seen.add(key)
            out.append(line)
    if voices:
        d = {"any": out}
        d.update(voices)
        return d
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


def needs_of(spec):
    return spec.split() if spec else []


def add(id, about, tags, leads, turns, needs=None, weight=None, base=1.0):
    seen = []
    for t in tags:
        if t not in seen:
            seen.append(t)
    tags = seen[:4]
    lead_list = []
    for t in leads:
        if t not in lead_list:
            lead_list.append(t)
    lead_list = lead_list[:4]
    if weight is None:
        situational = [n for n in (needs or []) if n not in PLAIN_NEEDS]
        weight = base if not situational else max(1.3, base + 0.6)
    t = {"id": PREFIX + id, "weight": weight}
    if needs:
        t["needs"] = needs
    t.update({"tags": tags, "leads": lead_list, "about": about, "turns": turns})
    topics.append(t)


# The usual reactions (short, so they may repeat across topics).
G_MR = "nice|oh nice|ooh|cool|lol nice|w|solid|newbie: ooh|newbie: wow|hermit: mm|hermit: ok"
G_MF = "yeah|lol|mhm|yep|ikr"
G_QA_AFTER = "oh|ohh ok|good to know|huh|makes sense|cool, ty|noted|ah ok"
G_QA_THX = "thanks|ty|you know a lot|ok that helps|appreciate it|newbie: ty ty|newbie: writing that down"
G_LKR = "same|fair|true|valid|ngl same|each their own|hermit: mm|hermit: fine"
G_LKF = "right?|exactly|lol|yeah|mhm"
G_TPR = "oh nice|good tip|didn't know that|huh, smart|noted|ooh|newbie: wait what|newbie: whoa ok|newbie: ohh"
G_TPEND = "thanks for the tip|i'll try that|learned something|appreciate it|good to know|ty|hermit: noted|hermit: ok"
G_BRR = "jealous|show off|nice|w|ok flex|not bad|respect|pvp_sweat: cute|pvp_sweat: i've seen better"
G_BRF = "lol|yep|thanks|ngl i'm proud|hehe|ty ty"
G_BREND = "gg|nice one|you earned it|respect"
G_CPS = "rough|oof|that's annoying|been there|classic|f|ugh same|helper: aw, that's rough|helper: sorry man"
G_CPF = "i'll try that|good idea|maybe|worth a shot|true|lol ok"
G_ST1 = "go on|uh oh|and?|lol go on|what happened|ok and"
G_ST2 = {"bad": "oh no|oof|nooo|rip|yikes|uh oh", "good": "no way|nice|whoa|w|sick|ooh",
         "funny": "lol|lmao|no way|classic|bruh lol"}
G_ST3 = {"bad": "rip|pain|that's rough|f|brutal", "good": "legendary|nice|jealous|gg|w story",
         "funny": "lol|legendary|classic|lmao|good story"}
G_DG_AGREE = "true|agreed|same|fair|facts"
G_DG_DIS = "nah|eh|hard disagree|not really"
G_DG_YES = "see|exactly|right?|knew it"
G_DG_NO = "fair|each their own|ok ok|suit yourself|lol ok"
G_DB_AGREE = "facts|true|100%|agreed|honestly yeah"
G_DB_DIS = "nah|hard disagree|that's wrong|lol no"
G_DB_YES = "see|exactly|glad someone gets it|finally someone"
G_DB_FINAL = "still no|agree to disagree|fine, maybe|nope|ok you have a point|we'll see"

RUNG_TAG = {"mention": None, "ask": "advice", "like": "opinion", "tip": "advice", "brag": "brag", "gripe": "complaints",
            "story": "stories", "take": "opinion", "debate": "argument"}


def S(key, about, tags, leads, gate=None, **f):
    tags = tags.split()
    leads = leads.split()

    def tg(rung, extra=True):
        t = list(tags[:3])
        if gate and rung != "ask":
            t = t[:2] + [gate]
        if RUNG_TAG[rung]:
            t.append(RUNG_TAG[rung])
        return t

    def ld(rung):
        l = list(leads)
        if rung == "debate":
            l = l[:3] + ["argument"]
        return l

    if f.get("m"):
        add(key + "_mention", about, tg("mention"), ld("mention"), [
            turn("a", parse(f["m"])),
            turn("b", mix(f.get("mr"), G_MR if f.get("mg", True) else None)),
            turn("a", mix(f.get("mf"), G_MF), chance=0.5),
        ], needs_of(f.get("mn")), f.get("mw"), base=0.7)
    if f.get("q"):
        qs = f["q"]
        first, rest = qs[:2], qs[2:]
        turns = [turn("a", parse(first)), turn("b", ["hm"]), turn("a", mix(f.get("qr"), G_QA_AFTER))]
        if rest:
            turns += [turn("a", parse(rest), chance=0.6, mark=["more"]), turn("b", ["hm"], cond=["t:more"]),
                      turn("a", parse(G_QA_THX), chance=0.7, cond=["t:more"])]
        add(key + "_ask", about, tg("ask"), ld("ask"), turns, needs_of(f.get("qn")), f.get("qw"))
    if f.get("lk"):
        add(key + "_like", about, tg("like"), ld("like"), [
            turn("a", parse(f["lk"])),
            turn("b", mix(f.get("lkr"), G_LKR)),
            turn("a", mix(f.get("lkf"), G_LKF), chance=0.6),
        ], needs_of(f.get("lkn")), f.get("lkw"))
    if f.get("tp"):
        tips = parse(f["tp"])
        if isinstance(tips, dict):
            tips = tips["any"]
        half = (len(tips) + 1) // 2
        add(key + "_tip", about, tg("tip"), ld("tip"), [
            turn("a", tips[:half]),
            turn("b", mix(f.get("tpr"), G_TPR)),
            turn("a", tips[half:], chance=0.7),
            turn("b", parse(G_TPEND), chance=0.7),
        ], needs_of(f.get("tpn")), f.get("tpw"))
    if f.get("br"):
        add(key + "_brag", about, tg("brag"), ld("brag"), [
            turn("a", parse(f["br"])),
            turn("b", mix(f.get("brr"), G_BRR)),
            turn("a", mix(f.get("brf"), G_BRF), chance=0.6),
            turn("b", parse(G_BREND), chance=0.35),
        ], needs_of(f.get("brn")), f.get("brw"))
    if f.get("cp"):
        turns = [turn("a", parse(f["cp"])), turn("b", mix(f.get("cps"), G_CPS))]
        if f.get("cpm"):
            turns.append(turn("a", parse(f["cpm"]), chance=0.7))
        if f.get("cpx"):
            turns += [turn("b", parse(f["cpx"]), chance=0.75, mark=["fix"]),
                      turn("a", parse(G_CPF), chance=0.6, cond=["t:fix"])]
        add(key + "_gripe", about, tg("gripe"), ld("gripe"), turns, needs_of(f.get("cpn")), f.get("cpw"))
    if f.get("st"):
        mood, story = f["st"]
        beats = [b.strip() for b in story.split(">>")]
        turns = [turn("a", parse(beats[0])), turn("b", parse(G_ST1))]
        for i, beat in enumerate(beats[1:]):
            last = i == len(beats) - 2
            turns.append(turn("a", parse(beat)))
            turns.append(turn("b", parse(G_ST3[mood] if last else G_ST2[mood]), chance=0.7 if last else None))
        add(key + "_story", about, tg("story"), ld("story"), turns, needs_of(f.get("stn")), f.get("stw"))
    if f.get("dg"):
        claims, agree, disagree, yes, no = (f["dg"] + ("", "", "", "", ""))[:5]
        add(key + "_take", about, tg("take"), ld("take"), [
            turn("a", parse(claims)),
            turn("b", mix(agree, G_DG_AGREE), chance=0.5, mark=["agree"]),
            turn("b", mix(disagree, G_DG_DIS), cond=["!t:agree"], mark=["disagree"]),
            turn("a", mix(yes, G_DG_YES), chance=0.6, cond=["t:agree"]),
            turn("a", mix(no, G_DG_NO), cond=["t:disagree"]),
        ], needs_of(f.get("dgn")), f.get("dgw"))
    if f.get("db"):
        claims, agree, disagree, comeback, yes, last = (f["db"] + ("", "", "", "", "", ""))[:6]
        add(key + "_debate", about, tg("debate"), ld("debate"), [
            turn("a", parse(claims)),
            turn("b", mix(agree, G_DB_AGREE), chance=0.45, mark=["agree"]),
            turn("b", mix(disagree, G_DB_DIS), cond=["!t:agree"], mark=["disagree"]),
            turn("a", mix(yes, G_DB_YES), chance=0.7, cond=["t:agree"]),
            turn("a", parse(comeback), cond=["t:disagree"]),
            turn("b", mix(last, G_DB_FINAL), chance=0.6, cond=["t:disagree"]),
        ], needs_of(f.get("dbn")), f.get("dbw"))


# ======================================================================= Blocks: woods
S("oak", "oak wood", "trees building", "trees building home",
  m="chopped a whole patch of oaks today|got like four stacks of oak logs|oak planks, oak planks, oak planks lol|"
    "planted a row of oak saplings by my place|got two apples off one oak tree",
  mr="classic|oak never lets you down|the starter wood",
  mf="smells like a fresh start|gonna need more though|the leaves took ages to go",
  q=["do oak leaves drop apples? > yeah, oak and dark oak do / yep, when the leaves decay",
     "how many planks from one oak log? > four / four planks a log, always",
     "why do some oaks grow huge with branches? > that's the big oak, it just happens sometimes / random, some saplings grow the fancy kind"],
  qr="huh|neat",
  lk="oak is the comfort wood|nothing beats plain oak planks|oak logs with stripped oak, so clean|"
     "builder: oak and cobble, the classic for a reason|builder: stripped oak makes great beams",
  lkr="kinda basic though|the starter wood|spruce is better tbh",
  lkf="basic is good|it just works",
  tp="tip: bone meal an oak sapling and it pops up right away|keep the apples, golden apples need them|"
     "strip oak logs with an axe for clean beams|leave the leaves to decay, you get saplings and sticks|"
     "saplings need light and dirt or grass under them|space your saplings out, oaks spread wide",
  br="my house is all oak and i love it|finished an oak extension on my house|put oak beams all through my house",
  brn="a:has_house",
  brr="classic|very homey|cozy",
  brf="took like six stacks|oak never misses",
  cp="big oaks are the worst to chop|those branchy oaks leave floating logs everywhere|i hate the big oaks with the arms",
  cps="big oaks are a pain",
  cpm="half the logs are up in the branches|i had to pillar up three times",
  cpx="just chop the trunk and move on|leave the floaters, nobody will see|get efficiency on your axe|plant birch instead, no branches",
  st=("funny", "so i planted an oak sapling right by my door|story: i put an oak sapling by my front door to look nice|"
               "i had this cute little oak sapling by my door"
               ">> went to sleep and it grew overnight|slept one night and it just shot up|next morning it had grown"
               ">> now my door opens into a tree trunk lol|the leaves are inside my house now|i go out the window now"),
  dg=("oak is the best wood for a first house|nothing beats oak for a starter house",
      "it's everywhere|cheap and easy", "spruce looks better|birch is nicer", "oak gang", "fine, be fancy"),
  db=("oak is the most boring wood in the game|oak is overrated, change my mind|anyone still building in oak needs help",
      "so plain|everyone uses it", "it's a classic|oak goes with everything|boring is fine, it's cozy",
      "every house in every town is oak|it's yellow brown nothing|try cherry once, you'll see",
      "change your wood", "oak forever|i'll stay basic"))

S("spruce", "spruce wood", "trees building", "trees building biomes",
  m="got a load of spruce from the taiga|spruce logs are so dark and nice|chopped a giant spruce, so many logs|"
    "my chests are full of spruce now|found a big spruce forest north of here",
  mr="the cabin wood|spruce is the best|so dark and nice",
  mf="took me all day|my arms hurt lol|cabin time",
  q=["how do you grow a giant spruce? > four saplings in a square / plant four in a 2x2 and bone meal it",
     "what's the brown dirt under big spruces? > podzol, giant spruces make it / podzol, mushrooms grow on it",
     "do spruce leaves drop apples? > no, only oak and dark oak / nope, just saplings and sticks"],
  qr="oh neat|huh, ok",
  lk="spruce is the best wood, no contest|spruce doors look like a real cabin|love a spruce cabin in the snow|"
     "builder: spruce and cobble, chef's kiss|builder: spruce trapdoors make perfect shutters",
  lkr="cabin vibes|a bit dark for me|spruce is so cozy",
  lkf="cabin forever|dark is cozy",
  tp="tip: four spruce saplings in a square grow one giant tree|giant spruces drop a ton of logs, worth the chop|"
     "spruce trapdoors on the walls make nice shutters|bring a pick for the top, giant spruces are tall|"
     "taiga villages are spruce, you can see how it looks|spruce and stone bricks never look bad",
  br="my cabin is all spruce and it's perfect|built a spruce house up by the snow|my house has spruce beams and a spruce door",
  brn="a:has_house",
  brr="cabin vibes|cozy|sounds lovely",
  brf="it smells like pine in there i swear|best wood for houses",
  cp="giant spruces take forever to chop|the top of a giant spruce is so far up|got stuck on top of a spruce again",
  cps="giant ones are brutal",
  cpm="had to pillar to the top|the leaves leave floating logs up there",
  cpx="spiral down from the top|start at the top and cut downward|leave the top logs, who cares|get an efficiency axe",
  st=("bad", "so i was chopping a giant spruce up high|story: i was on top of a giant spruce cutting the last logs|"
              "i was up a big spruce with my axe"
              ">> cut the block i was standing on|broke the log under my own feet|stepped back off the edge"
              ">> fell like thirty blocks and died|hit the ground, respawned in bed|gone, logs and all"),
  dg=("spruce is the best wood for a house|a spruce cabin beats any other house",
      "so cozy|cabins are the best", "too dark|oak is lighter", "cabin gang", "enjoy your pale wood"),
  db=("spruce is the only wood that looks good|every other wood is worse than spruce|spruce beats every wood, period",
      "cabins don't lie|honestly yeah", "dark oak is better|cherry looks way better|too dark, everything looks gloomy",
      "spruce goes with stone, snow, everything|dark oak is basically spruce but harder to grow|gloomy is cozy",
      "spruce gang", "still no|whatever, cabin boy"))

S("birch", "birch wood", "trees building", "trees building decor",
  m="chopped a birch forest today|birch logs are so pretty|got loads of birch for a floor|birch everywhere out east",
  mr="the white trees|birch is chill|light wood, nice",
  mf="it's so bright|easy chopping too",
  q=["why are birch trees easy to farm? > no branches, just a straight trunk / they grow straight up, no arms",
     "what colour are birch planks? > a pale yellow, almost cream / light, like sand",
     "do birch leaves drop apples? > no, only oak and dark oak / nope"],
  qr="good to know|nice",
  lk="birch is perfect for floors|birch planks make a room so bright|love birch logs, white with spots|"
     "builder: birch and quartz look so modern|builder: birch and dark oak contrast is perfect",
  lkr="bright is nice|a bit plain|birch floors are clean",
  lkf="so clean|clean is good",
  tp="tip: birch is the best tree farm, no branches|birch trunks are straight so you get every log|"
     "use birch for modern builds, it's light|birch in a dark room makes it look bigger|"
     "old growth birch forests are huge, tons of wood|birch saplings stay small, easy to fit near the house",
  br="my whole kitchen floor is birch|built a birch house, it's so bright|my birch tree farm is huge now",
  brr="clean|bright",
  brf="it looks great in the sun|so easy to chop",
  cp="birch looks washed out in my build|birch planks clash with everything|my birch house looks like a sandcastle",
  cps="birch is tricky",
  cpm="i thought it would look modern|it's just too pale",
  cpx="add dark oak trim|mix in some spruce|use stripped birch logs for edges|put some stone at the bottom",
  st=("good", "so i went looking for wood out east|story: i needed a lot of wood fast|i was low on logs"
               ">> walked into an old birch forest, trees like thirty tall|found a birch forest with giant trees"
               ">> came home with like eight stacks|filled two chests with birch|never running out again"),
  dg=("birch is the best tree to farm|birch beats oak for a tree farm",
      "no branches|straight up, so easy", "oak gives apples though|spruce gives more wood", "birch gang", "suit yourself"),
  db=("birch is the ugliest wood|birch looks like nothing|birch planks are just bland",
      "so pale|kind of true", "it's perfect for modern|it's clean, not bland|birch floors look amazing",
      "it's the color of old bread|nobody builds a whole house in birch|it washes out every build",
      "bland wood", "birch is classy|you just don't get it"))

S("dark_oak", "dark oak", "trees building", "trees building biomes",
  m="got a ton of dark oak from the dark forest|dark oak planks are so rich|chopped a big dark oak, one tree four logs wide|"
    "found a dark forest with huge mushrooms",
  mr="fancy wood|dark forest is creepy|where'd you find it? > past the river, big dark forest / up the hill, it's huge",
  mf="it's like chocolate|so many logs per tree",
  q=["how do you grow dark oak? > four saplings in a square / it only grows from a 2x2 of saplings",
     "is the dark forest dangerous? > yeah, mobs spawn under the leaves in daytime / kinda, it's dark even at noon",
     "do dark oak leaves drop apples? > yeah, like oak / yep, oak and dark oak"],
  qr="huh, didn't know|good to know",
  lk="dark oak is the classiest wood|dark oak roofs look so good|dark oak and stone bricks, perfect|"
     "builder: dark oak trim on anything makes it pop|builder: dark oak and white, timber frame heaven",
  lkr="very fancy|a bit heavy|dark oak roofs are the best",
  lkf="fancy is good|heavy is good",
  tp="tip: dark oak needs four saplings in a square|light up the dark forest, mobs spawn there in the day|"
     "dark oak leaves drop apples too|woodland mansions are in dark forests, careful|"
     "dark oak makes the best roofs, it doesn't look flat|one dark oak is a ton of logs",
  br="my roof is all dark oak|built my door frame in dark oak, so fancy|my house has dark oak floors throughout",
  brn="a:has_house",
  brr="fancy|very classy",
  brf="took a whole forest|it looks rich",
  cp="dark forests are so dark i got jumped at noon|a creeper came out of the dark forest in broad daylight|"
     "can't see anything in a dark forest",
  cps="dark forest is cursed",
  cpm="the leaves block the sun completely|zombies don't even burn in there",
  cpx="bring torches|cut a clearing first|go in with a shield|chop from the edge",
  st=("bad", "so i went into a dark forest for wood|story: i needed dark oak, went into the dark forest|i was chopping in the dark forest"
              ">> it was noon but a zombie walked right up|heard a hiss behind a tree|skeletons started shooting from the shade"
              ">> lost half my health and ran|i dropped my axe running|came home with two logs lol"),
  dg=("dark oak makes the best roof|nothing beats a dark oak roof",
      "so classy|it pops", "spruce roofs look better|deepslate tiles are better", "dark oak gang", "fine"),
  db=("dark oak is the best wood in the game|dark oak beats every other wood|no wood is better than dark oak",
      "facts, so rich|honestly, yeah", "spruce is better|cherry is way prettier|too dark for a whole house",
      "it works for roofs, floors, trim, everything|cherry is just pink oak|dark is classy, not gloomy",
      "dark oak gang", "brown is boring|you'll get bored of it"))

S("cherry", "cherry wood", "trees building biomes", "trees building decor",
  m="found a cherry grove up the mountain|cherry leaves are so pretty|got a stack of cherry logs|"
    "the petals were falling everywhere|planted cherry saplings by my house",
  mr="pink!|so pretty|cherry is the best",
  mf="it's the prettiest place|i stood there for ages",
  q=["where do cherry trees grow? > cherry groves, up on mountains / in the pink biome on mountain sides",
     "what colour is cherry wood? > pink planks, darker bark / pink, like the leaves but softer",
     "what are the pink flowers on the ground? > pink petals, you can pick them up / pink petals, bone meal adds more"],
  qr="pretty|ooh",
  lk="cherry wood is the prettiest in the game|cherry planks make a room glow|i could live in a cherry grove|"
     "builder: cherry and white, so soft|builder: cherry roofs with dark trim, perfect",
  lkr="very pink|a bit much|cherry groves are so pretty",
  lkf="pink is a vibe|i like a bit much",
  tp="tip: cherry groves are up in the mountains|pink petals can be placed as a flower carpet|"
     "cherry wood sounds different when you walk on it|cherry saplings come from the leaves|"
     "bone meal pink petals and they fill in|mix cherry with spruce so it's not all pink",
  br="my house is all cherry wood now|built a cherry tea house up the hill|my garden has cherry trees and petals everywhere",
  brr="so pretty|pink house!",
  brf="it glows at sunset|best decision i made",
  cp="cherry is so pink nothing goes with it|my cherry house looks like a cake|pink everything, it's too much",
  cps="pink is hard",
  cpm="i love it but it clashes|everything looks like candy now",
  cpx="add dark oak trim|use stone at the bottom|use it for accents only|mix in spruce",
  st=("good", "so i was crossing the mountains|story: i was climbing a mountain for no reason|i got lost up the mountains"
               ">> came over the top and it was all pink|found a whole cherry grove, petals falling|walked right into a cherry grove"
               ">> i built a little hut right there|i sat there till sunset|i go back there just to look at it"),
  dg=("cherry is the prettiest wood|cherry wood is the best looking",
      "so pretty|true", "too pink|dark oak looks better", "pink gang", "fine, be boring"),
  db=("cherry wood is too pink to build with|nobody can build a real house in cherry|cherry wood is just for decoration",
      "way too pink|kinda true", "cherry houses look amazing|it's soft, not too pink|mix it and it's perfect",
      "every cherry house looks like a cake|you get sick of pink fast|it only works in the grove",
      "pink overload", "cherry forever|you just can't build"))

S("mangrove", "mangrove wood", "trees biomes building", "trees biomes building",
  m="got mangrove logs from the swamp|mangrove roots are everywhere out there|chopped some mangroves, red wood|"
    "my boots are full of mud from the mangroves",
  mr="the swamp wood|red planks|how was the swamp? > muddy / full of frogs / wet",
  mf="frogs everywhere|i'll go back for more",
  q=["what's the hanging thing on mangrove leaves? > a propagule, it's the sapling / propagules, plant them like saplings",
     "where do mangroves grow? > mangrove swamps, out by warm water / warm swamps, full of mud",
     "what can you do with mud? > mud bricks, or packed mud with wheat / packed mud, then mud bricks"],
  qr="huh|weird but cool",
  lk="mangrove planks are such a nice red|mangrove roots look cool in a build|love the mangrove swamp at sunset|"
     "builder: mangrove and mud bricks, perfect together|builder: mangrove roots make a great fence",
  lkr="red wood is nice|the swamp is gross|mangrove is underrated",
  lkf="red is underrated|swamps are fine",
  tp="tip: propagules grow even in water|mangrove roots can be placed like blocks|"
     "bring a boat, the swamp is half water|mud comes from water bottle on dirt too|"
     "frogs live in mangrove swamps, lead one home|the roots slow you down, cut a path",
  br="built a mangrove dock out on the water|my house has mangrove floors, so red|got a whole chest of mangrove logs",
  brr="red floors|nice",
  brf="the swamp was worth it|looks amazing",
  cp="mangrove swamps are impossible to walk through|got stuck in mangrove roots again|mangroves are a maze of roots",
  cps="swamps are the worst",
  cpm="roots everywhere, water everywhere|my boat kept hitting roots",
  cpx="cut a tunnel through the roots|go along the edge|bring a shovel for the mud|swim instead",
  st=("funny", "so i went into a mangrove swamp|story: i needed mangrove wood|i was crossing the mangroves"
                ">> got stuck between roots for like five minutes|a frog kept staring at me|my boat got wedged in the roots"
                ">> i ended up leaving with a frog instead of wood|came home soaked with three logs|just gave up and swam home"),
  dg=("mangrove is the best red wood|mangrove beats every red block",
      "red and earthy|so nice", "crimson is redder|bricks are better", "mangrove gang", "fair"),
  db=("mangrove swamps are the worst biome|mangrove swamps should not exist|worst place to get wood, the mangroves",
      "roots everywhere|so true", "they're beautiful|frogs live there|the wood is worth it",
      "you can't walk ten blocks without getting stuck|there's mud in my boots forever|chop a birch and save your time",
      "swamp haters unite", "frogs though|still pretty"))

S("bamboo_block", "bamboo planks", "building plants", "building decor plants",
  m="made a stack of bamboo planks|bamboo blocks are so yellow|crafted my first bamboo raft|got loads of bamboo from the jungle",
  mr="so yellow|the beach wood|bamboo is fun",
  mf="so bright|beach vibes",
  q=["how do you make bamboo planks? > nine bamboo make a block, a block makes two planks / block of bamboo first, then planks",
     "what's a bamboo raft? > the bamboo boat, it's flat / a boat made of bamboo planks",
     "what's bamboo mosaic? > a pattern block from bamboo slabs / fancy bamboo tiles"],
  qr="nice|i'll try that",
  lk="bamboo planks are so bright and fun|bamboo mosaic floors look amazing|bamboo huts by the beach, perfect|"
     "builder: bamboo and sandstone, beach house vibes|builder: stripped bamboo blocks look great as posts",
  lkr="very yellow|tropical|bamboo is cute",
  lkf="tropical vibes|yellow is happy",
  tp="tip: nine bamboo make a block of bamboo|strip the block with an axe for a lighter look|"
     "bamboo planks work like any planks for crafting|the raft rides lower but works the same|"
     "bamboo mosaic comes from bamboo slabs|bamboo burns fast in a furnace, it's weak fuel",
  br="built a bamboo beach hut|my dock is all bamboo now|made a bamboo raft, it's my main boat",
  brr="tropical|beach vibes",
  brf="it's like a vacation|so chill",
  cp="bamboo planks are way too yellow|my bamboo house looks like a banana|everything bamboo is so bright",
  cps="lol yellow",
  cpm="it hurts my eyes|it's like living in a banana",
  cpx="mix it with dark wood|use it for floors only|strip it, it's softer|add spruce trim",
  st=("funny", "so i built a bamboo raft|story: my first bamboo raft|i made a raft out of bamboo"
                ">> took it out to sea to test it|sailed way out to see how it handles|went out on the ocean in it"
                ">> a drowned climbed on and took my seat|a dolphin followed me home|ended up on an island i never knew about"),
  dg=("bamboo rafts beat normal boats|the bamboo raft is the best boat",
      "it looks cool|true", "same speed, looks silly|normal boats look better", "raft gang", "ok boat person"),
  db=("bamboo is the most useless wood|bamboo planks are pointless|nobody needs bamboo wood",
      "kinda yeah|so much work", "scaffolding though|it's a great look|it grows faster than any tree",
      "nine bamboo for two planks is a joke|oak is right there|it's just a yellow oak",
      "oak is right there", "scaffolding alone|bamboo forever"))

# ======================================================================= Blocks: stone and its kin
S("stone", "smooth stone", "building mining", "building crafting mining",
  m="smelted a whole stack of cobble into stone|got some stone with silk touch|made a pile of stone bricks|"
    "my furnace has been cooking stone all day",
  mr="stone bricks incoming|classic|the real building block",
  mf="looks so much better than cobble|for the walls",
  q=["how do you make stone bricks? > four stone in a square / smelt cobble to stone, then four stone",
     "what's smooth stone for? > blast furnaces and slabs / smelt stone again, it's for blast furnaces",
     "how do you get mossy stone bricks? > stone bricks plus moss or vines / craft them with a vine"],
  qr="ok cool|good to know",
  lk="stone bricks are the best building block|nothing beats a stone brick wall|smooth stone slabs are so clean|"
     "builder: mixing stone bricks with mossy ones, perfect|builder: cracked stone bricks add age",
  lkr="castle vibes|very solid|a bit grey",
  lkf="grey is good|solid",
  tp="tip: smelt cobble into stone, then four make stone bricks|a stonecutter makes stairs one to one|"
     "smelt stone bricks to get cracked ones|smooth stone is stone smelted twice|"
     "mix in mossy and cracked bricks so walls aren't flat|silk touch gets stone without smelting",
  br="my house is all stone bricks now|built a stone brick wall around my place|finished a stone brick tower",
  brn="a:has_house",
  brr="solid|creeper proof|castle vibes",
  brf="took forever to smelt|it's not going anywhere",
  cp="smelting all this cobble takes forever|i need so much fuel for stone|my furnaces have been going for days",
  cps="smelting is slow",
  cpm="three stacks of cobble in the furnace|i ran out of coal halfway",
  cpx="get more furnaces going|use a lava bucket as fuel|silk touch saves smelting|make a kelp fuel farm",
  st=("good", "so i had like ten stacks of cobble|story: i was drowning in cobblestone|i had chests full of cobble"
               ">> lined up eight furnaces and smelted it all|set up a row of furnaces, ran them all night"
               ">> now my whole house is stone bricks|built a stone brick wall around the base|turned it all into stone bricks"),
  dg=("stone bricks are the best block|stone bricks beat everything",
      "true|classic", "too grey|deepslate is better", "stone gang", "grey is a vibe"),
  db=("stone bricks are boring and overused|every castle is stone bricks, so boring|stone bricks are the oak of stone",
      "so overused|fair", "they're a classic|mix them and they're great|what else would you use",
      "deepslate bricks look way better|tuff bricks exist now|nobody's impressed by a stone brick wall",
      "time to change", "classics are classics|still stone"))

S("cobblestone", "cobblestone", "building mining", "building mining crafting",
  m="got like eight stacks of cobble today|my inventory is all cobble again|cobble everywhere|built a quick cobble hut",
  mr="the eternal cobble|lol same|never throw it away",
  mf="always useful|it's endless",
  q=["how do you make a cobblestone generator? > water flowing into lava, but not on the source / lava next to flowing water",
     "how much cobble for a furnace? > eight / eight, a ring in the crafting table",
     "how do you make mossy cobble? > cobble plus moss or a vine / craft it with a vine"],
  qr="ok|good to know",
  lk="cobblestone is honest|nothing beats a cobble wall|cobble is the best building block for anything quick|"
     "builder: cobble foundations look great under wood|builder: mossy cobble makes everything older",
  lkr="it's ugly though|honest is nice|cobble keeps creepers out",
  lkf="ugly is honest|it does the job",
  tp="tip: cobble stops creepers better than wood|cobble walls make great fences|"
     "a cobble generator never runs out|keep a stack of cobble for pillaring|"
     "stone tools only need cobble and sticks|cobble foundations make a wood house look real",
  br="my wall is all cobble and it's 200 blocks long|built a cobble tower, it's huge|my cobble stash is like twenty stacks",
  brr="lol|so much cobble|creeper proof",
  brf="i never throw cobble away|that's commitment",
  cp="i have too much cobble|my chests are full of cobble|every trip down i come back with a stack of cobble",
  cps="the cobble never ends",
  cpm="i can't throw it out though|it might be useful one day",
  cpx="build something huge|make stone bricks|throw it in lava|build a cobble wall around town",
  st=("funny", "so i was building a cobble hut fast|story: night came and i panicked|i got caught out at night"
                ">> slammed down cobble walls around me|built a cobble box in like ten seconds"
                ">> slept in it and it's still there now|i kept it, it's my cobble hut now|people think it's a monument lol"),
  dg=("cobble is the most useful block|cobblestone is the best block in the game",
      "it's everywhere|you always need it", "it's so ugly|dirt is more useful", "cobble gang", "fine, whatever"),
  db=("cobblestone is the ugliest block in the game|cobblestone houses are a crime|cobble should never be a building block",
      "it's grey noise|honestly", "it's honest|cobble is fine|mossy cobble is gorgeous",
      "every noob house is cobble|it looks like gravel glued together|smelt it into stone at least",
      "smelt it", "cobble forever|it's charming"))

S("deepslate", "deepslate", "mining building caves", "mining caves building",
  m="dug into deepslate today, so slow|got a bunch of cobbled deepslate|mined a stack of deepslate|"
    "hit the deepslate layer finally",
  mr="the slow stone|deep down huh|diamond layer",
  mf="takes twice as long to mine|diamonds are down there",
  q=["why is deepslate so slow to mine? > it's twice as hard as stone / it's tougher, takes double",
     "where does deepslate start? > around y 8, all of it below 0 / below zero, it fades in around y 8",
     "can you make tools from cobbled deepslate? > yeah, stone tools and furnaces / yep, it works like cobble"],
  qr="huh|ok then",
  lk="deepslate bricks are the best stone|deepslate tiles make perfect roofs|polished deepslate looks so clean|"
     "builder: deepslate and dark oak, moody and perfect|builder: deepslate tile roofs, nothing better",
  lkr="very dark|moody|deepslate tiles are so good",
  lkf="moody is good|dark is classy",
  tp="tip: deepslate takes twice as long, bring efficiency|cobbled deepslate works for furnaces and tools|"
     "most diamonds are in the deepslate layer|deepslate tiles make the best roofs|"
     "stonecutter turns cobbled deepslate into bricks and tiles|deepslate ores look darker, watch for them",
  tpn="a:voice_miner",
  br="my roof is deepslate tiles|built a whole deepslate castle|my base is all deepslate bricks",
  brr="moody|fancy",
  brf="took so long to mine|looks evil, in a good way",
  cp="mining deepslate is so slow|deepslate takes forever with an iron pick|my pick wears out in deepslate",
  cps="deepslate is a grind",
  cpm="twice as hard as stone|i get nothing done down there",
  cpx="get efficiency|bring haste|use a diamond pick|mine above y 0 for the tunnel",
  cpn="a:voice_miner",
  st=("good", "so i was branch mining down in the deepslate|story: i was digging slow through deepslate|i was deep in the deepslate"
               ">> took forever per block but i kept going|swung for ages, deepslate is slow"
               ">> and then the wall went blue, diamonds|broke through into a diamond vein|found five diamonds in one spot"),
  dg=("deepslate is the best building stone|deepslate beats stone bricks",
      "so moody|true", "too dark|stone bricks are cleaner", "deepslate gang", "you'll come around"),
  db=("deepslate is the worst thing they added|deepslate ruined mining|mining was better before deepslate",
      "so slow|honestly", "it looks great|the bricks are worth it|diamonds are down there anyway",
      "it doubles every tunnel|my pickaxes die twice as fast|nobody asked for slower mining",
      "slow is bad", "tiles though|it's fine with efficiency"))

S("granite", "granite", "building mining", "building mining decor",
  m="got a pile of granite from the cave|polished some granite today|granite everywhere in this cave|pink stone everywhere lol",
  mr="the pink stuff|granite lol|polished is decent",
  mf="it's not bad polished|might use it",
  q=["how do you make polished granite? > four granite in a square / craft four granite, or use a stonecutter",
     "can you craft granite? > diorite and nether quartz make it / yeah, diorite plus quartz",
     "is granite good for anything? > building, it's just decoration / walls and floors, that's it"],
  qr="ok|fair",
  lk="polished granite is underrated|granite floors look warm|granite and spruce actually match|"
     "builder: granite adds warmth to a grey wall|builder: polished granite steps look great",
  lkr="it's pink though|warm is nice|polished is decent",
  lkf="pink is warm|give it a chance",
  tp="tip: polish granite and it looks way better|granite matches spruce and dark oak|"
     "use granite for floors, not walls|a stonecutter makes granite stairs one to one|"
     "mix granite into a path for colour|granite is best as an accent",
  br="my kitchen floor is polished granite|built a granite fireplace wall|used granite for my whole stairway",
  brr="warm|fancy",
  brf="looks like real stone|nobody expects granite",
  cp="i have way too much granite|granite blobs ruin my tunnels|nobody wants granite",
  cps="lol granite",
  cpm="it's in every cave|my chests are pink now",
  cpx="polish it and use it|make a path|throw it in lava|trade it off",
  dg=("granite is the best of the three|granite beats diorite and andesite",
      "warmest one|true", "andesite is better|it's too pink", "granite gang", "pink haters smh"),
  db=("granite is the ugliest stone in the game|granite should be removed|nobody has ever built with granite",
      "so pink|truly", "polished granite is nice|it's warm|it's great for floors",
      "it looks like raw meat|every cave is pink blobs|name one good granite build",
      "pink blobs", "polished is fine|it's great for floors"))

S("diorite", "diorite", "building mining", "building mining jokes",
  m="got a stack of diorite, don't ask why|polished diorite looks ok actually|diorite everywhere in this cave|"
    "accidentally kept a whole stack of diorite",
  mr="lol diorite|why|bold",
  mf="i'll find a use|it's clean at least",
  q=["how do you make diorite? > two cobble and two nether quartz / cobble plus quartz",
     "what's diorite good for? > making andesite / white walls i guess",
     "how do you make andesite from diorite? > diorite plus cobble / one diorite one cobble, makes two"],
  qr="lol ok|good to know",
  lk="polished diorite is a nice white|diorite is fine, don't hate|polished diorite floors are so bright|"
     "builder: polished diorite and dark oak looks clean|builder: diorite looks good in snow builds",
  lkr="it's speckly|bold take|polished is ok",
  lkf="i stand by it|bold but true",
  tp="tip: polish diorite, it gets way smoother|turn diorite into andesite with cobble|"
     "polished diorite is a cheap white block|diorite works in snowy builds|"
     "a stonecutter makes polished diorite slabs|diorite looks like cookies and cream, embrace it",
  br="my whole bathroom is polished diorite|built a diorite tower, it's white|my snow cabin has diorite trim",
  brr="bold|lol nice",
  brf="hater free zone|it looks good i swear",
  cp="diorite is the worst stone|why is diorite everywhere|i keep picking up diorite by accident",
  cps="diorite pain",
  cpm="it's spotty|looks like bird mess",
  cpx="turn it into andesite|polish it|throw it in lava|build a snowman out of it",
  st=("funny", "so i hate diorite right|story: you know how i hate diorite|i've always hated diorite"
                ">> i built a little diorite hut as a joke|built one diorite wall to prove a point"
                ">> and now it's my favourite room|it looks great with polished diorite and i'm upset|now i'm a diorite guy"),
  dg=("diorite is the ugliest of the three|diorite is worse than granite",
      "so spotty|true", "granite is worse|andesite is the worst", "diorite haters", "fine"),
  db=("diorite is the worst block in the game|diorite deserves all the hate|nothing is worse than diorite",
      "spotty mess|so true", "polished diorite is nice|it's fine|dirt is worse",
      "it looks like cookies and cream gone wrong|why is it spotty|nobody chooses diorite",
      "spotty mess", "polished is fine|leave diorite alone"))

S("andesite", "andesite", "building mining", "building mining decor",
  m="got a load of andesite|polished andesite looks so good|andesite everywhere down here|made a polished andesite floor",
  mr="the good one|nice grey|clean",
  mf="best of the three|clean grey",
  q=["how do you make andesite? > diorite plus cobble / one diorite one cobble, makes two",
     "what's polished andesite good for? > floors, it's a clean grey / paths and floors",
     "is andesite better than stone? > for floors, maybe / it's softer grey, nice for paths"],
  qr="nice|ok",
  lk="polished andesite is the best floor|andesite is the best of the three|andesite paths look so clean|"
     "builder: polished andesite with stone bricks, perfect|builder: andesite is my go-to grey",
  lkr="clean|it's alright|polished andesite is solid",
  lkf="solid grey|it goes with everything",
  tp="tip: polished andesite is the cleanest floor|andesite paths look better than gravel|"
     "craft andesite from diorite and cobble if you need more|mix andesite into stone walls for texture|"
     "polished andesite slabs make nice steps|andesite works with any wood",
  br="my floors are all polished andesite|built a polished andesite plaza in town|my path is andesite all the way",
  brr="clean|nice",
  brf="looks so tidy|best floor",
  cp="andesite is so boring|my build is all grey with andesite|andesite looks too much like stone",
  cps="grey on grey",
  cpm="i can't tell it from stone|my walls look flat",
  cpx="add some wood|mix in granite|use it for floors only|add moss",
  dg=("andesite is the best of the three stones|andesite wins over granite and diorite",
      "easy|true", "granite is better|they're all bad", "andesite gang", "ok hater"),
  db=("andesite is the most useless block|andesite is just worse stone|nobody needs andesite",
      "kinda|lol", "polished andesite floors|it's the best grey|it's for texture",
      "it's stone with a different name|stone bricks do it better|it's filler",
      "filler block", "floors though|polished is great"))

S("tuff", "tuff", "building mining caves", "building mining structures",
  m="got a bunch of tuff from deep down|tuff bricks look so good|made polished tuff today|tuff everywhere near the deepslate",
  mr="the grey green stone|tuff got good|tuff bricks are great",
  mf="it's way better than it used to be|trial chambers are full of it",
  q=["where do you find tuff? > deep down, below y 16 / in blobs near deepslate",
     "what can you make from tuff? > tuff bricks, polished tuff, chiseled tuff / bricks and polished, stairs too",
     "what's made of tuff? > trial chambers, tuff and copper / the trial chambers"],
  qr="cool|didn't know",
  lk="tuff bricks are my new favourite|tuff and copper look amazing together|chiseled tuff is so pretty|"
     "builder: tuff bricks with oak, perfect|builder: polished tuff makes the best floor",
  lkr="new favourite?|tuff is nice now|it was boring before",
  lkf="glow up|it really is",
  tp="tip: tuff is down below y 16, near the deepslate|a stonecutter turns tuff into bricks and stairs|"
     "tuff and copper go together, look at a trial chamber|polished tuff is great for floors|"
     "chiseled tuff makes nice trim|tuff mines faster than deepslate",
  br="my new house is all tuff bricks|built a tuff and copper tower|made a chiseled tuff fireplace",
  brr="trendy|nice",
  brf="it's so good now|you gotta try it",
  cp="i used to throw all my tuff away|tuff was useless for so long|i lavaed like ten stacks of tuff",
  cps="lol rip",
  cpm="now tuff bricks are a thing|i want it all back",
  cpx="go dig more|it's everywhere deep down|deepslate layer is full of it|trial chambers have tons",
  dg=("tuff bricks are better than stone bricks|tuff bricks beat stone bricks",
      "so much nicer|true", "stone bricks are classic|tuff is too green", "tuff gang", "you'll come around"),
  db=("tuff is the best stone now|tuff went from worst to best|nothing beats tuff bricks",
      "glow up|facts", "deepslate is better|it's still kinda grey|stone bricks forever",
      "tuff bricks with copper look amazing|it's the best middle grey|deepslate is too dark",
      "tuff gang", "still grey|it's ok"))

S("calcite", "calcite", "building caves", "building caves decor",
  m="got some calcite from a geode|calcite is so white|found calcite on the stony peaks|picked up a stack of calcite",
  mr="the white stuff|so white|nice for snow builds",
  mf="pure white|marble vibes",
  q=["where is calcite? > in amethyst geodes, the middle layer / geodes and stony peaks",
     "can you make calcite stairs? > no, it's just the block / nope, only full blocks",
     "what is calcite good for? > white walls, it's pure white / building, it's the best white stone"],
  qr="ah|ok",
  lk="calcite is the cleanest white block|calcite looks like marble|love calcite in a snow build|"
     "builder: calcite and dark oak is so crisp|builder: calcite walls make a room so bright",
  lkr="so white|marble vibes|calcite is nice",
  lkf="marble without the work|so clean",
  tp="tip: calcite is in geodes, around the amethyst|stony peaks have lots of calcite|"
     "calcite has no stairs or slabs, plan for that|calcite mines fast, any pickaxe|"
     "calcite makes great snowy builds|pair calcite with dark wood for contrast",
  br="my bathroom is all calcite|built a calcite tower, so white|my snow house is calcite and spruce",
  brr="marble!|fancy",
  brf="took a whole geode|looks like marble",
  cp="calcite has no stairs|wish calcite had slabs|my calcite build looks blocky",
  cps="no stairs is rough",
  cpm="i need stairs for the roof|everything has to be full blocks",
  cpx="use quartz stairs|mix in diorite slabs|use snow layers on top|just go blocky",
  dg=("calcite is the best white block|calcite beats quartz",
      "so clean|true", "quartz has stairs|white concrete is cleaner", "calcite gang", "fair enough"),
  db=("calcite should have stairs and slabs|calcite is useless without stairs|calcite is wasted without slabs",
      "so true|please", "it's fine as is|use quartz|it's for walls",
      "every white build needs stairs|it's the best white and you can't use it|they made tuff stairs, why not calcite",
      "give us stairs", "full blocks are fine|i'll use quartz"))

# ======================================================================= Blocks: sand, clay and colour
S("sandstone", "sandstone", "building biomes", "building biomes structures",
  m="crafted a stack of sandstone|smoothed some sandstone in the furnace|the desert is all sandstone under the sand|"
    "got red sandstone from the badlands",
  mr="desert vibes|sandy|smooth sandstone is great",
  mf="looks so clean|four sand a block",
  q=["how do you make sandstone? > four sand in a square / four sand, like a little block",
     "what's smooth sandstone? > sandstone smelted in a furnace / you smelt it, it loses the texture",
     "how do you get chiseled sandstone? > two sandstone slabs stacked / craft two slabs on top of each other"],
  qr="ok|nice",
  lk="smooth sandstone is so clean|sandstone houses fit the desert perfectly|cut sandstone makes great trim|"
     "builder: sandstone with dark oak, perfect desert look|builder: chiseled sandstone pillars, so good",
  lkr="desert vibes|a bit yellow|cut sandstone is nice",
  lkf="yellow is warm|it fits",
  tp="tip: sandstone under desert sand keeps it from falling|four sand make one sandstone|"
     "smelt it for smooth sandstone|red sand makes red sandstone, same recipes|"
     "you need a pickaxe or it drops nothing|cut sandstone is great for floors",
  br="my house is all sandstone and fits the desert|built a sandstone temple of my own|my walls are smooth sandstone",
  brn="a:has_house",
  brr="desert style|fancy",
  brf="the sand was free|took a lot of sand",
  cp="sandstone eats so much sand|i need four sand for every block|my sandstone house is taking forever",
  cps="sand adds up",
  cpm="i dug up half the desert|my shovel is almost dead",
  cpx="mine the sandstone that's already there|dig under the desert, it's all sandstone|get an efficiency shovel|use it for trim only",
  st=("good", "so i was crossing the desert|story: i was out in the desert|i was walking through a desert"
               ">> found a sandstone temple half in the sand|saw a pyramid sticking out of a dune"
               ">> got gold and two diamonds out of it|looted it carefully, avoided the tnt|came back rich, it was all in the basement"),
  dg=("sandstone is the best desert block|in the desert only sandstone makes sense",
      "fits perfectly|true", "terracotta looks better|mud bricks are better there", "sand gang", "ok fancy"),
  db=("sandstone is the most boring block|sandstone is ugly and flat|sandstone builds all look the same",
      "so flat|kinda", "cut sandstone is great|smooth sandstone is clean|it fits the desert",
      "every desert village looks the same|yellow on yellow on yellow|terracotta does it better",
      "same old yellow", "it's classic|still like it"))

S("terracotta", "terracotta", "building decor biomes", "building decor biomes",
  m="mined a ton of terracotta in the badlands|made some glazed terracotta|dyed a stack of terracotta|"
    "the badlands are all stripes of terracotta",
  mr="colours!|badlands stuff|glazed is the best",
  mf="so many colours|my floor is gonna be wild",
  q=["how do you make terracotta? > smelt a clay block / clay block in a furnace",
     "how do you dye terracotta? > eight terracotta and one dye / surround a dye with eight terracotta",
     "what's glazed terracotta? > smelt coloured terracotta, it gets a pattern / coloured terracotta smelted again"],
  qr="cool|nice",
  lk="glazed terracotta floors are amazing|terracotta has the best colours|the badlands stripes are so pretty|"
     "builder: orange terracotta with dark oak, so warm|builder: glazed terracotta patterns, my favourite floor",
  lkr="so colourful|a bit loud|glazed is the best",
  lkf="loud is fun|colour is life",
  tp="tip: the badlands have free terracotta in every colour|smelt clay blocks for plain terracotta|"
     "eight terracotta and one dye makes eight coloured|glazed terracotta turns when you place it, rotate it for patterns|"
     "sticky pistons can't pull glazed terracotta|terracotta is a nice soft colour for roofs",
  br="my floor is all glazed terracotta patterns|built a terracotta house in the badlands|my roof is orange terracotta",
  brr="colourful|wild",
  brf="took forever to line up|it looks like tiles",
  cp="glazed terracotta patterns never line up|placing glazed terracotta is so fiddly|my pattern is all wrong",
  cps="glazed is tricky",
  cpm="it turns depending where you look|i've placed it ten times",
  cpx="face a different way when you place it|place them in a circle|plan it on a test floor|just go random",
  st=("funny", "so i wanted a fancy glazed floor|story: i made a glazed terracotta floor|i spent all day on a glazed floor"
                ">> placed every tile facing the same way|did a whole room without checking"
                ">> it looked like a maze of arrows|turned out it was a weird swirl|everyone asks if it's a puzzle"),
  dg=("terracotta beats concrete|terracotta looks better than concrete",
      "softer colours|true", "concrete is cleaner|too dull", "terracotta gang", "fair"),
  db=("glazed terracotta is ugly|nobody should use glazed terracotta|glazed terracotta looks like a bathroom",
      "kinda loud|lol true", "it makes great floors|patterns are cool|it's art",
      "every pattern looks like a carpet from a dream|it never lines up|plain terracotta looks better",
      "too loud", "still art|my floor says no"))

S("concrete", "concrete", "building decor", "building decor town",
  m="mixed a stack of concrete powder|made white concrete today|turned powder into concrete in a river|"
    "got every colour of concrete now",
  mr="modern|bright colours|concrete gang",
  mf="so clean|modern build time",
  q=["how do you make concrete? > concrete powder touching water / make powder, then wet it",
     "what's in concrete powder? > four sand, four gravel and a dye / sand, gravel and dye",
     "does concrete powder fall? > yeah, like sand / yep, it falls until it's wet"],
  qr="huh|cool",
  lk="white concrete is the cleanest block|concrete colours are so bright|concrete makes the best modern builds|"
     "builder: white concrete and glass, modern heaven|builder: grey concrete with spruce looks so good",
  lkr="very modern|too flat|clean though",
  lkf="flat is clean|modern is the vibe",
  tp="tip: four sand, four gravel and one dye make eight powder|place the powder in water to harden it|"
     "use a pickaxe on concrete or it drops nothing|place powder next to water then mine it as concrete|"
     "concrete powder falls like sand, don't build overhangs with it|white dye from lily of the valley or bone meal",
  br="built a white concrete modern house|my house is all concrete and glass|made a rainbow concrete wall",
  brr="modern|clean",
  brf="took so much sand and gravel|feels like the future",
  cp="making concrete takes forever|wetting concrete powder is so tedious|i placed and broke like 500 blocks",
  cps="concrete is a chore",
  cpm="place, mine, place, mine|my pickaxe is dying",
  cpx="put it on the edge of a river|get efficiency on your pick|make it in a water channel|do it with a friend",
  st=("funny", "so i was hardening concrete powder|story: i made a pile of concrete powder|i was turning powder into concrete"
                ">> it was raining so i placed it outside|figured the rain would do it"
                ">> rain doesn't harden it lol|it just sat there for an hour|still powder after the whole storm"),
  dg=("concrete is the best block for modern builds|nothing beats concrete for modern",
      "so clean|true", "quartz is better|it's too flat", "concrete gang", "fair enough"),
  db=("concrete is the ugliest block for building|concrete houses have no soul|concrete is lazy building",
      "so flat|true", "it's clean|modern builds need it|it's bright",
      "flat colour, no texture, no life|every concrete house looks like a box|use terracotta, it has warmth",
      "no soul", "still clean|i like boxes"))

S("glass", "glass", "building decor crafting", "building decor crafting",
  m="smelted a stack of glass|put glass windows in my house|made some glass panes|got stained glass going",
  mr="windows!|clear|panes are nicer",
  mf="windows coming|so much better with glass",
  q=["how do you make glass? > smelt sand / sand in a furnace",
     "how do you pick glass back up? > silk touch, otherwise it breaks / silk touch only",
     "how do you make stained glass? > eight glass and a dye / glass around a dye"],
  qr="ok|nice",
  lk="glass makes a house feel alive|big windows are the best|stained glass looks amazing with light|"
     "builder: glass panes look so much better than blocks|builder: tinted glass is so classy",
  lkr="windows are a must|i like small windows|stained glass is pretty",
  lkf="must have|light is life",
  tp="tip: glass panes are thinner, nicer for windows|six glass make sixteen panes|"
     "glass breaks with no drop, use silk touch|mobs can't spawn on glass|"
     "tinted glass blocks light, amethyst and glass|stained glass windows look great at sunset",
  br="my house has a wall of glass|built a glass dome over my farm|put stained glass in my tower",
  brn="a:has_house",
  brr="fancy|so bright",
  brf="took three stacks of sand|you can see everything",
  cp="broke my window again|glass just shatters with nothing back|mined my own window by accident",
  cps="glass pain",
  cpm="it drops nothing|so much sand wasted",
  cpx="silk touch|be careful near windows|make panes, they're cheaper|keep spare glass",
  st=("funny", "so i built a glass floor over my lava pit|story: i made a glass floor for fun|i had a glass floor over lava"
                ">> stood on it to look down|was walking on it showing off"
                ">> mined the wrong block by accident|it held, but my heart did not|never doing that again lol"),
  dg=("glass panes look better than glass blocks|panes beat full glass blocks",
      "thinner, nicer|true", "full blocks look cleaner|panes look cheap", "pane gang", "fair"),
  db=("every house needs huge windows|small windows are a crime|a house without big windows is a cave",
      "light is life|true", "small windows are cozy|big windows let mobs see you|privacy please",
      "a dark house is a sad house|you can't see the sunrise|glass costs nothing, it's sand",
      "windows forever", "cozy is better|small windows forever"))

S("wool", "wool", "building decor animals", "decor animals building",
  m="sheared like ten sheep today|got a stack of white wool|made some coloured wool|dyed a sheep blue",
  mr="cozy|fluffy|sheep power",
  mf="for carpets|my bed needs it",
  q=["how much wool from a sheep? > one to three with shears / shears get up to three",
     "how do you get coloured wool? > dye the sheep, then shear / dye the wool or the sheep",
     "can you make wool from string? > four string make one / yeah, four string"],
  qr="nice|huh",
  lk="coloured wool is so fun|wool carpets make a room|love a cozy wool rug|"
     "builder: wool for colour accents, perfect|builder: wool carpets on a stair are the best",
  lkr="cozy|wool burns though|carpets are great",
  lkf="cozy wins|i don't light fires",
  tp="tip: dye a sheep and it grows that colour forever|shears give more wool than killing sheep|"
     "sheep regrow wool by eating grass|wool blocks sculk sensors, good in the deep dark|"
     "two wool make three carpets|wool burns, keep it away from lava",
  br="i have every colour of sheep now|built a wool farm with dyed sheep|my house has wool carpets everywhere",
  brr="colourful|cozy",
  brf="took ages to dye them|rainbow pen",
  cp="wool burns so easily|my rug caught fire|lava set my wool house on fire",
  cps="noooo",
  cpm="one spark and gone|the whole carpet went",
  cpx="keep fire away|use carpet on stone floors|put a block between lava and wool|use concrete instead",
  st=("funny", "so i dyed a sheep pink|story: i had a pink sheep|i made a pink sheep for fun"
                ">> it got out of the pen|it wandered off one night"
                ">> now there's a pink sheep in some village|a villager is raising it now lol|i see it on the hill sometimes"),
  dg=("wool is the best decor block|nothing beats wool for decor",
      "so many colours|true", "it burns|concrete is cleaner", "wool gang", "fine"),
  db=("wool should never be a building block|wool houses are asking for fire|building with wool is a mistake",
      "fire hazard|true", "it's soft and pretty|just don't light fires|wool looks great",
      "one lightning bolt and it's gone|it's a sweater, not a house|use concrete if you want colour",
      "fire hazard", "still pretty|i'll risk it"))

S("bricks", "bricks", "building crafting", "building crafting home",
  m="smelted a stack of bricks|my brick house is coming along|got a pile of clay for bricks|made a brick fireplace",
  mr="classic|red bricks|cozy",
  mf="it's so cozy|clay takes forever though",
  q=["how do you make bricks? > smelt clay balls, four bricks make a block / clay ball in the furnace",
     "where do you get clay? > rivers and swamps, it's grey under the water / in lakes and lush caves",
     "what else needs bricks? > flower pots and decorated pots / flower pots, three bricks"],
  qr="ok|good to know",
  lk="brick houses are the coziest|red brick with white trim is perfect|a brick fireplace makes a home|"
     "builder: bricks and dark oak, so warm|builder: brick stairs make a great roof edge",
  lkr="very cozy|it's a lot of clay|classic",
  lkf="classic for a reason|worth the clay",
  tp="tip: clay is grey under rivers, dig it up|one clay block gives four clay balls|"
     "four brick items make one brick block|a mason villager will trade you bricks|"
     "flower pots are three bricks|smelt a lot at once, bricks are slow to make",
  br="my house is all red brick|built a brick wall around my garden|made a brick fireplace in my living room",
  brn="a:has_house",
  brr="cozy|classy",
  brf="took like a hundred clay|it's my favourite room",
  cp="bricks need so much clay|i ran out of clay halfway|smelting clay takes forever",
  cps="clay is rare",
  cpm="one block is four bricks, that's four clay|i dug up every river",
  cpx="trade with a mason|lush caves have clay|swamps are full of it|use mud bricks instead",
  st=("good", "so i needed clay for bricks|story: i was looking for clay|i needed a lot of clay"
               ">> found a river bed full of it|dove into a lake and the bottom was all clay"
               ">> got like six stacks|came back with enough for a whole house|built my brick house that week"),
  dg=("brick is the best house material|bricks beat wood for houses",
      "so cozy|true", "too much work|wood is nicer", "brick gang", "fair"),
  db=("bricks are the best looking block in the game|nothing looks better than bricks|bricks beat every block",
      "so cozy|facts", "too red|deepslate bricks are better|too much clay",
      "red bricks never look bad|any house in brick looks finished|deepslate is gloomy, bricks are warm",
      "brick gang", "still too red|i'll stick to wood"))

S("mud_bricks", "mud bricks", "building biomes crafting", "building biomes crafting",
  m="made a stack of mud bricks|turned some dirt into mud|packed mud with wheat today|built a mud brick hut",
  mr="adobe vibes|earthy|mud gang",
  mf="looks so earthy|desert style",
  q=["how do you make mud? > water bottle on dirt / pour a water bottle on dirt",
     "how do you make packed mud? > mud and wheat / mud plus wheat",
     "can mud turn into clay? > put it on a block over pointed dripstone / yeah, with dripstone under it"],
  qr="cool|neat",
  lk="mud bricks look so earthy|mud brick houses fit the desert|love the warm brown of mud bricks|"
     "builder: mud bricks and mangrove, perfect|builder: mud bricks with spruce, so cozy",
  lkr="earthy|nice and warm|mud is underrated",
  lkf="warm is good|underrated for sure",
  tp="tip: a water bottle on dirt makes mud|mud plus wheat makes packed mud|four packed mud make four mud bricks|"
     "mangrove swamps have free mud|mud over dripstone dries into clay|you sink a bit in mud, careful",
  br="built a mud brick house in the desert|my walls are mud bricks now|made an adobe village of my own",
  brr="earthy|cool",
  brf="it fits so well|cheap too",
  cp="making mud takes so many water bottles|mud bricks need wheat too, so much work|i filled bottles all day",
  cps="that's a grind",
  cpm="one bottle per block|my wheat farm is empty now",
  cpx="get mud from the mangrove swamp|use a water source next to you|bring a stack of bottles|use bricks",
  dg=("mud bricks are better than normal bricks|mud bricks beat red bricks",
      "warmer|true", "red bricks are classic|mud looks dirty", "mud gang", "fair"),
  db=("mud bricks are the best block they added|mud bricks are underrated|mud bricks beat every brick",
      "so earthy|honestly", "too much work|bricks are better|they look like dirt",
      "free from dirt and water|they fit desert and swamp builds|red bricks need clay you can't find",
      "mud gang", "still dirt|red bricks forever"))

S("quartz", "quartz", "building crafting", "building crafting decor", gate="nether",
  m="mined a load of nether quartz|crafted a stack of quartz blocks|made some quartz pillars|got smooth quartz going",
  mr="fancy|so white|quartz is classy",
  mf="looks so clean|the nether was worth it",
  q=["where do you get quartz? > the nether, quartz ore in the red rock / nether quartz ore, all over the nether",
     "how do you make a quartz block? > four quartz / four nether quartz in a square",
     "what else is quartz for? > comparators, observers, daylight sensors / redstone stuff mostly"],
  qr="huh|ok",
  lk="quartz is the cleanest white|quartz pillars make any doorway grand|smooth quartz floors are perfect|"
     "builder: quartz and dark oak, so classy|builder: quartz pillars with glass, modern palace",
  lkr="fancy|very clean|quartz pillars are great",
  lkf="fancy is the goal|clean and classy",
  tp="tip: nether quartz ore gives xp too|four quartz make a block|smelt quartz blocks for smooth quartz|"
     "quartz pillars turn when you place them sideways|it's easy to find in the nether, everywhere|"
     "quartz goes with any dark wood",
  br="my house is all quartz now|built a quartz palace|made a quartz floor in my hall",
  brr="fancy|rich",
  brf="took a few nether trips|looks like marble",
  cp="quartz mining in the nether is scary|i keep getting knocked into lava mining quartz|ghasts hate me mining quartz",
  cps="nether is rough",
  cpm="every ore is next to lava|i need so much for a house",
  cpx="bring fire resistance|mine with blocks around you|bring a friend|bring cobble walls",
  dg=("quartz is the best white block|quartz beats calcite and concrete",
      "so clean|true", "concrete is cleaner|calcite looks better", "quartz gang", "fair"),
  db=("quartz builds look like hospitals|quartz is too white to live in|quartz houses are soulless",
      "so white|kinda", "it's classy|mix it with wood|quartz is beautiful",
      "all white, no warmth|it's a sterile box|add any colour and it looks better",
      "too white", "still classy|you just mix it"))

S("prismarine", "prismarine", "building ocean", "ocean building structures",
  m="got a load of prismarine shards|crafted some dark prismarine|made prismarine bricks|the guardians dropped crystals too",
  mr="ocean stuff|from a monument? > yeah, the guardians / yep, fought guardians for it",
  mf="it changes colour slowly|so pretty",
  q=["how do you get prismarine? > kill guardians, they drop shards / guardians at ocean monuments",
     "how do you make dark prismarine? > eight shards and black dye / shards around a black dye",
     "what are prismarine crystals for? > sea lanterns / sea lanterns, with shards"],
  qr="ok|neat",
  lk="prismarine is the prettiest block|dark prismarine is so moody|prismarine bricks look great underwater|"
     "builder: prismarine and sea lanterns, ocean palace|builder: dark prismarine roofs are amazing",
  lkr="ocean vibes|so pretty|the colour shifts",
  lkf="ocean palace|it's so pretty",
  tp="tip: guardians drop shards, kill them at monuments|four shards make prismarine, nine make bricks|"
     "dark prismarine needs black dye|the elder guardian gives mining fatigue, kill it first|"
     "drink water breathing before a monument|monuments have gold blocks inside",
  br="my underwater base is all prismarine|built a prismarine roof|got four stacks of shards",
  brr="ocean palace|rich",
  brf="took like ten trips|guardians were a pain",
  cp="mining fatigue in the monument is the worst|guardians keep lasering me|ocean monuments are so annoying",
  cps="guardians are mean",
  cpm="i can barely mine anything|they zap you from far away",
  cpx="kill the elder guardians first|bring milk for the fatigue|use doors for air|bring water breathing",
  st=("bad", "so i went to raid an ocean monument|story: i swam to an ocean monument|i tried an ocean monument"
              ">> an elder guardian gave me mining fatigue|guardians started lasering me from every side"
              ">> drowned trying to swim out|lost all my stuff at the bottom of the sea|never got a single shard"),
  dg=("prismarine is the best block for underwater builds|underwater, nothing beats prismarine",
      "made for it|true", "glass is better underwater|sea lanterns alone are enough", "ocean gang", "fair"),
  db=("prismarine is not worth the monument|prismarine is too much work|no block is worth an ocean monument",
      "so much work|true", "it's beautiful|monuments are fun|the gold alone is worth it",
      "mining fatigue, drowning, lasers, for a block|you need a whole guardian farm|wear the cost, get nothing",
      "too much work", "still worth it|pretty is worth pain"))

S("copper", "copper", "ore building", "ore mining building",
  m="finally got a stack of copper|copper everywhere in this cave lol|smelted a whole furnace of raw copper|"
    "found a fat copper vein by the dripstone",
  mr="orange gang|what are you gonna do with it? > copper roof probably / lightning rods / no idea honestly",
  mf="so shiny|my chest is all copper now",
  q=["what's copper even good for? > lightning rods, spyglasses, the brush / building mostly, it looks great",
     "where's the most copper? > dripstone caves are full of it / around y 48, it's everywhere",
     "does fortune work on copper? > yeah, raw copper drops way more / yep, it's huge on copper"],
  qr="huh|ok",
  lk="fresh copper is so shiny|copper blocks look amazing in the sun|copper doors are my favourite|"
     "builder: copper and tuff together, chef's kiss|builder: cut copper trim is so underrated",
  lkr="until it goes green|cut copper is nice|so shiny",
  lkf="wax it, problem solved|the green is pretty too",
  tp="tip: dripstone caves are full of copper|fortune on copper is huge|you need a stone pickaxe at least|"
     "three copper ingots make a lightning rod|a spyglass is two copper and an amethyst shard|"
     "nine ingots make a block, good for storing it",
  tpn="a:voice_miner",
  br="my roof is solid copper|got like six stacks of copper ingots|built a copper tower that shines",
  brr="shiny|rich",
  brf="it'll go green eventually|i'm keeping it orange",
  cp="copper is everywhere and i have no use for it|my chests are drowning in copper|i have stacks of copper and nothing to do",
  cps="copper flood",
  cpm="every cave is copper|i can't throw it away though",
  cpx="build a copper roof|make lightning rods|trade it|make copper blocks, they stack better",
  st=("funny", "so i was in a dripstone cave|story: i went into a dripstone cave|i found this huge dripstone cave"
                ">> the walls were just copper everywhere|mined copper for an hour straight"
                ">> came home with eight stacks and nothing to do with it|my furnaces ran for two days|built a copper roof out of spite"),
  dg=("copper is the best building metal|copper beats iron blocks for building",
      "so pretty|true", "it goes green|iron blocks are cleaner", "copper gang", "fair"),
  db=("copper is useless|copper is the worst ore|copper should not exist",
      "so useless|kinda", "lightning rods|spyglasses|copper roofs look amazing",
      "a spyglass and a rod, that's it|you get eight stacks for nothing|iron does everything copper doesn't",
      "useless ore", "still pretty|roofs though"))

S("copper_oxide", "copper going green", "building decor", "building decor home",
  m="my copper roof went a bit green|waxed my copper blocks today|scraped my copper back to orange|"
    "my copper door is turning teal",
  mr="the green phase|teal is nice|copper does that",
  mf="copper is so fun|took a while",
  q=["how do you stop copper going green? > wax it with honeycomb / honeycomb, right click it on",
     "how do you get the orange back? > scrape it with an axe / axe it, a stage at a time",
     "how long does copper take to go green? > ages, it goes in stages / a long time, four stages"],
  qr="huh|ok",
  lk="fully green copper is gorgeous|i love the weathered teal|exposed copper is the best stage|"
     "builder: a green copper roof is pure class|builder: half orange half green looks so real",
  lkr="green is classy|orange is better|the middle is nice",
  lkf="classy is right|green forever",
  tp="tip: honeycomb waxes copper, it stops changing|an axe scrapes off a stage or the wax|"
     "lightning strikes clean copper back to orange|it goes copper, exposed, weathered, oxidized|"
     "water doesn't speed it up, it just takes time|copper bulbs get dimmer as they go green",
  br="my roof is fully green now and it's perfect|waxed my copper at the exact shade i wanted|my copper dome went teal",
  brn="a:has_house",
  brr="patience|classy",
  brf="took forever to wait|worth the wait",
  cp="my copper went green when i wanted orange|forgot to wax my copper|my nice orange roof is all patchy",
  cps="nooo",
  cpm="half green half orange|it looks moldy",
  cpx="scrape it with an axe|wax it this time|get honeycomb from a hive|wait for lightning",
  st=("funny", "so i built a copper roof|story: my copper roof|i had this shiny copper roof"
                ">> forgot to wax it and went away for ages|didn't wax it, went mining for a week"
                ">> came back and it was green and patchy|it looked like a moldy loaf|my neighbour thought it was moss"),
  dg=("green copper looks better than orange|oxidised copper is the best stage",
      "classy|true", "orange is shinier|the middle is best", "green gang", "fair"),
  db=("waxing copper ruins the point|copper is meant to go green, let it|wax is cheating",
      "let it age|true", "i want my colour|wax is great|green is ugly",
      "the whole point of copper is it changes|a waxed roof is a fake roof|patience, it gets better",
      "let it age", "still waxing|orange forever"))

# ======================================================================= Blocks: minerals, earth, cold, light
S("amethyst", "amethyst", "caves building ore", "caves mining decor",
  m="found an amethyst geode today|got a stack of amethyst shards|the geode was so pretty inside|"
    "amethyst blocks make a sound when you walk",
  mr="ooh purple|so pretty|geodes are the best",
  mf="it chimes when you walk|so pretty",
  q=["can you move budding amethyst? > no, it breaks even with silk touch / nope, it stays where it is",
     "what are amethyst shards for? > spyglass, tinted glass / tinted glass and spyglasses",
     "how do you get shards? > mine the full clusters / break the big clusters, small buds give nothing"],
  qr="ah|ok",
  lk="amethyst geodes are the prettiest thing underground|the chime of amethyst is so nice|amethyst blocks look magical|"
     "builder: amethyst and calcite, crystal cave vibes|builder: amethyst with deepslate looks so good",
  lkr="so pretty|the sound is relaxing|purple is nice",
  lkf="so relaxing|i could sit in one all day",
  tp="tip: only full clusters drop shards|budding amethyst can't be moved, build a farm around it|"
     "fortune gives more shards|small buds break into nothing, wait for clusters|"
     "geodes are smooth basalt outside, calcite inside|four shards make an amethyst block",
  br="built my base around an amethyst geode|got an amethyst farm going|my house has an amethyst room",
  brr="magical|fancy",
  brf="it chimes all day|best room in the house",
  cp="broke all the budding amethyst by accident|i mined the budding blocks thinking they'd drop|my geode is ruined",
  cps="noooo",
  cpm="they just break into nothing|now nothing grows there",
  cpx="find another geode|they're everywhere deep down|never touch budding ones|look for clusters only",
  st=("good", "so i was digging a tunnel|story: i was branch mining|i was tunneling down"
               ">> broke into a geode by accident|heard a chime and broke through to purple"
               ">> it was huge and full of clusters|got a stack of shards and calcite|now it's my secret spot"),
  dg=("amethyst is the prettiest block|nothing is prettier than amethyst",
      "so pretty|true", "it's too purple|cherry leaves are prettier", "amethyst gang", "fair"),
  db=("amethyst is useless|amethyst is pretty but pointless|nobody needs amethyst",
      "kinda|lol", "spyglass|tinted glass|it's decoration",
      "a spyglass you never use|tinted glass, wow|it's a purple rock",
      "pretty rock", "still pretty|spyglass though"))

S("obsidian", "obsidian", "mining building crafting", "mining crafting building",
  m="mined some obsidian with my diamond pick|made obsidian with a water bucket|got ten obsidian for a portal|"
    "got a stack of obsidian",
  mr="tough stuff|obsidian is slow|nice",
  mf="worth it|finally",
  q=["how do you mine obsidian? > diamond pickaxe or better / you need diamond, iron just breaks it for nothing",
     "how do you make obsidian? > water on a lava source / pour water on still lava",
     "what's obsidian for? > portals, enchanting tables, ender chests / nether portals mostly"],
  qr="ok|got it",
  lk="obsidian is the toughest block, i love it|obsidian walls are so cool|obsidian looks so evil|"
     "builder: obsidian with crying obsidian, spooky|builder: obsidian trim on a dark build",
  lkr="evil vibes|super tough|cool",
  lkf="evil is cool|creeper proof",
  tp="tip: pour water on lava sources to make obsidian|a nether portal needs ten without corners|"
     "obsidian survives creepers and tnt|a diamond pick takes about ten seconds a block|"
     "you can make a portal with lava and water, no diamond pick|ender chests need eight obsidian",
  br="i have two stacks of obsidian|built an obsidian vault|my bunker is all obsidian",
  brr="tough|creeper proof",
  brf="took forever to mine|nothing gets in",
  cp="mining obsidian takes forever|obsidian destroyed my pick durability|obsidian is the slowest block ever",
  cps="ouch",
  cpm="ten seconds a block|i was bored out of my mind",
  cpx="efficiency on a diamond pick|make it on the spot with water|haste helps|use the bucket trick for portals",
  st=("bad", "so i was mining obsidian near lava|story: i was mining obsidian over a lava lake|i was getting obsidian at a lava pool"
              ">> broke a block and there was lava under it|the block i stood on was over lava"
              ">> fell in with all my stuff|my diamond pick went with me|lost everything, even the obsidian"),
  dg=("obsidian is the best defensive block|nothing protects like obsidian",
      "creepers can't touch it|true", "too slow to build with|stone is enough", "obsidian gang", "fair"),
  db=("you don't need a diamond pick for a portal|diamond picks for obsidian are a waste|bucket portals are better",
      "bucket method|true", "diamond pick is easier|bucket is risky|lava is dangerous",
      "water, lava, done, no pick needed|you save diamond durability|ten obsidian, ten buckets",
      "bucket gang", "still risky|i'll mine it"))

S("dirt", "dirt", "building random", "building farming jokes",
  m="got a stack of dirt for pillaring|my inventory is half dirt again|dug out a hill of dirt|"
    "made some coarse dirt for a path",
  mr="lol dirt|the eternal dirt|dirt is life",
  mf="always useful|you can never have enough",
  q=["how do you make coarse dirt? > two dirt and two gravel / dirt and gravel in a square",
     "how does grass spread? > it spreads to dirt next to it in light / it creeps onto dirt beside it",
     "how do you make a dirt path? > shovel on grass / right click grass with a shovel"],
  qr="ok|neat",
  lk="dirt is the most useful block|i love a fresh dirt patch|dirt is honest|"
     "builder: coarse dirt makes nice paths|builder: dirt paths make a garden feel real",
  lkr="lol|it's dirt|coarse dirt is nice",
  lkf="dirt is life|respect the dirt",
  tp="tip: carry dirt for pillaring, it's fast to break|coarse dirt stops grass spreading|"
     "shovel grass for a path|water a dirt patch with a hoe for farmland|"
     "a water bottle turns dirt into mud|dirt breaks fast with a shovel",
  br="my dirt hut is still standing|i have twelve stacks of dirt|built a dirt tower to the sky",
  brr="lol|legend",
  brf="dirt is life|no shame",
  cp="my inventory is full of dirt again|why do i keep picking up dirt|dirt everywhere in my chests",
  cps="lol",
  cpm="i dug one tunnel and it's all dirt|i can't throw it away",
  cpx="toss it|make a dirt hill|use it for a farm|fill some holes",
  st=("funny", "so on my first night i built a dirt hut|story: my first night ever|my first ever house was dirt"
                ">> it had no door, just a hole|i slept on the floor in a box of dirt"
                ">> and that hut is still my favourite place|i still go back to it sometimes|i won't let anyone break it"),
  dg=("dirt houses deserve more respect|a dirt house is still a house",
      "everyone starts there|true", "they're ugly|move on", "dirt gang", "fair"),
  db=("dirt is the most useful block in the game|dirt beats diamonds for usefulness|nothing is more useful than dirt",
      "farming|honestly", "cobble is more useful|diamonds lol|dirt's just dirt",
      "farms, paths, pillars, huts, all dirt|you can't grow wheat on diamonds|you use it every single day",
      "dirt gang", "still diamonds|dirt is dirt"))

S("gravel", "gravel", "mining caves", "mining caves crafting",
  m="got a stack of gravel|dug a gravel pile for flint|gravel everywhere in this cave|used gravel for concrete",
  mr="flint time|gravel lol|annoying stuff",
  mf="needed it|gravel is so annoying though",
  q=["how do you get flint? > dig gravel, sometimes it drops flint / break gravel, one in ten",
     "does fortune help flint? > yeah, fortune three is always flint / yep, more flint",
     "why does gravel fall? > it's like sand, no support / gravity, like sand"],
  qr="ok|got it",
  lk="gravel paths look nice|gravel is good for flint|i like a gravel beach|"
     "builder: gravel with coarse dirt makes a natural path|builder: gravel under water looks real",
  lkr="gravel is annoying|paths are nice|it's fine",
  lkf="annoying but useful|paths though",
  tp="tip: dig gravel for flint, about one in ten|fortune three always gives flint|"
     "put a torch under a gravel column and break the bottom|gravel falls, careful mining up|"
     "four gravel, four sand and a dye for concrete|dig gravel with a shovel",
  br="got a stack of flint from one gravel pile|i have like ten stacks of gravel|made a gravel path to my house",
  brr="nice|lot of flint",
  brf="arrows for days|i'm set",
  cp="gravel fell on my head again|nearly suffocated in gravel|gravel ruined my tunnel",
  cps="gravel is evil",
  cpm="i dug one block and the ceiling came down|it just kept falling",
  cpx="torch trick, place a torch under it|dig from the side|watch the ceiling|jump back when it falls",
  st=("bad", "so i was digging up in a cave|story: i mined up into the ceiling|i was digging upward in a cave"
              ">> gravel just came pouring down|a whole column of gravel fell on me"
              ">> suffocated before i could dig out|died in a gravel pile|respawned and it was still falling lol"),
  dg=("gravel is the most annoying block|nothing is more annoying than gravel",
      "falls on you|true", "sand is worse|it gives flint", "gravel haters", "fair"),
  db=("gravel should not fall|falling gravel is the worst mechanic|gravel physics need to go",
      "so annoying|please", "it's part of the game|sand falls too|it's a danger, that's fun",
      "more people die to gravel than creepers|it's dirt with a grudge|no other ore falls on your head",
      "remove it", "it's fine|just dig smart"))

S("sand", "sand", "building biomes", "building biomes crafting",
  m="dug up a stack of sand|got sand from the beach|sand for glass|the desert is free sand",
  mr="beach day|sandy|glass time",
  mf="so much sand|need more glass",
  q=["what is sand good for? > glass, sandstone, tnt, concrete / glass mostly",
     "why does sand fall? > no support under it, like gravel / gravity, careful digging under it",
     "what grows on sand? > cactus and sugar cane / cactus, sugar cane next to water"],
  qr="ok|nice",
  lk="sand is the most useful block for building|i love beach sand|deserts are so peaceful|"
     "builder: sand makes the best glass|builder: sand and sandstone, beach house vibes",
  lkr="beach vibes|sand is useful|deserts are hot",
  lkf="glass forever|beach life",
  tp="tip: sand falls, don't dig under it|smelt sand for glass|four sand make sandstone|"
     "cactus only grows on sand|sugar cane grows on sand next to water|tnt is sand and gunpowder",
  br="i have twenty stacks of sand|dug out a whole beach for glass|my glass dome took all the sand",
  brr="lots of sand|beach is gone lol",
  brf="the beach is no more|glass for days",
  cp="sand fell on me again|dug under sand and nearly died|sand buried me in the desert",
  cps="sand is sneaky",
  cpm="dug one block and the whole thing fell|i couldn't breathe",
  cpx="dig from the side|carry a torch for falling sand|watch for sand when mining up|use a shovel",
  st=("good", "so i was digging sand in the desert|story: i was mining sand in a desert|i was getting sand for glass"
               ">> hit a sandstone roof under the dunes|found a desert well, and under it"
               ">> it was a whole temple buried there|found suspicious sand and brushed out a sherd|got loot and a pottery sherd"),
  dg=("sand is more useful than gravel|sand beats gravel",
      "glass|true", "gravel gives flint|both fall, both bad", "sand gang", "fair"),
  db=("sand is the most useful block in the game|nothing beats sand|sand is more useful than iron",
      "glass, sandstone, tnt|true", "iron though|cobble is more useful|sand falls on you",
      "every window, every beacon needs glass|tnt, concrete, sandstone, all sand|you can't build without it",
      "sand gang", "iron though|nope"))

S("clay", "clay", "building crafting", "building crafting decor",
  m="found a clay patch in the river|dug up a stack of clay|got clay from a lush cave|smelted clay into bricks",
  mr="nice find|clay is rare|bricks incoming",
  mf="lucky|so rare",
  q=["where do you find clay? > rivers, swamps, lush caves / under shallow water",
     "how much clay from a block? > four clay balls / four",
     "what do you make with clay? > bricks and terracotta / smelt it into terracotta or bricks"],
  qr="ok|nice",
  lk="clay is the best find in a river|i love finding clay|clay patches are like treasure|"
     "builder: clay means bricks, bricks means cozy|builder: terracotta from clay is so versatile",
  lkr="treasure|so rare|it's grey mush",
  lkf="treasure indeed|grey mush with potential",
  tp="tip: clay is grey under river water|a clay block gives four clay balls|"
     "smelt a clay block for terracotta|lush caves have clay pools|"
     "mud over pointed dripstone dries into clay|shovel it, it's fast",
  br="found a huge clay deposit|got eight stacks of clay|my brick house is all from one clay lake",
  brr="jackpot|rich",
  brf="best day ever|bricks for life",
  cp="i can never find clay|clay is so rare|searched every river, no clay",
  cps="clay is rare",
  cpm="i need it for bricks|every river is just sand",
  cpx="try a swamp|lush caves|trade with a mason|make clay with mud and dripstone",
  dg=("clay is the rarest useful block|nothing is harder to find than clay",
      "so rare|true", "rivers have plenty|lush caves are full", "clay hunters", "fair"),
  db=("making clay from mud is better than finding it|the dripstone clay trick beats looking|clay farms beat rivers",
      "infinite|true", "rivers are faster|too slow|just find a swamp",
      "dirt is everywhere, clay isn't|one setup and it's forever|no diving, no searching",
      "clay farm gang", "still slow|rivers are fine"))

S("ice", "ice", "biomes building", "biomes exploring building",
  m="got some packed ice from the ice spikes|made a stretch of ice road|ice everywhere up north|"
    "my boat flies on ice",
  mr="cold|slippery|boats love ice",
  mf="so fast|cold but worth it",
  q=["how do you make packed ice? > nine ice / nine ice blocks",
     "why do boats go fast on ice? > ice is slippery, boats slide / no friction, boats fly",
     "does ice melt? > near light, normal ice melts / yeah, near torches, packed ice doesn't"],
  qr="ok|cool",
  lk="ice roads are the best way to travel|ice spikes are so pretty|packed ice looks great in builds|"
     "builder: blue ice and snow, perfect winter|builder: packed ice windows look cool",
  lkr="so fast|cold vibes|ice spikes are cool",
  lkf="speed is life|winter forever",
  tp="tip: nine ice make packed ice, nine packed make blue ice|boats on blue ice are crazy fast|"
     "normal ice melts near torches, packed doesn't|break ice with silk touch or it turns to water|"
     "ice spikes biome has tons of packed ice|frost walker freezes water as you walk",
  br="built an ice road to the next village|my boat road is blue ice|got four stacks of packed ice",
  brr="speed|nice",
  brf="takes seconds now|the commute is easy",
  cp="my ice melted|torches melted my ice floor|broke ice and flooded my house",
  cps="oops",
  cpm="it turned into water|everything's wet now",
  cpx="use packed ice|silk touch|keep torches away|use lanterns far off",
  st=("funny", "so i built a little ice rink|story: i made an ice floor in my house|i had an ice floor"
                ">> put torches around it to look nice|lit it up for the view"
                ">> next morning it was a pool|it all melted, i had a swimming pool|now it's a pond lol"),
  dg=("ice roads are the best transport|nothing beats boats on ice",
      "so fast|true", "rails are better|horses are fine", "ice gang", "fair"),
  db=("blue ice is too expensive to use|blue ice roads are a waste|nobody needs blue ice",
      "nine nine|true", "it's so fast|worth it|packed ice is enough",
      "eighty one ice per block|packed ice is fast enough|you'll never get enough for a long road",
      "packed is enough", "still worth it|speed is everything"))

S("snow", "snow", "biomes weather building", "biomes weather building",
  m="dug up a stack of snowballs|made some snow blocks|built a snow golem|it's snowing up north",
  mr="cold|snowy|cozy",
  mf="it leaves a trail|cute",
  q=["how do you make a snow golem? > two snow blocks and a carved pumpkin / snow, snow, pumpkin on top",
     "how do you get snowballs? > dig snow with a shovel / shovel on snow",
     "what's powder snow? > snow you sink in, you freeze / snow that freezes you, leather boots let you walk on it"],
  qr="ok|cute",
  lk="snowy biomes are the coziest|snow on roofs looks perfect|snow golems are adorable|"
     "builder: snow and spruce, winter cabin|builder: snow layers make a build look real",
  lkr="cozy|cold|snow golems are cute",
  lkf="cozy cold|perfect winter",
  tp="tip: leather boots let you walk on powder snow|snow golems throw snowballs at mobs|"
     "snowballs hurt blazes|four snowballs make a snow block|"
     "powder snow freezes you, get out fast|strays in snow shoot slowness arrows",
  br="my cabin is in a snowy biome, it's perfect|built a snow fort|got a snow golem guarding my door",
  brr="cozy|cute",
  brf="it keeps the mobs off|winter forever",
  cp="fell in powder snow and froze|powder snow got me again|sank into powder snow on the mountain",
  cps="cold",
  cpm="i couldn't climb out|the freezing hurt",
  cpx="wear leather boots|bring a bucket for the powder snow|climb out quick|watch your step",
  cpn="",
  st=("bad", "so i was walking on a snowy mountain|story: i was crossing snowy slopes|i was climbing a snowy hill"
              ">> stepped on what looked like snow and sank|fell into powder snow"
              ">> froze before i could get out|died from the cold|lost my stuff in the snow"),
  dg=("snow biomes are the best to live in|snowy places are the coziest",
      "cozy|true", "too cold|nothing grows", "snow gang", "fair"),
  db=("snow golems are useless|snow golems do nothing|nobody needs a snow golem",
      "weak|true", "they distract mobs|they're cute|they make snow",
      "a snowball does no damage|they melt in the rain|they die to the first creeper",
      "useless golem", "still cute|they're good guards"))

S("moss", "moss", "plants caves decor", "plants caves decor",
  m="found a lush cave full of moss|spread some moss with bone meal|made moss carpet|got moss from a wandering trader",
  mr="green|mossy|so lush",
  mf="it's so green|so pretty",
  q=["how do you spread moss? > bone meal on moss / bone meal it, it creeps onto stone",
     "how do you make mossy cobble? > cobble plus moss or a vine / craft it with moss",
     "where's moss? > lush caves / lush caves, under azalea trees"],
  qr="ok|nice",
  lk="moss makes everything look old|moss carpets are so cozy|lush caves are the prettiest|"
     "builder: mossy stone walls, ruined castle vibes|builder: moss carpet on a roof looks so good",
  lkr="old vibes|cozy|lush caves are nice",
  lkf="old is good|cozy green",
  tp="tip: bone meal moss and it spreads across stone|moss carpet is two moss blocks for three|"
     "a hoe breaks moss fastest|moss plus cobble makes mossy cobble|"
     "lush caves are under azalea trees|moss is good compost",
  br="my garden is all moss and azaleas|made a mossy ruin by my house|my roof has moss carpet",
  brr="old|pretty",
  brf="looks ancient|so natural",
  cp="bone meal spread moss everywhere|moss took over my whole floor|i can't stop the moss",
  cps="lol",
  cpm="i hit it once and it went everywhere|it ate my stone path",
  cpx="mine it back|use a hoe|bone meal less|keep it on a different block",
  dg=("moss makes every build better|a bit of moss fixes any build",
      "so true|age it up", "it looks messy|clean builds are better", "moss gang", "fair"),
  db=("moss is the best block they added|moss beats every new block|moss changed building",
      "so true|honestly", "it's just green dirt|deepslate was better|meh",
      "mossy anything looks good|aging builds was so hard before|lush caves made caves pretty",
      "moss gang", "green dirt|meh"))

S("glowstone", "glowstone", "building decor", "building decor crafting", gate="nether",
  m="mined a ton of glowstone in the nether|got four stacks of glowstone dust|my house is lit with glowstone|"
    "crafted glowstone blocks",
  mr="bright|nether light|fancy",
  mf="no more torches|so bright",
  q=["where do you get glowstone? > hanging from the nether ceiling / the nether, up on the ceiling",
     "how do you make a glowstone block? > four dust / four glowstone dust",
     "what's glowstone dust for? > stronger potions, spectral arrows / potions and redstone lamps"],
  qr="ok|neat",
  lk="glowstone is the best light|glowstone under carpet is a classic|glowstone looks warm and yellow|"
     "builder: hidden glowstone under the floor, perfect|builder: glowstone in a chandelier, so fancy",
  lkr="bright|warm|hidden lights are nice",
  lkf="warm light|best light",
  tp="tip: glowstone hangs from the nether ceiling|fortune gives more dust|"
     "it's fragile, it breaks like glass|hide it under carpet for invisible light|"
     "four dust make a block|glowstone makes potions stronger",
  br="my whole house is lit by hidden glowstone|got a double chest of glowstone|made a glowstone chandelier",
  brr="bright|fancy",
  brf="took a few trips|no torches anywhere",
  cp="glowstone is always over lava|getting glowstone is so dangerous|i fell trying to reach glowstone",
  cps="classic nether",
  cpm="it's always up high over lava|ghasts everywhere",
  cpx="bridge to it|bring fire resistance|use blocks to climb|pick the low ones",
  dg=("glowstone is better than torches|glowstone beats torches",
      "no clutter|true", "torches are cheaper|lanterns look better", "glowstone gang", "fair"),
  db=("glowstone is the best light block|no light beats glowstone|glowstone over everything",
      "so bright|true", "sea lanterns are better|lanterns look nicer|torches are cheaper",
      "light fifteen and it hides under carpet|full block of light|sea lanterns need monuments",
      "glowstone gang", "lanterns though|still prefer torches"))

S("sea_lantern", "sea lanterns", "building ocean decor", "ocean building decor",
  m="crafted some sea lanterns|lit my underwater base with sea lanterns|got sea lanterns from a monument|"
    "sea lanterns look so cool",
  mr="fancy|so pretty|they glow so nice",
  mf="they glow blue|perfect underwater",
  q=["how do you make a sea lantern? > four prismarine shards and five crystals / shards and crystals",
     "do sea lanterns work underwater? > yeah, perfectly / yep, that's what they're for",
     "how do you pick up a sea lantern? > silk touch, or it drops crystals / silk touch"],
  qr="ok|nice",
  lk="sea lanterns are the prettiest light|the glow of a sea lantern is perfect|sea lanterns underwater look magical|"
     "builder: sea lanterns in quartz, so clean|builder: sea lanterns under glass floors",
  lkr="pretty|clean light|fancy",
  lkf="best light|so clean",
  tp="tip: four shards and five crystals make one|silk touch or it breaks into crystals|"
     "they work underwater|monuments have tons in them|"
     "they look great behind glass|they give full light",
  br="my underwater base glows with sea lanterns|i have a stack of sea lanterns|built a sea lantern lighthouse",
  brr="fancy|glowing",
  brf="took monument trips|looks amazing at night",
  cp="broke my sea lantern and got crystals|forgot silk touch on sea lanterns|my sea lantern broke into crystals",
  cps="oops",
  cpm="it just shattered|now i need shards",
  cpx="silk touch next time|craft it back with shards|kill more guardians|use glowstone instead",
  dg=("sea lanterns are better than glowstone|sea lanterns beat glowstone",
      "prettier|true", "glowstone is easier|same light", "lantern gang", "fair"),
  db=("sea lanterns are the prettiest light in the game|no light is as pretty as a sea lantern|sea lanterns win",
      "so pretty|true", "lanterns are nicer|glowstone is warmer|they're too blue",
      "they shimmer|they work underwater|glowstone looks like cheese",
      "sea lantern gang", "still blue|glowstone though"))

S("lanterns", "lanterns", "building decor", "building decor town",
  m="crafted some lanterns|hung lanterns along my porch|made a soul lantern|lanterns on chains look great",
  mr="cozy|nice glow|lanterns are the best",
  mf="so cozy at night|perfect",
  q=["how do you make a lantern? > eight iron nuggets around a torch / iron nuggets and a torch",
     "can lanterns hang? > yeah, from ceilings or chains / yep, under any block",
     "what's a chain made of? > iron nuggets and an ingot / two nuggets and an ingot"],
  qr="ok|nice",
  lk="lanterns are the coziest light|hanging lanterns on chains, perfect|soul lanterns are so pretty|"
     "builder: lanterns on fence posts, the classic|builder: a lantern under every eave",
  lkr="cozy|pretty|soul lanterns are cool",
  lkf="cozy is everything|pretty light",
  tp="tip: eight nuggets and a torch make a lantern|hang them under blocks or chains|"
     "soul lanterns are dimmer and blue|lanterns don't break in water like torches|"
     "put them on fences for street lamps|smelt old iron tools for nuggets",
  br="my whole street has lanterns now|hung like twenty lanterns at my house|built lantern posts all through town",
  brr="cozy|pretty",
  brf="town looks great at night|so worth it",
  cp="lanterns need so much iron|i ran out of nuggets|lanterns are expensive",
  cps="iron is precious",
  cpm="eight nuggets each|my iron stash is gone",
  cpx="smelt old iron gear|build an iron farm|use torches outside|use fewer",
  dg=("lanterns are better than torches|lanterns beat torches",
      "so cozy|true", "torches are cheaper|torches are classic", "lantern gang", "fair"),
  db=("torches are fine, lanterns are a waste|lanterns aren't worth the iron|nobody needs lanterns",
      "iron is precious|true", "lanterns look way better|they're cozy|worth every nugget",
      "same light, way more iron|a torch does the job|nobody looks at your lights",
      "save the iron", "still prettier|lanterns forever"))

S("torches", "torches", "caves mining building", "caves mining night",
  m="crafted a stack of torches|used a whole stack of torches in one cave|lit up the whole hill with torches|"
    "torches on every wall now",
  mr="safety first|torch gang|can't have too many",
  mf="need more coal|cave is safe now",
  q=["how many torches from one coal? > four / one coal and a stick, four torches",
     "how far apart should torches be? > every ten blocks or so / close enough that it's never dark",
     "do mobs spawn near torches? > not where there's light / they need total darkness now"],
  qr="ok|got it",
  lk="torches are the best item in the game|a cave full of torches feels safe|torches everywhere is my style|"
     "builder: torches on fence posts look nice|miner: torches on the right side so i find my way back|"
     "miner: i light every cave i go through",
  lkr="safety|messy|torches are life",
  lkf="safety first|always",
  tp="tip: one coal makes four torches|charcoal works too, from logs|mobs only spawn in total darkness now|"
     "torches on the right going in, left coming out|carry a stack, always|"
     "torches break in water, careful",
  tpn="a:voice_miner",
  br="i lit up a whole cave system|my base has zero dark spots|i carry three stacks of torches always",
  brr="safe|prepared",
  brf="no creepers here|always ready",
  cp="ran out of torches deep in a cave|my torches keep getting washed away|i forgot torches again",
  cps="classic",
  cpm="had to feel my way out|it went dark so fast",
  cpx="carry more|make charcoal|bring a stack of coal|follow your old torches",
  st=("bad", "so i went caving with half a stack of torches|story: i went caving with like twelve torches|i went caving underprepared"
              ">> ran out halfway through|used my last torch in a huge cavern"
              ">> walked into the dark and got jumped|a creeper hissed in the dark|died in the dark, never found my stuff"),
  dg=("you can never have too many torches|more torches is always better",
      "safety|true", "it looks messy|lanterns are nicer", "torch gang", "fair"),
  db=("torch spam ruins builds|too many torches is ugly|torch spammers have no taste",
      "messy|true", "safety first|creepers don't care|light is life",
      "hide the light under carpet|lanterns, glowstone, anything else|a torch every block looks like a fire",
      "taste matters", "still torches|safety over looks"))

S("campfires", "campfires", "food building", "food cooking night",
  m="built a campfire by my house|cooked some fish on the campfire|lit a campfire for the night|"
    "put a hay bale under my campfire for smoke",
  mr="cozy|campfire vibes|warm",
  mf="so cozy|i can see it from the hill",
  q=["how do you make a campfire? > sticks, coal and logs / three sticks, coal, three logs",
     "can you cook on a campfire? > yeah, four things at once, no fuel / yep, slow but free",
     "how do you put out a campfire? > shovel it or splash water / right click with a shovel"],
  qr="ok|nice",
  lk="campfires are the coziest thing|cooking on a campfire feels right|sitting by a campfire at night is the best|"
     "builder: a campfire in a stone pit, perfect|builder: soul campfires look so cool",
  lkr="cozy|campfire vibes|warm",
  lkf="cozy nights|so warm",
  tp="tip: a campfire cooks four things at once with no fuel|put a hay bale under it for tall smoke|"
     "a campfire under a beehive calms the bees|don't step on it, it burns|"
     "breaking it gives charcoal|a shovel puts it out",
  br="built a campfire circle at my house|my campfire smoke is visible from miles|cooked a stack of beef on my campfire",
  brr="cozy|nice",
  brf="the best spot at night|free cooking",
  cp="walked into my own campfire|my campfire set my house on fire|stepped on my campfire again",
  cps="ouch",
  cpm="i forgot it was there|it burned a hole in my floor",
  cpx="put a slab around it|move it outside|keep it outside|build it in stone",
  st=("funny", "so i was cooking fish on a campfire|story: i had four fish on the campfire|i was cooking on my campfire"
                ">> a cat walked up and sat next to me|a cat came over and stared at the fish"
                ">> now the cat lives with me|i gave it a fish and now it's mine|i have a cat now, the fish was the price"),
  dg=("campfires beat furnaces for cooking|campfires are better than furnaces",
      "no fuel|true", "too slow|furnaces are faster", "campfire gang", "fair"),
  db=("every house needs a campfire|a house without a campfire isn't a home|campfires make a home",
      "so cozy|true", "they burn things|a furnace is enough|they're dangerous",
      "free cooking, smoke signals, calm bees|it's the heart of a camp|nothing beats the glow",
      "campfire gang", "fire hazard|furnace is enough"))

# ======================================================================= Tools: pickaxes by tier
S("wooden_pickaxe", "a wooden pickaxe", "tools mining", "tools mining crafting",
  m="made my first wooden pickaxe|wooden pick, here we go|still on a wooden pickaxe lol|crafted a wood pick to get some stone",
  mr="the humble start|everyone starts there|stone pick next",
  mf="gotta start somewhere|stone soon",
  q=["what can a wooden pickaxe mine? > stone and coal, that's it / just stone and coal, get a stone pick fast",
     "how long does a wooden pickaxe last? > like sixty blocks / not long, sixty uses or so",
     "should i make a wooden pickaxe first? > yeah, just for some cobble / yes, then stone right away"],
  qr="ok|got it",
  lk="there's something nice about a fresh wooden pickaxe|the wooden pick is the start of everything|"
     "i love the first pickaxe of a new world|speedrunner: wood pick for three cobble, then gone|speedrunner: wood pick lasts ten seconds in my hands",
  lkr="nostalgic|it's useless though|the start of it all",
  lkf="nostalgia|it's the first step",
  tp="tip: get three cobble with the wood pick and upgrade right away|don't waste a wood pick on dirt|"
     "wooden tools burn as fuel when they're done|three planks and two sticks|"
     "make a crafting table first or you can't make it|keep it in your hotbar till you have stone",
  br="i made it through day one with just a wooden pick|i mined iron with a wooden pick, almost|i'm doing a wood tools challenge",
  brr="brave|lol|why",
  brf="for fun|it's a challenge",
  cp="my wooden pick broke right before the ore|wooden pick breaks so fast|i forgot to upgrade from wood",
  cps="classic",
  cpm="it lasted like a minute|i was in a cave with nothing",
  cpx="get stone right away|carry planks for a spare|make a stone pick asap|grab three cobble first thing",
  dg=("you should skip the wooden pickaxe|the wooden pick is a waste of planks",
      "punch stone? no wait|true", "you can't mine stone without it|you need it for cobble", "skip it", "fine, three cobble"),
  db=("the wooden pickaxe is the most important tool|without the wood pick there's no game|wood pick is the real mvp",
      "everything starts there|facts", "stone pick does more|it lasts seconds|it's a stepping stone",
      "no wood pick, no cobble, no stone pick, no anything|every diamond started with a wood pick|respect the first tool",
      "respect the wood", "it's a stepping stone|stone is the real start"))

S("stone_pickaxe", "a stone pickaxe", "tools mining", "tools mining ore",
  m="crafted a stone pickaxe|stone pick, ready for iron|made a couple spare stone picks|stone pick in hand",
  mr="iron time|the workhorse|solid start",
  mf="iron next|it's enough for now",
  q=["can a stone pickaxe mine iron? > yeah, iron, copper and lapis / yep, just not gold or diamonds",
     "can it mine diamonds? > no, you need iron / nope, iron pick at least",
     "how long does a stone pick last? > about a hundred thirty blocks / a while, a couple stacks"],
  qr="ok|good to know",
  lk="the stone pick is the real workhorse|i love making five stone picks at once|stone picks are cheap and good enough|"
     "speedrunner: stone pick, find iron, done|speedrunner: stone tools all the way to iron",
  lkr="cheap|good enough|iron is better",
  lkf="cheap is good|why waste iron",
  tp="tip: make a few stone picks, they're cheap|save your iron for armor and buckets|"
     "stone picks mine iron and copper fine|cobbled deepslate works for stone picks too|"
     "use stone picks for tunnels, iron for ores|gold and diamonds need an iron pick",
  br="i mined like four stacks of stone with one stone pick|i still use stone picks for tunnels|got iron on day one with a stone pick",
  brr="efficient|nice",
  brf="save the iron|stone is plenty",
  cp="mined diamonds with a stone pick and got nothing|broke a gold ore with a stone pick|mined redstone with a stone pick, nothing",
  cps="painful",
  cpm="it just broke the ore|nothing dropped",
  cpx="carry an iron pick for ores|check the ore first|mark it and come back|never mine diamonds with stone",
  dg=("stone picks are better for tunnels than iron|save iron, use stone for tunneling",
      "cheap|true", "iron is faster|why not iron", "stone gang", "fair"),
  db=("you should stay on stone picks as long as possible|iron picks are a waste early|stone picks till diamonds",
      "iron is for armor|true", "iron is way faster|you need iron for gold|that's slow",
      "iron goes to armor and buckets first|stone mines everything you need early|cheap picks, more iron saved",
      "save the iron", "still faster with iron|nah"))

S("iron_pickaxe", "an iron pickaxe", "tools mining", "tools mining diamonds",
  m="finally made an iron pickaxe|iron pick, shiny and new|crafted a spare iron pick|iron pick, now we're talking",
  mr="diamond time|now you're mining|nice",
  mf="diamonds here i come|feels good",
  q=["what can an iron pickaxe mine? > everything but obsidian and debris / diamonds, gold, redstone, all of it",
     "how long does an iron pick last? > two fifty blocks / about two hundred fifty uses",
     "should i enchant my iron pick? > nah, save it for diamond / maybe efficiency, it's cheap"],
  qr="ok|nice",
  lk="the iron pick is the best feeling|iron pick day is my favourite day|an iron pickaxe means real mining|"
     "miner: iron pick and a stack of torches, perfect|miner: i've done whole worlds on iron picks",
  lkr="the real start|diamond is better|it's solid",
  lkf="solid is enough|iron is honest",
  tp="tip: iron picks mine diamonds, no need to wait|save diamonds for the pick, not the sword|"
     "carry a spare iron pick deep down|repair an iron pick at an anvil with ingots|"
     "an iron pick lasts two fifty blocks, watch it|use stone picks for tunnels and save the iron one",
  tpn="a:voice_miner",
  br="i got diamonds with my first iron pick|my iron pick has efficiency three|i went through five iron picks this week",
  brr="nice|efficient",
  brf="the iron farm helps|iron for days",
  cp="my iron pick broke at the diamonds|my iron pick died mid vein|iron picks die so fast",
  cps="pain",
  cpm="right at the vein|i had to walk back up",
  cpx="carry a spare|keep an eye on it|get mending|build an iron farm",
  st=("good", "so i was mining with my first iron pick|story: i made my first iron pick and went down|first trip with an iron pick"
               ">> mined a whole vein of redstone and gold|got gold, redstone, lapis on the first trip"
               ">> then saw diamonds right at the end|and then four diamonds right before it broke|it broke on the last diamond, perfect timing"),
  dg=("the iron pick is the most important upgrade|iron pick beats diamond pick in importance",
      "it unlocks everything|true", "diamond is bigger|stone to iron is small", "iron gang", "fair"),
  db=("you don't need a diamond pickaxe|iron picks are enough forever|diamond picks are a waste of diamonds",
      "iron is enough|true", "obsidian though|diamond lasts longer|you need it for debris",
      "iron mines diamonds, gold, everything|diamonds go to armor first|bucket portals, no diamond pick needed",
      "save the diamonds", "still need diamond|obsidian though"))

S("gold_pickaxe", "a gold pickaxe", "tools mining", "tools mining jokes",
  m="made a gold pickaxe for fun|my gold pick is so fast|crafted a gold pick, it's so fast|gold pick is shiny at least",
  mr="lol why|so fast though|shiny",
  mf="it was fun while it lasted|thirty uses lol",
  q=["is a gold pickaxe good? > fast but breaks super quick / fastest, but it lasts thirty blocks",
     "what can a gold pick mine? > only stone and coal, like wood / same as wood, it's weak",
     "why would anyone use a gold pick? > it enchants really well / for fast stone, or good enchants"],
  qr="huh|lol ok",
  lk="gold picks are so fast, it's addictive|i love how fast gold mines|gold pick on stone feels like cheating|"
     "speedrunner: gold pick goes brrr|speedrunner: fastest pick for a few seconds",
  lkr="it breaks instantly|so fast though|lol",
  lkf="speed is worth it|for a few seconds",
  tp="tip: gold picks mine faster than diamond but break fast|gold can't mine iron, it's wood tier|"
     "gold enchants better, nice for books|don't waste gold on tools, make carrots|"
     "a gold pick lasts about thirty blocks|piglins love gold items",
  br="i got efficiency five on a gold pick|my gold pick mines faster than your diamond one|cleared a whole room with gold picks",
  brr="lol|fast|silly",
  brf="speed is king|for like a minute",
  cp="my gold pick broke on the first ore|my gold pick died in a minute|gold pick couldn't mine iron",
  cps="lol rip",
  cpm="it just broke the ore|thirty uses, gone",
  cpx="use gold for carrots|make gold armor for the nether|trade it|don't do it again",
  dg=("gold tools are pointless|gold picks are a waste",
      "breaks instantly|true", "they're fast|good enchants", "gold is for carrots", "fair"),
  db=("the gold pickaxe is secretly the best pick|gold picks are underrated|gold pick beats diamond on stone",
      "so fast|honestly", "it breaks instantly|it can't mine iron|a joke tool",
      "fastest mining speed in the game|enchants better than anything|for stone it's unmatched",
      "speed gang", "still a joke|thirty uses lol"))

S("diamond_pickaxe", "a diamond pickaxe", "tools mining diamonds", "tools mining diamonds",
  m="crafted my first diamond pickaxe|my diamond pick has unbreaking|diamond pick, finally|"
    "got efficiency on my diamond pick",
  mr="big moves|the dream|now you're rich",
  mf="feels so good|three diamonds well spent",
  q=["how many diamonds for a pickaxe? > three / three diamonds and two sticks",
     "what can a diamond pick mine that iron can't? > obsidian and ancient debris / obsidian mostly",
     "what should i enchant it with? > efficiency, unbreaking, fortune / efficiency and fortune"],
  qr="ok|nice",
  lk="a diamond pickaxe is the best feeling in the game|nothing beats a fresh diamond pick|my diamond pick is my baby|"
     "miner: diamond pick with fortune three, perfect|miner: efficiency five diamond pick, insta mine",
  lkr="the dream|it's just a tool|treat it well",
  lkf="treat it like gold|it's family",
  tp="tip: three diamonds and two sticks|put fortune on it for more diamonds|"
     "unbreaking makes it last way longer|mending keeps it alive forever|"
     "repair it with diamonds at an anvil|silk touch on a second pick for blocks",
  br="i have two diamond picks, fortune and silk touch|my diamond pick has efficiency five|"
     "got a diamond pick on day two",
  brn="a:has_diamonds",
  brr="rich|the dream|nice",
  brf="worth every diamond|prepared",
  cp="lost my diamond pick in lava|my diamond pick broke, no mending|dropped my diamond pick in a ravine",
  cps="nooo",
  cpm="three diamonds gone|i had fortune on it",
  cpx="get mending next time|keep an eye on durability|carry a spare|don't mine over lava",
  st=("bad", "so i had this perfect diamond pick|story: i had a fortune three diamond pick|my best pick ever, fortune three"
              ">> mined a block over lava without looking|was mining by a lava lake and slipped"
              ">> it fell in and burned|watched it burn in the lava|gone, fortune and all"),
  dg=("fortune is better than silk touch on a pick|fortune beats silk touch",
      "more diamonds|true", "silk touch is more useful|both on two picks", "fortune gang", "fair"),
  db=("you should never use your diamond pick on stone|diamond picks are for ores only|stone pick for tunnels, diamond for diamonds",
      "save durability|true", "that's slow|mending fixes it|i'm not switching tools",
      "every stone block wears it down|why waste diamond on stone|swap picks, it takes a second",
      "save durability", "mending|too much switching"))

S("netherite_pickaxe", "a netherite pickaxe", "tools mining gear", "tools mining gear", gate="nether",
  m="upgraded my pick to netherite|netherite pick finally|my netherite pick floats in lava|smithed a netherite pickaxe",
  mr="end game|big flex|nice",
  mf="it's so dark and shiny|the end of picks",
  mn="a:knows_netherite",
  q=["how do you make a netherite pickaxe? > upgrade a diamond one at a smithing table / diamond pick, ingot and a template",
     "is netherite better than diamond? > a bit faster and lasts longer, and doesn't burn / yeah, and it floats in lava",
     "where do you get the template? > bastions, in the nether / bastion chests"],
  qr="ok|nice",
  lk="netherite picks are perfect|my netherite pick is my best item|netherite looks so good|"
     "miner: netherite pick, haste two, instant everything|miner: a netherite pick is a forever pick",
  lkr="end game|overkill|so good",
  lkf="overkill is fine|forever pick",
  tp="tip: upgrade your enchanted diamond pick, enchants stay|it floats in lava, you won't lose it|"
     "copy the template with seven diamonds and netherrack|you need a template, check bastions|"
     "four scrap and four gold make an ingot|it lasts way longer than diamond",
  tpn="a:knows_netherite",
  br="my netherite pick has every enchant|got netherite tools now|my pickaxe survived a lava fall",
  brn="a:knows_netherite",
  brr="end game|flex",
  brf="took so much debris|worth it",
  cp="ancient debris takes forever to find|i need more debris for the pick|debris is the rarest thing ever",
  cpn="a:knows_netherite",
  cps="debris is rare",
  cpm="i found one debris in an hour|my beds blew up everything but debris",
  cpx="bed mining at y 15|tnt tunnels|try the bastion for scrap|keep looking low",
  dg=("netherite picks are worth it|you need a netherite pick",
      "it lasts forever|true", "diamond is enough|too expensive", "netherite gang", "fair"),
  db=("netherite tools are overkill|netherite picks aren't worth the debris|diamond picks do the same job",
      "true|kinda", "it floats in lava|it's faster|it lasts longer",
      "mending diamond lasts forever too|the speed is barely different|debris for armor first",
      "diamond is enough", "lava though|still worth it"))

# ======================================================================= Tools: the rest
S("axes", "axes", "tools weapons trees", "tools trees fighting",
  m="made a new iron axe|stripped a stack of logs with my axe|my axe is my favourite tool|chopped a whole forest with one axe",
  mr="the woodcutter|nice|axe gang",
  mf="it's so satisfying|axe life",
  q=["what else can an axe do? > strip logs, scrape copper, break shields / strip logs and fight",
     "is an axe better than a sword? > hits harder but slower / harder hit, and it breaks shields",
     "how do you strip a log? > right click it with an axe / use the axe on it"],
  qr="huh|ok",
  lk="axes are the most useful tool|stripped logs are my favourite thing to make|an axe crit feels amazing|"
     "pvp_sweat: axe crits, nothing better|pvp_sweat: axe disables shields, game over",
  lkr="axe gang|sword is better|stripping is satisfying",
  lkf="axe gang forever|satisfying is right",
  tp="tip: an axe hit knocks a shield down for a few seconds|axes strip logs for nice beams|"
     "axes scrape wax and green off copper|axes chop chests and tables fast too|"
     "jump hits with an axe do huge crits|efficiency axes make tree farms fly",
  br="i chopped eight stacks of logs today|my axe has sharpness and efficiency|broke a pillager's shield with one hit",
  brr="nice|strong",
  brf="axe life|it hits hard",
  cp="i stripped a log by accident again|my axe keeps stripping logs i want to keep|right clicked my logs and stripped them",
  cps="classic",
  cpm="i wanted bark|now they're all stripped",
  cpx="hold something else when you place|craft new ones|use stripped wood anyway|swap to a block first",
  dg=("an axe is a better weapon than a sword|axe beats sword in a fight",
      "more damage|true", "sword is faster|sweep attack", "axe gang", "fair"),
  db=("the axe is the best weapon in the game|axes beat swords, end of story|swords are pointless with axes around",
      "more damage|true", "swords swing faster|sweep hits groups|axes are slow",
      "one crit and it's over|it breaks shields|you need one for wood anyway",
      "axe gang", "sword gang|too slow"))

S("shovels", "shovels", "tools mining", "tools mining building",
  m="made a new shovel|dug out a whole beach with my shovel|my shovel is my best tool|made a path with my shovel",
  mr="dig dig|nice|the underrated tool",
  mf="sand everywhere|dig life",
  q=["what can a shovel do? > dig dirt, sand, gravel fast, and make paths / dig and make paths",
     "how do you make a dirt path? > right click grass with a shovel / shovel on grass",
     "can a shovel put out a campfire? > yeah, right click it / yep"],
  qr="ok|neat",
  lk="shovels are underrated|digging sand with an efficiency shovel is so satisfying|i love making paths with a shovel|"
     "builder: shovel paths make a village look real|builder: the shovel is my favourite building tool",
  lkr="underrated|it's a shovel|paths are nice",
  lkf="respect the shovel|satisfying",
  tp="tip: efficiency five shovel breaks sand instantly|shovel on grass makes a path|"
     "dig snow with a shovel for snowballs|shovels are best for clay, gravel and soul sand|"
     "one material and two sticks|a shovel puts out a campfire",
  br="dug out a whole desert with one shovel|my shovel has efficiency five|made a path to every house in town",
  brr="nice|wow",
  brf="instant dig|so satisfying",
  cp="my shovel always breaks first|i forget my shovel every time|dug sand with my hand, so slow",
  cps="classic",
  cpm="i never make a spare|it takes forever by hand",
  cpx="carry two|make a cheap stone one|mend it|keep one in the hotbar",
  dg=("the shovel is the most underrated tool|shovels deserve more respect",
      "so true|true", "it's just a shovel|axes are more underrated", "shovel gang", "fair"),
  db=("you don't need a shovel|shovels are pointless, just use your hand|a pick does everything a shovel does",
      "lol maybe|eh", "sand by hand is so slow|paths though|no way",
      "dirt breaks fast anyway|it's just one more tool to carry|pick works on most stuff",
      "no shovel", "shovel gang|paths though"))

S("hoes", "hoes", "tools farming", "tools farming plants",
  m="made a hoe for my farm|tilled a whole field today|got the farm all tilled|made a diamond hoe, don't judge",
  mr="farm time|lol|a field!",
  mf="crops soon|farmer life",
  q=["what's a hoe good for? > tilling dirt for crops / farmland, and breaking leaves fast",
     "what does a hoe break fast? > leaves, hay, moss, sculk / leaves and hay bales",
     "does the hoe tier matter? > not for tilling / a wood one tills the same"],
  qr="huh|ok",
  lk="tilling a fresh field is so relaxing|hoes are underrated|the hoe is the farmer's best friend|"
     "homebody: tilling my garden every morning|homebody: a hoe and some seeds, perfect day",
  lkr="relaxing|it's a hoe|farming is nice",
  lkf="relaxing is right|farmer life",
  tp="tip: any hoe tills the same, don't waste diamonds|hoes break leaves and moss super fast|"
     "keep farmland near water, four blocks|hoes break hay bales fast|"
     "hoes break sculk fast too|two material and two sticks",
  br="tilled a field of like two hundred blocks|my farm is all hoed in neat rows|i have a netherite hoe",
  brr="farmer|lol why|neat",
  brf="farm life|because i can",
  cp="my hoe broke halfway through the field|my farmland keeps drying out|half my field went back to dirt",
  cps="farm problems",
  cpm="half the field is done|so much work wasted",
  cpx="keep water close|make a spare|use stone, it's cheap|light the farm",
  dg=("diamond hoes are a waste|never make a diamond hoe",
      "stone works the same|true", "they last longer|it's for leaves too", "save diamonds", "fair"),
  db=("the hoe is the most useless tool|hoes are pointless|nobody needs a hoe",
      "lol yeah|kinda", "you can't farm without it|it breaks leaves fast|farmland though",
      "you till once and you're done|a stone hoe lasts forever|nobody fights with a hoe",
      "useless", "farms need it|leaves though"))

S("swords", "swords", "weapons fighting", "weapons fighting mobs",
  m="crafted a new iron sword|made a stone sword for backup|enchanted my sword with sharpness|swung my sword all night",
  mr="ready to fight|nice|sharp",
  mf="mobs beware|it's sharp",
  q=["what's the best sword enchant? > sharpness, and looting for drops / sharpness five",
     "sword or axe? > sword is faster, axe hits harder / sword for crowds, axe for big hits",
     "what's sweeping edge? > it makes the sweep hit harder / more damage to the mobs around"],
  qr="ok|nice",
  lk="a sharp sword is the best feeling|the sweep attack is so satisfying|swords are the classic weapon|"
     "pvp_sweat: sharpness five, no one stands a chance|pvp_sweat: sword crits all day",
  lkr="classic|axes hit harder|sweep is nice",
  lkf="classic for a reason|sweep wins",
  tp="tip: wait for the swing to charge, spam does nothing|jump hits are crits|"
     "looting on a sword gets more drops|swords cut cobwebs fast|"
     "sweep attack hits everything around|fire aspect cooks the meat as you kill",
  br="my sword has sharpness five and looting three|killed ten zombies with one sword swing combo|my sword has never broken",
  brr="strong|nice",
  brf="they don't stand a chance|mending helps",
  cp="i spam click and do no damage|my sword keeps missing|my hits do nothing",
  cps="rough",
  cpm="i'm swinging and nothing happens|zombies just walk through",
  cpx="wait for the charge|time your hits|carry a spare|jump hit",
  st=("good", "so i was surrounded by zombies at night|story: five zombies came at me at once|i got swarmed one night"
               ">> swung my sword in a circle|sweep attack hit all of them"
               ">> cleared all five without losing a heart|walked away with a stack of rotten flesh|felt like a hero"),
  dg=("the sword is better than the axe|sword beats axe",
      "faster|true", "axe hits harder|axe breaks shields", "sword gang", "fair"),
  db=("swords are pointless now that axes exist|axes made swords useless|nobody needs a sword",
      "axes hit harder|true", "sweep attack|swords are faster|looting works the same",
      "axe crits do more damage|axes break shields|one tool for wood and fights",
      "axe gang", "sword gang|sweep though"))

S("bow", "the bow", "weapons fighting", "weapons fighting mobs",
  m="crafted a new bow|my bow got power three|shot a skeleton from like forty blocks|got infinity on my bow",
  mr="sniper|nice|power three!",
  mf="pew pew|no more melee",
  q=["what's the best bow enchant? > power, and infinity or mending / power five",
     "can you have infinity and mending? > no, pick one / nope, they don't go together",
     "how do you make a bow? > three sticks and three string / sticks and string"],
  qr="ok|nice",
  lk="bows are the best weapon|landing a long shot is so satisfying|i love sniping skeletons|"
     "pvp_sweat: punch two bow, launch them off cliffs|pvp_sweat: full charge headshots",
  lkr="sniper|melee is better|long shots are great",
  lkf="snipers win|range is safety",
  tp="tip: charge the bow fully for real damage|power is the damage enchant|"
     "infinity only needs one arrow|flame sets them on fire|"
     "skeletons drop bows, sometimes enchanted|aim a bit up for long shots",
  br="sniped a creeper before it saw me|my bow has power five and infinity|shot a phantom out of the sky",
  brr="sniper|nice shot",
  brf="aim is everything|long range gang",
  cp="i keep missing everything|my arrows go everywhere|can't hit a thing with my bow",
  cps="rough",
  cpm="i was so far away|nothing hit",
  cpx="charge it fully|practice on cows|aim a bit higher|get closer",
  st=("good", "so a creeper was walking at my house|story: i saw a creeper heading for my door|a creeper was creeping up on my house"
               ">> i shot it from the roof|grabbed my bow and aimed"
               ">> one shot, gone before it got close|it died before it hissed|my house survived, bow wins"),
  dg=("bows are better than crossbows|bow beats crossbow",
      "faster|true", "crossbow hits harder|crossbows stay loaded", "bow gang", "fair"),
  db=("the bow is the best weapon in the game|nothing beats a good bow|bow over sword any day",
      "range wins|true", "melee is better|crossbows are better|bows are slow",
      "you hit them before they reach you|skeletons can't even fight back|creepers die before they hiss",
      "bow gang", "melee gang|too slow"))

S("crossbow", "the crossbow", "weapons fighting", "weapons fighting villagers",
  m="crafted a crossbow|got multishot on my crossbow|loaded my crossbow with fireworks|pillager dropped a crossbow",
  mr="nice|pew|crossbow gang",
  mf="boom|it's so strong",
  q=["how do you make a crossbow? > sticks, string, iron and a tripwire hook / three sticks, two string, iron, a hook",
     "can a crossbow shoot fireworks? > yeah, with stars they explode / yep, big damage",
     "what's the best crossbow enchant? > quick charge, then multishot or piercing / quick charge"],
  qr="ok|cool",
  lk="crossbows stay loaded, that's huge|multishot is so fun|firework crossbows are insane|"
     "pvp_sweat: a loaded crossbow is a surprise|pvp_sweat: quick charge three, unstoppable",
  lkr="strong|slow reload|fireworks are crazy",
  lkf="strong wins|quick charge fixes it",
  tp="tip: load it before a fight, it stays loaded|quick charge fixes the reload|"
     "fireworks with stars explode, big damage|piercing goes through mobs|"
     "multishot fires three arrows|pillagers drop crossbows sometimes",
  br="my crossbow has quick charge three|killed three mobs with one multishot|blew up a ravager with firework rockets",
  brr="strong|boom",
  brf="it's my main weapon|loaded and ready",
  cp="crossbow reload is so slow|i forgot to load my crossbow|reloading in a fight gets me killed",
  cps="slow",
  cpm="they got me while reloading|it takes forever",
  cpx="quick charge|load it before|carry two|use a bow up close",
  dg=("crossbows are better than bows|crossbow beats bow",
      "stays loaded|true", "bows are faster|bow has infinity", "crossbow gang", "fair"),
  db=("the crossbow is the best ranged weapon|crossbows beat bows easily|no bow can beat a crossbow",
      "stays loaded|true", "bows fire faster|no infinity|too slow",
      "fireworks do insane damage|piercing goes through crowds|it's loaded when you need it",
      "crossbow gang", "bow gang|too slow"))

S("trident", "the trident", "weapons ocean fighting", "weapons ocean mobs",
  m="finally got a trident from a drowned|threw my trident and it came back|got loyalty on my trident|riptided across a lake",
  mr="rare!|so cool|sea weapon",
  mf="so cool|i'm a sea god now",
  q=["how do you get a trident? > drowned drop them, rarely / kill drowned holding one",
     "what's riptide? > you launch yourself in water or rain / it throws you in water or rain",
     "what's channeling? > lightning on the target in a thunderstorm / summons lightning in storms"],
  qr="ok|cool",
  lk="tridents are the coolest weapon|riptide in the rain is the best|loyalty tridents feel like magic|"
     "pvp_sweat: impaling five, the ocean is mine|pvp_sweat: trident throws from range, unstoppable",
  lkr="so cool|rare though|riptide is fun",
  lkf="worth the hunt|riptide forever",
  tp="tip: only drowned drop tridents, look for ones holding them|loyalty brings it back|"
     "riptide works in rain too|channeling makes lightning in thunderstorms|"
     "it hits hard in melee too|a trident can't be crafted, guard it",
  br="i have a loyalty three trident|riptided across the whole ocean|got a trident on my first drowned",
  brr="lucky|cool",
  brf="it was the first one i fought|sea god",
  cp="drowned never drop tridents for me|killed a hundred drowned, no trident|still no trident",
  cps="noooo",
  cpm="i've killed fifty drowned|they all drop copper",
  cpx="keep hunting drowned|check under water at night|looting helps|look for ones holding it",
  st=("funny", "so it was a thunderstorm|story: there was a big storm|it was storming hard"
                ">> i had a channeling trident and saw a creeper|threw my channeling trident at a creeper"
                ">> lightning hit and now it's a charged creeper|oops, it got charged|i ran so fast lol"),
  dg=("the trident is the best weapon|tridents beat swords",
      "range and melee|true", "too rare|swords are easier", "trident gang", "fair"),
  db=("riptide is the best way to travel|riptide beats elytra in the rain|nothing is faster than riptide",
      "in the rain|true", "only in rain|elytra is better|boats are fine",
      "rain, water, fly anywhere|no fireworks needed|it's free flying",
      "riptide gang", "only in rain|elytra forever"))

S("mace", "the mace", "weapons fighting", "weapons fighting structures",
  m="made a mace from a heavy core|smashed a zombie with the mace|got a breeze rod for a mace|"
    "jumped off a cliff with my mace",
  mr="big hammer|smash!|the heavy one",
  mf="it's insane|so heavy",
  mn="a:knows_trial_chambers",
  q=["how do you make a mace? > heavy core and a breeze rod / a heavy core from the vault, and a breeze rod",
     "where's the heavy core? > ominous vaults in trial chambers / the ominous vault, rare drop",
     "how does the mace work? > more damage the further you fall / fall on them, the higher the harder"],
  qr="huh|whoa",
  lk="the mace is the most fun weapon|smash attacks feel amazing|falling onto a mob with a mace is the best|"
     "pvp_sweat: wind charge, jump, smash, gg|pvp_sweat: density five mace, they're gone",
  lkr="overpowered|so fun|it's busted",
  lkf="busted and fun|fun wins",
  tp="tip: fall further, hit harder|wind charges launch you up for a smash|"
     "density adds fall damage|breach goes through armor|"
     "a hit with the mace saves you from fall damage|wind burst bounces you back up",
  tpn="a:knows_trial_chambers",
  br="one shot a ravager with the mace|got density on my mace|i have the heavy core",
  brn="a:knows_trial_chambers",
  brr="busted|strong",
  brf="the vault was worth it|smash",
  cp="heavy cores are so rare|i've opened ten ominous vaults, no core|still no heavy core",
  cpn="a:knows_trial_chambers",
  cps="unlucky",
  cpm="nothing but junk from the vaults|so many keys",
  cpx="keep doing ominous trials|try another chamber|bring friends|you'll get it",
  dg=("the mace is overpowered|the mace is too strong",
      "busted|true", "it's hard to use|you need height", "nerf it", "fair"),
  db=("the mace is the best weapon in the game|nothing hits like a mace|mace beats every weapon",
      "one shot|true", "you need height|sword is easier|too situational",
      "wind charges give you height anywhere|one smash kills anything|nothing else one shots a ravager",
      "mace gang", "too situational|sword gang"))

S("shield", "the shield", "weapons fighting gear", "fighting gear mobs",
  m="crafted a shield|put my banner on my shield|my shield saved me from a creeper|blocked a skeleton's arrows all night",
  mr="safe|nice|shield gang",
  mf="looks so cool|blocking life",
  q=["how do you make a shield? > six planks and an iron ingot / planks and one iron",
     "can a shield block creepers? > yeah, most of the blast / yep, face it and block",
     "what breaks a shield? > an axe hit disables it for a bit / axes knock it down"],
  qr="ok|good to know",
  lk="a shield makes every fight easier|blocking arrows feels great|my shield with my banner looks amazing|"
     "pvp_sweat: shield and axe, the combo|pvp_sweat: block, crit, block, crit",
  lkr="safe|it's slow|banner shields look great",
  lkf="safe wins|style too",
  tp="tip: block toward the creeper and you take almost nothing|axe hits disable shields|"
     "shields block arrows completely|a banner goes on in the crafting table|"
     "repair with planks at an anvil|keep it in your offhand always",
  br="i blocked a creeper blast with my shield|my shield has my banner on it|survived a raid with just a shield",
  brr="safe|nice",
  brf="shield saves lives|offhand forever",
  cp="a vindicator broke my shield|my shield broke in a raid|my shield broke at the worst time",
  cps="rough",
  cpm="it just shattered|i had nothing",
  cpx="carry a spare|repair it at an anvil|unbreaking on it|always keep it in the offhand",
  dg=("a shield is the most important piece of gear|shield beats armor",
      "it blocks everything|true", "armor is more important|totems are better", "shield gang", "fair"),
  db=("shields make fights too easy|shields are overpowered|blocking everything is cheesy",
      "kinda|true", "axes break them|it's skill|it's fair",
      "you can block arrows forever|creepers do nothing|mobs can't touch you",
      "too easy", "axes though|it's fair"))

S("fishing_rod", "the fishing rod", "tools fishing", "fishing tools ocean",
  m="made a fishing rod|my rod has luck of the sea|fished up a saddle|caught a fish first cast",
  mr="fishing time|nice|the relaxing tool",
  mf="so relaxing|fish for days",
  q=["how do you make a fishing rod? > three sticks and two string / sticks and string",
     "what's lure? > fish bite faster / it makes fish come quicker",
     "what's luck of the sea? > more treasure / better loot, books and stuff"],
  qr="ok|nice",
  lk="fishing is the most relaxing thing in the game|a good rod is my favourite tool|fishing in the rain is perfect|"
     "homebody: fishing off my dock every evening|homebody: a rod and a quiet lake",
  lkr="relaxing|boring|rain fishing is great",
  lkf="relaxing is the point|boring is peaceful",
  tp="tip: fishing is faster in the rain|lure makes them bite quicker|luck of the sea gets more treasure|"
     "you can pull mobs with a rod|open water gives treasure|a rod and a carrot makes a pig stick",
  br="fished up an enchanted bow|got mending from fishing|caught a name tag on my first day",
  brr="lucky|nice",
  brf="the sea provides|lucky cast",
  cp="i keep fishing up junk|fished up a boot again|fished for an hour, just leather boots",
  cps="junk",
  cpm="boots, sticks, bowls|so much junk",
  cpx="luck of the sea|open water|fish in the rain|be patient",
  dg=("fishing is worth it for the loot|fishing gives the best loot",
      "books!|true", "too slow|too much junk", "fish gang", "fair"),
  db=("fishing is the most overpowered thing|fishing gives free enchanted books|fishing beats enchanting",
      "free loot|true", "too random|it's slow|enchanting is better",
      "mending books from fishing|no levels needed|just sit and wait",
      "fish gang", "too random|too slow"))

S("shears", "shears", "tools animals farming", "animals tools farming",
  m="made a pair of shears|sheared a whole flock|got leaves with my shears|carved a pumpkin with shears",
  mr="snip snip|wool time|handy",
  mf="so much wool|handy little tool",
  q=["how do you make shears? > two iron ingots / two iron diagonal",
     "what can shears do? > shear sheep, get leaves, vines, cobwebs / wool, leaves, carving pumpkins",
     "do shears get honeycomb? > yeah, on a full beehive / yep, three honeycomb"],
  qr="huh|ok",
  lk="shears are the most underrated tool|shearing sheep is so relaxing|leaves blocks from shears are great for builds|"
     "builder: shears for leaves, best decor tool|builder: shears for vines and glow lichen",
  lkr="underrated|it's a snip|leaves are useful",
  lkf="underrated for sure|snip snip",
  tp="tip: shears get more wool than killing sheep|they cut cobwebs and keep them|"
     "shears on leaves keep the leaf block|use them on a full hive for honeycomb|"
     "carve pumpkins with shears|shears cut tripwire without setting it off",
  br="i have a full chest of wool from shearing|got every leaf block with my shears|carved twenty pumpkins for halloween",
  brr="nice|cute",
  brf="sheep farmer|leaves for days",
  cp="forgot shears and punched the sheep|left my shears at home, killed the sheep|no shears, so i punched the flock",
  cps="oops",
  cpm="i had to punch them|now no sheep",
  cpx="carry them always|breed more sheep|keep them in your hotbar|make a spare",
  dg=("shears are the best tool for building|shears are a builder's best tool",
      "leaves!|true", "pickaxe is more useful|axe is better", "shear gang", "fair"),
  db=("shears are useless|nobody needs shears|shears are a waste of iron",
      "kinda|lol", "wool|leaves|honeycomb",
      "kill a sheep, get wool|leaves just decay|you can buy wool",
      "useless", "wool though|still useful"))

S("flint_and_steel", "flint and steel", "tools crafting", "tools crafting nether",
  m="made a flint and steel|lit my nether portal|lit a campfire with flint and steel|set fire to a creeper with flint and steel",
  mr="fire!|careful|nice",
  mf="it worked|careful with that",
  q=["how do you make flint and steel? > an iron ingot and flint / iron plus flint",
     "how many uses? > sixty four / about sixty four",
     "what happens if you use it on a creeper? > it blows up / it explodes, run"],
  qr="ok|whoa",
  lk="lighting a portal with flint and steel is the best moment|flint and steel is so satisfying|"
     "there's something about lighting a campfire|speedrunner: flint and steel, portal, go|speedrunner: always carry one",
  lkr="fire is fun|careful|portals are cool",
  lkf="careful fun|portal time",
  tp="tip: flint from gravel, iron ingot, done|it lights portals, tnt, campfires and candles|"
     "careful near wood, fire spreads|you can light a creeper and it explodes|"
     "sixty four uses|fire charges work too",
  br="lit my first nether portal|i set a whole forest on fire, oops|lit a tnt chain from far away",
  brr="nice|oops",
  brf="portal time|it was an accident",
  cp="i set my house on fire with flint and steel|fire spread to my roof|burned down my own barn",
  cps="oops",
  cpm="one click and it spread|wood everywhere",
  cpx="build with stone|be careful near wood|keep a water bucket|use it outside",
  st=("funny", "so i was lighting a campfire|story: i wanted to light my campfire|i went to light my campfire"
                ">> missed and lit the grass|clicked the floor next to it"
                ">> the fire spread to my fence and my house|burned my fence down|now i have a smaller garden lol"),
  dg=("flint and steel is the most dangerous tool|nothing is more dangerous than flint and steel",
      "fire spreads|true", "tnt is worse|lava is worse", "fire gang", "fair"),
  db=("you should always carry flint and steel|flint and steel belongs in every hotbar|never leave home without flint and steel",
      "portals|true", "never needed it|too dangerous|waste of a slot",
      "portals, campfires, emergencies|it lights the way home|you never know",
      "always carry", "waste of a slot|never needed it"))

S("spyglass", "the spyglass", "tools exploring", "exploring tools places",
  m="made a spyglass|looked at the mountains with my spyglass|spotted a village with my spyglass|my spyglass is my favourite thing",
  mr="eagle eye|nice|scouting",
  mf="it's so clear|i can see everything",
  q=["how do you make a spyglass? > two copper and an amethyst shard / copper and amethyst",
     "what does a spyglass do? > zooms in / lets you see far",
     "is a spyglass useful? > for scouting, yeah / spotting villages and mobs"],
  qr="ok|cool",
  lk="the spyglass is so underrated|looking around with a spyglass is so fun|i spy everything with my spyglass|"
     "nomad: a spyglass on a mountain, best view|nomad: spyglass first, walk second",
  lkr="underrated|useless|fun though",
  lkf="fun is the point|underrated",
  tp="tip: two copper and one amethyst shard|climb high and spy for villages|"
     "spot mobs before they see you|look for structures across the ocean|"
     "a spyglass shows where to walk|zoom in on the moon lol",
  br="spotted a woodland mansion with my spyglass|found three villages from one mountain|i watched a creeper from a mile away",
  brr="eagle eye|nice",
  brf="spyglass gang|useful after all",
  cp="nobody uses the spyglass|my spyglass sits in a chest|i forget my spyglass every trip",
  cps="lol",
  cpm="it's such a cool tool|it never gets used",
  cpx="put it in your hotbar|use it for scouting|climb something|take it on trips",
  dg=("the spyglass is the most underrated tool|spyglass deserves more love",
      "true|so true", "it's pointless|just walk there", "spy gang", "fair"),
  db=("the spyglass is useless|nobody needs a spyglass|spyglass is a toy",
      "kinda|lol", "scouting though|it's useful|it's fun",
      "just walk there|it does nothing|you can see far enough without it",
      "useless", "scouting though|it's fun"))

S("brush", "the brush", "tools structures exploring", "structures exploring tools",
  m="made a brush|brushed some suspicious sand|got a pottery sherd from brushing|brushed an armadillo",
  mr="archaeology|careful|nice find",
  mf="so satisfying|slow but fun",
  q=["how do you make a brush? > a feather, copper ingot and a stick / feather, copper, stick",
     "what's a brush for? > brushing suspicious sand and gravel / archaeology, sherds and stuff",
     "what does an armadillo give? > scutes when you brush it / brush it for scutes"],
  qr="huh|cool",
  lk="brushing sand is so relaxing|archaeology is the best new thing|i love finding sherds|"
     "nomad: brushing ruins on the road|nomad: every ruin, i brush it",
  lkr="relaxing|slow|sherds are cool",
  lkf="slow is relaxing|i like slow",
  tp="tip: suspicious sand looks a bit different, brush it slow|don't mine it, it breaks|"
     "trail ruins have the best sherds|brushing an armadillo gives scutes|"
     "scutes make wolf armor|desert wells have suspicious sand",
  br="i have twelve different sherds|brushed up a sniffer egg|made wolf armor from armadillo scutes",
  brr="nice|cool",
  brf="took ages|archaeologist",
  cp="i broke the suspicious sand by accident|mined the suspicious gravel by mistake|destroyed a sherd block",
  cps="oops",
  cpm="mined it instead of brushing|it's gone forever",
  cpx="hold the brush, not a shovel|be patient|try trail ruins|look closer next time",
  dg=("archaeology is the best new thing|brushing is the most fun thing",
      "relaxing|true", "too slow|it's boring", "brush gang", "fair"),
  db=("archaeology is pointless|brushing gives nothing useful|the brush is a waste of copper",
      "sherds are useless|true", "sniffer eggs|armadillo scutes|it's fun",
      "a sherd is decoration|hours for a bowl|nothing you can use",
      "pointless", "sniffers though|scutes"))

S("compass", "the compass", "tools exploring", "exploring tools places",
  m="made a compass|my compass points home|got a lodestone compass going|found my way back with a compass",
  mr="never lost|handy|nice",
  mf="handy|saved me",
  q=["how do you make a compass? > four iron and a redstone / iron around redstone",
     "where does a compass point? > world spawn / spawn, not your bed",
     "what's a recovery compass? > points to where you died / echo shards and a compass, points to your death"],
  qr="ok|neat",
  lk="a compass makes me feel safe|i love lodestone compasses|i never travel without a compass|"
     "nomad: compass and a map, that's all i need|nomad: compass in my pocket always",
  lkr="safe|useless|lodestones are cool",
  lkf="safe travels|safe is good",
  tp="tip: a compass points to world spawn, not your bed|use it on a lodestone and it points there|"
     "it spins in the nether|a recovery compass points to your last death|"
     "you need it for maps|four iron and a redstone",
  br="i have a lodestone at my base and a compass to it|my recovery compass saved my stuff|never got lost once",
  brr="smart|nice",
  brf="never lost|always home",
  cp="my compass points to spawn, not home|followed my compass to spawn by mistake|i got lost with a compass",
  cps="lol",
  cpm="it went the wrong way|i walked to spawn",
  cpx="use a lodestone|write down coords|set it to home|use a map too",
  dg=("a compass is useless without a lodestone|compasses only matter with lodestones",
      "spawn is useless|true", "spawn is useful|maps need it", "lodestone gang", "fair"),
  db=("compasses are useless|nobody needs a compass|a compass is a waste of iron",
      "points to spawn|true", "maps need them|lodestones|recovery compass",
      "who cares about spawn|you'll get lost anyway|just remember the way",
      "useless", "maps though|lodestones"))

S("clock", "the clock", "tools", "tools night caves",
  m="made a clock|my clock says it's almost night|checked my clock in the mine|crafted a clock out of gold",
  mr="time check|nice|fancy",
  mf="almost night|handy down there",
  q=["how do you make a clock? > four gold and a redstone / gold around redstone",
     "what does a clock do? > shows if it's day or night / tells the time",
     "does a clock work in the nether? > no, it spins / nope, it goes crazy"],
  qr="ok|neat",
  lk="the clock is so useful underground|i love my clock in an item frame|a clock tells me when to go home|"
     "miner: clock down deep, so i know when to sleep|miner: no clock, no idea what time it is",
  lkr="useful|pointless|item frames are cute",
  lkf="useful down there|it's cute",
  tp="tip: four gold and one redstone|check it underground before heading home|"
     "it spins in the nether and the end|put it in an item frame at home|"
     "it shows when you can sleep|gold is cheap for this",
  br="i have a clock in every room|my mine has a clock at the entrance|i always know the time",
  brr="fancy|organised",
  brf="never late|time master",
  cp="i lost track of time underground again|came up and it was night|no clock and it got dark",
  cps="classic",
  cpm="mobs everywhere when i came up|i walked into a zombie horde",
  cpx="carry a clock|go up often|light your exit|sleep before mining",
  dg=("a clock is a waste of gold|clocks are pointless",
      "the sky tells you|true", "they're handy underground|useful", "save gold", "fair"),
  db=("every miner needs a clock|no miner should go down without a clock|clocks save lives",
      "underground, yes|true", "just look at the sky|waste of gold|never needed one",
      "underground you have no idea of time|coming up at night is how you die|it's four gold",
      "clock gang", "waste of gold|never needed"))

S("maps", "maps", "tools exploring", "exploring places tools",
  m="made a map of my area|zoomed out my map|put my maps on a wall|filled in a whole map today",
  mr="explorer|nice|maps are cool",
  mf="it's so cool to see|getting bigger",
  q=["how do you make a map? > eight paper and a compass / paper around a compass",
     "how do you zoom out a map? > cartography table with paper / add paper at a cartography table",
     "can you mark places on a map? > put a banner and click it with the map / use banners"],
  qr="ok|nice",
  lk="filling in a map is so satisfying|map walls are the coolest|i love seeing my world on a map|"
     "nomad: every trip, a new map|nomad: maps are my diary",
  lkr="satisfying|so cool|map walls are great",
  lkf="so satisfying|best decoration",
  tp="tip: eight paper and a compass makes a map|zoom out at a cartography table|"
     "banners show up on maps if you click them|map walls need item frames|"
     "copy maps at a cartography table|lock a map so it stops updating",
  br="my map wall covers the whole area|i mapped five thousand blocks|my map has all the villages marked",
  brr="explorer|wow",
  brf="took forever|every corner",
  cp="maps take so much paper|my map wall needs so much paper|i ran out of paper for maps",
  cps="paper problem",
  cpm="sugar cane all gone|my sugar cane farm can't keep up",
  cpx="grow more sugar cane|trade for paper|zoom out instead|use fewer maps",
  dg=("map walls are the best decoration|nothing beats a map wall",
      "so cool|true", "too much work|paintings are better", "map gang", "fair"),
  db=("maps are pointless, just remember the way|nobody needs maps|maps are a waste of paper",
      "true|kinda", "you'll get lost|map walls|marking villages",
      "remember your way, that's the skill|coords are better|you can't read a map in a cave",
      "no maps", "map gang|you'll get lost"))

S("lead", "leads", "tools animals", "animals farming tools",
  m="crafted a couple of leads|led a cow home on a lead|tied my horse to a fence|walked a sheep across the river",
  mr="walkies|nice|tied up",
  mf="it followed me all the way|so handy",
  q=["how do you make a lead? > four string and a slime ball / string and slime",
     "can you lead a villager? > no, only animals / nope",
     "what do you tie a lead to? > fences / a fence post"],
  qr="ok|got it",
  lk="leads are the best farm tool|walking animals home on leads is cute|leads make moving animals so easy|"
     "homebody: walking my sheep home on a lead|homebody: tying my horse to the porch",
  lkr="cute|handy|slime is rare",
  lkf="handy is right|cute too",
  tp="tip: four string and a slime ball make two leads|tie them to fences|"
     "you can't lead villagers|leads snap if you go too far|"
     "slime balls from slimes in swamps|leads work on horses and llamas",
  br="led three cows across the whole map|my horse is tied to my porch|got a whole farm with leads",
  brr="nice|farmer",
  brf="took forever but worth it|they followed",
  cp="my lead snapped|the cow got loose on the way home|my lead broke at the river",
  cps="ugh",
  cpm="i went too far ahead|it just broke",
  cpx="walk slower|use two leads|use food instead|keep close",
  dg=("leads are better than food for moving animals|leads beat wheat for moving animals",
      "they stay|true", "wheat works|slime is rare", "lead gang", "fair"),
  db=("leads are a waste of slime|just lure animals with food|nobody needs a lead",
      "wheat works|true", "food is slow|leads are better|horses need leads",
      "wheat is everywhere|slime is rare|they follow food just as well",
      "use food", "leads are easier|horses though"))

S("name_tag", "name tags", "tools animals pets", "pets animals tools",
  m="named my dog with a name tag|found a name tag in a dungeon|fished up a name tag|named my horse",
  mr="cute|what name? > something silly / you'll laugh / a secret",
  mf="it suits them|so cute",
  q=["how do you use a name tag? > rename it at an anvil then use it on a mob / anvil first, then on the mob",
     "where do you find name tags? > dungeons, fishing, librarians sell them / chests and fishing",
     "do named mobs despawn? > no, named ones stay / nope, they stay forever"],
  qr="ok|nice",
  lk="naming pets is the best part of the game|name tags make animals family|i name every animal i love|"
     "homebody: everyone in my barn has a name|homebody: named my cat after my first house",
  lkr="cute|rare though|named pets are family",
  lkf="family!|they deserve names",
  tp="tip: rename the tag at an anvil first|named mobs never despawn|"
     "librarians sell name tags sometimes|fishing gives name tags|"
     "name a sheep jeb_ for a rainbow|naming costs a level",
  br="all my pets have name tags|i named a zombie and it follows me around|my horse has a name tag",
  brr="cute|family",
  brf="they deserve it|my babies",
  cp="i misspelled my dog's name|typo on my horse's name tag|named my cat wrong",
  cps="oops",
  cpm="now it's called stve|forever",
  cpx="find another|fish for more|ask a librarian|just roll with it",
  st=("funny", "so i named a sheep jeb_|story: i heard a trick about naming a sheep|i used a name tag on a sheep"
                ">> called it jeb_ underscore and all|typed jeb_ on the tag"
                ">> it started changing colours|it went rainbow|now i have a disco sheep lol"),
  dg=("every pet needs a name|naming pets is important",
      "family|true", "waste of tags|they don't care", "name gang", "fair"),
  db=("name tags are the best loot in the game|name tags beat diamonds in chests|nothing in a chest beats a name tag",
      "cute|true", "diamonds lol|enchanted books|golden apples",
      "you can buy diamonds, not love|named pets are forever|it's the only item that's personal",
      "name gang", "diamonds though|still books"))

S("saddle", "saddles", "tools animals", "animals exploring tools",
  m="found a saddle in a dungeon|saddled my horse|rode a pig with a saddle|a ravager dropped a saddle",
  mr="ride time|nice|lucky",
  mf="giddy up|finally",
  q=["can you craft a saddle? > no, you find them / nope, chests, fishing or trades",
     "where do you find saddles? > dungeons, temples, fishing / chests mostly, or a leatherworker",
     "what can you ride with a saddle? > horses, pigs, striders, camels / horses mostly"],
  qr="ok|huh",
  lk="riding a horse with a saddle is the best|saddles are the best find|riding a pig is hilarious|"
     "nomad: a saddle and a horse, the road is mine|nomad: never travel on foot again",
  lkr="fast|rare|pigs are funny",
  lkf="fast travel|pig riding is art",
  tp="tip: you can't craft saddles, check every dungeon chest|ravagers drop saddles|"
     "fishing gets saddles sometimes|leatherworkers sell them at master level|"
     "pigs need a carrot on a stick to steer|camels carry two",
  br="i have three saddles|rode a pig across the whole town|my horse has a saddle and armor",
  brr="rich|lol nice",
  brf="never walking again|pig life",
  cp="i can never find a saddle|tamed a horse but no saddle|no saddle in any chest",
  cps="ugh",
  cpm="it's just sitting there|no saddle anywhere",
  cpx="fish for one|check dungeons|kill a ravager|trade with a leatherworker",
  dg=("saddles should be craftable|we need to craft saddles",
      "so annoying|true", "it's fine|finding them is fun", "craft gang", "fair"),
  db=("riding pigs is the best way to travel|pig riding beats horses|pigs are underrated mounts",
      "lol yes|true", "horses are way faster|pigs are slow|lol no",
      "a pig is always loyal|carrot on a stick, done|everyone respects a pig rider",
      "pig gang", "horses though|too slow"))

# ======================================================================= Armor
S("leather_armor", "leather armor", "armor gear animals", "armor gear animals",
  m="made a full set of leather armor|dyed my leather armor blue|still in leather armor lol|got enough leather for a tunic",
  mr="fashion|it's a start|cute",
  mf="it's all i've got|better than nothing",
  q=["how much leather for a full set? > twenty four / two dozen leather",
     "can you dye leather armor? > yeah, any colour, wash it in a cauldron / yep, craft it with dye",
     "is leather armor any good? > barely, but the boots stop powder snow / not really, but it's cheap"],
  qr="ok|huh",
  lk="leather armor is the most stylish|dyed leather is my look|i love a matching leather set|"
     "builder: leather armor dyed to match my house|builder: green leather is the only correct colour",
  lkr="stylish|weak though|fashion over function",
  lkf="fashion first|style is armor",
  tp="tip: leather boots let you walk on powder snow|dye leather any colour, wash it in a cauldron|"
     "rabbit hide makes leather, four for one|cows drop leather, breed them|"
     "piglins don't care about leather, wear gold|leather is good for enchanting practice",
  br="my leather set is dyed perfectly|i have leather armor in every colour|matching leather with my horse armor",
  brr="fashion|cute",
  brf="style matters|it's a whole look",
  cp="leather armor does nothing|a zombie walked through my leather armor|leather armor is paper",
  cps="weak stuff",
  cpm="i still died|it's basically a shirt",
  cpx="upgrade to iron|breed cows|get rabbit hides|hide from zombies",
  dg=("leather armor is the best looking armor|leather beats all armor for looks",
      "dye!|true", "netherite looks better|chain looks cooler", "fashion gang", "fair"),
  db=("leather armor is useless|nobody should wear leather|leather is a waste of a cow",
      "weak|true", "powder snow boots|it's cheap|fashion",
      "zombies hit right through it|iron is easy to get|it's paper armor",
      "useless", "boots though|style though"))

S("chainmail", "chainmail armor", "armor gear", "armor gear villagers",
  m="got a chainmail chestplate from an armorer|a zombie dropped chainmail boots|wearing chainmail today|"
    "found chainmail in a chest",
  mr="rare!|knight vibes|nice",
  mf="it looks so cool|rare stuff",
  q=["can you craft chainmail? > no, mobs drop it or armorers sell it / nope, trade or find it",
     "how do you repair chainmail? > iron ingots at an anvil / with iron at an anvil",
     "is chainmail better than iron? > a bit worse / no, iron is better"],
  qr="ok|neat",
  lk="chainmail is the coolest looking armor|knight vibes with chainmail|chainmail is rare and i love it|"
     "pvp_sweat: chainmail is drip|pvp_sweat: rare armor, rare player",
  lkr="cool|it's worse than iron|rare is nice",
  lkf="rare is cool|drip over stats",
  tp="tip: armorer villagers sell chainmail|zombies and skeletons drop it sometimes|"
     "repair it with iron at an anvil|it's between gold and iron for protection|"
     "it looks great with a trim|enchant it, it's worth it",
  br="i have a full set of chainmail|my whole set is from armorers|got chainmail boots off a zombie",
  brr="rare|knight",
  brf="took a while|knight life",
  cp="chainmail is impossible to find|i want chainmail and nobody sells it|my armorer has no chainmail",
  cps="rare stuff",
  cpm="i've traded with three armorers|no luck",
  cpx="level up the armorer|try another village|kill zombies with looting|kill skeletons too",
  dg=("chainmail looks better than iron|chainmail is the best looking armor",
      "knight|true", "iron looks better|it's see through", "chain gang", "fair"),
  db=("chainmail is pointless|chainmail is worse iron|nobody needs chainmail",
      "weaker|true", "it looks cool|it's rare|it's free from mobs",
      "it's iron armor but worse|iron is easy|you can't even craft it",
      "pointless", "it's rare|looks though"))

S("iron_armor", "iron armor", "armor gear", "armor gear mining",
  m="made a full set of iron armor|got an iron helmet too|got iron boots finally|iron armor, finally safe",
  mr="safe now|nice|tank",
  mf="feels good|creepers who",
  q=["how much iron for a full set? > twenty four / two dozen ingots",
     "is iron armor good enough? > for most things yeah / it's solid, diamond is better",
     "what's an iron farm? > golems spawn and drop iron / villagers spawn golems, you collect the iron"],
  qr="ok|good to know",
  lk="iron armor is the real workhorse|a full iron set feels so safe|iron armor is honest protection|"
     "speedrunner: iron armor and go|speedrunner: iron boots and a bucket, ready",
  lkr="solid|diamond is better|safe",
  lkf="solid is good|safe enough",
  tp="tip: iron armor is twenty four ingots|get the chestplate first, it protects most|"
     "iron golems drop iron|an iron farm keeps you in armor|"
     "repair iron armor at an anvil with ingots|protection on iron is plenty for most things",
  br="i have a full iron set with protection|my iron farm makes all my armor|got iron armor on day one",
  brr="nice|tank",
  brf="day one gang|iron farm life",
  cp="my iron armor broke in a fight|my iron boots broke in a cave|creeper ruined my iron chestplate",
  cps="ugh",
  cpm="it just broke|i'm back to leather",
  cpx="iron farm|mine more|repair at an anvil|carry spare iron",
  dg=("iron armor is enough for everything|you don't need diamond armor",
      "iron is enough|true", "diamond is way better|the nether needs diamond", "iron gang", "fair"),
  db=("iron armor is the best value in the game|iron armor beats diamond for value|iron armor is all you need",
      "cheap and good|true", "diamond lasts longer|toughness matters|iron breaks fast",
      "twenty four iron vs twenty four diamonds|iron farms make it free|it's most of the way there",
      "iron gang", "diamond though|toughness"))

S("gold_armor", "gold armor", "armor gear", "armor gear nether",
  m="made gold boots for the nether|wearing a gold helmet|crafted gold armor, it's so shiny|gold boots on, ready for the nether",
  mr="shiny|for the piglins|bling",
  mf="piglins love it|shiny is protection",
  q=["why wear gold armor? > piglins won't attack you / for the nether, piglins leave you alone",
     "do you need a full gold set? > one piece is enough / just one, boots or helmet",
     "is gold armor strong? > no, it breaks fast / weak, but it enchants well"],
  qr="ok|huh",
  lk="gold armor looks so good|shiny boots are my favourite|gold armor is pure style|"
     "pvp_sweat: gold helmet, nether king|pvp_sweat: shiny drip",
  lkr="shiny|weak though|piglins love it",
  lkf="shiny wins|piglins agree",
  tp="tip: one gold piece keeps piglins calm|gold boots are the cheapest piece for the nether|"
     "gold armor enchants really well|it breaks fast, repair or replace|"
     "don't open chests near piglins, even with gold|gold helmets on top of other armor is the move",
  br="i have gold boots with soul speed|my whole set is gold|piglins love me",
  brr="bling|rich",
  brf="nether king|they respect me",
  cp="i forgot my gold boots in the nether|piglins attacked me with no gold|took my gold off and piglins went crazy",
  cps="rough",
  cpm="the whole bastion came at me|i lost it all",
  cpx="carry gold boots|make a gold helmet|never forget the gold|keep a spare",
  dg=("gold armor is only good for the nether|gold armor is pointless outside the nether",
      "true|weak", "it enchants well|it looks good", "gold for piglins", "fair"),
  db=("gold armor is the most useful armor|gold armor beats iron|gold armor is underrated",
      "piglins|true", "it breaks fast|iron is better|it's weak",
      "piglins leave you alone|it enchants the best|you need gold for the nether anyway",
      "gold gang", "weak|iron gang"))

S("diamond_armor", "diamond armor", "armor gear diamonds", "armor gear diamonds",
  m="got a full set of diamond armor|made a diamond chestplate|my diamond armor has protection four|diamond boots, finally",
  mr="rich|the dream|tank",
  mf="feels unstoppable|took so many diamonds",
  mn="a:has_diamonds",
  q=["how many diamonds for a full set? > twenty four / two dozen",
     "which piece first? > chestplate, it's the most protection / chestplate",
     "what's the best armor enchant? > protection four / protection, and mending"],
  qr="ok|whoa",
  lk="diamond armor is the best feeling|walking around in diamond is so good|diamond armor shines|"
     "pvp_sweat: full diamond, prot four, try me|pvp_sweat: diamonds on, fear me",
  lkr="tank|rich|shiny",
  lkf="tank mode|rich and safe",
  tp="tip: chestplate first, it gives the most|protection four on every piece|"
     "mending keeps it forever|diamond armor has toughness, big hits hurt less|"
     "repair it with diamonds at an anvil|upgrade to netherite later, enchants stay",
  br="i have full diamond with protection four|my diamond armor has mending|full diamond, first week",
  brn="a:has_diamonds",
  brr="rich|tank",
  brf="worth every diamond|feels great",
  cp="my diamond armor broke|i lost my diamond armor in lava|i died in diamond armor and lost it",
  cps="nooo",
  cpm="twenty four diamonds gone|i didn't even get it back",
  cpx="mending next time|don't go near lava|get netherite, it floats|go back faster",
  st=("bad", "so i had full diamond armor|story: i finally got full diamond|i was in full diamond for the first time"
              ">> got cocky and went into a dungeon|walked into a spawner room like a boss"
              ">> got swarmed and died anyway|creeper and skeletons, gone|lost it all in a hole"),
  dg=("diamond armor is enough, skip netherite|diamond is the real end armor",
      "enough|true", "netherite is better|it burns in lava", "diamond gang", "fair"),
  db=("diamond armor is more important than a diamond pick|armor first, then pickaxe|diamonds go to armor first",
      "survive first|true", "pick first, more diamonds|you need the pick for obsidian|armor later",
      "a pick can't save you|you can't mine if you're dead|chestplate first, always",
      "armor first", "pick first|fortune pays for armor"))

S("netherite_armor", "netherite armor", "armor gear", "armor gear", gate="nether",
  m="upgraded my chestplate to netherite|full netherite now|my netherite boots survived the lava|smithed netherite leggings",
  mr="end game|flex|nice",
  mf="it doesn't burn|took forever",
  mn="a:knows_netherite",
  q=["how do you make netherite armor? > upgrade diamond at a smithing table / diamond piece, ingot and template",
     "what does netherite add? > more toughness and knockback resistance / it's tougher and floats in lava",
     "how do you copy the template? > seven diamonds and netherrack / diamonds and netherrack"],
  qr="ok|whoa",
  lk="netherite armor looks so good|full netherite feels untouchable|netherite is the best armor|"
     "pvp_sweat: full netherite, untouchable|pvp_sweat: no knockback, all aggression",
  lkr="untouchable|overkill|it's ugly",
  lkf="overkill is fine|safe is good",
  tp="tip: upgrade enchanted diamond pieces, enchants stay|netherite floats in lava, you won't lose it|"
     "it gives knockback resistance|copy templates with seven diamonds|"
     "ancient debris is around y 15 in the nether|four scrap and four gold for an ingot",
  tpn="a:knows_netherite",
  br="full netherite with protection four|i have netherite armor and tools|my netherite set is maxed",
  brn="a:knows_netherite",
  brr="end game|flex",
  brf="took weeks|unstoppable",
  cp="i need so much debris for armor|netherite armor eats so much debris|debris is so rare",
  cpn="a:knows_netherite",
  cps="debris pain",
  cpm="four ingots for one set|i'm short three",
  cpx="bed mining|tnt the nether|bastion loot|keep looking",
  dg=("netherite armor is worth the grind|full netherite is a must",
      "untouchable|true", "diamond is enough|too much grind", "netherite gang", "fair"),
  db=("netherite armor is ugly|netherite looks worse than diamond|diamond armor looks way better",
      "dark and dull|true", "it looks great|it's tough|trims fix it",
      "it's grey and dull|diamond shines|nobody wears it for looks",
      "ugly", "still tough|trims fix it"))

S("turtle_helmet", "the turtle helmet", "armor ocean animals", "ocean animals armor",
  m="made a turtle helmet|my turtle helmet gives water breathing|collected turtle scutes|turtles laid eggs on my beach",
  mr="cute|sea vibes|nice",
  mf="breathe underwater|cute",
  q=["how do you make a turtle helmet? > five turtle scutes / five scutes",
     "where do scutes come from? > baby turtles drop them when they grow up / babies growing up",
     "what does it do? > water breathing for a few seconds / extra air underwater"],
  qr="ok|cute",
  lk="the turtle helmet is so cute|turtles are my favourite animal|the turtle helmet is underrated|"
     "homebody: i raise turtles on my beach|homebody: baby turtles are the best",
  lkr="cute|weak|underrated",
  lkf="cute wins|underrated",
  tp="tip: breed turtles with seagrass|they lay eggs on their home beach|"
     "baby turtles drop a scute when they grow up|five scutes make the helmet|"
     "zombies stomp turtle eggs, guard them|it gives water breathing when you dive",
  br="i have a turtle farm|made a turtle helmet for ocean trips|raised like twenty baby turtles",
  brr="cute|farmer",
  brf="baby turtles everywhere|ocean ready",
  cp="zombies trampled my turtle eggs|a zombie stomped all my turtle eggs|my turtle eggs got crushed",
  cps="nooo",
  cpm="all the eggs gone|three whole clutches",
  cpx="light up the beach|fence the eggs|be patient|breed more",
  dg=("the turtle helmet is the cutest armor|turtle helmet beats every helmet for looks",
      "cute|true", "it's weak|diamond helmet is better", "turtle gang", "fair"),
  db=("the turtle helmet is useless|turtle helmets are a waste of time|nobody needs a turtle helmet",
      "weak|true", "water breathing|it's cute|it's useful",
      "respiration does it better|five scutes for ten seconds of air|it's weaker than iron",
      "useless", "cute though|still useful"))

S("elytra", "elytra", "gear armor exploring", "exploring gear", gate="end",
  m="flew over the whole ocean with my elytra|got an elytra from an end ship|glided down a mountain with my elytra|bought a stack of fireworks",
  mr="flying!|nice|the dream",
  mf="the view is incredible|so fast",
  mn="a:knows_elytra",
  q=["where do you get an elytra? > end ships in end cities / the end, on the ships",
     "how do you fly with it? > jump, then jump again in the air / jump twice, fireworks to boost",
     "how do you repair an elytra? > phantom membranes or mending / mending, or membranes at an anvil"],
  qr="ok|whoa",
  lk="flying with an elytra is the best feeling|elytra changed everything for me|soaring over mountains is unreal|"
     "nomad: elytra and fireworks, i go anywhere|nomad: i never walk now",
  lkr="freedom|it ruins walking|so fast",
  lkf="freedom|walking is overrated",
  tp="tip: fireworks boost you in the air|mending on an elytra is a must|"
     "don't fly into walls, kinetic damage hurts|phantom membranes repair it|"
     "jump off something high to start|land on water if you're low on durability",
  tpn="a:knows_elytra",
  br="i have two elytras|flew to the other side of the map in minutes|my elytra has mending and unbreaking",
  brn="a:knows_elytra",
  brr="flex|rich",
  brf="took forever to find|i fly everywhere",
  cp="flew into a wall with my elytra|my elytra broke mid flight|ran out of fireworks in the air",
  cpn="a:knows_elytra",
  cps="ouch",
  cpm="fell straight down|i was so high up",
  cpx="carry more fireworks|watch the durability|mending|fly high",
  st=("bad", "so i was flying over the mountains|story: i was gliding home over a mountain|i was elytra flying home"
              ">> ran out of fireworks|my elytra hit low durability"
              ">> crashed into a cliff|fell into a ravine|died a mile from home"),
  dg=("the elytra is the best item in the game|nothing beats an elytra",
      "freedom|true", "mending is better|totem is better", "elytra gang", "fair"),
  db=("elytra ruined exploring|flying makes the world too small|walking is how you see the world",
      "true|kinda", "it made it better|you see more|walking is slow",
      "you skip every cave and village|the world shrinks|you miss everything on the ground",
      "walk it", "still flying|it's freedom"))

S("armor_trims", "armor trims", "armor gear decor", "armor gear structures",
  m="put a trim on my chestplate|found a smithing template in a shipwreck|my armor has gold trim now|"
    "duplicated a trim template",
  mr="fashion|drip|nice",
  mf="it looks so good|style",
  q=["how do you trim armor? > template, armor and a material at a smithing table / smithing table with a template",
     "where do you find trim templates? > structures, each has its own / temples, shipwrecks, outposts, all over",
     "how do you copy a trim? > seven diamonds and the block it came from / diamonds and the template"],
  qr="ok|neat",
  lk="armor trims are the best thing they added|my trimmed armor looks so good|trims make armor mine|"
     "pvp_sweat: drip matters in a fight|pvp_sweat: trims say who you are",
  lkr="drip|it's cosmetic|so cool",
  lkf="drip is armor|cosmetic is everything",
  tp="tip: each structure has its own trim|the coast trim is in shipwrecks|"
     "you need the template, the armor and a material|gold, copper, amethyst, they all show different colours|"
     "copying costs seven diamonds|the silence trim is in ancient cities, it's rare",
  br="i have every trim template|my whole set has amethyst trim|got the silence trim",
  brr="drip|rich",
  brf="took forever|fashion icon",
  cp="trims cost so many diamonds to copy|copying a template costs seven diamonds|i spent all my diamonds on trims",
  cps="fashion pain",
  cpm="seven diamonds for a copy|my diamond chest is empty",
  cpx="only copy the ones you love|find another one|it's cosmetic, relax|mine more diamonds",
  dg=("trims are worth the diamonds|trims are worth it",
      "drip|true", "waste of diamonds|it's just looks", "drip gang", "fair"),
  db=("armor trims are a waste of diamonds|nobody needs trims|trims are pointless",
      "cosmetic|true", "it's style|it's fun|drip matters",
      "seven diamonds for a pattern|it does nothing|nobody sees it",
      "pointless", "drip matters|still worth it"))

# ======================================================================= Food
S("bread", "bread", "food farming", "food farming cooking",
  m="baked a stack of bread|got bread from a farmer|bread for the trip|made bread from my first wheat",
  mr="carbs|nice|the classic",
  mf="it's enough for a while|bread life",
  q=["how do you make bread? > three wheat in a row / three wheat",
     "how much does bread fill? > five, like two and a half drumsticks / five hunger",
     "is bread good food? > ok, steak is better / fine early on"],
  qr="ok|nice",
  lk="bread is the comfort food|nothing beats fresh bread|bread from my own wheat tastes better|"
     "homebody: baking bread is my morning routine|homebody: a loaf for the road",
  lkr="carbs|steak is better|homemade is best",
  lkf="comfort food|homemade wins",
  tp="tip: three wheat make bread, no furnace needed|a wheat farm keeps you in bread|"
     "villagers throw bread around|bread is great early on|"
     "steak fills more if you have cows|keep a stack in your pack",
  br="i have three stacks of bread|my wheat farm makes so much bread|i never run out of bread",
  brr="carbs|baker",
  brf="farmer life|never hungry",
  cp="i'm so sick of bread|bread doesn't fill me up|all i have is bread",
  cpn="a:hungry",
  cps="bread again",
  cpm="eat, eat, eat, still hungry|i need meat",
  cpx="get some cows|cook steak|go fishing|try potatoes",
  dg=("bread is the best early game food|nothing beats bread early",
      "easy|true", "meat is better|potatoes are better", "bread gang", "fair"),
  db=("bread is the worst food|bread is a waste of wheat|nobody should eat bread",
      "weak|true", "it's easy|no furnace needed|it's fine",
      "steak fills way more|wheat is for breeding|three wheat for five hunger",
      "steak gang", "bread forever|it's easy"))

S("steak", "steak", "food animals", "food animals cooking",
  m="cooked a stack of steak|got beef from my cow farm|steak for dinner|smelted some steak",
  mr="meat!|best food|nice",
  mf="fills you right up|cow farm life",
  q=["is steak the best food? > one of them, it fills a lot / with porkchop, yeah",
     "how much does steak fill? > eight, four drumsticks / eight hunger",
     "how do you get beef? > cows, one to three each / kill cows"],
  qr="ok|nice",
  lk="steak is the best food in the game|nothing beats a steak|i live on steak|"
     "speedrunner: steak, enough, go|speedrunner: cooked beef is all i need",
  lkr="best food|porkchop is the same|meat is life",
  lkf="meat is life|same thing, steak wins",
  tp="tip: breed cows with wheat for endless steak|a smoker cooks it twice as fast|"
     "steak fills eight and lasts|cows drop leather too|"
     "a campfire cooks four at once|keep a stack in your pack",
  br="i have four stacks of steak|my cow farm has fifty cows|never hungry, always steak",
  brr="rich|meat lord",
  brf="cow farm life|steak forever",
  cp="my cows all wandered off|my cow pen is empty|all my cows escaped",
  cpn="a:hungry",
  cps="rough",
  cpm="i forgot to close the gate|i'm eating rotten flesh",
  cpx="build a fence|breed more cows|go hunting|fish",
  dg=("steak is better than porkchop|steak beats porkchop",
      "cows give leather|true", "they're the same|pigs are easier", "steak gang", "fair"),
  db=("steak is the best food in the game|no food beats steak|steak is number one",
      "facts|true", "golden carrots|porkchop is the same|bread is easier",
      "it fills eight, saturation is high|cows are everywhere|it's simple",
      "steak gang", "golden carrots though|porkchop"))

S("porkchop", "porkchops", "food animals", "food animals cooking",
  m="cooked some porkchops|pig farm is doing great|got porkchops from a hoglin|porkchops for lunch",
  mr="bacon|nice|meat",
  mf="so good|hogs everywhere",
  q=["are porkchops as good as steak? > exactly the same / same fill",
     "what do you breed pigs with? > carrots, potatoes or beetroot / carrots",
     "do hoglins drop porkchops? > yeah, in the nether / yep, lots"],
  qr="ok|nice",
  lk="porkchops are the best food|cooked porkchop is my favourite|pigs are the easiest farm|"
     "homebody: porkchops for breakfast|homebody: my pig pen is my pride",
  lkr="bacon|steak is better|pigs are cute",
  lkf="bacon forever|cute and tasty",
  tp="tip: pigs breed with carrots, potatoes or beetroot|porkchops are the same as steak|"
     "hoglins drop porkchops too|cook them in a smoker for speed|"
     "lightning turns pigs into zombified piglins|pigs are easy to lure",
  br="my pig farm is huge|i have two stacks of porkchops|got ten porkchops from one hoglin trip",
  brr="bacon lord|nice",
  brf="pigs are easy|never hungry",
  cp="my pigs got struck by lightning|lightning hit my pig pen|a storm turned my pigs into zombie piglins",
  cps="rough",
  cpm="now they're zombie piglins|my pen is full of them",
  cpx="put up a lightning rod|breed more|build a roof|farm cows instead",
  dg=("porkchops are better than steak|porkchop beats steak",
      "pigs are easier|true", "same thing|steak gives leather", "pork gang", "fair"),
  db=("pigs are too cute to eat|eating pigs is wrong|you should never kill pigs",
      "so cute|true", "they're food|cows are cute too|it's survival",
      "look at their face|ride them instead|eat vegetables",
      "save the pigs", "bacon though|it's survival"))

S("chicken", "chicken", "food animals", "food animals farming",
  m="cooked some chicken|my chicken farm is full of eggs|got feathers and chicken|chickens everywhere at my place",
  mr="nice|chicken farm|eggs!",
  mf="eggs for days|so many chickens",
  q=["is raw chicken bad? > yeah, it can give you hunger / it might, cook it",
     "how often do chickens lay eggs? > every few minutes / every five to ten minutes",
     "what do chickens eat? > seeds / any seeds"],
  qr="ok|got it",
  lk="chicken farms are the easiest|i love my chickens|cooked chicken is fine food|"
     "homebody: my chickens all have names|homebody: collecting eggs every morning",
  lkr="easy|chickens are cute|meh food",
  lkf="cute|easy food",
  tp="tip: never eat raw chicken, you might get hunger|chickens breed with seeds|"
     "throw eggs for baby chickens|feathers make arrows|"
     "chicken farms with hoppers collect eggs|cooked chicken fills six",
  br="my chicken farm makes a stack of eggs a day|i have like a hundred chickens|cake every week from my eggs",
  brr="eggs|farmer",
  brf="egg life|baking time",
  cp="i ate raw chicken and got sick|raw chicken gave me hunger|ate chicken raw, big mistake",
  cps="oof",
  cpm="hunger effect|my food bar is draining",
  cpx="cook it next time|milk cures it|always cook chicken|eat bread instead",
  dg=("chicken farms are the best farms|chickens are the easiest farm",
      "eggs|true", "cows are better|too loud", "chicken gang", "fair"),
  db=("chickens are the most useful animal|chickens beat every animal|chickens are underrated",
      "eggs, feathers, meat|true", "cows give more|pigs|too loud",
      "feathers for arrows, eggs for cake, meat for food|they breed with seeds|so easy",
      "chicken gang", "cows though|too loud"))

S("mutton", "mutton", "food animals", "food animals cooking",
  m="cooked some mutton|got mutton from my sheep farm|mutton and wool, nice combo|ate mutton for lunch",
  mr="nice|meat|double use",
  mf="wool and food|not bad",
  q=["how much does mutton fill? > six / six, like chicken",
     "should i kill sheep or shear them? > shear, then kill the extra / shear them, more wool",
     "do sheep drop mutton? > one or two / yeah, one to two"],
  qr="ok|nice",
  lk="mutton is underrated|i like mutton better than chicken|sheep give everything|"
     "homebody: mutton stew vibes|homebody: my sheep farm feeds me",
  lkr="underrated|meh|sheep are useful",
  lkf="underrated|sheep are great",
  tp="tip: shear before you kill, more wool|mutton fills six|"
     "sheep breed with wheat|fire aspect cooks it right away|"
     "breed lots of sheep, they're double use|a smoker cooks it fast",
  br="i have a huge sheep farm|mutton and wool for days|cooked a stack of mutton",
  brr="farmer|nice",
  brf="sheep life|double use",
  cp="i feel bad killing sheep|killing sheep makes me sad|my sheep look at me funny",
  cps="rough",
  cpm="they're so fluffy|they just stand there",
  cpx="shear them instead|farm cows|eat bread|eat fish",
  dg=("mutton is better than chicken|mutton beats chicken",
      "fills more|true", "same|chicken is easier", "mutton gang", "fair"),
  db=("you should never kill sheep|sheep are for wool only|killing sheep is a waste",
      "wool|true", "they're food|breed more|it's fine",
      "a sheep gives wool forever|kill a cow instead|one mutton vs infinite wool",
      "wool forever", "it's food|breed more"))

S("rabbit_stew", "rabbit stew", "food animals cooking", "food cooking animals",
  m="made rabbit stew|caught a rabbit finally|rabbit stew for dinner|got a rabbit's foot",
  mr="fancy food|nice|chef",
  mf="took forever|so much work",
  q=["how do you make rabbit stew? > cooked rabbit, carrot, baked potato, mushroom and a bowl / rabbit, carrot, potato, mushroom",
     "how much does it fill? > ten / ten, the most of any stew",
     "does it stack? > no, one bowl at a time / nope"],
  qr="ok|whoa",
  lk="rabbit stew is the best meal|cooking rabbit stew feels fancy|i love a good stew|"
     "homebody: rabbit stew on a rainy day|homebody: stew night is the best night",
  lkr="fancy|too much work|cozy",
  lkf="worth it|cozy food",
  tp="tip: cooked rabbit, carrot, baked potato, a mushroom and a bowl|it fills ten|"
     "it doesn't stack, carry a few bowls|rabbits are fast, use a bow|"
     "rabbit's foot is for leaping potions|rabbits breed with carrots or dandelions",
  br="i made five rabbit stews|caught three rabbits in one go|my stew is the best in town",
  brr="chef|fancy",
  brf="took a while|cooking master",
  cp="rabbits are impossible to catch|chased a rabbit for ten minutes|rabbits are too fast",
  cps="rabbits are fast",
  cpm="they hop everywhere|i lost it in the grass",
  cpx="use a bow|trap them|breed them|eat steak",
  dg=("rabbit stew is the best food|nothing beats rabbit stew",
      "fills ten|true", "doesn't stack|too much work", "stew gang", "fair"),
  db=("rabbit stew is not worth making|rabbit stew is a waste of time|nobody makes rabbit stew",
      "too much work|true", "fills ten|it's fun|it's the best meal",
      "five things for one bowl|it doesn't stack|just eat steak",
      "steak instead", "fills ten|it's fun"))

S("golden_carrots", "golden carrots", "food health", "food farming health",
  m="made a stack of golden carrots|golden carrots are my main food now|crafted golden carrots|bought golden carrots from a farmer",
  mr="fancy|the best food|rich",
  mf="saturation king|so good",
  q=["how do you make golden carrots? > a carrot and eight gold nuggets / carrot surrounded by nuggets",
     "why are golden carrots so good? > the best saturation, you stay full / they keep you full longest",
     "what else are they for? > night vision potions and horses / brewing and breeding horses"],
  qr="ok|nice",
  lk="golden carrots are the best food|i only eat golden carrots now|golden carrots keep me full forever|"
     "speedrunner: golden carrots, eat once, go|speedrunner: best food per slot",
  lkr="rich|best food|fancy",
  lkf="best food|rich life",
  tp="tip: a carrot and eight gold nuggets|they have the best saturation|"
     "a gold farm makes them cheap|farmers sell them sometimes|"
     "they make night vision potions|feed them to horses to breed",
  br="i have three stacks of golden carrots|my gold farm feeds my carrot habit|i never eat anything else",
  brr="rich|fancy",
  brf="gold farm life|saturation king",
  cp="golden carrots cost so much gold|i ran out of nuggets|i can't afford golden carrots",
  cps="gold is precious",
  cpm="eight nuggets each|it adds up",
  cpx="build a gold farm|trade with farmers|eat steak|smelt gold tools",
  dg=("golden carrots are the best food|nothing beats golden carrots",
      "saturation|true", "too expensive|steak is enough", "gold gang", "fair"),
  db=("golden carrots are a waste of gold|gold carrots aren't worth it|steak is better value",
      "expensive|true", "best saturation|gold farms make it cheap|so good",
      "eight nuggets for a carrot|steak is free|you'll be hungry later anyway",
      "steak instead", "saturation|gold farm"))

S("golden_apples", "golden apples", "food health fighting", "food health fighting",
  m="crafted a golden apple|ate a golden apple in a fight|found an enchanted golden apple|saving my golden apples",
  mr="rich|nice|clutch food",
  mf="saved my life|keeping it",
  q=["how do you make a golden apple? > an apple and eight gold ingots / apple surrounded by gold ingots",
     "what does a golden apple do? > regen and absorption / extra hearts and healing",
     "can you cure zombie villagers with it? > yeah, weakness potion then golden apple / weakness first, then the apple"],
  qr="ok|whoa",
  lk="golden apples are the best item|eating a golden apple mid fight is clutch|golden apples saved me so many times|"
     "pvp_sweat: gapple and swing|pvp_sweat: golden apples win fights",
  lkr="clutch|expensive|life savers",
  lkf="clutch|life savers",
  tp="tip: an apple and eight gold ingots|eat it before a big fight|"
     "cure zombie villagers with weakness and a golden apple|enchanted ones can't be crafted|"
     "breed horses with them|keep a few for emergencies",
  br="i have twelve golden apples|i found two enchanted golden apples|cured three zombie villagers",
  brr="rich|nice",
  brf="ready for anything|gold farm",
  cp="golden apples cost so much gold|eight gold ingots for one apple|apples are so rare",
  cps="gold is precious",
  cpm="my gold is all gone|i need more apples",
  cpx="farm oak leaves|gold farm|trade for apples|save them",
  st=("good", "so i was fighting a raid|story: i was in a raid|raid night at the village"
               ">> down to two hearts|a ravager had me cornered"
               ">> ate a golden apple and turned it around|gapple, swing, swing, won|survived thanks to one apple"),
  dg=("golden apples are worth the gold|golden apples are worth it",
      "life savers|true", "too expensive|potions are better", "gapple gang", "fair"),
  db=("you should save golden apples forever|never eat your golden apples|golden apples are for emergencies only",
      "true|emergencies", "use them, that's what they're for|hoarding is dumb|eat them",
      "you never know when you need one|gold is rare|one day it saves you",
      "save them", "use them|hoarding is dumb"))

S("cake", "cake", "food crafting", "food crafting friends",
  m="baked a cake|put a cake on my table|ate a slice of cake|made a cake for no reason",
  mr="party|yum|cake!",
  mf="cake for everyone|it's not even a birthday",
  q=["how do you make a cake? > three milk, two sugar, an egg and three wheat / milk, sugar, egg, wheat",
     "how many slices in a cake? > seven / seven slices",
     "can you pick up a cake after eating? > no, it's gone / nope, eat it all"],
  qr="ok|nice",
  lk="cake is the best food|a cake on the table makes a home|cake with a candle is perfect|"
     "homebody: baking cakes for the neighbours|homebody: a cake in every kitchen",
  lkr="party|cute|cake is great",
  lkf="party time|cozy",
  tp="tip: three milk, two sugar, an egg and three wheat|you get the buckets back|"
     "seven slices, two hunger each|you can't pick it up once it's eaten|"
     "put a candle on it|place it where everyone can eat",
  br="i baked a cake for the whole town|my cakes are famous|i made ten cakes",
  brr="baker|nice",
  brf="everyone loves cake|baking king",
  cp="i broke my cake by accident|punched my cake and it vanished|lost my cake, clicked it wrong",
  cps="nooo",
  cpm="gone in one click|three milk buckets wasted",
  cpx="bake another|be careful|eat it slice by slice|right click, don't punch",
  st=("funny", "so i baked a cake|story: i made my first cake|i baked a cake for a friend"
                ">> put it down to show it off|placed it on the table"
                ">> broke it by accident trying to pick it up|i punched it and it vanished|lost the whole thing lol"),
  dg=("cake is the best food|cake beats every food",
      "yum|true", "it's not worth it|steak fills more", "cake gang", "fair"),
  db=("cake is a waste of time|nobody should bake cake|cake is pointless",
      "too much work|true", "it's fun|it's for sharing|it's a party",
      "three buckets of milk for fourteen hunger|steak is better|you can't even carry it",
      "pointless", "it's fun|party though"))

S("pumpkin_pie", "pumpkin pie", "food farming", "food farming cooking",
  m="made a pumpkin pie|baked pies from my pumpkins|pumpkin pie for dinner|got a stack of pumpkin pies",
  mr="yum|pie!|nice",
  mf="tasty|pumpkin season",
  q=["how do you make pumpkin pie? > a pumpkin, sugar and an egg / pumpkin, sugar, egg",
     "is pumpkin pie good food? > fills eight, saturation is low / fills a lot, not long",
     "does it stack? > yeah, sixty four / yep"],
  qr="ok|nice",
  lk="pumpkin pie is the coziest food|i love pumpkin pie|pumpkin pie is underrated|"
     "homebody: pumpkin pie in the evening|homebody: pie day is the best day",
  lkr="cozy|meh|underrated",
  lkf="cozy|underrated",
  tp="tip: pumpkin, sugar and egg, shapeless|it fills eight|"
     "the saturation is low, eat it when you're hungry|it stacks to sixty four|"
     "a pumpkin farm makes a ton|no furnace needed",
  br="i have a stack of pumpkin pies|my pies are famous|baked pies for everyone",
  brr="baker|nice",
  brf="pumpkin farm|pie master",
  cp="pumpkin pie doesn't keep me full|i get hungry so fast on pie|pie never lasts",
  cpn="a:hungry",
  cps="low saturation",
  cpm="ate two and i'm hungry again|it's all filling, no staying",
  cpx="eat steak|golden carrots|mix it up|eat pie first, then steak",
  dg=("pumpkin pie is better than bread|pie beats bread",
      "fills more|true", "bread is easier|pie is too much work", "pie gang", "fair"),
  db=("pumpkin pie is the best food nobody eats|pumpkin pie is underrated|pie deserves more respect",
      "underrated|true", "low saturation|steak is better|it's meh",
      "it fills eight and stacks|no furnace|pumpkin farms are easy",
      "pie gang", "meh|steak"))

S("cookies", "cookies", "food farming", "food farming pets",
  m="baked a stack of cookies|made cookies from cocoa|cookies for the road|got cookies from a farmer",
  mr="yum|cookies!|nice",
  mf="snack time|so many",
  q=["how do you make cookies? > two wheat and cocoa beans / wheat, cocoa, wheat",
     "how many cookies from one craft? > eight / eight",
     "can you feed cookies to parrots? > no, it kills them / never, it's poison to them"],
  qr="ok|whoa",
  lk="cookies are the best snack|i love baking cookies|cookies are so cute|"
     "homebody: cookies by the fire|homebody: cookie jar on the counter",
  lkr="cute|weak food|snack",
  lkf="snack life|cute food",
  tp="tip: two wheat and a cocoa bean make eight|cocoa grows on jungle logs|"
     "never feed cookies to parrots|they only fill two each|"
     "farmers sell cookies|they stack to sixty four",
  br="i have three stacks of cookies|my cocoa farm makes so many cookies|baked cookies for everyone",
  brr="baker|cute",
  brf="cookie king|share time",
  cp="cookies don't fill me up|i ate twenty cookies and still hungry|cookies are a waste",
  cpn="a:hungry",
  cps="snack problems",
  cpm="two each|i need real food",
  cpx="eat steak|bread|save them for snacks|golden carrots",
  st=("bad", "so i had a parrot|story: i had this parrot named sky|i tamed a parrot"
              ">> fed it a cookie because it looked hungry|gave it a cookie as a treat"
              ">> it just died|cookies are poison to parrots|i'll never forgive myself"),
  dg=("cookies are the best snack|cookies beat bread",
      "cute|true", "bread fills more|they're weak", "cookie gang", "fair"),
  db=("cookies are useless food|nobody should eat cookies|cookies are a waste of wheat",
      "two hunger|true", "they're cute|eight per craft|snacks matter",
      "two hunger each|bread is better|they poison parrots",
      "useless", "cute though|eight per craft"))

S("berries", "berries", "food plants", "food plants animals",
  m="picked a bunch of sweet berries|got glow berries from the lush cave|ate some berries on the road|"
    "berry bushes everywhere in the taiga",
  mr="snack|yum|fresh",
  mf="quick food|tasty",
  q=["what are glow berries? > berries from cave vines, they glow / from lush caves, they light up",
     "how much do berries fill? > two / just two",
     "do foxes like berries? > they love them / yeah, you can breed them with berries"],
  qr="ok|cute",
  lk="sweet berries are the best snack|glow berries are so pretty|berries make the taiga great|"
     "nomad: berries off the bush on the road|nomad: free food everywhere",
  lkr="snack|weak|pretty",
  lkf="free food|snacks count",
  tp="tip: sweet berries grow in taigas|glow berries are in lush caves|"
     "berries are quick to eat|foxes love sweet berries|"
     "plant glow berries on a ceiling for light|bone meal the bushes",
  br="i have a stack of glow berries|my berry farm is huge|ate nothing but berries for a week",
  brr="snack king|nice",
  brf="free food|berry life",
  cp="berry bushes hurt so much|walked through berry bushes and took damage|got stuck in a berry bush",
  cps="ouch",
  cpm="they slow you and poke you|i lost two hearts",
  cpx="walk around them|cut a path|pick them from the side|harvest them first",
  dg=("berries are the best survival food|berries are underrated",
      "free food|true", "two hunger|too weak", "berry gang", "fair"),
  db=("glow berries are better than torches|glow berries beat torches|glow berry lighting is the best",
      "pretty|true", "torches are easier|too dim|weird",
      "they hang and glow|natural light|they grow on their own",
      "glow gang", "torches are easier|too dim"))

S("melon", "melons", "food farming", "food farming plants",
  m="harvested my melon farm|ate some melon slices|got a stack of melon slices|my melons are growing",
  mr="juicy|nice|summer vibes",
  mf="so many slices|snack time",
  q=["how many slices in a melon? > three to seven / a few, fortune gets more",
     "what's glistering melon? > a slice and gold nuggets, for healing potions / for healing potions",
     "how do you plant melons? > melon seeds, from slices / seeds, need a free block next to them"],
  qr="ok|nice",
  lk="melons are the best snack|melon farms are so easy|i love melon slices|"
     "homebody: melon in the summer|homebody: fresh melon from my garden",
  lkr="juicy|weak food|easy farm",
  lkf="juicy|easy",
  tp="tip: melons need a free dirt block next to the stem|fortune gets more slices|"
     "a slice is two hunger|nine slices make a melon block|"
     "glistering melon makes healing potions|melon seeds from slices",
  br="my melon farm makes a stack a day|i have three stacks of melon slices|sold melons to a farmer",
  brr="farmer|nice",
  brf="melon king|easy money",
  cp="melons take forever to grow|my melon stems grow nothing|no melons on my stems",
  cps="melon problems",
  cpm="nothing growing|just stems",
  cpx="give the stem space|bone meal|light the farm|dirt next to the stem",
  dg=("melon is the best snack|melons beat apples",
      "juicy|true", "apples fill more|weak", "melon gang", "fair"),
  db=("melons are the best farm|melon farms beat every farm|nothing beats a melon farm",
      "easy|true", "weak food|sugar cane is better|wheat is better",
      "free food, emeralds from farmers|they regrow forever|potions too",
      "melon gang", "weak food|wheat"))

S("apples", "apples", "food trees", "food trees health",
  m="got an apple from an oak|apples from the leaves|ate an apple|saving apples for golden apples",
  mr="lucky|healthy|nice",
  mf="rare find|for later",
  q=["where do apples come from? > oak and dark oak leaves / when oak leaves decay",
     "how much does an apple fill? > four / four",
     "what are apples for? > golden apples / food and golden apples"],
  qr="ok|nice",
  lk="apples are the best surprise|finding an apple is so nice|apples are underrated|"
     "nomad: apples off the trees on the way|nomad: free apples on every road",
  lkr="lucky|rare|nice",
  lkf="lucky|free food",
  tp="tip: oak and dark oak leaves drop apples|let leaves decay for more|"
     "save apples for golden apples|apples fill four|"
     "farmers sell apples|a big oak farm gets lots",
  br="i have a stack of apples|got three apples from one tree|my apple stash is huge",
  brr="lucky|rich",
  brf="lucky tree|oak farm",
  cp="i never get apples|chopped twenty trees, zero apples|apples are so rare",
  cps="unlucky",
  cpm="all those leaves|no apples",
  cpx="let the leaves decay|chop more oaks|trade with farmers|fortune on a hoe",
  dg=("apples are the most underrated food|apples deserve more respect",
      "golden apples|true", "they're rare|steak is better", "apple gang", "fair"),
  db=("apples are useless as food|nobody eats apples|apples are just for golden apples",
      "true|kinda", "four hunger|they're free|nice snack",
      "you get one per twenty trees|four hunger, meh|they only matter in gold",
      "golden only", "free food|nice snack"))

S("potatoes", "potatoes", "food farming", "food farming cooking",
  m="baked a stack of potatoes|harvested my potato farm|got potatoes from a zombie|potatoes for dinner",
  mr="spuds|nice|baked potatoes",
  mf="so many|potato life",
  q=["how much does a baked potato fill? > five / five, like bread",
     "where do you get potatoes? > village farms or zombies / villages",
     "what's a poisonous potato? > a bad one, sometimes drops / don't eat it"],
  qr="ok|nice",
  lk="baked potatoes are the best farm food|potatoes are so easy|i love a potato farm|"
     "homebody: baked potatoes every night|homebody: potato soup vibes",
  lkr="spuds|easy|meh",
  lkf="spuds forever|easy food",
  tp="tip: plant potatoes, they drop more than you plant|bake them in a smoker|"
     "baked potatoes fill five|don't eat poisonous ones|"
     "zombies drop them|villages always have potato farms",
  br="i have six stacks of baked potatoes|my potato farm is huge|never hungry with potatoes",
  brr="spud lord|farmer",
  brf="potato life|easy food",
  cp="i ate a poisonous potato|poisonous potato got me|ate the green potato",
  cps="oof",
  cpm="i got poisoned|it was green, i should've known",
  cpx="toss the green ones|check before eating|milk cures it|cook only the good ones",
  dg=("potatoes are better than wheat|potato farms beat wheat",
      "more food|true", "wheat feeds animals|bread is easier", "potato gang", "fair"),
  db=("potatoes are the best crop|nothing beats potatoes|potatoes beat every crop",
      "easy|true", "wheat is more useful|carrots are better|meh",
      "plant one, get four|baked fills five|easy everywhere",
      "potato gang", "wheat|carrots"))

S("beetroot_soup", "beetroot soup", "food farming", "food farming cooking",
  m="made beetroot soup|harvested my beetroots|soup for dinner|got beetroot seeds from a village",
  mr="soup!|nice|red",
  mf="cozy food|tasty",
  q=["how do you make beetroot soup? > six beetroot and a bowl / six beets and a bowl",
     "how much does it fill? > six / six, like chicken",
     "what else are beetroots for? > red dye / red dye and pigs"],
  qr="ok|nice",
  lk="beetroot soup is so cozy|i love a beet farm|beetroot soup is underrated|"
     "homebody: soup on a cold night|homebody: my beet garden is lovely",
  lkr="cozy|weird|underrated",
  lkf="cozy|underrated",
  tp="tip: six beetroot and a bowl|beetroot gives red dye|"
     "pigs breed with beetroot|seeds come from villages|"
     "soup doesn't stack|a beet farm is easy",
  br="i have a big beet farm|my soup is famous|made ten bowls of soup",
  brr="chef|nice",
  brf="soup king|cozy",
  cp="beetroot soup doesn't stack|i can only carry so much soup|soup doesn't stack, so annoying",
  cps="soup problems",
  cpm="one bowl per slot|my inventory is all bowls",
  cpx="carry bowls|eat potatoes|use beets for dye|make it at home",
  dg=("beetroot soup is the best stew|beet soup beats mushroom stew",
      "cozy|true", "mushroom stew is easier|rabbit stew is better", "beet gang", "fair"),
  db=("beetroots are the worst crop|beets are useless|nobody grows beets",
      "true|lol", "red dye|soup|pigs",
      "six beets for six hunger|one beet is one hunger|grow potatoes",
      "useless", "red dye|soup though"))

S("suspicious_stew", "suspicious stew", "food cooking plants", "food plants cooking",
  m="made a suspicious stew|got stew from a brown mooshroom|ate a weird stew|made stew with a flower",
  mr="sus|what flower? > a poppy, night vision / an oxeye daisy, for regen / a dandelion",
  mf="it worked|weird but good",
  q=["how do you make suspicious stew? > both mushrooms, a bowl and a flower / mushrooms, bowl and a flower",
     "what does the flower do? > each flower gives an effect / the flower picks the effect",
     "which flower is best? > oxeye daisy for regen / dandelion for saturation"],
  qr="huh|ok",
  lk="suspicious stew is the most fun food|i love guessing the stew effect|stew with a daisy is great|"
     "homebody: regen stew in the cupboard|homebody: flower soup is cute",
  lkr="sus|risky|fun",
  lkf="risky fun|sus is good",
  tp="tip: oxeye daisy stew gives regen|poppy gives night vision|"
     "lily of the valley gives poison, avoid|dandelion stew fills you up|"
     "brown mooshrooms make it with a flower|wither rose stew is bad news",
  br="i have a stack of regen stews|made a night vision stew for mining|got stew from my mooshroom",
  brr="sus|nice",
  brf="regen life|flower power",
  cp="i ate a poison stew|my stew gave me poison|wrong stew, poisoned",
  cps="sus",
  cpm="lily of the valley|picked the wrong flower",
  cpx="label them|use oxeye daisies|milk cures it|remember the flower",
  dg=("suspicious stew is better than potions|stew beats potions",
      "cheap|true", "potions last longer|too random", "stew gang", "fair"),
  db=("suspicious stew is the most underrated food|sus stew is underrated|stew is secretly great",
      "regen|true", "too risky|it's random|potions are better",
      "regen from a daisy|night vision from a poppy|no brewing stand needed",
      "stew gang", "too random|potions"))

S("dried_kelp", "dried kelp", "food ocean", "food ocean cooking",
  m="dried a stack of kelp|ate some dried kelp|made a dried kelp block|kelp farm is running",
  mr="snack|kelp|fast food",
  mf="quick bites|so fast",
  q=["how do you make dried kelp? > smelt kelp / kelp in a furnace",
     "is dried kelp good food? > one hunger, but eaten fast / not much, but quick",
     "what's a dried kelp block for? > fuel, smelts twenty items / furnace fuel"],
  qr="ok|nice",
  lk="dried kelp is the best snack|i love how fast you eat kelp|kelp blocks are great fuel|"
     "speedrunner: kelp, chew, go|speedrunner: fastest eat in the game",
  lkr="fast|weak|fuel is nice",
  lkf="fast is good|fuel too",
  tp="tip: dried kelp eats really fast|nine make a block, great fuel|"
     "a block smelts twenty items|kelp grows in oceans, farm it|"
     "one hunger each, eat a few|kelp farms are easy",
  br="my kelp farm fuels all my furnaces|i have two stacks of kelp blocks|never buy coal again",
  brr="fuel king|nice",
  brf="kelp life|free fuel",
  cp="dried kelp barely fills me|i only had kelp all day|kelp doesn't fill me at all",
  cpn="a:hungry",
  cps="weak food",
  cpm="one hunger each|i ate thirty",
  cpx="make it fuel|eat real food|fish|cook steak",
  dg=("dried kelp is the best fuel|kelp blocks beat coal",
      "renewable|true", "coal is easier|lava is better", "kelp gang", "fair"),
  db=("dried kelp is useless food|nobody eats dried kelp|kelp isn't food",
      "one hunger|true", "it's fast|fuel|snacks matter",
      "one hunger per kelp|you need twenty for a meal|it's fuel, not food",
      "fuel only", "fast though|it counts"))

S("honey", "honey", "food animals", "food animals farming",
  m="got honey from a beehive|made a honey block|honey bottles for the road|my bees are making honey",
  mr="sweet|nice|bees!",
  mf="sweet stuff|buzz buzz",
  q=["how do you get honey? > glass bottle on a full hive / bottle on the hive when it drips",
     "will the bees attack? > not with a campfire under the hive / put a campfire under it",
     "what does honey do? > fills six and cures poison / food, and cures poison"],
  qr="ok|nice",
  lk="honey is the best food|i love my bees|honey blocks are so fun|"
     "homebody: my bees make my mornings|homebody: honey on everything",
  lkr="sweet|bees are scary|so fun",
  lkf="sweet|bees are friends",
  tp="tip: put a campfire under the hive and bees stay calm|a bottle on a full hive gets honey|"
     "shears get honeycomb|honey cures poison|"
     "honey blocks slow falls and stick to stuff|bees need flowers nearby",
  br="i have six beehives|my honey farm is huge|got a stack of honey bottles",
  brr="bee keeper|sweet",
  brf="bee life|they love me",
  cp="bees attacked me for honey|forgot the campfire and got stung|my bees stung me",
  cps="ouch",
  cpm="the whole hive came at me|they were mad",
  cpx="campfire under the hive|plant flowers|use a dispenser|be careful",
  st=("funny", "so i wanted some honey|story: i tried getting honey|i went for honey"
                ">> forgot the campfire|grabbed the honey without smoke"
                ">> the whole hive chased me into a lake|i jumped in the river to escape|i got honey and ten stings"),
  dg=("honey is the best food|honey beats steak",
      "sweet|true", "steak fills more|bees are scary", "honey gang", "fair"),
  db=("bees are the best animal|bees beat every animal|nothing is better than bees",
      "honey|true", "they sting|cats are better|they're scary",
      "honey, wax, they grow your crops|cute and useful|they pollinate",
      "bee gang", "they sting|cats though"))

S("rotten_flesh", "rotten flesh", "food mobs", "food mobs pets",
  m="ate rotten flesh again|got a stack of rotten flesh|fed rotten flesh to my dog|my chest is full of rotten flesh",
  mr="gross|eww|desperate times|ew why|that is grim|hermit: mm|hermit: grim", mg=False,
  mf="it's all i had|food is food",
  q=["is rotten flesh safe to eat? > you might get hunger / mostly no, you get hunger",
     "can dogs eat rotten flesh? > yeah, it's fine for them / yep, wolves love it",
     "can you sell rotten flesh? > clerics buy it / yeah, to clerics for emeralds"],
  qr="huh|eww ok",
  lk="rotten flesh is better than starving|my dog loves rotten flesh|rotten flesh is free food|"
     "bandit: rotten flesh is survival food|bandit: real survivors eat flesh",
  lkr="gross|desperate|dogs love it",
  lkf="desperate times|my dog agrees",
  tp="tip: clerics buy rotten flesh for emeralds|dogs can eat it safely|"
     "drink milk to clear the hunger|eat it only when starving|"
     "zombies drop it every time|you can't compost it, so trade it",
  br="sold three stacks of flesh to a cleric|my dog is fat on rotten flesh|survived a week on flesh",
  brr="gross|survivor",
  brf="emeralds|survival",
  cp="i've been eating rotten flesh all week|got the hunger effect again|rotten flesh is all i have",
  cpn="a:hungry",
  cps="gross",
  cpm="i'm desperate|everything else is gone",
  cpx="fish|go hunting|share food|plant a farm",
  dg=("rotten flesh is fine to eat|rotten flesh is real food",
      "if you're desperate|true", "gross|hunger effect", "flesh gang", "fair"),
  db=("rotten flesh is the most useless drop|zombies drop trash|rotten flesh is garbage",
      "gross|true", "clerics buy it|dogs eat it|emergency food",
      "you can't even compost it|hunger effect|zombies drop nothing good",
      "garbage", "clerics though|dogs"))

S("fish", "fish", "food fishing ocean", "fishing food ocean",
  m="cooked some cod|caught a salmon|caught a tropical fish|my fish stash is growing",
  mr="nice|fish!|fresh",
  mf="fresh food|ocean life",
  q=["is cooked salmon better than cod? > yeah, salmon fills more / salmon, six vs five",
     "can you eat pufferfish? > no, it poisons you / don't, it's poison",
     "what do cats eat? > raw fish / raw cod or salmon"],
  qr="ok|cool",
  lk="fish is the best renewable food|cooked salmon is great|fishing for dinner is the best|"
     "homebody: fish for dinner, every night|homebody: fresh fish from my dock",
  lkr="fresh|weak|fishing is relaxing",
  lkf="fresh is best|relaxing",
  tp="tip: salmon fills more than cod|don't eat pufferfish|"
     "cats tame with raw fish|dolphins lead you to treasure if you feed them fish|"
     "a smoker cooks fish fast|fish in the rain, they bite faster",
  br="i caught a stack of cod|my cat eats better than me|fed a dolphin and found treasure",
  brr="fisherman|nice",
  brf="ocean life|fish king",
  cp="ate a pufferfish by accident|ate a pufferfish thinking it was cod|pufferfish got me again",
  cps="oof",
  cpm="poisoned|nausea everywhere",
  cpx="check before you eat|drink milk|cook only cod and salmon|throw pufferfish back",
  dg=("fish is better than meat|fish beats steak",
      "renewable|true", "steak fills more|fishing is slow", "fish gang", "fair"),
  db=("fishing is the best way to get food|fish beats farming|fishing is the best food",
      "relaxing|true", "too slow|farms are faster|meh",
      "fish plus treasure|no animals to breed|just sit and wait",
      "fish gang", "farms are faster|too slow"))

# ======================================================================= Crafting and smelting
S("crafting_table", "the crafting table", "crafting tools", "crafting tools home",
  m="made a crafting table|put a crafting table in my house|carrying a crafting table around|placed a crafting table in the mine",
  mr="the first block|classic|always handy",
  mf="can't live without it|first thing every time",
  q=["how do you make a crafting table? > four planks in a square / four planks",
     "why do i need a crafting table? > for anything bigger than two by two / the three by three grid",
     "should i carry one? > yeah, always / one in your pack saves trips"],
  qr="ok|got it",
  lk="the crafting table is the heart of every base|nothing beats a fresh crafting table|i love the crafting table sound|"
     "speedrunner: crafting table, pick, go|speedrunner: table in the pack, always",
  lkr="the heart|it's a table|so classic",
  lkf="the heart of it all|classic",
  tp="tip: four planks make a crafting table|carry one in your pack|"
     "put one next to your furnace and chests|crafting tables burn as fuel|"
     "you can craft small stuff in your inventory grid|an axe breaks it fastest",
  br="i have a crafting table in every room|built a crafting room with every station|my crafting area is perfect",
  brr="organised|nice",
  brf="everything in one place|crafting life",
  cp="i forgot my crafting table again|left the crafting table in the cave|no crafting table and i need a pick",
  cps="classic",
  cpm="had to punch a tree again|walked all the way back",
  cpx="carry one always|keep planks on you|make a spare|put it in your hotbar",
  dg=("every room needs a crafting table|you can't have too many crafting tables",
      "handy|true", "one is enough|clutter", "table gang", "fair"),
  db=("the crafting table is the most important block|without the crafting table there's no game|crafting table is number one",
      "everything starts there|facts", "the furnace is more important|the bed matters more|it's just a table",
      "no table, no tools, no anything|every item goes through it|the first thing anyone makes",
      "table gang", "furnace though|it's a table"))

S("furnace", "the furnace", "crafting cooking", "crafting cooking mining",
  m="built a row of furnaces|my furnace is smelting iron|set up a furnace in my mine|my furnace is full of iron",
  mr="smelting time|nice|warm",
  mf="iron soon|fuel is the problem",
  q=["how do you make a furnace? > eight cobble / cobble in a ring",
     "what's the best fuel? > coal is easy, lava buckets last ages / lava bucket, a hundred items",
     "do you get xp from smelting? > yeah, take the stuff out / yep, it saves up in the furnace"],
  qr="ok|nice",
  lk="the furnace crackle is so cozy|a row of furnaces looks great|i love watching the furnace go|"
     "homebody: the furnace keeps the house warm|homebody: furnace on, kettle on",
  lkr="cozy|it's slow|furnaces are great",
  lkf="cozy|slow is fine",
  tp="tip: a lava bucket smelts a hundred items|coal does eight per piece|"
     "the xp builds up, take it out yourself|a block of coal does eighty|"
     "more furnaces, faster smelting|dried kelp blocks are great fuel",
  br="i have twelve furnaces in a row|my furnace array smelts everything|built an auto smelter",
  brr="efficient|nice",
  brf="smelting machine|never wait",
  cp="smelting takes forever|my furnace is so slow|waiting on the furnace again",
  cps="slow",
  cpm="one item at a time|ten seconds each",
  cpx="build more furnaces|use lava buckets|blast furnace for ores|kelp blocks",
  dg=("one furnace is never enough|you need at least five furnaces",
      "faster|true", "one is fine|patience", "furnace gang", "fair"),
  db=("the furnace is better than the blast furnace|blast furnaces are pointless|nobody needs a blast furnace",
      "furnaces do everything|true", "blast furnace is twice as fast|it's for ores|speed matters",
      "a furnace smelts everything|two furnaces is the same speed|five iron for a faster box",
      "furnace gang", "blast is faster|speed though"))

S("blast_furnace", "the blast furnace", "crafting mining", "crafting mining ore",
  m="built a blast furnace|smelting iron in the blast furnace|my blast furnace is going|made a blast furnace for ores",
  mr="speed|nice|industrial",
  mf="twice as fast|iron for days",
  q=["how do you make a blast furnace? > a furnace, five iron and three smooth stone / furnace, iron, smooth stone",
     "what does a blast furnace do? > smelts ores twice as fast / ores and metal, double speed",
     "can it cook food? > no, only ores and metal / nope, use a smoker"],
  qr="ok|nice",
  lk="blast furnaces are so satisfying|double speed smelting is the best|the blast furnace looks so cool|"
     "miner: blast furnace waiting at home|miner: raw iron straight in the blast furnace",
  lkr="fast|expensive|cool",
  lkf="fast is life|worth the iron",
  tp="tip: it smelts ores at double speed|it needs smooth stone, smelt stone twice|"
     "it only does ores and metal gear|it uses fuel twice as fast too|"
     "an armorer takes it as a job|smelt old iron tools into nuggets",
  br="i have four blast furnaces|my ore smelting is so fast|built a blast furnace room",
  brr="efficient|nice",
  brf="speed|industrial",
  cp="blast furnaces burn fuel so fast|my blast furnace eats coal|coal disappears in the blast furnace",
  cps="trade offs",
  cpm="same fuel per item|half the time",
  cpx="use lava buckets|use kelp blocks|blocks of coal|it's the same per item, relax",
  dg=("the blast furnace is worth five iron|blast furnaces are worth it",
      "speed|true", "same fuel|two furnaces do it", "blast gang", "fair"),
  db=("blast furnaces are a waste of iron|nobody needs a blast furnace|two furnaces beat one blast furnace",
      "true|kinda", "it's faster|armorers need it|it saves space",
      "two furnaces cost no iron|same fuel per item|it can't even cook",
      "two furnaces", "speed though|armorers"))

S("smoker", "the smoker", "crafting cooking food", "cooking food villagers",
  m="built a smoker|cooking steak in the smoker|my smoker is so fast|made a smoker for the kitchen",
  mr="chef|nice|fast food",
  mf="twice as fast|dinner time",
  q=["how do you make a smoker? > a furnace and four logs / furnace with logs around",
     "what does a smoker do? > cooks food twice as fast / food only, double speed",
     "does a smoker make a butcher? > yeah, it's the butcher's job / yep, a villager takes it"],
  qr="ok|nice",
  lk="the smoker is the best kitchen block|a smoker in the kitchen is perfect|smokers are so fast|"
     "homebody: smoker in the kitchen, always|homebody: dinner in half the time",
  lkr="fast|cozy|chef",
  lkf="chef life|fast food",
  tp="tip: a smoker cooks food twice as fast|furnace plus four logs|"
     "it only cooks food|a butcher villager takes it|"
     "same fuel per item as a furnace|put it next to your chests",
  br="my kitchen has two smokers|cooked a stack of steak in no time|my smoker never stops",
  brr="chef|fast",
  brf="kitchen life|dinner's ready",
  cp="my smoker can't smelt iron|i put ore in the smoker by mistake|put raw iron in my smoker, nothing",
  cps="oops",
  cpm="it just sits there|nothing happens",
  cpx="use the blast furnace|keep a normal furnace too|label them|it's food only",
  dg=("a smoker beats a furnace for food|smoker over furnace for food",
      "twice as fast|true", "a furnace does everything|campfires are free", "smoker gang", "fair"),
  db=("every house needs a smoker|a kitchen without a smoker isn't a kitchen|the smoker is essential",
      "true|kitchen", "a furnace works|campfires|not essential",
      "half the time for every meal|it makes a kitchen look real|it's cheap",
      "smoker gang", "furnace works|not essential"))

S("stonecutter", "the stonecutter", "crafting building", "crafting building decor",
  m="made a stonecutter|cutting stone bricks into stairs|my stonecutter is so handy|got a stonecutter for my build",
  mr="builder tool|nice|handy",
  mf="no waste|perfect stairs",
  q=["how do you make a stonecutter? > three stone and an iron ingot / stone on an iron ingot",
     "why use a stonecutter? > one block, one stair, no waste / better ratios than crafting",
     "can it cut wood? > no, just stone and copper / nope"],
  qr="ok|nice",
  lk="the stonecutter is a builder's best friend|no more wasting blocks on stairs|the stonecutter sound is so satisfying|"
     "builder: stonecutter is my most used block|builder: chiseled everything with the stonecutter",
  lkr="builder|handy|it's loud",
  lkf="builder's friend|loud is fine",
  tp="tip: one block makes one stair, crafting gives fewer|you get two slabs per block|"
     "it makes walls, bricks, chiseled blocks|a mason villager takes it|"
     "copper cuts there too|it's three stone and an iron",
  br="i cut eight stacks of stairs today|my stonecutter is in the middle of my workshop|built a whole castle with the stonecutter",
  brr="builder|nice",
  brf="no waste|efficient",
  cp="i used the crafting table for stairs and wasted so much|forgot the stonecutter existed|crafted stairs the old way",
  cps="oops",
  cpm="six blocks for four stairs|so much waste",
  cpx="use the stonecutter|make one now|keep it near your build|it's cheap",
  dg=("the stonecutter is the best builder tool|stonecutter beats the crafting table for builds",
      "no waste|true", "crafting is fine|too much switching", "cutter gang", "fair"),
  db=("the stonecutter should cut wood too|stonecutters need wood recipes|wood deserves a cutter",
      "please|true", "it's for stone|use the table|it's fine",
      "wood stairs waste so much|why only stone|builders need it",
      "cut the wood", "it's for stone|it's fine"))

S("loom", "the loom", "crafting decor", "crafting decor town",
  m="made a loom|designed a banner on the loom|my banner looks so good|made a banner for my house",
  mr="designer|nice|banners!",
  mf="it has a creeper face|took ages",
  q=["how do you make a loom? > two string and two planks / string over planks",
     "what's a loom for? > patterns on banners / banners, way easier than crafting",
     "how do you get banner patterns? > craft some with paper, find others / flower, creeper, skull with paper"],
  qr="ok|neat",
  lk="the loom is the most fun block|designing banners is the best|banners make a base look amazing|"
     "builder: banners on every tower|builder: a town flag on the loom",
  lkr="fun|hard to use|banners are cool",
  lkf="fun is the point|banner life",
  tp="tip: two string and two planks|it's way easier than crafting banners|"
     "pattern items unlock special designs|a shepherd villager takes it|"
     "banners on a shield look great|banners mark spots on maps",
  br="i made a flag for our town|my banners are on every tower|i have a banner for every base",
  brr="designer|nice",
  brf="town pride|took forever",
  cp="my banner design went wrong|i can't get the pattern right|wasted dye on a bad banner",
  cps="design pain",
  cpm="the layers are in the wrong order|it looks like a mess",
  cpx="plan it first|copy a banner|start simple|use fewer layers",
  dg=("banners are the best decoration|a banner makes any build",
      "colour|true", "too fiddly|paintings are better", "banner gang", "fair"),
  db=("every town needs a flag|towns should have their own banner|a town without a banner isn't a town",
      "pride|true", "it's just decoration|too fiddly|who cares",
      "you see the banner and know where you are|it's on every shield|it's who we are",
      "flag gang", "just decoration|meh"))

S("smithing_table", "the smithing table", "crafting gear armor", "crafting gear armor",
  m="made a smithing table|upgraded my sword at the smithing table|put a trim on my armor|smithing table is set up",
  mr="upgrade time|nice|fancy",
  mf="it's so good now|took forever",
  q=["how do you make a smithing table? > four planks and two iron / planks under iron",
     "what does it do? > netherite upgrades and armor trims / upgrades and trims",
     "does a toolsmith use it? > yeah, it's their job block / yep"],
  qr="ok|nice",
  lk="the smithing table is the coolest block|upgrading gear feels amazing|the smithing table is end game|"
     "pvp_sweat: smithing table, netherite, done|pvp_sweat: every piece goes through the smithing table",
  lkr="end game|cool|useful",
  lkf="end game vibes|so cool",
  tp="tip: four planks and two iron|you need a template for everything now|"
     "netherite upgrades need the netherite template|trims need trim templates|"
     "a toolsmith villager takes it|enchants stay when you upgrade",
  br="upgraded everything to netherite at my smithing table|my smithing table has seen a lot|trimmed my whole set",
  brr="flex|nice",
  brf="end game|fashion",
  cp="i need a template for every upgrade|templates are so rare|netherite needs a template now",
  cps="template pain",
  cpm="one template per upgrade|or seven diamonds to copy",
  cpx="copy the templates|explore bastions|trade|keep looking",
  dg=("the smithing table is the best block|smithing table beats anvil",
      "upgrades|true", "anvils are more useful|it needs templates", "smithing gang", "fair"),
  db=("templates ruined the smithing table|templates are annoying|netherite shouldn't need a template",
      "so annoying|true", "it's a challenge|copy them|it's fine",
      "you need diamonds to copy a template|it's a grind on a grind|it used to be simple",
      "templates are annoying", "it's fine|copy them"))

S("grindstone", "the grindstone", "crafting gear", "crafting gear enchanting",
  m="made a grindstone|ground off a bad enchant|repaired two picks at the grindstone|got some xp from the grindstone",
  mr="handy|nice|clean slate",
  mf="good as new|cheap fix",
  q=["how do you make a grindstone? > two sticks, a stone slab and two planks / sticks, slab, planks",
     "what does a grindstone do? > removes enchants and repairs / takes enchants off, gives some xp",
     "does it remove curses? > no, curses stay / nope"],
  qr="ok|neat",
  lk="the grindstone is underrated|wiping a bad enchant is so satisfying|the grindstone is a lifesaver|"
     "hoarder: i grind every junk book for xp|hoarder: free xp from junk gear",
  lkr="underrated|handy|meh",
  lkf="underrated|handy",
  tp="tip: combine two worn tools for one better one|it strips enchantments, not curses|"
     "you get some xp back|a weaponsmith takes it|"
     "grind enchanted junk from mobs for xp|no levels needed",
  br="ground off ten junk enchants for xp|repaired all my tools for free|my grindstone is always busy",
  brr="efficient|nice",
  brf="free fixes|xp for days",
  cp="i ground off my mending by accident|lost my enchants at the grindstone|grindstone took my good enchant",
  cps="noooo",
  cpm="i meant to repair it|it took everything",
  cpx="check before you grind|use an anvil|be careful|use a book next time",
  dg=("the grindstone is better than the anvil for repairs|grindstone beats anvil",
      "no levels|true", "anvil keeps enchants|grindstone wipes everything", "grind gang", "fair"),
  db=("the grindstone is useless|nobody uses a grindstone|grindstones are pointless",
      "lol|true", "it repairs for free|xp from junk|it's handy",
      "it wipes your enchants|the anvil does it better|you never need it",
      "useless", "free repairs|handy"))

S("cartography_table", "the cartography table", "crafting exploring", "crafting exploring places",
  m="made a cartography table|copied my map at the cartography table|zoomed out my map|locked a map",
  mr="map maker|nice|explorer",
  mf="so useful|maps for days",
  q=["how do you make a cartography table? > two paper and four planks / paper on planks",
     "what does it do? > copy, zoom and lock maps / map stuff",
     "who uses it? > cartographer villagers / cartographers"],
  qr="ok|neat",
  lk="the cartography table is a must for explorers|copying maps is so easy|i love my map room|"
     "nomad: cartography table at every base|nomad: copies for every trip",
  lkr="explorer|niche|map rooms are cool",
  lkf="explorer life|maps are life",
  tp="tip: copy a map with an empty map|zoom out with paper|"
     "lock a map with a glass pane|a cartographer takes it as a job|"
     "cartographers sell explorer maps|zooming out shows more area",
  br="my map room has a cartography table|copied twenty maps for the town|zoomed my map all the way out",
  brr="explorer|nice",
  brf="map king|everyone has a map now",
  cp="i forgot how to zoom out|my map is too zoomed in|my map only shows my house",
  cps="map problems",
  cpm="it shows like ten houses|nothing useful",
  cpx="add paper at the table|use the cartography table|make a new one|check twice",
  dg=("the cartography table beats crafting maps|use the table for maps",
      "easier|true", "crafting works|same thing", "map gang", "fair"),
  db=("cartography tables are pointless|nobody needs a cartography table|maps are enough without it",
      "true|kinda", "zooming|copies|cartographers",
      "make a new map instead|nobody copies maps|who zooms out",
      "pointless", "zooming|copies though"))

S("fletching_table", "the fletching table", "crafting villagers", "crafting villagers trade",
  m="made a fletching table|put a fletching table by a villager|got a fletcher|fletching table for my village",
  mr="fletcher time|nice|arrows",
  mf="sticks for emeralds|village growing",
  q=["what does a fletching table do? > gives a villager the fletcher job / makes a fletcher, that's it",
     "can you use it yourself? > no, it does nothing for you / nope, it's for villagers",
     "what do fletchers buy? > sticks / sticks, for emeralds"],
  qr="huh|lol ok",
  lk="fletchers are the best villagers|selling sticks to a fletcher is easy money|i love my fletcher|"
     "hoarder: sticks for emeralds, infinite money|hoarder: my fletcher made me rich",
  lkr="easy money|it does nothing|fletchers are cool",
  lkf="easy money|fletcher gang",
  tp="tip: fletchers buy sticks for emeralds|two flint and four planks|"
     "it does nothing when you use it|fletchers sell arrows|"
     "tipped arrows from master fletchers|a stick farm is a money farm",
  br="i sell stacks of sticks to my fletcher|my fletcher made me so rich|got tipped arrows from my fletcher",
  brr="rich|nice",
  brf="stick money|easy",
  cp="the fletching table does nothing|i right clicked it and nothing|why can't i use the fletching table",
  cps="lol",
  cpm="it's just for villagers|so pointless",
  cpx="put it by a villager|make a fletcher|use it as decor|sell sticks",
  dg=("fletchers are the best villagers|fletchers beat librarians",
      "sticks|true", "librarians are better|mending", "fletcher gang", "fair"),
  db=("the fletching table is the most useless block|fletching tables do nothing|nobody needs a fletching table",
      "true|lol", "fletchers|stick money|arrows",
      "you can't even use it|it's a fancy table|nothing to click",
      "useless", "fletchers|money"))

S("composter", "the composter", "crafting farming", "farming crafting plants",
  m="made a composter|composted a stack of seeds|got bone meal from the composter|my composter is full",
  mr="green thumb|nice|bone meal",
  mf="free bone meal|farm life",
  q=["how do you make a composter? > seven wooden slabs / slabs in a u",
     "what goes in a composter? > seeds, crops, leaves, flowers / plant stuff",
     "what comes out? > bone meal / bone meal when it's full"],
  qr="ok|nice",
  lk="composting is so satisfying|free bone meal is the best|my composter turns junk into gold|"
     "homebody: compost in the garden|homebody: nothing goes to waste",
  lkr="satisfying|slow|free stuff",
  lkf="nothing wasted|free is free",
  tp="tip: seven wooden slabs|seeds and leaves fill it up|"
     "full composter gives bone meal|a farmer villager takes it|"
     "hoppers can feed it|cake and pies fill it fast",
  br="my composter makes a stack of bone meal a day|composted all my extra seeds|my farm has four composters",
  brr="farmer|nice",
  brf="bone meal for days|nothing wasted",
  cp="composting takes forever|seeds barely fill it|my composter takes so much",
  cps="slow",
  cpm="like thirty seeds for one|so slow",
  cpx="use better stuff|pumpkins fill more|hoppers|kill skeletons for bones",
  dg=("composters beat skeleton farms for bone meal|compost beats bones",
      "renewable|true", "skeletons are faster|too slow", "compost gang", "fair"),
  db=("composters are useless|nobody needs a composter|composting is a waste of time",
      "slow|true", "free bone meal|farmers|it's useful",
      "thirty seeds for one bone meal|kill a skeleton instead|it's slow",
      "useless", "free bone meal|farmers"))

S("barrel", "barrels", "storage crafting", "storage crafting home",
  m="made a stack of barrels|switched my storage to barrels|barrels look so good|put barrels in my kitchen",
  mr="rustic|nice|storage",
  mf="look better than chests|so cozy",
  q=["how do you make a barrel? > six planks and two slabs / planks and slabs",
     "are barrels better than chests? > they open with a block on top / same size, no doubles",
     "do barrels make a fisherman? > yeah / yep, fisherman's job"],
  qr="ok|nice",
  lk="barrels look so much better than chests|barrel walls are the best storage|barrels make a base feel rustic|"
     "builder: barrels sideways look great|builder: barrels in the walls, perfect storage",
  lkr="rustic|small|nice",
  lkf="rustic is the vibe|small is fine",
  tp="tip: barrels open with a block on top|they're the same size as a single chest|"
     "barrels can face any way|a fisherman takes it as a job|"
     "barrels don't make doubles, stack them|cats can't sit on barrels lol",
  br="my storage room is all barrels|i have a wall of barrels|my barrels are labelled",
  brr="rustic|organised",
  brf="looks so good|organised life",
  cp="barrels don't make doubles|i need so many barrels|my barrel wall is messy",
  cps="storage pain",
  cpm="twenty seven slots each|i have like forty",
  cpx="label them|sort them|use chests too|shulker boxes",
  dg=("barrels are better than chests|barrels beat chests",
      "open with blocks on top|true", "chests double|chests look better", "barrel gang", "fair"),
  db=("chests are obsolete now|nobody needs chests with barrels|barrels replaced chests",
      "true|kinda", "double chests|chests are classic|cheaper",
      "barrels open with a block on top|they face any way|same space",
      "barrel gang", "double chests|classic"))

S("chests", "chests", "storage home", "storage home crafting",
  m="crafted a double chest|my chests are all full|made ten new chests|sorted my chests today",
  mr="storage|nice|organised",
  mf="so much stuff|always more",
  q=["how many slots in a double chest? > fifty four / fifty four, two rows of twenty seven",
     "why won't my chest open? > something's on top, a block or a cat / a cat is sitting on it",
     "what's a trapped chest? > a chest that gives a redstone signal / chest plus tripwire hook"],
  qr="ok|lol",
  lk="a row of chests is the most satisfying sight|i love a fresh chest|chest rooms are the best room|"
     "hoarder: chests, more chests, always more|hoarder: a chest for every item",
  lkr="satisfying|hoarder|nice",
  lkf="hoarder and proud|satisfying",
  tp="tip: eight planks make a chest|two side by side make a double|"
     "cats sit on chests and block them|a block on top stops it opening|"
     "label chests with signs|ender chests share between places",
  br="i have a hundred chests|my chest room is perfect|every chest is labelled",
  brr="hoarder|organised",
  brf="nothing is lost|hoarder life",
  cp="my chests are a mess|i can't find anything in my chests|everything is in random chests",
  cps="chest monster",
  cpm="i spend ages looking|everything everywhere",
  cpx="sort them|label them|a chest per item|item frames",
  dg=("you can never have too many chests|more chests is always better",
      "true|always", "sort them instead|clutter", "chest gang", "fair"),
  db=("chest monsters are the worst|unsorted chests are a crime|people who don't sort chests are chaos",
      "true|so true", "it works for me|i know where stuff is|sorting is boring",
      "you spend more time looking than playing|one chest per thing|signs, frames, sort it",
      "sort it", "it works|chaos is fine"))

S("shulker_boxes", "shulker boxes", "storage gear", "storage gear exploring", gate="end",
  m="made a shulker box|filled a shulker box with blocks|got shulker shells from the end city|dyed my shulker boxes",
  mr="portable storage|rich|nice",
  mf="game changer|so useful",
  q=["how do you make a shulker box? > two shulker shells and a chest / shells around a chest",
     "where do shells come from? > shulkers in end cities / end cities",
     "do shulker boxes keep items when broken? > yeah, everything stays inside / yep"],
  qr="ok|whoa",
  lk="shulker boxes are the best item|shulkers changed storage forever|portable chests are amazing|"
     "hoarder: shulker of everything|hoarder: shulkers inside shulkers, no wait",
  lkr="game changer|rare|so useful",
  lkf="game changer|useful",
  tp="tip: shulker boxes keep items when you break them|two shells and a chest|"
     "dye them to sort|shells drop from shulkers in end cities|"
     "bring a shulker on long trips|you can't put a shulker in a shulker",
  br="i have twenty shulker boxes|every colour of shulker|my whole base is in shulkers",
  brr="rich|organised",
  brf="portable base|organised",
  cp="i need more shulker shells|shulkers never drop shells for me|shulker shells are so rare",
  cps="shell pain",
  cpm="half the shulkers drop nothing|i need so many",
  cpx="looting|more end cities|keep hunting|be patient",
  dg=("shulker boxes are the best storage|nothing beats shulker boxes",
      "portable|true", "too rare|chests work", "shulker gang", "fair"),
  db=("shulker boxes are the best item in the game|nothing is better than shulkers|shulkers beat elytra",
      "portable storage|true", "elytra is better|mending|too rare",
      "carry ten chests in one slot|move house in minutes|nothing saves more time",
      "shulker gang", "elytra though|mending"))

S("hoppers", "hoppers", "storage crafting", "storage farming crafting",
  m="made a few hoppers|set up a hopper into my furnace|my hopper is moving items|hoppers under my farm",
  mr="automation|nice|smart",
  mf="no more clicking|so handy",
  q=["how do you make a hopper? > five iron and a chest / iron around a chest",
     "what does a hopper do? > moves items into a container / it pulls items down",
     "can you stop a hopper? > redstone signal locks it / power it and it stops"],
  qr="ok|neat",
  lk="hoppers are the best automation|i love a hopper setup|hoppers make everything easier|"
     "hoarder: hoppers collect everything for me|hoarder: hoppers under every farm",
  lkr="smart|expensive|automation",
  lkf="smart life|worth it",
  tp="tip: five iron and a chest|point it at the chest you want|"
     "hoppers into a furnace auto smelt|hoppers under farms catch drops|"
     "a hopper minecart grabs items through blocks|they move two or three items a second",
  br="my farm has a hopper line|built an auto smelter with hoppers|my hoppers do all the work",
  brr="smart|automation",
  brf="i just collect|automation life",
  cp="my hopper points the wrong way|placed my hopper backwards|my hopper filled the wrong chest",
  cps="hopper pain",
  cpm="i placed it facing sideways|everything went to the wrong place",
  cpx="sneak when you place|check the spout|redo it|use a bit of redstone to lock it",
  dg=("hoppers are worth the iron|every base needs hoppers",
      "automation|true", "too expensive|manual is fine", "hopper gang", "fair"),
  db=("hoppers are the best invention|nothing beats hoppers|hoppers changed the game",
      "automation|true", "too expensive|manual is fine|they're fiddly",
      "farms collect themselves|smelting runs itself|you just collect",
      "hopper gang", "too expensive|manual"))

S("beds", "beds", "sleep home", "sleep home night",
  m="made a new bed|put my bed in a new spot|crafted a blue bed|slept in my own bed last night",
  mr="cozy|nice|sweet dreams",
  mf="spawn set|so comfy",
  q=["how do you make a bed? > three wool and three planks / wool on planks",
     "why can't i sleep? > monsters nearby / mobs too close",
     "can you sleep in the nether? > no, beds explode / never, it blows up"],
  qr="ok|whoa",
  lk="my bed is my favourite block|a cozy bed makes a home|i love setting my spawn|"
     "homebody: my bed is my castle|homebody: same bed every night",
  lkr="cozy|home|comfy",
  lkf="home is a bed|comfy",
  tp="tip: three wool and three planks|beds set your spawn point|"
     "you can't sleep with monsters near|beds explode in the nether and the end|"
     "sleep to keep phantoms away|you can sleep in thunderstorms",
  br="my bed has the best view|i have a bed in every base|my bed is in a tower",
  brr="cozy|fancy",
  brf="best sleep ever|home is everywhere",
  cp="my bed got blown up|a creeper blew up my bed|forgot to set my spawn",
  cps="ugh",
  cpm="and i respawned at world spawn|miles from home",
  cpx="keep a spare bed|sleep in it once|wall your house|put it somewhere safe",
  dg=("sleeping every night is a must|always sleep",
      "phantoms|true", "night is fun|it's fine", "sleep gang", "fair"),
  db=("beds are the most important item|without a bed you're lost|a bed beats any gear",
      "spawn|true", "armor is more important|a sword|food",
      "spawn point, night skip, no phantoms|die without one and you start at spawn|it's home",
      "bed gang", "armor|food"))

S("boats", "boats", "travel ocean crafting", "ocean exploring travel",
  m="crafted a boat|took my boat down the river|made a boat with a chest|boated across the ocean",
  mr="sailor|nice|ahoy",
  mf="so fast on water|smooth sailing",
  q=["how do you make a boat? > five planks in a u / planks in a u shape",
     "how many people fit in a boat? > two / two, or one and a mob",
     "what's a chest boat? > a boat with a chest on it / boat plus chest"],
  qr="ok|cool",
  lk="boats are the best way to travel|sailing at sunset is perfect|i love my boat|"
     "nomad: river after river in my boat|nomad: the ocean is a road",
  lkr="sailor|slow|peaceful",
  lkf="peaceful|sailor life",
  tp="tip: five planks make a boat|boats go super fast on ice|"
     "mobs get stuck in boats|a chest boat carries storage|"
     "landing in a boat stops fall damage|break your boat and it drops itself",
  br="sailed across three oceans|i have a boat for every river|trapped a villager in a boat",
  brr="sailor|lol",
  brf="sea life|i'm a captain",
  cp="boats get stuck on everything|my boat got stuck on a beach|my boat keeps hitting rocks",
  cps="boat pain",
  cpm="i walked the rest|it hit a rock",
  cpx="carry a spare|go slow|go around|steer around the shore",
  dg=("boats are the best transport|boats beat horses",
      "smooth|true", "horses go on land|rails are better", "boat gang", "fair"),
  db=("boats are the most underrated item|nobody uses boats enough|boats are secretly the best",
      "true|underrated", "horses are faster|land is bigger|meh",
      "rivers go everywhere|they work on ice too|five planks",
      "boat gang", "horses|meh"))

S("minecarts", "minecarts and rails", "travel crafting exploring", "travel exploring crafting",
  m="laid down a rail line|rode a minecart to the mine|made powered rails|my rail line is two hundred blocks",
  mr="choo choo|nice|rail gang",
  mf="so fast|engineering",
  q=["how do you make rails? > six iron and a stick, sixteen rails / iron and a stick",
     "how do you make a minecart? > five iron / five iron in a u",
     "how often do you need powered rails? > every thirty blocks or so / a few per long line"],
  qr="ok|nice",
  lk="rail lines are the best travel|riding a minecart is so fun|i love building rail lines|"
     "miner: rails all the way to the mine|miner: minecart down, ore up",
  lkr="fun|expensive|choo choo",
  lkf="worth it|choo choo",
  tp="tip: six iron and a stick make sixteen rails|powered rails need gold and redstone|"
     "space powered rails along the line|mineshafts have free rails|"
     "hopper minecarts collect items|a chest minecart carries stuff",
  br="my rail line goes across the map|built a station at my house|i have rails to every village",
  brr="engineer|nice",
  brf="choo choo|took forever",
  cp="rails need so much iron|my rail line eats all my iron|i ran out of gold for powered rails",
  cps="rail pain",
  cpm="two hundred blocks of track|i need more",
  cpx="loot mineshafts for rails|iron farm|fewer powered rails|use boats",
  dg=("rails are the best transport|rails beat horses",
      "steady|true", "too expensive|horses are free", "rail gang", "fair"),
  db=("rail lines are a waste of iron|minecarts aren't worth it|nobody needs rails",
      "expensive|true", "they're fun|fast|automation",
      "horses are free|boats are cheaper|iron for armor",
      "save the iron", "fun though|rails forever"))

# ======================================================================= Doings: ways of mining
S("branch_mining", "branch mining", "mining diamonds ore", "mining diamonds ore",
  m="did some branch mining today|started a new branch mine|my branch mine is getting long|dug like ten branches today",
  mr="the grind|classic|any diamonds? > not yet / a couple / two so far",
  mf="slow and steady|nothing yet",
  q=["what level do you branch mine at? > around y minus fifty eight for diamonds / low, just above the lava level",
     "how far apart should the branches be? > two blocks between them / every third block",
     "is branch mining better than caving? > safer, slower / safer, caves show more ore"],
  qr="ok|got it",
  lk="branch mining is so relaxing|i love a long straight tunnel|branch mining is pure zen|"
     "miner: a perfect branch mine is art|miner: me, my pick, and a long tunnel",
  lkr="boring|zen|relaxing",
  lkf="zen mode|boring is calm",
  tp="tip: diamonds are best around y minus fifty eight|leave two blocks between branches|"
     "watch for lava below y minus fifty five|bring a water bucket for lava|"
     "put torches on one side so you know the way back|efficiency makes it fly",
  tpn="a:voice_miner",
  br="found eight diamonds branch mining today|my branch mine is five hundred blocks|perfect branches, all lit",
  brr="nice|jealous",
  brf="patience pays|grind pays off",
  cp="branch mining for hours and nothing|my branch mine has zero diamonds|so much cobble, no ores",
  cps="unlucky",
  cpm="three hours of tunnels|just cobble and tuff",
  cpx="go lower|try caving|keep going|move to a new area",
  cpn="a:voice_miner",
  st=("good", "so i was branch mining for ages|story: i was on my twentieth branch|i'd been branch mining all day"
               ">> about to give up and go home|my pick was almost broken"
               ">> broke one more block and it was diamonds|found a vein of six diamonds|last swing, diamonds"),
  dg=("branch mining is the best way to find diamonds|branch mining beats caving",
      "safe|true", "caving is faster|too slow", "branch gang", "fair"),
  db=("branch mining is a waste of time now|caving finds way more|nobody should branch mine anymore",
      "caves are huge now|true", "it's safer|caves are dangerous|diamonds hide in stone",
      "caves show you everything|branch mining is hours of cobble|the caves are huge now",
      "cave gang", "safer though|still branch"))

S("caving", "caving", "caves mining danger", "caves mining danger",
  m="went caving today|found a huge cave system|explored a cave for hours|caving is so fun",
  mr="brave|nice|caves!",
  mf="ores everywhere|so big",
  q=["is caving dangerous? > yeah, mobs everywhere, light it up / bring torches and a water bucket",
     "what should i bring caving? > torches, food, a water bucket / torches and a shield",
     "what are the big caves called? > cheese caves, the huge open ones / cheese, spaghetti, noodle caves"],
  qr="ok|whoa",
  lk="caving is the best part of the game|i love finding a new cave|caves are so beautiful now|"
     "miner: nothing beats a fresh cave|miner: caves are my home",
  lkr="scary|beautiful|caves are great",
  lkf="scary is fun|beautiful",
  tp="tip: always carry a water bucket for falls|light every corner you pass|"
     "torches on the right going in|bring a shield for skeletons|"
     "lush caves are under azaleas|listen for mobs before you go in",
  tpn="a:voice_miner",
  br="mapped out a whole cave system|found a lush cave and a dripstone cave in one trip|came back with stacks of ore",
  brr="explorer|nice",
  brf="caves are my thing|ore everywhere",
  cp="got lost in a cave|got lost underground again|i can't find my way out of caves",
  cps="caves are rough",
  cpm="it all looks the same|every tunnel is the same",
  cpx="mark your path|torches on one side|pillar up to the surface|dig up carefully",
  st=("bad", "so i was caving deep down|story: i was exploring a cave|i went into a huge cave"
              ">> heard a hiss behind me|a creeper came out of the dark"
              ">> blew me off a ledge into lava|knocked me into a ravine|died and lost everything"),
  dg=("caving beats branch mining|caving is better than strip mining",
      "faster|true", "too dangerous|branch is safer", "cave gang", "fair"),
  db=("caving is too dangerous to be worth it|caving is a death wish|caves aren't worth the risk",
      "true|risky", "the loot is worth it|that's the fun|just be careful",
      "skeletons, creepers, lava, falls|one mistake and it's all gone|stay safe, branch mine",
      "too risky", "worth it|that's the fun"))

S("strip_mining", "strip mining", "mining ore building", "mining ore building",
  m="started strip mining a whole layer|cleared out a huge area underground|my quarry is getting huge|strip mined a chunk",
  mr="ambitious|whoa|big dig",
  mf="so much stone|no ore escapes",
  q=["what's strip mining? > clearing out whole layers / digging out everything in an area",
     "is strip mining worth it? > you get every ore, but it takes ages / thorough, slow",
     "what do you do with the stone? > build with it, or toss it / store it, build huge"],
  qr="ok|whoa",
  lk="strip mining is so satisfying|clearing a whole area feels amazing|nothing escapes a strip mine|"
     "miner: strip mining with haste two is heaven|miner: a clean quarry is beautiful",
  lkr="satisfying|so slow|thorough",
  lkf="slow is fine|thorough",
  tp="tip: haste from a beacon makes it way faster|bring shulkers or chests for the stone|"
     "light the floor as you go|watch for lava and water|"
     "efficiency five pick is a must|work in layers from top to bottom",
  tpn="a:voice_miner",
  br="i cleared a whole chunk to bedrock|my quarry is a hundred blocks wide|found every ore in the area",
  brr="crazy|whoa",
  brf="took weeks|every block",
  cp="strip mining takes forever|my inventory fills in minutes|so much stone, so little ore",
  cps="grind",
  cpm="i'm drowning in cobble|days of digging",
  cpx="get haste|bring shulkers|toss the cobble|use tnt",
  dg=("strip mining is the most thorough way|strip mining beats branch mining",
      "every ore|true", "too slow|ugly hole", "strip gang", "fair"),
  db=("strip mining ruins the world|strip mines are ugly holes|nobody should strip mine",
      "ugly|true", "it's underground|who cares|it's efficient",
      "giant holes everywhere|the world looks scarred|branch mining leaves the world alone",
      "leave the world", "it's underground|efficient"))

# ======================================================================= Doings: ways of building
S("medieval_build", "medieval builds", "building decor", "building decor town",
  m="building a medieval house|did some timber framing today|my medieval street is coming together|built a medieval tavern",
  mr="old vibes|nice|timber frame",
  mf="so cozy|it's coming along",
  q=["how do you do timber framing? > stripped logs as beams, white blocks between / logs and white walls",
     "what's the best medieval roof? > steep dark oak or spruce stairs / steep and dark",
     "what blocks for medieval? > cobble base, spruce, white walls / stone, spruce, dark oak"],
  qr="ok|nice",
  lk="medieval builds are the coziest|timber frames are so pretty|i love a medieval street|"
     "builder: overhanging upper floors, the jetty, perfect|builder: cobble base, timber top, steep roof",
  lkr="cozy|classic|so pretty",
  lkf="classic for a reason|cozy",
  tp="tip: stripped logs for beams, white walls between|make the upper floor stick out|"
     "steep roofs look more medieval|cobble or stone at the base|"
     "lanterns on fences for streets|mix spruce and dark oak",
  tpn="a:voice_builder",
  br="built a whole medieval town|my tavern has a sign and everything|finished a timber frame house",
  brr="cozy|nice",
  brf="took forever|feels like a story",
  cp="my timber frame looks flat|medieval roofs are so hard|my medieval house looks like a box",
  cps="building pain",
  cpm="it just looks plain|no depth at all",
  cpx="add depth, push the beams out|overhang the second floor|steeper roof|add trapdoors",
  cpn="a:voice_builder",
  dg=("medieval is the best building style|nothing beats medieval",
      "cozy|true", "modern is cleaner|rustic is better", "medieval gang", "fair"),
  db=("medieval builds are overdone|everyone builds medieval|medieval is boring now",
      "so many|true", "it's classic|it fits minecraft|it's cozy",
      "every town is a medieval street|try something new|timber frames everywhere",
      "try something new", "classic though|it fits"))

S("modern_build", "modern builds", "building decor", "building decor town",
  m="building a modern house|my modern house is all white|added a glass wall to my build|built a modern villa",
  mr="clean|fancy|modern",
  mf="so clean|coming along",
  q=["what blocks for modern builds? > white concrete, glass, quartz / concrete and glass",
     "do modern houses need roofs? > flat roofs are the style / flat, clean",
     "how do you make it not look like a box? > layers, different heights / overhangs and depth"],
  qr="ok|nice",
  lk="modern builds are so clean|white and glass is perfect|i love a modern villa|"
     "builder: concrete and glass, clean lines|builder: a modern house with a pool, chef's kiss",
  lkr="clean|boxy|fancy",
  lkf="clean is the point|boxes can be art",
  tp="tip: white concrete and glass are the base|use different heights so it's not a box|"
     "big windows are a must|flat roofs fit modern|"
     "add plants for contrast|dark trim around white walls",
  tpn="a:voice_builder",
  br="built a modern mansion|my modern house has a pool|my glass walls look amazing",
  brr="fancy|clean",
  brf="took so much concrete|it's so clean",
  cp="my modern house looks like a box|modern builds look so empty|my white walls look plain",
  cps="boxes",
  cpm="it's just a white cube|no detail",
  cpx="add layers|different heights|add trim|plants",
  cpn="a:voice_builder",
  dg=("modern builds are the best style|modern beats medieval",
      "clean|true", "medieval is cozier|too boxy", "modern gang", "fair"),
  db=("modern builds don't belong in minecraft|modern houses look wrong here|modern builds ruin the vibe",
      "true|kinda", "they look great|it's style|it's creative",
      "white cubes next to villages|nothing fits around it|it looks like a different game",
      "doesn't fit", "it's style|creative"))

S("rustic_build", "rustic builds", "building decor home", "building decor home",
  m="built a rustic cabin|my rustic house has barrels everywhere|added hay bales to my build|logs and cobble, rustic as it gets",
  mr="cozy|cabin|rustic",
  mf="so cozy|perfect",
  q=["what makes a build rustic? > logs, cobble, barrels, hay / natural stuff, logs and stone",
     "what wood is the most rustic? > spruce or oak logs / spruce",
     "what decor is rustic? > barrels, lanterns, hay, flower pots / hay bales and barrels"],
  qr="ok|nice",
  lk="rustic builds are the coziest|log cabins are my style|i love a rustic farmhouse|"
     "builder: barrels and lanterns, rustic heaven|builder: hay bales on a porch",
  lkr="cozy|cabin|nice",
  lkf="cozy is everything|cabin life",
  tp="tip: logs for the frame, planks inside|cobble at the base|"
     "barrels and hay bales for clutter|lanterns, not torches|"
     "a porch makes it rustic|mix stripped and normal logs",
  tpn="a:voice_builder",
  br="my farmhouse is so rustic|built a log cabin by the lake|my porch has hay and barrels",
  brn="a:has_house",
  brr="cozy|nice",
  brf="feels like home|so cozy",
  cp="my rustic build looks messy|too many logs, it looks heavy|my cabin looks like a crate",
  cps="build pain",
  cpm="it's all brown|no contrast",
  cpx="add stone|lighter wood inside|windows|flowers",
  dg=("rustic is the coziest style|nothing is cozier than rustic",
      "cabin|true", "medieval is cozier|cottagecore", "rustic gang", "fair"),
  db=("rustic builds all look the same|every rustic build is a log cabin|rustic is boring",
      "true|kinda", "they're cozy|it's classic|it fits",
      "logs, barrels, hay, done|every one is brown|try a new palette",
      "same old cabin", "cozy though|classic"))

S("castles", "castles", "building structures", "building town fighting",
  m="started building a castle|my castle wall is done|built a castle tower today|working on a gatehouse",
  mr="king|whoa|big project",
  mf="took forever|so many blocks",
  q=["how many blocks does a castle take? > stacks and stacks / way more than you think",
     "what blocks for a castle? > stone bricks, deepslate, cobble / stone bricks mostly",
     "do castles need a moat? > for looks, yeah / a moat is the best part"],
  qr="ok|whoa",
  lk="castles are the best builds|i love a big castle wall|towers are my favourite part|"
     "builder: battlements with walls on top|builder: a gatehouse with a portcullis of fences",
  lkr="epic|big|so much work",
  lkf="epic is right|worth the work",
  tp="tip: walls on top make battlements|mix stone bricks with cracked and mossy|"
     "round towers look better than square|a moat keeps mobs out|"
     "plan it on the ground first|deepslate for a dark castle",
  tpn="a:voice_builder",
  br="my castle has four towers|built a castle on a mountain|my castle wall goes around the town",
  brr="king|epic",
  brf="took months|my kingdom",
  cp="castles take so many blocks|my castle is taking forever|ran out of stone bricks again",
  cps="castle pain",
  cpm="stacks and stacks|still not done",
  cpx="cobble generator|smelt more|get help|smaller castle",
  st=("funny", "so i built a huge castle|story: i spent a month on a castle|i finally finished my castle"
                ">> moat, towers, the whole thing|walls, a gate, a moat, everything"
                ">> then i realised i forgot a door|and i built it in the middle of a swamp|a creeper spawned inside on day one"),
  dg=("castles are the best thing to build|nothing beats a castle",
      "epic|true", "too much work|houses are cozier", "castle gang", "fair"),
  db=("castles are a waste of blocks|nobody needs a castle|castles are pointless",
      "true|kinda", "they're epic|protection|it's fun",
      "you live in one room anyway|stacks of stone for nothing|a house does the same",
      "pointless", "epic though|it's fun"))

S("treehouses", "treehouses", "building trees home", "building trees home",
  m="building a treehouse|my treehouse has a bridge now|built up a jungle tree|my treehouse is so high",
  mr="cool|up high|fun",
  mf="the view is great|mobs can't reach me",
  q=["what tree is best for a treehouse? > big jungle trees / jungle or dark oak",
     "how do you get up? > ladders or a spiral stair / ladders on the trunk",
     "do leaves decay in a treehouse? > not natural ones next to logs / ones you place don't decay"],
  qr="ok|nice",
  lk="treehouses are the best homes|living in the trees is the dream|treehouse bridges are so fun|"
     "builder: spiral stairs around the trunk|builder: rope bridges with fences",
  lkr="fun|dangerous|so cool",
  lkf="fun and high|dangerous is fun",
  tp="tip: giant jungle trees are perfect|use ladders on the trunk|"
     "fences make good rope bridges|leaves you place never decay|"
     "light the platforms, mobs spawn up there|don't fall, bring a water bucket",
  tpn="a:voice_builder",
  br="my treehouse has three levels|built a treehouse village|my treehouse has a zip line, kind of",
  brr="cool|fun",
  brf="best view|up in the clouds",
  cp="my treehouse burned down|my treehouse caught fire|lightning hit my treehouse",
  cps="ouch",
  cpm="all that wood|it went up so fast",
  cpx="build railings|keep fire away|lightning rod|carry a water bucket",
  dg=("treehouses are the best homes|nothing beats a treehouse",
      "views|true", "too dangerous|they burn", "tree gang", "fair"),
  db=("treehouses are the most fun build|nothing is more fun than a treehouse|treehouses beat castles",
      "true|fun", "castles|too dangerous|they burn",
      "ladders, bridges, views|you feel like a kid|mobs can't climb up",
      "tree gang", "castles though|dangerous"))

S("underground_base", "underground bases", "building caves home", "building caves home",
  m="dug out an underground base|my base is under a mountain|built a hidden room underground|finished my bunker",
  mr="secret|cozy|hidden",
  mf="nobody knows it's there|so safe",
  q=["is an underground base safe? > if you light it up / yeah, light it and it's safe",
     "how do you get light down there? > glowstone, lanterns, skylights / lanterns and a glass ceiling",
     "how do you hide the entrance? > piston door or a hidden stair / a trapdoor and a carpet"],
  qr="ok|nice",
  lk="underground bases are so cozy|hidden bases are the best|i love living in a mountain|"
     "miner: home is where the stone is|miner: my base is my mine",
  lkr="cozy|dark|secret",
  lkf="secret is safe|cozy",
  tp="tip: light every block, mobs spawn in the dark|a glass skylight helps|"
     "dig into a mountain side for a window|keep a farm under glass|"
     "hide the entrance with a trapdoor|watch for water and lava when you dig",
  br="my base is fifty blocks underground|built a hidden base in a mountain|nobody's found my bunker",
  brr="secret|nice",
  brf="safe forever|hidden",
  cp="mobs spawned in my underground base|a creeper spawned in my bunker|zombies in my underground base again",
  cps="underground pain",
  cpm="i forgot a corner|it's so dark down there",
  cpx="more lights|glass skylight|check every corner|go up more",
  dg=("underground bases are the safest|nothing is safer than underground",
      "hidden|true", "mobs spawn down there|you need light", "bunker gang", "fair"),
  db=("living underground is depressing|underground bases are sad|nobody should live underground",
      "no sun|true", "it's cozy|it's safe|mountains are cool",
      "no sky, no sun, no view|you forget what time it is|you turn into a mole",
      "go outside", "it's cozy|safe"))

S("interior_decor", "interior decor", "decor home building", "decor home building",
  m="decorated my living room|made a couch out of stairs|put item frames up|added bookshelves to my house",
  mr="cozy|nice|fancy",
  mf="feels lived in|getting there",
  q=["how do you make a chair? > a stair with signs or trapdoors on the side / stairs and trapdoors",
     "how do you make a table? > a fence with a pressure plate / fence and a carpet",
     "what makes a room feel cozy? > carpets, lanterns, plants / clutter, lights and rugs"],
  qr="ok|neat",
  lk="decorating is my favourite part|a decorated room feels alive|i love making furniture|"
     "builder: trapdoors on everything|builder: a room needs three kinds of light",
  lkr="cozy|clutter|nice",
  lkf="cozy wins|clutter is cozy",
  tp="tip: stairs with trapdoors make armchairs|fence and a carpet makes a table|"
     "flower pots everywhere|item frames show off stuff|"
     "chiseled bookshelves look great|candles on tables",
  tpn="a:voice_builder",
  br="my kitchen has a full counter|my living room is perfect|built a library with chiseled bookshelves",
  brn="a:has_house",
  brr="cozy|fancy",
  brf="took ages|every detail",
  cp="my house is so empty inside|i don't know how to decorate|my rooms look bare",
  cps="empty rooms",
  cpm="just walls and a bed|so bare",
  cpx="add carpets|flower pots|make furniture|lanterns",
  cpn="a:has_house",
  dg=("the inside matters more than the outside|interior beats exterior",
      "you live inside|true", "outside first|people see the outside", "inside gang", "fair"),
  db=("decorating is a waste of time|nobody needs furniture|decor is pointless",
      "true|kinda", "it's home|it's cozy|you live there",
      "you can't sit on stairs|a bed and chests is all you need|it does nothing",
      "pointless", "it's home|cozy matters"))

S("roofs", "roofs", "building decor", "building decor home",
  m="finished a roof today|redid my roof with stairs|my roof finally looks right|added an overhang to my roof",
  mr="roofs are hard|nice|finally",
  mf="took forever|looks so much better",
  q=["stairs or slabs for a roof? > stairs for steep, slabs for gentle / both, mix them",
     "how steep should a roof be? > forty five degrees with stairs / steeper looks medieval",
     "how do you make a roof not look flat? > overhang and trim / add an overhang"],
  qr="ok|nice",
  lk="a good roof makes the whole house|i love a steep roof|roofs are my favourite part|"
     "builder: a roof needs an overhang, always|builder: mixing stairs and slabs for a smooth slope",
  lkr="hard|true|nice",
  lkf="hard but worth it|true",
  tp="tip: always overhang the roof by one block|mix stairs and slabs for gentle slopes|"
     "use a different colour from the walls|dark roofs on light walls|"
     "a gable roof is easiest|trim the edge with a different block",
  tpn="a:voice_builder",
  br="my roof has three layers|built a perfect roof|my roof is the best in town",
  brr="nice|roof master",
  brf="took three tries|perfect",
  cp="roofs are so hard|my roof looks wrong|i hate building roofs",
  cps="roofs are tough",
  cpm="the corners never work|it looks like a box",
  cpx="overhang it|use stairs|copy a roof you like|start with a gable",
  cpn="a:voice_builder",
  dg=("the roof is the most important part|a roof makes or breaks a house",
      "true|so true", "the walls matter more|the inside", "roof gang", "fair"),
  db=("steep roofs are always better|shallow roofs are ugly|steep roofs win",
      "true|classic", "shallow is fine|flat roofs exist|depends",
      "steep looks finished|shallow looks lazy|every good house has a steep roof",
      "steep gang", "flat roofs|depends"))

S("gardens", "gardens", "plants decor home", "plants decor home",
  m="planted a flower garden|my garden is blooming|added a pond to my garden|made a hedge out of leaves",
  mr="pretty|nice|green thumb",
  mf="bees love it|so pretty",
  q=["how do you get lots of flowers? > bone meal on grass / bone meal the grass",
     "do placed leaves decay? > no, only natural ones / leaves you place stay forever",
     "what flowers attract bees? > any flowers / all flowers"],
  qr="ok|nice",
  lk="gardens are my favourite thing to build|flowers make a house a home|my garden is my happy place|"
     "homebody: watering my flowers every morning|homebody: my garden is my pride",
  lkr="pretty|so nice|green",
  lkf="pretty is everything|green is good",
  tp="tip: bone meal grass for random flowers|placed leaves never decay|"
     "azaleas make nice bushes|flower pots for indoors|"
     "bees love flowers, bring a hive|ponds with lily pads",
  br="my garden has every flower|built a garden maze|my garden has bees and a pond",
  brr="pretty|nice",
  brf="took forever|my pride",
  cp="a creeper blew a hole in my flower beds|a creeper blew up my garden|my flowers got trampled",
  cps="nooo",
  cpm="all my flowers gone|hours of work",
  cpx="fence it|light it|replant|bone meal",
  dg=("every house needs a garden|a house without a garden is sad",
      "pretty|true", "too much work|not every house", "garden gang", "fair"),
  db=("gardens are the most underrated build|nobody builds gardens enough|gardens beat houses",
      "true|underrated", "houses matter more|too much work|meh",
      "flowers make everything better|bees, ponds, paths|you can't live in a garden but you want to",
      "garden gang", "houses|meh"))

S("paths", "paths", "building town", "building town decor",
  m="made a path to my house|laid a gravel path|made dirt paths with my shovel|connected my house to the road",
  mr="nice|tidy|paths!",
  mf="no more getting lost|looks so good",
  q=["how do you make a dirt path? > shovel on grass / right click grass with a shovel",
     "what's the best path block? > dirt path with gravel mixed / mix coarse dirt and gravel",
     "should paths have lights? > yeah, every few blocks / lanterns on posts"],
  qr="ok|nice",
  lk="paths make the world feel lived in|a good path is so satisfying|i love winding paths|"
     "builder: mixed block paths look natural|builder: path edges with slabs",
  lkr="tidy|nice|winding is pretty",
  lkf="tidy world|pretty",
  tp="tip: shovel on grass makes a path|mix gravel, coarse dirt and path blocks|"
     "lanterns along the path keep it safe|winding paths look natural|"
     "slabs on the edges|paths are slightly lower than grass",
  br="built a path to every house in town|my path is lit all the way|made a path to the mine",
  brr="tidy|nice",
  brf="everyone uses it|so tidy",
  cp="my path is so straight and boring|my path looks like a line|my road is so plain",
  cps="path pain",
  cpm="it just looks like a line|meh",
  cpx="make it wind|mix blocks|add flowers|edge it",
  dg=("winding paths are better than straight ones|curvy paths beat straight",
      "natural|true", "straight is faster|straight is clean", "winding gang", "fair"),
  db=("every town needs proper roads|towns without roads are a mess|roads make a town",
      "true|tidy", "paths are enough|it's fine|nature is fine",
      "you get lost without roads|it connects everyone|it looks finished",
      "road gang", "paths are enough|nature"))

# ======================================================================= Doings: farming
S("wheat_farm", "the wheat farm", "farming plants food", "farming food animals",
  m="harvested my wheat field|planted a new row of wheat|my wheat is almost ready|got a stack of seeds from the grass",
  mr="farmer|nice|golden fields",
  mf="bread soon|so many seeds",
  q=["how close does water need to be? > four blocks / within four blocks of the farmland",
     "do crops need light? > yeah, they stop growing in the dark / yep, light them at night",
     "where do you get seeds? > break grass / punch tall grass"],
  qr="ok|got it",
  lk="a full wheat field is so pretty|harvesting wheat is relaxing|i love a golden field|"
     "homebody: tending my wheat every morning|homebody: my wheat field is my pride",
  lkr="pretty|relaxing|golden",
  lkf="relaxing life|golden",
  tp="tip: water reaches four blocks out|one water block in a nine by nine field covers it all|"
     "don't jump on farmland, it turns back to dirt|light the field so it grows at night|"
     "nine wheat makes a hay bale|bone meal makes it grow faster",
  br="my wheat farm is a hundred blocks|i have twenty stacks of wheat|my farm feeds the whole town",
  brr="farmer|nice",
  brf="bread for everyone|farm life",
  cp="i trampled my own farmland|jumped on my farm again|i keep trampling my crops",
  cps="farm pain",
  cpm="i jumped and it went back to dirt|the whole row is gone",
  cpx="don't jump on it|walk around|sneak across it|replant",
  st=("funny", "so i planted my first wheat farm|story: my first farm ever|i made a wheat farm"
                ">> got excited and ran through it to check|jumped across it to look"
                ">> trampled every single plant|turned it all back to dirt|replanted the whole thing lol"),
  dg=("wheat is the best crop|wheat beats every crop",
      "bread and breeding|true", "potatoes are better|carrots are better", "wheat gang", "fair"),
  db=("every base needs a wheat farm|you can't survive without wheat|wheat is essential",
      "true|bread", "potatoes are better|meat is better|not essential",
      "bread, breeding, hay|cows and sheep need it|it's the start of everything",
      "wheat gang", "potatoes|meat"))

S("carrot_farm", "the carrot farm", "farming plants food", "farming food animals",
  m="harvested my carrots|planted a row of carrots|got carrots from a zombie|my carrot farm is growing",
  mr="farmer|nice|orange",
  mf="pigs will love it|so many",
  q=["where do you get carrots? > villages or zombies / village farms",
     "what animals like carrots? > pigs and rabbits / pigs",
     "do carrots replant themselves? > plant one, get more back / you plant the carrot itself"],
  qr="ok|nice",
  lk="carrots are the best crop|my carrot farm is so neat|carrots feed everything|"
     "homebody: carrots in my garden|homebody: fresh carrots every day",
  lkr="neat|nice|orange",
  lkf="neat rows|orange gang",
  tp="tip: plant the carrot itself, no seeds|pigs breed with carrots|"
     "golden carrots need carrots and gold|farmers buy carrots for emeralds|"
     "carrot on a stick for pigs|zombies drop carrots sometimes",
  br="i have ten stacks of carrots|sold carrots to farmers for a stack of emeralds|my carrot farm is huge",
  brr="rich|farmer",
  brf="emerald farm|carrot king",
  cp="i can't find carrots anywhere|no village near me for carrots|zombies never drop carrots",
  cps="carrot hunt",
  cpm="i've looked everywhere|no luck",
  cpx="find a village|kill more zombies|trade for them|check shipwrecks",
  dg=("carrots are better than potatoes|carrots beat potatoes",
      "golden carrots|true", "potatoes fill more|same thing", "carrot gang", "fair"),
  db=("carrots are the best crop for emeralds|carrot farms make the most money|carrots are money",
      "farmers love them|true", "pumpkins pay more|sugar cane|wheat",
      "farmers buy them all day|grow fast, sell fast|they replant themselves",
      "carrot gang", "pumpkins|sugar cane"))

S("sugar_cane", "sugar cane", "farming plants crafting", "farming crafting trade",
  m="harvested my sugar cane|planted sugar cane by the river|made a stack of paper|my sugar cane farm is getting tall",
  mr="paper time|nice|tall",
  mf="books soon|so much paper",
  q=["where does sugar cane grow? > next to water on sand, dirt or grass / beside water",
     "how do you make paper? > three sugar cane / three cane in a row",
     "does bone meal work on sugar cane? > no / nope, you have to wait"],
  qr="ok|huh",
  lk="sugar cane farms are so easy|paper means books, books means enchanting|i love a sugar cane river|"
     "hoarder: paper by the chest|hoarder: you can never have enough paper",
  lkr="easy|boring|useful",
  lkf="easy is good|useful",
  tp="tip: plant it right next to water|break the second block so it regrows|"
     "three cane make three paper|librarians buy paper|"
     "bone meal does nothing on it|sugar for cakes and potions",
  br="i have twenty stacks of paper|my sugar cane farm lines the whole river|sold paper for so many emeralds",
  brr="rich|nice",
  brf="paper empire|librarians love me",
  cp="sugar cane grows so slow|my sugar cane takes forever|waiting on sugar cane again",
  cps="cane pain",
  cpm="i wait and wait|one block at a time",
  cpx="plant more|wait longer|trade for paper|make the farm bigger",
  dg=("sugar cane is the most useful crop|sugar cane beats wheat",
      "paper|true", "wheat feeds more|it's slow", "cane gang", "fair"),
  db=("sugar cane is the most important farm|you need sugar cane above all|sugar cane is the best farm",
      "books|true", "wheat is more important|food first|meh",
      "paper, books, maps, enchanting|librarians pay in emeralds|rockets need paper too",
      "cane gang", "food first|wheat"))

S("pumpkins", "pumpkins", "farming plants", "farming plants decor",
  m="harvested my pumpkins|carved a pumpkin|made a jack o'lantern|my pumpkin patch is growing",
  mr="spooky|nice|pumpkins!",
  mf="halloween vibes|so orange",
  q=["how do you carve a pumpkin? > use shears on it / shears",
     "how do you make a jack o'lantern? > carved pumpkin and a torch / torch in a carved pumpkin",
     "why wear a pumpkin? > endermen won't get mad when you look at them / so endermen leave you alone"],
  qr="ok|neat",
  lk="pumpkin patches are so cozy|jack o'lanterns are the best lights|i love carving pumpkins|"
     "homebody: pumpkins by the door|homebody: my pumpkin patch is so cozy",
  lkr="spooky|cozy|cute",
  lkf="spooky cozy|cute",
  tp="tip: the stem needs a free dirt block next to it|shears carve pumpkins|"
     "carved pumpkin plus torch makes a jack o'lantern|wear a carved pumpkin around endermen|"
     "snow golems and iron golems need carved pumpkins|farmers buy pumpkins",
  br="i have a huge pumpkin patch|made jack o'lanterns for the whole town|my door has pumpkins everywhere",
  brr="spooky|nice",
  brf="halloween all year|so cozy",
  cp="my pumpkin stems won't grow pumpkins|my stems just sit there|no pumpkins on my stems",
  cps="stem pain",
  cpm="the stem is all grown|nothing next to it",
  cpx="give it a free dirt block|clear around it|light it|wait longer",
  dg=("jack o'lanterns are the best light|jack o'lanterns beat torches",
      "cozy|true", "torches are easier|lanterns", "pumpkin gang", "fair"),
  db=("wearing a pumpkin is worth it|a pumpkin helmet is genius|always wear a pumpkin near endermen",
      "endermen|true", "you can't see|it blocks your view|just don't look",
      "endermen leave you alone|no more teleporting fights|it's worth the view",
      "pumpkin gang", "can't see|just don't look"))

S("cactus", "cactus", "farming plants biomes", "farming plants biomes",
  m="harvested my cactus farm|got cactus from the desert|smelted cactus into green dye|my cactus farm is running",
  mr="spiky|ouch|green",
  mf="green dye for days|careful",
  q=["what grows cactus? > sand, with nothing next to it / only on sand",
     "what's cactus for? > green dye / green dye, and camels",
     "does cactus destroy items? > yeah, anything that touches it / yep, careful"],
  qr="ok|ouch",
  lk="cactus farms are so easy|green dye from cactus is great|cactus looks great in a desert build|"
     "builder: cactus in a pot looks so cute|builder: a cactus garden is perfect",
  lkr="spiky|easy|cute",
  lkf="cute but spiky|easy",
  tp="tip: cactus only grows on sand with nothing beside it|smelt it for green dye|"
     "it destroys items that touch it|grow it next to a block and it breaks itself|"
     "camels breed with cactus|it hurts to walk into",
  br="my cactus farm runs itself|i have stacks of green dye|built a cactus garden",
  brr="smart|nice",
  brf="auto farm|green everything",
  cp="cactus destroyed my diamonds|cactus ate my items|dropped my stuff next to a cactus",
  cps="ouch",
  cpm="i dropped them and gone|it burned up my stuff",
  cpx="never drop near cactus|use a fence|watch your step|keep it far from paths",
  st=("funny", "so i was in the desert|story: i was walking through the desert|i was racing across the desert"
                ">> sprinted right into a cactus|bumped into a cactus"
                ">> bounced into another cactus|took damage from two at once|lost half my health to plants lol"),
  dg=("cactus is the best green dye|cactus beats every green",
      "easy|true", "kelp is better|green concrete", "cactus gang", "fair"),
  db=("cactus is the most dangerous plant|cactus is worse than creepers|cactus kills more than mobs",
      "lol true|true", "creepers are worse|it's a plant|lol no",
      "it destroys your items|it hurts you|it's everywhere in the desert",
      "cactus gang", "creepers|it's a plant"))

S("bamboo_farm", "the bamboo farm", "farming plants crafting", "farming crafting plants",
  m="harvested my bamboo farm|bamboo grows so fast|made a stack of scaffolding|planted bamboo by the house",
  mr="fast grow|nice|pandas!",
  mf="it's so tall|scaffolding for days",
  q=["how do you make scaffolding? > six bamboo and a string / bamboo and string",
     "how fast does bamboo grow? > really fast / faster than anything",
     "can you smelt with bamboo? > yeah, it's weak fuel / yep, it smelts a bit"],
  qr="ok|nice",
  lk="bamboo farms are the best|bamboo grows so fast|scaffolding is the best building tool|"
     "builder: scaffolding for every build|builder: bamboo farm by my workshop",
  lkr="fast|tall|scaffolding is great",
  lkf="fast life|scaffolding wins",
  tp="tip: six bamboo and a string make scaffolding|break the bottom and the whole stalk drops|"
     "bamboo is weak fuel, use it for smelting quick|pandas eat bamboo|"
     "two bamboo make a stick|bone meal makes it shoot up",
  br="my bamboo farm is huge|i have ten stacks of scaffolding|built a whole tower with scaffolding",
  brr="builder|nice",
  brf="scaffolding king|tall",
  cp="bamboo spreads everywhere|my bamboo is too tall|bamboo blocks my view",
  cps="bamboo pain",
  cpm="it's like twelve blocks|it took over",
  cpx="harvest it more|cut it lower|move it|use it all",
  dg=("scaffolding is the best building tool|scaffolding beats pillaring",
      "safe|true", "pillaring is faster|dirt works", "scaffold gang", "fair"),
  db=("bamboo is the most useful plant|bamboo beats every plant|nothing is more useful than bamboo",
      "scaffolding|true", "sugar cane|wheat|meh",
      "scaffolding, fuel, sticks, planks|it grows the fastest|pandas love it",
      "bamboo gang", "sugar cane|meh"))

S("cocoa", "cocoa", "farming plants food", "farming food plants",
  m="planted cocoa on jungle logs|harvested my cocoa|got cocoa beans in the jungle|made cookies from my cocoa",
  mr="chocolate|nice|jungle",
  mf="cookies soon|brown dye too",
  q=["where does cocoa grow? > on jungle logs / jungle tree trunks",
     "what's cocoa for? > cookies and brown dye / cookies",
     "how do you know it's ripe? > it's big and orange brown / big and orange"],
  qr="ok|nice",
  lk="cocoa farms are so cute|i love growing cocoa|cocoa pods look cool|"
     "homebody: cookies from my own cocoa|homebody: my cocoa wall is so cute",
  lkr="cute|niche|chocolate",
  lkf="cute|chocolate is life",
  tp="tip: cocoa only grows on jungle logs|place the beans on the side of the log|"
     "when it's big and orange brown it's ripe|bone meal works|"
     "cocoa makes brown dye|cookies need cocoa and wheat",
  br="i have a whole wall of cocoa|my cocoa farm makes stacks|baked cookies from my own cocoa",
  brr="cute|nice",
  brf="cookie life|chocolate",
  cp="i need jungle logs for cocoa|no jungle near me|cocoa only grows on jungle wood",
  cps="jungle pain",
  cpm="the jungle is so far|i need logs",
  cpx="grow a jungle tree|bring back logs|trade for logs|plant a sapling",
  dg=("cocoa is the cutest crop|cocoa beats every crop for looks",
      "cute|true", "pumpkins are cuter|it's niche", "cocoa gang", "fair"),
  db=("cocoa farms are useless|nobody needs cocoa|cocoa is a waste",
      "cookies are weak|true", "brown dye|cookies|it's cute",
      "cookies fill two|brown dye, wow|jungle logs are a pain",
      "useless", "cute though|cookies"))

# ======================================================================= Doings: animals
S("breeding", "breeding animals", "animals farming", "animals farming food",
  m="bred a bunch of cows|my sheep had babies|breeding chickens with seeds|my animal pen is full of babies",
  mr="babies!|cute|farmer",
  mf="so many babies|the pen is full",
  q=["what do cows eat to breed? > wheat / wheat, same as sheep",
     "what do pigs breed with? > carrots, potatoes or beetroot / carrots",
     "how long until babies grow up? > about twenty minutes / twenty minutes, faster if you feed them"],
  qr="ok|cute",
  lk="baby animals are the cutest|breeding animals is so relaxing|a pen full of babies is perfect|"
     "homebody: baby cows in my barn|homebody: my animals are my family",
  lkr="cute|crowded|babies!",
  lkf="cute overload|crowded is cozy",
  tp="tip: cows and sheep breed with wheat|pigs breed with carrots|chickens breed with seeds|"
     "feeding babies makes them grow faster|wait five minutes between breeding|"
     "rabbits breed with dandelions or carrots",
  br="i have fifty cows|bred a rainbow of sheep|my pen has every animal",
  brr="farmer|cute",
  brf="farm life|baby boom",
  cp="my pen is too crowded|my animals keep escaping|too many chickens",
  cps="farm pain",
  cpm="they're everywhere|so loud",
  cpx="build a bigger pen|cull some|fence it better|make another pen",
  st=("funny", "so i was breeding cows|story: i bred my cows|i was feeding my cows wheat"
                ">> kept feeding them for an hour|bred like forty cows"
                ">> now my pen is just cow|they filled the whole barn|i can't even get in lol"),
  dg=("cows are the best animal to breed|cows beat pigs",
      "leather and milk|true", "pigs are easier|sheep are better", "cow gang", "fair"),
  db=("breeding animals is the best food source|animal farms beat crop farms|meat beats crops",
      "meat|true", "crops are easier|crops don't escape|wheat feeds the animals",
      "steak fills more than bread|leather, wool, eggs too|crops can't give you leather",
      "meat gang", "crops|wheat first"))

S("taming", "taming animals", "animals pets", "pets animals friends",
  m="tamed a wolf today|tamed a cat with fish|tamed a parrot|tamed a horse finally",
  mr="new friend|cute|nice",
  mf="it follows me now|so cute",
  q=["how do you tame a wolf? > bones / give it bones till hearts show",
     "how do you tame a cat? > raw fish, move slowly / raw cod or salmon",
     "how do you tame a parrot? > seeds / any seeds"],
  qr="ok|cute",
  lk="taming animals is the best part|a new pet is the best feeling|i tame everything i see|"
     "homebody: taming a cat for my kitchen|homebody: every animal deserves a home",
  lkr="cute|crazy pet person|aww",
  lkf="aww|pet person forever",
  tp="tip: wolves need bones|cats need raw fish, don't move fast|parrots need seeds|"
     "horses, keep getting on until it stops bucking|foxes trust you if you breed them|"
     "never feed a parrot cookies",
  br="i've tamed ten wolves|i have a cat for every room|tamed a horse in two tries",
  brr="pet army|cute",
  brf="pet life|they love me",
  cp="this wolf ate ten bones and no hearts|the cat won't come near me|the horse keeps throwing me off",
  cps="taming pain",
  cpm="i keep trying|so many tries",
  cpx="be patient|move slowly|bring more food|try again",
  dg=("wolves are the best pets|wolves beat cats",
      "loyal|true", "cats are better|cats scare creepers", "dog gang", "fair"),
  db=("cats are better than dogs|cats beat wolves|cats are the best pet",
      "creepers|true", "dogs fight|wolves are loyal|cats are useless",
      "cats scare off creepers and phantoms|they bring gifts|they don't die in fights",
      "cat gang", "dog gang|wolves fight"))

S("pets", "pets", "pets animals home", "pets animals home",
  m="my dog followed me all day|my cat brought me a gift|my parrot is dancing|gave my dog a new collar",
  mr="cute|aww|pet love",
  mf="best friend|so cute",
  q=["why does my cat bring gifts? > if it sleeps on your bed, it brings stuff in the morning / it sleeps with you",
     "how do you change a dog's collar? > dye on the dog / use dye on it",
     "why is my parrot dancing? > a jukebox is playing / music nearby"],
  qr="ok|cute",
  lk="pets make the game better|my dog is my best friend|cats are the best roommates|"
     "homebody: my cat sleeps on my bed every night|homebody: coming home to my pets is the best",
  lkr="cute|aww|best friends",
  lkf="best friends|aww",
  tp="tip: make your dog sit before you go into danger|cats scare creepers away|"
     "dye a dog's collar|parrots dance to jukeboxes|"
     "wolf armor from armadillo scutes|leave pets home in the nether",
  br="my dog has wolf armor|my cat brought me a rabbit's foot|my parrot sits on my shoulder",
  brr="cute|nice",
  brf="best pet|they're the best",
  cp="my dog died to a creeper|my dog fell in lava|i lost my cat",
  cps="nooo",
  cpm="i miss him|he was my best friend",
  cpx="get another|keep them sitting|wolf armor next time|name them",
  st=("bad", "so i had this dog for ages|story: my old dog|i had a dog named biscuit"
              ">> he followed me into a cave|he followed me mining"
              ">> a creeper got him|he jumped in lava after a zombie|i couldn't save him"),
  dg=("every house needs a pet|a home isn't a home without a pet",
      "true|aww", "pets are a pain|they die", "pet gang", "fair"),
  db=("pets should stay home|never take your dog mining|pets belong at home",
      "true|safe", "they help fight|dogs love adventure|they protect you",
      "they die in caves|they fall in lava|they're safer at home",
      "keep them home", "they help|adventure"))

S("horses", "horses", "animals travel exploring", "animals exploring travel",
  m="tamed a fast horse|rode my horse to the village|bred two horses|put armor on my horse",
  mr="giddy up|nice|fast",
  mf="so fast|best friend",
  q=["how do you tame a horse? > keep getting on until hearts / ride it until it stops bucking",
     "how do you breed horses? > golden apples or golden carrots / golden carrots",
     "can a donkey carry chests? > yeah / yep, put a chest on it"],
  qr="ok|nice",
  lk="riding a horse is the best way to travel|my horse is my best friend|horses make exploring fun|"
     "nomad: me and my horse, the open road|nomad: a fast horse is all i need",
  lkr="fast|slow|best friend",
  lkf="best friend|fast enough",
  tp="tip: keep getting on until it stops bucking|feed it to make taming faster|"
     "golden carrots breed them|horses jump fences|"
     "donkeys carry chests|horses won't swim deep water with you",
  br="my horse is the fastest around|i have a stable with ten horses|my horse has diamond armor",
  brr="fast|nice",
  brf="speed demon|stable life",
  cp="my horse ran away|lost my horse at the village|my horse wandered off",
  cps="horse pain",
  cpm="i left it untied|gone",
  cpx="tie it to a fence|use a lead|name it|build a stable",
  dg=("horses are the best transport|horses beat boats",
      "fast|true", "boats are smoother|rails are better", "horse gang", "fair"),
  db=("horses are useless with elytras around|nobody needs horses anymore|horses are obsolete",
      "true|kinda", "elytra is late game|horses are fun|they're cheap",
      "elytras fly over everything|horses get stuck on trees|they can't swim",
      "obsolete", "fun though|cheap"))

# ======================================================================= Doings: fishing, exploring, trading, sleeping
S("fishing_trips", "fishing", "fishing ocean", "fishing ocean food",
  m="went fishing all morning|caught a bunch of cod off the dock|sat by the lake fishing|spent the rain fishing",
  mr="relaxing|nice|peaceful",
  mf="so peaceful|perfect day",
  q=["is fishing better in the rain? > yeah, they bite faster / yep, rain speeds it up",
     "how do you get treasure fishing? > open water, deep and wide / fish in open water, not a puddle",
     "what can you fish up? > fish, junk, treasure like books / fish, boots, sometimes enchanted stuff"],
  qr="ok|nice",
  lk="fishing is the most relaxing thing|i could fish all day|fishing at sunset is perfect|"
     "homebody: fishing off my own dock|homebody: a quiet lake and a rod",
  lkr="relaxing|boring|peaceful",
  lkf="peaceful|boring is relaxing",
  tp="tip: fish in the rain, they bite faster|open water gets you treasure|"
     "lure makes them bite quicker|luck of the sea gets better loot|"
     "you get xp from fishing|bring a boat to reach open water",
  br="fished up a mending book|caught a stack of fish in one evening|got a saddle and a name tag fishing",
  brr="lucky|nice",
  brf="the lake provides|lucky day",
  cp="i fished for an hour and got junk|fished up nothing but boots|just sticks and bowls from fishing",
  cps="junk",
  cpm="boots, bowls, sticks|so much junk",
  cpx="luck of the sea|open water|fish in the rain|be patient",
  st=("good", "so i was fishing in the rain|story: i went fishing in a storm|i was out fishing one rainy night"
               ">> felt a tug and pulled hard|the bobber dipped and i yanked"
               ">> it was an enchanted bow|fished up a mending book|got a name tag and a saddle in a row"),
  dg=("fishing is the best way to relax|nothing relaxes like fishing",
      "peaceful|true", "building is more relaxing|it's boring", "fish gang", "fair"),
  db=("fishing is a waste of time|nobody should fish|fishing is boring",
      "boring|true", "treasure|it's relaxing|books",
      "you wait forever for a boot|farm food is faster|nothing happens",
      "boring", "treasure though|relaxing"))

S("exploring", "exploring", "exploring places biomes", "exploring places biomes",
  m="went exploring past the mountains|found a new biome today|walked further than ever|explored a whole new area",
  mr="explorer|nice|adventure",
  mf="so much out there|can't wait to go back",
  q=["what should i bring exploring? > a bed, food, a sword, torches / bed and food",
     "how do you not get lost? > write down coords / pillars and torches, and coords",
     "what's the best thing to find? > a village / a temple or a mansion"],
  qr="ok|nice",
  lk="exploring is the best part of the game|seeing a new biome is the best feeling|i love going where nobody's been|"
     "nomad: there's always something past the next hill|nomad: home is the road",
  lkr="adventure|dangerous|so fun",
  lkf="adventure is life|dangerous is fun",
  tp="tip: always bring a bed|write down your coords|build a pillar on hills to find your way|"
     "bring a boat for oceans|carry food for a week|"
     "mark villages on a map",
  br="found a woodland mansion|explored three biomes in a day|walked ten thousand blocks",
  brr="explorer|whoa",
  brf="adventure|never stopped",
  cp="i got lost exploring|couldn't find my way home|lost my way coming back",
  cps="lost",
  cpm="everything looks the same|i have no idea where home is",
  cpx="write coords|build pillars|follow a river|bring a compass",
  st=("good", "so i was exploring past the desert|story: i walked past the badlands|i went further than ever"
               ">> came over a hill and saw something huge|saw a massive structure in the distance"
               ">> it was a woodland mansion|it was a whole ocean monument|it was a village bigger than ours"),
  dg=("exploring beats building|exploring is more fun than building",
      "adventure|true", "building is better|too dangerous", "explorer gang", "fair"),
  db=("you should always explore before you settle|never build before you explore|explore first, build later",
      "true|smart", "build first, safety|explore later|you need a base",
      "find the best spot first|you might be next to a better biome|explore, then settle",
      "explore first", "safety first|base first"))

S("trading", "trading with villagers", "villagers trade", "villagers trade town",
  m="traded with a librarian|got mending from a villager|sold wheat for emeralds|my villager trades are so good now",
  mr="emerald time|nice|trader",
  mf="rich now|emeralds",
  q=["how do villagers get jobs? > a workstation near them / give them a job block",
     "why did the prices go up? > you hit one, or it's in demand / they remember if you hurt them",
     "how do you get better trades? > trade more, they level up / level them up"],
  qr="ok|nice",
  lk="trading with villagers is the best|a good librarian is priceless|i love a full emerald stack|"
     "hoarder: emeralds, more emeralds|hoarder: i trade everything",
  lkr="rich|villagers are annoying|nice",
  lkf="rich life|worth it",
  tp="tip: put a lectern down to make a librarian|the more you trade, the better the trades|"
     "curing a zombie villager gets huge discounts|they restock at their workstation|"
     "hero of the village makes everything cheaper|nitwits in green robes never trade",
  br="got a mending librarian|i have a stack of emeralds|cured a zombie villager and got cheap trades",
  brr="rich|nice",
  brf="emerald king|best trades",
  cp="my librarian has terrible trades|my villager only sells junk|the prices are crazy",
  cps="villager pain",
  cpm="nothing good|it's all junk",
  cpx="break the lectern and place it again|try another villager|cure a zombie villager|level it up",
  st=("good", "so i set up a lectern for a villager|story: i made a librarian|i was rolling a librarian"
               ">> broke and placed it like twenty times|kept breaking the lectern for better trades"
               ">> and finally got mending|got mending for ten emeralds|it finally offered mending"),
  dg=("librarians are the best villagers|nothing beats a librarian",
      "mending|true", "farmers are better|fletchers", "librarian gang", "fair"),
  db=("trading halls are cheating|villager trading is too easy|trading ruins the game",
      "kinda|true", "it's smart|it's survival|it's fair",
      "mending for ten emeralds is broken|you skip the whole game|no more looting",
      "too easy", "it's smart|survival"))

S("sleeping", "sleeping", "sleep night home", "sleep night home",
  m="slept through the night|went to bed early|skipped the night|slept in my bed finally",
  mr="rested|nice|good night",
  mf="feels good|no phantoms",
  q=["why can't i sleep? > monsters nearby / something hostile is close",
     "what happens if you don't sleep? > phantoms come after three nights / phantoms",
     "can you sleep in the day? > only in thunderstorms / no, only at night or in storms"],
  qr="ok|got it",
  lk="sleeping is the best part of the day|a good night's sleep is everything|i love skipping the night|"
     "homebody: in bed by sunset|homebody: sleep is sacred",
  lkr="cozy|boring|sleep is life",
  lkf="sleep is life|cozy",
  tp="tip: sleep every few nights or phantoms come|you can't sleep with monsters near|"
     "you can sleep in thunderstorms|sleeping sets your spawn|"
     "light around your house so you can sleep|beds explode in the nether",
  br="i haven't seen a phantom in weeks|my bed has the best view|i always sleep on time",
  brr="disciplined|nice",
  brf="routine|no phantoms",
  cp="i couldn't sleep, monsters nearby|zombies outside wouldn't let me sleep|something kept me up",
  cps="ugh",
  cpm="i could hear them|they were right outside",
  cpx="light around your house|wall it in|kill them first|build a fence",
  st=("funny", "so i hadn't slept in like five nights|story: i stayed up way too long|i never slept for days"
                ">> phantoms showed up|heard screeching overhead"
                ">> chased me all the way home|i dove into bed|they followed me home lol"),
  dg=("sleeping every night is a must|always sleep",
      "phantoms|true", "night is fun|skipping is boring", "sleep gang", "fair"),
  db=("skipping the night ruins the game|you should play the nights|real players stay up",
      "nights are fun|true", "phantoms|monsters|i like my bed",
      "nights have the best fights|you miss half the game|mobs drop good stuff",
      "night gang", "phantoms|bed"))

S("storage", "storage and sorting", "storage home", "storage home crafting",
  m="sorted all my chests today|built a new storage room|labelled every chest|reorganised my storage",
  mr="organised|nice|neat",
  mf="finally|so neat",
  q=["how do you sort chests? > one per type of item / ores here, wood there",
     "signs or item frames? > item frames look nicer / signs are cheaper",
     "what's a sorting system? > hoppers that send items to the right chest / hoppers and filters"],
  qr="ok|nice",
  lk="a sorted storage room is the best|labelled chests make me happy|i love sorting|"
     "hoarder: everything sorted, everything kept|hoarder: a chest for every block",
  lkr="neat|obsessive|nice",
  lkf="neat freak|obsessive is good",
  tp="tip: one chest per type of item|item frames on the front show what's in it|"
     "signs work too|keep a dump chest for random stuff|"
     "hoppers can sort for you|shulker boxes for the overflow",
  br="my storage room has a hundred chests|every chest is labelled|built an auto sorter",
  brr="organised|neat",
  brf="never lose anything|perfect",
  cp="my storage is a mess|i can't find anything|everything is in random chests",
  cps="chaos",
  cpm="it takes forever to find stuff|it's all mixed up",
  cpx="sort it|label it|one chest per item|item frames",
  st=("funny", "so i sorted every chest in my house|story: i spent all day sorting|i organised everything"
                ">> labels, frames, everything perfect|every chest had its own sign"
                ">> came back from mining and dumped it all in one chest|forgot and threw everything in the first chest|messed it all up in one trip lol"),
  dg=("sorting chests is worth it|everyone should sort their chests",
      "true|neat", "it's boring|messy is fine", "sort gang", "fair"),
  db=("a messy storage is fine|you don't need to sort|sorting is a waste of time",
      "true|lol", "you'll lose stuff|it's so much faster sorted|chaos",
      "i know where everything is|sorting takes hours|just look around",
      "chaos gang", "sort it|you'll lose stuff"))

S("home_routine", "home life", "home feelings", "home feelings day",
  m="did my chores at home|restocked my furnace and fed the animals|spent the day at home|cleaned up my house",
  mr="cozy|nice|homebody",
  mf="feels good|quiet day",
  q=["what do you do at home all day? > farm, sort chests, cook / chores mostly",
     "do you like staying home? > love it / sometimes",
     "what's the first thing you do at home? > put stuff away / check my chests"],
  qr="ok|nice",
  lk="a quiet day at home is the best|i love my home routine|coming home after a trip is the best feeling|"
     "homebody: farm, cook, sleep, perfect|homebody: home is my favourite place",
  lkr="cozy|boring|nice",
  lkf="cozy life|boring is peaceful",
  tp="tip: put your loot away first thing|refuel the furnaces|"
     "feed the animals in the morning|repair your gear at home|"
     "check for creeper holes|keep the farm watered",
  br="my house runs like clockwork|i finished every chore by noon|my home is spotless",
  brn="a:has_house",
  brr="organised|neat",
  brf="routine|home life",
  cp="chores never end|there's always something at home|i spend all day fixing my house",
  cpn="a:has_house",
  cps="home life",
  cpm="farm, furnace, chests, repeat|it never ends",
  cpx="build some auto farms|do one thing a day|get help|hoppers",
  dg=("home days are the best days|staying home beats adventures",
      "cozy|true", "adventure is better|boring", "home gang", "fair"),
  db=("staying home is boring|home days are wasted days|you should never stay home",
      "boring|true", "home is cozy|chores matter|rest is good",
      "adventure is out there|nothing happens at home|you won't find diamonds at home",
      "go out", "home is cozy|rest"))

S("town_life", "town life", "town friends", "town friends building",
  m="spent the day in town|the town square is busy|walked around town all day|town feels alive today",
  mr="busy|nice|town life",
  mf="lots going on|nice to see",
  mn="a:in_town",
  q=["what's the best part of living in town? > safety in numbers / neighbours / everything's close",
     "do you like town or the wild better? > town / the wild",
     "how big is our town now? > bigger every day / big enough"],
  qn="a:in_town",
  qr="ok|true",
  lk="town life is the best|i love having neighbours|walking through town at night is so nice|"
     "helper: helping out around town|helper: i love lending a hand in town",
  lkr="nice|crowded|cozy",
  lkf="cozy|crowded is fine",
  lkn="a:in_town",
  tp="tip: light up the town so nothing spawns|build roads between houses|"
     "share a farm, it's easier|a wall keeps mobs out|"
     "iron golems guard a town|a bell in the square is nice",
  tpn="a:in_town",
  br="our town has the best roads|built the town square|our town is the biggest around",
  brn="a:in_town",
  brr="proud|nice",
  brf="town pride|we built it together",
  cp="town is so crowded|too many people in town|town is so noisy",
  cpn="a:in_town",
  cps="town life",
  cpm="everyone's always around|no space",
  cpx="build outside the walls|find a quiet corner|go mining|build a bigger town",
  dg=("town life beats living alone|living in town is better",
      "safety|true", "alone is peaceful|too crowded", "town gang", "fair"),
  dgn="a:in_town",
  db=("towns need walls|every town needs a wall|a town without walls is asking for trouble",
      "safety|true", "walls are ugly|lights are enough|open towns are nicer",
      "zombies walk right in|raids|one wall keeps everyone safe",
      "wall gang", "lights are enough|ugly"),
  dbn="a:in_town")

S("neighbours", "neighbours", "town friends gossip", "town friends gossip",
  m="said hi to the neighbours|my neighbour built a new porch|my neighbours waved at me|helped my neighbour with their roof",
  mr="nice|neighbourly|lol",
  mf="good people|they're alright",
  mn="a:in_town",
  q=["what are your neighbours like? > nice / loud / quiet, they keep to themselves",
     "do you borrow stuff from neighbours? > never their chests / only if they say so",
     "do you help your neighbours? > always / when they ask"],
  qn="a:in_town",
  qr="ok|fair",
  lk="good neighbours are the best|i love my neighbours|helping neighbours feels good|"
     "helper: i fix my neighbours' fences|helper: a good neighbour lends a hand",
  lkr="nice|lucky|neighbourly",
  lkf="lucky me|neighbourly",
  lkn="a:in_town",
  tp="tip: don't build too close to your neighbour|never open their chests|"
     "share your extra food|help fix creeper holes|"
     "ask before you build on their side|a fence between houses keeps it friendly",
  tpn="a:in_town",
  br="i have the best neighbours|my neighbour gave me a whole stack of food|my neighbours helped build my house",
  brn="a:in_town",
  brr="lucky|nice",
  brf="good people|lucky",
  cp="my neighbour built right up to my wall|my neighbour's house blocks my view|my neighbour's wall is right on my window",
  cpn="a:in_town",
  cps="neighbour problems",
  cpm="it's right against my house|so rude",
  cpx="talk to them|build a fence|move|plant a hedge",
  st=("funny", "so my neighbour asked me to watch their farm|story: my neighbour went mining for a week|my neighbour left me in charge of their farm"
                ">> i forgot all about it|i went mining too"
                ">> came back and their cows were in my garden|their sheep were on my roof somehow|their chickens took over my house"),
  dg=("good neighbours make a good town|neighbours matter more than houses",
      "true|so true", "houses matter more|walls make good neighbours", "neighbour gang", "fair"),
  dgn="a:in_town",
  db=("neighbours should build at least ten blocks apart|houses shouldn't touch|give your neighbours space",
      "space|true", "close is cozy|towns are close|it's fine",
      "nobody wants a wall in their window|space keeps the peace|you need room for farms",
      "give space", "close is cozy|it's a town"),
  dbn="a:in_town")

# ======================================================================= Doings: getting around
S("sea_voyage", "crossing the ocean", "travel ocean exploring", "ocean exploring travel",
  m="crossed the ocean by boat|sailed all night|found an island out at sea|my boat trip took forever",
  mr="sailor|whoa|adventure",
  mf="the sea is huge|land ho",
  q=["is it safe to sail at night? > drowned come up, be careful / not really, drowned everywhere",
     "what should i bring on a boat trip? > a bed, food, a spare boat / food and a bed",
     "what's out at sea? > islands, shipwrecks, monuments / shipwrecks and ruins"],
  qr="ok|whoa",
  lk="sailing is the best way to explore|the ocean at night is beautiful|i love finding islands|"
     "nomad: the sea is the biggest road|nomad: i live on my boat",
  lkr="beautiful|scary|adventure",
  lkf="beautiful and scary|adventure",
  tp="tip: bring a spare boat|drowned come out at night, sail by day|"
     "shipwrecks have treasure maps|dolphins lead you to treasure if you feed them|"
     "bring a bed for islands|follow the coast so you don't get lost",
  br="crossed the biggest ocean|found a shipwreck with a treasure map|sailed to an island and built a base",
  brr="sailor|nice",
  brf="sea legs|captain",
  cp="got lost at sea|lost sight of land|sailed for hours and saw nothing",
  cps="sea pain",
  cpm="water everywhere|no land in sight",
  cpx="follow the coast|write down coords|sail by day|bring a map",
  st=("good", "so i was sailing in the dark|story: i was out on the ocean at night|i sailed through the night"
               ">> saw a mast sticking out of the water|bumped into a shipwreck"
               ">> it had a treasure map|got a buried treasure map|dug up a chest with a heart of the sea"),
  dg=("the ocean is the best place to explore|oceans beat land for exploring",
      "treasure|true", "too empty|land is better", "sea gang", "fair"),
  db=("oceans are the most boring biome|nothing happens at sea|the ocean is empty",
      "empty|true", "shipwrecks|monuments|treasure",
      "it's water for miles|you see the same thing for an hour|nothing to build on",
      "boring sea", "treasure though|monuments"))

S("ice_roads", "ice roads", "travel nether", "travel nether building", gate=None,
  m="built an ice road in the nether|my nether ice road is done|boated down my ice road|laid blue ice all the way",
  mr="speed|nice|zoom",
  mf="so fast|crosses the map in seconds",
  q=["why build an ice road in the nether? > one block there is eight here / eight times the distance",
     "packed ice or blue ice? > blue is fastest, packed is cheaper / blue if you can",
     "do you need walls on an ice road? > yeah, or you fly off / walls keep the boat on"],
  qr="ok|whoa",
  lk="ice roads are the fastest way to travel|zooming down an ice road is so fun|my ice road changed everything|"
     "speedrunner: blue ice, boat, gone|speedrunner: the fastest commute in the game",
  lkr="fast|expensive|fun",
  lkf="fast is fun|worth it",
  tp="tip: walls on both sides keep the boat on|blue ice is the fastest|"
     "a gap every other block still works|build it in the nether, eight times the distance|"
     "put the road at a safe height, away from lava|light the tunnel",
  br="my ice road goes to every base|built a thousand block ice road|i cross the map in a minute",
  brr="speed|whoa",
  brf="zoom|worth every block",
  cp="ice roads need so much ice|i need way more packed ice|my ice road ate all my ice",
  cps="ice pain",
  cpm="i need stacks of packed ice|it's never enough",
  cpx="use packed ice|leave gaps|trade for ice|build walls",
  dg=("ice roads beat rails|ice roads are better than rails",
      "faster|true", "rails are cheaper|rails are automatic", "ice gang", "fair"),
  db=("every base should be on an ice road|ice roads are a must|no ice road, no network",
      "speed|true", "too expensive|rails are fine|horses",
      "you cross the map in seconds|everything connects|nothing is faster",
      "ice gang", "too expensive|rails"))

S("long_trips", "long trips", "travel exploring plans", "exploring travel plans",
  m="back from a long trip|packed for a long trip|been on the road for days|walked for three days straight",
  mr="welcome back|adventure|road trip",
  mf="so tired|worth it",
  q=["what do you pack for a long trip? > a bed, food, torches, tools / food and a bed",
     "how do you find your way back? > coords / pillars and torches",
     "where do you sleep on the road? > a quick hut, or my bed in a hole / dig into a hill"],
  qr="ok|smart",
  lk="long trips are the best adventures|i love being on the road|coming home after a long trip feels amazing|"
     "nomad: the road is my home|nomad: never stay anywhere too long",
  lkr="adventure|tiring|fun",
  lkf="tiring and fun|adventure",
  tp="tip: always bring a bed|carry a stack of food|write down your home coords|"
     "dig into a hill to sleep|bring a spare pick|"
     "drop a pillar of torches on high ground",
  br="walked to the edge of the map and back|been on the road for a week|explored a thousand blocks in one trip",
  brr="adventurer|whoa",
  brf="road warrior|never tired",
  cp="i got lost on my trip|ran out of food on the road|died far from home",
  cps="road pain",
  cpm="miles from anywhere|nobody around to help",
  cpx="bring more food|write coords|bring a bed|travel with someone",
  st=("bad", "so i went on a long trip|story: i walked for days|i set out on a big trip"
              ">> forgot to write down my coords|never noted where home was"
              ">> it took me a week to find home|i built a new house where i got lost|still haven't found my old house"),
  dg=("long trips are worth it|you should take long trips",
      "adventure|true", "too risky|home is better", "road gang", "fair"),
  db=("you should always travel with someone|never go on a long trip alone|travel in pairs",
      "safety|true", "alone is peaceful|no one to slow you down|solo is fine",
      "two beds, two swords, double the safety|someone has your back|it's more fun",
      "pairs", "solo is fine|peaceful"))


# ---- Write it out (and check the lines: plain ASCII, lowercase, at most 90 characters, ids unique).
def all_text(lines):
    groups = [lines] if isinstance(lines, list) else list(lines.values())
    for group in groups:
        for line in group:
            if isinstance(line, dict):
                yield line["line"]
                yield from line["then"]
            else:
                yield line


ids = set()
said = {}
for t in topics:
    if t["id"] in ids:
        problems.append("id twice: " + t["id"])
    ids.add(t["id"])
    for tr in t["turns"]:
        if not tr["lines"]:
            problems.append("%s: a turn with no lines" % t["id"])
        for x in all_text(tr["lines"]):
            if len(x) > 90:
                problems.append("%s: over 90 characters: %s" % (t["id"], x))
            if not x.isascii() or x != x.lower() or x != x.strip():
                problems.append("%s: not plain lowercase ASCII: %s" % (t["id"], x))
            said.setdefault(x, set()).add(t["id"])
repeats = [x for x, where in said.items() if len(where) > 1 and len(x) > 12]

out = sys.argv[1]
with open(out, "w", encoding="utf-8") as f:
    json.dump({"_comment": "Things and doings, subject by subject, from small talk to debate (generated by "
                           "tools/talk_gen/things.py; the user: \"more topics for each subject matter (from small talks to "
                           "debate)\"): blocks, tools and weapons, armor, food, workstations, mining, building, farming, "
                           "animals, travel, town life. Each subject gets a passing mention, a question, a like, a tip, a "
                           "brag, a complaint, a story, a mild disagreement and a debate. Minecraft 1.21.1 facts.",
               "topics": topics}, f, indent=1, ensure_ascii=True)
print(len(topics), "topics,", len(said), "distinct lines,", len(repeats), "repeated longer lines")
for p in problems:
    print("PROBLEM:", p)
if "--repeats" in sys.argv:
    for x in sorted(repeats):
        print("  repeat:", x, sorted(said[x])[:4])
