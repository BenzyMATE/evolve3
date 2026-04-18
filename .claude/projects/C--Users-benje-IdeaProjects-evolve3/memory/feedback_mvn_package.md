---
name: Use mvn package not mvn compile
description: When verifying builds, run mvn package instead of mvn compile — the project needs the full package phase
type: feedback
---

Use `mvn package` rather than `mvn compile` when verifying the project builds.

**Why:** The project uses maven-shade-plugin and wagon-maven-plugin in the package phase. `mvn compile` alone doesn't fully validate the build.

**How to apply:** After making code changes, always run `mvn package` to verify.
