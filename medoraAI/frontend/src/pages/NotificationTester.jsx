import { useState } from 'react';
import { sendNotificationEvent } from '../services/api';

function NotificationTester() {
  const [patientName, setPatientName] = useState('John Doe');
  const [patientEmail, setPatientEmail] = useState('john@example.com');
  const [patientPhone, setPatientPhone] = useState('+1234567890');
  const [doctorName, setDoctorName] = useState('Dr. Smith');
  const [assessmentSummary, setAssessmentSummary] = useState('Patient is in good health. Follow-up in 6 months.');
  const [result, setResult] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setResult('');

    const eventData = {
      eventType: 'doctor_assessment_completed',
      patientName,
      patientEmail,
      patientPhone,
      doctorName,
      assessmentSummary,
      timestamp: new Date().toISOString(),
    };

    try {
      const res = await sendNotificationEvent(eventData);
      setResult(`✅ Notification event sent successfully!\n\n${res}\n\nThis will trigger:\n• Email to: ${patientEmail}\n• SMS to: ${patientPhone}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h2>🔔 Notification Tester</h2>
        <p className="subtitle">
          Simulate healthcare events that trigger real-time email and SMS notifications via Kafka streaming.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="form notification-form">
        <div className="form-row">
          <label>
            Patient Name
            <input type="text" value={patientName} onChange={(e) => setPatientName(e.target.value)} className="input" />
          </label>
          <label>
            Patient Email
            <input type="email" value={patientEmail} onChange={(e) => setPatientEmail(e.target.value)} className="input" />
          </label>
        </div>

        <div className="form-row">
          <label>
            Patient Phone
            <input type="text" value={patientPhone} onChange={(e) => setPatientPhone(e.target.value)} className="input" />
          </label>
          <label>
            Doctor Name
            <input type="text" value={doctorName} onChange={(e) => setDoctorName(e.target.value)} className="input" />
          </label>
        </div>

        <label>
          Assessment Summary
          <textarea
            value={assessmentSummary}
            onChange={(e) => setAssessmentSummary(e.target.value)}
            rows={3}
            className="input"
          />
        </label>

        <button type="submit" disabled={loading} className="btn">
          {loading ? (
            <span className="btn-loading">
              <span className="spinner"></span>
              Sending...
            </span>
          ) : (
            <span>📧 Send Notification Event</span>
          )}
        </button>
      </form>

      {error && <div className="error-box">❌ {error}</div>}

      {result && (
        <div className="result-card animate-in">
          <pre className="result-text">{result}</pre>
        </div>
      )}

      <div className="info-box">
        <strong>ℹ️ How it works:</strong>
        <ul>
          <li>A JSON event is published to the <code>medora-events</code> Kafka topic</li>
          <li>The Notification Consumer detects <code>doctor_assessment_completed</code> events</li>
          <li>Email is sent via SMTP (configure in <code>application.properties</code>)</li>
          <li>SMS is sent via Twilio (or mock mode if credentials are missing)</li>
        </ul>
      </div>
    </div>
  );
}

export default NotificationTester;
