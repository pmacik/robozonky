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

package com.github.robozonky.app.daemon;

import com.github.robozonky.api.Money;
import com.github.robozonky.api.notifications.*;
import com.github.robozonky.api.remote.entities.Investment;
import com.github.robozonky.api.remote.entities.Loan;
import com.github.robozonky.api.remote.entities.Participation;
import com.github.robozonky.api.remote.enums.Rating;
import com.github.robozonky.api.strategies.InvestmentStrategy;
import com.github.robozonky.api.strategies.LoanDescriptor;
import com.github.robozonky.api.strategies.ParticipationDescriptor;
import com.github.robozonky.api.strategies.PurchaseStrategy;
import com.github.robozonky.app.AbstractZonkyLeveragingTest;
import com.github.robozonky.app.tenant.PowerTenant;
import com.github.robozonky.internal.Defaults;
import com.github.robozonky.internal.remote.PurchaseResult;
import com.github.robozonky.internal.remote.Zonky;
import com.github.robozonky.internal.test.DateUtil;
import com.github.robozonky.test.mock.MockLoanBuilder;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.*;

class StrategyExecutorTest extends AbstractZonkyLeveragingTest {

    private static final PurchaseStrategy NONE_ACCEPTING_PURCHASE_STRATEGY = (a, p, r) -> Stream.empty();
    private static final PurchaseStrategy ALL_ACCEPTING_PURCHASE_STRATEGY =
            (a, p, r) -> a.stream().map(d -> d.recommend().get());
    private static final InvestmentStrategy NONE_ACCEPTING_INVESTMENT_STRATEGY = (a, p, r) -> Stream.empty();
    private static final InvestmentStrategy ALL_ACCEPTING_INVESTMENT_STRATEGY =
            (a, p, r) -> a.stream().map(d -> d.recommend(Money.from(200)).get());

    private static PurchasingOperationDescriptor mockPurchasingOperationDescriptor(
            final ParticipationDescriptor... pd) {
        final PurchasingOperationDescriptor d = new PurchasingOperationDescriptor();
        final PurchasingOperationDescriptor spied = spy(d);
        final MarketplaceAccessor<ParticipationDescriptor> marketplace = mock(MarketplaceAccessor.class);
        when(marketplace.getMarketplace()).thenReturn(pd.length == 0 ? Collections.emptyList() : Arrays.asList(pd));
        when(marketplace.hasUpdates()).thenReturn(true);
        doReturn(marketplace).when(spied).newMarketplaceAccessor(any());
        return spied;
    }

    private static InvestingOperationDescriptor mockInvestingOperationDescriptor(final LoanDescriptor... ld) {
        final InvestingOperationDescriptor d = new InvestingOperationDescriptor();
        final InvestingOperationDescriptor spied = spy(d);
        final MarketplaceAccessor<LoanDescriptor> marketplace = mock(MarketplaceAccessor.class);
        when(marketplace.getMarketplace()).thenReturn(ld.length == 0 ? Collections.emptyList() : Arrays.asList(ld));
        when(marketplace.hasUpdates()).thenReturn(true);
        doReturn(marketplace).when(spied).newMarketplaceAccessor(any());
        return spied;
    }

    @Test
    void purchasingNoStrategy() {
        final Participation mock = mock(Participation.class);
        final ParticipationDescriptor pd = new ParticipationDescriptor(mock, () -> MockLoanBuilder.fresh());
        final PowerTenant tenant = mockTenant();
        final PurchasingOperationDescriptor d = mockPurchasingOperationDescriptor(pd);
        final StrategyExecutor<ParticipationDescriptor, PurchaseStrategy> exec = new StrategyExecutor<>(tenant, d);
        assertThat(exec.get()).isEmpty();
        // check events
        final List<Event> events = getEventsRequested();
        assertThat(events).isEmpty();
    }

