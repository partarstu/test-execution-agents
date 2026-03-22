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
package org.tarik.ta.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UiElementLocationResultTest {

    @Test
    void empty_returnsNonNullRecordWithSuccessFalse() {
        var result = UiElementLocationResult.empty();

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.elementId()).isNull();
        assertThat(result.elementName()).isNull();
        assertThat(result.elementScreenRegion()).isNull();
        assertThat(result.message()).isNull();
    }

    @Test
    void endExecutionAndGetFinalResult_storesExactFieldValues() {
        var uuid = UUID.randomUUID();
        var region = new ScreenRegion(20, 10, 180, 90);

        var result = UiElementLocationResult
                .endExecutionAndGetFinalResult(new UiElementLocationResult(true, uuid.toString(), "Submit Button",
                        region, "Success"));

        assertThat(result.success()).isTrue();
        assertThat(result.elementId()).isEqualTo(uuid.toString());
        assertThat(result.elementName()).isEqualTo("Submit Button");
        assertThat(result.elementScreenRegion()).isEqualTo(region);
        assertThat(result.message()).isEqualTo("Success");
    }

    @Test
    void endExecutionAndGetFinalResult_withNullFieldsOnFailure() {
        var result = UiElementLocationResult.endExecutionAndGetFinalResult(
                new UiElementLocationResult(false, null, null, null, "Failed to locate"));

        assertThat(result.success()).isFalse();
        assertThat(result.elementId()).isNull();
        assertThat(result.elementName()).isNull();
        assertThat(result.elementScreenRegion()).isNull();
        assertThat(result.message()).isEqualTo("Failed to locate");
    }

    @Test
    void endExecutionAndGetFinalResult_withNullScreenRegionForManuallyCreatedElement() {
        var uuid = UUID.randomUUID();

        var result = UiElementLocationResult
                .endExecutionAndGetFinalResult(new UiElementLocationResult(true, uuid.toString(), "Username Input",
                        null, null));

        assertThat(result.success()).isTrue();
        assertThat(result.elementId()).isEqualTo(uuid.toString());
        assertThat(result.elementName()).isEqualTo("Username Input");
        assertThat(result.elementScreenRegion()).isNull();
        assertThat(result.message()).isNull();
    }
}
