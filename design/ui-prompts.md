# Raphael — UI journey: image prompts + design tokens

Reference look: the "AI Voice Chat" render (dark navy background, glowing glass diamond, transcript card, waveform, three round controls).
Every screen below has a **sketch prompt** (black-ink wireframe, left side of the reference) and a **hi-fi prompt** (polished render, right side).
Generate the hi-fi screens with the same tool and seed if it supports one, and feed **Screen 5** back in as a style reference for all the others, so the whole set looks like one app.

> Image generators garble small text. Treat the text in the images as layout guidance; the exact strings below are what we code.

---

## 0. Shared blocks (paste into every prompt)

**SKETCH STYLE:**
"Hand-drawn low-fidelity mobile wireframe, black ink marker on white paper, loose wobbly lines, Android phone outline with rounded corners and a thin status bar, handwritten labels, no color, no shading, no gradients, plain white background, single phone centered, portrait 9:19.5."

**HI-FI STYLE:**
"High-fidelity Android app UI mockup, single phone screen, portrait 9:19.5, edge to edge. Deep navy-black background (#05060F fading to #0B0D2A at the bottom) with soft violet and blue ambient glow. Signature element: a glowing glass diamond made of overlapping translucent violet (#7C4DFF) and electric-blue (#3D7BFF) ribbon bands, with a faint teal (#2EE6D6) light pool beneath it. Glassmorphism cards (6% white fill, thin violet border, 24 dp radius). Clean geometric sans-serif (Inter), off-white text (#E8E9F5), secondary text lavender-grey (#9AA0C3). Android status bar at top (time, Wi-Fi, battery) and a thin Android gesture bar at the bottom. Premium, calm, minimal, no clutter, crisp, 4K."

**Avoid (negative prompt):** "iPhone, notch, iOS status bar, keyboard, text input box, chat bubbles, emojis, people, hands, extra phones, watermark, lorem ipsum, cluttered UI"

---

## The journey

```
First run:  1 Welcome → 2 Permissions → 3 Choose headset → 4 Wake word
Every day:  5 Home idle → 6 Listening → 7 Thinking → 8 Speaking → back to 5
Around it:  9 Paused · 10 Lock-screen notification · 11 Settings · 12 Can't connect
```

Common layout for the main screens (5–9), top to bottom:
1. Top bar: left **pill** "Raphael · Nord Buds 2 ⌄" (tap = switch headset), right **round gear** button.
2. **Diamond** visualizer, upper-middle.
3. **Status chip** under the diamond (small dot + text).
4. **Glass card**: what you said / what Raphael says / a hint.
5. **Waveform** line.
6. **Three round controls**: ✕ Stop (left, small) · 🎤 Ask (center, large, glowing) · ‖ Pause listening (right, small).

---

### 1. Welcome

**Text:** title "Raphael" · subtitle "Your advisor, in your ear." · button "Get started" · small text "Works with any Bluetooth earbuds"

**Sketch:** "[SKETCH STYLE] Screen: large diamond outline in the upper half, below it the handwritten title 'Raphael' and subtitle 'Your advisor, in your ear.', a wide rounded button at the bottom labelled 'Get started', small text under it 'Works with any Bluetooth earbuds'."

**Hi-fi:** "[HI-FI STYLE] Welcome screen. The glass diamond floats large in the upper half, glowing softly, light pool below. Under it the title 'Raphael' in large semibold off-white, subtitle 'Your advisor, in your ear.' in lavender-grey. At the bottom a wide pill button with a violet-to-blue gradient and soft outer glow labelled 'Get started', and below it small grey text 'Works with any Bluetooth earbuds'. Lots of breathing room."

### 2. Permissions

**Text:** title "Raphael needs a few things" · rows: 🎤 "Microphone — to hear you" · 🎧 "Nearby devices — to use your earbuds" · 🔔 "Notifications — so you always know when Raphael can hear you" · button "Allow" · footnote "Silence never leaves your phone."

**Sketch:** "[SKETCH STYLE] Screen: title 'Raphael needs a few things' at top, three list rows each with a small circle icon on the left and two lines of text ('Microphone — to hear you', 'Nearby devices — to use your earbuds', 'Notifications — so you always know when Raphael can hear you'), a wide button 'Allow' at the bottom, tiny footnote 'Silence never leaves your phone.'"

**Hi-fi:** "[HI-FI STYLE] Permissions screen. Small dim diamond icon at the top, title 'Raphael needs a few things'. Three stacked glass cards, each with a glowing line icon in a round violet tile on the left (microphone, earbuds, bell) and a title plus one-line description. Wide gradient pill button 'Allow' at the bottom, small grey footnote 'Silence never leaves your phone.' with a tiny lock icon."

