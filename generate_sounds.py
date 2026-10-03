#!/usr/bin/env python3
"""
Record every sound the room makes, in one voice, with the timing the captions follow.

Three kinds of clip, matching the keys Sounds.java plays:

  phoneme/<x>   the sound a tile makes: /k/ for c, never "see" or "kuh"
  word/<w>      a whole word, one per phonics_words entry
  phrase/<p>    what the room says: prompts, cheers, laughs; phrase/<p>_v2... are other ways of
                saying the same thing, for the room to pick between
  number/<n>    a number's name, zero to twenty
  name/<x>      a consonant's name ("bee" for b); a vowel's name is its long sound, phoneme/<v>_long
  scene/<w>     the line that sets up word w's situation puzzle, from its "scene": {"line": ...}
  blend/<w>/<i> the vowel of word w's tile i, cut from that word's own recording

Each is asked of ElevenLabs with character timestamps, cut to the part that is wanted, peak
normalised, and written as a mono .ogg under assets/.../sounds/. Beside it goes a caption file
under data/.../phonics_captions/ listing each spoken word's start and end, shifted to match the
cut, so the room can light the words up as they are heard.

A speech engine will not say a bare sound, so each is cut out of something it will say: /k/
from "kuh", up to where the "u" begins; /f/ from "fffffish"; short /a/ from the front of
"apple", slowed to twice its length. Held vowels ("aaaapple") come out as the letter's name. A word
said alone can come out ambiguous ("hat" drifting to "hot"), so words are cut out of "The word
is hat." The timestamps say exactly where each cut falls.

In a blend the consonants are the standard ones, but each vowel is cut from the word being built,
so "c... a... t... cat" leads into the word it becomes; a loose tile still says the standard
sound. Blend clips follow whichever take the word uses.

Every clip has one or more takes: different ways of asking for the same sound. The first is used
unless sound-takes.json picks another, so when one sounds wrong it can be swapped without
losing it:

  --takes <key>    list a clip's takes, marking the one in use
  --next <key>     move a clip to its next take (wrapping round) and record it
  --take <key> <n> pick a take by number

What ElevenLabs returned is kept in .sound-cache/, one file per distinct request, so re-running,
or going back to an earlier take, costs nothing. A clip whose take and settings haven't changed
since it was last cut, and whose files are all there, is skipped (.sound-cache/made.json);
bump PIPELINE when the cutting itself changes. --force <prefix> re-asks for the clips in use
under that prefix (e.g. --force phoneme/k).

Key from $ELEVENLABS_API_KEY or ~/.config/elevenlabs/key. Needs ffmpeg.

Usage: python3 generate_sounds.py [--force <prefix>]... [--takes|--next <key>] [--take <key> <n>]
"""

import base64
import hashlib
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import urllib.request

HERE = pathlib.Path(__file__).parent
RESOURCES = HERE / "src/main/resources"
SOUNDS = RESOURCES / "assets/learning-blocks-justfatlard/sounds"
CAPTIONS = RESOURCES / "data/learning-blocks-justfatlard/phonics_captions"
WORDS = RESOURCES / "data/learning-blocks-justfatlard/phonics_words"
CACHE = HERE / ".sound-cache"
TAKES = HERE / "sound-takes.json"

VOICE = "cgSgspJ2msm6clMCkdW9"  # Jessica, the teacher: playful, bright, warm
BOB = "IKne3meq5aSn9XLyUdCD"  # Charlie, deep and energetic: Bob, the helper who does exactly what he's told
SPEECH = "eleven_multilingual_v2"
EXPRESSIVE = "eleven_v3"  # the only model that acts out [laughs] rather than reading it
PRONOUNCING = "eleven_flash_v2"  # honours <phoneme alphabet="ipa">, so it can hold a bare sound
SEED = 1

STOP_TAIL = 0.015
FADE_OUT = 0.02
TAIL = 0.12
WORD_TAIL = 0.08


IPA = {"sh": "ʃ", "th": "θ", "ll": "l", "ff": "f", "ss": "s", "zz": "z", "a": "æ", "e": "ɛ", "i": "ɪ", "o": "ɑ", "u": "ʌ", "f": "f", "z": "z", "s": "s", "m": "m", "n": "n", "l": "l", "r": "ɹ", "v": "v"}


def held_ipa(letter):
	"""The bare sound, held, spelled out in IPA; no cutting or slowing needed."""
	return {"text": f'<phoneme alphabet="ipa" ph="{IPA[letter]}ː">{letter}</phoneme>', "model": PRONOUNCING, "cut": ["whole"]}


