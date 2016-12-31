/*
 * Copyright 2016 Lukáš Petrovický
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

package com.github.triceo.robozonky.app;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.github.triceo.robozonky.api.Defaults;
import com.github.triceo.robozonky.api.State;
import com.github.triceo.robozonky.api.remote.Api;
import com.github.triceo.robozonky.api.remote.entities.Loan;
import com.github.triceo.robozonky.app.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides whether or not the application should fall asleep because of general marketplace inactivity. Uses two sources
 * of data to make the decision: the marketplace, and the app's internal state concerning the last time the marketplace
 * was checked.
 *
 * In order for the state to be persisted, the App needs to eventually call {@link #settle()} after calling
 * {@link #shouldSleep()}.
 */
class Activity {

    /**
     * Simple abstraction over {@link Api#getLoans()} which provides additional intelligence. Use {@link #from(Api)} as
     * entry point to the API.
     */
    private static class Marketplace {

        /**
         * Instantiate the marketplace.
         *
         * @param api Remote API from which to load all loans.
         * @return Marketplace backed by the API.
         */
        public static Activity.Marketplace from(final Api api) {
            return new Activity.Marketplace(api.getLoans());
        }

        private final List<Loan> recentLoansDescending;

        private Marketplace(final Collection<Loan> loans) { // Zotify occasionally returns null loans for unknown reason
            this.recentLoansDescending = loans == null ? Collections.emptyList() :
                    Collections.unmodifiableList(loans.stream()
                            .filter(l -> l.getRemainingInvestment() > 0)
                            .sorted(Comparator.comparing(Loan::getDatePublished).reversed())
                            .collect(Collectors.toList()));
        }

        /**
         * Retrieve all loans in the marketplace which have not yet been fully funded and which have been published at
         * least a certain time ago.
         * @param delay How long ago at the very least should the loans have been published.
         * @return Ordered by publishing time descending.
         */
        public List<Loan> getLoansOlderThan(final TemporalAmount delay) {
            return this.recentLoansDescending.stream()
                    .filter(l -> OffsetDateTime.now().isAfter(l.getDatePublished().plus(delay)))
                    .collect(Collectors.toList());
        }

        /**
         * Retrieve all loans in the marketplace which have not yet been fully funded and which have been published past
         * a certain point in time.
         * @param instant The earliest point in time for the loans to published on.
         * @return Ordered by publishing time descending.
         */
        public List<Loan> getLoansNewerThan(final OffsetDateTime instant) {
            return this.recentLoansDescending.stream()
                    .filter(l -> l.getDatePublished().isAfter(instant))
                    .collect(Collectors.toList());
        }

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Activity.class);
    private static final OffsetDateTime EPOCH = OffsetDateTime.ofInstant(Instant.EPOCH, Defaults.ZONE_ID);
    static final State.ClassSpecificState STATE = State.INSTANCE.forClass(Activity.class);
    static final String LAST_MARKETPLACE_CHECK_STATE_ID = "lastMarketplaceCheck";

    private final TemporalAmount closedSeason, sleepInterval;
    private final Activity.Marketplace marketplace;
    private Runnable settler = null;

    Activity(final Configuration ctx, final Api api) {
        this.closedSeason = ctx.getCaptchaDelay();
        this.sleepInterval = ctx.getSleepPeriod();
        this.marketplace = Activity.Marketplace.from(api);
    }

    private OffsetDateTime getLatestMarketplaceAction() {
        final Optional<String> timestamp = Activity.STATE.getValue(Activity.LAST_MARKETPLACE_CHECK_STATE_ID);
        return timestamp.map(s -> {
            try {
                return OffsetDateTime.parse(s);
            } catch (final DateTimeParseException ex) {
                Activity.LOGGER.debug("Failed read marketplace timestamp.", ex);
                return Activity.EPOCH;
            }
        }).orElse(Activity.EPOCH);
    }

    public List<Loan> getUnactionableLoans() {
        return this.marketplace.getLoansNewerThan(
                OffsetDateTime.now().minus(this.closedSeason)
        );
    }

    /**
     * Retrieves loans that are available for robotic investing, ie. not protected by CAPTCHA.
     *
     * @return Loans ordered by their time of publishing, descending.
     */
    public List<Loan> getAvailableLoans() {
        return this.marketplace.getLoansOlderThan(this.closedSeason);
    }

    /**
     * Whether or not the application should fall asleep and not make any further contact with API.
     *
     * @return True if no further contact should be made during this run of the app.
     */
    public boolean shouldSleep() {
        final OffsetDateTime lastKnownAction = this.getLatestMarketplaceAction();
        final boolean hasUnactionableLoans = !this.getUnactionableLoans().isEmpty();
        Activity.LOGGER.debug("Marketplace last checked on {}, has un-actionable loans: {}.", lastKnownAction,
                hasUnactionableLoans);
        boolean shouldSleep = true;
        if (!this.marketplace.getLoansNewerThan(lastKnownAction).isEmpty()) {
            // try investing since there are loans we haven't seen yet
            Activity.LOGGER.debug("Will not sleep due to new loans.");
            shouldSleep = false;
        } else if (lastKnownAction.plus(this.sleepInterval).isBefore(OffsetDateTime.now())) {
            // try investing since we haven't tried in a while; maybe we have some more funds now
            Activity.LOGGER.debug("Will not sleep due to already sleeping too much.");
            shouldSleep = false;
        }
        synchronized (this) { // do not allow concurrent modification of the settler variable
            if (this.settler != null) {
                Activity.LOGGER.warn("Scrapping unsettled activity.");
            }
            if (!shouldSleep || hasUnactionableLoans) {
                /*
                 * only persist (= change marketplace check timestamp) when we're intending to execute some actual
                 * investing.
                 */
                this.settler = () -> this.persist(hasUnactionableLoans);
            } else {
                this.settler = null;
            }
        }
        return shouldSleep;
    }

    /**
     * Persists the new marketplace state following a {@link #shouldSleep()} call.
     */
    public synchronized void settle() {
        if (this.settler == null) {
            Activity.LOGGER.debug("No activity to settle.");
        } else {
            this.settler.run();
            this.settler = null;
        }
    }

    private void persist(final boolean hasUnactionableLoans) {
        // make sure the unactionable loans are never included in the time the marketplace was last checked
        final OffsetDateTime result = hasUnactionableLoans ?
                OffsetDateTime.now().minus(Duration.from(this.closedSeason).plus(Duration.ofSeconds(30)))
                : OffsetDateTime.now();
        if (hasUnactionableLoans) {
            Activity.LOGGER.debug("New marketplace last checked time placed before beginning of closed season: {}.",
                    result);
        } else {
            Activity.LOGGER.debug("New marketplace last checked time is {}.", result);
        }
        Activity.STATE.setValue(Activity.LAST_MARKETPLACE_CHECK_STATE_ID, result.toString());
    }
}
