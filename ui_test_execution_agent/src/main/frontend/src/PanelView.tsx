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
import { Activity as ActivityIcon, HeartPulse, LogOut, Search, Waypoints, WifiOff } from 'lucide-react';
import { Activity, lazy, startTransition, Suspense, useEffect, useState, ViewTransition, type ReactNode } from 'react';
import { flushSync } from 'react-dom';
import { logout } from './api';
import { promptAnnouncement, runAnnouncement, storedNotify, useAttention } from './attention';
import { COPY_BLOCKED, copyText } from './clipboard';
import { CommandPalette } from './CommandPalette';
import { panelCommands } from './commands';
import { DisplacedNotice } from './DisplacedNotice';
import { GraphHealth } from './health/GraphHealth';
import { ErrorBoundary } from './kit/ErrorBoundary';
import { SignalMark } from './kit/SignalMark';
import { TabList, tabPanelProps, type TabItem } from './kit/Tabs';
import type { FocusRequest, KnowledgeView, ViewRequest } from './knowledge/KnowledgeTab';
import { PreferencesMenu } from './PreferencesMenu';
import { PROMPT_ANCHOR_ID } from './prompts/anchor';
import { PromptStrip } from './prompts/PromptStrip';
import { RunChip } from './RunProgress';
import { LOGS_TOGGLE_ID, storedLogsOpen, storeLogsOpen, unseenProblems } from './logsPanel';
import { RunTab, type RunView } from './RunTab';
import { ShortcutsSheet } from './ShortcutsSheet';
import { MOD_KEY, useShortcuts } from './shortcuts';
import { followPrompt, INITIAL_TABS, promptToFollow, selectTab, type Tab } from './tabs';
import type { Displaced, SessionInfo } from './types';
import { useEvents } from './useEvents';
import './PanelView.css';

// Loaded on demand, so the Run tab never downloads the graph libraries
const KnowledgeTab = lazy(() => import('./knowledge/KnowledgeTab').then((module) => ({ default: module.KnowledgeTab })));

const TAB_PREFIX = 'view';
const TOAST_MILLIS = 4_000;
const MAIN_ID = 'panel-main';

interface Props {
  info: SessionInfo;
  onSignedOut: (notice: Displaced | null) => void;
}

