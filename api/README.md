# api/ — nhzterm-api client library (stub)

The documented client SDK every application (the in-app UI, Valence Studio,
any future consumer) uses to talk to `nhztermd`:

- Transport: Android `LocalSocket`, length-prefixed JSON (4-byte big-endian
  length + UTF-8 body) — concept doc §6.1
- `hello`/`hello_ack` token handshake — §6.2
- Session control: `session.create/attach/list/kill/write/resize/rename` — §6.3
- Process control: `process.spawn/status/stop/list/kill` — §6.4
- Streamed events + error codes — §6.5/§6.6

**Status:** not yet built in this workspace (see `../docs/STATUS.md`). The
protocol itself is language-agnostic; the first client is Kotlin.

Note for implementers: nhzsh's daemon control channel (foreground-PID
reporting, verified in `../nhzsh/tests/test_daemon_link.c`) uses the **same
framing** as this protocol, on a separate socket.
