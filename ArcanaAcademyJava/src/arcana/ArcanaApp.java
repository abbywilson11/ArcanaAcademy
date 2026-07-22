package arcana;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ArcanaApp — the main window of the Arcana Academy tarot learning app.
 *
 * HOW THE APP IS ORGANISED
 * ────────────────────────
 * The window is one JFrame with three "tabs" (Home / Learn / Memory) that
 * are swapped in and out of the centre of the frame using a CardLayout.
 * The bottom bar holds the navigation buttons plus a one-line "toast"
 * label used for temporary status messages.
 *
 *   • HOME   — pick exactly 3 cards from the deck, type a question, and
 *              press "Reveal Interpretation". The interpretation is
 *              produced off the UI thread (SwingWorker) and shown in a
 *              dialog, then saved to disk via {@link Storage}.
 *   • LEARN  — the same deck grid, but clicking a card opens a lesson
 *              dialog explaining its upright and reversed meanings.
 *   • MEMORY — a list of every past reading loaded from {@link Storage},
 *              each expandable/collapsible with a click.
 *
 * CLASSES THIS FILE DEPENDS ON (defined elsewhere in the package)
 *   • Deck / Card      — the 78-card data set (name, symbol, lessons).
 *   • SelectedCard     — a Card plus a "reversed" flag.
 *   • Interpreter      — turns (question, 3 cards) into 4 text sections.
 *   • Storage          — saves/loads/clears past readings on disk.
 *   • SimpleDocListener— tiny DocumentListener that runs a Runnable.
 *
 * VISUAL DESIGN
 * ─────────────
 *  • Dark purple theme with a single GOLD accent for primary actions and
 *    selection, LAVENDER for secondary emphasis, MUTED for hint text.
 *  • Modern rounded look: every surface (buttons, inputs, card tiles,
 *    list items, borders) is drawn with antialiased rounded corners via
 *    the small custom components at the bottom of this file
 *    (RoundedButton, RoundedPanel, RoundedTextField/Area, RoundedLineBorder).
 *  • An 8-px spacing scale (SP_1..SP_4) keeps padding consistent.
 *
 * ACCESSIBILITY
 * ─────────────
 *  • Full keyboard operability: tiles are focusable and respond to
 *    Enter/Space, Ctrl+Enter submits the question, Esc closes dialogs,
 *    Alt+1/2/3 switch tabs, R flips a selected card.
 *  • Visible focus indicators are drawn on every control (never removed,
 *    only restyled to match the theme).
 *  • The destructive "Clear all memory" action requires confirmation.
 *  • State changes are communicated with text, never colour alone.
 */
public class ArcanaApp extends JFrame {

    /* ══════════════════════ THEME CONSTANTS ═══════════════════════ */
    // Colour palette. Everything in the app draws from these ten colours
    // so the theme can be re-skinned by editing this block alone.
    static final Color BG        = new Color(0x15102B); // window background
    static final Color PANEL     = new Color(0x211A3E); // raised surfaces
    static final Color PANEL_HI  = new Color(0x2B2352); // borders / active nav
    static final Color PANEL_HOV = new Color(0x322A5E); // hover "lift"
    static final Color GOLD      = new Color(0xE8C36A); // primary accent
    static final Color GOLD_DIM  = new Color(0x8A7440); // disabled gold
    static final Color LAVENDER  = new Color(0xC9BFE8); // secondary accent
    static final Color TEXT      = new Color(0xEDE8F7); // body text
    static final Color MUTED     = new Color(0xA79CCB); // hints / captions
    static final Color DANGER    = new Color(0xE07A7A); // destructive action

    // Typography: serif for headings (mystical feel), sans for body text.
    static final Font TITLE_FONT  = new Font(Font.SERIF, Font.BOLD, 26);
    static final Font HEADER_FONT = new Font(Font.SERIF, Font.BOLD, 16);
    static final Font BODY_FONT   = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    static final Font BOLD_FONT   = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    static final Font SMALL_FONT  = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    static final Font SYMBOL_FONT = new Font(Font.SERIF, Font.PLAIN, 22);

    // Spacing scale (multiples of 4/8 px) — used for all padding/insets.
    static final int SP_1 = 4, SP_2 = 8, SP_3 = 16, SP_4 = 24;

    // Corner radii for the rounded look: large for cards/panels, medium
    // for inputs and buttons. Tweaking these two numbers changes how
    // "soft" the whole app feels.
    static final int RADIUS_LG = 16;
    static final int RADIUS_MD = 12;

    // Behavioural constants.
    static final int MAX_SELECTION = 3;   // a reading uses exactly 3 cards
    static final int GRID_COLS     = 4;   // deck grid column count
    static final int CARD_VIEWPORT = 270; // fixed height of the deck scroller

    /* ═══════════════════════════ STATE ════════════════════════════ */
    /** Cards the user has picked on the Home tab, in selection order.
     *  Keyed by the card's index in Deck.CARDS. LinkedHashMap preserves
     *  the order picked, which becomes Past → Present → Future. */
    private final Map<Integer, SelectedCard> selected = new LinkedHashMap<>();

    /** True while a reading is being generated; blocks double-submits. */
    private boolean loading = false;

    /* ════════════════════════ COMPONENTS ══════════════════════════ */
    // Tab switching: CardLayout shows one of three panels in tabHolder.
    private final CardLayout tabLayout = new CardLayout();
    private final JPanel     tabHolder = new JPanel(tabLayout);
    private final Map<String, RoundedButton> navButtons = new LinkedHashMap<>();

    // Home tab widgets (created in buildHomeTab, updated elsewhere).
    private JPanel        homeGrid;      // the deck grid being filtered
    private RoundedTextField homeSearch; // search box above the deck
    private JLabel        statusPill;    // "Cards chosen: n of 3"
    private RoundedTextArea questionInput;
    private RoundedButton revealBtn;     // primary gold action button
    private final List<CardTile> homeTiles = new ArrayList<>();

    // Learn tab widgets.
    private JPanel           learnGrid;
    private RoundedTextField learnSearch;
    private final List<CardTile> learnTiles = new ArrayList<>();

    // Memory tab list + toast message machinery.
    private JPanel memoryList;
    private JLabel toast;
    private Timer  toastTimer;

    /* ═══════════════════════ CONSTRUCTION ═════════════════════════ */
    public ArcanaApp() {
        super("\u2726 Arcana Academy \u2726 \u2014 Tarot Learning App");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Size the window relative to the screen so a plain launch looks
        // right on both laptops and large monitors, clamped to sane bounds.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(820, Math.max(600, (int) (screen.width  * 0.42)));
        int h = Math.min(920, Math.max(720, (int) (screen.height * 0.85)));
        setSize(w, h);
        setMinimumSize(new Dimension(600, 700));
        setLocationRelativeTo(null); // centre on screen

        // Root layout: header (N) / tabs (C) / toast + nav (S).
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);

