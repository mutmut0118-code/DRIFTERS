"""Writes drifter_talk/topics_gen_debates.json: controversial opinions and long debates between Drifters.

The user: "controversial opinions ... long debates ... as detailed and in depth as possible".

Two kinds, both written below as data in a small text format (one block per subject), so every argument is hand-written
for its subject and the code only arranges them into turns:

* Hot takes (`@` blocks): a spicy opinion about life in the world. Each take makes three topics:
  - `g_debate_hot_<key>`: a says it, b agrees (+trust) or objects, a defends it, b comes round (+trust) or holds firm.
  - `g_debate_why_<key>`: b asks why (the reason comes back as the answer), then buys it or pushes back.
  - `g_debate_spicy_<key>`: b is outraged, a doubles down, b comes round, laughs it off or falls out (-trust). When the
    take names a kind of player who'd hold it, only that kind says this one (a:voice_<kind>).
* Long debates (`@@` blocks): a holds one side, b (usually) the other. Two parts: `g_debate_long_<key>` (round one:
  claims, arguments, examples from their own lives, knocks on each other's side, someone getting heated) and
  `g_debate_long_<key>_2` (round two, a follow_up: more arguments, a concession or a double-down, someone trying to end
  it, a truce, agree to disagree, or a falling-out; friends end warmer, rivals colder). Round one marks the talk
  (`<id>_s` they agree, `<id>_d` they don't, `<id>_h` it got heated), and round two branches on those marks (they last
  the whole talk; only agree/disagree/plan_yes/decline are cleared after each topic). The final turns set agree (+1
  trust) or disagree (-1.5) only when the debate actually ends that way. Round one has a low cost, so round two usually
  gets its turn.

Usage:
    python tools/talk_gen/debates.py src/main/resources/data/drifter/drifter_talk/topics_gen_debates.json
    python tools/talk_gen/debates.py --lint <blocks.txt>       checks a file of blocks in the format below
    python tools/talk_gen/debates.py --play <id part> [n]      prints n random playthroughs of the matching topics

The format (`#` starts a comment line; a field's lines are separated by `|`; a line `kind> text` is that kind of
player's own line: casual newbie builder miner homebody speedrunner nomad hoarder hermit pvp_sweat bandit helper):

    @ key | tags | leads | about | needs | voice
    C: 6 claims, plain statements without "hot take:" (the code frames some: "hot take: ...", "..., fight me")
    W: 8 reasons a gives for it (0-2 when b agrees, 3-5 as the answer to "why?", 6-7 when b is outraged)
    P: 2 lines of b agreeing, with substance
    X: 8 objections b raises (0-2, 3-5, 6-7 the same way); a's comebacks must fit after any of them
    R: 5 comebacks a gives to an objection (0-1, 2-3, 4)
    Y: 3 lines of b coming round, with substance

    @@ key | tags | leads | about | needs | voice_a | voice_b
    AO: 3 a's position (also a fine opener)       BO: 3 b's position (fine as a reply to a's)
    AA: 4 a's arguments for its side               BA: 2 b's arguments for its side
    AL: 2 a's examples from its own life           BL: 2 b's examples from its own life
        (b says AL too when it agrees with a)
    AK: 4 b's jabs at a's side                     BK: 4 a's jabs at b's side
    AG: 2 b conceding a point to a's side          BG: 2 a conceding a point to b's side
    AD: 2 a doubling down, heated
    J:  2 jokes (by b) that ease the tension       E:  2 agree-to-disagree lines about the subject (by b)
    Round one: AO, BO, AA 0-1, AK 0-1, AL, BA, BK 0-1, then AK 2-3 said heatedly or J. Round two: AA 2-3, then AG (b
    bends) and BG back, or BL, AD (heated) or BK 2-3, E. When they agree: AO, AA 0-1, AL, BK 0-1; then BK 2-3.

needs: comma-separated conditions (or empty / -). Tags nether, end, enchanting, potions and redstone add
a:knows_<area> and b:knows_<area> (both of them know that part of the game), unless needs already say who knows what.
"""
import json
import random
import re
import sys

# ---------------------------------------------------------------------------------------------------------------------
# Shared reactions (said across many topics; the substance is in the blocks). Voices: a Drifter of that kind uses its
# own lines most of the time, so each turn takes a few of these at random.

VOICES = ["casual", "newbie", "builder", "miner", "homebody", "speedrunner", "nomad", "hoarder", "hermit", "pvp_sweat",
          "bandit", "helper"]


def pool(spec):
    """'any line | any line\\nvoice: line | line' -> {voice: [lines]}."""
    out = {}
    for row in spec.strip().splitlines():
        row = row.strip()
        if not row:
            continue
        voice, _, rest = row.partition(":") if re.match(r"^[a-z_]+:", row) else ("any", "", row)
        out.setdefault(voice, []).extend(s.strip() for s in rest.split("|") if s.strip())
    return out


AGREE = pool("""
any: true | facts | honestly yeah | finally someone said it | 100% | you're not wrong | no notes | say it louder
any: i've been saying this forever | agreed | can't argue with that | real | correct | yep, same | you get it
any: honestly? yeah | ok yeah, that's real | that's actually true
casual: lol true | yeah fr | ngl yes | fr fr
newbie: oh true! | wait yeah, i think so too | i was thinking that! | yeah that makes sense
builder: agreed, and it looks better too | yes, from a building view too | true, and it's prettier
miner: true, even underground | facts from the mines
homebody: yeah, agree | true, i think about this at home a lot
speedrunner: facts, saves time too | yes. efficient | agreed, next
nomad: true everywhere i've been | seen enough of the world to agree
hoarder: true, and i'd know | agree, i have proof in my chests
hermit: yes. | mm. true | correct. | finally, sense
pvp_sweat: obviously | took you long enough | rare w take from you | only real ones know
bandit: heh, true | for once you're right
helper: i think you're right | yeah that's fair | honestly agree with you | i see it too
""")

