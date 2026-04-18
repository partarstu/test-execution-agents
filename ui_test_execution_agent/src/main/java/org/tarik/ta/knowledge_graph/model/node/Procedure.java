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
package org.tarik.ta.knowledge_graph.model.node;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Domain entity representing a procedure stored as a Neo4j graph node.
 * Procedures can be either composite (containing child procedures via CONTAINS relationships)
 * or atomic (leaf-level actions that target a specific UI element via TARGETS relationships).
 *
 * <p>Prerequisite and effect phrases are stored as plain strings. Their semantic embeddings
 * are held in separate {@code PhraseEmbedding} Neo4j nodes linked via
 * {@code HAS_PREREQUISITE} and {@code HAS_EFFECT} relationships.</p>
 *
 * <p>All fields are stored as flat Neo4j node properties (no JSON blob).
 * TimingProfile is always populated from the same node read — no lazy DB call needed.</p>
 */
public record Procedure(
        UUID id,
        String description,
        List<String> testData,
        String expectedResults,
        boolean isAtomic,
        boolean isPrecondition,
        boolean optional,
        List<String> prerequisites,
        List<String> effects,
        @Nullable String additionalInfo,
        Instant createdAt,
        Instant updatedAt,
        float @Nullable [] embedding,
        @Nullable TimingProfile timingProfile
) implements Embeddable {

    public static final String LABEL = "Procedure";
    public static final String PROP_DESCRIPTION = "description";
    public static final String PROP_IS_ATOMIC = "isAtomic";
    public static final String PROP_TEST_DATA = "testData";
    public static final String PROP_EXPECTED_RESULTS = "expectedResults";
    public static final String PROP_IS_PRECONDITION = "isPrecondition";
    public static final String PROP_OPTIONAL = "optional";
    public static final String PROP_PREREQUISITES = "prerequisites";
    public static final String PROP_EFFECTS = "effects";
    public static final String PROP_ADDITIONAL_INFO = "additionalInfo";
    public static final String PROP_CREATED_AT = "createdAt";
    public static final String PROP_UPDATED_AT = "updatedAt";
    public static final String PROP_AVG_EXECUTION_MS = "avgExecutionMs";
    public static final String PROP_AVG_VERIFICATION_DELAY_MS = "avgVerificationDelayMs";
    public static final String PROP_MAX_VERIFICATION_DELAY_MS = "maxVerificationDelayMs";
    public static final String PROP_LAST_TIMING_UPDATE = "lastTimingUpdate";

    public Procedure {
        requireNonNull(id, "id");
        requireNonNull(description, "description");
        testData = List.copyOf(requireNonNull(testData, "testData"));
        requireNonNull(expectedResults, "expectedResults");
        prerequisites = List.copyOf(requireNonNull(prerequisites, "prerequisites"));
        effects = List.copyOf(requireNonNull(effects, "effects"));
        requireNonNull(createdAt, "createdAt");
        requireNonNull(updatedAt, "updatedAt");
        embedding = embedding != null ? embedding.clone() : null;
    }

    /**
     * Creates a composite procedure that contains child procedures.
     */
    public static Procedure createComposite(@NotNull String description,
                                            @NotNull List<String> testData,
                                            @NotNull String expectedResults,
                                            @NotNull List<String> prerequisites,
                                            @NotNull List<String> effects,
                                            boolean isPrecondition) {
        var now = Instant.now();
        return new Procedure(UUID.randomUUID(), description, testData, expectedResults,
                false, isPrecondition, false, prerequisites, effects, null, now, now, null, null);
    }

    /**
     * Creates an atomic procedure representing a leaf-level action.
     */
    public static Procedure createAtomic(@NotNull String description,
                                         @NotNull List<String> testData,
                                         @NotNull String expectedResults,
                                         @NotNull List<String> prerequisites,
                                         @NotNull List<String> effects,
                                         boolean isPrecondition) {
        var now = Instant.now();
        return new Procedure(UUID.randomUUID(), description, testData, expectedResults,
                true, isPrecondition, false, prerequisites, effects, null, now, now, null, null);
    }

    @Override
    public float @Nullable [] embedding() {
        return embedding != null ? embedding.clone() : null;
    }

    @Override
    public @Nullable TimingProfile timingProfile() {
        return timingProfile;
    }

    /** Returns a copy with the given embedding vector populated. */
    public Procedure withEmbedding(float @NotNull [] newEmbedding) {
        return new Procedure(id, description, testData, expectedResults, isAtomic, isPrecondition, optional,
                prerequisites, effects, additionalInfo, createdAt, updatedAt, requireNonNull(newEmbedding, "embedding"), timingProfile);
    }

    /** Returns a copy with the given timing profile populated. */
    public Procedure withTiming(@NotNull TimingProfile newTiming) {
        return new Procedure(id, description, testData, expectedResults, isAtomic, isPrecondition, optional,
                prerequisites, effects, additionalInfo, createdAt, updatedAt, embedding, requireNonNull(newTiming, "timing"));
    }

    /** Returns a copy with the given optional flag. */
    public Procedure withOptional(boolean newOptional) {
        return new Procedure(id, description, testData, expectedResults, isAtomic, isPrecondition, newOptional,
                prerequisites, effects, additionalInfo, createdAt, updatedAt, embedding, timingProfile);
    }

    /** Returns a copy with the given additional info. */
    public Procedure withAdditionalInfo(@Nullable String newAdditionalInfo) {
        return new Procedure(id, description, testData, expectedResults, isAtomic, isPrecondition, optional,
                prerequisites, effects, newAdditionalInfo, createdAt, updatedAt, embedding, timingProfile);
    }

    /**
     * Returns a copy of this procedure with a different {@code id} and {@code createdAt}, refreshing {@code updatedAt}.
     * Used when updating an existing procedure node to preserve its original identity and creation timestamp.
     */
    public Procedure withId(UUID newId, Instant originalCreatedAt) {
        return new Procedure(newId, description, testData, expectedResults, isAtomic, isPrecondition, optional,
                prerequisites, effects, additionalInfo, originalCreatedAt, Instant.now(), embedding, timingProfile);
    }

    public record TimingProfile(long avgExecutionMs, long avgVerificationDelayMs, long maxVerificationDelayMs,
                                Instant lastTimingUpdate) {
        public static long computeDelay(@Nullable TimingProfile profile, long minDelay, long defaultDelay) {
            return profile != null ? Math.max(minDelay, profile.avgVerificationDelayMs()) : defaultDelay;
        }
    }

    @Override
    public @NonNull String toString() {
        return new StringJoiner(", ", Procedure.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("isAtomic=" + isAtomic)
                .add("isPrecondition=" + isPrecondition)
                .add("optional=" + optional)
                .add("prerequisites=" + prerequisites)
                .add("effects=" + effects)
                .add("testData=" + testData)
                .add("expectedResults='%s'".formatted(this.expectedResults))
                .add("additionalInfo='%s'".formatted(additionalInfo))
                .add("description='%s'".formatted(description))
                .toString();
    }
}
