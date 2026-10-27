# Changelog

## Unreleased

### Reconstructed baseline

- Verified the project root, source tree, configuration, migration, and existing product API.
- Recorded that Git is not initialized.
- Reconstructed project context, implementation plan, ADR, and setup documentation from source.

### Current milestone

- Added opt-in, idempotent demo seed data for an empty product database.
- Added service tests for duplicate SKU rejection and stock state transitions.
- Added single-product order processing with idempotency protection and transactional stock decrement.
- Added immutable post-order inventory snapshots, retrieval API, Flyway V2 migration, and requirements traceability.
- Code review fixed the concurrent same-key retry path by rechecking idempotency after the product lock.
- Code review fixed replay response accuracy by persisting `orders.remaining_stock`.
- Added regression tests for both confirmed defects.
- Started pricing and reorder suggestions with a rule-based strategy, persisted lifecycle, duplicate-pending protection, and approval API.
- Added Flyway V3 and suggestion service/strategy tests.
- Refined the rule-based milestone to implement exact pricing priority, category-average demand comparison, confidence/direction/trigger fields, and the required reorder formula.
- Changed lifecycle terminology to `PENDING`, `ACCEPTED`, and `REJECTED`; accepted reorder suggestions simulate inbound stock.
- Static source review confirmed suggestion persistence, repository locking for generation/resolution, DTO boundaries, and transactional price approval.
- Updated setup, demo, testing, architecture, and next-action documentation.
- Added product-scoped order and suggestion aliases and replaced the read-only dashboard with live product, sale, generation, and approval workflows.
- Added configurable HTTP AI advisor integration with strict response validation and deterministic rule fallback.
- Added after-commit asynchronous inventory-change events for low-inventory and demand-spike recommendation generation.
- Added row locking for direct stock updates and pricing/reorder resolution route aliases.
- Refactored feature HTTP boundaries into `controller` and `dto` packages, moved CORS into `config`, and added a bounded async executor.
- Fixed the frontend API module so product/order/suggestion functions are real module exports rather than nested inside `getProducts`.
- Added generic sanitized API error handling for unexpected failures and constraint validation.
- Completed the backend physical refactor to layer-first packages: controllers, services, repositories, entities, request/response DTOs, mappers, exceptions, events, advisors, configuration, and seed data.
- Removed obsolete feature-local, `domain`, `persistence`, and `api` package directories and corrected all Java package/import references.
- Corrected request/response DTO package declarations to match their physical `dto.request` and `dto.response` directories, and extracted four explicit mapper classes.

### Verification

- `npm install`: passed; installed 133 packages and reported 0 vulnerabilities.
- `npm run build`: passed; TypeScript and Vite production build completed.
- `npm run lint`: initially failed because the repository had no ESLint 9 flat config; adding `frontend/eslint.config.js` resolved it.
- Final `npm run build`: passed.
- Final `npm run lint`: passed with no reported errors.
- `java -version`: reported OpenJDK runtime 21.0.12.
- `javac -version`: failed because `javac` is not on `PATH`.
- `mvn test` from the workspace parent: failed because no POM exists there.
- `mvn test` from `backend`: reached compilation but failed because no Java compiler was provided.
- Backend tests for the order/snapshot milestone were added but not executed successfully because the environment still has no JDK compiler.
- Final `mvn test` after review fixes: blocked during compilation with `No compiler is provided in this environment`.
- Verification retry on 2026-10-27: `java -version` reported OpenJDK 21.0.12; `javac -version` was unavailable; `mvn -version` reported Maven 3.9.16 running on `C:\Program Files\OpenLogic\jre-21.0.12.8-hotspot`.
- A read-only directory check found no installed JDK directory, only the OpenLogic JRE. No software or environment setting was changed.
- Source review confirmed product locking, transaction boundaries, unique idempotency constraint, validation/schema alignment, and atomic order/snapshot persistence.
- Source review did not execute a real concurrent integration test because the required JDK compiler is unavailable.
- Verification attempt on 2026-10-27: `mvn test` from `backend` reached Java compilation and failed with `No compiler is provided in this environment`; no backend tests executed.
- Refactor verification on 2026-10-27: `npm.cmd run build` and `npm.cmd run lint` from `frontend` both passed after the package/API boundary changes.
- Refactor verification on 2026-10-27: `mvn test` from `backend` again reached compilation and failed because the environment provides no `javac`.
- Layer-first refactor verification on 2026-10-27: source searches found no legacy package declarations/imports; `mvn test` from `backend` reached compilation and failed with `No compiler is provided in this environment`.
- Layer-first refactor verification on 2026-10-27: `npm.cmd run build` and `npm.cmd run lint` from `frontend` both passed.
- Tightened required numeric request validation so omitted product/stock values are rejected instead of silently becoming zero.
- Added domain validation for order, snapshot, and suggestion invariants.
- Added Flyway V4 to enforce case-insensitive SKU uniqueness at the database boundary, matching service validation.
- Backend verification retry after these fixes: `mvn test` reached compilation and failed with `No compiler is provided in this environment`; no backend tests executed.
- Frontend contract verification after these fixes: `npm.cmd run build` and `npm.cmd run lint` passed.

