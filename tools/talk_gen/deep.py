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
- a debate (agree/disagree marks) when the angle has a claim.

Usage: python tools/talk_gen/deep.py src/main/resources/data/drifter/drifter_talk/topics_gen_deep.json
Check: python tools/check_talk_web.py . --quiet

Format of the tables below (to keep thousands of lines readable): a pool is one string, lines split by " | ". An angle
is a block, one field per line ("m: ..."), the field letters:
  m  a's musings (statements)              q  a question > answers split by " / " (one q per line)
  d  a's deeper thought                    r  b's reply to it
  o  a's next, deeper thought (part 2)     p  b's reply to that
  l  a's feelings (part 3, optional)       k  b's answer to those
  c  a claim to argue about (optional)     y  agreeing with it        n  disagreeing with it
"""
import json
import re
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
SHORT = ["yeah", "mhm", "same", "true", "for real", "hm", "yeah.", "fr", "right"]
PLACEHOLDER = ["hm"]
# b's reply to a thought when none of the angle's replies fits it.
REPLY_ANY = ["i get that", "yeah, i know what you mean", "that makes sense", "huh. yeah", "i hear you", "same, honestly"]
DEBATE_END = ["fair", "hm", "we'll see", "maybe", "ok ok", "lol", "if you say so"]

THEMES = {}
ANGLES = []
JOKES = {}  # theme -> a casual's joke to start on (then it gets serious)


def theme(key, tags, leads, react, ponder, nudge, close, bclose, yes, no, sits, needs=(), trust=False, weight=1.0):
    THEMES[key] = dict(tags=tags, leads=leads, react=V(react), ponder=V(ponder), nudge=V(nudge), close=V(close),
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
    a = {name: L(" | ".join(texts)) for name, texts in fields.items() if name != "q"}
    a["q"] = [q_line(x) for x in fields.get("q", [])]
    for name in ("m", "d", "r", "o", "p"):
        assert a.get(name), (key, name)
    assert a["q"], (key, "q")
    ANGLES.append(dict(theme=th, key=key, about=about, a=a, need=list(need)))


# ======================================================================================================================
# Death and respawning.
RESPAWN = theme(
    "respawn", ["death", "philosophy"], ["death", "philosophy", "feelings", "memories"],
    react={"any": "heavy thought | i try not to go there | ngl that's crossed my mind too | huh. i've wondered the same | "
                  "that one keeps me up some nights",
           "casual": "bro who hurt you lol. but yeah | lol ok philosopher. fair though | that's dark. i kinda get it though",
           "hermit": "the bed forgets. we don't. | everyone wakes. not everyone wakes the same. | mm. dying teaches.",
           "newbie": "wait, should i be scared of that?? | i've only died like twice and it was awful | "
                     "i just thought dying was part of it",
           "helper": "you ok? that's a big thing to carry | i'm glad you're here to wonder about it | "
                     "you can always talk to me about this",
           "speedrunner": "dying costs me a minute. that's all i let it be | a death is just a reset with extra walking | "
                          "i count my deaths. the number never feels good"},
    ponder={"any": "huh. maybe | i want to believe that | i keep going back and forth on it | that's one way to see it | "
                   "maybe you're right",
            "hermit": "mm. maybe. | perhaps.", "newbie": "oh. i never thought of it like that | woah ok"},
    nudge="you've been chewing on this one | go on | what got you thinking about dying | there's more, isn't there",
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
          "friends": "can i tell you something weird | i trust you, so i'll just say it | you're the only one i'd ask this"})

angle(RESPAWN, "respawn_counts", "whether dying counts", """
m: been thinking about whether dying even counts if we come back | if we always wake up after, is it really dying?
m: dying feels like it should be bigger than it is | you fall, it goes dark, then you're just back in bed
q: does dying count if you always come back? > it counts. you lose everything / not really, it's like a bad nap / i think it counts because it hurts / idk. it feels like it should
q: is it still dying if you wake up after? > yeah, something ends every time / no, dying means not coming back / it's dying with a second chance
d: like, the fall still hurts. then you're back in bed like nothing happened | your stuff stays where you died, but you don't
d: the world keeps your stuff and hands you back to yourself
r: i think it counts because we're scared of it | if it didn't count we wouldn't run from creepers
r: it counts. ask anyone who lost their diamonds | maybe it's practice for something
o: the thing that gets me is nobody else even notices | the world doesn't stop for it. the sun just keeps going
o: what if every death costs something we can't see | maybe a little bit of us stays where we fell
p: then i've left a lot of me in lava lol | if that's true i want my bits back | i think the part that stays is the fear
p: then we should be careful how many we spend
l: i think i'm scared of the one time it doesn't work | maybe that's why i put beds everywhere
l: honestly i'm more scared of dying than i let on
k: then let's make sure it always works | i'll watch your back out there | everybody is. you're just honest about it
c: dying doesn't count if you come back | if you wake up after, it's not really dying | respawning makes death just a nap
y: yeah, a nap with extra walking | honestly true | fair, we always come back
n: it counts. you lose your stuff and your nerve | nah, ask anyone who lost diamonds | it counts every time, trust me
""")

angle(RESPAWN, "respawn_same_self", "whether you're still you after", """
m: i keep wondering if the one who wakes up in bed is really me | what if every respawn makes a new us
m: i woke up in bed and felt like a copy of myself | the me that fell in the lava and the me here, same one?
q: when you respawn, are you still you? > yeah, same scars, same grudges / i hope so / i remember dying, so it's me / no idea, and i don't want to know
q: what if the one who wakes up isn't the one who died? > then i owe the last me some diamonds / you'd still be you to me / then i've been a lot of people
d: i remember the fall, so i guess it's me | but remembering isn't the same as being there
d: i feel the same, but would i know if i wasn't? | my house still feels like mine, so maybe
r: you still laugh at the same dumb stuff. it's you | if you remember it, you're you. that's enough for me
r: you came back and knew my name. that's you | i'd notice if you weren't you, trust me
o: sometimes i feel a bit different after, like quieter | what if we lose a tiny bit each time and don't notice
o: after my last death i stopped going near cliffs | maybe the scared part is what comes back stronger
p: different isn't worse | that's not losing a bit, that's learning | quieter is ok. you're still you
p: i think that's just being careful now
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
d: if it was the last one i think i'd stop mining so deep | i'd probably be braver, weirdly
d: i'd want to finish my house first | i'd stop wasting days on dumb stuff
r: maybe live like it is, just a bit | that's a good reason to do the stuff you keep putting off
r: i'd spend it with people, not chests | then don't wait for it to be the last one
o: the funny thing is, everyone would live better if they knew | we waste lives because we think we have a lot
o: i think about the ones who wandered off and never came back | maybe the bed is a gift and we treat it like a chore
p: so let's not waste this one | ok, tomorrow we do the thing we keep saying we'll do
p: that's why i say hi to everyone now | gifts get taken for granted. you're right
l: if it was my last day, i'd want you there | i don't say it much, but i'm glad i'm not doing this alone
l: if i don't come back one day, you can have my chests. not the diamonds though
k: i'd be there. no question | same. you're stuck with me | deal. but you're coming back, so it doesn't matter
c: we should live like every life is the last one | living like it's your last life is the only way to live
y: yeah. no more putting stuff off | honestly true | that's how i try to live
n: that sounds exhausting | nah, then i'd never go mining again | i'd be too scared to do anything
""")

angle(RESPAWN, "respawn_between", "the dark between dying and waking", """
m: there's a moment between dying and waking that i can't remember | it goes dark and then it's morning in my bed
m: where do we go in that little dark bit | i wish i knew what's on the other side of the dark
q: what do you think happens in the dark between dying and waking? > nothing. just dark / i think we wait somewhere / i think i hear music in it / i don't remember, and that scares me
q: do you remember anything after you die? > just red, then my bed / a feeling like falling / nothing. it's a blank / a kind of quiet, then morning
d: it's not like sleeping. sleep at least feels like rest | it's like the world blinks and i'm somewhere else
d: i think it's short, but i can't tell | sometimes it feels like it lasts forever
r: maybe it's best we don't remember | i think it's just the world catching its breath
r: the dark is just the bed pulling you home | whatever it is, you always come out of it
o: what if other people are in there too, waiting | sometimes i think i hear voices in it
o: what if the ones who don't come back stay in the dark | i'd like to know it's peaceful in there at least
p: i hope it's peaceful | if it's dark, i hope it's at least quiet | if they're there, i hope they're ok
p: i think it is. people come back calm
l: i don't like not knowing. it's the worst part | can we not talk about the dark at night, actually
l: i think i'll sleep with a torch by the bed tonight
k: you're not alone in not knowing | yeah. torches all round | ok. let's talk about sheep instead
c: there's nothing between dying and waking up | it's just nothing between death and the bed, no place at all
y: yeah, just a blink | i think so too | nothing, it's a blank
n: there's something there. i can feel it | nah, it's a place. we just forget | i don't think it's nothing
""")

angle(RESPAWN, "respawn_stuff", "what you carry when you die", """
m: when you die everything you carried just spills out | all that work, scattered on the ground in a second
m: if you don't get back fast enough your stuff just vanishes | you come back with nothing but yourself
q: if you died right now, what would you miss most from your pockets? > my pickaxe, we've been through a lot / honestly nothing, it's all just stuff / the food. i always die hungry / my map. it took days
q: do you think the stuff we carry says who we are? > yeah, i carry way too much dirt / maybe, my pockets are a mess like me / no, it's what we do with it / kinda. you always carry flowers
d: you learn what matters when you have to choose what to save | the first thing i grab is never the diamonds, weirdly
d: i always think about my pickaxe first. it's silly | coming back with nothing makes you feel light
r: maybe stuff is just borrowed | the stuff doesn't remember you, you remember the stuff
r: losing it all teaches you to hold things loosely | you can always get more stuff. not more you
o: one time i went back and it was all gone. even my sword | i stood where my stuff used to be for ages
o: it's like the world cleans up after us | there's something sad about xp floating with no one to take it
p: i've done that too. just stood there | yeah, it's sad. but you made a new sword
p: the world's tidy like that, i guess | that's oddly beautiful
c: losing your stuff is worse than dying | dying is nothing. losing the stuff is the real death
y: true, the walk back is the worst | facts, rip my diamonds | yeah, you get yourself back but not your stuff
n: nah, stuff is stuff | you can get stuff again | i'd rather lose stuff than lose myself
""")

angle(RESPAWN, "respawn_careless", "whether coming back makes us careless", """
m: i think knowing we come back makes us reckless | i jumped off a cliff once just to save walking
m: we treat our lives like they're cheap | if we only had one life we'd live so differently
q: would you be braver or more scared if you only had one life? > way more scared / braver, weirdly. every second would matter / the same. i'm already scared / careful, not scared
q: do you think coming back makes us careless? > definitely. i've seen people dig straight down / a bit / no, it makes us brave / careful people come back too
d: i walk past lava without thinking now. that's not normal | i used to be so careful my first few days
d: dying stopped scaring me and that scares me | maybe being careless is the price of coming back
r: careful is good. careless is what gets you | being a bit scared is healthy
r: i'd rather you be scared than in lava | some fear keeps you alive, even if you come back
o: i watched someone run into a cave with no torches, laughing | the ones who are scared are the ones who last
o: i bet the oldest drifters are the careful ones | maybe that's why some of us hold back. we remember
p: some people want to feel something, i think | i like the careful ones. they're still around
p: maybe the scared ones are the smart ones | careful isn't boring, it's alive
c: knowing you'll respawn makes you reckless | respawning made everyone careless
y: yeah, look at all the pillars into lava | true, i do dumb stuff | it does
n: nah, nobody likes dying | it makes you brave, not reckless | i'm careful every time
""")

angle(RESPAWN, "respawn_bed", "a bed deciding where you wake", """
m: it's funny how a bed decides where you come back | i put my bed somewhere and that's home now
m: if my bed breaks i wake up back where i started | my bed is the only thing that knows where i belong
q: if you could only respawn in one place, where would it be? > right where i am now / my house, by the window / somewhere near water / wherever my friends are
q: does a bed make a place home? > yeah, the bed is the heart of it / no, people make a place home / kinda, at least the world thinks so / a bed and a chest, that's a home
d: the first night in my own bed felt like a promise | when someone breaks your bed you lose your place in the world
d: i keep my bed close to my chests just in case | waking up at the start felt like being a stranger again
r: the world remembers your bed even if it forgets you | a bed is a promise you'll come back
r: home is a bed someone cares about | that's why i never let my bed get broken
o: i woke up at the start once and walked days to get home | i wonder where i'd wake if i had no bed at all
o: the first bed i made was ugly but i loved it | a bed in a cave isn't a home. it's just a bed
p: that walk must've been long | the start isn't home. it's just where it began | ugly first beds are the best ones
p: yeah, home needs more than a bed
l: knowing my bed is there makes the dark easier | i think i'm scared of dying without a bed somewhere
k: then let's always make sure you've got one | i'll help you find a safe spot for it | you'll always have a bed at mine
c: your bed is the most important thing you own | a bed is worth more than diamonds
y: true, it's your whole life really | yeah, no bed no home | agreed
n: diamonds though | nah, beds are cheap | a bed is just wool and planks
""")

angle(RESPAWN, "respawn_first", "the first death", """
m: i still remember my first death | the first time i died i thought that was it
m: my first death was so dumb, but it changed me | i didn't know we came back until i did
q: how did you die the first time? > a zombie at night, didn't see it / fell in a ravine / drowned trying to swim too far / creeper. of course
q: did you know we came back, the first time you died? > no, i was so scared / i guessed, but i wasn't sure / i didn't know anything back then / i thought i'd wake up somewhere else
d: i remember waking up and just sitting there for a while | everything looked the same but i felt different
d: after that first time, nights got scarier, not less | i think that's when i grew up a bit
r: first deaths stick with you | everyone's first time is like that | it's ok to remember it
r: i sat in my bed for ages too
o: sometimes i think about the zombie that got me. is it still out there | i went back to where i fell and put a torch there
o: i don't think i ever told anyone about it | funny how the first one is the one that matters
p: you can tell me | putting a torch there is kind of beautiful | the first one is always the loudest
p: zombies burn in the sun. it's long gone, i bet
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
    ponder={"any": "maybe. i hope so | that helps a little | yeah | i hadn't thought of it like that", "hermit": "mm."},
    nudge="you can tell me more if you want | i'm listening | take your time | there's more to it, huh",
    close={"any": "thanks for listening | anyway. i'm ok, mostly | it helps to say it out loud | i just needed to say that",
           "hermit": "enough. thank you.", "newbie": "sorry for being sad lol", "casual": "ok. back to normal. ish"},
    bclose="anytime | i'm around if you need me | you're not alone in this | come find me whenever",
    yes="yeah. glad you get it | right | thank you",
    no="maybe. i see it different | that's fair, i guess | we all handle it our own way",
    sits={"grief": "i'm having a rough day | i keep thinking about someone who's gone | today's been heavy | "
                   "i'm not really ok today",
          "night": "nights are the worst for missing people | it's quiet. i keep thinking about them | "
                   "can't sleep, keep thinking about who's gone",
          "friends": "i haven't told anyone this | can i talk to you about something sad | "
                     "you're the only one i'd say this to"},
    needs=["!strangers", "!rivals"], trust=True)

angle(GRIEF, "grief_gone_friend", "a friend who walked off", """
m: i had a friend once who just walked off one day | someone i knew went exploring and never came back
m: i still look for a friend who left | there was someone i used to talk to every day. then nothing
q: do you think people who leave ever come back? > some do. the world's big but paths cross / i hope so / i think they find a new home / i don't know. i'd like to think so
q: ever lose someone and not know what happened to them? > yeah. that's the worst kind / once. i still wonder / no, and i hope i never do / everyone has, i think
d: not knowing is worse than knowing, i think | i keep a chest of their stuff, just in case
d: i walked the way they went for a whole day | sometimes i think i see them on the horizon
r: maybe they're out there building something | not knowing means there's still hope
r: keeping their chest is kind of beautiful | the world's huge. they could be anywhere, alive
o: i hope they found somewhere nice, with water and trees | i think about what i'd say if they walked up right now
o: i never got to say bye. that's the part | i wonder if they think about me too
p: i bet they do | you'd say hi. that's enough | the bye would've been hard anyway | i hope they found a good spot
l: i don't want to lose anyone else | i think that's why i'm scared to get close to people now
l: so... don't go walking off, ok?
k: i'm not going anywhere | i get it. i'll stick around | deal. if i go, i'll say bye first
c: it's better to say bye than to just leave | leaving without saying bye is the worst thing you can do
y: yeah, always say bye | true. it stays with people | agreed
n: sometimes you can't say bye | some people just need to go | saying bye is harder than leaving
""")

angle(GRIEF, "grief_pet", "losing a pet", """
m: my dog died to a creeper and i still think about him | i lost a cat once. the house feels empty without her
m: i miss my dog. he'd sit by the door | had a wolf that followed me everywhere. not anymore
q: do you think pets know how much we love them? > yeah, look how they wag / i think so / they know. they always come back to you / i hope so. i told mine all the time
q: would you get a new pet after losing one? > not for a while / yeah, but it'd never replace them / i don't know if i could / maybe. they'd want me to
d: i put his collar colour on a banner by my door | the worst part is the quiet when i get home
d: i still save bones like he's gonna want them | i keep looking at the spot where she slept
r: that quiet is love, kind of | a banner for him is a nice idea | he had a good life with you
r: they don't need much. you gave him a lot
o: pets don't come back. that's what gets me | we get to respawn and they don't. it's not fair
o: i named him and that made it worse | he followed me into a cave i shouldn't have gone into
p: it's not your fault | i know. it's not fair at all | naming them is what makes them family
p: he wanted to be with you. that's why he followed
l: i think i'll tame another one. one day | can we go see the wolves sometime? just to look
k: whenever you're ready | sure. just to look | i'd like that
c: pets matter more than any diamond | losing a pet is worse than losing gear
y: by far | yeah, gear comes back | of course
n: i love them, but it's not the same kind of loss | gear keeps you alive though | hmm, depends on the gear
""")

angle(GRIEF, "grief_empty_house", "an empty house", """
m: there's a house in town nobody lives in anymore | i walk past an empty house and wonder who built it
m: someone left their house and the torches are still lit | there's a bed in an old house nobody sleeps in
q: do you think a house remembers who lived in it? > i like to think so / no, but people do / the chests remember / a little, in the way it's built
q: should someone move into a house that's empty? > yes, houses want to be lived in / no, it's still theirs / only if they're not coming back / maybe, if they look after it
d: the chests are still full. nobody touches them | the door's open and it creaks in the wind
d: someone planted flowers by it once | you can see where they ran out of planks on the roof
r: that's someone's whole story in one house | maybe we should keep it nice, for them
r: i like that the torches are still on | leaving it how it is feels right
o: i went in once and there was a sign that said home | what if they come back and find someone in their bed
o: i fixed a hole in the roof. didn't feel right leaving it | empty houses are the saddest builds
p: a sign that says home. that's rough | fixing the roof was kind
p: if they come back they'll be glad it's there | yeah. builds need people
c: an empty house should be left alone | nobody should move into someone else's house
y: yeah, it's theirs | true, out of respect | agreed
n: houses are for living in | if they're gone, it should be used | a house rots if no one lives there
""")

angle(GRIEF, "grief_remember", "how to remember someone", """
m: i want to remember the people i've lost somehow | i made a sign for someone who's gone
m: i think we should mark the places people are lost | i keep their name in a book so i don't forget
q: how do you want to be remembered? > as someone who helped / by my house, i guess / as a good friend / i don't need to be, honestly
q: what's the best way to remember someone? > keep doing the stuff they loved / a sign with their name / tell stories about them / build something for them
d: i keep telling the story of how we met | i planted a tree where they used to sit
d: i still use the pickaxe they gave me | every time i see a sunflower i think of them
r: a tree is perfect. it keeps growing | using their pickaxe is a good way to keep them
r: stories keep people around | that's a really nice way to remember them
o: i'm scared one day i'll forget their voice | i forgot their name for a second last week. it scared me
o: memories fade like a path the grass grows over | i wonder if anyone will tell stories about me
p: write it down then. books don't forget | that happens. it doesn't mean you didn't care
p: i'll tell stories about you | then keep walking the path
l: i'd like to make something for them. would you help? | i think i'll build a little garden for them
k: of course | i'd be honored | just tell me where
c: graves and signs don't matter, memories do | you don't need a sign to remember someone
y: yeah, it's in your head | true | agreed
n: signs help, though | i like having a place to go | a sign stays when memory fades
""")

angle(GRIEF, "grief_graves", "graves on the hills", """
m: i saw a little grave on a hill today | someone made a grave out of cobble and a sign
m: there's a grave by the river with flowers on it | i made a grave once, for my old horse
q: do graves matter if we all come back? > they're for the ones who don't / they're for the living / not really, but i like them / yeah, they mean someone was loved
q: would you want a grave? > a small one, with flowers / no, just keep my house nice / a big one, obviously lol / under a tree, i think
d: the sign just said a name and a day | somebody keeps putting fresh flowers on it
d: it was on the best view around | it was so small but someone took care building it
r: someone loved them. that's what a grave says | flowers mean someone still remembers
r: they picked a nice spot | small graves hit the hardest
o: we come back but some things don't. animals. friends who wander | who do you think the grave was for
o: i stood there for a while and didn't know why | i wanted to add a flower but felt like i shouldn't
p: you should add one. it's kind | i think standing there is the point
p: maybe someone like us, before | it doesn't matter who. someone
c: graves are for the living, not the dead | graves are more for us than for them
y: yeah, we need somewhere to go | true | that's what they're for
n: they're for both | i think they're for the dead too | nah, they honour them
""")

angle(GRIEF, "grief_miss_me", "whether anyone would miss you", """
m: sometimes i wonder if anyone would miss me | if i walked off, i don't think anyone would notice
m: i think about who'd notice if i stopped coming back | i don't know who'd come looking for me
q: if i disappeared, would you come looking? > obviously / yeah, i'd check every cave / i'd look until i found you / you're not disappearing, so
q: do you think people notice when you're gone? > more than you think / yeah, the quiet gets loud / some do / the ones who matter do
d: i'm not sad, i just wonder | my house would just sit there with the door open
d: i don't talk much, so maybe nobody would notice | i don't even know who'd get my chests
r: i'd notice. i'd notice the first day | people notice more than they say
r: i'd miss your dumb jokes | you matter more than you think
o: i think everyone wonders that at night | maybe that's why i keep saying hi to people
o: i want someone to know where my house is, just in case | it's nice to hear someone say it
p: then i'll say it again. i'd miss you | saying hi matters. keep doing it
p: tell me where it is. i'll remember | anytime
l: thanks. i needed that more than i thought | ok. i feel a bit better now
k: anytime. i mean it | good. you're stuck with me | i'm glad i was here for it
""")

# ======================================================================================================================
# Memory: before we arrived, why here, names, knowing things, forgetting.
MEMORY = theme(
    "memory", ["memories", "philosophy"], ["memories", "philosophy", "dreams", "feelings"],
    react={"any": "huh. i've never thought about that | that's a weird one. i like it | i honestly can't remember either | "
                  "now you've got me wondering",
           "casual": "my memory's so bad i forget where my bed is lol. but yeah | lol i can't remember yesterday. but fr",
           "hermit": "the past is a cave. no torch reaches it. | some doors stay shut.",
           "newbie": "wait, do other people remember stuff?? | i only remember like a week back lol",
           "helper": "it's ok not to remember. we're here now | that's a gentle thing to wonder about",
           "speedrunner": "don't look back, it costs time | i remember routes, not feelings"},
    ponder={"any": "huh | that's oddly comforting | maybe | i don't know if that helps or not | that's a good way to put it",
            "newbie": "oh, that makes sense", "hermit": "mm."},
    nudge="what made you think of that | go on | you remember something, don't you | keep going",
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
          "friends": "can i ask you something i never ask anyone | you ever talk about this stuff? | "
                     "weird question, but you're the one to ask"})

angle(MEMORY, "memory_before", "anything from before", """
m: i don't remember anything before i got here | sometimes i try to remember before, and there's just nothing
m: it's like i started the day i arrived | i keep feeling like there was something before this
q: do you remember anything from before you arrived? > nothing at all / just a feeling, like a dream / i remember my name, that's it / i don't like to think about it
q: where do you think we were before we got here? > nowhere. we just started / somewhere else, like another world / asleep, maybe / i ask myself that every night
d: my first memory is grass and a tree and the sun coming up | i knew how to punch a tree but not why
d: it's like waking up in the middle of a sentence | the oldest thing i remember is being hungry
r: maybe there's nothing to remember | i think we just began, like a sapling
r: the first sunrise is my oldest memory too | maybe before doesn't matter
o: what if we lived whole lives before and forgot | sometimes a place feels familiar and i've never been there
o: i get this feeling near water, like i've been somewhere like it | what if we'll forget this too one day
p: then let's make this one worth remembering | familiar places freak me out too
p: maybe some things stay even when memories don't | i hope not. i like this one
l: honestly it scares me a little, not knowing | i'm glad that whatever came before, i ended up near you
k: you're not the only one | same. here is good | it scares me too sometimes
c: it doesn't matter where we came from | what happened before we got here doesn't matter
y: yeah, here is what counts | true | agreed, it's the now that matters
n: it matters, i want to know | nah, it's part of who we are | i'd give a lot to know
""")

