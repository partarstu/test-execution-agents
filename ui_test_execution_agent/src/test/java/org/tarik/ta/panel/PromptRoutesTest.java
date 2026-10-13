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

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.core.security.SecretRegistry;
import org.tarik.ta.dto.PopupType;
import org.tarik.ta.dto.RerunOptions;
import org.tarik.ta.dto.UserSelectionResult.SelectionAction;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Acknowledgement;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Message;
import org.tarik.ta.dto.panel.prompt.PromptPayload.StartStep;
import org.tarik.ta.dto.panel.prompt.PromptView;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;
import org.tarik.ta.knowledge_graph.service.KnowledgeService.ScoredProcedure;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptRoutesTest {
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final PromptSpec<StartStep.Answer> START_STEP = PromptSpec.of(
            new StartStep(List.of(new StartStep.StepRow("Open the cart", List.of(), "Cart shown"),
                    new StartStep.StepRow("Pay", List.of(), "Paid"))),
            StartStep.Answer.class, new PromptImages()).withCheck(answer -> PromptInput.require(answer.stepIndex() < 2, "No such step"));

    private final ExecutorService executor = newVirtualThreadPerTaskExecutor();
    private PanelTestFixture panel;
    private String cookie;

    @BeforeEach
    void setUp() {
        SecretRegistry.global().reset(Pattern.compile("(?i).*password.*"));
        panel = PanelTestFixture.builder().countdownSeconds(30).start();
        cookie = login();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        panel.close();
        SecretRegistry.global().reset(Pattern.compile("(?!)"));
    }

    @Test
    void answer_shouldCompleteThePrompt_andRefuseASecondAnswerWith409() throws Exception {
        var asked = askAsync(START_STEP);
        var prompt = awaitOpenPrompt();

        assertThat(post(answerPath(prompt), "{\"stepIndex\":1}").statusCode()).isEqualTo(204);

        assertThat(asked.get(WAIT.toSeconds(), SECONDS).stepIndex()).isEqualTo(1);
        var second = post(answerPath(prompt), "{\"stepIndex\":0}");
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.jsonPath().getString("error")).isEqualTo("This prompt is no longer open");
    }

    @Test
    void answer_shouldBeRejectedWith400_andLeaveThePromptOpen_whenItFailsTheCheckOrIsMalformed() throws Exception {
        var asked = askAsync(START_STEP);
        var prompt = awaitOpenPrompt();

        var invalid = post(answerPath(prompt), "{\"stepIndex\":5}");
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.jsonPath().getString("error")).isEqualTo("No such step");
        assertThat(post(answerPath(prompt), "{\"stepIndex\":\"first\"}").statusCode()).isEqualTo(400);
        assertThat(post(answerPath(prompt), "null").statusCode()).isEqualTo(400);
        assertThat(asked.isDone()).isFalse();

        assertThat(post(answerPath(prompt), "{\"stepIndex\":0}").statusCode()).isEqualTo(204);
        assertThat(asked.get(WAIT.toSeconds(), SECONDS).stepIndex()).isZero();
    }

    @Test
    void answer_shouldBeAnsweredWith409_whenTheDefaultAppliedFirst() throws Exception {
        var asked = askAsync(PromptSpec.of(new Message("Note", "Read me", PopupType.INFO, null), Acknowledgement.class,
                new PromptImages()).withDefault(new Acknowledgement()).withCountdown(Duration.ofMillis(50)));
        var prompt = awaitOpenPrompt();
        asked.get(WAIT.toSeconds(), SECONDS);

        assertThat(post(answerPath(prompt), "{}").statusCode()).isEqualTo(409);
    }

    @Test
    void promptRoutes_shouldRequireASession() {
        askAsync(START_STEP);
        var prompt = awaitOpenPrompt();

        assertThat(given().baseUri(panel.baseUrl()).header("Sec-Fetch-Site", "same-origin").contentType(ContentType.JSON)
                .body("{\"stepIndex\":0}").post(answerPath(prompt)).statusCode()).isEqualTo(401);
        assertThat(given().baseUri(panel.baseUrl()).get("/api/prompts/%s/images/x".formatted(prompt.id())).statusCode()).isEqualTo(401);
        post(answerPath(prompt), "{\"stepIndex\":0}");
    }

    @Test
    void hold_shouldHoldACountdown_andRefuseAPromptWithoutOne() throws Exception {
        var asked = askAsync(PromptSpec.of(new Message("Note", "Read me", PopupType.INFO, null), Acknowledgement.class,
                new PromptImages()).withDefault(new Acknowledgement()).withCountdown(Duration.ofSeconds(30)));
        var prompt = awaitOpenPrompt();

        assertThat(post(promptPath(prompt, "hold"), "").statusCode()).isEqualTo(204);
        assertThat(panel.broadcaster().snapshot().prompt().held()).isTrue();
        assertThat(post(promptPath(prompt, "hold"), "").statusCode()).isEqualTo(409);
        post(answerPath(prompt), "{}");
        asked.get(WAIT.toSeconds(), SECONDS);

        askAsync(START_STEP);
        var withoutCountdown = awaitOpenPrompt();
        assertThat(post(promptPath(withoutCountdown, "hold"), "").statusCode()).isEqualTo(409);
        post(answerPath(withoutCountdown), "{\"stepIndex\":0}");
    }

    @Test
    void pauseAndResume_shouldFreezeAndRestartACountdown_andRefuseAPromptWithoutOne() throws Exception {
        var asked = askAsync(PromptSpec.of(new Message("Note", "Read me", PopupType.INFO, null), Acknowledgement.class,
                new PromptImages()).withDefault(new Acknowledgement()).withCountdown(Duration.ofSeconds(30)));
        var prompt = awaitOpenPrompt();

        assertThat(post(promptPath(prompt, "resume"), "").statusCode()).isEqualTo(409);
        assertThat(post(promptPath(prompt, "pause"), "").statusCode()).isEqualTo(204);
        assertThat(panel.broadcaster().snapshot().prompt().paused()).isTrue();
        assertThat(post(promptPath(prompt, "pause"), "").statusCode()).isEqualTo(409);
        assertThat(post(promptPath(prompt, "resume"), "").statusCode()).isEqualTo(204);
        assertThat(panel.broadcaster().snapshot().prompt().paused()).isFalse();
        post(answerPath(prompt), "{}");
        asked.get(WAIT.toSeconds(), SECONDS);

        askAsync(START_STEP);
        var withoutCountdown = awaitOpenPrompt();
        assertThat(post(promptPath(withoutCountdown, "pause"), "").statusCode()).isEqualTo(409);
        post(answerPath(withoutCountdown), "{\"stepIndex\":0}");
    }

    @Test
    void action_shouldAnswerTheRedactedResult_404ForAnUnknownAction_400ForInvalidInput_and500ForAFailure() {
        SecretRegistry.global().register("password", "s3cr3t-value");
        var actions = PromptActions.builder()
                .add("echo", StartStep.Answer.class, request -> new Message("Echo", "step %d with s3cr3t-value".formatted(
                        request.stepIndex()), PopupType.INFO, null))
                .add("reject", Acknowledgement.class, _ -> {
                    throw new PromptInputException("Pick a step first");
                })
                .add("fail", Acknowledgement.class, _ -> {
                    throw new IllegalStateException("database password leaked in here");
                })
                .build();
        askAsync(START_STEP.withActions(actions));
        var prompt = awaitOpenPrompt();

        var echo = post(actionPath(prompt, "echo"), "{\"stepIndex\":3}");
        assertThat(echo.statusCode()).isEqualTo(200);
        assertThat(echo.jsonPath().getString("message")).isEqualTo("step 3 with {{secret:password}}");
        assertThat(post(actionPath(prompt, "missing"), "{}").statusCode()).isEqualTo(404);
        var rejected = post(actionPath(prompt, "reject"), "{}");
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(rejected.jsonPath().getString("error")).isEqualTo("Pick a step first");
        assertThat(post(actionPath(prompt, "echo"), "[1]").statusCode()).isEqualTo(400);
        var failed = post(actionPath(prompt, "fail"), "{}");
        assertThat(failed.statusCode()).isEqualTo(500);
        assertThat(failed.asString()).doesNotContain("database");
        post(answerPath(prompt), "{\"stepIndex\":0}");
    }

    @Test
    void action_shouldBeAnsweredWith409_afterThePromptClosed() throws Exception {
        var actions = PromptActions.builder().add("echo", Acknowledgement.class, _ -> new Acknowledgement()).build();
        var asked = askAsync(START_STEP.withActions(actions));
        var prompt = awaitOpenPrompt();
        post(answerPath(prompt), "{\"stepIndex\":0}");
        asked.get(WAIT.toSeconds(), SECONDS);

        assertThat(post(actionPath(prompt, "echo"), "{}").statusCode()).isEqualTo(409);
    }

    @Test
    void image_shouldServeAnImageOfTheOpenPrompt_and404Otherwise() {
        var images = new PromptImages();
        var screenshotId = images.addScreen(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB));
        askAsync(PromptSpec.of(new Message("Shot", "See the screen", PopupType.ERROR, screenshotId), Acknowledgement.class, images));
        var prompt = awaitOpenPrompt();

        var image = request().get(promptPath(prompt, "images/" + screenshotId));
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(image.asByteArray()).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(request().get(promptPath(prompt, "images/unknown")).statusCode()).isEqualTo(404);
        post(answerPath(prompt), "{}");
    }

    @Test
    void chooseNextAction_shouldAcceptOnlyAnOfferedActionAndAProcedureTheSearchShowed() throws Exception {
        var procedure = Procedure.createAtomic("Open the cart", List.of(), "", List.of(), List.of(), false);
        var knowledgeService = mock(KnowledgeService.class);
        when(knowledgeService.findTopRankedWithScores(eq("cart"), any(), any())).thenReturn(List.of(new ScoredProcedure(procedure, 1, 2)));
        var chosen = executor.submit(() -> panel.operatorPrompts().chooseNextAction("The step failed", "Open the cart",
                knowledgeService, new RerunOptions(false, false, RerunOptions.Halt.NONE)));
        var prompt = awaitOpenPrompt();

        assertThat(post(answerPath(prompt), "{\"action\":\"RERUN_ATOMIC_PROCEDURE\"}").statusCode()).isEqualTo(400);
        var browseUnseen = "{\"action\":\"BROWSE\",\"procedureId\":\"%s\"}".formatted(procedure.id());
        assertThat(post(answerPath(prompt), browseUnseen).statusCode()).isEqualTo(400);
        var hits = post(actionPath(prompt, "search-procedures"), "{\"query\":\"cart\",\"showAllTypes\":false}");
        assertThat(hits.jsonPath().getList("hits.description")).containsExactly("Open the cart");
        assertThat(hits.jsonPath().getInt("hits[0].satisfiedPrerequisites")).isEqualTo(1);
        assertThat(post(answerPath(prompt), browseUnseen).statusCode()).isEqualTo(204);

        var selection = chosen.get(WAIT.toSeconds(), SECONDS).orElseThrow();
        assertThat(selection.action()).isEqualTo(SelectionAction.BROWSE);
        assertThat(selection.selectedProcedure()).isEqualTo(procedure);
        assertThat(prompt.payload().get("actions").toString()).isEqualTo("[\"RERUN_TEST_STEP\",\"CREATE\",\"BROWSE\",\"CANCEL\"]");
    }

    @Test
    void chooseProcedure_shouldReturnNothing_whenTheOperatorTerminates() throws Exception {
        var chosen = executor.submit(() -> panel.operatorPrompts().chooseProcedure("No match", "Pay", mock(KnowledgeService.class),
                Set.of(), Set.of(), false));
        var prompt = awaitOpenPrompt();

        assertThat(post(answerPath(prompt), "{\"action\":\"CANCEL\"}").statusCode()).isEqualTo(204);

        assertThat(chosen.get(WAIT.toSeconds(), SECONDS)).isEmpty();
    }

    @Test
    void captureFailureContext_shouldReturnTheSubmittedContext_withAPlaceholderForAnEmptySymptom() throws Exception {
        var captured = executor.submit(() -> panel.operatorPrompts().captureFailureContext(ErrorCategory.TIMEOUT, "Save not clickable"));
        var prompt = awaitOpenPrompt();
        assertThat(prompt.countdown()).isTrue();

        assertThat(post(answerPath(prompt), "{\"choice\":\"SUBMIT\"}").statusCode()).isEqualTo(400);
        assertThat(post(answerPath(prompt), "{\"choice\":\"SUBMIT\",\"category\":\"TIMEOUT\",\"symptom\":\" \",\"resolution\":\"wait\"}")
                .statusCode()).isEqualTo(204);

        Optional<FailureContext> context = captured.get(WAIT.toSeconds(), SECONDS);
        assertThat(context).hasValueSatisfying(failureContext -> {
            assertThat(failureContext.symptom()).isEqualTo("Unknown failure");
            assertThat(failureContext.resolution()).isEqualTo("wait");
            assertThat(failureContext.mode()).isEqualTo(FailureContext.Mode.SUPERVISED);
        });
    }

    private <A> Future<A> askAsync(PromptSpec<A> spec) {
        return executor.submit(() -> panel.operatorPanel().ask(spec));
    }

    private PromptView awaitOpenPrompt() {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            var prompt = panel.broadcaster().snapshot().prompt();
            if (prompt != null) {
                return prompt;
            }
            LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
        }
        throw new AssertionError(new TimeoutException("No prompt opened within %s".formatted(WAIT)));
    }

    private static String answerPath(PromptView prompt) {
        return promptPath(prompt, "answer");
    }

    private static String actionPath(PromptView prompt, String action) {
        return promptPath(prompt, "actions/" + action);
    }

    private static String promptPath(PromptView prompt, String suffix) {
        return "/api/prompts/%s/%s".formatted(prompt.id(), suffix);
    }

    private Response post(String path, String body) {
        return request().header("Sec-Fetch-Site", "same-origin").contentType(ContentType.JSON).body(body).post(path);
    }

    private RequestSpecification request() {
        return given().baseUri(panel.baseUrl()).header("Cookie", cookie);
    }

    private String login() {
        var response = given().baseUri(panel.baseUrl()).header("Sec-Fetch-Site", "same-origin").contentType(ContentType.JSON)
                .body("{\"password\":\"%s\"}".formatted(PanelTestFixture.PASSWORD)).post("/api/login");
        assertThat(response.statusCode()).isEqualTo(200);
        return "panel_session=" + response.getCookie("panel_session");
    }
}
