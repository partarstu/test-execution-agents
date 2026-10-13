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
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.AgentConfig.ModelProvider;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static dev.langchain4j.data.message.ImageContent.DetailLevel.HIGH;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Locale.ROOT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tarik.ta.utils.ImageUtils.getRescaledBox;
import static org.tarik.ta.utils.ImageUtils.getScalingRatio;
import static org.tarik.ta.utils.ImageUtils.scaleImage;

/**
 * The ground-truth grounding targets the model comparison suites measure against, with the helpers that build a request and
 * judge an answer, so that a number means the same for every model. Expected regions are the elements' bounds in each
 * screenshot's own pixel space under {@code src/test/resources/screens_4k}, established by visual inspection or by reading back a
 * marker drawn on a copy.
 */
final class GroundingBaselines {
    static final Path SCREENS_DIR = Path.of("src/test/resources/screens_4k");
    /** The deployed {@code bbox.screenshot.longest.allowed.dimension.pixels}, which never binds before the budgets measured. */
    private static final int LONGEST_ALLOWED_DIMENSION_PIXELS = 5000;

    private static final Rectangle MINUTE_OPTION_30 = new Rectangle(1370, 494, 88, 43);
    private static final Rectangle HOUR_OPTION_05 = new Rectangle(1259, 618, 60, 42);
    private static final Rectangle HIGHLIGHTED_HOUR_OPTION_13 = new Rectangle(1259, 957, 60, 42);
    private static final Rectangle DEPARTURE_HOUR_SEGMENT = new Rectangle(1568, 789, 34, 40);
    private static final Rectangle ADD_VIA_LINK = new Rectangle(1117, 704, 121, 31);
    private static final Rectangle CELLAR_1009_CELL = new Rectangle(2666, 512, 98, 150);
    private static final Rectangle TO_CALENDAR_ICON = new Rectangle(2731, 401, 35, 36);
    private static final Rectangle APPLY_TIME_RANGE_BUTTON = new Rectangle(2490, 488, 258, 56);
    private static final Rectangle LAST_6_HOURS_QUICK_RANGE = new Rectangle(2927, 600, 350, 69);
    private static final Rectangle POD_ALL_CHIP_REMOVE_ICON = new Rectangle(1378, 127, 25, 27);
    private static final Rectangle DATACENTER_A_CHIP_REMOVE_ICON = new Rectangle(786, 127, 25, 27);
    private static final Rectangle WRAP_HEADER_TEXT_TOGGLE = new Rectangle(3285, 2096, 71, 42);
    private static final Rectangle SHOW_TABLE_HEADER_TOGGLE = new Rectangle(3285, 1055, 71, 42);
    private static final Rectangle STAR_SHAPE_MAP_ZOOM_OUT = new Rectangle(2033, 671, 39, 38);

    private static final String STAR_SHAPE_MAP = "Base layer ArcGIS wold imagery + star shape + share view";

