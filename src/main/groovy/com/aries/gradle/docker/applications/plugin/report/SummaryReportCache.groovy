package com.aries.gradle.docker.applications.plugin.report

import com.bmuschko.gradle.docker.shaded.com.google.common.collect.Lists
import com.bmuschko.gradle.docker.shaded.com.google.common.collect.Maps

import javax.annotation.Nullable

import static java.util.Objects.requireNonNull

final class SummaryReportCache {

    // key=application-name, value=list-of-summary-reports-for-application-name
    private static final Map<String, List<SummaryReport>> summaryReportCache = Maps.newConcurrentMap()

    private SummaryReportCache() {
        throw new RuntimeException('Purposefully not implemented')
    }

    static List<SummaryReport> get(final String appName, @Nullable final SummaryReport summaryReport = null) {
        requireNonNull(appName)
        final List<SummaryReport> reports = summaryReportCache.get(appName, Lists.newCopyOnWriteArrayList())
        if (summaryReport) {
            reports.add(summaryReport)
        }
        reports
    }

    static List<SummaryReport> view(final String appName) {
        final List<SummaryReport> reports = get(appName)
        return Lists.newArrayList(reports);
    }

    static SummaryReport create(final String appName) {
        final SummaryReport summaryReport = SummaryReport.newInstance()
        get(appName, summaryReport)
        summaryReport
    }

    static List<SummaryReport> matching(final String... regex) {
        final List<SummaryReport> matchingReports = Lists.newArrayList();
        if (regex) {
            for(final String possibleRegex : regex) {
                if (possibleRegex) {
                    summaryReportCache.each { k, v ->
                        if(k.matches(possibleRegex)) {
                            matchingReports.addAll(v)
                        }
                    }
                }
            }
        }

        return matchingReports
    }
}
