"""Writes drifter_talk/topics_gen_world.json: the world, subject by subject, from small talk to debate.

The user: "more topics for each subject matter (from small talks to debate)". Every mob, biome, structure, the weather
and the sky, and the day/night cycle (Minecraft 1.21.1 facts), each with a ladder of topics (ids g_world_<subject>_<rung>):
  see    a passing mention ("saw a fox earlier"), lower weight
  ask    a question, answered with the line's own answers (then)
  like   why one likes it          hate   why one doesn't
  tip    advice from one who knows story  a short story (one part)
  feel   a feeling (fear of it, love of it)
  take   a mild disagreement (agree/disagree marks)
  debate a full debate on a claim (agree/disagree, then each side answers)
A rung is skipped when its subject doesn't give the fields for it.

Usage: python tools/talk_gen/world.py src/main/resources/data/drifter/drifter_talk/topics_gen_world.json
Then:  python tools/check_talk_web.py . --quiet

The subjects are written in a small text format (DATA), one block per subject:
  === key | category tags | extra leads | tone (good, bad or meh) | about
  field: line | line | line
key is the subject's own tag (all its topics share it, so a talk can climb its ladder). Fields:
  see re                      the mention and the other's reactions
  q                           "question > answer / answer", one question per q line
  like why likere             a like, its reasons, the other's replies
  hate hatere                 a dislike and the other's replies
  tip                         tips (half in the first turn, half in the second)
  setup climax storyre coda   a story: every setup fits every climax, every climax every coda
  feel feelre feelend         a feeling, the other's reply, a's last word
  op op+ op-                  a mild claim, a specific agree, a specific disagree
  deb deb+ deb- deb++ deb-- debx  a debate claim; b agrees (with why) or disagrees (with why); a builds on the agree
                              or answers the disagree; b's last word after a's answer
  need.<rung>: conditions     (space-separated) extra needs for one rung
  gate: area                  only one who knows that part of the game (knows_<area>) brings it up or answers it;
                              nether and end subjects are gated on nether/end by their tags
"""
import json
import sys

DATA = []

# Generic reactions (they may repeat across topics; every subject-specific line is the subject's own).
CLOSE = ["yeah", "lol", "mhm", "anyway", "yep", "just saying"]
SEE_RE = {
    "good": ["aw", "nice", "cute", "love that", "lucky", "oh nice"],
    "bad": ["yikes", "ugh", "oof", "nope", "careful", "not a fan"],
    "meh": ["huh", "cool", "neat", "oh nice", "lol", "ok"],
}
ASK_PLACEHOLDER = ["not sure tbh"]
ASK_THX = {"any": ["oh ok", "good to know", "huh", "makes sense", "ah ok", "noted", "ok cool"],
           "newbie": ["ohh", "wait really", "ok writing that down", "you know so much"],
           "speedrunner": ["k", "noted"], "hermit": ["hm", "ok"]}
LIKE_RE = ["same", "fair", "honestly same", "i get that", "lol ok", "can't argue with that"]
FAIR = ["fair", "true", "lol", "makes sense", "fair enough", "valid"]
HATE_RE = ["fair", "same", "i get that", "lol"]
HATE_CLOSE = ["lol", "ugh", "yeah", "eh", "just saying"]
TIP_RE = {"any": ["good to know", "huh, didn't know that", "noted", "oh nice", "ok that's useful", "really"],
          "newbie": ["wait really", "omg ty", "ok writing that down", "i had no idea"],
          "speedrunner": ["known", "yep"], "hermit": ["hm", "ok"], "pvp_sweat": ["knew that", "obviously"]}
THANKS = {"any": ["thanks", "appreciate it", "ty", "i'll remember that", "good tips"],
          "newbie": ["thank you!!", "you're the best", "ty ty"], "hermit": ["ok"], "speedrunner": ["k"],
          "pvp_sweat": ["sure", "yeah yeah"], "helper": ["thanks, i'll pass it on"]}
GO = {"any": ["go on", "uh oh", "oh no", "and?", "what happened", "lol go on", "i'm listening"],
      "newbie": ["omg what happened", "and then??"], "hermit": ["and", "hm"], "speedrunner": ["and", "get to the point"]}
STORY_RE = {
    "good": ["lol nice", "no way", "love that", "best story", "lucky"],
    "bad": ["oof", "rip", "nooo", "brutal", "lmao", "unlucky"],
    "meh": ["lol", "no way", "classic", "of course", "lmao"],
}
AGREE = ["true", "agreed", "fair", "yeah honestly", "same"]
DISAGREE = ["nah", "eh, not really", "disagree", "hmm no"]
SEE_AGREE = ["see", "exactly", "right", "glad someone gets it", "thank you"]
FAIR_DIS = ["fair", "each their own", "ok ok", "agree to disagree", "we'll see", "suit yourself"]
HOLD = ["still not convinced", "nah, still", "you won't change my mind", "maybe, but still no", "i hear you, still no"]
BOTH = ["exactly", "100%", "glad we agree", "facts", "couldn't have said it better"]
DEB_END = ["we'll see", "whatever you say", "you'll come around", "fine, agree to disagree", "your loss"]

RUNG_TAG = {"like": "opinion", "hate": "opinion", "tip": "advice", "story": "stories", "feel": "feelings",
            "take": "opinion", "debate": "argument"}
RUNG_LEAD = {"tip": "advice", "story": "stories", "feel": "feelings", "debate": "argument"}
AREAS = {"trading", "enchanting", "nether", "brewing", "raids", "redstone", "anvils", "fortresses", "bartering", "end",
         "trial_chambers", "netherite", "warden", "dragon", "elytra", "beacons"}


def parse(text):
    subjects = []
    cur = None
    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        if line.startswith('=== '):
            parts = [p.strip() for p in line[4:].split('|')]
            if len(parts) != 5:
                raise ValueError('bad header: ' + line)
            key, cats, leads, tone, about = parts
            if tone not in SEE_RE:
                raise ValueError('bad tone: ' + line)
            cur = {'key': key, 'cats': [c for c in cats.split(',') if c], 'leads': [c for c in leads.split(',') if c],
                   'tone': tone, 'about': about, 'f': {}, 'q': [], 'need': {}, 'gate': None}
            subjects.append(cur)
            continue
        name, sep, value = line.partition(': ')
        if not sep or cur is None:
            raise ValueError('bad line %d: %s' % (number, line))
        if name == 'q':
            question, sep, answers = value.partition(' > ')
            if not sep:
                raise ValueError('q without answers: ' + line)
            cur['q'].append((question.strip(), [a.strip() for a in answers.split(' / ')]))
        elif name.startswith('need.'):
            cur['need'][name[5:]] = value.split()
        elif name == 'gate':
            cur['gate'] = value.strip()
        else:
            cur['f'].setdefault(name, []).extend(s.strip() for s in value.split(' | ') if s.strip())
    return subjects


def turn(by, lines, chance=None, cond=None, mark=None):
    t = {"by": by, "lines": lines}
    if chance is not None:
        t["chance"] = chance
    if cond:
        t["if"] = cond
    if mark:
        t["mark"] = mark
    return t


def dedupe(items):
    out = []
    for item in items:
        if item not in out:
            out.append(item)
    return out


def build(subjects):
    topics = []
    for s in subjects:
        key, f, tone = s['key'], s['f'], s['tone']
        cats = s['cats'][:2]
        area = s['gate'] or ('nether' if 'nether' in cats else 'end' if 'end' in cats else None)
        if area and area not in AREAS:
            raise ValueError('%s: unknown area %s' % (key, area))
        base_leads = dedupe([key] + cats[:1] + s['leads'])[:3]

        def has(*names):
            return all(f.get(n) for n in names)

        def topic(rung, turns, effects=None):
            needs = list(s['need'].get(rung, []))
            situational = bool(needs)
            if area:
                if rung == 'ask':
                    needs.append('b:knows_' + area)
                elif rung in ('take', 'debate'):
                    needs += ['a:knows_' + area, 'b:knows_' + area]
                else:
                    needs.append('a:knows_' + area)
            tags = dedupe([key] + cats + ([RUNG_TAG[rung]] if rung in RUNG_TAG else []))[:4]
            leads = dedupe(base_leads + ([RUNG_LEAD[rung]] if rung in RUNG_LEAD else []))[:4]
            weight = (1.5 if situational else 0.7) if rung == 'see' else (1.3 if situational else 1.0)
            t = {"id": "g_world_%s_%s" % (key, rung), "weight": weight}
            if needs:
                t["needs"] = needs
            t["tags"] = tags
            t["leads"] = leads
            t["about"] = s['about']
            t["turns"] = turns
            if effects:
                t["effects"] = effects
            topics.append(t)

        if has('see', 're'):
            topic('see', [turn("a", f['see']), turn("b", f['re'] + SEE_RE[tone]), turn("a", CLOSE, chance=0.5)])
        if s['q']:
            qs = [{"line": q, "then": answers} for q, answers in s['q']]
            first, more = qs[:2], qs[2:]
            turns = [turn("a", first), turn("b", ASK_PLACEHOLDER), turn("a", ASK_THX)]
            if more:
                turns += [turn("a", more, chance=0.6, mark=["more"]), turn("b", ASK_PLACEHOLDER, cond=["t:more"]),
                          turn("a", THANKS, chance=0.8, cond=["t:more"])]
            topic('ask', turns, ['learn_' + area] if area else None)
        if has('like', 'why'):
            topic('like', [turn("a", f['like']), turn("b", f.get('likere', []) + LIKE_RE), turn("a", f['why'], chance=0.8),
                           turn("b", FAIR, chance=0.5)])
        if has('hate', 'hatere'):
            topic('hate', [turn("a", f['hate']), turn("b", f['hatere'] + HATE_RE), turn("a", HATE_CLOSE, chance=0.5)])
        if has('tip'):
            tips = f['tip']
            half = (len(tips) + 1) // 2
            turns = [turn("a", tips[:half]), turn("b", TIP_RE)]
            if tips[half:]:
                turns += [turn("a", tips[half:], chance=0.7)]
            turns += [turn("b", THANKS, chance=0.6)]
            topic('tip', turns, ['teach_' + area] if area else None)
        if has('setup', 'climax'):
            turns = [turn("a", f['setup']), turn("b", GO), turn("a", f['climax']),
                     turn("b", f.get('storyre', []) + STORY_RE[tone])]
            if f.get('coda'):
                turns.append(turn("a", f['coda'], chance=0.7))
            topic('story', turns)
        if has('feel', 'feelre'):
            turns = [turn("a", f['feel']), turn("b", f['feelre'])]
            if f.get('feelend'):
                turns.append(turn("a", f['feelend'], chance=0.6))
            topic('feel', turns)
        if has('op', 'op-'):
            topic('take', [turn("a", f['op']),
                           turn("b", f.get('op+', []) + AGREE, chance=0.5, mark=["agree"]),
                           turn("b", f['op-'] + DISAGREE, cond=["!t:agree"], mark=["disagree"]),
                           turn("a", SEE_AGREE, chance=0.6, cond=["t:agree"]),
                           turn("a", FAIR_DIS, cond=["t:disagree"])])
        if has('deb', 'deb+', 'deb-', 'deb++', 'deb--'):
            topic('debate', [turn("a", f['deb']),
                             turn("b", f['deb+'], chance=0.45, mark=["agree"]),
                             turn("b", f['deb-'], cond=["!t:agree"], mark=["disagree"]),
                             turn("a", f['deb++'], cond=["t:agree"]),
                             turn("a", f['deb--'], cond=["t:disagree"]),
                             turn("b", BOTH, chance=0.5, cond=["t:agree"]),
                             turn("b", f.get('debx', []) + HOLD, cond=["t:disagree"]),
                             turn("a", DEB_END, chance=0.5, cond=["t:disagree"])])
    return topics


def lines_of(lines):
    groups = [lines] if isinstance(lines, list) else list(lines.values())
    out = []
    for group in groups:
        for line in group:
            if isinstance(line, dict):
                out.append(line['line'])
                out += line['then']
            else:
                out.append(line)
    return out


def check(subjects, topics):
    """Line length, plain ASCII, no fill words; prints lines said in two of this file's topics, and short subjects."""
    problems = 0
    seen = {}
    for t in topics:
        for tr in t['turns']:
            for line in lines_of(tr['lines']):
                if len(line) > 90 or not line.isascii() or '{' in line or line != line.strip():
                    print('BAD LINE (%s): %r' % (t['id'], line))
                    problems += 1
                if len(line) > 12:
                    seen.setdefault(line, set()).add(t['id'])
    generic = set()
    for pool in (CLOSE, ASK_PLACEHOLDER, LIKE_RE, FAIR, HATE_RE, HATE_CLOSE, AGREE, DISAGREE, SEE_AGREE, FAIR_DIS, HOLD,
                 BOTH, DEB_END, *SEE_RE.values(), *STORY_RE.values(), *ASK_THX.values(), *TIP_RE.values(),
                 *THANKS.values(), *GO.values()):
        generic.update(pool)
    repeats = [(line, ids) for line, ids in seen.items() if len(ids) > 1 and line not in generic]
    for line, ids in sorted(repeats):
        print('repeated: %r in %s' % (line, ', '.join(sorted(ids))))
    counts = {}
    for t in topics:
        counts[t['id'].rsplit('_', 1)[1]] = counts.get(t['id'].rsplit('_', 1)[1], 0) + 1
    print('rungs:', counts)
    for s in subjects:
        n = sum(1 for t in topics if t['id'].startswith('g_world_%s_' % s['key']) and t['id'][len('g_world_%s_' % s['key']):] in
                ('see', 'ask', 'like', 'hate', 'tip', 'story', 'feel', 'take', 'debate'))
        if n < 7:
            print('short subject: %s (%d topics)' % (s['key'], n))
    keys = [s['key'] for s in subjects]
    for k in set(keys):
        if keys.count(k) > 1:
            print('DUPLICATE SUBJECT: ' + k)
            problems += 1
    return problems


def main(out):
    subjects = parse('\n'.join(DATA))
    topics = build(subjects)
    problems = check(subjects, topics)
    with open(out, 'w', encoding='utf-8') as fh:
        json.dump({"_comment": "Generated by tools/talk_gen/world.py (don't edit by hand): the world, subject by subject, from "
                               "small talk to debate (every mob, biome, structure, the weather and sky, the day/night "
                               "cycle; Minecraft 1.21.1). Per subject: a mention, a question, a like, a dislike, a tip, a "
                               "story, a feeling, a mild disagreement, a debate. Nether and End subjects are tagged so only "
                               "one who knows them brings them up.",
                   "topics": topics}, fh, indent=1, ensure_ascii=True)
    print('%d subjects, %d topics%s' % (len(subjects), len(topics), ', %d BAD LINES' % problems if problems else ''))


# ---------------------------------------------------------------------------------------------------------------------
# The subjects.
# ---------------------------------------------------------------------------------------------------------------------

# --- Hostile mobs of the overworld.
DATA.append(r'''
=== zombie | mobs,night | danger,fighting | bad | zombies
see: zombies are out early tonight | heard a zombie groaning outside my door | a baby zombie chased me halfway home | killed four zombies on the way over here
re: they never shut up | the baby ones are the worst | at least they burn at sunrise | more rotten flesh for nobody
q: what do zombies drop? > rotten flesh mostly, sometimes iron or a carrot / rotten flesh, and once in a while an iron ingot
q: can zombies break doors? > on hard they can, the wooden ones / only on hard, iron doors are safe
q: why didn't that zombie burn in the sun? > it had a helmet on / it was wearing a hat, they don't burn then
q: can you cure a zombie villager? > weakness splash, then a golden apple / throw weakness at it and feed it a golden apple
like: zombies are kind of my favourite mob | i like fighting zombies honestly | zombies are the honest mob
why: they're slow and you can see them coming | no tricks, they just walk at you | they're easy xp and they barely fight back
likere: they're easy at least | until a horde shows up | lol you would
hate: i hate baby zombies so much | zombies at my door every night, i'm sick of it | the groaning keeps me up at night
hatere: light up around your house | get an iron door | the babies are tiny and fast, i know | a fence keeps them back
tip: zombies burn in daylight unless they wear a helmet | baby zombies are fast and small, aim low | zombies can't break iron doors | a zombie that sits in water long enough turns into a drowned | zombies go for turtle eggs, fence your beach
setup: so last night i left my door open for one second | ok so i went to grab something at dusk and left the door open | story time: i forgot to close my door last night
climax: and a baby zombie ran right in | and a baby zombie was in my house before i turned around | and a tiny zombie sprinted straight past me inside
storyre: of course it was a baby | the babies are so fast | small and evil
coda: chased it round my house for ages | it hit me like ten times before i got it | i close my door every time now
feel: zombies don't scare me but the groaning does | i get nervous when it's dark and i hear a zombie | baby zombies kind of scare me ngl
feelre: they're all groan and no bite | stay inside at night then | the groan is spooky ngl | you'll be fine, they're slow
feelend: easy for you to say | i guess | still | ok
op: zombies are the easiest hostile mob | zombies are harmless if you have a sword | zombies are just xp that walks at you
op+: true, they're so slow | nothing simpler than a zombie
op-: not in a horde | baby zombies say otherwise | try that on hard
deb: zombies are more dangerous than creepers | honestly zombies are worse than creepers
deb+: they come in groups and never stop | true, a horde wears you down
deb-: creepers blow up your house, zombies don't | nah, zombies are slow, creepers sneak up
deb++: and the babies are fast | a creeper is one boom, zombies keep coming
deb--: a horde at night will kill you before any creeper | on hard they break your door down | you're forgetting zombie sieges
debx: one creeper took out my whole wall | a hole in my wall beats a groan

=== husk | mobs,biomes | danger,exploring | bad | husks
see: husks all over the desert tonight | a husk followed me out of the dunes | got hit by a husk and now i'm starving | the desert had husks walking around in broad daylight
re: they don't even burn | husks make you so hungry | deserts are full of them | sandy zombies, great
q: why don't husks burn in the sun? > they're desert zombies, the sun doesn't bother them / they're made for the desert, no burning
q: what's the difference between a husk and a zombie? > husks don't burn and their hits make you hungry / same thing, but they make you hungry and walk in daylight
q: what happens if a husk goes in water? > it turns into a normal zombie / it drowns into a regular zombie, then a drowned
like: i kind of like husks | husks are a cool mob honestly | i like husks more than zombies
why: they fit the desert so well | the sandy look is nice | at least they look different
likere: weird pick | they look cool i guess
hate: husks are the worst thing about the desert | i hate husks, daytime should be safe | husks drain my food bar every time
hatere: bring extra food to the desert | that's the desert tax | at least they're slow
tip: husks don't burn, so the desert isn't safe in the day | a husk hit gives you hunger, pack extra food | push a husk into water and it turns into a zombie | husks only spawn in deserts, out under the open sky
setup: so i was crossing the desert at noon thinking i was safe | ok so it's broad daylight and i'm walking through the dunes | story time: me, the desert, high noon, no armor
climax: and three husks just walked out from behind a dune | and a husk came over a dune and smacked me | and husks started showing up like it was midnight
storyre: they don't care about the sun | desert things
coda: lost half my food to the hunger effect | ran all the way to the river | now i treat the desert like night
feel: husks make me nervous because i can't count on daytime | deserts freak me out because of husks | husks honestly scare me more than zombies
feelre: just bring food and a sword | stay out of the desert then | i get that, daylight should mean safe
feelend: yeah | it's the principle | lol
op: husks are worse than regular zombies | husks are the scariest kind of zombie | husks ruin the desert
op+: no burning is a big deal | the hunger hit is nasty
op-: they're just zombies with a tan | they're slow like the rest | the hunger thing is mild
deb: husks are the most annoying mob in the overworld
deb+: zombies that don't burn and eat your food, yeah | the hunger is so annoying
deb-: phantoms exist | nah, they only live in deserts
deb++: and they're out all day | you can't even escape them in the morning
deb--: one trip through a desert changes your mind | the desert is huge though | phantoms you can sleep away, husks you can't
debx: just avoid the desert | i'd still take a husk over a phantom

=== drowned | mobs,ocean | danger,fishing | bad | drowned
see: drowned in the river again | a drowned threw a trident at me from the water | saw a drowned holding a nautilus shell | the beach had drowned walking out of the waves
re: tridents are scary | they love rivers | the trident ones are a pain | swimming at night is a no
q: where do you get a trident? > drowned, only the ones holding one / kill a drowned that's holding one, rare drop
q: what do drowned drop? > rotten flesh and copper sometimes / rotten flesh, copper ingots, sometimes whatever they hold
q: how do zombies become drowned? > leave a zombie underwater for a while / a zombie drowns and turns into one
like: i like drowned, they're kind of cool | drowned are a cool mob idk | i like fighting drowned
why: they drop copper | the trident ones are worth it | the ocean needs a monster
likere: brave | you like soggy zombies
hate: i hate drowned so much | drowned ruin every swim | drowned keep sniping me from rivers
hatere: stay out of the water at night | bring a shield | the trident ones hurt
tip: only drowned holding a trident can drop one | drowned can drop nautilus shells | they come up on land at night | they hunt baby turtles, keep them away | they burn in the sun unless they're underwater
setup: so i'm crossing a river in my boat at night | ok so i was rowing home on the river after dark | story time: me, a boat, a river, nighttime
climax: and a trident flies out of the water and nearly takes my head off | and a drowned started throwing tridents at me | and a drowned surfaced next to the boat with a trident
storyre: trident drowned are no joke | rivers at night are a trap
coda: i rowed so fast | never got the trident though | i take the bridge now
feel: drowned scare me, i can't see them coming | deep water freaks me out because of drowned | drowned are nightmare fuel
feelre: stay near the shore | they're slow on land at least | yeah, things in the water are creepy
feelend: i'll stick to bridges | still | ugh
op: drowned are worse than zombies | the river is more dangerous than the forest at night | drowned are the most annoying mob to fight
op+: tridents hurt a lot | can't fight back well in water
op-: on land they're just zombies | they're easy from a boat | stay out of the water, problem solved
deb: tridents are only worth it if you farm drowned
deb+: true, one drop takes forever | yeah, they're so rare
deb-: nah, one lucky drop is enough | you only need one trident
deb++: and you want loyalty and riptide on it anyway | otherwise you're fishing for one drop for weeks
deb--: one trident with loyalty lasts forever | i got mine on my second drowned | mending fixes everything
debx: lucky you | still too rare for me

=== skeleton | mobs,night | danger,fighting | bad | skeletons
see: skeletons sniping from the trees tonight | got shot by a skeleton from way off | saw a skeleton burning in the sunrise | there's a skeleton under the big oak
re: they have perfect aim | free arrows though | burning skeletons are the best sight | shields fix them
q: what do skeletons drop? > bones and arrows, sometimes their bow / bones and arrows, good for bone meal
q: how do i stop getting shot? > use a shield, or fight around corners / shield up, and break line of sight
q: what makes a stray? > a skeleton freezing in powder snow / skeletons stuck in powder snow turn into strays
like: skeletons are my favourite mob to fight | i like skeletons, they're a real fight | honestly skeletons are fun
why: they make you think about cover | free arrows and bones | bones mean bone meal for my farm
likere: they're annoying though | bones are nice
hate: skeletons are so annoying | i hate skeletons knocking me off ledges | skeletons in the dark get me every time
hatere: rush them | wolves go after them | a shield helps a lot
tip: skeletons run from wolves | they burn in the sun unless they're in shade | bones make bone meal, great for crops | dungeons with a skeleton spawner make good xp farms | a shield stops all their arrows
setup: so i was building on a cliff at dusk | ok so i'm up on a ledge putting blocks down at night | story time: cliff, night, me building
climax: and a skeleton shot me right off the edge | and an arrow knocked me off the cliff | and a skeleton i couldn't even see shot me off
storyre: knockback is evil | classic skeleton
coda: landed in water, somehow alive | i fell and lost half my hearts | i build with walls up now
feel: the sound of a skeleton's bow gives me anxiety | skeletons make me nervous at night | i hate hearing rattling bones in a cave
feelre: shield up and you're fine | the rattle is creepy | i jump at it too
feelend: yeah | still spooky | ugh
op: skeletons are the most dangerous basic mob | skeletons are harder than zombies | skeletons have better aim than me
op+: they hit from so far | the knockback is the worst part
op-: just use a shield | they're easy up close | creepers are worse
deb: skeletons are worse than creepers
deb+: creepers you can hear, skeletons hit from range | true, they shoot from cover
deb-: a creeper takes your whole wall | skeletons are a bit of damage, creepers are a disaster
deb++: and they knock you off cliffs | a creeper is one fight, skeletons keep shooting
deb--: skeletons kill more people off ledges | creepers are rare, skeletons are everywhere | creepers you can see coming
debx: a creeper ruined my house though | i still fear the hiss more

=== stray | mobs,biomes | danger,fighting | bad | strays
see: strays out in the snow again | a stray hit me with a slowness arrow | saw a stray by the ice spikes | strays all over the snowy plains last night
re: slowness arrows are mean | snow skeletons, great | cold places have the worst skeletons
q: what's a stray? > a frozen skeleton from snowy places / a skeleton that shoots slowness arrows
q: what do strays drop? > bones, arrows, sometimes slowness arrows / bones and slowness tipped arrows
like: strays look really cool | i like strays, they look like ice skeletons | strays are the best looking skeleton
why: the ragged look is great | slowness arrows are worth having | they look like they've been out there for ages
likere: they look creepy | sure
hate: i hate strays and their slowness arrows | strays make snowy places miserable | strays are the reason i don't live in the snow
hatere: shield, always | keep moving | fair, the slowness is brutal
tip: strays shoot slowness arrows, bring a shield | a skeleton stuck in powder snow turns into a stray | strays drop tipped arrows sometimes, keep them | they spawn in snowy plains and ice spikes
setup: so i was walking across the snowy plains at night | ok so i'm out on the ice at night | story time: snowy plains, dark, me lost
climax: and a stray hit me with slowness and two more showed up | and i got slowed and then a whole group of strays came | and a stray slowed me so much i couldn't run
storyre: slowness is nasty | snowy nights are a trap
coda: walking like a snail with arrows flying | barely made it to an igloo | stayed in that igloo till morning
feel: strays creep me out, they look so empty | i'm a bit scared of strays, the slowness is awful | snowy nights make me uneasy because of strays
feelre: they're just skeletons in rags | stay near light | the slowness is scary yeah
feelend: still | i'll stay home | yeah
op: strays are worse than skeletons | strays are the most annoying archer | slowness arrows are unfair
op+: slowed and shot, terrible | the slowness makes it worse
op-: they're just as easy with a shield | they only live in the snow | slowness wears off fast
deb: strays are scarier than regular skeletons
deb+: slowness means you can't run | yeah, being slowed is the worst
deb-: nah, they're skeletons with a coat | regular skeletons are everywhere, strays aren't
deb++: and the snow already slows you | plus powder snow, you're stuck
deb--: get slowed in a snowstorm once and you'll see | being slowed when three of them are shooting is awful | rare doesn't mean easy
debx: still a skeleton | skeletons are worse because they're everywhere

=== bogged | mobs,biomes | danger,fighting | bad | bogged
see: saw a bogged in the swamp | a bogged hit me with a poison arrow | bogged in the trial chamber again | there's a mossy skeleton in the mangroves
re: poison arrows are mean | mossy skeletons, gross | swamps have everything bad
q: what's a bogged? > a mossy skeleton from swamps, poison arrows / a swamp skeleton, shoots poison
q: where do bogged spawn? > swamps, mangroves and trial chambers / swamps and trial chamber spawners
q: can you shear a bogged? > yeah, you get mushrooms / yes lol, they drop mushrooms
like: i like bogged, they look so cool | bogged are a sick mob | i like the mossy skeleton guys
why: the mushrooms on them are great | they fit the swamp | they shoot slower than skeletons
likere: they look gross | fair, they look cool
hate: i hate bogged | bogged poison is the worst | bogged made the trial chamber hell
hatere: shield and milk | they have less health at least | poison can't kill you
tip: bogged shoot poison arrows, bring milk | they shoot slower than normal skeletons | shear a bogged for mushrooms | they show up in swamps, mangroves and trial chambers
setup: so i'm in a trial chamber fighting a spawner | ok so i was clearing a trial chamber room | story time: trial chamber, me, a spawner
climax: and it started spawning bogged one after another | and the spawner kept sending bogged with poison arrows | and a wave of bogged showed up
storyre: poison stacking is awful | trial spawners don't mess around
coda: poisoned the whole fight | i got the key though | drank so much milk
feel: bogged give me the creeps | the swamp is scary with bogged around | i hate the rattle bogged make
feelre: they're just mossy skeletons | swamps are creepy yeah | stay out at night
feelend: still | lol | yeah
op: bogged are worse than strays | bogged are the worst skeleton | poison beats slowness for evil
op+: poison stacks up fast | poison is meaner
op-: they shoot slow | slowness is worse | they're easier to kill
deb: bogged are the hardest skeleton to fight
deb+: poison arrows hurt over time | true, the poison adds up
deb-: they have less health | they shoot slower than skeletons
deb++: and they come in waves in trial chambers | plus they hide in swamp water and roots
deb--: try a trial spawner full of them | the poison lasts after the fight | slower still hurts
debx: strays are worse | i'll take poison over slowness

=== creeper | mobs,night | danger,building | bad | creepers
see: a creeper blew a hole in the hill by my house | heard a hiss behind me earlier | creeper's lurking by the farm | almost got blown up by a creeper today
re: that hiss is the worst sound | they always sneak up | cats keep them away | gunpowder though
q: what do creepers drop? > gunpowder, and a disc if a skeleton kills it / gunpowder mostly
q: how do i keep creepers away? > get a cat, they run from cats / cats, or light everything up
q: what's a charged creeper? > one that got hit by lightning, bigger boom / a creeper struck by lightning, it blows up way harder
q: can a creeper drop a mob head? > if a charged creeper blows another mob up / only from a charged creeper explosion
like: i kind of like creepers | creepers are the best mob design | creepers are iconic
why: tnt needs gunpowder | they keep you on your toes | the game wouldn't be the same without them
likere: they're the worst | you're crazy | gunpowder is nice
hate: i hate creepers so much | creepers ruin everything i build | creepers are the worst mob, period
hatere: get a cat | light up your base | build in stone | they're terrible, yeah
tip: creepers are scared of cats and ocelots | hit and back off, a creeper stops hissing when you're far | a creeper killed by a skeleton drops a music disc | lightning makes charged creepers, they blow up way bigger | stone takes a creeper blast better than wood
setup: so i was finishing my house and looking at it from outside | ok so i just finished my house and stepped back to admire it | story time: my house was finally done
climax: and a creeper walked up and blew up the front wall | and i hear a hiss and half my house is gone | and a creeper took out my door and my chests
storyre: they love fresh builds | creepers hate art
coda: rebuilt it in stone | i got a cat the next day | still mad about it
feel: the hiss makes my heart stop every time | i'm honestly scared of creepers | creepers give me trust issues with quiet places
feelre: same, that hiss | get a cat | that sound gets everyone
feelend: one day i'll be brave | yeah | i flinch at every hiss now
op: creepers are the worst mob in the game | creepers are worse than spiders | creepers are the reason i build in stone
op+: they wreck builds | the sneak up is unfair
op-: they're easy if you listen | cats solve them | phantoms are worse
deb: creepers are the most iconic mob in the game
deb+: everyone knows the creeper | yeah, it's the face of the game
deb-: zombies are more iconic | endermen are cooler
deb++: green, sad face, boom | there's no mob more known
deb--: everyone's scared of the hiss, that's iconic | nobody fears an enderman like a creeper | they changed how everyone builds
debx: still zombies | iconic, sure, but i hate them

=== spider | mobs,night | danger,fighting | bad | spiders
see: spiders climbing my walls again | a spider dropped on me from a tree | saw a spider just sitting there in the daytime | got jumped by a spider on the way home
re: they climb everything | the eyes in the dark | string though | they're chill in the daylight
q: what do spiders drop? > string and spider eyes / string, and spider eyes for potions
q: are spiders dangerous in the day? > not unless you hit them / they're neutral in daylight, leave them alone
q: how do i stop spiders climbing my walls? > put an overhang on the top / a lip at the top of the wall stops them
like: i don't mind spiders | spiders are kind of cute | i like spiders honestly
why: they leave you alone in the day | string for bows and wool | they're polite in daylight
likere: that's weird | string is nice
hate: i hate spiders | spiders climbing walls is so unfair | spider jumps get me every time
hatere: overhangs on your walls | they're chill in daylight at least | string makes up for it
tip: spiders can't climb past an overhang | in daylight spiders leave you alone | string makes bows and wool | sometimes a skeleton rides a spider, kill the spider first | spider eyes go into fermented spider eye for potions
setup: so i was standing on my wall at night | ok so i'm up on my wall looking out at night | story time: me, my wall, nighttime
climax: and a spider just climbed straight up and jumped me | and a spider crawled up the wall right into my face | and a spider with a skeleton riding it climbed up
storyre: they climb anything | spiders at night, nope
coda: added an overhang after that | fell off the wall | now there's a lip on every wall i build
feel: spiders give me the creeps | i'm scared of spiders, those red eyes | spiders crawling up walls freaks me out
feelre: same, the eyes | they're chill in the day | overhangs help
feelend: still creepy | ugh | yeah
op: spiders are the most annoying mob | spiders are scarier than zombies | spiders climbing walls is unfair
op+: walls mean nothing to them | the jump is scary
op-: overhangs fix it | they're chill half the day | they're easy to kill
deb: spiders are harmless if you light your base
deb+: lit up and walled, they're nothing | true, they're easy
deb-: they climb your walls anyway | spider jockeys exist
deb++: and in the day they won't even bother you | light plus an overhang and done
deb--: one spider jockey changes your mind | caves are full of them | they spawn in the dark outside your light
debx: light fixes most | still harmless enough

=== cave_spider | mobs,caves | danger,structures | bad | cave spiders
see: found a cave spider spawner in a mineshaft | got poisoned by a cave spider today | cave spiders everywhere in that mineshaft | so many cobwebs, cave spider spawner for sure
re: poison is the worst | mineshafts are cursed | cobwebs mean trouble | they're so tiny
q: where do cave spiders come from? > spawners in mineshafts / only from mineshaft spawners, look for cobwebs
q: what's the deal with cave spiders? > tiny, fast, and they poison you / they poison you and fit through tiny gaps
q: how do i turn off a spawner? > light around it with torches / put torches on and around it
like: cave spiders are cute honestly | i like cave spiders, they're tiny | cave spider spawners are great
why: they're tiny and blue | spawners mean xp | the mineshaft loot is worth the poison
likere: they poison you | small things can be cute i guess
hate: cave spiders are the worst | i hate cave spiders and their poison | cave spiders ruin mineshafts
hatere: torch the spawner | bring milk | they're tiny and mean, yeah
tip: cave spiders fit through one block gaps | their bite poisons you, carry milk | torches around a spawner stop it | cobwebs in a mineshaft mean a cave spider spawner nearby | a sword cuts cobwebs fast
setup: so i was exploring a mineshaft | ok so i'm deep in a mineshaft, cutting cobwebs | story time: mineshaft, me, too many cobwebs
climax: and i walked right into a cave spider spawner | and like eight cave spiders came out of the webs | and a cave spider poisoned me down to half a heart
storyre: the cobwebs were a warning | poison is evil
coda: torched the spawner and ran | i had no milk, worst part | now i carry milk everywhere
feel: cave spiders scare me more than anything | i get nervous in mineshafts because of cave spiders | the tiny spiders freak me out
feelre: bring torches | they're scary yeah | milk and a sword and you're fine
feelend: maybe | still | ok
op: cave spiders are worse than regular spiders | cave spiders are the worst thing in mineshafts | the poison makes them evil
op+: poison is nasty | they fit everywhere
op-: they only come from spawners | regular spiders are everywhere | they have less health
deb: mineshafts aren't worth it because of cave spiders
deb+: poison and webs, no thanks | true, they're a nightmare
deb-: the rails and chests are worth it | nah, torch the spawner and loot
deb++: and you get lost too | plus the webs slow you down
deb--: name tags, golden apples, all in those chests | one torch and the spawner's done | lots of free wood too
debx: still not going in | the loot is meh

=== enderman | mobs,night | danger,exploring | meh | endermen
see: an enderman took a block out of my yard | saw an enderman carrying grass | almost looked an enderman in the eye earlier | endermen everywhere in the plains tonight
re: don't look at them | they love grass blocks | they're so creepy | pearls though
q: what happens if you look at an enderman? > it gets mad and teleports at you / it screams and comes for you
q: how do i fight an enderman? > stand under a two block ceiling, they can't fit / get under something two tall, or stand in water
q: what do endermen drop? > ender pearls / pearls, for eyes of ender and teleporting
q: do endermen hate water? > yeah, water hurts them / water and rain hurt them, they teleport away
like: endermen are my favourite mob | i love endermen | endermen are cool honestly
why: they just walk around minding their business | they move blocks around, it's funny | pearls are useful
likere: until you look at one | they're creepy though | pearls are nice
hate: i hate endermen | endermen stealing blocks from my yard | endermen are terrifying
hatere: don't look at them | build with stone, they can't take it | carve a pumpkin and wear it
tip: a carved pumpkin on your head stops endermen getting mad | they can't fit under a two block high ceiling | water hurts them, stand in it | they can't take stone, only grass, dirt and stuff | arrows don't hit them, they teleport away
setup: so i was walking through the plains at night | ok so i'm crossing a field at night minding my business | story time: field, night, me not paying attention
climax: and i looked up right at an enderman | and i accidentally looked an enderman in the face | and an enderman caught me looking at it
storyre: never look up | the scream gets me every time
coda: jumped in a pond and it gave up | dug a hole two blocks deep and fought it there | got a pearl out of it though
feel: endermen scare me so much | that scream makes my blood run cold | i'm scared to look up at night because of endermen
feelre: don't look at them and you're fine | wear a pumpkin | the scream is awful
feelend: easy for you to say | ok | still
op: endermen are the creepiest mob | endermen are the coolest mob | endermen are scarier than creepers
op+: the scream is scary | the way they stare
op-: they're harmless if you don't look | they're just tall and shy | creepers are worse
deb: endermen are the best mob in the game
deb+: neutral, cool and they drop pearls | yeah, they're so unique
deb-: they steal blocks from my yard | they're terrifying, no
deb++: and they're in every dimension | plus no pearls, no eyes of ender
deb--: they only take dirt and grass | build in stone and they can't touch it | they're only mean if you stare
debx: they took my grass path | still creepy
''')

