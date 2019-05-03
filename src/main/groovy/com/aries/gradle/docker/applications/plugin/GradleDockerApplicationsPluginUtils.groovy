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

import com.aries.gradle.docker.applications.plugin.domain.AbstractApplication
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import static com.bmuschko.gradle.docker.utils.IOUtils.getProgressLogger

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

    /**
     * Configure the passed TaskProvider against a list of Closures.
     *
     * @param taskToConfig the task to further configure.
     * @param configsToApply list of Closure's to configure against task.
     */
    static void applyConfigs(final TaskProvider<?> taskToConfig,
                             final List<Closure> configsToApply) {

        taskToConfig.configure { tsk ->
            configsToApply.each { cnf ->
                tsk.configure(cnf)
            }
        }
    }

    // create task which will acquire an execution lock for a given task chain
    static TaskProvider<Task> buildAcquireExecutionLockTask(final Project project,
                                                            final String appName,
                                                            final String appGroup) {

        // using random string as this method is called ad-hoc in multiple places
        // and so the name must be unique but still named appropriately.
        return project.tasks.register("${appName}AcquireExecutionLock_${randomString(null)}") {
            outputs.upToDateWhen { false }

            group: appGroup
            description: "Acquire execution lock for '${appName}'."

            doLast {
                logger.quiet "Acquiring execution lock for '${appName}'."

                final String lockName = appGroup
                if(!project.gradle.ext.has(lockName)) {
                    synchronized (GradleDockerApplicationsPluginUtils) {
                        if(!project.gradle.ext.has(lockName)) {
                            final AtomicBoolean executionLock = new AtomicBoolean(false);
                            project.gradle.ext.set(lockName, executionLock)
                        }
                    }
                }

                final def progressLogger = getProgressLogger(project, GradleDockerApplicationsPluginUtils)
                progressLogger.started()

                int pollTimes = 0
                long pollInterval = 5000
                long totalMillis = 0
                final AtomicBoolean executionLock = project.gradle.ext.get(lockName)
                while(!executionLock.compareAndSet(false, true)) {
                    pollTimes += 1

                    totalMillis = pollTimes * pollInterval
                    long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis)

                    progressLogger.progress("Waiting on lock for ${totalMinutes}m...")
                    sleep(pollInterval)
                }
                progressLogger.completed()

                logger.info "Lock took ${totalMillis}m to acquire."
            }
        }
    }

    // create task which will release an execution lock for a given task chain
    static TaskProvider<Task> buildReleaseExecutionLockTask(final Project project,
                                                            final String appName,
                                                            final String appGroup) {

        // using random string as this method is called ad-hoc in multiple places
        // and so the name must be unique but still named appropriately.
        return project.tasks.register("${appName}ReleaseExecutionLock_${randomString(null)}") {
            outputs.upToDateWhen { false }

            group: appGroup
            description: "Release execution lock for '${appName}'."

            doLast {
                logger.quiet "Releasing execution lock for '${appName}'."

                final String lockName = appGroup
                if(project.gradle.ext.has(lockName)) {
                    final AtomicBoolean executionLock = project.gradle.ext.get(lockName)
                    executionLock.set(false)
                } else {
                    throw new GradleException("Failed to find execution lock for '${appName}'.")
                }
            }
        }
    }
}
