# AI-Based Student Performance Prediction System

Java console application for managing student records, subject-wise internal marks, assignment/quiz scores, and performance summaries. Student data is stored in MySQL. CSV export and the optional OpenAI Responses API analysis remain available.

## Student scoring format

For each of seven subjects in both internals, enter the exam score and three assignment and three quiz scores.

- Java, DS, CA, TTPS: exam out of 30, plus assignment average out of 5 and quiz average out of 5 (subject total /40).
- CS, CT, HVPE: exam out of 20, plus the same two averages (subject total /30).
- A complete internal assessment is out of 250.

The program calculates assignment and quiz averages, each subject's Internal 1 and Internal 2 percentages, each internal percentage, the recorded overall percentage, and subject trends. Incomplete assessment records remain visible; the application labels provisional percentages and excludes incomplete students from rankings and support lists.

## Student management menu

1. Add student (duplicate roll numbers are rejected).
2. View all student profiles.
3. Search and view a profile by roll number.
4. Update name, CGPA, and previous-semester SGPA.
5. Update all academic marks.
6. Delete a student after typing `YES` to confirm.
7. Compare two students subject by subject.
8. Rank students by overall percentage (complete records only).
9. List students below the support threshold (complete records only).
10. Export CSV with marks, averages, trends, and analysis.
11. Request optional AI analysis. API key/model configuration remains as described below.
12. Upload a text-based syllabus PDF, review/correct extracted structure, then approve it into MySQL.
13. View approved syllabus documents and the active internal-to-unit mapping.
14. Configure which unit numbers are associated with Internal 1 and Internal 2.
15. Bulk import students from Excel (.xlsx or .xls) with validation and preview.
16. Generate sample Excel import template (.xlsx) with embedded documentation.

Roll numbers must match `1602-25-733-XXX` with three digits in place of `XXX`. GPA values must be from 0 through 10, and every mark is validated against its subject's maximum.

## Performance rules

- The support threshold defaults to 50% and can be changed with `SUPPORT_THRESHOLD_PERCENT` (0 through 100).
- Internal 1 below the threshold flags Units 1 and 2; Internal 2 below the threshold flags Units 3, 4, and 5.
- Subject improvement/decline is Internal 2 percentage minus Internal 1 percentage.
- Strongest and weakest subjects are calculated from recorded subject percentages.
- Numerical calculations and ranking use Java. The existing AI integration remains an optional language-model explanation, not a trained or validated ML model.

PowerShell threshold example:

```powershell
$env:SUPPORT_THRESHOLD_PERCENT = "55"
```

## Safe database setup and migration

Requirements: JDK 17+, Maven, and MySQL Server 8.0+.

