FROM ghcr.io/graalvm/native-image-community:21 AS builder
WORKDIR /build
COPY ../../gradlew ../../gradlew.bat ./
COPY ../../gradle ./gradle/
COPY ../../build.gradle.kts ../../settings.gradle.kts ./
COPY ../../workflow-engine-sdk ./workflow-engine-sdk/
COPY ../../workflow-engine-sdk-spring-boot-starter ./workflow-engine-sdk-spring-boot-starter/
COPY ../../samples ./samples/
RUN ./gradlew publishToMavenLocal --no-daemon
RUN cd samples && ./gradlew :erc20:nativeCompile --no-daemon

FROM gcr.io/distroless/base-debian12
WORKDIR /app
COPY --from=builder /build/samples/erc20/build/native/nativeCompile/erc20 /app/erc20

EXPOSE 5503
USER nonroot
ENTRYPOINT ["/app/erc20"]
