require('dotenv').config();
const express = require('express');
const multer = require('multer');
const AdmZip = require('adm-zip');
const simpleGit = require('simple-git');
const fetch = require('node-fetch');
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');

const app = express();

const {
  GITHUB_TOKEN,
  GITHUB_REPO,          // "owner/repo"
  GITHUB_BRANCH = 'main', // base branch the workflow files live on
  WORKFLOW_FILE = 'build.yml',
  PORT = 3000,
  MAX_UPLOAD_MB = 200,
  JOB_TTL_MINUTES = 60   // how long a finished job (and its branch) stays around
} = process.env;

if (!GITHUB_TOKEN || !GITHUB_REPO) {
  console.error('Missing GITHUB_TOKEN or GITHUB_REPO in .env — see .env.example');
  process.exit(1);
}

const upload = multer({
  dest: os.tmpdir(),
  limits: { fileSize: Number(MAX_UPLOAD_MB) * 1024 * 1024 }
});

const [OWNER, REPO] = GITHUB_REPO.split('/');
const API = 'https://api.github.com';
const authHeaders = {
  Authorization: `token ${GITHUB_TOKEN}`,
  Accept: 'application/vnd.github+json',
  'User-Agent': 'apk-builder-app'
};

app.use(express.static(path.join(__dirname, 'public')));
app.use(express.json());

// The wrapped Android app loads index.html from file:///android_asset/,
// which puts every request to this server on a different origin (the
// WebView sends "Origin: null"). Without these headers the browser engine
// silently blocks the response from being read -- the request actually
// succeeds server-side, but the app sees it as "Could not reach the
// server". This is not needed for the plain website (same-origin there),
// only for the packaged app.
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

// In-memory job store (fine for a single-instance demo; use a DB/queue for real traffic).
// Every job gets its own git branch, so two people building at the same time
// never overwrite each other's project files or pick up each other's run.
const jobs = {}; // jobId -> { status, message, branch, runId, artifactId, artifactName, createdAt }

function newJobId() {
  return Date.now().toString(36) + crypto.randomBytes(4).toString('hex');
}

function setJob(jobId, patch) {
  jobs[jobId] = { ...jobs[jobId], ...patch, updatedAt: Date.now() };
}

// Bump this any time buildFallbackWorkflowYaml()'s actual steps change
// (new fix, pinned tool version, etc). ensureBaseBranchWorkflow() compares
// this against the version marker already committed on the base branch and
// re-pushes when they differ — that's what makes a fix like pinning
// gradle-version actually reach already-existing repos instead of only
// applying to brand new ones.
const FALLBACK_WORKFLOW_VERSION = 2;

// Fallback workflow used when neither the uploaded zip NOR the base branch
// has a .github/workflows/*.yml file. This is the "make any zip work" net:
// it doesn't assume the uploaded project is CI-ready. At run time it:
//   - finds the Gradle project wherever it landed (any depth, any name of
//     top-level folder), instead of trusting a fixed path,
//   - deletes any committed local.properties, since those almost always
//     hardcode a developer's local Android SDK path (e.g. a Mac/Windows
//     path) which doesn't exist on the runner and would otherwise break
//     the build even though the project itself is fine,
//   - fixes the Gradle wrapper if it's there but broken (CRLF line endings
//     from a Windows zip, or missing the exec bit — both are extremely
//     common causes of a project that "should" build but 422s/fails),
//   - pins a Gradle version compatible with the AGP version used here,
//     instead of trusting whatever the runner defaults to (that drifts
//     forward over time and silently breaks builds),
//   - falls back to a runner-installed Gradle if there's no usable wrapper
//     at all, instead of just failing.
// This single workflow is regenerated per job (see buildFallbackWorkflowYaml)
// so it can be biased toward whatever that job's project actually contains.
function buildFallbackWorkflowYaml() {
  return `# apk-builder fallback workflow — version: ${FALLBACK_WORKFLOW_VERSION}
# Auto-generated. Edit buildFallbackWorkflowYaml() in server.js, not this
# file directly — direct edits get overwritten the next time the version
# above is bumped and this gets re-synced to the base branch.
name: Build APK

on:
  push:
    branches: [ "main" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          # Pinned rather than left to the runner default: AGP 8.4.0 (used by
          # both the auto-detected fallback and our generated WebView-wrapper
          # projects) relies on Gradle APIs that get removed in newer Gradle
          # releases (e.g. DependencyHandler.module(...)), and GitHub's
          # runner-default Gradle version drifts forward over time. Pinning
          # here is what actually fixes "...DependencyHandler.module(...)"
          # style failures, since they're a version-compatibility issue, not
          # a problem with the project itself.
          gradle-version: '8.7'

      - name: Locate Android project
        id: locate
        run: |
          MATCH=$(find "$GITHUB_WORKSPACE" \\
            -path '*/.git' -prune -o \\
            -path '*/__MACOSX' -prune -o \\
            -path '*/node_modules' -prune -o \\
            \\( -name settings.gradle -o -name settings.gradle.kts \\) -print | head -n 1)
          if [ -z "$MATCH" ]; then
            echo "::error::No settings.gradle or settings.gradle.kts found anywhere in the uploaded project."
            exit 1
          fi
          PROJECT_DIR=$(dirname "$MATCH")
          echo "Using Gradle project at: $PROJECT_DIR"
          echo "dir=$PROJECT_DIR" >> "$GITHUB_OUTPUT"

      - name: Normalize the project for CI
        working-directory: \${{ steps.locate.outputs.dir }}
        run: |
          # A committed local.properties almost always points at a developer's
          # own machine (sdk.dir=/Users/xxx/Library/Android/sdk) and will make
          # the build fail to find the SDK on the runner even though the
          # project itself is fine. setup-android already exports ANDROID_HOME,
          # so it's safe to drop.
          rm -f local.properties

          # If a wrapper script exists, make sure it will actually run:
          # strip Windows CRLF line endings (breaks the '#!/usr/bin/env sh'
          # shebang on Linux) and set the executable bit, which zip uploads
          # frequently lose.
          if [ -f "./gradlew" ]; then
            sed -i 's/\\r$//' ./gradlew
            chmod +x ./gradlew
          fi

      - name: Build debug APK
        working-directory: \${{ steps.locate.outputs.dir }}
        run: |
          if [ -x "./gradlew" ] && [ -f "./gradle/wrapper/gradle-wrapper.properties" ]; then
            echo "Building with the project's own Gradle wrapper"
            ./gradlew assembleDebug --no-daemon
          else
            echo "No usable Gradle wrapper found — building with the runner's Gradle instead"
            gradle assembleDebug --no-daemon
          fi

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk-\${{ github.run_id }}
          path: \${{ steps.locate.outputs.dir }}/**/build/outputs/apk/debug/*.apk
          if-no-files-found: error
`;
}

