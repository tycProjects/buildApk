CC      ?= gcc
CFLAGS  ?= -std=c11 -D_GNU_SOURCE -Wall -Wextra -O1 -g
LDFLAGS ?=

SRCS     := $(wildcard src/*.c)
OBJS     := $(SRCS:.c=.o)
LIB_OBJS := $(filter-out src/main.o,$(OBJS))

TEST_CS   := $(wildcard tests/test_*.c)
TEST_BINS := $(TEST_CS:.c=)

.PHONY: all test testbins clean android

all: nhzsh

nhzsh: $(OBJS)
	$(CC) $(CFLAGS) -o $@ $(OBJS) $(LDFLAGS)

src/%.o: src/%.c
	$(CC) $(CFLAGS) -c -o $@ $<

tests/test_%: tests/test_%.c $(LIB_OBJS)
	$(CC) $(CFLAGS) -Isrc -o $@ $< $(LIB_OBJS) $(LDFLAGS)

testbins: $(TEST_BINS)

test: nhzsh testbins
	sh tests/run_tests.sh

clean:
	rm -f nhzsh $(OBJS) $(TEST_BINS)

# ---------------------------------------------------------------------------
# Android cross-compile for nhztermd packaging (concept doc §12.1).
# Produces PIE executables named libnhzsh.so per ABI — Android requires the
# lib*.so name inside jniLibs/ (Termux uses the same convention); the daemon
# stages the file to system/bin/nhzsh and marks it executable at startup.
#
#   make android ANDROID_NDK_HOME=/path/to/ndk
# ---------------------------------------------------------------------------
android:
	@test -n "$(ANDROID_NDK_HOME)" || { echo "error: set ANDROID_NDK_HOME to your NDK directory"; exit 1; }; \
	set -e; \
	for abi in arm64-v8a armeabi-v7a x86_64; do \
	  case $$abi in \
	    arm64-v8a)   triple=aarch64-linux-android24;; \
	    armeabi-v7a) triple=armv7a-linux-androideabi24;; \
	    x86_64)      triple=x86_64-linux-android24;; \
	  esac; \
	  clang=$$(find "$(ANDROID_NDK_HOME)/toolchains/llvm/prebuilt" -name "$$triple-clang" 2>/dev/null | head -1); \
	  test -n "$$clang" || { echo "error: no $$triple-clang under $$ANDROID_NDK_HOME"; exit 1; }; \
	  mkdir -p build-android/$$abi; \
	  echo "==> $$abi ($$triple)"; \
	  $$clang -std=c11 -D_GNU_SOURCE -Wall -O2 -o build-android/$$abi/libnhzsh.so $(SRCS); \
	done; \
	echo "Android binaries in build-android/<abi>/libnhzsh.so"
