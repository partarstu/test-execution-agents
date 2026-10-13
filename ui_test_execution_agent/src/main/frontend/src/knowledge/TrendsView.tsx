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
import { ChevronRight, LoaderCircle } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { useLoaded } from '../useLoaded';
import { fetchTrends } from './api';
import type { Week } from './model';
import { OUTCOME_LABELS, SOURCE_LABELS } from './ResolutionExplanation';
import './TrendsView.css';

const RANGES = [4, 12, 26, 52];
const CHART = { height: 168, top: 12, bottom: 22, left: 28, gap: 2, maxBar: 20, radius: 4 };

/** One stacked part of a bar; `swatch` names the CSS class that colours it, so the colour stays in the stylesheets for both themes. */
interface Series {
  label: string;
  swatch: string;
  value: (week: Week) => number;
}

const GROWTH: Series[] = [
  { label: 'Procedures created', swatch: 'series-1', value: (week) => week.proceduresCreated },
];
const ANOMALIES: Series[] = [
  { label: 'Element not located', swatch: 'series-1', value: (week) => week.anomalies.ELEMENT_LOCATION_FAILED ?? 0 },
  { label: 'Step failed', swatch: 'series-2', value: (week) => week.anomalies.STEP_FAILED ?? 0 },
  { label: 'Unusually slow step', swatch: 'series-3', value: (week) => week.anomalies.TIMING_OUTLIER ?? 0 },
];
const RESOLUTIONS: Series[] = [
  { label: SOURCE_LABELS.KNOWLEDGE, swatch: 'series-1', value: (week) => week.resolutions.KNOWLEDGE ?? 0 },
  { label: SOURCE_LABELS.OPERATOR_SELECTED, swatch: 'series-2', value: (week) => week.resolutions.OPERATOR_SELECTED ?? 0 },
  { label: SOURCE_LABELS.OPERATOR_CREATED, swatch: 'series-3', value: (week) => week.resolutions.OPERATOR_CREATED ?? 0 },
  { label: SOURCE_LABELS.UNRESOLVED, swatch: 'series-4', value: (week) => week.resolutions.UNRESOLVED ?? 0 },
];
const OUTCOMES: Series[] = [
  { label: OUTCOME_LABELS.SUCCEEDED, swatch: 'status-passed', value: (week) => week.outcomes.SUCCEEDED ?? 0 },
  { label: OUTCOME_LABELS.FAILED, swatch: 'status-failed', value: (week) => week.outcomes.FAILED ?? 0 },
  { label: OUTCOME_LABELS.NOT_EXECUTED, swatch: 'status-muted', value: (week) => week.outcomes.NOT_EXECUTED ?? 0 },
];

/** How the knowledge and the runs developed week by week, in UTC weeks starting on Monday. */
export function TrendsView({ onUnauthorized }: { onUnauthorized: () => void }) {
  const [weeks, setWeeks] = useState(12);
  const { loaded, loading } = useLoaded(String(weeks), (signal) => fetchTrends(weeks, signal), onUnauthorized);
  const rangeId = useId();

  return (
    <div className="trends card">
      <div className="toolbar">
        <label htmlFor={rangeId}>Range</label>
        <select id={rangeId} value={weeks} onChange={(event) => setWeeks(Number(event.target.value))}>
          {RANGES.map((range) => <option key={range} value={range}>Last {range} weeks</option>)}
        </select>
        {loading && loaded !== null && <span className="loading-note" role="status"><LoaderCircle size={16} className="spin" />Updating…</span>}
      </div>
      {loaded === null && <p className="loading-note"><LoaderCircle size={18} className="spin" />Counting the weeks…</p>}
      {loaded !== null && loaded.kind !== 'ok' && <p className="alert" role="alert">Knowledge graph unavailable.</p>}
      {loaded?.kind === 'ok' && (
        <div className="trend-grid">
          <WeeklyBars title="Knowledge growth" weeks={loaded.data.weeks} series={GROWTH}
            note="UI elements carry no creation time, so their growth is not shown." />
          <WeeklyBars title="Anomalies by kind" weeks={loaded.data.weeks} series={ANOMALIES} />
          <WeeklyBars title="How steps were resolved" weeks={loaded.data.weeks} series={RESOLUTIONS} note={hitRate(loaded.data.weeks)} />
          <WeeklyBars title="Step outcomes" weeks={loaded.data.weeks} series={OUTCOMES} />
        </div>
      )}
    </div>
  );
}

