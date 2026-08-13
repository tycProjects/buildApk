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
const jobs = {}; // jobId -> { status, message, branch, runId, artifactId, artifactName, createdAt, buildStartedAt }

function newJobId() {
  return Date.now().toString(36) + crypto.randomBytes(4).toString('hex');
}

function setJob(jobId, patch) {
  jobs[jobId] = { ...jobs[jobId], ...patch, updatedAt: Date.now() };
}

// ---- Queue position + ETA ----
//
// There's no real server-side queue (each /api/build request runs its own
// push/dispatch/poll concurrently), but from the user's point of view what
// matters is "how many builds are ahead of mine" and "how much longer".
// PENDING_STATUSES are the phases before a job actually has a GitHub Actions
// run in flight; queue position counts other pending jobs that were created
// earlier. This is intentionally a same-process, in-memory notion (like the
// job store itself) -- fine for a single instance, not meant to survive a
// restart or coordinate across instances.
const PENDING_STATUSES = new Set(['starting', 'pushing', 'queued']);

function computeQueuePosition(job) {
  if (!PENDING_STATUSES.has(job.status)) return 0;
  return Object.values(jobs).filter(
    (j) => PENDING_STATUSES.has(j.status) && j.createdAt < job.createdAt
  ).length;
}

// Rolling window of recent real build durations (ms), used to estimate how
// long a build in progress has left. Kept short and in-memory on purpose --
// this only needs to track "recent" pace, not a historical record.
const recentBuildDurations = [];
const MAX_DURATION_SAMPLES = 20;
const DEFAULT_BUILD_ESTIMATE_MS = 4 * 60 * 1000; // used until we have real samples

function recordBuildDuration(jobId) {
  const job = jobs[jobId];
  if (!job || !job.buildStartedAt) return;
  recentBuildDurations.push(Date.now() - job.buildStartedAt);
  if (recentBuildDurations.length > MAX_DURATION_SAMPLES) recentBuildDurations.shift();
}

function averageBuildDurationMs() {
  if (recentBuildDurations.length === 0) return DEFAULT_BUILD_ESTIMATE_MS;
  return recentBuildDurations.reduce((a, b) => a + b, 0) / recentBuildDurations.length;
}

function computeEtaSeconds(job) {
  if (job.status === 'building' && job.buildStartedAt) {
    const elapsedMs = Date.now() - job.buildStartedAt;
    const remainingMs = averageBuildDurationMs() - elapsedMs;
    // Floor at 15s rather than letting this hit 0/negative -- a build that's
    // taking longer than average is still probably close, not "overdue".
    return Math.max(Math.round(remainingMs / 1000), 15);
  }
  if (PENDING_STATUSES.has(job.status)) {
    return Math.round(averageBuildDurationMs() / 1000);
  }
  return null; // done / error / cancelled — nothing left to estimate
}

function withProgressFields(job) {
  return {
    ...job,
    queuePosition: computeQueuePosition(job),
    estimatedSecondsRemaining: computeEtaSeconds(job)
  };
}

