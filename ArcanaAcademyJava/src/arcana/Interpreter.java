package arcana;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreter.java — ported from js/interpreter.js.
 *
 * Generates a 4-section interpretation:
 *   sections[0] = Past    (1 sentence)
 *   sections[1] = Present (1 sentence)
 *   sections[2] = Future  (1 sentence)
 *   sections[3] = Together (<= 2 sentences)
 *
 * ENGINE ORDER:
 *   1. Anthropic API (if API_KEY is set below)
 *   2. Local rule-based fallback — always available, no network needed
 *
 * ERROR HANDLING (mirrors the JS version):
 *   - The HTTP call is wrapped in try/catch; any network or parse error
 *     silently falls through to the local engine.
 *   - Non-200 HTTP responses are treated as failure.
 *   - The AI response is validated; any missing section triggers fallback.
 *   - A 10-second timeout is enforced on the request (10-second requirement).
 *   - clampSentences() enforces 1/1/1/2 limits on EVERY result
 *     regardless of which engine produced it.
 */
public final class Interpreter {

    /** Paste your Anthropic API key here, or leave empty to always use the local engine. */
    private static final String API_KEY = "";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final int TIMEOUT_MS = 10000; // 10-second requirement

    /** Result of an interpretation: the four sections + which engine produced them. */
    public static class Result {
        public final String[] sections;
        public final boolean fromAI;
        Result(String[] sections, boolean fromAI) {
            this.sections = sections;
            this.fromAI = fromAI;
        }
    }

    private Interpreter() {}

    /* ── Public entry point ────────────────────────────────────────── */
    public static Result interpret(String question, List<SelectedCard> cards) {
        String[] sections = null;
        boolean fromAI = false;

        if (!API_KEY.trim().isEmpty()) {
            try {
                String raw = callAnthropic(question, cards);
                if (raw != null) {
                    sections = parseSections(raw);
                    fromAI = sections != null;
                }
            } catch (Exception ignored) {
                // network failure / timeout / bad JSON -> fall through to local
            }
        }

        if (sections == null) {
            sections = localSections(cards);
        }

        // Enforce sentence limits regardless of engine
        sections[0] = clampSentences(sections[0], 1);
        sections[1] = clampSentences(sections[1], 1);
        sections[2] = clampSentences(sections[2], 1);
        sections[3] = clampSentences(sections[3], 2);

        return new Result(sections, fromAI);
    }

    /* ── Engine 1: Anthropic API ───────────────────────────────────── */
    private static String callAnthropic(String question, List<SelectedCard> cards) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
                .build();

        String body = "{"
                + "\"model\":\"claude-sonnet-4-6\","
                + "\"max_tokens\":400,"
                + "\"messages\":[{\"role\":\"user\",\"content\":"
                + jsonString(buildPrompt(question, cards))
                + "}]}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofMillis(TIMEOUT_MS)) // enforces the 10 s limit
                .header("Content-Type", "application/json")
                .header("x-api-key", API_KEY)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;

        String text = extractTextBlocks(response.body()).trim();
        return text.isEmpty() ? null : text;
    }

    private static String buildPrompt(String question, List<SelectedCard> cards) {
        String[] positions = { "Past", "Present", "Future" };
        StringBuilder cardLines = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            SelectedCard c = cards.get(i);
            if (i > 0) cardLines.append("\n");
            cardLines.append(positions[i]).append(": ").append(c.card.name)
                     .append(" \u2014 ").append(c.reversed ? "REVERSED" : "upright")
                     .append(" (").append(c.activeMeaning()).append(")");
        }
        return "You are a warm tarot teacher helping a beginner learn comparative 3-card readings. "
                + "The querent asked: \"" + question + "\". Their cards:\n" + cardLines
                + "\n\nRespond in EXACTLY this format, nothing else:\n"
                + "PAST: <one sentence applying the past card to the question>\n"
                + "PRESENT: <one sentence applying the present card to the question>\n"
                + "FUTURE: <one sentence applying the future card to the question>\n"
                + "TOGETHER: <at most two sentences comparing the three cards as one story and inviting reflection>";
    }

    /**
     * Validates and extracts the four labelled sections from the AI response.
     * Returns null if any label is missing or out of order — triggers fallback.
     */
    static String[] parseSections(String raw) {
        String[] labels = { "PAST:", "PRESENT:", "FUTURE:", "TOGETHER:" };
        int[] starts = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            starts[i] = raw.indexOf(labels[i]);
            if (starts[i] < 0) return null;
        }
        for (int i = 1; i < starts.length; i++) {
            if (starts[i] <= starts[i - 1]) return null;
        }
        String[] out = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            int from = starts[i] + labels[i].length();
            int to = i < labels.length - 1 ? starts[i + 1] : raw.length();
            String text = raw.substring(from, to).trim();
            if (text.isEmpty()) return null;
            out[i] = text;
        }
        return out;
    }

    /* ── Engine 2: Local rule-based fallback ───────────────────────── */
    static String[] localSections(List<SelectedCard> cards) {
        SelectedCard past = cards.get(0), present = cards.get(1), future = cards.get(2);
        return new String[] {
            past.card.shortName() + orientation(past) + " shows that "
                + lower(past.activeMeaning()) + " shaped how this question arose.",
            present.card.shortName() + orientation(present) + " points to "
                + lower(present.activeMeaning()) + " at the heart of your situation right now.",
            future.card.shortName() + orientation(future) + " suggests "
                + lower(future.activeMeaning()) + " is where this is heading.",
            "The spread moves from " + keyword(past) + " through " + keyword(present)
                + " toward " + keyword(future)
                + ". Ask which of these three energies feels strongest to you \u2014 that is where your answer lives."
        };
    }

    /* ── Helpers ───────────────────────────────────────────────────── */
    private static String orientation(SelectedCard c) {
        return c.reversed ? " (reversed)" : "";
    }

    /** First keyword of the active meaning — text before the first comma or " and ". */
    private static String keyword(SelectedCard c) {
        String m = c.activeMeaning();
        Matcher matcher = Pattern.compile(",| and ").matcher(m);
        return matcher.find() && matcher.start() > 0 ? m.substring(0, matcher.start()) : m;
    }

    private static String lower(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** Keeps at most `max` sentences of the text (same regex as the JS version). */
    static String clampSentences(String text, int max) {
        if (text == null) return "";
        String[] sentences = text.trim().split("(?<=[.!?])\\s+");
        if (sentences.length <= max) return text.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            if (i > 0) sb.append(" ");
            sb.append(sentences[i]);
        }
        return sb.toString().trim();
    }

    /* ── Minimal JSON helpers (no external libraries needed) ───────── */

    /** Encodes a string as a JSON string literal. */
    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append("\"").toString();
    }

    /**
     * Pulls the "text" values out of the API response's content blocks and joins
     * them — the Java equivalent of the .filter(type==="text").map(.text).join("")
     * chain in the JS version. A tiny purpose-built scanner, not a full JSON parser.
     */
    static String extractTextBlocks(String json) {
        List<String> texts = new ArrayList<>();
        Matcher m = Pattern.compile("\"text\"\\s*:\\s*\"").matcher(json);
        while (m.find()) {
            int i = m.end();
            StringBuilder sb = new StringBuilder();
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char n = json.charAt(i + 1);
                    switch (n) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'u' -> {
                            if (i + 5 < json.length()) {
                                sb.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                                i += 4;
                            }
                        }
                        default -> sb.append(n);
                    }
                    i += 2;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            texts.add(sb.toString());
        }
        return String.join("", texts);
    }
}
