# Arcana Academy — Tarot Interpretation Learning App (Java Edition)
### SEG2105 – Introduction to Software Engineering

A tarot card interpretation **learning tool**, now a Java desktop application
(Java Swing — plain Java, **not** JavaScript, **no Android Studio needed**).
Users select 3 cards from a searchable full 78-card deck, mark any of them
reversed, type a question, and receive a structured interpretation
(Past / Present / Future / Together). A Learn tab teaches every card
(upright **and** reversed), and every reading is saved to the Memory tab.

This is a full port of the original web version (`index.html` + `js/*.js`)
into standard Java. It uses **zero external libraries** — only the JDK.

---

## Requirements

- A **JDK (Java Development Kit), version 17 or newer.**
  A JRE alone is not enough because the run scripts compile the code first.
  - Check with: `javac -version` in a terminal.
  - Free download if you don't have one: https://adoptium.net (pick the
    latest LTS, e.g. Temurin 21). No IDE required — and **no Android Studio.**

---

## How to run

### Option A — one double-click / one command (recommended)

- **Windows:** double-click `run.bat`
  (or run `run.bat` from Command Prompt inside this folder).
- **Mac / Linux:** open a terminal in this folder and run:
  ```
  ./run.sh
  ```

The script compiles all the source files into `bin/` and launches the app window.

### Option B — manual commands (any OS)

```
cd ArcanaAcademyJava
javac -d bin src/arcana/*.java
java -cp bin arcana.ArcanaApp
```

### Enabling AI interpretations (optional)

Without an API key, the built-in learning-guide engine handles every reading
automatically — the app is fully functional offline.

To enable real AI interpretations:
1. Open `src/arcana/Interpreter.java`.
2. Paste your Anthropic API key into the `API_KEY` constant at the top.
3. Re-run `run.bat` / `run.sh` (they recompile automatically).

The AI engine runs within the 10-second timeout. If it fails or times out,
the local engine takes over automatically — the user always gets an
interpretation.

---

## File structure

```
ArcanaAcademyJava/
├── run.bat                     One-click compile & run (Windows)
├── run.sh                      One-click compile & run (Mac/Linux)
├── src/arcana/
│   ├── ArcanaApp.java          Main window + controller (tabs, grids, reading, memory, toast)
│   ├── Card.java               Card model (upright + reversed data)
│   ├── SelectedCard.java       A chosen card + its orientation
│   ├── Deck.java               Builds the full 78-card deck
│   ├── DeckData.java           Raw card data (ported verbatim from js/deck.js)
│   ├── Interpreter.java        AI engine + local fallback, 4-section output
│   ├── Storage.java            File-based persistence for the Memory tab
│   └── SimpleDocListener.java  Helper for live search
└── README.md                   This file
```

## Requirements traceability

| Requirement | Implementation |
|---|---|
| Access to all 78 tarot cards | `Deck.java` builds exactly 78 cards; both Home and Learn grids display all of them |
| Search for specific cards | Search fields on Home and Learn tabs filter live on `card.name` |
| Type and enter a question | Question text area on Home tab, validated before interpretation |
| Interpretation loads within 10 seconds | 10 s `HttpRequest` timeout in `Interpreter.java`; local engine is instant |
| Interpretation: 1 sentence each for Past, Present, Future + ≤2 sentence Together | Enforced by `clampSentences()` on every result regardless of engine |
| Access previous questions and interpretations | Memory tab — `Storage.loadAll()` reads the readings file, expandable entries |
| Learn about specific cards | Learn tab — click any card for a lesson dialog, including its reversed meaning |
| Reversed cards (major enhancement) | "Set Reversed" toggle on selected cards; reversed meanings flow into both engines, the reading display, and the Learn dialog |
| Mystical theme | Midnight indigo / celestial gold / moon lavender palette; serif headers |
| Home page = card selection + question entry | Home tab in `ArcanaApp.java` |

---

## Error handling summary (for report)

| File | Protection |
|---|---|
| `Interpreter.java` | 10 s request timeout; non-200 responses rejected; all network/parse errors caught → local fallback; AI responses missing any of the 4 sections rejected → local fallback; `clampSentences()` enforces 1/1/1/2 sentence limits on every result |
| `ArcanaApp.java` | Toast + block when not exactly 3 cards selected; toast + block on empty question; reveal button disabled while a reading is in flight; interpreter runs on a background thread so the UI can't freeze; try/catch around the interpret call; empty-state message on Memory tab |
| `Storage.java` | `saveReading()` wrapped in try/catch — a disk failure never crashes the reading display; `loadAll()` returns an empty list on any error; corrupted lines skipped; entries without exactly 4 sections filtered out; fields URL-encoded so pipes/newlines in questions can't corrupt the file |


## AI tools used

- **Claude (Anthropic)** — primary code generation, prompt engineering, and
  iterative refinement, including the JavaScript → Java port. See the prompt
  engineering log for the full interaction history.
- **Anthropic Messages API** — used at runtime to generate AI interpretations
  when an API key is configured (model: `claude-sonnet-4-6`).