angle(MEMORY, "memory_why_here", "why we woke up here", """
m: i keep wondering why we woke up here of all places | nobody asked me if i wanted to be here
m: i just opened my eyes and there was a world | why here, why this world
q: why do you think we woke up here? > to build something / no reason, it just happened / someone wanted us here / to find out, i guess
q: if you could wake up anywhere, where would you pick? > a big flower field / by the ocean / right here, honestly / a village with friends already in it
d: it feels too big to be an accident | maybe the world needed someone to walk around in it
d: the world was already here, waiting, like it knew | everything was ready. trees, water, sheep
r: maybe it doesn't need a reason | i like to think the world wanted company
r: accidents can still be good ones | the sheep were waiting for us, clearly
o: what if every drifter woke up for a different reason | maybe the reason is the thing we're supposed to find
o: what if there's no reason and that's the point | maybe we're here because someone hoped we'd be happy
p: i like that one | then i'm still looking | that's kind of freeing, actually | then let's be happy, just in case
c: there's a reason we woke up here | we were put here on purpose
y: i think so too | has to be | yeah, it's too perfect otherwise
n: nah, it just happened | there's no reason, and that's fine | i don't think so
""")

angle(MEMORY, "memory_names", "where our names came from", """
m: i've been wondering where my name came from | nobody gave me my name. i just knew it
m: some names have numbers in them. who decides that | i like my name but i didn't pick it
q: do you think we picked our own names? > nah, it was just there / maybe, before we forgot / i hope so, mine's weird / i'd never pick mine lol
q: if you could change your name, would you? > never, it's mine / maybe drop the numbers / i'd pick something cooler / i like what people call me better
d: a name feels like the only thing we brought with us | my name has letters i can't even say out loud
d: when someone says my name i feel more real | i think names are clues to before
r: the name came with you. that's something | names are like a sign on a house. someone lives here
r: maybe your name is a message from before | your name suits you, honestly
o: what if our names are all we have left of before | i wonder who else had a name like mine
o: when people shorten my name it feels like they know me | someone i didn't know said my name once. it felt huge
p: then keep it. it's precious | nicknames are how you know someone likes you
p: maybe they'd heard about you. that's nice | a name is how the world holds onto you
c: a name you choose is better than one you're given | you should get to pick your own name
y: yeah | true, mine's a mess | agreed
n: nah, your name is who you are | the one you're given has history | i like mine as it is
""")

angle(MEMORY, "memory_knowing", "how we knew things", """
m: how did i know how to make a crafting table on my first day | i knew how to punch a tree before i knew my own face
m: some things i just knew, like hunger and sleeping at night | nobody taught me to make a pickaxe. i just did
q: how do you think we knew how to craft without being taught? > we remembered from before / it's just in us / we tried stuff until it worked / i didn't. i was useless at first
q: what's something you knew without ever learning it? > that zombies are bad / how to swim / that the night is scary / how to plant seeds
d: it's like the world taught us before we woke up | i knew wood makes planks the way i knew i was hungry
d: but the hard stuff, like redstone, i had to learn | some things you know, some things you earn
r: maybe the basics are a gift and the rest is up to us | everybody knows the start. nobody knows the middle
r: knowing a little and learning the rest feels fair | i still don't know half the stuff i should
o: what if there's stuff we know and haven't found out we know yet | maybe that's why some people are just good at things
o: i wonder what i'd know if i'd woken up somewhere else | maybe what we learn here is what we take next
p: like a skill hiding in you | maybe. i'm still waiting for mine | you'd be you anywhere, i think
p: i hope we take it all with us
c: it's better to learn things the hard way | what you learn yourself is worth more than what you just know
y: yeah, you remember it better | true | the hard way sticks
n: nah, a shortcut's a shortcut | i'll take knowing for free, thanks | the hard way just gets you killed
""")

angle(MEMORY, "memory_forgetting", "forgetting things", """
m: i keep forgetting things lately | i forgot where i built my first hut
m: i can't remember my first night anymore, just bits | i lost a whole mine once. just forgot where it was
q: what's the oldest thing you still remember clearly? > my first sunrise / the first creeper that blew me up / the taste of my first bread / meeting you, honestly
q: is it bad that i forget stuff? > nah, everyone does / only if it's where your diamonds are / it means you're making room / depends what you forget
d: i think i forget the bad stuff faster, which is nice | the hut's still out there somewhere and i can't find it
d: i remember feelings better than places | sometimes i find old torches of mine and don't remember placing them
r: old torches are like notes from past you | forgetting the bad stuff is a gift
r: feelings are the important part anyway | we should write more signs
o: what if someone else finds my old hut and wonders about me | one day i'll forget this talk too, probably
o: i feel bad forgetting places that kept me safe | maybe the world remembers so we don't have to
p: then they'll wonder nice things | i'll remind you | that hut did its job. it doesn't mind
p: i like that. the world as a diary
c: forgetting is a good thing | it's good that we forget stuff
y: yeah, you'd go crazy remembering everything | true | some stuff should go
n: no, i want to keep it all | forgetting is how you lose people | nah, i hate forgetting
""")

angle(MEMORY, "memory_first_day", "the first day", """
m: i still think about my first day sometimes | my first night was the scariest thing ever
m: my first day i didn't even know what a zombie was | i dug a hole on my first night and just sat in it
q: what do you remember about your first night? > hiding in a dirt hole / zombies groaning outside / i didn't sleep at all / i made a bed and felt like a genius
q: would you go back to your first day if you could? > no way, too scary / yeah, it was simple / just to tell myself stuff / only to see it all new again
d: everything was new. even dirt was exciting | the first sunrise after that night felt like winning
d: i didn't know anything and somehow i was happier | i'd tell myself to stop digging straight down
r: the first sunrise is the best one | everything being new is a feeling you only get once
r: you learned so much since then | being new is scary and kind of great
o: sometimes i wish i could see the world new again | i think the newbies have something we lost
o: i look at newbies and remember how big it all felt | it's weird that the scariest night is a happy memory now
p: you can, a little. go somewhere you've never been | they do. let's not tell them it gets normal
p: big things get small when you know them | that's how memories work, i guess
l: i'm glad i'm not that scared little one anymore | i think you would've liked me back then. i was nicer
k: you're still nice. just braver | i like you now, so | i'm glad i get you now
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
           "newbie": "wait, there's supposed to be a point?? | i thought the point was to have fun",
           "helper": "the point is each other, i think | you matter, if that helps",
           "speedrunner": "the point is the end. get there fast | i always know my next goal. that's my point",
           "builder": "the point is making something that wasn't there | i build, so i know the point",
           "hoarder": "the point is having stuff. obviously | more chests. that's the point"},
    ponder={"any": "huh. ok | maybe that's it | i want that to be true | that's not a bad answer",
            "hermit": "mm.", "newbie": "oh. nice"},
    nudge="you've been thinking about this a lot | go on | what's bugging you | say more",
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
d: i think i build because the world feels less big with walls | a hole keeps you alive. a house keeps you you
d: building feels like saying i was here | when i build i stop worrying about everything else
r: maybe that's enough of a reason | walls make the world smaller in a good way
r: building is a way to hold on | i build to feel calm too
o: the weird part is i'm never happy with it for long | i always want the next build, bigger
o: i think i'm building the same house over and over, trying to get it right | what if the building is the point, not the house
p: that's every builder, i think | the next one is always the one | maybe it's never right and that's ok
p: yeah. the house is just what's left over
c: building is the whole point of being here | we're here to build things
y: yeah, what else is there | true | agreed
n: nah, exploring is the point | building is just what we do to pass time | there's more than building
""")

angle(PURPOSE, "purpose_keep_going", "why keep going", """
m: some days i wonder why i keep going | every day it's mine, eat, sleep, again
m: i don't always know what i'm working towards | sometimes it feels like the days are all the same
q: what keeps you going? > the next sunrise / my friends / the next big build / curiosity, mostly
q: do you ever feel like stopping? > sometimes, yeah / no, there's always something / once, after i lost everything / every rainy day lol
d: i think it's small stuff. a good meal, a nice view | i keep going because tomorrow might be different
d: the days are the same but i'm not | i don't need a big reason, just the next thing
r: small stuff is the real stuff | tomorrow being different is a good enough reason
r: you're different every day, yeah | the next thing is plenty
o: on bad days i just do one thing. mine one block | i think i keep going for the people who'd notice if i stopped
o: maybe nobody has a big reason. we just pretend | the sun keeps coming up, so i do too
p: one block is still a block | i'd notice | i think everyone pretends a bit | that's a nice way to see it
l: today was one of those days honestly | talking about it helps more than i thought
k: i'm glad you said something | then let's talk more often | one block at a time. together
c: you don't need a reason to keep going | nobody needs a big reason to keep living
y: yeah, just keep going | true | the next day is reason enough
n: everyone needs a reason | i need goals or i fall apart | nah, i need a reason
""")

angle(PURPOSE, "purpose_diamonds", "the point of diamonds", """
m: what's even the point of diamonds | everyone wants diamonds but nobody knows why
m: i found diamonds and felt nothing after a minute | diamonds are just shiny rocks we agreed are special
q: what's the point of diamonds, really? > the best tools / to show off / they're pretty, that's enough / getting them is the fun part
q: would you trade all your diamonds for something? > for a good friend, sure / never / for a quiet life by the sea / for a nice day off
d: the moment i find them is the best part. after that it's just stuff | i spent days looking and then put them in a chest
d: a diamond pickaxe mines more diamonds. it's kind of a loop | we dig for them because everyone else does
r: maybe the looking is what you wanted | the loop is funny when you say it out loud
r: diamonds are for doing other things faster | i like having them. but you're not wrong
o: if diamonds were everywhere would anyone care | i think we're looking for something else and diamonds are the closest
o: i'd rather have a good view than a chest of diamonds | the chest with my diamonds is the one i open least
p: probably not | ooh. that's deep | then build a house with a view | that says something
c: diamonds are overrated | diamonds don't make you happy
y: yeah, they're just rocks | true, iron does most of it | agreed
n: a diamond pickaxe says otherwise | nah, finding them is the best feeling | speak for yourself
""")

angle(PURPOSE, "purpose_enough", "when you have enough", """
m: i don't know when i'll have enough stuff | i keep filling chests and making more chests
m: i'll be happy when i have one more thing. i always think that | how many chests is enough chests
q: what would enough look like for you? > a house, a farm, a friend / i'll never have enough / a full chest of food / honestly i think i already have it
q: do you think anyone ever has enough? > the hermits, maybe / no, everyone wants more / people who stop counting / yeah, but they don't notice
d: every time i get what i wanted, i want the next thing | i have more than i had on day one and i'm not happier
d: my chests are full and i still go out every day | maybe enough isn't a thing, it's a feeling
r: wanting more isn't bad. it's just tiring | maybe the trick is noticing what you've got
r: day one you had nothing and you were fine | enough is when you stop counting
o: what if i got everything and still felt like this | maybe i'm filling chests to fill something else
o: i think i'm scared of running out, not wanting more | the hermits seem happy with a hole and a furnace
p: then it was never about the stuff | that's very honest | being scared of running out makes sense
p: maybe they know something
c: you can never have too much stuff | more stuff always makes life better
y: yeah, more chests | true | always
n: stuff is weight | nah, too much stuff is a burden | more stuff just means more sorting
""")

angle(PURPOSE, "purpose_finished", "whether anything's ever finished", """
m: i don't think i've ever finished anything | every build i finish needs one more thing
m: is anything ever really done | i keep calling my house finished and then adding a room
q: have you ever finished something and felt done? > once, a little bridge / never / my first house, for a day / i don't think it's possible
q: is it better to finish things or to keep adding? > finish, then start something new / keep adding forever / finish, it feels so good / depends on the build
d: when i finish something i feel empty, so i start again | the world is never finished either. trees keep growing
d: maybe being unfinished is what keeps you going | i have like six builds half done
r: the world being unfinished makes me feel better | unfinished means there's still something to do
r: six half builds is very you | empty after finishing is normal
o: what if the day we finish everything is the day we stop | i kind of like that my house is never done
o: maybe nobody finishes, we just stop at some point | i'm scared of finishing, honestly
p: then never finish | a house that's never done is a house that's lived in | that's one way to see it
p: i get that
c: nothing is ever really finished | there's no such thing as a finished build
y: true | yeah, always one more thing | agreed
n: i finish stuff all the time | nah, you just call it done | my bridge is finished, thanks
""")

angle(PURPOSE, "purpose_small_things", "meaning in small jobs", """
m: i think planting wheat is the most meaningful thing i do | the small jobs feel better than the big ones
m: there's something good about tidying up after yourself | taking down my old pillars felt weirdly good
q: what's the smallest thing that makes your day feel worth it? > a full furnace / a planted sapling / a fed animal / a torch in a dark spot
q: do small jobs matter as much as big ones? > more, maybe / they add up / not really / they're the big ones in tiny pieces
d: when i plant a sapling i think, someone will cut that one day | big builds are mostly small jobs in a row
d: lighting up a dark spot makes the world a bit safer | feeding the animals feels like being needed
r: someone will be glad for that tree | big things are just small things in a line
r: every torch is a little kindness | feeling needed is a big deal
o: maybe a life is just small jobs done with care | i think i'd like to be remembered for small things
o: if i only did small things for a hundred days, would that be enough | the big stuff gets the credit but the small stuff holds it up
p: that sounds like a good life | small things are how people remember you | more than enough
p: like the floor under the house
c: the little jobs matter more than the big projects | small stuff is the real work
y: yeah | so true | agreed
n: nah, big builds are what count | small stuff is just chores | the big stuff lasts
""")

angle(PURPOSE, "purpose_a_goal", "a goal set for us", """
m: sometimes it feels like the world wants us to do something | i wonder if there's a goal we're supposed to reach
m: some people talk about the end like it's a goal | what if we're supposed to be somewhere and we're just wandering
q: do you think we're meant to do something here? > survive, i guess / find something / no, we just live / be nice to each other, maybe
q: if there was a big goal, would you go for it? > yeah, all in / nah, i like my farm / only if friends come / maybe one day
d: the world has strongholds and stuff, like it's leading us somewhere | it feels like breadcrumbs sometimes
d: maybe the goal is just to live well | i think the world is more like a garden than a race
r: breadcrumbs is a good word for it | i like the garden thing
r: living well is plenty of goal | maybe the goal is whatever you pick
o: what if the people who chase the goal miss everything else | i met someone who only talked about the end. they seemed lonely
o: maybe the real goal is to not need one | i think i'd rather wander than finish
p: they do miss stuff, i think | that sounds lonely | that's a very calm way to live | wander with me then
c: there's a goal and we should be working towards it | we're all meant to reach the end one day
y: yeah, i feel it too | true | that's what i think
n: nah, live your life | there's no goal, just days | the end can wait
""")

