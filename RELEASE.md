# Release Instructions

This document explains how to create releases for the WebSocket Proxy project.

## Automated Releases

The project uses GitHub Actions to automatically build and release artifacts when version tags are pushed.

### Creating a Release

1. **Update version in pom.xml** (if needed):
   ```xml
   <version>1.1.0</version>
   ```

2. **Commit and push changes**:
   ```bash
   git add pom.xml
   git commit -m "Bump version to 1.1.0"
   git push origin main
   ```

3. **Create and push a version tag**:
   ```bash
   git tag v1.1.0
   git push origin v1.1.0
   ```

4. **Monitor the release build**:
   - Go to GitHub Actions tab
   - Watch the "Build and Release" workflow
   - The workflow will automatically create a GitHub release with artifacts

### Release Artifacts

The automated release creates:

1. **websocket-proxy-v1.1.0-standalone.jar**
   - Self-contained executable JAR with all dependencies
   - Can be run directly with `java -jar`

2. **websocket-proxy-v1.1.0-complete.zip** 
   - Complete package including:
     - Standalone JAR
     - Launcher scripts (`run-proxy.sh`, `run-validator.sh`)
     - Example schema files
     - Documentation (README.md, SCHEMA_VALIDATION.md)

3. **checksums.txt**
   - SHA256 checksums for verification

## Manual Release (if needed)

If you need to create a release manually:

```bash
# Build the project
mvn clean package

# Create release package
mkdir release-package
cp run-proxy.sh run-validator.sh release-package/
cp target/websocket-proxy-*-standalone.jar release-package/
cp -r schemas release-package/
cp README.md SCHEMA_VALIDATION.md release-package/

# Create archive
cd release-package
zip -r ../websocket-proxy-v1.1.0-complete.zip .
cd ..

# Create checksums
sha256sum target/websocket-proxy-*-standalone.jar > checksums.txt
sha256sum websocket-proxy-v1.1.0-complete.zip >> checksums.txt
```

Then manually create the GitHub release and upload the artifacts.

## Version Numbering

This project follows [Semantic Versioning](https://semver.org/):

- **MAJOR** (X.0.0): Incompatible API changes
- **MINOR** (X.Y.0): Add functionality in a backwards compatible manner  
- **PATCH** (X.Y.Z): Backwards compatible bug fixes

Examples:
- `v1.0.0` - Initial stable release
- `v1.1.0` - Added new feature (schema validation)
- `v1.1.1` - Bug fix release
- `v2.0.0` - Breaking changes to command-line interface

## Testing Releases

Before tagging a release:

1. **Test locally**:
   ```bash
   mvn clean package
   java -jar target/*-standalone.jar --help
   ./run-proxy.sh --help
   ./test_validator.sh
   ```

2. **Verify CI passes**:
   - Check that all GitHub Actions workflows pass
   - Review any dependency updates from Dependabot

3. **Test key functionality**:
   - Proxy basic WebSocket connections
   - Schema validation with sample data
   - PCAP file generation

## Release Notes

GitHub Actions automatically generates release notes, but you can customize them:

1. After the release is created, edit it on GitHub
2. Add specific changelog information
3. Highlight breaking changes or important updates
4. Include usage examples for new features

## Rollback

If a release has issues:

1. Delete the problematic tag and release
2. Fix the issues
3. Create a new patch version
4. Never reuse version numbers