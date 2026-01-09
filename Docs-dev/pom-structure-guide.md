# NoiseModelling POM Structure Guide

This guide explains the Maven Project Object Model (POM) structure in the NoiseModelling project, how it manages dependencies, and how the multi-module architecture is organized.

## Table of Contents

1. [Overview](#overview)
2. [Multi-Module Architecture](#multi-module-architecture)
3. [Parent POM](#parent-pom)
4. [Module POMs](#module-poms)
5. [Dependency Management](#dependency-management)
6. [Build Configuration](#build-configuration)
7. [Best Practices](#best-practices)

---

## Overview

### What is a POM?

A POM (Project Object Model) is an XML file that contains information about the project and configuration details used by Maven to build the project. It contains:

- Project dependencies (external libraries)
- Build plugins and configurations
- Project metadata (name, version, description)
- Module definitions
- Build profiles

### Why Multiple POMs?

NoiseModelling uses a **multi-module Maven project** structure, which means:
- One **parent POM** at the root level manages common configurations
- Multiple **module POMs** (one per module) define module-specific dependencies
- This structure promotes code reuse, consistent versioning, and centralized dependency management

### Project Structure

```
NoiseModelling/
├── pom.xml                              # Parent POM
├── noisemodelling-emission/
│   └── pom.xml                          # Emission module POM
├── noisemodelling-pathfinder/
│   └── pom.xml                          # Pathfinder module POM
├── noisemodelling-propagation/
│   └── pom.xml                          # Propagation module POM
├── noisemodelling-jdbc/
│   └── pom.xml                          # JDBC module POM
└── noisemodelling-tutorial-01/
    └── pom.xml                          # Tutorial module POM
```

---

## Multi-Module Architecture

### Module Organization

NoiseModelling is divided into five main modules:

| Module | Purpose | Key Functionality |
|--------|---------|-------------------|
| **noisemodelling-emission** | Sound emission calculations | Calculate sound power levels from different sources |
| **noisemodelling-pathfinder** | Sound propagation path finding | Find paths between sources and receivers |
| **noisemodelling-propagation** | Sound propagation calculations | Compute sound attenuation and propagation |
| **noisemodelling-jdbc** | Database integration | H2GIS/PostGIS database operations |
| **noisemodelling-tutorial-01** | Tutorial and examples | Sample code and use cases |

### Module Dependencies

The modules have dependencies on each other:

```
noisemodelling-propagation
    ├── depends on → noisemodelling-pathfinder
    └── depends on → noisemodelling-emission

noisemodelling-jdbc
    └── depends on → noisemodelling-propagation

noisemodelling-tutorial-01
    └── depends on → noisemodelling-jdbc
```

This dependency hierarchy ensures:
- Low-level modules (emission, pathfinder) have no internal dependencies
- Higher-level modules (propagation, jdbc) build upon lower-level functionality
- Changes in parent modules cascade appropriately to dependent modules

---

## Parent POM

### Location and Basic Information

**File**: `NoiseModelling/pom.xml`

The parent POM serves as the central configuration point for the entire project.

### Key Sections

#### 1. Project Metadata

```xml
<groupId>org.orbisgis</groupId>
<artifactId>noisemodelling-parent</artifactId>
<version>5.0.1-SNAPSHOT</version>
<packaging>pom</packaging>
```

- **groupId**: Organization identifier (org.orbisgis)
- **artifactId**: Parent project identifier
- **version**: Current project version (5.0.1-SNAPSHOT indicates development version)
- **packaging**: `pom` indicates this is a parent/aggregator project

#### 2. Module Declaration

```xml
<modules>
    <module>noisemodelling-emission</module>
    <module>noisemodelling-pathfinder</module>
    <module>noisemodelling-propagation</module>
    <module>noisemodelling-jdbc</module>
    <module>noisemodelling-tutorial-01</module>
</modules>
```

This tells Maven which modules are part of the project and their build order.

#### 3. Properties

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <!-- Additional properties -->
</properties>
```

Defines variables used throughout the POM files.

#### 4. Dependency Management

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <version>5.11.3</version>
            <scope>test</scope>
        </dependency>
        <!-- More dependencies -->
    </dependencies>
</dependencyManagement>
```

**Purpose**: 
- Centrally defines **versions** of dependencies
- Child modules can reference these dependencies **without specifying versions**
- Ensures version consistency across all modules
- Does **not** automatically add dependencies to child modules

#### 5. Build Configuration

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.8.0</version>
            <configuration>
                <source>11</source>
                <target>11</target>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Configures the Java compiler to use Java 11 for all modules.

---

## Module POMs

Each module has its own POM that inherits from the parent POM.

### Example: noisemodelling-propagation

**File**: `noisemodelling-propagation/pom.xml`

#### 1. Parent Reference

```xml
<parent>
    <groupId>org.orbisgis</groupId>
    <artifactId>noisemodelling-parent</artifactId>
    <version>5.0.1-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>
```

This links the module to the parent POM and inherits its configuration.

#### 2. Module Identity

```xml
<artifactId>noisemodelling-propagation</artifactId>
<name>noisemodelling-propagation</name>
<packaging>bundle</packaging>
```

- Inherits `groupId` and `version` from parent
- Defines its own `artifactId` and `name`
- `packaging: bundle` creates an OSGi bundle

#### 3. Dependencies

```xml
<dependencies>
    <!-- External dependencies -->
    <dependency>
        <groupId>org.locationtech.jts</groupId>
        <artifactId>jts-core</artifactId>
    </dependency>
    
    <!-- Internal module dependencies -->
    <dependency>
        <groupId>${project.groupId}</groupId>
        <artifactId>noisemodelling-pathfinder</artifactId>
        <version>${project.version}</version>
    </dependency>
    
    <!-- Test dependencies -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-api</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Key points**:
- External dependencies are inherited from parent's `dependencyManagement`
- No version specified for managed dependencies (version comes from parent)
- Internal dependencies use `${project.groupId}` and `${project.version}` variables
- Test dependencies use `scope>test</scope>` to limit them to test phase

---

## Dependency Management

### How Dependency Management Works

```
Parent POM (defines versions)
    ↓
Child POM (uses dependencies without versions)
    ↓
Maven resolves actual versions from parent
```

### Example Flow

#### Parent POM defines version:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <version>5.11.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### Child POM uses dependency (no version):
```xml
<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-api</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Result**: Child module gets JUnit Jupiter API version 5.11.3

### Benefits

1. **Version Consistency**: All modules use the same version of each dependency
2. **Easy Updates**: Change version once in parent POM, affects all modules
3. **Reduced Duplication**: No need to specify versions in every module
4. **Conflict Prevention**: Prevents different modules from using incompatible versions

### Dependency Scopes

| Scope | Description | When Available |
|-------|-------------|----------------|
| `compile` (default) | Required for compilation and runtime | All phases |
| `test` | Only needed for testing | Test compilation and execution |
| `provided` | Available at compile time, provided by runtime environment | Compile and test, not packaged |
| `runtime` | Not needed for compilation, only for execution | Runtime and test |

---

## Build Configuration

### Compiler Configuration

All modules inherit Java 11 compiler configuration from parent POM:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.8.0</version>
    <configuration>
        <source>11</source>
        <target>11</target>
    </configuration>
</plugin>
```

This ensures:
- Source code is compiled as Java 11
- Generated bytecode is compatible with Java 11+
- Consistent language features across all modules

### OSGi Bundle Plugin

Some modules use the Felix Bundle Plugin to create OSGi bundles:

```xml
<plugin>
    <groupId>org.apache.felix</groupId>
    <artifactId>maven-bundle-plugin</artifactId>
    <extensions>true</extensions>
    <configuration>
        <instructions>
            <Bundle-Name>${project.name}</Bundle-Name>
            <Bundle-SymbolicName>${project.groupId}.${project.artifactId}</Bundle-SymbolicName>
        </instructions>
    </configuration>
</plugin>
```

This allows the modules to be used in OSGi environments.

### Test Execution

Maven Surefire Plugin (configured by default) handles test execution:

```bash
mvn test                              # Run all tests in all modules
mvn -pl noisemodelling-propagation test  # Run tests in specific module
mvn test -Dtest=SpecificTest         # Run specific test class
```

---

## Best Practices

### 1. Adding a New Dependency

#### To add a dependency used by multiple modules:

**Step 1**: Add to parent POM's `dependencyManagement`:
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>example-library</artifactId>
    <version>1.2.3</version>
</dependency>
```

**Step 2**: Add to module POM's `dependencies` (without version):
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>example-library</artifactId>
</dependency>
```

#### To add a dependency used by only one module:

Add directly to module POM's `dependencies` with version:
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>example-library</artifactId>
    <version>1.2.3</version>
</dependency>
```

### 2. Dependency Scope Selection

- Use `test` scope for testing libraries (JUnit, Mockito, etc.)
- Use `compile` scope (default) for libraries needed at runtime
- Use `provided` scope for APIs provided by the container (Servlet API, etc.)

### 3. Version Management

- Define all versions in parent POM's `dependencyManagement`
- Use properties for version numbers that appear multiple times:
  ```xml
  <properties>
      <junit.version>5.11.3</junit.version>
  </properties>
  
  <dependency>
      <artifactId>junit-jupiter-api</artifactId>
      <version>${junit.version}</version>
  </dependency>
  ```

### 4. Module Dependency Order

When one module depends on another:
```xml
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>noisemodelling-pathfinder</artifactId>
    <version>${project.version}</version>
</dependency>
```

- Use `${project.groupId}` instead of hardcoding "org.orbisgis"
- Use `${project.version}` to automatically match the current version
- Maven builds modules in correct order based on dependencies

### 5. Keeping POMs Clean

- Don't duplicate information from parent POM
- Don't specify versions for managed dependencies
- Group related dependencies with comments
- Remove unused dependencies regularly

---

## Common Operations

### Building the Entire Project

```bash
# Clean and build all modules
mvn clean install

# Build without running tests (faster)
mvn clean install -DskipTests

# Build specific module and its dependencies
mvn -pl noisemodelling-propagation -am clean install
```

### Updating Dependencies

```bash
# Check for dependency updates
mvn versions:display-dependency-updates

# Check for plugin updates
mvn versions:display-plugin-updates

# Update parent and all modules to new version
mvn versions:set -DnewVersion=5.0.2-SNAPSHOT
```

### Analyzing Dependencies

```bash
# Show dependency tree
mvn dependency:tree

# Show dependency tree for specific module
mvn -pl noisemodelling-propagation dependency:tree

# Analyze dependency conflicts
mvn dependency:analyze
```

---

## POM File Organization

### Recommended Section Order

For consistency and readability, organize POM sections in this order:

1. XML declaration and project element
2. Parent reference (module POMs only)
3. Project coordinates (groupId, artifactId, version, packaging)
4. Project information (name, description, organization, url)
5. Properties
6. Dependency Management (parent POM only)
7. Dependencies
8. Build configuration
9. Profiles (if any)
10. Repositories (if any)

### Example Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <modelVersion>4.0.0</modelVersion>
    
    <!-- Parent -->
    <parent>...</parent>
    
    <!-- Coordinates -->
    <artifactId>...</artifactId>
    <packaging>...</packaging>
    
    <!-- Information -->
    <name>...</name>
    <description>...</description>
    
    <!-- Properties -->
    <properties>...</properties>
    
    <!-- Dependencies -->
    <dependencies>...</dependencies>
    
    <!-- Build -->
    <build>...</build>
</project>
```

---

## Troubleshooting

### Common POM Issues

#### 1. Missing Version Error

**Error**:
```
'dependencies.dependency.version' for xxx:yyy:jar is missing
```

**Solution**: Add the dependency to parent POM's `dependencyManagement` section with a version.

#### 2. Circular Dependency

**Error**:
```
The projects in the reactor contain a cyclic reference
```

**Solution**: Check module dependencies - no module should depend on another that depends back on it.

#### 3. Dependency Conflict

**Error**:
```
Dependency convergence error for xxx
```

**Solution**: Use `mvn dependency:tree` to identify conflicts, then use `<exclusions>` to exclude conflicting versions:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>example-lib</artifactId>
    <exclusions>
        <exclusion>
            <groupId>conflicting.group</groupId>
            <artifactId>conflicting-artifact</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

#### 4. Wrong Java Version

**Error**:
```
Source option 11 is no longer supported. Use 17 or later.
```

**Solution**: Verify `maven-compiler-plugin` configuration in parent POM matches your Java version.

---

## Real-World Example: Adding JUnit 5 Parameterized Tests

This example demonstrates how dependency management works in practice.

### Problem

The `noisemodelling-propagation` module needed JUnit 5's parameterized test feature, which requires the `junit-jupiter-params` dependency.

### Solution

**Step 1**: Add version definition to parent POM (`NoiseModelling/pom.xml`):

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-params</artifactId>
            <version>5.11.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Step 2**: Add dependency to module POM (`noisemodelling-propagation/pom.xml`):

```xml
<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-params</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Result**: The module can now use parameterized tests, and if other modules need this dependency later, they'll automatically use the same version.

---

## References

- [Maven POM Reference](https://maven.apache.org/pom.html)
- [Maven Dependency Mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
- [Maven Multi-Module Projects](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
- [Maven Build Lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)
- [Maven Best Practices](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html)
