import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { LogOut, MessageCircle, Plus, Palette, Hash, Clock, Grid, Trash2, LogOut as ExitIcon, Check, X, Users } from 'lucide-react'
import { useAuth } from '../auth/AuthContext.jsx'
import { useDirectMessages } from '../contexts/DirectMessagesContext.jsx'
import { canvasApi } from '../api/canvasApi.js'
import { authApi } from '../api/authApi.js'
import PixelLogo from '../components/PixelLogo.jsx'
import ErrorMessage from '../components/ErrorMessage.jsx'
import Spinner from '../components/Spinner.jsx'
import SkeletonCard from '../components/SkeletonCard.jsx'
import CreateCanvasModal from '../components/CreateCanvasModal.jsx'
import Avatar from '../components/Avatar.jsx'
import ProfileModal from '../components/ProfileModal.jsx'

function formatDate(dateStr) {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

function mapApiError(err) {
  if (err.code === 'ERR_NETWORK') return 'No se pudo conectar con el servidor de lienzos.'

  const data = err.response?.data

  if (typeof data === 'string') return data

  return data?.message
    || data?.error
    || err.message
    || 'Error inesperado.'
}

export default function LobbyPage() {
  const { token, username, userId, logout, avatarUrl, firstName, lastName } = useAuth()
  const navigate = useNavigate()
  const { totalUnread } = useDirectMessages()

  const [myCanvases, setMyCanvases] = useState([])
  const [canvasesLoading, setCanvasesLoading] = useState(true)
  const [canvasesError, setCanvasesError] = useState('')

  const [sharedCanvases, setSharedCanvases] = useState([])
  const [sharedLoading, setSharedLoading] = useState(true)
  const [sharedError, setSharedError] = useState('')

  const [pendingInvitations, setPendingInvitations] = useState([])
  const [pendingLoading, setPendingLoading] = useState(true)
  const [pendingError, setPendingError] = useState('')
  const [respondingInvitationId, setRespondingInvitationId] = useState(null)
  const [respondError, setRespondError] = useState('')

  const [generalLoading, setGeneralLoading] = useState(false)
  const [generalError, setGeneralError] = useState('')

  const [joinCode, setJoinCode] = useState('')
  const [joinLoading, setJoinLoading] = useState(false)
  const [joinError, setJoinError] = useState('')

  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showProfileModal, setShowProfileModal] = useState(false)

  const [confirmDeleteId, setConfirmDeleteId] = useState(null)
  const [deleteLoading, setDeleteLoading] = useState(false)
  const [deleteError, setDeleteError] = useState('')

  const [confirmLeaveId, setConfirmLeaveId] = useState(null)
  const [leaveLoading, setLeaveLoading] = useState(false)
  const [leaveError, setLeaveError] = useState('')

  const fetchMyCanvases = useCallback(async () => {
    setCanvasesLoading(true)
    setCanvasesError('')
    try {
      const res = await canvasApi.getByOwner(userId, token)
      setMyCanvases(res.data)
    } catch (err) {
      setCanvasesError(mapApiError(err))
    } finally {
      setCanvasesLoading(false)
    }
  }, [userId, token])

  const fetchSharedCanvases = useCallback(async () => {
    setSharedLoading(true)
    setSharedError('')
    try {
      const res = await canvasApi.getShared(userId, token)
      setSharedCanvases(res.data)
    } catch (err) {
      setSharedError(mapApiError(err))
    } finally {
      setSharedLoading(false)
    }
  }, [userId, token])

  const fetchPendingInvitations = useCallback(async () => {
    setPendingLoading(true)
    setPendingError('')
    try {
      const res = await canvasApi.listMyInvitations(userId, token)
      const invitations = res.data
      const uniqueInviterIds = [...new Set(invitations.map((inv) => inv.invitedByUserId))]
      const profileEntries = await Promise.all(
        uniqueInviterIds.map((uid) =>
          authApi.getUserProfile(uid, token)
            .then((res2) => [uid, res2.data])
            .catch(() => [uid, null])
        )
      )
      const profileMap = Object.fromEntries(profileEntries)
      setPendingInvitations(invitations.map((inv) => ({
        ...inv,
        inviterProfile: profileMap[inv.invitedByUserId] || null,
      })))
    } catch (err) {
      setPendingError(mapApiError(err))
    } finally {
      setPendingLoading(false)
    }
  }, [userId, token])

  useEffect(() => {
    fetchMyCanvases()
    fetchSharedCanvases()
    fetchPendingInvitations()
  }, [fetchMyCanvases, fetchSharedCanvases, fetchPendingInvitations])

  const handleEnterGeneral = async () => {
    setGeneralLoading(true)
    setGeneralError('')
    try {
      const res = await canvasApi.getGeneral(token)
      navigate(`/canvas/${res.data.id}`)
    } catch (err) {
      setGeneralError(mapApiError(err))
    } finally {
      setGeneralLoading(false)
    }
  }

  const handleJoin = async (e) => {
    e.preventDefault()
    if (!joinCode.trim()) return
    setJoinLoading(true)
    setJoinError('')
    try {
      const res = await canvasApi.join(userId, joinCode.trim(), token)
      navigate(`/canvas/${res.data.id}`)
    } catch (err) {
      const data = err.response?.data
      const msg = typeof data === 'string'
        ? data
        : data?.message || data?.error || ''
      if (err.response?.status === 400) {
        setJoinError(msg || 'Código inválido o ya utilizado.')
      } else {
        setJoinError(mapApiError(err))
      }
    } finally {
      setJoinLoading(false)
    }
  }

  const handleCreate = async (name, width, height) => {
    const res = await canvasApi.create(name, width, height, userId, token)
    setShowCreateModal(false)
    await fetchMyCanvases()
    navigate(`/canvas/${res.data.id}`)
  }

  const handleConfirmDelete = async () => {
    if (!confirmDeleteId) return
    setDeleteLoading(true)
    setDeleteError('')
    try {
      await canvasApi.delete(confirmDeleteId, token)
      setConfirmDeleteId(null)
      await fetchMyCanvases()
    } catch (err) {
      setDeleteError(mapApiError(err))
    } finally {
      setDeleteLoading(false)
    }
  }

  const handleConfirmLeave = async () => {
    if (!confirmLeaveId) return
    setLeaveLoading(true)
    setLeaveError('')
    try {
      await canvasApi.leaveCanvas(confirmLeaveId, userId, token)
      setConfirmLeaveId(null)
      await fetchSharedCanvases()
    } catch (err) {
      setLeaveError(mapApiError(err))
    } finally {
      setLeaveLoading(false)
    }
  }

  const handleRespondInvitation = async (invitation, accept) => {
    setRespondingInvitationId(invitation.id)
    setRespondError('')
    try {
      await canvasApi.respondToInvitation(invitation.canvasId, invitation.id, userId, accept, token)
      await fetchPendingInvitations()
      if (accept) await fetchSharedCanvases()
    } catch (err) {
      setRespondError(mapApiError(err))
    } finally {
      setRespondingInvitationId(null)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b border-border bg-primary/60 backdrop-blur-sm sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
          <PixelLogo />
          <div className="flex items-center gap-4">
            <button
              onClick={() => setShowProfileModal(true)}
              className="btn-ghost hidden sm:flex items-center gap-2"
              aria-label="Ver mi perfil"
            >
              <Avatar avatarUrl={avatarUrl} firstName={firstName} lastName={lastName} userId={userId} size="sm" />
              <span className="font-body text-xl text-secondary">
                Hola, <span className="text-accent">{username}</span>
              </span>
            </button>
            <button
              onClick={() => navigate('/messages')}
              className="btn-ghost relative flex items-center gap-2 text-base"
              title="Mensajes"
            >
              <MessageCircle size={16} aria-hidden="true" />
              <span className="hidden sm:inline">Mensajes</span>
              {totalUnread > 0 && (
                <span className="absolute -top-1.5 -right-1.5 bg-destructive text-white text-base w-5 h-5 rounded-full flex items-center justify-center">
                  {totalUnread > 9 ? '9+' : totalUnread}
                </span>
              )}
            </button>
            <button
              onClick={logout}
              className="btn-ghost flex items-center gap-2 text-base"
              title="Cerrar sesión"
            >
              <LogOut size={16} aria-hidden="true" />
              <span className="hidden sm:inline">Salir</span>
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-4 py-8">
        {/* General Canvas */}
        <section aria-labelledby="general-canvas-title" className="mb-10">
          <h2 id="general-canvas-title" className="font-heading text-xs text-secondary mb-4 uppercase tracking-widest">
            Lienzo Público
          </h2>
          <div
            className="card border-accent shadow-accent relative overflow-hidden cursor-pointer group"
            style={{ background: 'linear-gradient(135deg, #2A1345 0%, #3B1B5C 100%)' }}
            onClick={handleEnterGeneral}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') handleEnterGeneral() }}
            aria-label="Entrar al lienzo general colaborativo"
          >
            <div className="absolute inset-0 opacity-10 pointer-events-none" style={{
              backgroundImage: 'repeating-linear-gradient(0deg, #EC4899 0px, #EC4899 1px, transparent 1px, transparent 16px), repeating-linear-gradient(90deg, #EC4899 0px, #EC4899 1px, transparent 1px, transparent 16px)'
            }} aria-hidden="true" />
            <div className="relative flex items-center gap-4 p-2">
              <div className="flex-shrink-0 p-3 bg-accent/20 border border-accent">
                <Grid size={28} className="text-accent" aria-hidden="true" />
              </div>
              <div className="flex-1">
                <h3 className="font-heading text-sm text-foreground group-hover:text-accent transition-colors mb-1">
                  LIENZO GENERAL
                </h3>
                <p className="font-body text-base text-secondary">
                  Colabora en tiempo real con todos los usuarios de la plataforma
                </p>
              </div>
              <div className="flex-shrink-0">
                {generalLoading
                  ? <Spinner size="md" />
                  : <span className="font-body text-2xl text-accent group-hover:translate-x-1 transition-transform inline-block">→</span>
                }
              </div>
            </div>
            {generalError && (
              <div className="mt-3">
                <ErrorMessage message={generalError} />
              </div>
            )}
          </div>
        </section>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-10">
          {/* My Canvases */}
          <section aria-labelledby="my-canvases-title" className="lg:col-span-2">
            <div className="flex items-center justify-between mb-4">
              <h2 id="my-canvases-title" className="font-heading text-xs text-secondary uppercase tracking-widest">
                Mis Lienzos Privados
              </h2>
              <button
                onClick={() => setShowCreateModal(true)}
                className="btn-primary flex items-center gap-2"
              >
                <Plus size={14} aria-hidden="true" />
                Crear
              </button>
            </div>

            {canvasesLoading ? (
              <div className="space-y-3" aria-label="Cargando lienzos...">
                <SkeletonCard />
                <SkeletonCard />
                <SkeletonCard />
              </div>
            ) : canvasesError ? (
              <ErrorMessage message={canvasesError} />
            ) : myCanvases.length === 0 ? (
              <div className="card border-border text-center py-10">
                <Palette size={36} className="mx-auto mb-3 text-secondary" aria-hidden="true" />
                <p className="font-body text-xl text-secondary mb-4">Aún no tienes lienzos privados</p>
                <button
                  onClick={() => setShowCreateModal(true)}
                  className="btn-primary inline-flex items-center gap-2"
                >
                  <Plus size={14} />
                  Crear mi primer lienzo
                </button>
              </div>
            ) : (
              <div className="space-y-3">
                {myCanvases.map((canvas) => (
                  <article key={canvas.id} className="card">
                    <div
                      className="cursor-pointer"
                      onClick={() => navigate(`/canvas/${canvas.id}`)}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') navigate(`/canvas/${canvas.id}`) }}
                      aria-label={`Abrir lienzo ${canvas.name}`}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <h3 className="font-heading text-xs text-foreground truncate flex-1">{canvas.name}</h3>
                        <span className="text-accent font-body text-xl flex-shrink-0">→</span>
                      </div>
                      <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2">
                        <span className="font-body text-base text-secondary flex items-center gap-1">
                          <Grid size={12} aria-hidden="true" />
                          {canvas.width} × {canvas.height} px
                        </span>
                        <span className="font-body text-base text-secondary flex items-center gap-1">
                          <Clock size={12} aria-hidden="true" />
                          {formatDate(canvas.createdAt)}
                        </span>
                      </div>
                    </div>
                    <div className="mt-2 pt-2 border-t border-border flex justify-end">
                      <button
                        onClick={(e) => { e.stopPropagation(); setConfirmDeleteId(canvas.id); setDeleteError('') }}
                        className="btn-ghost flex items-center gap-1 text-xs px-2 py-1 text-destructive hover:bg-destructive/10"
                      >
                        <Trash2 size={12} aria-hidden="true" />
                        Eliminar lienzo
                      </button>
                    </div>
                    {confirmDeleteId === canvas.id && (
                      <div className="mt-2 bg-destructive/10 border border-destructive p-3 space-y-2">
                        <p className="font-body text-lg text-foreground">
                          ¿Eliminar <span className="text-destructive">{canvas.name}</span> definitivamente? Esta acción borra el lienzo para todos los participantes y no se puede deshacer.
                        </p>
                        <ErrorMessage message={deleteError} />
                        <div className="flex gap-2">
                          <button
                            onClick={handleConfirmDelete}
                            disabled={deleteLoading}
                            className="btn-primary flex items-center gap-2 bg-destructive border-destructive hover:shadow-none"
                          >
                            {deleteLoading && <Spinner size="sm" />}
                            Eliminar
                          </button>
                          <button
                            onClick={() => { setConfirmDeleteId(null); setDeleteError('') }}
                            disabled={deleteLoading}
                            className="btn-secondary"
                          >
                            Cancelar
                          </button>
                        </div>
                      </div>
                    )}
                  </article>
                ))}
              </div>
            )}
          </section>

          {/* Join with Code */}
          <aside aria-labelledby="join-title">
            <h2 id="join-title" className="font-heading text-xs text-secondary mb-4 uppercase tracking-widest">
              Unirse con Código
            </h2>
            <div className="card border-border">
              <form onSubmit={handleJoin} noValidate>
                <label htmlFor="join-code" className="label-field">
                  Código de invitación
                </label>
                <input
                  id="join-code"
                  type="text"
                  value={joinCode}
                  onChange={(e) => setJoinCode(e.target.value)}
                  className="input-field mb-3"
                  placeholder="ABC123"
                  autoComplete="off"
                  aria-describedby="join-code-hint"
                />
                <p id="join-code-hint" className="font-body text-base text-secondary mb-3">
                  Ingresa el código que te compartió el dueño del lienzo.
                </p>
                <ErrorMessage message={joinError} className="mb-3" />
                <button
                  type="submit"
                  disabled={joinLoading || !joinCode.trim()}
                  className="btn-secondary w-full flex items-center justify-center gap-2"
                >
                  {joinLoading
                    ? <><Spinner size="sm" /> Uniéndome...</>
                    : <><Hash size={14} aria-hidden="true" /> Unirme</>
                  }
                </button>
              </form>
            </div>
          </aside>
        </div>

        {/* Shared with me */}
        <section aria-labelledby="shared-canvases-title" className="mb-10">
          <h2 id="shared-canvases-title" className="font-heading text-xs text-secondary mb-4 uppercase tracking-widest">
            Lienzos Compartidos Conmigo
          </h2>
          {sharedLoading ? (
            <div className="space-y-3" aria-label="Cargando lienzos compartidos...">
              <SkeletonCard />
            </div>
          ) : sharedError ? (
            <ErrorMessage message={sharedError} />
          ) : sharedCanvases.length === 0 ? (
            <div className="card border-border text-center py-8">
              <Users size={32} className="mx-auto mb-3 text-secondary" aria-hidden="true" />
              <p className="font-body text-xl text-secondary">
                Todavía no participas en ningún lienzo privado de otra persona.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {sharedCanvases.map((canvas) => (
                <article key={canvas.id} className="card">
                  <div
                    className="cursor-pointer"
                    onClick={() => navigate(`/canvas/${canvas.id}`)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') navigate(`/canvas/${canvas.id}`) }}
                    aria-label={`Abrir lienzo ${canvas.name}`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <h3 className="font-heading text-xs text-foreground truncate flex-1">{canvas.name}</h3>
                      <span className="text-accent font-body text-xl flex-shrink-0">→</span>
                    </div>
                    <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2">
                      <span className="font-body text-base text-secondary flex items-center gap-1">
                        <Grid size={12} aria-hidden="true" />
                        {canvas.width} × {canvas.height} px
                      </span>
                    </div>
                  </div>
                  <div className="mt-2 pt-2 border-t border-border flex justify-end">
                    <button
                      onClick={(e) => { e.stopPropagation(); setConfirmLeaveId(canvas.id); setLeaveError('') }}
                      className="btn-ghost flex items-center gap-1 text-xs px-2 py-1 text-destructive hover:bg-destructive/10"
                    >
                      <ExitIcon size={12} aria-hidden="true" />
                      Abandonar lienzo
                    </button>
                  </div>
                  {confirmLeaveId === canvas.id && (
                    <div className="mt-2 bg-destructive/10 border border-destructive p-3 space-y-2">
                      <p className="font-body text-lg text-foreground">
                        ¿Estás seguro de que deseas abandonar <span className="text-destructive">{canvas.name}</span>? Para volver a ingresar necesitarás una nueva invitación.
                      </p>
                      <ErrorMessage message={leaveError} />
                      <div className="flex gap-2">
                        <button
                          onClick={handleConfirmLeave}
                          disabled={leaveLoading}
                          className="btn-primary flex items-center gap-2 bg-destructive border-destructive hover:shadow-none"
                        >
                          {leaveLoading && <Spinner size="sm" />}
                          Abandonar
                        </button>
                        <button
                          onClick={() => { setConfirmLeaveId(null); setLeaveError('') }}
                          disabled={leaveLoading}
                          className="btn-secondary"
                        >
                          Cancelar
                        </button>
                      </div>
                    </div>
                  )}
                </article>
              ))}
            </div>
          )}
        </section>

        {/* Pending invitations */}
        <section aria-labelledby="pending-invitations-title">
          <h2 id="pending-invitations-title" className="font-heading text-xs text-secondary mb-4 uppercase tracking-widest">
            Invitaciones Pendientes
          </h2>
          {pendingLoading ? (
            <div className="space-y-3" aria-label="Cargando invitaciones...">
              <SkeletonCard />
            </div>
          ) : pendingError ? (
            <ErrorMessage message={pendingError} />
          ) : pendingInvitations.length === 0 ? (
            <div className="card border-border text-center py-8">
              <p className="font-body text-xl text-secondary">No tienes invitaciones pendientes.</p>
            </div>
          ) : (
            <div className="space-y-3">
              <ErrorMessage message={respondError} />
              {pendingInvitations.map((inv) => {
                const isResponding = respondingInvitationId === inv.id
                const inviterName = inv.inviterProfile
                  ? `${inv.inviterProfile.firstName || ''} ${inv.inviterProfile.lastName || ''}`.trim() || inv.inviterProfile.username
                  : `Usuario ${inv.invitedByUserId}`
                return (
                  <article key={inv.id} className="card flex items-center gap-3">
                    <Avatar
                      avatarUrl={inv.inviterProfile?.avatarUrl}
                      firstName={inv.inviterProfile?.firstName}
                      lastName={inv.inviterProfile?.lastName}
                      userId={inv.invitedByUserId}
                      size="md"
                    />
                    <div className="min-w-0 flex-1">
                      <h3 className="font-heading text-xs text-foreground truncate">{inv.canvasName}</h3>
                      <p className="font-body text-base text-secondary truncate">Invitado por {inviterName}</p>
                    </div>
                    <div className="flex gap-2 flex-shrink-0">
                      <button
                        onClick={() => handleRespondInvitation(inv, true)}
                        disabled={isResponding}
                        className="btn-primary flex items-center gap-1 text-xs px-2 py-1"
                      >
                        {isResponding ? <Spinner size="sm" /> : <Check size={12} aria-hidden="true" />}
                        Aceptar
                      </button>
                      <button
                        onClick={() => handleRespondInvitation(inv, false)}
                        disabled={isResponding}
                        className="btn-secondary flex items-center gap-1 text-xs px-2 py-1"
                      >
                        <X size={12} aria-hidden="true" />
                        Rechazar
                      </button>
                    </div>
                  </article>
                )
              })}
            </div>
          )}
        </section>
      </main>

      {showCreateModal && (
        <CreateCanvasModal
          onClose={() => setShowCreateModal(false)}
          onCreate={handleCreate}
        />
      )}
      {showProfileModal && <ProfileModal onClose={() => setShowProfileModal(false)} />}
    </div>
  )
}
