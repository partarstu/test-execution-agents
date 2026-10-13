/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
 * Copyright © 2025-2026 Taras Paruta (partarstu@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import { LoaderCircle, MonitorX, RefreshCw } from 'lucide-react';
import { useEffect, useEffectEvent, useRef, useState, type PointerEvent } from 'react';
import {
  AmbientLight,
  Color,
  DirectionalLight,
  type Mesh,
  type Object3D,
  PerspectiveCamera,
  Raycaster,
  Scene,
  Vector2,
  WebGLRenderer,
} from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js';
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js';
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js';
import ThreeForceGraph from 'three-forcegraph';
import { useTheme } from '../colorScheme';
import { useLoaded } from '../useLoaded';
import { fetchOverview } from './api';
import { type Framing, framing, stepTowards } from './brainCamera';
import { KindLegendEntries } from './KindLegend';
import type { GraphNode, GraphSlice } from './model';
import { KIND_LABELS, readTheme } from './theme';
import './BrainView.css';

interface BrainNode extends GraphNode {
  x?: number;
  y?: number;
  z?: number;
}

interface BrainLink {
  source: string | BrainNode;
  target: string | BrainNode;
}

interface Tooltip {
  label: string;
  kind: string;
  left: number;
  top: number;
}

// The layout freezes after this many ticks, so a large graph stops costing CPU once it has settled
const COOLDOWN_TICKS = 200;
const BLOOM = { strength: 1.2, radius: 0.5, threshold: 0.7 };
// Bloom lifts everything brighter than its threshold, so the scene needs a dark backdrop in either theme to keep only the chain glowing
const BACKDROP = '#0e131b';
const ACTIVE_COLOUR = '#bdfff6';
// The settled graph fills the view with this much room around it, and a tiny one is never brought closer than this
const FIT_MARGIN = 1.3;
const MIN_CAMERA_DISTANCE = 40;
// The running chain is framed from at least this far, so the neighbours of a single glowing node stay in view
const MIN_FOCUS_DISTANCE = 150;
const FOCUS_EASING = 0.08;
// A change of the camera's distance across a controls update larger than rounding can only come from the operator zooming
const ZOOM_TOLERANCE = 1e-3;

interface Props {
  activeProcedures: string[];
  onOpenProcedure: (procedureId: string) => void;
  onUnauthorized: () => void;
}

/** The whole graph at a glance, with the running chain glowing. */
export function BrainView({ activeProcedures, onOpenProcedure, onUnauthorized }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const graphRef = useRef<ThreeForceGraph<BrainNode, BrainLink> | null>(null);
  const pickRef = useRef<((event: PointerEvent<HTMLDivElement>) => BrainNode | null) | null>(null);
  const focusRef = useRef<((procedureIds: string[]) => void) | null>(null);
  const { loaded, loading, reload } = useLoaded('overview', fetchOverview, onUnauthorized);
  const theme = useTheme();
  const [webGlMissing] = useState(() => !supportsWebGl());
  const [tooltip, setTooltip] = useState<Tooltip | null>(null);
  const colourOf = useEffectEvent((node: BrainNode) => (activeProcedures.includes(node.id) ? ACTIVE_COLOUR : readTheme().kinds[node.kind]));
  const focusRunning = useEffectEvent(() => focusRef.current?.(activeProcedures));

  // <Activity> runs this cleanup whenever the view is hidden, which releases the WebGL context; the data survives in state
  useEffect(() => {
    const host = container.current;
    if (!host || loaded?.kind !== 'ok' || webGlMissing) {
      return;
    }
    let renderer: WebGLRenderer;
    try {
      renderer = new WebGLRenderer({ antialias: true });
    } catch {
      // A context can still fail after the check, e.g. when the GPU process has crashed; the view then stays empty
      console.warn('Could not create a WebGL renderer for the brain view');
      return;
    }
    const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
    const theme = readTheme();
    const scene = new Scene();
    scene.background = new Color(BACKDROP);
    scene.add(new AmbientLight(0xffffff, 1.6), new DirectionalLight(0xffffff, 1.2));
    const camera = new PerspectiveCamera(50, 1, 1, 20_000);
    camera.position.set(0, 0, 400);
    renderer.setPixelRatio(devicePixelRatio);
    host.appendChild(renderer.domElement);

    const graph = new ThreeForceGraph<BrainNode, BrainLink>()
      .graphData(graphDataOf(loaded.data))
      .numDimensions(3)
      .cooldownTicks(COOLDOWN_TICKS)
      .nodeVal((node) => 1 + node.testCases)
      .nodeColor((node) => colourOf(node))
      .nodeOpacity(0.9)
      .linkColor(() => theme.muted)
      .linkOpacity(0.35);
    scene.add(graph);
    graphRef.current = graph;

    const controls = new OrbitControls(camera, renderer.domElement);
    controls.autoRotate = !reducedMotion;
    controls.autoRotateSpeed = 0.4;
    controls.enableDamping = !reducedMotion;

    let settled = false;
    let zoomedByUser = false;
    let goal: Framing | null = null;
    focusRef.current = (procedureIds) => {
      const box = settled && procedureIds.length > 0 ? graph.getGraphBbox((node) => procedureIds.includes(node.id)) : null;
      if (box) {
        goal = framing(box, camera.fov, FIT_MARGIN, MIN_FOCUS_DISTANCE);
      }
    };

    // three-forcegraph restarts its engine on every re-colouring, which stops it again at once; only the first stop is the settled layout
    graph.onEngineStop(() => {
      if (settled) {
        return;
      }
      settled = true;
      const box = graph.getGraphBbox();
      if (box) {
        const { centre, distance } = framing(box, camera.fov, FIT_MARGIN, MIN_CAMERA_DISTANCE);
        controls.target.copy(centre);
        camera.position.set(centre.x, centre.y, centre.z + distance);
      }
      focusRunning();
    });

    const composer = new EffectComposer(renderer);
    composer.addPass(new RenderPass(scene, camera));
    composer.addPass(new UnrealBloomPass(new Vector2(1, 1), BLOOM.strength, BLOOM.radius, BLOOM.threshold));
    composer.addPass(new OutputPass());

    const resize = () => {
      const { clientWidth, clientHeight } = host;
      renderer.setSize(clientWidth, clientHeight);
      composer.setSize(clientWidth, clientHeight);
      camera.aspect = clientWidth / Math.max(1, clientHeight);
      camera.updateProjectionMatrix();
    };
    const observer = new ResizeObserver(resize);
    observer.observe(host);
    resize();

    const raycaster = new Raycaster();
    pickRef.current = (event) => {
      const bounds = renderer.domElement.getBoundingClientRect();
      const pointer = new Vector2(((event.clientX - bounds.left) / bounds.width) * 2 - 1, -((event.clientY - bounds.top) / bounds.height) * 2 + 1);
      raycaster.setFromCamera(pointer, camera);
      return raycaster.intersectObject(graph, true).map((hit) => nodeOf(hit.object)).find((node) => node !== null) ?? null;
    };

    let frame = 0;
    const animate = () => {
      graph.tickFrame();
      if (goal && stepTowards(controls.target, camera.position, goal, reducedMotion ? 1 : FOCUS_EASING, zoomedByUser)) {
        goal = null;
      }
      const distanceBefore = camera.position.distanceTo(controls.target);
      controls.update();
      zoomedByUser ||= Math.abs(camera.position.distanceTo(controls.target) - distanceBefore) > ZOOM_TOLERANCE;
      composer.render();
      frame = requestAnimationFrame(animate);
    };
    animate();

    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      controls.dispose();
      composer.dispose();
      scene.traverse((object) => {
        const mesh = object as Partial<Mesh>;
        mesh.geometry?.dispose();
      });
      renderer.dispose();
      renderer.forceContextLoss();
      renderer.domElement.remove();
      graphRef.current = null;
      pickRef.current = null;
      focusRef.current = null;
    };
  }, [loaded, webGlMissing, theme]);

  useEffect(() => {
    graphRef.current?.nodeColor((node) => colourOf(node));
    focusRef.current?.(activeProcedures);
  }, [activeProcedures]);

  const hover = (event: PointerEvent<HTMLDivElement>) => {
    const node = pickRef.current?.(event) ?? null;
    const bounds = event.currentTarget.getBoundingClientRect();
    setTooltip(node && { label: node.label, kind: KIND_LABELS[node.kind], left: event.clientX - bounds.left, top: event.clientY - bounds.top });
  };

  const open = (event: PointerEvent<HTMLDivElement>) => {
    const node = pickRef.current?.(event) ?? null;
    if (node && (node.kind === 'COMPOSITE' || node.kind === 'ATOMIC')) {
      onOpenProcedure(node.id);
    }
  };

  if (webGlMissing) {
    return (
      <div className="brain card">
        <div className="empty-state" role="alert">
          <MonitorX size={32} />
          <p>This browser cannot draw 3D graphics (WebGL is off or missing). The Map view shows the same graph.</p>
        </div>
      </div>
    );
  }
  return (
    <div className="brain card">
      <div className="toolbar">
        <span className="loading-note">
          {loading && <LoaderCircle size={16} className="spin" />}
          {loaded?.kind === 'ok'
            ? `${loaded.data.nodes.length} procedures and elements${loaded.data.capped ? ', the first 5000 only' : ''}`
            : loaded === null ? 'Loading the whole graph…' : 'Knowledge graph unavailable'}
        </span>
        {activeProcedures.length === 0 && <span>Nothing is running.</span>}
        <button type="button" className="small toolbar-end" disabled={loading} onClick={reload}>
          <RefreshCw size={16} className={loading ? 'spin' : undefined} />
          Refresh
        </button>
      </div>
      {loaded?.kind === 'ok' && activeProcedures.length > 0 && (
        <div className="brain-running">
          <span className="eyebrow">Glowing now</span>
          <ol className="chain">
            {activeProcedures.map((id, index) => (
              <li key={`${id}-${index}`}>
                <button type="button" className="link" onClick={() => onOpenProcedure(id)}>
                  {loaded.data.nodes.find((node) => node.id === id)?.label ?? id}
                </button>
              </li>
            ))}
          </ol>
        </div>
      )}
      <ul className="legend" aria-label="Brain legend">
        <KindLegendEntries />
        <li><span className="ring-swatch ring-active" aria-hidden="true" />Glowing: running now</li>
      </ul>
      {/* Picking a node in 3D has no keyboard form; the running chain above and the Map view's search reach every procedure instead */}
      {/* eslint-disable-next-line jsx-a11y-x/click-events-have-key-events, jsx-a11y-x/no-noninteractive-element-interactions */}
      <div ref={container} className="brain-canvas" onPointerMove={hover} onPointerLeave={() => setTooltip(null)} onClick={open}
        aria-label="3D overview of the knowledge graph; click a procedure to open it on the map" role="img">
        {tooltip && (
          <div className="canvas-tooltip" style={{ left: tooltip.left, top: tooltip.top }}>
            <strong>{tooltip.label}</strong>
            <span>{tooltip.kind}</span>
          </div>
        )}
      </div>
    </div>
  );
}

function graphDataOf(slice: GraphSlice): { nodes: BrainNode[]; links: BrainLink[] } {
  return {
    nodes: slice.nodes.map((node) => ({ ...node })),
    links: slice.edges.map((edge) => ({ source: edge.source, target: edge.target })),
  };
}

// three-forcegraph tags every node object it draws with its type and datum
function nodeOf(object: Object3D | null): BrainNode | null {
  for (let current = object; current; current = current.parent) {
    const tagged = current as Object3D & { __graphObjType?: string; __data?: BrainNode };
    if (tagged.__graphObjType === 'node' && tagged.__data) {
      return tagged.__data;
    }
  }
  return null;
}

function supportsWebGl(): boolean {
  const canvas = document.createElement('canvas');
  return canvas.getContext('webgl2') !== null || canvas.getContext('webgl') !== null;
}
