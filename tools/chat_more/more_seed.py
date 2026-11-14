"""Writes the starting convo_more.json (Drifter) and town_more.json (Drifter: Civilizations): the phrases players type for
batch 2's meanings (docs/PLAYER_TALK.md rows marked 2) and a few lines for each reply. More lines are added after with
merge.py; run this only to start the files over (it replaces them).

Usage: python tools/chat_more/more_seed.py <project dir>
"""
import json
import os
import sys

A = {
    # ----- glue -----
    "gm": ["gm", "good morning", "morning", "mornin", "gm all", "morning all", "gm everyone", "rise and shine everyone"],
    "back": ["im back", "back", "bk", "ok im back", "back now", "and im back", "im here now", "returned", "back again"],
    "gg_said": ["gg", "ggs", "good game", "gg wp", "wp", "well played", "gj", "good job everyone", "nice one everyone"],
    "later": ["later", "not yet", "in a bit", "tomorrow", "after this", "maybe later", "not right now", "some other time", "another time", "soon"],
    "ew": ["ew", "eww", "gross", "cringe", "weird", "sus", "yikes", "bruh", "thats weird", "thats gross", "thats cringe", "wtf", "what the heck"],
    "anyway": ["anyway", "anyways", "moving on", "new topic", "change of subject", "lets talk about something else", "enough about that"],
    "btw": ["btw", "by the way", "oh and", "one more thing", "quick question", "also btw", "oh also", "wait one more thing"],
    "already_said": ["you already said that", "you said that already", "you asked me that already", "i told you already", "i already told you",
                     "you just said that", "you keep saying that", "stop repeating yourself", "broken record"],
    "ignoring": ["why are you ignoring me", "why wont you answer", "why dont you answer", "rude", "thats rude", "ignoring me", "hello rude",
                 "why are you so quiet", "why arent you talking"],
    "shut_up": ["shut up", "be quiet", "stop talking", "shh", "shhh", "too loud", "stop spamming", "quiet", "zip it", "pipe down", "enough talking",
                "talk less"],
    "leave_me": ["leave me alone", "go away from me", "stop following me", "back off", "dont talk to me", "stop talking to me", "leave me be",
                 "get away from me", "stop bothering me", "buzz off", "dont follow me"],
    "repeat": ["say that again", "what did you say", "repeat that", "i missed that", "come again", "say again", "repeat", "once more", "huh say that again",
               "what was that"],
    "sure": ["are you sure", "you sure", "positive", "how do you know", "how would you know", "really sure", "for sure", "you certain"],
    "when_q": ["when", "since when", "how long ago", "when was that", "when did that happen"],
    "how_q": ["how", "how so", "hows that work", "how did you do that", "how come", "how does that work"],
    "who_meant": ["who", "whos that", "which one", "who do you mean", "who are you talking about", "who is that"],
    "where_that": ["where", "where was that", "whereabouts", "where at", "where is that"],
    "tell_more": ["tell me more", "go on", "and then", "then what", "what happened next", "continue", "keep going"],
    "ask_me": ["ask me something", "ask me a question", "what do you wanna know", "ask me anything", "got any questions", "any questions for me"],
    "im_bored": ["im bored", "so bored", "bored", "im so bored", "nothing to do", "theres nothing to do"],
    "im_sad": ["im sad", "im feeling sad", "im down", "feeling down", "im upset", "i feel bad", "im not feeling great"],
    "im_happy": ["im happy", "im so happy", "feeling good", "im in a good mood", "great day", "im having a great day", "best day ever"],
    "im_angry": ["im angry", "im mad", "im so mad", "im furious", "this makes me angry", "im annoyed", "im pissed"],
    "im_lonely": ["im lonely", "i feel lonely", "nobody talks to me", "im all alone", "i have no friends", "can you stay"],
    "logging_off": ["im logging off", "logging off", "gtg for today", "see you tomorrow", "see ya tomorrow", "im off for the night", "done for today",
                    "time to log off", "im heading out", "signing off"],
    "im_busy": ["im busy", "not now", "cant talk", "cant talk rn", "busy rn", "talk later", "im in the middle of something"],
    "saved_me": ["you saved me", "thanks for saving me", "thanks for the help", "couldnt have done it without you", "you saved my life", "lifesaver"],
    "good_job": ["good job", "well done", "nice shot", "nice fight", "youre good", "nicely done", "great job", "good work", "nice work", "you did great"],
    "condolence": ["sorry for your loss", "rip your friend", "sorry they died", "my condolences", "im sorry they died", "sorry to hear about"],
    "lost_stuff": ["i lost all my stuff", "i died and lost everything", "lost everything", "lost all my items", "all my stuff is gone", "i lost my stuff",
                   "my stuff despawned"],
    "distress": ["i want to die", "i hate my life", "i hate myself", "i want to disappear", "nobody cares about me", "everything is pointless",
                 "i cant do this anymore"],
    "did_it": ["i did it", "finally", "lets gooo", "i made it", "yes finally", "i finally did it", "look i did it", "ayy i did it"],
    "missed_you": ["i missed you", "missed you", "glad youre here", "good to see you", "long time no see", "its been a while", "did you miss me",
                   "havent seen you in ages", "nice to see you again"],
    "love": ["love you", "ily", "i love you", "hug", "hugs", "<3", "luv u", "love ya", "big hug"],
    "fun_fact": ["tell me a fun fact", "fun fact", "did you know", "tell me something interesting", "teach me something", "random fact"],
    "correction": ["it is raining actually", "its night actually", "no thats wrong", "thats not right", "actually its", "youre wrong", "thats not true",
                   "no it isnt", "no its not"],
    "rematch": ["rematch", "one more round", "run it back", "lets go again", "best two out of three", "another round"],
    "fight_me": ["fight me", "come at me", "you scared", "coward", "chicken", "1v1 me bro", "square up", "lets fight", "wanna go"],
    "are_we_good": ["are we good", "are we cool", "we good", "we cool", "are we ok", "are we okay", "still mad at me", "are you still mad"],
    "we_friends": ["were friends remember", "we're friends remember", "but were friends", "i thought we were friends", "arent we friends"],
    "owe_me": ["you owe me", "pay me back", "wheres my payment", "you promised", "you said you would", "remember your promise"],
    "ready": ["you ready", "ready", "are you coming", "lets go", "go go", "you coming", "come on lets go", "lets move", "hurry lets go"],
    "done_outing": ["im done", "lets stop", "lets head back", "lets go home", "thats enough for today", "head back"],
    "deal": ["deal", "ok deal", "its a deal", "you got a deal", "sounds good", "agreed then"],
    "no_deal": ["no deal", "too much", "too expensive", "forget it then", "thats a rip off", "nah too pricey"],
    "hurry": ["hurry up", "faster", "keep up", "move faster", "speed up", "hurry"],
    "slow_down": ["slow down", "wait for me", "not so fast", "youre too fast", "slow"],

    # ----- about it -----
    "how_long": ["how long have you been here", "when did you join", "how long have you lived here", "how long you been here", "are you new here",
                 "how old are you in game", "been here long", "how many days have you been here"],
    "from_where": ["where are you from", "where did you come from", "you from round here", "are you from here", "where do you come from", "whats your home town"],
    "what_kind": ["what kind of player are you", "whats your thing", "what are you", "what do you like doing", "what type of player are you",
                  "whats your playstyle"],
    "why_doing": ["why are you doing that", "whats that for", "why are you doing", "what for", "why that", "whats the point of that"],
    "standing": ["why are you just standing there", "are you afk", "are you stuck", "why are you standing still", "are you lost", "what are you staring at"],
    "next_plan": ["whats next", "what are you doing after", "got plans", "whats the plan today", "what are you doing tomorrow", "any plans",
                  "what are you up to later", "whats the plan"],
    "hungry_q": ["are you hungry", "did you eat", "you hungry", "have you eaten", "you need food", "hungry"],
    "inventory": ["whats in your inventory", "what you got", "show me your stuff", "what do you have on you", "whats in your bag", "what are you carrying"],
    "rich": ["how many diamonds do you have", "are you rich", "how much money do you have", "you rich", "how many dias you got", "got any diamonds on you"],
    "item_story": ["where did you get that", "how did you make that", "is that enchanted", "where did you get your sword", "wheres that from",
                   "what enchants does that have"],
    "saving_for": ["what are you saving up for", "what are you working toward", "whats your next upgrade", "what do you want to get next"],
    "pets": ["do you have a farm", "any pets", "got a horse", "do you have pets", "whats your dog called", "got a dog", "got a cat", "do you have animals"],
    "deaths_q": ["have you died", "how many times have you died", "how did you die last", "worst death", "ever died", "how many deaths"],
    "best_find": ["whats the best thing youve found", "found anything good", "best find", "whats your best find", "found anything cool lately"],
    "been_nether": ["have you been to the nether", "been to the nether", "have you been to the end", "seen the dragon", "fought the dragon",
                    "killed the dragon", "been to the end", "ever been to the nether"],
    "story": ["tell me a story", "got any stories", "story time", "tell me about yourself", "whats the scariest thing thats happened to you",
              "funniest thing thats happened to you", "dumbest thing youve done"],
    "dream": ["whats your goal", "what do you want", "whats your dream", "what do you want most", "whats your big goal", "if you could have anything"],
    "building_what": ["what are you building", "what will it be", "whats that going to be", "what is that build", "what are you making"],
    "favorite": ["whats your favorite", "favorite", "fav", "whats your fav", "what is your favorite", "favourite"],
    "hate_what": ["what do you hate", "least favorite mob", "what annoys you", "what do you hate most", "what bugs you", "least favorite"],
    "think_of": [f"{q} {t}" for t in ["villagers", "pvp", "the nether", "nether", "the end", "creepers", "endermen", "phantoms", "mining", "building",
                                      "farming", "fishing", "diamonds", "redstone", "zombies", "skeletons", "piglins", "wolves", "cats", "night", "rain",
                                      "caves"]
                 for q in ["what do you think of", "what do you think about", "thoughts on", "how do you feel about"]],
    "rate": ["rate my", "out of 10", "out of ten", "how would you rate", "rate it", "rate this", "give it a score"],
    "good_at": ["what are you good at", "are you a builder", "do you mine", "whats your best skill", "what are you best at", "are you good at building"],
    "brave_q": ["are you brave", "are you scared of creepers", "are you scared of the dark", "are you scared of dying", "are you afraid", "you scared of anything"],
    "lonely_q": ["are you lonely", "do you get bored", "do you get lonely", "dont you get lonely", "are you ever bored"],
    "like_here": ["do you like it here", "do you like this place", "hows life here", "is it nice here", "you like it here"],
    "miss_x": ["do you miss", "are you sad about", "do you think about", "you miss"],
    "best_friend": ["whos your best friend", "who do you trust most", "best friend", "who do you like most", "whos your closest friend"],
    "enemies": ["who do you hate", "got any enemies", "whos your rival", "any enemies", "who dont you like", "who do you not like"],
    "strongest": ["whos the strongest", "who is the strongest", "strongest person here", "whos the best fighter", "who has the best gear"],
    "group_q": ["are you in a group", "whats your crew", "whos your leader", "who do you belong to", "are you in a team", "whats your group called"],
    "make_up": ["how do i make it up to you", "what can i do", "how do we become friends", "how can i make it right", "what do you want from me",
                "how do i make you like me"],

    # ----- what it knows -----
    "where_find": ["where can i find", "where do i find", "where can i get", "wheres some", "where is there", "know where to find", "where are there",
                   "where are the", "where do you get", "anywhere to find", "where should i look for"],
    "where_died": ["where did i die", "have you seen my stuff", "where are my items", "where did my stuff go", "do you know where i died", "where is my stuff"],
    "which_way": ["which way is north", "which way is south", "which way is east", "which way is west", "where is north", "which way is spawn",
                  "where is spawn", "how do i get to spawn", "point me north", "which way to spawn"],
    "biome_q": ["what biome is this", "what biome are we in", "whats this biome", "which biome is this"],
    "over_there": ["whats over there", "whats up there", "whats down there", "whats in that cave", "what is that", "whats that over there"],
    "bandits_q": ["are there bandits", "any raiders", "is the road safe", "any bandits", "bandits around", "are there raiders around"],
    "what_killed": ["what killed", "how did * die", "who killed", "what happened to", "how did they die"],
    "weather_q": ["is it going to rain", "hows the weather", "nice day huh", "ugh rain", "nice weather", "is it raining", "what a storm", "lovely day"],
    "who_here": ["who was here", "did anyone come by", "whos around", "who else is here", "anyone around", "seen anyone"],
    "who_stole": ["who took my stuff", "who stole from me", "who broke my wall", "who took my things", "who robbed me", "who griefed me"],
    "whose": ["who built that", "whose farm is this", "whose chest is this", "whose horse is this", "whose house is that", "who owns this", "who owns that"],
    "x_doing": ["what is * doing", "whats * doing", "what is * up to", "what are they doing", "whats he doing", "whats she doing", "what are they up to"],
    "where_everyone": ["where is everyone", "why is it so quiet", "why is the town empty", "wheres everybody", "so quiet here"],
    "gossip": ["any gossip", "spill", "whats the drama", "spill the tea", "any drama", "got any gossip", "whats the tea"],
    "recipe": ["how do i make", "how do you make", "whats the recipe for", "recipe for", "what do i need for", "how do i craft", "how to craft",
               "what do i need to make"],
    "ylevel": ["what level for", "best y level", "where do i mine", "what y level", "what layer for", "how deep for", "where do i dig for"],
    "fight_tips": ["how do i kill", "how do you kill", "tips for fighting", "how do i beat", "how do i fight", "tips against", "how to kill"],

    # ----- doing things -----
    "go_away": ["go away", "leave now", "please leave", "you can leave", "get out", "get out of my house", "shoo", "scram", "beat it", "get lost"],
    "move_aside": ["move", "youre in my way", "scoot", "let me through", "back up", "excuse me move", "out of the way", "step aside"],
    "meet_me": ["meet me at", "meet me in", "meet me by", "wait for me at", "meet me there", "see you at the"],
    "come_up": ["come in", "come inside", "come out", "come up here", "come down here", "come down", "get over here"],
    "show_where": ["show me where", "take me to the diamonds", "take me there", "lead me to", "can you show me where", "show me the way to the"],
    "walk_me_home": ["walk me home", "come with me to my base", "escort me", "take me home", "walk me back", "can you walk me home"],
    "explore": ["lets explore", "wanna go on an adventure", "lets go somewhere", "lets go exploring", "adventure time", "wanna explore"],
    "calm_together": ["lets go fishing", "lets go farming", "lets chop trees", "wanna go fishing", "lets fish", "lets get some wood", "lets farm"],
    "race": ["race you", "wanna race", "first to the", "lets race", "race me"],
    "eat_together": ["lets eat", "wanna share a meal", "lunch", "dinner time", "lets have food", "eat with me", "sit by the fire with me"],
    "bring_me": ["can you get me", "bring me", "fetch me", "can you bring me", "get me some", "i need some"],
    "craft_me": ["craft me", "make me a", "cook this", "smelt this", "can you craft", "can you make me"],
    "rescue": ["come get me", "im stuck", "help im trapped", "rescue me", "im trapped", "save me", "get me out of here"],
    "work_for_me": ["work for me", "ill pay you", "how much to do it", "can i hire you", "ill give you emeralds", "be my worker"],
    "attack_that": ["kill that", "attack it", "get the skeleton", "shoot it", "kill it", "attack that", "get it", "get that zombie"],
    "run_away": ["run", "retreat", "get back", "get out of there", "fall back", "run away", "flee"],
    "stop_attacking": ["dont attack", "stop fighting", "leave it", "its mine", "thats my pet", "stop attacking", "dont hurt it", "leave it alone"],
    "yield_q": ["do you yield", "give up", "surrender", "do you give up", "yield"],
    "want_this": ["do you want", "need a", "want some", "you want this", "do you need", "want a"],
    "name_price": ["what do you want for it", "name your price", "what would you take for", "how much for yours", "whats it worth to you"],
    "give_back": ["give it back", "thats mine", "you took my", "return it", "give me my", "thats my stuff"],
    "rob": ["give me your stuff", "drop your diamonds", "pay up", "hand it over", "give me everything", "drop it", "drop your sword", "your money or your life",
            "empty your pockets"],
    "use_station": ["can i use your crafting table", "can i use your furnace", "can i use your anvil", "can i use your chest", "mind if i use your",
                    "can i borrow your crafting table", "can i use your bed"],
    "take_from_chest": ["can i take some", "can i have some from your chest", "can i grab some", "mind if i take", "can i take this"],
    "pet_animals": ["can i pet your dog", "can i ride your horse", "can i feed them", "can i pet your cat", "can i pet it", "can i feed your animals"],
    "dont_touch": ["dont touch my stuff", "stay out of my house", "thats my chest", "dont touch that", "keep your hands off", "stay away from my base"],
    "what_should_i_do": ["what should i do", "im bored what now", "any ideas", "what now", "give me something to do", "what do you suggest"],
    "look_my_house": ["look at my house", "come see my base", "what do you think of my build", "come see my house", "check out my base", "wanna see my house"],
    "relay": ["tell * i said hi", "tell * hi", "say hi to", "tell * im looking for", "tell * thanks", "tell * sorry", "let * know", "pass it on to", "give * a message", "tell them i said hi"],
    "new_gear": ["i got a new sword", "look what i found", "i got new armor", "check out my new", "look at my new", "i just got"],
    "safe_now": ["its safe now", "i killed them", "i cleared the cave", "its clear now", "i cleared it", "its safe", "all clear"],
    "home_alarm": ["your house is on fire", "your door is open", "your animals got out", "someones in your house", "theres someone in your house",
                   "your house is burning", "theres a fire at your house"],
    "x_died": ["is dead", "got killed", "was killed", "passed away", "died yesterday", "died today", "is gone"],
    "what_happened": ["what happened here", "can you tell me what happened", "tell me what happened", "what happened", "tell me about what happened here",
                      "what has been going on", "what happened around here", "anything happen here"],
}

