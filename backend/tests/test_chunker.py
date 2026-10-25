from raphael.chunker import SentenceChunker


def run(deltas: list[str]) -> list[str]:
    c = SentenceChunker()
    out: list[str] = []
    for d in deltas:
        out += c.feed(d)
    return out + c.flush()


def test_splits_on_sentence_end():
    assert run(["Hello there. ", "How are", " you? Fine"]) == ["Hello there.", "How are you?", "Fine"]


def test_emits_as_soon_as_sentence_is_certain():
    c = SentenceChunker()
    assert c.feed("It is four lakh.") == []  # could still be "4.5"
    assert c.feed(" Next") == ["It is four lakh."]


def test_keeps_decimals_and_abbreviations():
    assert run(["That is 4.5 percent. ", "Ask Dr. Rao, e.g. today."]) == [
        "That is 4.5 percent.",
        "Ask Dr. Rao, e.g. today.",
    ]


def test_token_by_token():
    text = "Eighteen percent of four lakh is seventy two thousand. Anything else?"
    assert run(list(text)) == ["Eighteen percent of four lakh is seventy two thousand.", "Anything else?"]


def test_long_run_on_cut_at_comma():
    c = SentenceChunker(soft_limit=40)
    out = c.feed("This sentence just keeps going and going, with no end in sight ")
    assert out == ["This sentence just keeps going and going,"]


def test_newline_ends_chunk():
    assert run(["First line\nSecond line"]) == ["First line", "Second line"]
