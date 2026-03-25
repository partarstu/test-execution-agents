/*
 * Copyright Â© 2026 Taras Paruta (partarstu@gmail.com)
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

import io.avaje.inject.BeanScope;
import jakarta.inject.Singleton;
import org.tarik.ta.core.BaseAgentRequestModule;
import org.tarik.ta.model.VisualState;

@Singleton
public class UiAgentRequestScopeFactory {
    private final BeanScope appScope;

    public UiAgentRequestScopeFactory(BeanScope appScope) {
        this.appScope = appScope;
    }

    public BeanScope create(VisualState visualState) {
        return BeanScope.builder()
                .parent(appScope)
                .bean(VisualState.class, visualState)
                .modules(new BaseAgentRequestModule(), new UiAgentRequestModule())
                .build();
    }
}
