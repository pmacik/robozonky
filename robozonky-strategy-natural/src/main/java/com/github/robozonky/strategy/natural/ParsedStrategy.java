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

package com.github.robozonky.strategy.natural;

import com.github.robozonky.api.Money;
import com.github.robozonky.api.Ratio;
import com.github.robozonky.api.remote.enums.Rating;
import com.github.robozonky.api.strategies.*;
import com.github.robozonky.strategy.natural.conditions.MarketplaceFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ParsedStrategy {

    private static final Logger LOGGER = LogManager.getLogger(ParsedStrategy.class);

    private final DefaultValues defaults;
    private final Map<Rating, PortfolioShare> portfolio;
    private final Map<Rating, InvestmentSize> investmentSizes;
    private final FilterSupplier filters;
    private RoboZonkyVersion minimumVersion;

    public ParsedStrategy(final DefaultPortfolio portfolio) {
        this(portfolio, Collections.emptySet());
    }

    ParsedStrategy(final DefaultValues values) {
        this(values, Collections.emptySet(), Collections.emptyMap(), new FilterSupplier(values));
    }

    ParsedStrategy(final DefaultPortfolio portfolio, final Collection<MarketplaceFilter> filters) {
        this(new DefaultValues(portfolio), filters);
    }

    ParsedStrategy(final DefaultValues values, final Collection<MarketplaceFilter> filters) {
        this(values, Collections.emptyList(), Collections.emptyMap(), new FilterSupplier(values, filters));
    }

    ParsedStrategy(final DefaultValues defaults, final Collection<PortfolioShare> portfolio,
                   final Map<Rating, InvestmentSize> investmentSizes) {
        this(defaults, portfolio, investmentSizes, new FilterSupplier(defaults));
    }

    public ParsedStrategy(final DefaultValues defaults, final Collection<PortfolioShare> portfolio,
                          final Map<Rating, InvestmentSize> investmentSizes, final FilterSupplier filters) {
        this.defaults = defaults;
        this.portfolio = portfolio.isEmpty() ? Collections.emptyMap() :
                new EnumMap<>(portfolio.stream().
                        collect(Collectors.toMap(PortfolioShare::getRating, Function.identity())));
        this.investmentSizes = investmentSizes.isEmpty() ? Collections.emptyMap() : new EnumMap<>(investmentSizes);
        this.filters = filters;
        final long shareSum = sumMinimalShares();
        if (shareSum > 100) {
            throw new IllegalArgumentException("Sum of minimal rating shares in portfolio is over 100 %.");
        } else if (shareSum < 100) {
            LOGGER.info("Sum of minimal rating shares in the portfolio is less than 100 %.");
        }
    }

    private static boolean matchesFilter(final Wrapper<?> item, final Collection<MarketplaceFilter> filters,
                                         final String logMessage) {
        return filters.stream()
                .filter(f -> f.test(item))
                .peek(f -> Audit.LOGGER.debug(logMessage, item, f))
                .findFirst()
                .isPresent();
    }

    private long sumMinimalShares() {
        return Math.round(Stream.of(Rating.values())
                .map(this::getMinimumShare)
                .map(Ratio::asPercentage)
                .mapToDouble(BigDecimal::doubleValue)
                .sum());
    }

    public int getMinimumInvestmentShareInPercent() {
        return defaults.getInvestmentShare().getMinimumShareInPercent();
    }

    public int getMaximumInvestmentShareInPercent() {
        return defaults.getInvestmentShare().getMaximumShareInPercent();
    }

    public Money getMaximumInvestmentSize() {
        return defaults.getTargetPortfolioSize();
    }

    public Optional<RoboZonkyVersion> getMinimumVersion() {
        return Optional.ofNullable(minimumVersion);
    }

    public void setMinimumVersion(final RoboZonkyVersion minimumVersion) {
        this.minimumVersion = minimumVersion;
    }

    public Ratio getMinimumShare(final Rating rating) {
        if (portfolio.containsKey(rating)) {
            return portfolio.get(rating).getMininum();
        } else { // no minimum share specified; use the one from default portfolio
            return defaults.getPortfolio().getDefaultShare(rating);
        }
    }

    public Ratio getMaximumShare(final Rating rating) {
        if (portfolio.containsKey(rating)) {
            return portfolio.get(rating).getMaximum();
        } else { // no maximum share specified; calculate minimum share and use it as maximum too
            return this.getMinimumShare(rating);
        }
    }

    private InvestmentSize getInvestmentSize(final Rating rating) {
        return investmentSizes.getOrDefault(rating, defaults.getInvestmentSize());
    }

    public Money getMinimumInvestmentSize(final Rating rating) {
        return getInvestmentSize(rating).getMinimumInvestment();
    }

    public Money getMaximumInvestmentSize(final Rating rating) {
        return getInvestmentSize(rating).getMaximumInvestment();
    }

    private <T> Stream<T> getApplicable(final Stream<Wrapper<T>> wrappers) {
        var loanFilters = filters.getPrimaryMarketplaceFilters();
        var investmentFilters = filters.getSellFilters();
        return wrappers
                .filter(w -> !matchesFilter(w, loanFilters, "{} skipped due to primary marketplace filter {}."))
                .filter(w -> !matchesFilter(w, investmentFilters, "{} skipped due to sell filter {}."))
                .map(Wrapper::getOriginal);
    }

    public Stream<LoanDescriptor> getApplicableLoans(final Collection<LoanDescriptor> l,
                                                     final PortfolioOverview portfolioOverview) {
        return getApplicable(l.parallelStream().map(d -> Wrapper.wrap(d, portfolioOverview)));
    }

    public Stream<ReservationDescriptor> getApplicableReservations(final Collection<ReservationDescriptor> r,
                                                                   final PortfolioOverview portfolioOverview) {
        return getApplicable(r.parallelStream().map(d -> Wrapper.wrap(d, portfolioOverview)));
    }

    public Stream<ParticipationDescriptor> getApplicableParticipations(final Collection<ParticipationDescriptor> p,
                                                                       final PortfolioOverview portfolioOverview) {
        var participationFilters = filters.getSecondaryMarketplaceFilters();
        var sellFilters = filters.getSellFilters();
        return p.parallelStream()
                .map(d -> Wrapper.wrap(d, portfolioOverview))
                .filter(w -> !matchesFilter(w, participationFilters, "{} skipped due to secondary marketplace filter {}."))
                .filter(w -> !matchesFilter(w, sellFilters, "{} skipped due to sell filter {}."))
                .map(Wrapper::getOriginal);
    }

    public boolean isPurchasingEnabled() {
        return filters.isSecondaryMarketplaceEnabled();
    }

    public boolean isInvestingEnabled() {
        return filters.isPrimaryMarketplaceEnabled();
    }

    public Optional<ReservationMode> getReservationMode() {
        return defaults.getReservationMode();
    }

    public Optional<SellingMode> getSellingMode() {
        return defaults.getSellingMode();
    }

    public Stream<InvestmentDescriptor> getMatchingSellFilters(final Collection<InvestmentDescriptor> i,
                                                               final PortfolioOverview portfolioOverview) {
        var investmentFilters = filters.getSellFilters();
        return i.parallelStream()
                .map(d -> Wrapper.wrap(d, portfolioOverview))
                .filter(w -> matchesFilter(w, investmentFilters, "{} to be sold due to sell filter {}."))
                .map(Wrapper::getOriginal);
    }

    public Stream<InvestmentDescriptor> getMatchingPrimaryMarketplaceFilters(final Collection<InvestmentDescriptor> i,
                                                                             final PortfolioOverview portfolioOverview) {
        var loanFilters = filters.getPrimaryMarketplaceFilters();
        return i.parallelStream()
                .map(d -> Wrapper.wrap(d, portfolioOverview))
                .filter(w -> matchesFilter(w, loanFilters, "{} sellable due to primary marketplace filter {}."))
                .map(Wrapper::getOriginal);
    }

    @Override
    public String toString() {
        return "ParsedStrategy{" +
                "defaults=" + defaults +
                ", investmentSizes=" + investmentSizes +
                ", minimumVersion=" + minimumVersion +
                ", portfolio=" + portfolio +
                ", filters=" + filters +
                '}';
    }
}
