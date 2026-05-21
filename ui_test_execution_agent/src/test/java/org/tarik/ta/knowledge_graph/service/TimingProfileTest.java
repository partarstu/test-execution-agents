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
package org.tarik.ta.knowledge_graph.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tarik.ta.knowledge_graph.model.node.Procedure.TimingProfile;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TimingProfileTest {

    @Test
    @DisplayName("computeDelay should use floor when avg delay is low")
    void computeDelay_shouldUseFloor_whenAvgDelayIsLow() {
        TimingProfile profile = new TimingProfile(100, 200, 300, Instant.now());
        long minDelay = 500;
        long defaultDelay = 1000;

        long delayMs = TimingProfile.computeDelay(profile, minDelay, defaultDelay);

        assertThat(delayMs).isEqualTo(500); // Floor is 500, avg is 200
    }

    @Test
    @DisplayName("computeDelay should use avg when it is above floor")
    void computeDelay_shouldUseAvg_whenAboveFloor() {
        TimingProfile profile = new TimingProfile(100, 800, 1000, Instant.now());
        long minDelay = 500;
        long defaultDelay = 1000;

        long delayMs = TimingProfile.computeDelay(profile, minDelay, defaultDelay);

        assertThat(delayMs).isEqualTo(800);
    }

    @Test
    @DisplayName("computeDelay should fallback to default when profile is missing")
    void computeDelay_shouldFallback_whenProfileMissing() {
        long minDelay = 500;
        long defaultDelay = 1000;

        long delayMs = TimingProfile.computeDelay(null, minDelay, defaultDelay);

        assertThat(delayMs).isEqualTo(1000);
    }
}
