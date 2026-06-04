# Paper 26.1.2 — Upgrade Notes & Feature Opportunities

> **Prerequisite:** Paper 26.1.2 requires **Java 25 LTS** (class file version 69).
> Current environment is Java 21. Install Java 25, update IntelliJ SDK, bump `java.version`
> to `25` in `pom.xml`, then switch the `paper-api` dependency to `26.1.2.build.69-stable`.

---

## 1. Dialog API

The biggest addition. Dialogs are native Minecraft UI screens the server sends to the client —
not chest inventories. They render as proper modal overlays with real input widgets.

### Dialog types

| Type | Description |
|---|---|
| `Confirmation` | Yes / No with custom button labels |
| `Notice` | Single dismiss button |
| `Multiple Actions` | Up to N labelled buttons |
| `Dialog List` | Navigates to other dialogs |
| `Server Links` | Opens URLs in the browser |

### Input widgets

- **Boolean** — checkbox
- **Single Option** — radio-style button group
- **Text** — free-text string field
- **Number Range** — slider

### How to show a dialog

```java
Dialog confirm = Dialog.create(builder -> builder
    .type(DialogType.CONFIRMATION)
    .title(Component.text("Confirm Prestige"))
    // ... configure buttons and body
);
player.showDialog(confirm);
// Responses fire PlayerCustomClickEvent
// Close with: player.closeDialog()
```

### Evolv3 opportunities

- **`PrestigeConfirmMenu`** — currently a 27-slot inventory using only 3 slots. A native
  `Confirmation` dialog is more polished and impossible to glitch by dropping items.
- **Stone selection** — could complement the slot-machine inventory: run the animation in
  the chest GUI, then confirm the chosen stone via a `Multiple Actions` dialog.
- **Preferences / settings menu** — replace glass-pane toggles with sliders and checkboxes.
- **Cell / island naming** — text input dialog instead of chat-based input.

---

## 2. Data Component API

Direct access to the properties Minecraft stores per-item, bypassing `ItemMeta` where it
falls short. Version-specific but gives far finer control over item appearance.

```java
// Example: force enchantment glint on a stone without a dummy enchant
itemStack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

// Example: set vanilla rarity (controls name colour automatically)
itemStack.setData(DataComponentTypes.RARITY, ItemRarity.EPIC);

// Example: non-italic base name that anvil can't override
itemStack.setData(DataComponentTypes.ITEM_NAME, Component.text("Legendary Stone"));
```

### Most useful components for Evolv3

| Component | Current workaround | Improvement |
|---|---|---|
| `ENCHANTMENT_GLINT_OVERRIDE` | Dummy enchant + `HIDE_ENCHANTS` flag | Remove the hack entirely |
| `RARITY` | Manual colour prefix in display name | Name colour driven by vanilla rarity enum |
| `ITEM_NAME` | `meta.displayName(...)` with italic stripped | Non-renameable base name |
| `USE_EFFECTS` | None | Sound + particle burst on right-click |
| `BREAK_SOUND` | None | Custom sound when pickaxe breaks |
| `TOOLTIP_DISPLAY` | `ItemFlag` combinations | Clean per-section tooltip hiding |
| `MAX_DAMAGE` / `DAMAGE` | `Damageable` meta | Direct durability manipulation |

`EvolutionStone.create()` currently uses a dummy enchant for the glint effect.
`ENCHANTMENT_GLINT_OVERRIDE` removes that entirely.

---

## 3. New Particles for Animations

### Rarity reveal & color transitions

**`DUST_COLOR_TRANSITION`** — smoothly shifts between two `Color` values over the particle's
lifetime. Maps perfectly to rarity reveals: start white, resolve to the rarity color.

```java
world.spawnParticle(
    Particle.DUST_COLOR_TRANSITION,
    location, count,
    new Particle.DustTransition(Color.WHITE, rarityColor, size)
);
```

### Stone flight & movement

**`TRAIL`** — a flowing path that follows a trajectory. Replaces the current particle burst
loop in `PrestigeStoneFlightAnimation` with a path that hugs the stone's arc.

### Ambient atmosphere