DISAGREE = pool("""
any: nah | hard disagree | lol no | absolutely not | that's crazy | worst take i've heard today | respectfully, no
any: nope | where is this coming from | i can't get behind that | that's just wrong | lmao no | you're joking right
any: no way | that's a bad take | hmm, no
casual: nah fr | lol nope | that's wild
newbie: wait really? i don't think so | huh? no way | that doesn't sound right
builder: no, and it looks bad too | aesthetically? no
miner: nah, not down where i dig | no way
homebody: hm, no | not in my house
speedrunner: inefficient take | no. waste of time
nomad: not where i've been | nah, seen too much to agree
hoarder: no, and i'm keeping my opinion | nope, mine says otherwise
hermit: no. | wrong. | no. next
pvp_sweat: L take | that's a noob take | bro is cooked | skill issue opinion
bandit: lol whatever | nah, soft take
helper: hmm, i don't think so | i see it differently | not sure about that one
""")

OUTRAGE = pool("""
any: excuse me?? | take that back | you can't just say that | delete that | who let you cook | say sorry | bro what
any: i'm telling everyone you said that | did you hit your head | unbelievable | the audacity | i need to sit down
any: what is wrong with you | i'm going to pretend i didn't hear that
casual: nah you did not just say that | bro said it with his chest
newbie: is that allowed to say?? | omg no
builder: my eyes hurt reading that | no. absolutely not
miner: i'd rather dig straight down than hear that again
homebody: not under my roof
speedrunner: wrong and slow
nomad: i've walked a thousand blocks and never heard worse
hoarder: i'd throw that take in lava, and i never throw anything
hermit: leave. | no. just no.
pvp_sweat: 1v1 me over that | say that at spawn | i'd fight you for that one
bandit: bold. dumb, but bold
helper: oh no, i like you but no | oh buddy no
""")

GLOAT = pool("""
any: see | glad someone gets it | finally | told you | thank you | great minds | exactly | knew you'd get it
any: that's what i'm saying | at last, someone with taste | right?? | this is why we get along
casual: lol right | ayy
newbie: yay! we agree | omg same brain
builder: taste recognises taste
miner: knew you'd dig it
speedrunner: good. moving on
hoarder: i'm keeping that agreement forever
hermit: good. | mm.
pvp_sweat: obviously i'm right | as always | ez
bandit: smart, for once
helper: glad we see it the same | nice to agree on something
""")

COME_ROUND = pool("""
any: ok... fair point | hm. maybe | dang, ok you got me | i hate that that makes sense | ok i see it now
any: fine, you win this one | huh, never thought of it like that | alright, that's fair | ugh ok fine
any: you might be onto something | ok that's actually a good point | ...ok yeah | i'll allow it
casual: ok ok fair | lol fine
newbie: ohh ok that makes sense | wait yeah you're right
builder: ok, i can picture it now
speedrunner: fine. noted
hermit: fine. | mm. fair.
pvp_sweat: fine, you win this round | lucky argument
bandit: heh, fine
helper: you know what, fair | ok you changed my mind
""")

HOLD = pool("""
any: still no | not buying it | nope, still wrong | agree to disagree | you're still wrong | nice try though
any: doesn't change my mind | i'll pretend you didn't say that | keep telling yourself that | that's a no from me
any: we'll never agree on this | yeah no
casual: nah lol | still nah
newbie: hmm, i still don't think so
builder: still no, sorry
speedrunner: no. moving on
hermit: no. | still no.
pvp_sweat: cope | still an L take | you lost this one
bandit: whatever you say
helper: we'll have to agree to disagree | i still see it differently, sorry
""")

HOLD_HEATED = pool("""
any: you've lost it | i'm never listening to you again | worst thing you've ever said | i need new friends
any: i'm going to pretend this never happened | unbelievable. bye | this is why nobody asks you stuff
pvp_sweat: i'd duel you for this, honestly | you're done
hermit: never talk to me. | no.
helper: ok i'm a little upset ngl
""")

LAUGH_OFF = pool("""
any: lol ok you're insane | i can't with you | you're so weird lol | this is why i like you, you're unhinged
any: ok weirdo | lmao fine, enjoy being wrong | you're a menace | never change, i guess
casual: lmao ok | bro is built different
newbie: lol you're so funny
helper: lol ok, you do you
""")

WHY = pool("""
any: why tho? | explain | based on what? | go on... | how so? | elaborate | and why's that? | wait why
any: ok defend that | i need reasons | what makes you say that?
casual: why lol | ok why
newbie: wait why? | how come?
builder: why though, explain
speedrunner: why. quick
hermit: why. | reason?
pvp_sweat: source? | prove it
bandit: says who?
helper: ooh why do you think so?
""")

STAND = pool("""
any: we'll see who's right | history will prove me right | you'll come around | time will tell | i stand by it
any: write it down, i'm right | fine, be wrong then | you'll see | i'm not taking it back | one day you'll get it
casual: lol whatever, i'm right
newbie: i still think i'm right though
hermit: whatever. | mm.
pvp_sweat: i'm always right, you'll learn | cope harder
helper: it's ok to disagree | no hard feelings
""")

# (Long debates.)
EARLY_SAME = pool("""
any: wait, same | finally someone with taste | no argument here | oh you're preaching to the choir | we agree??
any: easy, yes | same, easily | that's not even a debate, you're right | ok you're right, sadly for the debate
newbie: oh i think so too!
hermit: correct.
pvp_sweat: obviously
helper: totally with you
""")

GLOAT_SAME = pool("""
any: wait we actually agree | nice, i thought this was gonna be a fight | finally, someone sane | love that
any: good, we can stay friends | oh thank goodness
hermit: good.
pvp_sweat: course you do
""")

SAME_END = pool("""
any: good talk | glad we're on the same page | we should start a club | see, this is why we get along
any: finally, a peaceful chat | two smart people agreeing, rare | right opinion, two of us now
hermit: good.
helper: love when we agree
""")

HEATED = pool("""
any: ok now you're just wrong on purpose | i can't believe i'm hearing this | you're doing this to annoy me
any: why are you like this | i'm actually getting mad lol | no, listen to me | how are you this stubborn
any: you're not even listening | are you serious right now
pvp_sweat: you'd lose this in a fight too | bro you're so wrong it hurts
hermit: stop talking.
helper: ok i'm getting a little worked up
""")

