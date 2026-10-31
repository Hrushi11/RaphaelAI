# Raphael — UI Wireframe

Two parts:

- **Part A — v0 (ships Oct 31).** Matches `spec/spec.md` and the app in `app-android/`. Use this for the demo video.
- **Part B — Vision (post-v0).** Whisper mode, context, post-meeting brief, Bolna calls. Out of v0 scope (spec §1.2). Use this for the pitch, clearly labelled "coming next".

Style: v0 uses **Material 3 defaults** (spec §3.1: no custom design). The glassmorphism prompts at the end are for pitch visuals only.

---

## Part A — v0: the wake-word assistant

The core loop has almost no UI: **say "Hey Raphael" (or tap your earbud) → ask → hear the answer in your ear in ~1.5 s**, phone in your pocket. The screens exist to set this up and to show what's happening.

### A1. First run — Permissions

- **Title:** "Raphael"
- **Text:** "Raphael needs the microphone and Bluetooth (nearby devices) permissions."
- **Primary button:** "Grant permissions". Opens Android's prompts for Microphone, Nearby devices and Notifications.

### A2. Home — Setup (not connected)

- **Header:** "Raphael"
- **Backend URL** field: `ws://192.168.0.5:8000/v1/session` (see README → "Connect the phone app").
- **Mic source** (radio):
  - **Headset mic (call link)**: phone in pocket. The buds become a call-quality mic and speaker.
  - **Phone mic, reply on buds**: phone on the table. Better audio, music unaffected.
- **Headset list** (headset mode only): headsets *already paired* in Android Settings, e.g.
  - ◉ OnePlus Nord Buds 2 — Bluetooth call link (HFP)
  - Empty state: "No Bluetooth headset connected. Pair it in Android Settings → Bluetooth."
  - Raphael never scans or pairs by itself (spec §3.2).
- **Primary button:** "Connect"
- **Wake word card** (A4)
- **Secondary:** "Allow Raphael to run in the background" (shown only if Android may restrict it).
- **Text button:** "Headset audio test" (A5)

### A3. Home — Active (connected)

- **Status line:** "Connected · OnePlus Nord Buds 2 — Bluetooth call link (HFP)"
- **Hint:** "Keeps listening with the screen off. Tap your earbud to ask."
- **Big state button** (one button, label and colour follow the turn state):

  | State | Label | Tap does |
  |---|---|---|
  | Idle | "Tap to ask" (+ "Say your wake phrase, or tap the button.") | Starts a question |
  | Checking | "Hearing speech… (checking for wake phrase)" | Skips the check, starts a question |
  | Listening | "Listening… tap to finish" | Ends the question now |
  | Thinking | "Thinking… tap to stop" | Cancels |
  | Speaking | "Speaking… tap to stop" | Barge-in: stops the answer |

- **Last exchange:**
  - **You:** "What is 18% of 4 lakh?" (live words appear while you speak)
  - **Raphael:** "Eighteen percent of four lakh is seventy-two thousand."
- **Latency line** (small, real per-turn numbers from the backend): "end of speech → first audio ___ ms · silence wait ___ ms · LLM first sentence ___ ms · TTS ___ ms"
- **Secondary button:** "Disconnect"
- **Wake word card** (A4)

### A4. Wake word card

- **Toggle:** "Hands-free (wake word)" — on by default.
- **Privacy note:** "When you speak, that speech goes to the backend to check for the wake phrase. Speech without it is discarded; silence never leaves the phone."
- **Wake phrase** field: "hey raphael" → **"Save wake phrase"** (active instantly, no download or retraining).
- **Status:** "Active: “hey raphael” · other speech ignored: 3"
- **Recent wake detections:** `15:14:47  “hey raphael”` (last 5)

### A5. Headset audio test

- Pick headset + mic source → **"Record 5 s and play back"**, with a live level bar.
- **Result:** "PASS — mic and speaker both routed correctly", plus a log showing which device Android actually used.

### A6. Notification (screen off / lock screen)

Always visible while Raphael can hear you (spec §7.3).

- **Title:** Raphael
- **Text follows the state:** "Say “hey raphael” or tap your earbud" → "Listening…" → "Thinking…" → "Speaking…" / "Paused — not listening" / "Reconnecting…"
- **Actions:** **Ask** · **Pause / Resume** · **Stop**

