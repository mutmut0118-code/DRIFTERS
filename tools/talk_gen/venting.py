"""Writes drifter_talk/topics_gen_venting.json: one Drifter unloads (a), the other listens (b) its own way.

The user: "venting ... as detailed and in depth as possible". Each grievance below (what went wrong, with its own
hand-written lines: how a says it, b's questions and a's answers, more detail, b's own bad story, advice, a joke, an
offer of help, a pvp sweat's "skill issue", what makes it worse) becomes a set of topics, one per way of listening:

- comfort: b comforts (helper and homebody in their own voices), a feels better (agree) or is still mad
- relate: b tells its own bad story of the same kind, a feels less alone
- advice: b (not a newbie) says what it'd do; a takes it (agree) or "easy for you to say"
- joke: b cracks a joke to cheer it up; it lands (agree) or "not funny", "ok sorry"
- skill: b is a pvp sweat: "skill issue"; a owns it (agree) or it turns into an argument (disagree)
  (for grief, loneliness and the like: an awkward pvp sweat trying to comfort instead)
- help: b offers to help ("i'll help you rebuild"); a takes it (agree) or would rather do it alone
- hermit: b is a hermit: "that sucks." and changes the subject
- friend: friends only, "can i vent for a sec?"
- sit_<need>: a's state or the world fits (a:died_lately, a:mood_angry, rain...): weight 1.2-2.0
- rant, rant_2: the big ones, a long rant in two parts (next / follow_up)

Usage: python tools/talk_gen/venting.py [out.json]
Check: python tools/check_talk_web.py . --quiet
"""
import json
import random
import re
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/data/drifter/drifter_talk/topics_gen_venting.json"
P = "g_vent_"


def some(pool, k, key):
    """k lines from a pool, the same ones every run for this key (so topics differ from each other, and the file is stable)."""
    pool = list(pool)
    return random.Random(key).sample(pool, min(k, len(pool)))


def uniq(xs):
    out = []
    for x in xs:
        if x not in out:
            out.append(x)
    return out


# =====================================================================================================================
# Shared reactions (short ones may repeat across topics; the substance is in the grievances)
# =====================================================================================================================

# b, situational: what's wrong? (a's answer is the grievance itself)
WHATS_WRONG = ["what happened?", "what's wrong?", "uh oh. what now?", "talk to me, what's up?", "oh no, what is it?",
               "what's going on?", "you ok? what happened?", "rough day? what is it?"]
WHATS_WRONG_SOFT = ["i'm here. what is it?", "what's on your mind?", "take your time. what happened?", "talk to me"]

# a, how it feels right now, before the grievance (the situational topics)
NEED_PREFACE = {
    "a:mood_frustrated": ["ugh. today.", "i'm so done with today", "nothing is going right", "i'm about to lose it",
                          "today has been one thing after another"],
    "a:mood_angry": ["i'm so mad right now", "i'm actually fuming", "don't even get me started", "i could punch a creeper",
                     "i am so angry right now"],
    "a:mood_grieving": ["i'm not doing great", "can we just talk for a bit", "it's been a rough few days",
                        "i keep thinking about it"],
    "a:died_lately": ["so i {a.death}", "guess who {a.death} today", "don't laugh, but i {a.death}", "i died earlier. again.",
                      "just respawned. it's been a day."],
    "a:close_call": ["{a.close_call} nearly killed me earlier", "i almost died to {a.close_call}", "i nearly died earlier",
                     "that was way too close earlier"],
    "a:hurt": ["i'm on like two hearts", "ow. everything hurts", "i got beat up out there", "give me a sec, i'm barely alive"],
    "a:hungry": ["i'm starving, and that's not even the problem", "my stomach's empty and so are my chests",
                 "so hungry. and it gets worse."],
    "a:no_home": ["i don't even have a home right now", "sleeping in a hole again tonight", "no home, no bed, nothing"],
    "rain": ["this rain, man", "it won't stop raining", "rain again. of course.", "look at this weather"],
    "thunder": ["this storm is the worst", "lightning everywhere out here", "that thunder made me jump"],
    "time_night": ["i hate nights", "it's dark again", "every night it's the same thing", "here comes the night again"],
    "time_evening": ["it's getting dark already", "sun's going down, here we go", "night's coming, ugh"],
    "time_morning": ["morning. if you can call it that.", "what a way to start the day", "worst morning ever"],
    "a:mood_anxious": ["i can't relax", "i've been on edge all day", "i keep jumping at every noise", "i've got a bad feeling"],
    "a:mood_bored": ["i'm so bored of everything", "same thing every day", "i need something new to do",
                     "everything's so samey lately"],
    "a:taxed": ["tax day again", "the town wants its cut again", "can we talk about the taxes"],
    "a:tax_high": ["have you seen what the taxes are now", "the taxes are robbery now", "i can't afford these taxes"],
    "a:dislikes_leader": ["can i say something about the leader", "i'm not happy with how things are run",
                          "don't tell the leader i said this"],
    "a:farms_closed": ["the farms are closed again", "can't even farm now", "who closed the farms, seriously"],
}
SIT_WEIGHT = {"a:mood_frustrated": 1.6, "a:mood_angry": 1.6, "a:mood_grieving": 2.0, "a:died_lately": 1.8,
              "a:close_call": 1.5, "a:hurt": 1.4, "a:hungry": 1.4, "a:no_home": 1.5, "rain": 1.3, "thunder": 1.6,
              "time_night": 1.2, "time_evening": 1.2, "time_morning": 1.2, "a:mood_anxious": 1.5, "a:mood_bored": 1.3,
              "a:taxed": 1.4, "a:tax_high": 1.8, "a:dislikes_leader": 1.6, "a:farms_closed": 1.5}
SIT_EXTRA = {"rain": ["outside"], "thunder": ["outside"]}

# a, after b's own story
RELATE_REACT = ["no way", "ok yours is worse", "lol at least it's not just me", "oof, ok you win",
                "that's actually worse than mine", "wait, seriously", "ok i feel slightly less dumb", "lmao no",
                "ok that's rough too", "you never told me that"]
RELATE_RELIEF = ["ok i feel a bit better now", "misery loves company lol", "thanks, honestly", "we should start a club",
                 "glad it's not just me", "ok that helped, weirdly", "we're both cursed then", "good to know i'm not alone"]

# a, after comfort
RESOLVE_OK = ["thanks for listening", "ok, i feel a bit better", "needed to get that out", "yeah. thanks",
              "you're right, i'll be ok", "appreciate it, really", "ok. i'll live", "talking helped, thanks"]
RESOLVE_OK_VOICES = {
    "hoarder": ["at least my main stash is safe", "ok. back to collecting, i guess"],
    "speedrunner": ["whatever. back to it.", "ok, wasted enough time on this"],
    "newbie": ["thanks, i'll get better i hope", "ok, i'll try again"],
    "casual": ["lol ok, it's fine, whatever", "eh, it'll be fine"],
    "hermit": ["enough talking. thanks.", "ok. done now."],
    "nomad": ["ok. time to move on anyway", "maybe a fresh start somewhere new"],
}
RESOLVE_BAD = ["still mad though", "i'm still annoyed", "doesn't fix it but ok", "i'll be grumpy a while longer",
               "ugh, i know. still.", "i'm not over it", "nope, still salty"]
CLOSE_OK = ["anytime", "that's what i'm here for", "you'd do the same for me", "any time, really", "go easy on yourself",
            "i've got you"]
CLOSE_BAD = ["give it a day", "fair, be mad", "you're allowed to be mad", "it'll pass", "ok, grump away", "that's fair too"]

# advice
ADVICE_TAKE = ["huh, good idea", "ok that's actually smart", "noted", "i'll try that next time", "why didn't i think of that",
               "yeah, you're right", "ok, writing that down"]
ADVICE_BRUSH = ["easy for you to say", "yeah yeah i know", "i know, i just didn't", "thanks, mom", "i wasn't asking for tips",
                "i KNOW. i just wanted to complain", "ok mr expert"]
ADVICE_OK = ["you'll be fine", "trust me on that one", "it works, i swear", "you got this", "next time will go better"]
ADVICE_BACK = ["just saying", "suit yourself", "ok ok, i'll stop", "fine, complain away", "noted, no tips", "lol sorry"]

# jokes
JOKE_LAUGH = ["lol ok that's fair", "stop lmao", "ok that got me", "lol shut up", "haha ok, i needed that",
              "i hate that that's funny", "lmao ok ok"]
JOKE_FLAT = ["not funny", "i'm being serious", "too soon", "wow ok", "really", "i'm hurting here"]
JOKE_SMILE = ["there's the smile", "see, not so bad", "you'll laugh about it more later", "got you", "that's the spirit"]
JOKE_SORRY = ["ok ok sorry, that really does suck", "my bad, that's rough", "sorry. for real, that sucks", "too soon, got it",
              "sorry, i joke when it's bad"]

# skill issue (b is a pvp sweat)
SKILL_GENERIC = ["skill issue", "sounds like a skill issue tbh", "skill issue, ngl", "that's a you problem",
                 "L + skill issue", "hate to say it but skill issue"]
SKILL_CONCEDE = ["ok yeah, that one's on me", "...fine, maybe a little", "ugh. yeah. skill issue", "i hate that you're right",
                 "ok fair, i messed up"]
SKILL_ARGUE = ["it was NOT a skill issue", "you weren't even there", "ok mr perfect", "like you've never messed up",
               "wow. thanks."]
SKILL_RESPECT = ["respect for admitting it", "first step is admitting it", "see, growth", "we'll make a sweat out of you yet",
                 "good. now go again"]
SKILL_COPE = ["cope", "whatever helps you sleep", "i'd have done better, just saying", "keep telling yourself that", "seethe lol",
              "ok, not your fault, sure"]
SKILL_LAST = ["you're unbearable", "see if i tell you anything again", "i'll remember this", "never venting to you again",
              "ugh, why do i talk to you"]

# an awkward pvp sweat trying to comfort
AWK_REACT = ["...thanks i guess", "you're bad at this lol", "that's your idea of comfort", "lol ok, maybe", "weirdly that helps",
             "i'll take it"]
AWK_B2 = ["i tried", "i'm better with a sword", "that's all i got", "offer stands", "yeah. sorry. i'm not good at this"]

# help
HELP_ACCEPT = ["really? thanks", "you'd do that? thanks", "that'd be amazing", "ok yes please", "you're the best",
               "i won't say no to that"]
HELP_DECLINE = ["nah, i got it, thanks though", "i'd rather do it myself", "i'm good, really", "thanks, but this one's on me",
                "i need to do it myself, you know"]
HELP_SURE = ["course", "that's what friends are for", "say when", "no problem at all", "we'll get it done", "easy"]
HELP_STANDS = ["offer stands", "ok, holler if you change your mind", "fair enough", "door's open if you need it",
               "ok, but i'm around"]

# hermit
HERMIT_CURT = ["that sucks.", "rough.", "hm. unlucky.", "happens.", "bad luck.", "mm.", "oh.", "huh. sucks."]
HERMIT_CHANGE = ["anyway. seen any iron around?", "anyway.", "so. nice weather.", "hm. got any coal?",
                 "anyway, i'm heading off soon", "you know where the sheep went?", "...anyway. how's your farm?",
                 "ok. i'm going mining.", "right. well.", "anyway, the river's high today",
                 "that tree over there looks good for logs", "hm. gonna rain later, i think", "anyway. need torches?",
                 "so. the stone's good around here."]
HERMIT_REACT = ["...ok then", "wow, thanks for the support", "lol you're the worst", "classic you", "great talk",
                "i don't know why i tell you things", "ok, glad we had this chat", "never change lol"]

# a friend's "can i vent?"
VENT_ASK = [("can i vent for a sec?", ["always", "go for it", "sure, what's up?"]),
            ("got a minute? i need to complain", ["for you, always", "sure, spill", "uh oh, go on"]),
            ("ok i need to rant, you got time?", ["i've got time", "rant away", "go on then"]),
            ("can i whine at you for a bit?", ["lol go ahead", "whine away", "sure thing"]),
            ("you mind if i complain a little?", ["not at all", "go ahead", "complain away"]),
            ("i need to get something off my chest", ["i'm listening", "go on", "what's up?"])]
FRIEND_OVER = ["ok, rant over", "thanks, i needed to get that out", "ok i feel better already", "sorry for dumping that on you",
               "glad i have you to tell", "ok. rant complete."]
FRIEND_STILL = ["still mad though", "ok but i'm still annoyed", "i'll get over it. eventually.", "doesn't fix it, but thanks"]
FRIEND_CLOSE = ["anytime", "my turn next time lol", "always here", "dump away, anytime", "that's what friends do",
                "you know where i am"]


# Softer reactions for heavy grievances (a friend gone, loneliness): no "lol", no "thanks, mom".
SOFT = {
    "RELATE_REACT": ["i didn't know that", "i'm sorry, that's awful too", "you never said", "so you get it",
                     "that sounds hard", "oh. i'm sorry."],
    "RELATE_RELIEF": ["it helps knowing you get it", "thanks for telling me that", "ok. that helps a little",
                      "glad i'm not the only one"],
    "RESOLVE_OK": ["thanks for listening", "it helps to say it", "thanks. really.", "i feel a little lighter",
                   "i needed that"],
    "RESOLVE_BAD": ["it still hurts though", "it's not gone, but ok", "i'm not ok yet", "one day at a time, i guess"],
    "CLOSE_BAD": ["that's ok", "take all the time you need", "it's ok to not be ok", "i'm not going anywhere"],
    "ADVICE_TAKE": ["yeah. i'll try", "maybe you're right", "thanks, i'll try that", "that helps a little"],
    "ADVICE_BRUSH": ["i know. it's just hard", "easier said than done", "maybe. not yet though", "i can't right now"],
    "ADVICE_OK": ["take your time", "no rush", "whenever you're ready"],
    "ADVICE_BACK": ["that's ok", "fair, sorry", "no pressure", "i get it"],
    "AWK_REACT": ["...thanks", "it's ok. thanks for trying", "you're sweet, in your way", "that's kind of nice actually"],
    "AWK_B2": ["i mean it though", "i'm here, is what i'm saying", "yeah. sorry."],
    "HERMIT_CURT": ["sorry.", "mm. that's hard.", "...i'm sorry.", "hm. sorry to hear."],
    "HERMIT_CHANGE": ["...want to walk a bit", "i'll sit here a while, if you want", "hm. i'm around.", "anyway. i'm here.",
                      "...the river's nice this time of day"],
    "HERMIT_REACT": ["...ok", "right. thanks.", "i guess that's something", "ok then", "that's more than most say"],
    "FRIEND_OVER": ["thanks for listening", "sorry, that was heavy", "i needed to say it", "thanks for being here"],
    "FRIEND_STILL": ["it still hurts though", "it doesn't feel real yet"],
    "FRIEND_CLOSE": ["always", "i'm here, any time", "you can always talk to me", "i mean it"],
    "VENT_ASK": [("can we talk for a minute?", ["of course", "always", "yeah. what is it?"]),
                 ("i need to tell someone something", ["i'm listening", "go on", "what is it?"]),
                 ("you got a minute? it's kind of heavy", ["for you, yes", "take your time", "i'm here"])],
}

# the long rant
RANT_PROMPT = ["oh no", "oof", "yikes", "brutal", "rip", "nooo", "that's awful", "ouch"]
RANT_TEASE = ["and that's not even the worst part", "oh, it gets worse", "wait, there's more", "and then. AND THEN.",
              "you haven't heard the worst bit", "hold on, it gets better. i mean worse."]
RANT_REACT = ["no way", "oh no", "you're kidding", "nooo", "stop", "that's criminal", "that's cruel", "ugh, no"]

# =====================================================================================================================
# Kinds of grievance: their tags and leads, and what's said about any grievance of the kind
# =====================================================================================================================

CATS = {}


def CAT(name, **kw):
    CATS[name] = kw


CAT("death", tags=["death", "complaints"], leads=["death", "gear", "danger"],
    comfort={"any": ["that's the worst feeling", "ugh, losing your stuff hurts", "you'll get it all back, promise",
                     "at least you respawned", "i hate that for you"],
             "helper": ["aw no, i'm so sorry", "come by later, i've got spare tools for you",
                        "i can lend you a pick and some food", "don't beat yourself up, it happens to everyone"],
             "homebody": ["come home and rest a bit", "i'll cook you something, come over", "stuff can be replaced, you can't"],
             "pvp_sweat": ["rough. gear comes back though", "shake it off, regear, go again", "deaths happen. get back out there"]},
    relate_tail=["never went near that spot again", "we've all been there", "lesson learned the hard way",
                 "took me days to get back on my feet lol"],
    resolve_ok=["time to go get new stuff i guess", "ok. back to square one."],
    resolve_bad=["i was so close to a full kit too", "i'm still thinking about that pickaxe"],
    argue=["you can't dodge what you can't see", "you'd have died too", "it came out of nowhere, ok"],
    curt=["dying sucks.", "lost it all? rough."],
    vent_ask=[("can i complain about dying for a sec?", ["go ahead", "lol sure", "how'd you die this time?"])])

CAT("close", tags=["danger", "health"], leads=["danger", "health", "mobs"],
    comfort={"any": ["glad you're ok", "that sounds terrifying", "you made it, that's what counts", "breathe, you're safe now"],
             "helper": ["sit down, eat something, i'll keep watch", "here, let me look at you. you're ok.",
                        "you're safe now, i've got you"],
             "homebody": ["come rest at mine, you need it", "stay in today, seriously"],
             "pvp_sweat": ["close fights are the best ones", "half a heart and still standing, respect"]},
    relate_tail=["never went back there", "my hands shook for an hour after", "it stays with you a bit"],
    resolve_ok=["ok. i'm ok. i think.", "yeah. i'll take it easy today"],
    resolve_bad=["i'm still shaking a bit", "i keep seeing it when i close my eyes"],
    argue=["it was a close one, not a bad one", "i lived, didn't i", "you'd have been dead"],
    curt=["you lived.", "close one."],
    vent_ask=[("can i tell you about the scariest thing today?", ["sure", "uh oh", "what happened?"])])

CAT("build", tags=["building", "complaints"], leads=["building", "decor", "home"],
    comfort={"any": ["builds never come out right the first time", "it's not as bad as you think",
                     "every good build started as an ugly one", "you'll fix it, you always do"],
             "helper": ["aw, show me, i bet we can save it", "don't give up on it, i'll help",
                        "i bet it looks better than you think"],
             "homebody": ["it's still your place, that's what counts", "a home doesn't have to be perfect",
                          "it'll feel right once you live in it a bit"],
             "pvp_sweat": ["walls are walls. does it keep mobs out", "who cares how it looks, does it work",
                           "just build a box, boxes are fine"],
             "builder": ["oof, i feel that in my soul", "every builder's nightmare", "breathe. we can fix this."]},
    relate_tail=["tore mine down twice before it looked right", "it happens to every builder", "my first three houses were awful"],
    resolve_ok=["maybe i'll fix it tomorrow", "ok, round two tomorrow"],
    resolve_bad=["i still can't look at it", "every time i walk past it i'm mad"],
    argue=["you can't skill issue a build", "your house is a dirt box, you can't talk", "like you've never had a bad build day"],
    curt=["builds are hard.", "tear it down then."],
    vent_ask=[("can i rant about my build?", ["sure", "uh oh, go on", "what did it do?"])])

CAT("grief", tags=["complaints", "home"], leads=["home", "gossip", "storage"], sit=["a:mood_angry"],
    comfort={"any": ["that's so not ok", "who even does that", "you didn't deserve that", "that's low, really low"],
             "helper": ["i'm so sorry, that's awful", "i'll help you fix it up if you want",
                        "we'll keep an eye out together"],
             "homebody": ["your home should feel safe, i hate this", "come stay at mine till it's sorted"],
             "pvp_sweat": ["find who did it. then we talk", "give me a name and i'll handle it", "that's war, honestly"]},
    relate_tail=["never found out who did it", "i still check my stuff twice", "i put up a sign after that, didn't help lol"],
    resolve_ok=["ok. i'll move my stuff somewhere safer", "you're right, it's just stuff"],
    resolve_bad=["i'm going to find out who did it", "if i find them, i swear"],
    argue=["how is someone else's mess a skill issue", "oh so it's my fault now", "you sound like the one who did it"],
    awkward=["that's messed up. want me to find whoever did it", "uh. sorry. revenge is an option"],
    curt=["people are the worst.", "lock it up next time."],
    vent_ask=[("can i be mad about something for a sec?", ["sure", "uh oh", "who made you mad?"])])

CAT("mobs", tags=["mobs", "complaints"], leads=["mobs", "fighting", "danger"],
    comfort={"any": ["mobs are the worst", "they really have it out for you", "honestly the mobs have been bad lately",
                     "ugh, i'm sorry, that sounds exhausting"],
             "helper": ["you should've called me, i'd have come", "next time shout and i'll come running",
                        "let's light up your place together"],
             "homebody": ["that's why i never leave my house lol", "stay in tonight, it's not worth it"],
             "pvp_sweat": ["just kill them lol", "mobs are free xp, honestly", "give me a sword and an hour"]},
    relate_tail=["now i light everything twice", "i swear they wait for me", "mobs hate us both apparently"],
    resolve_ok=["ok. more torches it is", "fine, i'll go hunt them down"],
    resolve_bad=["i'll be up all night hearing them", "they'll be back tonight, watch"],
    argue=["there were like ten of them", "you try fighting that in the dark", "you'd have run too"],
    curt=["mobs.", "kill them then."],
    vent_ask=[("can i complain about mobs?", ["always", "which ones now?", "go on"])])

CAT("luck", tags=["complaints"], leads=["jokes", "plans"], sit=["a:mood_frustrated"],
    comfort={"any": ["rng hates you today", "that's just bad luck, not you", "your luck has to turn soon",
                     "ugh, the game is mocking you"],
             "helper": ["i'm sorry, that's so frustrating", "i can spare some of mine if you need",
                        "next one will go better, i'm sure"],
             "homebody": ["take a break, have some bread", "come rest, try again tomorrow"],
             "pvp_sweat": ["luck is for people who don't grind", "grind harder lol", "rng is part of it, deal"]},
    relate_tail=["the game just does that sometimes", "my luck's been cursed for weeks", "rng is cruel to everyone"],
    resolve_ok=["ok. trying again tomorrow", "fine. one more try."],
    resolve_bad=["the game hates me, i'm sure of it", "i'm cursed, it's official"],
    argue=["you can't skill issue luck", "luck isn't a skill, genius", "go roll the dice yourself then"],
    curt=["unlucky.", "rng."],
    vent_ask=[("can i complain about my luck?", ["lol go", "what now?", "sure"])])

CAT("work", tags=["complaints", "boredom"], leads=["plans", "doing", "home"],
    comfort={"any": ["you work way too hard", "take a day off, seriously", "you've earned a break",
                     "nobody works as hard as you"],
             "helper": ["let me take some of that off you", "i'll do a run for you tomorrow",
                        "you can't do everything alone, let me help"],
             "homebody": ["come sit, the work will still be there", "you need a nap and a warm meal"],
             "pvp_sweat": ["grinding builds character", "no days off", "work hard now, flex later"],
             "speedrunner": ["you need a better system, not more hours", "automate it, then complain"]},
    relate_tail=["it never ends, does it", "work work work, every day", "i feel that in my bones"],
    resolve_ok=["ok. maybe i'll take tomorrow off", "a break sounds nice actually"],
    resolve_bad=["and tomorrow i do it all again", "the list never gets shorter"],
    argue=["working hard isn't a skill issue", "you try doing all of this", "you'd quit in a day"],
    curt=["work's work.", "do less then."],
    vent_ask=[("can i complain about work for a sec?", ["sure", "lol always", "what are you stuck doing?"])])

