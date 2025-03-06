# Inventory Reader

A Minecraft Fabric mod that seamlessly tracks inventory and storage data for Hypixel Skyblock.

## Overview
Inventory Reader automatically monitors your in-game inventories, containers, and sacks to keep track of resource changes. The mod works alongside a companion application to provide enhanced resource tracking functionality.

## Features
- **Automatic Resource Tracking**: Monitors player inventory and storage containers  
- **Real-time Change Detection**: Tracks additions and removals of items  
- **Sack Integration**: Special support for reading Hypixel Skyblock sack contents  
- **Data Persistence**: Maintains resource records between gaming sessions  
- **Companion App Integration**: Works with a desktop application for enhanced functionality  

## Requirements
- Minecraft 1.21.1  
- Fabric Loader 0.16.3+  
- Java 21  

## Installation
1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft 1.21.1.  
2. Download the latest release jar from the [Releases](https://github.com/yourusername/FabricMod_1/releases) page.  
3. Place the jar file in your Minecraft `mods` folder.  
4. Launch Minecraft with the Fabric profile.  
5. On first run, the mod automatically downloads and starts the companion application.

## Usage
1. Launch Minecraft with the mod installed.  
2. Play normally on Hypixel Skyblock.  
3. The mod tracks your resources automatically in the background.  
4. Data is saved to local JSON files and synchronized with the companion app.

## Files Created
- `hypixel_dwarven_forge-v1.0.0.exe` - Companion application  
- `allcontainerData.json` - Storage container data  
- `inventorydata.json` - Player inventory data  

## Troubleshooting
- Check that the companion app is running (it should start automatically).  
- Verify your Minecraft version matches the supported version.  
- Check logs for any error messages.  
- Restart Minecraft if the companion app connection fails.

## License
This project is licensed under the terms of the [CC0 1.0 Universal License](LICENSE).  

## Credits
Created by Scholiboi  

## Support
For questions, issues, or feature requests, please [open an issue](https://github.com/yourusername/FabricMod_1/issues) on GitHub.