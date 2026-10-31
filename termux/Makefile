# Builds Lawnchair natively on Android in Termux: make build, then make install.
# Each step is a file target or a stamp under .toolchain/state, so finished steps are skipped
# and an interrupted build resumes. Nothing is written outside this directory and Termux.

APP_ID     := app.lawnchair.debug
VARIANT    := LawnWithQuickstepGithubDebug
GRADLE_APK := build/outputs/apk/lawnWithQuickstepGithub/debug

SELF     := $(abspath $(firstword $(MAKEFILE_LIST)))
HERE     := $(patsubst %/,%,$(dir $(SELF)))
SRC      := $(patsubst %/,%,$(dir $(HERE)))
OUT_DIR  := $(HERE)/out
APK      := $(OUT_DIR)/lawnchair-debug.apk
SUBMAKE   = $(MAKE) --no-print-directory -f $(SELF)

# Shells such as zsh setups do not always export PREFIX, so fall back to Termux's fixed path
PREFIX  := $(if $(findstring com.termux,$(PREFIX)),$(PREFIX),/data/data/com.termux/files/usr)
PKG     := $(PREFIX)/bin/pkg
export PATH := $(PREFIX)/bin:$(PATH)

# All tooling, caches and temp files live here, nothing is written to $HOME.
# := so an ANDROID_HOME or GRADLE_USER_HOME exported in the shell cannot pull them back out.
TOOLCHAIN         := $(HERE)/.toolchain
STATE             := $(TOOLCHAIN)/state
ANDROID_HOME      := $(TOOLCHAIN)/android-sdk
GRADLE_USER_HOME  := $(TOOLCHAIN)/gradle
ANDROID_USER_HOME := $(TOOLCHAIN)/android
TOOL_TMP          := $(TOOLCHAIN)/tmp
# user.home catches any tool that still falls back to ~ (Kotlin, AGP analytics, java prefs)
JAVA_SANDBOX      := -Djava.io.tmpdir=$(TOOL_TMP) -Duser.home=$(TOOLCHAIN)/home \
                     -Djava.util.prefs.userRoot=$(TOOLCHAIN)/home/.java

# The bootstrap sdkmanager is too old for minor API packages (android-37.2), so it only installs
# CMDLINE_PINNED. 20.0 is the first with that fix; 22.0+ wraps a native x86 binary Termux cannot run.
CMDLINE_TOOLS  := 11076708
CMDLINE_URL    ?= https://dl.google.com/android/repository/commandlinetools-linux-$(CMDLINE_TOOLS)_latest.zip
CMDLINE_PINNED ?= 20.0
SDKMANAGER     := $(ANDROID_HOME)/cmdline-tools/$(CMDLINE_PINNED)/bin/sdkmanager
LICENSE        := $(ANDROID_HOME)/licenses/android-sdk-license

# SDK versions follow build.gradle, so a git pull that bumps them is picked up on the next build
BUILD_TOOLS    := $(shell sed -n 's/.*buildToolsVersion "\([0-9.]*\)".*/\1/p' $(SRC)/build.gradle 2>/dev/null | head -1)
COMPILE_API    := $(shell sed -n 's/.*version = release(\([0-9]*\)).*/\1/p' $(SRC)/build.gradle 2>/dev/null | head -1)
COMPILE_MINOR  := $(shell sed -n 's/.*minorApiLevel = \([0-9]*\).*/\1/p' $(SRC)/build.gradle 2>/dev/null | head -1)
PLATFORM       := android-$(COMPILE_API)$(if $(filter-out 0,$(COMPILE_MINOR)),.$(COMPILE_MINOR))
BT_DIR         := $(ANDROID_HOME)/build-tools/$(BUILD_TOOLS)
SUBMODULE      := $(SRC)/platform_frameworks_libs_systemui/iconloaderlib/build.gradle

# phones kill a large gradle process more often than not; raise on OOM, lower if Android kills it
GRADLE_HEAP    ?= 3g
# every worker can spawn an aapt2 daemon, and Android 12+ kills Termux past 32 child processes
GRADLE_WORKERS ?= 2
TERMUX_PROPS   := $(HOME)/.termux/termux.properties $(HOME)/.config/termux/termux.properties

