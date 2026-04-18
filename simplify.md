# Simplify: Replace NextActionPopup with UserChoiceDialog

## Goal

Eliminate `NextActionPopup` by reusing `UserChoiceDialog` in the halt/failure path of
`StepExecutionOrchestrator`. Retry semantics differ by call site only — the dialog itself is identical.

---

## Step 1 — Delete `NextActionPopup.java`

---

## Step 2 — Refactor `promptUserAndDispatch` in `StepExecutionOrchestrator`

Replace the `NextActionPopup.displayAndGetUserDecision` call with `UserChoiceDialog.displayAndGetSelection`:

```
matches          = List.of(atomicStep)
allScoredMatches = knowledgeService.findTopRankedWithScores(atomicStep.description(), Set.of(), Set.of())
headerText       = the existing halt/failure message
itemDescription  = atomicStep.description()
effectNodeIds    = Set.of()   // execution-state scoring not needed in this context
recentParentIds  = Set.of()
```

Action mapping (return `null` when the user cancels an action mid-way — caller re-shows dialog):

| `SelectionAction`         | Outcome                                                                                                                                                                                                                   |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `RETRY`                   | `RE_FETCH_AND_RETRY` (retry the atomic step)                                                                                                                                                                              |
| `CANCEL` (empty Optional) | `TERMINATE_EXECUTION`                                                                                                                                                                                                     |
| `EDIT` / `BROWSE`         | call `triggerEditProcedureFlow` on `selectedProcedure`; if saved and `savedId == atomicStep.id()` → `RE_FETCH_AND_RETRY`; if saved and `savedId != atomicStep.id()` → warn + `TERMINATE_EXECUTION`; if not saved → `null` |
| `CREATE`                  | call `triggerNewProcedureFlow`; if created → ingest + `RE_DECOMPOSE_AND_RETRY`; if cancelled → `null`                                                                                                                     |

Remove the inner `while(true)` loop from `promptUserAndDispatch` — it becomes single-shot.

---

## Step 3 — Update `handleHaltDecision`

Add a `null` check: when `promptUserAndDispatch` returns `null` (user cancelled an action),
continue the loop to re-show the dialog.

---

## Step 4 — Update imports in `StepExecutionOrchestrator`

- Remove `NextActionPopup` import.
- Add `UserChoiceDialog` import.

---

## Step 5 — Update `README.md`
