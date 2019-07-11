package com.aries.gradle.docker.applications.plugin.tasks

import com.aries.gradle.docker.applications.plugin.report.SummaryReport
import com.aries.gradle.docker.applications.plugin.report.SummaryReportCache
import com.bmuschko.gradle.docker.shaded.com.google.common.collect.Lists
import com.bmuschko.gradle.docker.shaded.com.google.common.collect.Sets
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import javax.annotation.Nullable

/**
 *
 * Collects and houses all reports for downstream querying and assessing.
 *
 */
class SummaryReportCollector extends DefaultTask {

    @TaskAction
    void execute() {
        logger.debug("Invoke method 'reports()' on this task to get lazily initialized reports...")
    }

    List<SummaryReport> reports(@Nullable final String matching = null) {
        final Set<String> requestedNames = Sets.newHashSet()
        requestedNames.add(getGroup())
        requestedNames.add(matching)
        return Lists.newArrayList(SummaryReportCache.matching(requestedNames))
    }
}
