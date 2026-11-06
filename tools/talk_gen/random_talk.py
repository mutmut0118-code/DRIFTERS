"""Writes drifter_talk/topics_gen_random.json: random talk to pass the time, from small talk to debates.

The user (2026-11-06): "more random convos to pass time ... from small talks to debate". Kinds (all ids start g_random_):
would you rather (b picks, a pushes back, b defends), quick would-you-rathers (b's pick comes as the question's answer),
hypotheticals and what-ifs, riddles and guessing games (guess the mob, 20 questions, i spy, guess what i found), jokes and
puns, knock knock jokes, random thoughts, favourites and top-3 lists, small hot takes (agree or disagree, which counts),
small talk (the day, the weather, the time, being bored, working side by side, comfortable silence) and silly
back-and-forths (naming animals, made-up games and songs, rating armor, dream builds).

Every topic is written by hand in the tables below (a small text format, see parse()); the builders only add the shared
reactions ("lol", "fair enough") and the turn shapes. Generic ones weigh 0.5-0.9 so they mix with everything else;
situational ones (rain, night, bored) weigh more.

    python tools/talk_gen/random_talk.py src/main/resources/data/drifter/drifter_talk/topics_gen_random.json
    python tools/check_talk_web.py . --quiet
"""
import json
import os
import random
import re
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src/main/resources/data/drifter/drifter_talk/topics_gen_random.json')
P = 'g_random_'
rng = random.Random(20261106)
topics = []
ids = set()


# ---------------------------------------------------------------------------------------------------------------------
# Helpers.

def S(text):
    """'a | b | c' -> ['a', 'b', 'c']."""
    return [s.strip() for s in text.split(' | ') if s.strip()]


def turn(by, lines, chance=None, cond=None, mark=None):
    d = {'by': by, 'lines': lines}
    if chance is not None and chance < 1:
        d['chance'] = chance
    if cond:
        d['if'] = cond
    if mark:
        d['mark'] = mark
    return d


def ask(questions, answers):
    """Questions that carry their answers: the other one's next turn says one of these."""
    return [{'line': q, 'then': list(answers)} for q in questions]


def some(pool, n):
    return rng.sample(pool, min(n, len(pool)))


def add(key, about, tags, leads, turns, needs=None, weight=None, effects=None):
    tid = P + key
    assert tid not in ids, 'duplicate id ' + tid
    ids.add(tid)
    t = {'id': tid}
    if weight is not None:
        t['weight'] = weight
    if needs:
        t['needs'] = needs
    t['tags'] = tags[:4]
    t['leads'] = leads
    t['about'] = about
    t['turns'] = turns
    if effects:
        t['effects'] = effects
    topics.append(t)


def leads_of(base, tags, extra=None):
    """The category's leads plus the topic's own subject (2 to 4, no repeats)."""
    out = []
    for tag in list(base) + [t for t in tags if t not in base] + list(extra or []):
        if tag not in out and tag != 'story_part':
            out.append(tag)
    return out[:4]


def parse(text):
    """The table format: a block per topic.

        # key | about | tag tag
        needs: rain outside        (optional, space separated)
        w: 1.5                     (optional weight)
        leads: weather random      (optional, replaces the category's leads)
        field: line | line | line

    Lines starting with // are comments. Field names depend on the builder; the free builder reads them in order.
    """
    blocks = []
    cur = None
    for raw in text.strip().splitlines():
        line = raw.strip()
        if not line or line.startswith('//'):
            continue
        if line.startswith('# '):
            head = [h.strip() for h in line[2:].split('|')]
            cur = {'key': head[0], 'about': head[1], 'tags': head[2].split(), 'needs': None, 'w': None, 'leads': None,
                   'f': {}, 'order': []}
            blocks.append(cur)
            continue
        m = re.match(r'^([a-z0-9_.?+]+):\s?(.*)$', line)
        assert m and cur is not None, 'bad line: ' + line
        key, val = m.group(1), m.group(2).strip()
        if key == 'needs':
            cur['needs'] = val.split()
        elif key == 'w':
            cur['w'] = float(val)
        elif key == 'leads':
            cur['leads'] = val.split()
        else:
            cur['f'].setdefault(key, []).extend(S(val) if '=>' not in val else [val])
            cur['order'].append((key, val))
    return blocks


def weight_of(block, default):
    if block['w'] is not None:
        return block['w']
    if block['needs']:
        return 1.5
    return default if not isinstance(default, (list, tuple)) else rng.choice(default)


GENERIC_W = (0.5, 0.6, 0.6, 0.7, 0.7, 0.8)


# ---------------------------------------------------------------------------------------------------------------------
# Shared reactions (each topic takes a few at random, so no one line is in every topic).

WYR_PREFIX = ['ok ', 'random q, ', 'quick one, ', 'hypothetically, ', 'ok be honest, ', 'serious question, ']
WYR_LEAD = ['nah, ', 'but ', 'idk, ', 'hmm, ', 'nah see, ', 'really? ']
WYR_PUSH = ['wrong answer lol', 'really? that one?', 'bold pick', 'interesting. wrong, but interesting', 'huh, i\'d go the other way',
            'oh no', 'you sure about that?', 'hmm, i don\'t know about that', 'that\'s the wrong one', 'brave']
WYR_DEFEND = ['not changing my answer', 'i stand by it', 'still my pick', 'final answer', 'and i\'d do it again', 'my answer stays',
              'easy choice honestly', 'not even close', 'think about it', 'trust me on this']
WYR_CLOSE = ['fair enough', 'ok that\'s actually a good point', 'agree to disagree', 'you\'ve thought about this way too much',
             'i stand by mine', 'lol we\'re both wrong', 'ok you win this one', 'hm. i\'ll allow it', 'solid reasoning honestly',
             'i respect it', 'we\'ll never know', 'next question', 'lol fair', 'that\'s so you', 'you might be onto something',
             'still wrong but ok', 'ok ok', 'hard to argue with that', 'you\'d last a day lol', 'can\'t believe we\'re arguing about this',
             'good thing it\'ll never happen', 'i\'d still pick mine', 'ok your logic checks out', 'mhm sure']

QWYR_REACT = ['same honestly', 'really? i\'d go the other way', 'good answer', 'interesting', 'i knew you\'d say that', 'hmm that\'s fair',
              'wrong lol', 'see i\'m the opposite', 'bold', 'can\'t argue with that', 'lol ok', 'that\'s what i\'d pick too',
              'oh you\'re serious', 'huh, didn\'t expect that']
QWYR_B = ['it\'s the obvious pick', 'fight me', 'think about it', 'you\'d pick the same if you were honest', 'easy choice',
          'no regrets', 'it\'s not even close', 'trust me', 'i\'ve thought about this before', 'lol i stand by it']

QWYR_C = ['ok your turn to ask one', 'lol ok', 'fair enough', 'good thing it\'s only a question', 'ok next one',
          'i\'d change my mind tomorrow', 'we think about this stuff too much', 'noted', 'that says a lot about you',
          'i\'ll remember that']
HYPO_PRE = ['nice, ', 'ha, ', 'ooh. ', 'lol ok. ', 'ok but ', 'fair. ', 'heh. ']
HYPO_B = ['that\'d be wild', 'lol imagine', 'chaos', 'i\'d love that', 'terrifying', 'sign me up', 'no thanks', 'could be cool',
          'honestly yeah', 'that\'s a good one', 'ok that\'s better than mine', 'the world would be so different', 'lol why',
          'i\'d pay emeralds for that', 'wait that\'s genius']
HYPO_C = ['one day maybe', 'we can dream', 'imagine though', 'this world\'s weird enough already', 'lol', 'a guy can dream',
          'someone should make that happen', 'ok back to reality', 'if only', 'stop, i want it now', 'the dream', 'yeah...']

JOKE_ASK = ['what?', 'idk, what?', 'no idea', 'go on', 'oh no', 'i\'m scared to ask', 'tell me', 'hit me', 'i don\'t know, what?',
            '...what?', 'dunno', 'ugh, what', 'i give up', 'what', 'here we go']
JOKE_GROAN = ['lol', 'boo', 'that was awful', 'i hate that i laughed', 'get out', '...', 'terrible', 'ok that one got me', 'booo',
              'why', 'lmao', 'i\'m leaving', 'that\'s so bad', 'please stop', 'that was painful', 'ha', 'lol ok', 'who taught you that',
              'ok that\'s actually funny', 'ugh', 'nope', 'i want those seconds back', 'that\'s criminal', 'lol what', 'oh come on',
              'you\'re the worst', 'i walked right into that']
JOKE_AFTER = ['i\'ll be here all week', 'got a million of these', 'you laughed, i saw it', 'i made that one up', 'thank you thank you',
              'tough crowd', 'that\'s my best one', 'i\'ve got more', 'admit it, that was good', 'an old miner told me that one',
              'i\'ve been saving that one', 'comedy gold', 'that\'s a classic', 'you\'re welcome']
JOKE_END = ['please don\'t', 'no more', 'spare me', 'lol ok one more', 'save some for later', 'i\'m walking away', 'never again',
            'ok that\'s enough jokes for today', 'keep them coming i guess', 'you need new material']

RIDDLE_GROAN = ['ohhh', 'i should\'ve got that', 'that\'s so dumb', 'how was i supposed to know that', 'ok that\'s clever', 'lol ok',
                'that\'s cheating', 'i was close', 'ugh, obviously', 'oh come on', 'wow', 'nice one', 'got me', 'that\'s a good one']
RIDDLE_AFTER = ['got another one', 'i made that up myself', 'too easy', 'your turn', 'i\'ll get you next time', 'that one\'s a classic',
                'ok one more', 'heh', 'i\'m a genius']

THOUGHT_Q = ['random thought: ', 'ok weird question, ', 'random question, ', 'thought of the day: ', 'be honest, ',
             'been wondering, ', 'real question, ']
THOUGHT_S = ['random thought: ', 'ok hear me out, ', 'just realised ', 'you ever notice ', 'thought of the day: ',
             'been thinking, ', 'weird thought: ']
THOUGHT_B = ['lol what', 'go to bed', 'that\'s deep', 'huh', 'whoa', 'never thought about that', 'too deep for me', 'you ok?',
             'where did that come from', 'hm']
THOUGHT_C = ['ok now i can\'t stop thinking about it', 'you think too much', 'my brain hurts', 'this is why you can\'t sleep',
             'deep stuff', 'i\'m gonna be thinking about that all day', 'ok philosopher', 'write that on a sign',
             'you should be a librarian', 'thanks, i hate it', 'lol fair', 'let\'s not think about it']
THOUGHT_A = ['just saying', 'think about it', 'anyway', 'it keeps me up at night', 'lol', 'food for thought', 'you\'re welcome',
             'mind blown, right?']

FAV_PRE = ['nice. ', 'good one. ', 'valid. ', 'ooh. ', 'solid. ', '', '', '', 'lol ', 'fair. ', 'ha. ']
FAV_B = ['good pick', 'respectable', 'can\'t argue with that', 'solid choice', 'valid', 'taste', 'ooh nice', 'classic', 'fair',
         'ok i see you', 'strong pick', 'i was gonna say that', 'great minds', 'controversial but ok']
FAV_END = ['obviously', 'mhm', 'lol', 'i know', 'thanks', 'we both have taste', 'correct answer', 'right?', 'heh']

TAKE_Y = ['true', 'facts', 'fr', '100%', 'no argument here', 'finally someone said it', 'agreed', 'real']
TAKE_N = ['nah', 'hard disagree', 'absolutely not', 'what? no', 'that\'s wrong', 'couldn\'t disagree more', 'no way']
TAKE_AY = ['see', 'exactly', 'someone gets it', 'thank you', 'glad i\'m not alone', 'knew you\'d get it', 'right?', 'told you']
TAKE_AN = ['agree to disagree', 'you\'re wrong but ok', 'we\'ll see', 'whatever you say', 'fine, be wrong', 'i\'ll convince you one day',
           'hmm. noted', 'fair enough i guess', 'you\'ll come around', 'wow ok']
TAKE_BN = ['we\'ll never agree on this', 'nope', 'lol', 'never', 'keep dreaming', 'not happening', 'ok we drop it', 'sure buddy']


# ---------------------------------------------------------------------------------------------------------------------
# Builders.

def build_wyr(block):
    """Would you rather: a asks; b picks one (mark), a pushes back with the other side, b defends its pick, a wraps up.
    Fields: q (questions), b1/b2 (b picks the first/second, with a reason), x1/x2 (arguments for the first/second)."""
    f = block['f']
    key = 'wyr_' + block['key']
    m1, m2 = key + '_1', key + '_2'
    qs = list(f['q'])
    shortest = min(qs, key=len)
    prefix = rng.choice(WYR_PREFIX)
    if shortest.startswith('would you rather') and len(prefix + shortest) <= 80:
        qs.append(prefix + shortest)

    def push(lines):
        out = []
        for line in lines:
            if re.match(r'^(nah|but|idk|hmm|really|see|ok|what|wait|no|lol)\b', line):
                out.append(line)
            else:
                out.append(rng.choice(WYR_LEAD) + line)
        return out + some(WYR_PUSH, 1)

    tags = block['tags']
    add(key, block['about'], ['would_you_rather'] + tags, block['leads'] or leads_of(['would_you_rather', 'random'], tags),
        [turn('a', qs),
         turn('b', f['b1'], chance=0.5, mark=[m1]),
         turn('b', f['b2'], cond=['!t:' + m1], mark=[m2]),
         turn('a', push(f['x2']), cond=['t:' + m1]),
         turn('a', push(f['x1']), cond=['t:' + m2]),
         turn('b', f['x1'] + some(WYR_DEFEND, 1), cond=['t:' + m1]),
         turn('b', f['x2'] + some(WYR_DEFEND, 1), cond=['t:' + m2]),
         turn('a', some(WYR_CLOSE, 4), chance=0.65)],
        needs=block['needs'], weight=weight_of(block, GENERIC_W))


def build_qwyr(block):
    """Quick would you rather: b's pick (and why) is the question's answer. Fields: q, ans, c (a's last word, optional)."""
    f = block['f']
    tags = block['tags']
    add('qwyr_' + block['key'], block['about'], ['would_you_rather'] + tags,
        block['leads'] or leads_of(['would_you_rather', 'random'], tags),
        [turn('a', ask(f['q'], f['ans'])),
         turn('b', ['hm']),
         turn('a', some(QWYR_REACT, 4)),
         turn('b', some(QWYR_B, 3), chance=0.6),
         turn('a', f.get('c', []) + some(QWYR_C, 3), chance=0.5)],
        needs=block['needs'], weight=weight_of(block, GENERIC_W))


def build_hypo(block):
    """A what-if: a asks (b's answer comes with it), a gives its own, b reacts, a muses.
    Fields: q, ans, a (a's own take), b (b on a's take, optional), c (a's last word, optional)."""
    f = block['f']
    tags = block['tags']
    takes = [(rng.choice(HYPO_PRE) + line) if rng.random() < 0.4 and not line.startswith(('ok', 'lol', 'mine', 'same')) else line
             for line in f['a']]
    add('hypo_' + block['key'], block['about'], ['hypothetical'] + tags,
        block['leads'] or leads_of(['hypothetical', 'dreams', 'random'], tags),
        [turn('a', ask(f['q'], f['ans'])),
         turn('b', ['hm']),
         turn('a', takes),
         turn('b', f.get('b', []) + some(HYPO_B, 3 if f.get('b') else 5), chance=0.85),
         turn('a', f.get('c', []) + some(HYPO_C, 2 if f.get('c') else 4), chance=0.5)],
        needs=block['needs'], weight=weight_of(block, GENERIC_W))


def build_riddle(block):
    """A riddle: a asks, b guesses wrong (the question's answers), a tells, b groans. Fields: q, g, r, b (optional)."""
    f = block['f']
    tags = block['tags']
    add('riddle_' + block['key'], block['about'], ['riddles'] + tags, block['leads'] or leads_of(['riddles', 'jokes', 'random'], tags),
        [turn('a', ask(f['q'], f['g'])),
         turn('b', ['hm']),
         turn('a', f['r']),
         turn('b', f.get('b', []) + some(RIDDLE_GROAN, 4)),
         turn('a', some(RIDDLE_AFTER, 3), chance=0.5)],
        needs=block['needs'], weight=weight_of(block, GENERIC_W))


def build_guess_mob(block):
    """Guess the mob (or block): a gives a clue, b guesses wrong, a says no and gives another, b gets it, a says yes.
    Fields: c (first clue), g1 (wrong guesses), n (no + second clue), g2 (the right guess), y (yes)."""
    f = block['f']
    tags = block['tags']
    add('guess_' + block['key'], block['about'], ['riddles'] + tags, block['leads'] or leads_of(['riddles', 'random', 'mobs'], tags),
        [turn('a', ask(f['c'], f['g1'])),
         turn('b', ['hm']),
         turn('a', ask(f['n'], f['g2'])),
         turn('b', ['hm']),
         turn('a', f['y']),
         turn('b', f.get('b', []) + some(['my turn next', 'do another', 'too easy', 'that took me way too long', 'ok i\'m good at this',
                                           'ask me a harder one', 'lol got it', 'easy'], 3), chance=0.6)],
        needs=block['needs'], weight=weight_of(block, GENERIC_W))


def build_joke(block):
    """A joke: a sets it up, b asks what (or guesses), a gives the punchline, b groans. Fields: j, p, g (optional), b (optional)."""
    f = block['f']
    tags = block['tags']
    guesses = f.get('g', []) + some(JOKE_ASK, 5 - min(3, len(f.get('g', []))))
    add('joke_' + block['key'], block['about'], ['jokes'] + tags, block['leads'] or leads_of(['jokes', 'riddles'], tags, ['random']),
        [turn('a', ask(f['j'], guesses)),
         turn('b', ['hm']),
         turn('a', f['p']),
         turn('b', f.get('b', []) + some(JOKE_GROAN, 5)),
         turn('a', some(JOKE_AFTER, 3), chance=0.45),
         turn('b', some(JOKE_END, 3), chance=0.3)],
        needs=block['needs'], weight=weight_of(block, GENERIC_W))


def build_knock(block):
    """Knock knock. Fields: n (the name, one line), p (punchlines), who (the who-answers, optional)."""
    f = block['f']
    name = f['n'][0]
    add('knock_' + block['key'], block['about'], ['jokes'] + block['tags'], block['leads'] or ['jokes', 'riddles', 'random'],
        [turn('a', ask(['knock knock', 'knock knock!', 'ok. knock knock'], ['who\'s there?', 'who\'s there', 'ugh. who\'s there',
                                                                            'oh no. who\'s there?'])),
         turn('b', ['hm']),
         turn('a', ask([name], f.get('who') or [name + ' who?', name + ' who', 'ok... ' + name + ' who?'])),
         turn('b', ['hm']),
         turn('a', f['p']),
         turn('b', some(JOKE_GROAN, 5)),
         turn('a', some(JOKE_AFTER, 2), chance=0.4)],
        needs=block['needs'], weight=weight_of(block, GENERIC_W))


def build_thought(block):
    """A random thought: a says it, b reacts, a goes on, b sums up. Fields: t (thoughts), b, e (a goes on), c (optional)."""
    f = block['f']
    tags = block['tags']
    lines = []
    for t in f['t']:
        pres = THOUGHT_Q if t.endswith('?') else THOUGHT_S
        lines += [p + t for p in some(pres, 3 if len(f['t']) == 1 else 2)]
    add('thought_' + block['key'], block['about'], ['philosophy'] + tags,
        block['leads'] or leads_of(['philosophy', 'random'], tags, ['jokes']),
        [turn('a', lines),
         turn('b', f['b'] + some(THOUGHT_B, 1)),
         turn('a', f['e']),
         turn('b', f.get('c', []) + some(THOUGHT_C, 2 if f.get('c') else 4), chance=0.7),
         turn('a', some(THOUGHT_A, 3), chance=0.35)],
        needs=block['needs'], weight=weight_of(block, GENERIC_W))


def build_fav(block):
    """Favourites: a asks (b's answer comes with it), a says its own, b reacts. Fields: q, ans, a, b (optional)."""
    f = block['f']
    tags = block['tags']
    takes = [(rng.choice(FAV_PRE) + line) if not line.startswith(('ok', 'lol', 'same', 'mine', 'hm')) else line for line in f['a']]
    add('fav_' + block['key'], block['about'], ['favourites'] + tags, block['leads'] or leads_of(['favourites', 'opinion'], tags),
        [turn('a', ask(f['q'], f['ans'])),
         turn('b', ['hm']),
         turn('a', takes),
         turn('b', f.get('b', []) + some(FAV_B, 3), chance=0.85),
         turn('a', some(FAV_END, 3), chance=0.4)],
        needs=block['needs'], weight=weight_of(block, GENERIC_W))


def build_take(block):
    """A small hot take: b agrees (mark agree) or not (mark disagree), and a answers each way (agree +1 trust, disagree -1.5).
    Fields: c (claims), y (b agrees), n (b disagrees), ay, an (a's answer to each, optional)."""
    f = block['f']
    tags = block['tags']
    add('take_' + block['key'], block['about'], ['argument'] + tags, block['leads'] or leads_of(['argument', 'opinion'], tags),
        [turn('a', f['c']),
         turn('b', f['y'] + some(TAKE_Y, 2), chance=0.5, mark=['agree']),
         turn('b', f['n'] + some(TAKE_N, 2), cond=['!t:agree'], mark=['disagree']),
         turn('a', f.get('ay', []) + some(TAKE_AY, 3), chance=0.7, cond=['t:agree']),
         turn('a', f.get('an', []) + some(TAKE_AN, 3), cond=['t:disagree']),
         turn('b', some(TAKE_BN, 3), chance=0.4, cond=['t:disagree'])],
        needs=block['needs'], weight=weight_of(block, GENERIC_W))


def build_free(block, prefix, base_leads, default_w):
    """Turns written out in order. Field names:
        a: / b:            a turn (lines)
        a?.5: / b?.6:      a turn with a chance
        aq: / bq:          a question, its answers after ' => ' (answers shared by every question); the other's answer turn
                           is added after it
        +voice:            lines for one voice, added to the turn before (newbie, miner, builder, ...)
        if: cond cond      conditions for the turn before
    """
    turns = []
    for key, val in block['order']:
        if key.startswith('+'):
            last = turns[-1]
            if isinstance(last['lines'], list):
                last['lines'] = {'any': last['lines']}
            last['lines'][key[1:]] = S(val)
            continue
        if key == 'if':
            turns[-1]['if'] = val.split()
            continue
        m = re.match(r'^([ab])(q{0,2})(?:\?([0-9.]+))?$', key)
        assert m, 'bad field %s in %s' % (key, block['key'])
        by, q, chance = m.group(1), m.group(2), m.group(3)
        if q == 'qq':
            # Each question with its own answers: 'q1 => a / b || q2 => c / d'.
            lines = []
            for part in val.split(' || '):
                question, answers = part.split(' => ')
                lines.append({'line': question.strip(), 'then': [x.strip() for x in answers.split(' / ')]})
            turns.append(turn(by, lines, chance=float(chance) if chance else None))
            turns.append(turn('b' if by == 'a' else 'a', ['hm']))
        elif q:
            qs, ans = val.split(' => ')
            turns.append(turn(by, ask(S(qs), S(ans)), chance=float(chance) if chance else None))
            turns.append(turn('b' if by == 'a' else 'a', ['hm']))
        else:
            turns.append(turn(by, S(val), chance=float(chance) if chance else None))
    tags = block['tags']
    add(prefix + block['key'], block['about'], tags, block['leads'] or leads_of(base_leads, tags), turns,
        needs=block['needs'], weight=weight_of(block, default_w))


# ---------------------------------------------------------------------------------------------------------------------
# 1. Would you rather. b1/b2: b picks the first/second (and why); x1/x2: what's to be said for the first/second.

