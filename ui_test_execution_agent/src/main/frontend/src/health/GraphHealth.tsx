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
import { ChevronRight, CircleAlert, HeartPulse, Image, MapPin, RefreshCw, ShieldAlert, ShieldCheck, ShieldX } from 'lucide-react';
import { useEffect, useId, useState } from 'react';
import { fetchHealth } from '../api';
import { age, localTime } from '../format';
import { Aspect, type Tone } from '../kit/Aspect';
import { useLoaded } from '../useLoaded';
import type { GraphHealthReport, HealthCheckCategory, NodeRef, Severity } from './GraphHealthReport';
import './GraphHealth.css';

const SEVERITY_LABELS: Record<Severity, string> = { OK: 'OK', WARNING: 'Warning', CRITICAL: 'Critical' };

const SEVERITY_TONES: Record<Severity, Tone> = { OK: 'passed', WARNING: 'warning', CRITICAL: 'failed' };

const SEVERITY_ORDER: Severity[] = ['CRITICAL', 'WARNING', 'OK'];

const VERDICTS: Record<Severity, string> = {
  OK: 'The knowledge graph is healthy',
  WARNING: 'The knowledge graph needs attention',
  CRITICAL: 'The knowledge graph has critical findings',
};

type Filter = Severity | 'ALL';

const FILTER_LABELS: Record<Filter, string> = { ALL: 'All checks', CRITICAL: 'Critical', WARNING: 'Warnings', OK: 'Passed' };

const SUMMARY_LABELS: Record<Severity, (count: number) => string> = {
  CRITICAL: (count) => (count === 1 ? '1 critical check' : `${count} critical checks`),
  WARNING: (count) => (count === 1 ? '1 check with warnings' : `${count} checks with warnings`),
  OK: (count) => (count === 1 ? '1 check passed' : `${count} checks passed`),
};

const AGE_REFRESH_MS = 30_000;

interface Props {
  onUnauthorized: () => void;
  onOpenNode: (kind: 'procedure' | 'element', id: string) => void;
}

/** The knowledge graph health report, checked when the tab is first shown and on every Refresh, never in the background. */
export function GraphHealth({ onUnauthorized, onOpenNode }: Props) {
  const { loaded, loading, reload } = useLoaded('health', fetchHealth, onUnauthorized);
  const [now, setNow] = useState(() => Date.now());
  const headingId = useId();

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), AGE_REFRESH_MS);
    return () => clearInterval(timer);
  }, []);

  const report = loaded?.kind === 'ok' ? loaded.data : null;
  return (
    <section className="health card" aria-labelledby={headingId} aria-busy={loading}>
      <div className="section-head">
        <h2 id={headingId}><HeartPulse size={20} />Knowledge graph health</h2>
        <div className="section-tools">
          {report && (
            <span className="section-note">
              Checked <time dateTime={report.generatedAt}>{localTime(report.generatedAt)}</time>{', '}
              {age(report.generatedAt, Math.max(now, Date.parse(report.generatedAt)))}
            </span>
          )}
          <button type="button" onClick={reload} disabled={loading} aria-busy={loading}>
            <RefreshCw size={16} className={loading ? 'spin' : undefined} />
            {loading ? 'Checking…' : 'Check again'}
          </button>
        </div>
      </div>
      {loaded === null && <HealthSkeleton />}
      {loaded !== null && loaded.kind !== 'ok' && (
        <div className="alert" role="alert">
          <CircleAlert size={18} />
          <p><strong>Knowledge graph unavailable.</strong> The agent could not run the check against its Neo4j database. Check again to retry.</p>
        </div>
      )}
      {report && <ReportBody report={report} onOpenNode={onOpenNode} />}
    </section>
  );
}

/** While the first check runs: the shape of the report to come, and why it takes a moment. */
function HealthSkeleton() {
  return (
    <div className="health-loading">
      <p className="loading-note">Checking the knowledge graph. This runs sixteen queries and can take a while.</p>
      <span className="skeleton health-skeleton-verdict" />
      <span className="skeleton health-skeleton-row" />
      <span className="skeleton health-skeleton-row" />
      <span className="skeleton health-skeleton-row" />
    </div>
  );
}

