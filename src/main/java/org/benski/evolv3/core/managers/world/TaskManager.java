package org.benski.evolv3.core.managers.world;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.benski.evolv3.Main;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manager for handling scheduled tasks with automatic tracking and cleanup. Provides a clean API
 * for sync/async tasks and automatically cancels all tasks on shutdown.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * TaskManager.getInstance().runAsync(() -> {
 *
 * });
 *
 *
 * TaskManager.getInstance().runSync(() -> {
 *
 * });
 *
 *
 * TaskManager.getInstance().runLater(() -> {
 *
 * }, 100);
 *
 *
 * TaskManager.getInstance().runTimer(() -> {
 *
 * }, 0, 20);
 * }</pre>
 */
public class TaskManager {
  @Getter private static final TaskManager instance = new TaskManager();
  private final JavaPlugin plugin = Main.getInstance();
  private final Set<BukkitTask> activeTasks = new HashSet<>();

  /** Creates a new TaskManager instance. */
  private TaskManager() {}

  /**
   * Runs a task synchronously on the main server thread. Use this for any Bukkit API operations.
   *
   * @param runnable the task to run
   * @return the BukkitTask
   */
  public BukkitTask runSync(Runnable runnable) {
    BukkitTask task = Bukkit.getScheduler().runTask(plugin, runnable);
    activeTasks.add(task);
    return task;
  }

  /**
   * Runs a task synchronously after a delay.
   *
   * @param runnable the task to run
   * @param delayTicks the delay in ticks (20 ticks = 1 second)
   * @return the BukkitTask
   */
  public BukkitTask runLater(Runnable runnable, long delayTicks) {
    BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
    activeTasks.add(task);
    return task;
  }

  /**
   * Runs a task synchronously after a delay (using TimeUnit).
   *
   * @param runnable the task to run
   * @param delay the delay amount
   * @param unit the time unit
   * @return the BukkitTask
   */
  public BukkitTask runLater(Runnable runnable, long delay, TimeUnit unit) {
    long ticks = convertToTicks(delay, unit);
    return runLater(runnable, ticks);
  }

  /**
   * Runs a repeating task synchronously.
   *
   * @param runnable the task to run
   * @param delayTicks the initial delay in ticks
   * @param periodTicks the period between executions in ticks
   * @return the BukkitTask
   */
  public BukkitTask runTimer(Runnable runnable, long delayTicks, long periodTicks) {
    BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    activeTasks.add(task);
    return task;
  }

  /**
   * Runs a repeating task synchronously (using TimeUnit).
   *
   * @param runnable the task to run
   * @param delay the initial delay
   * @param period the period between executions
   * @param unit the time unit
   * @return the BukkitTask
   */
  public BukkitTask runTimer(Runnable runnable, long delay, long period, TimeUnit unit) {
    long delayTicks = convertToTicks(delay, unit);
    long periodTicks = convertToTicks(period, unit);
    return runTimer(runnable, delayTicks, periodTicks);
  }

  /**
   * Runs a task asynchronously on a separate thread. WARNING: Do NOT use Bukkit API from async
   * tasks! Use this for database operations, file I/O, or heavy computations.
   *
   * @param runnable the task to run
   * @return the BukkitTask
   */
  public BukkitTask runAsync(Runnable runnable) {
    BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    activeTasks.add(task);
    return task;
  }

  /**
   * Runs a task asynchronously after a delay. WARNING: Do NOT use Bukkit API from async tasks!
   *
   * @param runnable the task to run
   * @param delayTicks the delay in ticks (20 ticks = 1 second)
   * @return the BukkitTask
   */
  public BukkitTask runAsyncLater(Runnable runnable, long delayTicks) {
    BukkitTask task =
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks);
    activeTasks.add(task);
    return task;
  }

  /**
   * Runs a task asynchronously after a delay (using TimeUnit). WARNING: Do NOT use Bukkit API from
   * async tasks!
   *
   * @param runnable the task to run
   * @param delay the delay amount
   * @param unit the time unit
   * @return the BukkitTask
   */
  public BukkitTask runAsyncLater(Runnable runnable, long delay, TimeUnit unit) {
    long ticks = convertToTicks(delay, unit);
    return runAsyncLater(runnable, ticks);
  }

  /**
   * Runs a repeating task asynchronously. WARNING: Do NOT use Bukkit API from async tasks!
   *
   * @param runnable the task to run
   * @param delayTicks the initial delay in ticks
   * @param periodTicks the period between executions in ticks
   * @return the BukkitTask
   */
  public BukkitTask runAsyncTimer(Runnable runnable, long delayTicks, long periodTicks) {
    BukkitTask task =
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
    activeTasks.add(task);
    return task;
  }

  /**
   * Runs a repeating task asynchronously (using TimeUnit). WARNING: Do NOT use Bukkit API from
   * async tasks!
   *
   * @param runnable the task to run
   * @param delay the initial delay
   * @param period the period between executions
   * @param unit the time unit
   * @return the BukkitTask
   */
  public BukkitTask runAsyncTimer(Runnable runnable, long delay, long period, TimeUnit unit) {
    long delayTicks = convertToTicks(delay, unit);
    long periodTicks = convertToTicks(period, unit);
    return runAsyncTimer(runnable, delayTicks, periodTicks);
  }

  /**
   * Cancels a specific task.
   *
   * @param task the task to cancel
   */
  public void cancelTask(BukkitTask task) {
    if (task != null && !task.isCancelled()) {
      task.cancel();
      activeTasks.remove(task);
    }
  }

  /**
   * Cancels a task by its ID.
   *
   * @param taskId the task ID
   */
  public void cancelTask(int taskId) {
    Bukkit.getScheduler().cancelTask(taskId);
    activeTasks.removeIf(task -> task.getTaskId() == taskId);
  }

  /**
   * Gets the number of active tasks.
   *
   * @return the number of active tasks
   */
  public int getActiveTaskCount() {
    activeTasks.removeIf(BukkitTask::isCancelled);
    return activeTasks.size();
  }

  /**
   * Checks if a task is currently running.
   *
   * @param task the task to check
   * @return true if the task is running
   */
  public boolean isTaskRunning(BukkitTask task) {
    return task != null && !task.isCancelled() && activeTasks.contains(task);
  }

  /** Cancels all active tasks. This is called automatically on plugin disable. */
  public void cancelAllTasks() {
    plugin.getLogger().info("Cancelling " + activeTasks.size() + " active tasks...");

    for (BukkitTask task : new HashSet<>(activeTasks)) {
      try {
        if (!task.isCancelled()) {
          task.cancel();
        }
      } catch (Exception e) {
        plugin
            .getLogger()
            .warning("Error cancelling task " + task.getTaskId() + ": " + e.getMessage());
      }
    }

    activeTasks.clear();
    plugin.getLogger().info("All tasks cancelled.");
  }

  /**
   * Converts a time value to ticks.
   *
   * @param time the time value
   * @param unit the time unit
   * @return the equivalent ticks (20 ticks = 1 second)
   */
  private long convertToTicks(long time, TimeUnit unit) {
    long milliseconds = unit.toMillis(time);
    return milliseconds / 50;
  }

  /**
   * Converts seconds to ticks.
   *
   * @param seconds the seconds
   * @return the ticks
   */
  public static long toTicks(double seconds) {
    return (long) (seconds * 20);
  }

  /**
   * Converts ticks to seconds.
   *
   * @param ticks the ticks
   * @return the seconds
   */
  public static double toSeconds(long ticks) {
    return ticks / 20.0;
  }
}