WYR = """
# creeper_size | creeper size question | mobs
q: would you rather fight 100 chicken-sized creepers or one creeper the size of a house? | 100 tiny creepers or one giant house-sized creeper, go
b1: the tiny ones, one sword sweep and half are gone | hundred tiny ones easy, they'd barely dent the grass | tiny ones. i'd pillar up and wait
b2: the giant one, you'd see it coming a mile off | one big one, i'm not getting swarmed | giant one. one creeper is one problem
x1: tiny blasts would barely scratch your armor | a hundred little hisses would be kinda cute
x2: a hundred of them would surround you in seconds | a few arrows and the big one's done

# bread_stew | bread or stew question | food
q: would you rather only eat bread forever or only rabbit stew? | bread for the rest of your life or rabbit stew for the rest of your life?
b1: bread. three wheat and done, no hunting | bread easy, it stacks to 64 | bread, i'm not chasing rabbits every day
b2: rabbit stew, it actually fills you up | stew. bread gets old by day two | rabbit stew, at least it has carrots in it
x1: wheat grows anywhere, rabbits run off | you can carry a whole stack of bread
x2: stew is like five foods in one bowl | bread forever sounds like a punishment

# mine_build | mining or building question | mining building
q: would you rather never mine again or never build again? | no more mining ever, or no more building ever. pick
b1: never mine. i'd trade for stone | give up mining, building is the fun part | never mine again, caves scare me anyway
b2: never build, i'd live in a cave happily | give up building. mining is my whole life | never build. a hole is a house
x1: you can trade for stone, you can't trade for a home | building is what makes a place yours
x2: no mining means no iron, no diamonds, nothing | a cave is a house if you squint

# nether_end | nether or end question | nether end
q: would you rather live in the nether forever or the end forever? | if you had to move to the nether or the end for good, which one?
b1: nether. at least there's food, hoglins | the nether, it's warm and there's stuff to do | nether. the end is just purple and quiet
b2: the end, nothing there sets you on fire | end. endermen leave you alone if you don't look | the end. quiet, no ghasts screaming
x1: there's no food in the end but chorus fruit | the nether has gold, quartz, all the good stuff
x2: one wrong step in the nether and you're in lava | ghasts would drive me mad in a week

# sleep_eat | sleep or food question | sleep food
q: would you rather never need to sleep or never need to eat? | no sleep ever or no food ever, which do you give up?
b1: never sleep, i'd mine all night | no sleep, the night is when stuff happens | sleep is for people with beds. no sleep
b2: never eat. think of all the time farming | no food. i hate running out of food in caves | never eat, hunger is the worst part
x1: imagine all the mining you'd get done at night | you'd never lose your spawn looking for a bed
x2: no sleep means phantoms every single night | farming is easy, phantoms are not

# rain_night | endless rain or night question | weather night
q: would you rather it always rained or it was always night? | forever rain or forever night?
b1: forever rain. at least it's daytime | rain, crops love it | always rain. i like the sound
b2: forever night. rain is so gloomy | always night, the stars are nice | night. at least mobs drop stuff
x1: fishing's better in the rain anyway | you can still see where you're going in rain
x2: rain brings storms, and storms bring charged creepers | night at least has stars and a moon

# lose_stuff | losing stuff question | storage death
q: would you rather lose all your stuff once, or lose one random item every day? | everything gone once, or one random item gone every day forever?
b1: all at once, get it over with | all at once. i can start over | once. i've died with everything before
b2: one a day, most days it'd be dirt | one item a day, i've got so much junk | one a day, odds are it's a seed
x1: one a day would drive you mad wondering what's next | starting over is half the fun
x2: half my inventory is cobble, i'd never notice | all at once means your best pickaxe too

# wolf_cat | wolf or cat question | pets animals
q: would you rather have a wolf or a cat following you everywhere? | dog or cat as your one pet forever?
b1: wolf, it fights for you | dog, obviously, it bites skeletons | wolf. loyal and scary
b2: cat. creepers run from it | cat, they bring you gifts in the morning | cat, they never take fall damage
x1: a wolf actually helps in a fight | wolves sit when you tell them, cats do whatever
x2: creepers won't come near a cat | wolves get you in trouble attacking everything

# horse_pig | horse or pig question | animals
q: would you rather ride a horse forever or a pig with a carrot on a stick? | travel by horse only, or by pig only, for life?
b1: horse, it's not even close | horse. jumping fences beats everything | horse, pigs are so slow
b2: pig. nobody steals a pig | pig, it's funnier | pig with a carrot. style points
x1: a horse clears fences and outruns zombies | a pig gets stuck on every fence
x2: you can't be sad riding a pig | horses wander off the second you get down

# desert_snow | desert or snow question | biomes home
q: would you rather live in the desert or the snowy plains? | sand forever or snow forever, where are you building?
b1: desert. it never rains, nice | desert, sandstone houses look good | desert, i hate being cold
b2: snow, it's peaceful | snow. igloos and foxes | snowy plains, the quiet is nice
x1: there are temples to raid in the desert | sand is free building blocks
x2: at least snowy places have some trees | snow at night looks unreal

# wood_tools | wood tools or leather question | tools armor
q: would you rather only ever use wooden tools or only ever wear leather armor? | wood tools forever or leather armor forever?
b1: wood tools, i'll just wear good armor | wooden tools. diamond armor makes up for it | wood tools, i can make a hundred of them
b2: leather armor, i'd dodge everything | leather armor, as long as my pickaxe is diamond | leather. a good pickaxe matters more
x1: armor keeps you alive, tools just make it faster | wooden tools are cheap, you'd never run out
x2: a wooden pickaxe can't even mine iron | you won't get anything good with wood tools

# ravager | ravager or vindicators question | fighting mobs
q: would you rather fight a ravager or three vindicators? | one ravager or three axe guys, pick your fight
b1: the ravager, it's slow to turn | ravager. one big target | ravager, i'd just block with a shield
b2: three vindicators, they die fast | the vindicators, ravagers hit way too hard | vindicators. a ravager would launch me
x1: a shield blocks most of the ravager's hits | three axes at once would shred you
x2: vindicators go down in a few good hits | a ravager can knock you clean off a ledge

# windows_door | windows or door question | building home
q: would you rather have a house with no windows or no door? | no windows ever or no door ever?
b1: no windows, it's safer | no windows, i like my walls solid | windows? who needs them. no windows
b2: no door. i'd dig in and wall it up | no door, i'll use a trapdoor in the floor | no door, zombies can't break what isn't there
x1: torches light it up fine without windows | nothing can look in at you
x2: zombies break doors down anyway | a house with no windows is just a box

# spawner_neighbour | spawner next door question | mobs home
q: would you rather live next to a spider spawner or a zombie spawner? | spider spawner or zombie spawner under your house?
b1: spiders, string for days | spider spawner, i need string for bows | spiders, they're chill in the day
b2: zombies. rotten flesh is food at least | zombie spawner, easy xp farm | zombies, they're slow
x1: spiders don't even attack you in daylight | string makes bows and wool
x2: zombies can't climb walls | a zombie spawner makes the best xp farm

# creeper_pet | pet creeper question | mobs pets jokes
q: would you rather have a creeper as a pet or a skeleton as a roommate? | pet creeper or skeleton roommate?
b1: pet creeper, i'd keep it in a glass box | creeper. it'd be like a weird green dog | the creeper, as long as it's tame
b2: skeleton roommate, it'd guard the place | skeleton. free arrows | the skeleton, it just sits there rattling
x1: a creeper doesn't shoot you while you sleep | a creeper never talks back
x2: one hiss and your house is a crater | the skeleton could hunt for you

# stone_armor | stone tools or no armor question | tools armor
q: would you rather use stone tools forever or never wear armor again? | stone tools for life or no armor for life?
b1: stone tools, armor is too important | stone tools. stone mines iron anyway | stone forever, i don't need fancy
b2: no armor, i'd just never fight | no armor. i'd be fast and careful | no armor, i'd keep my diamond pick
x1: stone still gets you iron and coal | armor is what keeps you alive at night
x2: stone can't mine diamonds, that's the end of it | a diamond pickaxe changes everything

# boat_cart | boat or minecart question | exploring
q: would you rather only travel by boat or only by minecart? | boats only or minecarts only for every trip?
b1: boats, they go on ice too | boat, rivers go everywhere | boat. no rails to lay
b2: minecart, rails go anywhere you put them | minecart, i love building rail lines | minecart. i'd put rails everywhere
x1: boats on ice are faster than anything | you don't need iron for every block of the way
x2: boats can't go uphill | rails take you right into your house

# chickens_llama | chickens or llama question | animals jokes
q: would you rather have ten chickens follow you everywhere or one llama that spits at you? | ten chickens on you forever or one angry llama?
b1: the chickens, free eggs | ten chickens, they're harmless | chickens. it'd be a parade
b2: the llama, it's one problem | llama. spit doesn't even hurt much | the llama, ten chickens is too loud
x1: chickens drop eggs as they walk | chickens can't hurt you at all
x2: ten chickens would get stuck in every door | llama spit is barely half a heart

# trader | wandering trader question | trade villagers
q: would you rather only ever trade with the wandering trader or never trade at all? | wandering trader forever or no trading ever?
b1: the trader, at least i get some stuff | wandering trader, sometimes he has good saplings | trader. bad prices but whatever
b2: never trade. i'll get it myself | no trading, the trader is a scammer | never, i'd rather earn it
x1: he sells stuff you can't find around here | some trading is better than none
x2: a whole emerald for one sapling? come on | everything he sells you can find yourself

# mineshaft_temple | mineshaft or temple question | structures
q: would you rather spend a night stuck in a mineshaft or a jungle temple? | locked in a mineshaft or a jungle temple overnight?
b1: mineshaft, there's rails and chests | mineshaft, i can at least mine my way out | the mineshaft, it's got loot
b2: jungle temple, it's small and quiet | temple, no cave spiders | jungle temple. arrow traps are easy if you know
x1: mineshafts have chests everywhere | you can always dig out of a mineshaft
x2: cave spiders in mineshafts are the worst | a temple has walls, a mineshaft has holes everywhere

# lost_cave_sea | getting lost question | caves ocean
q: would you rather get lost in a cave or lost at sea? | lost underground or lost on the ocean?
b1: cave, i'd dig up | cave. caves have ore at least | lost in a cave, i know caves
b2: at sea, you can see the sky | the ocean, just keep rowing | sea. no creepers on a boat
x1: you can always dig up toward the sky | drowned with tridents are worse than anything in a cave
x2: out at sea you can at least see the sun | a boat's safer than any cave at night

# unbreakable | unbreakable tool question | tools weapons
q: would you rather have a pickaxe that never breaks or a sword that never breaks? | unbreakable pickaxe or unbreakable sword?
b1: pickaxe, i use it way more | the pick, swords are easy to make | pickaxe. mining is my life
b2: sword, it's what keeps me alive | sword, i'd never fear a night | the sword. pickaxes are cheap anyway
x1: you swing a pickaxe a thousand times a day | a sword only matters a few times a night
x2: dying with a broken sword is the worst | a good sword saves you, a pickaxe saves time

# apples_pearls | apples or pearls question | gear
q: would you rather have unlimited golden apples or unlimited ender pearls? | infinite golden apples or infinite pearls?
b1: golden apples, you'd never die | gapples, easy | golden apples. infinite healing
b2: pearls, i'd teleport everywhere | ender pearls, no more walking | pearls. escape anything
x1: you can't die if you're always healing | apples work in every fight
x2: why fight when you can just leave | pearls get you up any cliff

# treehouse_bunker | treehouse or bunker question | home building
q: would you rather live in a treehouse or an underground bunker? | treehouse or bunker, where are you living?
b1: treehouse, the view | treehouse, high up and safe | treehouse. i'd build bridges between trees
b2: bunker, nothing gets in | bunker, i love being underground | bunker. solid stone all round
x1: nothing spawns on leaves | you can see trouble coming for miles
x2: one stray spark and a treehouse burns down | stone doesn't burn

# mountain_beach | mountain or beach question | home biomes
q: would you rather have a house on a mountain or on the beach? | mountain house or beach house?
b1: mountain, the view is unreal | mountain. above the clouds | the mountain, goats are great neighbours
b2: beach, sand and sea | beach, turtles come by | beach house, i'd fish every day
x1: you can see the whole world from up there | no drowned walking up a mountain
x2: goats will knock you right off the mountain | you can fish right off the porch

# hunt_farm | hunting or farming question | food farming
q: would you rather only hunt for food or only farm for it? | hunting only or farming only, forever?
b1: hunt, i like the chase | hunting. steak is the best | hunter life
b2: farm, it's steady | farming, plants don't run away | farm. i'd plant carrots everywhere
x1: steak and porkchops beat bread any day | animals breed, you never run out
x2: crops grow while you sleep | hunting gets boring once the animals are gone

# horse_kind | fast or jumpy horse question | animals
q: would you rather have a fast horse that can't jump or a slow one that jumps huge? | fast horse with no jump or slow horse with a huge jump?
b1: fast one, i'd just go around stuff | speed. fences can be broken | the fast one, obviously
b2: the jumper, i'd go over everything | jump. there are fences everywhere | the slow jumper, mountains are easy then
x1: most trips are just flat ground | speed wins every race
x2: one fence and the fast horse is stuck | you can't outrun a cliff

# fish_farm | fishing or farming question | fishing farming
q: would you rather never fish again or never farm again? | fishing or farming, which do you give up?
b1: never fish, it's boring | give up fishing, i'm bad at it | fishing, gone, i don't care
b2: never farm, i'd just fish | give up farming, i love fishing | no farming, fish is food anyway
x1: farms feed you way more than fishing | fishing is just sitting there
x2: fishing gets you saddles and books, not just fish | farms need so much work

# cave_noise | cave noise question | caves
q: would you rather always hear cave sounds or never hear anything? | constant cave noises, or silence forever?
b1: cave sounds, at least it's something | cave noises, i'm used to them | the noises, silence is creepier
b2: silence, those sounds are too creepy | never hear anything, peace at last | silence, cave sounds scare me
x1: you'd never hear a creeper hiss with no sound | silence would drive you mad
x2: you'd never get spooked again | those noises keep me up at night

# bee_phantom | bee or phantom question | mobs jokes
q: would you rather be followed by an angry bee forever or a phantom forever? | angry bee or phantom following you for life?
b1: bee, it'd die after it stings me | angry bee, it's small | bee. one sting and it's done
b2: phantom, i'd just stay inside at night | the phantom, it only comes out at night | phantom. bees are relentless
x1: a bee loses its stinger after one hit | a phantom swoops down from nowhere
x2: phantoms burn up in the sun | an angry bee brings its whole hive

# miner_builder | miner or builder question | mining building
q: would you rather be the best miner around or the best builder? | best miner or best builder, pick one
b1: best miner, diamonds every trip | miner. builders need miners anyway | the miner, i'd be rich
b2: best builder, people would visit my stuff | builder. anyone can dig | the builder, mining is just walking in the dark
x1: without miners nobody has stone to build with | miners find the diamonds
x2: anyone can dig a hole | a great build lasts forever

# fox_parrot | fox or parrot question | animals pets
q: would you rather have a fox that steals your stuff or a parrot that never stops talking? | thief fox or loud parrot?
b1: the fox, it's so cute | fox, i'd hide the good stuff | the fox, it only takes one thing
b2: parrot, i like the noise | parrot. it'd warn me about mobs | the parrot, it does mob sounds
x1: a fox only holds one thing at a time | foxes sleep all day anyway
x2: a parrot copies mob sounds, free alarm | a fox would grab your best sword off the floor

# daily_cake | cake question | food
q: would you rather eat cake every day or never have cake again? | cake every day forever, or no cake ever?
b1: cake every day, obviously | every day. cake is great | daily cake, no question
b2: never, cake is a pain to make | no cake, i'd survive | never. you need three buckets of milk every time
x1: cake on the table feeds you for days | life's short, eat cake
x2: three buckets of milk per cake | cake every day gets old, and you can't carry it around

# diamond_iron | diamond or iron question | ore diamonds
q: would you rather have one diamond now or ten iron every day for a week? | one diamond today or seventy iron over a week?
b1: one diamond now, it's a diamond | the diamond, i'm impatient | diamond. now. please
b2: the iron, that's seventy iron | iron, it adds up | the week of iron, i can count
x1: you can mine iron yourself anyway | a diamond is a diamond
x2: seventy iron is a full set of armor and then some | one diamond doesn't even make a pickaxe

# base_size | base size question | home building
q: would you rather have a huge base nobody ever sees or a small one everyone visits? | giant hidden base or tiny popular one?
b1: huge base, i don't need visitors | the huge one, all to myself | giant secret base
b2: small one, i like company | small and busy, sounds cozy | the small one, people make a place
x1: room for every chest you'd ever need | nobody touches your stuff
x2: what's a big base with nobody in it | visitors bring news and trades

# torches_beds | torches or beds question | sleep night
q: would you rather have no torches or no beds? | give up torches or give up beds?
b1: no torches, i'd use campfires | torches, i'll just sleep through every night | no torches, beds are more important
b2: no beds, i'd stay up all night | beds, torches keep the mobs away | no beds. light matters more
x1: a bed skips the dark anyway | beds set your spawn, that's huge
x2: without torches every cave is a death trap | you can wall yourself in for the night

# roam_home | roaming or home question | exploring home
q: would you rather explore forever and never have a home, or stay home forever? | explore and never settle, or settle and never explore?
b1: explore, there's always more | the road, every time | explore. homes are overrated
b2: stay home, i love my place | home, i've seen enough | stay home. my bed, my chests
x1: there's always a new biome over the next hill | a house just ties you down
x2: you can't decorate a road | a home is where your stuff is safe

# ice_sand | ice or sand world question | biomes
q: would you rather the whole world be ice or the whole world be sand? | all ice or all sand?
b1: ice, boats would be so fast | ice world, i'd boat everywhere | ice. sliding everywhere
b2: sand, at least you can build with it | sand, glass everywhere | sand. ice is slippery and sad
x1: boats on ice are the fastest thing there is | ice is pretty in the sun
x2: you'd slide off every cliff on ice | sand makes glass and sandstone

# dark_hunger | night vision or hunger question | caves food
q: would you rather see in the dark or never get hungry? | night vision forever or no hunger forever?
b1: see in the dark, caves would be easy | night vision, no more torches | seeing in the dark, for sure
b2: never hungry, food is so much work | no hunger, i'd never stop mining | never hungry, easy
x1: you'd spot every mob and every ore | no more torches eating your coal
x2: you still need food to heal | half my day is finding food

# fall_fire | fall or fire damage question | danger health
q: would you rather never take fall damage or never take fire damage? | no fall damage or no fire damage?
b1: no fall damage, i'd jump off everything | fall damage, it's killed me so many times | no fall. i'm clumsy
b2: no fire damage, lava swimming | fire, lava would be a pool | no fire damage, i'd never fear lava again
x1: falling kills more people than lava | jumping off cliffs would be the fastest way down
x2: lava would just be orange water | lava takes your stuff too, falling doesn't

# immune | immune to a mob question | mobs
q: would you rather be immune to creepers or immune to skeletons? | creepers can't hurt you or skeletons can't, which one?
b1: creepers, they wreck your house too | creepers, by far | creepers. i've lost too much to them
b2: skeletons, they're always shooting | skeletons, arrows from nowhere | skeletons. creepers you can at least hear
x1: a creeper takes your house with it | creepers are why everyone builds in stone
x2: skeletons are everywhere at night | skeletons knock you off ledges into lava

# sword_bow | sword or bow question | weapons fighting
q: would you rather only have a sword forever or only a bow forever? | sword or bow, you only get one
b1: sword, no arrows to run out of | sword, simple | the sword. arrows run out
b2: bow, why get close | bow, i'd never get hit | bow, keep them far away
x1: a sword never runs out of ammo | creepers up close are fine if you're quick
x2: you can drop a creeper before it gets close | skeletons are easy with a bow

# mushroom_mountain | odd house question | home building
q: would you rather live inside a giant mushroom or a hollowed-out mountain? | giant mushroom house or mountain house?
b1: the mushroom, it's so cute | mushroom, i've always wanted to | giant mushroom, no question
b2: the mountain, solid stone all round | mountain. it's a fortress | inside a mountain. nothing gets in
x1: a mushroom house is one of a kind | mushroom blocks are easy to cut
x2: a mountain is basically a free castle | nothing breaks a mountain

# tower_hole | tower or hole question | building mining
q: would you rather build the tallest tower or dig the deepest hole? | tallest tower or deepest hole?
b1: tallest tower, you can see it from everywhere | tower. build up | the tower, holes are boring
b2: deepest hole, straight to bedrock | the hole, it's less work | hole. imagine the ore on the way
x1: a tower is a landmark for everyone | you can see the whole world from the top
x2: you find ore all the way down a hole | a hole is done at bedrock, a tower never ends

# stairs_ladders | stairs or ladders question | building
q: would you rather never use stairs or never use ladders? | no stairs or no ladders, forever
b1: no stairs, ladders save room | no stairs. ladders are fine | stairs gone, i'll climb
b2: no ladders, i hate climbing | ladders gone, stairs are nicer | no ladders, i always fall off them
x1: ladders fit in one block | stairs eat up half your room
x2: stairs look way better | it's way harder to fall off stairs

# wool_glass | wool or glass question | building decor
q: would you rather have infinite wool or infinite glass? | infinite wool or infinite glass?
b1: wool, every colour | wool. carpets everywhere | wool, beds and rugs for life
b2: glass, windows on everything | glass, giant greenhouse | glass. i'd build a glass dome
x1: wool comes in every colour there is | you can make beds from wool
x2: glass needs sand and smelting, that's the hard one | shearing sheep is easy anyway

# bodyguard | bodyguard question | mobs pets
q: would you rather have an iron golem bodyguard or a pack of five wolves? | iron golem or five wolves guarding you?
b1: iron golem, it throws zombies | golem. it's a tank | the golem, it never quits
b2: wolves, they're fast | five wolves, they swarm anything | the wolf pack, loyal
x1: a golem tanks hits all day | an iron golem throws mobs into the sky
x2: wolves follow you everywhere, golems just wander | five wolves are faster than one golem

# rain_ever | rain every day question | weather
q: would you rather it rained every day or never rained again? | rain every day or no rain ever?
b1: rain every day, crops grow anyway | rain daily, i like it | every day, i love the sound
b2: never rain, sunny forever | never. rain is gloomy | no rain ever, please
x1: fishing is faster in the rain | rain puts out fires
x2: rain means thunder means charged creepers | sunny days mean zombies burn

# one_heart | one heart question | health armor
q: would you rather have one heart in full netherite, or full hearts and no armor? | one heart in netherite, or ten hearts naked?
b1: one heart in netherite, i'd be careful | netherite. armor is everything | one heart, but full netherite
b2: full hearts, no armor | ten hearts. one heart is one fall | hearts. armor doesn't stop a fall
x1: netherite stops almost everything | you'd just never fight anything big
x2: one bad fall and that's it | one bee sting and you're gone

# cave_stars | sleeping spot question | sleep night
q: would you rather sleep in a cave or out under the stars? | cave or open sky for the night?
b1: cave, walled in | cave, safe and cozy | a cave. dirt wall and done
b2: under the stars, it's beautiful | stars, i'd risk it | out under the sky
x1: two blocks of dirt and you're safe | caves keep the rain off
x2: the stars are worth it | caves have mobs too, deeper down

# mine_or_farm_job | job question | mining farming
q: would you rather work in a mine all day or on a farm all day? | mine shift or farm shift, every day?
b1: mine, it's cool down there | the mine, you never know what you'll find | mine. farms are boring
b2: farm, sunshine | farm, it's calm | the farm. mines are dark
x1: every day in a mine is a treasure hunt | farms are the same every day
x2: farms are peaceful, no creepers in the dark | you get to see the sun

# tame_mob | tame mob question | mobs pets
q: would you rather have a tame enderman or a tame spider? | pet enderman or pet spider?
b1: enderman, it'd carry blocks for me | enderman. it could build with me | the enderman, so cool
b2: spider, i'd ride it up walls | spider. climbing pet | the spider, it'd guard the walls
x1: an enderman could fetch stuff for you | endermen teleport away from trouble
x2: a spider climbs anything | endermen run off the second it rains

# neighbours_nether | villagers or piglins question | villagers nether
q: would you rather live in a village or in a bastion with piglins? | villagers or piglins as neighbours?
b1: villagers, they're harmless | village. they just say hm | villagers, obviously
b2: piglins, they love gold like me | piglins, at least they trade gold | bastion. the loot!
x1: villagers never attack you | villages have beds and food
x2: piglins barter good stuff for gold | a bastion is a castle full of loot

# forget_lose | forgetting question | storage exploring
q: would you rather forget where everything is or lose your whole inventory? | forget the map or lose your stuff?
b1: forget the map, exploring again is fun | the map. i'd find my way | forget where stuff is, i'd find it again
b2: lose my stuff, i know where to get more | inventory, stuff is replaceable | lose the stuff. memory is everything
x1: getting lost is half the fun | stuff takes ages to get back
x2: if you remember where your chests are, nothing's lost | you'd never find your house again

# hungry_hurt | hungry or hurt question | health food
q: would you rather always be a little hungry or always be a little hurt? | always hungry or always one heart down?
b1: hungry, i'd snack all day | hungry. food is everywhere | a little hungry, it's fine
b2: hurt, it's only a heart | a little hurt, i barely notice | hurt. hunger makes me slow
x1: a snack fixes hunger | that missing heart could be the one that matters
x2: you can't sprint when you're hungry | one heart down is barely anything

# understand | understanding question | animals villagers
q: would you rather understand animals or understand villagers? | talk to animals or actually talk to villagers?
b1: animals, i want to know what cows think | animals. imagine what the cats say | animals, easy
b2: villagers, think of the trades | villagers. what does hm even mean | villagers, they have stories
x1: villagers probably only talk about emeralds | animals see everything
x2: villagers could tell you where stuff is | you'd finally get the good prices

# famous | being famous question | building end
q: would you rather be known for the best build around or for killing the dragon? | famous builder or dragon slayer?
b1: the build, it lasts | builder, people visit it | the best build, for sure
b2: the dragon, that's a legend | dragon slayer. sounds cool | the dragon, everyone remembers that
x1: a build is all yours | people can walk around a build
x2: killing the dragon is a story forever | builds get wrecked, a dragon kill doesn't

# axolotl_frog | axolotl or frog question | animals pets
q: would you rather have a pet axolotl or a pet frog? | axolotl or frog?
b1: axolotl, it fights for you underwater | axolotl, the pink ones | axolotl, so cute
b2: frog, it eats slimes | frog. they croak | frog, they jump so high
x1: axolotls fight drowned with you | they come in five colours
x2: frogs can live out of water | a frog eats small slimes whole

# lava_spiders | bad neighbours question | danger home
q: would you rather live next to a lava lake or a forest full of spiders? | lava lake next door or spider forest next door?
b1: lava, it's pretty at night | lava lake, i just won't go near it | lava. at least it doesn't move
b2: spiders, string farm | spider forest. spiders are fine in the day | spiders. lava burns down houses
x1: lava stays put, spiders don't | lava gives off light for free
x2: lava sets fire to anything wooden nearby | spiders are only mean at night

# arrows_food | arrows or food question | weapons food
q: would you rather have infinite arrows or infinite food? | endless arrows or endless food?
b1: arrows, i'd hunt for food anyway | arrows. food is easy | infinite arrows, skeletons beware
b2: food, never hungry again | food, obviously | food. arrows are easy to craft
x1: with infinite arrows you can hunt anything | arrows need feathers, flint and sticks every time
x2: food is something you need every day | you can just pick arrows back up

# chest_furnace | chest or furnace question | storage crafting
q: would you rather have a chest that never fills or a furnace that never needs fuel? | endless chest or fuel-free furnace?
b1: endless chest, no more sorting | the chest, i hoard everything | chest. storage is my problem
b2: furnace, fuel is so annoying | the furnace, coal for days | furnace, i'd smelt everything
x1: storage is the biggest problem in any base | you'd never throw anything away again
x2: coal goes so fast | you'd save every lump of coal for torches

# walk_odd | walking question | jokes
q: would you rather only walk backwards or only walk sideways? | backwards forever or sideways forever?
b1: backwards, you'd see what's chasing you | backwards. at least it's straight | backwards, lol
b2: sideways, like a crab | sideways. you can still look ahead | sideways, i'd see both ways
x1: nothing sneaks up on you going backwards | going backwards is still going straight
x2: sideways you can see where you're going | backwards you'd walk right off a cliff

# roof_floor | roof or floor question | building home
q: would you rather have a house with no roof or no floor? | no roof or no floor?
b1: no roof, i'd see the stars | no roof. the rain is fine | roof, gone. the view
b2: no floor, the ground is a floor | no floor, dirt is fine | no floor. mobs spawn on roofs anyway
x1: you'd see the stars every night | a house with no floor is just a hole
x2: no roof means phantoms and rain | the ground is already a floor

# village_stronghold | finding question | structures villagers
q: would you rather find a village right now or a stronghold? | village or stronghold, which do you want to find next?
b1: village, beds and food | village, i need trades | the village, easy
b2: stronghold, it's so rare | stronghold, the library | stronghold. the end portal!
x1: villages have food, beds and iron golems | you can live in a village
x2: strongholds have libraries full of books | you find villages all the time

# one_material | one material question | building trees
q: would you rather only have one kind of wood forever or one kind of stone? | one wood type for life or one stone type for life?
b1: one wood, i'd pick spruce and be done | wood, spruce goes with everything | one wood. oak's enough
b2: one stone, cobble is fine | one stone. stone bricks forever | stone. i hardly use wood
x1: wood is just trim, stone does the walls | you can get away with one wood easy
x2: half the houses around here are one stone anyway | stone bricks go with everything

# jump_run | jumping or running question | random
q: would you rather jump really high or run really fast? | super jump or super speed?
b1: jump, i'd never need stairs | jump. cliffs would be nothing | high jump, easy
b2: run, outrun everything | speed, travel would be so quick | run fast. outrun creepers
x1: you could jump onto your roof | no more pillaring up
x2: you could outrun any mob | a long trip would take no time

# skip_night | night question | sleep night
q: would you rather sleep through every night or stay up every night? | always skip the night or always stay up?
b1: sleep every night, safe and easy | sleep, nights are long | skip it. every time
b2: stay up, the night is when stuff happens | stay up. mobs drop the good stuff | stay up, i like the dark
x1: phantoms show up if you don't sleep | the night is just mobs
x2: night is when the good drops come out | the stars are the best part

# town_island | town or island question | town home
q: would you rather live in a big busy town or alone on an island? | busy town or lonely island?
b1: busy town, there's always someone | town, i like people | the town. trading and gossip
b2: island, peace and quiet | island. just me and the sea | the island, people are a lot
x1: a town has someone to trade with | nobody to help you on an island
x2: nobody touches your stuff on an island | towns are so loud

# snowball_duel | fight kind question | fighting jokes
q: would you rather have a snowball fight or a sword duel? | snowball fight or sword duel?
b1: snowball fight, nobody gets hurt | snowballs, it's fun | snowballs. friendlier
b2: duel, let's go | sword duel. i want to win | duel. snowballs are for babies
x1: snowballs don't cost you your gear | nobody respawns upset after a snowball fight
x2: a duel actually proves something | snowballs only knock you back

# hug | hug question | animals jokes
q: would you rather hug a sheep or a polar bear? | sheep hug or polar bear hug?
b1: sheep, they're fluffy | sheep. polar bears bite | sheep, obviously
b2: polar bear, it's huge and fluffy | polar bear. go big | the bear, i'd risk it
x1: a sheep won't maul you | sheep are basically walking wool
x2: polar bears are only angry around cubs | go big or go home

# bed_chests | bed or chests question | home storage
q: would you rather lose your bed or your chests? | your bed burns or your chests vanish?
b1: my bed, it's three wool | lose the bed. easy to make | bed, i can make another tonight
b2: chests, i'll find new stuff | lose the chests. starting fresh | chests. my bed is my spawn
x1: a bed is just wool and planks | chests hold everything you've ever found
x2: your bed is where you come back to | chests are mostly junk anyway

# lucky | lucky tool question | fishing mining
q: would you rather have a lucky fishing rod or a lucky pickaxe? | luck on your rod or luck on your pick?
b1: rod, saddles and books from fishing | lucky rod, i'd fish all day | rod. relaxing and rich
b2: pickaxe, diamonds everywhere | lucky pick. more diamonds | the pickaxe, obviously
x1: you can fish up enchanted books | fishing is relaxing, mining isn't
x2: lucky diamonds beat lucky fish | a lucky pick pays for everything

# cave_ocean | exploring question | caves ocean exploring
q: would you rather explore a giant cave or a giant ocean? | huge cave or huge ocean?
b1: cave, ore everywhere | the cave, i love caves | giant cave. lush caves maybe
b2: ocean, shipwrecks and treasure | the ocean, monuments and coral | ocean, i'd boat for days
x1: caves have ore around every corner | a cave has walls to put torches on
x2: shipwrecks have treasure maps | coral reefs are the prettiest thing there is

# perfect_day | perfect day question | feelings day
q: would you rather have one perfect day or ten okay days? | one perfect day or ten fine days?
b1: one perfect day, you'd remember it forever | the perfect one | perfect day. memories
b2: ten okay days, more is more | ten. i'll take steady | ten days, steady wins
x1: a perfect day is a story you tell forever | okay days blur together
x2: ten days is ten times the stuff done | one day goes by so fast

# carrots_steak | golden carrots or steak question | food
q: would you rather eat only golden carrots or only steak forever? | golden carrots or steak, for life?
b1: golden carrots, best food there is | carrots. they fill you up the most | golden carrots, easy
b2: steak, cows are everywhere | steak. golden carrots cost gold | steak, it's cheaper
x1: golden carrots keep you full the longest | carrots grow back, cows have to be bred
x2: golden carrots eat all your gold | steak is just as good and way cheaper

# pie_cake | pie or cake question | food
q: would you rather have pumpkin pie or cake? | pie or cake, pick one
b1: pumpkin pie, you can carry it | pie. it fits in your pocket | pumpkin pie, cozy
b2: cake, you share it | cake, it's a party | cake. seven slices
x1: pie fits in your pocket | you can eat pie while running around
x2: a cake on the table feeds everyone | cake is a whole event

# cookies_stew | cookies or stew question | food
q: would you rather have cookies or mushroom stew forever? | cookies or mushroom stew for life?
b1: cookies, eight at once | cookies, they're tiny and cute | cookies. snacks
b2: mushroom stew, it's a real meal | stew. it fills you | mushroom stew, cozy
x1: you get eight cookies from one craft | cookies stack, stew doesn't
x2: one stew is worth like four cookies | stew is a proper meal

# falls_indoors | indoor water question | decor home
q: would you rather have a waterfall in your house or a lava fall behind glass? | indoor waterfall or lava fall behind glass?
b1: waterfall, it's relaxing | water. the sound is nice | waterfall, and a pond
b2: lava fall, it lights the room | lava, it's so pretty | lava behind glass, so cool
x1: water can't burn your house down | the sound of water is calming
x2: lava lights the whole room with no torches | lava looks amazing at night

# taming_aim | taming or aim question | animals weapons
q: would you rather tame every horse first try or never miss an arrow? | perfect horse taming or perfect aim?
b1: horses, i always get thrown off | taming, it takes forever | horse taming. so annoying
b2: never miss, think of the skeletons | aim, easy | perfect aim, every time
x1: horse taming takes forever | a good horse changes everything
x2: you'd never lose a fight with a bow | perfect aim means no wasted arrows

# be_animal | animal for a day question | animals jokes
q: would you rather be a chicken for a day or a cow for a day? | chicken or cow for a day?
b1: chicken, i could float down from anywhere | chicken. tiny and fast | chicken, i'd lay eggs lol
b2: cow, just chill and eat grass | cow, calm life | the cow. moo
x1: chickens never take fall damage | chickens are tiny, nobody notices them
x2: cows have the most peaceful life there is | at least a cow is big

# shear_milk | sheep or cow chores question | animals farming
q: would you rather shear sheep forever or milk cows forever? | shearing or milking, forever?
b1: shear sheep, wool is useful | sheep. colours! | shearing, it's satisfying
b2: milk cows, it cures poison | milk. cake needs it | cows, i like cows
x1: wool makes beds and carpets | sheep grow it back eating grass
x2: milk clears any bad effect | cake needs three buckets

# carry_forever | carrying question | jokes
q: would you rather carry a cake everywhere or a bucket of lava everywhere? | always holding a cake or always holding lava?
b1: cake, snacks | the cake, it's harmless | cake, obviously
b2: lava, it's a weapon | lava bucket, burn the mobs | lava. nobody messes with me
x1: a cake can't burn your house down | at least you'd never be hungry
x2: a lava bucket stops anything | lava's the strongest thing you can carry

# lost_where | getting lost question | nether caves
q: would you rather be lost in the nether or lost in the deep dark? | stranded in the nether or in the deep dark?
b1: nether, at least you can see | the nether, i'd find a portal | nether. the warden scares me more
b2: deep dark, just sneak | deep dark. it's quiet at least | the deep dark, no lava everywhere
x1: in the nether you can see where you're going | the warden finishes you in two hits
x2: in the deep dark you just sneak and you're fine | the nether has lava and ghasts everywhere

# outpost_witch | neighbour question | villagers mobs
q: would you rather live next to a pillager outpost or a witch hut? | pillagers next door or a witch next door?
b1: outpost, i'd raid it every day | pillagers. free crossbows | the outpost, there's a caged golem to free
b2: the witch, she stays in her hut | witch hut. one witch is fine | the witch, quiet neighbour
x1: an outpost has loot and a golem to free | pillagers are easy one at a time
x2: pillagers patrol, a witch just sits there | one witch beats a whole outpost

# ice_honey | odd house question | building jokes
q: would you rather live in a house of ice or a house of honey blocks? | ice house or honey house?
b1: ice, it's so pretty | ice house. sparkly | ice, obviously
b2: honey, it's warm and sticky | honey house, so cozy | honey. it probably smells nice
x1: ice is see-through like glass | ice looks amazing at sunset
x2: ice melts next to a torch | honey blocks stop you falling

# villager_golem | villager or golem question | villagers
q: would you rather be a villager or an iron golem? | villager life or golem life?
b1: villager, just trade and sleep | villager. no responsibilities | a villager, hm
b2: iron golem, protect the town | golem. i'd throw zombies | the golem, so strong
x1: villagers just work and sleep, no stress | golems never get to sleep
x2: villagers get eaten by zombies | golems are basically heroes

# double_mobs | double mobs question | mobs
q: would you rather have no creepers but double phantoms, or no phantoms but double creepers? | no creepers and double phantoms, or the other way?
b1: no creepers, phantoms are easy if you sleep | no creepers. sleep fixes phantoms | creepers gone, easy
b2: no phantoms, creepers you can see coming | no phantoms. they're the worst | phantoms gone, double creepers is fine
x1: phantoms only come if you skip sleep | creepers wreck builds, phantoms don't
x2: phantoms dive at you from nowhere | you can hear creepers coming

# slime_bee | slime or bee pet question | pets animals
q: would you rather have a pet slime or a pet bee? | slime or bee as a pet?
b1: slime, it bounces around | slime. so squishy | slime, it'd be a good guard
b2: bee, it makes honey | bee. it's fuzzy | bee, crops grow around it
x1: a slime is basically a jelly dog | slimes don't die after one sting
x2: bees make your crops grow | honey, that's why

# year_without | year without question | diamonds food
q: would you rather go a year without diamonds or a year without bread? | no diamonds or no bread for a year?
b1: no diamonds, iron is fine | diamonds. i'll live | no diamonds, easy
b2: no bread, i'll eat steak | no bread. there's other food | bread, i barely eat it anyway
x1: iron gear does the job | bread is the easiest food there is
x2: diamonds are what everything good needs | there's a hundred other foods

# roads_portals | travel network question | building exploring
q: would you rather have a road to every village or a portal to every village? | roads everywhere or portals everywhere?
b1: roads, you see the world on the way | roads. walking is nice | road, i like the trip
b2: portals, instant travel | portals. no walking | portals, obviously
x1: a road shows you the land on the way | portals mean the nether every trip
x2: walking takes forever | a portal is two steps and you're there

# starts | starting question | gear villagers
q: would you rather start in iron gear somewhere random, or with nothing by a village? | iron kit in the wild or empty pockets by a village?
b1: iron gear, i'd survive anywhere | the iron. a village can wait | iron gear, easy start
b2: the village, beds and food | village start, that's free food | village. i'd trade my way up
x1: full iron means the first night is nothing | you can find a village later
x2: a village has beds, food, maybe a smith | iron gear won't feed you

# first_things | first things question | memories tools
q: would you rather lose your first ever pickaxe or your first ever bed? | first pick or first bed, which one goes?
b1: the pickaxe, it was wood anyway | pickaxe. it was falling apart | my first pick, it broke already lol
b2: the bed, i've slept in a hundred | bed, they're all the same | bed. the pick has history
x1: a first pickaxe is wood, it'd break anyway | the pick probably broke ages ago
x2: every bed is the same bed | that pick dug your first iron

# pumpkin_boots | wearing question | armor jokes
q: would you rather wear a pumpkin on your head forever or only leather boots forever? | pumpkin head or leather boots, forever
b1: pumpkin, endermen leave me alone | pumpkin. no more angry endermen | the pumpkin, it's a look
b2: leather boots, at least i can see | boots, the pumpkin blocks half your view | leather boots. comfy
x1: endermen can't get mad at you through a pumpkin | it's a fashion statement
x2: you can barely see out of a pumpkin | boots don't block anything

# farmers_librarians | villager job question | villagers trade
q: would you rather only trade with farmers or only with librarians? | farmers only or librarians only?
b1: farmers, they buy crops | farmers. easy emeralds | farmer, i've got wheat
b2: librarians, books | librarians. mending! | librarian, obviously
x1: farmers buy wheat, emeralds for nothing | you can get golden carrots from a farmer
x2: librarians sell enchanted books | librarians sell name tags too

# stuck_where | stuck question | ocean danger
q: would you rather be stuck in a boat in the middle of the ocean or on a pillar in the sky? | ocean boat or sky pillar, stuck?
b1: boat, i can fish | the boat, at least it moves | boat. ocean's nice
b2: pillar, nothing can get me | pillar, i'll just wait | sky pillar. phantoms aside
x1: you can fish from a boat forever | a boat goes somewhere eventually
x2: no mobs reach you up a pillar | the drowned would get you in a boat

# glass_dirt | glass or dirt house question | building
q: would you rather build a house only from glass or only from dirt? | glass house or dirt house?
b1: glass, the view | glass house. sunlight | glass, fancy
b2: dirt, cozy and private | dirt. it's honest | dirt house, classic
x1: you'd see sunrise from bed | glass houses look amazing lit up
x2: dirt is everywhere, free house | glass means everyone sees you sleep

# sun_moon | sun or moon question | sky
q: would you rather never see the sun again or never see the moon again? | no more sun or no more moon?
b1: no sun, i live underground anyway | sun, gone. i'm a cave person | no sun. the moon is prettier
b2: no moon, sun is life | the moon, gone. i sleep anyway | moon. nights are for sleeping
x1: half my life is underground anyway | the moon is prettier
x2: the sun is what burns the zombies | daytime is safe time

# map_compass | map or compass question | exploring
q: would you rather have a map that always works or a compass that points home? | perfect map or a compass home?
b1: map, see everything | map. maps are beautiful | the map, plan routes
b2: compass home, never lost | compass. home is all i need | compass, simple
x1: a map shows you what's around | you can mark places on a map
x2: you only ever need to find home | a compass fits in your pocket

# filter | talking question | jokes friends
q: would you rather say everything you think out loud, or never talk again? | no filter or no voice?
b1: say everything, i do anyway | everything. honesty | no filter lol
b2: never talk, i'm fine with quiet | never talk. less trouble | silence, easy
x1: at least people would know where you stand | you'd never be lonely talking
x2: half of what i think would get me in trouble | quiet never hurt anyone

# sing_dance | silly mobs question | mobs jokes music
q: would you rather creepers sang before exploding or skeletons danced instead of shooting? | singing creepers or dancing skeletons?
b1: singing creepers, a warning song | creepers. at least you'd hear them | singing creepers lol
b2: dancing skeletons, no more arrows | skeletons dancing. no arrows | dancing skeletons, easy
x1: a warning song beats a quiet hiss | you'd hear them coming for miles
x2: no arrows is way safer | creepers still blow up even if they sing

# friends_kind | friends question | friends
q: would you rather have one best friend or a big group of friends? | one best friend or a whole crew?
b1: one best friend, someone you trust | one. quality | best friend. no drama
b2: a big crew, more fun | group, more people more stuff | crew. raids, towns, all of it
x1: one friend you can really trust | groups fight over loot
x2: a group can build a whole town | more friends, more help when you die

# build_partner | build partner question | building friends
q: would you rather build alone or with a really bossy partner? | solo build or bossy build partner?
b1: alone, my build my way | alone. peace | solo, every time
b2: bossy partner, twice as fast | partner. even bossy helps | the partner, i'd learn stuff
x1: nobody moves your blocks | your build looks how you want
x2: two people build twice as fast | someone to tell you the roof is ugly

# pink_squeak | odd tools question | tools jokes
q: would you rather have a bright pink sword or a pickaxe that squeaks every swing? | pink sword or squeaky pickaxe?
b1: pink sword, it's a look | pink. i'd rock it | the pink sword, stylish
b2: squeaky pickaxe, funny | squeaky pick. mining music | the squeak, obviously
x1: a pink sword is scary in its own way | nobody forgets a pink sword
x2: a squeak every swing is basically a song | you can't hide a pink sword

# dirt_slots | inventory question | storage
q: would you rather always carry 100 dirt or only have 9 slots? | forever 100 dirt or only your hotbar?
b1: the dirt, i carry it anyway | dirt. i already do | 100 dirt, easy
b2: nine slots, keeps it simple | hotbar only. less junk | nine slots, travel light
x1: dirt saves lives, pillar up anytime | a stack of dirt is never wasted
x2: less room means less junk | you'd never have to sort again

# fly_breathe | flying or breathing question | sky ocean
q: would you rather be able to fly or breathe underwater? | flying or underwater breathing?
b1: fly, no question | flying, obviously | fly. elytra without the elytra
b2: breathe underwater, the ocean is huge | underwater. monuments, shipwrecks | water breathing, nobody goes down there
x1: you'd never fall again | travel would take no time
x2: the ocean is half the world and nobody explores it | you'd never drown, that's half my deaths

# recipes_lost | recipes or lost question | crafting exploring
q: would you rather know every crafting recipe or never get lost? | every recipe or never lost?
b1: recipes, i forget them all the time | every recipe. so useful | recipes, crafting is hard
b2: never lost, i'm always lost | never get lost. home every time | never lost, easy
x1: half the game is knowing what to craft | you'd never waste materials
x2: getting lost has killed me more than anything | you can always ask someone for a recipe

# gross_food | gross food question | food jokes
q: would you rather eat rotten flesh for a day or spider eyes for an hour? | rotten flesh all day or spider eyes for an hour?
b1: rotten flesh, i've done it before | flesh, it fills you a bit | rotten flesh, ugh, fine
b2: spider eyes, it's just an hour | spider eyes. short pain | an hour of eyes. done
x1: rotten flesh only makes you hungry, eyes poison you | i've lived on flesh before
x2: an hour is nothing | a whole day of rotten flesh is miserable

# zombie_fight | zombie question | mobs fighting
q: would you rather fight a baby zombie or a regular zombie with a diamond sword? | baby zombie or armed zombie?
b1: the baby, it's tiny | baby zombie, they're small | baby zombie, i'll just kick it
b2: the armed one, at least it's slow | regular one, baby zombies are menaces | the big one. babies are the worst
x1: the diamond sword one hits so hard | a baby has no weapon at all
x2: baby zombies are so fast you can't hit them | the big one is slow, you can back off

# diamonds_iron | diamonds or iron question | ore mining
q: would you rather never find diamonds again or never find iron again? | no diamonds or no iron, forever?
b1: no diamonds, iron does everything | diamonds, i'll live without | no diamonds. iron is more useful
b2: no iron, i'd go straight to diamonds | iron, i've got enough | no iron. diamonds are better anyway
x1: you need an iron pick to even mine diamonds | iron makes buckets, shears, rails, everything
x2: diamonds make the best tools | iron's nothing next to diamond

# noises | mob noise question | mobs
q: would you rather hear a creeper hiss every night or a ghast scream every hour? | nightly hiss or hourly ghast scream?
b1: the hiss, once a night is fine | creeper hiss. at least it's quiet | hiss. ghasts are the worst noise
b2: ghast scream, i'd get used to it | the ghast, it's just noise | ghast. a hiss means a blast
x1: a hiss once a night is nothing | ghast screams make you jump every time
x2: a hiss means something's about to blow | a scream is just a noise

# boss_fight | boss question | end fighting
q: would you rather fight the ender dragon or the wither? | dragon or wither, which fight?
b1: the dragon, it's a known fight | dragon. break the crystals and go | the dragon, the wither is chaos
b2: the wither, at least it comes to you | wither. you pick where to fight | the wither, no trip to the end
x1: the dragon's the same fight every time, crystals then hits | the wither blows up everything around it
x2: you choose where to spawn the wither | the dragon can knock you into the void

# odd_size | odd size mob question | mobs jokes
q: would you rather fight a bee the size of a cow or a ghast the size of a chicken? | cow-sized bee or chicken-sized ghast?
b1: the giant bee, it's still just a bee | big bee. one sting and it's done | the bee, honestly
b2: the tiny ghast, cute fireballs | tiny ghast. little fireballs | the small ghast, lol
x1: a bee still dies after it stings | you could hit a big bee easy
x2: a cow-sized stinger would be terrifying | tiny fireballs probably just tickle

# treasure | treasure question | exploring
q: would you rather follow a treasure map or open a mystery chest right now? | treasure map or mystery chest?
b1: the map, the trip is the fun part | map. adventure | treasure map, always
b2: the chest, right now, no walking | chest. instant | the chest, i'm impatient
x1: buried treasure always has a heart of the sea | the trip is half the fun
x2: maps send you halfway across the ocean | a chest right now beats a maybe later

# glow_hide | glow or invisible question | night
q: would you rather glow in the dark or be invisible at night? | glow at night or invisible at night?
b1: glow, no more torches | glowing. walking torch | glow, i'd light the way
b2: invisible, mobs would never see me | invisible. sneak past everything | invisible at night, easy
x1: you'd never need a torch again | mobs can't spawn near a light
x2: mobs can't chase what they can't see | glowing makes you a target for every skeleton

# grow_stuff | growing question | farming trees
q: would you rather have infinite bone meal or infinite saplings? | endless bone meal or endless saplings?
b1: bone meal, everything grows instantly | bone meal. instant farms | bone meal, flowers everywhere
b2: saplings, forests forever | saplings. wood for life | saplings, i'd plant everywhere
x1: bone meal grows your trees and your crops | flowers everywhere you step
x2: saplings are wood forever | you can make bone meal from skeletons anyway

# lush_mushroom | odd biome question | biomes caves
q: would you rather live in a lush cave or on a mushroom island? | lush cave or mushroom island?
b1: lush cave, glow berries and axolotls | lush cave. so pretty | the cave, glow berries
b2: mushroom island, no mobs spawn there | mushroom island. mooshrooms | the island, peaceful
x1: lush caves have glow berries and dripleaf | it's cozy down there
x2: hostile mobs don't spawn on mushroom islands | free stew from every mooshroom

# keep_on_death | dying question | death
q: would you rather keep your items when you die or keep your levels? | die and keep stuff, or die and keep levels?
b1: items, obviously | items. levels come back | keep the stuff
b2: levels, i've got thirty saved up | levels. stuff is replaceable | levels, they take forever to get
x1: levels come back fast if you mine | gear took weeks to get
x2: thirty levels is a lot of grinding | you can always make new tools

# proof_house | house safety question | building danger
q: would you rather have a creeper-proof house or a fireproof one? | creeper-proof or fireproof?
b1: creeper-proof, they're everywhere | creepers. by far | creeper-proof, easy
b2: fireproof, lightning sets things on fire | fireproof. my house is all wood | fire, i'm clumsy with lava
x1: creepers are the number one house wrecker | fire you can stop, creepers you can't
x2: one lightning strike and a wood house is gone | build in stone and creepers barely matter
"""

