"""Writes docs/CHAT_LINES.md from the chat data and the whims. Usage: python tools/gen_chat_doc.py <project dir>"""
import glob
import json
import os
import sys

ROOT = sys.argv[1]
CHAT = os.path.join(ROOT, 'src/main/resources/data/drifter/drifter_chat')
WHIMS = os.path.join(ROOT, 'src/main/resources/data/drifter/drifter_whims')

WHEN = {
    'hi': 'A friendly Drifter that likes you walks over and greets you (crouching at you).',
    'huh': 'You say its name in chat (or a part of it, 4 letters or more) within 32 blocks: it turns to look for you, nods, and may answer.',
    'thanks': 'You throw it an item from close by (a gift), or kill a mob that was attacking it.',
    'gg_won': 'It killed you (a hostile Drifter, over your body).',
    'gg_lost': 'You killed it (said as it dies).',
    'stop': 'Warning you off: it doesn\'t trust you and won\'t fight, so it tells you to back off.',
    'ouch': 'You hit it once (the first hit).',
    'thief': 'It caught you at its chest with things gone, or sees you holding something missing from its chest.',
    'war_cry': 'A raid (bandits, or a group at war) reaches the base it\'s raiding.',
    'greet_back': 'You say hi in chat (hi, hey, yo, o/, sup, gm...): one or two Drifters within 64 blocks who don\'t mind you say it back.',
    'welcome_back': 'You join the world and a Drifter round you knows you.',
    'welcome_new': 'You join and none of the Drifters round you know you.',
    'bye_back': 'You say bye in chat (cya, gtg, brb, o7...), or leave the game (by day).',
    'bye_night': 'You leave the game at night.',
    'night_back': 'You say good night in chat (gn, night, going to bed...).',
    'you_welcome': 'You say thanks in chat (ty, thx, tysm...): the nearest friendly Drifter within 16 blocks.',
    'rip': 'Your death message (Drifters within 160 blocks read chat): an ordinary death.',
    'rip_funny': 'Your death message, a silly death: a fall, lava, drowning, a cactus, a berry bush, a wall, starving, fire...',
    'rip_drifter': 'A Drifter\'s death message: another Drifter within 160 blocks (now and then).',
    'rip_friend': 'A Drifter\'s death message: one of its friends (always).',
    'congrats': 'You make an advancement that shows in chat: one or two Drifters within 160 blocks.',
    'shop.nothing': 'You right-click a town\'s stall keeper who has nothing on offer (only you see it).',
    'news.deed': 'Two Drifters meet, one passes on news of something a player did ({what}); said a third of the time, with a player within 16 blocks.',
    'news.death': 'The same, news of a Drifter\'s death ({what} is its death message).',
    'trade.ask': 'A Drifter asks another for things, for free (a player within 16 blocks).',
    'trade.ask_pay': 'A Drifter asks another for things, offering pay.',
    'trade.give_free': 'The one asked says yes, giving it free.',
    'trade.agree': 'The one asked says yes to a paid trade.',
    'trade.handover_gift': 'The giver hands over a gift.',
    'trade.handover_paid': 'The giver hands over a paid trade.',
    'encounter.unsure': 'You answer an escort or a race with something unclear (maybe, idk, hmm, why...): it asks again.',
}

ENC = {
    'hungry': 'Hungry traveller', 'injured': 'Injured traveller', 'lostdog': 'Lost dog', 'escort': 'Escort', 'miner': 'Trapped miner',
    'hostage': 'Hostage', 'horde': 'Chased by a horde', 'duel': 'Duel', 'race': 'Race', 'merchant': 'Travelling merchant',
    'party': 'Campfire party', 'mugging': 'Mugging', 'ambush': 'Ambush', 'burglar': 'Burglar', 'thief': 'Thief', 'bounty': 'Bounty hunters',
    'revenge': 'Revenge squad', 'raid': 'Village raid', 'feud': 'Feud', 'war': 'Warbands', 'wanted': 'Wanted poster and reward',
    'convoy': 'Convoy', 'mourner': 'Mourner', 'builders': 'Builders',
}

