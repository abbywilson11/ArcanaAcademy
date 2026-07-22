package arcana;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** SimpleDocListener.java — runs one callback on any text change (live search). */
class SimpleDocListener implements DocumentListener {
    private final Runnable onChange;

    SimpleDocListener(Runnable onChange) {
        this.onChange = onChange;
    }

    @Override public void insertUpdate(DocumentEvent e)  { onChange.run(); }
    @Override public void removeUpdate(DocumentEvent e)  { onChange.run(); }
    @Override public void changedUpdate(DocumentEvent e) { onChange.run(); }
}
