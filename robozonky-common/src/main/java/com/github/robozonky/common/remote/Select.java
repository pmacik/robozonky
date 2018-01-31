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

package com.github.robozonky.common.remote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Allows to filter Zonky API requests based on the values of individual fields.
 * @see <~ href="https://zonky.docs.apiary.io/#introduction/pagination,-sorting-and-filtering">Zonky API docs.</~>
 */
public class Select implements Consumer<RoboZonkyFilter> {

    private final Map<String, List<Object>> conditions = new LinkedHashMap<>(0);

    private void addObjects(final String field, final String operation, final Object... value) {
        final String key = field + "__" + operation;
        conditions.compute(key, (k, v) -> {
            final String val = Stream.of(value).map(Object::toString)
                    .collect(Collectors.joining("\",\"", "[\"", "\"]"));
            final List<Object> result = (v == null) ? new ArrayList<>(1) : v;
            result.add(val);
            return result;
        });
    }

    private void addLongs(final String field, final String operation, final long... value) {
        final String key = field + "__" + operation;
        conditions.compute(key, (k, v) -> {
            final String val = LongStream.of(value).mapToObj(String::valueOf)
                    .collect(Collectors.joining("\",\"", "[\"", "\"]"));
            final List<Object> result = (v == null) ? new ArrayList<>(1) : v;
            result.add(val);
            return result;
        });
    }

    private void addObject(final String field, final String operation, final Object value) {
        final String key = field + "__" + operation;
        conditions.compute(key, (k, v) -> {
            final List<Object> result = (v == null) ? new ArrayList<>(1) : v;
            result.add(value);
            return result;
        });
    }

    private void addLong(final String field, final String operation, final long value) {
        addObject(field, operation, String.valueOf(value));
    }

    public Select contains(final String field, final String value) {
        addObject(field, "contains", value);
        return this;
    }

    public Select containsCaseInsensitive(final String field, final String value) {
        addObject(field, "icontains", value);
        return this;
    }

    public Select startsWith(final String field, final String value) {
        addObject(field, "startswith", value);
        return this;
    }

    public Select startsWithCaseInsensitive(final String field, final String value) {
        addObject(field, "istartswith", value);
        return this;
    }

    public Select endsWith(final String field, final String value) {
        addObject(field, "endswith", value);
        return this;
    }

    public Select endsWithCaseInsensitive(final String field, final String value) {
        addObject(field, "iendswith", value);
        return this;
    }

    public Select in(final String field, final String... values) {
        addObjects(field, "in", (Object[]) values);
        return this;
    }

    public Select containsAll(final String field, final String... values) {
        addObjects(field, "containsall", (Object[]) values);
        return this;
    }

    public Select containsAny(final String field, final String... values) {
        addObjects(field, "containsany", (Object[]) values);
        return this;
    }

    public Select in(final String field, final long... values) {
        addLongs(field, "in", values);
        return this;
    }

    public Select containsAll(final String field, final long... values) {
        addLongs(field, "containsall", values);
        return this;
    }

    public Select containsAny(final String field, final long... values) {
        addLongs(field, "containsany", values);
        return this;
    }

    public Select equals(final String field, final Object value) {
        addObject(field, "eq", value);
        return this;
    }

    public Select notEquals(final String field, final Object value) {
        addObject(field, "noteq", value);
        return this;
    }

    public Select greaterThan(final String field, final long value) {
        addLong(field, "gt", value);
        return this;
    }

    public Select greaterThanOrEquals(final String field, final long value) {
        addLong(field, "gte", value);
        return this;
    }

    public Select greaterThanOrNull(final String field, final long value) {
        addLong(field, "gteornull", value);
        return this;
    }

    public Select lessThan(final String field, final long value) {
        addLong(field, "lt", value);
        return this;
    }

    public Select lessThanOrEquals(final String field, final long value) {
        addLong(field, "lte", value);
        return this;
    }

    public Select lessThanOrNull(final String field, final long value) {
        addLong(field, "lteornull", value);
        return this;
    }

    @Override
    public void accept(final RoboZonkyFilter roboZonkyFilter) {
        conditions.forEach((k, v) -> v.forEach(r -> {
            roboZonkyFilter.setQueryParam(k, r);
        }));
    }
}
