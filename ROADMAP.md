# Roadmap

Planned work for Evolv3, roughly in priority order. Background for the Paper
items is in [docs/paper-26-upgrade-notes.md](docs/paper-26-upgrade-notes.md).

## Next up

- [ ] **Align the Java version**: set `java.version` in `pom.xml` to `25` so it
      matches Paper 26.x and the CI build.
- [ ] **Automated tests**: add JUnit 5 + MockBukkit and cover the progression
      math (XP, evolution, prestige caps), enchant proc chances and config
      loading.
- [ ] **Make local deploy opt-in**: switch the `local-deploy` Maven profile to
      activate on a property, with the target folder set by the developer
      instead of hardcoded.
- [ ] **Remove the `/test` command** before release.

## Paper 26 features

- [ ] **Brigadier commands**: move commands to Paper's Brigadier API for
      typed arguments and client-side tab completion.
- [ ] **More native dialogs**: prestige confirmation, cell and island naming
      (text input), and stone selection confirmation.
- [ ] **Display-entity stone flight**: replace teleport-stepped stone movement
      with interpolated display entities for smoother animations.
- [ ] **Data Component API**: use item data components where `ItemMeta` falls
      short (custom models, tooltips, glint).
- [ ] **New particles**: add the 26.x particles to rarity reveals and ambient
      mine effects.

## Gameplay

- [ ] **Crates**: keys and crates with configurable reward tables and an
      opening animation.
- [ ] **Events**: timed server events such as boosted XP, essence or coin
      multipliers and mining challenges.
- [ ] **RPG elements**: player stats, skills and class-style progression
      alongside the pickaxe.
- [ ] More evolution animation styles and pickup animations.
- [ ] Island upgrades and visit statistics.
- [ ] Leaderboards for level, prestige and coins through PlaceholderAPI.

## Staff tools

- [ ] **Staff menu and commands**: a staff GUI and commands for moderation,
      player lookup and inspecting or editing player data.

## Tooling

- [ ] **Refactor**: break up large classes, reduce duplication between
      managers and tidy package structure.
- [ ] Run SpotBugs and PMD in CI and fail the build on new findings.
- [ ] Add a pull-request workflow that builds without deploying.
