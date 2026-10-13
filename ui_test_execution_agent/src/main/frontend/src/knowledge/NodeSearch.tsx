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
import { LoaderCircle, Search } from 'lucide-react';
import { useId, useState, type FormEvent } from 'react';
import { IconButton } from '../kit/IconButton';
import { searchKnowledge, type Loaded } from './api';
import type { GraphNode, NodeKind, SearchResults } from './model';
import { KIND_LABELS } from './theme';
import './NodeSearch.css';

interface Props {
  label: string;
  /** The kinds of node offered as hits; every kind when absent. */
  kinds?: readonly NodeKind[];
  disabled?: boolean;
  onOpen: (hit: GraphNode) => void;
  onUnauthorized: () => void;
}

/** A search of the knowledge graph by description or name, whose hits open what the caller does with a node. */
export function NodeSearch({ label, kinds, disabled = false, onOpen, onUnauthorized }: Props) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Loaded<SearchResults> | null>(null);
  const [searching, setSearching] = useState(false);
  const queryId = useId();

  const search = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = query.trim();
    if (trimmed === '') {
      return;
    }
    setSearching(true);
    void searchKnowledge(trimmed).then((result) => {
      setSearching(false);
      if (result.kind === 'unauthorized') {
        onUnauthorized();
      } else {
        setResults(result);
      }
    });
  };

  return (
    <>
      <form className="node-search" role="search" onSubmit={search}>
        <label htmlFor={queryId}>{label}</label>
        <div className="node-search-row">
          <input id={queryId} type="search" maxLength={100} value={query} onChange={(event) => setQuery(event.target.value)} />
          <IconButton icon={Search} label="Search" tone="primary" submit disabled={disabled || searching} />
        </div>
      </form>
      {searching && <p className="loading-note" role="status"><LoaderCircle size={16} className="spin" />Searching…</p>}
      {results?.kind === 'ok' && (
        <SearchHits hits={results.data.hits.filter((hit) => kinds?.includes(hit.kind) ?? true)} capped={results.data.capped}
          onOpen={onOpen} />
      )}
      {results !== null && results.kind !== 'ok' && <p className="alert" role="alert">The search failed. Try again.</p>}
    </>
  );
}

function SearchHits({ hits, capped, onOpen }: { hits: GraphNode[]; capped: boolean; onOpen: (hit: GraphNode) => void }) {
  if (hits.length === 0) {
    return (
      <p className="empty">
        {capped ? 'The first 50 matches are all of another kind; narrow the search to see the rest.' : 'Nothing matches. Try a shorter part of the description.'}
      </p>
    );
  }
  return (
    <div className="node-hits">
      <ul>
        {hits.map((hit) => (
          <li key={hit.id}>
            <button type="button" onClick={() => onOpen(hit)}>
              <span className={`kind-swatch kind-${hit.kind.toLowerCase()}`} aria-hidden="true" />
              <span className="node-hit-label">{hit.label}</span>
              <span className="node-hit-kind">{KIND_LABELS[hit.kind]}</span>
            </button>
          </li>
        ))}
      </ul>
      {capped && <p className="empty">Showing the first 50 matches; narrow the search to see the rest.</p>}
    </div>
  );
}
