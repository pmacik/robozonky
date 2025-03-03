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

import org.apache.http.ConnectionClosedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.ProcessingException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

class Api<T> {

    private static final Logger LOGGER = LogManager.getLogger(Api.class);
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

    private final T proxy;
    private final RequestCounter counter;

    Api(final T proxy) {
        this(proxy, null);
    }

    public Api(final T proxy, final RequestCounter counter) {
        this.proxy = proxy;
        this.counter = counter;
    }

    private static boolean isConnectionIssue(final Throwable throwable) {
        if (throwable == null) {
            return false;
        } else if (throwable instanceof SocketTimeoutException || throwable instanceof ConnectionClosedException) {
            return true;
        } else {
            return isConnectionIssue(throwable.getCause());
        }
    }

    private static String assembleLogMessage(final long id, final RequestCounter counter) {
        final int count1 = counter.count(ONE_MINUTE);
        final int count5 = counter.count(FIVE_MINUTES);
        return "... done. (Request #" + id + ", " + count1 + " in last 60 sec., " + count5 + " in last 5 min.)";
    }

    private static <Y, Z> Z call(final Function<Y, Z> function, final Y proxy, final RequestCounter counter,
                                 final int attemptNo) {
        LOGGER.trace("Executing...");
        try {
            return function.apply(proxy);
        } catch (final ProcessingException ex) {
            if (!isConnectionIssue(ex)) { // nothing to retry
                throw new ProcessingException("Operation failed and can not be retried.", ex);
            } else if (attemptNo > 2) { // no longer retry
                throw new ProcessingException("Operation failed even after " + attemptNo + " retries.", ex);
            }
            LOGGER.debug("Caught socket timeout. Retry #{} starting.", attemptNo, ex);
            return call(function, proxy, counter, attemptNo + 1);
        } finally {
            if (counter != null) {
                final long id = counter.mark();
                LOGGER.trace(() -> assembleLogMessage(id, counter));
            } else {
                LOGGER.trace("... done. (Not counting towards the API quota.)");
            }
        }
    }

    static <Y, Z> Z call(final Function<Y, Z> function, final Y proxy, final RequestCounter counter) {
        return call(function, proxy, counter, 1);
    }

    <S> S call(final Function<T, S> function) {
        return call(function, proxy, counter);
    }

    void run(final Consumer<T> consumer) {
        final Function<T, Boolean> wrapper = t -> {
            consumer.accept(t);
            return false; // we need to return something
        };
        call(wrapper);
    }
}