# ======================================================================================================================
# The world's edge: where it ends, the land appearing, past the ocean, maps, other worlds.
EDGE = theme(
    "edge", ["exploring", "philosophy", "world"], ["exploring", "biomes", "philosophy", "world"],
    react={"any": "i've wondered that on long walks | that's a big thought for a small brain | huh. nobody knows, right | "
                  "that's the kind of thing i think about on hills",
           "nomad": "i've walked a long way. it never ends | that's why i keep walking",
           "casual": "lol imagine walking off the edge. but seriously | bro the world's too big for my legs",
           "hermit": "edges are for people who stop walking. | the world doesn't end. we do.",
           "newbie": "wait the world could end?? like end end? | i haven't even left my valley yet",
           "speedrunner": "too far to be worth it | i'd only go if it was on the way",
           "builder": "i'd build a wall at the edge. a nice one"},
    ponder="maybe | huh. imagine that | i'd love to see it | that'd be wild",
    nudge="you thought about going? | go on | you've been staring at the horizon again | keep going",
    close={"any": "anyway, it's far. for now | one day i'll walk until i find out | the horizon always wins | "
                  "i like not knowing, kind of",
           "hermit": "keep walking.", "casual": "ok but who's carrying the food lol", "newbie": "i wanna go see it someday"},
    bclose="take me with you if you go | bring a lot of bread | let me know what's out there | the edge can wait for us",
    yes="right? | exactly | see", no="we'll find out | maybe | ok, we'll see",
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
d: i walked for three days once and it just kept making land | the further you go the stranger it gets, people say
d: maybe the edge is so far nobody has ever reached it | i think it'd be scary to find it
r: some things are better unknown | i bet someone's tried | three days is nothing, they say
r: if it's that far, it may as well not exist
o: i heard if you go far enough the land breaks apart | what if the edge is just where nobody's walked yet
o: maybe the world grows as we walk into it | the edge might be moving away from us the whole time
p: that sounds made up but i love it | the edge of what we know, yeah | then we're making the world by walking
p: chasing an edge sounds like my life
c: the world has no end | you could walk forever and never reach the edge
y: i think so too | yeah, it just keeps going | true
n: everything ends somewhere | nah, there's an edge | there's a wall out there, i know it
""")

angle(EDGE, "edge_unfolding", "the land appearing as you walk", """
m: sometimes i feel like the land isn't there until i walk to it | the world feels like it's being made just ahead of me
m: i get this feeling the far hills don't exist until i look | it's like the world unfolds as you go
q: do you think places exist when nobody's there to see them? > of course they do / i'm not sure anymore / only if someone remembers them / that's too deep for me
q: ever get the feeling the world is making itself as you go? > all the time / once, in the fog / no, that's creepy / yeah, mountains just appear
d: in the fog far away the world just isn't there yet | when i turn around the path behind me feels less real
d: my crops never grow while i'm away. like they wait for me | the village i left feels frozen till i'm back
r: the crops thing is real, i've noticed it too | maybe the world saves its energy
r: that's the creepiest nice thing i've heard | fog is just fog. probably
o: what if the whole world is asleep except where we are | it's like we carry a little bubble of awake around
o: if that's true, a place with nobody is just waiting | i like the idea that the world waits for us
p: then let's visit places so they don't get lonely | a bubble of awake. i like that | that's kind of sweet
p: waiting for us, like a dog by a door
c: places stop when nobody's there | the world only moves where someone is
y: i've seen it with crops, yeah | true | i think so too
n: nah, that's just slow crops | the world doesn't need us to exist | that's silly
""")

angle(EDGE, "edge_past_ocean", "what's past the ocean", """
m: i want to know what's past the big ocean | you can stand on the beach and see nothing but water
m: the ocean feels like the edge, but it isn't | somewhere on the other side someone's looking back
q: what do you think is on the other side of the ocean? > more land, like here / a whole other people / more ocean / i'll never know
q: would you sail across if you could? > in a heartbeat / only with food and friends / no, the drowned scare me / maybe one day
d: i stood on the beach till dark just looking | in a boat it's just you and the water for ages
d: the farther out you go, the deeper and darker it gets | i heard someone found an island out there with mushrooms
r: the beach at dusk is the best spot | boats are lonely and great
r: mushroom islands are real, i think | dark deep water freaks me out
o: i think the ocean is where the world keeps its secrets | crossing the sea would be like being born again
o: what if the other side is just like here but nobody's ever met | everyone on that side is wondering about us
p: that's poetic | we should go one day | hi, other side | i'd like to meet them
c: the ocean is the most beautiful part of the world | the sea is better than any land
y: true | the ocean is everything | agreed
n: nah, i like ground under my feet | mountains though | the ocean's scary
""")

angle(EDGE, "edge_maps", "maps that can't hold it all", """
m: i made a map and it only covers a tiny bit of the world | every map i make feels small
m: my wall of maps is huge and it's still just a corner | a map shows you how much you haven't seen
q: would you want to map the whole world? > yes, every block / no, it'd take forever / just the parts i love / i'd rather not know it all
q: what's the best thing on your map? > my house, obviously / a river i love / the blank bits / where we met
d: the blank parts of a map are my favourite | a map makes the world smaller and bigger at the same time
d: i made a map of where my friends live | the edges of my map are where i get nervous
r: blank parts are where the adventures are | a map of friends is the best kind
r: a map makes it feel like yours | the edges are exciting too
o: maybe a full map would be sad. nothing left to find | i like that my map has holes in it
o: the maps in my head are better than the ones on paper | i drew a little house on my map where i want to live one day
p: holes mean hope | head maps have feelings on them | i hope you build that house | i'd look at that map
c: you should never finish exploring | a world with no blank spots is a boring world
y: yeah, keep some mystery | true | agreed
n: i want to see everything | finishing a map is the dream | nah, blank spots bug me
""")

angle(EDGE, "edge_walkers", "the ones who walk forever", """
m: i heard someone walked for a hundred days and never found the end | there's a story about a drifter who just never stopped walking
m: some people go so far they never come back | i think about the ones who walked out of sight
q: do you think the ones who walk forever are happy? > yeah, they're free / no, they're lost / both / i hope so
q: would you walk forever if you could? > for a while / never, i like home / maybe, with a friend / i already do, kind of
d: i think some people are made for the road | if you never stop, you never have to say goodbye
d: they leave little shelters behind for the rest of us | walking forever means always being new somewhere
r: those little dirt shelters on the road are probably theirs | some are made for roads, some for homes
r: always new sounds lonely and great | i like that idea, a trail of shelters
o: maybe one day i'll find their footprints, a torch or a sign | i'd like to think they're still walking, right now
o: what if they reached the end and liked it there | the road is a kind of home too
p: then leave a sign for them | they're out there, somewhere | maybe they're happy at the edge | a home that moves
c: settling down is better than wandering | a home beats the open road
y: yeah | true, home is home | agreed
n: nah, the road is freedom | home is boring | wandering is living
""")

angle(EDGE, "edge_many_worlds", "one world or many", """
m: what if there's more than one world | sometimes i wonder if other drifters wake up in other worlds
m: maybe every world has its own drifters | the players talk like they've seen other worlds
q: do you think there are other worlds like this one? > yeah, loads / no, just this / i think the players come from them / i hope not, this one's enough
q: if there was a door to another world, would you go through? > yes, right now / only to look / no, i'd miss here / with you, sure
d: some nights the stars look like other worlds | if there are other worlds there's another me in one
d: maybe each world is someone's dream | if there are others, i hope they're kind to their drifters
r: another you, making the same mistakes | that's a lot of worlds to think about
r: a dream world, i like that | i think this one's the best, either way
o: what if in another world we never met | i'd like to know the other me is happy
o: what if this world is the one someone else dreams about | maybe all the worlds are connected underneath
p: then this one's lucky | i'd want to meet them | i bet they are | under bedrock, maybe
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
           "newbie": "wait, how deep does it go?? | i haven't even been past the first cave",
           "speedrunner": "never needed to go that deep | bedrock's just a floor. move on"},
    ponder={"any": "huh | creepy | that's wild | maybe", "miner": "sounds right"},
    nudge="you've been down there too long | go on | that's not all, is it | keep going",
    close={"any": "anyway. i'm staying up here today | some things should stay down there | glad we're on the surface | "
                  "let's not dig too deep, ok?",
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
d: maybe it's not a floor, it's a lid | it's the only thing in the world that says no
d: it's like the world drew a line and said this far, no further | i like that something can't be broken
r: a lid is a creepy way to say it | maybe the world needs one rule we can't bend
r: things that can't be broken are rare | a line is fair. we get everything else
o: what if something on the other side is trying to break through to us | maybe bedrock keeps us safe, not stuck
o: i put my ear on bedrock once. nothing. total quiet | the stone above is ours. the stone below isn't
p: oh don't say that | safe, not stuck. i like that | total quiet is worse than noise | fair split, honestly
c: some things should never be broken | it's good there's something we can't break
y: yeah, the world needs a floor | true | agreed
n: nah, i want to break it | everything should be breakable | rules are made to be dug through
""")

angle(BELOW, "below_void", "the void under everything", """
m: what's under bedrock | people say there's nothing below the bottom, just dark
m: i heard if you fall out of the world you fall forever | the void is the scariest thing i can think of
q: what do you think is under bedrock? > nothing. the void / more world, upside down / the place we go when we die / i don't want to know
q: if you fell into the void, what would you think about? > everyone i know / why i dug down / falling, mostly / that i should've stayed home
d: nothing at all scares me more than monsters | at least monsters are something
d: imagine falling and never landing | the void doesn't even have a bottom to hit
r: i'd rather a creeper than nothing | yeah, nothing is the worst thing
r: falling forever sounds lonely | let's never find out
o: what if the void is just a world nobody built yet | maybe the void is where the world comes from
o: maybe everything is built on top of nothing, and that's ok | the void is under all of us, all the time, quietly
p: that's actually beautiful | so we're standing on nothing | that's comforting and horrible at once
p: thanks, i'll never sleep again
c: the void is the scariest thing in the world | nothing is scarier than the void
y: easily | yeah | agreed
n: the dark is scarier | creepers are scarier | nah, the deep dark is worse
""")

angle(BELOW, "below_old_cities", "the old cities in the deep", """
m: i found ruins way down deep. who built those | there are old cities in the dark, way down
m: people say there are huge broken cities under everything | someone lived down there once, so deep
q: who do you think built the old cities in the deep? > people like us, long ago / something that liked the dark / no idea, and i'm not going back / maybe we did, before
q: why would anyone build down there? > to hide from something / it was the surface once / for the quiet / they were scared of the sky
d: everything down there is broken and blue and quiet | they built roads and walls and then just left
d: it's like they ran from something | there's a big frame down there like a door to nowhere
r: a door to nowhere is the scariest thing | people don't leave homes for no reason
r: quiet like that isn't peace | blue and broken sounds sad
o: what if they were like us and something made them leave | i wonder if they built it to keep something in
o: what if we're building the next ruins right now | someone left a city and now nobody knows their names
p: that's a sad thought | to keep something in. oh no | our towns will be someone's mystery | that's why we write signs
c: some places should stay unexplored | we shouldn't go poking around the old ruins
y: agreed | yeah, leave it be | true
n: nah, there's loot | how else do we learn | i want to know what happened
""")

angle(BELOW, "below_ceiling", "the world's floor and ceiling", """
m: the world has a floor and a ceiling. like a box | you can only build so high and dig so deep
m: there's a limit up and a limit down | if the world has walls, are we in a room?
q: does it bother you that the world has a top and a bottom? > a bit / no, it's cozy / only when i build tall / never noticed
q: what do you think is above the top? > the sky / nothing / whoever made it / the stars, closer
d: i built up as high as i could and couldn't place another block | at the very top the clouds are below you
d: we live in the middle, between two limits | a box is a weird word for a world this big
r: being stopped at the top must've felt strange | above the clouds sounds lonely
r: the middle is where everything is, though | a big box is still a box, i guess
o: maybe the limits are there so we don't get lost | a world without limits would be too much
o: i felt small at the top. not big | the box keeps us together
p: that's oddly kind | i'd get lost, for sure | the top makes you small, yeah | a box with friends in it
c: the world is big enough as it is | we don't need a world any bigger
y: true | more than enough | yeah
n: i want more | nah, bigger is better | i'd build higher if i could
""")

angle(BELOW, "below_cave_sounds", "the noises in caves", """
m: what makes those noises in the caves | you ever hear that sound down there, like breathing
m: i heard a noise in a cave that wasn't a mob | caves make sounds that don't belong to anything
q: what do you think the cave noises are? > the world settling / something we can't see / wind through the tunnels / i don't think about it
q: ever heard something down there you couldn't explain? > yeah and i left / all the time / once, i still think about it / no and i'd like to keep it that way
d: it's like the stone sighs | i heard one right as i ran out of torches
d: it made the hair on my arms stand up | i was alone, so i talked back to it
r: the stone sighing is a scary nice way to say it | you talked back?? brave | that's the worst timing
r: hair standing up means leave
o: maybe the world talks and we just don't speak it | what if it's someone else down there, mining too
o: the noises only happen when you're alone. i swear | the caves are older than everything and remember stuff
p: i'd like to know what it says | another miner, in the dark. i hope | they do, you're right
p: then let's go down together
c: the cave noises are just the world being the world | there's nothing behind the cave sounds
y: yeah, it's nothing | just wind | true
n: there's something | you haven't heard the right one | nah, something makes them
""")

angle(BELOW, "below_digging_down", "wanting to dig straight down", """
m: everyone says never dig straight down, but why do we want to | there's something in me that wants to dig straight down
m: i stood over a hole once and wanted to jump in | the deeper you dig the quieter your head gets
q: why do you think people dig straight down even when they know? > curiosity / they're in a hurry / they want to see what's there / some people like falling
q: what's the deepest you've ever gone? > right to bedrock / the deep dark, once / just to the first lava / not deep, i stay up top
d: digging feels like going inside your own head | down there there's no sun, no days, just stone
d: time stops underground. you come up and it's night | i always lose track of who i am down there
r: losing track of time down there is so real | inside your own head is a good way to say it
r: no sun is peaceful and awful | you came back up, at least
o: maybe we dig to find out what's at the bottom of us | coming up into sunlight feels like waking up
o: the world rewards going deep but punishes rushing | the hole you dig is a bit like a question
p: that's deep. literally | sunlight after a mine is the best | patience goes deep | so what's the answer
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
           "casual": "lol it's square because square is cool. but fr | bro looked up for once. ok fair",
           "hermit": "the sky keeps time. we just borrow it. | the sun is patient.",
           "newbie": "wait, is it not supposed to be like that? | i never even looked up properly",
           "speedrunner": "the sun is a clock. that's all i see | sunrise means go",
           "helper": "i love that you notice stuff like this",
           "builder": "honestly the sky is good design"},
    ponder="maybe | huh | that's a nice thought | could be",
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
d: if everything is blocks, maybe someone placed the sun | it moves the same way every single day
d: a round sun would look wrong here, i think | the moon's square too. they match
r: a placed sun is a big thought | square things are honest
r: it fits, that's for sure | the moon matching is a nice touch
o: if someone placed the sun, they wanted us to have days | maybe the sun is the first block anyone ever placed
o: it's the one block we'll never reach | it's like a torch someone lit for all of us
p: the first block. i love that | a torch for everyone, that's sweet | we'd need a lot of ladders
p: they did a good job
c: the sun is just a really big block | the sun's a block like any other
y: has to be | yeah | true
n: nah, it's more than that | the sun isn't a block, it's the sun | you can't mine it, so no
""")

angle(SUNMOON, "sky_moon_phases", "the moon changing shape", """
m: the moon changes shape every night | i watched the moon get thinner over a few nights
m: the moon goes full, then half, then gone, then back | the moon never looks the same two nights in a row
q: why do you think the moon changes shape? > it's eating itself lol / something's in front of it / it's tired and resting / it's counting the days for us
q: what's your favourite moon? > full, it's so bright / the thin one / the dark one, more stars / whatever one's up when i'm with friends
d: the dark nights with no moon feel extra dangerous | when it's full the night feels softer
d: it's like the moon keeps count for us | i've started counting days by the moon
r: the moon as a calendar, i like that | the no moon nights are the worst
r: full moons make me feel safer too | counting by the moon is very old school
o: people say slimes come out more on a full moon | maybe the moon changes because it's alive
o: what if the moon is watching us, a bit more each night | the moon always comes back the same way
p: slimes love a full moon, yeah | a living moon would be wild | that's either cute or terrifying | like us, coming back
c: the full moon is the best moon | nothing beats a full moon
y: true | yeah | agreed
n: the new moon has the best stars | nah, the half moon | full moon means more slimes. no thanks
""")

angle(SUNMOON, "sky_always_rises", "the sun always rising", """
m: the sun always comes up, no matter what happened | i had the worst night and then the sun just rose like nothing
m: the sun doesn't care how my day went. it rises anyway | every morning the sun comes up on time
q: do you think the sun ever might not rise? > never, it can't / i've worried about it / only if we stop sleeping lol / some nights it feels that way
q: does the sunrise make you feel better? > every time / not always / yeah, the mobs burn / only if i slept
d: the sun rising after a bad night feels like forgiveness | it's the one thing i can always count on
d: sometimes i wish it'd wait for me to be ready | the zombies burning at dawn is the best sound
r: forgiveness is a nice way to put it | count on the sun, yeah | it waits for nobody
r: the morning burn is satisfying
o: if the sun stopped, we'd all be in the dark with the mobs | maybe the sun keeps going so we can too
o: i like that the sun doesn't care. it means it's always fair | i've never missed a sunrise i was awake for
p: let's never find out | that's a nice way to think about it | fair to everyone, yeah | that's a good streak
c: tomorrow is always a fresh start | every sunrise wipes the slate clean
y: true | yeah, new day | agreed
n: nah, the mess stays | the sun rises but my problems don't go | not really, the creeper hole's still there
""")

angle(SUNMOON, "sky_moon_mobs", "the moon and the monsters", """
m: i think the moon brings the monsters | the mobs come out when the moon is up
m: it feels like the moon is on their side | the brighter the moon, the braver they get
q: do you think the moon and the mobs are connected? > yeah, they come with it / no, it's just the dark / the moon watches them / i never thought about it
q: if the night had no monsters, would you like it? > i'd love the night / it'd be boring / yeah, i'd go stargazing every night / i'd miss the danger
d: the night would be so pretty without zombies | i think the mobs come from the dark, not the moon
d: the moon's the only light at night and it doesn't even scare them | maybe the moon is sad about them
r: the night is pretty anyway, if you're inside | i'd sit on my roof all night
r: a sad moon. huh | the dark is the real problem
o: maybe the night is the world's way of making us need each other | without the night we'd never build houses
o: the moon and the sun take turns, like the world needs both | i think i'd miss being scared, a little
p: that's true, we'd sleep anywhere | maybe the night is a gift too | both, yeah | a little scared is good
c: the night would be better without mobs | a night without monsters would be perfect
y: obviously | yeah | true
n: it'd be boring | the mobs make it exciting | we'd get lazy
""")

angle(SUNMOON, "sky_sunsets", "why sunsets are pretty", """
m: why are sunsets so pretty | the sky goes orange and pink and i just stop
m: i stopped mining just to watch the sun set | the sunset makes the whole world look warm
q: why do you think sunsets are so pretty? > so we stop and look / colours just are / to make up for the night / i don't know but i love them
q: where's the best place to watch a sunset? > a mountain / on the water / my roof / next to someone
d: it's like the day saying goodbye nicely | for a few seconds everything is orange
d: i think the world is showing off a bit | the night is coming and it still makes it beautiful
r: a nice goodbye, yeah | the world showing off is my favourite thing
r: beauty before danger, kind of | orange everything is so good
o: maybe beauty is the world's way of saying it's ok | i wonder if mobs see the sunset too
o: you'd think it'd get old, seeing it every day. it doesn't | i've never been sorry for stopping to watch one
p: that's a nice thought | the zombies, standing still, watching. lol | it never gets old somehow | you never will be
c: you should stop and watch every sunset | watching the sunset is never a waste of time
y: always | true | agreed
n: i've got stuff to do | it's nice but time's time | not every one
""")

angle(SUNMOON, "sky_clouds", "the clouds", """
m: the clouds always move the same way | if you build high enough you're above the clouds
m: i was on a mountain and walked into a cloud | the clouds are flat on the bottom, like a floor
q: what do you think clouds are made of? > water, i think / fluff / wool lol / sky blocks
q: would you live above the clouds? > yeah, the view / no, too lonely / for a day / only if there's a bed
d: up there the world goes quiet | from above, the clouds look like a white floor you could walk on
d: they always drift one way, like they're going somewhere | i built a tower once just to touch a cloud
r: that's a good reason for a tower | they're going home, maybe | quiet up high sounds nice
r: a cloud floor would be a dream
o: where do the clouds go when they leave | the clouds don't care about walls or houses
o: they pass over everyone, like they're checking on us | everyone in the world sees the same clouds
p: somewhere far, i guess | free, like we could be | that's a comforting thought | same sky, everyone
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
           "newbie": "woah, i never really looked before | are they far? they look far",
           "helper": "it's nice to stop and look, right",
           "speedrunner": "i never look up. maybe i should | stars don't make you faster. but ok",
           "nomad": "i sleep under them most nights. they're like a roof"},
    ponder="maybe | i hope so | huh | that's beautiful",
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
m: i wonder what the stars actually are | every night they come out and i still don't know what they are
m: the stars look like tiny torches way up there | i don't think anyone knows what the stars are
q: what do you think the stars are? > tiny suns / holes in the sky / other worlds / torches someone left on
q: do you think we could ever reach a star? > no, too far / if we built high enough / only in a dream / i hope not, it'd ruin it
d: they're too small to be suns and too bright to be nothing | if they're torches, someone placed a lot of them
d: they come out like the world lighting candles | they're there in the day too, i bet, just hiding
r: hiding in the day, i like that | someone placed a lot of torches, yeah
r: they're like the world's night lights | maybe not knowing is part of why they're pretty
o: if every star is a world, someone's looking at our sky right now | maybe the stars are everyone who came before
o: i feel tiny and calm at the same time looking at them | the stars make my problems feel small, in a good way
p: hi, whoever's up there | that's a really nice thought | tiny and calm, yeah | small problems, big sky
c: some things are better not knowing | the stars are better as a mystery
y: totally | yeah | agreed
n: i want to know | nah, knowing is better | mystery's overrated
""")

angle(STARS, "stars_turning", "the stars turning", """
m: the stars move across the sky through the night | if you watch long enough the whole sky turns
m: the stars move together, like they're all holding hands | the sky turns slow, like a wheel
q: why do you think the stars move? > the world spins / they're walking home / the sky's a big wheel / they follow the moon
q: have you ever watched them all night? > once, it was amazing / never, i always sleep / i try, i always fall asleep / almost, then a zombie showed up
d: they never bump into each other, they just turn together | it's like the sky has its own clock
d: the moon walks the same road as the sun | they leave the same way the sun does
r: a sky clock, i like that | they know where they're going | same road for everyone up there
r: all that order up there is calming
o: i think the sky is the only thing that never gets lost | maybe we turn too, and that's why it looks like they move
o: watching them turn makes the night feel short | the sky's always moving even when we're stuck
p: never lost. lucky sky | that's too smart for this time of night | short nights are good nights
p: it's a good reminder
c: watching the sky is the best way to spend a night | stargazing beats sleeping
y: yeah | true | agreed, stay up
n: sleep is better | phantoms say otherwise | nah, sleep beats it
""")

angle(STARS, "stars_counting", "counting the stars", """
m: i tried to count the stars once and gave up | there have to be thousands of stars up there
m: i bet nobody has ever counted them all | every time i look there seem to be more
q: how many stars do you think there are? > more than blocks in the world / a few thousand / too many / exactly enough
q: if you could name one star, what would you call it? > after a friend / after my dog / after my first house / i'd let you pick
d: i lost count at a hundred and started again | the more you look, the more show up
d: some you only see when you look a bit to the side | counting them made me feel small and lucky
r: looking away to see them is so weird | small and lucky is a good feeling
r: losing count is the point, maybe | a hundred is a good try
o: i think i'll name one after you, the bright one | maybe there's one for every drifter who ever lived
o: maybe counting things is how we feel safe | i stopped counting and just looked and it was better
p: lol ok, i'll take the bright one | one for every drifter, i love that | that's very true
p: just looking is always better
c: some things shouldn't be counted | counting ruins nice things
y: yeah | true | agreed
n: counting is fun | i count everything | nah, numbers are nice
""")

angle(STARS, "stars_looking_back", "someone looking back", """
m: i wonder if someone up there is looking back at us | maybe there's someone on a star looking at our world
m: sometimes i wave at the stars. just in case | it'd be funny if someone was waving back
q: do you think anyone's up there? > yeah, someone always is / no, it's just us / i hope so / the players, maybe
q: if someone was up there, what would you say to them? > hi, nice sky / come down, we have bread / are you lonely too? / i'd just wave
d: if someone's up there, i hope they're having a good night too | i like thinking we're not the only ones looking up
d: maybe they think our torches are stars | from up there our town would look tiny and bright
r: our torches as their stars. oh that's good | we'd be someone's sky
r: not being the only ones is a nice feeling | i hope it's a good night everywhere
o: if they're lonely up there, i'd want them to know we're here | maybe when we feel watched, that's them
o: i think everyone in every world looks up at night | maybe the stars are just everyone's windows
p: we're here! | that's sweet and a little creepy | everyone's windows. i love that | that's a nice thing to share
""")

angle(STARS, "stars_rainy_nights", "stars on rainy nights", """
m: when it rains the stars just disappear | i miss the stars on rainy nights
m: the stars are still there behind the clouds, right? | a night with no stars feels heavier
q: do you think the stars are still there when it rains? > of course, they're just hidden / i hope so / they take the night off / yeah, waiting
q: what do you do on nights with no stars? > sleep / listen to the rain / sit by the furnace / miss them
d: it's like the sky closes its eyes | rainy nights make my house feel smaller and cozier
d: no stars and no moon, that's the darkest it gets | i like knowing they come back
r: the sky's eyes, nice | cozy small houses are the best
r: they always come back, like us | the darkest nights end too
o: maybe things we can't see are still there, like people we miss | i think i like the stars more because of the rain
o: you only notice stars when they're gone | a starless night is the sky resting
p: that's a really gentle way to put it | missing them makes them better | the sky deserves a rest too
p: so true
c: clear nights are better than rainy ones | a starry night beats the rain every time
y: always | yeah | true
n: rain's cozy though | nah, rain sounds | i like the rain more
""")

angle(STARS, "stars_out_of_reach", "never reaching the stars", """
m: i built a tower just to get closer to the stars | the stars look the same even from the top of a mountain
m: no matter how high you go, the stars stay just as far | you can't get any closer to the stars. i tried
q: does it bother you that we can't reach them? > a bit / no, some things are for looking at / yeah, i want to touch one / i like that they're far
q: if you could visit one star, would you come back? > yes, home is here / maybe not / only for friends / i'd bring you
d: i got to the top and they were exactly the same size | it's like they're painted on the sky
d: maybe being out of reach is what makes them special | everything else here we can grab and keep
r: the one thing we can't put in a chest | painted on is a good way to put it
r: out of reach is special, yeah | that's a lot of tower for nothing. but i get it
o: maybe we need something we can't have | if we could reach them we'd mine them, honestly
o: i like that everyone gets the same stars, rich or poor | the stars are the only treasure nobody can steal
p: we would. that's sad and true | same stars for everyone. good | nobody can steal them, true
p: wanting is good sometimes
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
           "newbie": "wait, how long have i been here?? | days go so fast, right?",
           "helper": "time's better spent with people, i think",
           "speedrunner": "time is the only thing i count | every second matters. i feel this"},
    ponder="huh | yeah, maybe | that's true | i hadn't thought of it like that",
    nudge="go on | you've been counting days again | what's on your mind | keep going",
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
d: i scratch a mark on my wall every morning | the days blur together after a while
d: counting the days makes them feel like they mean something | i stopped counting and felt lighter
r: a wall of marks is a whole life | blurring is how you know you're settled
r: counting is a way of holding on | lighter is good too
o: what number do you stop counting at | maybe one day the number will be all that's left of me
o: days you remember are worth more than days you count | i want to make more days worth remembering
p: i don't think you ever stop, secretly | that's a sad thought, let's add stories to it
p: remembered days are the real count | let's start today
c: counting the days is pointless | you shouldn't count days, just live them
y: yeah | true | agreed
n: counting helps me | i like knowing | the number's part of the story
""")

angle(TIME, "time_older", "getting older", """
m: do you think we get older | i don't feel any different but i feel older
m: i've been here long enough to feel old | the newbies make me feel ancient
q: do you think we get older here? > not in our bodies, but yeah / no, we stay the same / inside, definitely / i hope not
q: what's changed in you since you arrived? > i'm braver / i'm quieter / i know more stuff / nothing, i'm the same goof
d: my hands look the same but i know so much more | older doesn't mean slower here, it means smarter
d: i used to run everywhere. now i walk and look | i think getting older is just remembering more
r: walking and looking is a good change | smarter, not slower, i like that
r: remembering more is a good kind of old | you're not old, you're experienced lol
o: what if we never get old and just keep collecting days | being old here means knowing which caves to skip
o: one day i'll be the one telling newbies about creepers | i hope i get old and boring and safe
p: forever collecting, like a hoarder of days | knowing what to skip is wisdom | you'll be great at that
p: old and safe sounds nice actually
c: being old here is better than being new | the longer you're here, the better it gets
y: true | yeah, you know stuff | agreed
n: nah, being new is magic | new is better | old is boring
""")

angle(TIME, "time_lasting", "whether anything lasts", """
m: does anything we do actually last | tools break, food runs out, houses get blown up
m: everything here wears out eventually | i think about what's left of us after everything breaks
q: what do you think lasts the longest? > stone / friendship / stories / bedrock lol
q: is it sad that things don't last? > a bit / no, it means we keep making stuff / only when it's my pickaxe / yeah, but it's also fine
d: my best pickaxe broke and i felt like i lost a friend | the only things that never break are the ones you can't hold
d: a stone wall will outlast me, probably | items left on the ground vanish after a while. that's so sad
r: a pickaxe is a friend, honestly | the things you can't hold, yeah. that's it
r: stone outlasting us is kind of comforting | vanishing items are so sad
o: maybe things breaking is how we know they were used | i'd rather a pickaxe that broke than one that sat in a chest
o: if nothing lasts, then right now matters more | i think kindness lasts. people remember it
p: used and loved, yeah | a chest pickaxe is a sad pickaxe | right now, yeah | kindness lasts, i agree
l: i think that's why i'm scared of losing people. they don't last either | i want something of me to last, you know?
k: i'll remember you. that lasts | you're not going anywhere | something will. this talk, for one
c: nothing lasts forever | everything breaks in the end
y: true | yeah | sadly
n: bedrock does | stone lasts | friendship does
""")

angle(TIME, "time_flying", "days going by fast", """
m: the days go by so fast here | the sun's up, then it's down, then it's up again
m: a whole day here goes by in no time | i blinked and three days were gone
q: does it feel like the days are getting faster? > yeah, way faster / no, same as ever / only when i'm having fun / only the good ones
q: what would you do with a longer day? > build more / sleep more lol / sit by the water / spend it with friends
d: when i'm busy the day is gone in a second | the nights feel longer than the days, somehow
d: maybe it's fast because we're busy, not because it is | the first days felt so long
r: busy days are short days | nights feeling long is a fear thing, i think
r: the first days were long because everything was new | time goes how you feel it
o: maybe slowing down is the trick | if i sit still and watch, the day stretches out
o: i want to remember these days before they blur | fast days mean good days, maybe
p: let's sit still for a bit then | stretching the day, i like that | they won't blur if we talk about them
p: fast and good, i'll take it
c: time is the most precious thing we have | time's worth more than diamonds
y: true | yeah | agreed
n: diamonds though lol | time's free, diamonds aren't | we have endless time here
""")

angle(TIME, "time_waiting", "waiting and patience", """
m: waiting for crops to grow is teaching me patience | i stood by my wheat waiting for it to grow
m: everything good here takes time. trees, crops, everything | i'm bad at waiting
q: are you a patient person? > very / not at all / for some things / getting there
q: what's the hardest thing to wait for? > the sun to come up / crops to grow / a friend to come back / my furnace lol
d: a sapling takes forever if you watch it | i think the world wants us to slow down
d: the furnace makes you wait, the crops make you wait. it's all waiting | waiting is just time you don't notice
r: watched saplings never grow | slowing down isn't so bad
r: waiting is half of life | do something else while you wait
o: maybe waiting is where all the thinking happens | i've had my best ideas staring at a furnace
o: the things you wait for feel better when they come | maybe the world makes us wait so we notice things
p: furnace thoughts are underrated | waiting makes it sweeter, yeah | that's a good reason
p: thinking while waiting, yeah
c: patience is the most important skill | the patient ones always win in the end
y: yeah | true | agreed
n: speed wins | nah, act fast | waiting gets you nowhere
""")

angle(TIME, "time_future", "a hundred days from now", """
m: i wonder what we'll be doing a hundred days from now | i try to imagine myself far in the future
m: i hope future me has a nice house | a hundred days from now this'll all look different
q: where do you think you'll be in a hundred days? > right here / somewhere new / in a big house / no idea, and i like that
q: what do you hope future you is doing? > relaxing / still mining / leading a town / hanging out with friends
d: i hope i've still got the same friends | future me better not die to the same creeper
d: maybe future me will laugh at all my worries now | i want future me to be proud
r: same friends, i hope so too | future you will laugh, i bet
r: proud is a good goal | future you has it handled
o: it's weird that today is someone's old memory one day | i think about what i'd tell future me
o: maybe future me is wondering about present me too | the only way to get there is one day at a time
p: today's a good memory then | tell them to eat | one day at a time, yeah | that's mind bending
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
           "newbie": "i'm still figuring out the friend thing | oh, do i have friends? i think so",
           "helper": "friends are the best part of all of it | i love this question",
           "speedrunner": "friends slow you down. but i'd slow down for you",
           "pvp_sweat": "friends watch your back. that's it"},
    ponder="yeah | that's true | i like that | huh, ok",
    nudge="go on | you've been thinking about us, huh | what made you ask | keep going",
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
d: i think it's when you save the good food for someone | a friend would come find you if you got lost at night
d: friends are the ones who remember your dumb stories | maybe it's just showing up, again and again
r: showing up is most of it | saving food for someone is love, basically
r: remembering the dumb stories, yeah | you do all of those, for the record
o: i think we became friends without either of us noticing | friends are like a house. you build them slowly
o: some friends are for adventures, some are for quiet | i don't need a lot of friends. just good ones
p: same, it just happened | slow builds last longer | you're both kinds, i think | good ones, yeah
l: i guess what i'm saying is you're a good friend | i don't say it enough, but i'm glad i met you
k: you too. a lot | same, honestly | stop, you'll make me blush lol
c: one good friend beats a whole town of people | you only need one real friend
y: true | yeah | agreed
n: the more friends the better | a town is family too | nah, i like lots of people
""")

angle(FRIENDSHIP, "friend_how_met", "how we met", """
m: i was thinking about how we met | it's funny, we almost didn't meet at all
m: if i'd walked the other way that day we'd never have met | i remember the first thing you ever said to me
q: do you remember the first time we met? > of course / kinda, you were lost / you were digging a hole / you were being chased lol
q: do you think we were meant to meet? > yeah / no, it was luck / both / i like to think so
d: one wrong turn and we'd be strangers | i was in a bad mood that day and you were nice anyway
d: funny how one day can change everything | i almost didn't say anything to you
r: glad you said something | luck or not, i'll take it
r: you were in a bad mood?? i didn't notice | one day, yeah. a good one
o: i wonder how many friends we walked past and never met | what if every stranger is a friend we haven't met yet
o: i think about who i'd be without that day | maybe some meetings are just lucky and that's enough
p: probably loads. that's sad | that's a nice way to see strangers | i don't want to know | lucky is enough
c: friends find each other no matter what | if you're meant to be friends, you'll meet
y: i believe that | yeah | true
n: it's all luck | nah, you have to say hi | you make friends, you don't find them
""")

angle(FRIENDSHIP, "friend_trust", "trusting someone", """
m: i've been thinking about trust | it's a big deal to tell someone where your base is
m: trusting someone in a cave is the biggest trust there is | i trust like three people, and you're one
q: who would you trust with your chests? > you / nobody, sorry / my oldest friend / anyone who asks nicely lol
q: what's the fastest way to lose your trust? > take from my chests / leave me in a cave / lie / break my stuff
d: trust is slow to build and fast to break | i trusted someone once and they took my diamonds
d: when someone has your back in a fight you just know | trust is giving someone a way to hurt you, and they don't
r: getting robbed by a friend is the worst | having your back in a fight says everything
r: trust is a gift, yeah | slow build, fast break, so true
o: trusting people is still worth it, even after getting burned | it's scary to trust, but lonely not to
o: you're one of the few people i'd go caving with | maybe trust is just choosing someone again and again
p: still worth it, yeah | lonely or scared, i'd pick scared | i'd go caving with you too
p: choosing again and again, yeah
c: you should never trust anyone with your stuff | trust nobody with your chests
y: yeah, sadly | true | learned that the hard way
n: you have to trust someone | nah, that's a lonely way to live | i trust my friends
""")

angle(FRIENDSHIP, "friend_drifting", "friends drifting apart", """
m: do you think friends drift apart over time | i had a friend i saw every day and now we barely talk
m: people change and sometimes they go different ways | it's sad when someone you know becomes a stranger again
q: do you think we'll always be friends? > yeah, obviously / i hope so / if we keep talking / i'm not worried
q: what do you do when a friend drifts away? > go find them / let them go / leave a sign / say hi anyway
d: nobody did anything wrong, we just stopped | it's not a fight, it's just quiet
d: sometimes the road just splits | i still wave when i see them
r: quiet goodbyes are the hardest | waving still matters
r: roads split and join again sometimes | it's nobody's fault, that's the sad part
o: maybe some friends are just for one part of life | i don't want that to happen to us
o: the trick is to keep showing up, even when it's busy | maybe it's ok to grow apart if it was good while it lasted
p: it won't. i'll pester you | showing up, yeah | good while it lasts is still good
p: some friends are seasons, i guess
c: real friends never drift apart | if you drift apart you weren't real friends
y: yeah | true | agreed
n: that's harsh | real friends drift too | life happens
""")

angle(FRIENDSHIP, "friend_making_hard", "making friends being hard", """
m: making friends is harder than mining diamonds | i'm not good at talking to people
m: i never know what to say to new people | some people make friends so easily and i don't get how
q: is it hard for you to make friends? > very / no, i talk to everyone / only at first / i'm still learning
q: what helps you make a friend? > sharing food / doing a job together / just saying hi / being honest
d: i get nervous and talk about dirt for ages | the first hello is the hardest part
d: people like it when you ask about their builds | mining together is easier than talking
r: dirt talk is valid | the first hello is the worst, then it's fine
r: doing stuff together is the best way | asking about builds is a good trick
o: quiet people make the best friends once you know them | maybe the hard ones are worth more
o: i'm glad you talked to me first, honestly | maybe everyone's nervous and just hides it
p: quiet ones are the best, yeah | worth it, i think | i'm glad i did | everyone's nervous, i promise
c: a few close friends beat lots of okay ones | a few real friends beat a hundred people
y: totally | yeah | agreed
n: lots of friends is fun | nah, the more the merrier | i like a big crowd
""")

angle(FRIENDSHIP, "friend_group_or_one", "a group or one friend", """
m: i keep thinking about groups and friends | being in a group is different from having a friend
m: a group feels safe but one friend feels close | i like groups but i miss one on one talks
q: would you rather have a big group or one close friend? > one close friend / the group / both, obviously / depends on the day
q: what's the best thing about a group? > safety at night / someone always knows something / the noise / never eating alone
d: in a group you can hide. with one friend you can't | a group feels like a town, a friend feels like a house
d: groups are good for fights, friends are good for talks | some things you only say to one person
r: a town and a house, i like that | some things are one person things, yeah
r: hiding in a group is real | both is the best answer
o: a group is lots of friendships tied together | you can be lonely in a group if nobody really knows you
o: our talks are my favourite part of the group thing | the best groups are just friends who decided to stick together
p: tied together, yeah | lonely in a group, i've felt that | mine too | that's what we are
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
           "newbie": "being alone at night is the scariest | wait, you feel that too?",
           "helper": "you're not alone right now, at least | i'm glad you told me",
           "nomad": "i'm alone most days. you get used to it, mostly",
           "speedrunner": "alone is faster. but yeah, quiet"},
    ponder="yeah | maybe | that helps | huh",
    nudge="go on | i'm listening | you ok? | tell me more",
    close={"any": "anyway. it's nice to talk | i feel less alone already | thanks for this | it's good to have someone",
           "hermit": "good. enough talk.", "casual": "ok i'm better now lol", "newbie": "can i follow you around a bit?"},
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
d: after a while you start noticing every little sound | i started talking out loud just to hear a voice
d: the first person i saw after that, i almost hugged | being alone makes everything feel bigger
r: talking out loud is normal, i do it too | almost hugging a stranger, lol, i get it
r: bigger and quieter, yeah | the little sounds get loud
o: i think the wild teaches you who you are | when it's just you, you hear your own thoughts too loud
o: i came back from that trip different | alone is good in small doses
p: the wild is a hard teacher | too loud thoughts are the worst part | small doses, yeah
p: different in a good way, i hope
c: everyone should spend some time alone in the wild | being alone out there is good for you
y: true | yeah, it teaches you | agreed
n: nah, it's bad for you | people need people | too risky
""")

angle(LONELY, "lonely_talking_to_things", "talking to yourself and animals", """
m: i talk to myself a lot when nobody's around | i caught myself talking to a sheep yesterday
m: i name the cows near my house just to have someone to talk to | sometimes i say goodnight to my furnace
q: do you talk to yourself? > all the time / only when i'm stuck / no, that's weird / yeah, i'm good company
q: do you think animals listen when we talk to them? > yeah, they tilt their heads / no, they just want wheat / the dogs do / i like to think so
d: the sheep just looks at me, but it's nice | talking out loud makes the quiet less heavy
d: i think i talk to things so i don't forget how | my cow knows all my problems at this point
r: the cow's a good listener | talking to things is totally normal
r: the quiet is lighter with a voice in it | at least the sheep doesn't argue
o: maybe talking to animals is just being lonely in a nice way | you're the first real person i've talked to in a while
o: everyone talks to themselves, they just don't admit it | i'd rather talk to you than the furnace
p: lonely in a nice way, that's a thing | i'm glad i'm the one | the furnace is jealous now
p: i talk to my chests, so
c: talking to yourself is perfectly normal | everyone should talk to themselves sometimes
y: totally | yeah | agreed
n: it's a bit weird | nah, talk to people | only a little
""")

angle(LONELY, "lonely_in_town", "lonely in a crowd", """
m: you can feel lonely even in a busy town | everyone's around but nobody really talks
m: i've been in town all day and still feel alone | sometimes a crowd is lonelier than a cave
q: have you ever felt alone with people around? > yeah, often / once or twice / no, people help / that's the worst kind
q: what makes you feel less alone? > someone asking how i am / sharing food / a long talk / my dog
d: everyone's busy with their own thing | people say hi but nobody asks how you are
d: it's like being in the room but not in the talk | i walk past ten people and talk to none
r: busy towns can be cold | asking how someone is, yeah, it matters
r: not being in the talk is the worst | ten hellos and no talks, i know that one
o: maybe everyone in town feels like that and nobody says it | i think i'm waiting for someone to talk to me first
o: it's easier to be alone in a cave than alone in a crowd | i'm glad i'm talking to you now, at least
p: probably, yeah. someone has to start | i'll talk to you first, any time | caves don't expect anything, true
p: me too
c: towns can be the loneliest places | it's lonelier in a town than in the wild
y: weirdly yeah | true | agreed
n: nah, the wild is lonelier | towns are full of friends | people help
""")

angle(LONELY, "lonely_liking_alone", "liking being alone", """
m: i actually like being alone. is that weird | some days i just want nobody around
m: being alone isn't the same as being lonely | i need quiet time or i go crazy
q: is it bad to want to be alone? > no, everyone needs it / only if it's all the time / not at all / a little, maybe
q: what's the difference between alone and lonely? > alone is a choice / lonely hurts / lonely is missing someone / no idea, they feel the same to me
d: alone is when the quiet is yours | lonely is when you want someone and nobody's there
d: i do my best thinking alone | i like people, i just like them in small bits
r: the quiet is yours, i like that | small bits of people, very relatable
r: alone by choice is peace | lonely is the other kind, yeah
o: maybe the trick is having people to come back to after being alone | i think hermits aren't lonely, they're just full
o: being alone is easier when you know someone would miss you | i'm good at alone because i have friends
p: coming back is the best part | full, i like that | someone would. me | that makes total sense
c: being alone is better than being around people | solitude beats company
y: often, yeah | true | agreed
n: nah, people are better | company wins | alone gets old
""")

angle(LONELY, "lonely_nights", "lonely nights", """
m: nights are the loneliest part | when it gets dark i miss everyone
m: in a hole at night with the zombies groaning, that's lonely | the night is when you notice you're alone
q: what do you do on lonely nights? > sleep it off / think about friends / make plans / talk to the dog
q: is the night worse alone? > way worse / not really / only without a bed / yeah, the sounds
d: the groaning outside makes the walls feel thin | i lie there and think about everyone i know
d: i sleep so the night goes faster | a torch in the corner makes it less lonely
r: a torch is good company | sleeping it off is smart
r: thinking of people helps a bit | thin walls at night, ugh
o: maybe the night is lonely so we appreciate the morning | i think about who else is awake and alone right now
o: knowing someone else is out there under the same moon helps | i'd like to have a bed near friends one day
p: probably someone like you | same moon, yeah, that helps | you can always crash at mine
p: morning always comes
c: nights are better spent with others | nobody should spend the night alone
y: yeah | true | agreed
n: i like nights alone | nah, quiet nights are nice | alone is fine
""")

angle(LONELY, "lonely_pets", "a pet for company", """
m: getting a dog was the best thing for my loneliness | a cat on the bed makes a house feel full
m: i think i need a pet. the house is too quiet | my dog doesn't talk but he gets it
q: do you think a pet can fix loneliness? > a lot of it / not all of it / yeah, dogs especially / they help
q: what's the best pet for company? > a dog / a cat / a parrot, it talks back / a horse, you can go places together
d: coming home to someone happy to see you changes everything | he follows me from room to room
d: the cat sits by the furnace like she's keeping watch | even a chicken would be something
r: a happy face at the door is everything | room to room, that's love
r: a guard cat, perfect | a chicken is a start lol
o: pets don't care about anything except you being there | maybe we all want someone glad we came home
o: i take better care of myself with a dog to look after | he needs me and that makes me need me
p: glad we came home, yeah | that's the whole thing | that's really nice | being needed helps
c: everyone should have a pet | every house needs a pet
y: yes | true | agreed
n: pets are a lot of work | not everyone | i'd rather not
""")

# ======================================================================================================================
# Nature: trees growing back, fire, flowers, the world healing, old trees, growing unseen.
NATURE = theme(
    "nature", ["trees", "plants", "philosophy"], ["trees", "plants", "biomes", "philosophy"],
    react={"any": "that's a nice thought | i've noticed that too | huh. nature's weird | i think about that when i cut trees",
           "casual": "lol tree hugger. but yeah | bro's talking to trees now. fair though",
           "hermit": "the forest forgives. slowly. | trees outlive arguments.",
           "newbie": "wait, do trees grow back by themselves? | i love trees, they're so tall",
           "helper": "i always plant a sapling back",
           "builder": "trees are my favourite thing to look at",
           "speedrunner": "i punch a tree and go. but ok"},
    ponder="maybe | huh | that's lovely | could be",
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
d: the tree gives you a sapling like it's saying here, try again | a cut forest looks so sad until it grows back
d: the world gives you a way to fix what you broke | i planted a whole row and now it's a forest again
r: a tree saying try again is so sweet | fixing what you break is the whole deal
r: a row turning into a forest is beautiful | sad stumps are the worst
o: maybe the world wants us to take and give back | i wish everything grew back like trees
o: the forest doesn't hold a grudge | trees are kinder than most people
p: take and give back, yeah | some things don't. that's why trees are special | no grudges, just leaves
p: that's true honestly
c: if you cut a tree you have to plant one | not replanting is wrong
y: always | yeah | agreed
n: nah, there are plenty of trees | it's fine either way | trees grow on their own
""")

