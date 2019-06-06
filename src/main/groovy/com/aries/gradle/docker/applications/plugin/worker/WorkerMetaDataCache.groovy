package com.aries.gradle.docker.applications.plugin.worker

import com.bmuschko.gradle.docker.shaded.com.google.common.collect.Maps

class WorkerMetaDataCache {

    private static final Map<String, WorkerMetaData> workerMetaDataCache = Maps.newConcurrentMap()

    private WorkerMetaDataCache() {
        throw new RuntimeException('Purposefully not implemented')
    }

    static void put(final String cacheKey, final WorkerMetaData workerObject) {
        workerMetaDataCache.put(cacheKey, workerObject)
    }

    static WorkerMetaData get(final String cacheKey) {
        workerMetaDataCache.remove(cacheKey)
    }
}
