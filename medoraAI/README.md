# Medora AI - Kafka Producer, Consumer & RAG System

A Spring Boot application demonstrating Kafka message production and consumption with retry, Dead Letter Queue (DLQ), email/SMS notifications, and a **RAG (Retrieval-Augmented Generation) system** for patient disease Q&A — built for the Medora medical CRM platform.

## Tech Stack

### Backend
- **Java 21**
- **Spring Boot 4.1.1**
- **Spring Kafka 4.1.1** (Kafka Clients 4.2.1)
- **Spring Mail** (for email notifications)
- **Twilio API** (for SMS notifications — mock mode by default)
- **Apache PDFBox 3.0.1** (PDF parsing for RAG)
- **Apache Lucene 9.10.0** (full-text search for RAG)
- **LangChain4j 0.36.2** (AI services with tool-calling) — [GitHub](https://github.com/langchain4j/langchain4j)
- **LangGraph4j 1.9.2** (workflow state graphs for appointment bot) — [GitHub](https://github.com/langgraph4j/langgraph4j)
- **Maven**

### Frontend
- **React 19** (Vite build tool)
- **Fetch API** for REST communication
- **CORS-enabled** backend for local development

## Project Structure

```
src/main/java/com/medora/kafka/medoraAI/
├── config/
│   ├── KafkaTopicConfig.java        # Topic definitions (main, retry, DLQ)
│   └── KafkaConsumerConfig.java     # Retry topic configuration with exponential backoff
├── consumer/
│   ├── KafkaConsumerService.java    # Default consumer (group: medora-consumer-group)
│   ├── DoctorConsumer.java          # Doctor events consumer (group: medora-doctor-group-A)
│   ├── NotificationConsumer.java    # Notification consumer (group: medora-notification-group-B)
│   └── DeadLetterQueueConsumer.java # DLQ consumer for failed messages
├── model/
│   └── NotificationEvent.java       # Event model for notifications (assessment completion, etc.)
├── service/
│   ├── EmailService.java            # Sends email notifications via SMTP
│   └── SMSService.java              # Sends SMS notifications via Twilio (or mock)
├── producer/
│   └── KafkaProducerService.java    # Sends messages to medora-events topic
├── controller/
│   └── KafkaController.java         # REST endpoint: POST /api/kafka/send
├── rag/
│   ├── controller/
│   │   └── RagController.java       # REST endpoints: POST/GET /api/rag/ask
│   ├── model/
│   │   ├── DocumentChunk.java       # Text chunk extracted from PDF
│   │   ├── QuestionRequest.java     # Patient question request model
│   │   └── QuestionResponse.java    # Generated answer with sources
│   └── service/
│       ├── PdfDocumentLoader.java   # Loads and chunks PDF/text documents
│       ├── PdfGeneratorService.java # Generates PDF from text at startup
│       ├── DocumentIndexService.java# Lucene in-memory index management
│       └── RagService.java          # Core RAG pipeline orchestration
└── MedoraAiApplication.java         # Main entry point
```

### Frontend Structure

```
frontend/
├── src/
│   ├── services/
│   │   └── api.js               # API calls to backend REST endpoints
│   ├── pages/
│   │   ├── RagQuery.jsx         # Disease knowledge base Q&A page
│   │   ├── KafkaMessage.jsx     # Kafka message sender page
│   │   └── NotificationTester.jsx # Notification event simulator
│   ├── App.jsx                  # Main app with tab navigation
│   ├── App.css                  # Styles for all components
│   └── main.jsx                 # React entry point
├── package.json
└── vite.config.js
```

## Kafka Topics

| Topic | Partitions | Replicas | Purpose |
|-------|------------|----------|---------|
| `medora-events` | 3 | 1 | Main events topic |
| `medora-events-retry` | 3 | 1 | Automatic retry topic (exponential backoff) |
| `medora-events-dlq` | 3 | 1 | Dead Letter Queue for exhausted retries |

## How Retry & DLQ Works

When a consumer throws an exception while processing a message:

1. **Attempt 1** → Message is retried after **1 second**
2. **Attempt 2** → Message is retried after **2 seconds**
2. **Attempt 3** → Message is retried after **4 seconds**
3. **All retries exhausted** → Message is sent to `medora-events-dlq`

The `DeadLetterQueueConsumer` listens to the DLQ topic and logs failed messages for manual review.

### Configuration

Retry behavior is controlled in `application.properties`:

```properties
spring.kafka.retrytopics.retry-multiplier=2.0
spring.kafka.retrytopics.initial-delay=1000
spring.kafka.retrytopics.max-attempts=3
spring.kafka.retrytopics.max-delay=10000
spring.kafka.retrytopics.dlt-backoff-delay=5000
```

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+
- Apache Kafka broker running on `localhost:9092`

### Run Kafka Locally

```bash
# Using Docker Compose (example)
docker-compose up -d
```

### Build & Run

```bash
./mvnw clean package
java -jar target/medoraAI-0.0.1-SNAPSHOT.jar
```

Or run with Maven directly:

```bash
./mvnw spring-boot:run
```

The backend will start on **http://localhost:8080**.

## Running the React Frontend

### Prerequisites

- **Node.js 18+** and **npm** installed

### Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

The React dev server will start on **http://localhost:5173**.

### Full Stack — Running Both

Open two terminals:

**Terminal 1 — Backend:**
```bash
./mvnw spring-boot:run
```

**Terminal 2 — Frontend:**
```bash
cd frontend && npm run dev
```

Then open **http://localhost:5173** in your browser.

### UI Features

| Tab | Description |
|-----|-------------|
| 🔬 **Disease Query** | Ask questions about diseases, symptoms, treatments, and prevention. Answers are retrieved from the PDF knowledge base using Lucene search. |
| 📨 **Kafka Messages** | Send plain-text messages to the Kafka topic. All 3 consumer groups will receive them. |
| 🔔 **Notifications** | Simulate a doctor assessment completion event. Triggers email (SMTP) and SMS (Twilio/mock) notifications. |

### CORS Configuration

The backend includes `CorsConfig.java` that allows requests from `http://localhost:5173` (Vite dev server) and `http://localhost:3000` (production build).

### Configuration

Update `src/main/resources/application.properties` if your Kafka broker runs on a different address:

```properties
spring.kafka.bootstrap-servers=localhost:9092
```

## API Endpoints

### Send a Message

```bash
curl -X POST "localhost:8080/api/kafka/send?message=Hello%20Medora"
```

Response:
```
Message submitted to Kafka: Hello Medora
```

## Consumer Groups

Three independent consumer groups listen to the `medora-events` topic, each receiving its own copy of every message:

- `medora-consumer-group` — Default general-purpose consumer
- `medora-doctor-group-A` — Doctor-related event processing
- `medora-notification-group-B` — Email and SMS notifications

## Email & SMS Notifications

When a doctor assessment is completed, the system automatically sends email and SMS notifications to the patient.

### How it Works

1. A JSON message with `eventType: "doctor_assessment_completed"` is published to the `medora-events` Kafka topic.
2. The `NotificationConsumer` listens for these events and triggers both email and SMS notifications.
3. **Email** is sent via SMTP (configured in `application.properties`).
4. **SMS** is sent via Twilio API (or logged in mock mode if credentials are not configured).

### Email Configuration

Update `application.properties` with your SMTP settings:

```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
notification.email.from=noreply@medora.com
```

### SMS Configuration (Twilio)

Update `application.properties` with your Twilio credentials:

```properties
notification.sms.provider=twilio
notification.sms.account-sid=your-twilio-account-sid
notification.sms.auth-token=your-twilio-auth-token
notification.sms.from-number=+1234567890
```

To use **mock mode** (logs SMS instead of sending), set:

```properties
notification.sms.provider=mock
```

### Example: Sending an Assessment Completion Event

```bash
curl -X POST "localhost:8080/api/kafka/send" \
  -d "message={\"eventType\":\"doctor_assessment_completed\",\"patientName\":\"John Doe\",\"patientEmail\":\"john@example.com\",\"patientPhone\":\"+1234567890\",\"doctorName\":\"Dr. Smith\",\"assessmentSummary\":\"Patient is in good health. Follow-up in 6 months.\",\"timestamp\":\"2025-01-15T10:30:00\"}"
```

This will trigger:
- **Email** to `john@example.com` with the assessment summary.
- **SMS** to `+1234567890` with a short notification message.

### Notification Event JSON Format

```json
{
  "eventType": "doctor_assessment_completed",
  "patientName": "John Doe",
  "patientEmail": "john@example.com",
  "patientPhone": "+1234567890",
  "doctorName": "Dr. Smith",
  "assessmentSummary": "Patient is in good health. Follow-up in 6 months.",
  "timestamp": "2025-01-15T10:30:00"
}
```

### Supported Event Types

| Event Type | Description | Triggers |
|------------|-------------|----------|
| `doctor_assessment_completed` | Doctor finished an assessment | Email + SMS to patient |
| `appointment_reminder` | Upcoming appointment reminder | Email + SMS to patient |
| `general_notification` | General notification | Logged only |

## Testing

```bash
./mvnw test
```

The project includes Spring Kafka test support (`spring-boot-starter-kafka-test`) for integration testing with an embedded Kafka broker.

---

## RAG (Retrieval-Augmented Generation) System

The RAG system allows patients to ask questions about diseases and receive answers based on a comprehensive medical knowledge base stored as a PDF document.

### Architecture

```
Patient Question → RagController → RagService
                                        ├→ DocumentIndexService (Lucene Search)
                                        │       └→ Retrieve top-K relevant chunks
                                        └→ Generate Answer from retrieved context
                                                └→ Return answer + sources + confidence
```

### How it Works

1. **Startup**: The system loads the disease knowledge base PDF (`diseases_knowledge_base.pdf`), extracts text, splits it into chunks, and indexes them in an in-memory Lucene search index.
2. **Query**: When a patient asks a question, the system searches the index for the most relevant document chunks.
3. **Synthesis**: The retrieved chunks are analyzed and synthesized into a structured answer with symptoms, treatments, causes, and prevention information.
4. **Response**: The answer is returned with source references and a confidence level.

### Knowledge Base

The disease knowledge base covers **10 common diseases**:

| # | Disease | Topics Covered |
|---|---------|----------------|
| 1 | Diabetes Mellitus | Types, symptoms, diagnosis, treatment, complications |
| 2 | Hypertension | Classification, risk factors, medications, prevention |
| 3 | Asthma | Triggers, diagnosis, inhalers, action plans |
| 4 | Coronary Artery Disease | Angina, heart attack, procedures, lifestyle |
| 5 | COPD | Bronchitis, emphysema, oxygen therapy, rehabilitation |
| 6 | Alzheimer's Disease | Stages, cognitive decline, medications, caregiver support |
| 7 | Influenza (Flu) | Types, antivirals, vaccination, complications |
| 8 | Common Cold | Viruses, symptom relief, duration, when to see a doctor |
| 9 | Pneumonia | Bacterial/viral, antibiotics, hospitalization criteria |
| 10 | Depression | Types, therapy, medications, emergency help |

### API Endpoints

#### Ask a Question (POST — JSON)

```bash
curl -X POST "localhost:8080/api/rag/ask" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What are the symptoms of diabetes?",
    "patientName": "John Doe"
  }'
```

#### Ask a Question (GET — Simple)

```bash
curl "localhost:8080/api/rag/ask?question=How+is+asthma+treated?"
```

#### Health Check

```bash
curl "localhost:8080/api/rag/health"
```

### Example Response

```json
{
  "question": "What are the symptoms of diabetes?",
  "answer": "Based on our medical knowledge base, here is the information:\n\n**Symptoms:**\n  • Frequent urination (polyuria)\n  • Excessive thirst (polydipsia)\n  • Increased hunger (polyphagia)\n  • Unexplained weight loss\n  • Fatigue and weakness\n  • Blurred vision\n  • Slow-healing sores or cuts\n  • Frequent infections\n\n**Important:** This information is for educational purposes only.",
  "sources": [
    "1. DIABETES MELLITUS (Chunk #5)",
    "1. DIABETES MELLITUS (Chunk #6)"
  ],
  "confidence": "High",
  "disclaimer": "This information is for educational purposes only and should not replace professional medical advice."
}
```

### Adding More Knowledge

To expand the knowledge base:

1. Edit `src/main/resources/diseases/diseases_knowledge_base.pdf.txt`
2. Add new disease sections following the existing format
3. Restart the application — the PDF is regenerated and re-indexed automatically

### Response Confidence Levels

| Confidence | Meaning |
|------------|----------|
| **High** | 4+ relevant chunks found — comprehensive answer |
| **Medium** | 2-3 relevant chunks found — partial coverage |
| **Low** | 1 relevant chunk found — limited information |

### Sample Questions to Try

- "What are the symptoms of diabetes?"
- "How is asthma treated?"
- "What causes high blood pressure?"
- "How can I prevent pneumonia?"
- "What are the stages of Alzheimer's disease?"
- "What medications are used for depression?"
- "What are the risk factors for heart disease?"
- "How is COPD diagnosed?"

---

## Appointment Chat Bot (LangGraph4j Workflow + LangChain4j)

The appointment chat bot allows patients to **book, view, cancel, and reschedule** medical appointments through natural language conversation. It uses **LangGraph4j** for structured workflow management with a state graph, backed by **LangChain4j** with **Ollama** (local LLM).

**Project Links:**
- **LangGraph4j**: https://github.com/langgraph4j/langgraph4j
- **LangChain4j**: https://github.com/langchain4j/langchain4j
- **Ollama**: https://ollama.com

### Architecture: LangGraph4j State Graph

The bot uses a **LangGraph4j StateGraph** workflow with 4 nodes connected by conditional edges:

```
START → classify_intent → extract_data → [conditional edge]
                                    ↓
                    needs data? → yes → format_response → END (asks for missing info)
                    needs data? → no  → execute_action → format_response → END
```

**Workflow Nodes:**
1. **IntentClassifierNode** — Classifies user intent (create/list/cancel/reschedule) using rule-based matching + LLM fallback
2. **DataExtractorNode** — Extracts structured data (patient name, doctor, date, time) using regex + LLM entity extraction
3. **ActionExecutorNode** — Executes the actual DB operation (CRUD on `medical_appointment` table)
4. **ResponseFormatterNode** — Formats the final response with follow-up prompts when data is missing

**State Management:** `AppointmentState` extends LangGraph4j's `AgentState`, flowing through all nodes with accumulated data.

### Prerequisites

1. **Install Ollama**: https://ollama.com/download
2. **Pull the model**: `ollama pull llama3.2`
3. **Start Ollama**: `ollama serve` (runs on `http://localhost:11434`)

### How it Works

```
Patient Message → AppointmentChatController → AppointmentWorkflow (LangGraph4j)
                                                              ├→ classify_intent (rule + LLM)
                                                              ├→ extract_data (regex + LLM)
                                                              ├→ [conditional: missing data?]
                                                              ├→ execute_action (DB CRUD)
                                                              └→ format_response (final answer)
```

### API Endpoints

#### Chat with the Bot

```bash
curl -X POST "localhost:8080/api/appointment-chat/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "Book an appointment with Dr. Smith for tomorrow at 10am"}'
```

#### Get All Scheduled Appointments

```bash
curl "localhost:8080/api/appointment-chat/appointments"
```

#### Get Patient Appointments

```bash
curl "localhost:8080/api/appointment-chat/appointments/patient?patientName=John"
```

### Example Conversations

| User Message | Bot Action |
|--------------|------------|
| "Book an appointment with Dr. Smith for tomorrow at 10am" | Creates appointment, returns confirmation with ID |
| "Show my appointments" | Lists all active appointments for the patient |
| "Cancel appointment #1" | Cancels the appointment by ID |
| "Reschedule appointment #1 to next Monday at 2pm" | Updates date/time, marks as rescheduled |

### Database Schema

The `medical_appointment` table stores all appointments:

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGSERIAL | Unique appointment ID |
| `patient_name` | VARCHAR | Patient's full name |
| `patient_email` | VARCHAR | Patient's email (optional) |
| `patient_phone` | VARCHAR | Patient's phone (optional) |
| `doctor_name` | VARCHAR | Doctor's name |
| `appointment_date` | DATE | Scheduled date |
| `appointment_time` | TIME | Scheduled time |
| `reason` | TEXT | Reason for visit |
| `status` | VARCHAR | scheduled / cancelled / completed / rescheduled |

### React UI

The **"📅 Appointments"** tab in the React UI provides a chat interface with:
- **Quick action buttons** for common tasks
- **Conversation history** with message bubbles
- **Typing indicator** while the bot processes
- **Markdown formatting** for bold text and structured responses

---

## Voice Control

Both the **Disease Knowledge Base** and **Appointment Assistant** pages support full voice interaction using the browser's built-in Web Speech API — no external services required.

### Features

| Feature | Description |
|---------|-------------|
| **🔊 Text-to-Speech (TTS)** | The AI speaks every response aloud automatically |
| **🎤 Speech-to-Text (STT)** | Click the microphone button to speak your question |
| **⏹️ Stop Listening** | Click the stop button to end voice input |
| **🔇 Mute Toggle** | Turn voice on/off with the voice toggle button |
| **🌅 Startup Greeting** | The AI greets you with a time-appropriate message ("Good morning/afternoon/evening") when the chat loads |

### How It Works

```
User clicks 🎤 → Browser listens → Speech transcribed → Sent to AI → Response spoken aloud
```

1. **Startup Greeting**: When you open the Appointment Assistant tab, the AI says *"Hey, good morning! How can I help you?"* (or afternoon/evening based on the current time).
2. **Voice Input**: Click the 🎤 microphone button — the browser will ask for microphone permission. Speak your question, and it's automatically transcribed and sent.
3. **Voice Output**: Every AI response is spoken aloud using the browser's `SpeechSynthesis` API.
4. **Mute**: Click 🔊 Voice On/Off to toggle all voice output.

### Browser Support

- **Chrome/Edge**: Full support for both SpeechRecognition and SpeechSynthesis
- **Safari**: SpeechSynthesis works; SpeechRecognition may require enabling in experimental features
- **Firefox**: SpeechSynthesis works; SpeechRecognition is not supported (mic button won't appear)

### Technical Details

- Uses `window.SpeechRecognition` / `window.webkitSpeechRecognition` for speech-to-text
- Uses `window.speechSynthesis` for text-to-speech
- Prefers Google English voices when available
- Emojis and markdown are stripped before speaking for natural-sounding output