DATA.append(r'''
=== witch | mobs,night | danger,potions | bad | witches
see: a witch was throwing potions at me last night | saw a witch by the swamp hut | heard a witch cackling in the dark | a witch drank a potion right in front of me
re: witches are cheaters | they heal mid fight | the swamp hut one never leaves | that laugh is creepy
q: what do witches drop? > redstone, glowstone, sugar, bottles, sometimes a potion / a bunch of brewing stuff, redstone and glowstone
q: why won't that witch die? > it drinks healing potions, hit it fast / it heals itself, burst it down
q: where do witches come from? > swamp huts, and normal spawns at night / they spawn in the dark, and swamp huts keep making them
q: how is a witch made? > lightning hits a villager / a villager struck by lightning turns into one
like: witches are a fun fight | i like witches, they drop good stuff | witches are my favourite mob to farm
why: redstone and glowstone for free | the drops are great for brewing | they make you play smart
likere: they're so annoying | the drops are nice
hate: i hate witches | witches throwing poison at me is the worst | witches healing mid fight drives me nuts
hatere: rush them before they drink | bring milk | they're the worst, yeah
tip: rush a witch, don't let it drink a potion | milk clears their poison and slowness | witches shrug off a lot of magic, use a sword | a swamp hut keeps spawning witches, good for a farm | lightning on a villager makes a witch
setup: so i was fighting zombies at night | ok so i'm holding off some mobs by my house at night | story time: night fight, me versus a bunch of mobs
climax: and a witch shows up from nowhere and poisons me | and a witch starts lobbing splash potions at me | and a witch threw slowness and poison at the same time
storyre: witches always join late | the potions are unfair
coda: drank all my milk | i ran inside and blocked the door | killed it eventually, got redstone at least
feel: witches scare me more than zombies | the witch laugh gives me chills | i get uneasy around swamps because of witches
feelre: the laugh is the worst | stay away from the swamp hut | rush them and they fold
feelend: still | yeah | no thanks
op: witches are the most annoying mob | witches are worse than skeletons | witches are unfair with the potions
op+: healing mid fight is cheating | the poison is so annoying
op-: they're slow | rush them and it's done | they drop good stuff though
deb: witches are the hardest mob in the overworld
deb+: poison, slowness and healing, they have it all | yeah, they're like a player with potions
deb-: they go down fast if you rush | the warden is way harder
deb++: and they come in raids too | plus they shrug off magic damage
deb--: they heal back faster than you hit if you're slow | a witch in a raid is a nightmare | try fighting one with a horde around
debx: rushing still works | the warden though

=== slime | mobs,biomes | farming,caves | meh | slimes
see: slimes bouncing around the swamp tonight | found a slime chunk while mining | a big slime split into a dozen small ones | full moon, the swamp is all slimes
re: they're kind of cute | slimeballs though | the little ones are funny | sticky pistons time
q: where do slimes spawn? > swamps at night, and slime chunks deep down / slime chunks below y 40, and swamps
q: what are slimeballs for? > sticky pistons, leads and slime blocks / leads and sticky pistons mostly
q: why are there more slimes some nights? > full moon, more slimes in swamps / swamp slimes follow the moon, full moon is the most
like: slimes are adorable | i love slimes | slimes are my favourite mob
why: they bounce, it's so cute | slimeballs make sticky pistons | the little ones can barely hurt you
likere: bouncy | they're gross
hate: slimes are so annoying | i hate when a big slime splits | slimes in my mine keep bothering me
hatere: light doesn't stop them in slime chunks | they give slimeballs though | kill the small ones, they're harmless
tip: slimes spawn in slime chunks below y 40, light doesn't stop them | swamp slimes come out more on a full moon | only the tiny slimes drop slimeballs | a slime block makes you bounce, no fall damage | frogs eat the small ones and drop slimeballs
setup: so i was digging a tunnel deep down | ok so i'm strip mining near the bottom | story time: deep tunnel, me mining
climax: and a huge slime hopped out of the dark at me | and slimes kept spawning in my tunnel even with torches | and a giant slime bounced right into my face
storyre: slime chunk for sure | the bounce is terrifying
coda: got a stack of slimeballs though | turned the tunnel into a slime farm | i have sticky pistons for days now
feel: i love when the slimes come out in the swamp | slimes make me happy | big slimes scare me a little
feelre: they're so bouncy | the big ones are a lot | they're harmless mostly
feelend: lol | love them | the bouncing gets me
op: slimes are the cutest hostile mob | slimes are basically harmless | slime chunks are the best thing to find
op+: they bounce | the little ones can't even hurt you
op-: the big ones hit hard | they're annoying in a mine | they're not cute, they're gross
deb: slimes shouldn't even count as hostile
deb+: the tiny ones do nothing | true, they're like pets
deb-: a big slime can kill you | they still attack you
deb++: and they're so cute | they barely hurt you
deb--: only the big one hits and you can see it coming | half of them do zero damage | they're blobs
debx: tell that to my health bar | hostile is hostile

=== phantom | mobs,sky | sleep,night | bad | phantoms
need.see: time_night a:outside
need.feel: time_night
see: phantoms are out, i didn't sleep | phantoms screeching overhead | a phantom swooped me twice already | i can hear phantoms up there
re: go to bed | sleep fixes that | the screech gets me | look up and swing
q: why are there phantoms? > you haven't slept in three nights / you skipped sleeping, they show up after three nights
q: what do phantoms drop? > phantom membrane, fixes elytra / membranes, for slow falling and elytra
q: how do i get rid of phantoms? > sleep in a bed / just sleep, or get a cat
like: phantom membranes are worth it honestly | i kind of like phantoms | phantoms make the nights exciting
why: slow falling potions need them | elytra repair | it's a good fight
likere: weird | membranes are useful
hate: i hate phantoms | phantoms are the worst mob | phantoms ruin every night i don't sleep
hatere: just sleep | cats scare them | sleep in a bed, problem gone
tip: sleep and phantoms go away | they're undead, smite works on them | cats scare phantoms off | phantom membranes repair elytra at an anvil | they burn in the sun
setup: so i stayed up three nights building | ok so i hadn't slept in days because i was busy | story time: me, no sleep, building all night
climax: and a swarm of phantoms dove at me | and phantoms knocked me off my scaffolding | and like five phantoms came screeching
storyre: should've slept | they're the sleep police
coda: slept every night after | fell all the way down | took forever to fight them off
feel: phantoms make me anxious at night | the phantom screech keeps me up | phantoms terrify me, they come from above
feelre: just sleep, they go away | the screech is awful | look up more
feelend: easier said than done | ok | fine, bed
op: phantoms are the worst mob in the game | phantoms shouldn't exist | phantoms make building at night impossible
op+: they're so annoying | they break your focus
op-: just sleep | membranes are good | they're easy with a bow
deb: phantoms are a good mob, they make you sleep
deb+: true, that's the point | yeah, it's a reminder
deb-: they're annoying, that's all | nah, they just punish building
deb++: and the membranes are useful | sleeping is good for you anyway
deb--: then sleep, problem solved | they keep the night cycle meaningful | without them nobody would sleep
debx: still the worst mob | i'll never like them

=== silverfish | mobs,caves | structures,mining | bad | silverfish
see: silverfish came out of the stone | a block in the mountain turned into a silverfish | stronghold was full of silverfish | broke one stone and five silverfish showed up
re: infested blocks are the worst | they're like little bugs | they call their friends | strongholds, of course
q: what are silverfish? > little bugs that hide in stone / tiny pests that live in infested blocks
q: where do silverfish live? > strongholds and mountain stone / infested blocks in mountains and strongholds
q: do silverfish drop anything? > nothing / nope, nothing at all
like: silverfish are kind of cute | i like silverfish, they're tiny | silverfish are funny little guys
why: they wiggle | they're harmless one at a time | they're like little bugs
likere: they're pests | cute, sure
hate: i hate silverfish | silverfish are the most pointless mob | silverfish in the stronghold drove me nuts
hatere: they drop nothing, yeah | infested blocks are a trap | one hit each, they're tiny
tip: hit a silverfish and it calls others from the blocks around it | strongholds have a silverfish spawner by the portal room | kill them in one hit so they don't call friends | they hide in stone in mountains, watch for infested blocks
setup: so i'm digging through a mountain | ok so i'm mining in the hills | story time: me tunneling into a mountain
climax: and a stone block turned into a silverfish | and silverfish came out of the walls | and every block i broke had a silverfish
storyre: infested stone | the mountain was full of them
coda: i backed out slowly | took forever to kill them all | i check the stone now
feel: silverfish creep me out | the tiny bugs make my skin crawl | silverfish make me paranoid about stone
feelre: they're tiny | ew, yeah | at least they're weak
feelend: still gross | ugh | wiggly little things
op: silverfish are the most pointless mob | silverfish should drop something | silverfish are the most annoying mob
op+: they drop nothing | waste of time
op-: they're harmless | they're kind of funny | endermites are worse
deb: silverfish are the worst mob in the game
deb+: no drops and they call friends | yeah, pure annoyance
deb-: they're barely a threat | phantoms are worse
deb++: and they hide in blocks | in the stronghold they're everywhere
deb--: you can't even trust stone | they swarm you | the stronghold portal room is full of them
debx: still small fry | phantoms win

=== pillager | mobs,villagers | danger,fighting | bad | pillagers
see: saw a pillager patrol on the hill | pillagers shooting at me from the trees | a pillager captain with a banner walked by | there's a pillager outpost over by the river
re: crossbows hurt | the banner guy is the leader | patrols are the worst | they hate everyone
q: what do pillagers drop? > crossbows and arrows / arrows, sometimes a crossbow
q: what's the guy with the banner? > a raid captain, it drops an ominous bottle / the captain, kill it and you get an ominous bottle
q: where do pillagers come from? > outposts and patrols / they spawn round outposts and walk around in patrols
like: i like fighting pillagers | pillagers are a fun fight | pillagers drop free crossbows, i like them
why: free crossbows | they're like players with crossbows | it's a real fight
likere: they're annoying | crossbows are nice
hate: i hate pillagers | pillager patrols ruin my walks | pillagers shooting from far is the worst
hatere: shield | get close fast | patrols are rough
tip: kill the captain with the banner for an ominous bottle | pillagers keep spawning around their outpost | a shield blocks crossbow bolts | patrols have one captain and a few pillagers | iron golems fight them for you
setup: so i was walking through the plains at dusk | ok so i'm heading home over a hill in the evening | story time: plains, sunset, me alone
climax: and a whole pillager patrol came over the hill | and pillagers started shooting at me from every side | and a patrol spotted me and opened fire
storyre: patrols are no joke | crossbows hurt
coda: killed the captain and got an ominous bottle | ran into the river | i bring a shield everywhere now
feel: pillagers make me nervous | the crossbow click scares me | i'm scared of patrols at night
feelre: shield up | they're just illagers | patrols are scary, i get it
feelend: still | i'll stay in | ok
op: pillagers are the most dangerous mob | pillagers are worse than skeletons | pillager outposts should be destroyed
op+: crossbows hurt a lot | they come in groups
op-: they're slow to reload | shields block them | iron golems handle them
deb: pillager outposts are worth raiding
deb+: allays in the cages | yeah, the loot is decent
deb-: they never stop spawning | not worth dying for
deb++: and there's an iron golem to free | plus the crossbows
deb--: the loot is good and allays are free | you get crossbows and arrows | the chest at the top has good stuff
debx: still too risky | i'll pass

=== vindicator | mobs,villagers | danger,fighting | bad | vindicators
gate: raids
see: a vindicator nearly took my head off | vindicators all over that mansion | saw a vindicator in the raid | a vindicator broke down a door in the village
re: those axes hurt | they hit like a truck | mansions are full of them | raids bring them
q: what do vindicators drop? > emeralds and sometimes their axe / emeralds, maybe an iron axe
q: where do vindicators come from? > woodland mansions and raids / mansions and raids
q: what's johnny? > name a vindicator johnny and it attacks everything / a named vindicator goes crazy on all mobs
like: i like fighting vindicators | vindicators look cool | vindicators are a fun fight
why: emeralds | they hit hard, it's a real fight | the axe is sick
likere: they're terrifying | bold
hate: i hate vindicators | vindicators hit way too hard | vindicators are why i hate mansions
hatere: shield up | bow from range | the axe hurts, yeah
tip: vindicators hit hard, use a shield | they can break doors in raids | name one johnny and it attacks everything | they drop emeralds
setup: so i'm exploring a woodland mansion | ok so i was walking down a mansion hallway | story time: dark forest, a mansion, me
climax: and a vindicator came around the corner swinging | and a vindicator chopped me down to two hearts | and two vindicators came at me with axes
storyre: the axes hurt | mansions are terrifying
coda: ran all the way out | killed them, got emeralds | i bring a shield now
feel: vindicators scare me | the vindicator axe gives me chills | i'm scared of vindicators in raids
feelre: shield | they're scary yeah | bow them from range
feelend: still | i'll pass | ok
op: vindicators are the scariest illager | vindicators hit harder than anything | vindicators are worse than pillagers
op+: the axe hurts | they rush you
op-: evokers are worse | ravagers hit harder | they're easy with a shield
deb: vindicators are the most dangerous illager
deb+: they hit hard and fast | yeah, the axe is brutal
deb-: evokers are worse | ravagers are worse
deb++: and they break doors | in a raid they rush the villagers
deb--: evokers just summon stuff | ravagers are slow | vindicators go for you directly
debx: evokers still | ravagers are scarier

=== evoker | mobs,villagers | danger,fighting | bad | evokers
gate: raids
see: an evoker summoned vexes at me | fought an evoker, got a totem | evoker fangs came out of the ground | the evoker turned a blue sheep red lol
re: totems though | vexes are the worst | the fangs hurt | wololo
q: what do evokers drop? > totem of undying / a totem, and emeralds
q: what are vexes? > little flying things evokers summon / tiny ghosts that go through walls
q: what's a totem of undying? > it saves you from dying once / hold it and it brings you back once
like: evokers are cool | i like evokers, totems are amazing | evokers are my favourite illager
why: totems | the fangs look sick | they're like wizards
likere: they're scary | totems are nice
hate: i hate evokers | evoker vexes are the worst | evokers are annoying
hatere: kill it fast | vexes go away eventually | totems though
tip: kill the evoker fast, the vexes die off on their own | evokers drop a totem of undying | they turn blue sheep red | the fangs come out of the ground in a line, move sideways
setup: so i was in a raid defending a village | ok so i'm fighting off a raid | story time: a raid, a village, me
climax: and an evoker summoned like six vexes | and an evoker sent fangs at me through the ground | and vexes went through the walls at me
storyre: vexes are evil | evokers are wizards
coda: got the totem though | barely survived | i bring a better sword now
feel: evokers scare me | vexes terrify me | the evoker fangs freak me out
feelre: kill the evoker first | vexes are awful yeah | totems though
feelend: still | i'll try | ok
op: evokers are the best mob for loot | evokers are the scariest illager | totems make raids worth it
op+: totems are amazing | evokers are worth it
op-: vexes are too annoying | a totem isn't worth the fight | i'd rather avoid them
deb: totems make the game too easy
deb+: a free life, yeah | they're kind of broken
deb-: you still need to find an evoker | they're a fair reward
deb++: hold one and you can't die | everyone just carries one now
deb--: one totem is one save, that's it | you have to win a raid for it | evokers are hard to fight
debx: still too easy | a free life is a lot

=== ravager | mobs,villagers | danger,fighting | bad | ravagers
gate: raids
see: a ravager trampled my crops | saw a ravager in the raid | a ravager roared and knocked me back | a pillager riding a ravager came at the village
re: ravagers are huge | the roar is scary | they wreck farms | that's a lot of hp
q: what do ravagers drop? > a saddle / a saddle, that's it
q: how do i fight a ravager? > shield it, it gets stunned / block with a shield and it gets dazed
q: can you ride a ravager? > no, only illagers ride them / nope, pillagers can
like: ravagers are awesome | i like ravagers | ravagers look sick
why: they're huge | the roar is cool | they're like a boss
likere: they're terrifying | you're brave
hate: i hate ravagers | ravagers wreck everything | ravagers are the worst part of raids
hatere: shield | they're slow to turn | they hit hard, yeah
tip: blocking a ravager with a shield stuns it | ravagers break leaves and crops | they drop a saddle | they have a lot of health, bring a good sword
setup: so i was defending a village from a raid | ok so i'm in a raid, wave three or so | story time: raid, village, a big beast
climax: and a ravager charged me and knocked me flying | and a ravager trampled the whole farm | and a ravager roared and threw me back
storyre: ravagers are monsters | the roar
coda: blocked with my shield and it got stunned | killed it, got a saddle | the farm's gone though
feel: ravagers scare me | the ravager roar is terrifying | i'm scared of ravagers more than anything
feelre: shield | they're big but slow | yeah, they're huge
feelend: still | yeah | ok
op: ravagers are the scariest mob | ravagers are worse than the warden | ravagers are the best raid mob
op+: they're huge | the roar is scary
op-: shields stun them | they're slow | warden is worse
deb: ravagers are the hardest part of a raid
deb+: they're huge and hit hard | yeah, they're a mini boss
deb-: evokers are worse | vexes are harder
deb++: and they knock you back | and they wreck farms
deb--: they have so much health | they knock you into everything | they trample everything
debx: evokers still | vexes are worse

=== guardian | mobs,ocean | danger,structures | bad | guardians
see: guardians zapped me near the monument | saw guardians swimming around the monument | a guardian flopped on the beach | the monument is full of guardians
re: lasers | prismarine though | they hurt | the monument is rough
q: what do guardians drop? > prismarine shards and crystals, sometimes fish / prismarine stuff and raw cod
q: what's a guardian? > a spiky fish with a laser, monument guard / ocean monument fish, shoots lasers
q: how do i fight guardians? > break line of sight, the laser needs to charge / use cover, the laser takes a second
like: i like guardians | guardians are cool | guardians look sick
why: they have lasers | prismarine is pretty | they're like fish with eyes
likere: they're scary | prismarine is nice
hate: i hate guardians | guardians laser me every time | guardian lasers are unfair
hatere: break line of sight | use cover | doors underwater help
tip: guardian lasers need to charge, break line of sight | they have spikes that hurt when you hit them | prismarine from them makes lanterns and blocks | doors in water give you air bubbles
setup: so i swam to an ocean monument | ok so i'm diving down to the monument | story time: ocean, a monument, me
climax: and like five guardians lasered me at once | and a guardian zapped me from across the room | and guardians surrounded me
storyre: lasers | monuments are no joke
coda: swam up so fast | got some prismarine though | never going back
feel: guardians scare me | the laser sound terrifies me | i'm scared of the ocean because of guardians
feelre: break line of sight | they're spiky menaces | lasers are the worst
feelend: still | yeah | ok
op: guardians are the worst ocean mob | guardians are unfair | guardian lasers are too strong
op+: lasers hurt | they're everywhere in monuments
op-: they're slow to charge | doors help | drowned are worse
deb: ocean monuments aren't worth it because of guardians
deb+: lasers and mining fatigue, no | true, too much effort
deb-: sponges and gold blocks | prismarine is worth it
deb++: and the elder guardians | you need water breathing too
deb--: eight gold blocks in the middle | sponges are the best block | prismarine looks amazing
debx: still not worth it | too hard

=== elder_guardian | mobs,ocean | danger,structures | bad | elder guardians
see: got mining fatigue from an elder guardian | the elder guardian face popped up out of nowhere | three elder guardians in that monument | killed an elder guardian, got a sponge
re: mining fatigue is the worst | that face | sponges though | they're huge
q: what do elder guardians drop? > a wet sponge and prismarine / sponge and prismarine stuff
q: how many elder guardians are in a monument? > three / three, one at the top, two in the wings
q: why can't i mine? > mining fatigue from the elder guardian / elder guardian curse, kill them first
like: elder guardians are cool | i like elder guardians | elder guardians are a great boss fight
why: sponges | they're huge | it's like a mini boss
likere: they're terrifying | sponges are nice
hate: i hate elder guardians | the mining fatigue is the worst | elder guardian face scares me
hatere: kill them first | milk clears it | that face is creepy yeah
tip: elder guardians give mining fatigue, kill them before mining | there are three in every monument | milk clears the fatigue but it comes back | they drop wet sponges, dry them in a furnace
setup: so i'm trying to mine into a monument | ok so i was breaking prismarine near a monument | story time: me, a pickaxe, a monument
climax: and an elder guardian face flashed in front of me | and suddenly i'm mining in slow motion | and mining fatigue hit me out of nowhere
storyre: that face | mining fatigue
coda: had to find and kill all three | drank milk and it came back | gave up and went home
feel: elder guardians terrify me | the elder guardian face is nightmare fuel | i'm scared of elder guardians
feelre: that face, yeah | kill them first | milk helps
feelend: still | yeah | ok
op: elder guardians are the most annoying boss | elder guardians are worse than the wither | mining fatigue is the worst effect
op+: you can't do anything | mining fatigue is awful
op-: just kill them | sponges make it worth it | the wither is way worse
deb: elder guardians are the best mini boss
deb+: sponges and a real fight | yeah, they're great
deb-: the warden is better | the wither is a real boss
deb++: and the monument loot | three in one place, it's a real raid
deb--: three of them in one place, with lasers | sponges are rare and useful | mining fatigue makes you plan
debx: still meh | wither for me

=== breeze | mobs,structures | danger,fighting | meh | breezes
gate: trial_chambers
see: a breeze knocked me across the room | fought a breeze in the trial chamber | breezes deflect arrows, it's so annoying | got breeze rods today
re: wind charges | they're bouncy | breeze rods though | they're so annoying
q: what do breezes drop? > breeze rods, for wind charges / breeze rods, for wind charges and the mace
q: why do my arrows miss breezes? > they deflect projectiles / breezes knock arrows away, use a sword
q: what's a wind charge? > a ball of wind from breeze rods, it knocks things back / it pushes you, good for jumping high
like: breezes are cool | i like breezes | breezes are my favourite new mob
why: wind charges | the mace needs breeze rods | they're bouncy
likere: they're annoying | wind charges are fun
hate: i hate breezes | breezes knock me around | breezes are so annoying
hatere: sword them | they're bouncy | get close fast
tip: breezes deflect arrows, use a sword | breeze rods make wind charges | wind charges launch you up | breezes only show up in trial chambers | you need breeze rods and a heavy core for the mace
setup: so i was in a trial chamber | ok so i'm fighting through a trial chamber | story time: trial chamber, me
climax: and a breeze knocked me off a ledge | and a breeze launched me into a pit | and a breeze kept bouncing me around
storyre: breezes are bouncy | trial chambers are no joke
coda: got breeze rods though | barely made it out | got a wind charge out of it
feel: breezes stress me out | breezes are scary | i'm scared of breezes knocking me off ledges
feelre: they're bouncy | sword them | yeah
feelend: still | yeah | ok
op: breezes are the best new mob | breezes are the most annoying mob | wind charges are amazing
op+: they're so fun | wind charges are great
op-: they're annoying | they deflect arrows | they're just a nuisance
deb: the mace is the best weapon in the game
deb+: falling attacks are insane | yeah, it's so strong
deb-: netherite sword is better | it's too situational
deb++: and wind charges launch you up | plus density stacks damage
deb--: one hit from high up kills anything | wind charge, then smash | nothing beats it
debx: sword still | too situational

=== warden | mobs,caves | danger,structures | bad | the warden
gate: warden
see: heard the warden heartbeat in the deep dark | almost woke the warden today | the warden came out of the ground near me | a sculk shrieker went off and i ran
re: run | the warden is terrifying | sneak | the heartbeat is so scary
q: what's the warden? > a huge blind mob in the deep dark / the scariest mob, run from it
q: can you fight the warden? > you can but don't / it kills you in a few hits, avoid it
q: what does the warden drop? > a sculk catalyst / just a sculk catalyst, not worth it
like: the warden is the coolest mob | i love the warden | the warden is so cool
why: the heartbeat | it's blind, it hunts by sound | it's like a horror movie
likere: it's terrifying | brave
hate: i hate the warden | the warden is too strong | the warden ruins the deep dark
hatere: sneak | it's blind | stay away
tip: the warden is blind, it hears vibrations | sneak and you're quiet | wool blocks vibrations | throw snowballs to distract it | sonic boom goes through walls and shields
setup: so i was sneaking through an ancient city | ok so i'm in the deep dark looking for loot | story time: deep dark, me, sneaking
climax: and i stepped on a shrieker and the warden came out | and the warden dug out of the ground right next to me | and the heartbeat got louder and louder
storyre: the heartbeat | the warden is no joke
coda: ran so fast | pillared up and waited | lost everything, it killed me
feel: the warden terrifies me | i can't go in the deep dark because of the warden | the heartbeat gives me nightmares
feelre: same | sneak and you're fine | the warden is scary yeah
feelend: still | yeah | never
op: the warden is the scariest mob | the warden is too strong | the warden ruins the deep dark
op+: so much health | the sonic boom is unfair
op-: just sneak | it's a hazard, not a boss | avoid it
deb: the warden is harder than the wither
deb+: way more health and damage | yeah, it kills in two hits
deb-: the wither flies and breaks blocks | the warden is avoidable
deb++: and it goes through walls | the sonic boom ignores shields
deb--: avoidable doesn't mean easy | try fighting it | it has more health than the wither
debx: still the wither | avoid it then

=== wither | mobs,danger | fighting,nether | bad | the wither
gate: beacons
see: thinking about summoning the wither | got three wither skulls finally | the wither blew up half my base | saw a wither fight once, never again
re: the wither is no joke | nether star though | beacons | bring milk
q: how do you summon the wither? > three wither skulls on four soul sand in a t / a t of soul sand with three skulls on top
q: what does the wither drop? > a nether star, for a beacon / a nether star
q: where do i fight the wither? > underground or in the nether / somewhere enclosed, it breaks blocks
like: the wither is the best boss | i love fighting the wither | the wither is a great fight
why: nether star means beacon | it's a real challenge | three heads, so cool
likere: brave | beacons are worth it
hate: i hate the wither | the wither destroyed my base | the wither is too hard
hatere: fight it underground | bring milk | it's hard, yeah
tip: summon the wither underground so it can't fly away | bring milk for the wither effect | at half health it gets armor, use a sword | it breaks blocks, keep it away from your base | you need three wither skeleton skulls
setup: so i finally summoned the wither | ok so i got three skulls and built the wither | story time: me, three skulls, a bad idea
climax: and it blew up and flew away | and it destroyed half my base | and it shot me with blue skulls
storyre: the wither is no joke | blue skulls
coda: got the nether star though | took forever | i'm never doing that again
feel: the wither scares me | i'm scared to summon the wither | the wither is terrifying
feelre: fight it underground | it's scary yeah | milk and a sword
feelend: still | yeah | ok
op: the wither is the hardest boss | the wither is harder than the dragon | beacons aren't worth the wither
op+: it flies and breaks blocks | the explosions
op-: the dragon is harder | the warden is harder | beacons are worth it
deb: the wither is harder than the ender dragon
deb+: it breaks blocks and flies | yeah, the explosions
deb-: the dragon has crystals | the wither is easy underground
deb++: and the wither effect | you can't hide from it
deb--: the dragon is only hard the first time | the wither explodes everything | underground is still hard
debx: still the dragon | wither is easy

=== endermite | mobs,caves | enderman,random | meh | endermites
see: an endermite came out of my pearl | saw an endermite crawling around | killed an endermite, tiny thing | endermites spawn from pearls, so annoying
re: tiny | endermen hate them | they're like silverfish | they don't last long
q: where do endermites come from? > thrown ender pearls sometimes / sometimes a pearl spawns one
q: do endermites drop anything? > nothing / nope
q: why do endermen chase endermites? > they hate them / endermen attack them on sight
like: endermites are cute | i like endermites | endermites are funny
why: they're tiny and purple | endermen hate them | they're like little bugs
likere: they're pests | sure
hate: i hate endermites | endermites are pointless | endermites spawn from my pearls, so annoying
hatere: they despawn | they're tiny | they're harmless mostly
tip: endermites sometimes spawn when you throw an ender pearl | endermen attack endermites | they despawn after a couple minutes | an endermite in a minecart lures endermen in farms
setup: so i threw a pearl to get across a gap | ok so i was pearling across a ravine | story time: me, a pearl, a ravine
climax: and an endermite popped out where i landed | and i landed next to an endermite | and an endermite spawned and bit me
storyre: tiny but mean | of course
coda: killed it with one hit | it was tiny | whatever
feel: endermites gross me out | i hate the little purple bugs | endermites make my skin crawl
feelre: they're tiny | ew, yeah | at least they're weak
feelend: still gross | ugh | yeah
op: endermites are pointless | endermites are cute | endermites are better than silverfish
op+: they do nothing | they're tiny
op-: they're useful for farms | silverfish are worse | they're harmless
deb: endermites are the most useless mob
deb+: no drops, despawn, why exist | yeah, pointless
deb-: they're useful in enderman farms | silverfish are worse
deb++: they don't even last | endermen kill them on sight anyway
deb--: being bait in one farm isn't a job | they drop nothing and vanish in two minutes | silverfish at least guard strongholds
debx: bait is a job | i'll defend the little guys
''')