# Quick ones: b's pick (and why) comes as the question's answer.
QWYR = """
# shoulder_pet | shoulder pet question | pets animals
q: would you rather have a cat or a parrot on your shoulder? | shoulder cat or shoulder parrot?
ans: parrot, obviously, it rides there | cat, it'd keep creepers off | parrot, it'd dance to music | cat. parrots fly off every time you jump

# tall_small | size question | random
q: would you rather be really tall or really small? | giant you or tiny you?
ans: tall, i'd reach everything | small, i'd fit through anything | small. one block gaps! | tall, i'd see over every hill

# cake_blocks | cake blocks question | food jokes
q: would you rather every block tasted like cake or every food tasted like dirt? | blocks taste like cake or food tastes like dirt?
ans: blocks like cake, obviously | the cake one, i'd eat my walls | uh, cake blocks | is that even a choice
c: i'd eat my whole house in a week

# wake_up | waking up question | structures
q: would you rather wake up in a desert temple or an igloo? | desert temple or igloo, where do you wake up?
ans: igloo, cozy | temple, but i'm not stepping on that plate | igloo. there's a basement sometimes | temple, loot!

# talking_gear | talking gear question | tools jokes
q: would you rather your pickaxe sang or your sword told jokes? | singing pick or joking sword?
ans: singing pickaxe, mining music | joke sword, fights would be funnier | the sword, i need jokes | pickaxe, it'd sing about diamonds

# sunrise_sunset | sky question | sky
q: would you rather it was always sunrise or always sunset? | forever sunrise or forever sunset?
ans: sunrise, new day feeling | sunset, so pretty | sunset. cozy forever | sunrise, zombies burning forever

# with_map | map question | exploring
q: would you rather explore with a map or without one? | map or no map when you explore?
ans: without, more fun | with, i get lost | without, maps are cheating lol | with a map, always

# apple_cake | snack question | food
q: would you rather eat a golden apple or a whole cake right now? | golden apple or cake, right now?
ans: golden apple, it's gold | cake, i'm hungry for real | the apple, extra hearts | cake. you can share a cake

# house_smell | house smell question | home
q: would you rather your house smelled like bread or like the forest? | bread smell or forest smell at home?
ans: bread, obviously | forest, fresh air | bread. always bread | forest, pine and dirt

# tiny_huge | house size question | home building
q: would you rather have a tiny house with everything or a huge empty one? | tiny and full or huge and empty?
ans: tiny with everything | huge and empty, i'll fill it | the tiny one, cozy | huge. room to grow

# vision_water | potion question | potions
q: would you rather have night vision or water breathing for a day? | night vision or water breathing for a day?
ans: night vision, caves! | water breathing, ocean monument | night vision, easy | water, i'd find a shipwreck

# rain_snow_fight | weather fight question | weather fighting
q: would you rather fight in the rain or in the snow? | rain fight or snow fight?
ans: rain, at least it's warm | snow, snowballs as backup | rain. snow makes me slow | snow, it's pretty

# bells | bell question | town
q: would you rather have a bell that calls your friends or one that scares mobs? | friend bell or mob scaring bell?
ans: calls friends | scares mobs, obviously | friends. it gets lonely | mob scaring bell, safety first

# cook_fisher | skill question | food fishing
q: would you rather be a really good cook or a really good fisher? | great cook or great fisher?
ans: cook, every meal amazing | fisher, relaxing | cook. stew for days | fisher, i'd catch treasure

# garden_pond | garden question | home plants
q: would you rather have a garden or a pond? | garden or pond out back?
ans: garden, flowers | pond, fish and frogs | garden, carrots | pond with lily pads

# trapdoors | odd house question | building jokes
q: would you rather every door was a trapdoor or every window was just a hole? | trapdoor doors or hole windows?
ans: trapdoors, i'd crawl lol | holes, open air | trapdoor doors, weird but fine | holes. windows are overrated

# night_in | spooky night question | structures night
q: would you rather spend a night in a witch hut or a woodland mansion? | witch hut or mansion for a night?
ans: witch hut, it's tiny | mansion, there's loot | the hut, the mansion has evokers | mansion, beds

# hay_wool | bed question | sleep
q: would you rather sleep on a hay bale or a pile of wool? | hay or wool to sleep on?
ans: wool, it's soft | hay, rustic | wool. it's a bed already | hay bale, horses love it

# turtle_rabbit | small pet question | pets animals
q: would you rather have a pet turtle or a pet rabbit? | turtle or rabbit?
ans: turtle, slow and steady | rabbit, fluffy | turtle, scutes for a helmet | rabbit, they hop around

# fears | fear question | feelings
q: would you rather be scared of the dark or scared of heights? | afraid of the dark or afraid of heights?
ans: heights, i live underground | dark, i live on mountains | heights. i'd just stay low | dark, torches everywhere then

# food_shelter | rough night question | food night
q: would you rather go a day without food or a night without shelter? | no food for a day or no shelter for a night?
ans: no food, i'll manage | no shelter, i'd fight all night | food. nights are dangerous | shelter, i've done it before

# pink_world | pink question | jokes
q: would you rather all mobs were pink or all blocks were pink? | pink mobs or pink blocks?
ans: mobs, funny | blocks, pretty | mobs. pink creepers lol | blocks, a pink world

# boat_upgrade | boat question | ocean
q: would you rather your boat was twice as fast or twice as big? | fast boat or big boat?
ans: twice as fast | twice as big, bring friends | fast. speed is everything | big, more chests

# saddle_tag | loot question | animals
q: would you rather find a saddle or a name tag? | saddle or name tag?
ans: saddle, horses | name tag, i'd name my dog | saddle, obviously | name tag. naming is everything

# talk_to | talking things question | jokes
q: would you rather be able to talk to your pickaxe or your bed? | chat with your pick or your bed?
ans: pickaxe, it's seen things | bed, it knows my dreams | the pick, mining stories | bed, lol

# pockets | pockets question | storage
q: would you rather have double pocket space or a backpack you have to wear? | bigger pockets or a backpack?
ans: double pockets | backpack, i'd decorate it | pockets, simple | backpack. bigger is better

# cleanup | cleanup question | building
q: would you rather clean up after a creeper or after a flood? | creeper mess or flood mess?
ans: creeper, it's one crater | flood, water's easy to clear | creeper hole, fill it in | flood. creepers break stuff

# fields | fields question | plants farming
q: would you rather have a sunflower field or a pumpkin patch? | sunflowers or pumpkins?
ans: sunflowers, they face the sunrise | pumpkin patch, pies | sunflowers, pretty | pumpkins, carved ones at night

# crossing | crossing question | ocean biomes
q: would you rather sail across the ocean or walk across the desert? | ocean or desert crossing?
ans: ocean, fishing on the way | desert, temples | sail. no husks | desert. drowned are worse

# secret | secret question | home building
q: would you rather your house had a secret door or a secret room? | secret door or secret room?
ans: secret door | secret room | a room, for my stash | the door, it leads somewhere

# meal_spot | meal question | food
q: would you rather have breakfast in bed or dinner on a mountain? | breakfast in bed or dinner on a peak?
ans: breakfast in bed | dinner on a mountain | mountain, the view | bed, cozy

# names | naming question | pets tools
q: would you rather name all your tools or all your animals? | named tools or named animals?
ans: animals, obviously | tools, my pick deserves one | animals. a cow named bob | tools, they work harder

# cozy_sound | cozy sound question | music
q: would you rather listen to rain on a roof or a crackling campfire? | rain on the roof or a campfire?
ans: rain on a roof | campfire | rain, so relaxing | campfire, cozy

# jungle_swamp | lost biome question | biomes
q: would you rather be lost in the jungle or lost in the swamp? | jungle or swamp, lost?
ans: jungle, food everywhere | swamp, slimes are funny | jungle, melons | swamp. jungles are a maze

# bees_chickens | farm animal question | animals farming
q: would you rather have a beehive or a chicken coop? | bees or chickens?
ans: beehive, honey | chicken coop, eggs | bees, they help crops | chickens, feathers for arrows

# shiny | shiny gear question | gear
q: would you rather your sword glowed or your armor sparkled? | glowing sword or sparkly armor?
ans: glowing sword | sparkly armor | the sword, so cool | armor, enchanted look

# wake_weather | waking question | weather
q: would you rather wake up to a sunrise or a thunderstorm? | sunrise or thunderstorm to wake up to?
ans: sunrise | thunderstorm, it's exciting | sunrise. calm | storm. dramatic

# fire_window | house question | home decor
q: would you rather have a fireplace or a big window? | fireplace or big window?
ans: fireplace | big window | fireplace, cozy nights | window, the view

# stilts_raft | house question | home ocean
q: would you rather your house was on stilts or on a raft? | stilts or raft house?
ans: stilts | raft | stilts, above floods | raft. float around

# bonk | clumsy question | jokes
q: would you rather never trip on a slab again or never bonk your head on a ceiling again? | slab trips or ceiling bonks?
ans: the ceiling one, i'm always jumping into it | slabs, they trip me | ceiling. every tunnel | slab. hate them
"""

# ---------------------------------------------------------------------------------------------------------------------
# 2. What-ifs. q (b's answer comes with it: ans), a (a's own take), b (b on a's take), c (a's last word).

