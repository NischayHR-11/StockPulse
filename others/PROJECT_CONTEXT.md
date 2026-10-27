# StockPulse Project Context

## Verified workspace

- Workspace root: `C:\Users\Zycus\Downloads\stockpulse-starter\stockpulse`
- Git: not initialized in this directory or its parent directories as of 2026-10-27.
- The historical project context files were absent; this document is reconstructed from the source.

## Purpose and stack

StockPulse is an AI-assisted inventory and dynamic pricing engine for the Zycus AI-Native Java Engineer hackathon. The verified stack is Java 17, Spring Boot 3.4.2, Spring Web, Spring Data JPA, Jakarta Bean Validation, PostgreSQL 16, Flyway, Spring Boot Actuator, React 18, TypeScript, and Vite.

## Directory structure

- `backend/src/main/java/com/stockpulse`: Spring Boot application and layer-first packages.
- `controller`, `service`, `repository`, `entity`, `dto/request`, `dto/response`, `mapper`, `exception`, `event`, `advisor`, `config`, and `seed` separate technical responsibilities.
- `config`: CORS and bounded asynchronous executor configuration.
- `backend/src/main/resources/db/migration`: Flyway migrations.
- Flyway V4 adds a case-insensitive unique SKU index to match service-level duplicate checking.
- `backend/src/test`: service-focused unit tests, advisor/trigger tests, a Spring context test, and test configuration.
- `frontend/src`: React application, API client, types, and CSS.
- `docker-compose.yml`: local PostgreSQL service.
- `REQUIREMENTS_TRACEABILITY.md`: milestone requirement-to-code/test mapping.

## Implemented functionality

- Product creation with SKU uniqueness checking.
- Product listing with optional status and category filters.
- Product lookup by UUID.
- Product stock updates with active/out-of-stock state transitions.
- Request validation and consistent error response DTOs.
- CORS configuration for the local Vite origin.
- PostgreSQL schema migration for products and indexes.
- React console that loads products, creates products, simulates sales, generates suggestions, and accepts or rejects pending suggestions.
- Opt-in demo seeding for an empty database, controlled by `APP_SEED_ENABLED`.
- Single-product order processing with idempotency, pessimistic product locking, stock decrement, and a post-order inventory snapshot. Orders persist the resulting stock level so replay responses remain historical.
- Inventory snapshot retrieval by product.
- Rule-based pricing and reorder suggestion generation with persisted pending/accepted/rejected lifecycle.
- Suggestions contain current/recommended price, direction, confidence, rationale, trigger, and reorder quantity where applicable.
- Suggestion approval API; accepted price suggestions update product price transactionally and accepted reorder suggestions simulate inbound stock.
- Product-scoped order and pricing/reorder suggestion aliases support the frontend demo.
- Configurable AI advisor adapter with strict response validation and deterministic rule fallback.
- After-commit asynchronous recommendation triggers for low inventory and demand spikes after stock-changing operations.
- Actuator health endpoint configuration.

## Architecture and domain

Controllers accept and return DTOs. Services contain business logic, repositories contain Spring Data access, and entities contain persisted state and invariants. Mappers isolate entity-to-response conversion. AI adapters and deterministic strategies are under `advisor`; events/listeners are under `event`; API errors and advice are under `exception`. `Product` owns SKU, name, category, price, stock, reorder threshold, demand velocity, status, and audit timestamps. Product status is `ACTIVE`, `OUT_OF_STOCK`, or the currently reserved `PRICE_REVIEW_PENDING`; stock reaching zero sets `OUT_OF_STOCK`, and a positive stock update restores an out-of-stock product to `ACTIVE`.

## API contract

