# Asynchronous Job Processing System

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Java](https://img.shields.io/badge/Java%2021-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)

A production-ready asynchronous job processing system demonstrating enterprise-grade backend architecture patterns including multithreading, caching, and rate limiting.

## 📌 Overview

This project showcases a scalable asynchronous job processing system built with **Spring Boot**, **Java concurrency**, **MySQL**, **Redis**, and **rate limiting**. It demonstrates how modern backend systems handle:

- ✅ Long-running jobs without blocking HTTP threads
- ✅ High-frequency job status polling
- ✅ Graceful job cancellation
- ✅ Performance optimization using caching
- ✅ API protection against abuse

### Architecture Philosophy

Instead of executing heavy work inside HTTP request threads, the system:

1. Accepts job requests
2. Immediately returns a Job ID
3. Processes jobs asynchronously using worker threads
4. Stores **cold, reliable data** in MySQL
5. Stores **hot, frequently changing data** in Redis
6. Applies rate limiting to protect APIs

This architecture is commonly used in **banking systems**, **reporting engines**, **analytics pipelines**, **file processing services**, and **enterprise schedulers**.

---

## 🎯 Why This Project Exists

This project was built to deeply understand:

- Why long-running tasks must never block request threads
- Producer–Consumer architecture
- Thread pools and safe concurrency
- Asynchronous job orchestration
- Database vs cache responsibility split
- Job cancellation in real systems
- Read-heavy optimization using Redis
- API protection using rate limiting
- Clean layering and backend evolution


---

## 🧠 Core Idea (In Simple Terms)

```
1. Client submits a job request
2. Server immediately returns a Job ID
3. Job ID is placed into a queue
4. Worker threads process the job in background
5. Job progress & status are updated in Redis
6. Final job result is stored in MySQL
7. Client polls job status efficiently
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
JobService (Producer + Orchestrator)
  |
  | Save cold data → MySQL
  | Save hot data  → Redis
  | Enqueue jobId
  v
BlockingQueue<Long>
  |
  v
JobWorker (Consumer Threads)
  |
  | Update progress/status → Redis
  | Persist final result   → MySQL
  v
MySQL (Source of Truth)
Redis (Hot State Cache)
```

---

## 🧩 Key Architectural Patterns

### 1. Producer–Consumer Pattern

- **Producer:** `JobService`
- **Consumer:** `JobWorker`
- **Buffer:** `BlockingQueue<Long>`

Ensures thread-safe coordination between HTTP threads and worker threads.

### 2. Asynchronous Processing

- HTTP requests return immediately
- Jobs execute in background threads
- System remains responsive under load

### 3. Cold Data vs Hot Data Separation

| Data Type      | Stored In | Reason                 |
|----------------|-----------|------------------------|
| Job result     | MySQL     | Reliable & persistent  |
| Final status   | MySQL     | Source of truth        |
| Progress       | Redis     | Changes frequently     |
| Running status | Redis     | Read-heavy             |

This prevents excessive database writes and improves scalability.

### 4. Redis as Performance Accelerator

- In-memory storage
- O(1) reads and writes
- Handles high-frequency polling
- TTL-based automatic cleanup
- Drastically reduces database load

### 5. Rate Limiting

- Protects APIs from abuse
- Prevents polling storms
- Ensures fairness across clients
- Improves overall system stability

### 6. Cancel Job Support

- Clients can cancel running jobs
- Workers check cancellation state
- Graceful termination of execution
- Prevents wasted compute resources

---

## 📦 Package Structure

```
com.savi.jobprocessor
│
├── config        → Executor & rate limiter config
├── controller    → REST APIs
├── core          → Domain enums (JobStatus)
├── dto           → API response models
├── entity        → JPA entities (cold data)
├── redis         → Redis hot-state services
├── repository    → Database access
├── service       → Job orchestration & fallback logic
└── worker        → Background job execution
```

---

## 🔑 Important Components

### JobController

Exposes REST APIs:
- `POST /jobs` - Create a new job
- `GET /jobs/{id}` - Get job status
- `DELETE /jobs/{id}` - Cancel a job

**Responsibilities:**
- No business logic
- No job execution
- Thin, clean controller

### JobService

**Responsibilities:**
- Creates jobs
- Stores persistent data in MySQL
- Enqueues job IDs
- Retrieves job state using Redis-first fallback logic
- Acts as the system orchestrator

### JobWorker

**Responsibilities:**
- Runs inside a thread pool
- Picks job IDs from queue
- Updates progress in Redis
- Checks for cancellation
- Persists final result in MySQL
- Cleans Redis state using TTL or delete

### RedisJobStateService

**Responsibilities:**
- Stores job status & progress
- Uses Redis Hash per job
- Applies TTL for cleanup
- Handles hot state efficiently

### JobEntity (MySQL)

Stores **cold, reliable data:**
- Final status
- Result
- Error message
- Timestamps

Ensures crash recovery and auditability.

### DTOs

- Clear separation of API contracts
- Redis-based DTO for fast responses
- Database-based DTO for fallback
- Prevents leaking internal models

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
- Workers control execution
- Redis handles live state
- MySQL is the final authority

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
  "jobId": 5,
  "status": "PENDING",
  "progress": 0
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
  "jobId": 5,
  "status": "RUNNING",
  "progress": 60
}
```

### Poll Status (Fallback – MySQL)

**Response:**
```json
{
  "jobId": 5,
  "status": "COMPLETED",
  "progress": 100,
  "result": "Job Completed Successfully"
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
  "jobId": 5,
  "status": "CANCELLED"
}
```

---

## ⚙ Technology Stack

- **Java 21**
- **Spring Boot**
- **Spring Data JPA**
- **Spring Data Redis**
- **ThreadPoolTaskExecutor**
- **MySQL**
- **Redis**
- **Maven**

---

## 🚧 Challenges Faced & Solutions

| Challenge | Solution |
|-----------|----------|
| Excessive DB Writes | Moved progress & status updates to Redis |
| Polling Load | Redis-first reads and rate limiting |
| Job Cancellation | Shared state checks and graceful exit |
| System Abuse | Implemented rate limiting |
| Clean Evolution | System evolved without breaking APIs |

---

## 🔮 Future Enhancements

- [ ] Distributed workers (Kafka / RabbitMQ)
- [ ] WebSocket live updates
- [ ] Job retry & backoff
- [ ] UI dashboard
- [ ] Horizontal scaling
- [ ] Metrics & tracing



---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

## 📧 Contact

For questions or feedback, please open an issue in the repository.

---

**Built with ❤️ to demonstrate production-grade backend architecture**