# Asynchronous Job Processing System

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Java](https://img.shields.io/badge/Java%2021-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-231F20?style=for-the-badge&logo=apache-kafka&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![React](https://img.shields.io/badge/React-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)
![nginx](https://img.shields.io/badge/nginx-009639?style=for-the-badge&logo=nginx&logoColor=white)

A production-ready asynchronous job processing system demonstrating enterprise-grade backend architecture patterns including Kafka-driven async execution, Redis caching, rate limiting, and a React UI — all containerised with Docker Compose.

---

## 📌 Overview

This project showcases a scalable asynchronous job processing system built with **Spring Boot**, **Apache Kafka**, **MySQL**, **Redis**, a **React frontend**, and **Docker Compose**. It demonstrates how modern backend systems handle:

- ✅ Long-running jobs without blocking HTTP threads
- ✅ Durable async execution via Kafka (survives restarts, supports replay)
- ✅ High-frequency job status polling with Redis
- ✅ Graceful job cancellation
- ✅ Performance optimisation using caching
- ✅ API protection against abuse via rate limiting
- ✅ Full containerisation with multi-stage Docker builds

---

## 🎯 Why This Project Exists

This project was built to deeply understand:

- Why long-running tasks must never block request threads
- Why Kafka is preferred over in-memory queues for production async systems
- Producer–Consumer architecture with partition-based parallelism
- Kafka offset management and at-least-once delivery semantics
- Database vs cache responsibility split
- Job cancellation in real distributed systems
- Read-heavy optimisation using Redis
- API protection using rate limiting
- Multi-stage Docker builds for lean, secure images
- Clean layering and backend evolution

---

## 🧠 Core Idea (In Simple Terms)

```
1. Client submits a job request
2. Server immediately returns a Job ID (< 100ms)
3. Job ID is published to a Kafka topic
4. Kafka consumer threads process jobs in parallel
5. Job progress & status are updated in Redis
6. Final job result is stored in MySQL
7. Client polls job status efficiently (Redis-first, MySQL fallback)
8. Client can cancel a running job
9. Rate limiting protects the system from overload
```

---

## 🏗 High-Level Architecture

```
Client (React + nginx)
  |
  | POST /api/jobs
  | GET  /api/jobs/{id}
  | DELETE /api/jobs/{id}
  v
nginx (port 80)  ──proxy /api/──►  JobController (Spring Boot :8080)
                                         |
                                         v
                                   JobService (Orchestrator)
                                         |
                                   Save job as PENDING → MySQL
                                   Publish jobId       → Kafka Topic (job-requests)
                                         |
                                         v
                                Apache Kafka (3 Partitions)
                                         |
                                         v
                              JobKafkaConsumer (3 Concurrent Threads)
                                         |
                                   Update progress/status → Redis (TTL: 2 min)
                                   Persist final result   → MySQL
                                         v
                                MySQL (Source of Truth)
                                Redis  (Hot State Cache)
```

---

## 🚀 Running the Project

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running
- Git

### Quick Start

```bash
git clone <your-repo-url>
cd Job_Scheduler_MultiThreading_Project
docker compose up --build
```

Open **http://localhost** in your browser.

> **Note:** First build downloads all Maven dependencies and npm packages. Subsequent builds are fast due to Docker layer caching.

### Useful Commands

| Command | Purpose |
|---|---|
| `docker compose up --build` | Build images and start all services |
| `docker compose up` | Start without rebuilding (uses cached images) |
| `docker compose down` | Stop and remove containers |
| `docker compose down -v` | Stop, remove containers **and** delete volumes (wipes MySQL data) |
| `docker compose logs -f backend` | Stream backend logs |
| `docker compose ps` | Check service health status |

### Service URLs

| Service | URL | Notes |
|---|---|---|
| React UI | http://localhost | Main entry point |
| Backend API | http://localhost:8080 | Direct access for Postman/curl |
| MySQL | localhost:3306 | Use DBeaver or MySQL Workbench |
| Redis | localhost:6379 | Use `redis-cli` |
| Kafka | localhost:9092 | Use `kafka-console-consumer` |

---

## 🏢 Service Startup Order

Docker Compose starts services in dependency order with health checks:

```
mysql (healthy) ──┐
redis (healthy) ──┼──► backend ──► frontend
kafka (healthy) ──┘
     ▲
zookeeper
```

Health checks ensure each service is **ready to accept connections** (not just started) before dependents launch.

---

## 🧩 Key Architectural Patterns

### 1. Kafka Producer–Consumer Pattern

- **Producer:** `JobKafkaProducer` — publishes Job ID to `job-requests` topic
- **Consumer:** `JobKafkaConsumer` — 3 concurrent threads, one per Kafka partition
- **Buffer:** Apache Kafka (durable, persistent, replayable)

Unlike an in-memory queue, Kafka survives JVM crashes. Unprocessed messages are redelivered on consumer restart — ensuring no job is silently lost.

### 2. Asynchronous Processing

- HTTP requests return immediately after DB save + Kafka publish
- Jobs execute in Kafka consumer threads
- System remains responsive under load regardless of job duration

### 3. Cold Data vs Hot Data Separation

| Data Type | Stored In | Reason |
|---|---|---|
| Job result | MySQL | Reliable, persistent, auditable |
| Final status | MySQL | Source of truth |
| Progress | Redis | Changes every few seconds |
| Running status | Redis | Read-heavy during polling |

This prevents excessive database writes and improves read scalability.

### 4. Redis as Performance Accelerator

- In-memory Hash structure: `job:{id}` → `{ status, progress }`
- O(1) reads — single round trip returns both status and progress
- TTL-based automatic cleanup (2 minutes)
- Drastically reduces database load during active job polling

### 5. Rate Limiting

- Redis atomic `INCR` + `EXPIRE` — 5 requests/IP/minute
- Race-condition-free by design (Redis INCR is atomic)
- Protects job creation API from abuse
- Ensures fairness across clients

### 6. Cancel Job Support

- Cancel flag stored in `ConcurrentHashMap` (checked every 5s in consumer loop)
- DB immediately updated to `CANCELLED` on cancel request
- Consumer detects flag within next loop cycle and stops gracefully
- Prevents wasted compute on unwanted jobs

### 7. Multi-Stage Docker Builds

Both backend and frontend use multi-stage builds to produce lean, secure images:

**Backend:**
- Stage 1 (builder): `eclipse-temurin:21-jdk-alpine` — compiles fat JAR with Maven
- Stage 2 (runtime): `eclipse-temurin:21-jre-alpine` — runs only the JAR, no Maven or source

**Frontend:**
- Stage 1 (builder): `node:20-alpine` — installs dependencies and runs Vite build
- Stage 2 (runtime): `nginx:1.27-alpine` — serves static HTML/CSS/JS, proxies `/api/*` to backend

---

## 📦 Package Structure

```
Job_Scheduler_MultiThreading_Project/
├── Dockerfile.backend          # Multi-stage build for Spring Boot
├── Dockerfile.frontend         # Multi-stage build for React + nginx
├── docker-compose.yml          # Orchestrates all 6 services
├── nginx.conf                  # nginx config (SPA routing + API proxy)
│
├── job-processor/              # Spring Boot backend
│   ├── pom.xml
│   └── src/main/java/com/savi/jobprocessor/
│       ├── config/             # Kafka producer/consumer configuration
│       ├── controller/         # REST APIs
│       ├── core/               # Domain enums (JobStatus)
│       ├── dto/                # API response models
│       ├── entity/             # JPA entities (cold data)
│       ├── kafka/              # Kafka producer and consumer
│       ├── ratelimit/          # Rate limiter service and exception
│       ├── redis/              # Redis hot-state service
│       ├── repository/         # Database access
│       └── service/            # Job orchestration & fallback logic
│
└── job-processor-ui/           # React frontend
    ├── package.json
    ├── vite.config.js
    └── src/
```

---

## 🔑 Key Components

### JobController
Exposes REST APIs (`POST /jobs`, `GET /jobs/{id}`, `DELETE /jobs/{id}`). Thin HTTP adapter — no business logic.

### JobService
Orchestrator: creates jobs in MySQL, triggers Kafka producer, retrieves state via Redis-first/MySQL-fallback, manages cancel flags via `ConcurrentHashMap`.

### JobKafkaProducer
Publishes Job ID (`Long`) to `job-requests` topic. Logs delivery success/failure via `whenComplete` callback.

### JobKafkaConsumer
Listens on `job-requests` with `concurrency = 3`. Transitions jobs through `PENDING → RUNNING → COMPLETED/FAILED/CANCELLED`. Updates Redis every 5 seconds. Persists final result to MySQL.

### RedisJobStateService
Stores `status` and `progress` as Redis Hash fields under `job:{id}` with 2-minute TTL. Returns `Optional<JobStatus>` and `Optional<Long>` for safe fallback logic.

### JobEntity (MySQL)
Stores cold, reliable data: final status, result, error message, `createdAt`, `updatedAt`.

---

## 🔄 Job Lifecycle

```
PENDING  →  RUNNING  →  COMPLETED
                     →  FAILED
                     →  CANCELLED
```

- Kafka consumer controls execution and transitions
- Redis handles live state during `RUNNING`
- MySQL is the final authority for all terminal states
- Cancel is best-effort: if job completes before cancel is detected, `409 Conflict` is returned

---

## 🧪 Example API Flow

### Create Job
```http
POST /jobs
```
```json
{
  "success": true,
  "data": { "id": 5, "status": "PENDING", "progress": 0 }
}
```

### Poll Status — Redis Fast Path
```http
GET /jobs/5
```
```json
{
  "success": true,
  "data": { "jobId": 5, "status": "RUNNING", "progress": 60 }
}
```

### Poll Status — MySQL Fallback
```json
{
  "success": true,
  "data": { "jobId": 5, "status": "COMPLETED", "progress": 100, "result": "Job Completed Successfully", "errorMessage": null }
}
```

### Cancel Job
```http
DELETE /jobs/5
```
```json
{
  "success": true,
  "data": { "id": 5, "status": "CANCELLED" }
}
```

### Rate Limited Response
```json
{
  "success": false,
  "error": { "status": 429, "error": "Too Many Requests", "message": "Too many job creation requests, please try again later." }
}
```

---

## ⚙ Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (Virtual Threads enabled) |
| Framework | Spring Boot 4.0.1 |
| Messaging | Apache Kafka 3.5.1 (ZooKeeper mode) |
| Cache | Redis 7.x (Spring Data Redis / Lettuce) |
| Database | MySQL 8.x (Spring Data JPA / Hibernate 7) |
| Frontend | React + Vite |
| Web Server | nginx 1.27 |
| Containerisation | Docker + Docker Compose |
| Build | Maven (via Maven Wrapper) |

---

## 🚧 Challenges Faced & Solutions

| Challenge | Solution |
|---|---|
| Jobs lost on JVM crash | Replaced in-memory queue with Kafka — messages persist on disk and are redelivered on restart |
| Excessive DB writes during polling | Moved progress & status updates to Redis Hash with 2-min TTL |
| Polling load on MySQL | Redis-first reads absorb hot traffic; MySQL only hit on cache miss |
| Job cancellation in async context | `ConcurrentHashMap` cancel flag checked in consumer loop every 5 seconds |
| System abuse on job creation | Redis atomic `INCR`+`EXPIRE` rate limiter — 5 requests/IP/min |
| kafka-clients version conflict | Removed manual version override; let Spring Boot 4.x manage compatible version |
| `mvnw: not found` in Docker build | Maven wrapper must be copied and made executable **before** `pom.xml` is processed; also strip Windows line endings via `sed -i 's/\r$//' mvnw` |
| Docker layer cache using stale broken layers | Rebuilt with `docker compose build --no-cache` to force fresh layers |

---

## 🔮 Future Enhancements

- [ ] WebSocket live progress updates
- [ ] KRaft mode (Kafka without ZooKeeper)
- [ ] UI dashboard for job monitoring

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

## 📧 Contact

For questions or feedback, please open an issue in the repository.

---

**Built with ❤️ to demonstrate production-grade backend architecture**