# --- Mobs of the Nether and the End (tagged nether/end: only one who knows them brings them up).
DATA.append(r'''
=== blaze | mobs,nether | fighting,potions | bad | blazes
see: got set on fire by a blaze today | blazes all around the fortress spawner | got six blaze rods from a fortress | a blaze shot three fireballs at me
re: blaze rods though | fire resistance is a must | the fortress is full of them | they float around so annoying
q: what do blazes drop? > blaze rods / blaze rods, for brewing and eyes of ender
q: where do blazes spawn? > nether fortresses, from spawners / in fortresses, around the spawners
q: what hurts blazes? > snowballs, they hate snow / snowballs and splash water bottles
like: blazes are my favourite nether mob | i like fighting blazes | blazes are cool
why: blaze rods are so useful | they look sick spinning | the fight is fun
likere: they burn you though | blaze rods are worth it
hate: i hate blazes | blazes set me on fire every time | blazes are the worst part of the nether
hatere: fire resistance | snowballs | fight around corners
tip: snowballs hurt blazes a lot | fire resistance makes blazes harmless | you need blaze rods for eyes of ender and brewing stands | light around the spawner or they keep coming | they shoot three fireballs then pause, rush them in the gap
setup: so i found a blaze spawner in a fortress | ok so i'm in a fortress hunting for blaze rods | story time: fortress, me, a blaze spawner
climax: and four blazes lit me on fire at once | and a blaze knocked me off the bridge into lava | and blazes kept spawning faster than i could kill them
storyre: fortress life | blazes don't play
coda: got seven rods out of it though | had fire resistance, thank goodness | i'm never going without fire res again
feel: blazes make me nervous | the blaze breathing sound scares me | i'm scared of blaze spawners
feelre: fire resistance and they're nothing | snowballs | fight from a corner
feelend: still | i'll bring fire res | ok
op: blazes are the worst nether mob | blazes are worse than ghasts | blaze rods are the most useful drop
op+: you need them for the end | blaze rods are everything
op-: ghasts are worse | fire res makes them easy | wither skeletons are worse
deb: blazes are harder than ghasts
deb+: they shoot three fireballs | yeah, and they come in groups
deb-: ghasts knock you into lava | ghasts are bigger and further away
deb++: and they swarm the spawner | and you can't bat their fireballs back
deb--: ghast fireballs you can hit back | blazes never stop coming | ghasts are slow, blazes chase you
debx: still ghasts | lava is scarier than fire

=== ghast | mobs,nether | danger,exploring | bad | ghasts
see: a ghast nearly knocked me into lava | heard ghasts crying all over the nether | hit a ghast fireball back and killed it | a ghast blew out my portal
re: return to sender | they sound so sad | ghast tears though | they always hit your portal
q: what do ghasts drop? > ghast tears and gunpowder / tears for regen potions, and gunpowder
q: how do you kill a ghast? > hit its fireball back / bow, or punch the fireball back at it
q: why did my portal go out? > a ghast fireball put it out, relight it / ghasts blow out portals, bring flint and steel
like: ghasts are my favourite mob | i love ghasts | ghasts are so cute
why: they look like big sad babies | hitting the fireball back is so satisfying | ghast tears are worth it
likere: they're terrifying | they are kinda cute
hate: i hate ghasts | ghasts knock me off bridges | ghasts crying keeps me up
hatere: cobble walls | bow them | hit the fireball back
tip: hit a ghast fireball back and it can kill the ghast | ghast fireballs can't break cobblestone | ghast tears often fall in lava, kill them over land | always carry flint and steel, ghasts turn off portals | ghast tears make regeneration potions and end crystals
setup: so i was building a bridge over lava | ok so i'm bridging across a lava lake | story time: me, a bridge, the nether
climax: and a ghast fireball blew up the bridge under me | and a ghast knocked me straight into the lava | and a ghast hit me off my bridge
storyre: ghasts love bridges | lava is forever
coda: lost everything | had fire res, survived | i build with cobble walls now
feel: ghasts scare me | the ghast scream is terrifying | i'm scared of ghasts more than anything
feelre: hit the fireball back | they're just sad babies | cobble walls
feelend: still | i'll try | ok
op: ghasts are the cutest mob | ghasts are the worst nether mob | ghasts are harmless
op+: they're just big crying babies | look at their little faces
op-: they knock you into lava | they're terrifying | blazes are worse
deb: ghasts are the most dangerous mob in the nether
deb+: lava and knockback, deadly combo | yeah, they make you fall
deb-: piglin brutes are worse | the fireballs are slow
deb++: and they hit from so far away | they destroy your bridges and portals
deb--: they knock you into lava, game over | slow fireballs still blow up bridges | brutes stay in bastions, ghasts are everywhere
debx: still brutes | blazes are worse

=== piglin | mobs,nether | trade,gear | meh | piglins
see: piglins took my gold and gave me string | traded with piglins all day | a piglin chased me because i opened a chest | saw piglins hunting a hoglin
re: wear gold | they love gold | bartering is a gamble | they're so greedy
q: what do piglins give for gold? > random stuff, pearls, potions, string, obsidian / ender pearls, fire res, obsidian, string
q: why do piglins attack me? > you're not wearing gold / no gold armor, or you opened a chest
q: what are piglins scared of? > soul fire and warped fungus / soul fire, soul lanterns, warped fungus
like: piglins are my favourite nether mob | i like piglins | piglins are fun to trade with
why: bartering is fun | they dance with gold | pearls for gold, nice
likere: greedy guys | they're useful
hate: i hate piglins | piglins are so greedy | piglins attack me for opening a chest
hatere: wear gold | don't open chests near them | they're greedy, yeah
tip: wear one piece of gold and piglins leave you alone | throw gold ingots to barter | don't open chests or mine gold near them | they're scared of soul fire and warped fungus | piglins turn into zombified piglins in the overworld
setup: so i was bartering with piglins | ok so i'm trading gold with piglins | story time: me, a stack of gold, piglins
climax: and i opened a chest and they all attacked | and i mined some gold and they turned on me | and they all got mad at once
storyre: greedy | piglins are moody
coda: ran so fast | lost all my gold | i don't open chests near them anymore
feel: piglins make me nervous | i'm scared i'll forget my gold boots | piglins freak me out, they're so moody
feelre: wear gold and you're fine | they're nice with gold | just don't open chests
feelend: gold boots on | still moody | ok
op: piglins are the best nether mob | piglins are the most useful mob | bartering is the best way to get pearls
op+: bartering is great | they're so useful
op-: they're too greedy | endermen are better for pearls | they attack you
deb: piglin bartering is the best way to get ender pearls
deb+: faster than endermen | yeah, it's so fast
deb-: endermen are easier | you need so much gold
deb++: and you get fire res too | plus obsidian
deb--: gold is easy in the nether | endermen are rare | bartering is faster
debx: still endermen | gold is hard

=== piglin_brute | mobs,nether | danger,bastion | bad | piglin brutes
see: a piglin brute nearly killed me in a bastion | brutes don't care about your gold | saw a brute with a golden axe | finally killed a piglin brute
re: brutes are no joke | gold doesn't help with them | that axe hurts | bastions are rough
q: what's a piglin brute? > a piglin that hits hard and always attacks / a strong piglin in bastions, gold doesn't work
q: do brutes respawn? > nope, once they're dead they're gone / no, they only spawn with the bastion
q: what do brutes drop? > sometimes their golden axe / a golden axe, rarely
like: brutes are cool | i like piglin brutes | brutes are a great fight
why: they don't play the gold game | it's a real fight | they guard the good loot
likere: they're terrifying | you would
hate: i hate piglin brutes | brutes ruin bastions | brutes ignoring gold is so unfair
hatere: shield | bow them from the doorway | they don't respawn at least
tip: piglin brutes always attack, gold doesn't help | they don't respawn, so a cleared bastion stays clear | they hit hard, bring a shield | they only spawn in bastions
setup: so i was looting a bastion | ok so i'm sneaking into a bastion | story time: bastion, gold, me
climax: and a brute came out swinging a golden axe | and two brutes cornered me by the chest | and a brute took me down in three hits
storyre: brutes are no joke | bastions are rough
coda: barely made it out | i lost everything | never going back without a shield
feel: piglin brutes scare me | brutes terrify me | i'm scared of brutes more than ghasts
feelre: shield up | they're slow to turn | bow them from far
feelend: still | i'll try | ok
op: brutes are the scariest nether mob | brutes are worse than wither skeletons | brutes make bastions not worth it
op+: they hit so hard | gold doesn't work
op-: they don't respawn | wither skeletons are worse | bastions are worth it
deb: piglin brutes are the hardest mob in the nether
deb+: no gold trick, big axe | yeah, they hit so hard
deb-: wither skeletons are worse | ghasts are worse
deb++: and they guard the best loot | and they come in groups in the treasure room
deb--: the wither effect is nothing next to that axe | ghasts you can dodge | brutes chase you down
debx: still wither skeletons | ghasts are worse

=== hoglin | mobs,nether | danger,food | bad | hoglins
see: a hoglin launched me into the air | saw a herd of hoglins in the crimson forest | killed a hoglin, got porkchops | hoglins in the bastion stables
re: they hit so hard | free porkchops | crimson forest is full of them | they knock you sky high
q: what do hoglins drop? > porkchops and leather / raw porkchops and leather
q: how do i keep hoglins away? > warped fungus scares them / they're scared of warped fungus
q: can you breed hoglins? > yeah, with crimson fungus / crimson fungus, they breed like pigs
like: hoglins are cool | i like hoglins | hoglins are my favourite food source
why: free porkchops | they're like big angry pigs | you can farm them
likere: they're terrifying | porkchops are nice
hate: i hate hoglins | hoglins launch me into the air | hoglins are so annoying
hatere: warped fungus | they're slow | they're food
tip: hoglins are scared of warped fungus | breed them with crimson fungus for a food farm | they knock you way up, fight on flat ground | in the overworld they turn into zoglins
setup: so i was walking through the crimson forest | ok so i'm exploring the crimson forest | story time: crimson forest, me
climax: and a hoglin launched me into the air | and a herd of hoglins charged me | and a hoglin knocked me off a cliff
storyre: hoglins hit so hard | the crimson forest
coda: landed in lava | got porkchops though | i bring warped fungus now
feel: hoglins scare me | i'm scared of hoglins | getting launched by a hoglin is my nightmare
feelre: warped fungus | they're just big pigs | fight them on flat ground
feelend: still | i'll bring fungus | ok
op: hoglins are the best food in the nether | hoglins are scarier than piglins | hoglins are just angry pigs
op+: free porkchops | they're food
op-: they're dangerous | piglins are worse | they launch you
deb: a hoglin farm is the best food farm
deb+: porkchops and leather, nonstop | yeah, they breed so easy
deb-: cows are easier | you have to live in the nether for it
deb++: and crimson fungus is everywhere | plus they drop a lot
deb--: cows only drop one or two | hoglins drop way more meat | it's easy once it's built
debx: still cows | too far for me

=== zoglin | mobs,nether | danger,hoglin | bad | zoglins
see: brought a hoglin through the portal and it turned into a zoglin | a zoglin went after my cows | saw a zoglin by the portal, it shook and turned | zoglins attack literally everything
re: undead pigs, great | never bring a hoglin over | they hate everything that moves | that's the overworld curse
q: what's a zoglin? > a hoglin that went undead in the overworld / a zombified hoglin, it attacks anything
q: can you breed zoglins? > no, they're undead / nope, no fungus works on them
q: what do zoglins drop? > just rotten flesh / rotten flesh, no porkchops
hate: zoglins are the worst | i hate that zoglins go after my animals | zoglins are pure chaos
hatere: keep hoglins away from portals | smite them | rotten flesh for all that trouble
tip: a hoglin turns into a zoglin about fifteen seconds out of the nether | zoglins attack every mob, not just you | they're undead, smite works | warped fungus doesn't scare them like it does hoglins
setup: so a hoglin followed me through my portal | ok so i didn't notice a hoglin come through with me | story time: me, my portal, a hoglin tagging along
climax: and it turned into a zoglin and went for my cows | and it shook, turned green and went crazy | and the zoglin started attacking my animals
storyre: zoglins are chaos | never bring hoglins over
coda: had to fight it in my own field | lost two cows | i fenced my portal after that
feel: zoglins scare me more than hoglins | the zoglin growl gives me chills | i get nervous when anything follows me through a portal
feelre: fence your portal | they're undead, smite | they're nasty, yeah
feelend: still | i'll check behind me | ok
op: zoglins are worse than hoglins | zoglins should stay in the nether | zoglins are the most chaotic mob
op+: they attack everything | no fungus trick
op-: they're rare | smite handles them | hoglins come in herds
deb: zoglins are more dangerous than hoglins
deb+: they attack everything | yeah, no fungus trick
deb-: hoglins come in herds | zoglins are rare
deb++: and they go for your animals | you can't even breed them away
deb--: a herd you can see coming, a zoglin is loose by your house | warped fungus doesn't work on them | they wreck farms
debx: still hoglins | herds are worse

=== zombified_piglin | mobs,nether | danger,ore | meh | zombified piglins
see: hit a zombified piglin by accident and the whole group came | zombified piglins everywhere near my portal | saw a zombified piglin in the overworld | got gold nuggets from a zombified piglin
re: never hit one | they come in packs | they're harmless if you leave them | gold nuggets though
q: what do zombified piglins drop? > rotten flesh, gold nuggets, sometimes an ingot / gold nuggets and rotten flesh
q: why did they all attack me? > you hit one, they all got mad / hit one and the whole group gets angry
q: why are there zombified piglins near my portal? > portals spawn them in the overworld sometimes / they spawn by portals now and then
like: zombified piglins are my favourite | i like zombified piglins | zombified piglins are chill
why: they leave you alone | gold nuggets | they're peaceful unless you hit them
likere: until you hit one | they're chill
hate: i hate zombified piglins | zombified piglins chase me forever | zombified piglins are scary in packs
hatere: don't hit them | they're neutral | they forget eventually
tip: never hit a zombified piglin unless you want the whole pack | they spawn near overworld portals sometimes | they drop gold nuggets, good for gold farms | lightning on a pig makes a zombified piglin
setup: so i was walking through the nether | ok so i'm swinging at a magma cube in the nether | story time: nether, me, a missed swing
climax: and i accidentally hit a zombified piglin | and the whole pack came at me | and twenty zombified piglins chased me
storyre: never hit one | they come in packs
coda: ran so fast | they chased me into lava | i keep my sword away from them now
feel: zombified piglins make me nervous | i'm scared of hitting a zombified piglin by accident | a pack of zombified piglins is my nightmare
feelre: just don't swing near them | they're chill unless you hit one | they forget after a while
feelend: still | i'll keep my sword away | ok
op: zombified piglins are the chillest mob | zombified piglins are the best gold farm | zombified piglins are scarier than piglins
op+: they leave you alone | gold farms are great
op-: they're scary in packs | piglins are worse | they chase you forever
deb: gold farms are the best farm in the game
deb+: gold and xp, both | yeah, so much xp
deb-: iron farms are more useful | gold farms are so laggy
deb++: and gold is for bartering | xp for every enchant
deb--: iron farms don't give xp | gold means bartering, means pearls | xp from a gold farm is insane
debx: iron is for everything | too laggy for me

=== magma_cube | mobs,nether | potions,slime | meh | magma cubes
see: magma cubes bouncing all over the basalt deltas | a big magma cube split into a bunch of small ones | got four magma cream today | magma cubes hopping around the fortress
re: they're slimes but on fire | they bounce so high | magma cream though | the deltas are full of them
q: what do magma cubes drop? > magma cream / magma cream, for fire resistance
q: where do magma cubes spawn? > basalt deltas mostly, and fortresses / the deltas, and around fortresses
q: what's magma cream for? > fire resistance potions / brewing fire res, and magma blocks
like: magma cubes are cute | i like magma cubes more than slimes | magma cubes are my favourite nether mob
why: they open up when they jump | they're slimes with style | free magma cream
likere: cute and on fire | they're like hot jelly
hate: i hate magma cubes | magma cubes jump way too high | magma cubes always knock me toward lava
hatere: kill the small ones fast | fight them on flat ground | the cream makes up for it
tip: magma cubes drop magma cream for fire resistance | big ones split into smaller ones, like slimes | frogs eat small magma cubes and leave froglights | they have armor, so hit hard | they don't take fall damage
setup: so i was crossing the basalt deltas | ok so i'm picking my way through the basalt deltas | story time: basalt, ash, me climbing
climax: and a big magma cube landed right on me | and a magma cube jumped out of the ash at me | and a magma cube bounced me off a basalt pillar
storyre: they jump so high | the deltas are cursed
coda: got magma cream out of it at least | fell in lava, lost everything | i go around the deltas now
feel: magma cubes stress me out | the squish sound of magma cubes makes me jump | the big magma cubes kind of scare me
feelre: kill the small ones first | they're just hot slimes | the big ones are a lot
feelend: still | i'll go around | ok
op: magma cubes are the cutest nether mob | magma cubes are worse than slimes | magma cream is the best drop
op+: they're squishy | they look cool opening up
op-: they hit hard | they're annoying in the deltas | slimes are cuter
deb: magma cubes are harder than slimes
deb+: they hit harder and have armor | yeah, and they jump higher
deb-: slimes are everywhere | they're basically the same
deb++: and there's lava around them | they don't take fall damage either
deb--: same shape, more damage, lava everywhere | slimes don't have armor | a slime won't knock you into lava
debx: slimes are in my mine though | same thing to me

=== wither_skeleton | mobs,nether | danger,fortress | bad | wither skeletons
see: got a wither skeleton skull today | wither skeletons all over the fortress | got withered by a wither skeleton | saw a wither skeleton with a stone sword
re: skulls are so rare | the wither effect is nasty | fortresses are full of them | they're so tall
q: what do wither skeletons drop? > coal, bones, sometimes a skull / skulls, coal and bones
q: how do i get wither skulls? > kill wither skeletons, looting helps / looting sword on wither skeletons
q: can wither skeletons fit through a two block gap? > no, they're too tall / nope, make a two tall tunnel
like: wither skeletons are cool | i like wither skeletons | wither skeletons are my favourite nether mob
why: skulls | they look sick | they're tall and spooky
likere: they're scary | skulls are worth it
hate: i hate wither skeletons | wither skeletons are the worst | the wither effect is awful
hatere: milk | looting | they're tall, make a short tunnel
tip: wither skeletons can't fit through two block gaps | looting gets you more skulls | milk clears the wither effect | they spawn in fortresses, around the nether bricks
setup: so i was farming wither skeletons for skulls | ok so i'm hunting wither skeletons | story time: fortress, me, a looting sword
climax: and after twenty kills i finally got a skull | and after an hour of fighting one skull dropped | and the last one i killed dropped a skull
storyre: skulls are so rare | finally
coda: still need two more | took all night | the wither better be worth it
feel: wither skeletons scare me | i'm scared of the wither effect | wither skeletons are so tall and spooky
feelre: milk fixes the wither | short tunnels, they can't follow | they're scary, but skulls
feelend: still | i'll bring milk | ok
op: wither skeletons are the worst nether mob | wither skulls are too rare | wither skeletons are scarier than blazes
op+: skulls are so rare | the wither effect
op-: they're easy with a shield | blazes are worse | skulls aren't that rare
deb: wither skulls are too rare
deb+: way too rare | yeah, so many kills
deb-: looting helps | they're fine
deb++: and you need three | plus the fortress is dangerous
deb--: even with looting three skulls take forever | you need three, and the fortress isn't safe | i killed fifty for one skull
debx: looting three is fine | it's meant to be rare

=== strider | animals,nether | exploring,pets | good | striders
see: rode a strider across a whole lava lake | saw striders walking on the lava | a strider was shivering on the netherrack | there's a herd of striders by the lava sea
re: they shiver so much | lava boats with legs | warped fungus on a stick time | the little purple guys
q: how do you ride a strider? > saddle and a warped fungus on a stick / saddle it, steer with warped fungus on a stick
q: what do striders drop? > string / string, nothing else
q: why is that strider purple? > it's cold, it's out of the lava / they turn purple and shiver off lava
like: striders are my favourite nether mob | i love striders | striders are adorable
why: they shiver when they're cold | they walk on lava like it's nothing | their little legs
likere: the shivering gets me | they're the nether's only cute thing
hate: riding striders is so slow | striders wander off the second i stop | i don't get the strider hype
hatere: hold the fungus up | they're slow on land, fast on lava | they're best on lava, yeah
tip: saddle a strider and steer it with warped fungus on a stick | striders walk on lava, great for crossing lava seas | they shiver and slow down off lava | you can breed them with warped fungus | they drop string
setup: so i saddled a strider to cross a lava sea | ok so i'm riding a strider over lava | story time: me, a strider, a sea of lava
climax: and halfway across a ghast started shooting at us | and halfway across i dropped my fungus stick | and halfway across it just stopped and looked around
storyre: halfway, of course | the worst place to have trouble
coda: we made it, somehow | i was sweating the whole way | it still got me there, good boy
feel: i love striders | striders make me so happy | the nether needed something cute and it's striders
feelre: the shivering is the best part | they're precious | the little purple faces
feelend: protect them | lol same | love them
op: striders are the cutest mob | striders beat horses | everyone should own a strider
op+: they walk on lava, horses can't | the shiver is unbeatable
op-: they're slow | horses are better in the overworld | they're kind of goofy looking
deb: striders are the best way to cross lava
deb+: they walk right on it | yeah, no risk of falling in
deb-: just build a bridge | fire resistance is easier
deb++: and they're fast on lava | and you don't need any blocks
deb--: bridges take forever and ghasts break them | fire res runs out halfway | a strider never runs out
debx: a cobble bridge is forever | a potion is simpler

=== shulker | mobs,end | end_city,storage | bad | shulkers
see: a shulker made me float right off an end city | got two shulker shells today | shulkers hiding in the purpur walls again | got levitated off a tower last night
re: levitation is terrifying | shulker boxes though | they blend into the purpur | the void is right there
q: what do shulkers drop? > shulker shells / shells, for shulker boxes
q: what's a shulker box? > a chest you can pick up with the stuff still inside / a box that keeps its items when you break it
q: how do i not float away? > block the bullets with a shield / shield up, and carry slow falling
like: shulkers are my favourite end mob | i like shulkers | shulkers are cute in a weird way
why: shulker boxes changed my life | they peek out of their shell | they're little boxes with feelings
likere: they're terrifying | boxes are worth it
hate: i hate shulkers | shulkers floating me toward the void is the worst | levitation should not exist
hatere: shield, always | slow falling in your hotbar | hit them when they open
tip: shulker bullets give levitation, block them with a shield | slow falling saves you after levitation | two shells make a shulker box | hit them when they're open, closed they're armored | a shulker hit by another's bullet can split into two
setup: so i was climbing an end city tower | ok so i'm way up an end city | story time: end city, tall tower, me
climax: and a shulker made me float off the edge | and a bullet hit me and i floated out over the void | and three shulkers shot me at once
storyre: levitation is evil | over the void, no
coda: drank slow falling just in time | lost everything | i bring a shield there now
feel: shulkers scare me | levitation terrifies me | shulkers in the walls make me jumpy
feelre: shield up | slow falling saves lives | hit them when they open
feelend: still | i'll bring a shield | ok
op: shulkers are the most useful mob | shulkers are the scariest end mob | shulker boxes are the best reward
op+: shulker boxes, enough said | they make storage so easy
op-: they're terrifying | the elytra is the real prize | boxes aren't worth the risk
deb: shulker boxes are the best item in the game
deb+: portable storage, nothing beats it | yeah, so useful
deb-: the elytra is better | mending is better
deb++: and they keep everything when broken | you can carry a whole base
deb--: an elytra is fun, boxes are useful | mending fixes things, boxes carry everything | you can move a whole chest room in one trip
debx: still elytra | mending wins

=== ender_dragon | mobs,end | fighting,the_end | bad | the ender dragon
see: killed the dragon last night | the dragon knocked me off the island | saw the dragon circling the pillars | the dragon egg is on my shelf now
re: gg | the crystals are the trick | that egg is a trophy | the void is the real boss
q: how do you beat the dragon? > break the crystals, then hit it when it lands / crystals first, then swing at it on the portal
q: what does the dragon drop? > tons of xp and the egg / a mountain of xp, and the dragon egg
q: can you fight the dragon again? > yeah, four end crystals on the exit portal / put four end crystals on the portal and it comes back
like: the dragon is my favourite boss | i love the dragon fight | the dragon fight is the best moment in the game
why: the music when it shows up | it's the big finale | all that xp at once
likere: it's iconic | the purple sky and the pillars
hate: i hate the dragon | the dragon breath ruins me | the dragon knocking me off pillars is the worst
hatere: bring blocks and a bow | stay off the edges | hit it on the portal
tip: break the end crystals first, they heal it | hit it when it perches on the portal | some crystals are caged, climb or shoot carefully | wear a carved pumpkin, the end is full of endermen | you can bottle dragon's breath for lingering potions
setup: so i finally went to fight the dragon | ok so i jumped into the end portal for the first time | story time: me, the end, the dragon
climax: and i forgot about the caged crystals and it kept healing | and every time i got it low it flew off and healed | and it kept healing off a crystal i couldn't see
storyre: the caged ones get everyone | crystals are sneaky
coda: had to pillar up and break it | took me three trips | killed it in the end though
feel: the dragon scares me | i'm nervous about the dragon fight | the dragon roar gives me chills
feelre: break the crystals and it's fine | bring a bow and blocks | everyone's nervous the first time
feelend: maybe soon | i'll prepare more | ok
op: the dragon is too easy | the dragon is the hardest boss | killing the dragon is the end of the game
op+: once you know the crystals it's easy | it's predictable
op-: it's hard the first time | the void makes it scary | the wither is harder
deb: the dragon is the easiest boss in the game
deb+: once you know it, yeah | beds make it trivial
deb-: the void makes it scary | it's hard the first time
deb++: it always perches on the portal | crystals, perch, hit, done
deb--: the void only gets you if you're careless | first time is hard, every boss is | the wither breaks blocks, the dragon just circles
debx: careless happens | still scary for me
''')