def team(grapheme, word):
	"""A two-letter vowel, cut from a word that says it, slowed a little."""
	i = word.index(grapheme)
	return [
		{"text": word, "cut": ["span", i, i + len(grapheme)], "stretch": 1.3},
		{"text": word, "cut": ["span", i, i + len(grapheme)]},
	]


def digraph(grapheme, word):
	"""A two-letter consonant: held when it can be, else cut from the front of a word."""
	start = [{"text": word, "cut": ["span", 0, len(grapheme)]}]
	return ([held_ipa(grapheme)] if grapheme in IPA else []) + start


def stop(text, word):
	"""/k/ as "kuh" cut where the vowel starts, or as the start of a word."""
	return [{"text": text, "cut": ["until", 1]}, {"text": word, "cut": ["span", 0, 1]}]


def hold(letter, word):
	"""/f/ stretched at the front of a word, or on its own."""
	stretched = letter * 5 + word[1:]
	return [
		{"text": stretched, "cut": ["span", 0, 5]},
		{"text": letter * 5, "cut": ["whole"]},
		{"text": letter * 5, "cut": ["whole"], "model": EXPRESSIVE},
		{"text": word, "cut": ["span", 0, 1], "stretch": 3.0},
		held_ipa(letter),
	]


def short(vowel, word):
	"""Held spellings ("aaaa") are read as the letter's name, so the in-use take is taken from the word."""
	held = vowel * 4 + word[1:]
	return [
		{"text": held, "cut": ["span", 0, 4]},
		{"text": word, "cut": ["span", 0, 1]},
		{"text": held, "cut": ["span", 0, 4], "model": EXPRESSIVE},
		{"text": word, "cut": ["span", 0, 1], "stretch": 2.0},
	]


def long(vowel, word):
	"""The letter's name, which is also its long sound."""
	return [{"text": f"{vowel}, as in {word}", "cut": ["span", 0, 1]}, {"text": f"{vowel.upper()}.", "cut": ["whole"]}]


def within(text, part, **extra):
	i = text.index(part)
	return {"text": text, "cut": ["span", i, i + len(part), WORD_TAIL], **extra}


# Chunks that aren't words are cut from the big word they belong to, so "ster" sounds the
# way it does in "lobster"; words a bare carrier says wrong get a carrier that says them right.
CHUNK_OF = {"puf": "puffin", "munk": "chipmunk", "ster": "lobster", "lob": "lobster"}
SAID_IN = {"bow": "I tied a bow."}


def word_takes(word):
	if word in CHUNK_OF:
		text = f"The word is {CHUNK_OF[word]}."
		i = text.index(word)
		return [{"text": text, "cut": ["span", i, i + len(word)]}]
	if word in SAID_IN:
		return [within(SAID_IN[word], word)]
	return [
		within(f"The word is {word}.", word),
		{"text": f"{word}.", "cut": ["whole"]},
		{"text": f"{word}.", "cut": ["whole"], "model": EXPRESSIVE},
	]


VOWELS = {"a": "apple", "e": "egg", "i": "igloo", "o": "octopus", "u": "umbrella"}

