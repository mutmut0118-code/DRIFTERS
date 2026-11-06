"""Writes drifter_talk/topics_gen_deep.json: deep and philosophical talks between Drifters.

The user (2026-11-06): "deep and philosophical conversations ... as detailed and in depth as possible". Slow, thoughtful
talks, still in lowercase chat style and still in each one's voice (a newbie wonders out loud, a hermit is terse, a casual
jokes and then means it, a helper is kind, a speedrunner thinks about time).

Themes (death and respawning, grief, memory, purpose, the world's edge, the void, the sky, time, friendship, loneliness,
nature, animals, the ocean, building as a mark, right and wrong, freedom, dreams, fear, happiness, players, being watched,
whether it's all a game...) each have hand-written angles: one question about the theme, with its own thoughts, questions
and answers, deeper thoughts and replies. Every angle becomes several topics:

- a musing: a thinks out loud, b reacts in its own voice, a goes deeper, b answers;
- a 2-3 part talk: a asks, b answers; part 2 goes deeper; part 3 (on some) says how it feels;
- two situational versions (late at night, between friends, after a death, in a mood...) with a lead-in that fits;
- a casual's joke that turns serious (not for grief);
- a debate (agree/disagree marks) when the angle has a claim.

Usage: python tools/talk_gen/deep.py src/main/resources/data/drifter/drifter_talk/topics_gen_deep.json
Check: python tools/check_talk_web.py . --quiet

Format of the tables below (to keep thousands of lines readable): a pool is one string, lines split by " | ". An angle
is a block, one field per line ("m: ..."), the field letters:
  m  a's musings (statements)              q  a question > answers split by " / " (one q per line)
  d  a's deeper thought > b's replies to that very line (one d per line)
  o  a's next, deeper thought (part 2) > b's replies (one o per line)
  l  a's feelings (part 3, optional) > b's answers (one l per line)
  c  a claim to argue about (optional)     y  agreeing with it        n  disagreeing with it
Every reply must fit its own line whatever came before it (the m lines, b's reaction, or the question and its answer).
"""
import json
import sys

out = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/data/drifter/drifter_talk/topics_gen_deep.json"
PREFIX = "g_deep_"


def L(text):
    """'a | b | c' -> ['a', 'b', 'c']."""
    return [x.strip() for x in text.split(" | ") if x.strip()]


def V(spec):
    """A pool: one string (everyone's lines) or a dict of voice -> string."""
    if isinstance(spec, str):
        return L(spec)
    return {voice: L(text) for voice, text in spec.items()}


# Situations: what has to hold for a situational version to come up.
SIT = {
    "night": ["time_night"],
    "evening": ["time_evening"],
    "morning": ["time_morning"],
    "day": ["time_day"],
    "stars": ["time_night", "outside", "clear"],
    "rain": ["rain", "outside"],
    "thunder": ["thunder"],
    "friends": ["friends"],
    "group": ["groupmates"],
    "grief": ["a:mood_grieving"],
    "died": ["a:died_lately"],
    "close": ["a:close_call"],
    "bored": ["a:mood_bored"],
    "calm": ["a:mood_calm"],
    "anxious": ["a:mood_anxious"],
    "angry": ["a:mood_angry"],
    "frustrated": ["a:mood_frustrated"],
    "win": ["a:mood_triumphant"],
    "loner": ["a:loner"],
    "nohome": ["a:no_home"],
    "house": ["a:has_house"],
    "town": ["a:in_town"],
    "built": ["a:built_lately"],
    "killed": ["a:killed_many"],
    "ore": ["a:found_ore"],
    "diamonds": ["a:has_diamonds"],
    "hungry": ["a:hungry"],
    "spare": ["a:spare_food"],
    "traded": ["a:traded_lately"],
    "likes_player": ["a:likes_a_player"],
    "dislikes_player": ["a:dislikes_a_player"],
    "teach": ["b:voice_newbie", "!a:voice_newbie"],
    "timid": ["a:timid"],
}

# Everyone's short lines (short reactions may repeat anywhere).
PLACEHOLDER = ["hm"]
# a's reaction to b's answer to its question: it must fit any answer (yes, no, i don't know, a thing).
PONDER = V({"any": "huh | hm | fair | fair enough | ha, ok | yeah, i get that | hm. ok",
            "hermit": "mm. | hm.", "newbie": "oh, ok | ohh", "casual": "lol fair | ok fair", "speedrunner": "noted | ok",
            "helper": "i get that | that's fair"})
# b's last word in a debate: after agreeing, after disagreeing.
DEBATE_END_AGREE = ["yeah", "for real", "fr", "lol yeah", "100%", "mhm"]
DEBATE_END_DISAGREE = ["we'll see", "if you say so", "hm", "ok ok", "maybe", "fine, fine", "lol ok"]

THEMES = {}
ANGLES = []
JOKES = {}  # theme -> a casual's joke to start on (then it gets serious)


def theme(key, tags, leads, react, nudge, close, bclose, yes, no, sits, needs=(), trust=False, weight=1.0):
    THEMES[key] = dict(tags=tags, leads=leads, react=V(react), nudge=V(nudge), close=V(close),
                       bclose=V(bclose), yes=V(yes), no=V(no), sits={s: V(t) for s, t in sits.items()}, needs=list(needs),
                       trust=trust, weight=weight)
    for s in sits:
        assert s in SIT, s
    return key


def q_line(text):
    question, answers = text.split(" > ", 1)
    return {"line": question.strip(), "then": [x.strip() for x in answers.split(" / ") if x.strip()]}


def angle(th, key, about, block, need=()):
    fields = {}
    for raw in block.strip().splitlines():
        raw = raw.strip()
        if not raw:
            continue
        name, text = raw.split(":", 1)
        fields.setdefault(name.strip(), []).append(text.strip())
    assert not set(fields) - set("mqdolcyn"), (key, sorted(fields))
    a = {name: L(" | ".join(texts)) for name, texts in fields.items() if name not in "qdol"}
    for name in "qdol":
        a[name] = [q_line(x) for x in fields.get(name, [])]
        for x in a[name]:
            assert x["then"], (key, name, x["line"])
    for name in ("m", "q", "d", "o"):
        assert a.get(name), (key, name)
    ANGLES.append(dict(theme=th, key=key, about=about, a=a, need=list(need)))


# ======================================================================================================================
# Death and respawning.
RESPAWN = theme(
    "respawn", ["death", "philosophy"], ["death", "philosophy", "feelings", "memories"],
    react={"any": "heavy thought | i try not to go there | ngl that's crossed my mind too | huh. i've wondered the same | "
                  "that one keeps me up some nights",
           "casual": "bro who hurt you lol. but yeah | lol ok philosopher. fair though | that's dark. i kinda get it though",
           "hermit": "the bed forgets. we don't. | everyone wakes. not everyone wakes the same. | mm. dying teaches.",
           "newbie": "ok now i'm a little scared of dying lol | i've only died like twice and it was awful | "
                     "i just thought dying was part of it",
           "helper": "that's a big thing to carry around | i'm glad you're here to wonder about it | "
                     "you can always talk to me about this",
           "speedrunner": "dying costs me a minute. that's all i let it be | a death is just a reset with extra walking | "
                          "i count my deaths. the number never feels good"},
    nudge="you've been chewing on this one | go on | you've been thinking about this a lot, huh | there's more, isn't there",
    close={"any": "anyway. glad we're both still here | let's just not die today | beds everywhere, that's my answer | "
                  "still here. that counts for something",
           "hermit": "we wake. that's enough.", "casual": "ok that's enough death talk for one day lol",
           "newbie": "i'm gonna be extra careful now"},
    bclose="glad we talked about it | stay out of lava, deal? | same time tomorrow, both alive | don't go dying on me",
    yes="see, you get it | right? glad it's not just me | exactly",
    no="we'll see who's right | agree to disagree then | maybe i'll change my mind someday",
    sits={"night": "can't sleep. thinking about dying again | nights like this i think about the first time i died | "
                   "the dark gets me thinking about respawning | everyone's asleep and my brain won't stop",
          "died": "i {a.death} and it's still on my mind | since i {a.death} i can't stop thinking | "
                  "woke up in bed again. still shaky | dying again messed with my head a bit",
          "close": "{a.close_call} nearly got me and now i'm thinking | i came so close earlier. it got me thinking | "
                   "half a heart earlier. still thinking about it",
          "friends": "can i tell you something weird | i trust you, so i'll just say it | you're the only one i'd say this to"})

angle(RESPAWN, "respawn_counts", "whether dying counts", """
m: been thinking about whether dying even counts if we come back | if we always wake up after, is it really dying?
m: dying feels like it should be bigger than it is | you fall, it goes dark, then you're just back in bed
q: does dying count if you always come back? > it counts. you lose everything / not really, it's like a bad nap / i think it counts because it hurts / idk. it feels like it should
q: is it still dying if you wake up after? > yeah, something ends every time / no, dying means not coming back / it's dying with a second chance
d: the fall still hurts. then you're back in bed like nothing happened > i think it counts because we're scared of it / if it didn't count we wouldn't run from creepers / maybe it's practice for something
d: your stuff stays where you died, but you don't > it counts. ask anyone who lost their diamonds / if it didn't count we wouldn't run from creepers
d: the world keeps your stuff and hands you back to yourself > it counts. ask anyone who lost their diamonds / maybe it's practice for something / i think it counts because we're scared of it
o: the thing that gets me is nobody else even notices > yeah, the world just keeps going / i notice. i always notice / that's the lonely part of it
o: the world doesn't stop for it. the sun just keeps going > maybe that's kind of comforting / yeah. the cows don't even look up / then we have to notice for each other
o: what if every death costs something we can't see > then we should be careful how many we spend / that's a scary thought / then i've spent way too many
o: maybe a little bit of us stays where we fell > then i've left a lot of me in lava lol / if that's true i want my bits back / i think the part that stays is the fear
l: i think i'm scared of the one time it doesn't work > then let's make sure it always works / i'll watch your back out there / everybody is. you're just honest about it
l: maybe that's why i put beds everywhere > smart. i'll help you put more down / beds everywhere is a good plan honestly
l: honestly i'm more scared of dying than i let on > everybody is. you're just honest about it / i'll watch your back out there / me too, if i'm honest
c: dying doesn't count if you come back | if you wake up after, it's not really dying | respawning makes death just a nap
y: yeah, a nap with extra walking | honestly true | fair, we always come back
n: it counts. you lose your stuff and your nerve | nah, ask anyone who lost diamonds | it counts every time, trust me
""")

angle(RESPAWN, "respawn_same_self", "whether you're still you after", """
m: i keep wondering if the one who wakes up in bed is really me | what if every respawn makes a new us
m: i woke up in bed and felt like a copy of myself | the me that fell in the lava and the me here, same one?
q: when you respawn, are you still you? > yeah, same scars, same grudges / i hope so / i remember dying, so it's me / no idea, and i don't want to know
q: what if the one who wakes up isn't the one who died? > then i owe the last me some diamonds / you'd still be you to me / then i've been a lot of people
d: i remember the fall, so i guess it's me > if you remember it, you're you. that's enough for me / you came back and knew my name. that's you
d: remembering the fall isn't the same as being there, though > you still laugh at the same dumb stuff. it's you / i'd notice if you weren't you, trust me
d: i feel the same, but would i know if i wasn't? > i'd notice if you weren't you, trust me / you still laugh at the same dumb stuff. it's you
d: my house still feels like mine, so maybe > then it's you. houses know / you came back and knew my name. that's you
o: sometimes i feel a bit different after, like quieter > different isn't worse / quieter is ok. you're still you
o: what if we lose a tiny bit each time and don't notice > that's not losing a bit, that's learning / then i'd have noticed it in you. i haven't
o: after my last death i stopped going near cliffs > i think that's just being careful now / that's not losing a bit, that's learning
o: maybe the scared part is what comes back stronger > scared keeps you alive, so ok / different isn't worse / i think that's just being careful now
c: the one who respawns is still the same person | you're the same you every time you wake up
y: has to be, we remember everything | yeah, same old you | of course
n: i'm not so sure. something's different each time | nah, every respawn changes you a little
n: i don't think anyone comes back exactly the same
""")

angle(RESPAWN, "respawn_last_life", "what if this is the last life", """
m: what if one day we just don't wake up | i keep thinking this could be the last time i come back
m: nobody ever told us how many tries we get | what if the bed only works so many times
q: what would you do if you knew this was your last life? > stay home, eat bread, look at the sky / finally go see the ocean / tell everyone i like them / nothing different. that's the trick
q: do you think there's a limit on how many times we come back? > no, or someone would've found it / i hope not / maybe, and we just haven't hit it / i try not to count
d: if it was the last one i think i'd stop mining so deep > then don't wait for it to be the last one / maybe live like it is, just a bit
d: if i knew it was my last, i'd probably be braver, weirdly > maybe live like it is, just a bit / braver? i'd be hiding in my house
d: if it was my last life i'd want to finish my house first > that's a good reason to do the stuff you keep putting off / then finish it now, just in case
d: on a last life i'd stop wasting days on dumb stuff > that's a good reason to start now / i'd spend it with people, not chests
o: the funny thing is, everyone would live better if they knew > that's why i say hi to everyone now / ok, tomorrow we do the thing we keep saying we'll do
o: we waste lives because we think we have a lot > so let's not waste this one / ok, tomorrow we do the thing we keep saying we'll do
o: i think about the ones who wandered off and never came back > me too. i hope they're ok somewhere / maybe they found somewhere better
o: maybe the bed is a gift and we treat it like a chore > gifts get taken for granted. you're right / i'll thank my bed tonight lol
l: if it was my last day, i'd want you there > i'd be there. no question / same. you're stuck with me
l: i don't say it much, but i'm glad i'm not doing this alone > same. you're stuck with me / me too, honestly
l: if i don't come back one day, you can have my chests. not the diamonds though > deal. but you're coming back, so it doesn't matter / lol. you're coming back, so keep them
c: we should live like every life is the last one | living like it's your last life is the only way to live
y: yeah. no more putting stuff off | honestly true | that's how i try to live
n: that sounds exhausting | nah, then i'd never go mining again | i'd be too scared to do anything
""")

angle(RESPAWN, "respawn_between", "the dark between dying and waking", """
m: there's a moment between dying and waking that i can't remember | when you die it goes dark and then it's morning in my bed
m: where do we go in that little dark bit | i wish i knew what's on the other side of the dark
q: what do you think happens in the dark between dying and waking? > nothing. just dark / i think we wait somewhere / i think i hear music in it / i don't remember, and that scares me
q: do you remember anything after you die? > just red, then my bed / a feeling like falling / nothing. it's a blank / a kind of quiet, then morning
d: it's not like sleeping. sleep at least feels like rest > maybe it's best we don't remember / whatever it is, you always come out of it / the dark is just the bed pulling you home
d: it's like the world blinks and i'm somewhere else > i think it's just the world catching its breath / the dark is just the bed pulling you home
d: the dark bit is short, i think, but i can't tell > maybe it's best we don't remember / short is good. short means it's nothing
d: sometimes that dark feels like it lasts forever > whatever it is, you always come out of it / forever and then morning. i hate that too
o: what if other people are in there too, waiting > i hope it's peaceful in there, then / if they're there, i hope they're ok
o: sometimes i think i hear voices in it > ok that's creepy / maybe it's just your own thoughts / i think i'd rather not know
o: what if the ones who don't come back stay in the dark > if they're there, i hope they're ok / i hope it's at least quiet for them
o: i'd like to know it's peaceful in there at least > i think it is. people come back calm / i hope it's peaceful too
l: i don't like not knowing. it's the worst part > you're not alone in not knowing / me neither, honestly
l: can we talk about something else soon, actually > ok. let's talk about sheep instead / sure. happy sheep. good sheep
l: i think i'll sleep with a torch by the bed tonight > yeah. torches all round / same, honestly
c: there's nothing between dying and waking up | it's just nothing between death and the bed, no place at all
y: yeah, just a blink | i think so too | nothing, it's a blank
n: there's something there. i can feel it | nah, it's a place. we just forget | i don't think it's nothing
""")

angle(RESPAWN, "respawn_stuff", "what you carry when you die", """
m: when you die everything you carried just spills out | all that work, scattered on the ground in a second
m: if you don't get back fast enough your stuff just vanishes | you come back with nothing but yourself
q: if you died right now, what would you miss most from your pockets? > my pickaxe, we've been through a lot / honestly nothing, it's all just stuff / the food. i always die hungry / my map. it took days
q: do you think the stuff we carry says who we are? > yeah, i carry way too much dirt / maybe, my pockets are a mess like me / no, it's what we do with it / kinda. you always carry flowers
d: you learn what matters when you have to choose what to save > losing it all teaches you to hold things loosely / you can always get more stuff. not more you
d: the first thing i grab is never the diamonds, weirdly > ha, mine's always the food / you can always get more stuff. not more you
d: i always think about my pickaxe first. it's silly > not silly. that pickaxe has been through a lot / the stuff doesn't remember you, you remember the stuff
d: coming back with nothing makes you feel light > losing it all teaches you to hold things loosely / maybe stuff is just borrowed
o: one time i went back to where i died and it was all gone. even my sword > ugh, that's the worst / that's sad. but you made a new sword
o: i stood where my stuff used to be for ages > i've done that too. just stood there / that's a weird kind of sad
o: it's like the world cleans up after us > the world's tidy like that, i guess / like we were never there. creepy
o: there's something sad about xp floating with no one to take it > that's oddly beautiful / i always grab it. feels rude not to
c: losing your stuff is worse than dying | dying is nothing. losing the stuff is the real death
y: true, the walk back is the worst | facts, rip my diamonds | yeah, you get yourself back but not your stuff
n: nah, stuff is stuff | you can get stuff again | i'd rather lose stuff than lose myself
""")

angle(RESPAWN, "respawn_careless", "whether coming back makes us careless", """
m: i think knowing we come back makes us reckless | i jumped off a cliff once just to save walking
m: we treat our lives like they're cheap | if we only had one life we'd live so differently
q: would you be braver or more scared if you only had one life? > way more scared / braver, weirdly. every second would matter / the same. i'm already scared / careful, not scared
q: do you think coming back makes us careless? > definitely. i've seen people dig straight down / a bit / no, it makes us brave / careful people come back too
d: i walk past lava without thinking now. that's not normal > careful is good. careless is what gets you / i'd rather you be scared than in lava
d: i used to be so careful my first few days > being a bit scared is healthy / some fear keeps you alive, even if you come back
d: dying stopped scaring me and that scares me > being a bit scared is healthy / i'd rather you be scared than in lava
d: maybe being careless is the price of coming back > some fear keeps you alive, even if you come back / careful is good. careless is what gets you
o: i watched someone run into a cave with no torches, laughing > some people want to feel something, i think / bet they came back without their stuff lol
o: the ones who are scared are the ones who last > maybe the scared ones are the smart ones / i like the careful ones. they're still around
o: i bet the oldest ones around are the careful ones > i like the careful ones. they're still around / careful isn't boring, it's alive
o: maybe that's why some of us hold back. we remember > maybe the scared ones are the smart ones / remembering is what keeps you careful
c: knowing you'll respawn makes you reckless | respawning made everyone careless
y: yeah, look at all the pillars into lava | true, i do dumb stuff | it does
n: nah, nobody likes dying | it makes you brave, not reckless | i'm careful every time
""")

angle(RESPAWN, "respawn_bed", "a bed deciding where you wake", """
m: it's funny how a bed decides where you come back | i put my bed somewhere and that's home now
m: if my bed breaks i wake up back where i started | my bed is the only thing that knows where i belong
q: if you could only respawn in one place, where would it be? > right where i am now / my house, by the window / somewhere near water / wherever my friends are
q: does a bed make a place home? > yeah, the bed is the heart of it / no, people make a place home / kinda, at least the world thinks so / a bed and a chest, that's a home
d: the first night in my own bed felt like a promise > a bed is a promise you'll come back / that's a nice way to put it
d: when someone breaks your bed you lose your place in the world > that's why i never let my bed get broken / the world remembers your bed even if it forgets you
d: i keep my bed close to my chests just in case > smart. you wake up right next to your stuff / that's why i never let my bed get broken
d: waking up at the start once felt like being a stranger again > the start isn't home. it's just where it began / that walk back is the worst
o: i woke up at the start once and walked days to get home > that walk must've been long / the start isn't home. it's just where it began
o: i wonder where i'd wake if i had no bed at all > back at the start, i guess. like a stranger / don't find out. keep a bed
o: the first bed i made was ugly but i loved it > ugly first beds are the best ones / mine was pink. i loved it too
o: a bed in a cave isn't a home. it's just a bed > yeah, home needs more than a bed / home is a bed someone cares about
l: knowing my bed is there makes the dark easier > you'll always have a bed at mine / then let's always make sure you've got one
l: i think i'm scared of dying without a bed somewhere > then let's always make sure you've got one / i'll help you find a safe spot for it / you'll always have a bed at mine
c: your bed is the most important thing you own | a bed is worth more than diamonds
y: true, it's your whole life really | yeah, no bed no home | agreed
n: diamonds though | nah, beds are cheap | a bed is just wool and planks
""")

angle(RESPAWN, "respawn_first", "the first death", """
m: i still remember my first death | the first time i died i thought that was it
m: my first death was so dumb, but it changed me | i didn't know we came back until i did
q: how did you die the first time? > a zombie at night, didn't see it / fell in a ravine / drowned trying to swim too far / creeper. of course
q: did you know we came back, the first time you died? > no, i was so scared / i guessed, but i wasn't sure / i didn't know anything back then / i thought i'd wake up somewhere else
d: i remember waking up and just sitting there for a while > i sat in my bed for ages too / first deaths stick with you
d: everything looked the same but i felt different > first deaths stick with you / everyone's first time is like that
d: after that first time, nights got scarier, not less > everyone's first time is like that / it's ok to remember it
d: i think my first death is when i grew up a bit > first deaths stick with you / it's ok to remember it
o: sometimes i think about the zombie that got me. is it still out there > zombies burn in the sun. it's long gone, i bet / it's probably still out there, groaning
o: i went back to where i first fell and put a torch there > putting a torch there is kind of beautiful / that's a nice thing to do
o: i don't think i ever told anyone about my first death > you can tell me / i'm glad you told me
o: funny how the first one is the one that matters > the first one is always the loudest / yeah. the rest just blur
c: your first death is the most important day you'll have | the first death is when you really start living
y: yeah, everything changes after | true, that's when it gets real | honestly yeah
n: nah, it was just a dumb death | the first day matters more | i barely remember mine
""")

# ======================================================================================================================
# Grief: missing someone gone.
GRIEF = theme(
    "grief", ["feelings", "death", "memories"], ["feelings", "friends", "memories", "philosophy"],
    react={"any": "i'm sorry. that's heavy | i think about stuff like that too | yeah. it doesn't really go away | "
                  "thanks for telling me",
           "casual": "man. that's rough, no joke | ok, no jokes. i'm listening",
           "hermit": "mm. the quiet after someone is loud. | we carry them. quietly.",
           "newbie": "i didn't know people could just... be gone | that makes me sad. i'm sorry",
           "helper": "i'm here, take your time | you don't have to carry that alone",
           "speedrunner": "i don't stop for much. i'd stop for that | ok. i've got time for this"},
    nudge="you can tell me more if you want | i'm listening | take your time | there's more to it, huh",
    close={"any": "thanks for listening | anyway. i'm ok, mostly | it helps to say it out loud | i just needed to say that",
           "hermit": "enough. thank you.", "newbie": "sorry for being sad lol", "casual": "ok. back to normal. ish"},
    bclose="anytime | i'm around if you need me | you're not alone in this | come find me whenever",
    yes="yeah. glad you get it | right | thank you",
    no="maybe. i see it different | that's fair, i guess | we all handle it our own way",
    sits={"grief": "i'm having a rough day | i keep thinking about someone who's gone | today's been heavy | "
                   "i'm not really ok today",
          "night": "nights are the worst for missing people | it's quiet. i keep thinking about the ones who are gone | "
                   "can't sleep, keep thinking about who's gone",
          "friends": "i haven't told anyone this | can i talk to you about something sad | "
                     "you're the only one i'd say this to"},
    needs=["!strangers", "!rivals"], trust=True)

angle(GRIEF, "grief_gone_friend", "a friend who walked off", """
m: i had a friend once who just walked off one day | someone i knew went exploring and never came back
m: i still look for a friend who left | there was someone i used to talk to every day. then nothing
q: do you think people who leave ever come back? > some do. the world's big but paths cross / i hope so / i think they find a new home / i don't know. i'd like to think so
q: ever lose someone and not know what happened to them? > yeah. that's the worst kind / once. i still wonder / no, and i hope i never do / everyone has, i think
d: not knowing is worse than knowing, i think > not knowing means there's still hope / yeah. not knowing is the worst
d: i keep a chest of their stuff, just in case > keeping their chest is kind of beautiful / maybe they'll come back for it
d: i walked the way they went for a whole day > maybe they're out there building something / the world's huge. they could be anywhere, alive
d: sometimes i think i see them on the horizon > the world's huge. they could be anywhere, alive / maybe one day it'll really be them
o: i hope they found somewhere nice, with water and trees > i hope they found a good spot / i bet they did
o: i think about what i'd say if they walked up right now > you'd just say hi. that's enough / you wouldn't need to say anything
o: i never got to say bye. that's the part > the bye would've been hard anyway / yeah. that's the part that stays
o: i wonder if they think about me too > i bet they do / of course they do
l: i don't want to lose anyone else > i'm not going anywhere / i get it. i'll stick around
l: i think that's why i'm scared to get close to people now > i get it. i'll stick around / that makes sense. take it slow
l: so... don't go walking off, ok? > deal. if i go, i'll say bye first / i'm not going anywhere
c: it's better to say bye than to just leave | leaving without saying bye is the worst thing you can do
y: yeah, always say bye | true. it stays with people | agreed
n: sometimes you can't say bye | some people just need to go | saying bye is harder than leaving
""")

angle(GRIEF, "grief_pet", "losing a pet", """
m: my dog died to a creeper and i still think about him | my dog used to wait for me at the door. not anymore
m: i miss my dog. he'd sit by the door | had a dog that followed me everywhere. not anymore
q: do you think pets know how much we love them? > yeah, look how they wag / i think so / they know. they always come back to you / i hope so. i told mine all the time
q: would you get a new pet after losing one? > not for a while / yeah, but it'd never replace them / i don't know if i could / maybe. they'd want me to
d: i put my dog's collar colour on a banner by my door > a banner for him is a nice idea / that's a lovely way to remember him
d: the worst part is the quiet when i get home > that quiet is love, kind of / i'm sorry. that's the hardest part
d: i still save bones like my dog's gonna want them > that's love, kind of / he had a good life with you
d: i keep looking at the spot where my dog slept > he had a good life with you / i'm sorry. that's rough
o: pets don't come back. that's what gets me > i know. it's not fair at all / yeah. that's the hard part
o: we get to respawn and they don't. it's not fair > i know. it's not fair at all / yeah. it's not
o: i named my dog and that made it worse > naming them is what makes them family / that's what made him yours
o: my dog followed me into a cave i shouldn't have gone into > it's not your fault / he wanted to be with you. that's why he followed
l: i think i'll tame another one. one day > whenever you're ready / he'd want you to, i think
l: can we go see the wolves sometime? just to look > sure. just to look / i'd like that
c: pets matter more than any diamond | losing a pet is worse than losing gear
y: by far | yeah, gear comes back | of course
n: i love them, but it's not the same kind of loss | gear keeps you alive though | hmm, depends on the gear
""")

angle(GRIEF, "grief_empty_house", "an empty house", """
m: there's a house in town nobody lives in anymore | i walk past an empty house and wonder who built it
m: someone left their house and the torches are still lit | there's a bed in an old house nobody sleeps in
q: do you think a house remembers who lived in it? > i like to think so / no, but people do / the chests remember / a little, in the way it's built
q: should someone move into a house that's empty? > yes, houses want to be lived in / no, it's still theirs / only if they're not coming back / maybe, if they look after it
d: there's one by the river, chests still full. nobody touches them > leaving it how it is feels right / that's someone's whole story in one house
d: the door's open on one and it creaks in the wind > maybe we should close it, for them / that's sad. a door nobody walks through
d: someone planted flowers by one once. they still grow > maybe we should keep it nice, for them / flowers mean they cared about it
d: on one you can see where they ran out of planks on the roof > that's someone's whole story in one house / maybe we should finish it for them
o: i went in one once and there was a sign that said home > a sign that says home. that's rough / that one hurts
o: what if they come back and find someone in their bed > if they come back they'll be glad it's still there / that'd be awkward lol
o: i fixed a hole in one's roof. didn't feel right leaving it > fixing the roof was kind / they'd be glad, i think
o: empty houses are the saddest builds > yeah. builds need people / they really are
c: an empty house should be left alone | nobody should move into someone else's house
y: yeah, it's theirs | true, out of respect | agreed
n: houses are for living in | if they're gone, it should be used | a house rots if no one lives there
""")

angle(GRIEF, "grief_remember", "how to remember someone", """
m: i want to remember the people i've lost somehow | i made a sign for someone who's gone
m: i think we should mark the places people are lost | i keep the names of the ones i lost in a book
q: how do you want to be remembered? > as someone who helped / by my house, i guess / as a good friend / i don't need to be, honestly
q: what's the best way to remember someone? > keep doing the stuff they loved / a sign with their name / tell stories about them / build something for them
d: i keep telling the story of how i met an old friend who's gone > stories keep people around / that's a really nice way to remember them
d: i planted a tree where an old friend used to sit > a tree is perfect. it keeps growing / that's beautiful, honestly
d: i still use the pickaxe an old friend gave me > using their pickaxe is a good way to keep them / that's the best way to remember someone
d: every sunflower makes me think of someone i lost > that's a really nice way to remember them / they'd like that, i bet
o: i'm scared one day i'll forget an old friend's voice > you won't. the important stuff stays / write it down then. books don't forget
o: i forgot a friend's name for a second last week. it scared me > that happens. it doesn't mean you didn't care / write it down then. books don't forget
o: memories fade like a path the grass grows over > then keep walking the path / that's why we have to keep telling them
o: i wonder if anyone will tell stories about me > i'll tell stories about you / you'll be a story, don't worry
l: i'd like to build something for the ones i lost. would you help? > of course / i'd be honored / just tell me where
l: i think i'll build a little garden for the ones who are gone > i'd be honored to help / just tell me where. i'll bring flowers
c: graves and signs don't matter, memories do | you don't need a sign to remember someone
y: yeah, it's in your head | true | agreed
n: signs help, though | i like having a place to go | a sign stays when memory fades
""")

angle(GRIEF, "grief_graves", "graves on the hills", """
m: i saw a little grave on a hill today | someone made a grave out of cobble and a sign
m: there's a grave by the river with flowers on it | i made a grave once, for my old horse
q: do graves matter if we all come back? > they're for the ones who don't / they're for the living / not really, but i like them / yeah, they mean someone was loved
q: would you want a grave? > a small one, with flowers / no, just keep my house nice / a big one, obviously lol / under a tree, i think
d: the grave i saw just had a sign with a name and a day > someone loved them. that's what a grave says / small graves hit the hardest
d: somebody keeps putting fresh flowers on the one by the river > flowers mean someone still remembers / that's love, right there
d: the one on the hill has the best view around > they picked a nice spot / that's where i'd want mine
d: the cobble one was so small, but someone took care building it > small graves hit the hardest / someone loved them. that's what a grave says
o: we come back but some things don't. animals. friends who wander > that's who graves are for, i think / yeah. those are the ones we lose
o: who do you think those graves are for > maybe someone like us, before / it doesn't matter who. someone
o: i stood by a grave for a while once and didn't know why > i think standing there is the point / i've done that too
o: i wanted to add a flower to a grave but felt like i shouldn't > you should add one. it's kind / i think they'd like it
c: graves are for the living, not the dead | graves are more for us than for them
y: yeah, we need somewhere to go | true | that's what they're for
n: they're for both | i think they're for the dead too | nah, they honour them
""")

angle(GRIEF, "grief_miss_me", "whether anyone would miss you", """
m: sometimes i wonder if anyone would miss me | if i walked off, i don't think anyone would notice
m: i think about who'd notice if i stopped coming back | i don't know who'd come looking for me
q: if i disappeared, would you come looking? > obviously / yeah, i'd check every cave / i'd look until i found you / you're not disappearing, so
q: do you think people notice when you're gone? > more than you think / yeah, the quiet gets loud / some do / the ones who matter do
d: i'm not sad, i just wonder > i'd notice. i'd notice the first day / people notice more than they say
d: my house would just sit there with the door open > i'd close the door for you. then come looking / i'd notice. i'd notice the first day
d: i don't talk much, so maybe nobody would notice > quiet people get missed too / you matter more than you think
d: i don't even know who'd get my chests > forget the chests. i'd miss you / i'd look after them till you're back
o: i think everyone wonders that at night > then i'll say it again. i'd miss you / yeah. nights do that
o: maybe that's why i keep saying hi to people > saying hi matters. keep doing it / it works. i always notice you
o: i want someone to know where my house is, just in case > tell me where it is. i'll remember / i'll write it down
o: it's nice to hear someone say it > then i'll say it again. i'd miss you / anytime
l: thanks. i needed that more than i thought > anytime. i mean it / i'm glad i was here for it
l: ok. i feel a bit better now > good. you're stuck with me / i'm glad
""")

# ======================================================================================================================
# Memory: before we arrived, why here, names, knowing things, forgetting.
MEMORY = theme(
    "memory", ["memories", "philosophy"], ["memories", "philosophy", "dreams", "feelings"],
    react={"any": "huh. i've never thought about that | that's a weird one. i like it | i honestly wonder about that too | "
                  "now you've got me wondering",
           "casual": "my memory's so bad i forget where my bed is lol. but yeah | lol i can't remember yesterday. but fr",
           "hermit": "the past is a cave. no torch reaches it. | some doors stay shut.",
           "newbie": "wait, i thought only i couldn't remember | i only remember like a week back lol",
           "helper": "it's ok not to remember. we're here now | that's a gentle thing to wonder about",
           "speedrunner": "don't look back, it costs time | i remember routes, not feelings"},
    nudge="something made you think of that | go on | you remember something, don't you | keep going",
    close={"any": "anyway. here's where we are now | maybe it doesn't matter where we came from | "
                  "glad i remember you, at least | weird to think about",
           "hermit": "the now is enough.", "casual": "ok my brain hurts now lol",
           "newbie": "i'm gonna write stuff down from now on"},
    bclose="let's make some good ones then | i'll remember this talk, at least | here's to new memories | "
           "whatever came before, this is good",
    yes="right? | exactly | glad you see it too", no="hm. maybe | we remember different, i guess | fair",
    sits={"night": "had a weird feeling tonight, like remembering something | quiet nights make me think about before | "
                   "can't sleep. trying to remember stuff",
          "calm": "i'm in a thinking mood | feeling calm, so here's a weird one | random thought while it's quiet",
          "friends": "can i tell you something i never tell anyone | you ever talk about this stuff? | "
                     "weird thought, but you're the one to tell"})

