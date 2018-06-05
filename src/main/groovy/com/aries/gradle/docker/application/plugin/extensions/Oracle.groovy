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

package com.aries.gradle.docker.application.plugin.extensions

import com.aries.gradle.docker.application.plugin.common.AbstractContainer

/**
 *  Oracle specific extension point.
 */
class Oracle  {

    public Oracle() {
        this.main = new AbstractContainer(repository: 'sath89/oracle-12c', tag: 'latest')
    }

    String liveOnLog() {
        this.liveOnLog ?: 'Database ready to use.'
    }
}

