# Branching / CI policy

Three long-lived branches, because UAT is explicitly a separate release gate:

| Base branch | Allowed PR head | Merge method | Intended use |
|---|---|---|---|
| `develop` | `feature/*`, `fix/*`, `chore/*` | Squash only | DEV |
| `staging` | `develop` | Merge commit only | UAT |
| `main` | `staging` | Merge commit only | Production |

**GitHub settings after the remote repository is created**
- Allow *Squash merging* and *Merge commits*. Disable *Rebase merging*.
- Ruleset for `develop`: require PR, require status check `android / verify`, require squash; require linear history.
- Ruleset for `staging`: require PR, require status check, require merge commit.
- Ruleset for `main`: require PR, require status check, require merge commit.
- One CI workflow also rejects an incorrect source → target PR pairing.
- Release tags `vX.Y.Z` only from `main`, manually after UAT approval.
- If private-repository plan entitlements prevent enforcement of branch rulesets, keep the CI source-branch guard and make the PR merge-type check manual until plan support is enabled.

**Hotfixes** begin with `fix/* → develop`, then promote through staging → main, keeping the branch ancestry straightforward.

**CI:** One Gradle job, one JVM version, no matrix, no deploy server, no scheduled runs. Run tests, lint and debug build on protected PRs. Upload debug APK only with `workflow_dispatch`.

Do not put Google API keys, production keystores or accounts in GitHub Actions YAML or source control.
