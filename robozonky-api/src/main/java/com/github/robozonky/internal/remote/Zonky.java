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

package com.github.robozonky.internal.remote;

import com.github.robozonky.api.remote.*;
import com.github.robozonky.api.remote.entities.*;
import com.github.robozonky.api.remote.enums.Resolution;
import com.github.robozonky.internal.Defaults;
import com.github.robozonky.internal.Settings;
import com.github.rutledgepaulv.pagingstreams.PagingStreams;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.ClientErrorException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Represents an instance of Zonky API that is fully authenticated and ready to perform operations on behalf of the
 * user.
 * <p>
 * Zonky implements API quotas on their endpoints. Reaching the quota will result in receiving "HTTP 429 Too Many
 * Requests". The following operations have their own quotas:
 *
 * <ul>
 *   <li>{@link #getLastPublishedLoanInfo()} allows for 3000 requests, with one request cleared every second. Therefore
 *   we do not request-count this API, as we will only ever request this value once every second.</li>
 *   <li>{@link #getAvailableParticipations(Select)} ()} is in the same situation.</li>
 *   <li>Everything else is on the same quote and therefore is request-counted.</li>
 * </ul>
 * <p>
 * Request counting does not actually limit anything. It just gives us an idea through logs how close or how far we are
 * from reaching the quota.
 */
public class Zonky {

    private static final Logger LOGGER = LogManager.getLogger(Zonky.class);

    private final Api<ControlApi> controlApi;
    private final Api<ReservationApi> reservationApi;
    private final PaginatedApi<Loan, LoanApi> loanApi;
    private final PaginatedApi<Participation, ParticipationApi> participationApi;
    private final PaginatedApi<Investment, PortfolioApi> portfolioApi;
    private final PaginatedApi<Development, CollectionsApi> collectionsApi;

    Zonky(final ApiProvider api, final Supplier<ZonkyApiToken> tokenSupplier) {
        this.controlApi = api.control(tokenSupplier);
        this.loanApi = api.marketplace(tokenSupplier);
        this.reservationApi = api.reservations(tokenSupplier);
        this.participationApi = api.secondaryMarketplace(tokenSupplier);
        this.portfolioApi = api.portfolio(tokenSupplier);
        this.collectionsApi = api.collections(tokenSupplier);
    }

    private static <T, S> Stream<T> getStream(final PaginatedApi<T, S> api, final Function<S, List<T>> function) {
        return getStream(api, function, Settings.INSTANCE.getDefaultApiPageSize());
    }

    private static <T, S> Stream<T> getStream(final PaginatedApi<T, S> api, final Function<S, List<T>> function,
                                              final int pageSize) {
        return getStream(api, function, new Select(), pageSize);
    }

    private static <T, S> Stream<T> getStream(final PaginatedApi<T, S> api, final Function<S, List<T>> function,
                                              final Select select) {
        return getStream(api, function, select, Settings.INSTANCE.getDefaultApiPageSize());
    }

    private static <T, S> Stream<T> getStream(final PaginatedApi<T, S> api, final Function<S, List<T>> function,
                                              final Select select, final int pageSize) {
        return PagingStreams.streamBuilder(new EntityCollectionPageSource<>(api, function, select, pageSize))
                .pageSize(pageSize)
                .build();
    }

    private static <T> Stream<T> excludeNonCZK(final Stream<T> data,
                                               final Function<T, Currency> extractor) {
        return data.filter(i -> Objects.equals(extractor.apply(i), Defaults.CURRENCY));
    }

    /**
     * @param investment
     * @return Success or one of known investment failures.
     * @throws Exception Non-investment related failures, such as expired authentication.
     */
    public InvestmentResult invest(final Investment investment) {
        LOGGER.debug("Investing into loan #{}.", investment.getLoanId());
        try {
            controlApi.run(api -> api.invest(investment));
            return InvestmentResult.success();
        } catch (final ClientErrorException ex) {
            LOGGER.debug("Caught API exception during investment.", ex);
            return InvestmentResult.failure(ex);
        }
    }

    public void cancel(final Investment investment) {
        LOGGER.debug("Cancelling offer to sell investment in loan #{}.", investment.getLoanId());
        controlApi.run(api -> api.cancel(investment.getId()));
    }

    public PurchaseResult purchase(final Participation participation) {
        LOGGER.debug("Purchasing participation #{} in loan #{}.", participation.getId(), participation.getLoanId());
        try {
            controlApi.run(api -> api.purchase(participation.getId(), new PurchaseRequest(participation)));
            return PurchaseResult.success();
        } catch (final ClientErrorException ex) {
            LOGGER.debug("Caught API exception during purchasing.", ex);
            return PurchaseResult.failure(ex);
        }
    }

    public void sell(final Investment investment) {
        LOGGER.debug("Offering to sell investment in loan #{}.", investment.getLoanId());
        controlApi.run(api -> api.offer(new SellRequest(investment)));
    }

    public void accept(final Reservation reservation) {
        final ResolutionRequest r = new ResolutionRequest(reservation.getMyReservation().getId(), Resolution.ACCEPTED);
        final Resolutions rs = new Resolutions(Collections.singleton(r));
        controlApi.run(c -> c.accept(rs));
    }

    /**
     * Retrieve reservations that the user has to either accept or reject.
     * @return All items from the remote API, lazy-loaded.
     */
    public Stream<Reservation> getPendingReservations() {
        return excludeNonCZK(reservationApi.call(ReservationApi::items).getReservations().stream(),
                             Reservation::getCurrency)
                .filter(r -> r.getCurrency().equals(Defaults.CURRENCY));
    }

    /**
     * Retrieve investments from user's portfolio via {@link PortfolioApi}, in a given order.
     * @param select Rules to filter the selection by.
     * @return All items from the remote API, lazy-loaded.
     */
    public Stream<Investment> getInvestments(final Select select) {
        return excludeNonCZK(getStream(portfolioApi, PortfolioApi::items, select), Investment::getCurrency);
    }

    public Stream<Investment> getDelinquentInvestments() {
        return getInvestments(new Select()
                                      .in("loan.status", "ACTIVE", "PAID_OFF")
                                      .equals("loan.unpaidLastInst", "true")
                                      .equals("status", "ACTIVE"));
    }

    public Loan getLoan(final int id) {
        return loanApi.execute(api -> api.item(id));
    }

    public Optional<Investment> getInvestment(final long id) {
        final Select s = new Select().equals("id", id);
        return getInvestments(s).findFirst();
    }

    public Optional<Investment> getInvestmentByLoanId(final int loanId) {
        final Select s = new Select().equals("loan.id", loanId);
        return getInvestments(s).findFirst();
    }

    public LastPublishedLoan getLastPublishedLoanInfo() {
        return loanApi.execute(LoanApi::lastPublished, false);
    }

    /**
     * Retrieve loans from marketplace via {@link LoanApi}.
     * @param select Rules to filter the selection by.
     * @return All items from the remote API, lazy-loaded.
     */
    public Stream<Loan> getAvailableLoans(final Select select) {
        return excludeNonCZK(getStream(loanApi, LoanApi::items, select), Loan::getCurrency);
    }

    /**
     * Retrieve loan collections information via {@link CollectionsApi}.
     * @param loanId Loan in question.
     * @return All items from the remote API, lazy-loaded.
     */
    public Stream<Development> getDevelopments(final int loanId) {
        return getStream(collectionsApi, a -> a.items(loanId));
    }

    /**
     * Retrieve participations from secondary marketplace via {@link ParticipationApi}.
     * @param select Rules to filter the selection by.
     * @return All items from the remote API, lazy-loaded.
     */
    public Stream<Participation> getAvailableParticipations(final Select select) {
        return excludeNonCZK(getStream(participationApi, ParticipationApi::items, select), Participation::getCurrency);
    }

    public ReservationPreferences getReservationPreferences() {
        return reservationApi.call(ReservationApi::preferences);
    }

    public void setReservationPreferences(final ReservationPreferences preferences) {
        controlApi.run(c -> c.setReservationPreferences(preferences));
    }

    public Restrictions getRestrictions() {
        return controlApi.call(ControlApi::restrictions);
    }

    public Statistics getStatistics() {
        return portfolioApi.execute(PortfolioApi::item);
    }

}
