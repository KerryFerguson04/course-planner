package planner;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main window: catalog search on the left, semester columns of course cards on
 * the right. Courses can be dragged from the search results onto a column, and
 * cards can be dragged between columns.
 */
final class PlannerFrame extends JFrame {
    // Card stripes and status text
    private static final Color OK = new Color(0x2E9E55);
    private static final Color WARN = new Color(0xD99A00);
    private static final Color ERROR = new Color(0xD64545);
    private static final Color DONE = new Color(0x9AA0A6);
    // Search-result badges (background, text)
    private static final Color READY_BG = new Color(0xDFF5E1), READY_FG = new Color(0x1E6B39);
    private static final Color CHECK_BG = new Color(0xFFF4CC), CHECK_FG = new Color(0x7A5600);
    private static final Color LOCKED_BG = new Color(0xF9D6D5), LOCKED_FG = new Color(0x8F2424);
    private static final Color IN_PLAN_BG = new Color(0xE3E6EA), IN_PLAN_FG = new Color(0x4A5058);

    private static final Color ACTIVE_BORDER = new Color(0x2F6FDB);
    private static final Color CARD_BORDER = new Color(0xD5D9DE);
    private static final Color COLUMN_BG = new Color(0xF3F4F6);
    private static final Color MUTED = new Color(0x6B7280);
    private static final int MAX_RESULTS = 300;
    private static final int COLUMN_WIDTH = 250;
    /** CSS width for wrapped card text; Swing scales HTML px up, so this stays well under the card width. */
    private static final int CARD_TEXT_WIDTH = 130;
    /** Drag source tag for courses dragged from the search results. */
    private static final String FROM_CATALOG = "CATALOG";

    private final Catalog catalog;
    private Plan plan = new Plan();
    /** Column that "Add" puts courses into: -1 = Completed, otherwise a semester index. */
    private int active = 0;
    /** The selected card, if any. */
    private int selectedCol = Integer.MIN_VALUE;
    private String selectedId;

    private final JTextField search = new JTextField();
    private final DefaultListModel<Course> results = new DefaultListModel<>();
    private final JList<Course> resultList = new JList<>(results) {
        @Override
        public String getToolTipText(MouseEvent e) {
            int i = locationToIndex(e.getPoint());
            if (i < 0 || !getCellBounds(i, i).contains(e.getPoint())) return null;
            return availabilityHint(getModel().getElementAt(i));
        }
    };
    private final JLabel resultCount = new JLabel(" ");
    private final JTextArea details = new JTextArea();
    private final JButton addButton = new JButton();
    private final JPanel columns = new JPanel();
    private final List<JPanel> columnPanels = new ArrayList<>();
    private final Map<String, JPanel> cardPanels = new HashMap<>();
    private final JFileChooser chooser = new JFileChooser(new File("."));

    PlannerFrame(Catalog catalog) {
        super("Course Planner");
        this.catalog = catalog;
        plan.semesters.add(new Plan.Semester(Plan.nextTermName(null)));

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildCatalogPanel(), buildPlanPanel());
        main.setDividerLocation(430);
        add(main);
        setSize(1350, 820);
        setLocationRelativeTo(null);

