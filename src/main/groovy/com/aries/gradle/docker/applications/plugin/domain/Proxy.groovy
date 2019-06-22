/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aries.gradle.docker.applications.plugin.domain

import org.gradle.api.GradleException

import javax.annotation.Nullable

/**
 *
 *  Represents a proxy object we'll use to configure nginx.
 *
 */
class Proxy implements Comparable<Proxy> {

    String method
    Integer fromPort
    Integer toPort
    final List<String> matchers = []

    // method('ip_hash')
    Proxy method(String requestedMethod) {
        if (requestedMethod) {
            if (!requestedMethod.endsWith(";")) {
                requestedMethod += ";"
            }
            this.method = requestedMethod
        }
        this
    }

    Proxy expose(String to) {
        expose(null, Integer.valueOf(to))
    }

    Proxy expose(Integer to) {
        expose(null, to)
    }

    Proxy expose(String from, String to) {
        Integer fromInt = (from != null ? Integer.valueOf(from) : null)
        Integer toInt = Integer.valueOf(to)
        expose(fromInt, toInt)
    }

    Proxy expose(@Nullable Integer from, Integer to) {
        if (from != null && from < 0) { from = 0 }
        if (to == null || to < 0) { throw new GradleException("Value for 'to' port must be non-null and greater than zero: to='${to}'") }

        this.fromPort = from
        this.toPort = to

        this
    }

    // matching("^(myAppThatStarted|myOtherAppThatStarted)$")
    Proxy matching(final String regex) {
        if (regex?.trim()) {
            matchers.add(regex)
        }
        this
    }

    @Override
    int compareTo(Proxy obj) {
        final Integer localFromPort = (this.fromPort == null || this.fromPort < 0) ? 0 : this.fromPort
        final Integer remoteFromPort = (obj.fromPort == null || obj.fromPort < 0) ? 0 : obj.fromPort
        if (localFromPort < remoteFromPort) {
            return 1
        } else if (localFromPort > remoteFromPort) {
            return -1
        } else {
            return 0
        }
    }
}
