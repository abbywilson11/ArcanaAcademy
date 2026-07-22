package arcana;

/**
 * Card.java — one tarot card, ported from the card objects in js/deck.js.
 * Every card carries both upright and reversed data.
 */
public class Card {
    public final String name;
    public final String meaning;
    public final String symbol;
    public final String lesson;
    public final String reversedMeaning;
    public final String reversedLesson;

    public Card(String name, String meaning, String symbol, String lesson,
                String reversedMeaning, String reversedLesson) {
        this.name = name;
        this.meaning = meaning;
        this.symbol = symbol;
        this.lesson = lesson;
        this.reversedMeaning = reversedMeaning; 
        this.reversedLesson = reversedLesson;
    }

    /** "XVIII · The Moon" -> "The Moon"; minor arcana names pass through unchanged. */
    public String shortName() {
        int dot = name.indexOf("\u00b7 ");
        return dot >= 0 ? name.substring(dot + 2) : name;
    }
}
