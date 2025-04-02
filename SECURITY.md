# Security Measures in Inventory Reader Mod

## Why This Mod Uses a Native Executable

This mod requires a native executable component because:
1. It needs to interface with system-level APIs not available in Java
2. It runs a local web server to provide real-time inventory data to external applications
3. It handles optimized data processing that would be inefficient in Minecraft's runtime

## Security Measures

To protect users, we implement the following security measures:

### Checksum Verification
- Every executable file is verified using SHA-256 checksums
- Checksums are hardcoded in the mod code and tied to specific mod versions
- Files that fail verification are automatically quarantined

### Transparent Source Code
- All source code for both the mod and executable are available on GitHub
- Users can review, compile, and verify the executable themselves

### Controlled Distribution
- Executables are only downloaded from official GitHub releases
- Each mod version is locked to a specific executable version

### Safe Storage
- All files are stored in a dedicated mod directory
- Legacy files from previous versions are automatically cleaned up

## Verifying Checksums Manually

You can manually verify the executable's checksum:

1. Download the file manually from [GitHub Releases](https://github.com/Scholiboi/hypixel-forge/releases)
2. Calculate SHA-256 checksum using a tool like:
   - On Windows: `certutil -hashfile <filename> SHA256`
   - On Mac: `shasum -a 256 <filename>`
   - On Linux: `sha256sum <filename>`
3. Compare with the expected checksum shown in the mod's download screen