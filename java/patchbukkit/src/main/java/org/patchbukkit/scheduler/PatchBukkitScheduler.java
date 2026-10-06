package org.patchbukkit.scheduler;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;
import org.jetbrains.annotations.NotNull;

public class PatchBukkitScheduler implements BukkitScheduler {

    private static final long MS_PER_TICK = 50L;

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "patchbukkit-scheduler");
                t.setDaemon(true);
                return t;
            }
    );

    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private volatile long currentTick;

    private final class ScheduledTask {
        final PatchBukkitTask handle;
        final Runnable action;
        final Runnable onCancel;
        final long period;
        volatile long nextTick;
        volatile ScheduledFuture<?> future;
        volatile boolean running;
        volatile boolean cancelled;

        ScheduledTask(PatchBukkitTask handle, Runnable action, long delay, long period, Runnable onCancel) {
            this.handle = handle;
            this.action = action;
            this.onCancel = onCancel;
            this.period = period;
            this.nextTick = currentTick + Math.max(1, delay);
        }

        void run() {
            if (cancelled) return;
            running = true;
            try {
                action.run();
            } catch (Throwable failure) {
                handle.getOwner().getLogger().log(Level.SEVERE, "Scheduled task " + handle.getTaskId() + " failed", failure);
            } finally {
                running = false;
                if (period <= 0) tasks.remove(handle.getTaskId(), this);
            }
        }
    }

    private BukkitTask submit(Plugin plugin, Runnable task, long delayTicks, long periodTicks, boolean sync) {
        return submit(plugin, ignored -> task.run(), delayTicks, periodTicks, sync);
    }

    private BukkitTask submit(Plugin plugin, Consumer<? super BukkitTask> task, long delayTicks, long periodTicks, boolean sync) {
        return submit(plugin, task, delayTicks, periodTicks, sync, null);
    }

    private BukkitTask submit(Plugin plugin, Consumer<? super BukkitTask> task, long delayTicks, long periodTicks, boolean sync, Runnable onCancel) {
        int id = nextId.getAndIncrement();
        PatchBukkitTask handle = new PatchBukkitTask(id, plugin, sync, this);
        ScheduledTask scheduled = new ScheduledTask(handle, () -> task.accept(handle), delayTicks, periodTicks, onCancel);
        tasks.put(id, scheduled);
        if (!sync) {
            long delayMs = Math.max(0, delayTicks) * MS_PER_TICK;
            ScheduledFuture<?> future = periodTicks > 0
                    ? executor.scheduleAtFixedRate(scheduled::run, delayMs, periodTicks * MS_PER_TICK, TimeUnit.MILLISECONDS)
                    : executor.schedule(scheduled::run, delayMs, TimeUnit.MILLISECONDS);
            scheduled.future = future;
            if (scheduled.cancelled) future.cancel(false);
        }
        return handle;
    }

    /** Pumps synchronous jobs on the same native JVM worker that dispatches Bukkit commands/events. */
    public void tick() {
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Synchronous scheduler must run on the primary thread");
        }
        long tick = ++currentTick;
        List<ScheduledTask> ready = tasks.values().stream()
                .filter(task -> task.handle.isSync() && task.nextTick <= tick)
                .sorted(Comparator.comparingLong((ScheduledTask task) -> task.nextTick).thenComparingInt(task -> task.handle.getTaskId()))
                .toList();
        for (ScheduledTask task : ready) {
            if (tasks.get(task.handle.getTaskId()) != task) continue;
            task.run();
            task.nextTick = tick + Math.max(1, task.period);
        }
    }

    @Override
    public void cancelTask(int taskId) {
        ScheduledTask task = tasks.remove(taskId);
        if (task == null) return;
        task.cancelled = true;
        task.handle.markCancelled();
        if (task.future != null) task.future.cancel(false);
        if (task.onCancel != null) task.onCancel.run();
    }

    @Override
    public void cancelTasks(@NotNull Plugin plugin) {
        tasks.forEach((id, task) -> {
            if (task.handle.getOwner().equals(plugin)) cancelTask(id);
        });
    }

    @Override
    public boolean isCurrentlyRunning(int taskId) {
        ScheduledTask task = tasks.get(taskId);
        return task != null && task.running;
    }

    @Override
    public boolean isQueued(int taskId) {
        ScheduledTask task = tasks.get(taskId);
        return task != null && !task.cancelled && !task.running;
    }

    @Override
    public @NotNull List<BukkitWorker> getActiveWorkers() {
        return Collections.emptyList();
    }

    @Override
    public @NotNull List<BukkitTask> getPendingTasks() {
        return tasks.values().stream().filter(task -> !task.cancelled).map(task -> (BukkitTask) task.handle).toList();
    }

    @Override
    public @NotNull BukkitTask runTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        return submit(plugin, task, 0, 0, true);
    }

    @Override
    public void runTask(@NotNull Plugin plugin, @NotNull Consumer<? super BukkitTask> task) {
        submit(plugin, task, 0, 0, true);
    }

    @Override
    public @NotNull BukkitTask runTask(@NotNull Plugin plugin, @NotNull BukkitRunnable task) {
        return submit(plugin, task, 0, 0, true);
    }

    @Override
    public @NotNull BukkitTask runTaskAsynchronously(@NotNull Plugin plugin, @NotNull Runnable task) {
        return submit(plugin, task, 0, 0, false);
    }

    @Override
    public void runTaskAsynchronously(@NotNull Plugin plugin, @NotNull Consumer<? super BukkitTask> task) {
        submit(plugin, task, 0, 0, false);
    }

    @Override
    public @NotNull BukkitTask runTaskAsynchronously(@NotNull Plugin plugin, @NotNull BukkitRunnable task) {
        return submit(plugin, task, 0, 0, false);
    }

    @Override
    public @NotNull BukkitTask runTaskLater(@NotNull Plugin plugin, @NotNull Runnable task, long delay) {
        return submit(plugin, task, delay, 0, true);
    }

    @Override
    public void runTaskLater(@NotNull Plugin plugin, @NotNull Consumer<? super BukkitTask> task, long delay) {
        submit(plugin, task, delay, 0, true);
    }

    @Override
    public @NotNull BukkitTask runTaskLater(@NotNull Plugin plugin, @NotNull BukkitRunnable task, long delay) {
        return submit(plugin, task, delay, 0, true);
    }

    @Override
    public @NotNull BukkitTask runTaskLaterAsynchronously(@NotNull Plugin plugin, @NotNull Runnable task, long delay) {
        return submit(plugin, task, delay, 0, false);
    }

    @Override
    public void runTaskLaterAsynchronously(@NotNull Plugin plugin, @NotNull Consumer<? super BukkitTask> task, long delay) {
        submit(plugin, task, delay, 0, false);
    }

    @Override
    public @NotNull BukkitTask runTaskLaterAsynchronously(@NotNull Plugin plugin, @NotNull BukkitRunnable task, long delay) {
        return submit(plugin, task, delay, 0, false);
    }

    @Override
    public @NotNull BukkitTask runTaskTimer(@NotNull Plugin plugin, @NotNull Runnable task, long delay, long period) {
        return submit(plugin, task, delay, period, true);
    }

    @Override
    public void runTaskTimer(@NotNull Plugin plugin, @NotNull Consumer<? super BukkitTask> task, long delay, long period) {
        submit(plugin, task, delay, period, true);
    }

    @Override
    public @NotNull BukkitTask runTaskTimer(@NotNull Plugin plugin, @NotNull BukkitRunnable task, long delay, long period) {
        return submit(plugin, task, delay, period, true);
    }

    @Override
    public @NotNull BukkitTask runTaskTimerAsynchronously(@NotNull Plugin plugin, @NotNull Runnable task, long delay, long period) {
        return submit(plugin, task, delay, period, false);
    }

    @Override
    public void runTaskTimerAsynchronously(@NotNull Plugin plugin, @NotNull Consumer<? super BukkitTask> task, long delay, long period) {
        submit(plugin, task, delay, period, false);
    }

    @Override
    public @NotNull BukkitTask runTaskTimerAsynchronously(@NotNull Plugin plugin, @NotNull BukkitRunnable task, long delay, long period) {
        return submit(plugin, task, delay, period, false);
    }

    @Override
    public int scheduleSyncDelayedTask(@NotNull Plugin plugin, @NotNull Runnable task, long delay) {
        return submit(plugin, task, delay, 0, true).getTaskId();
    }

    @Override
    public int scheduleSyncDelayedTask(@NotNull Plugin plugin, @NotNull BukkitRunnable task, long delay) {
        return submit(plugin, task, delay, 0, true).getTaskId();
    }

    @Override
    public int scheduleSyncDelayedTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        return submit(plugin, task, 0, 0, true).getTaskId();
    }

    @Override
    public int scheduleSyncDelayedTask(@NotNull Plugin plugin, @NotNull BukkitRunnable task) {
        return submit(plugin, task, 0, 0, true).getTaskId();
    }

    @Override
    public int scheduleSyncRepeatingTask(@NotNull Plugin plugin, @NotNull Runnable task, long delay, long period) {
        return submit(plugin, task, delay, period, true).getTaskId();
    }

    @Override
    public int scheduleSyncRepeatingTask(@NotNull Plugin plugin, @NotNull BukkitRunnable task, long delay, long period) {
        return submit(plugin, task, delay, period, true).getTaskId();
    }

    @Override
    public int scheduleAsyncDelayedTask(@NotNull Plugin plugin, @NotNull Runnable task, long delay) {
        return submit(plugin, task, delay, 0, false).getTaskId();
    }

    @Override
    public int scheduleAsyncDelayedTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        return submit(plugin, task, 0, 0, false).getTaskId();
    }

    @Override
    public int scheduleAsyncRepeatingTask(@NotNull Plugin plugin, @NotNull Runnable task, long delay, long period) {
        return submit(plugin, task, delay, period, false).getTaskId();
    }

    @Override
    public <T> @NotNull Future<T> callSyncMethod(@NotNull Plugin plugin, @NotNull Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        BukkitTask scheduled = submit(plugin, ignored -> {
            try {
                result.complete(task.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, 0, 0, true, () -> result.cancel(false));
        result.whenComplete((value, failure) -> {
            if (result.isCancelled()) scheduled.cancel();
        });
        return result;
    }

    @Override
    public @NotNull Executor getMainThreadExecutor(@NotNull Plugin plugin) {
        return task -> runTask(plugin, task);
    }
}