angle(NATURE, "nature_fire", "a forest burning", """
m: lightning hit a forest and the whole thing burned | i saw a forest fire. everything just gone
m: fire spreads so fast through the leaves | the forest burned and nobody could stop it
q: have you ever seen a forest burn? > yeah, it was awful / once, from far away / no, and i hope not / i started one, oops
q: do you think the forest comes back after a fire? > slowly / only if someone plants / yeah, nature's tough / not the same
d: the ground was all bare after, just dirt and ash | the animals ran away. the sheep were so scared
d: it was beautiful and horrible at the same time | the trees can't run. that's the sad part
r: bare ground is so sad | the sheep running, aw
r: trees can't run, oof | beautiful and horrible, yeah, fire's like that
o: i planted saplings where it burned. it felt right | maybe the world needs to start over sometimes
o: the grass came back first, then the flowers | fire is scary because it doesn't care
p: planting after a fire is so kind | starting over, yeah | grass first, of course | things that don't care are scary
c: fire is the most dangerous thing in the world | nothing's scarier than fire
y: easily | yeah | true
n: lava is | creepers | the void
""")

angle(NATURE, "nature_flowers", "why flowers grow", """
m: why do flowers grow everywhere | a field of flowers is the prettiest thing in the world
m: flowers don't do much but i love them | i picked a flower today and felt bad
q: what's your favourite flower? > the blue one / sunflowers / poppies / the tiny white ones
q: do you think flowers are useful or just pretty? > just pretty, and that's enough / dye, they're useful / bees need them / both
d: the bees need them, so they're not just pretty | they grow where nobody planted them
d: a flower field makes me forget about mobs for a second | i keep one in my house, in a pot
r: pretty is a job too | forgetting mobs for a second is a gift
r: a flower in a pot is a good choice | they plant themselves, kind of
o: maybe the world makes flowers just to be nice | things that are only pretty matter too
o: i'd like my life to be a bit like a flower field | bees make honey from flowers, so flowers are food too
p: a nice world, then | pretty matters, yeah | a flower field life sounds good | everything connects
c: flowers are the best part of the world | nothing beats a flower field
y: so true | agreed | yeah
n: diamonds though | nah, trees | flowers are just decoration
""")

angle(NATURE, "nature_healing", "the world healing", """
m: grass grows back over my old paths | the leaves fall away on their own when you cut the trunk
m: the world keeps fixing the scars we leave | a hole i dug filled up with water and lily pads
q: do you think the world heals from us? > slowly / yeah, look at the grass / not from creeper holes / it tries
q: what's the worst scar you've left on the world? > a huge strip mine / a burned forest / so many dirt pillars / a creeper hole by my house
d: the creeper hole by my house has grass in it now | the path i walked every day is turning green again
d: i left a strip mine and now there's moss growing in it | nature takes back what we leave
r: moss in an old mine is beautiful | green paths mean you moved on
r: the world forgives, slowly | grass in a creeper hole, nice
o: maybe one day everything we built will be covered in grass | i think the world is patient with us
o: i like that we don't get the last word. the grass does | i hope it covers my mistakes gently
p: covered in grass, peacefully | patient world | the grass always wins | gently, yeah
c: we should leave the world how we found it | clean up after yourself, always
y: yeah | true | agreed
n: the world's for using | nah, it's fine | it heals itself
""")

angle(NATURE, "nature_old_trees", "giant old trees", """
m: there are trees out there way older than me | i found a giant tree in a dark forest
m: the big spruce trees feel like they've seen everything | some trees are so big you need a pillar to see the top
q: how old do you think the biggest trees are? > older than all of us / as old as the world / a few hundred days / no idea, old
q: would you cut down a really old tree? > never / if i needed the wood / not the biggest one / i'd feel bad
d: standing under one made me feel small and safe | i didn't cut it. it felt wrong
d: those trees were here before any of us woke up | the shade under them is the best
r: not cutting it was right | small and safe is a good feeling
r: old trees deserve to stay | best shade in the world
o: maybe old trees are the world's elders | if trees could talk they'd have the best stories
o: i want to grow something that big and old one day | i put a little fence around it so nobody cuts it
p: elder trees, yeah | stories about us, probably | plant a big one, then wait | a fence around a tree, love that
c: the oldest trees should never be cut | old trees should be protected
y: yeah | agreed | true
n: wood is wood | nah, they're just big | i need the logs
""")

angle(NATURE, "nature_growing_unseen", "things growing while we're away", """
m: my crops only seem to grow when i'm close | i leave my farm and come back and nothing's changed
m: the trees by my house grow but the far ones never change | it's like things only grow when someone's near
q: do you think plants grow when nobody's watching? > nope, they wait / of course they do / only the trees / they grow, just slower
q: why do you think things grow faster when we're near? > they like company / the world's lazy / we look after them / it just feels that way
d: i stayed by my wheat all day and it grew | i went exploring for days and my farm was still green
d: it's like the world only works where we are | the far fields feel asleep
r: maybe it's waiting for you | the world being lazy is so funny
r: asleep fields, that's creepy and cute | stay close and they grow, simple
o: maybe that's why people build farms next to their houses | it's like the world needs us to keep it going
o: if nobody was here, would anything grow at all | i like being needed by my wheat
p: smart wheat | we keep it going, huh | maybe not. sad | needed by wheat is still needed
c: the world needs us to keep it going | nothing would grow without us
y: i've seen it | yeah | true
n: the world was here before us | nah, trees grow anyway | that's silly
""")

# ---- A casual's jokes before it gets serious, for the themes above.
JOKES.update({
    RESPAWN: V("if i die again i'm writing my bed an angry letter | dying is just a fast way home lol | "
               "i've died so much my bed knows me by name"),
    GRIEF: V("i'm bad at sad stuff so bear with me | ok i'm not joking for once, for real"),
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
           "newbie": "wait, how deep does it go? | i've only seen the ocean once and it was huge",
           "helper": "the beach is a good place to think",
           "nomad": "i've crossed it twice. it changes you",
           "speedrunner": "oceans are slow. boats are slow. but sure"},
    ponder="maybe | huh | wow | could be",
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
d: down there it's quiet in a way the land never is | the light just fades and you lose which way is up
d: you only have so much breath, and the sea knows it | i saw a kelp forest swaying in the dark
r: the sea knows how long you can hold your breath, creepy | kelp forests are so pretty
r: losing which way is up is the scariest | quiet like that is heavy
o: maybe the ocean is the world's biggest secret | everything down there is older than our towns
o: if the land is ours, the ocean belongs to something else | i want to see the bottom once, just once
p: biggest secret, yeah | older than us, probably | it belongs to the fish | bring a lot of air
c: the deep ocean is scarier than any cave | the sea is more dangerous than the deep dark
y: easily | yeah | true
n: caves are worse | nah, the deep dark wins | at least the ocean has light
""")

angle(OCEAN, "ocean_shipwrecks", "who sailed the wrecks", """
m: i found a shipwreck and wondered who sailed it | there are old ships all over the sea floor
m: somebody built that ship, then it sank | a wreck on the beach made me sad for some reason
q: who do you think sailed the old ships? > people like us, long ago / pirates, obviously / nobody, they just appeared / explorers
q: would you sail a ship across the sea? > yes, the whole way / with a crew / no, look what happened to them / one day
d: the maps in the chests point to treasure. someone hid it | there's a captain's room with a little bed
d: they had a plan and a map and then the sea said no | the wood's all mossy and soft now
r: a captain's bed is so sad | the sea saying no, oof
r: hidden treasure means someone hoped to come back | mossy old ships are beautiful
o: what if they made it home and the ship just sank later | someone wrote those maps for themselves
o: i hope the crew got to shore | every wreck is a story nobody will finish
p: that's a nicer story | maps for themselves, then lost | i hope so too | we can finish them, kind of
c: taking treasure from a wreck is fine | the sea's treasure is anyone's
y: yeah, it's lost | true | finders keepers
n: it's still theirs | it feels like robbing a grave | leave the wrecks alone
""")

angle(OCEAN, "ocean_dolphins", "dolphins leading the way", """
m: a dolphin swam with my boat for ages | dolphins lead you to treasure, people say
m: i fed a dolphin and it took me somewhere | dolphins look like they're always happy
q: do you think dolphins are happy? > the happiest / they look it / no idea / happier than us
q: why do you think dolphins help us? > they're nice / they like fish / they're bored / they want friends
d: it jumped out of the water like it was showing off | it swam ahead and waited for me to catch up
d: it led me to a wreck and just left, job done | they're smart, you can see it
r: waiting for you is so sweet | job done, like a little guide
r: showing off, fair | smart and kind is the best combo
o: i wish people helped each other like that, no deal, just fun | maybe the sea isn't so lonely with them in it
o: i think about that dolphin when i'm being grumpy | animals that help for no reason are the best
p: no deal, just fun, yeah | a sea with friends in it | be the dolphin | no reason is the best reason
c: dolphins are the best animal | nothing beats a dolphin
y: yeah | agreed | they're the best
n: dogs though | nah, cats | axolotls
""")

angle(OCEAN, "ocean_drowning", "the fear of drowning", """
m: drowning is the death i'm most scared of | running out of air is the worst feeling
m: when the bubbles run out you panic | i nearly drowned once and still think about it
q: what's the worst way to die? > drowning / lava / falling / the void
q: are you scared of deep water? > yes / a little / no, i love it / only at night
d: it's slow, you know it's coming | you watch the bubbles go one by one
d: i couldn't find the surface. everything looked the same | the light was right there and i couldn't reach it
r: watching the bubbles is the worst | not finding the surface, oh no
r: slow deaths are scary | so close and so far
o: since then i always carry a door to make an air pocket | i think the sea taught me respect
o: i still swim. i just don't go deep | being scared of it doesn't mean hating it
p: a door for air is smart | respect, yeah | shallow is fine | love and fear at once, yeah
c: everyone should learn to swim properly | you're not ready for the world till you can swim
y: true | yeah | agreed
n: just use a boat | nah, stay on land | swimming's overrated
""")

angle(OCEAN, "ocean_horizon", "looking out at the sea", """
m: i like standing on the beach looking at the horizon | the sea and the sky meet somewhere out there
m: looking at the ocean makes my head go quiet | the waves just keep coming, forever
q: what do you think about when you look at the sea? > nothing, that's the point / what's out there / old friends / home
q: would you live by the sea? > yes, in a heartbeat / no, too windy / maybe a little hut / i already do, kind of
d: the water doesn't care about my problems, and that helps | it's the biggest thing i've ever seen
d: everything feels smaller next to it, even worries | the sound of it is better than any music
r: the sea not caring is comforting, weirdly | small worries, big sea
r: the sound is the best part | the biggest thing, yeah
o: maybe that's why people go to the sea when they're sad | the waves come in and go out, like breathing
o: the sea makes me feel like i'm part of something big | i'd like a grave by the sea, honestly
p: i'd go to the sea when sad | breathing, yeah | something big, yes | that's peaceful, actually
c: everyone should live near the sea | the best houses are by the sea
y: yes | true | agreed
n: mountains are better | forests | too much sand
""")

# ======================================================================================================================
# Animals: do they think, turtles, bees, parrots, axolotls, wolves.
ANIMALS = theme(
    "animals", ["animals", "philosophy"], ["animals", "pets", "farming", "philosophy"],
    react={"any": "huh. i've wondered that watching my animals | animals are smarter than people think | "
                  "that's sweet, actually | good question, honestly",
           "casual": "lol the cows are plotting. ok but fr | bro's thinking about cows again. fair",
           "hermit": "animals don't lie. | they know more than they say.",
           "newbie": "wait, do animals have feelings?? | i love the animals so much",
           "helper": "i always feed the animals near town",
           "hoarder": "i keep my animals close. they're mine"},
    ponder="maybe | aw | huh | could be",
    nudge="go on | you really love animals huh | keep going | what else",
    close={"any": "anyway. be nice to animals | i'm gonna go feed my cows | animals are good | they're better than us sometimes",
           "hermit": "be kind to the small ones.", "casual": "ok i'm gonna go apologise to a cow lol",
           "newbie": "i wanna pet every animal now"},
    bclose="go give them some wheat | your animals are lucky | animals rule | say hi to them for me",
    yes="right? | exactly | yeah", no="hm, maybe | we'll see | fair",
    sits={"calm": "feeling peaceful, watching the animals | i've been watching the sheep all morning",
          "morning": "the animals are all out this morning | the cows are mooing like crazy today",
          "friends": "you're an animal person, right? | can i ask you an animal thing"})
JOKES[ANIMALS] = V("what if chickens are the real masterminds lol | cows just stand there judging me all day")

angle(ANIMALS, "animals_think", "whether animals think", """
m: i wonder what animals think about | the cows just stare at me like they know something
m: my dog looks at me like he understands everything | do sheep have thoughts, or just grass
q: do you think animals think like we do? > a little / no, just food / more than we know / dogs do, sheep don't
q: do you think animals have feelings? > of course / some do / dogs, definitely / i hope not, for the cows' sake
d: my dog gets sad when i leave. that's a feeling | the cow follows me if i hold wheat. that's a thought
d: wolves remember who fed them | the fox stole my bread and looked so proud
r: proud fox, lol | remembering who fed them is a big deal
r: sad dog when you leave, that's love | wheat logic is still logic
o: maybe animals think simpler, but not less | if they feel stuff, then how we treat them matters
o: animals are honest in a way people aren't | the bees work so hard, i hope they're happy
p: simpler, not less. i like that | it matters either way | honest animals, yeah | busy bees, happy bees
c: animals feel things just like us | animals have feelings, end of story
y: totally | yeah | of course
n: not like us | nah, they're animals | some do, some don't
""")

angle(ANIMALS, "animals_turtles", "turtles going home", """
m: turtles always go back to the beach where they hatched | i saw a turtle swim all the way home to lay eggs
m: a turtle knows where its home is better than i do | turtles remember their beach forever
q: how do you think turtles find their way home? > they just know / they remember the sand / the stars / they follow the moon
q: do you have a place you'd always go back to? > my first house / the spot where we met / no, i keep moving / the beach i woke up on
d: they swim across the whole sea just to get back | they never forget, no matter how far they go
d: the babies run to the water and vanish, but they come back | it's the most loyal thing in the world
r: loyal to a beach, i love that | never forgetting, that's beautiful
r: crossing the sea for home, yeah | they come back, like we do
o: i wish i knew where my home beach was | maybe we all have a place we'd swim across the sea for
o: home is the place you go back to even when it's far | i'd like to be as sure of anything as a turtle is
p: you'll find it | i'd swim back to my house | far but still home | turtle certainty, yeah
c: everyone has one place they always come back to | everyone has a home beach, like the turtles
y: true | yeah | i think so
n: not me | some people don't | i'm a wanderer, so no
""")

angle(ANIMALS, "animals_bees", "bees at work", """
m: the bees work all day and never complain | i watched the bees for an hour today
m: bees go flower to flower all day long | the bees make honey and we just take it
q: do you think bees like working? > they love it / they don't know anything else / i hope so / they look happy
q: is it fair to take their honey? > if you leave some / yeah, if you're gentle / not really / they make more
d: they go back to their hive every night, like us | they only sting if you mess with them
d: they work together without anyone telling them | if you hurt one the whole hive comes for you
r: the hive sticks together, respect | home every night, like us
r: nobody bosses them, nice | they just want to be left alone
o: maybe the bees are what a good town looks like | i'd like to work like that, calm and busy
o: if one gets hurt they all come. i wish people did that | a bee dies if it stings, and still does it for its hive
p: a bee town, yeah | calm and busy is the dream | looking out for each other, yeah | that's brave and sad
c: we should learn from the bees | bees are smarter than people
y: true | yeah | probably
n: they're just bugs | nah, people are smarter | we have pickaxes
""")

angle(ANIMALS, "animals_parrots", "parrots dancing", """
m: parrots dance when music plays | i saw a parrot dancing by a jukebox and it made my day
m: parrots copy the sounds of mobs. that's creepy and smart | a parrot sat on my shoulder all day
q: why do you think parrots dance? > they love music / they're happy / they can't help it / to show off
q: what makes you want to dance? > good news / a sunrise / music / nothing, i don't dance
d: it didn't care who was watching | it bobbed its head like nobody was around
d: it was the happiest thing i saw all week | it danced till the song stopped, then just sat there
r: dancing like nobody's watching, yeah | happiest thing all week, i believe it
r: the song stops and it just waits, aw | i want that energy
o: maybe joy doesn't need a reason | i wish i danced more
o: if a parrot can be that happy, i can find something | maybe happiness is letting yourself enjoy stuff
p: joy for no reason is the best kind | dance then, nobody's looking | find your jukebox | letting yourself, yeah
c: everyone should dance more | dancing is the best thing in the world
y: yes | true | agreed
n: i'll pass | dancing is for parrots | nah
""")

angle(ANIMALS, "animals_axolotls", "axolotls playing dead", """
m: axolotls play dead when they're hurt, then get better | i saw an axolotl pretend to die and come back
m: axolotls are tiny but they fight fish | axolotls help you when you fight underwater
q: do you think playing dead is smart or sad? > smart / sad / both / it's what we do too, kind of
q: what's the bravest small animal? > axolotls / foxes / bees / chickens, they carry zombies around
d: it rested and then got right back up | it's like giving yourself a little break before trying again
d: they're tiny and they still go after drowned | they fight next to you if you're nice to them
r: a little break, smart | tiny and brave, my kind
r: fighting next to you, loyal | getting back up is the main thing
o: maybe it's ok to play dead for a while when life's hard | they come back, like we do
o: i've had days where i played dead too | resting isn't giving up
p: take a break, then get up | like us, yeah | i've had those days | resting is part of it
c: sometimes hiding is the smartest thing to do | running away isn't cowardly
y: true | yeah | smart
n: stand and fight | nah, face it | hiding never helps
""")

angle(ANIMALS, "animals_wolves", "a wolf's loyalty", """
m: a tamed wolf will follow you anywhere | my dog jumped into lava after me once
m: dogs fight anything that hurts you | a wolf only needs a few bones to love you forever
q: why do you think wolves are so loyal? > they just are / the bones / they know who's good / they choose you
q: would you do for a friend what a dog does for you? > yes / i'd try / probably not, honestly / i'd like to think so
d: he doesn't care what i have, he just follows | he sits when i tell him, even when he wants to run
d: he attacked a skeleton twice his size for me | a few bones and he gave me everything
r: loyal for bones, that's a good deal for us | he'd fight anything for you
r: following without caring what you have, that's real | sitting when told, so good
o: i don't think i deserve how much he loves me | dogs make you want to be better
o: loyalty like that is rare in people | i'd like to be someone's dog, in a good way lol
p: you do | be the person your dog thinks you are | rare, yeah | lol i get it
c: dogs are more loyal than people | a dog's the most loyal friend you'll have
y: yeah | true | always
n: some people are loyal too | my friends are | nah, people can be too
""")

# ======================================================================================================================
# Building as leaving a mark.
LEGACY = theme(
    "legacy", ["building", "philosophy", "legacy"], ["building", "home", "memories", "legacy"],
    react={"any": "that's a good thought | i think about that when i build | huh, i never thought of it like that | "
                  "that's kind of beautiful",
           "builder": "every build is a bit of you. i believe that | that's why i obsess over details",
           "casual": "lol my builds are a dirt hut. ok but yeah | bro's thinking about legacy. respect",
           "hermit": "build it well. that's all. | stone remembers longer than people.",
           "newbie": "wait, my builds will still be there after? | i've only built one house so far",
           "speedrunner": "i don't build. i leave pillars. maybe that's my mark",
           "hoarder": "my mark is my chests. full ones"},
    ponder="yeah | huh | maybe | i like that",
    nudge="go on | you've been building a lot lately | keep going | what's on your mind",
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
d: i carve my name on signs in every build | a house is a letter to whoever comes next
d: the world's full of other people's marks, ruins and towers | my first house is still standing and that makes me happy
r: a house as a letter, love that | your first house still standing is great
r: signs with names are like saying hi to the future | marks everywhere, yeah
o: maybe it doesn't matter if anyone remembers, just that we made it | someone will walk in my house and wonder about me
o: what if the mark we leave is in people, not blocks | i want my mark to be kind, not big
p: making it is enough | let them wonder nice things | in people, yeah | kind beats big
c: what you build is the best thing you leave behind | builds say more than words
y: true | yeah | agreed
n: friends are what you leave | nah, people remember people | builds fall down
""")

