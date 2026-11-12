"""Writes the starting drifter_civ drifter_chat/town_talk.json: the phrases players type to ask a townsperson about its
town (docs/PLAYER_TALK.md section D, batch 1) and a few lines for each answer. More lines are added after with merge.py;
run this only to start the file over (it replaces it).

Usage: python tools/chat_more/town_talk_seed.py <project dir>
"""
import json
import os
import sys

ANSWERS = {
    "town_name": ["what is this place", "whats this place", "what town is this", "whats this town called", "what is this town called", "where am i",
                  "what is this town", "name of this town", "whats the name of this place", "what do you call this place", "which town is this"],
    "town_leader": ["who runs this town", "who runs this place", "whos the leader", "who is the leader", "whos in charge", "who is in charge", "whos the mayor",
                    "who is the mayor", "whos the king", "who leads this town", "who is your leader", "whos your leader", "who rules here", "who is the boss here",
                    "wheres the leader", "wheres the mayor", "do you have a leader"],
    "town_rules": ["what are the rules", "any rules", "whats not allowed", "what are the laws", "any laws", "what cant i do here", "rules here",
                   "what are the rules here", "is stealing allowed", "whats illegal", "what is illegal here", "what happens if i steal", "do you have laws"],
    "town_people": ["how many people live here", "how many live here", "how big is this town", "how many of you are there", "how many people are here",
                    "how many people in this town", "whats the population"],
    "town_story": ["tell me about this town", "whats the story of this town", "who founded this town", "how old is this town", "when was this town founded",
                   "tell me about this place", "whats this towns story", "history of this town", "how long has this town been here", "who built this town"],
    "where_shop": ["wheres the shop", "where is the shop", "wheres the stall", "where is the stall", "wheres the market", "where is the market",
                   "where can i buy stuff", "where can i buy things", "where do i buy", "where can i trade", "is there a shop", "wheres the store",
                   "where can i sell stuff", "where can i sell", "where do i sell", "wheres the trader", "where to buy"],
    "where_smith": ["wheres the smith", "where is the smith", "wheres the blacksmith", "where is the blacksmith", "who fixes tools", "where can i repair",
                    "where can i fix my", "is there a smith", "who repairs stuff", "where do i get armor", "where can i get tools fixed", "wheres the smithy"],
    "where_enchanter": ["wheres the enchanter", "where is the enchanter", "who enchants stuff", "where can i enchant", "is there an enchanter",
                        "who can enchant my", "where do i enchant"],
    "where_plaza": ["wheres the plaza", "where is the plaza", "wheres the town square", "where is the square", "wheres the middle of town", "wheres the center of town"],
    "where_well": ["wheres the well", "where is the well", "where can i get water", "is there a well"],
    "where_board": ["wheres the notice board", "where is the notice board", "wheres the board", "where is the board", "wheres the bounty board",
                    "where are the notices", "where are the bounties", "where do i find jobs"],
    "where_mine": ["wheres the mine", "where is the mine", "wheres the town mine", "is there a mine", "where do you mine", "where do you guys mine"],
    "where_fire": ["wheres the fire", "wheres the campfire", "where is the town fire", "where does everyone hang out", "where do people hang out"],
    "where_sleep": ["where can i sleep", "where do i sleep", "is there an inn", "any spare bed", "is there a spare bed", "where can i stay",
                    "where do i stay", "is there a bed for me", "any empty houses", "is there an empty house", "somewhere to sleep", "a place to sleep",
                    "where can i spend the night", "is there a hotel", "anywhere to sleep"],
    "what_sell": ["what do you sell", "what does the shop sell", "whats for sale", "anything for sale", "what you got for sale", "whats in stock",
                  "what can i buy here", "what can i buy", "what does the stall sell", "what are you selling", "got anything good for sale"],
    "any_work": ["any work", "got a job for me", "any jobs", "how can i earn money", "how do i earn money", "any bounties", "any notices", "need anything done",
                 "is there work", "can i work here", "got any work", "how do i make money", "any quests", "got a quest", "anything i can do for you",
                 "how can i help the town", "how do i get emeralds"],
    "am_wanted": ["am i wanted", "am i in trouble", "why are the guards after me", "do i have a fine", "how much is my fine", "whats my fine", "am i banned",
                  "am i exiled", "am i welcome here", "does the town like me", "whats my standing", "am i a criminal", "are the guards after me",
                  "can i come back", "how long am i banned"],
    "whats_on": ["whats going on", "whats going on today", "why is everyone at the plaza", "whats the bell for", "why is the bell ringing", "whats happening",
                 "whats the occasion", "why is everyone running", "whats the alarm", "why is there an alarm", "is today special", "is there a festival",
                 "is it market day", "whats everyone doing", "why are people gathering", "what is happening"],
    "tour": ["show me around", "can you show me around", "show me round", "give me a tour", "show me the town", "can i get a tour", "tour of the town",
             "can you give me a tour", "show me this place", "wanna show me around", "want to show me around", "could you show me around", "tour pls",
             "show me where everything is"],
    "take_me": ["take me to", "lead me to", "bring me to", "can you take me to", "walk me to", "show me the way to", "show me to", "guide me to",
                "show me where the", "take me to the"],
    "your_job": ["are you a guard", "whats your job", "what is your job", "what do you do here", "do you work", "what do you do for work", "are you the smith",
                 "are you a farmer", "are you the leader", "are you the mayor", "are you a miner", "are you the shopkeeper", "do you have a job",
                 "what do you do around here", "whats your role", "what are you here", "are you a builder", "are you a guard here"],
}