    @Test
    void purchasingNoneAccepted() {
        final Zonky zonky = harmlessZonky();
        final Loan loan = new MockLoanBuilder().setAmount(200).build();
        when(zonky.getLoan(eq(loan.getId()))).thenReturn(loan);
        final Participation mock = mock(Participation.class);
        when(mock.getRemainingPrincipal()).thenReturn(Money.from(250));
        final ParticipationDescriptor pd = new ParticipationDescriptor(mock, () -> loan);
        final PowerTenant tenant = mockTenant(zonky);
        when(tenant.getPurchaseStrategy()).thenReturn(Optional.of(NONE_ACCEPTING_PURCHASE_STRATEGY));
        final PurchasingOperationDescriptor d = mockPurchasingOperationDescriptor(pd);
        final StrategyExecutor<ParticipationDescriptor, PurchaseStrategy> exec = new StrategyExecutor<>(tenant, d);
        assertThat(exec.get()).isEmpty();
        final List<Event> e = getEventsRequested();
        assertThat(e).hasSize(2);
        assertSoftly(softly -> {
            softly.assertThat(e).first().isInstanceOf(PurchasingStartedEvent.class);
            softly.assertThat(e).last().isInstanceOf(PurchasingCompletedEvent.class);
        });
    }

    @Test
    void purchasingSomeAccepted() {
        final Loan loan = new MockLoanBuilder()
                .setAmount(100_000)
                .setRating(Rating.D)
                .setNonReservedRemainingInvestment(1000)
                .setMyInvestment(mockMyInvestment())
                .setDatePublished(OffsetDateTime.now())
                .build();
        final int loanId = loan.getId();
        final Rating rating = loan.getRating();
        final Zonky zonky = harmlessZonky();
        when(zonky.getLoan(eq(loanId))).thenReturn(loan);
        final Participation mock = mock(Participation.class);
        when(mock.getId()).thenReturn(1L);
        when(mock.getLoanId()).thenReturn(loanId);
        when(mock.getRemainingPrincipal()).thenReturn(Money.from(250));
        when(mock.getRating()).thenReturn(rating);
        final ParticipationDescriptor pd = new ParticipationDescriptor(mock, () -> loan);
        final PowerTenant tenant = mockTenant(zonky);
        when(tenant.getPurchaseStrategy()).thenReturn(Optional.of(ALL_ACCEPTING_PURCHASE_STRATEGY));
        final PurchasingOperationDescriptor d = mockPurchasingOperationDescriptor(pd);
        final StrategyExecutor<ParticipationDescriptor, PurchaseStrategy> exec = new StrategyExecutor<>(tenant, d);
        assertThat(exec.get()).isNotEmpty();
        verify(zonky, never()).purchase(eq(mock)); // do not purchase as we're in dry run
        final List<Event> e = getEventsRequested();
        assertThat(e).hasSize(4);
        assertSoftly(softly -> {
            softly.assertThat(e).first().isInstanceOf(PurchasingStartedEvent.class);
            softly.assertThat(e.get(1)).isInstanceOf(PurchaseRecommendedEvent.class);
            softly.assertThat(e.get(2)).isInstanceOf(InvestmentPurchasedEvent.class);
            softly.assertThat(e).last().isInstanceOf(PurchasingCompletedEvent.class);
        });
        // doing a dry run; the same participation is now ignored
        assertThat(exec.get()).isEmpty();
    }

    @Test
    void tryPurchaseButZonkyFail() {
        final Loan loan = new MockLoanBuilder()
                .setAmount(100_000)
                .setRating(Rating.D)
                .setNonReservedRemainingInvestment(1000)
                .setMyInvestment(mockMyInvestment())
                .setDatePublished(OffsetDateTime.now())
                .build();
        final int loanId = loan.getId();
        final Rating rating = loan.getRating();
        final Zonky zonky = harmlessZonky();
        when(zonky.getLoan(eq(loanId))).thenReturn(loan);
        doReturn(PurchaseResult.failure(new ClientErrorException(410))).when(zonky).purchase(any());
        final Participation mock = mock(Participation.class);
        when(mock.getId()).thenReturn(1L);
        when(mock.getLoanId()).thenReturn(loanId);
        when(mock.getRemainingPrincipal()).thenReturn(Money.from(250));
        when(mock.getRating()).thenReturn(rating);
        final ParticipationDescriptor pd = new ParticipationDescriptor(mock, () -> loan);
        final PowerTenant tenant = mockTenant(zonky, false);
        when(tenant.getPurchaseStrategy()).thenReturn(Optional.of(ALL_ACCEPTING_PURCHASE_STRATEGY));
        final PurchasingOperationDescriptor d = mockPurchasingOperationDescriptor(pd);
        final StrategyExecutor<ParticipationDescriptor, PurchaseStrategy> exec = new StrategyExecutor<>(tenant, d);
        assertThat(exec.get()).isEmpty();
    }

