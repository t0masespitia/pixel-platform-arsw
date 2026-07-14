import { UserPlus, Check, X, Copy, CheckCheck, ExternalLink } from 'lucide-react'
import Spinner from './Spinner.jsx'
import ErrorMessage from './ErrorMessage.jsx'

function formatTime(ts) {
  if (!ts) return ''
  return new Date(ts).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
}

/**
 * Tarjeta especial para mensajes de tipo CANVAS_INVITATION dentro del chat de DMs.
 * Solo se usa para invitaciones recibidas (no para las que el propio usuario envió).
 *
 * - pendingInfo: objeto MyInvitationResponse ({ id, canvasId, canvasName, code, ... }) si
 *   la invitación sigue PENDING, o null/undefined si ya no está pendiente.
 * - resolvedInfo: { status: 'ACCEPTED' | 'REJECTED', code } si el usuario ya la respondió
 *   en esta misma sesión (tiene prioridad sobre pendingInfo, que ya no la incluiría).
 */
export default function InvitationMessageCard({
  message,
  pendingInfo,
  resolvedInfo,
  isResponding,
  respondError,
  copied,
  onRespond,
  onCopyCode,
  onOpenCanvas,
}) {
  const isPending = !resolvedInfo && Boolean(pendingInfo)
  const code = resolvedInfo?.code || pendingInfo?.code

  return (
    <div className="bg-muted border border-accent/40 px-3 py-2 max-w-[92%] space-y-2">
      <div className="flex items-center gap-1.5">
        <UserPlus size={13} className="text-accent flex-shrink-0" aria-hidden="true" />
        <span className="font-heading text-[9px] text-accent">INVITACIÓN A LIENZO</span>
        <span className="font-body text-base text-secondary/60 ml-auto">{formatTime(message.sentAt)}</span>
      </div>

      <p className="font-body text-lg text-foreground break-words">{message.content}</p>

      {resolvedInfo?.status === 'ACCEPTED' && (
        <div className="flex items-center gap-2">
          <span className="font-heading text-xs text-success flex items-center gap-1">
            <Check size={12} aria-hidden="true" /> Aceptada
          </span>
          <button
            onClick={() => onOpenCanvas(message.canvasId)}
            className="btn-secondary flex-shrink-0 flex items-center gap-1 text-xs px-2 py-1 ml-auto"
          >
            <ExternalLink size={12} aria-hidden="true" /> Abrir lienzo
          </button>
        </div>
      )}

      {resolvedInfo?.status === 'REJECTED' && (
        <span className="font-heading text-xs text-destructive flex items-center gap-1">
          <X size={12} aria-hidden="true" /> Rechazada
        </span>
      )}

      {!resolvedInfo && isPending && (
        <>
          <ErrorMessage message={respondError} />
          <div className="flex flex-wrap items-center gap-2">
            <button
              onClick={() => onRespond(true)}
              disabled={isResponding}
              className="btn-primary flex-shrink-0 flex items-center gap-1 text-xs px-2 py-1"
            >
              {isResponding ? <Spinner size="sm" /> : <Check size={12} aria-hidden="true" />}
              Aceptar
            </button>
            <button
              onClick={() => onRespond(false)}
              disabled={isResponding}
              className="btn-secondary flex-shrink-0 flex items-center gap-1 text-xs px-2 py-1"
            >
              <X size={12} aria-hidden="true" />
              Rechazar
            </button>
            {code && (
              <button
                onClick={() => onCopyCode(code)}
                className="btn-ghost flex-shrink-0 flex items-center gap-1 text-xs px-2 py-1"
              >
                {copied ? <CheckCheck size={12} className="text-success" aria-hidden="true" /> : <Copy size={12} aria-hidden="true" />}
                {copied ? 'Copiado' : 'Copiar código'}
              </button>
            )}
          </div>
        </>
      )}

      {!resolvedInfo && !isPending && (
        <p className="font-body text-base text-secondary italic">
          Esta invitación ya no está disponible.
        </p>
      )}
    </div>
  )
}
