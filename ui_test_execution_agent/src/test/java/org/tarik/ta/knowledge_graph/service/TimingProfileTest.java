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