angle(LEGACY, "legacy_after_gone", "builds after we're gone", """
m: will anyone see my builds after i'm gone | i wonder who'll live in my house one day
m: someone will find my tower years from now and wonder | my builds will outlast me and that's weird
q: what do you think happens to our builds when we're gone? > someone moves in / they get overgrown / creepers / they stay forever
q: would you want strangers living in your house later? > yes, if they're nice / no, it's mine / i don't mind / i'd leave them a note
d: i'm going to leave a chest with a note in it | i hope they don't change the roof
d: maybe someone will fix the bits i never finished | i'd want them to keep the garden
r: a chest with a note is a great idea | they'd better not touch the roof lol
r: finishing your unfinished bits, that's nice | keep the garden, yeah
o: it's like passing something on to someone you'll never meet | my house will remember me even if nobody else does
o: maybe the future people will be like us, wondering | i'm building for someone i'll never know
p: a gift for a stranger | the house remembers, yeah | they'll wonder about you | that's generous
l: honestly i want someone to know i was here | i hope someone thinks i was a good builder
k: i know you were here | you are a good builder | i'll tell them
c: builds should be left for the next people | you shouldn't tear down your builds when you leave
y: yeah, leave them | true | agreed
n: take your stuff with you | nah, clean up | nobody wants old builds
""")

angle(LEGACY, "legacy_ruins_homes", "ruins that were homes", """
m: every ruin was someone's house once | i found an old broken house and wondered who lived there
m: the old ruins in the forest had a bed in them | someone built that and now it's moss and cobwebs
q: who do you think lived in the old ruins? > drifters like us / villagers / players / nobody knows
q: do you think our houses will be ruins one day? > yeah, everything is / not if we look after them / i hope not / maybe, and that's fine
d: they had a furnace and a little garden | there was a sign but the words were gone
d: they built it right where i would have | the roof fell in but the walls are still standing
r: a garden means they planned to stay | the words being gone, sad
r: they picked a good spot, like you | walls standing, still trying
o: one day someone will find our town and think the same | maybe ruins are just houses that are resting
o: i fixed a broken wall on a ruin. it felt like helping them | someone's whole life is a few mossy blocks now
p: they'll wonder about us | resting houses, that's lovely | that's kind of you | sad, but they lived
c: we should fix up old ruins | ruins should be rebuilt, not left
y: yeah | true | agreed
n: leave them as they are | ruins are history | they're prettier broken
""")

angle(LEGACY, "legacy_signs", "writing on signs", """
m: i write little notes on signs everywhere | a sign is a way of talking to someone you'll never meet
m: i found a sign in a cave that said turn back | someone left a sign that just said hello
q: what's the best sign you've ever found? > one that said hello / a warning about a ravine / someone's name and a date / a joke
q: what would you write on a sign for a stranger? > you're not alone / turn back lol / home is that way / be careful
d: the hello sign made me smile for a whole day | the turn back sign probably saved my life
d: a sign is the only way to talk to the future | i left a sign on a mountain that said nice view, right
r: hello signs are the best | that sign did its job
r: talking to the future, yeah | nice view, right. perfect
o: what if the person who wrote it is long gone | maybe one day someone will read mine and feel less alone
o: i like the idea of someone smiling at my sign years from now | words on a sign last longer than words in chat
p: then their words are still here, at least | they will | that's a nice thought | signs last, yeah
c: everyone should leave nice signs for strangers | the world needs more nice signs
y: yes | true | agreed
n: signs are clutter | nah, they spam the place | only useful ones
""")

angle(LEGACY, "legacy_never_done", "a build never being done", """
m: my build is never done. i always see something to change | i've rebuilt my roof four times
m: i finish a build and immediately want to start over | my house is a work in progress forever
q: is a build ever truly finished? > never / when you stop caring / when you move out / when someone else loves it
q: do you keep changing your house? > all the time / once in a while / no, it's done / only the roof
d: every time i look at it i see a mistake | it's like the house grows with me
d: the house i have now looks nothing like the first one | i think i like the building more than the house
r: a house that grows with you, i like that | the building is the fun part
r: mistakes are just the next project | it's a whole history now
o: maybe a house is never done because a life is never done | i'll probably still be fixing it on my last day
o: if i ever say it's done, i think i'll be sad | the unfinished parts are where i still have ideas
p: a never done life, yeah | fixing it to the end, sounds right | then keep it unfinished | ideas in the gaps
c: a perfect build doesn't exist | no build is ever perfect
y: true | agreed | yeah
n: mine is | nah, some are | i've seen perfect ones
""")

angle(LEGACY, "legacy_creeper_hole", "a build blown up", """
m: a creeper blew up part of my house and i felt it in my chest | all that work, gone in a second
m: it took days to build and one hiss to wreck | i stood in the hole the creeper left for ages
q: does it matter that builds can be blown up? > it hurts / no, you rebuild / it makes them more precious / it makes me want stone
q: would you rebuild the same, or different? > the same / different, better / smaller / out of stone this time
d: the hole looked like a bite out of my life | i rebuilt it, but the new wall looks different
d: i almost didn't rebuild. i was so tired | the creeper didn't even want anything. it just blew up
r: a bite out of your life, oof | different isn't worse
r: being too tired to rebuild is real | creepers don't care, that's the worst
o: maybe the rebuilt part is the strongest part now | it made me realise how much i love that house
o: everything we build is a bit fragile, and that's why it matters | i put a flower where the hole was
p: the scar is the strong part | you love it, yeah | fragile things matter more | a flower in the scar, beautiful
c: you should always build with stone so creepers can't wreck it | wood builds are asking for trouble
y: true | stone gang | yeah
n: wood is cozy | stone breaks too | just light it up
""")

# ======================================================================================================================
# Right and wrong: eating animals.
FOOD = theme(
    "food", ["food", "animals", "philosophy", "morals"], ["food", "animals", "farming", "morals"],
    react={"any": "that's a hard one | i think about that when i'm hungry | huh. i don't love that question | "
                  "fair thing to wonder",
           "casual": "lol i just ate a pork chop, don't do this to me. but yeah | bro i'm hungry, not now. ok fine",
           "hermit": "we eat. we thank. | the hungry don't argue.",
           "newbie": "wait, is eating cows bad?? | i feel bad for the pigs now",
           "helper": "i always try to be gentle with the animals",
           "hoarder": "i've got a chest of steak. no regrets",
           "pvp_sweat": "steak is fuel. fuel wins fights"},
    ponder="hm | maybe | that's fair | i guess",
    nudge="go on | you're feeling guilty, huh | keep going | what's bugging you",
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
d: i only take what i need and leave the rest | hunger makes the choice for you most days
d: i say thanks after, in my head | wolves eat sheep, so maybe it's just the world
r: only what you need is the right way | saying thanks is nice
r: hunger decides, yeah | the world works like that, i think
o: maybe what matters is not wasting any of it | i'd feel worse if i let them suffer
o: i breed two for every one i eat | caring about it is better than not caring
p: wasting is the real wrong | quick and kind, yeah | two for one, good rule | caring counts
c: eating animals is just part of life | there's nothing wrong with eating animals
y: yeah | true | it's how it works
n: i'm not so sure | it's a bit wrong | we could eat bread
""")

angle(FOOD, "food_farm_or_hunt", "farming or hunting", """
m: is raising animals to eat better or worse than hunting | the farm cows never see the wild
m: i hunt wild ones instead of farming them. feels fairer | my farm animals trust me and then i eat them
q: is it better to farm animals or hunt wild ones? > farm, it's steady / hunt, it's fair / both are fine / crops only, honestly
q: do your farm animals trust you? > totally / they follow the wheat / i think so / i hope not, for their sake
d: the wild ones have a chance at least | the farm ones have food and safety, at least
d: they follow me when i hold wheat and it gets me | a farm is a deal: safety for food
r: a chance matters | safety counts for something
r: the wheat follow is heartbreaking | a deal, yeah
o: maybe the trust is what makes it feel bad | i treat my farm animals well, at least while they're there
o: wild ones run, farm ones don't. that's the difference | i think about it, and i still eat
p: trust is a heavy thing | treating them well matters | running, yeah | thinking and still eating is honest
c: hunting wild animals is fairer than farming | wild hunting beats farm animals
y: yeah | true | fairer, i agree
n: farming is kinder | nah, the farm is steady | wild ones suffer more
""")

angle(FOOD, "food_crops_only", "living on crops", """
m: i've been eating only bread and carrots lately | i want to try a day without meat
m: crops don't look at you when you eat them | a carrot farm feels kinder than a cow pen
q: could you live on just crops? > easily / for a while / no, i need steak / i'd get so hungry
q: what's the best food that isn't meat? > bread / baked potatoes / golden carrots / pumpkin pie
d: bread is fine, but steak fills you up so much better | crops don't run away, that's nice
d: i feel lighter, weirdly | my farm feeds me and nothing has to die
r: steak's hard to beat | lighter, nice
r: nothing dying is a good feeling | crops are polite
o: maybe it's not all or nothing, just less | i like knowing my food grew because i planted it
o: if everyone ate a bit more bread, the cows would be happier | being full and not guilty is a nice combo
p: less is a good start | planted food tastes better | happier cows, i like it | best combo
c: you can live just fine without meat | meat isn't needed here
y: yeah | true | agreed
n: steak though | you'd starve | meat's the best food
""")

angle(FOOD, "food_fishing", "fishing", """
m: fishing is the most peaceful way to get food | i fished all morning and caught mostly boots
m: sitting with a rod is like thinking out loud | the fish don't see it coming
q: is fishing different from hunting? > yeah, it's calmer / no, same thing / fish don't count lol / it feels different
q: why do you think people love fishing? > the waiting / the quiet / the surprise / the treasure
d: i caught an enchanted book once and almost cried | waiting for the bobber is like a little meditation
d: half the time i don't even care if i catch anything | the fish probably have a different opinion
r: the enchanted book is the dream | a little meditation, yeah
r: not caring is the point | the fish disagree, probably
o: maybe fishing is just an excuse to sit by the water | the surprise is the best part. could be anything
o: you can't rush fishing. that's why i like it | i fish more for the quiet than the food
p: an excuse, sure, a good one | anything, even a boot | no rushing, yes | quiet first, fish second
c: fishing is the best way to relax | nothing beats an afternoon of fishing
y: yes | true | agreed
n: boring | nah, building | too slow
""")

angle(FOOD, "food_waste", "wasting food", """
m: i threw out rotten flesh and felt bad | i let some bread burn and it bugged me all day
m: wasting food feels wrong, even here | i left a stack of potatoes in a chest till i forgot them
q: is wasting food worse than wasting anything else? > yeah, something died for it / no, stuff is stuff / it's worse when others are hungry / i never think about it
q: what do you do with food you don't need? > give it away / feed the animals / keep it, just in case / eat it anyway lol
d: someone's always hungry somewhere | it took work to grow, so tossing it feels rude
d: i think about the first night i was starving | food is the one thing that keeps us going
r: someone's always hungry, yeah | rude to the wheat, lol
r: remembering the hungry nights helps | food is life
o: maybe sharing is the answer to waste | the villagers love bread, i give them extra
o: i've started leaving food in a chest by the road for whoever | waste is just sharing too late
p: share it, yeah | villagers deserve bread | a road chest, that's kind | sharing too late, oof
c: wasting food is the worst thing you can do | throwing out food is wrong
y: true | yeah | agreed
n: it's just food | worse things exist | nah, rotten flesh doesn't count
""")

# ======================================================================================================================
# Right and wrong: taking from ruins and others.
RUINS = theme(
    "ruins", ["structures", "philosophy", "morals"], ["structures", "exploring", "philosophy", "morals"],
    react={"any": "that's a fair question | i loot everything and now i feel weird | huh. i never thought about that | "
                  "hm, good point",
           "casual": "lol i robbed a temple yesterday. ok but yeah | bro i'm a loot goblin. fair question though",
           "hermit": "take what you need. leave the rest. | the dead don't trade.",
           "newbie": "wait, is looting bad?? i've done it so much | whose stuff is it then?",
           "bandit": "if nobody's using it, it's mine | finders keepers, always",
           "helper": "i always leave something back, just in case",
           "hoarder": "loot is loot. into the chest it goes"},
    ponder="hm | fair | maybe | i guess",
    nudge="go on | feeling guilty about something? | keep going | there's more",
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
d: the chests were so neat, like someone packed them carefully | it felt like going through someone's drawers
d: nobody's been there for ages, so who's it for | i left a torch so they'd know someone visited
r: neat chests make it worse | the drawers feeling, yeah
r: abandoned is abandoned | a torch is a nice touch
o: maybe taking is fine if you use it well | if the owner came back, i'd give it all back
o: what if they left it for someone who needed it | i'd want someone to use my stuff if i was gone
p: use it well, yeah | that's fair | for someone like us, maybe | i'd want that too
c: loot in ruins belongs to whoever finds it | ruins are free for anyone
y: yeah | true | finders keepers
n: it's someone's | it's still theirs | i feel bad taking it
""")

angle(RUINS, "ruins_temples", "the desert temples", """
m: the desert temples have chests under the floor, like tombs | who built the temples, and why the traps
m: i found a temple with a trap right next to the loot | the temples feel like they're guarding something
q: why do you think the temples have traps? > to keep thieves out / to test you / someone was paranoid / they were hiding treasure
q: would you take from a temple if you knew it was someone's tomb? > no / yes, they can't use it / i'd say sorry first / maybe, not the bones
d: the trap means someone really didn't want it taken | the room under the floor is so quiet
d: there's an old feeling down there, like i'm not supposed to be there | they put a lot of work into hiding it
r: the trap says it all | quiet rooms like that are spooky
r: not supposed to be there, yeah | hiding it took effort
o: maybe the trap was for someone they hated, not us | i took the loot and left the bones alone
o: if someone guarded my stuff that hard, i'd want it respected | maybe it's a test, and only careful people win
p: maybe | leaving the bones, good | respect, yeah | careful people win, true
c: traps mean you shouldn't take it | if it's trapped, leave it
y: yeah | fair | true
n: traps mean it's worth it | nah, beat the trap, take the loot | that's the fun
""")

angle(RUINS, "ruins_village_chests", "taking from villagers", """
m: is it wrong to take from village chests | the villagers don't stop you when you open their chests
m: i took bread from a village chest and felt bad | villagers just watch you take their stuff
q: is taking from a village chest stealing? > yes / not if they don't mind / kinda / they don't care, so no
q: would you take from a villager if you were starving? > yes / i'd ask first / i'd trade / no, never
d: they don't fight back, which makes it worse | i put the bread back later
d: they work all day for that stuff | taking from people who can't stop you feels wrong
r: putting it back is good | they work hard, yeah
r: not fighting back makes it worse, yeah | that's the test, i think
o: maybe what's right is what you'd do if someone was watching | i trade with them now instead
o: the villagers gossip, you know. they'd remember | kindness to people who can't stop you matters most
p: right when nobody's watching, yeah | trading is better | they do remember | that matters most
c: taking from villagers is stealing, plain and simple | village chests are off limits
y: true | yeah | agreed
n: they don't mind | it's fine | they don't need it
""")

angle(RUINS, "ruins_dropped_items", "someone's dropped items", """
m: i found someone's stuff on the ground where they died | a pile of items in the grass, someone's whole life
m: if you find someone's dropped stuff, do you keep it | i found a sword on the ground and wondered who lost it
q: if you find someone's dropped stuff, do you keep it? > i'd guard it for them / i'd keep it / i'd look for them / depends what it is
q: how would you feel if someone took your dropped stuff? > furious / sad / fair, i'd do it too / depends on who
d: their armor was all there, scattered | i stood guard for a while in case they came back
d: it's someone's worst moment, lying in the grass | the stuff vanishes soon anyway, so maybe take it
r: standing guard is kind | their worst moment, oof
r: vanishing anyway, yeah, it's tricky | scattered armor is sad
o: i'd want someone to save my stuff if they found it | maybe the right thing is to keep it and find them
o: i kept a sword once and gave it back a day later | what you do with someone's dropped stuff says a lot
p: save it and return it, yeah | finding them is the hard part | giving it back is the move | it says a lot, yeah
c: finders keepers. dropped stuff is fair game | if it's on the ground it's yours
y: yeah | true | fair game
n: give it back | that's cold | it's someone's
""")

angle(RUINS, "ruins_old_pots", "digging up old pots", """
m: i brushed sand and found an old pot shard | some sand hides little pieces of old pots
m: the old pots have pictures on them, like someone was telling a story | finding an old shard feels like hearing a voice
q: who do you think made the old pots? > someone like us / a village long ago / an artist / nobody knows
q: what do you think the pictures on the shards mean? > a story / a map / just decoration / a message
d: you have to brush so slowly or it breaks | i put four together and made a pot with their pictures
d: it's the closest thing to talking to someone from long ago | a skull, a heart, an arrow... it's like a language
r: slow brushing, patience | a pot made of old pictures, nice
r: talking to the past, yeah | maybe it is a language
o: maybe one day someone will dig up our stuff and wonder | the pictures feel like they mean something sad
o: i put the pot on a shelf. it feels like keeping something safe | they wanted to be remembered, i think
p: they'll wonder about us | sad pictures, yeah | keeping it safe, good | they are, by you
c: digging up old stuff is good, it keeps the past alive | the past should be dug up, not left
y: yeah | true | agreed
n: leave it buried | the past is past | some stuff should stay buried
""")