    @Test
    void tryPurchaseButZonkyUnknownFailPassthrough() {
        final Loan loan = new MockLoanBuilder()
                .setAmount(100_000)
                .setRating(Rating.D)
                .setNonReservedRemainingInvestment(1000)
                .setMyInvestment(mockMyInvestment())
                .setDatePublished(OffsetDateTime.now())
                .build();
        final int loanId = loan.getId();
        final Rating rating = loan.getRating();
        final Zonky zonky = harmlessZonky();
        when(zonky.getLoan(eq(loanId))).thenReturn(loan);
        doThrow(BadRequestException.class).when(zonky).purchase(any());
        final Participation mock = mock(Participation.class);
        when(mock.getId()).thenReturn(1L);
        when(mock.getLoanId()).thenReturn(loanId);
        when(mock.getRemainingPrincipal()).thenReturn(Money.from(250));
        when(mock.getRating()).thenReturn(rating);
        final ParticipationDescriptor pd = new ParticipationDescriptor(mock, () -> loan);
        final PowerTenant tenant = mockTenant(zonky, false);
        when(tenant.getPurchaseStrategy()).thenReturn(Optional.of(ALL_ACCEPTING_PURCHASE_STRATEGY));
        final PurchasingOperationDescriptor d = mockPurchasingOperationDescriptor(pd);
        final StrategyExecutor<ParticipationDescriptor, PurchaseStrategy> exec = new StrategyExecutor<>(tenant, d);
        assertThatThrownBy(exec::get).isNotNull();
    }

    @Test
    void purchasingNoItems() {
        final Zonky zonky = harmlessZonky();
        final PowerTenant tenant = mockTenant(zonky);
        final PurchasingOperationDescriptor d = mockPurchasingOperationDescriptor();
        final StrategyExecutor<ParticipationDescriptor, PurchaseStrategy> exec = new StrategyExecutor<>(tenant, d);
        when(tenant.getPurchaseStrategy()).thenReturn(Optional.of(ALL_ACCEPTING_PURCHASE_STRATEGY));
        assertThat(exec.get()).isEmpty();
        final List<Event> e = getEventsRequested();
        assertThat(e).isEmpty();
    }

    @Test
    void investingNoStrategy() {
        final Loan loan = new MockLoanBuilder()
                .setAmount(2)
                .build();
        final LoanDescriptor ld = new LoanDescriptor(loan);
        final Zonky z = AbstractZonkyLeveragingTest.harmlessZonky();
        final PowerTenant tenant = mockTenant(z);
        final InvestingOperationDescriptor d = mockInvestingOperationDescriptor(ld);
        final StrategyExecutor<LoanDescriptor, InvestmentStrategy> exec = new StrategyExecutor<>(tenant, d);
        assertThat(exec.get()).isEmpty();
        // check events
        final List<Event> events = getEventsRequested();
        assertThat(events).isEmpty();
    }

    @Test
    void investingNoItems() {
        final Zonky z = AbstractZonkyLeveragingTest.harmlessZonky();
        final PowerTenant tenant = mockTenant(z);
        when(tenant.getInvestmentStrategy()).thenReturn(Optional.of(ALL_ACCEPTING_INVESTMENT_STRATEGY));
        final InvestingOperationDescriptor d = mockInvestingOperationDescriptor();
        final StrategyExecutor<LoanDescriptor, InvestmentStrategy> exec = new StrategyExecutor<>(tenant, d);
        assertThat(exec.get()).isEmpty();
    }

    @Test
    void investingNoneAccepted() {
        final Loan loan = new MockLoanBuilder()
                .setAmount(100_000)
                .setRating(Rating.D)
                .setMyInvestment(mockMyInvestment())
                .setDatePublished(OffsetDateTime.now())
                .build();
        final int loanId = loan.getId();
        final LoanDescriptor ld = new LoanDescriptor(loan);
        final Zonky z = harmlessZonky();
        final PowerTenant tenant = mockTenant(z);
        when(tenant.getInvestmentStrategy()).thenReturn(Optional.of(NONE_ACCEPTING_INVESTMENT_STRATEGY));
        when(z.getLoan(eq(loanId))).thenReturn(loan);
        final InvestingOperationDescriptor d = mockInvestingOperationDescriptor(ld);
        final StrategyExecutor<LoanDescriptor, InvestmentStrategy> exec = new StrategyExecutor<>(tenant, d);
        assertThat(exec.get()).isEmpty();
    }

