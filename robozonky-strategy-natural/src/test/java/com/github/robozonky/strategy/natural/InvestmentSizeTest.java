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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class InvestmentSizeTest {

    private static final int MIN = 400, MAX = 2 * InvestmentSizeTest.MIN;

    @Test
    void regular() {
        final InvestmentSize s = new InvestmentSize(InvestmentSizeTest.MIN, InvestmentSizeTest.MAX);
        assertSoftly(softly -> {
            softly.assertThat(s.getMinimumInvestment()).isEqualTo(Money.from(InvestmentSizeTest.MIN));
            softly.assertThat(s.getMaximumInvestment()).isEqualTo(Money.from(InvestmentSizeTest.MAX));
        });
    }

    @Test
    void switched() {
        final InvestmentSize s = new InvestmentSize(InvestmentSizeTest.MAX, InvestmentSizeTest.MIN);
        assertSoftly(softly -> {
            softly.assertThat(s.getMinimumInvestment()).isEqualTo(Money.from(InvestmentSizeTest.MIN));
            softly.assertThat(s.getMaximumInvestment()).isEqualTo(Money.from(InvestmentSizeTest.MAX));
        });
    }

    @Test
    void omitted() {
        final InvestmentSize s = new InvestmentSize(InvestmentSizeTest.MAX);
        assertSoftly(softly -> {
            softly.assertThat(s.getMinimumInvestment()).isEqualTo(Money.ZERO);
            softly.assertThat(s.getMaximumInvestment()).isEqualTo(Money.from(InvestmentSizeTest.MAX));
        });
    }
}