1. Back up the database before applying a schema change.
2. In MySQL Workbench, create the `student_performance` database or run `database/schema.sql`.
3. Set the MySQL connection if your login differs from the defaults:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/student_performance?serverTimezone=UTC"
$env:DB_USER = "root"
$env:DB_PASSWORD = "your_mysql_password"
```

4. Start the application with `mvn compile exec:java`.

On startup, `DatabaseMigrator` uses metadata checks and additive `CREATE TABLE IF NOT EXISTS` / `ALTER TABLE` operations. It never drops or rewrites the existing `students`, `internal_marks`, or `internal_assessments` tables. It creates a `schema_migrations` log and a `legacy_internal_marks_archive` table. Legacy rows are copied with `INSERT IGNORE`, so startup can be repeated. The original `internal_marks` table is left intact.

The old `internal_marks` structure stores only a raw mark and does not record its maximum or whether it was an exam, assignment, or quiz score. For that reason, the migration archives the exact old mark and displays it in the student profile, but does not guess how to convert it into the newer scoring format. A student with only legacy marks remains in MySQL and is reported as incomplete until modern assessment marks are entered. Existing rows in `internal_assessments` are preserved; nullable component columns allow incomplete assessments without treating missing scores as zero. The optional SQL archive script is `database/migrations/V2__archive_legacy_internal_marks.sql`; the application performs the same archive automatically.

The schema has primary keys on roll numbers and on student/internal/subject assessment combinations, foreign keys from assessments to students, and checks for assessment numbers and marks in a newly created database. A delete confirmation removes a student's current record/assessments through the FK; the separate raw legacy archive remains available.

## Phase 3: syllabus PDF upload and review

PDFBox extracts selectable text from college syllabus PDFs. Choose menu option 12 and enter the full PDF path (drag/drop may paste a path in the console). The parser treats each `SYLLABUS FOR ... SEMESTER` heading as a course-section boundary, detects the preceding standalone subject heading, and reads the department, semester, code (including `UI25PC310CS` and `U25OE310CE`), credits and marks from that section only. This prevents a later course code or elective label from being attached to an earlier course. It recognizes Arabic/Roman unit numbering and hyphen, en dash, em dash, colon, and space separators. Page breaks do not reset an open unit. Course types include theory (core), professional elective (PE), laboratory, open elective, skill development, and bridge course. Professional electives (such as `COMPUTER ARCHITECTURE – UI25PE310CS` and `ARTIFICIAL INTELLIGENCE AND MACHINE LEARNING – UI25PE410CS`) are correctly classified as professional electives and eligible for departmental import rather than being discarded as open electives. Noise, page numbers, and running institution headers across page breaks are filtered from unit topics.

After extraction, choose department and semester (for example `CSE` and `III` or `3`), then import all matching subjects, all detected subjects (`ALL_DETECTED`), or select specific subject numbers (including manual inclusion of previously excluded subjects). Other departments, semesters, and explicit open electives display clear exclusion reasons. Uncertain metadata is identified for manual review. Review options allow editing metadata, course types, unit titles, and topics, as well as manually adding missing subjects before approving into MySQL.

## Excel bulk student import

Menu option 15 enables high-speed bulk import of student academic profiles and internal marks from Excel (`.xlsx` or `.xls`) spreadsheets. Menu option 16 generates a pre-formatted, documented Excel template containing:
- 102 columns: Student Information (Roll Number, Full Name, CGPA, Previous SGPA) and 49 assessment columns per internal for all 7 theory subjects (Exam Marks, Assignment 1-3, Quiz 1-3).
- Embedded `Instructions` sheet explaining all scoring constraints and formats.
- Sample rows with verified data.

Validation rules:
1. Roll numbers must strictly match `1602-25-733-XXX`.
2. Roll numbers must be unique within the spreadsheet and not already exist in MySQL (overwrite protection).
3. CGPA and SGPA must be between 0.00 and 10.00.
4. Exam marks must not exceed subject maximums (30 for Java, DS, CA, TTPS; 20 for CS, CT, HVPE).
5. Assignment and quiz marks must be integers from 0 to 5.
6. Required fields (Roll Number, Name, CGPA, SGPA) cannot be missing.

The system performs pre-import validation, shows a detailed preview of valid vs invalid rows, allows exporting an `excel-import-errors.csv` report for review, and performs an atomic transactional batch insert into MySQL upon user confirmation.

## Full-Stack Architecture (Spring Boot & React)

In Phase 6 & 7, the project expanded to provide a modern, full-stack web application alongside the CLI console application.

### Backend Architecture
- **Framework**: Spring Boot 3.3.5 (Java 17)
- **Database**: Local MySQL 8.0 (`student_performance`) accessed via high-performance Spring JDBC without JPA overhead.
- **REST Endpoints**:
  - `GET /api/students`: Roster listing with computed performance metrics.
  - `GET /api/students/{rollNo}`: Individual student lookup.
  - `GET /api/students/{rollNo}/report`: Comprehensive academic diagnostic report with unit-level mapping alerts.
  - `POST /api/students/{rollNo}/ai-analysis`: Live Google Gemini AI academic diagnosis grounded in approved syllabus topics.
  - `GET /api/analytics/subjects`: Cohort health across all 7 theory subjects.
  - `GET /api/excel/template`: Official 102-column Excel bulk import template download.
  - `POST /api/excel/upload`: Spreadsheet validation, preview, and transactional commit with overwrite protection.
  - `GET /api/syllabus`: List approved syllabus documents and unit/topic trees.
  - `POST /api/syllabus/upload`: Multipart syllabus PDF extraction and grounding.
  - `POST /api/syllabus/{id}/approve`: Document approval.

### Frontend Architecture
- **Framework**: React with Vite
- **Styling**: Tailwind CSS
- **Visualization**: Recharts (Cohort distribution, subject averages, high-performers vs. struggling students)
- **Navigation**: React Router
- **HTTP Client**: Axios with centralized error interceptors and Vite proxy (`/api` -> `http://localhost:8080`)
- **Pages**:
  - `Dashboard`: Cohort KPIs, Recharts analytics, subject attention alerts, and priority monitoring roster.
  - `Students`: Direct roll number search and filterable student roster.
  - `Student Report`: Detailed subject breakdown, Internal 1 (Units 1-2) and Internal 2 (Units 3-5) alerts, and live Gemini AI analysis.
  - `Subject Analytics`: 7 core theory subjects deep-dive with high-performer / remediation charts.
  - `Excel Upload`: Drag-and-drop file upload, template download, validation preview, and commit actions.
  - `Syllabus Management`: Curriculum PDF upload and unit/topic tree inspector.

