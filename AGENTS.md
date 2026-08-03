# Repository workflow

- Before starting work, fetch `origin` and inspect the current branch, working tree, and divergence.
- Unless the user explicitly requests a separate branch, perform repository work directly on `main`.
- After modifying files, verify the change, stage only the intended files, commit with a clear message, and push directly to `origin/main`.
- Code review or approval from a particular team member is not a required gate. Run a review only when the user explicitly requests one.
- Never include unrelated or pre-existing working-tree changes in a commit.
- Do not copy the personal `timeTabler` project wholesale; port only the required contracts and verified academic data.