TOWN = {
    "price": ["how much is", "how much for", "how much does", "whats the price of", "price of", "how much are", "what does it cost", "how much do you want for"],
    "buys": ["do you buy", "what do you buy", "wanna buy this", "how much will you give me", "will you buy", "what will you pay for", "do you take"],
    "money_take": ["what do you take as payment", "do you take diamonds", "do you take emeralds", "what money do you use", "what currency", "do you take coins"],
    "live_here": ["can i live here", "can i join the town", "can i move in", "can i stay here for good", "can i be part of the town", "can i join"],
    "build_here": ["can i build here", "where can i build", "is this spot taken", "can i put a house here", "where can i put my house"],
    "nation": ["what nation is this", "who do you belong to", "wheres the capital", "which nation", "what kingdom is this", "whats the capital"],
    "like_town": ["is this a good town", "do you like it here", "hows life here", "is it nice living here", "do you like this town"],
    "allies": ["who are your allies", "who are your enemies", "do you have allies", "who are you friends with", "any allies", "who do you trade with"],
    "war": ["are you at war", "who are you fighting", "whos winning", "are you fighting anyone", "is there a war", "any wars"],
    "wanted_list": ["whos wanted", "any bounties", "whos the criminal", "any criminals", "who is wanted here", "wanted list"],
    "join_festival": ["can i join the party", "can i come to the festival", "can i join the contest", "can i join", "am i invited", "can i come too"],
    "leader_opinion": ["what do you think of the leader", "is the leader good", "taxes too high", "do you like the leader", "is the mayor good",
                       "how is the leader"],
    "who_lives": ["who lives here", "whose house is this", "who lives in this house", "is this house taken", "who owns this house"],
    "guards_call": ["guards", "help thief", "call the guards", "get the guards", "guard help", "somebody call the guards"],
    "didnt_do_it": ["i didnt do it", "it wasnt me", "i was framed", "im innocent", "wasnt me", "i didnt steal", "i didnt kill anyone"],
    "sorry_guard": ["ill pay", "i wont do it again", "can i pay the fine", "how do i pay", "who do i pay", "how much is my fine", "can i pay it"],
    "haggle": ["lower", "discount", "can you do better", "cheaper", "any discount", "give me a deal", "come on lower", "ill give you less"],
    "buy_it": ["ill take it", "i want to buy", "sell me", "ill buy", "can i buy", "i want one"],
    "job_done": ["heres the stuff", "i got your iron", "job done", "heres what you wanted", "i finished the job", "got it for you"],
    "market_when": ["when is market day", "when is the festival", "when is the contest", "when is the next market", "is there a market soon"],
    "report_crime": ["stole from me", "hit me", "attacked me", "i want to report a crime", "report a crime", "someone stole", "theres a thief",
                     "killed someone"],
    "raid_coming": ["a raid is coming", "bandits are coming", "theyre going to attack the town", "raiders are coming", "theres a raid coming",
                    "pillagers are coming", "the town is going to be attacked"],
}

