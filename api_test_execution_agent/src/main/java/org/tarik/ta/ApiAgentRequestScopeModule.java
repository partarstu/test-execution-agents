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
package org.tarik.ta;

import io.avaje.inject.Factory;
import io.avaje.inject.InjectModule;

/**
 * Activates the {@link ApiAgentRequestScope} in a child {@link io.avaje.inject.BeanScope}.
 * All beans annotated with {@code @ApiAgentRequestScope} are auto-wired from constructor
 * injection; no external state needs to be supplied at request time.
 */
@Factory
@InjectModule(requires = ApiAgentRequestScope.class)
class ApiAgentRequestScopeModule {
}
