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
package org.tarik.ta.utils;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

import static dev.langchain4j.data.message.ImageContent.DetailLevel.HIGH;
import static java.awt.Image.SCALE_AREA_AVERAGING;
import static java.awt.Image.SCALE_SMOOTH;
import static java.lang.Math.min;
import static javax.imageio.ImageIO.write;

public class ImageUtils {
    private static final String DEFAULT_IMAGE_FORMAT = "png";

    public static Image getImage(@NotNull String base64Image, @NotNull String format) {
        return Image.builder()
                .mimeType("image/" + format)
                .base64Data(base64Image)
                .build();
    }

    @NotNull
    public static BufferedImage toBufferedImage(@NotNull java.awt.Image image, int targetWidth, int targetHeight) {
        if (image instanceof BufferedImage result) {
            return result;
        } else {
            image.getHeight(null);
            BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D bGr = result.createGraphics();
            bGr.drawImage(image, 0, 0, null);
            bGr.dispose();
            return result;
        }
    }

    public static byte[] imageToByteArray(@NotNull BufferedImage image, @NotNull String formatName) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            write(image, formatName, stream);
            return stream.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Image getImage(@NotNull BufferedImage bufferedImage, @NotNull String format) {
        return getImage(convertImageToBase64(bufferedImage, format), format);
    }

    public static String convertImageToBase64(@NotNull BufferedImage image, @NotNull String format) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            write(image, format, stream);
            byte[] imageBytes = stream.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BufferedImage convertBase64ToImage(@NotNull String encodedString) {
        byte[] imageBytes = Base64.getDecoder().decode(encodedString);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
            return ImageIO.read(bis);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BufferedImage cloneImage(BufferedImage image) {
        ColorModel cm = image.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = image.copyData(image.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    public static BufferedImage padImage(BufferedImage source, int targetWidth, int targetHeight) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width >= targetWidth && height >= targetHeight) {
            return source;
        }

        int newWidth = Math.max(width, targetWidth);
        int newHeight = Math.max(height, targetHeight);
        BufferedImage paddedImage = new BufferedImage(newWidth, newHeight, source.getType());
        Graphics2D g = paddedImage.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, newWidth, newHeight);
        g.drawImage(source, 0, 0, null);
        g.dispose();

        return paddedImage;
    }

    public static java.awt.Image scaleToFitBox(@NotNull BufferedImage src, int maxW, int maxH) {
        double ratio = min((double) maxW / src.getWidth(), (double) maxH / src.getHeight());
        int w = Math.max(1, (int) (src.getWidth() * ratio));
        int h = Math.max(1, (int) (src.getHeight() * ratio));
        return src.getScaledInstance(w, h, SCALE_AREA_AVERAGING);
    }

    public static BufferedImage scaleImage(BufferedImage source, double ratio) {
        int newWidth = (int) (source.getWidth() * ratio);
        int newHeight = (int) (source.getHeight() * ratio);
        var scaledImage = source.getScaledInstance(newWidth, newHeight, SCALE_SMOOTH);
        return toBufferedImage(scaledImage, newWidth, newHeight);
    }

    public static ImageContent singleImageContent(@NotNull BufferedImage image, @NotNull ImageContent.DetailLevel detailLevel) {
        return ImageContent.from(getImage(image, DEFAULT_IMAGE_FORMAT), detailLevel);
    }
}