CALM = pool("""
any: whoa, calm down | touchy lol | breathe | ok ok, easy | relax, it's just a chat | someone's heated
any: no need to yell | lol did i hit a nerve
pvp_sweat: mad cause you're wrong
hermit: calm.
helper: hey, it's ok, we're just talking
""")

SORRY = pool("""
any: ok sorry for getting loud | my bad, got heated | sorry, i care too much about this | ok i'm calm now
any: sorry for snapping earlier
pvp_sweat: ok i got a bit tilted
helper: sorry, that came out mean
""")

END_ATTEMPT = pool("""
any: can we drop this | ok we're never agreeing on this | let's just agree to disagree | i'm not doing this again
any: truce? | let's talk about something else | fine. moving on | we're going in circles
hermit: enough.
helper: let's leave it there, ok?
""")

TRUCE = pool("""
any: fine, truce | deal | ok ok | for now | fair enough | fine by me | alright, truce
hermit: fine.
helper: deal, no hard feelings
""")

FRIENDLY_END = pool("""
any: love you but you're wrong | still friends. still wrong | we're good, you're just wrong | you're lucky i like you
any: fine, but only because we're friends | truce, buddy
""")

RIVAL_END = pool("""
any: typical | of course you'd think that | why am i not surprised | figures, coming from you
any: whatever, you always do this | of course it's you
""")

REFUSE = pool("""
any: no, we're finishing this | not until you admit it | nope, i'm right and you know it | no truce. you're wrong
any: drop it? you drop it
pvp_sweat: not until you say i won
""")

FINAL_HEAT = pool("""
any: whatever. i'm right | fine. be wrong | unbelievable | i'm done | ok. bye
hermit: done.
""")

GRACIOUS = pool("""
any: see, we can agree on stuff | look at us being reasonable | ok that's big of you | respect for that
helper: thanks for hearing me out
""")

AFTER_TRUCE = pool("""
any: ok we're good | glad we sorted that | peace | good chat actually | that was almost civil
hermit: fine.
""")

BENDS = pool("""
any: ok fine, you've got a point | hm, i'll give you that one | alright, fair
""")

FRAMES = ["hot take: {}", "unpopular opinion: {}", "{}, fight me", "i'll say it: {}", "ngl {}", "controversial but {}",
          "{} and i'm not sorry", "i will die on this hill: {}", "say what you want, {}", "{}. change my mind",
          "real talk, {}", "ok hear me out: {}", "{}, i said what i said", "nobody wants to hear it but {}"]

LORE_TAGS = {"nether": "nether", "end": "end", "enchanting": "enchanting", "potions": "brewing", "redstone": "redstone"}

TAGS = set("""feelings building argument jokes danger mobs home friends town complaints exploring plans mining brag decor
memories animals stories gear night day fighting random caves tools storage trade philosophy biomes structures nether
crafting weather food villagers places end ocean redstone enchanting death ore advice gossip help plants weapons pets
dreams trees farming sleep players potions armor sky health diamonds boredom music doing fishing greeting rivals politics
opinion trust cooking""".split())

FILL_OK = {"a", "b"} | {"%s.%s" % (w, x) for w in "ab" for x in """ally armor base built capital close_call crook day dead
death diamonds disliked_player doing enemy group hate held insult kills leader like liked_player mob mob_count nation
news_what news_who ore ore_count other place plan rival town traded_with""".split()}

CONDITION = re.compile(r"^!?((a|b):)?[a-z_]+$")

# ---------------------------------------------------------------------------------------------------------------------
# The block format.

TAKE_COUNTS = {"C": 6, "W": 8, "P": 2, "X": 8, "R": 5, "Y": 3}
DEBATE_COUNTS = {"AO": 3, "AA": 4, "AL": 2, "AK": 4, "AG": 2, "AD": 2, "BO": 3, "BA": 2, "BL": 2, "BK": 4, "BG": 2, "J": 2, "E": 2}
SECTION = re.compile(r"^([A-Z]{1,2}):\s*(.*)$")


class Block:
    def __init__(self, kind, fields, at):
        self.kind = kind
        self.fields = fields + [""] * 8
        self.at = at
        self.sec = {}

    def get(self, code):
        return self.sec.get(code, [])


def parse(text, where="data", problems=None):
    problems = problems if problems is not None else []
    blocks = []
    cur = None
    for n, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("@"):
            kind = "debate" if line.startswith("@@") else "take"
            fields = [f.strip() for f in line.lstrip("@").split("|")]
            cur = Block(kind, fields, "%s:%d" % (where, n))
            blocks.append(cur)
            continue
        m = SECTION.match(line)
        if not m or cur is None:
            problems.append("%s:%d: not a header or a field line: %s" % (where, n, line[:60]))
            continue
        code, rest = m.groups()
        for item in rest.split("|"):
            item = item.strip()
            if not item:
                continue
            vm = re.match(r"^([a-z_]+)>\s*(.+)$", item)
            cur.sec.setdefault(code, []).append((vm.group(1), vm.group(2).strip()) if vm else (None, item))
    return blocks


def split_list(field):
    return [x.strip() for x in field.split(",") if x.strip() and x.strip() != "-"]


