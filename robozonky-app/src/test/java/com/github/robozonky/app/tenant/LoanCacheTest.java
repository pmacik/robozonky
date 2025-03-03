/*
 * Copyright 2019 The RoboZonky Project
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

package com.github.robozonky.app.tenant;

import com.github.robozonky.api.remote.entities.Loan;
import com.github.robozonky.api.remote.entities.MyInvestment;
import com.github.robozonky.app.AbstractZonkyLeveragingTest;
import com.github.robozonky.internal.Defaults;
import com.github.robozonky.internal.remote.Zonky;
import com.github.robozonky.internal.tenant.Tenant;
import com.github.robozonky.test.mock.MockLoanBuilder;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LoanCacheTest extends AbstractZonkyLeveragingTest {

    @Test
    void emptyGetLoan() {
        final int loanId = 1;
        final MyInvestment mi = mock(MyInvestment.class);
        final OffsetDateTime d = OffsetDateTime.now();
        when(mi.getTimeCreated()).thenReturn(d);
        final Loan loan = new MockLoanBuilder()
                .setMyInvestment(mi)
                .setRemainingInvestment(1_000)
                .build();
        final Zonky z = harmlessZonky();
        final Tenant t = mockTenant(z);
        final Cache<Loan> c = Cache.forLoan(t);
        assertThat(c.getFromCache(loanId)).isEmpty(); // nothing returned at first
        when(z.getLoan(eq(loanId))).thenReturn(loan);
        assertThat(c.get(loanId)).isEqualTo(loan); // return the freshly retrieved loan
    }

    @Test
    void loadLoan() {
        final Instant instant = Instant.now();
        setClock(Clock.fixed(instant, Defaults.ZONE_ID));
        final Loan loan = new MockLoanBuilder()
                .setRemainingInvestment(0)
                .build();
        final int loanId = loan.getId();
        final Zonky z = harmlessZonky();
        when(z.getLoan(eq(loanId))).thenReturn(loan);
        final Tenant t = mockTenant(z);
        final Cache<Loan> c = Cache.forLoan(t);
        assertThat(c.get(loanId)).isEqualTo(loan); // return the freshly retrieved loan
        verify(z).getLoan(eq(loanId));
        assertThat(c.getFromCache(loanId)).contains(loan);
        verify(z, times(1)).getLoan(eq(loanId));
        // and now test eviction
        setClock(Clock.fixed(instant.plus(Duration.ofHours(25)), Defaults.ZONE_ID));
        assertThat(c.getFromCache(loanId)).isEmpty();
    }

    @Test
    void fail() {
        final Instant instant = Instant.now();
        setClock(Clock.fixed(instant, Defaults.ZONE_ID));
        final Loan loan = MockLoanBuilder.fresh();
        final int loanId = loan.getId();
        final Zonky z = harmlessZonky();
        doThrow(IllegalStateException.class).when(z).getLoan(eq(loanId));
        final Tenant t = mockTenant(z);
        final Cache<Loan> c = Cache.forLoan(t);
        assertThatThrownBy(() -> c.get(loanId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Loan")
                .hasMessageContaining(String.valueOf(loanId));
    }
}