ENC_WHEN = {
    'hungry.ask': 'It walks up to you.', 'hungry.thanks': 'You threw it food; it gives you something.', 'hungry.giveup': 'Nobody fed it.',
    'injured.help_unseen': 'Running from zombies, before it sees you.', 'injured.help': 'Running from zombies, once it sees you.',
    'injured.thanks': 'You killed the zombies and it lived.',
    'lostdog.ask': 'It asks you, handing you a lead.', 'lostdog.call': 'Calling for its dog.', 'lostdog.found': 'The dog came back.',
    'lostdog.thanks': 'You brought the dog back.', 'lostdog.dead': 'The dog died.',
    'escort.ask': 'It asks you to walk it to a village.', 'escort.yes': 'You said yes (section 10).', 'escort.no': 'You said no.',
    'escort.noanswer': 'No answer within a minute.', 'escort.bandit': 'A bandit, when bandits jump you on the way.', 'escort.waitup': 'You got too far ahead.',
    'escort.alone': 'You left it behind.', 'escort.arrived': 'You got there; it pays.',
    'miner.call1': 'Calling from underground (first call).', 'miner.call2': 'Second call.', 'miner.call3': 'Third call.', 'miner.freed': 'You dug through to it.',
    'miner.thanks': 'It pays you.',
    'hostage.call1': 'Calling out from the bandit camp.', 'hostage.call2': 'Calling out again.', 'hostage.seen': 'It sees you.', 'hostage.bandit': 'A bandit, when you turn up.',
    'hostage.freed': 'You beat the bandits.', 'hostage.thanks': 'It pays you.',
    'horde.run': 'Someone runs past with a pack of zombies behind them.',
    'duel.ask': 'It challenges you (hit it to accept).', 'duel.accept': 'You hit it: the duel is on.', 'duel.coward': 'You didn\'t hit it in time, or walked off.',
    'duel.yield': 'It\'s low on health and gives up.', 'duel.paid': 'You won; it pays.', 'duel.spare': 'You\'re low on health: it stops and lets you off.',
    'duel.after_yield': 'You kept hitting it after it gave up.',
    'race.ask': 'It challenges you (say go).', 'race.no': 'You said no.', 'race.noanswer': 'No answer within a minute.', 'race.count': 'The countdown (3, 2, 1).',
    'race.go': 'Go!', 'race.player_won': 'You got there first.', 'race.paid': 'It pays you.', 'race.won': 'It got there first.',
    'merchant.hello': 'It walks up to you (right-click to trade).', 'merchant.soldout': 'It has nothing left.', 'merchant.bye': 'It moves on.',
    'party.invite': 'Evening: they call you over.', 'party.cheer': 'Cheering during the party.', 'party.end': 'The party\'s over.',
    'mugging.demand': 'A bandit stops you (throw it the money).', 'mugging.paid': 'You threw it enough.', 'mugging.refused': 'You hit it, left or waited too long.',
    'ambush.spring': 'Bandits lying in wait spring the trap.', 'burglar.caught': 'A thief in your base is caught after taking something.',
    'thief.shout': 'A Drifter chasing someone who robbed it.', 'thief.thanks': 'You stopped the thief; it pays you.',
    'bounty.found': 'Hunters come for the price on your head.', 'bounty.done': 'They killed you.',
    'revenge.found': 'The friends of a Drifter you killed come for you.', 'revenge.found_murder': 'The same, if it had given up.',
    'revenge.join': 'Joining the fight.', 'revenge.done': 'They killed you.',
    'raid.attack': 'Raiders reaching the village you\'re in.', 'raid.retreat': 'The raiders lost.',
    'feud.shout': 'Two Drifters fighting each other.', 'feud.thanks': 'You helped one side and it won.',
    'war.rally': 'Captains rallying their side.', 'war.regret': 'You attacked one side.', 'war.stay_out': 'A soldier, when you get in the middle.',
    'war.victory': 'One side won.', 'war.retreat': 'The other side lost.', 'war.thanks_chest': 'You fought for the winners (a war chest).',
    'war.thanks_field': 'You fought for the winners (no chest).',
    'wanted.poster': 'A Drifter tells you about a wanted Drifter...', 'wanted.map_home': '...and hands you a map to where it lives.',
    'wanted.map_seen': '...or to where it was last seen.', 'wanted.reward': 'You killed the wanted Drifter; it pays.',
    'convoy.hello_morning': 'Riders passing through greet you (before noon).', 'convoy.hello': 'The same, after noon.',
    'mourner.1': 'At a grave by the road, first.', 'mourner.2': 'Then.', 'mourner.3': 'Last.',
    'builders.hello': 'A crew turns up to build a waystation.', 'builders.done': 'They finished it.',
}

VOICE = {'any': 'everyone', 'casual': 'casual', 'newbie': 'newbie', 'builder': 'builder', 'miner': 'miner', 'homebody': 'homebody',
         'speedrunner': 'speedrunner', 'nomad': 'nomad', 'hoarder': 'hoarder', 'hermit': 'hermit', 'pvp_sweat': 'PvP sweat', 'bandit': 'bandit', 'helper': 'helper'}

lines = {}
answers = {}
slang = {}
for path in sorted(glob.glob(CHAT + '/*.json')):
    data = json.load(open(path, encoding='utf-8'))
    slang.update(data.get('slang', {}))
    for key, value in data.get('lines', {}).items():
        voices = lines.setdefault(key, {})
        if isinstance(value, list):
            voices.setdefault('any', []).extend(value)
        else:
            for voice, more in value.items():
                voices.setdefault(voice, []).extend(more)
    for intent, phrases in data.get('answers', {}).items():
        answers.setdefault(intent, []).extend(phrases)


def esc(text):
    return text.replace('|', '\\|').replace('<', '&lt;')