# ======================================================================================================================
# Right and wrong: fighting.
FIGHT = theme(
    "fight", ["fighting", "philosophy", "morals"], ["fighting", "mobs", "argument", "morals"],
    react={"any": "that's a hard one | i think about that after fights | huh. i don't know | fair question",
           "pvp_sweat": "fighting is just what we do | i fight to win. but yeah, i've wondered",
           "casual": "lol i just swing and hope. but fair | bro i hit a cow by accident once and felt bad",
           "hermit": "fight when you must. not when you can. | every fight costs.",
           "newbie": "wait, should i feel bad when i hit zombies? | i'm still scared of fighting",
           "helper": "i only fight to protect people",
           "bandit": "a fight's a fight. winner takes all"},
    ponder="hm | maybe | true | i guess",
    nudge="go on | that fight's still on your mind | keep going | what happened",
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
d: they come at us every night like it's their job | the creeper just walks up and hisses, no anger, nothing
d: the spiders leave you alone in the day. they're not all bad | i think the night makes them that way
r: just doing their job, kinda | no anger is the weird part
r: spiders in the day are chill, true | the night changes everything
o: maybe they're not evil, just made wrong | if they could choose, would they still come for us
o: i fight them, but i don't hate them | i'd rather understand them than hate them
p: made wrong, sad | i don't know | not hating them is mature | understanding is better
c: mobs are evil and deserve it | every hostile mob deserves to be killed
y: yeah | true | every single one
n: they're just mobs | nah, they can't help it | evil's a strong word
""")

angle(FIGHT, "fight_zombie_villagers", "zombies that were villagers", """
m: zombie villagers used to be villagers | you can cure a zombie villager. so they're still in there
m: some zombies were people once | i saw a zombie villager and it looked sad
q: if a zombie can be cured, is it wrong to kill it? > a bit, yeah / no, it's attacking you / only if you could've cured it / i don't know
q: have you ever cured a zombie villager? > yes, it was amazing / no, but i want to / i didn't know you could / no, too hard
d: a golden apple and a potion and they come back | they shake when they're being cured, like they're fighting it
d: the cured ones are so grateful, they give you deals | it changes how you see every zombie
r: they're fighting to come back, oh | grateful villagers, aw
r: every zombie a person once, heavy | curing is so worth it
o: what if regular zombies were someone too, once | maybe there's always something left in there to save
o: i try to cure them when i can now | i can't look at a zombie the same way anymore
p: don't say that | always something left, i like that | that's really kind | me neither now
c: if you can cure a zombie villager, you should | killing a zombie villager is wrong if you can cure it
y: yes | true | agreed
n: too much work | it's attacking me | self defense first
""")

angle(FIGHT, "fight_each_other", "fighting each other", """
m: why do drifters ever fight each other | there's enough danger without us hitting each other
m: i got into a fight with someone over nothing | fighting another drifter feels different from fighting mobs
q: is it ever right to fight another drifter? > to protect someone / never / if they started it / only in a spar
q: have you ever fought someone you liked? > once, over loot / yeah, a spar / no / never, i'd walk away
d: mobs don't hold grudges. people do | after a fight with someone, it's awkward for days
d: we're all in the same dark, why fight | a spar is fine, but a real fight hurts more than the hits
r: grudges last longer than wounds | we're all in the same dark, yeah
r: awkward for days, so true | spars are fine, real fights aren't
o: the night is the enemy, not each other | maybe fights happen because people are scared
o: i made up with the one i fought, eventually | i'd rather lose an argument than a friend
p: same dark, same team | scared people fight, yeah | making up is the best part | wise
c: drifters should never fight each other | fighting each other is always wrong
y: true | agreed | yeah
n: sometimes you have to | nah, bandits exist | some people deserve it
""")

angle(FIGHT, "fight_neutral_mobs", "leaving neutral mobs alone", """
m: endermen leave you alone if you don't look at them | the wolves only fight if you start it
m: i like the mobs that only fight back. they seem fair | i accidentally hit a wolf and the whole pack came
q: do you leave neutral mobs alone? > always / mostly / not the endermen, they have pearls / i try to
q: is it fair to start a fight with something that leaves you alone? > no / if you need something from it / not really / it's the world, everything's fair
d: the enderman just carries blocks around and minds its business | they're only dangerous if you bother them
d: a fight you start is different from one you finish | the pack came because they look after each other
r: minding its business, respect | bother them and you get what you get
r: starting versus finishing, yeah | the pack sticks together, fair
o: maybe the world would be calmer if we left more things alone | i try to treat them like neighbours
o: how you treat things that don't fight back says a lot | the endermen are just lonely block collectors
p: calmer, yeah | neighbours, nice | says a lot, yes | lonely block collectors, aw
c: you should never attack something that leaves you alone | starting fights with neutral mobs is wrong
y: yeah | true | agreed
n: pearls though | sometimes you need stuff | it's just a mob
""")

angle(FIGHT, "fight_revenge", "getting even", """
m: i want to get even with the skeleton that killed me | revenge feels good for a minute, then it's empty
m: someone took my stuff and i want to get them back | i keep thinking about getting even
q: is getting even ever worth it? > sometimes / never / it feels good though / only if it stops it happening again
q: what's the best way to deal with someone who wronged you? > talk to them / ignore them / get even / forgive and stay away
d: i went back for that skeleton and it wasn't even there | getting even took a whole day and i felt worse
d: revenge is like a fire that burns you too | i'd rather they just said sorry
r: the skeleton probably burned in the sun anyway | felt worse, yeah, it does that
r: a fire that burns you, yeah | a sorry is worth more
o: maybe letting it go is the stronger move | the best revenge might be living well
o: grudges are heavy to carry around all day | i'd rather spend the day building than plotting
p: letting go is hard but strong | living well, yeah | heavy, yes | building beats plotting
c: you should always get even | revenge is fair
y: yeah | true | an eye for an eye
n: let it go | revenge is a waste | it never ends
""")

angle(FIGHT, "fight_to_protect", "fighting to protect", """
m: i only fight when someone needs protecting | fighting is ok if it's for someone else
m: i jumped in front of a friend once, didn't even think | protecting someone is the only fight that feels right
q: what would you fight for? > my friends / my home / my animals / nothing, i'd run
q: is fighting for someone braver than fighting for yourself? > yes / no, both are / maybe / it's easier, weirdly
d: when i'm protecting someone, i'm not scared anymore | i'd fight a whole raid for my town
d: it's weird how brave you get for someone else | a golem fights for villagers like that
r: not scared for someone else, yeah | that's real courage
r: like the golems, yeah | you'd be a good protector
o: maybe protecting people is what makes fighting worth it | i think golems might love the villagers
o: i'd rather be the one who stands in front than the one who runs | being needed makes you brave
p: worth it, yeah | golem love, i believe it | standing in front, respect | needed and brave
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
           "newbie": "wait, we can go anywhere?? | i didn't know i was allowed to leave",
           "speedrunner": "free to go fast. that's my freedom"},
    ponder="huh | maybe | that's true | fair",
    nudge="go on | you've got itchy feet, huh | keep going | what's on your mind",
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
d: i stood at the edge of town and just thought, i could go | no walls, no rules, just the world
d: freedom is great until you need a bed at night | knowing i could go makes staying feel like a choice
r: staying as a choice is a nice way to see it | no walls is freeing and scary
r: the bed problem is real lol | you could go, yeah. that's something
o: maybe freedom isn't going, it's knowing you could | the ones who never leave are free too, if they chose it
o: too much freedom makes it hard to pick anything | i like a little freedom and a lot of home
p: knowing you could, yes | chosen staying is free | too many choices, yeah | best of both
c: freedom is the most important thing | nothing beats being free
y: true | yeah | agreed
n: safety matters more | home matters more | friends matter more
""")

angle(FREEDOM, "freedom_choice", "too much choice", """
m: sometimes there are too many things i could do | i wake up and don't know what to do with the day
m: with the whole world open i just stand there | too much choice makes me freeze
q: do you ever get stuck because you could do anything? > all the time / sometimes / no, i always know / only on rest days
q: how do you decide what to do each day? > whatever i need / whatever's fun / i ask someone / i just pick, kinda random
d: when i had nothing, i knew exactly what to do. find food | now i have stuff, and i'm lost
d: small goals help. one chest of iron, then see | the newbies are lucky, every day has a clear job
r: needing food makes it simple, yeah | small goals are the trick
r: having stuff makes it harder, weirdly | one chest at a time
o: maybe a little bit of need is good for us | i'd rather have a plan someone else made, sometimes
o: being free is harder than people say | i make lists now, so the freedom has edges
p: a little need, yeah | sometimes, sure | harder, true | lists are good edges
c: having fewer choices makes life easier | it's easier when you have less to choose from
y: true | yeah | agreed
n: more choice is better | nah, i like options | i'd hate being told
""")

angle(FREEDOM, "freedom_rules", "rules in towns", """
m: out in the wild there are no rules at all | towns have leaders and rules now
m: i wonder if we need rules, or if we're fine without | some towns are getting strict
q: do you think we need rules? > some, yeah / no, we're fine / only don't steal / just be nice, that's the rule
q: would you follow a town's rules? > if they're fair / probably / no / depends who made them
d: the rule against stealing from chests is a good one | without rules, the bandits win
d: rules are just people agreeing so nobody gets hurt | a leader with too many rules is scary
r: don't touch chests is the one rule that matters | bandits need rules, lol
r: agreeing, yeah | too many rules is a cage
o: maybe the best rules are the ones nobody needs to say | freedom out there, safety in here. you pick
o: a good rule protects the weak ones | i'd rather have kind people than lots of rules
p: unsaid rules, yeah | you pick, i like that | protect the weak, yes | kind people, always
c: towns need rules to work | without rules, everything falls apart
y: yeah | true | agreed
n: kindness is enough | rules make it worse | nah, we're fine without
""")

angle(FREEDOM, "freedom_stuck", "feeling stuck", """
m: i feel stuck doing the same thing every day | free to go anywhere, and i still feel stuck
m: mine, smelt, sleep, mine. it's a loop | i haven't been anywhere new in ages
q: do you ever feel stuck? > yeah, often / sometimes / no, i love my routine / right now, actually
q: what gets you unstuck? > going somewhere new / a new build / talking to someone / a good night's sleep
d: it's not a bad loop, it's just a loop | the walls of my house feel closer lately
d: i think i'm scared to try something new and fail | i keep saying tomorrow i'll go explore
r: a loop isn't bad, just a loop | closer walls means you need a walk
r: failing at something new is still new | tomorrow never comes, so go today
o: maybe being stuck is just a sign it's time for a change | i'll go somewhere new this week. i mean it
o: maybe the trick is one small new thing every day | i don't need to leave, just look at things fresh
p: time for a change, yeah | i'll hold you to it | one small new thing, i like that | fresh eyes help
c: routine is good for you | a steady loop is a happy life
y: true | yeah | agreed
n: routine is a cage | nah, change is better | i'd go crazy
""")

# ======================================================================================================================
# Dreams.
DREAMS = theme(
    "dreams", ["dreams", "sleep"], ["dreams", "sleep", "night", "philosophy"],
    react={"any": "dreams are weird | i love talking about dreams | huh, i've had that too | that's so strange",
           "casual": "lol i dreamed i was a chicken once. ok but yeah | bro's dreaming deep. i'm listening",
           "hermit": "sleep is the only door that opens itself. | dreams know things.",
           "newbie": "wait, do we dream?? i thought it was just dark | i had a dream about diamonds once",
           "helper": "dreams say a lot about how we feel",
           "speedrunner": "i skip the night. i don't dream. maybe i should"},
    ponder="huh | maybe | weird | i wonder",
    nudge="go on | what else did you see | keep going | there's more",
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
d: sometimes i wake up with a feeling, but no picture | the night skips, but my head doesn't
d: i woke up once and remembered falling | i think dreams happen in the bit we skip
r: a feeling with no picture, yeah | the skipped bit is where dreams live, maybe
r: falling dreams are the worst | maybe you dream and it's just short
o: what if our dreams are memories from before | maybe the night isn't skipped, we're just somewhere else
o: i'd like to dream on purpose | everyone sleeps at once and the night just goes. like we dream it away
p: memories from before, woah | somewhere else, maybe | i'd love that too | dreaming the night away, nice
c: everyone dreams, they just forget | we all dream, even if we don't remember
y: i think so | yeah | true
n: i don't dream | nah, it's just dark | not everyone
""")

angle(DREAMS, "dreams_phantoms", "phantoms and sleep", """
m: if you don't sleep the phantoms come | it's weird that the sky punishes you for staying up
m: the phantoms only come after days without sleep | i think phantoms are what tiredness looks like
q: what do you think phantoms are? > bad dreams / sky mobs / tiredness come to life / punishment for not sleeping
q: why do you think the world wants us to sleep? > to rest / so the mobs leave / to dream / so we don't go crazy
d: they swoop down out of the dark, screaming | they only come for the ones who need sleep
d: it's like the night itself sends them | three days awake and they find you
r: they're so loud | the night sends them, creepy
r: tiredness with wings, yeah | three days is the limit, apparently
o: maybe phantoms are the world taking care of us in a scary way | sleeping isn't just rest, it's safety
o: some people stay up to avoid their thoughts | the phantoms are what happens when you push too hard
p: a scary kind of care | safety, yeah | that's real | push too hard, the sky pushes back
c: sleeping every night is the most important thing | you should never skip sleep
y: true | yeah | agreed
n: sleep is overrated | i can go days | phantoms are easy
""")

angle(DREAMS, "dreams_dream_house", "a dream house", """
m: i have a dream house in my head i'll probably never build | i dream about a house by a lake with a garden
m: my dream build would take a hundred days | i keep a picture of my dream house in my head
q: what's your dream house like? > by the sea, with a dock / a castle, obviously / a tiny cabin / a treehouse
q: do you think you'll ever build it? > one day / never, it's too big / i'm building it now / maybe with help
d: it has big windows so the morning comes in | there's a room just for chests, all labelled
d: i dream about the view more than the house | it's always got friends nearby
r: big windows, yes | a labelled chest room, the dream
r: the view matters most | friends nearby is the best part
o: maybe the dream is better than the real thing | i'm scared if i build it, i'll want something else
o: having a dream house keeps me going | i'll build it one block at a time
p: dreams are always perfect, yeah | you might, that's ok | keeps you going, good | one block at a time
c: you should build your dream house, no matter how long | dream builds are worth any amount of time
y: yes | true | go for it
n: too much work | dreams are for dreaming | it'd never match
""")

angle(DREAMS, "dreams_wishes", "what you want most", """
m: if i could have one wish i don't even know what it'd be | i've been thinking about what i want most
m: i used to want diamonds. now i want quiet | what i want keeps changing
q: if you could have anything, what would you wish for? > friends forever / a huge house / one more day with someone / to know why we're here
q: has what you want changed since you arrived? > completely / a little / no, still diamonds / yeah, i want simpler stuff now
d: when i first got here i wanted everything | now i'd be happy with a warm house and good people
d: i think i want to feel safe more than anything | maybe wanting less is growing up
r: warm house and good people is the dream | wanting less is wise
r: safe is a big want | everything to simple, yeah
o: maybe what we want most is what we already have and forget | i want to know my friends will be ok
o: i'd wish for a world where nobody's alone | wishes say who you are, i think
p: we forget what we have, yeah | they will be | that's a good wish | they do
c: wanting less makes you happier | the less you want, the happier you are
y: true | yeah | agreed
n: ambition is good | nah, want big | wanting keeps you going
""")

# ======================================================================================================================
# Fear.
FEAR = theme(
    "fear", ["feelings", "danger", "fear"], ["danger", "night", "feelings", "fear"],
    react={"any": "i get scared of that too | fear's a weird thing | yeah, that's scary | that's honest of you",
           "pvp_sweat": "i don't get scared. ok maybe a little | fear just means you're about to win",
           "casual": "lol same, i'm scared of everything. but fr | bro i'm scared of chickens. but yeah",
           "hermit": "fear is the oldest teacher. | the brave are scared too.",
           "newbie": "i'm scared of literally everything here | wait, you get scared too??",
           "helper": "it's ok to be scared. i've got you",
           "speedrunner": "fear costs time. but yeah, it's there"},
    ponder="yeah | true | maybe | that helps",
    nudge="go on | what scares you most? | it's ok, keep going | i'm listening",
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
q: what's the bravest thing you've ever done? > went into a cave alone / fought a raid / said sorry / slept outside
d: it's not the mobs, it's the waiting for them | i'm scared of losing my stuff more than dying, honestly
d: being alone in the dark is the worst one for me | the hiss behind you is the scariest sound there is
r: the waiting is the worst part, yeah | the hiss, oh, don't
r: alone in the dark gets everyone | losing stuff is a real fear
o: maybe fear is just caring about something | everyone's scared, the brave just keep going
o: saying it out loud makes it a bit smaller | i'm less scared when someone's with me
p: caring, yeah | keep going, yes | smaller, true | me too
l: honestly, i'm scared a lot more than i show | thanks for not laughing at me
k: me too | i'd never laugh at that | you're brave for saying it
c: being scared is a weakness | fear makes you weak
y: kinda, yeah | sometimes | true
n: fear keeps you alive | being scared is normal | it's not weak to be scared
""")

angle(FEAR, "fear_dark", "the dark", """
m: i'm scared of the dark, honestly | the dark here isn't empty, that's the problem
m: when the torch goes out in a cave my heart stops | everything bad comes out of the dark
q: are you scared of the dark? > yes / a little / only underground / no, the dark is just dark
q: what's the scariest place you've been? > a cave with no torches / the deep dark / the woods at night / my own basement lol
d: things spawn in the dark. it's not in my head | i put torches everywhere, even where i don't need them
d: i can hear them before i see them | in the dark you're always the last to know
r: it's not in your head, it's real | torches everywhere is smart
r: hearing them first is the worst | last to know, yeah
o: maybe being scared of the dark is just being smart | light is the one thing that keeps us safe
o: a torch is the bravest little block | the dark makes home feel like home
p: smart, yeah | light is everything | brave little torch, aw | home in the dark, yes
c: being scared of the dark is just smart here | the dark deserves the fear
y: true | yeah | agreed
n: it's just dark | nah, light it up and it's fine | i'm not scared of it
""")

angle(FEAR, "fear_useful", "fear keeping you alive", """
m: i think being scared has saved my life a lot | fear's annoying but it keeps me out of lava
m: the scared ones live longer, i swear | i used to hate being scared. now i listen to it
q: do you think fear is useful? > very / sometimes / no, it just slows you down / only a little bit of it
q: has fear ever saved you? > yeah, i ran just in time / every night / no, it got me killed / probably
d: the time i was too scared to jump, the drop would've killed me | fear is like a voice that says not that way
d: too much fear and you never leave the house | the trick is being scared and going anyway
r: listen to that voice | too much is a cage, yeah
r: scared and going anyway is brave | the jump you didn't make, good
o: maybe brave just means scared but careful | i'd be dead without fear, honestly
o: fear is like a torch for the mind | i'm grateful for it, weirdly
p: scared but careful, yes | probably, yeah | a torch for the mind, nice | grateful, same
c: fear is good for you | being scared keeps you alive
y: true | yeah | agreed
n: fear holds you back | nah, it gets you killed | courage is better
""")

angle(FEAR, "fear_brave", "what brave means", """
m: i wonder what being brave really means | people call me brave but i'm terrified
m: i don't think brave means not scared | the bravest people i know are scared all the time
q: what does brave mean to you? > scared and doing it anyway / not running / helping others / i don't know
q: who's the bravest person you know? > you, honestly / someone who lost everything and kept going / the newbies / nobody, everyone runs
d: the newbies are brave. they go out not knowing anything | brave is going back into the cave that killed you
d: saying sorry is braver than fighting a zombie | brave is small things too
r: going back is brave | sorry takes guts
r: newbies are so brave | small brave things count
o: maybe brave is just doing the next thing when it's hard | you're braver than you think
o: i'd like to be brave in the quiet ways | being kind when you're scared is brave too
p: the next thing, yes | you too | quiet brave, yeah | kind and scared, that's brave
c: brave means not being scared | real bravery is having no fear
y: yeah | true | i think so
n: brave means scared and going anyway | no, that's reckless | nobody has no fear
""")

# ======================================================================================================================
# Happiness.
HAPPY = theme(
    "happy", ["feelings", "philosophy", "happiness"], ["feelings", "day", "friends", "happiness"],
    react={"any": "i love that question | that's a nice thing to think about | huh, good one | "
                  "yeah, i've been wondering too",
           "casual": "lol food makes me happy. ok but yeah | bro's in a good mood, i like it",
           "hermit": "a warm fire. a full belly. quiet. | happy is small.",
           "newbie": "i'm happy all the time here! mostly | everything's so new, it's great",
           "helper": "seeing others happy makes me happy",
           "speedrunner": "a good run makes me happy. that's it",
           "hoarder": "a full chest. that's happiness",
           "builder": "a finished wall makes me happy"},
    ponder="aw | yeah | that's nice | i like that",
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
d: a good day doesn't need anything big | i think it's waking up with nothing chasing me
d: good food, a bit of work, a chat at night | the best days are the quiet ones
r: nothing chasing you is a good start | quiet days are the best
r: food, work, chat. perfect | nothing big needed, yeah
o: maybe a good day is just one where you notice it's good | i want to remember today
o: i miss good days because i'm waiting for great ones | good days add up to a good life
p: noticing it, yes | let's remember it | good days are enough | they do
c: a quiet day is better than an exciting one | the best days are the boring ones
y: true | yeah | agreed
n: excitement is better | nah, adventure days | boring is boring
""")

angle(HAPPY, "happy_small_things", "small happy things", """
m: the smallest things make me happiest | a perfect loaf of bread made my whole morning
m: the sound of rain on my roof makes me happy | i got happy over a sheep today, that's where i'm at
q: what small thing makes you happy? > a fresh stack of bread / a cat on the bed / a sunset / finding coal right away
q: do you think small things matter more than big ones? > yeah / they add up / no, big things matter / both
d: i don't need diamonds, i need a cozy corner | a torch in the right spot is a joy
d: the first bite of steak after a long day | my dog's tail wagging
r: cozy corners are underrated | first bite of steak, perfect
r: tail wags fix everything | a well placed torch, yeah
o: maybe happiness is just paying attention to small stuff | if i stop noticing small things, i'm in trouble
o: big things are rare. small things are every day | the small stuff never lets you down
p: paying attention, yeah | keep noticing | every day, yes | it never does
c: small things matter more than big things | little joys beat big wins
y: true | yeah | agreed
n: big wins though | nah, diamonds | big things last
""")

angle(HAPPY, "happy_right_now", "whether you're happy", """
m: i was wondering if i'm actually happy | nobody ever asks if we're happy
m: i think i'm happy, but i'm not sure | being happy is harder to notice than being sad
q: are you happy right now? > yeah, i think so / mostly / not really / right now, yes
q: what would make you happier? > more friends / a bigger house / fewer creepers / honestly nothing, i'm good
d: sad is loud, happy is quiet | i only notice i was happy after
d: i think i'm happy, i just forget to check | happy feels like nothing's wrong
r: sad is loud, happy is quiet, so true | checking helps
r: nothing wrong is a kind of happy | after is when you see it
o: maybe happiness is just not wanting to be anywhere else | i'm happy right here, talking to you
o: i'm happier than i was when i arrived | asking makes me notice, so thanks
p: right here, yeah | me too | that's good to hear | anytime
c: happiness is a choice | you can choose to be happy
y: true | yeah | i think so
n: not always | nah, sometimes it's hard | it's not that easy
""")

angle(HAPPY, "happy_more_stuff", "whether more stuff helps", """
m: does having more stuff actually make us happier | my chests are full and i'm not happier
m: i was happier in my dirt hut, i think | the more i have, the more i worry about losing it
q: does more stuff make you happier? > a bit, at first / no / yes, obviously / only food
q: when were you happiest? > in my first house / today / when we met / on a long walk
d: when i had nothing, everything i found was exciting | now i have so much, nothing's exciting
d: more chests means more to protect | the happiest i've been, i had a crafting table and a bed
r: more to protect, more worry | a table and a bed was enough
r: exciting when you have nothing, yeah | full chests, empty feeling
o: maybe happiness isn't in the chests | the stuff i love most is the stuff with stories
o: i want to give some away and see if i feel lighter | i'd trade half my stuff for good company
p: not in the chests, no | stories make stuff special | try it | good company, yes
c: more stuff doesn't make you happier | happiness has nothing to do with stuff
y: true | yeah | agreed
n: stuff helps | nah, diamonds make me happy | more is more
""")