### Remaining issues

- Backend compilation and tests require a JDK with `javac`; only a JRE is available in this workspace environment.
- The project is not a Git repository, so no diff or commit history can be reported.
- No frontend automated component tests exist yet.
- The original requirements document is unavailable; single-product orders and post-order snapshot semantics are documented assumptions.
- The two confirmed review defects are fixed in source but remain runtime-unverified until backend tests can execute.
- Suggestion source tests are added but also remain runtime-unverified because `javac` is unavailable.
- Added source tests for demand-spike pricing, low-stock priority, zero category average, and HOLD behavior.
- Frontend verification after the workflow changes was blocked because the environment denied the npm command permission request.
- AI and async source paths are implemented but backend runtime verification remains blocked by the missing JDK compiler.
- Backend package refactor is source-reviewed but not compiler-verified for the same environment limitation.
- Mapper extraction and package cleanup are source-reviewed but not compiler-verified.
- Added test-hardening coverage for strict pricing boundaries, reorder overflow, request validation, mapper conversion, accepted/rejected suggestion behavior, and unexpected AI advisor fallback.
- Test-hardening verification on 2026-10-27: 11 backend test classes and 36 JUnit test methods are present; source/package checks passed, but execution remains blocked at Maven compilation because `javac` is unavailable.
- Toolchain verification on 2026-10-27: `java -version` reported OpenJDK 21.0.12, `javac -version` was unavailable, and `mvn -version` reported Maven 3.9.16 running on the OpenLogic JRE.
- Frontend verification on 2026-10-27: `npm.cmd run build` and `npm.cmd run lint` both passed after test-hardening changes.
- Final verification on 2026-10-27: `mvn clean test` from `backend` cleaned successfully and reached compilation, but remained blocked by the JRE-only environment (`No compiler is provided in this environment`).
- Final source review found and fixed a missing `SuggestionService` import in `InventoryRecommendationTriggerListener.java`.
- Final static checks passed: main/test package paths match declarations and no legacy package/import references remain.
- Final build-verification pass on 2026-10-27: `java -version` succeeded, `javac -version` was unavailable, `mvn -version` succeeded, and no alternate JDK installation was found in the checked program directories.
- Final build-verification pass on 2026-10-27: `mvn clean test` from `backend` cleaned successfully but was blocked at compilation by the missing JDK compiler.
- Final frontend pass on 2026-10-27: `npm.cmd run build` and `npm.cmd run lint` passed; `frontend/package.json` has no automated test script.
- Final environment pass on 2026-10-27: Docker was unavailable, so PostgreSQL, Flyway runtime execution, application startup, and end-to-end smoke tests could not run.
- Repository cleanup on 2026-10-27: removed regenerable `backend/target`, `frontend/dist`, and `frontend/node_modules` outputs after verification; removed empty obsolete root `inventory` and `order` directories; no miscellaneous files required moving to `others`.
- Updated `.gitignore` to cover nested Maven/Node outputs, local environment files while preserving `.env.example`, temporary files, and caches.
- Cleanup verification on 2026-10-27: `npm.cmd ci`, `npm.cmd run build`, and `npm.cmd run lint` passed before generated frontend outputs were removed; `mvn clean test` remained blocked by the missing JDK compiler.

### Next action

Expose a full JDK with `javac` and run `mvn clean test` from `backend`; frontend build and lint are already passing.
