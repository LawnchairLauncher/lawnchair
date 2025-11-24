import React, { useState, useEffect } from 'react';

export default function One() {
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);
  const [apiKey, setApiKey] = useState('');
  const [showKeyInput, setShowKeyInput] = useState(false);

  useEffect(() => {
    const saved = localStorage.getItem('claude_api_key');
    if (saved) setApiKey(saved);
    else setShowKeyInput(true);
  }, []);

  const saveApiKey = (key) => {
    localStorage.setItem('claude_api_key', key);
    setApiKey(key);
    setShowKeyInput(false);
  };

  const sendMessage = async () => {
    if (!input.trim() || !apiKey) return;

    const userMessage = { role: 'user', content: input };
    setMessages([...messages, userMessage]);
    setInput('');
    setLoading(true);

    try {
      const response = await fetch('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-api-key': apiKey,
          'anthropic-version': '2023-06-01'
        },
        body: JSON.stringify({
          model: 'claude-sonnet-4-20250514',
          max_tokens: 2048,
          messages: [...messages, userMessage]
        })
      });

      const data = await response.json();
      const assistantMessage = {
        role: 'assistant',
        content: data.content[0].text
      };
      setMessages([...messages, userMessage, assistantMessage]);
    } catch (error) {
      console.error('Error:', error);
      setMessages([...messages, userMessage, {
        role: 'assistant',
        content: 'Error connecting to One. Check your API key.'
      }]);
    }

    setLoading(false);
  };

  if (showKeyInput) {
    return (
      <div style={{
        minHeight: '100vh',
        background: '#0a0a0a',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontFamily: 'Roboto, sans-serif',
        color: '#e5e5e5',
        padding: '2rem'
      }}>
        <div style={{
          maxWidth: '400px',
          width: '100%',
          textAlign: 'center'
        }}>
          <h1 style={{
            fontSize: '3rem',
            fontWeight: '300',
            margin: '0 0 0.5rem 0',
            letterSpacing: '0.1em'
          }}>
            ONE
          </h1>
          <p style={{
            color: '#737373',
            fontSize: '0.875rem',
            marginBottom: '2rem',
            fontWeight: '300'
          }}>
            truth before narrative
          </p>
          <input
            type="password"
            placeholder="Claude API Key"
            onKeyPress={(e) => {
              if (e.key === 'Enter') saveApiKey(e.target.value);
            }}
            style={{
              width: '100%',
              background: '#171717',
              border: '1px solid #262626',
              borderRadius: '4px',
              padding: '1rem',
              color: '#e5e5e5',
              fontSize: '0.875rem',
              marginBottom: '1rem'
            }}
          />
          <button
            onClick={(e) => {
              const input = e.target.previousSibling;
              saveApiKey(input.value);
            }}
            style={{
              width: '100%',
              background: '#171717',
              border: '1px solid #10b981',
              borderRadius: '4px',
              padding: '1rem',
              color: '#10b981',
              fontSize: '0.875rem',
              cursor: 'pointer',
              fontWeight: '300',
              letterSpacing: '0.05em'
            }}
          >
            CONNECT
          </button>
          <p style={{
            color: '#404040',
            fontSize: '0.75rem',
            marginTop: '1rem',
            lineHeight: '1.5'
          }}>
            Your API key is stored locally in your browser.
            Get one at console.anthropic.com
          </p>
        </div>
      </div>
    );
  }

  return (
    <div style={{
      minHeight: '100vh',
      background: '#0a0a0a',
      display: 'flex',
      flexDirection: 'column',
      fontFamily: 'Roboto, sans-serif',
      color: '#e5e5e5'
    }}>
      {/* Header */}
      <header style={{
        padding: '1.5rem 2rem',
        borderBottom: '1px solid #171717',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center'
      }}>
        <h1 style={{
          fontSize: '1.5rem',
          fontWeight: '300',
          margin: 0,
          letterSpacing: '0.1em'
        }}>
          ONE
        </h1>
        <div style={{ display: 'flex', gap: '1rem', fontSize: '0.75rem', color: '#737373' }}>
          <a href="https://git.iotaverum.com" style={{ color: '#737373', textDecoration: 'none' }}>git</a>
          <a href="https://build.iotaverum.com" style={{ color: '#737373', textDecoration: 'none' }}>build</a>
          <button
            onClick={() => {
              localStorage.removeItem('claude_api_key');
              setShowKeyInput(true);
            }}
            style={{
              background: 'none',
              border: 'none',
              color: '#737373',
              cursor: 'pointer',
              padding: 0,
              fontSize: '0.75rem'
            }}
          >
            🥦
          </button>
        </div>
      </header>

      {/* Messages */}
      <div style={{
        flex: 1,
        overflowY: 'auto',
        padding: '2rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '1.5rem'
      }}>
        {messages.length === 0 && (
          <div style={{
            textAlign: 'center',
            color: '#404040',
            fontSize: '0.875rem',
            marginTop: '4rem'
          }}>
            What do you need?
          </div>
        )}

        {messages.map((msg, i) => (
          <div key={i} style={{
            alignSelf: msg.role === 'user' ? 'flex-end' : 'flex-start',
            maxWidth: '70%',
            background: msg.role === 'user' ? '#171717' : 'transparent',
            padding: msg.role === 'user' ? '1rem' : '0',
            borderRadius: '4px',
            fontSize: '0.875rem',
            lineHeight: '1.6',
            whiteSpace: 'pre-wrap'
          }}>
            {msg.content}
          </div>
        ))}

        {loading && (
          <div style={{
            alignSelf: 'flex-start',
            color: '#737373',
            fontSize: '0.875rem'
          }}>
            ...
          </div>
        )}
      </div>

      {/* Input */}
      <div style={{
        padding: '2rem',
        borderTop: '1px solid #171717'
      }}>
        <div style={{
          display: 'flex',
          gap: '1rem',
          maxWidth: '900px',
          margin: '0 auto'
        }}>
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyPress={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
              }
            }}
            placeholder="Ask One..."
            disabled={loading}
            style={{
              flex: 1,
              background: '#171717',
              border: '1px solid #262626',
              borderRadius: '4px',
              padding: '1rem',
              color: '#e5e5e5',
              fontSize: '0.875rem',
              outline: 'none'
            }}
          />
          <button
            onClick={sendMessage}
            disabled={loading || !input.trim()}
            style={{
              background: input.trim() && !loading ? '#10b981' : '#171717',
              border: 'none',
              borderRadius: '4px',
              padding: '1rem 2rem',
              color: input.trim() && !loading ? '#0a0a0a' : '#404040',
              fontSize: '0.875rem',
              cursor: input.trim() && !loading ? 'pointer' : 'not-allowed',
              fontWeight: '300',
              letterSpacing: '0.05em'
            }}
          >
            →
          </button>
        </div>
      </div>
    </div>
  );
}