    static final List<GroundingCase> CASES = List.of(
            new GroundingCase("Minute dropdown option (data-driven)",
                    "3_minute_dropdown_open.png",
                    "Pick-up Minute Option", "Option item in the minute dropdown list.",
                    "Located inside the opened minute dropdown.",
                    "Pick-up minute is 30",
                    "the '30' option in the open minute dropdown list",
                    MINUTE_OPTION_30),
            new GroundingCase("Hour option mid-list, not highlighted (data-driven)",
                    "5_hour_dropdown_open.png",
                    "Pick-up Hour Option", "Option item in the hour dropdown list.",
                    "Located inside the opened hour dropdown.",
                    "Pick-up hour is 05",
                    "the '05' option in the open hour dropdown list",
                    HOUR_OPTION_05),
            new GroundingCase("Hour option, highlighted one (data-driven)",
                    "6_hour_after_arrowdown.png",
                    "Pick-up Hour Option", "Option item in the hour dropdown list.",
                    "Located inside the opened hour dropdown.",
                    "Pick-up hour is 13",
                    "the '13' option, currently highlighted in blue, in the open hour dropdown list",
                    HIGHLIGHTED_HOUR_OPTION_13),
            // Only the hour segment accepts hour input - the minute segment and the clock icon sit in the same control,
            // so a click that lands anywhere else in it cannot enter the value.
            new GroundingCase("Hour segment inside a combined time control (data-driven)",
                    "9_train_hour_field.png",
                    "Departure Time Hour", "Hour segment of the departure time input.",
                    "Located in the journey search form, left of the ':' separator, the minute segment and the clock icon.",
                    "Departure time is 15:14",
                    "the hour value '15' inside the departure time input, left of the ':' separator",
                    DEPARTURE_HOUR_SEGMENT),



            // Six cells of the Cellar row read "15", so the value cannot pick the target out - only its column can.
            new GroundingCase("Grid cell among same-valued twins in its row (data-driven)",
                    "10_status_history_matrix.png",
                    "Temperature Reading Value Cell",
                    "Cell holding the reading of the temperature inside the specified room at one point in time.",
                    "Located in the 'Status history with time series' grid, in the row for the specified room, "
                            + "and in the column for the specified time.",
                    "Cellar, 10:09",
                    "the cell above the '10:09' tick in the 'Cellar' row of the 'Status history with time series' grid",
                    CELLAR_1009_CELL),
            // The 'From' icon directly above it is pixel-identical: only the input each one sits beside tells them apart.
            new GroundingCase("Unlabelled icon twinned with the one above it (no test data)",
                    "11_time_picker_open.png",
                    "Time Range 'To' Calendar Icon",
                    "Calendar icon opening the date picker for the end of the range.",
                    "Located in the opened absolute time range popup, right of the 'To' input.",
                    null,
                    "the calendar icon right of the 'To' input in the open absolute time range popup",
                    TO_CALENDAR_ICON),
            // The 'server' picker renders an identical 'All' chip, and every picker carries a second, separate clear icon.
            new GroundingCase("Chip remove icon among identical chips (data-driven)",
                    "12_nested_variable_pickers.png",
                    "Pod Value Remove Icon",
                    "Remove icon inside the chip of a picker for the specified variable.",
                    "Located in the variable chips horizontal bar, inside the value chip.",
                    "Variable is 'pod'",
                    "the remove icon inside the 'All' chip of the 'pod' variable picker",
                    POD_ALL_CHIP_REMOVE_ICON),
            // Four unset toggles share the pane, one of them under the near-identical 'Wrap text' caption directly above.
            new GroundingCase("Toggle among four identical toggles in a dense pane (no test data)",
                    "13_panel_edit_options.png",
                    "Wrap header text toggle",
                    "Toggle located under the label 'Wrap header text'.",
                    "Located in the panel options pane on the right, directly under its label.",
                    null,
                    "the toggle under the 'Wrap header text' caption in the panel options pane",
                    WRAP_HEADER_TEXT_TOGGLE),
            // Three maps on screen carry the same control pair, so only the panel title tells them apart - which makes the
            // panel the datum the step has to supply, exactly as a step naming one of several twin widgets would.
            new GroundingCase("Map control repeated across panels (data-driven)",
                    "14_geomap_zoom_controls.png",
                    "Zoom Out Button",
                    "Button zooming out the map.",
                    "Located at the top left of the map with specified name, under its zoom in button.",
                    "Map is '%s'".formatted(STAR_SHAPE_MAP),
                    "the zoom out button at the top left of the '%s' map".formatted(STAR_SHAPE_MAP),
                    STAR_SHAPE_MAP_ZOOM_OUT));