angle(MEMORY, "memory_before", "anything from before", """
m: i don't remember anything before i got here | sometimes i try to remember before, and there's just nothing
m: it's like i started the day i arrived | i keep feeling like there was something before this
q: do you remember anything from before you arrived? > nothing at all / just a feeling, like a dream / i remember my name, that's it / i don't like to think about it
q: where do you think we were before we got here? > nowhere. we just started / somewhere else, like another world / asleep, maybe / i ask myself that every night
d: my first memory is grass and a tree and the sun coming up > the first sunrise is my oldest memory too / i think we just began, like a sapling
d: i knew how to punch a tree but not why > maybe there's nothing to remember / i think we just began, like a sapling
d: it's like waking up in the middle of a sentence > maybe before doesn't matter / maybe there's nothing to remember
d: the oldest thing i remember is being hungry > ha, same. hungry and confused / maybe before doesn't matter
o: what if we lived whole lives before and forgot > then let's make this one worth remembering / maybe some things stay even when memories don't
o: sometimes a place feels familiar and i've never been there > familiar places freak me out too / maybe some things stay even when memories don't
o: i get this feeling near water, like i've been somewhere like it > maybe some things stay even when memories don't / i get that with forests
o: what if we'll forget this too one day > i hope not. i like this one / then let's make this one worth remembering
l: honestly it scares me a little, not knowing > you're not the only one / it scares me too sometimes
l: i'm glad that whatever came before, i ended up near you > same. here is good / me too, honestly
c: it doesn't matter where we came from | what happened before we got here doesn't matter
y: yeah, here is what counts | true | agreed, it's the now that matters
n: it matters, i want to know | nah, it's part of who we are | i'd give a lot to know
""")

angle(MEMORY, "memory_why_here", "why we woke up here", """
m: i keep wondering why we woke up here of all places | nobody asked me if i wanted to be here
m: i just opened my eyes and there was a world | why here, why this world
q: why do you think we woke up here? > to build something / no reason, it just happened / someone wanted us here / to find out, i guess
q: if you could wake up anywhere, where would you pick? > a big flower field / by the ocean / right here, honestly / a village with friends already in it
d: us waking up here feels too big to be an accident > accidents can still be good ones / maybe it doesn't need a reason
d: maybe the world needed someone to walk around in it > i like to think the world wanted company / then we're doing our job lol
d: the world was already here, waiting, like it knew > i like to think the world wanted company / the sheep were waiting for us, clearly
d: everything was ready. trees, water, sheep > the sheep were waiting for us, clearly / maybe it doesn't need a reason
o: what if each of us woke up for a different reason > i like that one / then i'm still looking for mine
o: maybe the reason is the thing we're supposed to find > then i'm still looking / i like that one
o: what if there's no reason and that's the point > that's kind of freeing, actually / i can live with that
o: maybe we're here because someone hoped we'd be happy > then let's be happy, just in case / i like that one
c: there's a reason we woke up here | we were put here on purpose
y: i think so too | has to be | yeah, it's too perfect otherwise
n: nah, it just happened | there's no reason, and that's fine | i don't think so
""")

angle(MEMORY, "memory_names", "where our names came from", """
m: i've been wondering where my name came from | nobody gave me my name. i just knew it
m: some names have numbers in them. who decides that | i like my name but i didn't pick it
q: do you think we picked our own names? > nah, it was just there / maybe, before we forgot / i hope so, mine's weird / i'd never pick mine lol
q: if you could change your name, would you? > never, it's mine / maybe drop the numbers / i'd pick something cooler / i like what people call me better
d: a name feels like the only thing we brought with us > the name came with you. that's something / maybe your name is a message from before
d: my name has letters i can't even say out loud > lol same. mine has an underscore / your name suits you anyway
d: when someone says my name i feel more real > names are like a sign on a house. someone lives here / your name suits you, honestly
d: i think names are clues to before > maybe your name is a message from before / then mine's a weird clue lol
o: what if our names are all we have left of before > then keep it. it's precious / a name is how the world holds onto you
o: i wonder who else had a name like mine > probably nobody. yours is one of a kind / there's only one you, i bet
o: when people shorten my name it feels like they know me > nicknames are how you know someone likes you / that's why i shorten yours
o: someone i didn't know said my name once. it felt huge > maybe they'd heard about you. that's nice / a name is how the world holds onto you
c: a name you choose is better than one you're given | you should get to pick your own name
y: yeah | true, mine's a mess | agreed
n: nah, your name is who you are | the one you're given has history | i like mine as it is
""")

angle(MEMORY, "memory_knowing", "how we knew things", """
m: how did i know how to make a crafting table on my first day | i knew how to punch a tree before i knew my own face
m: some things i just knew, like hunger and sleeping at night | nobody taught me to make a pickaxe. i just did
q: how do you think we knew how to craft without being taught? > we remembered from before / it's just in us / we tried stuff until it worked / i didn't. i was useless at first
q: what's something you knew without ever learning it? > that zombies are bad / how to swim / that the night is scary / how to plant seeds
d: it's like the world taught us before we woke up > maybe the basics are a gift and the rest is up to us / everybody knows the start. nobody knows the middle
d: i knew wood makes planks the way i knew i was hungry > maybe the basics are a gift and the rest is up to us / everybody knows the start. nobody knows the middle
d: the hard stuff though, like redstone, i had to learn > knowing a little and learning the rest feels fair / i still don't know half the stuff i should
d: some things you know, some things you earn > knowing a little and learning the rest feels fair / everybody knows the start. nobody knows the middle
o: what if there's stuff we know and haven't found out we know yet > like a skill hiding in you / maybe. i'm still waiting for mine
o: maybe that's why some people are just good at things > maybe. i'm still waiting for mine / like a skill hiding in them
o: i wonder what i'd know if i'd woken up somewhere else > you'd be you anywhere, i think / probably how to swim better lol
o: maybe what we learn here is what we take next > i hope we take it all with us / then i'm taking a lot of creeper lessons
c: it's better to learn things the hard way | what you learn yourself is worth more than what you just know
y: yeah, you remember it better | true | the hard way sticks
n: nah, a shortcut's a shortcut | i'll take knowing for free, thanks | the hard way just gets you killed
""")

angle(MEMORY, "memory_forgetting", "forgetting things", """
m: i keep forgetting things lately | i forgot where i built my first hut
m: i can't remember my first night anymore, just bits | i lost a whole mine once. just forgot where it was
q: what's the oldest thing you still remember clearly? > my first sunrise / the first creeper that blew me up / the taste of my first bread / meeting you, honestly
q: is it bad that i forget stuff? > nah, everyone does / only if it's where your diamonds are / it means you're making room / depends what you forget
d: i think i forget the bad stuff faster, which is nice > forgetting the bad stuff is a gift / lucky. i remember every creeper
d: my first hut's still out there somewhere and i can't find it > we should write more signs / it's probably still standing, waiting
d: i remember feelings better than places > feelings are the important part anyway / same. places blur, feelings don't
d: sometimes i find old torches of mine and don't remember placing them > old torches are like notes from past you / past you was looking out for you
o: what if someone else finds my old hut and wonders about me > then they'll wonder nice things / leave a sign in it, just in case
o: one day i'll forget this talk too, probably > i'll remind you / then we'll just have it again
o: i feel bad forgetting places that kept me safe > those places did their job. they don't mind / that's kind of sweet
o: maybe the world remembers so we don't have to > i like that. the world as a diary / that's a nice way to see it
c: forgetting is a good thing | it's good that we forget stuff
y: yeah, you'd go crazy remembering everything | true | some stuff should go
n: no, i want to keep it all | forgetting is how you lose people | nah, i hate forgetting
""")

angle(MEMORY, "memory_first_day", "the first day", """
m: i still think about my first day sometimes | my first night was the scariest thing ever
m: my first day i didn't even know what a zombie was | i dug a hole on my first night and just sat in it
q: what do you remember about your first night? > hiding in a dirt hole / zombies groaning outside / i didn't sleep at all / i made a bed and felt like a genius
q: would you go back to your first day if you could? > no way, too scary / yeah, it was simple / just to tell myself stuff / only to see it all new again
d: on day one everything was new. even dirt was exciting > everything being new is a feeling you only get once / being new is scary and kind of great
d: the first sunrise after my first night felt like winning > the first sunrise is the best one / it was winning, honestly
d: i didn't know anything back then and somehow i was happier > being new is scary and kind of great / everything being new is a feeling you only get once
d: i'd tell my first-day self to stop digging straight down > lol. everyone needs that lesson / you learned so much since then
o: sometimes i wish i could see the world new again > you can, a little. go somewhere you've never been / same. i'd love that
o: i think the newbies have something we lost > they do. let's not tell them it gets normal / yeah. they still get excited about sheep
o: i look at newbies and remember how big it all felt > big things get small when you know them / it still is big. we just got used to it
o: it's weird that the scariest night is a happy memory now > that's how memories work, i guess / funny how that happens
l: i'm glad i'm not that scared little one anymore > you came a long way / i'm glad i get you now
l: i think you would've liked me back then. i was nicer > you're still nice. just braver / i like you now, so
c: your first day is the best day you'll ever have | the start is the best part of all of it
y: yeah, everything was new | true | honestly, yeah
n: nah, i was terrified | it gets better, not worse | i like knowing stuff more
""")

# ======================================================================================================================
# Purpose: why build, why keep going, the point of diamonds, enough, finishing.
PURPOSE = theme(
    "purpose", ["philosophy", "meaning"], ["philosophy", "building", "plans", "meaning"],
    react={"any": "that's a big one | i ask myself that a lot | ngl i don't have an answer | "
                  "you're in a thinking mood today",
           "casual": "lol existential crisis hours. but fair | bro just mine and don't think. ok but actually",
           "hermit": "the point is the doing. | the stone doesn't ask why.",
           "newbie": "i didn't know there was supposed to be a point lol | i thought the point was to have fun",
           "helper": "the point is each other, i think | you matter, if that helps",
           "speedrunner": "the point is the end. get there fast | i always know my next goal. that's my point",
           "builder": "the point is making something that wasn't there | i build, so i know the point",
           "hoarder": "the point is having stuff. obviously | more chests. that's the point"},
    nudge="you've been thinking about this a lot | go on | something's bugging you | say more",
    close={"any": "anyway. back to work, i guess | maybe the point is just this, talking | i'll figure it out one day | "
                  "it's a good question to carry",
           "hermit": "work. then rest. then work.", "casual": "ok enough thinking, i'm going mining lol",
           "speedrunner": "back to it. clock's running"},
    bclose="go build something | the point is you're here | let's find out together | ask me again tomorrow",
    yes="right? | exactly | see", no="we'll see | maybe you'll get it someday | fair, i guess",
    sits={"bored": "i'm bored and it's making me think too much | when there's nothing to do i start asking why | "
                   "so bored i'm getting deep",
          "built": "finished my {a.built} and now i feel kind of empty | i just finished something and now what | "
                   "done building. weird feeling",
          "evening": "end of another day. what was it for | the day's ending and i'm wondering what i did | "
                     "another day done",
          "diamonds": "i've got diamonds and i don't feel any different | got {a.diamonds} diamonds and i'm still not happy | "
                      "i have diamonds now. now what"})

angle(PURPOSE, "purpose_why_build", "why we build", """
m: why do we build anything if we could live in a hole | i built a whole house and still sleep in one room
m: sometimes i wonder why i build at all | we could sleep in caves, but we build towers
q: why do you build? > because it's fun / to feel safe / to leave something behind / i honestly don't know
q: do you think we'd still build if nobody could see it? > yeah, i build for me / probably not as much / i'd build even more / of course
d: i think i build because the world feels less big with walls > walls make the world smaller in a good way / maybe that's enough of a reason
d: a hole keeps you alive. a house keeps you you > maybe that's enough of a reason / ok that one's going on a sign
d: building feels like saying i was here > building is a way to hold on / maybe that's enough of a reason
d: when i build i stop worrying about everything else > i build to feel calm too / building is a way to hold on
o: the weird part is i'm never happy with a build for long > same. i see every mistake after / that's every builder, i think
o: i always want the next build, bigger > the next one is always the one / that's how you get good, though
o: i think i'm building the same house over and over, trying to get it right > that's every builder, i think / maybe it's never right and that's ok
o: what if the building is the point, not the house > yeah. the house is just what's left over / i like that a lot
c: building is the whole point of being here | we're here to build things
y: yeah, what else is there | true | agreed
n: nah, exploring is the point | building is just what we do to pass time | there's more than building
""")

angle(PURPOSE, "purpose_keep_going", "why keep going", """
m: some days i wonder why i keep going | every day it's mine, eat, sleep, again
m: i don't always know what i'm working towards | sometimes it feels like the days are all the same
q: what keeps you going? > the next sunrise / my friends / the next big build / curiosity, mostly
q: do you ever feel like stopping? > sometimes, yeah / no, there's always something / once, after i lost everything / every rainy day lol
d: i think it's small stuff. a good meal, a nice view > small stuff is the real stuff / a good meal fixes a lot
d: i keep going because tomorrow might be different > tomorrow being different is a good enough reason / it usually is, a little
d: the days are the same but i'm not > you're different every day, yeah / that's a good way to see it
d: i don't need a big reason, just the next thing > the next thing is plenty / same, honestly
o: on bad days i just do one thing. mine one block > one block is still a block / that's a nice way to get through
o: i think i keep going for the people who'd notice if i stopped > i'd notice / that's a good reason
o: maybe nobody has a big reason. we just pretend > i think everyone pretends a bit / that's kind of a relief
o: the sun keeps coming up, so i do too > that's a nice way to see it / same as the sun. i like that
l: today was one of those days honestly > i'm glad you said something / one block at a time. together
l: talking about it helps more than i thought > then let's talk more often / i'm glad you said something
c: you don't need a reason to keep going | nobody needs a big reason to keep living
y: yeah, just keep going | true | the next day is reason enough
n: everyone needs a reason | i need goals or i fall apart | nah, i need a reason
""")

angle(PURPOSE, "purpose_diamonds", "the point of diamonds", """
m: what's even the point of diamonds | everyone wants diamonds but nobody knows why
m: i found diamonds and felt nothing after a minute | diamonds are just shiny rocks we agreed are special
q: what's the point of diamonds, really? > the best tools / to show off / they're pretty, that's enough / getting them is the fun part
q: would you trade all your diamonds for something? > for a good friend, sure / never / for a quiet life by the sea / for a nice day off
d: the moment i find them is the best part. after that it's just stuff > i like having them. but you're not wrong / maybe the looking is what you wanted
d: i spent days looking and then put them in a chest > maybe the looking is what you wanted / i like having them. but you're not wrong
d: a diamond pickaxe mines more diamonds. it's kind of a loop > the loop is funny when you say it out loud / diamonds are for doing other things faster
d: we dig for them because everyone else does > i like having them. but you're not wrong / i dig for them because pickaxes break lol
o: if diamonds were everywhere would anyone care > probably not / ooh. that's deep
o: i think we're looking for something else and diamonds are the closest > that says something / ooh. that's deep
o: i'd rather have a good view than a chest of diamonds > then build a house with a view / same, honestly
o: the chest with my diamonds is the one i open least > lol that says a lot / then they're not doing much, huh
c: diamonds are overrated | diamonds don't make you happy
y: yeah, they're just rocks | true, iron does most of it | agreed
n: a diamond pickaxe says otherwise | nah, finding them is the best feeling | speak for yourself
""")

angle(PURPOSE, "purpose_enough", "when you have enough", """
m: i don't know when i'll have enough stuff | i keep filling chests and making more chests
m: i'll be happy when i have one more thing. i always think that | how many chests is enough chests
q: what would enough look like for you? > a house, a farm, a friend / i'll never have enough / a full chest of food / honestly i think i already have it
q: do you think anyone ever has enough? > the hermits, maybe / no, everyone wants more / people who stop counting / yeah, but they don't notice
d: every time i get what i wanted, i want the next thing > wanting more isn't bad. it's just tiring / maybe the trick is noticing what you've got
d: i have more than i had on day one and i'm not happier > day one you had nothing and you were fine / maybe the trick is noticing what you've got
d: my chests are full and i still go out every day > maybe the going out is what you like / maybe the trick is noticing what you've got
d: maybe enough isn't a thing, it's a feeling > enough is when you stop counting / maybe the trick is noticing what you've got
o: what if i got everything and still felt like this > then it was never about the stuff / that's very honest
o: maybe i'm filling chests to fill something else > then it was never about the stuff / that's very honest
o: i think i'm scared of running out, not wanting more > being scared of running out makes sense / that's very honest
o: the hermits seem happy with a hole and a furnace > maybe they know something / then it was never about the stuff
c: you can never have too much stuff | more stuff always makes life better
y: yeah, more chests | true | always
n: stuff is weight | nah, too much stuff is a burden | more stuff just means more sorting
""")

angle(PURPOSE, "purpose_finished", "whether anything's ever finished", """
m: i don't think i've ever finished anything | every build i finish needs one more thing
m: is anything ever really done | i keep calling my house finished and then adding a room
q: have you ever finished something and felt done? > once, a little bridge / never / my first house, for a day / i don't think it's possible
q: is it better to finish things or to keep adding? > finish, then start something new / keep adding forever / finish, it feels so good / depends on the build
d: when i finish something i feel empty, so i start again > empty after finishing is normal / unfinished means there's still something to do
d: the world is never finished either. trees keep growing > the world being unfinished makes me feel better / true. even the world adds rooms
d: maybe being unfinished is what keeps you going > unfinished means there's still something to do / the world being unfinished makes me feel better
d: i have like six builds half done > six half builds is very you / lol same
o: what if the day we finish everything is the day we stop > then never finish / that's one way to see it
o: i kind of like that my house is never done > a house that's never done is a house that's lived in / i get that
o: maybe nobody finishes, we just stop at some point > that's one way to see it / i get that
o: i'm scared of finishing, honestly > then never finish / i get that
c: nothing is ever really finished | there's no such thing as a finished build
y: true | yeah, always one more thing | agreed
n: i finish stuff all the time | nah, you just call it done | my bridge is finished, thanks
""")

angle(PURPOSE, "purpose_small_things", "meaning in small jobs", """
m: i think planting wheat is the most meaningful thing i do | the small jobs feel better than the big ones
m: there's something good about tidying up after yourself | taking down my old pillars felt weirdly good
q: what's the smallest thing that makes your day feel worth it? > a full furnace / a planted sapling / a fed animal / a torch in a dark spot
q: do small jobs matter as much as big ones? > more, maybe / they add up / not really / they're the big ones in tiny pieces
d: when i plant a sapling i think, someone will cut that one day > someone will be glad for that tree / that's a nice thought
d: big builds are mostly small jobs in a row > big things are just small things in a line / true. one block at a time
d: lighting up a dark spot makes the world a bit safer > every torch is a little kindness / someone will be glad it's there
d: feeding the animals feels like being needed > feeling needed is a big deal / they need you, for real
o: maybe a life is just small jobs done with care > that sounds like a good life / i'd take that life
o: i think i'd like to be remembered for small things > small things are how people remember you / you will be
o: if i only did small things for a hundred days, would that be enough > more than enough / i think so, yeah
o: the big stuff gets the credit but the small stuff holds it up > like the floor under the house / true. nobody thanks the floor
c: the little jobs matter more than the big projects | small stuff is the real work
y: yeah | so true | agreed
n: nah, big builds are what count | small stuff is just chores | the big stuff lasts
""")

angle(PURPOSE, "purpose_a_goal", "a goal set for us", """
m: sometimes it feels like the world wants us to do something | i wonder if there's a goal we're supposed to reach
m: some people talk about the end like it's a goal | what if we're supposed to be somewhere and we're just wandering
q: do you think we're meant to do something here? > survive, i guess / find something / no, we just live / be nice to each other, maybe
q: if there was a big goal, would you go for it? > yeah, all in / nah, i like my farm / only if friends come / maybe one day
d: the world has strongholds and stuff, like it's leading us somewhere > it does feel like that / breadcrumbs, kind of
d: the world feels like breadcrumbs sometimes > breadcrumbs is a good word for it / it does feel like that
d: maybe the goal is just to live well > living well is plenty of goal / maybe the goal is whatever you pick
d: i think the world is more like a garden than a race > i like the garden thing / then i'm a very slow gardener
o: what if the people who chase the goal miss everything else > they do miss stuff, i think / probably, yeah
o: i met someone who only talked about the end. they seemed lonely > that sounds lonely / some people need a goal like that
o: maybe the real goal is to not need one > that's a very calm way to live / i like that
o: i think i'd rather wander than finish > wander with me then / that's a very calm way to live
c: there's a goal and we should be working towards it | we're all meant to reach the end one day
y: yeah, i feel it too | true | that's what i think
n: nah, live your life | there's no goal, just days | the end can wait
""")

# ======================================================================================================================
# The world's edge: where it ends, the land appearing, past the ocean, maps, other worlds.
EDGE = theme(
    "edge", ["exploring", "philosophy", "world"], ["exploring", "biomes", "philosophy", "world"],
    react={"any": "i've wondered that on long walks | that's a big thought for a small brain | huh. nobody really knows | "
                  "that's the kind of thing i think about on hills",
           "nomad": "i've walked a long way. it never ends | that's why i keep walking",
           "casual": "lol imagine walking off the edge. but seriously | bro the world's too big for my legs",
           "hermit": "edges are for people who stop walking. | the world doesn't end. we do.",
           "newbie": "woah. i never thought the world could just end | i haven't even left my valley yet",
           "speedrunner": "too far to be worth it | i'd only go if it was on the way",
           "builder": "i'd build a wall at the edge. a nice one"},
    nudge="you've thought about going, huh | go on | you've been staring at the horizon again | keep going",
    close={"any": "anyway, it's far. for now | one day i'll walk until i find out | the horizon always wins | "
                  "i like not knowing, kind of",
           "hermit": "keep walking.", "casual": "ok but who's carrying the food lol", "newbie": "i wanna go see it someday"},
    bclose="take me with you if you go | bring a lot of bread | let me know what's out there | the edge can wait for us",
    yes="right? | exactly | see", no="maybe | ok, we'll see | agree to disagree",
    sits={"evening": "watching the sun go down makes me wonder how far it goes | look at that horizon | "
                     "sunsets make the world feel huge",
          "calm": "random thought | i've been staring at the horizon | feeling chill, thinking big",
          "nohome": "no home means i keep walking. makes you wonder how far it goes | walking every day gets me thinking",
          "bored": "bored enough to think about walking forever | nothing to do, so i'm wondering how big the world is"})

angle(EDGE, "edge_where", "where the world ends", """
m: i wonder where the world ends | if you walked in one direction forever, what would happen
m: there has to be an edge somewhere | i can't imagine a world without an end
q: do you think the world has an edge? > yeah, far far away / no, it just keeps going / maybe a wall / i don't want to know
q: what do you think is at the end of the world? > a wall / more ocean / nothing, just dark / another world
d: i walked for three days once and it just kept making land > three days is nothing, they say / i bet someone's tried going further
d: the further you go the stranger it gets, people say > some things are better unknown / i bet someone's tried
d: maybe the edge is so far nobody has ever reached it > if it's that far, it may as well not exist / some things are better unknown
d: i think it'd be scary to find it > some things are better unknown / i'd want to see it anyway
o: i heard if you go far enough the land breaks apart > that sounds made up but i love it / ok that's creepy
o: what if the edge is just where nobody's walked yet > the edge of what we know, yeah / chasing an edge sounds like my life
o: maybe the world grows as we walk into it > then we're making the world by walking / that sounds made up but i love it
o: the edge might be moving away from us the whole time > chasing an edge sounds like my life / then we'll never catch it. good
c: the world has no end | you could walk forever and never reach the edge
y: i think so too | yeah, it just keeps going | true
n: everything ends somewhere | nah, there's an edge | there's a wall out there, i know it
""")

angle(EDGE, "edge_unfolding", "the land appearing as you walk", """
m: sometimes i feel like the land isn't there until i walk to it | the world feels like it's being made just ahead of me
m: i get this feeling the far hills don't exist until i look | it's like the world unfolds as you go
q: do you think places exist when nobody's there to see them? > of course they do / i'm not sure anymore / only if someone remembers them / that's too deep for me
q: ever get the feeling the world is making itself as you go? > all the time / once, in the fog / no, that's creepy / yeah, mountains just appear
d: in the fog far away the world just isn't there yet > maybe the world saves its energy / fog is just fog. probably
d: when i turn around the path behind me feels less real > that's the creepiest nice thing i've heard / i never look back, so idk
d: my crops never grow while i'm away. like they wait for me > the crops thing is real, i've noticed it too / that's the creepiest nice thing i've heard
d: the village i left feels frozen till i'm back > that's the creepiest nice thing i've heard / i get that too
o: what if the whole world is asleep except where we are > then let's visit places so they don't get lonely / that's kind of sweet
o: it's like we carry a little bubble of awake around > a bubble of awake. i like that / that's kind of sweet
o: if that's true, a place with nobody is just waiting > waiting for us, like a dog by a door / then let's visit places so they don't get lonely
o: i like the idea that the world waits for us > waiting for us, like a dog by a door / that's kind of sweet
c: places stop when nobody's there | the world only moves where someone is
y: i've seen it with crops, yeah | true | i think so too
n: nah, that's just slow crops | the world doesn't need us to exist | that's silly
""")

angle(EDGE, "edge_past_ocean", "what's past the ocean", """
m: i want to know what's past the big ocean | you can stand on the beach and see nothing but water
m: the ocean feels like the edge, but it isn't | somewhere on the other side someone's looking back
q: what do you think is on the other side of the ocean? > more land, like here / a whole other people / more ocean / i'll never know
q: would you sail across the big ocean if you could? > in a heartbeat / only with food and friends / no, the drowned scare me / maybe one day
d: i stood on the beach till dark just looking > the beach at dusk is the best spot / i've done that too
d: in a boat it's just you and the water for ages > boats are lonely and great / that sounds peaceful and scary
d: the farther out you go, the deeper and darker it gets > dark deep water freaks me out / that's where the drowned live
d: i heard someone found an island out there with mushrooms > mushroom islands are real, i think / i want to see that
o: i think the ocean is where the world keeps its secrets > that's poetic / we should go one day
o: crossing the sea would be like being born again > that's poetic / we should go one day
o: what if the other side is just like here but nobody's ever met > i'd like to meet them / we should go one day
o: everyone on that side is wondering about us > hi, other side / i'd like to meet them
c: the ocean is the most beautiful part of the world | the sea is better than any land
y: true | the ocean is everything | agreed
n: nah, i like ground under my feet | mountains though | the ocean's scary
""")

angle(EDGE, "edge_maps", "maps that can't hold it all", """
m: i made a map and it only covers a tiny bit of the world | every map i make feels small
m: my wall of maps is huge and it's still just a corner | a map shows you how much you haven't seen
q: would you want to map the whole world? > yes, every block / no, it'd take forever / just the parts i love / i'd rather not know it all
q: what's the best thing on your map? > my house, obviously / a river i love / the blank bits / where we met
d: the blank parts of a map are my favourite > blank parts are where the adventures are / same. that's where i go next
d: a map makes the world smaller and bigger at the same time > a map makes it feel like yours / that's true, weirdly
d: i made a map of where my friends live > a map of friends is the best kind / i'd better be on it lol
d: the edges of my map are where i get nervous > the edges are exciting too / same. it's all unknown past there
o: maybe a full map would be sad. nothing left to find > yeah, i'd miss the blank bits / i'd still look at it
o: i like that my map has holes in it > holes mean hope / holes mean there's more to see
o: the maps in my head are better than the ones on paper > head maps have feelings on them / mine are mostly wrong lol
o: i drew a little house on my map where i want to live one day > i hope you build that house / i'd visit
c: you should never finish exploring | a world with no blank spots is a boring world
y: yeah, keep some mystery | true | agreed
n: i want to see everything | finishing a map is the dream | nah, blank spots bug me
""")

angle(EDGE, "edge_walkers", "the ones who walk forever", """
m: i heard someone walked for a hundred days and never found the end | there's a story about someone who just never stopped walking
q: do you think the ones who walk forever are happy? > yeah, they're free / no, they're lost / both / i hope so
q: would you walk forever if you could? > for a while / never, i like home / maybe, with a friend / i already do, kind of
d: i think some people are made for the road > some are made for roads, some for homes / i think so too
d: if you never stop, you never have to say goodbye > that's sad and kind of nice / you never get to say hello either
d: the ones who walk forever leave little shelters behind for us > those little dirt shelters on the road are probably theirs / i like that idea, a trail of shelters
d: walking forever means always being new somewhere > always new sounds lonely and great / i could never, i like my bed
o: maybe one day i'll find a walker's footprints, a torch or a sign > then leave a sign for them / i hope you do
o: i'd like to think the walkers are still out there, walking > they're out there, somewhere / i bet they are
o: what if the ones who walk forever reached the end and liked it there > then good for them / maybe they're happy at the edge
o: the road is a kind of home too > a home that moves / for some people, yeah
c: settling down is better than wandering | a home beats the open road
y: yeah | true, home is home | agreed
n: nah, the road is freedom | home is boring | wandering is living
""")

angle(EDGE, "edge_many_worlds", "one world or many", """
m: what if there's more than one world | sometimes i wonder if there's another me waking up in another world
m: maybe every world has its own people like us | the players talk like they've seen other worlds
q: do you think there are other worlds like this one? > yeah, loads / no, just this / i think the players come from them / i hope not, this one's enough
q: if there was a door to another world, would you go through? > yes, right now / only to look / no, i'd miss here / with you, sure
d: some nights the stars look like other worlds > that's a lot of worlds to think about / i think this one's the best, either way
d: if there are other worlds there's another me in one > another you, making the same mistakes / that's a lot of worlds to think about
d: maybe each world is someone's dream > a dream world, i like that / that's a lot of worlds to think about
d: if there are others, i hope they're kind to the ones in them > i hope so too / i think this one's the best, either way
o: what if in another world we never met > then this one's lucky / i'd find you anyway
o: i'd like to know the other me is happy > i bet they are / i hope so too
o: what if this world is the one someone else dreams about > then this one's lucky / then they've got good taste
o: maybe all the worlds are connected underneath > under bedrock, maybe / that's a wild thought
c: this is the only world there is | there's just one world, this one
y: yeah, just this one | true | i think so
n: no way, there's more | the players prove there's more | i think there are loads
""")

# ======================================================================================================================
# Below: bedrock, the void, the old cities, the floor and the ceiling, cave sounds, digging down.
BELOW = theme(
    "below", ["caves", "philosophy", "world"], ["caves", "mining", "philosophy", "world"],
    react={"any": "i've thought about that down in the mines | that's a creepy one | huh. good one for down there | "
                  "ngl the deep stuff freaks me out",
           "miner": "i've been to the bottom. it's quiet down there | the deeper you go the older it feels",
           "casual": "lol why are you like this. ok but yeah | bro just don't dig straight down. but yeah",
           "hermit": "the deep doesn't answer. it listens. | below is older than all of us.",
           "newbie": "i never thought about how deep it all goes | i haven't even been past the first cave",
           "speedrunner": "never needed to go that deep | bedrock's just a floor. move on"},
    nudge="you've been down there too long | go on | that's not all, is it | keep going",
    close={"any": "anyway. i'm staying up here today | some things should stay down there | glad we're on the surface | "
                  "let's not dig too deep, though",
           "hermit": "the deep keeps its own.", "miner": "i'm still going down tomorrow though",
           "casual": "ok i'm scared of my own mine now lol"},
    bclose="torches. lots of torches | sleep tight lol | don't dig straight down | stay where the light is",
    yes="right? | see | exactly", no="you'll see one day | we'll see | hm",
    sits={"night": "it's dark and i keep thinking about how deep the world goes | nights like this the caves feel closer | "
                   "can't sleep. thinking about what's under us",
          "close": "{a.close_call} almost got me down there and now i'm thinking | i nearly didn't make it up today | "
                   "came up shaking today",
          "ore": "found {a.ore} down there and saw something weird | was down mining and got thinking | dug really deep today",
          "calm": "random one for you | weird thought, but i'm calm, so"})

angle(BELOW, "below_bedrock", "why bedrock can't be broken", """
m: why is bedrock the one thing we can't break | i hit bedrock today and it just doesn't care
m: everything breaks except bedrock. that's weird, right | i tried to break bedrock and my pickaxe just bounced
q: why do you think we can't break bedrock? > it's holding the world up / something's under it that shouldn't get out / it's just too hard / the world needs a floor
q: would you break bedrock if you could? > in a heartbeat / no, some things should stay / once, just to look / no, i'd fall forever
d: maybe bedrock's not a floor, it's a lid > a lid is a creepy way to say it / ok i don't like that
d: it's the only thing in the world that says no > maybe the world needs one rule we can't bend / and it says it loud
d: it's like the world drew a line and said this far, no further > maybe the world needs one rule we can't bend / a line is fair. we get everything else
d: i like that something can't be broken > things that can't be broken are rare / same. it's kind of comforting
o: what if something on the other side is trying to break through to us > oh don't say that / then i'm glad it can't
o: maybe bedrock keeps us safe, not stuck > safe, not stuck. i like that / i'll take safe
o: i put my ear on bedrock once. nothing. total quiet > total quiet is worse than noise / that's so creepy
o: the stone above is ours. the stone below isn't > fair split, honestly / fine by me
c: some things should never be broken | it's good there's something we can't break
y: yeah, the world needs a floor | true | agreed
n: nah, i want to break it | everything should be breakable | rules are made to be dug through
""")

angle(BELOW, "below_void", "the void under everything", """
m: what's under bedrock | people say there's nothing below the bottom, just dark
m: i heard if you fall out of the world you fall forever | the void is the scariest thing i can think of
q: what do you think is under bedrock? > nothing. the void / more world, upside down / the place we go when we die / i don't want to know
q: if you fell into the void, what would you think about? > everyone i know / why i dug down / falling, mostly / that i should've stayed home
d: nothing at all scares me more than monsters > i'd rather a creeper than nothing / yeah, nothing is the worst thing
d: at least monsters are something you can hit > i'd rather a creeper than nothing / let's never find out
d: imagine falling and never landing > falling forever sounds lonely / let's never find out
d: the void doesn't even have a bottom to hit > let's never find out / that's what makes it the worst
o: what if the void is just a world nobody built yet > that's actually beautiful / that's comforting and horrible at once
o: maybe the void is where the world comes from > that's actually beautiful / thanks, i'll never sleep again
o: maybe everything is built on top of nothing, and that's ok > so we're standing on nothing / that's comforting and horrible at once
o: the void is under all of us, all the time, quietly > thanks, i'll never sleep again / that's comforting and horrible at once
c: the void is the scariest thing in the world | nothing is scarier than the void
y: easily | yeah | agreed
n: the dark is scarier | creepers are scarier | nah, the deep dark is worse
""")

angle(BELOW, "below_old_cities", "the old cities in the deep", """
m: i found ruins way down deep. who built those | there are old cities in the dark, way down
m: people say there are huge broken cities under everything | someone lived down there once, so deep
q: who do you think built the old cities in the deep? > people like us, long ago / something that liked the dark / no idea, and i'm not going back / maybe we did, before
q: why would anyone build down there? > to hide from something / it was the surface once / for the quiet / they were scared of the sky
d: everything down there is broken and blue and quiet > quiet like that isn't peace / blue and broken sounds sad
d: they built roads and walls and then just left > people don't leave homes for no reason / that's the creepy part
d: it's like they ran from something > people don't leave homes for no reason / and whatever it was might still be there
d: there's a big frame down there like a door to nowhere > a door to nowhere is the scariest thing / i'd leave that door alone
o: what if they were like us and something made them leave > that's a sad thought / that's why we write signs
o: i wonder if they built it to keep something in > to keep something in. oh no / that's not better
o: what if we're building the next ruins right now > that's a sad thought / then let's build them nice
o: someone left a city and now nobody knows their names > our towns will be someone's mystery / that's why we write signs
c: some places should stay unexplored | we shouldn't go poking around the old ruins
y: agreed | yeah, leave it be | true
n: nah, there's loot | how else do we learn | i want to know what happened
""")

