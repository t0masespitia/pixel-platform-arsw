import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import PixelLogo from '../components/PixelLogo.jsx'
import ErrorMessage from '../components/ErrorMessage.jsx'
import Spinner from '../components/Spinner.jsx'

function mapLoginError(err) {
  if (err.response?.status === 401) return 'Credenciales inválidas. Verifica tu email y contraseña.'
  if (err.response?.status === 400) return err.response.data?.error || err.response.data?.message || 'Datos inválidos.'
  if (err.response?.status === 0 || err.code === 'ERR_NETWORK') return 'No se pudo conectar con el servidor. Verifica que el servicio esté activo.'
  return err.response?.data?.error || err.response?.data?.message || 'Error inesperado. Intenta de nuevo.'
}

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login(email.trim(), password)
    } catch (err) {
      if (err.response?.status === 403 && err.response?.data?.emailVerified === false) {
        navigate('/verify-email', { state: { email: email.trim() } })
        return
      }
      setError(mapLoginError(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-4" style={{ background: 'radial-gradient(ellipse at center, #2A1345 0%, #1B0B2E 70%)' }}>
      <div className="w-full max-w-sm">
        <div className="flex justify-center mb-8">
          <PixelLogo />
        </div>

        <div className="card border-border">
          <h1 className="font-heading text-sm text-foreground mb-6 text-center">INICIAR SESIÓN</h1>

          <form onSubmit={handleSubmit} noValidate>
            <div className="mb-4">
              <label htmlFor="email" className="label-field">Email</label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="input-field"
                placeholder="tu@email.com"
                autoComplete="email"
                autoFocus
                required
              />
            </div>

            <div className="mb-6">
              <label htmlFor="password" className="label-field">Contraseña</label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="input-field"
                placeholder="••••••••"
                autoComplete="current-password"
                required
              />
            </div>

            <ErrorMessage message={error} className="mb-4" />

            <button
              type="submit"
              disabled={loading || !email || !password}
              className="btn-primary w-full flex items-center justify-center gap-2 mb-4"
            >
              {loading && <Spinner size="sm" />}
              {loading ? 'Entrando...' : 'ENTRAR'}
            </button>

            <p className="text-center font-body text-lg text-secondary">
              ¿Sin cuenta?{' '}
              <Link to="/register" className="text-accent hover:text-accent/80 transition-colors underline">
                Registrarse
              </Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  )
}
