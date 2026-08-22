package org.benski.evolv3.core.interfaces;

import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

public interface DataCenter<T> {

  /**
   * Retrieves data for the specified player via callback. Immediately invoked from cache if
   * present.
   */
  void get(Player player, Consumer<T> callback);

  /** Returns the entire in-memory cache. */
  Map<String, T> getAll();

  /** Returns true if an entry with this ID is currently in cache. */
  boolean exists(String id);

  /**
   * Returns the cached entry for the given player, or null.
   *
   * @throws UnsupportedOperationException if this data center doesn't support player-keyed lookup
   */
  default T getLocal(Player player) {
    throw new UnsupportedOperationException("getLocal(Player) not supported by this DataCenter");
  }

  /**
   * Loads an existing entry from the backing store, or creates a new default entry if none exists.
   * Implementations must be idempotent when called concurrently for the same ID.
   */
  void loadOrCreate(Player player, String id);
}
