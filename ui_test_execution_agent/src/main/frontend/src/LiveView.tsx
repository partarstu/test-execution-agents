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
import {
  Eye, Keyboard, LoaderCircle, Maximize, Maximize2, Minimize, Minimize2, Monitor, MonitorOff, MousePointerClick, RefreshCw,
} from 'lucide-react';
import { useEffect, useEffectEvent, useId, useRef, useState, type KeyboardEvent } from 'react';
import type RFB from '@novnc/novnc';
import { IconButton } from './kit/IconButton';
import './LiveView.css';

type Connection = 'connecting' | 'connected' | 'disconnected';

const CONNECTION_LABELS: Record<Connection, string> = {
  connecting: 'Connecting…',
  connected: 'Showing the agent’s screen',
  disconnected: 'Not connected',
};

/** Whether the key gives the keyboard back from the agent's screen; noVNC never sees it. */
export function isReleaseKey(event: Pick<KeyboardEvent, 'key' | 'shiftKey' | 'ctrlKey' | 'altKey' | 'metaKey'>): boolean {
  return event.key === 'Escape' && event.shiftKey && !event.ctrlKey && !event.altKey && !event.metaKey;
}

interface Props {
  liveControl: boolean;
  /** A view-only tile beside the timeline, or large in the middle of the Run tab, where the operator may control it. */
  presentation: 'tile' | 'large';
  onToggleSize: () => void;
}

export function LiveView({ liveControl, presentation, onToggleSize }: Props) {
  const stage = useRef<HTMLDivElement>(null);
  const screen = useRef<HTMLDivElement>(null);
  const controlToggle = useRef<HTMLButtonElement>(null);
  const rfb = useRef<RFB | null>(null);
  const [controlling, setControlling] = useState(false);
  const [watchOnlyExplained, setWatchOnlyExplained] = useState(false);
  const [fullscreen, setFullscreen] = useState(false);
  const [connection, setConnection] = useState<Connection>('connecting');
  const [session, setSession] = useState(0);
  const headingId = useId();
  const noteId = useId();
  const tile = presentation === 'tile';
  const controlActive = controlling && !tile;
  // Read when the client is created, which happens again each time <Activity> shows the tab, while `controlling` survives in state
  const isViewOnly = useEffectEvent(() => !(controlActive && liveControl));

  useEffect(() => {
    let disposed = false;
    // Loaded on demand, so a tab without a live view never downloads noVNC
    void import('@novnc/novnc').then(({ default: RemoteFrameBuffer }) => {
      if (disposed || !screen.current) {
        return;
      }
      const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
      const client = new RemoteFrameBuffer(screen.current, `${scheme}://${location.host}/api/screen/live`);
      client.viewOnly = isViewOnly();
      client.scaleViewport = true;
      // The agent's coordinates depend on the display geometry, so no viewer may resize it
      client.resizeSession = false;
      // noVNC paints its own grey behind the screen; the stage's colour shows through instead
      client.background = 'transparent';
      // A disposed client reports its disconnect late, possibly after its successor has connected
      const report = (next: Connection) => () => {
        if (!disposed) {
          setConnection(next);
        }
      };
      client.addEventListener('connect', report('connected'));
      client.addEventListener('disconnect', report('disconnected'));
      setConnection('connecting');
      rfb.current = client;
    });
    return () => {
      disposed = true;
      rfb.current?.disconnect();
      rfb.current = null;
    };
  }, [session]);

  useEffect(() => {
    if (rfb.current) {
      rfb.current.viewOnly = !(controlActive && liveControl);
    }
  }, [controlActive, liveControl]);

  useEffect(() => {
    const onChange = () => setFullscreen(document.fullscreenElement !== null && document.fullscreenElement === stage.current);
    document.addEventListener('fullscreenchange', onChange);
    return () => document.removeEventListener('fullscreenchange', onChange);
  }, []);

  // In control, noVNC takes every key on its canvas, Tab included; this capture runs first, so the keyboard can always leave
  const onKeyDownCapture = (event: KeyboardEvent<HTMLDivElement>) => {
    if (controlActive && isReleaseKey(event)) {
      event.preventDefault();
      event.stopPropagation();
      setControlling(false);
      controlToggle.current?.focus();
    }
  };

  const reconnect = () => {
    setConnection('connecting');
    setSession(session + 1);
  };

  const toggleFullscreen = () => {
    if (document.fullscreenElement) {
      void document.exitFullscreen();
    } else {
      void stage.current?.requestFullscreen();
    }
  };

  return (
    <section className={`live card live-${presentation}${controlActive ? ' live-controlling' : ''}`} aria-labelledby={headingId}>
      <div className="section-head">
        <h2 id={headingId}><Monitor size={18} />Live view</h2>
        <span className={`live-state live-${connection}`} role="status">{CONNECTION_LABELS[connection]}</span>
        <div className="section-tools">
          {connection === 'disconnected' && (
            <button type="button" className="small" onClick={reconnect}><RefreshCw size={16} />Reconnect</button>
          )}
          <button type="button" className={tile ? 'small quiet live-size' : 'small live-size'} aria-keyshortcuts="V" onClick={onToggleSize}>
            {tile ? <Maximize2 size={16} /> : <Minimize2 size={16} />}
            {tile ? 'Open large' : 'Back to the step'}
            <kbd className="kbd" aria-hidden="true">V</kbd>
          </button>
          {tile ? null : liveControl ? (
            <button ref={controlToggle} type="button" className={controlling ? 'small primary' : 'small'} aria-pressed={controlling}
              aria-describedby={controlling ? noteId : undefined} onClick={() => setControlling(!controlling)}>
              <MousePointerClick size={16} />
              Control the screen
            </button>
          ) : (
            <button type="button" className="small quiet live-watch-only" aria-expanded={watchOnlyExplained} aria-controls={noteId}
              onClick={() => setWatchOnlyExplained(!watchOnlyExplained)}>
              <Eye size={16} />
              Watching only
            </button>
          )}
          {!tile && (
            <IconButton icon={fullscreen ? Minimize : Maximize} label={fullscreen ? 'Leave full screen' : 'Show full screen'} small
              onClick={toggleFullscreen} />
          )}
        </div>
      </div>
      {controlActive && (
        <p id={noteId} className="note live-note">
          <Keyboard size={18} />
          <span>
            Your mouse and keyboard reach the agent’s screen. <kbd className="kbd">Shift</kbd> + <kbd className="kbd">Esc</kbd> gives
            the keyboard back.
          </span>
        </p>
      )}
      {!tile && !liveControl && watchOnlyExplained && (
        <p id={noteId} className="note live-note">
          <Eye size={18} />
          <span>This deployment allows watching only: your mouse and keyboard do not reach the agent’s screen.</span>
        </p>
      )}
      <div ref={stage} className={connection === 'disconnected' ? 'live-stage live-stage-idle' : 'live-stage'}
        onKeyDownCapture={onKeyDownCapture}>
        <div ref={screen} className="live-screen" />
        {/* The whole tile opens the live view for a pointer; the keyboard has the button above with its name */}
        {tile && <button type="button" className="live-tile-open" tabIndex={-1} aria-hidden="true" onClick={onToggleSize} />}
        {connection !== 'connected' && (
          <div className="live-overlay" aria-hidden="true">
            {connection === 'connecting' ? <LoaderCircle size={28} className="spin" /> : <MonitorOff size={28} />}
            <span>{connection === 'connecting' ? 'Connecting to the agent’s screen…' : 'The agent’s screen is not connected.'}</span>
          </div>
        )}
      </div>
    </section>
  );
}
