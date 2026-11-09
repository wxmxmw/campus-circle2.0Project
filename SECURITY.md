# Public release checklist

## Google API key exposed in Git history

GitGuardian reported a Google API key in `backend/src/main/resources/application-local.properties` in the history of `wxmxmw/campus-circle2.0Project`. The current working tree no longer contains that key, but a new commit cannot remove it from old public commits.

1. In the Google Cloud project that owns the key, open [Credentials](https://console.cloud.google.com/apis/credentials), rotate or delete the exposed key, and verify the old key no longer works. If a replacement is needed, restrict it to the APIs and callers the application uses. Review recent API usage and billing for unexpected activity.
2. The current backend uses `OPENAI_API_KEY` or `LLM_API_KEY` for optional AI features. Put any replacement credential in `backend/.env` or the deployment platform's protected environment settings; never in a tracked `.properties` file or frontend code. The old Google key is not needed by the current backend.
3. After revocation, resolve the GitGuardian alert as revoked. If you must remove the key from public Git history as well, coordinate a full history rewrite with collaborators and GitHub Support; force-pushing alone may leave cached commits and forks accessible. Do not paste the key into a ticket or chat.
4. To enable the repository's local Google-key commit guard, run `git config core.hooksPath .githooks` once in this clone. This guard checks staged files and supplements repository-host secret scanning.

This repository previously tracked a reusable JWT signing key, database passwords, host addresses, and an automatically provisioned administrator account. **Editing the latest files does not erase those values from Git history.** Treat any values already exposed in a public repository as compromised and complete the steps below.

1. Rotate every JWT signing key and database/Redis password used with this project. Revoke old API tokens if any were used. Reset or remove any previously bootstrapped `admin` account with the old `admin123` password. Rotating the JWT key invalidates old tokens.
2. Check all branches, tags, commit messages, and tracked binaries for credentials and personal information. Remove any real user data before publishing.
3. Publish from a new repository with a clean initial commit containing only the reviewed working tree, or rewrite all remote history and coordinate a forced update with every collaborator. Simply deleting a file or creating a new branch on the old remote does not remove reachable history.
4. Recheck the public repository after publication with a secret scanner and inspect every branch and tag. Enable host-side secret scanning where available.
5. Configure a unique `JWT_SECRET` (at least 32 bytes), private database credentials, exact CORS origins, TLS, and protected upload storage for any deployment.

The app is a course project and has not received a full production security audit. Avoid hosting it with real user information until authorization, upload handling, and account recovery have been reviewed.

To report a vulnerability, contact the repository owner privately rather than opening a public issue with an exploit or secret.