# --- Animals and friendly mobs.
DATA.append(r'''
=== cow | animals,farming | food | good | cows
see: saw a herd of cows by the river | a cow walked right into my doorway | got leather from some cows today | the cows were mooing all night
re: cows are the best | leather time | steak tonight | they're so chill
q: what do cows drop? > beef and leather / raw beef and leather
q: how do you breed cows? > wheat / hold wheat, feed two of them
q: what's milk good for? > clears bad effects, poison and stuff / it removes every potion effect, good and bad
q: how do i get milk? > use a bucket on a cow / click a cow with an empty bucket
like: cows are my favourite animal | i love cows | cows are the best farm animal
why: steak, leather, milk, they do it all | they just stand there and moo | leather for books and armor
likere: steak gang | they're very calm
hate: cows are boring | cows are always in the way | cows push me around in the pen
hatere: they're useful though | make the pen bigger | steak makes up for it
tip: breed cows with wheat for steady leather | milk clears poison and wither | cooked beef is one of the best foods | leather makes books for enchanting | cows follow you if you hold wheat
setup: so i was building a fence around my cows | ok so i'm making a pen and herding cows in | story time: me, a fence, six cows
climax: and i left one gap and they all walked out | and one cow found the gap and they followed it | and when i turned around the pen was empty
storyre: cows find every gap | of course they did
coda: spent an hour getting them back | i used wheat to lure them in | double checked every fence block after
feel: cows make me happy | i love hearing the cows moo | cows are so calming
feelre: they're peaceful | moo | they're so chill
feelend: moo | lol | love them
op: cows are the most useful animal | cows are better than pigs | everyone should have a cow pen
op+: steak and leather, easy | milk too
op-: sheep are more useful | chickens are easier | pigs are just as good
deb: cows are the best animal in the game
deb+: food, leather and milk | yeah, they do everything
deb-: wolves are better | sheep are more useful
deb++: and they're easy to breed | wheat is everywhere
deb--: a wolf can't feed you | wool doesn't heal you | name another animal that gives milk
debx: still wolves | sheep for beds

=== pig | animals,farming | food | good | pigs
see: saw some pigs rolling around in the mud | a pig followed me home for my carrots | rode a pig today lol | got three porkchops from one pig
re: pigs are cute | pork | riding pigs is so slow | carrot on a stick time
q: what do pigs drop? > porkchops / raw porkchops
q: how do you breed pigs? > carrots, potatoes or beetroot / carrots work
q: how do you ride a pig? > saddle and a carrot on a stick / saddle it, steer with a carrot on a stick
q: what happens if lightning hits a pig? > it turns into a zombified piglin / zombified piglin, poor guy
like: pigs are adorable | i love pigs | pigs are my favourite
why: the little snouts | porkchops are as good as steak | riding one is hilarious
likere: they're so pink | the snout
hate: pigs are so slow | riding pigs is useless | pigs eat all my carrots
hatere: carrot on a stick | they're cute though | porkchops though
tip: breed pigs with carrots, potatoes or beetroot | cooked porkchop is as good as steak | saddle a pig and steer with a carrot on a stick | lightning turns a pig into a zombified piglin
setup: so i saddled a pig | ok so i'm riding a pig with a carrot on a stick | story time: me, a pig, a carrot
climax: and it walked right off a cliff with me on it | and it steered us straight off a ledge | and it went off the edge of a hill
storyre: pigs have no fear | of course
coda: rip pig | landed in water, both fine | i walk now
feel: i feel bad eating porkchops ngl | i named one of my pigs and now i can't eat him | i get sad when a pig wanders off
feelre: that's farm life | name tags make it worse | just eat steak then
feelend: he's called bacon | i'll stick to carrots | lol
op: pigs are the cutest animal | pigs are better than cows | pigs are the best mount
op+: they're so pink | porkchops are just as good
op-: cows are better | horses are better | they're slow
deb: pigs are better than cows
deb+: porkchops are the same | yeah, carrots are easier
deb-: cows give leather | milk
deb++: and you can ride them | they're cuter
deb--: porkchops are as good as steak | carrots grow faster than wheat | you can ride them, can you ride a cow
debx: still cows | leather

=== sheep | animals,farming | decor,building | good | sheep
see: saw a pink sheep earlier | sheared some sheep for a bed | a sheep was eating my grass | there's a black sheep in the herd
re: pink ones are rare | wool time | they regrow it eating grass | beds
q: how do you get wool? > shears, or kill the sheep / shears, and it grows back
q: how do you dye a sheep? > use dye on it before shearing / dye it and it stays that colour
q: why are pink sheep rare? > they just are, tiny chance / they spawn really rarely
like: sheep are my favourite | i love sheep | sheep are so fluffy
why: wool for beds | they're fluffy | you can dye them
likere: wool | fluffy
hate: sheep are annoying | sheep eat my grass | sheep are boring
hatere: they regrow wool | beds though | they're useful
tip: shear sheep instead of killing them, the wool grows back | dye a sheep and it stays dyed | sheep eat grass to regrow wool | breed them with wheat | you need three wool for a bed
setup: so i dyed one sheep red and one yellow | ok so i'm breeding a red sheep with a yellow one | story time: me, two dyed sheep, some wheat
climax: and the baby came out orange | and they had an orange baby | and the lamb was orange, they mix
storyre: colour mixing | that's actually cool
coda: now i'm trying for every colour | free orange wool forever | i'm making a rainbow pen
feel: sheep are so peaceful | i love watching the sheep eat grass | i feel bad killing sheep for wool
feelre: just shear them | they're peaceful | baa
feelend: baa | lol | shears it is
op: sheep are the best farm animal | wool is the most useful thing | pink sheep are the luckiest find
op+: beds need wool | they're useful
op-: cows are better | wool isn't that useful | pink sheep are just pink
deb: shearing is better than killing sheep
deb+: they grow it back | yeah, more wool
deb-: killing is faster | you get mutton
deb++: and you get more wool | one to three wool per shear
deb--: they regrow it in minutes | shearing gives more wool | mutton isn't worth it
debx: still killing | mutton

=== chicken | animals,farming | food | good | chickens
see: chickens everywhere by the village | got eggs from my chickens | a chicken flapped off a cliff and floated down | saw a chicken jockey earlier
re: eggs | they flap | chicken jockeys are terrifying | feathers for arrows
q: what do chickens drop? > raw chicken and feathers / chicken and feathers
q: how do you get chickens? > throw eggs, some hatch / throw eggs, sometimes a chick pops out
q: how do you breed chickens? > seeds / any seeds
like: chickens are my favourite | i love chickens | chickens are so funny
why: eggs for cake | feathers for arrows | they flap
likere: they're cute | eggs
hate: chickens are annoying | chickens are everywhere | chickens are so loud
hatere: they're useful | eggs | feathers
tip: throw eggs to hatch chickens | breed them with seeds | feathers make arrows and books | chickens don't take fall damage | raw chicken can make you sick
setup: so i threw a whole stack of eggs to start a chicken farm | ok so i'm chucking eggs at a wall to get chickens | story time: me, sixteen eggs, a dream
climax: and only two chicks hatched | and like two chicks came out of all that | and almost nothing hatched
storyre: eggs are a gamble | one in eight, roughly
coda: two chickens is a start | they breed with seeds, i'll get there | now i have two chickens and no eggs
feel: chickens make me laugh | i love chickens so much | i feel bad eating chicken
feelre: they're so funny | they flap | farm life
feelend: lol | yeah | love them
op: chickens are the best farm animal | chickens are underrated | chickens are the funniest mob
op+: eggs and feathers | they're so easy
op-: cows are better | they're annoying | they're loud
deb: chickens are the most useful animal
deb+: eggs, feathers and food | yeah, so easy
deb-: cows give more | sheep for beds
deb++: and arrows need feathers | plus cake
deb--: cows don't give feathers | arrows need feathers | eggs are free
debx: still cows | sheep

=== horse | animals,exploring | pets | good | horses
see: tamed a horse today | saw a herd of horses on the plains | my horse jumped a fence like nothing | lost my horse again
re: horses are the best | saddle up | fast ones are rare | nothing beats a good horse
q: how do you tame a horse? > keep getting on it till it stops bucking / ride it over and over till hearts show up
q: how do you make a horse faster? > breed fast ones with golden carrots / breed the best ones together
q: can you put armor on a horse? > yeah, horse armor from chests / horse armor, found in dungeons and stuff
like: horses are the best mob | i love horses | horses are my favourite
why: nothing on legs is faster | they jump fences like nothing | a good horse makes the world small
likere: saddle up | fast ones are the best
hate: horses are annoying | horses are hard to tame | horses get lost so easy
hatere: use a lead | keep at it, they give in | a fast one is worth the trouble
tip: tame a horse by riding it till it stops bucking | breed horses with golden carrots or apples | tie them to a fence with a lead | horses can't climb ladders, plan your base | feeding them hay helps them heal
setup: so i left my horse outside a cave while i mined | ok so i parked my horse by a cave entrance | story time: me, my horse, a cave i went into for a minute
climax: and when i came back it was gone | and when i came out it had wandered off | and it was nowhere when i got back
storyre: always use a lead | horses wander
coda: found it two hills over | never saw it again | i tie it to a fence post now
feel: i'd be heartbroken if my horse died | i talk to my horse on long rides lol | my horse is my best friend honestly
feelre: name it, it's family now | horses are good listeners | keep it out of ravines
feelend: already named | lol | i will
op: horses are the best transport | horses are better than boats | a fast horse is worth more than diamonds
op+: they're fast and they jump | nothing beats one on open plains
op-: boats are faster on ice | they're useless in caves | a boat never wanders off
deb: horses are the best way to travel
deb+: fast and they jump | yeah, nothing beats them on land
deb-: boats on ice roads are faster | an elytra beats everything
deb++: and they work in every biome | and they never run out of fuel or rockets
deb--: ice roads take forever to build | an elytra is end game, a horse is day two | rockets cost gunpowder, hay is cheap
debx: still elytra | ice roads are worth it

=== donkey | animals,storage | pets | good | donkeys
see: put a chest on my donkey | saw a donkey in the plains | my donkey carries half my stuff | a donkey followed me home
re: portable chest on legs | donkeys are underrated | those ears though | smart move
q: how do you put a chest on a donkey? > tame it, then use a chest on it / tame it and right click with a chest
q: what's a mule? > horse plus donkey / a horse and a donkey's baby
q: how many slots does a donkey chest have? > fifteen / three rows of five
like: donkeys are underrated | i love donkeys | donkeys are my favourite
why: they carry fifteen stacks for you | the ears | they're calmer than horses
likere: the ears get me | they're so useful
hate: donkeys are so slow | donkeys are useless in a fight | donkeys bray at the worst times
hatere: they're for carrying, not racing | you'd be slow too with a chest | the bray is kind of cute
tip: put a chest on a donkey for fifteen extra slots | breed a donkey and a horse for a mule | donkeys spawn in plains, savannas and meadows | breed donkeys with golden carrots
setup: so i loaded my donkey with everything from my mine | ok so my donkey was carrying all my ore home | story time: me, a donkey full of ore, a long walk
climax: and it walked right into lava | and it stepped into a lava pool | and the dumb thing wandered into lava
storyre: noo | the chest burned too
coda: all that ore, gone | rip donkey, rip ore | i carry the good stuff myself now
feel: i love my donkey | my donkey makes long trips less lonely | i'd be lost without my donkey
feelre: donkeys are loyal | the ears make everything better | protect that donkey
feelend: lol | he's the best | always
op: donkeys are better than horses | a donkey is the best early game buy | everyone should own a donkey
op+: a chest on legs, yeah | storage beats speed
op-: horses are faster | a chest at home is enough | mules are better
deb: donkeys are better than horses
deb+: they carry a chest | yeah, storage wins
deb-: horses are faster | horses jump higher
deb++: and you can still ride them | fifteen extra slots on a mining trip
deb--: speed doesn't matter if you go home twice | a horse carries nothing | the ore gets home either way
debx: still horses | speed wins

=== llama | animals,storage | trade | good | llamas
see: a llama spat on me | saw a llama caravan with a trader | my llamas follow each other in a line | put a carpet on my llama
re: they spit | caravans are so cool | carpets on llamas are cute | the trader's llamas are the best part
q: how do you make a llama caravan? > lead one and the others follow it / put a lead on one, up to ten follow in a line
q: can llamas carry chests? > yeah, depends how strong the llama is / yes, three to fifteen slots
q: can you ride a llama? > yeah but you can't steer it / you can sit on it, it goes where it wants
like: llamas are my favourite | i love llamas | llamas are so funny
why: the spit | carpets make them fancy | a caravan feels like an adventure
likere: they spit though | that face
hate: llamas spit on me | llamas are so rude | you can't even steer a llama
hatere: don't hit them then | they're for carrying | the spit is fair
tip: lead one llama and up to ten follow in a caravan | llamas carry chests, stronger ones carry more | wolves are scared of llamas | put a carpet on a llama for looks | breed them with hay bales
setup: so i hit a llama by accident | ok so i bumped into a llama with my sword out | story time: me, a llama, a mistake
climax: and it spat right in my face | and the whole herd spat at me | and it turned around and spat on me
storyre: they spit | llama justice
coda: gross | i said sorry | i keep my sword away now
feel: llamas make me laugh | the llama face cracks me up | llamas are so dramatic
feelre: the spit | that judging look | they have attitude
feelend: lol | love them | they really do
op: llamas are better than donkeys | llamas are the funniest mob | llamas are underrated
op+: caravans carry so much | they've got personality
op-: a donkey you can steer | they spit | they're slow
deb: llamas are better than donkeys
deb+: caravans carry way more | yeah, ten llamas, ten chests
deb-: you can't steer a llama | a donkey is simpler
deb++: and they scare wolves | and you can dress them in carpet
deb--: you lead one, the rest follow | ten chests beats one chest | steering doesn't matter on a lead
debx: still donkeys | one donkey is enough
''')

DATA.append(r'''
=== wolf | animals,pets | fighting | good | wolves
see: tamed a wolf today | my dog sat by the bed all night | saw a pack of wolves in the taiga | my wolf killed a skeleton for me
re: good boy | dogs are the best | bones well spent | the red collar suits him
q: how do you tame a wolf? > bones / give it bones till hearts show up
q: how do you heal a wolf? > feed it meat / any meat, even rotten flesh
q: what's wolf armor? > armor made from armadillo scutes / six armadillo scutes make it
q: why do wolves look different in different places? > each biome has its own kind of wolf / there are wolf types now, by biome
like: wolves are the best mob | i love my dog | wolves are my favourite
why: they fight anything for you | they sit when you tell them | they never leave your side
likere: good boys all of them | dogs are family
hate: wolves jump in lava for no reason | wolves die so easily | my dog keeps attacking things i don't want it to
hatere: wolf armor fixes a lot | make them sit before a fight | they're loyal to a fault
tip: tame wolves with bones | feed them any meat to heal, even rotten flesh | make them sit before anything dangerous | wolf armor from armadillo scutes saves their life | they hunt skeletons, sheep and foxes on their own
setup: so i got cornered by three zombies at night | ok so i'm surrounded by zombies in the dark | story time: night, me, too many zombies
climax: and my dog came out of nowhere and took them all on | and my wolf jumped in and saved me | and my dog fought off every one of them
storyre: good boy | dogs are the best
coda: he's getting all the steak | gave him a name after that | best dog ever
feel: i love my dog | my dog is my best friend | i'd cry if my dog died
feelre: protect him at all costs | get him armor | dogs make everything better
feelend: i will | already did | always
op: wolves are the best mob | dogs are better than cats | every drifter needs a dog
op+: they fight for you | loyal till the end
op-: cats are better | they die too easily | they attack the wrong things
deb: dogs are better than cats
deb+: they fight for you | yeah, loyal and tough
deb-: cats scare creepers | cats bring you gifts
deb++: and they hunt skeletons | and now they have armor
deb--: a dog kills the creeper, a cat just scares it | gifts are random string | a dog has your back in a fight
debx: still cats | gifts are nice

=== cat | animals,pets | home | good | cats
see: my cat brought me a string this morning | tamed a cat in the village | my cat is sitting on my chest again | saw a black cat at the swamp hut
re: cats are the best | morning gifts | they love sitting on chests | creepers stay away now
q: how do you tame a cat? > raw cod or salmon / give it raw fish, move slow
q: what do cats bring you? > gifts in the morning, string, feathers, rabbit hide / random gifts when you sleep
q: why do creepers run from cats? > they're scared of cats / creepers hate cats, nobody knows why
like: cats are the best | i love my cat | cats are my favourite
why: creepers won't come near | morning gifts | they curl up on the bed
likere: cats rule | creepers agree
hate: cats sit on my chests | my cat blocks every chest i own | cats never listen
hatere: tell it to sit somewhere else | it's love, they sit on what you use | creepers though
tip: cats scare creepers and phantoms away | they leave gifts if you sleep | tame cats with raw cod or salmon | a cat on a chest stops it opening
setup: so i woke up this morning | ok so i got out of bed this morning | story time: morning, me, my cat by the bed
climax: and my cat had left me a phantom membrane | and there was a phantom membrane on the floor from my cat | and my cat gifted me a phantom membrane
storyre: best cat | that's a rare gift
coda: i'm keeping it forever | she's the best | gonna fix my elytra with it lol
feel: i love my cat | my cat makes my house feel like home | i'd be lost without my cat
feelre: cats make a home | protect her | the purring
feelend: lol | she's the best | always
op: cats are better than dogs | cats are the best creeper defense | cats are the best pet
op+: no creeper holes | gifts every morning
op-: dogs are better | they sit on chests | they don't fight
deb: every base needs a cat
deb+: creepers stay away | yeah, no more holes in walls
deb-: a dog does more | cats sit on chests
deb++: and phantoms stay away too | and morning gifts
deb--: a dog fights, a cat stops the fight before it starts | move the chest, problem solved | no creeper holes since i got mine
debx: still dogs | my chests say no

=== fox | animals,biomes | trees | good | foxes
see: saw a fox sleeping by the berry bushes | a fox ran off with my sword lol | foxes in the taiga tonight | a white fox in the snow
re: they're so cute | foxes steal everything | the snow ones are the best | they sleep all day
q: can you tame a fox? > breed two with sweet berries, the baby trusts you / only the babies you breed trust you
q: what do foxes drop? > whatever they're holding / just what's in their mouth
q: where do foxes live? > taigas and snowy taigas / spruce forests, the cold ones
like: foxes are my favourite | i love foxes | foxes are so cute
why: the way they pounce | they sleep curled up | the white ones in the snow
likere: the pounce | they're adorable
hate: foxes steal my stuff | a fox took my sword | foxes eat my chickens
hatere: put stuff away | they're thieves, yeah | fence your chickens
tip: breed foxes with sweet berries, the baby will trust you | foxes pick up items, careful with dropped stuff | they sleep in the day, hunt at night | they pounce on chickens and rabbits
setup: so i dropped my sword for a second | ok so i put my sword down to grab something | story time: me, my sword on the ground, a fox
climax: and a fox ran off with it | and a fox grabbed it and bolted | and a fox picked it up and ran into the trees
storyre: foxes are thieves | of course a fox
coda: chased it for ages | never got it back | it's his sword now
feel: foxes make me so happy | i stop everything when i see a fox | finding a fox asleep in the snow made my day
feelre: they're precious | don't wake it | the white ones are magic
feelend: i didn't | lol | they really are
op: foxes are the cutest mob | foxes are better than cats | foxes are the best thing about the taiga
op+: the sleeping curl | the pounce wins it
op-: cats are cuter | they're thieves | wolves are the taiga's best
deb: foxes are the cutest animal in the game
deb+: the pounce, yeah | the sleeping curl is unbeatable
deb-: pandas are cuter | axolotls win easily
deb++: and the white ones in the snow | and they nap all day like a cat
deb--: pandas just sit and eat bamboo | an axolotl is a pink noodle, a fox is a fox | have you seen a fox pounce in the snow
debx: pandas still | axolotls forever

=== rabbit | animals,biomes | food,plants | good | rabbits
see: rabbits ate my carrots again | saw a rabbit hopping in the desert | got a rabbit foot today | rabbits everywhere in the flower forest
re: carrot thieves | rabbit stew time | lucky foot | they're tiny and fast
q: what do rabbits drop? > rabbit meat, hide, sometimes a foot / meat, hide and the odd lucky foot
q: what's a rabbit foot for? > leaping potions / brewing, it makes leaping potions
q: how do you breed rabbits? > carrots or dandelions / carrots, golden carrots or dandelions
like: rabbits are my favourite | i love rabbits | rabbits are so cute
why: the little hop | the desert ones match the sand | they're tiny and fluffy
likere: the hop | they're so small
hate: rabbits eat my crops | rabbits are impossible to catch | rabbits ruined my carrot farm
hatere: fence your carrots | sneak up on them | rabbit stew is revenge
tip: rabbits eat carrot crops, fence them in | rabbit stew is a great meal | rabbit feet make leaping potions | breed them with carrots or dandelions | four rabbit hides make leather
setup: so i planted a whole field of carrots | ok so my carrots were almost ready | story time: me, a carrot field, one night away from harvest
climax: and rabbits ate half of it overnight | and in the morning there were rabbits chewing every row | and rabbits got in and ate the carrots down
storyre: carrot thieves | rabbits are fast eaters
coda: fenced it right after | rabbit stew that night, fair trade | i check my fence every day now
feel: rabbits make me happy | the little hop gets me every time | i can't hunt rabbits, they're too cute
feelre: they're tiny | the hop is the best | stick to cows then
feelend: i will | lol | cows it is
op: rabbits are the cutest mob | rabbits are pests | rabbit stew is the best food
op+: the hop | stew is the best meal
op-: they eat crops | they're cute, not pests | stew is too much work
deb: rabbit stew is the best food in the game
deb+: it fills you right up | yeah, best meal there is
deb-: steak is easier | too many ingredients
deb++: carrot, potato, mushroom, rabbit, so much hunger back | and it feels like a real meal
deb--: one bowl fills almost the whole bar | steak can't do that | worth the cooking
debx: still steak | too much cooking

=== bee | animals,plants | farming,trees | good | bees
see: found a bee nest in a birch tree | the bees all went home in the rain | got stung by a bee today | harvested honey this morning
re: honey time | bees are so cute | never hit a bee | the buzz is relaxing
q: how do you get honey? > glass bottle on a full hive / use a bottle when the hive is dripping
q: how do i harvest without getting stung? > campfire under the hive / put a campfire below, the smoke calms them
q: how do you move a bee nest? > silk touch, the bees stay inside / break it with silk touch at night
q: what are bees good for? > honey, honeycomb, and they grow your crops / they pollinate crops and make honey
like: bees are my favourite | i love bees | bees are so cute
why: the buzz | honey cures poison | they help my crops grow
likere: the fuzzy little guys | honey is the best
hate: bees sting me every time | bees are terrifying in a swarm | i got swarmed by bees
hatere: campfire under the hive | don't take honey without smoke | they die after stinging, poor things
tip: put a campfire under a hive before harvesting | glass bottle gets honey, shears get honeycomb | silk touch moves a nest with the bees in it | bees pollinate crops near them | breed bees with flowers
setup: so i took honeycomb from a nest without a campfire | ok so i sheared a bee nest with no smoke under it | story time: me, shears, a bee nest
climax: and the whole nest came out after me | and every bee came out angry | and a swarm chased me down the hill
storyre: no campfire, no mercy | the bees remember
coda: got stung like five times | jumped in the river | always a campfire now
feel: i love bees | the buzzing around my flowers makes me so happy | i'm a little scared of bees ngl
feelre: they're harmless if you're nice | the buzz is relaxing | campfire and they're calm
feelend: i'll be nice | lol | ok
op: bees are the best mob | a bee farm is a must | honey is the best way to cure poison
op+: honey fixes poison | they grow your crops for free
op-: they sting | milk does the same | too much effort
deb: every farm needs bees
deb+: they grow crops faster | yeah, free help
deb-: bone meal is faster | they'll sting you
deb++: and you get honey and wax | honeycomb makes candles
deb--: bone meal costs bones, bees are free | they only sting if you mess with them | the honey alone is worth it
debx: bone meal still | too risky

=== parrot | animals,pets | biomes | good | parrots
see: a parrot landed on my shoulder | saw parrots in the jungle | my parrot dances to music | a parrot made a creeper sound and scared me
re: parrots are so cool | shoulder buddy | the dancing | they copy mobs
q: how do you tame a parrot? > seeds, any kind / give it seeds
q: what can't you feed a parrot? > cookies, they poison it / never cookies, it kills them
q: how do you get a parrot on your shoulder? > tame it and walk into it while it's standing / tame it, it hops on
like: parrots are my favourite | i love parrots | parrots are so cool
why: shoulder buddy | they dance | they copy mob noises
likere: shoulder | the dancing
hate: parrots copy creeper sounds | parrots fall off my shoulder | parrots are noisy
hatere: it's just a parrot | they're cute | the sounds are funny
tip: tame parrots with seeds | never feed a parrot a cookie | they dance near a playing jukebox | parrots copy the sounds of mobs nearby | they hop off when you jump or fall
setup: so i'm working in my base at night | ok so i'm sorting chests at night | story time: me, my chests, my parrot on the shelf
climax: and i hear a creeper hiss right behind me | and a hiss comes from behind me | and something hisses right next to me
storyre: was it the parrot | no way
coda: it was the parrot | spun around swinging, just my parrot | my parrot was laughing at me i swear
feel: my parrot is my favourite thing | i love when my parrot dances | i miss having a parrot on my shoulder
feelre: shoulder buddies are the best | the dancing never gets old | go to the jungle and get another
feelend: lol | i will | always
op: parrots are the best pet | a parrot is better than a cat | parrots are useless
op+: they ride on your shoulder | they warn you with mob sounds
op-: cats actually scare creepers | they fall off when you jump | they just squawk
deb: parrots are the best pet in the game
deb+: they ride on your shoulder everywhere | yeah, best company
deb-: dogs fight for you | cats keep creepers away
deb++: and they dance to music | they warn you when something's close
deb--: a dog stays home, a parrot comes along | their noises warn you before you see anything | a cat can't climb on your shoulder
debx: still dogs | a cat does more

=== panda | animals,biomes | plants | good | pandas
see: saw a panda rolling down a hill | a panda sneezed and a slimeball came out | found pandas in the bamboo jungle | a brown panda, so rare
re: pandas are so cute | the rolling | brown pandas are rare | bamboo jungle best biome
q: how do you breed pandas? > bamboo, with bamboo nearby / feed them bamboo when there's lots of bamboo around
q: why did that panda sneeze? > it's a weak panda, they sneeze slimeballs / weak pandas sneeze, sometimes a slimeball comes out
q: why is that panda angry? > it's an aggressive one / some pandas are aggressive, don't hit it
like: pandas are my favourite | i love pandas | pandas are so cute
why: the rolling | the sneezing | they sit and eat bamboo like people
likere: rolling | cute
hate: pandas are so lazy | pandas just block the path | an aggressive panda hit me
hatere: they're pandas, that's the point | leave them alone | some have a temper, yeah
tip: pandas have personalities, lazy, worried, playful, aggressive | breed pandas with bamboo, with bamboo nearby | weak pandas sneeze slimeballs | brown pandas are rare | pandas drop bamboo
setup: so i was cutting bamboo near a panda | ok so i bumped a panda while chopping bamboo | story time: me, bamboo, a panda
climax: and it was an aggressive one and hit me | and it turned around and smacked me | and it got mad and swiped at me
storyre: aggressive panda | they hit hard
coda: i said sorry and backed off | it calmed down after a bit | always check which panda it is now
feel: pandas make me so happy | watching pandas roll around is my therapy | i could watch pandas all day
feelre: the rolling is the best part | the sneezes | the lazy ones are me
feelend: lol same | truly | love them
op: pandas are the cutest mob | pandas are useless | the bamboo jungle is worth it just for pandas
op+: rolling and sneezing, unbeatable | the baby ones especially
op-: they just eat bamboo | foxes are cuter | the jungle's too much hassle
deb: pandas are the cutest mob in the game
deb+: the rolling, yeah | they have personalities
deb-: foxes are cuter | axolotls win
deb++: and the baby ones sneeze | brown ones are even better
deb--: a fox doesn't roll down hills | axolotls don't have moods | a lazy panda lying on its back wins
debx: foxes | axolotls forever

=== polar_bear | animals,biomes | danger | meh | polar bears
see: a polar bear chased me on the ice | saw polar bears by the ice spikes | a polar bear cub and its mom | polar bears swimming in the frozen ocean
re: don't go near the cub | they're huge | the moms are mean | cute but deadly
q: are polar bears dangerous? > only if there's a cub / mothers with cubs attack you
q: what do polar bears drop? > fish, cod or salmon / raw cod and salmon
q: where do polar bears live? > snowy places, ice spikes, frozen oceans / ice spikes and frozen oceans
like: polar bears are my favourite | i love polar bears | polar bears are cool
why: they stand up on their back legs | they swim through ice water like it's nothing | big fluffy snow dogs
likere: the standing up | fluffy giants
hate: polar bears attacked me for nothing | polar bears are scary | the snow is ruined by polar bears
hatere: was there a cub | stay away from cubs | they're only mad around their babies
tip: polar bears only attack if there's a cub nearby | they drop fish | they swim fast, water won't save you | they live in snowy plains, ice spikes and frozen oceans
setup: so i saw a polar bear cub | ok so i walked up to a baby polar bear | story time: me, the cutest cub, snow
climax: and its mom came out of nowhere | and the mom charged me | and a huge polar bear stood up and roared
storyre: never the cub | the mom always comes
coda: ran so fast | jumped in the water, bad idea, they swim | learned my lesson about cubs
feel: polar bears scare me | i love polar bears from a distance | a polar bear standing up is terrifying
feelre: cubs are the danger | from far away they're cute | the stand up is the warning
feelend: i'll keep my distance | still | ok
op: polar bears are the cutest mob | polar bears are scarier than wolves | polar bears are underrated
op+: big fluffy snow dogs | they're so fluffy
op-: they attacked me | wolves are scarier in packs | they just drop fish
deb: polar bears are more dangerous than wolves
deb+: one hit hurts a lot | yeah, and they're huge
deb-: wolves come in packs | bears leave you alone without cubs
deb++: and they swim faster than you | you can't outrun them on ice
deb--: one mom bear hits harder than three wolves | you never see the cub till it's too late | wolves you can tame
debx: still wolves | just watch for cubs

=== goat | animals,biomes | danger,exploring | meh | goats
see: a goat rammed me off a mountain | heard a goat scream up on the peak | got a goat horn | goats jumping all over the peaks
re: goats are evil | the screaming ones | goat horns are cool | they jump so high
q: how do you get a goat horn? > a goat rams into stone or a log / when a goat rams a hard block, it drops a horn
q: why did that goat scream? > screaming goat, they're rare / some goats just scream, it's normal
q: can you milk a goat? > yeah, with a bucket / yes, same as a cow
like: goats are my favourite | i love goats | goats are hilarious
why: the screaming ones are hilarious | they jump like five blocks | goat horns can be heard from so far away
likere: the screaming | the horns
hate: a goat rammed me off a cliff | goats are evil | goats have no respect for edges
hatere: stay away from edges | they're only dangerous up high | free horn though
tip: goats ram you, stay away from cliffs | a goat that rams stone or a log drops a horn | goats can be milked like cows | they live on snowy slopes and peaks | breed them with wheat
setup: so i was climbing a mountain | ok so i'm on a cliff edge looking at the view | story time: me, a mountain peak, a goat
climax: and a goat rammed me off | and a goat knocked me off the edge | and a goat charged out of nowhere and sent me flying
storyre: goats are evil | they wait for you
coda: fell all the way down | landed in powder snow, somehow fine | i stay off the peaks now
feel: goats scare me more than creepers | i'm scared of goats near cliffs | a goat staring at me on a ledge is my nightmare
feelre: they're evil | stay off the edges | they lower their heads before they ram, watch for it
feelend: noted | still | yeah
op: goats are the most dangerous mob | goats are evil | goats are hilarious
op+: one ram and you're off the mountain | cliffs plus goats, deadly
op-: they're funny | creepers are worse | they're harmless on flat ground
deb: goats are the most dangerous mob in the game
deb+: one ram off a peak and you're done | yeah, they kill more than the warden
deb-: the warden is way worse | creepers kill way more
deb++: and they jump to wherever you're standing | and the screaming ones are worse
deb--: the warden stays in the deep dark, goats are everywhere up high | a creeper hisses, a goat just rams | fall damage doesn't care how tough you are
debx: still the warden | stay off cliffs then

=== frog | animals,biomes | plants | good | frogs
see: frogs croaking in the swamp | a frog ate a slime | got a tadpole in a bucket | saw a white frog in the mangroves
re: frogs are so cute | they eat slimes | tadpoles | froglights
q: what do frogs eat? > small slimes and magma cubes / small slimes, they spit out slimeballs
q: how do you get different frogs? > tadpoles grow up different depending on the biome / raise tadpoles in cold, warm or temperate places
q: what's a froglight? > a light block from frogs eating small magma cubes / frog eats a magma cube, you get a froglight
like: frogs are my favourite | i love frogs | frogs are so cute
why: they croak | that long tongue | they eat slimes
likere: croak | the tongue
hate: frogs croak all night next to my house | frogs are useless outside the swamp | frogs are slow and dumb
hatere: they make froglights | they eat the slimes for you | the croaking is relaxing though
tip: frogs eat small slimes and drop slimeballs | a frog eating a small magma cube makes a froglight | tadpoles grow into different frogs by biome | breed frogs with slimeballs | scoop tadpoles with a water bucket
setup: so i brought a frog to the nether | ok so i took a frog through the portal | story time: me, a frog, the nether
climax: and it ate a magma cube and spat out a froglight | and it licked up a small magma cube and a froglight popped out | and it snapped a magma cube and dropped a froglight
storyre: froglights | frogs are geniuses
coda: now i have froglights | best lamp i own | my whole house is lit with them now
feel: frogs make me happy | the croaking in the swamp at night is so peaceful | i love the way frogs hop
feelre: the swamp choir | they're little dancers | the croaks are relaxing
feelend: lol | so peaceful | love them
op: frogs are the best swamp mob | frogs are underrated | froglights are the best light block
op+: they eat slimes for you | froglights look amazing
op-: they're useless | lanterns are better | they're slow
deb: froglights are the best light block
deb+: they look amazing | yeah, three colours
deb-: lanterns are better | glowstone is easier
deb++: and they glow as bright as glowstone | and you pick the colour by the frog
deb--: glowstone looks like noise, froglights look clean | you get to pick a colour, try that with a lantern | a frog farm makes them forever
debx: lanterns look cozier | glowstone is just easier

=== axolotl | animals,caves | ocean,pets | good | axolotls
see: found axolotls in the lush caves | caught an axolotl in a bucket | a blue axolotl, so rare | my axolotl helped me fight a drowned
re: blue is so rare | axolotls are the cutest | they play dead | bucket buddies
q: how do you catch an axolotl? > water bucket / use a water bucket on it
q: what do axolotls eat? > tropical fish in a bucket / a bucket of tropical fish
q: how rare is a blue axolotl? > super rare, one in twelve hundred or so / like one in twelve hundred babies
like: axolotls are my favourite | i love axolotls | axolotls are so cute
why: they play dead | the smile | they help you fight
likere: that smile | the pink ones especially
hate: axolotls dry out on land so fast | axolotls eat my tropical fish | axolotls play dead right when i need them
hatere: keep them in water | they're hunters, it's nature | playing dead is how they heal
tip: catch axolotls with a water bucket | breed them with a bucket of tropical fish | they help you fight drowned and guardians | they play dead when hurt | blue ones are super rare
setup: so i was fighting a drowned underwater | ok so i'm diving and a drowned shows up | story time: me, a drowned, underwater
climax: and my axolotls swarmed it | and three axolotls jumped on it | and the axolotls came out of nowhere and helped
storyre: axolotl army | they help
coda: they gave me regen too | best friends | i love them
feel: axolotls make me so happy | my bucket axolotl is my emotional support | i'd do anything for a blue axolotl
feelre: that little smile | protect the bucket | keep breeding, you'll get one
feelend: one day | lol | always
op: axolotls are the cutest mob | axolotls are the best underwater pet | lush caves are worth it just for axolotls
op+: that smile | they fight for you underwater
op-: they're just pink lizards | they dry out on land | lush caves are dangerous
deb: axolotls are the best pet in the game
deb+: they fight drowned and give you regen | yeah, and that smile
deb-: dogs follow you everywhere | they can't leave the water
deb++: and the rare blue one | and they play dead, smart little guys
deb--: underwater a dog is useless | a bucket goes everywhere | regen when they help you, name another pet that does that
debx: still dogs | land matters more

=== turtle | animals,ocean | biomes | good | turtles
see: saw turtles on the beach | baby turtles hatched last night | made a turtle shell helmet | a zombie stomped my turtle eggs
re: baby turtles are the cutest | turtle shell gang | zombies are monsters | they always go home to their beach
q: how do you get turtle scutes? > baby turtles drop them when they grow up / babies drop a scute as they grow
q: what's a turtle shell good for? > water breathing for ten seconds / a helmet that gives you water breathing
q: how do you breed turtles? > seagrass / feed them seagrass, they lay eggs on their home beach
like: turtles are my favourite | i love turtles | turtles are so chill
why: the babies are tiny | they always find their way home | the shell helmet
likere: the babies | they're so chill
hate: turtles are so slow | turtle eggs take forever to hatch | turtles are a pain to farm
hatere: they're turtles | worth it for the shell | they hatch at night, be patient
tip: baby turtles drop scutes when they grow up | five scutes make a turtle shell | turtles always go back to their home beach to lay eggs | zombies and drowned go for turtle eggs | eggs hatch at night
setup: so my turtles laid eggs on the beach | ok so i finally got turtle eggs | story time: me, a beach, turtle eggs
climax: and a zombie walked over and stomped them | and a zombie came out of the dark and trampled them | and a drowned came up the beach and stomped them
storyre: zombies are monsters | why the eggs
coda: fenced the beach the next day | lost two out of four | i guard them at night now
feel: baby turtles make me so happy | watching turtles go home to their beach gets me | i'm protective of turtles
feelre: the tiny babies | they always remember their beach | guard those eggs
feelend: i will | lol | always
op: turtles are the cutest | turtle shells are the best helmet | turtles are a waste of time
op+: water breathing for free | the babies are adorable
op-: a potion does the same | they take forever | a netherite helmet is better
deb: the turtle shell is the best helmet early on
deb+: water breathing, free | yeah, saves you in caves
deb-: iron helmet protects more | potions are easier
deb++: and you can brew turtle master with them | and it lasts forever
deb--: armor doesn't let you breathe underwater | a potion runs out, the shell doesn't | ten extra seconds of air saves lives
debx: still iron | potions for me

=== dolphin | animals,ocean | exploring | good | dolphins
see: dolphins swimming next to my boat | fed a dolphin and it led me to a shipwreck | got dolphin's grace today | dolphins jumping in the ocean
re: dolphins are the best | treasure guides | so fast | they jump so high
q: how do i get dolphin's grace? > swim next to a dolphin / swim near dolphins, they speed you up
q: what happens if you feed a dolphin fish? > it leads you to treasure / it swims to a shipwreck or ruins
q: can dolphins drown? > yeah, they need air / yes, they need to surface
like: dolphins are my favourite | i love dolphins | dolphins are so cool
why: dolphin's grace is so fast | they lead you to loot | they play with dropped items
likere: dolphin's grace | they're so smart
hate: dolphins attacked me | dolphins steal my stuff | dolphins never stay put
hatere: don't hit one, the pod gets mad | they play with items, it's cute | they're wild, yeah
tip: feed dolphins raw cod or salmon and they lead you to loot | swim near them for dolphin's grace | hit one and the whole pod gets mad | they need air, don't trap them underwater
setup: so i fed a dolphin a raw cod | ok so i gave a dolphin some fish | story time: me, a dolphin, some cod
climax: and it led me straight to a shipwreck | and it swam off and stopped right over a shipwreck | and it took me to a sunken ship
storyre: dolphins are smart | treasure guides
coda: got a treasure map out of it | found some loot | best dolphin ever
feel: dolphins make me so happy | swimming with dolphins is the best | i love when dolphins jump next to my boat
feelre: they're so happy | dolphin's grace | they jump so high
feelend: lol | yeah | love them
op: dolphins are the best ocean mob | dolphins are better than horses | dolphins are underrated
op+: dolphin's grace | they lead you to loot
op-: they leave you at the shore | a boat is faster | they're useless on land
deb: dolphins are the best mob in the ocean
deb+: they lead you to loot | yeah, and they make you fast
deb-: axolotls are better | turtles are better
deb++: and they make swimming fast | and they find treasure for one fish
deb--: an axolotl helps in a fight, a dolphin finds you loot | turtles just sit on the beach | one fish and it shows you a shipwreck
debx: axolotls fight for you | turtles give shells
''')

