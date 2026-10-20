package ru.mcmon.plugin.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class BanSync {

    public static final Duration INTERVAL = Duration.ofMinutes(5);

    private final SiteClient site;
    private final Path file;
    private final Log log;
    private volatile BanList current;
    private volatile LookupOutcome reportedFailure;
    private volatile boolean connected;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> schedule;

    private BanSync(SiteClient site, Path file, Log log, BanList current) {
        this.site = site;
        this.file = file;
        this.log = log;
        this.current = current;
    }

    public static BanSync open(SiteClient site, Path file, Log log) {
        return new BanSync(site, file, log, read(file, log));
    }

    public BanList current() {
        return current;
    }

    public Optional<Ban> find(PlayerId id) {
        return current.find(id);
    }

    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "mcmonitor-bans");
            thread.setDaemon(true);
            return thread;
        });
        schedule = scheduler.scheduleAtFixedRate(() -> refresh().join(), 0, INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            schedule.cancel(true);
            schedule = null;
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public CompletableFuture<Void> refresh() {
        try {
            return site.bans().thenAccept(this::apply).exceptionally(error -> {
                report(LookupOutcome.PLUGIN_ERROR, internal(error));
                return null;
            });
        } catch (RuntimeException e) {
            report(LookupOutcome.PLUGIN_ERROR, internal(e));
            return CompletableFuture.completedFuture(null);
        }
    }

    private void apply(BansFetch fetch) {
        if (!fetch.list().isPresent()) {
            report(fetch.failure());
            return;
        }
        BanList fetched = fetch.list().get();
        boolean recovered = reportedFailure != null;
        reportedFailure = null;
        if (!fetched.newerOrSameAs(current)) {
            log.warn("the site sent an older ban list (" + fetched.updatedAt() + ") than the saved one ("
                    + current.updatedAt() + "), using the site's list");
        }
        boolean changed = !fetched.bans().equals(current.bans());
        boolean switched = fetched.requireLauncher() != current.requireLauncher();
        current = fetched;
        if (switched) {
            log.info("launcher-only mode turned " + (fetched.requireLauncher() ? "on" : "off") + " by the site");
        }
        if (changed || switched) {
            write(fetched);
        }
        if (!connected) {
            connected = true;
            log.info("connected to the site: " + bans(fetched.size()) + " as of " + fetched.updatedAt());
        } else if (changed || recovered) {
            log.info("ban list updated: " + bans(fetched.size()) + " as of " + fetched.updatedAt());
        }
    }

    private void report(LookupOutcome failure) {
        report(failure, failure.description());
    }

    private void report(LookupOutcome failure, String reason) {
        if (reportedFailure == failure) {
            return;
        }
        reportedFailure = failure;
        String hint = failure == LookupOutcome.INVALID_KEY
                ? "; copy the key from the MC Monitor dashboard into server-key"
                : "";
        log.warn("could not update bans (" + reason + "), using " + bans(current.size()) + " saved earlier" + hint);
    }

    private static String bans(int count) {
        return count == 1 ? "1 ban" : count + " bans";
    }

    private static String internal(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        return "internal error " + cause.getClass().getName();
    }

    private static BanList read(Path file, Log log) {
        if (Files.notExists(file)) {
            return BanList.EMPTY;
        }
        try {
            return BanList.parse(Utf8Files.read(file)).orElseGet(() -> {
                log.warn(file.getFileName()
                        + " could not be read, starting without saved bans; a fresh list comes from the site"
                        + " shortly");
                return BanList.EMPTY;
            });
        } catch (IOException e) {
            log.warn(file.getFileName() + " could not be read (" + e.getClass().getSimpleName()
                    + "), starting without saved bans; a fresh list comes from the site shortly");
            return BanList.EMPTY;
        }
    }

    private void write(BanList list) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Utf8Files.write(temp, list.toJson());
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn(file.getFileName() + " could not be saved (" + e.getClass().getSimpleName()
                    + "); check disk space and file permissions in the MC Monitor folder");
        }
    }
}
