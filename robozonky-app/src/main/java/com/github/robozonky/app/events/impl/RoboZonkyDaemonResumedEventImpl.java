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

package com.github.robozonky.app.events.impl;

import java.time.OffsetDateTime;

import com.github.robozonky.api.notifications.RoboZonkyDaemonResumedEvent;

final class RoboZonkyDaemonResumedEventImpl extends AbstractEventImpl implements RoboZonkyDaemonResumedEvent {

    private final OffsetDateTime unavailableSince;
    private final OffsetDateTime unavailableUntil;

    public RoboZonkyDaemonResumedEventImpl(final OffsetDateTime since, final OffsetDateTime until) {
        this.unavailableSince = since;
        this.unavailableUntil = until;
    }

    @Override
    public OffsetDateTime getUnavailableSince() {
        return unavailableSince;
    }

    @Override
    public OffsetDateTime getUnavailableUntil() {
        return unavailableUntil;
    }
}