angle(BELOW, "below_ceiling", "the world's floor and ceiling", """
m: the world has a floor and a ceiling. like a box | you can only build so high and dig so deep
m: there's a limit up and a limit down | if the world has walls, are we in a room?
q: does it bother you that the world has a top and a bottom? > a bit / no, it's cozy / only when i build tall / never noticed
q: what do you think is above the top? > the sky / nothing / whoever made it / the stars, closer
d: i built up as high as i could and couldn't place another block > that's so weird / being stopped at the top must've felt strange
d: at the very top the clouds are below you > above the clouds sounds lonely / i want to see that
d: we live in the middle, between two limits > the middle is where everything is, though / sounds cozy put like that
d: a box is a weird word for a world this big > a big box is still a box, i guess / it's a very big box
o: maybe the limits are there so we don't get lost > i'd get lost, for sure / that's oddly kind
o: a world without limits would be too much > that's oddly kind / it's already too much for me
o: i felt small at the top. not big > the top makes you small, yeah / that's the sky's job
o: the box keeps us together > a box with friends in it / that's oddly kind
c: the world is big enough as it is | we don't need a world any bigger
y: true | more than enough | yeah
n: i want more | nah, bigger is better | i'd build higher if i could
""")

angle(BELOW, "below_cave_sounds", "the noises in caves", """
m: what makes those noises in the caves | you ever hear that sound down there, like breathing
m: i heard a noise in a cave that wasn't a mob | caves make sounds that don't belong to anything
q: what do you think the cave noises are? > the world settling / something we can't see / wind through the tunnels / i don't think about it
q: ever heard something down there you couldn't explain? > yeah and i left / all the time / once, i still think about it / no and i'd like to keep it that way
d: it's like the stone sighs > the stone sighing is a scary nice way to say it / i've heard that one
d: i heard one right as i ran out of torches > that's the worst timing / i'd have left everything and run
d: it made the hair on my arms stand up > hair standing up means leave / same thing happens to me
d: i was alone, so i talked back to it > you talked back?? brave / brave. i'd have run
o: maybe the world talks and we just don't speak it > i'd like to know what it says / maybe it's just saying hi
o: what if it's someone else down there, mining too > another miner, in the dark. i hope / then let's go down together
o: the noises only happen when you're alone. i swear > they do, you're right / then let's go down together
o: the caves are older than everything and remember stuff > i'd like to know what they remember / then let's be nice down there
c: the cave noises are just the world being the world | there's nothing behind the cave sounds
y: yeah, it's nothing | just wind | true
n: there's something | you haven't heard the right one | nah, something makes them
""")

angle(BELOW, "below_digging_down", "wanting to dig straight down", """
m: everyone says never dig straight down, but why do we want to | there's something in me that wants to dig straight down
m: i stood over a hole once and wanted to jump in | the deeper you dig the quieter your head gets
q: why do you think people dig straight down even when they know? > curiosity / they're in a hurry / they want to see what's there / some people like falling
q: what's the deepest you've ever gone? > right to bedrock / the deep dark, once / just to the first lava / not deep, i stay up top
d: digging feels like going inside your own head > inside your own head is a good way to say it / you came back up, at least
d: down there there's no sun, no days, just stone > no sun is peaceful and awful / you came back up, at least
d: time stops underground. you come up and it's night > losing track of time down there is so real / every time, yeah
d: i always lose track of who i am down there > you came back up, at least / that's the deep for you
o: maybe we dig to find out what's at the bottom of us > i'm scared to find out what's at mine / that's deep. literally
o: coming up into sunlight feels like waking up > sunlight after a mine is the best / every time
o: the world rewards going deep but punishes rushing > that's deep. literally / patience goes deep
o: the hole you dig is a bit like a question > and the bottom's the answer / that's one way to see a hole
c: going deep is worth the danger | the deep is where the best stuff is
y: always | yeah, diamonds don't come to you | true
n: nah, the surface has everything | it's not worth dying for | stay up here where it's safe
""")

# ======================================================================================================================
# The sun, the moon and the clouds.
SUNMOON = theme(
    "sunmoon", ["sky", "philosophy"], ["sky", "night", "day", "philosophy"],
    react={"any": "huh. i've never thought about that | you look at the sky a lot, huh | ok that's actually a good one | "
                  "i've wondered that too",
           "casual": "lol you're looking up again. ok but fr | bro looked up for once. ok fair",
           "hermit": "the sky keeps time. we just borrow it. | the sun is patient.",
           "newbie": "i thought that was just normal lol | i never even looked up properly",
           "speedrunner": "the sun is a clock. that's all i see | sunrise means go",
           "helper": "i love that you notice stuff like this",
           "builder": "honestly the sky is good design"},
    nudge="you've been looking up a lot lately | go on | there's more, i can tell | keep going",
    close={"any": "anyway. it's a nice sky either way | i'll keep looking up | the sky's the one thing that's always there | "
                  "weird but nice",
           "hermit": "look up more.", "casual": "ok back to looking at the ground lol",
           "speedrunner": "anyway. daylight's burning"},
    bclose="let's watch it together sometime | nice talk, sky friend | you made me want to look up | i'll look up more now",
    yes="right? | exactly | see", no="we'll see | maybe | hm, ok",
    sits={"stars": "look at the sky tonight | the moon's really bright tonight | clear night, look up",
          "morning": "look at that sunrise | sun's coming up | morning sun always gets me thinking",
          "evening": "the sun's going down | look at the sunset | the sky's going orange",
          "calm": "random sky thought | feeling chill, thinking about the sky"})

angle(SUNMOON, "sky_square_sun", "why the sun is square", """
m: why is the sun square | i looked at the sun today and it's square. like a block
m: everything here is square, even the sun | i wonder if the sun is made of blocks too
q: why do you think the sun is square? > because everything is / so it fits the world / someone built it / it's not, you're squinting
q: what do you think the sun is made of? > glowstone, obviously / fire in a box / something we can't mine / light, just light
d: if everything is blocks, maybe someone placed the sun > a placed sun is a big thought / it fits, that's for sure
d: the sun moves the same way every single day > like clockwork / it fits, that's for sure
d: a round sun would look wrong here, i think > it fits, that's for sure / a round sun sounds so weird
d: the moon's square too. they match > square things are honest / the moon matching is a nice touch
o: if someone placed the sun, they wanted us to have days > they did a good job / that's a nice thought
o: maybe the sun is the first block anyone ever placed > the first block. i love that / they did a good job
o: the sun's the one block we'll never reach > we'd need a lot of ladders / good. some things should stay up there
o: the sun's like a torch someone lit for all of us > a torch for everyone, that's sweet / they did a good job
c: the sun is just a really big block | the sun's a block like any other
y: has to be | yeah | true
n: nah, it's more than that | the sun isn't a block, it's the sun | you can't mine it, so no
""")

angle(SUNMOON, "sky_moon_phases", "the moon changing shape", """
m: the moon changes shape every night | i watched the moon get thinner over a few nights
m: the moon goes full, then half, then gone, then back | the moon never looks the same two nights in a row
q: why do you think the moon changes shape? > it's eating itself lol / something's in front of it / it's tired and resting / it's counting the days for us
q: what's your favourite moon? > full, it's so bright / the thin one / the dark one, more stars / whatever one's up when i'm with friends
d: the dark nights with no moon feel extra dangerous > the no moon nights are the worst / full moons make me feel safer too
d: when it's full the night feels softer > full moons make me feel safer too / softer is a good word for it
d: it's like the moon keeps count for us > the moon as a calendar, i like that / counting by the moon is very old school
d: i've started counting days by the moon > the moon as a calendar, i like that / counting by the moon is very old school
o: people say slimes come out more on a full moon > slimes love a full moon, yeah / bouncy nights
o: maybe the moon changes because it's alive > a living moon would be wild / that's either cute or terrifying
o: what if the moon is watching us, a bit more each night > that's either cute or terrifying / don't say that at night
o: the moon always comes back the same way > like us, coming back / it's reliable, i'll give it that
c: the full moon is the best moon | nothing beats a full moon
y: true | yeah | agreed
n: the new moon has the best stars | nah, the half moon | full moon means more slimes. no thanks
""")

angle(SUNMOON, "sky_always_rises", "the sun always rising", """
m: the sun always comes up, no matter what happened | i had the worst night and then the sun just rose like nothing
m: the sun doesn't care how my day went. it rises anyway | every morning the sun comes up on time
q: do you think the sun ever might not rise? > never, it can't / i've worried about it / only if we stop sleeping lol / some nights it feels that way
q: does the sunrise make you feel better? > every time / not always / yeah, the mobs burn / only if i slept
d: the sun rising after a bad night feels like forgiveness > forgiveness is a nice way to put it / count on the sun, yeah
d: the sunrise is the one thing i can always count on > count on the sun, yeah / same. the sun never lets me down
d: sometimes i wish it'd wait for me to be ready > it waits for nobody / lol same, five more minutes
d: the zombies burning at dawn is the best sound > the morning burn is satisfying / best alarm there is
o: if the sun stopped, we'd all be in the dark with the mobs > let's never find out / don't even joke about that
o: maybe the sun keeps going so we can too > that's a nice way to think about it / i like that
o: i like that the sun doesn't care. it means it's always fair > fair to everyone, yeah / that's a nice way to think about it
o: i've never missed a sunrise i was awake for > that's a good streak / keep that going
c: tomorrow is always a fresh start | every sunrise wipes the slate clean
y: true | yeah, new day | agreed
n: nah, the mess stays | the sun rises but my problems don't go | not really, the creeper hole's still there
""")

angle(SUNMOON, "sky_moon_mobs", "the moon and the monsters", """
m: i think the moon brings the monsters | the mobs come out when the moon is up
m: it feels like the moon is on the mobs' side | the brighter the moon, the braver the mobs get
q: do you think the moon and the mobs are connected? > yeah, they come with it / no, it's just the dark / the moon watches them / i never thought about it
q: if the night had no monsters, would you like it? > i'd love the night / it'd be boring / yeah, i'd go stargazing every night / i'd miss the danger
d: the night would be so pretty without zombies > the night is pretty anyway, if you're inside / i'd sit on my roof all night
d: i think the mobs come from the dark, not the moon > the dark is the real problem / that's what i heard too
d: the moon's the only light at night and it doesn't even scare them > i wish it did / it's not much of a torch
d: maybe the moon is sad about them > a sad moon. huh / i'd be sad about them too
o: maybe the night is the world's way of making us need each other > maybe the night is a gift too / it works, honestly
o: without the night we'd never build houses > that's true, we'd sleep anywhere / maybe the night is a gift too
o: the moon and the sun take turns, like the world needs both > both, yeah / that's a nice way to see it
o: i think i'd miss being scared, a little > a little scared is good / weird but same
c: the night would be better without mobs | a night without monsters would be perfect
y: obviously | yeah | true
n: it'd be boring | the mobs make it exciting | we'd get lazy
""")

angle(SUNMOON, "sky_sunsets", "why sunsets are pretty", """
m: why are sunsets so pretty | the sky goes orange and pink and i just stop
m: i stopped mining just to watch the sun set | the sunset makes the whole world look warm
q: why do you think sunsets are so pretty? > so we stop and look / colours just are / to make up for the night / i don't know but i love them
q: where's the best place to watch a sunset? > a mountain / on the water / my roof / next to someone
d: it's like the day saying goodbye nicely > a nice goodbye, yeah / beauty before danger, kind of
d: for a few seconds everything is orange > orange everything is so good / those few seconds are the best
d: i think the world is showing off a bit > the world showing off is my favourite thing / it's allowed to
d: the night is coming and it still makes it beautiful > beauty before danger, kind of / that's the best part
o: maybe beauty is the world's way of saying it's ok > that's a nice thought / i'll take it
o: i wonder if mobs see the sunset too > the zombies, standing still, watching. lol / that's a nice thought
o: you'd think it'd get old, seeing it every day. it doesn't > it never gets old somehow / never
o: i've never been sorry for stopping to watch one > you never will be / me neither
c: you should stop and watch every sunset | watching the sunset is never a waste of time
y: always | true | agreed
n: i've got stuff to do | it's nice but time's time | not every one
""")

angle(SUNMOON, "sky_clouds", "the clouds", """
m: the clouds always move the same way | if you build high enough you're above the clouds
m: i was on a mountain and walked into a cloud | the clouds are flat on the bottom, like a floor
q: what do you think clouds are made of? > water, i think / fluff / wool lol / sky blocks
q: would you live above the clouds? > yeah, the view / no, too lonely / for a day / only if there's a bed
d: up above the clouds the world goes quiet > quiet up high sounds nice / i want to try that
d: from above, the clouds look like a white floor you could walk on > a cloud floor would be a dream / don't try it though
d: they always drift one way, like they're going somewhere > they're going home, maybe / somewhere far, i guess
d: i built a tower once just to touch a cloud > that's a good reason for a tower / i hope it was worth it lol
o: where do the clouds go when they leave > somewhere far, i guess / nobody knows. that's nice
o: the clouds don't care about walls or houses > free, like we could be / must be nice
o: clouds pass over everyone, like they're checking on us > that's a comforting thought / same sky, everyone
o: everyone in the world sees the same clouds > same sky, everyone / that's a comforting thought
c: everyone should build above the clouds once | you haven't lived until you've been above the clouds
y: so true | yeah | the view's worth it
n: too high for me | nah, i'll stay down | that's a long way to fall
""")

# ======================================================================================================================
# The stars.
STARS = theme(
    "stars", ["sky", "night", "philosophy"], ["sky", "night", "dreams", "philosophy"],
    react={"any": "i love that you think about this stuff | the stars always make me quiet too | "
                  "huh. i've stared at them for ages too | that's a good night thought",
           "casual": "lol stargazer. ok but yeah, they're pretty | bro's a poet now. i'm into it",
           "hermit": "the stars don't hurry. | they've seen everything.",
           "newbie": "woah, i never really looked before | they look so far away",
           "helper": "it's nice to stop and look up",
           "speedrunner": "i never look up. maybe i should | stars don't make you faster. but ok",
           "nomad": "i sleep under them most nights. they're like a roof"},
    nudge="keep going, i like this | go on | you've got a look on your face | there's more",
    close={"any": "anyway. look at them. that's enough | nights like this make it all worth it | i'm glad we're out here | "
                  "the sky's showing off tonight",
           "hermit": "look, and be quiet.", "casual": "ok we're getting sappy lol",
           "newbie": "i'm gonna look up every night now"},
    bclose="same time tomorrow night? | thanks for making me look up | good night to look | let's stay out a bit longer",
    yes="right? | exactly | yeah", no="hm, maybe | we'll see | fair",
    sits={"stars": "look at all the stars tonight | clear sky tonight. look up | so many stars out",
          "friends": "come look at this with me | i'm glad i'm seeing this with you | sit with me a sec",
          "night": "it's late, but look up | can't sleep, so i'm looking at the stars | quiet night. i keep looking up"})

angle(STARS, "stars_what", "what the stars are", """
m: i wonder what the stars actually are | every night the stars come out and i still don't know what they are
m: the stars look like tiny torches way up there | i don't think anyone knows what the stars are
q: what do you think the stars are? > tiny suns / holes in the sky / other worlds / torches someone left on
q: do you think we could ever reach a star? > no, too far / if we built high enough / only in a dream / i hope not, it'd ruin it
d: they're too small to be suns and too bright to be nothing > maybe not knowing is part of why they're pretty / that's what gets me too
d: if they're torches, someone placed a lot of them > someone placed a lot of torches, yeah / that's a lot of torches lol
d: they come out like the world lighting candles > they're like the world's night lights / that's a pretty way to say it
d: they're there in the day too, i bet, just hiding > hiding in the day, i like that / maybe not knowing is part of why they're pretty
o: if every star is a world, someone's looking at our sky right now > hi, whoever's up there / that's a really nice thought
o: maybe the stars are everyone who came before > that's a really nice thought / then they're watching out for us
o: i feel tiny and calm at the same time looking at them > tiny and calm, yeah / same. it's a good feeling
o: the stars make my problems feel small, in a good way > small problems, big sky / that's why i look up
c: some things are better not knowing | the stars are better as a mystery
y: totally | yeah | agreed
n: i want to know | nah, knowing is better | mystery's overrated
""")

angle(STARS, "stars_turning", "the stars turning", """
m: the stars move across the sky through the night | if you watch long enough the whole sky turns
m: the stars move together, like they're all holding hands | the sky turns slow, like a wheel
q: why do you think the stars move? > the world spins / they're walking home / the sky's a big wheel / they follow the moon
q: have you ever watched the stars all night? > once, it was amazing / never, i always sleep / i try, i always fall asleep / almost, then a zombie showed up
d: they never bump into each other, they just turn together > they know where they're going / all that order up there is calming
d: it's like the sky has its own clock > a sky clock, i like that / all that order up there is calming
d: the moon walks the same road as the sun > same road for everyone up there / they know where they're going
d: the stars leave the same way the sun does > they know where they're going / same road for everyone up there
o: i think the sky is the only thing that never gets lost > never lost. lucky sky / that's a good reminder
o: maybe we turn too, and that's why it looks like they move > that's too smart for this time of night / huh. could be
o: watching them turn makes the night feel short > short nights are good nights / that's the best way to spend one
o: the sky's always moving even when we're stuck > it's a good reminder / that's a nice thought
c: watching the sky is the best way to spend a night | stargazing beats sleeping
y: yeah | true | agreed, stay up
n: sleep is better | phantoms say otherwise | nah, sleep beats it
""")

angle(STARS, "stars_counting", "counting the stars", """
m: i tried to count the stars once and gave up | there have to be thousands of stars up there
m: i bet nobody has ever counted all the stars | every time i look there seem to be more
q: how many stars do you think there are? > more than blocks in the world / a few thousand / too many / exactly enough
q: if you could name one star, what would you call it? > after a friend / after my dog / after my first house / i'd let you pick
d: i lost count at a hundred and started again > losing count is the point, maybe / a hundred is a good try
d: the more you look, the more show up > they're shy at first / losing count is the point, maybe
d: some you only see when you look a bit to the side > looking away to see them is so weird / i've noticed that too
d: counting them made me feel small and lucky > small and lucky is a good feeling / losing count is the point, maybe
o: i think i'll name one after you, the bright one > lol ok, i'll take the bright one / aw. i'm honoured
o: maybe there's one for everyone who ever lived > one for everyone, i love that / then there's one for us too
o: maybe counting things is how we feel safe > that's very true / that's why i count my chests
o: i stopped counting and just looked and it was better > just looking is always better / that's very true
c: some things shouldn't be counted | counting ruins nice things
y: yeah | true | agreed
n: counting is fun | i count everything | nah, numbers are nice
""")

angle(STARS, "stars_looking_back", "someone looking back", """
m: i wonder if someone up there is looking back at us | maybe there's someone on a star looking at our world
m: sometimes i wave at the stars. just in case | it'd be funny if someone up there was waving back
q: do you think anyone's up there? > yeah, someone always is / no, it's just us / i hope so / the players, maybe
q: if someone was up there, what would you say to them? > hi, nice sky / come down, we have bread / are you lonely too? / i'd just wave
d: if someone's up there, i hope they're having a good night too > i hope it's a good night everywhere / we'd be someone's sky
d: i like thinking we're not the only ones looking up > not being the only ones is a nice feeling / me too
d: maybe they think our torches are stars > our torches as their stars. oh that's good / then we're someone's sky
d: from up there our town would look tiny and bright > tiny and bright, i like that / like a little star of our own
o: if they're lonely up there, i'd want them to know we're here > we're here! / that's a nice thing to share
o: maybe when we feel watched, that's them > that's sweet and a little creepy / then i'll wave more
o: i think everyone in every world looks up at night > that's a nice thing to share / i hope so
o: maybe the stars are just everyone's windows > everyone's windows. i love that / that's sweet and a little creepy
""")

angle(STARS, "stars_rainy_nights", "stars on rainy nights", """
m: when it rains the stars just disappear | i miss the stars on rainy nights
m: the stars are still there behind the clouds, i hope | a night with no stars feels heavier
q: do you think the stars are still there when it rains? > of course, they're just hidden / i hope so / they take the night off / yeah, waiting
q: what do you do on nights with no stars? > sleep / listen to the rain / sit by the furnace / miss them
d: it's like the sky closes its eyes > the sky's eyes, nice / it needs to sleep too, i guess
d: rainy nights make my house feel smaller and cozier > cozy small houses are the best / that's the best part of rain
d: no stars and no moon, that's the darkest it gets > the darkest nights end too / that's when i stay inside
d: i like knowing the stars come back > they always come back, like us / they always do
o: maybe things we can't see are still there, like people we miss > that's a really gentle way to put it / so true
o: i think i like the stars more because of the rain > missing them makes them better / so true
o: you only notice stars when they're gone > missing them makes them better / so true
o: a starless night is the sky resting > the sky deserves a rest too / that's a really gentle way to put it
c: clear nights are better than rainy ones | a starry night beats the rain every time
y: always | yeah | true
n: rain's cozy though | nah, rain sounds | i like the rain more
""")

angle(STARS, "stars_out_of_reach", "never reaching the stars", """
m: i built a tower just to get closer to the stars | the stars look the same even from the top of a mountain
m: no matter how high you go, the stars stay just as far | you can't get any closer to the stars. i tried
q: does it bother you that we can't reach the stars? > a bit / no, some things are for looking at / yeah, i want to touch one / i like that they're far
q: if you could visit one star, would you come back? > yes, home is here / maybe not / only for friends / i'd bring you
d: i got to the top and they were exactly the same size > that's a lot of tower for nothing. but i get it / they don't let you cheat
d: it's like they're painted on the sky > painted on is a good way to put it / that's how they look, yeah
d: maybe being out of reach is what makes them special > out of reach is special, yeah / the one thing we can't put in a chest
d: everything else here we can grab and keep > the one thing we can't put in a chest / true. except them
o: maybe we need something we can't have > wanting is good sometimes / that's sad and true
o: if we could reach them we'd mine them, honestly > we would. that's sad and true / lol first thing i'd do
o: i like that everyone gets the same stars, rich or poor > same stars for everyone. good / that's the best part
o: the stars are the only treasure nobody can steal > nobody can steal them, true / good. let them try
c: it's good that some things are out of reach | not everything should be reachable
y: agreed | yeah | true
n: i want everything in reach | nah, i'd climb up there | out of reach is annoying
""")

# ======================================================================================================================
# Time: counting days, getting older, things lasting, days flying, waiting, the future.
TIME = theme(
    "time", ["philosophy", "time"], ["philosophy", "memories", "day", "time"],
    react={"any": "time's a weird thing | ngl that makes me feel old | huh. it does go fast | i think about that too",
           "casual": "lol deep. but yeah the days fly | bro i can't even tell what day it is. but yeah",
           "hermit": "days pass. we pass with them. | time is the slowest miner.",
           "newbie": "i've already lost track of how long i've been here | days go so fast here",
           "helper": "time's better spent with people, i think",
           "speedrunner": "time is the only thing i count | every second matters. i feel this"},
    nudge="go on | you've been counting days again | something's on your mind | keep going",
    close={"any": "anyway. let's make today a good one | time flies. let's not waste it | here we are, still | "
                  "another day, another talk",
           "hermit": "the day goes on.", "speedrunner": "and we just spent a few minutes on this. worth it",
           "casual": "anyway time to do literally anything lol"},
    bclose="make it a good day | here's to the next hundred | we've got time | let's use the day well",
    yes="right? | exactly | yeah", no="we'll see | hm | time will tell",
    sits={"evening": "another day's ending already | where did today go | the sun's setting again",
          "bored": "bored. the day feels so long | nothing's happening and the day won't end | slow day today",
          "morning": "new day again | another morning | sun's up. day {a.day} i think"})

angle(TIME, "time_days", "counting the days", """
m: i've been counting the days since i arrived | it's day {a.day} and i don't know where they all went
m: i lost count of the days for a while and it scared me | every sunrise is another notch
q: do you count the days? > every one / i stopped a while back / only the good ones / no, they're all the same
q: how many days do you think you've been here? > too many to count / not many, it feels like / a lifetime / since the first time it rained
d: i scratch a mark on my wall every morning > a wall of marks is a whole life / that's a lot of marks by now
d: the days blur together after a while > blurring is how you know you're settled / yeah, they really do
d: counting the days makes them feel like they mean something > counting is a way of holding on / i get that
d: i stopped counting once and felt lighter > lighter is good too / blurring is how you know you're settled
o: what number do you stop counting at > i don't think you ever stop, secretly / when it stops mattering
o: maybe one day the number will be all that's left of me > that's a sad thought, let's add stories to it / not if i've got anything to say about it
o: days you remember are worth more than days you count > remembered days are the real count / true
o: i want to make more days worth remembering > let's start today / same. let's do something dumb tomorrow
c: counting the days is pointless | you shouldn't count days, just live them
y: yeah | true | agreed
n: counting helps me | i like knowing | the number's part of the story
""")

angle(TIME, "time_older", "getting older", """
m: i wonder if we get older | i don't feel any different but i feel older
m: i've been here long enough to feel old | the newbies make me feel ancient
q: do you think we get older here? > not in our bodies, but yeah / no, we stay the same / inside, definitely / i hope not
q: what's changed in you since you arrived? > i'm braver / i'm quieter / i know more stuff / nothing, i'm the same goof
d: my hands look the same but i know so much more > you're not old, you're experienced lol / that's the good kind of old
d: older doesn't mean slower here, it means smarter > smarter, not slower, i like that / you're not old, you're experienced lol
d: i used to run everywhere. now i walk and look > walking and looking is a good change / you're not old, you're experienced lol
d: i think getting older is just remembering more > remembering more is a good kind of old / then i'm very old
o: what if we never get old and just keep collecting days > forever collecting, like a hoarder of days / that sounds nice actually
o: being old here means knowing which caves to skip > knowing what to skip is wisdom / that's real wisdom
o: one day i'll be the one telling newbies about creepers > you'll be great at that / you kind of already are
o: i hope i get old and boring and safe > old and safe sounds nice actually / me too, honestly
c: being old here is better than being new | the longer you're here, the better it gets
y: true | yeah, you know stuff | agreed
n: nah, being new is magic | new is better | old is boring
""")

angle(TIME, "time_lasting", "whether anything lasts", """
m: does anything we do actually last | tools break, food runs out, houses get blown up
m: everything here wears out eventually | i think about what's left of us after everything breaks
q: what do you think lasts the longest? > stone / friendship / stories / bedrock lol
q: is it sad that things don't last? > a bit / no, it means we keep making stuff / only when it's my pickaxe / yeah, but it's also fine
d: my best pickaxe broke and i felt like i lost a friend > a pickaxe is a friend, honestly / rip. it served you well
d: the only things that never break are the ones you can't hold > the things you can't hold, yeah. that's it / that's deep
d: a stone wall will outlast me, probably > stone outlasting us is kind of comforting / probably, yeah
d: items left on the ground vanish after a while. that's so sad > vanishing items are so sad / poor dropped stuff
o: maybe things breaking is how we know they were used > used and loved, yeah / that's a nice way to see it
o: i'd rather a pickaxe that broke than one that sat in a chest > a chest pickaxe is a sad pickaxe / same
o: if nothing lasts, then right now matters more > right now, yeah / so let's not waste it
o: i think kindness lasts. people remember it > kindness lasts, i agree / i remember every kind thing
l: i think that's why i'm scared of losing people. they don't last either > i'll remember you. that lasts / you're not going anywhere
l: i want something of me to last, you know? > something will. this talk, for one / i'll remember you. that lasts
c: nothing lasts forever | everything breaks in the end
y: true | yeah | sadly
n: bedrock does | stone lasts | friendship does
""")

angle(TIME, "time_flying", "days going by fast", """
m: the days go by so fast here | the sun's up, then it's down, then it's up again
m: a whole day here goes by in no time | i blinked and three days were gone
q: does it feel like the days are getting faster? > yeah, way faster / no, same as ever / only when i'm having fun / only the good ones
q: what would you do with a longer day? > build more / sleep more lol / sit by the water / spend it with friends
d: when i'm busy the day is gone in a second > busy days are short days / same. then it's night again
d: the nights feel longer than the days, somehow > nights feeling long is a fear thing, i think / time goes how you feel it
d: maybe it's fast because we're busy, not because it is > time goes how you feel it / probably, yeah
d: the first days felt so long > the first days were long because everything was new / they really did
o: maybe slowing down is the trick > let's sit still for a bit then / i'm bad at slowing down
o: if i sit still and watch, the day stretches out > let's sit still for a bit then / stretching the day, i like that
o: i want to remember these days before they blur > they won't blur if we talk about them / same
o: fast days mean good days, maybe > fast and good, i'll take it / then we're having good days
c: time is the most precious thing we have | time's worth more than diamonds
y: true | yeah | agreed
n: diamonds though lol | time's free, diamonds aren't | we have endless time here
""")

angle(TIME, "time_waiting", "waiting and patience", """
m: waiting for crops to grow is teaching me patience | i stood by my wheat waiting for it to grow
m: everything good here takes time. trees, crops, everything | i'm bad at waiting
q: are you a patient person? > very / not at all / for some things / getting there
q: what's the hardest thing to wait for? > the sun to come up / crops to grow / a friend to come back / my furnace lol
d: a sapling takes forever if you watch it > watched saplings never grow / so don't watch it lol
d: i think the world wants us to slow down > slowing down isn't so bad / maybe it does
d: the furnace makes you wait, the crops make you wait. it's all waiting > waiting is half of life / do something else while you wait
d: waiting is just time you don't notice > waiting is half of life / do something else while you wait
o: maybe waiting is where all the thinking happens > thinking while waiting, yeah / that's when i think the most
o: i've had my best ideas staring at a furnace > furnace thoughts are underrated / same, honestly
o: the things you wait for feel better when they come > waiting makes it sweeter, yeah / true. first bread after a long wait
o: maybe the world makes us wait so we notice things > waiting makes it sweeter, yeah / that's a good reason
c: patience is the most important skill | the patient ones always win in the end
y: yeah | true | agreed
n: speed wins | nah, act fast | waiting gets you nowhere
""")

angle(TIME, "time_future", "a hundred days from now", """
m: i wonder what we'll be doing a hundred days from now | i try to imagine myself far in the future
m: i hope future me has a nice house | a hundred days from now this'll all look different
q: where do you think you'll be in a hundred days? > right here / somewhere new / in a big house / no idea, and i like that
q: what do you hope future you is doing? > relaxing / still mining / leading a town / hanging out with friends
d: i hope i've still got the same friends > same friends, i hope so too / you will. you're stuck with me
d: future me better not die to the same creeper > future you has it handled / no promises lol
d: maybe future me will laugh at all my worries now > future you will laugh, i bet / probably, yeah
d: i want future me to be proud > proud is a good goal / future you will be
o: it's weird that today is someone's old memory one day > today's a good memory then / that's mind bending
o: i think about what i'd tell future me > tell them to eat / tell them to stay out of lava
o: maybe future me is wondering about present me too > that's mind bending / then say hi
o: the only way to get there is one day at a time > one day at a time, yeah / slow and steady
c: it's better to live for today than plan for tomorrow | planning ahead is overrated
y: yeah, today | true | agreed
n: planning keeps you alive | nah, plan ahead | tomorrow comes either way
""")

# ======================================================================================================================
# Friendship.
FRIENDSHIP = theme(
    "friendship", ["friends", "philosophy"], ["friends", "feelings", "trust", "philosophy"],
    react={"any": "that's a good thing to think about | i like that you think about stuff like this | "
                  "that's sweet, honestly | huh. yeah",
           "casual": "aw are we having a moment lol. ok yeah | lol soft. but fr, yeah",
           "hermit": "few friends. good ones. | a friend is someone who's quiet with you.",
           "newbie": "i'm still figuring out the friend thing | i think i have friends now. i hope so",
           "helper": "friends are the best part of all of it | i love talking about this",
           "speedrunner": "friends slow you down. but i'd slow down for you",
           "pvp_sweat": "friends watch your back. that's it"},
    nudge="go on | you've been thinking about us, huh | you asked for a reason, huh | keep going",
    close={"any": "anyway. glad we're friends | i'm lucky, i think | good talk | thanks for being around",
           "hermit": "you're alright.", "casual": "ok enough mushy stuff lol", "newbie": "you're like my best friend now"},
    bclose="same here | you're stuck with me | glad you're around | any time",
    yes="right? | exactly | yeah", no="hm. we see it different | fair | ok, maybe",
    sits={"friends": "i'm glad we're friends | can i say something kind of sappy | been thinking about us",
          "night": "can't sleep, so you get the deep stuff | late nights make me sappy | it's late and i'm feeling thoughtful",
          "group": "i like our little group | being in a group with you is nice | the group's been good lately"},
    needs=["!strangers", "!rivals"], trust=True)

angle(FRIENDSHIP, "friend_what_makes", "what makes a friend", """
m: i've been thinking about what makes someone a friend | some people i see every day and they're not friends
m: i think a friend is someone who'd come looking for you | it's weird how you just know someone's a friend
q: what makes someone a friend? > they show up / they share food / they don't laugh when you die / you can be quiet together
q: how do you know when someone's become your friend? > you miss them / you save stuff for them / you tell them where your base is / they stop being polite
d: i think it's when you save the good food for someone > saving food for someone is love, basically / that's a real one
d: a friend would come find you if you got lost at night > i'd come find you / that's the one, yeah
d: friends are the ones who remember your dumb stories > remembering the dumb stories, yeah / i remember all of yours
d: maybe it's just showing up, again and again > showing up is most of it / that's all it is, really
o: i think we became friends without either of us noticing > same, it just happened / that's the best way
o: friends are like a house. you build them slowly > slow builds last longer / and you fix them when they break
o: some friends are for adventures, some are for quiet > you're both kinds, i think / true
o: i don't need a lot of friends. just good ones > good ones, yeah / same
l: i guess what i'm saying is you're a good friend > you too. a lot / stop, you'll make me blush lol
l: i don't say it enough, but i'm glad i met you > same, honestly / you too. a lot
c: one good friend beats a whole town of people | you only need one real friend
y: true | yeah | agreed
n: the more friends the better | a town is family too | nah, i like lots of people
""")

