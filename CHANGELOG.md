# Changelog

All notable changes to this project will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Pre-commit hook for checkstyle enforcement
- CHANGELOG.md to track iteration 3 progress
- Multimodal search: image indexing with dominant color extraction (Strategy Pattern)
- color: query filter for searching images by dominant color
- Producer-Consumer indexing: parallel file extraction via reader thread pool with dedicated IndexWriter consuming results and committing to SQLite
- IndexWriter: extracted as standalone consumer class responsible for batch writes and writer thread lifecycle
- Tests: parallel indexing correctness and image color filter integration tests
- Context-aware widgets: gallery view, export file list, copy folder path, and informational markers
- Secure localfile:// Electron protocol for loading local images in gallery view
- Tests: widget activation integration tests for gallery, export, folder path, and content marker
- Query Pre-Processor Pipeline: SanitizationDecorator, SynonymDecorator, and LogicDecorator chain operating on parsed Query objects after QueryParser

### Changed
- SearchResponse moved from app.model to ApiServer as an API transport record
- QueryBuilder.buildFtsMatchString simplified — FTS normalization moved to LogicDecorator
- FileTypes Javadoc updated to reflect image extensions and isIndexable

## [2.0.0] - 2026-04-09

### Added
- Ranking: query parser supports path: and content: qualifiers
- Ranking: swappable strategies (alphabetical, date, static, behavior)
- Ranking: behavior-based ranking using search and open history (Observer pattern)
- Ranking: query suggestions and recent queries from history
- Ranking: result open tracking with position signals