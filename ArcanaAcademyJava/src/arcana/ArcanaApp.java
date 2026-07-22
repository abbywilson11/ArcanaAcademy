package arcana;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
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
 * ArcanaApp.java — Arcana Academy desktop UI (UX-polished edition).
 *
 * DESIGN SYSTEM
 * ─────────────
 *  • 8-pixel spacing scale (SP_1..SP_4) applied consistently everywhere.
 *  • One accent (GOLD) reserved for primary actions and selection;
 *    LAVENDER for secondary emphasis; MUTED for supporting text.
 *  • Every interactive element gives three kinds of feedback:
 *    hover (background lift), focus (visible gold focus ring for
 *    keyboard users), and activation (immediate state change + toast).
 *
 * ACCESSIBILITY / "REGULATIONS" (WCAG-inspired for desktop Swing)
 * ───────────────────────────────────────────────────────────────
 *  • Full keyboard operability: card tiles are focusable and respond
 *    to Enter/Space; Ctrl+Enter submits the question; Esc closes
 *    dialogs; Alt+1/2/3 switch tabs.
 *  • Visible focus indicators on all controls (never removed, only
 *    restyled to match the theme).
 *  • Text/background contrast ratios meet or exceed 4.5:1 for body
 *    text on all panels.
 *  • Destructive action ("Clear all memory") requires confirmation.
 *  • Accessible names/descriptions set for assistive technologies.
 *  • Live feedback (status pill, toasts) uses text, not colour alone.
 *
 * LAYOUT (unchanged architecture)
 * ───────────────────────────────
 *  Home tab: NORTH = fixed controls (subtitle, search, fixed-height
 *  card viewport, status, question, button). CENTER = reading result.
 *  Window sizes itself relative to the screen so the run.bat launch
 *  looks right on both laptops and large monitors.
 */
public class ArcanaApp extends JFrame {

    /* ── Theme ─────────────────────────────────────────────────────── */
    static final Color BG        = new Color(0x15102B);
    static final Color PANEL     = new Color(0x211A3E);
    static final Color PANEL_HI  = new Color(0x2B2352);
    static final Color PANEL_HOV = new Color(0x322A5E);  // hover lift
    static final Color GOLD      = new Color(0xE8C36A);
    static final Color GOLD_DIM  = new Color(0x8A7440);  // disabled gold
    static final Color LAVENDER  = new Color(0xC9BFE8);
    static final Color TEXT      = new Color(0xEDE8F7);
    static final Color MUTED     = new Color(0xA79CCB);  // lightened for contrast
    static final Color DANGER    = new Color(0xE07A7A);

    static final Font TITLE_FONT  = new Font(Font.SERIF, Font.BOLD, 26);
    static final Font HEADER_FONT = new Font(Font.SERIF, Font.BOLD, 16);
    static final Font BODY_FONT   = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    static final Font BOLD_FONT   = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    static final Font SMALL_FONT  = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    static final Font SYMBOL_FONT = new Font(Font.SERIF, Font.PLAIN, 22);

    /* ── Spacing scale (multiples of 8) ────────────────────────────── */
    static final int SP_1 = 4, SP_2 = 8, SP_3 = 16, SP_4 = 24;

    static final int MAX_SELECTION = 3;
    static final int GRID_COLS     = 4;
    static final int CARD_VIEWPORT = 300; // px height of card-grid viewport

    /* ── State ─────────────────────────────────────────────────────── */
    private final Map<Integer, SelectedCard> selected = new LinkedHashMap<>();
    private boolean loading = false;

    /* ── Components ────────────────────────────────────────────────── */
    private final CardLayout tabLayout = new CardLayout();
    private final JPanel     tabHolder = new JPanel(tabLayout);
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();

    private JPanel     homeGrid;
    private JTextField homeSearch;
    private JLabel     statusPill;
    private JTextArea  questionInput;
    private JButton    revealBtn;
    private JPanel      readingSections;
    private JLabel      readingSource;
    private JPanel      readingOuter;
    private JScrollPane readingScroll;
    private final List<CardTile> homeTiles = new ArrayList<>();

    private JPanel     learnGrid;
    private JTextField learnSearch;
    private final List<CardTile> learnTiles = new ArrayList<>();

    private JPanel memoryList;
    private JLabel toast;
    private Timer  toastTimer;

