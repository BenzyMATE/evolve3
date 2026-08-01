package org.benski.evolv3.core.managers.player;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.matrixcreations.libraries.MatrixColorAPI;
import org.benski.evolv3.core.abstracts.BaseEnchantAnimation;
import org.benski.evolv3.core.constants.ConfigNames;
import org.benski.evolv3.core.constants.ConfigPaths;
import org.benski.evolv3.core.helpers.FontUtils;
import org.benski.evolv3.core.helpers.Utils;
import org.benski.evolv3.core.managers.enchant.EnchantAnimationConfig;
import org.benski.evolv3.core.managers.enchant.EnchantSelectionManager;
import org.benski.evolv3.core.managers.pickaxe.OmnitoolManager;
import org.benski.evolv3.core.types.EnchantOption;
import org.benski.evolv3.core.types.RarityConfig;
import org.benski.evolv3.datacenters.PlayerDataCenter;
import org.benski.evolv3.models.pickaxe.Pickaxe;
import org.benski.evolv3.models.player.PlayerProfile;
import org.benski.evolv3.registry.ConfigRegistry;
import org.benski.evolv3.registry.DataCenterRegistry;
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

/**
 * Manages per-player sidebar scoreboards.
 *
 * <p>Each player receives their own {@link Scoreboard} instance with a fixed set of {@link Team}
 * objects — one per line. Lines are updated by changing the team prefix (not by removing and
 * re-adding score entries), which eliminates the flicker that occurs with naive add/remove
 * approaches.
 *
 * <p>Diff-based rendering: the last rendered text for each line is cached. {@link
 * Team#prefix(Component)} is only called when the text actually changes, keeping packet count
 * minimal even at high update rates.
 *
 * <p>The line containing {@code %evolv3_pickaxe_evolution%} receives special treatment: it
 * alternates between the Evo display and an atoms count display using a typewriter-style reveal
 * animation between the two.
 *
 * <p>Supports up to 15 sidebar lines (Minecraft's hard limit). Configure lines, title, and update
 * rate in {@code default.yml}.
 */
public final class EvolveScoreboardManager {
  private static final EvolveScoreboardManager INSTANCE = new EvolveScoreboardManager();

  /** Maximum lines the Minecraft sidebar can display. */
  private static final int MAX_LINES = 15;

  /** Ticks between animation task runs — drives typewriter speed. */
  private static final long ANIM_INTERVAL_TICKS = 2L;

  /**
   * Unique invisible entry strings used as score entries. Control codes render as nothing, ensuring
   * lines appear blank rather than showing the raw entry name. Each must be distinct within the
   * scoreboard.
   */
  private static final String[] ENTRIES;

  static {
    ENTRIES = new String[MAX_LINES];
    String codes = "0123456789abcde";
    for (int i = 0; i < MAX_LINES; i++) {
      ENTRIES[i] = "§" + codes.charAt(i);
    }
  }

  private enum EvoAtomPhase {
    EVO_DISPLAY,
    EVO_ERASE,
    ATOMS_REVEAL,
    ATOMS_DISPLAY,
    ATOMS_ERASE,
    EVO_REVEAL
  }

  private enum RankTogglePhase {
    RANK_DISPLAY,
    RANK_ERASE,
    RANKUP_REVEAL,
    RANKUP_DISPLAY,
    RANKUP_ERASE,
    RANK_REVEAL
  }

  private JavaPlugin plugin;
  private BukkitTask updateTask;
  private BukkitTask animTask;
  private final Map<UUID, PlayerState> states = new HashMap<>();

  /** Creates a new EvolveScoreboardManager instance. */
  private EvolveScoreboardManager() {}

  /** Returns the singleton instance of this manager. */
  public static EvolveScoreboardManager getInstance() {
    return INSTANCE;
  }

  /**
   * Starts the update and animation tasks. Call once during plugin enable after the server is fully
   * loaded.
   *
   * @param plugin the owning plugin
   */
  public void initialize(JavaPlugin plugin) {
    this.plugin = plugin;
    startTasks();
  }

  /** Stops all tasks, tears down every player's scoreboard, and clears internal state. */
  public void cleanup() {
    stopTasks();
    for (UUID uuid : new java.util.ArrayList<>(states.keySet())) {
      Player p = Bukkit.getPlayer(uuid);
      if (p != null) teardown(p);
    }
    states.clear();
  }

