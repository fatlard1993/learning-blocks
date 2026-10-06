# Learning Blocks

A server-side Fabric mod of learning games for young children, in a quiet classroom dimension of their own, on an ordinary survival server. It has three subjects: **Reading** (letter sounds, words, sentences, big words), **Numbers** (counting, quick looks, comparing and order) and **Logic** (sorting, patterns, odd one out, what's missing, putting things in order). Everything is spoken, so a child who can't read yet can play; the voice, captions and screens are English only.

The classroom, Subjects, Numbers and Logic are about all three subjects. The puzzle through Stages are Reading's. From Prizes on, everything is shared again, except Words, which is Reading's data format.

## Install

Put the Learning Blocks jar on the server. It also needs these two mods, on the server **and** on every player's game:

- [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
- [Pandorical](https://www.curseforge.com/minecraft/mc-mods/pandorical)

The Minecraft, Fabric Loader and Java versions it targets are the ones in this mod's `gradle.properties`. Players need nothing else: Pandorical brings the tiles, pictures, captions, screens and voice from the server. A player without Pandorical gets none of these.

Children get in from the action menu. Press **J** (Pandorical's "Open action menus" key, which can be changed in Controls) and click **Learning Blocks**, or type `/learn`.

**Offline-mode servers.** With `online-mode=false`, the server does not check names. Anyone can join under an operator's name or a child's, and take over their commands, progress, things and prizes. Install an authentication mod (one that makes each player set a password) before children play on such a server.

## The classroom

Press the **Learning Blocks** button on the action menu (or type `/learn`) and you are taken to your own classroom: a small room with a green chalkboard, open to the sky: a clear noon, unless a word says otherwise, and every child's sky is their own (one child's night or rain is nobody else's). Every child gets a room of their own, far from everyone else's, so nobody's reading is interrupted and nobody sees anybody else's.

Nothing in the classroom can get hurt and nobody gets hungry. It's survival, so that letter tiles can be knocked loose and set down the ordinary way, but that is all: the only blocks that break are letter tiles, a tile can only go in the word's gap or on the floor, nothing else can be placed, and nothing a word puts in the room can be opened, slept in or taken. You can't fall out of it either: step off the edge and you're back on the floor.

What you were carrying is put aside on the way in. Your things, your experience, your game mode and any potion effects still running are put aside on the way in and handed back on the way out, exactly as they were. Nothing from the classroom comes home with you.

To go home, walk through the portal behind you: no clicking, just walk in (facing it, or any way round as long as you walk in and stand in it for a second; a push from a word doesn't count). While lessons keep a child in, the portal is dark with a soft wall across it and the number left over it, and walking into it says how many are left; when the last one is done it lights up with a whoosh and swirls. **Go home** on the action menu (or `/learn leave`) is the other way out, and says the same count while lessons are left. Clicking a creature or a villager in the classroom never opens another mod's dialogue or a trade.

Nobody gets stuck. A child free to go who has tried to leave three times in thirty seconds and is still in ten seconds later is sent home anyway, the same safe way, and the server log says `STUCK:` with what the room was doing. So is a child kept for lessons when the room has had nothing to ask for ten seconds (the lessons are let go).

## Subjects

Reading, Numbers and Logic share one classroom, one voice, one Toybox, one prize system and one daily schedule; each has its own rounds and keeps its own progress. A child's rounds take turns among their subjects, and when the subject changes the room says so ("Numbers time!", "Let's read!", "Thinking time!") and the top edge of the board turns the subject's colour: blue for reading, green for numbers, purple for logic. A child with only one subject on keeps the plain wooden edge.

- **Numbers**: see Numbers below.
- **Logic**: see Logic below.

A wrong pick says what it was and asks again; a right one counts like any round: towards mastery (the same clean-days rule as words), prizes (a new item pays `new_word`, `solved` and streaks count), a lesson off, and the day's count. A toy can be earned by mastering items of any subject.

Number tiles are wooden blocks like the letter tiles, with a numeral (0 to 20) front and back and the same amount in dots on the sides: a die's pips up to six, a ten-frame up to ten, two ten-frames past that. A right-click says the number. There are symbol tiles too, plus, minus and equals (equals is "they're the same" in the pens; plus and minus are for adding and taking away, later).

## Numbers

Counting to 20, adding and taking away within 10 and number sense, for about 4 to 7; this much is the first three stages, each in two bands, like Reading's. A band is finished when its numbers are mastered (the same clean-days rule as words), with "ready for more" and the `stage_complete` prize once a stage is done. Most rounds are the band's main kind; every third is one of its extras; now and then something from an earlier band comes back for review. Answers are picked by clicking or punching: number tiles on the floor, or the pens, towers or pictures themselves.

- **N0, numbers 0 to 10** (1 to 5, then 0 and 6 to 10). "Which block says... five?": three tiles; the right one starts the chant, "five! one, two, three, four, five!", while five things pop out of the button, each glowing as it is counted; another says its own name, gently. Quick looks: a die's face or a ten-frame of dots flashes on the board for two seconds, "How many?". A number's name, read: the word ("six") in letter tiles, pick its tile; only for number words the child has read in Reading.
- **N1, counting to 10, with real things.** "How many pigs?": creatures pop out and stand in rows of five; on the answer each glows in turn as it is counted. "Make five!": put a tile in the gap and press the button, and exactly that many come out (too many: that many chickens, all at once, "Whoa, that's too many!"). "Every kitten needs a bed!": how many more beds? and each kitten is tucked in. Counting their own toys or plushies from the Toybox, which makes collecting matter. Scenes: "The three pigs are hungry, but there's only one carrot!", "Each villager wants a cake!". Bob with number cards: "put 3 apples in box", and he does exactly that (nine apples spill over; nine cats on the bed are a cat pile); What's wrong?: Bob put four apples in, the goal shows three, fix the card. Reading together, a team-up: "You count these, and you count those! How many altogether?"
- **N2, comparing and order.** "Which pen has more?" (or fewer, or are they the same?): two pens of creatures and the equals tile. Towers of wooden blocks: which is taller, or shorter, and "How many more blocks to make them the same?". A number line: 1 to 5 (then 1 to 10) jumbled on the floor, put in order on the board. What comes after, or before. The numeral on the board and three pictures: which has that many? Bob: "put more apples in big box".

Toys earned by mastering numbers: the shape sorter (every numeral 0 to 10), the dice (quick looks 1 to 6), the abacus (counting 1 to 5), the dominoes (counting 6 to 10) and the balance scale (more, fewer, the same, taller and shorter). The grown-ups' view has a Numbers section: the band, how many numerals, counts and comparisons are mastered, and the numbers mixed up most (6 and 9, 2 and 5).

## Logic

Thinking for about 4 to 7: matching and sorting, patterns, spotting what's odd or missing, and putting things in order; this much is the first four stages, each in two bands, mixed and mastered as Numbers is.

- **L0, matching and sorting.** "Put the animals in their pens!": two pens, one already in each to show what goes there, and a row of mixed ones out of the button; click one, then its pen, and it hops in (the wrong pen, and it hops back). By kind (pigs and cows), by colour (two colours of sheep, each pen's rug its colour), by size (grown-ups and babies), and toys into the box, plushies into the crib. Same or different: two pictures on the board, a tick tile and a cross tile.
- **L1, patterns.** What comes next: a row of coloured blocks (or plushies) and a gap, AB, AAB, ABB, ABC, and growing ones (one block, two, three...). Find the mistake: click the one that's wrong. Copy it: build the same row on the floor (reading together, a team-up: one builds the first half, the other the rest). Notes: note blocks play high, low, high... and two bells, a little one and a big one, for what comes next.
- **L2, odd one out and what's missing.** "Which one doesn't belong?": three pigs and a cow, three red sheep and a blue one, three animals and a toy; the right one waves bye and goes. "What's missing?": a few things on the board, "Look carefully!", the lights go out for a blink, and one is gone.
- **L3, sequencing.** First, next, last: picture cards in order on the board (a seed, a sprout, a flower; an egg, a chick, a hen; day, dusk, night, and then the child's own sky does it). Little to big. A scene: "He's all mixed up!", put his morning in order.
- **Bob**, all through: "put all the pigs in the pen", "put the big cat in the box", "put the cat left of the box" (the child's left), with word cards; and What's wrong: Bob sorted one animal into the wrong pen.

Toys earned by mastering logic: the puzzle pieces (sorting), the bead maze (patterns), the puzzle cube (odd one out and what's missing), the stacking cups (little to big) and the toy robot (putting things in order). The grown-ups' view has a Logic section: the band, what's mastered of each part, and what they find hard.

Which subjects are on, and how they mix, is in `prizes.json` under `subjects` (all on by default) and on the prize editor's General tab; `/learn subjects` sets one child's own. Mixes: `alternate` (one round of each in turn, skipping any with nothing to ask; the default), `blocks` (`block` rounds of one, then the next), `weights` (each by chance, as often as its weight). A daily schedule can ask for some of each (`/learn daily Sam 10 reading 5 numbers 5`); until a subject's share is done, only the subjects still owed are asked. Reading together, each turn is from the turn reader's own subjects.

## The puzzle

On the chalkboard is a word with its first letter missing, like `_ a t`, and beside it a picture of the word it wants (click the picture to hear the word again). On the floor are a few letter tiles.

- **Break a tile** (hold left-click, it takes a moment) and it drops; walk over it to pick it up.
- **Place it in the gap**: right-click the underscore at the back of the gap with the tile in your hand. Placing into a filled gap swaps the tiles; the old one flies back to its place on the floor. A tile set down anywhere else flies straight back to its place too, so the floor never gets muddled.
- **Press the button** and the room sounds out each letter in turn, lighting its tile as it says it, then says the whole word, and then the word happens: it comes out of the button, a creature hopping out, an item thrown to your feet, a hat flying onto your head, a bed flying to its spot, a trail of sparks for anything that happens to you.

Every letter says its sound when you touch it: hit it, break it, place it, or right-click it with an empty hand. A tile says its own sound (the short one, for a vowel: a as in cat); a letter in the word row says it the way the word does. When the room sounds a word out, each vowel is the very vowel from that word's own recording, so the a in cat is cat's a; consonants are the ordinary letter sounds. After a wrong word, the tile in the gap flies back to its place so you can try again. Tiles dropped or still in your pocket are tidied up for the next round, and any tile that goes missing comes back to its place.

Moving up a stage is a clean slate: the pack is emptied, creatures and anything the words put down are cleared, and the room says "Wow! You're ready for new words!" before the first new word. The room asks for one word, out loud, and asks again if you go quiet for a while. Make it and the room cheers, and a new word comes along. The other tiles make real words too, and they happen as well, only sillier: build hat when the room asked for cat and a hat flies over and bonks you on the head; build ten and the button throws ten snowballs at you; build pig and a piglet sneezes. Creatures in a wrong answer are silly, never unfriendly: they chase their tails, bounce, sneeze or wander off the wrong way and bonk into the wall, and nothing runs away from the child. A wrong answer is still something to read, and it never hurts and never lasts.

Everything the room says is spoken, in one voice (and the lines said most often have several versions, so "Well done!" isn't the same every time), with big captions that light up each word as it is said, so a child can follow along before they can read the whole line. A line too long for the window wraps onto a second line (or a third), still lighting word by word, so nothing runs off the edges at any caption size or GUI scale. Nothing goes to chat. Each player can turn captions off, make them smaller, or move them from the top of the screen (clear of the board and the tiles) to raised or the bottom, on Learning Blocks' page of Pandorical's settings.

## Situations

Once a child has read a word, it can come back as a little problem instead of "Can you make...?". There's no picture and no word said: the room acts something out ("Oh no! He's getting wet!", a villager under a rain cloud), asks "What can help?", and offers the usual letters. The right word fixes it (the hat flies onto his head and the cloud drifts away) and the room says "That helped!". A wrong word does its usual silly thing while the rain keeps falling, and they can try again. Clicking the character asks again.

The situations: hat (rain), lit (the room goes dark), bun (a rumbling tummy, crumbs, and hungry looks at the button), cot (a sleepy villager), hot (a shivering villager in the snow; a campfire comes to warm him), map (a lost villager pacing about), bin (litter on the floor), hug (a sad kitten), fan (a hot villager), cup (a thirsty villager by a cauldron), sit (two pups bouncing about; sit calms them), fed (a hungry pig, cow and sheep), hop (a cake behind a little fence; hop gives the jump to get over it), cut (a villager tangled in vines), wet (droopy flowers that bloom once watered), hum (a baby villager who can't sleep), nap (a tired dog), hut (a villager blown about by the wind, leaves flying), mop (a puddle), rod (a villager by a pond who wants to go fishing), vet (a puppy that needs an animal doctor, and a cleric comes), tug (a villager stuck in the floor), pop (a pinata to burst, only with the pinata mod installed), ship (a villager who can't get across the water; a boat comes), fish (a hungry kitty), bone (a puppy that wants something to chew), milk (a thirsty baby villager), hay (a hungry foal), tree (a parrot with nowhere to sit; a little tree comes), sheep (a villager who needs some wool; a sheep comes), night (a sleepy villager who can't sleep in broad daylight; night falls and a bed comes) and snow (a villager who wants to build a snowman). The character's feeling shows in a little bubble over its head (a sad kitten, a cold villager, a hot one, a sleepy one, a lost and scared one), a villager waves hello when it arrives, and once it is solved everyone is glad and the one it was about nods thank you. One round in four is a situation, once there's a read word with one; operators can turn them off or change how often on the prize editor's General tab (`puzzles` in prizes.json). They count as lessons and pay like any other round, and everything in them is harmless and gone at the next round and on the way out, the dark included.

## Sky, weather and feelings

Every child's sky is their own. A word can turn it to dusk, night (the stars come out and the real moon rises), dawn or day (the sun coming up), and make it rain, drizzle, snow or storm (a rumble and a flash in the clouds far off, never a real lightning bolt), or float a fat white cloud over the room; another child, in their own room, keeps their clear noon. It stays so until the next round, which brings a clear noon back (the sun coming up quickly if it was dark), and going home gives back the world's own sky. Dark puts the lights out for a moment, then clicks them back on.

Feelings show as a little bubble with a face in it over whoever feels them: glad, sad, mad, sleepy, surprised, scared (but cute), in love, cold and hot. Characters, creatures, Bob and plushies have them. Doing words are done by a character standing still to do them: a nod, a shake of the head, a wave (a hand held up), a bow, a dance, ducking down, a jump, a spin, hiding behind a bush and peeking out, a shiver, a stomp; each puts it back exactly where it was.

## Reading together

Two (or three) children can share one classroom. `/learn join <player>` asks to learn with someone who is in their classroom. They hear "Somebody wants to learn with you!" and get a notice with **Accept** and **Deny** (a minute to answer; a child can ask again after half a minute). An accept brings the asker in by the ordinary way (their own things put aside, their own lessons and prizes) or straight across from their own classroom. Operators can `/learn pair <a> <b>` and `/learn unpair <player>`, and `/learn daily <reader> with <other>` (or `with nobody`) pairs two for their daily lessons: whoever arrives second goes into the first one's room.

Rounds take turns. Each is picked for the reader whose turn it is, from their own stage band, mastery, review and today's lessons, and starts with "Your turn!" and their name in the caption; their gap's underscore is in their colour (blue, orange, green) and a little arrow in the same colour floats over them. Only they can put a tile in the gap, press the button or pick a picture: the other can knock tiles loose, carry and drop them, and hear them, but a tile aimed at the gap stays in their hand and they hear "Wait for your turn!" with "It's Max's turn!" written. Everything counts only for the reader whose turn it is (mastery, lessons, the daily count, prizes, which fly into their own chest: each reader has one beside the portal), and everybody hears and sees everything.

One round in three (`team_up_every`, and `team_up` to switch it off, on the prize editor's General tab) is a team-up: "Let's do this one together!", a word from the lower reader's band, its first sound that reader's gap and its last the other's, each in their colour. Each puts in only their own; either presses the button. Right: "Great teamwork!", the word counts for both, and each for their own tile; built wrong, the miss is quietly the one who put the wrong tile in.

The portal is each reader's own: one whose lessons are done walks out, and the others carry on (the room passes to them if it was the leaver's); one still kept is turned back from it, sees their own frame dark, and the count over it names them. A disconnect is one reader's, as ever.

## Tricky words

Some words can't be sounded out: the, I, to, no, go, he, she, we, me, be, was, you, they, my, said, are, of. From stage 2 they open a few at a time (the, I and to first; are and of at stage 7), and one round in six is one of them, the fewest-practised first. The word is on the board and the room says it, with a star in the caption instead of sounds, since there is nothing to blend. On the floor stand two or three word cards, and the child right-clicks or punches the one that matches. The right one: the word again, a sparkle, and on to the next. Another: that card says its own word and wiggles. They are mastered like any word (three clean picks), and have their own **Tricky** tab.

## Big and little letters

Once a letter's sound is mastered in the letters level, its capital comes in too, both ways round: the board shows a and asks "Which one is the big... A?", with three capitals on the floor, or shows A and asks for the little one. The other tiles are letters the child knows, and never b with d, p with q, m with w or n with u until both are solid. Right: the letter's name, twice, with a sparkle running between the two tiles ("A... a!"). Wrong: the tile says its name and flies home. Each letter's pair is mastered after three clean matches, either way round, and a group (and the letters level) isn't done until its sounds and its pairs both are. After the letters level, a big-and-little round comes up now and then as review. The Letters tab has an **A B C** button to show the capitals, with each pair's matches as pips, and the progress screen shows each pair's mastery.

## Plurals and phrases

After stage 5 (and past every stage), one round in eight is a plural of a word the child has read: cat with a gap before it and a gap after, and on the floor the first letters of the family (c, b) and an s. Cats: three cats. Bats: three bats. The s says /s/ in cats, bats and pups, and /z/ in dogs, pigs, hogs, hens and cubs. Only creatures have plurals.

One round in seven is a two-word phrase: an adjective the child has read (big, red, wet, fun, six, sad, glad, mad) above a creature word to build. The button reads the adjective, sounds out the noun, then says the two together, and the phrase happens: big pig is one big pig, six cod is six cod, red makes a sheep red (anything else gets a red puff), wet drips, fun makes it hop with sparkles, and sad, glad and mad put a face over it (a sad pig in tears, a glad dog hopping, a mad cat huffing). Building another creature word of the family is friendly: its own phrase happens instead (big hog in a big pig round); any other word does what it always does (big makes you grow). Building bats in a cats round brings three bats. Only pairs the room can show are offered, and only once both words have been read.

## Plushies and toys

Thirty-four soft toys, each a word Minecraft has no creature or thing for: owl, duck, ant, rat, ox, shark, crab, worm, moth, yak, teddy (bear), snail, seal, mouse, snake, skunk, hawk, ladybug, mole, crow, pug, deer, whale, fly, beetle and mice (three little mice in a row), and with the big words bobcat, catfish, starfish, chipmunk, puffin, robin, lobster and hedgehog. They are chunky, felt-coloured, big-eyed, and about half a block tall. Put one down anywhere on the server and it faces you; right-click it and it squishes down and bounces back up, squeaks, puffs a heart, shows a little loving (or glad) face and says its word, with a caption; one punch breaks it (like a torch), and it comes back as itself.

In lessons they stand in for the animals: reading owl brings an owl plushie popping out of the button, a lesson prop that is cleared at the next word (not a prize); built wrong, it pops out and you spin round. Bug is the ladybug now, and hug brings the teddy as well as the hearts.

Twenty-nine toys, the second family to collect. Nineteen are word toys, in the same chunky style: a drum, a kite, a spinning top, stacking rings, a toy cap and a dress-up wig (both worn on the head), a butterfly net, a bowling pin, a sock puppet, a doll's crib, a toy car, van, jet, bus, ship, train and truck, a doll and an ABC block made of the same wood as the letter tiles. Five are counting toys and five are thinking toys, earned in Numbers and Logic (see those sections). Put one down and right-click it, and it makes its own little noise (a toot, a ding, a squeak, a whirr) and says its word. Reading a toy word pops the toy out with its noise and hearts, a lesson prop like the plushies; built wrong, it pops out and you spin round. Cap and wig put the toy on your head; ship still gives a boat to ride, and jet still sends you up.

**Earned by mastery.** Every plushie and toy belongs to the word (or words) that bring it: the owl to owl, the car to car, the teddy to hug, the ladybug to bug. When the last of them is mastered (clean on three different days), the child earns it: it flies into the prize chest with "Ooh! You got a new plushie!" or "Ooh! A new toy!" and comes home the usual way, once per toy, ever. This is the `toy_mastered` event, on by default (the editor's Events tab can turn it off, or add prizes to it). A reader who had already mastered a toy's words earns it on their next way in. A word file can say `"earns": "teddy"` to choose for itself; otherwise the link comes from the toy its picture or outcome names. The **Toybox** tab (`/learn toybox`) has two shelves, Plushies and Toys: the ones a child has in colour (with how many, if more than one; a click says the word); the rest as "?", with the word that earns it underneath and pips for how near it is to mastered ("Master: hug" in its tooltip); and "Plushies 7 of 34 · Toys 3 of 29".

They can still be prizes. `{ "plush": "random_new" }` gives a plushie the child hasn't got yet (any one, once they have them all), `{ "plush": "owl" }` that one; `{ "toy": "random_new" }` and `{ "toy": "drum" }` the same for toys; and `{ "toybox": "random_new" }` one they haven't got from either, every missing one equally likely. Each prize list on the editor has **Add a plushie** and **Add a toy**, and a row's arrows go through "A new one" and every kind.

**Play.** Every toy, put down anywhere, plays when right-clicked (once a second at most), saying its word as it goes: the car zooms a little loop on turning wheels and parks, the van honks and bounces, the bus beeps and rocks side to side as its wheels roll, the truck rumbles and its dump bed tips up on its hinge and back down, the train chugs forward and back on its wheels puffing smoke, the jet takes off with its propeller whirring, circles overhead and lands, the ship bobs on the waves, the top spins and wobbles to a stop, the drum bounces on each beat, the kite lifts off its reel and flies up on its string, fluttering, its bow-tied tail waving, and settles back, the stacking rings (each a square ring round a hole) come off the post one by one into a pile beside it and go back on, biggest first, the bowling pin tips over and springs back, the doll dances, the ABC block tumbles, the sock puppet's mouth flaps as it chatters, the crib rocks to a lullaby, the net swishes and catches a butterfly, and the cap and wig spin on their stands. Most move as one piece; the truck, bus, rings, sock puppet, kite, jet, car and train move their parts as well; each ends exactly where it began. Toys, plushies and the props (bin, bathtub, box, jar, fan, mop, corn, mug, desk, fork) all break at a punch, outside the classroom; inside it the room's own rules still hold.

**Toys together.** A toy or plushie held and used on another goes in, on or onto it: the doll or any plushie into the crib (tucked in), toys and plushies into the box, a plushie into the bathtub (where it floats and bobs), the little critters (ant, ladybug, worm, mice, fly, beetle, snail, moth, crab) into the jar or the mug, anything small into the bin or onto the desk; a plushie or the doll rides the car, van, bus, truck, train, ship or jet, and goes with it when it plays; the cap or the wig goes on a plushie or the doll. One thing at a time. Sneak and right-click with an empty hand to take it back out; break the toy and both drop. What is put there stays, through a reload or a restart. These are the same rules Bob follows (see Sentences with Bob).

## Props and picture cards

Props are things words need that Minecraft hasn't got and that aren't toys, plain objects to put down, not collected: a bin, a bathtub, a cardboard box (open, its flaps folded out), a glass jar, a desk fan, a mop, an ear of corn, a mug, a school desk and a fork. A key is Minecraft's trial key, so there is no new one.

A word with no item at all can have a picture card, `"picture_card": "<name>"`: a small pixel picture (`textures/card/<name>.png` in the mod) on a panel beside the word, drawn by Pandorical. The moon, a sad face, a glad face, a mouth and a town have one, and so do night, day, dark, dusk, dawn, mad, cold, nod, wave and hide. Only players with Pandorical see a card, and it is gone with the round.

## Big words

Stage 14 is the jump from one-syllable words to book words: compounds first, two words the child already reads (cat|fish, star|fish, bob|cat, hedge|hog, sun|set, cup|cake, pop|corn, rain|bow, snow|man, pan|cake, sand|box, bath|tub), then words of two syllables split between their consonants (rob|in, puf|fin, lob|ster, chip|munk). A big word is written in chunks: the word row has a column between them, so cat and fish stand apart on the board.

- **A chunk to build**: one chunk is on the board and the other is gaps, with its tiles and a decoy or two on the floor. A chunk with a tile twice (pop, in popcorn) is never the one to build.
- **Both chunks**: every tile a gap, the column still there as a hint (only for words of six tiles or fewer, with no tile twice).
- **Read it**: big words with a clear picture come up as read-it rounds like any other.

The button sounds out each chunk and says it as its own little word ("c... a... t... cat. f... i... sh... fish."); built right, the chunks then slide together, the column closing, and the room says the whole word: "catfish!". A wrong build is sounded out the same way, chunk by chunk, and goes home as usual. The eight animals bring their plushie (and join the collection); sunset, cupcake, popcorn, rainbow, snowman, pancake and sandbox have picture cards; bathtub brings the bathtub. A word too long to leave room for its picture on the left (cupcake, sandbox) has the picture above it, and every row stays clear of the button.

The chunks that are not words on their own (munk, puf, lob, ster, hedge, bob, rob, set, pan, bow, snow) are word files with `"puzzle": false`, so each has its own recording to be said by. A big word's file says `"chunks": [3, 3]` (tiles per chunk) and, for a compound, `"compound": true`; the compounds come first in the stage's bands.

## Read it

From stage 2, now and then (one round in five, `read_it_every`; `read_it` switches it off) a word the child has already read comes back to be read on its own. It is shown whole on the board, with no gap, no picture and nothing said but "What does it say?" (or "Can you read it?"...). Only words whose picture a five-year-old names at once as the word are used (`"picture": "clear"` in the word file: cat, bell, ship, crown...), never a verb or a loosely pictured word (sat, run, jam). On the floor stand two or three such pictures of words that sound alike (cat, bat, hat), or, where a family has too few, other clear words of the stage starting or ending the same, and the child right-clicks or punches the one they think it is. The right one: now the word is sounded out, as a check, and it happens as any right word does. Another: that picture says its own word ("Oops! That's... bat!"), gives a little wiggle, and they try again; nothing cheeky, since this is a check of reading. It counts as a lesson and pays like any round. A situation is never a read-it round.

## Sentences with Bob

From stage 5, one round in five (`tell_bob_every`; `tell_bob` switches it off) is Bob's. Bob is a villager who helps, very literally: he does exactly what a sentence says, which is the whole game (like a grown-up making a sandwich from a child's too-literal instructions). His lines are his own voice, and his captions start with "Bob:".

- **Tell Bob**: framed on the floor to the left is what Bob should make (a cat in a box), and on the floor are word cards: the sentence's words and one or two that make something else (dog, on). Click a card and it flies into the sentence row on the board; click it there and it flies home; each card says its word as it goes. The button reads the sentence back a word at a time, each card lit and the sentence in the caption, then Bob says "Okay!" and does it on his stage. Put cat in box: the cat sits in the box, "Easy peasy!", and it counts like a word. Put box in cat: a box can't go in a cat, so it goes on the cat's head. Put cat on box: the cat perches on top. Put cat on pig: the cat rides the pig. Then "Hmm, that's not quite it!", the cards fly home, and the stage is set back to try again. Sentences come in order: plain ones (put hat on dog), then with big (put big cat in box), then with the and a (put the cat in the box), then two in a row where the order matters (put cat in box, put lid on box: the lid on first, and the cat ends up on the lid). A kind opens once two of the one before are mastered. Only words the child has read are used, and the little words once their stage is reached (put from 5, a from 4, under from 12).
- **What's wrong?**: every fourth of Bob's rounds (`whats_wrong` switches them off), the sentence is already in the row and Bob has already done it, wrong: a wrong thing (put cat in tub), a wrong little word (put cat on box), or the wrong way round (put box in cat). "Uh oh! Something's not right." The child takes the wrong card out, puts the right one in, and presses the button: Bob does it again from the start.
- **Fix the word**: from stage 2, one word round in six (`whats_wrong_every`) has the gap already filled with a wrong letter that makes another real word: the picture is a cat, and the board says cot. "Uh oh! Something's not right. Can you fix it?" The child swaps the o for an a, and it goes on as any word round.
- **Silly sentences**: the **Silly sentences** button on the action menu (or `/learn silly`), from stage 5, gives a sentence row and a floor of cards (put, in, on, the little words the child has, and things they have read), and Bob does any sentence at all, one after another, the stage keeping what is done. Pressing the button again goes back to reading. It is for fun: no mastery, and a lesson only if `silly_counts` says so.
- **Reading together**: Tell Bob is both readers': the one whose turn it isn't picks which of two goals Bob should make ("Ava, pick what Bob should make!"), then the other tells Bob, and a right sentence counts for both.

- **Little words**: every fourth of Bob's rounds, the board shows three words, "owl in box", and the floor two framed pictures of the same two things, one in and one on (or under). The child reads it and clicks the picture it says; the other gets "Hmm, try again". It practises the little words, and is remembered like a sentence.

Bob's world is the same as the child's: one set of rules says what goes in, on, under or onto what (`learning_relations/<thing>.json`, written by `generate_relations.py`), for Bob, the pictures, the toys in the world and the situations. Bob's goals are the 28 written ones and every sentence the rules make with a small thing and a thing that takes it ("put doll in crib", "put owl in car", "put duck in tub", "put cap on owl", "put ant in jar"): about 950 in all, of which a child is only ever given ones whose words they read (about 140 at stage 5, 320 at stage 8, 680 at stage 12). Silly sentences have every toy, plushie and prop the child can read. Crib has a situation now: the baby can't sleep, and the crib comes with the doll tucked in.

Bob waves hello when he arrives, nods as he says "Okay!", shakes his head, surprised, at a sentence that isn't it, and jumps for joy when it is. Goals made from the rules lean towards plushies and toys: one with only props in it (put mug on desk) comes at most one time in five (`bob_prop_share`, on the editor's General tab), and never two in a row.

Sentences are mastered the way words are, clean on three different days, and the progress screen has a Sentences section: each sentence tried, its clean days and misses.

## Remembering, and looking again

The room remembers what each child gets right and wrong: every word and every sound, how often tried, got first time and missed, and the last few things built instead (bad for bed; b picked for d). Every word and sound has a box: a miss puts it in box 1, a first-time answer moves it up, and it comes back after a few rounds in box 1 and rarely once it is solid. About three rounds in ten (`review_share`, 0 to 90) are a word, or in the letters level a letter, that is due for another look, rather than the usual pick; the rest are as before. The sounds a child mixes up are counted in pairs (b and d), for the progress screen. On My words, the Letters tab and the Two-letter tab, what is due for another look and still shaky (missed lately, or not yet got first time much) gets a small gold star, with "Let's practise this one!"; nothing a child sees counts what they got wrong.

## Hearing it again

One screen opens from anywhere, the overworld included, from the **Letter sounds** and **My words** buttons on the action menu (or `/learn sounds`, `/learn vowels`, `/learn words`, `/learn toybox`), with tabs across the top: Letters, Two-letter, Vowels, Tricky, Toybox, My words, and for a child with Numbers or Logic on, **123** and **Logic**. Click anything to hear it, just to you, with what was said written along the bottom.

- **Letters**: all 26 letters, drawn like the tiles (vowels red, consonants blue), with a green tick on each one a child has learned in the letters level. Click a letter for its sound, or the **Name** button under it for its name ("bee" for b; a vowel's name is its long sound). **A B C** shows the capitals instead, each with its big-and-little matches as pips; clicking one says its name.
- **Two-letter**: every two-letter tile, to hear, with a word that uses it, and a tick once a child has learned it (by reading words with it, first try, a couple of times).
- **Vowels**: a, e, i, o and u, each with a short button (the sound, then the vowel's keyword from the letters level, apple, egg, ink, on or up, with its picture) and a long button.
- **Tricky**: the tricky words open at your stage, to hear, with their clean picks as pips, and how many more are to come.
- **Toybox**: the plushies and toys earned (see Plushies and toys).
- **123**: the numbers 0 to 20, each with its dots; a click says it, and a star marks one mastered.
- **Logic**: each of Logic's games (pens, colours, what comes next, odd one out...), with a star for each part of it mastered; a click asks its question.
- **My words**: every word you've read, by stage band (1a, 1b...), with its picture. The words still to find in the stage you're on show as question marks, so you can see how many are left without being told what they are. The prize chest's screen has a **My words** button too.

Operators also get **Phrases**, every recorded line the room says, with its caption and length, and can see every word the server has (`/learn words all`, or the button on the screen) or another player's (`/learn words <player>`). Opened on another player, every tab shows that player's progress, with their name in the title.

## Letters first

A new reader starts on the letters level, stage 0, before any words. The room plays a sound ("Which letter says... /m/?", or "Can you find the letter that says..."), and there's one gap on the board, no picture, and three letter tiles on the floor (four later on). Right-clicking the gap's underscore asks again. The right letter gets the alphabet chant, its sound twice and then its keyword ("m, m, map!"), and the keyword happens; another letter just says its own sound, goes back to its place, and the sound is asked for again, with nothing silly and nothing that feels like a mistake.

Letters come in the usual groups: s a t p i n, then m d g o c k, then e u r h b, then f l j v w, then x y z (q waits for "qu"). The other tiles are always letters already met. A letter is mastered after three clean matches (`mastery_clean`, the same count as for words); when a group is all mastered the next one opens, with earlier letters mixed back in now and then. When every group is known, the reader moves on to stage 1 with the usual fresh start and "You're ready for new words!". Keywords are the words that start with each letter (bat, cat, dog, hat, kid, web, yum...), plus keyword-only words for the rest: apple, egg, gold, ink, jump, nest, on (a lamp lights up), rabbit, sun, turtle, up, villager; zap's lightning is a little one, and quiet; x ends its keyword, fox. Two tiles that say the same sound (c and k) are never on the floor together, since either would be right.

Blending starts early. Once s a t p i n are known, one round in three (`letter_word_every`; 0 turns it off) is a word made only of letters the child knows, asked by its first sound with three tiles, all known: sat, sit, sip, tap, tip, pat and nap to begin with, and more as more letters are learned (map, cat, dig, kid, dog...). Reading one counts like any word, but the level is still finished by knowing every letter.

`letters_first` (on by default, in `puzzles` in prizes.json and on the prize editor's General tab) turns it off for new readers, and `/learn stage <player> 1` skips it; `/learn stage <player> 0` starts the letters again.

## Stages

Words come a short vowel at a time, the order reading is usually taught in, and a child moves up a stage once they have mastered every word in theirs. A word is mastered after three clean completions (`mastery_clean`, 1 to 10, on the prize editor's General tab): right first time that round, with nothing wrong built or picked before, in any kind of round (a situation or a read-it round too). A miss neither adds nor takes away. Within a stage the room asks for the words with fewest clean completions first, and never the same word twice running; a mastered word leaves the usual rounds and comes back only now and then for review (and in read-it and middle-vowel rounds, which mix in earlier stages), and a slip on a mastered word brings it back sooner without unmastering it. Mastering a word gets a sparkle over its picture and "You've got it!"; the new stage's "You're ready for new words!" and fresh start come when the last word of a stage is mastered, and `stage_complete` pays then. Words a missing mod leaves out don't hold a stage up, and `/learn stage` still moves a reader. Readers from before mastery keep the stage they had reached, with the ones before it counted as done. Past the last stage, every word is open. My words shows each word's clean completions as little pips, with a gold border once mastered.

A child's day has no repeats: a word asked once is not asked again that day while their band has another. Once every word of the band has had its turn, the room fills in, first with the same words in a kind of round they haven't had today (the last sound swapped instead of the first, the whole word, read it, a situation), then with something from before due for another look (a word, then a big-and-little pair), then a situation or phrase of words already read, and only after all that the same word again, never twice running. Leaving and coming back the same day doesn't start it over; the day is the schedule's (`timezone`).

Only one clean completion a day counts towards mastering anything (a word, a letter, a two-letter tile, a big-and-little pair, a tricky word): mastered means clean on three different days. More rounds the same day are practice: they still count as lessons, for review and for the daily schedule. So a band takes at least three days; at ten lessons a day, a band of up to ten words takes three, and a bigger one about its words times three over ten (1c's 15 words, five days; 8a's 12, four). The whole Reading course is then at least about 95 days at ten Reading lessons a day, after the letters level. With all three subjects on and taking turns (the default), only about one lesson in three is Reading, so expect roughly three times as long, or give Reading its own share of the day (`/learn daily Sam 10 reading 6 numbers 2 logic 2`).

Long stages come in bands, so the gate (and "You're ready for new words!", and `stage_complete`) comes every 4 to 15 words rather than every 24. Within a stage, words are ordered by the latest letter group they need (s a t p i n; m d g o c k; e u r h b; f l j v w; x y z; then two-letter tiles). Stages 1 and 2 split by group: 1a is words of s a t p i n alone (ant, sat, tap, pat, nap), 1b adds the second group (cat, map, man, tag), and 1c is the rest; a band of fewer than four words joins its neighbour, so stage 2 is 2a (pin, sip, sit, tip, pig, dig, kid) and 2b. Any other stage that would take more than 45 rounds at mastery is split in two halves in the same order. A band only limits which words are asked for: the tiles on the floor still come from the whole stage's families, so a 1a round (_at: sat) still offers c, b and h. The bands are shown as 1a, 1b, 1c on My words and the progress screen, and `/learn stage <player> 1b` puts a reader at one.

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

The table is the shipped words with no optional mods installed (cone, poop and the like join their band where their mod is); a different `mastery_clean` changes which stages are long enough to split. The letters level takes about 75 clean matches, and as many again for big and little letters.

Some tiles are two letters that make one sound: sh, ch, th, ck, qu, ll, ss, ff, zz, gg, ng, nk, wh (whale) and le (beetle); the vowel teams ee, ea, oa, ai, oo, ie, ow (as in cow), ou, ay, oi, oy and aw (hawk); the r-controlled ar, or, ur, ir and er; and igh and dge (hedge), three letters on one tile. A word can have a tile say something else with `sounds`: crow's ow says /oh/, fly's y says its name, mice's c says /s/, worm's or says /ur/. Each is a tile of its own with both letters on it, blue for consonants and red for vowel teams. A word like ship is three tiles, sh-i-p, and sounded out as three sounds.

Stage 9 is magic e: the e on the end says nothing and makes the vowel before it say its name (c-a-k-e, cake). Sounding one out, the vowel says its long sound, shown in the caption as its capital (c · A · k · e); at the silent e, the e lights up together with its vowel and a sparkle runs from one to the other. Stage 10 is the vowel teams (b-oa-t, sh-ee-p).

Rounds come in four kinds. At first the first sound is swapped (_at: cat, bat, hat). Once most of a stage is read, the last sound is too (ma_: map, mat, man). From stage 2 the vowel in the middle is swapped as well (c_t: cat, cot, cut), with words from every stage read so far, since it needs more than one vowel. A kind that has nothing new to offer waits, and a word only one kind can reach (web, in we_; chop, in ch_p) is always reached. From stage 6 there are whole-word rounds too: every slot is a gap, and the floor holds the word's tiles and a decoy or two the child knows; a row not yet full only gets "hmm", and anything built is sounded out as built. Words with no family (mud, yes, most of stage 8's blends) are asked this way. Each kind can be switched off on the prize editor's General tab (`first_sound`, `last_sound`, `middle_vowel`, `whole_word` in `puzzles`).

| Stage | Families | Words, and what they do |
|-------|----------|-------------------------|
| 1, short a | -at -ag -ap -an -am | **cat** a cat, **bat** a bat, **hat** a hat on your head, **mat** a mat at your feet, **sat** a cat sits by you, **pat** a sheep, and hearts, **bag** a bundle, **tag** a name tag, **map** a map, **cap** a toy cap on your head, **tap** tap, tap, tap, **man** a villager, **fan** a desk fan and a gust that pushes you back, **zap** a lightning rod struck by a little harmless bolt, **yap** a fox cub yapping, **nap** a lullaby, **ram** a sheep, **ham** a porkchop, **jam** sweet berries, **wag** a dog of your own, wagging, **rag** a white rag, **van** a toy van, **pad** a lily pad, **wax** honeycomb |
| 2, short i | -ig -in -it -ip -id -ix | **pig** a pig, **big** you grow for a few seconds, **dig** a shovel and a burst of speed digging, **jig** a dance, **bin** a bin, **fin** a tropical fish in a bucket, **win** a firework and a cheer, **lit** a torch, **sit** a dog sits, and every pet in the room sits too, **hit** a target, **zip** a dash forward, **sip** a drink, **tip** a bucket tipped out, **rip** paper ripped, **kid** a villager child, **lid** a trapdoor lid, **hid** you hide in a puff of leaves, **six** six carrots, **fix** a fix and a cheer, **mix** potions mixed |
| 3, short o | -og -ot -ox -op -od | **dog** a dog of your own, **log** a log, **jog** speed, **hog** a pig, **pot** a flower in a pot, **cot** a white bed, **hot** a campfire beside you, **fox** a fox, **box** a cardboard box, **hop** jump boost, **pop** bubbles pop, **mop** a mop, **top** a spinning top, **rod** a fishing rod, **cod** a cod flopping about |
| 4, short u | -un -ug -ub -up -um -ut | **bun** bread, **run** more speed, **fun** a firework, **rug** a rug at your feet, **hug** hearts, and every creature in the room hops over to you, **cub** a polar bear cub, **tub** a bathtub, **bus** a toy bus, **pup** a puppy of your own, **cup** a bottle, **sun** sparkles and a sunflower, **yum** a cookie and a burp, **hum** a little tune, **gum** a slime ball, **bug** a bee, **tug** a lead, and you're pulled towards the button, **cut** shears, **hut** a tiny wooden hut round you for a while, **mud** a block of mud |
| 5, short e | -en -ed -et -eg (and web, yes) | **hen** a chicken, **pen** a book and quill, **ten** ten snowballs, **bed** a red bed, **red** red dye and a puff of red, **pet** a cat of your own that sits by you, **wet** a splash, **jet** a toy jet, then a jump into the air and a slow float down, **leg** leggings on, **beg** a cat sits up and begs, **keg** a barrel, **fed** hearts over every creature, **vet** a cleric who makes every creature feel better, **web** a cobweb, **yes** a villager's "hm!" and a cheer |
| 6, double letters and ck | -ell -ick -ock -iss -ill (and buzz, fizz, puff, pack) | **bell** a bell rings, **well** a well of water, **yell** a cheer that makes every creature jump, **fell** a log rolls by, **kick** a slime ball rolls off, **egg** an egg (and e's keyword), **pick** a pickaxe, **tick** a clock and a tick-tock, **rock** cobblestone, **sock** a sock puppet, **doll** a doll, **kiss** hearts, **hiss** a cat hisses, **buzz** a bee, **fizz** fizzing bubbles, **puff** a puff of cloud, **hill** a grass mound, **fill** a bottle filled with water, **pack** a bundle |
| 7, sh ch th and qu | -ip -ish -ick -ath (and shed, shell, chop, quill) | **ship** a boat to sit in (pictured as the toy ship), **chip** a baked potato, **fish** a fish, **dish** a bowl, **wish** a star firework, **chick** a chick, **quick** speed, **quill** a feather, **bath** a bath of water and a splash, **path** a path block, **shed** a tiny shed round you, **shell** a nautilus shell, **chop** an axe |
| 8, blends | -lock (and whole-word rounds) | **frog** a frog, **swim** dolphin's grace, **spin** you spin round, **grin** a happy face, **drum** a toy drum and a drumbeat, **twig** a stick, **clap** clapping, **flap** a parrot, **snap** a snap, **stop** every creature freezes a moment, **drop** an apple drops, **drip** drips, **trip** a stumble forward, **flag** a banner, **step** a stair, **sled** a sled to sit in, **clock** a clock, **block** an ABC block, **truck** a toy truck, **chest** a chest |
| 9, magic e | -ake -ine -ive -one -ose ... | **cake** a cake (the Let's Cook chocolate cake, with that mod), **cape** a cape on your back, **gate** a fence gate, **bone** a bone, **cone** an ice cream (only with Let's Cook), **rope** a rope ladder (with Better Ladders; a lead without), **rose** a rose bush and hearts, **pine** a spruce sapling, **vine** vines, **hive** a beehive and dripping honey, **dive** a big splash, **cube** an ice cube, **mule** a baby mule |
| 10, vowel teams | -oat -ail (and more) | **bee** a bee, **tree** a little tree, **sheep** a lamb, **seed** seeds, **leaf** leaves, **bean** a magic bean (with Cloud Kingdoms; cocoa beans without), **meat** a steak, **boat** a boat to sit in, **goat** a baby goat, **coat** a coat on, **rail** a rail (Minecart Mania's wooden rail, with that mod), **mail** a mailbox (with Village Mail; a letter without), **rain** rain, **train** a toy train, **pie** a pie (Let's Cook's, with that mod), **food** a roast chicken, **boot** an iron boot, **moo** a calf mooing, **loop** a loop-the-loop spin, **poop** a poop pops out (only with Poopsmith) |
| 11, ng, nk and final blends | (whole-word rounds, mostly) | **sing** a villager sings, **pink** pink dye and a pink puff, **honk** a honk, **nest** a bee nest, **jump** a jump, **lamp** a lantern, **tent** a little wool tent round you, **milk** a bucket of milk, **sand** sand, **mask** a pumpkin mask on, **gift** a present, **raft** a raft to sit on, **melt** ice melts |
| 12, r-controlled | -orn -ird ... | **star** a star, **car** a toy car, **farm** a hay bale and a chick, **horse** a foal, **storm** clouds and rain, **bird** a parrot, **fern** a fern, **herd** three sheep, **horn** a horn toots, **torch** a torch, **dirt** dirt |
| 13, ow ou ay igh oi oy | -ay (and more) | **cow** a cow, **down** you float down, **crown** a gold crown on, **cloud** a cloud (Cloud Kingdoms' with that mod), **house** a little brick house round you, **howl** a wolf howls, **hay** a hay bale, **play** a tune, **clay** clay, **light** a glowing block, **high** up you go, **coin** a gold coin, **joy** hearts and a firework, **oink** a piglet |
| 14, big words | chunks: cat\|fish, rob\|in | **bobcat**, **catfish**, **starfish**, **chipmunk**, **puffin**, **robin**, **lobster**, **hedgehog** their plushie, **sunset** a sunset glow, **cupcake** a cupcake and a happy burp, **popcorn** popping, **rainbow** a rainbow shimmer, **snowman** snowflakes, **pancake** a pancake stack, **sandbox** a puff of sand, **bathtub** a bathtub and a splash |

Added with the plushies, toys and props (each brings its plushie, toy or prop, and a friendly spin when built wrong): stage 1 ant, rat, yak, sad (a picture card); 2 pin, wig; 3 ox; 4 pug, mug; 5 net; 6 duck; 7 moth; 8 crab, crib, glad (card); 9 kite, snake, mole, whale, fly, mice; 10 snail, seal, beetle, moon (card); 11 ring, king (a gold crown), skunk, desk; 12 shark, worm, deer, fork, corn, jar; 13 owl, mouse, hawk, crow, mouth and town (cards).

Added with the sky, weather and feelings (each sky word glides the child's own sky, and a wrong one is a blink of it and back): stage 1 mad (a villager stomps, a mad face over him; card); 3 nod (a villager nods, glad; card); 9 wave (a villager waves hello; card) and hide (a rabbit hides behind a bush and peeks out; card); 11 dusk (the sky goes to dusk; card) and cold (snow falls, and a villager shivers with a cold face; card); 12 dark (the lights go out, then click back on; card); 13 night (night falls, stars and the moon; card), day (night, then the sun comes up; card), dawn (card) and snow (it snows, a real snow; with a situation). Words that are real now: rain rains, storm storms, wet drizzles, cloud drifts a cloud over, moon and star bring the night, sun comes up, sunset turns the sky to dusk, sad, glad and hot bring a villager feeling it (sad bows its head, glad dances, hot sweats).

Gem (an emerald) and kit (leather armour) are kept as word files but are not puzzles: gem's soft g is never taught, and kit is a word many children don't know. They are there to turn back on with `"puzzle": true`.

Everything a word brings is harmless, can't be hurt, and stays in the classroom: creatures are invulnerable and only a few are kept, anything put down goes on open floor away from the board and the tiles and is cleared at the next word, and a grown child shrinks back on the way out if not before.

## Prizes

Every subject can earn things to take home to the main server. Nothing is given inside the classroom: whatever a child earns waits with the things put aside on the way in, a chest by the portal home shows that something is waiting, and on the way out the room says "You earned a prize!". At home they get their own things back first, then the prizes; whatever doesn't fit is dropped at their feet. That holds however the visit ends: the portal, `/learn leave`, logging out and back in somewhere else, or a respawn.

What pays, and what with, is set in `config/learning-blocks/prizes.json`, written with every setting and a comment beside it the first time the server starts.

Prizes are seen arriving. When something is earned, the room says "You earned a prize!" (after the lesson's own lines) as the prize pops out of the button and arcs across the room into the chest by the portal, which pops into place first if it isn't there yet; its lid opens to catch each one, with sparkles and a soft chime, one after another, and a stack shows "×2" for a moment. Experience flies in as a stream of green sparks. Over the chest, a slowly turning display shows the haul so far (up to three things, then "+N"). Opening the chest shows what's waiting (to look at, not to take), with a My words button. On the way out the haul lifts off the chest and goes to the child, and everything comes home with them.

Operators can change all of it in game with `/learn prizes`: a General tab (prizes on or off, the daily cap, points, and the Puzzles settings: situations and how often, letters first and when a letter counts as known, read it, the kinds of word round, team-ups, Tell Bob and how often, what's wrong and how often, and whether silly sentences count), an Events tab with every event's settings and prizes, and Milestones and Thresholds tabs where totals can be added and removed. Every prize list shows its items as icons, with an **Edit** button that puts your own things aside and lays the list out in your inventory: hold whatever should be given, from the creative menu or chests (named and enchanted items keep their names and enchantments), and say done to get your own things back. Loot tables are picked from every table the server has loaded; experience has a row of its own. Every change is written to the file as it's made, so the menu and the file always agree, and a hand edit followed by `/learn reload` shows up in the menu at once. The simple on/off and number settings also appear on Learning Blocks' page of Pandorical's settings screen, under its server heading.

A setting or prize that's wrong, or names an item or loot table this server doesn't have, is skipped and named in the log and in the reply to `/learn reload`; the rest keeps working, and the entry stays in the file for when the mod comes back.

Events, each of which can give points, prizes, or both, and each of which can be switched off:

| Event | When |
|-------|------|
| `new_word` | The first time a child ever reads a word. Once per word, ever, so guessing every letter in turn earns nothing more. Also says "That's a new word!" |
| `solved` | Any word read; `first_try_only` limits it to a word read before any other was built that round |
| `streak` | Every `length` words read in a row; building a different word starts the count over |
| `stage_complete` | Every word of a stage band (1a, 1b...) mastered; pays once per band |
| `milestone` | Totals of different things ever learned: words read, and Numbers' and Logic's things mastered |
| `toy_mastered` | Every word that earns a plushie or toy mastered: the toy, once (on by default) |
| `daily_visit` | The first visit of each real day |

Points are optional and off by default: each threshold pays once, the first time a child's total reaches it. `daily_cap` limits how many payouts a child gets in a real day. Nothing past it is lost: it's owed, and paid on a later day, oldest first, within that day's cap.

A prize is items (`item`, `count`, optional `components`), experience (`xp`), one roll of a loot table (`loot_table`), or something for the toybox (`plush`, `toy`, `toybox`; see Plushies and toys). There are no command prizes.

The file is written the first time the server starts, at `config/learning-blocks/prizes.json`, with a comment over every setting saying what it does; the prize editor keeps the comments when it saves. It holds the whole server config despite its name: prizes, the kinds of round (`puzzles`) and the subjects (`subjects`).

A child's progress (every word read, streak, points, what has already paid) is kept on the player in the world save, and outlives every visit and death.

## Words

Words are data, not code. Each is one file, `data/<namespace>/learning_words/<word>.json`:

```json
{
  "tiles": ["c", "a", "t"],
  "stage": 1,
  "outcome": { "type": "spawn_entity", "entity": "minecraft:cat" }
}
```

`icon` (an item id) is the picture shown over the board while the word is asked for; without it the picture is what the outcome brings (a creature's spawn egg, the item, the block). `sounds`, one per tile, says a tile differently in this word: `"a_long"` for the letter's name, `"silent"` for a letter that says nothing (shown greyed in the caption), `null` for the tile's own sound:

```json
{ "tiles": ["c", "a", "k", "e"], "sounds": [null, "a_long", null, "silent"], "stage": 9, "outcome": { ... } }
```

`picture: "clear"` marks a word whose picture is unmistakably that word, for read-it rounds. `requires` (a list of mod ids) leaves a word out entirely when any of those mods isn't installed (poop needs Poopsmith). A word that only prefers a mod's thing uses a `prefer` outcome, which falls back to vanilla.

`picture_card` names a picture card for the board (see Props and picture cards); `icon` can name a plushie (`learning-blocks-justfatlard:plush_owl`) or a thing.

`tricky: true` makes it a tricky word (with `puzzle: false`): its `stage` is when it opens, and `shown` (a list of tiles, `cap_i` for I) is how the board and its card write it when that isn't its tiles. `plural: true` (with `puzzle: false`) makes it a plural: its tiles are a word's plus `s`, `"z"` in `sounds` for an s that says /z/, and its outcome the creature three times.

`keyword: true` makes a word a letter's keyword too (zap for z): said and done at the end of that letter's chant in the letters level. `puzzle: false` makes it nothing else (apple): never a puzzle, in no family, and never in My words.

`scene` (optional) makes the word a situation, asked once it has been read: `line` (said instead of "Can you make...", and recorded by the sound generator as `scene/<word>`), `setup` (what's in the room) and `solve` (what the right word does). Setup steps (and in a solve, `feel`, `gesture`, `sky` and `weather` too): `feel` (a `face` in a bubble over the character, or every one with `all`), `gesture` (the character does it: `nod`, `shake`, `wave`, `bow`, `dance`, `duck`, `jump`, `spin`, `hide`, `shiver`, `stomp`), `sky` (the child's own sky to `day`, `dusk`, `night` or `dawn`), `weather` (`rain`, `drizzle`, `snow`, `storm` or `clear`), `character` (`entity`, a villager by default, harmless only; `baby`; `act` `still`, `wander`, `shiver`, `bounce`, `stuck` or `hungry` (glancing over at the button); up to three, the first the one the scene is about), `ambient` (particles kept up over the character: `particle`, `at` `over`/`head`/`face`/`body`/`beside`, `count`, `spread`, `every`, an optional `sound` every `sound_every` ticks, and `drift` to float away when solved), `bubble` (a thought over its head: `text`, `color`), `dim` (the room goes dark for the child), `litter` (`items` lying on the floor), `block` (`block` at squares `at` from the scene's spot, `[east, south]` or `[east, south, up]`, beside the character by default, and what it `becomes` when solved), `wrap` (`item`s wound round the character), `ring` (a ring of `block` round the spot), `prop` (an `item` turning on the spot). Solve steps: `give` (an `item` flies to the character, or to every one with `all`, `then` `wear`, `hold`, `eat`, `drink`, `snip` the wrapping, `pull` a stuck one free, or `soak` a puddle), `place` (a `block` flies beside it, `then` `sleep`, `absorb` the litter, or `none`), `burst` (particles and a sound), `bubble`, `light`, `pose` (every character `sit`s or `lie`s down), `visit` (a helper `entity` comes from the button `to` `beside`, `over` or `front`, with a villager's `profession`), `tune`, `effect` (a beneficial effect on the child), `hut`. After the solve the trouble stops by itself, the blocks become what they become, and the character cheers up.

`wrong` (optional, same shape as `outcome`) is what the word does when it's built but wasn't the word asked for: a funnier, harmless version, over in a couple of seconds, with nothing left behind. Without one, or if it names something missing, the word does its ordinary outcome instead.

A puzzle is made from a family of words sharing everything but one tile (cat, bat, hat, mat; map, mat, man; cat, cot, cut), so a family's other words are the wrong answers, and each is a real word that does something too. An outcome is one of:

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

A word naming a mob, item, block, particle, sound or effect that isn't installed is skipped, so a data pack can reach for another mod's creatures without breaking anything when that mod is missing.

A data pack can add words, or replace the shipped ones, by writing files at the same paths. A word added this way has no recording: its caption shows as usual, but where the word would be said the room plays a short chime instead, and its letters are sounded out from the shipped letter sounds.

## Commands

- `/learn`: go to your classroom
- `/learn leave`: come home
- `/learn sounds`, `/learn vowels`, `/learn words`: the letter sounds, the vowels, and the words you've read
- `/learn toybox` (or `/learn plushies`): the Toybox tab, your plushies and toys
- `/learn silly`: silly sentences with Bob, from stage 5 (again, back to reading); the Silly sentences button on the action menu runs it
- `/learn words all`, `/learn words <player>`, `/learn phrases`: every word, someone else's words, and every recorded phrase (operators only)
- `/learn reload`: read `config/learning-blocks/prizes.json` again (operators only)
- `/learn send <player> [lessons]`: send a player to the classroom (operators only). With a number of lessons (solved words), the portal is locked, with the count over it, until they're done; `/learn leave` waits too. If something else takes them out, the lessons are kept and their next login takes them back. Operators are never kept in
- `/learn join <player>`, `/learn accept <player>`, `/learn decline <player>`: ask to learn with someone, and answer (anyone). `/learn pair <a> <b>`, `/learn unpair <player>`, `/learn daily <reader> with <other|nobody>` (operators): see Reading together
- `/learn home <player>`: sends a child home right now, past lessons, the daily schedule and the portal's lock, the same safe way as the portal (their things, experience, effects and prizes back, the visit ended). The lessons left on that visit are cleared; their daily schedule is not, so a day not done sends them in again at their next login (`/learn daily <player> off` stops that). Operators only.
- `/learn lessons <player>`, `/learn release <player>`: how many are left, and clearing them (operators only). These, `send` and `progress` say so when the player named is offline, by the name the server knows them by
- `/learn stage <player> [n]`: show where a player is, or put them at stage `n`, or a band of one, `1b` (operators only). Everything from there up is read again; what it paid stays paid. `1` starts them over. A numbers band, `N0a` to `N2b`, or a logic band, `L0a` to `L3b`, puts their numbers or logic there instead (the bands before it mastered)
- `/learn progress <player>`: a reader's progress (operators only): their stage; every letter and two-letter sound, and every word, coloured not tried / learning / shaky / solid; the sounds they mix up most ("mixes up b and d: 5 times"); the sentences told to Bob, with their clean days and misses; time and rounds this week; and their lessons history. Also from a reader's name on the prize editor's General tab. `/learn progress <player> export` writes the same to `config/learning-blocks/reports/<uuid>-<player>.json`
- `/learn daily <player> [n|off]`, `/learn daily <player> <weekday> <n>` (operators only): a daily schedule, a number of lessons to finish each day before playing, with any day of the week different (weekends 0, say). With no number it shows the schedule and today's progress. A scheduled child joining with fewer rounds solved today than the day asks for is sent into the classroom ("Learning time first!") for what is left, rounds they solved earlier that day counting, with the portal locked until they're done ("All done for today! Off you go and play!"). A new day starting while they play waits for their next login (`enforce_on_rollover` gives a minute's notice and sends them in instead). Operators are never kept in unless they schedule themselves. An offline player is found by name through the server's own name cache (`usercache.json`: anyone who has joined; a name it has never seen is refused, "have them join once"), or by UUID, which stores their real name if the server knows it; the stored name is brought up to date whenever they join. Kept in `config/learning-blocks/schedules.json` by UUID, with the name alongside and a `timezone` for when a day starts (the server's own by default; the prize cap and daily visit use it too). The prize editor's Learners list sets lessons a day and weekends off too
- `/learn try [kind] [word]` (operators): the sampler. Runs a round of any kind right now in your classroom (taking you in if need be), as practice: it plays and says and does everything, but nothing counts (no mastery, memory, prizes, lessons or daily count, no stage change), and the round after is your ordinary next one. Kinds: letters, case, early, first, last, middle, whole, readit, scene, phrase, tricky, plural, teamup (or together, when two read together), fixit (a word with one wrong letter), bob (Tell Bob, by goal: cat-in-box), whatswrong (Bob's sentence made wrong), prep (the little words: owl in box or on box), sentence (silly sentences); the word or letter is optional and tab-completes. With no kind, a screen of buttons, one per kind, and Next for another of the last kind
- `/learn subjects <player> [reading on|off|default] [numbers ...] [logic ...]` (operators): show or set which subjects a child has on; `default` follows the server's `subjects` setting. See Subjects
- `/learn daily <player> <n> reading <n> numbers <n> logic <n>` (operators): a daily schedule of some of each subject (any subject left out isn't asked for); a plain `/learn daily <player> <n>` is any subject again
- `/learn try numbers [kind [target]]`, `/learn try logic [kind [target]]` (operators): the sampler for the other subjects, as practice like the rest. Logic's kinds: sort, same, next, mistake, copy, notes, odd, missing, story, size, morning, bobsort, bobbig, bobleft, bobfix (`/learn try logic next abc`). Numbers' kinds: find, look, name, count, make, beds, toybox, scene, bob, fix, team, pens, towers, order, next, picture, more; their targets tab-complete (`/learn try numbers pens same`, `/learn try numbers make 7`)
- `/learn prizes`: the prize editor (operators only); `/learn prizes done` and `cancel` finish editing a list's items

`/learn`, `/learn leave`, `/learn join`, `/learn accept`, `/learn decline`, `/learn silly`, `/learn toybox` and the review screens are open to everyone. `/tp` won't take anybody into or out of the classroom: the way in and out does work a teleport would skip, so the command is refused and names the right one.

## Pandorical

Learning Blocks runs on the server, and Pandorical is required there and on every player's game (see Install). Pandorical carries the tiles' textures and models, the pictures, the recorded voice and the captions to each player, draws the screens, and puts the Learning Blocks buttons on the action menu.

## Development

Building, testing and the layout of the code are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
