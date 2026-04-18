---
name: Maven CLI setup on this machine
description: Maven and Java paths for running builds from CLI on this Windows machine
type: feedback
---

Maven is not on PATH. To run builds from CLI:

- **Maven:** `C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd`
- **JAVA_HOME:** `C:\Users\benje\.jdks\openjdk-25.0.1`
- Set JAVA_HOME via `[System.Environment]::SetEnvironmentVariable('JAVA_HOME', '...', 'Process')` in PowerShell before invoking mvn.cmd

**Why:** Only JDK available is Java 25 bundled with IntelliJ. No standalone Maven install.

**How to apply:** Use these paths whenever running Maven commands.
