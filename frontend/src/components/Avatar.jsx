import { useState } from 'react'

const ASSET_BASE_URL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080'

const PALETTE = ['#EC4899', '#22C55E', '#F59E0B', '#3B82F6', '#A855F7', '#14B8A6']

const SIZES = { sm: 24, md: 32, lg: 48, xl: 80 }

function colorForId(seed) {
  const str = String(seed || '')
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = (hash * 31 + str.charCodeAt(i)) >>> 0
  }
  return PALETTE[hash % PALETTE.length]
}

function initials(firstName, lastName) {
  const a = (firstName || '').trim().charAt(0).toUpperCase()
  const b = (lastName || '').trim().charAt(0).toUpperCase()
  return `${a}${b}` || '?'
}

/**
 * Avatar reutilizable con iniciales de respaldo.
 * Si `avatarUrl` viene null, o si la imagen falla al cargar (404, etc),
 * cae automáticamente a un círculo de color con las iniciales.
 */
export default function Avatar({ avatarUrl, firstName, lastName, userId, size = 'md', className = '' }) {
  const [imgFailed, setImgFailed] = useState(false)
  const px = SIZES[size] || SIZES.md
  const showImage = Boolean(avatarUrl) && !imgFailed

  if (showImage) {
    return (
      <img
        src={`${ASSET_BASE_URL}${avatarUrl}`}
        alt=""
        onError={() => setImgFailed(true)}
        className={`rounded-full object-cover flex-shrink-0 border border-border ${className}`}
        style={{ width: px, height: px }}
      />
    )
  }

  return (
    <div
      className={`rounded-full flex items-center justify-center flex-shrink-0 font-heading text-white ${className}`}
      style={{
        width: px,
        height: px,
        backgroundColor: colorForId(userId || `${firstName}${lastName}`),
        fontSize: Math.max(Math.round(px * 0.36), 9),
      }}
      aria-hidden="true"
    >
      {initials(firstName, lastName)}
    </div>
  )
}
