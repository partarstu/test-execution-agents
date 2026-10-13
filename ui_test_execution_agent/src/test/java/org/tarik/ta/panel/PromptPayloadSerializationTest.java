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
package org.tarik.ta.panel;

import org.junit.jupiter.api.Test;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.dto.PopupType;
import org.tarik.ta.dto.ProcedureExecutionConfirmationResult;
import org.tarik.ta.dto.ProcedureExecutionConfirmationResult.Decision;
import org.tarik.ta.dto.UserSelectionResult.SelectionAction;
import org.tarik.ta.dto.panel.prompt.EditorFields;
import org.tarik.ta.dto.panel.prompt.EditorNode;
import org.tarik.ta.dto.panel.prompt.EditorNode.ChildRow;
import org.tarik.ta.dto.panel.prompt.ElementCard;
import org.tarik.ta.dto.panel.prompt.PromptPayload;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Acknowledgement;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Confirmation;
import org.tarik.ta.dto.panel.prompt.PromptPayload.FailureContextCapture;
import org.tarik.ta.dto.panel.prompt.PromptPayload.ItemView;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Message;
import org.tarik.ta.dto.panel.prompt.PromptPayload.NextAction;
import org.tarik.ta.dto.panel.prompt.PromptPayload.ProcedureEditor;
import org.tarik.ta.dto.panel.prompt.PromptPayload.StartStep;
import org.tarik.ta.dto.panel.prompt.PromptPayload.VerificationFailure;
import org.tarik.ta.dto.panel.prompt.SavedNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the JSON of every prompt payload and answer record, which the frontend's {@code Prompt.ts} union mirrors. */
class PromptPayloadSerializationTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final ItemView ITEM = new ItemView("Open the cart", List.of("user=anna"), false);

    @Test
    void everyPayload_shouldHaveItsTypeName_andNoTypeField() {
        Map<PromptPayload, String> types = Map.of(
                new StartStep(List.of()), "start-step",
                new Confirmation("Click", List.of(), ITEM, true), "confirmation",
                new NextAction("No match", "Pay", false, List.of(SelectionAction.CANCEL)), "next-action",
                new Message("Note", "Read", PopupType.INFO, null), "message",
                new VerificationFailure("Cart shown", "It is not", null), "verification-failure",
                new FailureContextCapture(ErrorCategory.TIMEOUT, "slow", List.of()), "failure-context",
                new ProcedureEditor(true, ITEM, node()), "procedure-editor");

        types.forEach((payload, type) -> {
            assertThat(PromptSpec.of(payload, Acknowledgement.class, new PromptImages()).type()).isEqualTo(type);
            assertThat(PanelJson.MAPPER.valueToTree(payload).has("type")).isFalse();
        });
    }

    @Test
    void simplePayloads_shouldSerializeTheirFieldsByName() throws Exception {
        assertThat(json(new StartStep(List.of(new StartStep.StepRow("Pay", List.of("card=visa"), null)))))
                .isEqualTo("{\"steps\":[{\"description\":\"Pay\",\"testData\":[\"card=visa\"],\"expectedResults\":null}]}");
        assertThat(json(new Confirmation("Click Pay", List.of("Checkout"), ITEM, false)))
                .isEqualTo("{\"procedureDescription\":\"Click Pay\",\"ancestors\":[\"Checkout\"],\"item\":{\"description\":\"Open the cart\","
                        + "\"testData\":[\"user=anna\"],\"precondition\":false},\"beforeExecution\":false}");
        assertThat(json(new NextAction("Halted", "Pay", true, List.of(SelectionAction.RERUN_TEST_STEP, SelectionAction.CANCEL))))
                .isEqualTo("{\"message\":\"Halted\",\"itemDescription\":\"Pay\",\"precondition\":true,"
                        + "\"actions\":[\"RERUN_TEST_STEP\",\"CANCEL\"]}");
        assertThat(json(new Message("Note", "Read", PopupType.WARNING, "img")))
                .isEqualTo("{\"title\":\"Note\",\"message\":\"Read\",\"severity\":\"WARNING\",\"screenshotId\":\"img\"}");
        assertThat(json(new VerificationFailure("Cart shown", "It is not", null)))
                .isEqualTo("{\"verification\":\"Cart shown\",\"reason\":\"It is not\",\"screenshotId\":null}");
        assertThat(json(new FailureContextCapture(ErrorCategory.TIMEOUT, "slow", List.of(ErrorCategory.UNKNOWN))))
                .isEqualTo("{\"category\":\"TIMEOUT\",\"symptom\":\"slow\",\"categories\":[\"UNKNOWN\"]}");
    }

    @Test
    void editorPayload_shouldNestTheNodeWithItsFieldsChildrenAndElement() throws Exception {
        assertThat(json(new ProcedureEditor(false, ITEM, node()))).isEqualTo("{\"create\":false,\"item\":{\"description\":\"Open the cart\","
                + "\"testData\":[\"user=anna\"],\"precondition\":false},\"node\":{\"id\":\"" + ID + "\",\"precondition\":false,"
                + "\"fields\":{\"description\":\"Open the cart\",\"additionalInfo\":null,\"atomic\":false,\"optional\":false,"
                + "\"prerequisites\":[],\"effects\":[\"Cart is open\"],\"negatedEffects\":[],\"testData\":[],\"expectedResults\":\"\"},"
                + "\"targetElement\":{\"id\":\"" + ID + "\",\"name\":\"Cart\",\"description\":\"icon\",\"locationDetails\":\"top\","
                + "\"pageSummary\":\"Shop\",\"dataDependent\":false,\"screenshotId\":null},"
                + "\"children\":[{\"id\":null,\"description\":\"Click the cart\",\"atomic\":true,\"precondition\":false,\"effects\":[],"
                + "\"negatedEffects\":[],\"screenshotId\":null}],\"parents\":[\"Buy\"],\"testCases\":[\"Checkout\"]}}");
    }

    @Test
    void answers_shouldBeReadFromTheJsonTheBrowserSends() throws Exception {
        assertThat(read("{\"stepIndex\":-1}", StartStep.Answer.class).stepIndex()).isEqualTo(StartStep.Answer.ABORT);
        assertThat(read("{\"decision\":\"HALTED\"}", ProcedureExecutionConfirmationResult.class).decision()).isEqualTo(Decision.HALTED);
        assertThat(read("{\"action\":\"BROWSE\",\"procedureId\":\"" + ID + "\"}", NextAction.Answer.class))
                .isEqualTo(new NextAction.Answer(SelectionAction.BROWSE, ID));
        assertThat(read("{}", Acknowledgement.class)).isEqualTo(new Acknowledgement());
        assertThat(read("{\"choice\":\"DISMISS\"}", FailureContextCapture.Answer.class))
                .isEqualTo(new FailureContextCapture.Answer(FailureContextCapture.Choice.DISMISS, null, null, null));
        var editorAnswer = read("{\"outcome\":\"SAVE\",\"node\":{\"id\":null,\"precondition\":true,\"fields\":{\"description\":\"Shop\","
                + "\"additionalInfo\":null,\"atomic\":true,\"optional\":false,\"prerequisites\":[],\"effects\":[],\"negatedEffects\":[],"
                + "\"testData\":[],\"expectedResults\":\"\"},\"targetElementId\":null,\"childIds\":[]},\"procedureId\":null}",
                ProcedureEditor.Answer.class);
        assertThat(editorAnswer.outcome()).isEqualTo(ProcedureEditor.Outcome.SAVE);
        assertThat(editorAnswer.node()).isEqualTo(new SavedNode(null, true, new EditorFields("Shop", null, true, false, List.of(),
                List.of(), List.of(), List.of(), ""), null, List.of()));
    }

    private static EditorNode node() {
        var fields = new EditorFields("Open the cart", null, false, false, List.of(), List.of("Cart is open"), List.of(), List.of(), "");
        return new EditorNode(ID, false, fields, new ElementCard(ID, "Cart", "icon", "top", "Shop", false, null),
                List.of(new ChildRow(null, "Click the cart", true, false, List.of(), List.of(), null)), List.of("Buy"),
                List.of("Checkout"));
    }

    private static String json(Object value) throws Exception {
        return PanelJson.MAPPER.writeValueAsString(value);
    }

    private static <T> T read(String json, Class<T> type) throws Exception {
        return PanelJson.MAPPER.readValue(json, type);
    }
}
