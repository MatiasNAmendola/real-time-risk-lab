#!/usr/bin/env bash
# Post-process Cucumber JSON + JaCoCo into structured report tree.
# Usage: ./scripts/report.sh [exit_code]
# Called automatically from root scripts/atdd-bare.sh after Gradle test.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ATDD_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$ATDD_DIR/../.." && pwd)"

# Input artifacts
CUCUMBER_JSON="$ATDD_DIR/build/cucumber-reports/report.json"
CUCUMBER_LOG="$ATDD_DIR/build/cucumber.log"
JACOCO_XML="$ATDD_DIR/build/reports/jacoco/test/jacocoTestReport.xml"
JACOCO_HTML_INDEX="$ATDD_DIR/build/reports/jacoco/test/html/index.html"

# Output tree
TIMESTAMP="$(date -u '+%Y-%m-%dT%H-%M-%S')"
OUT_ROOT="$PROJECT_ROOT/out/atdd-cucumber"
OUT_DIR="$OUT_ROOT/$TIMESTAMP"
LATEST_LINK="$OUT_ROOT/latest"

ORIGINAL_EXIT="${1:-0}"

# ------------------------------------------------------------------
# Check prerequisites
# ------------------------------------------------------------------
if [[ ! -f "$CUCUMBER_JSON" ]]; then
  echo "report.sh: cucumber JSON not found at $CUCUMBER_JSON — skipping report generation." >&2
  exit 0
fi

# ------------------------------------------------------------------
# Prefer jq; fall back to Python 3 (always available on macOS/Linux)
# ------------------------------------------------------------------
if command -v jq &>/dev/null; then
  USE_JQ=true
else
  USE_JQ=false
fi

mkdir -p "$OUT_DIR/features" "$OUT_DIR/coverage"

# ------------------------------------------------------------------
# Parse cucumber.json via Python (works regardless of jq presence)
# ------------------------------------------------------------------
python3 - "$CUCUMBER_JSON" "$OUT_DIR" "$JACOCO_XML" "$JACOCO_HTML_INDEX" "$CUCUMBER_LOG" "$TIMESTAMP" "$ORIGINAL_EXIT" <<'PYEOF'
import json, sys, os, xml.etree.ElementTree as ET, re, math

cucumber_json = sys.argv[1]
out_dir       = sys.argv[2]
jacoco_xml    = sys.argv[3]
jacoco_html   = sys.argv[4]
cucumber_log  = sys.argv[5]
timestamp     = sys.argv[6]
exit_code     = sys.argv[7]

feat_dir  = os.path.join(out_dir, "features")
cov_dir   = os.path.join(out_dir, "coverage")

# ---- Load cucumber report ----------------------------------------
with open(cucumber_json) as f:
    features = json.load(f)

# ---- Helpers -------------------------------------------------------
STATUS_SYMBOL = {"passed": "PASS", "failed": "FAIL", "skipped": "SKIP",
                 "pending": "SKIP", "undefined": "SKIP"}

def ns_to_ms(ns):
    return round((ns or 0) / 1_000_000, 1)

def scenario_status(el):
    steps = el.get("steps", [])
    statuses = [s.get("result", {}).get("status", "skipped") for s in steps]
    if "failed" in statuses:
        return "FAIL"
    if all(s in ("passed",) for s in statuses):
        return "PASS"
    return "SKIP"

def scenario_duration_ms(el):
    return sum(ns_to_ms(s.get("result", {}).get("duration", 0)) for s in el.get("steps", []))

def step_note(step):
    name = step.get("name", "")
    doc  = step.get("doc_string", {})
    if doc:
        return "(docstring)"
    rows = step.get("rows", [])
    if rows:
        return f"({len(rows)} rows)"
    return ""

# ---- Aggregate totals ---------------------------------------------
total_scenarios = 0
total_pass = 0
total_fail = 0
total_skip = 0
total_duration_ms = 0.0
feature_rows = []

FEATURE_SLUG_RE = re.compile(r'[^a-z0-9]+')

