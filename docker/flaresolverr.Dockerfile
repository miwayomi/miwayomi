# syntax=docker/dockerfile:1
#
# FlareSolverr built from source inside a Python virtualenv.
#
# NOTE: FlareSolverr REQUIRES a headless Chromium engine to solve challenges —
# that Chromium belongs to FlareSolverr itself (it launches its own browser),
# it is NOT part of the miwayomi image. The miwayomi app only talks to the
# FlareSolverr HTTP API and ships no browser.

# ---- Stage 1: fetch FlareSolverr source (pinned) ----
FROM python:3.11-slim-bookworm AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/fs-src
RUN git clone --depth 1 --branch v3.5.0 https://github.com/FlareSolverr/FlareSolverr . \
    && rm -rf .git

# ---- Stage 2: runtime with a virtualenv ----
FROM python:3.11-slim-bookworm
WORKDIR /app

# FlareSolverr's own browser engine (Chromium) + Xvfb for headless solving.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        chromium chromium-common chromium-driver \
        xvfb dumb-init procps curl xauth \
    && rm -rf /var/lib/apt/lists/* \
    # remove broken hardware-decoding libs (same cleanup as the official image)
    && rm -f /usr/lib/x86_64-linux-gnu/libmfxhw* \
    && rm -f /usr/lib/x86_64-linux-gnu/mfx/*

# FlareSolverr looks for the driver at /app/chromedriver when running in Docker.
RUN mv /usr/bin/chromedriver /app/chromedriver

# Build the virtualenv and install FlareSolverr dependencies.
COPY --from=build /opt/fs-src/requirements.txt /app/requirements.txt
RUN python3 -m venv /opt/fsvenv \
    && /opt/fsvenv/bin/pip install --no-cache-dir -r /app/requirements.txt

# Copy the source (package.json goes to /, which the app expects).
COPY --from=build /opt/fs-src/src /app
COPY --from=build /opt/fs-src/package.json /package.json

# Non-root user.
RUN useradd --home-dir /app --shell /bin/sh flaresolverr \
    && chown -R flaresolverr:flaresolverr /app \
    && mkdir -p "/app/.config/chromium/Crash Reports/pending" \
    && mkdir /config && chown flaresolverr:flaresolverr /config

VOLUME ["/config"]

EXPOSE 8191
EXPOSE 8192

# dumb-init avoids zombie Chromium processes.
ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["/opt/fsvenv/bin/python", "-u", "/app/flaresolverr.py"]