DATA.append(r'''
=== squid | animals,ocean | fishing,crafting | meh | squid
see: squid floating around in the river | killed a squid for ink sacs | a squid inked right in my face | squid everywhere in the ocean tonight
re: ink sacs for books | they're so weird | they just drift | free black dye
q: what do squid drop? > ink sacs / ink sacs, for black dye and books
q: what's an ink sac for? > black dye, and a book and quill / black dye and writing books
q: can squid hurt you? > no, they just squirt ink / nope, totally harmless
like: squid are kind of great | i like squid | squid are my favourite ocean mob
why: they're so chill | free ink for books | the way they pulse through the water
likere: they're weird though | ink is useful
hate: squid are pointless | squid get in the way of my boat | squid are creepy
hatere: ink sacs though | they're harmless | they're just vibing
tip: squid drop ink sacs for black dye | a book and quill needs an ink sac and a feather | squid are harmless, they just flee | they spawn in rivers and oceans
setup: so i went out to get ink sacs | ok so i was hunting squid for ink | story time: me, a sword, the river
climax: and every squid i hit just inked and swam off | and i got inked in the face like six times | and the squid kept slipping away
storyre: squid are slippery | ink to the face
coda: got two ink sacs after an hour | went home with nothing | i trade for dye now
feel: squid creep me out a little | i find squid kind of soothing | deep water with squid in it freaks me out
feelre: they're harmless | they're just floating | the tentacles are a lot
feelend: still | lol | ok
op: squid are the most useless mob | squid barely matter | squid are just ink with tentacles
op+: just ink, that's it | they do nothing
op-: ink for books is useful | black dye matters | they make the ocean feel alive
deb: squid are the most pointless mob in the game
deb+: ink sacs and nothing else | yeah, they just float
deb-: books need ink | bats are way more pointless
deb++: and they're annoying to catch | and they get stuck in rivers
deb--: black dye comes from wither roses too | one book and quill a year doesn't need a whole mob | a bat at least hangs in caves looking spooky
debx: books need ink | bats are worse, sorry

=== glow_squid | animals,caves | ocean,decor | good | glow squid
see: saw a glow squid in an underground lake | got some glow ink sacs | a glow squid lit up the cave water | glow squid way down in the caves
re: they glow | glowing signs | so pretty | underground lakes are magic
q: what do glow squid drop? > glow ink sacs / glow ink, makes signs glow
q: where do glow squid live? > deep underground water, in the dark / dark water in caves, way down
q: what's glow ink for? > glowing signs and glow item frames / glow item frames, and glowing sign text
like: glow squid are my favourite | i love glow squid | glow squid are so pretty
why: they light up the water | glowing signs | they're so calm
likere: they're beautiful | glowing signs are great
hate: glow squid are hard to find | glow squid are just squid that glow | i can never catch glow squid
hatere: go deeper | they're rare, yeah | look in the darkest lakes
tip: glow squid live in dark water deep underground | glow ink makes sign text glow | glow item frames need glow ink | they stop glowing for a bit when hurt
setup: so i found a huge underground lake | ok so i'm deep in a cave by a big lake | story time: me, a dark cave lake
climax: and it was full of glow squid lighting it up | and the whole lake was glowing with squid | and glow squid were swimming all over it
storyre: so pretty | underground lakes are the best
coda: i just sat there for a while | got glow ink too | i built my base next to it
feel: glow squid make me so happy | seeing a glow squid in a dark cave is magic | glow squid make caves less scary
feelre: they're like underwater stars | the glow is so calming | caves need more of them
feelend: lol | so true | love them
op: glow squid are prettier than any mob | glow squid are the best thing in caves | glow ink is the best dye
op+: they light up caves | glowing signs look amazing
op-: they're just glowing squid | glow ink is niche | they're hard to even find
deb: glow squid are the prettiest mob
deb+: they light up caves | yeah, so pretty
deb-: tropical fish are prettier | allays glow too
deb++: in a dark lake they're like stars | and they make signs glow
deb--: tropical fish don't light up a dark lake | allays are blue, glow squid are a whole show | a cave lake full of them, nothing beats it
debx: tropical fish | allays

=== cod | animals,ocean | fishing,food | meh | cod
see: caught a cod fishing | cod everywhere in the ocean | cooked cod for dinner | a school of cod swam past my boat
re: fish dinner | cod is so common | cats love it | dolphins love it too
q: what's raw cod good for? > taming cats and feeding dolphins / cats, dolphins, and cooking
q: where do cod live? > oceans, mostly the cold and normal ones / most oceans except warm ones
q: how do you catch cod? > fishing rod, or a sword in the water / fish for it, or just hit one
hate: cod is boring food | i always catch cod when i want treasure | cod is everywhere and useless
hatere: get luck of the sea | cats love it though | that's fishing
tip: raw cod tames cats and ocelots | feed cod to dolphins and they lead you to loot | cook it, raw cod is barely food | catch them with a bucket for an aquarium
setup: so i went fishing for treasure | ok so i'm fishing for enchanted books | story time: me, a rod, an hour by the water
climax: and caught cod twenty times in a row | and all i got was cod | and it was just cod after cod
storyre: classic | cod, always cod
coda: my cat was happy at least | now i have a stack of cod | i gave up on treasure
feel: fishing for cod at sunset is so relaxing | i love fishing, even if it's cod | cod fishing calms me down
feelre: fishing is therapy | the bobber dipping is the best feeling | peaceful
feelend: lol | so true | same
op: cod is the most boring fish | cod is just cat food | nobody gets excited about cod
op+: it's just cod | cats agree
op-: it feeds cats and dolphins | it's fine cooked | it's reliable food
deb: fishing is the best way to get food
deb+: just sit and wait | yeah, endless food
deb-: farms are faster | cows are easier
deb++: and you get treasure sometimes | and it's so relaxing
deb--: a farm needs work, a rod just needs water | you get books and saddles too | cows run out, fish don't
debx: farms still | too slow for me

=== salmon | animals,ocean | fishing,food | meh | salmon
see: salmon jumping up the river | caught a salmon today | cooked salmon for dinner | salmon swimming upstream
re: they jump so high | salmon is good food | rivers are full of them | fish dinner
q: where do salmon live? > rivers and cold oceans / rivers, and the cold oceans
q: is salmon better than cod? > yeah, cooked salmon fills more / a little, it gives more hunger cooked
q: what eats salmon? > cats, dolphins, polar bears / cats and dolphins love it
tip: cooked salmon gives more food than cod | raw salmon tames cats too | polar bears drop salmon sometimes | they leap out of rivers, easy to spot
setup: so i'm sitting by the river fishing | ok so i was at the river with my rod | story time: me, a river, a rod
climax: and a salmon jumps right over my bobber | and a salmon leapt out right next to my line | and a salmon jumped clean over my fishing line
storyre: showoff fish | they love to jump
coda: caught it two casts later | i swear it was laughing | had it for dinner, revenge
feel: i love watching salmon jump | rivers with salmon in them make me happy | salmon jumping at sunset is so pretty
feelre: they leap so high | rivers are the best | sunset fishing is perfect
feelend: lol | yeah | love it
op: salmon is better than cod | salmon is the best fish to eat | salmon is the king of the river
op+: more food than cod | and it's prettier
op-: cod is just as good | tropical fish are prettier | it's still just fish
deb: cooked salmon is the best fish food
deb+: more hunger than cod | yeah, it's the best fish
deb-: cod is more common | fish is weak food anyway
deb++: and rivers are full of them | and it cooks fast
deb--: a little more hunger is still more | cod is common, salmon is better | for fish, salmon wins
debx: steak beats any fish | cod is fine

=== pufferfish | animals,ocean | fishing,potions | bad | pufferfish
see: a pufferfish puffed up at me | ate a pufferfish by accident | caught a pufferfish fishing | pufferfish all over the warm ocean
re: don't eat it | they poison you | so spiky | water breathing potions
q: what happens if you eat a pufferfish? > poison, hunger and nausea / you get really sick
q: what's pufferfish for? > water breathing potions / brewing water breathing
q: why did that fish hurt me? > pufferfish, they poison you when puffed / you touched a pufferfish
like: pufferfish are funny | i love pufferfish | pufferfish are my favourite fish
why: they puff up | the angry little face | water breathing
likere: the puff | so round
hate: pufferfish poisoned me | i hate pufferfish | pufferfish ruin my swims
hatere: don't touch them | milk | they're spiky, yeah
tip: never eat pufferfish, it poisons you bad | pufferfish make water breathing potions | they puff up when you get close and sting | milk cures their poison
setup: so i was hungry with nothing but fish | ok so i was starving and ate whatever i caught | story time: me, hungry, a fishing rod
climax: and i ate a pufferfish | and i ate a pufferfish without looking | and i bit into a pufferfish
storyre: noo | rookie mistake
coda: poison, nausea and hunger at once | the screen was spinning | never again
feel: pufferfish make me nervous in the water | i swim around pufferfish like they're creepers | i'm weirdly fond of pufferfish
feelre: they're slow, just go around | give them space | they puff when you get close
feelend: lol | still | yeah
op: pufferfish are the worst thing you can eat | pufferfish are a trap | pufferfish should come with a warning
op+: poison, hunger and nausea at once | nothing else is that bad
op-: rotten flesh is worse | they make water breathing | they're hilarious when they puff
deb: eating a pufferfish is the worst thing you can do
deb+: poison, hunger and nausea | yeah, worst snack ever
deb-: rotten flesh is worse | spider eyes are just as bad
deb++: and the screen spins | and you lose the food you just ate
deb--: rotten flesh is just hunger, pufferfish is three bad things at once | a spider eye is a little poison, a pufferfish is a lot | try it once and tell me
debx: rotten flesh is a whole meal of sad | i'll take my chances

=== tropical_fish | animals,ocean | biomes,decor | good | tropical fish
see: saw a bunch of tropical fish at the coral reef | caught a tropical fish in a bucket | the reef was full of colours | got a really rare looking tropical fish
re: so colourful | coral reefs | aquarium time | there's thousands of kinds
q: how many kinds of tropical fish are there? > thousands, all different patterns / like three thousand patterns
q: what are tropical fish good for? > axolotl food and aquariums / feeding axolotls, and looks
q: where do tropical fish live? > warm oceans, coral reefs / warm and lukewarm oceans, the reefs
like: tropical fish are my favourite | i love tropical fish | tropical fish are so pretty
why: so many colours | they make reefs look amazing | every one is different
likere: colourful | pretty
tip: bucket a tropical fish to keep it | axolotls breed with buckets of tropical fish | they live in warm and lukewarm oceans | a bucket keeps the fish's pattern
setup: so i built an aquarium in my house and filled it with fish | ok so i made a fish tank and dumped in all my buckets | story time: me, glass, a dozen fish buckets
climax: and i accidentally put a pufferfish in it | and one of the buckets was a pufferfish | and turns out one of them was a pufferfish
storyre: oh no | the spiky one
coda: now nobody can clean the tank | it's the guard fish now | it puffs at everyone who walks by
feel: tropical fish make me happy | the reef with all the fish is so calming | i could watch tropical fish all day
feelre: colourful | calming | the reef is magic
feelend: lol | yeah | love them
op: tropical fish are the prettiest mob | coral reefs are the best part of the ocean | the reef beats any build
op+: so many colours | reefs are beautiful
op-: they're useless | glow squid are prettier | i've seen better builds
deb: aquariums are the best decoration
deb+: living colour | yeah, they move
deb-: paintings are easier | plants are better
deb++: and every fish is different | and they look great at night with lights
deb--: paintings don't swim | a plant doesn't have three thousand patterns | nothing beats moving colour
debx: paintings | plants

=== armadillo | animals,biomes | pets,gear | good | armadillos
see: saw an armadillo in the savanna | an armadillo rolled up when i walked past | got armadillo scutes | brushed an armadillo today
re: they roll up | scutes for wolf armor | so cute | they're shy
q: how do you get armadillo scutes? > brush them, or they drop them sometimes / use a brush on one, gently
q: what are scutes for? > wolf armor / six scutes make wolf armor
q: why did that armadillo roll up? > it got scared / you were sprinting near it, they get scared
like: armadillos are my favourite | i love armadillos | armadillos are adorable
why: they roll into a ball | scutes for my dog's armor | they're so shy
likere: shy little guys | the roll
hate: armadillos roll up when i want scutes | armadillos are slow | armadillos are boring
hatere: walk, don't run | brush them gently | wolf armor though
tip: brush an armadillo for a scute | they roll up if you sprint near them | six scutes make wolf armor | they live in savannas and badlands | breed them with spider eyes
setup: so i went to get scutes for my dog's armor | ok so i'm brushing armadillos for wolf armor | story time: me, a brush, a savanna
climax: and every armadillo rolled up when i ran up | and they kept curling into balls | and they all rolled up because i sprinted
storyre: walk slow | shy guys
coda: had to wait for them to unroll | took forever but my dog has armor now | i walk slow around them now
feel: armadillos make me happy | i love when armadillos peek out | armadillos are so shy and cute
feelre: the peek is everything | shy little guys | they just need a minute
feelend: lol | yeah | love them
op: armadillos are the best new mob | armadillos are underrated | wolf armor makes armadillos worth it
op+: wolf armor | cute and useful
op-: they're boring | they just roll up | wolves did fine without armor
deb: armadillos are the best mob added in a long time
deb+: wolf armor saves so many dogs | yeah, and they're adorable
deb-: the breeze is way cooler | sniffers are better
deb++: and they peek out when it's safe | and you don't even hurt them for scutes
deb--: a breeze knocks you off ledges, an armadillo saves your dog | sniffers dig up flowers, armadillos save lives | my dog would agree with me
debx: breeze for me | sniffers forever

=== camel | animals,biomes | exploring,pets | good | camels
see: rode a camel through the desert | a camel sat down in the middle of the village | camels in the desert village | two of us rode one camel
re: camels are huge | two seats | they dash | desert taxis
q: how do you ride a camel? > saddle it, it fits two / saddle and climb on, a friend can ride too
q: where do you find camels? > desert villages / they spawn in desert villages
q: how do you breed camels? > cactus / feed them cactus
like: camels are my favourite | i love camels | camels are so cool
why: two riders | they dash | you sit so high up
likere: two seats | so tall
hate: camels are slow | camels sit down at random | camels are too tall for my door
hatere: use the dash | they're chill | make a bigger door
tip: camels fit two riders | they can dash forward a few blocks | breed them with cactus | you don't need to tame a camel, just saddle it | they spawn in desert villages
setup: so i rode a camel with a friend | ok so two of us got on one camel | story time: me, a friend, a camel
climax: and it sat down in the middle of the desert | and it just stopped and sat | and the camel decided to take a break
storyre: camels do what they want | the camel sit
coda: we just waited it out | we walked the rest | it got up eventually
feel: camels make me happy | riding a camel through the desert at sunset is peak | camels are so chill, i love them
feelre: they're so calm | desert sunset rides are the best | the little sit down they do
feelend: lol | so peak | love them
op: camels are better than horses | camels are the best desert mob | camels are the best mount
op+: two riders on one mount | the dash is great
op-: horses are faster | they sit down whenever they want | they're too tall for most doors
deb: camels are better than horses
deb+: a friend can ride with you | yeah, and the dash
deb-: horses are faster | horses jump higher
deb++: and they don't need taming like horses | and you're so high up
deb--: a horse can't carry a friend | the dash jumps gaps a horse can't | you don't have to tame a camel ten times
debx: horses are faster, that's it | speed wins

=== sniffer | animals,plants | farming,exploring | good | sniffers
see: hatched a sniffer egg | my sniffer dug up a seed | saw a sniffer sniffing around | found a sniffer egg in the ocean ruins
re: sniffers are so cute | ancient seeds | big fluffy guys | torchflowers
q: where do sniffer eggs come from? > brushing suspicious sand in warm ocean ruins / warm ocean ruins, with a brush
q: what do sniffers dig up? > torchflower seeds and pitcher pods / old seeds, torchflowers and pitcher plants
q: how do you breed sniffers? > torchflower seeds / feed them torchflower seeds
like: sniffers are my favourite | i love sniffers | sniffers are so cute
why: the sniffing | they're huge and gentle | they dig up ancient seeds
likere: the sniff | gentle giants
hate: sniffers are so slow | sniffer eggs take forever | sniffers only dig up flowers
hatere: put the egg on moss | flowers are pretty | they're old, let them be slow
tip: sniffer eggs come from warm ocean ruins | put the egg on moss to hatch it faster | sniffers dig up torchflower seeds and pitcher pods | breed them with torchflower seeds
setup: so i sat by my sniffer egg for ages | ok so my sniffer egg was cracking for days | story time: me, a sniffer egg, a lot of waiting
climax: and it finally hatched into a tiny snifflet | and a little snifflet wobbled out | and out came the smallest sniffer ever
storyre: snifflet | the tiny nose
coda: i'm a parent now | it follows me everywhere | already sniffing for seeds
feel: sniffers make me so happy | i love my sniffer more than anything | sniffers are like gentle giants
feelre: the sniffing is the best | protect the big guy | gentle and fluffy
feelend: always | lol | yeah
op: sniffers are the cutest mob | sniffers are underrated | torchflowers are the best flower
op+: big gentle fluffballs | torchflowers look amazing
op-: they just dig up flowers | they're slow | a poppy does the job
deb: sniffers are worth the trouble
deb+: torchflowers and pitcher plants, nowhere else | yeah, and they're adorable
deb-: too much work for flowers | the eggs take forever
deb++: and ancient seeds are the rarest plants | and they're a gentle giant to have around
deb--: you need a brush, an ocean ruin and patience, that's an adventure | moss hatches them faster | what else gets you torchflowers
debx: flowers aren't worth it | poppies are fine

=== allay | animals,pets | storage,music | good | allays
see: freed an allay from a pillager outpost | my allay collected all my wool | an allay danced to my jukebox | saw allays in a mansion cell
re: allays are so cute | they collect stuff | little blue fairy | they dance
q: where do you find allays? > pillager outposts and woodland mansions / caged in outposts and locked in mansions
q: what do allays do? > give them an item and they collect more of it / they pick up whatever you gave them
q: how do you get more allays? > amethyst shard while they're dancing to a jukebox / give a dancing allay an amethyst shard
like: allays are my favourite | i love allays | allays are so cute
why: they collect for you | they dance | they hum
likere: the humming | the dance
hate: allays bring me junk | allays wander off | allays never stay near my chests
hatere: give them the right item | play a note block, they stay | they're trying their best
tip: give an allay an item and it collects more of it | they stay near a note block you played | give a dancing allay an amethyst shard to duplicate it | free them from pillager outposts
setup: so i gave my allay a cobblestone | ok so i handed my allay some cobble by mistake | story time: me, an allay, a mistake
climax: and it brought me every cobblestone for miles | and it collected all the cobble around | and it came back with stacks of cobble
storyre: allays are loyal | so helpful
coda: now i have stacks of cobble | it's working so hard though | at least it's trying
feel: allays make me so happy | the little allay dance gets me every time | i'd do anything for my allay
feelre: the humming | the dance is precious | protect the blue fairy
feelend: lol | always | love her
op: allays are the cutest mob | allays are the most helpful mob | every base needs an allay
op+: they hum and dance | free help sorting drops
op-: they bring you junk | a hopper does it better | they wander off
deb: an allay is better than a hopper
deb+: it flies and finds stuff | yeah, a hopper can't go get things
deb-: hoppers never wander off | allays bring you junk
deb++: and it dances to your music | and it follows you around
deb--: a hopper only takes what falls in it | give it the right item and it brings you that | a hopper never danced for anyone
debx: hoppers are reliable | still hoppers

=== iron_golem | mobs,villagers | town,fighting | good | iron golems
see: an iron golem handed a kid a poppy | the iron golem smashed a zombie | built an iron golem today | the iron golem in the village is huge
re: the poppy thing | golems are the best | they guard the village | that's a lot of iron
q: how do you build an iron golem? > four iron blocks in a t and a carved pumpkin / iron blocks in a t, pumpkin on top
q: what do iron golems drop? > iron ingots and poppies / iron and a poppy
q: how do you fix an iron golem? > iron ingots / use iron ingots on it
like: iron golems are my favourite | i love iron golems | iron golems are so cool
why: they protect the village | they give out poppies | they throw zombies in the air
likere: the poppies | gentle giants
hate: an iron golem attacked me | iron golems are scary | iron golems hit way too hard
hatere: did you hit a villager | they protect, that's all | just be nice to villagers
tip: build an iron golem with four iron blocks and a carved pumpkin | fix them with iron ingots | don't hit villagers near a golem | villagers summon a golem when they get scared together
setup: so i was fighting a zombie in the village | ok so i swung at a zombie and hit a villager | story time: me, a village, a bad swing
climax: and the iron golem launched me | and the golem threw me in the air | and the iron golem came for me
storyre: golem justice | you deserved that
coda: barely survived | i apologised to the villager | i'm careful with my sword now
feel: iron golems make me feel safe | seeing a golem give a kid a poppy melts me | iron golems kind of scare me ngl
feelre: they're gentle giants | the poppy thing is the best | just don't hit villagers
feelend: lol | so sweet | ok
op: iron golems are the best mob | iron golems are the best village defense | a village without a golem is doomed
op+: they smash zombies | they hand out poppies
op-: they're slow | walls are better | they can't stop a raid alone
deb: iron farms ruin the game
deb+: infinite iron makes everything easy | yeah, it's too much
deb-: they're just smart | iron is boring to mine anyway
deb++: and nobody mines anymore | iron blocks everywhere, no effort
deb--: mining iron is half the game | a beacon from a farm feels empty | you skip the whole early game
debx: it's still work to build | mining is boring

=== snow_golem | mobs,weather | building | good | snow golems
see: built a snow golem today | my snow golem melted in the desert | my snow golem threw snowballs at a zombie | a snow golem left a trail of snow
re: little snowball guy | they melt so easy | so cute | the snow trail
q: how do you build a snow golem? > two snow blocks and a carved pumpkin / snow blocks and a pumpkin on top
q: why did my snow golem melt? > it's too warm / warm biomes and rain melt them
q: what's under the snow golem's pumpkin? > shear it and see / a face, shear the pumpkin off
like: snow golems are my favourite | i love snow golems | snow golems are so cute
why: the snowballs | the trail of snow | they're brave little guys
likere: the snowballs | brave
hate: snow golems are useless | snow golems melt so easily | snow golem snowballs do nothing
hatere: they distract mobs | build them in the cold | they're cute though
tip: build a snow golem with two snow blocks and a carved pumpkin | they melt in warm biomes and take damage in rain | they leave a snow trail you can scoop up | snowballs hurt blazes | shear the pumpkin off to see its face
setup: so i built a snow golem to guard my door | ok so i put a snow golem out front as a guard | story time: me, my new snow guard
climax: and then it started raining | and that night it rained | and right after, a storm rolled in
storyre: oh no | snow and rain don't mix
coda: it melted into nothing | rip snowy | i built it a little roof after
feel: snow golems make me happy | i love the little snow trail they leave | i feel bad when my snow golem melts
feelre: they're so brave | the trail is the best | build it a roof
feelend: lol | i will | rip
op: snow golems are the cutest mob | snow golems are the best decoration | every snowy base needs a snow golem
op+: little snow guards | they fight for you with snowballs
op-: snowballs do nothing | they melt | an iron golem is a real guard
deb: snow golems are useless
deb+: snowballs do nothing | yeah, and they melt
deb-: they distract mobs | snowballs hurt blazes
deb++: and they melt in the rain | they're just decoration
deb--: a distraction is still nothing | how often are blazes at your house | they melt in half the world
debx: they're cute, that's a use | blazes though

=== villager | villagers,town | trade | meh | villagers
see: a villager went hmm at me again | villagers ran inside the second it got dark | traded with a librarian today | there's a nitwit just wandering the village
re: the hmm never gets old | emeralds incoming | librarians are the best | nitwits are useless lol
q: how do you get a villager to trade? > give it a workstation / put a job block near it, like a lectern
q: what's a nitwit? > a villager who never trades / green robe, never gets a job
q: how do villagers breed? > enough beds and food / they need free beds and lots of food
q: what's the best villager job? > librarian, for books / librarian, they can sell mending
like: villagers are my favourite | i love villagers | villagers are so funny
why: the hmm noise | they all run inside at night | they gossip in the middle of town
likere: the hmm | gossip hour lol
hate: villagers are so dumb | villagers walk into my crops | villagers jump in my bed
hatere: they're just living | fence the farm | they need beds too lol
tip: workstations give villagers jobs | trade more and their trades level up | cure a zombie villager for big discounts | villagers need free beds to breed | an iron golem shows up when villagers get scared
setup: so i was rerolling a librarian for mending | ok so i'm breaking and placing a lectern over and over | story time: me, a lectern, one villager
climax: and after fifty tries it finally had mending | and on like the hundredth try mending showed up | and mending finally popped up
storyre: finally | the lectern dance pays off
coda: twenty emeralds, worth it | i'm protecting that villager forever | i built him a house with walls
feel: villagers make me happy | i feel bad when villagers get hurt | a village at night with everyone inside feels so cozy
feelre: protect them | they're like neighbours | the lit windows at night
feelend: always | lol | love it
op: villagers are the best mob | villagers are dumb but lovable | the village is the best place to live
op+: trades and company | they make the world feel alive
op-: they're dumb | they get killed so easy | they just hmm
deb: trading halls are cruel to villagers
deb+: stuck in a box forever | yeah, they can't even move
deb-: they're fine | it's just efficient
deb++: and they never see the sun | one bed, one block, forever
deb--: they're fine, they live in a hole with a workstation | would you want to live in a box | efficient for who
debx: they don't mind | efficient for me

=== wandering_trader | villagers,trade | exploring | meh | the wandering trader
see: the wandering trader showed up with his llamas | the wandering trader wants five emeralds for a sapling | the wandering trader drank invisibility at night | traded with the wandering trader today
re: overpriced as always | his llamas though | he drinks potions like water | he never stays long
q: what does the wandering trader sell? > saplings, dyes, flowers, weird stuff / rare plants and stuff from far biomes
q: why did the trader go invisible? > he drinks invisibility at night / at night he goes invisible to hide
q: how long does the trader stay? > not long, then he leaves / about an hour, then he's gone
like: i like the wandering trader | the wandering trader is kind of cool | i always check what the trader has
why: he has plants from places i've never been | sometimes he has something wild | it's like a little event when he visits
likere: he's mysterious | the llamas make it
hate: the wandering trader is so overpriced | the wandering trader never has anything good | the wandering trader's llamas spat on me
hatere: check every visit, sometimes it's good | don't hit him, the llamas get mad | he's a gamble
tip: the wandering trader sells stuff from biomes you haven't found | he drinks invisibility at night | his llamas spit if you hit him | he leaves after a while, buy fast
setup: so the wandering trader came to my house | ok so a wandering trader showed up at my base | story time: me, the trader, his two llamas
climax: and he had a sapling i'd never seen | and he was selling a tree i'd never found | and he had some plant i'd never even heard of
storyre: the trader comes through | rare stuff
coda: paid way too much for it | it's a whole tree now | worth every emerald
feel: it feels like a holiday when the wandering trader shows up | i always feel bad for his tied up llamas | the wandering trader seems lonely to me
feelre: he's got his llamas | buy something, make his day | the llamas seem fine
feelend: lol | i will | maybe
op: the wandering trader is a scam | the wandering trader's prices are robbery | the wandering trader isn't worth the emeralds
op+: five emeralds for a sapling | yeah, robbery
op-: he sells stuff you can't find | some trades are gold | the rare plants are worth it
deb: the wandering trader is the most useless mob
deb+: overpriced junk and then he leaves | yeah, he never has what i need
deb-: he sells stuff from biomes you've never seen | his llamas are worth something
deb++: and he drinks potions and vanishes | and you can't even get your emeralds back
deb--: you can grow every one of those plants yourself | llamas that spit at you, great | one useful trade in ten visits
debx: the rare plants are worth it | i like his visits

=== mooshroom | animals,biomes | food,plants | good | mooshrooms
see: found mooshrooms on a mushroom island | milked a mooshroom with a bowl | saw a brown mooshroom | sheared a mooshroom and it turned into a cow
re: free mushroom stew | they're so rare | brown ones are rarer | poor cow lol
q: how do you get mushroom stew from a mooshroom? > use a bowl on it / milk it with a bowl
q: how do you get a brown mooshroom? > lightning hits a red one / lightning turns them brown
q: what happens if you shear one? > it turns into a normal cow, you get mushrooms / mushrooms drop and it becomes a cow
like: mooshrooms are my favourite | i love mooshrooms | mooshrooms are so cool
why: free stew forever | the red and white spots | they're so rare
likere: free stew | they're like cows in costume
hate: mooshrooms are so rare | mooshroom islands are so far | mooshrooms are weird looking
hatere: bring a boat and lead two home | that's what makes them special | they're cute weird
tip: milk a mooshroom with a bowl for free stew | shearing one turns it into a plain cow | lightning turns red mooshrooms brown | feed a brown mooshroom a flower for suspicious stew
setup: so i was sailing for ages looking for land | ok so i got lost at sea in my boat | story time: me, a boat, open ocean
climax: and i found a mushroom island full of mooshrooms | and i ran into a whole island of mooshrooms | and the island i found was all mooshrooms
storyre: jackpot | so rare
coda: built a house there | took two home on leads | best island ever
feel: mooshrooms make me happy | a mushroom island with mooshrooms is the most peaceful place | i'd live with the mooshrooms forever
feelre: no mobs there either | free stew forever | it's like a secret world
feelend: lol | someday | love it
op: mooshrooms are the best animal | mooshrooms beat cows | a mooshroom is worth the boat trip
op+: free stew forever | they're cows plus mushrooms
op-: regular cows do the job | they're too far away | stew doesn't even stack
deb: mooshrooms are better than cows
deb+: free stew, no cooking | yeah, they're cows with a bonus
deb-: they're on a far island | stew doesn't stack
deb++: they still give milk and leather | and steak, same as a cow
deb--: bring two home and breed them | one bowl, one meal, no fire | far away just means one trip
debx: too far for me | bowls are annoying

=== bat | animals,caves | night | meh | bats
see: a bat flew right in my face in the cave | bats squeaking all over the mine | a bat got in my house | bats hanging upside down in the cave
re: they scare me every time | just a bat | squeak | they hang there like decorations
q: what do bats drop? > nothing / nothing at all
q: are bats dangerous? > no, they're harmless / not at all, they just flap around
like: bats are cute | i like bats | bats are kind of adorable
why: they hang upside down | the tiny squeak | they make caves feel alive
likere: the tiny wings | hanging upside down is cute
hate: bats scare me in caves | bats are useless | bats fly into me
hatere: they're harmless | it's just a bat | they were there first
tip: bats are harmless | they hang from ceilings when they rest | a flapping sound in a cave is usually just a bat | bats only spawn in dark places
setup: so i was mining in a dark cave | ok so i'm deep in a cave, super quiet | story time: me, a cave, silence
climax: and a bat flew right into my face | and a bat came screeching out of nowhere | and something flapped right past my head
storyre: bats | jumpscare
coda: almost fell into lava | i screamed | it was just a bat
feel: bats make me jump every time | i'm scared of bats in caves | the squeaking in caves gets in my head
feelre: they're harmless | it's always just a bat | the squeak means nothing
feelend: still | it's the surprise | ok
op: bats are the most useless mob | bats should drop something | bats exist just to scare people
op+: they drop nothing | they're pure jumpscare
op-: they make caves feel alive | they're cute | they're harmless at least
deb: bats are the most useless mob in the game
deb+: they drop nothing and do nothing | yeah, flapping noise and that's it
deb-: silverfish are worse | at least they're harmless
deb++: and they fly into your face mid jump | and they make you think a mob's behind you
deb--: silverfish at least make strongholds scary | harmless and useless is still useless | name one thing a bat is good for
debx: they make caves feel alive | they're cute, that counts

=== ocelot | animals,biomes | pets,trees | good | ocelots
see: saw an ocelot in the jungle | an ocelot ran away from me | an ocelot ate my fish and left | an ocelot trusts me now
re: they're so shy | jungle cats | fish thieves | you got lucky
q: can you tame an ocelot? > no, but you can make it trust you with fish / feed it raw fish slowly, it trusts you
q: what do ocelots do? > scare creepers and phantoms / creepers run from them like cats
q: where do ocelots live? > jungles / jungles, they're shy
like: ocelots are my favourite | i love ocelots | ocelots are so cool
why: the spots | they're wild and free | creepers run from them too
likere: the spots | wild cats
hate: ocelots always run away | ocelots are so hard to befriend | ocelots won't sit like cats
hatere: move slower | bring more fish | that's what cats are for
tip: ocelots can't be tamed, but fish makes them trust you | creepers are scared of ocelots too | move slowly or they run | they hunt chickens and baby turtles
setup: so i spent an hour trying to befriend an ocelot | ok so i'm crouching in the jungle with raw fish | story time: me, an ocelot, a stack of fish
climax: and it ate all my fish then ran off | and it took every fish and bolted | and it ate the whole stack and disappeared
storyre: ocelots are like that | fish thief
coda: out of fish, no ocelot | i'm getting a cat instead | it's out there somewhere, full
feel: seeing an ocelot in the jungle feels lucky | i wish ocelots liked me | ocelots are so graceful
feelre: they're shy, not mean | bring more fish | the jungle's best cat
feelend: maybe | lol | one day
op: ocelots are better than cats | ocelots are the best jungle mob | ocelots are prettier than any cat
op+: the spots | wild and free
op-: cats you can tame | parrots are better | they just run away
deb: ocelots should be tameable again
deb+: yeah, give us jungle cats | i miss tamed ocelots
deb-: that's what cats are for | trust is enough
deb++: they're the original creeper guard | the spots beat any cat
deb--: trust isn't a pet | cats are fine but ocelots are wild cool | let me put one on a leash at least
debx: cats exist | wild is the point

=== mule | animals,storage | pets | good | mules
see: bred a mule today | my mule carries my chest everywhere | a mule followed my donkey around | rode a mule home from the mine
re: half horse half donkey | sturdy guy | mules are underrated | chest on legs
q: how do you get a mule? > breed a horse and a donkey / horse and donkey baby
q: can mules breed? > no / nope, mules can't breed
q: can a mule carry a chest? > yeah, same as a donkey / yes, fifteen slots
like: mules are my favourite | i love mules | mules are underrated
why: a chest on a sturdy body | they look like tough little horses | they never complain
likere: sturdy guys | fair, they're tough
hate: mules can't even breed | making a mule costs golden carrots | mules are stubborn to ride
hatere: breed another horse and donkey | golden carrots are worth it | they're tougher than they look
tip: breed a horse and a donkey for a mule | mules carry chests like donkeys | mules can't breed, so keep yours safe | you still need to tame a mule before you ride it
feel: my mule is my best friend | i love my mule more than my horse | i get attached to every mule
feelre: mules are loyal | they've got heart | give it a name
feelend: already did | lol | yeah
op: mules are better than donkeys | mules are underrated | a mule is the best mining buddy
op+: chest plus horse genes | they're sturdy
op-: a donkey is the same | they can't breed | llamas carry more
deb: mules are the best pack animal
deb+: chest plus the horse half | yeah, they're sturdier
deb-: llamas carry more in a caravan | donkeys are the same thing
deb++: and they're bigger than donkeys | and they look tougher
deb--: a caravan is ten llamas and ten leads, a mule is one | a donkey is a mule without the horse half | you can steer a mule, try that with a llama
debx: llamas for big trips | donkeys do the same
''')