for idx, feat in enumerate(features, start=1):
    feat_name = feat.get("name", f"feature-{idx}")
    slug = FEATURE_SLUG_RE.sub('-', feat_name.lower()).strip('-')
    slug = slug[:40]
    file_id = f"{idx:02d}-{slug}"
    file_base = feat.get("uri", "").split("/")[-1].replace(".feature", "")

    elements = feat.get("elements", [])
    scenarios = [e for e in elements if e.get("type") == "scenario"]
    f_pass = sum(1 for e in scenarios if scenario_status(e) == "PASS")
    f_fail = sum(1 for e in scenarios if scenario_status(e) == "FAIL")
    f_skip = sum(1 for e in scenarios if scenario_status(e) == "SKIP")
    f_dur  = sum(scenario_duration_ms(e) for e in scenarios)

    total_scenarios += len(scenarios)
    total_pass += f_pass
    total_fail += f_fail
    total_skip += f_skip
    total_duration_ms += f_dur

    feature_rows.append({
        "idx": idx, "name": feat_name, "file_base": file_base,
        "slug": slug, "file_id": file_id,
        "scenarios": len(scenarios),
        "pass": f_pass, "fail": f_fail, "skip": f_skip,
        "duration_ms": f_dur,
        "elements": scenarios,
    })

    # ---- Per-feature detail file ---------------------------------
    feat_md_path = os.path.join(feat_dir, f"{file_id}.md")
    lines = []
    lines.append(f"# Feature: {feat_name}\n")
    lines.append(f"**File**: `{file_base}.feature`  ")
    lines.append(f"**Scenarios**: {len(scenarios)} ({f_pass} PASS, {f_fail} FAIL, {f_skip} SKIP)  ")
    lines.append(f"**Duration**: {f_dur:.1f}ms\n")

    for el in scenarios:
        sc_name   = el.get("name", "unnamed")
        sc_status = scenario_status(el)
        sc_dur    = scenario_duration_ms(el)
        tags      = " ".join(t.get("name","") for t in el.get("tags", []))
        lines.append(f"## Scenario: {sc_name}\n")
        lines.append(f"**Status**: {sc_status} · **Duration**: {sc_dur:.1f}ms  ")
        if tags:
            lines.append(f"**Tags**: `{tags}`\n")
        lines.append("")
        lines.append("### Steps\n")
        lines.append("| # | Step | Status | Duration |")
        lines.append("|---|---|---|---|")
        for si, step in enumerate(el.get("steps", []), start=1):
            kw   = step.get("keyword", "").strip()
            sn   = step.get("name", "")
            sr   = step.get("result", {})
            ss   = STATUS_SYMBOL.get(sr.get("status", "skipped"), "SKIP")
            sdur = f"{ns_to_ms(sr.get('duration', 0)):.1f}ms"
            err  = sr.get("error_message", "")
            note = f" `{err[:80]}`" if err else step_note(step)
            lines.append(f"| {si} | {kw} {sn} | {ss} | {sdur} |")
            if err:
                lines.append(f"|   | _Error_: `{err[:120]}` | | |")
        lines.append("")

        # Embeddings / output (if any)
        for step in el.get("steps", []):
            for emb in step.get("embeddings", []):
                mime = emb.get("mime_type", "")
                if "text" in mime:
                    lines.append(f"```\n{emb.get('data','')[:500]}\n```\n")

    with open(feat_md_path, "w") as f:
        f.write("\n".join(lines) + "\n")

# ---- JaCoCo coverage -------------------------------------------
cov_lines = []
cov_summary = {}
cov_total_covered = 0
cov_total_lines = 0

if os.path.exists(jacoco_xml):
    tree = ET.parse(jacoco_xml)
    root = tree.getroot()
    for pkg in root.findall("package"):
        name = pkg.get("name", "").replace("/", ".")
        short = name.split(".")[-2] + "." + name.split(".")[-1] if "." in name else name
        lc = [c for c in pkg.findall("counter") if c.get("type") == "LINE"]
        if lc:
            missed  = int(lc[0].get("missed", 0))
            covered = int(lc[0].get("covered", 0))
            total   = missed + covered
            if total > 0:
                pct = covered / total * 100
                cov_summary[short] = (pct, covered, total)
                cov_total_covered += covered
                cov_total_lines   += total

overall_pct = (cov_total_covered / cov_total_lines * 100) if cov_total_lines else 0

