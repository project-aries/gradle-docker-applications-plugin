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

package com.aries.gradle.docker.applications.plugin

import org.gradle.api.GradleException

/**
 *
 * Place to house project wide static methods.
 *
 */
class GradleDockerApplicationsPluginUtils {

    // list of exception class names which, if thrown in our context
    // from the docker-java library, simply means the container is either
    // not running or doesn't even exist.
    public static final String CLASS_NAME_REGEX = '^(NotModifiedException|NotFoundException|ConflictException)$'

    public static final String EXCEPTION_MESSAGE_REGEX = 'not running'

    private GradleDockerApplicationsPluginUtils() {
        throw new RuntimeException('Purposefully not implemented')
    }

    /**
     * Get a random string prepended with some identifer.
     *
     * @param prependWith optional string to prepend to generated random string.
     * @return randomized String
     */
    static String randomString(def prependWith = 'gdap-') {
        (prependWith ?: '') + UUID.randomUUID().toString().replaceAll("-", "")
    }

    /**
     * Return true if this is a valid error and not something we can ignore.
     *
     * @param throwable the Exception to check for validity.
     * @return true if valid error false otherwise.
     */
    static boolean isValidError(final Throwable throwable) {
        (throwable.class.simpleName.matches(CLASS_NAME_REGEX) ||
            throwable.getMessage().contains(EXCEPTION_MESSAGE_REGEX)) ? false : true
    }

    /**
     * Throw the passed Throwable IF its a valid error.
     *
     * @param throwable the Exception to validate and potentially re-throw.
     */
    static void throwOnValidError(final Throwable throwable) {
        if (isValidError(throwable)) { throw throwable }
    }

    /**
     * Throw the passed Throwable IF its a valid error otherwise re-throw as GradleException.
     *
     * @param throwable the Exception to validate and potentially re-throw.
     * @param message the message to insert into potentially re-thrown GradleException.
     */
    static void throwOnValidErrorElseGradleException(final Throwable throwable, final String message) {
        if (isValidError(throwable)) {
            throw throwable
        } else {
            throw new GradleException(message, throwable)
        }
    }
}
