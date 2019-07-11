package com.aries.gradle.docker.applications.plugin.report

import com.bmuschko.gradle.docker.shaded.com.google.common.collect.Maps
import com.bmuschko.gradle.docker.shaded.com.google.common.collect.Sets

import static java.util.Objects.requireNonNull

final class SummaryReportCache {

    // key=application-name, value=list-of-summary-reports-for-application-name
    private static final Map<String, Set<SummaryReport>> summaryReportCache = Maps.newHashMap()

    private SummaryReportCache() {
        throw new RuntimeException('Purposefully not implemented')
    }

    static synchronized Set<SummaryReport> get(final String appName) {
        requireNonNull(appName)
        Set<SummaryReport> reports = summaryReportCache.get(appName)
        if (reports == null) {
            final Set<SummaryReport> reportsList = Sets.newHashSet()
            reports = reportsList
            summaryReportCache.put(appName, reports)
        }

        return reports
    }

    static synchronized Set<SummaryReport> view(final String appName) {
        final Set<SummaryReport> reports = SummaryReportCache.get(appName)
        return Sets.newHashSet(reports);
    }

    static synchronized SummaryReport create(final String appName = null) {
        final SummaryReport summaryReport = new SummaryReport()
        if (appName != null) {
            SummaryReportCache.get(appName).add(summaryReport)
        }
        return summaryReport
    }

    static synchronized Set<SummaryReport> matching(final String... regex) {
        matching(Sets.newHashSet(regex))
    }

    static synchronized Set<SummaryReport> matching(final Collection<String> regex) {
        final Set<SummaryReport> matchingReports = Sets.newHashSet()
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
