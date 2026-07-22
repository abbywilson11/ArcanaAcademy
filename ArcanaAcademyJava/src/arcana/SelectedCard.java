package arcana;

/**
 * SelectedCard.java — a card chosen for a reading, plus its orientation.
 * Mirrors the { card, reversed } entries kept in the `selected` Map in js/app.js,
 * and the { ...card, isReversed, activeMeaning } objects built at reveal time.
 */
public class SelectedCard {
    public final Card card;
    public boolean reversed;

    public SelectedCard(Card card) {
        this.card = card;
        this.reversed = false;
    }

    /** The meaning that applies given the card's orientation. */
    public String activeMeaning() {
        return reversed ? card.reversedMeaning : card.meaning;
    }
}