L = {}


def lines(key, *any_lines, **voices):
    L[key] = {"any": list(any_lines), **{k: list(v) for k, v in voices.items()}}


lines("town.talk.name", "this is {town}", "{town}. welcome", "youre in {town}", "its called {town}", "{town}, best town around",
      hermit=["{town}."], newbie=["its {town}!! i live here"], helper=["this is {town}! welcome"], pvp_sweat=["{town}. dont start anything"],
      nomad=["{town}. nice place to stop a while"])
lines("town.talk.leader", "{leader} runs things", "thatd be {leader}", "{leader}'s the leader", "{leader}. usually round the plaza", "{leader}, for now",
      hermit=["{leader}."], newbie=["{leader}!! theyre really nice"], pvp_sweat=["{leader}. id do it better"], bandit=["{leader}. for now"],
      helper=["{leader} is our leader! want me to point them out?"])
lines("town.talk.leader.me", "that would be me", "me, actually", "youre talking to them", "i run this place", "me. be nice",
      hermit=["me."], newbie=["me!! i know right"], pvp_sweat=["me. so behave"])
lines("town.talk.leader.none", "nobody right now", "we dont have one atm", "no leader at the moment", "its... complicated right now")
lines("town.talk.rules", "simple: {laws}", "the rules: {laws}", "{laws}. thats about it", "basically {laws}", "{laws}. break them and the guards come",
      hermit=["{laws}."], pvp_sweat=["{laws}. try it and see"], helper=["just {laws}! easy"], bandit=["{laws}. officially"])
lines("town.talk.rules.none", "not really any rules", "no real laws yet", "just dont be a jerk", "nothing written down lol")
lines("town.talk.people", "{count} of us", "{count} or so", "theres {count} of us now", "{count}, give or take", "hard to say, {count} maybe",
      hermit=["{count}. too many"], newbie=["{count}!! and me"])
lines("town.talk.story", "{town}'s been here {days} days now", "founded {days} days ago", "weve been at it {days} days", "{days} days old and still standing",
      "started as a few huts {days} days back", builder=["{days} days of building. you should have seen it at the start"],
      hermit=["{days} days. it was quieter before"], homebody=["{days} days. feels like home now"])
lines("town.talk.story.new", "its brand new, we just founded it", "we only just started this place", "barely a few days old", "new town, still building")
lines("town.talk.where", "{what}'s {way} of here", "{way}, cant miss it", "head {way} for {what}", "{what}? {way}", "go {way}, youll see {what}",
      hermit=["{way}."], helper=["{what} is {way} of here! want me to take you?"], speedrunner=["{way}. go"], newbie=["um i think {what} is {way}"])
