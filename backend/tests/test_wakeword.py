from raphael import wakeword

PHRASES = ["hey raphael"]


def test_matches_spellings_and_returns_question():
    assert wakeword.match("Hey Raphael, what's the time?", PHRASES) == ("hey raphael", "what's the time?")
    assert wakeword.match("hey rafael what is 18% of 4 lakh", PHRASES) == ("hey raphael", "what is 18% of 4 lakh")
    assert wakeword.match("Okay. Hey, Raphael.", PHRASES) == ("hey raphael", "")


def test_rejects_other_speech():
    for text in ["How far is it to the station?", "Hey, how are you doing today?", "The rifle was old.", "I was talking to Rachel"]:
        assert wakeword.match(text, PHRASES) is None, text


def test_ruled_out_only_after_enough_words():
    assert not wakeword.ruled_out("hey how", PHRASES)
    assert wakeword.ruled_out("so I was thinking that we could", PHRASES)
    assert not wakeword.ruled_out("hey raphael what is the time now", PHRASES)


def test_other_phrases():
    assert wakeword.match("okay rafael play music", ["okay raphael"]) == ("okay raphael", "play music")
