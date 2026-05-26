package org.benski.evolv3.core.types;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for menu types in the plugin. Unlike an enum, this allows dynamic registration of menu
 * types at runtime. This means anyone can register custom menu types without modifying core code.
 */
public class MenuType {
  private static final Map<String, MenuType> REGISTRY = new HashMap<>();

  private final String identifier;

  /** Creates a new MenuType with the given identifier. */
  private MenuType(String identifier) {
    this.identifier = identifier;
  }

  /**
   * Registers a new menu type. If a menu type with this identifier already exists, returns the
   * existing one.
   *
   * @param identifier the unique identifier for this menu type
   * @return the MenuType instance
   */
  public static MenuType register(String identifier) {
    return REGISTRY.computeIfAbsent(identifier.toLowerCase(), MenuType::new);
  }

  /**
   * Gets a MenuType by its identifier. Returns null if not registered.
   *
   * @param identifier the identifier to look up
   * @return the MenuType, or null if not found
   */
  public static MenuType fromIdentifier(String identifier) {
    return REGISTRY.get(identifier.toLowerCase());
  }

  /**
   * Gets the unique identifier for this menu type.
   *
   * @return the identifier
   */
  public String getIdentifier() {
    return identifier;
  }

  /**
   * Gets all registered menu types.
   *
   * @return array of all menu types
   */
  public static MenuType[] values() {
    return REGISTRY.values().toArray(new MenuType[0]);
  }

  /** To string. */
  @Override
  public String toString() {
    return identifier;
  }

  /** Equals. */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof MenuType)) return false;
    MenuType other = (MenuType) obj;
    return identifier.equals(other.identifier);
  }

  /** Returns {@code true} if this has h code. */
  @Override
  public int hashCode() {
    return identifier.hashCode();
  }
}
