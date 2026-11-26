# PikminBee — Issue-based development tracking

Since 2026-11-25, **GitHub Issues are the only active task ledger**. GitHub Projects is the planning and status view; the personal-account MCP connection and Project #1 have been verified. Do not maintain a second Markdown checklist.

## Start here

- [All open Issues](https://github.com/Naos2w/PikminBee/issues?q=is%3Aissue+is%3Aopen)
- **Current development direction:** [Commercial Readiness plan](COMMERCIAL-READINESS.md), governed by [release Epic #168](https://github.com/Naos2w/PikminBee/issues/168). Start with #169, then #171 and #170. Do not promote to main or publish 1.0.0 while release gates remain open.
- [V4 design review / Epic #140](https://github.com/Naos2w/PikminBee/issues/140): links to existing features, design proposals, wireframes, requirements and acceptance criteria.
- [PikminBee Development — Project #1](https://github.com/users/Naos2w/projects/1): verified planning board (32 tracked items verified on 2026-11-26; live count is authoritative). Views: All Tasks, Development Board, V4 Design Review, UAT Acceptance and Future Backlog. Access Projects through the independent `github-personal` MCP (verified identity `Naos2w`), not by switching the shared GitHub CLI account.
- [Branching and CI policy](BRANCHING.md)

## Task workflow

Create or reuse a single Issue per work item. Include source evidence, proposed UI and interaction flow (when applicable), acceptance criteria, dependencies, relevant PRs and actual test results. Review new visual directions in the Issue before implementation. Use `Refs #123` rather than auto-closing keywords for work awaiting device acceptance. Keep design review, implementation, CI, release packaging and physical acceptance distinct.

Current board fields include Status (Todo / In Progress / Done), Labels, Assignees, Parent issue, Sub-issues progress, Linked pull requests and Milestone. V4 design review and physical UAT are separate filtered views. Expanded Status options (Backlog / Ready / Review / UAT / Blocked) and dedicated Priority, Platform and Version fields are planned but not yet configured: the current MCP only exposes item/value updates, view management and iteration-field creation. Existing issue metadata remains authoritative for those distinctions. Never treat a merged PR or automated workflow as physical UAT. A task is closed only when its actual acceptance criteria are satisfied; cancellation and supersession are recorded separately. Do not auto-promote to staging or main.

## Migration map — history/reference only, not a task list

| Previous area | Canonical Issue |
|---|---|
| V4-01–09 design and implementation | #140 epic → #141–#149 |
| V4-10, L4, FAV-5, V3-GATE, U2/U3/U7 and consolidated acceptance | #18 |
| V3-2/3, WEB-5, U1 map/road/route acceptance | #16 |
| R4/U4 retired mode and legacy data preservation | #17 |
| V3-4/5/6, UX-4, U5 visual acceptance | #26 |
| U6 signed upgrade acceptance | #179 (replacement Issue; original deleted) |
| FAV-4 future collection management | #150 |
| Commercial release and operations | #168 → #169–#178; see [release plan](COMMERCIAL-READINESS.md) |

Existing implementation evidence and cancelled scope remain in the immutable historical sources below. All prior open device gates remain open. No new development is authorized merely by this migration.

- [2026-11-24 original active TODO, immutable develop commit](https://github.com/Naos2w/PikminBee/blob/dae6911b15d2efbec17fcb57d4e79a0ee35e1045/docs/TODO.md)
- [2026-11-25 complete V4 draft before migration, immutable commit](https://github.com/Naos2w/PikminBee/blob/bfacc39bcf24800d2e488a9e3ad45e0bab234c09/docs/TODO.md)
- [Earlier consolidated source audit and milestones](https://github.com/Naos2w/PikminBee/blob/4f4ec6107757f14810ef53e23ec1d5e9e3b3bb59/docs/TODO.md)

This file is a navigation and historical reference only. Update the actual Issue, not this document, for task progress, design decisions, acceptance, and closure.
