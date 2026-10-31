from raphael import wakeword

PHRASES = ["hey raphael"]


def test_matches_spellings_and_returns_question():
    assert wakeword.match("Hey Raphael, what's the time?", PHRASES) == ("hey raphael", "what's the time?")
    assert wakeword.match("hey rafael what is 18% of 4 lakh", PHRASES) == ("hey raphael", "what is 18% of 4 lakh")
    assert wakeword.match("Okay. Hey, Raphael.", PHRASES) == ("hey raphael", "")


def test_rejects_other_speech():
    for text in ["How far is it to the station?", "Hey, how are you doing today?", "The rifle was old.", "I was talking to Rachel",
                 "Her file is on the table", "Hey Rachel, how are you?"]:
        assert wakeword.match(text, PHRASES) is None, text


def test_ruled_out_only_after_enough_words():
    assert not wakeword.ruled_out("hey how", PHRASES)
    assert wakeword.ruled_out("so I was thinking that we could", PHRASES)
    assert not wakeword.ruled_out("hey raphael what is the time now", PHRASES)


def test_other_phrases():
    assert wakeword.match("okay rafael play music", ["okay raphael"]) == ("okay raphael", "play music")


def test_name_alone_counts_at_the_start():
    # Seen live on Oct 31: Deepgram often drops the short "hey".
    assert wakeword.match("Raphael.", PHRASES) == ("hey raphael", "")
    assert wakeword.match("Raphael, what's the time?", PHRASES) == ("hey raphael", "what's the time?")
    assert wakeword.match("Paraphael.", PHRASES) == ("hey raphael", "")
    assert wakeword.match("Okay Raphael, how far is the moon", PHRASES) == ("hey raphael", "how far is the moon")


def test_name_alone_not_mid_sentence():
    for text in ["The rifle was old.", "I bought a raffle ticket", "Can you refill my glass"]:
        assert wakeword.match(text, PHRASES) is None, text


def test_repeated_wake_phrase_is_stripped():
    assert wakeword.match("Hey Raphael. Hey Raphael, what's the time?", PHRASES) == ("hey raphael", "what's the time?")
