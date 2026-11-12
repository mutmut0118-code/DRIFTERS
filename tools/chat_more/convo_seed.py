"""Writes the starting drifter_chat/convo.json: the phrases players type for each new meaning (docs/PLAYER_TALK.md,
batch 1) and a few lines for each reply. More lines are added after with merge.py; run this only to start the file
over (it replaces it).

Usage: python tools/chat_more/convo_seed.py <project dir>
"""
import json
import os
import sys

ANSWERS = {
    # ----- the glue -----
    "laugh": ["lol", "lmao", "lmfao", "haha", "hahaha", "hehe", "xd", "rofl", "im dead", "im crying", "thats funny", "thats hilarious", "lmaoo"],
    "nice": ["nice", "cool", "thats cool", "sick", "epic", "awesome", "thats awesome", "neat", "dope", "pog", "poggers", "sweet", "thats nice",
             "good for you", "good stuff"],
    "wow": ["wow", "woah", "whoa", "damn", "dang", "omg", "no way", "fr", "for real", "seriously", "really", "wait really", "wait what", "holy",
            "sheesh", "are you serious", "you serious", "deadass"],
    "agree": ["same", "me too", "me neither", "mood", "relatable", "real", "facts", "true", "so true", "ikr", "i know right", "agreed", "exactly",
              "fair", "fair enough", "right", "yeah same", "big facts", "valid"],
    "doubt": ["cap", "thats cap", "i doubt it", "doubt", "no you didnt", "liar", "sure buddy", "yeah right", "thats wrong", "youre lying", "i dont believe you",
              "thats not true", "prove it", "nah no way"],
    "sympathy": ["oof", "rip", "unlucky", "that sucks", "sad", "sorry to hear", "sorry to hear that", "thats rough", "that hurts", "ouch", "big oof",
                 "that stinks", "poor you", "aw", "aww"],
    "ack": ["ok", "okay", "k", "kk", "alright", "i see", "got it", "makes sense", "hm", "hmm", "mhm", "oh", "oh ok", "ah", "ah ok", "gotcha", "okay then",
            "if you say so", "interesting", "good to know", "noted", "it has", "it is", "it does"],
    "pushback": ["its alright", "just alright", "thats it", "thats all", "only that", "is that all", "only alright", "just fine", "thats boring",
                 "come on", "cmon", "be honest", "be serious"],
    "why": ["why", "why not", "how come", "whys that", "why is that", "y tho", "why tho", "but why", "whyy", "why do you ask", "whats it to you",
            "what for", "why cant you", "why wont you"],
    "wdym": ["wdym", "what do you mean", "what", "huh", "meaning", "what does that mean", "what are you talking about", "wym", "i dont get it",
             "i dont understand", "explain", "what did you say", "say that again", "come again", "pardon"],
    "nvm": ["nvm", "nevermind", "never mind", "forget it", "forget i asked", "doesnt matter", "its nothing", "dw", "dont worry about it", "nothing",
            "no reason"],
    "talk_to_me": ["talk to me", "say something", "lets chat", "lets talk", "wanna chat", "wanna talk", "im bored talk", "entertain me", "tell me something",
                   "ask me something", "ask me anything", "got a minute", "can we talk", "chat with me"],
    "you_there": ["you there", "are you there", "are you listening", "answer me", "anyone there", "are you ignoring me", "earth to", "respond", "say something back", "are you even listening", "u there"],
    "not_you": ["not you", "i wasnt talking to you", "wasnt talking to you", "wrong person", "not talking to you", "i didnt mean you", "who asked you",
                "i meant someone else"],
    "wait_up": ["wait up", "stop walking away", "come back", "dont walk away", "where are you going come back", "stay still", "stop moving", "hold still",
                "dont leave", "wait for me", "hey come back", "stop running", "stop running away"],
    "sorry": ["sorry", "sry", "my bad", "mb", "im sorry", "i apologize", "apologies", "sorry about that", "sorry for that", "didnt mean to", "it was an accident",
              "accident", "misclick", "forgive me", "sorry i hit you", "sorry for hitting you", "my fault", "soz", "sorry bro"],
    "middle": ["kinda", "kind of", "sort of", "a bit", "a little", "sometimes", "eh", "meh", "so so", "not really", "i guess", "depends", "it depends"],
    "brb": ["brb", "be right back", "one sec", "one second", "hold on", "hang on", "give me a sec", "gimme a sec", "afk", "just a sec", "wait a sec"],
    "gl": ["gl", "good luck", "be safe", "stay safe", "careful out there", "take care", "be careful", "have fun", "glhf"],
    "iam": ["i am", "yes i am", "i am yeah", "that i am", "sure am", "i sure am"],
    "everyone": ["you guys", "everyone", "everybody", "anyone", "guys", "yall", "you all", "hey all", "hi all", "chat", "anybody", "any1", "ppl"],

    # ----- about it -----
    "have": ["do you have", "do you have any", "you got any", "got any", "you have any", "have you got", "are you carrying", "do you got", "you got a",
             "got a spare"],
    "gear": ["nice sword", "nice armor", "nice armour", "nice helmet", "nice hat", "nice pick", "nice pickaxe", "nice axe", "nice bow", "nice shield",
             "nice boots", "nice gear", "nice skin", "nice outfit", "nice fit", "cool sword", "cool armor", "cool helmet", "cool hat", "cool skin",
             "cool outfit", "i like your sword", "i like your armor", "i like your helmet", "i like your hat", "i like your pick", "i like your skin",
             "i like your outfit", "i like your gear", "i like your boots", "i like your shield", "i like your bow", "i like your axe", "love your skin",
             "love your hat", "love your helmet", "love your armor", "sick sword", "sick armor", "sick skin", "nice drip", "cool gear", "nice trident",
             "nice mace", "i like your fit", "like your hat", "like your helmet", "like your skin", "like your sword"],
    "your_house": ["is this your house", "is this your base", "is that your house", "whose house is this", "whose house is that", "whose base is this",
                   "who lives here", "is this yours", "is this your place", "this your house", "this your base", "do you live here", "you live here",
                   "is this where you live", "who built this"],
    "nice_house": ["nice house", "nice base", "nice build", "nice place", "cool house", "cool base", "cool build", "i like your house", "i like your base",
                   "love your house", "love your base", "your house is nice", "your base is nice", "your house looks good", "sick base", "sick house",
                   "sick build", "cozy house", "nice home", "i like your place", "nice crib"],
    "show_house": ["show me your house", "show me your base", "can i see your house", "can i see your base", "show me your place", "give me a tour of your house",
                   "take me to your house", "take me to your base", "lets see your house", "lets see your base", "show me where you live",
                   "wanna show me your house", "can you show me your house", "can you show me your base"],
    "today": ["what did you do today", "how was your day", "hows your day", "hows your day been", "what have you been up to", "what have you been doing",
              "what did you do", "been up to much", "do anything fun today", "what happened today", "busy day", "how was today", "whatd you do today"],
    "good_pvp": ["are you good at pvp", "you good at pvp", "can you fight", "are you strong", "are you any good at pvp", "are you good at fighting",
                 "you any good at pvp", "how good are you at pvp", "do you pvp", "are you a good fighter", "can you pvp"],
    "mood_why": ["whats wrong", "why are you mad", "why are you sad", "why are you angry", "why so sad", "why so mad", "why so grumpy", "you seem off",
                 "you seem sad", "you seem mad", "whats the matter", "whats up with you", "are you sad", "are you mad", "are you angry", "are you happy",
                 "why are you upset", "are you upset", "what happened to you", "why the long face", "everything ok"],
    "who_is": ["do you know", "who is", "whos", "tell me about", "what do you know about", "have you met"],
    "grudge": ["why dont you like me", "why do you hate me", "what did i do", "what did i do to you", "why are you mad at me", "why are you ignoring me",
               "why did you attack me", "why did you hit me", "what was that for", "why are you being mean", "why are you so cold", "whats your problem with me",
               "what do you have against me", "why are you after me", "why did you do that", "why you mad at me"],
    "be_friends": ["wanna be friends", "want to be friends", "be my friend", "can we be friends", "lets be friends", "will you be my friend",
                   "can i be your friend", "we should be friends", "do you want to be friends"],

    # ----- what it knows -----
    "where_food": ["wheres food", "where is food", "where can i get food", "where can i find food", "where do i get food", "any food around", "wheres the food",
                   "is there food", "where do you get food", "how do i get food", "know where food is", "i need food where"],
    "danger": ["is it safe", "is it safe here", "is it safe at night", "whats dangerous", "anything dangerous", "any danger", "where shouldnt i go",
               "is it dangerous", "is this place safe", "any mobs around", "are there mobs", "should i be worried", "what should i watch out for",
               "is it safe round here", "whats dangerous around here"],
    "news": ["any news", "whats new", "whats been going on", "what did i miss", "whats going on around here", "anything happen", "anything new",
             "whats the news", "got any news", "heard anything", "what happened here", "can you tell me what happened", "tell me what happened",
             "what happened", "whats been happening", "any gossip", "whats the tea", "tell me about what happened here", "what has been going on"],
    "who_died": ["who died", "did anyone die", "did someone die", "who got killed", "anyone die", "who is dead", "who was killed"],
    "new_tips": ["any tips", "got any tips", "what should i do first", "what do i do", "how do i survive", "im new help", "help im new", "what should i do",
                 "any advice", "got any advice", "how do i start", "where do i start", "what now", "tips for a new player", "teach me"],
    "im_new": ["im new", "im new here", "i am new", "i am new here", "i just got here", "just got here", "i just joined", "just joined", "first day",
               "its my first day", "first time here", "new here", "i just started", "just started"],

    # ----- telling it how they are -----
    "im_hurt": ["im hurt", "im dying", "im low", "im on half a heart", "im almost dead", "i need healing", "heal me", "im injured", "i have no health",
                "im on low health", "im bleeding"],
    "im_lost": ["im lost", "i am lost", "i got lost", "i dont know where i am", "i cant find my way", "i cant find my base", "i cant find my house",
                "how do i get home", "how do i get back", "wheres my base", "i lost my base", "which way is home", "lost my way"],
    "im_scared": ["im scared", "i am scared", "im afraid", "this is scary", "i hate the dark", "i dont want to go down there", "im terrified",
                  "im nervous", "its scary", "i dont like this"],
    "danger_tell": ["dont go in the mine", "dont go down there", "dont go in there", "its dangerous there", "its dangerous down there", "its not safe there",
                    "its not safe down there", "theres a dungeon down there", "theres a dungeon in the mine", "stay away from the mine", "stay out of the mine",
                    "the mine is dangerous", "the mine isnt safe", "dont go that way", "its dangerous here", "its not safe here", "stay away from there",
                    "theres something down there", "the cave is dangerous", "dont go in the cave"],

    # ----- asking it to do things -----
    "stay": ["stay here", "wait here", "stay", "stay put", "dont move", "wait right here", "stay right here", "stay there", "wait there", "hold here",
             "just wait here", "dont go anywhere"],
    "look": ["look at this", "look at that", "look over there", "look here", "check this out", "look what i found", "look what i made", "look what i built",
             "see that", "see this", "do you see that", "look up", "look down", "look there", "come look at this", "look at my"],
    "mine_with": ["lets go mining", "lets mine", "come mine with me", "wanna go mining", "wanna mine", "want to go mining", "mine with me", "lets go caving",
                  "wanna go caving", "come mining", "come caving", "lets go to the mine", "wanna mine with me", "wanna come mining", "lets go dig",
                  "lets get some diamonds", "come to the mine with me"],
    "hunt_with": ["lets kill mobs", "lets go kill mobs", "wanna kill mobs", "do you wanna kill mobs", "lets hunt", "lets go hunting", "wanna hunt",
                  "wanna go hunting", "lets fight mobs", "lets fight some mobs", "lets kill some zombies", "lets fight zombies", "come hunt with me",
                  "hunt with me", "lets go fight", "lets go kill stuff", "wanna fight mobs", "lets kill stuff", "want to kill mobs", "help me kill mobs",
                  "lets go monster hunting", "lets kill monsters", "wanna kill monsters"],
    "warning": ["behind you", "watch out", "look out", "creeper behind you", "heads up", "look behind you", "zombie behind you",
                "skeleton behind you", "theres a creeper", "watch your back", "incoming", "get out of there", "its behind you"],
    "truce": ["truce", "i give up", "i surrender", "i yield", "dont hurt me", "stop hitting me", "mercy", "spare me", "i come in peace",
              "i dont want to fight", "lets stop fighting", "stop attacking me", "dont kill me", "please dont kill me", "i mean no harm"],
    "offer": ["here take this", "take this", "this is for you", "i got you something", "i have something for you", "want this", "do you want this",
              "i got food", "i have food", "i got food for you", "i have food for you", "here you go", "heres some food", "have some food", "want some food",
              "need a sword", "i brought you something", "a gift for you", "got a gift for you", "here have this", "catch"],
    "come_in": ["can i come in", "may i come in", "mind if i come in", "can i come inside", "let me in", "im coming in", "im coming inside", "knock knock",
                "anyone home", "can i enter", "ok if i come in", "open up", "is it ok if i come in"],
    "sleep_here": ["can i sleep here", "can i stay the night", "can i stay here", "can i use your bed", "can i sleep in your house", "can i crash here",
                   "got a spare bed", "do you have a spare bed", "can i stay with you", "let me sleep here", "can i spend the night"],
    "wake_up": ["wake up", "get up", "rise and shine", "wakey wakey", "wake up please", "time to wake up", "get out of bed", "stop sleeping", "wakeup"],

    # ----- hostile, and taking it back -----
    "threat": ["im gonna kill you", "im going to kill you", "ill kill you", "youre dead", "you are dead", "watch your back or", "im coming for you",
               "ill end you", "prepare to die", "you will die", "ill burn your house", "ill burn your house down", "im gonna burn your house",
               "ill take everything", "say goodbye", "say your prayers", "ill destroy you", "im gonna hurt you", "your days are numbered",
               "ill blow up your house", "im gonna grief you"],
    "threat_all": ["im gonna kill all of you", "im going to kill all of you", "ill kill you all", "ill kill all of you", "everyone is going to die",
                   "you are all going to die", "youre all dead", "you will all die", "ill burn this town", "ill burn this town down", "im gonna destroy this town",
                   "this town is going to burn", "ill kill everyone", "im gonna kill everyone", "everyone here is going to die", "ill destroy this place",
                   "a raid is coming for you all", "none of you will survive", "this town is finished", "ill raid this town", "im going to raid this town",
                   "say goodbye to your town", "everyone dies", "war is coming for this town"],
    "jk": ["jk", "just kidding", "im joking", "im kidding", "i was joking", "i was kidding", "just a joke", "it was a joke", "lol relax", "relax",
           "chill it was a joke", "only joking", "kidding", "j k", "calm down it was a joke", "i didnt mean it", "i take it back", "no offense",
           "just messing", "im just messing with you", "just playing"],
}