# ======================================================================================================================
# The players: are they like us?
PLAYERS = theme(
    "players", ["players", "philosophy"], ["players", "philosophy", "gossip"],
    react={"any": "the players are a mystery | i've wondered that about players too | huh. they're so strange | good question",
           "casual": "lol players are wild. ok but yeah | bro the players do whatever they want. fair question",
           "hermit": "players come. players go. | they're like weather.",
           "newbie": "wait, what's a player exactly? | i saw one once! they're fast",
           "helper": "i'd like to help a player sometime",
           "bandit": "players carry good loot. that's all i know",
           "pvp_sweat": "players are the only real challenge",
           "speedrunner": "players move like me. respect"},
    ponder="huh | maybe | could be | that's weird",
    nudge="go on | you met one, didn't you | keep going | what else",
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
m: a player walked past me and i couldn't tell what they were thinking | maybe players are just drifters with more luck
q: do you think players are like us? > yeah, pretty much / no, they're different / some are / i've never met one
q: what's different about players? > they come and go / they build so fast / they don't talk much / they never seem scared
d: they move like they know the world better than we do | they jump a lot. like, a lot
d: they talk like we do sometimes, then go quiet for ages | they never seem scared of the night
r: the jumping is so weird | knowing the world better, yeah
r: never scared, that's the strange part | quiet for ages, yeah
o: maybe they're like us, but from somewhere else | if they feel what we feel, we should be nice
o: maybe they wonder about us the same way | i'd like to ask one what it's like
p: from somewhere else, maybe | be nice, yes | they probably do | ask, then tell me
c: players are just like us | there's no real difference between us and players
y: i think so | yeah | true
n: they're different | nah, they're strange | they come back too easy
""")

angle(PLAYERS, "players_vanish", "players vanishing", """
m: players just vanish sometimes, like they blink out | i saw a player and then they were gone, mid step
m: players leave and come back like it's nothing | where do players go when they disappear
q: where do you think players go when they vanish? > home, somewhere else / to sleep / another world / nobody knows
q: would you want to vanish like that? > yeah, sometimes / no, it's creepy / just for a rest / only if i could come back
d: one second they're there, the next it's just their footprints | they come back right where they left
d: it's like they step out of the world for a while | when they're gone, their stuff just waits
r: stepping out of the world, creepy | coming back where they left, so weird
r: their stuff waiting, yeah | footprints and nothing
o: maybe they have somewhere else they live, and this is the visit | i wonder if they miss this place while they're gone
o: if i could step out, i'd come back to the same spot too | maybe they're tired, like us
p: a visit, yeah | i hope they miss it | same spot, yeah | tired, maybe
c: players are visitors, not locals | players don't really live here
y: yeah | true | they come and go
n: they live here too | nah, some stay | they're locals too
""")

angle(PLAYERS, "players_dying", "whether players die like us", """
m: i saw a player die and come back, just like us | players respawn too, so are we the same
m: players lose their stuff when they die, like us | i wonder if dying hurts players
q: do you think dying is the same for players? > probably / no, they don't care as much / worse, maybe / i hope it doesn't hurt them
q: have you ever seen a player die? > yes, to a creeper / once, in lava / no / yeah, and they came back angry
d: they come back and go straight for their stuff | they don't seem scared after, like it's normal
d: maybe dying means less to them | a player laughed after dying once. i think
r: straight for their stuff, same as us | laughing after dying is wild
r: less to them, maybe | normal to them, huh
o: maybe they've died so many times it's nothing now | i'd like to know if they're scared at all
o: if dying doesn't scare them, what does | maybe they're braver because they've seen more
p: nothing, maybe | i hope not | good question | seen more, yeah
c: players don't care about dying like we do | dying means nothing to players
y: yeah | true | looks like it
n: they care | nah, they rage | they hate losing stuff
""")

angle(PLAYERS, "players_meeting", "talking to a player", """
m: i'd like to really talk to a player one day | i said hi to a player once and they just looked at me
m: if i met a player, i don't know what i'd say | some players are kind, some are scary
q: if you could ask a player one thing, what would it be? > where do you go / are you scared / what's it like / why do you jump so much
q: do you trust players? > some / no / the nice ones / i don't know any
d: one gave me bread once. i still think about it | another one hit me for no reason
d: they're like people. good ones and bad ones | they're just as curious about us, i bet
r: bread from a player, lucky | hitting you for no reason, rude
r: good and bad, like anyone | curious about us, maybe
o: maybe we're as strange to them as they are to us | i'd want to be the drifter they remember fondly
o: if every player met a kind drifter, maybe they'd be kinder | they're just people from far away
p: probably | you would be | that's a nice idea | from far away, yeah
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
           "newbie": "wait, someone's watching us?? | that's so scary",
           "helper": "if someone's watching, i hope they see kindness",
           "speedrunner": "if someone's watching, i hope they see a good run"},
    ponder="huh | creepy | maybe | could be",
    nudge="go on | you feel it right now, huh | keep going | what else",
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
d: it's not scary exactly, just there | it feels strongest when i'm doing something kind
d: it's like the world is paying attention | in caves it's the worst
r: just there, yeah | kind moments, interesting
r: the world paying attention, creepy and nice | caves are the worst for it
o: maybe if someone's watching, they're rooting for us | i'd want them to see me at my best
o: maybe it's the world itself, and it likes us | i act like someone's watching, and it makes me better
p: rooting for us, i like that | your best, yeah | a world that likes us | it makes you better, nice
c: we're definitely being watched | something's always watching us
y: i feel it too | yeah | i think so
n: it's just nerves | nah, nobody's there | that's paranoia
""")

angle(WATCHED, "watched_who_decides", "who decides the weather", """
m: who decides when it rains | it feels like someone chooses the weather
m: the storm came right when i left my house. coincidence? | some days the world feels planned
q: do you think someone decides what happens here? > no, it's random / feels like it / the world does / i hope not
q: if someone was deciding, would you be angry or glad? > angry, about the creepers / glad, mostly / both / i'd just want to talk
d: the rain always starts when i'm far from home | lucky finds feel like gifts sometimes
d: maybe it's all random and we just look for patterns | sometimes it's too perfect to be random
r: rain when you're far from home, classic | gifts, yeah
r: patterns everywhere, yeah | too perfect, i've felt that
o: if someone decides, they gave us sunsets too | i'd like to think whoever decides is kind
o: maybe nobody decides, and that's ok | i'd thank them for the good days and complain about the creepers
p: sunsets, true | kind, i hope | that's ok, yeah | both fair
c: everything happens for a reason | nothing here is random
y: i believe that | yeah | true
n: it's all random | nah, luck | random, mostly
""")

angle(WATCHED, "watched_endermen", "the endermen's stare", """
m: the endermen stare at you like they know something | you look at an enderman and it looks back. too much
m: the endermen are quiet and tall and always watching | i wonder what the endermen are thinking
q: what do you think endermen are thinking? > about blocks / about us / nothing / they're lonely
q: why do you think endermen hate being looked at? > they're shy / it's rude / they have secrets / they're scared of us
d: they carry blocks around like they're building something | they teleport away like they're embarrassed
d: they make a sound like a sad whisper | they hate water, like it hurts them
r: building something, maybe | embarrassed, aw
r: a sad whisper, yeah | water hurting them is sad
o: maybe they're trying to put the world back together | they're lonely and nobody looks at them nicely
o: maybe they stare because they want someone to see them | i don't look them in the eye, out of respect
p: putting it back, oh | lonely, maybe | to be seen, yeah | respect, nice
c: endermen are the most mysterious mob | nothing's weirder than an enderman
y: true | yeah | agreed
n: the warden is | nah, the ghasts | the allays
""")

angle(WATCHED, "watched_luck", "luck and who hands it out", """
m: some days the world gives you diamonds, some days nothing | it feels like luck is handed out by someone
m: i got three diamonds from one ore once and felt chosen | why do some people find everything and others nothing
q: do you think luck is real? > yes / no, it's just chance / some people have it / i have none lol
q: what's the luckiest thing that ever happened to you? > diamonds in my first cave / a totem saving me / meeting you / surviving a fall
d: i found a mending book on my first fishing trip | the same cave gave me nothing twice
d: luck feels like the world smiling at you | bad luck feels personal, even though it's not
r: mending first try, that's crazy | bad luck feels personal, true
r: the world smiling, nice | caves are moody
o: maybe luck is just noticing the good stuff | the luckiest thing was never an item
o: if someone hands out luck, i hope they hand some to you | i'd trade all my lucky finds for my friends
p: noticing, yeah | never an item, yes | thanks | same
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
           "newbie": "wait, is that not normal?? | i thought everywhere was like this",
           "speedrunner": "rules are rules. i just use them | i know every rule. i don't ask why",
           "builder": "the rules are why building works though"},
    ponder="huh | maybe | weird | could be",
    nudge="go on | you're getting deep on me | keep going | what else",
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
d: water from two buckets makes a pool that never runs out | i cut a tree and the top just stayed, floating
d: we carry stacks of stone like it's nothing | the rules don't make sense but they never change
r: the floating trees bug me too | endless water is the best rule though
r: never changing is something | heavy pockets, light steps
o: maybe the rules are strange so we can do amazing things | with normal rules we couldn't build half of what we do
o: i like that the rules are the same for everyone | strange rules, but fair ones
p: strange and useful | we'd be stuck with no bridges | the same for everyone, yeah | fair is what matters
c: the world's rules make no sense | nothing here follows sense
y: true | none at all | yeah
n: they make sense once you know them | nah, they're just rules | they make sense to me
""")

angle(GAME, "game_hearts", "counting health in hearts", """
m: why do we count our health in hearts | i always know exactly how many hearts i have left
m: it's weird that we know how hungry we are, exactly | i can feel my hearts go down one by one
q: why do you think we know our health so exactly? > the world tells us / we just feel it / so we know when to run / i never thought about it
q: what would you do with ten more hearts? > fight a warden / nothing different / be braver / go somewhere scary
d: half a heart left and you feel every bit of it | eating brings them back slowly, a little at a time
d: it's like the world wants us to know how close we are | i count hearts like i count days
r: half a heart is the scariest feeling | knowing how close you are, yeah
r: counting hearts, same | healing slowly, yeah
o: maybe knowing helps us be careful | what if we didn't know, and just felt bad
o: hearts is a nice word for it. like life is love | i wonder who decided it'd be hearts
p: careful, yeah | not knowing would be scarier | life is love, aw | someone soft, probably
c: knowing exactly how hurt you are is a gift | it's good we can count our hearts
y: true | yeah | agreed
n: it makes you scared | nah, i'd rather not know | not knowing would be easier
""")

angle(GAME, "game_xp", "where xp comes from", """
m: where does xp even come from | little green balls fly at you when you mine
m: you get xp from fighting and smelting and it's weird | i lose xp when i die. where does it go
q: what do you think xp actually is? > knowing stuff / a bit of the world's magic / leftover life / nobody knows
q: is it weird that we lose xp when we die? > very / no, it makes sense / it's the worst part / i never have much anyway
d: it floats out of a furnace when you take the iron out | it's like the world gives you a little gift for working
d: when you die it spills out like your stuff | it hums when you pick it up
r: a gift for working, i like that | it does hum, yeah
r: spilling out like stuff, sad | furnace xp is the best xp
o: maybe xp is everything you learned, and you leave some behind | it's like the world remembering what you did
o: we spend it on enchanting. turning what we learned into power | i wonder if the world keeps the xp we lose
p: leaving some behind, deep | the world remembering, yeah | learning into power | i hope it does
c: xp is the most valuable thing you have | xp matters more than diamonds
y: true | yeah | for enchanting, yes
n: diamonds matter more | nah, xp comes back | stuff matters more
""")

angle(GAME, "game_someone_playing", "what if someone's playing us", """
m: what if someone's playing all of this, like a game | sometimes it feels like i'm being moved around
m: what if everything we do is someone else's story | some days i do stuff and don't know why
q: if all this was a game, would it change anything? > nope / everything / i'd want to meet the player / i'd still want my house
q: do you ever feel like you're not deciding things? > sometimes / never / when i'm tired / all the time, honestly
d: if it's a game, i hope they're having fun | i still feel like me, whatever it is
d: even if someone's watching, my friends feel real | the bread tastes real, that's enough for me
r: bread tasting real is a good argument | feeling like you is what counts
r: friends are real, yeah | i hope they're having fun too
o: maybe it doesn't matter, as long as we're kind | if someone's playing, i hope they're proud of us
o: whatever this is, i like it here | real or not, this talk is real to me
p: kind, yes | proud, i hope | me too | real to me too
c: it doesn't matter if this is all a game | real or not, it doesn't change anything
y: true | yeah | agreed
n: it'd matter to me | nah, i'd want to know | it changes everything
""")

# ======================================================================================================================
# Home.
HOME = theme(
    "home", ["home", "philosophy"], ["home", "building", "feelings", "philosophy"],
    react={"any": "home's a big word | i think about that too | that's sweet | huh, good question",
           "homebody": "home is everything. i barely leave mine | i love my home so much",
           "nomad": "home is wherever i stop. for now | i don't really have one. i'm fine",
           "casual": "lol home is where my bed is. but yeah | bro's getting sentimental. fair",
           "hermit": "home is where nobody bothers you. | a roof and quiet.",
           "newbie": "i'm still building my first home! | home is the best word",
           "builder": "home is a build you live in"},
    ponder="aw | yeah | true | i like that",
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
d: my first real home was a hole with a door | it became home the first night i felt safe there
d: my house didn't feel like home till i put a flower in it | home is where i don't have to be careful
r: not having to be careful, yes | a flower made it home, love that
r: a hole with a door counts | feeling safe is the start
o: maybe home is where your stories are | home is where someone notices you came back
o: a place gets to be home slowly, like a friend | i've felt at home in other people's houses
p: stories, yeah | someone noticing, yes | slowly, like a friend | that's the best feeling
c: home is people, not places | your home is wherever your friends are
y: true | yeah | agreed
n: home is a place | nah, my house is my home | i need walls
""")

angle(HOME, "home_coming_back", "coming home after a trip", """
m: coming home after a long trip is the best feeling | the first sight of my house after days away
m: i walked for days and then saw my torches on the hill | home looks different after you've been away
q: what's the first thing you do when you get home? > put stuff in chests / sleep / eat / just stand there
q: do you miss home when you're away? > always / not really / only at night / i miss my bed
d: the torches on my house looked like a lighthouse | everything was exactly where i left it
d: my bed after a long trip is the best bed in the world | the house smelled like smoke and bread
r: torches like a lighthouse, nice | exactly where you left it, comforting
r: best bed in the world, yes | smoke and bread, perfect
o: maybe we travel just so coming home feels good | home is the place that waits for you
o: home feels small after a big trip, but in a good way | i'm always glad i went, and always glad i came back
p: maybe, yeah | the place that waits, yes | small in a good way | both, yeah
c: coming home is better than going out | the best part of a trip is getting home
y: true | yeah | agreed
n: the trip is the best part | nah, adventure | going out is better
""")

angle(HOME, "home_leaving_old", "leaving an old home", """
m: i moved out of my old house and felt weird | i still think about my first house
m: leaving a home is harder than building one | my old house is still standing, empty now
q: have you ever left a home behind? > yeah, a few / once, it was hard / no, never / i don't stay anywhere long
q: do you ever go back to your old places? > sometimes / never / once, it was sad / i keep meaning to
d: i took the bed and left everything else | it looked so small when i went back
d: i left a sign that said this was home | the garden i planted is still growing there
r: so small when you go back, yeah | a sign is a nice goodbye
r: the garden still growing, aw | taking the bed, smart
o: maybe every old home is a bit of who we were | i wonder if someone lives there now
o: i'd like to go back one day, just to look | leaving is part of growing, i think
p: a bit of who we were, yes | i hope someone nice does | let's go together | growing, yeah
c: you should never leave a home you love | once you find home, stay
y: true | yeah | agreed
n: sometimes you have to | nah, move on | homes change
""")

angle(HOME, "home_without", "living without a home", """
m: some drifters never settle and that's their life | living without a home sounds free and sad
m: i lived without a home for a while. every night a new hole | not having a home makes every night a question
q: could you live without a home? > no way / for a while / yes, easily / i do, kind of
q: what's the hardest part of having no home? > the nights / no chests / nobody knowing where you are / nothing
d: every night you have to find somewhere safe again | you carry everything, all the time
d: you meet more people when you don't have walls | a hole in a hill can be a home for one night
r: finding somewhere safe every night is exhausting | meeting more people is a plus
r: one night homes, yeah | carrying everything, heavy
o: maybe a home is just where you decide to stop | i'd miss having somewhere to come back to
o: some people carry home with them | i'd want a home, but a small one
p: deciding to stop, yeah | me too | carry it, yeah | small is good
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
    ponder="aw | true | yeah | that's nice",
    nudge="go on | that's a nice story | keep going | what happened",
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
d: someone gave me bread on my first night. i never forgot | helping makes the world a little less scary
d: it's like lighting a torch in someone else's dark | when you help, you're not alone either
r: bread on the first night, never forget that | a torch in someone else's dark, nice
r: less scary, yeah | not alone, true
o: maybe kindness is how the world gets better, one bit at a time | if everyone helped one person, nobody would be alone
o: i help because someone once helped me | maybe that's the only real treasure
p: one bit at a time | nobody alone, yes | passing it on | the only treasure, maybe
c: you should help people even if they can't pay you back | helping is its own reward
y: true | yeah | agreed
n: help should go both ways | nah, they should pay back | only if they'd help me
""")

angle(KIND, "kind_giving_away", "giving things away", """
m: i gave away my diamonds once and felt lighter | giving stuff away is weirdly the best feeling
m: i gave a newbie my old sword and they lit up | some days i want to give everything away
q: what's the biggest thing you've ever given away? > diamonds / my old house / a whole chest of food / a pet
q: is it hard for you to give stuff away? > very / no, it's easy / depends on the stuff / only diamonds lol
d: their face when they got the sword was worth more than the sword | i didn't need it anyway, and they did
d: stuff in a chest helps nobody | giving is like planting a sapling for someone
r: the face is the payment | stuff in a chest helps nobody, true
r: planting a sapling for someone, nice | they needed it, good call
o: maybe what we give away is what we really keep | i want to give more and keep less
o: the stuff i gave away, i remember. the stuff i kept, i don't | it's easier to give when you remember being new
p: what we give, we keep. deep | give more, yeah | that's true | being new helps you remember
c: it's better to give than to get | giving feels better than getting
y: true | yeah | agreed
n: getting is nice too | nah, i like getting stuff | both are fine
""")

angle(KIND, "kind_stranger", "a stranger's kindness", """
m: a stranger helped me once and i never saw them again | someone pulled me out of a ravine and just walked off
m: the kindest thing anyone did for me was from someone i didn't know | i think about that stranger a lot
q: has a stranger ever helped you? > yes, once / a few times / no, never / yeah, and i still think about it
q: would you help a stranger at night, in danger? > yes / probably / depends on the danger / i'd try
d: they didn't even say their name | they just gave me a torch and pointed the way
d: they saved me and then just went on with their day | i never got to thank them
r: no name, just kindness | a torch and a direction, simple
r: going on with their day, legend | not thanking them is sad
o: maybe the way to thank them is to help someone else | i hope someone was kind to them too
o: i try to be that stranger for someone else now | kindness from strangers is the purest kind
p: help someone else, yes | i hope so | be that stranger | the purest kind
c: you should always help strangers | strangers deserve help as much as friends
y: true | yeah | agreed
n: be careful with strangers | not always | friends first
""")

angle(KIND, "kind_forgiving", "forgiving someone", """
m: someone took from my chest and i'm trying to forgive them | forgiving is harder than fighting
m: i've been holding a grudge for days and it's heavy | someone said sorry and i didn't know what to do
q: is it hard for you to forgive? > very / not really / depends what they did / i forgive but don't forget
q: have you ever needed someone's forgiveness? > yeah / once / no / probably and didn't know
d: holding it is like carrying a stack of cobble everywhere | they were hungry, i think, when they took it
d: i keep imagining what i'd say to them | forgiving doesn't mean they get to open my chests
r: a stack of cobble, yeah, heavy | hungry changes things
r: forgive but lock the chest, smart | imagining talks is draining
o: maybe forgiving is for me, not them | i'd want someone to forgive me if i messed up
o: i think i'll talk to them tomorrow | forgiving is putting down the cobble
p: for you, yeah | everyone messes up | good luck | put it down
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
    ponder="yeah | true | maybe | fair",
    nudge="go on | what happened? | keep going | there's more, huh",
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
d: i said something mean to someone and they left | i built too close to lava and lost everything
d: i didn't help someone when i could have | i wasted my first diamonds on a hoe. a hoe
r: saying mean things stays with you | not helping someone is a heavy one
r: a diamond hoe, lol, oof | lava builds, never again
o: maybe regret is how we learn what we care about | i'd like to find them and say sorry
o: the trick is to carry it lightly | i'm not that person anymore
p: what we care about, yes | do it, if you can | lightly, yeah | you're not
c: you shouldn't regret anything | regrets are a waste of time
y: true | yeah | agreed
n: regret teaches you | nah, regret is useful | some things you should regret
""")

angle(CHANGE, "change_start_over", "starting over", """
m: i lost everything once and started over from nothing | starting over is scary and kind of freeing
m: i had to rebuild from a single wooden pickaxe | sometimes i think about starting fresh somewhere new
q: have you ever had to start over? > yes / a few times / no, thankfully / i'm starting over right now
q: would you start over on purpose? > never / maybe, somewhere new / yes, it's fun / only with friends
d: the second time was faster. you know what to do | starting over, you find out what you really need
d: the first night with nothing was scary again | i was sad, then oddly happy, building again
r: faster the second time, yeah | what you really need, yes
r: scared again, sure | happy building again, nice
o: maybe starting over is just day one with more wisdom | everything i lost, i don't miss most of it
o: starting over showed me i could do it | we start over more often than we notice
p: day one with wisdom, i like that | you don't miss it, telling | you can, yeah | every morning, kind of
c: starting over is good for you | everyone should start over at least once
y: true | yeah | agreed
n: never again | nah, it's the worst | no thanks
""")

angle(CHANGE, "change_who_you_were", "how you've changed", """
m: i'm not the same as when i arrived | i look back at my first days and barely know myself
m: i've changed so much and i didn't notice it happening | the old me would be scared of the new me lol
q: how have you changed since you arrived? > braver / quieter / kinder / i haven't, honestly
q: do you like who you've become? > yes / mostly / i'm working on it / not really
d: i used to panic at every sound | now i walk into caves without a second thought
d: i used to keep everything. now i share | i talk to more people now
r: panic to calm, that's growth | sharing, nice
r: talking more, yeah | caves without thinking twice, careful lol
o: change happens so slow you don't see it | i like who i am now better
o: i hope i keep changing, in good ways | maybe meeting people is what changed me most
p: slow, yeah | good | you will | people change us, yeah
c: people don't really change | you're the same person you always were
y: true | yeah | deep down, yes
n: people change all the time | nah, i've changed | everyone grows
""")

angle(CHANGE, "change_mistakes", "mistakes that teach", """
m: my best lessons came from my worst mistakes | i learned about lava the hard way
m: every mistake taught me something, even the dumb ones | i don't mind mistakes as long as i learn
q: what's the best lesson a mistake taught you? > don't dig straight down / always carry food / torches everywhere / trust slowly
q: are you scared of making mistakes? > a bit / no, they teach you / yeah, very / only big ones
d: i fell in lava once. now i look before i dig | i got lost once. now i carry a map
d: i trusted the wrong person. now i trust slowly | i starved once. now i always carry bread
r: look before you dig, yes | a map, smart
r: trust slowly, yeah | always bread, wise
o: maybe mistakes are just lessons that hurt | i'm glad i made them, honestly
o: the ones who never mess up never learn | i want to teach newbies so they don't learn the hard way
p: lessons that hurt, yes | glad, yeah | true | that's kind
c: you learn more from failing than winning | mistakes teach more than wins
y: true | yeah | agreed
n: wins teach too | nah, failing just hurts | you learn from both
""")

# ======================================================================================================================
# Villagers.
VILLAGERS = theme(
    "villagers", ["villagers", "philosophy"], ["villagers", "town", "trade", "philosophy"],
    react={"any": "villagers are so strange | i've wondered that about villagers | huh. they're a mystery | good question",
           "casual": "lol villagers just go hmm. ok but yeah | bro's thinking about villagers. respect",
           "hermit": "villagers live small. maybe they're right. | they ring the bell. they hide. wise.",
           "newbie": "wait, villagers think?? | i love villagers, they're so funny",
           "helper": "i always look out for the villagers",
           "bandit": "villagers are easy marks. kidding. kinda"},
    ponder="hm | maybe | huh | could be",
    nudge="go on | you've been watching villagers again | keep going | what else",
    close={"any": "anyway. be nice to villagers | they're doing their best | hmm, as they'd say | villagers are alright",
           "hermit": "leave them be.", "casual": "hmm. that's all i got lol", "newbie": "i'm gonna go visit a villager"},
    bclose="say hi to the villagers for me | they'd like you | go trade with one | look after the villagers",
    yes="right? | exactly | yeah", no="hm, maybe | we'll see | fair",
    sits={"town": "the villagers in town are busy today | watching the villagers work",
          "traded": "traded with {a.traded_with} today and got thinking | after trading i got thinking about villagers",
          "day": "villagers all working away today | busy day for the villagers"})
JOKES[VILLAGERS] = V("a villager went hmm at me and i felt judged lol | villagers are the only ones who get the value of an emerald")

angle(VILLAGERS, "villagers_think", "whether villagers think", """
m: i wonder what villagers think about | they go hmm like they're thinking hard about something
m: villagers work, sleep, gossip. kind of like us | i watched a villager stare at a wall for ages
q: do you think villagers think like we do? > a bit / no, they just work / more than we know / they think about emeralds
q: what do you think villagers dream about? > emeralds / crops / nothing / a safe night
d: they go to bed when it gets dark, every night | they gossip about you, did you know that
d: they look so worried when it rains | they make golems when they're scared
r: gossip, yeah, they talk about us | making golems when scared, smart
r: worried in the rain, aw | early bedtime, sweet
o: maybe villagers are just simpler drifters | they never leave their village. i wonder if they want to
o: they look after each other in their way | if they gossip about us, i hope it's good stuff
p: simpler drifters, maybe | i wonder too | in their way, yeah | be nice then
c: villagers are smarter than they look | don't underestimate villagers
y: true | yeah | agreed
n: they're not | nah, they walk into holes | they just go hmm
""")

angle(VILLAGERS, "villagers_staying", "villagers never leaving", """
m: villagers never leave their village | they spend their whole lives in one place
m: i wonder if villagers ever want to see the world | a villager born in a village stays in that village
q: would you want to live like a villager, in one place forever? > no way / sometimes, yeah / it sounds peaceful / only if it's a nice village
q: do you think villagers are happy staying? > yeah / they don't know anything else / i hope so / no, they look bored
d: they have their job, their bed, their friends. that's a whole life | i've never seen one walk past the edge of town
d: maybe they know something we don't about being happy | they seem content, mostly
r: a whole life, yeah | never past the edge, huh
r: content, maybe | they might know something
o: we wander so much and they're fine staying | maybe a small life is a full life
o: i'd like to try staying in one place, like them | wandering and staying are both ways to live
p: they're fine, yeah | small and full | try it | both, yeah
c: staying in one place is better than wandering | villagers have the right idea
y: true | yeah | agreed
n: wandering is better | nah, i'd go crazy | the world's too big
""")