TERMUX_PKGS    := openjdk-21 aapt2 aidl protobuf git curl unzip android-tools
# their library deps are unversioned, so pkg install can leave an old fmt/abseil that no longer links
TERMUX_LIBS    := fmt abseil-cpp libc++
TOOLS_OK        = ( for t in aapt2 aidl protoc; do test -x $(PREFIX)/bin/$$t || exit 1; \
                    ! $(PREFIX)/bin/$$t --help 2>&1 | grep -q 'CANNOT LINK' || exit 1; done )

# Lawnchair targets Java 21; = so an exported JAVA_HOME for an older JDK is ignored, make JAVA_HOME=... wins
JAVA_HOME = $(patsubst %/bin/java,%,$(firstword $(wildcard $(PREFIX)/lib/jvm/java-21*/bin/java $(PREFIX)/lib/jvm/*21*/bin/java)))

# build.gradle runs git rev-parse; safe.directory avoids git refusing a checkout on shared storage
JAVA_ENV        = JAVA_HOME=$(JAVA_HOME) PATH=$(JAVA_HOME)/bin:$$PATH TMPDIR=$(TOOL_TMP) \
                  ANDROID_HOME=$(ANDROID_HOME) ANDROID_USER_HOME=$(ANDROID_USER_HOME) \
                  GRADLE_USER_HOME=$(GRADLE_USER_HOME) \
                  GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0=$(SRC)
SDKM_RUN        = $(JAVA_ENV) SDKMANAGER_OPTS="$(JAVA_SANDBOX)" sh
# The SDK and maven only ship glibc x86/arm binaries: Termux's aapt2, protoc and aidl replace them.
# Gradle's native integration is glibc too, and file watching needs it.
# Kotlin IC and the build cache can keep Compose stability fields out of sync (NoSuchFieldError $stable).
# Shared storage (Downloads etc) cannot hold executables, so the wrapper is run through sh.
GRADLE          = cd $(SRC) && $(JAVA_ENV) GRADLE_OPTS="$(JAVA_SANDBOX)" sh ./gradlew --no-daemon --no-parallel \
                  --max-workers=$(GRADLE_WORKERS) \
                  --project-cache-dir=$(OUT_DIR)/gradle-cache \
                  -Dorg.gradle.jvmargs="-Xmx$(GRADLE_HEAP) -Dfile.encoding=UTF-8 $(JAVA_SANDBOX)" \
                  -Pkotlin.compiler.execution.strategy=in-process \
                  -Pkotlin.incremental=false --no-build-cache \
                  -Dorg.gradle.native=false --no-watch-fs \
                  -Pandroid.aapt2FromMavenOverride=$(PREFIX)/bin/aapt2 \
                  -I $(HERE)/termux.init.gradle -Ptermux.protoc=$(PREFIX)/bin/protoc

.DEFAULT_GOAL := build

.PHONY: help
help:
	@awk '/^[a-zA-Z][a-zA-Z0-9_-]*:.*## / { \
	    name = substr($$0, 1, index($$0, ":") - 1); desc = substr($$0, index($$0, "## ") + 3); \
	    printf "  \033[1m%-12s\033[0m %s\n", name, desc }' $(MAKEFILE_LIST)

# ---- entry points: packages first, then a sub-make that sees the new JDK and the checkout

.PHONY: build
build: _termux _source _packages ## Install packages and the SDK, then build the debug APK (the default)
	@$(SUBMAKE) _apk

.PHONY: setup
setup: _termux _source _packages ## Install everything needed to build, without building
	@$(SUBMAKE) _toolchain

.PHONY: deploy
deploy: build install ## Build and install

.PHONY: _termux
_termux:
	@test -x $(PKG) || { echo "Termux not found at $(PREFIX). Run this inside the Termux app, not proot or a Linux VM."; exit 1; }

# ---- state: source

.PHONY: _source
_source: $(SUBMODULE)
	@for p in /termux/out /termux/.toolchain; do \
	    grep -qxF "$$p" $(SRC)/.git/info/exclude 2>/dev/null || echo "$$p" >> $(SRC)/.git/info/exclude; done

$(SUBMODULE):
	@test -d $(SRC)/.git || { echo "$(SRC) is not a git checkout, build.gradle needs the commit hash"; exit 1; }
	GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0='*' \
	    git -C $(SRC) submodule update --init --depth 1

# ---- state: Termux packages and JDK

.PHONY: _packages
_packages: $(STATE)/packages
	@$(TOOLS_OK) || { \
	    echo "aapt2, aidl or protoc fail to start, upgrading Termux packages"; \
	    $(PKG) upgrade -y -o Dpkg::Options::=--force-confold; \
	    $(TOOLS_OK) || { echo "aapt2, aidl or protoc still fail to start, see: aidl --help"; exit 1; }; }

