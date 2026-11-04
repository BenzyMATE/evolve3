# Evolv3

Evolv3 is the core gameplay plugin for **EvolveMC**, a mining and progression
Minecraft server. Players mine with a single pickaxe that levels up, evolves,
and prestiges. Along the way they unlock custom enchants, socket evolution
stones, fill backpacks, rank up, and rent cells and islands. Most of these
moments come with their own animation.

## Who it's for

- **Server owners and admins** running a Paper 26.x mining or prison-style
  server who want a full progression loop out of the box.
- **Players** on EvolveMC. This plugin is what they interact with.
- **Developers** who want to see a large, config-driven Paper plugin built on
  the newer Paper APIs (plugin bootstrapper, Dialog API, display entities,
  Registry API).

## Features

- **Evolving pickaxe**: XP, levels, evolutions and prestiges, with a choice of
  evolution animation styles (`/evolutionstyle`).
- **Custom enchants**:
  - Mining enchants: Cascade, Drill, Explosive, Frostbite, Jackhammer, Lazer,
    Nuclear Shower, Thor, Vortex.
  - Passive enchants: Alchemy, Auto-Sell, Efficiency, Extraction, Fortune,
    Furnace, Merchant, Radioactive, Scavenger, Unbreaking.
- **Evolution stones**: rarity-tiered stones (common to legendary) that socket
  into the pickaxe and change how its enchants behave.
- **Backpacks**: tiered storage for mined blocks (`/backpack`).
- **Economy and ranks**: coins and essence, `/rankup`, `/bal`, optional Vault
  hook.
- **Mines, cells and islands**: WorldGuard-backed mine regions, rentable cells
  (`/cell`) and player islands (`/island`) with members and visit controls.
- **World features**: launch pads, beams, borders, spawners and schematics
  (spawn and cave).
- **Menus and preferences**: inventory GUIs plus native Paper dialogs for
  player preferences (`/preferences`) and pickup animations
  (`/pickupanimation`).
- **PlaceholderAPI expansion**: `%evolv3_...%` placeholders for scoreboards
  and other plugins.
- **Custom resource pack**: textures, models, fonts, tooltips and sounds in
  [`resource-pack/`](resource-pack).

## Built with

| Area | Technology |
| --- | --- |
| Language | Java 25 |
| Server API | [Paper](https://papermc.io/) 26.1.2 (`paper-api`, plugin bootstrapper) |
| Build | Maven, with the Shade plugin to produce a single jar |
| Integrations | PlaceholderAPI, WorldGuard / WorldEdit, Vault (all optional at runtime) |
| Libraries | Gson, Lombok, MatrixColorAPI |
| Code quality | fmt-maven-plugin (Google Java Format), SpotBugs, PMD |
| Persistence | A separate EvolveMC HTTP API (player profiles), called through `EvolveApiClient` |
| CI/CD | GitHub Actions: builds on every push to `master`, uploads to the server over SFTP, publishes the resource pack, then hot-reloads the plugin |

## Getting started

### Prerequisites

- **JDK 25** (Paper 26.x needs Java 25)
- **Maven 3.9+**
- A **Paper 26.1.2** server to run the plugin on
- Optional: PlaceholderAPI, WorldGuard + WorldEdit, and Vault on the server

### Build

```bash
git clone https://github.com/BenzyMATE/evolve3.git
cd evolve3
mvn package
```

The plugin jar is written to `target/Evolv3.jar`.

> On Windows, a `local-deploy` Maven profile turns on automatically and copies
> the jar into the author's local test server folder. To skip it, run
> `mvn package -P '!local-deploy'`.

### Run

1. Copy `target/Evolv3.jar` into your server's `plugins/` folder.
2. Start the server once. Evolv3 creates its config files under
   `plugins/Evolv3/`.
3. Edit the configs (see below), then restart the server or run
   `/evolv3 reload`.
4. (Optional) Zip the contents of `resource-pack/` with `pack.mcmeta` at the
   root of the zip, host it, and set it as the server resource pack in
   `server.properties`.

### Configuration

All gameplay values and player-facing text live in YAML files under
`src/main/resources/`, which are copied to `plugins/Evolv3/` on first run:

| File | Purpose |
| --- | --- |
| `default.yml` | Core settings: scoreboard, API connection, mine region, schedules, block values |
| `messages.yml` | All player-facing messages |
| `enchants.yml`, `stones.yml`, `scrolls.yml` | Enchant, stone and scroll definitions |
| `ranks.yml`, `mines.yml`, `cells.yml` | Ranks, mines and cells |
| `menus.yml` | GUI layouts and item text |
| `beams.yml`, `borders.yml`, `launchpads.yml`, `spawners.yml` | World features |

Player data is stored through the EvolveMC HTTP API, configured in
`default.yml`:

```yaml
Database:
  ApiUrl: "https://your-api.example.com/"
  SslVerify: true
  ApiKey: ""   # set on your server only, never commit a real key
```

No secrets are committed to this repository. The `ApiKey` default is blank
and is meant to be filled in on the server's own copy of the config.

### Commands

| Command | Aliases | Description |
| --- | --- | --- |
| `/pickaxe` | `/pick`, `/p` | Open the pickaxe menu |
| `/backpack` | `/bp`, `/bag` | Open your backpack |
| `/rankup` | | Rank up when you can afford it |
| `/bal [player]` | `/balance`, `/coins`, `/money` | Show coin balance |
| `/preferences` | `/prefs`, `/settings` | Personal preferences |
| `/pickupanimation` | `/pa` | Choose a pickup animation |
| `/evolutionstyle` | `/evostyle` | Choose an evolution animation |
| `/cell` | | Rent and manage a cell |
| `/island` | `/islands` | Manage your island |
| `/evolv3 <subcommand>` | `/ev3`, `/e3`, `/e` | Admin tools: reload, mines, borders, beams, launch pads, ranks… (`evolv3.admin`) |
| `/enchantpickaxe <enchant> <level>` | `/ep` | Admin: set an enchant on the held pickaxe (`evolv3.admin`) |

### Deployment (maintainers)

`.github/workflows/deploy.yml` builds and deploys on every push to `master`.
It reads these values from GitHub Actions secrets (never from the repository):

- `SFTP_USERNAME`, `SFTP_PASSWORD`: server file access
- `PACK_REPO_TOKEN`: publishes the resource pack release
- `WEBHOOK_PORT`, `WEBHOOK_TOKEN`: triggers the plugin hot-reload

## Project layout

```
src/main/java/org/benski/evolv3/
├── animations/     enchant, pickup and progression animations
├── api/            HTTP client and DTOs for the EvolveMC API
├── commands/       admin and player commands
├── core/           managers, helpers, constants, shared types
├── enchants/       mining and passive enchants plus their stone handlers
├── listeners/      Bukkit event listeners
├── menus/          inventory GUIs
├── models/         player, pickaxe, backpack and armor models
└── registry/       config and content registries
src/main/resources/ YAML configs, plugin descriptor, schematics
resource-pack/      client resource pack (textures, models, sounds)
docs/               design and upgrade notes
```

## Roadmap

Planned work is tracked in [ROADMAP.md](ROADMAP.md).

## License

Copyright © 2026 BenzyMATE. Licensed under the
[Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE) for
attribution.