def block(key, when):
    out = ['#### `' + key + '`', '', when, '']
    for voice, said in lines.get(key, {}).items():
        out.append('- **' + VOICE.get(voice, voice) + ':** ' + ' · '.join(esc(s) for s in said))
    out.append('')
    return out


total = sum(len(v) for k in lines.values() for v in k.values())
whim_lines = []
for path in sorted(glob.glob(WHIMS + '/*.json')):
    for whim in json.load(open(path, encoding='utf-8'))['whims']:
        said = list(whim.get('chat', []))
        if whim.get('action') == 'chat':
            said += whim.get('params', {}).get('lines', [])
        for step in whim.get('params', {}).get('steps', []):
            if step.get('do') == 'say':
                said += step['lines']
        if whim.get('action') == 'oops_drop':
            said += ['oops']
        if said:
            whim_lines.append((whim['id'], whim.get('thought', ''), whim.get('when', 'any'), whim.get('action'), said))

whim_total = sum(len(w[4]) for w in whim_lines)
answer_total = sum(len(v) for v in answers.values())

doc = []
doc += ['# Every chat line a Drifter says', '',
        'Everything Drifters say in chat, when they say it, and what you can say to them (generated from the data files; '
        '%d lines in the chat data, %d in whims, and %d answer phrases they understand).' % (total, whim_total, answer_total), '',
        'Lines come from `data/drifter/drifter_chat/*.json` (a datapack can add more: files add to each other) and the whims\' own files. '
        'They\'re written the way players type in Minecraft chat (the user\'s guidebooks "How Minecraft Players Chat" and "The Science of Gamer '
        'Text Chat"): lowercase, no final period, clipped words (dias, obby, coords), rituals (o/, wb, rip, gg, gn, o7), and each personality its '
        'own voice. `/drifter config chat false` turns all of it off.', '',
        'In the lines, `{player}` is your name, `{name}` the Drifter\'s own, `{who}` another\'s, `{way}` a direction, `{coords}` x y z, and '
        'the rest as each says.', '',
        '## How it picks and how often it talks', '',
        '- **Its voice.** A Drifter of a personality with lines of its own (newbie, builder, PvP sweat, bandit...) uses them about two times in three; '
        'otherwise everyone\'s.',
        '- **Who hears it.** Only players near the Drifter (48 blocks, set by "Chat heard within" on the config screen), plus the player an '
        'encounter is with; the server log gets everything.',
        '- **Not twice in a row.** Each remembers its last couple dozen lines and picks a different one while it can, and not a line anyone '
        'near said in the last half minute (one "gm", then "morning").',
        '- **Chattiness** (config screen, or `/drifter config chattiness quiet|normal|lively`): how much they chat on their own. Quiet is '
        'less than half as much, lively twice as much: their talks with each other and with you, the canned reactions, the rounds, '
        'bystanders. Answers to what you ask them, and encounters, are always said.',
        '- **Typing like players** ("Typos and split messages", or `/drifter config typing true|false`): now and then a typo (letters '
        'swapped, dropped, doubled, the key next to it), the tidy often fixing it with "*word" a moment later; a longer line with a comma '
        '(or a question midway) sometimes sent in two, as a joke and its punchline. Newbies slip most, builders and hermits least; never in '
        'names or numbers.',
        '- **Habits.** For the canned reactions (section 1) it has a habit: the same Drifter greets the same way most of the time, now and then another line.',
        '- **Rate limits.** Each Drifter says one line in 20 seconds at most (the same kind once a minute), and all of them together four in 10 seconds. '
        'Quiet ones (low sociability) mostly don\'t bother. Encounter lines (section 5) are always said: it\'s talking to you.',
        '- **Try them.** `/drifter lines say <key>` makes the nearest Drifter say a line of that kind (with made-up names for the blanks); '
        '`/drifter lines meaning <text>` says what a message would mean to a Drifter.', '']

doc += ['## 1. Reactions (things that happen to it)', '']
for key in ['hi', 'huh', 'thanks', 'gg_won', 'gg_lost', 'stop', 'ouch', 'thief', 'war_cry']:
    doc += block(key, WHEN[key])

doc += ['## 2. Chat rituals (answering you and the game)', '',
        'Players reply to each other and to the game\'s own messages; so do Drifters. A moment after (as someone types), one or two of them.', '']
for key in ['greet_back', 'night_back', 'bye_back', 'bye_night', 'you_welcome', 'welcome_back', 'welcome_new', 'rip', 'rip_funny', 'rip_drifter', 'rip_friend', 'congrats']:
    doc += block(key, WHEN[key])

doc += ['## 3. News, trades and the shop', '']
for key in ['news.deed', 'news.death', 'trade.ask', 'trade.ask_pay', 'trade.give_free', 'trade.agree', 'trade.handover_gift', 'trade.handover_paid', 'shop.nothing']:
    doc += block(key, WHEN[key])