angle(FRIENDSHIP, "friend_how_met", "how we met", """
m: i was thinking about how we met | it's funny, we almost didn't meet at all
m: if i'd walked the other way that day we'd never have met | i remember the first thing you ever said to me
q: do you remember the first time we met? > of course / kinda, you were lost / you were digging a hole / you were being chased lol
q: do you think we were meant to meet? > yeah / no, it was luck / both / i like to think so
d: one wrong turn and we'd be strangers > luck or not, i'll take it / glad we didn't
d: i was in a bad mood the day we met and you were nice anyway > you were in a bad mood?? i didn't notice / glad i was
d: funny how one day can change everything > one day, yeah. a good one / luck or not, i'll take it
d: i almost didn't say anything to you > glad you said something / i'm glad you did
o: i wonder how many friends we walked past and never met > probably loads. that's sad / i don't want to know
o: what if every stranger is a friend we haven't met yet > that's a nice way to see strangers / that's very you
o: i think about who i'd be without that day > me too, sometimes / probably lonelier
o: maybe some meetings are just lucky and that's enough > lucky is enough / i'll take lucky
c: friends find each other no matter what | if you're meant to be friends, you'll meet
y: i believe that | yeah | true
n: it's all luck | nah, you have to say hi | you make friends, you don't find them
""")

angle(FRIENDSHIP, "friend_trust", "trusting someone", """
m: i've been thinking about trust | it's a big deal to tell someone where your base is
m: trusting someone in a cave is the biggest trust there is | i trust like three people, and you're one
q: who would you trust with your chests? > you / nobody, sorry / my oldest friend / anyone who asks nicely lol
q: what's the fastest way to lose your trust? > take from my chests / leave me in a cave / lie / break my stuff
d: trust is slow to build and fast to break > slow build, fast break, so true / trust is a gift, yeah
d: i trusted someone once and they took my diamonds > getting robbed by a friend is the worst / that's awful. i'm sorry
d: when someone has your back in a fight you just know > having your back in a fight says everything / yeah. that's when you know
d: trust is giving someone a way to hurt you, and they don't > trust is a gift, yeah / that's a good way to put it
o: trusting people is still worth it, even after getting burned > still worth it, yeah / most of the time
o: it's scary to trust, but lonely not to > lonely or scared, i'd pick scared / yeah. lonely is worse
o: you're one of the few people i'd go caving with > i'd go caving with you too / that means a lot
o: maybe trust is just choosing someone again and again > choosing again and again, yeah / i like that
c: you should never trust anyone with your stuff | trust nobody with your chests
y: yeah, sadly | true | learned that the hard way
n: you have to trust someone | nah, that's a lonely way to live | i trust my friends
""")

angle(FRIENDSHIP, "friend_drifting", "friends drifting apart", """
m: i wonder if friends always drift apart over time | i had a friend i saw every day and now we barely talk
m: people change and sometimes they go different ways | it's sad when someone you know becomes a stranger again
q: do you think we'll always be friends? > yeah, obviously / i hope so / if we keep talking / i'm not worried
q: what do you do when a friend drifts away? > go find them / let them go / leave a sign / say hi anyway
d: nobody did anything wrong, we just stopped > it's nobody's fault, that's the sad part / that happens, sadly
d: it's not a fight, it's just quiet > quiet goodbyes are the hardest / yeah. that's the sad kind
d: sometimes the road just splits > roads split and join again sometimes / yeah. it does
d: i still wave when i see them > waving still matters / that's nice of you
o: maybe some friends are just for one part of life > some friends are seasons, i guess / maybe
o: i don't want that to happen to us > it won't. i'll pester you / same. not us
o: the trick is to keep showing up, even when it's busy > showing up, yeah / i'll keep showing up
o: maybe it's ok to grow apart if it was good while it lasted > good while it lasts is still good / i think so
c: real friends never drift apart | if you drift apart you weren't real friends
y: yeah | true | agreed
n: that's harsh | real friends drift too | life happens
""")

angle(FRIENDSHIP, "friend_making_hard", "making friends being hard", """
m: making friends is harder than mining diamonds | i'm not good at talking to people
m: i never know what to say to new people | some people make friends so easily and i don't get how
q: is it hard for you to make friends? > very / no, i talk to everyone / only at first / i'm still learning
q: what helps you make a friend? > sharing food / doing a job together / just saying hi / being honest
d: i get nervous and talk about dirt for ages > dirt talk is valid / lol i'd listen
d: the first hello is the hardest part > the first hello is the worst, then it's fine / so true
d: people like it when you ask about their builds > asking about builds is a good trick / it works on me lol
d: mining together is easier than talking > doing stuff together is the best way / no talking needed, yeah
o: quiet people make the best friends once you know them > quiet ones are the best, yeah / true
o: maybe the hard ones are worth more > worth it, i think / they usually are
o: i'm glad you talked to me first, honestly > i'm glad i did / me too
o: maybe everyone's nervous and just hides it > everyone's nervous, i promise / i definitely am
c: a few close friends beat lots of okay ones | a few real friends beat a hundred people
y: totally | yeah | agreed
n: lots of friends is fun | nah, the more the merrier | i like a big crowd
""")

angle(FRIENDSHIP, "friend_group_or_one", "a group or one friend", """
m: i keep thinking about groups and friends | being in a group is different from having a friend
m: a group feels safe but one friend feels close | i like groups but i miss one on one talks
q: would you rather have a big group or one close friend? > one close friend / the group / both, obviously / depends on the day
q: what's the best thing about a group? > safety at night / someone always knows something / the noise / never eating alone
d: in a group you can hide. with one friend you can't > hiding in a group is real / true
d: a group feels like a town, a friend feels like a house > a town and a house, i like that / both is the best answer
d: groups are good for fights, friends are good for talks > both is the best answer / that's pretty much it
d: some things you only say to one person > some things are one person things, yeah / like this talk
o: a group is lots of friendships tied together > tied together, yeah / that's a nice way to see it
o: you can be lonely in a group if nobody really knows you > lonely in a group, i've felt that / that's the worst kind of lonely
o: one on one talks like this are my favourite part of having friends > mine too / same, honestly
o: the best groups are just friends who decided to stick together > that's what a good group is / yeah
c: being part of a group is better than going it alone | groups beat going solo
y: yeah | true | safety in numbers
n: solo is freedom | nah, groups are drama | i do better alone
""")

# ======================================================================================================================
# Loneliness.
LONELY = theme(
    "lonely", ["feelings", "loneliness"], ["feelings", "friends", "home", "loneliness"],
    react={"any": "i know that feeling | that sounds hard | yeah. it gets quiet out there | i've felt that too",
           "casual": "ok that hit. i feel that | lol relatable. but fr, that's rough",
           "hermit": "alone isn't lonely. lonely is something else. | i like alone. lonely i don't.",
           "newbie": "being alone at night is the scariest | oh. i feel that too sometimes",
           "helper": "you're not alone right now, at least | i'm glad you told me",
           "nomad": "i'm alone most days. you get used to it, mostly",
           "speedrunner": "alone is faster. but yeah, quiet"},
    nudge="go on | i'm listening | you seem a bit down | tell me more",
    close={"any": "anyway. it's nice to talk | i feel less alone already | thanks for this | it's good to have someone",
           "hermit": "good. enough talk.", "casual": "ok i'm better now lol", "newbie": "maybe i'll follow you around a bit lol"},
    bclose="come find me any time | i'm here | you're not alone | let's hang out more",
    yes="right | yeah | exactly", no="hm, maybe | we're different, i guess | fair",
    sits={"loner": "i'm usually on my own, so this is weird for me | i don't talk to many people | i spend most days alone",
          "night": "nights alone are long | it's so quiet at night out here | can't sleep and it's lonely",
          "nohome": "no home, no one waiting. it gets to you | sleeping somewhere new every night is lonely"},
    needs=["!rivals"], trust=True)

angle(LONELY, "lonely_wild", "being alone in the wild", """
m: i spent days alone in the wild once | out there it's just you and the wind
m: you can go days without seeing anyone out there | the wild is beautiful but it's quiet in a heavy way
q: what's the longest you've gone without seeing anyone? > a few days / a week, i think / never, i stay near town / too long
q: do you like being alone out there? > sometimes / no, it's scary / yeah, it's peaceful / at first, then it gets heavy
d: after a while out there you start noticing every little sound > the little sounds get loud, yeah / every twig snapping. i know
d: i started talking out loud just to hear a voice > talking out loud is normal, i do it too / i've done that. it helps
d: after days alone out there, i almost hugged the first person i saw > lol i get it / that's how you know it was too long
d: being alone out there makes everything feel bigger > bigger and quieter, yeah / and further from home
o: i think the wild teaches you who you are > the wild is a hard teacher / it's a hard teacher, but a good one
o: when it's just you, you hear your own thoughts too loud > that's the worst part / yeah. no one to drown them out
o: i came back from that trip different > different in a good way, i hope / it does that
o: alone is good in small doses > small doses, yeah / same. then i need people
l: i don't want to do it like that again, honestly > then take me next time / you won't have to
c: everyone should spend some time alone in the wild | being alone out there is good for you
y: true | yeah, it teaches you | agreed
n: nah, it's bad for you | people need people | too risky
""")

angle(LONELY, "lonely_talking_to_things", "talking to yourself and animals", """
m: i talk to myself a lot when nobody's around | i caught myself talking to a sheep yesterday
m: i name the cows near my house just to have someone to talk to | sometimes i say goodnight to my furnace
q: do you talk to yourself? > all the time / only when i'm stuck / no, that's weird / yeah, i'm good company
q: do you think animals listen when we talk to them? > yeah, they tilt their heads / no, they just want wheat / the dogs do / i like to think so
d: when i talk to a sheep it just looks at me, but it's nice > at least the sheep doesn't argue / sheep are good listeners
d: talking out loud makes the quiet less heavy > the quiet is lighter with a voice in it / totally normal, i do it too
d: i think i talk to things so i don't forget how > that makes sense, honestly / you haven't forgotten. you're great at it
d: my cow knows all my problems at this point > the cow's a good listener / lol that cow knows too much
o: maybe talking to animals is just being lonely in a nice way > lonely in a nice way, that's a thing / that's a sweet way to put it
o: you're the first real person i've talked to in a while > i'm glad i'm the one / then let's talk more
o: everyone talks to themselves, they just don't admit it > i talk to my chests, so / guilty
o: i'd rather talk to you than the furnace > the furnace is jealous now / i'm honoured. i think
c: talking to yourself is perfectly normal | everyone should talk to themselves sometimes
y: totally | yeah | agreed
n: it's a bit weird | nah, talk to people | only a little
""")

angle(LONELY, "lonely_in_town", "lonely in a crowd", """
m: you can feel lonely even in a busy town | everyone's around but nobody really talks
m: i've been in town all day and still feel alone | sometimes a crowd is lonelier than a cave
q: have you ever felt alone with people around? > yeah, often / once or twice / no, people help / that's the worst kind
q: what makes you feel less alone? > someone asking how i am / sharing food / a long talk / my dog
d: everyone's busy with their own thing > busy towns can be cold / yeah. nobody looks up
d: people say hi but nobody asks how you are > asking how someone is, it matters / i'll ask, then. and i mean it
d: it's like being in the room but not in the talk > not being in the talk is the worst / i know that feeling
d: i walk past ten people and talk to none > ten hellos and no talks, i know that one / that's lonely
o: maybe everyone in town feels like that and nobody says it > me too, if i'm honest / probably, yeah
o: i think i'm waiting for someone to talk to me first > someone has to start / i'll talk to you first, any time
o: it's easier to be alone in a cave than alone in a crowd > caves don't expect anything, true / yeah. no one to pretend for
o: i'm glad i'm talking to you now, at least > me too / same. this is nice
c: towns can be the loneliest places | it's lonelier in a town than in the wild
y: weirdly yeah | true | agreed
n: nah, the wild is lonelier | towns are full of friends | people help
""")

angle(LONELY, "lonely_liking_alone", "liking being alone", """
m: i actually like being alone. is that weird | some days i just want nobody around
m: being alone isn't the same as being lonely | i need quiet time or i go crazy
q: is it bad to want to be alone? > no, everyone needs it / only if it's all the time / not at all / a little, maybe
q: what's the difference between alone and lonely? > alone is a choice / lonely hurts / lonely is missing someone / no idea, they feel the same to me
d: alone is when the quiet is yours > the quiet is yours, i like that / alone by choice is peace
d: lonely is when you want someone and nobody's there > lonely is the other kind, yeah / that's the hard one
d: i do my best thinking alone > alone by choice is peace / same. no noise
d: i like people, i just like them in small bits > small bits of people, very relatable / same, honestly
o: maybe the trick is having people to come back to after being alone > coming back is the best part / that makes total sense
o: i think hermits aren't lonely, they're just full > full, i like that / that makes total sense
o: being alone is easier when you know someone would miss you > someone would. me / that makes total sense
o: i'm good at alone because i have friends > that makes total sense / you've got me, at least
c: being alone is better than being around people | solitude beats company
y: often, yeah | true | agreed
n: nah, people are better | company wins | alone gets old
""")

angle(LONELY, "lonely_nights", "lonely nights", """
m: nights are the loneliest part | when it gets dark i miss everyone
m: in a hole at night with the zombies groaning, that's lonely | the night is when you notice you're alone
q: what do you do on lonely nights? > sleep it off / think about friends / make plans / talk to the dog
q: is the night worse alone? > way worse / not really / only without a bed / yeah, the sounds
d: the groaning outside makes the walls feel thin > thin walls at night, ugh / that sound gets into your head
d: i lie there and think about everyone i know > thinking of people helps a bit / i do that too
d: i sleep so the night goes faster > sleeping it off is smart / the bed is the best answer
d: a torch in the corner makes it less lonely > a torch is good company / i keep one by my bed too
o: maybe the night is lonely so we appreciate the morning > morning always comes / that's a nice way to see it
o: i think about who else is awake and alone right now > probably someone like you / me, sometimes
o: knowing someone else is out there under the same moon helps > same moon, yeah, that helps / i'll think of you next time
o: i'd like to have a bed near friends one day > you can always crash at mine / that'd be nice
c: nights are better spent with others | nobody should spend the night alone
y: yeah | true | agreed
n: i like nights alone | nah, quiet nights are nice | alone is fine
""")

angle(LONELY, "lonely_pets", "a pet for company", """
m: getting a dog was the best thing for my loneliness | a dog in the house makes it feel full
m: i think i need a pet. the house is too quiet | my dog doesn't talk but he gets it
q: do you think a pet can fix loneliness? > a lot of it / not all of it / yeah, dogs especially / they help
q: what's the best pet for company? > a dog / a cat / a parrot, it talks back / a horse, you can go places together
d: coming home to someone happy to see you changes everything > a happy face at the door is everything / that's the best part
d: a dog follows you from room to room > room to room, that's love / they really do
d: a cat by the furnace looks like it's keeping watch > a guard cat, perfect / cats are good at that
d: even a chicken would be something > a chicken is a start lol / chickens are terrible company
o: pets don't care about anything except you being there > that's the whole thing / that's really nice
o: maybe we all want someone glad we came home > glad we came home, yeah / that's the whole thing
o: i take better care of myself with a dog to look after > being needed helps / that's really nice
o: being needed by a pet makes me feel like i matter > you do matter / being needed helps
c: everyone should have a pet | every house needs a pet
y: yes | true | agreed
n: pets are a lot of work | not everyone | i'd rather not
""")

# ======================================================================================================================
# Nature: trees growing back, fire, flowers, the world healing, old trees, growing unseen.
NATURE = theme(
    "nature", ["trees", "plants", "philosophy"], ["trees", "plants", "biomes", "philosophy"],
    react={"any": "that's a nice thought | i've noticed that too | huh. nature's weird | i think about that on walks",
           "casual": "lol tree hugger. but yeah | bro's talking to trees now. fair though",
           "hermit": "the forest forgives. slowly. | trees outlive arguments.",
           "newbie": "i love nature here, it's so pretty | i love trees, they're so tall",
           "helper": "i always plant a sapling back",
           "builder": "trees are my favourite thing to look at",
           "speedrunner": "i punch a tree and go. but ok"},
    nudge="go on | you're in a nature mood | there's more, huh | keep going",
    close={"any": "anyway. i'm planting a sapling later | the world grows back. that's something | nature's got it handled | "
                  "i like that",
           "hermit": "plant one. walk on.", "casual": "ok i'm gonna go hug a tree lol", "newbie": "i'm gonna go look at a tree now"},
    bclose="plant one for me | the forest thanks you | let's go for a walk sometime | nature wins again",
    yes="right? | see | exactly", no="hm. maybe | we'll see | fair",
    sits={"morning": "the forest smells nice this morning | morning dew on everything | birds and leaves, nice morning",
          "rain": "the rain's good for the trees | listen to the rain on the leaves | everything's growing in this rain",
          "calm": "feeling peaceful, so here's a thought | i've been sitting under a tree all morning"})

angle(NATURE, "nature_regrowing", "trees growing back", """
m: i cut down a tree and planted a sapling and now it's back | trees always grow back if you plant them
m: the forest i chopped is growing back. it's kind of beautiful | i like that cutting a tree isn't forever
q: do you always replant when you cut a tree? > always / mostly / no, oops / only the pretty ones
q: do you think trees mind being cut? > they don't seem to / maybe a little / no, they give us a sapling back / i hope not
d: the tree gives you a sapling like it's saying here, try again > a tree saying try again is so sweet / never thought of it like that
d: a cut forest looks so sad until it grows back > sad stumps are the worst / it always comes back, though
d: the world gives you a way to fix what you broke > fixing what you break is the whole deal / and that's kind of nice
d: i planted a whole row once and now it's a forest again > a row turning into a forest is beautiful / that's so good
o: maybe the world wants us to take and give back > take and give back, yeah / that's true, honestly
o: i wish everything grew back like trees > some things don't. that's why trees are special / me too
o: the forest doesn't hold a grudge > no grudges, just leaves / lucky for us
o: trees are kinder than most people > that's true, honestly / they don't argue, that's for sure
c: if you cut a tree you have to plant one | not replanting is wrong
y: always | yeah | agreed
n: nah, there are plenty of trees | it's fine either way | trees grow on their own
""")

angle(NATURE, "nature_fire", "a forest burning", """
m: lightning hit a forest and the whole thing burned | i saw a forest fire. everything just gone
m: fire spreads so fast through the leaves | the forest burned and nobody could stop it
q: have you ever seen a forest burn? > yeah, it was awful / once, from far away / no, and i hope not / i started one, oops
q: do you think the forest comes back after a fire? > slowly / only if someone plants / yeah, nature's tough / not the same
d: after a forest fire the ground's all bare, just dirt and ash > bare ground is so sad / that's such a sad sight
d: in the fire i saw, the sheep ran off so scared > poor sheep / that's the worst part
d: a forest fire is beautiful and horrible at the same time > fire's like that / i get that
d: trees can't run from a fire. that's the sad part > yeah. that's the sad part / poor trees
o: i planted saplings where a forest burned. it felt right > planting after a fire is so kind / that's a good thing to do
o: maybe the world needs to start over sometimes > maybe it does / starting over isn't always bad
o: after a fire the grass comes back first, then the flowers > that's kind of hopeful / it always comes back
o: fire is scary because it doesn't care > things that don't care are scary / yeah. it just takes
c: fire is the most dangerous thing in the world | nothing's scarier than fire
y: easily | yeah | true
n: lava is | creepers | the void
""")

angle(NATURE, "nature_flowers", "why flowers grow", """
m: why do flowers grow everywhere | a field of flowers is the prettiest thing in the world
m: flowers don't do much but i love them | i picked a flower today and felt bad
q: what's your favourite flower? > the blue one / sunflowers / poppies / the tiny white ones
q: do you think flowers are useful or just pretty? > just pretty, and that's enough / dye, they're useful / bees need them / both
d: the bees need them, so they're not just pretty > pretty is a job too / true, no flowers no honey
d: they grow where nobody planted them > they plant themselves, kind of / free decorations
d: a flower field makes me forget about mobs for a second > forgetting mobs for a second is a gift / same, it's so calm
d: i keep one in my house, in a pot > a flower in a pot is a good choice / every house needs one
o: maybe the world makes flowers just to be nice > a nice world, then / i like that idea
o: things that are only pretty matter too > pretty matters, yeah / they do
o: i'd like my life to be a bit like a flower field > a flower field life sounds good / calm and colourful, yeah
o: bees make honey from flowers, so flowers are food too > everything connects / i never thought of it like that
c: flowers are the best part of the world | nothing beats a flower field
y: so true | agreed | yeah
n: diamonds though | nah, trees | flowers are just decoration
""")

angle(NATURE, "nature_healing", "the world healing", """
m: grass grows back over my old paths | the leaves fall away on their own when you cut the trunk
m: the world keeps fixing the scars we leave | a hole i dug filled up with water and lily pads
q: do you think the world heals from us? > slowly / yeah, look at the grass / not from creeper holes / it tries
q: what's the worst scar you've left on the world? > a huge strip mine / a burned forest / so many dirt pillars / a creeper hole by my house
d: the creeper hole by my house has grass in it now > grass in a creeper hole, nice / the world forgives, slowly
d: the path i walked every day is turning green again > green paths mean you moved on / the world forgives, slowly
d: i left a strip mine and now there's moss growing in it > moss in an old mine is beautiful / the world takes it back
d: nature takes back what we leave > the world forgives, slowly / always, eventually
o: maybe one day everything we built will be covered in grass > covered in grass, peacefully / the grass always wins
o: i think the world is patient with us > it is. more than we deserve / i think so too
o: i like that we don't get the last word. the grass does > the grass always wins / that's a good way to put it
o: i hope it covers my mistakes gently > it will. it always does / mine too
c: we should leave the world how we found it | clean up after yourself, always
y: yeah | true | agreed
n: the world's for using | nah, it's fine | it heals itself
""")

angle(NATURE, "nature_old_trees", "giant old trees", """
m: there are trees out there way older than me | i found a giant tree in a dark forest
m: the big spruce trees feel like they've seen everything | some trees are so big you need a pillar to see the top
q: how old do you think the biggest trees are? > older than all of us / as old as the world / a few hundred days / no idea, old
q: would you cut down a really old tree? > never / if i needed the wood / not the biggest one / i'd feel bad
d: standing under one made me feel small and safe > small and safe is a good feeling / big trees do that
d: i didn't cut it. it felt wrong > not cutting it was right / good call
d: those trees were here before any of us woke up > old trees deserve to stay / that's crazy to think about
d: the shade under them is the best > best shade in the world / perfect nap spot
o: maybe old trees are the world's elders > elder trees, yeah / they look like it
o: if trees could talk they'd have the best stories > stories about us, probably / i'd listen to all of them
o: i want to grow something that big and old one day > plant a big one, then wait / i'd come see it
o: i put a little fence around one so nobody cuts it > a fence around a tree, love that / that's so nice of you
c: the oldest trees should never be cut | old trees should be protected
y: yeah | agreed | true
n: wood is wood | nah, they're just big | i need the logs
""")

angle(NATURE, "nature_growing_unseen", "things growing while we're away", """
m: my crops only seem to grow when i'm close | i leave my farm and come back and nothing's changed
m: the trees by my house grow but the far ones never change | it's like things only grow when someone's near
q: do you think plants grow when nobody's watching? > nope, they wait / of course they do / only the trees / they grow, just slower
q: why do you think things grow faster when we're near? > they like company / the world's lazy / we look after them / it just feels that way
d: i stayed by my wheat all day and it grew > stay close and they grow, simple / so it is waiting for you
d: i went exploring for days and my farm was still green > maybe it's waiting for you / same thing happens to mine
d: it's like the world only works where we are > the world being lazy is so funny / that's creepy and cute
d: the far fields feel asleep > asleep fields, that's creepy and cute / maybe they're waiting for someone
o: maybe that's why people build farms next to their houses > that makes sense actually / that's why mine's by the door
o: it's like the world needs us to keep it going > we keep it going, huh / that's a lot of pressure lol
o: if nobody was here, would anything grow at all > maybe not. sad / i hope so
o: i like being needed by my wheat > needed by wheat is still needed / lol your wheat loves you
c: the world needs us to keep it going | nothing would grow without us
y: i've seen it | yeah | true
n: the world was here before us | nah, trees grow anyway | that's silly
""")

# ---- A casual's jokes before it gets serious, for the themes above (none for grief: b answers a joke with "lol").
JOKES.update({
    RESPAWN: V("if i die again i'm writing my bed an angry letter | dying is just a fast way home lol | "
               "i've died so much my bed knows me by name"),
    MEMORY: V("i can't remember what i ate this morning lol | my memory's like a chest with no labels | "
              "i forgot where my own house was once. true story"),
    PURPOSE: V("i've been mining so long i forgot why lol | what's the point of anything, says me, with forty chests of dirt"),
    EDGE: V("what if i walk off the end of the world and just fall lol | "
            "imagine the world ends and it's just a sign that says go back"),
    BELOW: V("what if bedrock is just really stubborn dirt lol | the void sounds like my chests after i die"),
    SUNMOON: V("the sun is just a giant square torch, change my mind | what if the moon is the sun's night shift lol"),
    STARS: V("what if the stars are just someone's torches really far away lol | i tried to mine a star once. long story"),
    TIME: V("it's been like a hundred days and i still don't own a clock lol | time flies when you're running from creepers"),
    FRIENDSHIP: V("we've been friends so long you're basically furniture lol | "
                  "you're like my favourite pickaxe. reliable and a bit beat up"),
    LONELY: V("i talk to my chickens. they're bad listeners lol | my best friend is a furnace, help"),
    NATURE: V("i've punched so many trees they should put up wanted posters lol | trees are just wood with extra steps"),
})

# ======================================================================================================================
# The ocean.
OCEAN = theme(
    "ocean", ["ocean", "philosophy"], ["ocean", "exploring", "fishing", "philosophy"],
    react={"any": "the ocean gets me thinking too | i love the sea but it scares me | huh. i've stared at the water like that | "
                  "the ocean's a whole other world",
           "casual": "lol the ocean is just a big puddle. ok it's not | bro i can barely swim. but yeah",
           "hermit": "the sea keeps what it takes. | water remembers.",
           "newbie": "the ocean scares me a little | i've only seen the ocean once and it was huge",
           "helper": "the beach is a good place to think",
           "nomad": "i've crossed it twice. it changes you",
           "speedrunner": "oceans are slow. boats are slow. but sure"},
    nudge="go on | you've been staring at the water | keep going | there's more, huh",
    close={"any": "anyway. the sea's still there, being the sea | i want to sit on the beach for a day | the ocean always wins | "
                  "let's go look at it sometime",
           "hermit": "listen to the water.", "casual": "ok i'm not going swimming now lol", "newbie": "i'm gonna learn to swim better"},
    bclose="beach day soon? | bring a boat | the sea will wait for us | stay out of the deep bits",
    yes="right? | exactly | yeah", no="hm, maybe | we'll see | fair",
    sits={"calm": "i'm feeling calm. ocean calm | sitting by the water is so peaceful | the waves are nice today",
          "evening": "the sun's setting on the water | the sea goes gold in the evening | look at the water right now",
          "bored": "bored, so i've been watching the waves | nothing to do but look at the sea"})
JOKES[OCEAN] = V("the ocean is just a giant water bucket nobody picked up lol | fish don't pay rent and honestly, respect")

angle(OCEAN, "ocean_deep", "how deep the ocean goes", """
m: i wonder how deep the ocean really goes | the water gets darker the further down you look
m: swimming down, it gets dark so fast | the deep ocean is a whole world we never see
q: how deep do you think the ocean goes? > deeper than caves / to bedrock, probably / not that deep, it just looks it / too deep for me
q: what do you think lives at the bottom of the ocean? > drowned / big fish / old ships / something we haven't met
d: down there it's quiet in a way the land never is > quiet like that is heavy / i've felt that
d: the light just fades and you lose which way is up > losing which way is up is the scariest / that happened to me once
d: you only have so much breath, and the sea knows it > that's creepy / that's why i don't go deep
d: i saw a kelp forest swaying in the dark > kelp forests are so pretty / that sounds beautiful
o: maybe the ocean is the world's biggest secret > biggest secret, yeah / and it keeps it well
o: everything down there is older than our towns > older than us, probably / that's a weird thought
o: if the land is ours, the ocean belongs to something else > it belongs to the fish / and the drowned
o: i want to see the bottom once, just once > bring a lot of air / i'll hold the boat
c: the deep ocean is scarier than any cave | the sea is more dangerous than the deep dark
y: easily | yeah | true
n: caves are worse | nah, the deep dark wins | at least the ocean has light
""")

angle(OCEAN, "ocean_shipwrecks", "who sailed the wrecks", """
m: i found a shipwreck and wondered who sailed it | there are old ships all over the sea floor
m: somebody built that ship, then it sank | a wreck on the beach made me sad for some reason
q: who do you think sailed the old ships? > people like us, long ago / pirates, obviously / nobody, they just appeared / explorers
q: would you sail a ship across the sea? > yes, the whole way / with a crew / no, look what happened to them / one day
d: the maps in wreck chests point to treasure. someone hid it > hidden treasure means someone hoped to come back / and never did
d: every wreck has a captain's room with a little bed > a captain's bed is so sad / someone slept there every night
d: the sailors had a plan and a map and then the sea said no > the sea saying no, oof / the sea does that
d: the wreck wood's all mossy and soft now > mossy old ships are beautiful / it's been there a long time
o: what if they made it home and the ship just sank later > that's a nicer story / i'll believe that one
o: someone wrote those maps for themselves > and then lost them / that's sad
o: i hope the crew got to shore > i hope so too / i bet they did
o: every wreck is a story nobody will finish > we can finish them, kind of / that's a sad way to see it
c: taking treasure from a wreck is fine | the sea's treasure is anyone's
y: yeah, it's lost | true | finders keepers
n: it's still theirs | it feels like robbing a grave | leave the wrecks alone
""")

angle(OCEAN, "ocean_dolphins", "dolphins leading the way", """
m: a dolphin swam with my boat for ages | dolphins lead you to treasure, people say
m: i fed a dolphin and it took me somewhere | dolphins look like they're always happy
q: do you think dolphins are happy? > the happiest / they look it / no idea / happier than us
q: why do you think dolphins help us? > they're nice / they like fish / they're bored / they want friends
d: one jumped out of the water like it was showing off > showing off, fair / they always do that
d: one swam ahead of my boat and waited for me to catch up > waiting for you is so sweet / they're like that
d: one led me to a wreck and just left, job done > like a little guide / best guide ever
d: dolphins are smart, you can see it > smart and kind is the best combo / smarter than some people
o: i wish people helped each other like that, no deal, just fun > no deal, just fun, yeah / same
o: maybe the sea isn't so lonely with them in it > a sea with friends in it / that's a nice thought
o: i think about that dolphin when i'm being grumpy > be the dolphin / lol that's a good plan
o: animals that help for no reason are the best > no reason is the best reason / they really are
c: dolphins are the best animal | nothing beats a dolphin
y: yeah | agreed | they're the best
n: dogs though | nah, cats | axolotls
""")

angle(OCEAN, "ocean_drowning", "the fear of drowning", """
m: drowning is the death i'm most scared of | running out of air is the worst feeling
m: when the bubbles run out you panic | i nearly drowned once and still think about it
q: what's the worst way to die? > drowning / lava / falling / the void
q: are you scared of deep water? > yes / a little / no, i love it / only at night
d: drowning is slow, you know it's coming > slow deaths are scary / that's the worst part
d: you watch the bubbles go one by one > watching the bubbles is the worst / ugh, don't
d: i couldn't find the surface. everything looked the same > that's so scary / i'd have panicked
d: the light was right there and i couldn't reach it > so close and so far / that's awful
o: since then i always carry a door to make an air pocket > a door for air is smart / that's a good trick
o: i think the sea taught me respect > respect, yeah / it teaches everyone
o: i still swim. i just don't go deep > shallow is fine / that's smart
o: being scared of it doesn't mean hating it > love and fear at once, yeah / true
c: everyone should learn to swim properly | you're not ready for the world till you can swim
y: true | yeah | agreed
n: just use a boat | nah, stay on land | swimming's overrated
""")

angle(OCEAN, "ocean_horizon", "looking out at the sea", """
m: i like standing on the beach looking at the horizon | the sea and the sky meet somewhere out there
m: looking at the ocean makes my head go quiet | the waves just keep coming, forever
q: what do you think about when you look at the sea? > nothing, that's the point / what's out there / old friends / home
q: would you live by the sea? > yes, in a heartbeat / no, too windy / maybe a little hut / i already do, kind of
d: the water doesn't care about my problems, and that helps > the sea not caring is comforting, weirdly / i get that
d: the sea's the biggest thing i've ever seen > it really is / makes everything else look tiny
d: everything feels smaller next to it, even worries > small worries, big sea / that's why i go there
d: the sound of the sea is better than any music > the sound is the best part / i fall asleep to it sometimes
o: maybe that's why people go to the sea when they're sad > i'd go to the sea when sad / makes sense to me
o: the waves come in and go out, like breathing > like breathing, yeah / that's peaceful, actually
o: the sea makes me feel like i'm part of something big > you are / that's peaceful, actually
o: i'd like a grave by the sea, honestly > that's peaceful, actually / not for a long time though
c: everyone should live near the sea | the best houses are by the sea
y: yes | true | agreed
n: mountains are better | forests | too much sand
""")

# ======================================================================================================================
# Animals: do they think, turtles, bees, parrots, axolotls, wolves.
ANIMALS = theme(
    "animals", ["animals", "philosophy"], ["animals", "pets", "farming", "philosophy"],
    react={"any": "huh. i've wondered that watching my animals | animals are smarter than people think | "
                  "that's sweet, actually | good point, honestly",
           "casual": "lol the cows are plotting. ok but fr | bro's thinking about cows again. fair",
           "hermit": "animals don't lie. | they know more than they say.",
           "newbie": "i never thought animals had feelings | i love the animals so much",
           "helper": "i always feed the animals near town",
           "hoarder": "i keep my animals close. they're mine"},
    nudge="go on | you really love animals huh | keep going | say more",
    close={"any": "anyway. be nice to animals | i'm gonna go feed my cows | animals are good | they're better than us sometimes",
           "hermit": "be kind to the small ones.", "casual": "ok i'm gonna go apologise to a cow lol",
           "newbie": "i wanna pet every animal now"},
    bclose="go give them some wheat | your animals are lucky | animals rule | say hi to them for me",
    yes="right? | exactly | yeah", no="hm, maybe | we'll see | fair",
    sits={"calm": "feeling peaceful, watching the animals | i've been watching the sheep all morning",
          "morning": "the animals are all out this morning | the cows are mooing like crazy today",
          "friends": "you're an animal person, right? | can i tell you an animal thing"})
JOKES[ANIMALS] = V("what if chickens are the real masterminds lol | cows just stand there judging me all day")

angle(ANIMALS, "animals_think", "whether animals think", """
m: i wonder what animals think about | the cows just stare at me like they know something
m: my dog looks at me like he understands everything | do sheep have thoughts, or just grass
q: do you think animals think like we do? > a little / no, just food / more than we know / dogs do, sheep don't
q: do you think animals have feelings? > of course / some do / dogs, definitely / i hope not, for the cows' sake
d: my dog gets sad when i leave. that's a feeling > that's love / dogs feel everything
d: the cow follows me if i hold wheat. that's a thought > wheat logic is still logic / lol fair
d: wolves remember who fed them > that's a big deal / they never forget
d: a fox stole my bread once and looked so proud > lol foxes are thieves / proud fox. classic
o: maybe animals think simpler, but not less > simpler, not less. i like that / that sounds right
o: if they feel stuff, then how we treat them matters > it matters either way / true
o: animals are honest in a way people aren't > honest animals, yeah / they don't pretend
o: the bees work so hard, i hope they're happy > they look happy to me / i hope so too
c: animals feel things just like us | animals have feelings, end of story
y: totally | yeah | of course
n: not like us | nah, they're animals | some do, some don't
""")

