---
title: Approved Milestones
description: The structured roadmap and definition of done for successive versions of the Dice Chess Engine.
---

Assign tasks to these milestones logically. Each milestone must be fully tested (including performance benchmarks) before moving to the next.

---

### ✅ v0.1 - Foundation & Core Types
* **Status**: Completed 🏆
* **Scope**: Project setup (SBT 1.x / Scala 3), configuration, `mise` setup.
* **Key Deliverables**:
  * Implementation of basic Opaque Types (`Bitboard`, `Square`, `Piece`, `Color`).
  * Basic FEN parsing and serialization.

### ⚔️ v0.2 - Move Generation (Classic)
* **Scope**: Fast legal and pseudo-legal move computation.
* **Key Deliverables**:
  * Bitwise operations and precomputed attack tables (Magic Bitboards).
  * Pawn, knight, king, and sliding piece move generation.
  * Perft (Performance Test) framework integration to verify move correctness.

### 🎲 v0.3 - Dice Chess Mechanics
* **Scope**: Integrating physical probability layers of Dice Chess.
* **Key Deliverables**:
  * Dice roll representations.
  * Filtering pseudo-legal moves based on dice outcomes.
  * Game state management with random events.

### 🧠 v0.4 - Evaluation & Heuristics
* **Scope**: Understanding board values statically and efficiently caching calculations.
* **Key Deliverables**:
  * Static evaluation function (Material balance, Piece-Square Tables).
  * Zobrist Hashing and Transposition Tables (TT) for caching board states.

### 🔭 v0.5 - Expectimax Search Engine
* **Scope**: Main multi-threaded search algorithm.
* **Key Deliverables**:
  * Core search algorithm implementation.
  * Integration of Virtual Threads (`Ox`) for parallelizing probability branches.
  * Mathematical expectation calculations.

### 🔌 v0.6 - WebSocket API
* **Scope**: Serving engine moves over the network.
* **Key Deliverables**:
  * Integration of `Http4s` (or `Cask`).
  * Implementing the command protocol (`start_search`, `stop`, `info`, `bestmove`).
  * Structured concurrency for search cancellation.

### 🚀 v1.0 - Production & Native Image
* **Scope**: Deployment optimization and infrastructure operations.
* **Key Deliverables**:
  * GraalVM Native Image compilation.
  * Dockerfile optimization.
  * CI/CD pipelines.
  * Deployment configurations for Homelab / Oracle Cloud.
