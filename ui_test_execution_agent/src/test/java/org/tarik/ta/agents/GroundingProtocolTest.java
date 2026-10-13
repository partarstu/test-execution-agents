/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
package org.tarik.ta.agents;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tarik.ta.agents.GroundingProtocol.HOLO_CLICK_POSITION;
import static org.tarik.ta.agents.GroundingProtocol.QWEN_CLICK_POINT;

class GroundingProtocolTest {
    private static final int IMAGE_WIDTH = 2000;
    private static final int IMAGE_HEIGHT = 1000;

    @Test
    void shouldCarryTheTargetTextIntoTheInstruction() {
        for (var protocol : GroundingProtocol.values()) {
            assertThat(protocol.instruction("the hour segment of the pickup time field"))
                    .as("instruction of %s", protocol)
                    .contains("the hour segment of the pickup time field");
        }
    }

    @Test
    void shouldConstrainTheAnswerWithAJsonSchema() {
        for (var protocol : GroundingProtocol.values()) {
            assertThat(protocol.responseFormat().jsonSchema()).as("schema of %s", protocol).isNotNull();
        }
    }

    @Test
    void shouldRejectAnAnswerWhichIsNotTheAgreedShape() {
        for (var protocol : GroundingProtocol.values()) {
            assertThatThrownBy(() -> protocol.toRectangles("not json at all", IMAGE_WIDTH, IMAGE_HEIGHT, true))
                    .as("rejection by %s", protocol)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not json at all");
        }
    }

    @Test
    void holoShouldCentreAPlaceholderOnTheReportedPoint() {
        var rectangles = HOLO_CLICK_POSITION.toRectangles("""
                {"x": 500, "y": 500}""", IMAGE_WIDTH, IMAGE_HEIGHT, true);

        assertThat(rectangles).containsExactly(new Rectangle(975, 475, 50, 50));
    }

    @Test
    void holoShouldTakeThePointAsPixelsWhenItIsNotNormalized() {
        var rectangles = HOLO_CLICK_POSITION.toRectangles("""
                {"x": 640, "y": 480}""", IMAGE_WIDTH, IMAGE_HEIGHT, false);

        assertThat(rectangles).containsExactly(new Rectangle(615, 455, 50, 50));
    }

    /** H Company's own answers carry the action name alongside the point, and reading it back must not choke on it. */
    @Test
    void holoShouldIgnoreTheActionNameTheModelReportsAlongsideThePoint() {
        var rectangles = HOLO_CLICK_POSITION.toRectangles("""
                {"action": "click_absolute", "x": 500, "y": 500}""", IMAGE_WIDTH, IMAGE_HEIGHT, true);

        assertThat(rectangles).containsExactly(new Rectangle(975, 475, 50, 50));
    }

    @Test
    void qwenShouldCentreAPlaceholderOnTheReportedPoint() {
        var rectangles = QWEN_CLICK_POINT.toRectangles("""
                {"point_2d": [500, 500]}""", IMAGE_WIDTH, IMAGE_HEIGHT, true);

        assertThat(rectangles).containsExactly(new Rectangle(975, 475, 50, 50));
    }

    @Test
    void qwenShouldTakeThePointAsPixelsWhenItIsNotNormalized() {
        var rectangles = QWEN_CLICK_POINT.toRectangles("""
                {"point_2d": [640, 480]}""", IMAGE_WIDTH, IMAGE_HEIGHT, false);

        assertThat(rectangles).containsExactly(new Rectangle(615, 455, 50, 50));
    }

    @Test
    void qwenShouldRejectAPointWhichIsMissingACoordinate() {
        assertThatThrownBy(() -> QWEN_CLICK_POINT.toRectangles("""
                {"point_2d": [500]}""", IMAGE_WIDTH, IMAGE_HEIGHT, true))
                .isInstanceOf(IllegalStateException.class);
    }
}
