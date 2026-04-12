# ShopNova - Complete Microservices eCommerce System

This project is a production-ready eCommerce backend and frontend demo built using Spring Boot, React, PostgreSQL, and Redis.

## Architecture

The system consists of 5 microservices:
1. **Product Service**: Manages catalog and inventory (PostgreSQL)
2. **Cart Service**: Manages temporary shopping carts (Redis)
3. **Order Service**: Orchestrates checkout and state transitions (PostgreSQL)
4. **Payment Service**: Highly deterministic payment simulation (PostgreSQL)
5. **User Service**: JWT authentication and profile management (PostgreSQL)

## AI Governance & Principles

- **No AI in Payments**: All payment logic is rigid, deterministic Java code.
- **Contract First**: OpenAPI specifications drive the API logic.
- **Validation First**: Every controller enforces strict `@Valid` constraints.

## Running the Application

### 1. Requirements
* Docker & Docker Compose
* Java 17 (if running manually)
* Node.js (if extending frontend further)

### 2. Start the Backend via Docker Compose

From the root directory:
```bash
# This will spin up the 4 Postgres instances, 1 Redis instance, and build/run the 5 Java Microservices.
docker-compose up -d --build
```
> **Note**: You will need to create a `Dockerfile` for each microservice if you wish to use the `build: context:` configuration in docker-compose. Alternatively, you can run the Java services directly via Maven (`mvn spring-boot:run`).

### 3. Run the Frontend

The React frontend has been bundled into a single inline HTML file to sidestep CORS complications from the `file://` protocol. 
Just open:
`frontend/index.html`
directly in your web browser.

## Default Ports
- Frontend: N/A (runs in browser)
- Product Service: 8081
- Cart Service: 8082
- Order Service: 8083
- Payment Service: 8084
- User Service: 8085

## Edge Cases and Risk Analysis
Please see `docs/risk-edge-cases.md` and `docs/code-review-report.json` for detailed insights into stability, scalability, and code quality.