### A7. In-ear audio (the real interface)

| Moment | User hears |
|---|---|
| Wake phrase recognised, or earbud tapped | Short beep |
| Answer | Raphael's voice, ≤ 2 sentences, numbers spoken ("seventy-two thousand") |
| Ordinary speech without the wake phrase | Nothing |

### Not in v0 (spec lists these; build later)

- Follow-up window (ask again within 5 s without the wake word)
- Assistant settings (persona, answer length, voice, Hinglish)
- Privacy screen (keep or delete transcripts)
- Earbud-tap test on the Headset screen

---

## Part B — Vision (post-v0, for the pitch)

The long-term product: an ambient copilot that listens to live conversations and whispers useful facts at the right moment. Each screen below builds on the v0 loop.

### B1. Prepare Context (the Brain)

- **Header:** "Prepare context" (Back · Settings)
- **Session name:** "Q3 Vendor Negotiations"
- **Add knowledge:** upload contracts, pricing sheets, past notes (PDF, DOCX, TXT)
  - 📄 Vendor_Pricing_Sheet_Q3.pdf (✕)
  - 📄 Past_Commitments_June.txt (✕)
- **Consent step (required):** "Everyone in this conversation must know Raphael is listening." ☐ "I have told the other participants." Recording other people without consent is illegal in many places, so this gate must be real, not decorative.
- **Primary button:** "Start session". Starts whisper mode. Headset pairing is already done in v0 (A2), so there is no setup screen here.

### B2. Live Session (whisper mode)

- **Header:** "Live: Vendor Negotiations" · **Pause listening** toggle (always one tap away)
- **Visible recording indicator:** red dot + "Listening to this conversation"
- **Live transcript feed:**
  - **Them · 10:14:** "We can do the enterprise tier for ₹4L today."
  - **Raphael whisper** (highlighted card, also spoken quietly in your ear): "Contradiction: they quoted ₹3.5L in Monday's email." Source: Vendor_Pricing_Sheet_Q3.pdf
  - **You · 10:15:** "I thought we agreed on 3.5L?"
- **Ask on demand** (same as v0): say "Hey Raphael…" or tap the earbud. The answer comes privately in your ear. A typed "Ask Raphael quietly…" field is optional, for when you can't speak.
- The phone screen is a companion view; the product works with the phone in your pocket.

### B3. Session Brief (after the conversation)

- **Header:** "Session complete" · **Export** · **Delete everything**
- **Key points** (card)
- **Contradictions caught** (card): "Price mismatch: ₹4L quoted vs ₹3.5L in email (resolved)"
- **Commitments** (checkboxes):
  - ☐ Vendor sends updated deck by Friday
  - ☐ You approve API access
- **Act after the meeting (Bolna):**
  - **"Call vendor to confirm Friday delivery"**: a Bolna voice agent places the call. Shows a preview of what the agent will say, and needs your confirmation before dialling.
  - **"Call me with a 30-second recap"**: Bolna calls you.

---

## Image-generator prompts (pitch visuals only, not the v0 app)

**Live Session (B2):** "Dark mode mobile UI mockup for an AI earpiece copilot during a live negotiation. Top: a small audio-reactive visualizer made of translucent purple and blue glass bands, and a clear red 'listening' indicator with a pause toggle. Middle: a live transcript with speaker labels ('Them', 'You'); one highlighted card with a soft violet glow labelled 'Raphael whisper' shows a price contradiction and a source file chip. Bottom: a microphone button with an audio waveform. Charcoal background, glassmorphism, clean sans-serif, premium and professional."

**Prepare Context (B1):** "Dark mode mobile UI mockup, 'Prepare context' screen for an AI earpiece copilot. A session-name field, a frosted-glass upload card listing PDF and text files, a consent checkbox reading 'I have told the other participants', and a violet primary button 'Start session'. Charcoal background, violet and deep-blue accents, minimalist, premium."

**Session Brief (B3):** "Dark mode mobile UI mockup of a post-meeting brief. Frosted-glass cards for 'Key points' and 'Contradictions caught', a commitment checklist, and a gradient blue-to-violet button 'Call vendor to confirm (Bolna voice agent)' with a smaller secondary button 'Call me with a recap'. Professional, futuristic, executive-focused."
