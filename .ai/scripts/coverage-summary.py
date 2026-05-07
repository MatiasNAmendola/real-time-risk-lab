#!/usr/bin/env python3
"""
coverage-summary.py — parse jacoco.xml and print a coverage table.

Usage:
  python3 coverage-summary.py path/to/jacoco.xml
  python3 coverage-summary.py path/to/jacoco.xml --json
  python3 coverage-summary.py path/to/jacoco.xml --markdown
  python3 coverage-summary.py path/to/jacoco.xml --threshold 80
  python3 coverage-summary.py path/to/jacoco.xml --no-color
"""

import argparse
import json
import os
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone


# ---------------------------------------------------------------------------
# Counter helpers
# ---------------------------------------------------------------------------

def _empty_counters():
    return {t: {"missed": 0, "covered": 0} for t in
            ("INSTRUCTION", "BRANCH", "LINE", "METHOD", "CLASS")}


def _pct(missed, covered):
    total = missed + covered
    if total == 0:
        return None  # n/a
    return round(covered / total * 100)


def _fmt_pct(val, width=7):
    if val is None:
        return "n/a".rjust(width)
    return f"{val}%".rjust(width)


# ---------------------------------------------------------------------------
# Parse
# ---------------------------------------------------------------------------

def parse_jacoco_xml(path):
    """
    Returns a dict:
      {
        "packages": {pkg_name: {counter_type: {missed, covered}, ...}, ...},
        "totals": {counter_type: {missed, covered}, ...}
      }
    """
    tree = ET.parse(path)
    root = tree.getroot()

    packages = {}
    for pkg in root.findall("package"):
        name = pkg.get("name", "").replace("/", ".")
        counters = _empty_counters()
        for ctr in pkg.findall("counter"):
            t = ctr.get("type")
            if t in counters:
                counters[t]["missed"] += int(ctr.get("missed", 0))
                counters[t]["covered"] += int(ctr.get("covered", 0))
        packages[name] = counters

    # Totals from root-level counters (authoritative aggregated values)
    totals = _empty_counters()
    for ctr in root.findall("counter"):
        t = ctr.get("type")
        if t in totals:
            totals[t]["missed"] = int(ctr.get("missed", 0))
            totals[t]["covered"] = int(ctr.get("covered", 0))

    # Fallback: if root has no counters, sum packages
    if all(totals[t]["missed"] + totals[t]["covered"] == 0 for t in totals):
        for pkg_counters in packages.values():
            for t, vals in pkg_counters.items():
                totals[t]["missed"] += vals["missed"]
                totals[t]["covered"] += vals["covered"]

    return {"packages": packages, "totals": totals}


# ---------------------------------------------------------------------------
# Build summary rows
# ---------------------------------------------------------------------------

def build_rows(data):
    """
    Returns list of (pkg_name, line_pct, branch_pct, method_pct) sorted by
    line_pct desc (None treated as -1 for sorting).
    """
    rows = []
    for pkg, ctrs in data["packages"].items():
        line_pct = _pct(ctrs["LINE"]["missed"], ctrs["LINE"]["covered"])
        branch_pct = _pct(ctrs["BRANCH"]["missed"], ctrs["BRANCH"]["covered"])
        method_pct = _pct(ctrs["METHOD"]["missed"], ctrs["METHOD"]["covered"])
        rows.append((pkg, line_pct, branch_pct, method_pct))
    rows.sort(key=lambda r: (r[1] if r[1] is not None else -1), reverse=True)
    return rows


def total_row(data):
    t = data["totals"]
    return (
        _pct(t["LINE"]["missed"], t["LINE"]["covered"]),
        _pct(t["BRANCH"]["missed"], t["BRANCH"]["covered"]),
        _pct(t["METHOD"]["missed"], t["METHOD"]["covered"]),
    )


# ---------------------------------------------------------------------------
# Color helpers
# ---------------------------------------------------------------------------

RESET = "\033[0m"
RED = "\033[31m"
YELLOW = "\033[33m"
GREEN = "\033[32m"
BOLD = "\033[1m"
DIM = "\033[2m"


def _color_pct(val, use_color):
    if not use_color or val is None:
        return _fmt_pct(val)
    if val >= 80:
        return GREEN + _fmt_pct(val) + RESET
    if val >= 60:
        return YELLOW + _fmt_pct(val) + RESET
    return RED + _fmt_pct(val) + RESET


# ---------------------------------------------------------------------------
# Terminal output
# ---------------------------------------------------------------------------

COL_PKG = 55
COL_NUM = 9


def _sep(use_color):
    line = "─" * (COL_PKG + COL_NUM * 3 + 2)
    if use_color:
        return DIM + line + RESET
    return line


