import { useState, useEffect } from 'react'

const PALETTE = [
  { hex: '#000000', name: 'Negro' },
  { hex: '#FFFFFF', name: 'Blanco' },
  { hex: '#808080', name: 'Gris' },
  { hex: '#C0C0C0', name: 'Gris claro' },
  { hex: '#FF0000', name: 'Rojo' },
  { hex: '#FF8800', name: 'Naranja' },
  { hex: '#FFFF00', name: 'Amarillo' },
  { hex: '#00CC00', name: 'Verde' },
  { hex: '#00CCCC', name: 'Cyan' },
  { hex: '#0000FF', name: 'Azul' },
  { hex: '#8800FF', name: 'Violeta' },
  { hex: '#EC4899', name: 'Magenta' },
  { hex: '#8B4513', name: 'Marron' },
  { hex: '#FF69B4', name: 'Rosa' },
  { hex: '#006400', name: 'Verde oscuro' },
  { hex: '#000080', name: 'Azul marino' },
]

export default function ColorPalette({ selectedColor, onSelectColor }) {
  const [rawText, setRawText] = useState(selectedColor)

  useEffect(() => { setRawText(selectedColor) }, [selectedColor])

  const handleTextChange = (e) => {
    const val = e.target.value
    setRawText(val)
    if (/^#[0-9A-Fa-f]{6}$/.test(val)) {
      onSelectColor(val.toUpperCase())
    }
  }

  const handlePickerChange = (e) => {
    onSelectColor(e.target.value.toUpperCase())
  }

  return (
    <div className="card border-border p-3">
      <p className="label-field mb-2">Color seleccionado</p>
      <div className="flex flex-wrap gap-1.5 mb-3">
        {PALETTE.map(({ hex, name }) => (
          <button
            key={hex}
            onClick={() => onSelectColor(hex)}
            title={name}
            aria-label={`${name} (${hex})`}
            aria-pressed={selectedColor.toUpperCase() === hex}
            className="cursor-pointer transition-transform hover:scale-110 focus:outline-none"
            style={{
              width: 20,
              height: 20,
              backgroundColor: hex,
              border:
                selectedColor.toUpperCase() === hex
                  ? '2px solid #EC4899'
                  : '2px solid #6D3FA0',
              boxShadow:
                selectedColor.toUpperCase() === hex
                  ? '0 0 6px #EC4899'
                  : 'none',
            }}
          />
        ))}
      </div>

      <div className="flex items-center gap-2">
        <label htmlFor="color-picker" className="label-field mb-0 whitespace-nowrap">
          Personalizado
        </label>
        <input
          id="color-picker"
          type="color"
          value={selectedColor}
          onChange={handlePickerChange}
          className="cursor-pointer border border-border bg-muted"
          style={{ width: 36, height: 28, padding: 2 }}
          aria-label="Selector de color libre"
        />
        <input
          type="text"
          value={rawText}
          onChange={handleTextChange}
          maxLength={7}
          placeholder="#RRGGBB"
          className="input-field font-mono"
          style={{ width: 90 }}
          aria-label="Valor hexadecimal del color"
          spellCheck={false}
        />
        <div
          aria-hidden="true"
          style={{
            width: 28,
            height: 28,
            backgroundColor: selectedColor,
            border: '1px solid #6D3FA0',
            flexShrink: 0,
          }}
          title={`Color actual: ${selectedColor}`}
        />
      </div>
    </div>
  )
}