doc += ['## 4. Whims (things it does for fun)', '',
        'Said as the whim starts (half the time), as the whole of a chat whim, or at one step of it. These live in the whims\' own files '
        '(`data/drifter/drifter_whims/`, docs/WHIMS_HOWTO.md).', '',
        '| Whim | When | Lines |', '| --- | --- | --- |']
for wid, thought, when, action, said in whim_lines:
    doc.append('| %s | %s (%s) | %s |' % (wid, esc(thought), when, ' · '.join(esc(s) for s in said)))
doc.append('')

doc += ['## 5. Encounters (it\'s talking to you)', '', 'Always said, no rate limits.', '']
for key in ['encounter.unsure']:
    doc += block(key, WHEN[key])
for short, title in ENC.items():
    doc += ['### ' + title, '']
    for key in [k for k in lines if k.startswith('encounter.' + short + '.')]:
        doc += block(key, ENC_WHEN.get(key[len('encounter.'):], ''))

TALK = os.path.join(ROOT, 'src/main/resources/data/drifter/drifter_talk')
topics = []
player_topics = []
links = {}
for path in sorted(glob.glob(TALK + '/*.json')):
    data = json.load(open(path, encoding='utf-8'))
    topics += data.get('topics', [])
    player_topics += data.get('player_topics', [])
    for topic_id, link in data.get('links', {}).items():
        links.setdefault(topic_id, {}).update(link)

NOT_TAGS = {'a', 'b', 'the', 'and', 'of', 'to', 'with', 'vs', 'talk', 'chat', 'story', 'show', 'lately'}


def web(topic):
    """A topic's place in the web, as the game reads it: its own fields, a link's over them, tags from its id if none."""
    w = {k: topic[k] for k in ('tags', 'leads', 'next', 'cost', 'about', 'follow_up', 'opener') if k in topic}
    w.update(links.get(topic['id'], {}))
    w.setdefault('tags', [t for t in topic['id'].split('_') if t and t not in NOT_TAGS])
    w.setdefault('leads', w['tags'])
    return w


def said(lines):
    out = []
    if isinstance(lines, list):
        lines = {'any': lines}
    for voice, items in lines.items():
        parts = []
        for item in items:
            if isinstance(item, dict):
                parts.append(esc(item['line']) + ' *(answered: ' + ' / '.join(esc(a) for a in item['then']) + ')*')
            else:
                parts.append(esc(item))
        out.append(('**' + VOICE.get(voice, voice) + ':** ' if voice != 'any' else '') + ' · '.join(parts))
    return out


talk_lines = 0
for topic in topics + player_topics:
    for turn in topic['turns']:
        for source in turn.get('options', [turn]):
            topic_lines = source['lines']
            for items in (topic_lines.values() if isinstance(topic_lines, dict) else [topic_lines]):
                for item in items:
                    talk_lines += 1 + (len(item['then']) if isinstance(item, dict) else 0)

doc += ['## 6. Conversations between Drifters', '',
        'Two Drifters near each other with nothing pressing, and a player near enough to overhear, now and then strike up a conversation '
        '(%d topics, %d lines). They walk up to each other and face each other, and take turns, a moment between each as it is typed. '
        '**a** brings it up, **b** answers. A topic comes up only when its conditions hold: what each has done lately (their diary: ore mined, '
        'a death, a close call, mobs killed, a build, a trade, a place found), their gear, mood, home and town, how they feel about each other, '
        'news one has that the other does not, players they like or distrust, and the time and weather. A question carries its own answers. '
        'Lines are in `data/drifter/drifter_talk/`; `/drifter talk [topic]` makes the nearest two talk, `/drifter diary` shows what one has done.' % (len(topics), talk_lines), '']
def render(topic):
    global doc
    needs = ', '.join(topic.get('needs', [])) or 'any time'
    w = web(topic)
    place = '*About:* ' + ', '.join(w['tags']) + '  ·  *Leads to:* ' + ', '.join(w['leads'] + w.get('next', []))
    if w.get('opener'):
        place += '  ·  only starts a talk'
    if w.get('follow_up'):
        place += '  ·  only follows another'
    if w.get('cost', 1) != 1:
        place += '  ·  uses %g interest' % w['cost']
    doc += ['#### ' + topic['id'].replace('_', ' '), '', '*Needs:* ' + needs + ('  ·  *Effects:* ' + ', '.join(topic['effects']) if topic.get('effects') else ''), '',
            place, '']
    for i, turn in enumerate(topic['turns']):
        head = '%d. **%s**' % (i + 1, turn['by'])
        if turn.get('chance', 1) < 1:
            head += ' (%d%%)' % round(turn['chance'] * 100)
        if turn.get('if'):
            head += ' if ' + ', '.join(turn['if'])
        if turn.get('mark') or turn.get('leads'):
            head += ' *(' + '; '.join((['marks ' + ', '.join(turn['mark'])] if turn.get('mark') else [])
                                      + (['leads to ' + ', '.join(turn['leads'])] if turn.get('leads') else [])) + ')*'

        if 'options' in turn:
            doc.append(head + ':')
            for option in turn['options']:
                cond = ', '.join(option.get('if', [])) or 'otherwise'
                for line in said(option['lines']):
                    doc.append('   - *' + cond + ':* ' + line)
        else:
            for j, line in enumerate(said(turn['lines'])):
                doc.append((head + ': ' if j == 0 else '   - ') + line)
    if topic.get('question'):
        q = topic['question']
        doc.append('- *You say yes* (' + (q['yes'].get('do') or 'it just answers') + '): ' + ' · '.join(said(q['yes']['lines'])))
        doc.append('- *You say no:* ' + ' · '.join(said(q['no']['lines'])))
    if topic.get('chime'):
        doc.append('- *Someone standing by may add:* ' + ' · '.join(said(topic['chime'])))
    doc.append('')