PHONEMES = {
	# Slowed too far, a short vowel drifts to its long neighbour (i toward "ee", u toward "oo"), so
	# each also has gentler takes from short words that hold it lax.
	**{v: short(v, w) + [
		{"text": word, "cut": ["span", 0, 1], "stretch": 1.5} for word in (*extra, w)
	] + [held_ipa(v)] for (v, w), extra in zip(VOWELS.items(), [("at", "an"), ("egg", "end"), ("it", "in"), ("on", "ox"), ("up", "us")])},
	**{f"{v}_long": long(v, w) for v, w in VOWELS.items()},
	"u_long": long("u", "umbrella") + [{"text": "You.", "cut": ["whole"]}],
	"b": stop("buh", "bat"),
	"c": stop("kuh", "cat"),
	"d": stop("duh", "dog"),
	"f": hold("f", "fish"),
	"g": stop("guh", "got"),
	"h": stop("huh", "hat"),
	"j": stop("juh", "jam"),
	"k": stop("kuh", "kit"),
	"l": hold("l", "log"),
	"m": hold("m", "map"),
	"n": hold("n", "nap"),
	"p": stop("puh", "pig"),
	"q": [{"text": "kwuh", "cut": ["until", 2]}],
	"r": hold("r", "run"),
	"s": hold("s", "sun"),
	"t": stop("tuh", "top"),
	"v": hold("v", "van"),
	"w": stop("wuh", "wet"),
	"x": [
		{"text": "ks", "cut": ["whole"]},
		{"text": "box", "cut": ["span", 2, 3]},
		{"text": "fox", "cut": ["span", 2, 3, WORD_TAIL]},
		{"text": "box", "cut": ["span", 2, 3, WORD_TAIL], "stretch": 1.5},
	],
	"y": stop("yuh", "yes"),
	"z": hold("z", "zip"),
	# Two letters, one sound.
	"sh": digraph("sh", "ship"),
	"th": digraph("th", "thin"),
	"ch": [{"text": "chuh", "cut": ["until", 2]}, {"text": "chip", "cut": ["span", 0, 2]}],
	"ck": stop("kuh", "kit"),
	"gg": stop("guh", "got"),
	"qu": [{"text": "kwuh", "cut": ["until", 2]}, {"text": "quilt", "cut": ["span", 0, 2]}],
	# A doubled letter says what the single one does, by whichever take that one uses.
	"ll": hold("l", "log"),
	"ff": hold("f", "fish"),
	"ss": hold("s", "sun"),
	"zz": hold("z", "zip"),
	"ai": team("ai", "rain"),
	"ee": team("ee", "bee"),
	"ea": team("ea", "bean"),
	"ie": team("ie", "pie"),
	"oa": team("oa", "boat"),
	"oo": team("oo", "moon"),
	"ar": team("ar", "star"),
	"or": team("or", "fork"),
	"ur": team("ur", "fur"),
	"ir": team("ir", "bird"),
	"er": team("er", "fern"),
	"ow": team("ow", "cow"),
	"ou": team("ou", "loud"),
	"ay": team("ay", "hay"),
	"igh": team("igh", "light"),
	"oi": team("oi", "coin"),
	"oy": team("oy", "toy"),
	"aw": team("aw", "paw"),
	# ow says two things: "cow" (phoneme/ow) and "snow", for words that set sounds: ["ow_long"].
	"ow_long": team("ow", "snow"),
	"wh": [{"text": "whale", "cut": ["span", 0, 2]}, {"text": "what", "cut": ["span", 0, 2]}],
	"dge": [{"text": "hedge", "cut": ["span", 2, 5, WORD_TAIL]}],
	"y_long": team("y", "fly"),
	"le": [{"text": "beetle", "cut": ["span", 4, 6, WORD_TAIL]}],
	"c_soft": [held_ipa("s")],
	"ng": [{"text": "ring", "cut": ["span", 2, 4]}, {"text": "sing", "cut": ["span", 2, 4], "stretch": 1.5}],
	"nk": [{"text": "pink", "cut": ["span", 2, 4]}],
}


# Jessica's own takes, chosen by ear: where a cut or a slow-down didn't carry her voice, the
# sound is said outright, or cut from a word that holds it better.
for _key, _take in {
	"ai": {"text": "Ay!", "cut": ["whole"]},
	"ay": {"text": "Ay!", "cut": ["whole"]},
	"y_long": {"text": "Eye.", "cut": ["whole"]},
	"aw": {"text": "Aww.", "cut": ["whole"]},
	"er": {"text": "Errr.", "cut": ["whole"]},
	"h": {"text": "hit", "cut": ["span", 0, 2, 0.02]},
	"p": {"text": "puh", "cut": ["until", 1], "tail": 0.045},
	"u": {"text": "Uh.", "cut": ["whole"]},
	"l": {"text": "Llll.", "cut": ["whole"]},
	"ll": {"text": "Llll.", "cut": ["whole"]},
}.items():
	PHONEMES[_key] = PHONEMES[_key] + [_take]

NUMBER_NAMES = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
	"eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"]

# Spelled the way the voice says them best; the caption is the letter itself.
NAMES = {
	"b": "Bee.", "c": "See.", "d": "Dee.", "f": "Eff.", "g": "Gee.", "h": "Aitch.", "j": "Jay.",
	"k": "Kay.", "l": "Ell.", "m": "Em.", "n": "En.", "p": "Pee.", "q": "Cue.", "r": "Are.",
	"s": "Ess.", "t": "Tee.", "v": "Vee.", "w": "Double-you.", "x": "Ex.", "y": "Why.", "z": "Zee.",
}

