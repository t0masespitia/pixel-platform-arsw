import { useState, useEffect, useCallback, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export function useCanvasSocket(canvasId, userId, token) {
  const [pixels, setPixels] = useState({})
  const [connectionStatus, setConnectionStatus] = useState('connecting')
  const [socketError, setSocketError] = useState('')
  const clientRef = useRef(null)

  useEffect(() => {
    if (!canvasId) return

    const baseUrl = import.meta.env.VITE_CANVAS_SERVICE_URL || 'http://localhost:8082'

    const client = new Client({
      webSocketFactory: () => new SockJS(`${baseUrl}/ws-canvas`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      debug: (str) => console.debug('[STOMP canvas]', str),
      onConnect: () => {
        console.debug('[Canvas socket connected]', { canvasId, userId })
        setSocketError('')
        setConnectionStatus('connected')

        client.subscribe(`/app/canvas/${canvasId}/state`, (msg) => {
          const data = JSON.parse(msg.body)
          console.debug('[Canvas snapshot]', data)
          setPixels(data.pixels || {})
        })

        client.subscribe(`/topic/canvas/${canvasId}`, (msg) => {
          const pixel = JSON.parse(msg.body)
          console.debug('[Canvas pixel broadcast]', pixel)
          setPixels((prev) => ({ ...prev, [`${pixel.x},${pixel.y}`]: pixel.color }))
        })

        client.subscribe(`/topic/canvas/${canvasId}/bulk-update`, (msg) => {
          const data = JSON.parse(msg.body)
          console.debug('[Canvas bulk snapshot]', data)
          setPixels(data.pixels || {})
        })
      },
      onDisconnect: () => setConnectionStatus('disconnected'),
      onWebSocketClose: () => setConnectionStatus('disconnected'),
      onWebSocketError: (event) => {
        console.error('[Canvas WebSocket error]', event)
        setSocketError('Error de WebSocket con el lienzo.')
        setConnectionStatus('error')
      },
      onStompError: (frame) => {
        console.error('[STOMP ERROR]', frame.headers, frame.body)
        setSocketError(frame.body || 'Error STOMP con el lienzo.')
        setConnectionStatus('error')
      },
    })

    clientRef.current = client
    client.activate()

    return () => {
      client.deactivate()
    }
  }, [canvasId, token])

  const paintPixel = useCallback(
    (x, y, color) => {
      const client = clientRef.current
      if (!userId) {
        console.error('[Canvas] No se puede pintar: userId vacío')
        setSocketError('No se puede pintar: sesión sin userId.')
        return false
      }
      if (!client || !client.connected) {
        console.error('[Canvas] No se puede pintar: socket no conectado')
        setSocketError('No se puede pintar: socket no conectado.')
        return false
      }
      console.debug('[Canvas publish pixel]', { canvasId, userId, x, y, color })
      client.publish({
        destination: `/app/canvas/${canvasId}/pixel`,
        body: JSON.stringify({ userId, x, y, color }),
      })
      return true
    },
    [canvasId, userId],
  )

  return { pixels, connectionStatus, socketError, paintPixel }
}