for topic in topics:
    render(topic)

TALK_WHEN = {
    'talk.segue.linked': 'Going on to a topic linked to the last one: said in front of its first line (about half the time).',
    'talk.segue.change': 'Changing the subject (now and then, or when nothing linked suits them): in front of the new topic\'s first line.',
    'talk.resume': 'Picking up a talk that was cut short, the next time the two meet (within a day): in front of the first line. {about} is what they were on.',
    'talk.bye': 'A long talk ends (their interest is used up): one says goodbye.',
    'talk.bye_back': 'The other answers the goodbye.',
    'talk.cut.trouble': 'A talk cut short by trouble (a fight, panic, fire): the one in trouble says so.',
    'talk.cut.away': 'A long talk cut short because one walked too far off (48 blocks): sometimes.',
}
tag_count = {}
for topic in topics:
    for tag in web(topic)['tags']:
        tag_count[tag] = tag_count.get(tag, 0) + 1
doc += ['## 6b. Long conversations (the topic web)', '',
        'A conversation can go on from topic to topic. Each topic says what it is **about** (tags) and what it **leads to** (tags, and '
        'sometimes topics by name). When a topic ends, they go on to one it leads to that suits them (either of them can bring it up, so it '
        'goes back and forth), or now and then (15%%) change the subject. A topic is never repeated within one talk; greetings only start a talk; '
        'follow-ups (like "agree to disagree") only come after another topic.', '',
        '- **How long:** each Drifter has interest in the talk: the chattier, the more; strangers a topic or two, acquaintances two or three, '
        'friends three to five, best friends (trust 80+) up to ten; bored, more; angry or anxious, less. Each topic uses some (jokes half). '
        'At most 10 topics or 5 minutes.',
        '- **Working while they talk:** past the first topic they go back to what they were doing and keep chatting (a little slower, as '
        'they type while working), as long as they stay within 48 blocks. With nothing to do they keep standing together.',
        '- **Arguments:** a line can **mark** the talk: "disagree" costs both a little trust when the topic ends, "agree" builds it. A '
        'disagreement leads on to **argument** topics: friends agree to disagree, rivals keep at it.',
        '- **Cut short** (a mob, getting hurt, walking off): the one in trouble says "hold up"; the next time the two meet (within a day) '
        'they pick it up: "anyway, about the ore".',
        '- **Try it:** `/drifter talk long [topic]` starts the nearest two on a long talk (plenty of interest, whatever their bond); '
        '`/drifter talk status` shows each talk going on, its topics so far and the interest left; `/drifter talk links <topic>` what a '
        'topic leads on to.',
        '- **Writing more:** a new topic only needs `tags` and `leads` to join the web (with none, it is about the words of its id). '
        '`links` in `drifter_talk/web.json` place topics written in other files. Themed batches live in `drifter_talk/topics_*.json`; '
        '`python tools/check_talk_web.py .` checks them (unknown tags and conditions, words the game never fills, topics nothing '
        'leads to).',
        '- **Which topic opens a talk:** when one of them has something real to talk about (a topic that needs a find, a death, the '
        'weather, a mood...), that comes first 7 times in 10; otherwise an everyday topic (best ore, fav food...). Everyday topics '
        'mostly come up as a talk wanders on.', '',
        '**Tags in use** (topics about each): ' + ', '.join('%s (%d)' % (t, n) for t, n in sorted(tag_count.items(), key=lambda x: (-x[1], x[0]))), '']
for key in TALK_WHEN:
    doc += block(key, TALK_WHEN[key])