// Directories that are never the real project and should be skipped both
// when searching for it and when copying files into the build branch.
const IGNORED_DIR_NAMES = new Set(['.git', '__MACOSX', 'node_modules', '.gradle', '.idea', 'build']);

// Walks the extracted zip looking for every directory that contains
// settings.gradle or settings.gradle.kts — the actual root of a Gradle
// project can be at the top level, one level deep inside a wrapper folder
// (the common case), or buried further in if someone zipped a whole
// workspace. Rather than guess based on "is there exactly one top-level
// folder", this finds every real candidate and picks the shallowest one,
// so almost any zip shape is handled the same way GitHub Actions itself
// would locate it.
function findGradleProjectRoots(dir, depth = 0, maxDepth = 8) {
  const found = [];
  if (depth > maxDepth) return found;

  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return found;
  }

  const hasSettings = entries.some(
    (e) => e.isFile() && (e.name === 'settings.gradle' || e.name === 'settings.gradle.kts')
  );
  if (hasSettings) found.push({ dir, depth });

  for (const e of entries) {
    if (!e.isDirectory() || IGNORED_DIR_NAMES.has(e.name)) continue;
    found.push(...findGradleProjectRoots(path.join(dir, e.name), depth + 1, maxDepth));
  }
  return found;
}

// Picks the best root among candidates: shallowest wins (a settings.gradle
// closer to the top of the zip is virtually always the intended project;
// deeper matches are usually included sample/library sub-projects).
function pickProjectRoot(extractDir) {
  const candidates = findGradleProjectRoots(extractDir);
  if (candidates.length === 0) return { root: null, ambiguous: false };
  candidates.sort((a, b) => a.depth - b.depth);
  const ambiguous = candidates.length > 1 && candidates[0].depth === candidates[1].depth;
  return { root: candidates[0].dir, ambiguous };
}

// Best-effort fixes for the most common reasons a real Android/Gradle
// project fails to build once it lands on a CI runner, even though it
// builds fine on the developer's own machine:
//   - a committed local.properties hardcodes that developer's SDK path
//   - the gradlew wrapper lost its executable bit or has CRLF line endings
//     (both very common after zipping on Windows or via some zip tools)
// Doing this server-side means it's fixed even if a project brings its own
// custom workflow that doesn't already handle these.
//
// Returns an array of human-readable strings describing what it changed, so
// the caller can surface "auto-fixed: ..." messages to the user instead of
// silently patching things (or, previously, just failing the build).
function normalizeProjectForCI(projectRoot) {
  const fixes = [];

  const localProps = path.join(projectRoot, 'local.properties');
  if (fs.existsSync(localProps)) {
    fs.rmSync(localProps, { force: true });
    fixes.push('Removed committed local.properties (hardcodes a developer machine\'s SDK path)');
  }

  const gradlew = path.join(projectRoot, 'gradlew');
  if (fs.existsSync(gradlew)) {
    const content = fs.readFileSync(gradlew, 'utf8');
    if (content.includes('\r')) {
      fs.writeFileSync(gradlew, content.replace(/\r\n/g, '\n'), 'utf8');
      fixes.push('Fixed gradlew line endings (CRLF -> LF)');
    }
    fs.chmodSync(gradlew, 0o755);
  }

  if (ensureGradleProperties(projectRoot)) {
    fixes.push('Added missing AndroidX properties to gradle.properties (android.useAndroidX / android.enableJetifier)');
  }

  if (fixRepositoryConflict(projectRoot)) {
    fixes.push('Removed a root build.gradle repositories block that conflicted with settings.gradle\'s centralized repository management');
  }

  return fixes;
}

// Many real-world Gradle projects use AndroidX libraries (anything under
// androidx.*, including appcompat, core-ktx, etc.) but never opted in via
// gradle.properties. That produces a build-time failure
// ("...contains AndroidX dependencies, but the android.useAndroidX property
// is not enabled...") that has nothing to do with the app's actual code.
// If the file is missing entirely, create it. If it exists but is missing
// either flag, append just what's missing rather than overwriting anything
// the project owner already set.
function ensureGradleProperties(projectRoot) {
  const propsPath = path.join(projectRoot, 'gradle.properties');
  let content = fs.existsSync(propsPath) ? fs.readFileSync(propsPath, 'utf8') : '';

  const required = [
    ['android.useAndroidX=true', /^\s*android\.useAndroidX\s*=/m],
    ['android.enableJetifier=true', /^\s*android\.enableJetifier\s*=/m]
  ];
  const missing = required.filter(([, re]) => !re.test(content)).map(([line]) => line);
  if (missing.length === 0) return false;

  const sep = content.length && !content.endsWith('\n') ? '\n' : '';
  fs.writeFileSync(propsPath, content + sep + missing.join('\n') + '\n', 'utf8');
  return true;
}