HYPO = """
# pigs_fly | flying pigs idea | animals jokes
q: what if pigs could fly? | imagine if pigs could fly
ans: porkchops falling from the sky | we'd need roofs on every pig pen | i'd saddle one and never walk again | they'd still be slow, just higher up | phantoms would finally have competition
a: i'd build a pig airport lol | the carrot on a stick would be so much harder | i'd tie one to a fence and fly it like a kite
b: pig kite lol | you'd fall off so fast
c: when pigs fly, i guess

# add_mob | new mob idea | mobs
q: if you could add one mob, what would it be? | what mob would you add if you could add one?
ans: a mole that digs tunnels for you | a cave owl that hoots when a creeper's close | a mob that sorts your chests | a giant turtle you can ride | something that eats gravel
a: mine's a little firefly that lights up caves | i'd add a mule that follows you without a lead | a mob that picks up your stuff when you die
b: that'd save so many lost items | fireflies in caves, cute

# diamonds_common | common diamonds idea | diamonds ore
q: if diamonds were as common as coal, what would be rare? | what if diamonds were everywhere, like coal?
ans: coal would be rare then lol | probably dirt somehow | sticks. i'd be broke on sticks | nothing, everyone would just have diamond everything | iron, maybe
a: diamond shovels would finally be normal | i'd build a diamond house just because | diamond hoes for everyone
b: a diamond house is so tacky | a diamond hoe, the real luxury
c: we'd all be rich and bored

# live_structure | dream structure | structures home
q: if you could live in any structure, which one? | which structure would you move into?
ans: a woodland mansion, so many rooms | an ocean monument, minus the guardians | the stronghold library | an igloo, cozy | a desert temple, cool in the day
a: i'd take a jungle temple and fix it up | a big ruined portal, just for the look | an ancient city if the warden moved out
b: the warden's never moving out | fixing up a temple, i like that

# one_tool | one tool idea | tools
q: if you could only use one tool forever, which one? | one tool for the rest of your life, what is it?
ans: pickaxe, obviously | axe, it's a weapon too | shovel, i'd just dig | shears, for style | a fishing rod, i'd never leave the water
a: pickaxe for me, it does most stuff | axe, it chops and it fights | i'd take a hoe just to be weird
b: the axe is a good shout | a hoe? brave

# tame_any | taming anything idea | mobs pets
q: if you could tame any mob, what would it be? | what mob would you tame if you could tame anything?
ans: a ghast, flying fireball dog | an iron golem, my own bodyguard | a creeper, if it never blew up | a phantom, ride it at night | an enderman, block carrier
a: i'd tame a warden and nobody would bother me again | a ravager, ride it into town | a polar bear, the biggest cuddle
b: a ravager in town would wreck everything lol | a tame warden is terrifying

# creepers_friendly | friendly creepers idea | mobs
q: what if creepers were friendly? | imagine creepers just wanted hugs
ans: they'd still blow up when they hug you | i'd keep one as a pet | they'd stand around looking sad | i'd still run from them | nobody would believe it
a: i'd still flinch at every hiss | i'd build them a little house | they'd follow you around like cats
b: a creeper house, lol | the hiss would still get me

# nether_water | water in the nether idea | nether
q: what if water worked in the nether? | imagine you could place water in the nether
ans: nether swimming pools | obsidian everywhere | you could make cobble anywhere | lava lakes would turn to obsidian | it'd get steamy lol
a: i'd turn a whole lava lake into obsidian | piglins would finally get a bath | the nether would be way less scary
b: piglin bath time | that'd ruin the nether's whole thing

# glow_block | glowing block idea | building night
q: if you could make one block glow, which one? | what block would you make glow?
ans: dirt, glowing dirt everywhere | grass, so the fields light up | leaves, glowing trees | stone, caves would be lit | sand, glowing beaches
a: i'd pick leaves, imagine the forests at night | glowing stairs, so you never trip | gravel, so you see it falling
b: glowing forests would be so pretty | glowing gravel lol

# villagers_talk | talking villagers idea | villagers
q: what if villagers could actually talk? | imagine villagers saying more than hm
ans: they'd complain about our prices | they'd tell us where the good loot is | they'd gossip about us | they'd just say hm louder | they'd ask us to stop taking their crops
a: i bet they'd yell at us about the iron golems | they'd tell the zombies to go away | they'd probably be really rude
b: honestly fair, we take their carrots | the farmers would be the grumpiest

# change_night | changing the night idea | night
q: if you could change one thing about the night, what would it be? | what would you change about the night?
ans: no more phantoms | make it shorter | brighter moon | no creepers at night | spiders stay friendly
a: i'd make the moon brighter, easier to see | fewer skeletons, that's all i ask | nights should be half as long
b: brighter moon, yes | half as long sounds perfect

# infinite_block | infinite block idea | building
q: if you could have infinite of one block, which? | infinite of any block, what do you pick?
ans: glass | stone bricks | oak logs | diamond blocks, obviously | glowstone
a: i'd go with deepslate tiles, they're so nice | quartz, i'd build a palace | moss, everything would be green
b: a quartz palace would be blinding | moss everything, cozy

# breathe_water | water breathing idea | ocean
q: if you could breathe underwater forever, what would you do first? | water breathing forever, where are you going first?
ans: ocean monument, obviously | shipwrecks, all of them | build an underwater house | find every buried treasure | swim with the dolphins
a: i'd build a glass dome at the bottom of the ocean | i'd go find the deepest part of the ocean | i'd start a kelp farm down there
b: an underwater dome, so cool | the drowned would be your neighbours

# mobs_chests | mobs opening chests idea | mobs storage
q: what if mobs could open chests? | imagine zombies could open your chests
ans: i'd lock everything | zombies stealing my bread | i'd hide my diamonds in a hole | skeletons taking my arrows back | creepers wouldn't care
a: i'd booby-trap every chest | i'd keep everything in barrels, they wouldn't know | i'd just carry everything always
b: zombies don't know about barrels lol | trapped chests everywhere

# be_a_mob | being a mob idea | mobs animals
q: if you were a mob, which one would you be? | what mob are you, honestly?
ans: a cat, sleep all day | a fox, sneaky | an enderman, tall and mysterious | a bee, busy busy | a sheep, chill
a: i'd be a goat, headbutt everyone | definitely a panda, lazy panda | a parrot, i never stop talking
b: lol the parrot fits | a lazy panda, yeah that's you

# undo_death | undoing a death idea | death memories
q: if you could undo one death, which one? | if you could take back one death, which would it be?
ans: the lava one, lost everything | the creeper behind the door | falling off my own roof | the time i drowned in a cave | my first night, honestly
a: mine's the one where i fell in a ravine with all my iron | the gravel one, i was so close to the surface | the one right in front of my own house, so dumb
b: in front of your house is the worst | ravines are evil

# xray | seeing ore idea | mining ore
q: what if you could see ores through walls? | imagine seeing every ore through the stone
ans: that's cheating lol | it'd ruin mining | i'd find every diamond in a day | i'd get bored so fast | it'd be like the game playing itself
a: honestly i'd turn it off, finding stuff is the fun | i'd use it once and feel guilty | mining would just be walking to dots
b: same, the surprise is the whole thing | yeah no fun in that

# fly_free | flying idea | sky exploring
q: if you could fly without an elytra, where would you go first? | flying, no elytra, first stop?
ans: the top of the tallest mountain | out over the ocean | straight to a village i saw once | as high as the clouds | right above my house to see it
a: i'd fly around my town at sunset | i'd go see how far the clouds go | i'd follow a river all the way
b: a sunset flight, nice | the clouds are just flat, i checked

# talk_animal | talking to animals idea | animals
q: if you could talk to one animal, which one first? | which animal would you talk to first?
ans: my dog, ask if he's happy | a cow, ask what grass tastes like | a fox, why do you steal stuff | a cat, why do you sit on my chest | a parrot, it'd answer back anyway
a: i'd ask a llama why it spits | i'd ask a sheep how it feels about shearing | i'd ask a bee what flowers taste like
b: the llama would just spit at you | the sheep probably hates it

# sun_never_sets | endless day idea | day sky
q: what if the sun never set? | imagine it was always daytime
ans: no more mobs at night | i'd never sleep | i'd get so much done | i'd miss the stars | zombies would only live in caves
a: i'd miss the sunsets though | caves would be the only scary place left | no phantoms ever, that's the dream
b: the sunsets though | caves would get crowded

# rain_forever | endless rain idea | weather
q: what if it rained forever? | imagine it never stopped raining
ans: crops would love it | everyone would fish all day | i'd go mad | i'd build a huge roof over town | zombies would stop burning in the day
a: i'd learn to love the sound | i'd build everything with roofs | we'd all be soggy forever
b: soggy forever lol | the sound is nice though

# trees_instant | instant trees idea | trees
q: what if trees grew the second you planted them? | imagine saplings grew instantly
ans: wood would be free | i'd build a forest in a day | oops, a tree in my house | i'd plant one under someone's floor lol | no more waiting, yes
a: i'd plant a whole forest around my house | i'd make a tree maze | i'd accidentally grow one in my face
b: tree maze lol | that's a prank waiting to happen

# pick_respawn | respawn spot idea | death home
q: if you could respawn anywhere you wanted, where? | where would your respawn spot be, anywhere at all?
ans: right next to my chests | on a mountain top | in the middle of town | next to the last place i died | in my bed, obviously
a: next to where i died, so i can get my stuff back | a little hut by the ocean | right in front of a furnace with food in it
b: next to where you died is smart | food first, yes

# new_dimension | new dimension idea | exploring dreams
q: what if you found a portal to a brand new dimension? | imagine there was a dimension nobody's seen yet
ans: i'd jump in right away | i'd bring everything i own | i'd send someone else first lol | i'd build a base on the other side | i'd be scared, honestly
a: i'd want a dimension made of clouds | i bet it'd be all water | i'd hope it's just one giant forest
b: a cloud dimension would be pretty | an all water one sounds awful

# build_anything | infinite blocks idea | building dreams
q: if you had infinite blocks, what would you build? | infinite blocks, what's the build?
ans: a castle the size of a mountain | a bridge across the ocean | a giant statue of myself | a city for everyone | a tower to the clouds
a: i'd build a road around the whole world | a huge dragon made of blocks | a copy of my town, but floating
b: a road around the world, respect | a floating town would be so cool

# huge_chickens | huge chickens idea | animals jokes
q: what if chickens were the size of horses? | imagine horse-sized chickens
ans: huge eggs | i'd ride one | they'd peck you to bits | one egg feeds a town | they'd still be scared of everything
a: i'd build a chicken stable | i'd want to see one flap down a cliff | the feathers would make the best pillows
b: chicken stable lol | that's a lot of chicken

# nice_endermen | nice endermen idea | mobs
q: what if endermen were friendly? | imagine endermen were nice
ans: they'd help us build | they'd bring us blocks | they'd still teleport into my house | i'd finally look at one | they'd be the best neighbours
a: i'd hire one to move my whole house | they'd just stand in town looking tall | i'd ask one to carry my dirt
b: hiring an enderman, lol | finally, eye contact

# walking_house | walking house idea | home jokes
q: what if your house could walk? | imagine your house had legs
ans: i'd take it to the beach | it'd walk away from creepers | i'd park it by the ocean | it'd get lost | i'd let it go wherever it wanted
a: mine would walk right into a lake | i'd make it follow me when i mine | i'd move it closer to the iron
b: a house that follows you, the dream | into a lake lol

# lava_ocean | lava ocean idea | ocean danger
q: what if the ocean was lava? | imagine all the oceans were lava
ans: nobody would ever boat again | it'd be so bright at night | i'd stay inland | striders would be everywhere | the drowned would be toast
a: the whole world would glow at night | fishing would be over | beaches would be terrifying
b: no fishing would be the worst part | a glowing world sounds pretty though

# cave_maps | cave maps idea | caves exploring
q: what if every cave came with a map at the entrance? | imagine caves had maps
ans: i'd never get lost again | it'd take the fun out | who'd draw them though | i'd still get lost lol | i'd go straight to the diamonds
a: i'd love that, i always get lost | someone would draw fake ones as a prank | i'd add little notes like here be creepers
b: here be creepers, lol | fake maps would be evil

# pet_dragon | pet dragon idea | pets dreams
q: what if you had a pet dragon? | imagine your own pet dragon
ans: i'd fly it everywhere | it'd eat all my cows | i'd name it something tiny | it'd burn my house down | i'd teach it to fetch
a: i'd call it nugget | i'd keep it far from town so it doesn't wreck stuff | i'd ride it to the mountains and back
b: nugget lol | it'd eat your whole farm

# bees_other | odd bees idea | animals
q: what if bees made something other than honey? | imagine bees made something else
ans: lava, terrifying | redstone, free wiring | slime, bouncy hives | milk, somehow | sugar, straight sugar
a: bees that make glowstone, glowing hives | bees that make wool, fluffy | bees that make cake, done
b: cake bees, sign me up | glowing hives sound pretty

# hire_villager | hiring a villager idea | villagers
q: if you could hire a villager for anything, what would it be? | what job would you give a villager?
ans: sorting my chests | farming for me | standing guard, lol | cooking | telling me when it's night
a: i'd hire one to say hm when creepers are near | one to carry my stuff | a librarian to label my chests
b: the hm alarm lol | a chest labeller would be useful

# enchant_self | enchanting yourself idea | enchanting
q: if you could put one enchantment on yourself, which one? | one enchantment on you, not your gear?
ans: feather falling, no more fall deaths | mending, heal while i mine | protection | depth strider, swim fast | efficiency, do everything faster
a: i'd take unbreaking, lol | fire protection, i keep falling in lava | silk touch, so i pick everything up gently
b: silk touch hands, lol | unbreaking you, smart

# long_night | long night idea | night
q: what if nights lasted twice as long? | imagine the nights were double length
ans: i'd sleep more | more mobs, more drops | i'd go mad | i'd make a lot more torches | the moon would get more time
a: i'd spend it mining, safe underground | i'd finally learn to like the dark | i'd get two nights of phantoms
b: mining all night, classic | double phantoms, no

# one_block_build | one block idea | building
q: if you could only build with one block, which one? | one block for every build forever?
ans: stone bricks | oak planks | quartz | deepslate | white concrete
a: i'd pick spruce, warm and dark | sandstone, it does everything | cobble, i'm humble
b: cobble, humble indeed lol | spruce is a good one

# gravity_all | falling blocks idea | building
q: what if every block fell like sand? | imagine all blocks had gravity
ans: no more floating trees lol | every build would need pillars | caves would collapse | bridges would be impossible | pillaring up would still work, kinda
a: my roof would be on my floor | bridges would be over forever | the floating islands would finally fall
b: your roof on your floor, lol | no more bridges, sad

# water_spreads | endless water idea | ocean danger
q: what if water kept spreading forever? | imagine water never stopped flowing
ans: the whole world would flood | i'd live on a mountain | no more caves | boats everywhere | i'd never place water again
a: one bucket and the world drowns | i'd have to build everything on stilts | caves would just be lakes
b: one bucket, world gone | stilts everywhere

# shrink | shrinking idea | jokes
q: if you could shrink to the size of a silverfish, what would you do? | tiny like a silverfish, what's the plan?
ans: hide in a flower pot | sneak through cracks in walls | ride a chicken | live in a chest | scare someone from under their bed
a: i'd live in a flower pot | i'd ride a bee | i'd spy on the villagers
b: riding a bee, so cute | flower pot house lol

# wake_villager | waking up a villager idea | villagers jokes
q: what if you woke up as a villager? | imagine waking up as a villager
ans: i'd raise my prices | i'd run from zombies | i'd sleep all day | i'd finally get a job | hm
a: i'd make every trade one emerald | i'd hide in my house when it rains | i'd say hm at everyone
b: one emerald trades, a hero | hm indeed

# pick_talks | talking pickaxe idea | tools jokes
q: what if your pickaxe could talk? | imagine your pickaxe had a voice
ans: it'd complain all day | it'd yell when it sees ore | it'd beg me to fix it | it'd have stories | it'd scream at lava
a: mine would just say again? every swing | mine would cheer when i find diamonds | mine would cry when it breaks
b: again? again? lol | a cheering pick, i want that

# mob_names | named mobs idea | mobs jokes
q: what if every mob had a name? | imagine every zombie had a name
ans: i'd feel bad hitting them | i'd learn the names of the regulars | dave the zombie | i'd write their names down | i'd name the creeper that blew up my house
a: there'd be one zombie i'd recognise every night | i'd feel bad hitting a skeleton called tim | creepers would all be called trouble
b: tim the skeleton, rip | the regulars, lol

# beds_no_skip | sleepless beds idea | sleep night
q: what if beds didn't skip the night? | imagine sleeping didn't skip the night
ans: we'd all be up all night | i'd still sleep, i'm tired | i'd mine instead | i'd stay in bed and listen to the zombies | i'd craft all night
a: i'd stay inside and sort chests all night | i'd learn the stars by heart | i'd hate it
b: sorting chests all night, same | the stars would be nice

# bottomless | bottomless pockets idea | storage
q: what if you could carry infinite stuff? | imagine bottomless pockets
ans: i'd never go home | i'd carry my whole house | i'd never sort again | i'd lose everything in there | i'd pick up every block
a: i'd carry every chest i own around | i'd never find anything | i'd carry a full furnace setup
b: you'd never find anything, true | carrying your whole house lol

# choose_weather | choosing the weather idea | weather
q: if you could pick the weather every day, what would you pick? | if you controlled the weather, what's today?
ans: sunny, always | light rain, cozy | thunder, for the drama | snow, everywhere | clear nights, starry
a: i'd do sunny days and rainy nights | i'd make it snow everywhere once | i'd never pick thunder again
b: rainy nights, cozy | snow everywhere would be fun

# rain_mob | rain mob idea | weather mobs
q: what if there was a mob that only came out in the rain? | imagine a rain-only mob
ans: a huge frog | a little ghost cloud | something that splashes you | a soggy skeleton | a snail that leaves slime
a: i'd want a little rain sprite that waters crops | a big slow snail would be cute | probably something that floods your house
b: a rain sprite sounds nice | a snail mob would be adorable

# snow_anywhere | snow anywhere idea | weather biomes
q: if you could make it snow anywhere, where? | where would you make it snow?
ans: in the desert, lol | the jungle | my house only | the nether, imagine | the ocean
a: i'd make it snow in the desert, just to see | i'd snow on my town every night | i'd make it snow on a mushroom island
b: a snowy desert, cursed | snowy town at night, pretty

# stone_rare | rare stone idea | building mining
q: what if stone was as rare as gold? | imagine stone was super rare
ans: we'd all live in wood houses | cobble would be money | i'd be rich, i have chests of it | mining would be way different | dirt houses everywhere
a: i'd be the richest person around with my cobble | i'd guard my cobble chest with my life | we'd use stone as money
b: you'd be rich, you hoard cobble | stone money, lol

# no_wood | no wood idea | trees crafting
q: what if wood didn't exist? | imagine a world with no trees
ans: how would you even start | crafting tables from what | everything would be stone | i'd punch dirt forever | we'd be stuck
a: no crafting table, no nothing | i'd just sit there punching grass | sticks would be worth gold
b: no crafting table is rough | punching grass forever

# torch_bait | backwards torches idea | mobs night
q: what if torches attracted mobs instead of keeping them away? | imagine torches pulled mobs in
ans: i'd live in the dark | every house would be a trap | i'd use them as mob bait | i'd build a torch farm | caves would be safer than towns
a: i'd use torches as mob traps | everyone would live in the dark | i'd put a torch on a far hill as bait
b: torch bait, smart | living in the dark, cozy?

# live_end | living in the end idea | end
q: what if you had to live in the end? | imagine living in the end forever
ans: i'd farm chorus fruit | i'd build with purpur | i'd make friends with the shulkers | i'd go mad | i'd never look anyone in the eye
a: i'd build a purple castle | i'd grow chorus plants everywhere | i'd keep a pumpkin on forever
b: pumpkin forever, smart | a purple castle, nice

# three_lives | three lives idea | death
q: what if we only had three lives? | imagine you only got three respawns
ans: i'd be so careful | i'd never go in caves | i'd stay home forever | i'd use them all in a week | i'd keep a count on my wall
a: i'd never touch lava again | i'd stop doing dumb jumps | i'd probably waste one the first night
b: one the first night, classic | no dumb jumps, sure

# pause_world | pausing the world idea | random
q: if you could pause the world, what would you do? | stop time for an hour, what do you do?
ans: sort all my chests | finish my build | sleep | look at the sunset longer | mine in peace
a: i'd walk around town and look at everyone's builds | i'd sort every chest i own | i'd just sit and watch the sunset
b: sorting chests, so you | watching the sunset, nice

# swap_houses | swapping houses idea | home
q: if you could swap houses with anyone, would you? | whose house would you swap for?
ans: no, i love mine | yes, anyone with a bigger one | someone with a view | the one by the river | a big one in town
a: i'd keep mine, it's got my chests | i'd swap for one with a balcony | i'd swap for anything with a fireplace
b: a balcony would be nice | keeping yours, loyal

# run_town | running the town idea | town
q: if you ran the town, what's the first rule? | first rule if you were in charge of the town?
ans: no pillars left standing | torches everywhere | free bread for everyone | no creepers allowed lol | everyone helps at harvest
a: mine would be clean up your dirt towers | everyone gets a bed, no matter what | no chest snooping
b: clean up your dirt towers, yes | no chest snooping, good rule

# new_food | new food idea | food
q: if you could make a new food, what would it be? | invent a food, go
ans: carrot cake | honey bread | melon pie | fish stew | a sandwich, bread and steak
a: i'd make glow berry jam | i'd make mushroom pie | fried eggs, finally a use for eggs
b: fried eggs, yes please | glow berry jam sounds fancy

# combine_mobs | mixed mobs idea | mobs animals
q: if you could combine two mobs, which two? | mix two mobs together, which?
ans: a cat and a creeper, a catreeper | a bee and a chicken | a sheep and a slime | a wolf and an iron golem | an enderman and a parrot
a: i'd mix a cow and a pig, the ultimate food | a spider and a horse, a wall climbing mount | a fox and a cat, chaos
b: a wall climbing horse, wow | cow pig, efficient

# village_walls | walled villages idea | villagers town
q: what if every village came with walls? | imagine villages already had walls
ans: zombies would finally stay out | raids would be easier | i'd move in right away | villagers would still wander out | it'd be like a castle
a: i'd add a moat too | the villagers would still leave the gate open | i'd finally trust a village at night
b: they'd leave the gate open, lol | a moat, nice

# diamond_map | diamond map idea | diamonds mining
q: what if you had a map of every diamond? | imagine a map that marks every diamond
ans: that's cheating | i'd burn it | i'd use it once | mining would be boring | i'd give it away
a: i'd throw it in lava, it'd ruin everything | finding them is the fun part | maybe one peek. one.
b: one peek, sure | lava, good call

# sword_shape | dream sword idea | weapons jokes
q: if your sword could be any shape, what would it be? | design your dream sword
ans: a giant fish | a carrot | a big curved one | a pickaxe sword | a sword made of glass
a: mine would be a big fish, slap the mobs | a sword with a torch on the end | a sword that's also a fishing rod
b: a fish sword, lol | a torch sword, useful

# moon_fell | falling moon idea | sky night
q: what if the moon fell? | imagine the moon just dropped out of the sky
ans: the night would be pitch black | i'd go find it | it'd make a huge crater | i'd mine it | slimes would get confused
a: i'd go mine the moon for moon rocks | slimes would lose track of the moon | the nights would be so dark
b: moon rocks, lol | slimes and the moon, yeah

# rainbow_sheep | rainbow sheep idea | animals decor
q: what if sheep came in every colour on their own? | imagine wild rainbow sheep everywhere
ans: no more dye | the hills would look like a painting | i'd shear them all | i'd make the brightest rug ever | it'd be so pretty
a: i'd build a rainbow house | i'd herd them by colour | i'd never dye wool again
b: herding by colour, so you | a rainbow house would be loud

# ride_any | riding mobs idea | mobs animals
q: if you could ride any mob, which one? | what mob would you ride?
ans: a ghast | a spider, up walls | an enderman | a polar bear | a giant slime
a: i'd ride a phantom across the sky at night | a cow, slow and steady | a dolphin, ocean trips
b: dolphin rides, yes | a phantom ride would be cool

# creepers_diamonds | diamond creepers idea | mobs diamonds
q: what if creepers dropped diamonds? | imagine creepers dropped diamonds
ans: i'd hunt them every night | everyone would want them near | still not worth the craters | i'd finally like them | i'd build a creeper farm
a: i'd stand next to every creeper i see | the craters would be worth it | i'd feel bad, but i'd do it
b: craters for diamonds, fair trade | standing next to creepers, bold

# one_block_island | one block island idea | ocean
q: what if you had to live on a one-block island? | imagine life on a single block
ans: i'd build up | i'd fish forever | i'd go mad | i'd make friends with a dolphin | i'd swim away
a: i'd build a tower straight up | i'd fish until i had enough for a boat | i'd become a pro fisher
b: a tower on one block, brave | fish your way out

# empty_village | empty village idea | villagers
q: what if you found a village with nobody in it? | imagine finding an empty village
ans: creepy | free houses | i'd move in | i'd wonder where everyone went | i'd check for zombies first
a: i'd take the biggest house and wait | i'd fill it with my own friends | i'd be scared they come back at night
b: wait, that's actually creepy | free houses though

# animals_build | building animals idea | animals building
q: what if animals could build? | imagine cows building houses
ans: cows would build barns | foxes would build dens | bees already do lol | sheep would build with wool | they'd build better than me
a: pigs would build mud houses | wolves would build forts | rabbits would dig tunnels everywhere
b: a wolf fort, scary | bees already build, true

# dragon_back | returning dragon idea | end
q: what if the dragon came back by itself? | imagine the dragon respawned on its own
ans: i'd never go back | free xp | i'd be ready | i'd let someone else handle it | i'd build a bunker
a: i'd keep a bow by my bed just in case | i'd fight it every time, free xp | i'd hide on my island
b: free xp, sure | a bow by the bed, smart

# tiny_ghasts | tiny ghasts idea | nether mobs
q: what if ghasts were tiny? | imagine ghasts the size of a chicken
ans: cute | they'd still scream | tiny fireballs | i'd keep one in a jar | they'd be less scary
a: i'd keep a tiny ghast in a glass case | tiny screams would be hilarious | they'd still be annoying
b: a pet ghast in a jar lol | tiny screams, lol

# trader_anything | trader wish idea | trade
q: what if the wandering trader sold anything you wanted? | imagine the trader had anything you asked for
ans: i'd buy a mansion | i'd buy diamonds | i'd buy a dragon egg | i'd buy his llamas | i'd buy a map home
a: i'd ask for a netherite pickaxe | i'd buy a pet axolotl | i'd buy all his saplings, just in case
b: he'd still charge too much | a netherite pick, smart

# new_look | new look idea | random
q: if you could change how you look, what would you change? | new look, what's it gonna be?
ans: a cape | a hat | new hair | a cooler shirt | glowing eyes
a: i'd get a big hat | i'd get a cape, finally | nothing, i look great
b: nothing, confident lol | a cape is a good call

# world_road | world road idea | exploring building
q: what if there was one road around the whole world? | imagine one giant road around everything
ans: i'd walk it | i'd sell stuff along it | it'd take forever | i'd build a house on it | i'd ride it on a horse
a: i'd put an inn every few hundred blocks | i'd never stop walking | i'd put signs along the whole thing
b: an inn on the road, cozy | signs everywhere

# block_sounds | block sounds idea | music
q: what if every block made a different sound when you stepped on it? | imagine a different sound for every block
ans: it'd be so loud | i'd make a song with my floor | sneaking would be useless | my house would be a piano | caves would be chaos
a: i'd make my floor play a tune | caves would be so noisy | i'd put wool everywhere, nice and quiet
b: a floor tune, nice | wool floors, smart

# boats_only | boats only idea | ocean exploring
q: what if you could only travel by boat? | imagine boats were the only way to travel
ans: rivers would be roads | i'd boat over land, slowly | i'd live on the coast | i'd dig canals everywhere | i'd never visit the mountains
a: i'd build canals all over town | i'd love it, boats are calm | i'd boat on ice, fastest thing ever
b: canals, so pretty | ice boats, yeah

# longer_days | longer days idea | day
q: if you could make the days longer, would you? | longer days, yes or no?
ans: yes, more time to build | no, i like the nights | yes, double length | no, i'd get tired | maybe just a little
a: i'd add a bit for building | longer days would make me lazy | i'd want longer sunsets more than days
b: longer sunsets, nice | lazy, lol

# mob_gone | removing a mob idea | mobs
q: if you could make one mob disappear forever, which? | one mob gone forever, pick
ans: phantoms | creepers | baby zombies | silverfish | endermites
a: silverfish, they're the worst | creepers, for my house's sake | phantoms, no question
b: silverfish, agreed | phantoms, easy pick

# new_tool | new tool idea | tools crafting
q: if you could invent a new tool, what would it be? | design a tool that doesn't exist
ans: a torch thrower | a block grabber | a tool that sorts | a ladder you can carry | a lava detector
a: i'd make a pickaxe that places torches | a shovel that digs three wide | a bucket that holds everything
b: a torch pick, genius | a bucket for everything

# give_up_food | giving up a food idea | food
q: if you had to give up one food, which one? | what food could you give up forever?
ans: rotten flesh, easy | beetroot | dried kelp | cookies | pumpkin pie
a: beetroot, nobody will miss it | dried kelp, i've had enough | spider eyes, gladly
b: beetroot, agreed | spider eyes, lol

# emerald_drop | emerald drops idea | mobs trade
q: what if every mob dropped an emerald? | imagine emeralds from every mob
ans: i'd fight every night | villagers would be rich | emeralds would be worthless | i'd buy everything | i'd build an emerald house
a: i'd spend every night hunting | the villagers would raise their prices | i'd finally get mending
b: they'd raise prices, true | hunting every night, brave

# run_market | shop idea | trade town
q: if you ran a shop, what would you sell? | if you had a shop, what's on the shelf?
ans: bread | torches | arrows | cobble | iron
a: i'd sell torches, everyone needs them | i'd sell maps of the area | i'd sell cakes at a good price
b: torches, smart | maps of the area, useful

# safe_lava | safe lava idea | danger
q: what if lava was safe to swim in? | imagine lava didn't hurt
ans: i'd swim in it | lava pools everywhere | i'd still be scared | i'd swim across lava lakes | the nether would be easy
a: i'd build a lava pool | i'd swim across the nether | i'd still not trust it
b: still not trusting it, smart | a lava pool, wild

# snow_golems | useful snow golems idea | mobs
q: what if snow golems were actually useful? | imagine snow golems did something good
ans: they'd guard the town | they'd make snow for us | they'd fight harder | they'd carry stuff | they'd be cuter
a: i'd want them to keep the blazes away | i'd make an army | they'd make ice for my boats
b: a snow golem army lol | ice for boats, smart

# talking_dog | talking dog idea | pets
q: what if your dog could talk? | imagine your wolf could talk
ans: it'd say feed me | it'd tell me about mobs | it'd complain about the cat | it'd ask for bones | it'd say i love you
a: mine would ask why i keep making it sit | mine would beg for steak all day | mine would bark first then talk
b: why do i keep sitting, lol | steak, always

# mobs_respawn | respawning mobs idea | mobs death
q: what if mobs respawned like we do? | imagine mobs came back after dying
ans: the same zombie every night | they'd hold grudges | they'd get smarter | i'd recognise them | nightmare
a: there'd be one creeper that hates me personally | they'd get better at fighting | i'd end up friends with one skeleton
b: friends with a skeleton, lol | one creeper hates you, fair

# keep_everything | keeping stuff idea | death
q: what if you kept everything when you died? | imagine dying didn't lose your stuff
ans: i'd be so reckless | dying would be nothing | i'd jump off everything | lava wouldn't scare me | it'd be too easy
a: i'd do so many dumb jumps | i'd still be scared of lava | it'd take the fear out
b: dumb jumps, yes | the fear's kinda the point

# diamond_tree | diamond tree idea | diamonds trees
q: what if diamonds grew on trees? | imagine a diamond tree
ans: i'd plant a forest | they'd be worth nothing | i'd guard it with my life | i'd chop it carefully | everyone would want my land
a: i'd hide my tree in a cave | i'd build a fence around it | i'd still mine for the fun
b: a cave tree, smart | mining for fun, sure

# sky_island | floating island idea | sky home
q: what if you lived on a floating island? | imagine a house on a floating island
ans: amazing views | how do i get down | i'd need a lot of water buckets | phantoms would visit | i'd build a ladder down
a: i'd build a bridge to the next island | i'd keep a water bucket by the edge | i'd fish off the side
b: fishing off the edge, lol | a bridge, smart

# no_hunger | no hunger idea | food
q: what if nobody ever got hungry? | imagine no hunger at all
ans: no more farms | i'd never cook | i'd miss food | i'd still eat cake | villagers would be bored
a: i'd still eat for fun | farmers would be out of work | i'd miss cooking
b: eating for fun, same | cooking is half the fun

# day_mobs | daytime mobs idea | mobs day
q: what if mobs came out in the day and slept at night? | imagine mobs were day creatures
ans: we'd all be night people | i'd farm at night | sunrise would be scary | i'd sleep in the day | backwards world
a: i'd build at night and hide in the day | the moon would be our sun | sunset would mean safe time
b: a backwards world, weird | the moon as our sun, nice

# invisible_house | invisible house idea | home jokes
q: what if your house was invisible? | imagine an invisible house
ans: nobody would find it | i'd bump into it | i'd lose it | i'd love it | it'd be so weird inside
a: i'd forget where it is | i'd walk into my own wall | creepers would never find it
b: you'd walk into it every day | creepers wouldn't find it, nice

# flat_world | flat world idea | biomes
q: what if the whole world was flat? | imagine no hills anywhere
ans: easy building | boring | i'd miss mountains | you'd see forever | travel would be so fast
a: i'd miss the mountains | building would be so easy | you'd see villages from miles away
b: miles away, true | building would be nice

# world_edge | edge of the world idea | exploring
q: if you could walk to the edge of the world, would you? | would you walk to the end of the world?
ans: yes, right now | no, too far | maybe, with friends | i'd get lost | yes, with a lot of food
a: i'd want to see what's out there | i'd turn back halfway | i'd bring a bed and a lot of bread
b: a lot of bread, yes | halfway, lol

# diamond_song | diamond music idea | music diamonds
q: what if music played every time you found diamonds? | imagine a song every time you found diamonds
ans: i'd love it | i'd hear it in my dreams | i'd hum along | i'd mine forever | it'd be loud
a: i'd dance every time | i'd mine just to hear it | i'd get it stuck in my head
b: mining for the song, lol | dancing in a cave, nice

# cloud_walk | cloud walking idea | sky dreams
q: what if you could walk on clouds? | imagine standing on the clouds
ans: i'd build up there | i'd sleep on one | i'd fall through | i'd race across them | phantoms would get me
a: i'd build a cloud house | i'd jump from cloud to cloud | i'd watch the sunrise from up there
b: a cloud house, dreamy | sunrise from the clouds, nice

# cave_sun | underground sun idea | caves
q: what if there was a sun underground? | imagine caves had their own little sun
ans: no more torches | caves would have grass | i'd live down there | mobs would burn in caves too | so cozy
a: i'd move underground for good | caves would have little forests | i'd farm down there
b: cave forests, pretty | underground farms, smart

# first_day | first day again idea | memories
q: if you could live your first day again, would you? | first day again, would you do it?
ans: yes, i'd do it better | no, i'd mess it up again | yes, i miss it | no, the first night was scary | maybe
a: i'd skip the dirt hut and build a real house | i'd find iron faster | i'd sleep the first night instead of digging
b: the dirt hut is a rite of passage | iron faster, smart

# big_mobs | big mobs idea | mobs
q: what if all mobs were twice as big? | imagine every mob doubled in size
ans: terrifying | spiders would be huge | baby zombies would just be zombies | slimes would be enormous | i'd stay inside
a: spiders twice as big, no thanks | creepers would make huge craters | chickens would be fine, still harmless
b: huge spiders, no | double craters

# small_mobs | small mobs idea | mobs jokes
q: what if all mobs were tiny? | imagine every mob was tiny
ans: so cute | they'd be hard to hit | i'd step on them | tiny creepers, tiny holes | i'd keep them as pets
a: tiny creepers would be adorable | tiny skeletons with tiny bows | i'd lose them in the grass
b: tiny bows, cute | tiny holes, fine

# answer_book | book of answers idea | philosophy
q: if you had a book that answered any question, what would you ask? | one question to a book that knows everything?
ans: where the nearest diamonds are | who built the strongholds | what's under bedrock | why villagers say hm | where my dog went
a: i'd ask who put the chests in dungeons | i'd ask what endermen want | i'd ask where the end comes from
b: what endermen want, good one | the dungeon chests, i want to know too

# no_creepers | no creepers idea | mobs
q: what if creepers never existed? | imagine a world without creepers
ans: wooden houses everywhere | no more craters | i'd get lazy | i'd miss them a little | peace
a: i'd build with wool and wood | i'd finally relax at night | no gunpowder though, no fireworks
b: no fireworks, sad | wool houses, cozy

# dream_town | dream town idea | town building
q: if you designed a town from scratch, what's in the middle? | building a town from nothing, what goes in the center?
ans: a big well | a market | a bell | a giant tree | a statue
a: i'd put a huge fountain in the middle | a big farm everyone shares | a tower with a bell
b: a shared farm, nice | a fountain, classy

# underwater_town | underwater town idea | ocean town
q: what if our town was underwater? | imagine our town at the bottom of the sea
ans: glass houses | we'd all need helmets | turtles everywhere | drowned would visit | it'd be so pretty
a: i'd want a glass tunnel to every house | we'd need water breathing every day | i'd have a kelp garden
b: glass tunnels, cool | a kelp garden lol

# own_dimension | own dimension idea | dreams exploring
q: if you could make a new dimension, what would it be like? | design a dimension
ans: all forest | all cake | floating islands | upside down | all water
a: mine would be all caves, lit up | floating islands with waterfalls | a dimension where it's always sunset
b: always sunset, dreamy | lit caves, so you

# first_spawn | first spawn idea | biomes memories
q: if you could pick where you first woke up, where? | pick your first spawn biome
ans: plains, easy | forest, wood | by a village | mushroom island | on a beach
a: i'd pick a forest by a river | a plains next to a village | a birch forest, pretty
b: a forest by a river, perfect | a village start, cheating lol

# power_food | magic food idea | food
q: what if one food gave you a superpower? | if a food gave you a power, which food and what power?
ans: carrots, see in the dark | cookies, jump high | bread, never tired | fish, breathe underwater | cake, fly
a: mushroom stew that makes you glow | pumpkin pie that makes you invisible | honey that lets you climb walls
b: honey climbing, i like it | invisible pie lol

# self_sorting | self sorting chests idea | storage
q: what if chests sorted themselves? | imagine chests that sort themselves
ans: i'd cry | finally | i'd lose my system | i'd never open one again | i'd still check
a: i'd still re-sort them my way | i'd have so much free time | i'd trust nothing
b: re-sorting, so you | free time, yes

# mob_jobs | mobs with jobs idea | mobs jokes
q: what if mobs had jobs? | imagine zombies with jobs
ans: zombies would be farmers | skeletons would be archers for hire | creepers would be miners | spiders would make rope | endermen would be movers
a: creepers in demolition, perfect | skeletons teaching archery | endermen moving houses
b: creeper demolition, lol | archery lessons, ha

# grey_world | grey world idea | random
q: what if everything was one colour? | imagine the whole world was grey
ans: depressing | i'd never find anything | builds would be boring | ores would be invisible | i'd paint everything
a: i'd dye every sheep i see | i'd miss the flowers | caves would look the same as always lol
b: caves are already grey, true | dye every sheep, yes

# day_off | free day idea | dreams
q: if you had a whole day off, what would you do? | a day with nothing to do, what's the plan?
ans: fish | sleep | explore | build something silly | visit friends
a: i'd sit on a hill and watch the sky | i'd fish until night | i'd go find a new biome
b: a hill and the sky, peaceful | fishing all day, nice

# sky_ocean | upside down idea | sky ocean
q: what if the sky and the ocean swapped? | imagine the ocean in the sky
ans: fish flying | the rain would be fish | i'd swim to the clouds | squids in the sky | so weird
a: i'd fish upwards | it'd rain squids | drowned falling on my roof
b: raining squids, no | fishing upwards, lol

# creeper_words | talking creepers idea | mobs jokes
q: what if creepers could talk? | imagine creepers saying something before they blow
ans: sorry | surprise | hi friend | nice house | i'm so sorry
a: mine would say oops | they'd all just say hey | they'd say nice house every time
b: nice house, the worst | oops, lol

# wish_chest | wish chest idea | dreams
q: if you found a chest with anything you wished for, what's in it? | magic chest, anything you want, what's inside?
ans: a full stack of diamonds | a netherite pickaxe | an elytra | food forever | a map home
a: i'd want a book of every enchant | a totem, just in case | a bread loaf that never runs out
b: never ending bread, so you | a totem, smart

# small_world | small world idea | exploring
q: what if the world was really small? | imagine the whole world was tiny
ans: we'd all be neighbours | there'd be no room | i'd know every block | we'd run out of trees | cozy
a: everyone would know everyone | i'd map the whole thing in a day | we'd fight over the one mountain
b: the one mountain, lol | neighbours with everyone

# free_torches | free torches idea | night caves
q: what if torches were free and never ran out? | imagine free torches forever
ans: i'd light up the whole world | no more mobs | caves would be bright | i'd put them everywhere | my coal would be useless
a: i'd light every cave i find | i'd put torches on every tree lol | mobs would have nowhere to spawn
b: torches on trees, fire hazard | every cave lit, nice

# know_tomorrow | knowing tomorrow idea | random
q: if you could know one thing about tomorrow, what? | one thing about tomorrow, what do you want to know?
ans: the weather | if i'll find diamonds | if i'll die | what's for dinner | who i'll meet
a: i'd want to know if it rains | i'd want to know if creepers are near my house | i'd want to know if i find iron
b: the weather, practical | creepers, smart

# house_name | house name idea | home jokes
q: if your house had a name, what would it be? | what would you name your house?
ans: the hole | cozy corner | fort dirt | the cottage | home base
a: mine would be the creeper's regret | i'd call mine the last torch | mine's called home, simple
b: the creeper's regret, lol | home, simple

# clean_world | cleaning up idea | building
q: if you could clean up one thing in the world, what? | fix one thing in the world, what?
ans: all the dirt pillars | floating trees | the creeper holes | the lava pools | the cobwebs
a: every floating tree top, i'd chop them all | every creeper crater | all the half-built bridges
b: floating trees, so annoying | half built bridges, guilty

# sleep_anywhere | sleeping anywhere idea | sleep
q: what if you could sleep anywhere, even with mobs around? | imagine sleeping with mobs nearby
ans: i'd sleep in caves | i'd nap on a mountain | i'd get eaten | i'd sleep in a field | i'd sleep on a boat
a: i'd nap in the middle of a mine | i'd sleep under the stars every night | i'd still feel weird about it
b: napping in a mine, bold | under the stars, nice

# ghost | ghost idea | death
q: what if you came back as a ghost after dying? | imagine you became a ghost when you died
ans: i'd haunt the creeper | i'd float around | i'd scare my friends | i'd find my stuff | i'd watch over my house
a: i'd haunt whoever took my stuff | i'd guard my chests | i'd just float around looking at builds
b: haunting the thief, fair | guarding chests, so you

# rails_everywhere | rails everywhere idea | exploring building
q: what if there was a rail line to everywhere? | imagine rails going everywhere
ans: i'd ride all day | i'd never walk again | i'd get lost | i'd build stations | i'd love it
a: i'd build a little station by my house | i'd ride it just to see stuff | i'd race people on it
b: a station by your house, nice | racing minecarts lol

# no_gravity | no gravity idea | sky jokes
q: what if there was no gravity for a day? | imagine floating around for a whole day
ans: i'd float to the clouds | chaos | i'd hold onto a tree | i'd float to the moon | my stuff would float off
a: i'd tie myself to my house | i'd float up and look at everything | the cows would float away
b: floating cows lol | tied to your house, smart

# cats_rule | cats in charge idea | pets jokes
q: what if cats ran the world? | imagine cats in charge
ans: naps everywhere | fish for everyone | creepers would be gone | we'd be their servants | already happening
a: there'd be a nap time law | every house would have a sunny spot | creepers would be banned
b: nap time law, yes | already happening, lol

# one_animal | one animal idea | animals
q: if only one kind of animal could exist, which one? | one animal in the whole world, which?
ans: cows, food forever | cats | chickens | villagers count? | sheep
a: i'd pick sheep, wool and food | wolves, everyone gets a dog | bees, honey forever
b: wolves for everyone, nice | sheep, practical

# dig_through | digging through idea | philosophy mining
q: if you dug through the bottom of the world, what would you find? | dig past the bottom of the world, what's there?
ans: the void | another world | the nether | nothing | an upside down world
a: i bet there's another world under the void | i'd find the void and turn right back | i'd find someone else digging up
b: someone digging up, lol | the void is scary

# one_skill | one skill idea | dreams
q: if you could be great at one thing overnight, what? | one skill overnight, what is it?
ans: building | fighting | farming | mining | fishing
a: i'd want to be great at building roofs | i'd want perfect aim | i'd want to tame anything
b: roofs are the hardest, true | perfect aim, useful

# fish_anything | fishing anything idea | fishing
q: what if you could fish up anything? | imagine fishing up anything at all
ans: diamonds | a boat | a dog | a whole house | a pickaxe
a: i'd fish up my lost stuff | i'd fish up a friend lol | i'd fish up a mansion
b: your lost stuff, yes | fishing up a friend, lol

# last_day | last day idea | feelings
q: if it was your last day in this world, what would you do? | last day here, what's the plan?
ans: watch the sunset | eat a whole cake | visit everyone | build one last thing | mine for one last diamond
a: i'd sit on my roof and watch the sun go down | i'd give away everything | i'd go to the place i first woke up
b: that's kind of sad | giving everything away, nice

# build_together | big build together idea | building friends
needs: friends
w: 1.2
q: if we built something huge together, what would it be? | if we teamed up on a giant build, what would it be?
ans: a castle | a bridge | a giant tree house | a statue of us | a tower
a: a castle with a moat, obviously | a big lighthouse on the coast | a road between our houses
b: a road between our houses, cute | a lighthouse, nice

# villager_dreams | villager dreams idea | villagers dreams
q: what do you think villagers dream about? | if villagers dream, what about?
ans: emeralds | zombies | bread | us | nothing, just hm
a: they probably dream about better trades | probably about iron golems | probably about not being eaten
b: hm in their sleep | better trades lol

# shy_endermen | shy endermen idea | mobs philosophy
q: what if endermen were just shy? | imagine endermen are shy and hate being stared at
ans: that explains a lot | i'd stop staring | poor guys | i'd be shy too | they'd still be scary
a: maybe they take blocks to build their own houses | they just want to be left alone | i'd leave them notes
b: notes for endermen, lol | building their own houses, cute

# mood_weather | mood weather idea | feelings weather
q: if your mood was the weather, what is it today? | what's the weather in your head today?
ans: sunny | a bit rainy | thunder, honestly | clear skies | foggy
a: mine's sunny with a chance of creepers | mine's cloudy, could go either way | mine's a light drizzle
b: chance of creepers, lol | cloudy, fair

# secret_city | secret city idea | caves structures
q: what if there was a whole city underground nobody had found? | imagine a secret city under our feet
ans: i'd find it | ancient cities exist already | i'd be scared | i'd move in | i'd sell the map
a: i'd dig every day until i found it | i'd hope nobody lives there | i'd want it full of books
b: full of books, nice | hope nobody lives there, yeah

# golem_feelings | golem feelings idea | villagers mobs
q: what if iron golems had feelings? | imagine golems could feel stuff
ans: they'd love the villagers | they'd be lonely | they'd like flowers | they'd hate zombies | they already do, they give poppies
a: they already give poppies, so yes | i'd hug one | they'd be sad when villagers die
b: poppies, true | hugging a golem, careful

# gold_tools | gold tools idea | tools
q: what if gold tools were the best tools? | imagine gold tools were stronger than diamond
ans: gold rush | i'd go to the badlands | piglins would be rich | everyone would be shiny | i'd mine gold all day
a: i'd raid every bastion | gold armor would finally be good | i'd look so shiny
b: shiny you, lol | bastions would be busy

# map_all | mapping everything idea | exploring
q: would you map every block of the world if you could? | if you could map the whole world, would you?
ans: yes | no, too long | i'd start | i'd give up | only the good parts
a: i'd map the area around town and call it done | i'd hang the whole thing on a wall | i'd get lost making it
b: a map wall, cool | getting lost making a map, lol

# relive_day | reliving a day idea | memories
q: if you could live one day again, which one? | one day to live again, which?
ans: my first diamond | the day i built my house | the day we met | the first night | a day i just fished
a: the day i finished my house | the day i found my dog | a quiet rainy day i spent inside
b: finding your dog, aw | a rainy day, cozy

# dolphin_guide | dolphin guide idea | ocean animals
q: what if dolphins could lead you to anything? | imagine dolphins took you anywhere you wanted
ans: i'd ask for diamonds | i'd ask for home | shipwrecks | a new island | a village
a: they already take you to treasure, kinda | i'd ask one to find me a mushroom island | i'd follow one forever
b: they kinda do, true | a mushroom island, nice

# golden_touch | golden touch idea | jokes
q: what if everything you touched turned to gold? | imagine everything you touched became gold
ans: piglins would love me | i'd starve | rich | i couldn't hold my sword | gold everything
a: i couldn't eat bread, it'd turn to gold | piglins would follow me everywhere | my house would be so shiny
b: gold bread, sad | a piglin fan club, lol

# every_house | every house idea | home building
q: if you could add one thing to every house, what? | one thing every house should have, what?
ans: a bed | a chest | a cat | a torch | a garden
a: a window with a view | a little flower pot | a crafting table by the door
b: a flower pot, nice | a crafting table by the door, smart

# sky_ladder | sky ladder idea | sky
q: what if there was a ladder to the sky? | imagine a ladder that goes up forever
ans: i'd climb it | i'd fall | i'd build a house at the top | it'd lead to the clouds | i'd sit halfway
a: i'd climb until i saw the whole world | i'd hang a bed halfway up | i'd get scared at the top
b: a bed halfway up, lol | scared at the top, same
"""

# ---------------------------------------------------------------------------------------------------------------------
# 3. Riddles (q, g: b's wrong guesses, r: the answer), guess the mob (c, g1, n, g2, y), games (written out), jokes
# (j, p: punchline, g: odd guesses) and knock knock jokes (n: the name, p: the punchline, who: who-answers).