### 3. Choose headset

**Text:** title "Choose your earbuds" · list (radio): "OnePlus Nord Buds 2" (selected, "Connected") · "Sony WH-1000XM5" ("Paired") · link "Pair new earbuds in Bluetooth settings" · section "Where should Raphael listen?" segmented control **Earbuds mic** | **Phone mic** · helper "Earbuds mic: phone can stay in your pocket." · button "Continue"

**Sketch:** "[SKETCH STYLE] Screen: title 'Choose your earbuds', two list rows with radio circles and earbud icons ('OnePlus Nord Buds 2 — Connected' selected, 'Sony WH-1000XM5 — Paired'), an underlined link 'Pair new earbuds in Bluetooth settings', then a heading 'Where should Raphael listen?' with a two-part toggle 'Earbuds mic | Phone mic', one helper line, and a wide 'Continue' button at the bottom."

**Hi-fi:** "[HI-FI STYLE] Headset picker. Title 'Choose your earbuds'. Two glass list cards with earbud icons; the selected one 'OnePlus Nord Buds 2' has a glowing violet border, a filled radio and a small green 'Connected' tag; the other 'Sony WH-1000XM5' shows a grey 'Paired' tag. A small blue text link 'Pair new earbuds in Bluetooth settings'. Below, the heading 'Where should Raphael listen?' and a pill segmented control with 'Earbuds mic' selected (violet fill) and 'Phone mic'. Helper text in grey. Gradient 'Continue' pill button at the bottom."

### 4. Wake word

**Text:** title "Your wake word" · field "hey raphael" · test card "Try it — say “Hey Raphael”" → success state "✓ Heard you" · toggle "Hands-free" (on) · privacy note "Raphael checks speech for your wake word and discards everything else." · button "Done"

**Sketch:** "[SKETCH STYLE] Screen: title 'Your wake word', a text field containing 'hey raphael', a dashed card with a small diamond and the text 'Try it — say “Hey Raphael”' and a check mark with 'Heard you', a toggle switch row 'Hands-free' (on), a small paragraph of privacy text, and a wide 'Done' button."

**Hi-fi:** "[HI-FI STYLE] Wake word setup. Title 'Your wake word'. A glass text field showing 'hey raphael' with an edit pencil icon. Below it a glass card with a small glowing diamond on the left and the text 'Try it — say “Hey Raphael”', with a teal check mark and 'Heard you' on the right, a faint waveform behind it. A toggle row 'Hands-free' switched on (violet). Small lavender-grey privacy note with a shield icon: 'Raphael checks speech for your wake word and discards everything else.' Gradient 'Done' pill button at the bottom."

### 5. Home — idle (hero screen; use as style reference)

**Text:** pill "Raphael · Nord Buds 2 ⌄" · chip "● Listening for “Hey Raphael”" (green dot) · card hint "Say “Hey Raphael” or tap your earbud" · controls ✕ / 🎤 / ‖

**Sketch:** "[SKETCH STYLE] Screen: top-left a rounded pill 'Raphael · Nord Buds 2 ⌄', top-right a circle with a gear. Large diamond outline in the center. Under it a small chip with a dot 'Listening for “Hey Raphael”'. A rounded card 'Say “Hey Raphael” or tap your earbud'. A flat dotted horizontal line for the waveform. At the bottom three circles: small 'X' on the left, large microphone in the middle, small pause '||' on the right."

**Hi-fi:** "[HI-FI STYLE] Home screen, idle. Top-left a dark glass pill with a small violet sparkle icon and text 'Raphael · Nord Buds 2' with a chevron; top-right a round dark glass button with a gear icon. Center: the glass diamond, calm, glowing gently, light pool below. Under it a small chip with a green dot 'Listening for “Hey Raphael”'. A glass card with a tiny waveform icon and the hint 'Say “Hey Raphael” or tap your earbud' in off-white, 'Hey Raphael' in violet-blue. Below, a quiet waveform that is mostly flat dots. Bottom row: small dark round button with ✕, large glowing electric-blue round microphone button with a soft halo, small dark round button with a pause icon."

### 6. Home — listening (you're asking)

**Text:** chip "● Listening…" (blue) · card label "You" + live text "What is 18% of four lakh…" · center button shows mic with pulsing ring · caption under button "Tap to finish"

**Sketch:** "[SKETCH STYLE] Same layout as the home screen. The diamond is drawn bigger with motion lines around it. Chip 'Listening…'. The card has a small label 'You' and the text 'What is 18% of four lakh…'. The waveform is tall and spiky. The middle microphone circle has two concentric rings around it and a caption 'Tap to finish'."

