package ru.mcmon.plugin.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public interface SiteClient {

    CompletableFuture<Lookup> lookup(String nick, LaunchCode code);

    default CompletableFuture<BansFetch> bans() {
        return CompletableFuture.completedFuture(BansFetch.failed(LookupOutcome.UNREACHABLE));
    }

    default Executor executor() {
        return ForkJoinPool.commonPool();
    }

    default void shutdown() {}
}
