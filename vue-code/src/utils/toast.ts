let container: HTMLDivElement | null = null

function getContainer() {
  if (!container) {
    container = document.createElement('div')
    container.className = 'toast-container'
    container.style.cssText = 'position:fixed;top:20px;left:50%;transform:translateX(-50%);z-index:99999;display:flex;flex-direction:column;gap:8px;pointer-events:none;'
    document.body.appendChild(container)
  }
  return container
}

const BG_MAP: Record<string, string> = {
  success: 'rgba(48,209,88,0.92)',
  error: 'rgba(255,69,58,0.92)',
  warning: 'rgba(255,159,10,0.92)',
  info: 'rgba(120,120,128,0.72)',
}

const SVG_NS = 'http://www.w3.org/2000/svg'

function createIcon(type: string): SVGSVGElement | null {
  const svg = document.createElementNS(SVG_NS, 'svg')
  svg.setAttribute('width', '16')
  svg.setAttribute('height', '16')
  svg.setAttribute('viewBox', '0 0 24 24')
  svg.setAttribute('fill', 'none')
  svg.setAttribute('stroke', '#fff')
  svg.setAttribute('stroke-width', '2.5')
  svg.setAttribute('stroke-linecap', 'round')
  svg.setAttribute('stroke-linejoin', 'round')

  const add = (name: string, attributes: Record<string, string>) => {
    const node = document.createElementNS(SVG_NS, name)
    Object.entries(attributes).forEach(([key, value]) => node.setAttribute(key, value))
    svg.appendChild(node)
  }

  if (type === 'success') {
    add('polyline', { points: '20 6 9 17 4 12' })
  } else if (type === 'error') {
    add('line', { x1: '18', y1: '6', x2: '6', y2: '18' })
    add('line', { x1: '6', y1: '6', x2: '18', y2: '18' })
  } else if (type === 'warning') {
    add('path', { d: 'M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z' })
    add('line', { x1: '12', y1: '9', x2: '12', y2: '13' })
    add('line', { x1: '12', y1: '17', x2: '12.01', y2: '17' })
  } else if (type === 'info') {
    add('circle', { cx: '12', cy: '12', r: '10' })
    add('line', { x1: '12', y1: '16', x2: '12', y2: '12' })
    add('line', { x1: '12', y1: '8', x2: '12.01', y2: '8' })
  } else {
    return null
  }

  return svg
}

function show(message: string, type: string = 'info', duration: number = 2500) {
  const el = document.createElement('div')
  el.style.cssText = `
    display:flex;align-items:center;gap:8px;
    padding:10px 18px;border-radius:100px;
    background:${BG_MAP[type] || BG_MAP.info};
    backdrop-filter:blur(20px) saturate(1.8);-webkit-backdrop-filter:blur(20px) saturate(1.8);
    color:#fff;font-size:14px;font-weight:500;
    box-shadow:0 8px 32px rgba(0,0,0,0.18),0 2px 8px rgba(0,0,0,0.08);
    pointer-events:auto;white-space:nowrap;
    animation:toast-in .25s cubic-bezier(.34,1.56,.64,1) forwards;
    font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
  `
  const icon = createIcon(type)
  if (icon) el.appendChild(icon)
  const messageElement = document.createElement('span')
  messageElement.textContent = message
  el.appendChild(messageElement)
  getContainer().appendChild(el)
  setTimeout(() => {
    el.style.animation = 'toast-out .2s ease forwards'
    setTimeout(() => el.remove(), 200)
  }, duration)
}

export const toast = {
  success: (msg: string) => show(msg, 'success'),
  error: (msg: string) => show(msg, 'error'),
  warning: (msg: string) => show(msg, 'warning'),
  info: (msg: string) => show(msg, 'info'),
}