**Hi-fi:** "[HI-FI STYLE] Home screen while the user speaks. Same layout as idle. The diamond is brighter and slightly expanded, its blue bands flaring outward. Chip with a blue pulsing dot 'Listening…'. Glass card with a small label 'You' and live transcript 'What is 18% of four lakh…' with the last word fading in. The waveform is tall and energetic in teal-blue, peaking in the center. The center microphone button glows brighter with two expanding ripple rings; small caption 'Tap to finish' beneath it."

### 7. Home — thinking

**Text:** chip "● Thinking…" (violet) · card "You" + "What is 18% of 4 lakh?" + three-dot loader · center button becomes ■ stop · caption "Tap to cancel"

**Sketch:** "[SKETCH STYLE] Same layout. Diamond with small swirl marks inside. Chip 'Thinking…'. Card: label 'You', text 'What is 18% of 4 lakh?', and three dots '• • •' below. Waveform is a gentle low wave. Middle button contains a small square, caption 'Tap to cancel'."

**Hi-fi:** "[HI-FI STYLE] Home screen while Raphael thinks. Same layout. The diamond's inner bands swirl and shimmer, glowing more violet than blue. Chip with a violet dot 'Thinking…'. Glass card: label 'You', the final question 'What is 18% of 4 lakh?', and three softly animated violet dots. Waveform a low, slow violet ripple. Center button shows a rounded stop square instead of the microphone; caption 'Tap to cancel'."

### 8. Home — speaking (the answer)

**Text:** chip "● Speaking" (violet) · card label "Raphael" + "Eighteen percent of four lakh is **seventy-two thousand**." · small latency tag "answered in 1.4 s" · center button ■ · caption "Tap to stop"

**Sketch:** "[SKETCH STYLE] Same layout. Diamond with sound-wave arcs coming out of it. Chip 'Speaking'. Card with label 'Raphael' and the text 'Eighteen percent of four lakh is seventy-two thousand.', with 'seventy-two thousand' underlined, and a tiny tag 'answered in 1.4 s'. Waveform tall. Middle button with a square, caption 'Tap to stop'."

**Hi-fi:** "[HI-FI STYLE] Home screen while Raphael answers. Same layout. The diamond pulses in rhythm with the voice, violet and blue bands breathing outward, light pool brighter. Chip with a violet dot 'Speaking'. Glass card with label 'Raphael' (small sparkle icon) and the answer 'Eighteen percent of four lakh is seventy-two thousand.' with 'seventy-two thousand' in a violet-to-blue gradient; a tiny grey tag 'answered in 1.4 s' at the card's bottom right. Waveform full and symmetrical in violet-blue. Center button shows a stop square with a soft halo; caption 'Tap to stop'."

### 9. Paused

**Text:** chip "○ Paused — Raphael can't hear you" (grey) · card "Your mic is off. Tap ▶ to resume." · center button ▶ "Resume" · right button highlighted

**Sketch:** "[SKETCH STYLE] Same layout. Diamond drawn with dashed lines (dormant). Chip with an empty dot 'Paused — Raphael can't hear you'. Card 'Your mic is off. Tap ▶ to resume.'. Waveform a straight flat line. Middle button contains a play triangle, caption 'Resume'."

**Hi-fi:** "[HI-FI STYLE] Home screen, paused. Same layout but subdued: the diamond is desaturated grey-violet with almost no glow, the light pool gone. Chip with a hollow grey dot 'Paused — Raphael can't hear you'. Glass card 'Your mic is off. Tap ▶ to resume.' in grey. Waveform a flat thin line. Center button shows a play triangle in muted blue with caption 'Resume'; the right pause button is outlined violet to show it is active."

### 10. Lock screen notification

**Text:** Android lock screen, time "10:14". Notification: app "Raphael" · "Say “hey raphael” or tap your earbud" · action buttons **Ask** · **Pause** · **Stop**

**Sketch:** "[SKETCH STYLE] An Android lock screen: big handwritten clock '10:14' at the top, a date line, and one notification card below with a small diamond icon, 'Raphael', the text 'Say “hey raphael” or tap your earbud', and three text buttons 'Ask   Pause   Stop'. Lock icon at the bottom."

**Hi-fi:** "Android 16 lock screen on a Pixel phone, dark wallpaper with a faint violet-blue glass diamond glow. Large thin clock '10:14' and the date. One Material You notification card: small white diamond app icon, app name 'Raphael', text 'Say “hey raphael” or tap your earbud', and three action buttons 'Ask', 'Pause', 'Stop'. Fingerprint icon at the bottom. Realistic, crisp, 4K. [Avoid: iPhone, notch]"