$(STATE)/packages:
	$(PKG) update -y -o Dpkg::Options::=--force-confold
	$(PKG) install -y -o Dpkg::Options::=--force-confold $(TERMUX_PKGS) $(TERMUX_LIBS)
	@mkdir -p $(@D) && touch $@

# ---- state: Android SDK (sub-make, where BUILD_TOOLS and PLATFORM are known)

$(SDKMANAGER):
	@test -n "$(JAVA_HOME)" || { echo "No JDK 21 found, pass make JAVA_HOME=<jdk 21>"; exit 1; }
	@mkdir -p $(TOOL_TMP) $(TOOLCHAIN)/home
	@set -e; \
	staging=$(ANDROID_HOME)/.cmdline-tools-download; \
	rm -rf $$staging $(ANDROID_HOME)/cmdline-tools/latest $(ANDROID_HOME)/cmdline-tools/bootstrap; \
	mkdir -p $$staging $(ANDROID_HOME)/cmdline-tools; \
	echo "Downloading Android command line tools"; \
	curl -fL --progress-bar -o $$staging/tools.zip $(CMDLINE_URL); \
	unzip -q $$staging/tools.zip -d $$staging; \
	yes | $(SDKM_RUN) $$staging/cmdline-tools/bin/sdkmanager --sdk_root=$(ANDROID_HOME) --licenses > /dev/null; \
	$(SDKM_RUN) $$staging/cmdline-tools/bin/sdkmanager --sdk_root=$(ANDROID_HOME) "cmdline-tools;$(CMDLINE_PINNED)"; \
	rm -rf $$staging

$(LICENSE): | $(SDKMANAGER)
	yes | $(SDKM_RUN) $(SDKMANAGER) --sdk_root=$(ANDROID_HOME) --licenses > /dev/null

$(BT_DIR)/source.properties: | $(LICENSE)
	$(SDKM_RUN) $(SDKMANAGER) --sdk_root=$(ANDROID_HOME) "build-tools;$(BUILD_TOOLS)"

# if this sdkmanager does not know the platform, AGP downloads it on the first build
$(STATE)/platform-$(PLATFORM): | $(LICENSE)
	$(SDKM_RUN) $(SDKMANAGER) --sdk_root=$(ANDROID_HOME) "platforms;$(PLATFORM)" || \
	    echo "Gradle will download the $(PLATFORM) platform on the first build"
	@mkdir -p $(@D) && touch $@

.PHONY: _toolchain
_toolchain: $(BT_DIR)/source.properties $(STATE)/platform-$(PLATFORM)
	@test -n "$(BUILD_TOOLS)" -a -n "$(COMPILE_API)" || { echo "Could not read SDK versions from $(SRC)/build.gradle"; exit 1; }
	@test -n "$(JAVA_HOME)" -a -x "$(JAVA_HOME)/bin/java" || { echo "No JDK 21 found, pass make JAVA_HOME=<jdk 21>"; exit 1; }
	@"$(JAVA_HOME)/bin/java" -version 2>&1 | grep -Eq 'version "(2[1-9]|[3-9][0-9])' || \
	    { echo "$(JAVA_HOME) is not JDK 21 or newer"; exit 1; }
	@test "$$(readlink $(BT_DIR)/aidl)" = "$(PREFIX)/bin/aidl" || ln -sf $(PREFIX)/bin/aidl $(BT_DIR)/aidl

# ---- build

