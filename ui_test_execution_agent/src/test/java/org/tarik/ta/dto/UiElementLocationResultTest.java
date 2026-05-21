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
