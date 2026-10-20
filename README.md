# QuerySleuth — PostgreSQL Diagnostics & Execution Plan Explorer

> A lightweight, open-source diagnostic engine and developer tool that investigates PostgreSQL performance bottlenecks, explains execution plans (JSON and standard text), detects memory and buffer churn, and surfaces evidence-backed tuning recommendations.

---

## 🎯 What QuerySleuth Does

Standard PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` output can be dense, complex, and difficult to interpret across large queries, nested loops, and stored procedures. QuerySleuth solves this by automatically answering three questions:

1. **Why is this query slow?**
2. **Which operation inside this execution plan or function is responsible for the bottleneck?**
3. **What empirical evidence supports each finding, and what should be investigated next?**

---

## 🚀 Key Features & Capabilities

- **Dual Plan Formats**: Parses both PostgreSQL `FORMAT JSON` and standard text-based `EXPLAIN ANALYZE` output.
- **Deterministic Diagnostic Rule Engine**:
  - `PLAN-RULE-001` (**Row Estimate Discrepancy**): Flags severe cardinality under/over-estimates misguiding join and scan choices.
  - `PLAN-RULE-002` (**Repeated Loop Work**): Detects nested loops and inner table lookups with high iteration counts multiplying work.
  - `PLAN-RULE-003` (**Wasteful Sequential Scan**): Identifies table scans that examine many rows but discard a large percentage through filters.
  - `PLAN-RULE-004` (**Sort Spilled to Disk**): Flags sort operations exceeding `work_mem` and resorting to disk-based external merge sorts.
  - `PLAN-RULE-005` (**Heavy Buffer Misses**): Identifies cold cache reads and shared buffer pool pressure requiring disk I/O.
  - `PLAN-RULE-006` (**Excessive Buffer Cache Churn**): Detects queries and stored procedures accessing hundreds of thousands of memory buffer blocks, causing CPU cache contention and memory bus churn.
- **Explicit Limitation Reporting**: Warns developers when an execution plan was generated without `ANALYZE` or `BUFFERS` instead of presenting incomplete metrics as conclusive.
- **Interactive Dark-Mode Web UI**: Served directly by Spring Boot at `/`, featuring one-click sample presets, hierarchical tree visualization, node inspector, severity filters, and limitation alerts.
- **Production-Grade Multi-Stage Dockerfile**: Engineered for low-memory container environments (< 150 MB RAM) with non-root security.
- **Zero Secrets / Zero External Dependencies**: 100% self-contained, no external database required to run the analyzer, no credentials or keys stored.

---

## 📁 Repository Structure

```
querysleuth/
├── backend/
│   ├── src/main/java/com/querysleuth/
│   │   ├── QuerySleuthApplication.java
│   │   ├── api/
│   │   │   ├── PlanController.java
│   │   │   ├── HealthController.java
│   │   │   └── dto/
│   │   ├── common/
│   │   │   ├── Finding.java
│   │   │   ├── Severity.java
│   │   │   ├── Confidence.java
│   │   │   ├── FindingCategory.java
│   │   │   ├── AnalysisResult.java
│   │   │   └── GlobalExceptionHandler.java
│   │   └── plan/
│   │       ├── PlanNode.java
│   │       ├── PlanSummary.java
│   │       ├── PlanParser.java
│   │       ├── TextPlanParser.java
│   │       ├── PlanAnalyzer.java
│   │       └── rule/
│   │           ├── PlanRule.java
│   │           ├── RowEstimateMismatchRule.java
│   │           ├── RepeatedLoopWorkRule.java
│   │           ├── SuspiciousSeqScanRule.java
│   │           ├── SortDiskSpillRule.java
│   │           ├── BufferReadHeavyRule.java
│   │           └── HeavyBufferAccessRule.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── static/                   # Interactive Web UI (index.html, style.css, app.js)
│   ├── src/test/java/                # Automated unit and integration tests
│   ├── src/test/resources/fixtures/
│   ├── Dockerfile
│   └── pom.xml
├── docs/
│   ├── architecture.md
│   └── deployment.md
├── sample-data/
│   ├── schema.sql                    # Synthetic PostgreSQL benchmark generator
│   └── plans/                        # Real-world benchmark execution plans
├── .dockerignore
├── .env.example
├── .gitignore
├── Dockerfile                        # Multi-stage container build
├── docker-compose.yml                # Optional local PostgreSQL test sandbox
├── render.yaml                       # 1-Click deployment blueprint
└── README.md
```

---

## 🛠️ Quickstart

### Prerequisites
- **Java**: 21+ (OpenJDK / Temurin)
- **Maven**: 3.9+
- *(Optional)* **Docker**: For running containerized or deploying to cloud

### 1. Run Locally with Maven
From the `backend` directory:
```bash
cd backend
mvn spring-boot:run
```
Once started, open your browser:
👉 **`http://localhost:8080`**