// When settings.gradle declares centralized repositories with
// FAIL_ON_PROJECT_REPOS, Gradle refuses to also let a module's build.gradle
// declare its own `allprojects { repositories { ... } } }` block -- even a
// totally standard google()/mavenCentral() one -- and fails with "Build was
// configured to prefer settings repositories over project repositories".
// This is a common copy-paste leftover from older Gradle project templates.
// If both the centralized-repo mode AND a conflicting root-level repo block
// are present, comment the block out so Gradle's settings-level repos win,
// which is what the project's own settings.gradle already says it wants.
function fixRepositoryConflict(projectRoot) {
  const settingsPath = ['settings.gradle', 'settings.gradle.kts']
    .map((f) => path.join(projectRoot, f))
    .find(fs.existsSync);
  if (!settingsPath) return false;

  const settingsContent = fs.readFileSync(settingsPath, 'utf8');
  const usesCentralRepos =
    /dependencyResolutionManagement/.test(settingsContent) && /FAIL_ON_PROJECT_REPOS/.test(settingsContent);
  if (!usesCentralRepos) return false;

  const buildGradlePath = ['build.gradle', 'build.gradle.kts']
    .map((f) => path.join(projectRoot, f))
    .find(fs.existsSync);
  if (!buildGradlePath) return false;

  let buildContent = fs.readFileSync(buildGradlePath, 'utf8');
  const allprojectsRepoRegex = /allprojects\s*\{\s*repositories\s*\{[^}]*\}\s*\}/;
  if (!allprojectsRepoRegex.test(buildContent)) return false;

  buildContent = buildContent.replace(
    allprojectsRepoRegex,
    '// [auto-fixed by apk-builder] removed allprojects { repositories {...} } -- ' +
      'conflicted with settings.gradle\'s centralized repositoriesMode(FAIL_ON_PROJECT_REPOS)'
  );
  fs.writeFileSync(buildGradlePath, buildContent, 'utf8');
  return true;
}

function hasWorkflowFiles(dir) {
  // Must check the SPECIFIC file we dispatch to (WORKFLOW_FILE, e.g.
  // "build.yml") — not just "any yml file with a workflow_dispatch trigger".
  // An uploaded project can legitimately ship its own workflow (e.g.
  // "android-ci.yml") that has workflow_dispatch but a different filename;
  // that used to satisfy this check and cause build.yml to never be
  // restored onto the job branch, so dispatching to build.yml 422'd with
  // "Workflow does not have a workflow_dispatch trigger" (GitHub couldn't
  // find that filename on the ref at all).
  const target = path.join(dir, WORKFLOW_FILE);
  if (!fs.existsSync(target)) return false;
  return /^\s*workflow_dispatch\s*:/m.test(fs.readFileSync(target, 'utf8'));
}

