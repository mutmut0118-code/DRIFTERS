"""The canned chat line pools (not the talk topics): where each is, what it says now, and its fill words.

Used by merge.py (adds lines to them) and to write the briefs for whoever writes more lines.
A pool id is "<file stem>:<key>" for the chat data (reactions:hi, encounters:encounter.duel.ask, town:bell.raiders) and
"whim:<file>:<json path>" for a whim's chat/say lines.
"""
import glob
import json
import os
import re

CHAT_FILES = {
    'reactions': 'src/main/resources/data/drifter/drifter_chat/reactions.json',
    'encounters': 'src/main/resources/data/drifter/drifter_chat/encounters.json',
    'rounds': 'src/main/resources/data/drifter/drifter_chat/rounds.json',
    'alerts': 'src/main/resources/data/drifter/drifter_chat/alerts.json',
    'convo': 'src/main/resources/data/drifter/drifter_chat/convo.json',
    'convo_tone': 'src/main/resources/data/drifter/drifter_chat/convo_tone.json',
    'town_talk': 'src/civ/resources/data/drifter_civ/drifter_chat/town_talk.json',
    'friends': 'src/civ/resources/data/drifter_civ/drifter_chat/friends.json',
    'town': 'src/civ/resources/data/drifter_civ/drifter_chat/town.json',
}
WHIM_GLOBS = ['src/main/resources/data/drifter/drifter_whims/*.json', 'src/civ/resources/data/*/drifter_whims/*.json']

# Keys in those files that aren't canned chat lines: the running jokes' words (used inside talk topics).
SKIP_KEYS = re.compile(r'^joke\.')
VOICES = ['any', 'casual', 'newbie', 'builder', 'miner', 'homebody', 'speedrunner', 'nomad', 'hoarder', 'hermit', 'pvp_sweat',
          'bandit', 'helper']
WORD = re.compile(r'\{([a-z_.]+)\}')


def words(line):
    return set(WORD.findall(line))


class Pool:
    def __init__(self, pid, file, path, voices, about):
        self.id = pid
        self.file = file          # repo-relative
        self.path = path          # list: json path to the key's object (chat) or to the list (whims)
        self.voices = voices      # voice -> [lines]
        self.about = about        # when it's said
        all_lines = [l for v in voices.values() for l in v]
        self.optional = set().union(*[words(l) for l in all_lines]) if all_lines else set()
        common = None
        for l in all_lines:
            common = words(l) if common is None else common & words(l)
        self.required = common if (common and len(all_lines) >= 1) else set()

    def count(self):
        return sum(len(v) for v in self.voices.values())


def _descriptions(root):
    """'#### `key`' sections of docs/CHAT_LINES.md: the paragraph under each."""
    out = {}
    path = os.path.join(root, 'docs/CHAT_LINES.md')
    if not os.path.exists(path):
        return out
    text = open(path, encoding='utf-8').read().split('\n')
    for i, line in enumerate(text):
        m = re.match(r'^#### `([^`]+)`', line)
        if m:
            j = i + 1
            while j < len(text) and not text[j].strip():
                j += 1
            if j < len(text) and not text[j].startswith('- **'):
                out[m.group(1)] = text[j].strip()
    return out