- `POST /api/v1/products` creates a product and returns `201`.
- `GET /api/v1/products?status=&category=` lists products.
- `GET /api/v1/products/{id}` returns one product.
- `PATCH /api/v1/products/{id}/stock` updates non-negative stock.
- `GET /actuator/health` exposes service health.
- `POST /api/v1/orders` processes one product order.
- `GET /api/v1/products/{id}/inventory-snapshots` lists snapshots newest first.
- `POST /api/v1/products/{id}/suggestions?trigger=INITIAL|LOW_INVENTORY|DEMAND_SPIKE|MANUAL` generates pending suggestions.
- `GET /api/v1/products/{id}/suggestions` lists suggestions newest first.
- `PATCH /api/v1/suggestions/{id}` approves or rejects a pending suggestion.
- `PATCH /api/v1/pricing-suggestions/{id}` and `/api/v1/reorder-suggestions/{id}` are compatible resolution aliases.

Product creation accepts `sku`, `name`, `category`, `currentPrice`, `stockLevel`, `reorderThreshold`, and `demandVelocity`. Error responses contain timestamp, HTTP status, error, message, path, and field errors.

## Persistence

Flyway `V1__create_products.sql` creates the `products` table with UUID primary keys, positive prices, non-negative inventory metrics, unique SKU, and status/category indexes. `V2__create_orders_and_inventory_snapshots.sql` adds single-product orders, unique idempotency keys, foreign keys, and immutable snapshot records. Production Hibernate schema management is `validate`; tests use an in-memory H2 database with schema generation and Flyway disabled.

## Frontend status

The React console uses the live API for product creation, sale simulation, suggestion generation, and pending suggestion approval/rejection. Its API module keeps request helpers at module scope and has loading, empty, success, and error states. Authentication, stock editing, and automated component tests are not implemented. The API base URL is configurable with `VITE_API_BASE_URL`.

## AI and asynchronous workflow

`FallbackSuggestionAdvisor` selects deterministic rules by default. When `ADVISOR_PROVIDER=ai`, `HttpAiAdvisorClient` uses configured endpoint, API key, and timeout; missing configuration, transport failures, non-2xx responses, malformed JSON, and invalid fields fall back to rules. AI responses never mutate products directly.

`ProductService.updateStock` and successful `OrderService.process` publish `InventoryChangedEvent` inside their transactions. `InventoryRecommendationTriggerListener` handles events with `@TransactionalEventListener(AFTER_COMMIT)` and `@Async`, then generates low-inventory and demand-spike suggestions. Pending duplicate prevention remains in `SuggestionService`; failures are logged without rolling back committed inventory changes.

## Testing status

The backend source now includes 11 focused test classes and 36 JUnit tests covering rules, validation, lifecycle, AI fallback/validation, triggers, orders, inventory, mappers, and product behavior. The frontend has build and lint scripts but no automated component tests. After the test-hardening changes, `npm.cmd run build` and `npm.cmd run lint` passed from `frontend`. The latest `mvn test` reached backend compilation but could not run because this environment has a JRE but no `javac`; all backend compilation, migration, and test results remain unverified. See `CHANGELOG.md` for command history.

## Known issues and assumptions

- The original StockPulse problem statement was not included in the workspace; planned requirements are tracked without claiming implementation.
- No Git history or remote is available.
- Backend verification is blocked until a JDK with `javac` is available on `PATH` or `JAVA_HOME`. The environment contains only `C:\Program Files\OpenLogic\jre-21.0.12.8-hotspot`. Source review found and fixed concurrent same-key retry and replay-response defects; runtime verification of those fixes remains pending.
- Docker is not installed in the current environment, so PostgreSQL startup, Flyway execution against PostgreSQL, and live API smoke tests could not run.
- Repository cleanup removed only regenerable build/dependency outputs and empty obsolete package remnants. The root `others` directory is reserved for nonessential artifacts; none were present to move.
- Demo data must be opt-in so normal startup never mutates a user's database unexpectedly.
- Authentication and multi-product order/fulfillment remain planned. Backend compilation and runtime tests remain unverified because the JDK compiler is unavailable.

## Exact next action

Minimum submission action: expose a full JDK with `javac`, run `mvn clean test` from `backend`, and fix any compiler or test failures it reveals. Frontend build and lint are already verified; PostgreSQL smoke testing additionally requires Docker or a configured development PostgreSQL instance.