def print_table(data, html_path=None, md_path=None, use_color=True, out=None):
    if out is None:
        out = sys.stdout

    rows = build_rows(data)
    tot_line, tot_branch, tot_method = total_row(data)

    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    hdr = f"Aggregated coverage — {ts}"
    if use_color:
        hdr = BOLD + hdr + RESET
    print(hdr, file=out)
    print(file=out)

    col_hdr = (
        "Package".ljust(COL_PKG)
        + "Lines".rjust(COL_NUM)
        + "Branches".rjust(COL_NUM)
        + "Methods".rjust(COL_NUM)
    )
    if use_color:
        col_hdr = BOLD + col_hdr + RESET
    print(col_hdr, file=out)
    print(_sep(use_color), file=out)

    for pkg, line_pct, branch_pct, method_pct in rows:
        name = pkg if len(pkg) <= COL_PKG else "…" + pkg[-(COL_PKG - 1):]
        line = (
            name.ljust(COL_PKG)
            + _color_pct(line_pct, use_color)
            + _color_pct(branch_pct, use_color)
            + _color_pct(method_pct, use_color)
        )
        print(line, file=out)

    print(_sep(use_color), file=out)

    tot_label = "TOTAL"
    if use_color:
        tot_label = BOLD + tot_label + RESET
    tot_line_str = _color_pct(tot_line, use_color)
    tot_branch_str = _color_pct(tot_branch, use_color)
    tot_method_str = _color_pct(tot_method, use_color)
    print(
        tot_label.ljust(COL_PKG) + tot_line_str + tot_branch_str + tot_method_str,
        file=out,
    )
    print(file=out)

    if html_path:
        print(f"Report HTML: {html_path}", file=out)
    if md_path:
        print(f"Markdown:    {md_path}", file=out)


# ---------------------------------------------------------------------------
# Markdown output
# ---------------------------------------------------------------------------

def print_markdown(data, html_path=None, out=None):
    if out is None:
        out = sys.stdout

    rows = build_rows(data)
    tot_line, tot_branch, tot_method = total_row(data)
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    print(f"# Coverage Summary", file=out)
    print(f"", file=out)
    print(f"Generated: {ts}", file=out)
    print(f"", file=out)
    print(f"| Package | Lines | Branches | Methods |", file=out)
    print(f"|---------|------:|---------:|--------:|", file=out)

    for pkg, line_pct, branch_pct, method_pct in rows:
        line_s = _fmt_pct(line_pct).strip()
        branch_s = _fmt_pct(branch_pct).strip()
        method_s = _fmt_pct(method_pct).strip()
        print(f"| `{pkg}` | {line_s} | {branch_s} | {method_s} |", file=out)

    print(f"| **TOTAL** | **{_fmt_pct(tot_line).strip()}** | **{_fmt_pct(tot_branch).strip()}** | **{_fmt_pct(tot_method).strip()}** |", file=out)

    if html_path:
        print(f"", file=out)
        print(f"Full HTML report: `{html_path}`", file=out)


# ---------------------------------------------------------------------------
# JSON output
# ---------------------------------------------------------------------------

def build_json(data):
    rows = build_rows(data)
    tot_line, tot_branch, tot_method = total_row(data)
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    packages_out = []
    for pkg, line_pct, branch_pct, method_pct in rows:
        packages_out.append({
            "package": pkg,
            "lines": line_pct,
            "branches": branch_pct,
            "methods": method_pct,
        })

    return {
        "generated": ts,
        "packages": packages_out,
        "total": {
            "lines": tot_line,
            "branches": tot_branch,
            "methods": tot_method,
        },
    }


# ---------------------------------------------------------------------------
# Threshold check
# ---------------------------------------------------------------------------

def check_threshold(data, threshold):
    """Returns True if TOTAL lines coverage >= threshold, False otherwise."""
    t = data["totals"]
    line_pct = _pct(t["LINE"]["missed"], t["LINE"]["covered"])
    if line_pct is None:
        return True  # no data, do not fail
    return line_pct >= threshold


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser():
    p = argparse.ArgumentParser(
        prog="coverage-summary.py",
        description="Parse jacoco.xml and print coverage table.",
    )
    p.add_argument(
        "xml",
        nargs="?",
        default="build/reports/jacoco/aggregate/jacoco.xml",
        help="Path to jacoco.xml (default: build/reports/jacoco/aggregate/jacoco.xml)",
    )
    p.add_argument(
        "--json",
        action="store_true",
        dest="output_json",
        help="Output JSON instead of table",
    )
    p.add_argument(
        "--markdown",
        action="store_true",
        dest="output_markdown",
        help="Output Markdown table",
    )
    p.add_argument(
        "--threshold",
        type=int,
        default=None,
        metavar="N",
        help="Exit 1 if TOTAL lines coverage < N%%",
    )
    p.add_argument(
        "--no-color",
        action="store_true",
        dest="no_color",
        help="Disable ANSI color output",
    )
    p.add_argument(
        "--html-path",
        default=None,
        metavar="PATH",
        help="HTML report path to show in footer",
    )
    p.add_argument(
        "--md-path",
        default=None,
        metavar="PATH",
        help="Markdown report path to show in footer",
    )
    return p


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)

    xml_path = args.xml
    if not os.path.isfile(xml_path):
        print(f"ERROR: jacoco.xml not found: {xml_path}", file=sys.stderr)
        sys.exit(1)

    data = parse_jacoco_xml(xml_path)

    use_color = not args.no_color and sys.stdout.isatty()
    if args.no_color:
        use_color = False

    if args.output_json:
        print(json.dumps(build_json(data), indent=2))
    elif args.output_markdown:
        print_markdown(data, html_path=args.html_path)
    else:
        print_table(
            data,
            html_path=args.html_path,
            md_path=args.md_path,
            use_color=use_color,
        )

    if args.threshold is not None:
        if not check_threshold(data, args.threshold):
            t = data["totals"]
            line_pct = _pct(t["LINE"]["missed"], t["LINE"]["covered"])
            print(
                f"THRESHOLD FAIL: lines coverage {line_pct}% < {args.threshold}%",
                file=sys.stderr,
            )
            sys.exit(1)


if __name__ == "__main__":
    main()