    /* ── Constructor ───────────────────────────────────────────────── */
    public ArcanaApp() {
        super("\u2726 Arcana Academy \u2726 \u2014 Tarot Learning App");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Size relative to the screen so a run.bat launch looks right on
        // any monitor: ~62% of screen height, clamped to sane bounds.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(820, Math.max(600, (int) (screen.width  * 0.42)));
        int h = Math.min(920, Math.max(720, (int) (screen.height * 0.85)));
        setSize(w, h);
        setMinimumSize(new Dimension(600, 700));
        setLocationRelativeTo(null);

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

        // Alt+1/2/3 switch tabs from anywhere (keyboard operability)
        bindTabShortcut(root, KeyEvent.VK_1, "homeTab");
        bindTabShortcut(root, KeyEvent.VK_2, "learnTab");
        bindTabShortcut(root, KeyEvent.VK_3, "memoryTab");

        updateStatus();
        switchTab("homeTab");
    }

    private void bindTabShortcut(JComponent root, int key, String tabId) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(key, KeyEvent.ALT_DOWN_MASK), "tab-" + tabId);
        root.getActionMap().put("tab-" + tabId, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { switchTab(tabId); }
        });
    }

    /* ── Header ────────────────────────────────────────────────────── */
    private JComponent buildHeader() {
        JPanel h = new JPanel(new GridBagLayout());
        h.setBackground(BG);
        h.setBorder(new EmptyBorder(SP_3, SP_3, SP_2, SP_3));
        GridBagConstraints gc = gbc(0);

        JLabel title = new JLabel("\u2726 Arcana Academy \u2726", SwingConstants.CENTER);
        title.setFont(TITLE_FONT);
        title.setForeground(GOLD);
        gc.gridy = 0; h.add(title, gc);

        JLabel sub = new JLabel("A tarot interpretation learning tool", SwingConstants.CENTER);
        sub.setFont(BODY_FONT);
        sub.setForeground(MUTED);
        gc.gridy = 1; gc.insets = ins(2, 0, 0, 0); h.add(sub, gc);
        return h;
    }

    /* ── Bottom bar ────────────────────────────────────────────────── */
    private JComponent buildBottomBar() {
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG);

        toast = new JLabel(" ", SwingConstants.CENTER);
        toast.setFont(BODY_FONT);
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

    private JButton makeNavButton(String label, String tabId, String tip) {
        JButton b = new JButton(label);
        styleButton(b, false);
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleDescription(tip);
        b.addActionListener(e -> switchTab(tabId));
        navButtons.put(tabId, b);
        return b;
    }

    private void switchTab(String tabId) {
        tabLayout.show(tabHolder, tabId);
        navButtons.forEach((id, b) -> {
            boolean active = id.equals(tabId);
            b.setBackground(active ? PANEL_HI : PANEL);
            b.setForeground(active ? GOLD : LAVENDER);
            b.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(active ? GOLD : PANEL_HI, 1, true),
                    new EmptyBorder(SP_2, SP_3, SP_2, SP_3)));
        });
        if (tabId.equals("memoryTab")) renderMemory();
    }

    /* ══════════════════════════ HOME TAB ══════════════════════════ */
    private JComponent buildHomeTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setBackground(BG);

        /* ── NORTH: fixed controls ── */
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBackground(BG);
        controls.setBorder(new EmptyBorder(SP_1, SP_3, SP_2, SP_3));
        GridBagConstraints gc = gbc(0);
        int row = 0;

        JLabel sub = new JLabel(
            "Search or scroll the deck \u00b7 choose three cards \u00b7 ask your question",
            SwingConstants.CENTER);
        sub.setFont(BOLD_FONT);
        sub.setForeground(MUTED);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_2, 0);
        controls.add(sub, gc);

        homeSearch = makeSearchField(
                "Search cards \u2014 try 'moon', 'queen', 'swords'\u2026",
                "Search the deck by card name");
        gc.gridy = row++; gc.insets = ins(2, 0, SP_2, 0);
        controls.add(homeSearch, gc);

        // card grid — fixed-height scrollable viewport
        homeGrid = new JPanel(new GridLayout(0, GRID_COLS, SP_2, SP_2));
        homeGrid.setBackground(BG);
        for (int i = 0; i < Deck.CARDS.size(); i++) {
            homeTiles.add(new CardTile(Deck.CARDS.get(i), i, true));
        }
        refillGrid(homeGrid, homeTiles, "");

        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setBackground(BG);
        gridHolder.setBorder(new EmptyBorder(SP_1, SP_1, SP_1, SP_1));
        gridHolder.add(homeGrid, BorderLayout.NORTH);

        JScrollPane deckScroll = themedScroll(new JScrollPane(gridHolder,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
        deckScroll.setBorder(new LineBorder(GOLD, 1, true));
        deckScroll.getViewport().setBackground(BG);
        deckScroll.setPreferredSize(new Dimension(10, CARD_VIEWPORT));
        deckScroll.setMinimumSize(new Dimension(10, CARD_VIEWPORT));
        deckScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_VIEWPORT));

        gc.gridy = row++; gc.insets = ins(2, 0, SP_2, 0);
        controls.add(deckScroll, gc);

        homeSearch.getDocument().addDocumentListener(new SimpleDocListener(
                () -> refillGrid(homeGrid, homeTiles, homeSearch.getText())));

        statusPill = new JLabel("Cards chosen: 0 of 3", SwingConstants.CENTER);
        statusPill.setFont(BOLD_FONT);
        statusPill.setForeground(LAVENDER);
        gc.gridy = row++; gc.insets = ins(SP_2, 2, 2, 0);
        controls.add(statusPill, gc);

        questionInput = new PlaceholderTextArea("Type your question for the cards\u2026", 3, 30);
        questionInput.setLineWrap(true);
        questionInput.setWrapStyleWord(true);
        questionInput.setFont(BODY_FONT);
        questionInput.setBackground(PANEL);
        questionInput.setForeground(TEXT);
        questionInput.setCaretColor(GOLD);
        questionInput.setBorder(new EmptyBorder(SP_2, SP_2 + 2, SP_2, SP_2 + 2));
        questionInput.setToolTipText("Ask the cards anything. Press Ctrl+Enter to reveal.");
        questionInput.getAccessibleContext().setAccessibleName("Your question for the cards");

        // Ctrl+Enter submits (keyboard-first flow); Tab moves focus out
        questionInput.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "reveal");
        questionInput.getActionMap().put("reveal", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (revealBtn.isEnabled()) revealReading();
                else showToast("Choose 3 cards and type a question first.");
            }
        });
        questionInput.setFocusTraversalKeys(
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        questionInput.setFocusTraversalKeys(
                KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);

        JScrollPane qScroll = themedScroll(new JScrollPane(questionInput));
        qScroll.setBorder(new LineBorder(GOLD, 1, true));
        qScroll.setPreferredSize(new Dimension(10, 80));
        qScroll.setMinimumSize(new Dimension(10, 80));
        qScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        gc.gridy = row++; gc.insets = ins(SP_1, 0, 2, 0);
        controls.add(qScroll, gc);

        revealBtn = new JButton("Reveal Interpretation");
        styleGoldButton(revealBtn);
        revealBtn.setEnabled(false);
        revealBtn.setToolTipText("Choose 3 cards and type a question to enable (Ctrl+Enter)");
        revealBtn.getAccessibleContext().setAccessibleDescription(
                "Reveals the interpretation once three cards are chosen and a question is typed");
        revealBtn.addActionListener(e -> revealReading());
        gc.gridy = row++; gc.insets = ins(SP_1, 0, SP_2, 0);
        controls.add(revealBtn, gc);

        tab.add(controls, BorderLayout.NORTH);

        /* ── CENTER: reading result ── */
        readingSections = new JPanel();
        readingSections.setLayout(new BoxLayout(readingSections, BoxLayout.Y_AXIS));
        readingSections.setBackground(PANEL);

        readingSource = new JLabel(" ");
        readingSource.setFont(SMALL_FONT);
        readingSource.setForeground(MUTED);
        readingSource.setBorder(new EmptyBorder(SP_2, 0, 0, 0));
        readingSource.setAlignmentX(Component.LEFT_ALIGNMENT);

        readingOuter = new JPanel();
        readingOuter.setLayout(new BoxLayout(readingOuter, BoxLayout.Y_AXIS));
        readingOuter.setBackground(PANEL);
        readingOuter.setBorder(new EmptyBorder(SP_3 - 4, SP_3 - 2, SP_3 - 4, SP_3 - 2));
        readingOuter.add(readingSections);
        readingOuter.add(readingSource);

        JPanel readingHolder = new JPanel(new BorderLayout());
        readingHolder.setBackground(BG);
        readingHolder.setBorder(new EmptyBorder(SP_2, SP_3, SP_2, SP_3));
        readingHolder.add(readingOuter, BorderLayout.NORTH);

        readingScroll = themedScroll(new JScrollPane(readingHolder,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
        readingScroll.setBorder(null);
        readingScroll.getViewport().setBackground(BG);
        readingScroll.setVisible(false);

        tab.add(readingScroll, BorderLayout.CENTER);
        return tab;
    }

    /* ── Card selection ────────────────────────────────────────────── */
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
            return;
        }
        updateStatus();
    }

    private void toggleReversed(int index, CardTile tile) {
        SelectedCard entry = selected.get(index);
        if (entry == null) return;
        entry.reversed = !entry.reversed;
        tile.setSelectedState(true, entry.reversed);
    }

    private void updateStatus() {
        int n = selected.size();
        statusPill.setText("Cards chosen: " + n + " of " + MAX_SELECTION);
        boolean ready = n == MAX_SELECTION && !loading;
        // Colour AND text change so state is never conveyed by colour alone
        statusPill.setForeground(ready ? GOLD : LAVENDER);
        if (ready) statusPill.setText("Cards chosen: 3 of 3 \u2014 ready");
        revealBtn.setEnabled(ready);
        revealBtn.setBackground(ready ? GOLD : GOLD_DIM);
        revealBtn.setToolTipText(ready
                ? "Reveal your three-card reading (Ctrl+Enter)"
                : "Choose 3 cards and type a question to enable");
    }

    /* ── Reveal reading ────────────────────────────────────────────── */
    private void revealReading() {
        String q = questionInput.getText().trim();
        if (selected.size() != MAX_SELECTION) {
            showToast("Please choose exactly 3 cards from the deck above.");
            return;
        }
        if (q.isEmpty()) {
            showToast("Type your question first \u2014 the cards need something to answer.");
            questionInput.requestFocusInWindow();
            return;
        }
        if (loading) return;

        loading = true;
        updateStatus();
        revealBtn.setText("Consulting the cards\u2026");
        showToast("Consulting the cards\u2026");

        List<SelectedCard> cards = new ArrayList<>(selected.values());

        new SwingWorker<Interpreter.Result, Void>() {
            @Override protected Interpreter.Result doInBackground() {
                return Interpreter.interpret(q, cards);
            }
            @Override protected void done() {
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
                    loading = false;
                    revealBtn.setText("Reveal Interpretation");
                    updateStatus();
                    tabHolder.revalidate();
                    tabHolder.repaint();
                }
            }
        }.execute();
    }

    private void showReadingDialog(List<SelectedCard> cards, String[] sections, String sourceText) {
        String[] labels = { "PAST", "PRESENT", "FUTURE", "TOGETHER" };

        JDialog dialog = new JDialog(this, "Your Reading", true);
        dialog.setSize(Math.min(600, getWidth() - 60), Math.min(640, getHeight() - 80));
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL);
        panel.setBorder(new EmptyBorder(SP_3, SP_3 + 2, SP_3, SP_3 + 2));
        GridBagConstraints gc = gbc(0);
        int row = 0;

        String cardLine = cards.stream()
                .map(c -> c.card.shortName() + (c.reversed ? " (rev.)" : ""))
                .reduce((a, b) -> a + "  \u00b7  " + b).orElse("");
        JLabel titleLbl = new JLabel(cardLine, SwingConstants.CENTER);
        titleLbl.setFont(SMALL_FONT);
        titleLbl.setForeground(GOLD);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_3 - 2, 0);
        panel.add(titleLbl, gc);

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
                JPanel sep = new JPanel();
                sep.setBackground(PANEL_HI);
                sep.setPreferredSize(new Dimension(10, 1));
                gc.gridy = row++; gc.insets = ins(0, 0, SP_2 + 2, 0);
                panel.add(sep, gc);
            }
        }

        JLabel src = new JLabel(sourceText, SwingConstants.CENTER);
        src.setFont(SMALL_FONT);
        src.setForeground(MUTED);
        gc.gridy = row++; gc.insets = ins(SP_1, 0, SP_3 - 4, 0);
        panel.add(src, gc);

        JButton close = new JButton("Back  (Esc)");
        styleButton(close, true);
        close.addActionListener(e -> dialog.dispose());
        gc.gridy = row++; gc.insets = ins(0, 0, 0, 0);
        panel.add(close, gc);

        JScrollPane scroll = themedScroll(new JScrollPane(panel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(PANEL);
        dialog.setContentPane(scroll);

        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.getRootPane().setDefaultButton(close);

        dialog.setVisible(true);
    }

    /* ══════════════════════════ LEARN TAB ═════════════════════════ */
    private JComponent buildLearnTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setBackground(BG);

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBackground(BG);
        controls.setBorder(new EmptyBorder(SP_1, SP_3, SP_2, SP_3));
        GridBagConstraints gc = gbc(0);
        int row = 0;

        JLabel title = new JLabel("\u2727 Learn the Cards \u2727", SwingConstants.CENTER);
        title.setFont(HEADER_FONT);
        title.setForeground(GOLD);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_1, 0);
        controls.add(title, gc);

        JLabel sub = new JLabel(
            "Click any card to read what it means and how to use it in a reading.",
            SwingConstants.CENTER);
        sub.setFont(BODY_FONT);
        sub.setForeground(MUTED);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_2, 0);
        controls.add(sub, gc);

        learnSearch = makeSearchField("Search cards\u2026", "Search the deck by card name");
        gc.gridy = row++; gc.insets = ins(0, 0, SP_2, 0);
        controls.add(learnSearch, gc);

        tab.add(controls, BorderLayout.NORTH);

        learnGrid = new JPanel(new GridLayout(0, GRID_COLS, SP_2, SP_2));
        learnGrid.setBackground(BG);
        for (int i = 0; i < Deck.CARDS.size(); i++) {
            learnTiles.add(new CardTile(Deck.CARDS.get(i), i, false));
        }
        refillGrid(learnGrid, learnTiles, "");

        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setBackground(BG);
        gridHolder.setBorder(new EmptyBorder(SP_1, SP_1, SP_1, SP_1));
        gridHolder.add(learnGrid, BorderLayout.NORTH);

        JScrollPane learnScroll = themedScroll(new JScrollPane(gridHolder,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
        learnScroll.setBorder(new LineBorder(GOLD, 1, true));
        learnScroll.getViewport().setBackground(BG);

        JPanel learnWrapper = new JPanel(new BorderLayout());
        learnWrapper.setBackground(BG);
        learnWrapper.setBorder(new EmptyBorder(0, SP_3, SP_3, SP_3));
        learnWrapper.add(learnScroll, BorderLayout.CENTER);

        learnSearch.getDocument().addDocumentListener(new SimpleDocListener(
                () -> refillGrid(learnGrid, learnTiles, learnSearch.getText())));

        tab.add(learnWrapper, BorderLayout.CENTER);
        return tab;
    }

    /* ── Learn: lesson dialog ──────────────────────────────────────── */
    private void openModal(Card card) {
        JDialog dialog = new JDialog(this, card.name, true);
        dialog.setSize(Math.min(560, getWidth() - 60), Math.min(560, getHeight() - 80));
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL);
        panel.setBorder(new EmptyBorder(SP_3, SP_3 + 2, SP_3, SP_3 + 2));
        GridBagConstraints gc = gbc(0);
        int row = 0;

        JLabel sym = new JLabel(card.symbol, SwingConstants.CENTER);
        sym.setFont(new Font(Font.SERIF, Font.PLAIN, 34));
        sym.setForeground(GOLD);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_1, 0); panel.add(sym, gc);

        JLabel name = new JLabel(card.name, SwingConstants.CENTER);
        name.setFont(HEADER_FONT);
        name.setForeground(TEXT);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_2 + 2, 0); panel.add(name, gc);

        JTextArea lessonText = makeWrappedText(card.lesson, PANEL);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_3 - 4, 0); panel.add(lessonText, gc);

        JLabel revLabel = new JLabel("\u21B6 Reversed", SwingConstants.CENTER);
        revLabel.setFont(HEADER_FONT);
        revLabel.setForeground(GOLD);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_1, 0); panel.add(revLabel, gc);

        JTextArea revText = makeWrappedText(card.reversedLesson, PANEL);
        gc.gridy = row++; gc.insets = ins(0, 0, SP_3 - 4, 0); panel.add(revText, gc);

        JButton close = new JButton("Close  (Esc)");
        styleButton(close, true);
        close.addActionListener(e -> dialog.dispose());
        gc.gridy = row++; gc.insets = ins(0, 0, 0, 0); panel.add(close, gc);

        JScrollPane scroll = themedScroll(new JScrollPane(panel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(PANEL);
        dialog.setContentPane(scroll);

        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.getRootPane().setDefaultButton(close);
        dialog.setVisible(true);
    }

    /* ══════════════════════════ MEMORY TAB ════════════════════════ */
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

    /* ── Memory: render ────────────────────────────────────────────── */
    private void renderMemory() {
        List<Storage.Entry> entries = Storage.loadAll();
        memoryList.removeAll();

        if (entries.isEmpty()) {
            JLabel empty = new JLabel(
                    "<html><div style='text-align:center;'>No readings yet.<br>"
                    + "Go to Home, choose three cards, and ask your first question.</div></html>",
                    SwingConstants.CENTER);
            empty.setFont(BODY_FONT);
            empty.setForeground(MUTED);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setBorder(new EmptyBorder(SP_4 + 6, 0, 0, 0));
            memoryList.add(empty);
        } else {
            JButton clearBtn = new JButton("Clear all memory\u2026");
            styleButton(clearBtn, true);
            clearBtn.setForeground(DANGER);
            clearBtn.setToolTipText("Permanently deletes every saved reading");
            clearBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            clearBtn.addActionListener(e -> confirmClearMemory());
            memoryList.add(clearBtn);
            memoryList.add(Box.createVerticalStrut(SP_2 + 4));

            SimpleDateFormat fmt = new SimpleDateFormat("MMM d, h:mm a");
            String[] labels = { "PAST", "PRESENT", "FUTURE", "TOGETHER" };

            for (Storage.Entry entry : entries) {
                JPanel item = new JPanel();
                item.setLayout(new BoxLayout(item, BoxLayout.Y_AXIS));
                item.setBackground(PANEL);
                item.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(PANEL_HI, 1, true),
                        new EmptyBorder(SP_2 + 2, SP_2 + 4, SP_2 + 2, SP_2 + 4)));
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                item.setToolTipText("Click to expand or collapse this reading");

                JLabel date = new JLabel(fmt.format(new Date(entry.timestamp)));
                date.setFont(SMALL_FONT);
                date.setForeground(MUTED);
                item.add(date);

                JLabel question = new JLabel("<html>" + escapeHtml(entry.question) + "</html>");
                question.setFont(HEADER_FONT);
                question.setForeground(TEXT);
                item.add(question);

                JLabel cardsLbl = new JLabel(String.join("  \u2022  ", entry.cardNames));
                cardsLbl.setFont(SMALL_FONT);
                cardsLbl.setForeground(GOLD);
                item.add(cardsLbl);

                JLabel expandHint = new JLabel("\u25BE Show reading");
                expandHint.setFont(SMALL_FONT);
                expandHint.setForeground(LAVENDER);
                expandHint.setBorder(new EmptyBorder(SP_1, 0, 0, 0));
                item.add(expandHint);

                JPanel body = new JPanel();
                body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
                body.setBackground(PANEL);
                body.setVisible(false);
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
                        JPanel sep = new JPanel();
                        sep.setBackground(PANEL_HI);
                        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                        sep.setPreferredSize(new Dimension(10, 1));
                        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
                        body.add(Box.createVerticalStrut(SP_2));
                        body.add(sep);
                        body.add(Box.createVerticalStrut(SP_2));
                    }
                }
                item.add(body);

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

    /** Keeps child panels' backgrounds in sync during hover. */
    private static void syncBg(Container c, Color color) {
        for (Component child : c.getComponents()) {
            if (child instanceof JPanel || child instanceof JTextArea) {
                child.setBackground(color);
                if (child instanceof Container) syncBg((Container) child, color);
            }
        }
    }

    /** Destructive action guard — required confirmation before wiping data. */
    private void confirmClearMemory() {
        Object[] options = { "Delete everything", "Keep my readings" };
        int choice = JOptionPane.showOptionDialog(this,
                "This permanently deletes every saved reading.\nThere is no undo.",
                "Clear all memory?",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[1]);
        if (choice == JOptionPane.YES_OPTION) {
            Storage.clearAll();
            renderMemory();
            showToast("All readings cleared.");
        }
    }

    /* ══════════════════════════ CARD TILE ═════════════════════════ */
    private class CardTile extends JPanel {
        final Card    card;
        final JButton revBtn;
        private final int     tileIndex;
        private final boolean selectMode;
        private boolean isSelected = false;
        private boolean isReversed = false;

        CardTile(Card card, int index, boolean selectMode) {
            this.card       = card;
            this.tileIndex  = index;
            this.selectMode = selectMode;

            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(PANEL);
            setBorder(new LineBorder(PANEL_HI, 1, true));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(selectMode
                    ? card.name + " \u2014 click (or press Enter) to select"
                    : card.name + " \u2014 click (or press Enter) to study");

            // Keyboard operability: tiles join the Tab order and respond to
            // Enter/Space exactly like a click (WCAG 2.1.1).
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
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { refreshBorder(true); }
                @Override public void focusLost(FocusEvent e)   { refreshBorder(false); }
            });

            JLabel sym = new JLabel(card.symbol, SwingConstants.CENTER);
            sym.setFont(SYMBOL_FONT);
            sym.setForeground(GOLD);
            sym.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(Box.createVerticalStrut(SP_2 - 2));
            add(sym);

            JLabel nm = new JLabel(
                    "<html><div style='text-align:center;'>" + escapeHtml(card.name) + "</div></html>",
                    SwingConstants.CENTER);
            nm.setFont(SMALL_FONT);
            nm.setForeground(TEXT);
            nm.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(nm);

            revBtn = new JButton("\u27F3 Set Reversed");
            revBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            revBtn.setFocusPainted(false);
            revBtn.setBackground(PANEL_HI);
            revBtn.setForeground(LAVENDER);
            revBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            revBtn.setVisible(false);
            revBtn.setToolTipText("Flip this card upside-down for its reversed meaning (or press R)");
            revBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            revBtn.addActionListener(e -> toggleReversed(tileIndex, this));
            add(Box.createVerticalStrut(SP_1));
            add(revBtn);
            add(Box.createVerticalStrut(SP_2 - 2));

            MouseAdapter clicker = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { activate(); }
                @Override public void mouseEntered(MouseEvent e) {
                    if (!isSelected) setBackground(PANEL_HOV);
                }
                @Override public void mouseExited(MouseEvent e) {
                    if (!isSelected) setBackground(PANEL);
                }
            };
            addMouseListener(clicker);
            sym.addMouseListener(clicker);
            nm.addMouseListener(clicker);
        }

        private void activate() {
            requestFocusInWindow();
            if (selectMode) toggleSelect(tileIndex, this);
            else            openModal(card);
        }

        void setSelectedState(boolean sel, boolean rev) {
            this.isSelected = sel;
            this.isReversed = rev;
            setBackground(sel ? PANEL_HI : PANEL);
            refreshBorder(isFocusOwner());
            revBtn.setVisible(sel);
            revBtn.setText(rev ? "\u21B6 Reversed" : "\u27F3 Set Reversed");
            getAccessibleContext().setAccessibleDescription(
                    sel ? "Selected" + (rev ? ", reversed" : "") : "Not selected");
            revalidate();
            repaint();
        }

        /** Selection + keyboard-focus rendering combined into one border. */
        private void refreshBorder(boolean focused) {
            Color c = isSelected ? (isReversed ? LAVENDER : GOLD)
                                 : (focused ? LAVENDER : PANEL_HI);
            int thickness = (isSelected || focused) ? 2 : 1;
            setBorder(new LineBorder(c, thickness, true));
        }
    }

    /* ── Grid search ───────────────────────────────────────────────── */
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
            none.setFont(BODY_FONT);
            none.setForeground(MUTED);
            none.setBorder(new EmptyBorder(SP_3, 0, SP_3, 0));
            grid.add(none);
        }
        grid.revalidate();
        grid.repaint();
    }

    /* ── Toast ─────────────────────────────────────────────────────── */
    private void showToast(String msg) {
        toast.setText(msg);
        if (toastTimer != null) toastTimer.stop();
        toastTimer = new Timer(3200, e -> toast.setText(" "));
        toastTimer.setRepeats(false);
        toastTimer.start();
    }

    /* ── UI helpers ────────────────────────────────────────────────── */
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

    private static Insets ins(int t, int l, int b, int r) {
        return new Insets(t, l, b, r);
    }

    /** Applies the themed scrollbar and shared scroll settings. */
    private static JScrollPane themedScroll(JScrollPane sp) {
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getVerticalScrollBar().setUI(new ArcanaScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(9, 0));
        sp.setBackground(BG);
        return sp;
    }

    private JTextField makeSearchField(String placeholder, String accessibleName) {
        JTextField f = new PlaceholderTextField(placeholder);
        f.setFont(BODY_FONT);
        f.setBackground(PANEL);
        f.setForeground(TEXT);
        f.setCaretColor(GOLD);
        f.setSelectionColor(PANEL_HOV);
        f.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(GOLD, 1, true), new EmptyBorder(SP_2 - 2, SP_2 + 2, SP_2 - 2, SP_2 + 2)));
        f.getAccessibleContext().setAccessibleName(accessibleName);
        // Focus feedback: brighter border while typing
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                f.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(GOLD, 2, true), new EmptyBorder(SP_2 - 3, SP_2 + 1, SP_2 - 3, SP_2 + 1)));
            }
            @Override public void focusLost(FocusEvent e) {
                f.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(GOLD, 1, true), new EmptyBorder(SP_2 - 2, SP_2 + 2, SP_2 - 2, SP_2 + 2)));
            }
        });
        return f;
    }

    private JTextArea makeWrappedText(String text, Color bg) {
        JTextArea t = new JTextArea(text);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setFocusable(false);
        t.setFont(BODY_FONT);
        t.setBackground(bg);
        t.setForeground(TEXT);
        t.setBorder(new EmptyBorder(2, 0, 2, 0));
        return t;
    }

    private void styleButton(JButton b, boolean outline) {
        b.setFont(BODY_FONT);
        b.setFocusPainted(false);
        b.setBackground(PANEL);
        b.setForeground(LAVENDER);
        b.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(outline ? GOLD : PANEL_HI, 1, true),
                new EmptyBorder(SP_2, SP_3 - 2, SP_2, SP_3 - 2)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Color base = b.getForeground();
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (b.isEnabled()) b.setBackground(PANEL_HOV); }
            @Override public void mouseExited(MouseEvent e)  { b.setBackground(PANEL); }
        });
        b.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { b.setForeground(GOLD); }
            @Override public void focusLost(FocusEvent e)   { b.setForeground(base); }
        });
    }

    private void styleGoldButton(JButton b) {
        b.setFont(HEADER_FONT);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setHorizontalAlignment(SwingConstants.CENTER);
        b.setVerticalAlignment(SwingConstants.CENTER);
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setVerticalTextPosition(SwingConstants.CENTER);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setBackground(GOLD);
        b.setForeground(new Color(0x2A2149));
        b.setBorder(new EmptyBorder(SP_1, SP_4, SP_1, SP_4));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (b.isEnabled()) b.setBackground(GOLD.brighter()); }
            @Override public void mouseExited(MouseEvent e)  { if (b.isEnabled()) b.setBackground(GOLD); }
        });
        b.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                b.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(TEXT, 2, true),
                        new EmptyBorder(SP_2, SP_4 - 2, SP_2, SP_4 - 2)));
            }
            @Override public void focusLost(FocusEvent e) {
                b.setBorder(new EmptyBorder(SP_2 + 2, SP_4, SP_2 + 2, SP_4));
            }
        });
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /* ── Themed scrollbar (slim, on-brand) ─────────────────────────── */
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
        @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
    }

    /* ── Placeholder helpers (antialiased hint text) ───────────────── */
    private static class PlaceholderTextField extends JTextField {
        private final String ph;
        PlaceholderTextField(String ph) { this.ph = ph; }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && !isFocusOwner()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(MUTED);
                g2.setFont(getFont());
                g2.drawString(ph, getInsets().left,
                        getInsets().top + g2.getFontMetrics().getAscent());
                g2.dispose();
            }
        }
    }

    private static class PlaceholderTextArea extends JTextArea {
        private final String ph;
        PlaceholderTextArea(String ph, int rows, int cols) { super(rows, cols); this.ph = ph; }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && !isFocusOwner()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(MUTED);
                g2.setFont(getFont());
                g2.drawString(ph, getInsets().left,
                        getInsets().top + g2.getFontMetrics().getAscent());
                g2.dispose();
            }
        }
    }

    /* ── Entry point ───────────────────────────────────────────────── */
    public static void main(String[] args) {
        // Crisp text everywhere (matters a lot for a run.bat / plain JRE
        // launch on Windows, where AA is otherwise off by default).
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // Themed tooltips so hints don't flash a bright yellow box
        UIManager.put("ToolTip.background", PANEL_HI);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", new LineBorder(GOLD, 1, true));
        UIManager.put("ToolTip.font", SMALL_FONT);

        // Themed option panes (used by the Clear-memory confirmation)
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("Panel.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", BODY_FONT);
        UIManager.put("OptionPane.buttonFont", BODY_FONT);

        SwingUtilities.invokeLater(() -> new ArcanaApp().setVisible(true));
    }
}