angle(ANIMALS, "animals_turtles", "turtles going home", """
m: turtles always go back to the beach where they hatched | i saw a turtle swim all the way home to lay eggs
m: a turtle knows where its home is better than i do | turtles remember their beach forever
q: how do you think turtles find their way home? > they just know / they remember the sand / the stars / they follow the moon
q: do you have a place you'd always go back to? > my first house / the spot where we met / no, i keep moving / the beach i woke up on
d: turtles swim across the whole sea just to get back > crossing the sea for home, wow / that's dedication
d: turtles never forget, no matter how far they go > never forgetting, that's beautiful / i wish i was like that
d: baby turtles run to the water and vanish, but they come back > they come back, like we do / that's the best part
d: a turtle's the most loyal thing in the world > loyal to a beach, i love that / it really is
o: i wish i knew where my home beach was > you'll find it / maybe it's wherever you're happy
o: maybe we all have a place we'd swim across the sea for > i'd swim back to my house / i'd swim back to my friends
o: home is the place you go back to even when it's far > far but still home / exactly
o: i'd like to be as sure of anything as a turtle is > turtles have it figured out / me too
c: everyone has one place they always come back to | everyone has a home beach, like the turtles
y: true | yeah | i think so
n: not me | some people don't | i'm a wanderer, so no
""")

angle(ANIMALS, "animals_bees", "bees at work", """
m: the bees work all day and never complain | i watched the bees for an hour today
m: bees go flower to flower all day long | the bees make honey and we just take it
q: do you think bees like working? > they love it / they don't know anything else / i hope so / they look happy
q: is it fair to take the bees' honey? > if you leave some / yeah, if you're gentle / not really / they make more
d: they go back to their hive every night, like us > home every night, like us / i never noticed that
d: they only sting if you mess with them > they just want to be left alone / fair rule, honestly
d: they work together without anyone telling them > nobody bosses them, nice / that's impressive
d: if you hurt one the whole hive comes for you > the hive sticks together, respect / learned that the hard way
o: maybe the bees are what a good town looks like > a bee town, yeah / looking out for each other, yeah
o: i'd like to work like that, calm and busy > calm and busy is the dream / same
o: if one gets hurt they all come. i wish people did that > some people do / that'd be nice
o: a bee dies if it stings, and still does it for its hive > that's brave and sad / that's loyalty
c: we should learn from the bees | bees are smarter than people
y: true | yeah | probably
n: they're just bugs | nah, people are smarter | we have pickaxes
""")

angle(ANIMALS, "animals_parrots", "parrots dancing", """
m: parrots dance when music plays | i saw a parrot dancing by a jukebox and it made my day
m: parrots copy the sounds of mobs. that's creepy and smart | a parrot sat on my shoulder all day
q: why do you think parrots dance? > they love music / they're happy / they can't help it / to show off
q: what makes you want to dance? > good news / a sunrise / music / nothing, i don't dance
d: the parrot i saw didn't care who was watching > dancing like nobody's watching, yeah / i want that energy
d: that parrot bobbed its head like nobody was around > i want that energy / lol i can picture it
d: a dancing parrot was the happiest thing i saw all week > i believe it / parrots are pure joy
d: the parrot danced till the song stopped, then just sat there > the song stops and it just waits, aw / waiting for the next one
o: maybe joy doesn't need a reason > joy for no reason is the best kind / i think so
o: i wish i danced more > dance then, nobody's looking / same, honestly
o: if a parrot can be that happy, i can find something > find your jukebox / you will
o: maybe happiness is letting yourself enjoy stuff > letting yourself, yeah / that's hard but true
c: everyone should dance more | dancing is the best thing in the world
y: yes | true | agreed
n: i'll pass | dancing is for parrots | nah
""")

angle(ANIMALS, "animals_axolotls", "axolotls playing dead", """
m: axolotls play dead when they're hurt, then get better | i saw an axolotl pretend to die and come back
m: axolotls are tiny but they fight fish | axolotls help you when you fight underwater
q: do you think playing dead is smart or sad? > smart / sad / both / it's what we do too, kind of
q: what's the bravest small animal? > axolotls / foxes / bees / chickens, they carry zombies around
d: one i saw rested and then got right back up > getting back up is the main thing / smart little guy
d: playing dead is like giving yourself a little break before trying again > a little break, smart / i should do that more
d: axolotls are tiny and they still go after drowned > tiny and brave, my kind / respect
d: axolotls fight next to you if you're nice to them > loyal little things / that's adorable
o: maybe it's ok to play dead for a while when life's hard > take a break, then get up / i think it is
o: axolotls come back, like we do > like us, yeah / huh. never thought of that
o: i've had days where i played dead too > i've had those days / me too, honestly
o: resting isn't giving up > resting is part of it / it really isn't
c: sometimes hiding is the smartest thing to do | running away isn't cowardly
y: true | yeah | smart
n: stand and fight | nah, face it | hiding never helps
""")

angle(ANIMALS, "animals_wolves", "a wolf's loyalty", """
m: a tamed wolf will follow you anywhere | my dog jumped into lava after me once
m: dogs fight anything that hurts you | a wolf only needs a few bones to love you forever
q: why do you think wolves are so loyal? > they just are / the bones / they know who's good / they choose you
q: would you do for a friend what a dog does for you? > yes / i'd try / probably not, honestly / i'd like to think so
d: my dog doesn't care what i have, he just follows > that's real loyalty / he loves you, not your stuff
d: my dog sits when i tell him, even when he wants to run > such a good dog / that's trust
d: my dog attacked a skeleton twice his size for me > he'd fight anything for you / brave boy
d: a few bones and my dog gave me everything > loyal for bones, that's a good deal for us / best trade ever
o: i don't think i deserve how much he loves me > be the person your dog thinks you are / you do
o: dogs make you want to be better > be the person your dog thinks you are / they really do
o: loyalty like that is rare in people > rare, yeah / it is. hold on to the ones who have it
o: i'd like to be someone's dog, in a good way lol > lol i get it / loyal and loved, yeah
c: dogs are more loyal than people | a dog's the most loyal friend you'll have
y: yeah | true | always
n: some people are loyal too | my friends are | nah, people can be too
""")

# ======================================================================================================================
# Building as leaving a mark.
LEGACY = theme(
    "legacy", ["building", "philosophy", "legacy"], ["building", "home", "memories", "legacy"],
    react={"any": "i think about that when i build | huh, i never thought of it like that | "
                  "builds mean more than people think | i feel that",
           "builder": "every build is a bit of you. i believe that | builds outlast us. that's the point",
           "casual": "lol my builds are a dirt hut. ok but yeah | bro's thinking about legacy. respect",
           "hermit": "build it well. that's all. | stone remembers longer than people.",
           "newbie": "i never thought about my builds outlasting me | i've only built one house so far",
           "speedrunner": "i don't build. i leave pillars. maybe that's my mark",
           "hoarder": "my mark is my chests. full ones"},
    nudge="go on | you've been building a lot lately | keep going | something's on your mind",
    close={"any": "anyway. i'm gonna add a window to something | build well, i guess | let's leave something nice | "
                  "it's all we can do",
           "hermit": "build. leave it.", "casual": "ok now i'm stressed about my dirt hut lol", "newbie": "i'm gonna build something nice"},
    bclose="build something i can visit | i'll remember your builds | your stuff's good, for what it's worth | leave a sign",
    yes="right? | see | exactly", no="hm | we'll see | fair",
    sits={"built": "just finished my {a.built} | finally finished building | done with a build. feels good",
          "house": "standing in my house thinking | been looking around my house today",
          "evening": "watching the light on my builds at sunset | end of a building day"})
JOKES[LEGACY] = V("my legacy is going to be a hundred dirt pillars lol | future people will think i worshipped cobblestone")

angle(LEGACY, "legacy_mark", "leaving a mark", """
m: i think building is how we leave a mark | every block i place says i was here
m: i want to build something that lasts | when i'm gone at least my house will be there
q: what do you want to leave behind? > a house someone loves / a town / a tree / nothing, honestly
q: do you think we build to be remembered? > a bit / no, for us / yeah, that's why / both
d: i carve my name on signs in every build > signs with names are like saying hi to the future / i've seen some of those
d: a house is a letter to whoever comes next > a house as a letter, love that / that's a nice way to see it
d: the world's full of other people's marks, ruins and towers > it really is / and we're adding ours
d: my first house is still standing and that makes me happy > your first house still standing is great / mine got blown up lol
o: maybe it doesn't matter if anyone remembers, just that we made it > making it is enough / i think so too
o: someone will walk in my house and wonder about me > let them wonder nice things / they'll like it
o: what if the mark we leave is in people, not blocks > in people, yeah / then you've left one on me
o: i want my mark to be kind, not big > kind beats big / it already is
c: what you build is the best thing you leave behind | builds say more than words
y: true | yeah | agreed
n: friends are what you leave | nah, people remember people | builds fall down
""")

angle(LEGACY, "legacy_after_gone", "builds after we're gone", """
m: will anyone see my builds after i'm gone | i wonder who'll live in my house one day
m: someone will find my tower years from now and wonder | my builds will outlast me and that's weird
q: what do you think happens to our builds when we're gone? > someone moves in / they get overgrown / creepers / they stay forever
q: would you want strangers living in your house later? > yes, if they're nice / no, it's mine / i don't mind / i'd leave them a note
d: i'm going to leave a chest with a note in it > a chest with a note is a great idea / that's so nice
d: i hope they don't change the roof > they'd better not touch the roof lol / they will. they always do
d: maybe someone will fix the bits i never finished > that'd be nice / then it's a team build
d: i'd want them to keep the garden > keep the garden, yeah / the garden's the best part
o: it's like passing something on to someone you'll never meet > a gift for a stranger / that's generous
o: my house will remember me even if nobody else does > the house remembers, yeah / i'll remember you too
o: maybe the future people will be like us, wondering > they'll wonder about you / probably
o: i'm building for someone i'll never know > a gift for a stranger / that's generous
l: honestly i want someone to know i was here > i know you were here / i'll tell them
l: i hope someone thinks i was a good builder > you are a good builder / i'll tell them
c: builds should be left for the next people | you shouldn't tear down your builds when you leave
y: yeah, leave them | true | agreed
n: take your stuff with you | nah, clean up | nobody wants old builds
""")

angle(LEGACY, "legacy_ruins_homes", "ruins that were homes", """
m: every ruin was someone's house once | i found an old broken house and wondered who lived there
m: the old ruins in the forest had a bed in them | someone built that ruin and now it's moss and cobwebs
q: who do you think lived in the old ruins? > people like us / villagers / players / nobody knows
q: do you think our houses will be ruins one day? > yeah, everything is / not if we look after them / i hope not / maybe, and that's fine
d: one old ruin had a furnace and a little garden > a garden means they planned to stay / that's sad
d: there was a sign in one but the words were gone > that's sad / whatever it said, it mattered to them
d: one was built right where i would have built > they picked a good spot, like you / great minds
d: the roof fell in but the walls are still standing > walls standing, still trying / good walls
o: one day someone will find our town and think the same > they'll wonder about us / sad, but we lived
o: maybe ruins are just houses that are resting > resting houses, that's lovely / i like that
o: i fixed a broken wall on a ruin. it felt like helping them > that's kind of you / i'd do the same
o: someone's whole life is a few mossy blocks now > sad, but they lived / that's how it goes
c: we should fix up old ruins | ruins should be rebuilt, not left
y: yeah | true | agreed
n: leave them as they are | ruins are history | they're prettier broken
""")

angle(LEGACY, "legacy_signs", "writing on signs", """
m: i write little notes on signs everywhere | a sign is a way of talking to someone you'll never meet
m: i found a sign in a cave that said turn back | someone left a sign that just said hello
q: what's the best sign you've ever found? > one that said hello / a warning about a ravine / someone's name and a date / a joke
q: what would you write on a sign for a stranger? > you're not alone / turn back lol / home is that way / be careful
d: a hello sign made me smile for a whole day > hello signs are the best / that sign did its job
d: a turn back sign probably saved my life > that sign did its job / good thing you listened
d: a sign is the only way to talk to the future > talking to the future, yeah / i never thought of it like that
d: i left a sign on a mountain that said nice view, right > lol perfect / i'd smile at that
o: what if the person who wrote it is long gone > then their words are still here, at least / that's a nice thought
o: maybe one day someone will read mine and feel less alone > they will / that's a nice thought
o: i like the idea of someone smiling at my sign years from now > they will / that's a nice thought
o: words on a sign last longer than words said out loud > signs last, yeah / that's why i write them
c: everyone should leave nice signs for strangers | the world needs more nice signs
y: yes | true | agreed
n: signs are clutter | nah, they spam the place | only useful ones
""")

angle(LEGACY, "legacy_never_done", "a build never being done", """
m: my build is never done. i always see something to change | i've rebuilt my roof four times
m: i finish a build and immediately want to start over | my house is a work in progress forever
q: is a build ever truly finished? > never / when you stop caring / when you move out / when someone else loves it
q: do you keep changing your house? > all the time / once in a while / no, it's done / only the roof
d: every time i look at my house i see a mistake > mistakes are just the next project / same with mine
d: it's like the house grows with me > a house that grows with you, i like that / that's the best kind
d: the house i have now looks nothing like the first one > it's a whole history now / mine too
d: i think i like the building more than the house > the building is the fun part / same, honestly
o: maybe a house is never done because a life is never done > that's deep / yeah. that's it
o: i'll probably still be fixing it on my last day > sounds about right / same lol
o: if i ever say it's done, i think i'll be sad > then don't say it / i get that
o: the unfinished parts are where i still have ideas > then keep it unfinished / ideas in the gaps
c: a perfect build doesn't exist | no build is ever perfect
y: true | agreed | yeah
n: mine is | nah, some are | i've seen perfect ones
""")

angle(LEGACY, "legacy_creeper_hole", "a build blown up", """
m: a creeper blew up part of my house and i felt it in my chest | all that work, gone in a second
m: my house took days to build and one hiss to wreck | i stood in the hole the creeper left for ages
q: does it matter that builds can be blown up? > it hurts / no, you rebuild / it makes them more precious / it makes me want stone
q: would you rebuild the same, or different? > the same / different, better / smaller / out of stone this time
d: the creeper hole in my house looked like a bite out of my life > oof / that's the worst feeling
d: i rebuilt my wall, but the new part looks different > different isn't worse / it's got a story now
d: i almost didn't rebuild. i was so tired > being too tired to rebuild is real / i'm glad you did
d: the creeper didn't even want anything. it just blew up > creepers don't care, that's the worst / that's creepers for you
o: maybe the rebuilt part is the strongest part now > the scar is the strong part / i like that
o: losing that wall made me realise how much i love my house > that's the silver lining / you do love it
o: everything we build is a bit fragile, and that's why it matters > fragile things matter more / true
o: i put a flower where the hole was > a flower in the scar, beautiful / that's so nice
c: you should always build with stone so creepers can't wreck it | wood builds are asking for trouble
y: true | stone gang | yeah
n: wood is cozy | stone breaks too | just light it up
""")

# ======================================================================================================================
# Right and wrong: eating animals.
FOOD = theme(
    "food", ["food", "animals", "philosophy", "morals"], ["food", "animals", "farming", "morals"],
    react={"any": "that's a hard one | i think about that when i'm hungry | huh. i don't love thinking about that | "
                  "fair, honestly",
           "casual": "lol i just ate a pork chop, don't do this to me. but yeah | bro i'm hungry, not now. ok fine",
           "hermit": "we eat. we thank. | the hungry don't argue.",
           "newbie": "now i feel weird about eating cows | i feel bad for the pigs now",
           "helper": "i always try to be gentle with the animals",
           "hoarder": "i've got a chest of steak. no regrets",
           "pvp_sweat": "steak is fuel. fuel wins fights"},
    nudge="go on | you're feeling guilty, huh | keep going | something's bugging you",
    close={"any": "anyway. i'm still hungry, honestly | i'll thank the next cow | we do what we have to | "
                  "let's eat bread today, maybe",
           "hermit": "eat. waste nothing.", "casual": "ok now i want a steak lol", "newbie": "i'm gonna eat carrots today"},
    bclose="waste nothing, that's the rule | carrots for dinner? | you're a kind one | let's farm more wheat",
    yes="right? | exactly | see", no="hm, ok | we see it different | fair",
    sits={"hungry": "i'm so hungry right now | i need to eat something soon | starving, and thinking about where food comes from",
          "spare": "i've got food to share, and it got me thinking | i cooked a lot today",
          "calm": "random food thought | calm day, weird thought"})
JOKES[FOOD] = V("the cows know what i did lol | pork chops are just pigs that trusted me. too dark?")

angle(FOOD, "food_eating_animals", "eating animals", """
m: i feel bad eating the cows sometimes | the pigs look at me and i feel guilty
m: we eat animals every day and never think about it | i wonder if it's wrong to eat animals
q: do you think it's wrong to eat animals? > no, it's how it works / a little / only if you waste it / i try not to think about it
q: how do you feel when you hunt? > fine / a bit bad / hungry, mostly / grateful
d: i only take what i need and leave the rest > that's the right way / same rule here
d: hunger makes the choice for you most days > hunger decides, yeah / true
d: i say thanks after, in my head > saying thanks is nice / that's sweet
d: wolves eat sheep, so maybe it's just the world > the world works like that, i think / fair point
o: maybe what matters is not wasting any of it > wasting is the real wrong / agreed
o: i'd feel worse if i let them suffer > quick and kind, yeah / me too
o: i breed two for every one i eat > two for one, good rule / that's fair
o: caring about it is better than not caring > caring counts / yeah, it does
c: eating animals is just part of life | there's nothing wrong with eating animals
y: yeah | true | it's how it works
n: i'm not so sure | it's a bit wrong | we could eat bread
""")

angle(FOOD, "food_farm_or_hunt", "farming or hunting", """
m: is raising animals to eat better or worse than hunting | the farm cows never see the wild
m: i hunt wild animals instead of farming them. feels fairer | my farm animals trust me and then i eat them
q: is it better to farm animals or hunt wild ones? > farm, it's steady / hunt, it's fair / both are fine / crops only, honestly
q: do your farm animals trust you? > totally / they follow the wheat / i think so / i hope not, for their sake
d: the wild ones have a chance at least > a chance matters / true
d: the farm ones have food and safety, at least > safety counts for something / that's the deal
d: my cows follow me when i hold wheat and it gets me > the wheat follow is heartbreaking / ugh, i know
d: a farm is a deal: safety for food > safety counts for something / a deal, yeah
o: maybe the trust is what makes it feel bad > trust is a heavy thing / yeah, that's it
o: i treat my farm animals well, at least while they're there > treating them well matters / that's what counts
o: wild ones run, farm ones don't. that's the difference > that's a big difference / true
o: i think about it, and i still eat > thinking and still eating is honest / same
c: hunting wild animals is fairer than farming | wild hunting beats farm animals
y: yeah | true | fairer, i agree
n: farming is kinder | nah, the farm is steady | wild ones suffer more
""")

angle(FOOD, "food_crops_only", "living on crops", """
m: i've been eating only bread and carrots lately | i want to try a day without meat
m: crops don't look at you when you eat them | a carrot farm feels kinder than a cow pen
q: could you live on just crops? > easily / for a while / no, i need steak / i'd get so hungry
q: what's the best food that isn't meat? > bread / baked potatoes / golden carrots / pumpkin pie
d: bread is fine, but steak fills you up so much better > steak's hard to beat / true
d: crops don't run away, that's nice > crops are polite / lol true
d: i feel lighter, weirdly > lighter, nice / huh. maybe i'll try it
d: my farm feeds me and nothing has to die > that's a good feeling / that's nice
o: maybe it's not all or nothing, just less > less is a good start / i could do less
o: i like knowing my food grew because i planted it > planted food tastes better / same
o: if everyone ate a bit more bread, the cows would be happier > happier cows, i like it / the cows agree
o: being full and not guilty is a nice combo > best combo / can't argue with that
c: you can live just fine without meat | meat isn't needed here
y: yeah | true | agreed
n: steak though | you'd starve | meat's the best food
""")

angle(FOOD, "food_fishing", "fishing", """
m: fishing is the most peaceful way to get food | i fished all morning and caught mostly boots
m: sitting with a rod is like thinking out loud | the fish don't see it coming
q: is fishing different from hunting? > yeah, it's calmer / no, same thing / fish don't count lol / it feels different
q: why do you think people love fishing? > the waiting / the quiet / the surprise / the treasure
d: i caught an enchanted book once and almost cried > the enchanted book is the dream / lucky
d: waiting for the bobber is like a little meditation > a little meditation, yeah / that's why i fish
d: half the time i don't even care if i catch anything > not caring is the point / same
d: the fish probably have a different opinion > the fish disagree, probably / lol yeah
o: maybe fishing is just an excuse to sit by the water > a good excuse / that's all it is for me
o: the surprise is the best part. could be anything > anything, even a boot / mostly boots lol
o: you can't rush fishing. that's why i like it > no rushing, yeah / same
o: i fish more for the quiet than the food > quiet first, fish second / me too
c: fishing is the best way to relax | nothing beats an afternoon of fishing
y: yes | true | agreed
n: boring | nah, building | too slow
""")

angle(FOOD, "food_waste", "wasting food", """
m: i threw out rotten flesh and felt bad | i dropped some bread in lava and it bugged me all day
m: wasting food feels wrong, even here | i left a stack of potatoes in a chest till i forgot them
q: is wasting food worse than wasting anything else? > yeah, something died for it / no, stuff is stuff / it's worse when others are hungry / i never think about it
q: what do you do with food you don't need? > give it away / feed the animals / keep it, just in case / eat it anyway lol
d: someone's always hungry somewhere > someone's always hungry, yeah / that's true
d: food takes work to grow, so tossing it feels rude > rude to the wheat, lol / i get that
d: i think about the first night i was starving > remembering the hungry nights helps / i remember mine too
d: food is the one thing that keeps us going > food is life / true
o: maybe sharing is the answer to waste > share it, yeah / that's the answer
o: the villagers love bread, i give them extra > villagers deserve bread / that's nice of you
o: i've started leaving food in a chest by the road for whoever > a road chest, that's kind / i love that
o: waste is just sharing too late > oof, that's true / sharing too late, yeah
c: wasting food is the worst thing you can do | throwing out food is wrong
y: true | yeah | agreed
n: it's just food | worse things exist | nah, rotten flesh doesn't count
""")

# ======================================================================================================================
# Right and wrong: taking from ruins and others.
RUINS = theme(
    "ruins", ["structures", "philosophy", "morals"], ["structures", "exploring", "philosophy", "morals"],
    react={"any": "that's a fair thing to wonder | i loot everything and now i feel weird | huh. i never thought about that | "
                  "hm, good point",
           "casual": "lol i robbed a temple yesterday. ok but yeah | bro i'm a loot goblin. fair point though",
           "hermit": "take what you need. leave the rest. | the dead don't trade.",
           "newbie": "uh oh. i've looted so much stuff | it's hard to say whose stuff it is",
           "bandit": "if nobody's using it, it's mine | finders keepers, always",
           "helper": "i always leave something back, just in case",
           "hoarder": "loot is loot. into the chest it goes"},
    nudge="go on | you sound guilty about something | keep going | there's more",
    close={"any": "anyway. i'll leave something back next time | take what you need, i guess | "
                  "the dead don't need it, probably | it's complicated",
           "hermit": "take little.", "casual": "ok i'm still taking the diamonds lol", "newbie": "i'm gonna say sorry to the next ruin"},
    bclose="leave a torch, at least | take what you need | your call | be respectful out there",
    yes="right? | see | exactly", no="we see it different | fair | hm, ok",
    sits={"night": "can't stop thinking about the ruin i looted | it's late and i'm thinking about the old ruins",
          "calm": "random thought about loot | calm day, so here's a weird one"})
JOKES[RUINS] = V("i've looted so many chests they flinch when i walk by lol | if a temple doesn't want robbing it shouldn't have chests")

angle(RUINS, "ruins_looting", "taking from ruins", """
m: is it wrong to take from old ruins | i took everything from a ruin and felt weird after
m: someone left that loot there. maybe for a reason | i always wonder if the owners are coming back
q: is it wrong to take loot from a ruin? > no, it's abandoned / a bit / only if they're coming back / it's the whole point
q: do you leave anything behind when you loot? > nope / a torch / sometimes / i put stuff back if i don't need it
d: the chests in one were so neat, like someone packed them carefully > neat chests make it worse / that's the weird part
d: looting one felt like going through someone's drawers > i know that feeling / abandoned is abandoned, though
d: nobody's been there for ages, so who's it for > abandoned is abandoned / good point
d: i left a torch so they'd know someone visited > a torch is a nice touch / that's sweet
o: maybe taking is fine if you use it well > use it well, yeah / that's fair
o: if the owner came back, i'd give it all back > that's fair / that's the right way
o: what if they left it for someone who needed it > for someone like us, maybe / i like that idea
o: i'd want someone to use my stuff if i was gone > i'd want that too / use it well, yeah
c: loot in ruins belongs to whoever finds it | ruins are free for anyone
y: yeah | true | finders keepers
n: it's someone's | it's still theirs | i feel bad taking it
""")

angle(RUINS, "ruins_temples", "the desert temples", """
m: the desert temples have chests under the floor, like tombs | who built the temples, and why the traps
m: i found a temple with a trap right next to the loot | the temples feel like they're guarding something
q: why do you think the temples have traps? > to keep thieves out / to test you / someone was paranoid / they were hiding treasure
q: would you take from a temple if you knew it was someone's tomb? > no / yes, they can't use it / i'd say sorry first / maybe, not the bones
d: the trap means someone really didn't want it taken > the trap says it all / that's pretty clear
d: the room under the floor is so quiet > quiet rooms like that are spooky / too quiet
d: there's an old feeling down there, like i'm not supposed to be there > i get that feeling too / not supposed to be there, yeah
d: they put a lot of work into hiding it > hiding it took effort / they really did
o: maybe the trap was for someone they hated, not us > maybe / hope so
o: i took the loot and left the bones alone > leaving the bones, good / that seems fair
o: if someone guarded my stuff that hard, i'd want it respected > respect, yeah / me too
o: maybe it's a test, and only careful people win > careful people win, true / maybe
c: traps mean you shouldn't take it | if it's trapped, leave it
y: yeah | fair | true
n: traps mean it's worth it | nah, beat the trap, take the loot | that's the fun
""")

angle(RUINS, "ruins_village_chests", "taking from villagers", """
m: is it wrong to take from village chests | the villagers don't stop you when you open their chests
m: i took bread from a village chest and felt bad | villagers just watch you take their stuff
q: is taking from a village chest stealing? > yes / not if they don't mind / kinda / they don't care, so no
q: would you take from a villager if you were starving? > yes / i'd ask first / i'd trade / no, never
d: they don't fight back, which makes it worse > not fighting back makes it worse, yeah / that's the test, i think
d: i put the bread back later > putting it back is good / that's the right thing
d: they work all day for that stuff > they work hard, yeah / true
d: taking from people who can't stop you feels wrong > that's the test, i think / it is wrong
o: maybe what's right is what you'd do if no one was watching > right when nobody's watching, yeah / that's a good test
o: i trade with them now instead > trading is better / good
o: the villagers gossip, you know. they'd remember > they do remember / then be nice
o: kindness to people who can't stop you matters most > that matters most / agreed
c: taking from villagers is stealing, plain and simple | village chests are off limits
y: true | yeah | agreed
n: they don't mind | it's fine | they don't need it
""")

angle(RUINS, "ruins_dropped_items", "someone's dropped items", """
m: i found someone's stuff on the ground where they died | a pile of items in the grass, someone's whole life
m: if you find someone's dropped stuff, do you keep it | i found a sword on the ground and wondered who lost it
q: if you find someone's dropped stuff, do you keep it? > i'd guard it for them / i'd keep it / i'd look for them / depends what it is
q: how would you feel if someone took your dropped stuff? > furious / sad / fair, i'd do it too / depends on who
d: once i found someone's armor all scattered on the ground > scattered armor is sad / ugh, that's sad
d: i once stood guard over someone's stuff in case they came back > standing guard is kind / that's nice of you
d: a pile of dropped stuff is someone's worst moment, lying in the grass > their worst moment, oof / that's a sad way to see it
d: the stuff vanishes soon anyway, so maybe take it > vanishing anyway, yeah, it's tricky / that's fair
o: i'd want someone to save my stuff if they found it > save it and return it, yeah / me too
o: maybe the right thing is to keep it and find them > finding them is the hard part / that's the right thing
o: i kept a sword once and gave it back a day later > giving it back is the move / that's good
o: what you do with someone's dropped stuff says a lot > it says a lot, yeah / true
c: finders keepers. dropped stuff is fair game | if it's on the ground it's yours
y: yeah | true | fair game
n: give it back | that's cold | it's someone's
""")

angle(RUINS, "ruins_old_pots", "digging up old pots", """
m: i brushed sand and found an old pot shard | some sand hides little pieces of old pots
m: the old pots have pictures on them, like someone was telling a story | finding an old shard feels like hearing a voice
q: who do you think made the old pots? > someone like us / a village long ago / an artist / nobody knows
q: what do you think the pictures on the shards mean? > a story / a map / just decoration / a message
d: you have to brush so slowly or it breaks > slow brushing, patience / that's the hard part
d: i put four together and made a pot with their pictures > a pot made of old pictures, nice / i want to see it
d: a shard is the closest thing to talking to someone from long ago > talking to the past, yeah / that's a nice way to put it
d: a skull, a heart, an arrow... it's like a language > maybe it is a language / huh. could be
o: maybe one day someone will dig up our stuff and wonder > they'll wonder about us / probably, yeah
o: the pictures feel like they mean something sad > sad pictures, yeah / maybe they do
o: i put the pot on a shelf. it feels like keeping something safe > keeping it safe, good / that's a nice thing to do
o: whoever made the pots wanted to be remembered, i think > they are, by you / i think so too
c: digging up old stuff is good, it keeps the past alive | the past should be dug up, not left
y: yeah | true | agreed
n: leave it buried | the past is past | some stuff should stay buried
""")

# ======================================================================================================================
# Right and wrong: fighting.
FIGHT = theme(
    "fight", ["fighting", "philosophy", "morals"], ["fighting", "mobs", "argument", "morals"],
    react={"any": "that's a hard one | i think about that after fights | huh. i don't know | fair point",
           "pvp_sweat": "fighting is just what we do | i fight to win. but yeah, i've wondered",
           "casual": "lol i just swing and hope. but fair | bro i hit a cow by accident once and felt bad",
           "hermit": "fight when you must. not when you can. | every fight costs.",
           "newbie": "i never felt bad about hitting zombies before | i'm still scared of fighting",
           "helper": "i only fight to protect people",
           "bandit": "a fight's a fight. winner takes all"},
    nudge="go on | that fight's still on your mind | keep going | sounds like something happened",
    close={"any": "anyway. i'd rather not fight today | only when i have to, i guess | sword away for now | let's keep the peace",
           "hermit": "sheathe it.", "casual": "ok but if a creeper comes i'm swinging lol", "newbie": "i'll try to be brave but fair"},
    bclose="watch your back out there | fight smart | peace is good | i've got your back if it comes to it",
    yes="right? | exactly | see", no="we'll see | hm | agree to disagree",
    sits={"killed": "fought a lot today, feeling it | my sword arm's tired from today | killed {a.kills} mobs today, got me thinking",
          "close": "{a.close_call} nearly got me and now i'm thinking about fighting | that fight earlier is still in my head",
          "angry": "i'm so angry i want to hit something | i'm fuming right now | someone's really getting on my nerves"})
JOKES[FIGHT] = V("i've killed so many zombies they probably have a wanted poster of me lol | my sword has more kills than i have friends")

angle(FIGHT, "fight_mobs_evil", "whether mobs are evil", """
m: are the mobs evil, or just doing what mobs do | a zombie doesn't hate me, it's just a zombie
m: i wonder if the skeletons know what they're doing | i killed a creeper and felt kind of bad
q: do you think mobs are evil? > yes / no, they're just mobs / some are / the creepers are, for sure
q: do you feel bad killing mobs? > never / sometimes / only the passive ones / only the endermen
d: they come at us every night like it's their job > just doing their job, kinda / the night changes everything
d: the creeper just walks up and hisses, no anger, nothing > no anger is the weird part / that's what makes it creepy
d: the spiders leave you alone in the day. they're not all bad > spiders in the day are chill, true / good point
d: i think the night makes them that way > the night changes everything / maybe
o: maybe they're not evil, just made wrong > made wrong, sad / maybe
o: if they could choose, would they still come for us > i don't know / i hope not
o: i fight them, but i don't hate them > not hating them is mature / same
o: i'd rather understand them than hate them > understanding is better / not hating them is mature
c: mobs are evil and deserve it | every hostile mob deserves to be killed
y: yeah | true | every single one
n: they're just mobs | nah, they can't help it | evil's a strong word
""")

angle(FIGHT, "fight_zombie_villagers", "zombies that were villagers", """
m: zombie villagers used to be villagers | you can cure a zombie villager. so they're still in there
m: some zombies were people once | i saw a zombie villager and it looked sad
q: if a zombie can be cured, is it wrong to kill it? > a bit, yeah / no, it's attacking you / only if you could've cured it / i don't know
q: have you ever cured a zombie villager? > yes, it was amazing / no, but i want to / i didn't know you could / no, too hard
d: a golden apple and a potion and they come back > curing is so worth it / that's amazing
d: they shake when they're being cured, like they're fighting it > they're fighting to come back, oh / curing is so worth it
d: the cured ones are so grateful, they give you deals > grateful villagers, aw / curing is so worth it
d: curing one changes how you see every zombie > heavy / it really does
o: what if regular zombies were someone too, once > don't say that / that's heavy
o: maybe there's always something left in there to save > always something left, i like that / that's really kind
o: i try to cure them when i can now > that's really kind / good
o: i can't look at a zombie the same way anymore > me neither now / don't say that
c: if you can cure a zombie villager, you should | killing a zombie villager is wrong if you can cure it
y: yes | true | agreed
n: too much work | it's attacking me | self defense first
""")

angle(FIGHT, "fight_each_other", "fighting each other", """
m: why do we ever fight each other | there's enough danger without us hitting each other
m: i got into a fight with someone over nothing | fighting someone like us feels different from fighting mobs
q: is it ever right to fight someone like us? > to protect someone / never / if they started it / only in a spar
d: mobs don't hold grudges. people do > grudges last longer than wounds / so true
d: after a fight with someone, it's awkward for days > awkward for days, so true / yeah, that's the worst
d: we're all in the same dark, why fight > we're all in the same dark, yeah / exactly
d: a spar is fine, but a real fight hurts more than the hits > spars are fine, real fights aren't / true
o: the night is the enemy, not each other > same dark, same team / wise
o: maybe fights happen because people are scared > scared people fight, yeah / wise
o: i made up with the one i fought, eventually > making up is the best part / good
o: i'd rather lose an argument than a friend > wise / same
c: we should never fight each other | fighting each other is always wrong
y: true | agreed | yeah
n: sometimes you have to | nah, bandits exist | some people deserve it
""")

