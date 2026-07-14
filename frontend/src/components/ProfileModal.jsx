import { useState, useRef } from 'react'
import { X, Upload, Trash2 } from 'lucide-react'
import { useAuth } from '../auth/AuthContext.jsx'
import { authApi } from '../api/authApi.js'
import Avatar from './Avatar.jsx'
import ErrorMessage from './ErrorMessage.jsx'
import Spinner from './Spinner.jsx'

const ALLOWED_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp']
const MAX_SIZE_BYTES = 15 * 1024 * 1024

export default function ProfileModal({ onClose }) {
  const { token, userId, username, firstName, lastName, avatarUrl, refreshProfile } = useAuth()
  const [uploading, setUploading] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState('')
  const fileInputRef = useRef(null)

  const handleFileSelected = async (e) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return

    setError('')
    if (!ALLOWED_TYPES.includes(file.type)) {
      setError('Formato no soportado. Usa JPG, PNG o WEBP.')
      return
    }
    if (file.size > MAX_SIZE_BYTES) {
      setError('La imagen no puede pesar más de 15MB.')
      return
    }

    setUploading(true)
    try {
      await authApi.uploadAvatar(token, file)
      await refreshProfile()
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Error al subir la imagen.')
    } finally {
      setUploading(false)
    }
  }

  const handleDelete = async () => {
    setError('')
    setDeleting(true)
    try {
      await authApi.deleteAvatar(token)
      await refreshProfile()
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Error al eliminar la imagen.')
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="profile-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ background: 'rgba(9,9,11,0.85)' }}
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="card w-full max-w-sm border-accent shadow-accent-lg">
        <div className="flex items-center justify-between mb-6">
          <h2 id="profile-modal-title" className="font-heading text-sm text-foreground">MI PERFIL</h2>
          <button onClick={onClose} className="btn-ghost p-1" aria-label="Cerrar modal">
            <X size={18} />
          </button>
        </div>

        <div className="flex flex-col items-center gap-3 mb-6">
          <Avatar
            avatarUrl={avatarUrl}
            firstName={firstName}
            lastName={lastName}
            userId={userId}
            size="xl"
          />
          <div className="text-center">
            <p className="font-body text-xl text-foreground">
              {`${firstName || ''} ${lastName || ''}`.trim() || username}
            </p>
            <p className="font-body text-base text-secondary">@{username}</p>
          </div>
        </div>

        <ErrorMessage message={error} className="mb-4" />

        <div className="flex flex-col gap-2">
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            capture=""
            onChange={handleFileSelected}
            className="hidden"
          />
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading || deleting}
            className="btn-primary flex items-center justify-center gap-2"
          >
            {uploading ? <Spinner size="sm" /> : <Upload size={14} aria-hidden="true" />}
            {uploading ? 'Subiendo...' : (avatarUrl ? 'Cambiar foto' : 'Subir foto')}
          </button>
          {avatarUrl && (
            <button
              onClick={handleDelete}
              disabled={uploading || deleting}
              className="btn-secondary flex items-center justify-center gap-2 text-destructive hover:border-destructive"
            >
              {deleting ? <Spinner size="sm" /> : <Trash2 size={14} aria-hidden="true" />}
              {deleting ? 'Eliminando...' : 'Eliminar foto'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