L = {}


def lines(key, *any_lines, **voices):
    L[key] = {"any": list(any_lines), **{k: list(v) for k, v in voices.items()}}


# ----- states -----
lines("convo.gross", "nah", "weird thing to say", "not answering that", "ok no")
lines("convo.asleep", "zzz", "...zzz", "mm. sleeping", "bro its night", "zzz... 5 more min", "im asleep. go away",
      hermit=["...", "zzz. no"], newbie=["zzz... wha", "im sleeping!!"], pvp_sweat=["afk. sleeping", "zzz. 1v1 tmrw"])
lines("convo.woken", "ugh. what", "im up im up", "what is it", "this better be good", "huh what happened",
      hermit=["...what", "what"], helper=["im up! whats wrong?", "ok ok im awake, you ok?"], newbie=["wha?? what happened", "im awake!!"])
lines("convo.fighting", "busy!!", "not now!!", "kinda fighting rn", "one sec", "in a fight", "LATER",
      pvp_sweat=["busy. fighting", "not now"], newbie=["HELP", "cant talk dying"], hermit=["busy", "not now"])
lines("convo.gtg", "anyway, gtg", "ok back to it", "gtg, cya", "alright im off", "catch you later {player}", "ok later",
      hermit=["ok. bye", "going now"], helper=["ok gtg, shout if you need me!", "back to work, take care {player}"], speedrunner=["gtg", "ok moving on"])
