"""Checks the Drifter-to-Drifter topics and their web (data/drifter/drifter_talk/*.json) before a build.

Usage: python tools/check_talk_web.py <project dir> [--quiet]

Errors (exit code 1): a broken file, a topic id used twice, a turn by someone other than a or b, a turn with no lines,
a condition nobody sets, a "next" naming no topic, a lead to a tag no topic has, a {a.word} the game never fills. (A
line whose words this Drifter hasn't got is just skipped in game, so "{a.ore}" needs no condition to be safe.)
Warnings: a tag on one topic only (a typo?), a topic nothing leads to, a topic that leads nowhere but back to itself, a
line said in more than one topic.
"""
import glob
import json
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
QUIET = '--quiet' in sys.argv
TALK = os.path.join(ROOT, 'src/main/resources/data/drifter/drifter_talk')

NOT_TAGS = {'a', 'b', 'the', 'and', 'of', 'to', 'with', 'vs', 'talk', 'chat', 'story', 'show', 'lately'}

# Conditions and fill words the game sets: read from its code (flags.add("..."), words.put("..."), in the mod and the
# addon), so they stay right as the code grows. A flag built as "prefix_" + something allows any flag with that prefix.
FLAGS = set()
FLAG_PREFIXES = set()
WORDS = set()
for java in glob.glob(os.path.join(ROOT, 'src/*/java/**/*.java'), recursive=True):
    code = open(java, encoding='utf-8').read()
    for call in re.findall(r'flags\.add\((.*?)\);', code):
        for literal, plus in re.findall(r'"([a-z_]+)"(\s*\+)?', call):
            (FLAG_PREFIXES if plus else FLAGS).add(literal)
    WORDS |= set(re.findall(r'words\.put\("([a-z_]+)"', code))
# Flags set only about a player (Facts.player: owed, insulted, what they hold...) never hold in a talk between two
# Drifters, so a Drifter topic needing one would never come up.
FLAGS_ELSEWHERE = set()
PLAYER_ONLY = set()
for java in glob.glob(os.path.join(ROOT, 'src/*/java/**/*.java'), recursive=True):
    code = open(java, encoding='utf-8').read()
    player = ''
    if java.endswith('Facts.java') and 'static Facts player(' in code:
        start = code.index('static Facts player(')
        end = code.find('public static', start + 1)
        player = code[start:end if end > 0 else len(code)]
        code = code[:start] + (code[end:] if end > 0 else '')
    for call in re.findall(r'flags\.add\((.*?)\);', code):
        FLAGS_ELSEWHERE.update(re.findall(r'"([a-z_]+)"', call))
    for call in re.findall(r'flags\.add\((.*?)\);', player):
        PLAYER_ONLY.update(literal for literal, plus in re.findall(r'"([a-z_]+)"(\s*\+)?', call) if not plus)
PLAYER_ONLY -= FLAGS_ELSEWHERE
MOODS = {'calm', 'anxious', 'panicked', 'confident', 'frustrated', 'bored', 'triumphant', 'grieving', 'angry'}
FLAGS |= {'mood_' + m for m in MOODS} | {'strangers', 'friends', 'rivals', 'acquainted'}


def known_flag(flag):
    return flag in FLAGS or any(flag.startswith(p) for p in FLAG_PREFIXES)


FILL = re.compile(r'\{([ab])\.([a-z_]+)\}')

errors = []
warnings = []


def error(where, what):
    errors.append('%s: %s' % (where, what))


def warn(where, what):
    warnings.append('%s: %s' % (where, what))


topics = []
links = {}
for path in sorted(glob.glob(TALK + '/*.json')):
    name = os.path.basename(path)
    try:
        data = json.load(open(path, encoding='utf-8'))
    except Exception as e:
        error(name, 'not readable JSON: %s' % e)
        continue
    for topic in data.get('topics', []):
        topics.append((name, topic))
    for topic_id, link in data.get('links', {}).items():
        links.setdefault(topic_id, {}).update(link)


def web(topic):
    w = {k: topic[k] for k in ('tags', 'leads', 'next', 'cost', 'about', 'follow_up', 'opener') if k in topic}
    w.update(links.get(topic['id'], {}))
    w.setdefault('tags', [t for t in topic['id'].split('_') if t and t not in NOT_TAGS])
    w.setdefault('leads', w['tags'])
    w.setdefault('next', [])
    return w


