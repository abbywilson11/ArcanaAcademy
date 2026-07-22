package arcana;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deck.java — builds the full 78-card deck, ported from buildDeck() in js/deck.js.
 */
public final class Deck {

    public static final List<Card> CARDS = Collections.unmodifiableList(build());

    private Deck() {}

    private static List<Card> build() {
        List<Card> deck = new ArrayList<>(78);

        // Major Arcana — full data supplied per card
        for (String[] m : DeckData.MAJORS) {
            deck.add(new Card(m[0], m[1], m[2], m[3], m[4], m[5]));
        }

        // Minor Arcana — generated from suit x rank tables
        for (int s = 0; s < DeckData.SUITS.length; s++) {
            for (int r = 0; r < DeckData.RANKS.length; r++) {
                String name    = DeckData.RANKS[r] + " of " + DeckData.SUITS[s];
                String meaning = DeckData.RANK_THEMES[r] + " " + DeckData.SUIT_THEMES[s];
                String lesson  = "The " + name + " combines two ideas every tarot learner should know. "
                        + DeckData.RANK_LESSONS[r] + " " + DeckData.SUIT_LESSONS[s] + " "
                        + "Read together, this card speaks to " + meaning + " \u2014 "
                        + "watch how that theme shifts depending on the cards beside it.";
                String reversedMeaning = "blocked or excessive " + DeckData.RANK_THEMES[r] //reversed meaning is a short summary of the card's theme, not the full lesson
                        + " " + DeckData.SUIT_THEMES[s];
                String reversedLesson = "Reversed, the " + name + " suggests " //reversed lesson is a longer explanation of the reversed meaning, not the full lesson
                        + DeckData.RANK_REVERSED[r] + " within the domain of "
                        + DeckData.SUIT_THEMES[s] + ". When this card appears inverted, the usual energy of the "
                        + DeckData.RANKS[r] + " is either blocked, excessive, or turned inward. "
                        + "Ask yourself where resistance or imbalance might be at play.";
                deck.add(new Card(name, meaning, DeckData.SUIT_SYMS[s], lesson, 
                        reversedMeaning, reversedLesson)); //add the card to the deck with its reversed data
            }
        }
        return deck;
    }
}
