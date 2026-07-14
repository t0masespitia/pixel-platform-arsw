import { useState, useRef } from 'react'
import { Sparkles } from 'lucide-react'
import { aiApi } from '../api/aiApi.js'
import ErrorMessage from '../components/ErrorMessage.jsx'
import Spinner from '../components/Spinner.jsx'

const MODES = [
  { value: 'GRAYSCALE', label: 'Generar en grises' },
  { value: 'COLOR', label: 'Generar a color' },
  { value: 'LIGHT', label: 'Generar muy clarito' },
]

export default function AiTemplatePanel({ canvasId, userId, token, onGenerated }) {
  const [file, setFile] = useState(null)
  const [preview, setPreview] = useState(null)
  const [status, setStatus] = useState('idle')
  const [activeMode, setActiveMode] = useState(null)
  const [result, setResult] = useState(null)
  const [errorMsg, setErrorMsg] = useState('')
  const inputRef = useRef(null)

  const handleFileChange = (e) => {
    const selected = e.target.files[0]
    if (!selected) return
    if (preview) URL.revokeObjectURL(preview)
    setFile(selected)
    setPreview(URL.createObjectURL(selected))
    setStatus('idle')
    setActiveMode(null)
    setResult(null)
    setErrorMsg('')
  }

  const handleGenerate = async (mode) => {
    if (!file || status === 'generating') return
    setStatus('generating')
    setActiveMode(mode)
    setResult(null)
    setErrorMsg('')
    try {
      const res = await aiApi.generateTemplate(token, canvasId, userId, file, mode)
      setResult(res.data)
      setStatus('success')
      onGenerated?.()
    } catch (err) {
      setErrorMsg(err.response?.data?.error || 'Error al generar la plantilla.')
      setStatus('error')
    }
  }

  return (
    <div className="space-y-3">
      <div>
        <label className="label-field block mb-1">Imagen de referencia (PNG, JPG, GIF, BMP)</label>
        <input
          ref={inputRef}
          type="file"
          accept="image/png,image/jpeg,image/jpg,image/gif,image/bmp"
          onChange={handleFileChange}
          className="font-body text-lg text-foreground w-full cursor-pointer"
        />
      </div>

      {preview && (
        <img
          src={preview}
          alt="Vista previa"
          className="max-h-32 border border-border object-contain"
        />
      )}

      <div className="space-y-2">
        {MODES.map(({ value, label }) => (
          <button
            key={value}
            onClick={() => handleGenerate(value)}
            disabled={!file || status === 'generating'}
            className="btn-primary w-full flex items-center justify-center gap-2"
          >
            {status === 'generating' && activeMode === value ? (
              <Spinner size="sm" />
            ) : (
              <Sparkles size={15} aria-hidden="true" />
            )}
            {label}
          </button>
        ))}
      </div>

      {status === 'success' && result && (
        <p className="font-body text-lg text-success">
          Plantilla aplicada: {result.pixelsWritten} píxeles escritos ({result.width} × {result.height}).
        </p>
      )}

      {status === 'error' && <ErrorMessage message={errorMsg} />}
    </div>
  )
}