// GitHub only honors workflow_dispatch for a workflow that has been
// registered via the repo's DEFAULT branch — a copy that exists only on a
// throwaway job branch is not enough, even with the trigger correctly
// declared. This runs once at boot: if the base branch doesn't already have
// .github/workflows/<WORKFLOW_FILE> WITH a workflow_dispatch trigger in it,
// a working one is committed straight there so every future dispatch (on
// any branch) actually works.
//
// It also re-syncs an outdated auto-generated workflow: if the committed
// file carries OUR version marker (see FALLBACK_WORKFLOW_VERSION) and that
// version is older than what buildFallbackWorkflowYaml() now produces, it's
// safe to assume this is still our own generated file and just needs
// updating — e.g. a fix like pinning gradle-version otherwise never reaches
// a repo that already has some workflow committed. A file with
// workflow_dispatch but NO version marker is treated as user-authored and
// is never touched, version bump or not.
function extractFallbackWorkflowVersion(content) {
  const m = content.match(/^#\s*apk-builder fallback workflow — version:\s*(\d+)/m);
  return m ? Number(m[1]) : null;
}

async function ensureBaseBranchWorkflow() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'apkbuild-baseinit-'));
  try {
    const remote = `https://x-access-token:${GITHUB_TOKEN}@github.com/${OWNER}/${REPO}.git`;
    await simpleGit().clone(remote, dir, ['--branch', GITHUB_BRANCH, '--single-branch', '--depth', '1']);
    const workflowsDir = path.join(dir, '.github', 'workflows');
    const targetPath = path.join(workflowsDir, WORKFLOW_FILE);

    let commitMessage = `Add missing ${WORKFLOW_FILE} workflow (auto-recovered on boot)`;

    if (fs.existsSync(targetPath)) {
      const existing = fs.readFileSync(targetPath, 'utf8');
      // Cheap but reliable check: workflow_dispatch has to appear as its own
      // key under `on:`, not just anywhere in a comment/string.
      const hasDispatchTrigger = /^\s*workflow_dispatch\s*:/m.test(existing);
      const existingVersion = extractFallbackWorkflowVersion(existing);

      if (!hasDispatchTrigger) {
        console.warn(
          `WARNING: .github/workflows/${WORKFLOW_FILE} exists on "${GITHUB_BRANCH}" but has NO workflow_dispatch trigger. ` +
          `This is exactly what causes the 422 "does not have workflow_dispatch trigger" error. Replacing it with a working default now.`
        );
        commitMessage = `Fix ${WORKFLOW_FILE}: add missing workflow_dispatch trigger (auto-recovered on boot)`;
      } else if (existingVersion === null) {
        console.log(
          `.github/workflows/${WORKFLOW_FILE} on "${GITHUB_BRANCH}" has workflow_dispatch but no apk-builder version marker — ` +
          `treating it as user-authored and leaving it alone.`
        );
        return;
      } else if (existingVersion >= FALLBACK_WORKFLOW_VERSION) {
        console.log(
          `Base branch "${GITHUB_BRANCH}" already has .github/workflows/${WORKFLOW_FILE} at version ${existingVersion} (current) — good.`
        );
        return;
      } else {
        console.warn(
          `.github/workflows/${WORKFLOW_FILE} on "${GITHUB_BRANCH}" is our own generated file but out of date ` +
          `(version ${existingVersion}, current is ${FALLBACK_WORKFLOW_VERSION}) — re-syncing now.`
        );
        commitMessage = `Update ${WORKFLOW_FILE}: v${existingVersion} -> v${FALLBACK_WORKFLOW_VERSION} (auto-synced on boot)`;
      }
    } else {
      console.warn(
        `WARNING: .github/workflows/${WORKFLOW_FILE} was missing entirely on "${GITHUB_BRANCH}". ` +
        `Committing a default one now — dispatches would 422 until this exists on the default branch.`
      );
    }

    fs.mkdirSync(workflowsDir, { recursive: true });
    fs.writeFileSync(targetPath, buildFallbackWorkflowYaml(), 'utf8');

    const git = simpleGit(dir);
    await git.addConfig('user.email', 'apk-builder@example.com');
    await git.addConfig('user.name', 'apk-builder-bot');
    await git.add('.');
    await git.commit(commitMessage);
    await git.push(['origin', GITHUB_BRANCH]);
    console.log(`Pushed .github/workflows/${WORKFLOW_FILE} to "${GITHUB_BRANCH}".`);
  } catch (err) {
    console.error('ensureBaseBranchWorkflow failed — dispatches will likely keep 422ing until this is fixed manually:', err.message);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

// ---- Step 1: upload zip, unzip, push to a job-only branch, trigger workflow ----
app.post('/api/build', upload.single('projectZip'), async (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'No zip file uploaded' });
  if (!req.file.originalname.toLowerCase().endsWith('.zip')) {
    fs.rmSync(req.file.path, { force: true });
    return res.status(400).json({ error: 'Please upload a .zip file' });
  }

  const jobId = newJobId();
  const branch = `build/${jobId}`;
  setJob(jobId, { status: 'starting', message: 'Preparing project files', branch, createdAt: Date.now() });
  res.json({ jobId });

  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'apkbuild-'));

  try {
    // 1. Extract the uploaded zip
    const zip = new AdmZip(req.file.path);
    const extractDir = path.join(workDir, 'extracted');
    zip.extractAllTo(extractDir, true);

    // Find the actual Gradle project inside the zip, whatever shape it's
    // in — a bare project, one wrapped in a single top-level folder, or
    // buried a bit deeper.
    let { root: projectRoot, ambiguous } = pickProjectRoot(extractDir);

    if (!projectRoot) {
      // No Android/Gradle project anywhere — but if there's an HTML file in
      // here, this is very likely just a static web app someone wants
      // packaged as an APK, not a broken Android project. Auto-generate a
      // minimal WebView-wrapper project around it instead of failing.
      const entryHtmlRel = findEntryHtml(extractDir);
      if (!entryHtmlRel) {
        throw new Error(
          'No settings.gradle/settings.gradle.kts AND no HTML file found anywhere in the zip, ' +
          'so this can\'t be built as an Android project or auto-wrapped as a web app. ' +
          'Zip either a Gradle project (see "How to prep it") or a folder with an index.html.'
        );
      }
      const zipBaseName = path.basename(req.file.originalname, '.zip');
      projectRoot = generateWebViewWrapperProject(extractDir, workDir, zipBaseName, entryHtmlRel);
      setJob(jobId, {
        message: `No Android project found — auto-generated a WebView wrapper app around your web files (entry: ${entryHtmlRel})`
      });
    }
    if (ambiguous) {
      setJob(jobId, { message: 'Multiple Gradle projects found in the zip — using the top-level one' });
    }

    // Fix the most common reasons a real project fails to build on a CI
    // runner even though it builds fine locally (stale local.properties,
    // a non-executable or CRLF-damaged gradlew, missing AndroidX opt-in,
    // conflicting repository declarations).
    const appliedFixes = normalizeProjectForCI(projectRoot);
    if (appliedFixes.length > 0) {
      setJob(jobId, { message: `Auto-fixed: ${appliedFixes.join('; ')}`, autoFixes: appliedFixes });
    }

    setJob(jobId, { status: 'pushing', message: 'Pushing project to a private build branch' });

    // 2. Clone the base branch, create a job-only branch, wipe it, copy in the
    //    new project, commit, push. The base branch (and every other user's
    //    branch) is never touched — this is what keeps concurrent builds
    //    from ever colliding.
    const repoDir = path.join(workDir, 'repo');
    const remote = `https://x-access-token:${GITHUB_TOKEN}@github.com/${OWNER}/${REPO}.git`;
    const git = simpleGit();

    await git.clone(remote, repoDir, ['--branch', GITHUB_BRANCH, '--single-branch', '--depth', '1']);
    const repoGit = simpleGit(repoDir);
    await repoGit.checkoutLocalBranch(branch);

    // Preserve the base branch's .github/workflows before wiping the repo,
    // so we can restore it if the uploaded project doesn't bring its own.
    const baseWorkflowsDir = path.join(repoDir, '.github', 'workflows');
    let preservedWorkflowsDir = null;
    if (hasWorkflowFiles(baseWorkflowsDir)) {
      preservedWorkflowsDir = path.join(workDir, 'preserved-workflows');
      copyRecursive(baseWorkflowsDir, preservedWorkflowsDir);
    }

    for (const entry of fs.readdirSync(repoDir)) {
      if (entry === '.git') continue;
      fs.rmSync(path.join(repoDir, entry), { recursive: true, force: true });
    }
    copyRecursive(projectRoot, repoDir);

    // fs.copyFileSync doesn't reliably carry the executable bit across the
    // copy, so re-apply it here — this is what git actually commits (a
    // gradlew that isn't marked executable in the tree still fails on the
    // runner even if it was fixed in the tmp extraction dir a moment ago).
    const repoGradlew = path.join(repoDir, 'gradlew');
    if (fs.existsSync(repoGradlew)) fs.chmodSync(repoGradlew, 0o755);

    // If the uploaded project didn't include its own workflow, restore the
    // base branch's, or generate a sensible default as a last resort — this
    // is what was 422ing before: the branch had no workflow file at all.
    const newWorkflowsDir = path.join(repoDir, '.github', 'workflows');
    if (!hasWorkflowFiles(newWorkflowsDir)) {
      fs.mkdirSync(newWorkflowsDir, { recursive: true });
      if (preservedWorkflowsDir) {
        copyRecursive(preservedWorkflowsDir, newWorkflowsDir);
        setJob(jobId, { message: 'No workflow in upload — reused existing build workflow' });
      } else {
        fs.writeFileSync(path.join(newWorkflowsDir, WORKFLOW_FILE), buildFallbackWorkflowYaml(), 'utf8');
        setJob(jobId, { message: 'No workflow in upload — generated a default build workflow' });
      }
    }

    await repoGit.addConfig('user.email', 'apk-builder@example.com');
    await repoGit.addConfig('user.name', 'apk-builder-bot');
    await repoGit.add('.');
    await repoGit.commit(`Build ${jobId}`);
    await repoGit.push(['-u', 'origin', branch, '--force']);

    setJob(jobId, { status: 'queued', message: 'Triggering Tycept Actions build' });

    // 3. Trigger the workflow on that branch specifically.
    //    GitHub can take a moment to index a just-pushed branch/file, so a
    //    422 right after push is retried a few times before giving up.
    let dispatchRes, dispatchBody;
    const MAX_DISPATCH_ATTEMPTS = 8;
    for (let attempt = 1; attempt <= MAX_DISPATCH_ATTEMPTS; attempt++) {
      dispatchRes = await fetch(`${API}/repos/${OWNER}/${REPO}/actions/workflows/${WORKFLOW_FILE}/dispatches`, {
        method: 'POST',
        headers: { ...authHeaders, 'Content-Type': 'application/json' },
        body: JSON.stringify({ ref: branch })
      });
      if (dispatchRes.ok) break;
      dispatchBody = await dispatchRes.text();
      if (dispatchRes.status !== 422 || attempt === MAX_DISPATCH_ATTEMPTS) {
        throw new Error(`Could not start the workflow (${dispatchRes.status}): ${dispatchBody.slice(0, 200)}`);
      }
      console.log(`Dispatch 422'd (GitHub still indexing?), retrying in ${2000 * attempt}ms (attempt ${attempt}/${MAX_DISPATCH_ATTEMPTS})`);
      await sleep(2000 * attempt);
    }

    // 4. Poll for the run on THIS branch (not a time guess — every job has its
    //    own branch name, so there's no ambiguity even if many jobs start at once)
    const runId = await findRunForBranch(branch);
    if (!runId) {
      setJob(jobId, { status: 'error', message: 'Could not find the triggered workflow run' });
      await deleteBranch(branch);
      return;
    }
    setJob(jobId, { status: 'building', message: 'Build running', runId });
    await pollRun(jobId, runId, branch);

  } catch (err) {
    console.error(err);
    setJob(jobId, { status: 'error', message: err.message || 'Build failed' });
    await deleteBranch(branch);
  } finally {
    fs.rmSync(workDir, { recursive: true, force: true });
    fs.rmSync(req.file.path, { force: true });
  }
});

