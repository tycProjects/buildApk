Complete Floating Widget integration:
- Add TXT files from widget and main app.
- Persist imported TXT files in app-private storage.
- Show saved files in widget with selected check mark and delete action.
- Select Gõ từng chữ or Dán từng dòng.
- Configure delay in seconds; converted internally to milliseconds.
- Main app and widget share TextFileStore and broadcast library updates.
- Widget uses focusable WindowManager flags so buttons, Spinner and delay input work.
- Hidden state removes the whole overlay; Volume Down x2 restores it.
- Foreground and Sticky Service retained.
Static XML/resource checks passed. Build requires Android SDK Platform 34.