L = {}
T = {}


def lines(store, key, *any_lines, **voices):
    store[key] = {"any": list(any_lines), **{k: list(v) for k, v in voices.items()}}


def m(key, *a, **v):
    lines(L, key, *a, **v)


def t(key, *a, **v):
    lines(T, key, *a, **v)


# ----- glue -----
m("more.gm", "gm", "morning", "gm o/", "mornin", "morning, sleep ok?")
m("more.gm.night", "gm? its night lol", "its the middle of the night", "morning? look at the sky", "gm i guess, its dark tho")
m("more.back", "wb", "welcome back", "oh hey, wb", "wb, missed anything?")
m("more.gg_said", "gg", "ggs", "gg wp", "gg o7")
m("more.rematch", "nah im good", "maybe later", "not rn", "another time")
m("more.later", "kk", "ok lmk", "sure, later", "whenever", "np, later then")
m("more.ew", "lol", "what", "ok fair", "bruh", "harsh")
m("more.anyway", "anyway", "so yeah", "ok, whats up", "sure, what else")
m("more.btw", "yeah?", "go on", "whats up", "ya?")
m("more.already_said", "oh right mb", "oops, mb", "did i? lol", "yeah yeah")
m("more.ignoring", "sorry, didnt see", "im here, whats up", "not ignoring you lol", "my bad, what")
m("more.ignoring.busy", "sorry, kinda busy", "busy, one sec", "working, gimme a sec")
m("more.ignoring.cold", "maybe i am", "and?", "not in the mood")
m("more.shut_up", "ok ok", "fine", "sorry", "geez ok")
m("more.shut_up.back", "make me", "you shut up", "lol no", "whatever")
m("more.leave_me", "ok, sorry", "fine, im going", "alright, alright", "ok ill leave you be")
m("more.repeat", "i said {last}", "{last}", "like i said, {last}")
m("more.sure", "pretty sure", "yeah", "i think so", "not really honestly")
m("more.sure.heard", "thats what i heard", "someone told me, so", "heard it, didnt see it")
m("more.sure.mine", "yeah im sure", "its my stuff, i know", "100%")
m("more.when_q", "a while back", "not long ago", "dont remember exactly", "recently i think")
m("more.how_q", "no idea honestly", "just did lol", "practice i guess", "dunno, it worked")
m("more.who_meant", "nobody, nevermind", "just someone", "doesnt matter")
m("more.where_that", "round here", "not far", "cant remember exactly")
m("more.tell_more", "thats about it", "not much more to say", "thats all i know")
m("more.ask_me", "ok, been here long?", "found any diamonds yet?", "whats your base like?", "ok, whats your favorite mob?")
m("more.feel.bored", "same honestly. wanna do something?", "go mining?", "bored? lets go explore", "relatable")
m("more.feel.sad", "aw, whats wrong?", "sorry. want to talk?", "that sucks, im here")
m("more.feel.happy", "nice!", "love that", "good vibes", "glad to hear it")
m("more.feel.angry", "what happened?", "who made you mad", "breathe lol", "uh oh, whats up")
m("more.feel.lonely", "im here", "ill hang out a bit", "you got me", "stick around then")
m("more.logoff", "cya", "bye o/", "later", "take care")
m("more.logoff.friend", "cya tmrw", "bye! come back soon", "gn, see you next time", "later friend")
m("more.im_busy", "ok np", "kk, later", "sure, catch you later")
m("more.saved_me", "anytime", "np, glad youre ok", "thats what friends are for", "you owe me one lol")
m("more.good_job", "ty", "thanks!", "i try", "ty ty")
m("more.condolence.grief", "thanks. means a lot", "ty... miss them", "yeah. thanks")
m("more.condolence.none", "for what?", "im ok, ty", "huh?")
m("more.lost_stuff", "oof, rip", "thats rough", "unlucky, go get it back quick")
m("more.lost_stuff.offer", "rip, want some food at least?", "oof. need food? i got spare")
m("more.distress", "hey, im here if you want to talk", "hope youre ok. im around", "take it easy, ok? im here")
m("more.did_it", "lets gooo", "W", "gg!!", "nice, told you")
m("more.missed.friend", "missed you too", "good to see you", "same here")
m("more.missed.long", "been ages! where were you", "finally, missed you", "look who's back")
m("more.missed.stranger", "do i know you?", "uh, hi?", "have we met?")
m("more.love.friend", "lol ily2", "aw <3", "love ya bro")
m("more.love.stranger", "ok weirdo", "uh, thanks?", "lol ok")
m("more.fun_fact", "axolotls regenerate", "creepers were a pig mistake", "gold tools mine fastest", "you can sleep through thunder")
m("more.correction", "oh. so it is lol", "mb", "huh, my bad", "fair, you're right")
m("more.fight_me.no", "no thanks", "nah im good", "why would i", "not interested")
m("more.we_good.yes", "yeah were good", "all good", "always")
m("more.we_good.almost", "getting there", "mostly", "almost")
m("more.we_good.no", "no", "not yet", "after what you did? no")
m("more.remember.yes", "of course", "yeah, were friends", "always")
m("more.remember.sort_of", "kinda, i guess", "sort of", "were getting there")
m("more.remember.no", "since when", "we barely know each other", "are we?")
m("more.owe.yes", "yeah fair, i owe you", "i havent forgotten", "ill pay you back")
m("more.owe.no", "i dont owe you anything", "dont think so", "nope")
m("more.ready.yes", "ready", "lets go", "right behind you")
m("more.ready.what", "ready for what", "uh, ready?", "for what lol")
m("more.done", "ok, good run", "alright, see you", "gg, nice trip")
m("more.done.ore", "good run, got some {ore}", "nice, came out with {ore}", "gg, {ore} for me")
m("more.done.mob", "gg, killed a few {mob}s", "good hunt", "nice, cleared some {mob}s")
m("more.deal", "deal", "nice", "good")
m("more.no_deal", "suit yourself", "ok, your loss", "fine")
m("more.hurry", "im going im going", "ok ok", "faster? fine")
m("more.slow_down", "ok, slowing down", "sorry", "kk")
m("more.dots", "...", "what", "yes?", "hm?")
# ----- about it -----
m("more.how_long", "like {days} days", "{days} days now", "been here {days} days", "about {days} days")
m("more.how_long.new", "just got here", "only a day or so", "im pretty new")
m("more.from.town", "{town}", "im from {town}", "{town}, born and raised")
m("more.from.road", "everywhere", "the road", "nowhere really, i move around")
m("more.from.around", "round here", "not far", "a bit that way")
m("more.kind", "i do a bit of everything", "just a normal player", "a bit of all of it")
m("more.kind.builder", "i build", "builder mostly", "i make stuff look nice")
m("more.kind.miner", "i mine", "miner", "caves and ore")
m("more.kind.pvp_sweat", "pvp", "i fight", "pvp all day")
m("more.kind.homebody", "i like home stuff", "farming and cozy stuff", "homebody")
m("more.kind.nomad", "i wander", "traveller", "always on the move")
m("more.kind.speedrunner", "i go fast", "speedrun stuff", "efficient")
m("more.kind.hermit", "keep to myself", "hermit", "i like quiet")
m("more.kind.hoarder", "i collect stuff", "collector", "i keep everything")
m("more.kind.helper", "i help people", "helper", "i like helping out")
m("more.kind.bandit", "you dont want to know", "business", "i take what i want")
m("more.kind.newbie", "im new lol", "learning", "noob tbh")
m("more.why_doing", "need to {doing}", "gotta {doing}", "because i need to {doing}")
m("more.why_doing.nothing", "im not doing anything", "nothing much", "just hanging out")
m("more.standing", "talking to you lol", "just thinking", "waiting", "taking a break")
m("more.hungry.yes", "yeah kinda", "starving", "a bit")
m("more.hungry.no", "nah im good", "just ate", "im fine")
m("more.inv", "{items}", "got {items}", "{items}, nothing special")
m("more.inv.guarded", "none of your business", "stuff", "why do you want to know")
m("more.rich.yes", "{count} diamonds, not bad", "doing alright", "rich enough")
m("more.rich.some", "a couple dias", "{count}, so not really", "a little")
m("more.rich.no", "broke", "no diamonds", "nope, poor")
m("more.item.nothing", "nothing in my hand", "what?", "im not holding anything")
m("more.item.made", "made it myself", "crafted it", "made it")
m("more.item.found", "found it", "got lucky", "traded for it")
m("more.item.enchanted", "its got {enchants}", "{enchants} on it", "enchanted with {enchants}")
m("more.saving", "{goal}", "working on {goal}", "saving for {goal}")
m("more.pets.none", "no pets", "nope", "not yet")
m("more.deaths.none", "never died", "not once", "zero, knock on wood")
m("more.deaths.some", "{count} times", "like {count}", "{count} deaths, dont judge")
m("more.deaths.last", "{count} times. last one, i {death}", "{count}. i {death}")
m("more.best_find", "{ore}", "found {ore} once", "{ore}, best find")
m("more.best_find.none", "nothing great yet", "not much", "still looking")
m("more.been_nether.yes", "yeah, its rough", "been there", "yep")
m("more.been_nether.no", "not yet", "nope", "too scared lol")
m("more.been_end.yes", "yeah", "been there", "seen it")
m("more.been_end.no", "not yet", "no", "one day")
m("more.story", "not really", "nothing interesting", "maybe another time")
m("more.dream", "a big house", "best gear", "just to survive", "be left alone")
m("more.building.yes", "{doing}", "working on {doing}", "just {doing}")
m("more.building.no", "nothing rn", "not building", "nothing yet")
m("more.fav", "{pick}", "{pick} for sure", "gotta be {pick}")
m("more.fav.what", "favorite what", "of what?", "depends what you mean")
m("more.hate", "{pick}", "{pick}, easy", "{pick} for sure")
m("more.pick", "{pick}", "{pick}, easy", "definitely {pick}")
m("more.think", "its alright", "not sure", "never thought about it")
m("more.rate", "{n}/10", "solid {n}", "i'd say {n}", "{n} out of 10")
m("more.good_at", "a bit of everything", "nothing special", "surviving")
m("more.brave.yes", "yeah", "pretty brave", "not much scares me")
m("more.brave.no", "not really", "im a scaredy cat", "creepers scare me ok")
m("more.brave.mid", "kinda", "depends", "sometimes")
m("more.brave.last_life", "not anymore. one more death and im done", "cant afford to be, last life", "no. one life left")
m("more.lonely.yes", "sometimes", "kinda", "a bit")
m("more.lonely.no", "nah", "i got friends", "not really")
m("more.lonely.loner", "i like being alone", "nope", "quiet is good")
m("more.like_here.yes", "yeah its nice", "love it", "its home")
m("more.like_here.passing", "just passing through", "its ok", "for now")
m("more.like_here.sad", "its been hard lately", "used to", "not today")
m("more.miss.friend", "yeah. every day", "a lot", "miss {who} so much")
m("more.miss.barely", "barely knew {who}", "a bit", "sad it happened")
m("more.miss.alive", "they're not dead lol", "miss who?", "they're around")
m("more.best_friend", "{who}", "probably {who}", "{who} for sure")
m("more.best_friend.none", "dont really have one", "no one", "still looking")
m("more.enemies", "{who}", "{who}, ugh", "dont like {who}")
m("more.enemies.none", "no one", "nah", "dont have any")
m("more.enemies.secret", "not saying", "wouldnt you like to know", "no comment")
m("more.strongest", "{who}", "{who} probably", "{who} has the best gear")
m("more.strongest.me", "me obviously", "me lol", "you're looking at them")
m("more.strongest.none", "no idea", "nobody around", "dunno")
m("more.group", "{group}, {leader} leads", "im with {group}", "{group}")
m("more.group.lead", "i lead {group}", "{group}, my crew", "im in charge of {group}")
m("more.group.none", "no group", "solo", "just me")
m("more.make_up.hit", "say sorry for hitting me", "dont hit me again", "start with sorry")
m("more.make_up.theft", "give my stuff back", "return what you took", "my stuff first")
m("more.make_up.grief", "fix what you broke", "repair my place", "fix it")
m("more.make_up.insult", "take back what you said", "say sorry", "be nice")
m("more.make_up.nothing", "were fine", "nothing, we're good", "you dont need to")
m("more.make_up.time", "give it time", "just be around", "be nice and we'll see")
# ----- what it knows -----
m("more.find.yes", "{what} at {coords}", "saw {what} at {coords}", "{coords}, theres {what}")
m("more.find.way", "{what} {way}, about {dist} blocks", "head {way} for {what}", "{way}, maybe {dist} blocks")
m("more.find.no", "not telling", "find your own", "sorry, keeping that one")
m("more.find.none", "havent seen {what}", "no idea where {what} is", "dunno, sorry")
m("more.find.what", "find what?", "where can you find what", "what are you looking for")
m("more.show_where.yes", "ok follow me", "sure, this way", "come on")
m("more.show_where.here", "right here", "this is it", "here")
m("more.your_death.yes", "{way}, about {dist} blocks", "you died {way}, like {dist} blocks", "{way}. {when}")
m("more.your_death.no", "didnt see", "no idea", "wasnt there")
m("more.way.spawn", "{way}, like {dist} blocks", "spawns {way}", "head {way}")
m("more.way.point", "that way", "{way} is there", "there")
m("more.way.what", "which way to what", "to where?", "where to")
m("more.biome", "{biome}", "its {biome}", "looks like {biome}")
m("more.over_there.known", "{what} i think", "theres {what} over there", "{what}")
m("more.over_there.unknown", "no clue", "never been", "dunno, wanna look?")
m("more.bandits.warning", "{warning}", "yeah, {warning}", "careful, {warning}")
m("more.bandits.seen", "saw some earlier", "yeah, careful", "a few around")
m("more.bandits.none", "havent seen any", "dont think so", "seems quiet")
m("more.killed.known", "{who} {how}", "{who} {how}. {when}", "heard {who} {how}")
m("more.killed.unknown", "no idea", "didnt hear", "dunno")
m("more.x_died.friend", "no... {who}? i know. {who} {how}", "yeah... {who} {how}", "dont remind me")
m("more.x_died.known", "yeah, {who} {how}", "heard. {who} {how}", "i know")
m("more.x_died.doubt", "wait what? really?", "no way", "are you sure?")
m("more.weather.rain", "ugh, rain", "yep, raining", "wet day")
m("more.weather.thunder", "storm out there", "loud one", "stay inside")
m("more.weather.night", "dark out", "night time", "cant see the weather lol")
m("more.weather.clear", "nice day", "clear skies", "good weather")
m("more.who_here", "{who}", "saw {who}", "{who} around")
m("more.who_here.nobody", "nobody", "just us", "no one")
m("more.who_stole", "didnt see", "wasnt watching", "no idea, sorry")
m("more.whose.mine", "mine", "thats mine", "mine lol")
m("more.whose.known", "{who}'s", "thats {who}'s", "{who} owns that")
m("more.whose.unknown", "no idea", "dunno", "not sure")
m("more.x_doing", "{who}'s trying to {doing}", "{doing}", "{who}? {doing}")
m("more.x_doing.nothing", "nothing much", "just standing", "no idea")
m("more.x_doing.unknown", "who?", "cant see them", "dunno")
m("more.everyone.night", "asleep probably", "its night", "in bed")
m("more.everyone.day", "around somewhere", "working", "busy")
m("more.gossip.likes", "{who}'s cool", "i like {who}", "{who} is alright")
m("more.gossip.hates", "{who}, dont get me started", "{who}... ugh", "dont trust {who}")
m("more.gossip.none", "nothing juicy", "all quiet", "no drama")
m("more.gossip.no", "i dont gossip", "not my thing", "no")
m("more.recipe", "{needs}", "{item}: {needs}", "you need {needs}")
m("more.recipe.unknown", "no idea", "cant make that", "dunno")
m("more.recipe.what", "make what?", "recipe for what", "what do you want to make")
m("more.ylevel.diamond", "deep, like -58", "bottom of the world", "y -59")
m("more.ylevel.iron", "around 16, or mountains", "y 16", "mountains have tons")
m("more.ylevel.gold", "around -16", "badlands", "deep")
m("more.ylevel.coal", "anywhere, up high", "mountains", "everywhere")
m("more.ylevel.copper", "around 48", "dripstone caves", "mid levels")
m("more.ylevel.lapis", "around 0", "y 0", "middle")
m("more.ylevel.redstone", "deep, near diamonds", "bottom", "-58")
m("more.ylevel.emerald", "mountains", "up high", "mountains only")
m("more.ylevel.debris", "nether, y 15", "y 15 in the nether", "nether, low")
m("more.tips.general", "shield up", "hit and back off", "dont get surrounded", "eat before you fight")
m("more.tips.creeper", "hit it and back off", "shield", "dont let it get close")
m("more.tips.skeleton", "shield and charge", "use cover", "get close fast")
m("more.tips.enderman", "dont look at it", "stand under 2 blocks", "water")
# ----- doing things -----
m("more.go_away", "ok ok", "fine", "geez, going")
m("more.go_away.my_house", "its my house??", "you leave lol", "this is my place")
m("more.move_aside", "oops", "sorry", "mb")
m("more.meet.yes", "ok see you there", "sure, be there soon", "on my way")
m("more.meet.no", "dont know you that well", "no", "maybe not")
m("more.meet.where", "where?", "meet you where", "where exactly")
m("more.walk_home.yes", "ok, lead on... wait i'll lead", "sure, this way", "come on")
m("more.walk_home.no", "nah", "you can make it", "not today")
m("more.walk_home.unknown", "where do you live?", "dunno where your base is", "where is it?")
m("more.walk_home.here", "here you go", "home sweet home", "made it")
m("more.explore.yes", "lets go", "adventure time", "im in")
m("more.calm_together.yes", "sure", "sounds nice", "ok, im in")
m("more.race", "nah im good", "not rn", "maybe later")
m("more.eat.yes", "sure, want some?", "ok, im hungry too", "lets eat")
m("more.eat.none", "no food on me", "i got nothing", "out of food")
m("more.bring.yes", "sure, here", "ok coming", "one sec")
m("more.bring.none", "no {item} on me", "dont have any {item}", "out of {item}")
m("more.bring.no", "nah", "get your own", "no")
m("more.bring.what", "bring you what", "what do you need", "what?")
m("more.craft_me", "cant rn", "ask the smith", "do it yourself lol")
m("more.rescue.yes", "on my way", "coming!", "hold on")
m("more.rescue.no", "sorry, cant", "you'll be ok", "nah")
m("more.work_for_me", "not for hire", "im good", "no thanks")
m("more.attack.yes", "on it", "got it", "coming")
m("more.attack.no", "thats not a monster", "no", "why")
m("more.attack.what", "attack what", "what?", "where")
m("more.attack.scared", "no way", "you do it", "not me")
m("more.run", "run from what", "ok", "why")
m("more.run.fighting", "go go go", "running!", "ok!")
m("more.stop_attacking.yes", "ok sorry", "fine", "stopping")
m("more.stop_attacking.nothing", "im not attacking anything", "what?", "huh")
m("more.stop_attacking.monster", "its a monster", "nope", "it started it")
m("more.yield.yes", "ok ok i give up", "fine. you win", "gg")
m("more.yield.no", "never", "no", "not yet")
m("more.want.yes", "yes please", "ooh yes", "sure")
m("more.want.no", "nah", "no thanks", "im good")
m("more.want.what", "want what", "what?", "what is it")
m("more.name_price", "not selling", "its not for sale", "its mine")
m("more.give_back.gift", "you gave it to me", "it was a gift", "no take backs")
m("more.give_back.nothing", "i didnt take anything", "what?", "i dont have your stuff")
m("more.rob.gives", "ok ok take it", "here, just leave me alone", "fine, take it")
m("more.rob.nothing", "i got nothing", "nothing to give", "im broke")
m("more.rob.brave", "make me", "try it", "no")
m("more.use.yes", "sure", "go ahead", "yeah")
m("more.use.chest_no", "not the chest", "dont touch the chest", "no")
m("more.take.yes", "sure, take some", "go ahead", "help yourself")
m("more.take.hoarder", "fine. just a little", "one. only one", "ugh fine")
m("more.take.no", "no", "dont", "absolutely not")
m("more.pet_animals", "sure", "go ahead", "be gentle")
m("more.dont_touch", "ok", "wasnt going to", "fine")
m("more.suggest.night", "sleep", "go to bed", "wait for morning")
m("more.suggest.gear", "get some armor", "go mining", "make some iron armor")
m("more.suggest.explore", "go explore", "find a village", "go to the nether")
m("more.look_house.yes", "sure, show me", "ok lead the way", "lets see it")
m("more.look_house.no", "maybe later", "nah", "not now")
m("more.look_house.opinion", "nice place", "not bad", "cozy")
m("more.look_house.builder", "needs more detail", "roof could be better", "decent")
m("more.relay.ok", "sure, ill tell {who}", "ok, will do", "ill pass it on")
m("more.relay.who", "tell who?", "who?", "tell who what")
m("more.relay.deliver", "hey {who}, {from} {what}", "{who}, {from} {what}", "oh {who}, {from} {what}")
m("more.relay.heard", "oh ty", "ok cool", "good to know")
m("more.new_gear", "nice {item}", "ooh, {item}", "sick {item}")
m("more.new_gear.nothing", "what?", "i dont see anything", "show me")
m("more.safe_now", "oh nice, ty", "good to know", "thanks for clearing it")
m("more.safe_now.doubt", "sure", "ill see for myself", "hm")
m("more.home_alarm.go", "WHAT", "on my way", "no no no")
m("more.home_alarm.real", "ty for telling me!!", "phew, thanks", "thanks!!")
m("more.home_alarm.false", "theres nothing wrong", "not funny", "looks fine to me")
m("more.walked_in", "uh, hello?", "can i help you?", "this is my house")
m("more.walked_in.friend", "oh hey, come in", "make yourself at home", "hey!")
m("more.walked_in.hoarder", "dont touch anything", "eyes off the chests", "out")
m("more.dead_named", "{who}'s gone. {who} {how}", "{who} {how}. {when}", "{who}... {how}")
m("more.history", "{when}, {event}", "{event}", "well, {event}")
m("more.history.none", "nothing much", "quiet lately", "not that i know")

