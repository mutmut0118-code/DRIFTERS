# Drifter Whims

Companion file to `docs/DESIGN.md`. Sessions only need to read this file when working on the whim system.

Real players don't only optimize. They bounce on slime blocks, build dumb statues, rampage through villages and stare at sunsets. This file catalogs about 900 random activities a vanilla 1.21.1 player can do, plus modded whims for popular RPG modpack mods (section 30), so Drifters can do them too. In the design these are **whims**: low-priority goals the selector (DESIGN.md section 7) picks when urgent needs are met, driven mostly by Boredom and Curiosity.

## How whims work

- **Each whim is a data entry** with: prerequisites (items, stage, a nearby block or mob), a trigger (bored, triumphant, near a player, at a structure), trait weights (which personalities love it), a duration, and gates.
- **Gates:**
    - **[G]** griefing or harm to others: needs hostility plus the matching griefing config.
    - **[M]** multiplayer: only with a player or another Drifter present.
    - **[R]** risky: Cautious Drifters rarely pick it.
    - **[L]** later version: complex enough to leave out of v1.
- **Cooldowns** stop the same whim from repeating, and a whim never runs at night outdoors unless the Drifter is brave.
- **Whims are where personality shows most,** so weight them heavily by archetype: Builders build silly things, Newbies try everything once, Bandits pick the [G] ones. Section 29 maps archetypes to categories.
- **Templates:** many entries are one pattern with different fill-ins ("collect every [flower / wool color / disc]", "tame a [wolf / cat / parrot]"). Implement those as templates with fill-in lists rather than one entry each.

## 1. Movement and physics play

- Bouncing on slime blocks; building a slime block trampoline
- Bouncing on beds (beds bounce players a little)
- Jumping off a cliff into a 1-block-deep water pool
- Water bucket clutch from high up, just to show off [R]
- Jumping from build height into deep water [R]
- Landing on hay bales to see how much fall damage they save
- Ice boating on packed or blue ice roads
- Pushing a boat across land in tiny hops
- Riding a pig with a carrot on a stick
- Riding a strider across lava with a warped fungus on a stick
- Horse jumping over fences; horse racing
- Riding a camel, two to a camel [M]
- Leading a llama caravan
- Minecart rides; building a rollercoaster [L]
- Elytra flying through ring courses; firework-boosted flights
- Riptide trident launching in the rain
- Wind charge jumping (1.21)
- Mace smash attacks from height (1.21)
- Sliding down honey block walls
- Building scaffolding towers and dropping through them
- Ladder and vine climbing races [M]
- Parkour courses, including tricky neo jumps around corners
- Sprint-jump races [M]
- Soul sand and magma bubble column elevators
- Ender pearl travel spam
- Ender pearl stasis chamber [L]
- Getting launched by levitation from a shulker
- Slow falling jumps from mountains
- Big dripleaf parkour in lush caves
- Crawling through 1-block gaps under trapdoors
- Ice speed running (sprint-jumping on ice under a low ceiling)
- Climbing twisting vines in the Nether
- Swimming through dolphin pods to get Dolphin's Grace
- Sitting in a parked boat or minecart as a chair
- Jump-spamming in water to bob
- Sinking into powder snow on purpose

## 2. Silly and personal builds

