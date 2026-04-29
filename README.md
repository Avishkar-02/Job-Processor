# Asynchronous Job Processing System

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Java](https://img.shields.io/badge/Java%2021-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-231F20?style=for-the-badge&logo=apache-kafka&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)

A production-ready asynchronous job processing system demonstrating enterprise-grade backend architecture patterns including Kafka-driven async execution, Redis caching, and rate limiting.

## 📌 Overview

This project showcases a scalable asynchronous job processing system built with **Spring Boot**, **Apache Kafka**, **MySQL**, **Redis**, and **rate limiting**. It demonstrates how modern backend systems handle:

- ✅ Long-running jobs without blocking HTTP threads
- ✅ Durable async execution via Kafka (survives restarts, supports replay)
- ✅ High-frequency job status polling with Redis
- ✅ Graceful job cancellation
- ✅ Performance optimization using caching
- ✅ API protection against abuse

### Architecture Philosophy

Instead of executing heavy work inside HTTP request threads, the system:

1. Accepts job requests and immediately returns a Job ID
2. Publishes only the Job ID to a Kafka topic
3. Kafka consumers process jobs asynchronously in parallel
4. Stores **hot, frequently changing data** (status, progress) in Redis
5. Stores **cold, reliable data** (result, final status) in MySQL
6. Applies rate limiting to protect APIs

This architecture is commonly used in **banking systems**, **reporting engines**, **analytics pipelines**, **file processing services**, and **enterprise schedulers**.

---

## 🎯 Why This Project Exists

This project was built to deeply understand:

- Why long-running tasks must never block request threads
- Why Kafka is preferred over in-memory queues for production async systems
- Producer–Consumer architecture with partition-based parallelism
- Kafka offset management and at-least-once delivery semantics
- Database vs cache responsibility split
- Job cancellation in real distributed systems
- Read-heavy optimization using Redis
- API protection using rate limiting
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
Client
  |
  | POST /jobs
  | GET /jobs/{id}
  | DELETE /jobs/{id}
  v
JobController
  |
  v
JobService (Orchestrator)
  |
  | Save job as PENDING → MySQL
  | Publish jobId       → Kafka Topic (job-requests)
  v
Apache Kafka (3 Partitions)
  |
  v
JobKafkaConsumer (3 Concurrent Consumer Threads)
  |
  | Update progress/status → Redis (TTL: 2 min)
  | Persist final result   → MySQL
  v
MySQL (Source of Truth)
Redis (Hot State Cache)
```

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

| Data Type      | Stored In | Reason                          |
|----------------|-----------|----------------------------------|
| Job result     | MySQL     | Reliable, persistent, auditable  |
| Final status   | MySQL     | Source of truth                  |
| Progress       | Redis     | Changes every few seconds        |
| Running status | Redis     | Read-heavy during polling        |

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

---

## 📦 Package Structure

```
com.savi.jobprocessor
│
├── config        → Kafka producer/consumer configuration
├── controller    → REST APIs
├── core          → Domain enums (JobStatus)
├── dto           → API response models
├── entity        → JPA entities (cold data)
├── kafka         → Kafka producer and consumer
├── ratelimit     → Rate limiter service and exception
├── redis         → Redis hot-state service
├── repository    → Database access
└── service       → Job orchestration & fallback logic
```

---

## 🔑 Important Components

### JobController

Exposes REST APIs:
- `POST /jobs` — Create a new job
- `GET /jobs/{id}` — Get job status and progress
- `DELETE /jobs/{id}` — Cancel a job

**Responsibilities:**
- No business logic
- No job execution
- Thin, clean HTTP adapter

### JobService

**Responsibilities:**
- Creates jobs and persists to MySQL as `PENDING`
- Triggers Kafka producer to publish Job ID
- Retrieves job state using Redis-first, MySQL-fallback logic
- Manages cancel flag via `ConcurrentHashMap`
- Acts as the system orchestrator

### JobKafkaProducer

**Responsibilities:**
- Publishes Job ID (`Long`) to Kafka topic `job-requests`
- Uses `KafkaTemplate.send()` with async delivery confirmation
- Logs delivery success/failure via `whenComplete` callback

### JobKafkaConsumer

**Responsibilities:**
- Listens on `job-requests` with `concurrency = 3`
- Fetches full `JobEntity` from DB by ID (always reads latest state)
- Transitions job through `PENDING → RUNNING → COMPLETED/FAILED/CANCELLED`
- Updates Redis every 5 seconds with progress
- Checks cancel flag on each loop iteration
- Persists final result to MySQL; cleans Redis on completion

### RedisJobStateService

**Responsibilities:**
- Stores `status` and `progress` as Redis Hash fields under `job:{id}`
- Applies 2-minute TTL on every status write
- Provides typed getters returning `Optional<JobStatus>` and `Optional<Long>`
- Deletes job hash on terminal state

### JobEntity (MySQL)

Stores **cold, reliable data:**
- Final status, result, error message
- `createdAt`, `updatedAt` (auto-managed via `@PrePersist` / `@PreUpdate`)

Ensures crash recovery, auditability, and historical queries.

### DTOs

- `PostJobResponse` — returned on job creation (id, status, progress)
- `GetJobResponse` — returned from DB fallback (includes result, errorMessage)
- `RedisJobResponse` — returned from Redis cache hit (id, status, progress)
- `CancelJobResponse` — returned on cancel (id, status)
- `ApiResponse<T>` — unified wrapper with success/error envelope

---

## 🔄 Job Lifecycle

```
PENDING
   ↓
RUNNING
   ↓
COMPLETED / FAILED / CANCELLED
```

**Rules:**
- Kafka consumer controls execution and transitions
- Redis handles live state during `RUNNING`
- MySQL is the final authority for all terminal states
- Cancel is best-effort — if job completes before cancel is detected, `409 Conflict` is returned

---

## 🧪 Example API Flow

### Create Job

**Request:**
```http
POST /jobs
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": 5,
    "status": "PENDING",
    "progress": 0
  }
}
```

### Poll Status (Fast Path – Redis)

**Request:**
```http
GET /jobs/5
```

**Response:**
```json
{
  "success": true,
  "data": {
    "jobId": 5,
    "status": "RUNNING",
    "progress": 60
  }
}
```

### Poll Status (Fallback – MySQL)

**Response:**
```json
{
  "success": true,
  "data": {
    "jobId": 5,
    "status": "COMPLETED",
    "progress": 100,
    "result": "Job Completed Successfully",
    "errorMessage": null
  }
}
```

### Cancel Job

**Request:**
```http
DELETE /jobs/5
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": 5,
    "status": "CANCELLED"
  }
}
```

### Error Response (Rate Limited)

```json
{
  "success": false,
  "error": {
    "status": 429,
    "error": "Too Many Requests",
    "message": "Too many job creation requests, please try again later."
  }
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
| Build | Maven |

---

## 🚧 Challenges Faced & Solutions

| Challenge | Solution |
|---|---|
| Jobs lost on JVM crash | Replaced in-memory queue with Kafka — messages persist on disk and are redelivered on restart |
| Excessive DB writes during polling | Moved progress & status updates to Redis Hash with 2-min TTL |
| Polling load on MySQL | Redis-first reads absorb hot traffic; MySQL only hit on cache miss |
| Job cancellation in async context | ConcurrentHashMap cancel flag checked in consumer loop every 5 seconds |
| System abuse on job creation | Redis atomic INCR+EXPIRE rate limiter — 5 requests/IP/min |
| kafka-clients version conflict | Removed manual version override; let Spring Boot 4.x manage compatible version |

---

## 🔮 Future Enhancements

- [ ] Retry with exponential backoff and Dead Letter Queue (`@RetryableTopic`)
- [ ] Idempotency guard — skip reprocessing if job already `RUNNING` or `COMPLETED`
- [ ] Distributed cancel flag via Redis (supports multi-instance deployments)
- [ ] Micrometer + Prometheus metrics (job duration, consumer lag, failure rate)
- [ ] WebSocket live progress updates
- [ ] Flyway database migrations
- [ ] UI dashboard for job monitoring

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

## 📧 Contact

For questions or feedback, please open an issue in the repository.

---

**Built with ❤️ to demonstrate production-grade backend architecture**