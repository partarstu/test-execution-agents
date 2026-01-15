/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.utils;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment;
import static java.util.Comparator.comparingInt;
import static org.tarik.ta.utils.ImageUtils.toBufferedImage;

public class UiCommonUtils {
    private static Robot robot;

    public static Object getStaticFieldValue(Field field) {
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Color getColorByName(@NotNull String colorName) {
        try {
            Field colorField = Color.class.getField(colorName.toLowerCase());
            var value = getStaticFieldValue(colorField);
            checkArgument(value instanceof Color, "No suitable instance of %s found in JDK for the value of %s"
                    .formatted(Color.class.getName(), colorName));
            return (Color) value;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No suitable instance of %s found in JDK for the value of %s"
                    .formatted(Color.class.getName(), colorName));
        }
    }

    public static String getColorName(@NotNull Color color) {
        return Arrays.stream(Color.class.getFields())
                .filter(field -> field.getType() == Color.class)
                .filter(field -> color.equals(getStaticFieldValue(field)))
                .findFirst()
                .map(Field::getName)
                .orElseThrow(() -> new IllegalStateException("No suitable color name found in JDK for the value of " + color));
    }

    public static BufferedImage captureScreen(boolean withHighestResolution) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        return captureScreenPart(new Rectangle(screenSize), withHighestResolution);
    }

    public static Dimension getScreenSize() {
        return Toolkit.getDefaultToolkit().getScreenSize();
    }

    public static BufferedImage captureScreen() {
        return captureScreen(true);
    }

    public static BufferedImage captureScreenPart(@NotNull Rectangle target, boolean withHighestResolution) {
        var screenShots = getRobot().createMultiResolutionScreenCapture(target);
        Comparator<BufferedImage> comparator = comparingInt(BufferedImage::getHeight);
        if (withHighestResolution) {
            comparator = comparator.reversed();
        }

        return screenShots.getResolutionVariants().stream()
                .map(i -> toBufferedImage(i, target.width, target.height))
                .min(comparator)
                .orElseThrow();
    }

    public static synchronized Robot getRobot() {
        if (robot == null) {
            try {
                robot = new Robot();
            } catch (AWTException e) {
                throw new RuntimeException(e);
            }
        }
        return robot;
    }

    public static Point getMouseLocation() {
        return MouseInfo.getPointerInfo().getLocation();
    }

    public static Point getScaledScreenLocationCoordinates(@NotNull Point physicalScreenCoordinates) {
        var graphicsConfiguration = getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        AffineTransform tx = graphicsConfiguration.getDefaultTransform();
        double uiScaleX = tx.getScaleX();
        double uiScaleY = tx.getScaleY();
        if (uiScaleX == 1 && uiScaleY == 1) {
            return physicalScreenCoordinates;
        } else {
            return new Point((int) (physicalScreenCoordinates.getX() / uiScaleX), (int) (physicalScreenCoordinates.getY() / uiScaleY));
        }
    }

    public static Rectangle getScaledBoundingBox(@NotNull Rectangle originalBoundingBox) {
        var graphicsConfiguration = getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        AffineTransform tx = graphicsConfiguration.getDefaultTransform();
        double uiScaleX = tx.getScaleX();
        double uiScaleY = tx.getScaleY();
        if (uiScaleX == 1 && uiScaleY == 1) {
            return originalBoundingBox;
        } else {
            var scaledX = originalBoundingBox.getX() / uiScaleX;
            var scaledY = originalBoundingBox.getY() / uiScaleY;
            var scaledWidth = originalBoundingBox.getWidth() / uiScaleX;
            var scaledHeight = originalBoundingBox.getHeight() / uiScaleY;
            return new Rectangle((int) scaledX, (int) scaledY, (int) scaledWidth, (int) scaledHeight);
        }
    }

    public static Rectangle getPhysicalBoundingBox(@NotNull Rectangle logicalBoundingBox) {
        var graphicsConfiguration = getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        AffineTransform tx = graphicsConfiguration.getDefaultTransform();
        double uiScaleX = tx.getScaleX();
        double uiScaleY = tx.getScaleY();
        if (uiScaleX == 1 && uiScaleY == 1) {
            return logicalBoundingBox;
        } else {
            var physicalX = logicalBoundingBox.getX() * uiScaleX;
            var physicalY = logicalBoundingBox.getY() * uiScaleY;
            var physicalWidth = logicalBoundingBox.getWidth() * uiScaleX;
            var physicalHeight = logicalBoundingBox.getHeight() * uiScaleY;
            return new Rectangle((int) physicalX, (int) physicalY, (int) physicalWidth, (int) physicalHeight);
        }
    }

    public static Point getPhysicalScreenLocationCoordinates(@NotNull Point scaledScreenCoordinates) {
        var gc = getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        AffineTransform tx = gc.getDefaultTransform();
        double uiScaleX = tx.getScaleX();
        double uiScaleY = tx.getScaleY();
        return new Point((int) (scaledScreenCoordinates.getX() * uiScaleX), (int) (scaledScreenCoordinates.getY() * uiScaleY));
    }

    @NotNull
    public static Rectangle getCommonArea(List<Rectangle> initialCandidates) {
        if (initialCandidates.isEmpty()) {
            return new Rectangle();
        } else {
            int minX = initialCandidates.stream().mapToInt(r -> r.x).min().getAsInt();
            int minY = initialCandidates.stream().mapToInt(r -> r.y).min().getAsInt();
            int maxX = initialCandidates.stream().mapToInt(r -> r.x + r.width).max().getAsInt();
            int maxY = initialCandidates.stream().mapToInt(r -> r.y + r.height).max().getAsInt();
            return new Rectangle(minX, minY, maxX - minX, maxY - minY);
        }
    }
}



