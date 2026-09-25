# Knockback Analyzer

Advanced knockback analysis mod for Minecraft **1.8.9** (Forge).

## Features

| Command | Description |
|---|---|
| `/kbtester [n]` | **Auto-extract the server's knockback profile** — fully client-side, undetectable |
| `/findkb <n>` | Track your own knockback for N hits |
| `/kbtrack <player>` | Track another player's knockback |
| `/kbgetvelocity` | Live velocity display |
| `/kbcenter` | Smoothly center yourself on a block |
| `/kbcancel` | Stop everything |

## How to get the jar (NO manual Gradle needed)

1. Push this project to GitHub.
2. Go to the **Actions** tab of your repository.
3. Wait for the workflow to finish (green check).
4. Click the workflow run → scroll to **Artifacts** → download **kbanalyzer-build.zip**.
5. Unzip → drop `kbanalyzer-2.0.0.jar` into your `.minecraft/mods/` folder.

That's it. GitHub builds everything for you.

## How `/kbtester` works (no server detection)

- It only **reads** incoming `S12PacketEntityVelocity` packets.
- It **never** sends or modifies outgoing packets.
- No fake hits, no automation — you just play normally.
- Produces a full knockback profile with extracted multipliers.
