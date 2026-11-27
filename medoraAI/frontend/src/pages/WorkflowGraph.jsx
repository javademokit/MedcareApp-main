import { useState, useEffect, useRef } from 'react'
import mermaid from 'mermaid'
import { getWorkflowGraph, simulateWorkflow } from '../services/api'

export default function WorkflowGraph() {
  const [mermaidCode, setMermaidCode] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showCode, setShowCode] = useState(false)
  const graphRef = useRef(null)

  // Simulation state
  const [simMessage, setSimMessage] = useState('Book an appointment for John Doe with Dr. Smith for tomorrow at 10am')
  const [simulating, setSimulating] = useState(false)
  const [simResult, setSimResult] = useState(null)
  const [activeStep, setActiveStep] = useState(-1)
  const [animating, setAnimating] = useState(false)
  const animTimerRef = useRef(null)

  // Workflow node definitions
  const workflowNodes = [
    { id: 'START', name: 'Workflow Start', color: '#10b981', icon: '▶️', description: 'Entry point — user message enters the graph' },
    { id: 'classify_intent', name: 'Intent Classifier', color: '#3b82f6', icon: '🔍', description: 'Detects user intent: create, list, cancel, or reschedule' },
    { id: 'extract_data', name: 'Data Extractor', color: '#f59e0b', icon: '📝', description: 'Extracts patient name, doctor, date, time from the message' },
    { id: 'execute_action', name: 'Action Executor', color: '#ef4444', icon: '⚡', description: 'Performs CRUD on the medical_appointment table' },
    { id: 'format_response', name: 'Response Formatter', color: '#06b6d4', icon: '💬', description: 'Formats the final response with follow-up prompts' },
    { id: 'END', name: 'Workflow End', color: '#6366f1', icon: '🏁', description: 'Workflow completion — response returned to user' },
  ]

  const workflowEdges = [
    { from: 'START', to: 'classify_intent', type: 'solid' },
    { from: 'classify_intent', to: 'extract_data', type: 'solid' },
    { from: 'extract_data', to: 'execute_action', type: 'conditional', label: 'data complete' },
    { from: 'extract_data', to: 'format_response', type: 'conditional', label: 'needs follow-up' },
    { from: 'execute_action', to: 'format_response', type: 'solid' },
    { from: 'format_response', to: 'END', type: 'solid' },
  ]

  useEffect(() => {
    mermaid.initialize({
      startOnLoad: false,
      theme: 'default',
      flowchart: { useMaxWidth: true, htmlLabels: true },
    })

    async function loadGraph() {
      try {
        const data = await getWorkflowGraph()
        setMermaidCode(data.content || data.mermaid)
        setLoading(false)
      } catch (err) {
        setError(err.message || 'Failed to load workflow graph')
        setLoading(false)
      }
    }

    loadGraph()
    return () => {
      if (animTimerRef.current) clearInterval(animTimerRef.current)
    }
  }, [])

  useEffect(() => {
    if (mermaidCode && !showCode && graphRef.current) {
      graphRef.current.innerHTML = ''
      mermaid.run({
        nodes: [graphRef.current],
      })
    }
  }, [mermaidCode, showCode])

  // Handle simulation
  async function handleSimulate() {
    if (!simMessage.trim()) return
    setSimulating(true)
    setSimResult(null)
    setActiveStep(-1)
    setAnimating(false)

    try {
      const data = await simulateWorkflow(simMessage)
      setSimResult(data)
      
      // Start animation
      if (data.trace && data.trace.length > 0) {
        startAnimation(data.trace)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setSimulating(false)
    }
  }

  function startAnimation(trace) {
    setAnimating(true)
    setActiveStep(0)
    
    let step = 0
    animTimerRef.current = setInterval(() => {
      step++
      if (step >= trace.length) {
        clearInterval(animTimerRef.current)
        setAnimating(false)
        setActiveStep(trace.length - 1)
      } else {
        setActiveStep(step)
      }
    }, 1200)
  }

  function stopAnimation() {
    if (animTimerRef.current) {
      clearInterval(animTimerRef.current)
      setAnimating(false)
    }
  }

  function jumpToStep(step) {
    setActiveStep(step)
    if (animTimerRef.current) {
      clearInterval(animTimerRef.current)
      setAnimating(false)
    }
  }

  // Get which nodes are active based on current trace step
  function getActiveNodes() {
    if (!simResult || !simResult.trace || activeStep < 0) return new Set()
    const active = new Set()
    for (let i = 0; i <= activeStep; i++) {
      const step = simResult.trace[i]
      if (step.node) active.add(step.node)
    }
    return active
  }

  function getActiveEdges() {
    if (!simResult || !simResult.trace || activeStep < 0) return new Set()
    const active = new Set()
    for (let i = 0; i <= activeStep; i++) {
      const step = simResult.trace[i]
      if (step.nextNode) {
        active.add(`${step.node}→${step.nextNode}`)
      }
    }
    return active
  }

  const activeNodes = getActiveNodes()
  const activeEdges = getActiveEdges()
  const currentTraceStep = simResult && simResult.trace && activeStep >= 0 ? simResult.trace[activeStep] : null

  return (
    <div className="page-container">
      <div className="page-header">
        <h2>🔬 LangGraph4j Workflow Explorer</h2>
        <p>Interactive visualization of the appointment management state graph — see how LangGraph works step by step</p>
      </div>

      {/* Simulation Panel */}
      <div className="simulation-panel">
        <div className="simulation-header">
          <h3>⚡ Workflow Simulation</h3>
          <p>Enter a message and watch the workflow execute node by node</p>
        </div>
        
        <div className="simulation-input-row">
          <input
            className="input"
            type="text"
            value={simMessage}
            onChange={(e) => setSimMessage(e.target.value)}
            placeholder="Type a message to simulate..."
            disabled={simulating}
          />
          <button 
            className="btn-primary" 
            onClick={handleSimulate}
            disabled={simulating || !simMessage.trim()}
          >
            {simulating ? '⏳ Running...' : '▶️ Simulate'}
          </button>
          {animating && (
            <button className="btn-secondary" onClick={stopAnimation}>
              ⏸️ Pause
            </button>
          )}
        </div>

        {/* Quick examples */}
        <div className="simulation-examples">
          <span className="examples-label">Try:</span>
          {[
            'Book an appointment for John Doe with Dr. Smith for tomorrow at 10am',
            'Show my appointments for John Doe',
            'Cancel appointment #1',
            'I want to book an appointment',
          ].map((ex, i) => (
            <button key={i} className="example-chip" onClick={() => { setSimMessage(ex); setSimResult(null); setActiveStep(-1); }}>
              {ex.length > 40 ? ex.substring(0, 40) + '...' : ex}
            </button>
          ))}
        </div>
      </div>

      {/* Interactive Flow Diagram */}
      {simResult && (
        <div className="flow-diagram-container">
          <div className="flow-diagram-header">
            <h3>📊 Execution Flow</h3>
            <div className="flow-controls">
              {animating && (
                <span className="animating-badge">🔄 Animating...</span>
              )}
              {!animating && simResult.trace && (
                <span className="steps-badge">Step {activeStep + 1} / {simResult.trace.length}</span>
              )}
            </div>
          </div>

          {/* Node visualization */}
          <div className="flow-nodes">
            {workflowNodes.map((node, i) => {
              const isActive = activeNodes.has(node.id)
              const isCurrent = currentTraceStep && currentTraceStep.node === node.id && activeStep === simResult.trace.findIndex(s => s.node === node.id)
              const nodeDelay = i * 0.1
              
              return (
                <div 
                  key={node.id}
                  className={`flow-node ${isActive ? 'active' : ''} ${isCurrent ? 'current' : ''}`}
                  style={{ 
                    '--node-color': node.color,
                    animationDelay: `${nodeDelay}s`
                  }}
                  onClick={() => {
                    const traceIndex = simResult.trace.findIndex(s => s.node === node.id)
                    if (traceIndex >= 0) jumpToStep(traceIndex)
                  }}
                >
                  <div className="flow-node-icon">{node.icon}</div>
                  <div className="flow-node-content">
                    <div className="flow-node-name">{node.name}</div>
                    <div className="flow-node-desc">{node.description}</div>
                  </div>
                  {isActive && <div className="flow-node-check">✓</div>}
                  {isCurrent && <div className="flow-node-pulse"></div>}
                </div>
              )
            })}
          </div>

          {/* Edge connections visualization */}
          <div className="flow-edges">
            {workflowEdges.map((edge, i) => {
              const edgeKey = `${edge.from}→${edge.to}`
              const isActive = activeEdges.has(edgeKey)
              const isConditional = edge.type === 'conditional'
              
              return (
                <div 
                  key={i}
                  className={`flow-edge ${isActive ? 'active' : ''} ${isConditional ? 'conditional' : ''}`}
                >
                  <div className="flow-edge-line">
                    {isActive && <div className="flow-edge-progress"></div>}
                  </div>
                  <div className="flow-edge-label">
                    {workflowNodes.find(n => n.id === edge.from)?.icon} → {workflowNodes.find(n => n.id === edge.to)?.icon}
                    {isConditional && <span className="edge-condition">{edge.label}</span>}
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Step-by-step trace details */}
      {simResult && simResult.trace && currentTraceStep && (
        <div className="trace-details">
          <h3>📋 Current Step: {currentTraceStep.nodeName}</h3>
          <div className="trace-step-card">
            <div className="trace-step-header">
              <span className="trace-step-number">Step {activeStep + 1}</span>
              <span className="trace-step-node">{currentTraceStep.node}</span>
            </div>
            <div className="trace-step-description">{currentTraceStep.description}</div>
            
            {currentTraceStep.stateBefore && Object.keys(currentTraceStep.stateBefore).length > 0 && (
              <div className="trace-state">
                <strong>State:</strong>
                <pre>{JSON.stringify(currentTraceStep.stateBefore, null, 2)}</pre>
              </div>
            )}
            
            {currentTraceStep.result && Object.keys(currentTraceStep.result).length > 0 && (
              <div className="trace-result">
                <strong>Output:</strong>
                <pre>{JSON.stringify(currentTraceStep.result, null, 2)}</pre>
              </div>
            )}
            
            {currentTraceStep.nextNode && (
              <div className="trace-next">
                <strong>Next:</strong> {currentTraceStep.nextNode}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Simulation response */}
      {simResult && simResult.response && (
        <div className="result-card success">
          <div className="result-header">
            <div className="result-status">✅ Workflow Response</div>
          </div>
          <div className="result-answer" style={{ whiteSpace: 'pre-wrap' }}>
            {simResult.response}
          </div>
        </div>
      )}

      {/* Step navigation */}
      {simResult && simResult.trace && simResult.trace.length > 0 && (
        <div className="step-navigation">
          <button 
            className="btn-secondary" 
            onClick={() => jumpToStep(Math.max(0, activeStep - 1))}
            disabled={activeStep <= 0}
          >
            ← Previous
          </button>
          <div className="step-dots">
            {simResult.trace.map((step, i) => (
              <button
                key={i}
                className={`step-dot ${i === activeStep ? 'active' : ''} ${i < activeStep ? 'done' : ''}`}
                onClick={() => jumpToStep(i)}
                title={step.nodeName}
              >
                {workflowNodes.find(n => n.id === step.node)?.icon || '•'}
              </button>
            ))}
          </div>
          <button 
            className="btn-secondary" 
            onClick={() => jumpToStep(Math.min(simResult.trace.length - 1, activeStep + 1))}
            disabled={activeStep >= simResult.trace.length - 1}
          >
            Next →
          </button>
        </div>
      )}

      {/* Mermaid Diagram (toggleable) */}
      <div className="graph-controls">
        <button className="btn-secondary" onClick={() => setShowCode(!showCode)}>
          {showCode ? 'Show Diagram' : 'Show Mermaid Code'}
        </button>
        {mermaidCode && (
          <button
            className="btn-secondary"
            onClick={() => {
              navigator.clipboard.writeText(mermaidCode)
              alert('Mermaid code copied to clipboard!')
            }}
          >
            Copy Code
          </button>
        )}
      </div>

      {loading && (
        <div className="loading-container">
          <div className="spinner"></div>
          <p>Loading workflow graph...</p>
        </div>
      )}

      {error && !simResult && (
        <div className="result-card error">
          <div className="result-status">Error</div>
          <p>{error}</p>
        </div>
      )}

      {mermaidCode && !loading && (
        <>
          {!showCode && (
            <div className="graph-container">
              <div ref={graphRef} className="mermaid-diagram"></div>
            </div>
          )}
          {showCode && (
            <pre className="code-block">{mermaidCode}</pre>
          )}
        </>
      )}

      {/* Workflow Legend */}
      <div className="workflow-legend">
        <h3>📖 How LangGraph Works</h3>
        <div className="legend-grid">
          {workflowNodes.map(node => (
            <div key={node.id} className="legend-item">
              <span className="legend-icon" style={{ background: node.color }}>{node.icon}</span>
              <div>
                <strong>{node.name}</strong>
                <p>{node.description}</p>
              </div>
            </div>
          ))}
        </div>
        <div className="legend-note">
          <strong>Conditional edges</strong> (dotted lines): After extracting data, the workflow checks if all required fields are present. If data is missing, it routes to <code>format_response</code> to ask follow-up questions. If complete, it routes to <code>execute_action</code> to perform the operation.
        </div>
        <div className="legend-note">
          <strong>State management:</strong> Each node receives the current state, processes it, and returns state updates. The state flows through the graph, accumulating data at each step — this is the core concept of LangGraph's StateGraph.
        </div>
      </div>
    </div>
  )
}