# ----- town -----
t("town.more.price", "{who} sells {item} for {price}", "{item}? {price} at the stall", "about {price}")
t("town.more.price.mine", "{item}? {price}", "{price} for {item}", "{price}")
t("town.more.price.not_sold", "nobody sells {item} here", "no {item} at the stall", "we dont sell {item}")
t("town.more.price.what", "how much is what", "price of what?", "what do you want")
t("town.more.buy.mine", "{price}, right click me", "ok, {price}", "{price}, throw it or trade")
t("town.more.buys.list", "{who} buys {goods}", "mostly {goods}", "{goods}")
t("town.more.buys.item", "{who} pays {price} for {item}", "about {price}", "{price} each")
t("town.more.money.emeralds", "emeralds", "we take emeralds", "emeralds only")
t("town.more.money.coins", "coins", "we take coins", "bronze, silver, gold")
t("town.more.live.welcome", "ask {leader}, but youd be welcome", "sure, talk to {leader}", "id like that")
t("town.more.live.maybe", "ask {leader}", "maybe, talk to {leader}", "depends on {leader}")
t("town.more.live.no", "i dont think so", "no", "not after what happened")
t("town.more.build.inside", "not in the middle of town", "theres no room here", "outside town maybe")
t("town.more.build.outside", "out here's fine", "sure, just not on the road", "go for it")
t("town.more.nation", "{nation}, capitals {capital}", "were part of {nation}", "{nation}")
t("town.more.nation.capital", "this is the capital of {nation}", "{nation}, and youre in the capital", "{nation}")
t("town.more.nation.none", "no nation", "were on our own", "just this town")
t("town.more.like.yes", "yeah its nice", "love it here", "its home")
t("town.more.like.taxes", "taxes are too high", "its ok", "could be better")
t("town.more.like.sad", "its been hard", "used to be", "not lately")
t("town.more.allies", "{them}", "friends with {them}", "{them}, mostly")
t("town.more.allies.none", "nobody", "we keep to ourselves", "no allies")
t("town.more.allies.enemies_only", "no friends, just {enemies}", "at war with {enemies}", "{enemies}, and theyre not friends")
t("town.more.war", "{them}", "were fighting {them}", "war with {them}")
t("town.more.war.none", "no war", "peaceful", "nope")
t("town.more.wanted", "{who}", "{who} is wanted", "watch out for {who}")
t("town.more.wanted.none", "nobody", "no one", "clean town")
t("town.more.join.yes", "sure, come on", "everyone's welcome", "yeah!")
t("town.more.join.nothing", "nothing on today", "no party today", "nothing going on")
t("town.more.market.today", "today!", "its today", "right now")
t("town.more.market.tomorrow", "tomorrow", "tmrw", "next day")
t("town.more.market.soon", "in {days} days", "{days} days", "soon")
t("town.more.market.none", "no market", "dunno", "not soon")
t("town.more.leader.good", "{leader}'s good", "fine", "i like {leader}")
t("town.more.leader.careful", "cant complain", "theyre ok", "no comment")
t("town.more.leader.frank", "honestly? not great", "taxes are killing us", "between us, no")
t("town.more.leader.me", "im doing my best", "you tell me", "pretty good, if i say so")
t("town.more.who_lives", "{who}", "thats {who}", "{who}, so its taken")
t("town.more.who_lives.me", "me", "i do", "this is mine")
t("town.more.who_lives.nobody", "nobody", "its empty", "no one")
t("town.more.guards.coming", "guards are coming!", "help is coming", "theyre on their way")
t("town.more.guards.none", "no guards near", "theyre not around", "nobody coming")
t("town.more.report", "tell a guard", "the guards should hear", "go to the guards")
t("town.more.report.guard", "ill keep an eye out", "noted", "ill look into it")
t("town.more.innocent.nothing", "nobody said you did", "did what?", "youre fine")
t("town.more.innocent.guard", "tell it to the leader", "sure", "everyone says that")
t("town.more.innocent.friend", "i believe you", "ok", "i know you")
t("town.more.innocent.doubt", "sure", "right", "if you say so")
t("town.more.sorry.fine", "pay the {fine} to a guard", "{fine}, to a guard", "throw it to a guard")
t("town.more.sorry.leader", "only the leader can pardon you", "talk to the leader", "too late for sorry")
t("town.more.sorry.nothing", "you dont owe anything", "nothing to pay", "youre clear")
t("town.more.haggle.no", "prices are prices", "no", "nope")
t("town.more.haggle.friend", "for you, a little", "ok fine, a bit off", "only for you")
t("town.more.haggle.never", "never", "no discounts", "full price")
t("town.more.haggle.not_mine", "not my stall", "ask the keeper", "not mine")
t("town.more.job_done", "give it to whoever asked", "nice, go tell them", "the one who asked'll want it")
t("town.more.raid.believe", "really?? ill tell people", "oh no, ty", "ill warn everyone")
t("town.more.raid.guard", "ill be ready", "thanks, ill watch", "noted. ill tell the others")
t("town.more.raid.doubt", "sure", "why should i believe you", "hm")

