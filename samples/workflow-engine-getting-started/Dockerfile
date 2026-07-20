# Illustrative container build for this sample: GraalVM native-image, two stages.
# Build from this directory: `docker build -t provider .`
FROM ghcr.io/graalvm/native-image-community:21 AS builder

# Gradle wrapper script requires xargs.
RUN microdnf install -y findutils && microdnf clean all

WORKDIR /build
COPY . .

# The GitHub Packages token is passed as a BuildKit secret, so it can't leak
# into the image or build cache.
RUN --mount=type=secret,id=gpr_token \
    GITHUB_ACTOR=kaleido-io GITHUB_TOKEN="$(cat /run/secrets/gpr_token)" \
    ./gradlew --no-daemon nativeCompile

FROM gcr.io/distroless/base-debian12
WORKDIR /app

# Include libz from the builder image
COPY --from=builder /usr/lib64/libz.so.1 /usr/lib/libz.so.1

COPY --from=builder /build/build/native/nativeCompile/provider /app/provider
COPY --from=builder /build/src/main/resources/kaleido-config.yaml /config/kaleido-config.yaml

ENV KALEIDO_CONFIG_FILE=/config/kaleido-config.yaml
ENV LD_LIBRARY_PATH=/usr/lib
USER nonroot
ENTRYPOINT ["/app/provider"]