doc += ['## 7. Drifters talking to you', '',
        'A Drifter near you (8 blocks, seeing you) with nothing pressing now and then brings something up with you (%d topics in '
        '`drifter_talk/players.json`; **a** is the Drifter, **b** you): a stranger gets a hi, a friend their name and its news, your gear, '
        'how hurt you look, the night coming, what it found, a running joke about you, and questions you answer in chat ("wanna team up?": '
        'a yes however you say it, and it does it). One Drifter at a time with you, every minute and a half at most; each Drifter every 3 to '
        '15 minutes (the chatty sooner). `/drifter talk me [topic]` makes the nearest one bring something up with you.' % len(player_topics), '',
        '**Running jokes.** Drifters who see someone (you, or a Drifter) die a silly death (lava, a fall, drowning, a cactus...) have a joke '
        'for a week: they bring it up with each other ("remember when Tester fell off a cliff lol", and one who was there: "never forget"), '
        'and with you ("hey its the cliff diver lol"). The words are `joke.what.*` and `joke.nick.*` below.', '',
        '**Rounds.** The first time you\'re about in the morning near two or more Drifters who know you, a few say gm, one after another '
        '(`round.gm`); going to bed near them, gn (`round.gn`).', '',
        '**Standing by.** When a Drifter finishes a talk or answers you, another nearby may add its bit (the *chime* lines of a topic, '
        'and `chime.<what you asked>` below).', '']
for topic in player_topics:
    render(topic)
for key in sorted(k for k in lines if k.startswith('round.') or k.startswith('chime.')):
    doc += block(key, 'A round, or a bystander\'s bit.')
for key in sorted(k for k in lines if k.startswith('joke.')):
    doc.append('- `' + key + '`: ' + ' · '.join(esc(x) for v in lines[key].values() for x in v))
doc.append('')

ASKS = [
    ('wyd', 'What it\'s doing (its job right now)', 'anyone it isn\'t hostile to', ['ask.wyd', 'ask.wyd_idle']),
    ('hru', 'How it is: hurt, hungry, or its mood', 'anyone', ['ask.hru_good', 'ask.hru_triumphant', 'ask.hru_hurt', 'ask.hru_hungry', 'ask.hru_bored',
                                                               'ask.hru_scared', 'ask.hru_angry', 'ask.hru_sad']),
    ('name', 'Its name', 'anyone', ['ask.name']),
    ('coords', 'Where it is', 'its coords to a friend (trust 25+), "around" to someone it knows (10+), else no', ['ask.coords_yes', 'ask.coords_vague', 'ask.coords_no']),
    ('base', 'Where it lives', 'coords to a friend (a helper: to anyone it knows), a direction and distance to someone it knows, else no',
     ['ask.base_yes', 'ask.base_way', 'ask.base_none', 'ask.base_no']),
    ('seen_village, seen_diamonds, seen_iron, seen_dungeon, seen_portal, seen_fortress', 'Somewhere it has seen one (its memory only)',
     'as for its base; a greedy one keeps ore to itself', ['ask.seen_yes', 'ask.seen_way', 'ask.seen_none', 'ask.seen_no']),
    ('follow', 'It comes along (once you walk off; as crouching twice does)', 'a friend, or someone it knows (10+) and has spent time near',
     ['ask.follow_yes', 'ask.follow_no']),
    ('team', 'It comes along for 15 minutes (and helps against mobs, as friends do)', 'a friend', ['ask.team_yes', 'ask.team_no']),
    ('come', 'It walks over to you', 'as follow', ['ask.come_yes', 'ask.follow_no']),
    ('stop', 'It stops following', 'anyone', ['ask.stop']),
    ('help', 'It drops what it\'s doing and runs to you, then sticks around a minute', 'a friend; a helper for anyone it doesn\'t dislike; a brave one it knows',
     ['ask.help_yes', 'ask.help_no']),
    ('give', '"can i have 3 bread": it brings it over and tosses it, from what it can spare, about what you\'re worth to it; no item named, it asks what',
     'a friend, or anyone it owes for a gift', ['ask.give_yes', 'ask.give_back', 'ask.give_need', 'ask.give_none', 'ask.give_no', 'ask.give_much', 'ask.give_what']),
    ('trade', 'Points you to its shop, or asks what you need (then name the item)', 'anyone', ['ask.trade', 'ask.shop']),
    ('duel', 'A friendly 1v1: it fights you, nobody goes below a heart; whoever would have lost it says gg', 'a PvP sweat, a bandit or a brave, aggressive one, '
     'within 20 blocks, not on Peaceful; most others say no', ['ask.duel_yes', 'ask.duel_far', 'ask.duel_no', 'ask.duel_scared', 'ask.duel_won', 'ask.duel_lost']),
    ('compliment', 'It thanks you, and likes you a little more (3 a day count)', 'anyone', ['ask.compliment']),
    ('insult', 'It answers back and likes you less (5 a day count); for a day it brings it up when you ask for something', 'anyone',
     ['ask.insult', 'ask.insult_again', 'ask.refuse_insulted']),
    ('joke', 'A joke', 'anyone', ['ask.joke']),
    ('bot', '"are you a bot"', 'anyone', ['ask.bot']),
]
doc += ['## 8. Asking a Drifter something', '',
        'Say it to a Drifter: with its name (or a good part of it) in the message, or to the one you\'ve been talking with this last minute, '
        'or the one you\'re looking at (16 blocks), or the only one within 10 blocks. It turns to look at you and answers a moment later, as '
        'it\'s typed. What it tells you and what it does depends on how it feels about you (`/drifter social show <you>`): trust 25 is a '
        'friend. To someone it\'s hostile to it only says to get lost. Only what it knows: its memory, its pockets, its home. The phrases '
        'are in section 10 (`requests.json`).', '',
        '| You say (intent) | What it does | For whom |', '| --- | --- | --- |']