CAT("lonely", soft=True, tags=["feelings", "friends"], leads=["friends", "feelings", "plans"],
    comfort={"any": ["hey, you've got me", "i'm glad you told me", "you're not as alone as you feel",
                     "i like talking to you, for what it's worth"],
             "helper": ["come find me any time, i mean it", "we should hang out more, seriously",
                        "i'll check on you more often"],
             "homebody": ["come over, i'll make food and we can talk", "my door's always open for you"],
             "pvp_sweat": ["come train with me then", "you can watch me fight if you want"]},
    relate_tail=["it gets better once you find your people", "felt like that for ages", "you're not the only one"],
    resolve_ok=["thanks. that means a lot", "ok. that actually helps"],
    resolve_bad=["i'll still be alone tonight though", "it's fine. i'm used to it."],
    awkward=["uh. you can come spar with me if you want", "i'm not good at feelings. want to fight zombies",
             "lonely? join my practice fights lol"],
    curt=["alone's not so bad.", "i like being alone."],
    vent_ask=[("can i be honest about something?", ["sure", "of course", "what's up?"])])

CAT("homesick", tags=["home", "memories"], leads=["home", "memories", "feelings"],
    comfort={"any": ["places stay with you", "you can make a new place feel like that", "it sounds like it was a good home",
                     "missing it means it mattered"],
             "helper": ["we could go visit it together sometime", "i'll help you make this place feel like home"],
             "homebody": ["oh i get that, home is everything", "give it time, this place will grow on you"],
             "pvp_sweat": ["new base, new start. that's how i see it", "bases are just walls, man"]},
    relate_tail=["i still think about my first hut", "you never really stop missing it", "my old place is a ruin now"],
    resolve_ok=["thanks. maybe i'll visit someday", "yeah. new memories here too"],
    resolve_bad=["it's not the same though", "nowhere feels like that place"],
    awkward=["bases come and go. build a better one", "just build a copy of it here lol"],
    curt=["old places.", "move back then."],
    vent_ask=[("can i get sappy for a sec?", ["sure", "go on", "uh oh, what's up?"])])

CAT("loss", soft=True, tags=["feelings", "friends"], leads=["memories", "feelings", "friends"], needs=["!strangers"],
    sit=["a:mood_grieving"],
    comfort={"any": ["i'm so sorry", "that's really hard", "it's ok to be sad about it", "i'm here, take your time"],
             "helper": ["you don't have to be ok right now", "i'll stay with you a while",
                        "come by whenever, even just to sit"],
             "homebody": ["come over tonight, you shouldn't be alone", "i'll make some stew, come sit with me"],
             "pvp_sweat": ["...i'm sorry, man", "that sucks. really."]},
    relate_tail=["it never fully goes away, but it gets lighter", "i still talk about mine sometimes",
                 "remembering helps, weirdly"],
    resolve_ok=["thanks for listening", "it helps to say it out loud"],
    resolve_bad=["i just miss them", "it doesn't feel real yet"],
    awkward=["i don't know what to say. sorry.", "want to come hit a training dummy with me. helps me"],
    curt=["sorry.", "that's hard."],
    vent_ask=[("can i talk about something heavy?", ["of course", "always", "yeah, what is it?"])])

CAT("newbie", tags=["feelings", "advice"], leads=["advice", "help", "feelings"], needs=["a:voice_newbie"],
    sit=["a:mood_frustrated"],
    comfort={"any": ["everyone starts somewhere", "you're learning way faster than you think",
                     "i was worse when i started, trust me", "being new is hard, give yourself time"],
             "helper": ["i'll teach you, it's fun, promise", "come with me tomorrow, i'll show you",
                        "you're doing great, honestly"],
             "homebody": ["you've got a roof and food, that's a win", "small steps, you'll get there"],
             "pvp_sweat": ["you're bad now, so was i. grind.", "every pro was a noob once"]},
    relate_tail=["my first week was a disaster too", "i died like ten times my first night", "nobody's good at the start"],
    resolve_ok=["ok, i'll keep trying", "thanks, i feel less useless"],
    resolve_bad=["i still feel so behind", "everyone else makes it look easy"],
    argue=["i'm literally new", "you've been at this way longer, that's not fair", "everyone's bad at first, rude"],
    curt=["you'll learn.", "practice."],
    vent_ask=[("can i tell you something embarrassing?", ["sure", "lol go ahead", "what is it?"])])

CAT("weather", tags=["weather", "complaints"], leads=["weather", "sky", "home"],
    comfort={"any": ["weather can't last forever", "it'll clear up, it always does", "ugh, the sky's been brutal",
                     "at least it's good for the crops"],
             "helper": ["come wait it out at my place", "i've got a spare bed if you want to sleep it off"],
             "homebody": ["perfect weather to stay in, honestly", "rainy days are for staying home"],
             "pvp_sweat": ["rain makes the mobs stick around, i love it", "who cares, fight in the rain"]},
    relate_tail=["i just stay in when it's like that now", "the sky hates us all", "worst week of weather i've had"],
    resolve_ok=["ok. i'll wait it out", "fine, i'll stay in today"],
    resolve_bad=["it's going to rain forever, i know it", "the sky owes me one"],
    awkward=["weather's weather. go fight something.", "rain's fine. mobs don't burn, more to fight"],
    curt=["it's rain.", "stay in then."],
    vent_ask=[("can i complain about the weather?", ["lol sure", "classic", "go on"])])

CAT("night", tags=["night", "danger"], leads=["night", "sleep", "mobs"], sit=["time_night"],
    comfort={"any": ["nights are rough, i get it", "you've made it to morning every time so far",
                     "the dark gets to everyone", "morning always comes"],
             "helper": ["you can stay at mine tonight", "i'll walk you home before dark", "next night, we stick together"],
             "homebody": ["that's why i'm in bed by dusk lol", "a bed and four walls, that's the secret"],
             "pvp_sweat": ["night's the best part, more mobs", "the dark's where the xp is"]},
    relate_tail=["i still jump at every sound", "nights used to scare me so bad", "i slept with ten torches for a week"],
    resolve_ok=["ok. maybe i'll sleep at yours", "thanks, i'll be ok"],
    resolve_bad=["i'm still not going out tonight", "i'll be awake listening anyway"],
    argue=["it's DARK, there's nothing to see", "you try it with no torches", "you'd be scared too"],
    curt=["it's just night.", "sleep then."],
    vent_ask=[("can i admit something?", ["sure", "go on", "what is it?"])])

CAT("lost", tags=["exploring", "complaints"], leads=["exploring", "places", "advice"], sit=["a:mood_anxious"],
    comfort={"any": ["getting lost is the worst", "glad you found your way back", "everyone gets turned around sometimes",
                     "you made it back, that's what counts"],
             "helper": ["next time we'll go together", "i'll show you the landmarks around here", "i can help you find it"],
             "homebody": ["that's why i don't wander far lol", "home's right here, you made it"],
             "pvp_sweat": ["just remember where you went lol", "count your steps next time"]},
    relate_tail=["now i pillar up and look around every hour", "i still don't know how i got back",
                 "never leaving without a compass again"],
    resolve_ok=["ok. compass next time", "yeah. i'm back now at least"],
    resolve_bad=["i still don't know where i was", "i'll probably get lost again tomorrow"],
    argue=["every tree looks the same, ok", "you try finding a hole in a forest", "the sun moved, not me"],
    curt=["use the sun.", "you're back now."],
    vent_ask=[("can i tell you how dumb i was today?", ["lol go", "always", "what did you do?"])])

CAT("villagers", tags=["villagers", "complaints"], leads=["villagers", "trade", "town"],
    comfort={"any": ["villagers have no idea what they're doing", "they mean well. probably.", "ugh, villagers",
                     "you're too patient with them"],
             "helper": ["i'll help you fence them in", "let's fix it together, they need us"],
             "homebody": ["at least they're company", "they're sweet in their own way"],
             "pvp_sweat": ["villagers are just shops with legs", "they're shops, don't stress"]},
    relate_tail=["one walked into my lava moat once", "villagers drive everyone nuts", "they never learn"],
    resolve_ok=["ok. they're still dumb though", "fine, i'll fence them in"],
    resolve_bad=["i'm not trading with them for a week", "they're going to do it again"],
    argue=["how is a villager's brain my skill issue", "you try herding them", "they're not even mine to control"],
    curt=["villagers.", "hrmm."],
    vent_ask=[("can i complain about villagers?", ["lol yes", "what did they do now?", "always"])])

CAT("town", tags=["town", "complaints"], leads=["town", "gossip", "building"], needs=["a:in_town"], scale=0.7,
    comfort={"any": ["town life isn't for everyone", "you're allowed to want some quiet", "it's a lot, i get it",
                     "honestly i think the same sometimes"],
             "helper": ["we could bring it up with everyone", "i'll talk to them for you if you want",
                        "let's find you a quieter corner"],
             "homebody": ["make your house your own little world", "close the door, light a candle, ignore them"],
             "pvp_sweat": ["just tell them off lol", "i'll have a word with them if you want"]},
    relate_tail=["i thought about leaving town once too", "everyone grumbles about town stuff", "town life, man"],
    resolve_ok=["ok. i'll stick it out", "maybe it's not that bad"],
    resolve_bad=["one more week and i'm moving out", "i'm still not happy about it"],
    argue=["how is a crowded town my skill issue", "oh sure, i'll just fix the whole town", "you live here too"],
    awkward=["want me to go yell at someone", "i could guard your door lol"],
    curt=["towns.", "leave then."],
    vent_ask=[("can i complain about the town for a sec?", ["sure", "lol always", "what now?"])])

CAT("restless", tags=["feelings", "boredom"], leads=["feelings", "plans", "doing"],
    comfort={"any": ["some days are just like that", "you'll get back on track", "tomorrow's a fresh start",
                     "don't be so hard on yourself"],
             "helper": ["want me to help you get going again", "i'll keep you company while you figure it out",
                        "we can do something together, shake it up"],
             "homebody": ["sometimes a slow day is a good day", "rest, it'll come back to you"],
             "pvp_sweat": ["go hit something, clears the head", "a fight fixes everything, trust me"]},
    relate_tail=["it passed for me, it'll pass for you", "happens to me every few weeks", "we all get stuck sometimes"],
    resolve_ok=["ok. tomorrow then", "yeah. i'll shake it off"],
    resolve_bad=["i still feel stuck", "i don't know, it's just a rut"],
    argue=["you don't get it, it's how i'm built", "easy for you, you just hit things", "it's not about skill"],
    curt=["it passes.", "do something then."],
    vent_ask=[("can i whine about nothing for a sec?", ["lol sure", "go on", "what's up?"])])

# =====================================================================================================================
# Grievances
# =====================================================================================================================

GRIEVANCES = []


def G(slug, cat, about, o, q, m, r, a, h, j=None, s=None, c=None, w=None, sit=None, needs=None, tags=None):
    """One grievance: o = a's openers, q = b's questions [(question, [a's answers])], m = a's more detail, r = b's own story,
    a = b's advice, h = b's offers of help, j = b's jokes (None: no joke topic), s = b's "skill issue" lines (None: the kind's
    awkward comfort, if it has one), c = b's comfort for this one, w = what made it worse (a two-part rant),
    sit = situational needs (default: the kind's), needs = needs for every topic, tags = tags (default: the kind's)."""
    GRIEVANCES.append(dict(slug=slug, cat=cat, about=about, o=o, q=q, m=m, r=r, a=a, h=h, j=j, s=s, c=c or [], w=w,
                           sit=sit, needs=needs, tags=tags))


# ---- Dying and losing its loot -------------------------------------------------------------------------------------

G("lava", "death", "losing its stuff in lava",
  o=["fell in lava with all my stuff", "lost my whole inventory to lava", "lava ate everything i had on me",
     "mined one block too many and there was lava", "watched my diamond pick burn in lava"],
  q=[("were you mining?", ["yeah, digging down like an idiot", "strip mining, broke one block and there it was"]),
     ("did anything survive?", ["nothing. lava eats everything", "not a single item. not one."]),
     ("how much did you have on you?", ["a stack of iron and my good pick", "everything. i was on my way home."])],
  m=["i heard it bubbling and kept going anyway", "the lava was right behind the stone", "i had just enchanted that pick too",
     "i was literally on my way back to my chest"],
  r=["same, i lost a full iron set in lava once", "i fell in lava on my second day, lost everything",
     "i swam in lava by accident once. once.", "lava took my first diamond sword, i still miss it"],
  a=["always carry a water bucket, it saves you", "never dig straight down, ever",
     "if you hear lava, back off and wall it up", "keep spare gear in a chest at home"],
  j=["the lava says thanks for the donation", "at least it was a warm way to go", "lava's just hungry, you fed it"],
  h=["i'll give you my spare pick to start over", "come mining with me, we'll get it back", "i've got iron to spare, take some"],
  s=["who digs down without a water bucket", "you heard the lava and kept mining?"],
  w=["and then i went back down and nearly fell in again", "and the pick had fortune on it", "and my backup pick was at home"])

G("creeper", "death", "a creeper blowing it up",
  o=["a creeper blew me up and my stuff went everywhere", "creeper snuck up on me again", "didn't even hear the creeper, just boom",
     "lost half my stuff to a creeper blast", "a creeper got me right at my own door"],
  q=[("did you hear the hiss?", ["way too late", "nope, just boom"]),
     ("did you see it coming?", ["not even a little", "i saw it too late"]),
     ("did you get your stuff back?", ["some. the blast threw it everywhere", "half of it fell in the hole"])],
  m=["i was just standing there sorting my stuff", "it came from behind a tree", "the hole it left is still there",
     "it waited till i turned around, i swear"],
  r=["a creeper took out me and my front wall once", "creepers love me too, three times this week",
     "one got me while i was asleep. asleep!"],
  a=["always look behind you when you stop", "light up the area around your door",
     "if you hear the hiss, sprint away, don't turn around"],
  j=["the creeper just wanted a hug", "nice crater though, very rustic", "the creeper said ssss... surprise"],
  h=["i'll help you fill the crater back in", "let's light up your place so it doesn't happen again", "i've got spare dirt, lots of it"],
  s=["just hit it before it hisses lol", "creepers are slow. you were slower."],
  w=["and then a second one showed up", "and it blew up my chest too", "and my bed went flying"],
  sit=["a:mood_angry"])

G("fall", "death", "falling to its death",
  o=["died to fall damage like an idiot", "misjudged a jump and fell to my death", "fell down a ravine and lost everything",
     "one wrong step off a cliff and that was it", "fell into a cave i didn't even see"],
  q=[("how high was it?", ["high enough", "like forty blocks, no joke"]),
     ("were you running?", ["sprinting, of course", "yeah, in the dark"]),
     ("could you get your stuff back?", ["it's at the bottom of a ravine", "took me an hour to climb down"])],
  m=["it was dark and i thought it was a block lower", "i was trying to jump a gap", "the grass hid the edge",
     "i didn't even have a water bucket"],
  r=["i fell off my own roof once lol", "same, a ravine got me on my first day", "i jumped off a mountain thinking there was water"],
  a=["learn the water bucket trick, it saves you", "sneak near edges, you can't fall off", "pillar down, don't jump down"],
  j=["gravity's undefeated", "you tried flying, respect", "the ground came up fast huh"],
  h=["i'll help you climb down and get it", "let's bridge over that ravine so nobody falls again", "i'll lend you a water bucket"],
  s=["sneak exists for a reason", "you walked off a cliff. that's the whole story."],
  w=["and then i fell in the same ravine going back for it", "and my stuff landed in water and floated off",
     "and there was a zombie waiting at the bottom"])

G("skeleton_cliff", "death", "a skeleton shooting it off a ledge",
  o=["a skeleton knocked me off a cliff", "skeleton shot me off the ledge, gone", "died to a skeleton i couldn't even see",
     "a skeleton sniped me from across the cave", "got shot down by a skeleton in a ravine"],
  q=[("where was it shooting from?", ["somewhere in the dark", "across the ravine"]),
     ("did you have a shield?", ["no, that's the worst part", "it was in my chest at home"])],
  m=["every arrow knocked me back further", "i was on a ledge mining iron", "i couldn't even reach it", "it just kept shooting"],
  r=["skeletons have knocked me into lava twice", "same, one shot me off my own bridge", "i hate skeletons more than anything"],
  a=["carry a shield, it blocks the arrows", "get to cover first, then close in", "don't fight skeletons on a ledge"],
  j=["it's got no eyes and still hit you", "that skeleton's bragging to its friends right now", "perfect aim, zero brain"],
  h=["i'll make you a shield, i have iron", "let's go clear that cave together", "i'll come back with you and cover you"],
  s=["shield. make one.", "you got outaimed by a skeleton lol"],
  w=["and then it shot me again when i went back", "and my bow went flying into the ravine"],
  sit=["a:mood_angry"])

G("drown", "death", "drowning",
  o=["drowned trying to get to an underwater ravine", "ran out of air down in the ocean", "drowned in a flooded cave",
     "i drowned. in water. like a fish that can't swim.", "got stuck under ice and drowned"],
  q=[("what were you doing down there?", ["chasing some iron i saw", "looking for a shipwreck"]),
     ("didn't you see your bubbles?", ["i saw them, i thought i'd make it", "i was too busy mining"])],
  m=["i was one block from the surface", "the way out was blocked by gravel", "i forgot doors give you air",
     "my stuff's at the bottom of the ocean now"],
  r=["same, i drowned looking for a shipwreck", "i drowned in a lake once, a small lake", "ice trapped me once too, worst way to go"],
  a=["place a door underwater, it makes an air pocket", "always watch your bubbles", "bring a boat, you can breathe in it"],
  j=["you were one with the fish", "the squids were laughing at you", "glub glub glub"],
  h=["i'll dive down with you and get it", "let's make you some doors for air", "i've got a boat you can use"],
  s=["you can see your air, you know", "who mines underwater without a door"],
  w=["and then a drowned picked up my sword", "and when i dove back down it was all gone"])

G("void", "death", "the void taking its gear", tags=["end", "death", "complaints"],
  o=["fell into the void in the end, everything's gone", "an enderman knocked me off the end island", "lost a full kit to the void",
     "the void took my best gear", "fell off the end, no coming back from that"],
  q=[("what knocked you off?", ["an enderman i looked at", "i bridged and missed a block"]),
     ("what did you have?", ["full iron and my bow", "everything i owned, basically"])],
  m=["there's no getting stuff back from the void", "i was so close to the portal", "i just kept falling for ages",
     "i didn't even reach the dragon"],
  r=["the void took my diamond sword once", "same, an enderman did it to me too", "i still dream about falling in the void"],
  a=["put a railing on your bridges out there", "don't look endermen in the eye in the end", "bring blocks and bridge wide"],
  j=["the void's just hungry", "say hi to everything else that fell", "you're still falling somewhere lol"],
  h=["i'll help you gear up before you go back", "next time we go together", "i've got spare arrows for you"],
  s=["you looked at an enderman. in the end. come on.", "bridge better lol"],
  w=["and my ender pearls were in that inventory too", "and the portal home was right there"])

G("cactus", "death", "dying to a cactus",
  o=["died to a cactus. a cactus.", "walked into a cactus and lost my stuff", "a cactus killed me and shredded my items",
     "the desert got me, cactus edition", "pricked to death by a plant, my life"],
  q=[("how do you die to a cactus?", ["very slowly and very stupidly", "i was being chased and backed into it"]),
     ("did your stuff survive?", ["cactus destroys items, so no", "half of it got shredded"])],
  m=["i was running from a husk", "i couldn't see it, it was night", "something knocked me into it",
     "my stuff landed on it and vanished"],
  r=["i lost a stack of iron to a cactus once", "same, cactus farm accident", "cacti have killed me more than creepers"],
  a=["never fight next to a cactus", "light the desert path so you can see them", "cactus breaks items, so stay well clear"],
  j=["the cactus wins this round", "you hugged a cactus, bold", "worst way to go, top five"],
  h=["i'll come back to the desert with you", "let's chop that cactus down together", "i can spare some food and a sword"],
  s=["it doesn't even move", "lost to a plant, couldn't be me"])

G("sneak_zombie", "death", "zombies jumping it in the dark",
  o=["got jumped by zombies in the dark", "a zombie got me from behind at night", "three zombies cornered me outside",
     "died to zombies right outside the wall", "a zombie came out of nowhere and i panicked"],
  q=[("how many were there?", ["three, maybe four", "i stopped counting"]),
     ("why were you out at night?", ["i was almost home", "i lost track of time"])],
  m=["i swung and missed like five times", "one had armor on, not fair", "i backed into another one", "i was so close to my door"],
  r=["zombies got me on my first night too", "same, i got surrounded by like six once", "a zombie with a shovel got me once lol"],
  a=["back up while you swing", "light the path home", "get inside before dusk, always"],
  j=["they just wanted to say hi", "groaning fan club", "they liked your brains"],
  h=["i'll walk home with you at dusk", "i'll make you a better sword", "let's light up the path to your place"],
  s=["zombies. the slowest mob. ok.", "back up and swing, it's not hard"],
  sit=["time_night"])

G("despawn", "death", "its things despawning",
  o=["my stuff despawned before i got back", "died far from home and lost everything to despawn",
     "ran all the way back and my items were gone", "five minutes. that's all you get. gone.", "everything despawned, even my diamonds"],
  q=[("how far away were you?", ["way out past the river", "like a thousand blocks out"]),
     ("couldn't you get there faster?", ["i got lost on the way", "zombies slowed me down"])],
  m=["i found the spot and it was just empty", "i'd have made it if not for the river", "i had a full stack of iron",
     "my map was in there too"],
  r=["same, i lost a full kit to despawn", "i ran back once and got there seconds late", "despawn timers have ruined me"],
  a=["set your bed near where you're working", "leave your good stuff in a chest before long trips",
     "remember a landmark where you die"],
  j=["your items went to a better place", "they got bored waiting for you", "you got ghosted by your own stuff"],
  h=["let me help you gather some basics", "i'll give you some spare tools", "come on, we'll go get you new gear"],
  s=["run faster", "should've slept closer lol"],
  w=["and then i died again on the way back", "and i'd only just enchanted my sword"],
  sit=["a:died_lately"])

G("diamond_pick", "death", "losing its diamond pickaxe",
  o=["lost my diamond pickaxe when i died", "had my diamond pick for weeks and now it's gone", "my diamond pick is gone forever",
     "died with my only diamond tool on me", "lost the pick i spent forever making"],
  q=[("how many diamonds was that?", ["three, found one at a time", "three. three diamonds."]),
     ("was it enchanted?", ["efficiency and unbreaking", "no, but it was mine"])],
  m=["i named it and everything", "it had been with me since the first cave", "i was saving up for a sword too",
     "i keep reaching for it"],
  r=["same, i lost a diamond sword once", "i lost my first diamond pick in lava", "my diamond pick fell in a ravine once"],
  a=["take an iron pick on risky trips, keep diamond for home", "keep the next one in a chest when you explore",
     "mending helps, but not dying helps more"],
  j=["rip pick, you'll be missed", "a moment of silence for the pick", "it's in pickaxe heaven now"],
  h=["i have a diamond i can give you", "come mining with me, we'll find more", "i'll lend you my iron pick for now"],
  s=["who takes their only diamond pick into danger", "should've not died lol"],
  sit=["a:died_lately"])

G("witch", "death", "a witch killing it",
  o=["a witch killed me with poison", "witches are the worst, one just ended me", "got splashed to death by a witch",
     "a witch kept drinking healing and i couldn't win", "a witch near the swamp wrecked me"],
  q=[("did you get close to it?", ["it kept drinking potions", "every time i got close it splashed me"]),
     ("where was it?", ["near the swamp", "right on my way home"])],
  m=["it threw slowness, then poison", "i had no milk on me", "it healed every time i hurt it", "it laughed at me, i swear"],
  r=["same, a witch got me in the swamp", "witches have killed me more than creepers", "i hate that witch laugh"],
  a=["rush them, don't stand at range", "carry milk for the poison", "shoot them from far away"],
  j=["she cast 'you're dead'", "should've brought a broom", "the witch was just doing chemistry"],
  h=["let's go clear that swamp together", "i'll bring milk next time", "i'll make you a bow for witches"],
  s=["just rush it lol", "lost to a potion seller"])

