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

package com.github.robozonky.api.remote.entities;

import com.github.robozonky.api.remote.enums.OAuthScope;
import com.github.robozonky.api.remote.enums.OAuthScopes;
import com.github.robozonky.internal.test.DateUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OAuth access token for Zonky API.
 * <p>
 * Knowledge of this token will allow anyone to access the service as if they were the authenticated user. This is
 * therefore highly sensitive information and should never be kept in memory for longer than necessary.
 */
@XmlRootElement(name = "token")
@XmlAccessorType(XmlAccessType.FIELD)
public class ZonkyApiToken extends BaseEntity {

    public static final String REFRESH_TOKEN_STRING = "refresh_token";
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);
    @XmlTransient
    private final long id = ID_GENERATOR.getAndIncrement();
    @XmlElement(name = "access_token")
    private char[] accessToken;
    @XmlElement(name = REFRESH_TOKEN_STRING)
    private char[] refreshToken;
    @XmlElement(name = "token_type")
    private String type;
    @XmlElement
    private OAuthScopes scope;
    @XmlElement(name = "expires_in")
    private int expiresIn;
    /**
     * This is not part of the Zonky API, but it will be useful inside RoboZonky.
     */
    @XmlTransient
    private OffsetDateTime obtainedOn = DateUtil.offsetNow();

    ZonkyApiToken() {
        // fox JAXB
    }

    public ZonkyApiToken(final String accessToken, final String refreshToken, final OffsetDateTime obtainedOn) {
        this(accessToken, refreshToken, 299, obtainedOn, REFRESH_TOKEN_STRING);
    }

    public ZonkyApiToken(final String accessToken, final String refreshToken, final int expiresIn) {
        this(accessToken, refreshToken, expiresIn, DateUtil.offsetNow(), REFRESH_TOKEN_STRING);
    }

    public ZonkyApiToken(final String accessToken, final String refreshToken, final int expiresIn,
                         final OAuthScope scope) {
        this(accessToken, refreshToken, expiresIn, DateUtil.offsetNow(), REFRESH_TOKEN_STRING, scope);
    }

    public ZonkyApiToken(final String accessToken, final String refreshToken, final int expiresIn,
                         final OffsetDateTime obtainedOn) {
        this(accessToken, refreshToken, expiresIn, obtainedOn, REFRESH_TOKEN_STRING);
    }

    public ZonkyApiToken(final String accessToken, final String refreshToken, final int expiresIn,
                         final OffsetDateTime obtainedOn, final String type, final OAuthScope... scope) {
        this.accessToken = accessToken.toCharArray();
        this.refreshToken = refreshToken.toCharArray();
        this.expiresIn = expiresIn;
        this.type = type;
        this.scope = scope.length == 0 ? OAuthScopes.of() : OAuthScopes.of(scope);
        this.obtainedOn = obtainedOn;
    }

    public static ZonkyApiToken unmarshal(final String token) {
        try {
            final JAXBContext ctx = JAXBContext.newInstance(ZonkyApiToken.class);
            final Unmarshaller u = ctx.createUnmarshaller();
            return (ZonkyApiToken) u.unmarshal(new StringReader(token));
        } catch (final JAXBException ex) {
            throw new IllegalStateException("Failed unmarshalling Zonky API token.", ex);
        }
    }

    /**
     * Requires {@link CharArrayAdapter} to work properly, otherwise the char[] is represented as a sequence of
     * character elements.
     */
    public static String marshal(final ZonkyApiToken token) {
        try {
            final JAXBContext ctx = JAXBContext.newInstance(ZonkyApiToken.class);
            final Marshaller m = ctx.createMarshaller();
            final StringWriter w = new StringWriter();
            m.marshal(token, w);
            return w.toString();
        } catch (final JAXBException ex) {
            throw new IllegalStateException("Failed marshalling Zonky API token.", ex);
        }
    }

    public long getId() {
        return id;
    }

    public char[] getAccessToken() {
        return accessToken;
    }

    public char[] getRefreshToken() {
        return refreshToken;
    }

    public String getType() {
        return type;
    }

    /**
     * Interval in seconds in which the token will expire.
     * @return Time left before token expiration, in seconds, at the time token was retrieved.
     */
    public int getExpiresIn() {
        return expiresIn;
    }

    public boolean isExpired() {
        return willExpireIn(Duration.ZERO);
    }

    public OAuthScopes getScope() {
        return scope;
    }

    public OffsetDateTime getObtainedOn() {
        return obtainedOn;
    }

    public OffsetDateTime getExpiresOn() {
        return obtainedOn.plus(Duration.ofSeconds(expiresIn));
    }

    public boolean willExpireIn(final TemporalAmount temporalAmount) {
        final OffsetDateTime maxExpirationDate = DateUtil.offsetNow().plus(temporalAmount);
        return getExpiresOn().isBefore(maxExpirationDate);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !Objects.equals(getClass(), o.getClass())) {
            return false;
        }
        final ZonkyApiToken that = (ZonkyApiToken) o;
        if (Arrays.equals(accessToken, that.accessToken)) {
            if (Arrays.equals(refreshToken, that.refreshToken)) {
                return Objects.equals(scope, that.scope);
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessToken, refreshToken, scope);
    }
}
