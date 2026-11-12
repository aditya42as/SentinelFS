# Publishing SentinelFS on GitHub

## One-time setup
1. Create the repository on GitHub (or use the one you already have) and push your code:
   ```bash
   git remote -v                 # shows your existing origin, if any
   git add .
   git commit -m "Add desktop UI, real-time monitoring and release workflow"
   git push origin main
   ```
2. On GitHub open **Settings → Actions → General → Workflow permissions** and choose **Read and write permissions** (the release job needs this to attach files).
3. Open the **Actions** tab — the *CI* workflow should be running and turn green.

## Every release
```bash
git tag v1.0.0
git push origin v1.0.0
```
The *Release* workflow builds the Windows / macOS / Linux downloads and the plain jar, then publishes them at
`https://github.com/<you>/<repo>/releases`. Share that link — it is your download page.

Tags like `v1.1.0-beta` are accepted too; tick "pre-release" on the release page if you want.

## If a release fails
- Open **Actions → Release → the failed run** and read the red step.
- Delete the tag and retry:
  ```bash
  git tag -d v1.0.0 && git push origin :refs/tags/v1.0.0
  ```
  then fix and tag again.
