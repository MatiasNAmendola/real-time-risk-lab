#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: cli/clean-ignored.sh [--dry-run|--force] [--include-tracked]

Deletes files/directories that Git would ignore according to .gitignore and
other standard Git exclude rules.

Default is --dry-run: it only prints what would be removed.

Options:
  --dry-run           Show ignored files/directories that would be removed.
                      This is the default.
  --force            Actually remove ignored, untracked files/directories using
                      git clean -Xdf.
  --include-tracked  Also include tracked files that now match .gitignore.
                      In dry-run mode they are only listed. With --force they
                      are deleted from the working tree too, so use carefully.
  -h, --help          Show this help.

Examples:
  cli/clean-ignored.sh
  cli/clean-ignored.sh --force
  cli/clean-ignored.sh --dry-run --include-tracked
USAGE
}

mode="dry-run"
include_tracked="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      mode="dry-run"
      ;;
    --force)
      mode="force"
      ;;
    --include-tracked)
      include_tracked="true"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

if ! command -v git >/dev/null 2>&1; then
  echo "Error: git is required because this script follows Git ignore semantics." >&2
  exit 1
fi

if ! repo_root="$(git rev-parse --show-toplevel 2>/dev/null)"; then
  cat >&2 <<'ERROR'
Error: this directory is not inside a Git work tree.

Run git init first, or run this script from the repository that contains the
.gitignore you want to apply.
ERROR
  exit 1
fi

cd "$repo_root"

echo "Repository: $repo_root"
echo "Mode: $mode"
echo "Include tracked ignored files: $include_tracked"
echo

if [[ "$mode" == "dry-run" ]]; then
  echo "Ignored untracked files/directories that would be removed:"
  git clean -Xdf -n
else
  echo "Removing ignored untracked files/directories..."
  git clean -Xdf -q
fi

mapfile -d '' tracked_ignored < <(git ls-files -ci --exclude-standard -z)

if [[ ${#tracked_ignored[@]} -gt 0 ]]; then
  echo
  if [[ "$include_tracked" == "true" ]]; then
    if [[ "$mode" == "dry-run" ]]; then
      echo "Tracked files that match ignore rules and would be removed with --force --include-tracked:"
      printf 'Would remove tracked ignored file: %s\n' "${tracked_ignored[@]}"
    else
      echo "Removing tracked files that match ignore rules from the working tree..."
      for path in "${tracked_ignored[@]}"; do
        rm -rf -- "$path"
        printf 'Removed tracked ignored file: %s\n' "$path"
      done
      cat <<'NOTE'

Note: those paths are still tracked by the local Git index until you also run
      git rm --cached <path>
or stage their deletion for a commit. This script only cleans the working tree.
NOTE
    fi
  else
    cat <<'NOTE'
Tracked files that currently match ignore rules were found, but were not removed.
Run again with --include-tracked if you intentionally want to delete them too.
NOTE
    printf 'Tracked ignored file: %s\n' "${tracked_ignored[@]}"
  fi
fi

echo
echo "Done."