function ReportBody({ report, onOpenNode }: { report: GraphHealthReport; onOpenNode: Props['onOpenNode'] }) {
  const [filter, setFilter] = useState<Filter>('ALL');
  const filterName = useId();
  const total = report.categories.reduce((sum, category) => sum + category.findings.length, 0);
  const countOf = (severity: Severity) => report.categories.filter((category) => category.severity === severity).length;
  const worst = SEVERITY_ORDER.find((severity) => countOf(severity) > 0) ?? 'OK';
  const shown = bySeverity(report.categories).filter((category) => filter === 'ALL' || category.severity === filter);
  return (
    <>
      <div className={`health-verdict health-verdict-${worst.toLowerCase()}`}>
        <span className="health-verdict-icon" aria-hidden="true"><VerdictIcon severity={worst} /></span>
        <div className="health-verdict-text">
          <p className="health-verdict-title">{VERDICTS[worst]}</p>
          <p className="health-verdict-counts">
            {SEVERITY_ORDER.filter((severity) => countOf(severity) > 0).map((severity) => (
              <span key={severity}>{SUMMARY_LABELS[severity](countOf(severity))}</span>
            ))}
          </p>
          <p className="section-note">{total === 1 ? '1 finding' : `${total} findings`} in {report.categories.length} checks</p>
        </div>
      </div>
      {total === 0 ? (
        <div className="empty-state">
          <ShieldCheck size={32} />
          <strong>No findings</strong>
          <p>The knowledge graph passed all {report.categories.length} checks.</p>
        </div>
      ) : (
        <>
          <fieldset className="stat-tiles">
            <legend className="visually-hidden">Show the checks</legend>
            {(['ALL', ...SEVERITY_ORDER] as Filter[]).map((option) => (
              <label key={option} className={`stat-tile stat-tile-${option.toLowerCase()}`}>
                <input type="radio" name={filterName} checked={filter === option} onChange={() => setFilter(option)} />
                <span className="stat-tile-label">{FILTER_LABELS[option]}</span>{' '}
                <span className="stat-tile-value">{option === 'ALL' ? report.categories.length : countOf(option)}</span>
              </label>
            ))}
          </fieldset>
          {shown.length === 0 ? (
            <p className="empty">No check has this severity.</p>
          ) : (
            <ul className="categories">
              {shown.map((category) => (
                <li key={category.name}>
                  <CategoryRow category={category} onOpenNode={onOpenNode} />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </>
  );
}

function VerdictIcon({ severity }: { severity: Severity }) {
  switch (severity) {
    case 'OK':
      return <ShieldCheck size={28} />;
    case 'WARNING':
      return <ShieldAlert size={28} />;
    case 'CRITICAL':
      return <ShieldX size={28} />;
  }
}

/** The categories with the critical ones first and the passed ones last, in the server's order within one severity. */
export function bySeverity(categories: HealthCheckCategory[]): HealthCheckCategory[] {
  return categories.toSorted((first, second) => SEVERITY_ORDER.indexOf(first.severity) - SEVERITY_ORDER.indexOf(second.severity));
}

function CategoryRow({ category, onOpenNode }: { category: HealthCheckCategory; onOpenNode: Props['onOpenNode'] }) {
  const count = category.findings.length;
  return (
    <details className="category">
      <summary>
        <ChevronRight size={18} className="disclosure" />
        <Aspect tone={SEVERITY_TONES[category.severity]}>{SEVERITY_LABELS[category.severity]}</Aspect>
        <span className="category-text">
          <span className="category-name">{category.name}</span>
          <span className="category-description">{category.description}</span>
        </span>
        <span className="category-count">
          {count}
          <span className="visually-hidden">{count === 1 ? ' finding' : ' findings'}</span>
        </span>
      </summary>
      {count === 0 ? (
        <p className="category-findings empty">Nothing found.</p>
      ) : (
        <ul className="category-findings findings">
          {category.findings.map((finding, index) => (
            <li key={index}>
              <span>{finding.text}</span>
              <FindingRefs refs={finding.refs} onOpenNode={onOpenNode} />
            </li>
          ))}
        </ul>
      )}
    </details>
  );
}

function FindingRefs({ refs, onOpenNode }: { refs: NodeRef[]; onOpenNode: Props['onOpenNode'] }) {
  const procedures = refs.filter((ref) => ref.label === 'Procedure');
  const elements = refs.filter((ref) => ref.label === 'UiElement');
  if (procedures.length + elements.length === 0) {
    return null;
  }
  return (
    <span className="finding-refs">
      {procedures.map((ref, index) => (
        <button key={`${ref.id}-${index}`} type="button" className="small quiet" onClick={() => onOpenNode('procedure', ref.id)}>
          <MapPin size={16} />
          {procedures.length > 1 ? `Show procedure ${index + 1} on the map` : 'Show on the map'}
        </button>
      ))}
      {elements.map((ref, index) => (
        <button key={`${ref.id}-${index}`} type="button" className="small quiet" onClick={() => onOpenNode('element', ref.id)}>
          <Image size={16} />
          Show the element
        </button>
      ))}
    </span>
  );
}
