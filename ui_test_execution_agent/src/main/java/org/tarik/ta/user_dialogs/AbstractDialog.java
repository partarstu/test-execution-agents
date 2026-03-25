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
package org.tarik.ta.user_dialogs;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import static javax.swing.text.StyleConstants.setAlignment;

public abstract class AbstractDialog extends JDialog {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDialog.class);
    public static final int SCREENSHOT_DISPLAY_MAX_WIDTH = 600;
    public static final int SCREENSHOT_DISPLAY_MAX_HEIGHT = 400;
    protected final UiTestAgentConfig uiTestAgentConfig;
    protected final int dialogDefaultVerticalGap;
    protected final int dialogDefaultHorizontalGap;

    public AbstractDialog(Window owner, String title, UiTestAgentConfig uiTestAgentConfig) throws HeadlessException {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.uiTestAgentConfig = uiTestAgentConfig;
        this.dialogDefaultVerticalGap = uiTestAgentConfig.getDialogDefaultVerticalGap();
        this.dialogDefaultHorizontalGap = uiTestAgentConfig.getDialogDefaultHorizontalGap();
        setAlwaysOnTop(true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                LOG.info("User closed the '{}' dialog.", title);
                onDialogClosing();
            }
        });
    }

    @Override
    public void pack() {
        super.pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension dialogSize = getSize();
        // Only limit size if it exceeds 95% of screen dimensions
        if (dialogSize.width > screenSize.width * 0.95 || dialogSize.height > screenSize.height * 0.95) {
            int newWidth = Math.min(dialogSize.width, (int) (screenSize.width * 0.95));
            int newHeight = Math.min(dialogSize.height, (int) (screenSize.height * 0.95));
            setSize(newWidth, newHeight);
        }
    }

    protected abstract void onDialogClosing();

    @NotNull
    protected JPanel getDefaultMainPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(dialogDefaultHorizontalGap, dialogDefaultVerticalGap));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(dialogDefaultVerticalGap, dialogDefaultHorizontalGap,
                dialogDefaultVerticalGap, dialogDefaultHorizontalGap));
        return mainPanel;
    }

    public void setHoverAsClick(JComponent component, Runnable actionAfterClick) {
        if (uiTestAgentConfig.isDialogHoverAsClick()) {
            component.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (component instanceof AbstractButton) {
                        ((AbstractButton) component).doClick();
                    } else {
                        MouseEvent clickEvent = new MouseEvent(
                                component,
                                MouseEvent.MOUSE_CLICKED,
                                System.currentTimeMillis(),
                                0,
                                e.getX(),
                                e.getY(),
                                1,
                                false);
                        component.dispatchEvent(clickEvent);
                    }
                    actionAfterClick.run();
                }
            });
        }
    }

    public void setHoverAsClick(JComponent component) {
        setHoverAsClick(component, () -> {
        });
    }

    @NotNull
    public JPanel getButtonsPanel(JButton... buttons) {
        var panel = new JPanel(
                new FlowLayout(FlowLayout.CENTER, dialogDefaultHorizontalGap, dialogDefaultVerticalGap));
        for (JButton button : buttons) {
            panel.add(button);
        }
        return panel;
    }

    @NotNull
    public JTextPane getUserMessageArea(String message) {
        JTextPane messageArea = new JTextPane();
        messageArea.setEditable(false);
        messageArea.setOpaque(false);
        messageArea.setFont(new Font(uiTestAgentConfig.getDialogDefaultFontType(), Font.PLAIN,
                uiTestAgentConfig.getDialogDefaultFontSize()));
        var styledDocument = messageArea.getStyledDocument();
        SimpleAttributeSet center = new SimpleAttributeSet();
        setAlignment(center, StyleConstants.ALIGN_CENTER);
        try {
            styledDocument.insertString(0, message, null);
            styledDocument.setParagraphAttributes(0, styledDocument.getLength(), center, false);
        } catch (BadLocationException e) {
            LOG.error("Couldn't display the popup user message", e);
        }
        return messageArea;
    }

    public void displayPopup() {
        setDefaultPosition();
        setVisible(true);
        toFront();
    }

    public void setDefaultPosition() {
        setLocationRelativeTo(null);
    }

    public void setDefaultSizeAndPosition() {
        pack();
        setDefaultPosition();
    }

    protected Point preHideLocation;

    public void hideTemporarily() {
        if (!isDisplayable()) {
            return;
        }
        preHideLocation = getLocation();
        setLocation(-10000, -10000);
    }

    public void restoreDialogVisibility() {
        if (!isDisplayable()) {
            return;
        }
        if (preHideLocation != null) {
            setLocation(preHideLocation);
            preHideLocation = null;
            validate();
        }
        toFront();
        requestFocusInWindow();
        setAlwaysOnTop(false);
    }

    public void withDialogHidden(Runnable action) {
        hideTemporarily();
        try {
            action.run();
        } finally {
            restoreDialogVisibility();
        }
    }

    public static DocumentListener dirtyDocListener(Runnable onDirty) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onDirty.run(); }
            public void removeUpdate(DocumentEvent e) { onDirty.run(); }
            public void changedUpdate(DocumentEvent e) { onDirty.run(); }
        };
    }

    public static ListDataListener dirtyListListener(Runnable onDirty) {
        return new ListDataListener() {
            public void intervalAdded(ListDataEvent e) { onDirty.run(); }
            public void intervalRemoved(ListDataEvent e) { onDirty.run(); }
            public void contentsChanged(ListDataEvent e) { onDirty.run(); }
        };
    }
}