export function PanelView({ info, onSignedOut }: Props) {
  const [tabs, setTabs] = useState(INITIAL_TABS);
  const [healthOpened, setHealthOpened] = useState(false);
  const [knowledgeOpened, setKnowledgeOpened] = useState(false);
  const [knowledgeFocus, setKnowledgeFocus] = useState<FocusRequest | null>(null);
  const [knowledgeView, setKnowledgeView] = useState<ViewRequest | null>(null);
  const [otherTab, setOtherTab] = useState<Displaced | null>(null);
  const [notify, setNotify] = useState(storedNotify);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [runView, setRunView] = useState<RunView>('step');
  const [logsOpen, setLogsOpen] = useState(storedLogsOpen);
  // The number of the first log line not seen yet: every line counts as seen while the panel is open
  const [logsSeenUpTo, setLogsSeenUpTo] = useState(0);
  const { state, streamStatus } = useEvents(otherTab === null, {
    onDisplaced: (displaced) => (displaced.reason === 'other-tab' ? setOtherTab(displaced) : onSignedOut(displaced)),
    onSignedOut,
  });
  useAttention(state, notify);

  useEffect(() => {
    if (toast === null) {
      return;
    }
    const timer = setTimeout(() => setToast(null), TOAST_MILLIS);
    return () => clearTimeout(timer);
  }, [toast]);

  // A new prompt waiting for an answer brings the Run tab forward, since its questions cannot be answered on a hidden tab
  const followed = followPrompt(tabs, promptToFollow(state.prompt));
  if (followed !== tabs) {
    setTabs(followed);
  }
  const tab = followed.selected;

  const select = (next: Tab) => {
    // A transition, so the views cross-fade
    startTransition(() => setTabs(selectTab(followed, next)));
    if (next === 'knowledge') {
      setKnowledgeOpened(true);
    }
    if (next === 'health') {
      setHealthOpened(true);
    }
  };

  const openInKnowledge = (kind: FocusRequest['kind'], id: string) => {
    setKnowledgeFocus({ kind, id, seq: (knowledgeFocus?.seq ?? 0) + 1 });
    select('knowledge');
  };

  const showKnowledgeView = (view: KnowledgeView) => {
    setKnowledgeView({ view, seq: (knowledgeView?.seq ?? 0) + 1 });
    select('knowledge');
  };

  // The prompt is only focusable once the Run tab is visible, so the switch is committed before the focus moves
  const goToPrompt = state.prompt === null ? null : () => {
    flushSync(() => setTabs(selectTab(followed, 'run')));
    document.getElementById(PROMPT_ANCHOR_ID)?.focus();
  };

  const signOut = async () => {
    await logout();
    onSignedOut(null);
  };

  const copyLog = state.logTail.length === 0 ? null : () => {
    void copyText(state.logTail.join('\n')).then((copied) => setToast(copied ? 'Copied the log' : COPY_BLOCKED));
  };

  const setLogs = (open: boolean) => {
    setLogsOpen(open);
    storeLogsOpen(open);
    setLogsSeenUpTo(state.logOffset + state.logTail.length);
  };

  // The logs live on the Run tab, so opening them from another tab brings it forward
  const toggleLogs = () => {
    if (!logsOpen && tab !== 'run') {
      select('run');
    }
    setLogs(!logsOpen);
  };

  const closeLogs = () => {
    flushSync(() => setLogs(false));
    document.getElementById(LOGS_TOGGLE_ID)?.focus();
  };

  const toggleRunView = () => setRunView(runView === 'live' ? 'step' : 'live');

  const toggleLiveView = !info.liveView || otherTab !== null ? null : () => {
    if (tab !== 'run') {
      select('run');
    }
    toggleRunView();
  };

  const commands = panelCommands({
    select,
    showKnowledgeView,
    goToPrompt,
    showShortcuts: () => setShortcutsOpen(true),
    copyLog,
    logsOpen,
    toggleLogs,
    liveViewLarge: runView === 'live',
    toggleLiveView,
    signOut: () => void signOut(),
  });

  useShortcuts({
    openPalette: () => setPaletteOpen(true), showShortcuts: () => setShortcutsOpen(true), select, goToPrompt, toggleLogs, toggleLiveView,
  });

  const tabItems: TabItem<Tab>[] = [
    {
      id: 'run',
      label: (
        <>
          <ActivityIcon size={18} />
          <span className="nav-label">Run</span>
          {state.prompt && <span className="tab-waiting"><span className="tab-waiting-text">Waiting for you</span></span>}
        </>
      ),
    },
    { id: 'health', label: <><HeartPulse size={18} /><span className="nav-label">Health</span></> },
    { id: 'knowledge', label: <><Waypoints size={18} /><span className="nav-label">Knowledge</span></> },
  ];

  return (
    <div className="panel">
      <a className="skip-link" href={`#${MAIN_ID}`}>Skip to the content</a>
      <header className="appbar">
        <div className="appbar-brand">
          <SignalMark waiting={state.prompt !== null} />
          <h1>Operator panel</h1>
        </div>
        <TabList label="Panel views" idPrefix={TAB_PREFIX} tabs={tabItems} selected={tab} onSelect={select} className="appbar-nav" />
        <div className="appbar-tools">
          {tab !== 'run' && <RunChip runStatus={state.runStatus} items={state.items} onOpen={() => select('run')} />}
          {streamStatus === 'reconnecting' && otherTab === null && (
            <span className="appbar-stream" role="status">
              <WifiOff size={16} />
              <span className="appbar-label">Reconnecting…</span>
            </span>
          )}
          <button type="button" className="quiet command-trigger" aria-keyshortcuts="Control+K Meta+K" onClick={() => setPaletteOpen(true)}>
            <Search size={18} />
            <span className="appbar-label">Search and jump</span>
            <kbd className="kbd command-trigger-kbd" aria-hidden="true">{MOD_KEY} K</kbd>
          </button>
          <PreferencesMenu notify={notify} onNotifyChange={setNotify} />
          <button type="button" className="quiet" onClick={() => void signOut()}>
            <LogOut size={18} />
            <span className="appbar-label">Log out</span>
          </button>
        </div>
      </header>
      {otherTab && <DisplacedNotice displaced={otherTab} onUseHere={() => setOtherTab(null)} />}
      {state.prompt && tab !== 'run' && goToPrompt && <PromptStrip prompt={state.prompt} onAnswer={goToPrompt} />}
      <main id={MAIN_ID} className="panel-main" tabIndex={-1}>
        <Activity mode={tab === 'run' ? 'visible' : 'hidden'}>
          <TabFade>
            <div {...tabPanelProps(TAB_PREFIX, 'run')} className="tab-panel">
              <ErrorBoundary>
                <RunTab state={state} info={info} liveViewAllowed={otherTab === null} view={runView} onToggleView={toggleRunView}
                  logs={{
                    open: logsOpen,
                    unseen: logsOpen ? 0 : unseenProblems(state.logTail, state.logOffset, logsSeenUpTo),
                    onToggle: toggleLogs,
                    onClose: closeLogs,
                  }}
                  onUnauthorized={() => onSignedOut(null)} />
              </ErrorBoundary>
            </div>
          </TabFade>
        </Activity>
        {healthOpened && (
          <Activity mode={tab === 'health' ? 'visible' : 'hidden'}>
            <TabFade>
              <div {...tabPanelProps(TAB_PREFIX, 'health')} className="tab-panel tab-panel-narrow">
                <ErrorBoundary>
                  <GraphHealth onUnauthorized={() => onSignedOut(null)} onOpenNode={openInKnowledge} />
                </ErrorBoundary>
              </div>
            </TabFade>
          </Activity>
        )}
        {knowledgeOpened && (
          <Activity mode={tab === 'knowledge' ? 'visible' : 'hidden'}>
            <TabFade>
              <div {...tabPanelProps(TAB_PREFIX, 'knowledge')} className="tab-panel tab-panel-wide">
                <ErrorBoundary>
                  <Suspense fallback={<p className="loading-note">Loading the knowledge views…</p>}>
                    <KnowledgeTab run={state} requestedFocus={knowledgeFocus} requestedView={knowledgeView} onUnauthorized={() => onSignedOut(null)} />
                  </Suspense>
                </ErrorBoundary>
              </div>
            </TabFade>
          </Activity>
        )}
      </main>
      <CommandPalette open={paletteOpen} commands={commands} onClose={() => setPaletteOpen(false)} />
      <ShortcutsSheet open={shortcutsOpen} onClose={() => setShortcutsOpen(false)} />
      <p className="toast" role="status">{toast}</p>
      <div className="visually-hidden" aria-live="assertive">{promptAnnouncement(state.prompt)}</div>
      <div className="visually-hidden" aria-live="polite">{runAnnouncement(state)}</div>
    </div>
  );
}

/** Cross-fades a view in and out as the tabs switch, without animating the changes inside it. */
function TabFade({ children }: { children: ReactNode }) {
  return <ViewTransition default="none" enter="auto" exit="auto">{children}</ViewTransition>;
}