  /** Sets up and assigns a fresh scoreboard for the given player. */
  public void setup(Player player) {
    if (!isEnabled()) return;

    teardown(player);

    FileConfiguration cfg = config();
    List<String> lines = cfg.getStringList(ConfigPaths.Default.Scoreboard.LINES);
    int lineCount = Math.min(lines.size(), MAX_LINES);
    if (lineCount == 0) return;

    Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
    Objective objective = board.registerNewObjective("evolv3sb", Criteria.DUMMY, Component.empty());
    objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    objective.numberFormat(NumberFormat.blank());

    String titleRaw = cfg.getString(ConfigPaths.Default.Scoreboard.TITLE, "&3&lEvolv3");
    objective.displayName(legacy(Utils.formatMessage(player, titleRaw)));

    List<Team> teams = new ArrayList<>(lineCount);
    List<String> lastTexts = new ArrayList<>(lineCount);
    int[] scores = new int[lineCount];
    boolean[] shown = new boolean[lineCount];

    int evoLineIndex = -1;
    int levelLineIndex = -1;
    int progressbarLineIndex = -1;
    int rankLineIndex = -1;
    int prestigeLineIndex = -1;
    for (int i = 0; i < lineCount; i++) {
      String l = lines.get(i);
      if (l.contains("%evolv3_pickaxe_evolution%")) evoLineIndex = i;
      if (l.contains("%evolv3_pickaxe_level%") && !l.contains("%evolv3_pickaxe_evolution%"))
        levelLineIndex = i;
      if (l.contains("%evolv3_pickaxe_progressbar%")) progressbarLineIndex = i;
      if (l.contains("%evolv3_rank%")) rankLineIndex = i;
      if (l.contains("%evolv3_pickaxe_prestige_line%")) prestigeLineIndex = i;
    }

    for (int i = 0; i < lineCount; i++) {
      Team team = board.registerNewTeam("evolv3_" + i);
      team.addEntry(ENTRIES[i]);
      int score = lineCount - i;
      objective.getScore(ENTRIES[i]).setScore(score);
      scores[i] = score;
      shown[i] = true;
      teams.add(team);
      lastTexts.add(null);
    }

    states.put(
        player.getUniqueId(),
        new PlayerState(
            board,
            objective,
            teams,
            lastTexts,
            scores,
            shown,
            evoLineIndex,
            levelLineIndex,
            progressbarLineIndex,
            rankLineIndex,
            prestigeLineIndex));
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
   * Resolves PlaceholderAPI placeholders and color codes, then pushes any changed lines to the
   * player's scoreboard. Skips the evo/atom toggle line, which is managed entirely by the animation
   * task. Unchanged lines produce no packets.
   */
  public void update(Player player) {
    if (!player.isOnline()) return;

    PlayerState state = states.get(player.getUniqueId());
    if (state == null) return;

    String titleRaw = config().getString(ConfigPaths.Default.Scoreboard.TITLE, "&3&lEvolv3");
    String titleResolved = Utils.formatMessage(player, titleRaw);
    if (!titleResolved.equals(state.lastTitle)) {
      state.lastTitle = titleResolved;
      state.objective.displayName(legacy(titleResolved));
    }

    List<String> configLines = config().getStringList(ConfigPaths.Default.Scoreboard.LINES);
    int lineCount = Math.min(configLines.size(), state.teams.size());

    boolean holdingPickaxe =
        player.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE")
            || isHoldingOmnitool(player);
    boolean pickaxeLoaded = isPickaxeLoaded(player);
    boolean enchantActive = EnchantSelectionManager.getInstance().hasActiveSelection(player);

    for (int i = 0; i < lineCount; i++) {
      if (i == state.evoLineIndex) continue;
      if (i == state.rankLineIndex) continue;
      if (state.levelUpAnimating && i == state.levelLineIndex) continue;
      if (enchantActive && i == state.levelLineIndex) continue;
      if (enchantActive && i == state.progressbarLineIndex) continue;

      if (i == state.prestigeLineIndex) {
        handlePrestigeLine(player, state, i);
        continue;
      }

      String raw = configLines.get(i);
      boolean isPickaxeLine = raw.contains("%evolv3_pickaxe_");

      if (!pickaxeLoaded && isPickaxeLine && !enchantActive) {
        if (state.lineShown[i]) {
          state.board.resetScores(ENTRIES[i]);
          state.lineShown[i] = false;
          state.lastTexts.set(i, null);
        }
        continue;
      }

      boolean shouldShow = holdingPickaxe || !isPickaxeLine || enchantActive;

      if (!shouldShow) {
        if (state.lineShown[i]) {
          state.board.resetScores(ENTRIES[i]);
          state.lineShown[i] = false;
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
   * Triggers a 3-second level-up flash animation on the level line for the given player. Called by
   * PickaxeManager immediately after a level-up is processed.
   */
  public void triggerLevelUpAnimation(Player player, int newLevel) {
    PlayerState state = states.get(player.getUniqueId());
    if (state == null || state.levelLineIndex < 0) return;
    state.levelUpAnimating = true;
    state.levelUpTick = 0;
    state.levelUpNewLevel = newLevel;
  }

  /** Ticks animation. */
  private void tickAnimation(Player player) {
    if (!player.isOnline()) return;

    PlayerState state = states.get(player.getUniqueId());
    if (state == null) return;

    BaseEnchantAnimation enchantAnim =
        EnchantSelectionManager.getInstance().getActiveAnimation(player);
    if (enchantAnim != null) {
      tickEnchantDisplay(player, state, enchantAnim);
      return;
    }

    if (state.enchantWasActive) {
      state.enchantWasActive = false;
      state.enchantArrowTick = 0;
      state.enchantRevealTick = 0;
      state.enchantRevealName = "";
      state.enchantRevealColor = "";
      state.enchantStrip = null;
      if (state.evoLineIndex >= 0) state.lastTexts.set(state.evoLineIndex, null);
      if (state.levelLineIndex >= 0) state.lastTexts.set(state.levelLineIndex, null);
      if (state.progressbarLineIndex >= 0) state.lastTexts.set(state.progressbarLineIndex, null);
      state.evoAtomPhase = EvoAtomPhase.EVO_DISPLAY;
      state.displayTick = 0;
    }

    tickRankLine(player, state);

    if (state.evoLineIndex < 0) return;

    int i = state.evoLineIndex;
    FileConfiguration cfg = config();
    List<String> configLines = cfg.getStringList(ConfigPaths.Default.Scoreboard.LINES);
    if (i >= configLines.size()) return;

    String evoRaw = configLines.get(i);
    boolean holding =
        player.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE")
            || isHoldingOmnitool(player);

    if (!holding) {
      if (state.lineShown[i]) {
        state.board.resetScores(ENTRIES[i]);
        state.lineShown[i] = false;
        state.lastTexts.set(i, null);
      }
      state.evoAtomPhase = EvoAtomPhase.EVO_DISPLAY;
      state.displayTick = 0;
      state.animCharIndex = 0;
      state.animText = "";
      state.animFormattedText = "";
      state.coloredPrefix = "";
      return;
    }

    state.loadingTick++;

    if (!state.lineShown[i]) {
      state.objective.getScore(ENTRIES[i]).setScore(state.lineScores[i]);
      state.lineShown[i] = true;
    }

    if (!isPickaxeLoaded(player)) {
      String loading = loadingText(state.loadingTick);
      if (!loading.equals(state.lastTexts.get(i))) {
        state.lastTexts.set(i, loading);
        state.teams.get(i).prefix(legacy(loading));
      }
      return;
    }

    int toggleSeconds = cfg.getInt(ConfigPaths.Default.Scoreboard.EVO_ATOM_TOGGLE_SECONDS, 5);
    int displayTicks = (int) ((toggleSeconds * 20L) / ANIM_INTERVAL_TICKS);

    String atomsRaw =
        cfg.getString(
            ConfigPaths.Default.Scoreboard.EVO_ATOM_TOGGLE_TEXT,
            "&#FFEE00⚛ &#FFEE00&l%evolv3_pickaxe_evolution_atoms%");

    String formattedEvo = Utils.formatMessage(player, evoRaw);
    int bracketOpen = formattedEvo.indexOf('(');
    int bracketClose = formattedEvo.lastIndexOf(')');
    String coloredPrefix = bracketOpen >= 0 ? formattedEvo.substring(0, bracketOpen + 1) : "";
    final String CLOSE = "§8)";

    switch (state.evoAtomPhase) {
      case EVO_DISPLAY -> {
        if (!formattedEvo.equals(state.lastTexts.get(i))) {
          state.lastTexts.set(i, formattedEvo);
          state.teams.get(i).prefix(legacy(formattedEvo));
        }
        if (++state.displayTick >= displayTicks) {
          String inner =
              (bracketOpen >= 0 && bracketClose > bracketOpen)
                  ? formattedEvo.substring(bracketOpen + 1, bracketClose)
                  : formattedEvo;
          state.coloredPrefix = coloredPrefix;
          state.animFormattedText = inner;
          state.animText = stripColors(inner);
          state.animCharIndex = state.animText.length();
          state.evoAtomPhase = EvoAtomPhase.EVO_ERASE;
          state.displayTick = 0;
        }
      }

      case EVO_ERASE -> {
        state.animCharIndex = Math.max(0, state.animCharIndex - 1);
        int rem = state.animCharIndex;
        if (rem > 0) {
          pushLine(
              state,
              i,
              coloredPrefix + truncateFormatted(state.animFormattedText, rem) + "§8▌" + CLOSE);
        } else {
          pushLine(state, i, coloredPrefix + CLOSE);
          String formattedAtoms = Utils.formatMessage(player, atomsRaw);
          state.animFormattedText = formattedAtoms;
          state.animText = stripColors(formattedAtoms);
          state.animCharIndex = 0;
          state.evoAtomPhase = EvoAtomPhase.ATOMS_REVEAL;
        }
      }

      case ATOMS_REVEAL -> {
        state.animCharIndex = Math.min(state.animText.length(), state.animCharIndex + 1);
        int shown = state.animCharIndex;
        boolean done = shown >= state.animText.length();
        if (done) {
          pushLine(state, i, coloredPrefix + state.animFormattedText + CLOSE);
          state.evoAtomPhase = EvoAtomPhase.ATOMS_DISPLAY;
          state.displayTick = 0;
        } else {
          pushLine(
              state,
              i,
              coloredPrefix + truncateFormatted(state.animFormattedText, shown) + "§8▌" + CLOSE);
        }
      }

      case ATOMS_DISPLAY -> {
        String formattedAtoms = coloredPrefix + Utils.formatMessage(player, atomsRaw) + CLOSE;
        if (!formattedAtoms.equals(state.lastTexts.get(i))) {
          state.lastTexts.set(i, formattedAtoms);
          state.teams.get(i).prefix(legacy(formattedAtoms));
        }
        if (++state.displayTick >= displayTicks) {
          String atomsFormatted = Utils.formatMessage(player, atomsRaw);
          state.animFormattedText = atomsFormatted;
          state.animText = stripColors(atomsFormatted);
          state.animCharIndex = state.animText.length();
          state.evoAtomPhase = EvoAtomPhase.ATOMS_ERASE;
          state.displayTick = 0;
        }
      }

      case ATOMS_ERASE -> {
        state.animCharIndex = Math.max(0, state.animCharIndex - 1);
        int rem = state.animCharIndex;
        if (rem > 0) {
          pushLine(
              state,
              i,
              coloredPrefix + truncateFormatted(state.animFormattedText, rem) + "§8▌" + CLOSE);
        } else {
          pushLine(state, i, coloredPrefix + CLOSE);
          String inner =
              (bracketOpen >= 0 && bracketClose > bracketOpen)
                  ? formattedEvo.substring(bracketOpen + 1, bracketClose)
                  : formattedEvo;
          state.animFormattedText = inner;
          state.animText = stripColors(inner);
          state.animCharIndex = 0;
          state.evoAtomPhase = EvoAtomPhase.EVO_REVEAL;
        }
      }

      case EVO_REVEAL -> {
        state.animCharIndex = Math.min(state.animText.length(), state.animCharIndex + 1);
        int shown = state.animCharIndex;
        boolean done = shown >= state.animText.length();
        if (done) {
          pushLine(state, i, formattedEvo);
          state.evoAtomPhase = EvoAtomPhase.EVO_DISPLAY;
          state.displayTick = 0;
        } else {
          pushLine(
              state,
              i,
              coloredPrefix + truncateFormatted(state.animFormattedText, shown) + "§8▌" + CLOSE);
        }
      }
    }

    if (state.levelUpAnimating && state.levelLineIndex >= 0) {
      int li = state.levelLineIndex;
      int llt = state.levelUpTick++;

      if (llt >= 30) {
        state.levelUpAnimating = false;
        state.lastTexts.set(li, null);
      } else {
        boolean bright = (llt / 5) % 2 == 0;
        String star = bright ? "§b§l\uD83E\uDC79" : "§3§l\uD83E\uDC79";
        String lvl = bright ? "§f§l" : "§7§l";
        String label = config().getString(ConfigPaths.Default.Scoreboard.LEVEL_UP_LABEL, "Level");
        String text = " §8| " + star + " " + lvl + label + " " + state.levelUpNewLevel + " ";
        pushLine(state, li, text);
      }
    }
  }

  /**
   * Drives the rank line animation. When the player has enough coins to rankup, the line alternates
   * between "currentRank » nextRank" and "/rankup" using the same typewriter erase/reveal pattern
   * as the evo/atoms toggle. When the player cannot rankup (at max or insufficient coins), the line
   * shows the static rank display without animating.
   */
  private void tickRankLine(Player player, PlayerState state) {
    int i = state.rankLineIndex;
    if (i < 0) return;

    if (!state.lineShown[i]) {
      state.objective.getScore(ENTRIES[i]).setScore(state.lineScores[i]);
      state.lineShown[i] = true;
    }

    FileConfiguration cfg = config();
    int toggleSeconds = cfg.getInt(ConfigPaths.Default.Scoreboard.EVO_ATOM_TOGGLE_SECONDS, 5);
    int displayTicks = (int) ((toggleSeconds * 20L) / ANIM_INTERVAL_TICKS);

    boolean canRankup = canRankUp(player);
    String rankInner = colored(buildRankInner(player));
    String rankupInner =
        colored(
            cfg.getString(ConfigPaths.Default.Scoreboard.RANKUP_TOGGLE_TEXT, "&#55FF55&l/rankup"));
    String staticPrefix = colored(" &#888888Rank: ");

    if (!canRankup && state.rankPhase != RankTogglePhase.RANK_DISPLAY) {
      state.rankPhase = RankTogglePhase.RANK_DISPLAY;
      state.rankDisplayTick = 0;
      state.rankAnimCharIndex = 0;
    }

    switch (state.rankPhase) {
      case RANK_DISPLAY -> {
        String full = staticPrefix + rankInner;
        if (!full.equals(state.lastTexts.get(i))) {
          state.lastTexts.set(i, full);
          state.teams.get(i).prefix(legacy(full));
        }
        if (canRankup && ++state.rankDisplayTick >= displayTicks) {
          state.rankAnimFormatted = rankInner;
          state.rankAnimText = stripColors(rankInner);
          state.rankAnimCharIndex = state.rankAnimText.length();
          state.rankPhase = RankTogglePhase.RANK_ERASE;
          state.rankDisplayTick = 0;
        }
      }

      case RANK_ERASE -> {
        state.rankAnimCharIndex = Math.max(0, state.rankAnimCharIndex - 1);
        if (state.rankAnimCharIndex > 0) {
          pushLine(
              state,
              i,
              staticPrefix
                  + truncateFormatted(state.rankAnimFormatted, state.rankAnimCharIndex)
                  + "§8▌");
        } else {
          pushLine(state, i, staticPrefix);
          state.rankAnimFormatted = rankupInner;
          state.rankAnimText = stripColors(rankupInner);
          state.rankAnimCharIndex = 0;
          state.rankPhase = RankTogglePhase.RANKUP_REVEAL;
        }
      }

      case RANKUP_REVEAL -> {
        state.rankAnimCharIndex =
            Math.min(state.rankAnimText.length(), state.rankAnimCharIndex + 1);
        boolean done = state.rankAnimCharIndex >= state.rankAnimText.length();
        if (done) {
          pushLine(state, i, staticPrefix + state.rankAnimFormatted);
          state.rankPhase = RankTogglePhase.RANKUP_DISPLAY;
          state.rankDisplayTick = 0;
        } else {
          pushLine(
              state,
              i,
              staticPrefix
                  + truncateFormatted(state.rankAnimFormatted, state.rankAnimCharIndex)
                  + "§8▌");
        }
      }

      case RANKUP_DISPLAY -> {
        String full = staticPrefix + rankupInner;
        if (!full.equals(state.lastTexts.get(i))) {
          state.lastTexts.set(i, full);
          state.teams.get(i).prefix(legacy(full));
        }
        if (++state.rankDisplayTick >= displayTicks) {
          state.rankAnimFormatted = rankupInner;
          state.rankAnimText = stripColors(rankupInner);
          state.rankAnimCharIndex = state.rankAnimText.length();
          state.rankPhase = RankTogglePhase.RANKUP_ERASE;
          state.rankDisplayTick = 0;
        }
      }

      case RANKUP_ERASE -> {
        state.rankAnimCharIndex = Math.max(0, state.rankAnimCharIndex - 1);
        if (state.rankAnimCharIndex > 0) {
          pushLine(
              state,
              i,
              staticPrefix
                  + truncateFormatted(state.rankAnimFormatted, state.rankAnimCharIndex)
                  + "§8▌");
        } else {
          pushLine(state, i, staticPrefix);
          state.rankAnimFormatted = rankInner;
          state.rankAnimText = stripColors(rankInner);
          state.rankAnimCharIndex = 0;
          state.rankPhase = RankTogglePhase.RANK_REVEAL;
        }
      }

      case RANK_REVEAL -> {
        state.rankAnimCharIndex =
            Math.min(state.rankAnimText.length(), state.rankAnimCharIndex + 1);
        boolean done = state.rankAnimCharIndex >= state.rankAnimText.length();
        if (done) {
          pushLine(state, i, staticPrefix + state.rankAnimFormatted);
          state.rankPhase = RankTogglePhase.RANK_DISPLAY;
          state.rankDisplayTick = 0;
        } else {
          pushLine(
              state,
              i,
              staticPrefix
                  + truncateFormatted(state.rankAnimFormatted, state.rankAnimCharIndex)
                  + "§8▌");
        }
      }
    }
  }

  /**
   * Resets the rank line animation to the RANK_DISPLAY phase. Call this immediately after a player
   * ranks up so the display refreshes.
   *
   * @param player the player who ranked up
   */
  public void resetRankAnimation(Player player) {
    PlayerState state = states.get(player.getUniqueId());
    if (state == null || state.rankLineIndex < 0) return;
    state.rankPhase = RankTogglePhase.RANK_DISPLAY;
    state.rankDisplayTick = 0;
    state.rankAnimCharIndex = 0;
    state.rankAnimText = "";
    state.rankAnimFormatted = "";
    state.lastTexts.set(state.rankLineIndex, null);
  }

  /**
   * Builds the formatted inner text for the rank display: {@code "§c[A1] §8» §e[B1]"} or just
   * {@code "§c[A1]"} at max rank.
   */
  private static String buildRankInner(Player player) {
    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dc == null) return "?";
    PlayerProfile profile = dc.getLocal(player);
    if (profile == null) return "?";

    RankManager rm = RankManager.getInstance();
    String rankId = profile.getRank() != null ? profile.getRank() : rm.getDefaultRank();
    String current = rm.getRankPrefix(rankId);
    String nextRank = rm.getNextRank(rankId);

    if (nextRank == null) return current;

    String next = rm.getRankPrefix(nextRank);
    return current + " &8» " + next;
  }

  /**
   * Returns true when the player's current coin balance meets or exceeds the cost of the next rank
   * (and they are not already at the maximum rank).
   */
  private static boolean canRankUp(Player player) {
    PlayerDataCenter dc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    if (dc == null) return false;
    PlayerProfile profile = dc.getLocal(player);
    if (profile == null) return false;

    RankManager rm = RankManager.getInstance();
    String rankId = profile.getRank() != null ? profile.getRank() : rm.getDefaultRank();
    String nextRank = rm.getNextRank(rankId);
    if (nextRank == null) return false;

    var ranksConfig = ConfigRegistry.getInstance().get(ConfigNames.RANKS);
    if (ranksConfig == null) return false;
    double cost =
        ranksConfig.getDouble(
            ConfigPaths.Ranks.DATA_PREFIX + nextRank + ConfigPaths.Ranks.COST_SUFFIX, 0);
    return profile.wallet.coins >= cost;
  }

  /** Pushes a line text to the team prefix, bypassing the diff cache (for animation). */
  private void pushLine(PlayerState state, int i, String text) {
    state.lastTexts.set(i, text);
    state.teams.get(i).prefix(legacy(text));
  }

  /** Returns {@code true} if holding omnitool. */
  private static boolean isHoldingOmnitool(Player player) {
    org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();
    org.bukkit.inventory.meta.ItemMeta meta = held.getItemMeta();
    return meta != null
        && meta.getPersistentDataContainer()
            .has(
                OmnitoolManager.OMNITOOL_ITEM_KEY,
                org.bukkit.persistence.PersistentDataType.STRING);
  }

  /** Returns {@code true} if pickaxe loaded. */
  private static boolean isPickaxeLoaded(Player player) {
    PlayerDataCenter pkc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    return pkc != null && pkc.getPickaxeForPlayer(player) != null;
  }

  /**
   * Returns a cycling "Loading." / "Loading.." / "Loading..." string based on the given tick. The
   * dot count advances once per second (every 10 animation ticks at 2-tick interval).
   */
  private String loadingText(int tick) {
    int dots = (tick / 10) % 3 + 1;
    String base = config().getString(ConfigPaths.Default.Scoreboard.LOADING_TEXT, " &7Loading");
    return MatrixColorAPI.process(base) + ".".repeat(dots);
  }

  /** Strips all §-coded color sequences from a formatted string. */
  private static String stripColors(String text) {
    if (text == null) return "";
    return text.replaceAll("§.", "");
  }

  /**
   * Returns the formatted string truncated to {@code visibleChars} visible characters, preserving
   * all §-color codes up to that point so original colors are maintained.
   */
  private static String truncateFormatted(String formatted, int visibleChars) {
    StringBuilder result = new StringBuilder();
    int visible = 0;
    int fi = 0;
    while (fi < formatted.length() && visible < visibleChars) {
      char c = formatted.charAt(fi);
      if (c == '§' && fi + 1 < formatted.length()) {
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

  /**
   * Drives the evo, level, and progressbar lines while an enchant selection animation is running.
   * Three visual phases:
   *
   * <ol>
   *   <li><b>Rolling</b> – slot machine scrolling; sweeping arrow animation
   *   <li><b>Selecting</b>– player can look and click; highlighted arrow/square
   *   <li><b>Selected</b> – typewriter reveal of the chosen enchant name
   * </ol>
   */
  private static final int STRIP_SIZE = 50;

  private static final int STRIP_SCROLL_MAX = 28;
  private static final int STRIP_RESULT_0 = 30;
  private static final int STRIP_RESULT_1 = 32;
  private static final int STRIP_RESULT_2 = 34;

  /** Ticks enchant display. */
  private void tickEnchantDisplay(Player player, PlayerState state, BaseEnchantAnimation anim) {
    state.enchantWasActive = true;
    state.enchantArrowTick++;

    ensureLineShown(state, state.evoLineIndex);
    ensureLineShown(state, state.levelLineIndex);
    ensureLineShown(state, state.progressbarLineIndex);

    List<EnchantOption> opts = anim.getEnchantOptions();
    if (opts == null || opts.size() < 3) return;

    if (state.enchantStrip == null) {
      state.enchantStrip = buildVirtualStrip(opts, anim);
    }

    if (anim.isSelectionMade()) {
      if (state.enchantRevealName.isEmpty()) {
        int idx = anim.getSelectedIndex();
        if (idx >= 0 && idx < opts.size()) {
          EnchantOption chosen = opts.get(idx);
          state.enchantRevealName = FontUtils.toSmallCaps(chosen.name());
          state.enchantRevealColor = rarityHex(chosen);
        }
      }
      state.enchantRevealTick++;
      renderSelectedPhase(state, opts, anim.getSelectedIndex());

    } else if (anim.isSelectionPhase()) {
      renderSelectingPhase(state, opts, anim.getHighlightedIndex());

    } else {
      double linearProgress = anim.getLinearScrollProgress();
      double easedProgress = easeInOutQuart(linearProgress);
      int offset = (int) Math.round(easedProgress * STRIP_SCROLL_MAX);
      renderRollingPhase(state, opts, offset);
    }
  }

  /**
   * Builds the 50-entry virtual rarity strip used by the scoreboard bar.
   *
   * <p>When the animation exposes its own strip via {@link
   * BaseEnchantAnimation#getStripRarities()}, the scoreboard strip is derived directly from it so
   * the colours match exactly. The 3D animation uses 30 blocks with result indices {20,22,24}; the
   * scoreboard uses 50 entries with result positions {30,32,34}. An offset of 10 aligns them:
   * {@code scoreboard[i] = animStrip[i - 10]}. Out-of-bounds entries (i &lt; 10 or i &gt; 39) are
   * filled with random rarities to keep the early roll visually smooth.
   *
   * <p>If no strip is available, all positions are independently random.
   */
  private static RarityConfig[] buildVirtualStrip(
      List<EnchantOption> opts, BaseEnchantAnimation anim) {
    RarityConfig[] strip = new RarityConfig[STRIP_SIZE];
    List<RarityConfig> all = EnchantAnimationConfig.getRarities();
    RarityConfig fallback = all.isEmpty() ? null : all.get(0);

    RarityConfig[] animStrip = anim.getStripRarities();
    if (animStrip != null) {
      final int ANIM_OFFSET = 10;
      for (int i = 0; i < STRIP_SIZE; i++) {
        int animIdx = i - ANIM_OFFSET;
        if (animIdx >= 0 && animIdx < animStrip.length && animStrip[animIdx] != null) {
          strip[i] = animStrip[animIdx];
        } else {
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

    strip[STRIP_RESULT_0] = opts.get(0).rarity();
    strip[STRIP_RESULT_1] = opts.get(1).rarity();
    strip[STRIP_RESULT_2] = opts.get(2).rarity();
    return strip;
  }

  /** Renders rolling phase. */
  private void renderRollingPhase(PlayerState state, List<EnchantOption> opts, int offset) {
    int dots = (state.enchantArrowTick / 5) % 3 + 1;
    String rollingBase =
        config()
            .getString(ConfigPaths.Default.Scoreboard.ENCHANT_ROLLING_TEXT, " &#FFD700⚙ Rolling");
    pushLineSafe(
        state, state.evoLineIndex, colored(FontUtils.toSmallCaps(rollingBase + ".".repeat(dots))));
    pushLineSafe(state, state.levelLineIndex, colored(buildArrowLine(opts, -1, false)));
    pushLineSafe(
        state, state.progressbarLineIndex, colored(buildRarityBar(state.enchantStrip, offset)));
  }

  /** Renders selecting phase. */
  private void renderSelectingPhase(PlayerState state, List<EnchantOption> opts, int highlighted) {
    String selectingText =
        config()
            .getString(
                ConfigPaths.Default.Scoreboard.ENCHANT_SELECTING_TEXT, " &#00FF88&lSelecting...");
    pushLineSafe(state, state.evoLineIndex, colored(FontUtils.toSmallCaps(selectingText)));
    pushLineSafe(state, state.levelLineIndex, colored(buildArrowLine(opts, highlighted, true)));
    pushLineSafe(
        state,
        state.progressbarLineIndex,
        colored(buildStoppedBar(state.enchantStrip, opts, highlighted, false)));
  }

  /** Renders selected phase. */
  private void renderSelectedPhase(PlayerState state, List<EnchantOption> opts, int selectedIndex) {
    String name = state.enchantRevealName;
    int chars = Math.min(state.enchantRevealTick, name.length());
    String reveal = name.substring(0, chars);
    String cursor = chars >= name.length() ? "" : "&#888888▌";
    pushLineSafe(
        state, state.evoLineIndex, colored(" " + state.enchantRevealColor + reveal + cursor));

    if (selectedIndex >= 0 && selectedIndex < opts.size()) {
      String rarityName = FontUtils.toSmallCaps(opts.get(selectedIndex).rarity().displayName());
      pushLineSafe(
          state, state.levelLineIndex, colored(" " + state.enchantRevealColor + "&l" + rarityName));
    }

    pushLineSafe(
        state,
        state.progressbarLineIndex,
        colored(buildStoppedBar(state.enchantStrip, opts, selectedIndex, true)));
  }

  /**
   * Builds the indicator line above the rarity bar using ▄ (LOWER HALF BLOCK, U+2584).
   *
   * <p>▄ is chosen over ▼ because it occupies the <em>same full character width</em> as █ (both are
   * block-elements that fill the entire 8-pixel advance width). ▼ is a triangle that carries less
   * visual mass and appears smaller even at the same advance width. ▄ fills the bottom half of its
   * cell, so when rendered directly above the bar's █ icons it creates a solid downward indicator
   * with matching visual weight.
   *
   * <p>Both lines share the same prefix {@code " "} and use {@code █} spacers at non-result
   * positions so every character slot has identical pixel widths.
   *
   * <ul>
   *   <li>Rolling: dim grey ▄ at all three positions — bar is the focal point.
   *   <li>Selecting: hovered = rarity colour ▄; others = very dim ▄.
   *   <li>Selected: winner = rarity colour ▄; losers = very dim ▄.
   * </ul>
   */
  private static String buildArrowLine(
      List<EnchantOption> opts, int highlighted, boolean isSelecting) {
    final String DARK = "&#0D0D0D";
    String[] ac = new String[3];
    for (int i = 0; i < 3; i++) {
      ac[i] = !isSelecting ? "&#666666" : (i == highlighted) ? rarityHex(opts.get(i)) : "&#2A2A2A";
    }
    return " " + DARK + "██" + ac[0] + "▄" + DARK + "█" + ac[1] + "▄" + DARK + "█" + ac[2] + "▄"
        + DARK + "██";
  }

  /**
   * Maps each Minecraft concrete material to its actual block texture colour so the scoreboard bar
   * squares exactly match what the player sees in the 3D animation. Colours sampled from the
   * standard Minecraft 1.21 concrete block textures.
   */
  private static final Map<Material, TextColor> CONCRETE_COLORS = new HashMap<>();

  static {
    CONCRETE_COLORS.put(Material.WHITE_CONCRETE, TextColor.fromHexString("#CFD5D6"));
    CONCRETE_COLORS.put(Material.ORANGE_CONCRETE, TextColor.fromHexString("#E06101"));
    CONCRETE_COLORS.put(Material.MAGENTA_CONCRETE, TextColor.fromHexString("#BD44B3"));
    CONCRETE_COLORS.put(Material.LIGHT_BLUE_CONCRETE, TextColor.fromHexString("#2489C7"));
    CONCRETE_COLORS.put(Material.YELLOW_CONCRETE, TextColor.fromHexString("#F1AF15"));
    CONCRETE_COLORS.put(Material.LIME_CONCRETE, TextColor.fromHexString("#5EA919"));
    CONCRETE_COLORS.put(Material.PINK_CONCRETE, TextColor.fromHexString("#D5658F"));
    CONCRETE_COLORS.put(Material.GRAY_CONCRETE, TextColor.fromHexString("#383838"));
    CONCRETE_COLORS.put(Material.LIGHT_GRAY_CONCRETE, TextColor.fromHexString("#7D7D73"));
    CONCRETE_COLORS.put(Material.CYAN_CONCRETE, TextColor.fromHexString("#157788"));
    CONCRETE_COLORS.put(Material.PURPLE_CONCRETE, TextColor.fromHexString("#641D92"));
    CONCRETE_COLORS.put(Material.BLUE_CONCRETE, TextColor.fromHexString("#2D2F97"));
    CONCRETE_COLORS.put(Material.BROWN_CONCRETE, TextColor.fromHexString("#603B1F"));
    CONCRETE_COLORS.put(Material.GREEN_CONCRETE, TextColor.fromHexString("#495B24"));
    CONCRETE_COLORS.put(Material.RED_CONCRETE, TextColor.fromHexString("#8E2020"));
    CONCRETE_COLORS.put(Material.BLACK_CONCRETE, TextColor.fromHexString("#080A0F"));
  }

  /** Returns the actual concrete block texture colour for a rarity, falling back to textColor. */
  private static TextColor concreteColor(RarityConfig rarity) {
    TextColor tc = CONCRETE_COLORS.get(rarity.concreteMaterial());
    return tc != null ? tc : rarity.textColor();
  }

  /**
   * Renders the 10-icon bar during rolling by slicing {@code strip[offset..offset+9]}. As the
   * offset increases from 0 → {@value STRIP_SCROLL_MAX} using the same easeInOutQuart curve as the
   * 3D animation, the bar decelerates and stops with the result rarities naturally landing at bar
   * positions 2, 5, 8.
   */
  private static String buildRarityBar(RarityConfig[] strip, int offset) {
    StringBuilder sb = new StringBuilder(" ");
    for (int p = 0; p < 9; p++) {
      int idx = Math.min(offset + p, strip.length - 1);
      TextColor tc = concreteColor(strip[idx]);
      sb.append(String.format("&#%02X%02X%02X", tc.red(), tc.green(), tc.blue())).append("█");
    }
    return sb.toString();
  }

  /**
   * Renders the bar in the stopped state (selecting or selected). Result rarities appear at
   * positions 2, 5, 8 (where the strip stopped). Filler positions show the actual strip colour
   * dimmed; result positions are either highlighted (selecting) or bright/empty (selected).
   */
  private static String buildStoppedBar(
      RarityConfig[] strip, List<EnchantOption> opts, int highlighted, boolean isSelected) {
    StringBuilder sb = new StringBuilder(" ");
    int[] resultAt = {-1, -1, 0, -1, 1, -1, 2, -1, -1};
    for (int p = 0; p < 9; p++) {
      int optIdx = resultAt[p];
      if (optIdx < 0) {
        TextColor tc = concreteColor(strip[STRIP_SCROLL_MAX + p]);
        int r = tc.red() / 5;
        int g = tc.green() / 5;
        int bv = tc.blue() / 5;
        sb.append(
                String.format(
                    "&#%02X%02X%02X", Math.max(r, 0x1A), Math.max(g, 0x1A), Math.max(bv, 0x1A)))
            .append("█");
      } else if (isSelected) {
        sb.append(optIdx == highlighted ? concreteHex(opts.get(optIdx)) + "█" : "&#333333░");
      } else {
        if (optIdx == highlighted) {
          sb.append(concreteHex(opts.get(optIdx))).append("█");
        } else {
          TextColor tc = concreteColor(opts.get(optIdx).rarity());
          sb.append(
                  String.format(
                      "&#%02X%02X%02X",
                      Math.max(tc.red() / 4, 0x10),
                      Math.max(tc.green() / 4, 0x10),
                      Math.max(tc.blue() / 4, 0x10)))
              .append("█");
        }
      }
    }
    return sb.toString();
  }

  /** Ease in out quart. */
  private static double easeInOutQuart(double x) {
    return x < 0.5 ? 8 * x * x * x * x : 1 - Math.pow(-2 * x + 2, 4) / 2;
  }

  /** Rarity hex. */
  private static String rarityHex(EnchantOption opt) {
    TextColor tc = opt.rarity().textColor();
    return String.format("&#%02X%02X%02X", tc.red(), tc.green(), tc.blue());
  }

  /** Concrete hex. */
  private static String concreteHex(EnchantOption opt) {
    TextColor tc = concreteColor(opt.rarity());
    return String.format("&#%02X%02X%02X", tc.red(), tc.green(), tc.blue());
  }

  /** Colored. */
  private static String colored(String text) {
    return MatrixColorAPI.process(text);
  }

  /** Push line safe. */
  private void pushLineSafe(PlayerState state, int lineIndex, String text) {
    if (lineIndex < 0 || lineIndex >= state.teams.size()) return;
    pushLine(state, lineIndex, text);
  }

  /** Ensures line shown. */
  private void ensureLineShown(PlayerState state, int lineIndex) {
    if (lineIndex < 0 || lineIndex >= state.lineShown.length) return;
    if (!state.lineShown[lineIndex]) {
      state.objective.getScore(ENTRIES[lineIndex]).setScore(state.lineScores[lineIndex]);
      state.lineShown[lineIndex] = true;
    }
  }

  /**
   * Shows or hides the prestige scoreboard line based on the player's current prestige level. When
   * prestige == 0 the line is removed from the sidebar entirely; when prestige > 0 the line is
   * shown with the configured prestige text.
   */
  private void handlePrestigeLine(Player player, PlayerState state, int lineIndex) {
    PlayerDataCenter pkc = DataCenterRegistry.getInstance().getPlayerDataCenter();
    Pickaxe pk = pkc != null ? pkc.getPickaxeForPlayer(player) : null;
    int prestige = pk != null ? pk.prestige : 0;

    if (prestige == 0) {
      if (state.lineShown[lineIndex]) {
        state.board.resetScores(ENTRIES[lineIndex]);
        state.lineShown[lineIndex] = false;
        state.lastTexts.set(lineIndex, null);
      }
      return;
    }

    if (!state.lineShown[lineIndex]) {
      state.objective.getScore(ENTRIES[lineIndex]).setScore(state.lineScores[lineIndex]);
      state.lineShown[lineIndex] = true;
    }

    FileConfiguration msgs = ConfigRegistry.getInstance().get(ConfigNames.MESSAGES);
    String raw =
        msgs.getString(
            ConfigPaths.Messages.Pickaxe.PRESTIGE_SCOREBOARD_LINE, " &5&lPrestige &d" + prestige);
    String formatted =
        Utils.formatMessage(
            player,
            raw.replace("%evolv3_pickaxe_prestige%", String.valueOf(prestige))
                .replace("%prestige%", String.valueOf(prestige)));
    if (!formatted.equals(state.lastTexts.get(lineIndex))) {
      state.lastTexts.set(lineIndex, formatted);
      state.teams.get(lineIndex).prefix(legacy(formatted));
    }
  }

  /** Starts tasks. */
  private void startTasks() {
    int intervalTicks = config().getInt(ConfigPaths.Default.Scoreboard.UPDATE_INTERVAL_TICKS, 20);
    updateTask =
        Bukkit.getScheduler()
            .runTaskTimer(
                plugin,
                () -> {
                  for (Player player : Bukkit.getOnlinePlayers()) update(player);
                },
                intervalTicks,
                intervalTicks);

    animTask =
        Bukkit.getScheduler()
            .runTaskTimer(
                plugin,
                () -> {
                  for (Player player : Bukkit.getOnlinePlayers()) tickAnimation(player);
                },
                ANIM_INTERVAL_TICKS,
                ANIM_INTERVAL_TICKS);
  }

  /** Stops tasks. */
  private void stopTasks() {
    if (updateTask != null) {
      updateTask.cancel();
      updateTask = null;
    }
    if (animTask != null) {
      animTask.cancel();
      animTask = null;
    }
  }

  /** Returns {@code true} if enabled. */
  private boolean isEnabled() {
    return config().getBoolean(ConfigPaths.Default.Scoreboard.ENABLED, true);
  }

  /** Config. */
  private FileConfiguration config() {
    return ConfigRegistry.getInstance().get(ConfigNames.DEFAULT);
  }

  /** Legacy. */
  private static Component legacy(String text) {
    return LegacyComponentSerializer.legacySection().deserialize(text);
  }

  private static final class PlayerState {
    final Scoreboard board;
    final Objective objective;
    final List<Team> teams;
    final List<String> lastTexts;

    final int[] lineScores;
    final boolean[] lineShown;
    final int evoLineIndex;
    final int levelLineIndex;
    final int progressbarLineIndex;
    final int rankLineIndex;
    final int prestigeLineIndex;

    EvoAtomPhase evoAtomPhase = EvoAtomPhase.EVO_DISPLAY;
    int displayTick = 0;
    int animCharIndex = 0;
    String animText = "";
    String animFormattedText = "";
    String coloredPrefix = "";
    int loadingTick = 0;

    boolean levelUpAnimating = false;
    int levelUpTick = 0;
    int levelUpNewLevel = 0;

    RankTogglePhase rankPhase = RankTogglePhase.RANK_DISPLAY;
    int rankDisplayTick = 0;
    int rankAnimCharIndex = 0;
    String rankAnimText = "";
    String rankAnimFormatted = "";

    String lastTitle = null;

    boolean enchantWasActive = false;
    int enchantArrowTick = 0;
    int enchantRevealTick = 0;
    String enchantRevealName = "";
    String enchantRevealColor = "";
    RarityConfig[] enchantStrip = null;

    PlayerState(
        Scoreboard board,
        Objective objective,
        List<Team> teams,
        List<String> lastTexts,
        int[] lineScores,
        boolean[] lineShown,
        int evoLineIndex,
        int levelLineIndex,
        int progressbarLineIndex,
        int rankLineIndex,
        int prestigeLineIndex) {
      this.board = board;
      this.objective = objective;
      this.teams = teams;
      this.lastTexts = lastTexts;
      this.lineScores = lineScores;
      this.lineShown = lineShown;
      this.evoLineIndex = evoLineIndex;
      this.levelLineIndex = levelLineIndex;
      this.progressbarLineIndex = progressbarLineIndex;
      this.rankLineIndex = rankLineIndex;
      this.prestigeLineIndex = prestigeLineIndex;
    }
  }
}