// Bump this any time buildFallbackWorkflowYaml()'s actual steps change
// (new fix, pinned tool version, etc). ensureBaseBranchWorkflow() compares
// this against the version marker already committed on the base branch and
// re-pushes when they differ — that's what makes a fix like pinning
// gradle-version actually reach already-existing repos instead of only
// applying to brand new ones.
const FALLBACK_WORKFLOW_VERSION = 4;

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
function buildFallbackWorkflowYaml(buildType = 'apk') {
  const isAab = buildType === 'aab';
  const gradleTask = isAab ? 'bundleRelease' : 'assembleDebug';
  const outputGlob = isAab ? '**/build/outputs/bundle/release/*.aab' : '**/build/outputs/apk/debug/*.apk';
  const artifactPrefix = isAab ? 'release-aab' : 'debug-apk';
  const stepLabel = isAab ? 'Build release AAB (Play Store bundle)' : 'Build debug APK';

  return `# apk-builder fallback workflow — version: ${FALLBACK_WORKFLOW_VERSION}
# Auto-generated. Edit buildFallbackWorkflowYaml() in server.js, not this
# file directly — direct edits get overwritten the next time the version
# above is bumped and this gets re-synced to the base branch.
name: Build ${isAab ? 'AAB' : 'APK'}

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

          # gradlew and gradle-wrapper.properties can exist while the actual
          # gradle-wrapper.jar (a binary file, easy to lose in a zip or via
          # .gitignore) is missing. That's the "Could not find or load main
          # class org.gradle.wrapper.GradleWrapperMain" failure -- gradlew has
          # nothing to run. Regenerate the missing jar with the Gradle we
          # already set up above (pinned to the same version), so the
          # project's own wrapper becomes usable again instead of just being
          # abandoned.
          if [ -f "./gradlew" ] && [ -f "./gradle/wrapper/gradle-wrapper.properties" ] && [ ! -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
            echo "gradle-wrapper.jar is missing — regenerating it"
            gradle wrapper --gradle-version 8.7 --no-daemon
            chmod +x ./gradlew
          fi

          # XML comments containing "--" anywhere inside them (e.g. a comment
          # like "<!-- fix -- update later -->") are invalid XML and make the
          # resource merger fail with "The string '--' is not permitted
          # within comments." Rather than trying to detect/repair just the
          # bad ones, strip ALL comments from res/*.xml files -- comments
          # have no effect on the build, so this is always safe and removes
          # the whole class of failure.
          find . -path '*/res/*' -name '*.xml' -print0 \\
            | xargs -0 -r perl -0777 -pi -e 's/<!--.*?-->//gs'

      - name: ${stepLabel}
        working-directory: \${{ steps.locate.outputs.dir }}
        run: |
          if [ -x "./gradlew" ] && [ -f "./gradle/wrapper/gradle-wrapper.properties" ]; then
            echo "Building with the project's own Gradle wrapper"
            ./gradlew ${gradleTask} --no-daemon
          else
            echo "No usable Gradle wrapper found — building with the runner's Gradle instead"
            gradle ${gradleTask} --no-daemon
          fi

      - name: Upload ${isAab ? 'AAB' : 'APK'}
        uses: actions/upload-artifact@v4
        with:
          name: ${artifactPrefix}-\${{ github.run_id }}
          path: \${{ steps.locate.outputs.dir }}/${outputGlob}
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
app.post('/api/build', upload.fields([{ name: 'projectZip', maxCount: 1 }, { name: 'logo', maxCount: 1 }]), async (req, res) => {
  const projectZipFile = req.files && req.files.projectZip && req.files.projectZip[0];
  const logoFile = req.files && req.files.logo && req.files.logo[0];

  if (!projectZipFile) return res.status(400).json({ error: 'No zip file uploaded' });
  if (!projectZipFile.originalname.toLowerCase().endsWith('.zip')) {
    fs.rmSync(projectZipFile.path, { force: true });
    if (logoFile) fs.rmSync(logoFile.path, { force: true });
    return res.status(400).json({ error: 'Please upload a .zip file' });
  }
  if (logoFile && !/\.(png|jpe?g|webp)$/i.test(logoFile.originalname)) {
    fs.rmSync(projectZipFile.path, { force: true });
    fs.rmSync(logoFile.path, { force: true });
    return res.status(400).json({ error: 'App icon must be a PNG, JPG, or WEBP image' });
  }

  const jobId = newJobId();
  const branch = `build/${jobId}`;
  // buildType/appName/packageSuffix/clientId are sent by the frontend's
  // "Build options" panel — read and honored here so those controls
  // actually affect the build instead of being silently accepted and
  // ignored.
  const buildType = req.body.buildType === 'aab' ? 'aab' : 'apk';
  const appName = typeof req.body.appName === 'string' ? req.body.appName.trim().slice(0, 50) : '';
  const packageSuffix = typeof req.body.packageSuffix === 'string' ? req.body.packageSuffix.trim().slice(0, 30) : '';
  const clientId = typeof req.body.clientId === 'string' ? req.body.clientId.trim().slice(0, 64) : null;
  setJob(jobId, { status: 'starting', message: 'Preparing project files', branch, createdAt: Date.now(), buildType, clientId, originalName: projectZipFile.originalname });
  res.json({ jobId });

  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'apkbuild-'));

  try {
    // 1. Extract the uploaded zip
    const zip = new AdmZip(projectZipFile.path);
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
      const zipBaseName = path.basename(projectZipFile.originalname, '.zip');
      projectRoot = generateWebViewWrapperProject(extractDir, workDir, zipBaseName, entryHtmlRel, { appName, packageSuffix });
      setJob(jobId, {
        message: `No Android project found — auto-generated a WebView wrapper app around your web files (entry: ${entryHtmlRel})`
      });
      // Custom app icon only applies to this auto-generated wrapper. A real
      // uploaded Gradle project can use any module name/layout, so blindly
      // writing to app/src/main/res could land in the wrong module (or no
      // module at all) — same scoping the appName/packageSuffix options use.
      if (logoFile) {
        writeLauncherIconFromFile(projectRoot, logoFile.path, path.extname(logoFile.originalname).toLowerCase());
        setJob(jobId, { message: 'Applied your uploaded app icon' });
      }
    } else if (logoFile) {
      setJob(jobId, { message: 'Custom app icon ignored — your zip already has its own Android project with its own icon' });
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
      // Only reuse whatever's already committed to the base branch when an
      // APK was requested — that preserved copy is always the boot-time
      // default (see ensureBaseBranchWorkflow), which builds assembleDebug.
      // An AAB request needs bundleRelease specifically, so it always gets
      // a freshly generated workflow instead of a silently ignored buildType.
      if (preservedWorkflowsDir && buildType === 'apk') {
        copyRecursive(preservedWorkflowsDir, newWorkflowsDir);
        setJob(jobId, { message: 'No workflow in upload — reused existing build workflow' });
      } else {
        fs.writeFileSync(path.join(newWorkflowsDir, WORKFLOW_FILE), buildFallbackWorkflowYaml(buildType), 'utf8');
        setJob(jobId, { message: `No workflow in upload — generated a default ${buildType.toUpperCase()} build workflow` });
      }
    }

    await repoGit.addConfig('user.email', 'apk-builder@example.com');
    await repoGit.addConfig('user.name', 'apk-builder-bot');
    await repoGit.add('.');
    await repoGit.commit(`Build ${jobId}`);
    await repoGit.push(['-u', 'origin', branch, '--force']);

    // A cancel request can land while we were still pushing -- check before
    // spending an Actions dispatch on a job the user already gave up on.
    if (jobs[jobId]?.status === 'cancelled') return;

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
    if (jobs[jobId]?.status === 'cancelled') return;

    const runId = await findRunForBranch(branch);
    if (!runId) {
      setJob(jobId, { status: 'error', message: 'Could not find the triggered workflow run' });
      await deleteBranch(branch);
      return;
    }
    if (jobs[jobId]?.status === 'cancelled') return;
    setJob(jobId, { status: 'building', message: 'Build running', runId, buildStartedAt: Date.now() });
    await pollRun(jobId, runId, branch);

  } catch (err) {
    console.error(err);
    setJob(jobId, { status: 'error', message: err.message || 'Build failed' });
    await deleteBranch(branch);
  } finally {
    fs.rmSync(workDir, { recursive: true, force: true });
    fs.rmSync(projectZipFile.path, { force: true });
    if (logoFile) fs.rmSync(logoFile.path, { force: true });
  }
});

// ---- Step 2: frontend polls this for status ----
app.get('/api/status/:jobId', (req, res) => {
  const job = jobs[req.params.jobId];
  if (!job) return res.status(404).json({ error: 'Unknown job' });
  res.json(withProgressFields(job));
});

// Lightweight build history for the current browser only. clientId is a
// random id the frontend generates and stores in localStorage -- not
// authentication, just a convenience filter so a returning visitor sees
// their own recent builds instead of a global list. This route was missing
// entirely, which is why the "Your recent builds" panel never showed
// anything (its fetch 404'd and was silently swallowed).
app.get('/api/history', (req, res) => {
  const clientId = typeof req.query.clientId === 'string' ? req.query.clientId : null;
  if (!clientId) return res.json([]);
  const mine = Object.entries(jobs)
    .filter(([, job]) => job.clientId === clientId)
    .map(([jobId, job]) => ({
      jobId,
      status: job.status,
      message: job.message,
      buildType: job.buildType || 'apk',
      originalName: job.originalName,
      artifactName: job.artifactName || null,
      createdAt: job.createdAt
    }))
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
    .slice(0, 25);
  res.json(mine);
});

// ---- Cancel a build that hasn't finished yet ----
// Marks the job cancelled immediately (so polling loops stop on their next
// tick), asks GitHub to cancel the Actions run if one has started, and tears
// down the job's branch the same way a normal failure would. Safe to call at
// any pending/building phase -- there's just less to clean up the earlier it
// happens.
app.post('/api/cancel/:jobId', async (req, res) => {
  const jobId = req.params.jobId;
  const job = jobs[jobId];
  if (!job) return res.status(404).json({ error: 'Unknown job' });
  if (['done', 'error', 'cancelled'].includes(job.status)) {
    return res.status(400).json({ error: `Job already ${job.status} — nothing to cancel` });
  }

  setJob(jobId, { status: 'cancelled', message: 'Cancelled by user' });

  if (job.runId) {
    try {
      await fetch(`${API}/repos/${OWNER}/${REPO}/actions/runs/${job.runId}/cancel`, {
        method: 'POST',
        headers: authHeaders
      });
    } catch (err) {
      console.error(`Could not cancel run ${job.runId}:`, err.message);
    }
  }
  if (job.branch) await deleteBranch(job.branch);

  res.json(withProgressFields(jobs[jobId]));
});

// GitHub always wraps an artifact in its OWN zip container, even when the
// artifact is a single .apk. Shared by both the variant listing and the
// actual download below so there's one place that downloads+extracts it.
async function fetchArtifactZip(artifactId, tag) {
  const zipRes = await fetch(
    `${API}/repos/${OWNER}/${REPO}/actions/artifacts/${artifactId}/zip`,
    { headers: authHeaders, redirect: 'follow' }
  );
  if (!zipRes.ok) {
    throw new Error(`GitHub returned ${zipRes.status} fetching artifact ${artifactId}`);
  }
  const tmpZipPath = path.join(os.tmpdir(), `artifact-${tag}-${Date.now()}.zip`);
  const buffer = Buffer.from(await zipRes.arrayBuffer());
  fs.writeFileSync(tmpZipPath, buffer);
  return { zip: new AdmZip(tmpZipPath), tmpZipPath };
}

function apkEntriesOf(zip) {
  // Matches .apk and .aab -- an AAB build produces the latter instead, and
  // reusing this same helper/variant-picker path means the variants and
  // download endpoints don't need separate logic per build type.
  return zip.getEntries().filter(
    (e) => !e.isDirectory && (e.entryName.toLowerCase().endsWith('.apk') || e.entryName.toLowerCase().endsWith('.aab'))
  );
}

// A build can produce more than one APK -- most commonly ABI-split debug
// builds (armeabi-v7a/arm64-v8a/x86/x86_64 + a universal one). Without a way
// to know the requesting device's ABI, "universal" (works everywhere, just
// bigger) is the safest default rather than an arbitrary architecture.
function pickDefaultApkEntry(apkEntries) {
  if (apkEntries.length === 0) return null;
  return apkEntries.find((e) => /universal/i.test(e.entryName)) || apkEntries[0];
}

// ---- Lists the APK(s) inside a finished job's artifact, so a frontend can ----
// ---- offer a variant picker (e.g. "arm64-v8a (18MB)" vs "universal (34MB)") ----
app.get('/api/variants/:jobId', async (req, res) => {
  const job = jobs[req.params.jobId];
  if (!job || job.status !== 'done' || !job.artifactId) {
    return res.status(400).json({ error: 'Build not ready' });
  }

  let tmpZipPath;
  try {
    const { zip, tmpZipPath: tzp } = await fetchArtifactZip(job.artifactId, `variants-${req.params.jobId}`);
    tmpZipPath = tzp;
    const apkEntries = apkEntriesOf(zip);
    const defaultEntry = pickDefaultApkEntry(apkEntries);
    res.json({
      variants: apkEntries.map((e) => ({
        name: path.basename(e.entryName),
        sizeBytes: e.header.size,
        isDefault: defaultEntry ? e.entryName === defaultEntry.entryName : false
      }))
    });
  } catch (err) {
    console.error('Could not list APK variants:', err.message);
    res.status(502).json({ error: 'Could not read the build artifact from GitHub' });
  } finally {
    if (tmpZipPath) fs.rmSync(tmpZipPath, { force: true });
  }
});

// ---- Step 3: proxy-download the finished APK (keeps the GitHub token server-side) ----
// Naively piping GitHub's response would give the browser a .zip, not an
// .apk. This downloads that wrapper server-side, finds the requested .apk
// entry inside it (or picks a sensible default -- see pickDefaultApkEntry),
// and streams just that file back with the right name and content-type.
// Pass ?apk=<filename from /api/variants> to pick a specific ABI variant.
app.get('/api/download/:jobId', async (req, res) => {
  const job = jobs[req.params.jobId];
  if (!job || job.status !== 'done' || !job.artifactId) {
    return res.status(400).send('Build not ready');
  }

  let tmpZipPath;
  try {
    const { zip, tmpZipPath: tzp } = await fetchArtifactZip(job.artifactId, req.params.jobId);
    tmpZipPath = tzp;
    const apkEntries = apkEntriesOf(zip);

    if (apkEntries.length === 0) {
      // Not an APK artifact at all (e.g. someone re-hit this URL for a lint
      // report job) — say so plainly instead of silently sending a zip.
      return res.status(400).send('This build artifact does not contain an APK or AAB file');
    }

    let apkEntry;
    if (req.query.apk) {
      apkEntry = apkEntries.find((e) => path.basename(e.entryName) === req.query.apk);
      if (!apkEntry) return res.status(404).send('Requested APK variant not found in this build');
    } else {
      apkEntry = pickDefaultApkEntry(apkEntries);
    }

    const apkBuffer = apkEntry.getData();
    const downloadName = path.basename(apkEntry.entryName);
    const isAab = downloadName.toLowerCase().endsWith('.aab');
    res.setHeader('Content-Type', isAab ? 'application/octet-stream' : 'application/vnd.android.package-archive');
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

// Zip-app mark (scaled/recentered from the topbar logo's 24x24 SVG viewBox
// into this icon's 108x108 canvas, kept inside the adaptive-icon safe zone
// of roughly 21-87): a rounded-square app-icon body with a zipper seam
// down the middle, mirroring the "zip becomes an app" idea from the
// in-app logo. Takes the stroke color as a param so the mark can be
// tinted to the wrapped app's own theme-color when one is available (and
// has enough contrast to actually be visible), instead of always being
// the same fixed off-white.
function buildLauncherBoxPaths(fgColor) {
  return `
    <path android:fillColor="${LAUNCHER_SURFACE}" android:strokeColor="${fgColor}"
          android:strokeWidth="5" android:strokeLineJoin="round" android:strokeLineCap="round"
          android:pathData="M42,27 L66,27 A10,10 0 0 1 76,37 L76,71 A10,10 0 0 1 66,81 L42,81 A10,10 0 0 1 32,71 L32,37 A10,10 0 0 1 42,27 Z" />
    <path android:strokeColor="${fgColor}" android:strokeWidth="5" android:strokeLineCap="round"
          android:pathData="M54,27 L54,81" />
    <path android:strokeColor="${fgColor}" android:strokeWidth="4" android:strokeLineCap="round"
          android:pathData="M48,41 L60,41 M48,54 L60,54 M48,67 L60,67" />`;
}

const HEX_COLOR_RE = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/;

// A theme-color close in brightness to the launcher background makes the
// generated stroke effectively invisible (a dark theme-color rendering a
// dark icon on a dark background). Reject any candidate whose perceived
// brightness isn't meaningfully different from LAUNCHER_BG's, rather than
// trusting every theme-color blindly.
function relativeBrightness(hex) {
  let h = hex.replace('#', '');
  if (h.length === 3) h = h.split('').map((c) => c + c).join('');
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return 0.299 * r + 0.587 * g + 0.114 * b;
}
const MIN_ICON_CONTRAST = 60; // out of 255
function hasEnoughContrastForIcon(hex) {
  return Math.abs(relativeBrightness(hex) - relativeBrightness(LAUNCHER_BG)) >= MIN_ICON_CONTRAST;
}

// If the wrapped web app declares a theme-color meta tag, use it to tint
// the generated mark so a fallback icon still looks like it belongs to
// *this* app rather than every generated app getting an identical icon.
function extractThemeColor(extractDir, entryHtmlRel) {
  try {
    const html = fs.readFileSync(path.join(extractDir, entryHtmlRel), 'utf8');
    const match = html.match(
      /<meta[^>]+name=["']theme-color["'][^>]+content=["']([^"']+)["']/i
    );
    if (match && HEX_COLOR_RE.test(match[1]) && hasEnoughContrastForIcon(match[1])) return match[1];
    return null;
  } catch {
    return null;
  }
}

// ---- Using the zip's own icon instead of generating one ----
//
// Generating a mark is only a fallback for zips that don't already have
// one. Most real web/app projects ship their own icon, so we check the
// standard places first and, if we find one, copy it in as-is rather than
// overriding it with our own mark.
const ICON_FILENAME_PATTERNS = [
  /^apple-touch-icon(-precomposed)?\.png$/i,
  /^icon-?512\.(png|webp)$/i,
  /^icon-?192\.(png|webp)$/i,
  /^(app-?)?icon\.(png|webp)$/i,
  /^logo\.(png|webp)$/i,
  /^favicon\.(png|webp)$/i, // favicon.ico is intentionally skipped -- not
                             // a format that can be copied straight into
                             // an Android mipmap resource
];

function findManifestIcon(dir, manifestName) {
  const manifestPath = path.join(dir, manifestName);
  if (!fs.existsSync(manifestPath)) return null;
  try {
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    if (!Array.isArray(manifest.icons) || !manifest.icons.length) return null;
    const usable = manifest.icons
      .filter((i) => i && i.src && /\.(png|webp)$/i.test(i.src))
      .map((i) => ({
        src: i.src,
        size: parseInt(String(i.sizes || '0').split('x')[0], 10) || 0,
      }))
      .sort((a, b) => b.size - a.size); // largest declared icon wins
    if (!usable.length) return null;
    const resolved = path.join(dir, usable[0].src.replace(/^\/+/, ''));
    return fs.existsSync(resolved) ? resolved : null;
  } catch {
    return null; // malformed manifest -- fall through to filename search
  }
}

// Same bounded BFS shape as findEntryHtml, so it respects IGNORED_DIR_NAMES
// and handles a zip with the real project nested a level or two down.
function findExistingAppIcon(extractDir) {
  const fromManifest =
    findManifestIcon(extractDir, 'manifest.json') ||
    findManifestIcon(extractDir, 'site.webmanifest');
  if (fromManifest) return fromManifest;

  const queue = [extractDir];
  const candidates = [];
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
      const patternIndex = ICON_FILENAME_PATTERNS.findIndex((p) => p.test(e.name));
      if (patternIndex !== -1) candidates.push({ path: path.join(d, e.name), patternIndex });
    }
  }
  if (!candidates.length) return null;
  candidates.sort((a, b) => a.patternIndex - b.patternIndex); // best pattern match first
  return candidates[0].path;
}

// Copies the zip's own icon into every launcher-icon slot Android looks at.
// One image is reused across all mipmap densities rather than resized per
// bucket -- Android scales it to fit, so it displays correctly everywhere,
// just not maximally crisp at the very largest launcher sizes. No adaptive
// icon (mipmap-anydpi-v26) is written here: that variant takes priority
// over plain mipmap PNGs on API 26+, so leaving it out is what lets the
// uploaded icon actually be used instead of silently overridden.
function writeLauncherIconFromFile(projectRoot, iconPath, extOverride) {
  const res = path.join(projectRoot, 'app', 'src', 'main', 'res');
  // multer's temp file itself has no extension (just a random name in
  // os.tmpdir()), so the extension must come from the original upload's
  // filename or aapt won't recognize the resource type at all.
  const ext = extOverride || path.extname(iconPath).toLowerCase();
  const buf = fs.readFileSync(iconPath);
  for (const bucket of ['mipmap-mdpi', 'mipmap-hdpi', 'mipmap-xhdpi', 'mipmap-xxhdpi', 'mipmap-xxxhdpi']) {
    const dir = path.join(res, bucket);
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, `ic_launcher${ext}`), buf);
  }
}

function writeLauncherIcon(projectRoot, accentColor) {
  const res = path.join(projectRoot, 'app', 'src', 'main', 'res');
  const scanlines = buildScanlines();
  const fgColor = accentColor && HEX_COLOR_RE.test(accentColor) ? accentColor : LAUNCHER_FG;
  const boxPaths = buildLauncherBoxPaths(fgColor);

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
    android:viewportWidth="108" android:viewportHeight="108">${boxPaths}
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
    <path android:fillColor="${LAUNCHER_BG}" android:pathData="M0,0h108v108h-108z" />${scanlines}${boxPaths}
</vector>
`;
  fs.writeFileSync(path.join(res, 'drawable', 'ic_launcher.xml'), flatIcon, 'utf8');

  // Resolve @mipmap/ic_launcher (used by AndroidManifest) to the drawable
  // above for pre-26 devices; API 26+ picks up the adaptive-icon.xml first.
  fs.mkdirSync(path.join(res, 'mipmap-anydpi'), { recursive: true });
  fs.writeFileSync(path.join(res, 'mipmap-anydpi', 'ic_launcher.xml'), flatIcon, 'utf8');
}

