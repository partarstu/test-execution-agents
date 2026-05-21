/*
 * ui-test-execution-agent - ${project.description}
 * Copyright © 2025-2026 Taras Paruta (partarstu@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.tarik.ta.user_dialogs;

import javax.swing.*;
import java.awt.*;

/**
 * Enum representing the type of informational popup to display.
 * Each type carries its own visual style (border color and system icon).
 */
public enum PopupType {
    INFO(new Color(70, 130, 180), JOptionPane.INFORMATION_MESSAGE),
    WARNING(new Color(255, 165, 0), JOptionPane.WARNING_MESSAGE),
    ERROR(new Color(200, 50, 50), JOptionPane.ERROR_MESSAGE);

    private final Color borderColor;
    private final int messageType;

    PopupType(Color borderColor, int messageType) {
        this.borderColor = borderColor;
        this.messageType = messageType;
    }

    public Color getBorderColor() {
        return borderColor;
    }

    public Icon getIcon() {
        String key = switch (messageType) {
            case JOptionPane.INFORMATION_MESSAGE -> "OptionPane.informationIcon";
            case JOptionPane.WARNING_MESSAGE -> "OptionPane.warningIcon";
            case JOptionPane.ERROR_MESSAGE -> "OptionPane.errorIcon";
            default -> throw new IllegalStateException("No icon mapped for message type: " + messageType);
        };
        return UIManager.getIcon(key);
    }
}