# A phrase that runs into a word ends in "..." so the voice leaves it open.
PHRASES = {
	"make_the_word": {"variants": [
		{"text": 'Can you make...'},
		{"text": "Let's make..."},
		{"text": 'Can you build...'},
		{"text": 'Try to make...'},
	]},
	"pick_a_letter": {"text": "Pick a letter!"},
	"sound_it_out": {"variants": [
		{"text": "Let's sound it out!"},
		{"text": 'Sound it out with me!'},
		{"text": "Let's hear it!"},
	]},
	"try_again": {"text": "Try again!"},
	"another_one": {"variants": [
		{"text": 'Here comes another one!'},
		{"text": 'Ready for the next one?'},
		{"text": "Let's do another!"},
		{"text": "Ooh, here's a new one!"},
		{"text": 'Next one!'},
	]},
	"well_done": {"variants": [
		{"text": '[gasps] You did it!', "model": EXPRESSIVE},
		{"text": '[excited] Great job!', "model": EXPRESSIVE},
		{"text": "[happy] Yes! That's it!", "model": EXPRESSIVE},
		{"text": '[excited] Wow, well done!', "model": EXPRESSIVE},
		{"text": '[cheerful] Nice reading!', "model": EXPRESSIVE},
	]},
	"oops_thats": {"variants": [
		{"text": "[laughs] Oops! That's...", "model": EXPRESSIVE},
		{"text": "[giggles] Uh oh, that's...", "model": EXPRESSIVE},
		{"text": "[amused] Whoops! That's...", "model": EXPRESSIVE},
		{"text": "[laughs] Silly! That's...", "model": EXPRESSIVE},
	]},
	# A laugh is heard, not read: no caption.
	"giggle": [
		{"text": "[chuckles softly] Heh.", "model": EXPRESSIVE, "caption": None},
		{"text": "[warmly] Hee hee!", "model": EXPRESSIVE, "caption": None},
		{"text": "[happy] Mmm!", "model": EXPRESSIVE, "caption": None},
		{"text": "[giggles] Hehe!", "model": EXPRESSIVE, "caption": None},
	],
	"hmm": {"text": "[curious] Hmmm...", "model": EXPRESSIVE},
	"you_earned_a_prize": {"variants": [
		{"text": '[excited] You earned a prize!', "model": EXPRESSIVE},
		{"text": '[excited] Ooh, a prize for you!', "model": EXPRESSIVE},
		{"text": '[happy] You got a prize!', "model": EXPRESSIVE},
	]},
	"new_word": {"variants": [
		{"text": "[excited] That's a new word!", "model": EXPRESSIVE},
		{"text": '[happy] Ooh, a brand new word!', "model": EXPRESSIVE},
		{"text": '[excited] You learned a new word!', "model": EXPRESSIVE},
	]},
	"next_level": {"text": "[excited] Wow! You're ready for new words!", "model": EXPRESSIVE},
	"find_big": {"variants": [
		{"text": "Which one is the big..."},
		{"text": "Can you find the big..."},
	]},
	"find_little": {"variants": [
		{"text": "Which one is the little..."},
		{"text": "Can you find the little..."},
	]},
	# Bob's lines, in Bob's voice.
	"bob_okay": {"variants": [
		{"text": "[cheerfully] Okay!", "model": EXPRESSIVE, "voice": BOB},
		{"text": "[happily] You got it!", "model": EXPRESSIVE, "voice": BOB},
		{"text": "[eagerly] On it!", "model": EXPRESSIVE, "voice": BOB},
	]},
	"bob_did_it": {"variants": [
		{"text": "[proudly] I did what you said!", "model": EXPRESSIVE, "voice": BOB},
		{"text": "[proudly] Done! Just like you said!", "model": EXPRESSIVE, "voice": BOB},
		{"text": "[confused] Like this?", "model": EXPRESSIVE, "voice": BOB},
	]},
	"bob_hooray": {"variants": [
		{"text": "[excited] Easy peasy!", "model": EXPRESSIVE, "voice": BOB},
		{"text": "[laughs] We did it!", "model": EXPRESSIVE, "voice": BOB},
	]},
	"tell_bob": {"text": "[cheerful] Tell Bob what to do!", "model": EXPRESSIVE},
	"pick_for_bob": {"text": "[brightly] Pick what Bob should make!", "model": EXPRESSIVE},
	"silly_sentences": {"text": "[playful, giggly] Tell Bob something silly!", "model": EXPRESSIVE},
	"not_quite": {"variants": [
		{"text": "Hmm, that's not quite it!"},
		{"text": "[laughs] Oh, Bob! That's not what we wanted!", "model": EXPRESSIVE},
	]},
	"whats_wrong": {"variants": [
		{"text": "Uh oh! Something's not right. Can you fix it?"},
		{"text": "Hmm, what's wrong here?"},
	]},
	"your_turn": {"variants": [
		{"text": "[cheerful] Your turn!", "model": EXPRESSIVE},
		{"text": "[cheerful] Okay, your turn now!", "model": EXPRESSIVE},
		{"text": "[cheerful] Ready? It's your go!", "model": EXPRESSIVE},
	]},
	"not_your_turn": {"variants": [
		{"text": "[gently] Wait for your turn!", "model": EXPRESSIVE},
		{"text": "[gently] Ooh, not yet! It's their turn.", "model": EXPRESSIVE},
	]},
	"team_up": {"variants": [
		{"text": "[excited] Let's do this one together!", "model": EXPRESSIVE},
		{"text": "[excited] Team time! You each have a spot!", "model": EXPRESSIVE},
	]},
	"great_teamwork": {"variants": [
		{"text": "[excited] Great teamwork!", "model": EXPRESSIVE},
		{"text": "[happy] You did it together!", "model": EXPRESSIVE},
	]},
	"new_plushie": {"variants": [
		{"text": "[excited] Ooh! You got a new plushie!", "model": EXPRESSIVE},
		{"text": "[excited] A new plushie for your collection!", "model": EXPRESSIVE},
	]},
	"new_toy": {"variants": [
		{"text": "[excited] Ooh! A new toy!", "model": EXPRESSIVE},
		{"text": "[excited] A new toy for your toybox!", "model": EXPRESSIVE},
	]},
	"which_block_says": {"text": "Which block says..."},
	"what_comes_next": {"text": "[curious] What comes next?", "model": EXPRESSIVE},
	"next_level_numbers": {"text": "[excited] You know one to five! Ready for more numbers!", "model": EXPRESSIVE},
	"next_level_logic": {"text": "[excited] You can find what comes next! Ready for trickier ones!", "model": EXPRESSIVE},
	"how_many": {"text": '[curious] How many?', "model": EXPRESSIVE},
	"quick_look": {"text": '[excited] Quick, look!', "model": EXPRESSIVE},
	"count_with_me": {"text": "[cheerful] Let's count them!", "model": EXPRESSIVE},
	"read_the_number": {"text": 'Read it! Which number is it?'},
	"make": {"text": 'Make...'},
	"put_number_press": {"text": 'Put a number in the gap and press the button!'},
	"every_kitten_needs_a_bed": {"text": '[cheerful] Every kitten needs a bed!', "model": EXPRESSIVE},
	"how_many_more": {"text": 'How many more...'},
	"how_many_toys": {"text": '[curious] How many toys have you got?', "model": EXPRESSIVE},
	"how_many_plushies": {"text": '[curious] How many plushies have you got?', "model": EXPRESSIVE},
	"which_has_more": {"text": 'Which pen has more?'},
	"which_has_fewer": {"text": 'Which pen has fewer?'},
	"more_or_same": {"text": 'Or are they the same?'},
	"the_same": {"text": "[happy] They're the same!", "model": EXPRESSIVE},
	"which_is_taller": {"text": 'Which tower is taller?'},
	"which_is_shorter": {"text": 'Which tower is shorter?'},
	"how_many_more_blocks": {"text": 'How many more blocks to make them the same?'},
	"put_in_order": {"text": '[cheerful] Put the numbers in order!', "model": EXPRESSIVE},
	"what_comes_after": {"text": 'What comes after...'},
	"what_comes_before": {"text": 'What comes before...'},
	"which_picture_has": {"text": 'Which picture has that many?'},
	"team_count": {"text": '[cheerful] You count these, and you count those! How many altogether?', "model": EXPRESSIVE},
	"too_many": {"text": "[laughs] Whoa, that's too many!", "model": EXPRESSIVE},
	"not_enough": {"text": '[playful] Hmm, not enough!', "model": EXPRESSIVE},
	"next_level_counting": {"text": '[excited] You can count to ten! Ready for more!', "model": EXPRESSIVE},
	"next_level_comparing": {"text": '[excited] You know more and fewer! Ready for more numbers!', "model": EXPRESSIVE},
	"bob_so_many": {"text": "[amazed] Whoa! That's a LOT!", "model": EXPRESSIVE, "voice": BOB},
	"bob_counted": {"text": "[proudly] I counted them all!", "model": EXPRESSIVE, "voice": BOB},
	"subject_reading": {"variants": [{"text": "[cheerful] Reading time!", "model": EXPRESSIVE}, {"text": "[cheerful] Let's read!", "model": EXPRESSIVE}]},
	"subject_numbers": {"variants": [{"text": "[cheerful] Numbers time!", "model": EXPRESSIVE}, {"text": "[cheerful] Let's count!", "model": EXPRESSIVE}]},
	"subject_logic": {"variants": [{"text": "[cheerful] Thinking time!", "model": EXPRESSIVE}, {"text": "[cheerful] Let's figure it out!", "model": EXPRESSIVE}]},
	"reading_time": {"variants": [
		{"text": "[cheerful] Reading time first!", "model": EXPRESSIVE},
		{"text": "[cheerful] Hello! Let's do some reading first!", "model": EXPRESSIVE},
		{"text": "[happy] Welcome back! Reading time!", "model": EXPRESSIVE},
	]},
	"done_for_today": {"variants": [
		{"text": "[excited] All done for today! Off you go and play!", "model": EXPRESSIVE},
		{"text": "[happy] Great reading today! Have fun!", "model": EXPRESSIVE},
	]},
	"mastered": {"variants": [
		{"text": "[excited] You've got it!", "model": EXPRESSIVE},
		{"text": "[happy] You know that one now!", "model": EXPRESSIVE},
		{"text": "[excited] Super star reader!", "model": EXPRESSIVE},
	]},
	"what_does_it_say": {"variants": [
		{"text": "What does it say?"},
		{"text": "Can you read it?"},
		{"text": "Hmm, what word is that?"},
		{"text": "Read it, then find it!"},
	]},
	"which_letter_says": {"text": "Which letter says..."},
	"find_the_letter": {"text": "Can you find the letter that says..."},
	"what_can_help": {"variants": [
		{"text": 'What can help?'},
		{"text": 'Which word can help?'},
		{"text": 'Hmm, what could fix it?'},
	]},
	"that_helped": {"variants": [
		{"text": '[happy] That helped!', "model": EXPRESSIVE},
		{"text": '[relieved] Phew, much better!', "model": EXPRESSIVE},
		{"text": '[happy] Hooray, that fixed it!', "model": EXPRESSIVE},
	]},
	**{f"magic_e_{v}": {"text": f"[excited] The E makes the {v.upper()} say its name!", "model": EXPRESSIVE} for v in "aiou"},
	"portal_open": {"variants": [
		{"text": "[excited] All done! The portal is open!", "model": EXPRESSIVE},
		{"text": "[excited] Woohoo! The portal is open!", "model": EXPRESSIVE},
	]},
	"door_open": {"text": "[excited] All done! The door is open!", "model": EXPRESSIVE},
	**{f"more_to_go_{n}": {"text": f"{word} more to go!"} for n, word in enumerate(
		["One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten"], start=1)},
	"prizes_come_home": {"text": "[warmly] Your prizes come home with you!", "model": EXPRESSIVE},
}


