/*
 * Copyright 2017 The RoboZonky Project
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

package com.github.robozonky.common.remote;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.github.robozonky.api.remote.LoanApi;
import com.github.robozonky.api.remote.entities.Loan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.SoftAssertions.*;
import static org.mockito.Mockito.*;

class EntitySpliteratorTest {

    @Test
    void empty() {
        final EntitySpliterator<Loan> e = new EntitySpliterator<>(Collections.emptyList());
        assertSoftly(softly -> {
            softly.assertThat(e.hasCharacteristics(Spliterator.IMMUTABLE)).isTrue(); // no way to add data
            softly.assertThat(e.hasCharacteristics(Spliterator.NONNULL)).isTrue();
            softly.assertThat(e.hasCharacteristics(Spliterator.SIZED)).isTrue();
            softly.assertThat(e.hasCharacteristics(Spliterator.DISTINCT)).isFalse();
            softly.assertThat(e.hasCharacteristics(Spliterator.CONCURRENT)).isFalse();
        });
        assertSoftly(softly -> {
            softly.assertThat(e.trySplit()).isSameAs(Spliterators.emptySpliterator());
            softly.assertThat(e.getExactSizeIfKnown()).isEqualTo(0);
            softly.assertThat(e.tryAdvance(null)).isFalse();
        });
    }

    private static <Q> PaginatedResult<Q> getResult(final Collection<Q> items, final int pageId,
                                                    final int totalResults) {
        return new PaginatedResult<Q>(items, pageId, totalResults);
    }

    @Test
    void twoPages() {
        final int pageSize = 2;
        final int totalResultCount = 3;
        final Loan loan1 = new Loan(1, 200);
        final Loan loan2 = new Loan(2, 300);
        final Loan loan3 = new Loan(3, 400);
        final PaginatedApi<Loan, LoanApi> api = mock(PaginatedApi.class);
        when(api.execute(any(), any(), any(), eq(0),
                         eq(pageSize)))
                .thenReturn(EntitySpliteratorTest.getResult(Arrays.asList(loan1, loan2), 0, totalResultCount));
        when(api.execute(any(), any(), any(), eq(1),
                         eq(pageSize)))
                .thenReturn(EntitySpliteratorTest.getResult(Collections.singleton(loan3), 1, totalResultCount));
        final Paginated<Loan> p = new PaginatedImpl<>(api, new Select(), Sort.unspecified(), 2);
        final EntitySpliterator<Loan> e = new EntitySpliterator<>(p);
        assertThat(e.getExactSizeIfKnown()).isEqualTo(totalResultCount);
        final Stream<Loan> s = StreamSupport.stream(e, false);
        assertThat(s.distinct().collect(Collectors.toList()))
                .containsExactly(loan1, loan2, loan3);
        assertThat(e.getExactSizeIfKnown()).isEqualTo(0);
    }
}
