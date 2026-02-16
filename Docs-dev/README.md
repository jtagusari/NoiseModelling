# Developer Documentation

This directory contains technical documentation for NoiseModelling developers and contributors.

## Contents

### Getting Started

- **[Setup and Build Guide](build-setup-guide.md)** - Complete guide for environment setup, building the project with Maven, and troubleshooting common issues (Java SE 11)
- **[POM Structure Guide](pom-structure-guide.md)** - Comprehensive guide to Maven POM files, dependency management, and multi-module architecture

### Architecture & Design

- **[Computation Scheme](computation_scheme.md)** - End-to-end computation flow and phases
- **[Input Data Schema](input_data_schema.md)** - Required input tables and parameters
- **[Source Algorithms](source_algorithms.md)** - Source data handling and emission algorithms
- **[Receiver Generation Algorithms](receiver_generation_algorithms.md)** - Receiver generation workflows and constraints
- **[Receiver Algorithms](receiver_algorithms.md)** - Receiver processing and evaluation pipeline
- **[NoiseMapByReceiverMaker Algorithms](noisemapbyreceivermaker_algorithms.md)** - Orchestration logic for grid-based computation
- **[Scene](scene.md)** - Runtime container for sources, receivers, and profile builder
- **[Pathfinder Algorithms](pathfinder_algorithms.md)** - Path finding algorithms for sound propagation
- **[Propagation Algorithms](propagation_algorithms.md)** - Sound propagation algorithms
- **[Attenuation Algorithms](attenuation_algorithms.md)** - Attenuation calculation algorithms

### Validation & Testing

- **[Case Study: Edge Cases Analysis](case_study.md)** - Comprehensive testing of edge cases and special scenarios:
  - Case 1: Receivers below and at ground level
  - Case 2: Indoor and boundary receivers
  - Case 3: Zero-distance source-receiver positions
  - Case 4: Single building obstruction and diffraction
  - Case 5: Multiple adjacent buildings with cumulative attenuation
  - Case 6: Overlapping building geometries and stacked diffraction
