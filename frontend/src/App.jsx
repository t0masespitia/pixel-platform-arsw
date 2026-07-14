import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext.jsx'
import { DirectMessagesProvider } from './contexts/DirectMessagesContext.jsx'
import LoginPage from './pages/LoginPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import VerifyEmailPage from './pages/VerifyEmailPage.jsx'
import LobbyPage from './pages/LobbyPage.jsx'
import CanvasPage from './pages/CanvasPage.jsx'
import MessagesPage from './pages/MessagesPage.jsx'

function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? children : <Navigate to="/login" replace />
}

function PublicRoute({ children }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? <Navigate to="/lobby" replace /> : children
}

export default function App() {
  return (
    <AuthProvider>
      <DirectMessagesProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<Navigate to="/lobby" replace />} />
            <Route path="/login" element={<PublicRoute><LoginPage /></PublicRoute>} />
            <Route path="/register" element={<PublicRoute><RegisterPage /></PublicRoute>} />
            <Route path="/verify-email" element={<PublicRoute><VerifyEmailPage /></PublicRoute>} />
            <Route path="/lobby" element={<ProtectedRoute><LobbyPage /></ProtectedRoute>} />
            <Route path="/messages" element={<ProtectedRoute><MessagesPage /></ProtectedRoute>} />
            <Route path="/canvas/:id" element={<ProtectedRoute><CanvasPage /></ProtectedRoute>} />
          </Routes>
        </BrowserRouter>
      </DirectMessagesProvider>
    </AuthProvider>
  )
}
