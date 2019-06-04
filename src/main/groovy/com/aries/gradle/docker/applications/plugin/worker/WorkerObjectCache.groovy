package com.aries.gradle.docker.applications.plugin.worker

import com.bmuschko.gradle.docker.shaded.com.google.common.collect.Maps

class WorkerObjectCache {

    private static final Map<String, WorkerObject> objectCache = Maps.newConcurrentMap()

    private WorkerObjectCache() {
        throw new RuntimeException('Purposefully not implemented')
    }

    static void put(final String hash, final WorkerObject workerObject) {
        objectCache.put(hash, workerObject)
    }

    static WorkerObject get(final String hash) {
        objectCache.remove(hash)
    }
}
