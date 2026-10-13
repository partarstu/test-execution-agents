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
import { ImageOff, Images, LoaderCircle, MousePointerClick } from 'lucide-react';
import { useEffect, useEffectEvent, useId, useRef, useState } from 'react';
import { duration, localDate, localDateTime } from '../format';
import { Meter } from '../kit/Meter';
import { useLoaded } from '../useLoaded';
import { appearanceScreenshotUrl, elementScreenshotUrl, fetchElement, fetchElements } from './api';
import type { FocusRequest } from './KnowledgeTab';
import type { ElementSort, ElementTile } from './model';
import './DetailsPanel.css';
import './ElementsView.css';

const SORT_LABELS: Record<ElementSort, string> = {
  stability: 'Least stable first',
  updated: 'Recently changed first',
  retries: 'Most location retries first',
};

interface Props {
  focus: FocusRequest | null;
  onOpenProcedure: (procedureId: string) => void;
  onUnauthorized: () => void;
}

/** The UI elements as their screenshots, with the statistics that tell an unstable element from a dependable one. */
export function ElementsView({ focus, onOpenProcedure, onUnauthorized }: Props) {
  const [sort, setSort] = useState<ElementSort>('stability');
  const [tiles, setTiles] = useState<ElementTile[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [openId, setOpenId] = useState<string | null>(null);
  const [adoptedFocus, setAdoptedFocus] = useState(-1);
  const sortId = useId();
  // Showing the view again re-runs its effects, which must not reload the pages already shown
  const loadedSort = useRef<ElementSort | null>(null);

  const loadPage = (nextSort: ElementSort, offset: number) => {
    setLoading(true);
    void fetchElements(nextSort, offset).then((result) => {
      setLoading(false);
      if (result.kind === 'unauthorized') {
        onUnauthorized();
        return;
      }
      setFailed(result.kind !== 'ok');
      if (result.kind === 'ok') {
        setTiles((current) => (offset === 0 ? result.data.elements : [...current, ...result.data.elements]));
        setHasMore(result.data.hasMore);
      }
    });
  };
  const loadFirstPage = useEffectEvent(() => {
    if (loadedSort.current !== sort) {
      loadedSort.current = sort;
      loadPage(sort, 0);
    }
  });

  useEffect(() => {
    loadFirstPage();
  }, [sort]);

  if (focus && focus.seq !== adoptedFocus) {
    setAdoptedFocus(focus.seq);
    setOpenId(focus.id);
  }

  return (
    <div className="elements">
      <div className="elements-main card">
        <div className="toolbar">
          <label htmlFor={sortId}>Order</label>
          <select id={sortId} value={sort} onChange={(event) => setSort(event.target.value as ElementSort)}>
            {(Object.keys(SORT_LABELS) as ElementSort[]).map((key) => <option key={key} value={key}>{SORT_LABELS[key]}</option>)}
          </select>
          {tiles.length > 0 && <span className="toolbar-end">{tiles.length === 1 ? '1 element shown' : `${tiles.length} elements shown`}</span>}
        </div>
        {failed && <p className="alert" role="alert">Knowledge graph unavailable. Change the order to try again.</p>}
        {!failed && !loading && tiles.length === 0 && (
          <div className="empty-state">
            <Images size={32} />
            <p>No UI elements yet. They appear once the agent has located some.</p>
          </div>
        )}
        <ul className="element-grid">
          {tiles.map((tile) => (
            <li key={tile.id}>
              <button type="button" className="element-tile" aria-pressed={openId === tile.id} onClick={() => setOpenId(tile.id)}>
                {tile.hasScreenshot ? (
                  <img src={elementScreenshotUrl(tile.id)} alt="" loading="lazy" decoding="async" />
                ) : (
                  <span className="element-no-image"><ImageOff size={20} />No screenshot</span>
                )}
                <span className="element-name">{tile.name}</span>
                <TileStats tile={tile} />
              </button>
            </li>
          ))}
        </ul>
        {hasMore && (
          <button type="button" disabled={loading} aria-busy={loading} onClick={() => loadPage(sort, tiles.length)}>
            {loading && <LoaderCircle size={16} className="spin" />}
            {loading ? 'Loading…' : 'Show more elements'}
          </button>
        )}
      </div>
      {openId ? (
        <ElementPanel key={openId} elementId={openId} onOpenProcedure={onOpenProcedure} onUnauthorized={onUnauthorized} />
      ) : (
        <section className="details card" aria-label="Element details">
          <div className="empty-state">
            <MousePointerClick size={28} />
            <p>Select an element to see its descriptions, the procedures acting on it and how it looked over time.</p>
          </div>
        </section>
      )}
    </div>
  );
}

function TileStats({ tile }: { tile: ElementTile }) {
  const stability = tile.stability === null ? null : Math.round(tile.stability * 100);
  return (
    <span className="element-stats">
      <span className="element-stability">
        <span>Stability {stability === null ? 'not measured' : `${stability}%`}</span>
        {stability !== null && <Meter label="Stability" value={stability} max={100} valueText={`${stability}%`} />}
      </span>
      {tile.avgLocationTimeMs !== null && <span>Found in {duration(tile.avgLocationTimeMs)}</span>}
      {tile.locationRetries !== null && tile.locationRetries > 0 && <span>{tile.locationRetries.toFixed(1)} retries</span>}
      <span>{tile.dataDependent ? 'Located by its data' : 'Located by its look'}</span>
    </span>
  );
}

function ElementPanel({ elementId, onOpenProcedure, onUnauthorized }: { elementId: string; onOpenProcedure: (id: string) => void;
  onUnauthorized: () => void }) {
  const { loaded } = useLoaded(elementId, (signal) => fetchElement(elementId, signal), onUnauthorized);
  const headingId = useId();

  if (loaded === null) {
    return (
      <section className="details card" aria-label="Element details" aria-busy="true">
        <p className="loading-note"><LoaderCircle size={18} className="spin" />Loading the element…</p>
      </section>
    );
  }
  if (loaded.kind !== 'ok') {
    return <section className="details card" aria-label="Element details"><p className="alert" role="alert">This element could not be loaded.</p></section>;
  }
  const details = loaded.data;
  return (
    <section className="details card" aria-labelledby={headingId}>
      <h3 id={headingId}>{details.tile.name}</h3>
      <p>{details.description}</p>
      <dl className="details-facts">
        <dt>Where it is</dt>
        <dd>{details.locationDetails || 'Not described'}</dd>
        <dt>On the page</dt>
        <dd>{details.parentElementSummary || 'Not described'}</dd>
        {details.tile.lastLocatedAt && (
          <>
            <dt>Last found</dt>
            <dd>{localDateTime(details.tile.lastLocatedAt)}</dd>
          </>
        )}
      </dl>
      {details.targetedBy.length > 0 && (
        <section>
          <h4>Procedures acting on it</h4>
          <ul>
            {details.targetedBy.map((ref) => (
              <li key={ref.id}><button type="button" className="link" onClick={() => onOpenProcedure(ref.id)}>{ref.description}</button></li>
            ))}
          </ul>
        </section>
      )}
      <section>
        <h4>How it looked</h4>
        <ol className="filmstrip">
          <li>
            {details.tile.hasScreenshot && <img src={elementScreenshotUrl(details.tile.id)} alt="The element as it looks now" loading="lazy" />}
            <span>Now{details.tile.updatedAt && `, since ${localDate(details.tile.updatedAt)}`}</span>
          </li>
          {details.appearances.map((appearance) => (
            <li key={appearance.id}>
              {appearance.hasScreenshot && (
                <img src={appearanceScreenshotUrl(details.tile.id, appearance.id)} alt="An earlier look of the element" loading="lazy" />
              )}
              <span>
                {appearance.validFrom ? `${localDate(appearance.validFrom)} to ` : 'Until '}
                {localDate(appearance.validTo)}
              </span>
              <span className="filmstrip-text">{appearance.ownDescription}</span>
            </li>
          ))}
        </ol>
        {details.appearances.length === 0 && <p className="empty">It has always looked like this.</p>}
      </section>
    </section>
  );
}