lines("convo.gtg_doing", "anyway, gotta {doing}", "ok back to it, need to {doing}", "gtg, got to {doing}", "alright, back to work",
      hermit=["busy. bye"], speedrunner=["no time. gotta {doing}"])
# ----- meeting -----
lines("convo.met", "nice to meet you {player}", "hey {player}, nice to meet you", "{player}, got it. welcome", "good to meet you {player}", "oh nice, hi {player}",
      newbie=["hi {player}!! nice to meet you!!", "omg hi {player}"], hermit=["...{player}. ok", "hm. {player}"], helper=["welcome {player}! good to have you", "nice to meet you {player}! shout if you need anything"],
      pvp_sweat=["{player}. cool", "sup {player}"], bandit=["{player}. ill remember that", "pleasure, {player}"])
lines("convo.met_hi", "hey! nice to meet you", "hi, welcome", "good to meet you", "hey, glad you stopped by",
      newbie=["hi!! nice to meet you!!"], hermit=["hi."], helper=["hi! welcome welcome"])
lines("convo.new_yes", "oh nice, welcome", "welcome then. its a good spot", "ah ok, welcome", "well, welcome. dont die lol", "nice, welcome aboard",
      newbie=["me too kinda!! welcome!!", "yay another new person"], hermit=["hm. welcome i guess"], helper=["welcome!! ask me if you need anything", "oh welcome! need food or tools?"],
      pvp_sweat=["fresh spawn huh. welcome", "welcome. dont die"], bandit=["welcome... keep your stuff close", "fresh face. welcome"])
lines("convo.new_yes_tour", "oh nice, welcome. want me to show you round?", "welcome then. wanna see the place?", "ah, welcome. i can show you around if you want",
      "welcome! want a quick tour?", helper=["welcome!! want me to show you around?", "oh welcome! come on, ill show you round. want to?"],
      newbie=["welcome!! want me to show you around?? i know like half of it"], hermit=["welcome. i can show you round. if you want"])
lines("convo.new_no", "oh mb, havent seen you around", "ah ok, my bad", "oh. guess i missed you before", "mb, thought you were new", "oh ok, hey then",
      hermit=["oh.", "ok"], newbie=["oh sorry!!", "oops mb"], pvp_sweat=["k", "mb"])
lines("convo.tips", "get wood, make a bed, dont dig straight down", "food first, then a bed before night", "torches. so many torches. and always carry food",
      "stay near people at night and youll be fine", "make a shield early, trust me",
      newbie=["idk im new too lol", "dont punch the iron golems... i learned"], hermit=["dont die"], speedrunner=["wood, stone pick, iron, go", "bed first. skip the night"],
      helper=["get some food and a bed first! i can spare some bread", "stick with me a bit and youll be fine"], miner=["never dig straight down. ever", "iron first, then diamonds at the bottom"])
lines("convo.tips_tour", "get a bed and some food first. want me to show you round?", "stay near the town at night. i can show you around if you want",
      "food, bed, torches. wanna see where everything is?", helper=["first things first, let me show you around. want to?"],
      hermit=["bed. food. i can show you the place. if you want"])
# ----- pvp -----
lines("convo.pvp.offer", "wanna find out? 1v1?", "one way to know. 1v1?", "lets see. duel?", "prove it then, 1v1?",
      pvp_sweat=["bet. 1v1 me rn?", "kinda? we'll see. 1v1?", "id clap you. wanna 1v1?"], bandit=["lets find out. duel?"], newbie=["um... wanna practice fight?"])
lines("convo.pvp.declined", "ok lol", "scared? jk", "fair, another time", "all good",
      pvp_sweat=["thought so", "scared huh", "L"], helper=["no worries!"])
lines("convo.pvp.you_no", "lol fair, me neither", "honest. i respect it", "we all start somewhere", "ok at least youre honest",
      pvp_sweat=["lol. i can tell", "practice then"], helper=["thats ok! i can show you a few things sometime"])
lines("convo.pvp.ok", "im alright i guess", "decent. not great", "i can hold my own", "i survive lol",
      newbie=["no im so bad", "i lose to zombies sometimes"], hermit=["good enough"], helper=["im ok! id rather not though"])
lines("convo.pvp.bad", "i dont even have a sword rn", "not without a weapon lol", "nah, no gear", "ask me when i have a sword",
      newbie=["no!! i dont even have a sword"], hermit=["no"])
# ----- the tour -----
lines("convo.tour.start", "ok follow me", "sure, this way", "come on, ill show you", "alright, quick tour. keep up", "ok stick close",
      helper=["yay ok follow me!", "of course! this way"], hermit=["fine. follow"], speedrunner=["ok fast tour. keep up"], newbie=["ok ok follow me!! i think its this way"])
