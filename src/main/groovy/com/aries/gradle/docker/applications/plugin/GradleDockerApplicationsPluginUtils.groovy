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

import org.gradle.api.Project
import org.gradle.api.Task

import javax.annotation.Nullable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import static com.bmuschko.gradle.docker.utils.IOUtils.getProgressLogger

/**
 * Project wide static methods.
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
     * Execute an arbitrary tasks code ensuring its `doFirst` closures are run before hand
     * and its `doLast` closures are run after.
     *
     * It's important to note that the task is not actually run in the gradle sense
     * but that its code is called much like any other piece of code.
     *
     * @param taskToExecute arbitrary task to execute.
     * @param configsToApply configs to apply to task prior to executing.
     */
    static void executeTaskCode(final Task taskToExecute,
                                final List<Closure> configsToApply) {

        executeTaskCode(applyConfigs(taskToExecute, configsToApply))
    }


    /**
     * Execute an arbitrary tasks code ensuring its `doFirst` closures are run before hand
     * and its `doLast` closures are run after.
     *
     * It's important to note that the task is not actually run in the gradle sense
     * but that its code is called much like any other piece of code.
     *
     * @param lockKey if non-null will surround execution with a lock given this key.
     * @param taskToExecute arbitrary task to execute.
     */
    static void executeTaskCode(final Task taskToExecute, @Nullable final String lockKey = null) {

        try {

            if (lockKey != null) {
                acquireLock(taskToExecute.getProject(), lockKey)
            }

            // ensure any and all `onlyIf` blocks resolve to true
            if (taskToExecute.getOnlyIf().isSatisfiedBy(taskToExecute)) {
                try {

                    // Execute doFirst actions in an ad-hoc manner
                    taskToExecute.getTaskActions().findAll { act -> act.getDisplayName().contains('doFirst') }.each {
                        it.execute(taskToExecute)
                    }

                    // Execute start action(s) in an ad-hoc manner (this should only return 1)
                    taskToExecute.getTaskActions().findAll { act -> act.getDisplayName().contains('start') }.each {
                        it.execute(taskToExecute)
                    }

                } finally {

                    // Execute doLast actions in an ad-hoc manner
                    taskToExecute.getTaskActions().findAll { act -> act.getDisplayName().contains('doLast') }.each {
                        it.execute(taskToExecute)
                    }
                }
            }
        } finally {
            if (lockKey != null) {
                releaseLock(taskToExecute.getProject(), lockKey)
            }
        }
    }

    /**
     * Configure the passed TaskProvider against a list of Closures.
     *
     * @param taskToConfig the task to further configure.
     * @param configsToApply list of Closure's to configure against task.
     */
    static Task applyConfigs(final Task taskToConfig,
                             final List<Closure> configsToApply) {

        taskToConfig.configure { tsk ->
            configsToApply.each { cnf ->
                tsk.configure(cnf)
            }
        }

        taskToConfig
    }

    static void acquireLock(final Project project, final String lockName) {

        project.logger.debug "Acquiring lock with '${lockName}'."

        final String hashedLockName = lockName.md5()
        if(!project.gradle.ext.has(hashedLockName)) {
            synchronized (GradleDockerApplicationsPluginUtils) {
                if(!project.gradle.ext.has(hashedLockName)) {
                    final AtomicBoolean executionLock = new AtomicBoolean(false);
                    project.gradle.ext.set(hashedLockName, executionLock)
                }
            }
        }

        final def progressLogger = getProgressLogger(project, GradleDockerApplicationsPluginUtils)
        progressLogger.started()

        int pollTimes = 0
        long pollInterval = 5000
        long totalMillis = 0
        final AtomicBoolean executionLock = project.gradle.ext.get(hashedLockName)
        while(!executionLock.compareAndSet(false, true)) {
            pollTimes += 1

            totalMillis = pollTimes * pollInterval
            long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis)

            progressLogger.progress("Waiting to acquire lock '${lockName}' for ${totalMinutes}m...")
            sleep(pollInterval)
        }
        progressLogger.completed()

        project.logger.debug "Lock took ${totalMillis}m to acquire."
    }

    static void releaseLock(final Project project, final String lockName) {

        project.logger.debug "Releasing lock for '${lockName}'."

        final String hashedLockName = lockName.md5()
        if(project.gradle.ext.has(hashedLockName)) {
            synchronized (GradleDockerApplicationsPluginUtils) {
                if(project.gradle.ext.has(hashedLockName)) {
                    final AtomicBoolean executionLock = project.gradle.ext.get(hashedLockName)
                    executionLock.set(false)
                }
            }
        }
    }
}