G("enderman", "death", "an enderman it looked at",
  o=["looked at an enderman by accident and it killed me", "an enderman hunted me down", "made eye contact with an enderman, mistake",
     "an enderman teleported right behind me", "died to an enderman i didn't even mean to look at"],
  q=[("why'd you look at it?", ["i didn't mean to!", "it was in front of the tree i was chopping"]),
     ("did you fight it?", ["it kept teleporting", "my sword wasn't enough"])],
  m=["it screamed and then it was on me", "i tried to hide under a roof", "it hit so hard", "i should've run for water"],
  r=["same, an enderman ended me in a forest", "i fought one in the rain once, it gave up lol", "endermen scare me so bad"],
  a=["stand under a two block roof, they can't reach you", "get in water, they hate it", "look at their feet, never the face"],
  j=["it was just shy", "don't stare, it's rude", "it was trying to make friends"],
  h=["i'll carve you a pumpkin to wear lol", "let's go fight one together", "i'll lend you my sword"],
  s=["don't look at it. that's the whole trick.", "two block roof, easy"])

G("gravel", "death", "falling gravel burying it",
  o=["gravel fell on my head and i suffocated", "died to falling gravel, embarrassing", "dug up into gravel and got buried",
     "the ceiling was all gravel and it came down", "sand dropped on me while i was mining"],
  q=[("were you digging up?", ["yes, and i know you shouldn't", "only one block, i swear"]),
     ("couldn't you dig out?", ["it kept falling", "i panicked and dug the wrong way"])],
  m=["it just kept coming", "i couldn't see anything", "i had a shovel and everything", "the whole column came down at once"],
  r=["sand buried me in the desert once", "same, gravel got me in a cave", "i got buried in my own tunnel once"],
  a=["never dig straight up", "put a torch under falling blocks", "dig gravel from the side"],
  j=["you got grounded, literally", "gravel's got a grudge", "buried like treasure"],
  h=["i'll come help you dig your stuff out", "i've got a shovel you can have", "let's clear that gravel together"],
  s=["dig up, get buried. classic.", "torch under the gravel. basics."])

G("starved", "death", "starving on a trip", tags=["death", "food", "complaints"],
  o=["ran out of food and starved on my trip", "i starved. i actually starved.", "hunger got me out in the wilds",
     "no food, no animals, just starved", "i forgot food and paid for it"],
  q=[("how far out were you?", ["way out past the hills", "days from home"]),
     ("no animals anywhere?", ["not one cow, not one pig", "just sand and more sand"])],
  m=["i ate my last bread on day two", "even the rotten flesh ran out", "i could see a village but didn't make it",
     "my hearts just kept dropping"],
  r=["same, i starved in a desert once", "i ate rotten flesh for three days once", "hunger nearly got me on a mining trip"],
  a=["always carry a stack of bread", "bring seeds and plant as you go", "hunt before you leave, not after"],
  j=["you forgot the most important item: food", "the hunger bar said bye", "should've eaten the dirt"],
  h=["i'll give you a stack of bread", "come eat at mine tonight", "let's set up a farm for your trips"],
  s=["bring food. that's it.", "starved? with cows everywhere?"],
  sit=["a:hungry"])

G("near_home", "death", "dying at its own door",
  o=["died right outside my own door", "i could see my house when i died", "two blocks from home, that's where i died",
     "died on my own doorstep", "made it all the way back and died at the door"],
  q=[("what got you?", ["a creeper hiding by the wall", "zombies waiting at my door"]),
     ("couldn't you get inside?", ["the door was right there", "i fumbled the door, it closed on me"])],
  m=["i had a full haul from the mine", "i literally touched the door", "i was so close to my bed",
     "my stuff is lying on my porch"],
  r=["same, i died at my door after a huge mine trip", "i died on my own roof once", "my house was right there too, it hurts"],
  a=["light up all around your house", "build a little wall around your porch", "make a second door you can duck into"],
  j=["at least your stuff's close", "home delivery, for the mobs", "you died at home, technically safe"],
  h=["i'll help you light up around your house", "let's build you a wall", "i'll keep watch at dusk"],
  s=["you couldn't open a door?", "died at home. impressive."],
  sit=["a:died_lately"])

G("nether_gold", "death", "piglins in the nether", tags=["nether", "death", "complaints"],
  o=["forgot my gold in the nether and piglins swarmed me", "piglins mobbed me in the nether", "the nether ate my whole kit",
     "lost everything to piglins", "went to the nether without gold, never again"],
  q=[("how many piglins?", ["like six", "a whole crowd"]),
     ("did you get your stuff?", ["it's next to lava, so no", "the piglins picked some of it up"])],
  m=["i forgot to put my gold boots on", "my gold helmet was in my chest at home", "they went straight for me",
     "i couldn't find the portal after"],
  r=["same, i forgot gold once. only once.", "piglins took my crossbow once", "the nether's ruined me so many times"],
  a=["always wear one piece of gold", "mark your portal with torches", "bring fire resistance if you can"],
  j=["piglins are just picky about fashion", "you broke the dress code", "no gold, no entry"],
  h=["i'll make you gold boots", "next nether trip we go together", "i'll help you find your portal"],
  s=["gold boots. that's all it takes.", "forgot gold? rookie."],
  w=["and a ghast shot at me while i ran", "and my portal was surrounded when i got back"])

G("ghast", "death", "a ghast knocking it into lava", tags=["nether", "death", "complaints"],
  o=["a ghast knocked me into lava", "ghast fireball sent me flying into lava", "died to a ghast in the nether",
     "a ghast blew up my bridge and i fell", "ghasts are the worst, one ruined my trip"],
  q=[("were you bridging?", ["yeah, over a lava lake", "halfway across"]),
     ("did you hit it back?", ["i tried, missed", "i didn't even see it"])],
  m=["the screech still haunts me", "i heard it and froze", "my bridge was netherrack, it blew right up", "everything burned"],
  r=["same, a ghast got me over lava", "i hate that screech so much", "ghasts made me skip the nether for a week"],
  a=["bridge with cobblestone, ghasts can't break it", "hit the fireball back at it", "put walls on your bridge"],
  j=["the ghast was just crying", "it's a big crying jellyfish", "it sneezed on you"],
  h=["i'll help you build a covered bridge", "next time i'll cover you", "i'll lend you a bow"],
  s=["you hit the fireball back. it's not hard.", "netherrack bridge? come on"])

G("died_recent", "death", "dying lately", needs=["a:died_lately"], sit=["a:mood_frustrated"],
  o=["so i {a.death}", "guess who {a.death}", "i {a.death} and lost everything", "don't laugh, but i {a.death}",
     "i died and lost all my stuff"],
  q=[("did you get your stuff back?", ["most of it", "nope, it's gone"]),
     ("how far from home?", ["too far", "close, which makes it worse"])],
  m=["it happened so fast", "i was having such a good day", "i had just got all my stuff together",
     "respawning with nothing is the worst"],
  r=["i died twice yesterday, so you're doing better", "same, i lost my stuff last week", "dying's the worst part of this life"],
  a=["keep a spare kit at home", "set your bed close to where you work", "remember where it happened, go back fast"],
  j=["welcome back to the land of the living", "respawn speedrun lol", "you looked good dead though"],
  h=["i'll give you some spare stuff", "come with me, we'll gear you up", "need food? i've got extra"],
  s=["skill issue, simple as", "died again? shocking"])

G("close_call", "close", "nearly dying", tags=["danger", "health", "complaints"], needs=["a:close_call"], sit=["a:hurt"],
  o=["{a.close_call} nearly killed me", "i almost died to {a.close_call}", "half a heart left after {a.close_call}",
     "i nearly died earlier", "i was one hit from dead earlier"],
  q=[("what did you do?", ["ran. just ran.", "ate everything i had"]),
     ("are you ok now?", ["shaky but ok", "still on like three hearts"])],
  m=["my heart is still pounding", "i thought that was it, i really did", "i had all my good stuff on me",
     "i'm not going back there today"],
  r=["i had a close one with a creeper yesterday", "i got down to half a heart once, never forgot it", "i nearly died twice last week"],
  a=["eat before you fight, always", "back off when you're under five hearts", "keep a golden apple for moments like that"],
  j=["you're basically immortal now", "death missed you by a hair", "the respawn point was getting lonely"],
  h=["here, take some food", "stay with me today, i'll watch your back", "i'll walk you home"],
  s=["shouldn't have been that close", "half a heart means you got outplayed"])

# ---- A build going wrong -------------------------------------------------------------------------------------------

G("collapsed", "build", "its build falling down",
  o=["took out one block and the whole wall came down", "my sand roof collapsed on me", "the gravel floor in my build just fell",
     "half my build fell into a cave underneath", "my tower came down, there was sand under the base"],
  q=[("how much fell?", ["the whole front wall", "most of the roof"]),
     ("what was holding it up?", ["sand, because i had a lot of it", "nothing, apparently"])],
  m=["i spent all day on it", "i only wanted to fix one block", "it poured down like a waterfall",
     "now there's a hole right in the middle"],
  r=["same, i built a sand bridge once, never again", "my gravel path dropped into a cave", "i built over a cave and it all fell in"],
  a=["put a solid block under sand before you build on it", "build with stone, it doesn't fall",
     "check for caves before you start a big build"],
  j=["it's a modern art piece now", "it was a waterfall build all along", "the sand just wanted to lie down"],
  h=["i'll help you put it back up", "i've got cobble for a proper base", "let's rebuild it right this time"],
  s=["sand falls. everyone knows sand falls.", "built on sand. classic."],
  w=["and then the rest fell while i was fixing it", "and half the blocks dropped into the cave below"],
  sit=["a:mood_frustrated"])

G("ugly", "build", "its house looking ugly", needs=["!a:no_home"], sit=["a:mood_frustrated"],
  o=["my house looks so ugly", "spent three days on a house and i hate it", "finished my build and it looks terrible",
     "my house looks like a box with a hat", "i can't stand looking at my build"],
  q=[("what's wrong with it?", ["everything. the shape, the roof, all of it", "it's flat and boring"]),
     ("what's it made of?", ["oak planks. just oak planks.", "cobble and more cobble"])],
  m=["it has no depth at all", "the roof is just a flat lid", "the windows are in weird spots", "it looked better in my head"],
  r=["my first house looked like a shoebox", "same, i hated my house for weeks", "i rebuilt my roof four times"],
  a=["add some depth, pull the frame out a block", "mix in another block, like stone with the wood",
     "put logs on the corners, it helps a lot"],
  j=["it's got character", "ugly houses keep the mobs away", "it's not ugly, it's humble"],
  h=["i'll come help you add some trim", "let's redo the roof together", "i've got logs you can use for corners"],
  s=["it's ugly because you built it", "you built a box. what did you expect."])

G("burned", "build", "its house burning down", sit=["a:no_home", "a:mood_angry"],
  o=["my house burned down", "lava spread fire to my roof and it's gone", "lightning hit my house and it burned",
     "my wooden house is just ash now", "set my own house on fire with a campfire"],
  q=[("how fast did it go?", ["so fast, the roof went first", "minutes. it was minutes."]),
     ("did anything survive?", ["the stone base", "my chests, barely"])],
  m=["it went up so fast", "i tried to put it out with dirt", "my bed burned too", "now it's just a stone floor"],
  r=["same, my first house burned from lava", "lightning took out my barn once", "i set my own roof on fire, kind of"],
  a=["build with stone near lava", "keep fire two blocks from anything wood", "a lightning rod saves houses"],
  j=["at least it was cozy for a minute", "very warm house", "you made a really big campfire"],
  h=["i'll help you rebuild, stone this time", "i've got planks to spare", "let's make a fireproof one together"],
  s=["lava next to wood. ok.", "who builds wood next to lava"],
  w=["and the fire spread to my farm", "and the chest by the wall had my savings in it"])

G("creeper_hole", "build", "a creeper hole in its house", needs=["!a:no_home"], sit=["a:mood_angry"],
  o=["a creeper blew a hole in my wall", "came home to a crater in my house", "creeper took out my door and half the wall",
     "there's a creeper hole in my roof now", "a creeper hole right through my house"],
  q=[("how big is it?", ["big enough to walk through", "like five blocks wide"]),
     ("can you fix it?", ["it's a lot of blocks", "i'll need all my cobble"])],
  m=["i can see the sky through my wall", "it took out a chest too", "my torches were on the wall that blew",
     "i'd just finished the windows"],
  r=["same, one blew up my bedroom once", "my garden got cratered last week", "creepers have redone my house twice"],
  a=["light up the outside more", "cobble walls hold up better than wood", "a fence keeps them off your walls"],
  j=["free skylight", "open plan now", "the creeper was just renovating"],
  h=["i'll help you patch it up", "i've got cobble, let's fix it now", "i'll help light your yard"],
  s=["you didn't light your yard, did you", "should've lit it up"],
  w=["and it blew out the wall right where my chests were", "and a second one finished the job"])

G("ran_out", "build", "running out of blocks", sit=["a:mood_frustrated"],
  o=["ran out of blocks halfway through the roof", "half a roof and no more planks", "my build's stuck, i'm out of stone",
     "i need like two hundred more blocks", "ran out of wood with one wall to go"],
  q=[("how much more do you need?", ["like three stacks", "more than i have, that's for sure"]),
     ("why didn't you get more first?", ["i thought i had enough", "i got excited and started"])],
  m=["now i have to go chop trees again", "it looks so dumb half done", "the rain's getting in", "i miscounted everything"],
  r=["same, my tower's been half done for weeks", "i always run out right at the roof", "my house had no roof for a week once"],
  a=["count your blocks before you start", "gather twice what you think", "plant a tree farm, never run out again"],
  j=["it's an open air house now", "half a roof is still a roof", "skylight build, very trendy"],
  h=["i've got planks, take some", "i'll come chop trees with you", "i'll bring stone over tomorrow"],
  s=["count your blocks lol", "plan ahead next time"])

G("symmetry", "build", "its build being off by one", tags=["building", "decor", "complaints"],
  o=["my house is off by one block and i can't unsee it", "the left side is one block wider", "my symmetry's off and it's ruining it",
     "the door isn't centered, it's killing me", "my tower's one block off center"],
  q=[("does anyone else notice?", ["no, but i do", "nobody, but it's there"]),
     ("can't you fix it?", ["i'd have to redo the whole wall", "i'd have to tear the roof down"])],
  m=["i counted three times", "the roof is crooked because of it", "i stare at it every morning", "it's one block. one."],
  r=["same, my windows are one off and i live with it", "i tore down a wall for one block once",
     "i'm still mad my gate's off center"],
  a=["use an odd width, the middle's easy then", "mark the middle with a block first", "make the other side match, don't redo it"],
  j=["one block off is called rustic", "nobody's perfect, especially your house", "it's asymmetrical on purpose now"],
  h=["i'll help you redo the wall", "let's count it together", "i'll hold the blocks while you fix it"],
  s=["count. it's one, two, three.", "imagine not counting lol"])

G("roof", "build", "its roof", needs=["!a:no_home"], tags=["building", "decor", "complaints"],
  o=["my roof looks terrible", "my roof stairs are all facing the wrong way", "i can't figure out how to make a roof",
     "my roof is just flat and sad", "the corners on my roof are a mess"],
  q=[("what kind of roof?", ["stairs, supposedly", "a slope, sort of"]),
     ("what went wrong?", ["the corners don't meet", "it's too steep and weird"])],
  m=["roofs are the hardest part", "it looks like a pile of stairs", "it leaks, somehow", "i tried three times"],
  r=["roofs took me forever to learn", "my first roof was a flat dirt lid", "i still mess up roof corners"],
  a=["start the stairs from the edges and work up", "slabs help on the corners", "let the roof hang over the walls a block"],
  j=["flat roofs are a style now", "the roof is abstract", "rain's gonna love it"],
  h=["i'll help with the corners", "let's redo it together tomorrow", "i've got spare stairs"],
  s=["roofs aren't hard, you're just bad", "stairs go up. that's it."])

G("walled_in", "build", "forgetting the door",
  o=["built my house and forgot a door", "walled myself in, had to dig out", "my house has no way in, i forgot the door",
     "built four walls and a roof, no door", "i had to punch a hole in my own wall"],
  q=[("how did you get out?", ["punched through the wall", "dug under it"]),
     ("how did you not notice?", ["i was so focused on the roof", "i built it from the inside"])],
  m=["now there's an ugly hole", "the door spot is all wrong now", "i was so proud until then", "i built it at night too"],
  r=["same, i built a house with no windows once", "i forgot the stairs in a two floor house", "i walled in my own chests once"],
  a=["put the door frame in first", "plan the door before the walls", "leave a gap and fill it at the end"],
  j=["very secure house", "zero mobs can get in, nice", "it's a bunker now"],
  h=["i'll help you put a proper door in", "i've got a spare door", "let's redo that wall together"],
  s=["forgot a door. ok.", "how do you forget a door"])

G("palette", "build", "clashing colours", tags=["building", "decor", "complaints"],
  o=["my house colours look awful together", "used birch and acacia, big mistake", "the orange and white just clash",
     "my palette is a crime", "everything's the same block and it's boring"],
  q=[("is it that bad?", ["it hurts to look at", "a sheep looked away, i swear"]),
     ("why those?", ["it's what i had", "i thought it'd look cool"])],
  m=["it looked fine at night", "in daylight it's so bad", "it hurts to look at", "someone said it looks like a cake"],
  r=["same, i did a pink and green house once", "my first build was all birch, looked like a ghost",
     "i mixed sandstone and nether brick once"],
  a=["pick two or three blocks that go together", "dark wood with stone always works", "spruce and cobble, can't go wrong"],
  j=["it's bold, i'll give you that", "you can see it from the next biome", "very cheerful"],
  h=["i'll trade you some spruce", "let's swap the trim together", "i'll help you pick blocks"],
  s=["you have no taste, it's ok", "who uses acacia"])

G("dark_spot", "build", "mobs spawning inside its house", needs=["!a:no_home"], tags=["building", "mobs", "complaints"],
  sit=["time_night"],
  o=["a zombie spawned inside my house", "a creeper spawned in my basement", "found a skeleton in my own bedroom",
     "my house has a dark corner and mobs keep spawning", "something spawned in my storage room"],
  q=[("how did it get in?", ["it didn't, it spawned inside", "one dark corner, that's all it takes"]),
     ("did it do anything?", ["it nearly blew up my chests", "it hit me while i slept"])],
  m=["i woke up to groaning", "it nearly blew up my chests", "i thought i lit everything", "i don't feel safe in my own house now"],
  r=["same, i had a spider in my attic", "a creeper spawned under my stairs once", "i found a zombie in my mine house once"],
  a=["put a torch every few blocks inside", "slabs on the floor stop spawns", "check every corner before night"],
  j=["you've got a roommate now", "free mob farm", "it pays rent in rotten flesh"],
  h=["i'll bring torches over", "let's light your place together", "i'll help you check every corner"],
  s=["torches exist", "you didn't light your own house?"])

G("flooded", "build", "its house flooding", needs=["!a:no_home"],
  o=["water broke into my basement", "my house flooded", "dug into a lake and flooded my mine", "the river got into my storage room",
     "everything in my cellar's underwater"],
  q=[("how did it get in?", ["i mined into the lake", "i broke the wrong block"]),
     ("can you drain it?", ["i'm trying, it's a lot", "sponges would help, i don't have any"])],
  m=["my chests are underwater now", "my torches all popped off", "it's so much water", "it's still flowing"],
  r=["same, i flooded my mine once", "i dug into the ocean by accident once", "my basement's been wet for days"],
  a=["fill it with sand, then dig the sand out", "find the source blocks and block them", "leave a wall between you and water"],
  j=["indoor pool, fancy", "your house has a moat now, inside", "you can fish in your basement"],
  h=["i'll help you drain it", "i've got sand for it", "let's block it off together"],
  s=["you dug into a lake. you saw the lake.", "should've looked"],
  w=["and my torches floated off with the current", "and it found my storage room"])

G("slope", "build", "building on a slope",
  o=["built my house on a hill and it's all wonky", "my foundation's crooked because of the slope", "my house floats on one side",
     "the ground is uneven under my house", "half my house is inside the hill"],
  q=[("why there?", ["the view was nice", "it looked flat at first"]),
     ("can you level it?", ["it's a lot of digging", "i'd have to tear it down"])],
  m=["one side is on pillars", "the door opens into dirt", "it looks so silly from the side", "my floor has steps in it now"],
  r=["same, my house on a cliff looked so odd", "i built on a hill once and gave up", "my first house was half in a mountain"],
  a=["level the ground before you build", "put a stone base under it to make it flat", "build into the hill on purpose"],
  j=["it's a split level house", "very modern", "the house is just relaxing on the hill"],
  h=["i'll help you dig it flat", "let's add a stone base", "i'll bring a shovel"],
  s=["level the ground first, rookie", "built on a hill. ok."])

G("redo", "build", "tearing down its build again", sit=["a:mood_frustrated"],
  o=["had to tear down half my build", "built the wrong size and had to redo it", "wasted two days on a wall i just took down",
     "my build was too small so i'm starting over", "tore my house down again"],
  q=[("why?", ["it was too small", "the shape was all wrong"]),
     ("how much did you take down?", ["half of it", "everything but the floor"])],
  m=["so many blocks", "i keep starting over", "it's the third time", "every time i think it's done, it isn't"],
  r=["same, i've rebuilt my house four times", "i tear stuff down all the time", "my castle's on version five"],
  a=["mark the size out with dirt first", "build the outline before the walls", "start small and add on"],
  j=["build it, hate it, repeat", "the house is a work in progress forever", "practice for the real one"],
  h=["i'll help you plan the next one", "let's do it together this time", "i'll help you mark it out"],
  s=["plan it first, then build. wild idea.", "rebuild number three, skill issue"])

# ---- Griefing and theft --------------------------------------------------------------------------------------------

G("chest_stolen", "grief", "its chest being robbed", tags=["storage", "complaints", "home"],
  o=["someone took stuff from my chest", "my chest is half empty and i didn't do it", "my diamonds are missing from my chest",
     "someone went through my chests", "came home and my iron was gone"],
  q=[("what did they take?", ["my iron and my diamonds", "food, tools, half of everything"]),
     ("any idea who?", ["no, but i have a guess", "no idea. none."])],
  m=["they even left the chest open", "it was all sorted, too", "i was only gone a day", "they left me the dirt, thanks"],
  r=["same, someone took my gold once", "my chest got cleared out last week", "i lost a whole chest of iron once"],
  a=["hide your best stuff somewhere secret", "put your chests deep inside the house", "keep a hidden chest no one knows about"],
  j=["at least they left the dirt", "they must've really needed iron", "a thief with taste"],
  h=["i'll help you find who did it", "i can spare some iron till you're back up", "let's hide your stuff better together"],
  s=["should've hidden it", "leaving chests out? rookie"],
  w=["and they took the bread too", "and they closed the chest so i wouldn't notice", "and i saw someone with my exact pickaxe"])

G("crops_trampled", "grief", "its crops being trampled", tags=["farming", "complaints", "home"],
  sit=["a:mood_angry", "a:hungry"],
  o=["someone trampled my whole wheat field", "my crops got stomped flat", "someone jumped all over my farmland",
     "my carrots are all trampled", "came back and my farm was just dirt"],
  q=[("all of it?", ["every row", "most of it, yeah"]),
     ("people or animals?", ["footprints everywhere, so people", "probably both, honestly"])],
  m=["i'd just planted it", "it was almost ready too", "they jumped on every block", "now i have to start over"],
  r=["same, a horse trampled my farm once", "someone stomped my melons last week", "rabbits ruined my carrots once"],
  a=["put a fence around it", "a path around it keeps people off", "sink the farm a block lower"],
  j=["they were testing the soil", "it's a jumping field now", "you have a dirt garden now"],
  h=["i'll help you replant", "i've got seeds for you", "let's fence it in together"],
  s=["fence. fence it. that's it.", "no fence? rookie"])

