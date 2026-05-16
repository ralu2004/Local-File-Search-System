# Changelog

All notable changes to this project will be documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Pre-commit hook for checkstyle enforcement
- CHANGELOG.md to track iteration 3 progress

## [2.0.0] - 2026-04-09

### Added
- Ranking: query parser supports path: and content: qualifiers
- Ranking: swappable strategies (alphabetical, date, static, behavior)
- Ranking: behavior-based ranking using search and open history (Observer pattern)
- Ranking: query suggestions and recent queries from history
- Ranking: result open tracking with position signals