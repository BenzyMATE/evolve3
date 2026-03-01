package org.benski.evolv3.animations;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.benski.evolv3.core.abstracts.BaseAnimation;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Premium enchantment selection animation.
 *
 * The pickaxe flies out IN FRONT of the player where they can see it.
 * Enchanted books shoot TOWARDS the player, settle in a fan formation.
 * On selection, the book flies back INTO the pickaxe, then the pickaxe
 * returns to the player's hand.
 */
public class EnchantSelectionAnimation extends BaseAnimation
{
    // === TIMING (in ticks) ===
    private static final int FLY_OUT_DURATION = 20;       // Pickaxe flies out (1s)
    private static final int CHARGE_START = 20;           // Energy builds
    private static final int BOOKS_SHOOT = 35;            // Books shoot out
    private static final int BOOKS_SETTLE = 55;           // Books reach position
    private static final int SELECTION_READY = 60;        // Can select
    private static final int MAX_DURATION = 500;          // 25 seconds max

    // === POSITIONING ===
    private static final double ITEM_DISTANCE = 3.0;      // Distance in front of player
    private static final double ITEM_HEIGHT = 1.5;        // Eye level
    private static final double BOOK_SPREAD = 1.2;        // Horizontal spread between books
    private static final double BOOK_DISTANCE = 1.8;      // Books closer to player than pickaxe

    // === VISUAL SETTINGS ===
    private static final float ITEM_SCALE = 0.7f;
    private static final float BOOK_SCALE = 0.5f;
    private static final float LABEL_SCALE = 0.8f;

    // === COLORS ===
    private static final Color MAGIC_PURPLE = Color.fromRGB(138, 43, 226);
    private static final Color MAGIC_CYAN = Color.fromRGB(0, 255, 255);
    private static final Color MAGIC_GOLD = Color.fromRGB(255, 215, 0);

    // === STATE ===
    private final Player player;
    private final List<String> enchantNames;
    private final BiConsumer<Player, String> onSelect;
    private ItemStack originalItem;
    private ItemDisplay floatingItem;
    private final List<EnchantBook> books = new ArrayList<>();
    private int selectedIndex = -1;
    private boolean selectionMade = false;
    private boolean itemReturned = false;
    private int selectionTick = -1;

    // Cached direction (player's facing when animation started)
    private Vector forwardDirection;
    private Vector rightDirection;
    private Location itemTargetLocation;

    private BukkitRunnable clickDetector;

    public EnchantSelectionAnimation(JavaPlugin plugin, Player player, List<String> enchantNames,
                                      BiConsumer<Player, String> onSelect) {
        super(plugin, MAX_DURATION);
        this.player = player;
        this.enchantNames = enchantNames.size() >= 3 ? enchantNames : List.of("Option 1", "Option 2", "Option 3");
        this.onSelect = onSelect;
    }

    public EnchantSelectionAnimation(JavaPlugin plugin) {
        this(plugin, null, List.of("⠌", "Fortune III", "Efficiency V"), null);
    }

