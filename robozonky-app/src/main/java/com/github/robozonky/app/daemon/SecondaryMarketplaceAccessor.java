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

import com.github.robozonky.api.remote.enums.LoanHealthInfo;
import com.github.robozonky.api.strategies.ParticipationDescriptor;
import com.github.robozonky.app.tenant.PowerTenant;
import com.github.robozonky.internal.remote.Select;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class SecondaryMarketplaceAccessor implements MarketplaceAccessor<ParticipationDescriptor> {

    private static final Logger LOGGER = Audit.purchasing();

    private final PowerTenant tenant;
    private final UnaryOperator<long[]> stateAccessor;
    private final ToLongFunction<ParticipationDescriptor> identifier;
    private final AtomicReference<Collection<ParticipationDescriptor>> marketplace = new AtomicReference<>();

    public SecondaryMarketplaceAccessor(final PowerTenant tenant, final UnaryOperator<long[]> stateAccessor,
                                        final ToLongFunction<ParticipationDescriptor> identifier) {
        this.tenant = tenant;
        this.stateAccessor = stateAccessor;
        this.identifier = identifier;
    }

    private static boolean contains(final long toFind, final long... original) {
        for (final long j : original) {
            if (j == toFind) {
                return true;
            }
        }
        return false;
    }

    static boolean hasAdditions(final long[] current, final long... original) {
        if (current.length == 0) {
            return false;
        } else if (current.length > original.length) {
            return true;
        }
        for (final long i : current) {
            final boolean found = contains(i, original);
            if (!found) {
                return true;
            }
        }
        return false;
    }

    /**
     * In order to not have to run the strategy over a marketplace and save CPU cycles, we need to know if the
     * marketplace changed since the last time this method was called.
     * @param marketplace Present contents of the marketplace.
     * @return Returning true triggers evaluation of the strategy.
     */
    private boolean hasMarketplaceUpdates(final Collection<ParticipationDescriptor> marketplace) {
        final long[] idsFromMarketplace = marketplace.stream().mapToLong(identifier).toArray();
        final long[] presentWhenLastChecked = stateAccessor.apply(idsFromMarketplace);
        return hasAdditions(idsFromMarketplace, presentWhenLastChecked);
    }

    private Stream<ParticipationDescriptor> readMarketplace() {
        final Select s = new Select()
                .equalsPlain("willNotExceedLoanInvestmentLimit", "true")
                .lessThanOrEquals("remainingPrincipal", tenant.getKnownBalanceUpperBound().getValue().longValue());
        final SoldParticipationCache cache = SoldParticipationCache.forTenant(tenant);
        return tenant.call(zonky -> zonky.getAvailableParticipations(s))
                .filter(p -> p.getLoanHealthInfo() == LoanHealthInfo.HEALTHY) // TODO enable discounted (=overdue)
                .filter(p -> { // never re-purchase what was once sold
                    final int loanId = p.getLoanId();
                    if (cache.wasOnceSold(loanId)) {
                        LOGGER.debug("Loan #{} already sold before, ignoring.", loanId);
                        return false;
                    } else {
                        return true;
                    }
                })
                .map(p -> new ParticipationDescriptor(p, () -> tenant.getLoan(p.getLoanId())));
    }

    @Override
    public Collection<ParticipationDescriptor> getMarketplace() {
        return marketplace.updateAndGet(old -> {
            if (old != null) {
                return old;
            }
            return readMarketplace().collect(Collectors.toList());
        });
    }

    @Override
    public boolean hasUpdates() {
        return hasMarketplaceUpdates(getMarketplace());
    }
}
