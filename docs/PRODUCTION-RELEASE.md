# Production release (1.0.0 and later)

## Channel separation

| Channel | Application ID | Version example | Build |
| --- | --- | --- | --- |
| DEV | com.naos.pikminbee | 0.27.2-dev | debug, universal by default |
| UAT | com.naos.pikminbee.uat | 0.27.1 | fixed UAT signer, ARM64 by default |
| PROD | com.naos.pikminbee.prod | 1.0.0 | non-debuggable release, independent production signer, ARM64 by default |

These packages coexist; Android does not automatically migrate app-private data between them. Keep UAT installed and export important saved data using its available export functions before changing packages. Do not uninstall or clear data to resolve signing conflicts. OS permissions must be granted separately to the new package. A release approval is not proof of every POCO acceptance test; retain the open UAT issues.

Production code is independently versioned, using `major * 1000000 + minor * 1000 + patch`. The first code is 1000000. Advance the committed production version through `python scripts/bump_version.py production --set-version 1.0.1`, never backwards. Do not change the package or signing certificate after distribution. R8 is deliberately disabled for the first release; the ABI filter already removes unused native binaries.

## One-time owner-controlled signing setup

Use a dedicated production PKCS12 signing keystore with alias `pikminbee-release`, independent from all debug and UAT keys. The build currently expects the PKCS12 store/key password to be the same. Securely back up the keystore and password in owner-controlled storage outside the repository and keep the backup recoverable. Never post credentials in chat, issues, logs or commits.

In GitHub repository Settings > Environments, create/select `production` and restrict deployment branches to `main`. Add these environment secrets:
- `PIKMINBEE_RELEASE_KEYSTORE_BASE64`: base64 encoding of the production PKCS12 file.
- `PIKMINBEE_RELEASE_SIGN_PASSWORD`: the associated signing password.

Add environment variable `PIKMINBEE_RELEASE_CERT_SHA256` with the independently verified SHA-256 of the public signing certificate (64 hex digits without colons). Keep this pin fixed after release. A missing credential, UAT certificate pin, wrong certificate, incorrect APK identity or unexpected ABI aborts packaging; no debug-key fallback.

## Build and publish

1. Approve the exact source on staging, then merge its CI-verified PR into main using a merge commit. The Android workflow also checks the unsigned production variant on the main PR.
2. On GitHub Actions select **production-release**, branch **main**, and the committed version (e.g. `1.0.0`). Select ARM64 for the target phone or universal for broader compatibility. Set `publish_release=true` only to publish an official GitHub Release.
3. The workflow validates the committed version, runs tests/lint, builds the release variant, verifies the actual packaged manifest and certificate, measures the APK and checks its exact ABI set. The verified file, JSON report and checksums are uploaded. Publication uses the exact verified main commit and never overwrites an existing tag.
4. Install the signed APK manually when desired. Verify permissions, keyless map, settings, data import/export, LAN pairing/consent and device behavior; no automatic installation.

An unsigned local production configuration check can be run using the environment flag `PIKMINBEE_PRODUCTION=true` with `-PpikminbeeUnsignedProductionCheck=true` and tasks `:app:compileReleaseKotlin :app:lintRelease :app:assembleRelease`. That output is **not** an official installable APK and must never be published as one.
