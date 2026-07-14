import { useRef, useEffect, useLayoutEffect, useState, useCallback } from 'react'

const MAX_VIEWPORT_SIZE = 1200
const MIN_ZOOM = 1
const MAX_ZOOM = 100

export default function PixelCanvasGrid({ width, height, pixels, zoom, minZoom = MIN_ZOOM, onZoomChange, onFitZoomReady, onPixelClick, disabled }) {
  const canvasRef = useRef(null)
  const containerRef = useRef(null)
  const pendingScrollRef = useRef(null)
  const pinchStateRef = useRef(null)
  const frozenSizeRef = useRef(null)
  const hasReportedFitZoom = useRef(false)
  const dragStateRef = useRef(null)
  const hasDraggedRef = useRef(false)
  const [measuredSize, setMeasuredSize] = useState(null)
  const [isDragging, setIsDragging] = useState(false)
  const canvasWidth = width * zoom
  const canvasHeight = height * zoom

  // Se mide UNA sola vez, al montar. Este es el UNICO lugar donde se decide el
  // tamaño del marco (frozenSizeRef) — despues de esto, el marco nunca vuelve
  // a cambiar de tamano, sin importar el zoom.
  useLayoutEffect(() => {
    if (frozenSizeRef.current || !containerRef.current) return
    const measuredWidth = containerRef.current.getBoundingClientRect().width
    const availableHeight = Math.max(200, window.innerHeight - containerRef.current.getBoundingClientRect().top - 16)
    const a = Math.max(200, Math.floor(availableHeight))
    const targetWidth = Math.max(200, Math.min(measuredWidth, a * 2))
    frozenSizeRef.current = { width: targetWidth, height: a }
    setMeasuredSize(a)
  }, [])

  useEffect(() => {
    if (measuredSize && !hasReportedFitZoom.current && onFitZoomReady) {
      const fitZoom = Math.max(1, Math.floor(measuredSize / Math.max(width, height)))
      hasReportedFitZoom.current = true
      onFitZoomReady(fitZoom)
    }
  }, [measuredSize, width, height, onFitZoomReady])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !zoom) return
    canvas.width = canvasWidth
    canvas.height = canvasHeight
    const ctx = canvas.getContext('2d')
    ctx.imageSmoothingEnabled = false
    ctx.fillStyle = '#FFFFFF'
    ctx.fillRect(0, 0, canvas.width, canvas.height)

    Object.entries(pixels).forEach(([key, color]) => {
      const [x, y] = key.split(',').map(Number)
      if (!Number.isInteger(x) || !Number.isInteger(y)) return
      ctx.fillStyle = color
      ctx.fillRect(x * zoom, y * zoom, zoom, zoom)
    })

    if (zoom >= 8) {
      ctx.beginPath()
      ctx.strokeStyle = 'rgba(0, 0, 0, 0.08)'
      ctx.lineWidth = 1
      for (let x = 0; x <= width; x += 1) {
        const px = x * zoom + 0.5
        ctx.moveTo(px, 0)
        ctx.lineTo(px, canvas.height)
      }
      for (let y = 0; y <= height; y += 1) {
        const py = y * zoom + 0.5
        ctx.moveTo(0, py)
        ctx.lineTo(canvas.width, py)
      }
      ctx.stroke()
    }

    if (pendingScrollRef.current) {
      const container = containerRef.current
      if (container) {
        container.scrollLeft = pendingScrollRef.current.scrollLeft
        container.scrollTop = pendingScrollRef.current.scrollTop
      }
      pendingScrollRef.current = null
    }
  }, [pixels, width, height, zoom, canvasWidth, canvasHeight])

  const applyZoom = useCallback((newZoom) => {
    const clamped = Math.min(MAX_ZOOM, Math.max(minZoom, newZoom))
    if (clamped !== zoom) onZoomChange(clamped)
  }, [zoom, minZoom, onZoomChange])

  const handleWheel = useCallback((e) => {
    e.preventDefault()
    const direction = e.deltaY < 0 ? 1 : -1
    applyZoom(zoom + direction)
  }, [zoom, applyZoom])

  const pinchDistance = (touches) => {
    const dx = touches[0].clientX - touches[1].clientX
    const dy = touches[0].clientY - touches[1].clientY
    return Math.hypot(dx, dy)
  }

  const handleTouchStart = useCallback((e) => {
    if (e.touches.length === 2) {
      pinchStateRef.current = {
        startDistance: pinchDistance(e.touches),
        startZoom: zoom,
      }
    }
  }, [zoom])

  const handleTouchMove = useCallback((e) => {
    if (e.touches.length === 2 && pinchStateRef.current) {
      e.preventDefault()
      const { startDistance, startZoom } = pinchStateRef.current
      const currentDistance = pinchDistance(e.touches)
      const newZoom = Math.round(startZoom * (currentDistance / startDistance))
      applyZoom(newZoom)
    }
  }, [applyZoom])

  const handleTouchEnd = useCallback((e) => {
    if (e.touches.length < 2) {
      pinchStateRef.current = null
    }
  }, [])

  const handleMouseDown = useCallback((e) => {
    if (e.button !== 0) return
    const container = containerRef.current
    if (!container) return
    dragStateRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      startScrollLeft: container.scrollLeft,
      startScrollTop: container.scrollTop,
    }
    hasDraggedRef.current = false
    setIsDragging(true)

    const onMouseMove = (me) => {
      if (!dragStateRef.current) return
      const { startX, startY, startScrollLeft, startScrollTop } = dragStateRef.current
      const dx = me.clientX - startX
      const dy = me.clientY - startY
      if (Math.abs(dx) > 4 || Math.abs(dy) > 4) {
        hasDraggedRef.current = true
      }
      container.scrollLeft = startScrollLeft - dx
      container.scrollTop = startScrollTop - dy
    }

    const onMouseUp = () => {
      dragStateRef.current = null
      setIsDragging(false)
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('mouseup', onMouseUp)
    }

    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  }, [])

  const handleClick = (e) => {
    if (hasDraggedRef.current) {
      hasDraggedRef.current = false
      return
    }
    if (disabled) return
    const canvas = canvasRef.current
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()
    const scaleX = canvas.width / rect.width
    const scaleY = canvas.height / rect.height
    const x = Math.floor(((e.clientX - rect.left) * scaleX) / zoom)
    const y = Math.floor(((e.clientY - rect.top) * scaleY) / zoom)
    if (x >= 0 && x < width && y >= 0 && y < height) {
      onPixelClick(x, y)
    }
  }

  // Una sola caja controla el marco. Antes de medir, se reserva espacio con
  // aspect-ratio para evitar un salto de layout. Despues de medir, el tamano
  // queda fijo en pixeles (frozenSizeRef) para siempre, sin importar el zoom.
  const containerStyle = frozenSizeRef.current
    ? {
        overflow: 'auto',
        width: `${frozenSizeRef.current.width}px`,
        height: `${frozenSizeRef.current.height}px`,
        touchAction: 'pan-x pan-y',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }
    : {
        width: '100%',
        maxWidth: `${MAX_VIEWPORT_SIZE}px`,
        aspectRatio: '2 / 1',
      }

  return (
    <div
      ref={containerRef}
      className="canvas-scroll-container mx-auto"
      onWheel={handleWheel}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
      onMouseDown={handleMouseDown}
      style={containerStyle}
    >
      {frozenSizeRef.current && zoom && (
        <canvas
          ref={canvasRef}
          onClick={handleClick}
          style={{
            cursor: isDragging ? 'grabbing' : disabled ? 'not-allowed' : 'crosshair',
            imageRendering: 'pixelated',
            display: 'block',
            flexShrink: 0,
            width: `${canvasWidth}px`,
            height: `${canvasHeight}px`,
            border: '1px solid #6D3FA0',
          }}
          aria-label={`Lienzo de píxeles ${width}×${height}. ${disabled ? 'Painting deshabilitado durante cooldown.' : 'Haz clic para pintar.'}`}
          role="img"
        />
      )}
    </div>
  )
}
