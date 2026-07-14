import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import PixelLogo from '../components/PixelLogo.jsx'
import ErrorMessage from '../components/ErrorMessage.jsx'
import Spinner from '../components/Spinner.jsx'

const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*\d).{8,}$/

function validateClient(firstName, lastName, email, password) {
  if (!firstName) return 'El nombre es obligatorio.'
  if (!lastName) return 'El apellido es obligatorio.'
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return 'Ingresa un email válido.'
  if (!PASSWORD_REGEX.test(password)) return 'La contraseña debe tener mínimo 8 caracteres, una mayúscula y un número.'
  return null
}

function mapRegisterError(err) {
  if (err.response?.status === 400) {
    const msg = err.response.data?.error || err.response.data?.message || ''
    if (msg.toLowerCase().includes('email')) return 'Este email ya está registrado.'
    return msg || 'Datos inválidos. Revisa los campos.'
  }
  if (err.code === 'ERR_NETWORK') return 'No se pudo conectar con el servidor.'
  return err.response?.data?.error || err.response?.data?.message || 'Error inesperado. Intenta de nuevo.'
}

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    const clientError = validateClient(firstName.trim(), lastName.trim(), email.trim(), password)
    if (clientError) { setError(clientError); return }
    setError('')
    setLoading(true)
    try {
      await register(firstName.trim(), lastName.trim(), email.trim(), password, nickname.trim())
      navigate('/verify-email', { state: { email: email.trim() } })
    } catch (err) {
      setError(mapRegisterError(err))
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
          <h1 className="font-heading text-sm text-foreground mb-6 text-center">CREAR CUENTA</h1>

          <form onSubmit={handleSubmit} noValidate>
            <div className="mb-4">
              <label htmlFor="firstName" className="label-field">Nombre</label>
              <input
                id="firstName"
                type="text"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                className="input-field"
                placeholder="Ana"
                autoComplete="given-name"
                autoFocus
                required
              />
            </div>

            <div className="mb-4">
              <label htmlFor="lastName" className="label-field">Apellido</label>
              <input
                id="lastName"
                type="text"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                className="input-field"
                placeholder="García"
                autoComplete="family-name"
                required
              />
            </div>

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
                required
              />
            </div>

            <div className="mb-6">
              <label htmlFor="password" className="label-field">
                Contraseña
                <span className="ml-1 text-secondary font-body text-base normal-case">(mín. 8, 1 mayúscula, 1 número)</span>
              </label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="input-field"
                placeholder="••••••••"
                autoComplete="new-password"
                minLength={8}
                required
              />
            </div>

            <div className="mb-6">
              <label htmlFor="nickname" className="label-field">
                Apodo
                <span className="ml-1 text-secondary font-body text-base normal-case">(opcional)</span>
              </label>
              <input
                id="nickname"
                type="text"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                className="input-field"
                placeholder="Como quieres que te llamen"
                autoComplete="nickname"
              />
            </div>

            <ErrorMessage message={error} className="mb-4" />

            <button
              type="submit"
              disabled={loading}
              className="btn-primary w-full flex items-center justify-center gap-2 mb-4"
            >
              {loading && <Spinner size="sm" />}
              {loading ? 'Registrando...' : 'CREAR CUENTA'}
            </button>

            <p className="text-center font-body text-lg text-secondary">
              ¿Ya tienes cuenta?{' '}
              <Link to="/login" className="text-accent hover:text-accent/80 transition-colors underline">
                Iniciar sesión
              </Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  )
}
