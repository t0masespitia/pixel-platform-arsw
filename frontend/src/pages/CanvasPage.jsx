import { useState, useEffect, useMemo, useRef, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, Grid, Clock, Lock, Globe, ZoomIn, ZoomOut, Wifi, WifiOff, Loader, Download } from 'lucide-react'
import { useAuth } from '../auth/AuthContext.jsx'
import { canvasApi } from '../api/canvasApi.js'
import { authApi } from '../api/authApi.js'
import { useCanvasSocket } from '../hooks/useCanvasSocket.js'
import { useChatSocket } from '../hooks/useChatSocket.js'
import { useVoiceChat } from '../hooks/useVoiceChat.js'
import PixelLogo from '../components/PixelLogo.jsx'
import ErrorMessage from '../components/ErrorMessage.jsx'
import Spinner from '../components/Spinner.jsx'
import PixelCanvasGrid from '../components/PixelCanvasGrid.jsx'
import ColorPalette from '../components/ColorPalette.jsx'
import ChatPanel from '../components/ChatPanel.jsx'
import MessagesPanel from '../components/MessagesPanel.jsx'
import VoiceControls from '../components/VoiceControls.jsx'
import CanvasMembersPanel from '../components/CanvasMembersPanel.jsx'
import AiTemplatePanel from '../components/AiTemplatePanel.jsx'
import Avatar from '../components/Avatar.jsx'
import ProfileModal from '../components/ProfileModal.jsx'
import { useDirectMessages } from '../contexts/DirectMessagesContext.jsx'

// Debe coincidir con canvas.cooldown-millis del backend.
// TODO: idealmente obtenerlo via API en lugar de hardcodear.
const COOLDOWN_MS = 500

