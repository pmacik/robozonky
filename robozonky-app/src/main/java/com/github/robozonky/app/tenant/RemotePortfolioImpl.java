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

import com.github.robozonky.api.Money;
import com.github.robozonky.api.remote.entities.RiskPortfolio;
import com.github.robozonky.api.remote.enums.Rating;
import com.github.robozonky.api.strategies.PortfolioOverview;
import com.github.robozonky.internal.async.Reloadable;
import com.github.robozonky.internal.tenant.RemotePortfolio;
import com.github.robozonky.internal.tenant.Tenant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

class RemotePortfolioImpl implements RemotePortfolio {

    private static final Logger LOGGER = LogManager.getLogger(RemotePortfolioImpl.class);
    private final Reloadable<RemoteData> portfolio;
    private final AtomicReference<Map<Integer, Blocked>> syntheticByLoanId =
            new AtomicReference<>(new LinkedHashMap<>(0));
    private final Reloadable<PortfolioOverview> portfolioOverview;
    private final boolean isDryRun;

    public RemotePortfolioImpl(final Tenant tenant) {
        this.isDryRun = tenant.getSessionInfo().isDryRun();
        this.portfolio = Reloadable.with(() -> RemoteData.load(tenant))
                .reloadAfter(Duration.ofMinutes(5))
                .finishWith(this::refreshPortfolio)
                .build();
        this.portfolioOverview = Reloadable.with(() -> (PortfolioOverview) new PortfolioOverviewImpl(this))
                .finishWith(po -> LOGGER.debug("New portfolio overview: {}.", po))
                .reloadAfter(Duration.ofMinutes(5))
                .build();
    }

    private static Money sumOutstanding(final RiskPortfolio portfolio) {
        return portfolio.getDue().add(portfolio.getUnpaid());
    }

    private void refreshPortfolio(final RemoteData data) {
        // remove synthetic charges that are replaced by actual remote blocked amounts
        LOGGER.debug("New remote data: {}.", data);
        LOGGER.debug("Current synthetics: {}.", syntheticByLoanId.get());
        final Map<Integer, Blocked> updatedSynthetics = syntheticByLoanId.updateAndGet(old -> old.entrySet().stream()
                .filter(e -> e.getValue().isValid(data))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        LOGGER.debug("New synthetics: {}.", updatedSynthetics);
        // force overview recalculation now that we have the latest portfolio
        portfolioOverview.clear();
    }

    @Override
    public void simulateCharge(final int loanId, final Rating rating, final Money amount) {
        LOGGER.debug("Current synthetics: {}.", syntheticByLoanId.get());
        final Map<Integer, Blocked> updatedSynthetics = syntheticByLoanId.updateAndGet(old -> {
            final Map<Integer, Blocked> result = new LinkedHashMap<>(old);
            /*
             * synthetic blocked amounts are persistent only during dry runs; otherwise all synthetics will be removed
             * after a remote update of blocked amounts.
             */
            result.put(loanId, new Blocked(loanId, amount, rating, isDryRun));
            return result;
        });
        LOGGER.debug("Synthetic added. New synthetics: {}", updatedSynthetics);
        // force re-fetch of blocked amounts and portfolio data now that we have registered a change
        portfolio.clear();
        portfolioOverview.clear();
    }

    RemoteData getRemotePortfolio() {
        return portfolio.get().getOrElseThrow(t -> new IllegalStateException("Failed fetching remote portfolio.", t));
    }

    @Override
    public Map<Rating, Money> getTotal() {
        final RemoteData data = getRemotePortfolio(); // use the same data for the entirety of this method
        LOGGER.debug("Remote data used: {}.", data);
        final Map<Rating, Money> amounts = data.getStatistics().getRiskPortfolio().stream()
                .collect(Collectors.toMap(RiskPortfolio::getRating,
                                          RemotePortfolioImpl::sumOutstanding,
                                          Money::add, // should not be necessary
                                          () -> new EnumMap<>(Rating.class)));
        LOGGER.debug("Remote portfolio: {}.", amounts);
        data.getBlocked().forEach((r, amount) -> amounts.put(r, amounts.getOrDefault(r, amount.getZero()).add(amount)));
        LOGGER.debug("Plus remote blocked: {}.", amounts);
        syntheticByLoanId.get().values().stream()
                .filter(syntheticBlocked -> syntheticBlocked.isValidInStatistics(data))
                .forEach(syntheticBlocked -> {
                    final Rating r = syntheticBlocked.getRating();
                    final Money zero = syntheticBlocked.getAmount().getZero();
                    amounts.put(r, amounts.getOrDefault(r, zero).add(syntheticBlocked.getAmount()));
                });
        LOGGER.debug("Grand total incl. synthetics: {}.", amounts);
        return Collections.unmodifiableMap(amounts);
    }

    @Override
    public PortfolioOverview getOverview() {
        return portfolioOverview.get()
                .getOrElseThrow(t -> new IllegalStateException("Failed calculating portfolio overview.", t));
    }
}