function hitRate(weeks: Week[]): string {
  const resolved = weeks.reduce((sum, week) => sum + RESOLUTIONS.reduce((total, series) => total + series.value(week), 0), 0);
  if (resolved === 0) {
    return 'No recorded runs in this range yet.';
  }
  const fromKnowledge = weeks.reduce((sum, week) => sum + RESOLUTIONS[0].value(week), 0);
  return `Knowledge hit rate: ${Math.round((fromKnowledge / resolved) * 100)}% of ${resolved} resolved steps came from the graph.`;
}

const MAX_TICK_STEPS = 4;
const NICE_STEPS = [1, 2, 5];

/** The y-axis ticks: 0 and at most four equal steps of 1, 2 or 5 times a power of ten, the last at or just above `max`. */
export function axisTicks(max: number): number[] {
  for (let power = 1; ; power *= 10) {
    for (const nice of NICE_STEPS) {
      const step = nice * power;
      const steps = Math.ceil(max / step);
      if (steps <= MAX_TICK_STEPS) {
        return Array.from({ length: steps + 1 }, (_, index) => index * step);
      }
    }
  }
}

// The width a medium date takes at the axis font size, and the room kept between two dates
const DATE_LABEL_WIDTH = 84;
const DATE_LABEL_GAP = 16;

/**
 * The weeks labelled between the first and the last date, which sit flush with the plot's ends: every so many weeks, centred under
 * their bar, as many as fit without touching each other or the end dates.
 */
export function innerWeekLabels(weekCount: number, plotStart: number, plotEnd: number): number[] {
  const slot = (plotEnd - plotStart) / Math.max(1, weekCount);
  const step = Math.ceil((DATE_LABEL_WIDTH + DATE_LABEL_GAP) / slot);
  const centre = (index: number) => plotStart + (index + 0.5) * slot;
  const clearOfEnds = (index: number) => centre(index) - DATE_LABEL_WIDTH / 2 >= plotStart + DATE_LABEL_WIDTH + DATE_LABEL_GAP
    && centre(index) + DATE_LABEL_WIDTH / 2 <= plotEnd - DATE_LABEL_WIDTH - DATE_LABEL_GAP;
  const labelled: number[] = [];
  for (let index = step; index < weekCount - 1; index += step) {
    if (clearOfEnds(index)) {
      labelled.push(index);
    }
  }
  return labelled;
}

