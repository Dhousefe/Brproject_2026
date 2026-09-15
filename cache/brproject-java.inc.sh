#!/usr/bin/env bash
# Brproject — localiza o Java (macOS / Linux).
# Exporta: JAVA_CMD, JAVA_HOME (se inferível), JAVA_MAJOR
#
# O server.jar do BrProject é compilado com Java 25 (class file 69).
# Mínimo: BRPROJECT_MIN_JAVA (default 25).
#
# Ordem de detecção (sempre preferindo major >= MIN):
#   1. BRPROJECT_JAVA_HOME
#   2. SDKMAN candidates (…/java/25*, current se ok)
#   3. /usr/libexec/java_home -v 25 (macOS)
#   4. JAVA_HOME (se major >= MIN)
#   5. java no PATH (se major >= MIN)

BRPROJECT_MIN_JAVA="${BRPROJECT_MIN_JAVA:-25}"

_brproject_normalize_java_home() {
  local jh="${1:-}"
  jh="${jh%\"}"
  jh="${jh#\"}"
  jh="${jh%/}"
  if [[ "$jh" == */bin/java ]]; then
    jh="${jh%/bin/java}"
  elif [[ "$jh" == */bin ]]; then
    jh="${jh%/bin}"
  fi
  printf '%s' "$jh"
}

_brproject_java_major() {
  local cmd="${1:-java}"
  "$cmd" -version 2>&1 | head -n 1 | sed -E 's/.*"([0-9]+)(\.[0-9]+)*.*/\1/; s/^1\.//'
}

_brproject_try_java_home() {
  local jh="$1"
  [[ -z "$jh" ]] && return 1
  jh="$(_brproject_normalize_java_home "$jh")"
  [[ -x "$jh/bin/java" ]] || return 1
  local maj
  maj="$(_brproject_java_major "$jh/bin/java")"
  if [[ -n "$maj" && "$maj" -ge "$BRPROJECT_MIN_JAVA" ]]; then
    JAVA_HOME="$jh"
    JAVA_CMD="$jh/bin/java"
    JAVA_MAJOR="$maj"
    return 0
  fi
  return 1
}

_brproject_try_java_cmd() {
  local cmd="$1"
  [[ -z "$cmd" || ! -x "$cmd" ]] && [[ ! -x "$(command -v "$cmd" 2>/dev/null)" ]] && return 1
  # resolve if just "java"
  if [[ "$cmd" != /* ]]; then
    cmd="$(command -v "$cmd" 2>/dev/null || true)"
  fi
  [[ -n "$cmd" && -x "$cmd" ]] || return 1
  local maj
  maj="$(_brproject_java_major "$cmd")"
  if [[ -n "$maj" && "$maj" -ge "$BRPROJECT_MIN_JAVA" ]]; then
    JAVA_CMD="$cmd"
    JAVA_MAJOR="$maj"
    if command -v realpath >/dev/null 2>&1; then
      JAVA_HOME="$(dirname "$(dirname "$(realpath "$cmd")")")"
    else
      JAVA_HOME="$(dirname "$(dirname "$cmd")")"
    fi
    return 0
  fi
  return 1
}

_brproject_find_java() {
  # 1) Forçado pelo usuário
  if [[ -n "${BRPROJECT_JAVA_HOME:-}" ]]; then
    if _brproject_try_java_home "$BRPROJECT_JAVA_HOME"; then
      return 0
    fi
    echo "AVISO: BRPROJECT_JAVA_HOME=$BRPROJECT_JAVA_HOME não é JDK >= $BRPROJECT_MIN_JAVA; ignorando." >&2
  fi

  # 2) SDKMAN (muito comum no macOS)
  local sdk_root="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java"
  if [[ -d "$sdk_root" ]]; then
    # 25.x primeiro (ordem reverso: mais novo primeiro)
    local d
    for d in $(ls -1d "$sdk_root"/25* 2>/dev/null | sort -r); do
      if _brproject_try_java_home "$d"; then
        return 0
      fi
    done
    # current só se for 25+
    if _brproject_try_java_home "$sdk_root/current"; then
      return 0
    fi
    # qualquer outro >= MIN
    for d in $(ls -1d "$sdk_root"/* 2>/dev/null | sort -r); do
      [[ "$(basename "$d")" == "current" ]] && continue
      if _brproject_try_java_home "$d"; then
        return 0
      fi
    done
  fi

  # 3) macOS java_home
  if [[ "$(uname -s)" == "Darwin" ]] && [[ -x /usr/libexec/java_home ]]; then
    local ver jh
    for ver in 25 26 24; do
      jh="$(/usr/libexec/java_home -v "$ver" 2>/dev/null || true)"
      if _brproject_try_java_home "$jh"; then
        return 0
      fi
    done
  fi

  # 4) JAVA_HOME do ambiente (só se >= MIN)
  if [[ -n "${JAVA_HOME:-}" ]]; then
    if _brproject_try_java_home "$JAVA_HOME"; then
      return 0
    fi
  fi

  # 5) PATH
  if _brproject_try_java_cmd "java"; then
    return 0
  fi

  return 1
}

if ! _brproject_find_java; then
  echo "════════════════════════════════════════════════════════════" >&2
  echo " ERRO: JDK $BRPROJECT_MIN_JAVA+ necessário (bytecode class 69)." >&2
  echo " O server.jar foi compilado com Java 25; Java 21 não roda." >&2
  echo "" >&2
  echo " Opções:" >&2
  echo "   sdkman:  sdk install java 25.0.3-tem && sdk use java 25.0.3-tem" >&2
  echo "   forçar:  export BRPROJECT_JAVA_HOME=\"\$HOME/.sdkman/candidates/java/25.0.3-tem\"" >&2
  echo "════════════════════════════════════════════════════════════" >&2
  return 1 2>/dev/null || exit 1
fi

export JAVA_CMD JAVA_HOME JAVA_MAJOR BRPROJECT_MIN_JAVA