for intent, what, who, _ in ASKS:
    doc.append('| ' + intent + ' | ' + what + ' | ' + who + ' |')
doc.append('')
for intent, _, _, keys in ASKS:
    for key in keys:
        doc += block(key, 'Answering **' + intent + '**.')

TELLS = [
    ('call me X / my name is X', 'It calls you that from now on (in its answers and talks with you)', ['told.nick']),
    ('my base is at x y z / i live here / my house is up north', 'It remembers where you live (coords, here, or a direction and distance)',
     ['told.base', 'told.base_here', 'told.base_way']),
    ('theres a village 300 blocks north / found dias at x y z', 'It remembers the place, who said so, and passes it on to other Drifters; every '
     'Drifter within 32 blocks reads it in chat. "here" or "over there" needs a word like "theres" or "found"',
     ['told.place', 'told.place_known', 'told.place_heard']),
    ('going mining / heading to the nether / going home', 'It wishes you luck, and asks how it went a few minutes later', ['told.plan'] + sorted(
        k for k in lines if k.startswith('told.plan.'))),
    ('ill bring you 5 iron / i owe you', 'It holds you to it: reminds you after a day; three days and nothing costs some trust; bring it and it '
     'thanks you for keeping your word (and likes you more)', ['told.promise', 'told.promise_any', 'told.promise_kept', 'told.promise_late_kept']),
    ('i love cats / i hate creepers', 'It remembers (things players talk about, items, creatures) and brings it up', ['told.like', 'told.hate']),
    ('bob is a griefer / annie is cool', 'If it trusts you (10+) and isn\'t sure of them itself, it thinks a little less (or more) of them, once a day '
     'from each teller, and passes the word on', ['told.word_bad', 'told.word_good', 'told.word_bad_disagree', 'told.word_good_disagree', 'told.word_doubt']),
    ('i died / found diamonds / im hungry / im back / im lost', 'It reacts; a hungry friend gets offered food (say yes); it asks after it later',
     sorted(k for k in lines if k.startswith('told.news.')) + ['told.hungry_yes', 'told.hungry_no']),
]
NEW_ASKS = [
    ('me', 'who am i, whats my name, do you remember me', 'What it calls you, and something it remembers', ['ask.me_known', 'ask.me_stranger']),
    ('opinion', 'what do you think of me / of bob, do you like cats, are we cool', 'How it feels about you or them (trust in words), or the thing',
     sorted(k for k in lines if k.startswith('ask.opinion_'))),
    ('where_is', 'wheres bob, have you seen annie', 'In sight, or where and when it last saw them (to someone it knows)',
     ['ask.where_here', 'ask.where_seen', 'ask.where_unknown', 'ask.where_no', 'ask.where_me']),
    ('going', 'where are you going', 'Where its current goal takes it', ['ask.going', 'ask.going_idle']),
    ('need', 'what do you need, need anything', 'What it\'s short of: health, food, a home, the next thing it\'s after',
     ['ask.need_heal', 'ask.need_food', 'ask.need_home', 'ask.need_target', 'ask.need_nothing']),
    ('friends', 'who are your friends', 'The players and Drifters it likes best', ['ask.friends', 'ask.friends_you', 'ask.friends_none']),
    ('told', 'what did i tell you', 'Something you told it (or did: a gift, an insult)', ['ask.told', 'ask.told_none']),
    ('my_base', 'wheres my base', 'Where you said you live, or where it saw you live', ['ask.my_base_told', 'ask.my_base_seen', 'ask.my_base_none']),
    ('forget', 'forget that, forget everything', 'Forgets the last thing you told it, or all of it', ['ask.forget', 'ask.forget_all', 'ask.forget_nothing']),
    ('likes', 'what do you like', 'Its favourite things, in its voice', ['ask.likes']),
    ('seen_... (heard)', 'have you seen a village', 'Not seen one, but told of one: says who said where', ['ask.seen_heard']),
]
doc += ['## 8b. Telling a Drifter things (it remembers)', '',
        'Said to a Drifter as in section 8 (statements, not questions). What it was told is kept with it (saved) and shown by `/drifter heard`; '
        '`/drifter heard age <minutes>` makes it older, to see the follow-ups at once. Phrases in `drifter_chat/heard.json` (section 10), '
        'the topics it brings up later in `drifter_talk/heard.json` (sections 6 and 7, ids starting `heard_`).', '',
        '| You say | What it does |', '| --- | --- |']
for said_, what, _ in TELLS:
    doc.append('| ' + said_ + ' | ' + what + ' |')
doc.append('')
for said_, _, keys in TELLS:
    for key in keys:
        if key in lines:
            doc += block(key, 'You said: *' + said_ + '*.')
