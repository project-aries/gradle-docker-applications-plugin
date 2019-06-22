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

import javax.annotation.Nullable
import java.util.concurrent.atomic.AtomicInteger

import static java.util.Objects.requireNonNull

/**
 *
 *  Holds meta-data about the front-end container
 *
 */
class FrontEnd {

    final FrontContainer frontContainer
    final AtomicInteger latch

    FrontEnd(final FrontContainer frontContainer,
             final AtomicInteger latch) {

        this.frontContainer = requireNonNull(frontContainer)
        this.latch = requireNonNull(latch)
    }

    static FrontEnd buildFrom(@Nullable final FrontContainer frontContainer,
                              final int count) {

        if (frontContainer) {
            final AtomicInteger latch = new AtomicInteger(count)
            final FrontEnd frontEnd = new FrontEnd(frontContainer, latch)
            return frontEnd
        } else {
            return null
        }
    }
}