- The classic dirt hut
- A 1x1 pillar tower just to look around, left standing forever
- A crude joke build out of dirt (the infamous phallic one) [G if near a player's base]
- A giant statue of itself
- Its own name in giant letters on a hillside
- Pixel art from wool or concrete
- A sign that says "[name] was here"
- Funny or rude signs on someone's house [G]
- A grave with a sign for a dead pet
- A shrine to a dead pet or a lost diamond pickaxe
- A tiny house for its horse
- A doghouse and a cat tower
- A snow golem army
- Building an iron golem to guard its house
- A skybridge between two mountains
- A bridge across the ocean
- A deliberately ugly house
- A castle with a moat
- A treehouse in a jungle or dark oak
- An underwater glass dome base [L]
- Digging a giant hole straight to bedrock (the "why not" project) [R]
- An obsidian cube for no reason
- A trophy room of mob heads and rare items
- A museum of item frames, one of every item it has
- A map wall
- Armor stand mannequins wearing every armor set
- Custom banner designs
- A painting gallery
- A flower garden with every flower
- An aquarium of tropical fish and axolotls
- A zoo of captured mobs
- A pet zombie kept in a glass cage with a name tag
- A villager in a hole (the classic trading setup)
- A bee sanctuary
- Growing a giant mushroom or a 2x2 mega spruce with bone meal
- A pumpkin patch with carved faces
- A bonfire or campfire camping spot
- A rainbow made of wool
- A floating island
- A mob fight arena
- A spleef arena [M]
- A hedge maze
- A secret base behind a waterfall
- A hidden door with pistons [L]
- A disco floor of redstone lamps and a jukebox [L]
- A lighthouse on the coast
- A fishing dock
- A lookout tower on the highest point
- A Herobrine shrine (gold blocks, netherrack and fire) to spook players
- A Christmas tree or Halloween pumpkin display in season

## 3. Destruction and chaos

Every item here is gated [G] when it touches players, their builds or villages they live near. Drifter-on-Drifter and wilderness versions are allowed by default.

- Village rampage: killing villagers, then fighting the iron golem
- Burning down a village
- Breaking every bed in a village
- Stealing every hay bale and crop from a village
- Trampling a village farm
- Killing the wandering trader and his llamas
- Lighting a forest fire with flint and steel
- Accidentally burning down its own house with a fireplace
- Blowing up its own house by mistake with TNT
- Blowing up a mountain with TNT, just because [R]
- Lava casting a mountain or a cobblestone cone [L]
- Flooding someone's base with water
- Filling a room to the ceiling with sand or gravel
- Replacing someone's door with dirt
- Filling someone's chest with dirt
- Putting a cow inside someone's house
- A TNT pressure-plate trap
- Luring a creeper into someone's base
- Spam-placing torches everywhere
- Dyeing every sheep pink
- Shearing every sheep it can find
- Killing every chicken in the area
- Breaking glass panes for the sound
- Staring at an enderman to pick a fight [R]
- Punching a zombified piglin and fleeing the horde [R]
- Punching a wolf and regretting it
- Summoning the Wither somewhere inconvenient [L]
- Killing someone's dog [G, severe]

## 4. Mobs, pets and animals

- Taming wolves, cats, parrots, horses, donkeys, mules and llamas
- Carrying a parrot on its shoulder
- Breeding foxes to get a trusting one
- Keeping axolotls in buckets
- Giving an allay an item to fetch
- Name tag tricks: "Dinnerbone" turns a mob upside down, "jeb_" makes a rainbow sheep, "Toast" gives a special rabbit
- Dyeing a wolf's collar
- Building a wolf army
- Leading animals home on leads and tying them to fences
- Moving mobs in boats or minecarts
- Milking cows and goats
- Getting rammed off a cliff by a goat
- Collecting goat horns and playing them
- Feeding pandas and watching them sneeze and roll
- Making parrots and allays dance to a jukebox
- Bartering with piglins
- Curing a zombie villager with weakness and a golden apple
- Channeling a lightning strike onto a villager (it becomes a witch) [G]
- Channeling lightning onto a creeper to charge it [R]
- Turning a red mooshroom brown with lightning
- Watching a fox steal its items
- Feeding dolphins fish so they lead it to treasure
- Hatching a sniffer egg
- Brushing an armadillo for scutes
- Protecting turtle eggs
- Breeding every animal for the achievement
- Angering bees and running
- Harvesting honey safely with a campfire
- Mixing sheep colors by breeding
- Spotting a chicken jockey and getting chased by it
- Spotting a spider jockey
- Triggering a skeleton horse trap in a thunderstorm
- Trading with villagers for fun items
- Triggering a raid on purpose with an ominous bottle [R]
- Defending a village raid to become Hero of the Village
- Killing a raid captain for the banner
- Racing horses [M]
- Using a mule as a pack animal
- Visiting a witch hut

## 5. Exploration and sightseeing

- Climbing the highest mountain in sight
- Watching the sunset or sunrise from a high point
- Standing on top of a tree to look around
- Visiting every biome
- Finding a mushroom island
- Sneaking through the deep dark to loot an ancient city [R]
- Finding a woodland mansion [L]
- Visiting an ocean monument from a distance
- Exploring shipwrecks
- Following a treasure map to buried treasure
- Buying an explorer map from a cartographer
- Filling in a map by walking around
- Spelunking huge cave systems
- Exploring lush caves and dripstone caves
- Finding amethyst geodes and listening to the chimes
- Following a river all the way to the ocean
- Touring every Nether biome [R]
- Finding a fossil underground
- Finding a desert well
- Brushing suspicious sand and gravel at trail ruins
- Collecting pottery sherds and making decorated pots
- Exploring trial chambers [R]
- Looking at the moon through a spyglass
- Stargazing at night from a safe roof
- Sleeping outside in a tent
- Visiting the edge of the void in the End [L]

## 6. Collecting hobbies

- Every flower type
- Every dye color
- Music discs (including getting a skeleton to shoot a creeper for one) [R]
- Every wool color
- Armor trims and smithing templates
- Mob heads from charged creeper explosions [L]
- Goat horns of every sound
- Banner patterns
- Every sapling and every log type
- A full stack of diamonds, just to have it
- Enchanted books
- A potion of every kind
- Shulker boxes in every color [L]
- Rare fish in buckets
- Every pottery sherd
- Every advancement it can reach

## 7. Farms and contraptions

- Sugar cane, pumpkin and melon farms
- A tree farm
- A cobblestone generator
- A kelp farm and a bamboo farm
- A chicken cooker [L]
- An iron farm with villagers and a zombie [L]
- A mob spawner XP farm [L]
- An AFK fishing spot
- A villager trading hall [L]
- A composter next to the farm
- Building and showing off a working piston door [L]

## 8. Food and cooking

- Baking a cake and placing it for others to eat [M]
- Baking cookies and pumpkin pie
- Experimenting with suspicious stew
- Making rabbit stew (the hardest food to make)
- Crafting golden apples
- A kitchen with a smoker and campfire
- Campfire cooking on a camping trip
- Berry picking in taiga
- Eating glow berries in lush caves
- Drinking honey to cure poison
- Drinking milk to clear effects
- Eating chorus fruit to teleport randomly [L]
- Feeding a parrot a cookie (which kills it): a terrible mistake only Newbies make, once

## 9. Combat for fun

- Duels with friends [M]
- Archery practice on target blocks
- An archery range with targets at distances
- Trident throwing practice
- Fighting mobs in its own arena
- Snowball fights [M]
- Throwing eggs to hatch chickens
- Shooting fireworks from a crossbow
- Shield blocking practice against skeletons
- Testing a new sword on a zombie
- Fighting the Wither [L, R]
- Hunting the Warden [L, R]
- Defending a raid

## 10. Social and multiplayer

- Crouch-spam greetings [M]
- Giving gifts [M]
- Trading items with players [M]
- Building together with a friend [M]
- Sharing a base [M]
- Crouching over a dead player's loot (the taunt) [G, M]
- Following a player around silently at a distance, which is very creepy [M]
- Hiding behind a player and waiting for them to turn around [M]
- Building a statue of a friend [M]
- A housewarming party with cake [M]
- A roleplay wedding with flowers and a cake [M, L]
- A funeral for a pet, with a grave [M]
- Races, hide and seek, tag [M, L]
- Spleef and capture the flag [M, L]
- Betting on mob fights in an arena [M, L]
- Running a chest shop with signs and prices [L]
- Pranks on players: fake chest of dirt, cow in the house, door swapped for dirt [G, M]
- Building a Herobrine shrine near a player's house to spook them [M]

## 11. Music, art and fashion

- Playing note block songs [L]
- Playing music discs in a jukebox
- Ringing bells in villages
- Signaling friends with a goat horn [M]
- Placing paintings until it gets one it likes
- Banner art
- Map art [L]
- Armor trim fashion
- Dyed leather armor outfits
- A fashion show with armor stands
- Wearing a carved pumpkin on its head
- Wearing a mob head

## 12. Weird, random and idle

- Trying to sleep in the Nether (it explodes) [R]
- Sleeping in a village bed
- Standing in the rain doing nothing
- Dying on purpose to clear its inventory [R]
- Swimming in lava with fire resistance just for fun
- Pillaring all the way to build height
- Standing on bedrock at the bottom of the world
- Trying to break bedrock
- Building a portal in an odd shape just to see if it works
- Bucket-casting an obsidian portal
- Dyeing leather armor in a cauldron
- Washing a banner in a cauldron
- Bone mealing a field of grass for flowers
- Spinning items in item frames
- Making a compass to find home and a clock to know the time
- Renaming items with funny names on an anvil
- Writing a diary in a book and quill, then leaving it in a chest
- Leaving lore books in its base for players to find
- A "rules" sign at its base entrance
- Launching fireworks at night for no reason
- A fireworks show
- An ice skating rink
- Burying a treasure chest and leaving a sign clue for players [M]
- Fishing for junk and keeping it
- Watching a sheep eat grass
- Staring at its own reflection in a lake (looking straight down into water)
- Throwing a stack of items into lava by accident
- Trying to pick up every item it ever dropped
- Mining a single diamond and walking home immediately to store it

## 13. Self-made challenges

- Speedrunning to the Nether [R]
- A pacifist day: no kills at all
- A vegetarian week
- A no-armor run [R]
- Living only in one biome
- A nomad week: no base, carry a bed
- Jumping from build height into a water bucket clutch [R]
- Finishing a parkour course it built itself
- Surviving a night in the open with no shelter
- Surviving a night with only a wooden sword
- A week without using a bed
- Living only underground for a week
- Living only on a boat or the water for a week
- Building a base with only one block type
- Reaching diamonds without ever going in a cave
- A Nether trip with no armor [R]
- Killing a creeper with only a fist
- Crossing an ocean with only a boat and no supplies
- Building a 100-block tall tower in one day
- Making a farm feed it for a whole week with no hunting
- A pacifist Nether trip using only bartering
- Speedrunning to full iron armor
- A whole day of only parkour travel

## 14. Blocks and workstations

- Cutting stairs and slabs on a stonecutter instead of crafting them
- Designing banners on a loom, including rare patterns (globe from the wandering trader, snout from bastions, flow and guster from trial chambers)
- Cloning a map on a cartography table
- Zooming a map out to a bigger scale
- Locking a map so it never changes
- Marking a spot on a map by clicking a banner with it
- Adding an armor trim on a smithing table
- Upgrading diamond gear to netherite on a smithing table
- Disenchanting an item on a grindstone for XP
- Repairing two damaged tools together on a grindstone
- Placing a book on a lectern and flipping its pages
- Filling a chiseled bookshelf one book at a time
- Breaking a decorated pot to see what's inside
- Storing an item in a decorated pot
- Setting up a crafter to auto-craft with redstone (1.21)
- Composting extra seeds into bone meal
- Filling a cauldron with water, lava, or powder snow
- Letting rain fill a cauldron
- Letting dripstone drip water or lava into a cauldron
- Building a beacon pyramid and choosing its effects
- Placing a conduit underwater for underwater breathing
- Charging a respawn anchor with glowstone in the Nether
- Binding a compass to a lodestone
- Tuning a note block by clicking it, and changing its instrument with the block underneath
- Making a smoke signal by putting a hay bale under a campfire
- Lighting a soul campfire
- Lighting candles in different colors and groups of up to four
- Putting a candle on a cake and lighting it
- Lighting sea pickles underwater by grouping them
- Hanging lanterns from chains
- Placing end rods as lights
- Installing a lightning rod on its roof
- Waxing copper with honeycomb
- Scraping oxidation off copper with an axe
- Watching copper slowly turn green
- Toggling a copper bulb with a lever (1.21)
- Using copper grates, doors and trapdoors (1.21)
- Flipping trapdoors open and closed
- Opening and closing fence gates
- Stepping on pressure plates
- Pressing buttons and pulling levers
- Tripping its own tripwire
- Shooting a target block to power redstone
- Setting off a sculk sensor by walking past
- Tuning a calibrated sculk sensor
- Watching a sculk catalyst bloom when a mob dies
- Growing amethyst clusters on budding amethyst
- Dyeing glass and glass panes
- Choosing a bed color
- Putting a banner pattern on a shield
- Using glow item frames
- Putting flowers in flower pots
- Editing a sign, writing on both sides, dyeing the text, making it glow with a glow ink sac, then waxing it so nobody can edit it
- Hanging signs from chains
- Opening a vault with a trial key (1.21)
- Opening an ominous vault with an ominous trial key (1.21)
- Crafting a mace from a heavy core and a breeze rod (1.21)
- Dyeing a shulker box
- Shooting arrows out of a dispenser
- Building an observer clock just to watch it tick [L]
- Placing a daylight sensor and flipping it to night mode
- Riding a TNT minecart [R, L]
- Riding a minecart with a furnace on powered rails
- Using detector and activator rails
- Stacking item frames on item frames as decoration
- Bone mealing moss to spread it across a cave floor
- Growing an azalea tree with bone meal
- Collecting glow berries from cave vines
- Placing a spore blossom on a cave ceiling
- Standing on big dripleaf until it tips
- Silk touching coral so it doesn't die
- Watching coral die when placed out of water
- Placing kelp and watching it grow
- Making mud by pouring a water bottle on dirt
- Turning mud into clay with pointed dripstone

## 15. Item uses

- Shooting tipped arrows of every type
- Using spectral arrows to make mobs glow through walls
- Throwing fire charges
- Designing firework stars with colors, fades and shapes
- Throwing splash potions
- Throwing lingering potions to make clouds
- Drinking invisibility to sneak around (armor still shows)
- Throwing ender eyes to find the stronghold
- Carving a pumpkin with shears
- Harvesting honeycomb from a beehive with shears
- Cutting tripwire with shears
- Shearing vines and glow lichen
- Shearing a mooshroom into a normal cow
- Shearing a snow golem's pumpkin off
- Saddling a pig
- Putting horse armor on a horse
- Putting wolf armor on a wolf (made from armadillo scutes)
- Dyeing leather horse armor
- Holding a totem of undying in the offhand
- Using a brush on suspicious sand
- Using a recovery compass to find its last death
- Fishing a mob closer with a fishing rod
- Hooking a player with a fishing rod [M]
- Lighting TNT with a flame bow from a distance [R]
- Catching fish in a bucket
- Catching an axolotl or a tadpole in a bucket
- Scooping powder snow into a bucket
- Using a lava bucket as furnace fuel
- Firing multishot and piercing crossbows
- Throwing a Loyalty trident and watching it come back
- Repairing elytra with phantom membranes
- Eating an enchanted golden apple
- Eating rotten flesh just to survive
- Eating a poisonous potato by mistake
- Eating a pufferfish by mistake
- Brewing with a rabbit's foot for leaping potions
- Wearing a turtle shell helmet for longer underwater breathing
- Wearing a dragon head
- Drinking an ominous bottle (1.21) [R]
- Throwing snowballs at blazes (they take damage)

## 16. Mob by mob

- Getting mushroom stew from a mooshroom with a bowl
- Getting suspicious stew from a brown mooshroom after feeding it a flower
- Breeding a horse and a donkey to get a mule
- Getting spat on by a llama
- Putting a chest on a llama or donkey
- Watching a sniffer dig up torchflower seeds and pitcher pods
- Planting torchflowers and pitcher plants
- Making an armadillo roll up by sprinting near it
- Collecting turtle scutes from baby turtles growing up
- Feeding a frog small magma cubes to get froglights
- Raising frogs in different biomes to get all three colors
- Watching an axolotl play dead
- Fighting alongside axolotls underwater
- Getting ink sacs from squid
- Getting glow ink sacs from glow squid
- Watching bats hang in caves
- Watching a fox sleep in the day
- Watching a fox pounce on chickens in snow
- Getting a parrot to imitate a mob sound
- Finding every panda personality (lazy, worried, playful, aggressive, weak)
- Breeding a rare brown panda
- Avoiding a polar bear with cubs
- Collecting goat horns when goats ram stone or logs
- Breeding hoglins in the Nether for food
- Watching piglins dance after killing a hoglin
- Watching piglins admire gold
- Collecting ghast tears
- Watching an enderman carry a block away
- Spawning an endermite by throwing pearls
- Collecting shulker shells
- Duplicating an allay by giving it an amethyst shard while it dances
- Assigning villagers jobs by placing workstations
- Watching villagers gather around the bell when it rings
- Watching villagers sleep at night
- Finding the nitwit (the villager with no job)
- Watching baby villagers play tag
- Watching an iron golem hand a poppy to a baby villager
- Following a snow golem's snow trail
- Naming a vindicator "Johnny" so it attacks everything
- Spotting illagers riding ravagers in a raid
- Watching zombies break doors on Hard
- Fleeing husks in a desert at night
- Dodging a drowned's trident
- Seeing a stray's slowness arrows in snowy biomes
- Seeing a bogged's poison arrows in swamps (1.21)
- Watching a breeze bounce around a trial chamber (1.21)
- Watching an elder guardian's mining fatigue curse appear
- Seeing the warden sniff the air
- Watching a slime split into smaller slimes
- Getting attacked by phantoms after not sleeping
- Watching skeletons burn in sunlight
- Watching creepers flee from cats
- Watching skeletons flee from wolves
- Getting zombies to pick up items it dropped
- Watching a witch drink potions mid-fight
- Watching an evoker summon vexes and fangs
- Collecting a totem from an evoker
- Seeing cave spiders pour out of a mineshaft spawner
- Watching silverfish crawl into stone
- Seeing a tamed cat bring it a gift in the morning
- Making a tamed cat sit on a bed or chest
- Making a tamed wolf sit and stay
- Collecting every wolf variant across biomes
- Collecting every cat variant
- Collecting every parrot color
- Collecting every axolotl color, including the rare blue
- Collecting every horse coat and pattern
- Collecting every llama color
- Collecting every rabbit type
- Collecting every fox type (red and snowy)
- Collecting a brown and a red mooshroom
- Collecting every frog variant
- Collecting every tropical fish variant in buckets
- Collecting every sheep color

## 17. Biome by biome

- **Desert:** cactus farming, dodging husks, looting temples, finding wells
- **Badlands:** finding gold, exploring surface mineshafts, collecting colored terracotta
- **Jungle:** harvesting melons and cocoa beans, befriending parrots, finding pandas in bamboo
- **Swamp:** visiting witch huts, hunting slimes at night (more on a full moon), catching frogs
- **Mangrove swamp:** collecting mud and mangrove propagules, wading through roots
- **Savanna:** finding horses and llamas, chopping acacia
- **Plains:** using sunflowers to find east (they always face east)
- **Flower forest:** picking every flower
- **Birch forest:** chopping birch for a pale palette
- **Dark forest:** finding giant mushrooms and a mansion
- **Taiga:** picking sweet berries, finding foxes and wolves
- **Snowy plains:** finding igloos and checking for the secret basement
- **Ice spikes:** mining packed ice for boat roads
- **Frozen ocean:** walking on icebergs, avoiding polar bears
- **Deep ocean:** spotting an ocean monument
- **Warm ocean:** collecting coral and sea pickles, catching tropical fish
- **Mushroom fields:** living with no hostile mob spawns
- **Cherry grove:** collecting pink petals and cherry wood
- **Meadow:** finding bee nests
- **Jagged and frozen peaks:** climbing, dodging goats, avoiding powder snow
- **Windswept hills:** mining emeralds
- **Stony shore:** fishing off the rocks
- **Beach:** protecting turtle eggs
- **River:** digging clay, dodging drowned at night
- **Lush caves:** collecting azalea, glow berries and axolotls
- **Dripstone caves:** collecting pointed dripstone, dodging falling stalactites
- **Deep dark:** sneaking past sculk, finding an ancient city [R]
- **Nether wastes:** mining quartz and nether gold
- **Crimson forest:** dodging hoglins, collecting crimson fungus
- **Warped forest:** hunting endermen for pearls in relative safety
- **Soul sand valley:** finding fossils, getting slowed by soul sand
- **Basalt deltas:** navigating rough terrain, dodging magma cubes
- **End highlands:** harvesting chorus fruit [L]
- **Small End islands:** pearling between islands [L, R]

## 18. Survival tricks and techniques

Things experienced players do that look like showing off but are actually useful.

- Nerd-poling: jumping and placing a block under itself to climb
- Sneak-bridging backwards over a gap
- Speed-bridging without sneaking [R]
- Breaking a sand or gravel pillar instantly by placing a torch at the bottom
- Making an infinite water source with two buckets
- Turning a lava source into obsidian with a water bucket
- Building a cobblestone generator to never run out
- Placing a sign or a door to hold back water or lava
- Making an air pocket underwater with a door
- Ladder clutch: placing a ladder on a wall mid-fall
- Boat clutch: placing a boat to land in mid-fall
- Slime block clutch
- Cobweb clutch
- Scaffolding clutch
- Block clutch: placing a block behind itself when knocked back
- Staircase mining downward for a safe way back up
- Mining ancient debris with beds that explode in the Nether [R]
- TNT mining for ancient debris [R]
- Testing light levels by placing torches until mobs stop spawning
- Checking chunk borders to plan builds
- Finding a slime chunk by watching where slimes spawn
- Triangulating a stronghold from two eye-of-ender throws
- Linking Nether portals with the 1-to-8 coordinate math
- Building a Nether ice road for boats
- Taking off with elytra from a tall pillar
- Pulling items closer with a fishing rod
- Blocking a creeper blast with a shield
- Killing mobs by dropping lava on them [R]
- Pouring water down a cliff and swimming up the waterfall
- Using a water bucket to climb back up a hole
- Crawling through tunnels by making itself prone with a trapdoor
- Leaving a trail of torches on one side to find the way back
- Writing coordinates on a sign at every important place
- Carrying a bed to set a spawn point mid-trip
- Carrying a boat to cross any water instantly
- Carrying a stack of food and a stack of blocks always
- Using a hoe to break leaves and sculk fast
- Using a shovel to make dirt paths
- Stripping logs with an axe for decoration
- Using fortune on gravel for flint
- Using silk touch on grass, mycelium and ice
- Holding sneak on magma blocks
- Using a carved pumpkin to look at endermen safely
- Wearing gold boots in the Nether for piglins
- Wearing leather boots to walk on powder snow
- Using frost walker boots to walk on water [L]
- Using depth strider to swim fast
- Using soul speed on soul sand
- Using swift sneak in the deep dark
- Keeping a milk bucket for wither effects and bad omen
- Building a farm under the base so crops grow at night with light

## 19. Fails and mistakes

The things that make it human. Low Skill and Knowledge Drifters do these more; everyone does some.

- Falling into lava with a full inventory
- Mining straight into a lava pocket
- Mining into an underwater cave and flooding the tunnel
- Mining into a ravine and falling in
- Getting lost in a cave system for a whole night
- Forgetting where its base is
- Forgetting where it put its Nether portal
- Building a Nether portal that links to the wrong place
- A ghast blowing up its portal and stranding it
- Leaving the door open and finding a creeper inside
- A creeper blowing up its chests
- Sleeping in the Nether and exploding
- Using a respawn anchor in the Overworld and exploding
- Drowning while mining underwater
- Getting stuck in powder snow and freezing
- Falling into the void in the End
- Overshooting an elytra landing into a wall
- Running out of fireworks mid-flight
- Breaking its last pickaxe deep underground
- Forgetting to repair its favorite tool until it breaks
- Using a diamond pickaxe on dirt
- Tossing diamonds into lava while clearing inventory
- Pressing the wrong key and dropping a whole stack
- A hopper eating items it didn't mean to store
- Accidentally hitting a villager and getting chased by the golem
- Hitting its own pet with a sword sweep
- Burning its own wheat field with a fireplace
- Starting a house fire with lava as decoration
- Lightning setting its wooden house on fire (no lightning rod)
- Breaking the spawner it wanted to use as a farm
- Angering a whole beehive by breaking it
- Eating a spider eye and getting poisoned
- Walking into a sweet berry bush repeatedly
- Getting cornered by drowned in a river at night
- Stepping on the desert temple pressure plate
- Triggering its own TNT trap
- Leaving its pet wolf sitting at home for weeks
- Losing its horse because it forgot to tie it up
- Killing the last two cows instead of breeding them
- Placing a bed in a spot too close to monsters to sleep
- Getting knocked off a cliff by a goat
- Swimming after a dropped item into deep water
- Climbing a mountain at night and getting swarmed
- Fighting a raid with stone tools
- Celebrating one diamond, then realizing a pickaxe needs three
- Opening a bastion chest and getting swarmed by piglins
- Punching an iron golem to see what happens
- Picking a fight with an enderman in the open
- Setting its spawn point in a death trap
- Falling asleep AFK somewhere dangerous

## 20. Tinkering and redstone toys

Most are [L] for v1, but good flavor for Builder and Newbie Drifters later.

- A doorbell made from a button and a note block
- A secret passage behind a painting
- A hidden staircase with pistons
- A trapdoor floor trap for visitors [G when aimed at players]
- A drawbridge for its castle
- A flying machine made of slime blocks and observers
- A simple auto-smelter with hoppers
- An item sorter
- A jukebox playlist with redstone timing
- A redstone clock for blinking lights
- A combination lock with levers
- A minecart station at its base
- An automatic sugar cane farm with observers and pistons
- An automatic melon farm
- A water elevator up its tower
- A TNT cannon [R]
- A firework launcher with dispensers
- A mob drop chute into hoppers
- A lava wall for base defense [R]
- A tripwire alarm with a bell
- A sculk sensor alarm that lights lamps when someone walks in
- A copper bulb light switch (1.21)
- A crafter-powered auto-bread machine (1.21)
- A daylight sensor that turns on lamps at night
- An armor stand that gets dressed by a dispenser
- A target block shooting gallery

## 21. Nether and End extras

- Building a Nether highway across the lava sea
- Bridging across basalt pillars
- Making a blaze farm at a fortress spawner [L]
- Setting up a piglin bartering station [L]
- Riding a strider down a lava river with a saddle
- Lighting soul fire for a blue glow
- Keeping a respawn anchor at a Nether base
- Mining glowstone from the ceiling
- Collecting magma cream from magma cubes
- Collecting nether wart for a farm at home
- Finding a bastion's treasure room
- Finding a nether fossil in a soul sand valley
- Watching ghasts float over the lava sea
- Collecting crimson and warped wood that never burns
- Farming chorus fruit in the End [L]
- Bottling dragon's breath after the dragon fight [L]
- Collecting the dragon egg with a piston trick [L]
- Respawning the dragon with end crystals [L, R]
- Throwing a pearl into an end gateway [L]
- Building with purpur and end stone [L]
- Finding an End ship and its elytra [L]
- Farming endermen in the End [L]

## 22. Roleplay and social, extended

- Playing village guard at the village gate
- Acting as mayor: fixing up a village, lighting it, building walls
- Moving a villager to its base by boat
- Building a villager a proper house instead of a hole
- Leaving a welcome chest with food and tools for new players [M]
- Giving a tamed wolf to a player as a present [M]
- A tollbooth on a road demanding items to pass [G, M]
- A bounty board of signs naming players it holds grudges against [M]
- A town hall built with other Drifters
- A trading post with signs listing prices
- Family roleplay with a home and baby animals
- A birthday party with cake and fireworks [M]
- A memorial for a dead Drifter friend
- Gravestones for players it has killed [M]
- Apology gifts after a fight it started [M]
- Planting a white banner as a truce flag [M]
- Hanging war banners during a feud
- Leaving coordinates for a friend on a sign [M]
- Gesturing for help when in trouble [M]
- Escorting a player home at night [M]
- Guiding a lost player back to spawn [M]
- Showing a player where it found diamonds [M]
- Guarding a player's base while they're away [M]
- Lending a player its spare armor [M]
- Waiting outside a player's house to say hi [M]
- Holding a doorway open for a player [M]
- Dropping food for a player who is eating constantly [M]
- Pretending to be an NPC: standing still in a shop until approached
- Cosplay with dyed leather armor and a mob head
- Racing other Drifters on horses
- Swapping pets with another Drifter
- Arguing with another Drifter over a diamond (crouch-spam and head shakes)
- Group photo pose: Drifters lining up on a hill [M]
- A dance party at a jukebox with crouch-spam [M]
- Following a player and copying everything they do [M]
- Throwing a snowball at a player and running away [M]
- Leaving a book for a player titled "read me" [M]
- Walking with a player on a long trip as a buddy [M]
- Joining a player's base build without being asked [M]
- Standing watch on a tower at night
- Building a jail for illagers it captures
- Running a "hotel" with beds for travelers [L]
- Signing a peace treaty book with another group [L]

## 23. Micro-moments

Tiny things that last a few seconds. They're closer to mannerisms (DESIGN.md section 16), but they make a Drifter's day feel lived in.

- Opening every chest in a village to peek
- Reading every lectern book it finds
- Checking every furnace it passes for leftovers
- Ringing a doorbell button at someone's house [M]
- Flipping a lever back and forth
- Hopping along the tops of fences on slabs
- Walking across lily pads
- Jumping down a staircase in one hop
- Swimming a few laps in its pool
- Climbing onto its own roof to look around
- Standing in a doorway watching rain
- Placing a torch on a dark spot it notices on the way past
- Picking up a single stray flower
- Kicking up snow by running through it
- Walking through tall grass and breaking some
- Feeding a random cow some wheat on the way past
- Pausing to watch a sheep eat grass
- Watching a chicken lay an egg
- Collecting a dropped feather
- Stopping to listen to a cave sound
- Turning to watch a firework someone else launched
- Watching lightning strike in the distance
- Squinting through a spyglass at a far-off structure
- Stepping onto a minecart just to sit in it
- Bouncing on a bed once before sleeping
- Lighting a candle before bed
- Straightening an item frame
- Rotating a painting until it likes it
- Re-sorting a chest it just sorted
- Topping off a furnace with one extra coal
- Grabbing an apple from a fallen leaf
- Replanting a single sapling on the way home
- Filling in a single hole in a path
- Placing a block to stop a creeper's view, then removing it
- Taking the long way home to see the view
- Checking a map halfway through a trip
- Looking at a compass and turning around
- Drinking from a water bottle for no reason (it does nothing)
- Hitting a wall of glass panes for the sound
- Pausing at the top of a ladder
- Sitting in a boat on a lake at sunset
- Breaking a single block of ice to watch it turn to water
- Melting snow with a torch
- Watching a sapling grow
- Watching a crop finish growing and harvesting immediately
- Collecting XP orbs it almost missed
- Spinning a full circle at the edge of a cliff
- Standing on the highest block of its build
- Looking straight up at the night sky
- Peeking out of a window before going outside at dawn

## 24. Weather and time

- Fishing in the rain for faster bites
- Staying in to wait out a thunderstorm
- Using a Channeling trident during a storm [R]
- Watching snow pile up in snowy biomes
- Collecting powder snow in a cauldron during snowfall
- Watching the moon change phase each night
- Hunting slimes in a swamp on a full moon
- Being extra careful on a full moon, when mobs are tougher
- Watching the sunrise from bed
- Staying up all night once just to see what happens (then dealing with phantoms)
- Planting crops right after the rain
- Watching rain put out a fire
- Waiting for a clear day before a big trip
- Racing home when a storm starts
- Keeping a clock in the hotbar during long mines

## 25. Self-expression and identity

- Naming its favorite sword and never replacing it
- Keeping its first wooden pickaxe in an item frame
- Carrying a keepsake item it never uses
- Wearing its first iron helmet long after upgrading
- A signature block it puts in every build
- A calling card sign it leaves wherever it's been
- A personal banner on its base and shield
- A trademark armor trim
- A favorite color it dyes everything
- A favorite food it always eats first
- Always carrying a specific pet with it
- A diary of its adventures in a book and quill
- A personal "hall of fame" wall of achievements
- Writing its name on every chest
- Renaming its horse after a famous one
- Giving every pet a name from the same theme
- A specific way of decorating every bed
- Repainting its house in a new color after a big win
- Building the same small hut style in every new place
- Carving its initials into a mountainside with blocks

## 26. Completionist goals

- Every music disc, including Pigstep, Otherside, Relic and the 1.21 discs Creator, Creator (Music Box) and Precipice
- Disc 5 by collecting and crafting its fragments
- Every froglight color
- Every armor trim on a full set
- Every banner pattern
- Every smithing template
- Every type of log, stripped and unstripped
- Every stone and deepslate variant
- Every tuff and copper block variant (1.21)
- Every concrete and terracotta color
- Every glazed terracotta pattern
- Every coral and coral block
- Every flower, including torchflower and pitcher plant
- Every food item
- Every potion and its splash and lingering forms
- Every enchantment at max level
- Every advancement
- Visiting every structure type at least once
- Every biome visited
- Every mob killed at least once (except passive pets)
- Every animal bred at least once
- Every villager profession traded with
- A full beacon at max power
- A full netherite set with max enchantments
- A conduit at full power
- A map wall covering its whole region
- A stack of every ore block
- A pet of every tameable kind

## 27. Crafting just because

- Crafting an armor stand to display its old armor
- Crafting a dozen item frames for a wall
- Crafting paintings until it gets a favorite
- Crafting a jukebox the moment it finds a disc
- Crafting a doorbell from a note block and a button
- Crafting a book and quill for a diary
- Crafting a map of its home area
- Crafting a clock and a compass for the hotbar
- Crafting a spyglass after finding copper and amethyst
- Crafting a lightning rod for the roof
- Crafting colored candles for every room
- Crafting a banner for its base
- Crafting a shield with that banner
- Crafting a cake for a celebration
- Crafting firework rockets in bulk for a show
- Crafting a lead from slime balls for its horse
- Crafting a bow just to have one
- Crafting a fishing rod on day one
- Crafting a boat for every lake it passes
- Crafting a chest boat for long trips
- Crafting a bamboo raft just for the look
- Crafting a scaffolding stack for a build
- Crafting stairs and slabs in every wood type
- Crafting a lantern instead of a torch for a nicer look
- Crafting a campfire for a cozy corner
- Crafting a brewing stand as soon as it gets a blaze rod
- Crafting a recovery compass after a bad death
- Crafting a target block for archery
- Crafting dyes from every flower it picks
- Crafting golden carrots to feed horses

## 28. Things to do at a village

- Sleeping in a villager's bed and waking them up
- Stealing bread from a farmer's chest [G]
- Harvesting a village farm and replanting it properly
- Trading every villager once to see what they sell
- Ringing the village bell to send everyone inside
- Lighting up the whole village so zombies can't spawn
- Building a wall around the village
- Fixing up a broken village house
- Giving a villager a job by placing a workstation
- Breeding villagers by giving them beds and food
- Curing a zombie villager for cheap trades
- Protecting villagers from a zombie siege at night
- Defending against a raid
- Watching the iron golem patrol
- Standing next to the iron golem waiting for a poppy (it only offers them to villagers, so it waits forever)
- Finding the cartographer's explorer maps
- Buying a Mending book from a librarian
- Getting a free gift from villagers after being Hero of the Village
- Moving into an empty village house
- Setting up its base right next to a village
- Ignoring the village entirely after a bad experience with the golem
- Looting the blacksmith chest every time it passes
- Walking through the village at night to check on it
- Hiding inside a village house during a storm
- Building a statue in the village square
- Leaving a gift chest in the village for players to find
- Burning the village down out of spite [G]
- Locking villagers in their houses for a raid
- Using the village bell to count villagers
- Turning a village into its own town over many days [L]

## 29. Who likes what

| Archetype | Favorite whim categories |
| --- | --- |
| Newbie | Movement play, silly builds, weird and idle, fails and mistakes |
| Casual | A little of everything |
| Builder | Silly and personal builds, music and art, exploration for views, crafting just because |
| Miner | Collecting, digging to bedrock, geodes, survival tricks |
| Homebody / Farmer | Animals, food and cooking, farms, gardens, villages |
| Speedrunner | Movement play, self-made challenges, survival tricks |
| Nomad | Exploration, biomes, camping, movement play |
| Hoarder | Collecting everything, completionist goals, trophy rooms, museums |
| Hermit | Secret bases, stargazing, weather and time, quiet idle |
| PvP Sweat | Combat for fun, duels, arenas |
| Bandit | Destruction and chaos, pranks, taunts |
| Helper | Social and roleplay, gifts, pet funerals, housewarming parties |

## 30. Modded whims (soft dependencies)

None of these mods are required. Each whim below only exists when its mod is detected at startup, and every entry carries a **[Mod: name]** gate on top of the usual [G], [M], [R] and [L] tags. The goal is that a Drifter in a modded world plays the modpack the way its players do, not like a vanilla player who ignores half the content.

Two rules for all modded content:

- **Learn by seeing.** A Drifter doesn't know a modded recipe or block until it has seen one (a cooking pot in a village kitchen, a bounty board in a gazebo) or its Knowledge trait is high. This mirrors players discovering a modpack gradually.
- **Use the mod's own systems.** Call the mod's own block interactions, recipes, item tags and data where possible, so behavior stays correct as the mods update.

### 30.1 Farmer's Delight (Fabric: Farmer's Delight Refabricated) [Mod: farmersdelight]

A cozy cooking and farming expansion: new crops (cabbage, tomato, onion, rice), a cooking pot, skillet, stove, cutting board and knives, plus big placeable feasts. Players treat it as the "real food" upgrade and build a kitchen early.

- Finding wild cabbages, tomatoes, onions and rice and bringing them home to plant
- Planting rice in shallow water
- Crafting a knife first thing, because it's cheap and gets more from crops and animals
- Cutting grass with a knife for straw, then making canvas and rope
- Building a kitchen corner: stove, cooking pot on top, cutting board, cabinets
- Chopping ingredients on the cutting board instead of crafting them
- Cooking stews and soups in the cooking pot for long-lasting food buffs
- Cooking on a skillet, and occasionally hitting a mob with it
- Placing a big feast (a roast or a pie) on a table and serving portions [M when shared]
- Making organic compost to turn soil into rich soil
- Planting a vegetable garden in rows next to the wheat farm
- Making dog food to feed and heal tamed wolves
- Feeding its horse proper horse feed
- Packing good meals before a big trip instead of plain steak
- Taking ingredients from a village kitchen [G near players]
- Decorating with rustic blocks: crates, baskets, canvas rugs, cabinets

**Signature moments:** the first stew out of the cooking pot; a dinner party where the Drifter places a feast and crouch-spams at a friendly player.

**Drifter notes:** treat Farmer's Delight meals as top-tier food in the eating policy (DESIGN.md section 10.5), and add the kitchen as a base module for Homebody, Farmer and Builder archetypes.

### 30.2 Simply Swords [Mod: simplyswords]

New weapon families (longswords, rapiers, katanas, spears, glaives, claymores, scythes, chakrams and more) with rolled stats, plus rare Unique weapons from loot that have special abilities, an awakening system, and socketable Runefused and Netherfused gems.

- Crafting one of each weapon type to find the one that feels best, then sticking with it
- Comparing the rolled stats on two copies of the same weapon
- Finding a Unique weapon in a chest and immediately testing its ability on the nearest mob
- Awakening a Unique weapon to restore its full power
- Smelting a duplicate Unique into an Empowered Remnant to make a gem
- Crafting Runefused or Netherfused gems at a smithing table and socketing them into a favorite weapon
- Rerolling gems until it gets a power it likes
- Using a Runic Forge to pull gems back out
- Showing off a Unique weapon by holding it in front of a player [M]
- Hanging favorite weapons on the wall in item frames

**Signature moments:** finding a Unique (freeze, stare, equip it, test it on the first mob); a Drifter carrying a rare Unique becomes a prized kill for players.

**Drifter notes:** weapon choice uses item attributes as in DESIGN.md section 21; ability weapons need a small "when to trigger" data entry. Add a favorite weapon family to the habit fingerprint.

### 30.3 Simply Bows, Better Combat and Combat Roll

Their combat rules are already in DESIGN.md section 21. These are the fun-time whims.

- **Simply Bows:** collecting every bow type, testing each on target blocks, keeping a favorite for long-range fights
- **Better Combat:** practicing combos on a zombie, choosing a two-handed weapon for big sweeping hits, duels where spacing matters [M]
- **Combat Roll:** rolling around for fun, rolling through a gap in a crowd of mobs, rolling away from a creeper at the last second, chaining rolls across a field when bored

### 30.4 Alex's Mobs (1.21.1: community ports, including a Fabric one) [Mod: alexsmobs]

A huge bestiary: 85+ creatures across every biome, many tameable or rideable, with unique drops that craft special gear. Players treat it like a nature documentary and a collection hunt.

- Reading the Animal Dictionary to learn about each new creature
- Taming a capuchin monkey with bananas and taking it on adventures
- Riding an elephant, or meeting the trader riding one
- Training a bald eagle with a falconry glove and hood
- Taming a kangaroo and using its pouch as extra storage
- Watching a raccoon wash items in water, and losing an item to one
- Losing crops to crows
- Feeding bananas to gorillas
- Spotting a giant squid or a cachalot whale in the ocean
- Riding a laviathan across Nether lava
- Getting chased by crocodiles in swamps and grizzly bears in forests
- Avoiding the hostile ones at night in their biomes
- Crafting gear from mob drops (like roadrunner boots from roadrunner feathers)
- Making cockroaches dance with maracas
- Fighting the void worm in the End [R, L]
- Building a zoo of Alex's Mobs creatures [L]
- Filling the whole Animal Dictionary as a completionist goal

**Signature moments:** the first meeting with a strange creature (stop, stare, approach carefully, look it up in the dictionary); a pet monkey tagging along.

**Drifter notes:** these mobs need entries in the threat table (DESIGN.md section 13) and hazard lists. Treat unknown modded mobs cautiously until the Drifter has seen how they behave. Since 1.21.1 only has community ports, keep this integration especially loose.

### 30.5 Numismatic Overhaul [Mod: numismatic-overhaul]

A Terraria-style coin economy: bronze, silver and gold coins kept in a purse, earned from pillagers, exploration and ruins, and spent on reworked villager trades and player-run shops.

- Picking up every coin it sees, even single bronze coins
- Checking its purse after a good day of looting
- Killing pillagers partly for the coins
- Saving up for a specific villager trade
- Setting up a shop block at its base to sell surplus (wheat, iron, food) [M]
- Buying from a player's or another Drifter's shop [M]
- Price-checking several villagers before buying
- Hoarding coins instead of spending them (Hoarder archetype)
- Losing coins on death and running back for them
- Paying a player in coins for help or items [M]
- Charging a toll in coins at a road or bridge [G, M]

**Signature moments:** a big coin haul from a ruin; a Drifter shopkeeper standing behind its shop waiting for customers.

**Drifter notes:** the Greed need (DESIGN.md section 7) should count coins as valuables, and the barter system (section 14) can use coins as a shared currency with players.

### 30.6 Bountiful (1.21.1: beta builds) [Mod: bountiful]

Bounty boards in villages post timed tasks (gather items or kill mobs) for rewards. Decrees shape which bounties appear, and each board builds reputation that unlocks rarer bounties.

- Checking the bounty board every time it visits a village
- Taking an easy bounty it can already fill from its chests
- Planning a trip around a bounty (a mob hunt or a specific item)
- Racing to finish a bounty before it expires
- Building a board's reputation to unlock rarer bounties
- Placing decrees on a board to shape what it asks for
- Putting a bounty board at its own base [L]
- Competing with players or other Drifters for the same bounty [M]
- Ignoring annoying bounties and waiting for the board to refresh

**Signature moments:** jogging back to a village with a full inventory to turn in a bounty; two Drifters racing for the same one.

**Drifter notes:** bounties are a ready-made goal source for the strategist (DESIGN.md section 8): a bounty becomes an intention like "get 16 leather" with a deadline.

### 30.7 Artifacts [Mod: artifacts]

Rare wearable items with passive powers that can't be crafted, found in structure chests, through archaeology, and from mimics: chest-shaped monsters hiding in underground campsites that always drop an artifact.

- Exploring structures specifically to hunt artifacts
- Opening a "chest" in a campsite, getting ambushed by a mimic, panicking, then fighting it for its guaranteed artifact [R]
- Hitting every campsite chest before opening it, after being burned once by a mimic
- Double-jumping everywhere with a cloud in a bottle
- Swapping artifacts for different jobs (mining, swimming, fighting)
- Wearing a novelty artifact just for the look
- Building an artifact display room
- Comparing artifact loadouts with other players [M]
- Trading away duplicate artifacts [M]

**Signature moments:** a mimic ambush, the classic panic moment; showing off a double jump in front of a player.

**Drifter notes:** artifacts sit in extra accessory slots (Trinkets on Fabric), so the Drifter's inventory model needs those slots when the mod is present. Mimics go in the threat table, and a Drifter bitten by one should pick up the "poke chests first" habit.

### 30.8 Adding more mods later

For any other mod in the pack, follow the same pattern: list what players actually do with it, write each as a whim with a [Mod] gate, add its mobs to the threat table and its foods to the food table, and let the planner use its recipes through the game's recipe manager. Mods with their own goal systems (bounties, shops, quests, accessories) are the most valuable to integrate, because they give the Drifter new reasons to act.