EXTRA_ABOUT = {
    'alarm.shout': 'A Drifter runs to a mine mouth and shouts down it to call everyone up to the alarm bell. {why} is what the alarm is about, a noun phrase like "the raiders", "the massacre", "the killing of Bob", "the war on Oakvale".',
    'alarm.shout_plain': 'The same shout down the mine when nobody knows what the alarm is for.',
    'crier.open': "The town crier at the plaza reads the paper out: the first headline ({news}, e.g. \"bandits seen near Oakvale\" or \"Steve built a castle\").",
    'crier.paper': 'The crier hands a copy of the paper to a player who came to the plaza.',
    'town.greet.welcome': 'A player the town likes walks into town; the nearest townsperson who sees them greets them.',
    'town.greet.wary': 'A player the town is wary of (small trouble before) walks into town.',
    'town.greet.unwelcome': 'A player the town dislikes (crimes there) walks into town.',
    'town.greet.wanted': 'A player wanted by the town walks in; the townsperson calls for the guards.',
    'town.festival.founding': "The leader opens the town's founding day festival in the morning. {years} is like \"3 years\" or \"1 year\".",
    'town.festival.victory': 'The leader opens victory day, the anniversary of a win in a war over {who} (another town).',
    'town.festival.remembrance': 'The leader opens remembrance day, for {who} (a fallen townsperson or old leader). Quiet, sad, respectful.',
    'town.festival.harvest': 'The leader opens the harvest festival.',
    'bell.festival.founding': 'The bell ringer shouts it while ringing the town bell for founding day.',
    'bell.festival.victory': 'The bell ringer shouts it while ringing the bell for victory day.',
    'bell.festival.remembrance': 'The bell ringer shouts it while ringing the bell for remembrance day (quiet, respectful).',
    'bell.festival.harvest': 'The bell ringer shouts it while ringing the bell for the harvest festival.',
    'town.market.open': 'The stall keeper announces market day in the morning.',
    'bell.market': 'The bell ringer shouts it while ringing the bell for market day.',
    'town.contest.won': 'A townsperson won the town fishing contest by evening. {count} is the number of fish they caught.',
    'town.notice.progress': 'A player handed in part of what a notice-board request asked for; {given} handed so far of {count} wanted.',
    'town.notice.paid': 'The notice was filled and the poster pays the promised reward in full.',
    'town.notice.short': 'The notice was filled but the poster could not pay all of it (ran out).',
    'town.bounty.paid': 'A player brought the {what} a town notice asked for; the poster thanks them, paid in full.',
    'town.bounty.short': 'The same, but the till could only pay part of it.',
    'service.busy': "The town's enchanter or smith is already doing a job for someone else when a player throws it something.",
    'service.refuse': 'A player wanted by the town asks the enchanter or smith for a job; it refuses.',
    'service.start': 'The enchanter/smith has been paid and has everything; it starts the job.',
    'service.quote.enchant': 'A player threw the enchanter an item to enchant; it names the price ({pay}, like "3 emeralds" or "2 emeralds more").',
    'service.quote.netherite': 'A player threw the smith a diamond tool/armor to upgrade to netherite; it names the price {pay} and says it needs the netherite ingot and the upgrade template too.',
    'service.quote.repair': 'A player threw the smith something to repair; it names the price {pay} and asks for the materials to repair it with.',
    'service.failed': 'The enchanter/smith could not do the job; it gives everything back.',
    'service.done': 'The job is done; it hands the item back to the player ({player}).',
    'service.timeout': 'The player never paid in time; it gives their things back.',
    'bell.war.declare': 'The bell ringer of a town that just declared war on {them} (another town) rallies its people.',
    'bell.war.declared': 'The bell ringer of a town that {them} just declared war on raises the alarm.',
    'bell.raid.march': "The bell ringer calls the town's fighters to gather for a raid they're marching on {enemy} (another town).",
    'bell.wanted': 'Alarm bell: a wanted player ({player}) came back to town.',
    'bell.killing': 'Alarm bell: {player} killed a townsperson ({victim}).',
    'bell.massacre': 'Alarm bell: {player} is killing many townspeople.',
    'bell.murder_leader': "Alarm bell: {player} murdered the town's leader {victim} ({victim_caps} is the name in capitals, for shouting).",
    'bell.raiders': 'Alarm bell: pillager raiders are attacking the town.',
}


