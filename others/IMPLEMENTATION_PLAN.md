# StockPulse Implementation Plan

Statuses: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `DONE`.

## Milestones

1. **Product catalog vertical slice — DONE**
   - Product entity, migration, repository, service, DTOs, validation, error handling, list filters, and stock updates.
   - React inventory and suggestion console.
   - Context-load and milestone behavior tests.

2. **Opt-in demo data and documentation — DONE**
   - Add idempotent demo seed data behind `APP_SEED_ENABLED`/`app.seed.enabled`.
   - Keep the default disabled and document startup/demo commands.
   - Verify backend tests and frontend build.
   - Earlier frontend verification passed; backend verification is blocked by the environment's missing JDK compiler.

3. **Orders and inventory snapshots — IN_PROGRESS**
   - Scope: one product per order; no payment, cart, or fulfillment workflow.
   - Accept a positive quantity and a required idempotency key.
   - Atomically lock the product, reject insufficient stock, decrement stock, persist a processed order, and persist one immutable post-order snapshot.
   - Return the existing order for a repeated identical idempotency key; reject reuse with different product or quantity.
   - Expose order processing and product snapshot retrieval through DTO-based APIs.
   - Add migrations and service tests for success, validation, unknown product, insufficient stock, idempotency, snapshots, and status transitions.
   - Review result: fixed same-key post-lock race and stable replay remaining-stock response; added regression tests.
   - Acceptance is blocked until backend tests run with a JDK.

4. **Pricing and reorder suggestions — IN_PROGRESS**
   - Add persisted pricing and reorder suggestions with pending/accepted/rejected lifecycle, direction, confidence, reasoning, and trigger context.
   - Implement exact pricing priority: low stock +10%; otherwise demand above twice category-average demand +5%; otherwise HOLD.
   - Implement exact reorder quantity `max(1, reorderThreshold * 3 - currentStock)`.
   - Prevent duplicate pending suggestions for the same product, type, and trigger.
   - Approve pricing transactionally; simulate approved reorder inbound stock; reject without changing product state.
   - Add focused tests for priority, category-average zero, boundaries, duplicate prevention, and resolution behavior.
   - Backend runtime verification remains blocked by the missing JDK compiler; source tests will be added regardless.

5. **AI integration — IN_PROGRESS**
   - Add strict structured parsing, validation, timeout, observability, and deterministic fallback behavior.
   - Implemented a configuration-driven HTTP adapter; provider selection defaults to rules and AI failures fall back to rules.
   - Remaining: compiled verification and adaptation if an authoritative provider contract is supplied.

6. **Async recommendation workflow — IN_PROGRESS**
   - Implemented post-commit asynchronous inventory-change events for low-inventory and demand-spike generation.
   - Duplicate pending prevention and human approval remain enforced.
   - Remaining: runtime verification and operational retry/status telemetry.

7. **Approval console and release readiness — IN_PROGRESS**
   - React live workflows are implemented; frontend test coverage and release validation remain.

8. **Production structure review — DONE (source-reviewed)**
   - Separated feature controllers from DTOs.
   - Moved infrastructure configuration into `config`.
   - Added bounded asynchronous executor configuration.
   - Centralized unexpected-error sanitization.
   - Fixed the frontend API module export boundary.
   - Backend compiler verification remains blocked by the environment.

9. **Conventional layer-first backend refactor — DONE (source-reviewed)**
   - Physically organize all backend classes under the required layer-first packages.
   - Extract entity-to-response conversion into the `mapper` package.
   - Remove all legacy feature-local, `domain`, `persistence`, and `api` packages.
   - Static package/import verification is complete; runtime acceptance remains blocked until Maven can access `javac`.

10. **Backend test hardening — IN_PROGRESS**
   - Added deterministic coverage for validation, rule boundaries and overflow, mapper conversion, suggestion resolution, and unexpected AI fallback.
   - 11 test classes and 36 JUnit test methods are present.
   - Runtime execution remains blocked by the missing JDK compiler.

## Verification blocker

The current environment has OpenJDK runtime 21 but no `javac`; Maven 3.9.16 runs on `C:\Program Files\OpenLogic\jre-21.0.12.8-hotspot`. Docker is also unavailable. Install or expose a JDK before marking runtime-blocked milestones `DONE`, and use Docker or a configured PostgreSQL instance for database smoke testing.

## Current acceptance criteria

- Demo seed data is never inserted unless explicitly enabled.
- Repeated enabled startups do not create duplicate SKUs.
- Seeded products are visible through the existing API and React console.
- Product service behavior has tests for creation conflict and stock state transitions.
- Order processing tests cover valid orders, invalid quantities, unknown products, insufficient stock, idempotency, snapshots, and resulting stock.
- Snapshot retrieval validates product existence and returns newest-first snapshot DTOs.
- Documentation states actual verification results and one exact next action.
- Code review confirms the order response stores original resulting stock rather than deriving it from later product state.
- Code review confirms a second idempotency lookup occurs after the product lock.

## Exact next action

Expose a full JDK with `javac`, run `mvn clean test` from `backend`, and fix any compiler or test failures before marking milestone 10 and the previously runtime-blocked milestones `DONE`.
