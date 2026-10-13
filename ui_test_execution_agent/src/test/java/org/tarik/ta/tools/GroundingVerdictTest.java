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
package org.tarik.ta.tools;

import org.junit.jupiter.api.Test;
import org.tarik.ta.tools.GroundingBaselines.AbsentTargetCase;
import org.tarik.ta.tools.GroundingBaselines.GroundingCase;
import org.tarik.ta.tools.GroundingVerdict.Invalid;
import org.tarik.ta.tools.GroundingVerdict.Located;
import org.tarik.ta.tools.GroundingVerdict.Rejected;

import java.awt.Point;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tarik.ta.tools.GroundingBaselines.ABSENT_CASES;
import static org.tarik.ta.tools.GroundingBaselines.CASES;
import static org.tarik.ta.tools.GroundingBaselines.centerOf;
import static org.tarik.ta.tools.GroundingVerdict.CORRECT_REJECTION;
import static org.tarik.ta.tools.GroundingVerdict.FALSE_REJECTION;
import static org.tarik.ta.tools.GroundingVerdict.HALLUCINATION;
import static org.tarik.ta.tools.GroundingVerdict.HIT;
import static org.tarik.ta.tools.GroundingVerdict.INVALID;
import static org.tarik.ta.tools.GroundingVerdict.MISS;
import static org.tarik.ta.tools.GroundingVerdict.SUBSTITUTION;

class GroundingVerdictTest {
    private static final GroundingCase PRESENT = CASES.getFirst();
    private static final AbsentTargetCase ABSENT = ABSENT_CASES.getFirst();
    private static final Point FAR_AWAY = new Point(5, 5);

    @Test
    void of_shouldScoreHit_whenAnyPointFallsInsideTheExpectedRegion() {
        var answer = new Located(List.of(FAR_AWAY, centerOf(PRESENT.expectedRegion())));

        assertThat(GroundingVerdict.of(PRESENT, answer)).isEqualTo(HIT);
    }

    @Test
    void of_shouldScoreMiss_whenNoPointFallsInsideTheExpectedRegion() {
        assertThat(GroundingVerdict.of(PRESENT, new Located(List.of(FAR_AWAY)))).isEqualTo(MISS);
    }

    @Test
    void of_shouldScoreFalseRejection_whenAPresentTargetIsRejected() {
        assertThat(GroundingVerdict.of(PRESENT, new Rejected())).isEqualTo(FALSE_REJECTION);
    }

    @Test
    void of_shouldScoreCorrectRejection_whenAnAbsentTargetIsRejected() {
        assertThat(GroundingVerdict.of(ABSENT, new Rejected())).isEqualTo(CORRECT_REJECTION);
    }

    @Test
    void of_shouldScoreSubstitution_whenAnAbsentTargetIsLocatedOnItsLookalike() {
        var answer = new Located(List.of(FAR_AWAY, centerOf(ABSENT.lookalikeRegion())));

        assertThat(GroundingVerdict.of(ABSENT, answer)).isEqualTo(SUBSTITUTION);
    }

    @Test
    void of_shouldScoreHallucination_whenAnAbsentTargetIsLocatedElsewhere() {
        assertThat(GroundingVerdict.of(ABSENT, new Located(List.of(FAR_AWAY)))).isEqualTo(HALLUCINATION);
    }

    @Test
    void of_shouldScoreInvalid_whenTheAnswerIsInvalid() {
        var answer = new Invalid("not JSON");

        assertThat(GroundingVerdict.of(PRESENT, answer)).isEqualTo(INVALID);
        assertThat(GroundingVerdict.of(ABSENT, answer)).isEqualTo(INVALID);
    }

    @Test
    void isHallucination_shouldHold_whenAnAbsentTargetWasLocated() {
        assertThat(HALLUCINATION.isHallucination()).isTrue();
        assertThat(SUBSTITUTION.isHallucination()).isTrue();
    }

    @Test
    void isHallucination_shouldNotHold_whenNothingWasLocatedForAnAbsentTarget() {
        assertThat(List.of(HIT, MISS, FALSE_REJECTION, CORRECT_REJECTION, INVALID))
                .noneMatch(GroundingVerdict::isHallucination);
    }

    @Test
    void located_shouldRejectAnEmptyPointList() {
        assertThatThrownBy(() -> new Located(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