def lint(blocks, problems):
    keys = set()
    for b in blocks:
        key = b.fields[0]
        where = "%s %s" % (b.at, key)
        if not re.match(r"^[a-z0-9_]+$", key):
            problems.append(where + ": key must be [a-z0-9_]")
        if (b.kind, key) in keys:
            problems.append(where + ": key used twice")
        keys.add((b.kind, key))
        tags, leads = split_list(b.fields[1]), split_list(b.fields[2])
        if not 1 <= len(tags) <= 3:
            problems.append(where + ": 1 to 3 tags")
        if not 2 <= len(leads) <= 4:
            problems.append(where + ": 2 to 4 leads")
        for t in tags + leads:
            if t not in TAGS:
                problems.append(where + ": unknown tag " + t)
        if not b.fields[3]:
            problems.append(where + ": no about")
        for c in split_list(b.fields[4]):
            if not CONDITION.match(c):
                problems.append(where + ": odd condition " + c)
        voices = [b.fields[5]] + ([b.fields[6]] if b.kind == "debate" else [])
        for v in voices:
            if v and v != "-" and v not in VOICES:
                problems.append(where + ": unknown voice " + v)
        counts = TAKE_COUNTS if b.kind == "take" else DEBATE_COUNTS
        for code, want in counts.items():
            have = len(b.get(code))
            if have < want:
                problems.append("%s: %s has %d lines, needs %d" % (where, code, have, want))
        for code in b.sec:
            if code not in counts:
                problems.append("%s: unknown field %s" % (where, code))
        seen = set()
        for code, items in b.sec.items():
            for voice, text in items:
                if voice and voice not in VOICES:
                    problems.append("%s: unknown voice %s> in %s" % (where, voice, code))
                if text in seen:
                    problems.append("%s: line twice in block: %s" % (where, text))
                seen.add(text)
                check_line(where + " " + code, text, problems, 75 if code == "C" else 88)


def check_line(where, text, problems, longest=88):
    if len(text) > longest:
        problems.append("%s: too long (%d): %s" % (where, len(text), text))
    if not all(32 <= ord(ch) < 127 for ch in text):
        problems.append("%s: not plain ascii: %s" % (where, text))
    if text != text.lower():
        problems.append("%s: not lowercase: %s" % (where, text))
    for word in re.findall(r"\{([^}]*)\}", text):
        if word not in FILL_OK:
            problems.append("%s: unknown fill word {%s}" % (where, word))
    if "|" in text or ">" in text:
        problems.append("%s: stray | or >: %s" % (where, text))


# ---------------------------------------------------------------------------------------------------------------------
# Building turns.

def lines(subst=(), glue=None, rng=None, k=3, nv=0, kv=2):
    """A turn's lines: its substance (list of (voice, text)), plus k shared reactions and nv voices' own reactions."""
    out = {"any": []}
    for voice, text in subst:
        out.setdefault(voice or "any", []).append(text)
    plain = [t for v, t in subst if v is None]
    if glue:
        out["any"] += rng.sample(glue["any"], min(k, len(glue["any"])))
        kinds = [v for v in glue if v != "any"]
        for voice in rng.sample(kinds, min(nv, len(kinds))):
            own = rng.sample(glue[voice], min(kv, len(glue[voice])))
            if plain:
                own.append(rng.choice(plain))
            out.setdefault(voice, [])
            out[voice] += [t for t in own if t not in out[voice]]
    if not out["any"]:
        for voice in list(out):
            if voice != "any":
                out["any"] += out.pop(voice)
    seen = set()
    out["any"] = [t for t in out["any"] if not (t in seen or seen.add(t))]
    return out["any"] if len(out) == 1 else out


def asks(glue, answers, rng, k=4, nv=2):
    """A question turn: every line expects one of these answers."""
    voiced = lines((), glue, rng, k=k, nv=nv, kv=1)
    wrap = lambda group: [{"line": q, "then": list(answers)} for q in group]
    return wrap(voiced) if isinstance(voiced, list) else {v: wrap(g) for v, g in voiced.items()}


def turn(by, lines_, chance=None, cond=None, mark=None, leads=None):
    t = {"by": by, "lines": lines_}
    if chance is not None:
        t["chance"] = chance
    if cond:
        t["if"] = cond
    if mark:
        t["mark"] = mark
    if leads:
        t["leads"] = leads
    return t


def options(by, opts, cond=None):
    """A turn of options, the first whose conditions hold: (conditions, lines, marks)."""
    t = {"by": by, "options": []}
    for c, l, m in opts:
        o = {"lines": l}
        if c:
            o["if"] = c
        if m:
            o["mark"] = m
        t["options"].append(o)
    if cond:
        t["if"] = cond
    return t


def framed(claim, rng, used):
    frames = [f for f in FRAMES if f not in used]
    rng.shuffle(frames)
    for f in frames:
        s = f.format(claim)
        if len(s) <= 86:
            used.add(f)
            return s
    return claim


def lore_needs(tags, needs):
    if any("knows_" in n or "new_to_" in n for n in needs):
        return needs
    out = list(needs)
    for t in tags:
        if t in LORE_TAGS:
            for who in "ab":
                c = "%s:knows_%s" % (who, LORE_TAGS[t])
                if c not in out:
                    out.append(c)
    return out


def topic(id_, about, tags, leads, turns, needs=None, weight=None, next_=None, follow_up=False, cost=None):
    t = {"id": id_}
    if weight is not None and weight != 1.0:
        t["weight"] = weight
    if needs:
        t["needs"] = needs
    if follow_up:
        t["follow_up"] = True
    t["tags"] = tags
    t["leads"] = leads
    if next_:
        t["next"] = next_
    if cost is not None:
        t["cost"] = cost
    t["about"] = about
    t["turns"] = turns
    return t


