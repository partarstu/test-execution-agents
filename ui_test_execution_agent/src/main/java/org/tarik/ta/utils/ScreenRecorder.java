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
package org.tarik.ta.utils;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import jakarta.inject.Inject;
import jakarta.annotation.PreDestroy;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.nio.file.Files.createDirectories;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.tarik.ta.utils.UiCommonUtils.getMouseLocation;

@UiAgentRequestScope
public class ScreenRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(ScreenRecorder.class);
    private final boolean recordingEnabled;
    private FFmpegFrameRecorder recorder;
    private ScheduledExecutorService executorService;
    private final Robot robot;
    private final Java2DFrameConverter converter;
    private String currentRecordingPath;
    private final UiTestAgentConfig config;

    @Inject
    public ScreenRecorder(UiTestAgentConfig config) {
        this.config = config;
        this.recordingEnabled = config.getScreenRecordingEnabled();
        if (recordingEnabled) {
            try {
                this.robot = new Robot();
                this.converter = new Java2DFrameConverter();
            } catch (AWTException e) {
                throw new RuntimeException("Failed to create Robot instance for video recording", e);
            }
        } else {
            this.robot = null;
            this.converter = null;
        }
    }

    public void beginScreenCapture() {
        if (!recordingEnabled) {
            return;
        }

        String folder = config.getScreenRecordingFolder();
        File videoFolder = new File(folder);
        if (!videoFolder.exists()) {
            try {
                createDirectories(Paths.get(folder));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String format = config.getRecordingFormat();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String fileName = Paths.get(folder, "test_run_" + timestamp + "." + format).toString();
        this.currentRecordingPath = fileName;

        try {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            recorder = new FFmpegFrameRecorder(fileName, screenSize.width, screenSize.height);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat(format);
            recorder.setFrameRate(config.getRecordingFrameRate());
            recorder.setVideoBitrate(config.getRecordingBitrate());
            recorder.start();

            executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.scheduleAtFixedRate(this::captureFrame, 0, 1000 / config.getRecordingFrameRate(), MILLISECONDS);
            LOG.info("Started video recording to file: {}", fileName);
        } catch (Exception e) {
            LOG.error("Failed to start video recording", e);
        }
    }

    private void captureFrame() {
        try {
            BufferedImage screenCapture = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));

            // Convert to BGR format to ensure correct channel order for FFmpeg
            BufferedImage bgrScreenCapture = new BufferedImage(screenCapture.getWidth(), screenCapture.getHeight(), TYPE_3BYTE_BGR);
            Graphics2D bgrGraphics = bgrScreenCapture.createGraphics();
            bgrGraphics.drawImage(screenCapture, 0, 0, null);
            Point mousePosition = getMouseLocation();
            bgrGraphics.setColor(new Color(255, 0, 0, 128));
            bgrGraphics.fillOval(mousePosition.x - 10, mousePosition.y - 10, 20, 20);
            bgrGraphics.dispose();
            recorder.record(converter.getFrame(bgrScreenCapture));
        } catch (Exception e) {
            LOG.error("Failed to capture frame for video recording", e);
        }
    }

    @PreDestroy
    public void endScreenCapture() {
        if (!recordingEnabled || recorder == null) {
            return;
        }

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        try {
            recorder.stop();
            recorder.release();
            converter.close();
            LOG.info("Stopped video recording.");
        } catch (Exception e) {
            LOG.error("Failed to stop video recording", e);
        }
    }

    public String getCurrentRecordingPath() {
        return currentRecordingPath;
    }
}