G("pet_killed", "loss", "its pet dying", tags=["pets", "feelings", "complaints"], sit=[],
  o=["my dog died", "something killed my cat", "my wolf got killed while i was gone", "i lost my dog today",
     "came home and my cat was gone"],
  q=[("what happened?", ["a skeleton, i think", "i don't know, it was just gone"]),
     ("how long did you have it?", ["since my first week", "ages. ages."])],
  m=["it followed me everywhere", "i named it and everything", "i keep waiting for it to come back", "it used to sit by my bed"],
  r=["my dog died in lava once, i still think about it", "i lost my cat to a creeper", "my wolf fell off a cliff once"],
  a=["make the next one sit at home when you go out", "build it a little fenced yard", "you can tame another, i know it's not the same"],
  h=["i'll help you find another one", "let's make it a little grave", "come with me, there are wolves up north"],
  c=["it had a good life with you", "pets are family"])

G("door_broken", "mobs", "zombies breaking its door", needs=["!a:no_home"], tags=["mobs", "home", "complaints"],
  o=["a zombie broke my door down", "my door's gone, zombies smashed it", "zombies chewed right through my door",
     "i came home to no door and a zombie in my kitchen", "zombies got through my door last night"],
  q=[("was it a wooden door?", ["yes, oak", "yeah, of course"]),
     ("did anything get in?", ["they wandered around my house", "one stood in my bedroom"])],
  m=["i heard them banging all night", "one was just standing by my chests", "the whole place smells of rotten flesh now",
     "i have to make another door"],
  r=["same, zombies broke my door in a storm", "i came home to a zombie in my kitchen once", "my door's been broken three times"],
  a=["put a fence gate in front of your door", "use an iron door with a button", "light up around your door"],
  j=["they just wanted to come in", "zombies don't knock", "your house has an open door policy"],
  h=["i'll make you an iron door", "let's light around your door", "i've got a spare door for you"],
  s=["wood door. bold move.", "iron door, it's not hard"])

G("wall_dug", "grief", "someone digging through its wall", needs=["!a:no_home"],
  o=["someone dug a hole through my wall", "somebody tunneled right through my house", "there's a tunnel through my basement now",
     "someone took blocks out of my wall", "found a hole in my house that wasn't there yesterday"],
  q=[("a tunnel?", ["right through the side", "from the hill behind it"]),
     ("did they take anything?", ["blocks, just my blocks", "not that i can tell"])],
  m=["they didn't even patch it up", "who mines through a house", "it lets the rain in now", "i'm gonna have to redo that wall"],
  r=["same, someone strip mined under my house", "my wall got taken apart once", "someone dug under my garden once"],
  a=["mark your land with a fence", "put a sign up, people listen sometimes", "use stone, it's slower to dig"],
  j=["they just wanted a shortcut", "free window", "your house is a road now"],
  h=["i'll help you patch it up", "i'll bring some stone", "let's put a fence around your place"],
  s=["put up a sign lol", "should've built thicker walls"])

G("blocked_view", "grief", "someone building in front of its house", needs=["!a:no_home"], sit=["a:mood_angry"],
  o=["someone built right in front of my window", "my view is a wall now", "someone stuck a house right up against mine",
     "someone put a huge tower right by my house", "my lake view is gone"],
  q=[("how close?", ["two blocks from my wall", "touching, basically"]),
     ("did you say anything?", ["not yet", "i tried, they just kept building"])],
  m=["i picked that spot for the view", "now my house is in the shade", "it's not even a nice build", "my flowers get no sun now"],
  r=["same, someone built a dirt tower by my house", "my mountain view got blocked once", "someone put a farm right outside my door"],
  a=["put up a fence around your space", "talk to them, nicely", "build up, get your view back"],
  j=["now you have a wall view", "very cozy, very close", "the neighbour is your view now"],
  h=["i'll go talk to them with you", "let's build you a balcony up top", "i'll help you move if you want"],
  s=["build higher lol", "should've claimed more land"])

G("moved_stuff", "grief", "someone messing up its chests", tags=["storage", "complaints", "home"],
  o=["someone reorganized my chests", "my chests are all mixed up and i didn't do it", "somebody moved all my stuff around",
     "my tools are in the food chest now", "someone rearranged my whole storage"],
  q=[("is anything missing?", ["no, it's just all wrong", "i can't even tell"]),
     ("who would do that?", ["no clue", "someone with way too much time"])],
  m=["it took me days to sort it", "my signs mean nothing now", "the cobble's in with the diamonds", "i can't find my pick"],
  r=["same, someone dumped dirt in my chests once", "someone put cobble in my food chest", "i came home to everything in one chest"],
  a=["put signs on every chest", "keep the important stuff hidden", "keep one chest just for you, somewhere else"],
  j=["they were just being helpful", "a mystery organizer", "maybe it's better now lol"],
  h=["i'll help you sort it back", "let's put signs on everything", "i'll help you find your pick"],
  s=["just sort it again lol", "who cares, it's chests"])

G("bed_taken", "grief", "its bed being taken", needs=["!a:no_home"],
  o=["someone broke my bed", "my bed's gone, someone took it", "came home and my bed was missing", "someone slept in my bed and broke it",
     "no bed, no spawn point, great"],
  q=[("where did you sleep?", ["on the floor", "i didn't"]),
     ("why would someone take a bed?", ["no idea", "they needed one, i guess"])],
  m=["i had to sleep on the floor", "the phantoms were awful", "it was my red bed too", "i liked that bed"],
  r=["same, my bed got blown up once", "someone took my bed once, i woke up miles away", "i lost my bed to a creeper"],
  a=["keep a spare bed in a chest", "sleep somewhere hidden", "make a couple of beds, wool's easy"],
  j=["the bed ran away", "someone wanted a nap", "the floor builds character"],
  h=["i've got wool for a bed", "come stay at mine till then", "i'll help you shear some sheep"],
  s=["make another bed lol", "three wool. come on."])

G("gate_open", "grief", "its animals getting out", tags=["animals", "complaints", "farming"],
  o=["someone left my gate open and my animals got out", "my cows wandered off", "all my sheep are gone, gate was open",
     "someone opened my pen", "lost half my animals because of an open gate"],
  q=[("how many got out?", ["all of them", "most of them, maybe ten"]),
     ("did you find any?", ["two, maybe", "they're everywhere now"])],
  m=["i spent days breeding them", "one walked into the river", "i found a sheep on a hill", "i have no idea who did it"],
  r=["same, my pigs ran off once", "my horses got out last week", "someone let my chickens out"],
  a=["put a second gate on it", "a wall around the gate stops runners", "lead them back with wheat"],
  j=["they went on a trip", "the cows wanted to see the world", "free range now"],
  h=["i'll help you round them up", "i've got wheat to lure them", "let's build a better pen"],
  s=["gates close, you know", "should've checked"])

G("farm_harvested", "grief", "someone taking its harvest", tags=["farming", "complaints", "food"], sit=["a:hungry", "a:mood_angry"],
  o=["someone harvested my whole farm", "someone took all my wheat and didn't replant", "my farm got picked clean",
     "someone ate all my carrots", "my farm is empty and i didn't harvest it"],
  q=[("did they replant?", ["no, of course not", "not one seed"]),
     ("how much was there?", ["the whole field", "enough for a week"])],
  m=["i was going to make bread for everyone", "now i have to start from seeds", "they left the dirt bare", "it was my best crop yet"],
  r=["same, someone took my potatoes once", "my melons got taken last week", "someone stole my pumpkins"],
  a=["build the farm somewhere hidden", "fence it and light it", "plant extra and share some"],
  j=["somebody was really hungry", "a farm fairy, but evil", "they did the harvest for you, kind of"],
  h=["i'll help you replant", "i've got seeds and bone meal", "here, take some bread for now"],
  s=["guard your farm lol", "should've harvested first"])

# ---- Mobs ----------------------------------------------------------------------------------------------------------

G("phantoms", "mobs", "phantoms", sit=["time_night", "a:hurt"],
  o=["phantoms every single night", "the phantoms are back and i can't get any sleep", "phantoms dove at me all night",
     "phantoms keep swooping me", "i haven't slept in days and the phantoms know it"],
  q=[("why haven't you slept?", ["mobs near my bed", "no time, too much to do"]),
     ("how many?", ["three of them", "a whole flock"])],
  m=["they come out of nowhere", "i can't hit them", "one took me down to two hearts", "i hear them screeching in my sleep"],
  r=["same, phantoms got me on my roof", "i got swarmed by phantoms for not sleeping", "phantoms are why i sleep every night"],
  a=["just sleep, they stop coming", "fight them under a roof", "a bow helps, shoot them as they dive"],
  j=["they're just big angry bats", "you're their favourite", "nap time, or else"],
  h=["you can sleep at mine", "i'll make you a bow", "i'll help you clear the mobs near your bed"],
  s=["sleep. it's not hard.", "phantoms? just sleep lol"],
  w=["and one knocked me off my own roof", "and my bed's still got monsters nearby so i can't sleep"])

G("endermen_blocks", "mobs", "endermen taking its blocks",
  o=["an enderman took a block out of my wall", "endermen keep stealing my grass", "an enderman stole a piece of my house",
     "there's a hole in my roof, an enderman did it", "endermen keep moving my blocks around"],
  q=[("which block?", ["one right out of my wall", "a bit of my grass path"]),
     ("did you get it back?", ["it teleported away with it", "it put it down somewhere random"])],
  m=["it just looked at me and left", "i couldn't fight it", "now there's a gap in my wall", "it put dirt on my roof"],
  r=["same, an enderman took my melon once", "endermen ate my grass path", "one stole a block of my garden"],
  a=["build with stone, they can't pick it up", "light up, fewer of them spawn", "use blocks they can't carry around your yard"],
  j=["it just wanted a souvenir", "endermen are collectors", "it's decorating its own place"],
  h=["i'll help you patch it", "i've got blocks to fill the gap", "let's light up your yard"],
  s=["kill it, it drops pearls", "just hit it, it's one enderman"])

G("spiders_walls", "mobs", "spiders climbing its walls", sit=["time_night", "a:hurt"],
  o=["spiders keep climbing over my walls", "a spider got over my wall again", "spiders on my roof every night",
     "my walls are useless, spiders climb them", "woke up to a spider on my ceiling"],
  q=[("how high is your wall?", ["three blocks", "high enough for zombies"]),
     ("where do they come from?", ["the forest next door", "everywhere"])],
  m=["they just walk right up", "one jumped me in my garden", "i hear them clicking", "my wall doesn't stop anything"],
  r=["same, spiders got into my farm", "a spider dropped on me from my roof", "i hate the clicking more than anything"],
  a=["put a lip on top of the wall", "light up the forest edge", "fences with an overhang stop them"],
  j=["they're just really good climbers", "you built them a playground", "they like your house"],
  h=["i'll help you build an overhang", "let's light the forest edge", "i'll bring torches"],
  s=["overhang. basic stuff.", "spiders? swing at them lol"])

G("drowned", "mobs", "drowned with tridents", sit=["a:hurt", "time_night"],
  o=["drowned with tridents everywhere in the river", "a drowned threw a trident at me", "can't cross the river, drowned everywhere",
     "drowned keep sniping me from the water", "got hit by a trident at night by the beach"],
  q=[("where?", ["the river by my house", "the beach"]),
     ("how bad?", ["took me down to three hearts", "almost dead"])],
  m=["they come up at night", "i can't fight them in the water", "the trident hurt so much", "i have to cross every day"],
  r=["same, a drowned got me on a boat", "tridents hurt so much", "i stopped fishing at night because of them"],
  a=["build a bridge over the water", "cross in the day", "shield up when you see them"],
  j=["at least they're throwing free tridents", "they're just fishing", "the river has opinions about you"],
  h=["i'll help you build a bridge", "i'll make you a shield", "let's cross together"],
  s=["shield. it's not hard", "just fight them lol"])

G("creepers_door", "mobs", "creepers waiting at its door", sit=["time_morning", "a:mood_anxious"],
  o=["creepers wait at my door every morning", "there's always a creeper right outside", "i open my door and a creeper's there",
     "three creepers outside my house", "creepers love my front yard"],
  q=[("why your house?", ["it's dark around it", "no idea"]),
     ("did one blow up?", ["two did", "not yet"])],
  m=["i'm scared to open the door", "one blew up my flowers", "i hear the hiss in my sleep", "i'm out of torches"],
  r=["same, creepers camp my garden", "i had three at my door yesterday", "one waited at my door for a whole day"],
  a=["light it up all around", "a cat scares creepers off", "put a window by the door to peek out first"],
  j=["they're your fan club", "they want to come in for tea", "very green welcome committee"],
  h=["i'll bring torches", "let's get you a cat", "i'll come clear them in the morning"],
  s=["just hit them lol", "light your yard"])

G("skeletons_night", "mobs", "skeletons at night", sit=["time_night", "a:hurt"],
  o=["skeletons on my roof every night", "skeletons keep shooting at my windows", "a skeleton shot me through my window",
     "the skeletons out here are relentless", "skeletons won't leave me alone"],
  q=[("where are they coming from?", ["my flat roof, they spawn up there", "the dark field behind my house"]),
     ("can you get them?", ["they shoot first", "i don't have a bow"])],
  m=["i hear arrows hitting my walls", "one hit me in my own house", "they burn in the morning at least",
     "my walls are full of arrows"],
  r=["same, skeletons got on my roof too", "i fought a skeleton through my window", "my first house had a skeleton on top every night"],
  a=["light up your roof", "use a shield", "slabs on the roof stop spawns"],
  j=["free arrows", "they're practicing archery", "you have a skeleton problem, literally"],
  h=["i'll light your roof", "i'll make you a shield", "i'll come help fight tonight"],
  s=["get a bow lol", "skeletons? easy"])

G("zombies_door", "mobs", "zombies at its door", sit=["time_night", "a:mood_anxious"],
  o=["zombies banging on my door all night", "i can't sleep with the zombies groaning", "the zombies won't leave my door alone",
     "zombies outside every single night", "i hear zombies scratching at my door"],
  q=[("how many?", ["three or four", "a crowd"]),
     ("are they getting in?", ["not yet", "one almost did"])],
  m=["groan, groan, groan, all night", "i can't go out", "the door's going to give out", "i haven't slept"],
  r=["same, zombies kept me up for days", "a baby zombie got in my house once", "i hear them in my sleep now"],
  a=["light up around your house", "put a fence around your yard", "build a wall"],
  j=["they just want a sleepover", "your door's very popular", "zombie fan club"],
  h=["i'll help you build a wall", "i'll come fight them with you", "i'll bring torches"],
  s=["just kill them", "zombies. easy xp."],
  w=["and one of them had my old sword", "and they trampled my garden on the way"])

G("slimes", "mobs", "slimes",
  o=["slimes everywhere in my mine", "there's a slime spot under my house, i swear", "slimes keep bouncing into my tunnel",
     "i can't mine with all these slimes", "the swamp slimes are driving me nuts"],
  q=[("big ones?", ["big, medium, small, all of them", "the big ones hit hard"]),
     ("where?", ["my strip mine", "the swamp by my house"])],
  m=["they split and split", "the little ones are everywhere", "they bounce right into me", "i kill one and there are four"],
  r=["same, slimes spawn under my mine", "i spent an hour killing slimes once", "slimes ruined my swamp base"],
  a=["torches don't stop them down there, just fight", "wall off the area", "keep the slime balls, they're useful"],
  j=["free slime balls", "they're just bouncy friends", "bounce bounce"],
  h=["i'll help you clear them", "let's wall off that area", "i'll take the slime balls lol"],
  s=["slimes are easy", "just swing"])

G("baby_zombies", "mobs", "baby zombies", sit=["a:hurt"],
  o=["baby zombies are the worst", "a baby zombie nearly killed me", "i can't hit the baby zombies",
     "a baby zombie ran under my legs and wrecked me", "baby zombie on a chicken. a chicken."],
  q=[("how fast was it?", ["so fast", "i couldn't even see it"]),
     ("did you get it?", ["eventually", "it got me first"])],
  m=["it's so small and so fast", "i kept missing", "it ran right at me", "it ran circles around me"],
  r=["same, a baby zombie chased me home", "baby zombies are the scariest mob", "one ran under my door once"],
  a=["back into a corner and swing low", "fight them in a doorway", "use a sword, not your fists"],
  j=["tiny but angry", "it's just a toddler with a temper", "chicken jockey, legendary"],
  h=["i'll come fight them with you", "i'll make you a sword", "i'll cover you next time"],
  s=["aim lower lol", "a baby zombie got you? ok"])

G("silverfish", "mobs", "silverfish",
  o=["silverfish came out of the stone and swarmed me", "mined a block and it was a silverfish", "silverfish everywhere in the mountain",
     "a silverfish nest in my mine", "silverfish nearly got me"],
  q=[("how many?", ["they kept coming", "a dozen at least"]),
     ("where?", ["in the mountains", "in my mine"])],
  m=["every block was a silverfish", "they keep waking each other up", "i ran away", "they're like rats"],
  r=["same, silverfish chased me out of a mountain", "silverfish scare me", "i found a stronghold full of them"],
  a=["don't fight them standing on stone", "bury them with gravel", "back off and fight in the open"],
  j=["the stone had bugs", "rats of the underground", "pest control needed"],
  h=["i'll help you clear them", "let's mine somewhere else", "i'll fight them with you"],
  s=["just hit them", "silverfish? lol"])

G("witches_near", "mobs", "witches near its house",
  o=["witches keep spawning near my house", "a witch threw a potion at my window", "the witch hut by my house is a nightmare",
     "witches keep poisoning me", "a witch with slowness potions ruined my day"],
  q=[("how close?", ["right by the swamp", "too close"]),
     ("can you fight them?", ["they keep healing", "i'm trying"])],
  m=["they throw potions everywhere", "i drank milk all day", "the laugh is so annoying", "i can't farm in peace"],
  r=["same, there's a witch hut by my place", "witches ruined my swamp trip", "i hate the laugh"],
  a=["rush them fast", "carry milk", "shoot them from far"],
  j=["she's just making soup", "potion delivery", "the witch is your neighbour now"],
  h=["i'll help you fight them", "i'll bring milk", "let's clear the hut"],
  s=["rush them", "they're easy, swing"])

G("pillagers", "mobs", "pillagers", tags=["mobs", "fighting", "complaints"],
  o=["pillagers chased me across the plains", "a pillager patrol found me", "pillagers keep shooting at me",
     "i got shot by a crossbow patrol", "pillagers by my farm again"],
  q=[("how many?", ["four, with a captain", "a whole group"]),
     ("did you fight them?", ["i ran", "i tried, the crossbows hurt"])],
  m=["the crossbow bolts hurt so bad", "they follow you forever", "the captain had a banner", "i hid in a cave"],
  r=["same, a patrol chased me home", "pillagers shot my horse once", "i got cornered by a patrol"],
  a=["shield up against crossbows", "fight them one at a time", "use the trees for cover"],
  j=["they just wanted to say hi", "very rude neighbours", "banner gang"],
  h=["i'll come fight them with you", "i'll make you a shield", "let's watch your farm together"],
  s=["shield up lol", "patrols are easy"],
  sit=["a:hurt"])

G("rabbits", "mobs", "rabbits eating its carrots", tags=["animals", "complaints", "farming"], sit=["a:hungry"],
  o=["rabbits keep eating my carrots", "the rabbits ate my whole carrot patch", "a rabbit's living in my farm now",
     "every morning there are fewer carrots", "rabbits. in my garden. again."],
  q=[("rabbits eat carrots?", ["right off the plant", "they eat them down to nothing"]),
     ("how many rabbits?", ["three that i've seen", "a whole family"])],
  m=["they're so fast", "they get in through the gate", "i chased one for ages", "it looked right at me and kept eating"],
  r=["rabbits ate my carrot farm too", "same, i lost a whole row to a rabbit", "a rabbit lived in my garden for a week once"],
  a=["fence it in properly, no gaps", "a dog chases them off", "plant potatoes, they leave those alone"],
  j=["they're just hungry", "cute thieves", "the rabbits are farming you"],
  h=["i'll help you fence it", "i've got carrots for you", "let's chase them off together"],
  s=["fence. fence it.", "lost to a bunny lol"])

G("fox", "mobs", "a fox stealing its things", tags=["animals", "complaints", "jokes"],
  o=["a fox stole my food", "a fox ran off with my sword", "foxes keep taking my stuff", "a fox took my emerald and ran",
     "woke up and a fox was holding my bread"],
  q=[("did you catch it?", ["no, too fast", "almost, it went into the bushes"]),
     ("where did it go?", ["into the bushes", "off into the woods with it"])],
  m=["it looked at me and ran", "it was so fast", "they come at night", "it's in the woods somewhere with my stuff"],
  r=["same, a fox took my emerald", "a fox stole my bread once", "foxes take everything from me"],
  a=["foxes drop what they hold if you get them", "keep stuff in chests, not on the ground", "a dog keeps them away"],
  j=["the fox has style", "cute thief", "it's got a sword now, be scared"],
  h=["i'll help you catch it", "i'll give you some bread", "let's get a dog"],
  s=["catch it lol", "lost to a fox, couldn't be me"])

# ---- Bad luck ------------------------------------------------------------------------------------------------------

G("no_diamonds", "luck", "no diamonds", tags=["mining", "diamonds", "complaints"],
  o=["mined for hours and not one diamond", "three hours of strip mining, zero diamonds", "still no diamonds and i'm so deep",
     "everyone finds diamonds but me", "i've seen so much stone and no diamonds"],
  q=[("how deep are you mining?", ["really deep, near the bottom", "where everyone says to"]),
     ("how long?", ["all day", "three days now"])],
  m=["just coal and more coal", "i found redstone, so much redstone", "my pick's almost broken", "i can't feel my arms"],
  r=["same, i went a week without diamonds", "i mined for two days and found one", "my first diamond took forever"],
  a=["try caves, you see more ore", "mine deeper, near the bottom", "branch mine, two blocks between tunnels"],
  j=["the diamonds are hiding from you", "you're collecting cobble professionally now", "diamonds are a myth"],
  h=["come mining with me tomorrow", "i'll show you a good cave", "i can lend you a better pick"],
  s=["you're mining wrong", "diamonds aren't hard, lol"],
  w=["and then i found one and it fell in lava", "and someone found diamonds right next to my tunnel"])

G("enchant_junk", "luck", "a junk enchantment", tags=["enchanting", "complaints", "gear"],
  o=["enchanted my pick and got bane of arthropods", "thirty levels for unbreaking one", "enchanting gave me junk again",
     "my book was just luck of the sea", "spent all my levels on a garbage enchant"],
  q=[("what did you want?", ["efficiency or fortune", "anything useful"]),
     ("how many levels?", ["thirty", "all of them"])],
  m=["i saved those levels for days", "the lapis was hard to find too", "bane of arthropods on a pick", "i could cry"],
  r=["same, i got unbreaking one on a level thirty", "i got smite on my sword, fine i guess", "i got aqua affinity on a helmet, useless"],
  a=["enchant books, then use an anvil", "do cheap enchants first to change the options", "grindstone it and try again"],
  j=["at least spiders fear your pickaxe now", "very powerful against fish", "your pick is spider-proof"],
  h=["i've got spare lapis", "i'll help you farm levels", "let's grind xp together"],
  s=["reroll it lol", "enchanting is rng, cope"])

G("rod_broke", "luck", "its fishing rod breaking", tags=["fishing", "tools", "complaints"],
  o=["my fishing rod broke right when i hooked something", "fishing rod snapped mid-catch", "fished for an hour, rod broke",
     "my rod broke and i caught nothing", "my lucky fishing rod finally broke"],
  q=[("what were you fishing for?", ["anything good", "a saddle, honestly"]),
     ("what did you catch?", ["boots. old boots.", "three fish and a stick"])],
  m=["it was an enchanted rod too", "the bobber went under and snap", "i caught boots four times", "i was so close"],
  r=["same, my rod broke on a treasure catch", "i caught nothing but sticks once", "fishing is cursed"],
  a=["carry two rods", "keep string on you to make another", "an anvil can fix it"],
  j=["the fish won", "the fish union is strong", "the boots were a gift"],
  h=["i've got string for a new rod", "let's fish together tomorrow", "i'll make you a rod"],
  s=["fish better lol", "carry two"])

