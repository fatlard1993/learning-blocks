# Sound effects

`sounds/sfx/plush_squish.ogg` is a plushie being squeezed: a soft poof with a little boop
in it, chosen by ear from several takes. Both halves came from ElevenLabs' sound-effects
generator, and were then filtered and mixed with ffmpeg:

- `fluffy_1.ogg`: "a soft muffled poof of squeezing a fluffy pillow, gentle airy whoomp, very
  soft, no crinkle, no crunch, no plastic, no paper", low-passed at 2.2 kHz and high-passed
  at 80 Hz to take the crunch out
- `accent_1.mp3`: "a tiny cute cartoon boop, soft and round, playful, like a gentle squeaky
  toy, very short"

Mixed as:

    ffmpeg -i fluffy_1.ogg -i accent_1.mp3 -filter_complex \
      "[1:a]volume=0.2,lowpass=f=3200,afade=t=in:d=0.04,adelay=110|110[a];[0:a][a]amix=inputs=2:duration=longest:normalize=0,afade=t=out:st=0.55:d=0.2,loudnorm=I=-20:TP=-2" \
      plush_squish.ogg