// ---- Step 2: frontend polls this for status ----
app.get('/api/status/:jobId', (req, res) => {
  const job = jobs[req.params.jobId];
  if (!job) return res.status(404).json({ error: 'Unknown job' });
  res.json(job);
});

// ---- Step 3: proxy-download the finished APK (keeps the GitHub token server-side) ----
// GitHub always wraps an artifact in its OWN zip container, even when the
// artifact is a single .apk — so naively piping that response gives the
// browser a .zip, not an .apk. This downloads that wrapper server-side,
// finds the actual .apk entry inside it, and streams just that file back
// with the right name and content-type.
app.get('/api/download/:jobId', async (req, res) => {
  const job = jobs[req.params.jobId];
  if (!job || job.status !== 'done' || !job.artifactId) {
    return res.status(400).send('Build not ready');
  }

  let tmpZipPath;
  try {
    const zipRes = await fetch(
      `${API}/repos/${OWNER}/${REPO}/actions/artifacts/${job.artifactId}/zip`,
      { headers: authHeaders, redirect: 'follow' }
    );
    if (!zipRes.ok) {
      return res.status(502).send('Could not fetch the build artifact from GitHub');
    }

    tmpZipPath = path.join(os.tmpdir(), `artifact-${req.params.jobId}.zip`);
    const buffer = Buffer.from(await zipRes.arrayBuffer());
    fs.writeFileSync(tmpZipPath, buffer);

    const zip = new AdmZip(tmpZipPath);
    const apkEntry = zip.getEntries().find((e) => !e.isDirectory && e.entryName.toLowerCase().endsWith('.apk'));

    if (!apkEntry) {
      // Not an APK artifact at all (e.g. someone re-hit this URL for a lint
      // report job) — say so plainly instead of silently sending a zip.
      return res.status(400).send('This build artifact does not contain an APK file');
    }

    const apkBuffer = apkEntry.getData();
    const downloadName = path.basename(apkEntry.entryName);
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader('Content-Disposition', `attachment; filename="${downloadName}"`);
    res.setHeader('Content-Length', apkBuffer.length);
    res.send(apkBuffer);
  } catch (err) {
    console.error('Download failed:', err);
    res.status(500).send('Failed to prepare the APK for download');
  } finally {
    if (tmpZipPath) fs.rmSync(tmpZipPath, { force: true });
  }
});

