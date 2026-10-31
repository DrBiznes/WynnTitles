FROM golang:1.26-bookworm AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY catalog ./catalog
COPY internal ./internal
COPY cmd ./cmd
ARG VERSION=dev
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w -X main.version=${VERSION}" -o /out/wynntitles ./cmd/wynntitles

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /data && chown 568:568 /data
LABEL org.opencontainers.image.source="https://github.com/DrBiznes/WynnTitles"
WORKDIR /app
COPY --from=build /out/wynntitles /app/wynntitles
USER 568:568
ENV WT_ADDR=:8080 WT_DB=/data/wynntitles.db
EXPOSE 8080 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD ["/app/wynntitles", "healthcheck"]
ENTRYPOINT ["/app/wynntitles"]