# --- Biomes of the overworld.
DATA.append(r'''
=== plains | biomes,exploring | building,home | meh | the plains
see: walked across the plains all morning | the plains go on forever out here | found a village on the plains | horses all over the plains today
re: flat and easy | plains are so open | perfect for building | villages love plains
q: what's good about the plains? > flat land, villages, horses / easy building, villages and horses
q: why are there so many mobs on the plains at night? > it's wide open and unlit / nothing's lit out there, they spawn everywhere
q: what's special about sunflowers? > they always face east / they point east, like a compass
like: the plains are my favourite biome | i love the plains | the plains feel like home to me
why: it's flat, building is easy | you can see mobs coming | sunsets over the plains are the best
likere: flat is underrated | easy living
hate: the plains are so boring | there's no wood on the plains | the plains are too open at night
hatere: plant a tree farm | flat means easy building | light it up
tip: sunflowers always face east, use them as a compass | the plains are the best place for a first base | there are villages and horses on the plains | bring saplings, trees are rare out there
setup: so on my first night i was out on the plains | ok so i got caught on the open plains at dusk | story time: first night, open plains, no shelter
climax: and mobs came from every direction | and i could see zombies coming from a mile away, all of them | and there was nowhere to hide
storyre: nothing to hide behind | the plains at night
coda: dug a hole and waited it out | built my first dirt hut that night | i always build before dark now
feel: the plains feel peaceful | i feel safe on the plains in the day | the plains at night make me nervous
feelre: so open | wide skies | nowhere to hide though
feelend: yeah | lol | exactly
op: the plains are the best biome for a base | the plains are underrated | flat land is the best land
op+: building on flat land is so easy | villages and horses right there
op-: they're boring | no trees | too exposed
deb: the plains are the best biome in the game
deb+: flat, open, villages, horses | yeah, it has everything
deb-: forests have wood | they're too boring
deb++: and you can see every mob coming | and building is so easy
deb--: wood is a sapling away | boring is safe | a forest hides creepers
debx: forests still | boring is boring

=== forest | biomes,trees | building,animals | meh | the forest
see: chopped some oak in the forest | the forest is so dark at night | wolves in the forest earlier | found a cave in the middle of the forest
re: oak everywhere | creepers hide in there | wood for days | wolves love forests
q: what trees grow in a forest? > oak and birch / oak mostly, some birch
q: why are forests dangerous at night? > mobs hide behind the trees / you can't see what's behind the trunks
q: where do apples come from? > oak leaves, sometimes / break oak leaves, they drop apples now and then
like: the forest is my favourite biome | i love the forest | forests feel like home
why: wood everywhere | the leaves are so pretty | it's cozy under the trees
likere: wood for days | cozy
hate: forests are scary at night | forests are a maze | i always get lost in the forest
hatere: light it up | pillar up and look around | follow the sun
tip: forests have all the oak and birch you need | creepers hide behind trees, look around trunks | apples drop from oak leaves | wolves spawn in forests, bring bones
setup: so i was chopping a tree in the forest at dusk | ok so i'm cutting oak in the forest as it got dark | story time: me, an axe, the forest, sunset
climax: and a creeper came around the trunk | and a creeper walked out from behind the tree | and a hiss came from right behind the tree
storyre: trees hide everything | creeper ambush
coda: lost half my wood | the tree's gone now, and so is the hill | i only chop in the day now
feel: the forest makes me feel at home | walking under the leaves is so calming | the forest at night scares me
feelre: cozy | the light through the leaves | creepers love the dark trees
feelend: yeah | lol | same
op: the forest is the best starting biome | forests are the coziest biome | every base should be in a forest
op+: wood right there | it's so cozy
op-: too many places for mobs to hide | plains are easier | it's dark all the time
deb: the forest is the best starting biome
deb+: wood, animals and caves | yeah, everything you need
deb-: the plains are easier | mobs hide in the trees
deb++: and apples from the leaves | and wolves to tame
deb--: in the plains you start with no wood | a few torches fix the dark | caves under the trees are perfect
debx: plains still | too many creepers

=== birch_forest | biomes,trees | building,plants | good | the birch forest
see: walked through a birch forest | the birch trees are so pretty today | found bees in the birch forest | saw the really tall birches in the old growth part
re: birch is so pretty | so bright in there | bees love birch | the tall ones are amazing
q: what's birch wood good for? > light builds, it's pale / it's light coloured, good for clean builds
q: why is the birch forest so bright? > the trees are pale and thin / white bark, lots of light
q: what's an old growth birch forest? > a birch forest with really tall trees / extra tall birches
like: the birch forest is my favourite | i love birch forests | birch forests are so pretty
why: it's so bright | the white bark | it feels clean
likere: so bright | the white bark
hate: birch wood is ugly | birch forests are boring | birch planks look like paper
hatere: it's light, that's nice | it's pretty | use it with dark oak
tip: birch planks look great with dark oak | birch forests have bee nests | birch trees are short, easy to chop | old growth birch forests have huge trees
setup: so i built my whole house out of birch | ok so i made my house all birch, walls floor and roof | story time: me, a stack of birch, a house plan
climax: and from far away it was just a white block | and it looked so plain and pale | and it was blinding in the sun
storyre: too much birch | birch needs a friend
coda: added dark oak trim, way better | put spruce on the roof, saved it | now it's my favourite house
feel: the birch forest makes me happy | walking through birch trees is so calming | the birch forest feels so clean
feelre: so bright | peaceful | the white bark
feelend: yeah | lol | love it
op: birch forests are the prettiest forest | birch is the best wood | birch is underrated
op+: so bright and clean | the white bark is beautiful
op-: birch looks like paper | spruce is prettier | too pale
deb: birch is the best wood for building
deb+: light, clean, goes with everything | yeah, it makes any room bright
deb-: spruce has way more character | it's too pale on its own
deb++: and the trees are short, fast to chop | and the leaves are a nice light green
deb--: spruce is gloomy, birch is fresh | pale is the point, add some trim | a birch floor brightens any room
debx: spruce forever | still too pale

=== dark_forest | biomes,trees | danger,structures | bad | the dark forest
see: walked through a dark forest | mobs spawn in the dark forest in the day | huge mushrooms in the dark forest | found a woodland mansion in a dark forest
re: so dark in there | mobs everywhere | dark oak though | mansions are creepy
q: why do mobs spawn in the dark forest during the day? > the canopy blocks the light / it's so dark under the leaves
q: how do you grow dark oak? > four saplings in a square / plant four saplings in a two by two
q: where are woodland mansions? > dark forests, rare / deep in dark forests, use an explorer map
like: the dark forest is my favourite | i love the dark forest | dark forests are so cool
why: the giant mushrooms | dark oak looks amazing | it's spooky
likere: spooky | dark oak
hate: the dark forest is terrifying | the dark forest is a maze | mobs in the dark forest at noon
hatere: torches | go around | bring a sword
tip: mobs spawn in the dark forest even in daytime, the leaves block the light | dark oak needs four saplings in a square | huge mushrooms grow there | woodland mansions hide deep in dark forests
setup: so i cut through a dark forest at noon thinking i'd be safe | ok so i walked into a dark forest in broad daylight | story time: me, noon, a dark forest shortcut
climax: and zombies came at me like it was midnight | and there were skeletons waiting under the leaves | and a creeper came out of the shade
storyre: the canopy | daylight means nothing in there
coda: ran out the other side | took the long way home | never taking that shortcut again
feel: the dark forest scares me | i get nervous under the dark oak canopy | the dark forest gives me chills even at noon
feelre: bring torches | the leaves block everything | it's spooky but pretty
feelend: still | i'll go around | yeah
op: the dark forest is the scariest biome | the dark forest is worse than caves | nobody should live in a dark forest
op+: mobs at noon, enough said | you can't see anything under the leaves
op-: a few torches and it's fine | the deep dark is scarier | it's beautiful in there
deb: dark oak is the best wood in the game
deb+: that deep brown is perfect | yeah, it makes builds look rich
deb-: it's too dark | spruce is better
deb++: and one tree gives a ton of logs | and it pairs with everything
deb--: too dark is what windows are for | spruce is dark oak's cousin, but worse | one dark oak tree is a full stack of logs
debx: spruce | it's gloomy

=== flower_forest | biomes,plants | decor,animals | good | the flower forest
see: found a flower forest | picked a whole bunch of flowers | bees everywhere in the flower forest | rabbits hopping through the flower forest
re: so many colours | dye heaven | bees love it | rabbits too
q: what's in a flower forest? > every flower, bees and rabbits / tons of flowers, so tons of dyes
q: why go to a flower forest? > dyes, every colour / all the flowers for dyes
q: can you bone meal flowers? > yeah, bone meal on grass grows that biome's flowers / bone meal the grass there and flowers pop up
like: the flower forest is my favourite | i love flower forests | flower forests are so pretty
why: every colour at once | the bees | it's so peaceful
likere: the colours | the bees
hate: flower forests are dark at night under the trees | too many flowers, i can't place anything | the flower forest is all bees
hatere: light it up | the dyes make up for it | bees are friends
tip: flower forests have almost every flower, great for dyes | bone meal on the grass there grows more of its flowers | bees nest in the trees | rabbits live there too
setup: so i went to a flower forest to get every dye | ok so i needed every colour of dye | story time: me, a flower forest, empty pockets
climax: and i had every colour by sunset | and i filled every slot with flowers in an hour | and i picked a whole inventory of flowers
storyre: dye heaven | so many colours
coda: now i dye everything | rainbow wool everywhere | my chests are full of dye
feel: the flower forest makes me happy | walking through the flowers is so peaceful | i always feel lighter in a flower forest
feelre: every colour at once | the bees humming | it's so peaceful
feelend: yeah | lol | love it
op: the flower forest is the prettiest biome | the flower forest is the best place in the overworld | nothing beats a flower forest
op+: every colour at once | bees, rabbits and flowers, perfect
op-: the cherry grove is prettier | it's just flowers | a meadow is nicer
deb: the flower forest is prettier than the cherry grove
deb+: every colour beats one colour | yeah, it's a rainbow
deb-: the pink trees win | cherry petals are magic
deb++: and it has bees and rabbits | and you get every dye
deb--: pink is one colour, the flower forest is all of them | petals are nice, a sea of flowers is nicer | you can't dye a rainbow with pink
debx: pink trees forever | cherry grove wins

=== taiga | biomes,trees | animals,villagers | meh | the taiga
see: walked through the taiga | sweet berries all over the taiga | foxes in the taiga tonight | found a taiga village
re: spruce everywhere | free berries | foxes | taiga villages are cozy
q: what grows in the taiga? > spruce and sweet berries / spruce trees and berry bushes
q: what lives in the taiga? > foxes, wolves, rabbits / foxes and wolves mostly
q: why do berry bushes hurt? > they poke you and slow you / walking through them hurts, go around
like: the taiga is my favourite biome | i love the taiga | taigas are so cozy
why: spruce everywhere | free berries | the foxes
likere: spruce | foxes
hate: berry bushes hurt | the taiga is so dark | the taiga is always cold
hatere: walk around the bushes | the berries are worth it | light it up
tip: sweet berries are food you can grab right away | berry bushes hurt you if you walk through them | spruce makes great dark planks | foxes and wolves live in the taiga | taiga villages have spruce houses
setup: so i was running from a zombie through the taiga | ok so i'm sprinting through the taiga with a zombie behind me | story time: me, the taiga, a zombie chasing me
climax: and i ran straight into a patch of berry bushes | and i got stuck in berry bushes | and berry bushes slowed me to a crawl
storyre: the bushes always win | ouch
coda: the zombie caught me in there | got hurt by the bush and the zombie | at least i grabbed some berries
feel: the taiga feels so cozy | spruce trees make me happy | the taiga feels like home
feelre: spruce and snow | cozy | foxes
feelend: yeah | lol | love it
op: the taiga is the coziest biome | taiga villages are the best villages | the taiga is the best place to live
op+: spruce is perfect | the spruce houses are so cozy
op-: too dark | the berry bushes hurt | plains villages are nicer
deb: the taiga is the best biome for a base
deb+: wood, berries and wolves | yeah, everything a base needs
deb-: it's too dark | the bushes hurt
deb++: and foxes for company | and the spruce houses look amazing
deb--: torches fix dark | you walk into a bush once and learn | free food on every bush
debx: plains still | too cold

=== snowy_taiga | biomes,trees | weather,animals | meh | the snowy taiga
see: snow everywhere in the snowy taiga | white foxes in the snowy taiga | found an igloo in the snowy taiga | wolves in the snow
re: white foxes | snow on spruce | igloos | so cold
q: what lives in a snowy taiga? > white foxes, wolves, rabbits / foxes, wolves and rabbits
q: does it rain in the snowy taiga? > no, it snows / it snows instead
q: what's in an igloo? > a bed, a furnace, sometimes a basement / sometimes a secret basement under the carpet
like: the snowy taiga is my favourite | i love the snowy taiga | snowy taigas are so pretty
why: white foxes | snow on the spruce | it's so quiet
likere: white foxes | snow
hate: the snowy taiga is so cold | strays in the snowy taiga | snowy taigas are dark
hatere: build a fire | shield up | torches
tip: white foxes only spawn in snowy taigas | igloos can have hidden basements | strays spawn there at night | snow piles up on the ground
setup: so i found an igloo in the snowy taiga | ok so i walked into an igloo in the snow | story time: me, an igloo, a suspicious carpet
climax: and under the carpet was a trapdoor to a basement | and there was a trapdoor under the carpet, with a ladder down | and the carpet hid a ladder to a secret basement
storyre: igloo basements are wild | the carpet trick
coda: a zombie villager was locked up down there | brewing stand, a chest, and a zombie villager in a cell | i cured the zombie villager down there
feel: the snowy taiga makes me happy | snow on spruce trees is so pretty | the snowy taiga is so peaceful
feelre: everything goes quiet in the snow | it's like a painting | white foxes everywhere
feelend: yeah | lol | love it
op: the snowy taiga is the prettiest biome | snow and spruce is the best combo | the snowy taiga beats every other forest
op+: so quiet with the snow | white foxes seal it
op-: too cold to live in | strays ruin it at night | the cherry grove is prettier
deb: the snowy taiga is better than the regular taiga
deb+: snow makes everything prettier | yeah, and white foxes
deb-: too cold for a base | strays at night
deb++: and igloos with secret basements | and the quiet when it snows
deb--: cold just means a campfire inside | strays at least look cool | white foxes beat orange ones
debx: regular taiga is comfier | still too cold

=== old_growth_taiga | biomes,trees | mining,plants | meh | the old growth taiga
see: walked through an old growth taiga | the giant spruces there are huge | podzol everywhere in the old growth taiga | mossy boulders all over the old growth taiga
re: giant trees | podzol is nice | free mossy cobble | so tall
q: what's podzol good for? > mushrooms grow on it in any light / you can plant mushrooms on it anywhere
q: what's special about old growth taiga? > giant two by two spruces, podzol, mossy boulders / huge spruces and mossy rocks
q: how do you grow a giant spruce? > four spruce saplings in a square / plant four in a two by two
like: the old growth taiga is my favourite | i love the giant spruces | old growth taigas are so epic
why: the trees are huge | podzol everywhere | the mossy boulders
likere: huge trees | mossy rocks
hate: the old growth taiga is so dark | giant spruces are a pain to chop | i always get lost in the old growth taiga
hatere: torches | one tree is a lot of wood | pillar up and look around
tip: four spruce saplings in a square grow a giant spruce | podzol lets mushrooms grow in any light | the mossy boulders are free mossy cobblestone | brown mushrooms grow all over the floor | silk touch gets you podzol
setup: so i tried to chop down a giant spruce | ok so i decided to cut a giant spruce for wood | story time: me, an axe, a giant spruce
climax: and it took me the whole day | and the top was so high i had to pillar up | and my axe broke halfway up
storyre: giant trees are giant | oof
coda: got like four stacks of logs though | left the top floating, sorry | i'm never doing that again
feel: the old growth taiga makes me feel tiny | i love how ancient it feels | the giant spruces make me feel small
feelre: they're so tall | it feels ancient | like a giant's forest
feelend: yeah | lol | love it
op: the old growth taiga is the most epic forest | giant spruces are the best trees | the old growth taiga is the best place for a treehouse
op+: those trunks are huge | mossy boulders and podzol, perfect
op-: too dark | they're a pain to chop | jungle trees are bigger
deb: giant spruces are better than jungle trees
deb+: taller and straighter | yeah, and no vines
deb-: jungle trees have cocoa | jungle trees are wider
deb++: and the podzol underneath | and they look properly ancient
deb--: cocoa beans are one thing, podzol and moss is way more | wider isn't better, taller is | no vines tangling everything
debx: jungle trees forever | cocoa

=== jungle | biomes,trees | exploring,structures | meh | the jungle
see: got lost in the jungle again | parrots everywhere in the jungle | found a jungle temple | melons growing wild in the jungle
re: so dense | parrots | temple loot | free melons
q: what's in the jungle? > parrots, ocelots, melons, cocoa beans / temples, parrots and cocoa
q: where do cocoa beans come from? > they grow on jungle logs / the sides of jungle trees
q: how do i not get lost in the jungle? > climb a tree and look / build up above the leaves
like: the jungle is my favourite | i love the jungle | jungles are so cool
why: the trees are huge | parrots everywhere | it feels wild
likere: wild | parrots
hate: the jungle is a maze | i hate the jungle | jungle leaves everywhere
hatere: climb a tree | shears clear leaves fast | it's pretty though
tip: cocoa beans grow on jungle logs | melons grow wild in jungles | climb a tree to see over the canopy | jungle temples have arrow traps, watch for tripwire | parrots and ocelots live there
setup: so i went into the jungle without a map | ok so i walked into the jungle for an hour | story time: me, the jungle, no idea where i was
climax: and i couldn't find my way out | and every direction looked the same | and the leaves were so thick i lost the sun
storyre: jungles do that | classic
coda: climbed a giant tree and saw my house | wandered for a whole day | i build pillars now
feel: the jungle feels so alive | i feel lost in the jungle | the jungle is overwhelming
feelre: it's so loud with parrots | the leaves swallow you up | climb up and it opens out
feelend: yeah | lol | true
op: the jungle is the hardest biome to cross | the jungle is a nightmare | nobody should build in a jungle
op+: the leaves are a maze | vines everywhere
op-: it's beautiful in there | the trees make it easy, just climb | it's worth it for the temples
deb: the jungle is the best biome to explore
deb+: temples, parrots, melons | yeah, so much to find
deb-: it's a maze | you can't see anything
deb++: and the trees are huge | and cocoa beans
deb--: climb a tree and the whole jungle is a map | getting lost is the adventure | melons, cocoa, parrots and a temple, what else has that
debx: still a maze | i'll stick to the plains

=== bamboo_jungle | biomes,plants | animals,building | good | the bamboo jungle
see: found a bamboo jungle | pandas in the bamboo jungle | so much bamboo out there | got lost in the bamboo
re: pandas | bamboo forever | so green | bamboo maze
q: what lives in a bamboo jungle? > pandas, parrots, ocelots / pandas mostly
q: what's bamboo good for? > scaffolding, sticks, bamboo planks, fuel / scaffolding and bamboo planks
q: how fast does bamboo grow? > super fast / it shoots up really fast
like: the bamboo jungle is my favourite | i love bamboo jungles | bamboo jungles are so pretty
why: pandas | bamboo forever | the green light in there
likere: pandas | green
hate: the bamboo jungle is a maze | bamboo blocks the view | bamboo jungles are so annoying
hatere: break it | a sword cuts it fast | pandas though
tip: a sword breaks bamboo instantly | bamboo makes scaffolding with string | bamboo makes its own planks and rafts | pandas only spawn in bamboo jungles
setup: so i went into a bamboo jungle to find pandas | ok so i'm pushing through a bamboo jungle | story time: me, a sword, wall to wall bamboo
climax: and i cut so much bamboo my inventory filled | and i swung my sword for an hour and still couldn't see | and the bamboo was so thick i walked in circles
storyre: bamboo everywhere | the maze
coda: found the pandas eventually | made a scaffolding tower to get out | i have bamboo forever now
feel: the bamboo jungle is so peaceful | watching pandas in the bamboo is my happy place | the green light in a bamboo jungle is so calming
feelre: the pandas make it | all that green | it's like its own world
feelend: yeah | lol | love it
op: the bamboo jungle is the best jungle | the bamboo jungle beats the regular jungle | the bamboo jungle is underrated
op+: pandas live there | bamboo forever
op-: it's a maze of stalks | the regular jungle has temples | you can't see anything
deb: bamboo is the most useful plant
deb+: scaffolding, fuel, planks | yeah, it does everything
deb-: sugar cane is more useful | wheat feeds everyone
deb++: and it grows so fast | and you can build a raft from it
deb--: sugar cane is just paper | wheat is food, bamboo is everything else | scaffolding alone wins it
debx: sugar cane makes books | wheat feeds you

=== swamp | biomes,plants | danger,mobs | meh | the swamp
see: walked through a swamp | there's a witch hut in the swamp | slimes in the swamp tonight | lily pads everywhere in the swamp
re: swamps are gross | witches | slimes | lily pads
q: what's in a swamp? > slimes, witches, frogs, lily pads / witch huts and slimes
q: when do slimes come out in the swamp? > at night, most on a full moon / nights, more with a fuller moon
q: what's a blue orchid? > a flower that only grows in swamps / blue flower, swamps only
like: the swamp is my favourite | i love swamps | swamps are so cool
why: the frogs | slimes for slimeballs | it's spooky
likere: frogs | spooky
hate: swamps are gross | i hate swamps | the swamp water is so dark
hatere: the frogs make up for it | lily pads are cute | it's spooky, that's the fun
tip: slimes spawn in swamps at night, most on full moons | blue orchids only grow in swamps | witch huts keep spawning witches | lily pads make a path over water | clay is easy to find in swamp water
setup: so i was crossing a swamp at night | ok so i'm walking through a swamp after dark | story time: me, a swamp, the night
climax: and a witch threw poison at me from the hut | and i walked right past a witch hut and the witch came out | and the witch in the hut started throwing potions
storyre: witch huts | swamps at night
coda: drank all my milk and ran | fell in the murky water running away | i go around swamps now
feel: the swamp creeps me out | swamps make me nervous | i kind of love how spooky the swamp is
feelre: the witch hut doesn't help | spooky is fun though | the frogs are cute at least
feelend: still | yeah | lol
op: the swamp is the worst biome | swamps are the ugliest biome | nobody should live in a swamp
op+: murky water and witches | gross water
op-: the frogs are cute | slimeballs are useful | it's spooky, that's cool
deb: the swamp is the worst biome in the game
deb+: witches, slimes and gross water | yeah, it's awful
deb-: frogs live there | slimeballs come from there
deb++: and vines on every tree | and the water is so murky you can't see in it
deb--: frogs are cute but that's it | slimeballs come from slime chunks too | the witches make it a no
debx: the frogs though | i like spooky

=== mangrove_swamp | biomes,plants | building,animals | meh | the mangrove swamp
see: walked through a mangrove swamp | so much mud in the mangroves | mangrove roots everywhere | white frogs in the mangroves
re: mud | roots | white frogs | so tangled
q: what's mud good for? > mud bricks, or dry it into clay / packed mud and mud bricks
q: how do you make packed mud? > mud and wheat / mud plus wheat
q: how do you grow mangroves? > propagules / plant the propagules, they hang under the leaves
like: the mangrove swamp is my favourite | i love mangroves | mangroves are so cool
why: the roots | mud bricks | white frogs
likere: roots | frogs
hate: mangroves are a maze | mud slows you down | mangrove roots everywhere
hatere: boat through the water instead | mud bricks make up for it | the frogs are worth it
tip: mud makes packed mud with wheat, then mud bricks | a water bottle on dirt makes mud | mangrove propagules hang from the leaves, plant them | tadpoles there grow into white frogs
setup: so i tried to walk through a mangrove swamp | ok so i'm crossing the mangroves on foot | story time: me, the mangroves, mud
climax: and i got tangled in the roots for an hour | and the roots and mud took me an hour to cross | and it took an hour of hopping over roots
storyre: mangroves | roots
coda: should've taken a boat | came out covered in mud | got a stack of mud for bricks at least
feel: the mangrove swamp feels so wild | i love the mangroves | mangroves feel ancient
feelre: those roots look like old hands | it's so wild in there | it feels like it's been there forever
feelend: yeah | lol | love it
op: mangroves are better than regular swamps | mangrove wood is the best red wood | mangrove swamps are underrated
op+: mud bricks are great | the roots look amazing
op-: they're a maze | regular swamps are easier | mangrove wood is too red
deb: mud bricks are the best building block
deb+: they look warm | yeah, so cozy
deb-: stone bricks are classier | sandstone looks better
deb++: and they're cheap, mud and wheat | and they're warm and earthy
deb--: stone bricks are grey and boring | sandstone is the desert's, mud bricks go anywhere | cheap and cozy
debx: stone bricks forever | sandstone for me

=== desert | biomes,exploring | structures,danger | meh | the desert
see: crossed the desert today | cactus everywhere in the desert | found a desert pyramid | camels in a desert village
re: so hot | cactus | pyramid loot | camels
q: what's in the desert? > pyramids, villages, wells, husks / temples, villages and husks
q: does it rain in the desert? > never / no, deserts don't get rain
q: how do you get water in the desert? > bring a bucket / carry water, there's little out there
q: what's in a desert well? > suspicious sand, use a brush / brush the sand, there's loot
like: the desert is my favourite | i love the desert | deserts are so cool
why: pyramids | it's wide open | no rain ever
likere: pyramid loot | the open sky
hate: the desert is so empty | no wood in the desert | husks in the desert
hatere: sandstone is free building | bring wood with you | husks are slow at least
tip: bring wood, there are no trees | cactus hurts, careful | desert pyramids have a tnt trap under the middle | husks don't burn in the sun | sand makes glass
setup: so i found a desert pyramid and dug down to the loot room | ok so i'm in a desert temple, going for the chests | story time: me, a pyramid, the secret room
climax: and i landed right on the pressure plate | and my foot hit the pressure plate in the middle | and i stepped right on the plate
storyre: the tnt trap | every time
coda: nine tnt, gone in a flash | the whole room blew, chests and all | respawned at home, loot gone
feel: the desert makes me feel so small | i love how quiet the desert is | the desert at night freaks me out
feelre: all that sand | nothing for miles | husks don't sleep
feelend: yeah | lol | true
op: the desert is the worst biome | deserts are boring | nobody should live in the desert
op+: no wood, no water | husks all day
op-: pyramids and villages | sandstone looks great | camels
deb: the desert is the worst place to live
deb+: no wood, no water, and husks | yeah, it's rough out there
deb-: sandstone houses look great | it never rains, that's nice
deb++: and you can't even farm without hauling water | and cactus pokes you every step
deb--: nice houses can't make up for no trees | no rain also means no growing for free | husks in broad daylight, no thanks
debx: sandstone is worth it | sunny forever though
''')

