/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.user_dialogs.knowledge;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import static java.awt.BorderLayout.*;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static javax.swing.BorderFactory.createEmptyBorder;
import static org.tarik.ta.utils.HtmlUtils.escapeHtml;
import static org.tarik.ta.utils.ImageUtils.getInstance;

/**
 * Builds Swing row panels for the child-steps list in {@link ProcedureKnowledgeCollectionDialog}.
 * Encapsulates all rendering logic (thumbnail, badges, action buttons) so the main dialog stays lean.
 */
class ChildStepRowBuilder {

    private static final int THUMBNAIL_WIDTH = 80;
    private static final int THUMBNAIL_HEIGHT = 50;

    /** Creates buttons with logging and hover-as-click behaviour. */
    @FunctionalInterface
    interface ButtonFactory {
        JButton create(String logContext, String text, ActionListener action);
    }

    private final IntSupplier selectedIndex;
    private final Set<Integer> stepsWithSimilarItems;
    private final BooleanSupplier isAtomic;
    @Nullable private final KnowledgeService knowledgeService;
    private final IntConsumer onSelectAndRefresh;
    private final IntConsumer onEdit;
    private final IntConsumer onLookup;
    private final ButtonFactory buttonFactory;

    ChildStepRowBuilder(IntSupplier selectedIndex,
                        Set<Integer> stepsWithSimilarItems,
                        BooleanSupplier isAtomic,
                        @Nullable KnowledgeService knowledgeService,
                        IntConsumer onSelectAndRefresh,
                        IntConsumer onEdit,
                        IntConsumer onLookup,
                        ButtonFactory buttonFactory) {
        this.selectedIndex = selectedIndex;
        this.stepsWithSimilarItems = stepsWithSimilarItems;
        this.isAtomic = isAtomic;
        this.knowledgeService = knowledgeService;
        this.onSelectAndRefresh = onSelectAndRefresh;
        this.onEdit = onEdit;
        this.onLookup = onLookup;
        this.buttonFactory = buttonFactory;
    }

    JPanel buildRow(int index, ChildProcedureInDialog step) {
        boolean selected = (index == selectedIndex.getAsInt());
        Color bg = getListColor(selected ? "List.selectionBackground" : "List.background",
                selected ? SystemColor.textHighlight : SystemColor.window);
        Color fg = getListColor(selected ? "List.selectionForeground" : "List.foreground",
                selected ? SystemColor.textHighlightText : SystemColor.windowText);

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBorder(createEmptyBorder(5, 5, 5, 5));
        row.setBackground(bg);
        row.add(new JLabel(buildThumbnailIcon(step)), WEST);
        row.add(buildCenterPanel(index, step, fg), CENTER);
        row.add(buildActionPanel(index), EAST);
        // Prevent BoxLayout from stretching rows vertically — fixes button/badge misalignment
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        addMouseListenerRecursively(row, new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isAtomic.getAsBoolean()) {
                    onSelectAndRefresh.accept(index);
                    if (e.getClickCount() == 2) onEdit.accept(index);
                }
            }
        });
        return row;
    }

    private JPanel buildCenterPanel(int index, ChildProcedureInDialog step, Color fg) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        boolean needsSave = step instanceof ChildProcedureInDialog.New n && n.needsSave();
        String dot = needsSave ? "<font color='red'>&#9679;</font>" : "<font color='green'>&#9679;</font>";
        String type = needsSave ? "TBD" : (step.isAtomic() ? "[Atomic]" : "[Composite]");
        // Use BorderLayout so the description fills available width and wraps on word boundaries
        JLabel text = new JLabel("<html><body>%s %d. %s %s</body></html>"
                .formatted(dot, index + 1, escapeHtml(type), escapeHtml(step.description())));
        text.setForeground(fg);
        if (needsSave) text.setToolTipText("Please edit this procedure and save it before proceeding");
        panel.add(text, CENTER);

        JPanel badges = buildBadgePanel(index, step);
        if (badges != null) panel.add(badges, SOUTH);
        return panel;
    }

    /** Returns a badge sub-panel for the row, or {@code null} if there are no badges to show. */
    @Nullable
    private JPanel buildBadgePanel(int index, ChildProcedureInDialog step) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        boolean hasBadges = false;
        // Badge: show "[→ similar items exist]" only for unlinked steps with HIGH-confidence matches
        if (step instanceof ChildProcedureInDialog.New && stepsWithSimilarItems.contains(index)) {
            panel.add(createBadge("[→ similar items exist]", new Color(180, 100, 0), Font.ITALIC));
            hasBadges = true;
        }
        // Badge: show "[→ linked]" for steps already linked to an existing procedure
        if (step instanceof ChildProcedureInDialog.Linked) {
            panel.add(createBadge("[→ linked]", new Color(0, 120, 0), Font.BOLD));
            hasBadges = true;
        }
        return hasBadges ? panel : null;
    }

    private static JLabel createBadge(String text, Color color, int fontStyle) {
        JLabel badge = new JLabel(text);
        badge.setForeground(color);
        badge.setFont(badge.getFont().deriveFont(fontStyle, badge.getFont().getSize() - 1f));
        return badge;
    }

    private JPanel buildActionPanel(int index) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        panel.setOpaque(false);
        // Search icon button — opens the existing-procedure lookup dialog for this step
        if (knowledgeService != null) {
            JButton searchBtn = buttonFactory.create("Search existing procedures for this step", "⌕", _ -> {
                onSelectAndRefresh.accept(index);
                onLookup.accept(index);
            });
            searchBtn.setEnabled(!isAtomic.getAsBoolean());
            searchBtn.setToolTipText("Search for existing similar procedures");
            panel.add(searchBtn);
        }
        JButton editBtn = buttonFactory.create("Edit child step", "Edit", _ -> {
            onSelectAndRefresh.accept(index);
            onEdit.accept(index);
        });
        editBtn.setEnabled(!isAtomic.getAsBoolean());
        panel.add(editBtn);
        return panel;
    }

    static ImageIcon buildThumbnailIcon(ChildProcedureInDialog step) {
        if (!step.isAtomic() || step.elementScreenshot() == null) {
            return new ImageIcon(new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, TYPE_INT_ARGB));
        }
        return new ImageIcon(getInstance().scaleToFitBox(step.elementScreenshot(), THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
    }

    private static Color getListColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    private static void addMouseListenerRecursively(Component component, MouseListener listener) {
        component.addMouseListener(listener);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                addMouseListenerRecursively(child, listener);
            }
        }
    }
}