// ---------- auto-generated WebView wrapper (for zips with no Gradle project) ----------

// If nothing in the zip looks like an Android project, it may still be a
// perfectly good static web app (HTML/CSS/JS) that the user just wants
// packaged as an APK. Rather than failing outright, look for any HTML file
// — that's the signal this is a "wrap it in a WebView" job instead of a
// "this isn't an Android project at all" job.
function findEntryHtml(dir) {
  const queue = [dir];
  let firstAnyHtml = null;
  while (queue.length) {
    const d = queue.shift();
    let entries;
    try {
      entries = fs.readdirSync(d, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const e of entries) {
      if (e.isDirectory()) {
        if (IGNORED_DIR_NAMES.has(e.name)) continue;
        queue.push(path.join(d, e.name));
        continue;
      }
      if (!e.isFile()) continue;
      const full = path.join(d, e.name);
      const rel = path.relative(dir, full).split(path.sep).join('/');
      if (e.name.toLowerCase() === 'index.html') return rel; // best match, stop immediately
      if (!firstAnyHtml && /\.html?$/i.test(e.name)) firstAnyHtml = rel;
    }
  }
  return firstAnyHtml;
}

function sanitizePackageSegment(str) {
  let s = (str || '').toLowerCase().replace(/[^a-z0-9]/g, '');
  if (!s) s = 'app';
  if (/^[0-9]/.test(s)) s = 'a' + s;
  return s;
}

// Builds a minimal-but-complete Android/Gradle project (settings.gradle,
// build.gradle x2, gradle.properties, manifest, a WebView MainActivity) with
// the entire uploaded zip copied into app/src/main/assets, and points the
// WebView at whichever HTML file looks like the entry point. This mirrors
// exactly the manual wrapper pattern that's known to build cleanly, so any
// static web project can be turned into an APK with zero setup on the
// user's part.
// Brand mark: the same isometric box/package icon used in the app's own
// topbar logo (not a generic shape), scaled up and given a two-tone fill
// so it reads clearly at launcher size -- recognizable as *this* app's
// icon rather than a placeholder. Background carries the faint repeating
// scanline texture from the web UI's own ".scan" overlay, so the icon
// still feels unmistakably on-brand even though it's bolder than the flat
// dark square it replaces. Written as vector XML rather than a raster PNG
// so no image-generation step is needed at build time.
const LAUNCHER_BG = '#050505';
const LAUNCHER_SURFACE = '#0C0C0C';
const LAUNCHER_FG = '#F2F2EE';

// Recreates the CSS repeating-linear-gradient scanline texture
// (1px lines every 3px, ~2.5% opacity) as thin low-opacity vector strokes.
function buildScanlines() {
  let lines = '';
  for (let y = 1; y <= 107; y += 3) {
    lines += `\n    <path android:strokeColor="#08FFFFFF" android:strokeWidth="1" android:pathData="M0,${y} L108,${y}" />`;
  }
  return lines;
}

// Isometric box mark (scaled/recentered from the topbar logo's 24x24 SVG
// viewBox into this icon's 108x108 canvas, k=3): outer hexagon body with a
// subtle fill for depth, plus the front-edge/center-seam lines.
const LAUNCHER_BOX_PATHS = `
    <path android:fillColor="${LAUNCHER_SURFACE}" android:strokeColor="${LAUNCHER_FG}"
          android:strokeWidth="4.8" android:strokeLineJoin="round" android:strokeLineCap="round"
          android:pathData="M54,24 L27,39 L27,69 L54,84 L81,69 L81,39 Z" />
    <path android:strokeColor="${LAUNCHER_FG}" android:strokeWidth="4.8"
          android:strokeLineJoin="round" android:strokeLineCap="round"
          android:pathData="M27,39 L54,54 L81,39 M54,54 L54,84" />`;

function writeLauncherIcon(projectRoot) {
  const res = path.join(projectRoot, 'app', 'src', 'main', 'res');
  const scanlines = buildScanlines();

  // Adaptive icon (API 26+): separate background + foreground layers.
  fs.mkdirSync(path.join(res, 'mipmap-anydpi-v26'), { recursive: true });
  fs.writeFileSync(
    path.join(res, 'mipmap-anydpi-v26', 'ic_launcher.xml'),
    `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
`,
    'utf8'
  );

  fs.mkdirSync(path.join(res, 'drawable'), { recursive: true });
  fs.writeFileSync(
    path.join(res, 'drawable', 'ic_launcher_background.xml'),
    `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="${LAUNCHER_BG}" android:pathData="M0,0h108v108h-108z" />${scanlines}
</vector>
`,
    'utf8'
  );

  fs.writeFileSync(
    path.join(res, 'drawable', 'ic_launcher_foreground.xml'),
    `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">${LAUNCHER_BOX_PATHS}
</vector>
`,
    'utf8'
  );

  // Flat fallback (API 21-25, no adaptive-icon support): background,
  // scanlines, and the box mark combined into one vector, referenced
  // directly as ic_launcher.
  const flatIcon = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="${LAUNCHER_BG}" android:pathData="M0,0h108v108h-108z" />${scanlines}${LAUNCHER_BOX_PATHS}
</vector>
`;
  fs.writeFileSync(path.join(res, 'drawable', 'ic_launcher.xml'), flatIcon, 'utf8');

  // Resolve @mipmap/ic_launcher (used by AndroidManifest) to the drawable
  // above for pre-26 devices; API 26+ picks up the adaptive-icon.xml first.
  fs.mkdirSync(path.join(res, 'mipmap-anydpi'), { recursive: true });
  fs.writeFileSync(path.join(res, 'mipmap-anydpi', 'ic_launcher.xml'), flatIcon, 'utf8');
}

function generateWebViewWrapperProject(extractDir, workDir, zipBaseName, entryHtmlRel) {
  const projectRoot = path.join(workDir, 'generated-android-project');
  const assetsDir = path.join(projectRoot, 'app', 'src', 'main', 'assets');
  fs.mkdirSync(assetsDir, { recursive: true });
  copyRecursive(extractDir, assetsDir);

  const pkgSegment = sanitizePackageSegment(zipBaseName);
  const packageName = `com.generated.${pkgSegment}`;
  const packagePath = packageName.replace(/\./g, '/');
  const javaDir = path.join(projectRoot, 'app', 'src', 'main', 'java', packagePath);
  fs.mkdirSync(javaDir, { recursive: true });
  fs.mkdirSync(path.join(projectRoot, 'app', 'src', 'main', 'res', 'values'), { recursive: true });

  const appLabel = (zipBaseName || 'App').slice(0, 50);

  fs.writeFileSync(
    path.join(projectRoot, 'settings.gradle'),
    `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "${appLabel.replace(/"/g, '')}"
