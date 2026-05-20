# NoiseModelling - Development Environment Setup & Build Guide

This guide covers environment setup, building the project with Maven, and troubleshooting common build issues, specifically for Java SE 11 environments.

## Table of Contents

1. [Environment Setup](#environment-setup)
2. [Building the Project](#building-the-project)
3. [Maven Basic Commands](#maven-basic-commands)
4. [Troubleshooting](#troubleshooting)
5. [References](#references)

---

## Environment Setup

### 1. Java Development Kit (JDK) Installation

**NoiseModelling requires Java SE 11 (JDK 11) to build and run.**

#### Check Current Java Version

**NoiseModelling requires Java SE 11 (JDK 11) to build and run.**

```bash
java -version
```

**Expected output:**
```
openjdk version "11.0.x"
OpenJDK Runtime Environment
OpenJDK 64-Bit Server VM
```

#### Install Java 11

If you don't have Java 11, download and install from:
- **OpenJDK 11** (Recommended): https://adoptium.net/temurin/releases/?version=11
- **Oracle JDK 11**: https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html

#### Set JAVA_HOME Environment Variable

**Windows:**

```powershell
# Set permanently (requires restart or new terminal)
[System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Java\jdk-11.0.x', 'Machine')

# Set for current session
$env:JAVA_HOME='C:\Program Files\Java\jdk-11.0.x'
```

**Linux/Mac:**

```bash
# Add to ~/.bashrc or ~/.zshrc
export JAVA_HOME=/path/to/jdk-11
export PATH=$JAVA_HOME/bin:$PATH

# Apply changes
source ~/.bashrc  # or source ~/.zshrc
```

#### Verify Installation

```bash
echo $JAVA_HOME  # Should show your JDK 11 path
java -version    # Should show version 11.0.x
```

### 2. Apache Maven Installation

Check if Maven is installed:
```bash
mvn --version
```

If not installed, download from: https://maven.apache.org/download.cgi

**Minimum version**: Maven 3.6.0 or higher (Maven 3.9.x recommended)

### 3. Git (Optional)

For version control and cloning the repository:
```bash
git --version
```

Download from: https://git-scm.com/downloads

---

## Building the Project

### First-Time Setup

**Important for Java 11 users**: Use project-local Maven JVM options to avoid intermittent TLS handshake issues (`peer not authenticated`, `No PSK available. Unable to resume.`).

Create `.mvn/jvm.config` in the repository root:

```text
-Dhttps.protocols=TLSv1.2
-Djdk.tls.client.protocols=TLSv1.2
```

This keeps the workaround local to this repository and avoids weakening SSL certificate validation globally.

Legacy fallback (only if needed in restricted network environments):

**Windows PowerShell:**
```powershell
$env:MAVEN_OPTS='-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dhttps.protocols=TLSv1.2'
```

**Linux/Mac:**
```bash
export MAVEN_OPTS="-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dhttps.protocols=TLSv1.2"
```

### Quick Start Build

Navigate to the project root directory and run:

```bash
# Clean and build all modules (skip tests for faster build)
mvn clean install -DskipTests
```

**Expected build time**: 1-2 minutes on first run (downloading dependencies), ~1 minute for subsequent builds.

**Successful build output:**
```
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary:
[INFO] 
[INFO] noisemodelling-parent .............................. SUCCESS
[INFO] noisemodelling-emission ............................ SUCCESS
[INFO] noisemodelling-pathfinder .......................... SUCCESS
[INFO] noisemodelling-propagation ......................... SUCCESS
[INFO] noisemodelling-jdbc ................................ SUCCESS
[INFO] noisemodelling-tutorial-01 ......................... SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Build with Tests

To run all tests:
```bash
mvn clean install
```

### Project Structure

After a successful build, you'll find compiled artifacts in each module's `target` directory:

```
NoiseModelling/
├── noisemodelling-emission/target/
├── noisemodelling-pathfinder/target/
├── noisemodelling-propagation/target/
├── noisemodelling-jdbc/target/
└── noisemodelling-tutorial-01/target/
```

---

## Maven Basic Commands

### Common Maven Operations

#### 1. Clean Build Output

Remove previously compiled files and build artifacts:

```bash
mvn clean
```

#### 2. Compile Source Code

Compile the project source code without creating packages:

```bash
mvn compile
```

For multi-module projects, compile all modules:

```bash
mvn clean compile
```

#### 3. Package (Create JAR/Bundle)

Compile and package the project into JAR files:

```bash
mvn package
```

Skip tests during packaging:

```bash
mvn package -DskipTests
```

#### 4. Install to Local Repository

Install the built artifacts to your local Maven repository (`~/.m2/repository`):

```bash
mvn install
```

Skip tests during installation:

```bash
mvn install -DskipTests
```

Clean and install (recommended for fresh builds):

```bash
mvn clean install -DskipTests
```

#### 5. Run Tests

Run all tests in the project:

```bash
mvn test
```

Run tests for a specific module:

```bash
mvn -pl noisemodelling-propagation test
```

Run a specific test class:

```bash
mvn test -Dtest=AttenuationComputeOutputCnossosBridgeTest
```

Run a specific test method:

```bash
mvn test -Dtest=AttenuationComputeOutputCnossosBridgeTest#testMethod
```

Run tests for a specific module:

```bash
mvn -pl noisemodelling-propagation test -Dtest=AttenuationComputeOutputCnossosBridgeTest
```

#### 6. Skip Tests

You can skip test execution with any Maven command:

```bash
mvn clean install -DskipTests          # Compile tests but don't run them
mvn clean install -Dmaven.test.skip=true  # Don't compile or run tests
```

#### 7. Build Specific Modules

Build only a specific module in a multi-module project:

```bash
mvn -pl noisemodelling-emission clean install
```

Build a module and its dependencies:

```bash
mvn -pl noisemodelling-jdbc -am clean install
```

Build multiple modules:

```bash
mvn -pl noisemodelling-emission,noisemodelling-pathfinder clean install
```

#### 8. Debug and Verbose Output

Run Maven with debug output:

```bash
mvn clean install -X
```

Show version information:

```bash
mvn --version
```

#### 9. Dependency Management

Display dependency tree:

```bash
mvn dependency:tree
```

Download dependencies without building:

```bash
mvn dependency:resolve
```

### Development Workflow

**First-time setup or after major changes:**
```bash
# Configure project-local TLS settings for Java 11 (one-time)
# Create .mvn/jvm.config with:
# -Dhttps.protocols=TLSv1.2
# -Djdk.tls.client.protocols=TLSv1.2

# Clean build and install all modules
mvn clean install -DskipTests
```

**Daily development workflow:**
```bash
# Build specific module you're working on
mvn -pl noisemodelling-propagation clean install -DskipTests

# Run tests for your module
mvn -pl noisemodelling-propagation test
```

**Before committing changes:**
```bash
# Run full build with all tests
mvn clean install
```

### Maven Command Options Reference

| Option | Description |
|--------|-------------|
| `clean` | Delete the `target` directory |
| `compile` | Compile source code |
| `test` | Run unit tests |
| `package` | Create JAR/Bundle files |
| `install` | Install to local repository |
| `deploy` | Deploy to remote repository |
| `-DskipTests` | Compile tests but don't run them |
| `-Dmaven.test.skip=true` | Don't compile or run tests |
| `-pl <module>` | Build specific module(s) |
| `-am` | Build dependencies of specified modules |
| `-X` | Debug output |
| `-e` | Show full error stack traces |
| `-U` | Force update of snapshots/releases |
| `-o` | Offline mode (use local repository only) |

---

## Troubleshooting

### Common Build Issues

#### SSL/TLS Connection Errors

**Problem Description:**

When building the project with Maven, you may encounter SSL/TLS-related errors at various stages. These errors prevent Maven from downloading required dependencies and plugins.

#### Error Type 1: Bundle Plugin Resolution

```
[ERROR] Unresolveable build extension: Plugin org.apache.felix:maven-bundle-plugin:5.1.1 or one of its dependencies could not be resolved
[ERROR]     Could not transfer artifact org.codehaus.plexus:plexus-classworlds:jar:2.5.2 from/to central (https://repo1.maven.org/maven2): No PSK available. Unable to resume.
[ERROR]     Could not transfer artifact org.codehaus.plexus:plexus-component-annotations:jar:1.6 from/to central (https://repo1.maven.org/maven2): peer not authenticated
[ERROR] Unknown packaging: bundle @ line 7, column 16
```

#### Error Type 2: Buildnumber Plugin Resolution

```
[ERROR] Failed to execute goal org.codehaus.mojo:buildnumber-maven-plugin:3.0.0:create (default) on project noisemodelling-parent: 
[ERROR] Execution default of goal org.codehaus.mojo:buildnumber-maven-plugin:3.0.0:create failed: 
[ERROR] Plugin org.codehaus.mojo:buildnumber-maven-plugin:3.0.0 or one of its dependencies could not be resolved:
[ERROR]     Could not transfer artifact org.codehaus.plexus:plexus-classworlds:jar:2.2.3 from/to central (https://repo1.maven.org/maven2): No PSK available. Unable to resume.
[ERROR]     Could not transfer artifact org.codehaus.plexus:plexus-component-annotations:jar:1.5.5 from/to central (https://repo1.maven.org/maven2): No PSK available. Unable to resume.
[ERROR]     Could not transfer artifact org.codehaus.plexus:plexus-container-default:jar:1.0-alpha-9 from/to central (https://repo1.maven.org/maven2): peer not authenticated
```

#### Error Type 3: Generic Dependency Download

```
[ERROR] Could not transfer artifact ... from/to central (https://repo1.maven.org/maven2): 
No PSK available. Unable to resume.
```

Or:

```
[ERROR] Could not transfer artifact ... from/to central (https://repo1.maven.org/maven2): 
peer not authenticated
```

#### Commonly Affected Artifacts

These artifacts frequently fail to download due to SSL/TLS issues:
- `org.codehaus.plexus:plexus-classworlds` (versions: 2.2.3, 2.5.2, 2.6.0)
- `org.codehaus.plexus:plexus-component-annotations` (versions: 1.5.5, 1.6)
- `org.codehaus.plexus:plexus-container-default:1.0-alpha-9`
- `org.codehaus.plexus:plexus-archiver:4.2.0`
- `org.codehaus.plexus:plexus-io:3.2.0`
- `org.codehaus.plexus:plexus-utils` (various versions)
- `org.osgi:org.osgi.core:6.0.0`

**Root Cause:**

These errors occur due to TLS/SSL handshake failures between Maven and Maven Central repository. This is **particularly common with Java SE 11**. Main causes:

1. **Java 11 SSL/TLS Compatibility Issues**: Known TLS handshake problems in certain environments
2. **Certificate Validation Failures**: Outdated cacerts or missing intermediate certificates
3. **Corporate Proxy/Firewall**: Network security policies blocking/modifying SSL connections
4. **Maven Wagon HTTP Provider**: Compatibility issues with Java 11's SSL implementation
5. **Corrupted Cache**: Incomplete downloads from network interruptions

The problem can occur at different build stages:
- **POM Processing**: Resolving build extensions (e.g., maven-bundle-plugin)
- **Plugin Resolution**: Downloading plugin dependencies (e.g., buildnumber-maven-plugin)
- **Dependency Download**: Downloading project dependencies

**Solutions:**

##### Solution 1: Use Project-Local TLSv1.2 in `.mvn/jvm.config` (Recommended)

**Tested and working with Java 11.0.2 in this repository.**

Create `.mvn/jvm.config` at the repository root:

```text
-Dhttps.protocols=TLSv1.2
-Djdk.tls.client.protocols=TLSv1.2
```

Then run:

```bash
mvn clean install -DskipTests
```

##### Solution 1b: Use MAVEN_OPTS with SSL Bypass (Last Resort)

**Tested and working solution for Java SE 11 environments.**

**Windows PowerShell:**

```powershell
# Set environment variable with enhanced SSL bypass flags
$env:MAVEN_OPTS='-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dhttps.protocols=TLSv1.2'

# Build the project
mvn clean compile
# OR for full build with tests skipped
mvn clean install -DskipTests
```

**Linux/Mac Bash:**

```bash
# Set environment variable with enhanced SSL bypass flags
export MAVEN_OPTS="-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dhttps.protocols=TLSv1.2"

# Build the project
mvn clean compile
# OR for full build with tests skipped
mvn clean install -DskipTests
```

**Key flags explained:**
- `-Dmaven.wagon.http.ssl.insecure=true`: Disables SSL certificate validation
- `-Dmaven.wagon.http.ssl.allowall=true`: Allows all SSL certificates
- `-Dmaven.wagon.http.ssl.ignore.validity.dates=true`: Ignores certificate expiration dates
- `-Dhttps.protocols=TLSv1.2`: Forces TLS 1.2 protocol (better compatibility with Java 11)

##### Basic Configuration (May work in some environments)

If the enhanced configuration above has issues, try the basic configuration:

**Windows PowerShell:**

```powershell
$env:MAVEN_OPTS='-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true'
mvn clean install -DskipTests
```

**Linux/Mac Bash:**

```bash
export MAVEN_OPTS="-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true"
mvn clean install -DskipTests
```

##### Make it permanent (Recommended to avoid typing every time):

To avoid entering MAVEN_OPTS every time, set it permanently using one of the following methods:

**Method 1: Add to PowerShell Profile (Windows - Recommended)**

This method automatically sets MAVEN_OPTS every time you open PowerShell:

```powershell
# 1. Open PowerShell profile file (will be created if it doesn't exist)
notepad $PROFILE

# 2. Add the following line and save:
$env:MAVEN_OPTS='-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dhttps.protocols=TLSv1.2'

# 3. Restart PowerShell or reload the profile with:
. $PROFILE

# 4. Verify:
echo $env:MAVEN_OPTS
```

**Note**: If you get an execution policy error on first run, execute:
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

**Method 2: Set as System Environment Variable (Windows - Persistent)**

```powershell
# Set as user environment variable (no admin rights required)
[System.Environment]::SetEnvironmentVariable('MAVEN_OPTS', '-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dhttps.protocols=TLSv1.2', 'User')

# Or set system-wide (requires admin rights)
[System.Environment]::SetEnvironmentVariable('MAVEN_OPTS', '-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dhttps.protocols=TLSv1.2', 'Machine')

# After restarting PowerShell, verify:
echo $env:MAVEN_OPTS
```

**Method 3: Set via GUI (Windows - Beginner-friendly)**

1. Press Windows key and search for "environment variables"
2. Open "Edit environment variables"
3. In "User variables" section, click "New"
4. Variable name: `MAVEN_OPTS`
5. Variable value: `-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dhttps.protocols=TLSv1.2`
6. Click "OK" on all windows
7. Restart PowerShell

**Linux/Mac** - Add to `~/.bashrc` or `~/.zshrc`:
```bash
export MAVEN_OPTS="-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dhttps.protocols=TLSv1.2"

# Reload
source ~/.bashrc  # or source ~/.zshrc
```

**Verify the configuration:**

```powershell
# Windows PowerShell
echo $env:MAVEN_OPTS

# Linux/Mac
echo $MAVEN_OPTS
```

If configured correctly, you should see output like:
```
-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dhttps.protocols=TLSv1.2
```

**After configuration, simply run the build command:**

```bash
mvn clean install -DskipTests
```

#### Solution 2: Clear Maven Cache and Retry

If you encounter "peer not authenticated" or "No PSK available" errors, cached corrupted files may be the cause. This solution clears potentially corrupted artifacts from your local Maven repository.

**When to use this solution:**
- After network interruptions during previous builds
- When specific artifacts consistently fail to download
- After seeing "peer not authenticated" errors
- When switching between different network environments

```powershell
# Windows PowerShell - Clear problematic cached dependencies
Remove-Item -Recurse -Force "$env:USERPROFILE\.m2\repository\org\codehaus\plexus" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$env:USERPROFILE\.m2\repository\org\codehaus\mojo" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$env:USERPROFILE\.m2\repository\org\apache\felix" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$env:USERPROFILE\.m2\repository\org\osgi" -ErrorAction SilentlyContinue

# Then build with MAVEN_OPTS
$env:MAVEN_OPTS='-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true'
mvn clean install -DskipTests
```

```bash
# Linux/Mac - Clear problematic cached dependencies
rm -rf ~/.m2/repository/org/codehaus/plexus
rm -rf ~/.m2/repository/org/codehaus/mojo
rm -rf ~/.m2/repository/org/apache/felix
rm -rf ~/.m2/repository/org/osgi

# Then build with MAVEN_OPTS
export MAVEN_OPTS="-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true"
mvn clean install -DskipTests
```

**Note**: Always combine cache clearing with MAVEN_OPTS to ensure successful re-downloads.

##### Solution 2: Clear Maven Cache and Retry

Create a `~/.m2/settings.xml` file (Windows: `%USERPROFILE%\.m2\settings.xml`) with the following content:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
    
    <!-- Proxy settings (if needed) -->
    <!--
    <proxies>
        <proxy>
            <id>example-proxy</id>
            <active>true</active>
            <protocol>http</protocol>
            <host>proxy.example.com</host>
            <port>8080</port>
        </proxy>
    </proxies>
    -->
    
    <!-- Repository mirror settings (optional) -->
    <!--
    <mirrors>
        <mirror>
            <id>nexus</id>
            <mirrorOf>central</mirrorOf>
            <url>http://your-internal-repo/nexus/content/repositories/central/</url>
        </mirror>
    </mirrors>
    -->
</settings>
```

##### Solution 3: Maven Settings with Proxy/Mirror

Create `~/.m2/settings.xml` with appropriate proxy or mirror settings (see example above).

##### Solution 4: Update Java Certificates (Production)

If you want a more secure solution, update Java's certificate store:

```bash
# Download Maven Central certificate
# Then import it to Java keystore
keytool -import -trustcacerts -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit -alias maven-central -file maven-central.cer
```

For production environments, update Java certificates or use a newer Java version with current certificates.

#### Other Common Issues

##### Modules Not Found

If other project modules cannot be found, build them in this order:

```bash
# 1. Build parent project and all modules
mvn clean install -DskipTests

# 2. Run tests for specific module
mvn -pl noisemodelling-propagation test
```

#### Issue: Partial Download Failures

If some dependencies download successfully but others fail:

1. **Delete the failed artifact folder** from `~/.m2/repository`
2. **Retry the build** with SSL workarounds:
   ```bash
   $env:MAVEN_OPTS='-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true'
   mvn clean install -DskipTests
   ```

#### Issue: "peer not authenticated" Errors

This specific error indicates SSL handshake failure. Solutions:

1. **Set MAVEN_OPTS** (quickest fix):
   ```powershell
   $env:MAVEN_OPTS='-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true'
   ```

2. **Update Java** to a version with updated SSL/TLS support (Java 11+ recommended)

3. **Check proxy settings** if behind a corporate firewall

## Successful Build Verification

After applying the recommended MAVEN_OPTS configuration, a successful build should show:

```
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for noisemodelling-parent 5.0.1-SNAPSHOT:
[INFO]
[INFO] noisemodelling-parent .............................. SUCCESS [  2.557 s]
[INFO] noisemodelling-emission ............................ SUCCESS [ 16.764 s]
[INFO] noisemodelling-pathfinder .......................... SUCCESS [ 53.269 s]
[INFO] noisemodelling-propagation ......................... SUCCESS [  5.729 s]
[INFO] noisemodelling-jdbc ................................ SUCCESS [ 17.651 s]
[INFO] noisemodelling-tutorial-01 ......................... SUCCESS [ 14.135 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

**Expected build time:** Approximately 1-2 minutes for `mvn clean compile` on first build (longer on subsequent builds with tests).

### Compilation Warnings (Expected)

The following warnings are expected and do not prevent successful compilation:

```
[WARNING] Cannot get the branch information from the git repository
[WARNING] Ignoring incompatible plugin version 4.0.0-beta-1
```

These warnings indicate:
1. **Git repository warning**: Project is not in a git repository (safe to ignore)
2. **Plugin version warnings**: Some plugins require Java 17+ but the project uses Java 11 (Maven automatically selects compatible versions)

### Verification Steps

After successful compilation:

1. **Check compiled classes exist:**
   ```powershell
   Test-Path "noisemodelling-emission\target\classes"
   # Should return: True
   ```

2. **Run specific module tests:**
   ```bash
   mvn -pl noisemodelling-propagation test -Dtest=AttenuationComputeOutputCnossosBridgeTest
   ```

3. **Build without tests (faster):**
   ```bash
   mvn clean install -DskipTests
   ```

## Summary

For **Java SE 11** environments experiencing SSL/TLS issues with Maven Central:

1. ✅ **Use `.mvn/jvm.config` with TLSv1.2** (recommended, repository-local)
2. ✅ **Clear corrupted cache** if needed
3. ✅ **Use MAVEN_OPTS SSL bypass only as a last resort**
4. ✅ **Verify build success** with `BUILD SUCCESS` message

**Quick Start Command:**
```text
# .mvn/jvm.config
-Dhttps.protocols=TLSv1.2
-Djdk.tls.client.protocols=TLSv1.2
```

```powershell
# Windows PowerShell
mvn clean compile
```

```bash
# Linux/Mac
mvn clean compile
```

### References

- [Maven Settings Reference](https://maven.apache.org/settings.html)
- [Maven Security Best Practices](https://maven.apache.org/guides/mini/guide-repository-ssl.html)
- [Java 11 SSL/TLS Compatibility Issues](https://bugs.openjdk.java.net/browse/JDK-8236039)
