import { useState, useEffect, useRef } from 'react';
import { askDiseaseQuestion, submitFeedback, getFeedbackSummary } from '../services/api';

function RagQuery() {
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [feedbackGiven, setFeedbackGiven] = useState(null); // 'positive' or 'negative'
  const [feedbackMsg, setFeedbackMsg] = useState('');
  const [summary, setSummary] = useState(null);
  const [loadingSummary, setLoadingSummary] = useState(true);
  const [voiceEnabled, setVoiceEnabled] = useState(true);
  const [isListening, setIsListening] = useState(false);
  const [autoVoice, setAutoVoice] = useState(false); // Hands-free mode disabled by default
  const recognitionRef = useRef(null);
  const silenceTimerRef = useRef(null);
  const autoSendTimerRef = useRef(null);
  const lastQuestionRef = useRef('');

  // Initialize speech recognition with continuous listening and auto-send
  useEffect(() => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (SpeechRecognition) {
      const recognition = new SpeechRecognition();
      recognition.continuous = true;
      recognition.interimResults = true;
      recognition.lang = 'en-US';

      recognition.onresult = (event) => {
        let finalTranscript = '';
        let interimTranscript = '';

        for (let i = event.resultIndex; i < event.results.length; i++) {
          const transcript = event.results[i][0].transcript;
          if (event.results[i].isFinal) {
            finalTranscript += transcript;
          } else {
            interimTranscript += transcript;
          }
        }

        const fullTranscript = finalTranscript + interimTranscript;
        setQuestion(fullTranscript);
        lastQuestionRef.current = fullTranscript;

        // Reset silence timer on new speech
        if (silenceTimerRef.current) {
          clearTimeout(silenceTimerRef.current);
          silenceTimerRef.current = null;
        }

        // Auto-send after 1.2 seconds of silence
        if (finalTranscript) {
          silenceTimerRef.current = setTimeout(() => {
            const questionToSend = finalTranscript.trim();
            if (questionToSend) {
              // Auto-submit the question
              handleAutoSubmit(questionToSend);
            }
            // Restart listening
            try {
              recognition.start();
              setIsListening(true);
            } catch (e) {}
          }, 1200);
        }
      };

      recognition.onerror = (event) => {
        if (event.error !== 'no-speech' && event.error !== 'aborted') {
          console.error('Speech recognition error:', event.error);
        }
        setIsListening(false);
        if (autoVoice) {
          setTimeout(() => {
            try {
              recognition.start();
              setIsListening(true);
            } catch (e) {}
          }, 1000);
        }
      };

      recognition.onend = () => {
        if (autoVoice && isListening) {
          try {
            recognition.start();
            setIsListening(true);
          } catch (e) {}
        } else {
          setIsListening(false);
        }
      };

      recognitionRef.current = recognition;
    }
  }, [autoVoice]);


  // Auto-submit question without form submission
  const handleAutoSubmit = async (questionText) => {
    if (!questionText.trim() || loading) return;
    setLoading(true);
    setError('');
    setAnswer(null);
    setFeedbackGiven(null);
    setFeedbackMsg('');

    try {
      const result = await askDiseaseQuestion(questionText);
      setAnswer(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // Speak the answer when it changes
  useEffect(() => {
    if (voiceEnabled && answer && window.speechSynthesis) {
      window.speechSynthesis.cancel();
      const cleanText = answer.answer
        .replace(/[💡📚✨⚠️👍👎]/g, '')
        .trim();
      const utterance = new SpeechSynthesisUtterance(cleanText);
      utterance.rate = 1.0;
      const voices = window.speechSynthesis.getVoices();
      const preferred = voices.find(v => v.name.includes('Google') && v.lang.startsWith('en'))
        || voices.find(v => v.lang.startsWith('en'));
      if (preferred) utterance.voice = preferred;
      window.speechSynthesis.speak(utterance);
    }
  }, [answer?.answer, voiceEnabled]);

  const toggleVoice = () => {
    if (voiceEnabled) {
      window.speechSynthesis?.cancel();
    }
    setVoiceEnabled(prev => !prev);
  };

  const toggleMicrophone = () => {
    if (isListening && recognitionRef.current) {
      recognitionRef.current.stop();
      setIsListening(false);
      setAutoVoice(false);
    } else if (recognitionRef.current && !isListening) {
      recognitionRef.current.start();
      setIsListening(true);
      setAutoVoice(true);
    }
  };

  const toggleAutoVoice = () => {
    setAutoVoice(prev => !prev);
    if (autoVoice && recognitionRef.current) {
      recognitionRef.current.stop();
      setIsListening(false);
    } else if (!autoVoice && recognitionRef.current) {
      try {
        recognitionRef.current.start();
        setIsListening(true);
      } catch (e) {}
    }
  };

  useEffect(() => {
    loadSummary();
  }, []);

  const loadSummary = async () => {
    try {
      const data = await getFeedbackSummary();
      setSummary(data);
    } catch (e) {
      // ignore on first load
    } finally {
      setLoadingSummary(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!question.trim()) return;

    setLoading(true);
    setError('');
    setAnswer(null);
    setFeedbackGiven(null);
    setFeedbackMsg('');

    try {
      const result = await askDiseaseQuestion(question);
      setAnswer(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleFeedback = async (rating) => {
    if (feedbackGiven) return;
    try {
      await submitFeedback(question, rating, '');
      setFeedbackGiven(rating);
      setFeedbackMsg(rating === 'positive' ? '👍 Thanks! This helps improve our answers.' : '👎 Sorry! We\'ll work on improving this.');
      loadSummary();
    } catch (err) {
      setFeedbackMsg('Failed to submit feedback.');
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h2>🔬 Disease Knowledge Base</h2>
        <p className="subtitle">Ask any question about diseases, symptoms, treatments, lab tests, or prevention — powered by AI.</p>
        <div className="voice-controls">
          <button
            className={`voice-toggle-btn ${voiceEnabled ? 'active' : ''}`}
            onClick={toggleVoice}
            title={voiceEnabled ? 'Mute voice' : 'Enable voice'}
          >
            {voiceEnabled ? '🔊 Voice On' : '🔇 Voice Off'}
          </button>
          {recognitionRef.current && (
            <button
              className={`auto-voice-btn ${autoVoice ? 'active' : ''}`}
              onClick={toggleAutoVoice}
              title={autoVoice ? 'Disable hands-free mode' : 'Enable hands-free mode'}
            >
              {autoVoice ? '🎙️ Hands-Free On' : '⌨️ Hands-Free Off'}
            </button>
          )}
          {recognitionRef.current && (
            <button
              className={`mic-btn ${isListening ? 'listening' : ''}`}
              onClick={toggleMicrophone}
              title={isListening ? 'Stop listening' : 'Start voice input'}
            >
              {isListening ? '⏹️' : '🎤'}
            </button>
          )}
          {isListening && (
            <span className="listening-indicator">🔴 Listening...</span>
          )}
        </div>
      </div>

      <form onSubmit={handleSubmit} className="form">
        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder={isListening ? '🎤 Listening... speak now...' : 'e.g., What are the symptoms of diabetes? How is asthma treated? What is normal creatinine?'}
          rows={3}
          className="input"
          disabled={isListening}
        />
        <div className="form-actions-row">
          {recognitionRef.current && (
            <button
              type="button"
              className={`mic-btn-inline ${isListening ? 'listening' : ''}`}
              onClick={toggleMicrophone}
              title={isListening ? 'Stop listening' : 'Voice input'}
            >
              {isListening ? '⏹️' : '🎤'}
            </button>
          )}
          <button type="submit" disabled={loading || !question.trim()} className="btn">
            {loading ? (
              <span className="btn-loading">
                <span className="spinner"></span>
                Searching Knowledge Base...
              </span>
            ) : (
              <span>🔍 Ask Question</span>
            )}
          </button>
        </div>
      </form>

      {error && <div className="error-box">❌ {error}</div>}

      {answer && (
        <div className="result-card animate-in">
          <div className="result-header">
            <h3>💡 Answer</h3>
            <div className="result-badges">
              <span className="confidence" style={{
                background: answer.confidence > 0.7 ? 'linear-gradient(135deg, #4caf50, #66bb6a)' : answer.confidence > 0.4 ? 'linear-gradient(135deg, #ff9800, #ffa726)' : 'linear-gradient(135deg, #f44336, #ef5350)'
              }}>
                {(answer.confidence * 100).toFixed(0)}% confident
              </span>
              {answer.autoAddedToKB && (
                <span className="kb-badge">
                  📚 Added to Knowledge Base
                </span>
              )}
            </div>
          </div>
          {answer.autoAddedToKB && (
            <div className="kb-notice">
              ✨ This information was fetched from open-source medical sources and saved to our knowledge base. Next time, it'll be answered faster from our local database!
            </div>
          )}
          <p className="answer-text">{answer.answer}</p>

          {answer.sources && answer.sources.length > 0 && (
            <div className="sources">
              <strong>📚 Sources:</strong>
              <ul>
                {answer.sources.map((src, i) => (
                  <li key={i}>{src}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Feedback buttons */}
          <div className="feedback-section">
            <span className="feedback-label">Was this answer helpful?</span>
            <div className="feedback-buttons">
              <button
                className={`feedback-btn positive ${feedbackGiven === 'positive' ? 'active' : ''}`}
                onClick={() => handleFeedback('positive')}
                disabled={!!feedbackGiven}
              >
                👍 Yes
              </button>
              <button
                className={`feedback-btn negative ${feedbackGiven === 'negative' ? 'active' : ''}`}
                onClick={() => handleFeedback('negative')}
                disabled={!!feedbackGiven}
              >
                👎 No
              </button>
            </div>
            {feedbackMsg && <p className="feedback-message">{feedbackMsg}</p>}
          </div>

          {answer.disclaimer && (
            <div className="disclaimer">⚠️ {answer.disclaimer}</div>
          )}
        </div>
      )}

      {/* Feedback Summary */}
      {summary && summary.totalFeedback > 0 && (
        <div className="feedback-summary animate-in">
          <h3>📊 Feedback Summary</h3>
          <div className="summary-stats">
            <div className="stat-card">
              <span className="stat-number">{summary.totalFeedback}</span>
              <span className="stat-label">Total Feedback</span>
            </div>
            <div className="stat-card positive">
              <span className="stat-number">{summary.positiveFeedback}</span>
              <span className="stat-label">Positive</span>
            </div>
            <div className="stat-card negative">
              <span className="stat-number">{summary.negativeFeedback}</span>
              <span className="stat-label">Negative</span>
            </div>
            <div className="stat-card rate">
              <span className="stat-number">{summary.satisfactionRate}%</span>
              <span className="stat-label">Satisfaction</span>
            </div>
          </div>
          {summary.topQuestions && summary.topQuestions.length > 0 && (
            <div className="top-questions">
              <strong>🔥 Popular Questions:</strong>
              <ul>
                {summary.topQuestions.map((tq, i) => (
                  <li key={i} onClick={() => setQuestion(tq.question)} className="top-question-item">
                    <span className="top-q-text">{tq.question}</span>
                    <span className="top-q-count">{tq.count} feedback</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      <div className="suggestions">
        <strong>💡 Try asking:</strong>
        <div className="suggestion-chips">
          {[
            'What are the symptoms of diabetes?',
            'How is asthma treated?',
            'What causes hypertension?',
            'How to prevent COPD?',
            'What are Alzheimer\'s early signs?',
            'What is normal creatinine level?',
            'What is infection?',
          ].map((q, i) => (
            <button key={i} className="chip" onClick={() => setQuestion(q)}>{q}</button>
          ))}
        </div>
      </div>
    </div>
  );
}

export default RagQuery;
