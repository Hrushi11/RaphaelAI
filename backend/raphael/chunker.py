"""Cuts a streaming LLM token stream into sentences for TTS (spec §4.1 step 6).

A sentence is emitted as soon as its end is certain, so speech can start before
the LLM finishes. A terminator only counts once the next character is
whitespace, which keeps "4.5" and "e.g." intact.
"""

import re

_TERMINATORS = ".!?…"
# Words that end with a period but don't end a sentence.
_ABBREVIATIONS = {"mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "e.g", "i.e", "approx", "rs"}
# Long run-on text is cut at a comma/semicolon after this many characters.
_SOFT_LIMIT = 160


class SentenceChunker:
    def __init__(self, soft_limit: int = _SOFT_LIMIT) -> None:
        self._buf = ""
        self._soft_limit = soft_limit

    def feed(self, delta: str) -> list[str]:
        """Add LLM text; return any sentences that are now complete."""
        self._buf += delta
        out: list[str] = []
        while True:
            cut = self._find_cut()
            if cut is None:
                break
            sentence, self._buf = self._buf[:cut].strip(), self._buf[cut:].lstrip()
            if sentence:
                out.append(sentence)
        return out

    def flush(self) -> list[str]:
        """Return whatever is left at the end of the stream."""
        rest, self._buf = self._buf.strip(), ""
        return [rest] if rest else []

    def _find_cut(self) -> int | None:
        buf = self._buf
        for i, ch in enumerate(buf):
            if ch == "\n" and buf[:i].strip():
                return i + 1
            if ch in _TERMINATORS and i + 1 < len(buf) and buf[i + 1].isspace():
                if ch == "." and self._is_abbreviation(buf[: i + 1]):
                    continue
                return i + 1
        if len(buf) > self._soft_limit:
            m = None
            for m in re.finditer(r"[,;:]\s", buf):
                pass
            if m:
                return m.end()
        return None

    @staticmethod
    def _is_abbreviation(text: str) -> bool:
        word = re.split(r"\s", text)[-1].rstrip(".").lower()
        return word in _ABBREVIATIONS or (len(word) == 1 and word.isalpha())
