# Agent instructions

Gadgetbridge is an Android application for connecting to and managing wearable devices. Keep device support changes consistent with neighboring device integrations and the project developer documentation.

## Working and validation

- Use the repository's Gradle wrapper and documented development workflow; run focused tests or checks relevant to the changed module.
- Keep release and upstream synchronization workflows intact when changing Actions.
- Before changing GitHub Actions, check whether [pschmitt/ci-workflows](https://github.com/pschmitt/ci-workflows) provides a reusable workflow or action. Preserve current behavior and check coverage, and use `@main` for shared workflow or action references.