include ':app'
`,
    'utf8'
  );

  fs.writeFileSync(
    path.join(projectRoot, 'build.gradle'),
    `buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.4.0'
    }
}
`,
    'utf8'
  );

  fs.writeFileSync(
    path.join(projectRoot, 'gradle.properties'),
    `android.useAndroidX=true
android.enableJetifier=true
org.gradle.jvmargs=-Xmx2048m
`,
    'utf8'
  );

  fs.writeFileSync(
    path.join(projectRoot, 'app', 'build.gradle'),
    `apply plugin: 'com.android.application'

android {
    namespace '${packageName}'
    compileSdk 34

    defaultConfig {
        applicationId "${packageName}"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
}
`,
    'utf8'
  );

  fs.writeFileSync(
    path.join(projectRoot, 'app', 'src', 'main', 'AndroidManifest.xml'),
    `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <!-- Only needed pre-Android 10: DownloadManager writing to the public
         Downloads folder is exempt from this on API 29+ (scoped storage). -->
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />

    <application
        android:allowBackup="true"
        android:label="${appLabel.replace(/"/g, '')}"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:theme="@style/Theme.GeneratedApp"
        android:usesCleartextTraffic="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
`,
    'utf8'
  );

  // Themed launcher icon: dark background + the same corner-bracket "frame"
  // motif used across the web UI, so the installed app icon actually looks
  // like it belongs to this project instead of a bare default icon.
  writeLauncherIcon(projectRoot);

  fs.writeFileSync(
    path.join(projectRoot, 'app', 'src', 'main', 'res', 'values', 'styles.xml'),
    `<resources>
    <style name="Theme.GeneratedApp" parent="Theme.AppCompat.DayNight.NoActionBar">
        <item name="android:statusBarColor">#0f1115</item>
    </style>
</resources>
`,
    'utf8'
  );

  fs.writeFileSync(
    path.join(javaDir, 'MainActivity.java'),
    `package ${packageName};

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// Shows a themed loading screen (matching the app's dark background) the
// moment the app opens, and swaps it out for the WebView content only once
// the page has actually finished loading -- so opening the app never shows
// a blank white flash while the WebView engine spins up.
//
// Also wires up onShowFileChooser: a plain WebView does NOT respond to
// <input type="file"> clicks out of the box -- without this override,
// tapping a file-upload control silently does nothing, which is the most
// common "the button doesn't work" complaint for wrapped web apps that
// let the user pick a file.
//
// And a DownloadListener: a plain WebView also does NOT know what to do
// with a link to a downloadable file (an APK, a zip, etc) -- without this,
// tapping a "Download" link just fails to navigate anywhere and the app
// appears to do nothing / falls back to showing the page underneath it.
// This hands the download off to Android's real DownloadManager so it
// saves properly to the device's Downloads folder with a system
// notification, the way a normal download is expected to behave.
public class MainActivity extends AppCompatActivity {

    private static final int FILE_CHOOSER_REQUEST_CODE = 51426;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 51427;
    private ValueCallback<Uri[]> filePathCallback;

    // Stashed so we can retry the download once a requested permission is granted.
    private String pendingUrl, pendingUserAgent, pendingContentDisposition, pendingMimeType;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#050505"));

        final WebView webView = new WebView(this);
        webView.setVisibility(View.GONE);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(webView, webParams);