def build_take(b):
    key, about = b.fields[0], b.fields[3]
    tags = split_list(b.fields[1])
    leads = split_list(b.fields[2])
    needs = lore_needs(tags, split_list(b.fields[4]))
    voice = b.fields[5] if b.fields[5] not in ("", "-") else None
    C, W, P, X, R, Y = (b.get(c) for c in "CWPXRY")
    out = []
    tags_o = (tags + ["opinion"])[:4]
    tags_a = (tags + ["argument"])[:4]
    leads_o = (leads + ["opinion"])[:4] if "opinion" not in leads else leads
    leads_a = (leads + ["argument"])[:4] if "argument" not in leads else leads

    # 1. Straight: says it; agrees, or objects and maybe comes round.
    rng = random.Random("hot" + key)
    used = set()
    tid = "g_debate_hot_" + key
    no = "t:dh_%s_no" % key
    out.append(topic(tid, about, tags_o, leads_o, [
        turn("a", lines(C[0:2] + [(C[2][0], framed(C[2][1], rng, used)), (C[0][0], framed(C[0][1], rng, used))])),
        turn("b", lines(P[0:1], AGREE, rng, k=2, nv=1), chance=0.4, mark=["agree"]),
        turn("b", lines(X[0:3], DISAGREE, rng, k=2, nv=1), cond=["!t:agree"], mark=[no[2:]], leads=["argument"]),
        turn("a", lines(W[0:2], GLOAT, rng, k=2), chance=0.7, cond=["t:agree"]),
        turn("a", lines(R[0:2] + W[2:3]), cond=[no]),
        turn("b", lines(Y[0:1], COME_ROUND, rng, k=2, nv=1), chance=0.4, cond=[no], mark=["agree"]),
        turn("b", lines((), HOLD, rng, k=3, nv=1, kv=1), chance=0.75, cond=[no, "!t:agree"], mark=["disagree"]),
    ], needs=needs, weight=0.8))

    # 2. Why: b asks, the reason is the answer; b buys it or pushes back.
    rng = random.Random("why" + key)
    used = set()
    no = "t:dw_%s_no" % key
    out.append(topic("g_debate_why_" + key, about, tags_o, leads_o, [
        turn("a", lines(C[2:4] + [(C[4][0], framed(C[4][1], rng, used))])),
        turn("b", asks(WHY, [t for v, t in W[3:6]], rng, k=3, nv=2)),
        turn("a", ["hm", "well"]),
        turn("b", lines(P[1:2], COME_ROUND, rng, k=2, nv=1), chance=0.4, mark=["agree"]),
        turn("a", lines((), GLOAT, rng, k=2), chance=0.5, cond=["t:agree"]),
        turn("b", lines(X[3:6], DISAGREE, rng, k=1, nv=1), cond=["!t:agree"], mark=[no[2:]], leads=["argument"]),
        turn("a", lines(R[2:4]), cond=[no]),
        turn("b", lines(Y[1:2], COME_ROUND, rng, k=2, nv=1), chance=0.35, cond=[no], mark=["agree"]),
        turn("b", lines((), HOLD, rng, k=3, nv=1, kv=1), chance=0.6, cond=[no, "!t:agree"], mark=["disagree"]),
    ], needs=needs, weight=0.8))

    # 3. Spicy: b is outraged; a doubles down; b comes round, falls out, or laughs it off.
    rng = random.Random("spicy" + key)
    used = set()
    no = "t:ds_%s_no" % key
    sneeds = needs + (["a:voice_" + voice] if voice else [])
    out.append(topic("g_debate_spicy_" + key, about, tags_a, leads_a, [
        turn("a", lines(C[4:6] + [(C[5][0], framed(C[5][1], rng, used)), (C[1][0], framed(C[1][1], rng, used))])),
        turn("b", lines(X[6:8], OUTRAGE, rng, k=2, nv=1), mark=[no[2:]], leads=["argument"]),
        turn("a", lines(W[6:8] + R[4:5])),
        turn("b", lines(Y[2:3], COME_ROUND, rng, k=2, nv=1), chance=0.35, mark=["agree"]),
        turn("a", lines((), GLOAT, rng, k=2), chance=0.6, cond=["t:agree"]),
        turn("b", lines((), HOLD_HEATED, rng, k=3, nv=1, kv=1), chance=0.4, cond=["!t:agree"], mark=["disagree"]),
        turn("b", lines((), LAUGH_OFF, rng, k=3, nv=1, kv=1), chance=0.75, cond=["!t:agree", "!t:disagree"]),
        turn("a", lines((), STAND, rng, k=3, nv=1, kv=1), chance=0.5, cond=["!t:agree"]),
    ], needs=sneeds, weight=1.5 if voice else 0.6))
    return out


