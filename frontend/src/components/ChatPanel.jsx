import { useState, useEffect, useRef } from 'react'
import { Send } from 'lucide-react'
import Spinner from './Spinner.jsx'

const MAX_LENGTH = 500
const COUNTER_THRESHOLD = 50

function formatTime(timestamp) {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
}

export default function ChatPanel({ messages, onSendMessage, currentUserId, connectionStatus, userDirectory = {} }) {
  const [text, setText] = useState('')
  const bottomRef = useRef(null)
  const isConnected = connectionStatus === 'connected'
  const remaining = MAX_LENGTH - text.length

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSubmit = (e) => {
    e.preventDefault()
    const trimmed = text.trim()
    if (!trimmed || !isConnected) return
    onSendMessage(trimmed)
    setText('')
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit(e)
    }
  }

  return (
    <div className="card border-border flex flex-col" style={{ minHeight: 0 }}>
      <p className="label-field mb-2 flex items-center gap-2">
        Chat
        {connectionStatus === 'connecting' && <Spinner size="sm" />}
      </p>

      {/* Messages area — fixed height to prevent layout jump */}
      <div
        role="log"
        aria-live="polite"
        aria-label="Mensajes del chat"
        className="overflow-y-auto mb-3 space-y-1"
        style={{ height: 320 }}
      >
        {messages.length === 0 ? (
          <div className="h-full flex items-center justify-center">
            <p className="font-body text-lg text-secondary text-center px-4">
              Aún no hay mensajes. Sé el primero en escribir.
            </p>
          </div>
        ) : (
          messages.map((msg, i) => {
            const isOwn = msg.userId === currentUserId
            return (
              <div key={i} className={`flex flex-col ${isOwn ? 'items-end' : 'items-start'}`}>
                <div className="flex items-baseline gap-1.5 mb-0.5">
                  <span className={`font-heading text-[9px] ${isOwn ? 'text-accent' : 'text-secondary'}`}>
                    {isOwn ? 'Tú' : (userDirectory[msg.userId] || msg.userId)}
                  </span>
                  <span className="font-body text-base text-secondary/60">
                    {formatTime(msg.timestamp)}
                  </span>
                </div>
                <div
                  className={`font-body text-lg px-2 py-1 max-w-[90%] break-words ${
                    isOwn
                      ? 'bg-accent/20 border border-accent/30 text-foreground'
                      : 'bg-muted border border-border text-foreground'
                  }`}
                >
                  {msg.message}
                </div>
              </div>
            )
          })
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input form */}
      <form onSubmit={handleSubmit} className="flex flex-col gap-1">
        <div className="flex gap-2">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={!isConnected}
            maxLength={MAX_LENGTH}
            rows={2}
            placeholder={isConnected ? 'Escribe un mensaje... (Enter para enviar)' : 'Conectando al chat...'}
            aria-label="Mensaje de chat"
            className="input-field flex-1 resize-none font-body text-lg"
            style={{ lineHeight: 1.4 }}
          />
          <button
            type="submit"
            disabled={!isConnected || !text.trim()}
            className="btn-primary flex-shrink-0 self-end flex items-center gap-1"
            aria-label="Enviar mensaje"
          >
            <Send size={13} aria-hidden="true" />
          </button>
        </div>
        {remaining < COUNTER_THRESHOLD && (
          <p
            className={`font-body text-base text-right ${remaining < 20 ? 'text-destructive' : 'text-secondary'}`}
            aria-live="polite"
          >
            {remaining} caracteres restantes
          </p>
        )}
      </form>
    </div>
  )
}