function generateWebViewWrapperProject(extractDir, workDir, zipBaseName, entryHtmlRel, options = {}) {
  const projectRoot = path.join(workDir, 'generated-android-project');
  const assetsDir = path.join(projectRoot, 'app', 'src', 'main', 'assets');
  fs.mkdirSync(assetsDir, { recursive: true });
  copyRecursive(extractDir, assetsDir);

  const pkgSegment = sanitizePackageSegment(options.packageSuffix || zipBaseName);
  const packageName = `com.generated.${pkgSegment}`;
  const packagePath = packageName.replace(/\./g, '/');
  const javaDir = path.join(projectRoot, 'app', 'src', 'main', 'java', packagePath);
  fs.mkdirSync(javaDir, { recursive: true });
  fs.mkdirSync(path.join(projectRoot, 'app', 'src', 'main', 'res', 'values'), { recursive: true });

  const appLabel = (options.appName || zipBaseName || 'App').slice(0, 50);

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

    // ABI-split debug APKs: a WebView wrapper has no native code of its own,
    // but androidx/appcompat still ship a handful of native libs, so a
    // single "fat" APK bundles all four architectures even though any one
    // device only ever uses one. Splitting means most users can download a
    // much smaller per-architecture APK; universalApk keeps a "just works
    // everywhere" fallback around too (see pickDefaultApkEntry in server.js,
    // which defaults to this universal one when a variant isn't specified).
    splits {
        abi {
            enable true
            reset()
            include 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
            universalApk true
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

  // Launcher icon: prefer whatever icon the zip already ships (favicon,
  // apple-touch-icon, a manifest.json/site.webmanifest icons entry, etc.)
  // over generating one. Only when nothing usable is found do we fall back
  // to drawing our own mark -- tinted with the app's theme-color if it
  // declares one, so even the fallback reflects this specific app.
  const existingIcon = findExistingAppIcon(extractDir);
  if (existingIcon) {
    writeLauncherIconFromFile(projectRoot, existingIcon);
  } else {
    const accentColor = extractThemeColor(extractDir, entryHtmlRel);
    writeLauncherIcon(projectRoot, accentColor);
  }

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
import android.app.Dialog;
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
import android.os.Message;
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
        // Needed for Google/Firebase-style "sign in with popup" flows: that JS
        // calls window.open() on the auth provider's URL, and Chrome/Firebase
        // then closes that popup itself once sign-in finishes. Without these
        // two, WebView either can't open the popup at all or opens it detached
        // from the parent page's session, so the auth handler gets a request
        // it can't reconcile and shows "The requested action is invalid".
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
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

            // Handles window.open() calls, which is how Google/Firebase-style
            // "sign in with popup" flows work. A bare WebView has nowhere to put
            // that second window, so without this override the popup silently
            // fails (or opens detached from the parent page) and the auth
            // handler comes back with "The requested action is invalid".
            //
            // We give it a real WebView hosted in a full-screen Dialog, and rely
            // on the provider's own page calling window.close() when the flow
            // finishes (which Firebase's auth handler does) to dismiss it.
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView popupWebView = new WebView(MainActivity.this);
                WebSettings popupSettings = popupWebView.getSettings();
                popupSettings.setJavaScriptEnabled(true);
                popupSettings.setDomStorageEnabled(true);

                final Dialog popupDialog = new Dialog(MainActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                popupDialog.setContentView(popupWebView);
                popupDialog.setOnDismissListener(d -> popupWebView.destroy());
                popupDialog.show();

                popupWebView.setWebViewClient(new WebViewClient());
                popupWebView.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onCloseWindow(WebView window) {
                        popupDialog.dismiss();
                    }
                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popupWebView);
                resultMsg.sendToTarget();
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
  const POSITIVE = /apk|aab|assembledebug|assemblerelease|bundlerelease|debug-apk|release-apk|release-aab/i;

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

// ---- Failure diagnosis: turn a raw Actions log into "what broke + how to fix it" ----

function stripAnsiAndGroups(text) {
  return text
    .replace(/\x1b\[[0-9;]*m/g, '')       // color codes
    .replace(/^##\[[a-z]+\].*$/gim, '')   // ##[group]/##[endgroup]/##[error] markers
    .replace(/\r/g, '');
}

async function fetchFailedJobLogs(runId) {
  const jobsRes = await fetch(`${API}/repos/${OWNER}/${REPO}/actions/runs/${runId}/jobs`, { headers: authHeaders });
  const jobsData = await jobsRes.json();
  const failedJob = (jobsData.jobs || []).find((j) => j.conclusion === 'failure') || (jobsData.jobs || [])[0];
  if (!failedJob) return null;

  const logRes = await fetch(`${API}/repos/${OWNER}/${REPO}/actions/jobs/${failedJob.id}/logs`, { headers: authHeaders });
  if (!logRes.ok) return null;
  return stripAnsiAndGroups(await logRes.text());
}

// Known failure signatures, in priority order. Each returns null if it
// doesn't match, or {summary, fix, prompt} if it does. Checked top-to-bottom
// so more specific patterns win over generic ones.
const FAILURE_PATTERNS = [
  {
    name: 'unclosed-comment',
    test: (log) => log.match(/e:\s*\n?\s*(file:\/\/\/\S+\.kt):(\d+):(\d+)\s+Unclosed comment/),
    diagnose: (m) => {
      const [, file, line] = m;
      const shortFile = file.replace(/^file:\/\/\/.*?\/app\/src\/main\/java\//, '');
      return {
        summary: `Unclosed comment in ${shortFile} (line ${line})`,
        fix: `There's a /* comment in ${shortFile} around line ${line} that's never closed with a matching */. Open the file and close it.`,
        prompt: `My Android build failed with "Unclosed comment" in ${shortFile} at line ${line}. Please find the unterminated /* comment and close it with */.`
      };
    }
  },
  {
    name: 'unresolved-reference',
    test: (log) => [...log.matchAll(/e:\s*\n?\s*(file:\/\/\/\S+\.kt):(\d+):(\d+)\s+Unresolved reference:\s*'?(\w+)'?/g)],
    diagnose: (matches) => {
      const byFile = {};
      for (const [, file, line, , ref] of matches) {
        const shortFile = file.replace(/^file:\/\/\/.*?\/app\/src\/main\/java\//, '');
        (byFile[shortFile] ||= []).push(`${ref} (line ${line})`);
      }
      const fileList = Object.entries(byFile).map(([f, refs]) => `${f}: ${[...new Set(refs)].join(', ')}`);
      const allRefs = [...new Set(matches.map((m) => m[4]))];
      return {
        summary: `${matches.length} unresolved reference${matches.length > 1 ? 's' : ''} (${allRefs.slice(0, 4).join(', ')}${allRefs.length > 4 ? '…' : ''})`,
        fix: `These are almost always missing imports. Affected:\n${fileList.join('\n')}`,
        prompt: `My Android/Kotlin build failed with "Unresolved reference" errors for: ${allRefs.join(', ')}, in these files:\n${fileList.join('\n')}\nPlease add the correct import statements (likely from androidx.compose) to fix these.`
      };
    }
  },
  {
    name: 'xml-comment-dash',
    test: (log) => log.match(/(\S+\.xml):(\d+):(\d+):[\s\S]*?The string "--" is not permitted within comments/),
    diagnose: (m) => {
      const [, file, line] = m;
      return {
        summary: `Invalid XML comment in ${file} (line ${line})`,
        fix: `An XML comment in ${file} contains "--" inside it, which isn't valid XML. Remove the double-dash from that comment.`,
        prompt: `My Android build failed because ${file} line ${line} has an XML comment containing "--", which is invalid. Please fix that comment.`
      };
    }
  },
  {
    name: 'sdk-location-not-found',
    test: (log) => /SDK location not found/i.test(log),
    diagnose: () => ({
      summary: 'Android SDK location not found',
      fix: 'The project has a committed local.properties pointing at a path that doesn\'t exist on the build server, or the SDK setup step failed.',
      prompt: 'My Android CI build fails with "SDK location not found". Please check for a committed local.properties with a hardcoded sdk.dir and remove it.'
    })
  },
  {
    name: 'gradle-wrapper-main-missing',
    test: (log) => /Could not find or load main class org\.gradle\.wrapper\.GradleWrapperMain/.test(log),
    diagnose: () => ({
      summary: 'gradle-wrapper.jar is missing from the project',
      fix: 'The Gradle wrapper jar binary wasn\'t included in the zip (often stripped by .gitignore or the zip tool).',
      prompt: 'My Android project is missing gradle/wrapper/gradle-wrapper.jar, causing "Could not find or load main class org.gradle.wrapper.GradleWrapperMain". Please help me regenerate it.'
    })
  }
];

function diagnoseFailure(log) {
  if (!log) return null;
  for (const pattern of FAILURE_PATTERNS) {
    const match = pattern.test(log);
    if (match) return { pattern: pattern.name, ...pattern.diagnose(match) };
  }
  // Fallback: surface the first few raw error-looking lines so there's
  // still something actionable even for a pattern we don't recognize yet.
  const rawLines = log.split('\n').filter((l) => /error|FAILED|Exception/i.test(l)).slice(0, 5);
  if (rawLines.length === 0) return null;
  return {
    pattern: 'unknown',
    summary: 'Build failed — see raw log lines below',
    fix: rawLines.join('\n'),
    prompt: `My Android build failed. Here are the relevant log lines:\n${rawLines.join('\n')}\nPlease help me figure out what's wrong and how to fix it.`
  };
}

async function pollRun(jobId, runId, branch, maxAttempts = 90) {
  for (let i = 0; i < maxAttempts; i++) {
    await sleep(5000);

    // The cancel endpoint already set status + cleaned up the branch/run --
    // just stop polling rather than racing to overwrite what it did.
    if (jobs[jobId]?.status === 'cancelled') return;

    const r = await fetch(`${API}/repos/${OWNER}/${REPO}/actions/runs/${runId}`, { headers: authHeaders });
    const run = await r.json();

    if (run.status !== 'completed') {
      // Keep this message STATIC for a given run.status (no elapsed counter,
      // no per-poll-unique text). The frontend only appends a new log line
      // when job.message changes from the last one it saw -- if this string
      // changes on every 5s poll (e.g. by embedding elapsed time), that
      // dedup never triggers and the log fills with a near-duplicate line
      // every single poll. Leaving it static means the log gets exactly one
      // line per real phase change (queued -> in_progress, etc), while the
      // live statusText element (which re-renders every poll regardless)
      // still gives the "still working" heartbeat in the UI.
      setJob(jobId, { status: 'building', message: `Build in progress (${run.status})`, runId });
      continue;
    }

    if (run.conclusion !== 'success') {
      let message = `Build failed (${run.conclusion})`;
      let fixPrompt = null;
      try {
        const log = await fetchFailedJobLogs(runId);
        const diagnosis = diagnoseFailure(log);
        if (diagnosis) {
          message = `${diagnosis.summary}\n\n${diagnosis.fix}`;
          fixPrompt = diagnosis.prompt;
        }
      } catch (err) {
        console.error(`Could not diagnose failure for run ${runId}:`, err.message);
      }
      recordBuildDuration(jobId);
      setJob(jobId, { status: 'error', message, fixPrompt, runId });
      await deleteBranch(branch);
      return;
    }

    const artRes = await fetch(`${API}/repos/${OWNER}/${REPO}/actions/runs/${runId}/artifacts`, { headers: authHeaders });
    const artData = await artRes.json();
    const artifact = pickApkArtifact(artData.artifacts);

    if (!artifact) {
      recordBuildDuration(jobId);
      setJob(jobId, { status: 'error', message: 'Build succeeded but no APK artifact was found', runId });
      await deleteBranch(branch);
      return;
    }

    recordBuildDuration(jobId);
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
  recordBuildDuration(jobId);
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