G("axe_broke", "luck", "its axe breaking mid-tree", tags=["trees", "tools", "complaints"],
  o=["my axe broke mid-tree", "axe snapped with half a tree left", "my axe broke and i was miles from home",
     "broke my axe on a giant spruce", "my axe broke at the top of a tree"],
  q=[("how much was left?", ["the whole top", "half the tree"]),
     ("didn't you have another?", ["nope", "it was at home"])],
  m=["i had to punch the rest", "the leaves were just floating there", "i was up on a pillar too", "i hate punching logs"],
  r=["same, my axe broke on a dark oak", "i punched a whole tree once", "my axe always breaks at the worst time"],
  a=["carry a spare axe", "check how worn it is first", "make a stone one on the spot"],
  j=["the tree fought back", "your fists are axes now", "the tree won this round"],
  h=["i've got a spare axe", "i'll help you chop the rest", "let's make you a new one"],
  s=["check your durability", "carry a spare lol"])

G("pick_broke", "luck", "its pickaxe breaking deep down", tags=["mining", "tools", "complaints"],
  o=["my pickaxe broke at the bottom of the mine", "pick broke and i'm deep underground", "my pick snapped right before diamonds",
     "pick broke and i had no spare", "my pickaxe broke and i had to dig out by hand"],
  q=[("how deep?", ["really deep", "near the bottom"]),
     ("no spare?", ["no", "i forgot it"])],
  m=["i could see diamonds right there", "i had to punch stone", "i had no sticks", "it took forever to get out"],
  r=["same, my pick broke right before diamonds", "i punched my way out of a mine once", "my pick broke in a ravine"],
  a=["carry sticks and a crafting table", "bring two picks", "check how worn it is before going deep"],
  j=["punching stone builds character", "your hands are pickaxes now", "the stone won"],
  h=["i've got a spare pick", "i'll go back down with you", "i'll make you a new one"],
  s=["carry a spare", "check your durability lol"],
  w=["and then i lost my way out in the dark", "and the diamonds were gone when i came back"])

G("no_iron", "luck", "no iron", tags=["mining", "ore", "complaints"],
  o=["can't find any iron", "searched all day, no iron", "i need iron for a bucket and there's none",
     "every cave i find has no iron", "i'm stuck with stone tools, no iron"],
  q=[("where have you looked?", ["every cave around here", "the mountains"]),
     ("how much do you need?", ["a lot", "just enough for a bucket"])],
  m=["just coal everywhere", "i found copper, so much copper", "i'm sick of stone", "i need armor"],
  r=["same, i went days without iron", "i once mined for hours for one iron", "copper everywhere, no iron"],
  a=["mountains have lots of iron", "check caves, not tunnels", "zombies drop iron sometimes"],
  j=["the iron is hiding", "copper's just iron that gave up", "iron is a myth"],
  h=["i've got iron to spare", "come mining with me", "i'll show you a good cave"],
  s=["iron is everywhere", "look harder lol"])

G("no_breed", "luck", "animals that won't breed", tags=["animals", "farming", "complaints"], sit=["a:hungry"],
  o=["my cows won't breed", "my animals just won't breed", "i've been trying to breed sheep all day",
     "my pigs just stare at me", "two cows and they won't do anything"],
  q=[("what are you feeding them?", ["wheat", "carrots"]),
     ("how many do you have?", ["two", "just one, honestly"])],
  m=["i really need the meat", "they just stare at me", "i have one cow and a dream", "the plains are empty"],
  r=["same, i spent a day chasing two cows", "i couldn't find sheep for a week", "my pigs won't breed either"],
  a=["wheat for cows and sheep, carrots for pigs", "you need two, then breed them", "find a village, they have animals"],
  j=["the cows aren't in the mood", "give them some privacy", "the animals are on strike"],
  h=["i've got wheat for you", "i'll help you find more cows", "let's build a pen together"],
  s=["you need two lol", "feed them wheat"])

G("crops_slow", "luck", "crops that won't grow", tags=["farming", "plants", "complaints"], sit=["a:hungry"],
  o=["my crops won't grow", "my wheat's been seeds for days", "nothing grows on my farm", "my carrots won't come up",
     "my farm's taking forever"],
  q=[("is it watered?", ["yes", "i think so"]),
     ("is it lit?", ["no", "a little"])],
  m=["i watch it every morning", "it's still green", "i'm so hungry", "it's been days"],
  r=["same, my farm took forever", "i forgot to water my farm once, lol", "my carrots took a week"],
  a=["keep water within four blocks of every crop", "light it up, crops need light", "bone meal the rows you need first"],
  j=["watching it doesn't help", "the crops are shy", "they grow when you're not looking"],
  h=["i've got bone meal", "i'll help you set up water", "i'll give you some bread for now"],
  s=["water it lol", "light it up"])

G("sapling", "luck", "saplings that won't grow", tags=["trees", "plants", "complaints"],
  o=["my sapling won't grow", "my saplings just sit there", "i've planted ten saplings and nothing",
     "my tree farm's a joke", "planted a tree days ago, still a stick"],
  q=[("how much room do they have?", ["plenty, i think", "right up against my wall"]),
     ("how long's it been?", ["days", "since before the last rain"])],
  m=["it's been a stick for three days", "i need the wood for my roof", "it's got light, it's got dirt, nothing",
     "i check it every morning, still a stick"],
  r=["same, saplings took forever for me", "i never got an apple for a week", "my oak never grew"],
  a=["give them room, nothing above for a few blocks", "put a torch near them, they need light",
     "bone meal makes them pop right up"],
  j=["the tree is shy", "plants have feelings", "it's a bonsai now"],
  h=["i've got bone meal", "i'll give you logs", "i'll help plant more"],
  s=["give them room lol", "bone meal exists"])

G("gravel_flint", "luck", "gravel without flint", tags=["mining", "crafting", "complaints"],
  o=["broke so much gravel, no flint", "i need flint and the gravel won't give it", "fifty gravel, one flint",
     "i need arrows and no flint", "gravel hates me"],
  q=[("how much gravel?", ["a stack", "more than a stack"]),
     ("what do you need it for?", ["arrows", "flint and steel"])],
  m=["i keep placing it back", "my shovel's almost broken", "the gravel's everywhere", "my inventory's full of gravel"],
  r=["same, i dug a whole gravel pit once", "flint took forever for me", "gravel never gives me flint"],
  a=["place it and dig it again", "fortune on a shovel helps", "trade with a fletcher"],
  j=["the gravel's keeping the flint", "flint's on a trip", "the gravel's in a mood"],
  h=["i've got flint to spare", "i'll help you dig", "i'll trade you some"],
  s=["just dig more", "use fortune lol"])

G("dropped_lava", "luck", "dropping its gear into lava", tags=["gear", "complaints", "danger"],
  o=["dropped my sword into lava", "my diamonds fell in lava by accident", "i threw my pick into lava",
     "my iron fell into lava", "dropped my whole stack of gold in lava"],
  q=[("how?", ["i dropped it by accident", "it slipped off the edge"]),
     ("can you get it back?", ["lava doesn't give things back", "no, it's gone"])],
  m=["it just sizzled", "i watched it burn", "i reached for it", "it's gone"],
  r=["same, i dropped my diamonds in lava once", "i threw my bow in lava", "lava ate my gold once"],
  a=["stay away from lava with stuff out", "put a block between you and it", "sneak near lava"],
  j=["the lava wanted a snack", "a gift to the lava", "lava says thanks"],
  h=["i've got a spare sword", "i'll lend you some iron", "i'll help you mine more"],
  s=["how do you drop things in lava", "sneak near lava, it's not hard"])

G("item_fell", "luck", "dropping its stuff off a cliff", tags=["gear", "complaints", "exploring"],
  o=["dropped my pick into a ravine", "my stuff fell off a cliff", "i dropped my sword into the ocean", "my bow fell down a hole",
     "dropped my map into a waterfall"],
  q=[("can you get it?", ["it's at the bottom of a ravine", "no"]),
     ("how far down?", ["all the way to the bottom", "too far to even see"])],
  m=["it just kept falling", "it floated away", "i watched it go", "i swear i was holding it"],
  r=["same, my sword fell in the ocean", "my map floated away once", "i dropped my bow in a ravine"],
  a=["don't stand on edges with stuff out", "sneak on edges", "pillar down to get it"],
  j=["it went on an adventure", "it's exploring", "the ravine wanted it"],
  h=["i'll help you get it", "i'll lend you mine", "i'll go down with you"],
  s=["sneak on edges", "how do you drop things"])

G("anvil", "luck", "an anvil saying too expensive", tags=["gear", "crafting", "complaints"],
  o=["the anvil says too expensive", "can't fix my pick, too expensive", "the anvil broke after two uses",
     "too expensive on my favourite sword", "i saved levels and the anvil said no"],
  q=[("how many times have you fixed it?", ["too many, apparently", "like six"]),
     ("how many levels?", ["forty", "too many"])],
  m=["it's my favourite pick", "i've fixed it so many times", "the anvil broke too", "i can't use it anymore"],
  r=["same, my sword's too expensive now", "my anvil broke on me too", "i lost a pick to too expensive"],
  a=["combine the books first", "use mending", "make a new one and enchant it again"],
  j=["the anvil has standards", "your pick is retired", "the anvil said no"],
  h=["i'll help you farm xp", "i've got iron for a new anvil", "let's make a new pick"],
  s=["combine books first lol", "plan your enchants"])

G("fishing_junk", "luck", "fishing up junk", tags=["fishing", "complaints", "boredom"],
  o=["fished all day, caught boots", "caught nothing but junk", "my fishing trip was all sticks and leather",
     "caught the same old boots three times", "an hour of fishing, one fish"],
  q=[("where were you fishing?", ["the river", "the ocean"]),
     ("did you catch anything good?", ["a bowl", "a bottle"])],
  m=["i caught a stick, two boots, and a bowl", "the fish just ignore me", "i'm so bored", "i wanted a saddle"],
  r=["same, i caught boots like ten times", "i once fished all day for nothing", "fishing hates me too"],
  a=["fish in the rain, it's faster", "use a lure book", "fish in open water"],
  j=["you could open a boot shop", "fashion fishing", "the river's giving you clothes"],
  h=["let's fish together", "i'll show you my spot", "i've got a lure book"],
  s=["fish better lol", "open water, rookie"])

# ---- Tired and overworked ------------------------------------------------------------------------------------------

G("gathering", "work", "gathering and never building", tags=["complaints", "boredom", "building"], sit=["a:mood_bored"],
  o=["all i do is gather, never build", "chop wood, mine stone, repeat, forever", "i never get to build anything",
     "i've been gathering for days straight", "i haven't placed a single block for fun in a week"],
  q=[("what are you gathering for?", ["a house i'll never start at this rate", "everything, apparently"]),
     ("how much do you have now?", ["four chests of cobble and no house", "not enough. it's never enough."])],
  m=["my chests are full and i'm still going", "i have a whole house planned in my head", "it's always one more stack",
     "i dream about chopping logs now"],
  r=["same, i gathered for a month once and built a shed", "i never build either, i just collect",
     "i have nine chests of stone and a dirt hut lol"],
  a=["start building with what you have, gather as you go", "set a number, hit it, then stop gathering",
     "build something small first, just for fun"],
  j=["you're a professional gatherer now", "gathering is a lifestyle", "the build was the gathering all along"],
  h=["i'll help you gather so you can build", "i'll come chop with you, double speed", "let's start your build tomorrow, together"],
  s=["just build, lol", "gather faster then"])

G("chores", "work", "town chores", needs=["a:in_town"], tags=["town", "complaints", "boredom"], sit=["a:mood_frustrated"],
  o=["town chores all day, every day", "i'm always doing town stuff", "i'm the one who does all the chores here",
     "i've fixed every fence in town", "replanted the town farm again. alone."],
  q=[("what chores?", ["fences, farms, roads, torches", "anything nobody else wants to do"]),
     ("doesn't anyone help?", ["nope. not once.", "they help when the leader's watching"])],
  m=["i never get time for my own stuff", "my own house is still half done", "nobody even says thanks",
     "i relit every lamp on the road last night"],
  r=["same, i fixed the roads alone last week", "i'm the town farmer, apparently", "i got stuck with the walls for days"],
  a=["ask for help, out loud, in front of everyone", "say no sometimes, the fences won't die",
     "do your own stuff first, then town stuff"],
  j=["you're the town's mom", "town hero, unpaid", "chore champion of {a.town}", "chore champion of the whole town"],
  h=["i'll do the farm for you tomorrow", "let's split the list", "i'll take the fences, you take a nap"],
  s=["chores are easy lol", "just do them faster"])

G("guard_duty", "work", "guard duty", needs=["a:in_town"], tags=["town", "complaints", "night"],
  sit=["time_night", "a:mood_bored"],
  o=["guard duty all night again", "i'm stuck guarding the gate", "guards never get any sleep", "i've been on guard for days",
     "guarding the town is so boring"],
  q=[("how long is your shift?", ["all night. every night.", "dusk till the zombies burn"]),
     ("anything happen?", ["one zombie. one.", "nothing, ever, that's the worst part"])],
  m=["i just stand at the gate staring at grass", "i can't sleep, so the phantoms come", "nobody even says thanks",
     "i counted every torch on the wall twice"],
  r=["same, i guarded the walls all last week", "guard duty nearly broke me", "i nodded off on guard once, never again"],
  a=["take turns with someone, half a night each", "ask the leader for a break", "light it all up so there's less to watch"],
  j=["you're the town wall", "hero of the night shift", "zombie bouncer"],
  h=["i'll take a shift tonight", "i'll stand guard with you, it's less boring", "let's split the nights"],
  s=["guarding builds character", "you get to fight, what's the problem"])

G("taxes", "work", "taxes", needs=["a:in_town"], tags=["town", "politics", "complaints"], sit=["a:taxed", "a:tax_high"],
  o=["the taxes here are too high", "i pay half my iron in taxes", "taxes again, really", "the town takes too much",
     "i work all day just to pay taxes"],
  q=[("how much do they take?", ["way too much", "a big cut of everything i bring in"]),
     ("what's it even for?", ["the walls, supposedly", "no idea, nobody tells us"])],
  m=["i can barely keep anything", "my chest is emptier every week", "the leader's chest looks full though",
     "i mined all day and gave half of it away"],
  r=["same, i paid diamonds in taxes once", "taxes are the worst part of town", "i grumble every tax day too"],
  a=["bring it up at the next election", "ask what it's for, out loud", "talk to the others, you're not the only one"],
  j=["the leader needs a new hat", "taxes and creepers, the two sure things", "the town chest eats well"],
  h=["i'll help you mine some extra", "let's talk to the leader together", "i'll back you at the vote"],
  s=["earn more lol", "taxes? just mine more"])

G("farming_work", "work", "farming all day", tags=["farming", "complaints", "boredom"], sit=["a:mood_bored"],
  o=["farming all day and i'm sick of wheat", "i've harvested so much wheat", "the farm never stops",
     "replant, harvest, replant, i'm losing it", "i'm the only one who farms around here"],
  q=[("how big is the farm?", ["nine rows and growing", "too big for one person"]),
     ("who eats it all?", ["everyone", "everyone but me, somehow"])],
  m=["i see wheat when i close my eyes", "i trampled my own farmland jumping, that's how tired i am",
     "nobody helps with the replanting", "the second it's done the first row is ready again"],
  r=["same, i farmed for days once", "wheat is my nightmare too", "i'm so sick of carrots, honestly"],
  a=["put water channels in, it grows faster", "use bone meal on the slow rows", "plant less, rest more"],
  j=["you're a wheat person now", "bread empire", "wheat fever"],
  h=["i'll help you harvest tomorrow", "i'll take half the rows", "let's do it together, faster that way"],
  s=["farming is easy mode lol", "just harvest faster"])

G("mining_for_others", "work", "mining for everyone", tags=["mining", "complaints", "trade"], sit=["a:mood_frustrated"],
  o=["everyone wants my iron", "i mine and everyone asks for it", "i'm everyone's miner",
     "i mine all day for other people", "nobody mines but me, apparently"],
  q=[("who keeps asking?", ["everyone", "half the people i know"]),
     ("do they pay you?", ["sometimes a loaf of bread", "never. not once."])],
  m=["i keep nothing for myself", "my pick's always half broken", "i never get thanks",
     "i came up with a stack of iron and left with three"],
  r=["same, i'm everyone's farmer", "i mined for everyone last week, kept nothing", "i never keep my iron either"],
  a=["trade it, don't give it away", "say no sometimes, they'll live", "teach them to mine, then they'll stop asking"],
  j=["you're the iron bank", "iron dealer", "the mine is your office now"],
  h=["i'll mine for you for once", "let's split the next haul, fair", "i'll come mine with you"],
  s=["mine faster", "mining's easy, just do it"])

G("no_rest", "work", "never resting", tags=["sleep", "complaints", "feelings"], sit=["time_evening"],
  o=["i haven't rested in days", "no time to sleep", "i'm so tired", "i never get a break", "i'm running on nothing"],
  q=[("what's keeping you up?", ["work. always work.", "everything, all at once"]),
     ("when did you last sleep?", ["days ago", "i honestly don't remember"])],
  m=["i walked into a tree today", "the phantoms have started circling", "i put coal in the chest instead of the furnace",
     "i keep forgetting what i came for"],
  r=["same, i went days without sleep once", "i nodded off standing up once", "the phantoms got me for that too"],
  a=["sleep tonight, seriously", "take tomorrow off, the world will wait", "do less, it's ok"],
  j=["the phantoms are coming for you", "you're a zombie now", "sleep is for the weak, apparently"],
  h=["i'll take some of your work", "come rest at mine tonight", "i'll cover for you tomorrow"],
  s=["sleep is a skill too", "skill issue, sleep"])

G("inventory", "work", "a full inventory", tags=["storage", "complaints", "tools"],
  o=["my inventory's full again", "i carry too much stuff", "full inventory every single trip",
     "i have to throw stuff away every time", "my pockets are always full of junk"],
  q=[("full of what?", ["cobble, mostly", "dirt, seeds, and a lot of andesite"]),
     ("why carry so much?", ["i might need it", "i don't know, i can't stop"])],
  m=["i throw away cobble every trip", "i keep the dirt for some reason", "i dug for ages to find my torches",
     "i'm basically a walking chest"],
  r=["same, i carry way too much dirt", "i'm always throwing stuff away", "my pockets are a mess too"],
  a=["leave the junk, you'll never miss cobble", "bring a chest and drop it halfway", "go home more often"],
  j=["you're a walking chest", "a dirt museum", "the hoarder life"],
  h=["i'll carry some home for you", "let's make you more chests", "i'll help you sort it"],
  s=["drop the dirt lol", "you don't need it"])

G("smelting", "work", "smelting forever", tags=["crafting", "complaints", "boredom"], sit=["a:mood_bored"],
  o=["i've been smelting all day", "the furnace takes forever", "waiting on my furnace again", "so much ore, one furnace",
     "i need more furnaces"],
  q=[("how many furnaces?", ["one", "two, and one's out of fuel"]),
     ("how much ore?", ["stacks of it", "three stacks of raw iron"])],
  m=["i'm out of coal now too", "i just stand there watching it", "one ingot at a time", "i could be mining right now"],
  r=["same, i smelted for hours once", "i only have one furnace too", "smelting is so boring"],
  a=["build more furnaces, cobble's cheap", "a blast furnace does ore twice as fast", "smelt before you go, collect after"],
  j=["furnace watching is a hobby", "fire is relaxing", "you're the furnace whisperer"],
  h=["i'll make you a few furnaces", "i've got coal to spare", "let's smelt together"],
  s=["build more furnaces lol", "one furnace? come on"])

G("chopping", "work", "chopping trees all day", tags=["trees", "complaints", "boredom"], sit=["a:mood_bored"],
  o=["chopped trees all day", "i'm sick of chopping", "so many trees, so many logs", "my arms hurt from chopping",
     "i need so much wood"],
  q=[("how many logs?", ["stacks", "not enough, somehow"]),
     ("what's it all for?", ["a house", "everything needs wood"])],
  m=["the floating leaves never end", "my axe is almost broken", "i hate spruce, too tall",
     "i replanted every sapling and still need more"],
  r=["same, i chopped all day yesterday", "trees are endless", "i hate chopping more than mining"],
  a=["plant a tree farm by your house", "big spruce give way more logs", "get a better axe"],
  j=["you're a lumberjack now", "trees fear you", "it's the log life now"],
  h=["i'll chop with you", "i've got logs to spare", "let's plant a tree farm together"],
  s=["chop faster", "get a better axe lol"])

# ---- Loneliness ----------------------------------------------------------------------------------------------------

G("no_one", "lonely", "being alone", tags=["feelings", "friends", "complaints"], sit=["a:mood_bored"],
  o=["i don't really have anyone to talk to", "some days i don't talk to anyone", "it's lonely out here",
     "feels like i'm always alone", "nobody's around most days"],
  q=[("where do you live?", ["out on my own", "far from everyone"]),
     ("how long has it been like this?", ["a while", "ages"])],
  m=["i talk to my cows", "i walk to the village just to see someone", "the nights are long and quiet",
     "i miss having people around"],
  r=["i felt like that for a long time too", "same, i used to talk to my chickens", "being alone is hard, i know"],
  a=["move closer to a town", "visit people more, they'll visit back", "come by the town more often"],
  j=["the cows are great listeners", "you have me now, lucky you", "talk to the chickens, they're chatty"],
  h=["come hang out with me more", "i'll come visit you", "let's go mining together tomorrow"])

G("friends_busy", "lonely", "friends always busy", needs=["!strangers"], tags=["friends", "feelings", "complaints"],
  o=["everyone's always too busy for me", "my friends never have time", "i keep asking people and they're busy",
     "nobody wants to do stuff with me", "everyone's always off doing their own thing"],
  q=[("who's busy?", ["everyone i know", "my friends, all of them"]),
     ("what did you want to do?", ["just mine together", "anything, honestly"])],
  m=["i asked three people today", "they all said maybe later", "later never comes", "i end up doing stuff alone"],
  r=["same, i feel like that a lot", "people get busy, it's not you", "i've been there, it stings"],
  a=["ask for something specific, like a mining trip", "join a group", "find someone who wants the same stuff"],
  j=["i'm free, lucky you", "your friends are just very important", "busy busy busy"],
  h=["i'll do stuff with you", "let's go mining tomorrow", "i'm free tonight"])

G("no_visits", "lonely", "nobody visiting", needs=["!a:no_home"], tags=["home", "friends", "feelings"],
  o=["nobody ever visits my house", "i made a guest room and nobody came", "no one's been to my place",
     "i keep inviting people and no one comes", "my house is so quiet"],
  q=[("did you invite anyone?", ["everyone", "a few people, they said sure"]),
     ("how far out is it?", ["kind of far", "past the river"])],
  m=["i even made a path", "i put a guest bed in", "i cooked and everything", "it's just me and the chests"],
  r=["same, nobody visits me", "i made a guest room once too", "people never come out my way"],
  a=["build closer to the town", "make a road to your place", "invite people for something, like a feast"],
  j=["the zombies visit", "your house is exclusive", "invitation only, very fancy"],
  h=["i'll come visit", "i'll bring food", "i'll come by tomorrow"])

G("new_here", "lonely", "being new around here", tags=["friends", "feelings", "town"],
  o=["i'm new around here and i don't know anyone", "everyone here already knows each other", "it's hard being the new one",
     "nobody talks to me here", "i'm still a stranger to everyone"],
  q=[("how long have you been here?", ["a few days", "a week"]),
     ("have you met anyone?", ["a couple", "barely"])],
  m=["everyone's already in groups", "i don't know where anything is", "they all have their own jokes",
     "i wave and nobody waves back"],
  r=["i was new here once too", "it took me ages to make friends", "everyone feels that at first"],
  a=["help people out, they remember", "ask to join stuff", "say yes when someone asks you along"],
  j=["you know me now", "welcome to the club", "the new one, mysterious"],
  h=["i'll introduce you to people", "come with me tomorrow", "i'll show you around"])