lines("town.talk.where.here", "{what}'s right here", "youre standing next to it lol", "right there", "just here, look", "this is it",
      hermit=["here."], newbie=["its right here!!"], pvp_sweat=["right in front of you bro"])
lines("town.talk.where.none", "we dont have one yet", "no such thing here yet", "not built yet", "this town doesnt have that", "we dont have {what} yet")
lines("town.talk.sleep.empty", "theres an empty house {way} of here", "try the empty place {way}", "nobody's using the house {way}, should be fine",
      "theres a free house {way}", helper=["theres an empty house {way}! you can use that"], hermit=["empty house. {way}."])
lines("town.talk.sleep.none", "every house is taken, sorry", "no spare beds that i know of", "nowhere free rn", "bring your own bed lol",
      "ask a friend for their spare bed", helper=["no empty houses right now, sorry! maybe a friend has a spare bed"], hermit=["no."])
lines("town.talk.sells", "{who} sells {goods}", "the stall has {goods} today", "{goods}, ask {who}", "mostly {goods} at the stall", "{who}'s got {goods}")
lines("town.talk.sells.mine", "i got {goods}", "today its {goods}", "{goods}. right click me to trade", "selling {goods}, good prices", "{goods}, take a look",
      hoarder=["{goods}. no discounts"], helper=["{goods}! have a look"], pvp_sweat=["{goods}. buy or move along"])
lines("town.talk.sells.nothing", "stall's empty today", "nothing in stock rn", "sold out, come back tomorrow", "not much today")
lines("town.talk.sells.no_stall", "we dont have a stall yet", "no shop here yet", "the keeper's not around", "no market yet, still building")
lines("town.talk.work.notice", "{who} wants {what}, its on the board", "theres a notice up for {what}", "someone needs {what}, pays well",
      "check the board, {who} is after {what}", "bring {who} {what} and you get paid", helper=["yes! {who} needs {what}, itd really help"],
      hermit=["{what}. for {who}. board."])
lines("town.talk.work.none", "nothing on the board rn", "no work today", "not that i know of", "check the board later", "all quiet, no notices")
lines("town.talk.wanted.yes", "yes. for {crime}. id leave", "the guards want you for {crime}", "you? yeah, {crime}. {days} more days", "wanted for {crime}. run",
      hermit=["yes. go."], pvp_sweat=["wanted for {crime}. guards will love this"], helper=["yes... for {crime}. you should go before they see you"],
      newbie=["yes!! for {crime}!! why did you do that"])
lines("town.talk.wanted.exiled", "youre banned for {crime}, {days} more days", "exiled. {days} days left", "you shouldnt be here. {crime}", "{days} more days of exile for you")
lines("town.talk.wanted.fine", "you owe {fine} for {crime}", "theres a fine, {fine}. pay a guard", "{fine} for {crime}. throw it to a guard", "pay your {fine} and its forgotten")
lines("town.talk.wanted.no", "no, youre fine", "nope, clean record", "not that ive heard", "youre good", "no trouble that i know of",
      hermit=["no."], pvp_sweat=["not yet lol"], bandit=["not yet"])
lines("town.talk.wanted.liked", "wanted? people like you here", "no lol, youre welcome here", "youre one of the good ones", "nah, the town likes you",
      helper=["no way! everyone likes you here"])
lines("town.talk.unwelcome", "not talking to you", "ask someone else", "you should go", "i dont have anything to say to you", "leave",
      hermit=["go."], pvp_sweat=["get out"], helper=["i cant help you. sorry"])
lines("town.talk.on.alarm", "the alarm! somethings wrong", "bells ringing, trouble", "alarm. stay inside or grab a sword", "trouble in town, careful",
      newbie=["THE ALARM!!", "somethings attacking!!"], pvp_sweat=["alarm. finally some action"])
lines("town.talk.on.festival", "its {what}!", "{what} today, everyone's at the plaza", "{what}. come celebrate", "festival day, {what}",
      hermit=["{what}. too loud"], newbie=["its {what}!! come on"])