### 11. Settings

**Text:** title "Settings" · sections:
- **Connection:** "Server" `ws://192.168.0.5:8000` · status "● Connected"
- **Earbuds:** "OnePlus Nord Buds 2" · "Listen with: Earbuds mic"
- **Wake word:** "hey raphael" · toggle "Hands-free"
- **Background:** "Keep Raphael running" (toggle / battery exemption)
- **Tools:** "Headset audio test"
- footer "Raphael v0.4"

**Sketch:** "[SKETCH STYLE] Screen: back arrow and title 'Settings'. Grouped list with section headers 'Connection', 'Earbuds', 'Wake word', 'Background', 'Tools'; rows with a label on the left and value or toggle on the right ('Server — ws://192.168.0.5:8000', 'Connected' with a dot, 'OnePlus Nord Buds 2', 'Listen with — Earbuds mic', 'hey raphael', 'Hands-free' toggle, 'Keep Raphael running' toggle, 'Headset audio test >'). Footer 'Raphael v0.4'."

**Hi-fi:** "[HI-FI STYLE] Settings screen, no diamond. Back arrow and title 'Settings'. Grouped glass cards with small uppercase lavender section headers: CONNECTION, EARBUDS, WAKE WORD, BACKGROUND, TOOLS. Rows with line icons in round violet tiles, labels on the left, values or violet toggles on the right: 'Server  ws://192.168.0.5:8000' with a green 'Connected' dot, 'OnePlus Nord Buds 2', 'Listen with  Earbuds mic', 'Wake word  hey raphael', 'Hands-free' toggle on, 'Keep Raphael running' toggle on, 'Headset audio test' with a chevron. Small grey footer 'Raphael v0.4'."

### 12. Can't connect (error)

**Text:** chip "● Can't reach Raphael's server" (amber) · card "Check that your phone and computer are on the same Wi-Fi, then try again." + link "Server settings" · center button ↻ "Retry"

**Sketch:** "[SKETCH STYLE] Same home layout. Diamond with a small crack line and an exclamation mark. Chip 'Can't reach Raphael's server'. Card with two lines of help text and an underlined link 'Server settings'. Waveform flat. Middle button contains a circular arrow, caption 'Retry'."

**Hi-fi:** "[HI-FI STYLE] Home screen, connection error. Same layout, diamond dimmed with a faint amber (#FFB547) rim glow. Chip with an amber dot 'Can't reach Raphael's server'. Glass card: 'Check that your phone and computer are on the same Wi-Fi, then try again.' and a small blue link 'Server settings'. Flat waveform. Center button shows a circular retry arrow in amber-tinted blue, caption 'Retry'."

---

## Design tokens (for coding the Compose UI)

| Token | Value | Use |
|---|---|---|
| `bg.top` → `bg.bottom` | `#05060F` → `#0B0D2A` | Screen background gradient |
| `violet` | `#7C4DFF` | Primary accent, selected states, "thinking/speaking" |
| `blue` | `#3D7BFF` | Mic button, "listening" |
| `teal` | `#2EE6D6` | Light pool, success, user's waveform |
| `amber` | `#FFB547` | Warnings / connection errors |
| `green` | `#3DDC84` | "Connected", "Listening for wake word" dot |
| `text` / `text.secondary` | `#E8E9F5` / `#9AA0C3` | Body / secondary |
| `glass.fill` / `glass.border` | white 6% / `#7C4DFF` 25% | Cards, pills, round buttons |
| Card radius / pill radius | 24 dp / 999 dp | |
| Mic button / side buttons | 88 dp / 56 dp | Bottom controls |
| Font | Inter (fallback Roboto) | Title 28 sp semibold, body 17 sp, caption 13 sp |

State → visual mapping (drives the diamond, chip colour and center button in code):

| App state | Diamond | Chip | Center button |
|---|---|---|---|
| Idle (hands-free) | calm glow | green "Listening for “Hey Raphael”" | 🎤 Ask |
| Checking (speech heard) | slight brighten | green, unchanged | 🎤 |
| Listening | bright, expanded, blue | blue "Listening…" | 🎤 + ripples, "Tap to finish" |
| Thinking | swirl, violet | violet "Thinking…" | ■ "Tap to cancel" |
| Speaking | pulses with voice | violet "Speaking" | ■ "Tap to stop" |
| Paused | grey, no glow | grey "Paused…" | ▶ "Resume" |
| Disconnected / error | dim, amber rim | amber "Can't reach…" | ↻ "Retry" |