def as_takes(spec):
	return spec if isinstance(spec, list) else [spec]


BLEND_STRETCH = 1.5


def blends(word_spec, tiles, sounds):
	"""The word's vowel tiles as blend clips, cut from the same recording as the word."""
	start = word_spec["text"].index("".join(tiles))
	for i, tile in enumerate(tiles):
		at, start = start, start + len(tile)
		if sounds[i] == "silent" or tile == "qu" or not any(v in tile for v in "aeiou"):
			continue
		yield i, {**word_spec, "cut": ["span", at, at + len(tile)], "stretch": BLEND_STRETCH, "caption": tile}


def clips():
	"""Every clip by key, with its takes."""
	for grapheme, takes in PHONEMES.items():
		yield f"phoneme/{grapheme}", [{**t, "caption": grapheme.split("_")[0]} for t in takes]
	for path in sorted(WORDS.glob("*.json")):
		data = json.loads(path.read_text())
		word = "".join(data["tiles"])
		yield f"word/{word}", [{**t, "caption": word, "tiles": data["tiles"], "sounds": data.get("sounds") or [None] * len(data["tiles"])} for t in word_takes(word)]
	# Plushies whose name isn't a puzzle word still say it when squeezed.
	for word in ("teddy", "ladybug", "blue", "yellow", "apples", "cows", "carrots", "kittens", "beds", "chickens",
			"cakes", "blocks", "villagers", "more", "fewer", "same", "taller", "shorter", "plus", "minus", "equals"):
		if not (WORDS / f"{word}.json").exists():
			yield f"word/{word}", [{**t, "caption": word, "tiles": [word], "sounds": ["silent"]} for t in word_takes(word)]
	for name, line in {"hungry_pigs": "The three pigs are hungry, but there's only one carrot!",
			"cake_for_everyone": "[cheerful] Each villager wants a cake!"}.items():
		yield f"scene/{name}", [{"text": line, "cut": ["whole"], **({"model": EXPRESSIVE} if "[" in line else {})}]
	for n, said in enumerate(NUMBER_NAMES):
		yield f"number/{n}", [{"text": f"{said.capitalize()}.", "cut": ["whole"], "caption": said}]
	for letter, said in NAMES.items():
		yield f"name/{letter}", [{"text": said, "cut": ["whole"], "caption": letter}]
	for path in sorted(WORDS.glob("*.json")):
		data = json.loads(path.read_text())
		if "scene" in data:
			line = data["scene"]["line"]
			yield f"scene/{''.join(data['tiles'])}", [{"text": line, "cut": ["whole"], "model": EXPRESSIVE if "[" in line else SPEECH}]
	for name, spec in PHRASES.items():
		# Variants are different lines for the same moment, played in turn so a phrase heard
		# every round does not wear out; takes are different recordings of one line.
		for n, variant in enumerate(spec["variants"] if isinstance(spec, dict) and "variants" in spec else [spec]):
			yield f"phrase/{name}" + (f"_v{n + 1}" if n else ""), [{"cut": ["whole"], **t} for t in as_takes(variant)]


