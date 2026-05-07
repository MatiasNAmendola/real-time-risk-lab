import argparse
import json
import re
import sys
from collections import Counter
from datetime import datetime, timedelta
from pathlib import Path

# Categories of paths. ORDER matters: first match wins.
CATEGORIES = [
    ("PRIMITIVE", [r"^\.ai/primitives/"]),
    ("VAULT",     [r"^vault/"]),
    ("DOCS",      [r"^docs/"]),
    ("CODEBASE",  [r"^poc/", r"^pkg/", r"^sdks/", r"^cli/", r"^bench/", r"^tests/"]),
    ("INFRA",     [r"^compose/", r"^scripts/", r"^dashboard/", r"^setup\.sh", r"^\.github/"]),
    ("CONFIG",    [r"^\.ai/(scripts|hooks|context|adapters|research|audit-rules)/",
                   r"^build-logic/", r"^settings\.gradle\.kts", r"^build\.gradle\.kts",
                   r"^gradle/", r"^README\.md", r"^STATUS\.md", r"^BUILDING\.md", r"^nx"]),
    ("OTHER",     [r".*"]),  # catch-all
]


def categorize(path: str) -> str:
    # Normalize: strip leading ./ and absolute prefix
    p = path.lstrip("./")
    repo_root = Path(__file__).parent.parent.parent.resolve()
    try:
        p_abs = Path(path).resolve()
        p = str(p_abs.relative_to(repo_root))
    except (ValueError, OSError):
        pass

    for category, patterns in CATEGORIES:
        for pat in patterns:
            if re.match(pat, p):
                return category
    return "OTHER"


def load_reads(log_dir: Path, since_days: int = 7) -> list:
    cutoff = datetime.utcnow() - timedelta(days=since_days)
    events = []
    for f in sorted(log_dir.glob("reads-*.jsonl")):
        try:
            for line in f.read_text().splitlines():
                if not line.strip():
                    continue
                ev = json.loads(line)
                ts = datetime.fromisoformat(ev["ts"].replace("Z", "+00:00"))
                if ts.replace(tzinfo=None) >= cutoff:
                    events.append(ev)
        except Exception:
            continue
    return events


def categorize_events(events: list) -> dict:
    by_cat = Counter()
    by_file = Counter()
    for ev in events:
        cat = categorize(ev["path"])
        by_cat[cat] += 1
        by_file[ev["path"]] += 1
    return {
        "total": len(events),
        "by_category": dict(by_cat),
        "top_files": by_file.most_common(20),
    }


def primitive_coverage_ratio(by_cat: dict) -> float:
    primitive = by_cat.get("PRIMITIVE", 0) + by_cat.get("VAULT", 0)
    codebase = by_cat.get("CODEBASE", 0)
    if primitive + codebase == 0:
        return 0.0
    return 100.0 * primitive / (primitive + codebase)


def render_table(stats: dict) -> str:
    total = stats["total"]
    lines = [f"Total reads tracked: {total}", ""]
    lines.append(f"{'Category':<12} {'Count':>8} {'Pct':>8}")
    lines.append("-" * 32)
    for cat, _ in CATEGORIES:
        count = stats["by_category"].get(cat, 0)
        pct = (count / total * 100) if total else 0
        lines.append(f"{cat:<12} {count:>8} {pct:>7.1f}%")
    lines.append("-" * 32)
    ratio = primitive_coverage_ratio(stats["by_category"])
    lines.append(f"")
    lines.append(f"Primitive coverage ratio: {ratio:.1f}%")
    lines.append(f"  (= reads to PRIMITIVE+VAULT / (PRIMITIVE+VAULT + CODEBASE))")
    if ratio >= 50:
        lines.append("  Verdict: HIGH -- primitives are guiding work")
    elif ratio >= 25:
        lines.append("  Verdict: MEDIUM -- partial primitive adoption")
    else:
        lines.append("  Verdict: LOW -- going to codebase directly")
    lines.append("")
    lines.append(f"Top files read:")
    for path, count in stats["top_files"][:10]:
        lines.append(f"  {count:>4}x {path}")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Codebase access auditor")
    parser.add_argument("--since", type=int, default=7, help="Days back to analyze")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--out-dir", type=Path, help="Write summary.md here")
    parser.add_argument("--threshold", type=float, default=25, help="Min primitive coverage %%")
    parser.add_argument("--strict", action="store_true", help="Exit 1 if below threshold")
    args = parser.parse_args()

    repo_root = Path(__file__).parent.parent.parent.resolve()
    log_dir = repo_root / ".ai" / "logs"

    events = load_reads(log_dir, since_days=args.since)
    stats = categorize_events(events)

    if args.json:
        print(json.dumps(stats, indent=2, default=str))
    else:
        print(render_table(stats))

    if args.out_dir:
        args.out_dir.mkdir(parents=True, exist_ok=True)
        (args.out_dir / "summary.md").write_text(
            f"# Codebase access audit -- {datetime.utcnow().isoformat()}Z\n\n"
            f"```\n{render_table(stats)}\n```\n"
        )

    ratio = primitive_coverage_ratio(stats["by_category"])
    if args.strict and ratio < args.threshold:
        sys.exit(1)
    sys.exit(0)


if __name__ == "__main__":
    main()
