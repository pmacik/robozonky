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

package com.github.robozonky.internal.async;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.LongAccumulator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class ThreadPoolExecutorBasedSchedulerTest {

    @AfterEach
    void shutdown() {
        Tasks.closeAll();
    }

    @Test
    void repeating() throws Exception {
        final ScheduledExecutorService s1 = Executors.newSingleThreadScheduledExecutor();
        final ExecutorService s2 = Executors.newCachedThreadPool();
        final LongAccumulator accumulator = new LongAccumulator(Long::sum, 0);
        final Runnable r = () -> accumulator.accumulate(1);
        try (final Scheduler s = new ThreadPoolExecutorBasedScheduler(s1, s2, () -> {
            s1.shutdown();
            s2.shutdown();
        })) {
            final ScheduledFuture<?> f = s.submit(r, Duration.ofMillis(1));
            assertThat((Future)f).isNotNull();
            assertTimeout(Duration.ofSeconds(1), (ThrowingSupplier<?>)f::get);
        }
        assertThat(accumulator.longValue()).isGreaterThanOrEqualTo(1);
    }

}