def check_condition(where, condition):
    c = condition[1:] if condition.startswith('!') else condition
    if c.startswith('t:'):
        return
    if c.startswith('a:') or c.startswith('b:'):
        if not known_flag(c[2:]):
            error(where, 'condition "%s": nothing sets %s' % (condition, c[2:]))
        elif c[2:] in PLAYER_ONLY:
            error(where, 'condition "%s": %s is only set about players, never between Drifters' % (condition, c[2:]))
    elif not known_flag(c):
        error(where, 'condition "%s": nothing sets it' % condition)


def all_lines(element):
    """Every line in a lines field (a list, or voices), with its 'then' answers."""
    out = []
    groups = [element] if isinstance(element, list) else list(element.values())
    for group in groups:
        for line in group:
            if isinstance(line, dict):
                out.append(line['line'])
                out += line.get('then', [])
            else:
                out.append(line)
    return out


ids = {}
said = {}
for name, topic in topics:
    tid = topic.get('id', '?')
    where = '%s %s' % (name, tid)
    if tid in ids:
        error(where, 'id also used in ' + ids[tid])
    ids[tid] = name
    needs = topic.get('needs', [])
    for condition in needs:
        check_condition(where, condition)
    if not topic.get('turns'):
        error(where, 'no turns')
        continue
    for i, turn in enumerate(topic['turns']):
        at = '%s turn %d' % (where, i + 1)
        if turn.get('by') not in ('a', 'b'):
            error(at, 'by "%s" (a or b)' % turn.get('by'))
        for condition in turn.get('if', []):
            check_condition(at, condition)
        options = turn.get('options', [turn])
        for option in options:
            for condition in option.get('if', []) if option is not turn else []:
                check_condition(at, condition)
            if 'lines' not in option:
                error(at, 'no lines')
                continue
            lines = all_lines(option['lines'])
            if not lines:
                error(at, 'no lines')
            for line in lines:
                said.setdefault(line.lower(), set()).add(tid)
                for who, word in FILL.findall(line):
                    if word not in WORDS:
                        error(at, '{%s.%s}: the game fills no such word' % (who, word))

# The web.
tag_count = {}
webs = {}
for name, topic in topics:
    w = web(topic)
    webs[topic['id']] = w
    for tag in w['tags']:
        tag_count[tag] = tag_count.get(tag, 0) + 1
for topic_id in links:
    if topic_id not in ids:
        error('links ' + topic_id, 'no topic with that id')

led_to = set()
for tid, w in webs.items():
    for tag in w['leads']:
        if tag not in tag_count:
            error(tid, 'leads to "%s", which no topic is about' % tag)
        elif tag == 'story_part':
            error(tid, 'leads to story_part (the later part of some other story): give it leads of its own')
    for other in w['next']:
        if other not in ids:
            error(tid, 'next "%s": no such topic' % other)
        led_to.add(other)
    reaches = {o for tag in w['leads'] for o, ow in webs.items() if tag in ow['tags']} | set(w['next'])
    led_to |= reaches - {tid}
    if not reaches - {tid}:
        warn(tid, 'leads nowhere else')
for name, topic in topics:
    # (Option leads, too: an argument leads to argument topics.)
    for turn in topic['turns']:
        for option in turn.get('options', [turn]):
            for tag in option.get('leads', []):
                if tag not in tag_count:
                    error(topic['id'], 'a line leads to "%s", which no topic is about' % tag)
                led_to |= {o for o, ow in webs.items() if tag in ow['tags']}

for tid, w in webs.items():
    if tid not in led_to and not w.get('opener'):
        warn(tid, 'nothing leads to it (only starts a talk)')
for tag, n in tag_count.items():
    if n == 1:
        warn('tag ' + tag, 'only one topic is about it (a typo?)')
repeated = [(line, sorted(t)) for line, t in said.items() if len(t) > 1 and len(line) > 12]

lines_total = sum(len(t) for t in said.values())
print('%d topics, %d tags, %d distinct lines' % (len(topics), len(tag_count), len(said)))
if not QUIET:
    for w in warnings:
        print('warning: ' + w)
    if repeated:
        print('%d lines said in more than one topic (fine now and then):' % len(repeated))
        for line, where in repeated[:40]:
            print('  "%s": %s' % (line, ', '.join(where)))
else:
    print('%d warnings, %d repeated lines' % (len(warnings), len(repeated)))
for e in errors:
    print('ERROR: ' + e)
sys.exit(1 if errors else 0)