angle(VILLAGERS, "villagers_golems", "iron golems", """
m: iron golems protect villagers like they love them | an iron golem gave a villager a flower once
m: the golems never sleep, they just guard | i saw a golem fight a zombie for a villager it didn't know
q: do you think iron golems feel anything? > yeah, they love the villagers / no, they're iron / a little / the flower says yes
q: why do you think golems protect villagers? > they're made to / they love them / they're lonely / it's their job
d: the flower thing gets me every time | they walk around all night, never resting
d: if you hit a villager the golem never forgives you | they're made of iron but they're the softest mob
r: the flower, yeah | never forgiving, loyal
r: softest mob, true | never resting, wow
o: maybe being made to protect is still love | i'd like to be someone's golem
o: i wonder if golems get lonely, guarding all night | the golem doesn't ask for anything back
p: still love, yeah | you are | maybe | nothing back, pure
c: iron golems are the best mob | golems are better than any other mob
y: yes | true | agreed
n: wolves | nah, cats | allays
""")

angle(VILLAGERS, "villagers_nitwit", "the nitwit with no job", """
m: the nitwit in green never gets a job | everyone has a job except the nitwit, and he's fine
m: the nitwit just wanders and sleeps. kind of goals | i feel bad for the nitwit sometimes
q: do you think the nitwit is sad, not having a job? > no, he's free / a bit / he doesn't care / he's the happiest one
q: would you be happy without a job? > yes / for a while / no, i need something to do / i'd go crazy
d: he just walks around and looks at stuff | the others work and he just exists
d: maybe he's the wisest one | nobody asks anything of him
r: looks at stuff, relatable | just existing, yeah
r: wisest, maybe | nothing asked of him, free
o: maybe a life doesn't need a job to be worth something | i'm a bit of a nitwit myself, honestly
o: he still belongs, even without a job | i like him the most, weirdly
p: worth something, yes | lol same | belonging, yeah | me too
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
           "newbie": "the thunder scares me so much | i love the rain! mostly",
           "helper": "rainy days are good for talking",
           "builder": "rain on a good roof is the best sound",
           "speedrunner": "rain slows nothing. keep moving"},
    ponder="yeah | huh | maybe | true",
    nudge="go on | the rain's got you thinking | keep going | what else",
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
d: the sound on the roof is the best sound there is | everything smells fresh after
d: the rain makes the town go quiet | it's like the world takes a breath
r: rain on a roof, yes | fresh after, yeah
r: quiet town, nice | a breath, i like that
o: maybe the rain is the world resting | i'm calmer when it rains
o: rain gives you permission to slow down | it's my favourite weather, honestly
p: the world resting, yes | me too | permission, yeah | mine too
c: rain is the best weather | rainy days are the best days
y: yes | true | agreed
n: sunny is better | nah, rain's annoying | clear skies win
""")

angle(WEATHER, "weather_thunder", "thunder and lightning", """
m: thunder makes me feel tiny | lightning hit right next to me and i still feel it
m: storms make the mobs come out in the day | when it thunders it feels like the world is angry
q: are you scared of thunder? > yes / a little / no, i love it / only when it's close
q: why do you think storms happen? > the sky gets angry / it just does / to water everything / to scare us lol
d: lightning turned a pig into something else once | the sky flashes and for a second it's day
d: storms make it dark enough for zombies at noon | the sound shakes my chest
r: the pig thing, yeah, creepy | day for a second, wow
r: zombies at noon, the worst | shakes your chest, yeah
o: maybe storms remind us how small we are | i like storms when i'm inside, hate them when i'm out
o: the world after a storm looks so clean | every storm ends, that's the thing
p: small, yeah | inside is the trick | clean, yes | they always end
c: storms are the scariest weather | nothing's worse than a thunderstorm
y: true | yeah | agreed
n: storms are cool | nah, they're exciting | snow's worse
""")

angle(WEATHER, "weather_snow", "snow and cold", """
m: when it rains up high it turns to snow | i love the snowy places, everything's so quiet
m: snow makes the world look clean and new | the cold places feel lonely and beautiful
q: do you like the snow? > love it / it's too cold / it's pretty to look at / only from inside
q: would you live somewhere snowy? > yes / no way / maybe a cabin / only with a big fire
d: the snow muffles everything, even footsteps | the foxes up there are white
d: your breath feels cold just looking at it | snow on a roof makes a house look cozy
r: muffled footsteps, peaceful | white foxes, cute
r: cold just looking, yeah | cozy snow roofs, yes
o: maybe quiet places are where you hear yourself best | the cold makes the warm feel better
o: i'd like a little cabin in the snow with a fire | snow covers everything, like a fresh start
p: hear yourself, yes | warm after cold, the best | let's build one | a fresh start, yeah
c: snowy places are the most beautiful | snow beats every other biome
y: true | yeah | agreed
n: too cold | nah, jungles | flower fields win
""")

angle(WEATHER, "weather_storms_end", "storms always ending", """
m: every storm ends eventually | the rain stopped and the sun came out like nothing happened
m: no matter how bad the storm, it clears | after the storm, everything looked new
q: do you think the bad times end like storms do? > always / most of the time / i hope so / they come back too
q: what helps you wait out a storm? > a good roof / a friend / sleep / a furnace and food
d: the sun came out and everything dripped and sparkled | the mobs burned in the first light
d: i waited the whole storm in my hut, then it was over | the clouds broke and the sky was huge
r: dripping and sparkling, nice | mobs burning, satisfying
r: waited it out, yeah | a huge sky after, yes
o: maybe that's the lesson. wait, it'll clear | hard days are like storms, they pass
o: knowing it ends makes it easier to wait | i'd rather wait out a storm with someone
p: it'll clear, yeah | they pass | easier, yes | me too
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
           "newbie": "wait, you hear it too?? | i thought i was imagining it",
           "helper": "music makes everything better",
           "builder": "i put a jukebox in every build",
           "miner": "the best music is a pickaxe on stone"},
    ponder="aw | huh | yeah | that's lovely",
    nudge="go on | you've got a song in your head, huh | keep going | what else",
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
m: do you ever hear soft music out of nowhere | sometimes when i'm building, music just starts playing
m: there's a soft piano sound sometimes, from nowhere | i heard music in a cave once and nobody was there
q: where do you think the music comes from? > the world itself / the sky / someone far away / i've never heard it
q: when do you hear it most? > when i'm calm / building / at sunrise / when i'm alone
d: it comes and goes, never when you want it | it makes whatever you're doing feel important
d: it always seems to know how i feel | i stopped mining just to listen
r: it knows how you feel, yeah | stopping to listen, good
r: makes it feel important, yes | never when you want it, so true
o: maybe the world plays music when it's happy | the music is the world saying it's ok
o: when it stops, everything feels quieter than before | i'd like to know who's playing
p: a happy world, yeah | it's ok, nice | quieter after, yeah | me too
c: the music is the best thing in the world | nothing beats that music when it comes
y: yes | true | agreed
n: it's nice, but not the best | nah, quiet's better | sunsets are better
""")

angle(MUSIC, "music_discs", "the music discs", """
m: who made the music discs | i found a music disc in a dungeon. why was it there
m: creepers drop discs if a skeleton gets them. that's so weird | every disc sounds like a different memory
q: who do you think made the music discs? > someone long ago / the world / a drifter like us / nobody knows
q: what's the best disc you've found? > the cheerful one / the creepy one / the slow one / i only have one
d: one of them sounds like a happy day | some of them sound sad and broken
d: there's one that sounds like someone walking somewhere | a disc in a dungeon is like a note left for us
r: a note left for us, yeah | sad and broken, some of them are
r: someone walking, i hear it too | a happy day, yeah
o: maybe someone made them so we wouldn't feel alone | music outlasts whoever made it
o: i'd like to make a song one day | the discs are other people's feelings we get to keep
p: not alone, yeah | it does | you should | keeping feelings, yeah
c: music is the most important thing someone can make | songs matter more than builds
y: true | yeah | agreed
n: builds last longer | nah, houses | both matter
""")

angle(MUSIC, "music_silence", "silence", """
m: i like it when everything's quiet | silence can be the best sound
m: in the snowy places it's so quiet you can hear your heart | some silence is peaceful, some is scary
q: do you like the quiet? > love it / it scares me / sometimes / only with someone
q: what's the quietest place you've been? > the snow / deep in a cave / my house at night / the ocean floor
d: quiet in a meadow is peaceful | quiet in a cave means something's coming
d: i hear my own thoughts more in the quiet | quiet with a friend is the best quiet
r: meadow quiet and cave quiet, so different | quiet with a friend, yes
r: own thoughts, loud, yeah | something's coming, creepy
o: maybe silence is just the world listening | sitting quiet with someone means you trust them
o: i'm trying to like the quiet more | the world's never really silent, there's always wind
p: the world listening, nice | trust, yeah | good | always wind, true
c: quiet is better than noise | silence beats any music
y: true | yeah | agreed
n: music is better | nah, quiet is lonely | noise is life
""")

angle(MUSIC, "music_note_blocks", "making music", """
m: i made a song with note blocks and it was terrible | making music is harder than mining
m: i hit a note block a hundred times to get one tune | i want to make a song for my town
q: have you ever made music? > yeah, badly / no / a little / i hum, does that count
q: what would your song sound like? > happy / slow and sad / like rain / like the night
d: every block changes the sound. wood, stone, glass | it took me all day for ten seconds of song
d: when it finally worked i played it ten times | the villagers didn't even look up
r: all day for ten seconds, worth it | ten times, fair
r: villagers have no taste lol | every block different, cool
o: maybe making something nobody needs is the point | music is just feelings you can hear
o: i'd play it at a festival one day | even a bad song is something new in the world
p: nobody needs it, everyone wants it | feelings you can hear, yes | do it | something new, yeah
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
           "newbie": "wait, mobs have feelings?? | i'm scared of all of them",
           "pvp_sweat": "mobs are practice. that's it | they're just xp to me. but ok",
           "helper": "even mobs deserve a bit of thought",
           "miner": "i've met them all down there. they're just doing their thing"},
    ponder="huh | maybe | weird | could be",
    nudge="go on | you're feeling sorry for mobs, huh | keep going | what else",
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
m: why do the mobs always come for us | they don't even know us and they hate us
m: a zombie walked a whole field just to get me | the mobs only care about us, not the animals
q: why do you think mobs hate us? > they're made that way / they're jealous / they're scared of us / nobody knows
q: do you think the mobs know what they're doing? > no / some do / the endermen do / i hope not
d: they ignore the cows and come straight for me | it's not personal, but it feels personal
d: they're only like that at night, mostly | the spiders change their mind in the day
r: not personal, but feels it, yeah | ignoring the cows, rude
r: day and night different, yeah | the spiders are moody
o: maybe they want something we have, like light | maybe they're just lonely in the dark
o: maybe they don't hate us, they just don't know any other way | i'd like to know what they want
p: light, maybe | lonely, huh | no other way, sad | me too
c: mobs are just doing what they're made for | it's not the mobs' fault
y: true | yeah | agreed
n: they're evil | nah, they're mean | they choose it
""")

angle(MOBS, "mobs_creepers", "the creepers", """
m: creepers don't fight, they just walk up and explode | a creeper blows itself up just to hurt you
m: creepers are so quiet until the hiss | i feel kind of bad for creepers
q: why do you think creepers explode? > they're angry / they don't know how else to touch us / they're made that way / they want a hug, i swear
q: what would you say to a creeper if it could listen? > please go away / why / leave my house alone / i'm sorry
d: it gives everything just to hurt you once | it's the only mob that ends itself
d: they're green and sad looking | they never say anything, just that hiss
r: gives everything, yeah | the only one that ends itself, heavy
r: sad looking, true | the hiss is all they say
o: maybe creepers are the loneliest mob | a hug that ends everything. that's sad
o: cats scare them. that's the strangest thing | i still run from them. sad or not
p: loneliest, maybe | sad, yeah | cats, of all things | run, yes
c: creepers are the worst mob | nothing's worse than a creeper
y: true | yeah | easily
n: phantoms are worse | nah, the warden | endermen
""")

angle(MOBS, "mobs_skeletons", "who skeletons were", """
m: skeletons are just bones with bows | i wonder who the skeletons were before
m: skeletons burn in the sun like zombies | a skeleton's just bones that won't stop
q: do you think skeletons were someone once? > maybe / no, they're just mobs / i don't want to think about it / probably
q: what do you think skeletons want? > to shoot us / to rest / nothing / to be left alone
d: they never stop shooting, even when it's hopeless | they rattle when they walk
d: when they die they drop bones, like that's all they were | they hide from the sun under trees
r: they don't stop, yeah | rattling, creepy
r: just bones, sad | hiding from the sun, smart
o: maybe they're just tired and nobody let them rest | if they were someone, i hope they're at peace now
o: i'd like to think they're not angry, just stuck | their bones help the plants grow
p: tired, yeah | at peace, i hope | stuck, maybe | they help the farm, nice
c: skeletons are the hardest mob to fight | skeletons are worse than zombies
y: true | yeah | agreed
n: zombies swarm | creepers | nah, skeletons are easy with a shield
""")

angle(MOBS, "mobs_sun_burn", "mobs burning in the sun", """
m: why do zombies burn in the sun | the sun is like their enemy
m: the mobs burn at sunrise and i always feel safe | why does the sun hurt them and not us
q: why do you think the sun hurts the mobs? > they belong to the night / the sun is good / they're not meant to be here / nobody knows
q: do you feel bad when they burn? > a little / never / sometimes / only the zombie villagers
d: they run to the shade like they're scared | the sun is the one thing on our side
d: the drowned don't burn, they just stay underwater | the ones with helmets don't burn
r: running to the shade, sad | the sun's on our side, yeah
r: drowned hiding underwater, smart | helmets, clever
o: maybe the night is their home and the day is ours | maybe the sun is protecting us
o: we share the world, in shifts | i'd hate to be scared of the sun
p: in shifts, yeah | protecting us, yes | me too | their home, ours
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
           "newbie": "bored? there's so much to do here! | i'm never bored, everything's new",
           "speedrunner": "bored means you're slow. pick a goal",
           "helper": "let's find something to do together"},
    ponder="huh | maybe | true | could be",
    nudge="go on | you're bored, huh | keep going | what else",
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
d: on my first day i'd have killed to be bored | boredom means the hard part is done
d: but too much boredom and i get sad | boredom is the world saying, rest
r: killed to be bored, yeah | the hard part done, nice
r: too much is sad, true | rest, yeah
o: maybe boredom is when new ideas start | i had my best build idea while bored
o: being bored with a friend is better than being busy alone | maybe bored is just calm that's not used to itself
p: new ideas, yes | bored builds are the best | with a friend, yes | calm not used to itself, nice
c: boredom is a good thing | being bored means life is good
y: true | yeah | agreed
n: boredom is the worst | nah, it's awful | bored is bad
""")

angle(BORED, "bored_routine", "the same days", """
m: every day is the same now: mine, smelt, sleep | i like my routine, but it's getting old
m: i do the same things in the same order every day | the routine keeps me safe but it's dull
q: do you like having a routine? > love it / it's fine / hate it / it keeps me sane
q: what would you change about your days? > more exploring / more friends / more sleep / nothing
d: i know exactly where every chest is and what's in it | the routine is like a path worn into the grass
d: the days blur, but in a comfortable way | sometimes i change one thing just to feel new
r: a worn path, yeah | comfortable blur, nice
r: one thing new, smart | knowing every chest, very organised
o: maybe a routine is a home for your days | i think i need an adventure soon
o: the routine is safe, the change is alive | i'll keep the routine and add one new thing a week
p: a home for days, nice | let's go on one | both, yeah | good plan
c: routine is better than chaos | a steady routine beats surprises
y: true | yeah | agreed
n: chaos is fun | nah, surprises | routine is boring
""")

angle(BORED, "bored_nothing_to_do", "nothing to do", """
m: i finished everything and now there's nothing to do | i've done all my jobs and now i'm just standing here
m: when there's nothing to do i don't know who i am | having nothing to do is weirdly hard
q: what do you do when there's nothing to do? > find something / nothing, i rest / go exploring / bother a friend
q: do you feel bad when you're not doing anything? > yeah / no / a bit / only when others are busy
d: i always feel like i should be working | sitting still feels like wasting time
d: but the sunset doesn't need me to be busy | doing nothing is a skill i don't have
r: you don't always have to work | the sunset agrees
r: a skill, yeah, a hard one | wasting time isn't always waste
o: maybe we're more than what we do | rest is part of the work
o: i'm going to try doing nothing on purpose today | the world won't fall apart if i sit
p: more than what we do, yes | rest is work, yeah | try it | it won't
c: you should always be doing something useful | doing nothing is a waste
y: true | yeah | agreed
n: rest is useful | nah, chill sometimes | doing nothing is fine
""")

angle(BORED, "bored_seeing_new", "the newbies never bored", """
m: newbies are never bored. everything's new | i miss when every day was a surprise
m: i watched a newbie get excited about a sheep. i was jealous | when did everything stop being new
q: do you miss being new? > yes / no, i like knowing stuff / sometimes / a little
q: how do you make things feel new again? > go somewhere new / try something new / talk to newbies / i don't know
d: the first time i saw a village i stood there for ages | now i walk past villages like they're nothing
d: newbies notice stuff i stopped seeing | maybe being bored means i stopped looking
r: stood there for ages, yeah | stopped looking, oof
r: they notice stuff, true | walking past villages, sad
o: maybe the trick is to see things like a newbie again | i'm going to try noticing one new thing a day
o: the world didn't change. i did | teaching a newbie made me see things fresh
p: newbie eyes, yeah | one new thing, good | you did, yeah | teaching helps
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
    ponder="huh | true | yeah | good point",
    nudge="go on | you've learned something, huh | keep going | what else",
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
d: i thought i knew caves until i saw how deep they go | the newbies teach me stuff too, honestly
d: every biome has its own rules | the more i know, the more i know i don't know
r: newbies teaching you, nice | the more you know, yeah
r: every biome's different, true | the deep caves humble everyone
o: maybe learning is what keeps the world fresh | i never want to know everything
o: i like being surprised. it means there's more | knowing everything would be boring
p: fresh, yeah | me neither | more, yes | boring, true
c: you can never know everything | there's always something more to learn
y: true | yeah | agreed
n: you can know most of it | nah, eventually you know it all | i know enough
""")

angle(LEARN, "learn_teaching", "teaching a newbie", """
m: i've been teaching a newbie and it's teaching me too | explaining stuff makes me understand it better
m: i showed a newbie how to make a bed and felt proud | teaching someone is like seeing the world fresh
q: what's the first thing you'd teach a newbie? > make a bed / never dig straight down / torches / punch a tree
q: who taught you the most? > nobody, i learned alone / a friend / the creepers lol / a stranger
d: they asked why we sleep and i didn't have an answer | their face when the first iron came out of the furnace
d: they do everything wrong and it's kind of great | they ask questions i stopped asking
r: no answer, fair | the first iron face, the best
r: wrong and great, yeah | questions you stopped asking, yes
o: maybe teaching is how we pass on everything we learned | i was a newbie not long ago, honestly
o: the best teachers are the ones who remember being new | i hope they teach someone else one day
p: passing it on, yes | not long ago, yeah | remember being new, yes | they will
c: everyone should teach a newbie | teaching is the best way to learn
y: true | yeah | agreed
n: let them learn alone | nah, it's slow | they learn better alone
""")

angle(LEARN, "learn_hard_way", "learning the hard way", """
m: everything i know, i learned the hard way | nobody told me lava was that bad
m: i learned about creepers from a creeper | some lessons you only learn once
q: what's the hardest lesson you've learned? > lava / trust / patience / never go out at night unprepared
q: would you rather be told or learn it yourself? > be told / learn it myself / both / depends how much it hurts
d: i still flinch near lava | the hard lessons stick the best
d: one death and i never made that mistake again | someone told me once and i didn't listen. then i learned
r: flinching near lava, same | the hard ones stick, yeah
r: never again, good | didn't listen, classic
o: maybe some things you can't be told, only shown | i try to tell newbies, but they have to find out too
o: the hard way is slow but it's yours | i'd rather learn hard than not at all
p: shown, yeah | they do | yours, yes | not at all is worse
c: the hard way is the only way to really learn | you only learn by doing it wrong
y: true | yeah | agreed
n: being told works | nah, listen to people | you can learn easy too
""")

angle(LEARN, "learn_knowing_doing", "knowing versus doing", """
m: knowing how to do something isn't the same as doing it | i know how to fight, but my hands shake
m: i know every recipe, but i still mess up | knowing and doing are different worlds
q: is it harder to know something or to do it? > to do it / to know it / both / doing, always
q: what do you know how to do but still can't? > fight well / build pretty / stay calm / sleep when scared
d: i know to sneak at an edge, but i still forget | i know creepers hiss, but i still freeze
d: practice is the bridge between them | doing it once makes it real
r: still freezing, everyone does | the bridge, nice
r: forgetting at edges, scary | once makes it real, yeah
o: maybe the doing is where you actually learn | knowing gives you courage to start
o: i'm going to practice instead of just knowing | being brave is doing what you know
p: doing is learning, yes | courage to start, yeah | practice, good | brave is doing, yes
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


# ---- Pairing a's statements with b's replies: each reply goes with the statement it echoes (the words they share), so
# whatever line a picks, b answers that line ("then"); a reply that echoes none of them fits any of them.
STOP = set("""a an the and or but so i i'm im you you're your we we're our us it it's its is are was were be been being to
of in on at for with from by up down out over just like this that these those there then than too very really what when
where who why how if my me mine they them their he she his her do does did done not no yes yeah all any some more most
much many one ever never can could would should will won't don't didn't isn't wasn't can't have has had get got go going
gone make made kind kinda still even also maybe about into after before again only own same other such thing things stuff
lot lol ok oh huh ah hm mm sure yep nope well thats there's that's what's who's let's i'd i've i'll you'd you'll""".split())


def words(line):
    out = set()
    for w in re.findall(r"[a-z']+", line.lower()):
        w = w.strip("'")
        if w.endswith("'s"):
            w = w[:-2]
        if w in STOP or len(w) < 3:
            continue
        for suffix in ("ing", "ed", "es", "s", "y"):
            if w.endswith(suffix) and len(w) - len(suffix) >= 3:
                w = w[:-len(suffix)]
                break
        out.add(w)
    return out


PAIRINGS = []


def paired(said, replies, fallback, where):
    """said's lines, each with the replies that echo it (or, failing that, every reply that echoes none)."""
    said_words = [words(s) for s in said]
    mine = {i: [] for i in range(len(said))}
    generic = []
    for r in replies:
        scores = [len(words(r) & w) for w in said_words]
        best = max(scores)
        if best == 0:
            generic.append(r)
        else:
            for i, s in enumerate(scores):
                if s == best:
                    mine[i].append(r)
    out = []
    for i, s in enumerate(said):
        answers = mine[i] + generic or list(fallback)
        out.append({"line": s, "then": answers})
        PAIRINGS.append((where, s, answers))
    return out


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
    has3 = bool(a.get("l") and a.get("k"))
    # a's statements, each with the replies that fit it.
    fallback = REPLY_ANY
    d_said = paired(a["d"], a["r"], fallback, an["key"] + " d")
    o_said = paired(a["o"], a["p"], fallback, an["key"] + " o")
    l_said = paired(a["l"], a["k"], fallback, an["key"] + " l") if has3 else None

    # 1. Musing: a thinks out loud, b reacts in its voice, a goes deeper, b answers.
    topic(key, 0.7 * w, base, tags, leads, about, [
        turn("a", a["m"]),
        turn("b", th["react"]),
        turn("a", d_said),
        turn("b", PLACEHOLDER),
        turn("a", th["close"], chance=0.7),
        turn("b", SHORT, chance=0.4),
    ])

    # 2. A talk in parts: a asks, b answers; part 2 goes deeper; part 3 (some) how it feels.
    topic(key + "_talk", 0.6 * w, base, tags, leads, about, [
        turn("a", a["q"]),
        turn("b", PLACEHOLDER),
        turn("a", th["ponder"]),
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
            turn("b", SHORT, chance=0.4),
        ], follow_up=True, effects=["trust"] if th["trust"] else None)

    # 3. Situational: the musing with a lead-in that fits the moment.
    topic(key + "_at_" + sit_a, 1.4 * w, base + SIT[sit_a], tags, leads, about, [
        turn("a", th["sits"][sit_a]),
        turn("a", a["m"]),
        turn("b", th["react"]),
        turn("a", o_said),
        turn("b", PLACEHOLDER),
        turn("a", th["close"], chance=0.6),
        turn("b", SHORT, chance=0.4),
    ])
    # 4. Situational: the question with a lead-in.
    topic(key + "_at_" + sit_b + "_q", 1.3 * w, base + SIT[sit_b], tags, leads, about, [
        turn("a", th["sits"][sit_b]),
        turn("a", a["q"]),
        turn("b", PLACEHOLDER),
        turn("a", th["ponder"], chance=0.6),
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
            turn("a", th["yes"], chance=0.7, cond=["t:agree"]),
            turn("a", th["no"], cond=["t:disagree"]),
            turn("b", DEBATE_END, chance=0.35),
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
