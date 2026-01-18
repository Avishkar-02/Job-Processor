# Asynchronous Job Processing System (Spring Boot)

## 📌 Overview

This project is a **production-style asynchronous job processing system** built using **Spring Boot**, **Java concurrency**, and **MySQL**. It demonstrates how real backend systems handle **long-running tasks** without blocking HTTP request threads.

Instead of executing heavy work inside web requests, the system:

* Accepts a job request
* Immediately returns a **Job ID**
* Processes the job asynchronously using worker threads
* Allows clients to poll job status later

This architecture is widely used in **banking, e‑commerce, reporting, analytics, and enterprise systems**.

---

## 🎯 Why This Project Exists

This project was built to deeply understand:

* Why long-running tasks must **not block request threads**
* Producer–Consumer architecture
* Thread pools and task execution
* Safe concurrency with shared resources
* Clean layering and separation of concerns
* Gradual evolution from in-memory design to database-backed systems


---

## 🧠 Core Idea (In Simple Terms)

1. Client sends `POST /jobs`
2. Server creates a job record and returns a Job ID immediately
3. Job ID is pushed into a queue
4. Background workers pick jobs from the queue
5. Workers update job status in the database
6. Client polls `GET /jobs/{id}` to check status

---

## 🏗 High-Level Architecture

```
Client
  |
  | POST /jobs
  v
JobController
  |
  v
JobService  (Producer)
  |
  | save job to DB
  | enqueue jobId
  v
BlockingQueue<Long>
  |
  v
JobWorker (Consumer threads)
  |
  | process job
  | update DB
  v
MySQL Database
```

---

## 🧩 Key Architectural Patterns Used

### 1. Producer–Consumer Pattern

* **Producer**: `JobService`
* **Consumer**: `JobWorker`
* **Buffer**: `BlockingQueue<Long>`

This ensures thread-safe coordination between request threads and worker threads.

---

### 2. Asynchronous Processing

* HTTP threads return immediately
* Heavy work runs in background threads
* Improves throughput and scalability

---

### 3. Single Source of Truth

* Database is the **only source of job state**
* No in-memory job state is relied upon
* System is restart-safe

---

### 4. Thread Pool Management

* Uses Spring’s `ThreadPoolTaskExecutor`
* Fixed number of worker threads
* Graceful shutdown supported

---

## 📦 Package Structure

```
com.savi.jobprocessor
│
├── config        → Executor configuration
├── controller    → REST APIs
├── core          → Domain enums (JobStatus)
├── dto           → API response models
├── entity        → JPA entities
├── repository    → Database access
├── service       → Business logic & job orchestration
├── worker        → Background job execution
```

---

## 🔑 Important Classes Explained

### ExecutorConfig

* Defines `ThreadPoolTaskExecutor` as a Spring bean
* Controls number of worker threads
* Handles graceful shutdown

---

### JobController

* Entry point for clients
* Exposes:

  * `POST /jobs`
  * `GET /jobs/{id}`
* Does **not** execute jobs

---

### JobService

* Creates jobs
* Persists jobs in database
* Pushes job IDs to queue
* Starts worker threads

Acts as the **producer**.

---

### JobWorker

* Runs in background threads
* Picks job IDs from queue
* Fetches job from DB
* Updates status:

  * PENDING → RUNNING → COMPLETED / FAILED

Acts as the **consumer**.

---

### JobEntity

* Represents job state in database
* Stores:

  * status
  * progress
  * result
  * error message
  * timestamps

---

### JobRepository

* Spring Data JPA repository
* Handles all DB operations
* No SQL written manually

---

### DTOs (PostJobResponse / GetJobResponse)

* Separate API contracts for POST and GET
* Prevents leaking internal structure
* Clean and versionable API design

---

## 🔄 Job Lifecycle

```
PENDING
   ↓ (picked by worker)
RUNNING
   ↓
COMPLETED / FAILED
```

Rules:

* Only workers change execution state
* Controller never changes job status

---

## 🧪 Example API Flow

### Create Job

```
POST /jobs
```

Response:

```json
{
  "id": 5,
  "status": "PENDING",
  "progress": 0
}
```

---

### Check Status

```
GET /jobs/5
```

Response:

```json
{
  "jobId": 5,
  "status": "COMPLETED",
  "progress": 100,
  "result": "Job Completed Successfully",
  "errorMessage": null
}
```

---

## ⚙ Technology Stack

* Java 21
* Spring Boot
* Spring Data JPA
* ThreadPoolTaskExecutor
* MySQL
* Maven

---

## 🚧 Challenges Faced & Solutions

### 1. Blocking HTTP Threads

Solved by moving execution to background workers.

### 2. Thread Safety

Solved using `BlockingQueue` and thread pools.

### 3. In-Memory vs Database State

Solved via phased migration to DB-backed jobs.

### 4. Clean API Design

Solved using separate DTOs for POST and GET.

---

## 🔮 Future Enhancements

* Redis caching for job status
* Kafka / RabbitMQ for distributed workers
* Authentication & authorization
* Rate limiting
* UI dashboard
