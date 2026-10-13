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

export interface Box {
  x: [number, number];
  y: [number, number];
  z: [number, number];
}

export interface Framing {
  centre: Vector3;
  distance: number;
}

const ARRIVAL_TOLERANCE = 0.5;

/** The orbit centre and camera distance from which the box fills a view of the given vertical field, with `margin` room around it. */
export function framing(box: Box, fovDegrees: number, margin: number, minDistance: number): Framing {
  const centre = new Vector3((box.x[0] + box.x[1]) / 2, (box.y[0] + box.y[1]) / 2, (box.z[0] + box.z[1]) / 2);
  const radius = Math.max(box.x[1] - box.x[0], box.y[1] - box.y[0], box.z[1] - box.z[0]) / 2;
  return { centre, distance: Math.max(minDistance, (radius / Math.tan((fovDegrees * Math.PI) / 360)) * margin) };
}

/**
 * Moves the orbit target and the camera one eased frame towards the goal, keeping the viewing direction and, once the operator has
 * zoomed, their distance; returns whether the goal is reached.
 */
export function stepTowards(target: Vector3, position: Vector3, goal: Framing, easing: number, keepDistance: boolean): boolean {
  const offset = position.clone().sub(target);
  const current = offset.length();
  const distance = keepDistance ? current : current + (goal.distance - current) * easing;
  target.lerp(goal.centre, easing);
  position.copy(target).add(offset.setLength(distance));
  return target.distanceTo(goal.centre) < ARRIVAL_TOLERANCE && (keepDistance || Math.abs(distance - goal.distance) < ARRIVAL_TOLERANCE);
}
