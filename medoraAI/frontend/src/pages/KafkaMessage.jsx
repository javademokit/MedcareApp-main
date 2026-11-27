import { useState } from 'react';
import { sendKafkaMessage } from '../services/api';

function KafkaMessage() {
  const [message, setMessage] = useState('');
  const [result, setResult] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!message.trim()) return;

    setLoading(true);
    setError('');
    setResult('');

    try {
      const res = await sendKafkaMessage(message);
      setResult(`✅ Message sent successfully!\n\n${res}`);
      setMessage('');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h2>📨 Kafka Message Sender</h2>
        <p className="subtitle">Publish events to the Kafka streaming platform — consumed by multiple services in real-time.</p>
      </div>

      <form onSubmit={handleSubmit} className="form">
        <textarea
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          placeholder="Type your event message here..."
          rows={3}
          className="input"
        />
        <button type="submit" disabled={loading || !message.trim()} className="btn">
          {loading ? (
            <span className="btn-loading">
              <span className="spinner"></span>
              Publishing...
            </span>
          ) : (
            <span>🚀 Publish Event</span>
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
        <strong>ℹ️ Info:</strong>
        <p>Messages are sent to the <code>medora-events</code> topic and consumed by 3 groups:</p>
        <ul>
          <li><strong>Default Group</strong> — General message logging</li>
          <li><strong>Doctor Group</strong> — Doctor assessment processing</li>
          <li><strong>Notification Group</strong> — Email & SMS notifications</li>
        </ul>
      </div>
    </div>
  );
}

export default KafkaMessage;