def _friends_about(key):
    base = {
        'friends.up.friend': 'Its friendship with a player just went up to "friend" (it tells them so).',
        'friends.up.best': 'Its friendship with a player just went up to "best friend".',
        'friends.back_from_cool': "A friendship that had cooled (they hadn't seen each other) warms back up when they meet again.",
        'friends.brush_off': 'A player it is not friends with asks for a friend perk; it brushes them off.',
        'friends.food': 'A friend player is low on food; it gives them some.',
        'friends.invite.night': 'It invites a friend player in for the night (to its house).',
        'friends.invite.rain': 'It invites a friend player in out of the rain.',
        'friends.bed': 'It offers a friend player its spare bed.',
        'friends.tip.ore': 'It tells a friend player where it saw ore ({what} the ore, {where} where).',
        'friends.tip.diamonds': 'It tells a friend player where it saw diamonds or ancient debris ({what}, {where}).',
        'friends.tip.place': 'It tells a friend player about a place it found ({what}, {where}).',
        'friends.tip.town': 'It tells a friend player about another town ({town}) and which way it is ({way}).',
        'friends.tip.bandits': 'It warns a friend player about bandits ({warning} is what the town warns of).',
        'friends.warn.wanted': 'It warns a friend player that the town ({town}) has them wanted.',
        'friends.warn.wanted_elsewhere': 'It warns a friend player that another town ({town}) has them wanted.',
        'friends.favor.flaked': 'A friend player promised a favor ({what}) and never did it.',
        'friends.favor.thanks.iron': 'A friend player did the favor it asked: brought it iron.',
        'friends.favor.thanks.food': 'A friend player did the favor it asked: brought it food.',
        'friends.favor.thanks.zombies': 'A friend player did the favor it asked: cleared zombies near its place.',
    }
    return base.get(key, '')


def chat_pools(root):
    desc = _descriptions(root)
    pools = []
    for stem, rel in CHAT_FILES.items():
        path = os.path.join(root, rel)
        if not os.path.exists(path):
            continue
        data = json.load(open(path, encoding='utf-8'))
        for key, v in data.get('lines', {}).items():
            if SKIP_KEYS.match(key):
                continue
            voices = {'any': v} if isinstance(v, list) else v
            about = desc.get(key) or EXTRA_ABOUT.get(key) or (_friends_about(key) if stem == 'friends' else '')
            pools.append(Pool(f'{stem}:{key}', rel, ['lines', key], voices, about))
    return pools


def _walk_whim(o, path, whim, out):
    if isinstance(o, dict):
        here = o if 'id' in o and isinstance(o.get('id'), str) and 'category' in o else whim
        for k, v in o.items():
            if k in ('chat', 'lines') and isinstance(v, list) and v and all(isinstance(x, str) for x in v):
                out.append((path + [k], here, o))
            else:
                _walk_whim(v, path + [k], here, out)
    elif isinstance(o, list):
        for i, x in enumerate(o):
            _walk_whim(x, path + [i], whim, out)


def whim_pools(root):
    pools = []
    for g in WHIM_GLOBS:
        for path in sorted(glob.glob(os.path.join(root, g))):
            rel = os.path.relpath(path, root).replace('\\', '/')
            data = json.load(open(path, encoding='utf-8'))
            found = []
            _walk_whim(data, [], None, found)
            for p, whim, holder in found:
                if whim is None or whim.get('action') == 'sign':
                    continue  # sign text, not chat
                lines = holder[p[-1]]
                steps = whim.get('params', {}).get('steps')
                ctx = ''
                if isinstance(steps, list):
                    ctx = ' Steps: ' + ' > '.join(
                        s.get('do', '?') + (f"({s.get('target')})" if s.get('target') else '') + (' [THIS LINE]' if s is holder else '')
                        for s in steps if isinstance(s, dict))
                about = (f"Whim '{whim['id']}' ({whim.get('category')}, action {whim.get('action')}"
                         + (f", when {whim['when']}" if whim.get('when') else '') + f"): {whim.get('thought', '')}." + ctx
                         + (' Said with a friend/another Drifter nearby.' if 'friend' in whim['id'] or 'share' in whim['id'] else ''))
                pools.append(Pool('whim:' + os.path.basename(path) + ':' + '/'.join(map(str, p)), rel, p, {'any': lines}, about))
    return pools


def all_pools(root):
    return chat_pools(root) + whim_pools(root)


if __name__ == '__main__':
    import sys
    root = sys.argv[1] if len(sys.argv) > 1 else '.'
    ps = all_pools(root)
    for p in ps:
        print(f'{p.id}\t{p.count()}\treq={sorted(p.required)} opt={sorted(p.optional)}\t{p.about[:80]}')
    print(len(ps), 'pools,', sum(p.count() for p in ps), 'lines')