cov_lines.append("# JaCoCo Coverage Summary\n")
cov_lines.append(f"**Overall line coverage**: {overall_pct:.1f}% ({cov_total_covered}/{cov_total_lines} lines)\n")
cov_lines.append("| Package (short) | Line % | Covered | Total |")
cov_lines.append("|---|---|---|---|")
for pkg_short, (pct, cov, tot) in sorted(cov_summary.items(), key=lambda x: -x[1][0]):
    bar = int(pct / 10)
    cov_lines.append(f"| `{pkg_short}` | {pct:.1f}% | {cov} | {tot} |")

if os.path.exists(jacoco_html):
    cov_lines.append(f"\n[Full JaCoCo HTML report]({jacoco_html})\n")

jacoco_md_path = os.path.join(cov_dir, "jacoco-summary.md")
with open(jacoco_md_path, "w") as f:
    f.write("\n".join(cov_lines) + "\n")

# ---- Coverage matrix -------------------------------------------
# Hard-coded mapping based on README + feature content knowledge
MATRIX_KEYWORDS = {
    "low":         ["domain.rule", "domain.service", "usecase.risk", "resilience"],
    "high":        ["domain.rule", "domain.service", "usecase.risk", "resilience", "repository.event"],
    "device":      ["domain.rule", "domain.service", "usecase.risk", "resilience"],
    "idempotent":  ["usecase.risk", "repository.idempotency"],
    "outbox":      ["usecase.risk", "repository.event"],
    "fallback":    [],
    "correlation": ["domain.entity", "usecase.risk", "repository.event"],
}
COLS = ["domain.rule", "domain.service", "usecase.risk", "resilience", "repository.event",
        "repository.idempotency", "domain.entity"]

mat_lines = ["# Coverage Matrix per Feature\n"]
header = "| Feature | " + " | ".join(f"`{c}`" for c in COLS) + " |"
sep    = "|---|" + ":---:|" * len(COLS)
mat_lines.append(header)
mat_lines.append(sep)

def find_covered(feat_name):
    name_lower = feat_name.lower()
    for keyword, layers in MATRIX_KEYWORDS.items():
        if keyword in name_lower:
            return layers
    return []

for row in feature_rows:
    covered = find_covered(row["name"])
    cells   = " | ".join("●" if any(col_key in entry or entry in col_key for entry in covered) else " " for col_key in COLS)
    label   = f"{row['idx']:02d} {row['name'][:32]}"
    if row["skip"] > 0 and row["pass"] == 0:
        label += " (@wip)"
    mat_lines.append(f"| {label} | {cells} |")

matrix_md_path = os.path.join(cov_dir, "matrix.md")
with open(matrix_md_path, "w") as f:
    f.write("\n".join(mat_lines) + "\n")

# ---- summary.md ------------------------------------------------
total_dur_s = total_duration_ms / 1000
ts_display  = timestamp.replace("T", "T").replace("-", ":", 2)  # visual only
ts_iso      = timestamp[:10] + "T" + timestamp[11:].replace("-", ":") + "Z"

sm = []
sm.append(f"# ATDD Cucumber run — {ts_iso}\n")
sm.append(f"**Project**: no-vertx-clean-engine (bare-javac via Cucumber-JVM)  ")
sm.append(f"**Features**: {len(feature_rows)} · **Scenarios**: {total_scenarios} · "
          f"**PASS**: {total_pass} · **FAIL**: {total_fail} · **SKIP (@wip)**: {total_skip}  ")
sm.append(f"**Duration**: {total_dur_s:.1f}s  ")
sm.append(f"**Exit code**: {exit_code}\n")
sm.append("## Features\n")
sm.append("| # | Feature | Scenarios | PASS | FAIL | SKIP | Duration | Detail |")
sm.append("|---|---|---|---|---|---|---|---|")
for r in feature_rows:
    link = f"[→](features/{r['file_id']}.md)"
    sm.append(f"| {r['idx']:02d} | {r['name']} | {r['scenarios']} | {r['pass']} | {r['fail']} | {r['skip']} | {r['duration_ms']:.0f}ms | {link} |")

