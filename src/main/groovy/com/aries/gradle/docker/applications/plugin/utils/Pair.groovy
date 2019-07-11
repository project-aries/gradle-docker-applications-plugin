package com.aries.gradle.docker.applications.plugin.utils

class Pair<L, R> {

    private final L left;
    private final R right;

    static <L, R> Pair<L, R> of(final L left, final R right) {
        return new Pair<L, R>(left, right);
    }

    static <L, R> Pair<L, R> ofNullable() {
        return new Pair<L, R>(null, null);
    }

    Pair(final L left, final R right) {
        this.left = left;
        this.right = right;
    }

    L left() {
        return left;
    }

    R right() {
        return right;
    }

    boolean empty() {
        return left() == null && right() == null
    }
}
