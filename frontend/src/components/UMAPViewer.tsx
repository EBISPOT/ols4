import React, { useEffect, useRef } from "react";

interface UMAPViewerOptions {
  dataPath?: string;
  width?: string;
  height?: string;
  background?: string;
  olsUrl?: string;
  onReady?: (info: { count: number; ontologies: string[] }) => void;
  onHover?: (info: { index: number; name: string; count: number } | null) => void;
}

interface UMAPViewerAPI {
  setZoom: (z: number) => void;
  setCenter: (x: number, y: number) => void;
  highlight: (ontIndex: number | null) => void;
  getState: () => { centerX: number; centerY: number; scale: number; highlightedOnt: number | null };
  destroy: () => void;
}

interface UMAPViewerProps {
  dataPath?: string;
  width?: string;
  height?: string;
  background?: string;
  olsUrl?: string;
  onReady?: (info: { count: number; ontologies: string[] }) => void;
  onHover?: (info: { index: number; name: string; count: number } | null) => void;
}

const UMAPViewer: React.FC<UMAPViewerProps> = ({
  dataPath,
  width = "100%",
  height = "600px",
  background = "#ffffff",
  onReady,
  onHover,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const viewerRef = useRef<UMAPViewerAPI | null>(null);
  const olsUrl = `${process.env.PUBLIC_URL}/ontologies/{ontology}/entities/{iri}`;

  useEffect(() => {
    if (!containerRef.current) return;

    viewerRef.current = createUMAPViewer(containerRef.current, {
      dataPath,
      width: "100%",
      height: "100%",
      background,
      olsUrl,
      onReady,
      onHover,
    });

    return () => {
      if (viewerRef.current) {
        viewerRef.current.destroy();
        viewerRef.current = null;
      }
    };
  }, [dataPath, background, olsUrl, onReady, onHover]);

  return (
    <div 
      ref={containerRef} 
      style={{ 
        width, 
        height,
        position: 'relative',
      }} 
    />
  );
};

export default UMAPViewer;

const VERTEX_SHADER_SOURCE = `#version 300 es
  in vec2 aPos;
  in vec3 aColor;
  in float aOnt;
  uniform mat3 uTransform;
  uniform float uPointSize;
  uniform float uHighlight;
  out vec3 vColor;
  out float vAlpha;
  void main() {
    vec3 pos = uTransform * vec3(aPos, 1.0);
    gl_Position = vec4(pos.xy, 0.0, 1.0);
    gl_PointSize = uPointSize;
    if (uHighlight < 0.0 || abs(aOnt - uHighlight) < 0.5) {
      vColor = aColor;
      vAlpha = 1.0;
    } else {
      vColor = aColor * 0.4 + 0.6;
      vAlpha = 0.7;
    }
  }
`;

const FRAGMENT_SHADER_SOURCE = `#version 300 es
  precision mediump float;
  in vec3 vColor;
  in float vAlpha;
  out vec4 fragColor;
  void main() {
    vec2 c = gl_PointCoord * 2.0 - 1.0;
    float r = dot(c, c);
    if (r > 1.0) discard;
    float alpha = smoothstep(1.0, 0.2, r) * 0.85 * vAlpha;
    fragColor = vec4(vColor, alpha);
  }
`;

const GRID_SIZE = 256;

function createUMAPViewer(containerSelector: string | HTMLElement, options: UMAPViewerOptions = {}): UMAPViewerAPI {
  const container = typeof containerSelector === 'string' 
    ? document.querySelector(containerSelector) as HTMLElement
    : containerSelector;
  
  if (!container) throw new Error('Container not found');
  
  const config = {
    dataPath: options.dataPath || 'data/',
    width: options.width || '100%',
    height: options.height || '100%',
    background: options.background || '#ffffff',
    olsUrl: options.olsUrl || 'https://www.ebi.ac.uk/ols4/ontologies/{ontology}/entities/{iri}',
    onReady: options.onReady || null,
    onHover: options.onHover || null,
  };
  
  if (!config.dataPath.endsWith('/')) config.dataPath += '/';
  
  container.innerHTML = `
    <div class="umap-viewer" style="position:relative;width:${config.width};height:${config.height};background:${config.background};overflow:hidden;font-family:system-ui,sans-serif;">
      <canvas class="umap-canvas" style="position:absolute;top:0;left:0;cursor:grab;"></canvas>
      <canvas class="umap-labels-canvas" style="position:absolute;top:0;left:0;pointer-events:none;"></canvas>
      <div class="umap-loading" style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);color:#666;font-size:18px;background:rgba(255,255,255,0.9);padding:12px 20px;border-radius:8px;">Loading...</div>
      <div class="umap-legend" style="position:absolute;top:0;right:0;background:rgba(255,255,255,0.95);padding:12px;max-height:100%;overflow-y:auto;font-size:11px;color:#333;max-width:180px;"></div>
      <div class="umap-tooltip" style="position:fixed;background:rgba(50,50,50,0.95);color:#fff;padding:6px 10px;border-radius:4px;font-size:11px;pointer-events:none;display:none;box-shadow:0 2px 8px rgba(0,0,0,0.2);max-width:250px;"></div>
    </div>
    <style>
      @keyframes umap-spin { to { transform: rotate(360deg); } }
      .umap-viewer .umap-canvas.loading { filter: blur(8px); transition: filter 0.3s; }
      .umap-viewer .umap-labels-canvas.loading { filter: blur(8px); transition: filter 0.3s; }
      .umap-viewer .umap-legend::-webkit-scrollbar { width: 6px; }
      .umap-viewer .umap-legend::-webkit-scrollbar-thumb { background: #ccc; border-radius: 3px; }
      .umap-leg { display: flex; align-items: center; gap: 6px; padding: 3px 0; cursor: pointer; }
      .umap-leg:hover { background: rgba(0,0,0,0.05); margin: 0 -6px; padding: 3px 6px; }
      .umap-leg.off { opacity: 0.35; }
      .umap-leg.on { font-weight: 600; }
    </style>
  `;
  
  const root = container.querySelector('.umap-viewer') as HTMLDivElement;
  const canvas = root.querySelector('.umap-canvas') as HTMLCanvasElement;
  const gl = canvas.getContext('webgl2', { antialias: false, alpha: false })!;
  
  const labelsCanvas = root.querySelector('.umap-labels-canvas') as HTMLCanvasElement;
  const labelsCtx = labelsCanvas.getContext('2d')!;
  
  gl.clearColor(1.0, 1.0, 1.0, 1.0);
  gl.clear(gl.COLOR_BUFFER_BIT);
  
  const legend = root.querySelector('.umap-legend') as HTMLDivElement;
  const loading = root.querySelector('.umap-loading') as HTMLDivElement;
  
  // State
  let META: any;
  let ontologies: Uint16Array;
  let xCoords: Float32Array;
  let yCoords: Float32Array;
  let labels: string[] | null = null;
  let iris: string[] | null = null;
  let labelsLoading = false;
  let irisLoading = false;
  let ontCounts = new Map<number, number>();
  let highlightedOnt: number | null = null;
  let legendHovered = false;
  let ontColors: number[][] = [];
  
  let centerX = 0.5, centerY = 0.5, scale = 1;
  let targetCX = 0.5, targetCY = 0.5, targetScale = 1;
  let isDragging = false, lastMX: number, lastMY: number;
  
  let program: WebGLProgram;
  let posBuffer: WebGLBuffer;
  let uTransform: WebGLUniformLocation;
  let uPointSize: WebGLUniformLocation;
  let uHighlight: WebGLUniformLocation;
  let lodBuffers: WebGLBuffer[] = [];
  let lodCounts: number[] = [];
  
  let hiResLoaded = false, hiResLoading = false;
  
  let spatialGrid: number[][] | null = null;
  let legendWidth = 0;
  let animatingToOntology = false; 
  
  let needsRender = true;
  let lastCenterX = 0, lastCenterY = 0, lastScale = 0, lastHighlight: number | null = null;
  
  let hoveredPoint = -1;
  
  function clampCenter(clampTargets = false) {
    const halfViewW = 0.5 / scale;
    const halfViewH = 0.5 / scale;
    centerX = Math.max(halfViewW, Math.min(1 - halfViewW, centerX));
    centerY = Math.max(halfViewH, Math.min(1 - halfViewH, centerY));
    
    if (clampTargets) {
      const halfTargetW = 0.5 / targetScale;
      const halfTargetH = 0.5 / targetScale;
      targetCX = Math.max(halfTargetW, Math.min(1 - halfTargetW, targetCX));
      targetCY = Math.max(halfTargetH, Math.min(1 - halfTargetH, targetCY));
    }
  }

  async function loadData() {
    loading.textContent = 'Loading...';
    
    const response = await fetch(config.dataPath + 'umap.bin.gz');
    const buffer = await new Response(response.body!.pipeThrough(new DecompressionStream('gzip'))).arrayBuffer();
    
    const view = new DataView(buffer);
    const metaLen = view.getUint32(0, true);
    const metaJson = new TextDecoder().decode(new Uint8Array(buffer, 4, metaLen));
    META = JSON.parse(metaJson);
    ontColors = generateColors(META.ontologies.length);
    
    const n = META.count;
    const headerSize = 4 + metaLen;
    const coordsD = new Int8Array(buffer, headerSize, n * 2);
    const oD = new Int16Array(buffer, headerSize + n * 2, n);

    xCoords = new Float32Array(n);
    yCoords = new Float32Array(n);
    ontologies = new Uint16Array(n);

    let x = 0, y = 0, o = 0;
    for (let i = 0; i < n; i++) {
      x += coordsD[i * 2]; y += coordsD[i * 2 + 1]; o += oD[i];
      xCoords[i] = (x + Math.random() - 0.5) / 255;
      yCoords[i] = (y + Math.random() - 0.5) / 255;
      ontologies[i] = o;
      ontCounts.set(o, (ontCounts.get(o) || 0) + 1);
    }

    buildSpatialIndex();
    buildLegend();
    initGL();
    loading.style.display = 'none';
    initControls();
    animate();
    
    if (config.onReady) config.onReady({ count: n, ontologies: META.ontologies });
  }

  async function loadLabels() {
    if (labels || labelsLoading) return;
    labelsLoading = true;
    try {
      const r = await fetch(config.dataPath + 'labels.txt.gz');
      labels = (await new Response(r.body!.pipeThrough(new DecompressionStream('gzip'))).text()).split('\n');
      console.log('Labels loaded');
    } catch(e) { console.warn('Labels not loaded'); }
    labelsLoading = false;
  }

  async function loadIris() {
    if (iris || irisLoading) return;
    irisLoading = true;
    try {
      const r = await fetch(config.dataPath + 'iris.txt.gz');
      iris = (await new Response(r.body!.pipeThrough(new DecompressionStream('gzip'))).text()).split('\n');
      console.log('IRIs loaded');
    } catch(e) { console.warn('IRIs not loaded'); }
    irisLoading = false;
  }

  async function loadHiRes() {
    if (hiResLoaded || hiResLoading) return;
    hiResLoading = true;
    
    try {
      const coordsR = await fetch(config.dataPath + 'umap16.bin.gz');
      const coordsB = await new Response(coordsR.body!.pipeThrough(new DecompressionStream('gzip'))).arrayBuffer();
      
      const coordsD = new Int16Array(coordsB);
      const n = META.count;
      
      const xCoords16 = new Float32Array(n);
      const yCoords16 = new Float32Array(n);
      
      let x = 0, y = 0;
      for (let i = 0; i < n; i++) {
        x += coordsD[i * 2]; y += coordsD[i * 2 + 1];
        xCoords16[i] = x / 65535;
        yCoords16[i] = y / 65535;
      }
      
      const positions = new Float32Array(n * 2);
      for (let i = 0; i < n; i++) {
        positions[i * 2] = xCoords16[i];
        positions[i * 2 + 1] = yCoords16[i];
      }
      gl.bindBuffer(gl.ARRAY_BUFFER, posBuffer);
      gl.bufferSubData(gl.ARRAY_BUFFER, 0, positions);
      
      xCoords = xCoords16;
      yCoords = yCoords16;
      
      buildSpatialIndex();
      needsRender = true;
      
      hiResLoaded = true;
    } catch(e) {
      console.warn('Hi-res load failed:', e);
    }
    hiResLoading = false;
  }

  function buildLegend() {
    const sorted = Array.from(ontCounts.entries()).sort((a,b) => b[1]-a[1]);
    legend.innerHTML = '';
    for (const [oi, cnt] of sorted) {
      const c = ontColors[oi];
      const d = document.createElement('div');
      d.className = 'umap-leg';
      d.dataset.o = String(oi);
      d.innerHTML = `<div style="width:10px;height:10px;border-radius:50%;flex-shrink:0;background:${rgbToCss(c)}"></div>
        <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1" title="${META.ontologies[oi]}">${META.ontologies[oi]}</span>
        <span style="color:#999;font-size:10px">${(cnt/1000).toFixed(0)}k</span>`;
      d.onmouseenter = () => { 
        legendHovered = true;
        setHighlight(oi);
      };
      d.onclick = () => {
        zoomToOntology(oi);
      };
      d.style.cursor = 'pointer';
      legend.appendChild(d);
    }
    legend.onmouseleave = () => {
      legendHovered = false;
      setHighlight(null);
    };
  }
  
  function setHighlight(oi: number | null) {
    if (highlightedOnt === oi) return;
    highlightedOnt = oi;
    needsRender = true;
    
    legend.querySelectorAll('.umap-leg').forEach(el => {
      const htmlEl = el as HTMLElement;
      if (oi === null) {
        htmlEl.classList.remove('off', 'on');
      } else {
        const match = parseInt(htmlEl.dataset.o || '') === oi;
        htmlEl.classList.toggle('off', !match);
        htmlEl.classList.toggle('on', match);
        if (match) {
          htmlEl.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
        }
      }
    });
    
    gl.useProgram(program);
    gl.uniform1f(uHighlight, oi === null ? -1 : oi);
    if (config.onHover) config.onHover(oi !== null ? { index: oi, name: META.ontologies[oi], count: ontCounts.get(oi)! } : null);
  }
  
  function zoomToOntology(oi: number) {
    const ontName = META.ontologies[oi];
    const bounds = META.bounds && META.bounds[ontName];
    
    if (!bounds) {
      console.warn('No bounds for ontology:', ontName);
      return;
    }
    
    // Calculate the minimum scale needed to center on this position without going off-edge
    // For a center at cx, we need: cx >= 0.5/scale AND cx <= 1 - 0.5/scale
    // This means: scale >= 0.5/cx AND scale >= 0.5/(1-cx)
    const minScaleForCX = Math.max(0.5 / bounds.cx, 0.5 / (1 - bounds.cx));
    const minScaleForCY = Math.max(0.5 / bounds.cy, 0.5 / (1 - bounds.cy));
    const minScaleForCenter = Math.max(minScaleForCX, minScaleForCY);
    
    const newTargetCX = bounds.cx;
    const newTargetCY = bounds.cy;
    // Use whichever scale is larger: the one from bounds, or the minimum needed to center properly
    const newTargetScale = Math.max(bounds.scale, minScaleForCenter);
    
    // Stop any ongoing momentum so it doesn't overwrite our targets
    animatingToOntology = true;
    
    targetCX = newTargetCX;
    targetCY = newTargetCY;
    targetScale = newTargetScale;
    setHighlight(oi);
  }

  function initGL() {
    const vs = createShader(gl, gl.VERTEX_SHADER, VERTEX_SHADER_SOURCE)!;
    const fs = createShader(gl, gl.FRAGMENT_SHADER, FRAGMENT_SHADER_SOURCE)!;
    program = gl.createProgram()!;
    gl.attachShader(program, vs);
    gl.attachShader(program, fs);
    gl.linkProgram(program);
    gl.useProgram(program);

    uTransform = gl.getUniformLocation(program, 'uTransform')!;
    uPointSize = gl.getUniformLocation(program, 'uPointSize')!;
    uHighlight = gl.getUniformLocation(program, 'uHighlight')!;
    gl.uniform1f(uHighlight, -1);

    const positions = new Float32Array(META.count * 2);
    for (let i = 0; i < META.count; i++) {
      positions[i * 2] = xCoords[i];
      positions[i * 2 + 1] = yCoords[i];
    }
    posBuffer = gl.createBuffer()!;
    gl.bindBuffer(gl.ARRAY_BUFFER, posBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, positions, gl.DYNAMIC_DRAW);
    const aPos = gl.getAttribLocation(program, 'aPos');
    gl.enableVertexAttribArray(aPos);
    gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 0, 0);

    const colors = new Float32Array(META.count * 3);
    for (let i = 0; i < META.count; i++) {
      const c = ontColors[ontologies[i]];
      colors[i * 3] = c[0] / 255;
      colors[i * 3 + 1] = c[1] / 255;
      colors[i * 3 + 2] = c[2] / 255;
    }
    const colorBuffer = gl.createBuffer()!;
    gl.bindBuffer(gl.ARRAY_BUFFER, colorBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, colors, gl.STATIC_DRAW);
    const aColor = gl.getAttribLocation(program, 'aColor');
    gl.enableVertexAttribArray(aColor);
    gl.vertexAttribPointer(aColor, 3, gl.FLOAT, false, 0, 0);

    const ontBuffer = gl.createBuffer()!;
    gl.bindBuffer(gl.ARRAY_BUFFER, ontBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(ontologies), gl.STATIC_DRAW);
    const aOnt = gl.getAttribLocation(program, 'aOnt');
    gl.enableVertexAttribArray(aOnt);
    gl.vertexAttribPointer(aOnt, 1, gl.FLOAT, false, 0, 0);

    gl.enable(gl.BLEND);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    gl.clearColor(1.0, 1.0, 1.0, 1.0);
    
    const strides = [32, 16, 8, 4, 2, 1];
    for (const stride of strides) {
      const count = Math.ceil(META.count / stride);
      const indices = new Uint32Array(count);
      for (let i = 0; i < count; i++) indices[i] = i * stride;
      const buf = gl.createBuffer()!;
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, buf);
      gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indices, gl.STATIC_DRAW);
      lodBuffers.push(buf);
      lodCounts.push(count);
    }
  }

  function initControls() {
    resize();
    window.addEventListener('resize', resize);
    
    let velocityX = 0, velocityY = 0, velocityZoom = 0;
    let zoomPivotMX = 0.5, zoomPivotMY = 0.5;
    let momentumFrame: number | null = null;
    
    function applyMomentum() {
      const friction = 0.92;
      velocityX *= friction;
      velocityY *= friction;
      velocityZoom *= friction;
      
      // Don't apply momentum if we're animating to an ontology
      if (animatingToOntology) {
        velocityX = 0;
        velocityY = 0;
        velocityZoom = 0;
        momentumFrame = null;
        return;
      }
      
      if (Math.abs(velocityX) > 0.00001 || Math.abs(velocityY) > 0.00001) {
        centerX += velocityX;
        centerY += velocityY;
        clampCenter();
        targetCX = centerX;
        targetCY = centerY;
        needsRender = true;
      }
      
      if (Math.abs(velocityZoom) > 0.0001) {
        const viewW = 1 / scale, viewH = 1 / scale;
        const pivotDataX = centerX - viewW/2 + zoomPivotMX * viewW;
        const pivotDataY = centerY - viewH/2 + zoomPivotMY * viewH;
        
        const newScale = Math.max(1, Math.min(500, scale * (1 + velocityZoom)));
        const newViewW = 1 / newScale, newViewH = 1 / newScale;
        centerX = pivotDataX - (zoomPivotMX - 0.5) * newViewW;
        centerY = pivotDataY - (zoomPivotMY - 0.5) * newViewH;
        scale = newScale;
        clampCenter();
        targetCX = centerX;
        targetCY = centerY;
        targetScale = scale;
        needsRender = true;
      }
      
      if (Math.abs(velocityX) > 0.00001 || Math.abs(velocityY) > 0.00001 || Math.abs(velocityZoom) > 0.0001) {
        momentumFrame = requestAnimationFrame(applyMomentum);
      } else {
        momentumFrame = null;
      }
    }
    
    function startMomentum() {
      if (!momentumFrame) {
        momentumFrame = requestAnimationFrame(applyMomentum);
      }
    }
    
    canvas.addEventListener('wheel', e => {
      e.preventDefault();
      animatingToOntology = false;  // User is manually controlling, cancel ontology animation lock
      
      const rect = canvas.getBoundingClientRect();
      const mx = (e.clientX - rect.left) / rect.width;
      const my = 1 - (e.clientY - rect.top) / rect.height;
      
      let deltaX = e.deltaX, deltaY = e.deltaY;
      if (e.deltaMode === 1) {
        deltaX *= 20;
        deltaY *= 20;
      }
      
      const isPinch = e.ctrlKey;
      const isMouseWheel = !isPinch && deltaX === 0 && Math.abs(deltaY) >= 50;
      
      if (isPinch || isMouseWheel) {
        const viewW = 1 / scale, viewH = 1 / scale;
        const dataX = centerX - viewW/2 + mx * viewW;
        const dataY = centerY - viewH/2 + my * viewH;
        
        const zoomSpeed = isPinch ? 0.01 : 0.003;
        const factor = Math.pow(2, -deltaY * zoomSpeed);
        const newScale = Math.max(1, Math.min(500, scale * factor));
        
        if (newScale <= 1) {
          centerX = 0.5;
          centerY = 0.5;
          targetCX = 0.5;
          targetCY = 0.5;
        } else {
          const newViewW = 1 / newScale, newViewH = 1 / newScale;
          centerX = dataX - (mx - 0.5) * newViewW;
          centerY = dataY - (my - 0.5) * newViewH;
          targetCX = centerX;
          targetCY = centerY;
        }
        scale = newScale;
        targetScale = scale;
        clampCenter();
        needsRender = true;
        
        if (isPinch) {
          velocityZoom = (factor - 1) * 0.4;
          zoomPivotMX = mx;
          zoomPivotMY = my;
          startMomentum();
        }
      } else {
        const panSpeed = 1.2 / scale;
        const dx = deltaX * panSpeed / rect.width;
        const dy = -deltaY * panSpeed / rect.height;
        
        centerX += dx;
        centerY += dy;
        clampCenter();
        targetCX = centerX;
        targetCY = centerY;
        
        velocityX = dx * 0.4;
        velocityY = dy * 0.4;
        needsRender = true;
        startMomentum();
      }
    }, { passive: false });

    let dragStartX: number, dragStartY: number;
    canvas.addEventListener('mousedown', e => {
      isDragging = true;
      animatingToOntology = false;  // User is manually controlling, cancel ontology animation lock
      lastMX = e.clientX;
      lastMY = e.clientY;
      dragStartX = e.clientX;
      dragStartY = e.clientY;
      canvas.style.cursor = 'grabbing';
    });

    canvas.addEventListener('mousemove', e => {
      if (isDragging) {
        const rect = canvas.getBoundingClientRect();
        const dx = (e.clientX - lastMX) / rect.width / scale;
        const dy = (e.clientY - lastMY) / rect.height / scale;
        centerX -= dx;
        centerY += dy;
        clampCenter();
        targetCX = centerX;
        targetCY = centerY;
        needsRender = true;
        lastMX = e.clientX;
        lastMY = e.clientY;
        hoveredPoint = -1;
        return;
      }
      
      const animating = Math.abs(targetScale - scale) > 0.1 || 
                        Math.abs(targetCX - centerX) > 0.001 || 
                        Math.abs(targetCY - centerY) > 0.001;
      if (animating) {
        hoveredPoint = -1;
        return;
      }
      
      if (scale > 30 && labels) {
        hoveredPoint = findNearestPoint(e);
        canvas.style.cursor = hoveredPoint >= 0 ? 'pointer' : 'grab';
      } else {
        hoveredPoint = -1;
      }
      
      if (scale > 30) {
        if (!labels && !labelsLoading) loadLabels();
        if (!iris && !irisLoading) loadIris();
      }
      
      highlightOntUnderCursor(e);
    });

    canvas.addEventListener('mouseup', (e) => { 
      const wasDrag = Math.abs(e.clientX - dragStartX) > 5 || Math.abs(e.clientY - dragStartY) > 5;
      isDragging = false; 
      canvas.style.cursor = 'grab';
      
      if (!wasDrag) {
        if (scale > 30 && iris) {
          const nearest = findNearestPoint(e);
          if (nearest >= 0 && iris[nearest]) {
            const ontId = META.ontologies[ontologies[nearest]];
            const iri = iris[nearest];
            const url = config.olsUrl
              .replace('{ontology}', encodeURIComponent(ontId))
              .replace('{iri}', encodeURIComponent(encodeURIComponent(iri)));
            window.open(url, '_blank');
            return;
          }
        }
        const clickedOnt = findOntologyAtCursor(e);
        if (clickedOnt !== null) {
          zoomToOntology(clickedOnt);
        }
      }
    });
    canvas.addEventListener('mouseleave', () => { 
      isDragging = false; 
      canvas.style.cursor = 'grab'; 
      hoveredPoint = -1;
      if (!legendHovered) setHighlight(null);
    });
    canvas.style.cursor = 'grab';
  }
  
  function highlightOntUnderCursor(e: MouseEvent) {
    if (legendHovered) return;
    
    const rect = canvas.getBoundingClientRect();
    const mx = (e.clientX - rect.left) / rect.width;
    const my = 1 - (e.clientY - rect.top) / rect.height;
    
    const viewW = 1 / scale, viewH = 1 / scale;
    const dataX = centerX - viewW/2 + mx * viewW;
    const dataY = centerY - viewH/2 + my * viewH;
    
    const threshold = 15 / (scale * Math.min(canvas.width, canvas.height));
    const thresholdSq = threshold * threshold;
    
    const cellRadius = Math.ceil(threshold * GRID_SIZE) + 1;
    const centerGX = Math.floor(dataX * GRID_SIZE);
    const centerGY = Math.floor(dataY * GRID_SIZE);
    
    let nearest = -1, nearestDist = thresholdSq;
    
    for (let gy = Math.max(0, centerGY - cellRadius); gy <= Math.min(GRID_SIZE - 1, centerGY + cellRadius); gy++) {
      for (let gx = Math.max(0, centerGX - cellRadius); gx <= Math.min(GRID_SIZE - 1, centerGX + cellRadius); gx++) {
        const cell = spatialGrid![gy * GRID_SIZE + gx];
        for (let j = 0; j < cell.length; j++) {
          const i = cell[j];
          const dx = xCoords[i] - dataX, dy = yCoords[i] - dataY;
          const d = dx*dx + dy*dy;
          if (d < nearestDist) {
            nearestDist = d;
            nearest = i;
          }
        }
      }
    }
    
    setHighlight(nearest >= 0 ? ontologies[nearest] : null);
  }
  
  function findOntologyAtCursor(e: MouseEvent): number | null {
    const rect = canvas.getBoundingClientRect();
    const mx = (e.clientX - rect.left) / rect.width;
    const my = 1 - (e.clientY - rect.top) / rect.height;
    
    const viewW = 1 / scale, viewH = 1 / scale;
    const dataX = centerX - viewW/2 + mx * viewW;
    const dataY = centerY - viewH/2 + my * viewH;
    
    const threshold = 15 / (scale * Math.min(canvas.width, canvas.height));
    const thresholdSq = threshold * threshold;
    
    const cellRadius = Math.ceil(threshold * GRID_SIZE) + 1;
    const centerGX = Math.floor(dataX * GRID_SIZE);
    const centerGY = Math.floor(dataY * GRID_SIZE);
    
    let nearest = -1, nearestDist = thresholdSq;
    
    for (let gy = Math.max(0, centerGY - cellRadius); gy <= Math.min(GRID_SIZE - 1, centerGY + cellRadius); gy++) {
      for (let gx = Math.max(0, centerGX - cellRadius); gx <= Math.min(GRID_SIZE - 1, centerGX + cellRadius); gx++) {
        const cell = spatialGrid![gy * GRID_SIZE + gx];
        for (let j = 0; j < cell.length; j++) {
          const i = cell[j];
          const dx = xCoords[i] - dataX, dy = yCoords[i] - dataY;
          const d = dx*dx + dy*dy;
          if (d < nearestDist) {
            nearestDist = d;
            nearest = i;
          }
        }
      }
    }
    
    return nearest >= 0 ? ontologies[nearest] : null;
  }

  function buildSpatialIndex() {
    spatialGrid = new Array(GRID_SIZE * GRID_SIZE);
    for (let i = 0; i < spatialGrid.length; i++) spatialGrid[i] = [];
    
    for (let i = 0; i < META.count; i++) {
      const gx = Math.min(GRID_SIZE - 1, Math.max(0, Math.floor(xCoords[i] * GRID_SIZE)));
      const gy = Math.min(GRID_SIZE - 1, Math.max(0, Math.floor(yCoords[i] * GRID_SIZE)));
      spatialGrid[gy * GRID_SIZE + gx].push(i);
    }
  }

  function findNearestPoint(e: MouseEvent): number {
    const rect = canvas.getBoundingClientRect();
    const mx = (e.clientX - rect.left) / rect.width;
    const my = 1 - (e.clientY - rect.top) / rect.height;
    
    const viewW = 1 / scale, viewH = 1 / scale;
    const dataX = centerX - viewW/2 + mx * viewW;
    const dataY = centerY - viewH/2 + my * viewH;
    
    const threshold = 8 / (scale * Math.min(canvas.width, canvas.height));
    const thresholdSq = threshold * threshold;
    
    const cellRadius = Math.ceil(threshold * GRID_SIZE) + 1;
    const centerGX = Math.floor(dataX * GRID_SIZE);
    const centerGY = Math.floor(dataY * GRID_SIZE);
    
    let nearest = -1, nearestDist = thresholdSq;
    
    for (let gy = Math.max(0, centerGY - cellRadius); gy <= Math.min(GRID_SIZE - 1, centerGY + cellRadius); gy++) {
      for (let gx = Math.max(0, centerGX - cellRadius); gx <= Math.min(GRID_SIZE - 1, centerGX + cellRadius); gx++) {
        const cell = spatialGrid![gy * GRID_SIZE + gx];
        for (let j = 0; j < cell.length; j++) {
          const i = cell[j];
          if (highlightedOnt !== null && ontologies[i] !== highlightedOnt) continue;
          const dx = xCoords[i] - dataX, dy = yCoords[i] - dataY;
          const d = dx*dx + dy*dy;
          if (d < nearestDist) {
            nearestDist = d;
            nearest = i;
          }
        }
      }
    }
    return nearest;
  }

  function resize() {
    const rect = root.getBoundingClientRect();
    legendWidth = legend.offsetWidth + 20;
    const availableWidth = rect.width - legendWidth;
    canvas.width = availableWidth;
    canvas.height = rect.height;
    labelsCanvas.width = availableWidth;
    labelsCanvas.height = rect.height;
    gl.viewport(0, 0, canvas.width, canvas.height);
    needsRender = true;
  }

  function render() {
    const stateChanged = centerX !== lastCenterX || centerY !== lastCenterY || 
                         scale !== lastScale || highlightedOnt !== lastHighlight;
    if (!stateChanged && !needsRender) {
      renderLabels();
      return;
    }
    
    lastCenterX = centerX;
    lastCenterY = centerY;
    lastScale = scale;
    lastHighlight = highlightedOnt;
    needsRender = false;
    
    gl.clear(gl.COLOR_BUFFER_BIT);
    
    const viewW = 1 / scale, viewH = 1 / scale;
    const x0 = centerX - viewW / 2, y0 = centerY - viewH / 2;
    
    const sx = 2 * scale, sy = 2 * scale;
    const tx = -2 * x0 * scale - 1;
    const ty = -2 * y0 * scale - 1;
    
    gl.uniformMatrix3fv(uTransform, false, [sx, 0, 0, 0, sy, 0, tx, ty, 1]);
    
    const baseSize = Math.min(canvas.width, canvas.height) / 800;
    const pointSize = Math.max(1.0, Math.min(12, baseSize * Math.sqrt(scale)));
    gl.uniform1f(uPointSize, pointSize);
    
    let lodIndex;
    if (scale < 1.5) lodIndex = 0;
    else if (scale < 2.5) lodIndex = 1;
    else if (scale < 4) lodIndex = 2;
    else if (scale < 6) lodIndex = 3;
    else if (scale < 9) lodIndex = 4;
    else lodIndex = 5;
    
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, lodBuffers[lodIndex]);
    gl.drawElements(gl.POINTS, lodCounts[lodIndex], gl.UNSIGNED_INT, 0);
    
    renderLabels();
  }
  
  let lastLabelPoint = -1;
  function renderLabels() {
    if (hoveredPoint < 0 || !labels || !labels[hoveredPoint] || scale < 30) {
      if (lastLabelPoint >= 0) {
        labelsCtx.clearRect(0, 0, labelsCanvas.width, labelsCanvas.height);
        lastLabelPoint = -1;
      }
      return;
    }
    
    if (hoveredPoint === lastLabelPoint) return;
    lastLabelPoint = hoveredPoint;
    
    labelsCtx.clearRect(0, 0, labelsCanvas.width, labelsCanvas.height);
    
    const viewW = 1 / scale, viewH = 1 / scale;
    const x0 = centerX - viewW / 2, y0 = centerY - viewH / 2;
    
    const px = xCoords[hoveredPoint], py = yCoords[hoveredPoint];
    const sx = (px - x0) / viewW * labelsCanvas.width;
    const sy = (1 - (py - y0) / viewH) * labelsCanvas.height;
    
    const label = labels[hoveredPoint];
    const c = ontColors[ontologies[hoveredPoint]];
    const fontSize = 13;
    const padding = 5;
    
    labelsCtx.font = `600 ${fontSize}px system-ui, sans-serif`;
    labelsCtx.textBaseline = 'middle';
    const textWidth = labelsCtx.measureText(label).width;
    
    labelsCtx.fillStyle = 'rgba(255,255,255,0.95)';
    labelsCtx.fillRect(sx + 8 - padding, sy - fontSize/2 - padding, textWidth + padding * 2, fontSize + padding * 2);
    
    labelsCtx.strokeStyle = `rgb(${c[0]},${c[1]},${c[2]})`;
    labelsCtx.lineWidth = 1.5;
    labelsCtx.strokeRect(sx + 8 - padding, sy - fontSize/2 - padding, textWidth + padding * 2, fontSize + padding * 2);
    
    labelsCtx.fillStyle = `rgb(${c[0]},${c[1]},${c[2]})`;
    labelsCtx.fillText(label, sx + 8, sy);
  }

  function animate() {
    const animDist = Math.abs(targetCX - centerX) + Math.abs(targetCY - centerY);
    const animZoom = Math.abs(targetScale - scale) / Math.max(scale, 1);
    const isAnimating = animDist > 0.00001 || animZoom > 0.0001;
    
    if (isAnimating) {
      const ease = 0.18;
      centerX += (targetCX - centerX) * ease;
      centerY += (targetCY - centerY) * ease;
      scale += (targetScale - scale) * ease;
      needsRender = true;
    } else {
      // Animation complete, clear the flag
      animatingToOntology = false;
    }
    
    if (scale > 2 && !hiResLoaded && !hiResLoading) loadHiRes();
    if (scale > 40 && !labels && !labelsLoading) loadLabels();
    
    render();
    requestAnimationFrame(animate);
  }

  const api: UMAPViewerAPI = {
    setZoom(z) { targetScale = Math.max(1, Math.min(500, z)); },
    setCenter(x, y) { targetCX = x; targetCY = y; },
    highlight(ontIndex) { setHighlight(ontIndex); },
    getState() { return { centerX, centerY, scale, highlightedOnt }; },
    destroy() {
      window.removeEventListener('resize', resize);
      container.innerHTML = '';
    }
  };

  loadData();
  return api;
}