    /** The present elements whose absent look-alikes {@link #ABSENT_CASES} ask for and which {@link #CASES} does not cover. */
    static final List<GroundingCase> COUNTERPART_CASES = List.of(
            new GroundingCase("Text link under an input (no test data)",
                    "9_train_hour_field.png",
                    "'+ Add Via' Link", "Text link in the journey search form.",
                    "Located under the 'From' input.",
                    null,
                    "the '+ Add Via' link under the 'From' input",
                    ADD_VIA_LINK),
            new GroundingCase("Primary button of a popup (no test data)",
                    "11_time_picker_open.png",
                    "Apply Time Range Button", "Button applying the entered absolute time range.",
                    "Located in the opened absolute time range popup, under the 'To' input.",
                    null,
                    "the 'Apply time range' button in the open absolute time range popup",
                    APPLY_TIME_RANGE_BUTTON),
            new GroundingCase("Quick range option in a scrollable list (data-driven)",
                    "11_time_picker_open.png",
                    "Quick Range Option", "Option of the quick ranges list applying a relative time range.",
                    "Located in the quick ranges list of the opened time range popup.",
                    "Quick range is 'Last 6 hours'",
                    "the 'Last 6 hours' option in the quick ranges list of the open time range popup",
                    LAST_6_HOURS_QUICK_RANGE),
            new GroundingCase("Switched-on toggle in a dense pane (no test data)",
                    "13_panel_edit_options.png",
                    "Show table header toggle",
                    "Toggle located under the label 'Show table header'.",
                    "Located in the panel options pane on the right, directly under its label.",
                    null,
                    "the toggle under the 'Show table header' caption in the panel options pane",
                    SHOW_TABLE_HEADER_TOGGLE));