# What each clip was last cut from, so an unchanged clip is left alone. Anything that changes how a
# clip is cut belongs in here, or a change to it would not reach the clips already made.
MADE = CACHE / "made.json"
PIPELINE = 1


def fingerprint(spec):
	settings = [PIPELINE, VOICE, SEED, STOP_TAIL, FADE_OUT, TAIL, WORD_TAIL, BLEND_STRETCH]
	return hashlib.sha256(json.dumps([spec, settings], sort_keys=True).encode()).hexdigest()[:16]


def present(name):
	return (SOUNDS / f"{name}.ogg").exists() and (CAPTIONS / f"{name}.json").exists()


def chosen():
	return json.loads(TAKES.read_text()) if TAKES.exists() else {}


def choose(key, n):
	picks = chosen()
	if n:
		picks[key] = n
	else:
		picks.pop(key, None)
	TAKES.write_text(json.dumps(dict(sorted(picks.items())), indent="\t") + "\n")


def api_key():
	key = os.environ.get("ELEVENLABS_API_KEY")
	if key:
		return key.strip()
	return (pathlib.Path.home() / ".config/elevenlabs/key").read_text().strip()


def request_of(spec):
	return {"text": spec["text"], "model_id": spec.get("model", SPEECH), "voice": spec.get("voice", VOICE), "seed": SEED}