function hslToRgb(h: number, s: number, l: number): number[] {
  s /= 100; l /= 100;
  const c = (1 - Math.abs(2*l-1)) * s, x = c * (1 - Math.abs((h/60)%2-1)), m = l - c/2;
  let r: number, g: number, b: number;
  if (h < 60) [r,g,b] = [c,x,0];
  else if (h < 120) [r,g,b] = [x,c,0];
  else if (h < 180) [r,g,b] = [0,c,x];
  else if (h < 240) [r,g,b] = [0,x,c];
  else if (h < 300) [r,g,b] = [x,0,c];
  else [r,g,b] = [c,0,x];
  return [Math.round((r+m)*255), Math.round((g+m)*255), Math.round((b+m)*255)];
}

function generateColors(n: number): number[][] {
  return Array.from({length: n}, (_, i) => {
    const hue = (i * 137.508) % 360;
    return hslToRgb(hue, 80 + (i%3)*5, 45 + (i%4)*5);
  });
}

function rgbToCss(c: number[]): string { 
  return `rgb(${c[0]},${c[1]},${c[2]})`; 
}

function createShader(gl: WebGL2RenderingContext, type: number, source: string): WebGLShader | null {
  const s = gl.createShader(type)!;
  gl.shaderSource(s, source);
  gl.compileShader(s);
  if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
    console.error(gl.getShaderInfoLog(s));
    return null;
  }
  return s;
}