"""Cloud wake word check (spec §5, revised Oct 25).

The phone's on-device VAD opens a "vad" turn when someone starts speaking; the
backend transcribes it and keeps the turn only if it starts with a wake phrase.
Matching is by sound, so "hey rafael", "Hey Raphael" and "hey, raphael!" all
count. On-device keyword spotting (sherpa-onnx) missed the developer's real
voice entirely, which is why the check moved to the cloud ASR.
"""

import re

# Wake phrase must start within this many words of the utterance start.
MAX_LEAD_WORDS = 2


def phonetic(word: str) -> str:
    """Crude sound key: consonant skeleton with similar sounds merged."""
    w = re.sub(r"[^a-z]", "", word.lower())
    if not w:
        return ""
    for a, b in (("ph", "f"), ("gh", ""), ("ck", "k"), ("qu", "kw"), ("x", "ks"), ("th", "t"), ("sh", "s"), ("ch", "s")):
        w = w.replace(a, b)
    w = w.translate(str.maketrans("vbdzcqgjw", "fptskkkyy"))
    key = w[0] if w[0] in "aeiou" else ""
    key += re.sub(r"[aeiouy]", "", w if w[0] not in "aeiou" else w[1:])
    return re.sub(r"(.)\1+", r"\1", key)


def _lev(a: str, b: str) -> int:
    d = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        prev, d[0] = d[0], i
        for j, cb in enumerate(b, 1):
            prev, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, prev + (ca != cb))
    return d[-1]


def _key(words: list[str]) -> str:
    return "".join(phonetic(w) for w in words)


# Words that may come before the bare name ("okay Raphael, …") without breaking the wake check.
FILLERS = {"hey", "hi", "hello", "ok", "okay", "so", "um", "uh", "oh", "a", "yo"}


def _clean(word: str) -> str:
    return re.sub(r"[^a-z]", "", word.lower())


def _match_at(words: list[str], phrase: str, max_lead: int) -> int | None:
    """Index just after the wake phrase if it starts within `max_lead` words, else None."""
    parts = phrase.split()
    n = len(parts)
    target = _key(parts)
    if not target:
        return None
    targets = [phonetic(p) for p in parts]
    tolerance = 1 if len(target) >= 5 else 0
    # 1. The full phrase ("hey raphael").
    for start in range(0, max_lead + 1):
        for size in (n, n - 1, n + 1):  # ASR may merge or split words
            if size < 1 or start + size > len(words):
                continue
            window = words[start:start + size]
            if size == n:
                # Same word count: compare word by word, so "her file" doesn't pass for "hey raphael".
                ok = sum(_lev(phonetic(a), b) for a, b in zip(window, targets)) <= tolerance
            else:
                ok = _lev(_key(window), target) <= tolerance
            if ok:
                return start + size
    # 2. The name alone ("Raphael, …"): the short "hey" is often swallowed by ASR (seen Oct 31:
    #    "Raphael.", "Paraphael."). Only at the very start or after filler words, so
    #    "The rifle was old" doesn't count.
    name = targets[-1]
    if n >= 2 and len(name) >= 3:
        for start in range(0, min(max_lead, len(words) - 1) + 1):
            if any(_clean(w) not in FILLERS for w in words[:start]):
                break
            k = phonetic(words[start])
            if k == name or (k.endswith(name) and len(k) <= len(name) + 1):  # "Paraphael" = "hey" glued on
                return start + 1
    return None


def match(text: str, phrases: list[str]) -> tuple[str, str] | None:
    """If `text` starts with a wake phrase, return (phrase, rest of text)."""
    words = text.split()
    for phrase in phrases:
        end = _match_at(words, phrase, MAX_LEAD_WORDS)
        if end is None:
            continue
        rest = words[end:]
        # Said it twice ("Hey Raphael… hey Raphael, what's …")? Drop the repeat too.
        while (again := _match_at(rest, phrase, 0)) is not None:
            rest = rest[again:]
        return phrase, " ".join(rest).lstrip(" ,.!?;:-")
    return None


def ruled_out(text: str, phrases: list[str]) -> bool:
    """True once enough words are in that the phrase can no longer appear at the start."""
    longest = max((len(p.split()) for p in phrases), default=2)
    return len(text.split()) > MAX_LEAD_WORDS + longest + 1 and match(text, phrases) is None
