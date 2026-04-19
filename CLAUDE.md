# Evolv3 Project Context

## Development Environment

- **Server Platform**: Paper 1.21.1
- **Plugin Target**: Minecraft 1.21.1
- **Build Tool**: Maven (not Gradle)

## Code Standards

- Always find the best solution using industry-standard patterns
- Write clean, well-commented code
- Avoid compiler warnings - treat warnings as errors
- No messy or hacky solutions

## Configuration

- **Never hardcode** editable values like messages, default values, or configurable settings
- All user-facing messages must be stored in config files (e.g., `messages.yml`)
- Default values and settings should be in appropriate config files (e.g., `config.yml`, `settings.yml`)
- If no relevant config file exists, create one
- Use ConfigRegistry to load and access config files

## Workflow

- Always package with `mvn package` after making changes to verify no errors or warnings
- Use Registry API for sounds, enchantments, and other registry-based lookups (e.g.,
  `Registry.SOUNDS.get(NamespacedKey.minecraft(...))`)
- Avoid deprecated methods scheduled for removal in 1.22+
