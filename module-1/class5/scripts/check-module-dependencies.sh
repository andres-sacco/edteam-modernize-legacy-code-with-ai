#!/usr/bin/env bash
# Checks the dependencies between the modules of the monolith (search, provider, pricing, catalog and shared) with
# jdeps over the compiled classes. It fails if a package depends on a package of another module that is not in the
# list of allowed dependencies. The rules are explained in
# docs/modulo-1/clase-5-prepara-el-monolito/reglas-de-dependencias.md
# Usage: ./scripts/check-module-dependencies.sh [classes-dir]   (compile first: mvn -q -B clean package -DskipTests)

CLASSES="${1:-target/classes}"
BASE="com.twa.flights"

if [ ! -d "$CLASSES/com/twa/flights" ]; then
  echo "There are no compiled classes in $CLASSES, run: mvn -q -B clean package -DskipTests"
  exit 2
fi

# Allowed dependencies between packages of different modules: <source package regex> <target package regex>
# The packages are relative to com.twa.flights. Everything else between different modules is a violation.
ALLOWED='
# Every module can use the kernel
^(search|provider|pricing|catalog)(\..*)?$                ^shared(\..*)?$
# The search uses the public interface of the other modules
^search(\..*)?$                                           ^(catalog\.api|catalog\.dto)$
^search(\..*)?$                                           ^provider\.api$
^search(\..*)?$                                           ^(pricing\.api|pricing\.dto)$
# The providers use the catalog (time zones) and translate to the published model of the search (ACL)
^provider(\..*)?$                                         ^(catalog\.api|catalog\.dto)$
^provider(\..*)?$                                         ^(search\.dto|search\.dto\.request)$
# PENDING: the kernel registers the API key interceptor of pricing (WebConfiguration and SwaggerConfiguration)
^shared\.configuration$                                   ^pricing\.configuration$
# PENDING: classes that the plan did not move to a module
^search\.(helper|validator)$                              ^enums$
^service$                                                 ^(pricing\.api|pricing\.dto|pricing\.model|pricing\.repository|specification)$
^specification$                                           ^pricing\.model$
'

module() {
  case "$1" in
    search|search.*) echo search ;;
    provider|provider.*) echo provider ;;
    pricing|pricing.*) echo pricing ;;
    catalog|catalog.*) echo catalog ;;
    shared|shared.*) echo shared ;;
    *) echo "legacy:$1" ;;
  esac
}

allowed() {
  local source=$1 target=$2 source_regex target_regex
  while read -r source_regex target_regex; do
    [ -z "$source_regex" ] && continue
    [[ "$source_regex" == \#* ]] && continue
    if [[ "$source" =~ $source_regex ]] && [[ "$target" =~ $target_regex ]]; then
      return 0
    fi
  done <<<"$ALLOWED"
  return 1
}

violations=0
checked=0
while read -r source target; do
  source=${source#"$BASE".}
  target=${target#"$BASE".}
  [ "$source" = "$BASE" ] && source="(root)"
  [ "$target" = "$BASE" ] && target="(root)"
  [ "$(module "$source")" = "$(module "$target")" ] && continue
  [ "$source" = "(root)" ] && continue
  checked=$((checked + 1))
  if ! allowed "$source" "$target"; then
    echo "  VIOLATION  $source -> $target"
    violations=$((violations + 1))
  fi
done < <(jdeps -verbose:package -e "${BASE//./\\.}.*" "$CLASSES" 2>/dev/null | awk '$2 == "->" && $1 ~ /^com\.twa/ {print $1, $3}' | sort -u)

if [ "$checked" -eq 0 ]; then
  echo "jdeps did not return dependencies, check that the JDK is installed"
  exit 2
fi

if [ "$violations" -gt 0 ]; then
  echo "$violations dependencies between modules are not allowed (of $checked checked)"
  exit 1
fi
echo "OK: $checked dependencies between modules checked, all of them allowed"
