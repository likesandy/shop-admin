"""Fail staging acceptance if the workflow's end-to-end duration exceeds 15 minutes."""
import argparse
from datetime import datetime, timezone

parser = argparse.ArgumentParser()
parser.add_argument("created_at", help="GitHub workflow run created_at (includes runner queue time)")
args = parser.parse_args()
started = datetime.fromisoformat(args.created_at.replace("Z", "+00:00"))
elapsed = (datetime.now(timezone.utc) - started).total_seconds()
if elapsed < 0:
    raise SystemExit("Invalid workflow creation time in the future")
print(f"Workflow creation to verified staging: {elapsed:.0f}s / 900s")
if elapsed > 900:
    raise SystemExit("Staging acceptance failed: end-to-end delivery exceeded 15 minutes")