        refreshResults();
        rebuildColumns();
    }

    // ---- Left side: catalog -------------------------------------------------

    private JComponent buildCatalogPanel() {
        JPanel top = new JPanel(new BorderLayout(6, 4));
        top.add(new JLabel("Search:"), BorderLayout.WEST);
        top.add(search, BorderLayout.CENTER);
        top.add(resultCount, BorderLayout.SOUTH);
        search.setToolTipText("Course code (e.g. MATH 2) or words from the title");
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshResults(); }
            public void removeUpdate(DocumentEvent e) { refreshResults(); }
            public void changedUpdate(DocumentEvent e) { refreshResults(); }
        });
        search.addActionListener(e -> {
            if (!results.isEmpty()) resultList.setSelectedIndex(0);
        });

        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer(new ResultRenderer());
        ToolTipManager.sharedInstance().registerComponent(resultList);
        resultList.addListSelectionListener(e -> {
            Course c = resultList.getSelectedValue();
            if (!e.getValueIsAdjusting() && c != null) showDetails(c, null);
        });
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) addSelected();
            }
        });
        resultList.setDragEnabled(true);
        resultList.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                Course course = resultList.getSelectedValue();
                return course == null ? null : new StringSelection(FROM_CATALOG + "|" + course.id);
            }
        });

        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setMargin(new Insets(8, 8, 8, 8));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(resultList), new JScrollPane(details));
        split.setResizeWeight(0.55);

        addButton.addActionListener(e -> addSelected());
        JLabel tip = new JLabel("Tip: drag courses onto a semester, or double-click to add.");
        tip.setForeground(MUTED);
        tip.setFont(tip.getFont().deriveFont(11f));
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.add(addButton, BorderLayout.NORTH);
        bottom.add(tip, BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(top, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshResults() {
        List<Course> found = catalog.search(search.getText(), MAX_RESULTS);
        results.clear();
        results.addAll(found);
        resultCount.setText(found.size() >= MAX_RESULTS
                ? "Showing first " + MAX_RESULTS + " matches — keep typing to narrow"
                : found.size() + " match" + (found.size() == 1 ? "" : "es"));
    }

    /** One search result: bold code, title, credits, and a status badge. */
    private final class ResultRenderer extends JPanel implements ListCellRenderer<Course> {
        private final JLabel code = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel credits = new JLabel();
        private final JLabel badge = new JLabel();

        ResultRenderer() {
            super(new BorderLayout(8, 0));
            setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            code.setFont(code.getFont().deriveFont(Font.BOLD));
            credits.setFont(credits.getFont().deriveFont(11f));
            badge.setOpaque(true);
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 10.5f));
            badge.setBorder(BorderFactory.createEmptyBorder(1, 7, 1, 7));

            JPanel left = new JPanel(new BorderLayout(8, 0));
            left.setOpaque(false);
            left.add(code, BorderLayout.WEST);
            left.add(name, BorderLayout.CENTER);
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            right.setOpaque(false);
            right.add(credits);
            right.add(badge);
            add(left, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Course> list, Course c, int index,
                                                      boolean selected, boolean focus) {
            code.setText(c.id);
            name.setText(c.name);
            credits.setText(c.credits.isEmpty() ? "" : c.credits + " cr");
            switch (plan.availability(catalog, c.id)) {
                case IN_PLAN -> badge(" In plan ", IN_PLAN_BG, IN_PLAN_FG);
                case READY -> badge("Ready", READY_BG, READY_FG);
                case CHECK -> badge("Check", CHECK_BG, CHECK_FG);
                case LOCKED -> badge("Locked", LOCKED_BG, LOCKED_FG);
            }
            Color fg = selected ? list.getSelectionForeground() : list.getForeground();
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            code.setForeground(fg);
            name.setForeground(fg);
            credits.setForeground(selected ? fg : MUTED);
            return this;
        }

        private void badge(String text, Color bg, Color fg) {
            badge.setText(text);
            badge.setBackground(bg);
            badge.setForeground(fg);
        }
    }

    private String availabilityHint(Course c) {
        return switch (plan.availability(catalog, c.id)) {
            case IN_PLAN -> "Already in your plan";
            case READY -> "Ready — your plan covers the prerequisites";
            case CHECK -> "Some prerequisites can't be checked automatically";
            case LOCKED -> "Locked — needs " + String.join(" and ", plan.missing(catalog, c.id));
        };
    }

    private void addSelected() {
        Course c = resultList.getSelectedValue();
        if (c == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        addTo(active, c.id);
        select(active, c.id);
        rebuildColumns();
        showDetails(c, active >= 0 ? active : null);
    }

    private void showDetails(Course c, Integer semIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.id).append(" — ").append(c.name).append('\n');
        sb.append("Credits: ").append(c.credits.isEmpty() ? "—" : c.credits).append('\n');
        sb.append("Offered: ").append(c.typ.isEmpty() ? "(not listed)" : c.typ).append("\n\n");

        if (semIndex != null) {
            Plan.Check check = plan.check(catalog, semIndex, c.id);
            sb.append("Status in ").append(plan.semesters.get(semIndex).name).append(": ")
              .append(check.status()).append('\n');
            for (String m : check.messages()) sb.append("  • ").append(m).append('\n');
            sb.append('\n');
        }

        sb.append("Prerequisites: ").append(c.preReqText.isEmpty() ? "None" : c.preReqText).append('\n');
        if (!c.prereq.groups().isEmpty()) sb.append("  Checked as: ").append(c.prereq.describe()).append('\n');
        if (!c.prereq.notes().isEmpty()) {
            sb.append("  Not checked automatically: ").append(String.join("; ", c.prereq.notes())).append('\n');
        }
        if (c.prereq.concurrentOk()) sb.append("  Some prerequisites may be taken in the same semester.\n");
        if (!c.restrict.isEmpty()) sb.append("\nRestrictions: ").append(c.restrict).append('\n');
        if (!c.descr.isEmpty()) sb.append('\n').append(c.descr).append('\n');

        List<String> unlocks = catalog.unlockedBy(c.id);
        if (!unlocks.isEmpty()) {
            int shown = Math.min(unlocks.size(), 30);
            sb.append("\nPrerequisite for: ").append(String.join(", ", unlocks.subList(0, shown)));
            if (unlocks.size() > shown) sb.append(", … (").append(unlocks.size() - shown).append(" more)");
            sb.append('\n');
        }

        details.setText(sb.toString());
        details.setCaretPosition(0);
    }

    // ---- Right side: plan ---------------------------------------------------

    private JComponent buildPlanPanel() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(button("Add semester", e -> addSemester()));
        bar.add(button("Save plan…", e -> savePlan()));
        bar.add(button("Load plan…", e -> loadPlan()));
        bar.add(button("New plan", e -> newPlan()));
        bar.addSeparator();
        bar.add(legend(OK, "Prereqs met"));
        bar.add(legend(WARN, "Check manually"));
        bar.add(legend(ERROR, "Missing prereq"));

        columns.setLayout(new BoxLayout(columns, BoxLayout.X_AXIS));
        columns.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(bar, BorderLayout.NORTH);
        panel.add(new JScrollPane(columns), BorderLayout.CENTER);
        return panel;
    }

    private void rebuildColumns() {
        columns.removeAll();
        columnPanels.clear();
        cardPanels.clear();
        for (int idx = -1; idx < plan.semesters.size(); idx++) {
            JPanel col = buildColumn(idx);
            columnPanels.add(col);
            columns.add(col);
            columns.add(Box.createHorizontalStrut(10));
        }
        columns.add(Box.createHorizontalGlue());
        setActive(Math.min(active, plan.semesters.size() - 1));
        columns.revalidate();
        columns.repaint();
        resultList.repaint(); // badges depend on the plan
    }

    private JPanel buildColumn(int idx) {
        boolean isCompleted = idx < 0;
        List<String> ids = isCompleted ? new ArrayList<>(plan.completed) : plan.semesters.get(idx).courses;
        String title = isCompleted ? "Completed / Transfer" : plan.semesters.get(idx).name;

        JPanel cards = new JPanel();
        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        cards.setOpaque(false);
        if (ids.isEmpty()) {
            JLabel hint = new JLabel("Drag courses here");
            hint.setForeground(MUTED);
            hint.setBorder(BorderFactory.createEmptyBorder(16, 4, 16, 4));
            cards.add(hint);
        }
        for (String id : ids) {
            cards.add(buildCard(idx, id));
            cards.add(Box.createVerticalStrut(6));
        }
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(cards, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        // Header: title + credit total; double-click a semester title to rename it.
        JLabel header = new JLabel("<html><b>" + esc(title) + "</b><br>"
                + Plan.creditTotal(catalog, ids) + " credits</html>");
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header, BorderLayout.CENTER);
        if (!isCompleted) {
            header.setToolTipText("Double-click to rename");
            header.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) renameSemester(idx);
                }
            });
            JButton delete = small("✕", e -> deleteSemester(idx));
            delete.setToolTipText("Delete this semester");
            top.add(delete, BorderLayout.EAST);
        }

        JPanel bottom = new JPanel(new GridLayout(1, 0, 4, 0));
        bottom.setOpaque(false);
        if (!isCompleted) bottom.add(small("◀", e -> move(idx, selectedIn(idx), -1)));
        bottom.add(small("Remove", e -> remove(idx, selectedIn(idx))));
        if (!isCompleted) bottom.add(small("▶", e -> move(idx, selectedIn(idx), 1)));

        JPanel col = new JPanel(new BorderLayout(4, 8));
        col.setBackground(COLUMN_BG);
        col.add(top, BorderLayout.NORTH);
        col.add(scroll, BorderLayout.CENTER);
        col.add(bottom, BorderLayout.SOUTH);
        col.setPreferredSize(new Dimension(COLUMN_WIDTH, 400));
        col.setMaximumSize(new Dimension(COLUMN_WIDTH, Integer.MAX_VALUE));
        col.setTransferHandler(new PlanTransferHandler(idx, null));

        MouseAdapter activate = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                setActive(idx);
            }
        };
        col.addMouseListener(activate);
        header.addMouseListener(activate);
        return col;
    }

    /**
     * A course card: colored status stripe, code, credits, title, and (for
     * semesters) the first problem found, so it's visible without hovering.
     */
    private JPanel buildCard(int col, String id) {
        Course c = catalog.get(id);
        Plan.Check check = col < 0 ? null : plan.check(catalog, col, id);
        Color stripe = check == null ? DONE : switch (check.status()) {
            case OK -> OK;
            case WARN -> WARN;
            case ERROR -> ERROR;
        };

        JLabel code = new JLabel(id);
        code.setFont(code.getFont().deriveFont(Font.BOLD));
        JLabel credits = new JLabel(c == null || c.credits.isEmpty() ? "" : c.credits + " cr");
        credits.setForeground(MUTED);
        credits.setFont(credits.getFont().deriveFont(11f));
        JPanel firstRow = new JPanel(new BorderLayout());
        firstRow.setOpaque(false);
        firstRow.add(code, BorderLayout.WEST);
        firstRow.add(credits, BorderLayout.EAST);

        JLabel name = new JLabel(wrap(c == null ? "Not in catalog" : c.name));
        name.setFont(name.getFont().deriveFont(12f));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        for (JComponent part : List.of(firstRow, name)) {
            part.setAlignmentX(LEFT_ALIGNMENT);
            content.add(part);
        }
        if (check != null) {
            List<String> msgs = check.messages();
            String symbol = switch (check.status()) {
                case OK -> "✓ ";
                case WARN -> "! ";
                case ERROR -> "✗ ";
            };
            String more = msgs.size() > 1 ? " (+" + (msgs.size() - 1) + " more)" : "";
            JLabel status = new JLabel(wrap(symbol + msgs.get(0) + more));
            status.setFont(status.getFont().deriveFont(11f));
            status.setForeground(check.status() == Plan.Status.OK ? MUTED : stripe.darker());
            status.setAlignmentX(LEFT_ALIGNMENT);
            content.add(Box.createVerticalStrut(3));
            content.add(status);
        }

        JPanel stripePanel = new JPanel();
        stripePanel.setBackground(stripe);
        stripePanel.setPreferredSize(new Dimension(5, 0));

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.add(stripePanel, BorderLayout.WEST);
        card.add(content, BorderLayout.CENTER);
        styleCard(card, col == selectedCol && id.equals(selectedId));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setToolTipText(cardTooltip(id, c, check));
        card.setTransferHandler(new PlanTransferHandler(col, id));

        MouseAdapter mouse = new MouseAdapter() {
            private Point pressedAt;

            @Override
            public void mousePressed(MouseEvent e) {
                pressedAt = e.getPoint();
                select(col, id);
                setActive(col);
                if (c != null) showDetails(c, col >= 0 ? col : null);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressedAt != null && pressedAt.distance(e.getPoint()) > 5) {
                    pressedAt = null;
                    card.getTransferHandler().exportAsDrag(card, e, TransferHandler.MOVE);
                }
            }
        };
        card.addMouseListener(mouse);
        card.addMouseMotionListener(mouse);
        cardPanels.put(col + "|" + id, card);
        return card;
    }

    private String cardTooltip(String id, Course c, Plan.Check check) {
        StringBuilder sb = new StringBuilder("<html><b>").append(esc(id)).append("</b>");
        if (c != null) sb.append(" ").append(esc(c.name));
        if (check != null) {
            for (String m : check.messages()) sb.append("<br>• ").append(esc(m));
        }
        return sb.append("<br><i>Drag to move</i></html>").toString();
    }

    private static void styleCard(JPanel card, boolean selected) {
        Border line = selected
                ? BorderFactory.createLineBorder(ACTIVE_BORDER, 2)
                : BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1),
                        BorderFactory.createLineBorder(CARD_BORDER, 1));
        card.setBorder(line);
    }

    /** Selects a card and updates highlights in place (no rebuild, so a drag can start). */
    private void select(int col, String id) {
        JPanel previous = cardPanels.get(selectedCol + "|" + selectedId);
        if (previous != null) styleCard(previous, false);
        selectedCol = col;
        selectedId = id;
        JPanel current = cardPanels.get(col + "|" + id);
        if (current != null) styleCard(current, true);
    }

    private String selectedIn(int col) {
        return selectedCol == col ? selectedId : null;
    }

    /**
     * Accepts drops of "CATALOG|id" (from search results) or "column|id" (from
     * another card) onto a column. Cards use it too, so dropping onto a card
     * drops into that card's column.
     */
    private final class PlanTransferHandler extends TransferHandler {
        private final int column;
        private final String courseId;

        PlanTransferHandler(int column, String courseId) {
            this.column = column;
            this.courseId = courseId;
        }

        @Override
        public int getSourceActions(JComponent c) {
            return courseId == null ? NONE : MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            return new StringSelection(column + "|" + courseId);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) return false;
            if (active != column) setActive(column); // highlight the column under the pointer
            return true;
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                String[] parts = data.split("\\|", 2);
                if (parts.length < 2) return false;
                // Rebuild after the drag finishes, not while Swing is still handling it.
                SwingUtilities.invokeLater(() -> dropCourse(parts[0], parts[1], column));
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    private void dropCourse(String source, String id, int target) {
        Course c = catalog.get(id);
        if (c == null) return;
        if (!source.equals(FROM_CATALOG)) {
            int from;
            try {
                from = Integer.parseInt(source);
            } catch (NumberFormatException ex) {
                return;
            }
            if (from == target || from >= plan.semesters.size()) return;
            removeFrom(from, id);
        }
        addTo(target, id);
        active = target;
        selectedCol = target;
        selectedId = id;
        rebuildColumns();
        showDetails(c, target >= 0 ? target : null);
    }

    private void addTo(int col, String id) {
        if (col < 0) {
            plan.completed.add(id);
        } else {
            List<String> courses = plan.semesters.get(col).courses;
            if (!courses.contains(id)) courses.add(id);
        }
    }

    private void removeFrom(int col, String id) {
        if (col < 0) plan.completed.remove(id);
        else plan.semesters.get(col).courses.remove(id);
    }

    private void setActive(int idx) {
        active = idx;
        for (int k = 0; k < columnPanels.size(); k++) {
            boolean isActive = k - 1 == idx;
            columnPanels.get(k).setBorder(BorderFactory.createCompoundBorder(
                    isActive ? BorderFactory.createLineBorder(ACTIVE_BORDER, 2)
                             : BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1),
                                     BorderFactory.createLineBorder(CARD_BORDER, 1)),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        }
        addButton.setText("Add selected course to → "
                + (idx < 0 ? "Completed / Transfer" : plan.semesters.get(idx).name));
    }

    private void remove(int idx, String id) {
        if (id == null) return;
        removeFrom(idx, id);
        selectedId = null;
        rebuildColumns();
    }

    private void move(int idx, String id, int delta) {
        int target = idx + delta;
        if (id == null || target < 0 || target >= plan.semesters.size()) return;
        removeFrom(idx, id);
        addTo(target, id);
        active = target;
        selectedCol = target;
        rebuildColumns();
    }

    private void addSemester() {
        String last = plan.semesters.isEmpty() ? null : plan.semesters.get(plan.semesters.size() - 1).name;
        String name = askName("Semester name (e.g. Spring 2027):", Plan.nextTermName(last));
        if (name == null) return;
        plan.semesters.add(new Plan.Semester(name));
        active = plan.semesters.size() - 1;
        rebuildColumns();
    }

    private void renameSemester(int idx) {
        String name = askName("Rename semester:", plan.semesters.get(idx).name);
        if (name == null) return;
        plan.semesters.get(idx).name = name;
        rebuildColumns();
    }

    private void deleteSemester(int idx) {
        Plan.Semester s = plan.semesters.get(idx);
        if (!s.courses.isEmpty() && JOptionPane.showConfirmDialog(this,
                "Delete " + s.name + " and its " + s.courses.size() + " course(s)?",
                "Delete semester", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        plan.semesters.remove(idx);
        if (active >= idx) active--;
        selectedId = null;
        rebuildColumns();
    }

    private String askName(String prompt, String initial) {
        String name = (String) JOptionPane.showInputDialog(this, prompt, "Semester",
                JOptionPane.PLAIN_MESSAGE, null, null, initial);
        if (name == null || name.isBlank()) return null;
        return name.trim().replace('|', '/'); // '|' is the save-file separator
    }

    private void savePlan() {
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            plan.save(chooser.getSelectedFile().toPath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadPlan() {
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            plan = Plan.load(chooser.getSelectedFile().toPath());
            active = plan.semesters.isEmpty() ? -1 : 0;
            selectedId = null;
            rebuildColumns();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not load: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void newPlan() {
        if (JOptionPane.showConfirmDialog(this, "Start a new, empty plan? Unsaved changes will be lost.",
                "New plan", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        plan = new Plan();
        plan.semesters.add(new Plan.Semester(Plan.nextTermName(null)));
        active = 0;
        selectedId = null;
        rebuildColumns();
    }

    // ---- Helpers ------------------------------------------------------------

    private static JButton button(String text, ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    private static JButton small(String text, ActionListener action) {
        JButton b = button(text, action);
        b.setMargin(new Insets(1, 6, 1, 6));
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 11f));
        return b;
    }

    private static JLabel legend(Color stripe, String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.DARK_GRAY);
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 8, 0, 4),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 5, 0, 0, stripe),
                        BorderFactory.createEmptyBorder(2, 6, 2, 6))));
        return l;
    }

    /** HTML label text that wraps to the card width. */
    private static String wrap(String text) {
        return "<html><div style='width:" + CARD_TEXT_WIDTH + "px'>" + esc(text) + "</div></html>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
