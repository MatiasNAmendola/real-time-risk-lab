package io.riskplatform.rules.engine;

import io.riskplatform.rules.config.RulesConfig;
import io.riskplatform.rules.config.RulesConfigLoader;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * File-watch based hot reload for the RuleEngine.
 *
 * Uses java.nio.file.WatchService to detect ENTRY_MODIFY events on the rules config directory.
 * On change detected, loads and validates the new config and calls engine.reload() atomically.
 *
 * If the new config is invalid, the exception is logged and the previous config remains active.
 * This ensures the "failed load never replaces the active config" invariant.
 */
public final class HotReload implements Closeable {

    private final RuleEngine engine;
    private final RulesConfigLoader loader;
    private final Path configPath;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private WatchService watcher;
    private ExecutorService watchThread;

    public HotReload(RuleEngine engine, RulesConfigLoader loader, String configPath) {
        this.engine     = engine;
        this.loader     = loader;
        this.configPath = Path.of(configPath);
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;

        watcher = FileSystems.getDefault().newWatchService();
        Path dir = configPath.getParent() != null ? configPath.getParent() : Path.of(".");
        dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);

        watchThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rules-hot-reload");
            t.setDaemon(true);
            return t;
        });
        watchThread.submit(this::watchLoop);
    }

    @Override
    public void close() {
        running.set(false);
        if (watcher != null) {
            try { watcher.close(); } catch (IOException ignored) {}
        }
        if (watchThread != null) {
            watchThread.shutdownNow();
        }
    }

    /**
     * Force a reload from disk regardless of file-watch events.
     * Used by the admin API POST /admin/rules/reload endpoint.
     *
     * @return the new config hash after reload
     * @throws RuntimeException if the new config is invalid (previous config remains active)
     */
    public String forceReload() {
        RulesConfig newConfig = loader.load(configPath);
        engine.reload(newConfig);
        return newConfig.hash();
    }

    private void watchLoop() {
        while (running.get()) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                Thread.currentThread().interrupt();
                break;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                Path changed = (Path) event.context();
                if (configPath.getFileName().equals(changed)) {
                    tryReload();
                }
            }

            if (!key.reset()) break;
        }
    }

    private void tryReload() {
        try {
            RulesConfig newConfig = loader.load(configPath);
            engine.reload(newConfig);
        } catch (Exception e) {
            // Log but do NOT propagate — previous config stays active
            System.err.println("[HotReload] Config reload failed, keeping previous config: " + e.getMessage());
        }
    }
}