lines("convo.tour.declined", "ok, maybe later", "no worries", "all good, ask if you change your mind", "ok np", hermit=["ok"], helper=["ok! just ask anytime"])
lines("convo.tour.nothing", "not much to show round here tbh", "theres nothing to show yet lol", "its just this. thats the tour")
lines("convo.tour.done", "and thats about it", "thats the tour", "thats everything really", "ok thats the place. welcome", "and thats us",
      helper=["and thats everything! welcome again", "thats the tour! ask if you forget where something is"], hermit=["thats it"], speedrunner=["done. tour over"])
lines("convo.tour.shop", "this is the stall. you can buy and sell here", "the market stall. good prices on market day", "heres the shop, right click the keeper",
      "stall's here if you need stuff", hoarder=["the stall. overpriced. dont tell them i said that"], helper=["heres the stall! great for food and tools"])
lines("convo.tour.smith", "the smith works round here. repairs and iron stuff", "this is where you get tools fixed", "smith's place. bring iron",
      "need armor or a repair, this is the spot")
lines("convo.tour.plaza", "this is the plaza, everyone ends up here", "the plaza. festivals and market day happen here", "middle of town, the plaza",
      "plaza. good place to find people")
lines("convo.tour.well", "the well. yes its real water", "town well, free water", "heres the well")
lines("convo.tour.board", "notice board. jobs and bounties go up here", "check the board for work, it pays", "the board, people post what they need",
      "this is the notice board, worth a look every day")
lines("convo.tour.mine", "thats the town mine. careful down there", "the mine. bring torches and a sword", "town mine's down here, watch your step",
      "this goes down to the mine. dont go alone at first", miner=["the mine. best place in town", "this is where the good stuff is"])
lines("convo.tour.fire", "the town fire. we sit here most evenings", "campfire, come hang out at night", "this is where everyone sits in the evening")
lines("convo.tour.farm", "the fields. dont trample them or people get mad", "town farm. the food comes from here", "crops are here, leave them be")
lines("convo.tour.pen", "the animal pen. please dont hit them", "animals are kept here", "this is the pen, cows and such")
lines("convo.tour.store", "the town store. those chests arent free", "storehouse. dont take from it, its a crime", "this is where the town keeps its stuff")
lines("convo.tour.home", "and thats my place", "my house is that one", "i live just here")
lines("convo.guide.home", "this is it", "here we are, my place", "home sweet home", "and this is mine", "ta da",
      builder=["this is it. still working on the roof", "here. be honest, hows the front look"], hermit=["this. dont touch anything"],
      hoarder=["here. the chests are off limits"], newbie=["this is my house!! do you like it"], homebody=["here we are! come in if you like"])
lines("convo.guide.wait", "keep up", "this way", "you coming?", "over here", "cmon", "still with me?",
      speedrunner=["hurry up", "faster"], helper=["this way! take your time", "over here!"], hermit=["...coming?"])
# ----- the fallback -----
lines("convo.unknown", "?", "wdym", "huh", "lol what", "what", "hm?", "not sure what you mean", "say again?", "idk what that means lol",
      newbie=["huh??", "what does that mean", "i dont get it sorry"], hermit=["what", "?", "hm"], pvp_sweat=["what", "?", "bro what"],
      helper=["sorry, what do you mean?", "hm? say that again?"], casual=["lol what", "huh lol", "wdym lol"], bandit=["speak up", "what"])
lines("convo.unknown_again", "yeah no idea what youre on about lol", "still lost", "bro im confused", "i really dont follow", "ok lol",
      "anyway", hermit=["...ok", "sure"], newbie=["im so confused lol"], helper=["sorry, i dont follow! what do you need?"], casual=["lol ok", "aight"])
lines("convo.laugh", "lol", "lmao", "haha", "ikr", "xd", "glad someone laughed", "lmaoo",
      hermit=["heh"], newbie=["hehe", "lol!!"], pvp_sweat=["lol", "lmao fr"], helper=["haha :)"])
lines("convo.nice", "ty", "ikr", "right?", "yeah its pretty good", ":)", "thanks lol",
      hermit=["mm", "yeah"], newbie=["ty!!", "i know right!!"], pvp_sweat=["ik", "obviously"], builder=["ty, took ages"], helper=["aw ty!"])
lines("convo.wow", "fr", "yep", "for real", "deadass", "i know", "yeah lol", "100%",
      hermit=["yes"], newbie=["yes really!!", "i know right"], pvp_sweat=["fr fr", "no cap"], casual=["fr lol", "yeah fr"])
lines("convo.agree", "right??", "exactly", "fr", "ikr", "yep", "glad its not just me", "same energy",
      hermit=["mm"], newbie=["yes!!", "same!!"], casual=["fr fr", "real"], helper=["right? :)"])
lines("convo.doubt", "its true tho", "believe what you want", "why would i lie lol", "swear", "ok dont believe me then", "saw it myself",
      hermit=["believe what you like"], pvp_sweat=["cope", "no cap"], newbie=["its true!!", "i promise!!"], bandit=["suit yourself"])
lines("convo.sympathy", "yeah...", "its fine", "it happens", "ty", "ill live", "could be worse",
      hermit=["mm"], newbie=["i know :(", "ty :("], pvp_sweat=["whatever", "its nothing"], helper=["thanks, its ok!"])
lines("convo.pushback", "what, its true", "ok fine its pretty good", "i said what i said", "lol what did you expect", "fine, its great. happy?",
      "im just being honest", hermit=["yes. thats it"], pvp_sweat=["what did you want, a speech"], newbie=["ok ok its really good!!"], casual=["lol ok its good"])
lines("convo.gl", "ty you too", "you too", "always", "thanks, stay safe", "ty o7", hermit=["mm. you too"], newbie=["ty!! you too!!"], pvp_sweat=["dont need luck", "ty"])
lines("convo.brb", "kk", "ok", "np", "ill be here", "take your time", hermit=["ok"], speedrunner=["k hurry"], helper=["no rush!"])
lines("convo.nvm", "ok lol", "kk", "ok np", "alright", "if you say so", hermit=["ok"], newbie=["ok!!"], casual=["lol ok"])
lines("convo.you_there", "ya im here", "yeah sorry, what", "im here im here", "yep whats up", "sorry, was busy. what",
      hermit=["yes. what"], newbie=["yes sorry!!", "im here!!"], helper=["im here! sorry, whats up?"], pvp_sweat=["yeah what"])
lines("convo.ack", "yep", "mhm", "yeah", ":)", "so yeah", "anyway", "cool", hermit=["mm"], newbie=["yeah!!"], casual=["yep lol", "ye"])
lines("convo.wdym", "i mean, {last}", "i said {last}", "like i said, {last}", "just what i said lol", "exactly what it sounds like",
      "what part", hermit=["what i said."], newbie=["um, {last}", "i mean what i said i think"], pvp_sweat=["i said {last}. keep up"])
