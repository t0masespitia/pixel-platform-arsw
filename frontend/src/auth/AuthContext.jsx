import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { authApi } from '../api/authApi.js'

const AuthContext = createContext(null)

function decodeJwtPayload(token) {
  try {
    const payload = token.split('.')[1]
    const decoded = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
    return decoded
  } catch {
    return null
  }
}

function isTokenExpired(token) {
  const payload = decodeJwtPayload(token)
  if (!payload || !payload.exp) return true
  return Date.now() / 1000 > payload.exp
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(null)
  const [username, setUsername] = useState(null)
  const [userId, setUserId] = useState(null)
  const [email, setEmail] = useState(null)
  const [loading, setLoading] = useState(true)

  const [avatarUrl, setAvatarUrl] = useState(null)
  const [firstName, setFirstName] = useState(null)
  const [lastName, setLastName] = useState(null)

  useEffect(() => {
    const storedToken = localStorage.getItem('pp_token')
    if (storedToken && !isTokenExpired(storedToken)) {
      const payload = decodeJwtPayload(storedToken)
      setToken(storedToken)
      setUsername(localStorage.getItem('pp_username'))
      setEmail(payload?.sub || null)
      setUserId(String(payload?.userId || ''))
    } else {
      localStorage.removeItem('pp_token')
      localStorage.removeItem('pp_username')
    }
    setLoading(false)
  }, [])

  const refreshProfile = useCallback(async () => {
    if (!token || !userId) return
    try {
      const res = await authApi.getUserProfile(userId, token)
      setFirstName(res.data.firstName || null)
      setLastName(res.data.lastName || null)
      setAvatarUrl(res.data.avatarUrl || null)
    } catch {
      // Si falla, el header simplemente sigue mostrando iniciales/username; no es crítico.
    }
  }, [token, userId])

  useEffect(() => { refreshProfile() }, [refreshProfile])

  const persistSession = useCallback((authResponse) => {
    const { token: newToken, username: newUsername } = authResponse
    const payload = decodeJwtPayload(newToken)
    setToken(newToken)
    setUsername(newUsername)
    setEmail(payload?.sub || null)
    setUserId(String(payload?.userId || ''))
    localStorage.setItem('pp_token', newToken)
    localStorage.setItem('pp_username', newUsername)
  }, [])

  const login = useCallback(async (emailVal, password) => {
    const response = await authApi.login(emailVal, password)
    persistSession(response.data)
    return response.data
  }, [persistSession])

  const register = useCallback(async (firstName, lastName, emailVal, password) => {
    const response = await authApi.register(firstName, lastName, emailVal, password)
    return response.data
  }, [])

  const verifyEmail = useCallback(async (emailVal, code) => {
    const response = await authApi.verifyEmail(emailVal, code)
    persistSession(response.data)
    return response.data
  }, [persistSession])

  const resendCode = useCallback(async (emailVal) => {
    const response = await authApi.resendCode(emailVal)
    return response.data
  }, [])

  const logout = useCallback(() => {
    setToken(null)
    setUsername(null)
    setUserId(null)
    setEmail(null)
    setAvatarUrl(null)
    setFirstName(null)
    setLastName(null)
    localStorage.removeItem('pp_token')
    localStorage.removeItem('pp_username')
  }, [])

  return (
    <AuthContext.Provider value={{
      token,
      username,
      userId,
      email,
      avatarUrl,
      firstName,
      lastName,
      refreshProfile,
      isAuthenticated: !!token,
      loading,
      login,
      register,
      verifyEmail,
      resendCode,
      logout,
    }}>
      {!loading && children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
