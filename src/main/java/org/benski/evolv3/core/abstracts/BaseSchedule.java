package org.benski.evolv3.core.abstracts;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Base class for all scheduled/repeating tasks in the plugin. Provides common functionality and
 * lifecycle management.
 */
public abstract class BaseSchedule {
  protected final JavaPlugin plugin;
  protected final FileConfiguration config;
  private BukkitTask task;

  public BaseSchedule(JavaPlugin plugin) {
    this.plugin = plugin;
    this.config = plugin.getConfig();
  }

  /**
   * Gets the interval in ticks between executions. 20 ticks = 1 second Override this to provide a
   * default value if config is not set.
   *
   * @return the interval in ticks
   */
  public abstract long getIntervalTicks();

  /**
   * Gets the config path for this schedule's interval. Return null to use getIntervalTicks()
   * directly without config.
   *
   * @return the config path, or null
   */
  public String getIntervalConfigPath() {
    return null;
  }

  /**
   * Gets the actual interval to use, checking config first. Falls back to getIntervalTicks() if no
   * config path is set.
   *
   * @return the interval in ticks
   */
  public final long getActualInterval() {
    String configPath = getIntervalConfigPath();
    if (configPath != null && config.contains(configPath)) {
      return config.getLong(configPath);
    }
    return getIntervalTicks();
  }

  /**
   * Gets the initial delay before the first execution. Default is 0 (start immediately).
   *
   * @return the delay in ticks
   */
  public long getDelayTicks() {
    return 0;
  }

  /**
   * Whether this schedule should run asynchronously. Default is false (runs on main thread).
   * WARNING: If true, do NOT use Bukkit API!
   *
   * @return true to run async, false for sync
   */
  public boolean isAsync() {
    return false;
  }

  /**
   * The task to execute on each interval. This is called repeatedly based on getIntervalTicks().
   */
  public abstract void run();

  /** Called when the schedule is first started. Override to perform initialization logic. */
  public void onStart() {}

  /** Called when the schedule is stopped. Override to perform cleanup logic. */
  public void onStop() {}

  /**
   * Gets the BukkitTask instance.
   *
   * @return the task, or null if not started
   */
  public BukkitTask getTask() {
    return task;
  }

  /**
   * Sets the BukkitTask instance. Called by ScheduleRegistryManager.
   *
   * @param task the task
   */
  public void setTask(BukkitTask task) {
    this.task = task;
  }

  /**
   * Checks if this schedule is currently running.
   *
   * @return true if running
   */
  public boolean isRunning() {
    return task != null && !task.isCancelled();
  }

  /**
   * Gets the task ID.
   *
   * @return the task ID, or -1 if not started
   */
  public int getTaskId() {
    return task != null ? task.getTaskId() : -1;
  }
}