def build_debate(b):
    key, about = b.fields[0], b.fields[3]
    tags = split_list(b.fields[1])
    leads = split_list(b.fields[2])
    needs = lore_needs(tags, split_list(b.fields[4]))
    va = b.fields[5] if b.fields[5] not in ("", "-") else None
    vb = b.fields[6] if b.fields[6] not in ("", "-") else None
    g = b.get
    tid = "g_debate_long_" + key
    S, D, H, B, H2 = ("t:dl_%s_%s" % (key, m) for m in ("s", "d", "h", "b", "h2"))
    m = lambda c: [c[2:]]
    rng = random.Random("long" + key)
    needs1 = needs + (["a:voice_" + va] if va else []) + (["b:voice_" + vb] if vb else [])
    tags1 = (tags + ["argument"])[:4]
    leads1 = (leads + ["argument"])[:4] if "argument" not in leads else leads

    heat = lines(g("AK")[2:4], HEATED, rng, k=2, nv=1, kv=1)
    one = topic(tid, about, tags1, leads1, [
        turn("a", lines(g("AO"))),
        turn("b", lines((), EARLY_SAME, rng, k=3, nv=1, kv=1), chance=0.2, mark=m(S)),
        turn("b", lines(g("BO")), cond=["!" + S], mark=m(D), leads=["argument"]),
        # (They agree: a's pleased, b backs it with its own story, a knocks the other side.)
        turn("a", lines(g("AA")[0:2], GLOAT_SAME, rng, k=2), cond=[S]),
        turn("b", lines(g("AL")), cond=[S], mark=["agree"]),
        turn("a", lines(g("BK")[0:2]), chance=0.6, cond=[S]),
        # (They don't: argument, knock, example, b's argument, knock back, maybe heat.)
        turn("a", lines(g("AA")[0:2]), cond=[D]),
        turn("b", lines(g("AK")[0:2]), cond=[D]),
        turn("a", lines(g("AL")), cond=[D]),
        turn("b", lines(g("BA")), cond=[D]),
        turn("a", lines(g("BK")[0:2]), cond=[D]),
        turn("b", heat, chance=0.6, cond=[D, "rivals"], mark=m(H)),
        turn("b", heat, chance=0.3, cond=[D, "!rivals", "!" + H], mark=m(H)),
        turn("a", lines((), CALM, rng, k=3, nv=1, kv=1), cond=[H]),
        turn("b", lines(g("J")), chance=0.6, cond=[D, "!" + H]),
    ], needs=needs1, weight=1.4 if (va or vb) else 1.0, next_=[tid + "_2"], cost=0.5)

    rng = random.Random("long2" + key)
    heat2 = lines(g("AD"), HEATED, rng, k=2)
    two = topic(tid + "_2", about, ["story_part"] + tags[:2], leads1, [
        # (They agreed: piling on the other side together.)
        turn("a", lines(g("BK")[2:4]), cond=[S]),
        turn("b", lines((), AGREE, rng, k=3, nv=1, kv=1), cond=[S]),
        turn("a", lines((), SAME_END, rng, k=3), chance=0.7, cond=[S], mark=["agree"]),
        # (They didn't: round two.)
        turn("b", lines((), SORRY, rng, k=2, nv=1, kv=1), chance=0.6, cond=[H]),
        turn("a", lines(g("AA")[2:4]), cond=[D]),
        turn("b", lines(g("AG"), BENDS, rng, k=1), chance=0.35, cond=[D], mark=m(B)),
        turn("b", lines(g("BL")), cond=[D, "!" + B]),
        turn("a", lines(g("BG"), GRACIOUS, rng, k=1), cond=[B], mark=["agree"]),
        turn("b", lines((), AFTER_TRUCE, rng, k=2), chance=0.6, cond=[B]),
        turn("a", heat2, chance=0.6, cond=[D, "!" + B, H], mark=m(H2)),
        turn("a", heat2, chance=0.3, cond=[D, "!" + B, "!" + H], mark=m(H2)),
        turn("a", lines(g("BK")[2:4]), cond=[D, "!" + B, "!" + H2]),
        turn("b", lines(g("E"), END_ATTEMPT, rng, k=2), cond=[D, "!" + B]),
        options("a", [
            ([H2], lines((), REFUSE, rng, k=2, nv=1, kv=1), ["disagree"]),
            (["rivals"], lines((), RIVAL_END, rng, k=2), ["disagree"]),
            (["friends"], lines((), FRIENDLY_END, rng, k=2), None),
            (None, lines((), TRUCE, rng, k=3), None),
        ], cond=[D, "!" + B]),
        turn("b", lines((), FINAL_HEAT, rng, k=2), chance=0.6, cond=[H2]),
    ], follow_up=True)
    return [one, two]


# ---------------------------------------------------------------------------------------------------------------------
# Playing a topic out (for reading the output back): the engine's rules, simplified.

def play(t, rng, world):
    marks = set()
    out = []
    answers, asker = None, None

    def holds(conds):
        for c in conds:
            neg = c.startswith("!")
            c = c[1:] if neg else c
            if c.startswith("t:"):
                is_ = c[2:] in marks
            else:
                is_ = c in world
            if is_ == neg:
                return False
        return True

    def pick(group, voice):
        if isinstance(group, list):
            return rng.choice(group)
        own = group.get(voice)
        if own and rng.random() < 0.65:
            return rng.choice(own)
        return rng.choice(group["any"])

    for tr in t["turns"]:
        who = tr["by"]
        voice = world.get("voice_" + who)
        if answers is not None and who != asker:
            out.append((who, rng.choice(answers)))
            answers = None
            continue
        if rng.random() > tr.get("chance", 1.0) or not holds(tr.get("if", [])):
            continue
        opt = None
        for o in tr.get("options", [tr]):
            if o is tr or holds(o.get("if", [])):
                opt = o
                break
        if opt is None:
            continue
        line = pick(opt["lines"], voice)
        if isinstance(line, dict):
            answers, asker = line["then"], who
            line = line["line"]
        marks |= set(opt.get("mark", []))
        out.append((who, line))
    if answers is not None:
        out.append(("b" if asker == "a" else "a", rng.choice(answers)))
    return out, marks


# ---------------------------------------------------------------------------------------------------------------------
# The data.

DATA = []

