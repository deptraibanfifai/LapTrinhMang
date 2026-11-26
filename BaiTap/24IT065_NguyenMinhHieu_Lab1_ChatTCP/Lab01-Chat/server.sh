#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
java -Djava.net.preferIPv4Stack=true -cp target/lab01-chat-1.0.0.jar vn.vku.chat.server.ServerMain "$@"
