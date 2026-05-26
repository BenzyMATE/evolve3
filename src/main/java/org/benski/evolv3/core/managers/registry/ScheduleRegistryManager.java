package org.benski.evolv3.core.managers.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.benski.evolv3.core.abstracts.BaseSchedule;
import org.benski.evolv3.core.managers.world.TaskManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Core manager that handles schedule registration and management. Automatically starts repeating
 * tasks and manages their lifecycle.
 */
public class ScheduleRegistryManager {
  private final JavaPlugin plugin;
  private final TaskManager taskManager;
  private final Map<String, BaseSchedule> scheduleMap = new HashMap<>();
  private final List<Class<? extends BaseSchedule>> scheduleClasses = new ArrayList<>();

  /** Creates the ScheduleRegistryManager. @param plugin the owning plugin */
  public ScheduleRegistryManager(JavaPlugin plugin) {
    this.plugin = plugin;
    this.taskManager = TaskManager.getInstance();
  }

  /**
   * Registers a schedule class to be initialized later.
   *
   * @param scheduleClass the schedule class to register
   */
  public void registerSchedule(Class<? extends BaseSchedule> scheduleClass) {
    scheduleClasses.add(scheduleClass);
  }

  /** Initializes all registered schedules. Creates instances and starts them automatically. */
  public void initializeSchedules() {
    for (Class<? extends BaseSchedule> scheduleClass : scheduleClasses) {
      try {
        BaseSchedule schedule = scheduleClass.getConstructor(JavaPlugin.class).newInstance(plugin);

        String identifier = scheduleClass.getSimpleName().replace("Schedule", "").toLowerCase();

        scheduleMap.put(identifier, schedule);

        startSchedule(schedule);

        plugin
            .getLogger()
            .info("Registered and started schedule: " + scheduleClass.getSimpleName());

      } catch (Exception e) {
        plugin
            .getLogger()
            .severe(
                "Failed to register schedule "
                    + scheduleClass.getSimpleName()
                    + ": "
                    + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  /**
   * Starts a schedule.
   *
   * @param schedule the schedule to start
   */
  private void startSchedule(BaseSchedule schedule) {
    schedule.onStart();

    long interval = schedule.getActualInterval();

    BukkitTask task;
    if (schedule.isAsync()) {
      task = taskManager.runAsyncTimer(schedule::run, schedule.getDelayTicks(), interval);
    } else {
      task = taskManager.runTimer(schedule::run, schedule.getDelayTicks(), interval);
    }

    schedule.setTask(task);
  }

  /**
   * Stops a specific schedule.
   *
   * @param identifier the schedule identifier
   */
  public void stopSchedule(String identifier) {
    BaseSchedule schedule = scheduleMap.get(identifier.toLowerCase());
    if (schedule != null && schedule.isRunning()) {
      taskManager.cancelTask(schedule.getTask());
      schedule.onStop();
      plugin.getLogger().info("Stopped schedule: " + identifier);
    }
  }

  /**
   * Restarts a specific schedule.
   *
   * @param identifier the schedule identifier
   */
  public void restartSchedule(String identifier) {
    BaseSchedule schedule = scheduleMap.get(identifier.toLowerCase());
    if (schedule != null) {
      if (schedule.isRunning()) {
        taskManager.cancelTask(schedule.getTask());
        schedule.onStop();
      }
      startSchedule(schedule);
      plugin.getLogger().info("Restarted schedule: " + identifier);
    }
  }

  /**
   * Gets a schedule instance by identifier.
   *
   * @param identifier the schedule identifier
   * @return the schedule instance, or null if not found
   */
  public BaseSchedule getSchedule(String identifier) {
    return scheduleMap.get(identifier.toLowerCase());
  }

  /**
   * Gets a typed schedule instance by identifier.
   *
   * @param identifier the schedule identifier
   * @param type the expected schedule class
   * @param <T> the schedule type
   * @return the typed schedule instance, or null if not found or wrong type
   */
  @SuppressWarnings("unchecked")
  public <T extends BaseSchedule> T getSchedule(String identifier, Class<T> type) {
    BaseSchedule schedule = scheduleMap.get(identifier.toLowerCase());
    if (schedule == null) {
      return null;
    }

    try {
      return (T) schedule;
    } catch (ClassCastException e) {
      plugin.getLogger().warning("Schedule " + identifier + " is not of expected type");
      return null;
    }
  }

  /**
   * Checks if a schedule is registered.
   *
   * @param identifier the schedule identifier
   * @return true if registered
   */
  public boolean isScheduleRegistered(String identifier) {
    return scheduleMap.containsKey(identifier.toLowerCase());
  }

  /**
   * Gets all registered schedules.
   *
   * @return map of all schedules
   */
  public Map<String, BaseSchedule> getAllSchedules() {
    return new HashMap<>(scheduleMap);
  }

  /** Cleanup all registered schedules. Stops all running schedules and clears internal state. */
  public void cleanup() {
    for (Map.Entry<String, BaseSchedule> entry : scheduleMap.entrySet()) {
      try {
        String identifier = entry.getKey();
        BaseSchedule schedule = entry.getValue();

        if (schedule.isRunning()) {
          taskManager.cancelTask(schedule.getTask());
          schedule.onStop();
        }

        plugin.getLogger().info("Cleaned up schedule: " + identifier);
      } catch (Exception e) {
        plugin.getLogger().warning("Error cleaning up schedule: " + e.getMessage());
      }
    }

    scheduleMap.clear();
    scheduleClasses.clear();
  }
}
