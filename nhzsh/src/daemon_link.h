#ifndef NHZSH_DAEMON_LINK_H
#define NHZSH_DAEMON_LINK_H

#include "state.h"

/*
 * nhzsh daemon integration hook — Part 2, Phase 6 of the build plan
 * (concept doc §9).
 *
 * nhztermd spawns nhzsh with NHZSH_SESSION_ID and NHZSH_CONTROL_SOCKET
 * in its environment. Whenever a foreground command is exec'd, nhzsh
 * reports its PID to the daemon over that control side-channel (which is
 * separate from the main PTY byte stream); when the command finishes, it
 * reports pid: null. This is fire-and-forget: it never blocks waiting
 * for a response, and if the daemon is unreachable, nhzsh carries on.
 *
 * Wire format matches nhzterm-api §6.1: 4-byte big-endian length prefix
 * followed by a UTF-8 JSON body.
 */
void daemon_report_foreground(ShellState *st, int pid); /* pid <= 0 reports null */
void daemon_close(ShellState *st);

#endif /* NHZSH_DAEMON_LINK_H */
