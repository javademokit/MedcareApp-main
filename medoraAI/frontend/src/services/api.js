// API service for Medora AI backend
const BASE_URL = 'http://localhost:8080';

// Ask a disease question via RAG
export async function askDiseaseQuestion(question) {
  const response = await fetch(`${BASE_URL}/api/rag/ask?question=${encodeURIComponent(question)}`);
  if (!response.ok) {
    throw new Error('Failed to get answer from RAG service');
  }
  return response.json();
}

// Send a message to Kafka
export async function sendKafkaMessage(message) {
  const response = await fetch(`${BASE_URL}/api/kafka/send?message=${encodeURIComponent(message)}`, {
    method: 'POST',
  });
  if (!response.ok) {
    throw new Error('Failed to send Kafka message');
  }
  return response.text();
}

// Send a notification event via Kafka
export async function sendNotificationEvent(eventData) {
  const response = await fetch(`${BASE_URL}/api/kafka/send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `message=${encodeURIComponent(JSON.stringify(eventData))}`,
  });
  if (!response.ok) {
    throw new Error('Failed to send notification event');
  }
  return response.text();
}

// Submit feedback for a RAG answer
export async function submitFeedback(question, rating, comment) {
  const response = await fetch(`${BASE_URL}/api/feedback`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, rating, comment }),
  });
  if (!response.ok) {
    throw new Error('Failed to submit feedback');
  }
  return response.json();
}

// Get feedback summary
export async function getFeedbackSummary() {
  const response = await fetch(`${BASE_URL}/api/feedback/summary`);
  if (!response.ok) {
    throw new Error('Failed to get feedback summary');
  }
  return response.json();
}

// Chat with the appointment bot
export async function chatWithBot(sessionId, message) {
  const response = await fetch(`${BASE_URL}/api/appointment-chat/message`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId, message }),
  });
  if (!response.ok) {
    throw new Error('Failed to get response from chat bot');
  }
  return response.json();
}

// Get the LangGraph4j workflow graph as Mermaid diagram
export async function getWorkflowGraph() {
  const response = await fetch(`${BASE_URL}/api/appointment-chat/graph`);
  if (!response.ok) {
    throw new Error('Failed to fetch workflow graph');
  }
  return response.json();
}

// Simulate the workflow with step-by-step execution trace
export async function simulateWorkflow(message) {
  const response = await fetch(`${BASE_URL}/api/appointment-chat/simulate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }),
  });
  if (!response.ok) {
    throw new Error('Failed to simulate workflow');
  }
  return response.json();
}