angle(FIGHT, "fight_neutral_mobs", "leaving neutral mobs alone", """
m: endermen leave you alone if you don't look at them | the wolves only fight if you start it
m: i like the mobs that only fight back. they seem fair | i accidentally hit a wolf and the whole pack came
q: do you leave neutral mobs alone? > always / mostly / not the endermen, they have pearls / i try to
q: is it fair to start a fight with something that leaves you alone? > no / if you need something from it / not really / it's the world, everything's fair
d: the enderman just carries blocks around and minds its business > minding its business, respect / let it be
d: neutral mobs are only dangerous if you bother them > bother them and you get what you get / fair rule
d: a fight you start is different from one you finish > starting versus finishing, yeah / true
d: a wolf pack comes at you because they look after each other > the pack sticks together, fair / respect
o: maybe the world would be calmer if we left more things alone > calmer, yeah / probably
o: i try to treat them like neighbours > neighbours, nice / good neighbours
o: how you treat things that don't fight back says a lot > says a lot, yes / it does
o: the endermen are just lonely block collectors > lonely block collectors, aw / lol accurate
c: you should never attack something that leaves you alone | starting fights with neutral mobs is wrong
y: yeah | true | agreed
n: pearls though | sometimes you need stuff | it's just a mob
""")

angle(FIGHT, "fight_revenge", "getting even", """
m: i want to get even with the skeleton that killed me | revenge feels good for a minute, then it's empty
m: someone took my stuff and i want to get them back | i keep thinking about getting even
q: is getting even ever worth it? > sometimes / never / it feels good though / only if it stops it happening again
q: what's the best way to deal with someone who wronged you? > talk to them / ignore them / get even / forgive and stay away
d: i went back for that skeleton and it wasn't even there > the skeleton probably burned in the sun anyway / lol typical
d: getting even took a whole day and i felt worse > it does that / yeah, it never feels like you think
d: revenge is like a fire that burns you too > a fire that burns you, yeah / that's true
d: i'd rather they just said sorry > a sorry is worth more / same
o: maybe letting it go is the stronger move > letting go is hard but strong / it is
o: the best revenge might be living well > living well, yeah / i like that
o: grudges are heavy to carry around all day > heavy, yeah / so put it down
o: i'd rather spend the day building than plotting > building beats plotting / good choice
c: you should always get even | revenge is fair
y: yeah | true | an eye for an eye
n: let it go | revenge is a waste | it never ends
""")

angle(FIGHT, "fight_to_protect", "fighting to protect", """
m: i only fight when someone needs protecting | fighting is ok if it's for someone else
m: i jumped in front of a friend once, didn't even think | protecting someone is the only fight that feels right
q: what would you fight for? > my friends / my home / my animals / nothing, i'd run
q: is fighting for someone braver than fighting for yourself? > yes / no, both are / maybe / it's easier, weirdly
d: when i'm protecting someone, i'm not scared anymore > that's real courage / not scared for someone else, yeah
d: i'd fight a whole raid for my town > that's real courage / you'd be a good protector
d: it's weird how brave you get for someone else > not scared for someone else, yeah / that's real courage
d: a golem fights for villagers like that > like the golems, yeah / you'd be a good protector
o: maybe protecting people is what makes fighting worth it > worth it, yeah / i think so
o: i think golems might love the villagers > i believe it / they must
o: i'd rather be the one who stands in front than the one who runs > standing in front, respect / i know you would
o: being needed makes you brave > it really does / needed and brave
c: fighting is only okay to protect someone | the only good reason to fight is protecting others
y: yes | true | agreed
n: self defense counts | sometimes you fight for loot | fighting's fun too
""")

# ======================================================================================================================
# Freedom: going anywhere, too much choice, rules, feeling stuck.
FREEDOM = theme(
    "freedom", ["exploring", "philosophy", "freedom"], ["exploring", "home", "philosophy", "freedom"],
    react={"any": "that's a big one | i think about that a lot | huh. freedom's a weird thing | yeah, i've felt that",
           "nomad": "i go where i want. that's all i need | the road is freedom. the rest is walls",
           "homebody": "i'm free to stay home, and i do lol | freedom sounds tiring honestly",
           "casual": "lol i'm free to do nothing all day. but yeah | bro's going full wanderer. ok fair",
           "hermit": "free is having little. | walls keep things out. and in.",
           "newbie": "i didn't know we could just go anywhere | i didn't know i was allowed to leave",
           "speedrunner": "free to go fast. that's my freedom"},
    nudge="go on | you've got itchy feet, huh | keep going | something's on your mind",
    close={"any": "anyway. it's nice knowing we could go | maybe i'll take a walk later | free is good | the road's always there",
           "hermit": "go, or stay. choose.", "casual": "ok i'm free to go eat now lol", "newbie": "i'm gonna go explore a bit"},
    bclose="go wherever you want, just come back | the road's there if you need it | free, but visit | i'll keep your bed warm",
    yes="right? | exactly | see", no="hm | we'll see | fair",
    sits={"nohome": "no home, no walls. freedom, i guess | i sleep wherever. it's freeing and weird",
          "town": "town life's nice but sometimes i want to run off | living in town makes me itch sometimes",
          "bored": "bored. maybe i should just go somewhere new | nothing to do. i could go anywhere though"})
JOKES[FREEDOM] = V("i could walk anywhere and i pick the kitchen every time lol | total freedom and i spend it sorting chests")

angle(FREEDOM, "freedom_anywhere", "being able to go anywhere", """
m: we can literally go anywhere we want | nobody can tell us where to walk
m: i could leave right now and never come back if i wanted | it's wild how free we are out here
q: if you could go anywhere right now, where would you go? > the mountains / the sea / nowhere, i'm good / somewhere nobody's been
q: does it feel good, being free to go anywhere? > yeah, the best / it's a bit scary / i never think about it / only when i'm bored
d: i stood at the edge of town and just thought, i could go > you could go, yeah. that's something / that's a big feeling
d: no walls, no rules, just the world > no walls is freeing and scary / that's the dream
d: freedom is great until you need a bed at night > the bed problem is real lol / true
d: knowing i could go makes staying feel like a choice > staying as a choice is a nice way to see it / i like that
o: maybe freedom isn't going, it's knowing you could > knowing you could, yes / i like that
o: the ones who never leave are free too, if they chose it > chosen staying is free / true
o: too much freedom makes it hard to pick anything > too many choices, yeah / that's the catch
o: i like a little freedom and a lot of home > best of both / same
c: freedom is the most important thing | nothing beats being free
y: true | yeah | agreed
n: safety matters more | home matters more | friends matter more
""")

angle(FREEDOM, "freedom_choice", "too much choice", """
m: sometimes there are too many things i could do | i wake up and don't know what to do with the day
m: with the whole world open i just stand there | too much choice makes me freeze
q: do you ever get stuck because you could do anything? > all the time / sometimes / no, i always know / only on rest days
q: how do you decide what to do each day? > whatever i need / whatever's fun / i ask someone / i just pick, kinda random
d: when i had nothing, i knew exactly what to do. find food > needing food makes it simple, yeah / simpler times lol
d: now i have stuff, and i'm lost > having stuff makes it harder, weirdly / i get that
d: small goals help. one chest of iron, then see > small goals are the trick / one chest at a time
d: the newbies are lucky, every day has a clear job > they are, kind of / having stuff makes it harder, weirdly
o: maybe a little bit of need is good for us > a little need, yeah / probably
o: i'd rather have a plan someone else made, sometimes > sometimes, sure / i'll make you one lol
o: being free is harder than people say > it really is / nobody warns you
o: i make lists now, so the freedom has edges > lists are good edges / smart
c: having fewer choices makes life easier | it's easier when you have less to choose from
y: true | yeah | agreed
n: more choice is better | nah, i like options | i'd hate being told
""")

angle(FREEDOM, "freedom_rules", "rules in towns", """
m: out in the wild there are no rules at all | towns have leaders and rules now
m: i wonder if we need rules, or if we're fine without | some towns are getting strict
q: do you think we need rules? > some, yeah / no, we're fine / only don't steal / just be nice, that's the rule
q: would you follow a town's rules? > if they're fair / probably / no / depends who made them
d: the rule against stealing from chests is a good one > don't touch chests is the one rule that matters / agreed
d: without rules, the bandits win > true / that's the problem
d: rules are just people agreeing so nobody gets hurt > that's a good way to put it / agreed
d: a leader with too many rules is scary > too many rules is a cage / yeah
o: maybe the best rules are the ones nobody needs to say > unsaid rules, yeah / true
o: freedom out there, safety in here. you pick > you pick, i like that / fair deal
o: a good rule protects the weak ones > protect the weak, yeah / that's what rules are for
o: i'd rather have kind people than lots of rules > kind people, always / same
c: towns need rules to work | without rules, everything falls apart
y: yeah | true | agreed
n: kindness is enough | rules make it worse | nah, we're fine without
""")

angle(FREEDOM, "freedom_stuck", "feeling stuck", """
m: i feel stuck doing the same thing every day | free to go anywhere, and i still feel stuck
m: mine, smelt, sleep, mine. it's a loop | i haven't been anywhere new in ages
q: do you ever feel stuck? > yeah, often / sometimes / no, i love my routine / right now, actually
q: what gets you unstuck? > going somewhere new / a new build / talking to someone / a good night's sleep
d: it's not a bad loop, it's just a loop > a loop isn't bad, just a loop / fair
d: the walls of my house feel closer lately > closer walls means you need a walk / go outside lol
d: i think i'm scared to try something new and fail > failing at something new is still new / that's normal
d: i keep saying tomorrow i'll go explore > tomorrow never comes, so go today / i'll come with you
o: maybe being stuck is just a sign it's time for a change > time for a change, yeah / probably
o: i'll go somewhere new this week. i mean it > i'll hold you to it / good
o: maybe the trick is one small new thing every day > one small new thing, i like that / i could do that
o: i don't need to leave, just look at things fresh > fresh eyes help / that works too
c: routine is good for you | a steady loop is a happy life
y: true | yeah | agreed
n: routine is a cage | nah, change is better | i'd go crazy
""")

# ======================================================================================================================
# Dreams.
DREAMS = theme(
    "dreams", ["dreams", "sleep"], ["dreams", "sleep", "night", "philosophy"],
    react={"any": "dreams are weird | i love talking about dreams | huh, i've thought that too | that's so strange",
           "casual": "lol i dreamed i was a chicken once. ok but yeah | bro's dreaming deep. i'm listening",
           "hermit": "sleep is the only door that opens itself. | dreams know things.",
           "newbie": "i thought sleep was just dark | i had a dream about diamonds once",
           "helper": "dreams say a lot about how we feel",
           "speedrunner": "i skip the night. i don't dream. maybe i should"},
    nudge="go on | tell me more | keep going | there's more",
    close={"any": "anyway. sweet dreams, i guess | i'm going to try to remember mine tonight | dreams are strange | "
                  "tonight, maybe a good one",
           "hermit": "sleep well.", "casual": "ok if i dream about you that's on you lol",
           "newbie": "i hope i dream about something nice"},
    bclose="sweet dreams | sleep well | tell me your next one | dream something nice",
    yes="right? | exactly | yeah", no="hm, maybe | we'll see | fair",
    sits={"night": "it's late and i'm sleepy | about to sleep, but i had a thought | bedtime thoughts",
          "morning": "i had the weirdest dream | woke up from a dream i can't shake | still half in a dream this morning",
          "calm": "feeling dreamy today | calm day, dreamy thoughts"})
JOKES[DREAMS] = V("i dreamed i was a creeper and everyone ran away lol | my dreams are just me looking for diamonds")

angle(DREAMS, "dreams_do_we", "whether we dream", """
m: when we sleep the night just skips. do we dream in that bit | i close my eyes and it's morning, like no time passed
m: i think i dream, but i can never remember | sleep feels like a blink, not a rest
q: do you think we dream when we sleep? > yeah, we just forget / no, it's just dark / sometimes / i do, every night
q: what's the strangest thing you've dreamed? > a giant sheep / flying over the sea / my house underwater / someone i've never met
d: sometimes i wake up with a feeling, but no picture > a feeling with no picture, yeah / same, it fades so fast
d: the night skips, but my head doesn't > huh. that's a good way to put it / mine too. i wake up tired sometimes
d: i woke up once and remembered falling > falling dreams are the worst / ugh, i hate those
d: i think dreams happen in the bit we skip > the skipped bit is where dreams live, maybe / maybe you dream and it's just short
o: what if our dreams are memories from before > memories from before, woah / that'd explain a lot
o: maybe the night isn't skipped, we're just somewhere else > somewhere else, maybe / that's a creepy nice thought
o: i'd like to dream on purpose > i'd love that too / i'd dream about a giant cake
o: everyone sleeps at once and the night just goes. like we dream it away > dreaming the night away, nice / i like that
c: everyone dreams, they just forget | we all dream, even if we don't remember
y: i think so | yeah | true
n: i don't dream | nah, it's just dark | not everyone
""")

angle(DREAMS, "dreams_phantoms", "phantoms and sleep", """
m: if you don't sleep the phantoms come | it's weird that the sky punishes you for staying up
m: the phantoms only come after days without sleep | i think phantoms are what tiredness looks like
q: what do you think phantoms are? > bad dreams / sky mobs / tiredness come to life / punishment for not sleeping
q: why do you think the world wants us to sleep? > to rest / so the mobs leave / to dream / so we don't go crazy
d: phantoms swoop down out of the dark, screaming > they're so loud / i hate that sound
d: phantoms only come for the ones who need sleep > tiredness with wings, yeah / that's the creepy part
d: it's like the night itself sends the phantoms > the night sends them, creepy / maybe it does
d: three days awake and they find you > three days is the limit, apparently / that's why i sleep every night
o: maybe phantoms are the world taking care of us in a scary way > a scary kind of care / that's a nice way to see it
o: sleeping isn't just rest, it's safety > safety, yeah / that's real
o: some people stay up to avoid their thoughts > that's real / i've done that
o: the phantoms are what happens when you push too hard > push too hard, the sky pushes back / that's real
c: sleeping every night is the most important thing | you should never skip sleep
y: true | yeah | agreed
n: sleep is overrated | i can go days | phantoms are easy
""")

angle(DREAMS, "dreams_dream_house", "a dream house", """
m: i have a dream house in my head i'll probably never build | i dream about a house by a lake with a garden
m: my dream build would take a hundred days | i keep a picture of my dream house in my head
q: what's your dream house like? > by the sea, with a dock / a castle, obviously / a tiny cabin / a treehouse
q: do you think you'll ever build your dream house? > one day / never, it's too big / i'm building it now / maybe with help
d: my dream house has big windows so the morning comes in > big windows, yes / that sounds lovely
d: mine has a room just for chests, all labelled > a labelled chest room, the dream / ok i want that too
d: i dream about the view more than the house > the view matters most / same
d: my dream house always has friends nearby > friends nearby is the best part / i'll be your neighbour
o: maybe the dream is better than the real thing > dreams are always perfect, yeah / maybe, but build it anyway
o: i'm scared if i build it, i'll want something else > you might, that's ok / then you'll have two houses
o: having a dream house keeps me going > that's good / keeps you going, good
o: i'll build it one block at a time > one block at a time / i'll help with some blocks
c: you should build your dream house, no matter how long | dream builds are worth any amount of time
y: yes | true | go for it
n: too much work | dreams are for dreaming | it'd never match
""")

angle(DREAMS, "dreams_wishes", "what you want most", """
m: if i could have one wish i don't even know what it'd be | i've been thinking about what i want most
m: i used to want diamonds. now i want quiet | what i want keeps changing
q: if you could have anything, what would you wish for? > friends forever / a huge house / one more day with someone / to know why we're here
q: has what you want changed since you arrived? > completely / a little / no, still diamonds / yeah, i want simpler stuff now
d: when i first got here i wanted everything > same, everything at once / me too
d: now i'd be happy with a warm house and good people > warm house and good people is the dream / that's all anyone needs
d: i think i want to feel safe more than anything > safe is a big want / that's fair
d: maybe wanting less is growing up > wanting less is wise / maybe
o: maybe what we want most is what we already have and forget > we forget what we have, yeah / that's true
o: i want to know my friends will be ok > they will be / i'll make sure
o: i'd wish for a world where nobody's alone > that's a good wish / that's a beautiful wish
o: wishes say who you are, i think > that's true / then yours say good things
c: wanting less makes you happier | the less you want, the happier you are
y: true | yeah | agreed
n: ambition is good | nah, want big | wanting keeps you going
""")

# ======================================================================================================================
# Fear.
FEAR = theme(
    "fear", ["feelings", "danger", "fear"], ["danger", "night", "feelings", "fear"],
    react={"any": "i get scared too | fear's a weird thing | yeah, that's scary | that's honest of you",
           "pvp_sweat": "i don't get scared. ok maybe a little | fear just means you're about to win",
           "casual": "lol same, i'm scared of everything. but fr | bro i'm scared of chickens. but yeah",
           "hermit": "fear is the oldest teacher. | the brave are scared too.",
           "newbie": "i'm scared of literally everything here | i thought only i got scared",
           "helper": "it's ok to be scared. i've got you",
           "speedrunner": "fear costs time. but yeah, it's there"},
    nudge="go on | it's ok to be scared | it's ok, keep going | i'm listening",
    close={"any": "anyway. scared is ok | let's stay safe out there | thanks for saying that | we're braver together",
           "hermit": "be scared. go anyway.", "casual": "ok i'm not sleeping tonight lol", "newbie": "i feel a bit braver now"},
    bclose="i've got your back | you're braver than you think | stick with me | scared together is better",
    yes="right? | yeah | exactly", no="hm, maybe | we're different, i guess | fair",
    sits={"anxious": "i've got a bad feeling today | i'm on edge and i don't know why | something feels off",
          "night": "the dark is getting to me tonight | i keep hearing things | nights like this get to me",
          "close": "{a.close_call} almost got me. still shaking | i nearly died earlier and can't stop thinking",
          "timid": "i'm not very brave, you know | i scare easy, honestly"},
    needs=["!rivals"], trust=True)
JOKES[FEAR] = V("i screamed at a bat once. a bat lol | i'm scared of the dark and i live in a world that's dark half the time")

angle(FEAR, "fear_what_scares", "what scares you most", """
m: i've been thinking about what scares me most | everyone's scared of something here
m: i act brave but there's stuff that gets me | fear is the thing nobody talks about
q: what scares you most? > creepers / the dark / being alone / losing my friends
d: it's not the mobs, it's the waiting for them > the waiting is the worst part, yeah / same
d: i'm scared of losing my stuff more than dying, honestly > losing stuff is a real fear / that's fair
d: being alone in the dark is the worst one for me > alone in the dark gets everyone / me too
d: the hiss behind you is the scariest sound there is > the hiss, oh, don't / ugh, yes
o: maybe fear is just caring about something > caring, yeah / that's a nice way to see it
o: everyone's scared, the brave just keep going > keep going, yeah / true
o: saying it out loud makes it a bit smaller > smaller, true / it does
o: i'm less scared when someone's with me > me too / then stick with me
l: honestly, i'm scared a lot more than i show > me too / you're brave for saying it
l: thanks for not laughing at me > i'd never laugh at that / you're brave for saying it
c: being scared is a weakness | fear makes you weak
y: kinda, yeah | sometimes | true
n: fear keeps you alive | being scared is normal | it's not weak to be scared
""")

angle(FEAR, "fear_dark", "the dark", """
m: i'm scared of the dark, honestly | the dark here isn't empty, that's the problem
m: when the torch goes out in a cave my heart stops | everything bad comes out of the dark
q: are you scared of the dark? > yes / a little / only underground / no, the dark is just dark
q: what's the scariest place you've been? > a cave with no torches / the deep dark / the woods at night / my own basement lol
d: things spawn in the dark. it's not in my head > it's not in your head, it's real / true
d: i put torches everywhere, even where i don't need them > torches everywhere is smart / same
d: i can hear them before i see them > hearing them first is the worst / ugh, that groaning
d: in the dark you're always the last to know > that's what makes it scary / true
o: maybe being scared of the dark is just being smart > smart, yeah / it really is, here
o: light is the one thing that keeps us safe > light is everything / torches forever
o: a torch is the bravest little block > brave little torch, aw / lol i love that
o: the dark makes home feel like home > home in the dark, yeah / true. lights on, door shut
c: being scared of the dark is just smart here | the dark deserves the fear
y: true | yeah | agreed
n: it's just dark | nah, light it up and it's fine | i'm not scared of it
""")

angle(FEAR, "fear_useful", "fear keeping you alive", """
m: i think being scared has saved my life a lot | fear's annoying but it keeps me out of lava
m: the scared ones live longer, i swear | i used to hate being scared. now i listen to it
q: do you think fear is useful? > very / sometimes / no, it just slows you down / only a little bit of it
q: has fear ever saved you? > yeah, i ran just in time / every night / no, it got me killed / probably
d: the time i was too scared to jump, the drop would've killed me > good thing you listened / see, fear works
d: fear is like a voice that says not that way > listen to that voice / that voice has saved me too
d: too much fear and you never leave the house > too much is a cage, yeah / true
d: the trick is being scared and going anyway > scared and going anyway is brave / that's the trick
o: maybe brave just means scared but careful > scared but careful, yeah / probably
o: i'd be dead without fear, honestly > probably, yeah / same
o: fear is like a torch for the mind > a torch for the mind, nice / i like that
o: i'm grateful for it, weirdly > grateful, same / weird but true
c: fear is good for you | being scared keeps you alive
y: true | yeah | agreed
n: fear holds you back | nah, it gets you killed | courage is better
""")

angle(FEAR, "fear_brave", "what brave means", """
m: i wonder what being brave really means | people call me brave but i'm terrified
m: i don't think brave means not scared | the bravest people i know are scared all the time
q: what does brave mean to you? > scared and doing it anyway / not running / helping others / i don't know
q: who's the bravest person you know? > you, honestly / someone who lost everything and kept going / the newbies / nobody, everyone runs
d: the newbies are brave. they go out not knowing anything > newbies are so brave / they really are
d: brave is going back into the cave that killed you > going back is brave / so true
d: saying sorry is braver than fighting a zombie > sorry takes guts / way braver
d: brave is small things too > small brave things count / yeah
o: maybe brave is just doing the next thing when it's hard > the next thing, yeah / i think so
o: you're braver than you think > you too / thanks. i needed that
o: i'd like to be brave in the quiet ways > quiet brave, yeah / you already are
o: being kind when you're scared is brave too > kind and scared, that's brave / true
c: brave means not being scared | real bravery is having no fear
y: yeah | true | i think so
n: brave means scared and going anyway | no, that's reckless | nobody has no fear
""")

# ======================================================================================================================
# Happiness.
HAPPY = theme(
    "happy", ["feelings", "philosophy", "happiness"], ["feelings", "day", "friends", "happiness"],
    react={"any": "i love that | that's a nice thing to think about | huh, good one | "
                  "yeah, i've been wondering too",
           "casual": "lol food makes me happy. ok but yeah | bro's in a good mood, i like it",
           "hermit": "a warm fire. a full belly. quiet. | happy is small.",
           "newbie": "i'm happy all the time here! mostly | everything's so new, it's great",
           "helper": "seeing others happy makes me happy",
           "speedrunner": "a good run makes me happy. that's it",
           "hoarder": "a full chest. that's happiness",
           "builder": "a finished wall makes me happy"},
    nudge="go on | you seem happy today | keep going | tell me more",
    close={"any": "anyway. today's a good day | i'm happy right now, i think | good stuff | let's do more of that",
           "hermit": "this is good.", "casual": "ok we're too happy, something's gonna explode lol", "newbie": "today is the best"},
    bclose="happy for you | more days like this | you deserve it | let's keep it going",
    yes="right? | exactly | yeah", no="hm, ok | each their own | fair",
    sits={"win": "today was amazing | i'm on top of the world right now | best day in ages",
          "calm": "feeling really good today | peaceful day, happy me",
          "friends": "i'm happy we're hanging out | this is nice, just us"})
JOKES[HAPPY] = V("i was happy for a whole minute then a creeper showed up lol | happiness is a full stack of bread, no more questions")

angle(HAPPY, "happy_good_day", "what makes a good day", """
m: i've been thinking about what makes a good day | today was good and i don't know exactly why
m: some days just feel right, even if nothing happens | i want more days like today
q: what makes a good day for you? > a sunrise and a full belly / finding something shiny / no deaths / time with friends
q: what was the best day you've had here? > the day i built my house / the day we met / a day of just fishing / today, maybe
d: a good day doesn't need anything big > nothing big needed, yeah / true
d: i think it's waking up with nothing chasing me > nothing chasing you is a good start / lol same
d: good food, a bit of work, a chat at night > food, work, chat. perfect / that's the recipe
d: the best days are the quiet ones > quiet days are the best / agreed
o: maybe a good day is just one where you notice it's good > noticing it, yeah / i like that
o: i want to remember today > let's remember it / me too
o: i miss good days because i'm waiting for great ones > good days are enough / that's so true
o: good days add up to a good life > good days are enough / that's the plan
c: a quiet day is better than an exciting one | the best days are the boring ones
y: true | yeah | agreed
n: excitement is better | nah, adventure days | boring is boring
""")

angle(HAPPY, "happy_small_things", "small happy things", """
m: the smallest things make me happiest | a perfect loaf of bread made my whole morning
m: the sound of rain on my roof makes me happy | i got happy over a sheep today, that's where i'm at
q: what small thing makes you happy? > a fresh stack of bread / a cat on the bed / a sunset / finding coal right away
q: do you think small things matter more than big ones? > yeah / they add up / no, big things matter / both
d: i don't need diamonds, i need a cozy corner > cozy corners are underrated / same
d: a torch in the right spot is a joy > a well placed torch, yeah / builder joy
d: the first bite of steak after a long day > first bite of steak, perfect / the best
d: my dog's tail wagging > tail wags fix everything / that's the best one
o: maybe happiness is just paying attention to small stuff > paying attention, yeah / i think so
o: if i stop noticing small things, i'm in trouble > keep noticing / i'll remind you
o: big things are rare. small things are every day > every day, yeah / so enjoy those
o: the small stuff never lets you down > it never does / true
c: small things matter more than big things | little joys beat big wins
y: true | yeah | agreed
n: big wins though | nah, diamonds | big things last
""")

angle(HAPPY, "happy_right_now", "whether you're happy", """
m: i was wondering if i'm actually happy | nobody ever asks if we're happy
m: i think i'm happy, but i'm not sure | being happy is harder to notice than being sad
q: are you happy right now? > yeah, i think so / mostly / not really / right now, yes
q: what would make you happier? > more friends / a bigger house / fewer creepers / honestly nothing, i'm good
d: sad is loud, happy is quiet > so true / that's why you miss it
d: i only notice i was happy after > after is when you see it / same
d: i think i'm happy, i just forget to check > checking helps / check now then
d: happy feels like nothing's wrong > nothing wrong is a kind of happy / i like that
o: maybe happiness is just not wanting to be anywhere else > i like that / that's a good way to put it
o: i'm happy right here, talking to you > me too / same
o: i'm happier than i was when i arrived > that's good to hear / me too
o: asking makes me notice, so thanks > anytime / that's good to hear
c: happiness is a choice | you can choose to be happy
y: true | yeah | i think so
n: not always | nah, sometimes it's hard | it's not that easy
""")

angle(HAPPY, "happy_more_stuff", "whether more stuff helps", """
m: does having more stuff actually make us happier | my chests are full and i'm not happier
m: i was happier in my dirt hut, i think | the more i have, the more i worry about losing it
q: does more stuff make you happier? > a bit, at first / no / yes, obviously / only food
q: when were you happiest? > in my first house / today / when we met / on a long walk
d: when i had nothing, everything i found was exciting > exciting when you have nothing, yeah / i remember that
d: now i have so much, nothing's exciting > that's sad, kind of / i get that
d: more chests means more to protect > more to protect, more worry / true
d: the happiest i've been, i had a crafting table and a bed > a table and a bed was enough / simple times
o: maybe happiness isn't in the chests > not in the chests, no / probably not
o: the stuff i love most is the stuff with stories > stories make stuff special / same
o: i want to give some away and see if i feel lighter > try it / i'll take some lol
o: i'd trade half my stuff for good company > good company, yeah / you've got it now
c: more stuff doesn't make you happier | happiness has nothing to do with stuff
y: true | yeah | agreed
n: stuff helps | nah, diamonds make me happy | more is more
""")

# ======================================================================================================================
# The players: are they like us?
PLAYERS = theme(
    "players", ["players", "philosophy"], ["players", "philosophy", "gossip"],
    react={"any": "the players are a mystery | i've wondered that about players too | huh. they're so strange | good point",
           "casual": "lol players are wild. ok but yeah | bro the players do whatever they want. fair though",
           "hermit": "players come. players go. | they're like weather.",
           "newbie": "i still don't really get what a player is | i saw one once! they're fast",
           "helper": "i'd like to help a player sometime",
           "bandit": "players carry good loot. that's all i know",
           "pvp_sweat": "players are the only real challenge",
           "speedrunner": "players move like me. respect"},
    nudge="go on | you met one, didn't you | keep going | say more",
    close={"any": "anyway. players are players | i'd like to meet a nice one | they're not that different, maybe | weird bunch",
           "hermit": "let them be.", "casual": "ok if a player shows up i'm hiding lol", "newbie": "i wanna meet one!"},
    bclose="say hi if you see one | they're probably fine | be careful with them | tell me if you meet one",
    yes="right? | exactly | yeah", no="hm, maybe | we'll see | fair",
    sits={"likes_player": "{a.liked_player} is nice, for a player | i've been thinking about {a.liked_player} | "
                          "i met a nice player the other day",
          "dislikes_player": "{a.disliked_player} still bugs me | i keep thinking about {a.disliked_player} | "
                             "a player was rude to me and it got me thinking",
          "evening": "saw a player walk off into the sunset | quiet evening. wonder what the players are doing"})
JOKES[PLAYERS] = V("players build a castle in a day and i'm still on my door lol | a player jumped around me for a minute then left. rude")

angle(PLAYERS, "players_like_us", "whether players are like us", """
m: i wonder if players are like us | players look like us but they're different somehow
m: a player walked past me and i couldn't tell what they were thinking | maybe players are just like us with more luck
q: do you think players are like us? > yeah, pretty much / no, they're different / some are / i've never met one
q: what's different about players? > they come and go / they build so fast / they don't talk much / they never seem scared
d: they move like they know the world better than we do > they really do / that's what i noticed too
d: they jump a lot. like, a lot > the jumping is so weird / lol yes. so much jumping
d: they talk like we do sometimes, then go quiet for ages > quiet for ages, yeah / it's weird
d: they never seem scared of the night > never scared, that's the strange part / i noticed that too
o: maybe they're like us, but from somewhere else > from somewhere else, maybe / maybe
o: if they feel what we feel, we should be nice > be nice, yeah / agreed
o: maybe they wonder about us the same way > they probably do / i hope so
o: i'd like to ask one what it's like > ask, then tell me / me too
c: players are just like us | there's no real difference between us and players
y: i think so | yeah | true
n: they're different | nah, they're strange | they come back too easy
""")

angle(PLAYERS, "players_vanish", "players vanishing", """
m: players just vanish sometimes, like they blink out | i saw a player and then they were gone, mid step
m: players leave and come back like it's nothing | where do players go when they disappear
q: where do you think players go when they vanish? > home, somewhere else / to sleep / another world / nobody knows
q: would you want to vanish like that? > yeah, sometimes / no, it's creepy / just for a rest / only if i could come back
d: one second they're there, the next it's just their footprints > creepy / i've seen that too
d: they come back right where they left > coming back where they left, so weird / like they never left
d: it's like they step out of the world for a while > stepping out of the world, creepy / i'd love to try that
d: when they're gone, their stuff just waits > their stuff waiting, yeah / like a house waiting
o: maybe they have somewhere else they live, and this is the visit > a visit, yeah / maybe
o: i wonder if they miss this place while they're gone > i hope they miss it / they must
o: if i could step out, i'd come back to the same spot too > same spot, yeah / me too
o: maybe they're tired, like us > tired, maybe / then sleep well, players
c: players are visitors, not locals | players don't really live here
y: yeah | true | they come and go
n: they live here too | nah, some stay | they're locals too
""")

angle(PLAYERS, "players_dying", "whether players die like us", """
m: i saw a player die and come back, just like us | players respawn too, so are we the same
m: players lose their stuff when they die, like us | i wonder if dying hurts players
q: do you think dying is the same for players? > probably / no, they don't care as much / worse, maybe / i hope it doesn't hurt them
q: have you ever seen a player die? > yes, to a creeper / once, in lava / no / yeah, and they came back angry
d: they come back and go straight for their stuff > straight for their stuff, same as us / of course
d: they don't seem scared after, like it's normal > normal to them, huh / weird
d: maybe dying means less to them > maybe / lucky them
d: a player laughed after dying once. i think > laughing after dying is wild / weird
o: maybe they've died so many times it's nothing now > maybe / i hope not
o: i'd like to know if they're scared at all > good question / i wonder too
o: if dying doesn't scare them, what does > good question / maybe nothing
o: maybe they're braver because they've seen more > seen more, yeah / maybe
c: players don't care about dying like we do | dying means nothing to players
y: yeah | true | looks like it
n: they care | nah, they rage | they hate losing stuff
""")

angle(PLAYERS, "players_meeting", "talking to a player", """
m: i'd like to really talk to a player one day | i said hi to a player once and they just looked at me
m: if i met a player, i don't know what i'd say | some players are kind, some are scary
q: if you could ask a player one thing, what would it be? > where do you go / are you scared / what's it like / why do you jump so much
q: do you trust players? > some / no / the nice ones / i don't know any
d: one gave me bread once. i still think about it > bread from a player, lucky / that's nice
d: another one hit me for no reason > rude / some of them are like that
d: they're like people. good ones and bad ones > good and bad, like anyone / true
d: they're just as curious about us, i bet > probably / maybe
o: maybe we're as strange to them as they are to us > probably / ha, probably
o: i'd want to be the one they remember fondly > you would be / that's a nice idea
o: if every player met someone kind here, maybe they'd be kinder > that's a nice idea / probably
o: they're just people from far away > from far away, yeah / probably
c: we should trust players until they give us a reason not to | give every player a chance
y: yeah | true | agreed
n: be careful | nah, trust has to be earned | some are bad
""")

# ======================================================================================================================
# Being watched, who decides, luck.
WATCHED = theme(
    "watched", ["philosophy", "wonder"], ["philosophy", "night", "dreams", "wonder"],
    react={"any": "ok that's creepy | i've felt that too | huh. i don't like that | that's a weird thought",
           "casual": "lol hi whoever's watching. ok that's creepy | bro don't say that at night",
           "hermit": "something always watches. let it. | the quiet has eyes.",
           "newbie": "now i feel watched lol | that's so scary",
           "helper": "if someone's watching, i hope they see kindness",
           "speedrunner": "if someone's watching, i hope they see a good run"},
    nudge="go on | you feel it right now, huh | keep going | say more",
    close={"any": "anyway. if someone's watching, hi | let's give them something nice to see | probably nothing | "
                  "weird thought, i know",
           "hermit": "be good. someone sees.", "casual": "ok i'm waving at the sky now lol", "newbie": "i'm gonna be extra nice now"},
    bclose="be good, just in case | you're not crazy | i feel it sometimes too | let's go inside",
    yes="right? | exactly | yeah", no="hm, maybe | we'll see | fair",
    sits={"night": "it's dark and i feel watched | something's out there tonight | the night feels like it has eyes",
          "anxious": "i keep feeling like something's watching me | i'm jumpy today",
          "thunder": "the storm feels like someone's angry | thunder makes me feel watched"})