G("quiet_nights", "lonely", "lonely nights", tags=["night", "feelings", "friends"], sit=["time_night", "time_evening"],
  o=["the nights are so lonely", "nights are the worst when you're alone", "i sit in my house alone every night",
     "at night it's just me and the zombies", "nobody to talk to at night"],
  q=[("what do you do at night?", ["sit and sort chests", "listen to the zombies"]),
     ("do you sleep?", ["eventually", "not much"])],
  m=["it's so quiet", "i hear the zombies groan", "i talk to myself", "i miss company"],
  r=["same, nights are hard alone", "i used to feel that every night", "nights are long, i know"],
  a=["sleep through them", "find a roommate", "stay in town at night"],
  j=["the zombies are company", "night chats with zombies", "you have the moon"],
  h=["come stay at mine", "we can sit by the fire together", "i'll come by tonight"])

G("ignored", "lonely", "being ignored", needs=["!strangers"], tags=["feelings", "friends", "complaints"],
  o=["i feel like nobody listens to me", "people talk right past me", "i say stuff and nobody answers", "i feel invisible",
     "nobody listens to anything i say"],
  q=[("who?", ["everyone", "the people around here"]),
     ("what happened?", ["i said hi, nothing", "i tried to join in and nobody noticed"])],
  m=["i said something and they just kept going", "i don't know what i did", "it's like i'm not there",
     "even the villagers ignore me"],
  r=["same, i feel that sometimes", "people get caught up, it's not you", "i've been there"],
  a=["talk to one person at a time", "join in, don't wait to be asked", "find people who listen"],
  j=["i hear you, loud and clear", "the villagers ignore everyone", "you're a ghost now"],
  h=["i'm listening", "talk to me any time", "i'll hang out with you"])

# ---- Homesick ------------------------------------------------------------------------------------------------------

G("old_base", "homesick", "its old base",
  o=["i miss my old base", "i keep thinking about my old house", "my old place was so much better", "i miss my first base",
     "i moved and i miss it"],
  q=[("where was it?", ["far east, by a river", "up on a hill over a lake"]),
     ("why did you move?", ["needed more space", "monsters kept coming"])],
  m=["it had the best view of the sunrise", "i built every block of it by hand", "it was small but so cozy",
     "this place doesn't feel the same"],
  r=["same, i miss my first hut", "my old base is a ruin now", "i still visit mine sometimes"],
  a=["build something like it here", "go visit it, it's still there", "bring something from it here, like the bed"],
  j=["the old place misses you too", "it's still there, haunted by you", "you're a sentimental one"],
  h=["we could visit it together", "i'll help you build something like it", "let's make this place feel like home"])

G("base_gone", "homesick", "its old base being destroyed", tags=["home", "memories", "complaints"], sit=["a:no_home"],
  o=["my old base got destroyed", "my first house is just a crater now", "went back to my old base and it's gone",
     "someone tore down my old house", "my old base burned down"],
  q=[("how long since you'd been back?", ["ages", "too long, clearly"]),
     ("did anything survive?", ["one chest, half full", "nothing. not even the floor."])],
  m=["i had so many memories there", "my first bed was there", "i didn't even get to say goodbye",
     "it's just dirt and burnt planks now"],
  r=["same, my first base is gone too", "creepers took mine", "mine burned, i still think about it"],
  a=["rebuild it, just smaller", "keep a piece of it, a block or a sign", "build a new one and take the good parts"],
  j=["now it's a crater with history", "historical ruins", "a dig site now"],
  h=["i'll help you rebuild it", "let's go back there together", "i'll help you make a new one"])

G("first_house", "homesick", "its first house",
  o=["i miss my first little dirt hut", "my first house was tiny and i loved it", "i think about my first house a lot",
     "nothing beats my first hut", "my first house had one window and i miss it"],
  q=[("what was it like?", ["dirt walls, one bed", "tiny, by a lake"]),
     ("where is it?", ["somewhere back there", "i don't even know anymore"])],
  m=["it was so simple", "it kept me safe the first night", "i was so proud of it", "now everything's complicated"],
  r=["same, my first house was a hole in a hill", "i miss my first hut too", "simple times"],
  a=["build a little hut somewhere", "go find it", "keep things simple"],
  j=["a dirt mansion", "the good old dirt days", "the dirt hut era"],
  h=["let's go find it", "i'll help you build a copy", "let's build a tiny hut together"])

G("old_land", "homesick", "the land it came from", tags=["biomes", "memories", "feelings"],
  o=["i miss where i used to live", "i miss the old forest", "i miss the snow back home", "this biome isn't the same",
     "i miss the old hills"],
  q=[("what was it like?", ["huge dark oaks everywhere", "snowy and so quiet"]),
     ("why did you leave?", ["needed iron, there wasn't any", "too many mobs at night"])],
  m=["the trees there were massive", "everything here is flat and same-y", "it was quiet, really quiet",
     "the sunsets over the snow were something"],
  r=["same, i miss the desert, weirdly", "i miss the jungle i started in", "i miss the old forest too"],
  a=["go visit, it's not going anywhere", "bring saplings from there and plant them here", "move back sometime"],
  j=["the old land misses you too", "you're a nature critic", "this biome is trying its best"],
  h=["let's go visit together", "i'll help you plant trees like those", "i'll come with you"])

G("homeless", "homesick", "having no home", needs=["a:no_home"], tags=["home", "feelings", "complaints"],
  o=["i don't have a home", "i've been sleeping in holes", "no home, no bed", "every night somewhere new",
     "i don't have anywhere to go back to"],
  q=[("where do you sleep?", ["in holes in hills", "wherever i end up"]),
     ("why no house?", ["no time", "i can't find a spot that feels right"])],
  m=["i'm so tired of it", "i just want a bed of my own", "i want walls", "i want somewhere to come back to"],
  r=["i had no home for a while too", "i slept in holes for a week", "i know that feeling"],
  a=["build anything, even small", "pick a spot and just start", "join a town, they'll help you"],
  j=["living the hole life", "free real estate", "the world's your house"],
  h=["you can stay at mine", "i'll help you build", "let's find you a spot"])

# ---- A friend who left or died -------------------------------------------------------------------------------------

G("friend_died", "loss", "a friend who died", tags=["feelings", "friends", "death"],
  o=["my friend died", "i lost a friend", "my friend didn't make it", "a friend of mine died", "i keep thinking about my friend"],
  q=[("what happened?", ["they went out at night and didn't come back", "a fight. a bad one."]),
     ("were you there?", ["no. i heard later.", "yes. i couldn't do anything."])],
  m=["i didn't even say goodbye", "they were always around, every day", "it feels empty without them",
     "i keep looking for them at the farm"],
  r=["i lost a friend too, a while back", "it took me a long time to feel ok", "i still think about mine"],
  a=["keep busy, it helps a bit", "talk about them, it keeps them close", "remember the good stuff"],
  h=["i'll stay with you", "let's go somewhere quiet", "we can visit where they lived"],
  c=["they were lucky to have you", "you were a good friend to them"])

G("friend_left", "loss", "a friend who moved away", tags=["friends", "feelings", "memories"], sit=[],
  o=["my friend moved away", "a friend just left", "my best friend left", "my friend went off on their own",
     "my buddy left without saying much"],
  q=[("where did they go?", ["somewhere far", "no idea, they just went"]),
     ("will they come back?", ["maybe", "probably not"])],
  m=["i keep walking past their house", "it's so quiet now", "we used to mine together every day", "i just miss them"],
  r=["same, mine left too", "people come and go, it still hurts", "i lost a friend like that too"],
  a=["go visit them, it's not that far", "make some new friends, it's not replacing them", "keep in touch, drop by sometimes"],
  j=["you have me now", "it's an upgrade, honestly", "i'm way more fun"],
  h=["i'll hang out with you", "let's go find them", "i'll visit more"])

G("gone_for_good", "loss", "a friend gone for good", tags=["feelings", "friends", "death"],
  o=["they're gone for good", "my friend won't be coming back", "they died and didn't come back", "it's final this time",
     "they're really gone"],
  q=[("are you sure?", ["yes. i'm sure.", "i saw it happen"]),
     ("how are you holding up?", ["not great", "barely"])],
  m=["no coming back this time", "it doesn't feel real", "i keep waiting for them to walk up", "i miss them so much"],
  r=["i lost someone like that too", "it's the worst feeling there is", "i still miss mine"],
  a=["talk about them", "keep something of theirs", "take your time, there's no rush"],
  h=["i'm here", "i'll stay", "let's go somewhere quiet"])

G("falling_out", "loss", "a fight with a friend", tags=["friends", "feelings", "memories"], sit=[],
  o=["me and my friend had a fight", "my friend's not talking to me", "we argued and now it's weird",
     "i said something dumb to my friend", "my best friend's mad at me"],
  q=[("what was it about?", ["something stupid", "a chest, of all things"]),
     ("have you talked since?", ["no", "not yet"])],
  m=["i didn't mean it", "it was so stupid", "i miss them already", "i don't know how to fix it"],
  r=["same, me and my friend fought once", "we made up after a few days", "fights happen, even with the best ones"],
  a=["say sorry first, it's easier than you think", "give it a day or two", "go talk to them"],
  j=["a fight over a chest, classic", "the drama", "the whole town's watching"],
  h=["i'll talk to them for you", "i'll help you fix it", "let's figure out what to say"])

G("drifted_apart", "loss", "a friendship fading", tags=["friends", "feelings", "memories"], sit=[],
  o=["me and my old friend don't talk anymore", "we used to be close", "my friend and i drifted apart",
     "we don't hang out anymore", "i barely see my old friend"],
  q=[("what happened?", ["nothing, we just stopped", "we both got busy"]),
     ("do you miss them?", ["yes", "sometimes, a lot"])],
  m=["we used to do everything together", "now we just wave", "i don't know when it happened", "it just did"],
  r=["same, happened to me", "people change", "i lost a friend that way too"],
  a=["reach out, they might miss you too", "invite them mining", "it's not too late"],
  j=["you have me now", "it's an upgrade, honestly", "i'll take their spot"],
  h=["i'll come with you to see them", "let's invite them along", "i'll hang out with you meanwhile"])

G("missing_someone", "loss", "missing someone", needs=["a:mood_grieving"], tags=["feelings", "memories", "friends"],
  o=["i miss someone", "i keep thinking about someone i lost", "some days i miss them a lot", "i can't stop thinking about them",
     "i miss them today"],
  q=[("who?", ["a friend", "someone i knew"]),
     ("when did it happen?", ["a while ago", "not long ago"])],
  m=["little things remind me", "i walked past their spot today", "it comes back at night", "i'm ok, mostly"],
  r=["i miss someone too", "it comes and goes", "i know that feeling"],
  a=["remember the good times", "talk about them", "go easy on yourself"],
  h=["i'm here", "let's go for a walk", "stay a while"])

# ---- Being bad at things (a newbie) --------------------------------------------------------------------------------

G("cant_anything", "newbie", "being bad at everything",
  o=["i can't do anything right", "everything i try goes wrong", "i'm so bad at this", "i don't know what i'm doing",
     "i'm useless at everything"],
  q=[("what happened?", ["everything", "i died, then got lost, then died again"]),
     ("what are you trying to do?", ["anything", "just survive one day"])],
  m=["i died three times today", "i got lost twice going to the same tree", "i don't even have a pickaxe",
     "everyone else knows what they're doing"],
  r=["i was the same when i started", "my first week was a disaster", "nobody's good at first"],
  a=["start with wood, then stone tools", "make a bed first, sheep are your friends", "one thing at a time"],
  j=["you're doing great at dying", "practice makes slightly less bad", "you're a natural, at falling"],
  h=["i'll teach you", "come with me tomorrow", "i'll help you get started"],
  s=["it's a skill issue, but skills can be learned", "skill issue, literally"])

G("cant_fight", "newbie", "losing every fight", sit=["a:hurt", "a:mood_frustrated"],
  o=["i can't fight anything", "i lose every fight", "even zombies beat me", "i panic the second a mob shows up",
     "i run from everything, even chickens"],
  q=[("what do you fight with?", ["my fists", "a wooden sword"]),
     ("what beats you?", ["everything", "zombies, spiders, all of it"])],
  m=["i swing like crazy and miss", "i panic and run into more of them", "i forget to eat mid-fight",
     "a single spider chased me home"],
  r=["same, i used to run from chickens lol", "i couldn't fight for weeks", "fighting is hard at first"],
  a=["wait for your swing to charge, don't spam", "make a stone sword at least", "back up while you swing"],
  j=["the chickens respect you though", "running is a strategy", "you're a pacifist"],
  h=["i'll teach you to fight", "i'll make you a sword", "let's practice on zombies tonight"],
  s=["skill issue. learnable though", "charge your hits"])

G("cant_build", "newbie", "not knowing how to build",
  o=["my houses are so ugly", "i can't build anything nice", "my house is a dirt box", "i don't know how to build",
     "everyone's houses are so nice and mine isn't"],
  q=[("what did you build?", ["a dirt box with a door", "a cobble cube"]),
     ("what do you want to build?", ["a nice house with a real roof", "something cool, like yours"])],
  m=["it doesn't look like a house", "the roof is flat dirt", "i forgot windows", "i'm embarrassed to sleep in it"],
  r=["my first house was a dirt box too", "everyone starts with a box", "mine was worse, it had no roof"],
  a=["use logs on the corners", "add windows, even small ones", "make a slope roof with stairs"],
  j=["dirt chic is a style", "very minimalist", "the box era, we all had one"],
  h=["i'll help you build", "come look at mine for ideas", "let's build one together"],
  s=["box houses are a skill issue lol", "learn stairs"])

G("cant_mine", "newbie", "being scared of caves", sit=["a:mood_anxious"],
  o=["i always get lost in caves", "i can't find any ore", "caves scare me", "i dig and find nothing",
     "i don't know where to mine"],
  q=[("how deep do you go?", ["not very", "until i hear something"]),
     ("do you bring torches?", ["some", "i forgot them, last time"])],
  m=["i always get lost", "i run out of torches halfway", "i hear things and run", "i found one coal. one."],
  r=["same, i got lost in my first cave", "caves scared me too", "i couldn't find ore for days"],
  a=["torches on the right going in, follow them out", "bring a lot of torches", "dig a staircase down"],
  j=["caves are just spooky holes", "the ore is shy", "you're a surface dweller"],
  h=["i'll take you mining", "i'll show you a cave", "let's go together"],
  s=["torches on one side, rookie", "skill issue, learnable"])

G("crafting_confused", "newbie", "not understanding crafting", tags=["crafting", "feelings", "advice"],
  o=["i don't understand crafting", "how do you make anything", "i can't remember the recipes",
     "i tried to make a pickaxe and made sticks", "crafting is so confusing"],
  q=[("what did you try?", ["a pickaxe", "a furnace"]),
     ("did you use a crafting table?", ["what table", "yes, i think"])],
  m=["i made a hundred sticks", "i wasted all my wood", "i don't know the shapes", "everyone else just knows"],
  r=["i made sticks by accident too", "i didn't know about tables at first", "crafting confused me too"],
  a=["make a crafting table first", "three on top, two sticks down for a pick", "eight cobble in a ring is a furnace"],
  j=["the stick master", "stick collector", "sticks are useful"],
  h=["i'll show you", "i'll make it with you", "let's craft together"])

G("everyone_better", "newbie", "everyone being better",
  o=["everyone's better than me", "everyone has diamonds and i have stone", "i'm so far behind everyone", "everyone knows everything",
     "i feel so slow"],
  q=[("who?", ["everyone", "the people around here"]),
     ("behind how?", ["gear", "everything"])],
  m=["they all have iron armor", "they have real houses", "they talk about places i've never seen",
     "i'm still on stone tools"],
  r=["i felt that too", "everyone was new once", "it took me ages"],
  a=["go at your own pace", "learn one thing at a time", "ask people, they like teaching"],
  j=["you're better at being new", "stone gang, represent", "slow and steady"],
  h=["i'll help you catch up", "i'll show you stuff", "let's mine together"])

G("first_nights", "newbie", "surviving the night", sit=["time_night", "time_evening"],
  o=["the nights are so scary", "i keep dying at night", "i didn't make it through the night again",
     "i don't know how to survive the night", "the first night was awful"],
  q=[("did you have a bed?", ["no", "i couldn't find sheep"]),
     ("did you build a shelter?", ["a hole", "no"])],
  m=["zombies everywhere", "i hid in a hole all night", "a spider found my hole", "i heard things all night"],
  r=["my first night was awful too", "i died on my first night", "i hid in a hole too"],
  a=["dig into a hill and seal it", "make a bed fast", "torches around your shelter"],
  j=["you survived... mostly", "night one, round two", "holes are cozy"],
  h=["you can stay with me", "i'll help you make a bed", "i'll help you build a shelter"])

# ---- Weather -------------------------------------------------------------------------------------------------------

G("rain_days", "weather", "rain for days", sit=["rain"],
  o=["it's rained for days", "rain, rain, rain", "it won't stop raining", "three days of rain now", "i'm so sick of the rain"],
  q=[("what's it stopping you doing?", ["building, mostly", "everything outside"]),
     ("how long has it been?", ["three days", "it feels like forever"])],
  m=["i can't see ten blocks in front of me", "the zombies don't even burn in the morning", "my build's been on hold all week",
     "everything's grey and gloomy"],
  r=["same, it rained for a week once", "i hate the rain too", "rain ruined my whole build week once"],
  a=["go mining, it never rains underground", "go fishing, they bite faster in the rain", "sleep through it, it can clear"],
  j=["the sky is crying for you", "free water", "great for the crops though"],
  h=["come mine with me till it stops", "come wait it out at mine", "let's go fishing in it"],
  w=["and the roof of my hut leaks", "and a zombie came out at noon because of it"])

G("storm", "weather", "a thunderstorm", tags=["weather", "danger", "complaints"], sit=["thunder"],
  o=["this storm is scary", "lightning hit right next to me", "the thunder won't stop", "storms are the worst",
     "lightning set a tree on fire right by me"],
  q=[("where were you?", ["outside, of course", "up on my roof"]),
     ("did it hit you?", ["almost", "close enough to hear it crackle"])],
  m=["mobs everywhere in the dark", "the thunder's so loud it shakes", "i hid under a tree, which was dumb",
     "the fire spread across three trees"],
  r=["same, lightning hit my house once", "i hate storms, they make me jumpy", "a storm turned a creeper blue once, never ran so fast"],
  a=["stay inside till it passes", "put a lightning rod on your roof", "sleep through it, you can in a storm"],
  j=["the sky's angry at you specifically", "free fire, at least", "charged creeper season"],
  h=["come stay at mine", "i'll make you a lightning rod", "let's wait it out together"])

G("snow", "weather", "the cold and powder snow", tags=["weather", "biomes", "complaints"],
  o=["it's freezing up here", "fell into powder snow", "the snow is everywhere", "i'm so cold", "my farm froze over"],
  q=[("where are you living?", ["up in the mountains", "out on the snowy plains"]),
     ("powder snow?", ["yes, i sank right in", "it looks like normal snow, it isn't"])],
  m=["i sank in powder snow and started freezing", "everything's white, i can't find my path",
     "the river's all ice", "a stray shot me while i was stuck"],
  r=["same, powder snow got me once", "i hate snow biomes", "i nearly froze once, never again"],
  a=["leather boots let you walk on powder snow", "keep a campfire going near home", "move somewhere warmer"],
  j=["snowman life", "you're a popsicle now", "free snowballs"],
  h=["i'll make you leather boots", "come down to mine, it's warmer", "let's find you somewhere warmer"])

G("rain_build", "weather", "rain on its build day", tags=["weather", "building", "complaints"], sit=["rain"],
  o=["the rain ruined my build day", "i can't build in the rain", "it rained right when i started building",
     "rain every time i build", "my build plans got rained out"],
  q=[("what were you building?", ["the roof, finally", "the last wall"]),
     ("couldn't you build anyway?", ["i can't see where i'm placing", "the mobs come out in it"])],
  m=["it got so dark", "a skeleton shot me off the scaffold", "i placed three blocks in the wrong spot",
     "i gave up and went inside"],
  r=["same, it always rains when i build", "rain ruins my build days too", "i hate building in the rain"],
  a=["do the inside on rainy days", "put the roof on first next time", "plan the next part while it rains"],
  j=["the sky doesn't like your build", "build the roof first, then it can rain", "the rain just wanted to help"],
  h=["i'll help you when it stops", "let's do the inside together", "come wait at mine"])

G("thunder_mobs", "weather", "a storm bringing mobs out by day", tags=["weather", "mobs", "danger"], sit=["thunder", "rain"],
  o=["the storm brought mobs out in the day", "zombies don't burn when it's raining", "skeletons everywhere because of the storm",
     "the storm made it night at noon", "storm mobs everywhere"],
  q=[("how many?", ["lots", "more than at night"]),
     ("did you fight them?", ["i ran", "i tried, then i ran"])],
  m=["they just don't burn in the rain", "it's so dark at noon", "i got surrounded at my own farm",
     "i hid in my house and waited"],
  r=["same, a storm brought a horde to my door", "storm mobs are the worst", "i hate storm days"],
  a=["stay inside till it clears", "light up more, they still spawn in the dark", "sleep through it if you can"],
  j=["it's a mob party out there", "the mobs love the rain", "rainy day mob festival"],
  h=["come stay at mine", "i'll fight them with you", "i'll bring torches"])

# ---- Night fears ---------------------------------------------------------------------------------------------------

G("night_noises", "night", "noises at night", tags=["night", "danger", "feelings"], sit=["time_night", "a:mood_anxious"],
  o=["i keep hearing things at night", "something's scratching outside my wall", "the noises at night scare me",
     "i can't sleep, too many noises", "i hear footsteps outside"],
  q=[("what kind of noises?", ["groaning, and something clicking", "a hiss. just once."]),
     ("is it close?", ["right outside", "i think so, it's hard to tell"])],
  m=["i keep getting up to check", "i look out and see nothing", "it sounds like it's in the walls",
     "i don't sleep until the sun's up"],
  r=["same, i hear things too", "the night is so noisy", "i used to check every single noise"],
  a=["light up all around your house", "build a wall, then you know nothing's close", "get a dog, they hear stuff first"],
  j=["it's just the zombies saying goodnight", "night choir", "they're singing to you"],
  h=["i'll check it out with you", "come stay at mine", "i'll bring torches"],
  s=["it's a zombie. hit it.", "scared of noises lol"])

G("cant_sleep", "night", "monsters nearby", tags=["night", "sleep", "complaints"],
  o=["can't sleep, monsters nearby", "my bed says there are monsters nearby again", "there are monsters nearby and i can't find them",
     "my bed won't let me sleep", "something's near my bed and i can't find it"],
  q=[("did you look?", ["everywhere", "i can't find it"]),
     ("where's your bed?", ["in my house", "in a cave i dug"])],
  m=["i searched every corner", "it's probably under the floor somewhere", "i can hear it, i can't see it",
     "i'm so tired, the phantoms are coming"],
  r=["same, a zombie was under my floor once", "i couldn't sleep for a week", "monsters nearby is my nightmare"],
  a=["light everything around the bed", "check the caves under your floor", "move your bed higher up"],
  j=["monsters nearby, classic", "they want to cuddle", "sleepover"],
  h=["i'll help you find it", "come sleep at mine", "let's light up your place"],
  s=["find it and kill it", "light it up lol"])

G("long_nights", "night", "long nights", tags=["night", "boredom", "complaints"], sit=["time_night", "time_evening"],
  o=["nights are too long", "it's always night when i need to do stuff", "night came way too fast",
     "i'm stuck inside every night", "the nights drag on"],
  q=[("what do you do all night?", ["sort chests", "nothing"]),
     ("can't you sleep?", ["not tired", "monsters nearby, apparently"])],
  m=["i sorted my chests twice", "i waste half the day in here", "i could be mining", "i just listen to the zombies"],
  r=["same, nights take forever", "i hate waiting for morning", "i get so bored too"],
  a=["sleep, it skips it", "mine at night, it's always dark down there", "save inside jobs for the night"],
  j=["the moon is your friend", "night shift", "vampire life"],
  h=["let's mine at night together", "come over, we'll sort stuff", "i'll keep you company"],
  s=["go fight something", "the night's for grinding"])

