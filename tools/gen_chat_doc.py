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
    'huh': 'You say its name in chat within 32 blocks: it turns to look for you, nods, and may answer.',
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
    'escort.ask': 'It asks you to walk it to a village.', 'escort.yes': 'You said yes (section 7).', 'escort.no': 'You said no.',
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
for path in sorted(glob.glob(CHAT + '/*.json')):
    data = json.load(open(path, encoding='utf-8'))
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

doc += ['## 6. Not chat, but close', '',
        '- **Manhunt:** messages like "[Drifter] Bob respawned and is back on the hunt." are the mod\'s announcements.',
        '- **Thoughts:** what a Drifter thinks (`/drifter thoughts`) never goes to chat.',
        '- **The chronicle:** Drifter: Civilizations\'s morning news ("Day 4: ...") is the mod reporting.', '']

doc += ['## 7. What you can say to them', '',
        'A message means whichever of these it has the longest phrase of: "no problem" is a yes though it has "no" in it, "hell no" a no, '
        '"yeah no" a no. Capitals, punctuation and stretched letters don\'t matter ("NOOOO." is no, "yesss" yes); apostrophes are dropped '
        '("im" is "i\'m"). The words can be anywhere in the message.', '',
        '| Where | What it listens for |', '| --- | --- |',
        '| An escort, after it asks | **yes** or **ready** (it sets off), **no** (it goes alone), **unsure** (it asks again) |',
        '| A race, after it challenges you | **ready** or **yes** (the countdown), **no**, **unsure** |',
        '| A Drifter\'s name (within 32 blocks) | It turns to look for you and may answer (huh) |',
        '| Anyone near (not in an encounter) | **greeting**, **night**, **bye**, **thanks**: the rituals in section 2 |', '',
        'Other encounters are answered by doing, not typing: hit a duellist to accept, throw items (Q) at a mugger or a hungry traveller, '
        'right-click a merchant or a stall keeper.', '']
for intent, phrases in answers.items():
    doc.append('- **' + intent + '** (%d): ' % len(phrases) + ' · '.join(esc(p) for p in phrases))
doc.append('')

doc += ['## Where they live', '',
        '- Lines and answers: `src/main/resources/data/drifter/drifter_chat/` (`reactions.json`, `encounters.json`, `answers.json`); whims\' '
        'lines in `drifter_whims/*.json`. This doc is generated from them.',
        '- Code: `social/Lines.java` (loading, picking in a voice, reading answers), `social/ChatLines.java` (rate limits), '
        '`social/ChatReplies.java` (the rituals), `encounter/*.java`.', '']

open(os.path.join(ROOT, 'docs/CHAT_LINES.md'), 'w', encoding='utf-8', newline='\n').write('\n'.join(doc))
print(total, whim_total, answer_total)
