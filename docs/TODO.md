# PikminBee — Issue-based development tracking

Since 2026-11-25, **GitHub Issues are the only active task ledger**. GitHub Projects is the planning and status view once the account grants project access. Until then, use the issue list and issue discussions; do not maintain a second Markdown checklist.

## Start here

- [All open Issues](https://github.com/Naos2w/PikminBee/issues?q=is%3Aissue+is%3Aopen)
- [V4 design review / Epic #140](https://github.com/Naos2w/PikminBee/issues/140): links to each feature, design proposal, wireframe, requirements and acceptance criteria.
- [Repository Projects](https://github.com/Naos2w/PikminBee/projects): intended board; its creation and configuration are not yet verified because the personal CLI account lacks Projects authorization.
- [Branching and CI policy](BRANCHING.md)

## Task workflow

Create or reuse a single Issue per work item. Include source evidence, proposed UI and interaction flow (when applicable), acceptance criteria, dependencies, relevant PRs and actual test results. Review new visual directions in the Issue before implementation. Use `Refs #123` rather than auto-closing keywords for work awaiting device acceptance. Keep design review, implementation, CI, release packaging and physical acceptance distinct.

The planned Projects fields are Status (Backlog / Ready / In Progress / Review / UAT / Done / Blocked), Priority (P0–P3), Type, Platform, Version and Assignee. Never treat a merged PR or automated workflow as physical UAT. A task is closed only when its actual acceptance criteria are satisfied; cancellation and supersession are recorded separately. Do not auto-promote to staging or main.

## Migration map — history/reference only, not a task list

| Previous area | Canonical Issue |
|---|---|
| V4-01–09 design and implementation | #140 epic → #141–#149 |
| V4-10, L4, FAV-5, V3-GATE, U2/U3/U7 and consolidated acceptance | #18 |
| V3-2/3, WEB-5, U1 map/road/route acceptance | #16 |
| R4/U4 retired mode and legacy data preservation | #17 |
| V3-4/5/6, UX-4, U5 visual acceptance | #26 |
| U6 signed upgrade acceptance | #29 |
| FAV-4 future collection management | #150 |

Existing implementation evidence and cancelled scope remain in the immutable historical sources below. All prior open device gates remain open. No new development is authorized merely by this migration.

- [2026-11-24 original active TODO, immutable develop commit](https://github.com/Naos2w/PikminBee/blob/dae6911b15d2efbec17fcb57d4e79a0ee35e1045/docs/TODO.md)
- [2026-11-25 complete V4 draft before migration, immutable commit](https://github.com/Naos2w/PikminBee/blob/bfacc39bcf24800d2e488a9e3ad45e0bab234c09/docs/TODO.md)
- [Earlier consolidated source audit and milestones](https://github.com/Naos2w/PikminBee/blob/4f4ec6107757f14810ef53e23ec1d5e9e3b3bb59/docs/TODO.md)

This file is a navigation and historical reference only. Update the actual Issue, not this document, for task progress, design decisions, acceptance, and closure.
