/*
 * Copyright 2018 The RoboZonky Project
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

package com.github.robozonky.app.runtime;

import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;

import com.github.robozonky.common.async.Refreshable;
import com.github.robozonky.common.async.Scheduler;
import com.github.robozonky.common.remote.ApiProvider;
import com.github.robozonky.internal.api.Defaults;
import io.vavr.control.Try;
import org.apache.commons.io.IOUtils;

/**
 * Periodically queries remote Zonky API, checking whether it's accessible.
 */
class LivenessCheck extends Refreshable<String> {

    private static final String ZONKY_VERSION_URL = ApiProvider.ZONKY_URL + "/version";

    private final String url;

    private LivenessCheck(final RefreshListener<String> listener) {
        this(ZONKY_VERSION_URL, listener);
    }

    LivenessCheck(final String url, final RefreshListener<String> listener) {
        super(listener);
        this.url = url;
    }

    LivenessCheck(final String url) {
        this.url = url;
    }

    public static void setup(final MainControl mainThreadControl) {
        final Refreshable<String> liveness = new LivenessCheck(mainThreadControl);
        Scheduler.inBackground().submit(liveness, Duration.ofSeconds(5));
    }

    @Override
    protected String getLatestSource() {
        LOGGER.trace("Running.");
        // need to send parsed version, since the object itself changes every time due to currentApiTime field
        return Try.withResources(() -> new URL(url).openStream())
                .of(s -> {
                    final String source = IOUtils.readLines(s, Defaults.CHARSET).stream()
                            .collect(Collectors.joining(System.lineSeparator()));
                    LOGGER.trace("API info coming from Zonky: {}.", source);
                    final ApiVersion version = ApiVersion.read(source);
                    // need to send parsed version, since the object itself changes every time due to
                    // currentApiTime field
                    return version.getBuildVersion();
                })
                .getOrElseGet(ex -> {
                    // don't propagate this exception as it is likely to happen and the calling code would WARN about it
                    LOGGER.debug("Zonky servers are likely unavailable.", ex);
                    return null; // will fail during transform()
                });
    }

    @Override
    protected Optional<String> transform(final String source) {
        return Optional.ofNullable(source);
    }
}
