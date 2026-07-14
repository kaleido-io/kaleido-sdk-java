# Illustrative container build for a provider based on this sample. Assumes the
# sample sources are wired into a Gradle project with GraalVM native-image
# support (see README.md — this sample is source-only in this repository).
FROM ghcr.io/graalvm/native-image-community:21 AS builder
WORKDIR /build
COPY . .
RUN ./gradlew nativeCompile --no-daemon

FROM gcr.io/distroless/base-nossl-debian12
WORKDIR /app
COPY --from=builder /build/build/native/nativeCompile/provider /app/provider
COPY --from=builder /build/src/main/resources/kaleido-config.yaml /config/kaleido-config.yaml

ENV KALEIDO_CONFIG_FILE=/config/kaleido-config.yaml
USER nonroot
ENTRYPOINT ["/app/provider"]