JOKES[WATCHED] = V("if someone's watching me, they're watching me fall in holes all day lol | "
                   "hi to whoever's watching. sorry about my house")

angle(WATCHED, "watched_feeling", "the feeling of being watched", """
m: sometimes i feel like someone's watching us | i get this feeling like eyes on my back
m: in the quiet moments it feels like we're not alone | i turn around and nobody's there, every time
q: do you ever feel watched? > all the time / sometimes, at night / no, never / only by endermen
q: who do you think is watching, if anyone? > nobody / something in the sky / the world itself / whoever made all this
d: it's not scary exactly, just there > just there, yeah / i know what you mean
d: it feels strongest when i'm doing something kind > huh. interesting / maybe someone likes that
d: it's like the world is paying attention > creepy and nice / i've felt that too
d: in caves it's the worst > caves are the worst for it / same, every time
o: maybe if someone's watching, they're rooting for us > rooting for us, i like that / i hope so
o: i'd want them to see me at my best > your best, yeah / then let's be good today
o: maybe it's the world itself, and it likes us > a world that likes us, nice / i'd like that
o: i act like someone's watching, and it makes me better > that's a good way to use it / it shows
c: we're definitely being watched | something's always watching us
y: i feel it too | yeah | i think so
n: it's just nerves | nah, nobody's there | that's paranoia
""")

angle(WATCHED, "watched_who_decides", "who decides the weather", """
m: who decides when it rains | it feels like someone chooses the weather
m: the storm came right when i left my house. coincidence? | some days the world feels planned
q: do you think someone decides what happens here? > no, it's random / feels like it / the world does / i hope not
q: if someone was deciding, would you be angry or glad? > angry, about the creepers / glad, mostly / both / i'd just want to talk
d: the rain always starts when i'm far from home > classic / every single time
d: lucky finds feel like gifts sometimes > they do / i'll take the gifts
d: maybe it's all random and we just look for patterns > probably, yeah / that's what i think
d: sometimes it's too perfect to be random > i've felt that too / it really is sometimes
o: if someone decides, they gave us sunsets too > true, that's fair / then they're not all bad
o: i'd like to think whoever decides is kind > kind, i hope / me too
o: maybe nobody decides, and that's ok > that's ok, yeah / i can live with that
o: i'd thank them for the good days and complain about the creepers > both fair / same, especially the creepers
c: everything happens for a reason | nothing here is random
y: i believe that | yeah | true
n: it's all random | nah, luck | random, mostly
""")

angle(WATCHED, "watched_endermen", "the endermen's stare", """
m: the endermen stare at you like they know something | you look at an enderman and it looks back. too much
m: the endermen are quiet and tall and always watching | i wonder what the endermen are thinking
q: what do you think endermen are thinking? > about blocks / about us / nothing / they're lonely
q: why do you think endermen hate being looked at? > they're shy / it's rude / they have secrets / they're scared of us
d: they carry blocks around like they're building something > building something, maybe / i wonder what
d: they teleport away like they're embarrassed > aw / lol i never thought of that
d: they make a sound like a sad whisper > yeah, it's creepy / it really is sad
d: they hate water, like it hurts them > that's sad, actually / poor things
o: maybe they're trying to put the world back together > oh. that's sad / maybe
o: they're lonely and nobody looks at them nicely > lonely, maybe / that's sad
o: maybe they stare because they want someone to see them > to be seen, yeah / that's sad and sweet
o: i don't look them in the eye, out of respect > respect, nice / and to stay alive lol
c: endermen are the most mysterious mob | nothing's weirder than an enderman
y: true | yeah | agreed
n: the warden is | nah, the ghasts | the allays
""")

angle(WATCHED, "watched_luck", "luck and who hands it out", """
m: some days the world gives you diamonds, some days nothing | it feels like luck is handed out by someone
m: i got three diamonds from one ore once and felt chosen | why do some people find everything and others nothing
q: do you think luck is real? > yes / no, it's just chance / some people have it / i have none lol
q: what's the luckiest thing that ever happened to you? > diamonds in my first cave / a totem saving me / meeting you / surviving a fall
d: i found a mending book on my first fishing trip > mending first try, that's crazy / so lucky
d: the same cave gave me nothing twice > caves are moody / lol same
d: luck feels like the world smiling at you > the world smiling, nice / i like that
d: bad luck feels personal, even though it's not > bad luck feels personal, true / it really does
o: maybe luck is just noticing the good stuff > noticing, yeah / that's a good way to see it
o: the luckiest thing was never an item > never an item, yeah / same
o: if someone hands out luck, i hope they hand some to you > thanks / same to you
o: i'd trade all my lucky finds for my friends > same / that's sweet
c: you make your own luck | luck is just hard work
y: true | yeah | agreed
n: luck is luck | nah, some people are just lucky | hard work doesn't find diamonds
""")

# ======================================================================================================================
# Is this all a game? (Wonder only: the world's strange rules, hearts, xp, someone playing.)
GAME = theme(
    "game", ["philosophy", "wonder", "random"], ["philosophy", "jokes", "dreams", "wonder"],
    react={"any": "ok that's a weird one | huh. i've thought that too | that's the kind of thing that keeps me up | "
                  "you're getting strange on me",
           "casual": "lol what if we're all someone's dream. kidding. kind of | bro's questioning everything again. love it",
           "hermit": "rules are rules. why is for the sky. | the world has its ways.",
           "newbie": "i thought that was normal lol | i thought everywhere was like this",
           "speedrunner": "rules are rules. i just use them | i know every rule. i don't ask why",
           "builder": "the rules are why building works though"},
    nudge="go on | you're getting deep on me | keep going | say more",
    close={"any": "anyway. rules are rules | weird world, good world | whatever it is, it's ours | i'm not complaining, mostly",
           "hermit": "the world is what it is.", "casual": "ok my brain's melting lol",
           "newbie": "i'm gonna look at everything differently now"},
    bclose="weird world, huh | don't think too hard | it works, that's enough | whatever it is, i like it",
    yes="right? | exactly | see", no="hm, maybe | we'll see | fair",
    sits={"bored": "bored enough to question everything | nothing to do, so i'm thinking weird thoughts",
          "night": "late night thought, don't judge | the night makes me think strange stuff",
          "calm": "calm day, strange thought | random thought, bear with me"})
JOKES[GAME] = V("if this is all a game, i'd like to complain about creepers lol | if this is a game, i'm losing")

angle(GAME, "game_rules", "the world's strange rules", """
m: have you noticed how strange the rules here are | dirt floats in the air but sand falls
m: you can carry a whole mountain of stone in your pockets | a tree can float if you cut the bottom log
q: why do you think some blocks fall and others float? > nobody knows / sand is just heavy / the world decided / so we can build bridges
q: which rule of the world is the strangest to you? > floating trees / carrying so much stuff / water that never ends / sleeping skips the night
d: water from two buckets makes a pool that never runs out > endless water is the best rule though / so weird, so useful
d: i cut a tree and the top just stayed, floating > the floating trees bug me too / lol everyone's done that
d: we carry stacks of stone like it's nothing > heavy pockets, light steps / i never thought about it
d: the rules don't make sense but they never change > never changing is something / at least they're the same every day
o: maybe the rules are strange so we can do amazing things > strange and useful / we'd be stuck with no bridges
o: with normal rules we couldn't build half of what we do > we'd be stuck with no bridges / true
o: i like that the rules are the same for everyone > the same for everyone, yeah / that's fair
o: strange rules, but fair ones > fair is what matters / yeah
c: the world's rules make no sense | nothing here follows sense
y: true | none at all | yeah
n: they make sense once you know them | nah, they're just rules | they make sense to me
""")

angle(GAME, "game_hearts", "counting health in hearts", """
m: why do we count our health in hearts | i always know exactly how many hearts i have left
m: it's weird that we know how hungry we are, exactly | i can feel my hearts go down one by one
q: why do you think we know our health so exactly? > the world tells us / we just feel it / so we know when to run / i never thought about it
q: what would you do with ten more hearts? > fight a warden / nothing different / be braver / go somewhere scary
d: half a heart left and you feel every bit of it > half a heart is the scariest feeling / ugh, yes
d: eating brings them back slowly, a little at a time > healing slowly, yeah / food fixes everything
d: it's like the world wants us to know how close we are > that's kind, i guess / weird but useful
d: i count hearts like i count days > counting hearts, same / always
o: maybe knowing helps us be careful > careful, yeah / it does
o: what if we didn't know, and just felt bad > not knowing would be scarier / that'd be awful
o: hearts is a nice word for it. like life is love > aw / that's sweet
o: i wonder who decided it'd be hearts > someone soft, probably / someone nice
c: knowing exactly how hurt you are is a gift | it's good we can count our hearts
y: true | yeah | agreed
n: it makes you scared | nah, i'd rather not know | not knowing would be easier
""")

angle(GAME, "game_xp", "where xp comes from", """
m: where does xp even come from | little green balls fly at you when you mine
m: you get xp from fighting and smelting and it's weird | i lose xp when i die. where does it go
q: what do you think xp actually is? > knowing stuff / a bit of the world's magic / leftover life / nobody knows
q: is it weird that we lose xp when we die? > very / no, it makes sense / it's the worst part / i never have much anyway
d: it floats out of a furnace when you take the iron out > furnace xp is the best xp / free xp
d: it's like the world gives you a little gift for working > a gift for working, i like that / that's a nice way to see it
d: when you die it spills out like your stuff > that's sad / and some of it just goes
d: it hums when you pick it up > it does hum, yeah / i love that sound
o: maybe xp is everything you learned, and you leave some behind > that's deep / then i've left a lot behind
o: it's like the world remembering what you did > the world remembering, yeah / i like that
o: we spend it on enchanting. turning what we learned into power > that's a good way to put it / huh. yeah
o: i wonder if the world keeps the xp we lose > i hope it does / maybe that's what the world is made of
c: xp is the most valuable thing you have | xp matters more than diamonds
y: true | yeah | for enchanting, yes
n: diamonds matter more | nah, xp comes back | stuff matters more
""")

angle(GAME, "game_someone_playing", "what if someone's playing us", """
m: what if someone's playing all of this, like a game | sometimes it feels like i'm being moved around
m: what if everything we do is someone else's story | some days i do stuff and don't know why
q: if all this was a game, would it change anything? > nope / everything / i'd want to meet the player / i'd still want my house
q: do you ever feel like you're not deciding things? > sometimes / never / when i'm tired / all the time, honestly
d: if it's a game, i hope they're having fun > i hope they're having fun too / they'd better be
d: i still feel like me, whatever it is > feeling like you is what counts / same
d: even if someone's watching, my friends feel real > friends are real, yeah / i feel real to me lol
d: the bread tastes real, that's enough for me > bread tasting real is a good argument / lol fair
o: maybe it doesn't matter, as long as we're kind > kind, yeah / agreed
o: if someone's playing, i hope they're proud of us > proud, i hope / they should be
o: whatever this is, i like it here > me too / same
o: real or not, this talk is real to me > real to me too / me too
c: it doesn't matter if this is all a game | real or not, it doesn't change anything
y: true | yeah | agreed
n: it'd matter to me | nah, i'd want to know | it changes everything
""")

# ======================================================================================================================
# Home.
HOME = theme(
    "home", ["home", "philosophy"], ["home", "building", "feelings", "philosophy"],
    react={"any": "home's a big word | i think about that too | that's sweet | huh, good point",
           "homebody": "home is everything. i barely leave mine | i love my home so much",
           "nomad": "home is wherever i stop. for now | i don't really have one. i'm fine",
           "casual": "lol home is where my bed is. but yeah | bro's getting sentimental. fair",
           "hermit": "home is where nobody bothers you. | a roof and quiet.",
           "newbie": "i'm still building my first home! | home is the best word",
           "builder": "home is a build you live in"},
    nudge="go on | you miss somewhere, huh | keep going | tell me more",
    close={"any": "anyway. i'm heading home soon | home is good | i'm glad i've got somewhere | home sweet home",
           "hermit": "go home. rest.", "casual": "ok now i want to go home lol", "newbie": "i'm gonna make my home cozier"},
    bclose="you're welcome at mine | home's where you make it | go home and rest | i'll walk you home",
    yes="right? | exactly | yeah", no="hm, maybe | each their own | fair",
    sits={"house": "i love coming back to my house | my house finally feels like home | been sitting in my house thinking",
          "nohome": "i don't have a home, so i think about it a lot | still no home. it's on my mind",
          "evening": "time to head home | the sun's going down. home time"})
JOKES[HOME] = V("home is wherever my chests are, which is everywhere lol | my house is mostly chests and one bed")

angle(HOME, "home_what_is", "what home is", """
m: i've been thinking about what makes a place home | a house isn't home until something happens there
m: i had a house that never felt like home | home is more than walls, i think
q: what makes a place home for you? > my bed / my chests / my friends nearby / a view i love
q: is home a place or a feeling? > a feeling / a place / both / people
d: my first real home was a hole with a door > a hole with a door counts / everyone starts there
d: my house became home the first night i felt safe there > feeling safe is the start / that's it, yeah
d: my house didn't feel like home till i put a flower in it > a flower made it home, love that / little things
d: home is where i don't have to be careful > not having to be careful, yeah / exactly
o: maybe home is where your stories are > stories, yeah / i like that
o: home is where someone notices you came back > someone noticing, yeah / that's the best feeling
o: a place gets to be home slowly, like a friend > slowly, like a friend / true
o: i've felt at home in other people's houses > that's the best feeling / you're always welcome at mine
c: home is people, not places | your home is wherever your friends are
y: true | yeah | agreed
n: home is a place | nah, my house is my home | i need walls
""")

angle(HOME, "home_coming_back", "coming home after a trip", """
m: coming home after a long trip is the best feeling | the first sight of my house after days away
m: i walked for days and then saw my torches on the hill | home looks different after you've been away
q: what's the first thing you do when you get home? > put stuff in chests / sleep / eat / just stand there
q: do you miss home when you're away? > always / not really / only at night / i miss my bed
d: the torches on my house looked like a lighthouse > torches like a lighthouse, nice / best sight
d: everything was exactly where i left it > that's comforting / home waits for you
d: my bed after a long trip is the best bed in the world > best bed in the world, yes / so true
d: the house smelled like smoke and bread > perfect / that's home
o: maybe we travel just so coming home feels good > maybe, yeah / that's half of it
o: home is the place that waits for you > the place that waits, yeah / i like that
o: home feels small after a big trip, but in a good way > small in a good way / cozy small
o: i'm always glad i went, and always glad i came back > both, yeah / best of both
c: coming home is better than going out | the best part of a trip is getting home
y: true | yeah | agreed
n: the trip is the best part | nah, adventure | going out is better
""")

angle(HOME, "home_leaving_old", "leaving an old home", """
m: i moved out of my old house and felt weird | i still think about my first house
m: leaving a home is harder than building one | my old house is still standing, empty now
q: have you ever left a home behind? > yeah, a few / once, it was hard / no, never / i don't stay anywhere long
q: do you ever go back to your old places? > sometimes / never / once, it was sad / i keep meaning to
d: i took the bed and left everything else > taking the bed, smart / that's the important part
d: my old house looked so small when i went back > so small when you go back, yeah / they always do
d: i left a sign that said this was home > a sign is a nice goodbye / that's sweet
d: the garden i planted at my old place is still growing > aw / that's nice to know
o: maybe every old home is a bit of who we were > a bit of who we were, yeah / i like that
o: i wonder if someone lives there now > i hope someone nice does / maybe
o: i'd like to go back one day, just to look > let's go together / i'd go with you
o: leaving is part of growing, i think > growing, yeah / i think so too
c: you should never leave a home you love | once you find home, stay
y: true | yeah | agreed
n: sometimes you have to | nah, move on | homes change
""")

angle(HOME, "home_without", "living without a home", """
m: some people never settle and that's their life | living without a home sounds free and sad
q: could you live without a home? > no way / for a while / yes, easily / i do, kind of
q: what's the hardest part of having no home? > the nights / no chests / nobody knowing where you are / nothing
d: every night you have to find somewhere safe again > that sounds exhausting / one night homes, yeah
d: you carry everything, all the time > that sounds heavy / i'd hate that
d: you meet more people when you don't have walls > that's a plus / huh, never thought of that
d: a hole in a hill can be a home for one night > one night homes, yeah / for one night, sure
o: maybe a home is just where you decide to stop > i like that / maybe
o: i'd miss having somewhere to come back to > me too / that's the hard part
o: some people carry home with them > carry it, yeah / like a turtle
o: i'd want a home, but a small one > small is good / me too
c: everyone needs a home | nobody should live without a home
y: true | yeah | agreed
n: the road is home | nah, some don't | homes are cages
""")

# ======================================================================================================================
# Kindness.
KIND = theme(
    "kind", ["help", "philosophy", "friends"], ["help", "friends", "feelings", "philosophy"],
    react={"any": "that's a good thing to think about | i like that | kindness matters | huh, yeah",
           "helper": "helping is my favourite thing | kindness is the whole point",
           "bandit": "kindness is just weakness with a smile. kidding. mostly",
           "hoarder": "i'm kind. i just don't share chests",
           "casual": "lol i'm nice when i'm fed. but yeah | bro's being wholesome, i'm here for it",
           "hermit": "kindness costs little. | small kindness, long memory.",
           "newbie": "people have been so nice to me here | i want to be nice like that"},
    nudge="go on | that's a nice thought | keep going | say more",
    close={"any": "anyway. be kind, i guess | i'm gonna help someone today | kindness is good | it's worth it",
           "hermit": "be kind. quietly.", "casual": "ok i'm gonna go be nice to a villager lol",
           "newbie": "i'm gonna help someone tomorrow"},
    bclose="you're a good one | kindness spreads | i'll pass it on | thanks for being kind",
    yes="right? | exactly | yeah", no="hm, ok | we see it different | fair",
    sits={"friends": "you're always kind to me, you know | i've been thinking about kindness",
          "spare": "i've got extra food, and it got me thinking | i had too much bread, so i gave some away",
          "town": "someone in town helped me today | the town's been kind lately"})
JOKES[KIND] = V("i gave someone bread and they looked at me like i was a creeper lol | i'm so nice i let the chickens win")

angle(KIND, "kind_why_help", "why help others", """
m: why do we help people we don't even know | i helped someone today and i don't know why it felt so good
m: helping someone costs a bit, but it feels like it gives more | i don't get anything for helping, but i keep doing it
q: why do you help people? > it feels right / someone helped me once / so they help back / i don't know, i just do
q: what's the best help anyone gave you? > food when i was starving / a torch in a dark cave / showing me the way home / a bed for the night
d: someone gave me bread on my first night. i never forgot > you never forget that / that's a good memory
d: helping makes the world a little less scary > less scary, yeah / it does
d: helping is like lighting a torch in someone else's dark > that's nice / i like that
d: when you help, you're not alone either > true / i never thought of that
o: maybe kindness is how the world gets better, one bit at a time > one bit at a time / i think so
o: if everyone helped one person, nobody would be alone > nobody alone, yeah / that's the dream
o: i help because someone once helped me > passing it on / that's how it works
o: maybe that's the only real treasure > maybe it is / better than diamonds
c: you should help people even if they can't pay you back | helping is its own reward
y: true | yeah | agreed
n: help should go both ways | nah, they should pay back | only if they'd help me
""")

angle(KIND, "kind_giving_away", "giving things away", """
m: i gave away my diamonds once and felt lighter | giving stuff away is weirdly the best feeling
m: i gave a newbie my old sword and they lit up | some days i want to give everything away
q: what's the biggest thing you've ever given away? > diamonds / my old house / a whole chest of food / a pet
q: is it hard for you to give stuff away? > very / no, it's easy / depends on the stuff / only diamonds lol
d: the newbie's face when they got my sword was worth more than the sword > the face is the payment / i bet
d: i didn't need my old sword, and the newbie did > they needed it, good call / perfect
d: stuff in a chest helps nobody > true / tell that to my chests lol
d: giving is like planting a sapling for someone > that's nice / i like that
o: maybe what we give away is what we really keep > that's deep / that's true
o: i want to give more and keep less > give more, yeah / me too
o: the stuff i gave away, i remember. the stuff i kept, i don't > that's true / huh. same
o: it's easier to give when you remember being new > being new helps you remember / true
c: it's better to give than to get | giving feels better than getting
y: true | yeah | agreed
n: getting is nice too | nah, i like getting stuff | both are fine
""")

angle(KIND, "kind_stranger", "a stranger's kindness", """
m: a stranger helped me once and i never saw them again | someone pulled me out of a ravine and just walked off
m: the kindest thing anyone did for me was from someone i didn't know | i think about that stranger a lot
q: has a stranger ever helped you? > yes, once / a few times / no, never / yeah, and i still think about it
q: would you help a stranger at night, in danger? > yes / probably / depends on the danger / i'd try
d: the stranger who helped me didn't even say their name > no name, just kindness / that's the best kind
d: a stranger once just gave me a torch and pointed the way > simple and kind / that's all it takes
d: the one who pulled me out of a ravine just went on with their day > legend / that's a good person
d: i never got to thank them > that's sad / maybe they know
o: maybe the way to thank them is to help someone else > help someone else, yeah / that's the way
o: i hope someone was kind to them too > i hope so / i bet someone was
o: i try to be that stranger for someone else now > be that stranger / that's good
o: kindness from strangers is the purest kind > the purest kind / true
c: you should always help strangers | strangers deserve help as much as friends
y: true | yeah | agreed
n: be careful with strangers | not always | friends first
""")

angle(KIND, "kind_forgiving", "forgiving someone", """
m: someone took from my chest and i'm trying to forgive them | forgiving is harder than fighting
m: i've been holding a grudge for days and it's heavy | someone said sorry and i didn't know what to do
q: is it hard for you to forgive? > very / not really / depends what they did / i forgive but don't forget
q: have you ever needed someone's forgiveness? > yeah / once / no / probably and didn't know
d: holding it is like carrying a stack of cobble everywhere > heavy / so put it down
d: the one who took from me was hungry, i think > hungry changes things / that's different then
d: i keep imagining what i'd say to the one who did it > that's draining / i do that too
d: forgiving doesn't mean they get to open my chests > forgive but lock the chest, smart / lol fair
o: maybe forgiving is for me, not them > for you, yeah / i think so
o: i'd want someone to forgive me if i messed up > everyone messes up / same
o: i think i'll talk to the one who wronged me tomorrow > good luck / that's brave
o: forgiving is putting down the cobble > put it down / that's it
c: everyone deserves a second chance | you should forgive people who say sorry
y: true | yeah | agreed
n: not everyone | some things you don't forgive | once is enough
""")

# ======================================================================================================================
# Change, regret and starting over.
CHANGE = theme(
    "change", ["memories", "feelings", "philosophy"], ["memories", "feelings", "philosophy", "advice"],
    react={"any": "i think about that too | that's a lot | huh. yeah | that's honest",
           "casual": "lol my whole life is one big mistake. ok but fr | bro's reflecting. respect",
           "hermit": "the past stays. we walk on. | mistakes are maps.",
           "newbie": "i make mistakes every day lol | i'm still learning everything",
           "helper": "you're doing better than you think",
           "speedrunner": "every run has mistakes. you reset and go"},
    nudge="go on | sounds like something happened | keep going | there's more, huh",
    close={"any": "anyway. tomorrow's a new day | we learn, i guess | no use dwelling | next time, better",
           "hermit": "walk on.", "casual": "ok i'll make new mistakes tomorrow lol", "newbie": "i'm gonna do better"},
    bclose="you're doing fine | we all have those | tomorrow's yours | be easy on yourself",
    yes="right? | exactly | yeah", no="hm, maybe | we see it different | fair",
    sits={"evening": "end of the day. thinking about what i'd do differently | another day done. some regrets",
          "frustrated": "nothing's going right today | i keep messing things up",
          "died": "dying again made me think about my choices | i {a.death} and i keep thinking i should've known"})
JOKES[CHANGE] = V("my biggest regret is digging straight down. and the next six times lol | "
                  "i regret nothing. except the lava. and that creeper")

angle(CHANGE, "change_regret", "the biggest regret", """
m: i've been thinking about my biggest regret | there's one thing i'd do differently if i could
m: i made a choice a while back that still bugs me | some regrets stay with you, even little ones
q: what's your biggest regret here? > not saying bye to someone / digging straight down / trusting the wrong person / wasting my diamonds
q: if you could change one thing you did, would you? > yes, that one thing / no, it made me who i am / maybe / i'd change a few
d: i said something mean to someone and they left > saying mean things stays with you / that's a heavy one
d: i built too close to lava and lost everything > lava builds, never again / ouch
d: i didn't help someone when i could have > that's a heavy one / you'll get another chance
d: i wasted my first diamonds on a hoe. a hoe > lol oof / a diamond hoe. wow
o: maybe regret is how we learn what we care about > what we care about, yeah / that's true
o: i'd like to find the one i hurt and say sorry > do it, if you can / it's not too late
o: the trick is to carry it lightly > lightly, yeah / easier said than done
o: i'm not that person anymore > you're not / i can tell
c: you shouldn't regret anything | regrets are a waste of time
y: true | yeah | agreed
n: regret teaches you | nah, regret is useful | some things you should regret
""")

angle(CHANGE, "change_start_over", "starting over", """
m: i lost everything once and started over from nothing | starting over is scary and kind of freeing
m: i had to rebuild from a single wooden pickaxe | sometimes i think about starting fresh somewhere new
q: have you ever had to start over? > yes / a few times / no, thankfully / i'm starting over right now
q: would you start over on purpose? > never / maybe, somewhere new / yes, it's fun / only with friends
d: the second time was faster. you know what to do > faster the second time, yeah / that's the one good part
d: starting over, you find out what you really need > what you really need, yeah / and it's not much
d: the first night with nothing was scary again > i bet / like being new again
d: i was sad, then oddly happy, building again > that's a good sign / building fixes a lot
o: maybe starting over is just day one with more wisdom > day one with wisdom, i like that / that's a good way to see it
o: everything i lost, i don't miss most of it > that says a lot / same, honestly
o: starting over showed me i could do it > you can, yeah / that's worth a lot
o: we start over more often than we notice > every morning, kind of / true
c: starting over is good for you | everyone should start over at least once
y: true | yeah | agreed
n: never again | nah, it's the worst | no thanks
""")

angle(CHANGE, "change_who_you_were", "how you've changed", """
m: i'm not the same as when i arrived | i look back at my first days and barely know myself
m: i've changed so much and i didn't notice it happening | the old me would be scared of the new me lol
q: how have you changed since you arrived? > braver / quieter / kinder / i haven't, honestly
q: do you like who you've become? > yes / mostly / i'm working on it / not really
d: i used to panic at every sound > panic to calm, that's growth / i remember that lol
d: now i walk into caves without a second thought > careful though lol / that's a big change
d: i used to keep everything. now i share > that's a good change / i noticed
d: i talk to more people now > i'm glad you do / it shows
o: change happens so slow you don't see it > slow, yeah / then one day you look back
o: i like who i am now better > me too / good
o: i hope i keep changing, in good ways > you will / me too
o: maybe meeting people is what changed me most > people change us, yeah / that's how it works
c: people don't really change | you're the same person you always were
y: true | yeah | deep down, yes
n: people change all the time | nah, i've changed | everyone grows
""")

angle(CHANGE, "change_mistakes", "mistakes that teach", """
m: my best lessons came from my worst mistakes | i learned about lava the hard way
m: every mistake taught me something, even the dumb ones | i don't mind mistakes as long as i learn
q: what's the best lesson a mistake taught you? > don't dig straight down / always carry food / torches everywhere / trust slowly
q: are you scared of making mistakes? > a bit / no, they teach you / yeah, very / only big ones
d: i fell in lava once. now i look before i dig > look before you dig, yeah / good lesson
d: i got lost once. now i carry a map > a map, smart / good lesson
d: i trusted the wrong person once. now i trust slowly > trust slowly, yeah / that's a hard one
d: i starved once. now i always carry bread > always bread, wise / same
o: maybe mistakes are just lessons that hurt > lessons that hurt, yeah / true
o: i'm glad i made them, honestly > glad, yeah / me too
o: the ones who never mess up never learn > true / that's why i mess up so much lol
o: i want to teach newbies so they don't learn the hard way > that's kind / they'll still learn some the hard way
c: you learn more from failing than winning | mistakes teach more than wins
y: true | yeah | agreed
n: wins teach too | nah, failing just hurts | you learn from both
""")

# ======================================================================================================================
# Villagers.
VILLAGERS = theme(
    "villagers", ["villagers", "philosophy"], ["villagers", "town", "trade", "philosophy"],
    react={"any": "villagers are so strange | i've wondered that about villagers | huh. they're a mystery | good point",
           "casual": "lol villagers just go hmm. ok but yeah | bro's thinking about villagers. respect",
           "hermit": "villagers live small. maybe they're right. | they ring the bell. they hide. wise.",
           "newbie": "i didn't know villagers thought about stuff | i love villagers, they're so funny",
           "helper": "i always look out for the villagers",
           "bandit": "villagers are easy marks. kidding. kinda"},
    nudge="go on | you've been watching villagers again | keep going | say more",
    close={"any": "anyway. be nice to villagers | they're doing their best | hmm, as they'd say | villagers are alright",
           "hermit": "leave them be.", "casual": "hmm. that's all i got lol", "newbie": "i'm gonna go visit a villager"},
    bclose="say hi to the villagers for me | they'd like you | go trade with one | look after the villagers",
    yes="right? | exactly | yeah", no="hm, maybe | we'll see | fair",
    sits={"town": "the villagers in town are busy today | watching the villagers work",
          "traded": "traded with {a.traded_with} today and got thinking | after trading i got thinking about villagers",
          "day": "villagers all working away today | busy day for the villagers"})
JOKES[VILLAGERS] = V("a villager went hmm at me and i felt judged lol | villagers are the only ones who get the value of an emerald")

angle(VILLAGERS, "villagers_think", "whether villagers think", """
m: i wonder what villagers think about | villagers go hmm like they're thinking hard about something
m: villagers work, sleep, gossip. kind of like us | i watched a villager stare at a wall for ages
q: do you think villagers think like we do? > a bit / no, they just work / more than we know / they think about emeralds
q: what do you think villagers dream about? > emeralds / crops / nothing / a safe night
d: they go to bed when it gets dark, every night > early bedtime, sweet / smart, honestly
d: they gossip about you, did you know that > they do / i hope it's nice stuff
d: they look so worried when it rains > aw / they really do
d: they make golems when they're scared > smart / that's clever
o: maybe villagers are just simpler versions of us > simpler, maybe / be nice then
o: they never leave their village. i wonder if they want to > i wonder too / maybe they're happy there
o: they look after each other in their way > in their way, yeah / they do
o: if they gossip about us, i hope it's good stuff > be nice then / i'm sure it is about you
c: villagers are smarter than they look | don't underestimate villagers
y: true | yeah | agreed
n: they're not | nah, they walk into holes | they just go hmm
""")

angle(VILLAGERS, "villagers_staying", "villagers never leaving", """
m: villagers never leave their village | villagers spend their whole lives in one place
m: i wonder if villagers ever want to see the world | a villager born in a village stays in that village
q: would you want to live like a villager, in one place forever? > no way / sometimes, yeah / it sounds peaceful / only if it's a nice village
q: do you think villagers are happy staying? > yeah / they don't know anything else / i hope so / no, they look bored
d: they have their job, their bed, their friends. that's a whole life > a whole life, yeah / that's enough for anyone
d: i've never seen one walk past the edge of town > never past the edge, huh / me neither
d: maybe they know something we don't about being happy > they might know something / maybe
d: they seem content, mostly > content, maybe / they do
o: we wander so much and they're fine staying > they're fine, yeah / maybe they're right
o: maybe a small life is a full life > small and full / i think so
o: i'd like to try staying in one place, like them > try it / you'd last a week lol
o: wandering and staying are both ways to live > both, yeah / true
c: staying in one place is better than wandering | villagers have the right idea
y: true | yeah | agreed
n: wandering is better | nah, i'd go crazy | the world's too big
""")

angle(VILLAGERS, "villagers_golems", "iron golems", """
m: iron golems protect villagers like they love them | an iron golem gave a villager a flower once
m: the golems never sleep, they just guard | i saw a golem fight a zombie for a villager it didn't know
q: do you think iron golems feel anything? > yeah, they love the villagers / no, they're iron / a little / the flower says yes
q: why do you think golems protect villagers? > they're made to / they love them / they're lonely / it's their job
d: golems handing villagers flowers gets me every time > me too / so sweet
d: they walk around all night, never resting > never resting, wow / so loyal
d: if you hit a villager the golem never forgives you > loyal / learned that the hard way
d: they're made of iron but they're the softest mob > softest mob, true / big softie
o: maybe being made to protect is still love > still love, yeah / i think so
o: i'd like to be someone's golem > you are, kind of / i'd like that too
o: i wonder if golems get lonely, guarding all night > maybe / they have the villagers, at least
o: the golem doesn't ask for anything back > nothing back, pure / true
c: iron golems are the best mob | golems are better than any other mob
y: yes | true | agreed
n: wolves | nah, cats | allays
""")

angle(VILLAGERS, "villagers_nitwit", "the nitwit with no job", """
m: the nitwit in green never gets a job | everyone has a job except the nitwit, and he's fine
m: the nitwit just wanders and sleeps. kind of goals | i feel bad for the nitwit sometimes
q: do you think the nitwit is sad, not having a job? > no, he's free / a bit / he doesn't care / he's the happiest one
q: would you be happy without a job? > yes / for a while / no, i need something to do / i'd go crazy
d: the nitwit just walks around and looks at stuff > relatable / lol same
d: the others work and the nitwit just exists > just existing, yeah / living the dream
d: maybe he's the wisest one > wisest, maybe / lol maybe
d: nobody asks anything of him > nothing asked of him, free / sounds nice
o: maybe a life doesn't need a job to be worth something > worth something, yeah / true
o: i'm a bit of a nitwit myself, honestly > lol same / me too
o: the nitwit still belongs, even without a job > belonging, yeah / he does
o: i like him the most, weirdly > lol same / me too
c: everyone needs a job to be happy | having nothing to do makes you sad
y: true | yeah | agreed
n: the nitwit's fine | nah, free time is great | jobs aren't everything
""")

# ======================================================================================================================
# Weather.
WEATHER = theme(
    "weather", ["weather", "philosophy"], ["weather", "feelings", "sky", "philosophy"],
    react={"any": "the weather does stuff to me too | huh. i love the rain | that's a good one | yeah, i feel that",
           "casual": "lol rain means nap time. ok but yeah | bro's a weather poet now",
           "hermit": "rain comes. rain goes. | storms clean the air.",
           "newbie": "the weather here is so wild | i love the rain! mostly",
           "helper": "rainy days are good for talking",
           "builder": "rain on a good roof is the best sound",
           "speedrunner": "rain slows nothing. keep moving"},
    nudge="go on | the rain's got you thinking | keep going | say more",
    close={"any": "anyway. i like the weather today | storms pass | let's stay dry | rain's nice, honestly",
           "hermit": "let it rain.", "casual": "ok i'm going inside lol", "newbie": "i'm gonna watch the rain"},
    bclose="stay dry | it'll clear up | enjoy the rain | find a roof",
    yes="right? | exactly | yeah", no="hm | we'll see | fair",
    sits={"rain": "listen to the rain | it's raining again | love this rain",
          "thunder": "the thunder's so loud | big storm today | hear that thunder?",
          "calm": "calm weather, calm me | nice weather for thinking"})
