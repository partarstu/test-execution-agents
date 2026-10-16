/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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
package org.tarik.ta.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.dto.ProcedureNode;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository.TargetedUiElement;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tarik.ta.knowledge.ProcedureFixtures.atomic;
import static org.tarik.ta.knowledge.ProcedureFixtures.child;
import static org.tarik.ta.knowledge.ProcedureFixtures.composite;
import static org.tarik.ta.knowledge.ProcedureFixtures.root;

@ExtendWith(MockitoExtension.class)
class ProcedureTreeAssemblerTest {
    private static final int MAX_DEPTH = 5;
    private static final int MAX_NODES = 200;

    @Mock
    private ProcedureRepository procedureRepository;
    @Mock
    private KnowledgeAgentConfig config;

    private ProcedureTreeAssembler assembler;

    @BeforeEach
    void setUp() {
        lenient().when(config.getMaxTreeDepth()).thenReturn(MAX_DEPTH);
        lenient().when(config.getMaxTreeNodes()).thenReturn(MAX_NODES);
        assembler = new ProcedureTreeAssembler(procedureRepository, config);
    }

    @Test
    void assembleTree_shouldBuildTheTreeDownToTheAtomicProceduresInTheRecordedOrder() {
        var login = composite("Log in");
        var fillForm = composite("Fill the login form");
        var enterUser = atomic("Enter the username");
        var enterPassword = atomic("Enter the password");
        var submit = atomic("Click Sign in");
        when(procedureRepository.findSubtree(login.id(), MAX_DEPTH, MAX_NODES)).thenReturn(List.of(
                root(login, 2),
                child(submit, login, 2, 0),
                child(fillForm, login, 1, 2),
                child(enterPassword, fillForm, 2, 0),
                child(enterUser, fillForm, 1, 0)));

        var tree = assembler.assembleTree(login.id()).orElseThrow();

        assertThat(tree.description()).isEqualTo("Log in");
        assertThat(tree.children()).extracting(ProcedureNode::description).containsExactly("Fill the login form", "Click Sign in");
        assertThat(tree.children().getFirst().children()).extracting(ProcedureNode::description)
                .containsExactly("Enter the username", "Enter the password");
        var leaf = tree.children().getFirst().children().getFirst();
        assertThat(leaf.atomic()).isTrue();
        assertThat(leaf.uiElement()).isNotNull();
        assertThat(leaf.uiElement().name()).isEqualTo("Username field");
        assertThat(leaf.testData()).containsExactly("username");
        assertThat(leaf.successCount()).isEqualTo(2);
        assertThat(tree.childrenTruncated()).isFalse();
    }

    @Test
    void assembleTree_shouldMarkACompositeTruncated_whenTheCapsCutOffSomeOfItsChildren() {
        var login = composite("Log in");
        var enterUser = atomic("Enter the username");
        when(procedureRepository.findSubtree(login.id(), MAX_DEPTH, MAX_NODES)).thenReturn(List.of(root(login, 3), child(enterUser, login, 1, 0)));

        var tree = assembler.assembleTree(login.id()).orElseThrow();

        assertThat(tree.children()).hasSize(1);
        assertThat(tree.childrenTruncated()).isTrue();
    }

    @Test
    void assembleTree_shouldKeepAChildOnce_whenItsParentIsReachedThroughSeveralPaths() {
        var login = composite("Log in");
        var enterUser = atomic("Enter the username");
        when(procedureRepository.findSubtree(login.id(), MAX_DEPTH, MAX_NODES))
                .thenReturn(List.of(root(login, 1), child(enterUser, login, 1, 0), child(enterUser, login, 1, 0)));

        var tree = assembler.assembleTree(login.id()).orElseThrow();

        assertThat(tree.children()).hasSize(1);
    }

    @Test
    void assembleTree_shouldStopExpanding_whenTheGraphContainsACycle() {
        var first = composite("First");
        var second = composite("Second");
        when(procedureRepository.findSubtree(first.id(), MAX_DEPTH, MAX_NODES))
                .thenReturn(List.of(root(first, 1), child(second, first, 1, 1), child(first, second, 1, 1)));

        var tree = assembler.assembleTree(first.id()).orElseThrow();

        assertThat(tree.children()).hasSize(1);
        assertThat(tree.children().getFirst().children()).isEmpty();
        assertThat(tree.children().getFirst().childrenTruncated()).isTrue();
    }

    @Test
    void assembleTree_shouldReturnNothing_whenTheProcedureDoesNotExist() {
        var unknownId = UUID.randomUUID();
        when(procedureRepository.findSubtree(eq(unknownId), anyInt(), anyInt())).thenReturn(List.of());

        assertThat(assembler.assembleTree(unknownId)).isEmpty();
        verify(procedureRepository).findSubtree(unknownId, MAX_DEPTH, MAX_NODES);
    }

    @Test
    void summarize_shouldReturnTheOwnDataWithTheScoreAndTheUiElementOfAtomicProcedures() {
        var login = composite("Log in");
        var enterUser = atomic("Enter the username");
        when(procedureRepository.findTargetedUiElements(List.of(enterUser.id())))
                .thenReturn(Map.of(enterUser.id(), new TargetedUiElement("Username field", "Text input", "Below the logo", "Login form")));

        var summaries = assembler.summarize(List.of(login, enterUser), Map.of(enterUser.id(), 0.91));

        assertThat(summaries).hasSize(2);
        assertThat(summaries.getFirst().score()).isNull();
        assertThat(summaries.getFirst().uiElement()).isNull();
        assertThat(summaries.getFirst().effects()).containsExactly("logged in");
        assertThat(summaries.get(1).score()).isEqualTo(0.91);
        assertThat(summaries.get(1).uiElement().name()).isEqualTo("Username field");
        verify(procedureRepository).findTargetedUiElements(any());
    }
}
