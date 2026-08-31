#!/usr/bin/env python3
"""PreToolUse(Bash) guard: block bulk `git add` in this repo.

Client lists, billing allocations, policy schedules and application forms land
in the working tree as .xlsx/.pdf/.docx and carry customer PII under POPIA.
.gitignore covers them, but `git add -f`/`-A` still sweeps them in, and git
history is not cleanly reversible. Stage by explicit path instead.

Exit 2 = block the call and show stderr to Claude. Exit 0 = allow.
"""
import json
import re
import sys

# `git add` where the pathspec is everything: -A / --all / . / :/ / *
BULK_ADD = re.compile(
    r"\bgit\s+(?:-\S+\s+)*add\b(?:\s+-\w+|\s+--[\w-]+)*\s*"
    r"(?:-A\b|--all\b|\.(?:\s|$)|:/|\*)"
)
# `git add -f` — force overrides .gitignore, which is the whole protection here
FORCED_ADD = re.compile(r"\bgit\s+(?:-\S+\s+)*add\b[^;|&]*\s(?:-f\b|--force\b)")
# `git commit -a` stages every tracked modification without review
COMMIT_ALL = re.compile(r"\bgit\s+(?:-\S+\s+)*commit\b[^;|&]*\s(?:-a\b|--all\b)")


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return 0  # never break the session on a malformed payload

    command = (payload.get("tool_input") or {}).get("command") or ""
    if not command:
        return 0

    if BULK_ADD.search(command):
        reason = "bulk `git add` (-A / --all / . / *)"
    elif FORCED_ADD.search(command):
        reason = "`git add -f`, which overrides .gitignore"
    elif COMMIT_ALL.search(command):
        reason = "`git commit -a`, which stages every tracked change unreviewed"
    else:
        return 0

    print(
        f"BLOCKED: {reason}.\n"
        "\n"
        "This repo holds customer PII in the working tree (.xlsx/.pdf/.docx:\n"
        "client lists, billing allocations, policy schedules). Committing them\n"
        "is a POPIA disclosure and git history is not cleanly reversible.\n"
        "\n"
        "Stage explicitly instead:  git add path/to/file.java\n"
        "Review first with:         git status --porcelain\n"
        "\n"
        "If this file genuinely belongs in git, say so and run it yourself.\n"
        "This guard only constrains Claude.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
