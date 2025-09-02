# GitHub Actions Setup

This document explains the GitHub Actions workflows configured for the WebSocket Proxy project.

## Workflows

### 1. Continuous Integration (`.github/workflows/ci.yml`)

**Triggers:** 
- Push to `main`/`master` branches
- Pull requests to `main`/`master` branches

**Actions:**
- Builds the project with Maven
- Runs tests
- Verifies both JAR files are created
- Tests launcher scripts
- Tests basic application functionality
- Uploads debug artifacts if build fails

### 2. Build and Release (`.github/workflows/build-and-release.yml`)

**Triggers:**
- Git tags starting with `v*` (e.g., `v1.0.0`, `v2.1.3`)
- Also runs on pushes/PRs for testing (but only releases on tags)

**Actions:**
- Builds the project 
- Tests proxy and validator functionality
- Creates release package with:
  - Standalone JAR
  - Launcher scripts
  - Example schemas
  - Documentation
- Creates GitHub release with assets
- Generates SHA256 checksums

### 3. Schema Testing (`test-schemas` job in build-and-release.yml)

**Purpose:**
- Validates that schema validation works correctly
- Tests with sample JSON-RPC messages
- Ensures schemas are properly formatted

## Creating a Release

### Automated Process

1. **Update version** (if needed):
   ```bash
   # Edit pom.xml version
   git commit -am "Bump version to 1.1.0"
   git push
   ```

2. **Create and push tag**:
   ```bash
   git tag v1.1.0
   git push origin v1.1.0
   ```

3. **Monitor workflow**:
   - GitHub Actions will automatically build and create the release
   - Check the "Actions" tab for build progress
   - Release will appear in "Releases" section when complete

### Manual Release (if needed)

If the automated workflow fails, you can create releases manually using the instructions in `RELEASE.md`.

## Release Assets

Each release includes:

1. **`websocket-proxy-vX.Y.Z-standalone.jar`**
   - Single executable JAR with all dependencies
   - Usage: `java -jar websocket-proxy-vX.Y.Z-standalone.jar [options]`

2. **`websocket-proxy-vX.Y.Z-complete.zip`**
   - Complete package containing:
     - Standalone JAR
     - `run-proxy.sh` and `run-validator.sh` scripts
     - Example schema files in `schemas/` directory
     - README.md and SCHEMA_VALIDATION.md documentation
   - Extract and run: `./run-proxy.sh [options]`

3. **`checksums.txt`**
   - SHA256 checksums for integrity verification
   - Usage: `sha256sum -c checksums.txt`

## Workflow Features

### Build Optimization
- **Caching:** Maven dependencies are cached between runs
- **Parallel jobs:** Build, release, and schema testing run concurrently when possible
- **Artifact retention:** Build artifacts kept for 30 days (7 days for debug)

### Quality Assurance  
- **Automated testing:** Both applications tested with help commands
- **Script validation:** Launcher scripts tested for basic functionality
- **Schema validation:** Example schemas tested with sample data
- **Size verification:** JAR file sizes reported for monitoring

### Security
- **Minimal permissions:** Only `contents: write` for releases
- **Scoped tokens:** GitHub token only used where needed
- **Checksum verification:** SHA256 hashes provided for all assets

## Dependabot Configuration

`.github/dependabot.yml` configures automatic dependency updates:

- **Maven dependencies:** Updated weekly on Mondays
- **GitHub Actions:** Updated weekly on Mondays  
- **Pull request limits:** Max 5 Maven, 3 GitHub Actions PRs
- **Auto-assignment:** PRs assigned to repository owner

## Monitoring

### Status Badges

The README includes badges showing:
- CI workflow status
- Release workflow status

### Notifications

GitHub will notify on:
- Failed workflows (via email/web notifications)
- Dependabot PR creation
- Successful releases

## Troubleshooting

### Common Issues

1. **Build fails on Java version**
   - Workflows use Java 11 (Temurin distribution)
   - Update `JAVA_VERSION` environment variable if needed

2. **Release creation fails**
   - Check GitHub token permissions
   - Verify tag format (`v*` pattern required)
   - Check for duplicate version numbers

3. **Schema validation fails**
   - Verify schema files are valid JSON Schema
   - Check example log format in test job
   - Ensure SchemaValidator class is accessible

4. **Asset upload fails**
   - Check file paths in workflow
   - Verify Maven build produces expected artifacts
   - Check file size limits (2GB per asset)

### Manual Intervention

If workflows fail repeatedly:

1. Check the Actions tab for detailed logs
2. Review recent commits for breaking changes
3. Test locally with same Java version (11)
4. Create manual release if urgent (see RELEASE.md)

## Future Enhancements

Potential workflow improvements:

- **Security scanning:** Add CodeQL or dependency vulnerability checks
- **Performance testing:** Automated proxy performance benchmarks
- **Multi-platform builds:** Build on Windows/macOS for native launchers
- **Docker images:** Create and publish Docker containers
- **Deployment automation:** Auto-deploy to staging environments