        tabHolder.setBackground(BG);
        tabHolder.add(buildHomeTab(),   "homeTab");
        tabHolder.add(buildLearnTab(),  "learnTab");
        tabHolder.add(buildMemoryTab(), "memoryTab");
        root.add(tabHolder, BorderLayout.CENTER);

        root.add(buildBottomBar(), BorderLayout.SOUTH);

        // Alt+1/2/3 switch tabs from anywhere (keyboard operability).
        bindTabShortcut(root, KeyEvent.VK_1, "homeTab");
        bindTabShortcut(root, KeyEvent.VK_2, "learnTab");
        bindTabShortcut(root, KeyEvent.VK_3, "memoryTab");

        updateStatus();
        switchTab("homeTab");
    }

    /** Registers Alt+&lt;key&gt; as a global shortcut that opens a tab. */
    private void bindTabShortcut(JComponent root, int key, String tabId) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(key, KeyEvent.ALT_DOWN_MASK), "tab-" + tabId);
        root.getActionMap().put("tab-" + tabId, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { switchTab(tabId); }
        });
    }

    /* ══════════════════════ HEADER & FOOTER ═══════════════════════ */

    /** App title and one-line description, stacked at the top. */
    private JComponent buildHeader() {
        JPanel h = new JPanel(new GridBagLayout());
        h.setBackground(BG);
        h.setBorder(new EmptyBorder(SP_3, SP_3, SP_2, SP_3));
        GridBagConstraints gc = gbc(0);

        JLabel title = new JLabel("\u2726 Arcana Academy \u2726", SwingConstants.CENTER);
        // set font size by deriving from base TITLE_FONT
        title.setFont(TITLE_FONT.deriveFont(28f));
        title.setForeground(GOLD);
        gc.gridy = 0; h.add(title, gc);

        JLabel sub = new JLabel("A tarot interpretation learning tool", SwingConstants.CENTER);
        sub.setFont(BODY_FONT.deriveFont(14f));
        sub.setForeground(TEXT);
        gc.gridy = 1; gc.insets = ins(2, 0, 0, 0); h.add(sub, gc);
        return h;
    }

    /** Bottom bar: the toast message line on top, nav buttons below. */
    private JComponent buildBottomBar() {
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG);

        // Toast: a single reusable label that showToast() writes into and
        // clears after a few seconds. Holds " " so it always reserves height.
        toast = new JLabel(" ", SwingConstants.CENTER);
        toast.setFont(BODY_FONT.deriveFont(18f));
        toast.setForeground(GOLD);
        toast.setBorder(new EmptyBorder(SP_1, SP_2, SP_1, SP_2)); 
        south.add(toast, BorderLayout.NORTH);

        JPanel nav = new JPanel(new GridLayout(1, 3, SP_2, 0));
        nav.setBackground(BG);
        nav.setBorder(new EmptyBorder(SP_1, SP_3, SP_2, SP_3));
        nav.add(makeNavButton("\u2302  Home",   "homeTab",   "Choose cards and ask a question (Alt+1)"));
        nav.add(makeNavButton("\u2727  Learn",  "learnTab",  "Study each card's meaning (Alt+2)"));
        nav.add(makeNavButton("\u25D4  Memory", "memoryTab", "Revisit your past readings (Alt+3)"));
        south.add(nav, BorderLayout.CENTER);
        return south;
    }

    /** Creates one rounded nav button wired to switch to the given tab. */
    private RoundedButton makeNavButton(String label, String tabId, String tip) {
        RoundedButton b = RoundedButton.secondary(label);
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleDescription(tip);
        b.addActionListener(e -> switchTab(tabId));
        navButtons.put(tabId, b);
        return b;
    }

    /**
     * Shows a tab and restyles the nav buttons so the active one stands
     * out (gold text + gold outline). Also refreshes the Memory list on
     * entry so it always reflects what is currently on disk.
     */
    private void switchTab(String tabId) {
        tabLayout.show(tabHolder, tabId);
        navButtons.forEach((id, b) -> b.setActive(id.equals(tabId)));
        if (tabId.equals("memoryTab")) renderMemory();
    }

    /* ══════════════════════════ HOME TAB ══════════════════════════ */

    /**
     * Builds the Home tab. Layout:
     *   NORTH  — fixed controls: subtitle, search field, a fixed-height
     *            scrollable deck grid, the status pill, the question box,
     *            and the primary "Reveal Interpretation" button.
     * Keeping the controls in NORTH means they never move while the user
     * scrolls the deck.
     */
    private JComponent buildHomeTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setBackground(BG);

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBackground(BG);
        controls.setBorder(new EmptyBorder(SP_1, SP_3, SP_2, SP_3));
        GridBagConstraints gc = gbc(0);
        int row = 0;

        // Instruction line.
        JLabel sub = new JLabel(
            "Search or scroll the deck \u00b7 choose three cards \u00b7 ask your question",
            SwingConstants.CENTER);
        sub.setFont(BOLD_FONT.deriveFont(16f));
        sub.setForeground(MUTED);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_2, 0);
        controls.add(sub, gc);

        // Search field filters the deck grid live as the user types.
        homeSearch = makeSearchField(
                "Search cards \u2014 try 'moon', 'queen', 'swords'\u2026",
                "Search the deck by card name");
        gc.gridy = row++; gc.insets = ins(2, 0, SP_2, 0);
        controls.add(homeSearch, gc);

        // Deck grid inside a fixed-height scrollable viewport. Home tiles
        // are in "select mode": clicking toggles selection.
        homeGrid = new JPanel(new GridLayout(0, GRID_COLS, SP_2, SP_2));
        homeGrid.setBackground(BG);
        for (int i = 0; i < Deck.CARDS.size(); i++) {
            homeTiles.add(new CardTile(Deck.CARDS.get(i), i, true));
        }
        refillGrid(homeGrid, homeTiles, "");

        JScrollPane deckScroll = buildDeckScroll(homeGrid);
        // Fix the viewport height so the controls below never jump around.
        deckScroll.setPreferredSize(new Dimension(10, CARD_VIEWPORT));
        deckScroll.setMinimumSize(new Dimension(10, CARD_VIEWPORT));
        deckScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_VIEWPORT));
        gc.gridy = row++; gc.insets = ins(2, 0, SP_2, 0);
        controls.add(deckScroll, gc);

        // Live filtering: re-fill the grid whenever the search text changes.
        homeSearch.getDocument().addDocumentListener(new SimpleDocListener(
                () -> refillGrid(homeGrid, homeTiles, homeSearch.getText())));

        // Status pill: tells the user how many cards are chosen.
        statusPill = new JLabel("Cards chosen: 0 of 3", SwingConstants.CENTER);
        statusPill.setFont(BOLD_FONT.deriveFont(16f));
        statusPill.setForeground(LAVENDER);
        gc.gridy = row++; gc.insets = ins(SP_2, 2, 2, 0);
        controls.add(statusPill, gc);

        // Question input. Ctrl+Enter submits; Tab moves focus onward
        // instead of inserting a tab character (keyboard-friendly).
        questionInput = new RoundedTextArea("Type your question for the cards\u2026", 3, 30);
        questionInput.setToolTipText("Ask the cards anything. Press Ctrl+Enter to reveal.");
        questionInput.getAccessibleContext().setAccessibleName("Your question for the cards");
        questionInput.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "reveal");
        questionInput.getActionMap().put("reveal", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (revealBtn.isEnabled()) revealReading();
                else showToast("Choose 3 cards and type a question first.");
            }
        });
        // Restore default Tab/Shift+Tab focus traversal inside a text area.
        questionInput.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        questionInput.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);

        JScrollPane qScroll = themedScroll(new JScrollPane(questionInput));
        qScroll.setBorder(new RoundedLineBorder(PANEL_HI, 1, RADIUS_MD));
        qScroll.setPreferredSize(new Dimension(10, 80));
        qScroll.setMinimumSize(new Dimension(10, 80));
        qScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        qScroll.getViewport().setBackground(PANEL);
        gc.gridy = row++; gc.insets = ins(SP_1, 0, 2, 0);
        controls.add(qScroll, gc);

        // Primary action. Disabled until 3 cards are chosen (see updateStatus).
        revealBtn = RoundedButton.primary("Reveal Interpretation");
        revealBtn.setEnabled(false);
        revealBtn.setToolTipText("Choose 3 cards and type a question to enable (Ctrl+Enter)");
        revealBtn.setFont(BOLD_FONT.deriveFont(16f));
        revealBtn.getAccessibleContext().setAccessibleDescription(
                "Reveals the interpretation once three cards are chosen and a question is typed");
        revealBtn.addActionListener(e -> revealReading());
        gc.gridy = row++; gc.insets = ins(SP_1, 0, SP_2, 0);
        controls.add(revealBtn, gc);

        tab.add(controls, BorderLayout.NORTH);
        return tab;
    }

    /* ═══════════════════════ CARD SELECTION ═══════════════════════ */

    /**
     * Toggles a card in/out of the current selection.
     * Rules: max 3 cards; re-clicking a selected card deselects it;
     * trying to pick a 4th card only shows a toast.
     */
    private void toggleSelect(int index, CardTile tile) {
        if (selected.containsKey(index)) {
            selected.remove(index);
            tile.setSelectedState(false, false);
        } else if (selected.size() < MAX_SELECTION) {
            selected.put(index, new SelectedCard(Deck.CARDS.get(index)));
            tile.setSelectedState(true, false);
            if (selected.size() == MAX_SELECTION) {
                showToast("Three cards chosen \u2014 now type your question below.");
            }
        } else {
            showToast("You already hold 3 cards. Deselect one to choose another.");
            return; // nothing changed — skip the status refresh
        }
        updateStatus();
    }

    /** Flips a selected card between upright and reversed. */

    /* this is the part that i did, adding the feature that the user can choose to reverse a card (this 
    changes the meaning of the card and how it applies to the question) */

    private void toggleReversed(int index, CardTile tile) {
        SelectedCard entry = selected.get(index); // get the selected card
        if (entry == null) return; // card not actually selected
        entry.reversed = !entry.reversed; // toggle the reversed flag
        tile.setSelectedState(true, entry.reversed); // update the tile's visual state
    }

    /**
     * Central UI-state refresh: keeps the status pill and reveal button in
     * sync with the selection count and the loading flag. State is shown
     * with BOTH colour and text, so it is never conveyed by colour alone.
     */
    private void updateStatus() {
        int n = selected.size();
        boolean ready = n == MAX_SELECTION && !loading;
        statusPill.setText(ready
                ? "Cards chosen: 3 of 3 \u2014 ready"
                : "Cards chosen: " + n + " of " + MAX_SELECTION);
        statusPill.setForeground(ready ? GOLD : LAVENDER);
        revealBtn.setEnabled(ready);
        revealBtn.setToolTipText(ready
                ? "Reveal your three-card reading (Ctrl+Enter)"
                : "Choose 3 cards and type a question to enable");
    }

    /* ══════════════════════ REVEAL A READING ══════════════════════ */

    /**
     * Validates input, then generates the reading on a background thread
     * (SwingWorker) so the UI never freezes while the Interpreter works.
     * On success the reading is saved to Storage and shown in a dialog.
     */
    private void revealReading() {
        String q = questionInput.getText().trim();

        // Input validation with friendly, actionable messages.
        if (selected.size() != MAX_SELECTION) {
            showToast("Please choose exactly 3 cards from the deck above.");
            return;
        }
        if (q.isEmpty()) {
            showToast("Type your question first \u2014 the cards need something to answer.");
            questionInput.requestFocusInWindow();
            return;
        }
        if (loading) return; // ignore double-clicks while working

        // Enter the "busy" state: disable the button and tell the user.
        loading = true;
        updateStatus();
        revealBtn.setText("Consulting the cards\u2026");
        showToast("Consulting the cards\u2026");

        // Snapshot the selection so later clicks can't mutate this reading.
        List<SelectedCard> cards = new ArrayList<>(selected.values());

        new SwingWorker<Interpreter.Result, Void>() {
            @Override protected Interpreter.Result doInBackground() {
                // Runs OFF the Swing thread — may hit the network.
                return Interpreter.interpret(q, cards);
            }
            @Override protected void done() {
                // Back ON the Swing thread — safe to touch components.
                try {
                    Interpreter.Result result = get();
                    String sourceText = result.fromAI
                            ? "Interpretation generated with AI assistance"
                            : "Interpretation generated by the built-in learning guide";
                    Storage.saveReading(q, cards, result.sections);
                    showReadingDialog(cards, result.sections, sourceText);
                } catch (Exception err) {
                    showToast("The reading could not be completed. Check your connection and try again.");
                } finally {
                    // Always leave the busy state, even after an error.
                    loading = false;
                    revealBtn.setText("Reveal Interpretation");
                    updateStatus();
                }
            }
        }.execute();
    }

    /**
     * Modal dialog presenting the finished reading: the three cards, one
     * section per position (Past / Present / Future) plus a combined
     * "Together" section, and a note about where the text came from.
     */
    private void showReadingDialog(List<SelectedCard> cards, String[] sections, String sourceText) {
        String[] labels = { "PAST", "PRESENT", "FUTURE", "TOGETHER" };

        JPanel panel = newDialogPanel();
        GridBagConstraints gc = gbc(0);
        int row = 0;

        // Header line listing the three drawn cards.
        String cardLine = cards.stream()
                .map(c -> c.card.shortName() + (c.reversed ? " (rev.)" : ""))
                .reduce((a, b) -> a + "  \u00b7  " + b).orElse("");
        JLabel titleLbl = new JLabel(cardLine, SwingConstants.CENTER);
        titleLbl.setFont(SMALL_FONT.deriveFont(18f));
        titleLbl.setForeground(GOLD);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_3 - 2, 0);
        panel.add(titleLbl, gc);

        // One heading + body per section; a thin separator between the
        // first three (the "Together" section closes the list).
        for (int i = 0; i < sections.length; i++) {
            String cardPart = i < 3
                    ? "  \u00b7  " + cards.get(i).card.shortName()
                      + (cards.get(i).reversed ? " (reversed)" : "") : "";
            JLabel head = new JLabel(labels[i] + cardPart, SwingConstants.LEFT);
            head.setFont(HEADER_FONT);
            head.setForeground(GOLD);
            gc.gridy = row++; gc.insets = ins(0, 0, SP_1, 0);
            panel.add(head, gc);

            JTextArea body = makeWrappedText(sections[i], PANEL);
            gc.gridy = row++; gc.insets = ins(0, 0, SP_2 + 2, 0);
            panel.add(body, gc);

            if (i < 3) {
                gc.gridy = row++; gc.insets = ins(0, 0, SP_2 + 2, 0);
                panel.add(separator(), gc);
            }
        }

        // Provenance note (AI vs built-in guide).
        JLabel src = new JLabel(sourceText, SwingConstants.CENTER);
        src.setFont(SMALL_FONT.deriveFont(14f));
        src.setForeground(MUTED);
        gc.gridy = row++; gc.insets = ins(SP_1, 0, SP_3 - 4, 0);
        panel.add(src, gc);

        showThemedDialog("Your Reading", panel, 600, 640, "Back  (Esc)");
    }

    /* ══════════════════════════ LEARN TAB ═════════════════════════ */

    /**
     * Builds the Learn tab: title + subtitle + search on top, and the
     * full deck grid filling the remaining space. Learn tiles are NOT in
     * select mode — clicking one opens its lesson dialog instead.
     */
    private JComponent buildLearnTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setBackground(BG);

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBackground(BG);
        controls.setBorder(new EmptyBorder(SP_1, SP_3, SP_2, SP_3));
        GridBagConstraints gc = gbc(0);
        int row = 0;

        JLabel title = new JLabel("Learn the Cards", SwingConstants.CENTER);
        title.setFont(HEADER_FONT.deriveFont(24f));
        title.setForeground(GOLD);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_1, 0);
        controls.add(title, gc);

        JLabel sub = new JLabel(
            "Click any card to read what it means and how to use it in a reading.",
            SwingConstants.CENTER);
        sub.setFont(BODY_FONT.deriveFont(14f));
        sub.setForeground(TEXT);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_2, 0);
        controls.add(sub, gc);

        learnSearch = makeSearchField("Search cards\u2026", "Search the deck by card name");
        gc.gridy = row++; gc.insets = ins(0, 0, SP_2, 0);
        controls.add(learnSearch, gc);

        tab.add(controls, BorderLayout.NORTH);

        // Deck grid in "study mode" (selectMode = false).
        learnGrid = new JPanel(new GridLayout(0, GRID_COLS, SP_2, SP_2));
        learnGrid.setBackground(BG);
        for (int i = 0; i < Deck.CARDS.size(); i++) {
            learnTiles.add(new CardTile(Deck.CARDS.get(i), i, false));
        }
        refillGrid(learnGrid, learnTiles, "");

        JPanel learnWrapper = new JPanel(new BorderLayout());
        learnWrapper.setBackground(BG);
        learnWrapper.setBorder(new EmptyBorder(0, SP_3, SP_3, SP_3));
        learnWrapper.add(buildDeckScroll(learnGrid), BorderLayout.CENTER);

        learnSearch.getDocument().addDocumentListener(new SimpleDocListener(
                () -> refillGrid(learnGrid, learnTiles, learnSearch.getText())));

        tab.add(learnWrapper, BorderLayout.CENTER);
        return tab;
    }

    /** Lesson dialog: symbol, name, upright meaning, reversed meaning. */
    private void openModal(Card card) {
        JPanel panel = newDialogPanel();
        GridBagConstraints gc = gbc(0);
        int row = 0;

        JLabel sym = new JLabel(card.symbol, SwingConstants.CENTER);
        sym.setFont(new Font(Font.SERIF, Font.PLAIN, 34));
        sym.setForeground(GOLD);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_1, 0); panel.add(sym, gc);

        JLabel name = new JLabel(card.name, SwingConstants.CENTER);
        name.setFont(HEADER_FONT.deriveFont(24f));
        name.setForeground(TEXT);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_2 + 2, 0); panel.add(name, gc);

        gc.gridy = row++; gc.insets = ins(0, 0, SP_3 - 4, 0);
        panel.add(makeWrappedText(card.lesson, PANEL), gc);

        JLabel revLabel = new JLabel("\u21B6 Reversed", SwingConstants.CENTER);
        revLabel.setFont(HEADER_FONT.deriveFont(24f));
        revLabel.setForeground(GOLD);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_1, 0); panel.add(revLabel, gc);

        gc.gridy = row++; gc.insets = ins(0, 0, SP_3 - 4, 0);
        panel.add(makeWrappedText(card.reversedLesson, PANEL), gc);

        showThemedDialog(card.name, panel, 560, 560, "Close  (Esc)");
    }

    /* ═════════════════════════ MEMORY TAB ═════════════════════════ */

    /** Builds the Memory tab shell. The list itself is (re)filled by
     *  renderMemory() every time the tab is opened. */
    private JComponent buildMemoryTab() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(SP_1, SP_3, SP_3, SP_3));

        JLabel title = new JLabel("\u25D4 Memory", SwingConstants.CENTER);
        title.setFont(HEADER_FONT);
        title.setForeground(GOLD);
        title.setBorder(new EmptyBorder(0, 0, SP_2, 0));
        content.add(title, BorderLayout.NORTH);

        memoryList = new JPanel();
        memoryList.setLayout(new BoxLayout(memoryList, BoxLayout.Y_AXIS));
        memoryList.setBackground(BG);

        // Wrap in a BorderLayout holder so the list top-aligns instead of
        // stretching to fill the viewport.
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(BG);
        holder.add(memoryList, BorderLayout.NORTH);

        JScrollPane scroll = themedScroll(new JScrollPane(holder,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        content.add(scroll, BorderLayout.CENTER);
        return content;
    }

    /**
     * Rebuilds the Memory list from Storage. Each saved reading becomes a
     * rounded, clickable item that expands/collapses its full text.
     * Called every time the Memory tab is shown and after clearing.
     */
    private void renderMemory() {
        List<Storage.Entry> entries = Storage.loadAll();
        memoryList.removeAll();

        if (entries.isEmpty()) {
            // Empty state with a hint pointing the user to the Home tab.
            JLabel empty = new JLabel(
                    "<html><div style='text-align:center;'>No readings yet.<br>"
                    + "Go to Home, choose three cards, and ask your first question.</div></html>",
                    SwingConstants.CENTER);
            empty.setFont(BODY_FONT.deriveFont(18f));
            empty.setForeground(MUTED);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setBorder(new EmptyBorder(SP_4 + 6, 0, 0, 0));
            memoryList.add(empty);
        } else {
            // Destructive action at the top, styled in the danger colour.
            RoundedButton clearBtn = RoundedButton.secondary("Clear all memory\u2026");
            clearBtn.setTextColor(DANGER);
            clearBtn.setToolTipText("Permanently deletes every saved reading");
            clearBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            clearBtn.addActionListener(e -> confirmClearMemory());
            memoryList.add(clearBtn);
            memoryList.add(Box.createVerticalStrut(SP_2 + 4));

            SimpleDateFormat fmt = new SimpleDateFormat("MMM d, h:mm a");
            String[] labels = { "PAST", "PRESENT", "FUTURE", "TOGETHER" };

            for (Storage.Entry entry : entries) {
                // ── Item header: date, question, cards, expand hint ──
                RoundedPanel item = new RoundedPanel(RADIUS_LG, PANEL_HI);
                item.setLayout(new BoxLayout(item, BoxLayout.Y_AXIS));
                item.setBackground(PANEL);
                item.setBorder(new EmptyBorder(SP_2 + 2, SP_2 + 4, SP_2 + 2, SP_2 + 4));
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                item.setToolTipText("Click to expand or collapse this reading");

                JLabel date = new JLabel(fmt.format(new Date(entry.timestamp)));
                date.setFont(SMALL_FONT.deriveFont(14f));
                date.setForeground(MUTED);
                item.add(date);

                JLabel question = new JLabel("<html>" + escapeHtml(entry.question) + "</html>");
                question.setFont(HEADER_FONT.deriveFont(18f));
                question.setForeground(TEXT);
                item.add(question);

                JLabel cardsLbl = new JLabel(String.join("  \u2022  ", entry.cardNames));
                cardsLbl.setFont(SMALL_FONT.deriveFont(14f));
                cardsLbl.setForeground(GOLD);
                item.add(cardsLbl);

                JLabel expandHint = new JLabel("\u25BE Show reading");
                expandHint.setFont(SMALL_FONT.deriveFont(16f));
                expandHint.setForeground(LAVENDER);
                expandHint.setBorder(new EmptyBorder(SP_1, 0, 0, 0));
                item.add(expandHint);

                // ── Collapsible body: one heading + text per section ──
                JPanel body = new JPanel();
                body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
                body.setBackground(PANEL);
                body.setVisible(false); // starts collapsed
                body.setBorder(new EmptyBorder(SP_2, 0, 0, 0));
                for (int i = 0; i < entry.sections.size(); i++) {
                    String cardPart = (i < 3 && i < entry.cardNames.size())
                            ? "  \u00b7 " + entry.cardNames.get(i) : "";
                    JLabel head = new JLabel(labels[i] + cardPart);
                    head.setFont(new Font(Font.SERIF, Font.BOLD, 13));
                    head.setForeground(GOLD);
                    head.setAlignmentX(Component.LEFT_ALIGNMENT);
                    body.add(head);
                    JTextArea bt = makeWrappedText(entry.sections.get(i), PANEL);
                    bt.setAlignmentX(Component.LEFT_ALIGNMENT);
                    body.add(bt);
                    if (i < 3) {
                        JPanel sep = separator();
                        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
                        body.add(Box.createVerticalStrut(SP_2));
                        body.add(sep);
                        body.add(Box.createVerticalStrut(SP_2));
                    }
                }
                item.add(body);

                // Click toggles the body; hover lifts the whole item.
                // syncBg keeps nested panels/text areas matching on hover.
                MouseAdapter interact = new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        boolean open = !body.isVisible();
                        body.setVisible(open);
                        expandHint.setText(open ? "\u25B4 Hide reading" : "\u25BE Show reading");
                        memoryList.revalidate();
                        memoryList.repaint();
                    }
                    @Override public void mouseEntered(MouseEvent e) { item.setBackground(PANEL_HOV); syncBg(item, PANEL_HOV); }
                    @Override public void mouseExited(MouseEvent e)  { item.setBackground(PANEL);     syncBg(item, PANEL); }
                };
                item.addMouseListener(interact);

                memoryList.add(item);
                memoryList.add(Box.createVerticalStrut(SP_2 + 2));
            }
        }
        memoryList.revalidate();
        memoryList.repaint();
    }

    /** Recursively recolours child panels/text areas during hover so the
     *  whole memory item lifts together instead of leaving dark patches. */
    private static void syncBg(Container c, Color color) {
        for (Component child : c.getComponents()) {
            if (child instanceof JPanel || child instanceof JTextArea) {
                child.setBackground(color);
                if (child instanceof Container) syncBg((Container) child, color);
            }
        }
    }

    /** Destructive-action guard: confirmation dialog before wiping data. */
    private void confirmClearMemory() {
        Object[] options = { "Delete everything", "Keep my readings" };
        int choice = JOptionPane.showOptionDialog(this,
                "This permanently deletes every saved reading.\nThere is no undo.",
                "Clear all memory?",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[1]); // default = the safe option
        if (choice == JOptionPane.YES_OPTION) {
            Storage.clearAll();
            renderMemory();
            showToast("All readings cleared.");
        }
    }

    /* ═══════════════════════════ CARD TILE ════════════════════════ */

    /**
     * One card in the deck grid — a rounded tile showing the card's
     * symbol and name. Behaviour depends on the mode it was built with:
     *
     *   selectMode = true  (Home)  → click/Enter toggles selection, and a
     *                                small "Set Reversed" button appears
     *                                while selected (R also flips it).
     *   selectMode = false (Learn) → click/Enter opens the lesson dialog.
     *
     * The tile paints its own rounded background and outline, so its
     * visual state (normal / hover / focused / selected / reversed) is
     * all handled in paintComponent + a couple of flags.
     */
    private class CardTile extends JPanel {
        final Card    card;
        final JButton revBtn;             // "Set Reversed" toggle (Home only)
        private final int     tileIndex;  // index into Deck.CARDS
        private final boolean selectMode;
        private boolean isSelected = false;
        private boolean isReversed = false;
        private boolean hover      = false;

        CardTile(Card card, int index, boolean selectMode) {
            this.card       = card;
            this.tileIndex  = index;
            this.selectMode = selectMode;

            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false); // we paint our own rounded background
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(selectMode
                    ? card.name + " \u2014 click (or press Enter) to select"
                    : card.name + " \u2014 click (or press Enter) to study");

            // Keyboard operability: tiles join the Tab order and respond
            // to Enter/Space exactly like a click (WCAG 2.1.1).
            setFocusable(true);
            getAccessibleContext().setAccessibleName(card.name);
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
                        activate();
                    } else if (e.getKeyCode() == KeyEvent.VK_R && isSelected) {
                        toggleReversed(tileIndex, CardTile.this);
                    }
                }
            });
            // Focus changes only affect the painted outline → repaint.
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e)   { repaint(); }
            });

            // ── Contents: symbol, name, optional reverse button ──
            JLabel sym = new JLabel(card.symbol, SwingConstants.CENTER);
            sym.setFont(SYMBOL_FONT.deriveFont(18f));
            sym.setForeground(GOLD);
            sym.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(Box.createVerticalStrut(SP_2 - 2));
            add(sym);

            JLabel nm = new JLabel(
                    "<html><div style='text-align:center;'>" + escapeHtml(card.name) + "</div></html>",
                    SwingConstants.CENTER);
            nm.setFont(SMALL_FONT.deriveFont(14f));
            nm.setForeground(TEXT);
            nm.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(nm);

            revBtn = new JButton("\u27F3 Set Reversed");
            revBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            revBtn.setFocusPainted(false);
            revBtn.setBackground(PANEL_HI);
            revBtn.setForeground(LAVENDER);
            revBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            revBtn.setVisible(false); // only shown while selected
            revBtn.setToolTipText("Flip this card upside-down for its reversed meaning (or press R)");
            revBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            revBtn.addActionListener(e -> toggleReversed(tileIndex, this));
            add(Box.createVerticalStrut(SP_1));
            add(revBtn);
            add(Box.createVerticalStrut(SP_2 - 2));

            // One shared mouse handler for the tile AND its labels, so a
            // click anywhere on the tile counts (labels would otherwise
            // swallow the event).
            MouseAdapter clicker = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { activate(); }
                @Override public void mouseEntered(MouseEvent e) { hover = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            };
            addMouseListener(clicker);
            sym.addMouseListener(clicker);
            nm.addMouseListener(clicker);
        }

        /** The single "do the tile's thing" action for click/Enter/Space. */
        private void activate() {
            requestFocusInWindow();
            if (selectMode) toggleSelect(tileIndex, this);
            else            openModal(card);
        }

        /** Called by the selection logic to move the tile between states. */
        void setSelectedState(boolean sel, boolean rev) {
            this.isSelected = sel;
            this.isReversed = rev;
            revBtn.setVisible(sel);
            revBtn.setText(rev ? "\u21B6 Reversed" : "\u27F3 Set Reversed");
            getAccessibleContext().setAccessibleDescription(
                    sel ? "Selected" + (rev ? ", reversed" : "") : "Not selected");
            revalidate();
            repaint();
        }

        /**
         * Draws the rounded tile. Fill and outline both depend on state:
         *   fill    — PANEL normally, PANEL_HI when selected, PANEL_HOV on hover
         *   outline — GOLD (selected upright), LAVENDER (selected reversed
         *             or keyboard focus), otherwise a subtle PANEL_HI line
         */
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color fill = isSelected ? PANEL_HI : (hover ? PANEL_HOV : PANEL);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS_LG, RADIUS_LG);

            boolean focused = isFocusOwner();
            Color outline = isSelected ? (isReversed ? LAVENDER : GOLD)
                                       : (focused ? LAVENDER : PANEL_HI);
            g2.setStroke(new BasicStroke((isSelected || focused) ? 2f : 1f));
            g2.setColor(outline);
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, RADIUS_LG, RADIUS_LG);

            g2.dispose();
            super.paintComponent(g); // children paint on top
        }
    }

    /* ══════════════════════ GRID SEARCH & TOAST ═══════════════════ */

    /**
     * Refills a deck grid with only the tiles whose card name contains the
     * (case-insensitive) query. Shows a friendly "no matches" hint when
     * the filter comes up empty. Used by both Home and Learn tabs.
     */
    private void refillGrid(JPanel grid, List<CardTile> tiles, String query) {
        String q = query.trim().toLowerCase();
        grid.removeAll();
        int visible = 0;
        for (CardTile t : tiles) {
            if (t.card.name.toLowerCase().contains(q)) { grid.add(t); visible++; }
        }
        if (visible == 0) {
            JLabel none = new JLabel(
                    "<html><div style='text-align:center;'>No cards match \u201c"
                    + escapeHtml(query.trim())
                    + "\u201d.<br>Try a shorter word, like 'cup' or 'star'.</div></html>",
                    SwingConstants.CENTER);
            none.setFont(BODY_FONT.deriveFont(18f));
            none.setForeground(MUTED);
            none.setBorder(new EmptyBorder(SP_3, 0, SP_3, 0));
            grid.add(none);
        }
        grid.revalidate();
        grid.repaint();
    }

    /** Shows a temporary status message in the bottom bar for ~3 seconds.
     *  Restarting the timer means rapid messages don't cut each other off
     *  early. */
    private void showToast(String msg) {
        toast.setText(msg);
        if (toastTimer != null) toastTimer.stop();
        toastTimer = new Timer(3200, e -> toast.setText(" "));
        toastTimer.setRepeats(false);
        toastTimer.start();
    }

    /* ═════════════════════ SHARED UI BUILDERS ═════════════════════ */
    // Everything below is reusable plumbing: small factories/helpers used
    // by all three tabs so styling stays consistent and isn't repeated.

    /** Standard single-column GridBagConstraints: full width, no stretch. */
    private static GridBagConstraints gbc(int gridy) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx     = 0;
        gc.gridy     = gridy;
        gc.gridwidth = GridBagConstraints.REMAINDER;
        gc.fill      = GridBagConstraints.HORIZONTAL;
        gc.weightx   = 1.0;
        gc.weighty   = 0.0;
        gc.insets    = new Insets(0, 0, 0, 0);
        return gc;
    }

    /** Shorthand for new Insets(top, left, bottom, right). */
    private static Insets ins(int t, int l, int b, int r) {
        return new Insets(t, l, b, r);
    }

    /** A 1-px horizontal rule used between reading sections. */
    private static JPanel separator() {
        JPanel sep = new JPanel();
        sep.setBackground(PANEL_HI);
        sep.setPreferredSize(new Dimension(10, 1));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    /** Applies the slim themed scrollbar and shared scroll settings. */
    private static JScrollPane themedScroll(JScrollPane sp) {
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getVerticalScrollBar().setUI(new ArcanaScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(9, 0));
        sp.setBackground(BG);
        return sp;
    }

    /**
     * Wraps a deck grid in a top-aligned, vertically scrollable, rounded
     * viewport. Shared by the Home and Learn tabs so both decks look and
     * scroll identically.
     */
    private static JScrollPane buildDeckScroll(JPanel grid) {
        // BorderLayout.NORTH keeps the grid top-aligned instead of letting
        // it stretch to fill the viewport height.
        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setBackground(BG);
        gridHolder.setBorder(new EmptyBorder(SP_1, SP_1, SP_1, SP_1));
        gridHolder.add(grid, BorderLayout.NORTH);

        JScrollPane scroll = themedScroll(new JScrollPane(gridHolder,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
        scroll.setBorder(new RoundedLineBorder(PANEL_HI, 1, RADIUS_LG));
        scroll.getViewport().setBackground(BG);
        return scroll;
    }

    /** Creates a rounded, themed search field with placeholder text and
     *  an accessible name for assistive technologies. */
    private RoundedTextField makeSearchField(String placeholder, String accessibleName) {
        RoundedTextField f = new RoundedTextField(placeholder);
        f.getAccessibleContext().setAccessibleName(accessibleName);
        return f;
    }

    /** Read-only, word-wrapping text block used for all reading bodies. */
    private JTextArea makeWrappedText(String text, Color bg) {
        JTextArea t = new JTextArea(text);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setFocusable(false); // stays out of the Tab order — it's static text
        t.setFont(BODY_FONT.deriveFont(16f));
        t.setBackground(bg);
        t.setForeground(TEXT);
        t.setBorder(new EmptyBorder(2, 0, 2, 0));
        return t;
    }

    /** Basic HTML escaping for card names / user text shown via <html> labels. */
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Shared dialog scaffold used by both the reading dialog and the
     * lesson dialog: wraps the given content panel in a themed scroll
     * pane, appends a close button, and wires Esc / default-button to
     * close. Removes ~30 lines of duplication per dialog.
     */
    private void showThemedDialog(String title, JPanel panel, int maxW, int maxH, String closeLabel) {
        JDialog dialog = new JDialog(this, title, true); // modal
        dialog.setSize(Math.min(maxW, getWidth() - 60), Math.min(maxH, getHeight() - 80));
        dialog.setLocationRelativeTo(this);

        // Close button appended as the panel's final row.
        RoundedButton close = RoundedButton.secondary(closeLabel);
        close.setOutline(GOLD);
        close.addActionListener(e -> dialog.dispose());
        GridBagConstraints gc = gbc(GridBagConstraints.RELATIVE); // next free row
        panel.add(close, gc);

        // GridBagLayout vertically CENTRES its content when the dialog is
        // taller than the content, which left a big empty gap above the
        // text. Anchoring the panel to NORTH inside a wrapper makes the
        // content start at the top of the dialog instead.
        JPanel topAnchor = new JPanel(new BorderLayout());
        topAnchor.setBackground(PANEL);
        topAnchor.add(panel, BorderLayout.NORTH);

        JScrollPane scroll = themedScroll(new JScrollPane(topAnchor,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(PANEL);
        dialog.setContentPane(scroll);

        // Esc closes; Enter triggers the close button by default.
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.getRootPane().setDefaultButton(close);
        dialog.setVisible(true);
    }

    /** Fresh padded content panel for dialogs (GridBagLayout, PANEL bg).
     *  Generous side margins (~40 px) keep the text comfortably away from
     *  the dialog edges and give lines a more readable measure. */
    private static JPanel newDialogPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL);
        panel.setBorder(new EmptyBorder(SP_4, SP_4 + SP_3, SP_4, SP_4 + SP_3));
        return panel;
    }

    /* ═════════════════ CUSTOM ROUNDED COMPONENTS ══════════════════ */
    // These small classes give the app its modern rounded look. Swing has
    // no built-in corner radius, so each one paints an antialiased round
    // rectangle itself and stays non-opaque so the parent shows through
    // at the corners.

    /** An antialiased rounded outline border (replacement for LineBorder). */
    private static class RoundedLineBorder extends AbstractBorder {
        private final Color color;
        private final int   thickness;
        private final int   radius;

        RoundedLineBorder(Color color, int thickness, int radius) {
            this.color = color; this.thickness = thickness; this.radius = radius;
        }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            // Inset by half the stroke so the line isn't clipped at the edges.
            g2.drawRoundRect(x + thickness / 2, y + thickness / 2,
                    w - thickness, h - thickness, radius, radius);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) {
            int t = thickness + 1;
            return new Insets(t, t, t, t);
        }
    }

    /** A JPanel that paints a rounded background + subtle outline. Child
     *  components lay out normally on top. Used for memory list items. */
    private static class RoundedPanel extends JPanel {
        private final int   radius;
        private final Color outline;

        RoundedPanel(int radius, Color outline) {
            this.radius = radius; this.outline = outline;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.setColor(outline);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * A pill-style rounded button that handles its own hover / focus /
     * disabled painting, replacing the old styleButton/styleGoldButton
     * mouse- and focus-listener boilerplate.
     *
     * Two factory styles:
     *   primary()   — solid gold fill, dark text (the main call to action)
     *   secondary() — panel fill, lavender text, subtle outline
     */
    private static class RoundedButton extends JButton {
        private Color fill, hoverFill, disabledFill, textColor;
        private Color outline;       // nullable — no outline when null
        private boolean active;      // "current tab" state for nav buttons

        static RoundedButton primary(String label) {
            RoundedButton b = new RoundedButton(label);
            b.fill         = GOLD;
            b.hoverFill    = GOLD.brighter();
            b.disabledFill = GOLD_DIM;
            b.textColor    = new Color(0x2A2149);
            b.setFont(HEADER_FONT.deriveFont(24f));
            b.setBorder(new EmptyBorder(SP_2, SP_4, SP_2, SP_4));
            return b;
        }

        static RoundedButton secondary(String label) {
            RoundedButton b = new RoundedButton(label);
            b.fill         = PANEL;
            b.hoverFill    = PANEL_HOV;
            b.disabledFill = PANEL;
            b.textColor    = LAVENDER;
            b.outline      = PANEL_HI;
            b.setFont(BODY_FONT.deriveFont(16f));
            b.setBorder(new EmptyBorder(SP_2, SP_3 - 2, SP_2, SP_3 - 2));
            return b;
        }

        private RoundedButton(String label) {
            super(label);
            setContentAreaFilled(false); // we paint the fill ourselves
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);    // lets the ButtonModel track hover
        }

        void setOutline(Color c)   { this.outline = c; repaint(); }
        void setTextColor(Color c) { this.textColor = c; repaint(); }

        /** Marks this as the active nav tab (gold text + gold outline). */
        void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Fill: disabled < hover < active < normal, in that priority.
            Color bg = !isEnabled() ? disabledFill
                     : getModel().isRollover() ? hoverFill
                     : active ? PANEL_HI
                     : fill;
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS_MD, RADIUS_MD);

            // Outline: gold when active or keyboard-focused, else default.
            Color line = (active || isFocusOwner()) ? GOLD : outline;
            if (line != null) {
                g2.setStroke(new BasicStroke(isFocusOwner() || active ? 2f : 1f));
                g2.setColor(line);
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, RADIUS_MD, RADIUS_MD);
            }
            g2.dispose();

            // Text colour tracks state, then let JButton draw the label.
            setForeground(active ? GOLD : textColor);
            super.paintComponent(g);
        }
    }

    /** Shared placeholder painter used by both rounded text inputs: draws
     *  the muted hint string while the field is empty and unfocused. */
    private static void paintPlaceholder(JTextComponentLike c, Graphics g, String placeholder) {
        if (!c.textIsEmpty() || c.hasFocusNow()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(MUTED);
        g2.setFont(c.fontNow());
        g2.drawString(placeholder, c.insetsNow().left,
                c.insetsNow().top + g2.getFontMetrics().getAscent());
        g2.dispose();
    }

    /** Minimal view of a text component that paintPlaceholder needs —
     *  lets one painter serve both JTextField and JTextArea subclasses. */
    private interface JTextComponentLike {
        boolean textIsEmpty();
        boolean hasFocusNow();
        Font    fontNow();
        Insets  insetsNow();
    }

    /**
     * Rounded, themed single-line text field with placeholder text.
     * Paints its own rounded fill + outline (thicker gold while focused),
     * so the old focus-listener border-swapping code is no longer needed.
     */
    private static class RoundedTextField extends JTextField implements JTextComponentLike {
        private final String placeholder;

        RoundedTextField(String placeholder) {
            this.placeholder = placeholder;
            setFont(BODY_FONT.deriveFont(16f));
            setOpaque(false); // rounded fill painted below
            setForeground(TEXT);
            setCaretColor(GOLD);
            setSelectionColor(PANEL_HOV);
            setBorder(new EmptyBorder(SP_2, SP_2 + 4, SP_2, SP_2 + 4)); // inner padding
            // Focus only changes the painted outline → just repaint.
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e)   { repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(PANEL);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS_MD, RADIUS_MD);
            g2.setStroke(new BasicStroke(isFocusOwner() ? 2f : 1f));
            g2.setColor(isFocusOwner() ? GOLD : PANEL_HI);
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, RADIUS_MD, RADIUS_MD);
            g2.dispose();
            super.paintComponent(g);              // text + caret
            paintPlaceholder(this, g, placeholder); // hint when empty
        }

        // JTextComponentLike adapter methods.
        @Override public boolean textIsEmpty() { return getText().isEmpty(); }
        @Override public boolean hasFocusNow() { return isFocusOwner(); }
        @Override public Font    fontNow()     { return getFont(); }
        @Override public Insets  insetsNow()   { return getInsets(); }
    }

    /** Multi-line sibling of RoundedTextField (used for the question box).
     *  Lives inside a rounded scroll pane, so it only paints its fill and
     *  placeholder — the outline comes from the scroll pane's border. */
    private static class RoundedTextArea extends JTextArea implements JTextComponentLike {
        private final String placeholder;

        RoundedTextArea(String placeholder, int rows, int cols) {
            super(rows, cols);
            this.placeholder = placeholder;
            setLineWrap(true);
            setWrapStyleWord(true);
            setFont(BODY_FONT.deriveFont(16f));
            setBackground(PANEL);
            setForeground(TEXT);
            setCaretColor(GOLD);
            setSelectionColor(PANEL_HOV);
            setBorder(new EmptyBorder(SP_2, SP_2 + 4, SP_2, SP_2 + 4));
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            paintPlaceholder(this, g, placeholder);
        }

        // JTextComponentLike adapter methods.
        @Override public boolean textIsEmpty() { return getText().isEmpty(); }
        @Override public boolean hasFocusNow() { return isFocusOwner(); }
        @Override public Font    fontNow()     { return getFont(); }
        @Override public Insets  insetsNow()   { return getInsets(); }
    }

    /** Slim, on-brand scrollbar: a small rounded thumb, no arrow buttons,
     *  and a slightly brighter thumb while dragging. */
    private static class ArcanaScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = PANEL_HI;
            trackColor = BG;
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isDragging ? GOLD_DIM : PANEL_HOV);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
            g2.dispose();
        }
        // Replace the stock arrow buttons with zero-size stand-ins.
        @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
        private JButton zeroButton() {
            JButton b = new JButton();
            Dimension zero = new Dimension(0, 0);
            b.setPreferredSize(zero);
            b.setMinimumSize(zero);
            b.setMaximumSize(zero);
            return b;
        }
    }

    /* ═════════════════════════ ENTRY POINT ════════════════════════ */
    public static void main(String[] args) {
        // Crisp text everywhere — matters a lot for a plain-JRE launch on
        // Windows, where antialiasing is otherwise off by default.
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // Themed tooltips so hints don't flash the stock bright-yellow box.
        UIManager.put("ToolTip.background", PANEL_HI);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", new RoundedLineBorder(GOLD, 1, RADIUS_MD));
        UIManager.put("ToolTip.font", SMALL_FONT.deriveFont(14f));

        // Themed option panes (used by the Clear-memory confirmation).
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("Panel.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", BODY_FONT.deriveFont(16f));
        UIManager.put("OptionPane.buttonFont", BODY_FONT.deriveFont(16f));

        // All Swing work happens on the Event Dispatch Thread.
        SwingUtilities.invokeLater(() -> new ArcanaApp().setVisible(true));
    }
}