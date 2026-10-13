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
package org.tarik.ta;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.dto.Precondition;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;

import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the agent against the public Grafana Play instance the Grafana grounding samples came from, on controls the booking form
 * never has: pixel-identical neighbours told apart only by their input or panel, a grid cell addressed by row and column, a
 * toggle among unset twins in a scrollable pane, a chip beside a second clear icon and a control repeated across panels. Expected
 * results are phrased against structure rather than panel numbers or time ticks, because Play serves random-walk data that
 * follows the wall clock.
 */
@Disabled("Manual test that requires GUI and full bootstrap")
class GrafanaManualTest {

    @Test
    void testGrafanaPlayDashboards() throws Exception {
        // Given
        var steps = new LinkedList<TestStep>();

        steps.add(new TestStep("Open the dashboard with specified name using the search field.",
                List.of("Dashboard name is 'Table Panel Showcase'"),
                "The dashboard is opened and the breadcrumb at the top shows 'Dashboards > Examples > Table Panel Showcase'."));

        steps.add(new TestStep("Set the time range from now to the specified date.",
                List.of("The range is the last day of the displayed in the calendar month"),
                """                        
                        The time range picker in the top right corner shows an absolute range which ends with that date instead of showing
                        'Last 6 hours'.
                        """));

        steps.add(new TestStep("Open the edit view of the specified panel.",
                List.of("Panel = 'Bar gauge cell display mode' (the first one)."),
                """
                        The panel edit view is opened, the breadcrumb ends with 'Edit panel'.
                        The edited panel is displayed at the top and the panel options pane with the selected 'Table' visualization is
                        displayed on the right side.
                        """));

        steps.add(new TestStep("Activate wrapping the header text on the dashboard.",
                List.of(),
                "The toggle under the 'Wrap header text' label is switched on, the toggle under the 'Wrap text' label above it stays " +
                        "switched off."));

        steps.add(new TestStep("Set the cell height of the table to the value specified in the test data using the 'Cell height' control " +
                "in the panel options pane.",
                List.of("Cell height is 'Large'"),
                """
                        'Large' is the selected option of the 'Cell height' control.
                        The rows of the table in the panel preview above became higher.
                        """));

        steps.add(new TestStep("Discard the changes made to the panel and return back to the dashboard.",
                List.of(),
                "The panel edit view is closed and the 'Table Panel Showcase' dashboard is displayed again."));

        steps.add(new TestStep("Open the dashboard with specified name using the search field.",
                List.of("Dashboard name is 'Templating - Nested Variables Drilldown'"),
                "The specified dashboard is opened and the variable bar at its top shows the 'datacenter', the 'server' and the 'pod' " +
                        "pickers."));

        steps.add(new TestStep("Select the specified value in the variable picker using filter.",
                List.of("picker variable = 'pod', value = 'ABD'"),
                """
                        The specified picker shows the specified chip.
                        The text panel on the left shows the selected value as the only value of the specified picker variable.
                        """));

        steps.add(new TestStep("Open the dashboard with specified name using the search field.",
                List.of("Dashboard name is 'State timeline and Status history'"),
                "The specified dashboard is opened."));

        steps.add(new TestStep("Show the first temperature reading for the specified room",
                List.of("Room = 'Cellar'", "panel='Status history with time series'"),
                """
                        A tooltip is displayed which shows 'Cellar' as the name of the row.
                        The temperature value in that tooltip is the same one which is displayed inside the cell the mouse hovers over.
                        """));

        steps.add(new TestStep("Open the dashboard with specified name using the search field.",
                List.of("Dashboard name is 'Geomap Examples'"),
                """
                        The specified dashboard is opened.
                        The 'Size, color mapped to different fields + share view' and the 'Base layer ArcGIS wold imagery + star shape +
                        share view' map panels are displayed next to each other.
                        """));

        steps.add(new TestStep("Zoom the map of the specified panel out by two steps using the zoom out button of that map.",
                List.of("Map panel = 'Base layer ArcGIS wold imagery + star shape + share view'"),
                """
                        The satellite map of that panel shows a larger area than before.
                        """));

        TestCase testCase = new TestCase("Inspect and configure the Grafana example dashboards",
                List.of(new Precondition("The main page of Grafana is opened in a browser.", List.of())), List.of(),                steps);

        // When
        TestExecutionResult actualResult = ManualAgentRunner.runOnCurrentScreen(testCase);

        // Then
        assertThat(actualResult.getTestExecutionStatus()).isEqualTo(TestExecutionResult.TestExecutionStatus.PASSED);
    }
}
