import { AlertTriangle } from 'lucide-react'

export default function ErrorMessage({ message, className = '' }) {
  if (!message) return null
  return (
    <div
      role="alert"
      className={`flex items-start gap-2 bg-destructive/10 border border-destructive text-destructive px-3 py-2 font-body text-lg ${className}`}
    >
      <AlertTriangle size={18} className="flex-shrink-0 mt-0.5" aria-hidden="true" />
      <span>{message}</span>
    </div>
  )
}
