export default function PixelLogo({ className = '' }) {
  return (
    <div className={`flex items-center gap-3 ${className}`}>
      <svg width="32" height="32" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <rect x="8" y="8" width="8" height="8" fill="#EC4899"/>
        <rect x="16" y="8" width="8" height="8" fill="#6D3FA0"/>
        <rect x="8" y="16" width="8" height="8" fill="#6D3FA0"/>
        <rect x="16" y="16" width="8" height="8" fill="#EC4899"/>
      </svg>
      <span className="font-heading text-sm text-accent text-glow-accent tracking-widest">
        PIXEL<span className="text-foreground">PLATFORM</span>
      </span>
    </div>
  )
}