def fetch(key, req):
	body = json.dumps({"text": req["text"], "model_id": req["model_id"], "seed": req["seed"]}).encode()
	http = urllib.request.Request(
		f"https://api.elevenlabs.io/v1/text-to-speech/{req['voice']}/with-timestamps?output_format=mp3_44100_128",
		data=body,
		headers={"xi-api-key": key, "Content-Type": "application/json"},
	)
	with urllib.request.urlopen(http) as response:
		return json.load(response)


def recorded(key, spec, fresh_wanted):
	req = request_of(spec)
	digest = hashlib.sha256(json.dumps(req, sort_keys=True).encode()).hexdigest()[:16]
	cached = CACHE / f"{digest}.json"
	if cached.exists() and not fresh_wanted:
		return json.loads(cached.read_text()), False
	result = fetch(key, req)
	CACHE.mkdir(exist_ok=True)
	cached.write_text(json.dumps(result))
	return result, True


def window(spec, starts, ends, length):
	kind, *args = spec["cut"]
	if kind == "until":
		return starts[0], starts[args[0]] + spec.get("tail", STOP_TAIL)
	if kind == "span":
		tail = args[2] if len(args) > 2 else 0.0
		return starts[args[0]], min(length, ends[args[1] - 1] + tail)
	return starts[0], min(length, ends[-1] + TAIL)


def spoken_words(chars, starts, ends):
	"""Group characters into space-separated words, leaving out [stage directions]."""
	words, text, start, end = [], "", None, None
	for char, s, e in list(zip(chars, starts, ends)) + [(" ", None, None)]:
		if char.isspace():
			if text and not (text.startswith("[") and text.endswith("]")):
				words.append({"text": text, "start": start, "end": end})
			text, start = "", None
			continue
		if start is None:
			start = s
		text, end = text + char, e
	return words