.PHONY: _apk
_apk: _toolchain
	@mkdir -p $(TOOL_TMP) $(TOOLCHAIN)/home $(OUT_DIR)
	@case "$(SRC)" in /storage/*|/sdcard/*|/mnt/*) \
	    echo "Warning: building on shared storage is slow. Keep the project under ~ for faster builds.";; esac
	$(GRADLE) assemble$(VARIANT)
	@built=$$(ls -t $(SRC)/$(GRADLE_APK)/*.apk 2>/dev/null | head -1); \
	    test -n "$$built" || { echo "No APK in $(SRC)/$(GRADLE_APK)"; exit 1; }; \
	    cp "$$built" $(APK)
	@echo "APK: $(APK)"

.PHONY: install
install: ## Install the last built APK, keeping your settings, then open the home app picker
	@test -f $(APK) || { echo "No APK yet, run: make build"; exit 1; }
	@mkdir -p $(TOOLCHAIN)/home
	@if $(ADB_READY); then \
	    $(ADB) install -r $(APK) && $(ADB) shell am start -a android.settings.HOME_SETTINGS; \
	else \
	    $(SUBMAKE) termux-install; \
	fi

# termux-open only hands files to the installer when allow-external-apps=true in Termux's settings
.PHONY: termux-install
termux-install:
	@grep -Eqs '^[[:space:]]*allow-external-apps[[:space:]]*=[[:space:]]*true' $(TERMUX_PROPS) || { \
	    file=$$(for f in $(TERMUX_PROPS); do [ -f "$$f" ] && echo "$$f" && break; done); \
	    file=$${file:-$(firstword $(TERMUX_PROPS))}; \
	    mkdir -p "$$(dirname "$$file")" && chmod 700 "$$(dirname "$$file")" && touch "$$file"; \
	    if grep -Eq '^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=' "$$file"; then \
	        sed -i -E 's/^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=.*/allow-external-apps=true/' "$$file"; \
	    else \
	        [ -s "$$file" ] && [ -n "$$(tail -c 1 "$$file")" ] && echo >> "$$file"; \
	        echo "allow-external-apps=true" >> "$$file"; \
	    fi; \
	    echo "Set allow-external-apps=true in $$file"; \
	    termux-reload-settings; \
	}
	termux-open --view --content-type application/vnd.android.package-archive $(APK)
	@echo "Confirm the install in the dialog. Allow Termux to install unknown apps if asked."
	@echo "Then pick Lawnchair (Debug) as home app: make run"

# adb over wireless debugging lets the phone install to itself; its keys stay in .toolchain
ADB       := HOME=$(TOOLCHAIN)/home ANDROID_USER_HOME=$(ANDROID_USER_HOME) adb
ADB_READY := command -v adb > /dev/null 2>&1 && $(ADB) get-state > /dev/null 2>&1

.PHONY: pair
pair: ## Pair adb with this phone: make pair PORT=<pairing port> CODE=<pairing code>
	@test -n "$(PORT)" -a -n "$(CODE)" || \
	    { echo "Developer options > Wireless debugging > Pair device with pairing code,"; \
	      echo "then: make pair PORT=<port shown> CODE=<code shown>"; exit 1; }
	@mkdir -p $(TOOLCHAIN)/home
	$(ADB) pair localhost:$(PORT) $(CODE)

.PHONY: connect
connect: ## Connect adb after pairing: make connect PORT=<port on the Wireless debugging screen>
	@test -n "$(PORT)" || { echo "make connect PORT=<port under IP address & Port on the Wireless debugging screen>"; exit 1; }
	$(ADB) connect localhost:$(PORT)

.PHONY: reinstall
reinstall: ## Uninstall first (wiping Lawnchair settings and layout), then install
	@if $(ADB_READY); then \
	    $(ADB) uninstall $(APP_ID) 2>/dev/null || true; $(ADB) install $(APK); \
	else \
	    $(SUBMAKE) uninstall; \
	    echo "Confirm the uninstall, then press enter to continue."; read _; \
	    $(SUBMAKE) install; \
	fi

.PHONY: run
run: ## Open the default home app picker
	@if $(ADB_READY); then $(ADB) shell am start -a android.settings.HOME_SETTINGS; \
	else am start -a android.settings.HOME_SETTINGS; fi

.PHONY: logs
logs: ## Follow logcat for the app (needs adb over wireless debugging)
	@if $(ADB_READY); then $(ADB) logcat --pid=$$($(ADB) shell pidof -s $(APP_ID)); \
	else echo "Android only lets apps read their own logs. Connect adb first: make pair, then make connect."; fi

.PHONY: uninstall
uninstall: ## Uninstall the app
	@if $(ADB_READY); then $(ADB) uninstall $(APP_ID); \
	else am start -a android.intent.action.DELETE -d package:$(APP_ID); fi

.PHONY: clean
clean: ## Delete build output and compiler caches, keeping the toolchain
	@$(SUBMAKE) _toolchain
	$(GRADLE) clean
	rm -rf $(OUT_DIR) $(TOOL_TMP) $(SRC)/.kotlin $(GRADLE_USER_HOME)/caches/build-cache-1

.PHONY: distclean
distclean: ## Also delete .toolchain/ (SDK, gradle caches, debug signing key, step state)
	rm -rf $(OUT_DIR) $(TOOLCHAIN) $(SRC)/build $(SRC)/.gradle $(SRC)/.kotlin
