# Learning Blocks - Content Guide

The reference for people adding to or changing what the classroom teaches: the word file format, the shipped course word by word, and the plushies, toys and props. Everything here is data in a data pack; no Java is needed. For playing and running the mod, see [README.md](README.md). For building it, see [DEVELOPMENT.md](DEVELOPMENT.md).

## Words

Words are data, not code. Each is one file, `data/<namespace>/learning_words/<word>.json`:

```json
{
  "tiles": ["c", "a", "t"],
  "stage": 1,
  "outcome": { "type": "spawn_entity", "entity": "minecraft:cat" }
}
```

A data pack can add words, or replace the shipped ones, by writing files at the same paths. A word added this way has no recording: its caption shows as usual, but where the word would be said the room plays a short chime instead, and its letters are sounded out from the shipped letter sounds.

A word naming a mob, item, block, particle, sound or effect that isn't installed is skipped, so a data pack can reach for another mod's creatures without breaking anything when that mod is missing.

### Fields

- `icon` (an item id) is the picture shown over the board while the word is asked for; without it the picture is what the outcome brings (a creature's spawn egg, the item, the block). It can name a plushie (`learning-blocks-justfatlard:plush_owl`) or a thing.
- `sounds`, one per tile, says a tile differently in this word: `"a_long"` for the letter's name, `"silent"` for a letter that says nothing (shown greyed in the caption), `null` for the tile's own sound. Crow's ow says /oh/, fly's y says its name, mice's c says /s/, worm's or says /ur/:

  ```json
  { "tiles": ["c", "a", "k", "e"], "sounds": [null, "a_long", null, "silent"], "stage": 9, "outcome": { ... } }
  ```

- `picture: "clear"` marks a word whose picture a five-year-old names at once as the word, for read-it rounds (cat, bell, ship, crown; never a verb or a loosely pictured word such as sat, run, jam).
- `requires` (a list of mod ids) leaves a word out entirely when any of those mods isn't installed (poop needs Poopsmith). A word that only prefers a mod's thing uses a `prefer` outcome, which falls back to vanilla.
- `picture_card` names a picture card for the board (see Props and picture cards).
- `earns` (`"earns": "teddy"`) says which plushie or toy mastering this word earns; otherwise the link comes from the toy its picture or outcome names.
- `tricky: true` makes it a tricky word (with `puzzle: false`): its `stage` is when it opens, and `shown` (a list of tiles, `cap_i` for I) is how the board and its card write it when that isn't its tiles.
- `plural: true` (with `puzzle: false`) makes it a plural: its tiles are a word's plus `s`, `"z"` in `sounds` for an s that says /z/, and its outcome the creature three times. Only creatures have plurals.
- `keyword: true` makes a word a letter's keyword too (zap for z): said and done at the end of that letter's chant in the letters level.
- `puzzle: false` makes it nothing else (apple): never a puzzle, in no family, and never in My words.
- `chunks` (`[3, 3]`, tiles per chunk) makes a big word, and `compound: true` marks one made of two words; the compounds come first in the stage's bands. A chunk that is not a word on its own (munk, puf, lob, ster, hedge, bob, rob, set, pan, bow, snow) needs its own word file with `"puzzle": false`, so it has a recording to be said by.
- `wrong` (optional, same shape as `outcome`) is what the word does when it's built but wasn't the word asked for: a funnier, harmless version, over in a couple of seconds, with nothing left behind. Without one, or if it names something missing, the word does its ordinary outcome instead.
- `scene` (optional) makes the word a situation; see Scenes.

A puzzle is made from a family of words sharing everything but one tile (cat, bat, hat, mat; map, mat, man; cat, cot, cut), so a family's other words are the wrong answers, and each is a real word that does something too. Within a stage, words are ordered by the latest letter group they need (s a t p i n; m d g o c k; e u r h b; f l j v w; x y z; then two-letter tiles).

### Outcomes

| Type | Does |
|------|------|
| `spawn_entity` | A creature: `entity`, plus `baby`, `tamed_to_player`, `sitting`, `profession` (for a villager). It must be in the `learning-blocks-justfatlard:harmless` entity tag, or in `harmless_as_baby` and spawned as a baby, and never a monster. `act` makes it `spin` (chase its tail), `bounce` on the spot, `sneeze`, `bonk` (trot off the wrong way into the wall), `circle` the child's head, or `flee` to the far corner (not used by the shipped words: running away reads as rejection); `lasts` (ticks) makes it vanish in a puff after that long |
| `equip_item`, `give_item` | An item on (`slot`) or in the pack; `count` and `components` optional |
| `effect` | A beneficial or neutral effect: `effect`, `duration`, `amplifier` |
| `place_block` | A block (`properties` optional) `at` the child's `feet` or on the free floor `near` them; never on the board or the tiles, cleared at the next round and on leaving. A bed takes two squares. `lasts` (ticks) makes it go in a puff of smoke that long after it lands |
| `scale` | The child grows (`amount`, 0.8 is nearly double) for `duration` ticks |
| `particles` | `particle` (an id, or a full particle object such as dust with a colour), `count`, `spread`, `at` `player`, `above` or `face` (just in front of the eyes), an optional `sound` and `pitch`, and `repeat` bursts `every` ticks apart |
| `push` | A shove `back` from where the child is looking, and `up` |
| `firework_burst` | A firework over the child, `shape` and `colors` optional; a `dud` goes up and fizzles out |
| `creatures_come` | Every creature in the room hops over to the child |
| `creatures_go` | Every creature in the room runs off from the child (not used by the shipped words) |
| `throw_at_player` | `count` snowballs thrown at the child, `every` ticks apart. Snowballs only; none end up in the pack |
| `bonk` | `items` fly at the child's head, bonk (an optional `sound`), and fall off to lie on the floor a moment |
| `spin` | The child turns round on the spot: `turns` over `ticks` |
| `roll` | An `item` rolls out of the button and across the floor, gone at the far wall after `ticks` |
| `tune` | A little tune over the child: `notes` (semitones, 0 to 24, as a note block counts them) on an `instrument` (harp, bass, flute...), `every` ticks apart |
| `pull` | The child pulled towards the button, `strength` |
| `hut` | A tiny wooden hut round the child for `lasts` ticks: its doorway always open, a roof only over a child of ordinary size, only in empty squares |
| `creatures_do` | Every creature in the room: `what` is `hearts`, `sit`, `spin`, `hop`, `bounce`, `sneeze` or `freeze` |
| `strike` | A lightning rod out of the button, struck by a little bolt of sparks (particles, not real lightning: no thunder, no fire), gone after `lasts` ticks |
| `sky` | The child's own sky glides (`glide` ticks) to `time`: `day`, `dusk`, `night` or `dawn`, from `from` if given (the night before a sunrise); `flicker` is two blinks of it and back. Back to a clear noon at the next round |
| `weather` | The child's own `weather`: `rain`, `drizzle`, `snow`, `storm` (a rumble and a far-off flash, never a bolt) or `cloud` (a cloud drifts over); for `lasts` ticks, or until the next round |
| `character` | A character (`entity`, a villager by default, harmless only; `baby`, `profession`) hops out of the button to stand in front of the child, with a `face` in a bubble (`glad`, `sad`, `mad`, `sleepy`, `surprised`, `scared`, `love`, `cold`, `hot`) and a `gesture` (as a scene's), and goes in a puff after `lasts` ticks |
| `dark` | The lights go out for the child for `lasts` ticks (Pandorical clients), then click back on; `flicker` is two blinks |
| `sequence` | Several of these in order: `steps` |
| `prefer` | The first of its `options` this server can run: a mod's thing where the mod is installed, a vanilla one where it isn't |

Everything a word brings is harmless, can't be hurt, and stays in the classroom: creatures are invulnerable and only a few are kept, anything put down goes on open floor away from the board and the tiles and is cleared at the next word, and a grown child shrinks back on the way out if not before.

### Scenes

`scene` makes the word a situation, asked once it has been read: `line` (said instead of "Can you make...", and recorded by the sound generator as `scene/<word>`), `setup` (what's in the room) and `solve` (what the right word does).

Setup steps (and in a solve, `feel`, `gesture`, `sky` and `weather` too):

- `feel`: a `face` in a bubble over the character, or every one with `all`.
- `gesture`: the character does it: `nod`, `shake`, `wave`, `bow`, `dance`, `duck`, `jump`, `spin`, `hide`, `shiver`, `stomp`. Each puts it back exactly where it was.
- `sky`: the child's own sky to `day`, `dusk`, `night` or `dawn`.
- `weather`: `rain`, `drizzle`, `snow`, `storm` or `clear`.
- `character`: `entity` (a villager by default, harmless only), `baby`, and `act`: `still`, `wander`, `shiver`, `bounce`, `stuck` or `hungry` (glancing over at the button). Up to three, the first the one the scene is about.
- `ambient`: particles kept up over the character: `particle`, `at` (`over`, `head`, `face`, `body`, `beside`), `count`, `spread`, `every`, an optional `sound` every `sound_every` ticks, and `drift` to float away when solved.
- `bubble`: a thought over its head: `text`, `color`.
- `dim`: the room goes dark for the child.
- `litter`: `items` lying on the floor.
- `block`: `block` at squares `at` from the scene's spot, `[east, south]` or `[east, south, up]`, beside the character by default, and what it `becomes` when solved.
- `wrap`: `item`s wound round the character.
- `ring`: a ring of `block` round the spot.
- `prop`: an `item` turning on the spot.

Solve steps:

- `give`: an `item` flies to the character, or to every one with `all`, `then` `wear`, `hold`, `eat`, `drink`, `snip` the wrapping, `pull` a stuck one free, or `soak` a puddle.
- `place`: a `block` flies beside it, `then` `sleep`, `absorb` the litter, or `none`. `tuck:<item>` tucks a toy into the placed block.
- `burst` (particles and a sound), `bubble`, `light`.
- `pose`: every character `sit`s or `lie`s down.
- `visit`: a helper `entity` comes from the button `to` `beside`, `over` or `front`, with a villager's `profession`.
- `tune`, `effect` (a beneficial effect on the child), `hut`.

After the solve the trouble stops by itself, the blocks become what they become, and the character cheers up.

## The shipped course

### Bands

| Band | Words | Rounds at mastery 3 |
|------|-------|---------------------|
| 1a | ant nap pat sat tap | 15 |
| 1b | cap cat man map mat pad sad tag mad | 27 |
| 1c | bat hat rat bag ham rag ram fan van jam wag yap zap yak wax | 45 |
| 2a | pin sip sit tip pig dig kid | 21 |
| 2b | bin hit rip big hid fin lit win jig lid wig six zip mix fix | 45 |
| 3a | pop pot top cot mop nod cod dog hop | 27 |
| 3b | hot hog rod jog log ox box fox | 24 |
| 4a | pup sun cup cut pug tug gum mud mug bun bus | 33 |
| 4b | hut run tub bug cub hug hum rug fun yum | 30 |
| 5a | net pen pet ten keg hen bed beg | 24 |
| 5b | red jet vet wet fed leg web yes | 24 |
| 6a | pack pick tick kick kiss sock doll egg hill hiss | 30 |
| 6b | puff duck rock bell buzz fill fizz fell well yell | 30 |
| 7 | chip path ship chop dish moth bath shed fish wish chick quick quill shell | 42 |
| 8a | snap spin stop step trip drip grin drop crab crib drum clap | 36 |
| 8b | swim twig glad sled frog flap flag chest truck clock block | 33 |
| 9a | pine cape gate kite snake cake mice bone hide rope | 30 |
| 9b | rose cube vine dive mole hive mule wave fly whale | 30 |
| 10a | pie moo coat goat meat moon seed bee bean boat boot rain | 36 |
| 10b | tree train loop seal snail food mail rail leaf sheep beetle | 33 |
| 11a | sand mask nest tent desk dusk gift lamp milk cold | 30 |
| 11b | raft jump melt pink sing king ring honk skunk | 27 |
| 12a | star turn car corn dirt storm dark burp horn bird | 30 |
| 12b | deer herd horse jar fern farm fork worm torch shark | 30 |
| 13a | night snow town cow day coin dawn down hay high crow hawk | 36 |
| 13b | crown mouse house joy owl light play clay cloud howl oink mouth | 36 |
| 14a | pancake sunset bobcat cupcake sandbox snowman popcorn bathtub | 24 |
| 14b | hedgehog catfish rainbow starfish robin puffin lobster chipmunk | 24 |

The table is the shipped words with no optional mods installed (cone, poop and the like join their band where their mod is). Stages 1 and 2 split by letter group (1a is s a t p i n alone, 1b adds the second group, 1c is the rest; a band of fewer than four words joins its neighbour); any other stage that would take more than 45 rounds at mastery is split in two halves, so a different `mastery_clean` changes which stages are split. The letters level takes about 75 clean matches, and as many again for big and little letters.

Gem (an emerald) and kit (leather armour) are kept as word files but are not puzzles: gem's soft g is never taught, and kit is a word many children don't know. They are there to turn back on with `"puzzle": true`.

### Two-letter tiles

sh, ch, th, ck, qu, ll, ss, ff, zz, gg, ng, nk, wh (whale) and le (beetle); the vowel teams ee, ea, oa, ai, oo, ie, ow (as in cow), ou, ay, oi, oy and aw (hawk); the r-controlled ar, or, ur, ir and er; and igh and dge (hedge), three letters on one tile. Each is a tile of its own with both letters on it, blue for consonants and red for vowel teams. A word like ship is three tiles, sh-i-p, sounded out as three sounds.

### Words, and what they do

| Stage | Families | Words, and what they do |
|-------|----------|-------------------------|
| 1, short a | -at -ag -ap -an -am | **cat** a cat, **bat** a bat, **hat** a hat on your head, **mat** a mat at your feet, **sat** a cat sits by you, **pat** a sheep, and hearts, **bag** a bundle, **tag** a name tag, **map** a map, **cap** a toy cap on your head, **tap** tap, tap, tap, **man** a villager, **fan** a desk fan and a gust that pushes you back, **zap** a lightning rod struck by a little harmless bolt, **yap** a fox cub yapping, **nap** a lullaby, **ram** a sheep, **ham** a porkchop, **jam** sweet berries, **wag** a dog of your own, wagging, **rag** a white rag, **van** a toy van, **pad** a lily pad, **wax** honeycomb |
| 2, short i | -ig -in -it -ip -id -ix | **pig** a pig, **big** you grow for a few seconds, **dig** a shovel and a burst of speed digging, **jig** a dance, **bin** a bin, **fin** a tropical fish in a bucket, **win** a firework and a cheer, **lit** a torch, **sit** a dog sits, and every pet in the room sits too, **hit** a target, **zip** a dash forward, **sip** a drink, **tip** a bucket tipped out, **rip** paper ripped, **kid** a villager child, **lid** a trapdoor lid, **hid** you hide in a puff of leaves, **six** six carrots, **fix** a fix and a cheer, **mix** potions mixed |
| 3, short o | -og -ot -ox -op -od | **dog** a dog of your own, **log** a log, **jog** speed, **hog** a pig, **pot** a flower in a pot, **cot** a white bed, **hot** a campfire beside you, **fox** a fox, **box** a cardboard box, **hop** jump boost, **pop** bubbles pop, **mop** a mop, **top** a spinning top, **rod** a fishing rod, **cod** a cod flopping about |
| 4, short u | -un -ug -ub -up -um -ut | **bun** bread, **run** more speed, **fun** a firework, **rug** a rug at your feet, **hug** hearts, the teddy, and every creature in the room hops over to you, **cub** a polar bear cub, **tub** a bathtub, **bus** a toy bus, **pup** a puppy of your own, **cup** a bottle, **sun** sparkles and a sunflower, **yum** a cookie and a burp, **hum** a little tune, **gum** a slime ball, **bug** the ladybug plushie, **tug** a lead, and you're pulled towards the button, **cut** shears, **hut** a tiny wooden hut round you for a while, **mud** a block of mud |
| 5, short e | -en -ed -et -eg (and web, yes) | **hen** a chicken, **pen** a book and quill, **ten** ten snowballs, **bed** a red bed, **red** red dye and a puff of red, **pet** a cat of your own that sits by you, **wet** a drizzle and a splash, **jet** a toy jet, then a jump into the air and a slow float down, **leg** leggings on, **beg** a cat sits up and begs, **keg** a barrel, **fed** hearts over every creature, **vet** a cleric who makes every creature feel better, **web** a cobweb, **yes** a villager's "hm!" and a cheer |
| 6, double letters and ck | -ell -ick -ock -iss -ill (and buzz, fizz, puff, pack) | **bell** a bell rings, **well** a well of water, **yell** a cheer that makes every creature jump, **fell** a log rolls by, **kick** a slime ball rolls off, **egg** an egg (and e's keyword), **pick** a pickaxe, **tick** a clock and a tick-tock, **rock** cobblestone, **sock** a sock puppet, **doll** a doll, **kiss** hearts, **hiss** a cat hisses, **buzz** a bee, **fizz** fizzing bubbles, **puff** a puff of cloud, **hill** a grass mound, **fill** a bottle filled with water, **pack** a bundle |
| 7, sh ch th and qu | -ip -ish -ick -ath (and shed, shell, chop, quill) | **ship** a boat to sit in (pictured as the toy ship), **chip** a baked potato, **fish** a fish, **dish** a bowl, **wish** a star firework, **chick** a chick, **quick** speed, **quill** a feather, **bath** a bath of water and a splash, **path** a path block, **shed** a tiny shed round you, **shell** a nautilus shell, **chop** an axe |
| 8, blends | -lock (and whole-word rounds) | **frog** a frog, **swim** dolphin's grace, **spin** you spin round, **grin** a happy face, **drum** a toy drum and a drumbeat, **twig** a stick, **clap** clapping, **flap** a parrot, **snap** a snap, **stop** every creature freezes a moment, **drop** an apple drops, **drip** drips, **trip** a stumble forward, **flag** a banner, **step** a stair, **sled** a sled to sit in, **clock** a clock, **block** an ABC block, **truck** a toy truck, **chest** a chest |
| 9, magic e | -ake -ine -ive -one -ose ... | **cake** a cake (the Let's Cook chocolate cake, with that mod), **cape** a cape on your back, **gate** a fence gate, **bone** a bone, **cone** an ice cream (only with Let's Cook), **rope** a rope ladder (with Better Ladders; a lead without), **rose** a rose bush and hearts, **pine** a spruce sapling, **vine** vines, **hive** a beehive and dripping honey, **dive** a big splash, **cube** an ice cube, **mule** a baby mule |
| 10, vowel teams | -oat -ail (and more) | **bee** a bee, **tree** a little tree, **sheep** a lamb, **seed** seeds, **leaf** leaves, **bean** a magic bean (with Cloud Kingdoms; cocoa beans without), **meat** a steak, **boat** a boat to sit in, **goat** a baby goat, **coat** a coat on, **rail** a rail (Minecart Mania's wooden rail, with that mod), **mail** a mailbox (with Village Mail; a letter without), **rain** rain, **train** a toy train, **pie** a pie (Let's Cook's, with that mod), **food** a roast chicken, **boot** an iron boot, **moo** a calf mooing, **loop** a loop-the-loop spin, **poop** a poop pops out (only with Poopsmith) |
| 11, ng, nk and final blends | (whole-word rounds, mostly) | **sing** a villager sings, **pink** pink dye and a pink puff, **honk** a honk, **nest** a bee nest, **jump** a jump, **lamp** a lantern, **tent** a little wool tent round you, **milk** a bucket of milk, **sand** sand, **mask** a pumpkin mask on, **gift** a present, **raft** a raft to sit on, **melt** ice melts |
| 12, r-controlled | -orn -ird ... | **star** a star, **car** a toy car, **farm** a hay bale and a chick, **horse** a foal, **storm** clouds and rain, **bird** a parrot, **fern** a fern, **herd** three sheep, **horn** a horn toots, **torch** a torch, **dirt** dirt |
| 13, ow ou ay igh oi oy | -ay (and more) | **cow** a cow, **down** you float down, **crown** a gold crown on, **cloud** a cloud (Cloud Kingdoms' with that mod), **house** a little brick house round you, **howl** a wolf howls, **hay** a hay bale, **play** a tune, **clay** clay, **light** a glowing block, **high** up you go, **coin** a gold coin, **joy** hearts and a firework, **oink** a piglet |
| 14, big words | chunks: cat\|fish, rob\|in | **bobcat**, **catfish**, **starfish**, **chipmunk**, **puffin**, **robin**, **lobster**, **hedgehog** their plushie, **sunset** a sunset glow, **cupcake** a cupcake and a happy burp, **popcorn** popping, **rainbow** a rainbow shimmer, **snowman** snowflakes, **pancake** a pancake stack, **sandbox** a puff of sand, **bathtub** a bathtub and a splash |

The plushie, toy and prop words (each brings its plushie, toy or prop, and a friendly spin when built wrong): stage 1 ant, rat, yak, sad (a picture card); 2 pin, wig; 3 ox; 4 pug, mug; 5 net; 6 duck; 7 moth; 8 crab, crib, glad (card); 9 kite, snake, mole, whale, fly, mice; 10 snail, seal, beetle, moon (card); 11 ring, king (a gold crown), skunk, desk; 12 shark, worm, deer, fork, corn, jar; 13 owl, mouse, hawk, crow, mouth and town (cards).

The sky, weather and feeling words (each sky word glides the child's own sky, and a wrong one is a blink of it and back): stage 1 mad (a villager stomps, a mad face over him; card); 3 nod (a villager nods, glad; card); 9 wave (a villager waves hello; card) and hide (a rabbit hides behind a bush and peeks out; card); 11 dusk (the sky goes to dusk; card) and cold (snow falls, and a villager shivers with a cold face; card); 12 dark (the lights go out, then click back on; card); 13 night (night falls, stars and the moon; card), day (night, then the sun comes up; card), dawn (card) and snow (it snows; with a situation). Rain rains, storm storms, wet drizzles, cloud drifts a cloud over, moon and star bring the night, sun comes up, sunset turns the sky to dusk, and sad, glad and hot bring a villager feeling it (sad bows its head, glad dances, hot sweats).

### Tricky words

the, I, to, no, go, he, she, we, me, be, was, you, they, my, said, are, of. They open a few at a time from stage 2 (the, I and to first; are and of at stage 7).

### Keywords

The letters level's keywords are the words that start with each letter (bat, cat, dog, hat, kid, web, yum...), plus keyword-only words for the rest: apple, egg, gold, ink, jump, nest, on (a lamp lights up), rabbit, sun, turtle, up, villager; zap's lightning is a little one, and quiet; x ends its keyword, fox. The vowels' keywords are apple, egg, ink, on and up.

### Phrases and plurals

Phrase adjectives: big, red, wet, fun, six, sad, glad, mad. Big pig is one big pig, six cod is six cod, red makes a sheep red (anything else gets a red puff), wet drips, fun makes it hop with sparkles, and sad, glad and mad put a face over it. Plural s says /s/ in cats, bats and pups, and /z/ in dogs, pigs, hogs, hens and cubs.

### Situations

hat (rain), lit (the room goes dark), bun (a rumbling tummy, crumbs, and hungry looks at the button), cot (a sleepy villager), hot (a shivering villager in the snow; a campfire comes to warm him), map (a lost villager pacing about), bin (litter on the floor), hug (a sad kitten), fan (a hot villager), cup (a thirsty villager by a cauldron), sit (two pups bouncing about; sit calms them), fed (a hungry pig, cow and sheep), hop (a cake behind a little fence; hop gives the jump to get over it), cut (a villager tangled in vines), wet (droopy flowers that bloom once watered), hum (a baby villager who can't sleep), nap (a tired dog), hut (a villager blown about by the wind, leaves flying), mop (a puddle), rod (a villager by a pond who wants to go fishing), vet (a puppy that needs an animal doctor, and a cleric comes), tug (a villager stuck in the floor), pop (a pinata to burst, only with the pinata mod installed), ship (a villager who can't get across the water; a boat comes), fish (a hungry kitty), bone (a puppy that wants something to chew), milk (a thirsty baby villager), hay (a hungry foal), tree (a parrot with nowhere to sit; a little tree comes), sheep (a villager who needs some wool; a sheep comes), night (a sleepy villager who can't sleep in broad daylight; night falls and a bed comes), snow (a villager who wants to build a snowman) and crib (a baby who can't sleep; the crib comes with the doll tucked in).

## Plushies, toys and props

### Plushies

Thirty-four, each a word Minecraft has no creature or thing for: owl, duck, ant, rat, ox, shark, crab, worm, moth, yak, teddy (bear), snail, seal, mouse, snake, skunk, hawk, ladybug, mole, crow, pug, deer, whale, fly, beetle and mice (three little mice in a row), and with the big words bobcat, catfish, starfish, chipmunk, puffin, robin, lobster and hedgehog. Each belongs to the word that brings it; the teddy belongs to hug and the ladybug to bug.

### Toys

Twenty-nine. Nineteen word toys: drum, kite, spinning top, stacking rings, toy cap and dress-up wig (both worn on the head), butterfly net, bowling pin, sock puppet, doll's crib, toy car, van, jet, bus, ship, train and truck, doll, and an ABC block. Five counting toys (shape sorter, dice, abacus, dominoes, balance scale) and five thinking toys (puzzle pieces, bead maze, puzzle cube, stacking cups, toy robot) are earned in Numbers and Logic.

Each toy makes its own noise when right-clicked (a toot, a ding, a squeak, a whirr). Played (once a second at most), saying its word as it goes: the car zooms a little loop on turning wheels and parks, the van honks and bounces, the bus beeps and rocks side to side as its wheels roll, the truck rumbles and its dump bed tips up and back down, the train chugs forward and back puffing smoke, the jet takes off with its propeller whirring, circles overhead and lands, the ship bobs on the waves, the top spins and wobbles to a stop, the drum bounces on each beat, the kite flies up on its string and settles back, the stacking rings come off the post one by one and go back on, biggest first, the bowling pin tips over and springs back, the doll dances, the ABC block tumbles, the sock puppet's mouth flaps, the crib rocks to a lullaby, the net swishes and catches a butterfly, and the cap and wig spin on their stands. Each ends exactly where it began.

### Toys together

A toy or plushie held and used on another goes in, on or onto it: the doll or any plushie into the crib (tucked in), toys and plushies into the box, a plushie into the bathtub (where it floats and bobs), the little critters (ant, ladybug, worm, mice, fly, beetle, snail, moth, crab) into the jar or the mug, anything small into the bin or onto the desk; a plushie or the doll rides the car, van, bus, truck, train, ship or jet, and goes with it when it plays; the cap or the wig goes on a plushie or the doll. One thing at a time. Sneak and right-click with an empty hand to take it back out; break the toy and both drop. What is put there stays through a reload or a restart.

### Props and picture cards

Props are things words need that Minecraft hasn't got and that aren't toys, plain objects to put down, not collected: a bin, a bathtub, a cardboard box, a glass jar, a desk fan, a mop, an ear of corn, a mug, a school desk and a fork. A key is Minecraft's trial key. Toys, plushies and props all break at a punch outside the classroom.

A word with no item at all can have a picture card, `"picture_card": "<name>"`: a small pixel picture (`textures/card/<name>.png` in the mod) on a panel beside the word, drawn by Pandorical. The moon, a sad face, a glad face, a mouth and a town have one, and so do night, day, dark, dusk, dawn, mad, cold, nod, wave and hide, and the big words sunset, cupcake, popcorn, rainbow, snowman, pancake and sandbox. Only players with Pandorical see a card, and it is gone with the round.

## Relations: what goes in, on and under what

One set of rules says what goes in, on, under or onto what, for Bob, the pictures, the toys in the world and the situations: `data/<namespace>/learning_relations/<thing>.json`, one file per thing. Bob's goals are the 28 written ones plus every sentence the rules make with a small thing and a thing that takes it ("put doll in crib", "put owl in car", "put duck in tub", "put cap on owl", "put ant in jar"): about 950 in all, of which a child is only given ones whose words they read (about 140 at stage 5, 320 at stage 8, 680 at stage 12). The shipped files are generated; see DEVELOPMENT.md.

Bob's literal rules: something put in a creature goes on its head instead, a creature on a creature rides it, and nothing goes in a container with its lid on.