G("dark_caves", "night", "the dark in caves", tags=["caves", "danger", "feelings"], sit=["a:mood_anxious"],
  o=["caves are too dark", "i hate the dark in caves", "i ran out of torches in a cave", "caves freak me out",
     "the dark down there gets to me"],
  q=[("how deep were you?", ["deep", "too deep"]),
     ("how many torches?", ["ran out", "not nearly enough"])],
  m=["i couldn't see my own hands", "something was breathing, i swear", "i ran and ran", "i got lost on the way out"],
  r=["same, caves scare me too", "i ran out of torches once, worst day", "the dark gets to everyone"],
  a=["bring double the torches you think", "light as you go, every few blocks", "go with someone"],
  j=["the dark is just scary air", "the cave's shy", "bring a glow squid lol"],
  h=["i'll come with you next time", "i've got torches to spare", "let's go together"],
  s=["bring torches lol", "scared of the dark?"])

G("caught_out", "night", "being caught out at night", tags=["night", "danger", "complaints"], sit=["time_night", "time_evening"],
  o=["got caught out at night again", "night fell before i got home", "stuck outside at night", "i was out when it got dark",
     "dark came fast and i was far from home"],
  q=[("where were you?", ["far from home", "in the woods"]),
     ("what did you do?", ["dug a hole and sat in it", "ran for it"])],
  m=["zombies on every side", "i dug in and listened to them all night", "a spider found me anyway", "i barely made it home"],
  r=["same, i got caught out once", "i dug a hole and waited too", "night came fast for me too"],
  a=["head home when the sun gets low", "carry a bed on long trips", "dig in and wait, don't run"],
  j=["the hole hotel", "adventure", "sleepover with zombies"],
  h=["i'll walk home with you", "let's travel together next time", "i'll lend you a bed"],
  s=["watch the sun lol", "carry a bed, rookie"])

# ---- Getting lost --------------------------------------------------------------------------------------------------

G("lost_exploring", "lost", "getting lost exploring", tags=["exploring", "complaints", "places"],
  o=["got so lost exploring", "took me days to find my way back", "i wandered too far", "i had no idea where i was",
     "i got lost and couldn't find home"],
  q=[("how far did you go?", ["way too far", "past the mountains"]),
     ("how did you get back?", ["followed a river", "pure luck"])],
  m=["every tree looked the same", "i walked in a big circle", "i ran out of food on the way", "i almost gave up"],
  r=["same, i got lost for days once", "i got lost in a forest", "i walked in circles once"],
  a=["carry a compass", "pillar up and look around", "follow rivers"],
  j=["you're an explorer now", "scenic route", "the long way home"],
  h=["i'll make you a compass", "next time we go together", "i'll show you landmarks"],
  s=["remember where you went lol", "compass. exists."],
  w=["and when i got back, a creeper had been by", "and i'd left my iron in the furnace the whole time"])

G("lost_cave", "lost", "getting lost in a cave", tags=["caves", "exploring", "complaints"],
  o=["got lost in a cave", "couldn't find my way out of the mine", "spent forever looking for the exit", "lost in the tunnels",
     "the cave was a maze"],
  q=[("how did you get out?", ["dug straight up", "found my own torches"]),
     ("how long were you down there?", ["forever", "a whole day"])],
  m=["every tunnel looked the same", "i ran out of torches", "i heard things", "i dug straight up and came out in a lake"],
  r=["same, i got lost in a cave", "i dug up to get out once", "caves are mazes"],
  a=["torches on one side, follow them out", "mark the way out", "dig a staircase up"],
  j=["cave dweller", "the cave kept you", "you live there now"],
  h=["i'll come with you next time", "i've got torches", "let's mark it together"],
  s=["torches on one side lol", "lost in a cave, rookie"])

G("lost_death", "lost", "losing the place it died", tags=["death", "exploring", "complaints"], sit=["a:died_lately"],
  o=["can't find where i died", "my stuff is out there somewhere", "i lost my death spot", "i don't know where i died",
     "i've been looking for my stuff for hours"],
  q=[("do you remember anything?", ["a big tree", "a river"]),
     ("how long ago?", ["a while", "too long"])],
  m=["it's probably all gone now", "every hill looks like the one", "i keep finding the wrong big tree", "i'm giving up"],
  r=["same, i never found my stuff", "i looked for hours once", "lost my death spot too"],
  a=["remember a landmark next time", "set your bed nearer where you go", "go back fast, the clock's ticking"],
  j=["it's a treasure hunt now", "your stuff's on an adventure", "x marks nothing"],
  h=["i'll help you look", "let's search together", "i'll lend you stuff"],
  s=["remember where you died lol", "skill issue twice"])

G("lost_ocean", "lost", "getting lost at sea", tags=["ocean", "exploring", "complaints"],
  o=["got lost on the ocean", "my boat went the wrong way for hours", "i couldn't find land anywhere",
     "the ocean is way bigger than i thought", "i was lost at sea all night"],
  q=[("how long were you out there?", ["all night", "a whole day and a night"]),
     ("did you find land?", ["eventually, an island first", "barely, i saw a torch"])],
  m=["just water in every direction", "drowned kept popping up at night", "i think i paddled in circles",
     "i ate my last bread in a boat"],
  r=["same, i got lost at sea once", "the ocean's huge, it fooled me too", "my boat broke in the middle of nowhere once"],
  a=["follow the coast, don't cut across", "carry a compass, it points home", "the sun comes up in the east, use it"],
  j=["sailor life", "you're a pirate now", "the sea wanted to keep you"],
  h=["next time we go together", "i'll make you a compass", "i'll lend you a boat"],
  s=["follow the coast lol", "compass, rookie"])

G("lost_night", "lost", "getting lost in the dark", tags=["night", "exploring", "complaints"], sit=["time_night"],
  o=["got lost at night", "i couldn't find my house in the dark", "walked right past my house at night",
     "everything looks different at night", "lost in the dark"],
  q=[("where were you?", ["close to home", "in the woods"]),
     ("how did you find it?", ["morning", "a torch"])],
  m=["it was so dark", "i walked in circles", "zombies everywhere", "i found it at sunrise"],
  r=["same, i got lost at night", "my house was right there", "i walked past my house"],
  a=["light a path home", "put a column of torches on your roof", "carry torches and light as you go"],
  j=["your house was hiding from you", "a night tour of your own street", "your house is shy"],
  h=["i'll help you light a path", "i'll walk home with you", "let's put torches on your roof"],
  s=["light your house lol", "can't find your own house?"])

# ---- Villagers -----------------------------------------------------------------------------------------------------

G("dumb_villagers", "villagers", "dumb villagers",
  o=["villagers are so dumb", "a villager walked straight into lava", "villagers keep wandering out at night",
     "a villager fell in my well", "the villagers keep getting lost"],
  q=[("what did they do this time?", ["walked off a cliff", "went for a stroll at midnight"]),
     ("did they make it?", ["no", "barely, i dragged him in"])],
  m=["they never learn", "i fenced them in and one still got out", "they walk into everything",
     "one stood in the rain staring at a wall"],
  r=["same, a villager walked into my lava moat", "villagers are so dumb, it's almost cute", "one fell in my farm water"],
  a=["fence them in, two blocks high", "light the village so less comes for them", "build walls round the whole village"],
  j=["they're just adventurous", "brave villagers", "hrmm"],
  h=["i'll help you fence them", "let's light the village", "let's build a wall"])

G("villager_bed", "villagers", "villagers taking its things", tags=["villagers", "complaints", "home"],
  o=["a villager took my bed", "a villager claimed my bed", "a villager sleeps in my bed now",
     "i put a bed down and a villager took it", "the villagers keep claiming my beds"],
  q=[("how?", ["they just claim them", "it walked up and it was his"]),
     ("did you get it back?", ["no", "i broke it and made a new one"])],
  m=["it climbs in every night", "i have to make another", "it stares at me from my bed", "it's mine. it was mine."],
  r=["same, a villager took my bed", "villagers take everything", "a villager took my lectern once"],
  a=["put a block in the way", "build away from villagers", "make another and put it somewhere private"],
  j=["you have a roommate now", "it pays rent in emeralds, maybe", "hrmm, it says thanks"],
  h=["i'll make you another bed", "i've got wool", "let's build you a new spot"])

G("trade_prices", "villagers", "villager prices", tags=["villagers", "trade", "complaints"],
  o=["villager prices went up", "the librarian wants so many emeralds", "trades are so expensive",
     "the villager raised prices on me", "villagers are ripping me off"],
  q=[("how much now?", ["twenty emeralds", "way too much"]),
     ("for what?", ["one book", "the stuff i always buy"])],
  m=["it was cheap yesterday", "i traded too much, now it's mad", "i'm out of emeralds", "it's robbery"],
  r=["same, my librarian went up too", "villagers rip everyone off", "prices went up for me too"],
  a=["wait a bit, it comes back down", "trade other stuff for a while", "cure a zombie villager, they give discounts"],
  j=["the villagers are smart", "hrmm, pay up", "they've learned"],
  h=["i've got some emeralds", "i'll trade for you", "let's find another librarian"])

G("golem", "villagers", "an iron golem attacking it", tags=["villagers", "danger", "complaints"],
  o=["an iron golem attacked me", "the golem hit me", "a golem threw me in the air", "the iron golem hates me",
     "the golem chased me out of the village"],
  q=[("what did you do?", ["nothing!", "i hit a villager by accident"]),
     ("how bad was it?", ["bad", "really bad, i flew"])],
  m=["it threw me so high", "i landed on a roof", "it hit so hard", "it followed me to the edge of the village"],
  r=["same, a golem threw me", "golems hate me too", "i hit a villager by accident once, same thing"],
  a=["don't hit villagers, even by accident", "just run, they're slow", "give it some space for a day"],
  j=["you flew though, how was it", "golem airlines", "it was just playing with you"],
  h=["i'll walk you past it", "i'll go in first", "i'll talk to it lol"],
  s=["don't hit villagers lol", "lost to a golem"])

G("zombified", "villagers", "its villager turning into a zombie", tags=["villagers", "mobs", "complaints"],
  o=["a zombie got my villager", "my villager turned into a zombie", "the zombies got the village", "lost my best trader",
     "zombie villagers everywhere now"],
  q=[("which one?", ["the librarian", "the farmer"]),
     ("can you cure it?", ["i need a golden apple", "maybe, if i'm fast"])],
  m=["it had the best trades", "i didn't protect it", "it just groans at me now", "i feel bad"],
  r=["same, i lost my librarian that way", "the zombies got my farmer once", "i cured one once, worth it"],
  a=["weakness potion and a golden apple cures it", "build walls round the village", "light it up so fewer zombies come"],
  j=["it's a zombie librarian now", "groaning trader", "hrmm, braaains"],
  h=["i'll help you cure it", "i've got gold for an apple", "let's build walls"])

G("nitwit", "villagers", "a useless villager", tags=["villagers", "complaints", "jokes"],
  o=["the nitwit won't do anything", "there's a nitwit in my village", "the green villager won't trade",
     "useless villager, honestly", "the nitwit just walks around"],
  q=[("can't he work?", ["nope. never.", "won't even look at a workstation"]),
     ("what does he do all day?", ["walks around", "nothing at all"])],
  m=["he eats my bread", "he sleeps in", "he walks in circles", "nothing else, ever"],
  r=["same, we have a nitwit", "nitwits are useless", "ours does nothing either"],
  a=["leave him be", "build around him", "he's just there, let him be there"],
  j=["he's on holiday forever", "living the dream", "the nitwit life"],
  h=["i'll help you find a real trader", "let's move him somewhere", "i'll ignore him with you"])

# ---- The town ------------------------------------------------------------------------------------------------------

G("crowded", "town", "a crowded town",
  o=["this town is too crowded", "too many people here", "can't walk without bumping into someone", "the town's packed",
     "i can't find space anywhere"],
  q=[("how many people now?", ["too many", "everyone and their cow"]),
     ("where's it worst?", ["the market", "right outside my door"])],
  m=["i can't hear myself think", "there's always someone at my door", "the road's full all day", "i miss the quiet"],
  r=["same, i hate the crowd", "the town's grown a lot", "i miss when it was small"],
  a=["build on the edge of town", "go out early, before everyone", "find a quiet spot and keep it"],
  j=["popular town", "everyone loves {a.town}, apparently", "everyone loves it here, apparently"],
  h=["let's find you a quiet spot", "i'll show you a quiet place by the river", "come out with me tomorrow"])

G("loud", "town", "a loud town",
  o=["the town's so loud", "the bell keeps ringing", "the animals are so noisy", "people talk all night",
     "it's so loud here"],
  q=[("what's so loud?", ["the animals", "the bell, mostly"]),
     ("all the time?", ["yes", "mostly at night"])],
  m=["i can't sleep", "the cows moo right under my window", "someone rang the bell three times last night",
     "everyone talks by my door"],
  r=["same, the cows keep me up", "it's loud here, i know", "i hate the bell too"],
  a=["move to the edge", "build thicker walls", "wool walls muffle it"],
  j=["the cow choir sings again", "very lively town", "free music, all night"],
  h=["let's move you somewhere quieter", "i'll help build walls", "come stay out with me"])

G("ugly_neighbour", "town", "a neighbour's ugly house", tags=["town", "building", "gossip"],
  o=["my neighbour's house is so ugly", "the house next to mine is a dirt box", "my neighbour built a cobble tower",
     "the house next to me ruins the street", "my neighbour's house is an eyesore"],
  q=[("what's wrong with it?", ["it's dirt. all dirt.", "cobble everywhere, no windows"]),
     ("did you say anything?", ["no", "i hinted"])],
  m=["it's right by mine", "it ruins my view", "it doesn't even have a door, just a hole", "the roof is flat dirt"],
  r=["same, my neighbour has a dirt house", "ugly houses everywhere", "my neighbour's is worse"],
  a=["offer to help them fix it up", "plant trees in between", "build a hedge"],
  j=["dirt chic is a style now", "it's rustic, be nice", "it's modern art, you wouldn't get it"],
  h=["let's offer to help them", "i'll plant trees with you", "let's build a hedge"])

G("no_space", "town", "no room to build", tags=["town", "building", "complaints"],
  o=["no space left to build in town", "every spot's taken", "i want to build but there's no room", "the town's full",
     "no room for my farm"],
  q=[("what do you want to build?", ["a farm", "a bigger house"]),
     ("where would you put it?", ["anywhere", "near my house"])],
  m=["every flat bit is taken", "i'd have to go outside the wall", "the only spot left is a swamp", "i'm stuck"],
  r=["same, no room for my farm", "the town's packed", "i built outside the wall"],
  a=["build up, two floors", "build just outside the wall", "dig down, basements count"],
  j=["build up, tower time", "underground town", "tiny house, very cozy"],
  h=["let's look for a spot", "build next to mine", "i'll help you dig a basement"])

G("leader", "town", "the town's leader", tags=["town", "politics", "complaints"], sit=["a:dislikes_leader"],
  o=["the leader doesn't listen", "the town leader makes bad calls", "i don't like how the town's run",
     "the leader never asks us anything", "the leader's useless, honestly"],
  q=[("what did they do now?", ["new rules nobody asked for", "nothing, as usual, while the wall falls apart"]),
     ("did you tell them?", ["i tried. they nodded and walked off.", "what's the point, they don't listen"])],
  m=["nobody asked us about any of it", "rules for us, not for them", "the wall's still got a hole in it",
     "i'm tired of it, really tired"],
  r=["same, i don't like the leader either", "the leader never listens to me", "i voted for the other one, for what it's worth"],
  a=["vote them out next time", "talk to them when they're not busy", "get a few people together, they listen to a crowd"],
  j=["the leader needs a nap", "their hat's too big for their head", "the leader's busy leading, apparently"],
  h=["i'll back you at the next vote", "let's go talk to them together", "i'll vote with you"])

G("mess", "town", "a messy town",
  o=["the town's a mess", "there are pillars everywhere", "nobody cleans up after themselves", "dirt all over the roads",
     "the town looks awful lately"],
  q=[("what kind of mess?", ["dirt pillars and holes", "half-dug paths and floating trees"]),
     ("who's doing it?", ["everyone, a little", "no idea, it just appears"])],
  m=["there's a hole right in the main road", "dirt pillars from people cutting trees", "someone left a floating tree top",
     "nobody seems to care but me"],
  r=["same, it drives me nuts too", "i cleaned up once and it was back in a day", "nobody ever cleans, it's true"],
  a=["start cleaning, others might join", "ask everyone to take their pillars down", "make it a town rule"],
  j=["rustic", "it's lived in", "it's got character"],
  h=["let's clean it up together", "i'll take the pillars, you do the holes", "let's do it tomorrow morning"])

G("pens", "town", "animal pens next to its house", tags=["town", "animals", "complaints"],
  o=["the animal pens are right next to my house", "the pigs are right under my window", "the chickens are everywhere",
     "the cows keep ending up in my yard", "the pens are way too close to my door"],
  q=[("whose animals are they?", ["the town's", "my neighbour's"]),
     ("how close?", ["right against my wall", "close enough to hear them breathe"])],
  m=["they moo all night long", "a chicken got on my roof somehow", "the pigs got into my garden",
     "i step out my door into a cow"],
  r=["same, i had chickens on my roof", "the pens by me are loud too", "cows in my yard, every morning"],
  a=["ask to move the pens to the edge", "put a wall up on your side", "plant a hedge between you"],
  j=["farm life", "free eggs", "your alarm goes moo"],
  h=["let's ask to move them", "i'll help you build a wall", "i'll help you chase the cows back"])

G("gossip", "town", "gossip about it", tags=["town", "gossip", "complaints"],
  o=["people in town keep talking about me", "everyone's gossiping about me", "i heard what they said about me",
     "the town talks too much", "gossip everywhere"],
  q=[("what are they saying?", ["that i died twice in a day", "that my house is ugly"]),
     ("who's saying it?", ["everyone", "the people by the market"])],
  m=["it's not even true", "everyone already knows", "they stop talking when i walk up", "i can't do anything about it"],
  r=["same, they talked about me", "gossip is everywhere", "they talk about everyone"],
  a=["ignore it, it passes", "talk to them, straight up", "give them something better to talk about"],
  j=["you're famous now", "you're the news, congrats", "celebrity life"],
  h=["i'll talk to them", "i'll stick up for you", "let's ignore them together"])

G("shared_storage", "town", "the town chest", tags=["town", "storage", "complaints"],
  o=["someone keeps emptying the town chest", "the town storage is a mess", "nobody puts anything back",
     "the shared chest is empty again", "people take and never give"],
  q=[("what's missing?", ["everything", "the iron and the food"]),
     ("who's taking it?", ["no idea", "everyone, a bit at a time"])],
  m=["i filled it yesterday", "it's empty again", "nobody ever puts anything in", "i'm done filling it"],
  r=["same, i filled it and it emptied in a day", "people take, nobody gives, every time", "shared chests never work"],
  a=["make a rule, take one put one", "put signs on it", "stop filling it for a bit"],
  j=["the chest's a black hole", "it's a magic chest, things vanish", "it eats stuff, feed it less"],
  h=["let's make rules", "i'll fill it with you", "let's talk to everyone"])

G("town_rules", "town", "town rules", tags=["town", "politics", "complaints"], sit=["a:farms_closed"],
  o=["the town rules are too strict", "we can't use the farms now", "there's a new rule every day",
     "i can't do anything without asking first", "too many rules around here"],
  q=[("what rules?", ["the farms are closed", "no building past the wall"]),
     ("who made them?", ["the leader", "everyone voted, i didn't"])],
  m=["i'm hungry and the farm's right there", "i can't build where i want", "i got told off for picking a carrot",
     "it's not fair, it's our town too"],
  r=["same, i hate the new rules", "they're too strict, i agree", "rules everywhere lately"],
  a=["vote on it next time", "talk to the leader about it", "follow them for now, push back later"],
  j=["rules rule", "law and order", "the rule book's bigger than the town"],
  h=["i'll back you at the vote", "i'll share my food till they open", "let's talk to the leader together"])

G("newcomers", "town", "new people everywhere", tags=["town", "friends", "complaints"],
  o=["so many new people in town", "new faces every day", "the town's changing so fast", "i don't know anyone anymore",
     "newcomers everywhere"],
  q=[("is that bad?", ["kind of", "it's just different"]),
     ("how many new ones?", ["lots", "a few every day"])],
  m=["i don't know half of them", "they're building everywhere", "the quiet corner's got three houses now",
     "i miss the old town"],
  r=["same, i miss the old town", "so many new people, i know", "i don't know anyone either"],
  a=["say hi, they're probably nervous too", "get to know a couple of them", "it's good for the town, really"],
  j=["popular town, be proud", "fame has a price", "tourists everywhere"],
  h=["let's go meet them together", "i'll introduce you", "let's say hi tomorrow"])

# ---- More grief, and each kind's own -------------------------------------------------------------------------------

G("bandit_robbed", "grief", "being robbed on the road", tags=["complaints", "danger", "gear"],
  o=["a bandit robbed me on the road", "got jumped and robbed out past the river", "someone ambushed me and took my stuff",
     "a bandit hit me from behind and took my iron", "i got robbed walking home"],
  q=[("did you see who?", ["some guy with an iron sword", "no, it was too fast"]),
     ("what did they get?", ["my iron and my food", "my good pick, of course"])],
  m=["i didn't even have my sword out", "they knew exactly when to hit", "i'd just come back from the mine",
     "i was two hills from home"],
  r=["same, i got robbed near the swamp once", "a bandit took my bow last week", "i get robbed on that road all the time"],
  a=["walk with someone on that road", "keep your sword out after dark", "take a different way home each time"],
  j=["at least they didn't take your dignity... oh wait", "they had good taste", "the bandit's eating well tonight"],
  h=["i'll walk that road with you next time", "let's go find them", "i've got iron to spare, here"],
  s=["sword out, always", "you got robbed? couldn't be me"],
  w=["and then they waved at me the next day", "and they were wearing my helmet"])

G("animals_killed", "grief", "someone killing its animals", tags=["animals", "complaints", "farming"],
  o=["someone killed my cows", "half my sheep are gone, just wool on the ground", "someone hunted my pen empty",
     "my pigs were all killed", "came back to an empty pen"],
  q=[("all of them?", ["all but one", "every single one"]),
     ("people or mobs?", ["people, the gate was closed", "a zombie can't open a gate"])],
  m=["i bred those for days", "i named the old cow", "they left the leather behind", "the pen's so quiet now"],
  r=["same, someone hunted my chickens once", "i lost my whole cow pen once", "someone took my horse"],
  a=["move the pen closer to your house", "put a wall around the gate", "keep two hidden somewhere to start again"],
  j=["someone was really hungry", "the cows are in a better field", "steak night, but not for you"],
  h=["i'll give you two cows to start again", "i've got wheat for breeding", "let's build a safer pen"],
  s=["guard your animals lol", "should've walled it"],
  sit=["a:mood_angry", "a:hungry"])

G("forest_burned", "grief", "the forest by its house burning", tags=["trees", "complaints", "home"],
  o=["someone burned the forest by my house", "the woods next to me went up in flames", "the whole forest is ash now",
     "someone set a fire and it took the trees", "my tree spot is gone, burned"],
  q=[("how did it start?", ["someone left a campfire", "lava, someone dumped lava"]),
     ("did it reach your house?", ["it stopped at my wall", "it got the fence"])],
  m=["that was my wood for the year", "there were foxes in there", "it smells like smoke for days",
     "now i can see the ugly hill behind it"],
  r=["same, lightning burned the trees by me", "my forest burned once too", "someone torched my favourite oak once"],
  a=["replant it, saplings come back fast", "keep a gap between your house and the trees", "put stone around the edge"],
  j=["free clearing", "you've got a view now at least", "charcoal for days"],
  h=["i'll help you replant", "i've got saplings", "let's clear the ash together"])

