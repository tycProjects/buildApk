# YDK Client - GitHub Actions build

1. Create a GitHub repository.
2. Upload/extract this project so that `gradlew`, `settings.gradle.kts`, `app/`, and `.github/` are at the repository root.
3. Push to the `main` branch.
4. Open **Actions** → **Build YDK Client APK**.
5. Choose **Run workflow** (or push a commit to `main`).
6. When the job finishes, open the run and download the artifact **YDK-Client-debug-APK**.

This workflow builds a **debug APK**. It does not require the private keystore in the repository.