def duration(path):
	out = subprocess.run(
		["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
		capture_output=True, text=True, check=True,
	)
	return float(out.stdout)


def peak(path, a, b):
	out = subprocess.run(
		["ffmpeg", "-hide_banner", "-ss", f"{a}", "-to", f"{b}", "-i", str(path), "-af", "volumedetect", "-f", "null", "-"],
		capture_output=True, text=True, check=True,
	)
	for line in out.stderr.splitlines():
		if "max_volume" in line:
			return float(line.split(":")[1].split()[0])
	return 0.0


def write(name, spec, result):
	align = result["alignment"]
	chars, starts, ends = align["characters"], align["character_start_times_seconds"], align["character_end_times_seconds"]
	with tempfile.NamedTemporaryFile(suffix=".mp3") as mp3:
		mp3.write(base64.b64decode(result["audio_base64"]))
		mp3.flush()
		a, b = window(spec, starts, ends, duration(mp3.name))
		gain = -1.0 - peak(mp3.name, a, b)
		stretch = spec.get("stretch", 1.0)
		slow = (f"rubberband=tempo={1 / stretch}:transients=smooth:window=long:smoothing=on:formant=preserved:pitchq=quality,"
			if stretch != 1.0 else "")
		length = (b - a) * stretch
		ogg = SOUNDS / f"{name}.ogg"
		ogg.parent.mkdir(parents=True, exist_ok=True)
		subprocess.run(
			["ffmpeg", "-v", "error", "-y", "-ss", f"{a}", "-to", f"{b}", "-i", mp3.name,
			 "-af", f"{slow}afade=t=in:d=0.005,afade=t=out:st={max(0.0, length - FADE_OUT)}:d={FADE_OUT},volume={gain}dB",
			 "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", str(ogg)],
			check=True,
		)

	if "caption" in spec:
		words = [] if spec["caption"] is None else [{"text": spec["caption"], "start": 0.0, "end": round(length, 3)}]
	else:
		words = [
			{"text": w["text"], "start": round(max(0.0, (w["start"] - a) * stretch), 3), "end": round(min(length, (w["end"] - a) * stretch), 3)}
			for w in spoken_words(chars, starts, ends)
		]
	caption = CAPTIONS / f"{name}.json"
	caption.parent.mkdir(parents=True, exist_ok=True)
	caption.write_text(json.dumps({"duration": round(length, 3), "words": words}, indent="\t") + "\n")


def main(argv):
	force = [argv[i + 1] for i, arg in enumerate(argv) if arg == "--force"]
	every = dict(clips())

	for i, arg in enumerate(argv):
		if arg in ("--takes", "--next", "--take"):
			name = argv[i + 1]
			if name not in every:
				sys.exit(f"no clip called {name}")
			takes, now = every[name], chosen().get(name, 0)
			if arg == "--next":
				now = (now + 1) % len(takes)
				choose(name, now)
			elif arg == "--take":
				now = int(argv[i + 2])
				if not 0 <= now < len(takes):
					sys.exit(f"{name} has takes 0 to {len(takes) - 1}")
				choose(name, now)
			for n, take in enumerate(takes):
				print(("* " if n == now else "  ") + f"{n}  {take.get('model', SPEECH)}  {take['text']!r}" + (f"  slowed x{take['stretch']}" if 'stretch' in take else ""))
			if arg == "--takes":
				return
			every = {name: takes}
			break

	key = api_key()
	picks = chosen()
	made = json.loads(MADE.read_text()) if MADE.exists() else {}
	asked = cut = 0
	for name, takes in every.items():
		spec = takes[min(picks.get(name, 0), len(takes) - 1)]
		fresh_wanted = any(name.startswith(p) for p in force)
		outputs = [name] + ([f"blend/{name.removeprefix('word/')}/{i}" for i, _ in blends(spec, spec["tiles"], spec["sounds"])]
			if name.startswith("word/") else [])
		mark = fingerprint(spec)
		if not fresh_wanted and made.get(name) == mark and all(present(o) for o in outputs):
			continue
		result, fresh = recorded(key, spec, fresh_wanted)
		asked += fresh
		write(name, spec, result)
		cut += 1
		print(("asked " if fresh else "cut   ") + f"  {name}")
		if name.startswith("word/"):
			word = name.removeprefix("word/")
			for stale in (SOUNDS / "blend" / word).glob("*.ogg"):
				stale.unlink()
			for stale in (CAPTIONS / "blend" / word).glob("*.json"):
				stale.unlink()
			for i, blend in blends(spec, spec["tiles"], spec["sounds"]):
				write(f"blend/{word}/{i}", blend, result)
		made[name] = mark
		CACHE.mkdir(exist_ok=True)
		MADE.write_text(json.dumps(made, indent="\t", sort_keys=True) + "\n")
	print(f"{cut} cut, {len(every) - cut} unchanged")
	print(f"{asked} asked of ElevenLabs")


if __name__ == "__main__":
	main(sys.argv[1:])
