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
package org.tarik.ta.user_dialogs.knowledge;

import org.tarik.ta.dto.KnowledgeSuggestionResult;

/**
 * Loads AI suggestions for the given procedure description.
 * All execution context (executed atomics, preceding siblings) is baked into the loader at creation time.
 */
@FunctionalInterface
public interface SuggestionLoader {
    KnowledgeSuggestionResult load(String procedureDescription);
}