### 2. Run with Docker
Build and run the container locally:
```bash
docker build -t querysleuth:latest .
docker run -p 8080:8080 querysleuth:latest
```

### 3. Run Automated Tests
```bash
cd backend
mvn test
```
All 17 unit and integration tests will execute in seconds.

---

## 📡 REST API Reference

### 1. Health Check
```http
GET /api/v1/health
```
**Response:**
```json
{
  "status": "UP",
  "service": "QuerySleuth",
  "version": "0.0.1-SNAPSHOT",
  "timestamp": "2026-10-20T14:18:32Z"
}
```

### 2. Analyze Execution Plan
```http
POST /api/v1/plans/analyze
Content-Type: application/json or text/plain
```

Accepts raw JSON from `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` or standard text from `EXPLAIN ANALYZE`.

**Example Request:**
```json
[
  {
    "Plan": {
      "Node Type": "Seq Scan",
      "Relation Name": "tariff",
      "Startup Cost": 0.00,
      "Total Cost": 3845.00,
      "Plan Rows": 120,
      "Actual Rows": 150,
      "Actual Loops": 1,
      "Filter": "(origin_port = 'ANR' AND destination_port = 'NYC')",
      "Rows Removed by Filter": 99850,
      "Shared Hit Blocks": 8450
    },
    "Execution Time": 18.51
  }
]
```

**Response Structure:**
```json
{
  "status": "SUCCESS",
  "summary": {
    "executionTimeMs": 18.51,
    "totalCost": 3845.0,
    "totalSharedHitBlocks": 8450,
    "totalFindings": 1
  },
  "planTree": { ... },
  "findings": [
    {
      "id": "FINDING-SEQSCAN-1",
      "category": "INDEX_OPPORTUNITY",
      "severity": "HIGH",
      "confidence": "HIGH",
      "title": "Wasteful Sequential Scan on 'tariff' (99.8% rows discarded)",
      "evidence": "Scanned 100,000 rows, discarded 99,850 rows (99.85%)",
      "explanation": "A sequential scan examined 100,000 rows to return only 150 matching rows.",
      "suggestedNextStep": "1. Create an index on tariff(origin_port, destination_port)..."
    }
  ],
  "limitations": []
}
```

---

## 🌐 Free Cloud Deployment

QuerySleuth can be deployed for free in under two minutes:

- **Render**: Connect your GitHub repository and select [`render.yaml`](render.yaml) for 1-click deployment on Render's free tier.
- **Koyeb / Railway**: Select **Dockerfile** as the build method.

Detailed instructions are available in the [Cloud Deployment Guide](docs/deployment.md).

---

## 🔒 Security & Privacy Notice

- **No Credentials Stored**: QuerySleuth does not store database credentials, passwords, or connection strings.
- **No Remote Telemetry**: The application operates completely within your own network or container.
- **Safe by Default**: Execution plans are parsed and evaluated in memory with zero external API calls.

---

## 📄 License

MIT License. Free for developer, personal, and commercial use.