        final ProgressBar loading = new ProgressBar(this);
        loading.getIndeterminateDrawable().setColorFilter(
            Color.parseColor("#F2F2EE"), PorterDuff.Mode.SRC_IN);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        loadingParams.gravity = Gravity.CENTER;
        root.addView(loading, loadingParams);

        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loading.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
            startDownload(url, userAgent, contentDisposition, mimeType));

        webView.loadUrl("file:///android_asset/${entryHtmlRel}");
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        // On API < 29, writing to the public Downloads folder needs the
        // runtime WRITE_EXTERNAL_STORAGE permission. API 29+ (scoped
        // storage) doesn't need it for DownloadManager's public downloads.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingUrl = url;
            pendingUserAgent = userAgent;
            pendingContentDisposition = contentDisposition;
            pendingMimeType = mimeType;
            ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },
                STORAGE_PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.addRequestHeader("User-Agent", userAgent);
            request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
            request.setMimeType(mimeType);
            request.setTitle(filename);
            request.setDescription("Downloading " + filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);
            Toast.makeText(this, "Downloading " + filename + "\\u2026", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't start the download", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != STORAGE_PERMISSION_REQUEST_CODE) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
            && pendingUrl != null) {
            startDownload(pendingUrl, pendingUserAgent, pendingContentDisposition, pendingMimeType);
        } else {
            Toast.makeText(this, "Storage permission is needed to save the download", Toast.LENGTH_LONG).show();
        }
        pendingUrl = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILE_CHOOSER_REQUEST_CODE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (filePathCallback == null) return;

        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int i = 0; i < count; i++) {
                    results[i] = data.getClipData().getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                results = new Uri[]{ data.getData() };
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }
}
`,
    'utf8'
  );

  return projectRoot;
}

// A workflow can upload more than one artifact (lint report, test results,
// the APK itself, etc). Previously we always grabbed artifacts[0], which is
// whatever GitHub happens to list first — sometimes the lint report. This
// scores each artifact and picks the one that actually looks like the APK.
function pickApkArtifact(artifacts) {
  if (!artifacts || artifacts.length === 0) return null;
  const NEGATIVE = /lint|report|test-results?|checkstyle|pmd|coverage|jacoco|proguard-mapping|mapping\.txt/i;
  const POSITIVE = /apk|assembledebug|assemblerelease|debug-apk|release-apk/i;

  const scored = artifacts.map((a) => {
    let score = 0;
    if (POSITIVE.test(a.name)) score += 10;
    if (NEGATIVE.test(a.name)) score -= 10;
    return { a, score };
  });
  scored.sort((x, y) => y.score - x.score);
  return scored[0].a;
}

function copyRecursive(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    // Skip junk/build-output folders that some zip tools or IDEs leave
    // behind (__MACOSX from macOS zips, stale build/.gradle/.idea dirs) —
    // they only bloat the push and can occasionally shadow real project
    // files. A user's own .github/workflows is never affected since it's
    // handled separately before/after this copy.
    if (entry.isDirectory() && IGNORED_DIR_NAMES.has(entry.name)) continue;
    const s = path.join(src, entry.name);
    const d = path.join(dest, entry.name);
    if (entry.isDirectory()) copyRecursive(s, d);
    else fs.copyFileSync(s, d);
  }
}

async function findRunForBranch(branch, attempts = 10) {
  for (let i = 0; i < attempts; i++) {
    await sleep(2000);
    const r = await fetch(
      `${API}/repos/${OWNER}/${REPO}/actions/workflows/${WORKFLOW_FILE}/runs?branch=${encodeURIComponent(branch)}&per_page=1`,
      { headers: authHeaders }
    );
    const data = await r.json();
    if (data.workflow_runs && data.workflow_runs.length > 0) {
      return data.workflow_runs[0].id;
    }
  }
  return null;
}

async function pollRun(jobId, runId, branch, maxAttempts = 90) {
  for (let i = 0; i < maxAttempts; i++) {
    await sleep(5000);
    const r = await fetch(`${API}/repos/${OWNER}/${REPO}/actions/runs/${runId}`, { headers: authHeaders });
    const run = await r.json();

    if (run.status !== 'completed') {
      setJob(jobId, { status: 'building', message: `Build in progress (${run.status})`, runId });
      continue;
    }

    if (run.conclusion !== 'success') {
      setJob(jobId, { status: 'error', message: `Build failed (${run.conclusion})`, runId });
      await deleteBranch(branch);
      return;
    }

    const artRes = await fetch(`${API}/repos/${OWNER}/${REPO}/actions/runs/${runId}/artifacts`, { headers: authHeaders });
    const artData = await artRes.json();
    const artifact = pickApkArtifact(artData.artifacts);

    if (!artifact) {
      setJob(jobId, { status: 'error', message: 'Build succeeded but no APK artifact was found', runId });
      await deleteBranch(branch);
      return;
    }

    setJob(jobId, {
      status: 'done',
      message: 'APK ready',
      runId,
      artifactId: artifact.id,
      artifactName: artifact.name
    });
    scheduleCleanup(jobId, branch);
    return;
  }
  setJob(jobId, { status: 'error', message: 'Timed out waiting for build', runId });
  await deleteBranch(branch);
}

// Once a job is done (or after it errors), remove its build branch on GitHub
// and forget the job after a while, so nothing lingers to conflict with the
// next person's build.
function scheduleCleanup(jobId, branch) {
  setTimeout(async () => {
    await deleteBranch(branch);
    delete jobs[jobId];
  }, Number(JOB_TTL_MINUTES) * 60 * 1000);
}

async function deleteBranch(branch) {
  try {
    await fetch(`${API}/repos/${OWNER}/${REPO}/git/refs/heads/${encodeURIComponent(branch)}`, {
      method: 'DELETE',
      headers: authHeaders
    });
  } catch (err) {
    console.error(`Could not delete branch ${branch}:`, err.message);
  }
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

app.listen(PORT, async () => {
  console.log(`apk-builder running on http://localhost:${PORT}`);
  await ensureBaseBranchWorkflow();
});