RIDDLES = """
# clock | clock riddle | random
q: riddle: i have a face but no eyes, and i tell the time without talking. what am i? | ok riddle. a face, no eyes, knows the time. what is it?
g: a creeper? | a painting? | no idea | a villager?
r: a clock! | it's a clock, made of gold

# hole | hole riddle | mining
q: riddle: what gets bigger the more you take out of it? | what gets bigger the more you take away from it?
g: my chest? | a farm? | my inventory? | uh, a debt?
r: a hole lol | a hole. think about it, every mine

# breath | breath riddle | ocean
q: riddle: what can you hold without ever touching it? | what can you hold but never touch?
g: a torch? | a sword? | water? | a grudge
r: your breath. for swimming | your breath, like underwater

# level | level riddle | random
q: riddle: what goes up and up and never comes down? | what only ever goes up?
g: a pillar? | smoke? | a firework? | the price of bread
r: your level. until you die anyway | your xp level, till you die

# bottle | bottle riddle | crafting
q: riddle: what has a neck but no head? | what has a neck and no head?
g: a llama? | a skeleton? | a headless zombie?
r: a glass bottle | a bottle. a glass one

# river | river riddle | biomes
q: riddle: what runs but never walks, and has a bed but never sleeps? | runs but can't walk, has a bed but never sleeps. what?
g: a horse? | a villager? | me lol | a zombie?
r: a river! | a river. riverbed. get it

# sponge | sponge riddle | ocean
q: riddle: what's full of holes but still holds water? | full of holes, still holds water. what is it?
g: a bucket? | a cauldron? | a leaky boat? | a cave?
r: a sponge. there's some in ocean monuments | a sponge!

# ender_eye | eye riddle | end
q: riddle: what has an eye but can't see a thing? | what has one eye and can't see?
g: a spider? | a blind bat? | a skeleton?
r: an eye of ender. it just floats off | an eye of ender lol

# panda | panda riddle | animals
q: riddle: what's black and white and red all over? | black, white and red all over. what?
g: a skeleton with a rose? | a cow in lava? | a penguin? | no idea
r: a panda that walked into a berry bush | a panda that sat on sweet berries

# mushroom | mushroom riddle | plants
q: riddle: what kind of room has no doors and no windows? | a room with no door and no windows, what is it?
g: a hole? | a bunker? | a jail? | my mine?
r: a mushroom | a mush-room!

# candle | candle riddle | decor
q: riddle: i'm tall when i'm young and short when i'm old. what am i? | tall when young, short when old?
g: a villager? | a tree? | a torch?
r: a candle | a candle, they melt down

# tree_bark | bark riddle | trees
q: riddle: what has bark but no bite? | bark but no bite, what is it?
g: a wolf? | a quiet dog? | a tame wolf?
r: a tree lol | a tree. bark. you get it

# light | light riddle | night
q: riddle: what can fill a whole room but takes up no space? | fills a room, takes no space. what?
g: air? | chests? | a smell? | water?
r: light. like from a torch | light!

# egg | egg riddle | animals food
q: riddle: what has to be broken before you can use it? | what do you break before you use it?
g: a pickaxe? | a stone? | a door? | a promise?
r: an egg | an egg. ask any chicken

# darkness | dark riddle | night caves
q: riddle: the more there is of me, the less you see. what am i? | the more of it, the less you see?
g: fog? | smoke? | water? | lava?
r: the dark. why we carry torches | darkness. caves, basically

# time | time riddle | random
q: riddle: what flies without wings? | what flies with no wings?
g: a phantom? | an arrow? | a ghast? | a snowball
r: time. look how late it is | time. it flies

# log_rings | log riddle | trees
q: riddle: what has lots of rings but no fingers? | rings, no fingers, what is it?
g: a bell? | a villager? | a necklace?
r: a log. look at the top of one | a tree log. the rings!

# rain | rain riddle | weather
q: riddle: what comes down but never goes back up? | what only ever comes down?
g: gravel? | sand? | an anvil? | me, off a cliff
r: rain | the rain

# silence | silence riddle | philosophy
q: riddle: what breaks when you say its name? | say its name and it breaks. what is it?
g: glass? | a promise? | an egg? | a pot?
r: silence. oops, i just broke it | silence

# sign | sign riddle | building
q: riddle: what has words but never speaks? | words but no voice, what is it?
g: a villager? | a wandering trader? | a parrot?
r: a sign. or a book | a sign!

# bookshelf | bookshelf riddle | structures
q: riddle: i'm full of stories but never tell one, and strongholds are full of me. what am i? | full of stories, never talks, lives in strongholds?
g: a librarian? | a silverfish? | a ghost?
r: a bookshelf | a bookshelf, obviously

# crafting_table | table riddle | crafting
q: riddle: what has legs but never walks? | legs but no walking, what?
g: a skeleton? | a chair? | a stuck spider?
r: a crafting table lol | a crafting table

# fire | fire riddle | danger
q: riddle: what's always hungry, eats everything, and dies if you give it water? | always hungry, dies with water. what?
g: a zombie? | an enderman? | a blaze? | me before breakfast
r: fire | fire. like a campfire
b: wait, blaze was kinda right | i was so close with blaze

# bell | bell riddle | villagers
q: riddle: what has a ring but no finger? | a ring, no finger. what?
g: a skeleton? | a golem? | a clock?
r: a bell, like the village one | the village bell

# pumpkin_teeth | pumpkin riddle | plants
q: riddle: what has teeth but never bites? | teeth but no bite, what is it?
g: a baby zombie? | a toothless wolf? | a skeleton?
r: a carved pumpkin | a jack o lantern

# sapling | sapling riddle | trees
q: riddle: what kind of tree fits in your hand? | a tree you can hold in one hand?
g: a stick? | a bonsai? | a log?
r: a sapling. any tree | a sapling!

# map | map riddle | exploring
q: riddle: what has villages but no houses, and oceans but no water? | villages with no houses, seas with no water?
g: a painting? | a desert? | a dream?
r: a map | a map, obviously

# brain | brain riddle | philosophy
q: riddle: what gets sharper the more you use it? | what gets sharper with use?
g: a sword? | an axe? | a grindstone?
r: your brain. well, mine | your brain

# gravel | gravel riddle | mining
q: riddle: i'm grey, i fall on miners, and i hide flint inside. what am i? | grey, falls on you, has flint. what?
g: an anvil? | cobblestone? | a skeleton?
r: gravel. evil gravel | gravel, the worst

# bed | bed riddle | sleep
q: riddle: made of wool and wood, you visit me every night. what am i? | wool and wood, you see me every night. what?
g: a sheep? | a rug? | a fence?
r: a bed! | your bed lol

# shadow | shadow riddle | random
q: riddle: what follows you all day but disappears in the dark? | follows you all day, gone at night. what?
g: a wolf? | a cat? | a parrot?
r: your shadow | a shadow

# tomorrow | tomorrow riddle | philosophy
q: riddle: what's always coming but never arrives? | what's always on its way but never here?
g: the trader? | the rain? | dinner?
r: tomorrow | tomorrow. it's always tomorrow

# snowball | snowball riddle | weather
q: riddle: i'm thrown in fights but i never hurt anyone, except blazes. what am i? | thrown in fights, hurts nobody but blazes?
g: an egg? | a potion? | a pearl?
r: a snowball | snowballs!

# compass | compass riddle | exploring
q: riddle: i always point home but i never move from your hand. what am i? | points home, never leaves your hand?
g: a dog? | a map? | a sign?
r: a compass | a compass. well, sort of home

# echo | echo riddle | caves
q: riddle: i answer you in caves but i have no voice of my own. what am i? | in caves i talk back but i've got no voice. what?
g: a bat? | a cave spider? | a ghost?
r: an echo | your echo
"""

GUESS = """
# creeper | guessing a creeper | mobs
c: i'm thinking of a mob. it's green | guess the mob i'm thinking of. it's green
g1: a zombie? | a slime? | a turtle? | a frog?
n: nope. it hisses | no. it hisses at you
g2: oh, a creeper | creeper! | a creeper. obviously
y: yep, a creeper | got it | there it is

# enderman | guessing an enderman | mobs
c: guess the mob: it's tall and black | i'm thinking of a mob. tall and dark
g1: a wither skeleton? | a spider? | a tall zombie?
n: nope. it hates being looked at | no. don't stare at it
g2: an enderman | enderman! | ohh, enderman
y: yep | correct | that's the one

# golem | guessing an iron golem | villagers mobs
c: guess the mob: it loves villagers | i'm thinking of a mob that loves villagers
g1: a cat? | a villager? | the wandering trader?
n: nope. it hands out poppies | no. big, and it gives you flowers
g2: an iron golem! | iron golem | the golem!
y: yep | yes | got it

# cat | guessing a cat | pets animals
c: guess the animal: creepers are scared of it | i'm thinking of something creepers are scared of
g1: a torch? | an iron golem? | a dog?
n: nope. it purrs | no. it sits on your bed
g2: a cat! | ohh, a cat | cats
y: yep, a cat | correct | got it

# axolotl | guessing an axolotl | animals caves
c: guess the mob: it's pink and lives in caves | i'm thinking of a pink mob that lives in caves
g1: a pig? | a pink sheep? | a pink slime?
n: nope. it plays dead when it's hurt | no. it lives in water, fights drowned
g2: an axolotl! | axolotl | oh, an axolotl
y: yep | correct | that's it

# bee | guessing a bee | animals
c: guess what i'm thinking of: small, flies, fuzzy | i'm thinking of something small and fuzzy that flies
g1: a bat? | a parrot? | a phantom? | a chicken?
n: nope. it makes something sweet | no. it lives in a hive
g2: a bee! | bee | oh, a bee
y: yep | buzz buzz, correct | got it

# ghast | guessing a ghast | nether mobs
c: guess the mob: it cries all the time | i'm thinking of a mob that won't stop crying
g1: a villager? | a baby zombie? | a sad wolf?
n: nope. it shoots fireballs | no. big, white, floats in the nether
g2: a ghast | ghast! | ohh, a ghast
y: yep | correct | that's it

# llama | guessing a llama | animals
c: guess the animal: you can put a carpet on it | i'm thinking of an animal that wears a carpet
g1: a horse? | a pig? | a donkey?
n: nope. it spits | no. it carries chests and spits
g2: a llama! | llama, the rude one | a llama
y: yep | correct | the spitter

# phantom | guessing a phantom | mobs night
c: i'm thinking of a mob that only shows up when you're tired | guess the mob: it comes when you don't sleep
g1: a zombie? | a bat? | a ghost?
n: nope. it swoops from the sky | no. it dives at you at night
g2: a phantom | phantom, ugh | oh, a phantom
y: yep | correct | the worst one

# squid | guessing a squid | ocean animals
c: guess the mob: it gives you ink | i'm thinking of a mob that drops ink
g1: a librarian? | a cow? | a book?
n: nope. it lives in water | no. tentacles
g2: a squid | squid! | oh, a squid
y: yep | correct | got it

# witch | guessing a witch | mobs
c: guess the mob: it wears a pointy hat | i'm thinking of a mob with a hat
g1: a villager? | a pillager? | the trader?
n: nope. it throws potions | no. it lives in a swamp hut
g2: a witch! | witch | oh, the witch
y: yep | correct | got it

# dolphin | guessing a dolphin | ocean animals
c: guess the animal: it's super friendly in the ocean | i'm thinking of a friendly ocean animal
g1: a turtle? | a squid? | a cod?
n: nope. it leads you to treasure | no. it jumps and swims fast
g2: a dolphin! | dolphin | oh, a dolphin
y: yep | correct | got it

# fox | guessing a fox | animals
c: guess the animal: it sleeps all day and it's orange | i'm thinking of an orange animal that naps all day
g1: a cat? | a pig? | a chicken?
n: nope. it steals stuff out of your hands | no. it lives in the taiga
g2: a fox! | fox | oh, a fox
y: yep | correct | the thief

# turtle | guessing a turtle | ocean animals
c: guess the animal: it always goes back to the same beach | i'm thinking of an animal that always goes home to its beach
g1: a dolphin? | a squid? | a drowned?
n: nope. it's slow and has a shell | no. shell on its back
g2: a turtle | turtle! | oh, a turtle
y: yep | correct | slow and steady

# silverfish | guessing a silverfish | mobs mining
c: guess the mob: it hides inside stone | i'm thinking of a mob that hides in stone
g1: a bat? | an endermite? | a cave spider?
n: nope. tiny and grey, lives in strongholds | no. small, grey, comes in swarms
g2: silverfish | ugh, silverfish | silverfish, the worst
y: yep | correct | sorry

# sniffer | guessing a sniffer | animals
c: guess the mob: it digs up old seeds | i'm thinking of a mob that sniffs out seeds
g1: a pig? | a rabbit? | a mole?
n: nope. it's huge and it hatches from an egg | no. big and fluffy, from an old egg
g2: a sniffer! | sniffer | oh, a sniffer
y: yep | correct | got it

# slime | guessing a slime | mobs
c: guess the mob: it bounces | i'm thinking of a bouncy mob
g1: a rabbit? | a frog? | a goat?
n: nope. it splits when you hit it | no. green and squishy, splits in two
g2: a slime | slime! | oh, a slime
y: yep | correct | got it

# allay | guessing an allay | mobs
c: guess the mob: it's blue and it brings you stuff | i'm thinking of a little blue mob that fetches things
g1: a vex? | a parrot? | a fairy?
n: nope. it loves music | no. it dances to note blocks
g2: an allay! | allay | oh, an allay
y: yep | correct | got it

# goat | guessing a goat | animals
c: guess the animal: it lives up on mountains | i'm thinking of an animal that lives on mountains
g1: a llama? | a sheep? | a rabbit?
n: nope. it rams you off cliffs | no. it headbutts people
g2: a goat! | goat, the menace | oh, a goat
y: yep | correct | got it

# gravel | guessing gravel | mining
c: guess the block: it's grey and it falls | i'm thinking of a block. grey, falls down
g1: sand? | an anvil? | cobble?
n: nope. you get flint out of it | no. it has flint in it
g2: gravel! | gravel, ugh | oh, gravel
y: yep | correct | the worst block

# obsidian | guessing obsidian | mining
c: guess the block: it's super hard to mine | i'm thinking of a block that takes forever to mine
g1: deepslate? | bedrock? | iron?
n: nope. it's purple-black, you make portals with it | no. water on lava makes it
g2: obsidian | obsidian! | oh, obsidian
y: yep | correct | got it

# compass | guessing a compass | exploring
c: guess the item: it points somewhere | i'm thinking of an item that points at things
g1: a sign? | an arrow? | a stick?
n: nope. iron and redstone | no. it points home, kind of
g2: a compass | compass! | oh, a compass
y: yep | correct | got it

# carrot | guessing a carrot | food farming
c: guess the food: it's orange and grows in the ground | i'm thinking of a food. orange, grows in dirt
g1: a pumpkin? | a potato? | a beetroot?
n: nope. pigs follow you for it | no. you put it on a stick
g2: a carrot | carrot! | oh, a carrot
y: yep | correct | got it

# saddle | guessing a saddle | animals
c: guess the item: you can't craft it | i'm thinking of an item you can't craft
g1: a diamond? | a name tag? | a totem?
n: nope. you ride with it | no. you put it on a horse
g2: a saddle | saddle! | oh, a saddle
y: yep | correct | got it
"""

# Games, written out in turns (see build_free).
GAMES = """
# spy_sky | i spy the sky | riddles sky
needs: outside clear time_day
aq: i spy with my little eye, something blue | i spy something blue => water? | lapis? | your shirt? | a squid?
a: nope, the sky. look up | the sky lol | the whole sky
b: oh come on | that's cheating, it's everywhere | ok that's too easy
a?.5: your turn | ok you go

# spy_sun | i spy the sun | riddles sky day
needs: outside clear time_morning
aq: i spy with my little eye, something square and yellow | i spy something yellow and really bright => a block of gold? | a sunflower? | a torch? | a bee?
a: nope, the sun | the sun lol, don't look at it | the sun. it's square, have you noticed
b: the sun is not fair | ok that's cheating | ugh
a?.5: your turn | ok you go

# spy_moon | i spy the moon | riddles night sky
needs: outside clear time_night
aq: i spy with my little eye, something white up high | i spy something white and square at night => a ghast? | a snow golem? | a cloud? | a phantom?
a: nope, the moon | the moon lol | the moon. look up
b: oh, the moon | should've got that | it's always the sky with you
a?.5: your turn | ok you go

# spy_rain | i spy the rain | riddles weather
needs: outside rain
aq: i spy with my little eye, something wet | i spy something falling => a sapling? | an anvil? | gravel? | me, later
a: nope, the rain | the rain lol | all of it. the rain
b: that's everywhere, that's not fair | lol ok | rain doesn't count
a?.5: it counts | your turn

# spy_ground | i spy the ground | riddles jokes
aq: i spy with my little eye, something under your feet | i spy something you're standing on => my boots? | a pressure plate? | a creeper? | lava??
a: nope, the ground | the ground lol | the ground. it counts
b: that's the worst one yet | ok you're banned from i spy | sure, the ground
a?.5: your turn | i'm good at this

# spy_leather | i spy leather armor | riddles armor
needs: b:armor_leather
aq: i spy with my little eye, something made of leather | i spy something brown and stitched => a book? | a saddle? | a cow? | an item frame?
a: nope, your armor | your armor lol | your leather armor. very nice
b: hey, it's comfy | rude | i'll upgrade soon
a?.5: lol | it suits you

# spy_iron | i spy iron armor | riddles armor
needs: b:armor_iron
aq: i spy with my little eye, something shiny and grey | i spy something shiny => a bucket? | an iron golem? | a sword? | the moon?
a: nope, your armor | your iron armor | your chestplate, very shiny
b: oh, thanks | it is shiny | took me ages to smelt that
a?.5: lol | looking good

# spy_diamond | i spy diamond armor | riddles armor
needs: b:armor_diamond
aq: i spy with my little eye, something blue and expensive | i spy something very shiny and blue => lapis? | the sky? | a diamond block? | water?
a: nope, your armor, show-off | your diamond armor lol | your armor, mr fancy
b: i earned this | jealous? | it was a lot of mining
a?.5: maybe a little | lol

# spy_stars | i spy the stars | riddles night sky
needs: outside clear time_night
aq: i spy with my little eye, lots of little white dots | i spy something tiny and shiny up high => snow? | torches far away? | phantoms?? | bats?
a: nope, the stars | the stars | stars. look up, they're nice tonight
b: oh, they are nice | the sky again | ok that one was nice
a?.5: your turn | i love clear nights

# found_name_tag | found a name tag | riddles animals
aq: guess what i found today | guess what i found! => diamonds? | a saddle? | a dog? | a cave?
a: nope, a name tag! in a dungeon chest | a name tag, from a dungeon
b: ooh, what are you naming? | nice, those are rare | name something silly
a: my next cow, obviously | haven't decided yet | something long and dramatic

# found_waterfall_cave | found a waterfall cave | riddles caves
aq: guess what i found | guess what i found today => diamonds? | a village? | a dog? | a creeper?
a: nope, a cave with a waterfall inside | a cave with a waterfall in it. so pretty
b: pretty | take me there sometime | caves with waterfalls are the best
a: it's got lava too, careful | i'll show you sometime | i sat there for ages

# found_bee_nest | found a bee nest | riddles animals
aq: guess what i found | bet you can't guess what i found => a diamond? | a horse? | an axolotl? | a skeleton?
a: nope, a bee nest full of honey | a bee nest! all the honey
b: don't get stung | honey! | did you take some?
a: already got stung lol | i'm leaving it alone for now | gonna need a campfire under it first

# found_pink_sheep | found a pink sheep | riddles animals
aq: guess what i found | guess what i found today => a pig? | diamonds? | a cave? | a cat?
a: nope, a pink sheep! a real one | a pink sheep, like a natural pink one
b: no way, those are rare | pink wool! | did you keep it?
a: i named it | it follows me now, with wheat | i'm keeping it forever

# found_skeleton_horse | found skeleton horses | riddles mobs
aq: guess what i saw last storm | guess what i found during the thunder => a creeper? | lightning? | a witch? | a pig?
a: skeleton horses. with skeletons on them | a skeleton horse trap, four of them
b: creepy | careful, those are a trap | did you run?
a: i noticed lol | ran away fast | i got one horse out of it

# found_empty_boat | found an empty boat | riddles ocean
aq: guess what i found on the lake | guess what i found => a fish? | a drowned? | a shipwreck? | treasure?
a: an empty boat, just floating there | a boat. nobody in it
b: spooky | whose is it? | maybe someone fell out
a: no idea, i took it | i left it, felt wrong to take | i waited a bit, nobody came

# found_treasure_map | found a treasure map | riddles exploring
aq: guess what i found in a shipwreck | guess what i got from a shipwreck => gold? | a sword? | a fish? | a drowned?
a: a treasure map! | a buried treasure map
b: go get it! | we're going on a trip | where's the x?
a: it's halfway across the ocean | wanna come? | on a beach somewhere, i think

# found_flat_spot | found a building spot | riddles building
aq: guess what i found today | guess what i found => diamonds? | a village? | a cave? | a horse?
a: the perfect flat spot for a house | a perfect spot to build, by the river
b: build there! | is it near water? | finally
a: right on the water | already put a torch on it | it's even got trees around it

# found_lush_cave | found a lush cave | riddles caves plants
aq: guess what i found under the hill | guess what i found => iron? | a dungeon? | lava? | bats?
a: a lush cave, all green inside | a lush cave! glow berries everywhere
b: no way, those are so pretty | axolotls? | take me
a: two axolotls, yeah | i picked some glow berries | it's like a garden down there

# twenty_cow | twenty questions, a cow | riddles animals
a: twenty questions. i've got an animal | ok twenty questions, i'm thinking of an animal
bqq: does it fly? => nope / no wings || does it live in water? => no, on land / nope
bqq: do you farm it? => yep / yeah, kind of || is it big? => pretty big / bigger than a pig
b: a cow? | is it a cow? | it's a cow
a: yep, a cow | got it | took you three

# twenty_glass | twenty questions, glass | riddles building
a: twenty questions. i'm thinking of a block | twenty questions? i've got a block
bqq: can you mine it? => you can but you lose it / not really, it breaks || is it natural? => nope, you make it
bqq: can you see through it? => yep / yeah || do you make it in a furnace? => yep, from sand
b: glass? | is it glass? | glass!
a: yep, glass | got it | easy one

# twenty_zombie | twenty questions, a zombie | riddles mobs
a: twenty questions, i've got a mob | ok i'm thinking of a mob, twenty questions
bqq: is it hostile? => yep / very || does it explode? => nope, thankfully
bqq: does it burn in the sun? => yep / it does || does it shoot arrows? => no, it just walks at you
b: a zombie? | zombie! | is it a zombie?
a: yep, a zombie | got it | classic

# twenty_bread | twenty questions, bread | riddles food
a: twenty questions. i'm thinking of a food | ok, twenty questions. it's a food
bqq: do you cook it? => nope, you craft it / not in a furnace || is it meat? => no
bqq: does it come from wheat? => yep / yes || is it a cake? => nope, simpler
b: bread? | is it bread? | it's bread
a: yep, bread | got it | bread, the classic

# twenty_bucket | twenty questions, a bucket | riddles crafting
a: twenty questions. i've got an item | twenty questions, i'm thinking of an item
bqq: is it a tool? => kind of / sort of || can you eat it? => no lol
bqq: is it made of iron? => yep / yes, three ingots || can it hold stuff? => yep, liquids
b: a bucket? | is it a bucket? | bucket!
a: yep, a bucket | got it | nice

# twenty_village | twenty questions, a village | riddles villagers
a: twenty questions, i'm thinking of a place | twenty questions? i've got a place
bqq: is it underground? => nope / no || is it in the nether? => no, here
bqq: do people live there? => yep / lots of them || is there a bell? => yep
b: a village? | is it a village? | village!
a: yep, a village | got it | too easy

# rate_cloud | cloud shapes | sky jokes
needs: outside clear time_day
a: that cloud looks like a pig | look, that cloud's a pig | doesn't that cloud look like a creeper?
b: they're all just rectangles | squint and it's a cow | it looks like a block of wool
a: you have no imagination | ok yeah it's a rectangle | it's a pig. trust me
b?.5: lol fine, it's a pig | i'll allow it

# count_sheep | counting sheep game | sleep animals
needs: time_night
a: can't sleep, i'm counting sheep | one sheep, two sheep... | counting sheep, it's not working
b: count the pink ones, they're rarer | you're gonna get a headache | try counting creepers, you'll stay awake
a: now i'm counting creepers, thanks | i lost count at a white one | twelve sheep, still awake
b?.6: lol go to bed | try warm milk... from a bucket

# guess_number | number guessing game | jokes
aq: guess a number from one to ten | pick a number, one to ten => seven | three? | ten | one, final answer
a: nope, it was four | wrong, it was four | four. it's always four
b: you changed it | that's rigged | ok again | how was i meant to know that
a?.5: lol | i'll pick a new one | best of three?
"""

JOKES = """
# creeper_salad | creeper food joke | mobs
j: what's a creeper's favourite food? | ok joke. what do creepers eat?
g: gunpowder? | your house?
p: ssssssalad | ssssalad. obviously

# creeper_road | creeper road joke | mobs
j: why did the creeper cross the road? | joke: why'd the creeper cross the road?
g: to blow up the other side?
p: to get to the other ssssside | to get to the other ssside

# creeper_history | creeper reading joke | mobs
j: what does a creeper like reading about? | what do creepers read about?
p: hisss-tory | hissstory. obviously

# creeper_ex | creeper friend joke | mobs
j: what does a creeper call an old friend? | what do creepers call old friends?
p: a blast from the past

# creeper_hide | creeper hide and seek joke | mobs
j: why do creepers always lose at hide and seek? | why are creepers bad at hide and seek?
p: they keep blowing their cover | they always blow their cover

# creeper_party | creeper party joke | mobs
j: why don't creepers get invited to parties? | why do creepers never get invited anywhere?
p: they always blow up at people | they just can't keep it together

# creeper_blast | creeper joke joke | mobs
j: did you hear the joke about the creeper? | you hear the creeper joke?
g: no? | oh no
p: it was a blast | it really blew up

# skeleton_guts | skeleton fight joke | mobs
j: why don't skeletons fight each other? | why do skeletons never fight?
p: they don't have the guts | no guts

# skeleton_party | skeleton party joke | mobs
j: why did the skeleton stay home from the party? | why didn't the skeleton go out tonight?
p: it had no body to go with | no body to go with

# skeleton_bone | skeleton music joke | mobs music
j: what's a skeleton's favourite instrument? | what instrument do skeletons play?
p: the trom-bone | trombone. trom-bone

# skeleton_room | skeleton room joke | mobs home
j: what room do skeletons hate? | which room do skeletons never go in?
p: the living room

# stray_cold | stray cold joke | mobs weather
j: why do strays hate the snow? | why are strays always so cold?
p: the cold goes right through them

# skeleton_calm | calm skeleton joke | mobs
j: why are skeletons so calm all the time? | why don't skeletons ever get upset?
p: nothing gets under their skin

# zombie_rotten | sick zombie joke | mobs
j: why did the zombie stay in bed? | why'd the zombie take the day off?
p: it felt rotten | it was feeling rotten

# zombie_groan | zombie hide and seek joke | mobs
j: why are zombies bad at hide and seek? | why do zombies always get found?
p: they groan every time you get close | you can hear them groaning a mile off

# zombie_deadpan | zombie comedian joke | mobs
j: what do you call a zombie that tells jokes? | what's a funny zombie called?
p: deadpan | dead funny

# zombie_door | zombie door joke | mobs
j: why do zombies break doors down? | why don't zombies just knock?
p: nobody ever lets them in | they never learned to knock

# enderman_water | enderman water joke | mobs
j: why don't endermen ever go swimming? | why do endermen hate the beach?
p: water really gets to them | they can't handle getting their feet wet

# enderman_stare | enderman game joke | mobs
j: what's an enderman's favourite game? | what game do endermen play?
p: staring contest. you always lose | a staring contest, and it always wins

# enderman_furniture | enderman village joke | mobs villagers
j: why did the enderman get kicked out of the village? | why aren't endermen allowed in towns?
p: it kept moving the furniture | it kept rearranging everyone's houses

# ghast_secret | ghast secret joke | nether mobs
j: why are ghasts bad at keeping secrets? | why can't you tell a ghast anything?
p: they scream everything out | they just scream it to everyone

# sheep_basalt | sheep block joke | animals
j: what's a sheep's favourite block? | which block do sheep like most?
p: baaa-salt | basalt. baaa-salt

# pig_chop | fighting pig joke | animals
j: what do you call a pig that knows how to fight? | what's a fighting pig called?
p: a pork chop

# villager_nitwit | lazy villager joke | villagers
j: what do you call a lazy villager? | what's a villager with no job called?
p: a nitwit. it's literally their job title | a nitwit. that's the actual name

# villager_listen | villager listening joke | villagers
j: why are villagers such good listeners? | why do villagers make great friends?
p: all they ever say is hm | they just say hm and let you talk

# cow_toes | cow feet joke | animals
j: why do cows have hooves instead of feet? | why don't cows have toes?
p: because they lactose | they lack toes. lactose

# mooshroom | mushroom cow joke | animals
j: what do you call a cow that ate too many mushrooms? | what happens when a cow eats a mushroom?
p: a mooshroom | it becomes a mooshroom

# cow_ground | legless cow joke | animals food
j: what do you call a cow with no legs? | a cow with no legs, what's it called?
p: ground beef

# bee_hum | humming bee joke | animals music
j: why do bees hum? | why do bees always hum?
p: they don't know the words

# bee_comb | bee hair joke | animals
j: what do bees use to fix their hair? | how do bees do their hair?
p: a honeycomb

# bee_maybe | undecided bee joke | animals
j: what do you call a bee that can't make up its mind? | what's an indecisive bee called?
p: a maybe | a may-bee

# polar_b | polar bear joke | animals
j: what do you call a polar bear with no ears? | a polar bear with no ears is called what?
p: a polar b | polar b

# fish_eyes | eyeless fish joke | ocean
j: what do you call a fish with no eyes? | a fish with no eyes is a what?
p: a fsh

# ocean_waves | friendly ocean joke | ocean
j: why is the ocean so friendly? | why is the sea so nice?
p: it always waves | it waves at everyone

# squid_tickles | laughing squid joke | ocean
j: how do you make a squid laugh? | how do you get a squid to laugh?
p: ten-tickles | ten tickles

# glow_squid | glow squid joke | ocean caves
j: why do glow squids get invited everywhere? | why's everyone friends with glow squids?
p: they light up the room

# where_wolf | lost wolf joke | animals pets
j: what do you call a wolf that's lost? | what do you call a lost wolf?
p: a where-wolf | where wolf

# fox_won | fox game joke | animals
j: why does the fox win every game? | why can't anyone beat a fox at anything?
p: it outfoxes everyone

# frog_happy | happy frog joke | animals
j: why are frogs always happy? | why is a frog never upset?
p: they eat whatever bugs them

# cat_pile | cat pile joke | pets
j: what do you call a pile of cats? | what's a big heap of cats called?
p: a meow-ntain | a meowntain

# axolotl_asks | axolotl question joke | animals
j: why does the axolotl always have questions? | why's the axolotl so curious?
p: it axolotl questions | it axolotl of things

# llama_manners | llama dinner joke | animals
j: why does nobody invite llamas to dinner? | why are llamas never invited anywhere?
p: they spit everywhere | terrible manners. all spit

# phantom_tired | phantom friends joke | mobs night
j: why don't phantoms have any friends? | why is nobody friends with phantoms?
p: they only show up when you're tired | they only come around when you're tired

# torch_lit | torch party joke | night
j: what kind of party does a torch throw? | what's a torch's party like?
p: a lit one | lit

# redstone_signals | redstone joke | jokes
j: why did the two redstone wires stop talking? | why did the redstone dust break up?
p: too many mixed signals

# boat_row | boat joke | ocean
j: why did the boat get promoted? | why's that boat so successful?
p: it was a real row model | a row model

# glass_pane | glass joke | building
j: why is glass so annoying? | what's the most annoying block?
p: it's a real pane | glass. it's a pane

# sign_board | sign joke | building
j: why didn't the sign say anything? | why was the sign so quiet?
p: it was board | it was bored. board

# oak_leaf | oak tree joke | trees
j: why did the oak tree get in trouble? | what did the oak tree do wrong?
p: it was caught leafing early | it was leafing without asking

# tree_relief | tree axe joke | trees
j: what did the tree say when the axe missed? | what does a tree say when you miss with an axe?
p: what a re-leaf | phew. what a re-leaf

# granite | stone joke | mining
j: why does stone always feel ignored? | why is stone so sad?
p: everyone takes it for granite

# miner_rock | miner music joke | mining music
j: what music do miners listen to? | what do miners listen to down there?
p: rock. obviously | rock and coal

# diamond_pressure | diamond and coal joke | ore diamonds
j: what did the diamond say to the coal? | what's the diamond tell the coal?
p: you're under a lot of pressure | relax, you're under a lot of pressure

# dirt_earth | humble dirt joke | building
j: why is dirt so humble? | why is the dirt block so humble?
p: it's down to earth

# gravel_down | sad gravel joke | mining
j: why is gravel always so down? | why's gravel so sad?
p: it keeps falling for things | it's always falling

# anvil_falls | anvil joke | crafting
j: why did the anvil fall for everyone? | why does the anvil fall so hard?
p: it falls for anyone standing under it | it just falls hard

# melon_choly | sad melon joke | food
j: what do you call a sad melon? | what's an upset melon called?
p: melon-choly | melancholy. melon-choly

# carrot_parrot | carrot joke | food
j: what's orange and sounds like a parrot? | what's orange and sounds like a bird?
p: a carrot

# furnace_hot | furnace joke | crafting
j: why is the furnace so popular? | why does everyone love the furnace?
p: it's smelting hot

# bed_tired | bed joke | sleep
j: why did the bed want the night off? | why did the bed quit?
p: it was tired of being slept on

# bucket_lava | bucket joke | danger
j: what did the bucket say to the lava? | what's the bucket tell the lava?
p: you're too hot to handle | you're too hot to hold

# chicken_counts | counting chicken joke | animals
j: what do you call a chicken that counts its eggs? | what's a chicken that's good with numbers?
p: a mathema-chicken | a mathemachicken

# chicken_furnace | chicken furnace joke | animals food
j: why did the chicken lay its egg in the furnace? | why was the chicken sitting in the furnace?
p: it wanted it hard boiled

# dull_pick | dull pickaxe joke | tools
j: why was the pickaxe so boring at parties? | why is nobody friends with the old pickaxe?
p: it was really dull | it got dull

# sword_point | sword argument joke | weapons
j: why does the sword never win an argument? | why does the sword lose every argument?
p: it can't get to the point | it keeps missing the point

# shield_block | shield joke | weapons
j: why don't shields get invited out? | why do shields have no friends?
p: they block everyone

# witch_spell | witch spelling joke | mobs
j: why don't witches do spelling? | why can't witches spell?
p: they only know how to brew | they'd rather brew than spell

# witch_watch | witch time joke | mobs
j: how does a witch tell the time? | how do witches know what time it is?
p: she checks her witch watch | witch watch

# pillager_cross | grumpy pillager joke | mobs
j: why are pillagers always grumpy? | why is every pillager in a bad mood?
p: they're always so cross | they're cross. crossbow. get it

# iron_will | iron golem joke | villagers
j: why are iron golems never scared? | why don't iron golems get scared?
p: they've got an iron will

# slime_bounce | slime fun joke | mobs
j: what do slimes do for fun? | what's a slime's idea of fun?
p: they just bounce around | they bounce

# magma_temper | magma cube joke | nether mobs
j: why do magma cubes lose every argument? | why can't you talk to a magma cube?
p: they're too hot-headed | they've got a hot temper

# blaze_fired | blaze joke | nether mobs
j: why did the blaze lose the argument? | why did the blaze get kicked out?
p: it got too fired up

# turtle_home | turtle joke | ocean animals
j: why did the turtle cross the beach? | where was the turtle going?
p: back to the beach it was born on | home. they always go home

# rabbit_hop | rabbit party joke | animals
j: why are rabbits so good at parties? | why does everyone invite the rabbits?
p: they know how to hop in | they're always hopping around

# horse_neighbour | horse joke | animals
j: what do you call a horse that lives next door? | what's the horse next door called?
p: a neigh-bour | your neighbour. neigh-bour

# goat_kid | goat joke | animals
j: why are goats so funny? | why do goats make everyone laugh?
p: they're always kidding

# dolphin_fluke | dolphin treasure joke | ocean
j: how did the dolphin find the treasure? | why did the dolphin find all that treasure?
p: total fluke | it was a fluke

# puffer_mood | pufferfish joke | ocean
j: why are pufferfish so moody? | why is the pufferfish always upset?
p: they puff up at everything | they get all puffed up

# bat_hang | bat joke | caves
j: why do bats love caves so much? | what do bats do all day?
p: they just hang around | they hang around

# silverfish_cold | silverfish joke | mining mobs
j: why are silverfish so mean? | why don't silverfish have friends?
p: they're stone cold

# warden_music | warden music joke | caves music
j: what's the warden's favourite music? | what does the warden listen to?
p: silence. absolutely nothing | the sound of nobody walking

# vex_vexing | vex joke | mobs
j: why are vexes so annoying? | what's the deal with vexes?
p: they're just so vexing

# drowned_deep | drowned joke | ocean mobs
j: why are drowned always in trouble? | why do drowned never get a break?
p: they're in too deep

# snow_chill | snow golem joke | mobs weather
j: why is the snow golem so relaxed? | why does the snow golem never stress?
p: it just likes to chill

# bread_loaf | lazy bread joke | food
j: why did the bread stay home today? | why did the bread skip work?
p: it just wanted to loaf around

# toxic_potato | poisonous potato joke | food
j: why does the poisonous potato have no friends? | why is nobody friends with the poisonous potato?
p: it's too toxic

# crumby_cookie | cookie joke | food
j: why was the cookie sad? | why'd the cookie cry?
p: it was feeling crumby

# beet_music | beetroot joke | food music
j: what's a beetroot's favourite music? | what music do beetroots like?
p: anything with a good beet

# pumpkin_patch | pumpkin joke | plants
j: what does a pumpkin do when it gets hurt? | how does a pumpkin fix a cut?
p: it puts on a patch. a pumpkin patch

# bee_bud | bee and flower joke | animals plants
j: what did the bee say to the flower? | what's the bee tell the flower?
p: hey bud, what's up | hi bud

# fun_guy | mushroom joke | plants
j: why does everyone like the mushroom? | why's the mushroom so popular?
p: he's a fungi | a real fun guy

# lava_water | lava and water joke | danger
j: what happened when the lava met the water? | how did the lava and the water get along?
p: things got rocky | it got rocky fast

# ladder_up | ladder joke | building
j: why did the ladder get a promotion? | why is the ladder so successful?
p: it was always moving up

# mapped_out | map joke | exploring
j: why does the map never get lost? | why is the map so calm all the time?
p: it has everything mapped out

# clock_tocked | clock joke | random
j: why did the clock get sent to its room? | why was the clock in trouble?
p: it tocked back

# chest_trust | chest joke | storage
j: why can you always trust a chest? | why is a chest such a good friend?
p: it keeps everything inside

# barrel_laugh | barrel joke | storage
j: why was the barrel laughing? | why's that barrel so happy?
p: someone told it a barrel of jokes

# hoe_life | hoe joke | farming tools
j: why is the hoe the most laid back tool? | why does the hoe never stress?
p: it just goes with the furrow | it takes life one row at a time

# armor_stand | armor stand joke | armor
j: why is the armor stand so patient? | why doesn't the armor stand ever complain?
p: it'll stand for anything

# cauldron | cauldron joke | crafting
j: why did the cauldron get grounded? | what's the cauldron in trouble for?
p: it was always stirring things up

# lantern | lantern joke | night
j: why is the lantern so smart? | why does everyone ask the lantern for advice?
p: it's very bright

# cobweb | cobweb joke | mobs
j: why does everyone get stuck talking to cobwebs? | why are cobwebs so hard to leave?
p: they're so clingy

# minecart | minecart joke | exploring
j: why was the minecart so good at its job? | why does the minecart always do well?
p: it stays on track | it always stays on track

# emerald_envy | emerald joke | villagers trade
j: why is the emerald so jealous all the time? | why's the emerald always upset?
p: it's green with envy

# obsidian_tough | obsidian joke | mining
j: why does nobody argue with obsidian? | why is obsidian so tough?
p: it's hard to break | it just won't break

# kelp_help | kelp joke | ocean
j: what does seaweed say when it's stuck? | what does kelp shout when it's trapped?
p: kelp! kelp! | kelp me

# hay_bale | hay joke | farming animals
j: why do horses love hay bales so much? | what's a horse's favourite block?
p: it's the main course. hay! | hay, of course

# cake_layers | cake joke | food
j: why did the cake go to the doctor? | why was the cake feeling bad?
p: it felt crummy | it was feeling a bit crummy

# note_block | note block joke | music
j: why is the note block so good at its job? | what's the note block's secret?
p: it always hits the right note

# ice_cool | ice joke | weather
j: why is packed ice so popular? | why does everyone like ice blocks?
p: they're just really cool

# bell_ring | bell joke | villagers town
j: why did the bell get so famous? | why does everyone know the village bell?
p: its name rings a bell

# spider_eight | spider joke | mobs
j: why are spiders so good at tag? | why can't you win tag against a spider?
p: they've got eight legs on you

# moss_carpet | moss joke | plants
j: why is moss so laid back? | why is moss never in a hurry?
p: it just likes to grow on you | it grows on you
"""