function formatDate(dateStr) {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleString('es-CO', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}

function displayCreatorName(profile, ownerId) {
  if (!ownerId) return 'PixelPlatform'
  if (!profile) return `Usuario ${ownerId}`
  const fullName = `${profile.firstName || ''} ${profile.lastName || ''}`.trim()
  return fullName || profile.username || `Usuario ${ownerId}`
}

function ConnectionBadge({ status }) {
  const configs = {
    connecting:   { icon: <Loader size={13} className="animate-spin" />, label: 'Conectando...', color: 'text-warning border-warning bg-warning/10' },
    connected:    { icon: <Wifi size={13} />,    label: 'Conectado',       color: 'text-success border-success bg-success/10' },
    disconnected: { icon: <WifiOff size={13} />, label: 'Desconectado',    color: 'text-destructive border-destructive bg-destructive/10' },
    error:        { icon: <WifiOff size={13} />, label: 'Error de conexión', color: 'text-destructive border-destructive bg-destructive/10' },
  }
  const { icon, label, color } = configs[status] ?? configs.disconnected
  return (
    <span
      role="status"
      aria-live="polite"
      className={`inline-flex items-center gap-1.5 border px-2 py-1 font-body text-base ${color}`}
    >
      {icon}
      {label}
    </span>
  )
}

function CanvasEditor({ canvas, id, userId, token }) {
  const { pixels, connectionStatus, socketError, paintPixel } = useCanvasSocket(id, userId, token)
  const { messages, connectionStatus: chatStatus, sendMessage } = useChatSocket(id, userId, token)
  const { voiceStatus, connectedPeers, isMuted, micError, joinVoice, leaveVoice, toggleMute } =
    useVoiceChat(id, userId, token)
  const [selectedColor, setSelectedColor] = useState('#EC4899')
  const [optimisticPixels, setOptimisticPixels] = useState({})
  const [paintError, setPaintError] = useState('')
  const [cooldownUntil, setCooldownUntil] = useState(0)
  const [timeLeft, setTimeLeft] = useState(0)
  const [userDirectory, setUserDirectory] = useState({})
  const [creatorProfile, setCreatorProfile] = useState(null)
  const [sidebarTab, setSidebarTab] = useState('chat')
  const { totalUnread } = useDirectMessages()

  useEffect(() => {
    authApi.getAllUsers(token)
      .then((res) => {
        const dir = {}
        res.data.forEach(({ userId, username }) => { dir[userId] = username })
        setUserDirectory(dir)
      })
      .catch(() => {})
  }, [token])

  useEffect(() => {
    if (!canvas.ownerId) {
      setCreatorProfile(null)
      return
    }
    let cancelled = false
    authApi.getUserProfile(canvas.ownerId, token)
      .then((res) => { if (!cancelled) setCreatorProfile(res.data) })
      .catch(() => { if (!cancelled) setCreatorProfile(null) })
    return () => { cancelled = true }
  }, [canvas.ownerId, token])
  const pixelsRef = useRef(pixels)
  const optimisticTimersRef = useRef({})

  const [zoom, setZoom] = useState(1)
  const [minZoom, setMinZoom] = useState(1)
  const handleFitZoomReady = useCallback((fitZoom) => {
    setZoom(fitZoom)
    setMinZoom(fitZoom)
  }, [])

  const displayPixels = useMemo(
    () => ({ ...pixels, ...optimisticPixels }),
    [pixels, optimisticPixels],
  )

  useEffect(() => {
    pixelsRef.current = pixels
    setOptimisticPixels((prev) => {
      const next = { ...prev }
      let changed = false
      Object.entries(prev).forEach(([key, color]) => {
        if (pixels[key] === color) {
          clearTimeout(optimisticTimersRef.current[key])
          delete optimisticTimersRef.current[key]
          delete next[key]
          changed = true
        }
      })
      return changed ? next : prev
    })
  }, [pixels])

  useEffect(() => {
    return () => {
      Object.values(optimisticTimersRef.current).forEach(clearTimeout)
    }
  }, [])

  useEffect(() => {
    if (socketError) setPaintError(socketError)
  }, [socketError])

  useEffect(() => {
    if (!cooldownUntil) { setTimeLeft(0); return }
    const tick = () => {
      const remaining = Math.max(0, cooldownUntil - Date.now())
      setTimeLeft(remaining)
    }
    tick()
    const interval = setInterval(tick, 100)
    return () => clearInterval(interval)
  }, [cooldownUntil])

  const handlePixelClick = (x, y) => {
    setPaintError('')
    if (timeLeft > 0) return
    if (!userId) {
      console.error('[Canvas] No se puede pintar: userId vacío')
      setPaintError('No se puede pintar porque la sesión no tiene userId. Vuelve a iniciar sesión.')
      return
    }
    const published = paintPixel(x, y, selectedColor)
    if (!published) return
    const key = `${x},${y}`
    clearTimeout(optimisticTimersRef.current[key])
    setOptimisticPixels((prev) => ({ ...prev, [key]: selectedColor }))
    optimisticTimersRef.current[key] = setTimeout(() => {
      if (pixelsRef.current[key] !== selectedColor) {
        setOptimisticPixels((prev) => {
          const next = { ...prev }
          delete next[key]
          return next
        })
        setPaintError('El backend no confirmó ese pixel. Revisa la conexión o el cooldown.')
      }
      delete optimisticTimersRef.current[key]
    }, 2500)
    setCooldownUntil(Date.now() + COOLDOWN_MS)
  }

  const isCoolingDown = timeLeft > 0
  const isOwner = canvas.isPrivate && canvas.ownerId === userId
  const [membersOpen, setMembersOpen] = useState(false)
  const [aiPanelOpen, setAiPanelOpen] = useState(false)

  const handleDownloadImage = useCallback(() => {
    const exportCanvas = document.createElement('canvas')
    exportCanvas.width = canvas.width
    exportCanvas.height = canvas.height
    const ctx = exportCanvas.getContext('2d')
    ctx.fillStyle = '#FFFFFF'
    ctx.fillRect(0, 0, exportCanvas.width, exportCanvas.height)
    Object.entries(displayPixels).forEach(([key, color]) => {
      const [x, y] = key.split(',').map(Number)
      if (!Number.isInteger(x) || !Number.isInteger(y)) return
      ctx.fillStyle = color
      ctx.fillRect(x, y, 1, 1)
    })
    const link = document.createElement('a')
    link.download = `${canvas.name || 'lienzo'}.png`
    link.href = exportCanvas.toDataURL('image/png')
    link.click()
  }, [canvas, displayPixels])

  return (
    <div className="flex gap-3 items-start flex-wrap lg:flex-nowrap">
      {/* Left: owner tools (invitar/gestionar miembros + plantilla desde imagen) */}
      {isOwner && (
        <div className="w-full lg:w-64 flex-shrink-0 space-y-3">
          {!canvas.isDefaultTemplate && (
          <div>
            <button
              onClick={() => setAiPanelOpen((o) => !o)}
              className="btn-secondary w-full flex items-center justify-between"
              aria-expanded={aiPanelOpen}
              aria-controls="ai-panel"
            >
              <span>Generar plantilla desde imagen</span>
              <span aria-hidden="true">{aiPanelOpen ? '▲' : '▼'}</span>
            </button>
            {aiPanelOpen && (
              <div id="ai-panel" className="mt-3">
                <AiTemplatePanel
                  canvasId={id}
                  userId={userId}
                  token={token}
                  onGenerated={() => setZoom(minZoom)}
                />
              </div>
            )}
          </div>
          )}
          <div>
            <button
              onClick={() => setMembersOpen((o) => !o)}
              className="btn-secondary w-full flex items-center justify-between"
              aria-expanded={membersOpen}
              aria-controls="members-panel"
            >
              <span>Gestionar miembros</span>
              <span aria-hidden="true">{membersOpen ? '▲' : '▼'}</span>
            </button>
            {membersOpen && (
              <div id="members-panel" className="mt-3">
                <CanvasMembersPanel
                  canvasId={id}
                  requesterId={userId}
                  token={token}
                  canvasName={canvas.name}
                />
              </div>
            )}
          </div>
        </div>
      )}

      {/* Center: pixel editor */}
      <div className="flex-1 min-w-0 max-w-[1000px] mx-auto space-y-2">
      {/* Top info bar */}
      <div className="card border-border p-3 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-xs text-foreground mb-1 break-all">{canvas.name}</h1>
          <div className="flex flex-wrap gap-x-4 gap-y-1 font-body text-base text-secondary">
            <span className="flex items-center gap-1">
              <Grid size={12} aria-hidden="true" />
              {canvas.width} × {canvas.height} px
            </span>
            <span className="flex items-center gap-1">
              <Clock size={12} aria-hidden="true" />
              {formatDate(canvas.createdAt)}
            </span>
            <span className="flex items-center gap-1">
              {canvas.isPrivate
                ? <Lock size={12} className="text-warning" aria-hidden="true" />
                : <Globe size={12} className="text-success" aria-hidden="true" />}
              {canvas.isPrivate ? 'Privado' : 'Público'}
            </span>
            <span>
              Creado por {displayCreatorName(creatorProfile, canvas.ownerId)}
            </span>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <ConnectionBadge status={connectionStatus} />

          <button
            onClick={handleDownloadImage}
            className="btn-ghost p-1 flex items-center gap-1"
            aria-label="Descargar lienzo como imagen"
            title="Descargar como PNG"
          >
            <Download size={15} aria-hidden="true" />
          </button>

          {/* Zoom controls */}
          <div className="flex items-center gap-1" role="group" aria-label="Control de zoom">
            <button
              onClick={() => setZoom((z) => Math.max(minZoom, z - 1))}
              className="btn-ghost p-1"
              aria-label="Reducir zoom"
              disabled={zoom <= minZoom}
            >
              <ZoomOut size={15} />
            </button>
            <span className="font-body text-base text-foreground w-10 text-center" aria-live="polite">
              {zoom}x
            </span>
            <button
              onClick={() => setZoom((z) => Math.min(100, z + 1))}
              className="btn-ghost p-1"
              aria-label="Aumentar zoom"
              disabled={zoom >= 100}
            >
              <ZoomIn size={15} />
            </button>
          </div>
        </div>
      </div>

      {paintError && (
        <ErrorMessage message={paintError} />
      )}

      {/* Color palette + estado de cooldown, lado a lado */}
      <div className="flex gap-2 items-stretch flex-wrap sm:flex-nowrap">
        <ColorPalette selectedColor={selectedColor} onSelectColor={setSelectedColor} />
        <div
          role="status"
          aria-live="polite"
          className={`flex-1 min-w-[160px] card p-3 flex flex-col justify-center gap-2 ${
            isCoolingDown ? 'border-warning bg-warning/10' : 'border-border'
          }`}
        >
          {isCoolingDown ? (
            <>
              <div className="flex items-center gap-2">
                <Loader size={14} className="text-warning animate-spin flex-shrink-0" aria-hidden="true" />
                <span className="font-body text-base text-warning">
                  Espera {(timeLeft / 1000).toFixed(1)}s antes de pintar de nuevo
                </span>
              </div>
              <div className="h-1 bg-muted rounded-full overflow-hidden" aria-hidden="true">
                <div
                  className="h-full bg-warning transition-all duration-100"
                  style={{ width: `${(timeLeft / COOLDOWN_MS) * 100}%` }}
                />
              </div>
            </>
          ) : (
            <span className="font-body text-base text-secondary">Listo para pintar</span>
          )}
        </div>
      </div>

      {/* Pixel grid */}
      <PixelCanvasGrid
        width={canvas.width}
        height={canvas.height}
        pixels={displayPixels}
        zoom={zoom}
        minZoom={minZoom}
        onZoomChange={setZoom}
        onFitZoomReady={handleFitZoomReady}
        onPixelClick={handlePixelClick}
        disabled={isCoolingDown || connectionStatus !== 'connected' || !userId}
      />
      </div>{/* end left column */}

      {/* Right: voice + chat sidebar */}
      <div className="w-full lg:w-64 flex-shrink-0 space-y-3">
        <VoiceControls
          voiceStatus={voiceStatus}
          connectedPeers={connectedPeers}
          isMuted={isMuted}
          micError={micError}
          onJoin={joinVoice}
          onLeave={leaveVoice}
          onToggleMute={toggleMute}
        />
        <div className="space-y-2">
          <div className="flex gap-1">
            <button
              onClick={() => setSidebarTab('chat')}
              className={sidebarTab === 'chat' ? 'btn-primary flex-1 text-sm' : 'btn-secondary flex-1 text-sm'}
            >
              Chat
            </button>
            <button
              onClick={() => setSidebarTab('messages')}
              className={`relative flex-1 text-sm ${sidebarTab === 'messages' ? 'btn-primary' : 'btn-secondary'}`}
            >
              Mensajes
              {totalUnread > 0 && (
                <span className="absolute -top-1.5 -right-1.5 bg-destructive text-white text-base w-5 h-5 rounded-full flex items-center justify-center">
                  {totalUnread > 9 ? '9+' : totalUnread}
                </span>
              )}
            </button>
          </div>
          {sidebarTab === 'chat' ? (
            <ChatPanel
              messages={messages}
              onSendMessage={sendMessage}
              currentUserId={userId}
              connectionStatus={chatStatus}
              userDirectory={userDirectory}
            />
          ) : (
            <MessagesPanel currentUserId={userId} token={token} />
          )}
        </div>
      </div>
    </div>
  )
}

export default function CanvasPage() {
  const { id } = useParams()
  const { token, userId, username, avatarUrl, firstName, lastName, nickname } = useAuth()
  const navigate = useNavigate()

  const [canvas, setCanvas] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showProfileModal, setShowProfileModal] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    canvasApi.getById(id, token)
      .then((res) => { if (!cancelled) setCanvas(res.data) })
      .catch((err) => {
        if (cancelled) return
        if (err.response?.status === 404) setError('Este lienzo no existe o fue eliminado.')
        else if (err.code === 'ERR_NETWORK') setError('No se pudo conectar con el servidor de lienzos.')
        else setError(err.response?.data?.error || err.response?.data?.message || 'Error al cargar el lienzo.')
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [id, token])

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-primary/60 backdrop-blur-sm sticky top-0 z-10">
        <div className="max-w-[1600px] mx-auto px-4 py-3 flex items-center gap-4">
          <button
            onClick={() => navigate('/lobby')}
            className="btn-ghost flex items-center gap-2 text-base"
            aria-label="Volver al lobby"
          >
            <ArrowLeft size={16} aria-hidden="true" />
            Lobby
          </button>
          <PixelLogo className="flex-1 justify-center" />
          {username && (
            <button
              onClick={() => setShowProfileModal(true)}
              className="btn-ghost hidden sm:flex items-center gap-2"
              aria-label="Ver mi perfil"
            >
              <Avatar avatarUrl={avatarUrl} firstName={firstName} lastName={lastName} userId={userId} size="sm" />
              <span className="font-body text-lg text-secondary">
                Hola, <span className="text-accent">{username}</span>
                {nickname && <span className="text-secondary"> ({nickname})</span>}
              </span>
            </button>
          )}
        </div>
      </header>

      <main className="max-w-[1600px] mx-auto px-4 py-6">
        {loading ? (
          <div className="flex flex-col items-center justify-center min-h-64 gap-4">
            <Spinner size="lg" />
            <p className="font-body text-xl text-secondary">Cargando lienzo...</p>
          </div>
        ) : error ? (
          <div className="max-w-md mx-auto mt-16">
            <ErrorMessage message={error} className="mb-4" />
            <button onClick={() => navigate('/lobby')} className="btn-secondary w-full">
              Volver al Lobby
            </button>
          </div>
        ) : canvas ? (
          <CanvasEditor canvas={canvas} id={id} userId={userId} token={token} />
        ) : null}
      </main>
      {showProfileModal && <ProfileModal onClose={() => setShowProfileModal(false)} />}
    </div>
  )
}
