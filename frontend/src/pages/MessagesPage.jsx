import { useNavigate } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { useAuth } from '../auth/AuthContext.jsx'
import PixelLogo from '../components/PixelLogo.jsx'
import MessagesPanel from '../components/MessagesPanel.jsx'

export default function MessagesPage() {
  const { userId, token } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-primary/60 backdrop-blur-sm sticky top-0 z-10">
        <div className="max-w-2xl mx-auto px-4 py-3 flex items-center gap-3">
          <button onClick={() => navigate('/lobby')} className="btn-ghost p-1" aria-label="Volver al lobby">
            <ArrowLeft size={18} aria-hidden="true" />
          </button>
          <PixelLogo />
        </div>
      </header>
      <main className="max-w-2xl mx-auto px-4 py-6">
        <MessagesPanel currentUserId={userId} token={token} />
      </main>
    </div>
  )
}
