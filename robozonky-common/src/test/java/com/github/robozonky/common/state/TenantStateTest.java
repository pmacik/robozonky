/*
 * Copyright 2018 The RoboZonky Project
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

package com.github.robozonky.common.state;

import java.util.UUID;

import com.github.robozonky.api.SessionInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantStateTest {

    private static final SessionInfo SESSION = new SessionInfo("someone@robozonky.cz");

    @BeforeEach
    @AfterEach
    void deleteState() {
        TenantState.destroyAll();
    }

    @Test
    void correctInstanceManagement() {
        final TenantState ts = TenantState.of(SESSION);
        assertThat(TenantState.getKnownTenants()).containsOnly(SESSION);
        final TenantState ts2 = TenantState.of(SESSION);
        assertThat(ts2).isSameAs(ts);
        final TenantState ts3 = TenantState.of(new SessionInfo(UUID.randomUUID().toString()));
        assertThat(ts3).isNotSameAs(ts);
    }

}
