import { Mic, MicOff, PhoneOff, Phone, Loader } from 'lucide-react'
import ErrorMessage from './ErrorMessage.jsx'

export default function VoiceControls({
  voiceStatus,
  connectedPeers,
  isMuted,
  micError,
  onJoin,
  onLeave,
  onToggleMute,
}) {
  return (
    <div className="card border-border p-3">
      <p className="label-field mb-2">Voz</p>

      {voiceStatus === 'idle' && (
        <button onClick={onJoin} className="btn-secondary w-full flex items-center justify-center gap-2">
          <Phone size={14} aria-hidden="true" />
          Unirse a voz
        </button>
      )}

      {(voiceStatus === 'requesting-mic' || voiceStatus === 'connecting') && (
        <div className="flex items-center gap-2 font-body text-lg text-secondary" role="status">
          <Loader size={14} className="animate-spin text-accent flex-shrink-0" aria-hidden="true" />
          {voiceStatus === 'requesting-mic'
            ? 'Solicitando acceso al micrófono...'
            : 'Conectando...'}
        </div>
      )}

      {voiceStatus === 'disconnected' && (
        <div className="space-y-2">
          <ErrorMessage message="Se perdió la conexión de voz." />
          <button onClick={onJoin} className="btn-secondary w-full">
            Reconectar
          </button>
        </div>
      )}

      {voiceStatus === 'error' && (
        <div className="space-y-2">
          <ErrorMessage message={micError || 'Error de conexión de voz.'} />
          <button onClick={onJoin} className="btn-secondary w-full">
            Reintentar
          </button>
        </div>
      )}

      {voiceStatus === 'connected' && (
        <div className="space-y-3">
          <div>
            <p className="font-body text-base text-secondary mb-1">
              {connectedPeers.length === 0
                ? 'Solo tú en la sala'
                : `${connectedPeers.length + 1} participante${connectedPeers.length > 0 ? 's' : ''}`}
            </p>
            {connectedPeers.length > 0 && (
              <ul className="space-y-1" aria-label="Participantes en voz">
                {connectedPeers.map((peer) => (
                  <li key={peer} className="flex items-center gap-2 font-body text-lg text-foreground">
                    <Mic size={12} className="text-success flex-shrink-0" aria-hidden="true" />
                    <span className="truncate">{peer}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="flex gap-2">
            <button
              onClick={onToggleMute}
              className={`btn-secondary flex-1 flex items-center justify-center ${isMuted ? 'border-warning text-warning' : ''}`}
              aria-label={isMuted ? 'Activar micrófono' : 'Silenciar micrófono'}
              aria-pressed={isMuted}
              title={isMuted ? 'Activar micrófono' : 'Silenciar micrófono'}
            >
              {isMuted
                ? <MicOff size={18} aria-hidden="true" />
                : <Mic size={18} aria-hidden="true" />}
            </button>
            <button
              onClick={onLeave}
              className="btn-secondary flex items-center justify-center px-3 border-destructive text-destructive hover:bg-destructive/10"
              aria-label="Salir de la sala de voz"
              title="Salir de la sala de voz"
            >
              <PhoneOff size={18} aria-hidden="true" />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
