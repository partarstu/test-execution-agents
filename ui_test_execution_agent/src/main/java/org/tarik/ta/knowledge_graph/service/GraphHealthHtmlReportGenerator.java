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
package org.tarik.ta.knowledge_graph.service;

import jakarta.inject.Singleton;

import org.tarik.ta.knowledge_graph.health.GraphHealthReport;
import org.tarik.ta.knowledge_graph.health.HealthCheckCategory;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.tarik.ta.utils.HtmlUtils.escapeHtml;

/**
 * Generates a self-contained HTML health report from a {@link GraphHealthReport}.
 * No external templating library — uses {@code String.formatted()} and inline CSS only.
 */
@Singleton
class GraphHealthHtmlReportGenerator {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    String generateHtml(GraphHealthReport report) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                %s
                <body>
                  <div class="container">
                    <div class="header">
                      <h1>Knowledge Graph Health Report</h1>
                      <p class="timestamp">Generated: %s</p>
                    </div>
                    <h2>Summary</h2>
                    %s
                    <h2>Details</h2>
                    %s
                    %s
                  </div>
                </body>
                </html>
                """.formatted(
                buildHead(),
                TIMESTAMP_FMT.format(report.generatedAt()),
                buildSummaryGrid(report.categories()),
                buildDetailSections(report.categories()),
                buildFooter(report)
        );
    }

    private static String buildHead() {
        return """
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Knowledge Graph Health Report</title>
                  <style>
                    body { font-family: sans-serif; margin: 0; background: #f5f5f5; color: #333; }
                    .container { max-width: 1100px; margin: 0 auto; padding: 2em; }
                    .header { background: #2c3e50; color: white; padding: 1.5em 2em; border-radius: 8px; margin-bottom: 1.5em; }
                    .header h1 { margin: 0 0 0.3em; font-size: 1.6em; }
                    .timestamp { margin: 0; opacity: 0.8; font-size: 0.9em; }
                    h2 { color: #2c3e50; border-bottom: 2px solid #ddd; padding-bottom: 0.3em; }
                    .summary-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 1em; margin: 1em 0 2em; }
                    .card { padding: 1em 1.2em; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.12); }
                    .card h3 { margin: 0 0 0.4em; font-size: 0.95em; }
                    .badge { display: inline-block; padding: 0.2em 0.6em; border-radius: 12px; font-size: 0.85em; font-weight: bold; }
                    .ok { background: #d4edda; border-left: 4px solid #28a745; }
                    .ok .badge { background: #28a745; color: white; }
                    .warning { background: #fff3cd; border-left: 4px solid #ffc107; }
                    .warning .badge { background: #ffc107; color: #333; }
                    .critical { background: #f8d7da; border-left: 4px solid #dc3545; }
                    .critical .badge { background: #dc3545; color: white; }
                    details { margin: 0.6em 0; background: white; border-radius: 6px; padding: 0.6em 1em; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
                    summary { cursor: pointer; font-weight: bold; padding: 0.3em 0; }
                    ul { margin: 0.5em 0 0.2em 1.5em; padding: 0; }
                    li { margin: 0.3em 0; font-size: 0.9em; font-family: monospace; }
                    .no-issues { color: #28a745; font-style: italic; font-size: 0.9em; }
                    .footer { margin-top: 2em; padding: 1em; background: #ecf0f1; border-radius: 6px; font-size: 0.85em; color: #666; }
                  </style>
                </head>
                """;
    }

    private static String buildSummaryGrid(List<HealthCheckCategory> categories) {
        var cards = new StringBuilder();
        for (var category : categories) {
            cards.append(buildSummaryCard(category));
        }
        return "<div class=\"summary-grid\">%s</div>".formatted(cards);
    }

    private static String buildSummaryCard(HealthCheckCategory category) {
        return """
                <div class="card %s">
                  <h3>%s</h3>
                  <span class="badge">%d finding(s)</span>
                  <p style="margin: 0.4em 0 0; font-size: 0.8em;">%s</p>
                </div>
                """.formatted(
                severityCssClass(category.severity()),
                escapeHtml(category.name()),
                category.findings().size(),
                escapeHtml(category.description())
        );
    }

    private static String buildDetailSections(List<HealthCheckCategory> categories) {
        var sb = new StringBuilder();
        for (var category : categories) {
            sb.append(buildDetailSection(category));
        }
        return sb.toString();
    }

    private static String buildDetailSection(HealthCheckCategory category) {
        return """
                <details>
                  <summary>%s &mdash; <span class="%s">%s (%d)</span></summary>
                  %s
                </details>
                """.formatted(
                escapeHtml(category.name()),
                severityCssClass(category.severity()),
                category.severity().name(),
                category.findings().size(),
                buildFindingsList(category.findings())
        );
    }

    private static String buildFindingsList(List<String> findings) {
        if (findings.isEmpty()) {
            return "<p class=\"no-issues\">No issues found.</p>";
        }
        var items = new StringBuilder("<ul>");
        for (var finding : findings) {
            items.append("<li>%s</li>".formatted(escapeHtml(finding)));
        }
        items.append("</ul>");
        return items.toString();
    }

    private static String buildFooter(GraphHealthReport report) {
        return """
                <div class="footer">
                  Total findings: <strong>%d</strong> across %d categories &mdash; Generated at %s
                </div>
                """.formatted(
                report.totalFindings(),
                report.categories().size(),
                TIMESTAMP_FMT.format(report.generatedAt())
        );
    }

    private static String severityCssClass(HealthCheckCategory.Severity severity) {
        return switch (severity) {
            case OK -> "ok";
            case WARNING -> "warning";
            case CRITICAL -> "critical";
        };
    }
}