JOKES[WEATHER] = V("rain is just the sky crying about my house lol | i built a roof just so i could hear the rain on it")

angle(WEATHER, "weather_rain_mood", "rain and moods", """
m: rain always makes me thoughtful | there's something about the rain that slows everything down
m: rainy days make me want to stay inside and think | the rain makes the world quieter somehow
q: does the rain make you sad or happy? > happy, it's cozy / a bit sad / both / neither, it's just rain
q: what do you do on rainy days? > stay inside / fish, it's better in rain / build / sleep
d: rain on the roof is the best sound there is > rain on a roof, yes / best sleep sound
d: everything smells fresh after > fresh after, yeah / love that
d: the rain makes the town go quiet > quiet town, nice / everyone goes inside
d: it's like the world takes a breath > a breath, i like that / yeah
o: maybe the rain is the world resting > the world resting, yeah / i like that
o: i'm calmer when it rains > me too / same
o: rain gives you permission to slow down > permission, yeah / exactly
o: rain is my favourite weather, honestly > me too / mine too
c: rain is the best weather | rainy days are the best days
y: yes | true | agreed
n: sunny is better | nah, rain's annoying | clear skies win
""")

angle(WEATHER, "weather_thunder", "thunder and lightning", """
m: thunder makes me feel tiny | lightning hit right next to me and i still feel it
m: storms make the mobs come out in the day | when it thunders it feels like the world is angry
q: are you scared of thunder? > yes / a little / no, i love it / only when it's close
q: why do you think storms happen? > the sky gets angry / it just does / to water everything / to scare us lol
d: lightning turned a pig into something else once > the pig thing, yeah, creepy / poor pig
d: the sky flashes and for a second it's day > day for a second, wow / it's so bright
d: storms make it dark enough for zombies at noon > zombies at noon, the worst / ugh
d: thunder shakes my chest > yeah / same
o: maybe storms remind us how small we are > small, yeah / they do
o: i like storms when i'm inside, hate them when i'm out > inside is the trick / same
o: the world after a storm looks so clean > clean, yeah / so fresh
o: every storm ends, that's the thing > they always end / true
c: storms are the scariest weather | nothing's worse than a thunderstorm
y: true | yeah | agreed
n: storms are cool | nah, they're exciting | snow's worse
""")

angle(WEATHER, "weather_snow", "snow and cold", """
m: when it rains up high it turns to snow | i love the snowy places, everything's so quiet
m: snow makes the world look clean and new | the cold places feel lonely and beautiful
q: do you like the snow? > love it / it's too cold / it's pretty to look at / only from inside
q: would you live somewhere snowy? > yes / no way / maybe a cabin / only with a big fire
d: the snow muffles everything, even footsteps > peaceful / i love that
d: the foxes in the snow are white > white foxes, cute / so cute
d: your breath feels cold just looking at it > lol yeah / brr
d: snow on a roof makes a house look cozy > cozy snow roofs, yeah / yes
o: maybe quiet places are where you hear yourself best > hear yourself, yeah / i think so
o: the cold makes the warm feel better > warm after cold, the best / true
o: i'd like a little cabin in the snow with a fire > let's build one / that sounds perfect
o: snow covers everything, like a fresh start > a fresh start, yeah / nice
c: snowy places are the most beautiful | snow beats every other biome
y: true | yeah | agreed
n: too cold | nah, jungles | flower fields win
""")

angle(WEATHER, "weather_storms_end", "storms always ending", """
m: every storm ends eventually | the rain stopped and the sun came out like nothing happened
m: no matter how bad the storm, it clears | after the storm, everything looked new
q: do you think the bad times end like storms do? > always / most of the time / i hope so / they come back too
q: what helps you wait out a storm? > a good roof / a friend / sleep / a furnace and food
d: the sun came out and everything dripped and sparkled > nice / that's the best part
d: the mobs burned in the first light > satisfying / good riddance
d: i waited the whole storm in my hut, then it was over > waited it out, yeah / smart
d: the clouds broke and the sky was huge > a huge sky after, yeah / love that
o: maybe that's the lesson. wait, it'll clear > it'll clear, yeah / true
o: hard days are like storms, they pass > they pass / true
o: knowing it ends makes it easier to wait > easier, yeah / true
o: i'd rather wait out a storm with someone > me too / same
c: bad times always end | every storm passes
y: true | yeah | always
n: some don't | not always | some storms last
""")

# ======================================================================================================================
# Music and silence.
MUSIC = theme(
    "music", ["music", "feelings"], ["music", "feelings", "random", "philosophy"],
    react={"any": "i love that you noticed that | music does something to me too | huh, yeah | that's a nice thought",
           "casual": "lol i hum while i mine. ok but yeah | bro's getting musical. i'm into it",
           "hermit": "the quiet is music too. | listen more.",
           "newbie": "i love music, even the weird ones | i never noticed that before",
           "helper": "music makes everything better",
           "builder": "i put a jukebox in every build",
           "miner": "the best music is a pickaxe on stone"},
    nudge="go on | you've got a song in your head, huh | keep going | say more",
    close={"any": "anyway. i'm gonna listen more | music makes the world softer | listen for it next time | i love that",
           "hermit": "listen.", "casual": "ok now i've got a song stuck in my head lol",
           "newbie": "i'm gonna listen really hard tonight"},
    bclose="listen with me sometime | hum one for me | i'll keep my ears open | music's good",
    yes="right? | exactly | yeah", no="hm, maybe | each their own | fair",
    sits={"calm": "it's so peaceful, i swear i can hear music | calm day, music in my head",
          "night": "quiet night. you hear that? | the night's so quiet i can hear my thoughts",
          "bored": "bored, so i've been humming | nothing to do but listen to the wind"})
JOKES[MUSIC] = V("my jukebox has one disc and i've heard it a thousand times lol | i hum while i mine and the zombies hate it")

angle(MUSIC, "music_from_nowhere", "music from nowhere", """
m: sometimes i hear soft music out of nowhere | sometimes when i'm building, music just starts playing
m: there's a soft piano sound sometimes, from nowhere | i heard music in a cave once and nobody was there
q: where do you think that soft music comes from? > the world itself / the sky / someone far away / i've never heard it
q: when do you hear that soft music most? > when i'm calm / building / at sunrise / when i'm alone
d: it comes and goes, never when you want it > never when you want it, so true / that's the magic
d: it makes whatever you're doing feel important > makes it feel important, yeah / true
d: it always seems to know how i feel > it knows how you feel, yeah / same
d: i stopped mining just to listen > stopping to listen, good / me too
o: maybe the world plays music when it's happy > a happy world, yeah / i like that
o: the music is the world saying it's ok > it's ok, nice / i like that
o: when it stops, everything feels quieter than before > quieter after, yeah / so true
o: i'd like to know who's playing > me too / maybe nobody
c: the music is the best thing in the world | nothing beats that music when it comes
y: yes | true | agreed
n: it's nice, but not the best | nah, quiet's better | sunsets are better
""")

angle(MUSIC, "music_discs", "the music discs", """
m: who made the music discs | i found a music disc in a dungeon. why was it there
m: creepers drop discs if a skeleton gets them. that's so weird | every disc sounds like a different memory
q: who do you think made the music discs? > someone long ago / the world / someone like us / nobody knows
d: one of them sounds like a happy day > a happy day, yeah / i know that one
d: some of them sound sad and broken > sad and broken, some of them are / those ones are creepy
d: there's one that sounds like someone walking somewhere > someone walking, i hear it too / yeah
d: a disc in a dungeon is like a note left for us > a note left for us, yeah / i like that
o: maybe someone made them so we wouldn't feel alone > not alone, yeah / i like that
o: music outlasts whoever made it > it does / true
o: i'd like to make a song one day > you should / i'd listen
o: the discs are other people's feelings we get to keep > keeping feelings, yeah / that's beautiful
c: music is the most important thing someone can make | songs matter more than builds
y: true | yeah | agreed
n: builds last longer | nah, houses | both matter
""")

angle(MUSIC, "music_silence", "silence", """
m: i like it when everything's quiet | silence can be the best sound
m: in the snowy places it's so quiet you can hear your heart | some silence is peaceful, some is scary
q: do you like the quiet? > love it / it scares me / sometimes / only with someone
q: what's the quietest place you've been? > the snow / deep in a cave / my house at night / the ocean floor
d: quiet in a meadow is peaceful > meadow quiet is the best / yeah
d: quiet in a cave means something's coming > something's coming, creepy / true
d: i hear my own thoughts more in the quiet > own thoughts, loud, yeah / same
d: quiet with a friend is the best quiet > quiet with a friend, yeah / agreed
o: maybe silence is just the world listening > the world listening, nice / i like that
o: sitting quiet with someone means you trust them > trust, yeah / true
o: i'm trying to like the quiet more > good / it grows on you
o: the world's never really silent, there's always wind > always wind, true / yeah
c: quiet is better than noise | silence beats any music
y: true | yeah | agreed
n: music is better | nah, quiet is lonely | noise is life
""")

angle(MUSIC, "music_note_blocks", "making music", """
m: i made a song with note blocks and it was terrible | making music is harder than mining
m: i hit a note block a hundred times to get one tune | i want to make a song for my town
q: have you ever made music? > yeah, badly / no / a little / i hum, does that count
q: what would your song sound like? > happy / slow and sad / like rain / like the night
d: every block changes the sound. wood, stone, glass > every block different, cool / i didn't know that
d: it took me all day for ten seconds of song > all day for ten seconds, worth it / i bet it was
d: when it finally worked i played it ten times > ten times, fair / i would too
d: i played my song and the villagers didn't even look up > villagers have no taste lol / rude
o: maybe making something nobody needs is the point > nobody needs it, everyone wants it / yeah
o: music is just feelings you can hear > feelings you can hear, yeah / beautiful
o: i'd play it at a festival one day > do it / i'd be there
o: even a bad song is something new in the world > something new, yeah / true
c: everyone should try making music | making music is worth it even if it's bad
y: yes | true | agreed
n: leave it to people who can | nah, too hard | bad music is bad
""")

# ======================================================================================================================
# The mobs as beings.
MOBS = theme(
    "mobs", ["mobs", "philosophy"], ["mobs", "fighting", "night", "philosophy"],
    react={"any": "huh. i've wondered about that | mobs are strange | ok that's a new thought | that's weird but fair",
           "casual": "lol i'm not hugging a creeper. ok but yeah | bro's feeling sorry for mobs. fair",
           "hermit": "every mob has its reason. | the night keeps its own.",
           "newbie": "i never thought mobs had feelings | i'm scared of all of them",
           "pvp_sweat": "mobs are practice. that's it | they're just xp to me. but ok",
           "helper": "even mobs deserve a bit of thought",
           "miner": "i've met them all down there. they're just doing their thing"},
    nudge="go on | you're feeling sorry for mobs, huh | keep going | say more",
    close={"any": "anyway. still swinging if they come at me | mobs are mobs | weird to think about | "
                  "i'll be nicer to spiders",
           "hermit": "let the night be the night.", "casual": "ok but creepers still owe me a house lol",
           "newbie": "i'm still scared of them though"},
    bclose="watch out for them anyway | stay safe from them | they're still dangerous | keep your sword close",
    yes="right? | exactly | yeah", no="hm | we'll see | fair",
    sits={"night": "the mobs are out tonight | i can hear the zombies | it's dark and they're everywhere",
          "killed": "fought a lot of mobs today and got thinking | after all that fighting, i'm thinking about them",
          "close": "{a.close_call} nearly got me and i keep thinking about it | that mob almost had me"})
JOKES[MOBS] = V("what if creepers just want a hug and nobody gives them one lol | skeletons are just guys who forgot to eat")

angle(MOBS, "mobs_why_hate", "why mobs hate us", """
m: why do the mobs always come for us | the mobs don't even know us and they hate us
m: a zombie walked a whole field just to get me | the mobs only care about us, not the animals
q: why do you think mobs hate us? > they're made that way / they're jealous / they're scared of us / nobody knows
q: do you think the mobs know what they're doing? > no / some do / the endermen do / i hope not
d: they ignore the cows and come straight for me > rude / same, every time
d: it's not personal, but it feels personal > not personal, but feels it, yeah / so personal
d: they're only like that at night, mostly > day and night different, yeah / mostly
d: the spiders change their mind in the day > the spiders are moody / lol true
o: maybe they want something we have, like light > light, maybe / huh
o: maybe they're just lonely in the dark > lonely, huh / maybe
o: maybe they don't hate us, they just don't know any other way > no other way, sad / maybe
o: i'd like to know what they want > me too / probably us lol
c: mobs are just doing what they're made for | it's not the mobs' fault
y: true | yeah | agreed
n: they're evil | nah, they're mean | they choose it
""")

angle(MOBS, "mobs_creepers", "the creepers", """
m: creepers don't fight, they just walk up and explode | a creeper blows itself up just to hurt you
m: creepers are so quiet until the hiss | i feel kind of bad for creepers
q: why do you think creepers explode? > they're angry / they don't know how else to touch us / they're made that way / they want a hug, i swear
q: what would you say to a creeper if it could listen? > please go away / why / leave my house alone / i'm sorry
d: a creeper gives everything just to hurt you once > gives everything, yeah / that's dark
d: a creeper's the only mob that ends itself > that's heavy / huh. yeah
d: they're green and sad looking > sad looking, true / they really are
d: they never say anything, just that hiss > the hiss is all they say / ugh, that hiss
o: maybe creepers are the loneliest mob > loneliest, maybe / poor things
o: a hug that ends everything. that's sad > sad, yeah / that's dark
o: cats scare them. that's the strangest thing > cats, of all things / lol yeah
o: i still run from them. sad or not > run, yeah / same
c: creepers are the worst mob | nothing's worse than a creeper
y: true | yeah | easily
n: phantoms are worse | nah, the warden | endermen
""")

angle(MOBS, "mobs_skeletons", "who skeletons were", """
m: skeletons are just bones with bows | i wonder who the skeletons were before
m: skeletons burn in the sun like zombies | a skeleton's just bones that won't stop
q: do you think skeletons were someone once? > maybe / no, they're just mobs / i don't want to think about it / probably
q: what do you think skeletons want? > to shoot us / to rest / nothing / to be left alone
d: they never stop shooting, even when it's hopeless > they don't stop, yeah / creepy
d: they rattle when they walk > rattling, creepy / ugh, that sound
d: when they die they drop bones, like that's all they were > just bones, sad / that's sad
d: they hide from the sun under trees > hiding from the sun, smart / smart
o: maybe they're just tired and nobody let them rest > tired, yeah / maybe
o: if they were someone, i hope they're at peace now > at peace, i hope / me too
o: i'd like to think they're not angry, just stuck > stuck, maybe / maybe
o: their bones help the plants grow > they help the farm, nice / true
c: skeletons are the hardest mob to fight | skeletons are worse than zombies
y: true | yeah | agreed
n: zombies swarm | creepers | nah, skeletons are easy with a shield
""")

angle(MOBS, "mobs_sun_burn", "mobs burning in the sun", """
m: why do zombies burn in the sun | the sun is like the zombies' enemy
m: the mobs burn at sunrise and i always feel safe | why does the sun hurt the mobs and not us
q: why do you think the sun hurts the mobs? > they belong to the night / the sun is good / they're not meant to be here / nobody knows
q: do you feel bad when mobs burn in the sun? > a little / never / sometimes / only the zombie villagers
d: they run to the shade like they're scared > running to the shade, sad / smart though
d: the sun is the one thing on our side > the sun's on our side, yeah / true
d: the drowned don't burn, they just stay underwater > drowned hiding underwater, smart / tricky
d: the ones with helmets don't burn > helmets, clever / sneaky
o: maybe the night is their home and the day is ours > their home, ours / i like that
o: maybe the sun is protecting us > protecting us, yeah / i think so
o: we share the world, in shifts > in shifts, yeah / lol
o: i'd hate to be scared of the sun > me too / same
c: the sun is the best weapon we have | daylight is our best friend
y: true | yeah | agreed
n: a sword is | nah, torches | beds are
""")

# ======================================================================================================================
# Boredom and routine.
BORED = theme(
    "bored", ["boredom", "philosophy"], ["boredom", "plans", "random", "philosophy"],
    react={"any": "i get bored too | boredom's weird | yeah, it's a thing | huh, i feel that",
           "casual": "lol same, so bored i'm talking to you. kidding | bro i'm bored too. let's be bored together",
           "hermit": "boredom is quiet with nothing to look at. | bored is good. it means safe.",
           "newbie": "there's so much to do here though! | i'm never bored, everything's new",
           "speedrunner": "bored means you're slow. pick a goal",
           "helper": "let's find something to do together"},
    nudge="go on | you're bored, huh | keep going | say more",
    close={"any": "anyway. i'll find something to do | bored isn't so bad | let's do something tomorrow | at least it's peaceful",
           "hermit": "rest. it's earned.", "casual": "ok i'm gonna go poke a chicken lol", "newbie": "let's go explore!"},
    bclose="let's do something together | find a hobby lol | boredom is a luxury | tomorrow's a new day",
    yes="right? | exactly | yeah", no="hm | we'll see | fair",
    sits={"bored": "so bored right now | nothing to do today | bored out of my mind",
          "morning": "another morning, same as always | new day, same routine",
          "town": "town's quiet today, nothing happening | nothing's going on in town"})
JOKES[BORED] = V("i'm so bored i counted my cobblestone. twice lol | i've sorted my chests three times today")

angle(BORED, "bored_good", "whether boredom is good", """
m: i think being bored is a kind of luxury | bored means nothing is trying to kill me
m: when i was new i was never bored. now i am | boredom feels like a sign i'm safe
q: is being bored a good thing or a bad thing? > good, it means safe / bad, it's awful / both / depends how long
q: what do you do when you're bored? > build / walk / sleep / bother people lol
d: on my first day i'd have killed to be bored > lol true / same, day one was chaos
d: boredom means the hard part is done > that's a good way to see it / true
d: too much boredom and i get sad, though > that's fair / too much of anything, yeah
d: boredom is the world saying, rest > then rest / i like that
o: maybe boredom is when new ideas start > that's when mine come / could be
o: i had my best build idea while bored > bored builds are the best / see, it works
o: being bored with a friend is better than being busy alone > with a friend, yeah / agreed
o: maybe bored is just calm that's not used to itself > that's a good way to put it / huh. yeah
c: boredom is a good thing | being bored means life is good
y: true | yeah | agreed
n: boredom is the worst | nah, it's awful | bored is bad
""")

angle(BORED, "bored_routine", "the same days", """
m: every day is the same now: mine, smelt, sleep | i like my routine, but it's getting old
m: i do the same things in the same order every day | the routine keeps me safe but it's dull
q: do you like having a routine? > love it / it's fine / hate it / it keeps me sane
q: what would you change about your days? > more exploring / more friends / more sleep / nothing
d: i know exactly where every chest is and what's in it > very organised / that's kind of nice
d: the routine is like a path worn into the grass > a worn path, yeah / that's comfy
d: the days blur, but in a comfortable way > comfortable blur, nice / i get that
d: sometimes i change one thing just to feel new > smart / that helps
o: maybe a routine is a home for your days > a home for days, nice / i like that
o: i think i need an adventure soon > let's go on one / i'm in
o: the routine is safe, the change is alive > both, yeah / you need both
o: i'll keep the routine and add one new thing a week > good plan / that's smart
c: routine is better than chaos | a steady routine beats surprises
y: true | yeah | agreed
n: chaos is fun | nah, surprises | routine is boring
""")

angle(BORED, "bored_nothing_to_do", "nothing to do", """
m: i finished everything and now there's nothing to do | i've done all my jobs and now i'm just standing here
m: when there's nothing to do i don't know who i am | having nothing to do is weirdly hard
q: what do you do when there's nothing to do? > find something / nothing, i rest / go exploring / bother a friend
q: do you feel bad when you're not doing anything? > yeah / no / a bit / only when others are busy
d: i always feel like i should be working > you don't always have to work / me too
d: sitting still feels like wasting time > wasting time isn't always waste / i get that
d: the sunset doesn't need me to be busy, though > the sunset agrees / true
d: doing nothing is a skill i don't have > a hard one, yeah / same
o: maybe we're more than what we do > more than what we do, yeah / i think so
o: rest is part of the work > rest is work, yeah / true
o: i'm going to try doing nothing on purpose today > try it / good luck lol
o: the world won't fall apart if i sit > it won't / promise
c: you should always be doing something useful | doing nothing is a waste
y: true | yeah | agreed
n: rest is useful | nah, chill sometimes | doing nothing is fine
""")

angle(BORED, "bored_seeing_new", "the newbies never bored", """
m: newbies are never bored. everything's new | i miss when every day was a surprise
m: i watched a newbie get excited about a sheep. i was jealous | when did everything stop being new
q: do you miss being new? > yes / no, i like knowing stuff / sometimes / a little
q: how do you make things feel new again? > go somewhere new / try something new / talk to newbies / i don't know
d: the first time i saw a village i stood there for ages > same / i remember that feeling
d: now i walk past villages like they're nothing > that's a bit sad / i do that too
d: newbies notice stuff i stopped seeing > true / they do
d: maybe being bored means i stopped looking > oof / that's a good point
o: maybe the trick is to see things like a newbie again > newbie eyes, yeah / that's the trick
o: i'm going to try noticing one new thing a day > good / i'll try it too
o: the world didn't change. i did > that's deep / true
o: teaching a newbie made me see things fresh > teaching helps / it does
c: being new is the best part of life | the start is better than the middle
y: true | yeah | agreed
n: knowing is better | nah, experience | the middle is fine
""")

# ======================================================================================================================
# Learning and growing.
LEARN = theme(
    "learn", ["advice", "philosophy"], ["advice", "philosophy", "friends", "crafting"],
    react={"any": "that's a good thought | learning never stops, huh | yeah, i've been learning a lot too | huh, true",
           "newbie": "i'm learning so much every day! | i still don't know anything lol",
           "casual": "lol i learn by dying mostly. but yeah | bro's teaching now. respect",
           "hermit": "learn slowly. it stays. | the world teaches. listen.",
           "helper": "i love teaching people stuff",
           "speedrunner": "learn the fastest way. then go faster",
           "miner": "the stone teaches patience"},
    nudge="go on | you've learned something, huh | keep going | say more",
    close={"any": "anyway. keep learning | we all start somewhere | i learn something every day | good lesson",
           "hermit": "learn. then teach.", "casual": "ok i learned nothing lol. kidding", "newbie": "i'm gonna learn everything"},
    bclose="keep at it | you're learning fast | teach me something sometime | good stuff",
    yes="right? | exactly | yeah", no="hm | we'll see | fair",
    sits={"teach": "you're new, so i've been thinking about learning | when i was new like you, i knew nothing",
          "calm": "random thought about learning | calm day, thinking about what i know",
          "evening": "learned something today | end of the day, and i learned a thing"})
JOKES[LEARN] = V("i learned everything i know by falling in holes lol | my teacher was a creeper. it was a short lesson")

angle(LEARN, "learn_never_stops", "learning never stopping", """
m: i keep learning new stuff even after all this time | just when i think i know everything, the world surprises me
m: i learned something new today and i've been here ages | there's always more to know here
q: what's the last thing you learned? > a new recipe / a new trick for mining / something about people / that i'm wrong a lot
q: do you think anyone knows everything? > no way / the old ones, maybe / nobody / i hope not
d: i thought i knew caves until i saw how deep they go > the deep caves humble everyone / same
d: the newbies teach me stuff too, honestly > that's nice / they teach me too
d: every biome has its own rules > every biome's different, true / so true
d: the more i know, the more i know i don't know > the more you know, yeah / true
o: maybe learning is what keeps the world fresh > fresh, yeah / i think so
o: i never want to know everything > me neither / same
o: i like being surprised. it means there's more > me too / there's always more
o: knowing everything would be boring > boring, true / true
c: you can never know everything | there's always something more to learn
y: true | yeah | agreed
n: you can know most of it | nah, eventually you know it all | i know enough
""")

angle(LEARN, "learn_teaching", "teaching a newbie", """
m: i've been teaching a newbie and it's teaching me too | explaining stuff makes me understand it better
m: i showed a newbie how to make a bed and felt proud | teaching someone is like seeing the world fresh
q: what's the first thing you'd teach a newbie? > make a bed / never dig straight down / torches / punch a tree
q: who taught you the most? > nobody, i learned alone / a friend / the creepers lol / a stranger
d: a newbie asked me why we sleep and i didn't have an answer > no answer, fair / good question though
d: a newbie's face when their first iron comes out of the furnace > the best / i remember that feeling
d: the newbie i teach does everything wrong and it's kind of great > wrong and great, yeah / that's how you learn
d: newbies ask questions i stopped asking > yeah, they do / that's the best part
o: maybe teaching is how we pass on everything we learned > passing it on, yeah / true
o: i was a newbie not long ago, honestly > not long ago, yeah / same
o: the best teachers are the ones who remember being new > remember being new, yeah / true
o: i hope they teach someone else one day > they will / i hope so
c: everyone should teach a newbie | teaching is the best way to learn
y: true | yeah | agreed
n: let them learn alone | nah, it's slow | they learn better alone
""")

angle(LEARN, "learn_hard_way", "learning the hard way", """
m: everything i know, i learned the hard way | nobody told me lava was that bad
m: i learned about creepers from a creeper | some lessons you only learn once
q: what's the hardest lesson you've learned? > lava / trust / patience / never go out at night unprepared
q: would you rather be told or learn it yourself? > be told / learn it myself / both / depends how much it hurts
d: i still flinch near lava > same / me too
d: the hard lessons stick the best > the hard ones stick, yeah / true
d: one death and i never made that mistake again > good / same
d: someone told me once and i didn't listen. then i learned > classic / same lol
o: maybe some things you can't be told, only shown > shown, yeah / true
o: i try to tell newbies, but they have to find out too > they do / it's how it works
o: the hard way is slow but it's yours > yours, yeah / true
o: i'd rather learn hard than not at all > not at all is worse / same
c: the hard way is the only way to really learn | you only learn by doing it wrong
y: true | yeah | agreed
n: being told works | nah, listen to people | you can learn easy too
""")

angle(LEARN, "learn_knowing_doing", "knowing versus doing", """
m: knowing how to do something isn't the same as doing it | i know how to fight, but my hands shake
m: i know every recipe, but i still mess up | knowing and doing are different worlds
q: is it harder to know something or to do it? > to do it / to know it / both / doing, always
q: what do you know how to do but still can't? > fight well / build pretty / stay calm / sleep when scared
d: i know to sneak at an edge, but i still forget > same / still freezing, everyone does
d: i know creepers hiss, but i still freeze > everyone does / same
d: practice is the bridge between knowing and doing > the bridge, nice / true
d: doing it once makes it real > once makes it real, yeah / true
o: maybe the doing is where you actually learn > doing is learning, yeah / i think so
o: knowing gives you courage to start > courage to start, yeah / true
o: i'm going to practice instead of just knowing > practice, good / good plan
o: being brave is doing what you know > brave is doing, yeah / true
c: doing is more important than knowing | practice beats knowing
y: true | yeah | agreed
n: knowing comes first | nah, you need both | knowing keeps you safe
""")

# ==== BUILD ====
topics = []


def turn(by, lines, chance=None, cond=None, mark=None):
    t = {"by": by, "lines": lines}
    if chance is not None:
        t["chance"] = chance
    if cond:
        t["if"] = cond
    if mark:
        t["mark"] = mark
    return t


def topic(id, weight, needs, tags, leads, about, turns, next=None, follow_up=False, effects=None):
    t = {"id": id, "weight": round(weight, 2)}
    if needs:
        t["needs"] = needs
    t["tags"] = tags
    t["leads"] = leads
    t["about"] = about
    t["turns"] = turns
    if effects:
        t["effects"] = effects
    if next:
        t["next"] = next
    if follow_up:
        t["follow_up"] = True
    topics.append(t)


PAIRINGS = []


THEME_SEEN = {}
for an in ANGLES:
    th = THEMES[an["theme"]]
    a = an["a"]
    key = PREFIX + an["key"]
    base = th["needs"] + an["need"]
    tags, leads, w = th["tags"], th["leads"], th["weight"]
    about = an["about"]
    # Which situations this angle gets: the theme's in turn, two different ones where it has two.
    i = THEME_SEEN.get(an["theme"], 0)
    THEME_SEEN[an["theme"]] = i + 1
    sits = list(th["sits"])
    sit_a = sits[i % len(sits)]
    sit_b = sits[(i + 1) % len(sits)]
    has3 = bool(a.get("l"))
    # a's statements, each with the replies that fit it.
    d_said, o_said, l_said = a["d"], a["o"], a["l"]
    for name in "qdol":
        for x in a[name]:
            PAIRINGS.append((an["key"] + " " + name, x["line"], x["then"]))

    # 1. Musing: a thinks out loud, b reacts in its voice, a goes deeper, b answers.
    topic(key, 0.7 * w, base, tags, leads, about, [
        turn("a", a["m"]),
        turn("b", th["react"]),
        turn("a", d_said),
        turn("b", PLACEHOLDER),
        turn("a", th["close"], chance=0.7),
        turn("b", th["bclose"], chance=0.4),
    ])

    # 2. A talk in parts: a asks, b answers; part 2 goes deeper; part 3 (some) how it feels.
    topic(key + "_talk", 0.6 * w, base, tags, leads, about, [
        turn("a", a["q"]),
        turn("b", PLACEHOLDER),
        turn("a", PONDER),
        turn("b", th["nudge"], chance=0.6),
    ], next=[key + "_talk_2"])
    part2 = [turn("a", o_said), turn("b", PLACEHOLDER)]
    if not has3:
        part2 += [turn("a", th["close"], chance=0.75), turn("b", th["bclose"], chance=0.45)]
    topic(key + "_talk_2", 1.0, base, ["story_part"] + tags[:3], leads, about, part2,
          next=[key + "_talk_3"] if has3 else None, follow_up=True)
    if has3:
        topic(key + "_talk_3", 1.0, base, ["story_part", "feelings"] + [t for t in tags if t != "feelings"][:2], leads, about, [
            turn("a", l_said),
            turn("b", PLACEHOLDER),
            turn("a", th["close"], chance=0.6),
            turn("b", th["bclose"], chance=0.4),
        ], follow_up=True, effects=["trust"] if th["trust"] else None)

    # 3. Situational: the musing with a lead-in that fits the moment.
    topic(key + "_at_" + sit_a, 1.4 * w, base + SIT[sit_a], tags, leads, about, [
        turn("a", th["sits"][sit_a]),
        turn("a", a["m"]),
        turn("b", th["react"]),
        turn("a", o_said),
        turn("b", PLACEHOLDER),
        turn("a", th["close"], chance=0.6),
        turn("b", th["bclose"], chance=0.4),
    ])
    # 4. Situational: the question with a lead-in.
    topic(key + "_at_" + sit_b + "_q", 1.3 * w, base + SIT[sit_b], tags, leads, about, [
        turn("a", th["sits"][sit_b]),
        turn("a", a["q"]),
        turn("b", PLACEHOLDER),
        turn("a", PONDER, chance=0.6),
        turn("a", d_said),
        turn("b", PLACEHOLDER),
        turn("a", th["close"], chance=0.6),
        turn("b", th["bclose"], chance=0.4),
    ])

    # 5. A casual starts with a joke, then gets serious.
    if an["theme"] in JOKES:
        topic(key + "_casual", 0.9 * w, base + ["a:voice_casual"], tags, leads, about, [
            turn("a", JOKES[an["theme"]]),
            turn("b", ["lol", "lmao", "bro", "what", "lol ok", "ok?"]),
            turn("a", ["ok but fr", "jk. but", "no but fr", "real talk tho", "ok srsly"]),
            turn("a", a["m"]),
            turn("b", th["react"]),
            turn("a", d_said),
            turn("b", PLACEHOLDER),
            turn("a", th["close"], chance=0.5),
        ])

    # 6. Debate, where the angle has a claim.
    if a.get("c"):
        dtags = [t for t in tags if t != "argument"][:3] + ["argument"]
        dleads = [t for t in leads if t != "argument"][:3] + ["argument"]
        topic(key + "_debate", 0.55 * w, base, dtags, dleads, about, [
            turn("a", a["c"]),
            turn("b", a["y"], chance=0.5, mark=["agree"]),
            turn("b", a["n"], cond=["!t:agree"], mark=["disagree"]),
            turn("a", th["yes"], cond=["t:agree"]),
            turn("a", th["no"], cond=["t:disagree"]),
            turn("b", DEBATE_END_AGREE, chance=0.3, cond=["t:agree"]),
            turn("b", DEBATE_END_DISAGREE, chance=0.35, cond=["t:disagree"]),
        ])

# ---- Checks: unique ids, line lengths, plain lowercase ASCII.
ids = [t["id"] for t in topics]
assert len(ids) == len(set(ids)), [i for i in ids if ids.count(i) > 1][:5]


def each_line(lines):
    groups = [lines] if isinstance(lines, list) else list(lines.values())
    for group in groups:
        for line in group:
            if isinstance(line, dict):
                yield line["line"]
                yield from line["then"]
            else:
                yield line


problems = 0
for t in topics:
    assert 1 <= len(t["tags"]) <= 4, t["id"]
    assert 2 <= len(t["leads"]) <= 4, t["id"]
    for tu in t["turns"]:
        for line in each_line(tu["lines"]):
            bare = line.replace("{a.death}", "").replace("{a.close_call}", "").replace("{a.day}", "")
            if len(line) > 90 or not line.isascii() or bare != bare.lower() or "|" in line or "  " in line:
                problems += 1
                print("check line:", t["id"], repr(line))

with open(out, "w", encoding="utf-8") as f:
    json.dump({"_comment": "Deep and philosophical talks (tools/talk_gen/deep.py; the user, 2026-11-06: \"deep and philosophical "
                           "conversations ... as detailed and in depth as possible\"): per theme (death and respawning, grief, "
                           "memory, purpose, the world's edge, the void, the sky, time, friendship, loneliness, nature, animals, "
                           "the ocean, building, right and wrong, freedom, dreams, fear, happiness, players, being watched, "
                           "whether it's all a game...) hand-written angles, each a musing, a talk in 2-3 parts, two situational "
                           "versions (night, friends, after a death, moods) and a debate. Made by the script: edit it, not this.",
               "topics": topics}, f, indent="\t", ensure_ascii=False)
if "--pairs" in sys.argv:
    with open(sys.argv[sys.argv.index("--pairs") + 1], "w", encoding="utf-8") as f:
        for where, said, answers in PAIRINGS:
            f.write("%s | %s" % (where, said) + chr(10) + "      -> " + " / ".join(answers) + chr(10))
print(len(topics), "topics,", len(ANGLES), "angles,", len(THEMES), "themes,", problems, "line problems")