lines("convo.wdym_nothing", "i didnt say anything lol", "wdym wdym", "what", "? i didnt say anything", hermit=["?"])
lines("convo.talk_to_me", "sure, about what", "ok, whats up", "im listening", "go on then", "what do you wanna talk about",
      hermit=["...about what"], newbie=["ok!! what about"], helper=["of course! whats on your mind?"], pvp_sweat=["talk then"])
lines("convo.not_you", "oh. mb", "oh ok", "my bad", "oh lol sorry", "ah, carry on", hermit=["good"], newbie=["oh sorry!!"], pvp_sweat=["k"], bandit=["hm."])
lines("convo.wait_up", "ok ok im here", "sorry, im listening", "mb, whats up", "alright im not going anywhere", "ok, staying",
      hermit=["fine. what"], speedrunner=["ok but quick"], helper=["sorry! im here, go ahead"], newbie=["sorry!! im here"])
lines("convo.stay", "ok ill wait", "sure, waiting", "ok, not moving", "kk ill be right here", "alright, dont be long",
      hermit=["fine"], speedrunner=["ok. hurry"], helper=["sure! ill wait here"], newbie=["ok!! waiting"])
lines("convo.asked.middle", "lol fair", "ill take that as a maybe", "ok, fair enough", "kinda counts", "ha ok",
      hermit=["hm. ok"], pvp_sweat=["thats a no then lol", "weak answer"], helper=["thats ok!"], newbie=["oh ok!!"])
# ----- its things -----
lines("convo.have.what", "have what", "got what?", "what are you after", "what do you need")
lines("convo.have.guarded", "why. what do you want with it", "maybe. why", "none of your business", "whos asking")
lines("convo.have.yes", "yeah, {count} {item}", "yep got {count} {item}", "ya i have {count} {item}", "{count} {item}, why?", "i do, yeah",
      hermit=["yes."], newbie=["yes!! i have {count} {item}"], hoarder=["maybe. {count} {item}. theyre mine tho"], helper=["yep! {count} {item}, need some?"])
lines("convo.have.no", "nah no {item}", "no {item} on me", "nope, none", "i wish. no {item}", "none sorry",
      hermit=["no."], newbie=["no :( i need {item} too"], helper=["no sorry! none on me"], pvp_sweat=["nah"])
lines("convo.gear.look", "ty lol", "thanks, i try", "ty :)", "ha thanks", hermit=["hm. ty"], newbie=["ty!! yours too"], pvp_sweat=["ik", "obviously"],
      builder=["ty, yours could use work"], bandit=["flattery. noted"])
lines("convo.gear.none", "what {what} lol", "i dont have a {what}?", "uh, im not wearing a {what}", "{what}? where", "you good? no {what} here",
      hermit=["what {what}"], newbie=["i dont have a {what}?? do i"], pvp_sweat=["what {what} bro"], casual=["lol what {what}"])
lines("convo.gear.thanks", "ty, its just a {item}", "thanks, good old {item}", "ty lol, {item}", "ha thanks, does the job", "ty, took a while to get",
      hermit=["ty."], newbie=["ty!! i made it myself"], pvp_sweat=["ik. {item}", "it hits hard too"], hoarder=["ty. dont touch it"],
      builder=["ty, it matches the house"], miner=["ty, mined the stuff for it myself"])
lines("convo.gear.enchanted", "ty, its enchanted too", "thanks, the {item}'s enchanted btw", "ty, cost me a lot of levels", "ha ty, pride and joy",
      pvp_sweat=["ik. enchanted too", "yeah its cracked"], newbie=["ty!! its enchanted!! look"], hoarder=["ty. it stays with me"])
lines("convo.your_house.none", "nah, i dont have a place yet", "not mine, no house yet", "no, im between houses lol", "nope. still looking for a spot",
      nomad=["no house. i move around", "nah, never in one place long"], hermit=["no."], newbie=["no i dont have a house yet :("])
lines("convo.your_house.here", "yep thats mine", "ya this is my place", "yeah, mine", "mine, built it myself", "yep, home",
      builder=["yes, still not finished though", "mine. ignore the roof"], hoarder=["mine. dont go in", "yes and the chests are counted"],
      hermit=["mine. yes"], newbie=["yes!! do you like it"], homebody=["yes! come in sometime :)"])
lines("convo.your_house.elsewhere", "nah mine's {way} of here", "not mine, i live {way}", "no, my place is {way}", "nope, im over {way}",
      hermit=["no."], hoarder=["no. and im not saying where mine is"])
lines("convo.nice_house.none", "i dont have one lol", "what house", "wish it was mine", "not mine but ty i guess", nomad=["no house, just the road"])
lines("convo.nice_house.here", "ty! took ages", "thanks, built it myself", "ty, still working on it", "aw thanks", "ty :) come by anytime",
      builder=["ty!! the windows took forever", "thanks, the roof still bugs me"], hermit=["ty."], hoarder=["ty. dont go inside"], newbie=["ty!!! i worked so hard on it"],
      pvp_sweat=["ty. its just a box with a bed", "it does the job"], homebody=["thank you! its cozy inside too"])
lines("convo.nice_house.elsewhere", "thats not mine lol, mine's {way}", "not mine. but ty", "wrong house, im {way} of here", "ha, not mine")
lines("convo.show_house.none", "id love to but i dont have one", "no house to show yet", "nothing to show, no place yet", "ask me when ive built one",
      nomad=["no house. i can show you the road"])
lines("convo.show_house.yes", "sure, follow me", "ok come on", "yeah this way", "alright, its not far", "ok but wipe your feet",
      builder=["yes!! finally someone asks", "ok but its not finished"], hermit=["...fine. follow"], helper=["of course! this way"], newbie=["yes!! come come"],
      homebody=["oh yes, come see!"])
lines("convo.show_house.no", "nah, i barely know you", "maybe another time", "not today", "id rather not", hoarder=["no. nice try"], hermit=["no."], bandit=["heh. no"])
# ----- its day -----
lines("convo.today.close_call", "nearly died earlier. {close_call}", "had a close one, {close_call}", "almost lost everything. {close_call}",
      "scary day ngl, {close_call}", newbie=["i almost DIED. {close_call}"], pvp_sweat=["nearly died. {close_call}. lag"])
lines("convo.today.ore", "found some {ore}", "mined {ore} today", "good day, got {ore}", "came back with {ore}", "{ore}. finally",
      miner=["{ore}!! and more where that came from", "got {ore}. the grind pays"], newbie=["I FOUND {ore}!!"], hoarder=["found {ore}. not telling where"])
lines("convo.today.built", "finished {built}", "built {built}", "been working on {built}", "put up {built} earlier",
      builder=["finally finished {built}. come see", "{built}. redid it twice"], newbie=["i built {built}!!"])
lines("convo.today.mob", "killed some {mob}s", "been fighting {mob}s", "took out a few {mob}s", "{mob}s mostly. so many",
      pvp_sweat=["farmed {mob}s. ez", "dropped a few {mob}s"], newbie=["i killed a {mob}!!"])