    @Test
    void investingSomeAccepted() {
        final Loan loan = new MockLoanBuilder()
                .setAmount(100_000)
                .setRating(Rating.C)
                .setNonReservedRemainingInvestment(20_000)
                .build();
        final LoanDescriptor ld = new LoanDescriptor(loan);
        final Zonky z = harmlessZonky();
        final int loanId = loan.getId(); // will be random, to avoid problems with caching
        when(z.getLoan(eq(loanId))).thenReturn(loan);
        final PowerTenant tenant = mockTenant(z);
        when(tenant.getInvestmentStrategy()).thenReturn(Optional.of(ALL_ACCEPTING_INVESTMENT_STRATEGY));
        final InvestingOperationDescriptor d = mockInvestingOperationDescriptor(ld);
        final StrategyExecutor<LoanDescriptor, InvestmentStrategy> exec = new StrategyExecutor<>(tenant, d);
        final Collection<Investment> result = exec.get();
        verify(z, never()).invest(any()); // dry run
        assertThat(result)
                .extracting(Investment::getLoanId)
                .isEqualTo(Collections.singletonList(loanId));
        // re-check; no balance changed, no marketplace changed, nothing should happen
        assertThat(exec.get()).isEmpty();
        final List<Event> evt = getEventsRequested();
        assertThat(evt).isNotEmpty();
    }

    @Test
    void doesNotInvestWhenDisabled() {
        final Zonky zonky = harmlessZonky();
        final PowerTenant tenant = mockTenant(zonky);
        final OperationDescriptor<LoanDescriptor, InvestmentStrategy> d = mock(OperationDescriptor.class);
        when(d.getLogger()).thenReturn(LogManager.getLogger());
        when(d.isEnabled(any())).thenReturn(false);
        final StrategyExecutor<LoanDescriptor, InvestmentStrategy> e = new StrategyExecutor<>(tenant, d);
        assertThat(e.get()).isEmpty();
        verify(d, never()).newMarketplaceAccessor(any());
        final List<Event> evt = getEventsRequested();
        assertThat(evt).isEmpty();
    }

    @Test
    void forcesMarketplaceCheck() {
        final Instant now = Instant.now();
        setClock(Clock.fixed(now, Defaults.ZONE_ID));
        final Zonky zonky = harmlessZonky();
        final Loan loan = new MockLoanBuilder()
                .setDatePublished(DateUtil.offsetNow().minusMinutes(10)) // avoid CAPTCHA
                .setRating(Rating.D)
                .build();
        when(zonky.getAvailableLoans(any())).thenAnswer(i -> Stream.of(loan));
        final PowerTenant tenant = mockTenant(zonky);
        when(tenant.getAvailability().isAvailable()).thenReturn(true);
        when(tenant.getInvestmentStrategy())
                .thenReturn(Optional.of((available, portfolio, restrictions) -> Stream.empty()));
        final OperationDescriptor<LoanDescriptor, InvestmentStrategy> d = new InvestingOperationDescriptor();
        final StrategyExecutor<LoanDescriptor, InvestmentStrategy> e = new StrategyExecutor<>(tenant, d);
        assertThat(e.get()).isEmpty();
        verify(zonky, times(1)).getLastPublishedLoanInfo();
        verify(zonky, times(1)).getAvailableLoans(any());
        assertThat(e.get()).isEmpty(); // the second time, marketplace wasn't checked but the cache was
        verify(zonky, times(2)).getLastPublishedLoanInfo();
        verify(zonky, times(1)).getAvailableLoans(any());
        setClock(Clock.fixed(now.plus(Duration.ofMinutes(1)), Defaults.ZONE_ID));
        assertThat(e.get()).isEmpty(); // after 1 minute, marketplace was force-checked
        verify(zonky, times(3)).getLastPublishedLoanInfo();
        verify(zonky, times(2)).getAvailableLoans(any());
    }

}
