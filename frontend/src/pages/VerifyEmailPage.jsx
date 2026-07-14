import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import PixelLogo from '../components/PixelLogo.jsx'
import ErrorMessage from '../components/ErrorMessage.jsx'
import Spinner from '../components/Spinner.jsx'

export default function VerifyEmailPage() {
  const { verifyEmail, resendCode } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const emailFromState = location.state?.email || ''
  const [emailInput, setEmailInput] = useState(emailFromState)
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [resendLoading, setResendLoading] = useState(false)
  const [resendSuccess, setResendSuccess] = useState(false)

  const email = emailFromState || emailInput

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!email || code.length !== 6) {
      setError('Ingresa el código de 6 dígitos.')
      return
    }
    setError('')
    setLoading(true)
    try {
      await verifyEmail(email, code)
      navigate('/lobby', { replace: true })
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Código inválido o expirado.')
    } finally {
      setLoading(false)
    }
  }

  const handleResend = async () => {
    if (!email) return
    setResendSuccess(false)
    setResendLoading(true)
    try {
      await resendCode(email)
      setResendSuccess(true)
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'No se pudo reenviar el código.')
    } finally {
      setResendLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-4" style={{ background: 'radial-gradient(ellipse at center, #2A1345 0%, #1B0B2E 70%)' }}>
      <div className="w-full max-w-sm">
        <div className="flex justify-center mb-8">
          <PixelLogo />
        </div>

        <div className="card border-border">
          <h1 className="font-heading text-sm text-foreground mb-2 text-center">VERIFICAR CORREO</h1>
          <p className="font-body text-base text-secondary text-center mb-6">
            Revisa tu bandeja de entrada y escribe el código de 6 dígitos que te enviamos.
          </p>

          {emailFromState ? (
            <p className="font-body text-base text-secondary mb-4 text-center">
              Verificando: <span className="text-accent">{emailFromState}</span>
            </p>
          ) : (
            <div className="mb-4">
              <label htmlFor="email" className="label-field">Email</label>
              <input
                id="email"
                type="email"
                value={emailInput}
                onChange={(e) => setEmailInput(e.target.value)}
                className="input-field"
                placeholder="tu@email.com"
                autoComplete="email"
                required
              />
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate>
            <div className="mb-6">
              <label htmlFor="code" className="label-field">Código de verificación</label>
              <input
                id="code"
                type="text"
                inputMode="numeric"
                maxLength={6}
                pattern="[0-9]*"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                className="input-field text-center tracking-widest font-mono"
                placeholder="000000"
                autoFocus
                required
              />
            </div>

            <ErrorMessage message={error} className="mb-4" />

            <button
              type="submit"
              disabled={loading}
              className="btn-primary w-full flex items-center justify-center gap-2 mb-4"
            >
              {loading && <Spinner size="sm" />}
              {loading ? 'Verificando...' : 'VERIFICAR'}
            </button>
          </form>

          <div className="text-center">
            <button
              type="button"
              onClick={handleResend}
              disabled={resendLoading || !email}
              className="btn-ghost text-base"
            >
              {resendLoading && <Spinner size="sm" />}
              {resendLoading ? 'Reenviando...' : 'Reenviar código'}
            </button>
            {resendSuccess && (
              <p className="font-body text-base text-success mt-2">Código reenviado.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
