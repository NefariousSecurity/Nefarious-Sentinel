#!/usr/bin/env bash
# Set public repository presentation and safer reporting channels.
# Requires GitHub CLI authenticated as an administrator of the repository.
# Usage: bash scripts/configure-public-repository.sh --check|--apply
set -euo pipefail
umask 077

REPO="NefariousSecurity/Nefarious-Sentinel"
DESC="Open-source, local-first Android device protection with USB safeguards, Dead Man's Switch and optional Device Owner hardening."
HOME_URL="https://github.com/${REPO}/releases"
usage() { echo "Usage: bash scripts/configure-public-repository.sh --check|--apply"; }
[[ "$#" == 1 ]] || { usage; exit 2; }
MODE="$1"
[[ "$MODE" == "--check" || "$MODE" == "--apply" ]] ||
  { usage; exit 2; }
command -v gh >/dev/null || { echo "STOP: Install and authenticate GitHub CLI"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "STOP: GitHub CLI not authenticated"; exit 1; }
VISIBILITY="$(gh repo view "$REPO" --json visibility --jq '.visibility')"
[[ "$VISIBILITY" == PUBLIC ]] || { echo "STOP: Expected the PUBLIC release repository"; exit 1; }

echo "Repository: $REPO"
echo "Visibility: $VISIBILITY"
echo "Current metadata:"
gh api "repos/$REPO" --jq '{description,homepage,has_issues,default_branch,topics}'
echo "Private vulnerability reporting:"
gh api "repos/$REPO/private-vulnerability-reporting" --jq '.enabled'

if [[ "$MODE" == --check ]]; then
  echo "CHECK ONLY: no repository settings changed"
  exit 0
fi

gh repo edit "$REPO" \
  --description "$DESC" \
  --homepage "$HOME_URL" \
  --enable-issues \
  --add-topic android \
  --add-topic grapheneos \
  --add-topic open-source \
  --add-topic privacy \
  --add-topic security \
  --add-topic device-owner

# Security-critical repository: let researchers report privately,
# separately from ordinary public bugs and feature requests.
gh api -X PUT "repos/$REPO/private-vulnerability-reporting"

[[ "$(gh api "repos/$REPO/private-vulnerability-reporting" --jq '.enabled')" == true ]] ||
  { echo "STOP: Private vulnerability reporting not verified"; exit 1; }
[[ "$(gh api "repos/$REPO" --jq '.has_issues')" == true ]] ||
  { echo "STOP: GitHub Issues enablement not verified"; exit 1; }

echo "PASS: Public repository description, homepage, topics and Issues set"
echo "PASS: Private vulnerability reporting verified enabled"
echo "Manual: GitHub account avatar and social preview require web UI upload"
