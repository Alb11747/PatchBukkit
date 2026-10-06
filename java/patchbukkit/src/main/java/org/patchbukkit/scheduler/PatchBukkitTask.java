package org.patchbukkit.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class PatchBukkitTask implements BukkitTask {

    private final int id;
    private final Plugin plugin;
    private final boolean sync;
    private final PatchBukkitScheduler scheduler;
    private volatile boolean cancelled;

    public PatchBukkitTask(int id, Plugin plugin, boolean sync, PatchBukkitScheduler scheduler) {
        this.id = id;
        this.plugin = plugin;
        this.sync = sync;
        this.scheduler = scheduler;
    }

    @Override
    public int getTaskId() {
        return id;
    }

    @Override
    public @NotNull Plugin getOwner() {
        return plugin;
    }

    @Override
    public boolean isSync() {
        return sync;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    void markCancelled() { cancelled = true; }

    @Override
    public void cancel() {
        markCancelled();
        scheduler.cancelTask(id);
    }
}
