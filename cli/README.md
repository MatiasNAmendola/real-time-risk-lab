# CLI utilities

Small command-line helpers that are useful around the monorepo but are not part
of the runtime platform itself.

## Clean ignored files

`clean-ignored.sh` removes files and directories that Git would ignore according
to the repository `.gitignore` and standard Git exclude rules. It is intended for
pre-share cleanup: build outputs, Gradle state, logs, caches, generated files and
internal handoffs should not leak into the first shared version of the repo.

Dry-run first:

```bash
cli/clean-ignored.sh
```

Actually delete ignored untracked files/directories:

```bash
cli/clean-ignored.sh --force
```

If a file was already tracked locally before being added to `.gitignore`, Git
will not remove it with `git clean`. To inspect those paths:

```bash
cli/clean-ignored.sh --dry-run --include-tracked
```

To delete those tracked-but-now-ignored files from the working tree too:

```bash
cli/clean-ignored.sh --force --include-tracked
```

Use the tracked mode carefully: after deleting tracked paths, also remove them
from the local index before sharing if that is your intent:

```bash
git rm --cached <path>
```
