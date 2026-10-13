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
import type { LucideIcon } from 'lucide-react';
import type { ComponentProps } from 'react';
import { useTooltip, type TooltipTriggerProps } from './Tooltip';

type Props = Omit<ComponentProps<'button'>, 'children' | 'aria-label' | 'title' | 'type' | keyof TooltipTriggerProps> & {
  icon: LucideIcon;
  /** The button's accessible name, also shown as its tooltip; required, since the icon alone names nothing. */
  label: string;
  tone?: 'quiet' | 'primary' | 'plain';
  small?: boolean;
  submit?: boolean;
};

export function IconButton({ icon: Icon, label, tone = 'quiet', small = false, submit = false, className, ...rest }: Props) {
  const { trigger, tooltip } = useTooltip(label, false);
  const classes = ['icon-only', tone === 'plain' ? null : tone, small ? 'small' : null, className].filter(Boolean).join(' ');
  return (
    <>
      <button type={submit ? 'submit' : 'button'} className={classes} aria-label={label} {...rest} {...trigger}>
        <Icon size={small ? 16 : 18} aria-hidden="true" />
      </button>
      {tooltip}
    </>
  );
}