    /**
     * Targets absent from their screenshot, each a present element with one attribute changed; where the element name repeats
     * that attribute, the name changes with it. Every one of them has to be rejected.
     */
    static final List<AbsentTargetCase> ABSENT_CASES = List.of(
            new AbsentTargetCase("Minute option '35' of a 00/15/30/45 list",
                    "3_minute_dropdown_open.png",
                    "Pick-up Minute Option", "Option item in the minute dropdown list.",
                    "Located inside the opened minute dropdown.",
                    "Pick-up minute is 35",
                    Perturbation.VALUE, MINUTE_OPTION_30),
            new AbsentTargetCase("Minute option '30' of the closed drop-off dropdown",
                    "3_minute_dropdown_open.png",
                    "Drop-off Minute Option", "Option item in the minute dropdown list.",
                    "Located inside the opened drop-off minute dropdown.",
                    "Drop-off minute is 30",
                    Perturbation.CONTAINER, MINUTE_OPTION_30),
            new AbsentTargetCase("Hour option '21' scrolled out of view",
                    "5_hour_dropdown_open.png",
                    "Pick-up Hour Option", "Option item in the hour dropdown list.",
                    "Located inside the opened hour dropdown.",
                    "Pick-up hour is 21",
                    Perturbation.HIDDEN, HOUR_OPTION_05),
            new AbsentTargetCase("Option '05' of the closed minute dropdown",
                    "5_hour_dropdown_open.png",
                    "Pick-up Minute Option", "Option item in the minute dropdown list.",
                    "Located inside the opened minute dropdown.",
                    "Pick-up minute is 05",
                    Perturbation.CONTAINER, HOUR_OPTION_05),
            new AbsentTargetCase("Hour option '14' highlighted, while '13' is",
                    "6_hour_after_arrowdown.png",
                    "Pick-up Hour Option", "Option item in the hour dropdown list, currently highlighted in blue.",
                    "Located inside the opened hour dropdown.",
                    "Pick-up hour is 14",
                    Perturbation.STATE, HIGHLIGHTED_HOUR_OPTION_13),
            new AbsentTargetCase("Hour option '13' of the closed drop-off dropdown",
                    "6_hour_after_arrowdown.png",
                    "Drop-off Hour Option", "Option item in the hour dropdown list.",
                    "Located inside the opened drop-off hour dropdown.",
                    "Drop-off hour is 13",
                    Perturbation.CONTAINER, HIGHLIGHTED_HOUR_OPTION_13),
            new AbsentTargetCase("Hour value '16', shown only by the date (distractor)",
                    "9_train_hour_field.png",
                    "Departure Time Hour", "Hour segment of the departure time input.",
                    "Located in the journey search form, left of the ':' separator, the minute segment and the clock icon.",
                    "Departure time is 16:14",
                    Perturbation.VALUE, DEPARTURE_HOUR_SEGMENT),
            new AbsentTargetCase("Minute value '41' where '14' is shown",
                    "9_train_hour_field.png",
                    "Departure Time Minute", "Minute segment of the departure time input.",
                    "Located in the journey search form, right of the ':' separator.",
                    "Departure time is 15:41",
                    Perturbation.VALUE, DEPARTURE_HOUR_SEGMENT),
            new AbsentTargetCase("'+ Add return journey' link",
                    "9_train_hour_field.png",
                    "'+ Add return journey' Link", "Text link in the journey search form.",
                    "Located under the 'From' input.",
                    null,
                    Perturbation.LABEL, ADD_VIA_LINK),
            new AbsentTargetCase("Grid cell of a 'Garage' row",
                    "10_status_history_matrix.png",
                    "Temperature Reading Value Cell",
                    "Cell holding the reading of the temperature inside the specified room at one point in time.",
                    "Located in the 'Status history with time series' grid, in the row for the specified room, "
                            + "and in the column for the specified time.",
                    "Garage, 10:09",
                    Perturbation.LABEL, CELLAR_1009_CELL),
            new AbsentTargetCase("Grid cell of a '10:15' column (ticks are :09/:39)",
                    "10_status_history_matrix.png",
                    "Temperature Reading Value Cell",
                    "Cell holding the reading of the temperature inside the specified room at one point in time.",
                    "Located in the 'Status history with time series' grid, in the row for the specified room, "
                            + "and in the column for the specified time.",
                    "Cellar, 10:15",
                    Perturbation.VALUE, CELLAR_1009_CELL),
            new AbsentTargetCase("Cellar cell reading '17 °'",
                    "10_status_history_matrix.png",
                    "Temperature Reading Value Cell",
                    "Cell holding the reading of the temperature inside the specified room at one point in time.",
                    "Located in the 'Status history with time series' grid, in the row for the specified room, "
                            + "holding the specified reading.",
                    "Cellar, 17 °",
                    Perturbation.VALUE, CELLAR_1009_CELL),
            new AbsentTargetCase("Clock icon right of 'To'",
                    "11_time_picker_open.png",
                    "Time Range 'To' Clock Icon",
                    "Clock icon opening the time picker for the end of the range.",
                    "Located in the opened absolute time range popup, right of the 'To' input.",
                    null,
                    Perturbation.TYPE, TO_CALENDAR_ICON),
            new AbsentTargetCase("'To' calendar icon of a relative time range popup",
                    "11_time_picker_open.png",
                    "Time Range 'To' Calendar Icon",
                    "Calendar icon opening the date picker for the end of the range.",
                    "Located in the opened relative time range popup, right of the 'To' input.",
                    null,
                    Perturbation.CONTAINER, TO_CALENDAR_ICON),
            new AbsentTargetCase("'Cancel' button next to 'Apply time range'",
                    "11_time_picker_open.png",
                    "Cancel Button", "Button discarding the entered absolute time range.",
                    "Located in the opened absolute time range popup, under the 'To' input.",
                    null,
                    Perturbation.TYPE, APPLY_TIME_RANGE_BUTTON),
            new AbsentTargetCase("Quick range 'Last 7 days' scrolled out of view",
                    "11_time_picker_open.png",
                    "Quick Range Option", "Option of the quick ranges list applying a relative time range.",
                    "Located in the quick ranges list of the opened time range popup.",
                    "Quick range is 'Last 7 days'",
                    Perturbation.HIDDEN, LAST_6_HOURS_QUICK_RANGE),
            new AbsentTargetCase("Chip remove icon of a 'node' picker",
                    "12_nested_variable_pickers.png",
                    "Node Value Remove Icon",
                    "Remove icon inside the chip of a picker for the specified variable.",
                    "Located in the variable chips horizontal bar, inside the value chip.",
                    "Variable is 'node'",
                    Perturbation.LABEL, POD_ALL_CHIP_REMOVE_ICON),
            new AbsentTargetCase("Remove icon of an 'AAB' chip, shown only in panel text (distractor)",
                    "12_nested_variable_pickers.png",
                    "Pod Value Remove Icon",
                    "Remove icon inside the chip of the specified value in the picker for the 'pod' variable.",
                    "Located in the variable chips horizontal bar, inside the value chip.",
                    "Pod value is 'AAB'",
                    Perturbation.VALUE, POD_ALL_CHIP_REMOVE_ICON),
            new AbsentTargetCase("Remove icon of a 'B' chip of the 'datacenter' picker",
                    "12_nested_variable_pickers.png",
                    "Datacenter Value Remove Icon",
                    "Remove icon inside the chip of the specified value in the picker for the 'datacenter' variable.",
                    "Located in the variable chips horizontal bar, inside the value chip.",
                    "Datacenter value is 'B'",
                    Perturbation.VALUE, DATACENTER_A_CHIP_REMOVE_ICON),
            new AbsentTargetCase("'Wrap footer text' toggle",
                    "13_panel_edit_options.png",
                    "Wrap footer text toggle",
                    "Toggle located under the label 'Wrap footer text'.",
                    "Located in the panel options pane on the right, directly under its label.",
                    null,
                    Perturbation.LABEL, WRAP_HEADER_TEXT_TOGGLE),
            new AbsentTargetCase("'Show table footer' toggle",
                    "13_panel_edit_options.png",
                    "Show table footer toggle",
                    "Toggle located under the label 'Show table footer'.",
                    "Located in the panel options pane on the right, directly under its label.",
                    null,
                    Perturbation.LABEL, SHOW_TABLE_HEADER_TOGGLE),
            new AbsentTargetCase("Switched-on 'Wrap header text' toggle, while it is off",
                    "13_panel_edit_options.png",
                    "Wrap header text toggle",
                    "Switched-on toggle located under the label 'Wrap header text'.",
                    "Located in the panel options pane on the right, directly under its label.",
                    null,
                    Perturbation.STATE, WRAP_HEADER_TEXT_TOGGLE),
            new AbsentTargetCase("Zoom out button of a '+ circle shape +' map",
                    "14_geomap_zoom_controls.png",
                    "Zoom Out Button",
                    "Button zooming out the map.",
                    "Located at the top left of the map with specified name, under its zoom in button.",
                    "Map is 'Base layer ArcGIS wold imagery + circle shape + share view'",
                    Perturbation.LABEL, STAR_SHAPE_MAP_ZOOM_OUT),
            new AbsentTargetCase("Zoom out button at the top right of the map",
                    "14_geomap_zoom_controls.png",
                    "Zoom Out Button",
                    "Button zooming out the map.",
                    "Located at the top right of the map with specified name, under its zoom in button.",
                    "Map is '%s'".formatted(STAR_SHAPE_MAP),
                    Perturbation.POSITION, STAR_SHAPE_MAP_ZOOM_OUT),
            new AbsentTargetCase("Fullscreen button at the top left of the map",
                    "14_geomap_zoom_controls.png",
                    "Fullscreen Button",
                    "Button switching the map to fullscreen.",
                    "Located at the top left of the map with specified name, under its zoom in button.",
                    "Map is '%s'".formatted(STAR_SHAPE_MAP),
                    Perturbation.TYPE, STAR_SHAPE_MAP_ZOOM_OUT));