    @Override
    protected void onStart() {
        if (player == null || !player.isOnline()) {
            stop();
            return;
        }

        Location origin = getOrigin();
        if (origin == null || origin.getWorld() == null) return;

        World world = origin.getWorld();

        // Cache the player's facing direction (horizontal only)
        forwardDirection = player.getLocation().getDirection().setY(0).normalize();
        rightDirection = forwardDirection.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        // Calculate where the pickaxe should float (in front of player)
        itemTargetLocation = player.getEyeLocation().add(forwardDirection.clone().multiply(ITEM_DISTANCE));
        itemTargetLocation.setY(player.getLocation().getY() + ITEM_HEIGHT);

        // Take the item from player's hand
        originalItem = player.getInventory().getItemInMainHand().clone();
        if (originalItem.getType() == Material.AIR) {
            originalItem = new ItemStack(Material.DIAMOND_PICKAXE);
        }
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

        // Initial pickup sound
        world.playSound(origin, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);

        // Create the floating item at hand position (will fly forward)
        Location itemStart = player.getLocation().add(0, 1.0, 0).add(forwardDirection.clone().multiply(0.5));
        floatingItem = world.spawn(itemStart, ItemDisplay.class, entity -> {
            entity.setItemStack(originalItem);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(0.3f, 0.3f, 0.3f),
                new AxisAngle4f(0, 0, 1, 0)
            ));
            entity.setPersistent(false);
        });

        startClickDetection();
    }

    private void startClickDetection() {
        clickDetector = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isPlaying() || selectionMade || player == null || !player.isOnline()) {
                    cancel();
                    return;
                }

                if (getCurrentTick() < SELECTION_READY) return;

                // Detect which book player is looking at
                Location eye = player.getEyeLocation();
                Vector direction = eye.getDirection();

                int lookingAt = -1;
                double bestDot = 0.9; // Tight threshold

                for (int i = 0; i < books.size(); i++) {
                    EnchantBook book = books.get(i);
                    if (book.display == null || book.display.isDead()) continue;
                    if (book.currentPosition == null) continue;

                    Vector toBook = book.currentPosition.toVector().subtract(eye.toVector()).normalize();
                    double dot = direction.dot(toBook);

                    if (dot > bestDot) {
                        bestDot = dot;
                        lookingAt = i;
                    }
                }

                for (int i = 0; i < books.size(); i++) {
                    books.get(i).highlighted = (i == lookingAt);
                }
            }
        };
        clickDetector.runTaskTimer(plugin, 1L, 1L);
    }

    public void confirmSelection() {
        if (selectionMade || getCurrentTick() < SELECTION_READY) return;

        for (int i = 0; i < books.size(); i++) {
            if (books.get(i).highlighted) {
                selectBook(i);
                return;
            }
        }
    }

    public void selectBook(int index) {
        if (selectionMade || index < 0 || index >= 3) return;

        selectedIndex = index;
        selectionMade = true;
        selectionTick = getCurrentTick();

        Location origin = getOrigin();
        if (origin != null && origin.getWorld() != null) {
            World world = origin.getWorld();
            world.playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
            world.playSound(origin, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
        }
    }

    @Override
    protected void onTick(double progress) {
        if (player == null || !player.isOnline()) {
            stop();
            return;
        }

        Location playerLoc = player.getLocation();
        if (playerLoc.getWorld() == null) return;

        World world = playerLoc.getWorld();
        int tick = getCurrentTick();

        // Update target location to stay in front of player
        itemTargetLocation = player.getEyeLocation().add(forwardDirection.clone().multiply(ITEM_DISTANCE));
        itemTargetLocation.setY(playerLoc.getY() + ITEM_HEIGHT);

        // === PHASE 1: PICKAXE FLIES OUT IN FRONT ===
        animateItem(playerLoc, world, tick);

        // === PHASE 2: CHARGE EFFECT ===
        if (tick >= CHARGE_START && tick < BOOKS_SHOOT) {
            animateCharge(world, tick);
        }

        // === PHASE 3: BOOKS SHOOT TOWARDS PLAYER ===
        if (tick == BOOKS_SHOOT) {
            launchBooks(playerLoc, world);
        }

        // === PHASE 4: BOOKS IN MOTION ===
        if (tick > BOOKS_SHOOT) {
            animateBooks(playerLoc, world, tick);
        }

        // === AMBIENT EFFECTS ===
        if (tick > FLY_OUT_DURATION && !selectionMade) {
            ambientEffects(world, tick);
        }

        // === SELECTION HINT ===
        if (tick == SELECTION_READY) {
            player.sendActionBar(Component.text("Look at an enchant and ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text("LEFT-CLICK")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD))
                .append(Component.text(" to select")
                    .color(NamedTextColor.YELLOW)));
        }

        // === AUTO-TIMEOUT ===
        if (tick >= MAX_DURATION - 40 && !selectionMade) {
            selectBook(0);
        }
    }

    private void animateItem(Location playerLoc, World world, int tick) {
        if (floatingItem == null || floatingItem.isDead()) return;

        Location handPos = playerLoc.clone().add(0, 1.0, 0).add(forwardDirection.clone().multiply(0.5));

        // Flying out phase
        double flyProgress = Math.min(1.0, (double) tick / FLY_OUT_DURATION);
        double easedFly = easeOutCubic(flyProgress);

        // Interpolate from hand to target position
        Location currentItemLoc = lerpLocation(handPos, itemTargetLocation, easedFly);

        // Gentle bob after settling
        if (flyProgress >= 1.0 && !selectionMade) {
            double bob = Math.sin(tick * 0.08) * 0.03;
            currentItemLoc.add(0, bob, 0);
        }

        // Scale
        float currentScale = 0.3f + ((float) easedFly * (ITEM_SCALE - 0.3f));

        // Handle selection - book merges, then pickaxe returns
        if (selectionMade) {
            int selProgress = tick - selectionTick;

            // Phase 1: Wait for book to merge (0-35 ticks)
            // Phase 2: Pickaxe returns to player (35-60 ticks)
            if (selProgress > 35) {
                int returnProgress = selProgress - 35;
                double returnT = Math.min(1.0, returnProgress / 25.0);
                double easedReturn = easeInOutCubic(returnT);

                // Fly back to player's hand
                currentItemLoc = lerpLocation(itemTargetLocation, handPos, easedReturn);

                // Pulse effect
                currentScale = ITEM_SCALE * (1.0f + (float)(Math.sin(returnProgress * 0.4) * 0.08));

                if (returnT >= 1.0 && !itemReturned) {
                    returnItemToPlayer();
                }
            } else {
                // Still waiting for book merge - pulse with energy
                currentScale = ITEM_SCALE * (1.0f + (float)(Math.sin(selProgress * 0.3) * 0.1));
            }
        }

        // Rotation - face the player
        float yaw = (float) Math.toRadians(playerLoc.getYaw() + 180);
        float spinRotation = (float) (tick * 0.05);

        floatingItem.teleport(currentItemLoc);
        floatingItem.setTransformation(new Transformation(
            new Vector3f(-currentScale / 2, -currentScale / 2, -currentScale / 2),
            new AxisAngle4f(spinRotation, 0, 1, 0),
            new Vector3f(currentScale, currentScale, currentScale),
            new AxisAngle4f(0, 0, 1, 0)
        ));

        // Trail particles while flying out
        if (tick < FLY_OUT_DURATION && tick % 2 == 0) {
            world.spawnParticle(Particle.END_ROD, currentItemLoc, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private void animateCharge(World world, int tick) {
        int chargeProgress = tick - CHARGE_START;
        double intensity = (double) chargeProgress / (BOOKS_SHOOT - CHARGE_START);

        Location itemLoc = floatingItem != null ? floatingItem.getLocation() : itemTargetLocation;

        // Energy spiraling around the pickaxe
        if (tick % 2 == 0) {
            double spiralAngle = tick * 0.5;
            double spiralRadius = 0.5 * (1 - intensity * 0.3);

            for (int i = 0; i < 2; i++) {
                double angle = spiralAngle + (Math.PI * i);
                Location spiralLoc = itemLoc.clone().add(
                    Math.cos(angle) * spiralRadius,
                    Math.sin(angle * 2) * 0.3,
                    Math.sin(angle) * spiralRadius
                );
                world.spawnParticle(Particle.DUST, spiralLoc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(MAGIC_PURPLE, 0.8f));
            }
        }

        // Building sound
        if (chargeProgress == 5) {
            world.playSound(itemLoc, Sound.BLOCK_BEACON_AMBIENT, 0.4f, 1.5f);
        }

        // Burst before books launch
        if (tick == BOOKS_SHOOT - 1) {
            world.playSound(itemLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.8f);
            world.spawnParticle(Particle.END_ROD, itemLoc, 12, 0.15, 0.15, 0.15, 0.08);
        }
    }

    private void launchBooks(Location playerLoc, World world) {
        Location itemLoc = floatingItem != null ? floatingItem.getLocation() : itemTargetLocation;

        // Launch sound
        world.playSound(itemLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.4f, 1.6f);

        Color[] bookColors = {MAGIC_GOLD, MAGIC_CYAN, MAGIC_PURPLE};
        NamedTextColor[] labelColors = {NamedTextColor.GOLD, NamedTextColor.AQUA, NamedTextColor.LIGHT_PURPLE};

        // Books spread horizontally in front of player
        double[] horizontalOffsets = {-BOOK_SPREAD, 0, BOOK_SPREAD};

        for (int i = 0; i < 3; i++) {
            final String enchantName = enchantNames.get(i);
            final Color bookColor = bookColors[i];
            final NamedTextColor labelColor = labelColors[i];

            // Calculate final position - between player and pickaxe, spread horizontally
            Location finalPos = playerLoc.clone()
                .add(0, ITEM_HEIGHT, 0)
                .add(forwardDirection.clone().multiply(BOOK_DISTANCE))
                .add(rightDirection.clone().multiply(horizontalOffsets[i]));

            // Create book at pickaxe location
            ItemDisplay bookDisplay = world.spawn(itemLoc.clone(), ItemDisplay.class, entity -> {
                entity.setItemStack(new ItemStack(Material.ENCHANTED_BOOK));
                entity.setBrightness(new Display.Brightness(15, 15));
                entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.1f, 0.1f, 0.1f),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
                entity.setPersistent(false);
            });

            // Create label
            TextDisplay label = world.spawn(itemLoc.clone(), TextDisplay.class, entity -> {
                entity.text(Component.text(enchantName)
                    .color(labelColor)
                    .decorate(TextDecoration.BOLD));
                entity.setBrightness(new Display.Brightness(15, 15));
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setBackgroundColor(Color.fromARGB(160, 20, 20, 30));
                entity.setShadowed(true);
                entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
                entity.setPersistent(false);
            });

            books.add(new EnchantBook(bookDisplay, label, itemLoc.clone(), finalPos, enchantName, bookColor));
        }
    }

    private void animateBooks(Location playerLoc, World world, int tick) {
        Location itemLoc = floatingItem != null ? floatingItem.getLocation() : itemTargetLocation;
        int launchProgress = tick - BOOKS_SHOOT;
        double settleProgress = Math.min(1.0, launchProgress / 20.0);
        double eased = easeOutBack(settleProgress);

        // Update book target positions (in case player moved)
        double[] horizontalOffsets = {-BOOK_SPREAD, 0, BOOK_SPREAD};

        for (int i = 0; i < books.size(); i++) {
            EnchantBook book = books.get(i);
            if (book.display == null || book.display.isDead()) continue;

            // Update final position based on current player position
            Location finalPos = playerLoc.clone()
                .add(0, ITEM_HEIGHT, 0)
                .add(forwardDirection.clone().multiply(BOOK_DISTANCE))
                .add(rightDirection.clone().multiply(horizontalOffsets[i]));

            // Arc trajectory from pickaxe towards player
            double arcHeight = Math.sin(settleProgress * Math.PI) * 0.4;
            Location currentPos = lerpLocation(book.startPosition, finalPos, eased);
            currentPos.add(0, arcHeight, 0);

            // Gentle hover after settling
            if (settleProgress >= 1.0 && !selectionMade) {
                double bob = Math.sin(tick * 0.1 + i * 2) * 0.04;
                double sway = Math.sin(tick * 0.05 + i) * 0.02;
                currentPos = finalPos.clone().add(rightDirection.clone().multiply(sway)).add(0, bob, 0);
            }

            // Handle selection
            float finalScale = BOOK_SCALE * (float) Math.min(1.0, settleProgress * 2);
            float labelScale = LABEL_SCALE * (float) Math.min(1.0, Math.max(0, (settleProgress - 0.5) * 2));

            if (selectionMade) {
                int selProgress = tick - selectionTick;
                double mergeProgress = Math.min(1.0, selProgress / 35.0);

                if (i == selectedIndex) {
                    // Selected book flies BACK to the pickaxe
                    currentPos = lerpLocation(
                        book.currentPosition != null ? book.currentPosition : currentPos,
                        itemLoc,
                        easeInOutCubic(mergeProgress)
                    );
                    finalScale = BOOK_SCALE * (1.2f - (float) mergeProgress * 0.9f);
                    labelScale = LABEL_SCALE * (1 - (float) mergeProgress);

                    // Energy trail back to pickaxe
                    if (selProgress % 2 == 0 && mergeProgress < 0.9) {
                        world.spawnParticle(Particle.DUST, currentPos, 3, 0.08, 0.08, 0.08, 0,
                            new Particle.DustOptions(book.color, 1.0f));
                    }

                    // Merge burst
                    if (mergeProgress >= 0.95 && mergeProgress < 1.0) {
                        world.spawnParticle(Particle.END_ROD, itemLoc, 10, 0.1, 0.1, 0.1, 0.05);
                        world.playSound(itemLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);
                    }
                } else {
                    // Non-selected books fade out
                    double fadeProgress = easeOutCubic(mergeProgress);
                    finalScale = BOOK_SCALE * (1 - (float) fadeProgress);
                    labelScale = LABEL_SCALE * (1 - (float) fadeProgress);

                    // Drift away and down
                    if (book.currentPosition != null) {
                        currentPos = book.currentPosition.clone();
                        currentPos.add(rightDirection.clone().multiply(horizontalOffsets[i] * fadeProgress * 0.3));
                        currentPos.add(0, -fadeProgress * 0.4, 0);
                    }
                }
            } else if (book.highlighted && tick >= SELECTION_READY) {
                // Highlighted: pulse and glow
                float pulse = 1.0f + (float) Math.sin(tick * 0.3) * 0.15f;
                finalScale = BOOK_SCALE * pulse;
                labelScale = LABEL_SCALE * (0.9f + pulse * 0.15f);

                // Particles around highlighted book
                if (tick % 4 == 0) {
                    world.spawnParticle(Particle.END_ROD, currentPos, 1, 0.15, 0.15, 0.15, 0.01);
                }
            }

            // Update position
            book.currentPosition = currentPos.clone();
            book.display.teleport(currentPos);

            float bookRotation = (float) (tick * 0.06 + i);
            book.display.setTransformation(new Transformation(
                new Vector3f(-finalScale / 2, -finalScale / 2, -finalScale / 2),
                new AxisAngle4f(bookRotation, 0, 1, 0),
                new Vector3f(finalScale, finalScale, finalScale),
                new AxisAngle4f(0, 0, 1, 0)
            ));

            // Update label
            if (book.label != null && !book.label.isDead()) {
                Location labelLoc = currentPos.clone().add(0, 0.35, 0);
                book.label.teleport(labelLoc);

                if (book.highlighted && tick >= SELECTION_READY && !selectionMade) {
                    book.label.text(Component.text(book.enchantName)
                        .color(NamedTextColor.WHITE)
                        .decorate(TextDecoration.BOLD));
                }

                book.label.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(labelScale, labelScale, labelScale),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
            }

            // Launch trail
            if (settleProgress < 1.0 && launchProgress % 2 == 0) {
                world.spawnParticle(Particle.DUST, currentPos, 2, 0.04, 0.04, 0.04, 0,
                    new Particle.DustOptions(book.color, 0.6f));
            }
        }
    }

    private void ambientEffects(World world, int tick) {
        Location itemLoc = floatingItem != null ? floatingItem.getLocation() : itemTargetLocation;

        // Subtle particles around pickaxe
        if (tick % 8 == 0) {
            double angle = Math.random() * Math.PI * 2;
            double radius = 0.25 + Math.random() * 0.15;
            Location floatLoc = itemLoc.clone().add(
                Math.cos(angle) * radius,
                -0.2 + Math.random() * 0.4,
                Math.sin(angle) * radius
            );
            world.spawnParticle(Particle.END_ROD, floatLoc, 1, 0, 0.01, 0, 0.003);
        }

        // Ambient chime
        if (tick % 100 == 50) {
            world.playSound(itemLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.15f, 1.0f + (float) Math.random() * 0.3f);
        }
    }

    private void returnItemToPlayer() {
        if (itemReturned || player == null || !player.isOnline()) return;
        itemReturned = true;

        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();

        if (world != null) {
            // Completion burst at player
            Location handLoc = playerLoc.clone().add(0, 1.2, 0);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, handLoc, 25, 0.3, 0.3, 0.3, 0.1);
            world.spawnParticle(Particle.END_ROD, handLoc, 15, 0.2, 0.2, 0.2, 0.05);

            world.playSound(playerLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
            world.playSound(playerLoc, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
        }

        // Return item to player
        player.getInventory().setItemInMainHand(originalItem);

        // Callback
        if (onSelect != null && selectedIndex >= 0 && selectedIndex < enchantNames.size()) {
            onSelect.accept(player, enchantNames.get(selectedIndex));
        }

        // Stop animation
        new BukkitRunnable() {
            @Override
            public void run() {
                stop();
            }
        }.runTaskLater(plugin, 10L);
    }

    @Override
    protected void onComplete() {
        if (clickDetector != null) {
            clickDetector.cancel();
        }

        if (floatingItem != null && !floatingItem.isDead()) {
            floatingItem.remove();
        }

        for (EnchantBook book : books) {
            if (book.display != null && !book.display.isDead()) {
                book.display.remove();
            }
            if (book.label != null && !book.label.isDead()) {
                book.label.remove();
            }
        }
        books.clear();

        if (!itemReturned && player != null && player.isOnline() && originalItem != null) {
            player.getInventory().setItemInMainHand(originalItem);
        }
    }

    @Override
    public int getResourceCost() {
        return 5;
    }

    // === UTILITY METHODS ===

    private double easeOutBack(double x) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    private double easeOutCubic(double x) {
        return 1 - Math.pow(1 - x, 3);
    }

    private double easeInOutCubic(double x) {
        return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    }

    private Location lerpLocation(Location a, Location b, double t) {
        return new Location(
            a.getWorld(),
            a.getX() + (b.getX() - a.getX()) * t,
            a.getY() + (b.getY() - a.getY()) * t,
            a.getZ() + (b.getZ() - a.getZ()) * t
        );
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * Enchant book state.
     */
    private static class EnchantBook {
        ItemDisplay display;
        TextDisplay label;
        Location startPosition;
        Location targetPosition;
        Location currentPosition;
        String enchantName;
        Color color;
        boolean highlighted = false;

        EnchantBook(ItemDisplay display, TextDisplay label, Location startPosition,
                   Location targetPosition, String enchantName, Color color) {
            this.display = display;
            this.label = label;
            this.startPosition = startPosition;
            this.targetPosition = targetPosition;
            this.currentPosition = startPosition.clone();
            this.enchantName = enchantName;
            this.color = color;
        }
    }
}