lines("town.talk.on.market", "market day", "its market day, best prices", "market's on, go have a look", "market day, the stall's busy")
lines("town.talk.on.contest", "fishing contest today", "its the fishing contest, most fish by evening wins", "everyone's fishing, contest day")
lines("town.talk.on.nothing", "nothing special", "just a normal day", "the usual", "nothing really", "quiet day", hermit=["nothing."], casual=["nothing lol"])
lines("town.talk.job.keeper", "i keep the stall", "im the shopkeeper", "i run the market stall", "stall keeper. buy something", hoarder=["i keep the stall. and count every coin"])
lines("town.talk.job.leader", "i run this town", "im the leader", "leader. someone has to", "i lead {town}")
lines("town.talk.job.guard", "im a guard", "guard. i keep watch over the town", "i guard {town}", "yep, town guard", "guard duty",
      pvp_sweat=["guard. best job here", "i get paid to fight. yes"], hermit=["guard."])
lines("town.talk.job.smith", "im the smith", "i make tools and armor", "smith. bring iron", "blacksmith, repairs too", miner=["smith. i mine my own iron too"])
lines("town.talk.job.enchanter", "im the enchanter", "i enchant things, for a price", "enchanter. bring levels worth of emeralds", "i do the enchanting here")
lines("town.talk.job.farmer", "i farm", "farmer. wheat mostly", "i grow the food", "i work the fields", homebody=["i farm! best job"])
lines("town.talk.job.fisher", "i fish", "fisher. down by the water", "i catch the fish", "fishing all day")
lines("town.talk.job.fletcher", "i make bows and arrows", "fletcher", "im the fletcher, arrows mostly", "bows and arrows, thats me")
lines("town.talk.job.hunter", "i hunt", "hunter. meat and leather", "i bring in the meat", "out hunting most days")
lines("town.talk.job.miner", "i mine", "miner. iron and coal mostly", "im down the mine most days", "town miner", miner=["miner. best job there is"])
lines("town.talk.job.builder", "i build", "builder. half these houses are mine", "i put up the houses", "town builder", builder=["builder. obviously"])
lines("town.talk.job.woodcutter", "i chop wood", "woodcutter", "i bring in the logs", "trees, all day")
lines("town.talk.job.none", "i just live here", "bit of everything", "no real job, i help out", "whatever needs doing", "i do my own thing",
      hermit=["i keep to myself."], newbie=["i dont have a job yet!!"], pvp_sweat=["i fight when theres fighting"])
lines("town.talk.tour.no", "id rather not", "not for you", "ask someone else", "no", hermit=["no."])
lines("town.talk.take.where", "take you where?", "where to?", "to what", "where do you wanna go")
lines("town.talk.take.none", "we dont have one of those", "nothing like that here yet", "not built yet")
lines("town.talk.take.yes", "sure, follow me", "ok this way", "come on", "follow me, its not far", "ok, keep up",
      hermit=["fine. follow"], helper=["of course! this way"], speedrunner=["ok fast. go"], newbie=["ok!! follow me"])

COMMENT = ("Asking a townsperson about its town (docs/PLAYER_TALK.md section D, batch 1; TownTalk.java). 'answers': the phrases players type "
           "(the longest phrase found wins, against Drifter's own too). 'lines': its answers by voice. {player} is what it calls them, {town} "
           "its town, {leader} its leader; some kinds fill more: {laws}, {count}, {days}, {what} and {way} (a place and which way), {goods} "
           "and {who} (the stall), {crime} {fine} (the law). Started by tools/chat_more/town_talk_seed.py; more lines added with merge.py.")

if __name__ == '__main__':
    root = sys.argv[1]
    path = os.path.join(root, 'src/civ/resources/data/drifter_civ/drifter_chat/town_talk.json')
    out = json.dumps({"_comment": COMMENT, "answers": ANSWERS, "lines": L}, indent=1, ensure_ascii=False)
    open(path, 'w', encoding='utf-8', newline='\r\n').write(out + '\n')
    print(len(ANSWERS), 'meanings,', sum(len(v) for v in ANSWERS.values()), 'phrases;', len(L), 'kinds,',
          sum(len(x) for v in L.values() for x in v.values()), 'lines')