    private GroundingBaselines() {
    }

    /** Builds the target text exactly as {@link ElementLocatorTools#elementInfo} sends it to the grounder in production. */
    static @NotNull String productionTargetText(@NotNull GroundingTarget target) {
        return ElementLocatorTools.elementInfo(target.elementName(), target.elementDescription(), target.locationDetails(), target.testData());
    }

    static @NotNull Path imagePathOf(@NotNull GroundingTarget target) {
        return SCREENS_DIR.resolve(target.imageFile());
    }

    static @NotNull String encodeImage(@NotNull Path imagePath) throws IOException {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
    }

    static @NotNull Dimension imageSizeOf(@NotNull GroundingTarget target) throws IOException {
        var image = ImageIO.read(imagePathOf(target).toFile());
        return new Dimension(image.getWidth(), image.getHeight());
    }

    static @NotNull Point centerOf(@NotNull Rectangle rectangle) {
        return new Point(rectangle.x + rectangle.width / 2, rectangle.y + rectangle.height / 2);
    }

    static double intersectionOverUnion(@NotNull Rectangle first, @NotNull Rectangle second) {
        var intersection = first.intersection(second);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        double intersectionArea = (double) intersection.width * intersection.height;
        double unionArea = (double) first.width * first.height + (double) second.width * second.height - intersectionArea;
        return intersectionArea / unionArea;
    }

    /**
     * Tolerates a model that wraps its JSON in a markdown fence, which happens when no output constraint is applied
     * server-side - the normal case for a plain-text grounding request.
     */
    static @NotNull String stripJsonFence(@NotNull String rawContent) {
        var trimmed = rawContent.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        return trimmed.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
    }

