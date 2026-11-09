# Public release checklist

This repository previously tracked a reusable JWT signing key, database passwords, host addresses, and an automatically provisioned administrator account. **Editing the latest files does not erase those values from Git history.** Do not change the visibility of the existing remote repository until the steps below are complete.

1. Rotate every JWT signing key and database/Redis password used with this project. Revoke old API tokens if any were used. Reset or remove any previously bootstrapped `admin` account with the old `admin123` password. Rotating the JWT key invalidates old tokens.
2. Check all branches, tags, commit messages, and tracked binaries for credentials and personal information. Remove any real user data before publishing.
3. Publish from a new repository with a clean initial commit containing only the reviewed working tree, or rewrite all remote history and coordinate a forced update with every collaborator. Simply deleting a file or creating a new branch on the old remote does not remove reachable history.
4. Recheck the public repository after publication with a secret scanner and inspect every branch and tag. Enable host-side secret scanning where available.
5. Configure a unique `JWT_SECRET` (at least 32 bytes), private database credentials, exact CORS origins, TLS, and protected upload storage for any deployment.

The app is a course project and has not received a full production security audit. Avoid hosting it with real user information until authorization, upload handling, and account recovery have been reviewed.

To report a vulnerability, contact the repository owner privately rather than opening a public issue with an exploit or secret.
