# EN002 — Playwright E2E runner image.
#
# Built from the official Playwright image (OD-3), pinned to the SAME version as `@playwright/test`
# in package.json (keep these in lock-step). Chromium and its dependencies are already in the base
# image — a developer never needs locally installed browser binaries (VC-007).

FROM mcr.microsoft.com/playwright:v1.62.1-noble

WORKDIR /work

# Install exactly the locked dependency set.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# Test sources, config, and support utilities live in the image (enabler §12).
COPY . .

# `e2e.sh` runs the container; args (e.g. `-g "shell loads"`) are appended.
ENTRYPOINT ["npx", "playwright", "test"]
