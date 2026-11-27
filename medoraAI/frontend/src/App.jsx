import { useState, useEffect } from 'react';
import RagQuery from './pages/RagQuery';
import KafkaMessage from './pages/KafkaMessage';
import NotificationTester from './pages/NotificationTester';
import AppointmentChat from './pages/AppointmentChat';
import WorkflowGraph from './pages/WorkflowGraph';
import './App.css';

function App() {
  const [activeTab, setActiveTab] = useState('rag');
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const tabs = [
    { id: 'rag', icon: '🔬', label: 'Disease Query' },
    { id: 'appointment', icon: '📅', label: 'Appointments' },
    { id: 'workflow', icon: '🔬', label: 'Workflow Graph' },
    { id: 'kafka', icon: '📨', label: 'Kafka Messages' },
    { id: 'notification', icon: '🔔', label: 'Notifications' },
  ];

  return (
    <div className="app">
      {/* Hero Header */}
      <header className={`header ${scrolled ? 'scrolled' : ''}`}>
        <div className="header-bg">
          <div className="orb orb-1"></div>
          <div className="orb orb-2"></div>
          <div className="orb orb-3"></div>
        </div>
        <div className="header-content">
          <div className="header-top-row">
            <div className="header-brand">
              <div className="logo-icon">
                <svg width="56" height="56" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
                  {/* Brain outline with glow animation */}
                  <path className="brain-outline" d="M24 6C18 6 13 9 11 14C9 11 5 12 5 16C3 18 4 22 7 23C6 26 7 30 10 32C12 35 16 36 20 36V40C20 41.1 20.9 42 22 42H26C27.1 42 28 41.1 28 40V36C32 36 36 35 38 32C41 30 42 26 41 23C44 22 45 18 43 16C43 12 39 11 37 14C35 9 30 6 24 6Z" stroke="white" strokeWidth="1.8" fill="white" fillOpacity="0.2"/>
                  {/* Brain folds */}
                  <path d="M24 6V42" stroke="white" strokeWidth="1.2" strokeOpacity="0.6" strokeLinecap="round"/>
                  <path d="M14 12C16 14 18 13 20 16" stroke="white" strokeWidth="1.2" strokeOpacity="0.7" strokeLinecap="round"/>
                  <path d="M12 20C15 21 17 19 20 22" stroke="white" strokeWidth="1.2" strokeOpacity="0.7" strokeLinecap="round"/>
                  <path d="M14 28C16 27 18 29 20 26" stroke="white" strokeWidth="1.2" strokeOpacity="0.7" strokeLinecap="round"/>
                  <path d="M34 12C32 14 30 13 28 16" stroke="white" strokeWidth="1.2" strokeOpacity="0.7" strokeLinecap="round"/>
                  <path d="M36 20C33 21 31 19 28 22" stroke="white" strokeWidth="1.2" strokeOpacity="0.7" strokeLinecap="round"/>
                  <path d="M34 28C32 27 30 29 28 26" stroke="white" strokeWidth="1.2" strokeOpacity="0.7" strokeLinecap="round"/>
                  {/* Neural connections with signal flow */}
                  <line className="neural-signal" x1="16" y1="16" x2="24" y2="18" stroke="white" strokeWidth="1" strokeOpacity="0.7"/>
                  <line className="neural-signal" x1="32" y1="16" x2="24" y2="18" stroke="white" strokeWidth="1" strokeOpacity="0.7"/>
                  <line className="neural-signal-slow" x1="14" y1="24" x2="24" y2="28" stroke="white" strokeWidth="1" strokeOpacity="0.7"/>
                  <line className="neural-signal-slow" x1="34" y1="24" x2="24" y2="28" stroke="white" strokeWidth="1" strokeOpacity="0.7"/>
                  <line className="neural-signal" x1="18" y1="30" x2="24" y2="28" stroke="white" strokeWidth="1" strokeOpacity="0.7"/>
                  <line className="neural-signal" x1="30" y1="30" x2="24" y2="28" stroke="white" strokeWidth="1" strokeOpacity="0.7"/>
                  <line className="neural-signal-slow" x1="16" y1="16" x2="14" y2="24" stroke="white" strokeWidth="0.8" strokeOpacity="0.5"/>
                  <line className="neural-signal-slow" x1="32" y1="16" x2="34" y2="24" stroke="white" strokeWidth="0.8" strokeOpacity="0.5"/>
                  <line className="neural-signal" x1="18" y1="30" x2="14" y2="24" stroke="white" strokeWidth="0.8" strokeOpacity="0.5"/>
                  <line className="neural-signal" x1="30" y1="30" x2="34" y2="24" stroke="white" strokeWidth="0.8" strokeOpacity="0.5"/>
                  <line className="neural-signal" x1="24" y1="18" x2="24" y2="28" stroke="white" strokeWidth="1" strokeOpacity="0.8"/>
                  {/* Animated neuron nodes - center (brightest) */}
                  <circle className="neuron-center" cx="24" cy="18" r="2.5" fill="white" fillOpacity="1">
                    <animate attributeName="r" values="2.5;3.5;2.5" dur="2s" repeatCount="indefinite"/>
                    <animate attributeName="fill-opacity" values="1;0.8;1" dur="2s" repeatCount="indefinite"/>
                  </circle>
                  <circle className="neuron-center-2" cx="24" cy="28" r="2" fill="white" fillOpacity="0.9">
                    <animate attributeName="r" values="2;2.8;2" dur="2.5s" repeatCount="indefinite"/>
                    <animate attributeName="fill-opacity" values="0.9;0.7;0.9" dur="2.5s" repeatCount="indefinite"/>
                  </circle>
                  {/* Animated neuron nodes - left hemisphere */}
                  <circle className="neuron-left-1" cx="16" cy="16" r="2" fill="white" fillOpacity="0.9">
                    <animate attributeName="r" values="2;3;2" dur="2s" repeatCount="indefinite" begin="0.2s"/>
                    <animate attributeName="fill-opacity" values="0.9;1;0.9" dur="2s" repeatCount="indefinite" begin="0.2s"/>
                  </circle>
                  <circle className="neuron-left-2" cx="14" cy="24" r="1.5" fill="white" fillOpacity="0.8">
                    <animate attributeName="r" values="1.5;2.3;1.5" dur="2.2s" repeatCount="indefinite" begin="0.4s"/>
                    <animate attributeName="fill-opacity" values="0.8;1;0.8" dur="2.2s" repeatCount="indefinite" begin="0.4s"/>
                  </circle>
                  <circle className="neuron-left-3" cx="18" cy="30" r="1.5" fill="white" fillOpacity="0.8">
                    <animate attributeName="r" values="1.5;2.3;1.5" dur="2.4s" repeatCount="indefinite" begin="0.6s"/>
                    <animate attributeName="fill-opacity" values="0.8;1;0.8" dur="2.4s" repeatCount="indefinite" begin="0.6s"/>
                  </circle>
                  {/* Animated neuron nodes - right hemisphere */}
                  <circle className="neuron-right-1" cx="32" cy="16" r="2" fill="white" fillOpacity="0.9">
                    <animate attributeName="r" values="2;3;2" dur="2s" repeatCount="indefinite" begin="0.7s"/>
                    <animate attributeName="fill-opacity" values="0.9;1;0.9" dur="2s" repeatCount="indefinite" begin="0.7s"/>
                  </circle>
                  <circle className="neuron-right-2" cx="34" cy="24" r="1.5" fill="white" fillOpacity="0.8">
                    <animate attributeName="r" values="1.5;2.3;1.5" dur="2.2s" repeatCount="indefinite" begin="0.9s"/>
                    <animate attributeName="fill-opacity" values="0.8;1;0.8" dur="2.2s" repeatCount="indefinite" begin="0.9s"/>
                  </circle>
                  <circle className="neuron-right-3" cx="30" cy="30" r="1.5" fill="white" fillOpacity="0.8">
                    <animate attributeName="r" values="1.5;2.3;1.5" dur="2.4s" repeatCount="indefinite" begin="1.1s"/>
                    <animate attributeName="fill-opacity" values="0.8;1;0.8" dur="2.4s" repeatCount="indefinite" begin="1.1s"/>
                  </circle>
                </svg>
              </div>
              <div className="brand-text">
                <h1>Medora AI <span className="logo-badge">Beta</span></h1>
                <p className="brand-tagline">Intelligent Healthcare Assistant</p>
              </div>
            </div>
            <div className="header-stats">
              <div className="header-stat">
                <span className="stat-dot green"></span>
                <span>RAG Engine</span>
              </div>
              <div className="header-stat">
                <span className="stat-dot blue"></span>
                <span>Kafka Streaming</span>
              </div>
              <div className="header-stat">
                <span className="stat-dot purple"></span>
                <span>AI Chat Bot</span>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Navigation */}
      <nav className={`nav ${scrolled ? 'elevated' : ''}`}>
        <div className="nav-scroll">
          {tabs.map(tab => (
            <button
              key={tab.id}
              className={activeTab === tab.id ? 'nav-btn active' : 'nav-btn'}
              onClick={() => setActiveTab(tab.id)}
            >
              <span className="nav-icon">{tab.icon}</span>
              <span className="nav-label">{tab.label}</span>
            </button>
          ))}
        </div>
      </nav>

      {/* Main Content */}
      <main className="main">
        <div className="page-transition">
          {activeTab === 'rag' && <RagQuery />}
          {activeTab === 'appointment' && <AppointmentChat />}
          {activeTab === 'workflow' && <WorkflowGraph />}
          {activeTab === 'kafka' && <KafkaMessage />}
          {activeTab === 'notification' && <NotificationTester />}
        </div>
      </main>

      {/* Footer */}
      <footer className="footer">
        <div className="footer-bg">
          <div className="orb orb-1"></div>
          <div className="orb orb-2"></div>
          <div className="orb orb-3"></div>
        </div>
        <div className="footer-content">
          <div className="footer-top">
            <div className="footer-brand">
              <div className="footer-logo">
                <svg width="40" height="40" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path className="brain-outline" d="M24 6C18 6 13 9 11 14C9 11 5 12 5 16C3 18 4 22 7 23C6 26 7 30 10 32C12 35 16 36 20 36V40C20 41.1 20.9 42 22 42H26C27.1 42 28 41.1 28 40V36C32 36 36 35 38 32C41 30 42 26 41 23C44 22 45 18 43 16C43 12 39 11 37 14C35 9 30 6 24 6Z" stroke="white" strokeWidth="1.8" fill="white" fillOpacity="0.2"/>
                  <path d="M24 6V42" stroke="white" strokeWidth="1.2" strokeOpacity="0.6" strokeLinecap="round"/>
                  <path d="M14 12C16 14 18 13 20 16" stroke="white" strokeWidth="1.2" strokeOpacity="0.7" strokeLinecap="round"/>
                  <path d="M12 20C15 21 17 19 20 22" stroke="white" strokeWidth="1.2" strokeOpacity="0.7" strokeLinecap="round"/>
                  <path d="M34 12C32 14 30 13 28 16" stroke="white" strokeWidth="1.2" strokeOpacity="0.7" strokeLinecap="round"/>
                  <path d="M36 20C33 21 31 19 28 22" stroke="white" strokeWidth="1.2" strokeOpacity="0.7" strokeLinecap="round"/>
                  <line className="neural-signal" x1="16" y1="16" x2="24" y2="18" stroke="white" strokeWidth="1" strokeOpacity="0.7"/>
                  <line className="neural-signal" x1="32" y1="16" x2="24" y2="18" stroke="white" strokeWidth="1" strokeOpacity="0.7"/>
                  <line className="neural-signal-slow" x1="14" y1="24" x2="24" y2="28" stroke="white" strokeWidth="1" strokeOpacity="0.7"/>
                  <line className="neural-signal-slow" x1="34" y1="24" x2="24" y2="28" stroke="white" strokeWidth="1" strokeOpacity="0.7"/>
                  <line className="neural-signal" x1="24" y1="18" x2="24" y2="28" stroke="white" strokeWidth="1" strokeOpacity="0.8"/>
                  <circle className="neuron-center" cx="24" cy="18" r="2.5" fill="white" fillOpacity="1">
                    <animate attributeName="r" values="2.5;3.5;2.5" dur="2s" repeatCount="indefinite"/>
                  </circle>
                  <circle className="neuron-center-2" cx="24" cy="28" r="2" fill="white" fillOpacity="0.9">
                    <animate attributeName="r" values="2;2.8;2" dur="2.5s" repeatCount="indefinite"/>
                  </circle>
                  <circle className="neuron-left-1" cx="16" cy="16" r="2" fill="white" fillOpacity="0.9">
                    <animate attributeName="r" values="2;3;2" dur="2s" repeatCount="indefinite" begin="0.2s"/>
                  </circle>
                  <circle className="neuron-right-1" cx="32" cy="16" r="2" fill="white" fillOpacity="0.9">
                    <animate attributeName="r" values="2;3;2" dur="2s" repeatCount="indefinite" begin="0.7s"/>
                  </circle>
                  <circle className="neuron-left-2" cx="14" cy="24" r="1.5" fill="white" fillOpacity="0.8">
                    <animate attributeName="r" values="1.5;2.3;1.5" dur="2.2s" repeatCount="indefinite" begin="0.4s"/>
                  </circle>
                  <circle className="neuron-right-2" cx="34" cy="24" r="1.5" fill="white" fillOpacity="0.8">
                    <animate attributeName="r" values="1.5;2.3;1.5" dur="2.2s" repeatCount="indefinite" begin="0.9s"/>
                  </circle>
                </svg>
              </div>
              <div className="footer-brand-text">
                <strong>Medora AI</strong>
                <span className="footer-tagline">Empowering Healthcare with AI</span>
              </div>
            </div>
            <div className="footer-links">
              <div className="footer-link-group">
                <h4>Platform</h4>
                <a href="#" onClick={() => setActiveTab('rag')}>Disease Query</a>
                <a href="#" onClick={() => setActiveTab('appointment')}>Appointments</a>
                <a href="#" onClick={() => setActiveTab('kafka')}>Kafka Streaming</a>
                <a href="#" onClick={() => setActiveTab('notification')}>Notifications</a>
              </div>
              <div className="footer-link-group">
                <h4>Company</h4>
                <a href="#">About Us</a>
                <a href="#">Careers</a>
                <a href="#">Blog</a>
                <a href="#">Contact</a>
              </div>
              <div className="footer-link-group">
                <h4>Legal</h4>
                <a href="#">Privacy Policy</a>
                <a href="#">Terms of Service</a>
                <a href="#">HIPAA Compliance</a>
              </div>
            </div>
          </div>
          <div className="footer-divider"></div>
          <div className="footer-bottom">
            <p className="footer-copy">© 2026 Medora AI — Proudly built in 🇮🇳 India</p>
            <p className="footer-mission">AI-powered healthcare for every Indian household</p>
          </div>
        </div>
      </footer>
    </div>
  );
}

export default App;
