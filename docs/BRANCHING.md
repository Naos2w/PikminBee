# Branching / CI policy

Three long-lived branches, because UAT is explicitly a separate release gate:

## Issue-first planning and design review

- GitHub Issues are the canonical task ledger; GitHub Projects is the status/planning view when authorized. See [task navigation](TODO.md) and the [V4 design review epic #140](https://github.com/Naos2w/PikminBee/issues/140).
- Before opening a development branch, read the existing Issue, its latest review decisions, acceptance criteria and linked PRs. Do not recreate completed work or maintain a parallel TODO list.
- Record visual/function assessment, proposed UI, mockups, dependencies and test evidence in the corresponding Issue. New visual direction requires user review before implementation; no private coordinates, account details, PINs or unauthorized screenshots in public artifacts.
- Reference the Issue in the PR; avoid auto-closing a task merely on merge when physical UAT is required. A merged implementation, fixed-signer APK build and verified device acceptance are separate gates.
- Status proposal for Projects: Backlog, Ready, In Progress, Review, UAT, Done, Blocked. Close an Issue only after its stated acceptance criteria actually pass; preserve cancelled/superseded history. No automatic APK, staging or production promotion.

## DEV / UAT semantic versions

- **First UAT:** `0.1.1` (display version exactly, without an `-uat` suffix; Android app label remains PikminBee UAT).
- **Each DEV progression:** increment only the third number: `0.1.2-dev` → `0.1.3-dev` → `0.1.4-dev`. The `-dev` suffix identifies the local DEV package; the numeric version is `0.1.2`, `0.1.3`, etc.
- **Each new UAT cycle:** increment the second number and reset the third to 1: `0.1.1` → `0.2.1` → `0.3.1`. The next DEV number is automatically reset to `0.2.2-dev`, `0.3.2-dev`, etc.
- Edit `gradle.properties` only through `python scripts/bump_version.py dev` for a new DEV revision, or `python scripts/bump_version.py uat` **when approving the next UAT cycle**. Run `python scripts/bump_version.py check` to inspect/validate without changing files. Commit the version change through the ordinary PR workflow; **merging a PR does not itself publish or promote an APK**.
- DEV `versionCode` is encoded from `X.Y.Z`. Signed UAT `versionCode` is generated from the monotonically increasing GitHub Actions workflow run number, **must be higher than the previously distributed 1051**, and is deliberately separate from the visible semantic version. This allows upgrading the existing UAT installation even if its older displayed name was `0.3.0-dev-uat`.
- Do not automatically bump semantic versions on routine source verification or CI reruns; only increase them when cutting a new DEV or UAT revision. Rebuilding the same UAT is allowed with an increased install `versionCode` while keeping the same visible `X.Y.Z`.


| Base branch | Allowed PR head | Merge method | Intended use |
|---|---|---|---|
| `develop` | `feature/*`, `fix/*`, `chore/*` | Squash only | DEV |
| `staging` | `develop` | Merge commit only | UAT |
| `main` | `staging` | Merge commit only | Production |

**GitHub settings after the remote repository is created**
- Allow *Squash merging* and *Merge commits*. Disable *Rebase merging*.
- Ruleset for `develop`: require PR, require status check `android / verify` **after the PR becomes ready**, require squash; require linear history. Draft PRs intentionally do not run this check.
- Ruleset for `staging`: require PR, require status check, require merge commit.
- Ruleset for `main`: require PR, require status check, require merge commit.
- One CI workflow also rejects an incorrect source → target PR pairing.
- Release tags `vX.Y.Z` only from `main`, manually after UAT approval.
- If private-repository plan entitlements prevent enforcement of branch rulesets, keep the CI source-branch guard and make the PR merge-type check manual until plan support is enabled.

**Hotfixes** begin with `fix/* → develop`, then promote through staging → main, keeping the branch ancestry straightforward.

**CI (cost control):** One Gradle job, one JVM version, no matrix, no deploy server, no scheduled runs. Draft PRs skip the Android runner. When a PR is marked ready for review, Gradle runs unit tests and lint (without packaging APK). Synchronised updates while a PR is ready trigger verification; concurrency cancels superseded runs. `workflow_dispatch` allows explicit source verification; only if `package_apk=true` does it build and upload a UAT APK (3-day retention). Do not mark Draft PRs ready until a feature is reviewed locally. GitHub Actions is a final PR gate, not the development feedback loop.

**APK ABI and size:** ordinary DEV builds remain universal. Signed UAT defaults to arm64-v8a (confirmed target POCO ABI); manual `apk_abi` selection permits a universal or specific alternative build. Local unsigned/re-signed DEV-only measurements can use `-PpikminbeeApkAbi=arm64-v8a`. The packaging workflow audits the actual APK ZIP entries, exact ABI set, bytes and SHA-256 before upload. The fixed UAT signer, app ID, versionCode progression and default semantic-version filename are unchanged. Non-default ABI files include an ABI suffix. R8 is not enabled by this first packaging change; evaluate optimized builds separately and retain map/JNI/permission regressions and real-device acceptance as gates.

Do not put Google API keys, production keystores or accounts in GitHub Actions YAML or source control.