/** Stacked weekly bars with hairline gridlines, a readout of every series for the week under the pointer, and the same numbers as a table. */
function WeeklyBars({ title, weeks, series, note }: { title: string; weeks: Week[]; series: Series[]; note?: string }) {
  const [hovered, setHovered] = useState<number | null>(null);
  const [width, setWidth] = useState(640);
  const plot = useRef<HTMLDivElement>(null);
  const headingId = useId();

  // The chart is drawn at its rendered width, so its text and bars keep their pixel sizes in any column
  useEffect(() => {
    const element = plot.current;
    if (!element) {
      return;
    }
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(240, Math.round(entry.contentRect.width))));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const totals = weeks.map((week) => series.reduce((sum, part) => sum + part.value(week), 0));
  const ticks = axisTicks(Math.max(1, ...totals));
  const max = ticks[ticks.length - 1];
  const plotHeight = CHART.height - CHART.top - CHART.bottom;
  const plotWidth = width - CHART.left;
  const slot = plotWidth / Math.max(1, weeks.length);
  const barWidth = Math.min(CHART.maxBar, Math.max(4, slot * 0.6));
  const baseline = CHART.top + plotHeight;
  const y = (value: number) => baseline - (value / max) * plotHeight;
  const inRange = totals.reduce((sum, total) => sum + total, 0);
  const lastWeek = totals.at(-1) ?? 0;
  const hoveredWeek = hovered === null ? null : weeks[hovered];

  return (
    <section className="trend" aria-labelledby={headingId}>
      <div className="trend-head">
        <h3 id={headingId}>{title}</h3>
        <dl className="trend-figures">
          <div>
            <dt>In range</dt>
            <dd>{inRange.toLocaleString()}</dd>
          </div>
          <div>
            <dt>Last week</dt>
            <dd>{lastWeek.toLocaleString()}</dd>
          </div>
        </dl>
      </div>
      <ul className="legend" aria-label={`${title} legend`}>
        {series.map((part) => (
          <li key={part.label}><span className={`swatch ${part.swatch}`} aria-hidden="true" />{part.label}</li>
        ))}
      </ul>
      <div className="trend-plot" ref={plot}>
        <svg className="trend-chart" viewBox={`0 0 ${width} ${CHART.height}`} height={CHART.height} role="img" aria-labelledby={headingId}
          onPointerLeave={() => setHovered(null)}>
          {ticks.map((tick) => (
            <g key={tick}>
              {tick > 0 && <line className="trend-grid-line" x1={CHART.left} x2={width} y1={y(tick)} y2={y(tick)} />}
              <text className="trend-axis" x={CHART.left - 6} y={y(tick) + 4} textAnchor="end">{tick}</text>
            </g>
          ))}
          <line className="trend-baseline" x1={CHART.left} x2={width} y1={baseline} y2={baseline} />
          {weeks.map((week, index) => {
            const x = CHART.left + index * slot + (slot - barWidth) / 2;
            const parts = series.map((part) => ({ part, value: part.value(week) })).filter(({ value }) => value > 0);
            let top = baseline;
            return (
              <g key={week.start} className={hovered === index ? 'trend-week trend-week-hovered' : 'trend-week'} onPointerEnter={() => setHovered(index)}>
                <rect className="trend-band" x={CHART.left + index * slot} y={CHART.top} width={slot} height={plotHeight} />
                {parts.map(({ part, value }, partIndex) => {
                  const height = Math.max(1, (value / max) * plotHeight - CHART.gap);
                  const bottom = top;
                  top -= height + CHART.gap;
                  const segmentTop = bottom - height;
                  return partIndex === parts.length - 1
                    ? <path key={part.label} className={part.swatch} d={roundedTop(x, segmentTop, barWidth, height)} />
                    : <rect key={part.label} className={part.swatch} x={x} y={segmentTop} width={barWidth} height={height} />;
                })}
              </g>
            );
          })}
          {weeks.length > 0 && (
            <>
              <text className="trend-axis" x={CHART.left} y={CHART.height - 4}>{weekDate(weeks[0].start)}</text>
              {innerWeekLabels(weeks.length, CHART.left, width).map((index) => (
                <text key={weeks[index].start} className="trend-axis" x={CHART.left + (index + 0.5) * slot} y={CHART.height - 4} textAnchor="middle">
                  {weekDate(weeks[index].start)}
                </text>
              ))}
              <text className="trend-axis" x={width} y={CHART.height - 4} textAnchor="end">{weekDate(weeks[weeks.length - 1].start)}</text>
            </>
          )}
        </svg>
        {hoveredWeek && hovered !== null && (
          <div className={hovered >= weeks.length / 2 ? 'trend-tooltip trend-tooltip-before' : 'trend-tooltip'} aria-hidden="true"
            style={{ insetInlineStart: CHART.left + (hovered + 0.5) * slot }}>
            <span className="trend-tooltip-title">Week of {weekDate(hoveredWeek.start)}</span>
            {series.map((part) => (
              <span key={part.label} className="trend-tooltip-row">
                <span className={`trend-tooltip-key ${part.swatch}`} />
                <strong>{part.value(hoveredWeek)}</strong>
                <span>{part.label}</span>
              </span>
            ))}
          </div>
        )}
      </div>
      {note && <p className="section-note">{note}</p>}
      <details className="table-alternative">
        <summary><ChevronRight size={16} className="disclosure" />Show as table</summary>
        <table>
          <thead>
            <tr>
              <th scope="col">Week of</th>
              {series.map((part) => <th key={part.label} scope="col">{part.label}</th>)}
            </tr>
          </thead>
          <tbody>
            {weeks.map((week) => (
              <tr key={week.start}>
                <th scope="row">{weekDate(week.start)}</th>
                {series.map((part) => <td key={part.label}>{part.value(week)}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </section>
  );
}

// A bar's data end is rounded and its baseline end square, so a stacked bar reads as one bar growing from the axis
function roundedTop(x: number, top: number, width: number, height: number): string {
  const radius = Math.min(CHART.radius, width / 2, height);
  return `M${x},${top + height}V${top + radius}Q${x},${top} ${x + radius},${top}H${x + width - radius}`
    + `Q${x + width},${top} ${x + width},${top + radius}V${top + height}Z`;
}

// Weeks are UTC days without a time, which a local time zone west of UTC would show as the day before
function weekDate(day: string): string {
  return new Date(day).toLocaleDateString(undefined, { dateStyle: 'medium', timeZone: 'UTC' });
}
