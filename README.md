# Learning Blocks

Learning games for young children (about 4 to 7) on an ordinary survival server, each child in a quiet classroom of their own. There are three subjects: **Reading** (letter sounds, words, sentences, big words), **Numbers** (counting, quick looks, comparing and order) and **Logic** (sorting, patterns, odd one out, what's missing, putting things in order). Everything is spoken, so a child who can't read yet can play; the voice, captions and screens are English only.

Children earn prizes to take home, collect plushies and toys by mastering words, and can be given a number of lessons to finish each day before they play.

## Getting started

Children get in from the action menu: press **J** (Pandorical's "Open action menus" key, which can be changed in Controls) and click **Learning Blocks**, or type `/learn`.

**Offline-mode servers.** With `online-mode=false`, the server does not check names. Anyone can join under an operator's name or a child's, and take over their commands, progress, things and prizes. Install an authentication mod (one that makes each player set a password) before children play on such a server.

## The classroom

Each child gets a small room of their own with a green chalkboard, open to the sky, far from everyone else's. Nothing in it can get hurt and nobody gets hungry. The only blocks that break are letter tiles, and nothing a word puts in the room can be opened, slept in or taken. Step off the edge and you're back on the floor.

Your things, experience, game mode and potion effects are put aside on the way in and handed back on the way out, exactly as they were. Nothing from the classroom comes home except prizes.

To go home, walk into the portal behind you, or press **Go home** on the action menu (`/learn leave`). While a child still has lessons to do, the portal is dark with the number left over it; when the last is done it lights up. `/tp` won't take anybody into or out of the classroom; it is refused and names the right command.

Nobody gets stuck. A child free to go who has tried to leave three times in thirty seconds and is still there ten seconds later is sent home anyway, and the server log says `STUCK:` with what the room was doing. So is a child kept for lessons when the room has had nothing to ask for ten seconds (the lessons are let go).

## How a round works

On the board is a word with a gap, like `_ a t`, and a picture of the word it wants (click it to hear the word again). On the floor are a few letter tiles.

- **Break a tile** (hold left-click) and walk over it to pick it up.
- **Place it in the gap**: right-click the underscore with the tile in your hand. A tile set down anywhere else flies back to its place.
- **Press the button** and the room sounds out each letter, lighting its tile, says the word, and then the word happens: a cat hops out, a hat flies onto your head, the sky turns to night.

Every tile says its sound when touched. The other tiles make real words too, and they happen as well, only sillier: build hat when the room asked for cat and a hat bonks you on the head. Wrong answers never hurt and never last. Everything a word brings stays in the classroom.

Everything the room says is spoken, with big captions that light up each word as it is said. Nothing goes to chat.

## Subjects

Reading, Numbers and Logic share one classroom, one voice, one Toybox, one prize system and one daily schedule; each keeps its own progress. A child's rounds take turns among their subjects, and the board's top edge shows whose turn it is: blue for reading, green for numbers, purple for logic. All three are on by default; see Settings for how they mix and `/learn subjects` to set one child's.

### Reading

- **Letters first.** A new reader starts at stage 0: the room plays a sound ("Which letter says... /m/?") and the child finds its tile among three or four. Letters come in groups (s a t p i n, then m d g o c k, then e u r h b, then f l j v w, then x y z), and once s a t p i n are known, one round in three is a little word made only of known letters. Once a letter's sound is mastered, its capital comes in too ("Which one is the big... A?").
- **Stages 1 to 14.** Words come a short vowel at a time (a, i, o, u, e), then double letters and ck, sh ch th and qu, blends, magic e (stage 9), vowel teams, ng and nk, r-controlled vowels, ow ou ay oi oy, and at stage 14 big words (catfish, robin), built a chunk at a time. Two-letter sounds such as sh or ee are single tiles.
- **Kinds of round.** First the first sound is swapped (`_at`), then the last (`ma_`), from stage 2 the middle vowel (`c_t`), and from stage 6 whole words with every tile a gap.
- **Situations.** Once a word has been read it can come back as a little problem: a villager stands in the rain, the room asks "What can help?", and the right word (hat) fixes it. One round in four.
- **Read it** (from stage 2): a word already read is shown whole with nothing said, and the child picks its picture from two or three that sound alike.
- **Fix the word** (from stage 2): the gap already holds a wrong letter that makes another word (the picture is a cat, the board says cot).
- **Tricky words** (from stage 2): words that can't be sounded out (the, I, to, was, said...), picked from word cards. One round in six.
- **Plurals and phrases** (from stage 6): cats, three cats; big pig, one big pig.
- **Sentences with Bob** (from stage 5): Bob is a villager who does exactly what a sentence says. The child puts word cards in order ("put cat in box") and Bob acts it out; "put box in cat" puts the box on the cat's head. Some rounds show a sentence Bob got wrong to fix, or ask which picture "owl in box" means. **Silly sentences** on the action menu (`/learn silly`) lets a child give Bob any sentence at all, for fun.

### Numbers

Counting to 20, adding and taking away within 10, and number sense, in three stages of two bands each. Number tiles are wooden blocks with a numeral (0 to 20) and the same amount in dots.

- **N0, numbers 0 to 10**: "Which block says... five?", quick looks (a die's face or a ten-frame flashed for two seconds), and number words read in letter tiles.
- **N1, counting to 10 with real things**: "How many pigs?", "Make five!" (exactly that many come out of the button), "Every kitten needs a bed!", and Bob with number cards.
- **N2, comparing and order**: which pen has more, fewer or the same, which tower is taller, a number line to put in order, what comes before or after.

### Logic

Thinking skills in four stages of two bands each.

- **L0, matching and sorting**: animals into pens by kind, colour or size; same or different.
- **L1, patterns**: what comes next (AB, AAB, ABC, growing), find the mistake, copy it, and note-block tunes.
- **L2, odd one out and what's missing**: "Which one doesn't belong?", and "What's missing?" after a blink of darkness.
- **L3, sequencing**: first, next, last (a seed, a sprout, a flower), little to big, and a muddled morning to put in order.
- **Bob** throughout: "put all the pigs in the pen", "put the cat left of the box".

## Progress

- **Mastery.** A word, letter, number or other item is mastered after three clean answers (right first time, nothing wrong tried before) on three different days. Only one clean answer a day counts; more rounds are practice and still count as lessons. A miss neither adds nor takes away. The number is `mastery_clean`.
- **Stages and bands.** Long stages are split into bands (1a, 1b, 1c...), each 4 to 15 words. A child moves on when every word of their band is mastered, hears "You're ready for new words!", and starts the next with a fresh room. `/learn stage` moves a child by hand.
- **Review.** About three rounds in ten (`review_share`) are something missed lately or due for another look. Nothing a child sees counts what they got wrong.
- **No repeats in a day.** A word asked once is not asked again that day while the band has another.
- **How long it takes.** A band takes at least three days. At ten Reading lessons a day, Reading takes roughly 95 days after the letters level; with all three subjects taking turns, about three times that, unless Reading gets its own share of the daily schedule.

`/learn progress` shows a reader's stage, everything they have met as not tried, learning, shaky or solid, the sounds they mix up most, and time and rounds this week.

## Plushies and toys

Thirty-four plushies and twenty-nine toys, chunky and big-eyed, each a word: an owl, a duck, a teddy; a drum, a toy car, a doll's crib. Reading the word brings one out of the button as a lesson prop. When a child has mastered every word that brings it, they earn it to keep: "Ooh! You got a new plushie!", once per toy, ever (the `toy_mastered` event). Five counting toys and five thinking toys are earned in Numbers and Logic.

At home, right-click a plushie and it squeaks and says its word; a toy plays (the car zooms a loop, the kite flies). Use one on another to put it in or on (a plushie into the crib, the doll into the car); sneak and right-click with an empty hand to take it out. One punch picks any of them up.

The **Toybox** tab (`/learn toybox`) shows those collected and, for the rest, the word that earns each and how near it is.

The full lists, and how to add words, are in [CONTENT.md](CONTENT.md).

## Reading together

Up to six children can share one classroom (`max_readers`). `/learn join <player>` asks to learn with someone; they get **Accept** and **Deny** (a minute to answer; a child can ask again after half a minute). Operators can `/learn pair <a> <b>` and `/learn unpair <player>`, and `/learn daily <reader> with <other>` pairs two for their daily lessons: whoever arrives second goes into the first one's room.

Rounds take turns, each picked from that reader's own stage and subjects. Only the reader whose turn it is can answer; the others can point, which shows a puff in their colour and earns them the lesson but not mastery or prizes. A reader who only watched gets nothing. Prizes fly into each reader's own cubby. One round in three is a team-up, two readers each filling their own gap in one word.

A grown-up can come in as a teacher with `/learn teach <child>` (operators, and anyone named in `teachers`): they stand at the back, everything they click only points, in gold, they are never kept in or credited, and the portal always lets them out.

## Prizes

Whatever a child earns flies into a chest by the portal, which shows the haul so far. On the way out they get their own things back first, then the prizes; whatever doesn't fit is dropped at their feet. That holds however the visit ends: the portal, `/learn leave`, logging out, or a respawn.

What pays is set in `config/learning-blocks/prizes.json`, written with a comment over every setting the first time the server starts, or in game with `/learn prizes`. Each event can give points, prizes, or both, and can be switched off. Events count in every subject:

| Event | When | Default |
|-------|------|---------|
| `new_word` | The first time a child gets a thing right (a word, a letter, a numeral, a pattern); once per thing, ever | On: a cookie |
| `solved` | Any right answer; `first_try_only` (on) limits it to ones with nothing wrong tried first | Off |
| `streak` | Every `length` right in a row; a wrong answer starts over | On: every 3, 10 experience |
| `stage_complete` | A Reading band, the letters level, or a Numbers or Logic stage finished; once each | On: an emerald |
| `milestone` | Totals of things learned in every subject | On: 3 emeralds at 10, a diamond at 25, 5 diamonds at 100 |
| `toy_mastered` | Every word that brings a plushie or toy mastered: the toy itself, once | On, no extra prizes |
| `daily_visit` | The first visit of each real day | Off: an apple |

`daily_cap` (20) limits payouts per child per real day; anything over is owed and paid on a later day. Points are off by default; each threshold pays once.

A prize is items (`item`, `count`, optional `components`), experience (`xp`), one roll of a loot table (`loot_table`), or a plushie or toy (`{ "plush": "random_new" }`, `{ "plush": "owl" }`, `{ "toy": "random_new" }`, `{ "toy": "drum" }`, `{ "toybox": "random_new" }`). There are no command prizes. An entry naming an item or loot table the server doesn't have is skipped and named in the log and in the reply to `/learn reload`, and stays in the file.

In the prize editor, each prize list's **Edit** button puts your own things aside and lays the list out in your inventory: hold whatever should be given (names and enchantments are kept) and type `/learn prizes done` (or `cancel`). Every change is written to the file at once, and a hand edit followed by `/learn reload` shows up in the menu. The simple settings also appear on Learning Blocks' page of Pandorical's settings.

A child's progress is kept on the player in the world save and outlives every visit and death.

## Daily schedules and lessons

`/learn daily <player> <n>` sets a number of lessons to finish each day before playing. A scheduled child joining with fewer done today is sent into the classroom ("Learning time first!") with the portal locked until they're done ("All done for today! Off you go and play!"); rounds solved earlier that day count. Any weekday can be different (`/learn daily Sam saturday 0`), and a schedule can ask for some of each subject (`/learn daily Sam 10 reading 6 numbers 2 logic 2`). At most 20 lessons a day.

A new day starting while a child plays waits for their next login, unless `enforce_on_rollover` is on (a minute's notice, then in they go). Operators are never kept in unless they schedule themselves. An offline player can be named if they have joined before. Schedules are kept in `config/learning-blocks/schedules.json`, whose `timezone` sets when a day starts (the server's own by default). The prize editor's Learners list sets lessons a day and weekends off too.

`/learn send <player> <lessons>` sends a child in once with the portal locked until that many are done. If something else takes them out, the lessons are kept and their next login takes them back.

## Screens

The **Letter sounds** and **My words** buttons on the action menu open one screen, from anywhere, with tabs for letters, two-letter sounds, vowels, tricky words, the Toybox, every word read, and numbers and logic. Click anything to hear it. Operators also get every recorded phrase, and can open the screen on another player.

## Commands

Open to everyone:

| Command | Does |
|---------|------|
| `/learn` | Go to your classroom |
| `/learn leave` | Come home (waits while lessons are left) |
| `/learn sounds`, `/learn vowels`, `/learn words` | The letter sounds, the vowels, and the words you've read |
| `/learn toybox` (or `/learn plushies`) | Your plushies and toys |
| `/learn silly` | Silly sentences with Bob, from stage 5; again to go back to reading |
| `/learn join <player>` | Ask to learn with someone |
| `/learn accept <player>`, `/learn decline <player>` | Answer a join request |

Operators only (`/learn teach` also for names in `teachers`):

| Command | Does |
|---------|------|
| `/learn teach <child>` | Join a child's classroom as a teacher |
| `/learn send <player> [lessons]` | Send a player to the classroom, optionally locked in for that many lessons. Operators are never kept in |
| `/learn lessons <player>`, `/learn release <player>` | How many lessons are left; clear them |
| `/learn home <player>` | Send a child home now, past lessons, schedule and lock. Clears that visit's lessons but not the daily schedule |
| `/learn daily <player> [n\|off]` | Show or set a daily schedule |
| `/learn daily <player> <weekday> <n>` | A different count on one weekday |
| `/learn daily <player> <n> reading <n> numbers <n> logic <n>` | Some of each subject; a subject left out isn't asked for |
| `/learn daily <reader> with <other\|nobody>` | Pair two readers for their daily lessons |
| `/learn pair <a> <b>`, `/learn unpair <player>` | Put two readers in one room, or part them |
| `/learn subjects <player> [reading on\|off\|default] [numbers ...] [logic ...]` | Show or set a child's subjects; `default` follows the server setting |
| `/learn stage <player> [n]` | Show or set a reader's stage: `0` (letters), `1` (start over), a band such as `1b`, or a Numbers band `N0a` to `N2b` or Logic band `L0a` to `L3b`. Everything from there up is learned again; what it paid stays paid |
| `/learn progress <player> [export]` | A reader's progress screen; `export` writes it to `config/learning-blocks/reports/<uuid>-<player>.json` |
| `/learn words all`, `/learn words <player>`, `/learn phrases` | Every word, someone else's words, every recorded phrase |
| `/learn give <player> <thing>` | Give a child another copy of a plushie or toy they have already earned |
| `/learn try [kind] [word]` | Play any kind of Reading round now as practice; nothing counts. Kinds: letters, case, early, first, last, middle, whole, readit, scene, phrase, tricky, plural, teamup (or together), fixit, bob, whatswrong, prep, sentence. With no kind, a screen of buttons |
| `/learn try numbers [kind [target]]`, `/learn try logic [kind [target]]` | The same for Numbers (find, look, name, count, make, beds, toybox, scene, bob, fix, team, pens, towers, order, next, picture, more) and Logic (sort, same, next, mistake, copy, notes, odd, missing, story, size, morning, bobsort, bobbig, bobleft, bobfix) |
| `/learn prizes` | The prize editor; `/learn prizes done` and `cancel` finish editing a list's items |
| `/learn reload` | Read `prizes.json` again |
| `/learn plots prune <days>` | Give back the classroom plots of players not seen in that many days |

`/learn lessons`, `send` and `progress` say so when the player named is offline.

## Settings

In `config/learning-blocks/prizes.json`, and on the prize editor's General tab. Under `puzzles`:

| Setting | Default | What it does |
|---------|---------|--------------|
| `scenes`, `scene_every` | on, 4 | Situations, one round in this many |
| `letters_first` | on | New readers start on the letters level |
| `mastery_clean` | 3 | Clean answers on different days to master something (1 to 10) |
| `letter_word_every` | 3 | One letters round in this many is a little word; 0 for none |
| `read_it`, `read_it_every` | on, 5 | Read-it rounds, from stage 2 |
| `review_share` | 30 | Out of 100, how often a round is something due for another look (0 to 90) |
| `first_sound`, `last_sound`, `middle_vowel`, `whole_word` | on | The kinds of word round |
| `team_up`, `team_up_every` | on, 3 | Team-ups when children read together |
| `tell_bob`, `tell_bob_every` | on, 5 | Bob's rounds, from stage 5 |
| `whats_wrong`, `whats_wrong_every` | on, 6 | Bob's sentence to fix (every fourth Bob round), and a word to fix one round in this many |
| `silly_counts` | off | Whether a silly sentence counts as a lesson (never towards mastery) |
| `bob_prop_share` | 20 | Out of 100, at most how often Bob's goal has only props in it |
| `max_readers` | 6 | Children per room (1 to 6) |
| `teachers` | none | Names who may use `/learn teach` without being operators |

Under `subjects`: `reading`, `numbers`, `logic` (all on) for a child nobody has set; `mix` is `alternate` (one of each in turn, the default), `blocks` (`block` rounds of one, then the next; 3) or `weights` (each by chance, as often as its weight; 1 each). A daily schedule asking for some of each comes first.

Top level: `enabled` (all prizes on or off), `daily_cap`, `events`, `points`.

In `config/learning-blocks/schedules.json`: `timezone` and `enforce_on_rollover` (off).

Each player's own: captions on or off, size, and position, on Learning Blocks' page of Pandorical's settings.

## Pandorical

Learning Blocks goes on the server, and **Pandorical is required there and on every player's client**, along with [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api): the letter tiles, plushies, toys and props are new blocks and items, and a client without [Pandorical](https://www.curseforge.com/minecraft/mc-mods/pandorical) has no entry for them. Pandorical also brings the pictures, captions, screens and recorded voice from the server, so players need nothing else. The Minecraft, Fabric Loader and Java versions are the ones in `gradle.properties`.

Optional mods add words where installed (Let's Cook, Better Ladders, Cloud Kingdoms, Minecart Mania, Village Mail, Poopsmith, the pinata mod), and with Chest Utils each reader's prize cubby is painted their colour.

## Development

Building, testing and the layout of the code are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
