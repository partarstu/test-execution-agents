/*
 * knowledge-graph-core - Knowledge graph model, repositories and embeddings shared by the agents that use the knowledge graph.
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
package org.tarik.ta.utils;

import dev.langchain4j.data.image.Image;
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

import static java.awt.Image.SCALE_AREA_AVERAGING;
import static java.awt.Image.SCALE_SMOOTH;
import static java.lang.Math.min;
import static javax.imageio.ImageIO.write;

public class ImageUtils {
    private static final String IMAGE_MIME_TYPE_TEMPLATE = "image/%s";
    private static final int CHANNEL_VALUE_COUNT = 256;
    private static final double MAX_CHANNEL_VALUE = 255.0;
    private static final double SRGB_LINEAR_SEGMENT_THRESHOLD = 0.0031308;
    private static final double SRGB_LINEAR_SEGMENT_SLOPE = 12.92;
    private static final double SRGB_GAMMA_SCALE = 1.055;
    private static final double SRGB_GAMMA_EXPONENT = 1.0 / 2.4;
    private static final double SRGB_GAMMA_OFFSET = 0.055;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int ALPHA_MASK = 0xFF000000;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final double PIXELS_PER_MEGAPIXEL = 1_000_000d;
    private static final double NO_DOWNSCALING_RATIO = 1.0;
    private static final int MIN_SCALED_DIMENSION = 1;
    // Precomputed LUT: maps each linear 8-bit channel value to its sRGB gamma-encoded equivalent.
    // Needed because Robot.createMultiResolutionScreenCapture() on Windows with HDR enabled captures
    // pixels in linear light space, while BufferedImage/PNG expect sRGB (gamma-corrected) values.
    private static final int[] SRGB_GAMMA_LUT = buildSrgbGammaLut();

    private static int[] buildSrgbGammaLut() {
        int[] lut = new int[CHANNEL_VALUE_COUNT];
        for (int i = 0; i < CHANNEL_VALUE_COUNT; i++) {
            double encoded = encodeSrgb(i / MAX_CHANNEL_VALUE) * MAX_CHANNEL_VALUE;
            lut[i] = (int) Math.round(Math.clamp(encoded, 0, MAX_CHANNEL_VALUE));
        }
        return lut;
    }

    /** The sRGB transfer function (IEC 61966-2-1): a linear segment near black, a power curve above it. */
    private static double encodeSrgb(double linearValue) {
        return linearValue <= SRGB_LINEAR_SEGMENT_THRESHOLD
                ? SRGB_LINEAR_SEGMENT_SLOPE * linearValue
                : SRGB_GAMMA_SCALE * Math.pow(linearValue, SRGB_GAMMA_EXPONENT) - SRGB_GAMMA_OFFSET;
    }

    public static BufferedImage applyHdrCorrection(@NotNull BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = gammaEncode(pixels[i]);
        }
        BufferedImage result = new BufferedImage(width, height, getHdrCorrectionTargetType(image));
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }

    /** Gamma-encodes each color channel through the LUT, leaving alpha untouched. */
    private static int gammaEncode(int argb) {
        int red = SRGB_GAMMA_LUT[(argb >> RED_SHIFT) & CHANNEL_MASK];
        int green = SRGB_GAMMA_LUT[(argb >> GREEN_SHIFT) & CHANNEL_MASK];
        int blue = SRGB_GAMMA_LUT[argb & CHANNEL_MASK];
        return (argb & ALPHA_MASK) | (red << RED_SHIFT) | (green << GREEN_SHIFT) | blue;
    }

    private static int getHdrCorrectionTargetType(@NotNull BufferedImage image) {
        if (image.getType() != BufferedImage.TYPE_CUSTOM) {
            return image.getType();
        }
        return image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }

    public static Image getImage(@NotNull String base64Image, @NotNull String format) {
        return Image.builder()
                .mimeType(IMAGE_MIME_TYPE_TEMPLATE.formatted(format))
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
        int w = Math.max(MIN_SCALED_DIMENSION, (int) (src.getWidth() * ratio));
        int h = Math.max(MIN_SCALED_DIMENSION, (int) (src.getHeight() * ratio));
        return src.getScaledInstance(w, h, SCALE_AREA_AVERAGING);
    }

    /**
     * The ratio a screenshot must be downscaled by to respect both the longest-side and the total-size budget of its vision
     * model, {@code 1.0} when none is needed.
     */
    public static double getScalingRatio(@NotNull BufferedImage image, int longestAllowedDimensionPixels, double maxMegapixels) {
        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();
        int longestSide = Math.max(originalWidth, originalHeight);
        double downscaleRatio = NO_DOWNSCALING_RATIO;
        if (longestSide > longestAllowedDimensionPixels) {
            downscaleRatio = ((double) longestAllowedDimensionPixels) / longestSide;
        }

        double originalSizeMegapixels = originalWidth * originalHeight / PIXELS_PER_MEGAPIXEL;
        if (originalSizeMegapixels > maxMegapixels) {
            downscaleRatio = min(downscaleRatio, Math.sqrt(maxMegapixels / originalSizeMegapixels));
        }
        return downscaleRatio;
    }

    /** Maps a box measured on a downscaled image back into the pixel space of the original one. */
    @NotNull
    public static Rectangle getRescaledBox(@NotNull Rectangle scaledBox, double scaleFactor) {
        int rescaledX = (int) (scaledBox.x / scaleFactor);
        int rescaledY = (int) (scaledBox.y / scaleFactor);
        int rescaledWidth = (int) (scaledBox.width / scaleFactor);
        int rescaledHeight = (int) (scaledBox.height / scaleFactor);
        return new Rectangle(rescaledX, rescaledY, rescaledWidth, rescaledHeight);
    }

    public static BufferedImage scaleImage(BufferedImage source, double ratio) {
        return scaleImage(source, (int) (source.getWidth() * ratio), (int) (source.getHeight() * ratio));
    }

    @NotNull
    public static BufferedImage scaleImage(@NotNull BufferedImage source, int targetWidth, int targetHeight) {
        return toBufferedImage(source.getScaledInstance(targetWidth, targetHeight, SCALE_SMOOTH), targetWidth, targetHeight);
    }
}