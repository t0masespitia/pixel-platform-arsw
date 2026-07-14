import { useState, useEffect, useCallback } from 'react'
import { Search, ChevronLeft, ChevronRight, UserX, RefreshCw, UserPlus, RotateCcw, X } from 'lucide-react'
import { canvasApi } from '../api/canvasApi.js'
import { authApi } from '../api/authApi.js'
import Avatar from './Avatar.jsx'
import ErrorMessage from './ErrorMessage.jsx'
import Spinner from './Spinner.jsx'

const PAGE_SIZE = 8
const LETTERS = Array.from({ length: 26 }, (_, i) => String.fromCharCode(65 + i))

function formatDate(dateStr) {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

function displayName(profile, userId) {
  if (!profile) return `Usuario ${userId}`
  const full = `${profile.firstName || ''} ${profile.lastName || ''}`.trim()
  return full || profile.username || `Usuario ${userId}`
}

function resolveActionState(targetUserId, members, invitations) {
  if (members.some((m) => m.userId === targetUserId)) return 'member'
  const inv = invitations.find((i) => i.targetUserId === targetUserId)
  if (inv?.status === 'PENDING') return 'pending'
  if (inv?.status === 'REJECTED') return 'rejected'
  return 'none'
}

export default function CanvasMembersPanel({ canvasId, requesterId, token }) {
  // --- Miembros actuales + invitaciones del lienzo ---
  const [members, setMembers] = useState([])
  const [memberProfiles, setMemberProfiles] = useState({})
  const [invitations, setInvitations] = useState([])
  const [overviewLoading, setOverviewLoading] = useState(true)
  const [overviewError, setOverviewError] = useState('')

  const [confirmExpelId, setConfirmExpelId] = useState(null)
  const [expelLoading, setExpelLoading] = useState(false)
  const [expelError, setExpelError] = useState('')

  // --- Directorio para invitar ---
  const [queryInput, setQueryInput] = useState('')
  const [query, setQuery] = useState('')
  const [letter, setLetter] = useState('')
  const [page, setPage] = useState(0)
  const [directoryResult, setDirectoryResult] = useState({ users: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 })
  const [directoryLoading, setDirectoryLoading] = useState(true)
  const [directoryError, setDirectoryError] = useState('')

  const [inviteLoadingUserId, setInviteLoadingUserId] = useState(null)
  const [inviteError, setInviteError] = useState('')
  const [inviteErrorUserId, setInviteErrorUserId] = useState(null)
  const [cancelingInvitationId, setCancelingInvitationId] = useState(null)

  const loadOverview = useCallback(async () => {
    setOverviewLoading(true)
    setOverviewError('')
    try {
      const [membersRes, invitationsRes] = await Promise.all([
        canvasApi.getMembers(canvasId, requesterId, token),
        canvasApi.listInvitations(canvasId, requesterId, token),
      ])
      setMembers(membersRes.data)
      setInvitations(invitationsRes.data)

      const uniqueIds = [...new Set(membersRes.data.map((m) => m.userId))]
      const profileEntries = await Promise.all(
        uniqueIds.map((uid) =>
          authApi.getUserProfile(uid, token)
            .then((res) => [uid, res.data])
            .catch(() => [uid, null])
        )
      )
      setMemberProfiles(Object.fromEntries(profileEntries))
    } catch (err) {
      setOverviewError(err.response?.data?.error || err.response?.data?.message || 'Error al cargar miembros e invitaciones.')
    } finally {
      setOverviewLoading(false)
    }
  }, [canvasId, requesterId, token])

  useEffect(() => { loadOverview() }, [loadOverview])

  const fetchDirectory = useCallback(async () => {
    setDirectoryLoading(true)
    setDirectoryError('')
    try {
      const res = await authApi.getDirectory(token, requesterId, { letter, query, page, size: PAGE_SIZE })
      setDirectoryResult(res.data)
    } catch (err) {
      setDirectoryError(err.response?.data?.error || err.response?.data?.message || 'Error al buscar usuarios.')
    } finally {
      setDirectoryLoading(false)
    }
  }, [token, requesterId, letter, query, page])

  useEffect(() => { fetchDirectory() }, [fetchDirectory])

  // Debounce del buscador de texto
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(queryInput.trim())
      setLetter('')
      setPage(0)
    }, 300)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryInput])

  const handleLetterClick = (l) => {
    setQueryInput('')
    setQuery('')
    setPage(0)
    setLetter((current) => (current === l ? '' : l))
  }

  const handleResetFilters = () => {
    setQueryInput('')
    setQuery('')
    setLetter('')
    setPage(0)
  }

  const handleInvite = async (targetUserId) => {
    setInviteLoadingUserId(targetUserId)
    setInviteError('')
    setInviteErrorUserId(null)
    try {
      await canvasApi.createInvitation(canvasId, requesterId, targetUserId, token)
      await loadOverview()
    } catch (err) {
      setInviteError(err.response?.data?.error || err.response?.data?.message || 'Error al enviar la invitación.')
      setInviteErrorUserId(targetUserId)
    } finally {
      setInviteLoadingUserId(null)
    }
  }

  const handleCancelInvitation = async (invitation) => {
    if (!invitation) return
    setCancelingInvitationId(invitation.id)
    setInviteError('')
    setInviteErrorUserId(null)
    try {
      await canvasApi.cancelInvitation(canvasId, invitation.id, requesterId, token)
      await loadOverview()
    } catch (err) {
      setInviteError(err.response?.data?.error || err.response?.data?.message || 'Error al cancelar la invitación.')
      setInviteErrorUserId(invitation.targetUserId)
    } finally {
      setCancelingInvitationId(null)
    }
  }

  const handleConfirmExpel = async () => {
    if (!confirmExpelId) return
    setExpelLoading(true)
    setExpelError('')
    try {
      await canvasApi.removeMember(canvasId, confirmExpelId, requesterId, token)
      setConfirmExpelId(null)
      await loadOverview()
    } catch (err) {
      const msg = err.response?.data?.error || err.response?.data?.message
      setExpelError(msg || 'Error al expulsar al miembro.')
    } finally {
      setExpelLoading(false)
    }
  }

  const pendingCount = invitations.filter((i) => i.status === 'PENDING').length
  const confirmExpelName = confirmExpelId ? displayName(memberProfiles[confirmExpelId], confirmExpelId) : ''

  return (
    <div className="card border-border space-y-5">
      {/* --- Miembros actuales --- */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <p className="label-field mb-0">
            Miembros ({members.length}{pendingCount > 0 ? ` · ${pendingCount} pendiente${pendingCount === 1 ? '' : 's'}` : ''})
          </p>
          <button
            onClick={loadOverview}
            className="btn-ghost p-1"
            aria-label="Recargar miembros"
            disabled={overviewLoading}
          >
            <RefreshCw size={13} className={overviewLoading ? 'animate-spin' : ''} aria-hidden="true" />
          </button>
        </div>

        {overviewLoading ? (
          <div className="flex items-center gap-2 font-body text-lg text-secondary">
            <Spinner size="sm" />
            Cargando...
          </div>
        ) : overviewError ? (
          <ErrorMessage message={overviewError} />
        ) : members.length === 0 ? (
          <p className="font-body text-lg text-secondary">Sin miembros registrados.</p>
        ) : (
          <ul className="space-y-1" aria-label="Lista de miembros">
            {members.map((m) => {
              const profile = memberProfiles[m.userId]
              const isSelf = m.userId === requesterId
              return (
                <li
                  key={m.userId}
                  className="flex items-center gap-2 py-1.5 border-b border-border last:border-0"
                >
                  <Avatar
                    avatarUrl={profile?.avatarUrl}
                    firstName={profile?.firstName}
                    lastName={profile?.lastName}
                    userId={m.userId}
                    size="sm"
                  />
                  <div className="min-w-0 flex-1">
                    <span className={`font-body text-lg truncate block ${isSelf ? 'text-accent' : 'text-foreground'}`}>
                      {displayName(profile, m.userId)}{isSelf ? ' (tú)' : ''}
                    </span>
                    <span className="font-body text-base text-secondary">
                      {profile?.username ? `@${profile.username} · ` : ''}{formatDate(m.joinedAt)}
                    </span>
                  </div>
                  {!isSelf && (
                    <button
                      onClick={() => { setConfirmExpelId(m.userId); setExpelError('') }}
                      className="btn-ghost p-1 text-destructive hover:bg-destructive/10 flex-shrink-0"
                      aria-label={`Expulsar a ${displayName(profile, m.userId)}`}
                      title="Expulsar"
                    >
                      <UserX size={14} aria-hidden="true" />
                    </button>
                  )}
                </li>
              )
            })}
          </ul>
        )}

        {confirmExpelId && (
          <div className="bg-destructive/10 border border-destructive p-3 space-y-2 mt-2">
            <p className="font-body text-lg text-foreground">
              ¿Expulsar a <span className="text-destructive">{confirmExpelName}</span>?
            </p>
            <ErrorMessage message={expelError} />
            <div className="flex gap-2">
              <button
                onClick={handleConfirmExpel}
                disabled={expelLoading}
                className="btn-primary flex items-center gap-2 bg-destructive border-destructive hover:shadow-none"
              >
                {expelLoading && <Spinner size="sm" />}
                Expulsar
              </button>
              <button
                onClick={() => { setConfirmExpelId(null); setExpelError('') }}
                disabled={expelLoading}
                className="btn-secondary"
              >
                Cancelar
              </button>
            </div>
          </div>
        )}
      </div>

      {/* --- Invitar nuevos miembros --- */}
      <div className="pt-4 border-t border-border space-y-3">
        <p className="label-field mb-0">Invitar miembros</p>

        <div className="relative">
          <Search size={14} className="absolute left-2 top-1/2 -translate-y-1/2 text-secondary" aria-hidden="true" />
          <input
            type="text"
            value={queryInput}
            onChange={(e) => setQueryInput(e.target.value)}
            placeholder="Buscar por nombre o username..."
            className="input-field pl-7"
            aria-label="Buscar usuarios"
          />
        </div>

        <div className="flex flex-wrap gap-1" role="group" aria-label="Filtrar por letra">
          <button
            onClick={handleResetFilters}
            className={!letter && !query ? 'btn-primary text-base px-2 py-0.5' : 'btn-ghost text-base px-2 py-0.5'}
          >
            Todos
          </button>
          {LETTERS.map((l) => (
            <button
              key={l}
              onClick={() => handleLetterClick(l)}
              className={letter === l ? 'btn-primary text-base px-2 py-0.5' : 'btn-ghost text-base px-2 py-0.5'}
              aria-pressed={letter === l}
            >
              {l}
            </button>
          ))}
        </div>

        {inviteError && (
          <ErrorMessage message={inviteError} />
        )}

        {directoryLoading ? (
          <div className="flex items-center gap-2 font-body text-lg text-secondary">
            <Spinner size="sm" />
            Buscando...
          </div>
        ) : directoryError ? (
          <ErrorMessage message={directoryError} />
        ) : directoryResult.users.length === 0 ? (
          <p className="font-body text-lg text-secondary">No se encontraron usuarios.</p>
        ) : (
          <ul className="space-y-1" aria-label="Directorio de usuarios">
            {directoryResult.users.map((u) => {
              const state = resolveActionState(u.userId, members, invitations)
              const isLoading = inviteLoadingUserId === u.userId
              const pendingInvitation = invitations.find((i) => i.targetUserId === u.userId)
              return (
                <li key={u.userId} className="flex items-center gap-2 py-1.5 border-b border-border last:border-0">
                  <Avatar avatarUrl={u.avatarUrl} firstName={u.firstName} lastName={u.lastName} userId={u.userId} size="sm" />
                  <div className="min-w-0 flex-1">
                    <span className="font-body text-lg truncate block text-foreground">
                      {`${u.firstName || ''} ${u.lastName || ''}`.trim() || u.username}
                    </span>
                    <span className="font-body text-base text-secondary">@{u.username}</span>
                  </div>
                  {state === 'member' && (
                    <span className="font-heading text-xs text-success px-2 py-1 flex-shrink-0">Miembro</span>
                  )}
                  {state === 'pending' && (
                    <div className="flex items-center gap-1 flex-shrink-0">
                      <span className="font-heading text-xs text-warning px-2 py-1">Invitación enviada</span>
                      <button
                        onClick={() => handleCancelInvitation(pendingInvitation)}
                        disabled={cancelingInvitationId === pendingInvitation?.id}
                        className="btn-ghost p-1 text-destructive hover:bg-destructive/10"
                        aria-label="Cancelar invitación"
                        title="Cancelar invitación"
                      >
                        {cancelingInvitationId === pendingInvitation?.id ? <Spinner size="sm" /> : <X size={12} aria-hidden="true" />}
                      </button>
                    </div>
                  )}
                  {state === 'rejected' && (
                    <button
                      onClick={() => handleInvite(u.userId)}
                      disabled={isLoading}
                      className="btn-secondary flex-shrink-0 flex items-center gap-1 text-xs px-2 py-1"
                    >
                      {isLoading ? <Spinner size="sm" /> : <RotateCcw size={12} aria-hidden="true" />}
                      Reenviar
                    </button>
                  )}
                  {state === 'none' && (
                    <button
                      onClick={() => handleInvite(u.userId)}
                      disabled={isLoading}
                      className="btn-primary flex-shrink-0 flex items-center gap-1 text-xs px-2 py-1"
                    >
                      {isLoading ? <Spinner size="sm" /> : <UserPlus size={12} aria-hidden="true" />}
                      Invitar
                    </button>
                  )}
                  {inviteErrorUserId === u.userId && (
                    <ErrorMessage message={inviteError} className="w-full mt-1" />
                  )}
                </li>
              )
            })}
          </ul>
        )}

        {directoryResult.totalPages > 1 && (
          <div className="flex items-center justify-between pt-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0 || directoryLoading}
              className="btn-ghost p-1 flex items-center gap-1 text-base"
            >
              <ChevronLeft size={14} aria-hidden="true" /> Anterior
            </button>
            <span className="font-body text-base text-secondary">
              Página {directoryResult.page + 1} de {directoryResult.totalPages}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(directoryResult.totalPages - 1, p + 1))}
              disabled={page >= directoryResult.totalPages - 1 || directoryLoading}
              className="btn-ghost p-1 flex items-center gap-1 text-base"
            >
              Siguiente <ChevronRight size={14} aria-hidden="true" />
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
