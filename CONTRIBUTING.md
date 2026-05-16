# Contributing

## Development Workflow

**Workflow Order: Issue → Branch → Implementation → PR (mandatory).**

- Create an issue (Context, Objective, Definition of Done) before implementation.
- Create a branch using the naming convention: `task/<ID>-<short-desc>`, `feat/<ID>-<short-desc>`, `bug/<ID>-<short-desc>`.
- Implement changes on that branch only. Do not work directly on `main`.
- Run `mise run check` to validate formatting, compilation, and tests before opening a PR.
- In PR body include `Closes #<issue-number>` and a checklist matching the issue DoD.

See `AGENTS.md` for branch-name patterns and agent-specific guidance.

## Release Pipeline

### Local Release Preparation

We use a hybrid approach for releases:
1. **Local:** Mise task bumps version and creates git tag
2. **Automated:** GitHub Actions publishes to npm on new tags

### Creating a Release

#### 1. Show Release Help (Optional)
```bash
mise run release:help
```

#### 2. Prepare Release Locally
```bash
# From your task branch (after Issue & implementation)
mise run release:prepare [patch|minor|major]

# Examples:
mise run release:prepare patch  # 0.1.0 → 0.1.1
mise run release:prepare minor  # 0.1.0 → 0.2.0
mise run release:prepare major  # 0.1.0 → 1.0.0
```

The task will:
- ✅ Validate the bump type
- ✅ Calculate the new semantic version
- ✅ Show you a summary and ask for confirmation
- ✅ Update `build.sbt` (removes `-SNAPSHOT` suffix)
- ✅ Update `package.json`
- ✅ Create a git commit with release notes
- ✅ Create a git tag (e.g., `v0.1.1`)

#### 3. Verify & Review
```bash
# Check the commit
git log -1 --oneline
git show --stat

# List all tags
git tag -ln
```

#### 4. Push to GitHub
```bash
git push origin                 # Push commit
git push origin --tags          # Push tags
```

#### 5. Create Pull Request
- Link the PR to your original Issue
- Reference the release commit: `Closes #XX`
- Wait for CI checks to pass

#### 6. Merge & Auto-Publish
Once your PR is merged to `main`:
- GitHub Actions `publish.yml` workflow triggers automatically
- `mise run check` validates the build
- `mise run build:js` compiles the JavaScript
- `mise run package:prepare` prepares the npm package
- Package is published to GitHub Package Registry
- GitHub Release is created with auto-generated release notes

### Workflow Diagram
```
Issue #XX
    ↓
Branch: task/XX-release-version
    ↓
Implementation (if version-related changes)
    ↓
mise run release:prepare [type]  ← Creates commit + tag locally
    ↓
git push + git push --tags        ← Push to GitHub
    ↓
PR → Review & Merge
    ↓
GitHub Actions publish.yml       ← Auto-publishes to npm
    ↓
Release published! 🎉
```

