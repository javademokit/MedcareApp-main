import { useState, useRef, useEffect } from 'react';
import DatePicker from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';
import { chatWithBot } from '../services/api';

export default function AppointmentChat() {
  const [messages, setMessages] = useState([
    {
      role: 'bot',
      text: '👋 Hello! I\'m your Medora AI appointment assistant. I can help you:\n\n• 📅 **Book** a new appointment\n• 📋 **View** your appointments\n• ❌ **Cancel** an appointment\n• 🔄 **Reschedule** an appointment\n\nJust tell me what you need! For example: "Book an appointment with Dr. Smith for tomorrow at 10am."'
    }
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [sessionId] = useState(() => crypto.randomUUID());
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [selectedDate, setSelectedDate] = useState(null);
  const [voiceEnabled, setVoiceEnabled] = useState(true);
  const [isListening, setIsListening] = useState(false);
  const [autoVoice, setAutoVoice] = useState(false); // Hands-free mode disabled by default
  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);
  const datePickerRef = useRef(null);
  const recognitionRef = useRef(null);
  const silenceTimerRef = useRef(null);
  const hasSpeechRef = useRef(false);

  // Auto-send a message if the input field has text and user hasn't typed for 1.5 seconds
  const autoSendTimerRef = useRef(null);
  const lastInputRef = useRef('');

  // Initialize speech recognition with auto-send on silence
  useEffect(() => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (SpeechRecognition) {
      const recognition = new SpeechRecognition();
      recognition.continuous = true; // Keep listening continuously
      recognition.interimResults = true; // Show results as you speak
      recognition.lang = 'en-US';

      let interimTranscript = '';

      recognition.onresult = (event) => {
        let finalTranscript = '';
        interimTranscript = '';

        for (let i = event.resultIndex; i < event.results.length; i++) {
          const transcript = event.results[i][0].transcript;
          if (event.results[i].isFinal) {
            finalTranscript += transcript;
          } else {
            interimTranscript += transcript;
          }
        }

        // Combine final + interim for live preview
        const fullTranscript = finalTranscript + interimTranscript;
        setInput(fullTranscript);
        lastInputRef.current = fullTranscript;

        // Reset silence timer on new speech
        if (silenceTimerRef.current) {
          clearTimeout(silenceTimerRef.current);
          silenceTimerRef.current = null;
        }
        hasSpeechRef.current = true;

        // If we got a final result, start the silence timer for auto-send
        if (finalTranscript) {
          silenceTimerRef.current = setTimeout(() => {
            // Auto-send the final transcript after 1.2 seconds of silence
            const messageToSend = finalTranscript.trim();
            if (messageToSend) {
              handleVoiceMessage(messageToSend);
            }
            // Restart listening for the next turn
            try {
              recognition.start();
              setIsListening(true);
            } catch (e) {
              // Already started
            }
          }, 1200);
        }
      };

      recognition.onerror = (event) => {
        if (event.error !== 'no-speech' && event.error !== 'aborted') {
          console.error('Speech recognition error:', event.error);
        }
        setIsListening(false);
        // Auto-restart after error
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
        // If auto-voice is on, restart listening
        if (autoVoice && isListening) {
          try {
            recognition.start();
            setIsListening(true);
          } catch (e) {
            // Already started
          }
        } else {
          setIsListening(false);
        }
      };

      recognitionRef.current = recognition;
    }
  }, [autoVoice]);

  // Speak function — defined first so useEffects can use it
  const speak = (text, enabled) => {
    const voiceOn = enabled !== undefined ? enabled : voiceEnabled;
    if (!voiceOn || !window.speechSynthesis) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = 1.0;
    utterance.pitch = 1.0;
    utterance.volume = 1.0;
    const voices = window.speechSynthesis.getVoices();
    const preferred = voices.find(v => v.name.includes('Google') && v.lang.startsWith('en'))
      || voices.find(v => v.lang.startsWith('en'));
    if (preferred) utterance.voice = preferred;
    window.speechSynthesis.speak(utterance);
  };

  // Startup greeting with voice
  useEffect(() => {
    if (!window.speechSynthesis) return;
    const hour = new Date().getHours();
    let greeting;
    if (hour < 12) greeting = 'Hey, good morning!';
    else if (hour < 17) greeting = 'Hey, good afternoon!';
    else greeting = 'Hey, good evening!';

    // Small delay so the page loads first
    setTimeout(() => {
      speak(greeting + ' How can I help you?', true);
    }, 1500);
  }, []); // Only run once on mount

  // Speak bot responses
  useEffect(() => {
    if (!voiceEnabled || messages.length === 0) return;
    const lastMsg = messages[messages.length - 1];
    if (lastMsg.role === 'bot' && !lastMsg.spoken) {
      // Mark as spoken to avoid re-speaking
      setMessages(prev => prev.map((m, i) =>
        i === messages.length - 1 ? { ...m, spoken: true } : m
      ));
      // Clean the text for speech (remove emojis and markdown)
      const cleanText = lastMsg.text
        .replace(/[👋📅📋❌🔄🤖👤⏳📤✨📚🔊🔇🎤⏹️🔴]/g, '')
        .replace(/\*\*(.*?)\*\*/g, '$1')
        .replace(/\n/g, '. ')
        .trim();
      speak(cleanText, voiceEnabled);
    }
  }, [messages.length, voiceEnabled]);

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

  const handleVoiceMessage = (transcript) => {
    if (!transcript.trim() || loading) return;
    const userMessage = transcript.trim();
    setInput('');
    setMessages(prev => [...prev, { role: 'user', text: userMessage }]);
    setLoading(true);

    chatWithBot(sessionId, userMessage)
      .then(response => {
        setMessages(prev => [...prev, { role: 'bot', text: response.response, spoken: false }]);
      })
      .catch(error => {
        setMessages(prev => [...prev, {
          role: 'bot',
          text: '❌ Sorry, I encountered an error. Please try again.\n\nError: ' + (error.message || 'Unknown error'),
          spoken: false
        }]);
      })
      .finally(() => setLoading(false));
  };

  const sendMessage = async () => {
    if (!input.trim() || loading) return;

    const userMessage = input.trim();
    setInput('');
    setMessages(prev => [...prev, { role: 'user', text: userMessage }]);
    setLoading(true);

    try {
      const response = await chatWithBot(sessionId, userMessage);
      setMessages(prev => [...prev, { role: 'bot', text: response.response, spoken: false }]);
    } catch (error) {
      setMessages(prev => [...prev, {
        role: 'bot',
        text: '❌ Sorry, I encountered an error. Please try again.\n\nError: ' + (error.message || 'Unknown error'),
        spoken: false
      }]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  const handleDateSelect = (date) => {
    if (date) {
      const dateStr = date.toISOString().split('T')[0];
      setShowDatePicker(false);
      setSelectedDate(date);
      setMessages(prev => [...prev, { role: 'user', text: `📅 ${dateStr}` }]);
      setLoading(true);
      chatWithBot(sessionId, dateStr)
        .then(response => {
          setMessages(prev => [...prev, { role: 'bot', text: response.response, spoken: false }]);
        })
        .catch(error => {
          setMessages(prev => [...prev, {
            role: 'bot',
            text: '❌ Error: ' + (error.message || 'Unknown error'),
            spoken: false
          }]);
        })
        .finally(() => setLoading(false));
    }
  };

  const toggleDatePicker = () => {
    setShowDatePicker(!showDatePicker);
  };

  const quickActions = [
    { label: '📅 Book Appointment', text: 'I want to book a medical appointment' },
    { label: '📋 My Appointments', text: 'Show me my appointments' },
    { label: '❌ Cancel', text: 'I want to cancel an appointment' },
    { label: '🔄 Reschedule', text: 'I want to reschedule an appointment' }
  ];


  return (
    <div className="chat-page">
      <div className="page-header">
        <h2>📅 Appointment Assistant</h2>
        <p>Book, view, cancel, or reschedule appointments — powered by LangGraph4j AI workflow</p>
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

      <div className="chat-container">
        <div className="chat-messages">
          {messages.map((msg, i) => (
            <div key={i} className={`message ${msg.role}`}>
              <div className="message-avatar">
                {msg.role === 'bot' ? '🤖' : '👤'}
              </div>
              <div className="message-content">
                <div className="message-text">{formatMessage(msg.text)}</div>
                <div className="message-time">
                  {new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  {msg.role === 'bot' && msg.spoken && <span className="spoken-badge">🔊</span>}
                </div>
              </div>
            </div>
          ))}
          {loading && (
            <div className="message bot typing">
              <div className="message-avatar">🤖</div>
              <div className="message-content">
                <div className="typing-indicator">
                  <span></span><span></span><span></span>
                </div>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        <div className="quick-actions">
          {quickActions.map((action, i) => (
            <button
              key={i}
              className="quick-action-btn"
              onClick={() => {
                setInput(action.text);
                setMessages(prev => [...prev, { role: 'user', text: action.text }]);
                setLoading(true);
                chatWithBot(sessionId, action.text)
                  .then(response => {
                    setMessages(prev => [...prev, { role: 'bot', text: response.response, spoken: false }]);
                  })
                  .catch(error => {
                    setMessages(prev => [...prev, {
                      role: 'bot',
                      text: '❌ Error: ' + (error.message || 'Unknown error'),
                      spoken: false
                    }]);
                  })
                  .finally(() => setLoading(false));
                setInput('');
              }}
              disabled={loading}
            >
              {action.label}
            </button>
          ))}
        </div>

        <div className="chat-input-area">
          <div className="chat-input-wrapper">
            <textarea
              ref={inputRef}
              className="chat-input"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={isListening ? '🎤 Listening... speak now...' : 'Type your message... (e.g., "Book an appointment with Dr. Smith for tomorrow at 2pm")'}
              rows={2}
              disabled={loading || isListening}
            />
            <div className="input-actions">
              <button
                className="calendar-btn"
                onClick={toggleDatePicker}
                title="Select date from calendar"
                disabled={loading}
              >
                📅
              </button>
              {recognitionRef.current && (
                <button
                  className={`mic-btn-inline ${isListening ? 'listening' : ''}`}
                  onClick={toggleMicrophone}
                  title={isListening ? 'Stop listening' : 'Voice input'}
                  disabled={loading}
                >
                  {isListening ? '⏹️' : '🎤'}
                </button>
              )}
              <button
                className="send-btn"
                onClick={sendMessage}
                disabled={!input.trim() || loading}
              >
                {loading ? '⏳' : '📤'}
              </button>
            </div>
          </div>
          {showDatePicker && (
            <div className="date-picker-popup" ref={datePickerRef}>
              <DatePicker
                selected={selectedDate}
                onChange={handleDateSelect}
                dateFormat="yyyy-MM-dd"
                minDate={new Date()}
                placeholderText="Select a date..."
                inline
              />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function formatMessage(text) {
  return text.split('\n').map((line, i) => {
    let formatted = line.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    return <div key={i} dangerouslySetInnerHTML={{ __html: formatted || '&nbsp;' }} />;
  });
}