doc += ['### More to ask', '', '| Intent | For example | What it answers |', '| --- | --- | --- |']
for intent, example, what, _ in NEW_ASKS:
    doc.append('| ' + intent + ' | ' + example + ' | ' + what + ' |')
doc.append('')
for intent, _, _, keys in NEW_ASKS:
    for key in keys:
        if key in lines:
            doc += block(key, 'Answering **' + intent + '**.')
doc += ['### Words it fills in', '',
        'Where it\'s going, by its current goal (`going.<goal>`), and the words for places, plans, times and memories:', '']
for prefix in ['going.', 'place.', 'plan.', 'when.', 'memory.']:
    for key in sorted(k for k in lines if k.startswith(prefix)):
        doc.append('- `' + key + '`: ' + ' · '.join(esc(x) for v in lines[key].values() for x in v))
doc.append('')

doc += ['## 9. Not chat, but close', '',
        '- **Manhunt:** messages like "[Drifter] Bob respawned and is back on the hunt." are the mod\'s announcements.',
        '- **Thoughts:** what a Drifter thinks (`/drifter thoughts`) never goes to chat.',
        '- **The chronicle:** Drifter: Civilizations\'s morning news ("Day 4: ...") is the mod reporting.', '']

doc += ['## 10. What you can say to them', '',
        'A message means whichever of these it has the longest phrase of: "no problem" is a yes though it has "no" in it, "hell no" a no, '
        '"yeah no" a no. Capitals, punctuation and stretched letters don\'t matter ("NOOOO." is no, "yesss" yes); apostrophes are dropped '
        '("im" is "i\'m"). The words can be anywhere in the message.', '',
        '**Slang** is put plainly first, in both what you type and the phrases (%d words, `drifter_chat/slang.json`): "whatchu think bout me" '
        'reads as "what you think about me", "who r ur frens" as "who are your friends". **Gapped phrases** (with `*`) match words in that order '
        'with up to two words between: "think * of me" matches "what do you even think of me". A gapped match counts a little less than the '
        'same words said whole. `/drifter lines meaning <text>` shows what a message would mean.' % len(slang), '',
        '| Where | What it listens for |', '| --- | --- |',
        '| An escort, after it asks | **yes** or **ready** (it sets off), **no** (it goes alone), **unsure** (it asks again) |',
        '| A race, after it challenges you | **ready** or **yes** (the countdown), **no**, **unsure** |',
        '| A Drifter\'s name, or a part of it 4 letters or longer ("grumpy" or "baker" for xXGrumpybakerXx; not everyday words like "over" or "just"), within 32 blocks | It turns to look for you and may answer (huh) |',
        '| To a Drifter (section 8) | **wyd**, **hru**, **name**, **coords**, **base**, **seen_...**, **follow**, **team**, **come**, **stop**, '
        '**help**, **give**, **trade**, **duel**, **compliment**, **insult**, **joke**, **bot**; and **me**, **opinion**, **where_is**, **going**, '
        '**need**, **friends**, **told**, **my_base**, **forget**, **likes** (section 8b) |',
        '| Telling a Drifter (section 8b) | **tell_base**, **place_...** (with **place_cue** for "here"), **plan_...**, **promise**, **tell_like**, '
        '**tell_hate** (with **things**), **word_good**, **word_bad**, **news_...**; "call me ..." |',
        '| Anyone near (not in an encounter) | **greeting**, **night**, **bye**, **thanks**: the rituals in section 2 |', '',
        'Other encounters are answered by doing, not typing: hit a duellist to accept, throw items (Q) at a mugger or a hungry traveller, '
        'right-click a merchant or a stall keeper.', '']
for intent, phrases in answers.items():
    doc.append('- **' + intent + '** (%d): ' % len(phrases) + ' · '.join(esc(p) for p in phrases))
doc.append('')
doc += ['**Slang words:** ' + ' · '.join(esc(k) + ' = ' + esc(v) for k, v in sorted(slang.items()) if k != v), '']

doc += ['## Where they live', '',
        '- Lines and answers: `src/main/resources/data/drifter/drifter_chat/` (`reactions.json`, `encounters.json`, `answers.json`, `requests.json`, `rounds.json`, `heard.json`, `slang.json`, `talk_web.json`); whims\' '
        'lines in `drifter_whims/*.json`. This doc is generated from them.',
        '- Code: `social/Lines.java` (loading, picking in a voice, reading answers), `social/ChatLines.java` (rate limits), '
        '`social/ChatReplies.java` (the rituals), `social/talk/Topics.java` and `Conversations.java` (topics, the web, long talks), `social/talk/PlayerTalk.java` (asking a Drifter), `social/talk/Telling.java` and `Heard.java` (telling it things; what it remembers), '
        '`encounter/*.java`.', '']

open(os.path.join(ROOT, 'docs/CHAT_LINES.md'), 'w', encoding='utf-8', newline='\n').write('\n'.join(doc))
print(total, whim_total, answer_total, len(topics), talk_lines)