### How to Run

1. **Start Spring Boot Backend** (Port 8080):
   ```powershell
   $env:GEMINI_API_KEY = [System.Environment]::GetEnvironmentVariable("GEMINI_API_KEY", "User")
   mvn spring-boot:run
   ```

2. **Start React Frontend** (Port 5173):
   ```powershell
   cd frontend
   npm run dev
   ```
   Open `http://localhost:5173` in your browser.

3. **Run Console Interface** (Anytime):
   ```powershell
   mvn exec:java -Dexec.mainClass="studentperformance.StudentPerformanceSystem"
   ```

4. **Run Test Suite**:
   ```powershell
   mvn test
   ```

```

The unit tests cover score averages, subject trends, invalid values, and incomplete records. MySQL integration tests cover add/retrieve/update/delete, duplicate rolls, incomplete rows, migration repeatability, and connection failures. They run only when `PHASE2_TEST_DB_URL` points to a **dedicated test database whose name ends in `_test`**. Configure `PHASE2_TEST_DB_USER` and `PHASE2_TEST_DB_PASSWORD` as needed. Without that setting, JUnit skips database integration tests rather than touching a possibly real database.

## AI Educational Diagnosis & Personalized Learning Recommendations

The system provides intelligent, curriculum-aligned academic diagnosis and personalized learning guidance powered by **Google Gemini API** (primary provider leveraging Google's official free tier) with optional **OpenAI API** support.

### Strict Architectural Principles
1. **Deterministic Java Calculations**: AI never generates, recalculates, or alters raw marks, percentages, or GPA metrics. All numeric scoring is computed deterministically in Java.
2. **Zero PII Exposure**: Student names and roll numbers are strictly scrubbed before communicating with external AI models. Only anonymized academic performance metrics and curriculum topics are transmitted.
3. **Curriculum Alignment**: Diagnostic recommendations are directly linked with approved college syllabus units and topics from Vasavi College of Engineering.
4. **Structured JSON Responses**: AI outputs are strictly constrained to JSON schemas and validated before rendering.

### Primary Provider: Google Gemini API (Free Tier)
Gemini 1.5 Flash is the default model (`gemini-1.5-flash`), offering high throughput and generous free-tier limits (up to 15 requests per minute, 1,500 requests per day) without requiring paid billing.

#### Configuration Options

**Option 1: Windows PowerShell (Persistent for Current User)**
```powershell
[System.Environment]::SetEnvironmentVariable("GEMINI_API_KEY", "your_gemini_api_key_here", "User")
# Optional model override (defaults to gemini-1.5-flash)
[System.Environment]::SetEnvironmentVariable("GEMINI_MODEL", "gemini-1.5-flash", "User")
```

**Option 2: Local `.env` File (Git-ignored and Secure)**
Create a file named `.env` in the project root:
```properties
GEMINI_API_KEY=your_gemini_api_key_here
GEMINI_MODEL=gemini-1.5-flash
```

**Option 3: Command-line Flag via Maven**
```powershell
mvn exec:java -DGEMINI_API_KEY="your_gemini_api_key_here"
```

**Option 4: Interactive Entry**
If no key is configured in the environment, Option 11 prompts you to paste the key securely for that session without saving or logging it.

### Secondary Provider: OpenAI
To switch to OpenAI, configure:
```powershell
[System.Environment]::SetEnvironmentVariable("AI_PROVIDER", "openai", "User")
[System.Environment]::SetEnvironmentVariable("OPENAI_API_KEY", "your_openai_api_key_here", "User")
[System.Environment]::SetEnvironmentVariable("OPENAI_MODEL", "gpt-4o-mini", "User")
```

### Running the Live Gemini Connectivity & Verification Harness
A dedicated verification utility is provided to test genuine API connectivity, student diagnosis on a live MySQL record, and PDF generation:

```powershell
# Using environment key:
mvn exec:java "-Dexec.mainClass=studentperformance.GeminiLiveConnectivityTest"

