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

import java.net.URL;
import java.util.Optional;
import java.util.function.Function;

import com.github.robozonky.internal.async.Refreshable;
import com.github.robozonky.internal.util.StringUtil;
import com.github.robozonky.internal.util.UrlUtil;
import io.vavr.control.Try;

class RefreshableStrategy extends Refreshable<String> {

    private final URL url;

    protected RefreshableStrategy(final String target) {
        this(UrlUtil.toURL(target));
    }

    private RefreshableStrategy(final URL target) {
        this.url = target;
    }

    @Override
    protected String getLatestSource() {
        return Try.withResources(() -> UrlUtil.open(url))
                .of(StringUtil::toString)
                .getOrElseThrow((Function<Throwable, IllegalStateException>) IllegalStateException::new);
    }

    @Override
    protected Optional<String> transform(final String source) {
        return Optional.of(source);
    }
}