lines("convo.today.traded_with", "did a trade with {traded_with}", "traded with {traded_with} earlier", "bit of trading, {traded_with} mostly")
lines("convo.today.doing", "just trying to {doing}", "same as now, need to {doing}", "busy, got to {doing}", "bit of everything. now its {doing}",
      hermit=["work."], speedrunner=["{doing}. on a schedule"])
lines("convo.today.nothing", "not much tbh", "nothing special", "quiet day", "the usual", "just vibing honestly", "slow day",
      hermit=["nothing."], newbie=["i got lost for a while lol"], speedrunner=["not enough"], casual=["nothing lol", "vibed"])
# ----- its mood -----
lines("convo.mood.hurt", "im hurt, thats what", "low health rn", "got hit pretty bad", "need to heal up", newbie=["im on like 2 hearts!!"], pvp_sweat=["im low. its fine"])
lines("convo.mood.hungry", "just hungry", "starving ngl", "need food bad", "havent eaten", newbie=["im SO hungry"], hermit=["hungry."])
lines("convo.mood.bored", "just bored", "nothing to do", "bored out of my mind", "im fine, just bored", pvp_sweat=["bored. nobody to fight"])
lines("convo.mood.scared", "bit on edge", "something spooked me", "its been a scary one", "nervous, mobs everywhere", newbie=["im scared ok"], hermit=["...its nothing"])
lines("convo.mood.angry", "bad day", "just annoyed", "dont ask", "things keep going wrong", "im mad, give me a minute",
      pvp_sweat=["tilted", "lost a fight. dont ask"], hermit=["leave it"])
lines("convo.mood.sad", "lost a friend", "not a great day", "just sad. it'll pass", "someone i knew died", "rough one", hermit=["...dont want to talk about it"],
      helper=["im ok. just missing someone"])
lines("convo.mood.great", "nothing, im great actually", "im having a good day!", "all good, things went well", "best day in a while",
      pvp_sweat=["im up. everything's a W today"], newbie=["im so happy today!!"])
lines("convo.mood.fine", "nothing, im fine", "im good lol", "all good here", "nothings wrong", "im chill", hermit=["nothing."], newbie=["im ok!!"], pvp_sweat=["im good"])
# ----- people -----
lines("convo.who.unknown", "no idea who that is", "never heard of them", "who?", "dont know them", "doesnt ring a bell", hermit=["no."], newbie=["who's that??"])
lines("convo.who.heard_of", "{who}? seen them around, dont really know them", "ive heard the name", "{who}, yeah, never talked to them", "know of them. thats about it")
lines("convo.who.friend", "{who}? yeah thats my friend", "{who}'s great", "love {who}", "{who} is a good one", "we go way back, me and {who}",
      hermit=["{who}. one of the few i like"], pvp_sweat=["{who}'s my duo"], newbie=["{who} is my best friend!!"])
lines("convo.who.dislike", "{who}? ugh", "not a fan of {who}", "{who} and i dont get along", "i'd stay away from {who}", "dont get me started on {who}",
      hermit=["dont like {who}."], pvp_sweat=["{who}'s trash"], bandit=["{who} owes me"])
lines("convo.who.known", "{who}? yeah i know them", "{who}'s alright", "i know {who}, theyre ok", "seen {who} around a lot", "{who}, yeah. decent")
# ----- grudges -----
lines("convo.grudge.hit", "you hit me. remember?", "maybe because you attacked me", "you came at me first", "i dont forget getting hit",
      hermit=["you hit me."], pvp_sweat=["you swung first"], newbie=["you HIT me!!"], helper=["you hurt me. that wasnt ok"])
lines("convo.grudge.theft", "you were in my chests", "my stuff went missing and you were there", "you took my things", "i dont like thieves",
      hoarder=["you TOUCHED my chests", "i counted. things are missing"], hermit=["you stole."], newbie=["you took my stuff!!"])
lines("convo.grudge.animals", "you killed my animals", "you know what you did to my animals", "my animals. thats why", "ask the cow. oh wait",
      homebody=["you hurt my animals. how could you"])
lines("convo.grudge.grief", "you wrecked my place", "you broke my stuff", "look what you did to my house", "you messed with my base",
      builder=["you broke my build. that took days"])
lines("convo.grudge.friend", "you killed my friend", "you know who you killed", "my friend is dead because of you", "i saw what you did to them")
lines("convo.grudge.other", "you know what you did", "think about it", "you really dont remember?", "after what you did?")
lines("convo.grudge.insult", "you called me {insult}", "maybe dont call people {insult}", "{insult}, remember?", "i heard what you said",
      pvp_sweat=["you called me {insult}. 1v1 then"], newbie=["you called me {insult} :("])
lines("convo.grudge.threat", "you said youd kill me??", "you threatened me", "you literally threatened me earlier", "you dont get to say that and act normal")
lines("convo.grudge.none_like", "what? i like you fine", "i do like you lol", "were good, why", "im not mad at you", "nothing, youre alright",
      hermit=["i dont dislike you."], newbie=["i like you!!"], pvp_sweat=["were chill"])
lines("convo.grudge.none", "i just dont know you yet", "nothing, we just met", "i barely know you, thats all", "nothing personal, i dont know you",
      hermit=["i dont like anyone much"], bandit=["nothing. yet"])
lines("convo.friends.already", "we already are lol", "bro we're friends", "thought we were", "obviously", "been friends for a while now",
      hermit=["we are. dont make it weird"], newbie=["we are!! best friends"], pvp_sweat=["we are. still 1v1 you tho"])
lines("convo.friends.yes", "sure lol", "yeah why not", "ok, friends", "i'm down", "sure, youre alright",
      hermit=["...ok"], newbie=["YES!!", "omg yes"], pvp_sweat=["sure. dont get soft"], helper=["of course! :)"], bandit=["friends. sure. for now"])
lines("convo.friends.soon", "we just met lol", "maybe, give it time", "lets see how it goes", "slow down, i dont know you yet", "ask me again in a few days",
      hermit=["no."], newbie=["ok!! wait do i know you"], helper=["id like that! lets get to know each other first"], bandit=["i dont do friends"])
# ----- food, danger, news -----
lines("convo.food.offer", "i got some spare, want it?", "i can give you some. want some?", "got extra food, you want it?", "hungry? i can share",
      helper=["oh i have spare! want some?", "here, i can give you some. want it?"], hoarder=["...i have a little spare. want it?"], hermit=["i have some. want it?"])
lines("convo.food.give", "here", "here you go", "take it", "eat up", "there, dont starve", helper=["here you go! eat up"], hermit=["here."])
lines("convo.food.declined", "ok", "suit yourself", "ok, more for me", "np", hermit=["ok."])
lines("convo.food.where", "hunt some cows or sheep, or grow wheat", "kill a few animals, cook it", "the stall sells food if theres one near",
      "apples from oak trees if youre desperate", "fish. always fish", newbie=["idk i just eat whatever i find"], miner=["i just bring bread down the mine"],
      homebody=["a little wheat farm, best thing you can do"])
lines("convo.danger.told", "{who} said its bad {way} of here", "stay away from {way}, {who} warned me", "heard from {who} theres trouble {way}",
      "apparently its dangerous {way}. {who} told me")
