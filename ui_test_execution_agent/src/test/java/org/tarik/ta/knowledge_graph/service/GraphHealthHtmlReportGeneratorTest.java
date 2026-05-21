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
import org.tarik.ta.knowledge_graph.health.GraphHealthReport;
import org.tarik.ta.knowledge_graph.health.HealthCheckCategory;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphHealthHtmlReportGeneratorTest {

    private final GraphHealthHtmlReportGenerator generator = new GraphHealthHtmlReportGenerator();

    private static GraphHealthReport reportWith(List<HealthCheckCategory> categories) {
        return new GraphHealthReport(categories, Instant.parse("2026-03-19T10:00:00Z"));
    }

    private static HealthCheckCategory okCategory(String name) {
        return HealthCheckCategory.of(name, "desc", List.of(), 3, 10);
    }

    private static HealthCheckCategory warningCategory(String name) {
        return HealthCheckCategory.of(name, "desc", List.of("f1", "f2", "f3", "f4", "f5"), 3, 10);
    }

    private static HealthCheckCategory criticalCategory(String name) {
        return HealthCheckCategory.of(name, "desc",
                List.of("f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11"), 3, 10);
    }

    @Test
    @DisplayName("All-OK report produces valid HTML with ok CSS class for all cards")
    void generateHtml_emptyReport_allCategoriesOk() {
        var categories = List.of(okCategory("Cat A"), okCategory("Cat B"));
        var html = generator.generateHtml(reportWith(categories));

        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).containsPattern("class=\"card ok\"");
        assertThat(html).doesNotContain("class=\"card warning\"");
        assertThat(html).doesNotContain("class=\"card critical\"");
    }

    @Test
    @DisplayName("Mixed severities produce correct CSS classes")
    void generateHtml_mixedSeverities_correctColorClasses() {
        var categories = List.of(okCategory("OK Cat"), warningCategory("Warn Cat"), criticalCategory("Crit Cat"));
        var html = generator.generateHtml(reportWith(categories));

        assertThat(html).contains("class=\"card ok\"");
        assertThat(html).contains("class=\"card warning\"");
        assertThat(html).contains("class=\"card critical\"");
    }

    @Test
    @DisplayName("Finding text containing HTML tags is escaped")
    void generateHtml_escapesFindingText_preventXss() {
        var xssFinding = "<script>alert(1)</script>";
        var category = HealthCheckCategory.of("XSS Cat", "desc", List.of(xssFinding), 3, 10);
        var html = generator.generateHtml(reportWith(List.of(category)));

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("All category names appear in the generated HTML")
    void generateHtml_allCategoriesPresent() {
        var names = List.of("Orphaned UI Elements", "Leaf Procedures", "Deep Hierarchies",
                "Disconnected Procedures", "Missing Effects", "Stale SATISFIES Edges", "Orphaned Failure Contexts");
        var categories = names.stream().map(GraphHealthHtmlReportGeneratorTest::okCategory).toList();
        var html = generator.generateHtml(reportWith(categories));

        for (var name : names) {
            assertThat(html).contains(name);
        }
    }

    @Test
    @DisplayName("Collapsible detail sections use <details> and <summary> tags")
    void generateHtml_collapsibleSections_detailsTagPresent() {
        var html = generator.generateHtml(reportWith(List.of(okCategory("Test"))));

        assertThat(html).contains("<details>");
        assertThat(html).contains("<summary>");
    }

    @Test
    @DisplayName("Footer contains total finding count")
    void generateHtml_totalFindingsInFooter() {
        var categories = List.of(
                warningCategory("Cat A"),  // 5 findings
                okCategory("Cat B")        // 0 findings
        );
        var html = generator.generateHtml(reportWith(categories));

        // Footer should show total = 5
        assertThat(html).containsPattern("Total findings:.*<strong>5</strong>");
    }

    @Test
    @DisplayName("Empty findings list shows 'No issues found' message")
    void generateHtml_emptyFindings_showsNoIssuesMessage() {
        var html = generator.generateHtml(reportWith(List.of(okCategory("Empty Cat"))));

        assertThat(html).contains("No issues found.");
    }
}