sm.append("\n## Coverage\n")
if cov_summary:
    # Pick specific packages of interest by suffix match
    highlight_suffixes = ["usecase.risk", "domain.service", "domain.rule", "resilience"]
    for suffix in highlight_suffixes:
        for pkg_short, (pct, cov, tot) in cov_summary.items():
            if pkg_short.endswith(suffix):
                sm.append(f"- JaCoCo line coverage: **{pct:.1f}%** (`{pkg_short}`)")
                break
    sm.append(f"\n- Overall: **{overall_pct:.1f}%** ({cov_total_covered}/{cov_total_lines} lines)")
    if os.path.exists(jacoco_html):
        sm.append(f"- [JaCoCo HTML]({jacoco_html})")
    sm.append(f"- [Coverage matrix](coverage/matrix.md)")
    sm.append(f"- [JaCoCo package summary](coverage/jacoco-summary.md)")

summary_md_path = os.path.join(out_dir, "summary.md")
with open(summary_md_path, "w") as f:
    f.write("\n".join(sm) + "\n")

# ---- summary.txt (plain, no markdown) -------------------------
txt = []
txt.append(f"ATDD Cucumber run — {ts_iso}")
txt.append(f"Project : no-vertx-clean-engine (bare-javac via Cucumber-JVM)")
txt.append(f"Features: {len(feature_rows)}  Scenarios: {total_scenarios}  PASS: {total_pass}  FAIL: {total_fail}  SKIP: {total_skip}")
txt.append(f"Duration: {total_dur_s:.1f}s  Exit code: {exit_code}")
txt.append("")
txt.append(f"{'#':<3} {'Feature':<42} {'Sc':>3} {'OK':>4} {'FL':>4} {'SK':>4} {'ms':>8}")
txt.append("-" * 72)
for r in feature_rows:
    txt.append(f"{r['idx']:02d}  {r['name'][:42]:<42} {r['scenarios']:3d} {r['pass']:4d} {r['fail']:4d} {r['skip']:4d} {r['duration_ms']:8.0f}")
if cov_summary:
    txt.append("")
    txt.append(f"Coverage (overall): {overall_pct:.1f}% ({cov_total_covered}/{cov_total_lines} lines)")

summary_txt_path = os.path.join(out_dir, "summary.txt")
with open(summary_txt_path, "w") as f:
    f.write("\n".join(txt) + "\n")

# ---- meta.json -------------------------------------------------
import datetime
meta = {
    "timestamp": ts_iso,
    "duration_s": round(total_duration_ms / 1000, 3),
    "exit_code": int(exit_code),
    "features": len(feature_rows),
    "scenarios": {"total": total_scenarios, "pass": total_pass, "fail": total_fail, "skip": total_skip},
    "coverage_overall_pct": round(overall_pct, 1),
    "env": {
        "os": os.uname().sysname,
        "python": sys.version.split()[0],
    }
}
import json as _json
with open(os.path.join(out_dir, "meta.json"), "w") as f:
    _json.dump(meta, f, indent=2)

# ---- Console summary table ------------------------------------
print("")
print(f"  {'#':<3} {'Feature':<42} {'OK':>4} {'FL':>4} {'SK':>4} {'ms':>8}")
print(f"  " + "-" * 66)
for r in feature_rows:
    status_tag = "[FAIL]" if r["fail"] > 0 else ("[SKIP]" if r["skip"] == r["scenarios"] else "[ OK ]")
    print(f"  {r['idx']:02d}  {r['name'][:42]:<42} {r['pass']:4d} {r['fail']:4d} {r['skip']:4d} {r['duration_ms']:8.0f}  {status_tag}")
print(f"  " + "-" * 66)
print(f"  {'TOTAL':<46} {total_pass:4d} {total_fail:4d} {total_skip:4d} {total_duration_ms:8.0f}")
if cov_summary:
    print(f"\n  Coverage (lines): {overall_pct:.1f}% ({cov_total_covered}/{cov_total_lines})")
print("")
PYEOF

# ------------------------------------------------------------------
# Copy cucumber.log into full.log if it exists
# ------------------------------------------------------------------
if [[ -f "$CUCUMBER_LOG" ]]; then
  cp "$CUCUMBER_LOG" "$OUT_DIR/full.log"
fi

# ------------------------------------------------------------------
# Update symlink: latest -> <timestamp>
# ------------------------------------------------------------------
ln -sfn "$TIMESTAMP" "$LATEST_LINK"

echo "  Report: $OUT_DIR"
echo "  Symlink: $LATEST_LINK -> $TIMESTAMP"
