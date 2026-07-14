import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Send, Search } from 'lucide-react'
import { authApi } from '../api/authApi.js'
import { canvasApi } from '../api/canvasApi.js'
import { useDirectMessages } from '../contexts/DirectMessagesContext.jsx'
import InvitationMessageCard from './InvitationMessageCard.jsx'
import Spinner from './Spinner.jsx'
import ErrorMessage from './ErrorMessage.jsx'
import Avatar from './Avatar.jsx'

const MAX_LENGTH = 1000

function formatTime(ts) {
  if (!ts) return ''
  return new Date(ts).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
}

export default function MessagesPanel({ currentUserId, token }) {
  const { conversations, unreadCounts, loadConversation, clearActiveConversation, sendMessage } =
    useDirectMessages()

  const [users, setUsers] = useState([])
  const [usersLoading, setUsersLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [selectedUserId, setSelectedUserId] = useState(null)
  const [text, setText] = useState('')
  const [historyLoading, setHistoryLoading] = useState(false)
  const [sendError, setSendError] = useState('')
  const bottomRef = useRef(null)
  const navigate = useNavigate()

  // invitationId -> MyInvitationResponse (solo las que siguen PENDING)
  const [pendingInvitations, setPendingInvitations] = useState({})
  // invitationId -> { status: 'ACCEPTED' | 'REJECTED', code } (respondidas en esta sesión)
  const [resolvedInvitations, setResolvedInvitations] = useState({})
  const [respondingInvitationId, setRespondingInvitationId] = useState(null)
  const [respondError, setRespondError] = useState('')
  const [respondErrorInvitationId, setRespondErrorInvitationId] = useState(null)
  const [copiedInvitationId, setCopiedInvitationId] = useState(null)

  useEffect(() => {
    authApi.getAllUsers(token)
      .then((res) => setUsers(res.data.filter((u) => u.userId !== currentUserId)))
      .catch(() => {})
      .finally(() => setUsersLoading(false))
  }, [token, currentUserId])

  useEffect(() => {
    return () => { clearActiveConversation() }
  }, [clearActiveConversation])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [conversations, selectedUserId])

  const refreshPendingInvitations = useCallback(async () => {
    try {
      const res = await canvasApi.listMyInvitations(currentUserId, token)
      const map = {}
      res.data.forEach((inv) => { map[inv.id] = inv })
      setPendingInvitations(map)
    } catch {
      // Si falla, simplemente no se muestran acciones interactivas; no rompe el chat.
    }
  }, [currentUserId, token])

  useEffect(() => { refreshPendingInvitations() }, [refreshPendingInvitations])

  const handleRespondInvitation = useCallback(async (message, accept) => {
    const invitationId = message.invitationId
    setRespondingInvitationId(invitationId)
    setRespondError('')
    setRespondErrorInvitationId(null)
    try {
      await canvasApi.respondToInvitation(message.canvasId, invitationId, currentUserId, accept, token)
      const code = pendingInvitations[invitationId]?.code
      setResolvedInvitations((prev) => ({
        ...prev,
        [invitationId]: { status: accept ? 'ACCEPTED' : 'REJECTED', code },
      }))
      setPendingInvitations((prev) => {
        const next = { ...prev }
        delete next[invitationId]
        return next
      })
    } catch (err) {
      setRespondError(err.response?.data?.error || err.response?.data?.message || 'Error al responder la invitación.')
      setRespondErrorInvitationId(invitationId)
    } finally {
      setRespondingInvitationId(null)
    }
  }, [currentUserId, token, pendingInvitations])

  const handleCopyInvitationCode = useCallback(async (invitationId, code) => {
    try {
      await navigator.clipboard.writeText(code)
      setCopiedInvitationId(invitationId)
      setTimeout(() => setCopiedInvitationId((c) => (c === invitationId ? null : c)), 2000)
    } catch {
      // Sin acceso al portapapeles no hacemos nada más; el código sigue visible en el texto del mensaje.
    }
  }, [])

  const openConversation = useCallback(async (uid) => {
    setHistoryLoading(true)
    setSelectedUserId(uid)
    try {
      await loadConversation(uid)
      await refreshPendingInvitations()
    } finally {
      setHistoryLoading(false)
    }
  }, [loadConversation, refreshPendingInvitations])

  const handleBack = useCallback(() => {
    clearActiveConversation()
    setSelectedUserId(null)
    setText('')
  }, [clearActiveConversation])

  const handleSubmit = useCallback(async (e) => {
    e?.preventDefault()
    const trimmed = text.trim()
    if (!trimmed || !selectedUserId) return
    setSendError('')
    setText('')
    try {
      await sendMessage(selectedUserId, trimmed)
    } catch (err) {
      setSendError(err.response?.data?.error || 'Error al enviar el mensaje.')
      setText(trimmed)
    }
  }, [text, selectedUserId, sendMessage])

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit()
    }
  }

  const messages = selectedUserId ? (conversations[selectedUserId] || []) : []
  const selectedUser = users.find((u) => u.userId === selectedUserId)
  const remaining = MAX_LENGTH - text.length

  const filteredUsers = users.filter((u) => {
    const q = search.trim().toLowerCase()
    if (!q) return true
    return [u.username, u.firstName, u.lastName, u.nickname]
      .filter(Boolean)
      .some((field) => field.toLowerCase().includes(q))
  })

  if (selectedUserId !== null) {
    return (
      <div className="card border-border flex flex-col" style={{ minHeight: 0 }}>
        <div className="flex items-center gap-2 mb-2">
          <button onClick={handleBack} className="btn-ghost flex items-center gap-1 text-sm px-1" aria-label="Volver">
            <ArrowLeft size={14} aria-hidden="true" />
          </button>
          <Avatar
            avatarUrl={selectedUser?.avatarUrl}
            firstName={selectedUser?.firstName}
            lastName={selectedUser?.lastName}
            userId={selectedUser?.userId}
            size="sm"
          />
          <span className="label-field">
            {selectedUser?.username ?? selectedUserId}
            {selectedUser?.nickname && (
              <span className="text-secondary normal-case"> ({selectedUser.nickname})</span>
            )}
          </span>
        </div>

        <div
          role="log"
          aria-live="polite"
          aria-label="Conversación"
          className="overflow-y-auto mb-3 space-y-1"
          style={{ height: 320 }}
        >
          {historyLoading ? (
            <div className="h-full flex items-center justify-center">
              <Spinner size="sm" />
            </div>
          ) : messages.length === 0 ? (
            <div className="h-full flex items-center justify-center">
              <p className="font-body text-lg text-secondary text-center px-4">
                Aún no hay mensajes. Sé el primero en escribir.
              </p>
            </div>
          ) : (
            messages.map((msg, i) => {
              const isOwn = msg.fromUserId === currentUserId
              if (msg.messageType === 'CANVAS_INVITATION' && !isOwn) {
                return (
                  <div key={msg.id ?? i} className="flex flex-col items-start">
                    <InvitationMessageCard
                      message={msg}
                      pendingInfo={pendingInvitations[msg.invitationId]}
                      resolvedInfo={resolvedInvitations[msg.invitationId]}
                      isResponding={respondingInvitationId === msg.invitationId}
                      respondError={respondErrorInvitationId === msg.invitationId ? respondError : ''}
                      copied={copiedInvitationId === msg.invitationId}
                      onRespond={(accept) => handleRespondInvitation(msg, accept)}
                      onCopyCode={(code) => handleCopyInvitationCode(msg.invitationId, code)}
                      onOpenCanvas={(canvasId) => navigate(`/canvas/${canvasId}`)}
                    />
                  </div>
                )
              }
              return (
                <div key={msg.id ?? i} className={`flex flex-col ${isOwn ? 'items-end' : 'items-start'}`}>
                  <div className="flex items-baseline gap-1.5 mb-0.5">
                    <span className={`font-heading text-[9px] ${isOwn ? 'text-accent' : 'text-secondary'}`}>
                      {isOwn ? 'Tú' : (selectedUser?.username ?? msg.fromUserId)}
                    </span>
                    <span className="font-body text-base text-secondary/60">
                      {formatTime(msg.sentAt)}
                    </span>
                  </div>
                  <div
                    className={`font-body text-lg px-2 py-1 max-w-[90%] break-words ${
                      isOwn
                        ? 'bg-accent/20 border border-accent/30 text-foreground'
                        : 'bg-muted border border-border text-foreground'
                    }`}
                  >
                    {msg.content}
                  </div>
                </div>
              )
            })
          )}
          <div ref={bottomRef} />
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-1">
          <div className="flex gap-2">
            <textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              onKeyDown={handleKeyDown}
              maxLength={MAX_LENGTH}
              rows={2}
              placeholder="Escribe un mensaje... (Enter para enviar)"
              aria-label="Mensaje directo"
              className="input-field flex-1 resize-none font-body text-lg"
              style={{ lineHeight: 1.4 }}
            />
            <button
              type="submit"
              disabled={!text.trim()}
              className="btn-primary flex-shrink-0 self-end flex items-center gap-1"
              aria-label="Enviar mensaje"
            >
              <Send size={13} aria-hidden="true" />
            </button>
          </div>
          {MAX_LENGTH - text.length < 50 && (
            <p
              className={`font-body text-base text-right ${remaining < 20 ? 'text-destructive' : 'text-secondary'}`}
              aria-live="polite"
            >
              {remaining} caracteres restantes
            </p>
          )}
        </form>
        {sendError && <ErrorMessage message={sendError} className="mt-1" />}
      </div>
    )
  }

  return (
    <div className="card border-border flex flex-col" style={{ minHeight: 0 }}>
      <p className="label-field mb-2">Mensajes</p>
      {usersLoading ? (
        <div className="flex justify-center py-6">
          <Spinner size="sm" />
        </div>
      ) : users.length === 0 ? (
        <p className="font-body text-lg text-secondary text-center py-6">
          No hay otros usuarios registrados.
        </p>
      ) : (
        <>
          <div className="relative mb-2">
            <Search size={13} className="absolute left-2 top-1/2 -translate-y-1/2 text-secondary" aria-hidden="true" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por nombre..."
              aria-label="Buscar personas"
              className="input-field w-full pl-7 text-lg"
            />
          </div>
          {filteredUsers.length === 0 ? (
            <p className="font-body text-lg text-secondary text-center py-4">
              Nadie coincide con "{search}".
            </p>
          ) : (
            <ul className="space-y-1">
              {filteredUsers.map((u) => {
                const unread = unreadCounts[u.userId] || 0
                return (
                  <li key={u.userId}>
                    <button
                      onClick={() => openConversation(u.userId)}
                      className="btn-ghost w-full flex items-center justify-between text-left px-2 py-1.5"
                    >
                      <span className="flex items-center gap-2 min-w-0">
                        <Avatar
                          avatarUrl={u.avatarUrl}
                          firstName={u.firstName}
                          lastName={u.lastName}
                          userId={u.userId}
                          size="sm"
                        />
                        <span className="font-body text-lg truncate">
                          {u.username}
                          {u.nickname && (
                            <span className="text-secondary"> ({u.nickname})</span>
                          )}
                        </span>
                      </span>
                      {unread > 0 && (
                        <span className="bg-destructive text-white font-body text-base w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0">
                          {unread > 9 ? '9+' : unread}
                        </span>
                      )}
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </>
      )}
    </div>
  )
}
