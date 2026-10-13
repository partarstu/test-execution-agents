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
import { Vector3 } from 'three';
import { describe, expect, it } from 'vitest';
import { framing, stepTowards } from './brainCamera';

describe('framing', () => {
  it('centres on the box and backs off until its largest extent fits the field of view with the margin', () => {
    const { centre, distance } = framing({ x: [0, 200], y: [-50, 50], z: [10, 30] }, 90, 1.5, 10);

    expect(centre.toArray()).toEqual([100, 0, 20]);
    expect(distance).toBeCloseTo(150);
  });

  it('never comes closer than the minimum distance, e.g. for a single node', () => {
    const { centre, distance } = framing({ x: [5, 5], y: [6, 6], z: [7, 7] }, 50, 1.3, 150);

    expect(centre.toArray()).toEqual([5, 6, 7]);
    expect(distance).toBe(150);
  });
});

describe('stepTowards', () => {
  const goal = { centre: new Vector3(100, 0, 0), distance: 50 };

  it('eases the target and the distance towards the goal while keeping the viewing direction', () => {
    const target = new Vector3(0, 0, 0);
    const position = new Vector3(0, 0, 250);

    const arrived = stepTowards(target, position, goal, 0.5, false);

    expect(arrived).toBe(false);
    expect(target.toArray()).toEqual([50, 0, 0]);
    expect(position.toArray()).toEqual([50, 0, 150]);
  });

  it("keeps the operator's distance once they have zoomed and arrives when the target reaches the centre", () => {
    const target = new Vector3(0, 0, 0);
    const position = new Vector3(0, 300, 0);

    const arrived = stepTowards(target, position, goal, 1, true);

    expect(arrived).toBe(true);
    expect(target.toArray()).toEqual([100, 0, 0]);
    expect(position.toArray()).toEqual([100, 300, 0]);
  });

  it('arrives in one step without easing', () => {
    const target = new Vector3(0, 0, 0);
    const position = new Vector3(0, 0, 400);

    expect(stepTowards(target, position, goal, 1, false)).toBe(true);
    expect(position.toArray()).toEqual([100, 0, 50]);
  });
});
