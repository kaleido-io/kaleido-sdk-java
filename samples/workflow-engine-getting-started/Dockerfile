FROM ghcr.io/graalvm/native-image-community:21 AS builder
WORKDIR /build
COPY .. .
RUN ./gradlew :sample:nativeCompile --no-daemon

FROM gcr.io/distroless/base-nossl-debian12
WORKDIR /app
COPY --from=builder /build/sample/build/native/nativeCompile/wfe-sample /app/wfe-sample
COPY sample/src/main/resources/wfe-config.yaml /config/wfe-config.yaml

ENV WFE_CONFIG_FILE=/config/wfe-config.yaml
USER nonroot
ENTRYPOINT ["/app/wfe-sample"]