DATA.append(r'''
=== savanna | biomes,trees | animals,villagers | meh | the savanna
see: walked through the savanna | acacia trees everywhere in the savanna | horses and llamas out in the savanna | a savanna village on the hill
re: acacia | so flat and dry | horses | savanna villages are cute
q: what grows in the savanna? > acacia trees and tall grass / acacia, the orange wood
q: does it rain in the savanna? > no, it's too dry / never, like the desert
q: what lives in the savanna? > horses, llamas, armadillos / horses, donkeys, llamas, armadillos
like: the savanna is my favourite | i love the savanna | savannas are so pretty
why: acacia trees look so cool | horses everywhere | the orange wood
likere: acacia | horses
hate: acacia trees are weird to chop | the savanna is so dry | acacia wood is too orange
hatere: they're pretty though | no rain is nice | orange is warm
tip: acacia planks are bright orange | horses and llamas spawn in savannas | it never rains in the savanna | armadillos live in savannas, scutes for wolf armor | windswept savannas have crazy cliffs
setup: so i tried to chop an acacia tree | ok so i'm cutting down acacia | story time: me, an axe, a crooked acacia
climax: and the branches went every direction | and i left floating logs all over | and the logs were all sideways
storyre: acacia trees are chaos | crooked trees
coda: left a few floating logs, sorry savanna | took forever to get every branch | orange planks for days though
feel: the savanna feels so warm | i love the savanna at sunset | the savanna feels so open
feelre: the orange sky | acacia against the sunset | so open
feelend: yeah | lol | love it
op: the savanna is underrated | the savanna is the most underrated biome | nobody gives the savanna enough credit
op+: acacia and horses, what's not to love | the sunsets there are unreal
op-: it's just dry grass | acacia trees are a mess | there's nothing to do there
deb: acacia is the best wood
deb+: that orange is so warm | yeah, it brightens up a build
deb-: it's way too orange | the trees are crooked, chopping is awful
deb++: and the logs have that grey bark | and it goes great with stone
deb--: too orange just means it needs some stone | crooked trees are half the charm | nothing else is that colour
debx: oak is safer | still too orange

=== badlands | biomes,mining | ore,exploring | meh | the badlands
see: found a badlands biome | terracotta in every colour out there | a mineshaft sticking out of the badlands | found gold in the badlands
re: so colourful | free terracotta | surface mineshafts | gold everywhere
q: what's special about the badlands? > terracotta, red sand, extra gold / colourful terracotta and lots of gold
q: why is there so much gold there? > gold spawns higher up in the badlands / badlands get extra gold ore, even near the surface
q: what lives in the badlands? > armadillos, not much else / armadillos, and the usual mobs at night
like: the badlands are my favourite | i love the badlands | the badlands are so pretty
why: the colours | free terracotta | gold near the surface
likere: colours | gold
hate: the badlands are so empty | no trees in the badlands | the badlands are too far from everything
hatere: the wooded ones have trees | gold makes up for it | it's worth the trip
tip: gold ore spawns a lot higher up in the badlands | terracotta comes in layers of colour | mineshafts in the badlands poke out on the surface | eroded badlands have tall spires | wooded badlands have oak on top
setup: so i walked into the badlands for the first time | ok so i'm exploring the badlands | story time: me, red sand, colourful cliffs
climax: and there was a mineshaft sticking right out of a cliff | and a mineshaft was just poking out of the hillside | and i walked into a mineshaft on the surface
storyre: surface mineshafts | badlands are wild
coda: looted it in daylight | found gold all over it | best day of mining ever
feel: the badlands feel like another world | i love the colours of the badlands | the badlands make me feel tiny
feelre: so colourful | another world | the spires
feelend: yeah | lol | love it
op: the badlands are the most underrated biome | the badlands deserve more love | the badlands are better than people think
op+: gold and terracotta, free | the colours are amazing
op-: it's empty | no trees | too far
deb: the badlands are the best place to get gold
deb+: gold near the surface | yeah, extra gold everywhere
deb-: nether gold ore is everywhere | a gold farm beats mining
deb++: and mineshafts full of rails | and you don't even need to dig deep
deb--: nether gold gives nuggets, badlands gives real ore | a farm needs a lot of setup, badlands gold is right there | no ghasts in the badlands
debx: nether gold is easier | farms win

=== mushroom_fields | biomes,ocean | animals,exploring | good | the mushroom fields
see: found a mushroom island | mooshrooms all over the mushroom island | giant mushrooms on the mushroom island | not a single mob on the mushroom island
re: so rare | mooshrooms | the safest place | giant mushrooms
q: why don't mobs spawn on mushroom islands? > hostile mobs just can't spawn there / no hostile spawns, even at night
q: what's mycelium? > the purple grass on mushroom islands / mushroom dirt, mushrooms grow on it
q: how rare are mushroom islands? > super rare, out in the ocean / very, you have to sail for ages
like: the mushroom fields are my favourite | i love mushroom islands | mushroom islands are the best
why: no mobs | mooshrooms | giant mushrooms
likere: no mobs | mooshrooms
hate: mushroom islands are too far | mushroom islands have no trees | mycelium is ugly
hatere: bring saplings | the safety is worth it | it's unique
tip: hostile mobs don't spawn on mushroom fields, even at night | mooshrooms give stew with a bowl | mycelium spreads and lets mushrooms grow | bring saplings, there are no trees
setup: so i sailed for days and found a mushroom island | ok so i finally found a mushroom island | story time: me, a boat, a mushroom island
climax: and i slept outside for the first time ever | and i walked around at night with no torches | and nothing spawned all night
storyre: safest place in the world | no mobs at all
coda: built my house there | best night ever | moving there for good
feel: mushroom islands make me feel safe | i love how peaceful mushroom islands are | i feel calm on mushroom islands
feelre: not a single zombie | the mooshrooms are so chill | it's the only safe night in the world
feelend: yeah | lol | love it
op: the mushroom fields are the best place to live | mushroom islands are the safest biome | everyone should live on a mushroom island
op+: no mobs ever | mooshrooms
op-: it's too far from everything | there's no wood | it's boring without danger
deb: the mushroom fields are the best biome in the game
deb+: no mobs ever, that beats everything | yeah, the only safe place there is
deb-: there's no wood | it's out in the middle of the ocean
deb++: and free stew from the mooshrooms | and you can build at night with no torches
deb--: bring saplings and plant your own | the ocean around it is the moat | a world without creepers, come on
debx: no wood is a dealbreaker | too far from everything

=== snowy_plains | biomes,weather | animals,exploring | meh | the snowy plains
see: crossed the snowy plains | polar bears on the snowy plains | strays out on the snowy plains | a snowy village out there
re: so cold | polar bears | strays | snowy villages
q: what's on the snowy plains? > polar bears, rabbits, igloos, villages / igloos and snowy villages
q: why is the water frozen? > it's cold, water freezes in snowy biomes / the cold freezes the water
q: what spawns there at night? > strays instead of most skeletons / strays, the frozen skeletons
like: the snowy plains are my favourite | i love the snow | snowy plains are so pretty
why: everything's white | igloos | you can build snow golems right there
likere: white | snow
hate: the snowy plains are so empty | nothing lives on the snowy plains | the snowy plains have no trees
hatere: bring saplings | it's peaceful | igloos though
tip: water on the snowy plains freezes into ice | strays spawn there at night | snow blocks and snowballs everywhere | igloos are out there, check for a basement | crops still grow fine in the snow
setup: so i got lost on the snowy plains in a snowstorm | ok so i was crossing the snowy plains when it started snowing | story time: me, the snowy plains, snow coming down hard
climax: and everything went white and i lost my way | and i couldn't tell which way was home | and every direction looked exactly the same
storyre: snow blind | snowy plains are endless
coda: followed the sunset west | found an igloo and stayed the night | i carry a compass now
feel: the snowy plains feel so lonely | i love how quiet the snow is | standing on the snowy plains at night is eerie
feelre: white for miles | the quiet is nice | strays don't help
feelend: yeah | lol | true
op: the snowy plains are the most boring biome | the snowy plains are the emptiest biome | nobody should live on the snowy plains
op+: nothing but snow | no trees
op-: igloos and villages are out there | it's peaceful | snow is pretty
deb: the snowy plains are the worst place to start
deb+: no trees, no food | yeah, rough start
deb-: there are rabbits to eat | villages out there have food
deb++: and strays at night | and the water's all ice
deb--: rabbits run faster than you on day one | a village is luck, not a plan | no trees means no tools
debx: rabbit stew though | villages are common enough

=== ice_spikes | biomes,weather | exploring,building | good | the ice spikes
see: found an ice spikes biome | the ice spikes are huge | mined packed ice from the spikes | polar bears by the ice spikes
re: so rare | packed ice | ice roads | polar bears
q: what are ice spikes made of? > packed ice / packed ice, it doesn't melt
q: what's packed ice for? > ice roads, boats go super fast on it / boat roads, and blue ice
q: how do you mine packed ice? > silk touch / you need silk touch, or it breaks to nothing
like: the ice spikes are my favourite | i love ice spikes | ice spikes are so pretty
why: so rare | packed ice | they look unreal
likere: rare | unreal
hate: ice spikes are useless | ice spikes are slippery | ice spikes are so cold
hatere: packed ice | silk touch | ice roads
tip: packed ice needs silk touch to mine | nine packed ice make blue ice | boats on ice are super fast | ice spikes biomes are rare, mark the spot
setup: so i found ice spikes and wanted packed ice | ok so i'm at the ice spikes with a pickaxe | story time: me, a pickaxe, a field of ice spikes
climax: and i mined a whole spike and got nothing | and every block broke into nothing | and the ice just vanished when i broke it
storyre: silk touch | no silk touch no ice
coda: came back with silk touch | i have to find a silk touch book now | felt so dumb
feel: the ice spikes make me happy | i love the ice spikes | ice spikes feel magical
feelre: they look unreal | like a frozen forest | the polar bears add to it
feelend: yeah | lol | love it
op: the ice spikes are the coolest biome | the ice spikes are the prettiest biome | ice spikes are worth any trip
op+: they look unreal | packed ice for days
op-: it's just ice | too cold and empty | they're useless without silk touch
deb: ice boat roads are the best way to travel
deb+: boats on ice are insanely fast | yeah, blue ice roads are unmatched
deb-: an elytra is faster | horses go anywhere
deb++: and no fuel, no food | and they work for anyone with a boat
deb--: an elytra needs rockets and the end | a horse can't keep up with a boat on blue ice | build it once, ride it forever
debx: elytra wins | roads take forever to build

=== frozen_ocean | biomes,ocean | weather,danger | meh | the frozen ocean
see: sailed through a frozen ocean | icebergs in the frozen ocean | polar bears out on the ice | had to break ice to get my boat through
re: icebergs | polar bears | so cold | ice everywhere
q: what's in a frozen ocean? > icebergs, polar bears, strays / icebergs, and polar bears on the ice
q: why does the water freeze? > it's cold, the surface freezes / it's a frozen ocean, the top turns to ice
q: what's blue ice? > ice that's even faster, from icebergs / super slippery ice, found in icebergs
like: the frozen ocean is my favourite | i love frozen oceans | frozen oceans are so pretty
why: icebergs | polar bears | blue ice
likere: icebergs | polar bears
hate: the frozen ocean is so annoying to sail | the frozen ocean has strays | boats get stuck in ice
hatere: walk on the ice | shield up | break the ice
tip: icebergs have blue ice inside | you can walk on the frozen surface | strays and drowned spawn there | polar bears hang out on the ice
setup: so i was sailing across a frozen ocean at night | ok so my boat is crossing a frozen ocean | story time: me, a boat, ice everywhere
climax: and my boat got stuck in the ice | and the ice closed in and my boat stopped | and i got wedged between ice and an iceberg
storyre: icebergs | frozen in
coda: had to break my way out block by block | walked the rest on the ice | strays shot me while i was stuck
feel: the frozen ocean feels so lonely | i love the icebergs | the frozen ocean is eerie
feelre: icebergs are beautiful | so lonely out there | eerie for sure
feelend: yeah | lol | true
op: the frozen ocean is the worst ocean | nobody should sail the frozen ocean | the frozen ocean is a boat graveyard
op+: ice everywhere, you can't sail | strays on the ice
op-: you can walk on it | the icebergs are worth it | blue ice
deb: the frozen ocean is the most dangerous ocean
deb+: strays, drowned and freezing water | yeah, and you can't escape by boat
deb-: the deep ocean has guardians | the warm ocean has pufferfish
deb++: and polar bears on the ice | and you can't see under the ice
deb--: guardians stay in their monument | a pufferfish is a joke next to a stray on ice | you get stuck in the ice with mobs on you
debx: guardians are worse | still the deep

=== deep_ocean | biomes,ocean | structures,danger | bad | the deep ocean
see: sailed over the deep ocean | the deep ocean is so dark below | found an ocean monument in the deep ocean | drowned everywhere in the deep ocean
re: so deep | monuments | drowned | dark water
q: what's in the deep ocean? > monuments, shipwrecks, ruins / ocean monuments and drowned
q: how deep is the deep ocean? > deep enough you can't see the bottom / like thirty blocks of water
q: how do i breathe that deep? > water breathing potion or a turtle shell / potions, or a conduit
like: the deep ocean is my favourite | i love the deep ocean | the deep ocean is so cool
why: monuments | it's mysterious | the dark water
likere: mysterious | monuments
hate: the deep ocean is terrifying | the deep ocean is so dark | the deep ocean has guardians
hatere: boat | stay on top | water breathing
tip: ocean monuments are only in deep oceans | bring water breathing to go down | a conduit lets you breathe underwater | drowned are everywhere down there
setup: so i dove down in the deep ocean | ok so i'm diving to the bottom of the deep ocean | story time: me, the deep ocean, a lungful of air
climax: and i ran out of air halfway down | and my air ran out before i found the bottom | and my bubbles were gone before i could come up
storyre: deep ocean | air
coda: made it up with half a heart | drowned at the bottom, everything's still down there | i bring water breathing now
feel: the deep ocean terrifies me | i'm scared of the deep ocean | the deep ocean is so creepy
feelre: the dark water is the worst | you never know what's below | stay in the boat
feelend: i will | still | yeah
op: the deep ocean is the scariest biome | the deep ocean is scarier than caves | nobody should dive the deep ocean
op+: you can't see the bottom | drowned everywhere
op-: it's just water | a boat makes it easy | caves are scarier
deb: the ocean is scarier than the nether
deb+: you can't breathe, you can't see | yeah, the dark water is worse than lava
deb-: the nether has lava everywhere | ghasts beat drowned
deb++: and drowned come from below | and guardians
deb--: lava you can see, the deep ocean hides everything | a ghast you can hear, a drowned just grabs you | running out of air with drowned below is worse
debx: the nether is worse | lava is lava

=== warm_ocean | biomes,ocean | animals,exploring | good | the warm ocean
see: swam through a coral reef | the warm ocean is so colourful | tropical fish everywhere in the warm ocean | found sea pickles in the warm ocean
re: coral | tropical fish | so colourful | sea pickles
q: what's in a warm ocean? > coral reefs, tropical fish, pufferfish, sea pickles / coral and fish
q: how do you get coral? > silk touch, and keep it wet / silk touch, and it needs water or it dies
q: what are sea pickles? > little glowing things on coral / green lights, they glow in groups
like: the warm ocean is my favourite | i love coral reefs | warm oceans are the prettiest
why: coral | tropical fish | colours
likere: coral | fish
hate: coral dies out of water | pufferfish in the warm ocean | the warm ocean is all pufferfish
hatere: keep it wet | avoid them | tropical fish though
tip: coral dies outside water, keep it wet | silk touch to pick up coral blocks | sea pickles glow brighter in groups | warm ocean ruins hide sniffer eggs
setup: so i took coral home for my aquarium | ok so i mined some coral for my house | story time: me, a pickaxe, a coral reef
climax: and it all died and turned grey | and it went grey the moment i placed it | and every block of coral died
storyre: water | coral needs water
coda: coral needs to touch water, who knew | my grey reef is still there, sad | now my aquarium has dead coral and live fish
feel: the warm ocean makes me happy | i love swimming through coral | coral reefs are so peaceful
feelre: the colours | the fish | peaceful
feelend: yeah | lol | love it
op: the warm ocean is the prettiest biome | coral reefs are the best thing in the ocean | nothing beats a coral reef
op+: the colours | fish everywhere
op-: the cherry grove is prettier | it's just coral | pufferfish ruin it
deb: coral is the best decoration block
deb+: every colour, and it's alive | yeah, nothing looks like it
deb-: it dies out of water | you need silk touch
deb++: and sea pickles to light it | and fish swim right through it
deb--: build it underwater, problem gone | silk touch is easy to get | dead coral has its own grey charm
debx: too fussy | dies too easy

=== beach | biomes,ocean | animals | good | the beach
see: walked along the beach | turtles on the beach today | found a shipwreck on the beach | sugar cane growing by the beach
re: turtles | sand | shipwrecks | sugar cane
q: what's on a beach? > turtles, sand, sometimes buried treasure / turtles and shipwrecks
q: where's buried treasure? > under the sand on beaches, follow a map / treasure maps point to beaches
q: why are there turtles on the beach? > they nest there / they lay eggs on their home beach
like: the beach is my favourite | i love the beach | beaches are so nice
why: turtles | the ocean | sunsets
likere: turtles | sunsets
hate: beaches are boring | drowned walk out of the waves | beaches have nothing
hatere: turtles | buried treasure | sunsets
tip: buried treasure is under the sand on beaches, follow a treasure map | turtles nest on beaches, keep zombies away | sugar cane grows on sand next to water | sand makes glass, grab a stack
setup: so i found a treasure map in a shipwreck | ok so i followed a treasure map to a beach | story time: me, a map, a shovel, a beach
climax: and i dug for an hour and found nothing | and i dug up half the beach | and the x was right where i stood and i still missed it
storyre: x marks the spot | maps
coda: found it one block over | heart of the sea inside, worth it | turns out you dig in the middle of the x
feel: the beach makes me happy | i love the beach at sunset | the beach is so peaceful
feelre: the waves | sunsets | turtles
feelend: yeah | lol | love it
op: the beach is the best place for a house | a beach house is the best house | everyone should live by the beach
op+: ocean views | turtles for neighbours
op-: drowned come out at night | sand is a pain to build on | nothing to do there
deb: a beach base is better than a mountain base
deb+: ocean right there, fish and boats | yeah, so easy to get around
deb-: mountains have the view | drowned walk up the beach
deb++: and turtles, and sunsets | and buried treasure under your feet
deb--: the view is the ocean, best view there is | a mountain has goats, i'll take drowned | boats beat climbing
debx: mountains forever | goats are funny

=== river | biomes,ocean | fishing,exploring | meh | the river
see: followed the river downstream | crossed the river on a boat | salmon in the river today | clay at the bottom of the river
re: rivers | boats | salmon | clay
q: what's in rivers? > salmon, squid, clay, drowned / salmon and clay at the bottom
q: where do i find clay? > river beds and swamps / at the bottom of rivers
q: do rivers lead anywhere? > usually to the ocean / follow them and you'll hit the sea
like: rivers are my favourite | i love rivers | rivers are so nice
why: boats | fishing | they connect everything
likere: boats | fishing
hate: rivers are always in the way | drowned in rivers | rivers block my path
hatere: build a bridge | boat | fishing though
tip: follow a river to find the ocean | clay sits on river beds | salmon live in rivers | rivers are boat highways | drowned spawn in rivers at night
setup: so i built my house right by the river | ok so my base is on a river bank | story time: me, my house, the river
climax: and drowned came up out of it every night | and drowned kept climbing out at night | and every night drowned walked out of the river
storyre: river life | drowned
coda: built a wall along the bank | got a trident out of it eventually | i keep my door shut now
feel: rivers make me feel calm | i love sitting by the river | the river is so peaceful
feelre: water | peaceful | fishing
feelend: yeah | lol | love it
op: rivers are the best place to build | every base needs a river | a river base is the best base
op+: water for farms | boats and fishing
op-: drowned at night | they're in the way of building | the bank is muddy
deb: rivers are the best way to travel
deb+: boats are fast and safe | yeah, rivers go everywhere
deb-: roads are faster | rivers wind around
deb++: and most mobs can't follow a boat | and they lead to the ocean
deb--: a road takes days to build, a river is free | winding is the scenic route | zombies can't catch a boat
debx: roads | too winding

=== meadow | biomes,plants | animals,building | good | the meadow
see: walked through a meadow under the mountains | bees in the meadow | flowers all over the meadow | found a village in a meadow
re: so pretty | bees | flowers | mountains
q: where are meadows? > up on mountains, below the peaks / on mountain slopes
q: what's in a meadow? > flowers, bees, rabbits, donkeys / flowers and bee nests
like: the meadow is my favourite | i love meadows | meadows are so pretty
why: flowers | bees | the mountain view
likere: flowers | the view
hate: meadows are too high up | meadows have no trees | meadows are boring
hatere: the view | bees | flowers
tip: meadows sit below mountain peaks | bee nests on lone oak and birch trees there | pillager outposts can spawn in meadows, careful | donkeys and sheep wander the meadow
setup: so i built a house in a meadow | ok so i picked a meadow for my base | story time: me, a meadow, a perfect spot
climax: and a pillager outpost was right over the next hill | and turns out there was a pillager outpost right over the hill | and the next morning i saw a pillager outpost over the ridge
storyre: perfect spot, terrible neighbours | meadows hide outposts
coda: i moved one meadow over | fought them off every night for a week | it's still the best view i ever had
feel: meadows make me happy | the meadow is so peaceful | watching the bees in the meadow calms me
feelre: flowers | bees | peaceful
feelend: yeah | lol | love it
op: the meadow is the best biome for a house | meadows are the prettiest biome | a meadow base beats everything
op+: flowers and a mountain view | bees and sheep for neighbours
op-: too high up | outposts love meadows | no trees
deb: the meadow is prettier than the flower forest
deb+: open sky and flowers | yeah, and the mountains behind
deb-: the flower forest has more flowers | meadows are too empty
deb++: and no trees blocking the view | and you can see the peaks
deb--: more flowers isn't prettier, more sky is | empty means room to build | a flower forest is dark under the trees
debx: flower forest | too empty

=== cherry_grove | biomes,trees | building,decor | good | the cherry grove
see: found a cherry grove | pink petals everywhere | cherry trees are so pink | built a cherry house
re: so pink | petals | cherry wood | pretty
q: where's a cherry grove? > in the mountains / mountain slopes
q: what's cherry wood like? > pink / pink planks
q: what are pink petals? > the flowers on the ground in cherry groves / little petals you can place
like: the cherry grove is my favourite | i love cherry groves | cherry groves are the prettiest
why: everything's pink | the petals | cherry wood
likere: pink | petals
hate: cherry wood is too pink | cherry groves are too pink | cherry groves are overrated
hatere: it's pretty | use it with dark oak | petals
tip: cherry groves are on mountain slopes | cherry planks are pink | pink petals can be placed like flowers, four per block | bees and pigs live in cherry groves
setup: so i built a whole house out of cherry wood | ok so i made my house all pink | story time: me, cherry planks, a pink dream
climax: and you could see it from three mountains away | and it was the pinkest thing in the world | and it glowed pink at sunset
storyre: pink pride | that's a statement
coda: i love it anyway | added stone trim, now it's perfect | still pink, still proud
feel: cherry groves make me happy | i love the falling petals | cherry groves are so peaceful
feelre: pink | petals | peaceful
feelend: yeah | lol | love it
op: the cherry grove is the prettiest biome | nothing beats a cherry grove | cherry groves are the best thing in the game
op+: the petals falling | pink trees, come on
op-: too pink | the flower forest is prettier | it's overrated
deb: cherry wood is the best wood
deb+: pink planks, unbeatable | yeah, it makes any build pretty
deb-: too pink | dark oak is classier
deb++: and the pink leaves | and it goes with white
deb--: too pink is not a thing | dark oak is gloomy | a pink roof makes a house
debx: dark oak | too pink

=== grove | biomes,trees | weather,animals | meh | the grove
see: walked through a grove | powder snow in the grove | wolves in the grove | foxes in the grove
re: spruce and snow | powder snow | wolves | foxes
q: what's a grove? > a snowy spruce forest on mountain slopes / snowy forest below the peaks
q: what's powder snow? > snow you sink into / you fall through it, wear leather boots
q: how do you walk on powder snow? > leather boots / wear leather boots
like: groves are my favourite | i love groves | groves are so pretty
why: snow | spruce | foxes
likere: snow | spruce
hate: powder snow in groves | groves are cold | i sank in powder snow
hatere: leather boots | it's pretty | bucket it
tip: leather boots let you walk on powder snow | powder snow freezes you if you sink in | scoop powder snow with a bucket | groves sit on mountain slopes under the peaks
setup: so i was walking through a grove | ok so i'm crossing a grove | story time: me, a grove, snow
climax: and i fell into powder snow | and i sank into powder snow | and i was stuck in powder snow
storyre: powder snow is a trap | the freeze is so fast
coda: started freezing before i dug out | nearly froze to death in there | leather boots, every time now
feel: groves feel so quiet and cold | i love walking through snowy spruce | powder snow makes me nervous in groves
feelre: snow on spruce is the best | so peaceful | boots, always boots
feelend: yeah | lol | true
op: groves are the prettiest snowy biome | groves beat the snowy taiga | groves are underrated
op+: spruce and powder snow | the mountain backdrop
op-: powder snow ruins it | the snowy taiga is nicer | too cold
deb: powder snow is the most dangerous block
deb+: you sink and freeze | yeah, it looks like normal snow
deb-: lava is way worse | you just need leather boots
deb++: and it looks exactly like normal snow | and the freeze damage keeps going
deb--: lava glows, powder snow hides | boots help, if you remembered them | more people fall in powder snow than lava, i bet
debx: lava is lava | just wear boots
''')

