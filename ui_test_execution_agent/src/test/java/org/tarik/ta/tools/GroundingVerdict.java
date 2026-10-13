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

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.tools.GroundingBaselines.AbsentTargetCase;
import org.tarik.ta.tools.GroundingBaselines.GroundingCase;
import org.tarik.ta.tools.GroundingBaselines.GroundingTarget;

import java.awt.Point;
import java.util.List;

/**
 * How one grounding answer scores against its target: a present element has to be located inside its bounds, an absent one has
 * to be rejected.
 */
enum GroundingVerdict {
    HIT,
    MISS,
    FALSE_REJECTION,
    CORRECT_REJECTION,
    HALLUCINATION,
    /** A hallucination landing on the present element the absent target was derived from. */
    SUBSTITUTION,
    INVALID;

    static final int NORMALIZED_EXTENT = 1000;

    static @NotNull GroundingVerdict of(@NotNull GroundingTarget target, @NotNull Answer answer) {
        return switch (target) {
            case GroundingCase present -> switch (answer) {
                case Located(var points) -> points.stream().anyMatch(present.expectedRegion()::contains) ? HIT : MISS;
                case Rejected _ -> FALSE_REJECTION;
                case Invalid _ -> INVALID;
            };
            case AbsentTargetCase absent -> switch (answer) {
                case Located(var points) -> points.stream().anyMatch(absent.lookalikeRegion()::contains) ? SUBSTITUTION
                        : HALLUCINATION;
                case Rejected _ -> CORRECT_REJECTION;
                case Invalid _ -> INVALID;
            };
        };
    }

    boolean isHallucination() {
        return this == HALLUCINATION || this == SUBSTITUTION;
    }

    /** What a grounding answer amounts to once read, whatever request shape produced it. */
    sealed interface Answer permits Located, Rejected, Invalid {
    }

    /** Candidate click points in the original screenshot's pixel space. */
    record Located(List<Point> points) implements Answer {
        Located {
            if (points.isEmpty()) {
                throw new IllegalArgumentException("A located answer carries at least one point; an empty one is a rejection");
            }
            points = List.copyOf(points);
        }
    }

    record Rejected() implements Answer {
    }

    record Invalid(String reason) implements Answer {
    }
}