KNOCK = """
# lettuce | lettuce knock knock | jokes food
n: lettuce
p: lettuce in, there's a creeper out here

# creeper | creeper knock knock | mobs
n: creeper
p: ssssss. you shouldn't have opened the door | creeper. ssssss. run

# boo | boo knock knock | random
n: boo
p: don't cry, it's just a joke

# cows_go | cows go knock knock | animals
n: cows go
who: cows go who? | cows go who
p: no, cows go moo | cows don't go who, they go moo

# interrupting_cow | interrupting cow knock knock | animals
n: interrupting cow
who: interrupting cow wh- | interrupting cow w-
p: moo! | moooo!

# lava | lava knock knock | danger
n: lava
p: lava you, open up | lava you too much to stay out here

# wool | wool knock knock | animals
n: wool
p: wool you let me in already? | wool you open the door please

# honey_bee | honey bee knock knock | animals
n: honey bee
p: honey bee a friend and let me in

# ice | ice knock knock | weather
n: ice
p: ice to see you, open up | ice to see you again

# arrow | arrow knock knock | weapons
n: arrow
p: arrow you gonna let me in or not? | arrow you going to open up?

# moss | moss knock knock | plants
n: moss
p: moss be my lucky day, you're home

# gold | gold knock knock | ore
n: gold
p: gold on, i forgot the joke | gold on, i'm still thinking

# kelp | kelp knock knock | ocean
n: kelp
p: kelp me, i'm locked out! | kelp! open the door

# witch | witch knock knock | mobs
n: witch
p: witch one of you is gonna let me in? | witch way is the bed

# beets | beets knock knock | food
n: beets
p: beets me, i forgot the rest | beets me

# wither | wither knock knock | mobs
n: wither
p: wither you like it or not, i'm coming in

# boat | boat knock knock | ocean
n: boat
p: boat time you opened the door | boat time, it's cold out here
"""

# ---------------------------------------------------------------------------------------------------------------------
# 4. Random thoughts. t (the thought: a clause, or a question ending in ?), b (b reacts), e (a goes on), c (b sums up).

THOUGHTS = """
# cows_know | cows knowing they're cows | animals
t: do cows know they're cows?
b: no way, they just moo | they know. look at their faces | i hope not, honestly
e: like does a cow look at another cow and think, that's me | imagine being a cow and knowing it
c: now i feel weird about steak

# dungeon_chests | who fills dungeon chests | structures
t: who actually puts the chests in dungeons?
b: the zombies, obviously | someone with a lot of free time | the spawner does it, probably
e: someone filled a chest with saddles and just left | who's restocking these, that's what i want to know

# bucket_ocean | water buckets and the ocean | ocean
t: when you scoop up water, the ocean never gets any lower
b: magic | the ocean's just that big | infinite water, don't question it
e: so where's the bucket of water coming from | two buckets make infinite water. that's not how water works

# lava_bucket | lava buckets | danger
t: how does an iron bucket hold lava without melting?
b: really good iron | don't ask questions, it works | the bucket's just tough
e: lava melts everything but a bucket's fine? | it's the strongest bucket ever made

# pockets | big pockets | storage
t: we carry a whole stack of logs in one pocket
b: big pockets | my pockets are full of cobble right now | don't think about it
e: sixty four logs, one pocket | where does it all go

# punch_trees | punching trees | trees
t: we punch trees with our bare hands to get wood
b: it works though | that's how everyone starts | my hands hurt thinking about it
e: and the log just pops out | like, punching a tree. that's step one of everything

# floating_trees | floating treetops | trees
t: if you cut the bottom of a tree, the top just floats there
b: the tree took a day off | that's why there's floating trees everywhere | lol the leaves don't care
e: the whole top, hovering | someone left one floating by the river, it's still there

# spare_bones | skeleton bones | mobs jokes
t: skeletons drop bones. they're carrying their own spare parts
b: lol | that's grim | spare bones for later
e: like they've got a backup arm in their pocket | where do they keep them, they're all bones

# cake_bites | eating a whole cake | food
t: you can eat a whole cake in seven bites
b: big bites | i've done it | that's a lot of cake
e: seven bites and a whole cake is gone | nobody should eat cake that fast

# chicken_eggs | endless eggs | animals
t: chickens lay an egg every few minutes
b: busy chickens | that's a lot of eggs | where do they all go
e: my farm makes more eggs than i could ever throw | they never stop

# cooked_cows | cows and fire | animals food
t: if a cow dies in fire it drops cooked steak
b: efficient | that's dark | free cooking
e: so fire cooks them on the spot | i've seen it happen by a lava pool

# enderman_dirt | endermen and dirt | mobs
t: what do endermen even want with our dirt?
b: they're building something | they're collectors | they just like dirt
e: maybe there's a secret enderman house made of stolen blocks | they always take the one block you need

# creepers_know | creepers knowing | mobs
t: do creepers know they're going to explode?
b: they have to know | no, they're surprised every time | that's sad if they don't
e: imagine hissing and not knowing why | it's their one move, they must know

# respawn_us | respawning | death
t: when we respawn, is it still us?
b: woah | it's us, we remember stuff | don't, i'm scared now
e: like do we come back or is it a new us with our memories | i've respawned a lot, am i still the first me
c: i'm not thinking about that one

# emerald_miners | who mines emeralds | villagers trade
t: villagers pay in emeralds but who mines the emeralds for them?
b: us, basically | the nitwits | they find them in the mountains
e: emeralds are up in the mountains and i've never seen a villager there | the whole thing runs on us

# bread_wheat | three wheat bread | food
t: bread only needs three wheat. no oven, nothing
b: crafting magic | bread is easy | i love that about bread
e: you just put three wheat in a row and it's bread | no water, no fire, just bread

# wool_grass | sheep and grass | animals
t: sheep grow their wool back by eating grass
b: grass makes wool | that's how it works | sheep logic
e: so grass turns into wool somehow | the field is basically a wool farm

# bed_home | beds and home | home sleep
t: your bed sets your spawn, so home is wherever your bed is
b: that's kinda sweet | deep | true though
e: home is just a bed and a spot | you could make anywhere home with one bed

# stars_real | the stars | sky night
t: are the stars in the sky real, or just painted on?
b: they're real, they move | painted, definitely | i never looked that close
e: they all turn with the sky at night | nobody's ever flown up to check

# under_bedrock | under bedrock | mining
t: what's under bedrock?
b: the void | nothing | more bedrock | don't look
e: everyone says the void, but who's been | there's gotta be something down there

# the_void | the void | danger
t: where do things go when they fall into the void?
b: nowhere | gone forever | another world maybe
e: just falling forever | i dropped a pickaxe in the void once and i still think about it

# rain_ocean | the rain and the ocean | weather ocean
t: what if the rain is just the ocean coming back down?
b: that's... actually how rain works | deep | huh
e: it goes up, it comes down | the ocean never runs out, so maybe

# snow_golem_desert | snow golems melting | mobs biomes
t: snow golems melt in the desert and nobody talks about it
b: so sad | why would you take one there | rip snowman
e: they just leave a little puddle | they're made to be cold and we drag them everywhere

# trader_llamas | the trader's llamas | trade animals
t: who feeds the wandering trader's llamas?
b: him, i hope | nobody | they eat hay probably
e: they follow him everywhere and never look hungry | those llamas have seen the whole world

# trader_invisible | the trader at night | trade night
t: why does the wandering trader drink invisibility at night?
b: so the zombies don't see him | he's shy | he's hiding from us
e: he just vanishes and his llamas are standing there | smart guy, honestly

# cats_creepers | cats and creepers | pets mobs
t: creepers are scared of cats. a tiny cat
b: cats are scary | respect the cat | small but mighty
e: a whole creeper running from a kitten | the most dangerous mob, scared of a cat

# phantoms_tired | phantoms and sleep | mobs sleep
t: phantoms only come when we don't sleep. they're like tiredness with wings
b: that's poetic | that's creepy | sleep then
e: like the sky is telling you to go to bed | they're the worst bedtime reminder

# forgotten_players | mobs as players | mobs philosophy
t: what if every mob was once someone like us who forgot?
b: stop | that's creepy | no, i don't like that
e: like a zombie that used to mine | imagine forgetting and just groaning forever
c: ok i need a minute

# door_ways | mobs and doors | mobs building
t: zombies break doors, villagers open them, creepers don't even try
b: creepers just knock with their face | lol | doors are confusing
e: everybody has their own way with a door | the creeper's way is the worst

# ladders_walls | ladders | building
t: a ladder doesn't need anything but a wall
b: ladder magic | it's stuck to the wall | true
e: no legs, nothing, just a wall | you could climb the sky with a tall enough wall

# torches_forever | torches and furnaces | crafting night
t: torches never burn out, but furnaces need fuel
b: hmm | torches are special | don't tell the furnace
e: a torch burns forever on a stick and a bit of coal | the furnace is getting ripped off

# wood_math | planks and sticks | crafting trees
t: two planks make four sticks
b: wood math | that's a good deal | where do the extra come from
e: so wood makes more wood | one log is four planks is eight sticks

# water_landing | landing in water | danger
t: you can fall from any height into one block of water and be fine
b: water's a cushion | it's a lifesaver | true, learned that the hard way
e: one block deep and you survive a cliff | water is the real hero

# world_sleeps | the world at night | sleep
t: when we sleep, does the world keep going without us?
b: the night just skips | it waits for us | spooky
e: where does the night go | everyone else is awake and we just skip

# end_no_time | time in the end | end
t: there's no day or night in the end
b: it's always weird there | time stops | creepy
e: clocks just spin around there | imagine living with no sunrise

# nether_no_rain | rain in the nether | nether weather
t: it never rains in the nether
b: too hot | that's why they're all grumpy | no water, no rain
e: the rain just doesn't make it down there | no weather at all, ever

# lonely_dragon | the lonely dragon | end
t: is the ender dragon lonely?
b: probably | it has the endermen | it's just circling all alone
e: it's out there with nobody to talk to | flying circles forever

# villagers_before | villagers before us | villagers
t: what were villagers doing before we showed up?
b: farming | saying hm | the same as now
e: they've got houses and jobs and nobody to trade with | i bet they were bored

# village_names | names of villages | villagers town
t: who names the villages?
b: nobody, they don't have names | we do | the villagers, secretly
e: they never tell us | we just call it the village by the river

# wolves_sit | wolves sitting | pets
t: wolves sit when we tell them, and nobody ever trained them
b: they're smart | good dogs | they just get it
e: one bone and they understand everything | best behaved animal there is

# golem_poppies | golems and poppies | villagers
t: iron golems give villagers poppies. that's so sweet
b: aww | big softie | they're just big friends
e: a huge iron guy handing out flowers | they throw zombies and then give flowers

# slime_split | splitting slimes | mobs
t: when a slime splits, is it still the same slime?
b: it's four slimes now | it's a family | deep
e: does it remember being big | they just keep getting smaller

# bee_sting | bee stings | animals
t: bees die after they sting you
b: so sad | don't make them angry then | worth it for them i guess
e: they give everything for one sting | poor bees

# honey_slime | honey and slime blocks | building
t: honey blocks slow you down but slime blocks bounce you
b: sticky versus bouncy | different jobs | true
e: both are sticky, but one's a trampoline | i walked on honey once and gave up

# compass_spawn | compasses | exploring
t: compasses don't point north, they point to spawn
b: true | wait really | that's useful though
e: so where's north | they just point home, sort of

# nether_clocks | clocks in the nether | nether
t: clocks only work up here, they spin like crazy in the nether
b: true | they get confused | makes sense
e: the nether has no sun, so no time | i tried once, it just spun

# water_bubbles | bubbles underwater | ocean
t: where's the air in underwater bubbles coming from?
b: soul sand | the ocean floor | magic
e: you swim through and get your breath back | free air, nobody asks

# fish_bucket | fish in buckets | ocean animals
t: you can keep a fish in a bucket forever and it's fine
b: happy fish | it's got water | don't ask how
e: it just sits there, happy | i've had one in a bucket for days

# kelp_forest | kelp forests | ocean plants
t: there are whole kelp forests in the ocean and nobody goes there
b: too wet | the drowned go there | i've been, it's nice
e: a whole forest, underwater, empty | could be anything down there

# shipwrecks | shipwrecks | ocean structures
t: whose ships were all those shipwrecks?
b: no idea | someone unlucky | the drowned's maybe
e: there's so many and nobody knows | there's maps inside, so someone sailed them

# monuments | ocean monuments | ocean structures
t: who built the ocean monuments?
b: the guardians | someone very wet | someone a long time ago
e: guardians can't place blocks | there's gold inside, someone was rich

# temple_traps | temple traps | structures
t: who puts a tnt trap in their own temple?
b: someone who hates visitors | the builder was paranoid | protection
e: imagine forgetting where your own trap is | they really didn't want guests

# buried_treasure | buried treasure | exploring ocean
t: who buried all the treasure on the beaches?
b: someone with a map | some sailor | the drowned
e: they made a map and then just left it | they buried a heart of the sea and never came back

# pillager_crossbows | pillager crossbows | mobs
t: where do pillagers get all their crossbows?
b: they make them | they trade for them | outposts
e: they've got a whole outpost and no crafting table that i saw | there's a workshop somewhere

# who_taught | knowing how to craft | crafting
t: who taught us how to craft everything?
b: nobody, we just knew | we figured it out | trial and error
e: we just knew what a crafting table was from day one | i still don't know how i knew

# crafting_grid | crafting grids | crafting
t: the crafting table is three by three, but our pockets are two by two
b: makes sense | bigger table | the table's the upgrade
e: we carry a little crafting grid around | first thing you make is a bigger one

# bonemeal | bone meal farms | farming
t: bone meal comes from skeletons. we grow food with skeletons
b: oh no | circle of life | gross but useful
e: every carrot i eat is a little bit skeleton | we feed our farms with mobs
c: i'll never look at my farm the same

# golden_carrots | golden carrots | food
t: we turn gold into carrots and it's worth it
b: deep | gold's not that useful | true
e: the best food in the game is a carrot wearing gold | nobody ever says that out loud

# oak_apples | apples from oak | trees food
t: only oak trees drop apples
b: and dark oak | it's an oak thing | weird right
e: a birch tree never once gave anyone an apple | spruce, nothing. jungle, nothing

# eating_flesh | eating rotten flesh | food
t: we eat rotten flesh even though it makes us sick
b: when you're desperate | it's food, sort of | not me
e: we know it's bad and we still do it | that's how hungry we get

# spider_eyes | spider eyes as food | food mobs
t: who decided spider eyes were food?
b: someone very hungry | nobody, it's a dare | not me
e: someone looked at a spider eye and thought, lunch | i tried one once

# sugar_cane | sugar cane | plants crafting
t: sugar cane makes both sugar and paper
b: useful plant | true | weird combo
e: cake and books from the same plant | the most useful reed there is

# whispering_books | bookshelves | enchanting
t: bookshelves make enchanting stronger. the books are whispering
b: creepy | that's how it works | you can see the letters fly
e: little letters float from the books to the table | the books are helping, somehow

# furnace_xp | furnace experience | crafting
t: you get experience from smelting stuff
b: learning | furnace wisdom | nice bonus
e: you cook a potato and you're wiser | every furnace is a little school

# mending_hungry | mending | enchanting gear
t: mending means your gear eats experience
b: hungry gear | it's a fair trade | true
e: my pickaxe is hungrier than me | the gear grows up with you

# world_blocks | how many blocks | mining
t: there are more blocks out there than anyone could ever mine
b: challenge accepted | so many | we'll never run out
e: we could mine forever and never finish | and somehow i still run out of iron

# plant_trees | replanting trees | trees
t: we should plant a tree for every one we cut
b: i do | nobody does | good rule
e: imagine the forests then | i've cut so many trees, i owe a forest

# leaves_know | falling leaves | trees
t: leaves fall apart when the tree's gone, like they know
b: they do know | sad | they just give up
e: the log goes and the leaves just quit | it's kinda sad actually

# saplings | saplings from leaves | trees
t: leaves drop saplings, so a tree plants itself
b: smart tree | circle of life | that's nice
e: every tree is a little seed bag | the forest grows itself back

# sleep_rain | sleeping through rain | weather sleep
t: if you sleep through rain, it stops
b: really? | sleep fixes everything | i've noticed that
e: just go to bed and it's sunny | sleep is the best weather tool

# lightning_pigs | lightning and pigs | weather animals
t: lightning turns pigs into zombie piglins
b: poor pigs | lightning is wild | that's terrifying
e: one strike and a pig becomes something else | the sky picks who you become

# lightning_witches | lightning and villagers | weather villagers
t: lightning turns villagers into witches
b: that explains the witches | so they were villagers | oh no
e: every witch was a farmer once | one bad storm and you're a witch

# endermen_build | endermen as builders | mobs building
t: endermen pick up and place blocks, so they're builders too
b: bad builders | they place stuff randomly | true
e: they're the only other ones who build | their style is just chaos

# chicken_jockey | chicken jockeys | mobs jokes
t: when a baby zombie rides a chicken, who's steering?
b: the chicken, clearly | the zombie | nobody
e: the chicken just goes wherever | it's chaos on two legs

# spider_jockey | spider jockeys | mobs
t: where are spider jockeys even going?
b: hunting us | nowhere | just riding around
e: a skeleton on a spider, what a team | they look like they're on a mission

# never_older | never getting older | philosophy
t: we never get any older
b: lucky | we just keep going | i feel older though
e: same face, every day | days go by and nothing changes

# cows_eaten | cows we've eaten | food animals
t: how many cows do you think we've eaten?
b: too many | i don't want to know | hundreds
e: if you count all the steak, it's a lot | the cows must hate us

# drowned_sun | drowned in the sun | mobs ocean
t: drowned don't burn in the sun because they're all wet
b: makes sense | soggy zombies | smart
e: they're just zombies who went for a swim | the wet keeps them safe

# biome_mobs | strays and husks | mobs biomes
t: strays are just cold skeletons, husks are sunburnt zombies
b: biome mobs | that's true | so where you live changes you
e: the same mob, just a different place | the world shapes everything

# sleep_monsters | sleeping near monsters | sleep mobs
t: we can't sleep with monsters close, like our bodies just know
b: survival instinct | it's annoying | true
e: there's a zombie somewhere and you just can't nod off | your body knows before you do

# grass_spreads | spreading grass | plants
t: grass spreads onto dirt all by itself
b: sneaky grass | it's alive | that's nice
e: put dirt by grass and wait | grass is always moving, just slowly

# stairs_slabs | stairs and slabs | building
t: six blocks only make four stairs, but three blocks make six slabs
b: stair tax | slabs are the better deal | math
e: stairs are a bad deal | why does a stair cost more than a block

# moon_phases | moon phases | sky night
t: the moon has phases, so something's keeping track
b: the slimes are | it changes every night | i never noticed
e: full moon, half moon, no moon | slimes come out more on a full moon, i heard

# square_sun | the square sun | sky
t: the sun is square. so is the moon. everything's square
b: it's a square world | i like it | true
e: even the clouds are blocks | i wonder if anything's round anywhere

# cloud_blocks | cloud blocks | sky
t: what if the clouds are floating blocks we just can't reach?
b: they kinda are | i want to stand on one | maybe
e: they're so flat on the bottom | somebody pillar up and check

# zombie_helmets | zombies and helmets | mobs
t: zombies with helmets don't burn in the sun. they know
b: smart zombies | they plan ahead | that's scary
e: so one zombie figured out helmets | they're learning

# no_thirst | never thirsty | food
t: we never get thirsty
b: just hungry | lucky | water's for buckets
e: we drink milk to fix poison, but never water | nobody ever drinks from the river

# pumpkin_faces | pumpkin faces | plants
t: carved pumpkins have faces, so who carves all of them?
b: we do | nobody, they grow plain | the wind
e: every face on a pumpkin is someone's work | we're all little pumpkin artists

# snowman_face | snow golem faces | mobs
t: a snow golem without its pumpkin has a face underneath
b: creepy | it's a little face | i saw it once
e: shear the pumpkin off and there's a face | it was in there the whole time

# horse_jump | horse jumping | animals
t: horses jump better than we do
b: way better | that's their job | showoffs
e: a horse clears a fence like it's nothing | we're stuck at one block

# armor_stand | armor stands | armor
t: an armor stand is just a person who isn't there
b: creepy | it's just a stand | it's a fashion dummy
e: it wears your gear better than you | sometimes i think it's watching me

# paintings | who painted paintings | decor
t: who painted all the paintings?
b: someone talented | a villager | nobody knows
e: you hang one and it's a random picture | there's a painting of a skull, who did that

# music_discs | music discs | music
t: who made the music discs?
b: a creeper and a skeleton, together | nobody knows | someone musical
e: you get some when a skeleton shoots a creeper | that's a weird way to get music

# map_frame | maps in frames | exploring
t: a map in an item frame shows where it is. it's a map of the map
b: whoa | map inside a map | true
e: you can see the frame on the map | the map knows where it hangs

# unread_books | unread books | crafting
t: we can write books but nobody reads them
b: i read them | i've never written one | sad
e: all those books in chests, unread | i should write one about my mine

# cave_drips | cave sounds | caves
t: caves are so quiet you can hear water from far away
b: that's how i find lava | peaceful | creepy
e: drip, drip, drip | you can hear the whole cave breathing

# purple_portal | purple portals | nether
t: why is a nether portal purple?
b: it's pretty | magic | it's the nether's colour
e: the nether is red but its door is purple | someone picked that on purpose

# same_sunrise | the sunrise | sky day
t: the sun comes up in the same spot every day
b: reliable | that's how you find east | true
e: it never misses | the sun is the most dependable thing here

# river_end | where rivers go | biomes
t: every river ends somewhere
b: the ocean | a lake | rivers don't really end
e: i followed one once until it was just ocean | where does the river water come from

# cold_peaks | cold mountains | biomes weather
t: the higher you go, the colder it gets
b: snow on the peaks | true | mountains are cold
e: it starts snowing on the tops of mountains | the sky is cold, apparently

# dark_spawns | mobs in the dark | mobs night
t: mobs only spawn in the dark. the dark makes them
b: that's why torches matter | scary | true
e: no light, and they just appear | the dark is the real enemy

# village_kids | village kids | villagers
t: baby villagers run around the village all day playing
b: they're cute | they never stop | they play tag
e: they're the only ones having fun in the village | the kids run the place

# scared_villagers | scared villagers | villagers
t: villagers make iron golems when they're scared
b: really? | that's sweet | they panic and a golem shows up
e: they get scared enough and suddenly there's a bodyguard | a scared village makes its own hero

# axolotl_dead | axolotls playing dead | animals
t: axolotls play dead when they're hurt
b: smart | sneaky | adorable
e: they just float there pretending | best trick in the ocean

# turtle_home | turtles going home | animals ocean
t: turtles always go back to the beach they were born on
b: aww | that's loyal | how do they remember
e: they swim everywhere and still find home | wish i had that sense of direction

# frog_colours | frog colours | animals
t: frogs change colour depending on where they grew up
b: cool | i didn't know that | so many frogs
e: a cold frog and a warm frog look totally different | the place makes the frog

# panda_moods | panda moods | animals feelings
t: pandas have moods like us. lazy, worried, playful
b: same | i'm a lazy panda | they're relatable
e: some pandas sneeze, some sulk | a panda's just a big us

# screaming_goats | screaming goats | animals
t: some goats scream
b: why | i've heard one | it's terrifying
e: you're on a mountain and a goat just yells | there's screaming goats out there

# cat_gifts | cat gifts | pets
t: cats bring you gifts while you sleep
b: aww | it's usually a feather | they love us
e: wake up and there's a rabbit's foot on the floor | it's their way of saying thanks

# parrot_dance | dancing parrots | pets music
t: parrots dance when music plays
b: cute | i've seen it | they have rhythm
e: put on a disc and they go wild | the only mob with taste in music

# dolphin_treasure | dolphins and treasure | ocean animals
t: dolphins lead you to treasure if you feed them
b: smart dolphins | i love dolphins | true
e: feed one a fish and it shows you a shipwreck | best deal in the ocean

# trader_leaves | where the trader goes | trade
t: where does the wandering trader go when he leaves?
b: somewhere else | he just vanishes | another village
e: one day he's here, next day gone | he's seen more of the world than anyone

# lantern_torch | lanterns | crafting night
t: a lantern is just a torch inside a little iron cage
b: fancy torch | true | it's a torch house
e: a torch with a roof, basically | we put our torches in tiny houses

# stone_maker | water and lava stone | mining
t: water on lava makes stone forever
b: free stone | cobble magic | that's how i get stone
e: so the world makes stone out of nothing | infinite stone, just add water

# obsidian_couple | obsidian from water and lava | mining
t: water on still lava makes obsidian, the toughest block there is
b: the strongest block from two liquids | wild | that's how portals happen
e: the two things that hate each other make the toughest block | lava and water, power couple

# endless_water | infinite water | ocean
t: two buckets of water make an infinite water source
b: don't question it | the best trick | free water
e: one bucket in one corner, one in the other, and it never runs out | that's free water forever

# cooked_sand | glass from sand | crafting
t: glass is just cooked sand
b: true | cooked sand | i never thought about it
e: put sand in a furnace and you can see through it | every window was a beach once

# river_bricks | bricks from clay | building
t: bricks come from clay at the bottom of rivers
b: true | you dig it out of the water | nice
e: every brick house came from a riverbed | the rivers built our houses

# cow_books | books and cows | crafting animals
t: a book is paper and leather. so, cows and reeds
b: cows are everywhere | literally | wow
e: every book has a bit of cow in it | the library is a cow and reed museum

# wearing_cows | leather armor | armor animals
t: leather armor is just cow
b: wearing cows | gross when you put it that way | true
e: we wear the cow and eat the cow | cows give us everything

# sheep_beds | wool beds | sleep animals
t: we sleep on sheep every night
b: technically | wool beds, yeah | comfy sheep
e: three wool and a few planks | every bed is three sheep's worth of fluff

# bone_dogs | taming with bones | pets mobs
t: we tame wolves with skeleton bones
b: grim | it works though | dogs love bones
e: a skeleton goes down, a dog gets a home | everything gets used here

# spider_bows | spider string bows | weapons mobs
t: bows are made with spider string
b: true | spiders help us hunt | ironic
e: the spider helps you shoot the skeleton | we're using the mobs against each other

# creeper_fireworks | fireworks from creepers | mobs
t: fireworks come from creepers
b: creeper parties | they're pretty at least | true
e: creepers blow us up and then we make fireworks out of them | the best revenge

# egg_odds | thrown eggs | animals
t: most eggs you throw never hatch
b: lucky chickens | the odds are bad | i've hatched a few
e: you throw like twenty and one chick pops out | every egg is a little gamble

# melon_slices | melons | food plants
t: melons grow as blocks but break into slices
b: weird fruit | that's handy | true
e: you can't eat the block, only the slices | a melon is a slice factory

# snow_layers | snow layers | weather
t: snow piles up in layers on the ground
b: so pretty | sometimes it's deep | cozy
e: it gets thicker and thicker | you can walk on deep snow and not sink

# hungry_moss | spreading moss | plants
t: moss eats stone if you bone meal it
b: moss is hungry | wild | that's a good trick
e: it just takes over everything | give it bone meal and it's everywhere

# sculk_ears | the deep dark | caves
t: the floor in the deep dark can hear you
b: creepy | that's why you sneak | i don't go there
e: the floor is listening | every step echoes somewhere

# spawner_makers | who makes spawners | mobs structures
t: who made all the mob spawners?
b: nobody knows | they're just there | someone evil
e: little cages that make monsters forever | someone built those on purpose

# deep_strongholds | stronghold builders | structures
t: who built the strongholds, and why so deep?
b: someone hiding the portal | someone long ago | silverfish?
e: a whole castle underground around one portal | they really didn't want anyone to find it

# hay_falls | falling on hay | farming danger
t: landing on a hay bale hurts way less
b: true | soft landing | farmers know
e: jump off a cliff onto hay and you're mostly fine | hay is underrated

# ice_boats | boats on ice | exploring
t: boats on ice are faster than anything
b: fastest thing ever | i've raced on ice | true
e: ice roads are the secret | faster than a horse, in a boat, on land

# pig_minecart | pigs in minecarts | animals jokes
t: you can put a pig in a minecart and it just rides
b: lol | the pig's happy | best passenger
e: pigs riding the rails | a pig has seen more of the rails than me

# note_floor | note blocks | music
t: note blocks sound different depending on the block under them
b: cool | i didn't know that | music is complicated
e: put one on wood and it's a bass | the floor picks the instrument
"""

