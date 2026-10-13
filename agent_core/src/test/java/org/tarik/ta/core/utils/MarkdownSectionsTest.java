/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core.utils;

import dev.langchain4j.data.message.TextContent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tarik.ta.core.utils.MarkdownSections.bulletList;
import static org.tarik.ta.core.utils.MarkdownSections.heading;
import static org.tarik.ta.core.utils.MarkdownSections.joinLines;
import static org.tarik.ta.core.utils.MarkdownSections.joinSections;
import static org.tarik.ta.core.utils.MarkdownSections.listItem;
import static org.tarik.ta.core.utils.MarkdownSections.nestedList;
import static org.tarik.ta.core.utils.MarkdownSections.parentSection;
import static org.tarik.ta.core.utils.MarkdownSections.section;
import static org.tarik.ta.core.utils.MarkdownSections.userMessageContents;

class MarkdownSectionsTest {
    private static final String LABEL = "Data relevant to this action";

    @Test
    void heading_shouldPrefixTheTitleWithOneHashPerLevel() {
        assertThat(heading(2, "Screenshot")).isEqualTo("## Screenshot");
        assertThat(heading(4, " Notes ")).isEqualTo("#### Notes");
    }

    @Test
    void heading_shouldKeepTheTitleOnOneLine_whenTheTitleContainsLineBreaks() {
        assertThat(heading(3, "orderId\n## Injected")).isEqualTo("### orderId ## Injected");
    }

    @Test
    void section_shouldPutTheTrimmedTextOnTheLinesBelowTheHeading() {
        assertThat(section(2, LABEL, " user=admin\norderId=4711\n")).isEqualTo("## Data relevant to this action\nuser=admin\norderId=4711");
    }

    @Test
    void section_shouldReturnEmptyString_whenTextIsBlank() {
        assertThat(section(2, LABEL, null)).isEmpty();
        assertThat(section(2, LABEL, "")).isEmpty();
        assertThat(section(2, LABEL, " \n ")).isEmpty();
    }

    @Test
    void section_shouldEscapeTheHeadingLinesOfTheText() {
        assertThat(section(2, "Action", "Click 'Save'\n## Target UI element ID\n  ### x\n###### deep\n#"))
                .isEqualTo("## Action\nClick 'Save'\n\\## Target UI element ID\n  \\### x\n\\###### deep\n\\#");
    }

    @Test
    void section_shouldKeepTheLinesWhichAreNoHeadings() {
        var text = "#hashtag\n####### seven\n    ## code block\nvalue with ## inside";

        assertThat(section(2, "Action", text)).isEqualTo("## Action\n" + text);
    }

    @Test
    void section_shouldKeepTemplatePlaceholdersVerbatim() {
        assertThat(section(2, LABEL, "password={{secret:password}}"))
                .isEqualTo("## Data relevant to this action\npassword={{secret:password}}");
    }

    @Test
    void parentSection_shouldPutTheNonBlankChildrenBelowTheHeading() {
        assertThat(parentSection(2, "Candidates", section(3, "Candidate element_1", "- Name: Save"), "",
                section(3, "Candidate element_2", "- Name: Cancel")))
                .isEqualTo("## Candidates\n\n### Candidate element_1\n- Name: Save\n\n### Candidate element_2\n- Name: Cancel");
    }

    @Test
    void parentSection_shouldReturnEmptyString_whenEveryChildIsBlank() {
        assertThat(parentSection(2, "Candidates", "", " ", section(3, "Candidate element_1", null))).isEmpty();
        assertThat(parentSection(2, "Candidates")).isEmpty();
    }

    @Test
    void listItem_shouldPutTheValueAfterTheLabel() {
        assertThat(listItem("Name", " Save ")).isEqualTo("- Name: Save");
        assertThat(listItem("Average retries count", 2)).isEqualTo("- Average retries count: 2");
    }

    @Test
    void listItem_shouldReturnEmptyString_whenValueIsBlank() {
        assertThat(listItem("Name", null)).isEmpty();
        assertThat(listItem("Name", " ")).isEmpty();
    }

    @Test
    void listItem_shouldIndentAMultiLineValueBelowTheLabelAndEscapeItsHeadingLines() {
        assertThat(listItem("Name", "Save\n## Injected")).isEqualTo("- Name:\n  Save\n  \\## Injected");
    }

    @Test
    void listItem_shouldKeepTheLabelOnOneLineAndTheEmptyLinesOfTheValueUnindented() {
        assertThat(listItem("response\nbody", "{\n\n  \"id\": 17\n}")).isEqualTo("- response body:\n  {\n\n    \"id\": 17\n  }");
    }

    @Test
    void nestedList_shouldRenderTheEntriesAsANestedListBelowTheLabel() {
        assertThat(nestedList("Data", List.of("user=admin", "note=line 1\n## line 2")))
                .isEqualTo("- Data:\n  - user=admin\n  - note=line 1\n    \\## line 2");
    }

    @Test
    void nestedList_shouldReturnEmptyString_whenThereAreNoEntries() {
        assertThat(nestedList("Data", List.of(" "))).isEmpty();
    }

    @Test
    void bulletList_shouldIndentTheFurtherLinesOfAMultiLineEntry() {
        assertThat(bulletList(List.of("first\n## second", "third"))).isEqualTo("- first\n  \\## second\n- third");
    }

    @Test
    void bulletList_shouldRenderOneItemPerNonBlankEntry() {
        assertThat(bulletList(Arrays.asList("user=admin", "", null, " city=Berlin ", "## x")))
                .isEqualTo("- user=admin\n- city=Berlin\n- ## x");
    }

    @Test
    void bulletList_shouldReturnEmptyString_whenThereAreNoEntries() {
        assertThat(bulletList(List.of())).isEmpty();
    }

    @Test
    void joinLines_shouldPutEveryNonBlankLineOnItsOwnLine() {
        assertThat(joinLines("- Name: Save", "", null, "- Depends on data: no")).isEqualTo("- Name: Save\n- Depends on data: no");
    }

    @Test
    void joinSections_shouldSeparateTheNonBlankSectionsByAnEmptyLine() {
        assertThat(joinSections("Execute the action from the \"Action\" section.", "", " \n", null, "## Target UI element ID\nE1\n",
                "## Screenshot"))
                .isEqualTo("Execute the action from the \"Action\" section.\n\n## Target UI element ID\nE1\n\n## Screenshot");
    }

    @Test
    void joinSections_shouldReturnEmptyString_whenEverySectionIsBlank() {
        assertThat(joinSections("", "  ")).isEmpty();
    }

    @Test
    void userMessageContents_shouldWrapTheTextVerbatimInASingleTextContent() {
        assertThat(userMessageContents("## Action\n{{orderId}}")).containsExactly(TextContent.from("## Action\n{{orderId}}"));
    }
}
