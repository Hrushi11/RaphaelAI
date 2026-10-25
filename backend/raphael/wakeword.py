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


def match(text: str, phrases: list[str]) -> tuple[str, str] | None:
    """If `text` starts with a wake phrase, return (phrase, rest of text)."""
    words = text.split()
    for phrase in phrases:
        target = _key(phrase.split())
        if not target:
            continue
        n = len(phrase.split())
        tolerance = 1 if len(target) >= 5 else 0
        for start in range(0, MAX_LEAD_WORDS + 1):
            for size in (n, n - 1, n + 1):  # ASR may merge or split words
                if size < 1 or start + size > len(words):
                    continue
                if _lev(_key(words[start:start + size]), target) <= tolerance:
                    rest = " ".join(words[start + size:]).lstrip(" ,.!?;:-")
                    return phrase, rest
    return None


def ruled_out(text: str, phrases: list[str]) -> bool:
    """True once enough words are in that the phrase can no longer appear at the start."""
    longest = max((len(p.split()) for p in phrases), default=2)
    return len(text.split()) > MAX_LEAD_WORDS + longest + 1 and match(text, phrases) is None
