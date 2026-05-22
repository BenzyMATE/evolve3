package org.benski.evolv3.core.abstracts;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.benski.evolv3.core.interfaces.DataCenter;
import org.bukkit.entity.Player;

/** Thread-safe cache base for data centers. Subclasses implement loading and persistence logic. */
public abstract class BaseDataCenter<T> implements DataCenter<T> {

  protected final Map<String, T> data = new ConcurrentHashMap<>();

  /**
   * Tracks IDs whose load is already in-flight, preventing duplicate loads when two requests for
   * the same ID arrive before either async callback returns.
   */
  private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

  @Override
  public Map<String, T> getAll() {
    return data;
  }

  @Override
  public boolean exists(String id) {
    return data.containsKey(id);
  }

  @Override
  public T getLocal(Player player) {
    return data.get(player.getUniqueId().toString());
  }

  @Override
  public void updateLocal(T profile, boolean save) {
    data.put(getProfileId(profile), profile);
  }

  /** Attempts to mark this ID as in-flight. Returns true if the caller should proceed. */
  protected boolean tryMarkInFlight(String id) {
    return inFlight.add(id);
  }

  protected void clearInFlight(String id) {
    inFlight.remove(id);
  }

  protected abstract String getProfileId(T profile);
}
