# BrProject — monorepo entrypoint image (JDK 25).
# Prefer deploy/docker/Dockerfile.login + Dockerfile.game for Compose (Phase 5).
FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache util-linux bash dos2unix

WORKDIR /l2Brproject

# Non-root user (Phase 1 ops hardening)
RUN addgroup -S brproject && adduser -S brproject -G brproject \
    && mkdir -p log game/log login/log \
    && chown -R brproject:brproject /l2Brproject

# Copy distribution layout (build artifacts + runtime data expected by entrypoint)
COPY --chown=brproject:brproject . .

RUN dos2unix entrypoint.sh 2>/dev/null || true \
    && chmod +x entrypoint.sh \
    && chown brproject:brproject entrypoint.sh

USER brproject

EXPOSE 7777
EXPOSE 2106

ENTRYPOINT ["./entrypoint.sh"]