G("warden", "mobs", "the deep dark", tags=["mobs", "danger", "caves"], sit=["a:mood_anxious", "a:close_call"],
  o=["went into the deep dark and something screeched", "the warden came out and i ran", "i heard the shrieker and froze",
     "the deep dark is the scariest place i've been", "something huge came out of the floor down there"],
  q=[("did it see you?", ["it can't see, it heard me", "it came right at me"]),
     ("did you get out?", ["barely, i ran the whole way", "i dropped half my stuff running"])],
  m=["it was so quiet i could hear my own feet", "the ground was all black and moving", "its heartbeat, i can still hear it",
     "i sneaked for an hour and then sneezed, basically"],
  r=["same, i heard it once and left forever", "the deep dark scares me too", "i lost a pick down there running"],
  a=["sneak everywhere down there", "don't touch the shriekers", "put wool on the floor, it hides your steps"],
  j=["it's just a big guy who likes quiet", "you woke the neighbour", "shhhh"],
  h=["i'll never go there, but i'll listen", "if you go back, i'll stand guard up top", "i'll give you a new pick"],
  s=["sneak lol", "you made noise, simple"])

G("diamond_hoe", "luck", "wasting diamonds on something dumb", tags=["diamonds", "crafting", "complaints"],
  o=["i crafted a diamond hoe by accident", "used two diamonds on a hoe", "i made the wrong diamond tool",
     "wasted my diamonds on a hoe", "i meant to make a pick and made a hoe"],
  q=[("how many diamonds?", ["two. two whole diamonds.", "all i had"]),
     ("can you undo it?", ["no", "you can't, i checked"])],
  m=["i put them in the wrong slots", "i was rushing", "i stared at it for a full minute", "i have the shiniest hoe ever"],
  r=["same, i made diamond boots first once", "i made a diamond shovel by mistake", "i wasted mine on a sword i never use"],
  a=["take your time at the table", "craft with one diamond out at a time", "use it, a hoe's not useless"],
  j=["best farmer in the world now", "very fancy wheat incoming", "that hoe will outlive us all"],
  h=["i'll help you mine more", "i've got a diamond you can have", "come mining tomorrow"],
  s=["read the recipe lol", "a hoe. ok."])

G("bed_nether", "death", "a bed exploding in the nether", tags=["nether", "death", "complaints"],
  o=["tried to sleep in the nether and my bed exploded", "my bed blew up in the nether", "beds explode there, i know now",
     "i put a bed down in the nether, big mistake", "boom. bed. nether. dead."],
  q=[("why would you sleep there?", ["i was tired!", "i didn't know"]),
     ("did your stuff survive?", ["it's in lava now", "some of it, before a ghast came"])],
  m=["it blew a crater in the netherrack", "the fire spread everywhere", "i just wanted to skip the night", "nobody told me"],
  r=["same, i did it on my first trip", "i lost a whole kit to a nether bed", "everyone does it once"],
  a=["never put a bed down there", "there's no night there to skip anyway", "beds there are for fighting, not sleeping"],
  j=["the nether doesn't do bedtime", "worst nap ever", "explosive sleep"],
  h=["i'll help you get your stuff back", "i'll lend you gear", "next nether trip, i'm coming"],
  s=["everyone knows beds explode there", "nap in the nether? come on"])

G("old_town", "homesick", "the town it left", tags=["town", "memories", "feelings"],
  o=["i miss my old town", "i miss the people from my old town", "this place isn't like my old town",
     "i keep thinking about the town i left", "i miss knowing everyone"],
  q=[("why did you leave?", ["it fell apart", "i wanted to see more"]),
     ("do you still talk to them?", ["not really", "a couple of them, sometimes"])],
  m=["we had a market every few days", "everyone knew my name", "i had a spot by the well", "it was loud but it was mine"],
  r=["i left a town once too", "i still miss my old group", "you never forget your first town"],
  a=["visit if you can", "make this one feel like that", "bring the good stuff from there here"],
  j=["the new town's better, it has me", "your old town misses you", "you were their celebrity"],
  h=["i'll introduce you to people here", "let's start a market here", "we can visit it together"])

G("no_best_friend", "lonely", "having no best friend", needs=["!strangers"], tags=["friends", "feelings", "complaints"],
  o=["everyone has a best friend but me", "nobody picks me first", "everyone's paired up but me",
     "i don't have that one person, you know", "i'm everyone's second friend"],
  q=[("who's everyone?", ["the people around here", "everyone i know"]),
     ("since when?", ["always, kind of", "lately it's worse"])],
  m=["they all go mining in twos", "i always end up walking behind", "when i ask, they already have someone",
     "i'm fine, it just stings"],
  r=["i felt like that for ages", "same, i was always the third", "it takes time to find your person"],
  a=["ask one person, regularly", "do things with people, it grows", "be the one who asks first"],
  j=["i'll be your best friend, lucky you", "first friend, reporting", "i'm free for the job"],
  h=["i'll do stuff with you", "let's go mining, just us", "come with me tomorrow"])

G("builders_block", "restless", "a builder's block", needs=["a:voice_builder"], tags=["building", "feelings", "boredom"],
  o=["i can't think of anything to build", "i've got blocks and no ideas", "every build i start looks the same",
     "i stared at an empty field all day", "i've lost my building spark"],
  q=[("what did you try?", ["a tower, then a tower, then a tower", "three houses, tore all three down"]),
     ("how long has it been?", ["days", "since i finished my last house"])],
  m=["i keep making the same roof", "my palettes all look boring now", "i placed one block and gave up",
     "i used to dream in builds"],
  r=["same, i had a block for a week once", "every builder gets that", "i just copied someone else's house when i was stuck"],
  a=["build something tiny and silly", "try a block you never use", "go look at a village, steal an idea"],
  j=["build a dirt hut for old times' sake", "build me a house then", "your field looks great empty"],
  h=["let's build something together", "i'll show you a spot that needs a build", "i'll gather, you design"],
  s=["just build lol", "builders block? place blocks"])

G("wasted_day", "restless", "a wasted day", needs=["a:voice_speedrunner"], tags=["feelings", "boredom", "complaints"],
  o=["wasted a whole day", "i did nothing useful today", "my whole route fell apart today", "i lost so much time today",
     "today was pure inefficiency"],
  q=[("what happened?", ["everything went wrong in order", "i got lost, then stuck, then lost again"]),
     ("how much time?", ["a whole day", "too much to say out loud"])],
  m=["i went back for a pick i didn't need", "i walked the wrong way for ages", "i sorted chests for no reason",
     "my plan had five steps and i did zero"],
  r=["same, i wasted three days once", "i lose days all the time", "my plans never survive the morning"],
  a=["plan the next day tonight", "do the big thing first", "stop going back for stuff"],
  j=["you were efficient at resting", "speedrun of doing nothing, new record", "it was a rest day, you planned it"],
  h=["i'll help you catch up tomorrow", "let's do a fast run together", "i'll keep you on track"],
  s=["skill issue, speedrunner edition", "bad route lol"])

G("hoard_full", "restless", "running out of chest space", needs=["a:voice_hoarder"], tags=["storage", "complaints", "home"],
  o=["i ran out of chest space again", "every chest i own is full", "i need a bigger storage room", "i can't fit anything else",
     "i built more chests and filled them already"],
  q=[("full of what?", ["everything. it's all important.", "stuff. good stuff."]),
     ("can't you throw some out?", ["no", "absolutely not"])],
  m=["i have six chests of cobble", "i might need it someday", "i can't find anything anymore",
     "i started piling stuff on the floor"],
  r=["same, my storage is a maze", "i built a whole room for chests once", "i can't throw anything out either"],
  a=["toss the cobble, you can always get more", "build a storage room with signs", "give some away, it feels good"],
  j=["you're a museum now", "the chests are winning", "dragon on a hoard of dirt"],
  h=["i'll help you build a storage room", "i'll take some of the cobble off you", "let's sort it together"],
  s=["just throw stuff out lol", "skill issue, storage edition"])

G("stuck_nomad", "restless", "staying in one place too long", needs=["a:voice_nomad"], tags=["feelings", "exploring", "boredom"],
  o=["i've been in one place too long", "i feel stuck here", "i need to move on", "i'm getting restless",
     "my feet want to go and i keep staying"],
  q=[("why haven't you left?", ["people here, i guess", "i keep saying tomorrow"]),
     ("where would you go?", ["anywhere i haven't been", "past the mountains, maybe"])],
  m=["i know every tree around here", "i've walked every path twice", "the horizon keeps calling",
     "i built a house and now it feels like a cage"],
  r=["same, i get restless too", "i moved three times last month", "staying put is hard for me too"],
  a=["take a long trip, come back after", "go explore for a few days", "go, the house will still be here"],
  j=["the horizon misses you", "you've seen all the dirt here", "go, before you grow roots"],
  h=["i'll come with you for a bit", "i'll watch your place while you're gone", "let's plan a trip"],
  s=["just leave lol", "walk then"])

G("surface_chores", "restless", "being stuck on the surface", needs=["a:voice_miner"], tags=["mining", "feelings", "complaints"],
  o=["i've been stuck on the surface all week", "too much surface work, not enough mining", "i miss my mine",
     "i haven't held a pickaxe in days", "it's too bright up here"],
  q=[("why are you up here?", ["farming, chores, all of it", "my mine flooded"]),
     ("how long?", ["a week", "too long"])],
  m=["the sun hurts", "the sky's too big", "i keep looking at the ground", "i dream about stone"],
  r=["same, i hate the surface", "i get antsy after a day up here", "i feel that, bright and loud up here"],
  a=["get the chores done, then dive", "take a short mining trip at night", "dig a mine right under your house"],
  j=["touch grass, you're doing it", "you're a surface dweller now", "the sun misses you too, kind of"],
  h=["i'll do your chores, go mine", "let's go mining tonight", "i'll help drain your mine"],
  s=["just go mine lol", "surface is a skill issue"])

# @@GRIEVANCES@@

# =====================================================================================================================
# Topics
# =====================================================================================================================

topics = []
WEIGHT = {"comfort": 0.45, "relate": 0.45, "advice": 0.4, "joke": 0.4, "skill": 0.7, "awk": 0.6, "help": 0.4,
          "hermit": 0.6, "friend": 0.45, "rant": 0.4}
NO_LEAD = {"complaints", "venting", "boredom", "trust", "politics"}


def turn(by, lines, chance=None, cond=None, mark=None):
    d = {"by": by, "lines": lines}
    if chance is not None:
        d["chance"] = chance
    if cond:
        d["if"] = cond
    if mark:
        d["mark"] = mark
    return d


def asks(pairs):
    return [{"line": question, "then": list(answers)} for question, answers in pairs]


def tags_of(g):
    return ["venting"] + (g["tags"] or CATS[g["cat"]]["tags"])[:3]


def subject_leads(g):
    # (Not the tags these topics fill up: a lead there would mostly come back to another vent.)
    own = [t for t in (g["tags"] or []) if t not in NO_LEAD]
    return uniq(own + CATS[g["cat"]]["leads"])


def leads_of(g, first, n=3):
    return uniq(first + subject_leads(g))[:n]


def base_needs(g):
    return uniq((CATS[g["cat"]].get("needs") or []) + (g["needs"] or []))


def add(g, variant, turns, leads, needs=None, weight=None, extra=None):
    cat = CATS[g["cat"]]
    w = weight if weight is not None else round(WEIGHT[variant] * cat.get("scale", 1.0), 2)
    t = {"id": P + g["slug"] + "_" + variant, "weight": w}
    n = uniq((needs if needs is not None else base_needs(g)))
    if n:
        t["needs"] = n
    t["tags"] = tags_of(g)
    t["leads"] = leads
    t["about"] = g["about"]
    t["turns"] = turns
    if extra:
        t.update(extra)
    topics.append(t)
    return t


def comfort_voices(g, key, k=3):
    cat = CATS[g["cat"]]
    v = {"any": uniq(g["c"] + some(cat["comfort"]["any"], k, key))}
    for voice, lines in cat["comfort"].items():
        if voice != "any":
            v[voice] = lines
    return v


def pool(g, name):
    """A shared pool, or its softer twin for heavy grievances."""
    if CATS[g["cat"]].get("soft") and name in SOFT:
        return SOFT[name]
    return globals()[name]


def resolve_ok(g, key):
    cat = CATS[g["cat"]]
    v = {"any": uniq(cat["resolve_ok"] + some(pool(g, "RESOLVE_OK"), 3, key + "ok"))}
    if not CATS[g["cat"]].get("soft"):
        for voice in some(sorted(RESOLVE_OK_VOICES), 2, key + "v"):
            v[voice] = RESOLVE_OK_VOICES[voice]
    return v


def resolve_bad(g, key):
    return uniq(CATS[g["cat"]]["resolve_bad"] + some(pool(g, "RESOLVE_BAD"), 2, key + "bad"))


def mixed_response(g, key):
    """b's answer when it could be anyone: comfort, a word of help, its own voice (the situational topics and the rant)."""
    cat = CATS[g["cat"]]
    v = {"any": uniq(g["c"] + some(cat["comfort"]["any"], 2, key) + g["h"][:1] + g["r"][:1])}
    for voice in ("helper", "pvp_sweat"):
        if voice in cat["comfort"]:
            v[voice] = cat["comfort"][voice]
    if g["j"]:
        v["casual"] = g["j"][:2]
    return v


def build(g):
    cat = CATS[g["cat"]]
    slug = g["slug"]
    key = P + slug
    o, q, m = g["o"], asks(g["q"]), g["m"]

    # comfort
    add(g, "comfort", [
        turn("a", o),
        turn("b", q, chance=0.85),
        turn("a", m, chance=0.7),
        turn("b", comfort_voices(g, key + "c")),
        turn("a", resolve_ok(g, key + "c"), chance=0.65, mark=["agree"]),
        turn("a", resolve_bad(g, key + "c"), cond=["!t:agree"]),
        turn("b", some(CLOSE_OK, 3, key + "c1"), chance=0.5, cond=["t:agree"]),
        turn("b", some(pool(g, "CLOSE_BAD"), 3, key + "c2"), chance=0.6, cond=["!t:agree"]),
    ], leads_of(g, ["feelings", "friends"]), needs=uniq(base_needs(g) + ["!strangers"]))

    # relate: b's own story
    add(g, "relate", [
        turn("a", o),
        turn("a", m, chance=0.5),
        turn("b", g["r"]),
        turn("a", some(pool(g, "RELATE_REACT"), 4, key + "r")),
        turn("b", cat["relate_tail"], chance=0.6),
        turn("a", some(pool(g, "RELATE_RELIEF"), 3, key + "r2"), chance=0.7, mark=["agree"]),
    ], leads_of(g, ["stories", "venting"]))

    # advice
    add(g, "advice", [
        turn("a", o),
        turn("b", q, chance=0.6),
        turn("a", m, chance=0.5),
        turn("b", g["a"]),
        turn("a", some(pool(g, "ADVICE_TAKE"), 4, key + "a"), chance=0.55, mark=["agree"]),
        turn("a", some(pool(g, "ADVICE_BRUSH"), 4, key + "a2"), cond=["!t:agree"]),
        turn("b", some(pool(g, "ADVICE_OK"), 3, key + "a3"), chance=0.5, cond=["t:agree"]),
        turn("b", some(pool(g, "ADVICE_BACK"), 3, key + "a4"), chance=0.6, cond=["!t:agree"]),
    ], leads_of(g, ["advice"]), needs=uniq(base_needs(g) + ["!b:voice_newbie"]))

    # a joke to cheer it up
    if g["j"]:
        add(g, "joke", [
            turn("a", o),
            turn("a", m, chance=0.5),
            turn("b", g["j"]),
            turn("a", some(JOKE_LAUGH, 4, key + "j"), chance=0.6, mark=["agree"]),
            turn("a", some(JOKE_FLAT, 3, key + "j2"), cond=["!t:agree"]),
            turn("b", some(JOKE_SMILE, 3, key + "j3"), chance=0.6, cond=["t:agree"]),
            turn("b", some(JOKE_SORRY, 3, key + "j4"), cond=["!t:agree"]),
        ], leads_of(g, ["jokes"]), needs=uniq(base_needs(g) + ["!rivals", "!b:voice_hermit"]))

    # skill issue (a pvp sweat), or a pvp sweat trying to comfort
    if g["s"]:
        add(g, "skill", [
            turn("a", o),
            turn("b", uniq(g["s"] + some(SKILL_GENERIC, 2, key + "s"))),
            turn("a", some(SKILL_CONCEDE, 3, key + "s1"), chance=0.3, mark=["agree"]),
            turn("a", uniq(cat["argue"] + some(SKILL_ARGUE, 2, key + "s2")), cond=["!t:agree"], mark=["disagree"]),
            turn("b", some(SKILL_RESPECT, 3, key + "s3"), chance=0.6, cond=["t:agree"]),
            turn("b", some(SKILL_COPE, 3, key + "s4"), cond=["t:disagree"]),
            turn("a", some(SKILL_LAST, 3, key + "s5"), chance=0.6, cond=["t:disagree"]),
        ], leads_of(g, ["argument", "fighting"]), needs=uniq(base_needs(g) + ["b:voice_pvp_sweat"]))
    elif cat.get("awkward"):
        add(g, "awk", [
            turn("a", o),
            turn("a", m, chance=0.5),
            turn("b", cat["awkward"]),
            turn("a", some(pool(g, "AWK_REACT"), 3, key + "k")),
            turn("b", some(pool(g, "AWK_B2"), 3, key + "k2"), chance=0.5),
        ], leads_of(g, ["fighting", "feelings"]), needs=uniq(base_needs(g) + ["b:voice_pvp_sweat"]))

    # an offer of help
    add(g, "help", [
        turn("a", o),
        turn("b", q, chance=0.6),
        turn("a", m, chance=0.5),
        turn("b", g["h"]),
        turn("a", some(HELP_ACCEPT, 3, key + "h"), chance=0.7, mark=["agree"]),
        turn("a", some(HELP_DECLINE, 3, key + "h2"), cond=["!t:agree"]),
        turn("b", some(HELP_SURE, 3, key + "h3"), chance=0.7, cond=["t:agree"]),
        turn("b", some(HELP_STANDS, 3, key + "h4"), chance=0.5, cond=["!t:agree"]),
    ], leads_of(g, ["help", "plans"]), needs=uniq(base_needs(g) + ["!rivals", "!b:voice_hermit"]))

    # a hermit: "that sucks." and on to something else
    add(g, "hermit", [
        turn("a", o),
        turn("a", m, chance=0.6),
        turn("b", uniq(cat["curt"] + some(pool(g, "HERMIT_CURT"), 3, key + "e"))),
        turn("b", some(pool(g, "HERMIT_CHANGE"), 3, key + "e2"), chance=0.7),
        turn("a", some(pool(g, "HERMIT_REACT"), 3, key + "e3"), chance=0.6),
    ], uniq(["random"] + some(["mining", "weather", "day", "trees"], 1, key + "e4") + subject_leads(g)[:1]),
        needs=uniq(base_needs(g) + ["b:voice_hermit"]))

    # a friend: "can i vent for a sec?"
    vent = cat["vent_ask"] + some(pool(g, "VENT_ASK"), 2, key + "f")
    add(g, "friend", [
        turn("a", asks(vent)),
        turn("b", uniq(a for _, answers in vent for a in answers)[:4]),
        turn("a", o),
        turn("a", m, chance=0.6),
        turn("b", uniq(g["c"] + some(cat["comfort"]["any"], 2, key + "f2") + g["h"][:2])),
        turn("a", some(pool(g, "FRIEND_OVER"), 3, key + "f3"), chance=0.7, mark=["agree"]),
        turn("a", some(pool(g, "FRIEND_STILL"), 2, key + "f4"), cond=["!t:agree"]),
        turn("b", some(pool(g, "FRIEND_CLOSE"), 3, key + "f5"), chance=0.5),
    ], leads_of(g, ["friends", "feelings"]), needs=uniq(base_needs(g) + ["friends"]))

    # situational: how a is, or the weather, fits
    sits = g["sit"] if g["sit"] is not None else cat.get("sit", [])
    for need in sits:
        if need in base_needs(g):
            continue
        needs = [n for n in base_needs(g) if n != "!" + need] + [need] + SIT_EXTRA.get(need, [])
        prompts = WHATS_WRONG_SOFT if need == "a:mood_grieving" or g["cat"] == "loss" else WHATS_WRONG
        add(g, "sit_" + need.replace("a:", "").replace("mood_", ""), [
            turn("a", NEED_PREFACE[need]),
            turn("b", asks([(p, some(o, 3, key + need + p)) for p in some(prompts, 2, key + need)])),
            turn("a", o[:2]),
            turn("a", m, chance=0.6),
            turn("b", mixed_response(g, key + need)),
            turn("a", resolve_ok(g, key + need), chance=0.65, mark=["agree"]),
            turn("a", resolve_bad(g, key + need), cond=["!t:agree"]),
            turn("b", some(CLOSE_OK, 3, key + need + "c"), chance=0.5, cond=["t:agree"]),
        ], leads_of(g, ["feelings", "help"]), needs=uniq(needs), weight=SIT_WEIGHT[need])

    # the long rant, in two parts
    if g["w"]:
        add(g, "rant", [
            turn("a", o),
            turn("b", q),
            turn("a", m),
            turn("b", some(RANT_PROMPT, 4, key + "w"), chance=0.8),
            turn("a", some(RANT_TEASE, 3, key + "w2"), chance=0.9),
        ], leads_of(g, ["feelings"]), extra={"next": [key + "_rant_2"]})
        part2 = add(g, "rant_2", [
            turn("a", g["w"]),
            turn("b", some(RANT_REACT, 4, key + "w3")),
            turn("a", g["w"], chance=0.5),
            turn("b", mixed_response(g, key + "w4")),
            turn("a", resolve_ok(g, key + "w5"), chance=0.6, mark=["agree"]),
            turn("a", resolve_bad(g, key + "w5"), cond=["!t:agree"]),
            turn("b", some(CLOSE_OK, 3, key + "w6"), chance=0.5, cond=["t:agree"]),
        ], leads_of(g, ["feelings", "jokes"]), weight=WEIGHT["rant"], extra={"follow_up": True})
        part2["tags"] = ["story_part"] + tags_of(g)[:3]


# =====================================================================================================================
# Checks of our own (the style rules), then the file
# =====================================================================================================================

FILLS = set("ally armor base built capital close_call crook day dead death diamonds disliked_player doing enemy group hate "
            "held insult kills leader like liked_player mob mob_count nation news_what news_who ore ore_count other place "
            "plan rival town traded_with".split())


def lines_in(element):
    groups = [element] if isinstance(element, list) else list(element.values())
    for group in groups:
        for line in group:
            if isinstance(line, dict):
                yield line["line"]
                yield from line["then"]
            else:
                yield line


def check():
    problems = []
    ids = set()
    for g in GRIEVANCES:
        assert g["cat"] in CATS, g["slug"]
        for field in ("o", "q", "m", "r", "a", "h"):
            assert g[field], (g["slug"], field)
    for t in topics:
        assert t["id"] not in ids, t["id"]
        ids.add(t["id"])
        assert 1 <= len(t["tags"]) <= 4 and 2 <= len(t["leads"]) <= 4, t["id"]
        for tr in t["turns"]:
            for line in lines_in(tr["lines"]):
                if len(line) > 90 or not line.isascii() or line != line.strip():
                    problems.append("%s: %r" % (t["id"], line))
                for who, word in re.findall(r"\{([ab])\.([a-z_]+)\}", line):
                    if word not in FILLS:
                        problems.append("%s: fill word %s" % (t["id"], word))
                if re.search(r"\{(?![ab]\.)", line):
                    problems.append("%s: brace %r" % (t["id"], line))
    for p in problems:
        print("STYLE: " + p)
    return not problems


for grievance in GRIEVANCES:
    build(grievance)
ok = check()
with open(OUT, "w", encoding="utf-8") as f:
    f.write('{"_comment": "Venting (generated by tools/talk_gen/venting.py, do not edit by hand): one Drifter unloads, the '
            'other listens its own way (comfort, its own bad story, advice, a joke, skill issue, help, a hermit\'s shrug, a '
            'friend\'s ear), situational ones and two-part rants. Check with: python tools/check_talk_web.py .",\n'
            '"topics": [\n')
    f.write(",\n".join(json.dumps(t, ensure_ascii=True) for t in topics))
    f.write("\n]}\n")
print(len(GRIEVANCES), "grievances,", len(topics), "topics" + ("" if ok else " (STYLE problems above)"))