lines("convo.danger.close_call", "careful, {close_call} earlier", "i nearly died today. {close_call}", "its not that safe, {close_call}",
      "watch yourself. {close_call}")
lines("convo.danger.night", "its night, so no", "not at night its not", "mobs are out, stay close to light", "nowhere's safe at night lol",
      newbie=["no!! its night!!"], pvp_sweat=["nights fine if you can fight"])
lines("convo.danger.none", "seems fine round here", "its been quiet", "safe enough in the day", "nothing ive seen", "should be ok",
      hermit=["safe enough."], newbie=["i think so?? i hope"], pvp_sweat=["nothing i cant handle"])
lines("convo.news.yes", "did you hear? {news_who} {news_what}", "apparently {news_who} {news_what}", "well, {news_who} {news_what}",
      "heard {news_who} {news_what}", "big one: {news_who} {news_what}", hermit=["{news_who} {news_what}. thats all i know"],
      newbie=["omg yes, {news_who} {news_what}"])
lines("convo.news.none", "nothing much", "quiet lately", "not that ive heard", "same old", "nothing new tbh", "nope, slow week",
      hermit=["no."], newbie=["i dont think so??"], casual=["nah nothing lol"])
lines("convo.who_died.none", "nobody that i know of", "not that ive heard", "everyone's alive as far as i know", "nobody lately")
# ----- how they are -----
lines("convo.im_hurt.offer", "you look rough. want some food?", "eat something. want some of mine?", "i got food, want it?", helper=["oh no! here, want some food?"])
lines("convo.im_hurt", "eat something, quick", "get somewhere safe and eat", "ouch. stay back from the mobs", "food heals, eat up",
      pvp_sweat=["eat and get back in", "skill issue. eat"], newbie=["oh no!! eat something!!"], hermit=["eat."])
lines("convo.im_lost.base", "your base is {way} of here, about {dist} blocks", "you live {way}, like {dist} blocks", "head {way}, {dist} blocks or so",
      "thats {way} from here. {dist} blocks ish")
lines("convo.im_lost.mine", "no idea where yours is. my place is {way} if you need somewhere", "dont know your base sorry. im {way} of here if that helps")
lines("convo.im_lost.none", "no idea where you live sorry", "i dont know where your base is", "cant help, never seen your place",
      "follow a river maybe? idk", helper=["oh no. i dont know where you live, but you can stay near here"], newbie=["i get lost all the time too"])
lines("convo.im_scared.brave", "youre fine, stick with me", "its ok, ill watch your back", "nothing to be scared of. mostly", "stay close, its fine",
      pvp_sweat=["relax, i got this", "nothing here can touch me"], helper=["its ok! stay close to me"])
lines("convo.im_scared.same", "same honestly", "me too", "yeah i dont like it either", "lets not stay long", newbie=["ME TOO", "im scared too!!"], hermit=["then leave."])
lines("convo.danger_tell.ok", "noted, ty", "ok, ill stay clear", "good to know. thanks", "ty for the warning", "yikes, ok",
      hermit=["noted."], newbie=["oh no!! ok i wont go there"], pvp_sweat=["we'll see about that", "sounds like loot to me"], helper=["thank you! ill tell the others"],
      miner=["hm. that deep? ok, careful then"])
lines("convo.danger_tell.doubt", "sure it is", "and why should i believe you", "ill see for myself", "hm. maybe")
# ----- doing things -----
lines("convo.look", "oh nice", "huh, look at that", "what am i looking at", "oh yeah", "cool", "i see it", "ok and?",
      builder=["hm. needs more detail", "ooh nice"], hermit=["...ok"], newbie=["woah", "oooh"], pvp_sweat=["ok. and?"], helper=["oh thats nice!"])
lines("convo.outing.no_trust", "i barely know you", "maybe when i know you better", "nah, not with a stranger", "not today",
      hermit=["no."], bandit=["i work alone"], helper=["maybe once we know each other a bit!"])
lines("convo.outing.hurt", "cant, im hurt", "not like this, need to heal first", "im too low rn", "let me heal up first")
lines("convo.outing.no_pick", "id love to but i got no pickaxe", "no pick on me", "cant, need a pickaxe first", "with what? no pick lol")
lines("convo.outing.no_weapon", "with what? i got no sword", "cant, no weapon", "need a sword first", "not unarmed lol")
lines("convo.outing.scared", "at night?? no thanks", "nope. not going out there", "im not brave enough for that", "ill pass, too many mobs",
      newbie=["no way its scary out there"])
lines("convo.outing.mine_yes", "ok lets go", "sure, lead the way", "im down, lets mine", "yeah ok, right behind you", "lets get some ore",
      miner=["finally. lets go", "say no more"], hermit=["...ok"], newbie=["yes!! lets go!!"], pvp_sweat=["ok. dibs on diamonds"], speedrunner=["ok fast. go"])
lines("convo.outing.hunt_yes", "ok lets go", "sure, im in", "lets get em", "right behind you", "ok, stay close",
      pvp_sweat=["finally. lets go", "ez. lead the way"], hermit=["...fine"], newbie=["ok!! dont leave me behind"], helper=["ok! ill watch your back"],
      bandit=["i like the sound of that"])
lines("convo.offer.fight", "one sec!! toss it", "busy!! throw it here", "ty, toss it, bit busy", "after this zombie!!")
lines("convo.offer.yes", "oh, for me? ty", "ooh ok, coming", "sure, ty!", "wait really? ok", "ill take it",
      hermit=["...ok. ty"], newbie=["for me?? ty!!"], hoarder=["yes. always yes"], pvp_sweat=["ok ty"], helper=["aw you didnt have to!"])
lines("convo.come_in.yes", "sure, come in", "yeah go ahead", "doors open", "come on in", "ok, wipe your feet",
      hermit=["...fine. dont touch anything"], homebody=["of course! come in :)"], helper=["yes yes come in!"], newbie=["yes!! come see"])
lines("convo.come_in.night", "its late. tomorrow?", "not at night, sorry", "come back in the morning", "im in bed, another time")
lines("convo.come_in.no", "id rather you didnt", "no, sorry", "not today", "we dont know each other like that",
      hoarder=["no. my chests are in here", "absolutely not"], hermit=["no."], bandit=["try it and see"])
lines("convo.sleep_here.yes", "sure, theres room", "yeah you can stay", "ok, just tonight", "of course, come in",
      homebody=["of course! ill get the spare bed"], hermit=["...one night"], helper=["yes! stay as long as you need"])
lines("convo.sleep_here.no", "no spare bed, sorry", "not really, sorry", "id rather not", "theres no room", "try the town?",
      hermit=["no."], hoarder=["no. nothing personal"], helper=["i wish i had room! sorry"])
lines("convo.wake_up.awake", "im awake lol", "i am up", "already up", "?? im right here", hermit=["i am awake."])
lines("convo.warning.real", "WOAH. ty", "oh. thanks", "good looking out", "saw it, ty!!", "yikes, thanks",
      newbie=["AAAH ty", "omg ty!!"], pvp_sweat=["i see it", "got it"], hermit=["...ty"], helper=["thank you!!"])