| Particle | Best use |
|---|---|
| `FIREFLY` | Idle shimmer around the pickaxe during the evolution animation |
| `ELECTRIC_SPARK` | Sharp crackle at the prestige climax moment |
| `SHRIEK` | Expanding shockwave ring to punctuate a prestige unlock |
| `SCULK_CHARGE` | Spreading energy fan-out for evolution tier-up reveals |

---

## 4. Display Entities with Smooth Interpolation

Available since 1.19.4 but mature in 26.1.2. `ItemDisplay`, `TextDisplay`, and
`BlockDisplay` entities support **client-side keyframe interpolation** — the client smooths
between transforms automatically without a BukkitRunnable tick loop.

### Key animatable properties

| Method | What it does |
|---|---|
| `setInterpolationDuration(ticks)` | Client interpolates between transforms over N ticks |
| `setInterpolationDelay(ticks)` | Delay before interpolation starts |
| `setTeleportDuration(ticks)` | Smooths position updates (0–59) |
| `setTransformation(Transformation)` | Scale, rotate, translate keyframe |
| `setBrightness(new Brightness(15, 15))` | Full bright — glows in the dark |
| `setBillboard(Display.Billboard.CENTER)` | Always faces the player |

### Stone flight upgrade

The current `PrestigeStoneFlightAnimation` moves an item entity via a runnable. Replacing it
with an `ItemDisplay` flying on server-set keyframes + client interpolation gives noticeably
smoother movement:

```java
ItemDisplay display = world.spawn(startLoc, ItemDisplay.class, d -> {
    d.setItemStack(stoneItem);
    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
    d.setBillboard(Display.Billboard.CENTER);
    d.setBrightness(new Display.Brightness(15, 15));
    d.setInterpolationDuration(20); // 1 second smooth glide
    d.setInterpolationDelay(0);
});

// One tick later — set destination transform; client handles the smooth travel
Bukkit.getScheduler().runTaskLater(plugin, () ->
    display.setTransformation(destinationTransform), 1L);
```

---

## 5. Custom Enchantments via Registry API *(Experimental)*

The Bootstrap-phase registry API lets plugins register actual Minecraft enchantments — ones
that appear in the enchantment table, on tooltips natively, and work with `/enchant`.

```java
// In Bootstrap phase only
Lifecycle.RegistryEvent<Enchantment> event = ...;
event.registry().registerCustom(RegistryKey.ENCHANTMENT, "haste_boost", builder -> builder
    .description(Component.text("Haste Boost"))
    .maxLevel(5)
    .minimumCost(EnchantmentCost.of(1, 10))
    .maximumCost(EnchantmentCost.of(5, 10))
);
```

Evolv3's enchants are currently PDC-tracked. Registering them as real enchantments would
mean native tooltip rendering, grindstone / anvil compatibility, and `/enchant` support.

> **Status:** `@Experimental` — API shape may still change. Monitor for stabilisation.

---

## 6. Brigadier Command System

Paper's native command registration replaces the `CommandExecutor` / `TabCompleter` pattern
with Brigadier, giving:

- Typed arguments with built-in validation (integers with ranges, enums, player selectors)
- Instant server-side tab completion — no `AsyncTabCompleteEvent` needed
- Descriptive error messages shown to the player before execution

Worth migrating the `evolv3` admin command tree once on Java 25.

---

## Migration Checklist

- [ ] Install Java 25 JDK and update IntelliJ project SDK
- [ ] Set `java.version` to `25` in `pom.xml`
- [ ] Switch `paper-api` to `26.1.2.build.69-stable`
- [ ] Replace dummy enchant glint in `EvolutionStone.create()` with `ENCHANTMENT_GLINT_OVERRIDE`
- [ ] Replace manual rarity name colouring with `DataComponentTypes.RARITY`
- [ ] Upgrade `PrestigeConfirmMenu` to Dialog API
- [ ] Upgrade `PrestigeStoneFlightAnimation` to use `ItemDisplay` with interpolation
- [ ] Swap static dust particles in rarity reveal for `DUST_COLOR_TRANSITION`
- [ ] Swap flight trail particles for `TRAIL`
- [ ] Migrate admin commands to Brigadier
- [ ] Evaluate custom enchantment registration once `@Experimental` stabilises