DATA.append(r'''
=== snowy_slopes | biomes,weather | danger,animals | meh | the snowy slopes
see: climbed the snowy slopes today | goats all over the snowy slopes | powder snow all over the slopes | a pillager outpost up on the snowy slopes
re: goats everywhere | powder snow is a trap | so steep | the view must be good
q: what lives on snowy slopes? > goats and rabbits / goats mostly
q: why is it so hard to climb the slopes? > it's steep and full of powder snow / powder snow and cliffs
q: where do the slopes go? > up to the peaks / they lead up to the mountain tops
like: the snowy slopes are my favourite | i love the snowy slopes | snowy slopes are so pretty
why: the view from up there | the goats are hilarious | fresh snow everywhere
likere: the view | goats
hate: the snowy slopes are a nightmare to climb | goats on the snowy slopes ruin everything | powder snow on the slopes got me again
hatere: leather boots | stay away from goats | the view makes up for it
tip: wear leather boots on snowy slopes, powder snow is everywhere | goats ram you off the slopes | bring blocks to make steps | snowy slopes lead up to the peaks
setup: so i was climbing the snowy slopes | ok so i'm halfway up a snowy slope | story time: me, a steep slope, almost at the top
climax: and a goat rammed me all the way back down | and a goat knocked me down the whole slope | and a goat charged me and i slid all the way down
storyre: goats guard the slopes | all that climbing
coda: had to start over | landed in powder snow at the bottom | i went around the goat the next time
feel: the snowy slopes feel so high up | i love the view from the snowy slopes | the snowy slopes make me nervous
feelre: the view is unreal | don't look down | watch for goats
feelend: yeah | lol | true
op: the snowy slopes are the hardest biome to cross | the snowy slopes are the worst part of a mountain | nobody should build on snowy slopes
op+: powder snow and goats | steep and cold
op-: leather boots fix it | the view is worth it | the peaks are harder
deb: mountains are the best place for a base
deb+: the view, and nothing sneaks up on you | yeah, high ground wins
deb-: too hard to climb every day | goats, powder snow, no
deb++: and emeralds in the stone | and you can see everything coming
deb--: build stairs once and you're done | goats are a small price for that view | high ground beats a forest any day
debx: too much climbing | goats though

=== jagged_peaks | biomes,weather | exploring,mining | meh | the jagged peaks
see: climbed to the top of the jagged peaks | the jagged peaks are so sharp | goats up on the jagged peaks | packed ice on the jagged peaks
re: so high | sharp as anything | goats get everywhere | that's a climb
q: how high do the peaks go? > over two hundred blocks up / way up, near the clouds
q: what's up on the peaks? > goats, snow, emerald ore / snow, ice, and emeralds in the stone
q: what's emerald ore? > a rare ore that only shows up in the mountains / emeralds, mountains only
like: the jagged peaks are my favourite | i love the jagged peaks | the jagged peaks are epic
why: the view goes forever | you're above the clouds | the sharp tops look amazing
likere: the view | so epic
hate: the jagged peaks are terrifying | the jagged peaks are impossible to climb | the jagged peaks are too high
hatere: bring a water bucket for the way down | pillar up | the view though
tip: emerald ore only spawns in mountains | bring a water bucket for the way down | goats live up there | the peaks are covered in snow and packed ice
setup: so i climbed to the very top of the jagged peaks | ok so i made it to the tip of a jagged peak | story time: me, the highest point around
climax: and i could see the whole world from up there | and the view was unreal | and the clouds were right at my level
storyre: worth the climb | the view
coda: then i slipped all the way down, worth it | i stayed till sunset | i want to build up there now
feel: the jagged peaks make me feel so small | i love being on top of the world | heights scare me, the peaks especially
feelre: the view | so high | don't look down
feelend: yeah | lol | true
op: the jagged peaks are the most epic biome | the peaks have the best view in the game | every drifter should climb a peak once
op+: you can see everything | the clouds at your feet
op-: too dangerous | goats | it's cold and empty
deb: emeralds are worth more than diamonds
deb+: you can't trade without them | yeah, villagers run on emeralds
deb-: diamonds make gear | emeralds are just money
deb++: and emerald ore is rarer | and a good trade gets you diamond gear anyway
deb--: money buys gear | one librarian is worth a stack of diamonds | diamonds don't buy mending
debx: diamonds are diamonds | i'll mine my own gear

=== stony_peaks | biomes,mining | exploring,ore | meh | the stony peaks
see: climbed the stony peaks | calcite on the stony peaks | the stony peaks are all stone | found emeralds on the stony peaks
re: so much stone | calcite | emeralds | no snow at all
q: what's on the stony peaks? > stone, calcite, gravel, emerald ore / just stone and emeralds
q: what's calcite? > a white stone block / white rock, it's around amethyst too
q: why is there no snow on the stony peaks? > they're the warm kind of peak / stony peaks are warmer than the jagged ones
like: the stony peaks are my favourite | i love the stony peaks | stony peaks are so cool
why: stone everywhere | emeralds | calcite stripes
likere: stone | emeralds
hate: the stony peaks are boring | the stony peaks are all grey | stony peaks are hard to climb
hatere: emeralds though | calcite is pretty | the view
tip: emerald ore spawns in the stony peaks | calcite is white and pretty | gravel falls, watch your step | stony peaks have no snow, no powder snow either
setup: so i went mining on the stony peaks | ok so i'm digging into a stony peak | story time: me, a pickaxe, a stony peak
climax: and i found three emerald ores in an hour | and i kept hitting emerald ore | and there was emerald everywhere i dug
storyre: emeralds | lucky
coda: traded them for books | best day | i'm going back tomorrow
feel: the stony peaks feel calm | i love the stony peaks | the stony peaks feel like a quarry
feelre: stone forever | it's like a giant quarry | emeralds in every wall
feelend: yeah | lol | love it
op: the stony peaks are the best mountain for mining | stony peaks are underrated | the stony peaks are the best peaks
op+: emeralds and stone | no powder snow
op-: it's boring and grey | no snow, no fun | the jagged peaks are prettier
deb: the stony peaks are better than the snowy peaks
deb+: no powder snow, no freezing | yeah, stone is honest
deb-: snow peaks are prettier | it's all grey
deb++: and emeralds in every wall | and calcite for white builds
deb--: pretty doesn't stop you freezing | grey is calm | no powder snow pits to fall in
debx: snow peaks are prettier | grey is boring

=== windswept_hills | biomes,mining | ore,exploring | meh | the windswept hills
see: walked through the windswept hills | llamas on the windswept hills | silverfish in the windswept hills | found emeralds in the windswept hills
re: cliffs | llamas | silverfish | emeralds
q: what's in the windswept hills? > llamas, emeralds, silverfish / emerald ore and infested stone
q: why does stone turn into silverfish there? > infested stone / some stone has silverfish hiding in it
q: what's windswept gravelly hills? > the gravel version / same hills, but gravel everywhere
like: the windswept hills are my favourite | i love the windswept hills | windswept hills are so cool
why: the cliffs | the view | llamas
likere: cliffs | llamas
hate: the windswept hills are full of silverfish | the windswept hills are so steep | windswept hills are ugly
hatere: emeralds | the view | llamas though
tip: emerald ore spawns in the windswept hills | some stone there is infested with silverfish | llamas spawn on the hills | there's snow on the tops
setup: so i went looking for emeralds in the windswept hills | ok so i'm digging for emeralds in the hills | story time: me, a pickaxe, the windswept hills
climax: and after an hour i found one emerald ore | and all i found was one emerald | and i got one emerald and five silverfish
storyre: emeralds are rare | one is better than none
coda: traded it for bread lol | the villagers were happy at least | i'm a trader now, sort of
feel: the windswept hills feel wild | i love the cliffs | the windswept hills make me nervous
feelre: the cliffs are wild | the view from the edge | careful near the drops
feelend: yeah | lol | true
op: the windswept hills are the best place for emeralds | windswept hills are underrated | the windswept hills are the best mountain
op+: emeralds in the hills | the cliffs look amazing
op-: silverfish in the stone | too steep | the stony peaks are better
deb: mining for emeralds is a waste of time
deb+: trading gives way more | yeah, one ore an hour
deb-: you need some to start trading | emerald blocks look amazing
deb++: and silverfish in the stone | and villagers give them out for wheat
deb--: start trading with wheat, not ore | nobody makes emerald blocks from mining | an hour for one emerald, come on
debx: mining feels better | i like the hunt

=== dripstone_caves | biomes,caves | mining,danger | bad | the dripstone caves
see: found a dripstone cave | pointed dripstone everywhere | copper all over the dripstone caves | drowned in the dripstone caves
re: spikes | copper | drowned | so pointy
q: what's pointed dripstone? > stalactites and stalagmites / the spiky stone, it falls
q: why are there drowned in the dripstone caves? > drowned spawn there a lot / dripstone caves have extra drowned
q: what's dripstone good for? > cauldrons, dripping water or lava / it drips into cauldrons
like: dripstone caves are my favourite | i love dripstone caves | dripstone caves are so cool
why: the spikes | copper | it looks alien
likere: spikes | copper
hate: dripstone caves are deadly | the spikes fall on me | dripstone caves have drowned
hatere: look up | don't fall | copper
tip: falling on a stalagmite hurts way more | pointed dripstone falls if you break the base | dripstone over a cauldron fills it with water or lava | dripstone caves are full of copper | drowned spawn there a lot
setup: so i was mining in a dripstone cave | ok so i'm digging under dripstone | story time: me, a dripstone cave, a pickaxe
climax: and a stalactite fell on my head | and i broke the block above and a spike came down | and pointed dripstone dropped right on me
storyre: dripstone | look up
coda: half a heart left | lesson learned | i look up now
feel: dripstone caves make me nervous | i love dripstone caves | dripstone caves feel alien
feelre: watch the ceiling | it's like another planet | the drip sounds are creepy
feelend: yeah | lol | true
op: dripstone caves are the most dangerous caves | dripstone caves are deadlier than they look | dripstone caves are the worst place to mine
op+: spikes from above and below | drowned in the pools
op-: look up and you're fine | copper makes it worth it | the deep dark is way worse
deb: dripstone caves are better than lush caves
deb+: copper everywhere | yeah, they look so cool
deb-: lush caves are prettier | dripstone kills you
deb++: and you can farm dripstone for lava | and they look like another planet
deb--: lush caves are pretty, dripstone caves pay | spikes only hurt if you're careless | copper for days beats moss
debx: lush caves are calmer | i'll take moss

=== lush_caves | biomes,caves | animals,plants | good | the lush caves
see: found a lush cave | glow berries in the lush cave | axolotls in the lush cave | an azalea tree above a lush cave
re: so pretty | glow berries | axolotls | azalea
q: how do you find a lush cave? > look for an azalea tree on the surface / azalea trees grow above them
q: what's in a lush cave? > moss, glow berries, dripleaf, axolotls / axolotls, glow berries, moss
q: what are glow berries? > food that glows on vines / berries on cave vines, they light up
like: lush caves are my favourite | i love lush caves | lush caves are so pretty
why: glow berries | axolotls | moss
likere: glow berries | axolotls
hate: big dripleaf tipped me into a pool | lush caves still have mobs | lush caves are too green
hatere: don't stand on dripleaf | torches | it's pretty
tip: azalea trees on the surface mean a lush cave below | big dripleaf tips over if you stand on it | glow berries are food and light | moss spreads with bone meal | axolotls live in the pools
setup: so i was hopping across big dripleaf | ok so i'm jumping across dripleaf | story time: me, dripleaf, a pool below
climax: and it tipped and i fell in | and the leaf folded under me | and it dropped me into the water
storyre: dripleaf | lol
coda: the axolotls laughed at me | soaked | i walk around now
feel: lush caves make me happy | lush caves are so peaceful | i feel calm in lush caves
feelre: glow berries everywhere | the moss looks so soft | axolotls in every pool
feelend: yeah | lol | love it
op: lush caves are the prettiest caves | lush caves are the best caves | lush caves are the best place underground
op+: glow berries | axolotls
op-: dripstone caves are cooler | too green | mobs
deb: a lush cave is the best place for a base
deb+: light from glow berries, food on the vines | yeah, it's a garden underground
deb-: mobs still spawn in the dark parts | it's still a cave, it's damp and dark
deb++: and axolotls in the pools | and moss for bone meal farms
deb--: light it up like any base | damp and dark? it glows | where else do you get a garden with no sun
debx: i like the sky | too damp for me

=== deep_dark | biomes,caves | danger,warden | bad | the deep dark
gate: warden
see: found the deep dark | sculk everywhere in the deep dark | the deep dark is so quiet | a sculk sensor went off near me
re: sneak | sculk | so quiet | ancient cities
q: how deep is the deep dark? > way down, below zero / below y zero
q: what's sculk? > the dark stuff that spreads / blue black blocks, with sensors and shriekers
q: what's a sculk shrieker? > it calls the warden if you set it off / step near it and it screams
like: the deep dark is my favourite | i love the deep dark | the deep dark is so cool
why: sculk | ancient cities | it's eerie
likere: sculk | eerie
hate: the deep dark is terrifying | the deep dark has the warden | the deep dark is too dark
hatere: sneak | wool | stay away
tip: no mobs spawn in the deep dark, only the warden | sneak to avoid sculk sensors | wool blocks vibrations | sculk catalysts spread sculk when mobs die nearby
setup: so i was strip mining near the bottom of the world | ok so i'm digging deep, below zero | story time: me, a pickaxe, deepslate everywhere
climax: and my tunnel broke right into sculk | and suddenly the walls turned to sculk | and i opened a hole into the deep dark
storyre: sneak, sneak, sneak | the deep dark finds you
coda: backed out and blocked it with wool | sneaked away for ten minutes straight | a shrieker went off and i ran
feel: the deep dark terrifies me | the deep dark gives me chills | the deep dark is creepy
feelre: sneak and breathe | the silence is the worst | the sculk pulses like it's alive
feelend: yeah | lol | true
op: the deep dark is the scariest place in the game | the deep dark is scarier than the nether | nobody should go into the deep dark
op+: the silence is the worst part | the warden heartbeat
op-: no mobs spawn there | sneak and it's fine | the nether is scarier
deb: the deep dark isn't worth it
deb+: one wrong step and the warden's out | yeah, the loot isn't worth the risk
deb-: swift sneak and echo shards are only there | ancient cities have the best loot
deb++: and the darkness effect messes you up | and you can't even run, you have to sneak
deb--: loot means nothing if you die with it | swift sneak to sneak better, in the place you need to sneak | one shrieker and it's over
debx: worth it for the books | i'll keep sneaking

=== nether_wastes | biomes,nether | danger,exploring | bad | the nether wastes
see: walked through the nether wastes | lava everywhere in the nether wastes | ghasts in the nether wastes | quartz all over the nether wastes
re: netherrack | lava | ghasts | quartz
q: what's in the nether wastes? > netherrack, lava, ghasts, quartz / quartz, glowstone and ghasts
q: where do i get quartz? > the nether, it's in the netherrack / nether quartz ore, all over the wastes
q: where's glowstone? > hanging from the nether ceiling / on the ceiling, it drops dust
like: the nether wastes are my favourite | i love the nether wastes | the nether wastes are so cool
why: quartz | it's red | lava lakes
likere: quartz | lava
hate: the nether wastes are so ugly | the nether wastes are dangerous | i hate netherrack
hatere: quartz | fire res | it's the nether
tip: nether quartz ore is all over the wastes | glowstone hangs from the ceiling | netherrack burns forever if you light it | lava lakes everywhere, bring fire res
setup: so i was mining quartz in the nether wastes | ok so i'm digging quartz in the wastes | story time: me, a pickaxe, quartz
climax: and i broke a block and lava poured in | and lava came pouring out of the wall | and lava flowed right onto me
storyre: lava pockets | the nether wastes
coda: had fire res, phew | lost all my quartz | i place a block before i mine now
feel: the nether wastes scare me | the nether wastes feel so hot | i hate how red it is
feelre: lava everywhere | it's so red | ghasts crying the whole time
feelend: yeah | lol | true
op: the nether wastes are the ugliest biome | nobody likes the nether wastes | the nether wastes are just red rock
op+: netherrack everywhere | red red red
op-: quartz | they're the classic nether | glowstone
deb: the nether wastes are the best nether biome
deb+: quartz, glowstone, everything | yeah, the classic
deb-: warped forest is safer | crimson forest is prettier
deb++: and the lava lakes are huge | and you can find a fortress
deb--: safe and boring | pretty doesn't give quartz | the wastes have it all
debx: warped is safer | crimson is prettier

=== crimson_forest | biomes,nether | danger,mobs | bad | the crimson forest
see: walked through the crimson forest | hoglins in the crimson forest | piglins in the crimson forest | weeping vines hanging everywhere
re: red | hoglins | piglins | vines
q: what's in the crimson forest? > hoglins, piglins, crimson fungus / hoglins and piglins
q: what's crimson wood like? > red planks, and it doesn't burn / red, fireproof
q: what's a shroomlight? > a light block from the giant fungus / glowy blocks in huge fungi
like: the crimson forest is my favourite | i love the crimson forest | the crimson forest is so cool
why: it's red | shroomlights | crimson wood
likere: shroomlights | red
hate: the crimson forest is so dangerous | hoglins in the crimson forest | the crimson forest is too red
hatere: warped fungus | gold armor | it's pretty
tip: crimson and warped wood don't burn | hoglins live in the crimson forest | shroomlights are in the giant fungi | bring warped fungus to scare hoglins
setup: so i was chopping a huge crimson fungus | ok so i'm cutting crimson stems | story time: me, an axe, the crimson forest
climax: and i found shroomlights inside | and there were shroomlights hidden in the cap | and the cap was full of shroomlights
storyre: shroomlights | nice
coda: my house glows orange now | best lights | worth it
feel: the crimson forest scares me | i love the crimson forest | the crimson forest is eerie
feelre: red everywhere | the hoglins don't help | the weeping vines are creepy
feelend: yeah | lol | true
op: the crimson forest is the prettiest nether biome | the crimson forest is the best nether biome | the crimson forest beats the wastes
op+: shroomlights and red trees | it's the prettiest part of the nether
op-: hoglins everywhere | warped is prettier | too red
deb: crimson is better than warped
deb+: red planks beat teal any day | yeah, crimson just looks warmer
deb-: warped is way safer to live in | teal is cooler
deb++: and shroomlights glow orange in it | and the vines are creepy cool
deb--: safe and teal, sounds boring | cool colours don't make a home | crimson has piglins to trade with
debx: warped forever | teal is calming

=== warped_forest | biomes,nether | mobs,exploring | meh | the warped forest
see: walked through the warped forest | endermen everywhere in the warped forest | warped fungus everywhere | twisting vines in the warped forest
re: teal | endermen | safe | vines
q: why is the warped forest so safe? > mostly endermen spawn there / just endermen and striders
q: what's warped fungus for? > scaring hoglins, and steering striders / hoglins hate it, striders love it
q: what are twisting vines? > vines that grow up / you can climb them
like: the warped forest is my favourite | i love the warped forest | the warped forest is so pretty
why: teal | safe | it's calm
likere: teal | safe
hate: the warped forest is full of endermen | the warped forest is weird | warped wood is ugly
hatere: don't look at them | it's safe | it's pretty
tip: the warped forest is the safest nether biome | endermen spawn a lot there, don't look up | warped fungus scares hoglins | twisting vines grow upward, climb them
setup: so i built a nether base in the warped forest | ok so i set up camp in the warped forest | story time: me, the warped forest, a nether base
climax: and endermen kept wandering through my base | and there were endermen at every window | and endermen kept teleporting into my house
storyre: warped forest tax | endermen love it there
coda: made all my ceilings two high | wore a pumpkin the whole time | it's still my favourite base
feel: the warped forest feels calm | i love the warped forest | the warped forest is peaceful for the nether
feelre: teal is so calming | it's the only quiet place | except the endermen
feelend: yeah | lol | true
op: the warped forest is the best nether biome | the warped forest is the safest place in the nether | every nether base belongs in the warped forest
op+: no ghasts, no piglins | teal and calm
op-: endermen everywhere | it's weird | crimson is prettier
deb: the warped forest is the best place for a nether base
deb+: no ghasts and no hoglins | yeah, it's calm
deb-: too many endermen | the wastes have quartz
deb++: and warped fungus for striders | and the teal is calming
deb--: endermen leave you alone if you don't look | quartz is a walk away | a calm base beats a pretty one
debx: endermen though | quartz

=== soul_sand_valley | biomes,nether | danger,mobs | bad | the soul sand valley
see: walked through the soul sand valley | soul sand slows me down | skeletons in the soul sand valley | blue fire everywhere in the soul sand valley
re: slow | blue fire | skeletons | bones
q: why am i so slow in the soul sand valley? > soul sand / soul sand slows you down
q: what's soul speed? > boots enchant from piglins, faster on soul sand / an enchant you get from bartering
q: what's soul soil? > the other block there, doesn't slow you / it doesn't slow you, makes soul fire
like: the soul sand valley is my favourite | i love the soul sand valley | the soul sand valley is so cool
why: blue fire | bones | eerie
likere: blue fire | bones
hate: the soul sand valley is the worst | soul sand slows me down | skeletons in the soul sand valley
hatere: soul speed | soul soil | shield
tip: soul sand slows you, walk on soul soil | soul speed boots make you fast on it | skeletons and ghasts spawn there | fortresses are often in soul sand valleys | soul fire is blue
setup: so i was crossing a soul sand valley | ok so i'm walking across soul sand | story time: me, the soul sand valley
climax: and skeletons started shooting while i was crawling | and skeletons shot me while i was slow | and i got shot by skeletons while i crawled
storyre: soul sand is a trap | crawling target
coda: made soul speed boots after that | barely made it to the fortress | i bridge over it now
feel: the soul sand valley creeps me out | the blue fire there is weirdly pretty | the soul sand valley feels haunted
feelre: the faces in the sand | blue fire is beautiful though | the bones everywhere don't help
feelend: yeah | lol | true
op: the soul sand valley is the worst nether biome | the soul sand valley is the creepiest place | nobody likes the soul sand valley
op+: slowed down with skeletons shooting | the faces in the sand, no
op-: soul speed fixes it | fortresses are usually there | blue fire is pretty
deb: the soul sand valley is the scariest nether biome
deb+: slow, blue fire, skeletons, ghasts | yeah, everything wants you dead and you can't run
deb-: the basalt deltas are worse | the crimson forest has hoglins
deb++: and ghasts screaming over the valley | and that blue haze everywhere
deb--: the deltas are hard, the valley is haunted | hoglins you can scare off with fungus | you can't scare off soul sand
debx: deltas are worse | hoglins are scarier

=== basalt_deltas | biomes,nether | danger,exploring | bad | the basalt deltas
see: walked through the basalt deltas | magma cubes in the basalt deltas | ash falling in the basalt deltas | basalt pillars everywhere
re: magma cubes | ash | basalt | so hard to walk
q: what's in the basalt deltas? > basalt, blackstone, magma cubes, ghasts / magma cubes and basalt
q: why is it so hard to walk there? > basalt pillars and lava pools / it's all bumps and lava
q: what's blackstone? > nether cobblestone / black stone, works like cobble
like: the basalt deltas are my favourite | i love the basalt deltas | the basalt deltas are so cool
why: basalt | blackstone | it's dramatic
likere: basalt | dramatic
hate: the basalt deltas are the worst | the basalt deltas are a maze | magma cubes in the basalt deltas
hatere: bridge over | it's dramatic | magma cream
tip: blackstone works like cobblestone, tools and furnaces | magma cubes spawn there a lot | bridge over the deltas, don't walk | basalt makes smooth basalt and polished basalt
setup: so i was crossing the basalt deltas | ok so i'm jumping across basalt | story time: me, the basalt deltas
climax: and i missed a jump and landed in lava | and i fell into a lava pool | and i slipped into lava between the pillars
storyre: lava between pillars, classic | the deltas eat people
coda: lost everything | fire res saved me | i bridge over the deltas now
feel: the basalt deltas stress me out | the falling ash in the deltas is weirdly pretty | the basalt deltas feel hopeless
feelre: the ash is so moody | nothing about that place is flat | magma cubes everywhere
feelend: yeah | lol | true
op: the basalt deltas are the worst nether biome | the basalt deltas are the hardest biome to cross | nobody likes the basalt deltas
op+: lava pools between every pillar | magma cubes bouncing at you
op-: blackstone is useful | it's dramatic | the soul sand valley is worse
deb: the basalt deltas are worse than the soul sand valley
deb+: you can't walk two blocks without lava | yeah, and magma cubes everywhere
deb-: soul sand slows you right down | the valley has skeletons and ghasts
deb++: and the ash makes it hard to see | and ghasts float over the deltas too
deb--: slow is better than lava | skeletons you can shield, lava you can't | soul speed fixes the valley, nothing fixes the deltas
debx: soul sand is worse | skeletons though

=== the_end | biomes,end | exploring,danger | meh | the end
see: went to the end | endermen everywhere in the end | chorus fruit on the outer islands | the void is so scary
re: the void | endermen | chorus | purple
q: what's in the end? > the dragon, end cities, chorus fruit / end cities and the dragon
q: how do you get to the outer islands? > throw a pearl into the gateway / ender pearl into the end gateway
q: what's chorus fruit? > it teleports you when you eat it / food that teleports you
like: the end is my favourite | i love the end | the end is so cool
why: the end cities | chorus trees look so alien | the purple and black
likere: the cities are amazing | alien vibes
hate: the end is so empty | the void scares me to death | endermen everywhere in the end
hatere: get an elytra and it's great | don't look down | wear a pumpkin
tip: chorus fruit teleports you randomly | pearl into a gateway to reach the outer islands | bring blocks, the islands are far apart | the void kills you instantly | wear a pumpkin, endermen everywhere
setup: so i was bridging to an outer island | ok so i'm building a bridge out over the void | story time: me, a stack of blocks, the void
climax: and i stepped back to look and there was nothing there | and i placed a block wrong and walked off | and i misjudged one block and stepped into nothing
storyre: the void takes everything | one wrong step
coda: everything i had, gone forever | nothing to pick up, ever | i bridge with railings now
feel: the end feels so empty | the end is so quiet it's eerie | the void makes my stomach drop
feelre: the quiet is the worst part | never look down | it's like floating in nothing
feelend: yeah | ugh | true
op: the end is the coolest dimension | the end is better than the nether | the end is the best place in the game
op+: end cities and elytra | the outer islands are amazing
op-: it's empty | the void is the worst | the nether has more stuff
deb: the end is better than the nether
deb+: elytra and shulker boxes, nothing beats that | yeah, the end has the best loot
deb-: the nether has way more stuff | the end is empty and purple
deb++: and no lava everywhere | and the cities are huge
deb--: the nether has more stuff, the end has better stuff | purple is a vibe | an elytra beats every nether drop
debx: netherite though | the nether has more life
''')

# @@END_DATA@@
