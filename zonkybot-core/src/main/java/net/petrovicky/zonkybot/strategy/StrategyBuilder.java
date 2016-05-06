/*
 * Copyright 2016 Lukáš Petrovický
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.petrovicky.zonkybot.strategy;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import net.petrovicky.zonkybot.remote.Rating;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StrategyBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(StrategyBuilder.class);

    private final Map<Rating, StrategyPerRating> individualStrategies = new EnumMap<>(Rating.class);

    public StrategyBuilder addIndividualStrategy(Rating r, final BigDecimal targetShare, final int minTerm,
                                                 final int maxTerm, final int minAmount, final int maxAmount,
                                                 final boolean preferLongerTerms) {
        if (individualStrategies.containsKey(r)) {
            throw new IllegalArgumentException("Already added strategy for rating " + r);
        }
        individualStrategies.put(r, new StrategyPerRating(r, targetShare, minTerm, maxTerm, minAmount, maxAmount, preferLongerTerms));
        LOGGER.debug("Adding strategy for rating '{}'.", r.getDescription());
        LOGGER.debug("Target share for rating '{}' among total investments is {}.", r.getDescription(), targetShare);
        LOGGER.debug("Range of acceptable investment terms for rating '{}' is <{}, {}> months.", r.getDescription(),
                minTerm == -1 ? 0 : minTerm, maxTerm == -1 ? "+inf" : maxTerm);
        LOGGER.debug("Range of acceptable investment amounts for rating '{}' is <{}, {}> CZK.", r.getDescription(),
                minAmount, maxAmount);
        LOGGER.debug("Rating '{}' will prefer longer terms: ", r.getDescription(), preferLongerTerms);
        return this;
    }

    public InvestmentStrategy build() {
        if (individualStrategies.size() != Rating.values().length) {
            throw new IllegalStateException("Strategy is incomplete.");
        }
        return new InvestmentStrategy(individualStrategies);
    }

}
