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
import './ResolutionExplanation.css';
import type { ResolutionSource, StepOutcome, StepResolution } from './model';

export const SOURCE_LABELS: Record<ResolutionSource, string> = {
  KNOWLEDGE: 'Found in the knowledge graph',
  OPERATOR_SELECTED: 'Selected by the operator',
  OPERATOR_CREATED: 'Created by the operator',
  UNRESOLVED: 'No procedure chosen',
};

export const OUTCOME_LABELS: Record<StepOutcome, string> = {
  SUCCEEDED: 'Succeeded',
  FAILED: 'Failed',
  NOT_EXECUTED: 'Not executed',
};

/** Why a procedure was chosen for an item: how, with which confidence, and how every candidate of the match scored. */
export function ResolutionExplanation({ resolution }: { resolution: StepResolution }) {
  const chosen = resolution.candidates.find((candidate) => candidate.procedureId === resolution.chosenProcedureId);
  return (
    <div className="resolution">
      <p>
        {SOURCE_LABELS[resolution.source]}
        {resolution.confidence && `, ${resolution.confidence === 'HIGH' ? 'high' : 'low'} confidence`}
        {chosen && `: ${chosen.description}`}
      </p>
      {resolution.wasDemoted && (
        <p className="resolution-note">
          {resolution.demotedDueToPrerequisites
            ? 'The best semantic match was passed over because its prerequisites were not met.'
            : 'The best semantic match was passed over for a candidate that fits the run so far better.'}
        </p>
      )}
      {resolution.candidates.length === 0 ? (
        <p className="empty">The knowledge graph offered no candidates.</p>
      ) : (
        <div className="candidates-scroll">
          <table className="candidates">
            <caption>Candidates in the order the agent ranked them</caption>
            <thead>
              <tr>
                <th scope="col">Procedure</th>
                <th scope="col">Similarity</th>
                <th scope="col">Prerequisites met</th>
                <th scope="col">Shared parents</th>
                <th scope="col">Stability</th>
              </tr>
            </thead>
            <tbody>
              {resolution.candidates.map((candidate) => (
                <tr key={candidate.procedureId} aria-current={candidate.procedureId === resolution.chosenProcedureId ? 'true' : undefined}>
                  <th scope="row">
                    {candidate.description}
                    {candidate.unmetPrerequisites.length > 0 && (
                      <span className="candidate-unmet">Unmet: {candidate.unmetPrerequisites.join('; ')}</span>
                    )}
                  </th>
                  <td>{candidate.semanticScore.toFixed(3)}</td>
                  <td>
                    {candidate.totalPrerequisites === 0
                      ? 'None needed'
                      : `${candidate.satisfiedPrerequisites} of ${candidate.totalPrerequisites}`}
                  </td>
                  <td>{candidate.sharedParentCount}</td>
                  <td>{candidate.lowStability ? 'Low' : 'Fine'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
