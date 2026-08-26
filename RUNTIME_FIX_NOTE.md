Runtime hardening included:
- AccessibilityNodeInfo root/focused/parent/child nodes are recycled in finally blocks.
- FloatingWidgetService file loading uses a dedicated ExecutorService.
- Handler callbacks and executor are cleaned in onDestroy.
- WindowManager view is removed safely in onDestroy.
- Dynamic receivers are unregistered during service teardown.
- TextFileStore validates null InputStream.
Static checks: Java braces PASS; XML parse PASS; resource IDs PASS.
Build still requires Android SDK Platform 34 on the target machine.