DATA.append(r"""
# --- Samples (the others follow in the same format).
@ creepers_misunderstood | mobs | mobs,danger,home | creepers being misunderstood | | hermit
C: creepers are misunderstood | creepers just want a hug | creepers are the most honest mob in the game | the creeper is the only mob that warns you first | creepers aren't evil, they're lonely | every creeper hole is a free skylight
W: they hiss before they blow, that's manners | a skeleton never says sorry first | they don't burn in the sun, they just live with us | they only blow up when you get close, it's a boundary thing | cats scare them off, so they're basically shy | they don't follow you into water, very polite | a charged creeper is just a creeper having a big day | they drop gunpowder, so they literally leave you a gift
P: honestly the hiss is a courtesy | true, a zombie never warns you
X: one deleted my whole storage wall | polite? it ate half my house | they sneak up behind you on purpose | a warning one second before isn't manners | my chest room disagrees | they creep up silently, it's in the name | they killed me twice this week | tell that to my crater of a front yard
R: you built next to a cave, that's on you | light your yard and they leave you alone | you walked up to it, not the other way round | get a cat and you'll never see one again | that's a lighting problem, not a creeper problem
Y: ok the hiss is kinda polite | fine, the cat thing is cute, they're just scared | alright, i'll stop yelling at them

@ phantoms_deleted | mobs,night | mobs,night,sleep | phantoms | |
C: phantoms should not exist | phantoms are the worst thing in the game | phantoms are a punishment for being busy | nobody has ever been happy to see a phantom | phantoms are just flying guilt for skipping bed | the sky was better before phantoms
W: you stay up three nights for a project and the sky attacks you | they dive at you while you're bridging, every time | they knock you off ledges and into lava | they burn in the sun but somehow always find you at dusk | their membrane is the only good part and you need ten of them | they swoop at your horse and you lose the horse | they ruin every night build, you can't place a block in peace | they don't even make a sound you can track until it's too late
P: they took me off a cliff last week | yeah, i build at night and they hate me for it
X: just sleep then lol | the membrane fixes elytras though | they keep you honest about sleeping | it's one mob, get a bow | they burn in the morning, just wait | cats scare them, get a cat | it's three nights, that's on you | they make the night sky feel alive
R: i shouldn't have to sleep to build my own house | a bow while bridging? come on | a mob that punishes you for being busy is bad design for the world | cats don't fly with me to my builds | nobody asked for the sky to fight back
Y: ok fair, the bridging thing is evil | yeah the cliff thing has gotten me too | fine, they're kinda the worst

@ dirt_houses_valid | building,home | building,home,decor | dirt houses | |
C: dirt houses are valid | a dirt house is a real house | dirt is a perfectly good building block | first-night dirt huts are the most honest builds | dirt houses have more soul than stone ones | i'd live in a dirt house forever
W: it keeps the mobs out just as well as stone | you can dig it with your hands, no tool needed | it's free, every block of it | grass grows on the roof, it's basically decoration | it's warm, it looks like a hobbit hole | it's fast, i had walls before sunset | nobody breaks into a dirt house, nothing in there to want | a dirt house is a promise you'll build something better
P: my first house was dirt and i loved it | true, it does the job
X: creepers go through dirt like it's nothing | it looks like a pile of mud | endermen take bits of your walls | grass growing on your walls looks messy | you can't even put a painting on it right | it's a hut, not a house | spiders climb it easily | it falls apart if you look at it wrong
R: stone falls to creepers too, just slower | looks aren't everything, i'm alive aren't i | a house is where your bed is | a painting looks fine on dirt, try it | everyone started in dirt, don't be a snob
Y: ok, a house is where the bed is, fair | fine, i did start in dirt too | ok, dirt house respect

@ gold_tools_underrated | tools,gear | tools,mining,gear | gold tools | |
C: gold tools are underrated | gold pickaxes are secretly the best | everyone sleeps on gold tools | a gold pick is faster than diamond and nobody talks about it | gold tools deserve respect | i'd take a gold axe over iron any day
W: gold mines faster than any tool except netherite | it enchants better than anything, the highest enchant chance | for quick jobs like clearing sand, it's unbeatable | gold is everywhere in the badlands | piglins love you for it too | a gold pick on stone is crazy fast | gold shovel through gravel, it's like butter | if you have spare gold, why not
P: the speed is actually nuts | yeah the enchanting thing is real
X: it breaks in like thirty blocks | it can't even mine diamonds | it's a waste of good gold | you'll spend more time crafting than mining | iron lasts way longer | gold is for piglins and clocks | you can't mine iron ore with it either | every gold tool i've had broke mid-job
R: it breaks fast but it works fast, it evens out | i don't use it for diamonds, i use it for stone | you can always mend it | it's not for everything, it's for speed | for a quick dig it's the best tool you've got
Y: ok the speed thing is fair | fine, for sand and gravel maybe | alright, gold has a place

@ trading_halls_cruel | villagers,trade | villagers,trade,town | trading halls | | helper
C: trading halls are cruel to villagers | locking villagers in boxes is wrong | trading halls are basically villager jail | nobody would want to live in a trading hall | villagers deserve to walk around | a villager in a one-block cell is sad to look at
W: they stand in a box all day, every day | they can't even go to bed or see the sun | they're people, they have jobs and homes | they gossip, they sleep, they farm, and we put them in a box | a village is alive, a trading hall is a warehouse | you'd hate it if someone did that to you | they look at you through the glass like they know | the villages we take them from are left empty
P: yeah it always makes me feel bad | true, they look so bored in there
X: they're happy as long as they have a workstation | it keeps them safe from zombies | they don't actually mind, they just stand anyway | free villagers walk into lava | mending books don't trade themselves | a village gets raided, a hall doesn't | they get a job and a roof, that's a lot | they don't even notice where they are
R: safe isn't the same as happy | you could protect a village with walls instead | a bed and a window, that's all i'm asking | if they don't mind, why do they keep looking at me | you can trade with them in their own village just fine
Y: ok i'll give them a window at least | fine, maybe a bed for each one | ok, a village with walls could work too

@ sleeping_every_night | sleep,night | sleep,night,mobs | sleeping every night | | homebody
C: you should sleep every single night | skipping a night is just asking for trouble | bed every night, no exceptions | the night is not for working | a good bed is the best thing you'll ever craft | if you're not in bed by dark you're doing it wrong
W: no phantoms, ever | the night is when the mobs come out, why be out in it | you wake up with full energy for the day | your spawn is set, so if anything happens you're home | morning light makes everything easier | it skips the scary part, why wouldn't you | you save torches, you save food, you save your life | nothing good happens after dark
P: same, i'm in bed the second it gets dark | true, the night is just danger
X: the night is when the good mobs drop stuff | i need gunpowder, i need the dark | you miss so much stuff at night | sleeping is boring | night mining is the best mining | what if your bed is miles away | some nights you just have to finish the build | you can't sleep if a mob is nearby anyway
R: whatever you need from the night, buy it from someone else | i carry a bed, problem solved | the build will still be there in the morning | that's what fences and torches around your bed are for | i'd rather be boring and alive
Y: ok, carrying a bed is smart | fine, the build can wait, i guess | ok, no phantoms is a good point

@@ sword_vs_axe | weapons,fighting | weapons,fighting,gear | swords or axes | |
AO: swords are the best weapon, no contest | nothing beats a sword | a good sword is all you need in a fight
AA: the sweep hits three mobs at once | it swings way faster than an axe | looting on a sword gets you double drops | a sword with sharpness five shreds everything
AL: my sword got me through a whole raid last week | i held a cave with just a sword and a torch
AK: swords are slow to kill anything big | the sweep hits your own dog | axes break shields, swords just bounce | a sword can't chop a tree, it's one-job
AG: ok fine, the sweep is nice on groups | alright, swords do swing faster
AD: no, swords are the weapon, end of story | i will never swing an axe at a zombie
BO: axes all day | axes hit harder, simple | an axe is the better weapon
BA: one axe hit disables a shield for five seconds | an axe is a tool and a weapon, two for one
BL: i one-shot a skeleton with a diamond axe | my axe chopped my house and then killed a creeper
BK: axes are so slow between swings | you miss once and the mob's on you | no sweep, so a crowd eats you alive | you waste the durability of your chopping tool
BG: ok, the shield thing is pretty strong | fine, an axe does hit harder
J: this is the most heated anyone's been about a stick with metal on it | we're arguing about pointy rocks lol
E: swords for crowds, axes for one-on-one, maybe | use what you're good with, i guess

@@ mine_at_night | mining,night | mining,night,danger | mining at night | |
AO: night is the best time to mine | i only mine at night | why sleep when you can mine
AA: underground it doesn't matter what time it is | the surface is dangerous at night, the mine isn't any worse | you skip the walk home in the dark, you're already safe down there | a whole night of mining is like twenty stacks of cobble
AL: i found eleven diamonds on one night shift | last night i mined until sunrise and felt great
AK: you come up at dawn into a field of zombies | phantoms will find you on the third night | you forget to eat and starve down there | tired mining is how you fall into lava
AG: ok, underground the time really doesn't matter | fine, it's not more dangerous down there
AD: no, the night shift is the real shift | i'm not wasting eight minutes in bed
BO: sleep first, mine in the morning | nobody should be mining at night | night is for sleeping, the ore can wait
BA: you skip the night and you've lost nothing | the walk to your mine at night is the dangerous part
BL: i mined at night once and came out to five creepers at my door | i lost a full inventory because i was tired and dug into lava
BK: you sleep through a third of the day | by the time you walk to the mine it's noon | the stone doesn't care if you're rested | you wake up and the zombies are still at your door anyway
BG: ok fair, the mine itself isn't worse at night | fine, if you're already down there, keep going
J: the ores don't know what time it is lol | the diamonds aren't going to sleep either
E: night mine if you live in the mine, i guess | each to their own shift
""")


