package org.benski.evolv3.core.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.abstracts.BaseEnchantAnimation;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.managers.EnchantAnimationConfig;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.types.EnchantOption;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PickaxeDataCenter;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-player sidebar scoreboards.
 *
 * <p>Each player receives their own {@link Scoreboard} instance with a fixed
 * set of {@link Team} objects — one per line. Lines are updated by changing the
 * team prefix (not by removing and re-adding score entries), which eliminates
 * the flicker that occurs with naive add/remove approaches.</p>
 *
 * <p>Diff-based rendering: the last rendered text for each line is cached.
 * {@link Team#prefix(Component)} is only called when the text actually changes,
 * keeping packet count minimal even at high update rates.</p>
 *
 * <p>The line containing {@code %evolv3_pickaxe_evolution%} receives special
 * treatment: it alternates between the Evo display and an atoms count display
 * using a typewriter-style reveal animation between the two.</p>
 *
 * <p>Supports up to 15 sidebar lines (Minecraft's hard limit).
 * Configure lines, title, and update rate in {@code default.yml}.</p>
 */
public final class EvolveScoreboardManager
{
    private static final EvolveScoreboardManager INSTANCE = new EvolveScoreboardManager();

    /** Maximum lines the Minecraft sidebar can display. */
    private static final int MAX_LINES = 15;

    /** Ticks between animation task runs — drives typewriter speed. */
    private static final long ANIM_INTERVAL_TICKS = 2L;

    /**
     * Unique invisible entry strings used as score entries.
     * Control codes render as nothing, ensuring lines appear blank rather than
     * showing the raw entry name. Each must be distinct within the scoreboard.
     */
    private static final String[] ENTRIES;

    static {
        ENTRIES = new String[MAX_LINES];
        String codes = "0123456789abcde";
        for (int i = 0; i < MAX_LINES; i++) {
            ENTRIES[i] = "§" + codes.charAt(i);
        }
    }

    /** Animation phases for the evo/atom toggle line. */
    private enum EvoAtomPhase {
        EVO_DISPLAY,   // Evo text displayed, counting down to switch
        EVO_ERASE,     // Typewriter-erasing Evo text from right to left
        ATOMS_REVEAL,  // Typewriter-revealing atoms text from left to right
        ATOMS_DISPLAY, // Atoms text displayed, counting down to switch
        ATOMS_ERASE,   // Typewriter-erasing atoms text from right to left
        EVO_REVEAL     // Typewriter-revealing Evo text from left to right
    }

    private JavaPlugin plugin;
    private BukkitTask updateTask;
    private BukkitTask animTask;
    private final Map<UUID, PlayerState> states = new HashMap<>();

    private EvolveScoreboardManager() {}

    public static EvolveScoreboardManager getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void initialize(JavaPlugin plugin) {
        this.plugin = plugin;
        startTasks();
    }

    public void cleanup() {
        stopTasks();
        states.clear();
    }

    // -------------------------------------------------------------------------
    // Player management
    // -------------------------------------------------------------------------

    /** Sets up and assigns a fresh scoreboard for the given player. */
    public void setup(Player player) {
        if (!isEnabled()) return;

        // Tear down any existing state first (e.g. re-join before quit cleanup)
        teardown(player);

        FileConfiguration cfg   = config();
        List<String>      lines = cfg.getStringList(ConfigPaths.Default.Scoreboard.LINES);
        int lineCount = Math.min(lines.size(), MAX_LINES);
        if (lineCount == 0) return;

        Scoreboard board     = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective  objective = board.registerNewObjective(
                "evolv3sb", Criteria.DUMMY, Component.empty());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank()); // suppress score numbers from all sidebar lines

        // Set title (resolved once — title rarely changes)
        String titleRaw = cfg.getString(ConfigPaths.Default.Scoreboard.TITLE, "&3&lEvolv3");
        objective.displayName(legacy(Utils.formatMessage(player, titleRaw)));

        List<Team>   teams     = new ArrayList<>(lineCount);
        List<String> lastTexts = new ArrayList<>(lineCount);
        int[]        scores    = new int[lineCount];
        boolean[]    shown     = new boolean[lineCount];

        // Find which line is the evo/atom toggle line, the level line, and the progress bar line
        int evoLineIndex         = -1;
        int levelLineIndex       = -1;
        int progressbarLineIndex = -1;
        for (int i = 0; i < lineCount; i++) {
            String l = lines.get(i);
            if (l.contains("%evolv3_pickaxe_evolution%"))                                                   evoLineIndex         = i;
            if (l.contains("%evolv3_pickaxe_level%") && !l.contains("%evolv3_pickaxe_evolution%"))         levelLineIndex       = i;
            if (l.contains("%evolv3_pickaxe_progressbar%"))                                                progressbarLineIndex = i;
        }

        for (int i = 0; i < lineCount; i++) {
            Team team = board.registerNewTeam("evolv3_" + i);
            team.addEntry(ENTRIES[i]);
            int score = lineCount - i;
            objective.getScore(ENTRIES[i]).setScore(score);
            scores[i] = score;
            shown[i]  = true;
            teams.add(team);
            lastTexts.add(null); // null forces render on first update
        }

        states.put(player.getUniqueId(),
                new PlayerState(board, objective, teams, lastTexts, scores, shown, evoLineIndex, levelLineIndex, progressbarLineIndex));
        player.setScoreboard(board);
    }

    /** Removes the custom scoreboard and restores the main one. */
    public void teardown(Player player) {
        PlayerState state = states.remove(player.getUniqueId());
        if (state == null) return;

        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /**
     * Resolves PlaceholderAPI placeholders and color codes, then pushes any changed
     * lines to the player's scoreboard. Skips the evo/atom toggle line, which is
     * managed entirely by the animation task. Unchanged lines produce no packets.
     */
    public void update(Player player) {
        if (!player.isOnline()) return;

        PlayerState state = states.get(player.getUniqueId());
        if (state == null) return;

        List<String> configLines = config().getStringList(ConfigPaths.Default.Scoreboard.LINES);
        int lineCount = Math.min(configLines.size(), state.teams.size());

        boolean holdingPickaxe  = player.getInventory().getItemInMainHand()
                .getType().name().endsWith("_PICKAXE");
        boolean pickaxeLoaded   = isPickaxeLoaded(player);
        boolean enchantActive   = EnchantSelectionManager.getInstance().hasActiveSelection(player);

        for (int i = 0; i < lineCount; i++) {
            // Lines managed by the animation task
            if (i == state.evoLineIndex) continue;
            if (state.levelUpAnimating && i == state.levelLineIndex) continue;
            // During enchant selection, level and progressbar lines are driven by tickAnimation
            if (enchantActive && i == state.levelLineIndex)       continue;
            if (enchantActive && i == state.progressbarLineIndex) continue;

            String  raw           = configLines.get(i);
            boolean isPickaxeLine = raw.contains("%evolv3_pickaxe_");

            // While pickaxe data is loading, hide other pickaxe lines entirely.
            // The evo line (managed by tickAnimation) shows the loading indicator instead.
            // Skip this during enchant animation — the data is already loaded.
            if (!pickaxeLoaded && isPickaxeLine && !enchantActive) {
                if (state.lineShown[i]) {
                    state.board.resetScores(ENTRIES[i]);
                    state.lineShown[i]  = false;
                    state.lastTexts.set(i, null);
                }
                continue;
            }

            // During enchant the pickaxe is removed from hand; still show pickaxe lines.
            boolean shouldShow = holdingPickaxe || !isPickaxeLine || enchantActive;

            if (!shouldShow) {
                if (state.lineShown[i]) {
                    state.board.resetScores(ENTRIES[i]);
                    state.lineShown[i]  = false;
                    state.lastTexts.set(i, null);
                }
                continue;
            }

            if (!state.lineShown[i]) {
                state.objective.getScore(ENTRIES[i]).setScore(state.lineScores[i]);
                state.lineShown[i] = true;
            }

            String formatted = Utils.formatMessage(player, raw);
            if (formatted.equals(state.lastTexts.get(i))) continue;

            state.lastTexts.set(i, formatted);
            state.teams.get(i).prefix(legacy(formatted));
        }
    }

    /**
     * Triggers a 3-second level-up flash animation on the level line for the given player.
     * Called by PickaxeManager immediately after a level-up is processed.
     */
    public void triggerLevelUpAnimation(Player player, int newLevel) {
        PlayerState state = states.get(player.getUniqueId());
        if (state == null || state.levelLineIndex < 0) return;
        state.levelUpAnimating = true;
        state.levelUpTick      = 0;
        state.levelUpNewLevel  = newLevel;
    }

    // -------------------------------------------------------------------------
    // Animation task — runs every ANIM_INTERVAL_TICKS, owns the evo/atom line
    // -------------------------------------------------------------------------

    private void tickAnimation(Player player) {
        if (!player.isOnline()) return;

        PlayerState state = states.get(player.getUniqueId());
        if (state == null) return;

        // Enchant selection animation takes over the pickaxe scoreboard section
        BaseEnchantAnimation enchantAnim =
                EnchantSelectionManager.getInstance().getActiveAnimation(player);
        if (enchantAnim != null) {
            tickEnchantDisplay(player, state, enchantAnim);
            return;
        }

        // Enchant animation just ended — reset state and force a re-render
        if (state.enchantWasActive) {
            state.enchantWasActive   = false;
            state.enchantArrowTick   = 0;
            state.enchantRevealTick  = 0;
            state.enchantRevealName  = "";
            state.enchantRevealColor = "";
            state.enchantStrip       = null;
            if (state.evoLineIndex         >= 0) state.lastTexts.set(state.evoLineIndex, null);
            if (state.levelLineIndex       >= 0) state.lastTexts.set(state.levelLineIndex, null);
            if (state.progressbarLineIndex >= 0) state.lastTexts.set(state.progressbarLineIndex, null);
            state.evoAtomPhase = EvoAtomPhase.EVO_DISPLAY;
            state.displayTick  = 0;
        }

        if (state.evoLineIndex < 0) return;

        int i = state.evoLineIndex;
        FileConfiguration cfg = config();
        List<String> configLines = cfg.getStringList(ConfigPaths.Default.Scoreboard.LINES);
        if (i >= configLines.size()) return;

        String evoRaw   = configLines.get(i);
        boolean holding = player.getInventory().getItemInMainHand()
                .getType().name().endsWith("_PICKAXE");

        // Hide line and reset state when not holding a pickaxe
        if (!holding) {
            if (state.lineShown[i]) {
                state.board.resetScores(ENTRIES[i]);
                state.lineShown[i] = false;
                state.lastTexts.set(i, null);
            }
            state.evoAtomPhase      = EvoAtomPhase.EVO_DISPLAY;
            state.displayTick       = 0;
            state.animCharIndex     = 0;
            state.animText          = "";
            state.animFormattedText = "";
            state.coloredPrefix     = "";
            return;
        }

        // Always advance the loading tick (drives the dots animation)
        state.loadingTick++;

        // Restore line if it was hidden
        if (!state.lineShown[i]) {
            state.objective.getScore(ENTRIES[i]).setScore(state.lineScores[i]);
            state.lineShown[i] = true;
        }

        // While pickaxe data is loading, show cycling dots on the evo line and hold animation state
        if (!isPickaxeLoaded(player)) {
            String loading = loadingText(state.loadingTick);
            if (!loading.equals(state.lastTexts.get(i))) {
                state.lastTexts.set(i, loading);
                state.teams.get(i).prefix(legacy(loading));
            }
            return;
        }

        // Display duration: toggleSeconds converted to animation ticks
        int toggleSeconds = cfg.getInt(ConfigPaths.Default.Scoreboard.EVO_ATOM_TOGGLE_SECONDS, 5);
        int displayTicks  = (int) ((toggleSeconds * 20L) / ANIM_INTERVAL_TICKS);

        String atomsRaw = cfg.getString(ConfigPaths.Default.Scoreboard.EVO_ATOM_TOGGLE_TEXT,
                "&#FFEE00⚛ &#FFEE00&l%evolv3_pickaxe_evolution_atoms%");

        // Split the formatted evo line into three static parts + animated inner:
        //   coloredPrefix = everything up to and including '('  e.g. "§7 Pickaxe §8("
        //   inner         = text between '(' and ')'            e.g. "§b§lEvo 1§8"
        //   STATIC_CLOSE  = always "§8)" — parenthesis stays visible throughout
        String formattedEvo  = Utils.formatMessage(player, evoRaw);
        int    bracketOpen   = formattedEvo.indexOf('(');
        int    bracketClose  = formattedEvo.lastIndexOf(')');
        String coloredPrefix = bracketOpen >= 0 ? formattedEvo.substring(0, bracketOpen + 1) : "";
        // The closing paren color is always §8 (matches &8) in the config line
        final  String CLOSE  = "§8)";

        switch (state.evoAtomPhase) {

            case EVO_DISPLAY -> {
                if (!formattedEvo.equals(state.lastTexts.get(i))) {
                    state.lastTexts.set(i, formattedEvo);
                    state.teams.get(i).prefix(legacy(formattedEvo));
                }
                if (++state.displayTick >= displayTicks) {
                    // Capture the inner formatted + plain text (between the parens) for erase
                    String inner = (bracketOpen >= 0 && bracketClose > bracketOpen)
                            ? formattedEvo.substring(bracketOpen + 1, bracketClose)
                            : formattedEvo;
                    state.coloredPrefix     = coloredPrefix;
                    state.animFormattedText = inner;
                    state.animText          = stripColors(inner);
                    state.animCharIndex     = state.animText.length();
                    state.evoAtomPhase      = EvoAtomPhase.EVO_ERASE;
                    state.displayTick       = 0;
                }
            }

            case EVO_ERASE -> {
                state.animCharIndex = Math.max(0, state.animCharIndex - 1);
                int rem = state.animCharIndex;
                if (rem > 0) {
                    pushLine(state, i, state.coloredPrefix
                            + truncateFormatted(state.animFormattedText, rem) + "§8▌" + CLOSE);
                } else {
                    // Erase complete — prepare atoms reveal
                    pushLine(state, i, state.coloredPrefix + CLOSE);
                    String formattedAtoms       = Utils.formatMessage(player, atomsRaw);
                    state.animFormattedText     = formattedAtoms;
                    state.animText              = stripColors(formattedAtoms);
                    state.animCharIndex         = 0;
                    state.evoAtomPhase          = EvoAtomPhase.ATOMS_REVEAL;
                }
            }

            case ATOMS_REVEAL -> {
                state.animCharIndex = Math.min(state.animText.length(), state.animCharIndex + 1);
                int shown = state.animCharIndex;
                boolean done = shown >= state.animText.length();
                if (done) {
                    pushLine(state, i, state.coloredPrefix + state.animFormattedText + CLOSE);
                    state.evoAtomPhase = EvoAtomPhase.ATOMS_DISPLAY;
                    state.displayTick  = 0;
                } else {
                    pushLine(state, i, state.coloredPrefix
                            + truncateFormatted(state.animFormattedText, shown) + "§8▌" + CLOSE);
                }
            }

            case ATOMS_DISPLAY -> {
                String formattedAtoms = state.coloredPrefix + Utils.formatMessage(player, atomsRaw) + CLOSE;
                if (!formattedAtoms.equals(state.lastTexts.get(i))) {
                    state.lastTexts.set(i, formattedAtoms);
                    state.teams.get(i).prefix(legacy(formattedAtoms));
                }
                if (++state.displayTick >= displayTicks) {
                    String atomsFormatted       = Utils.formatMessage(player, atomsRaw);
                    state.animFormattedText     = atomsFormatted;
                    state.animText              = stripColors(atomsFormatted);
                    state.animCharIndex         = state.animText.length();
                    state.evoAtomPhase          = EvoAtomPhase.ATOMS_ERASE;
                    state.displayTick           = 0;
                }
            }

            case ATOMS_ERASE -> {
                state.animCharIndex = Math.max(0, state.animCharIndex - 1);
                int rem = state.animCharIndex;
                if (rem > 0) {
                    pushLine(state, i, state.coloredPrefix
                            + truncateFormatted(state.animFormattedText, rem) + "§8▌" + CLOSE);
                } else {
                    pushLine(state, i, state.coloredPrefix + CLOSE);
                    // Capture fresh evo inner for the reveal
                    String inner = (bracketOpen >= 0 && bracketClose > bracketOpen)
                            ? formattedEvo.substring(bracketOpen + 1, bracketClose)
                            : formattedEvo;
                    state.animFormattedText = inner;
                    state.animText          = stripColors(inner);
                    state.animCharIndex     = 0;
                    state.evoAtomPhase      = EvoAtomPhase.EVO_REVEAL;
                }
            }

            case EVO_REVEAL -> {
                state.animCharIndex = Math.min(state.animText.length(), state.animCharIndex + 1);
                int shown = state.animCharIndex;
                boolean done = shown >= state.animText.length();
                if (done) {
                    pushLine(state, i, formattedEvo);
                    state.evoAtomPhase = EvoAtomPhase.EVO_DISPLAY;
                    state.displayTick  = 0;
                } else {
                    pushLine(state, i, state.coloredPrefix
                            + truncateFormatted(state.animFormattedText, shown) + "§8▌" + CLOSE);
                }
            }
        }

        // ── Level-up flash animation ─────────────────────────────────────────
        if (state.levelUpAnimating && state.levelLineIndex >= 0) {
            int li  = state.levelLineIndex;
            int llt = state.levelUpTick++;

            // 30 anim-ticks = 3 seconds of flash, then hand back to normal update
            if (llt >= 30) {
                state.levelUpAnimating = false;
                state.lastTexts.set(li, null); // force normal re-render next update
            } else {
                // Alternate between two colours every 5 ticks for a pulse effect
                boolean bright = (llt / 5) % 2 == 0;
                String  star   = bright ? "§b§l\uD83E\uDC79" : "§3§l\uD83E\uDC79";
                String  lvl    = bright ? "§f§l" : "§7§l";
                String  text   = " §8| " + star + " " + lvl + "Level " + state.levelUpNewLevel + " ";
                pushLine(state, li, text);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Pushes a line text to the team prefix, bypassing the diff cache (for animation). */
    private void pushLine(PlayerState state, int i, String text) {
        state.lastTexts.set(i, text);
        state.teams.get(i).prefix(legacy(text));
    }

    /**
     * Returns true when the pickaxe profile is present in local cache.
     * Used to gate pickaxe placeholder rendering so values don't flash as "0" on join.
     */
    private static boolean isPickaxeLoaded(Player player) {
        PickaxeDataCenter pkc = DataCenterRegistry.getInstance().getPickaxeDataCenter();
        return pkc != null && pkc.getLocal(player) != null;
    }

    /**
     * Returns a cycling "Loading." / "Loading.." / "Loading..." string based on the given tick.
     * The dot count advances once per second (every 10 animation ticks at 2-tick interval).
     */
    private static String loadingText(int tick) {
        int dots = (tick / 10) % 3 + 1;
        return " §8| §7Loading" + ".".repeat(dots);
    }

    /** Strips all §-coded color sequences from a formatted string. */
    private static String stripColors(String text) {
        if (text == null) return "";
        return text.replaceAll("§.", "");
    }

    /**
     * Returns the formatted string truncated to {@code visibleChars} visible characters,
     * preserving all §-color codes up to that point so original colors are maintained.
     */
    private static String truncateFormatted(String formatted, int visibleChars) {
        StringBuilder result = new StringBuilder();
        int visible = 0;
        int fi = 0;
        while (fi < formatted.length() && visible < visibleChars) {
            char c = formatted.charAt(fi);
            if (c == '§' && fi + 1 < formatted.length()) {
                // Color/format code — include but don't count as a visible character
                result.append(c).append(formatted.charAt(fi + 1));
                fi += 2;
            } else {
                result.append(c);
                visible++;
                fi++;
            }
        }
        return result.toString();
    }

    // -------------------------------------------------------------------------
    // Enchant selection scoreboard display
    // -------------------------------------------------------------------------

    /**
     * Drives the evo, level, and progressbar lines while an enchant selection
     * animation is running. Three visual phases:
     * <ol>
     *   <li><b>Rolling</b>  – slot machine scrolling; sweeping arrow animation</li>
     *   <li><b>Selecting</b>– player can look and click; highlighted arrow/square</li>
     *   <li><b>Selected</b> – typewriter reveal of the chosen enchant name</li>
     * </ol>
     */
    // Virtual strip constants — mirrors the 3D animation's 30-block concrete strip.
    // The visible window scrolls from offset 0 → STRIP_SCROLL_MAX, placing the three
    // result rarities at bar positions 2, 5, 8 when fully stopped.
    private static final int STRIP_SIZE       = 50;
    private static final int STRIP_SCROLL_MAX = 28;          // final window offset
    // Results land at bar positions 2, 4, 6 (9-icon bar; 2 fillers either side, 1 between)
    private static final int STRIP_RESULT_0   = 30;          // bar pos 2 at offset 28
    private static final int STRIP_RESULT_1   = 32;          // bar pos 4 at offset 28
    private static final int STRIP_RESULT_2   = 34;          // bar pos 6 at offset 28

    private void tickEnchantDisplay(Player player, PlayerState state, BaseEnchantAnimation anim) {
        state.enchantWasActive = true;
        state.enchantArrowTick++;

        ensureLineShown(state, state.evoLineIndex);
        ensureLineShown(state, state.levelLineIndex);
        ensureLineShown(state, state.progressbarLineIndex);

        List<EnchantOption> opts = anim.getEnchantOptions();
        if (opts == null || opts.size() < 3) return;

        // Initialise the virtual strip once per animation session
        if (state.enchantStrip == null) {
            state.enchantStrip = buildVirtualStrip(opts, anim);
        }

        if (anim.isSelectionMade()) {
            // Initialise the reveal text on the first tick after selection
            if (state.enchantRevealName.isEmpty()) {
                int idx = anim.getSelectedIndex();
                if (idx >= 0 && idx < opts.size()) {
                    EnchantOption chosen = opts.get(idx);
                    state.enchantRevealName  = FontUtils.toSmallCaps(chosen.name());
                    state.enchantRevealColor = rarityHex(chosen);
                }
            }
            state.enchantRevealTick++;
            renderSelectedPhase(state, opts, anim.getSelectedIndex());

        } else if (anim.isSelectionPhase()) {
            // Strip stopped — results fixed at positions 2, 5, 8
            renderSelectingPhase(state, opts, anim.getHighlightedIndex());

        } else {
            // Compute eased scroll offset matching the actual 3D animation curve
            double linearProgress = anim.getLinearScrollProgress();
            double easedProgress  = easeInOutQuart(linearProgress);
            int    offset         = (int) Math.round(easedProgress * STRIP_SCROLL_MAX);
            renderRollingPhase(state, opts, offset);
        }
    }

    /**
     * Builds the 50-entry virtual rarity strip used by the scoreboard bar.
     *
     * <p>When the animation exposes its own strip via {@link BaseEnchantAnimation#getStripRarities()},
     * the scoreboard strip is derived directly from it so the colours match exactly.
     * The 3D animation uses 30 blocks with result indices {20,22,24}; the scoreboard uses
     * 50 entries with result positions {30,32,34}.  An offset of 10 aligns them:
     * {@code scoreboard[i] = animStrip[i - 10]}.  Out-of-bounds entries (i &lt; 10 or i &gt; 39)
     * are filled with random rarities to keep the early roll visually smooth.</p>
     *
     * <p>If no strip is available, all positions are independently random.</p>
     */
    private static RarityConfig[] buildVirtualStrip(List<EnchantOption> opts, BaseEnchantAnimation anim) {
        RarityConfig[] strip = new RarityConfig[STRIP_SIZE];
        List<RarityConfig> all = EnchantAnimationConfig.getRarities();
        RarityConfig fallback  = all.isEmpty() ? null : all.get(0);

        RarityConfig[] animStrip = anim.getStripRarities();
        if (animStrip != null) {
            // Align: scoreboard result positions 30,32,34 ← anim result positions 20,22,24
            final int ANIM_OFFSET = 10; // scoreboard_index = anim_index + ANIM_OFFSET
            for (int i = 0; i < STRIP_SIZE; i++) {
                int animIdx = i - ANIM_OFFSET;
                if (animIdx >= 0 && animIdx < animStrip.length && animStrip[animIdx] != null) {
                    strip[i] = animStrip[animIdx];
                } else {
                    // Portions outside the anim strip: random fill for smooth rolling
                    RarityConfig rc = EnchantAnimationConfig.rollRarity();
                    strip[i] = (rc != null) ? rc : fallback;
                }
            }
        } else {
            for (int i = 0; i < STRIP_SIZE; i++) {
                RarityConfig rc = EnchantAnimationConfig.rollRarity();
                strip[i] = (rc != null) ? rc : fallback;
            }
        }

        // Guarantee result positions always carry the correct option rarity
        strip[STRIP_RESULT_0] = opts.get(0).rarity();
        strip[STRIP_RESULT_1] = opts.get(1).rarity();
        strip[STRIP_RESULT_2] = opts.get(2).rarity();
        return strip;
    }

    private void renderRollingPhase(PlayerState state, List<EnchantOption> opts, int offset) {
        int dots = (state.enchantArrowTick / 5) % 3 + 1;
        pushLineSafe(state, state.evoLineIndex,
                colored(FontUtils.toSmallCaps(" &#FFD700⚙ Rolling" + ".".repeat(dots))));
        pushLineSafe(state, state.levelLineIndex,
                colored(buildArrowLine(opts, -1, false)));
        pushLineSafe(state, state.progressbarLineIndex,
                colored(buildRarityBar(state.enchantStrip, offset)));
    }

    private void renderSelectingPhase(PlayerState state, List<EnchantOption> opts, int highlighted) {
        pushLineSafe(state, state.evoLineIndex,
                colored(FontUtils.toSmallCaps(" &#00FF88✦ Selecting...")));
        pushLineSafe(state, state.levelLineIndex,
                colored(buildArrowLine(opts, highlighted, true)));
        pushLineSafe(state, state.progressbarLineIndex,
                colored(buildStoppedBar(state.enchantStrip, opts, highlighted, false)));
    }

    private void renderSelectedPhase(PlayerState state, List<EnchantOption> opts, int selectedIndex) {
        // Typewriter reveal of enchant name inside the evo line parentheses
        String name   = state.enchantRevealName;
        int    chars  = Math.min(state.enchantRevealTick, name.length());
        String reveal = name.substring(0, chars);
        String cursor = chars >= name.length() ? "" : "&#888888▌";
        pushLineSafe(state, state.evoLineIndex,
                colored(" " + state.enchantRevealColor + reveal + cursor));

        // Level line: shows the rarity tier name in small caps
        if (selectedIndex >= 0 && selectedIndex < opts.size()) {
            String rarityName = FontUtils.toSmallCaps(opts.get(selectedIndex).rarity().displayName());
            pushLineSafe(state, state.levelLineIndex,
                    colored(" &8| " + state.enchantRevealColor + "&l" + rarityName));
        }

        // Progress bar: winner stays bright, losers become dimmed empty blocks
        pushLineSafe(state, state.progressbarLineIndex,
                colored(buildStoppedBar(state.enchantStrip, opts, selectedIndex, true)));
    }

    /**
     * Builds the indicator line above the rarity bar using ▄ (LOWER HALF BLOCK, U+2584).
     *
     * <p>▄ is chosen over ▼ because it occupies the <em>same full character width</em> as
     * █ (both are block-elements that fill the entire 8-pixel advance width).  ▼ is a
     * triangle that carries less visual mass and appears smaller even at the same advance
     * width.  ▄ fills the bottom half of its cell, so when rendered directly above the
     * bar's █ icons it creates a solid downward indicator with matching visual weight.</p>
     *
     * <p>Both lines share the same prefix {@code " &8| "} and use {@code █} spacers at
     * non-result positions so every character slot has identical pixel widths.</p>
     *
     * <ul>
     *   <li>Rolling: dim grey ▄ at all three positions — bar is the focal point.</li>
     *   <li>Selecting: hovered = rarity colour ▄; others = very dim ▄.</li>
     *   <li>Selected: winner = rarity colour ▄; losers = very dim ▄.</li>
     * </ul>
     */
    private static String buildArrowLine(List<EnchantOption> opts, int highlighted, boolean isSelecting) {
        final String DARK = "&#0D0D0D"; // near-black █ spacer — visually invisible
        String[] ac = new String[3];
        for (int i = 0; i < 3; i++) {
            if (!isSelecting) {
                ac[i] = "&#666666"; // rolling: muted grey indicator
            } else {
                ac[i] = (i == highlighted) ? rarityHex(opts.get(i)) : "&#2A2A2A";
            }
        }
        // Same prefix and █ spacers as bar — guaranteed identical character widths.
        return " &8| "
                + DARK + "██"       // positions 0-1 (filler)
                + ac[0] + "▄"       // position 2   (opt0 indicator)
                + DARK + "█"        // position 3   (filler)
                + ac[1] + "▄"       // position 4   (opt1 indicator)
                + DARK + "█"        // position 5   (filler)
                + ac[2] + "▄"       // position 6   (opt2 indicator)
                + DARK + "██";      // positions 7-8 (filler — matches bar width)
    }

    /**
     * Maps each Minecraft concrete material to its actual block texture colour so
     * the scoreboard bar squares exactly match what the player sees in the 3D animation.
     * Colours sampled from the standard Minecraft 1.21 concrete block textures.
     */
    private static final Map<Material, TextColor> CONCRETE_COLORS = new HashMap<>();
    static {
        CONCRETE_COLORS.put(Material.WHITE_CONCRETE,      TextColor.fromHexString("#CFD5D6"));
        CONCRETE_COLORS.put(Material.ORANGE_CONCRETE,     TextColor.fromHexString("#E06101"));
        CONCRETE_COLORS.put(Material.MAGENTA_CONCRETE,    TextColor.fromHexString("#BD44B3"));
        CONCRETE_COLORS.put(Material.LIGHT_BLUE_CONCRETE, TextColor.fromHexString("#2489C7"));
        CONCRETE_COLORS.put(Material.YELLOW_CONCRETE,     TextColor.fromHexString("#F1AF15"));
        CONCRETE_COLORS.put(Material.LIME_CONCRETE,       TextColor.fromHexString("#5EA919"));
        CONCRETE_COLORS.put(Material.PINK_CONCRETE,       TextColor.fromHexString("#D5658F"));
        CONCRETE_COLORS.put(Material.GRAY_CONCRETE,       TextColor.fromHexString("#383838"));
        CONCRETE_COLORS.put(Material.LIGHT_GRAY_CONCRETE, TextColor.fromHexString("#7D7D73"));
        CONCRETE_COLORS.put(Material.CYAN_CONCRETE,       TextColor.fromHexString("#157788"));
        CONCRETE_COLORS.put(Material.PURPLE_CONCRETE,     TextColor.fromHexString("#641D92"));
        CONCRETE_COLORS.put(Material.BLUE_CONCRETE,       TextColor.fromHexString("#2D2F97"));
        CONCRETE_COLORS.put(Material.BROWN_CONCRETE,      TextColor.fromHexString("#603B1F"));
        CONCRETE_COLORS.put(Material.GREEN_CONCRETE,      TextColor.fromHexString("#495B24"));
        CONCRETE_COLORS.put(Material.RED_CONCRETE,        TextColor.fromHexString("#8E2020"));
        CONCRETE_COLORS.put(Material.BLACK_CONCRETE,      TextColor.fromHexString("#080A0F"));
    }

    /** Returns the actual concrete block texture colour for a rarity, falling back to textColor. */
    private static TextColor concreteColor(RarityConfig rarity) {
        TextColor tc = CONCRETE_COLORS.get(rarity.concreteMaterial());
        return tc != null ? tc : rarity.textColor();
    }

    /**
     * Renders the 10-icon bar during rolling by slicing {@code strip[offset..offset+9]}.
     * As the offset increases from 0 → {@value STRIP_SCROLL_MAX} using the same
     * easeInOutQuart curve as the 3D animation, the bar decelerates and stops with
     * the result rarities naturally landing at bar positions 2, 5, 8.
     */
    private static String buildRarityBar(RarityConfig[] strip, int offset) {
        StringBuilder sb = new StringBuilder(" &8| ");
        for (int p = 0; p < 9; p++) {
            int idx = Math.min(offset + p, strip.length - 1);
            TextColor tc = concreteColor(strip[idx]);
            sb.append(String.format("&#%02X%02X%02X", tc.red(), tc.green(), tc.blue())).append("█");
        }
        return sb.toString();
    }

    /**
     * Renders the bar in the stopped state (selecting or selected).
     * Result rarities appear at positions 2, 5, 8 (where the strip stopped).
     * Filler positions show the actual strip colour dimmed; result positions
     * are either highlighted (selecting) or bright/empty (selected).
     */
    private static String buildStoppedBar(RarityConfig[] strip, List<EnchantOption> opts,
                                           int highlighted, boolean isSelected) {
        StringBuilder sb = new StringBuilder(" &8| ");
        // 9-icon bar: [filler][filler][opt0][filler][opt1][filler][opt2][filler][filler]
        // 2 fillers either side, 1 filler between results — symmetric
        int[] resultAt = {-1, -1, 0, -1, 1, -1, 2, -1, -1};
        for (int p = 0; p < 9; p++) {
            int optIdx = resultAt[p];
            if (optIdx < 0) {
                // Filler: use the concrete block colour but dimmed
                TextColor tc = concreteColor(strip[STRIP_SCROLL_MAX + p]);
                int r = tc.red()   / 5;
                int g = tc.green() / 5;
                int bv = tc.blue()  / 5;
                sb.append(String.format("&#%02X%02X%02X", Math.max(r, 0x1A), Math.max(g, 0x1A), Math.max(bv, 0x1A))).append("█");
            } else if (isSelected) {
                sb.append(optIdx == highlighted
                        ? concreteHex(opts.get(optIdx)) + "█"
                        : "&#333333░");
            } else {
                // Selecting: hovered bright, others at 25% concrete colour
                if (optIdx == highlighted) {
                    sb.append(concreteHex(opts.get(optIdx))).append("█");
                } else {
                    TextColor tc = concreteColor(opts.get(optIdx).rarity());
                    sb.append(String.format("&#%02X%02X%02X",
                            Math.max(tc.red() / 4, 0x10),
                            Math.max(tc.green() / 4, 0x10),
                            Math.max(tc.blue() / 4, 0x10))).append("█");
                }
            }
        }
        return sb.toString();
    }

    /** Same easing curve used by EnchantAnimation for the concrete strip scroll. */
    private static double easeInOutQuart(double x) {
        return x < 0.5 ? 8 * x * x * x * x : 1 - Math.pow(-2 * x + 2, 4) / 2;
    }

    /** Returns the {@code &#RRGGBB} hex string for a rarity's Adventure TextColor. */
    private static String rarityHex(EnchantOption opt) {
        TextColor tc = opt.rarity().textColor();
        return String.format("&#%02X%02X%02X", tc.red(), tc.green(), tc.blue());
    }

    /** Returns the {@code &#RRGGBB} hex string matching the rarity's concrete block colour. */
    private static String concreteHex(EnchantOption opt) {
        TextColor tc = concreteColor(opt.rarity());
        return String.format("&#%02X%02X%02X", tc.red(), tc.green(), tc.blue());
    }

    /** Converts {@code &#RRGGBB} colour codes to §-format via MatrixColorAPI. */
    private static String colored(String text) {
        return MatrixColorAPI.process(text);
    }

    /** Pushes a line only if the index is valid. */
    private void pushLineSafe(PlayerState state, int lineIndex, String text) {
        if (lineIndex < 0 || lineIndex >= state.teams.size()) return;
        pushLine(state, lineIndex, text);
    }

    /** Ensures a scoreboard line has an active score entry (is visible). */
    private void ensureLineShown(PlayerState state, int lineIndex) {
        if (lineIndex < 0 || lineIndex >= state.lineShown.length) return;
        if (!state.lineShown[lineIndex]) {
            state.objective.getScore(ENTRIES[lineIndex]).setScore(state.lineScores[lineIndex]);
            state.lineShown[lineIndex] = true;
        }
    }

    private void startTasks() {
        int intervalTicks = config().getInt(ConfigPaths.Default.Scoreboard.UPDATE_INTERVAL_TICKS, 20);
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) update(player);
        }, intervalTicks, intervalTicks);

        animTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) tickAnimation(player);
        }, ANIM_INTERVAL_TICKS, ANIM_INTERVAL_TICKS);
    }

    private void stopTasks() {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
        if (animTask   != null) { animTask.cancel();   animTask   = null; }
    }

    private boolean isEnabled() {
        return config().getBoolean(ConfigPaths.Default.Scoreboard.ENABLED, true);
    }

    private FileConfiguration config() {
        return ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
    }

    /** Deserializes a legacy-formatted (§-code) string into an Adventure Component. */
    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    // -------------------------------------------------------------------------
    // Per-player state
    // -------------------------------------------------------------------------

    private static final class PlayerState
    {
        final Scoreboard   board;
        final Objective    objective;
        final List<Team>   teams;
        final List<String> lastTexts;
        /** Original score value for each line (used to restore after removal). */
        final int[]        lineScores;
        /** Whether each line currently has an active score entry on the sidebar. */
        final boolean[]    lineShown;
        /** Index of the evo/atom toggle line, or -1 if not configured. */
        final int          evoLineIndex;
        /** Index of the level line, or -1 if not configured. */
        final int          levelLineIndex;
        /** Index of the progress-bar line, or -1 if not configured. */
        final int          progressbarLineIndex;

        // Evo/atom toggle animation state
        EvoAtomPhase evoAtomPhase      = EvoAtomPhase.EVO_DISPLAY;
        int          displayTick       = 0;
        int          animCharIndex     = 0;
        String       animText          = "";
        String       animFormattedText = "";
        String       coloredPrefix     = "";
        int          loadingTick       = 0;

        // Level-up flash animation state
        boolean levelUpAnimating = false;
        int     levelUpTick      = 0;
        int     levelUpNewLevel  = 0;

        // Enchant selection display state
        boolean        enchantWasActive  = false;
        int            enchantArrowTick  = 0;
        int            enchantRevealTick = 0;
        String         enchantRevealName  = "";
        String         enchantRevealColor = "";
        /** Virtual rarity strip (50 entries) initialised on first enchant tick. */
        RarityConfig[] enchantStrip       = null;

        PlayerState(Scoreboard board, Objective objective,
                    List<Team> teams, List<String> lastTexts,
                    int[] lineScores, boolean[] lineShown,
                    int evoLineIndex, int levelLineIndex, int progressbarLineIndex) {
            this.board                = board;
            this.objective            = objective;
            this.teams                = teams;
            this.lastTexts            = lastTexts;
            this.lineScores           = lineScores;
            this.lineShown            = lineShown;
            this.evoLineIndex         = evoLineIndex;
            this.levelLineIndex       = levelLineIndex;
            this.progressbarLineIndex = progressbarLineIndex;
        }
    }
}