# ---------------------------------------------------------------------------------------------------------------------
# 5. Favourites and lists. q (b's answer comes with it: ans), a (a's own), b (b on a's pick).

FAVS = """
# block | favourite block | building
q: what's your favourite block? | fav block, go
ans: deepslate tiles | oak logs, classic | glass | moss, it's so soft looking | spruce planks, warm
a: mine's stone bricks, they go with everything | i like lanterns, does that count | mossy cobble, it looks old
b: lanterns count | mossy cobble is underrated

# mob | favourite mob | mobs
q: what's your favourite mob? | fav mob?
ans: the axolotl | wolves, obviously | the iron golem | the fox | bees
a: mine's the allay, it brings you stuff | creepers, weirdly | the goat, it's chaos
b: creepers, really? | goats are pure chaos

# food | favourite food | food
q: what's your favourite food? | best food, what's yours?
ans: steak | golden carrots | pumpkin pie | bread, i'm simple | mushroom stew
a: baked potatoes for me | cookies, even though they're useless | rabbit stew when i can be bothered
b: baked potatoes are so good | cookies, the snack king

# biome | favourite biome | biomes
q: what's your favourite biome? | fav biome?
ans: cherry grove | plains, easy living | the jungle | snowy taiga | mushroom fields
a: i love a birch forest | dark forest, moody | the badlands, all the colours
b: the badlands are so pretty | dark forest is creepy though

# tool | favourite tool | tools
q: what's your favourite tool? | which tool do you like using most?
ans: pickaxe, always | the axe | shears, for some reason | fishing rod | shovel, it's so fast
a: mine's the axe, chopping is relaxing | the hoe, i'm a farmer at heart | flint and steel, for the drama
b: flint and steel, careful | chopping is relaxing, true

# time_of_day | favourite time of day | day night
q: what's your favourite time of day? | best time of day?
ans: sunrise | midday, safe and bright | sunset, the colours | night, honestly | early morning
a: mine's the bit right before sunset | sunrise, the zombies burning | night, when it's quiet inside
b: right before sunset is perfect | zombies burning, lol

# weather | favourite weather | weather
q: what's your favourite weather? | sun, rain or storm, what's your favourite?
ans: sunny, obviously | rain, it's cozy | thunderstorms, dramatic | snow | clear nights
a: light rain while i'm inside | a clear sunny morning | the bit after a storm when it clears
b: rain while inside is the best | after a storm, yeah

# sound | favourite sound | music
q: what's your favourite sound in the game? | best sound, go
ans: rain on a roof | a furnace crackling | a cat purring | the ding when you level up | water dripping in a cave
a: mine's the sound of a chest opening with loot | the pop of picking stuff up | a door shutting before night
b: the chest sound is so good | a door shutting at night, safe

# smell | best smell | random
q: if this world had smells, what would be the best one? | best smell, if things here had a smell?
ans: fresh bread | rain on grass | a campfire | pine trees | baked potatoes
a: mine's a cake right out of the oven | the forest after rain | a smoker full of steak
b: forest after rain, perfect | steak, obviously

# animal | favourite animal | animals
q: what's your favourite animal? | fav animal?
ans: cats | horses | pandas | dolphins | rabbits
a: mine's the turtle, slow and steady | foxes, sneaky little things | frogs, they're so funny
b: frogs are funny | foxes are cute

# flower | favourite flower | plants
q: what's your favourite flower? | which flower do you like most?
ans: poppy | cornflower | sunflower | lily of the valley | the pink tulip
a: mine's the blue orchid | dandelions, i'm simple | the allium, it's purple
b: blue orchids are rare | dandelions, humble

# wood | favourite wood | trees building
q: what's your favourite wood? | fav wood type?
ans: spruce | dark oak | cherry | birch | oak, classic
a: mine's spruce, it goes with everything | jungle, nobody uses it | mangrove, the red one
b: jungle planks are underrated | mangrove's a nice red

# ore | favourite ore | ore mining
q: what's your favourite ore to find? | best ore to find?
ans: diamonds, duh | iron, it's useful | gold, shiny | lapis, so blue | emeralds, they're rare
a: mine's coal, honestly, it means torches | copper, it's everywhere and i love it | redstone, it lights up
b: coal is the real hero | redstone lighting up is nice

# structure | favourite structure | structures
q: what's your favourite structure to find? | best structure to stumble on?
ans: a village | a shipwreck | a desert temple | a mineshaft | a ruined portal
a: mine's an igloo, the secret basement | a woodland mansion, so many rooms | trail ruins, digging for stuff
b: igloo basements are wild | mansions are scary

# wool_colour | favourite wool colour | decor
q: what's your favourite wool colour? | fav colour of wool?
ans: light blue | red | black | lime | white, it's clean
a: mine's cyan | orange, it's warm | purple, always purple
b: cyan's a good one | purple, nice

# pet | best pet | pets
q: what's the best pet? | best pet, what do you think?
ans: a wolf | a cat | a parrot | an axolotl in a bucket | a horse
a: mine's the cat, creepers stay away | a parrot, it talks back | a wolf, loyal forever
b: cats for creeper safety, smart | parrots never shut up

# best_view | best view | biomes sky
q: what's the best view you've ever seen out here? | best view you've found?
ans: sunset from a mountain | a huge ravine | the ocean at night | a cherry grove in the morning | a lava lake from above
a: mine was a waterfall in a lush cave | the badlands at sunrise | the stars from a pillar
b: lush cave waterfalls are so pretty | stars from a pillar, nice

# build_favourite | favourite thing to build | building
q: what's your favourite thing to build? | what do you love building most?
ans: houses | bridges | towers | farms | little gardens
a: mine's roofs, weirdly | big doors | stairs that go somewhere nice
b: roofs are hard, respect | stairs that go somewhere, i like that

# travel | favourite way to travel | exploring
q: what's your favourite way to get around? | how do you like to travel?
ans: horse | boat | walking, slow is fine | minecart | sprinting everywhere
a: mine's boats, rivers go everywhere | walking, i like to look around | pigs. don't judge
b: pigs, lol | boats are calm

# villager_job | favourite villager job | villagers
q: what's your favourite villager job? | best kind of villager?
ans: librarian | farmer | fisherman | cartographer | the cleric
a: mine's the shepherd, all the wool | the fletcher, cheap arrows | the toolsmith, good pickaxes
b: fletchers are great | shepherds are underrated

# comfort_food | comfort food | food feelings
q: what's your comfort food? | what do you eat when you've had a bad day?
ans: mushroom stew | bread, warm | cake | baked potatoes | cookies
a: mine's pumpkin pie | a whole steak, sitting down | honey, straight from the bottle
b: pumpkin pie is perfect | honey from the bottle, lol

# night_thing | favourite thing to do at night | night
q: what's your favourite thing to do at night? | what do you like doing at night?
ans: sleep | mine | sort my chests | sit by a fire | watch the moon
a: mine's crafting stuff inside | fishing by torchlight | mining, it's safer underground
b: fishing at night, peaceful | crafting inside, cozy

# mob_drop | favourite mob drop | mobs
q: what's your favourite mob drop? | best thing a mob drops?
ans: ender pearls | string | gunpowder | bones, for dogs | slime balls
a: mine's the totem from evokers | feathers, for arrows | wool from sheep, if that counts
b: totems are the best | feathers, practical

# light_source | favourite light | night decor
q: what's your favourite light source? | best light, torch or something else?
ans: lanterns | torches, classic | glowstone | sea lanterns | candles
a: mine's campfires, cozy | jack o lanterns | shroomlights, they glow orange
b: jack o lanterns are cute | campfires, cozy

# stairs | favourite stairs | building
q: what's your favourite stair block? | best stairs?
ans: stone brick | spruce | cobble | dark oak | sandstone
a: mine's deepslate tile stairs | cherry stairs, so pink | mud brick stairs
b: deepslate tile stairs look clean | cherry, so pink

# door | favourite door | building
q: what's your favourite door? | best door?
ans: spruce | dark oak | iron, safe | jungle, the little windows | birch
a: mine's the crimson door | oak, simple | mangrove, it's fancy
b: crimson doors are cool | oak, simple and fine

# sunset_spot | favourite sunset spot | sky
q: where's your favourite spot to watch the sunset? | best place for a sunset?
ans: on a mountain | by the ocean | on my roof | a hill near town | a beach
a: mine's the top of my tower | a little island in a lake | the edge of a cliff
b: a little island, cozy | a cliff edge, careful

# rainy_day | rainy day plans | weather
q: what's your favourite thing to do on a rainy day? | rainy day, what are you doing?
ans: fishing | sorting chests | sleeping | building inside | listening to it
a: mine's sitting by the window | fishing, it's faster in the rain | smelting stuff
b: by the window, cozy | smelting while it rains, productive

# top3_blocks | top 3 blocks | building
q: top 3 blocks, go | name your top three blocks
ans: glass, oak logs, stone bricks | deepslate, spruce, lanterns | moss, cobble, glass | quartz, dark oak, glowstone | sandstone, terracotta, birch
a: mine's spruce, stone bricks, lanterns | cobble, glass, and dirt. dirt counts | deepslate tiles, cherry, copper
b: dirt counts, sure | copper is a great pick

# top3_mobs | top 3 mobs | mobs
q: top 3 mobs, go | name your three favourite mobs
ans: wolves, cats, axolotls | iron golems, foxes, bees | creepers, endermen, slimes | parrots, horses, dolphins | goats, frogs, pandas
a: mine's foxes, bees, and sniffers | allays, cats, horses | turtles, goats, llamas
b: llamas, really? | sniffers, nice

# top3_foods | top 3 foods | food
q: top 3 foods, go | three best foods
ans: steak, bread, golden carrots | pie, cake, cookies | stew, potatoes, porkchops | carrots, apples, melon | fish, kelp, bread
a: mine's steak, baked potatoes, and pumpkin pie | rabbit stew, honey, bread | golden carrots, steak, cake
b: rabbit stew, a classic | solid three

# top3_biomes | top 3 biomes | biomes
q: top 3 biomes, go | three favourite biomes
ans: plains, forest, taiga | jungle, desert, ocean | cherry grove, meadow, beach | badlands, swamp, snow | mushroom, lush caves, savanna
a: mine's meadow, birch forest, and beach | dark forest, taiga, mountains | savanna, desert, badlands
b: hot biomes, you like it warm | meadow's so nice

# worst_mob | worst mob | mobs complaints
q: what's the worst mob? | worst mob in the game, go
ans: phantoms | creepers | baby zombies | silverfish | drowned with tridents
a: mine's the witch, potions everywhere | endermites, nobody likes them | cave spiders, poison and tiny
b: cave spiders are the worst | witches are so annoying

# worst_food | worst food | food complaints
q: what's the worst food? | worst thing to eat?
ans: rotten flesh | spider eyes | pufferfish | poisonous potatoes | dried kelp
a: mine's beetroot, it does nothing | raw chicken, never again | suspicious stew, you never know
b: suspicious stew is a gamble | beetroot does nothing, true

# worst_block_mine | worst block to mine | mining complaints
q: what's the worst block to mine? | most annoying block to dig?
ans: gravel | obsidian | sand | deepslate | cobwebs
a: mine's netherrack, it's everywhere | bedrock, joke, you can't | clay, i always forget a shovel
b: forgetting the shovel, classic | bedrock, lol

# worst_sound | worst sound | mobs complaints
q: what's the worst sound in the game? | most stressful sound?
ans: a creeper hiss | a ghast scream | phantom screeches | the warden heartbeat | a zombie at your door
a: mine's lava popping near me | the cave noise when you're alone | a skeleton's bow being pulled
b: cave noises, ugh | lava popping, scary

# worst_biome | worst biome | biomes complaints
q: what's the worst biome? | worst place to live?
ans: the swamp | the desert | deep ocean | the jungle | snowy slopes
a: mine's the dark forest, too many mobs | stony peaks, nothing grows | a mangrove swamp, the roots
b: mangrove roots are a maze | dark forest at night, nope

# worst_death | worst way to die | death complaints
q: what's the worst way to die? | worst death you can have?
ans: lava, you lose everything | fall damage, so embarrassing | drowning | a creeper at your own door | suffocating in gravel
a: mine's dying with a full inventory far from home | in the void, nothing comes back | to your own trap
b: dying to your own trap, oof | in the void, gone forever

# annoying | most annoying thing | complaints
q: what's the most annoying thing out here? | biggest everyday annoyance?
ans: full inventory | running out of torches | gravel falling | getting lost | phantoms
a: mine's when your tool breaks right before the block | slipping off a ladder | when a chicken walks into your house
b: the tool breaking, every time | chickens in the house, lol

# best_feeling | best feeling | feelings
q: what's the best feeling out here? | best feeling ever?
ans: finding diamonds | finishing a build | sleeping after a long night | a full chest | making it home at dusk
a: mine's the first night in a new house | eating when you're starving | seeing torches on the way home
b: torches on the way home, yes | a first night in a new house, cozy

# underground_kit | top 3 things for underground | mining caves
q: top 3 things to bring underground? | three things you always take into caves?
ans: torches, food, pickaxe | water bucket, torches, blocks | food, sword, torches | pick, shovel, torches | bed, food, torches
a: mine's torches, a water bucket, and bread | a spare pickaxe, torches, steak | torches, cobble, and a shield
b: water bucket, smart | a spare pickaxe, always

# top3_woods | top 3 woods | trees building
q: top 3 woods? | name three woods you'd build with
ans: spruce, oak, dark oak | birch, cherry, jungle | mangrove, spruce, oak | dark oak, crimson, birch | acacia, spruce, oak
a: mine's spruce, dark oak, and birch | oak, cherry, warped | acacia, jungle, mangrove
b: acacia, brave | spruce is always there

# useless_items | most useless items | crafting jokes
q: top 3 most useless items? | three useless items, go
ans: beetroot, poisonous potatoes, wooden swords | sticks, dead bushes, seeds | gold swords, rotten flesh, kelp | stone hoes, paper, clay | flower pots, cookies, boats
a: mine's the golden hoe, the dead bush, and dirt | the wooden shovel, beetroot seeds, books | stone buttons, honestly
b: dirt's not useless, it's a house | books are useful though

# favourite_tree | favourite tree | trees
q: what's your favourite tree? | best tree?
ans: big oak | cherry | birch | jungle tree, it's huge | spruce
a: mine's the dark oak, it's chunky | a mangrove, so twisty | the acacia, it's sideways
b: dark oak is chunky, true | acacias are weird

# colour | favourite colour | random
q: what's your favourite colour? | fav colour?
ans: blue | green | red | black | purple
a: mine's orange, like a sunset | lime green | pink, don't judge
b: pink is great | orange like a sunset, nice

# enchant | favourite enchantment | enchanting
q: what's your favourite enchantment? | best enchant?
ans: mending | efficiency | fortune | silk touch | feather falling
a: mine's unbreaking, boring but good | looting, more drops | respiration, i love the ocean
b: looting is so good | respiration, nice

# potion | favourite potion | potions
q: what's your favourite potion? | best potion?
ans: fire resistance | night vision | healing | speed | water breathing
a: mine's invisibility, just to sneak | regeneration | slow falling, so fun
b: slow falling is fun | invisibility, sneaky

# nether_biome | favourite nether biome | nether biomes
q: what's your favourite nether biome? | best part of the nether?
ans: warped forest | crimson forest | basalt deltas, lol | soul sand valley | the plain nether wastes
a: mine's the warped forest, no hoglins | soul sand valley, it's eerie | crimson forest, so red
b: warped is the safest | soul sand valley is creepy

# fish | favourite fish | fishing ocean
q: what's your favourite fish? | best fish?
ans: salmon | cod | tropical fish | pufferfish, lol | the axolotl, does that count
a: mine's salmon, it's the best cooked | tropical fish, so colourful | cod, classic
b: salmon is the tastiest | tropical fish are pretty

# crop | favourite crop | farming
q: what's your favourite crop to grow? | best thing to farm?
ans: wheat | carrots | potatoes | pumpkins | melons
a: mine's sugar cane, it's useful | beetroot, the colour | sweet berries, even though they hurt
b: sweet berries hurt, yeah | sugar cane is useful

# house_thing | favourite thing in your house | home
needs: a:has_house
w: 1.2
q: what's your favourite thing in your house? | best part of your place?
ans: my bed | my chests | the fireplace | the window | my cat
a: mine's the furnace corner | the little garden out front | the rug, i made it myself
b: a garden out front, nice | homemade rug, cozy

# chest_loot | best chest find | structures
q: what's the best thing you've found in a chest? | best chest loot ever?
ans: diamonds | a saddle | a name tag | an enchanted book | a golden apple
a: mine was a music disc | a whole stack of bread, i was starving | a diamond horse armor
b: a stack of bread when starving, perfect | a music disc, nice

# best_pick | favourite pickaxe | tools memories
q: what's the best pickaxe you've ever had? | favourite pickaxe ever?
ans: my first iron one | a diamond one with efficiency | a stone one that lasted forever | my current one | one i named
a: mine was a gold one that broke in a day | my first diamond pick, i cried | an iron one i used till it broke
b: a gold one, rip | first diamond pick, emotional

# cave_type | favourite kind of cave | caves
q: what's your favourite kind of cave? | best cave type?
ans: lush caves | huge open ones | dripstone caves | ravines | little tunnels
a: mine's the big ones with lava lakes | lush caves, so pretty | dripstone, like teeth
b: dripstone does look like teeth | lava lake caves, risky

# ocean_thing | favourite thing in the ocean | ocean
q: what's your favourite thing in the ocean? | best part of the ocean?
ans: coral reefs | shipwrecks | dolphins | turtles | kelp forests
a: mine's the glow squid | the ruins at the bottom | sea pickles, they glow
b: sea pickles, cute | ocean ruins are cool

# mornings | favourite thing about mornings | day
q: what's your favourite thing about mornings? | best part of the morning?
ans: the sunrise | zombies burning | breakfast | a fresh start | the quiet
a: mine's opening the door and nothing's waiting | seeing the farm grew overnight | the sound of birds... i mean chickens
b: chickens count | the farm growing overnight, yes

# nights | favourite thing about night | night
q: what's your favourite thing about night? | best part of the night?
ans: the stars | the quiet | sleeping | the moon | mob drops
a: mine's torches glowing on the walls | the moon on the water | the end of it, honestly
b: torches glowing, cozy | the end of it, lol

# sleep_spot | favourite place to sleep | sleep
q: where's your favourite place to sleep? | best place to sleep?
ans: my own bed | a cozy hole in a hill | next to my chests | a treehouse | a boat
a: mine's a little room under the stairs | anywhere with a roof | a bed by the window
b: under the stairs, cozy | a bed by the window, nice

# funniest_death | funniest death | death jokes
q: what's the funniest way you've died? | your most embarrassing death?
ans: a chicken jockey | fell off my own ladder | drowned in a one block hole | a creeper right as i opened my door | walked into a cactus too many times
a: mine was a baby zombie on a chicken | i fell into my own furnace room lava | a goat knocked me off a mountain
b: a goat, lol | your own lava, classic

# cutest_mob | cutest mob | mobs animals
q: what's the cutest mob? | cutest thing out here?
ans: baby foxes | axolotls | baby pandas | cats | bees
a: mine's a baby turtle | the allay | a baby sniffer
b: baby turtles, so small | the allay dancing, yes

# scariest_mob | scariest mob | mobs danger
q: what's the scariest mob? | scariest thing in the world?
ans: the warden | creepers | endermen | phantoms | cave spiders
a: mine's the drowned, they come out of nowhere | a charged creeper | a witch in the dark
b: charged creepers, yeah | drowned out of nowhere, true

# ugliest_mob | ugliest mob | mobs jokes
q: what's the ugliest mob? | ugliest mob, be honest
ans: the hoglin | the zombie | the ravager | silverfish | the elder guardian
a: mine's the zoglin, rotten pig | the husk, all dried up | the evoker's face
b: zoglins are rough | the evoker's face, lol

# coolest_block | coolest looking block | building
q: what's the coolest looking block? | best looking block?
ans: amethyst | sculk | crying obsidian | prismarine | magma
a: mine's the chiseled bookshelf | copper when it turns green | a beacon, lit up
b: oxidized copper is so nice | chiseled bookshelves, fancy

# underrated_item | most underrated item | crafting
q: what's the most underrated item? | which item does nobody appreciate?
ans: the shield | the water bucket | hay bales | the composter | the lead
a: mine's the bowl, stews need it | the boat, saves so much time | the bell, nobody rings it
b: the boat, true | bowls are important

# overrated_item | most overrated item | crafting complaints
q: what's the most overrated item? | which item is overrated?
ans: gold armor | the trident | diamond hoes | the spyglass | the crossbow
a: mine's the golden apple, just eat steak | the elytra, walking's fine | the compass, i get lost anyway
b: elytra overrated, bold | the compass, lol

# base_spot | best place for a base | home building
q: what's the best place for a base? | where's the best spot to build a base?
ans: by a river | on a mountain | underground | near a village | by the ocean
a: mine's at the edge of a forest | in a cliff face | on an island in a lake
b: a cliff face base, cool | an island in a lake, nice

# hiding_spot | best hiding spot | jokes
q: what's the best hiding spot out here? | where would you hide if you had to?
ans: a hole in the ground | up a tree | behind a waterfall | in a haystack | under a bridge
a: mine's inside a pumpkin patch | in a cave behind some vines | under someone's floor lol
b: under someone's floor, creepy | behind vines, smart

# world_word | one word for this world | philosophy
q: if you had to describe this world in one word, what is it? | one word for this world?
ans: big | blocky | beautiful | dangerous | home
a: mine's endless | cozy, mostly | chaos
b: endless, true | cozy, sometimes

# mob_sound | favourite mob sound | mobs music
q: what's your favourite mob sound? | best noise a mob makes?
ans: a cat purring | a cow mooing | a villager's hm | a fox squeaking | a frog croaking
a: mine's a bee buzzing | an axolotl's little chirp | a horse snort
b: the villager hm is iconic | bees buzzing, cozy

# drinks | favourite drink | food
q: what's your favourite thing to drink? | best drink, milk or what?
ans: milk | honey | potions | water bottles, i'm simple | suspicious stew counts
a: mine's honey, so sweet | milk, it fixes everything | nothing, i eat my feelings
b: milk fixes everything, true | honey's the best

# chop_tree | favourite tree to chop | trees
q: what's your favourite tree to chop? | best tree for chopping?
ans: oak, it's quick | spruce, tall and straight | birch, clean logs | dark oak, so much wood | jungle trees, huge
a: mine's the big spruce, so many logs | birch, no branches | acacia, just for the colour
b: big spruces take forever | birch is easy

# friends_activity | favourite thing to do with friends | friends
needs: !strangers
q: what's your favourite thing to do with friends? | what's the best thing to do together?
ans: mining | building | exploring | just talking | fighting mobs at night
a: mine's going on long trips | building something huge together | sitting around a campfire
b: a campfire, yes | long trips together, fun

# mining_snack | favourite mining snack | food mining
q: what's your favourite snack while mining? | what do you eat down in the mines?
ans: bread | cookies | steak | dried kelp | carrots
a: mine's baked potatoes | golden carrots, they last | whatever i've got
b: whatever you've got, relatable | golden carrots, smart

# cat_type | favourite cat | pets
q: what's your favourite kind of cat? | best cat colour?
ans: the black one | the tabby | the white one | the orange one | the siamese one
a: mine's the calico | the tuxedo cat | the grey one, it looks so serious
b: tuxedo cats are classy | the grey one does look serious

# horse_colour | favourite horse | animals
q: what's your favourite horse colour? | best looking horse?
ans: white | black | brown with spots | chestnut | grey
a: mine's dark brown with white socks | a white one with spots | the cream one
b: white socks, cute | a spotted one, nice

# village_type | favourite village | villagers biomes
q: what's your favourite kind of village? | best village style?
ans: plains village | desert village | taiga village | snowy village | savanna village
a: mine's the savanna one, the orange wood | taiga, it's cozy | desert, sandstone is nice
b: savanna villages are colourful | taiga villages are cozy

# roof_style | favourite roof | building
q: what's your favourite kind of roof? | best roof style?
ans: steep and pointy | flat | stairs with an overhang | a big round dome | no roof lol
a: mine's a dark oak roof with an overhang | a simple flat one | a steep spruce one
b: overhangs make it | flat ones are clean

# floor_block | favourite floor | building
q: what's your favourite floor? | best block for a floor?
ans: oak planks | stone bricks | carpet | polished andesite | spruce
a: mine's a checker pattern | dark oak with a rug | smooth stone, clean
b: checker floors, fancy | dark oak and a rug, cozy

# copper_stage | favourite copper | building
q: copper fresh or green, which do you like better? | shiny copper or old green copper?
ans: shiny, wax it right away | green, it looks old | the middle bit, half green | fresh, it's so orange | green, let it age
a: mine's the green, fully oxidized | shiny, always | the half-green one, best of both
b: half green is the best | fully green looks so old

# glass_colour | favourite stained glass | building decor
q: what's your favourite stained glass colour? | best glass colour?
ans: light blue | orange | clear, plain | purple | green
a: mine's yellow, it makes the room warm | red, it's dramatic | cyan, like the sea
b: yellow glass is warm | cyan like the sea, nice

# recipe | favourite recipe | crafting
q: what's your favourite thing to craft? | best crafting recipe?
ans: a crafting table, the first | torches, always | a bed | a cake | a piston
a: mine's a jukebox | fireworks | a map, i love making maps
b: fireworks are fun | maps are so satisfying

# furnace_cook | favourite thing to smelt | crafting
q: what's your favourite thing to put in a furnace? | best thing to smelt?
ans: iron ore | raw beef | sand | potatoes | clay
a: mine's cobble into stone | logs into charcoal | kelp, so i can eat it later
b: cobble into stone, satisfying | charcoal, nice

# trade_for | favourite thing to trade for | trade villagers
q: what's your favourite thing to trade for? | best thing to get from villagers?
ans: mending books | cheap bread | arrows | maps | glass
a: mine's bookshelves | a diamond pickaxe from the smith | name tags
b: bookshelves, nice | name tags, rare

# weapon | favourite weapon | weapons
q: what's your favourite weapon? | fav weapon?
ans: sword | axe | bow | crossbow | trident
a: mine's the bow, keep them away | a good old iron sword | the axe, it hits hard
b: the axe does hit hard | a bow, safe

# armor | favourite armor | armor
q: what's your favourite armor? | best looking armor?
ans: diamond | iron | netherite | gold, it's shiny | leather, dyed
a: mine's chainmail, it looks cool | dyed leather, i match | iron, the classic look
b: chainmail does look cool | dyed leather, stylish

# moon_or_stars | moon or stars | sky night
q: what's better, the moon or the stars? | moon or stars, which do you like more?
ans: the moon | the stars | the moon, it's big | stars, there's so many | both, together
a: mine's the stars on a clear night | the full moon over water | a new moon, so dark
b: a full moon over water, pretty | clear night stars, nice

# shield_design | favourite shield design | weapons decor
q: what's your favourite shield design? | how do you decorate a shield?
ans: plain, i'm boring | a creeper face | stripes | a big cross | a flower
a: mine's a little sun on blue | black with a skull | i dyed mine red
b: a sun on blue, nice | a skull, edgy

# path_block | favourite path block | building
q: what's your favourite block for paths? | best path block?
ans: gravel | dirt path | cobble | stone bricks | coarse dirt
a: mine's packed mud | mossy stone bricks | plain dirt path, simple
b: mossy paths look great | packed mud, good choice

# house_light | favourite way to light a house | building night
q: how do you like to light your house? | best way to light a room?
ans: lanterns | torches on the walls | glowstone in the floor | candles | hidden lights under carpet
a: mine's lanterns on chains | torches, two above the floor | sea lanterns in the ceiling
b: lanterns on chains, fancy | sea lanterns are pretty

# pumpkin_face | favourite pumpkin face | plants decor
q: what's your favourite thing about pumpkins? | best part of a pumpkin?
ans: pie | the faces | lighting them at night | pumpkin patches | wearing one lol
a: mine's carving the face | the pie, obviously | putting them on fence posts
b: on fence posts, nice | pie, obviously

# fishing_spot | favourite fishing spot | fishing
q: where's your favourite place to fish? | best fishing spot?
ans: a river | the ocean | a little pond | under a bridge | off a dock
a: mine's a lake at sunset | a pond by my house | a river in the rain
b: a river in the rain, the best | a pond by your house, handy

# night_snack | favourite night snack | food night
q: what's your favourite thing to eat at night? | late night snack?
ans: cookies | bread | stew | an apple | cake
a: mine's a baked potato by the fire | honey | steak, no regrets
b: steak at night, bold | honey, sweet

# mountain_mob | favourite mountain mob | animals biomes
q: what's your favourite animal up in the mountains? | best mountain animal?
ans: goats | llamas | rabbits | foxes | sheep
a: mine's the goat, even if it rams me | snow foxes | llamas, they look so smug
b: snow foxes, cute | llamas do look smug

# water_mob | favourite water mob | ocean animals
q: what's your favourite water mob? | best thing that swims?
ans: dolphins | turtles | axolotls | glow squids | tropical fish
a: mine's the axolotl | dolphins, they help | a turtle, slow swimmer
b: dolphins help, true | axolotls are the best

# wake_sound | favourite morning sound | day music
q: what's the best sound to wake up to? | best thing to hear in the morning?
ans: rain | chickens | a cat | nothing, silence | the village bell
a: mine's water running nearby | the furnace still going | cows mooing outside
b: the furnace still going, cozy | cows mooing, nice

# hotbar | favourite hotbar slot | tools jokes
q: what's in your first hotbar slot, always? | what's always in your first slot?
ans: my sword | my pickaxe | torches | food | a block of dirt
a: mine's always a pickaxe | food, priorities | a water bucket, just in case
b: water bucket first, smart | food first, priorities

# naming | favourite thing to name | pets jokes
q: what's the best thing to name with a name tag? | what would you name first with a name tag?
ans: my dog | a cow | a sheep | a horse | a creeper, lol
a: mine's a chicken called captain | a pig named sir | a sheep called cloud
b: captain the chicken, lol | cloud the sheep, cute

# rain_love | favourite thing about rain | weather
q: what's your favourite thing about rain? | best part of rain?
ans: the sound | fishing is better | crops grow | puts out fires | it's cozy
a: mine's the smell, if it had one | sitting inside while it pours | the puddles that aren't there
b: sitting inside while it pours, yes | puddles that aren't there, lol

# snow_love | favourite thing about snow | weather biomes
q: what's your favourite thing about snow? | best part of snow?
ans: snowballs | snow golems | how quiet it gets | igloos | how pretty it is
a: mine's the snowy roofs | snowball fights | the foxes in the snow
b: snowy roofs are cozy | snowball fights, yes

# caves_love | favourite thing about caves | caves
q: what's your favourite thing about caves? | best part of caving?
ans: the ore | the quiet | finding lava lakes | getting lost a bit | glow lichen
a: mine's turning a corner and finding a huge open room | the echo | finding someone's old torches
b: finding old torches, spooky | a huge open room, yes

# villages_love | favourite thing about villages | villagers
q: what's your favourite thing about villages? | best part of a village?
ans: the trades | the beds | the farms | the bell | the iron golem
a: mine's the little kids running around | the hay bales | the paths between houses
b: the kids running around, cute | hay bales, cozy

# nether_love | favourite thing about the nether | nether
q: what's your favourite thing in the nether? | best part of the nether?
ans: the fortresses | the lava oceans | striders | the warped forest | quartz
a: mine's riding a strider over lava | finding a bastion | the glowstone on the ceiling
b: striders are so funny | a bastion, risky

# bird | favourite bird | animals
q: what's your favourite bird? | best bird?
ans: parrots | chickens | the phantom, lol | baby chickens | parrots, the red ones
a: mine's the blue parrot | a chicken, honestly | the grey parrot
b: a chicken, honestly, respect | blue parrots are nice

# bug | favourite bug | animals
q: what's your favourite bug? | best bug, there's not many
ans: bees | silverfish, no | endermites, no | bees, only bees | fireflies, if they existed
a: mine's bees, obviously | spiders count? | glow squid is basically a bug, right?
b: spiders don't count | glow squid is not a bug lol

# carry_item | favourite thing to carry | storage
q: what's one thing you always carry? | what do you never leave home without?
ans: torches | food | a water bucket | a bed | a crafting table
a: mine's a stack of cobble | a spare pickaxe | a boat, you never know
b: a boat, smart | spare pickaxe, always

# underrated_mob | most underrated mob | mobs
q: what's the most underrated mob? | which mob deserves more love?
ans: the snow golem | the squid | the bat | the mule | the turtle
a: mine's the strider | the donkey, great for carrying | the cod, it feeds everyone
b: donkeys, true | striders are great

# best_town_thing | best thing about our town | town
needs: a:in_town
w: 1.2
q: what's the best thing about living in town? | what do you like most about this town?
ans: people around | the walls | the market | the lights at night | the farms
a: mine's not being alone at night | hearing everyone talk | the bell in the middle
b: hearing everyone talk, nice | not being alone, yeah

# first_night_plan | best first night plan | night advice
q: what's the best way to survive the first night? | best first night plan?
ans: dig into a hill | build a dirt hut | pillar up | find a village | just stay up and fight
a: mine's a hole in the side of a hill with a door | a little box and a furnace | find sheep, make a bed
b: find sheep, make a bed, smart | a hole with a door, classic

# best_memory | best memory | memories feelings
q: what's your best memory out here? | favourite memory?
ans: my first diamond | my first house | the first time i saw the ocean | taming my first dog | surviving the first night
a: mine's watching the sunrise after my first night | finding a village after days of walking | building my first farm
b: a village after days of walking, aw | the first sunrise, yes

# underwater_build | favourite thing about building by water | building ocean
q: what's your favourite thing to build near water? | best water build?
ans: a dock | a bridge | a lighthouse | a boathouse | a fishing hut
a: mine's a little dock with a lantern | a stone bridge over a river | a lighthouse on a rock
b: a dock with a lantern, cute | a lighthouse, nice

# banner | favourite banner | decor
q: what's your favourite banner? | best banner design?
ans: a plain colour | stripes | a creeper face | a flower | a big circle
a: mine's black and gold | red with a white cross | blue with a little moon
b: blue with a moon, pretty | black and gold, fancy

# painting | favourite painting | decor
q: what's your favourite painting? | best painting?
ans: the sunset one | the skull one | the pond | the wanderer | the creepy one
a: mine's the little pond one | the big skull, it's dramatic | the one with the sunflowers
b: the sunflowers one, nice | the skull, dramatic

# music_disc | favourite music disc | music
q: what's your favourite music disc? | best disc for the jukebox?
ans: the cat one | the cheerful one | the creepy one | the one from the dungeon | the one i actually own
a: mine's whichever one i found first | the bouncy one | the calm one
b: the calm one, nice | the first one you found, sentimental

# sheep_colour | favourite sheep | animals
q: what's your favourite colour of sheep? | best sheep colour?
ans: pink, the rare ones | white, classic | black | brown | grey
a: mine's the brown one, it looks cozy | pink, so rare | a dyed blue one
b: a blue sheep, fun | pink sheep are rare

# dog_name | favourite dog name | pets
q: what's the best name for a dog? | if you got a dog, what would you name it?
ans: rex | biscuit | bones | shadow | max
a: mine would be pickles | chunk | bark... no, nugget
b: pickles is perfect | chunk, lol

# fence | favourite fence | building
q: what's your favourite fence? | best fence block?
ans: oak | spruce | dark oak | nether brick | stone walls
a: mine's cobble walls | jungle fences, nobody uses them | birch, clean
b: cobble walls are solid | birch fences, clean

# window | favourite window | building
q: what's your favourite kind of window? | best window style?
ans: glass panes | full glass blocks | stained glass | trapdoors as shutters | no windows
a: mine's panes with trapdoor shutters | a big round one | tinted glass, private
b: shutters, fancy | tinted glass, private

# day_part | favourite part of a long day | day feelings
q: what's your favourite part of a long day? | best moment in a long day?
ans: the end | lunch | finding something good | getting home | the walk back
a: mine's the moment i put everything in chests | eating dinner | taking my armor off
b: putting everything away, satisfying | dinner, yes

# moonlight | favourite moon | sky night
q: full moon or no moon, which do you like better? | big full moon or a dark night?
ans: full moon, so bright | no moon, the stars show more | full moon, always | the half moon | no moon, mysterious
a: mine's the full moon over the ocean | the thin little moon | a full moon, even with the slimes
b: the thin moon is pretty | full moon over the ocean, yes
"""

