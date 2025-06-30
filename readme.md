# Inventory Reader

## Also known as Skyblock Mining Resource Reader

A Minecraft Fabric mod for Hypixel Skyblock that tracks inventory, storage, and sack data, with advanced recipe and resource management.

---

## Overview
Inventory Reader automatically monitors your in-game inventories, containers, and sacks to keep track of resource changes. The mod provides enhanced resource tracking, recipe calculations, and crafting planning.

## Features
- **Automatic Resource Tracking:** Monitors player inventory, storage containers, and sacks in real time
- **Recipe Management:** View, expand, and calculate crafting recipes for Hypixel Skyblock items
- **Crafting Calculator:** See required ingredients and missing resources for any recipe and amount
- **HUD Widget:** In-game overlay for quick resource and recipe info, with full customization
- **Data Persistence:** All data is saved locally and persists between sessions
- **In-Game Commands & Shortcuts:** Access all features via chat commands or keybinds
- **First-Time User Onboarding:** Welcome message and instructions on first launch

## Requirements
- Minecraft 1.21.5+
- Fabric Loader 0.16.11+
- Java 21

## Installation
1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft 1.21.5 or newer.
2. Download the latest release jar from the [Releases](https://github.com/Scholiboi/InventoryReader-HypixelMining/releases) page.
3. Place the jar file in your Minecraft `mods` folder.
4. Launch Minecraft with the Fabric profile.

## Usage
- Play normally on Hypixel Skyblock. The mod tracks your resources in the background.
- Use the in-game HUD widget and GUI to view resources, recipes, and plan crafting.
- Data is saved to local JSON files.

## Files Created
All mod data is stored in `ir-data/data/` inside your Minecraft directory:
- `allcontainerData.json` — Storage container data
- `inventorydata.json` — Player inventory data
- `resources.json` — All tracked resources
- `widget_config.json` — HUD widget settings
- `forging.json`, `gemstone_recipes.json` — Recipe data
- `sackNames.txt` — List of recognized sack names

## In-Game Commands
- `/ir menu` — Open the Sandbox Viewer (resource/recipe GUI)
- `/ir widget` — Open Widget Customization Menu
- `/ir reset` — Reset all mod data
- `/ir done` — Acknowledge reminders

## Keyboard Shortcuts
- **V** — Open Sandbox Viewer
- **B** — Open Widget Customization

## Development
To build from source:
1. Clone this repository.
2. Ensure you have Java 21 and Gradle installed.
3. Run `./gradlew build` to build the mod.
4. The output jar will be in `build/libs/`.

## Troubleshooting
- Verify your Minecraft version matches the supported version.
- Check logs for any error messages.
- Restart Minecraft if you encounter issues.

## License
This project is licensed under the terms of the [CC-BY-SA-4.0 License](LICENSE).

## Credits
Created by Scholiboi

## Support
For questions, issues, or feature requests, please [open an issue](https://github.com/Scholiboi/InventoryReader-HypixelMining/issues) on GitHub.