# Or explicitly passing key:
mvn exec:java "-Dexec.mainClass=studentperformance.GeminiLiveConnectivityTest" "-DGEMINI_API_KEY=your_key_here"
```

The harness executes 7 verification steps:
1. Resolves and masks API key (never prints secrets).
2. Performs a live ping to Google Gemini endpoint and measures latency.
3. Connects to MySQL (`student_performance`) and selects a student record.
4. Sends anonymized academic metrics to Gemini.
5. Validates and parses all returned JSON fields into `AiAnalysisResult`.
6. Generates a formal advisory PDF report (`gemini-live-test-report.pdf`).
7. Confirms 100% preservation of database records (zero modifications/deletions).

## Main implementation files

- `StudentPerformanceSystem.java`: console menu, input, profile display, comparison, sorting, and confirmation.
- `StudentRepository.java`: JDBC CRUD, batch transactions, prepared statements, and incomplete-record loading.
- `DatabaseMigrator.java`: idempotent additive migration and raw legacy mark archival.
- `AiService.java`: clean abstraction interface for educational AI providers.
- `GeminiService.java`: Google Gemini API integration (v1beta REST API) using official free-tier models (`gemini-1.5-flash`).
- `OpenAIService.java`: OpenAI Chat Completions integration with structured JSON responses and PII scrubbing.
- `AiServiceFactory.java`: provider factory supporting Gemini and OpenAI with auto-detection.
- `AiPromptBuilder.java`: shared prompt generation, PII scrubbing, syllabus context embedding, and JSON parsing.
- `AiAnalysisResult.java`: structured domain model for AI academic evaluations, risk factors, and study plans.
- `SimpleJsonParser.java`: lightweight, zero-dependency JSON parser for AI API payload processing.
- `PdfReportGenerator.java`: Apache PDFBox report generator producing structured academic and advisory PDFs.
- `GeminiLiveConnectivityTest.java`: live verification utility for genuine Gemini connectivity, analysis, and PDF export.
- `ExcelImporter.java`: Apache POI Excel reader, 102-column template generator, pre-import validator, and error reporter.
- `Phase4Verification.java`: automated data integrity, calculation, ranking, and restart verification suite.
- `SyllabusParser.java`: PDFBox text extraction, professional/core/open elective categorization, and heuristic grouping.
- `SyllabusDocument.java`, `SyllabusSubject.java`, `SyllabusUnit.java`, `SyllabusTopic.java`: reviewable syllabus model.
- `SyllabusRepository.java`: transactional JDBC persistence and configurable unit mapping.
- `Student.java`, `Subject.java`, `SubjectAssessment.java`, `LegacyMark.java`: validated domain model and assessment details.
- `PerformanceAnalyzer.java`: deterministic score calculation, trends, rank eligibility, subject strengths, and support threshold.
- `CsvExporter.java`: CSV reporting handling full and partial records.
- `AiPredictionService.java`: legacy Responses API service with lazy client initialization.
- `database/schema.sql` and `database/migrations/`: fresh-install schema and safe legacy archive script.
- `src/test/java/studentperformance/`: 40 unit and integration tests covering scoring, Excel import, PDF syllabus parsing, mocked Gemini/OpenAI responses, and MySQL transactions.
