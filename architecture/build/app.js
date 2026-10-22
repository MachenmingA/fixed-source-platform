(function () {
  'use strict';

  const NS = 'http://www.w3.org/2000/svg';
  const FONT = '"Source Han Sans SC","Noto Sans CJK SC","PingFang SC","Microsoft YaHei",system-ui,sans-serif';
  const MONO = '"Cascadia Mono","JetBrains Mono","SFMono-Regular",Consolas,monospace';

  const KIND_LABEL = {
    actor: '业务角色',
    container: '容器',
    module: '模块',
    store: '数据存储',
    external: '外部系统',
    knowledge: '知识源',
    boundary: '系统边界',
    chip: '能力入口',
    note: '运行说明',
    step: '流程步骤',
    domain: '数据域',
    algorithm: '算法口径',
    edge: '关系'
  };
  const CONF_LABEL = { high: '高置信度', medium: '中置信度', low: '低置信度', unknown: '待验证' };
  const STATE_LABEL = { current: '当前状态', target: '目标状态', proposed: '拟建', deprecated: '已弃用' };
  const PALETTE = { teal: '#0f766e', indigo: '#4338ca', amber: '#b45309', rose: '#be123c', slate: '#64748b' };
  const CONF_COLOR = { high: '#0f766e', medium: '#b45309', low: '#be123c', unknown: '#64748b' };
  const KIND_STYLE = {
    actor: { accent: '#b45309', fill: '#ffffff', stroke: '#e5d5c2' },
    container: { accent: '#0f766e', fill: '#ffffff', stroke: '#c9dfd8' },
    module: { accent: '#0e7490', fill: '#ffffff', stroke: '#d6e5e2' },
    chip: { accent: '#0f766e', fill: '#f3f9f6', stroke: '#cfe3db' },
    external: { accent: '#64748b', fill: '#fbfcfc', stroke: '#b9c8c3', dash: '5 4' },
    knowledge: { accent: '#6d28d9', fill: '#fbfaff', stroke: '#d6cff0' },
    store: { accent: '#4338ca', fill: '#fbfcff', stroke: '#ccd5ea' },
    note: { accent: '#4d7c0f', fill: '#f7f9f2', stroke: '#dce5cf' }
  };
  const ICONS = {
    actor: '<circle cx="12" cy="8" r="3.2"/><path d="M5 20c0-4 3.2-6.5 7-6.5s7 2.5 7 6.5"/>',
    container: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18M8 10v9"/>',
    module: '<rect x="4" y="4" width="7" height="7" rx="1.5"/><rect x="13" y="4" width="7" height="7" rx="1.5"/><rect x="4" y="13" width="7" height="7" rx="1.5"/><rect x="13" y="13" width="7" height="7" rx="1.5"/>',
    store: '<ellipse cx="12" cy="6" rx="7" ry="3"/><path d="M5 6v12c0 1.7 3.1 3 7 3s7-1.3 7-3V6"/>',
    external: '<path d="M7 18a4 4 0 0 1-.6-8 5.5 5.5 0 0 1 10.6-1.4A4.5 4.5 0 0 1 18 18Z"/>',
    knowledge: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4M9 12h6M9 16h6"/>',
    note: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/>',
    chip: '<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M9 9h6v6H9z"/>'
  };
  const LANE_CLASS = {
    '自动': 'lane-teal',
    '审核': 'lane-amber',
    '现场': 'lane-rose',
    '业务平台': 'lane-green',
    '数据平台': 'lane-indigo',
    '订阅方': 'lane-slate',
    '开放方': 'lane-blue',
    '分析': 'lane-blue'
  };
  const TABS = [
    { id: 'context', no: '01', label: '系统上下文', level: 'L1' },
    { id: 'containers', no: '02', label: '容器与模块', level: 'L2' },
    { id: 'flows', no: '03', label: '关键闭环', level: 'FLOW' },
    { id: 'data', no: '04', label: '数据资产', level: 'DATA' }
  ];

  const state = { active: 'context', search: '', transforms: {} };
  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function svgEl(tag, attrs, parent) {
    const el = document.createElementNS(NS, tag);
    if (attrs) {
      Object.keys(attrs).forEach((k) => {
        const v = attrs[k];
        if (v !== undefined && v !== null && v !== false) el.setAttribute(k, v);
      });
    }
    if (parent) parent.appendChild(el);
    return el;
  }

  function svgText(parent, x, y, content, attrs) {
    const t = svgEl('text', Object.assign({ x: x, y: y }, attrs || {}), parent);
    t.textContent = content == null ? '' : String(content);
    return t;
  }

  function el(tag, className, parent, html) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (html != null) node.innerHTML = html;
    if (parent) parent.appendChild(node);
    return node;
  }

  function charWidth(ch, size) {
    if (/[\u2e80-\u9fff\uf900-\ufaff\uff00-\uffef]/.test(ch)) return size;
    if (/[A-Z]/.test(ch)) return size * 0.66;
    if (/[a-z0-9]/.test(ch)) return size * 0.56;
    if (ch === ' ') return size * 0.32;
    return size * 0.52;
  }

  function measure(text, size) {
    let w = 0;
    for (const ch of String(text)) w += charWidth(ch, size);
    return w;
  }

  function wrapByWidth(text, maxWidth, size, maxLines) {
    const source = String(text == null ? '' : text);
    const chars = Array.from(source);
    if (!chars.length) return [''];
    if (maxLines === 1) {
      if (measure(source, size) <= maxWidth) return [source];
      let rest = source;
      while (rest && measure(rest + '…', size) > maxWidth) rest = rest.slice(0, -1);
      return [rest + '…'];
    }
    const lines = [];
    let buf = '';
    let width = 0;
    let i = 0;
    while (i < chars.length) {
      const ch = chars[i];
      const cw = charWidth(ch, size);
      if (width + cw > maxWidth && buf.length) {
        lines.push(buf);
        buf = '';
        width = 0;
        if (lines.length === maxLines - 1) {
          const remaining = chars.slice(i).join('');
          let rest = remaining;
          while (rest && measure(rest + '…', size) > maxWidth) rest = rest.slice(0, -1);
          lines.push(rest + (rest.length < remaining.length ? '…' : ''));
          return lines;
        }
        continue;
      }
      buf += ch;
      width += cw;
      i++;
    }
    if (buf) lines.push(buf);
    return lines.slice(0, maxLines);
  }

  function dedupePoints(points) {
    const out = [];
    points.forEach((p) => {
      if (!p) return;
      const last = out[out.length - 1];
      if (!last || Math.abs(last.x - p.x) > 0.6 || Math.abs(last.y - p.y) > 0.6) {
        out.push({ x: Number(p.x), y: Number(p.y) });
      }
    });
    return out;
  }

  function roundedPath(points, radius) {
    if (points.length < 2) return '';
    if (points.length === 2) return `M${points[0].x},${points[0].y} L${points[1].x},${points[1].y}`;
    let d = `M${points[0].x},${points[0].y}`;
    for (let i = 1; i < points.length - 1; i++) {
      const prev = points[i - 1];
      const p = points[i];
      const next = points[i + 1];
      const v1 = { x: p.x - prev.x, y: p.y - prev.y };
      const v2 = { x: next.x - p.x, y: next.y - p.y };
      const l1 = Math.hypot(v1.x, v1.y) || 1;
      const l2 = Math.hypot(v2.x, v2.y) || 1;
      const rr = Math.min(radius, l1 / 2, l2 / 2);
      const a = { x: p.x - (v1.x / l1) * rr, y: p.y - (v1.y / l1) * rr };
      const b = { x: p.x + (v2.x / l2) * rr, y: p.y + (v2.y / l2) * rr };
      d += ` L${a.x},${a.y} Q${p.x},${p.y} ${b.x},${b.y}`;
    }
    const last = points[points.length - 1];
    d += ` L${last.x},${last.y}`;
    return d;
  }

  function port(node, side, at) {
    const f = typeof at === 'number' ? at : 0.5;
    if (side === 'l') return { x: node.x, y: node.y + node.h * f };
    if (side === 'r') return { x: node.x + node.w, y: node.y + node.h * f };
    if (side === 't') return { x: node.x + node.w * f, y: node.y };
    return { x: node.x + node.w * f, y: node.y + node.h };
  }

  function findInView(view, id) {
    return view.boundaries.find((n) => n.id === id) || view.nodes.find((n) => n.id === id);
  }

  function findEntity(id, preferredView) {
    const order = preferredView
      ? [MODEL.views.find((v) => v.id === preferredView)].concat(MODEL.views.filter((v) => v.id !== preferredView))
      : MODEL.views;
    for (const view of order) {
      if (!view) continue;
      const found = findInView(view, id);
      if (found) return { entity: found, view: view };
    }
    return null;
  }

  function viewStats(view) {
    const refs = new Set();
    const add = (obj) => { (obj.evidence || []).forEach((r) => refs.add(r)); };
    add(view);
    view.nodes.forEach(add);
    view.boundaries.forEach(add);
    view.edges.forEach(add);
    return { nodes: view.nodes.length + view.boundaries.length, edges: view.edges.length, evidence: refs.size };
  }

  function computeEvidenceCount() {
    const refs = new Set();
    (function walk(obj) {
      if (!obj || typeof obj !== 'object') return;
      if (Array.isArray(obj)) { obj.forEach(walk); return; }
      if (Array.isArray(obj.evidence)) obj.evidence.forEach((r) => refs.add(r));
      Object.keys(obj).forEach((k) => walk(obj[k]));
    })(MODEL);
    return refs.size;
  }

  /* ------------------------------------------------------------------ */
  /* shell                                                               */
  /* ------------------------------------------------------------------ */

  function init() {
    $('#meta-date').textContent = MODEL.meta.generatedAt;
    $('#evidence-count').textContent = computeEvidenceCount();
    buildTabs();
    buildViews();
    buildFlows();
    buildData();
    renderHelp();
    bindActions();
    switchView('context');
  }

  function buildTabs() {
    const tabs = $('#tabs');
    TABS.forEach((tab) => {
      const button = el('button', 'tab', tabs);
      button.type = 'button';
      button.dataset.view = tab.id;
      button.setAttribute('role', 'tab');
      button.setAttribute('aria-selected', 'false');
      button.innerHTML = `<span class="tab-no">${tab.no}</span><span>${tab.label}</span><span class="tab-level">${tab.level}</span>`;
      button.addEventListener('click', () => switchView(tab.id));
    });
  }

  function buildViews() {
    const workspace = $('#workspace');
    MODEL.views.forEach((view) => {
      const stats = viewStats(view);
      const section = el('section', 'view map-view', workspace);
      section.id = 'view-' + view.id;
      section.innerHTML = `
        <div class="view-head">
          <div class="head-left">
            <div class="kicker"><span class="level-pill">${esc(view.level)}</span> CURRENT STATE · ${esc(view.id === 'context' ? 'SYSTEM CONTEXT' : 'CONTAINER VIEW')}</div>
            <h2>${esc(view.title)}</h2>
            <p>${esc(view.caption)}</p>
          </div>
          <div class="head-stats">
            <div class="stat"><b>${stats.nodes}</b><span>节点</span></div>
            <div class="stat"><b>${stats.edges}</b><span>关系</span></div>
            <div class="stat"><b>${stats.evidence}</b><span>证据</span></div>
          </div>
        </div>
        <div class="stage">
          <svg role="img" aria-label="${esc(view.title)}"></svg>
          <div class="mobile-hint">拖拽平移 · 按钮缩放</div>
          <div class="legend-card">
            <div class="legend-row"><b>置信度</b><span class="cdot high"></span>高<span class="cdot medium"></span>中<span class="cdot low"></span>低</div>
            <div class="legend-row"><b>连线</b><span class="lline"></span>已证实<span class="lline dashed"></span>模拟 / 推断</div>
            <div class="legend-note">点击节点或连线查看职责、协议与证据</div>
          </div>
          <div class="zoom">
            <button type="button" data-zoom="out" aria-label="缩小">−</button>
            <span class="zoom-label">100%</span>
            <button type="button" data-zoom="in" aria-label="放大">+</button>
            <button type="button" class="fit" data-zoom="fit">适应</button>
          </div>
        </div>`;
      buildMap(section, view);
    });
  }

  /* ------------------------------------------------------------------ */
  /* svg map                                                             */
  /* ------------------------------------------------------------------ */

  function buildMap(section, view) {
    const svg = $('svg', section);
    svg.setAttribute('viewBox', `0 0 ${view.width} ${view.height}`);
    const compact = window.matchMedia('(max-width: 900px)').matches;
    svg.setAttribute('preserveAspectRatio', compact ? 'xMidYMid slice' : 'xMidYMid meet');
    svg.setAttribute('style', `font-family:${FONT};`);
    window.addEventListener('resize', () => {
      const small = window.matchMedia('(max-width: 900px)').matches;
      svg.setAttribute('preserveAspectRatio', small ? 'xMidYMid slice' : 'xMidYMid meet');
    });

    const defs = svgEl('defs', {}, svg);
    Object.keys(PALETTE).forEach((tone) => {
      const marker = svgEl('marker', {
        id: `arrow-${tone}-${view.id}`,
        viewBox: '0 0 10 10',
        refX: '9',
        refY: '5',
        markerWidth: '7.2',
        markerHeight: '7.2',
        orient: 'auto-start-reverse'
      }, defs);
      svgEl('path', { d: 'M 0 0 L 10 5 L 0 10 z', fill: PALETTE[tone] }, marker);
    });
    const filter = svgEl('filter', {
      id: `soft-${view.id}`,
      x: '-20%', y: '-20%', width: '140%', height: '140%'
    }, defs);
    svgEl('feDropShadow', {
      dx: '0', dy: '2.5', stdDeviation: '3', 'flood-color': '#132420', 'flood-opacity': '0.09'
    }, filter);

    const canvas = svgEl('g', { class: 'canvas' }, svg);
    const gBoundaries = svgEl('g', { class: 'layer-boundaries' }, canvas);
    const gPanels = svgEl('g', { class: 'layer-panels' }, canvas);
    const gEdges = svgEl('g', { class: 'layer-edges' }, canvas);
    const gLabels = svgEl('g', { class: 'layer-edge-labels' }, canvas);
    const gNodes = svgEl('g', { class: 'layer-nodes' }, canvas);

    view.boundaries.forEach((node) => drawBoundary(node, gBoundaries, view));
    (view.panels || []).forEach((panel) => drawPanel(panel, gPanels));
    view.edges.forEach((edge) => drawEdge(edge, gEdges, gLabels, view));
    view.nodes.forEach((node) => drawNode(node, gNodes, view));

    bindStage(section, view);
  }

  function drawBoundary(node, parent, view) {
    const datahub = node.tone === 'datahub';
    const fill = datahub ? 'rgba(180,83,9,0.032)' : 'rgba(15,118,110,0.036)';
    const stroke = datahub ? '#d7b88b' : '#94c4ba';
    const accent = datahub ? '#b45309' : '#0f766e';
    const g = svgEl('g', {
      class: 'boundary',
      'data-id': node.id,
      'data-view': view.id,
      tabindex: '0',
      role: 'button',
      transform: `translate(${node.x} ${node.y})`
    }, parent);
    svgEl('rect', {
      width: node.w, height: node.h, rx: 24,
      fill: fill, stroke: stroke, 'stroke-width': 1.5,
      'stroke-dasharray': datahub ? '7 5' : 'none'
    }, g);
    svgEl('rect', { x: 0, y: 22, width: 5, height: 34, rx: 2.5, fill: accent }, g);
    svgText(g, 24, 38, node.title, { 'font-size': 16, 'font-weight': 700, fill: '#142e27', 'font-family': FONT });
    svgText(g, 24, 60, node.subtitle, { 'font-size': 10.4, fill: '#688078', 'font-family': FONT });
    if (node.badge) {
      svgText(g, node.w - 22, 36, node.badge, {
        'text-anchor': 'end', 'font-size': 8.4, 'letter-spacing': '0.05em',
        fill: datahub ? '#9a5b12' : '#5f8479', 'font-family': MONO
      });
    }
    (node.footer || []).forEach((line, i) => {
      svgText(g, 24, node.h - 30 + i * 16, line, {
        'font-size': 9.4, fill: '#7c9088', 'font-family': FONT
      });
    });
    g.addEventListener('click', (event) => {
      event.stopPropagation();
      openNode(node, view);
    });
    g.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        openNode(node, view);
      }
    });
  }

  function drawPanel(panel, parent) {
    const g = svgEl('g', {
      class: 'panel',
      'pointer-events': 'none',
      transform: `translate(${panel.x} ${panel.y})`
    }, parent);
    svgEl('rect', {
      width: panel.w, height: panel.h, rx: 18,
      fill: 'rgba(15,118,110,0.032)', stroke: '#d3e6df', 'stroke-width': 1
    }, g);
    svgText(g, 22, 32, panel.title, { 'font-size': 12, 'font-weight': 700, fill: '#2e574b', 'font-family': FONT, 'letter-spacing': '0.04em' });
    (panel.footer || []).forEach((line, i) => {
      svgText(g, 22, panel.h - 30 + i * 16, line, { 'font-size': 9.2, fill: '#7d9189', 'font-family': FONT });
    });
  }

  function edgeTone(edge) {
    if (edge.tone) return edge.tone;
    const p = String(edge.protocol || '');
    if (/JDBC|SQL/i.test(p)) return 'indigo';
    if (/file|文件/i.test(p)) return 'slate';
    if (/in-process/i.test(p)) return 'amber';
    if (/HMAC|mock-consumer|open-data/i.test(p)) return 'teal';
    return 'teal';
  }

  function labelAnchor(points) {
    let best = null;
    for (let i = 0; i < points.length - 1; i++) {
      const a = points[i];
      const b = points[i + 1];
      const len = Math.hypot(b.x - a.x, b.y - a.y);
      if (!best || len > best.len) best = { a: a, b: b, len: len };
    }
    if (!best) return { x: points[0].x, y: points[0].y, horizontal: true };
    return {
      x: (best.a.x + best.b.x) / 2,
      y: (best.a.y + best.b.y) / 2,
      horizontal: Math.abs(best.b.x - best.a.x) >= Math.abs(best.b.y - best.a.y)
    };
  }

  function drawEdge(edge, edgeLayer, labelLayer, view) {
    const from = findInView(view, edge.from);
    const to = findInView(view, edge.to);
    if (!from || !to) return null;
    const start = port(from, (edge.fromPort && edge.fromPort.side) || 'r', edge.fromPort ? edge.fromPort.at : 0.5);
    const end = port(to, (edge.toPort && edge.toPort.side) || 'l', edge.toPort ? edge.toPort.at : 0.5);
    const via = (edge.via || []).map((point) => Array.isArray(point) ? { x: point[0], y: point[1] } : point);
    const points = dedupePoints([start].concat(via).concat([end]));
    const d = roundedPath(points, 10);
    const tone = edgeTone(edge);
    const g = svgEl('g', { class: `edge edge-${tone}`, 'data-id': edge.id, 'data-view': view.id }, edgeLayer);
    const lineAttrs = {
      d: d,
      fill: 'none',
      stroke: PALETTE[tone],
      'stroke-width': 1.7,
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
      'marker-end': `url(#arrow-${tone}-${view.id})`
    };
    if (edge.both) lineAttrs['marker-start'] = `url(#arrow-${tone}-${view.id})`;
    if (edge.dashed) lineAttrs['stroke-dasharray'] = '6 5';
    svgEl('path', lineAttrs, g);
    svgEl('path', {
      d: d,
      fill: 'none',
      stroke: 'transparent',
      'stroke-width': 18,
      'stroke-linecap': 'round',
      class: 'edge-hit'
    }, g);

    const anchor = labelAnchor(points);
    let lx = anchor.x;
    let ly = anchor.y;
    if (anchor.horizontal) ly -= 13;
    else lx += 12;
    if (edge.labelDx) lx += edge.labelDx;
    if (edge.labelDy) ly += edge.labelDy;

    const lines = (edge.labelLines && edge.labelLines.length ? edge.labelLines : [edge.label || '']).filter(Boolean);
    if (lines.length) {
      const size = 9.2;
      const widths = lines.map((line) => measure(line, size));
      const boxW = Math.max.apply(null, widths) + 16;
      const boxH = lines.length * 12 + 9;
      lx = Math.max(boxW / 2 + 5, Math.min(view.width - boxW / 2 - 5, lx));
      ly = Math.max(boxH / 2 + 5, Math.min(view.height - boxH / 2 - 5, ly));
      const label = svgEl('g', {
        class: 'edge-label',
        transform: `translate(${lx} ${ly})`,
        'data-id': edge.id,
        'data-view': view.id
      }, labelLayer);
      svgEl('rect', {
        x: -boxW / 2, y: -boxH / 2, width: boxW, height: boxH, rx: 6,
        fill: '#ffffff', stroke: '#d8e4df', 'stroke-width': 1, opacity: 0.97
      }, label);
      lines.forEach((line, i) => {
        svgText(label, 0, -boxH / 2 + 13 + i * 12, line, {
          'text-anchor': 'middle',
          'font-size': size,
          'font-weight': 600,
          fill: '#3d5a51',
          'font-family': FONT
        });
      });
      const open = (event) => {
        event.stopPropagation();
        openEdge(edge, view);
      };
      label.addEventListener('click', open);
      g.addEventListener('click', open);
      label.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openEdge(edge, view); }
      });
    } else {
      g.addEventListener('click', (event) => {
        event.stopPropagation();
        openEdge(edge, view);
      });
    }
    return g;
  }

  function drawIcon(parent, kind, x, y, size, accent) {
    const tile = svgEl('g', { transform: `translate(${x} ${y})` }, parent);
    svgEl('rect', { width: size, height: size, rx: size * 0.28, fill: accent, 'fill-opacity': 0.10 }, tile);
    const icon = svgEl('g', {
      transform: `translate(${size * 0.22} ${size * 0.22}) scale(${(size * 0.56) / 24})`,
      fill: 'none',
      stroke: accent,
      'stroke-width': 1.8,
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round'
    }, tile);
    icon.innerHTML = ICONS[kind] || ICONS.module;
  }

  function drawLinkGlyph(parent, x, y, color) {
    const g = svgEl('g', { transform: `translate(${x} ${y}) scale(0.72)`, opacity: 0.55 }, parent);
    svgEl('path', {
      d: 'M3 11 L11 3 M5 3 H11 V9',
      fill: 'none', stroke: color, 'stroke-width': 1.6,
      'stroke-linecap': 'round', 'stroke-linejoin': 'round'
    }, g);
  }

  function drawNode(node, parent, view) {
    const style = KIND_STYLE[node.kind] || KIND_STYLE.module;
    const g = svgEl('g', {
      class: `node kind-${node.kind}`,
      'data-id': node.id,
      'data-view': view.id,
      tabindex: '0',
      role: 'button',
      transform: `translate(${node.x} ${node.y})`
    }, parent);

    const isChip = node.kind === 'chip';
    const isModule = node.kind === 'module';
    const isStore = node.kind === 'store' && Array.isArray(node.lines);
    const rx = isChip ? 12 : isModule ? 12 : 14;
    const card = svgEl('rect', {
      class: 'card', width: node.w, height: node.h, rx: rx,
      fill: style.fill, stroke: style.stroke, 'stroke-width': 1.2
    }, g);
    if (style.dash) card.setAttribute('stroke-dasharray', style.dash);
    if (!isChip && !isModule) card.setAttribute('filter', `url(#soft-${view.id})`);

    if (isModule) {
      svgEl('rect', { x: 0, y: 14, width: 4, height: node.h - 28, rx: 2, fill: style.accent }, g);
    }
    if (isChip) {
      svgEl('rect', { x: 0, y: 0, width: node.w, height: 3, rx: 1.5, fill: style.accent, opacity: 0.55 }, g);
    }

    const confColor = CONF_COLOR[node.confidence] || CONF_COLOR.medium;

    if (isChip) {
      const titleSize = 12.6;
      const subSize = 9.3;
      svgText(g, node.w / 2, node.h > 56 ? 25 : 21, node.title, {
        'text-anchor': 'middle', 'font-size': titleSize, 'font-weight': 650,
        fill: '#16382e', 'font-family': FONT
      });
      const subs = wrapByWidth(node.subtitle || '', node.w - 22, subSize, 1);
      svgText(g, node.w / 2, node.h > 56 ? 43 : 38, subs[0] || '', {
        'text-anchor': 'middle', 'font-size': subSize, fill: '#70867d', 'font-family': FONT
      });
      if (node.link) drawLinkGlyph(g, node.w - 17, 7, style.accent);
      svgEl('circle', { cx: node.w - 10, cy: node.h - 9, r: 2.7, fill: confColor, opacity: 0.9 }, g);
    } else if (isModule) {
      const titleLines = wrapByWidth(node.title, node.w - 30, 12.6, 1);
      svgText(g, 16, 24, titleLines[0], {
        'font-size': 12.6, 'font-weight': 650, fill: '#15352c', 'font-family': FONT
      });
      const subs = wrapByWidth(node.subtitle || '', node.w - 30, 10, 2);
      subs.forEach((line, i) => {
        svgText(g, 16, 42 + i * 13, line, { 'font-size': 10, fill: '#6b8179', 'font-family': FONT });
      });
      svgEl('circle', { cx: node.w - 10, cy: node.h - 9, r: 2.7, fill: confColor, opacity: 0.9 }, g);
    } else if (isStore) {
      drawIcon(g, 'store', 15, 14, 34, style.accent);
      svgText(g, 60, 34, node.title, { 'font-size': 15.5, 'font-weight': 700, fill: '#172f28', 'font-family': FONT });
      const subs = wrapByWidth(node.subtitle || '', node.w - 78, 9.8, 2);
      subs.forEach((line, i) => {
        svgText(g, 60, 53 + i * 12, line, { 'font-size': 9.8, fill: '#6f827c', 'font-family': MONO });
      });
      if (node.badge) {
        svgText(g, node.w - 14, 18, node.badge, {
          'text-anchor': 'end', 'font-size': 8.3, fill: '#8a9b95', 'font-family': MONO, 'letter-spacing': '0.04em'
        });
      }
      svgEl('line', { x1: 18, y1: 88, x2: node.w - 18, y2: 88, stroke: '#e1e8f3', 'stroke-width': 1 }, g);
      let y = 108;
      node.lines.forEach((row) => {
        svgText(g, 18, y, row[0], { 'font-size': 9.8, 'font-weight': 700, fill: style.accent, 'font-family': FONT });
        const rows = wrapByWidth(row[1], node.w - 36, 9.1, 2);
        rows.forEach((line, i) => {
          svgText(g, 18, y + 14 + i * 11.5, line, { 'font-size': 9.1, fill: '#647a72', 'font-family': FONT });
        });
        y += 16 + rows.length * 11.5 + 9;
      });
      svgEl('circle', { cx: node.w - 12, cy: node.h - 11, r: 3, fill: confColor, opacity: 0.9 }, g);
    } else {
      const iconSize = node.kind === 'note' ? 26 : 32;
      const iconX = 14;
      const iconY = node.kind === 'note' ? 19 : node.kind === 'container' ? 16 : 17;
      drawIcon(g, node.kind === 'boundary' ? 'module' : node.kind, iconX, iconY, iconSize, style.accent);
      const tx = iconX + iconSize + 12;
      const titleY = node.kind === 'note' ? 27 : node.kind === 'container' ? 34 : 31;
      const titleSize = node.kind === 'note' ? 12.6 : node.kind === 'container' || node.kind === 'store' ? 16 : 14.6;
      const badgeSpace = node.badge ? 88 : 14;
      const titleLines = wrapByWidth(node.title, node.w - tx - badgeSpace, titleSize, 1);
      svgText(g, tx, titleY, titleLines[0], {
        'font-size': titleSize, 'font-weight': 650, fill: '#172f28', 'font-family': FONT
      });
      if (node.badge) {
        svgText(g, node.w - 14, 18, node.badge, {
          'text-anchor': 'end', 'font-size': 8.3, fill: '#8a9b95', 'font-family': MONO, 'letter-spacing': '0.04em'
        });
      }
      const subSize = node.kind === 'note' ? 9.7 : 10.7;
      const subTop = node.kind === 'note' ? 45 : node.kind === 'container' ? 56 : 52;
      const subLines = node.kind === 'note' ? 1 : 2;
      const subMax = node.w - tx - 32;
      const subs = wrapByWidth(node.subtitle || '', subMax, subSize, subLines);
      subs.forEach((line, i) => {
        svgText(g, tx, subTop + i * 14, line, {
          'font-size': subSize, fill: '#657b73', 'font-family': FONT
        });
      });
      if (node.kind === 'container' && Array.isArray(node.tech)) {
        const tech = node.tech.join(' · ');
        let text = tech;
        while (text && measure(text, 9.1) > node.w - 30) text = text.slice(0, -1);
        if (text !== tech) text = text.replace(/\s+$/, '') + '…';
        svgText(g, 15, node.h - 13, text, { 'font-size': 9.1, fill: '#8a9b95', 'font-family': MONO });
      }
      if (Array.isArray(node.footer) && node.footer.length) {
        node.footer.forEach((line, i) => {
          let text = line;
          while (text && measure(text, 9) > node.w - tx - 28) text = text.slice(0, -1);
          if (text !== line) text = text.replace(/\s+$/, '') + '…';
          const y = node.h - 14 - (node.footer.length - 1 - i) * 12;
          svgText(g, tx, y, text, { 'font-size': 9, fill: '#8a9b95', 'font-family': FONT });
        });
      }
      if (node.kind === 'note' && node.link) drawLinkGlyph(g, node.w - 18, 8, style.accent);
      svgEl('circle', { cx: 14, cy: node.h - 10, r: 2.9, fill: confColor, opacity: 0.9 }, g);
    }

    g.addEventListener('click', (event) => {
      event.stopPropagation();
      openNode(node, view);
    });
    g.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        openNode(node, view);
      }
    });
    g.addEventListener('mouseenter', () => focusNode(node.id, view));
    g.addEventListener('mouseleave', () => clearFocus(view));
    g.addEventListener('focus', () => focusNode(node.id, view));
    g.addEventListener('blur', () => clearFocus(view));
  }

  function focusNode(id, view) {
    const svg = document.querySelector(`#view-${view.id} svg`);
    if (!svg) return;
    const ids = new Set([id]);
    view.edges.forEach((edge) => {
      if (edge.from === id) ids.add(edge.to);
      if (edge.to === id) ids.add(edge.from);
    });
    $$('.node', svg).forEach((node) => {
      node.classList.toggle('dim', !ids.has(node.dataset.id));
      node.classList.toggle('hl', node.dataset.id === id);
    });
    $$('.edge', svg).forEach((edgeGroup) => {
      const edge = view.edges.find((item) => item.id === edgeGroup.dataset.id);
      const connected = edge && (edge.from === id || edge.to === id);
      edgeGroup.classList.toggle('dim', !connected);
      edgeGroup.classList.toggle('hl', !!connected);
    });
  }

  function clearFocus(view) {
    const svg = document.querySelector(`#view-${view.id} svg`);
    if (!svg) return;
    $$('.node,.edge', svg).forEach((node) => node.classList.remove('dim', 'hl'));
  }

  function bindStage(section, view) {
    const stage = $('.stage', section);
    const svg = $('svg', section);
    const canvas = $('.canvas', svg);
    const zoomLabel = $('.zoom-label', stage);
    const t = state.transforms[view.id] = { k: 1, x: 0, y: 0 };
    let dragging = false;
    let dragStarted = false;
    let last = null;
    let moved = 0;

    function apply() {
      canvas.setAttribute('transform', `translate(${t.x} ${t.y}) scale(${t.k})`);
      if (zoomLabel) zoomLabel.textContent = Math.round(t.k * 100) + '%';
    }

    function toSvgPoint(clientX, clientY) {
      const pt = svg.createSVGPoint();
      pt.x = clientX;
      pt.y = clientY;
      const matrix = svg.getScreenCTM();
      return matrix ? pt.matrixTransform(matrix.inverse()) : { x: clientX, y: clientY };
    }

    function zoomAt(point, factor) {
      const next = Math.max(0.35, Math.min(3, t.k * factor));
      t.x = point.x - (point.x - t.x) * (next / t.k);
      t.y = point.y - (point.y - t.y) * (next / t.k);
      t.k = next;
      apply();
    }

    stage.addEventListener('wheel', (event) => {
      event.preventDefault();
      const point = toSvgPoint(event.clientX, event.clientY);
      zoomAt(point, event.deltaY < 0 ? 1.12 : 1 / 1.12);
    }, { passive: false });

    stage.addEventListener('pointerdown', (event) => {
      if (event.button !== 0) return;
      if (event.target.closest('.node,.edge,[data-zoom],.legend-card')) return;
      dragging = true;
      dragStarted = false;
      moved = 0;
      last = toSvgPoint(event.clientX, event.clientY);
    });

    stage.addEventListener('pointermove', (event) => {
      if (!dragging) return;
      const point = toSvgPoint(event.clientX, event.clientY);
      const dx = point.x - last.x;
      const dy = point.y - last.y;
      if (!dragStarted) {
        if (Math.abs(dx) + Math.abs(dy) < 3) return;
        dragStarted = true;
        stage.setPointerCapture(event.pointerId);
        stage.classList.add('dragging');
      }
      t.x += dx;
      t.y += dy;
      moved += Math.abs(dx) + Math.abs(dy);
      last = point;
      apply();
    });

    function endDrag(event) {
      if (!dragging) return;
      dragging = false;
      dragStarted = false;
      stage.classList.remove('dragging');
      try { stage.releasePointerCapture(event.pointerId); } catch (e) { /* noop */ }
    }
    stage.addEventListener('pointerup', endDrag);
    stage.addEventListener('pointercancel', endDrag);

    stage.addEventListener('click', (event) => {
      if (moved > 5) {
        event.stopPropagation();
        event.preventDefault();
        moved = 0;
        return;
      }
      if (event.target.closest('.node,.edge,.edge-label,.boundary,[data-zoom],.legend-card')) return;
      closeDrawer();
    }, true);

    $$('[data-zoom]', stage).forEach((button) => {
      button.addEventListener('click', (event) => {
        event.stopPropagation();
        const action = button.dataset.zoom;
        if (action === 'fit') {
          t.k = 1; t.x = 0; t.y = 0; apply();
          return;
        }
        const rect = stage.getBoundingClientRect();
        const center = toSvgPoint(rect.left + rect.width / 2, rect.top + rect.height / 2);
        zoomAt(center, action === 'in' ? 1.18 : 1 / 1.18);
      });
    });

    if (window.matchMedia('(max-width: 900px)').matches) {
      const focusX = view.id === 'context' ? 720 : 870;
      t.k = 1;
      t.x = view.width / 2 - focusX;
      t.y = 0;
    }

    apply();
  }

  /* ------------------------------------------------------------------ */
  /* flows                                                               */
  /* ------------------------------------------------------------------ */

  function buildFlows() {
    const workspace = $('#workspace');
    const section = el('section', 'view scroll-view', workspace);
    section.id = 'view-flows';
    const totalSteps = MODEL.flowView.flows.reduce((sum, flow) => sum + flow.steps.length, 0);
    section.innerHTML = `
      <div class="view-head">
        <div class="head-left">
          <div class="kicker"><span class="level-pill">FLOW</span> END-TO-END · CURRENT STATE</div>
          <h2>${esc(MODEL.flowView.title)}</h2>
          <p>${esc(MODEL.flowView.caption)}</p>
        </div>
        <div class="head-stats">
          <div class="stat"><b>${MODEL.flowView.flows.length}</b><span>条链路</span></div>
          <div class="stat"><b>${totalSteps}</b><span>个步骤</span></div>
          <div class="stat"><b>${MODEL.flowView.tracks.length}</b><span>条状态机</span></div>
        </div>
      </div>
      <div class="scroll-body" id="flow-body"></div>`;
    const body = $('#flow-body', section);

    MODEL.flowView.flows.forEach((flow) => {
      const cols = flow.id === 'business' ? 4 : 3;
      const flowSection = el('section', 'flow-section', body);
      flowSection.innerHTML = `
        <div class="flow-head">
          <div>
            <h3>${esc(flow.title)}</h3>
            <p>${esc(flow.subtitle)}</p>
          </div>
          <span class="flow-tag">${flow.steps.length} STEPS</span>
        </div>
        <div class="flow-body"></div>`;
      const flowBody = $('.flow-body', flowSection);
      const rows = chunk(flow.steps, cols);
      rows.forEach((row, rowIndex) => {
        const rowEl = el('div', 'flow-row', flowBody);
        row.forEach((step) => {
          const laneClass = LANE_CLASS[step.lane] || 'lane-slate';
          const card = el('article', 'flow-step', rowEl);
          card.tabIndex = 0;
          card.setAttribute('role', 'button');
          card.dataset.search = [flow.title, step.lane, step.title, step.desc, step.artifact].join(' ');
          card.innerHTML = `
            <div class="step-top">
              <span class="step-no">${String(step.n).padStart(2, '0')}</span>
              <span class="lane ${laneClass}">${esc(step.lane)}</span>
            </div>
            <h4>${esc(step.title)}</h4>
            <p>${esc(step.desc)}</p>
            <code>${esc(step.artifact)}</code>
            <div class="step-foot">
              <span class="conf ${esc(step.confidence)}">${esc(CONF_LABEL[step.confidence] || step.confidence)}</span>
              <span class="more">查看证据</span>
            </div>`;
          card.addEventListener('click', () => openStep(flow, step));
          card.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              openStep(flow, step);
            }
          });
        });
        if (rowIndex < rows.length - 1) {
          const turn = el('div', 'flow-turn', flowBody);
          turn.innerHTML = '<span>↓ 下一行继续</span>';
        }
      });
    });

    const trackPanel = el('section', 'track-panel', body);
    trackPanel.innerHTML = `<h3>状态机与人工边界</h3>` + MODEL.flowView.tracks.map((track) => {
      const states = track.states.map((state, i) => {
        const terminal = i === track.states.length - 1;
        const pill = `<span class="state${terminal ? ' terminal' : ''}">${esc(state)}</span>`;
        return i < track.states.length - 1 ? pill + '<span class="track-arrow">→</span>' : pill;
      }).join('');
      return `<div class="track">
        <div class="track-name">${esc(track.name)}</div>
        <div class="track-states">${states}</div>
        <div class="track-note">${esc(track.note)}</div>
      </div>`;
    }).join('');
  }

  function chunk(items, size) {
    const result = [];
    for (let i = 0; i < items.length; i += size) result.push(items.slice(i, i + size));
    return result;
  }

  /* ------------------------------------------------------------------ */
  /* data                                                                */
  /* ------------------------------------------------------------------ */

  function buildData() {
    const workspace = $('#workspace');
    const section = el('section', 'view scroll-view', workspace);
    section.id = 'view-data';
    const tables = MODEL.dataView.domains.reduce((sum, domain) => sum + domain.tables.length, 0);
    section.innerHTML = `
      <div class="view-head">
        <div class="head-left">
          <div class="kicker"><span class="level-pill">DATA</span> DATA ASSETS · KEY RULES</div>
          <h2>${esc(MODEL.dataView.title)}</h2>
          <p>${esc(MODEL.dataView.caption)}</p>
        </div>
        <div class="head-stats">
          <div class="stat"><b>${tables}</b><span>张表</span></div>
          <div class="stat"><b>${MODEL.dataView.domains.length}</b><span>个域</span></div>
          <div class="stat"><b>${MODEL.dataView.algorithms.length}</b><span>组口径</span></div>
        </div>
      </div>
      <div class="scroll-body">
        <div class="section-title"><h2>数据域</h2><span>25 张 H2 表 · 6 个域 · 演示数据口径随采集与回测变化</span></div>
        <div class="domain-grid" id="domain-grid"></div>
        <div class="section-title"><h2>关键口径</h2><span>规则、优先级、风险分级与 AI 置信度</span></div>
        <div class="algo-grid" id="algo-grid"></div>
      </div>`;
    const domainGrid = $('#domain-grid', section);
    MODEL.dataView.domains.forEach((domain) => {
      const card = el('article', `domain-card tone-${domain.tone}`, domainGrid);
      card.tabIndex = 0;
      card.setAttribute('role', 'button');
      card.dataset.search = [domain.name, domain.key].concat(domain.tables.map((t) => t.name + ' ' + t.note)).join(' ');
      card.innerHTML = `
        <div class="domain-head">
          <div>
            <h3>${esc(domain.name)}</h3>
            <span class="count">${domain.count} TABLES</span>
          </div>
          <span class="tone-dot"></span>
        </div>
        <ul class="table-list">
          ${domain.tables.map((table) => `<li><code>${esc(table.name)}</code><span>${esc(table.note)}</span></li>`).join('')}
        </ul>
        <div class="domain-key">${esc(domain.key)}</div>
        <div class="domain-foot"><span>证据 ${domain.evidence.length} 条</span><span>查看数据域 →</span></div>`;
      card.addEventListener('click', () => openDomain(domain));
      card.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          openDomain(domain);
        }
      });
    });

    const algoGrid = $('#algo-grid', section);
    MODEL.dataView.algorithms.forEach((algo) => {
      const card = el('article', 'algo-card', algoGrid);
      card.tabIndex = 0;
      card.setAttribute('role', 'button');
      card.dataset.search = [algo.title].concat(algo.lines).join(' ');
      card.innerHTML = `
        <h3>${esc(algo.title)}</h3>
        <ul>${algo.lines.map((line) => `<li>${esc(line)}</li>`).join('')}</ul>
        <div class="domain-foot"><span>证据 ${algo.evidence.length} 条</span><span>查看口径 →</span></div>`;
      card.addEventListener('click', () => openAlgorithm(algo));
      card.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          openAlgorithm(algo);
        }
      });
    });
  }

  /* ------------------------------------------------------------------ */
  /* drawer                                                              */
  /* ------------------------------------------------------------------ */

  const drawer = $('#drawer');
  const drawerBody = $('#drawer-body');

  function showDrawer(entity, meta) {
    const data = Object.assign({}, entity);
    const kindLabel = KIND_LABEL[data.kind] || '对象';
    const confidence = data.confidence || 'medium';
    const badges = [];
    badges.push(`<span class="badge conf-${esc(confidence)}">${esc(CONF_LABEL[confidence] || confidence)}</span>`);
    if (data.badge) badges.push(`<span class="badge">${esc(data.badge)}</span>`);
    if (data.lane) badges.push(`<span class="badge">${esc(data.lane)}</span>`);
    if (data.state) badges.push(`<span class="badge">${esc(STATE_LABEL[data.state] || data.state)}</span>`);
    if (data.protocol) badges.push(`<span class="badge">${esc(data.protocol)}</span>`);
    if (data.sync) badges.push(`<span class="badge">${esc(data.sync === 'sync' ? '同步' : data.sync === 'async' ? '异步' : data.sync)}</span>`);

    const items = Array.isArray(data.items) ? data.items : [];
    const tech = Array.isArray(data.tech) ? data.tech : [];
    const evidence = Array.isArray(data.evidence) ? data.evidence : [];

    const sections = [];
    if (items.length) {
      sections.push(`<div class="drawer-section"><h3>职责与内容</h3><ul>${items.map((item) => `<li>${esc(item)}</li>`).join('')}</ul></div>`);
    }
    if (tech.length) {
      sections.push(`<div class="drawer-section"><h3>技术 / 协议</h3><div class="chips">${tech.map((item) => `<span class="chip">${esc(item)}</span>`).join('')}</div></div>`);
    }
    if (evidence.length) {
      sections.push(`<div class="drawer-section"><h3>证据来源 · ${evidence.length}</h3><ul class="evidence">${evidence.map((ref) => {
        const split = splitRef(ref);
        return `<li data-ref="${esc(ref)}" title="点击复制路径"><span class="ev-path">${esc(split.path)}</span><span class="ev-line">${esc(split.suffix)}</span></li>`;
      }).join('')}</ul></div>`);
    }

    const actions = [];
    if (data.link) {
      actions.push(`<button type="button" class="primary" data-goto="${esc(data.link.view)}:${esc(data.link.id)}">在容器视图查看</button>`);
    }
    if (actions.length) sections.push(`<div class="drawer-actions">${actions.join('')}</div>`);

    $('#drawer-kind').textContent = `${kindLabel} · ${meta && meta.scope ? meta.scope : '当前状态'}`;
    $('#drawer-title').textContent = data.title || '';
    drawerBody.innerHTML = `
      <div class="badge-row">${badges.join('')}</div>
      <p class="lede">${esc(data.summary || '')}</p>
      ${sections.join('')}`;

    $$('.evidence li', drawerBody).forEach((item) => {
      item.addEventListener('click', () => {
        copyText(item.dataset.ref);
        toast('已复制：' + item.dataset.ref);
      });
    });
    const goto = $('[data-goto]', drawerBody);
    if (goto) {
      goto.addEventListener('click', () => {
        const parts = goto.dataset.goto.split(':');
        switchView(parts[0]);
        setTimeout(() => {
          const found = findEntity(parts[1], parts[0]);
          if (found) {
            openNode(found.entity, found.view);
            flash(parts[1], parts[0]);
          }
        }, 80);
      });
    }

    drawer.classList.add('open');
    drawer.setAttribute('aria-hidden', 'false');
    drawerBody.scrollTop = 0;
  }

  function openNode(node, view) {
    const entity = Object.assign({}, node, { kind: node.kind === 'boundary' ? 'boundary' : node.kind });
    markSelected('node', entity, view);
    showDrawer(entity, { scope: '当前状态' });
  }

  function openEdge(edge, view) {
    const from = findInView(view, edge.from);
    const to = findInView(view, edge.to);
    const entity = {
      kind: 'edge',
      title: `${from ? from.title : edge.from} → ${to ? to.title : edge.to}`,
      summary: edge.summary,
      protocol: edge.protocol,
      sync: edge.sync,
      label: edge.labelLines ? edge.labelLines.join(' / ') : edge.label,
      evidence: edge.evidence,
      confidence: edge.confidence || 'medium',
      badge: '调用 / 数据流',
      state: 'current'
    };
    showDrawer(entity, { scope: '调用 / 数据流' });
    markSelected('edge', entity, view, edge.id);
  }

  function openStep(flow, step) {
    showDrawer({
      kind: 'step',
      title: step.title,
      summary: step.summary || step.desc,
      items: [step.desc],
      tech: [step.artifact],
      evidence: step.evidence,
      confidence: step.confidence,
      lane: step.lane,
      badge: flow.title,
      state: 'current'
    }, { scope: `第 ${step.n} 步` });
    clearSelection();
  }

  function openDomain(domain) {
    showDrawer({
      kind: 'domain',
      title: domain.name,
      summary: domain.key,
      items: domain.tables.map((table) => `${table.name} — ${table.note}`),
      tech: [`${domain.count} 张表`, 'H2 · JdbcTemplate'],
      evidence: domain.evidence,
      confidence: 'high',
      state: 'current'
    }, { scope: '数据域' });
    clearSelection();
  }

  function openAlgorithm(algo) {
    showDrawer({
      kind: 'algorithm',
      title: algo.title,
      summary: '当前原型中的确定性口径，可在代码中复核。',
      items: algo.lines,
      tech: ['可复现', 'risk-v1 / mock-rag-v1'],
      evidence: algo.evidence,
      confidence: 'high',
      state: 'current'
    }, { scope: '关键口径' });
    clearSelection();
  }

  function markSelected(type, entity, view, edgeId) {
    clearSelection();
    if (!view) return;
    const svg = document.querySelector(`#view-${view.id} svg`);
    if (!svg) return;
    const selector = type === 'edge' ? '.edge' : '.node,.boundary';
    $$(selector, svg).forEach((group) => {
      const match = type === 'edge' ? group.dataset.id === edgeId : group.dataset.id === entity.id;
      if (match) group.classList.add('selected');
    });
  }

  function clearSelection() {
    $$('.node.selected,.edge.selected,.boundary.selected').forEach((node) => node.classList.remove('selected'));
  }

  function flash(id, viewId) {
    const svg = document.querySelector(`#view-${viewId} svg`);
    if (!svg) return;
    const group = $$('.node,.boundary', svg).find((node) => node.dataset.id === id);
    if (!group) return;
    group.classList.add('flash');
    setTimeout(() => group.classList.remove('flash'), 1500);
  }

  function closeDrawer() {
    drawer.classList.remove('open');
    drawer.setAttribute('aria-hidden', 'true');
    clearSelection();
  }

  function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text).catch(() => fallbackCopy(text));
    } else {
      fallbackCopy(text);
    }
  }

  function splitRef(ref) {
    const text = String(ref || '');
    const idx = text.lastIndexOf(':');
    if (idx > 0 && /^\d+(-\d+)?$/.test(text.slice(idx + 1))) {
      return { path: text.slice(0, idx), suffix: text.slice(idx) };
    }
    return { path: text, suffix: '' };
  }

  function fallbackCopy(text) {
    const area = document.createElement('textarea');
    area.value = text;
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    try { document.execCommand('copy'); } catch (e) { /* noop */ }
    document.body.removeChild(area);
  }

  /* ------------------------------------------------------------------ */
  /* help / actions / search                                             */
  /* ------------------------------------------------------------------ */

  function renderHelp() {
    const notes = MODEL.notes.boundaries;
    const body = $('#help-body');
    body.innerHTML = `
      <h3>这张图回答什么</h3>
      <p>回答“当前原型的系统边界、主要容器与模块、关键业务闭环、数据资产与口径”。范围是 fixed-source-platform 当前状态，不包含目标架构或生产化设计。图中同时保留模拟系统与逻辑边界，避免把“同进程 Mock”误读为独立部署单元。</p>
      <h3>读图约定</h3>
      <div class="grid-2">
        <div><p><b>节点形状</b>：角色、容器、模块、数据存储、外部系统与知识源使用不同图标和边框；虚线边框表示模拟或逻辑外部。</p></div>
        <div><p><b>连线</b>：实线为代码或配置直接支持的关系；虚线为模拟、推断或待验证关系。箭头表示调用或数据方向，双向箭头表示请求与回执。</p></div>
        <div><p><b>圆点</b>：节点右下角的小圆点表示证据置信度，绿色为高、橙色为中、红色为低。</p></div>
        <div><p><b>交互</b>：滚轮缩放、拖拽平移、点击节点或连线查看职责、协议与证据；点击证据行可复制路径。</p></div>
      </div>
      <h3>证据与置信度</h3>
      <ul>
        <li><b>高</b>：代码、配置、Schema、Docker 清单或运行入口直接支持。</li>
        <li><b>中</b>：多处间接信号一致，或来自 README 的演示数据口径。</li>
        <li><b>低</b>：由命名、目录结构或业务惯例推断。</li>
        <li><b>待验证</b>：当前环境无法确认，例如 Android 真机相机与 UI 验收。</li>
      </ul>
      <h3>已知边界与验证项</h3>
      ${notes.map((note) => `<div class="note-item">
        <div class="note-head"><b>${esc(note.title)}</b><span class="badge conf-${esc(note.confidence)}">${esc(CONF_LABEL[note.confidence] || note.confidence)}</span></div>
        <p>${esc(note.text)}</p>
      </div>`).join('')}
      <h3>生成信息</h3>
      <p>生成日期 ${esc(MODEL.meta.generatedAt)}；模型源文件 <code>architecture/system-model.json</code>；全图共 ${computeEvidenceCount()} 条独立证据引用。同目录另附 <code>system-model.evidence.md</code>（证据索引）与 <code>system-model.summary.md</code>（架构说明）。源码路径均相对 <code>fixed-source-platform/</code>。</p>`;
  }

  function bindActions() {
    $$('[data-action]').forEach((button) => {
      button.addEventListener('click', () => {
        const action = button.dataset.action;
        if (action === 'help') openHelp();
        if (action === 'print') window.print();
        if (action === 'export') exportSvg();
      });
    });
    $('[data-close-drawer]').addEventListener('click', closeDrawer);
    $('[data-close-help]').addEventListener('click', closeHelp);
    $('#help-modal').addEventListener('click', (event) => {
      if (event.target.id === 'help-modal') closeHelp();
    });
    const search = $('#search');
    search.addEventListener('input', () => {
      state.search = search.value;
      clearFocusAll();
      applySearch();
    });
    search.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        search.value = '';
        state.search = '';
        applySearch();
        search.blur();
      }
    });
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        if ($('#help-modal').classList.contains('open')) closeHelp();
        else closeDrawer();
      }
      if (event.key === '/' && document.activeElement !== search) {
        event.preventDefault();
        search.focus();
      }
    });
  }

  function switchView(id) {
    state.active = id;
    $$('.tab').forEach((tab) => {
      const active = tab.dataset.view === id;
      tab.classList.toggle('active', active);
      tab.setAttribute('aria-selected', active ? 'true' : 'false');
    });
    $$('.view').forEach((view) => view.classList.toggle('active', view.id === 'view-' + id));
    closeDrawer();
    applySearch();
  }

  function applySearch() {
    const query = state.search.trim().toLowerCase();
    const countEl = $('#search-count');
    const activeView = 'view-' + state.active;
    let count = 0;

    $$('.node').forEach((group) => {
      if (group.dataset.view !== state.active) {
        group.classList.remove('search-hit', 'search-miss');
        return;
      }
      const found = findEntity(group.dataset.id, group.dataset.view);
      const entity = found ? found.entity : null;
      const haystack = entity
        ? [entity.title, entity.subtitle, entity.summary, (entity.tech || []).join(' '), (entity.items || []).join(' ')].join(' ').toLowerCase()
        : '';
      const hit = !!query && haystack.includes(query);
      group.classList.toggle('search-hit', hit);
      group.classList.toggle('search-miss', !!query && !hit);
      if (hit) count++;
    });

    $$('.flow-step,.domain-card,.algo-card').forEach((card) => {
      const scope = card.closest('.view');
      if (!scope || scope.id !== activeView) {
        card.classList.remove('search-hit', 'search-miss');
        return;
      }
      const haystack = String(card.dataset.search || '').toLowerCase();
      const hit = !!query && haystack.includes(query);
      card.classList.toggle('search-hit', hit);
      card.classList.toggle('search-miss', !!query && !hit);
      if (hit) count++;
    });

    countEl.textContent = query ? String(count) : '';
  }

  function clearFocusAll() {
    $$('.node,.edge').forEach((node) => node.classList.remove('dim', 'hl'));
  }

  function openHelp() {
    const modal = $('#help-modal');
    modal.classList.add('open');
    modal.setAttribute('aria-hidden', 'false');
  }

  function closeHelp() {
    const modal = $('#help-modal');
    modal.classList.remove('open');
    modal.setAttribute('aria-hidden', 'true');
  }

  function toast(message) {
    const box = $('#toast');
    box.textContent = message;
    box.classList.add('show');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => box.classList.remove('show'), 2200);
  }

  function exportSvg() {
    const view = MODEL.views.find((item) => item.id === state.active);
    if (!view) {
      toast('流程与数据视图请使用“打印”导出 PDF');
      return;
    }
    const source = document.querySelector(`#view-${view.id} svg`);
    const clone = source.cloneNode(true);
    clone.setAttribute('xmlns', NS);
    clone.setAttribute('width', view.width);
    clone.setAttribute('height', view.height);
    clone.setAttribute('viewBox', `0 0 ${view.width} ${view.height}`);
    clone.setAttribute('style', `font-family:${FONT};background:#eef2ee`);
    $$('.dim,.hl,.selected,.search-hit,.search-miss,.flash', clone).forEach((node) => {
      node.classList.remove('dim', 'hl', 'selected', 'search-hit', 'search-miss', 'flash');
    });
    const canvas = $('.canvas', clone);
    if (canvas) canvas.removeAttribute('transform');
    const background = document.createElementNS(NS, 'rect');
    background.setAttribute('width', view.width);
    background.setAttribute('height', view.height);
    background.setAttribute('fill', '#eef2ee');
    if (canvas) clone.insertBefore(background, canvas);
    else clone.appendChild(background);

    const xml = new XMLSerializer().serializeToString(clone);
    const blob = new Blob(['<?xml version="1.0" encoding="UTF-8"?>\n' + xml], { type: 'image/svg+xml;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `net-source-${view.id}-architecture.svg`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 2000);
    toast('已导出当前视图 SVG');
  }

  init();
})();
