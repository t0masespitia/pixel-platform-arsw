import { useState } from 'react'
import { X } from 'lucide-react'
import ErrorMessage from './ErrorMessage.jsx'
import Spinner from './Spinner.jsx'

export default function CreateCanvasModal({ onClose, onCreate }) {
  const [name, setName] = useState('')
  const [size, setSize] = useState(100)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const validate = () => {
    if (!name.trim()) return 'El nombre es requerido.'
    if (name.trim().length > 100) return 'El nombre no puede superar 100 caracteres.'
    if (!Number.isInteger(Number(size)) || Number(size) < 32 || Number(size) > 500)
      return 'El tamaño debe ser un número entero entre 32 y 500.'
    return null
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    const validationError = validate()
    if (validationError) { setError(validationError); return }
    setError('')
    setLoading(true)
    try {
      await onCreate(name.trim(), Number(size), Number(size))
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || err.message || 'Error al crear el lienzo.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ background: 'rgba(9,9,11,0.85)' }}
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="card w-full max-w-md border-accent shadow-accent-lg">
        <div className="flex items-center justify-between mb-6">
          <h2 id="modal-title" className="font-heading text-sm text-foreground">NUEVO LIENZO</h2>
          <button onClick={onClose} className="btn-ghost p-1" aria-label="Cerrar modal">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} noValidate>
          <div className="mb-4">
            <label htmlFor="canvas-name" className="label-field">Nombre</label>
            <input
              id="canvas-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="input-field"
              placeholder="Mi lienzo épico"
              maxLength={100}
              autoFocus
            />
          </div>

          <div className="mb-4">
            <label htmlFor="canvas-size" className="label-field">Tamaño del lienzo (píxeles por lado)</label>
            <input
              id="canvas-size"
              type="number"
              value={size}
              onChange={(e) => setSize(e.target.value)}
              className="input-field"
              min={32}
              max={500}
            />
            <p className="font-body text-base text-secondary mt-1">
              El lienzo será cuadrado de {size || 0}×{size || 0} píxeles.
            </p>
          </div>

          <ErrorMessage message={error} className="mb-4" />

          <div className="flex gap-3 justify-end">
            <button type="button" onClick={onClose} className="btn-secondary">
              Cancelar
            </button>
            <button type="submit" disabled={loading} className="btn-primary flex items-center gap-2">
              {loading && <Spinner size="sm" />}
              {loading ? 'Creando...' : 'Crear'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