    /**
     * Prepares the screenshot the way {@code ElementLocatorTools#identifyBoundingBoxesUsingVision} prepares it for the given
     * budget, reusing the production scaling math so that a suite measures what ships rather than its own arithmetic.
     */
    static @NotNull SentScreenshot screenshotFor(@NotNull GroundingTarget target, double maxMegapixels) throws IOException {
        var original = ImageIO.read(imagePathOf(target).toFile());
        var scalingRatio = getScalingRatio(original, LONGEST_ALLOWED_DIMENSION_PIXELS, maxMegapixels);
        var imageToSend = scalingRatio < 1.0 ? scaleImage(original, scalingRatio) : original;
        return new SentScreenshot(imageToSend, scalingRatio, new Dimension(original.getWidth(), original.getHeight()));
    }

    /** The settings the grounders read, at the values the deployed configuration uses. */
    static @NotNull UiTestAgentConfig groundingConfig(@NotNull ModelProvider modelProvider) {
        var config = mock(UiTestAgentConfig.class);
        when(config.getElementBoundingBoxAgentImageDetailLevel()).thenReturn(HIGH);
        when(config.getElementBoundingBoxAgentModelProvider()).thenReturn(modelProvider);
        // An unlimited pixel budget, because the suites scale their own screenshots and their recorded numbers depend on that.
        when(config.getMaxModelImagePixels()).thenReturn(MAX_VALUE);
        when(config.isBoundingBoxAlreadyNormalized()).thenReturn(true);
        return config;
    }

    static double mean(double[] values) {
        return values.length == 0 ? Double.NaN : Arrays.stream(values).average().orElse(Double.NaN);
    }

    /** Nearest-rank percentile over an already sorted array, which needs no interpolation rule to explain. */
    static double percentile(double[] sorted, double quantile) {
        if (sorted.length == 0) {
            return Double.NaN;
        }
        var rank = (int) Math.ceil(quantile * sorted.length);
        return sorted[Math.min(Math.max(rank, 1), sorted.length) - 1];
    }

    static @NotNull String format(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(ROOT, "%.1f", value);
    }

    /** What a present target and an absent one share, which is everything the production target text is built from. */
    sealed interface GroundingTarget permits GroundingCase, AbsentTargetCase {
        String name();

        String imageFile();

        String elementName();

        String elementDescription();

        String locationDetails();

        @Nullable String testData();

        default boolean isDataDependent() {
            return testData() != null;
        }
    }

    /**
     * One ground-truth grounding target, whose {@code testData} is {@code null} for an element that is not data-driven. {@code
     * resolvedTarget} describes it the way a vendor grounding guide phrases targets, already resolved against the test data, and
     * {@code expectedRegion} gives its bounds in the screenshot's own pixel space.
     */
    record GroundingCase(String name, String imageFile, String elementName, String elementDescription,
                         String locationDetails, String testData, String resolvedTarget, Rectangle expectedRegion)
            implements GroundingTarget {
    }

    /**
     * A target that is not on its screenshot, with {@code lookalikeRegion} the bounds of the present element it was derived from,
     * which is where a model that ignores the changed attribute would land.
     */
    record AbsentTargetCase(String name, String imageFile, String elementName, String elementDescription,
                            String locationDetails, @Nullable String testData, Perturbation perturbation,
                            Rectangle lookalikeRegion) implements GroundingTarget {
    }

    /** The attribute that tells an absent target apart from the present element it was derived from. */
    enum Perturbation {
        VALUE, LABEL, TYPE, STATE, CONTAINER, POSITION, HIDDEN
    }

    /**
     * The screenshot as it is sent for one budget, together with what it takes to read an answer expressed in its pixels
     * back in the original screenshot's ones, which is the space every expected region lives in.
     */
    record SentScreenshot(BufferedImage image, double scalingRatio, Dimension originalSize) {
        @NotNull Rectangle inOriginalPixelSpace(@NotNull Rectangle region) {
            return scalingRatio < 1.0 ? getRescaledBox(region, scalingRatio) : region;
        }
    }
}
