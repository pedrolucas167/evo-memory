"""Query OSV for the Maven dependencies present in a completed test run."""
import argparse
import json
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def inventory():
    reports = sorted((ROOT / "target/surefire-reports").glob("TEST-*.xml"))
    if not reports:
        raise RuntimeError("Run Maven tests before auditing dependencies")
    root = ET.parse(reports[0]).getroot()
    prop = root.find("./properties/property[@name='java.class.path']")
    if prop is None:
        raise RuntimeError("No test classpath found")
    packages = set()
    for entry in prop.attrib["value"].split(";" if ";" in prop.attrib["value"] else ":"):
        parts = Path(entry).parts
        if "repository" not in parts or not entry.endswith(".jar"):
            continue
        relative = parts[parts.index("repository") + 1:]
        if len(relative) >= 4:
            packages.add((".".join(relative[:-3]) + ":" + relative[-3], relative[-2]))
    return sorted(packages)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "target/security/osv-report.json")
    parser.add_argument("--details", action="store_true")
    args = parser.parse_args()
    packages = inventory()
    queries = [{"package": {"ecosystem": "Maven", "name": name}, "version": version}
               for name, version in packages]
    req = urllib.request.Request("https://api.osv.dev/v1/querybatch",
                                 data=json.dumps({"queries": queries}).encode("utf-8"),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as response:
        results = json.load(response)["results"]
    findings = [{"package": name, "version": version, "advisories": [v["id"] for v in result.get("vulns", [])]}
                for (name, version), result in zip(packages, results) if result.get("vulns")]
    report = {"source": "https://api.osv.dev", "packages_checked": len(packages), "findings": findings,
              "scope": "Dependencies on the tested classpath, including test libraries; no reachability analysis."}
    if args.details:
        def detail(advisory):
            with urllib.request.urlopen("https://api.osv.dev/v1/vulns/" + advisory, timeout=30) as response:
                data = json.load(response)
            return advisory, {"summary": data.get("summary"), "withdrawn": data.get("withdrawn"),
                              "affected": data.get("affected", [])}
        ids = sorted({item for finding in findings for item in finding["advisories"]})
        with ThreadPoolExecutor(max_workers=4) as pool:
            report["details"] = dict(pool.map(detail, ids))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"OSV: {len(packages)} dependencies checked; {len(findings)} packages with advisories.")
    for finding in findings:
        print(f"{finding['package']} {finding['version']}: {', '.join(finding['advisories'])}")
    print(f"Saved: {args.output}")


if __name__ == "__main__":
    main()
