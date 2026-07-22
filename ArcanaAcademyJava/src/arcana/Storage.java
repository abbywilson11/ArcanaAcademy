package arcana;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Storage.java — ported from js/storage.js.
 *
 * The browser version used localStorage; a desktop app has no localStorage,
 * so readings are persisted to a plain text file in the user's home folder:
 *     ~/.arcana-academy/readings.txt
 * One reading per line; fields are URL-encoded so questions containing
 * "|" or newlines can never corrupt the file format.
 *
 * ERROR HANDLING (mirrors the JS version):
 *   - save() is wrapped in try/catch: disk may be unwritable or full;
 *     failure is silent so the reading still displays on screen.
 *   - loadAll() returns an empty list on any read/parse error rather than
 *     crashing the Memory tab.
 *   - Individual corrupted lines are skipped, not fatal.
 *   - Entries missing the expected 4 sections are filtered out.
 */
public final class Storage {

    /** One saved reading, mirroring { question, cardNames[], sections[], timestamp }. */
    public static class Entry {
        public final String question;
        public final List<String> cardNames;
        public final List<String> sections;
        public final long timestamp;
        public Entry(String question, List<String> cardNames, List<String> sections, long timestamp) {
            this.question = question;
            this.cardNames = cardNames;
            this.sections = sections;
            this.timestamp = timestamp;
        }
    }

    private static final Path DIR  = Path.of(System.getProperty("user.home"), ".arcana-academy");
    private static final Path FILE = DIR.resolve("readings.txt");

    private Storage() {}

    public static void saveReading(String question, List<SelectedCard> cards, String[] sections) {
        try {
            StringBuilder line = new StringBuilder();
            line.append(System.currentTimeMillis());
            line.append("|").append(enc(question));
            line.append("|").append(cards.size());
            for (SelectedCard c : cards) line.append("|").append(enc(c.card.name));
            for (String s : sections) line.append("|").append(enc(s));

            Files.createDirectories(DIR);
            List<String> lines = new ArrayList<>();
            if (Files.exists(FILE)) lines.addAll(Files.readAllLines(FILE, StandardCharsets.UTF_8));
            lines.add(line.toString());
            Files.write(FILE, lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // storage unavailable or disk full — display still works
        }
    }

    /** Returns readings newest-first. Never throws. */
    public static List<Entry> loadAll() {
        try {
            if (!Files.exists(FILE)) return new ArrayList<>();
            List<Entry> out = new ArrayList<>();
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                Entry e = parseLine(line);
                if (e != null) out.add(e); // corrupted entries skipped, not fatal
            }
            Collections.reverse(out); // newest first
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void clearAll() {
        try { Files.deleteIfExists(FILE); } catch (IOException ignored) {}
    }

    /* ── Internals ─────────────────────────────────────────────────── */

    private static Entry parseLine(String line) {
        try {
            String[] parts = line.split("\\|", -1);
            long ts = Long.parseLong(parts[0]);
            String question = dec(parts[1]);
            int nCards = Integer.parseInt(parts[2]);
            if (question.isEmpty()) return null;

            List<String> cardNames = new ArrayList<>();
            for (int i = 0; i < nCards; i++) cardNames.add(dec(parts[3 + i]));

            List<String> sections = new ArrayList<>();
            for (int i = 3 + nCards; i < parts.length; i++) sections.add(dec(parts[i]));
            if (sections.size() != 4) return null; // must have exactly 4 sections

            return new Entry(question, cardNames, sections, ts);
        } catch (Exception e) {
            return null;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String dec(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