lines("convo.warning.false", "where??", "what? theres nothing", "dont scare me like that", "nothing there lol", "i dont see anything",
      newbie=["WHERE", "dont do that!!"], pvp_sweat=["nothing there. nice try", "lol no"], hermit=["nothing."])
# ----- threats, sorry, jk, truce -----
lines("convo.threat.brave", "try it", "lol ok. come on then", "youre welcome to try", "big talk", "id like to see that",
      pvp_sweat=["1v1 me then", "say that with your sword out", "L threat"], bandit=["get in line", "you? heh"])
lines("convo.threat.scared", "what?? why", "pls dont", "what did i do", "leave me alone", "im going",
      newbie=["WHAT why", "please dont!!"], homebody=["why would you say that", "please just go"], helper=["hey, lets not do this"])
lines("convo.threat.wary", "?? bro chill", "ok weird thing to say", "uh. ok", "not funny", "dont say that", "ill remember that",
      casual=["bro what", "?? chill"], hermit=["...leave."], hoarder=["stay away from me and my stuff"])
lines("convo.threat_all.brave", "you and what army", "lol try it, the guards will love you", "everyone? bold", "come on then",
      pvp_sweat=["all of us? ok. come on", "you couldnt take one of us"], bandit=["ha. ambitious"])
lines("convo.threat_all.scared", "what?? why would you do that", "please dont", "we didnt do anything to you", "someone get the guards",
      newbie=["WHAT. guards!!", "why would you say that"], homebody=["we have families here. sort of"], helper=["hey, nobody needs to get hurt"])
lines("convo.threat_all.wary", "?? bro chill", "ok thats not funny", "say that again and the guards hear it", "who even are you", "uh huh. sure",
      casual=["?? what is wrong with you", "bro chill"], hermit=["then leave town."], hoarder=["try it and see what happens"])
lines("convo.jk.what", "about what", "ok lol", "?", "i know", "didnt think you were serious")
lines("convo.jk.ok", "not funny but ok", "ok. dont do that", "phew. bad joke", "lol ok. warn me next time", "you had me for a sec",
      hermit=["...not funny"], newbie=["ok good!! you scared me"], pvp_sweat=["thought so", "weak"], helper=["ok! dont scare me like that"])
lines("convo.jk.again", "you keep saying that", "sure you are", "jokes getting old", "uh huh")
lines("convo.sorry.slowly", "...ok. its going to take more than that", "i hear you. give it time", "sorry's a start", "we'll see")
lines("convo.sorry.not_enough", "sorry doesnt fix it", "bit late for sorry", "no", "you think thats enough?", "save it",
      hermit=["no."], hoarder=["give it back then"], pvp_sweat=["too late"], bandit=["sorry dont pay"])
lines("convo.sorry.forgiven", "its fine, just watch it", "ok, were good", "all good, dont do it again", "apology accepted", "its ok",
      hermit=["...fine"], newbie=["its ok!!"], pvp_sweat=["k. were good", "whatever, its fine"], helper=["its ok! no harm done"])
lines("convo.sorry.again", "yeah you said", "its fine, really", "ok ok", "stop apologising lol")
lines("convo.sorry.nothing", "for what lol", "sorry for what", "?? youre fine", "all good, nothing to be sorry for", "you didnt do anything",
      hermit=["for what."], newbie=["for what??"], helper=["no need to be sorry!"])
lines("convo.truce.no_fight", "we're not fighting lol", "?? im not attacking you", "relax, were fine", "peace? we're already at peace",
      hermit=["i wasnt going to."], newbie=["i wasnt gonna hurt you!!"])
lines("convo.truce.no", "no", "too late for that", "should have thought of that earlier", "not a chance",
      bandit=["pay up first", "truce costs extra"], pvp_sweat=["nah. finish it"])
lines("convo.truce.maybe", "...fine. back off then", "ok. dont try anything", "walk away and were done", "alright. truce. for now",
      hermit=["go. now."], helper=["ok. lets just stop"], newbie=["ok ok truce!!"])
# ----- why -----
lines("convo.why.pvp", "wanna see who wins lol", "because a 1v1 would be fun", "just wanna fight someone decent", "bored. duels are fun",
      pvp_sweat=["cuz id win", "need someone to clap"])
lines("convo.why.nothing", "why what", "why what lol", "?", "why what, i didnt say anything")
lines("convo.why.dunno", "just because", "no reason", "idk it just is", "because lol", "thats just how it is", "just curious",
      hermit=["because."], newbie=["idk!!"], pvp_sweat=["cuz i said so"])
lines("convo.why.insult", "you called me {insult} earlier", "because you called me {insult}", "{insult}. remember?", "after what you said? no")
lines("convo.why.hurt", "im hurt, look at me", "cuz im on low health", "need to heal first", "barely alive rn")
lines("convo.why.scared", "because its scary out there", "too many mobs", "im not that brave ok", "ive died enough this week")
lines("convo.why.gear", "no gear for it", "i dont have the tools", "need better stuff first", "with these hands?")
lines("convo.why.trust", "i barely know you", "we just met", "dont know you well enough yet", "cuz youre a stranger lol", "ask me when we know each other",
      hermit=["i dont know you."], bandit=["i dont trust anyone"], helper=["nothing personal! we just met"])
lines("convo.why.night", "its night", "cuz its late", "im going to bed soon", "not at night", "ask me tomorrow")
lines("convo.why.busy", "im busy, got to {doing}", "cuz i need to {doing}", "busy rn. {doing}", "after i {doing} maybe", hermit=["busy."], speedrunner=["no time. {doing}"])
lines("convo.why.mood", "not feeling it", "just dont want to rn", "not in the mood", "maybe later", hermit=["dont want to."])

COMMENT = ("Holding a conversation with a player (docs/PLAYER_TALK.md, batch 1; social/talk/PlayerConvo.java). 'answers': the phrases "
           "players type for each new meaning (matched as in answers.json: the longest phrase found wins). 'lines': what a Drifter says back, "
           "by voice. {player} is what it calls them, {name} itself; some kinds fill more: {item} {count} {what} (its things), {way} {dist} "
           "(directions), {who} (someone), {last} (its last line), {insult}, {doing}, and what it did lately ({ore} {built} {mob} "
           "{close_call} {traded_with} {news_who} {news_what}). Started by tools/chat_more/convo_seed.py; more lines added with merge.py.")

if __name__ == '__main__':
    root = sys.argv[1]
    path = os.path.join(root, 'src/main/resources/data/drifter/drifter_chat/convo.json')
    out = json.dumps({"_comment": COMMENT, "answers": ANSWERS, "lines": L}, indent=1, ensure_ascii=False)
    open(path, 'w', encoding='utf-8', newline='\r\n').write(out + '\n')
    print(len(ANSWERS), 'meanings,', sum(len(v) for v in ANSWERS.values()), 'phrases;', len(L), 'kinds,',
          sum(len(x) for v in L.values() for x in v.values()), 'lines')