# ---------------------------------------------------------------------------------------------------------------------

def build_all(texts, where="data"):
    problems = []
    blocks = []
    for i, text in enumerate(texts):
        blocks += parse(text, "%s[%d]" % (where, i), problems)
    lint(blocks, problems)
    topics = []
    for b in blocks:
        try:
            topics += build_take(b) if b.kind == "take" else build_debate(b)
        except Exception as e:
            problems.append("%s %s: can't build: %r" % (b.at, b.fields[0], e))
    ids = set()
    for t in topics:
        if t["id"] in ids:
            problems.append("topic id twice: " + t["id"])
        ids.add(t["id"])
    return blocks, topics, problems


def write(topics, out):
    comment = ("Controversial opinions and long debates (tools/talk_gen/debates.py; the user: \"controversial opinions ... long "
               "debates ... as detailed and in depth as possible\"). Hot takes: hot_ (agree, or object and maybe come round), "
               "why_ (b asks why, the reason answers), spicy_ (b is outraged; voiced ones only from that kind of player). Long "
               "debates: long_<key> (round one) and long_<key>_2 (round two, a follow_up branching on round one's marks _s/_d/_h). "
               "agree/disagree marks only where it ends that way. Check with: python tools/check_talk_web.py .")
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write('{"_comment": %s,\n"topics": [\n' % json.dumps(comment))
        f.write(",\n".join(json.dumps(t, ensure_ascii=True, separators=(",", ":")) for t in topics))
        f.write("\n]}\n")


def main(argv):
    if len(argv) >= 2 and argv[0] == "--lint":
        text = open(argv[1], encoding="utf-8").read()
        blocks, topics, problems = build_all([text], argv[1])
        for p in problems:
            print("PROBLEM: " + p)
        takes = sum(1 for b in blocks if b.kind == "take")
        print("%d takes, %d debates -> %d topics, %d problems" % (takes, len(blocks) - takes, len(topics), len(problems)))
        return 1 if problems else 0
    if len(argv) >= 2 and argv[0] == "--play":
        texts = DATA + ([open(argv[3], encoding="utf-8").read()] if len(argv) > 3 else [])
        _, topics, _ = build_all(texts)
        n = int(argv[2]) if len(argv) > 2 else 3
        rng = random.Random()
        byid = {t["id"]: t for t in topics}
        for t in topics:
            if argv[1] not in t["id"] or t.get("follow_up"):
                continue
            for _ in range(n):
                world = {"voice_a": rng.choice(VOICES), "voice_b": rng.choice(VOICES)}
                world[rng.choice(["friends", "rivals", "strangers", "acquainted"])] = True
                shown, marks = play(t, rng, world)
                print("== %s (a %s, b %s, %s)" % (t["id"], world["voice_a"], world["voice_b"],
                                                   [k for k in world if not k.startswith("voice")][0]))
                for who, line in shown:
                    print("  %s: %s" % (who, line))
                for nid in t.get("next", []):
                    two = byid[nid]
                    more, marks2 = play_with(two, rng, world, marks)
                    print("  -- part 2")
                    for who, line in more:
                        print("  %s: %s" % (who, line))
                    marks = marks2
                print("  marks:", sorted(m for m in marks if m in ("agree", "disagree")) or "-")
        return 0
    blocks, topics, problems = build_all(DATA)
    for p in problems:
        print("PROBLEM: " + p)
    if problems:
        return 1
    write(topics, argv[0])
    takes = sum(1 for b in blocks if b.kind == "take")
    print("%d takes, %d debates -> %d topics" % (takes, len(blocks) - takes, len(topics)))
    return 0


def play_with(t, rng, world, marks):
    """Part two, carrying round one's own marks (agree/disagree are cleared between topics)."""
    carried = {m for m in marks if m not in ("agree", "disagree")}
    w = dict(world)
    shown, got = [], set()
    # (Run play() with the carried marks pre-set.)
    saved = list(t["turns"])
    seed = [turn("a", ["(carried)"], mark=sorted(carried))] if carried else []
    t2 = dict(t)
    t2["turns"] = seed + saved
    shown, got = play(t2, rng, w)
    return [s for s in shown if s[1] != "(carried)"], got


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