# ---------------------------------------------------------------------------------------------------------------------
# 6. Small hot takes: c (a's take), y (b agrees), n (b disagrees), ay / an (a answers each). Agreeing or not counts.

TAKES = """
# pie_cake | pie and cake | food
c: pumpkin pie is better than cake | pie beats cake, i said it
y: pie fits in your pocket | cake's too much work | pie is cozier
n: cake is a party, pie is a snack | cake feeds everyone | you can't share a pie
ay: pie gang
an: cake is just a big pie anyway

# bread_best | bread | food
c: bread is the best food in the game | nothing beats bread, honestly
y: three wheat, can't argue | bread never lets you down | it's so easy to make
n: steak is way better | bread barely fills you | golden carrots exist
ay: simple is best
an: steak is for show-offs

# moon_sun | the moon and the sun | sky
c: the moon is prettier than the sun | the moon wins, the sun's just bright
y: the moon has phases, it's interesting | you can actually look at the moon | true, the moon's calm
n: the sun burns the zombies, that's prettier | the sun is life | sunsets though
ay: moon gang
an: you just like zombies burning

# rain_best | rain | weather
c: rain is the best weather | rainy days are the best days
y: cozy | crops grow and fishing's better | i love the sound
n: rain is so gloomy | it ruins everything | sunny is better
ay: rain on a roof, nothing better
an: gloomy is cozy though

# night_better | night and day | night day
c: night is better than day | i like the night more than the day
y: it's quieter | the stars are great | the mobs make it exciting
n: day is safe | night is when you die | i can't see anything at night
ay: night people unite
an: you just need more torches

# slimes_cute | slimes | mobs
c: slimes are cute | slimes are the cutest mob, fight me
y: they bounce, how could they not be | little jelly cubes, yes | so squishy
n: they're slimy | the big ones are terrifying | they hurt
ay: tiny slimes especially
an: the small ones though

# spiders_misunderstood | spiders | mobs
c: spiders are misunderstood | spiders aren't that bad
y: they're nice in the day | they just want to climb | true, they leave you alone in daylight
n: they jump at you | cave spiders exist | they're creepy
ay: give them a chance
an: cave spiders ruined it for everyone

# trader_scam | the wandering trader | trade
c: the wandering trader is a scammer | that trader's prices are a crime
y: an emerald for one sapling | robbery | he knows what he's doing
n: he has rare stuff | it's fair for what he brings | he travels so far
ay: robbery, every time
an: you just don't like paying

# llamas_rude | llamas | animals
c: llamas are the rudest animal | llamas are so rude
y: they spit at you | they look so smug | rude and proud
n: they carry your stuff | they're fluffy | they only spit if you hit them
ay: the smug face says it all
an: they have a right to spit

# cats_dogs | cats and dogs | pets
c: cats are better than dogs | cats beat wolves, easy
y: creepers run from cats | cats bring gifts | cats never take fall damage
n: dogs actually fight for you | wolves are loyal | cats just sit there
ay: cat people know
an: a dog won't save you from a creeper though

# chickens_funny | chickens | animals jokes
c: chickens are the funniest mob | nothing's funnier than a chicken
y: the way they flap down | they're so dumb, i love them | baby chickens, so funny
n: goats are funnier | chickens are boring | they just peck
ay: the flapping gets me every time
an: goats are funny, i'll give you that

# birch_ugly | birch wood | trees building
c: birch is the ugliest wood | birch planks are boring
y: it's too pale | it looks unfinished | yeah, birch is plain
n: birch is clean | birch is pretty | it's nice with dark oak
ay: pale and sad
an: clean is just boring with a better name

# dark_oak_best | dark oak | trees building
c: dark oak is the best wood | dark oak beats every other wood
y: it's so rich looking | every build looks better in dark oak | great for roofs
n: spruce is better | it's too dark | it's a pain to grow
ay: dark oak roofs, the best
an: spruce is just dark oak lite

# cobble_ugly | cobblestone | building
c: cobblestone is ugly | i hate how cobble looks
y: it's so noisy | it looks like a mess | stone bricks are better
n: cobble is classic | it's rustic | it's free and it's fine
ay: smooth it out
an: rustic is just ugly with a nice name

# gravel_useless | gravel | mining
c: gravel is useless | gravel shouldn't exist
y: it falls on you | it's just annoying | only good for flint
n: flint! | paths look nice with gravel | concrete needs it
ay: flint is the only reason
an: fine, flint. that's it

# dirt_underrated | dirt | building
c: dirt is underrated | dirt doesn't get enough love
y: first house, every time | it's always there for you | it grows stuff
n: dirt is ugly | it's just filler | coarse dirt maybe
ay: respect the dirt
an: everybody's first house was dirt

# desert_worst | the desert | biomes
c: the desert is the worst biome | deserts are the worst
y: no trees, no water | husks everywhere | it's so empty
n: the temples though | sandstone looks nice | no creepers hiding in trees
ay: no wood, no thanks
an: the temples are traps anyway

# jungle_best | the jungle | biomes
c: the jungle is the best biome | jungles are the best
y: so much to find | parrots and pandas | melons and cocoa
n: you get lost so fast | the trees are a pain | too many ocelots running off
ay: so alive in there
an: getting lost is half the fun

# snow_cozy | snowy biomes | biomes weather
c: snowy biomes are the coziest | snow is the coziest place to live
y: igloos and fires | so quiet | a snowy roof is perfect
n: it's cold | strays everywhere | powder snow will kill you
ay: a fire and some snow, perfect
an: just wear leather boots

# ocean_scary | the ocean | ocean
c: the ocean is the scariest place | the deep ocean is scarier than any cave
y: drowned everywhere | you can't see the bottom | guardians, nope
n: caves are worse | the ocean is pretty | dolphins though
ay: you can't see what's below
an: dolphins don't make up for drowned

# caves_relaxing | caves | caves
c: caves are relaxing | i find caves really relaxing
y: quiet and calm | just you and a pickaxe | the drips are soothing
n: caves are terrifying | the noises | something's always behind you
ay: torch, pick, peace
an: you just need more torches

# mining_building | mining and building | mining building
c: mining is more fun than building | i'd rather mine than build
y: the surprise | finding stuff is the best | building is too much thinking
n: building lasts | mining is just walking in the dark | building is creative
ay: you never know what's next
an: builders need miners anyway

# fishing_boring | fishing | fishing
c: fishing is boring | fishing is the most boring thing to do
y: you just sit there | it takes forever | i fall asleep
n: fishing is relaxing | you get books and saddles | it's calm
ay: sit, wait, fish, repeat
an: relaxing is just boring with a better name

# farming_relaxing | farming | farming
c: farming is the most relaxing thing there is | nothing's more relaxing than a farm
y: planting rows, so calm | watching stuff grow | you're in the sun
n: it's slow | rabbits eat all your carrots | it's boring
ay: rows and rows, peace
an: slow is the point

# bed_important | beds | sleep
c: a bed is the most important item | beds matter more than anything
y: skip the night, set the spawn | it's everything | no bed, no life
n: a pickaxe matters more | a sword keeps you alive | food is more important
ay: three wool, the best investment
an: you can't sleep if you're dead

# torches_ugly | torches | building night
c: torches are ugly | i hate how torches look on walls
y: lanterns are way nicer | they clutter everything | true
n: they're classic | they're cheap and they work | torches are cozy
ay: lanterns, always
an: cheap and ugly though

# lanterns_better | lanterns | building night
c: lanterns are better than torches | lanterns beat torches, easy
y: they look way better | so cozy | they hang
n: torches are cheaper | iron for a light? no | torches are fine
ay: worth the iron
an: you're cheap, that's all

# panes_blocks | glass panes | building
c: glass panes are better than glass blocks | panes over full glass, always
y: they look thinner and nicer | more glass for less sand | they're cleaner
n: full glass is cleaner | panes look cheap | blocks are sturdier
ay: thin is in
an: panes are flimsy

# villagers_lazy | villagers | villagers
c: villagers are lazy | villagers don't do anything all day
y: they just wander | they sleep half the day | true, hm all day
n: they farm | they work their stations | they make golems
ay: hm and sleep, that's it
an: farming the same patch isn't work

# golems_heroes | iron golems | villagers mobs
c: iron golems are the real heroes | golems do all the work around here
y: they protect everyone | they never stop | and they give flowers
n: they attack you if you hit a villager | they just wander | they're slow
ay: give them a medal
an: well don't hit villagers then

# zombies_easy | zombies | mobs
c: zombies are the easiest mob | zombies are no threat at all
y: they're so slow | a few swings and done | they burn in the morning
n: baby zombies though | in a group they're bad | they break doors
ay: slow and dumb
an: babies are a different story

# skeletons_worse | skeletons | mobs
c: skeletons are worse than creepers | skeletons are more dangerous than creepers
y: arrows from far away | they knock you off ledges | they never stop shooting
n: creepers blow up your house | creepers are silent | you can block arrows
ay: knocked into lava, every time
an: a shield doesn't stop a creeper

# phantoms_worst | phantoms | mobs
c: phantoms are the worst mob | nothing's worse than phantoms
y: swooping from nowhere | they're so annoying | they ruin every night
n: just sleep | they're easy if you sleep | creepers are worse
ay: they're the worst
an: who has time to sleep

# endermen_cool | endermen | mobs
c: endermen are the coolest mob | endermen are so cool
y: tall, mysterious | they teleport | the way they carry blocks
n: they're creepy | they steal stuff | they get mad for nothing
ay: mysterious is cool
an: they get mad because you stare

# bees_best | bees | animals
c: bees are the best animal | bees are the best thing in the game
y: honey and crops | they're so cute | busy little things
n: they sting | they die when they sting | they're annoying
ay: buzz buzz
an: don't make them angry then

# foxes_cutest | foxes | animals
c: foxes are the cutest animal | nothing's cuter than a fox
y: when they sleep | the little ones | the snow ones especially
n: cats are cuter | they steal your stuff | axolotls are cuter
ay: the sleeping ones, so cute
an: they steal because they're cute

# pigs_underrated | pigs | animals
c: pigs are underrated | pigs deserve more respect
y: porkchops, rides | they're always happy | they're cute
n: they're boring | cows are better | they're slow
ay: pig gang
an: cows don't let you ride them

# horses_overrated | horses | animals
c: horses are overrated | horses aren't that great
y: taming is a pain | they wander off | boats are faster
n: they're so fast | they jump fences | they're beautiful
ay: boats don't wander off
an: boats can't go on land

# boats_best | boats | ocean exploring
c: boats are the best way to travel | nothing beats a boat
y: rivers everywhere | calm and fast | you can bring a friend
n: horses are better | boats can't go uphill | rails are faster
ay: rivers are roads
an: uphill is overrated

# minecarts_pointless | minecarts | exploring
c: minecarts are pointless | minecarts aren't worth the iron
y: walking is faster | so much iron for rails | true
n: they're relaxing | long trips are easy | they carry stuff
ay: walk, it's free
an: relaxing isn't fast though

# leather_style | leather armor | armor
c: leather armor looks the best | dyed leather is the best look
y: you can dye it any colour | matching outfits | it's stylish
n: it's useless | diamond looks better | it breaks so fast
ay: fashion first
an: diamond is just blue leather

# gold_armor | gold armor | armor
c: gold armor is pointless | gold armor is a waste of gold
y: breaks so fast | barely protects | except for piglins
n: piglins though | it enchants well | it's shiny
ay: one gold helmet for piglins, done
an: shiny isn't protection

# shields_cowards | shields | weapons fighting
c: shields are for cowards | real fighters don't use shields
y: just dodge | swords only | true
n: shields save lives | creepers though | skeletons
ay: dodge or die
an: alive and a coward, fine by me

# swords_axes | swords and axes | weapons
c: swords are better than axes | a sword beats an axe every time
y: faster | they sweep | they're made for it
n: axes hit harder | axes break shields | axes are tools too
ay: speed matters
an: one big hit beats three small ones

# dirt_stack | carrying dirt | storage
c: a stack of dirt is essential | always carry a stack of dirt
y: pillar up anytime | it saves lives | block off caves
n: cobble is better | it's just clutter | a water bucket does more
ay: dirt saves lives
an: cobble's tougher

# water_bucket | water buckets | danger
c: you should always carry a water bucket | never leave home without water
y: saves you from falls | puts out fires | lava turns to stone
n: it's heavy | i never use it | i forget it every time
ay: one bucket, a hundred lives
an: you'll need it one day

# sleep_skip | sleeping | sleep night
c: sleeping is skipping the best part | sleeping through the night is a waste
y: the night is exciting | the drops | stars
n: sleep is safe | the night is just danger | phantoms
ay: live a little
an: phantoms are a small price

# sunrise_best | sunrises | sky day
c: sunrise is the best time of day | sunrises beat sunsets
y: new day feeling | zombies burning | so pretty
n: sunsets are better | i'm asleep at sunrise | the sky's nicer at sunset
ay: a fresh start
an: sunsets mean bed though

# stew_underrated | mushroom stew | food
c: mushroom stew is underrated | nobody gives mushroom stew credit
y: it's so filling | mushrooms are everywhere | cozy
n: it doesn't stack | bread is easier | it's just mushrooms
ay: one bowl, full belly
an: stacking is overrated

# flesh_fine | rotten flesh | food
c: rotten flesh is fine to eat | rotten flesh isn't that bad
y: in an emergency | it fills you a bit | the hunger thing passes
n: it's disgusting | it makes you sick | just eat bread
ay: emergency food
an: when you're starving, it's a feast

# carrots_potatoes | carrots and potatoes | food farming
c: carrots are better than potatoes | carrots beat potatoes
y: no cooking | pigs follow you | golden carrots
n: baked potatoes are better | potatoes fill you more | potatoes are cozy
ay: no furnace needed
an: baked potatoes take one furnace

# baked_potato | baked potatoes | food
c: baked potatoes are the best food | nothing beats a baked potato
y: so filling | easy farm | cozy food
n: steak is better | you have to cook them | bread is easier
ay: crispy and warm
an: everything good needs cooking

# cookies_waste | cookies | food
c: cookies are a waste of cocoa | cookies are pointless
y: they barely fill you | eight of them and you're still hungry | true
n: they're a snack | they're cute | they stack
ay: snacks don't count
an: cute isn't food

# melons_overrated | melons | food farming
c: melons are overrated | melon is a bad food
y: barely fills you | so many slices | true
n: melon farms are easy | they're refreshing | glistering melons
ay: slices of nothing
an: refreshing doesn't fill you

# apples_sad | apples | food trees
c: apples are the most disappointing food | apples are so disappointing
y: you wait forever for one | they barely fill you | true
n: golden apples though | they're free from trees | they're fine
ay: so rare and so meh
an: golden apples are a different thing

# flowers_decor | flowers | decor plants
c: flowers are the best decoration | nothing beats flowers for decorating
y: they're free | so colourful | flower pots everywhere
n: paintings are better | lanterns are better | they're too small
ay: free and pretty
an: paintings are random though

# big_houses | big houses | building home
c: big houses are a waste | a big house is just empty space
y: small and cozy wins | too much to light | you never use the rooms
n: room for everything | big houses are impressive | i need space for chests
ay: cozy beats big
an: you just haven't got enough chests

# underground_houses | underground houses | home caves
c: underground houses are the best | living underground is the way
y: safe from everything | no creepers on the roof | stone all round
n: no sunlight | it's gloomy | cave noises at night
ay: safe and sound
an: just put in some lanterns

# name_pets | naming pets | pets
c: everyone should name their pets | pets need names
y: obviously | a name makes it yours | it's their right
n: name tags are rare | they know who they are | it's a waste of a tag
ay: every pet deserves a name
an: they deserve it more than a sword does

# garden_house | gardens | home plants
c: every house needs a garden | a house without a garden isn't a home
y: flowers out front | it makes it cozy | true
n: too much work | a farm is a garden | i live in a cave
ay: flowers out front, at least
an: a few flowers isn't work

# pink_sheep | pink sheep | animals
c: pink sheep are the best sheep | nothing beats a pink sheep
y: so rare | pink wool! | they're special
n: white is classic | they're just dyed | black sheep are cooler
ay: rare and pink
an: black sheep are just shadows

# green_best | green | random
c: green is the best colour | green beats every colour
y: grass, leaves, emeralds | it's everywhere | calming
n: creepers are green | blue is better | red is better
ay: the world is green for a reason
an: creepers being green is the only flaw

# copper_green | oxidized copper | building
c: copper looks better when it's gone green | old green copper is the best
y: it's so pretty | it looks ancient | nature does the work
n: shiny copper is better | wax it, keep it orange | green looks dirty
ay: let it age
an: dirty is character

# sandstone | sandstone | building biomes
c: sandstone is underrated | nobody uses sandstone enough
y: it's pretty | smooth sandstone is clean | cheap too
n: it's too yellow | it looks like a desert | it's boring
ay: smooth sandstone, so clean
an: it's supposed to look like a desert

# deepslate_best | deepslate | building mining
c: deepslate is the best building block | deepslate tiles are the best block
y: so dark and clean | it goes with everything | love it
n: it's too dark | it takes forever to mine | stone bricks are better
ay: dark and clean
an: worth the mining

# stone_bricks_overused | stone bricks | building
c: stone bricks are overused | everyone builds with stone bricks
y: so boring | every castle is stone bricks | true
n: they're classic | they look good everywhere | what else would you use
ay: try something new
an: deepslate, for one

# spruce_best | spruce | trees building
c: spruce is the best wood | nothing beats spruce
y: warm and dark | goes with everything | so cozy
n: oak is the classic | dark oak is better | cherry is prettier
ay: warm and cozy
an: oak is just plain

# cherry_prettiest | cherry trees | trees biomes
c: cherry trees are the prettiest thing in the world | nothing's prettier than a cherry grove
y: pink everywhere | the petals falling | true
n: too pink | birch forests are nicer | autumn colours would be better
ay: pink everywhere
an: there's no such thing as too pink

# bamboo_annoying | bamboo | plants
c: bamboo is annoying | i hate bamboo
y: it's everywhere in jungles | it grows so fast | it gets in the way
n: pandas love it | scaffolding! | it's fuel
ay: it takes over
an: pandas can have it

# kelp_useless | kelp | ocean plants
c: kelp is useless | kelp is the most useless plant
y: dried kelp is awful | it's just in the way | true
n: it's fuel | it's food | kelp blocks burn forever
ay: in the way of every swim
an: dried kelp blocks burn for ages

# swamp_underrated | the swamp | biomes
c: the swamp is underrated | swamps are nice actually
y: frogs and lily pads | it's moody | slimes
n: it's ugly | witches live there | it's murky
ay: frogs!
an: moody is a vibe

# mushroom_island | mushroom islands | biomes
c: mushroom islands are the best biome | nothing beats a mushroom island
y: no mobs! | mooshrooms | it's so weird and cool
n: they're so far | no trees | it's all purple dirt
ay: no mobs at night, think about it
an: mooshrooms make up for no trees

# badlands | the badlands | biomes
c: the badlands are the prettiest biome | the badlands are gorgeous
y: the colours | the terracotta stripes | gold everywhere
n: no trees, no animals | it's hot | it's empty
ay: all those stripes
an: who needs trees when you have stripes

# mountains_live | mountains | biomes home
c: mountains are the best place to live | living on a mountain is the dream
y: the view | safe from floods | goats as neighbours
n: goats will push you off | it's cold | climbing every day
ay: above the clouds
an: get a ladder

# rivers_boring | rivers | biomes
c: rivers are boring | there's nothing in rivers
y: just water | a few squid | boring
n: they're roads for boats | fishing | clay at the bottom
ay: just a wet road
an: wet roads are the best roads

# beaches_relaxing | beaches | biomes ocean
c: beaches are the most relaxing place | nothing beats a beach
y: sand and waves | turtles | sunsets
n: drowned walk out at night | too much sand | it's boring
ay: sit and watch the waves
an: drowned are just bad neighbours

# hunger_worst | hunger | food complaints
c: hunger is the worst part of surviving | hunger is more annoying than mobs
y: always eating | it slows you down | true
n: it's realistic | food is easy | mobs are worse
ay: always hungry
an: try not eating for a day

# dying_fun | dying | death
c: dying is part of the fun | dying is half the fun
y: it's a lesson | you come back stronger | lol true
n: losing my stuff is not fun | no it isn't | dying is the worst
ay: learn and go again
an: unless it's lava

# first_night | the first night | night memories
c: the first night is the best night | nothing beats your first night
y: so scary and exciting | you remember it forever | the dirt hut era
n: it's terrifying | i died my first night | the second night is better
ay: the dirt hut days
an: dying the first night is a rite of passage

# with_friends | playing with friends | friends
c: everything's better with friends | doing stuff with friends beats doing it alone
y: obviously | more fun | someone to watch your back
n: alone is calmer | friends steal your stuff | i like peace
ay: nobody to laugh with alone
an: you can't steal from a friend, that's the rule

# chests_more | chests | storage
c: chests should hold more | chests are too small
y: always full | double chests aren't enough | true
n: just make more chests | shulker boxes | they're fine
ay: never enough room
an: more chests, more problems

# barrels_better | barrels | storage
c: barrels are better than chests | barrels beat chests
y: they stack | you can open them under blocks | they look nice
n: chests can be doubled | chests look classic | barrels are weird
ay: stack them to the roof
an: double chest, that's one thing

# signs_underrated | signs | building
c: signs are underrated | everyone should use more signs
y: label everything | directions | messages
n: they're ugly | i know where my stuff is | waste of wood
ay: label your chests
an: you know now. wait a week

# item_frames | item frames | decor
c: item frames are tacky | item frames look cheap
y: so cluttered | they're everywhere | true
n: they're useful for labels | maps look great in them | they're fine
ay: less is more
an: maps don't count

# banners_best | banners | decor
c: banners are the best decoration | nothing beats a good banner
y: so many patterns | they make a place yours | colourful
n: they're too much work | paintings are easier | they block the view
ay: hang one by the door
an: work is the fun part

# shears_underrated | shears | tools
c: shears are the most underrated tool | nobody gives shears credit
y: wool, leaves, cobwebs | so useful | true
n: they're boring | i never use them | a sword cuts cobwebs
ay: cobwebs, gone
an: try getting leaves without them

# hoe_useless | the hoe | tools farming
c: the hoe is useless | nobody needs a hoe
y: i never use it | just till once | true
n: farming needs it | it's great for leaves | it's fast on moss
ay: till once and forget
an: farmers would disagree

# stone_tools | stone tools | tools
c: stone tools are fine forever | you don't need better than stone
y: cheap and easy | they do the job | stone is everywhere
n: they're so slow | can't mine diamonds | iron is way better
ay: cheap and cheerful
an: iron is cheap too

# diamond_tools | diamond tools | tools diamonds
c: diamond tools are overrated | diamond tools aren't that special
y: iron does the job | too rare to use | i save them anyway
n: they're the best | they last forever | you can mine obsidian
ay: iron does it all
an: obsidian, fine

# rain_roof_sound | rain on a roof | weather music
c: the best sound in the world is rain on a roof | nothing sounds better than rain on a roof
y: so cozy | perfect for sleeping | agreed
n: a furnace crackling is better | the cat purring | the ding of a level up
ay: cozy forever
an: a furnace is just tiny rain

# fireworks | fireworks | crafting
c: fireworks are the best item | nothing's cooler than fireworks
y: so pretty | they're fun | the colours
n: gunpowder is too precious | they're a waste | they scare animals
ay: light up the sky
an: precious gunpowder is the best kind to waste

# cake_block | cake | food building
c: cake is a block, not a food | cake is a block, change my mind
y: you place it | you can't carry it and eat | true, it's furniture
n: you eat it | it's food | a block you eat
ay: furniture you can eat
an: a block you eat is still a block

# snowballs_weapon | snowballs | weapons jokes
c: snowballs are the best weapon | snowballs are underrated as weapons
y: blazes hate them | free | knockback
n: they do nothing | it's a toy | a sword is better
ay: tell that to a blaze
an: knockback off a cliff counts

# tridents_cool | tridents | weapons ocean
c: tridents are the coolest weapon | the trident is the coolest thing you can hold
y: lightning! | throwing and flying | so rare
n: they're impossible to get | bows are better | drowned never drop them
ay: lightning on command
an: rare makes it cooler

# crossbow_bow | crossbows | weapons
c: crossbows are better than bows | i'd take a crossbow over a bow
y: stronger hits | it stays loaded | fireworks!
n: bows are faster | bows are simpler | infinity
ay: loaded and ready
an: simple is boring

# cow_best | cows | animals
c: cows are the best mob | the cow is the best mob in the game
y: steak, leather, milk | so useful | they're chill
n: sheep are better | chickens are funnier | cows are boring
ay: three things from one cow
an: boring and useful is fine

# axolotl_overrated | axolotls | animals
c: axolotls are overrated | axolotls aren't that special
y: they live in buckets | they're just pink lizards | true
n: they help in fights | they're cute | they come in colours
ay: pink lizards
an: cute is a reason

# parrots_annoying | parrots | pets
c: parrots are annoying | parrots never stop making noise
y: so loud | they copy creepers | they fly off
n: they dance | they're colourful | they warn you
ay: they copy creeper sounds, i nearly died
an: that's the warning, see

# turtles_slow | turtles | animals ocean
c: turtles being slow is fine | slow turtles are perfect the way they are
y: they're turtles | slow and steady | let them be
n: they're too slow | eggs take forever | they're boring
ay: slow and steady
an: you're just impatient

# frogs_best | frogs | animals
c: frogs are the best mob | nothing beats a frog
y: they croak | they eat slimes | they're funny
n: they're slimy | cats are better | they jump away
ay: croak croak
an: slimy is fine

# squids_pointless | squids | ocean animals
c: squids are pointless | squids don't do anything
y: just floating | ink, that's it | true
n: ink sacs for dye | you need ink for book and quill | glow squids are pretty
ay: ink and float
an: glow squids are a different thing

# goats_menaces | goats | animals
c: goats are menaces | goats are the most dangerous animal
y: they push you off cliffs | they scream | true
n: they're funny | they give milk | they're just playing
ay: off the cliff, every time
an: playing, lol

# hay_bales | hay bales | farming building
c: hay bales are underrated | hay bales are the best block
y: soft landings | they look rustic | horses love them
n: they're just wheat | they look messy | why not just bread
ay: soft landings forever
an: try falling on bread

# sugar_cane | sugar cane | plants farming
c: sugar cane is the best plant | nothing beats sugar cane
y: paper and sugar | it grows by itself | easy farm
n: it needs water | carrots are better | it's annoying to harvest
ay: books need paper
an: water's everywhere

# smoker_furnace | smokers | food crafting
c: the smoker is better than a furnace | smokers beat furnaces
y: twice as fast for food | it smells better probably | cozy
n: only food though | a furnace does everything | blast furnaces exist
ay: food twice as fast
an: food is all that matters

# composter | composters | farming
c: composters are the best block nobody uses | everyone should have a composter
y: turn junk into bone meal | so useful | true
n: it's slow | i just throw seeds away | farmers villagers use them
ay: seeds into bone meal
an: slow and free beats fast and nothing

# maps_fun | maps | exploring
c: making maps is the most fun thing to do | maps are the best part of exploring
y: watching it fill in | hanging them up | true
n: so much paper | they're slow | i just remember
ay: watching it fill in
an: you get lost all the time though

# lost_fun | getting lost | exploring
c: getting lost is fun | i like getting lost
y: adventure | you find stuff | true
n: no it isn't | you lose your stuff | it's scary
ay: you always find something
an: it's not lost, it's exploring

# early_rise | waking up early | day
c: getting up at sunrise is the only way to live | early risers get the most done
y: the whole day ahead | zombies are burning | true
n: sleeping in is nice | the night ends either way | mornings are rough
ay: up with the sun
an: the zombies won't wait for you

# small_house | small houses | home building
c: small houses are cozier than big ones | small houses are the best houses
y: easy to light | everything close | warm
n: no room for chests | they feel cramped | big is better
ay: cozy wins
an: build down for chests

# dogs_best | wolves | pets
c: a dog is the best friend you can have | wolves make the best friends
y: loyal | they fight for you | they follow you everywhere
n: cats are better | they die so easily | they attack everything
ay: loyal to the end
an: they're brave, that's all

# ravines | ravines | caves danger
c: ravines are the best thing to find | finding a ravine is the best
y: ore everywhere | so big | you can see everything
n: you fall in | mobs everywhere | they're scary
ay: ore on every wall
an: just bring water
"""

# @@MORE@@


# ---------------------------------------------------------------------------------------------------------------------
# Build, check and write.

def tables(name):
    return parse(globals()[name]) if name in globals() else []


for b in tables('WYR'):
    build_wyr(b)
for b in tables('QWYR'):
    build_qwyr(b)
for b in tables('HYPO'):
    build_hypo(b)
for b in tables('RIDDLES'):
    build_riddle(b)
for b in tables('GUESS'):
    build_guess_mob(b)
for b in tables('GAMES'):
    build_free(b, 'game_', ['riddles', 'jokes', 'random'], GENERIC_W)
for b in tables('JOKES'):
    build_joke(b)
for b in tables('KNOCK'):
    build_knock(b)
for b in tables('THOUGHTS'):
    build_thought(b)
for b in tables('FAVS'):
    build_fav(b)
for b in tables('TAKES'):
    build_take(b)
for b in tables('SMALL'):
    build_free(b, 'small_', ['small_talk', 'random'], (0.6, 0.7, 0.8, 0.9))
for b in tables('SILLY'):
    build_free(b, 'silly_', ['silly', 'jokes', 'random'], GENERIC_W)


def every_line(lines):
    groups = [lines] if isinstance(lines, list) else list(lines.values())
    for group in groups:
        for line in group:
            if isinstance(line, dict):
                yield line['line']
                yield from line['then']
            else:
                yield line


problems = []
long_lines = 0
seen = {}
for t in topics:
    for tr in t['turns']:
        for line in every_line(tr['lines']):
            if len(line) > 90 or not line.isascii() or line != line.strip() or not line:
                problems.append('%s: %r' % (t['id'], line))
            if any(c.isupper() for c in line):
                problems.append('%s: capitals in %r' % (t['id'], line))
            if len(line) > 75:
                long_lines += 1
            seen.setdefault(line, set()).add(t['id'])
    if not 1 <= len(t['tags']) <= 4 or not 2 <= len(t['leads']) <= 4:
        problems.append('%s: tags %s leads %s' % (t['id'], t['tags'], t['leads']))
for p in problems:
    print('PROBLEM', p)
assert not problems

if '--repeats' in sys.argv:
    for line, where in sorted(seen.items(), key=lambda kv: -len(kv[1])):
        if len(where) > 4 and len(line) > 12:
            print('%3d  %s' % (len(where), line))

with open(OUT, 'w', encoding='utf-8') as f:
    json.dump({'_comment': 'Random talk to pass the time, from small talk to debates (side session, 2026-11-06; the user: "more '
                           'random convos to pass time ... from small talks to debate"). Written by tools/talk_gen/random_talk.py: '
                           'would you rather, what-ifs, riddles and guessing games, jokes, random thoughts, favourites, small hot '
                           'takes (agree/disagree), small talk about the day, the weather, the time and being bored, and silly '
                           'back-and-forths. Check with: python tools/check_talk_web.py .',
               'topics': topics}, f, indent=1, ensure_ascii=True)
kinds = {}
for t in topics:
    kind = t['id'][len(P):].split('_')[0]
    kinds[kind] = kinds.get(kind, 0) + 1
print(len(topics), 'topics', kinds, '(%d lines over 75 chars)' % long_lines)