COMMENT_M = ("Batch 2 of talking with a player (docs/PLAYER_TALK.md, rows marked 2; social/talk/TalkMore.java). 'answers': the phrases "
             "players type; 'lines': its replies by voice. Started by tools/chat_more/more_seed.py; more lines added with merge.py.")
COMMENT_T = ("Batch 2 of asking a townsperson about its town (docs/PLAYER_TALK.md section D; TownTalkMore.java). Started by "
             "tools/chat_more/more_seed.py; more lines added with merge.py.")

if __name__ == '__main__':
    root = sys.argv[1]
    for rel, comment, answers, lines_ in (('src/main/resources/data/drifter/drifter_chat/convo_more.json', COMMENT_M, A, L),
                                          ('src/civ/resources/data/drifter_civ/drifter_chat/town_more.json', COMMENT_T, TOWN, T)):
        out = json.dumps({"_comment": comment, "answers": answers, "lines": lines_}, indent=1, ensure_ascii=False)
        open(os.path.join(root, rel), 'w', encoding='utf-8', newline='\r\n').write(out + '\n')
        print(rel, len(answers), 'meanings', sum(len(v) for v in answers.values()), 'phrases;', len(lines_), 'kinds',
              sum(len(x) for v in lines_.values() for x in v.values()), 'lines')
